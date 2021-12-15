/*
 * Copyright 2021 The Android Open Source Project
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

import static androidx.media3.common.Player.COMMAND_INVALID;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_SEEK_BACK;
import static androidx.media3.common.Player.COMMAND_SEEK_FORWARD;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS;
import static androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED;
import static androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYBACK_PARAMETERS_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY;
import static androidx.media3.common.Player.EVENT_REPEAT_MODE_CHANGED;
import static androidx.media3.common.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED;
import static androidx.media3.common.Player.EVENT_TIMELINE_CHANGED;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.BundleableUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NotificationUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Starts, updates and cancels a media style notification reflecting the player state. The actions
 * included in the notification can be customized along with their drawables, as described below.
 *
 * <p>The notification is cancelled when {@code null} is passed to {@link #setPlayer(Player)} or
 * when the notification is dismissed by the user.
 *
 * <p>If the player is released it must be removed from the manager by calling {@code
 * setPlayer(null)}.
 *
 * <h2>Overriding drawables</h2>
 *
 * The drawables used by PlayerNotificationManager can be overridden by drawables with the same
 * names defined in your application. The drawables that can be overridden are:
 *
 * <ul>
 *   <li><b>{@code media3_notification_small_icon}</b> - The icon passed by default to {@link
 *       NotificationCompat.Builder#setSmallIcon(int)}. A different icon can also be specified
 *       programmatically by calling {@link #setSmallIcon(int)}.
 *   <li><b>{@code media3_notification_play}</b> - The play icon.
 *   <li><b>{@code media3_notification_pause}</b> - The pause icon.
 *   <li><b>{@code media3_notification_rewind}</b> - The rewind icon.
 *   <li><b>{@code media3_notification_fastforward}</b> - The fast forward icon.
 *   <li><b>{@code media3_notification_previous}</b> - The previous icon.
 *   <li><b>{@code media3_notification_next}</b> - The next icon.
 * </ul>
 *
 * <p>Alternatively, the action icons can be set programatically by using the {@link Builder}.
 *
 * <p>Unlike the drawables above, the large icon (i.e. the icon passed to {@link
 * NotificationCompat.Builder#setLargeIcon(Bitmap)} cannot be overridden in this way. Instead, the
 * large icon is obtained from the {@link MediaDescriptionAdapter} passed to {@link
 * Builder#Builder(Context, int, String, MediaDescriptionAdapter)}.
 */
@UnstableApi
public class PlayerNotificationManager {

  /** An adapter to provide content assets of the media currently playing. */
  public interface MediaDescriptionAdapter {

    /**
     * Gets the content title for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setContentTitle(CharSequence)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     * @return The content title for the current media item.
     */
    CharSequence getCurrentContentTitle(Player player);

    /**
     * Creates a content intent for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setContentIntent(PendingIntent)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     * @return The content intent for the current media item, or null if no intent should be fired.
     */
    @Nullable
    PendingIntent createCurrentContentIntent(Player player);

    /**
     * Gets the content text for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setContentText(CharSequence)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     * @return The content text for the current media item, or null if no context text should be
     *     displayed.
     */
    @Nullable
    CharSequence getCurrentContentText(Player player);

    /**
     * Gets the content sub text for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setSubText(CharSequence)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     * @return The content subtext for the current media item, or null if no subtext should be
     *     displayed.
     */
    @Nullable
    default CharSequence getCurrentSubText(Player player) {
      return null;
    }

    /**
     * Gets the large icon for the current media item.
     *
     * <p>When a bitmap needs to be loaded asynchronously, a placeholder bitmap (or null) should be
     * returned. The actual bitmap should be passed to the {@link BitmapCallback} once it has been
     * loaded. Because the adapter may be called multiple times for the same media item, bitmaps
     * should be cached by the app and returned synchronously when possible.
     *
     * <p>See {@link NotificationCompat.Builder#setLargeIcon(Bitmap)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     * @param callback A {@link BitmapCallback} to provide a {@link Bitmap} asynchronously.
     * @return The large icon for the current media item, or null if the icon will be returned
     *     through the {@link BitmapCallback} or if no icon should be displayed.
     */
    @Nullable
    Bitmap getCurrentLargeIcon(Player player, BitmapCallback callback);
  }

  /** Defines and handles custom actions. */
  public interface CustomActionReceiver {

    /**
     * Gets the actions handled by this receiver.
     *
     * <p>If multiple {@link PlayerNotificationManager} instances are in use at the same time, the
     * {@code instanceId} must be set as an intent extra with key {@link
     * PlayerNotificationManager#INTENT_EXTRA_INSTANCE_ID} to avoid sending the action to every
     * custom action receiver. It's also necessary to ensure something is different about the
     * actions. This may be any of the {@link Intent} attributes considered by {@link
     * Intent#filterEquals}, or different request code integers when creating the {@link
     * PendingIntent}s with {@link PendingIntent#getBroadcast}. The easiest approach is to use the
     * {@code instanceId} as the request code.
     *
     * @param context The {@link Context}.
     * @param instanceId The instance id of the {@link PlayerNotificationManager}.
     * @return A map of custom actions.
     */
    Map<String, NotificationCompat.Action> createCustomActions(Context context, int instanceId);

