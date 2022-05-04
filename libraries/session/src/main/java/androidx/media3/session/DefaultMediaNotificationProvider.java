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

import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_STOP;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.castNonNull;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * The default {@link MediaNotification.Provider}.
 *
 * <h2>Actions</h2>
 *
 * The following actions are included in the provided notifications:
 *
 * <ul>
 *   <li>{@link MediaController#COMMAND_PLAY_PAUSE} to start or pause playback.
 *   <li>{@link MediaController#COMMAND_SEEK_TO_PREVIOUS} to seek to the previous item.
 *   <li>{@link MediaController#COMMAND_SEEK_TO_NEXT} to seek to the next item.
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
public final class DefaultMediaNotificationProvider implements MediaNotification.Provider {
  private static final String TAG = "NotificationProvider";
  private static final int NOTIFICATION_ID = 1001;
  private static final String NOTIFICATION_CHANNEL_ID = "default_channel_id";
  private static final String NOTIFICATION_CHANNEL_NAME = "Now playing";

  private final Context context;
  private final NotificationManager notificationManager;
  private final BitmapLoader bitmapLoader;
  // Cache the last loaded bitmap to avoid reloading the bitmap again, particularly useful when
  // showing a notification for the same item (e.g. when switching from playing to paused).
  private final LoadedBitmapInfo lastLoadedBitmapInfo;
  private final Handler mainHandler;

  private OnBitmapLoadedFutureCallback pendingOnBitmapLoadedFutureCallback;

  /** Creates an instance that uses a {@link SimpleBitmapLoader} for loading artwork images. */
  public DefaultMediaNotificationProvider(Context context) {
    this(context, new SimpleBitmapLoader());
  }

  /** Creates an instance that uses the {@code bitmapLoader} for loading artwork images. */
  public DefaultMediaNotificationProvider(Context context, BitmapLoader bitmapLoader) {
    this.context = context.getApplicationContext();
    this.bitmapLoader = bitmapLoader;
    lastLoadedBitmapInfo = new LoadedBitmapInfo();
    mainHandler = new Handler(Looper.getMainLooper());
    notificationManager =
        checkStateNotNull(
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
    pendingOnBitmapLoadedFutureCallback = new OnBitmapLoadedFutureCallback(bitmap -> {});
  }

  @Override
  public MediaNotification createNotification(
      MediaController mediaController,
      MediaNotification.ActionFactory actionFactory,
      Callback onNotificationChangedCallback) {
    ensureNotificationChannel();

    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
    Player.Commands availableCommands = mediaController.getAvailableCommands();
    // Skip to previous action.
    boolean skipToPreviousAdded = false;
    if (availableCommands.containsAny(
        COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
      skipToPreviousAdded = true;
      builder.addAction(
          actionFactory.createMediaAction(
              IconCompat.createWithResource(
                  context, R.drawable.media3_notification_seek_to_previous),
              context.getString(R.string.media3_controls_seek_to_previous_description),
              COMMAND_SEEK_TO_PREVIOUS));
    }
    boolean playPauseAdded = false;
    if (availableCommands.contains(COMMAND_PLAY_PAUSE)) {
      playPauseAdded = true;
      if (mediaController.getPlaybackState() == Player.STATE_ENDED
          || !mediaController.getPlayWhenReady()) {
        // Play action.
        builder.addAction(
            actionFactory.createMediaAction(
                IconCompat.createWithResource(context, R.drawable.media3_notification_play),
                context.getString(R.string.media3_controls_play_description),
                COMMAND_PLAY_PAUSE));
      } else {
        // Pause action.
        builder.addAction(
            actionFactory.createMediaAction(
                IconCompat.createWithResource(context, R.drawable.media3_notification_pause),
                context.getString(R.string.media3_controls_pause_description),
                COMMAND_PLAY_PAUSE));
      }
    }
    // Skip to next action.
    if (availableCommands.containsAny(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
      builder.addAction(
          actionFactory.createMediaAction(
              IconCompat.createWithResource(context, R.drawable.media3_notification_seek_to_next),
              context.getString(R.string.media3_controls_seek_to_next_description),
              COMMAND_SEEK_TO_NEXT));
    }

    // Set metadata info in the notification.
    MediaMetadata metadata = mediaController.getMediaMetadata();
    builder.setContentTitle(metadata.title).setContentText(metadata.artist);

    @Nullable ListenableFuture<Bitmap> bitmapFuture = loadArtworkBitmap(metadata);
    if (bitmapFuture != null) {
      if (bitmapFuture.isDone()) {
        try {
          builder.setLargeIcon(Futures.getDone(bitmapFuture));
        } catch (ExecutionException e) {
          Log.w(TAG, "Failed to load bitmap", e);
        }
      } else {
        Futures.addCallback(
            bitmapFuture,
            new OnBitmapLoadedFutureCallback(
                bitmap -> {
                  builder.setLargeIcon(bitmap);
                  onNotificationChangedCallback.onNotificationChanged(
                      new MediaNotification(NOTIFICATION_ID, builder.build()));
                }),
            // This callback must be executed on the next looper iteration, after this method has
            // returned a media notification.
            mainHandler::post);
      }
    }

    MediaStyle mediaStyle = new MediaStyle();
    if (mediaController.isCommandAvailable(COMMAND_STOP) || Util.SDK_INT < 21) {
      // We must include a cancel intent for pre-L devices.
      mediaStyle.setCancelButtonIntent(actionFactory.createMediaActionPendingIntent(COMMAND_STOP));
    }
    if (playPauseAdded) {
      // Show play/pause button only in compact view.
      mediaStyle.setShowActionsInCompactView(skipToPreviousAdded ? 1 : 0);
    }

    long playbackStartTimeMs = getPlaybackStartTimeEpochMs(mediaController);
    boolean displayElapsedTimeWithChronometer = playbackStartTimeMs != C.TIME_UNSET;
    builder
        .setWhen(playbackStartTimeMs)
        .setShowWhen(displayElapsedTimeWithChronometer)
        .setUsesChronometer(displayElapsedTimeWithChronometer);

    Notification notification =
        builder
            .setContentIntent(mediaController.getSessionActivity())
            .setDeleteIntent(actionFactory.createMediaActionPendingIntent(COMMAND_STOP))
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

  /**
   * Requests from the bitmapLoader to load artwork or returns null if the metadata don't include
   * artwork.
   */
  @Nullable
  private ListenableFuture<Bitmap> loadArtworkBitmap(MediaMetadata metadata) {
    if (lastLoadedBitmapInfo.matches(metadata.artworkData)
        || lastLoadedBitmapInfo.matches(metadata.artworkUri)) {
      return Futures.immediateFuture(lastLoadedBitmapInfo.getBitmap());
    }

    ListenableFuture<Bitmap> future;
    Consumer<Bitmap> onBitmapLoaded;
    if (metadata.artworkData != null) {
      future = bitmapLoader.decodeBitmap(metadata.artworkData);
      onBitmapLoaded =
          bitmap -> lastLoadedBitmapInfo.setBitmapInfo(castNonNull(metadata.artworkData), bitmap);
    } else if (metadata.artworkUri != null) {
      future = bitmapLoader.loadBitmap(metadata.artworkUri);
      onBitmapLoaded =
          bitmap -> lastLoadedBitmapInfo.setBitmapInfo(castNonNull(metadata.artworkUri), bitmap);
    } else {
      return null;
    }

    pendingOnBitmapLoadedFutureCallback.discardIfPending();
    pendingOnBitmapLoadedFutureCallback = new OnBitmapLoadedFutureCallback(onBitmapLoaded);
    Futures.addCallback(
        future,
        pendingOnBitmapLoadedFutureCallback,
        // It's ok to run this immediately to update the last loaded bitmap.
        runnable -> Util.postOrRun(mainHandler, runnable));
    return future;
  }

  private static int getSmallIconResId(Context context) {
    int appIcon = context.getApplicationInfo().icon;
    if (appIcon != 0) {
      return appIcon;
    } else {
      return Util.SDK_INT >= 21 ? R.drawable.media_session_service_notification_ic_music_note : 0;
    }
  }

  private static long getPlaybackStartTimeEpochMs(MediaController controller) {
    // Changing "showWhen" causes notification flicker if SDK_INT < 21.
    if (Util.SDK_INT >= 21
        && controller.isPlaying()
        && !controller.isPlayingAd()
        && !controller.isCurrentMediaItemDynamic()
        && controller.getPlaybackParameters().speed == 1f) {
      return System.currentTimeMillis() - controller.getContentPosition();
    } else {
      return C.TIME_UNSET;
    }
  }

  private static class OnBitmapLoadedFutureCallback implements FutureCallback<Bitmap> {

    private final Consumer<Bitmap> consumer;

    private boolean discarded;

    private OnBitmapLoadedFutureCallback(Consumer<Bitmap> consumer) {
      this.consumer = consumer;
    }

    public void discardIfPending() {
      discarded = true;
    }

    @Override
    public void onSuccess(Bitmap result) {
      if (!discarded) {
        consumer.accept(result);
      }
    }

    @Override
    public void onFailure(Throwable t) {
      if (!discarded) {
        Log.d(TAG, "Failed to load bitmap", t);
      }
    }
  }

  /**
   * Caches the last loaded bitmap. The key to identify a bitmap is either a byte array, if the
   * bitmap is loaded from compressed data, or a URI, if the bitmap was loaded from a URI.
   */
  private static class LoadedBitmapInfo {
    @Nullable private byte[] data;
    @Nullable private Uri uri;
    @Nullable private Bitmap bitmap;

    public boolean matches(@Nullable byte[] data) {
      return this.data != null && data != null && Arrays.equals(this.data, data);
    }

    public boolean matches(@Nullable Uri uri) {
      return this.uri != null && this.uri.equals(uri);
    }

    public Bitmap getBitmap() {
      return checkStateNotNull(bitmap);
    }

    public void setBitmapInfo(byte[] data, Bitmap bitmap) {
      this.data = data;
      this.bitmap = bitmap;
      this.uri = null;
    }

    public void setBitmapInfo(Uri uri, Bitmap bitmap) {
      this.uri = uri;
      this.bitmap = bitmap;
      this.data = null;
    }
  }
}
