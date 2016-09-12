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

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.R;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Util;
import java.util.Formatter;
import java.util.Locale;

/**
 * A view to control video playback of an {@link ExoPlayer}.
 */
public class PlaybackControlView extends FrameLayout {

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

  public static final int DEFAULT_FAST_FORWARD_MS = 15000;
  public static final int DEFAULT_REWIND_MS = 5000;
  public static final int DEFAULT_SHOW_DURATION_MS = 5000;

  private static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;

  private final ComponentListener componentListener;
  private final View previousButton;
  private final View nextButton;
  private final ImageButton playButton;
  private final TextView time;
  private final TextView timeCurrent;
  private final SeekBar progressBar;
  private final View fastForwardButton;
  private final View rewindButton;
  private final StringBuilder formatBuilder;
  private final Formatter formatter;
  private final Timeline.Window currentWindow;

  private ExoPlayer player;
  private VisibilityListener visibilityListener;

  private boolean dragging;
  private boolean isProgressUpdating;
  private int rewindMs = DEFAULT_REWIND_MS;
  private int fastForwardMs = DEFAULT_FAST_FORWARD_MS;
  private int showDurationMs = DEFAULT_SHOW_DURATION_MS;

  private final Runnable updateProgressAction = new Runnable() {
    @Override
    public void run() {
      long position = updateProgress();
      if (!dragging && isVisible() && isPlaying()) {
        postDelayed(updateProgressAction, 1000 - (position % 1000));
      } else {
        isProgressUpdating = false;
      }
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
    super(context, attrs);

    currentWindow = new Timeline.Window();
    formatBuilder = new StringBuilder();
    formatter = new Formatter(formatBuilder, Locale.getDefault());
    componentListener = new ComponentListener();

    LayoutInflater.from(context).inflate(R.layout.playback_control_view, this);

    time = (TextView) findViewById(R.id.time);
    timeCurrent = (TextView) findViewById(R.id.time_current);
    progressBar = (SeekBar) findViewById(R.id.mediacontroller_progress);
    progressBar.setOnSeekBarChangeListener(componentListener);
    progressBar.setMax(1000);

    playButton = (ImageButton) findViewById(R.id.pause);
    playButton.setOnClickListener(componentListener);
    previousButton = findViewById(R.id.prev);
    previousButton.setOnClickListener(componentListener);
    nextButton = findViewById(R.id.next);
    nextButton.setOnClickListener(componentListener);
    rewindButton = findViewById(R.id.rew);
    rewindButton.setOnClickListener(componentListener);
    fastForwardButton = findViewById(R.id.ffwd);
    fastForwardButton.setOnClickListener(componentListener);
  }

  /**
   * Sets the {@link ExoPlayer} to control.
   *
   * @param player the {@code ExoPlayer} to control.
   */
  public void setPlayer(ExoPlayer player) {
    if (this.player != null) {
      this.player.removeListener(componentListener);
    }
    this.player = player;
    if (player != null) {
      player.addListener(componentListener);
    }
    updatePlayPauseButton();
    updateTime();
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
   * Sets the rewind increment in milliseconds.
   *
   * @param rewindMs The rewind increment in milliseconds.
   */
  public void setRewindIncrementMs(int rewindMs) {
    this.rewindMs = rewindMs;
  }

  /**
   * Sets the fast forward increment in milliseconds.
   *
   * @param fastForwardMs The fast forward increment in milliseconds.
   */
  public void setFastForwardIncrementMs(int fastForwardMs) {
    this.fastForwardMs = fastForwardMs;
  }

  /**
   * Sets the duration to show the playback control in milliseconds.
   *
   * @param showDurationMs The duration in milliseconds.
   */
  public void setShowDurationMs(int showDurationMs) {
    this.showDurationMs = showDurationMs;
  }

  /**
   * Shows the controller for the duration last passed to {@link #setShowDurationMs(int)}, or for
   * {@link #DEFAULT_SHOW_DURATION_MS} if {@link #setShowDurationMs(int)} has not been called.
   */
  public void show() {
    show(showDurationMs);
  }

  /**
   * Shows the controller for the {@code durationMs}. If {@code durationMs} is 0 the controller is
   * shown until {@link #hide()} is called.
   *
   * @param durationMs The duration in milliseconds.
   */
  public void show(int durationMs) {
    setVisibility(VISIBLE);
    if (visibilityListener != null) {
      visibilityListener.onVisibilityChange(getVisibility());
    }
    isProgressUpdating = true;
    post(updateProgressAction);
    removeCallbacks(hideAction);
    showDurationMs = durationMs;
    if (durationMs > 0) {
      postDelayed(hideAction, durationMs);
    }
  }

  /**
   * Hides the controller.
   */
  public void hide() {
    setVisibility(GONE);
    if (visibilityListener != null) {
      visibilityListener.onVisibilityChange(getVisibility());
    }
    removeCallbacks(updateProgressAction);
    removeCallbacks(hideAction);
  }

  /**
   * Returns whether the controller is currently visible.
   */
  public boolean isVisible() {
    return getVisibility() == VISIBLE;
  }

  private void hideDeferred() {
    removeCallbacks(hideAction);
    if (showDurationMs != 0) {
      postDelayed(hideAction, showDurationMs);
    }
  }

  private void updatePlayPauseButton() {
    playButton.setImageResource(player != null && player.getPlayWhenReady()
        ? R.drawable.ic_media_pause : R.drawable.ic_media_play);
  }

  private void updateNavigationButtons() {
    if (player.getCurrentTimeline() == null || player.getCurrentTimeline().getWindowCount() < 2) {
      previousButton.setVisibility(GONE);
      nextButton.setVisibility(GONE);
    } else if (player.getCurrentWindowIndex() == 0) {
      setButtonEnabled(previousButton, false);
      setButtonEnabled(nextButton, true);
    } else if (player.getCurrentWindowIndex() == player.getCurrentTimeline().getWindowCount() - 1) {
      setButtonEnabled(previousButton, true);
      setButtonEnabled(nextButton, false);
    } else {
      setButtonEnabled(previousButton, true);
      setButtonEnabled(nextButton, true);
    }
  }

  private void setButtonEnabled(View button, boolean enabled) {
    button.setEnabled(enabled);
    if (Util.SDK_INT >= 11) {
      button.setAlpha(enabled ? 1f : 0.3f);
      button.setVisibility(VISIBLE);
    } else {
      button.setVisibility(enabled ? VISIBLE : INVISIBLE);
    }
  }

  private void updateUiForLiveStream() {
    int visibility = player.getCurrentTimeline() != null && player.getCurrentTimeline()
        .getWindow(player.getCurrentWindowIndex(), currentWindow).isDynamic ? GONE
        : VISIBLE;
    progressBar.setVisibility(visibility);
    timeCurrent.setVisibility(visibility);
    time.setVisibility(visibility);
    fastForwardButton.setVisibility(visibility);
    rewindButton.setVisibility(visibility);
  }

  private long updateProgress() {
    if (player == null || dragging) {
      return 0;
    }
    long position = player.getCurrentPosition();
    long duration = player.getDuration();
    if (progressBar != null) {
      if (duration > 0) {
        progressBar.setProgress((int) (1000 * position / duration));
      }
      progressBar.setSecondaryProgress(player.getBufferedPercentage() * 10);
    }
    updateTime();
    return position;
  }

  private void updateTime() {
    time.setText(stringForTime(player == null ? 0 : player.getDuration()));
    timeCurrent.setText(stringForTime(player == null ? 0 : player.getCurrentPosition()));
  }

  private String stringForTime(long timeMs) {
    long totalSeconds = timeMs / 1000;
    long seconds = totalSeconds % 60;
    long minutes = (totalSeconds / 60) % 60;
    long hours = totalSeconds / 3600;
    formatBuilder.setLength(0);
    return hours > 0 ? formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        : formatter.format("%02d:%02d", minutes, seconds).toString();
  }

  private boolean isPlaying() {
    return player != null && player.getPlayWhenReady() && (player.getPlaybackState()
        == ExoPlayer.STATE_READY || player.getPlaybackState() == ExoPlayer.STATE_BUFFERING);
  }

  private void previous() {
    int currentWindowIndex = player.getCurrentWindowIndex();
    if (currentWindowIndex > 0 && player.getCurrentPosition()
        <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS) {
      player.seekToDefaultPosition(currentWindowIndex - 1);
    } else {
      player.seekTo(0);
    }
  }

  private void next() {
    int currentWindowIndex = player.getCurrentWindowIndex();
    Timeline currentTimeline = player.getCurrentTimeline();
    if (currentTimeline != null && currentWindowIndex < currentTimeline.getWindowCount() - 1) {
      player.seekToDefaultPosition(currentWindowIndex + 1);
    }
  }

  private void rewind() {
    player.seekTo(Math.max(player.getCurrentPosition() - rewindMs, 0));
  }

  private void fastForward() {
    player.seekTo(Math.min(player.getCurrentPosition() + fastForwardMs, player.getDuration()));
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (player == null || event.getAction() != KeyEvent.ACTION_DOWN) {
      return super.dispatchKeyEvent(event);
    }
    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        fastForward();
        break;
      case KeyEvent.KEYCODE_MEDIA_REWIND:
      case KeyEvent.KEYCODE_DPAD_LEFT:
        rewind();
        break;
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
        player.setPlayWhenReady(!player.getPlayWhenReady());
        break;
      case KeyEvent.KEYCODE_MEDIA_PLAY:
        player.setPlayWhenReady(true);
        break;
      case KeyEvent.KEYCODE_MEDIA_PAUSE:
        player.setPlayWhenReady(false);
        break;
      case KeyEvent.KEYCODE_MEDIA_NEXT:
        next();
        break;
      case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
        previous();
        break;
      default:
        return false;
    }
    show();
    return true;
  }

  private final class ComponentListener implements ExoPlayer.EventListener,
      SeekBar.OnSeekBarChangeListener, OnClickListener {

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
      removeCallbacks(hideAction);
      dragging = true;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      timeCurrent.setText(stringForTime(player == null ? 0 : player.getDuration() * progress
          / 1000));
      progressBar.setSecondaryProgress(player == null ? 0 : player.getBufferedPercentage() * 10);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
      dragging = false;
      player.seekTo(player.getDuration() * seekBar.getProgress() / 1000);
      hideDeferred();
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      if (isPlaying() && !isProgressUpdating) {
        isProgressUpdating = true;
        post(updateProgressAction);
      }
      updatePlayPauseButton();
    }

    @Override
    public void onPositionDiscontinuity() {
      updateNavigationButtons();
      updateProgress();
      updateUiForLiveStream();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
      // Do nothing.
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
      // Do nothing.
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      // Do nothing.
    }

    @Override
    public void onClick(View view) {
      Timeline currentTimeline = player.getCurrentTimeline();
      if (nextButton == view) {
        next();
      } else if (previousButton == view) {
        previous();
      } else if (fastForwardButton == view) {
        fastForward();
      } else if (rewindButton == view && currentTimeline != null) {
        rewind();
      } else if (playButton == view) {
        player.setPlayWhenReady(!player.getPlayWhenReady());
      }
      hideDeferred();
    }

  }

}
