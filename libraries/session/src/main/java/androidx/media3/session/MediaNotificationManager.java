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
    MediaControllerListener listener = new MediaControllerListener(session);
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

  private void updateNotification(MediaSession session) {
    @Nullable ListenableFuture<MediaController> controllerFuture = controllerMap.get(session);
    if (controllerFuture == null) {
      return;
    }

    MediaController mediaController;
    try {
      mediaController = controllerFuture.get(0, TimeUnit.MILLISECONDS);
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      // We should never reach this point.
      throw new IllegalStateException(e);
    }

    int notificationSequence = ++this.totalNotificationCount;
    MediaNotification.Provider.Callback callback =
        notification ->
            mainExecutor.execute(
                () -> onNotificationUpdated(notificationSequence, session, notification));

    MediaNotification mediaNotification =
        this.mediaNotificationProvider.createNotification(mediaController, actionFactory, callback);
    updateNotification(session, mediaNotification);
  }

  private void onNotificationUpdated(
      int notificationSequence, MediaSession session, MediaNotification mediaNotification) {
    if (notificationSequence == this.totalNotificationCount) {
      updateNotification(session, mediaNotification);
    }
  }

  private void updateNotification(MediaSession session, MediaNotification mediaNotification) {
    int id = mediaNotification.notificationId;
    Notification notification = mediaNotification.notification;

    if (Util.SDK_INT >= 21) {
      // Call Notification.MediaStyle#setMediaSession() indirectly.
      android.media.session.MediaSession.Token fwkToken =
          (android.media.session.MediaSession.Token)
              session.getSessionCompat().getSessionToken().getToken();
      notification.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, fwkToken);
    }

    Player player = session.getPlayer();
    if (player.getPlayWhenReady()) {
      ContextCompat.startForegroundService(mediaSessionService, startSelfIntent);
      mediaSessionService.startForeground(id, notification);
    } else {
      stopForegroundServiceIfNeeded();
      notificationManagerCompat.notify(id, notification);
    }
  }

  private void stopForegroundServiceIfNeeded() {
    List<MediaSession> sessions = mediaSessionService.getSessions();
    for (int i = 0; i < sessions.size(); i++) {
      Player player = sessions.get(i).getPlayer();
      if (player.getPlayWhenReady()) {
        return;
      }
    }
    // Calling stopForeground(true) is a workaround for pre-L devices which prevents
    // the media notification from being undismissable.
    boolean shouldRemoveNotification = Util.SDK_INT < 21;
    mediaSessionService.stopForeground(shouldRemoveNotification);
  }

  private final class MediaControllerListener implements MediaController.Listener, Player.Listener {
    private final MediaSession session;

    public MediaControllerListener(MediaSession session) {
      this.session = session;
    }

    public void onConnected() {
      updateNotification(session);
    }

    @Override
    public void onEvents(Player player, Player.Events events) {
      if (events.containsAny(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          Player.EVENT_MEDIA_METADATA_CHANGED)) {
        updateNotification(session);
      }
    }

    @Override
    public void onDisconnected(MediaController controller) {
      mediaSessionService.removeSession(session);
      stopForegroundServiceIfNeeded();
    }
  }
}