    /**
     * Gets the actions to be included in the notification given the current player state.
     *
     * @param player The {@link Player} for which a notification is being built.
     * @return The actions to be included in the notification.
     */
    List<String> getCustomActions(Player player);

    /**
     * Called when a custom action has been received.
     *
     * @param player The player.
     * @param action The action from {@link Intent#getAction()}.
     * @param intent The received {@link Intent}.
     */
    void onCustomAction(Player player, String action, Intent intent);
  }

  /** A listener for changes to the notification. */
  public interface NotificationListener {

    /**
     * Called after the notification has been cancelled.
     *
     * @param notificationId The id of the notification which has been cancelled.
     * @param dismissedByUser {@code true} if the notification is cancelled because the user
     *     dismissed the notification.
     */
    default void onNotificationCancelled(int notificationId, boolean dismissedByUser) {}

    /**
     * Called each time after the notification has been posted.
     *
     * <p>For a service, the {@code ongoing} flag can be used as an indicator as to whether it
     * should be in the foreground.
     *
     * @param notificationId The id of the notification which has been posted.
     * @param notification The {@link Notification}.
     * @param ongoing Whether the notification is ongoing.
     */
    default void onNotificationPosted(
        int notificationId, Notification notification, boolean ongoing) {}
  }

  /** A builder for {@link PlayerNotificationManager} instances. */
  public static class Builder {

    protected final Context context;
    protected final int notificationId;
    protected final String channelId;

    @Nullable protected NotificationListener notificationListener;
    @Nullable protected CustomActionReceiver customActionReceiver;
    protected MediaDescriptionAdapter mediaDescriptionAdapter;
    protected int channelNameResourceId;
    protected int channelDescriptionResourceId;
    protected int channelImportance;
    protected int smallIconResourceId;
    @Nullable protected String groupKey;

    /**
     * @deprecated Use {@link #Builder(Context, int, String)} instead, then call {@link
     *     #setMediaDescriptionAdapter(MediaDescriptionAdapter)}.
     */
    @Deprecated
    public Builder(
        Context context,
        int notificationId,
        String channelId,
        MediaDescriptionAdapter mediaDescriptionAdapter) {
      this(context, notificationId, channelId);
      this.mediaDescriptionAdapter = mediaDescriptionAdapter;
    }

    /**
     * Creates an instance.
     *
     * @param context The {@link Context}.
     * @param notificationId The id of the notification to be posted. Must be greater than 0.
     * @param channelId The id of the notification channel.
     */
    public Builder(Context context, @IntRange(from = 1) int notificationId, String channelId) {
      checkArgument(notificationId > 0);
      this.context = context;
      this.notificationId = notificationId;
      this.channelId = channelId;
      channelImportance = NotificationUtil.IMPORTANCE_LOW;
      mediaDescriptionAdapter = new DefaultMediaDescriptionAdapter(/* pendingIntent= */ null);
      smallIconResourceId = R.drawable.media3_notification_small_icon;
    }

    /**
     * The name of the channel. If set to a value other than {@code 0}, the channel is automatically
     * created when {@link #build()} is called. If the application has already created the
     * notification channel, then this method should not be called.
     *
     * <p>The default is {@code 0}.
     *
     * @return This builder.
     */
    public Builder setChannelNameResourceId(int channelNameResourceId) {
      this.channelNameResourceId = channelNameResourceId;
      return this;
    }

    /**
     * The description of the channel. Ignored if {@link #setChannelNameResourceId(int)} is not
     * called with a value other than {@code 0}. If the application has already created the
     * notification channel, then this method should not be called.
     *
     * <p>The default is {@code 0}.
     *
     * @return This builder.
     */
    public Builder setChannelDescriptionResourceId(int channelDescriptionResourceId) {
      this.channelDescriptionResourceId = channelDescriptionResourceId;
      return this;
    }

    /**
     * The importance of the channel. Ignored if {@link #setChannelNameResourceId(int)} is not
     * called with a value other than {@code 0}. If the application has already created the
     * notification channel, then this method should not be called.
     *
     * <p>The default is {@link NotificationUtil#IMPORTANCE_LOW}.
     *
     * @return This builder.
     */
    public Builder setChannelImportance(@NotificationUtil.Importance int channelImportance) {
      this.channelImportance = channelImportance;
      return this;
    }

    /**
     * The {@link NotificationListener} to be used.
     *
     * <p>The default is {@code null}.
     *
     * @return This builder.
     */
    public Builder setNotificationListener(NotificationListener notificationListener) {
      this.notificationListener = notificationListener;
      return this;
    }

    /**
     * The {@link CustomActionReceiver} to be used.
     *
     * <p>The default is {@code null}.
     *
     * @return This builder.
     */
    public Builder setCustomActionReceiver(CustomActionReceiver customActionReceiver) {
      this.customActionReceiver = customActionReceiver;
      return this;
    }

