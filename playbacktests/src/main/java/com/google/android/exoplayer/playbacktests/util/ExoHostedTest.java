/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.playbacktests.util;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.playbacktests.util.HostActivity.HostedTest;

import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

/**
 * A {@link HostedTest} for {@link ExoPlayer} playback tests.
 */
public abstract class ExoHostedTest implements HostedTest, ExoPlayer.Listener {

  static {
    // ExoPlayer's AudioTrack class is able to work around spurious timestamps reported by the
    // platform (by ignoring them). Disable this workaround, since we're interested in testing
    // that the underlying platform is behaving correctly.
    AudioTrack.failOnSpuriousAudioTimestamp = true;
  }

  private final int rendererCount;
  private final boolean failOnPlayerError;

  private ActionSchedule pendingSchedule;
  private Handler actionHandler;
  private ExoPlayer player;
  private ExoPlaybackException playerError;
  private boolean playerWasPrepared;
  private boolean playerFinished;
  private boolean playing;
  private long totalPlayingTimeMs;
  private long lastPlayingStartTimeMs;

  /**
   * Constructs a test that fails if a player error occurs.
   *
   * @param rendererCount The number of renderers that will be injected into the player.
   */
  public ExoHostedTest(int rendererCount) {
    this(rendererCount, true);
  }

  /**
   * @param rendererCount The number of renderers that will be injected into the player.
   * @param failOnPlayerError True if a player error should be considered a test failure. False
   *     otherwise.
   */
  public ExoHostedTest(int rendererCount, boolean failOnPlayerError) {
    this.rendererCount = rendererCount;
    this.failOnPlayerError = failOnPlayerError;
  }

  /**
   * Sets a schedule to be applied during the test.
   *
   * @param schedule The schedule.
   */
  public final void setSchedule(ActionSchedule schedule) {
    if (player == null) {
      pendingSchedule = schedule;
    } else {
      schedule.start(player, actionHandler);
    }
  }

  // HostedTest implementation

  @Override
  public final void initialize(HostActivity host, Surface surface) {
    // Build the player.
    player = ExoPlayer.Factory.newInstance(rendererCount);
    player.addListener(this);
    player.prepare(buildRenderers(host, player, surface));
    player.setPlayWhenReady(true);
    actionHandler = new Handler();
    // Schedule any pending actions.
    if (pendingSchedule != null) {
      pendingSchedule.start(player, actionHandler);
      pendingSchedule = null;
    }
  }

  @Override
  public final void release() {
    actionHandler.removeCallbacksAndMessages(null);
    player.release();
    player = null;
  }

  @Override
  public final boolean isFinished() {
    return playerFinished;
  }

  @Override
  public final void assertPassed() {
    if (failOnPlayerError && playerError != null) {
      throw new Error(playerError);
    }
    assertPassedInternal();
  }

  // ExoPlayer.Listener

  @Override
  public final void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    playerWasPrepared |= playbackState != ExoPlayer.STATE_IDLE;
    if (playbackState == ExoPlayer.STATE_ENDED
        || (playbackState == ExoPlayer.STATE_IDLE && playerWasPrepared)) {
      playerFinished = true;
    }
    boolean playing = playWhenReady && playbackState == ExoPlayer.STATE_READY;
    if (!this.playing && playing) {
      lastPlayingStartTimeMs = SystemClock.elapsedRealtime();
    } else if (this.playing && !playing) {
      totalPlayingTimeMs += SystemClock.elapsedRealtime() - lastPlayingStartTimeMs;
    }
    this.playing = playing;
  }

  @Override
  public final void onPlayerError(ExoPlaybackException error) {
    playerWasPrepared = true;
    playerError = error;
    onPlayerErrorInternal(error);
  }

  @Override
  public final void onPlayWhenReadyCommitted() {
    // Do nothing.
  }

  // Internal logic

  @SuppressWarnings("unused")
  protected abstract TrackRenderer[] buildRenderers(HostActivity host, ExoPlayer player,
      Surface surface) throws IllegalStateException;

  @SuppressWarnings("unused")
  protected void onPlayerErrorInternal(ExoPlaybackException error) {
    // Do nothing. Interested subclasses may override.
  }

  protected void assertPassedInternal() {
    // Do nothing. Subclasses may override to add additional assertions.
  }

  // Utility methods and actions for subclasses.

  protected final long getTotalPlayingTimeMs() {
    return totalPlayingTimeMs;
  }

  protected final ExoPlaybackException getError() {
    return playerError;
  }

}
