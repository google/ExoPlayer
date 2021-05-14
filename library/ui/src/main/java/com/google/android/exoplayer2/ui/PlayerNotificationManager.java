/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ui;

import static com.google.android.exoplayer2.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.EVENT_IS_PLAYING_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAYBACK_PARAMETERS_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_POSITION_DISCONTINUITY;
import static com.google.android.exoplayer2.Player.EVENT_REPEAT_MODE_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_TIMELINE_CHANGED;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
 * <h3>Action customization</h3>
 *
 * Playback actions can be included or omitted as follows:
 *
 * <ul>
 *   <li><b>{@code usePlayPauseActions}</b> - Sets whether the play and pause actions are used.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUsePlayPauseActions(boolean)}
 *         <li>Default: {@code true}
 *       </ul>
 *   <li><b>{@code rewindIncrementMs}</b> - Sets the rewind increment. If set to zero the rewind
 *       action is not used.
 *       <ul>
 *         <li>Corresponding setter: {@link #setControlDispatcher(ControlDispatcher)}
 *         <li>Default: {@link DefaultControlDispatcher#DEFAULT_REWIND_MS} (5000)
 *       </ul>
 *   <li><b>{@code fastForwardIncrementMs}</b> - Sets the fast forward increment. If set to zero the
 *       fast forward action is not used.
 *       <ul>
 *         <li>Corresponding setter: {@link #setControlDispatcher(ControlDispatcher)}
 *         <li>Default: {@link DefaultControlDispatcher#DEFAULT_FAST_FORWARD_MS} (15000)
 *       </ul>
 *   <li><b>{@code usePreviousAction}</b> - Whether the previous action is used.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUsePreviousAction(boolean)}
 *         <li>Default: {@code true}
 *       </ul>
 *   <li><b>{@code usePreviousActionInCompactView}</b> - If {@code usePreviousAction} is {@code
 *       true}, sets whether the previous action is also used in compact view (including the lock
 *       screen notification). Else does nothing.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUsePreviousActionInCompactView(boolean)}
 *         <li>Default: {@code false}
 *       </ul>
 *   <li><b>{@code useNextAction}</b> - Whether the next action is used.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUseNextAction(boolean)}
 *         <li>Default: {@code true}
 *       </ul>
 *   <li><b>{@code useNextActionInCompactView}</b> - If {@code useNextAction} is {@code true}, sets
 *       whether the next action is also used in compact view (including the lock screen
 *       notification). Else does nothing.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUseNextActionInCompactView(boolean)}
 *         <li>Default: {@code false}
 *       </ul>
 *   <li><b>{@code useStopAction}</b> - Sets whether the stop action is used.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUseStopAction(boolean)}
 *         <li>Default: {@code false}
 *       </ul>
 * </ul>
 *
 * <h3>Overriding drawables</h3>
 *
 * The drawables used by PlayerNotificationManager can be overridden by drawables with the same
 * names defined in your application. The drawables that can be overridden are:
 *
 * <ul>
 *   <li><b>{@code exo_notification_small_icon}</b> - The icon passed by default to {@link
 *       NotificationCompat.Builder#setSmallIcon(int)}. A different icon can also be specified
 *       programmatically by calling {@link #setSmallIcon(int)}.
 *   <li><b>{@code exo_notification_play}</b> - The play icon.
 *   <li><b>{@code exo_notification_pause}</b> - The pause icon.
 *   <li><b>{@code exo_notification_rewind}</b> - The rewind icon.
 *   <li><b>{@code exo_notification_fastforward}</b> - The fast forward icon.
 *   <li><b>{@code exo_notification_previous}</b> - The previous icon.
 *   <li><b>{@code exo_notification_next}</b> - The next icon.
 *   <li><b>{@code exo_notification_stop}</b> - The stop icon.
 * </ul>
 *
 * <p>Alternatively, the action icons can be set programatically by using the {@link Builder}.
 *
 * <p>Unlike the drawables above, the large icon (i.e. the icon passed to {@link
 * NotificationCompat.Builder#setLargeIcon(Bitmap)} cannot be overridden in this way. Instead, the
 * large icon is obtained from the {@link MediaDescriptionAdapter} passed to {@link
 * Builder#Builder(Context, int, String, MediaDescriptionAdapter)}.
 */
public class PlayerNotificationManager {

  /** An adapter to provide content assets of the media currently playing. */
  public interface MediaDescriptionAdapter {

    /**
     * Gets the content title for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setContentTitle(CharSequence)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     */
    CharSequence getCurrentContentTitle(Player player);

    /**
     * Creates a content intent for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setContentIntent(PendingIntent)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     */
    @Nullable
    PendingIntent createCurrentContentIntent(Player player);

    /**
     * Gets the content text for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setContentText(CharSequence)}.
     *
     * @param player The {@link Player} for which a notification is being built.
     */
    @Nullable
    CharSequence getCurrentContentText(Player player);

    /**
     * Gets the content sub text for the current media item.
     *
     * <p>See {@link NotificationCompat.Builder#setSubText(CharSequence)}.
     *
     * @param player The {@link Player} for which a notification is being built.
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
     * PlayerNotificationManager#EXTRA_INSTANCE_ID} to avoid sending the action to every custom
     * action receiver. It's also necessary to ensure something is different about the actions. This
     * may be any of the {@link Intent} attributes considered by {@link Intent#filterEquals}, or
     * different request code integers when creating the {@link PendingIntent}s with {@link
     * PendingIntent#getBroadcast}. The easiest approach is to use the {@code instanceId} as the
     * request code.
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

    private final Context context;
    private final int notificationId;
    private final String channelId;
    private final MediaDescriptionAdapter mediaDescriptionAdapter;

    @Nullable private NotificationListener notificationListener;
    @Nullable private CustomActionReceiver customActionReceiver;
    private int channelNameResourceId;
    private int channelDescriptionResourceId;
    private int channelImportance;
    private int smallIconResourceId;
    private int rewindActionIconResourceId;
    private int playActionIconResourceId;
    private int pauseActionIconResourceId;
    private int stopActionIconResourceId;
    private int fastForwardActionIconResourceId;
    private int previousActionIconResourceId;
    private int nextActionIconResourceId;
    @Nullable private String groupKey;

    /**
     * Creates an instance.
     *
     * @param context The {@link Context}.
     * @param notificationId The id of the notification to be posted. Must be greater than 0.
     * @param channelId The id of the notification channel.
     * @param mediaDescriptionAdapter The {@link MediaDescriptionAdapter} to be used.
     */
    public Builder(
        Context context,
        int notificationId,
        String channelId,
        MediaDescriptionAdapter mediaDescriptionAdapter) {
      checkArgument(notificationId > 0);
      this.context = context;
      this.notificationId = notificationId;
      this.channelId = channelId;
      this.mediaDescriptionAdapter = mediaDescriptionAdapter;
      channelImportance = NotificationUtil.IMPORTANCE_LOW;
      smallIconResourceId = R.drawable.exo_notification_small_icon;
      playActionIconResourceId = R.drawable.exo_notification_play;
      pauseActionIconResourceId = R.drawable.exo_notification_pause;
      stopActionIconResourceId = R.drawable.exo_notification_stop;
      rewindActionIconResourceId = R.drawable.exo_notification_rewind;
      fastForwardActionIconResourceId = R.drawable.exo_notification_fastforward;
      previousActionIconResourceId = R.drawable.exo_notification_previous;
      nextActionIconResourceId = R.drawable.exo_notification_next;
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
     * <p>The default is {@code R.drawable#exo_notification_small_icon}.
     *
     * @return This builder.
     */
    public Builder setSmallIconResourceId(int smallIconResourceId) {
      this.smallIconResourceId = smallIconResourceId;
      return this;
    }

    /**
     * The resource id of the drawable to be used as the icon of action {@link #ACTION_PLAY}.
     *
     * <p>The default is {@code R.drawable#exo_notification_play}.
     *
     * @return This builder.
     */
    public Builder setPlayActionIconResourceId(int playActionIconResourceId) {
      this.playActionIconResourceId = playActionIconResourceId;
      return this;
    }

    /**
     * The resource id of the drawable to be used as the icon of action {@link #ACTION_PAUSE}.
     *
     * <p>The default is {@code R.drawable#exo_notification_pause}.
     *
     * @return This builder.
     */
    public Builder setPauseActionIconResourceId(int pauseActionIconResourceId) {
      this.pauseActionIconResourceId = pauseActionIconResourceId;
      return this;
    }

    /**
     * The resource id of the drawable to be used as the icon of action {@link #ACTION_STOP}.
     *
     * <p>The default is {@code R.drawable#exo_notification_stop}.
     *
     * @return This builder.
     */
    public Builder setStopActionIconResourceId(int stopActionIconResourceId) {
      this.stopActionIconResourceId = stopActionIconResourceId;
      return this;
    }

    /**
     * The resource id of the drawable to be used as the icon of action {@link #ACTION_REWIND}.
     *
     * <p>The default is {@code R.drawable#exo_notification_rewind}.
     *
     * @return This builder.
     */
    public Builder setRewindActionIconResourceId(int rewindActionIconResourceId) {
      this.rewindActionIconResourceId = rewindActionIconResourceId;
      return this;
    }

    /**
     * The resource id of the drawable to be used as the icon of action {@link
     * #ACTION_FAST_FORWARD}.
     *
     * <p>The default is {@code R.drawable#exo_notification_fastforward}.
     *
     * @return This builder.
     */
    public Builder setFastForwardActionIconResourceId(int fastForwardActionIconResourceId) {
      this.fastForwardActionIconResourceId = fastForwardActionIconResourceId;
      return this;
    }

    /**
     * The resource id of the drawable to be used as the icon of action {@link #ACTION_PREVIOUS}.
     *
     * <p>The default is {@code R.drawable#exo_notification_previous}.
     *
     * @return This builder.
     */
    public Builder setPreviousActionIconResourceId(int previousActionIconResourceId) {
      this.previousActionIconResourceId = previousActionIconResourceId;
      return this;
    }

    /**
     * The resource id of the drawable to be used as the icon of action {@link #ACTION_NEXT}.
     *
     * <p>The default is {@code R.drawable#exo_notification_next}.
     *
     * @return This builder.
     */
    public Builder setNextActionIconResourceId(int nextActionIconResourceId) {
      this.nextActionIconResourceId = nextActionIconResourceId;
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
          playActionIconResourceId,
          pauseActionIconResourceId,
          stopActionIconResourceId,
          rewindActionIconResourceId,
          fastForwardActionIconResourceId,
          previousActionIconResourceId,
          nextActionIconResourceId,
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

  /** The action which starts playback. */
  public static final String ACTION_PLAY = "com.google.android.exoplayer.play";
  /** The action which pauses playback. */
  public static final String ACTION_PAUSE = "com.google.android.exoplayer.pause";
  /** The action which skips to the previous window. */
  public static final String ACTION_PREVIOUS = "com.google.android.exoplayer.prev";
  /** The action which skips to the next window. */
  public static final String ACTION_NEXT = "com.google.android.exoplayer.next";
  /** The action which fast forwards. */
  public static final String ACTION_FAST_FORWARD = "com.google.android.exoplayer.ffwd";
  /** The action which rewinds. */
  public static final String ACTION_REWIND = "com.google.android.exoplayer.rewind";
  /** The action which stops playback. */
  public static final String ACTION_STOP = "com.google.android.exoplayer.stop";
  /** The extra key of the instance id of the player notification manager. */
  public static final String EXTRA_INSTANCE_ID = "INSTANCE_ID";
  /**
   * The action which is executed when the notification is dismissed. It cancels the notification
   * and calls {@link NotificationListener#onNotificationCancelled(int, boolean)}.
   */
  private static final String ACTION_DISMISS = "com.google.android.exoplayer.dismiss";

  // Internal messages.

  private static final int MSG_START_OR_UPDATE_NOTIFICATION = 0;
  private static final int MSG_UPDATE_NOTIFICATION_BITMAP = 1;

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
  private final Player.EventListener playerListener;
  private final NotificationBroadcastReceiver notificationBroadcastReceiver;
  private final Map<String, NotificationCompat.Action> playbackActions;
  private final Map<String, NotificationCompat.Action> customActions;
  private final PendingIntent dismissPendingIntent;
  private final int instanceId;
  private final Timeline.Window window;

  @Nullable private NotificationCompat.Builder builder;
  @Nullable private List<NotificationCompat.Action> builderActions;
  @Nullable private Player player;
  @Nullable private PlaybackPreparer playbackPreparer;
  private ControlDispatcher controlDispatcher;
  private boolean isNotificationStarted;
  private int currentNotificationTag;
  @Nullable private MediaSessionCompat.Token mediaSessionToken;
  private boolean usePreviousAction;
  private boolean useNextAction;
  private boolean usePreviousActionInCompactView;
  private boolean useNextActionInCompactView;
  private boolean usePlayPauseActions;
  private boolean useStopAction;
  private int badgeIconType;
  private boolean colorized;
  private int defaults;
  private int color;
  @DrawableRes private int smallIconResourceId;
  private int visibility;
  @Priority private int priority;
  private boolean useChronometer;
  @Nullable private String groupKey;

  /** @deprecated Use the {@link Builder} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public static PlayerNotificationManager createWithNotificationChannel(
      Context context,
      String channelId,
      @StringRes int channelName,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter) {
    return createWithNotificationChannel(
        context,
        channelId,
        channelName,
        /* channelDescription= */ 0,
        notificationId,
        mediaDescriptionAdapter);
  }

  /** @deprecated Use the {@link Builder} instead. */
  @Deprecated
  public static PlayerNotificationManager createWithNotificationChannel(
      Context context,
      String channelId,
      @StringRes int channelName,
      @StringRes int channelDescription,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter) {
    NotificationUtil.createNotificationChannel(
        context, channelId, channelName, channelDescription, NotificationUtil.IMPORTANCE_LOW);
    return new PlayerNotificationManager(
        context, channelId, notificationId, mediaDescriptionAdapter);
  }

  /** @deprecated Use the {@link Builder} instead. */
  @Deprecated
  public static PlayerNotificationManager createWithNotificationChannel(
      Context context,
      String channelId,
      @StringRes int channelName,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener) {
    return createWithNotificationChannel(
        context,
        channelId,
        channelName,
        /* channelDescription= */ 0,
        notificationId,
        mediaDescriptionAdapter,
        notificationListener);
  }

  /** @deprecated Use the {@link Builder} instead. */
  @Deprecated
  public static PlayerNotificationManager createWithNotificationChannel(
      Context context,
      String channelId,
      @StringRes int channelName,
      @StringRes int channelDescription,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener) {
    NotificationUtil.createNotificationChannel(
        context, channelId, channelName, channelDescription, NotificationUtil.IMPORTANCE_LOW);
    return new PlayerNotificationManager(
        context, channelId, notificationId, mediaDescriptionAdapter, notificationListener);
  }

  /** @deprecated Use the {@link Builder} instead. */
  @Deprecated
  public PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter) {
    this(
        context,
        channelId,
        notificationId,
        mediaDescriptionAdapter,
        /* notificationListener= */ null,
        /* customActionReceiver */ null);
  }

  /** @deprecated Use the {@link Builder} instead. */
  @Deprecated
  public PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener) {
    this(
        context,
        channelId,
        notificationId,
        mediaDescriptionAdapter,
        notificationListener,
        /* customActionReceiver= */ null);
  }

  /** @deprecated Use the {@link Builder} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable CustomActionReceiver customActionReceiver) {
    this(
        context,
        channelId,
        notificationId,
        mediaDescriptionAdapter,
        /* notificationListener= */ null,
        customActionReceiver);
  }

  /** @deprecated Use the {@link Builder} instead. */
  @Deprecated
  public PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener,
      @Nullable CustomActionReceiver customActionReceiver) {
    this(
        context,
        channelId,
        notificationId,
        mediaDescriptionAdapter,
        notificationListener,
        customActionReceiver,
        R.drawable.exo_notification_small_icon,
        R.drawable.exo_notification_play,
        R.drawable.exo_notification_pause,
        R.drawable.exo_notification_stop,
        R.drawable.exo_notification_rewind,
        R.drawable.exo_notification_fastforward,
        R.drawable.exo_notification_previous,
        R.drawable.exo_notification_next,
        null);
  }

  private PlayerNotificationManager(
      Context context,
      String channelId,
      int notificationId,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      @Nullable NotificationListener notificationListener,
      @Nullable CustomActionReceiver customActionReceiver,
      int smallIconResourceId,
      int playActionIconResourceId,
      int pauseActionIconResourceId,
      int stopActionIconResourceId,
      int rewindActionIconResourceId,
      int fastForwardActionIconResourceId,
      int previousActionIconResourceId,
      int nextActionIconResourceId,
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
    controlDispatcher = new DefaultControlDispatcher();
    window = new Timeline.Window();
    instanceId = instanceIdCounter++;
    // This fails the nullness checker because handleMessage() is 'called' while `this` is still
    // @UnderInitialization. No tasks are scheduled on mainHandler before the constructor completes,
    // so this is safe and we can suppress the warning.
    @SuppressWarnings("nullness:methodref.receiver.bound.invalid")
    Handler mainHandler = Util.createHandler(Looper.getMainLooper(), this::handleMessage);
    this.mainHandler = mainHandler;
    notificationManager = NotificationManagerCompat.from(context);
    playerListener = new PlayerListener();
    notificationBroadcastReceiver = new NotificationBroadcastReceiver();
    intentFilter = new IntentFilter();
    usePreviousAction = true;
    useNextAction = true;
    usePlayPauseActions = true;
    colorized = true;
    useChronometer = true;
    color = Color.TRANSPARENT;
    defaults = 0;
    priority = NotificationCompat.PRIORITY_LOW;
    badgeIconType = NotificationCompat.BADGE_ICON_SMALL;
    visibility = NotificationCompat.VISIBILITY_PUBLIC;

    // initialize actions
    playbackActions =
        createPlaybackActions(
            context,
            instanceId,
            playActionIconResourceId,
            pauseActionIconResourceId,
            stopActionIconResourceId,
            rewindActionIconResourceId,
            fastForwardActionIconResourceId,
            previousActionIconResourceId,
            nextActionIconResourceId);
    for (String action : playbackActions.keySet()) {
      intentFilter.addAction(action);
    }
    customActions =
        customActionReceiver != null
            ? customActionReceiver.createCustomActions(context, instanceId)
            : Collections.emptyMap();
    for (String action : customActions.keySet()) {
      intentFilter.addAction(action);
    }
    dismissPendingIntent = createBroadcastIntent(ACTION_DISMISS, context, instanceId);
    intentFilter.addAction(ACTION_DISMISS);
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
   * @deprecated Use {@link #setControlDispatcher(ControlDispatcher)} instead. The manager calls
   *     {@link ControlDispatcher#dispatchPrepare(Player)} instead of {@link
   *     PlaybackPreparer#preparePlayback()}. The {@link DefaultControlDispatcher} that this manager
   *     uses by default, calls {@link Player#prepare()}. If you wish to intercept or customize this
   *     behaviour, you can provide a custom implementation of {@link
   *     ControlDispatcher#dispatchPrepare(Player)} and pass it to {@link
   *     #setControlDispatcher(ControlDispatcher)}.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public void setPlaybackPreparer(@Nullable PlaybackPreparer playbackPreparer) {
    this.playbackPreparer = playbackPreparer;
  }

  /**
   * Sets the {@link ControlDispatcher}.
   *
   * @param controlDispatcher The {@link ControlDispatcher}.
   */
  public final void setControlDispatcher(ControlDispatcher controlDispatcher) {
    if (this.controlDispatcher != controlDispatcher) {
      this.controlDispatcher = controlDispatcher;
      invalidate();
    }
  }

  /**
   * @deprecated Use {@link #setControlDispatcher(ControlDispatcher)} with {@link
   *     DefaultControlDispatcher#DefaultControlDispatcher(long, long)}.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public final void setFastForwardIncrementMs(long fastForwardMs) {
    if (controlDispatcher instanceof DefaultControlDispatcher) {
      ((DefaultControlDispatcher) controlDispatcher).setFastForwardIncrementMs(fastForwardMs);
      invalidate();
    }
  }

  /**
   * @deprecated Use {@link #setControlDispatcher(ControlDispatcher)} with {@link
   *     DefaultControlDispatcher#DefaultControlDispatcher(long, long)}.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public final void setRewindIncrementMs(long rewindMs) {
    if (controlDispatcher instanceof DefaultControlDispatcher) {
      ((DefaultControlDispatcher) controlDispatcher).setRewindIncrementMs(rewindMs);
      invalidate();
    }
  }

  /**
   * Sets whether the next action should be used.
   *
   * @param useNextAction Whether to use the next action.
   */
  public void setUseNextAction(boolean useNextAction) {
    if (this.useNextAction != useNextAction) {
      this.useNextAction = useNextAction;
      invalidate();
    }
  }

  /**
   * Sets whether the previous action should be used.
   *
   * @param usePreviousAction Whether to use the previous action.
   */
  public void setUsePreviousAction(boolean usePreviousAction) {
    if (this.usePreviousAction != usePreviousAction) {
      this.usePreviousAction = usePreviousAction;
      invalidate();
    }
  }

  /**
   * Sets whether the navigation actions should be used.
   *
   * @param useNavigationActions Whether to use navigation actions.
   * @deprecated Use {@link #setUseNextAction(boolean)} and {@link #setUsePreviousAction(boolean)}.
   */
  @Deprecated
  public final void setUseNavigationActions(boolean useNavigationActions) {
    setUseNextAction(useNavigationActions);
    setUsePreviousAction(useNavigationActions);
  }

  /**
   * If {@link #setUseNextAction useNextAction} is {@code true}, sets whether the next action should
   * also be used in compact view. Has no effect if {@link #setUseNextAction useNextAction} is
   * {@code false}.
   *
   * @param useNextActionInCompactView Whether to use the next action in compact view.
   */
  public void setUseNextActionInCompactView(boolean useNextActionInCompactView) {
    if (this.useNextActionInCompactView != useNextActionInCompactView) {
      this.useNextActionInCompactView = useNextActionInCompactView;
      invalidate();
    }
  }

  /**
   * If {@link #setUsePreviousAction usePreviousAction} is {@code true}, sets whether the previous
   * action should also be used in compact view. Has no effect if {@link #setUsePreviousAction
   * usePreviousAction} is {@code false}.
   *
   * @param usePreviousActionInCompactView Whether to use the previous action in compact view.
   */
  public void setUsePreviousActionInCompactView(boolean usePreviousActionInCompactView) {
    if (this.usePreviousActionInCompactView != usePreviousActionInCompactView) {
      this.usePreviousActionInCompactView = usePreviousActionInCompactView;
      invalidate();
    }
  }

  /**
   * If {@link #setUseNavigationActions useNavigationActions} is {@code true}, sets whether
   * navigation actions should also be used in compact view. Has no effect if {@link
   * #setUseNavigationActions useNavigationActions} is {@code false}.
   *
   * @param useNavigationActionsInCompactView Whether to use navigation actions in compact view.
   * @deprecated Use {@link #setUseNextActionInCompactView(boolean)} and {@link
   *     #setUsePreviousActionInCompactView(boolean)} instead.
   */
  @Deprecated
  public final void setUseNavigationActionsInCompactView(
      boolean useNavigationActionsInCompactView) {
    setUseNextActionInCompactView(useNavigationActionsInCompactView);
    setUsePreviousActionInCompactView(useNavigationActionsInCompactView);
  }

  /**
   * Sets whether the play and pause actions should be used.
   *
   * @param usePlayPauseActions Whether to use play and pause actions.
   */
  public final void setUsePlayPauseActions(boolean usePlayPauseActions) {
    if (this.usePlayPauseActions != usePlayPauseActions) {
      this.usePlayPauseActions = usePlayPauseActions;
      invalidate();
    }
  }

  /**
   * Sets whether the stop action should be used.
   *
   * @param useStopAction Whether to use the stop action.
   */
  public final void setUseStopAction(boolean useStopAction) {
    if (this.useStopAction == useStopAction) {
      return;
    }
    this.useStopAction = useStopAction;
    invalidate();
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
   * NotificationChannel} with a given importance level and pass the id of the channel to the {@link
   * #PlayerNotificationManager(Context, String, int, MediaDescriptionAdapter) constructor}.
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
  public void invalidate() {
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
      builderActions = null;
      return null;
    }

    List<String> actionNames = getActions(player);
    List<NotificationCompat.Action> actions = new ArrayList<>(actionNames.size());
    for (int i = 0; i < actionNames.size(); i++) {
      String actionName = actionNames.get(i);
      @Nullable
      NotificationCompat.Action action =
          playbackActions.containsKey(actionName)
              ? playbackActions.get(actionName)
              : customActions.get(actionName);
      if (action != null) {
        actions.add(action);
      }
    }

    if (builder == null || !actions.equals(builderActions)) {
      builder = new NotificationCompat.Builder(context, channelId);
      builderActions = actions;
      for (int i = 0; i < actions.size(); i++) {
        builder.addAction(actions.get(i));
      }
    }

    MediaStyle mediaStyle = new MediaStyle();
    if (mediaSessionToken != null) {
      mediaStyle.setMediaSession(mediaSessionToken);
    }
    mediaStyle.setShowActionsInCompactView(getActionIndicesForCompactView(actionNames, player));
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
   *   +------------------------------------------------------------------------+
   *   | prev | &lt;&lt; | play/pause | &gt;&gt; | next | custom actions | stop |
   *   +------------------------------------------------------------------------+
   * </pre>
   *
   * <p>This method can be safely overridden. However, the names must be of the playback actions
   * {@link #ACTION_PAUSE}, {@link #ACTION_PLAY}, {@link #ACTION_FAST_FORWARD}, {@link
   * #ACTION_REWIND}, {@link #ACTION_NEXT} or {@link #ACTION_PREVIOUS}, or a key contained in the
   * map returned by {@link CustomActionReceiver#createCustomActions(Context, int)}. Otherwise the
   * action name is ignored.
   */
  protected List<String> getActions(Player player) {
    boolean enablePrevious = false;
    boolean enableRewind = false;
    boolean enableFastForward = false;
    boolean enableNext = false;
    Timeline timeline = player.getCurrentTimeline();
    if (!timeline.isEmpty() && !player.isPlayingAd()) {
      boolean isSeekable = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
      timeline.getWindow(player.getCurrentWindowIndex(), window);
      enablePrevious =
          isSeekable
              || !window.isLive()
              || player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
      enableRewind = isSeekable && controlDispatcher.isRewindEnabled();
      enableFastForward = isSeekable && controlDispatcher.isFastForwardEnabled();
      enableNext =
          (window.isLive() && window.isDynamic)
              || player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
    }

    List<String> stringActions = new ArrayList<>();
    if (usePreviousAction && enablePrevious) {
      stringActions.add(ACTION_PREVIOUS);
    }
    if (enableRewind) {
      stringActions.add(ACTION_REWIND);
    }
    if (usePlayPauseActions) {
      if (shouldShowPauseButton(player)) {
        stringActions.add(ACTION_PAUSE);
      } else {
        stringActions.add(ACTION_PLAY);
      }
    }
    if (enableFastForward) {
      stringActions.add(ACTION_FAST_FORWARD);
    }
    if (useNextAction && enableNext) {
      stringActions.add(ACTION_NEXT);
    }
    if (customActionReceiver != null) {
      stringActions.addAll(customActionReceiver.getCustomActions(player));
    }
    if (useStopAction) {
      stringActions.add(ACTION_STOP);
    }
    return stringActions;
  }

  /**
   * Gets an array with the indices of the buttons to be shown in compact mode.
   *
   * <p>This method can be overridden. The indices must refer to the list of actions passed as the
   * first parameter.
   *
   * @param actionNames The names of the actions included in the notification.
   * @param player The player for which a notification is being built.
   */
  @SuppressWarnings("unused")
  protected int[] getActionIndicesForCompactView(List<String> actionNames, Player player) {
    int pauseActionIndex = actionNames.indexOf(ACTION_PAUSE);
    int playActionIndex = actionNames.indexOf(ACTION_PLAY);
    int previousActionIndex =
        usePreviousActionInCompactView ? actionNames.indexOf(ACTION_PREVIOUS) : -1;
    int nextActionIndex = useNextActionInCompactView ? actionNames.indexOf(ACTION_NEXT) : -1;

    int[] actionIndices = new int[3];
    int actionCounter = 0;
    if (previousActionIndex != -1) {
      actionIndices[actionCounter++] = previousActionIndex;
    }
    boolean shouldShowPauseButton = shouldShowPauseButton(player);
    if (pauseActionIndex != -1 && shouldShowPauseButton) {
      actionIndices[actionCounter++] = pauseActionIndex;
    } else if (playActionIndex != -1 && !shouldShowPauseButton) {
      actionIndices[actionCounter++] = playActionIndex;
    }
    if (nextActionIndex != -1) {
      actionIndices[actionCounter++] = nextActionIndex;
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

  private static Map<String, NotificationCompat.Action> createPlaybackActions(
      Context context,
      int instanceId,
      int playActionIconResourceId,
      int pauseActionIconResourceId,
      int stopActionIconResourceId,
      int rewindActionIconResourceId,
      int fastForwardActionIconResourceId,
      int previousActionIconResourceId,
      int nextActionIconResourceId) {
    Map<String, NotificationCompat.Action> actions = new HashMap<>();
    actions.put(
        ACTION_PLAY,
        new NotificationCompat.Action(
            playActionIconResourceId,
            context.getString(R.string.exo_controls_play_description),
            createBroadcastIntent(ACTION_PLAY, context, instanceId)));
    actions.put(
        ACTION_PAUSE,
        new NotificationCompat.Action(
            pauseActionIconResourceId,
            context.getString(R.string.exo_controls_pause_description),
            createBroadcastIntent(ACTION_PAUSE, context, instanceId)));
    actions.put(
        ACTION_STOP,
        new NotificationCompat.Action(
            stopActionIconResourceId,
            context.getString(R.string.exo_controls_stop_description),
            createBroadcastIntent(ACTION_STOP, context, instanceId)));
    actions.put(
        ACTION_REWIND,
        new NotificationCompat.Action(
            rewindActionIconResourceId,
            context.getString(R.string.exo_controls_rewind_description),
            createBroadcastIntent(ACTION_REWIND, context, instanceId)));
    actions.put(
        ACTION_FAST_FORWARD,
        new NotificationCompat.Action(
            fastForwardActionIconResourceId,
            context.getString(R.string.exo_controls_fastforward_description),
            createBroadcastIntent(ACTION_FAST_FORWARD, context, instanceId)));
    actions.put(
        ACTION_PREVIOUS,
        new NotificationCompat.Action(
            previousActionIconResourceId,
            context.getString(R.string.exo_controls_previous_description),
            createBroadcastIntent(ACTION_PREVIOUS, context, instanceId)));
    actions.put(
        ACTION_NEXT,
        new NotificationCompat.Action(
            nextActionIconResourceId,
            context.getString(R.string.exo_controls_next_description),
            createBroadcastIntent(ACTION_NEXT, context, instanceId)));
    return actions;
  }

  private static PendingIntent createBroadcastIntent(
      String action, Context context, int instanceId) {
    Intent intent = new Intent(action).setPackage(context.getPackageName());
    intent.putExtra(EXTRA_INSTANCE_ID, instanceId);
    return PendingIntent.getBroadcast(
        context, instanceId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  @SuppressWarnings("nullness:argument.type.incompatible")
  private static void setLargeIcon(NotificationCompat.Builder builder, @Nullable Bitmap largeIcon) {
    builder.setLargeIcon(largeIcon);
  }

  private class PlayerListener implements Player.EventListener {

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
          EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
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
          || intent.getIntExtra(EXTRA_INSTANCE_ID, instanceId) != instanceId) {
        return;
      }
      String action = intent.getAction();
      if (ACTION_PLAY.equals(action)) {
        if (player.getPlaybackState() == Player.STATE_IDLE) {
          if (playbackPreparer != null) {
            playbackPreparer.preparePlayback();
          } else {
            controlDispatcher.dispatchPrepare(player);
          }
        } else if (player.getPlaybackState() == Player.STATE_ENDED) {
          controlDispatcher.dispatchSeekTo(player, player.getCurrentWindowIndex(), C.TIME_UNSET);
        }
        controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ true);
      } else if (ACTION_PAUSE.equals(action)) {
        controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ false);
      } else if (ACTION_PREVIOUS.equals(action)) {
        controlDispatcher.dispatchPrevious(player);
      } else if (ACTION_REWIND.equals(action)) {
        controlDispatcher.dispatchRewind(player);
      } else if (ACTION_FAST_FORWARD.equals(action)) {
        controlDispatcher.dispatchFastForward(player);
      } else if (ACTION_NEXT.equals(action)) {
        controlDispatcher.dispatchNext(player);
      } else if (ACTION_STOP.equals(action)) {
        controlDispatcher.dispatchStop(player, /* reset= */ true);
      } else if (ACTION_DISMISS.equals(action)) {
        stopNotification(/* dismissedByUser= */ true);
      } else if (action != null
          && customActionReceiver != null
          && customActions.containsKey(action)) {
        customActionReceiver.onCustomAction(player, action, intent);
      }
    }
  }
}
