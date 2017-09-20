/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.RepeatModeUtil;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;

/**
 * A view for controlling {@link Player} instances.
 * <p>
 * A PlaybackControlView can be customized by setting attributes (or calling corresponding methods),
 * overriding the view's layout file or by specifying a custom view layout file, as outlined below.
 *
 * <h3>Attributes</h3>
 * The following attributes can be set on a PlaybackControlView when used in a layout XML file:
 * <p>
 * <ul>
 *   <li><b>{@code show_timeout}</b> - The time between the last user interaction and the controls
 *       being automatically hidden, in milliseconds. Use zero if the controls should not
 *       automatically timeout.
 *       <ul>
 *         <li>Corresponding method: {@link #setShowTimeoutMs(int)}</li>
 *         <li>Default: {@link #DEFAULT_SHOW_TIMEOUT_MS}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code rewind_increment}</b> - The duration of the rewind applied when the user taps the
 *       rewind button, in milliseconds. Use zero to disable the rewind button.
 *       <ul>
 *         <li>Corresponding method: {@link #setRewindIncrementMs(int)}</li>
 *         <li>Default: {@link #DEFAULT_REWIND_MS}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code fastforward_increment}</b> - Like {@code rewind_increment}, but for fast forward.
 *       <ul>
 *         <li>Corresponding method: {@link #setFastForwardIncrementMs(int)}</li>
 *         <li>Default: {@link #DEFAULT_FAST_FORWARD_MS}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code repeat_toggle_modes}</b> - A flagged enumeration value specifying which repeat
 *       mode toggle options are enabled. Valid values are: {@code none}, {@code one},
 *       {@code all}, or {@code one|all}.
 *       <ul>
 *         <li>Corresponding method: {@link #setRepeatToggleModes(int)}</li>
 *         <li>Default: {@link PlaybackControlView#DEFAULT_REPEAT_TOGGLE_MODES}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code controller_layout_id}</b> - Specifies the id of the layout to be inflated. See
 *       below for more details.
 *       <ul>
 *         <li>Corresponding method: None</li>
 *         <li>Default: {@code R.id.exo_playback_control_view}</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>Overriding the layout file</h3>
 * To customize the layout of PlaybackControlView throughout your app, or just for certain
 * configurations, you can define {@code exo_playback_control_view.xml} layout files in your
 * application {@code res/layout*} directories. These layouts will override the one provided by the
 * ExoPlayer library, and will be inflated for use by PlaybackControlView. The view identifies and
 * binds its children by looking for the following ids:
 * <p>
 * <ul>
 *   <li><b>{@code exo_play}</b> - The play button.
 *       <ul>
 *         <li>Type: {@link View}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code exo_pause}</b> - The pause button.
 *       <ul>
 *         <li>Type: {@link View}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code exo_ffwd}</b> - The fast forward button.
 *       <ul>
 *         <li>Type: {@link View}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code exo_rew}</b> - The rewind button.
 *       <ul>
 *         <li>Type: {@link View}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code exo_prev}</b> - The previous track button.
 *       <ul>
 *         <li>Type: {@link View}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code exo_next}</b> - The next track button.
 *       <ul>
 *         <li>Type: {@link View}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code exo_repeat_toggle}</b> - The repeat toggle button.
 *       <ul>
 *         <li>Type: {@link View}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code exo_position}</b> - Text view displaying the current playback position.
 *       <ul>
 *         <li>Type: {@link TextView}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code exo_duration}</b> - Text view displaying the current media duration.
 *       <ul>
 *         <li>Type: {@link TextView}</li>
 *       </ul>
 *   </li>
 *   <li><b>{@code exo_progress}</b> - Time bar that's updated during playback and allows seeking.
 *       <ul>
 *         <li>Type: {@link TimeBar}</li>
 *       </ul>
 *   </li>
 * </ul>
 * <p>
 * All child views are optional and so can be omitted if not required, however where defined they
 * must be of the expected type.
 *
 * <h3>Specifying a custom layout file</h3>
 * Defining your own {@code exo_playback_control_view.xml} is useful to customize the layout of
 * PlaybackControlView throughout your application. It's also possible to customize the layout for a
 * single instance in a layout file. This is achieved by setting the {@code controller_layout_id}
 * attribute on a PlaybackControlView. This will cause the specified layout to be inflated instead
 * of {@code exo_playback_control_view.xml} for only the instance on which the attribute is set.
 */