    /**
     * The resource id of the small icon of the notification shown in the status bar. See {@link
     * NotificationCompat.Builder#setSmallIcon(int)}.
     *
     * <p>The default is {@code R.drawable#media3_notification_small_icon}.
     *
     * @return This builder.
     */
    public Builder setSmallIconResourceId(int smallIconResourceId) {
      this.smallIconResourceId = smallIconResourceId;
      return this;
    }

    /**
     * The key of the group the media notification should belong to.
     *
     * <p>The default is {@code null}
     *
     * @return This builder.
     */
    public Builder setGroup(String groupKey) {
      this.groupKey = groupKey;
      return this;
    }

    /**
     * The {@link MediaDescriptionAdapter} to be queried for the notification contents.
     *
     * <p>The default is {@link DefaultMediaDescriptionAdapter} with no {@link PendingIntent}
     *
     * @return This builder.
     */
    public Builder setMediaDescriptionAdapter(MediaDescriptionAdapter mediaDescriptionAdapter) {
      this.mediaDescriptionAdapter = mediaDescriptionAdapter;
      return this;
    }

    /** Builds the {@link PlayerNotificationManager}. */
    public PlayerNotificationManager build() {
      if (channelNameResourceId != 0) {
        NotificationUtil.createNotificationChannel(
            context,
            channelId,
            channelNameResourceId,
            channelDescriptionResourceId,
            channelImportance);
      }

      return new PlayerNotificationManager(
          context,
          channelId,
          notificationId,
          mediaDescriptionAdapter,
          notificationListener,
          customActionReceiver,
          smallIconResourceId,
          groupKey);
    }
  }

  /** Receives a {@link Bitmap}. */
  public final class BitmapCallback {
    private final int notificationTag;

    /** Create the receiver. */
    private BitmapCallback(int notificationTag) {
      this.notificationTag = notificationTag;
    }

    /**
     * Called when {@link Bitmap} is available.
     *
     * @param bitmap The bitmap to use as the large icon of the notification.
     */
    public void onBitmap(final Bitmap bitmap) {
      if (bitmap != null) {
        postUpdateNotificationBitmap(bitmap, notificationTag);
      }
    }
  }

  /** The action which is executed when a button in the notification is clicked. */
  private static final String INTENT_ACTION_COMMAND = "androidx.media3.session.command";

  /**
   * The action which is executed when the notification is dismissed. It cancels the notification
   * and calls {@link NotificationListener#onNotificationCancelled(int, boolean)}.
   */
  private static final String INTENT_ACTION_DISMISS =
      "androidx.media3.session.notification.dismiss";

  private static final String INTENT_EXTRA_PLAYER_COMMAND =
      "androidx.media3.session.EXTRA_PLAYER_COMMAND";
  private static final String INTENT_EXTRA_SESSION_COMMAND =
      "androidx.media3.session.EXTRA_SESSION_COMMAND";
  private static final String INTENT_EXTRA_INSTANCE_ID =
      "androidx.media3.session.notificaiton.EXTRA_INSTANCE_ID";
  private static final String INTENT_SCHEME = "media3";

  private static final int PENDING_INTENT_FLAGS =
      (Util.SDK_INT >= 23)
          ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
          : PendingIntent.FLAG_UPDATE_CURRENT;
  private static final String TAG = "NotificationManager";

  // Internal messages.

  private static final int MSG_START_OR_UPDATE_NOTIFICATION = 1;
  private static final int MSG_UPDATE_NOTIFICATION_BITMAP = 2;

