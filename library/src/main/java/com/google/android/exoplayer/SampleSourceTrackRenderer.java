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

import com.google.android.exoplayer.SampleSource.TrackStream;
import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;

/**
 * Base class for {@link TrackRenderer} implementations that render samples obtained from a
 * {@link SampleSource}.
 */
public abstract class SampleSourceTrackRenderer extends TrackRenderer {

  private TrackStream trackStream;

  @Override
  protected void onEnabled(TrackStream trackStream, long positionUs, boolean joining)
      throws ExoPlaybackException {
    this.trackStream = Assertions.checkNotNull(trackStream);
    onReset(positionUs);
  }

  @Override
  protected final void doSomeWork(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {
    // TODO[REFACTOR]: Consider splitting reading of resets into a separate method?
    long resetPositionUs = trackStream.readReset();
    if (resetPositionUs != TrackStream.NO_RESET) {
      onReset(resetPositionUs);
      return;
    }
    doSomeWork(positionUs, elapsedRealtimeUs, trackStream.isReady());
  }

  @Override
  protected void maybeThrowError() throws IOException {
    trackStream.maybeThrowError();
  }

  @Override
  protected void onDisabled() throws ExoPlaybackException {
    trackStream.disable();
    trackStream = null;
  }

  // Methods to be called by subclasses.

  /**
   * Reads from the enabled upstream source.
   *
   * @param formatHolder A {@link MediaFormatHolder} object to populate in the case of a new format.
   * @param sampleHolder A {@link SampleHolder} object to populate in the case of a new sample.
   *     If the caller requires the sample data then it must ensure that {@link SampleHolder#data}
   *     references a valid output buffer.
   * @return The result, which can be {@link TrackStream#SAMPLE_READ},
   *     {@link TrackStream#FORMAT_READ}, {@link TrackStream#NOTHING_READ} or
   *     {@link TrackStream#END_OF_STREAM}.
   */
  protected final int readSource(MediaFormatHolder formatHolder, SampleHolder sampleHolder) {
    return trackStream.readData(formatHolder, sampleHolder);
  }

  // Abstract methods.

  /**
   * Invoked when a reset is encountered. Also invoked when the renderer is enabled.
   *
   * @param positionUs The playback position in microseconds.
   * @throws ExoPlaybackException If an error occurs handling the reset.
   */
  protected abstract void onReset(long positionUs) throws ExoPlaybackException;

  /**
   * Called by {@link #doSomeWork(long, long)}.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @param sourceIsReady The result of the most recent call to  {@link TrackStream#isReady()}.
   * @throws ExoPlaybackException If an error occurs.
   * @throws ExoPlaybackException
   */
  protected abstract void doSomeWork(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady)
      throws ExoPlaybackException;

}
