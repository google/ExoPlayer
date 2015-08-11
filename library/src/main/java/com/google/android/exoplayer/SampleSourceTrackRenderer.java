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

/**
 * Base class for {@link TrackRenderer} implementations that render samples obtained from a
 * {@link SampleSource}.
 */
public abstract class SampleSourceTrackRenderer extends TrackRenderer {

  private final SampleSourceReader source;

  private int trackIndex;

  /**
   * @param source The upstream source from which the renderer obtains samples.
   */
  public SampleSourceTrackRenderer(SampleSource source) {
    this.source = source.register();
  }

  @Override
  protected int doPrepare(long positionUs) throws ExoPlaybackException {
    boolean sourcePrepared = source.prepare(positionUs);
    if (!sourcePrepared) {
      return TrackRenderer.STATE_UNPREPARED;
    }
    int trackCount = source.getTrackCount();
    for (int i = 0; i < trackCount; i++) {
      TrackInfo trackInfo = source.getTrackInfo(i);
      if (handlesTrack(trackInfo)) {
        trackIndex = i;
        onTrackSelected(trackInfo);
        return TrackRenderer.STATE_PREPARED;
      }
    }
    return TrackRenderer.STATE_IGNORE;
  }

  /**
   * Returns whether this renderer is capable of handling the provided track.
   *
   * @param trackInfo The track.
   * @return True if the renderer can handle the track, false otherwise.
   */
  protected abstract boolean handlesTrack(TrackInfo trackInfo);

  /**
   * Invoked when a track is selected.
   *
   * @param trackInfo The selected track.
   */
  protected void onTrackSelected(TrackInfo trackInfo) {
    // Do nothing.
  }

  @Override
  protected void onEnabled(long positionUs, boolean joining) throws ExoPlaybackException {
    source.enable(trackIndex, positionUs);
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
    return source.getTrackInfo(trackIndex).durationUs;
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
    source.disable(trackIndex);
  }

  @Override
  protected void onReleased() throws ExoPlaybackException {
    source.release();
  }

  protected final boolean continueBufferingSource(long positionUs) {
    return source.continueBuffering(trackIndex, positionUs);
  }

  protected final int readSource(long positionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder, boolean onlyReadDiscontinuity) {
    return source.readData(trackIndex, positionUs, formatHolder, sampleHolder,
        onlyReadDiscontinuity);
  }

}
