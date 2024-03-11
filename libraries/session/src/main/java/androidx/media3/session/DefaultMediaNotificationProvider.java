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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.annotation.DoNotInline;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaStyleNotificationHelper.MediaStyle;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

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
 * The drawables used can be overridden by drawables with the same file names in {@code
 * res/drawables} of the application module. Alternatively, you can override the drawable resource
 * ID with a {@code drawable} element in a resource file in {@code res/values}. The drawable
 * resource IDs are:
 *
 * <ul>
 *   <li><b>{@code media3_icon_play}</b> - The play icon.
 *   <li><b>{@code media3_icon_pause}</b> - The pause icon.
 *   <li><b>{@code media3_icon_previous}</b> - The previous icon.
 *   <li><b>{@code media3_icon_next}</b> - The next icon.
 *   <li><b>{@code media3_notification_small_icon}</b> - The {@link
 *       NotificationCompat.Builder#setSmallIcon(int) small icon}. A different icon can be set with
 *       {@link #setSmallIcon(int)}.
 * </ul>
 *
 * <h2>String resources</h2>
 *
 * String resources used can be overridden by resources with the same resource IDs defined by the
 * application. The string resource IDs are:
 *
 * <ul>
 *   <li><b>{@code media3_controls_play_description}</b> - The description of the play icon.
 *   <li><b>{@code media3_controls_pause_description}</b> - The description of the pause icon.
 *   <li><b>{@code media3_controls_seek_to_previous_description}</b> - The description of the
 *       previous icon.
 *   <li><b>{@code media3_controls_seek_to_next_description}</b> - The description of the next icon.
 *   <li><b>{@code default_notification_channel_name}</b> The name of the {@link
 *       NotificationChannel} on which created notifications are posted. A different string resource
 *       can be set when constructing the provider with {@link
 *       DefaultMediaNotificationProvider.Builder#setChannelName(int)}.
 * </ul>
 */
@UnstableApi
public class DefaultMediaNotificationProvider implements MediaNotification.Provider {

  /** A builder for {@link DefaultMediaNotificationProvider} instances. */
  public static final class Builder {
    private final Context context;
    private NotificationIdProvider notificationIdProvider;
    private String channelId;
    @StringRes private int channelNameResourceId;
    private boolean built;

    /**
     * Creates a builder.
     *
     * @param context Any {@link Context}.
     */
    public Builder(Context context) {
      this.context = context;
      notificationIdProvider = session -> DEFAULT_NOTIFICATION_ID;
      channelId = DEFAULT_CHANNEL_ID;
      channelNameResourceId = DEFAULT_CHANNEL_NAME_RESOURCE_ID;
    }

    /**
     * Sets the {@link MediaNotification#notificationId} used for the created notifications. By
     * default, this is set to {@link #DEFAULT_NOTIFICATION_ID}.
     *
     * <p>Overwrites anything set in {@link #setNotificationIdProvider(NotificationIdProvider)}.
     *
     * @param notificationId The notification ID.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setNotificationId(int notificationId) {
      this.notificationIdProvider = session -> notificationId;
      return this;
    }

    /**
     * Sets the provider for the {@link MediaNotification#notificationId} used for the created
     * notifications. By default, this is set to a provider that always returns {@link
     * #DEFAULT_NOTIFICATION_ID}.
     *
     * <p>Overwrites anything set in {@link #setNotificationId(int)}.
     *
     * @param notificationIdProvider The notification ID provider.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setNotificationIdProvider(NotificationIdProvider notificationIdProvider) {
      this.notificationIdProvider = notificationIdProvider;
      return this;
    }

    /**
     * Sets the ID of the {@link NotificationChannel} on which created notifications are posted on.
     * By default, this is set to {@link #DEFAULT_CHANNEL_ID}.
     *
     * @param channelId The channel ID.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setChannelId(String channelId) {
      this.channelId = channelId;
      return this;
    }

    /**
     * Sets the name of the {@link NotificationChannel} on which created notifications are posted
     * on. By default, this is set to {@link #DEFAULT_CHANNEL_NAME_RESOURCE_ID}.
     *
     * @param channelNameResourceId The string resource ID with the channel name.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setChannelName(@StringRes int channelNameResourceId) {
      this.channelNameResourceId = channelNameResourceId;
      return this;
    }

    /**
     * Builds the {@link DefaultMediaNotificationProvider}. The method can be called at most once.
     */
    public DefaultMediaNotificationProvider build() {
      checkState(!built);
      DefaultMediaNotificationProvider provider = new DefaultMediaNotificationProvider(this);
      built = true;
      return provider;
    }
  }

  /**
   * Provides notification IDs for posting media notifications for given media sessions.
   *
   * @see Builder#setNotificationIdProvider(NotificationIdProvider)
   */
  public interface NotificationIdProvider {
    /** Returns the notification ID for the media notification of the given session. */
    int getNotificationId(MediaSession mediaSession);
  }

  /**
   * An extras key that can be used to define the index of a {@link CommandButton} in {@linkplain
   * Notification.MediaStyle#setShowActionsInCompactView(int...) compact view}.
   */
  public static final String COMMAND_KEY_COMPACT_VIEW_INDEX =
      "androidx.media3.session.command.COMPACT_VIEW_INDEX";

  /** The default ID used for the {@link MediaNotification#notificationId}. */
  public static final int DEFAULT_NOTIFICATION_ID = 1001;

  /**
   * The default ID used for the {@link NotificationChannel} on which created notifications are
   * posted on.
   */
  public static final String DEFAULT_CHANNEL_ID = "default_channel_id";

  /**
   * The default name used for the {@link NotificationChannel} on which created notifications are
   * posted on.
   */
  @StringRes
  public static final int DEFAULT_CHANNEL_NAME_RESOURCE_ID =
      R.string.default_notification_channel_name;

  /**
   * The group key used for the {@link NotificationCompat.Builder#setGroup(String)} to avoid the
   * media notification being auto-grouped with the other notifications. The other notifications
   * sent from the app shouldn't use this group key.
   */
  public static final String GROUP_KEY = "media3_group_key";

  private static final String TAG = "NotificationProvider";

  private final Context context;
  private final NotificationIdProvider notificationIdProvider;
  private final String channelId;
  @StringRes private final int channelNameResourceId;
  private final NotificationManager notificationManager;

  private @MonotonicNonNull OnBitmapLoadedFutureCallback pendingOnBitmapLoadedFutureCallback;
  @DrawableRes private int smallIconResourceId;

  /**
   * Creates an instance. Use this constructor only when you want to override methods of this class.
   * Otherwise use {@link Builder}.
   */
  public DefaultMediaNotificationProvider(Context context) {
    this(
        context,
        session -> DEFAULT_NOTIFICATION_ID,
        DEFAULT_CHANNEL_ID,
        DEFAULT_CHANNEL_NAME_RESOURCE_ID);
  }

  /**
   * Creates an instance. Use this constructor only when you want to override methods of this class.
   * Otherwise use {@link Builder}.
   */
  public DefaultMediaNotificationProvider(
      Context context,
      NotificationIdProvider notificationIdProvider,
      String channelId,
      int channelNameResourceId) {
    this.context = context;
    this.notificationIdProvider = notificationIdProvider;
    this.channelId = channelId;
    this.channelNameResourceId = channelNameResourceId;
    notificationManager =
        checkStateNotNull(
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
    smallIconResourceId = R.drawable.media3_notification_small_icon;
  }

  private DefaultMediaNotificationProvider(Builder builder) {
    this(
        builder.context,
        builder.notificationIdProvider,
        builder.channelId,
        builder.channelNameResourceId);
  }

  // MediaNotification.Provider implementation

  @Override
  public final MediaNotification createNotification(
      MediaSession mediaSession,
      ImmutableList<CommandButton> customLayout,
      MediaNotification.ActionFactory actionFactory,
      Callback onNotificationChangedCallback) {
    ensureNotificationChannel();

    ImmutableList.Builder<CommandButton> customLayoutWithEnabledCommandButtonsOnly =
        new ImmutableList.Builder<>();
    for (int i = 0; i < customLayout.size(); i++) {
      CommandButton button = customLayout.get(i);
      if (button.sessionCommand != null
          && button.sessionCommand.commandCode == SessionCommand.COMMAND_CODE_CUSTOM
          && button.isEnabled) {
        customLayoutWithEnabledCommandButtonsOnly.add(customLayout.get(i));
      }
    }
    Player player = mediaSession.getPlayer();
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
    int notificationId = notificationIdProvider.getNotificationId(mediaSession);

    MediaStyle mediaStyle = new MediaStyle(mediaSession);
    int[] compactViewIndices =
        addNotificationActions(
            mediaSession,
            getMediaButtons(
                mediaSession,
                player.getAvailableCommands(),
                customLayoutWithEnabledCommandButtonsOnly.build(),
                !Util.shouldShowPlayButton(
                    player, mediaSession.getShowPlayButtonIfPlaybackIsSuppressed())),
            builder,
            actionFactory);
    mediaStyle.setShowActionsInCompactView(compactViewIndices);

    // Set metadata info in the notification.
    if (player.isCommandAvailable(Player.COMMAND_GET_METADATA)) {
      MediaMetadata metadata = player.getMediaMetadata();
      builder
          .setContentTitle(getNotificationContentTitle(metadata))
          .setContentText(getNotificationContentText(metadata));
      @Nullable
      ListenableFuture<Bitmap> bitmapFuture =
          mediaSession.getBitmapLoader().loadBitmapFromMetadata(metadata);
      if (bitmapFuture != null) {
        if (pendingOnBitmapLoadedFutureCallback != null) {
          pendingOnBitmapLoadedFutureCallback.discardIfPending();
        }
        if (bitmapFuture.isDone()) {
          try {
            builder.setLargeIcon(Futures.getDone(bitmapFuture));
          } catch (CancellationException | ExecutionException e) {
            Log.w(TAG, getBitmapLoadErrorMessage(e));
          }
        } else {
          pendingOnBitmapLoadedFutureCallback =
              new OnBitmapLoadedFutureCallback(
                  notificationId, builder, onNotificationChangedCallback);
          Futures.addCallback(
              bitmapFuture,
              pendingOnBitmapLoadedFutureCallback,
              // This callback must be executed on the next looper iteration, after this method has
              // returned a media notification.
              mediaSession.getImpl().getApplicationHandler()::post);
        }
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
        .setWhen(displayElapsedTimeWithChronometer ? playbackStartTimeMs : 0L)
        .setShowWhen(displayElapsedTimeWithChronometer)
        .setUsesChronometer(displayElapsedTimeWithChronometer);

    if (Util.SDK_INT >= 31) {
      Api31.setForegroundServiceBehavior(builder);
    }

    Notification notification =
        builder
            .setContentIntent(mediaSession.getSessionActivity())
            .setDeleteIntent(
                actionFactory.createMediaActionPendingIntent(mediaSession, COMMAND_STOP))
            .setOnlyAlertOnce(true)
            .setSmallIcon(smallIconResourceId)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setGroup(GROUP_KEY)
            .build();
    return new MediaNotification(notificationId, notification);
  }

  @Override
  public final boolean handleCustomCommand(MediaSession session, String action, Bundle extras) {
    // Make the custom action being delegated to the session as a custom session command.
    return false;
  }

  // Other methods

  /**
   * Sets the small icon of the notification which is also shown in the system status bar.
   *
   * @see NotificationCompat.Builder#setSmallIcon(int)
   * @param smallIconResourceId The resource id of the small icon.
   */
  public final void setSmallIcon(@DrawableRes int smallIconResourceId) {
    this.smallIconResourceId = smallIconResourceId;
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
   * <p>By default, the notification shows buttons for {@link Player#seekToPreviousMediaItem()},
   * {@link Player#play()} or {@link Player#pause()}, {@link Player#seekToNextMediaItem()} in
   * {@linkplain Notification.MediaStyle#setShowActionsInCompactView(int...) compact view}. This can
   * be customized by defining the index of the command in compact view of up to 3 commands in their
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
   * @param session The media session.
   * @param playerCommands The available player commands.
   * @param customLayout The {@linkplain MediaSession#setCustomLayout(List) custom layout of
   *     commands}.
   * @param showPauseButton Whether the notification should show a pause button (e.g., because the
   *     player is currently playing content), otherwise show a play button to start playback.
   * @return The ordered list of command buttons to be placed on the notification.
   */
  protected ImmutableList<CommandButton> getMediaButtons(
      MediaSession session,
      Player.Commands playerCommands,
      ImmutableList<CommandButton> customLayout,
      boolean showPauseButton) {
    // Skip to previous action.
    ImmutableList.Builder<CommandButton> commandButtons = new ImmutableList.Builder<>();
    if (playerCommands.containsAny(COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
      Bundle commandButtonExtras = new Bundle();
      commandButtonExtras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, INDEX_UNSET);
      commandButtons.add(
          new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
              .setPlayerCommand(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
              .setDisplayName(
                  context.getString(R.string.media3_controls_seek_to_previous_description))
              .setExtras(commandButtonExtras)
              .build());
    }
    if (playerCommands.contains(COMMAND_PLAY_PAUSE)) {
      Bundle commandButtonExtras = new Bundle();
      commandButtonExtras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, INDEX_UNSET);
      if (showPauseButton) {
        commandButtons.add(
            new CommandButton.Builder(CommandButton.ICON_PAUSE)
                .setPlayerCommand(COMMAND_PLAY_PAUSE)
                .setExtras(commandButtonExtras)
                .setDisplayName(context.getString(R.string.media3_controls_pause_description))
                .build());
      } else {
        commandButtons.add(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setPlayerCommand(COMMAND_PLAY_PAUSE)
                .setExtras(commandButtonExtras)
                .setDisplayName(context.getString(R.string.media3_controls_play_description))
                .build());
      }
    }
    // Skip to next action.
    if (playerCommands.containsAny(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
      Bundle commandButtonExtras = new Bundle();
      commandButtonExtras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, INDEX_UNSET);
      commandButtons.add(
          new CommandButton.Builder(CommandButton.ICON_NEXT)
              .setPlayerCommand(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
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
    return commandButtons.build();
  }

  /**
   * Adds the media buttons to the notification builder for the given action factory.
   *
   * <p>The list of {@code mediaButtons} is the list resulting from {@link #getMediaButtons(
   * MediaSession, Player.Commands, ImmutableList, boolean)}.
   *
   * <p>Override this method to customize how the media buttons {@linkplain
   * NotificationCompat.Builder#addAction(NotificationCompat.Action) are added} to the notification
   * and define which actions are shown in compact view by returning the indices of the buttons to
   * be shown in compact view.
   *
   * <p>By default, the buttons for {@link Player#seekToPreviousMediaItem()}, {@link Player#play()}
   * or {@link Player#pause()}, {@link Player#seekToNextMediaItem()} are shown in compact view,
   * unless some of the buttons are marked with {@link
   * DefaultMediaNotificationProvider#COMMAND_KEY_COMPACT_VIEW_INDEX} to declare the index in
   * compact view of the given command button in the button extras.
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
      ImmutableList<CommandButton> mediaButtons,
      NotificationCompat.Builder builder,
      MediaNotification.ActionFactory actionFactory) {
    int[] compactViewIndices = new int[3];
    int[] defaultCompactViewIndices = new int[3];
    Arrays.fill(compactViewIndices, INDEX_UNSET);
    Arrays.fill(defaultCompactViewIndices, INDEX_UNSET);
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
      } else if (commandButton.playerCommand == COMMAND_SEEK_TO_PREVIOUS
          || commandButton.playerCommand == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
        defaultCompactViewIndices[0] = i;
      } else if (commandButton.playerCommand == COMMAND_PLAY_PAUSE) {
        defaultCompactViewIndices[1] = i;
      } else if (commandButton.playerCommand == COMMAND_SEEK_TO_NEXT
          || commandButton.playerCommand == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
        defaultCompactViewIndices[2] = i;
      }
    }
    if (compactViewCommandCount == 0) {
      // If there is no custom configuration we use the seekPrev (if any), play/pause (if any),
      // seekNext (if any) action in compact view.
      int indexInCompactViewIndices = 0;
      for (int i = 0; i < defaultCompactViewIndices.length; i++) {
        if (defaultCompactViewIndices[i] == INDEX_UNSET) {
          continue;
        }
        compactViewIndices[indexInCompactViewIndices] = defaultCompactViewIndices[i];
        indexInCompactViewIndices++;
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

  /**
   * Returns the content title to be used to build the notification.
   *
   * <p>This method is called each time a new notification is built.
   *
   * <p>Override this method to customize which field of {@link MediaMetadata} is used for content
   * title of the notification.
   *
   * <p>By default, the notification shows {@link MediaMetadata#title} as content title.
   *
   * @param metadata The media metadata from which content title is fetched.
   * @return Notification content title.
   */
  @Nullable
  protected CharSequence getNotificationContentTitle(MediaMetadata metadata) {
    return metadata.title;
  }

  /**
   * Returns the content text to be used to build the notification.
   *
   * <p>This method is called each time a new notification is built.
   *
   * <p>Override this method to customize which field of {@link MediaMetadata} is used for content
   * text of the notification.
   *
   * <p>By default, the notification shows {@link MediaMetadata#artist} as content text.
   *
   * @param metadata The media metadata from which content text is fetched.
   * @return Notification content text.
   */
  @Nullable
  protected CharSequence getNotificationContentText(MediaMetadata metadata) {
    return metadata.artist;
  }

  private void ensureNotificationChannel() {
    if (Util.SDK_INT < 26 || notificationManager.getNotificationChannel(channelId) != null) {
      return;
    }
    Api26.createNotificationChannel(
        notificationManager, channelId, context.getString(channelNameResourceId));
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
    private final int notificationId;
    private final NotificationCompat.Builder builder;
    private final Callback onNotificationChangedCallback;

    private boolean discarded;

    public OnBitmapLoadedFutureCallback(
        int notificationId,
        NotificationCompat.Builder builder,
        Callback onNotificationChangedCallback) {
      this.notificationId = notificationId;
      this.builder = builder;
      this.onNotificationChangedCallback = onNotificationChangedCallback;
    }

    public void discardIfPending() {
      discarded = true;
    }

    @Override
    public void onSuccess(Bitmap result) {
      if (!discarded) {
        builder.setLargeIcon(result);
        onNotificationChangedCallback.onNotificationChanged(
            new MediaNotification(notificationId, builder.build()));
      }
    }

    @Override
    public void onFailure(Throwable t) {
      if (!discarded) {
        Log.w(TAG, getBitmapLoadErrorMessage(t));
      }
    }
  }

  @RequiresApi(26)
  private static class Api26 {
    @DoNotInline
    public static void createNotificationChannel(
        NotificationManager notificationManager, String channelId, String channelName) {
      NotificationChannel channel =
          new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
      if (Util.SDK_INT <= 27) {
        // API 28+ will automatically hide the app icon 'badge' for notifications using
        // Notification.MediaStyle, but we have to manually hide it for APIs 26 (when badges were
        // added) and 27.
        channel.setShowBadge(false);
      }
      notificationManager.createNotificationChannel(channel);
    }
  }

  @RequiresApi(31)
  private static class Api31 {
    @DoNotInline
    public static void setForegroundServiceBehavior(NotificationCompat.Builder builder) {
      builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
    }
  }

  private static String getBitmapLoadErrorMessage(Throwable throwable) {
    return "Failed to load bitmap: " + throwable.getMessage();
  }
}
