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

import java.io.IOException;

/**
 * Combines multiple {@link SampleSource} instances.
 */
public class MultiSampleSource implements SampleSource {

  private final SampleSource[] sources;

  private boolean prepared;
  private long durationUs;
  private SampleSource[] trackSources;
  private int[] trackIndices;

  public MultiSampleSource(SampleSource... sources) {
    this.sources = sources;
  }

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (this.prepared) {
      return true;
    }
    boolean prepared = true;
    for (int i = 0; i < sources.length; i++) {
      prepared &= sources[i].prepare(positionUs);
    }
    if (prepared) {
      this.prepared = true;
      this.durationUs = C.UNKNOWN_TIME_US;
      int trackCount = 0;
      for (int i = 0; i < sources.length; i++) {
        trackCount += sources[i].getTrackCount();
        if (sources[i].getDurationUs() > durationUs) {
          durationUs = sources[i].getDurationUs();
        }
      }
      trackSources = new SampleSource[trackCount];
      trackIndices = new int[trackCount];
      int index = 0;
      for (int i = 0; i < sources.length; i++) {
        int thisSourceTrackCount = sources[i].getTrackCount();
        for (int j = 0; j < thisSourceTrackCount; j++) {
          trackSources[index] = sources[i];
          trackIndices[index++] = j;
        }
      }
    }
    return prepared;
  }

  @Override
  public boolean isPrepared() {
    return prepared;
  }

  @Override
  public int getTrackCount() {
    return trackSources.length;
  }

  @Override
  public MediaFormat getFormat(int track) {
    return trackSources[track].getFormat(trackIndices[track]);
  }

  @Override
  public TrackStream enable(int track, long positionUs) {
    return trackSources[track].enable(trackIndices[track], positionUs);
  }

  @Override
  public void continueBuffering(long positionUs) {
    for (int i = 0; i < sources.length; i++) {
      sources[i].continueBuffering(positionUs);
    }
  }

  @Override
  public void seekToUs(long positionUs) {
    for (int i = 0; i < sources.length; i++) {
      sources[i].seekToUs(positionUs);
    }
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = durationUs != C.UNKNOWN_TIME_US ? durationUs : Long.MAX_VALUE;
    for (int i = 0; i < sources.length; i++) {
      long rendererBufferedPositionUs = sources[i].getBufferedPositionUs();
      if (rendererBufferedPositionUs == C.UNKNOWN_TIME_US) {
        return C.UNKNOWN_TIME_US;
      } else if (rendererBufferedPositionUs == C.END_OF_SOURCE_US) {
        // This source is fully buffered.
      } else {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.UNKNOWN_TIME_US : bufferedPositionUs;
  }

  @Override
  public void release() {
    for (int i = 0; i < sources.length; i++) {
      sources[i].release();
    }
    prepared = false;
  }

}
