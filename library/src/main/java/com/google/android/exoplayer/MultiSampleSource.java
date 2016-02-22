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

import android.util.Pair;

import java.io.IOException;

/**
 * Combines multiple {@link SampleSource} instances.
 */
public class MultiSampleSource implements SampleSource {

  private final SampleSource[] sources;

  private boolean prepared;
  private long durationUs;
  private TrackGroup[] tracks;

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
      int totalTrackGroupCount = 0;
      for (int i = 0; i < sources.length; i++) {
        totalTrackGroupCount += sources[i].getTrackGroups().length;
        if (sources[i].getDurationUs() > durationUs) {
          durationUs = sources[i].getDurationUs();
        }
      }
      tracks = new TrackGroup[totalTrackGroupCount];
      int trackGroupIndex = 0;
      for (int i = 0; i < sources.length; i++) {
        int sourceTrackGroupCount = sources[i].getTrackGroups().length;
        for (int j = 0; j < sourceTrackGroupCount; j++) {
          tracks[trackGroupIndex++] = sources[i].getTrackGroups()[j];
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
  public TrackGroup[] getTrackGroups() {
    return tracks;
  }

  @Override
  public TrackStream enable(TrackSelection selection, long positionUs) {
    Pair<Integer, Integer> sourceAndGroup = getSourceAndTrackGroupIndices(selection.group);
    return sources[sourceAndGroup.first].enable(
        new TrackSelection(sourceAndGroup.second, selection.tracks), positionUs);
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

  private Pair<Integer, Integer> getSourceAndTrackGroupIndices(int group) {
    int totalTrackGroupCount = 0;
    for (int i = 0; i < sources.length; i++) {
      int sourceTrackGroupCount = sources[i].getTrackGroups().length;
      if (group < totalTrackGroupCount + sourceTrackGroupCount) {
        return Pair.create(i, group - totalTrackGroupCount);
      }
      totalTrackGroupCount += sourceTrackGroupCount;
    }
    throw new IndexOutOfBoundsException();
  }

}