  /**
   * Visibility of notification on the lock screen. One of {@link
   * NotificationCompat#VISIBILITY_PRIVATE}, {@link NotificationCompat#VISIBILITY_PUBLIC} or {@link
   * NotificationCompat#VISIBILITY_SECRET}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    NotificationCompat.VISIBILITY_PRIVATE,
    NotificationCompat.VISIBILITY_PUBLIC,
    NotificationCompat.VISIBILITY_SECRET
  })
  public @interface Visibility {}

  /**
   * Priority of the notification (required for API 25 and lower). One of {@link
   * NotificationCompat#PRIORITY_DEFAULT}, {@link NotificationCompat#PRIORITY_MAX}, {@link
   * NotificationCompat#PRIORITY_HIGH}, {@link NotificationCompat#PRIORITY_LOW }or {@link
   * NotificationCompat#PRIORITY_MIN}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    NotificationCompat.PRIORITY_DEFAULT,
    NotificationCompat.PRIORITY_MAX,
    NotificationCompat.PRIORITY_HIGH,
    NotificationCompat.PRIORITY_LOW,
    NotificationCompat.PRIORITY_MIN
  })
  public @interface Priority {}

  private static int instanceIdCounter;

  private final Context context;
  private final String channelId;
  private final int notificationId;
  private final MediaDescriptionAdapter mediaDescriptionAdapter;
  @Nullable private final NotificationListener notificationListener;
  @Nullable private final CustomActionReceiver customActionReceiver;
  private final Handler mainHandler;
  private final NotificationManagerCompat notificationManager;
  private final IntentFilter intentFilter;
  private final Player.Listener playerListener;
  private final NotificationBroadcastReceiver notificationBroadcastReceiver;
  private final Map<String, NotificationCompat.Action> customActions;
  private final PendingIntent dismissPendingIntent;
  private final int instanceId;
  private final CommandButton playButton;
  private final CommandButton pauseButton;
  private final CommandButton seekToPreviousButton;
  private final CommandButton seekToNextButton;
  private final CommandButton seekBackButton;
  private final CommandButton seekForwardButton;

  @Nullable private NotificationCompat.Builder builder;
  @Nullable private Player player;
  private boolean isNotificationStarted;
  private int currentNotificationTag;
  @Nullable private MediaSessionCompat.Token mediaSessionToken;
  private int badgeIconType;
  private boolean colorized;
  private int defaults;
  private int color;
  @DrawableRes private int smallIconResourceId;
  private int visibility;
  @Priority private int priority;
  private boolean useChronometer;
  @Nullable private String groupKey;

  protected PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener,
      @Nullable CustomActionReceiver customActionReceiver,
      int smallIconResourceId,
      @Nullable String groupKey) {
    context = context.getApplicationContext();
    this.context = context;
    this.channelId = channelId;
    this.notificationId = notificationId;
    this.mediaDescriptionAdapter = mediaDescriptionAdapter;
    this.notificationListener = notificationListener;
    this.customActionReceiver = customActionReceiver;
    this.smallIconResourceId = smallIconResourceId;
    this.groupKey = groupKey;
    instanceId = instanceIdCounter++;
    // This fails the nullness checker because handleMessage() is 'called' while `this` is still
    // @UnderInitialization. No tasks are scheduled on mainHandler before the constructor completes,
    // so this is safe and we can suppress the warning.
    @SuppressWarnings("nullness:methodref.receiver.bound")
    Handler mainHandler = Util.createHandler(Looper.getMainLooper(), this::handleMessage);
    this.mainHandler = mainHandler;
    notificationManager = NotificationManagerCompat.from(context);
    playerListener = new PlayerListener();
    notificationBroadcastReceiver = new NotificationBroadcastReceiver();
    intentFilter = new IntentFilter();
    colorized = true;
    useChronometer = true;
    color = Color.TRANSPARENT;
    defaults = 0;
    priority = NotificationCompat.PRIORITY_LOW;
    badgeIconType = NotificationCompat.BADGE_ICON_SMALL;
    visibility = NotificationCompat.VISIBILITY_PUBLIC;

    // initialize default buttons
    playButton =
        new CommandButton.Builder()
            .setDisplayName(context.getText(R.string.media3_controls_play_description))
            .setIconResId(R.drawable.media3_notification_play)
            .setPlayerCommand(COMMAND_PLAY_PAUSE)
            .build();
    pauseButton =
        new CommandButton.Builder()
            .setDisplayName(context.getText(R.string.media3_controls_pause_description))
            .setIconResId(R.drawable.media3_notification_pause)
            .setPlayerCommand(COMMAND_PLAY_PAUSE)
            .build();
    seekToPreviousButton =
        new CommandButton.Builder()
            .setDisplayName(context.getText(R.string.media3_controls_seek_to_previous_description))
            .setIconResId(R.drawable.media3_notification_seek_to_previous)
            .setPlayerCommand(COMMAND_SEEK_TO_PREVIOUS)
            .build();
    seekToNextButton =
        new CommandButton.Builder()
            .setDisplayName(context.getText(R.string.media3_controls_seek_to_next_description))
            .setIconResId(R.drawable.media3_notification_seek_to_next)
            .setPlayerCommand(COMMAND_SEEK_TO_NEXT)
            .build();
    seekBackButton =
        new CommandButton.Builder()
            .setDisplayName(context.getText(R.string.media3_controls_seek_back_description))
            .setIconResId(R.drawable.media3_notification_seek_back)
            .setPlayerCommand(COMMAND_SEEK_BACK)
            .build();
    seekForwardButton =
        new CommandButton.Builder()
            .setDisplayName(context.getText(R.string.media3_controls_seek_forward_description))
            .setIconResId(R.drawable.media3_notification_seek_forward)
            .setPlayerCommand(COMMAND_SEEK_FORWARD)
            .build();
    intentFilter.addAction(INTENT_ACTION_COMMAND);
    intentFilter.addAction(INTENT_ACTION_DISMISS);
    intentFilter.addDataScheme(INTENT_SCHEME);
    customActions =
        customActionReceiver != null
            ? customActionReceiver.createCustomActions(context, instanceId)
            : Collections.emptyMap();
    dismissPendingIntent = createBroadcastIntent(context, INTENT_ACTION_DISMISS, instanceId);
  }

  /**
   * Sets the {@link Player}.
   *
   * <p>Setting the player starts a notification immediately unless the player is in {@link
   * Player#STATE_IDLE}, in which case the notification is started as soon as the player transitions
   * away from being idle.
   *
   * <p>If the player is released it must be removed from the manager by calling {@code
   * setPlayer(null)}. This will cancel the notification.
   *
   * @param player The {@link Player} to use, or {@code null} to remove the current player. Only
   *     players which are accessed on the main thread are supported ({@code
   *     player.getApplicationLooper() == Looper.getMainLooper()}).
   */
  public final void setPlayer(@Nullable Player player) {
    checkState(Looper.myLooper() == Looper.getMainLooper());
    checkArgument(player == null || player.getApplicationLooper() == Looper.getMainLooper());
    if (this.player == player) {
      return;
    }
    if (this.player != null) {
      this.player.removeListener(playerListener);
      if (player == null) {
        stopNotification(/* dismissedByUser= */ false);
      }
    }
    this.player = player;
    if (player != null) {
      player.addListener(playerListener);
      postStartOrUpdateNotification();
    }
  }

