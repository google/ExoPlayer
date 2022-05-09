/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages media notifications for a {@link MediaSessionService} and sets the service as
 * foreground/background according to the player state.
 */
/* package */ final class MediaNotificationManager {

  private final MediaSessionService mediaSessionService;
  private final MediaNotification.Provider mediaNotificationProvider;
  private final MediaNotification.ActionFactory actionFactory;
  private final NotificationManagerCompat notificationManagerCompat;
  private final Executor mainExecutor;
  private final Intent startSelfIntent;
  private final Map<MediaSession, ListenableFuture<MediaController>> controllerMap;

  private int totalNotificationCount;
  @Nullable private MediaNotification mediaNotification;

  public MediaNotificationManager(
      MediaSessionService mediaSessionService,
      MediaNotification.Provider mediaNotificationProvider,
      MediaNotification.ActionFactory actionFactory) {
    this.mediaSessionService = mediaSessionService;
    this.mediaNotificationProvider = mediaNotificationProvider;
    this.actionFactory = actionFactory;
    notificationManagerCompat = NotificationManagerCompat.from(mediaSessionService);
    Handler mainHandler = new Handler(Looper.getMainLooper());
    mainExecutor = (runnable) -> Util.postOrRun(mainHandler, runnable);
    startSelfIntent = new Intent(mediaSessionService, mediaSessionService.getClass());
    controllerMap = new HashMap<>();
  }

  public void addSession(MediaSession session) {
    if (controllerMap.containsKey(session)) {
      return;
    }
    MediaControllerListener listener = new MediaControllerListener(mediaSessionService, session);
    ListenableFuture<MediaController> controllerFuture =
        new MediaController.Builder(mediaSessionService, session.getToken())
            .setListener(listener)
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync();
    controllerFuture.addListener(
        () -> {
          try {
            MediaController controller = controllerFuture.get(/* time= */ 0, TimeUnit.MILLISECONDS);
            listener.onConnected();
            controller.addListener(listener);
          } catch (CancellationException
              | ExecutionException
              | InterruptedException
              | TimeoutException e) {
            // MediaSession or MediaController is released too early. Stop monitoring the session.
            mediaSessionService.removeSession(session);
          }
        },
        mainExecutor);
    controllerMap.put(session, controllerFuture);
  }

  public void removeSession(MediaSession session) {
    @Nullable ListenableFuture<MediaController> controllerFuture = controllerMap.remove(session);
    if (controllerFuture != null) {
      MediaController.releaseFuture(controllerFuture);
    }
  }

  public void onCustomAction(MediaSession session, String action, Bundle extras) {
    @Nullable ListenableFuture<MediaController> controllerFuture = controllerMap.get(session);
    if (controllerFuture == null) {
      return;
    }
    try {
      MediaController mediaController = controllerFuture.get(0, TimeUnit.MILLISECONDS);
      mediaNotificationProvider.handleCustomAction(mediaController, action, extras);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      // We should never reach this.
      throw new IllegalStateException(e);
    }
  }

  public void updateNotification(MediaSession session) {
    @Nullable ListenableFuture<MediaController> controllerFuture = controllerMap.get(session);
    if (controllerFuture == null) {
      return;
    }

    MediaController mediaController;
    try {
      mediaController = checkStateNotNull(Futures.getDone(controllerFuture));
    } catch (ExecutionException e) {
      // We should never reach this point.
      throw new IllegalStateException(e);
    }

    if (!mediaSessionService.isSessionAdded(session) || !canStartPlayback(session.getPlayer())) {
      maybeStopForegroundService(/* removeNotifications= */ true);
      return;
    }

    int notificationSequence = ++totalNotificationCount;
    MediaNotification.Provider.Callback callback =
        notification ->
            mainExecutor.execute(
                () -> onNotificationUpdated(notificationSequence, session, notification));

    MediaNotification mediaNotification =
        this.mediaNotificationProvider.createNotification(mediaController, actionFactory, callback);
    updateNotificationInternal(session, mediaNotification);
  }

  private void onNotificationUpdated(
      int notificationSequence, MediaSession session, MediaNotification mediaNotification) {
    if (notificationSequence == totalNotificationCount) {
      updateNotificationInternal(session, mediaNotification);
    }
  }

  private void updateNotificationInternal(
      MediaSession session, MediaNotification mediaNotification) {
    if (Util.SDK_INT >= 21) {
      // Call Notification.MediaStyle#setMediaSession() indirectly.
      android.media.session.MediaSession.Token fwkToken =
          (android.media.session.MediaSession.Token)
              session.getSessionCompat().getSessionToken().getToken();
      mediaNotification.notification.extras.putParcelable(
          Notification.EXTRA_MEDIA_SESSION, fwkToken);
    }

    this.mediaNotification = mediaNotification;
    Player player = session.getPlayer();
    if (player.getPlayWhenReady() && canStartPlayback(player)) {
      ContextCompat.startForegroundService(mediaSessionService, startSelfIntent);
      mediaSessionService.startForeground(
          mediaNotification.notificationId, mediaNotification.notification);
    } else {
      maybeStopForegroundService(/* removeNotifications= */ false);
      notificationManagerCompat.notify(
          mediaNotification.notificationId, mediaNotification.notification);
    }
  }

  /**
   * Stops the service from the foreground, if no player is actively playing content.
   *
   * @param removeNotifications Whether to remove notifications, if the service is stopped from the
   *     foreground.
   */
  private void maybeStopForegroundService(boolean removeNotifications) {
    List<MediaSession> sessions = mediaSessionService.getSessions();
    for (int i = 0; i < sessions.size(); i++) {
      Player player = sessions.get(i).getPlayer();
      if (player.getPlayWhenReady() && canStartPlayback(player)) {
        return;
      }
    }
    // To hide the notification on all API levels, we need to call both Service.stopForeground(true)
    // and notificationManagerCompat.cancel(notificationId). For pre-L devices, we must also call
    // Service.stopForeground(true) anyway as a workaround that prevents the media notification from
    // being undismissable.
    mediaSessionService.stopForeground(removeNotifications || Util.SDK_INT < 21);
    if (removeNotifications && mediaNotification != null) {
      notificationManagerCompat.cancel(mediaNotification.notificationId);
      // Update the notification count so that if a pending notification callback arrives (e.g., a
      // bitmap is loaded), we don't show the notification.
      totalNotificationCount++;
      mediaNotification = null;
    }
  }

  /**
   * Returns whether {@code player} can start playback and therefore we should present a
   * notification for this player.
   */
  private static boolean canStartPlayback(Player player) {
    return player.getPlaybackState() != Player.STATE_IDLE && !player.getCurrentTimeline().isEmpty();
  }

  private static final class MediaControllerListener
      implements MediaController.Listener, Player.Listener {
    private final MediaSessionService mediaSessionService;
    private final MediaSession session;

    public MediaControllerListener(MediaSessionService mediaSessionService, MediaSession session) {
      this.mediaSessionService = mediaSessionService;
      this.session = session;
    }

    public void onConnected() {
      if (canStartPlayback(session.getPlayer())) {
        // We need to present a notification.
        mediaSessionService.onUpdateNotification(session);
      }
    }

    @Override
    public void onEvents(Player player, Player.Events events) {
      // We must limit the frequency of notification updates, otherwise the system may suppress
      // them.
      if (events.containsAny(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          Player.EVENT_MEDIA_METADATA_CHANGED)) {
        mediaSessionService.onUpdateNotification(session);
      }
    }

    @Override
    public void onDisconnected(MediaController controller) {
      mediaSessionService.removeSession(session);
      // We may need to hide the notification.
      mediaSessionService.onUpdateNotification(session);
    }
  }
}
