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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/**
 * The default {@link MediaNotification.Provider}.
 *
 * <h2>Actions</h2>
 *
 * The following actions are included in the provided notifications:
 *
 * <ul>
 *   <li>{@link MediaNotification.ActionFactory#COMMAND_PLAY} to start playback. Included when
 *       {@link MediaController#getPlayWhenReady()} returns {@code false}.
 *   <li>{@link MediaNotification.ActionFactory#COMMAND_PAUSE}, to pause playback. Included when
 *       ({@link MediaController#getPlayWhenReady()} returns {@code true}.
 *   <li>{@link MediaNotification.ActionFactory#COMMAND_SKIP_TO_PREVIOUS} to skip to the previous
 *       item.
 *   <li>{@link MediaNotification.ActionFactory#COMMAND_SKIP_TO_NEXT} to skip to the next item.
 * </ul>
 *
 * <h2>Drawables</h2>
 *
 * The drawables used can be overridden by drawables with the same names defined the application.
 * The drawables are:
 *
 * <ul>
 *   <li><b>{@code media3_notification_play}</b> - The play icon.
 *   <li><b>{@code media3_notification_pause}</b> - The pause icon.
 *   <li><b>{@code media3_notification_seek_to_previous}</b> - The previous icon.
 *   <li><b>{@code media3_notification_seek_to_next}</b> - The next icon.
 * </ul>
 */
@UnstableApi
/* package */ final class DefaultMediaNotificationProvider implements MediaNotification.Provider {

  private static final int NOTIFICATION_ID = 1001;
  private static final String NOTIFICATION_CHANNEL_ID = "default_channel_id";
  private static final String NOTIFICATION_CHANNEL_NAME = "Now playing";

  private final Context context;
  private final NotificationManager notificationManager;

  /** Creates an instance. */
  public DefaultMediaNotificationProvider(Context context) {
    this.context = context.getApplicationContext();
    notificationManager =
        checkStateNotNull(
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
  }

  @Override
  public MediaNotification createNotification(
      MediaController mediaController,
      MediaNotification.ActionFactory actionFactory,
      Callback onNotificationChangedCallback) {
    ensureNotificationChannel();

    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
    // TODO(b/193193926): Filter actions depending on the player's available commands.
    // Skip to previous action.
    builder.addAction(
        actionFactory.createMediaAction(
            IconCompat.createWithResource(context, R.drawable.media3_notification_seek_to_previous),
            context.getString(R.string.media3_controls_seek_to_previous_description),
            MediaNotification.ActionFactory.COMMAND_SKIP_TO_PREVIOUS));
    if (mediaController.getPlayWhenReady()) {
      // Pause action.
      builder.addAction(
          actionFactory.createMediaAction(
              IconCompat.createWithResource(context, R.drawable.media3_notification_pause),
              context.getString(R.string.media3_controls_pause_description),
              MediaNotification.ActionFactory.COMMAND_PAUSE));
    } else {
      // Play action.
      builder.addAction(
          actionFactory.createMediaAction(
              IconCompat.createWithResource(context, R.drawable.media3_notification_play),
              context.getString(R.string.media3_controls_play_description),
              MediaNotification.ActionFactory.COMMAND_PLAY));
    }
    // Skip to next action.
    builder.addAction(
        actionFactory.createMediaAction(
            IconCompat.createWithResource(context, R.drawable.media3_notification_seek_to_next),
            context.getString(R.string.media3_controls_seek_to_next_description),
            MediaNotification.ActionFactory.COMMAND_SKIP_TO_NEXT));

    // Set metadata info in the notification.
    MediaMetadata metadata = mediaController.getMediaMetadata();
    builder.setContentTitle(metadata.title).setContentText(metadata.artist);
    if (metadata.artworkData != null) {
      Bitmap artworkBitmap =
          BitmapFactory.decodeByteArray(metadata.artworkData, 0, metadata.artworkData.length);
      builder.setLargeIcon(artworkBitmap);
    }

    androidx.media.app.NotificationCompat.MediaStyle mediaStyle =
        new androidx.media.app.NotificationCompat.MediaStyle()
            .setCancelButtonIntent(
                actionFactory.createMediaActionPendingIntent(
                    MediaNotification.ActionFactory.COMMAND_STOP))
            .setShowActionsInCompactView(1 /* Show play/pause button only in compact view */);

    Notification notification =
        builder
            .setContentIntent(mediaController.getSessionActivity())
            .setDeleteIntent(
                actionFactory.createMediaActionPendingIntent(
                    MediaNotification.ActionFactory.COMMAND_STOP))
            .setOnlyAlertOnce(true)
            .setSmallIcon(getSmallIconResId(context))
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .build();
    return new MediaNotification(NOTIFICATION_ID, notification);
  }

  @Override
  public void handleCustomAction(MediaController mediaController, String action, Bundle extras) {
    // We don't handle custom commands.
  }

  private void ensureNotificationChannel() {
    if (Util.SDK_INT < 26
        || notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) {
      return;
    }
    NotificationChannel channel =
        new NotificationChannel(
            NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
    notificationManager.createNotificationChannel(channel);
  }

  private static int getSmallIconResId(Context context) {
    int appIcon = context.getApplicationInfo().icon;
    if (appIcon != 0) {
      return appIcon;
    } else {
      return Util.SDK_INT >= 21 ? R.drawable.media_session_service_notification_ic_music_note : 0;
    }
  }
}