  /**
   * Sets the {@link MediaSessionCompat.Token}.
   *
   * @param token The {@link MediaSessionCompat.Token}.
   */
  public final void setMediaSessionToken(MediaSessionCompat.Token token) {
    if (!Util.areEqual(this.mediaSessionToken, token)) {
      mediaSessionToken = token;
      invalidate();
    }
  }

  /**
   * Sets the badge icon type of the notification.
   *
   * <p>See {@link NotificationCompat.Builder#setBadgeIconType(int)}.
   *
   * @param badgeIconType The badge icon type.
   */
  public final void setBadgeIconType(@NotificationCompat.BadgeIconType int badgeIconType) {
    if (this.badgeIconType == badgeIconType) {
      return;
    }
    switch (badgeIconType) {
      case NotificationCompat.BADGE_ICON_NONE:
      case NotificationCompat.BADGE_ICON_SMALL:
      case NotificationCompat.BADGE_ICON_LARGE:
        this.badgeIconType = badgeIconType;
        break;
      default:
        throw new IllegalArgumentException();
    }
    invalidate();
  }

  /**
   * Sets whether the notification should be colorized. When set, the color set with {@link
   * #setColor(int)} will be used as the background color for the notification.
   *
   * <p>See {@link NotificationCompat.Builder#setColorized(boolean)}.
   *
   * @param colorized Whether to colorize the notification.
   */
  public final void setColorized(boolean colorized) {
    if (this.colorized != colorized) {
      this.colorized = colorized;
      invalidate();
    }
  }

  /**
   * Sets the defaults.
   *
   * <p>See {@link NotificationCompat.Builder#setDefaults(int)}.
   *
   * @param defaults The default notification options.
   */
  public final void setDefaults(int defaults) {
    if (this.defaults != defaults) {
      this.defaults = defaults;
      invalidate();
    }
  }

  /**
   * Sets the accent color of the notification.
   *
   * <p>See {@link NotificationCompat.Builder#setColor(int)}.
   *
   * @param color The color, in ARGB integer form like the constants in {@link Color}.
   */
  public final void setColor(int color) {
    if (this.color != color) {
      this.color = color;
      invalidate();
    }
  }

  /**
   * Sets the priority of the notification required for API 25 and lower.
   *
   * <p>See {@link NotificationCompat.Builder#setPriority(int)}.
   *
   * <p>To set the priority for API levels above 25, you can create your own {@link
   * NotificationChannel} with a given importance level and pass the id of the channel to {@link
   * Builder#Builder(Context, int, String, MediaDescriptionAdapter)}.
   *
   * @param priority The priority which can be one of {@link NotificationCompat#PRIORITY_DEFAULT},
   *     {@link NotificationCompat#PRIORITY_MAX}, {@link NotificationCompat#PRIORITY_HIGH}, {@link
   *     NotificationCompat#PRIORITY_LOW} or {@link NotificationCompat#PRIORITY_MIN}. If not set
   *     {@link NotificationCompat#PRIORITY_LOW} is used by default.
   */
  public final void setPriority(@Priority int priority) {
    if (this.priority == priority) {
      return;
    }
    switch (priority) {
      case NotificationCompat.PRIORITY_DEFAULT:
      case NotificationCompat.PRIORITY_MAX:
      case NotificationCompat.PRIORITY_HIGH:
      case NotificationCompat.PRIORITY_LOW:
      case NotificationCompat.PRIORITY_MIN:
        this.priority = priority;
        break;
      default:
        throw new IllegalArgumentException();
    }
    invalidate();
  }

  /**
   * Sets the small icon of the notification which is also shown in the system status bar.
   *
   * <p>See {@link NotificationCompat.Builder#setSmallIcon(int)}.
   *
   * @param smallIconResourceId The resource id of the small icon.
   */
  public final void setSmallIcon(@DrawableRes int smallIconResourceId) {
    if (this.smallIconResourceId != smallIconResourceId) {
      this.smallIconResourceId = smallIconResourceId;
      invalidate();
    }
  }

