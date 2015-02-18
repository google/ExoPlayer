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
package com.google.android.exoplayer.source;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;

/** {@link SampleSource} that extracts sample data using a {@link SampleExtractor} */
public final class DefaultSampleSource implements SampleSource {

  private static final int TRACK_STATE_DISABLED = 0;
  private static final int TRACK_STATE_ENABLED = 1;
  private static final int TRACK_STATE_FORMAT_SENT = 2;

  private final SampleExtractor sampleExtractor;

  private TrackInfo[] trackInfos;
  private boolean prepared;
  private int remainingReleaseCount;
  private int[] trackStates;
  private boolean[] pendingDiscontinuities;

  private long seekPositionUs;

  /**
   * Creates a new sample source that extracts samples using {@code sampleExtractor}. Specify the
   * {@code downstreamRendererCount} to ensure that the sample source is released only when all
   * downstream renderers have been released.
   *
   * @param sampleExtractor Sample extractor for accessing media samples.
   * @param downstreamRendererCount Number of track renderers dependent on this sample source.
   */
  public DefaultSampleSource(SampleExtractor sampleExtractor, int downstreamRendererCount) {
    this.sampleExtractor = Assertions.checkNotNull(sampleExtractor);
    this.remainingReleaseCount = downstreamRendererCount;
  }

  @Override
  public boolean prepare() throws IOException {
    if (prepared) {
      return true;
    }

    if (sampleExtractor.prepare()) {
      prepared = true;
      trackInfos = sampleExtractor.getTrackInfos();
      trackStates = new int[trackInfos.length];
      pendingDiscontinuities = new boolean[trackInfos.length];
    }

    return prepared;
  }

  @Override
  public int getTrackCount() {
    Assertions.checkState(prepared);
    return trackInfos.length;
  }

  @Override
  public TrackInfo getTrackInfo(int track) {
    Assertions.checkState(prepared);
    return trackInfos[track];
  }

  @Override
  public void enable(int track, long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackStates[track] == TRACK_STATE_DISABLED);
    trackStates[track] = TRACK_STATE_ENABLED;
    sampleExtractor.selectTrack(track);
    seekToUsInternal(positionUs, positionUs != 0);
  }

  @Override
  public void disable(int track) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackStates[track] != TRACK_STATE_DISABLED);
    sampleExtractor.deselectTrack(track);
    pendingDiscontinuities[track] = false;
    trackStates[track] = TRACK_STATE_DISABLED;
  }

  @Override
  public boolean continueBuffering(long positionUs) throws IOException {
    // Do nothing.
    return true;
  }

  @Override
  public int readData(int track, long positionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder, boolean onlyReadDiscontinuity) throws IOException {
    Assertions.checkState(prepared);
    Assertions.checkState(trackStates[track] != TRACK_STATE_DISABLED);
    if (pendingDiscontinuities[track]) {
      pendingDiscontinuities[track] = false;
      return DISCONTINUITY_READ;
    }
    if (onlyReadDiscontinuity) {
      return NOTHING_READ;
    }
    if (trackStates[track] != TRACK_STATE_FORMAT_SENT) {
      sampleExtractor.getTrackMediaFormat(track, formatHolder);
      trackStates[track] = TRACK_STATE_FORMAT_SENT;
      return FORMAT_READ;
    }

    seekPositionUs = C.UNKNOWN_TIME_US;
    return sampleExtractor.readSample(track, sampleHolder);
  }

  @Override
  public void seekToUs(long positionUs) {
    Assertions.checkState(prepared);
    seekToUsInternal(positionUs, false);
  }

  @Override
  public long getBufferedPositionUs() {
    Assertions.checkState(prepared);
    return sampleExtractor.getBufferedPositionUs();
  }

  @Override
  public void release() {
    Assertions.checkState(remainingReleaseCount > 0);
    if (--remainingReleaseCount == 0) {
      sampleExtractor.release();
    }
  }

  private void seekToUsInternal(long positionUs, boolean force) {
    // Unless forced, avoid duplicate calls to the underlying extractor's seek method in the case
    // that there have been no interleaving calls to readSample.
    if (force || seekPositionUs != positionUs) {
      seekPositionUs = positionUs;
      sampleExtractor.seekTo(positionUs);
      for (int i = 0; i < trackStates.length; ++i) {
        if (trackStates[i] != TRACK_STATE_DISABLED) {
          pendingDiscontinuities[i] = true;
        }
      }
    }
  }

}
