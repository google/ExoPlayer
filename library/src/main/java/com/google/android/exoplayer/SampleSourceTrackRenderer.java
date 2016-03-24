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
package com.google.android.exoplayer;

import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;

/**
 * Base class for {@link TrackRenderer} implementations that render samples obtained from a
 * {@link SampleSource}.
 */
public abstract class SampleSourceTrackRenderer extends TrackRenderer {

  private TrackStream trackStream;

  @Override
  protected void onEnabled(Format[] formats, TrackStream trackStream, long positionUs,
      boolean joining) throws ExoPlaybackException {
    this.trackStream = Assertions.checkNotNull(trackStream);
    reset(positionUs);
  }

  @Override
  protected final void checkForReset() throws ExoPlaybackException {
    long resetPositionUs = trackStream.readReset();
    if (resetPositionUs != TrackStream.NO_RESET) {
      reset(resetPositionUs);
      return;
    }
  }

  @Override
  protected final void render(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {
    render(positionUs, elapsedRealtimeUs, trackStream.isReady());
  }

  @Override
  protected final void maybeThrowError() throws IOException {
    trackStream.maybeThrowError();
  }

  @Override
  protected void onDisabled() {
    trackStream = null;
  }

  // Methods to be called by subclasses.

  /**
   * Reads from the enabled upstream source.
   *
   * @see TrackStream#readData(FormatHolder, DecoderInputBuffer)
   */
  protected final int readSource(FormatHolder formatHolder, DecoderInputBuffer buffer) {
    return trackStream.readData(formatHolder, buffer);
  }

  // Abstract methods.

  /**
   * Invoked when a reset is encountered, and also when the renderer is enabled.
   *
   * @param positionUs The playback position in microseconds.
   * @throws ExoPlaybackException If an error occurs handling the reset.
   */
  protected abstract void reset(long positionUs) throws ExoPlaybackException;

  /**
   * Called by {@link #render(long, long)}.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @param sourceIsReady The result of the most recent call to  {@link TrackStream#isReady()}.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract void render(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady)
      throws ExoPlaybackException;

}