  /**
   * Sets whether the elapsed time of the media playback should be displayed.
   *
   * <p>Note that this setting only works if all of the following are true:
   *
   * <ul>
   *   <li>The media is {@link Player#isPlaying() actively playing}.
   *   <li>The media is not {@link Player#isCurrentWindowDynamic() dynamically changing its
   *       duration} (like for example a live stream).
   *   <li>The media is not {@link Player#isPlayingAd() interrupted by an ad}.
   *   <li>The media is played at {@link Player#getPlaybackParameters() regular speed}.
   *   <li>The device is running at least API 21 (Lollipop).
   * </ul>
   *
   * <p>See {@link NotificationCompat.Builder#setUsesChronometer(boolean)}.
   *
   * @param useChronometer Whether to use chronometer.
   */
  public final void setUseChronometer(boolean useChronometer) {
    if (this.useChronometer != useChronometer) {
      this.useChronometer = useChronometer;
      invalidate();
    }
  }

  /**
   * Sets the visibility of the notification which determines whether and how the notification is
   * shown when the device is in lock screen mode.
   *
   * <p>See {@link NotificationCompat.Builder#setVisibility(int)}.
   *
   * @param visibility The visibility which must be one of {@link
   *     NotificationCompat#VISIBILITY_PUBLIC}, {@link NotificationCompat#VISIBILITY_PRIVATE} or
   *     {@link NotificationCompat#VISIBILITY_SECRET}.
   */
  public final void setVisibility(@Visibility int visibility) {
    if (this.visibility == visibility) {
      return;
    }
    switch (visibility) {
      case NotificationCompat.VISIBILITY_PRIVATE:
      case NotificationCompat.VISIBILITY_PUBLIC:
      case NotificationCompat.VISIBILITY_SECRET:
        this.visibility = visibility;
        break;
      default:
        throw new IllegalStateException();
    }
    invalidate();
  }

  /** Forces an update of the notification if already started. */
  public final void invalidate() {
    if (isNotificationStarted) {
      postStartOrUpdateNotification();
    }
  }

  private void startOrUpdateNotification(Player player, @Nullable Bitmap bitmap) {
    boolean ongoing = getOngoing(player);
    builder = createNotification(player, builder, ongoing, bitmap);
    if (builder == null) {
      stopNotification(/* dismissedByUser= */ false);
      return;
    }
    Notification notification = builder.build();
    notificationManager.notify(notificationId, notification);
    if (!isNotificationStarted) {
      context.registerReceiver(notificationBroadcastReceiver, intentFilter);
    }
    if (notificationListener != null) {
      // Always pass true for ongoing with the first notification to tell a service to go into
      // foreground even when paused.
      notificationListener.onNotificationPosted(
          notificationId, notification, ongoing || !isNotificationStarted);
    }
    isNotificationStarted = true;
  }

  private void stopNotification(boolean dismissedByUser) {
    if (isNotificationStarted) {
      isNotificationStarted = false;
      mainHandler.removeMessages(MSG_START_OR_UPDATE_NOTIFICATION);
      notificationManager.cancel(notificationId);
      context.unregisterReceiver(notificationBroadcastReceiver);
      if (notificationListener != null) {
        notificationListener.onNotificationCancelled(notificationId, dismissedByUser);
      }
    }
  }

  /**
   * Creates the notification given the current player state.
   *
   * @param player The player for which state to build a notification.
   * @param builder The builder used to build the last notification, or {@code null}. Re-using the
   *     builder when possible can prevent notification flicker when {@code Util#SDK_INT} &lt; 21.
   * @param ongoing Whether the notification should be ongoing.
   * @param largeIcon The large icon to be used.
   * @return The {@link NotificationCompat.Builder} on which to call {@link
   *     NotificationCompat.Builder#build()} to obtain the notification, or {@code null} if no
   *     notification should be displayed.
   */
  @Nullable
  protected NotificationCompat.Builder createNotification(
      Player player,
      @Nullable NotificationCompat.Builder builder,
      boolean ongoing,
      @Nullable Bitmap largeIcon) {
    if (player.getPlaybackState() == Player.STATE_IDLE && player.getCurrentTimeline().isEmpty()) {
      return null;
    }

    if (builder == null) {
      builder = new NotificationCompat.Builder(context, channelId);
    }
    List<CommandButton> actionButtons = getActionButtons(player);
    for (int i = 0; i < actionButtons.size(); i++) {
      CommandButton button = actionButtons.get(i);
      NotificationCompat.Action action =
          new NotificationCompat.Action(
              button.iconResId,
              button.displayName,
              createBroadcastIntent(context, button, instanceId));
      builder.addAction(action);
    }

    MediaStyle mediaStyle = new MediaStyle();
    if (mediaSessionToken != null) {
      mediaStyle.setMediaSession(mediaSessionToken);
    }
    mediaStyle.setShowActionsInCompactView(
        getActionButtonIndicesForCompactView(actionButtons, player));
    // Configure dismiss action prior to API 21 ('x' button).
    mediaStyle.setShowCancelButton(!ongoing);
    mediaStyle.setCancelButtonIntent(dismissPendingIntent);
    builder.setStyle(mediaStyle);

    // Set intent which is sent if the user selects 'clear all'
    builder.setDeleteIntent(dismissPendingIntent);

    // Set notification properties from getters.
    builder
        .setBadgeIconType(badgeIconType)
        .setOngoing(ongoing)
        .setColor(color)
        .setColorized(colorized)
        .setSmallIcon(smallIconResourceId)
        .setVisibility(visibility)
        .setPriority(priority)
        .setDefaults(defaults);

    // Changing "showWhen" causes notification flicker if SDK_INT < 21.
    if (Util.SDK_INT >= 21
        && useChronometer
        && player.isPlaying()
        && !player.isPlayingAd()
        && !player.isCurrentWindowDynamic()
        && player.getPlaybackParameters().speed == 1f) {
      builder
          .setWhen(System.currentTimeMillis() - player.getContentPosition())
          .setShowWhen(true)
          .setUsesChronometer(true);
    } else {
      builder.setShowWhen(false).setUsesChronometer(false);
    }

    // Set media specific notification properties from MediaDescriptionAdapter.
    builder.setContentTitle(mediaDescriptionAdapter.getCurrentContentTitle(player));
    builder.setContentText(mediaDescriptionAdapter.getCurrentContentText(player));
    builder.setSubText(mediaDescriptionAdapter.getCurrentSubText(player));
    if (largeIcon == null) {
      largeIcon =
          mediaDescriptionAdapter.getCurrentLargeIcon(
              player, new BitmapCallback(++currentNotificationTag));
    }
    setLargeIcon(builder, largeIcon);
    builder.setContentIntent(mediaDescriptionAdapter.createCurrentContentIntent(player));

    if (groupKey != null) {
      builder.setGroup(groupKey);
    }

    builder.setOnlyAlertOnce(true);
    return builder;
  }

