/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.StandaloneMediaClock;

/**
 * Default {@link MediaClock} which uses a renderer media clock and falls back to a
 * {@link StandaloneMediaClock} if necessary.
 */
/* package */ final class DefaultMediaClock implements MediaClock {

  /** Listener interface to be notified of changes to the active playback speed. */
  public interface PlaybackSpeedListener {

    /**
     * Called when the active playback speed changed. Will not be called for {@link
     * #setPlaybackSpeed(float)}.
     *
     * @param newPlaybackSpeed The newly active playback speed.
     */
    void onPlaybackSpeedChanged(float newPlaybackSpeed);
  }

  private final StandaloneMediaClock standaloneClock;
  private final PlaybackSpeedListener listener;

  @Nullable private Renderer rendererClockSource;
  @Nullable private MediaClock rendererClock;
  private boolean isUsingStandaloneClock;
  private boolean standaloneClockIsStarted;

  /**
   * Creates a new instance with listener for playback speed changes and a {@link Clock} to use for
   * the standalone clock implementation.
   *
   * @param listener A {@link PlaybackSpeedListener} to listen for playback speed changes.
   * @param clock A {@link Clock}.
   */
  public DefaultMediaClock(PlaybackSpeedListener listener, Clock clock) {
    this.listener = listener;
    this.standaloneClock = new StandaloneMediaClock(clock);
    isUsingStandaloneClock = true;
  }

  /**
   * Starts the standalone fallback clock.
   */
  public void start() {
    standaloneClockIsStarted = true;
    standaloneClock.start();
  }

  /**
   * Stops the standalone fallback clock.
   */
  public void stop() {
    standaloneClockIsStarted = false;
    standaloneClock.stop();
  }

  /**
   * Resets the position of the standalone fallback clock.
   *
   * @param positionUs The position to set in microseconds.
   */
  public void resetPosition(long positionUs) {
    standaloneClock.resetPosition(positionUs);
  }

  /**
   * Notifies the media clock that a renderer has been enabled. Starts using the media clock of the
   * provided renderer if available.
   *
   * @param renderer The renderer which has been enabled.
   * @throws ExoPlaybackException If the renderer provides a media clock and another renderer media
   *     clock is already provided.
   */
  public void onRendererEnabled(Renderer renderer) throws ExoPlaybackException {
    @Nullable MediaClock rendererMediaClock = renderer.getMediaClock();
    if (rendererMediaClock != null && rendererMediaClock != rendererClock) {
      if (rendererClock != null) {
        throw ExoPlaybackException.createForUnexpected(
            new IllegalStateException("Multiple renderer media clocks enabled."));
      }
      this.rendererClock = rendererMediaClock;
      this.rendererClockSource = renderer;
      rendererClock.setPlaybackSpeed(standaloneClock.getPlaybackSpeed());
    }
  }

  /**
   * Notifies the media clock that a renderer has been disabled. Stops using the media clock of this
   * renderer if used.
   *
   * @param renderer The renderer which has been disabled.
   */
  public void onRendererDisabled(Renderer renderer) {
    if (renderer == rendererClockSource) {
      this.rendererClock = null;
      this.rendererClockSource = null;
      isUsingStandaloneClock = true;
    }
  }

  /**
   * Syncs internal clock if needed and returns current clock position in microseconds.
   *
   * @param isReadingAhead Whether the renderers are reading ahead.
   */
  public long syncAndGetPositionUs(boolean isReadingAhead) {
    syncClocks(isReadingAhead);
    return getPositionUs();
  }

  // MediaClock implementation.

  @Override
  public long getPositionUs() {
    return isUsingStandaloneClock ? standaloneClock.getPositionUs() : rendererClock.getPositionUs();
  }

  @Override
  public void setPlaybackSpeed(float playbackSpeed) {
    if (rendererClock != null) {
      rendererClock.setPlaybackSpeed(playbackSpeed);
      playbackSpeed = rendererClock.getPlaybackSpeed();
    }
    standaloneClock.setPlaybackSpeed(playbackSpeed);
  }

  @Override
  public float getPlaybackSpeed() {
    return rendererClock != null
        ? rendererClock.getPlaybackSpeed()
        : standaloneClock.getPlaybackSpeed();
  }

  private void syncClocks(boolean isReadingAhead) {
    if (shouldUseStandaloneClock(isReadingAhead)) {
      isUsingStandaloneClock = true;
      if (standaloneClockIsStarted) {
        standaloneClock.start();
      }
      return;
    }
    long rendererClockPositionUs = rendererClock.getPositionUs();
    if (isUsingStandaloneClock) {
      // Ensure enabling the renderer clock doesn't jump backwards in time.
      if (rendererClockPositionUs < standaloneClock.getPositionUs()) {
        standaloneClock.stop();
        return;
      }
      isUsingStandaloneClock = false;
      if (standaloneClockIsStarted) {
        standaloneClock.start();
      }
    }
    // Continuously sync stand-alone clock to renderer clock so that it can take over if needed.
    standaloneClock.resetPosition(rendererClockPositionUs);
    float playbackSpeed = rendererClock.getPlaybackSpeed();
    if (playbackSpeed != standaloneClock.getPlaybackSpeed()) {
      standaloneClock.setPlaybackSpeed(playbackSpeed);
      listener.onPlaybackSpeedChanged(playbackSpeed);
    }
  }

  private boolean shouldUseStandaloneClock(boolean isReadingAhead) {
    // Use the standalone clock if the clock providing renderer is not set or has ended. Also use
    // the standalone clock if the renderer is not ready and we have finished reading the stream or
    // are reading ahead to avoid getting stuck if tracks in the current period have uneven
    // durations. See: https://github.com/google/ExoPlayer/issues/1874.
    return rendererClockSource == null
        || rendererClockSource.isEnded()
        || (!rendererClockSource.isReady()
            && (isReadingAhead || rendererClockSource.hasReadStreamToEnd()));
  }
}