public class PlaybackControlView extends FrameLayout {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.ui");
  }

  /**
   * Listener to be notified about changes of the visibility of the UI control.
   */
  public interface VisibilityListener {

    /**
     * Called when the visibility changes.
     *
     * @param visibility The new visibility. Either {@link View#VISIBLE} or {@link View#GONE}.
     */
    void onVisibilityChange(int visibility);

  }

  /**
   * Dispatches operations to the {@link Player}.
   * <p>
   * Implementations may choose to suppress (e.g. prevent playback from resuming if audio focus is
   * denied) or modify (e.g. change the seek position to prevent a user from seeking past a
   * non-skippable advert) operations.
   */
  public interface ControlDispatcher {

    /**
     * Dispatches a {@link Player#setPlayWhenReady(boolean)} operation.
     *
     * @param player The {@link Player} to which the operation should be dispatched.
     * @param playWhenReady Whether playback should proceed when ready.
     * @return True if the operation was dispatched. False if suppressed.
     */
    boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady);

    /**
     * Dispatches a {@link Player#seekTo(int, long)} operation.
     *
     * @param player The {@link Player} to which the operation should be dispatched.
     * @param windowIndex The index of the window.
     * @param positionMs The seek position in the specified window, or {@link C#TIME_UNSET} to seek
     *     to the window's default position.
     * @return True if the operation was dispatched. False if suppressed.
     */
    boolean dispatchSeekTo(Player player, int windowIndex, long positionMs);

    /**
     * Dispatches a {@link Player#setRepeatMode(int)} operation.
     *
     * @param player The {@link Player} to which the operation should be dispatched.
     * @param repeatMode The repeat mode.
     * @return True if the operation was dispatched. False if suppressed.
     */
    boolean dispatchSetRepeatMode(Player player, @RepeatMode int repeatMode);

  }

  /**
   * Default {@link ControlDispatcher} that dispatches operations to the player without
   * modification.
   */
  public static final ControlDispatcher DEFAULT_CONTROL_DISPATCHER = new ControlDispatcher() {

    @Override
    public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
      player.setPlayWhenReady(playWhenReady);
      return true;
    }

    @Override
    public boolean dispatchSeekTo(Player player, int windowIndex, long positionMs) {
      player.seekTo(windowIndex, positionMs);
      return true;
    }

    @Override
    public boolean dispatchSetRepeatMode(Player player, @RepeatMode int repeatMode) {
      player.setRepeatMode(repeatMode);
      return true;
    }

  };

  /**
   * The default fast forward increment, in milliseconds.
   */
  public static final int DEFAULT_FAST_FORWARD_MS = 15000;
  /**
   * The default rewind increment, in milliseconds.
   */
  public static final int DEFAULT_REWIND_MS = 5000;
  /**
   * The default show timeout, in milliseconds.
   */
  public static final int DEFAULT_SHOW_TIMEOUT_MS = 5000;
  /**
   * The default repeat toggle modes.
   */
  public static final @RepeatModeUtil.RepeatToggleModes int DEFAULT_REPEAT_TOGGLE_MODES =
      RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE;

  /**
   * The maximum number of windows that can be shown in a multi-window time bar.
   */
  public static final int MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR = 100;

  private static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;

  private final ComponentListener componentListener;
  private final View previousButton;
  private final View nextButton;
  private final View playButton;
  private final View pauseButton;
  private final View fastForwardButton;
  private final View rewindButton;
  private final ImageView repeatToggleButton;
  private final TextView durationView;
  private final TextView positionView;
  private final TimeBar timeBar;
  private final StringBuilder formatBuilder;
  private final Formatter formatter;
  private final Timeline.Period period;
  private final Timeline.Window window;

  private final Drawable repeatOffButtonDrawable;
  private final Drawable repeatOneButtonDrawable;
  private final Drawable repeatAllButtonDrawable;
  private final String repeatOffButtonContentDescription;
  private final String repeatOneButtonContentDescription;
  private final String repeatAllButtonContentDescription;

  private Player player;
  private ControlDispatcher controlDispatcher;
  private VisibilityListener visibilityListener;

  private boolean isAttachedToWindow;
  private boolean showMultiWindowTimeBar;
  private boolean multiWindowTimeBar;
  private boolean scrubbing;
  private int rewindMs;
  private int fastForwardMs;
  private int showTimeoutMs;
  private @RepeatModeUtil.RepeatToggleModes int repeatToggleModes;
  private long hideAtMs;
  private long[] adGroupTimesMs;
  private boolean[] playedAdGroups;
  private long[] extraAdGroupTimesMs;
  private boolean[] extraPlayedAdGroups;

  private final Runnable updateProgressAction = new Runnable() {
    @Override
    public void run() {
      updateProgress();
    }
  };

  private final Runnable hideAction = new Runnable() {
    @Override
    public void run() {
      hide();
    }
  };

  public PlaybackControlView(Context context) {
    this(context, null);
  }

  public PlaybackControlView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public PlaybackControlView(Context context, AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, attrs);
  }

  public PlaybackControlView(Context context, AttributeSet attrs, int defStyleAttr,
      AttributeSet playbackAttrs) {
    super(context, attrs, defStyleAttr);
    int controllerLayoutId = R.layout.exo_playback_control_view;
    rewindMs = DEFAULT_REWIND_MS;
    fastForwardMs = DEFAULT_FAST_FORWARD_MS;
    showTimeoutMs = DEFAULT_SHOW_TIMEOUT_MS;
    repeatToggleModes = DEFAULT_REPEAT_TOGGLE_MODES;
    if (playbackAttrs != null) {
      TypedArray a = context.getTheme().obtainStyledAttributes(playbackAttrs,
          R.styleable.PlaybackControlView, 0, 0);
      try {
        rewindMs = a.getInt(R.styleable.PlaybackControlView_rewind_increment, rewindMs);
        fastForwardMs = a.getInt(R.styleable.PlaybackControlView_fastforward_increment,
            fastForwardMs);
        showTimeoutMs = a.getInt(R.styleable.PlaybackControlView_show_timeout, showTimeoutMs);
        controllerLayoutId = a.getResourceId(R.styleable.PlaybackControlView_controller_layout_id,
            controllerLayoutId);
        repeatToggleModes = getRepeatToggleModes(a, repeatToggleModes);
      } finally {
        a.recycle();
      }
    }
    period = new Timeline.Period();
    window = new Timeline.Window();
    formatBuilder = new StringBuilder();
    formatter = new Formatter(formatBuilder, Locale.getDefault());
    adGroupTimesMs = new long[0];
    playedAdGroups = new boolean[0];
    extraAdGroupTimesMs = new long[0];
    extraPlayedAdGroups = new boolean[0];
    componentListener = new ComponentListener();
    controlDispatcher = DEFAULT_CONTROL_DISPATCHER;

    LayoutInflater.from(context).inflate(controllerLayoutId, this);
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);

    durationView = (TextView) findViewById(R.id.exo_duration);
    positionView = (TextView) findViewById(R.id.exo_position);
    timeBar = (TimeBar) findViewById(R.id.exo_progress);
    if (timeBar != null) {
      timeBar.setListener(componentListener);
    }
    playButton = findViewById(R.id.exo_play);
    if (playButton != null) {
      playButton.setOnClickListener(componentListener);
    }
    pauseButton = findViewById(R.id.exo_pause);
    if (pauseButton != null) {
      pauseButton.setOnClickListener(componentListener);
    }
    previousButton = findViewById(R.id.exo_prev);
    if (previousButton != null) {
      previousButton.setOnClickListener(componentListener);
    }
    nextButton = findViewById(R.id.exo_next);
    if (nextButton != null) {
      nextButton.setOnClickListener(componentListener);
    }
    rewindButton = findViewById(R.id.exo_rew);
    if (rewindButton != null) {
      rewindButton.setOnClickListener(componentListener);
    }
    fastForwardButton = findViewById(R.id.exo_ffwd);
    if (fastForwardButton != null) {
      fastForwardButton.setOnClickListener(componentListener);
    }
    repeatToggleButton = (ImageView) findViewById(R.id.exo_repeat_toggle);
    if (repeatToggleButton != null) {
      repeatToggleButton.setOnClickListener(componentListener);
    }
    Resources resources = context.getResources();
    repeatOffButtonDrawable = resources.getDrawable(R.drawable.exo_controls_repeat_off);
    repeatOneButtonDrawable = resources.getDrawable(R.drawable.exo_controls_repeat_one);
    repeatAllButtonDrawable = resources.getDrawable(R.drawable.exo_controls_repeat_all);
    repeatOffButtonContentDescription = resources.getString(
        R.string.exo_controls_repeat_off_description);
    repeatOneButtonContentDescription = resources.getString(
        R.string.exo_controls_repeat_one_description);
    repeatAllButtonContentDescription = resources.getString(
        R.string.exo_controls_repeat_all_description);
  }

  @SuppressWarnings("ResourceType")
  private static @RepeatModeUtil.RepeatToggleModes int getRepeatToggleModes(TypedArray a,
      @RepeatModeUtil.RepeatToggleModes int repeatToggleModes) {
    return a.getInt(R.styleable.PlaybackControlView_repeat_toggle_modes, repeatToggleModes);
  }

  /**
   * Returns the {@link Player} currently being controlled by this view, or null if no player is
   * set.
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Sets the {@link Player} to control.
   *
   * @param player The {@link Player} to control.
   */
  public void setPlayer(Player player) {
    if (this.player == player) {
      return;
    }
    if (this.player != null) {
      this.player.removeListener(componentListener);
    }
    this.player = player;
    if (player != null) {
      player.addListener(componentListener);
    }
    updateAll();
  }

  /**
   * Sets whether the time bar should show all windows, as opposed to just the current one. If the
   * timeline has a period with unknown duration or more than
   * {@link #MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR} windows the time bar will fall back to showing a
   * single window.
   *
   * @param showMultiWindowTimeBar Whether the time bar should show all windows.
   */
  public void setShowMultiWindowTimeBar(boolean showMultiWindowTimeBar) {
    this.showMultiWindowTimeBar = showMultiWindowTimeBar;
    updateTimeBarMode();
  }

  /**
   * Sets the millisecond positions of extra ad markers relative to the start of the window (or
   * timeline, if in multi-window mode) and whether each extra ad has been played or not. The
   * markers are shown in addition to any ad markers for ads in the player's timeline.
   *
   * @param extraAdGroupTimesMs The millisecond timestamps of the extra ad markers to show, or
   *     {@code null} to show no extra ad markers.
   * @param extraPlayedAdGroups Whether each ad has been played, or {@code null} to show no extra ad
   *     markers.
   */
  public void setExtraAdGroupMarkers(@Nullable long[] extraAdGroupTimesMs,
      @Nullable boolean[] extraPlayedAdGroups) {
    if (extraAdGroupTimesMs == null) {
      this.extraAdGroupTimesMs = new long[0];
      this.extraPlayedAdGroups = new boolean[0];
    } else {
      Assertions.checkArgument(extraAdGroupTimesMs.length == extraPlayedAdGroups.length);
      this.extraAdGroupTimesMs = extraAdGroupTimesMs;
      this.extraPlayedAdGroups = extraPlayedAdGroups;
    }
    updateProgress();
  }

  /**
   * Sets the {@link VisibilityListener}.
   *
   * @param listener The listener to be notified about visibility changes.
   */
  public void setVisibilityListener(VisibilityListener listener) {
    this.visibilityListener = listener;
  }

  /**
   * Sets the {@link ControlDispatcher}.
   *
   * @param controlDispatcher The {@link ControlDispatcher}, or null to use
   *     {@link #DEFAULT_CONTROL_DISPATCHER}.
   */
  public void setControlDispatcher(ControlDispatcher controlDispatcher) {
    this.controlDispatcher = controlDispatcher == null ? DEFAULT_CONTROL_DISPATCHER
        : controlDispatcher;
  }

  /**
   * Sets the rewind increment in milliseconds.
   *
   * @param rewindMs The rewind increment in milliseconds. A non-positive value will cause the
   *     rewind button to be disabled.
   */
  public void setRewindIncrementMs(int rewindMs) {
    this.rewindMs = rewindMs;
    updateNavigation();
  }

  /**
   * Sets the fast forward increment in milliseconds.
   *
   * @param fastForwardMs The fast forward increment in milliseconds. A non-positive value will
   *     cause the fast forward button to be disabled.
   */
  public void setFastForwardIncrementMs(int fastForwardMs) {
    this.fastForwardMs = fastForwardMs;
    updateNavigation();
  }

  /**
   * Returns the playback controls timeout. The playback controls are automatically hidden after
   * this duration of time has elapsed without user input.
   *
   * @return The duration in milliseconds. A non-positive value indicates that the controls will
   *     remain visible indefinitely.
   */
  public int getShowTimeoutMs() {
    return showTimeoutMs;
  }

  /**
   * Sets the playback controls timeout. The playback controls are automatically hidden after this
   * duration of time has elapsed without user input.
   *
   * @param showTimeoutMs The duration in milliseconds. A non-positive value will cause the controls
   *     to remain visible indefinitely.
   */
  public void setShowTimeoutMs(int showTimeoutMs) {
    this.showTimeoutMs = showTimeoutMs;
  }

  /**
   * Returns which repeat toggle modes are enabled.
   *
   * @return The currently enabled {@link RepeatModeUtil.RepeatToggleModes}.
   */
  public @RepeatModeUtil.RepeatToggleModes int getRepeatToggleModes() {
    return repeatToggleModes;
  }

  /**
   * Sets which repeat toggle modes are enabled.
   *
   * @param repeatToggleModes A set of {@link RepeatModeUtil.RepeatToggleModes}.
   */
  public void setRepeatToggleModes(@RepeatModeUtil.RepeatToggleModes int repeatToggleModes) {
    this.repeatToggleModes = repeatToggleModes;
    if (player != null) {
      @Player.RepeatMode int currentMode = player.getRepeatMode();
      if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE
          && currentMode != Player.REPEAT_MODE_OFF) {
        controlDispatcher.dispatchSetRepeatMode(player, Player.REPEAT_MODE_OFF);
      } else if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE
          && currentMode == Player.REPEAT_MODE_ALL) {
        controlDispatcher.dispatchSetRepeatMode(player, Player.REPEAT_MODE_ONE);
      } else if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL
          && currentMode == Player.REPEAT_MODE_ONE) {
        controlDispatcher.dispatchSetRepeatMode(player, Player.REPEAT_MODE_ALL);
      }
    }
  }

  /**
   * Shows the playback controls. If {@link #getShowTimeoutMs()} is positive then the controls will
   * be automatically hidden after this duration of time has elapsed without user input.
   */
  public void show() {
    if (!isVisible()) {
      setVisibility(VISIBLE);
      if (visibilityListener != null) {
        visibilityListener.onVisibilityChange(getVisibility());
      }
      updateAll();
      requestPlayPauseFocus();
    }
    // Call hideAfterTimeout even if already visible to reset the timeout.
    hideAfterTimeout();
  }

  /**
   * Hides the controller.
   */
  public void hide() {
    if (isVisible()) {
      setVisibility(GONE);
      if (visibilityListener != null) {
        visibilityListener.onVisibilityChange(getVisibility());
      }
      removeCallbacks(updateProgressAction);
      removeCallbacks(hideAction);
      hideAtMs = C.TIME_UNSET;
    }
  }

  /**
   * Returns whether the controller is currently visible.
   */
  public boolean isVisible() {
    return getVisibility() == VISIBLE;
  }

  private void hideAfterTimeout() {
    removeCallbacks(hideAction);
    if (showTimeoutMs > 0) {
      hideAtMs = SystemClock.uptimeMillis() + showTimeoutMs;
      if (isAttachedToWindow) {
        postDelayed(hideAction, showTimeoutMs);
      }
    } else {
      hideAtMs = C.TIME_UNSET;
    }
  }

  private void updateAll() {
    updatePlayPauseButton();
    updateNavigation();
    updateRepeatModeButton();
    updateProgress();
  }

  private void updatePlayPauseButton() {
    if (!isVisible() || !isAttachedToWindow) {
      return;
    }
    boolean requestPlayPauseFocus = false;
    boolean playing = player != null && player.getPlayWhenReady();
    if (playButton != null) {
      requestPlayPauseFocus |= playing && playButton.isFocused();
      playButton.setVisibility(playing ? View.GONE : View.VISIBLE);
    }
    if (pauseButton != null) {
      requestPlayPauseFocus |= !playing && pauseButton.isFocused();
      pauseButton.setVisibility(!playing ? View.GONE : View.VISIBLE);
    }
    if (requestPlayPauseFocus) {
      requestPlayPauseFocus();
    }
  }

  private void updateNavigation() {
    if (!isVisible() || !isAttachedToWindow) {
      return;
    }
    Timeline timeline = player != null ? player.getCurrentTimeline() : null;
    boolean haveNonEmptyTimeline = timeline != null && !timeline.isEmpty();
    boolean isSeekable = false;
    boolean enablePrevious = false;
    boolean enableNext = false;
    if (haveNonEmptyTimeline) {
      int windowIndex = player.getCurrentWindowIndex();
      timeline.getWindow(windowIndex, window);
      isSeekable = window.isSeekable;
      enablePrevious = isSeekable || !window.isDynamic
          || timeline.getPreviousWindowIndex(windowIndex, player.getRepeatMode()) != C.INDEX_UNSET;
      enableNext = window.isDynamic
          || timeline.getNextWindowIndex(windowIndex, player.getRepeatMode()) != C.INDEX_UNSET;
      if (player.isPlayingAd()) {
        // Always hide player controls during ads.
        hide();
      }
    }
    setButtonEnabled(enablePrevious, previousButton);
    setButtonEnabled(enableNext, nextButton);
    setButtonEnabled(fastForwardMs > 0 && isSeekable, fastForwardButton);
    setButtonEnabled(rewindMs > 0 && isSeekable, rewindButton);
    if (timeBar != null) {
      timeBar.setEnabled(isSeekable);
    }
  }

  private void updateRepeatModeButton() {
    if (!isVisible() || !isAttachedToWindow || repeatToggleButton == null) {
      return;
    }
    if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE) {
      repeatToggleButton.setVisibility(View.GONE);
      return;
    }
    if (player == null) {
      setButtonEnabled(false, repeatToggleButton);
      return;
    }
    setButtonEnabled(true, repeatToggleButton);
    switch (player.getRepeatMode()) {
      case Player.REPEAT_MODE_OFF:
        repeatToggleButton.setImageDrawable(repeatOffButtonDrawable);
        repeatToggleButton.setContentDescription(repeatOffButtonContentDescription);
        break;
      case Player.REPEAT_MODE_ONE:
        repeatToggleButton.setImageDrawable(repeatOneButtonDrawable);
        repeatToggleButton.setContentDescription(repeatOneButtonContentDescription);
        break;
      case Player.REPEAT_MODE_ALL:
        repeatToggleButton.setImageDrawable(repeatAllButtonDrawable);
        repeatToggleButton.setContentDescription(repeatAllButtonContentDescription);
        break;
    }
    repeatToggleButton.setVisibility(View.VISIBLE);
  }

  private void updateTimeBarMode() {
    if (player == null) {
      return;
    }
    multiWindowTimeBar = showMultiWindowTimeBar
        && canShowMultiWindowTimeBar(player.getCurrentTimeline(), window);
  }

  private void updateProgress() {
    if (!isVisible() || !isAttachedToWindow) {
      return;
    }

    long position = 0;
    long bufferedPosition = 0;
    long duration = 0;
    if (player != null) {
      long currentWindowTimeBarOffsetUs = 0;
      long durationUs = 0;
      int adGroupCount = 0;
      Timeline timeline = player.getCurrentTimeline();
      if (!timeline.isEmpty()) {
        int currentWindowIndex = player.getCurrentWindowIndex();
        int firstWindowIndex = multiWindowTimeBar ? 0 : currentWindowIndex;
        int lastWindowIndex =
            multiWindowTimeBar ? timeline.getWindowCount() - 1 : currentWindowIndex;
        for (int i = firstWindowIndex; i <= lastWindowIndex; i++) {
          if (i == currentWindowIndex) {
            currentWindowTimeBarOffsetUs = durationUs;
          }
          timeline.getWindow(i, window);
          if (window.durationUs == C.TIME_UNSET) {
            Assertions.checkState(!multiWindowTimeBar);
            break;
          }
          for (int j = window.firstPeriodIndex; j <= window.lastPeriodIndex; j++) {
            timeline.getPeriod(j, period);
            int periodAdGroupCount = period.getAdGroupCount();
            for (int adGroupIndex = 0; adGroupIndex < periodAdGroupCount; adGroupIndex++) {
              long adGroupTimeInPeriodUs = period.getAdGroupTimeUs(adGroupIndex);
              if (adGroupTimeInPeriodUs == C.TIME_END_OF_SOURCE) {
                if (period.durationUs == C.TIME_UNSET) {
                  // Don't show ad markers for postrolls in periods with unknown duration.
                  continue;
                }
                adGroupTimeInPeriodUs = period.durationUs;
              }
              long adGroupTimeInWindowUs = adGroupTimeInPeriodUs + period.getPositionInWindowUs();
              if (adGroupTimeInWindowUs >= 0 && adGroupTimeInWindowUs <= window.durationUs) {
                if (adGroupCount == adGroupTimesMs.length) {
                  int newLength = adGroupTimesMs.length == 0 ? 1 : adGroupTimesMs.length * 2;
                  adGroupTimesMs = Arrays.copyOf(adGroupTimesMs, newLength);
                  playedAdGroups = Arrays.copyOf(playedAdGroups, newLength);
                }
                adGroupTimesMs[adGroupCount] = C.usToMs(durationUs + adGroupTimeInWindowUs);
                playedAdGroups[adGroupCount] = period.hasPlayedAdGroup(adGroupIndex);
                adGroupCount++;
              }
            }
          }
          durationUs += window.durationUs;
        }
      }
      duration = C.usToMs(durationUs);
      position = C.usToMs(currentWindowTimeBarOffsetUs);
      bufferedPosition = position;
      if (player.isPlayingAd()) {
        position += player.getContentPosition();
        bufferedPosition = position;
      } else {
        position += player.getCurrentPosition();
        bufferedPosition += player.getBufferedPosition();
      }
      if (timeBar != null) {
        int extraAdGroupCount = extraAdGroupTimesMs.length;
        int totalAdGroupCount = adGroupCount + extraAdGroupCount;
        if (totalAdGroupCount > adGroupTimesMs.length) {
          adGroupTimesMs = Arrays.copyOf(adGroupTimesMs, totalAdGroupCount);
          playedAdGroups = Arrays.copyOf(playedAdGroups, totalAdGroupCount);
        }
        System.arraycopy(extraAdGroupTimesMs, 0, adGroupTimesMs, adGroupCount, extraAdGroupCount);
        System.arraycopy(extraPlayedAdGroups, 0, playedAdGroups, adGroupCount, extraAdGroupCount);
        timeBar.setAdGroupTimesMs(adGroupTimesMs, playedAdGroups, totalAdGroupCount);
      }
    }
    if (durationView != null) {
      durationView.setText(Util.getStringForTime(formatBuilder, formatter, duration));
    }
    if (positionView != null && !scrubbing) {
      positionView.setText(Util.getStringForTime(formatBuilder, formatter, position));
    }
    if (timeBar != null) {
      timeBar.setPosition(position);
      timeBar.setBufferedPosition(bufferedPosition);
      timeBar.setDuration(duration);
    }

    // Cancel any pending updates and schedule a new one if necessary.
    removeCallbacks(updateProgressAction);
    int playbackState = player == null ? Player.STATE_IDLE : player.getPlaybackState();
    if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
      long delayMs;
      if (player.getPlayWhenReady() && playbackState == Player.STATE_READY) {
        delayMs = 1000 - (position % 1000);
        if (delayMs < 200) {
          delayMs += 1000;
        }
      } else {
        delayMs = 1000;
      }
      postDelayed(updateProgressAction, delayMs);
    }
  }

  private void requestPlayPauseFocus() {
    boolean playing = player != null && player.getPlayWhenReady();
    if (!playing && playButton != null) {
      playButton.requestFocus();
    } else if (playing && pauseButton != null) {
      pauseButton.requestFocus();
    }
  }

  private void setButtonEnabled(boolean enabled, View view) {
    if (view == null) {
      return;
    }
    view.setEnabled(enabled);
    if (Util.SDK_INT >= 11) {
      setViewAlphaV11(view, enabled ? 1f : 0.3f);
      view.setVisibility(VISIBLE);
    } else {
      view.setVisibility(enabled ? VISIBLE : INVISIBLE);
    }
  }

  @TargetApi(11)
  private void setViewAlphaV11(View view, float alpha) {
    view.setAlpha(alpha);
  }

  private void previous() {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return;
    }
    int windowIndex = player.getCurrentWindowIndex();
    timeline.getWindow(windowIndex, window);
    int previousWindowIndex = timeline.getPreviousWindowIndex(windowIndex, player.getRepeatMode());
    if (previousWindowIndex != C.INDEX_UNSET
        && (player.getCurrentPosition() <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS
        || (window.isDynamic && !window.isSeekable))) {
      seekTo(previousWindowIndex, C.TIME_UNSET);
    } else {
      seekTo(0);
    }
  }

  private void next() {
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return;
    }
    int windowIndex = player.getCurrentWindowIndex();
    int nextWindowIndex = timeline.getNextWindowIndex(windowIndex, player.getRepeatMode());
    if (nextWindowIndex != C.INDEX_UNSET) {
      seekTo(nextWindowIndex, C.TIME_UNSET);
    } else if (timeline.getWindow(windowIndex, window, false).isDynamic) {
      seekTo(windowIndex, C.TIME_UNSET);
    }
  }

  private void rewind() {
    if (rewindMs <= 0) {
      return;
    }
    seekTo(Math.max(player.getCurrentPosition() - rewindMs, 0));
  }

  private void fastForward() {
    if (fastForwardMs <= 0) {
      return;
    }
    long durationMs = player.getDuration();
    long seekPositionMs = player.getCurrentPosition() + fastForwardMs;
    if (durationMs != C.TIME_UNSET) {
      seekPositionMs = Math.min(seekPositionMs, durationMs);
    }
    seekTo(seekPositionMs);
  }

  private void seekTo(long positionMs) {
    seekTo(player.getCurrentWindowIndex(), positionMs);
  }

  private void seekTo(int windowIndex, long positionMs) {
    boolean dispatched = controlDispatcher.dispatchSeekTo(player, windowIndex, positionMs);
    if (!dispatched) {
      // The seek wasn't dispatched. If the progress bar was dragged by the user to perform the
      // seek then it'll now be in the wrong position. Trigger a progress update to snap it back.
      updateProgress();
    }
  }

  private void seekToTimeBarPosition(long positionMs) {
    int windowIndex;
    Timeline timeline = player.getCurrentTimeline();
    if (multiWindowTimeBar && !timeline.isEmpty()) {
      int windowCount = timeline.getWindowCount();
      windowIndex = 0;
      while (true) {
        long windowDurationMs = timeline.getWindow(windowIndex, window).getDurationMs();
        if (positionMs < windowDurationMs) {
          break;
        } else if (windowIndex == windowCount - 1) {
          // Seeking past the end of the last window should seek to the end of the timeline.
          positionMs = windowDurationMs;
          break;
        }
        positionMs -= windowDurationMs;
        windowIndex++;
      }
    } else {
      windowIndex = player.getCurrentWindowIndex();
    }
    seekTo(windowIndex, positionMs);
  }

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    isAttachedToWindow = true;
    if (hideAtMs != C.TIME_UNSET) {
      long delayMs = hideAtMs - SystemClock.uptimeMillis();
      if (delayMs <= 0) {
        hide();
      } else {
        postDelayed(hideAction, delayMs);
      }
    }
    updateAll();
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    isAttachedToWindow = false;
    removeCallbacks(updateProgressAction);
    removeCallbacks(hideAction);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    return dispatchMediaKeyEvent(event) || super.dispatchKeyEvent(event);
  }

  /**
   * Called to process media key events. Any {@link KeyEvent} can be passed but only media key
   * events will be handled.
   *
   * @param event A key event.
   * @return Whether the key event was handled.
   */
  public boolean dispatchMediaKeyEvent(KeyEvent event) {
    int keyCode = event.getKeyCode();
    if (player == null || !isHandledMediaKey(keyCode)) {
      return false;
    }
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
        fastForward();
      } else if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
        rewind();
      } else if (event.getRepeatCount() == 0) {
        switch (keyCode) {
          case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            controlDispatcher.dispatchSetPlayWhenReady(player, !player.getPlayWhenReady());
            break;
          case KeyEvent.KEYCODE_MEDIA_PLAY:
            controlDispatcher.dispatchSetPlayWhenReady(player, true);
            break;
          case KeyEvent.KEYCODE_MEDIA_PAUSE:
            controlDispatcher.dispatchSetPlayWhenReady(player, false);
            break;
          case KeyEvent.KEYCODE_MEDIA_NEXT:
            next();
            break;
          case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            previous();
            break;
          default:
            break;
        }
      }
    }
    return true;
  }

  @SuppressLint("InlinedApi")
  private static boolean isHandledMediaKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
        || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
        || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
        || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
        || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
  }

  /**
   * Returns whether the specified {@code timeline} can be shown on a multi-window time bar.
   *
   * @param timeline The {@link Timeline} to check.
   * @param window A scratch {@link Timeline.Window} instance.
   * @return Whether the specified timeline can be shown on a multi-window time bar.
   */
  private static boolean canShowMultiWindowTimeBar(Timeline timeline, Timeline.Window window) {
    if (timeline.getWindowCount() > MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR) {
      return false;
    }
    int windowCount = timeline.getWindowCount();
    for (int i = 0; i < windowCount; i++) {
      if (timeline.getWindow(i, window).durationUs == C.TIME_UNSET) {
        return false;
      }
    }
    return true;
  }

  private final class ComponentListener implements Player.EventListener, TimeBar.OnScrubListener,
      OnClickListener {

    @Override
    public void onScrubStart(TimeBar timeBar, long position) {
      removeCallbacks(hideAction);
      scrubbing = true;
    }

    @Override
    public void onScrubMove(TimeBar timeBar, long position) {
      if (positionView != null) {
        positionView.setText(Util.getStringForTime(formatBuilder, formatter, position));
      }
    }

    @Override
    public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
      scrubbing = false;
      if (!canceled && player != null) {
        seekToTimeBarPosition(position);
      }
      hideAfterTimeout();
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      updatePlayPauseButton();
      updateProgress();
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
      updateRepeatModeButton();
      updateNavigation();
    }

    @Override
    public void onPositionDiscontinuity() {
      updateNavigation();
      updateProgress();
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      // Do nothing.
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
      updateNavigation();
      updateTimeBarMode();
      updateProgress();
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
      // Do nothing.
    }

    @Override
    public void onTracksChanged(TrackGroupArray tracks, TrackSelectionArray selections) {
      // Do nothing.
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      // Do nothing.
    }

    @Override
    public void onClick(View view) {
      if (player != null) {
        if (nextButton == view) {
          next();
        } else if (previousButton == view) {
          previous();
        } else if (fastForwardButton == view) {
          fastForward();
        } else if (rewindButton == view) {
          rewind();
        } else if (playButton == view) {
          controlDispatcher.dispatchSetPlayWhenReady(player, true);
        } else if (pauseButton == view) {
          controlDispatcher.dispatchSetPlayWhenReady(player, false);
        } else if (repeatToggleButton == view) {
          controlDispatcher.dispatchSetRepeatMode(player, RepeatModeUtil.getNextRepeatMode(
              player.getRepeatMode(), repeatToggleModes));
        }
      }
      hideAfterTimeout();
    }

  }

}