  /**
   * Gets the names and order of the actions to be included in the notification at the current
   * player state.
   *
   * <p>The playback and custom actions are combined and placed in the following order if not
   * omitted:
   *
   * <pre>
   *   +------------------------------------------------+
   *   | prev | &lt;&lt; | play/pause | &gt;&gt; | next |
   *   +------------------------------------------------+
   * </pre>
   *
   * <p>This method can be safely overridden.
   */
  // Disclaimer: Custom action support is temporarily removed, but will be added back in next CL.
  protected List<CommandButton> getActionButtons(Player player) {
    boolean enablePrevious = player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS);
    boolean enableRewind = player.isCommandAvailable(COMMAND_SEEK_BACK);
    boolean enableFastForward = player.isCommandAvailable(COMMAND_SEEK_FORWARD);
    boolean enableNext = player.isCommandAvailable(COMMAND_SEEK_TO_NEXT);

    List<CommandButton> buttons = new ArrayList<>();
    if (enablePrevious) {
      buttons.add(seekToPreviousButton);
    }
    if (enableRewind) {
      buttons.add(seekBackButton);
    }
    if (shouldShowPauseButton(player)) {
      buttons.add(pauseButton);
    } else {
      buttons.add(playButton);
    }
    if (enableFastForward) {
      buttons.add(seekForwardButton);
    }
    if (enableNext) {
      buttons.add(seekToNextButton);
    }
    return buttons;
  }

  /**
   * Gets an array with the indices of the buttons to be shown in compact mode.
   *
   * <p>This method can be overridden. The indices must refer to the list of actions passed as the
   * first parameter.
   *
   * @param actionButtons The buttons of the actions included in the notification.
   * @param player The player for which a notification is being built.
   */
  @SuppressWarnings("unused")
  protected int[] getActionButtonIndicesForCompactView(
      List<CommandButton> actionButtons, Player player) {
    int previousIndex = C.INDEX_UNSET;
    int nextIndex = C.INDEX_UNSET;
    int playPauseIndex = C.INDEX_UNSET;
    for (int i = 0; i < actionButtons.size(); i++) {
      CommandButton button = actionButtons.get(i);
      switch (button.playerCommand) {
        case COMMAND_PLAY_PAUSE:
          playPauseIndex = i;
          break;
        case COMMAND_SEEK_TO_PREVIOUS:
          previousIndex = i;
          break;
        case COMMAND_SEEK_TO_NEXT:
          nextIndex = i;
          break;
        default:
          // Do nothing
      }
    }
    int[] actionIndices = new int[3];
    int actionCounter = 0;
    if (previousIndex != C.INDEX_UNSET) {
      actionIndices[actionCounter++] = previousIndex;
    }
    if (playPauseIndex != C.INDEX_UNSET) {
      actionIndices[actionCounter++] = playPauseIndex;
    }
    if (nextIndex != C.INDEX_UNSET) {
      actionIndices[actionCounter++] = nextIndex;
    }
    return Arrays.copyOf(actionIndices, actionCounter);
  }

  /** Returns whether the generated notification should be ongoing. */
  protected boolean getOngoing(Player player) {
    int playbackState = player.getPlaybackState();
    return (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)
        && player.getPlayWhenReady();
  }

  private boolean shouldShowPauseButton(Player player) {
    return player.getPlaybackState() != Player.STATE_ENDED
        && player.getPlaybackState() != Player.STATE_IDLE
        && player.getPlayWhenReady();
  }

  private void postStartOrUpdateNotification() {
    if (!mainHandler.hasMessages(MSG_START_OR_UPDATE_NOTIFICATION)) {
      mainHandler.sendEmptyMessage(MSG_START_OR_UPDATE_NOTIFICATION);
    }
  }

  private void postUpdateNotificationBitmap(Bitmap bitmap, int notificationTag) {
    mainHandler
        .obtainMessage(
            MSG_UPDATE_NOTIFICATION_BITMAP, notificationTag, C.INDEX_UNSET /* ignored */, bitmap)
        .sendToTarget();
  }

  private boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_START_OR_UPDATE_NOTIFICATION:
        if (player != null) {
          startOrUpdateNotification(player, /* bitmap= */ null);
        }
        break;
      case MSG_UPDATE_NOTIFICATION_BITMAP:
        if (player != null && isNotificationStarted && currentNotificationTag == msg.arg1) {
          startOrUpdateNotification(player, (Bitmap) msg.obj);
        }
        break;
      default:
        return false;
    }
    return true;
  }

  private static PendingIntent createBroadcastIntent(
      Context context, CommandButton button, int instanceId) {
    Intent intent = new Intent(INTENT_ACTION_COMMAND).setPackage(context.getPackageName());
    intent.putExtra(INTENT_EXTRA_INSTANCE_ID, instanceId);
    intent.putExtra(INTENT_EXTRA_PLAYER_COMMAND, button.playerCommand);
    intent.putExtra(
        INTENT_EXTRA_SESSION_COMMAND, BundleableUtil.toNullableBundle(button.sessionCommand));
    // Make intent distinguishable by Intent#filterEquals() due to the PendingIntent requirement.
    Uri intentUri =
        new Uri.Builder()
            .scheme(INTENT_SCHEME)
            .appendPath(Integer.toString(instanceId))
            .appendPath(Integer.toString(button.playerCommand))
            .appendPath(button.sessionCommand == null ? "null" : button.sessionCommand.customAction)
            .build();
    intent.setData(intentUri);
    return PendingIntent.getBroadcast(context, instanceId, intent, PENDING_INTENT_FLAGS);
  }

  private static PendingIntent createBroadcastIntent(
      Context context, String action, int instanceId) {
    Intent intent = new Intent(action).setPackage(context.getPackageName());
    intent.putExtra(INTENT_EXTRA_INSTANCE_ID, instanceId);
    return PendingIntent.getBroadcast(context, instanceId, intent, PENDING_INTENT_FLAGS);
  }

  @SuppressWarnings("nullness:argument")
  private static void setLargeIcon(NotificationCompat.Builder builder, @Nullable Bitmap largeIcon) {
    builder.setLargeIcon(largeIcon);
  }

  private class PlayerListener implements Player.Listener {

    @Override
    public void onEvents(Player player, Player.Events events) {
      if (events.containsAny(
          EVENT_PLAYBACK_STATE_CHANGED,
          EVENT_PLAY_WHEN_READY_CHANGED,
          EVENT_IS_PLAYING_CHANGED,
          EVENT_TIMELINE_CHANGED,
          EVENT_PLAYBACK_PARAMETERS_CHANGED,
          EVENT_POSITION_DISCONTINUITY,
          EVENT_REPEAT_MODE_CHANGED,
          EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          EVENT_MEDIA_METADATA_CHANGED)) {
        postStartOrUpdateNotification();
      }
    }
  }

  private class NotificationBroadcastReceiver extends BroadcastReceiver {

    @SuppressWarnings("deprecation")
    @Override
    public void onReceive(Context context, Intent intent) {
      Player player = PlayerNotificationManager.this.player;
      if (player == null
          || !isNotificationStarted
          || intent.getIntExtra(INTENT_EXTRA_INSTANCE_ID, instanceId) != instanceId) {
        return;
      }
      String action = intent.getAction();
      if (INTENT_ACTION_COMMAND.equals(action)) {
        @Player.Command
        int playerCommand = intent.getIntExtra(INTENT_EXTRA_PLAYER_COMMAND, COMMAND_INVALID);
        switch (playerCommand) {
          case COMMAND_PLAY_PAUSE:
            if (!player.getPlayWhenReady()) {
              if (player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare();
              } else if (player.getPlaybackState() == Player.STATE_ENDED) {
                player.seekToDefaultPosition(player.getCurrentWindowIndex());
              }
            } else {
              player.pause();
            }
            break;
          case COMMAND_SEEK_TO_PREVIOUS:
            player.seekToPrevious();
            break;
          case COMMAND_SEEK_BACK:
            player.seekBack();
            break;
          case COMMAND_SEEK_FORWARD:
            player.seekForward();
            break;
          case COMMAND_SEEK_TO_NEXT:
            player.seekToNext();
            break;
          default:
            Log.w(TAG, "Unsupported player command, playerCommand=" + playerCommand);
            break;
        }
      } else if (INTENT_ACTION_DISMISS.equals(action)) {
        stopNotification(/* dismissedByUser= */ true);
      } else if (action != null
          && customActionReceiver != null
          && customActions.containsKey(action)) {
        customActionReceiver.onCustomAction(player, action, intent);
      }
    }
  }
}
