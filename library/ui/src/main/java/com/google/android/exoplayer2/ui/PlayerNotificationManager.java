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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A notification manager to start, update and cancel a media style notification reflecting the
 * player state.
 *
 * <p>The notification is cancelled when {@code null} is passed to {@link #setPlayer(Player)} or
 * when an intent with action {@link #ACTION_STOP} is received.
 *
 * <p>If the player is released it must be removed from the manager by calling {@code
 * setPlayer(null)} which will cancel the notification.
 *
 * <h3>Action customization</h3>
 *
 * Standard playback actions can be shown or omitted as follows:
 *
 * <ul>
 *   <li><b>{@code useNavigationActions}</b> - Sets whether the navigation previous and next actions
 *       are displayed.
 *       <ul>
 *         <li>Corresponding setter: {@link #setUseNavigationActions(boolean)}
 *       </ul>
 *   <li><b>{@code stopAction}</b> - Sets which stop action should be used. If set to null, the stop
 *       action is not displayed.
 *       <ul>
 *         <li>Corresponding setter: {@link #setStopAction(String)}}
 *       </ul>
 *   <li><b>{@code rewindIncrementMs}</b> - Sets the rewind increment. If set to zero the rewind
 *       action is not displayed.
 *       <ul>
 *         <li>Corresponding setter: {@link #setRewindIncrementMs(long)}
 *         <li>Default: {@link #DEFAULT_REWIND_MS} (5000)
 *       </ul>
 *   <li><b>{@code fastForwardIncrementMs}</b> - Sets the fast forward increment. If set to zero the
 *       fast forward action is not included in the notification.
 *       <ul>
 *         <li>Corresponding setter: {@link #setFastForwardIncrementMs(long)}}
 *         <li>Default: {@link #DEFAULT_FAST_FORWARD_MS} (5000)
 *       </ul>
 * </ul>
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
    String getCurrentContentTitle(Player player);

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
    String getCurrentContentText(Player player);

    /**
     * Gets the large icon for the current media item.
     *
     * <p>When a bitmap initially needs to be asynchronously loaded, a placeholder (or null) can be
     * returned and the bitmap asynchronously passed to the {@link BitmapCallback} once it is
     * loaded. Because the adapter may be called multiple times for the same media item, the bitmap
     * should be cached by the app and whenever possible be returned synchronously at subsequent
     * calls for the same media item.
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

    /** Gets the actions handled by this receiver. */
    Map<String, NotificationCompat.Action> createCustomActions(Context context);

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

  /** A listener for start and cancellation of the notification. */
  public interface NotificationListener {

    /**
     * Called after the notification has been started.
     *
     * @param notificationId The id with which the notification has been posted.
     * @param notification The {@link Notification}.
     */
    void onNotificationStarted(int notificationId, Notification notification);

    /**
     * Called after the notification has been cancelled.
     *
     * @param notificationId The id of the notification which has been cancelled.
     */
    void onNotificationCancelled(int notificationId);
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
        mainHandler.post(
            new Runnable() {
              @Override
              public void run() {
                if (notificationTag == currentNotificationTag && isNotificationStarted) {
                  updateNotification(bitmap);
                }
              }
            });
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
  /** The action which cancels the notification and stops playback. */
  public static final String ACTION_STOP = "com.google.android.exoplayer.stop";

  /** Visibility of notification on the lock screen. */
  @Retention(SOURCE)
  @IntDef({
    NotificationCompat.VISIBILITY_PRIVATE,
    NotificationCompat.VISIBILITY_PUBLIC,
    NotificationCompat.VISIBILITY_SECRET
  })
  public @interface Visibility {}

  /** The default fast forward increment, in milliseconds. */
  public static final int DEFAULT_FAST_FORWARD_MS = 15000;
  /** The default rewind increment, in milliseconds. */
  public static final int DEFAULT_REWIND_MS = 5000;

  private static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;

  private final Context context;
  private final Handler mainHandler;
  private final NotificationManagerCompat notificationManager;
  private final IntentFilter intentFilter;
  private final Player.EventListener playerListener;
  private final NotificationBroadcastReceiver notificationBroadcastReceiver;
  private final MediaDescriptionAdapter mediaDescriptionAdapter;
  private final int notificationId;
  private final String channelId;
  private final Map<String, NotificationCompat.Action> playbackActions;
  private final Map<String, NotificationCompat.Action> customActions;
  private final CustomActionReceiver customActionReceiver;

  private Player player;
  private ControlDispatcher controlDispatcher;
  private boolean isNotificationStarted;
  private int currentNotificationTag;
  private NotificationListener notificationListener;
  private MediaSessionCompat.Token mediaSessionToken;
  private boolean useNavigationActions;
  private Pair<String, NotificationCompat.Action> stopAction;
  private long fastForwardMs;
  private long rewindMs;
  private int badgeIconType;
  private boolean colorized;
  private int defaults;
  private int color;
  private int smallIconResourceId;
  private int visibility;
  private boolean ongoing;
  private boolean useChronometer;
  private boolean wasPlayWhenReady;
  private int lastPlaybackState;

  /**
   * Creates the manager.
   *
   * @param context The {@link Context}.
   * @param mediaDescriptionAdapter The {@link MediaDescriptionAdapter}.
   * @param channelId The id of the notification channel.
   * @param notificationId The id of the notification.
   */
  public PlayerNotificationManager(
      Context context,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      String channelId,
      int notificationId) {
    this(context, mediaDescriptionAdapter, channelId, notificationId, null);
  }

  /**
   * Creates the manager with a {@link CustomActionReceiver}.
   *
   * @param context The {@link Context}.
   * @param mediaDescriptionAdapter The {@link MediaDescriptionAdapter}.
   * @param channelId The id of the notification channel.
   * @param notificationId The id of the notification.
   * @param customActionReceiver The {@link CustomActionReceiver}.
   */
  public PlayerNotificationManager(
      Context context,
      MediaDescriptionAdapter mediaDescriptionAdapter,
      String channelId,
      int notificationId,
      @Nullable CustomActionReceiver customActionReceiver) {
    this.context = context.getApplicationContext();
    this.mediaDescriptionAdapter = mediaDescriptionAdapter;
    this.channelId = channelId;
    this.customActionReceiver = customActionReceiver;
    this.notificationId = notificationId;
    this.controlDispatcher = new DefaultControlDispatcher();
    mainHandler = new Handler(Looper.getMainLooper());
    notificationManager = NotificationManagerCompat.from(context);
    playerListener = new PlayerListener();
    notificationBroadcastReceiver = new NotificationBroadcastReceiver();
    intentFilter = new IntentFilter();

    // initialize actions
    playbackActions = createPlaybackActions(context);
    for (String action : playbackActions.keySet()) {
      intentFilter.addAction(action);
    }
    customActions =
        customActionReceiver != null
            ? customActionReceiver.createCustomActions(context)
            : Collections.<String, NotificationCompat.Action>emptyMap();
    for (String action : customActions.keySet()) {
      intentFilter.addAction(action);
    }

    setStopAction(ACTION_STOP);

    useNavigationActions = true;
    ongoing = true;
    colorized = true;
    useChronometer = true;
    color = Color.TRANSPARENT;
    smallIconResourceId = R.drawable.exo_notification_small_icon;
    defaults = 0;
    fastForwardMs = DEFAULT_FAST_FORWARD_MS;
    rewindMs = DEFAULT_REWIND_MS;
    setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL);
    setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
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
   */
  public final void setPlayer(Player player) {
    if (this.player == player) {
      return;
    }
    if (this.player != null) {
      this.player.removeListener(playerListener);
      if (player == null) {
        stopNotification();
      }
    }
    this.player = player;
    if (player != null) {
      wasPlayWhenReady = player.getPlayWhenReady();
      lastPlaybackState = player.getPlaybackState();
      player.addListener(playerListener);
      if (lastPlaybackState != Player.STATE_IDLE) {
        startOrUpdateNotification();
      }
    }
  }

  /**
   * Sets the {@link ControlDispatcher}.
   *
   * @param controlDispatcher The {@link ControlDispatcher}, or null to use {@link
   *     DefaultControlDispatcher}.
   */
  public final void setControlDispatcher(ControlDispatcher controlDispatcher) {
    this.controlDispatcher =
        controlDispatcher != null ? controlDispatcher : new DefaultControlDispatcher();
  }

  /**
   * Sets the {@link NotificationListener}.
   *
   * @param notificationListener The {@link NotificationListener}.
   */
  public final void setNotificationListener(NotificationListener notificationListener) {
    this.notificationListener = notificationListener;
  }

  /**
   * Sets the fast forward increment in milliseconds.
   *
   * @param fastForwardMs The fast forward increment in milliseconds. A value of zero will cause the
   *     fast forward action to be disabled.
   */
  public final void setFastForwardIncrementMs(long fastForwardMs) {
    if (this.fastForwardMs == fastForwardMs) {
      return;
    }
    this.fastForwardMs = fastForwardMs;
    maybeUpdateNotification();
  }

  /**
   * Sets the rewind increment in milliseconds.
   *
   * @param rewindMs The rewind increment in milliseconds. A value of zero will cause the rewind
   *     action to be disabled.
   */
  public final void setRewindIncrementMs(long rewindMs) {
    if (this.rewindMs == rewindMs) {
      return;
    }
    this.rewindMs = rewindMs;
    maybeUpdateNotification();
  }

  /**
   * Sets whether the navigation actions should be used.
   *
   * @param useNavigationActions Whether to use navigation actions or not.
   */
  public final void setUseNavigationActions(boolean useNavigationActions) {
    if (this.useNavigationActions != useNavigationActions) {
      this.useNavigationActions = useNavigationActions;
      maybeUpdateNotification();
    }
  }

  /**
   * Sets the name of the action to be used as stop action to cancel the notification. If {@code
   * null} is passed the stop action is not displayed.
   *
   * @param stopAction The name of the stop action which must be {@link #ACTION_STOP} or an action
   *     provided by the {@link CustomActionReceiver}. {@code null} to omit the stop action.
   */
  public final void setStopAction(@Nullable String stopAction) {
    if ((this.stopAction == null && stopAction == null)
        || (this.stopAction != null && this.stopAction.first.equals(stopAction))) {
      return;
    }
    if (stopAction == null) {
      this.stopAction = null;
    } else if (ACTION_STOP.equals(stopAction)) {
      this.stopAction = new Pair<>(stopAction, playbackActions.get(ACTION_STOP));
    } else if (customActions.containsKey(stopAction)) {
      this.stopAction = new Pair<>(stopAction, customActions.get(stopAction));
    } else {
      throw new IllegalArgumentException();
    }
    maybeUpdateNotification();
  }

  /**
   * Sets the {@link MediaSessionCompat.Token}.
   *
   * @param token The {@link MediaSessionCompat.Token}.
   */
  public final void setMediaSessionToken(MediaSessionCompat.Token token) {
    if (!Util.areEqual(this.mediaSessionToken, token)) {
      mediaSessionToken = token;
      maybeUpdateNotification();
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
    maybeUpdateNotification();
  }

  /**
   * Sets whether the notification should be colorized (whether the notification color should be
   * applied).
   *
   * <p>See {@link NotificationCompat.Builder#setColorized(boolean)}.
   *
   * @param colorized Whether to colorize the notification.
   */
  public final void setColorized(boolean colorized) {
    if (this.colorized != colorized) {
      this.colorized = colorized;
      maybeUpdateNotification();
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
      maybeUpdateNotification();
    }
  }

  /**
   * Sets the color of the notification as a color value like for instance solid {@link
   * Color#BLACK)}. When set to {@link Color#TRANSPARENT} and a large icon is provided the
   * notification is colored with a color taken from the palette if the icon (when a media session
   * token is provided).
   *
   * <p>See {@link NotificationCompat.Builder#setColor(int)}.
   *
   * @param color The color.
   */
  public final void setColor(int color) {
    if (this.color != color) {
      this.color = color;
      maybeUpdateNotification();
    }
  }

  /**
   * Sets whether the notification should be ongoing. If {@code false} the user can dismiss the
   * notification by swiping. If in addition the stop action is enabled dismissing the notification
   * triggers the stop action.
   *
   * <p>See {@link NotificationCompat.Builder#setOngoing(boolean)}.
   *
   * @param ongoing Whether {@code true} the notification is ongoing and not dismissible.
   */
  public final void setOngoing(boolean ongoing) {
    if (this.ongoing != ongoing) {
      this.ongoing = ongoing;
      maybeUpdateNotification();
    }
  }

  /**
   * Sets the small icon of the notification which is also shown in the system status bar.
   *
   * <p>See {@link NotificationCompat.Builder#setSmallIcon(int)}.
   *
   * @param smallIconResourceId The resource id of the small icon.
   */
  public final void setSmallIcon(int smallIconResourceId) {
    if (this.smallIconResourceId != smallIconResourceId) {
      this.smallIconResourceId = smallIconResourceId;
      maybeUpdateNotification();
    }
  }

  /**
   * Sets whether the elapsed time of the media playback should be displayed
   *
   * <p>See {@link NotificationCompat.Builder#setUsesChronometer(boolean)}.
   *
   * @param useChronometer Whether to use chronometer.
   */
  public final void setUseChronometer(boolean useChronometer) {
    if (this.useChronometer != useChronometer) {
      this.useChronometer = useChronometer;
      maybeUpdateNotification();
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
    maybeUpdateNotification();
  }

  private Notification updateNotification(Bitmap bitmap) {
    Notification notification = createNotification(player, bitmap);
    notificationManager.notify(notificationId, notification);
    return notification;
  }

  private void startOrUpdateNotification() {
    Notification notification = updateNotification(null);
    if (!isNotificationStarted) {
      isNotificationStarted = true;
      context.registerReceiver(notificationBroadcastReceiver, intentFilter);
      if (notificationListener != null) {
        notificationListener.onNotificationStarted(notificationId, notification);
      }
    }
  }

  private void maybeUpdateNotification() {
    if (isNotificationStarted) {
      updateNotification(null);
    }
  }

  private void stopNotification() {
    if (isNotificationStarted) {
      notificationManager.cancel(notificationId);
      isNotificationStarted = false;
      context.unregisterReceiver(notificationBroadcastReceiver);
      if (notificationListener != null) {
        notificationListener.onNotificationCancelled(notificationId);
      }
    }
  }

  private Map<String, NotificationCompat.Action> createPlaybackActions(Context context) {
    Map<String, NotificationCompat.Action> actions = new HashMap<>();
    Intent playIntent = new Intent(ACTION_PLAY).setPackage(context.getPackageName());
    actions.put(
        ACTION_PLAY,
        new NotificationCompat.Action(
            R.drawable.exo_notification_play,
            context.getString(R.string.exo_controls_play_description),
            PendingIntent.getBroadcast(context, 0, playIntent, PendingIntent.FLAG_CANCEL_CURRENT)));
    Intent pauseIntent = new Intent(ACTION_PAUSE).setPackage(context.getPackageName());
    actions.put(
        ACTION_PAUSE,
        new NotificationCompat.Action(
            R.drawable.exo_notification_pause,
            context.getString(R.string.exo_controls_pause_description),
            PendingIntent.getBroadcast(
                context, 0, pauseIntent, PendingIntent.FLAG_CANCEL_CURRENT)));
    Intent stopIntent = new Intent(ACTION_STOP).setPackage(context.getPackageName());
    actions.put(
        ACTION_STOP,
        new NotificationCompat.Action(
            R.drawable.exo_notification_stop,
            context.getString(R.string.exo_controls_stop_description),
            PendingIntent.getBroadcast(context, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT)));
    Intent rewindIntent = new Intent(ACTION_REWIND).setPackage(context.getPackageName());
    actions.put(
        ACTION_REWIND,
        new NotificationCompat.Action(
            R.drawable.exo_notification_rewind,
            context.getString(R.string.exo_controls_rewind_description),
            PendingIntent.getBroadcast(
                context, 0, rewindIntent, PendingIntent.FLAG_CANCEL_CURRENT)));
    Intent fastForwardIntent = new Intent(ACTION_FAST_FORWARD).setPackage(context.getPackageName());
    actions.put(
        ACTION_FAST_FORWARD,
        new NotificationCompat.Action(
            R.drawable.exo_notification_fastforward,
            context.getString(R.string.exo_controls_fastforward_description),
            PendingIntent.getBroadcast(
                context, 0, fastForwardIntent, PendingIntent.FLAG_CANCEL_CURRENT)));
    Intent previousIntent = new Intent(ACTION_PREVIOUS).setPackage(context.getPackageName());
    actions.put(
        ACTION_PREVIOUS,
        new NotificationCompat.Action(
            R.drawable.exo_notification_previous,
            context.getString(R.string.exo_controls_previous_description),
            PendingIntent.getBroadcast(
                context, 0, previousIntent, PendingIntent.FLAG_CANCEL_CURRENT)));
    Intent nextIntent = new Intent(ACTION_NEXT).setPackage(context.getPackageName());
    actions.put(
        ACTION_NEXT,
        new NotificationCompat.Action(
            R.drawable.exo_notification_next,
            context.getString(R.string.exo_controls_next_description),
            PendingIntent.getBroadcast(context, 0, nextIntent, PendingIntent.FLAG_CANCEL_CURRENT)));
    return actions;
  }

  /**
   * Creates the notification given the current player state.
   *
   * @param player The player for which state to build a notification.
   * @param largeIcon The large icon to be used.
   * @return The {@link Notification} which has been built.
   */
  protected Notification createNotification(Player player, @Nullable Bitmap largeIcon) {
    boolean isPlayingAd = player.isPlayingAd();
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
    List<String> actionNames = getActions(player);
    for (int i = 0; i < actionNames.size(); i++) {
      String actionName = actionNames.get(i);
      NotificationCompat.Action action =
          playbackActions.containsKey(actionName)
              ? playbackActions.get(actionName)
              : customActions.get(actionName);
      if (action != null) {
        builder.addAction(action);
      }
    }
    // Create a media style notification.
    MediaStyle mediaStyle = new MediaStyle();
    builder.setStyle(mediaStyle);
    if (mediaSessionToken != null) {
      mediaStyle.setMediaSession(mediaSessionToken);
    }
    mediaStyle.setShowActionsInCompactView(getActionIndicesForCompactView(player));
    // Configure stop action (eg. when user dismisses the notification when !isOngoing).
    boolean useStopAction = stopAction != null && !isPlayingAd;
    mediaStyle.setShowCancelButton(useStopAction);
    if (useStopAction) {
      PendingIntent stopIntent = stopAction.second.actionIntent;
      builder.setDeleteIntent(stopIntent);
      mediaStyle.setCancelButtonIntent(stopIntent);
    }
    // Set notification properties from getters.
    builder
        .setBadgeIconType(badgeIconType)
        .setOngoing(ongoing)
        .setColor(color)
        .setColorized(colorized)
        .setSmallIcon(smallIconResourceId)
        .setVisibility(visibility)
        .setDefaults(defaults);
    if (useChronometer
        && !player.isCurrentWindowDynamic()
        && player.getPlayWhenReady()
        && player.getPlaybackState() == Player.STATE_READY) {
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
    if (largeIcon == null) {
      largeIcon =
          mediaDescriptionAdapter.getCurrentLargeIcon(
              player, new BitmapCallback(++currentNotificationTag));
    }
    if (largeIcon != null) {
      builder.setLargeIcon(largeIcon);
    }
    PendingIntent contentIntent = mediaDescriptionAdapter.createCurrentContentIntent(player);
    if (contentIntent != null) {
      builder.setContentIntent(contentIntent);
    }
    return builder.build();
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
   * map returned by {@link CustomActionReceiver#createCustomActions(Context)}. Otherwise the action
   * name is ignored.
   */
  protected List<String> getActions(Player player) {
    List<String> stringActions = new ArrayList<>();
    if (!player.isPlayingAd()) {
      if (useNavigationActions) {
        stringActions.add(ACTION_PREVIOUS);
      }
      if (rewindMs > 0) {
        stringActions.add(ACTION_REWIND);
      }
      if (player.getPlayWhenReady()) {
        stringActions.add(ACTION_PAUSE);
      } else if (!player.getPlayWhenReady()) {
        stringActions.add(ACTION_PLAY);
      }
      if (fastForwardMs > 0) {
        stringActions.add(ACTION_FAST_FORWARD);
      }
      if (useNavigationActions && player.getNextWindowIndex() != C.INDEX_UNSET) {
        stringActions.add(ACTION_NEXT);
      }
      if (!customActions.isEmpty()) {
        stringActions.addAll(customActionReceiver.getCustomActions(player));
      }
      if (stopAction != null) {
        stringActions.add(stopAction.first);
      }
    }
    return stringActions;
  }

  /**
   * Gets an array with the indices of the buttons to be shown in compact mode.
   *
   * <p>This method can be overridden. The indices must refer to the list of actions returned by
   * {@link #getActions(Player)}.
   *
   * @param player The player for which state to build a notification.
   */
  protected int[] getActionIndicesForCompactView(Player player) {
    int actionIndex = useNavigationActions ? 1 : 0;
    actionIndex += fastForwardMs > 0 ? 1 : 0;
    return new int[] {actionIndex};
  }

  private class PlayerListener extends Player.DefaultEventListener {

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      if ((wasPlayWhenReady != playWhenReady && playbackState != Player.STATE_IDLE)
          || lastPlaybackState != playbackState) {
        startOrUpdateNotification();
      }
      wasPlayWhenReady = playWhenReady;
      lastPlaybackState = playbackState;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
      if (player.getPlaybackState() == Player.STATE_IDLE) {
        return;
      }
      startOrUpdateNotification();
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      if (player.getPlaybackState() == Player.STATE_IDLE) {
        return;
      }
      startOrUpdateNotification();
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
      startOrUpdateNotification();
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
      if (player.getPlaybackState() == Player.STATE_IDLE) {
        return;
      }
      startOrUpdateNotification();
    }
  }

  private class NotificationBroadcastReceiver extends BroadcastReceiver {

    private final Timeline.Window window;

    /** Creates the broadcast receiver. */
    public NotificationBroadcastReceiver() {
      window = new Timeline.Window();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      if (!isNotificationStarted) {
        return;
      }
      String action = intent.getAction();
      if (ACTION_PLAY.equals(action) || ACTION_PAUSE.equals(action)) {
        controlDispatcher.dispatchSetPlayWhenReady(player, ACTION_PLAY.equals(action));
      } else if (ACTION_FAST_FORWARD.equals(action) || ACTION_REWIND.equals(action)) {
        long increment = ACTION_FAST_FORWARD.equals(action) ? fastForwardMs : -rewindMs;
        controlDispatcher.dispatchSeekTo(
            player, player.getCurrentWindowIndex(), player.getCurrentPosition() + increment);
      } else if (ACTION_NEXT.equals(action)) {
        int nextWindowIndex = player.getNextWindowIndex();
        if (nextWindowIndex != C.INDEX_UNSET) {
          controlDispatcher.dispatchSeekTo(player, nextWindowIndex, C.TIME_UNSET);
        }
      } else if (ACTION_PREVIOUS.equals(action)) {
        player.getCurrentTimeline().getWindow(player.getCurrentWindowIndex(), window);
        int previousWindowIndex = player.getPreviousWindowIndex();
        if (previousWindowIndex != C.INDEX_UNSET
            && (player.getCurrentPosition() <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS
                || (window.isDynamic && !window.isSeekable))) {
          controlDispatcher.dispatchSeekTo(player, previousWindowIndex, C.TIME_UNSET);
        } else {
          controlDispatcher.dispatchSeekTo(player, player.getCurrentWindowIndex(), C.TIME_UNSET);
        }
      } else if (ACTION_STOP.equals(action)) {
        controlDispatcher.dispatchStop(player, true);
        stopNotification();
      } else if (customActions.containsKey(action)) {
        customActionReceiver.onCustomAction(player, action, intent);
      }
    }
  }
}
