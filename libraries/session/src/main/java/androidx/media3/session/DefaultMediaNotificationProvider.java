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

import static androidx.media3.common.C.INDEX_UNSET;
import static androidx.media3.common.Player.COMMAND_INVALID;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_STOP;
import static androidx.media3.common.util.Assertions.checkState;
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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * <h2>Custom commands</h2>
 *
 * Custom actions are sent to the session under the hood. You can receive them by overriding the
 * session callback method {@link MediaSession.Callback#onCustomCommand(MediaSession,
 * MediaSession.ControllerInfo, SessionCommand, Bundle)}. This is useful because starting with
 * Android 13, the System UI notification sends commands directly to the session. So handling the
 * custom commands on the session level allows you to handle them at the same callback for all API
 * levels.
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
 *   <li><b>{@code media3_notification_small_icon}</b> - The {@link
 *       NotificationCompat.Builder#setSmallIcon(int) small icon}.
 * </ul>
 */
@UnstableApi
public class DefaultMediaNotificationProvider implements MediaNotification.Provider {

  /**
   * An extras key that can be used to define the index of a {@link CommandButton} in {@linkplain
   * Notification.MediaStyle#setShowActionsInCompactView(int...) compact view}.
   */
  public static final String COMMAND_KEY_COMPACT_VIEW_INDEX =
      "androidx.media3.session.command.COMPACT_VIEW_INDEX";

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
  public final MediaNotification createNotification(
      MediaSession mediaSession,
      ImmutableList<CommandButton> customLayout,
      MediaNotification.ActionFactory actionFactory,
      Callback onNotificationChangedCallback) {
    ensureNotificationChannel();

    Player player = mediaSession.getPlayer();
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);

    MediaStyle mediaStyle = new MediaStyle();
    int[] compactViewIndices =
        addNotificationActions(
            mediaSession,
            getMediaButtons(player.getAvailableCommands(), customLayout, player.getPlayWhenReady()),
            builder,
            actionFactory);
    mediaStyle.setShowActionsInCompactView(compactViewIndices);

    // Set metadata info in the notification.
    MediaMetadata metadata = player.getMediaMetadata();
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

    if (player.isCommandAvailable(COMMAND_STOP) || Util.SDK_INT < 21) {
      // We must include a cancel intent for pre-L devices.
      mediaStyle.setCancelButtonIntent(
          actionFactory.createMediaActionPendingIntent(mediaSession, COMMAND_STOP));
    }

    long playbackStartTimeMs = getPlaybackStartTimeEpochMs(player);
    boolean displayElapsedTimeWithChronometer = playbackStartTimeMs != C.TIME_UNSET;
    builder
        .setWhen(playbackStartTimeMs)
        .setShowWhen(displayElapsedTimeWithChronometer)
        .setUsesChronometer(displayElapsedTimeWithChronometer);

    Notification notification =
        builder
            .setContentIntent(mediaSession.getSessionActivity())
            .setDeleteIntent(
                actionFactory.createMediaActionPendingIntent(mediaSession, COMMAND_STOP))
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .build();
    return new MediaNotification(NOTIFICATION_ID, notification);
  }

  @Override
  public final boolean handleCustomCommand(MediaSession session, String action, Bundle extras) {
    // Make the custom action being delegated to the session as a custom session command.
    return false;
  }

  /**
   * Returns the ordered list of {@linkplain CommandButton command buttons} to be used to build the
   * notification.
   *
   * <p>This method is called each time a new notification is built.
   *
   * <p>Override this method to customize the buttons on the notification. Commands of the buttons
   * returned by this method must be contained in {@link MediaController#getAvailableCommands()}.
   *
   * <p>By default the notification shows {@link Player#COMMAND_PLAY_PAUSE} in {@linkplain
   * Notification.MediaStyle#setShowActionsInCompactView(int...) compact view}. This can be
   * customized by defining the index of the command in compact view of up to 3 commands in their
   * extras with key {@link DefaultMediaNotificationProvider#COMMAND_KEY_COMPACT_VIEW_INDEX}.
   *
   * <p>To make the custom layout and commands work, you need to {@linkplain
   * MediaSession#setCustomLayout(List) set the custom layout of commands} and add the custom
   * commands to the available commands when a controller {@linkplain
   * MediaSession.Callback#onConnect(MediaSession, MediaSession.ControllerInfo) connects to the
   * session}. Controllers that connect after you called {@link MediaSession#setCustomLayout(List)}
   * need the custom command set in {@link MediaSession.Callback#onPostConnect(MediaSession,
   * MediaSession.ControllerInfo)} also.
   *
   * @param playerCommands The available player commands.
   * @param customLayout The {@linkplain MediaSession#setCustomLayout(List) custom layout of
   *     commands}.
   * @param playWhenReady The current {@code playWhenReady} state.
   * @return The ordered list of command buttons to be placed on the notification.
   */
  protected List<CommandButton> getMediaButtons(
      Player.Commands playerCommands, List<CommandButton> customLayout, boolean playWhenReady) {
    // Skip to previous action.
    List<CommandButton> commandButtons = new ArrayList<>();
    if (playerCommands.containsAny(COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
      Bundle commandButtonExtras = new Bundle();
      commandButtonExtras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, INDEX_UNSET);
      commandButtons.add(
          new CommandButton.Builder()
              .setPlayerCommand(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
              .setIconResId(R.drawable.media3_notification_seek_to_previous)
              .setDisplayName(
                  context.getString(R.string.media3_controls_seek_to_previous_description))
              .setExtras(commandButtonExtras)
              .build());
    }
    if (playerCommands.contains(COMMAND_PLAY_PAUSE)) {
      Bundle commandButtonExtras = new Bundle();
      commandButtonExtras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, INDEX_UNSET);
      commandButtons.add(
          new CommandButton.Builder()
              .setPlayerCommand(COMMAND_PLAY_PAUSE)
              .setIconResId(
                  playWhenReady
                      ? R.drawable.media3_notification_pause
                      : R.drawable.media3_notification_play)
              .setExtras(commandButtonExtras)
              .setDisplayName(
                  playWhenReady
                      ? context.getString(R.string.media3_controls_pause_description)
                      : context.getString(R.string.media3_controls_play_description))
              .build());
    }
    // Skip to next action.
    if (playerCommands.containsAny(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
      Bundle commandButtonExtras = new Bundle();
      commandButtonExtras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, INDEX_UNSET);
      commandButtons.add(
          new CommandButton.Builder()
              .setPlayerCommand(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
              .setIconResId(R.drawable.media3_notification_seek_to_next)
              .setExtras(commandButtonExtras)
              .setDisplayName(context.getString(R.string.media3_controls_seek_to_next_description))
              .build());
    }
    for (int i = 0; i < customLayout.size(); i++) {
      CommandButton button = customLayout.get(i);
      if (button.sessionCommand != null
          && button.sessionCommand.commandCode == SessionCommand.COMMAND_CODE_CUSTOM) {
        commandButtons.add(button);
      }
    }
    return commandButtons;
  }

  /**
   * Adds the media buttons to the notification builder for the given action factory.
   *
   * <p>The list of {@code mediaButtons} is the list resulting from {@link #getMediaButtons(
   * Player.Commands, List, boolean)}.
   *
   * <p>Override this method to customize how the media buttons {@linkplain
   * NotificationCompat.Builder#addAction(NotificationCompat.Action) are added} to the notification
   * and define which actions are shown in compact view by returning the indices of the buttons to
   * be shown in compact view.
   *
   * <p>By default {@link Player#COMMAND_PLAY_PAUSE} is shown in compact view, unless some of the
   * buttons are marked with {@link DefaultMediaNotificationProvider#COMMAND_KEY_COMPACT_VIEW_INDEX}
   * to declare the index in compact view of the given command button in the button extras.
   *
   * @param mediaSession The media session to which the actions will be sent.
   * @param mediaButtons The command buttons to be included in the notification.
   * @param builder The builder to add the actions to.
   * @param actionFactory The actions factory to be used to build notifications.
   * @return The indices of the buttons to be {@linkplain
   *     Notification.MediaStyle#setShowActionsInCompactView(int...) used in compact view of the
   *     notification}.
   */
  protected int[] addNotificationActions(
      MediaSession mediaSession,
      List<CommandButton> mediaButtons,
      NotificationCompat.Builder builder,
      MediaNotification.ActionFactory actionFactory) {
    int[] compactViewIndices = new int[3];
    Arrays.fill(compactViewIndices, INDEX_UNSET);
    int compactViewCommandCount = 0;
    for (int i = 0; i < mediaButtons.size(); i++) {
      CommandButton commandButton = mediaButtons.get(i);
      if (commandButton.sessionCommand != null) {
        builder.addAction(
            actionFactory.createCustomActionFromCustomCommandButton(mediaSession, commandButton));
      } else {
        checkState(commandButton.playerCommand != COMMAND_INVALID);
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, commandButton.iconResId),
                commandButton.displayName,
                commandButton.playerCommand));
      }
      if (compactViewCommandCount == 3) {
        continue;
      }
      int compactViewIndex =
          commandButton.extras.getInt(
              COMMAND_KEY_COMPACT_VIEW_INDEX, /* defaultValue= */ INDEX_UNSET);
      if (compactViewIndex >= 0 && compactViewIndex < compactViewIndices.length) {
        compactViewCommandCount++;
        compactViewIndices[compactViewIndex] = i;
      } else if (commandButton.playerCommand == COMMAND_PLAY_PAUSE
          && compactViewCommandCount == 0) {
        // If there is no custom configuration we use the play/pause action in compact view.
        compactViewIndices[0] = i;
      }
    }
    for (int i = 0; i < compactViewIndices.length; i++) {
      if (compactViewIndices[i] == INDEX_UNSET) {
        compactViewIndices = Arrays.copyOf(compactViewIndices, i);
        break;
      }
    }
    return compactViewIndices;
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

  private static long getPlaybackStartTimeEpochMs(Player player) {
    // Changing "showWhen" causes notification flicker if SDK_INT < 21.
    if (Util.SDK_INT >= 21
        && player.isPlaying()
        && !player.isPlayingAd()
        && !player.isCurrentMediaItemDynamic()
        && player.getPlaybackParameters().speed == 1f) {
      return System.currentTimeMillis() - player.getContentPosition();
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
