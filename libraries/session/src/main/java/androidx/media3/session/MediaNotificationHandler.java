/*
 * Copyright 2019 The Android Open Source Project
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
 * limitations under the License.
 */
package androidx.media3.session;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.Util;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Class to provide default media notification for {@link MediaSessionService}, and set the service
 * as foreground/background according to the player state.
 */
/* package */ class MediaNotificationHandler
    implements MediaSession.ForegroundServiceEventCallback {

  private static final int NOTIFICATION_ID = 1001;
  private static final String NOTIFICATION_CHANNEL_ID = "default_channel_id";

  private final Object lock;
  private final MediaSessionService service;
  private final NotificationManager notificationManager;
  private final String notificationChannelName;

  private final Intent startSelfIntent;
  private final NotificationCompat.Action playAction;
  private final NotificationCompat.Action pauseAction;
  private final NotificationCompat.Action skipToPrevAction;
  private final NotificationCompat.Action skipToNextAction;

  @GuardedBy("lock")
  private final WeakHashMap<MediaSession, PlayerInfo> playerInfoMap;

  public MediaNotificationHandler(MediaSessionService service) {
    lock = new Object();
    this.service = service;
    startSelfIntent = new Intent(service, service.getClass());

    notificationManager =
        (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationChannelName =
        service.getResources().getString(R.string.default_notification_channel_name);

    playAction =
        createNotificationAction(
            service,
            R.drawable.media_session_service_notification_ic_play,
            R.string.play_button_content_description,
            ACTION_PLAY);
    pauseAction =
        createNotificationAction(
            service,
            R.drawable.media_session_service_notification_ic_pause,
            R.string.pause_button_content_description,
            ACTION_PAUSE);
    skipToPrevAction =
        createNotificationAction(
            service,
            R.drawable.media_session_service_notification_ic_skip_to_previous,
            R.string.skip_to_previous_item_button_content_description,
            ACTION_SKIP_TO_PREVIOUS);
    skipToNextAction =
        createNotificationAction(
            service,
            R.drawable.media_session_service_notification_ic_skip_to_next,
            R.string.skip_to_next_item_button_content_description,
            ACTION_SKIP_TO_NEXT);

    playerInfoMap = new WeakHashMap<>();
  }

  private void updateNotificationIfNeeded(MediaSession session) {
    @Nullable
    MediaSessionService.MediaNotification mediaNotification = service.onUpdateNotification(session);
    if (mediaNotification == null) {
      // The service implementation doesn't want to use the automatic start/stopForeground
      // feature.
      return;
    }

    int id = mediaNotification.notificationId;
    Notification notification = mediaNotification.notification;

    if (Util.SDK_INT >= 21) {
      // Call Notification.MediaStyle#setMediaSession() indirectly.
      android.media.session.MediaSession.Token fwkToken =
          (android.media.session.MediaSession.Token)
              session.getSessionCompat().getSessionToken().getToken();
      notification.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, fwkToken);
    }

    PlayerInfo playerInfo = getPlayerInfoOfSession(session);
    if (playerInfo.playWhenReady) {
      ContextCompat.startForegroundService(service, startSelfIntent);
      service.startForeground(id, notification);
    } else {
      stopForegroundServiceIfNeeded();
      notificationManager.notify(id, notification);
    }
  }

  @Override
  public void onPlayerInfoChanged(
      MediaSession session, PlayerInfo oldPlayerInfo, PlayerInfo newPlayerInfo) {
    synchronized (lock) {
      playerInfoMap.put(session, newPlayerInfo);
    }
    if (Util.areEqual(oldPlayerInfo.mediaMetadata, newPlayerInfo.mediaMetadata)
        && oldPlayerInfo.playWhenReady == newPlayerInfo.playWhenReady) {
      return;
    }
    updateNotificationIfNeeded(session);
  }

  @Override
  public void onSessionReleased(MediaSession session) {
    service.removeSession(session);
    stopForegroundServiceIfNeeded();
  }

  private void stopForegroundServiceIfNeeded() {
    List<MediaSession> sessions = service.getSessions();
    for (int i = 0; i < sessions.size(); i++) {
      PlayerInfo playerInfo = getPlayerInfoOfSession(sessions.get(i));
      if (playerInfo.playWhenReady) {
        return;
      }
    }
    // Calling stopForeground(true) is a workaround for pre-L devices which prevents
    // the media notification from being undismissable.
    boolean shouldRemoveNotification = Util.SDK_INT < 21;
    service.stopForeground(shouldRemoveNotification);
  }

  /** Creates a default media style notification for {@link MediaSessionService}. */
  public MediaSessionService.MediaNotification onUpdateNotification(MediaSession session) {
    PlayerInfo playerInfo = getPlayerInfoOfSession(session);

    ensureNotificationChannel();

    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID);

    // TODO(b/193193926): Filter actions depending on the player's available commands.
    builder.addAction(skipToPrevAction);
    if (playerInfo.playWhenReady) {
      builder.addAction(pauseAction);
    } else {
      builder.addAction(playAction);
    }
    builder.addAction(skipToNextAction);

    // Set metadata info in the notification.
    MediaMetadata metadata = playerInfo.mediaMetadata;
    builder.setContentTitle(metadata.title).setContentText(metadata.artist);
    if (metadata.artworkData != null) {
      Bitmap artworkBitmap =
          BitmapFactory.decodeByteArray(metadata.artworkData, 0, metadata.artworkData.length);
      builder.setLargeIcon(artworkBitmap);
    }

    MediaStyle mediaStyle =
        new MediaStyle()
            .setCancelButtonIntent(createPendingIntent(service, ACTION_STOP))
            .setMediaSession(session.getSessionCompat().getSessionToken())
            .setShowActionsInCompactView(1 /* Show play/pause button only in compact view */);

    Notification notification =
        builder
            .setContentIntent(session.getImpl().getSessionActivity())
            .setDeleteIntent(createPendingIntent(service, ACTION_STOP))
            .setOnlyAlertOnce(true)
            .setSmallIcon(getSmallIconResId())
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .build();

    return new MediaSessionService.MediaNotification(NOTIFICATION_ID, notification);
  }

  private void ensureNotificationChannel() {
    if (Util.SDK_INT < 26
        || notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) {
      return;
    }
    // Need to create a notification channel.
    NotificationChannel channel =
        new NotificationChannel(
            NOTIFICATION_CHANNEL_ID, notificationChannelName, NotificationManager.IMPORTANCE_LOW);
    notificationManager.createNotificationChannel(channel);
  }

  private int getSmallIconResId() {
    int appIcon = service.getApplicationInfo().icon;
    if (appIcon != 0) {
      return appIcon;
    } else {
      // App icon is not set.
      return R.drawable.media_session_service_notification_ic_music_note;
    }
  }

  private PlayerInfo getPlayerInfoOfSession(MediaSession session) {
    @Nullable PlayerInfo playerInfo;
    synchronized (lock) {
      playerInfo = playerInfoMap.get(session);
    }
    return playerInfo == null ? PlayerInfo.DEFAULT : playerInfo;
  }

  private static NotificationCompat.Action createNotificationAction(
      MediaSessionService service,
      int iconResId,
      int titleResId,
      @PlaybackStateCompat.Actions long action) {
    CharSequence title = service.getResources().getText(titleResId);
    return new NotificationCompat.Action(iconResId, title, createPendingIntent(service, action));
  }

  private static PendingIntent createPendingIntent(
      MediaSessionService service, @PlaybackStateCompat.Actions long action) {
    int keyCode = PlaybackStateCompat.toKeyCode(action);
    Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    intent.setComponent(new ComponentName(service, service.getClass()));
    intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));

    if (Util.SDK_INT >= 26 && action != ACTION_PAUSE && action != ACTION_STOP) {
      return PendingIntent.getForegroundService(
          service, /* requestCode= */ keyCode, intent, PendingIntent.FLAG_IMMUTABLE);
    } else {
      return PendingIntent.getService(
          service,
          /* requestCode= */ keyCode,
          intent,
          Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
  }
}
