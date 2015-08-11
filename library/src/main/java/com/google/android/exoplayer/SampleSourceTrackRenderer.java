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

import com.google.android.exoplayer.SampleSource.SampleSourceReader;

import java.io.IOException;
import java.util.Arrays;

/**
 * Base class for {@link TrackRenderer} implementations that render samples obtained from a
 * {@link SampleSource}.
 */
public abstract class SampleSourceTrackRenderer extends TrackRenderer {

  private final SampleSourceReader source;

  private int enabledSourceTrackIndex;
  private int[] handledSourceTrackIndices;

  /**
   * @param source The upstream source from which the renderer obtains samples.
   */
  public SampleSourceTrackRenderer(SampleSource source) {
    this.source = source.register();
  }

  @Override
  protected boolean doPrepare(long positionUs) throws ExoPlaybackException {
    boolean sourcePrepared = source.prepare(positionUs);
    if (!sourcePrepared) {
      return false;
    }
    int handledTrackCount = 0;
    int sourceTrackCount = source.getTrackCount();
    int[] trackIndices = new int[sourceTrackCount];
    for (int trackIndex = 0; trackIndex < sourceTrackCount; trackIndex++) {
      MediaFormat format = source.getFormat(trackIndex);
      if (handlesTrack(format)) {
        trackIndices[handledTrackCount] = trackIndex;
        handledTrackCount++;
      }
    }
    this.handledSourceTrackIndices = Arrays.copyOf(trackIndices, handledTrackCount);
    return true;
  }

  /**
   * Returns whether this renderer is capable of handling the provided track.
   *
   * @param mediaFormat The track.
   * @return True if the renderer can handle the track, false otherwise.
   */
  protected abstract boolean handlesTrack(MediaFormat mediaFormat);

  @Override
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    this.enabledSourceTrackIndex = handledSourceTrackIndices[track];
    source.enable(enabledSourceTrackIndex, positionUs);
  }

  @Override
  protected void seekTo(long positionUs) throws ExoPlaybackException {
    source.seekToUs(positionUs);
  }

  @Override
  protected long getBufferedPositionUs() {
    return source.getBufferedPositionUs();
  }

  @Override
  protected long getDurationUs() {
    return source.getFormat(enabledSourceTrackIndex).durationUs;
  }

  @Override
  protected void maybeThrowError() throws ExoPlaybackException {
    try {
      source.maybeThrowError();
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }
  }

  @Override
  protected void onDisabled() throws ExoPlaybackException {
    source.disable(enabledSourceTrackIndex);
  }

  @Override
  protected void onReleased() throws ExoPlaybackException {
    source.release();
  }

  protected final boolean continueBufferingSource(long positionUs) {
    return source.continueBuffering(enabledSourceTrackIndex, positionUs);
  }

  protected final int readSource(long positionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder, boolean onlyReadDiscontinuity) {
    return source.readData(enabledSourceTrackIndex, positionUs, formatHolder, sampleHolder,
        onlyReadDiscontinuity);
  }

  @Override
  protected final int getTrackCount() {
    return handledSourceTrackIndices.length;
  }

  @Override
  protected final MediaFormat getFormat(int track) {
    return source.getFormat(handledSourceTrackIndices[track]);
  }

}
