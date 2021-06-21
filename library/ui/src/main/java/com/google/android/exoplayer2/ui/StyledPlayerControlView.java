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
import static com.google.android.exoplayer2.Player.EVENT_TRACKS_CHANGED;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.Events;
import com.google.android.exoplayer2.Player.State;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.RepeatModeUtil;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A view for controlling {@link Player} instances.
 *
 * <p>A StyledPlayerControlView can be customized by setting attributes (or calling corresponding
 * methods), overriding drawables, overriding the view's layout file, or by specifying a custom view
 * layout file.
 *
 * <h3>Attributes</h3>
 *
 * The following attributes can be set on a StyledPlayerControlView when used in a layout XML file:
 *
 * <ul>
 *   <li><b>{@code show_timeout}</b> - The time between the last user interaction and the controls
 *       being automatically hidden, in milliseconds. Use zero if the controls should not
 *       automatically timeout.
 *       <ul>
 *         <li>Corresponding method: {@link #setShowTimeoutMs(int)}
 *         <li>Default: {@link #DEFAULT_SHOW_TIMEOUT_MS}
 *       </ul>
 *   <li><b>{@code show_rewind_button}</b> - Whether the rewind button is shown.
 *       <ul>
 *         <li>Corresponding method: {@link #setShowRewindButton(boolean)}
 *         <li>Default: true
 *       </ul>
 *   <li><b>{@code show_fastforward_button}</b> - Whether the fast forward button is shown.
 *       <ul>
 *         <li>Corresponding method: {@link #setShowFastForwardButton(boolean)}
 *         <li>Default: true
 *       </ul>
 *   <li><b>{@code show_previous_button}</b> - Whether the previous button is shown.
 *       <ul>
 *         <li>Corresponding method: {@link #setShowPreviousButton(boolean)}
 *         <li>Default: true
 *       </ul>
 *   <li><b>{@code show_next_button}</b> - Whether the next button is shown.
 *       <ul>
 *         <li>Corresponding method: {@link #setShowNextButton(boolean)}
 *         <li>Default: true
 *       </ul>
 *   <li><b>{@code rewind_increment}</b> - The duration of the rewind applied when the user taps the
 *       rewind button, in milliseconds. Use zero to disable the rewind button.
 *       <ul>
 *         <li>Corresponding method: {@link #setControlDispatcher(ControlDispatcher)}
 *         <li>Default: {@link DefaultControlDispatcher#DEFAULT_REWIND_MS}
 *       </ul>
 *   <li><b>{@code fastforward_increment}</b> - Like {@code rewind_increment}, but for fast forward.
 *       <ul>
 *         <li>Corresponding method: {@link #setControlDispatcher(ControlDispatcher)}
 *         <li>Default: {@link DefaultControlDispatcher#DEFAULT_FAST_FORWARD_MS}
 *       </ul>
 *   <li><b>{@code repeat_toggle_modes}</b> - A flagged enumeration value specifying which repeat
 *       mode toggle options are enabled. Valid values are: {@code none}, {@code one}, {@code all},
 *       or {@code one|all}.
 *       <ul>
 *         <li>Corresponding method: {@link #setRepeatToggleModes(int)}
 *         <li>Default: {@link #DEFAULT_REPEAT_TOGGLE_MODES}
 *       </ul>
 *   <li><b>{@code show_shuffle_button}</b> - Whether the shuffle button is shown.
 *       <ul>
 *         <li>Corresponding method: {@link #setShowShuffleButton(boolean)}
 *         <li>Default: false
 *       </ul>
 *   <li><b>{@code show_subtitle_button}</b> - Whether the subtitle button is shown.
 *       <ul>
 *         <li>Corresponding method: {@link #setShowSubtitleButton(boolean)}
 *         <li>Default: false
 *       </ul>
 *   <li><b>{@code animation_enabled}</b> - Whether an animation is used to show and hide the
 *       playback controls.
 *       <ul>
 *         <li>Corresponding method: {@link #setAnimationEnabled(boolean)}
 *         <li>Default: true
 *       </ul>
 *   <li><b>{@code time_bar_min_update_interval}</b> - Specifies the minimum interval between time
 *       bar position updates.
 *       <ul>
 *         <li>Corresponding method: {@link #setTimeBarMinUpdateInterval(int)}
 *         <li>Default: {@link #DEFAULT_TIME_BAR_MIN_UPDATE_INTERVAL_MS}
 *       </ul>
 *   <li><b>{@code controller_layout_id}</b> - Specifies the id of the layout to be inflated. See
 *       below for more details.
 *       <ul>
 *         <li>Corresponding method: None
 *         <li>Default: {@code R.layout.exo_styled_player_control_view}
 *       </ul>
 *   <li>All attributes that can be set on {@link DefaultTimeBar} can also be set on a
 *       StyledPlayerControlView, and will be propagated to the inflated {@link DefaultTimeBar}
 *       unless the layout is overridden to specify a custom {@code exo_progress} (see below).
 * </ul>
 *
 * <h3>Overriding drawables</h3>
 *
 * The drawables used by StyledPlayerControlView (with its default layout file) can be overridden by
 * drawables with the same names defined in your application. The drawables that can be overridden
 * are:
 *
 * <ul>
 *   <li><b>{@code exo_styled_controls_play}</b> - The play icon.
 *   <li><b>{@code exo_styled_controls_pause}</b> - The pause icon.
 *   <li><b>{@code exo_styled_controls_rewind}</b> - The background of rewind icon.
 *   <li><b>{@code exo_styled_controls_fastforward}</b> - The background of fast forward icon.
 *   <li><b>{@code exo_styled_controls_previous}</b> - The previous icon.
 *   <li><b>{@code exo_styled_controls_next}</b> - The next icon.
 *   <li><b>{@code exo_styled_controls_repeat_off}</b> - The repeat icon for {@link
 *       Player#REPEAT_MODE_OFF}.
 *   <li><b>{@code exo_styled_controls_repeat_one}</b> - The repeat icon for {@link
 *       Player#REPEAT_MODE_ONE}.
 *   <li><b>{@code exo_styled_controls_repeat_all}</b> - The repeat icon for {@link
 *       Player#REPEAT_MODE_ALL}.
 *   <li><b>{@code exo_styled_controls_shuffle_off}</b> - The shuffle icon when shuffling is
 *       disabled.
 *   <li><b>{@code exo_styled_controls_shuffle_on}</b> - The shuffle icon when shuffling is enabled.
 *   <li><b>{@code exo_styled_controls_vr}</b> - The VR icon.
 * </ul>
 *
 * <h3>Overriding the layout file</h3>
 *
 * To customize the layout of StyledPlayerControlView throughout your app, or just for certain
 * configurations, you can define {@code exo_styled_player_control_view.xml} layout files in your
 * application {@code res/layout*} directories. But, in this case, you need to be careful since the
 * default animation implementation expects certain relative positions between children. See also <a
 * href="CustomLayout">Specifying a custom layout file</a>.
 *
 * <p>The layout files in your {@code res/layout*} will override the one provided by the ExoPlayer
 * library, and will be inflated for use by StyledPlayerControlView. The view identifies and binds
 * its children by looking for the following ids:
 *
 * <ul>
 *   <li><b>{@code exo_play_pause}</b> - The play and pause button.
 *       <ul>
 *         <li>Type: {@link ImageView}
 *       </ul>
 *   <li><b>{@code exo_rew}</b> - The rewind button.
 *       <ul>
 *         <li>Type: {@link View}
 *       </ul>
 *   <li><b>{@code exo_rew_with_amount}</b> - The rewind button with rewind amount.
 *       <ul>
 *         <li>Type: {@link TextView}
 *         <li>Note: StyledPlayerControlView will programmatically set the text with the rewind
 *             amount in seconds. Ignored if an {@code exo_rew} exists. Otherwise, it works as the
 *             rewind button.
 *       </ul>
 *   <li><b>{@code exo_ffwd}</b> - The fast forward button.
 *       <ul>
 *         <li>Type: {@link View}
 *       </ul>
 *   <li><b>{@code exo_ffwd_with_amount}</b> - The fast forward button with fast forward amount.
 *       <ul>
 *         <li>Type: {@link TextView}
 *         <li>Note: StyledPlayerControlView will programmatically set the text with the fast
 *             forward amount in seconds. Ignored if an {@code exo_ffwd} exists. Otherwise, it works
 *             as the fast forward button.
 *       </ul>
 *   <li><b>{@code exo_prev}</b> - The previous button.
 *       <ul>
 *         <li>Type: {@link View}
 *       </ul>
 *   <li><b>{@code exo_next}</b> - The next button.
 *       <ul>
 *         <li>Type: {@link View}
 *       </ul>
 *   <li><b>{@code exo_repeat_toggle}</b> - The repeat toggle button.
 *       <ul>
 *         <li>Type: {@link ImageView}
 *         <li>Note: StyledPlayerControlView will programmatically set the drawable on the repeat
 *             toggle button according to the player's current repeat mode. The drawables used are
 *             {@code exo_controls_repeat_off}, {@code exo_controls_repeat_one} and {@code
 *             exo_controls_repeat_all}. See the section above for information on overriding these
 *             drawables.
 *       </ul>
 *   <li><b>{@code exo_shuffle}</b> - The shuffle button.
 *       <ul>
 *         <li>Type: {@link ImageView}
 *         <li>Note: StyledPlayerControlView will programmatically set the drawable on the shuffle
 *             button according to the player's current repeat mode. The drawables used are {@code
 *             exo_controls_shuffle_off} and {@code exo_controls_shuffle_on}. See the section above
 *             for information on overriding these drawables.
 *       </ul>
 *   <li><b>{@code exo_vr}</b> - The VR mode button.
 *       <ul>
 *         <li>Type: {@link View}
 *       </ul>
 *   <li><b>{@code exo_subtitle}</b> - The subtitle button.
 *       <ul>
 *         <li>Type: {@link ImageView}
 *       </ul>
 *   <li><b>{@code exo_fullscreen}</b> - The fullscreen button.
 *       <ul>
 *         <li>Type: {@link ImageView}
 *       </ul>
 *   <li><b>{@code exo_minimal_fullscreen}</b> - The fullscreen button in minimal mode.
 *       <ul>
 *         <li>Type: {@link ImageView}
 *       </ul>
 *   <li><b>{@code exo_position}</b> - Text view displaying the current playback position.
 *       <ul>
 *         <li>Type: {@link TextView}
 *       </ul>
 *   <li><b>{@code exo_duration}</b> - Text view displaying the current media duration.
 *       <ul>
 *         <li>Type: {@link TextView}
 *       </ul>
 *   <li><b>{@code exo_progress_placeholder}</b> - A placeholder that's replaced with the inflated
 *       {@link DefaultTimeBar}. Ignored if an {@code exo_progress} view exists.
 *       <ul>
 *         <li>Type: {@link View}
 *       </ul>
 *   <li><b>{@code exo_progress}</b> - Time bar that's updated during playback and allows seeking.
 *       {@link DefaultTimeBar} attributes set on the StyledPlayerControlView will not be
 *       automatically propagated through to this instance. If a view exists with this id, any
 *       {@code exo_progress_placeholder} view will be ignored.
 *       <ul>
 *         <li>Type: {@link TimeBar}
 *       </ul>
 * </ul>
 *
 * <p>All child views are optional and so can be omitted if not required, however where defined they
 * must be of the expected type.
 *
 * <h3 id="CustomLayout">Specifying a custom layout file</h3>
 *
 * Defining your own {@code exo_styled_player_control_view.xml} is useful to customize the layout of
 * StyledPlayerControlView throughout your application. It's also possible to customize the layout
 * for a single instance in a layout file. This is achieved by setting the {@code
 * controller_layout_id} attribute on a StyledPlayerControlView. This will cause the specified
 * layout to be inflated instead of {@code exo_styled_player_control_view.xml} for only the instance
 * on which the attribute is set.
 *
 * <p>You need to be careful when you set the {@code controller_layout_id}, because the default
 * animation implementation expects certain relative positions between children.
 */
public class StyledPlayerControlView extends FrameLayout {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.ui");
  }

  /** Listener to be notified about changes of the visibility of the UI control. */
  public interface VisibilityListener {

    /**
     * Called when the visibility changes.
     *
     * @param visibility The new visibility. Either {@link View#VISIBLE} or {@link View#GONE}.
     */
    void onVisibilityChange(int visibility);
  }

  /** Listener to be notified when progress has been updated. */
  public interface ProgressUpdateListener {

    /**
     * Called when progress needs to be updated.
     *
     * @param position The current position.
     * @param bufferedPosition The current buffered position.
     */
    void onProgressUpdate(long position, long bufferedPosition);
  }

  /**
   * Listener to be invoked to inform the fullscreen mode is changed. Application should handle the
   * fullscreen mode accordingly.
   */
  public interface OnFullScreenModeChangedListener {
    /**
     * Called to indicate a fullscreen mode change.
     *
     * @param isFullScreen {@code true} if the video rendering surface should be fullscreen {@code
     *     false} otherwise.
     */
    void onFullScreenModeChanged(boolean isFullScreen);
  }

  /** The default show timeout, in milliseconds. */
  public static final int DEFAULT_SHOW_TIMEOUT_MS = 5_000;
  /** The default repeat toggle modes. */
  public static final @RepeatModeUtil.RepeatToggleModes int DEFAULT_REPEAT_TOGGLE_MODES =
      RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE;
  /** The default minimum interval between time bar position updates. */
  public static final int DEFAULT_TIME_BAR_MIN_UPDATE_INTERVAL_MS = 200;
  /** The maximum number of windows that can be shown in a multi-window time bar. */
  public static final int MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR = 100;
  /** The maximum interval between time bar position updates. */
  private static final int MAX_UPDATE_INTERVAL_MS = 1_000;

  private static final int SETTINGS_PLAYBACK_SPEED_POSITION = 0;
  private static final int SETTINGS_AUDIO_TRACK_SELECTION_POSITION = 1;

  private final ComponentListener componentListener;
  private final CopyOnWriteArrayList<VisibilityListener> visibilityListeners;
  @Nullable private final View previousButton;
  @Nullable private final View nextButton;
  @Nullable private final View playPauseButton;
  @Nullable private final View fastForwardButton;
  @Nullable private final View rewindButton;
  @Nullable private final TextView fastForwardButtonTextView;
  @Nullable private final TextView rewindButtonTextView;
  @Nullable private final ImageView repeatToggleButton;
  @Nullable private final ImageView shuffleButton;
  @Nullable private final View vrButton;
  @Nullable private final TextView durationView;
  @Nullable private final TextView positionView;
  @Nullable private final TimeBar timeBar;
  private final StringBuilder formatBuilder;
  private final Formatter formatter;
  private final Timeline.Period period;
  private final Timeline.Window window;
  private final Runnable updateProgressAction;

  private final Drawable repeatOffButtonDrawable;
  private final Drawable repeatOneButtonDrawable;
  private final Drawable repeatAllButtonDrawable;
  private final String repeatOffButtonContentDescription;
  private final String repeatOneButtonContentDescription;
  private final String repeatAllButtonContentDescription;
  private final Drawable shuffleOnButtonDrawable;
  private final Drawable shuffleOffButtonDrawable;
  private final float buttonAlphaEnabled;
  private final float buttonAlphaDisabled;
  private final String shuffleOnContentDescription;
  private final String shuffleOffContentDescription;
  private final Drawable subtitleOnButtonDrawable;
  private final Drawable subtitleOffButtonDrawable;
  private final String subtitleOnContentDescription;
  private final String subtitleOffContentDescription;
  private final Drawable fullScreenExitDrawable;
  private final Drawable fullScreenEnterDrawable;
  private final String fullScreenExitContentDescription;
  private final String fullScreenEnterContentDescription;

  @Nullable private Player player;
  private ControlDispatcher controlDispatcher;
  @Nullable private ProgressUpdateListener progressUpdateListener;
  @Nullable private PlaybackPreparer playbackPreparer;

  @Nullable private OnFullScreenModeChangedListener onFullScreenModeChangedListener;
  private boolean isFullScreen;
  private boolean isAttachedToWindow;
  private boolean showMultiWindowTimeBar;
  private boolean multiWindowTimeBar;
  private boolean scrubbing;
  private int showTimeoutMs;
  private int timeBarMinUpdateIntervalMs;
  private @RepeatModeUtil.RepeatToggleModes int repeatToggleModes;
  private long[] adGroupTimesMs;
  private boolean[] playedAdGroups;
  private long[] extraAdGroupTimesMs;
  private boolean[] extraPlayedAdGroups;
  private long currentWindowOffset;
  private long rewindMs;
  private long fastForwardMs;

  private StyledPlayerControlViewLayoutManager controlViewLayoutManager;
  private Resources resources;

  private RecyclerView settingsView;
  private SettingsAdapter settingsAdapter;
  private PlaybackSpeedAdapter playbackSpeedAdapter;
  private PopupWindow settingsWindow;
  private boolean needToHideBars;
  private int settingsWindowMargin;

  @Nullable private DefaultTrackSelector trackSelector;
  private TrackSelectionAdapter textTrackSelectionAdapter;
  private TrackSelectionAdapter audioTrackSelectionAdapter;
  // TODO(insun): Add setTrackNameProvider to use customized track name provider.
  private TrackNameProvider trackNameProvider;

  @Nullable private ImageView subtitleButton;
  @Nullable private ImageView fullScreenButton;
  @Nullable private ImageView minimalFullScreenButton;
  @Nullable private View settingsButton;
  @Nullable private View playbackSpeedButton;
  @Nullable private View audioTrackButton;

  public StyledPlayerControlView(Context context) {
    this(context, /* attrs= */ null);
  }

  public StyledPlayerControlView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, /* defStyleAttr= */ 0);
  }

  public StyledPlayerControlView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, attrs);
  }

  @SuppressWarnings({
    "nullness:argument.type.incompatible",
    "nullness:assignment.type.incompatible",
    "nullness:method.invocation.invalid",
    "nullness:methodref.receiver.bound.invalid"
  })
  public StyledPlayerControlView(
      Context context,
      @Nullable AttributeSet attrs,
      int defStyleAttr,
      @Nullable AttributeSet playbackAttrs) {
    super(context, attrs, defStyleAttr);
    int controllerLayoutId = R.layout.exo_styled_player_control_view;
    rewindMs = DefaultControlDispatcher.DEFAULT_REWIND_MS;
    fastForwardMs = DefaultControlDispatcher.DEFAULT_FAST_FORWARD_MS;
    showTimeoutMs = DEFAULT_SHOW_TIMEOUT_MS;
    repeatToggleModes = DEFAULT_REPEAT_TOGGLE_MODES;
    timeBarMinUpdateIntervalMs = DEFAULT_TIME_BAR_MIN_UPDATE_INTERVAL_MS;
    boolean showRewindButton = true;
    boolean showFastForwardButton = true;
    boolean showPreviousButton = true;
    boolean showNextButton = true;
    boolean showShuffleButton = false;
    boolean showSubtitleButton = false;
    boolean animationEnabled = true;
    boolean showVrButton = false;

    if (playbackAttrs != null) {
      TypedArray a =
          context
              .getTheme()
              .obtainStyledAttributes(playbackAttrs, R.styleable.StyledPlayerControlView, 0, 0);
      try {
        rewindMs = a.getInt(R.styleable.StyledPlayerControlView_rewind_increment, (int) rewindMs);
        fastForwardMs =
            a.getInt(
                R.styleable.StyledPlayerControlView_fastforward_increment, (int) fastForwardMs);
        controllerLayoutId =
            a.getResourceId(
                R.styleable.StyledPlayerControlView_controller_layout_id, controllerLayoutId);
        showTimeoutMs = a.getInt(R.styleable.StyledPlayerControlView_show_timeout, showTimeoutMs);
        repeatToggleModes = getRepeatToggleModes(a, repeatToggleModes);
        showRewindButton =
            a.getBoolean(R.styleable.StyledPlayerControlView_show_rewind_button, showRewindButton);
        showFastForwardButton =
            a.getBoolean(
                R.styleable.StyledPlayerControlView_show_fastforward_button, showFastForwardButton);
        showPreviousButton =
            a.getBoolean(
                R.styleable.StyledPlayerControlView_show_previous_button, showPreviousButton);
        showNextButton =
            a.getBoolean(R.styleable.StyledPlayerControlView_show_next_button, showNextButton);
        showShuffleButton =
            a.getBoolean(
                R.styleable.StyledPlayerControlView_show_shuffle_button, showShuffleButton);
        showSubtitleButton =
            a.getBoolean(
                R.styleable.StyledPlayerControlView_show_subtitle_button, showSubtitleButton);
        showVrButton =
            a.getBoolean(R.styleable.StyledPlayerControlView_show_vr_button, showVrButton);
        setTimeBarMinUpdateInterval(
            a.getInt(
                R.styleable.StyledPlayerControlView_time_bar_min_update_interval,
                timeBarMinUpdateIntervalMs));
        animationEnabled =
            a.getBoolean(R.styleable.StyledPlayerControlView_animation_enabled, animationEnabled);
      } finally {
        a.recycle();
      }
    }

    LayoutInflater.from(context).inflate(controllerLayoutId, /* root= */ this);
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);

    componentListener = new ComponentListener();
    visibilityListeners = new CopyOnWriteArrayList<>();
    period = new Timeline.Period();
    window = new Timeline.Window();
    formatBuilder = new StringBuilder();
    formatter = new Formatter(formatBuilder, Locale.getDefault());
    adGroupTimesMs = new long[0];
    playedAdGroups = new boolean[0];
    extraAdGroupTimesMs = new long[0];
    extraPlayedAdGroups = new boolean[0];
    controlDispatcher = new DefaultControlDispatcher(fastForwardMs, rewindMs);
    updateProgressAction = this::updateProgress;

    durationView = findViewById(R.id.exo_duration);
    positionView = findViewById(R.id.exo_position);

    subtitleButton = findViewById(R.id.exo_subtitle);
    if (subtitleButton != null) {
      subtitleButton.setOnClickListener(componentListener);
    }

    fullScreenButton = findViewById(R.id.exo_fullscreen);
    initializeFullScreenButton(fullScreenButton, this::onFullScreenButtonClicked);
    minimalFullScreenButton = findViewById(R.id.exo_minimal_fullscreen);
    initializeFullScreenButton(minimalFullScreenButton, this::onFullScreenButtonClicked);

    settingsButton = findViewById(R.id.exo_settings);
    if (settingsButton != null) {
      settingsButton.setOnClickListener(componentListener);
    }

    playbackSpeedButton = findViewById(R.id.exo_playback_speed);
    if (playbackSpeedButton != null) {
      playbackSpeedButton.setOnClickListener(componentListener);
    }

    audioTrackButton = findViewById(R.id.exo_audio_track);
    if (audioTrackButton != null) {
      audioTrackButton.setOnClickListener(componentListener);
    }

    TimeBar customTimeBar = findViewById(R.id.exo_progress);
    View timeBarPlaceholder = findViewById(R.id.exo_progress_placeholder);
    if (customTimeBar != null) {
      timeBar = customTimeBar;
    } else if (timeBarPlaceholder != null) {
      // Propagate attrs as timebarAttrs so that DefaultTimeBar's custom attributes are transferred,
      // but standard attributes (e.g. background) are not.
      DefaultTimeBar defaultTimeBar =
          new DefaultTimeBar(context, null, 0, playbackAttrs, R.style.ExoStyledControls_TimeBar);
      defaultTimeBar.setId(R.id.exo_progress);
      defaultTimeBar.setLayoutParams(timeBarPlaceholder.getLayoutParams());
      ViewGroup parent = ((ViewGroup) timeBarPlaceholder.getParent());
      int timeBarIndex = parent.indexOfChild(timeBarPlaceholder);
      parent.removeView(timeBarPlaceholder);
      parent.addView(defaultTimeBar, timeBarIndex);
      timeBar = defaultTimeBar;
    } else {
      timeBar = null;
    }
    if (timeBar != null) {
      timeBar.addListener(componentListener);
    }

    playPauseButton = findViewById(R.id.exo_play_pause);
    if (playPauseButton != null) {
      playPauseButton.setOnClickListener(componentListener);
    }
    previousButton = findViewById(R.id.exo_prev);
    if (previousButton != null) {
      previousButton.setOnClickListener(componentListener);
    }
    nextButton = findViewById(R.id.exo_next);
    if (nextButton != null) {
      nextButton.setOnClickListener(componentListener);
    }
    Typeface typeface = ResourcesCompat.getFont(context, R.font.roboto_medium_numbers);
    View rewButton = findViewById(R.id.exo_rew);
    rewindButtonTextView = rewButton == null ? findViewById(R.id.exo_rew_with_amount) : null;
    if (rewindButtonTextView != null) {
      rewindButtonTextView.setTypeface(typeface);
    }
    rewindButton = rewButton == null ? rewindButtonTextView : rewButton;
    if (rewindButton != null) {
      rewindButton.setOnClickListener(componentListener);
    }
    View ffwdButton = findViewById(R.id.exo_ffwd);
    fastForwardButtonTextView = ffwdButton == null ? findViewById(R.id.exo_ffwd_with_amount) : null;
    if (fastForwardButtonTextView != null) {
      fastForwardButtonTextView.setTypeface(typeface);
    }
    fastForwardButton = ffwdButton == null ? fastForwardButtonTextView : ffwdButton;
    if (fastForwardButton != null) {
      fastForwardButton.setOnClickListener(componentListener);
    }
    repeatToggleButton = findViewById(R.id.exo_repeat_toggle);
    if (repeatToggleButton != null) {
      repeatToggleButton.setOnClickListener(componentListener);
    }
    shuffleButton = findViewById(R.id.exo_shuffle);
    if (shuffleButton != null) {
      shuffleButton.setOnClickListener(componentListener);
    }

    resources = context.getResources();
    buttonAlphaEnabled =
        (float) resources.getInteger(R.integer.exo_media_button_opacity_percentage_enabled) / 100;
    buttonAlphaDisabled =
        (float) resources.getInteger(R.integer.exo_media_button_opacity_percentage_disabled) / 100;

    vrButton = findViewById(R.id.exo_vr);
    if (vrButton != null) {
      updateButton(/* enabled= */ false, vrButton);
    }

    controlViewLayoutManager = new StyledPlayerControlViewLayoutManager(this);
    controlViewLayoutManager.setAnimationEnabled(animationEnabled);

    String[] settingTexts = new String[2];
    Drawable[] settingIcons = new Drawable[2];
    settingTexts[SETTINGS_PLAYBACK_SPEED_POSITION] =
        resources.getString(R.string.exo_controls_playback_speed);
    settingIcons[SETTINGS_PLAYBACK_SPEED_POSITION] =
        resources.getDrawable(R.drawable.exo_styled_controls_speed);
    settingTexts[SETTINGS_AUDIO_TRACK_SELECTION_POSITION] =
        resources.getString(R.string.exo_track_selection_title_audio);
    settingIcons[SETTINGS_AUDIO_TRACK_SELECTION_POSITION] =
        resources.getDrawable(R.drawable.exo_styled_controls_audiotrack);
    settingsAdapter = new SettingsAdapter(settingTexts, settingIcons);
    settingsWindowMargin = resources.getDimensionPixelSize(R.dimen.exo_settings_offset);
    settingsView =
        (RecyclerView)
            LayoutInflater.from(context).inflate(R.layout.exo_styled_settings_list, null);
    settingsView.setAdapter(settingsAdapter);
    settingsView.setLayoutManager(new LinearLayoutManager(getContext()));
    settingsWindow =
        new PopupWindow(settingsView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true);
    if (Util.SDK_INT < 23) {
      // Work around issue where tapping outside of the menu area or pressing the back button
      // doesn't dismiss the menu as expected. See: https://github.com/google/ExoPlayer/issues/8272.
      settingsWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }
    settingsWindow.setOnDismissListener(componentListener);
    needToHideBars = true;

    trackNameProvider = new DefaultTrackNameProvider(getResources());
    subtitleOnButtonDrawable = resources.getDrawable(R.drawable.exo_styled_controls_subtitle_on);
    subtitleOffButtonDrawable = resources.getDrawable(R.drawable.exo_styled_controls_subtitle_off);
    subtitleOnContentDescription =
        resources.getString(R.string.exo_controls_cc_enabled_description);
    subtitleOffContentDescription =
        resources.getString(R.string.exo_controls_cc_disabled_description);
    textTrackSelectionAdapter = new TextTrackSelectionAdapter();
    audioTrackSelectionAdapter = new AudioTrackSelectionAdapter();
    playbackSpeedAdapter =
        new PlaybackSpeedAdapter(
            resources.getStringArray(R.array.exo_playback_speeds),
            resources.getIntArray(R.array.exo_speed_multiplied_by_100));

    fullScreenExitDrawable = resources.getDrawable(R.drawable.exo_styled_controls_fullscreen_exit);
    fullScreenEnterDrawable =
        resources.getDrawable(R.drawable.exo_styled_controls_fullscreen_enter);
    repeatOffButtonDrawable = resources.getDrawable(R.drawable.exo_styled_controls_repeat_off);
    repeatOneButtonDrawable = resources.getDrawable(R.drawable.exo_styled_controls_repeat_one);
    repeatAllButtonDrawable = resources.getDrawable(R.drawable.exo_styled_controls_repeat_all);
    shuffleOnButtonDrawable = resources.getDrawable(R.drawable.exo_styled_controls_shuffle_on);
    shuffleOffButtonDrawable = resources.getDrawable(R.drawable.exo_styled_controls_shuffle_off);
    fullScreenExitContentDescription =
        resources.getString(R.string.exo_controls_fullscreen_exit_description);
    fullScreenEnterContentDescription =
        resources.getString(R.string.exo_controls_fullscreen_enter_description);
    repeatOffButtonContentDescription =
        resources.getString(R.string.exo_controls_repeat_off_description);
    repeatOneButtonContentDescription =
        resources.getString(R.string.exo_controls_repeat_one_description);
    repeatAllButtonContentDescription =
        resources.getString(R.string.exo_controls_repeat_all_description);
    shuffleOnContentDescription = resources.getString(R.string.exo_controls_shuffle_on_description);
    shuffleOffContentDescription =
        resources.getString(R.string.exo_controls_shuffle_off_description);

    // TODO(insun) : Make showing bottomBar configurable. (ex. show_bottom_bar attribute).
    ViewGroup bottomBar = findViewById(R.id.exo_bottom_bar);
    controlViewLayoutManager.setShowButton(bottomBar, true);
    controlViewLayoutManager.setShowButton(fastForwardButton, showFastForwardButton);
    controlViewLayoutManager.setShowButton(rewindButton, showRewindButton);
    controlViewLayoutManager.setShowButton(previousButton, showPreviousButton);
    controlViewLayoutManager.setShowButton(nextButton, showNextButton);
    controlViewLayoutManager.setShowButton(shuffleButton, showShuffleButton);
    controlViewLayoutManager.setShowButton(subtitleButton, showSubtitleButton);
    controlViewLayoutManager.setShowButton(vrButton, showVrButton);
    controlViewLayoutManager.setShowButton(
        repeatToggleButton, repeatToggleModes != RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
    addOnLayoutChangeListener(this::onLayoutChange);
  }

  /**
   * Returns the {@link Player} currently being controlled by this view, or null if no player is
   * set.
   */
  @Nullable
  public Player getPlayer() {
    return player;
  }

  /**
   * Sets the {@link Player} to control.
   *
   * @param player The {@link Player} to control, or {@code null} to detach the current player. Only
   *     players which are accessed on the main thread are supported ({@code
   *     player.getApplicationLooper() == Looper.getMainLooper()}).
   */
  public void setPlayer(@Nullable Player player) {
    Assertions.checkState(Looper.myLooper() == Looper.getMainLooper());
    Assertions.checkArgument(
        player == null || player.getApplicationLooper() == Looper.getMainLooper());
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
    if (player instanceof ExoPlayer) {
      TrackSelector trackSelector = ((ExoPlayer) player).getTrackSelector();
      if (trackSelector instanceof DefaultTrackSelector) {
        this.trackSelector = (DefaultTrackSelector) trackSelector;
      }
    } else {
      this.trackSelector = null;
    }
    updateAll();
  }

  /**
   * Sets whether the time bar should show all windows, as opposed to just the current one. If the
   * timeline has a period with unknown duration or more than {@link
   * #MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR} windows the time bar will fall back to showing a single
   * window.
   *
   * @param showMultiWindowTimeBar Whether the time bar should show all windows.
   */
  public void setShowMultiWindowTimeBar(boolean showMultiWindowTimeBar) {
    this.showMultiWindowTimeBar = showMultiWindowTimeBar;
    updateTimeline();
  }

  /**
   * Sets the millisecond positions of extra ad markers relative to the start of the window (or
   * timeline, if in multi-window mode) and whether each extra ad has been played or not. The
   * markers are shown in addition to any ad markers for ads in the player's timeline.
   *
   * @param extraAdGroupTimesMs The millisecond timestamps of the extra ad markers to show, or
   *     {@code null} to show no extra ad markers.
   * @param extraPlayedAdGroups Whether each ad has been played. Must be the same length as {@code
   *     extraAdGroupTimesMs}, or {@code null} if {@code extraAdGroupTimesMs} is {@code null}.
   */
  public void setExtraAdGroupMarkers(
      @Nullable long[] extraAdGroupTimesMs, @Nullable boolean[] extraPlayedAdGroups) {
    if (extraAdGroupTimesMs == null) {
      this.extraAdGroupTimesMs = new long[0];
      this.extraPlayedAdGroups = new boolean[0];
    } else {
      extraPlayedAdGroups = checkNotNull(extraPlayedAdGroups);
      Assertions.checkArgument(extraAdGroupTimesMs.length == extraPlayedAdGroups.length);
      this.extraAdGroupTimesMs = extraAdGroupTimesMs;
      this.extraPlayedAdGroups = extraPlayedAdGroups;
    }
    updateTimeline();
  }

  /**
   * Adds a {@link VisibilityListener}.
   *
   * @param listener The listener to be notified about visibility changes.
   */
  public void addVisibilityListener(VisibilityListener listener) {
    Assertions.checkNotNull(listener);
    visibilityListeners.add(listener);
  }

  /**
   * Removes a {@link VisibilityListener}.
   *
   * @param listener The listener to be removed.
   */
  public void removeVisibilityListener(VisibilityListener listener) {
    visibilityListeners.remove(listener);
  }

  /**
   * Sets the {@link ProgressUpdateListener}.
   *
   * @param listener The listener to be notified about when progress is updated.
   */
  public void setProgressUpdateListener(@Nullable ProgressUpdateListener listener) {
    this.progressUpdateListener = listener;
  }

  /**
   * @deprecated Use {@link #setControlDispatcher(ControlDispatcher)} instead. The view calls {@link
   *     ControlDispatcher#dispatchPrepare(Player)} instead of {@link
   *     PlaybackPreparer#preparePlayback()}. The {@link DefaultControlDispatcher} that the view
   *     uses by default, calls {@link Player#prepare()}. If you wish to customize this behaviour,
   *     you can provide a custom implementation of {@link
   *     ControlDispatcher#dispatchPrepare(Player)}.
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
  public void setControlDispatcher(ControlDispatcher controlDispatcher) {
    if (this.controlDispatcher != controlDispatcher) {
      this.controlDispatcher = controlDispatcher;
      updateNavigation();
    }
  }

  /**
   * Sets whether the rewind button is shown.
   *
   * @param showRewindButton Whether the rewind button is shown.
   */
  public void setShowRewindButton(boolean showRewindButton) {
    controlViewLayoutManager.setShowButton(rewindButton, showRewindButton);
    updateNavigation();
  }

  /**
   * Sets whether the fast forward button is shown.
   *
   * @param showFastForwardButton Whether the fast forward button is shown.
   */
  public void setShowFastForwardButton(boolean showFastForwardButton) {
    controlViewLayoutManager.setShowButton(fastForwardButton, showFastForwardButton);
    updateNavigation();
  }

  /**
   * Sets whether the previous button is shown.
   *
   * @param showPreviousButton Whether the previous button is shown.
   */
  public void setShowPreviousButton(boolean showPreviousButton) {
    controlViewLayoutManager.setShowButton(previousButton, showPreviousButton);
    updateNavigation();
  }

  /**
   * Sets whether the next button is shown.
   *
   * @param showNextButton Whether the next button is shown.
   */
  public void setShowNextButton(boolean showNextButton) {
    controlViewLayoutManager.setShowButton(nextButton, showNextButton);
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
    if (isFullyVisible()) {
      controlViewLayoutManager.resetHideCallbacks();
    }
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
    controlViewLayoutManager.setShowButton(
        repeatToggleButton, repeatToggleModes != RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
    updateRepeatModeButton();
  }

  /** Returns whether the shuffle button is shown. */
  public boolean getShowShuffleButton() {
    return controlViewLayoutManager.getShowButton(shuffleButton);
  }

  /**
   * Sets whether the shuffle button is shown.
   *
   * @param showShuffleButton Whether the shuffle button is shown.
   */
  public void setShowShuffleButton(boolean showShuffleButton) {
    controlViewLayoutManager.setShowButton(shuffleButton, showShuffleButton);
    updateShuffleButton();
  }

  /** Returns whether the subtitle button is shown. */
  public boolean getShowSubtitleButton() {
    return controlViewLayoutManager.getShowButton(subtitleButton);
  }

  /**
   * Sets whether the subtitle button is shown.
   *
   * @param showSubtitleButton Whether the subtitle button is shown.
   */
  public void setShowSubtitleButton(boolean showSubtitleButton) {
    controlViewLayoutManager.setShowButton(subtitleButton, showSubtitleButton);
  }

  /** Returns whether the VR button is shown. */
  public boolean getShowVrButton() {
    return controlViewLayoutManager.getShowButton(vrButton);
  }

  /**
   * Sets whether the VR button is shown.
   *
   * @param showVrButton Whether the VR button is shown.
   */
  public void setShowVrButton(boolean showVrButton) {
    controlViewLayoutManager.setShowButton(vrButton, showVrButton);
  }

  /**
   * Sets listener for the VR button.
   *
   * @param onClickListener Listener for the VR button, or null to clear the listener.
   */
  public void setVrButtonListener(@Nullable OnClickListener onClickListener) {
    if (vrButton != null) {
      vrButton.setOnClickListener(onClickListener);
      updateButton(onClickListener != null, vrButton);
    }
  }

  /**
   * Sets whether an animation is used to show and hide the playback controls.
   *
   * @param animationEnabled Whether an animation is applied to show and hide playback controls.
   */
  public void setAnimationEnabled(boolean animationEnabled) {
    controlViewLayoutManager.setAnimationEnabled(animationEnabled);
  }

  /** Returns whether an animation is used to show and hide the playback controls. */
  public boolean isAnimationEnabled() {
    return controlViewLayoutManager.isAnimationEnabled();
  }

  /**
   * Sets the minimum interval between time bar position updates.
   *
   * <p>Note that smaller intervals, e.g. 33ms, will result in a smooth movement but will use more
   * CPU resources while the time bar is visible, whereas larger intervals, e.g. 200ms, will result
   * in a step-wise update with less CPU usage.
   *
   * @param minUpdateIntervalMs The minimum interval between time bar position updates, in
   *     milliseconds.
   */
  public void setTimeBarMinUpdateInterval(int minUpdateIntervalMs) {
    // Do not accept values below 16ms (60fps) and larger than the maximum update interval.
    timeBarMinUpdateIntervalMs =
        Util.constrainValue(minUpdateIntervalMs, 16, MAX_UPDATE_INTERVAL_MS);
  }

  /**
   * Sets a listener to be called when the fullscreen mode should be changed. A non-null listener
   * needs to be set in order to display the fullscreen button.
   *
   * @param listener The listener to be called. A value of <code>null</code> removes any existing
   *     listener and hides the fullscreen button.
   */
  public void setOnFullScreenModeChangedListener(
      @Nullable OnFullScreenModeChangedListener listener) {
    onFullScreenModeChangedListener = listener;
    updateFullScreenButtonVisibility(fullScreenButton, listener != null);
    updateFullScreenButtonVisibility(minimalFullScreenButton, listener != null);
  }

  /**
   * Shows the playback controls. If {@link #getShowTimeoutMs()} is positive then the controls will
   * be automatically hidden after this duration of time has elapsed without user input.
   */
  public void show() {
    controlViewLayoutManager.show();
  }

  /** Hides the controller. */
  public void hide() {
    controlViewLayoutManager.hide();
  }

  /** Hides the controller without any animation. */
  public void hideImmediately() {
    controlViewLayoutManager.hideImmediately();
  }

  /** Returns whether the controller is fully visible, which means all UI controls are visible. */
  public boolean isFullyVisible() {
    return controlViewLayoutManager.isFullyVisible();
  }

  /** Returns whether the controller is currently visible. */
  public boolean isVisible() {
    return getVisibility() == VISIBLE;
  }

  /* package */ void notifyOnVisibilityChange() {
    for (VisibilityListener visibilityListener : visibilityListeners) {
      visibilityListener.onVisibilityChange(getVisibility());
    }
  }

  /* package */ void updateAll() {
    updatePlayPauseButton();
    updateNavigation();
    updateRepeatModeButton();
    updateShuffleButton();
    updateTrackLists();
    updatePlaybackSpeedList();
    updateTimeline();
  }

  private void updatePlayPauseButton() {
    if (!isVisible() || !isAttachedToWindow) {
      return;
    }
    if (playPauseButton != null) {
      if (shouldShowPauseButton()) {
        ((ImageView) playPauseButton)
            .setImageDrawable(resources.getDrawable(R.drawable.exo_styled_controls_pause));
        playPauseButton.setContentDescription(
            resources.getString(R.string.exo_controls_pause_description));
      } else {
        ((ImageView) playPauseButton)
            .setImageDrawable(resources.getDrawable(R.drawable.exo_styled_controls_play));
        playPauseButton.setContentDescription(
            resources.getString(R.string.exo_controls_play_description));
      }
    }
  }

  private void updateNavigation() {
    if (!isVisible() || !isAttachedToWindow) {
      return;
    }

    @Nullable Player player = this.player;
    boolean enableSeeking = false;
    boolean enablePrevious = false;
    boolean enableRewind = false;
    boolean enableFastForward = false;
    boolean enableNext = false;
    if (player != null) {
      Timeline timeline = player.getCurrentTimeline();
      if (!timeline.isEmpty() && !player.isPlayingAd()) {
        boolean isSeekable = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
        timeline.getWindow(player.getCurrentWindowIndex(), window);
        enableSeeking = isSeekable;
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
    }

    if (enableRewind) {
      updateRewindButton();
    }
    if (enableFastForward) {
      updateFastForwardButton();
    }

    updateButton(enablePrevious, previousButton);
    updateButton(enableRewind, rewindButton);
    updateButton(enableFastForward, fastForwardButton);
    updateButton(enableNext, nextButton);
    if (timeBar != null) {
      timeBar.setEnabled(enableSeeking);
    }
  }

  private void updateRewindButton() {
    if (controlDispatcher instanceof DefaultControlDispatcher) {
      rewindMs = ((DefaultControlDispatcher) controlDispatcher).getRewindIncrementMs();
    }
    int rewindSec = (int) (rewindMs / 1_000);
    if (rewindButtonTextView != null) {
      rewindButtonTextView.setText(String.valueOf(rewindSec));
    }
    if (rewindButton != null) {
      rewindButton.setContentDescription(
          resources.getQuantityString(
              R.plurals.exo_controls_rewind_by_amount_description, rewindSec, rewindSec));
    }
  }

  private void updateFastForwardButton() {
    if (controlDispatcher instanceof DefaultControlDispatcher) {
      fastForwardMs = ((DefaultControlDispatcher) controlDispatcher).getFastForwardIncrementMs();
    }
    int fastForwardSec = (int) (fastForwardMs / 1_000);
    if (fastForwardButtonTextView != null) {
      fastForwardButtonTextView.setText(String.valueOf(fastForwardSec));
    }
    if (fastForwardButton != null) {
      fastForwardButton.setContentDescription(
          resources.getQuantityString(
              R.plurals.exo_controls_fastforward_by_amount_description,
              fastForwardSec,
              fastForwardSec));
    }
  }

  private void updateRepeatModeButton() {
    if (!isVisible() || !isAttachedToWindow || repeatToggleButton == null) {
      return;
    }

    if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE) {
      updateButton(/* enabled= */ false, repeatToggleButton);
      return;
    }

    @Nullable Player player = this.player;
    if (player == null) {
      updateButton(/* enabled= */ false, repeatToggleButton);
      repeatToggleButton.setImageDrawable(repeatOffButtonDrawable);
      repeatToggleButton.setContentDescription(repeatOffButtonContentDescription);
      return;
    }

    updateButton(/* enabled= */ true, repeatToggleButton);
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
      default:
        // Never happens.
    }
  }

  private void updateShuffleButton() {
    if (!isVisible() || !isAttachedToWindow || shuffleButton == null) {
      return;
    }

    @Nullable Player player = this.player;
    if (!controlViewLayoutManager.getShowButton(shuffleButton)) {
      updateButton(/* enabled= */ false, shuffleButton);
    } else if (player == null) {
      updateButton(/* enabled= */ false, shuffleButton);
      shuffleButton.setImageDrawable(shuffleOffButtonDrawable);
      shuffleButton.setContentDescription(shuffleOffContentDescription);
    } else {
      updateButton(/* enabled= */ true, shuffleButton);
      shuffleButton.setImageDrawable(
          player.getShuffleModeEnabled() ? shuffleOnButtonDrawable : shuffleOffButtonDrawable);
      shuffleButton.setContentDescription(
          player.getShuffleModeEnabled()
              ? shuffleOnContentDescription
              : shuffleOffContentDescription);
    }
  }

  private void updateTrackLists() {
    initTrackSelectionAdapter();
    updateButton(textTrackSelectionAdapter.getItemCount() > 0, subtitleButton);
  }

  private void initTrackSelectionAdapter() {
    textTrackSelectionAdapter.clear();
    audioTrackSelectionAdapter.clear();
    if (player == null || trackSelector == null) {
      return;
    }
    DefaultTrackSelector trackSelector = this.trackSelector;
    @Nullable MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo == null) {
      return;
    }
    List<TrackInfo> textTracks = new ArrayList<>();
    List<TrackInfo> audioTracks = new ArrayList<>();
    List<Integer> textRendererIndices = new ArrayList<>();
    List<Integer> audioRendererIndices = new ArrayList<>();
    for (int rendererIndex = 0;
        rendererIndex < mappedTrackInfo.getRendererCount();
        rendererIndex++) {
      if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_TEXT
          && controlViewLayoutManager.getShowButton(subtitleButton)) {
        // Get TrackSelection at the corresponding renderer index.
        gatherTrackInfosForAdapter(mappedTrackInfo, rendererIndex, textTracks);
        textRendererIndices.add(rendererIndex);
      } else if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_AUDIO) {
        gatherTrackInfosForAdapter(mappedTrackInfo, rendererIndex, audioTracks);
        audioRendererIndices.add(rendererIndex);
      }
    }
    textTrackSelectionAdapter.init(textRendererIndices, textTracks, mappedTrackInfo);
    audioTrackSelectionAdapter.init(audioRendererIndices, audioTracks, mappedTrackInfo);
  }

  private void gatherTrackInfosForAdapter(
      MappedTrackInfo mappedTrackInfo, int rendererIndex, List<TrackInfo> tracks) {
    TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex);

    TrackSelectionArray trackSelections = checkNotNull(player).getCurrentTrackSelections();
    @Nullable TrackSelection trackSelection = trackSelections.get(rendererIndex);

    for (int groupIndex = 0; groupIndex < trackGroupArray.length; groupIndex++) {
      TrackGroup trackGroup = trackGroupArray.get(groupIndex);
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        Format format = trackGroup.getFormat(trackIndex);
        if (mappedTrackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex)
            == C.FORMAT_HANDLED) {
          boolean trackIsSelected =
              trackSelection != null && trackSelection.indexOf(format) != C.INDEX_UNSET;
          tracks.add(
              new TrackInfo(
                  rendererIndex,
                  groupIndex,
                  trackIndex,
                  trackNameProvider.getTrackName(format),
                  trackIsSelected));
        }
      }
    }
  }

  private void updateTimeline() {
    @Nullable Player player = this.player;
    if (player == null) {
      return;
    }
    multiWindowTimeBar =
        showMultiWindowTimeBar && canShowMultiWindowTimeBar(player.getCurrentTimeline(), window);
    currentWindowOffset = 0;
    long durationUs = 0;
    int adGroupCount = 0;
    Timeline timeline = player.getCurrentTimeline();
    if (!timeline.isEmpty()) {
      int currentWindowIndex = player.getCurrentWindowIndex();
      int firstWindowIndex = multiWindowTimeBar ? 0 : currentWindowIndex;
      int lastWindowIndex = multiWindowTimeBar ? timeline.getWindowCount() - 1 : currentWindowIndex;
      for (int i = firstWindowIndex; i <= lastWindowIndex; i++) {
        if (i == currentWindowIndex) {
          currentWindowOffset = C.usToMs(durationUs);
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
            if (adGroupTimeInWindowUs >= 0) {
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
    long durationMs = C.usToMs(durationUs);
    if (durationView != null) {
      durationView.setText(Util.getStringForTime(formatBuilder, formatter, durationMs));
    }
    if (timeBar != null) {
      timeBar.setDuration(durationMs);
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
    updateProgress();
  }

  private void updateProgress() {
    if (!isVisible() || !isAttachedToWindow) {
      return;
    }
    @Nullable Player player = this.player;
    long position = 0;
    long bufferedPosition = 0;
    if (player != null) {
      position = currentWindowOffset + player.getContentPosition();
      bufferedPosition = currentWindowOffset + player.getContentBufferedPosition();
    }
    if (positionView != null && !scrubbing) {
      positionView.setText(Util.getStringForTime(formatBuilder, formatter, position));
    }
    if (timeBar != null) {
      timeBar.setPosition(position);
      timeBar.setBufferedPosition(bufferedPosition);
    }
    if (progressUpdateListener != null) {
      progressUpdateListener.onProgressUpdate(position, bufferedPosition);
    }

    // Cancel any pending updates and schedule a new one if necessary.
    removeCallbacks(updateProgressAction);
    int playbackState = player == null ? Player.STATE_IDLE : player.getPlaybackState();
    if (player != null && player.isPlaying()) {
      long mediaTimeDelayMs =
          timeBar != null ? timeBar.getPreferredUpdateDelay() : MAX_UPDATE_INTERVAL_MS;

      // Limit delay to the start of the next full second to ensure position display is smooth.
      long mediaTimeUntilNextFullSecondMs = 1000 - position % 1000;
      mediaTimeDelayMs = Math.min(mediaTimeDelayMs, mediaTimeUntilNextFullSecondMs);

      // Calculate the delay until the next update in real time, taking playback speed into account.
      float playbackSpeed = player.getPlaybackParameters().speed;
      long delayMs =
          playbackSpeed > 0 ? (long) (mediaTimeDelayMs / playbackSpeed) : MAX_UPDATE_INTERVAL_MS;

      // Constrain the delay to avoid too frequent / infrequent updates.
      delayMs = Util.constrainValue(delayMs, timeBarMinUpdateIntervalMs, MAX_UPDATE_INTERVAL_MS);
      postDelayed(updateProgressAction, delayMs);
    } else if (playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
      postDelayed(updateProgressAction, MAX_UPDATE_INTERVAL_MS);
    }
  }

  private void updatePlaybackSpeedList() {
    if (player == null) {
      return;
    }
    playbackSpeedAdapter.updateSelectedIndex(player.getPlaybackParameters().speed);
    settingsAdapter.setSubTextAtPosition(
        SETTINGS_PLAYBACK_SPEED_POSITION, playbackSpeedAdapter.getSelectedText());
  }

  private void updateSettingsWindowSize() {
    settingsView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);

    int maxWidth = getWidth() - settingsWindowMargin * 2;
    int itemWidth = settingsView.getMeasuredWidth();
    int width = Math.min(itemWidth, maxWidth);
    settingsWindow.setWidth(width);

    int maxHeight = getHeight() - settingsWindowMargin * 2;
    int totalHeight = settingsView.getMeasuredHeight();
    int height = Math.min(maxHeight, totalHeight);
    settingsWindow.setHeight(height);
  }

  private void displaySettingsWindow(RecyclerView.Adapter<?> adapter) {
    settingsView.setAdapter(adapter);

    updateSettingsWindowSize();

    needToHideBars = false;
    settingsWindow.dismiss();
    needToHideBars = true;

    int xoff = getWidth() - settingsWindow.getWidth() - settingsWindowMargin;
    int yoff = -settingsWindow.getHeight() - settingsWindowMargin;

    settingsWindow.showAsDropDown(this, xoff, yoff);
  }

  private void setPlaybackSpeed(float speed) {
    if (player == null) {
      return;
    }
    controlDispatcher.dispatchSetPlaybackParameters(
        player, player.getPlaybackParameters().withSpeed(speed));
  }

  /* package */ void requestPlayPauseFocus() {
    if (playPauseButton != null) {
      playPauseButton.requestFocus();
    }
  }

  private void updateButton(boolean enabled, @Nullable View view) {
    if (view == null) {
      return;
    }
    view.setEnabled(enabled);
    view.setAlpha(enabled ? buttonAlphaEnabled : buttonAlphaDisabled);
  }

  private void seekToTimeBarPosition(Player player, long positionMs) {
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
    boolean dispatched = seekTo(player, windowIndex, positionMs);
    if (!dispatched) {
      // The seek wasn't dispatched then the progress bar scrubber will be in the wrong position.
      // Trigger a progress update to snap it back.
      updateProgress();
    }
  }

  private boolean seekTo(Player player, int windowIndex, long positionMs) {
    return controlDispatcher.dispatchSeekTo(player, windowIndex, positionMs);
  }

  private void onFullScreenButtonClicked(View v) {
    if (onFullScreenModeChangedListener == null) {
      return;
    }

    isFullScreen = !isFullScreen;
    updateFullScreenButtonForState(fullScreenButton, isFullScreen);
    updateFullScreenButtonForState(minimalFullScreenButton, isFullScreen);
    if (onFullScreenModeChangedListener != null) {
      onFullScreenModeChangedListener.onFullScreenModeChanged(isFullScreen);
    }
  }

  private void updateFullScreenButtonForState(
      @Nullable ImageView fullScreenButton, boolean isFullScreen) {
    if (fullScreenButton == null) {
      return;
    }
    if (isFullScreen) {
      fullScreenButton.setImageDrawable(fullScreenExitDrawable);
      fullScreenButton.setContentDescription(fullScreenExitContentDescription);
    } else {
      fullScreenButton.setImageDrawable(fullScreenEnterDrawable);
      fullScreenButton.setContentDescription(fullScreenEnterContentDescription);
    }
  }

  private void onSettingViewClicked(int position) {
    if (position == SETTINGS_PLAYBACK_SPEED_POSITION) {
      displaySettingsWindow(playbackSpeedAdapter);
    } else if (position == SETTINGS_AUDIO_TRACK_SELECTION_POSITION) {
      displaySettingsWindow(audioTrackSelectionAdapter);
    } else {
      settingsWindow.dismiss();
    }
  }

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    controlViewLayoutManager.onAttachedToWindow();
    isAttachedToWindow = true;
    if (isFullyVisible()) {
      controlViewLayoutManager.resetHideCallbacks();
    }
    updateAll();
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    controlViewLayoutManager.onDetachedFromWindow();
    isAttachedToWindow = false;
    removeCallbacks(updateProgressAction);
    controlViewLayoutManager.removeHideCallbacks();
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
    @Nullable Player player = this.player;
    if (player == null || !isHandledMediaKey(keyCode)) {
      return false;
    }
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
        if (player.getPlaybackState() != Player.STATE_ENDED) {
          controlDispatcher.dispatchFastForward(player);
        }
      } else if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
        controlDispatcher.dispatchRewind(player);
      } else if (event.getRepeatCount() == 0) {
        switch (keyCode) {
          case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
          case KeyEvent.KEYCODE_HEADSETHOOK:
            dispatchPlayPause(player);
            break;
          case KeyEvent.KEYCODE_MEDIA_PLAY:
            dispatchPlay(player);
            break;
          case KeyEvent.KEYCODE_MEDIA_PAUSE:
            dispatchPause(player);
            break;
          case KeyEvent.KEYCODE_MEDIA_NEXT:
            controlDispatcher.dispatchNext(player);
            break;
          case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            controlDispatcher.dispatchPrevious(player);
            break;
          default:
            break;
        }
      }
    }
    return true;
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    controlViewLayoutManager.onLayout(changed, left, top, right, bottom);
  }

  private void onLayoutChange(
      View v,
      int left,
      int top,
      int right,
      int bottom,
      int oldLeft,
      int oldTop,
      int oldRight,
      int oldBottom) {
    int width = right - left;
    int height = bottom - top;
    int oldWidth = oldRight - oldLeft;
    int oldHeight = oldBottom - oldTop;

    if ((width != oldWidth || height != oldHeight) && settingsWindow.isShowing()) {
      updateSettingsWindowSize();
      int xOffset = getWidth() - settingsWindow.getWidth() - settingsWindowMargin;
      int yOffset = -settingsWindow.getHeight() - settingsWindowMargin;
      settingsWindow.update(v, xOffset, yOffset, -1, -1);
    }
  }

  private boolean shouldShowPauseButton() {
    return player != null
        && player.getPlaybackState() != Player.STATE_ENDED
        && player.getPlaybackState() != Player.STATE_IDLE
        && player.getPlayWhenReady();
  }

  private void dispatchPlayPause(Player player) {
    @State int state = player.getPlaybackState();
    if (state == Player.STATE_IDLE || state == Player.STATE_ENDED || !player.getPlayWhenReady()) {
      dispatchPlay(player);
    } else {
      dispatchPause(player);
    }
  }

  @SuppressWarnings("deprecation")
  private void dispatchPlay(Player player) {
    @State int state = player.getPlaybackState();
    if (state == Player.STATE_IDLE) {
      if (playbackPreparer != null) {
        playbackPreparer.preparePlayback();
      } else {
        controlDispatcher.dispatchPrepare(player);
      }
    } else if (state == Player.STATE_ENDED) {
      seekTo(player, player.getCurrentWindowIndex(), C.TIME_UNSET);
    }
    controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ true);
  }

  private void dispatchPause(Player player) {
    controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ false);
  }

  @SuppressLint("InlinedApi")
  private static boolean isHandledMediaKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
        || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        || keyCode == KeyEvent.KEYCODE_HEADSETHOOK
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

  private static void initializeFullScreenButton(View fullScreenButton, OnClickListener listener) {
    if (fullScreenButton == null) {
      return;
    }
    fullScreenButton.setVisibility(GONE);
    fullScreenButton.setOnClickListener(listener);
  }

  private static void updateFullScreenButtonVisibility(
      @Nullable View fullScreenButton, boolean visible) {
    if (fullScreenButton == null) {
      return;
    }
    if (visible) {
      fullScreenButton.setVisibility(VISIBLE);
    } else {
      fullScreenButton.setVisibility(GONE);
    }
  }

  @SuppressWarnings("ResourceType")
  private static @RepeatModeUtil.RepeatToggleModes int getRepeatToggleModes(
      TypedArray a, @RepeatModeUtil.RepeatToggleModes int defaultValue) {
    return a.getInt(R.styleable.StyledPlayerControlView_repeat_toggle_modes, defaultValue);
  }

  private final class ComponentListener
      implements Player.EventListener,
          TimeBar.OnScrubListener,
          OnClickListener,
          PopupWindow.OnDismissListener {

    @Override
    public void onScrubStart(TimeBar timeBar, long position) {
      scrubbing = true;
      if (positionView != null) {
        positionView.setText(Util.getStringForTime(formatBuilder, formatter, position));
      }
      controlViewLayoutManager.removeHideCallbacks();
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
        seekToTimeBarPosition(player, position);
      }
      controlViewLayoutManager.resetHideCallbacks();
    }

    @Override
    public void onEvents(Player player, Events events) {
      if (events.containsAny(EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED)) {
        updatePlayPauseButton();
      }
      if (events.containsAny(
          EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_IS_PLAYING_CHANGED)) {
        updateProgress();
      }
      if (events.contains(EVENT_REPEAT_MODE_CHANGED)) {
        updateRepeatModeButton();
      }
      if (events.contains(EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
        updateShuffleButton();
      }
      if (events.containsAny(
          EVENT_REPEAT_MODE_CHANGED,
          EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          EVENT_POSITION_DISCONTINUITY,
          EVENT_TIMELINE_CHANGED)) {
        updateNavigation();
      }
      if (events.containsAny(EVENT_POSITION_DISCONTINUITY, EVENT_TIMELINE_CHANGED)) {
        updateTimeline();
      }
      if (events.contains(EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
        updatePlaybackSpeedList();
      }
      if (events.contains(EVENT_TRACKS_CHANGED)) {
        updateTrackLists();
      }
    }

    @Override
    public void onDismiss() {
      if (needToHideBars) {
        controlViewLayoutManager.resetHideCallbacks();
      }
    }

    @Override
    public void onClick(View view) {
      @Nullable Player player = StyledPlayerControlView.this.player;
      if (player == null) {
        return;
      }
      controlViewLayoutManager.resetHideCallbacks();
      if (nextButton == view) {
        controlDispatcher.dispatchNext(player);
      } else if (previousButton == view) {
        controlDispatcher.dispatchPrevious(player);
      } else if (fastForwardButton == view) {
        if (player.getPlaybackState() != Player.STATE_ENDED) {
          controlDispatcher.dispatchFastForward(player);
        }
      } else if (rewindButton == view) {
        controlDispatcher.dispatchRewind(player);
      } else if (playPauseButton == view) {
        dispatchPlayPause(player);
      } else if (repeatToggleButton == view) {
        controlDispatcher.dispatchSetRepeatMode(
            player, RepeatModeUtil.getNextRepeatMode(player.getRepeatMode(), repeatToggleModes));
      } else if (shuffleButton == view) {
        controlDispatcher.dispatchSetShuffleModeEnabled(player, !player.getShuffleModeEnabled());
      } else if (settingsButton == view) {
        controlViewLayoutManager.removeHideCallbacks();
        displaySettingsWindow(settingsAdapter);
      } else if (playbackSpeedButton == view) {
        controlViewLayoutManager.removeHideCallbacks();
        displaySettingsWindow(playbackSpeedAdapter);
      } else if (audioTrackButton == view) {
        controlViewLayoutManager.removeHideCallbacks();
        displaySettingsWindow(audioTrackSelectionAdapter);
      } else if (subtitleButton == view) {
        controlViewLayoutManager.removeHideCallbacks();
        displaySettingsWindow(textTrackSelectionAdapter);
      }
    }
  }

  private class SettingsAdapter extends RecyclerView.Adapter<SettingViewHolder> {

    private final String[] mainTexts;
    private final String[] subTexts;
    private final Drawable[] iconIds;

    public SettingsAdapter(String[] mainTexts, Drawable[] iconIds) {
      this.mainTexts = mainTexts;
      this.subTexts = new String[mainTexts.length];
      this.iconIds = iconIds;
    }

    @Override
    public SettingViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
      View v =
          LayoutInflater.from(getContext()).inflate(R.layout.exo_styled_settings_list_item, null);
      return new SettingViewHolder(v);
    }

    @Override
    public void onBindViewHolder(SettingViewHolder holder, int position) {
      holder.mainTextView.setText(mainTexts[position]);

      if (subTexts[position] == null) {
        holder.subTextView.setVisibility(GONE);
      } else {
        holder.subTextView.setText(subTexts[position]);
      }

      if (iconIds[position] == null) {
        holder.iconView.setVisibility(GONE);
      } else {
        holder.iconView.setImageDrawable(iconIds[position]);
      }
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public int getItemCount() {
      return mainTexts.length;
    }

    public void setSubTextAtPosition(int position, String subText) {
      this.subTexts[position] = subText;
    }
  }

  private final class SettingViewHolder extends RecyclerView.ViewHolder {

    private final TextView mainTextView;
    private final TextView subTextView;
    private final ImageView iconView;

    public SettingViewHolder(View itemView) {
      super(itemView);
      if (Util.SDK_INT < 26) {
        // Workaround for https://github.com/google/ExoPlayer/issues/9061.
        itemView.setFocusable(true);
      }
      mainTextView = itemView.findViewById(R.id.exo_main_text);
      subTextView = itemView.findViewById(R.id.exo_sub_text);
      iconView = itemView.findViewById(R.id.exo_icon);
      itemView.setOnClickListener(v -> onSettingViewClicked(getAdapterPosition()));
    }
  }

  private final class PlaybackSpeedAdapter extends RecyclerView.Adapter<SubSettingViewHolder> {

    private final String[] playbackSpeedTexts;
    private final int[] playbackSpeedsMultBy100;
    private int selectedIndex;

    public PlaybackSpeedAdapter(String[] playbackSpeedTexts, int[] playbackSpeedsMultBy100) {
      this.playbackSpeedTexts = playbackSpeedTexts;
      this.playbackSpeedsMultBy100 = playbackSpeedsMultBy100;
    }

    public void updateSelectedIndex(float playbackSpeed) {
      int currentSpeedMultBy100 = Math.round(playbackSpeed * 100);
      int closestMatchIndex = 0;
      int closestMatchDifference = Integer.MAX_VALUE;
      for (int i = 0; i < playbackSpeedsMultBy100.length; i++) {
        int difference = Math.abs(currentSpeedMultBy100 - playbackSpeedsMultBy100[i]);
        if (difference < closestMatchDifference) {
          closestMatchIndex = i;
          closestMatchDifference = difference;
        }
      }
      selectedIndex = closestMatchIndex;
    }

    public String getSelectedText() {
      return playbackSpeedTexts[selectedIndex];
    }

    @Override
    public SubSettingViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View v =
          LayoutInflater.from(getContext())
              .inflate(R.layout.exo_styled_sub_settings_list_item, null);
      return new SubSettingViewHolder(v);
    }

    @Override
    public void onBindViewHolder(SubSettingViewHolder holder, int position) {
      if (position < playbackSpeedTexts.length) {
        holder.textView.setText(playbackSpeedTexts[position]);
      }
      holder.checkView.setVisibility(position == selectedIndex ? VISIBLE : INVISIBLE);
      holder.itemView.setOnClickListener(
          v -> {
            if (position != selectedIndex) {
              float speed = playbackSpeedsMultBy100[position] / 100.0f;
              setPlaybackSpeed(speed);
            }
            settingsWindow.dismiss();
          });
    }

    @Override
    public int getItemCount() {
      return playbackSpeedTexts.length;
    }
  }

  private static final class TrackInfo {

    public final int rendererIndex;
    public final int groupIndex;
    public final int trackIndex;
    public final String trackName;
    public final boolean selected;

    public TrackInfo(
        int rendererIndex, int groupIndex, int trackIndex, String trackName, boolean selected) {
      this.rendererIndex = rendererIndex;
      this.groupIndex = groupIndex;
      this.trackIndex = trackIndex;
      this.trackName = trackName;
      this.selected = selected;
    }
  }

  private final class TextTrackSelectionAdapter extends TrackSelectionAdapter {
    @Override
    public void init(
        List<Integer> rendererIndices,
        List<TrackInfo> trackInfos,
        MappedTrackInfo mappedTrackInfo) {
      boolean subtitleIsOn = false;
      for (int i = 0; i < trackInfos.size(); i++) {
        if (trackInfos.get(i).selected) {
          subtitleIsOn = true;
          break;
        }
      }

      if (subtitleButton != null) {
        subtitleButton.setImageDrawable(
            subtitleIsOn ? subtitleOnButtonDrawable : subtitleOffButtonDrawable);
        subtitleButton.setContentDescription(
            subtitleIsOn ? subtitleOnContentDescription : subtitleOffContentDescription);
      }
      this.rendererIndices = rendererIndices;
      this.tracks = trackInfos;
      this.mappedTrackInfo = mappedTrackInfo;
    }

    @Override
    public void onBindViewHolderAtZeroPosition(SubSettingViewHolder holder) {
      // CC options include "Off" at the first position, which disables text rendering.
      holder.textView.setText(R.string.exo_track_selection_none);
      boolean isTrackSelectionOff = true;
      for (int i = 0; i < tracks.size(); i++) {
        if (tracks.get(i).selected) {
          isTrackSelectionOff = false;
          break;
        }
      }
      holder.checkView.setVisibility(isTrackSelectionOff ? VISIBLE : INVISIBLE);
      holder.itemView.setOnClickListener(
          v -> {
            if (trackSelector != null) {
              ParametersBuilder parametersBuilder = trackSelector.getParameters().buildUpon();
              for (int i = 0; i < rendererIndices.size(); i++) {
                int rendererIndex = rendererIndices.get(i);
                parametersBuilder =
                    parametersBuilder
                        .clearSelectionOverrides(rendererIndex)
                        .setRendererDisabled(rendererIndex, true);
              }
              checkNotNull(trackSelector).setParameters(parametersBuilder);
              settingsWindow.dismiss();
            }
          });
    }

    @Override
    public void onBindViewHolder(SubSettingViewHolder holder, int position) {
      super.onBindViewHolder(holder, position);
      if (position > 0) {
        TrackInfo track = tracks.get(position - 1);
        holder.checkView.setVisibility(track.selected ? VISIBLE : INVISIBLE);
      }
    }

    @Override
    public void onTrackSelection(String subtext) {
      // No-op
    }
  }

  private final class AudioTrackSelectionAdapter extends TrackSelectionAdapter {

    @Override
    public void onBindViewHolderAtZeroPosition(SubSettingViewHolder holder) {
      // Audio track selection option includes "Auto" at the top.
      holder.textView.setText(R.string.exo_track_selection_auto);
      // hasSelectionOverride is true means there is an explicit track selection, not "Auto".
      boolean hasSelectionOverride = false;
      DefaultTrackSelector.Parameters parameters = checkNotNull(trackSelector).getParameters();
      for (int i = 0; i < rendererIndices.size(); i++) {
        int rendererIndex = rendererIndices.get(i);
        TrackGroupArray trackGroups = checkNotNull(mappedTrackInfo).getTrackGroups(rendererIndex);
        if (parameters.hasSelectionOverride(rendererIndex, trackGroups)) {
          hasSelectionOverride = true;
          break;
        }
      }
      holder.checkView.setVisibility(hasSelectionOverride ? INVISIBLE : VISIBLE);
      holder.itemView.setOnClickListener(
          v -> {
            if (trackSelector != null) {
              ParametersBuilder parametersBuilder = trackSelector.getParameters().buildUpon();
              for (int i = 0; i < rendererIndices.size(); i++) {
                int rendererIndex = rendererIndices.get(i);
                parametersBuilder = parametersBuilder.clearSelectionOverrides(rendererIndex);
              }
              checkNotNull(trackSelector).setParameters(parametersBuilder);
            }
            settingsAdapter.setSubTextAtPosition(
                SETTINGS_AUDIO_TRACK_SELECTION_POSITION,
                getResources().getString(R.string.exo_track_selection_auto));
            settingsWindow.dismiss();
          });
    }

    @Override
    public void onTrackSelection(String subtext) {
      settingsAdapter.setSubTextAtPosition(SETTINGS_AUDIO_TRACK_SELECTION_POSITION, subtext);
    }

    @Override
    public void init(
        List<Integer> rendererIndices,
        List<TrackInfo> trackInfos,
        MappedTrackInfo mappedTrackInfo) {
      // Update subtext in settings menu with current audio track selection.
      boolean hasSelectionOverride = false;
      for (int i = 0; i < rendererIndices.size(); i++) {
        int rendererIndex = rendererIndices.get(i);
        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
        if (trackSelector != null
            && trackSelector.getParameters().hasSelectionOverride(rendererIndex, trackGroups)) {
          hasSelectionOverride = true;
          break;
        }
      }
      if (trackInfos.isEmpty()) {
        settingsAdapter.setSubTextAtPosition(
            SETTINGS_AUDIO_TRACK_SELECTION_POSITION,
            getResources().getString(R.string.exo_track_selection_none));
        // TODO(insun) : Make the audio item in main settings (settingsAdapater)
        //  to be non-clickable.
      } else if (!hasSelectionOverride) {
        settingsAdapter.setSubTextAtPosition(
            SETTINGS_AUDIO_TRACK_SELECTION_POSITION,
            getResources().getString(R.string.exo_track_selection_auto));
      } else {
        for (int i = 0; i < trackInfos.size(); i++) {
          TrackInfo track = trackInfos.get(i);
          if (track.selected) {
            settingsAdapter.setSubTextAtPosition(
                SETTINGS_AUDIO_TRACK_SELECTION_POSITION, track.trackName);
            break;
          }
        }
      }
      this.rendererIndices = rendererIndices;
      this.tracks = trackInfos;
      this.mappedTrackInfo = mappedTrackInfo;
    }
  }

  private abstract class TrackSelectionAdapter extends RecyclerView.Adapter<SubSettingViewHolder> {

    protected List<Integer> rendererIndices;
    protected List<TrackInfo> tracks;
    protected @Nullable MappedTrackInfo mappedTrackInfo;

    public TrackSelectionAdapter() {
      this.rendererIndices = new ArrayList<>();
      this.tracks = new ArrayList<>();
      this.mappedTrackInfo = null;
    }

    public abstract void init(
        List<Integer> rendererIndices, List<TrackInfo> trackInfos, MappedTrackInfo mappedTrackInfo);

    @Override
    public SubSettingViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View v =
          LayoutInflater.from(getContext())
              .inflate(R.layout.exo_styled_sub_settings_list_item, null);
      return new SubSettingViewHolder(v);
    }

    public abstract void onBindViewHolderAtZeroPosition(SubSettingViewHolder holder);

    public abstract void onTrackSelection(String subtext);

    @Override
    public void onBindViewHolder(SubSettingViewHolder holder, int position) {
      if (trackSelector == null || mappedTrackInfo == null) {
        return;
      }
      if (position == 0) {
        onBindViewHolderAtZeroPosition(holder);
      } else {
        TrackInfo track = tracks.get(position - 1);
        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(track.rendererIndex);
        boolean explicitlySelected =
            checkNotNull(trackSelector)
                    .getParameters()
                    .hasSelectionOverride(track.rendererIndex, trackGroups)
                && track.selected;
        holder.textView.setText(track.trackName);
        holder.checkView.setVisibility(explicitlySelected ? VISIBLE : INVISIBLE);
        holder.itemView.setOnClickListener(
            v -> {
              if (mappedTrackInfo != null && trackSelector != null) {
                ParametersBuilder parametersBuilder = trackSelector.getParameters().buildUpon();
                for (int i = 0; i < rendererIndices.size(); i++) {
                  int rendererIndex = rendererIndices.get(i);
                  if (rendererIndex == track.rendererIndex) {
                    parametersBuilder =
                        parametersBuilder
                            .setSelectionOverride(
                                rendererIndex,
                                checkNotNull(mappedTrackInfo).getTrackGroups(rendererIndex),
                                new SelectionOverride(track.groupIndex, track.trackIndex))
                            .setRendererDisabled(rendererIndex, false);
                  } else {
                    parametersBuilder =
                        parametersBuilder
                            .clearSelectionOverrides(rendererIndex)
                            .setRendererDisabled(rendererIndex, true);
                  }
                }
                checkNotNull(trackSelector).setParameters(parametersBuilder);
                onTrackSelection(track.trackName);
                settingsWindow.dismiss();
              }
            });
      }
    }

    @Override
    public int getItemCount() {
      return tracks.isEmpty() ? 0 : tracks.size() + 1;
    }

    public void clear() {
      tracks = Collections.emptyList();
      mappedTrackInfo = null;
    }
  }

  private static class SubSettingViewHolder extends RecyclerView.ViewHolder {

    public final TextView textView;
    public final View checkView;

    public SubSettingViewHolder(View itemView) {
      super(itemView);
      if (Util.SDK_INT < 26) {
        // Workaround for https://github.com/google/ExoPlayer/issues/9061.
        itemView.setFocusable(true);
      }
      textView = itemView.findViewById(R.id.exo_text);
      checkView = itemView.findViewById(R.id.exo_check);
    }
  }
}
