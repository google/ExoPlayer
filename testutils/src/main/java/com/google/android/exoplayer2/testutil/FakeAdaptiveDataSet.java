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
package com.google.android.exoplayer2.testutil;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.trackselection.TrackSelection;

/**
 * Fake data set emulating the data of an adaptive media source.
 * It provides chunk data for all {@link Format}s in the given {@link TrackSelection}.
 */
public final class FakeAdaptiveDataSet extends FakeDataSet {

  /**
   * Factory for {@link FakeAdaptiveDataSet}s.
   */
  public static final class Factory {

    private final long chunkDurationUs;

    public Factory(long chunkDurationUs) {
      this.chunkDurationUs = chunkDurationUs;
    }

    public FakeAdaptiveDataSet createDataSet(TrackSelection trackSelection, long mediaDurationUs) {
      return new FakeAdaptiveDataSet(trackSelection, mediaDurationUs, chunkDurationUs);
    }

  }

  private final long chunkCount;
  private final long chunkDurationUs;
  private final long lastChunkDurationUs;

  public FakeAdaptiveDataSet(TrackSelection trackSelection, long mediaDurationUs,
      long chunkDurationUs) {
    this.chunkDurationUs = chunkDurationUs;
    int selectionCount = trackSelection.length();
    long lastChunkDurationUs = mediaDurationUs % chunkDurationUs;
    int fullChunks = (int) (mediaDurationUs / chunkDurationUs);
    for (int i = 0; i < selectionCount; i++) {
      String uri = getUri(i);
      Format format = trackSelection.getFormat(i);
      int chunkLength = (int) (format.bitrate * chunkDurationUs / (8 * C.MICROS_PER_SECOND));
      FakeData newData = this.newData(uri);
      for (int j = 0; j < fullChunks; j++) {
        newData.appendReadData(chunkLength);
      }
      if (lastChunkDurationUs > 0) {
        int lastChunkLength = (int) (format.bitrate * (mediaDurationUs % chunkDurationUs)
            / (8 * C.MICROS_PER_SECOND));
        newData.appendReadData(lastChunkLength);
      }
    }
    this.lastChunkDurationUs = lastChunkDurationUs == 0 ? chunkDurationUs : lastChunkDurationUs;
    this.chunkCount = lastChunkDurationUs == 0 ? fullChunks : fullChunks + 1;
  }

  public long getChunkCount() {
    return chunkCount;
  }

  public String getUri(int trackSelectionIndex) {
    return "fake://adaptive.media/" + Integer.toString(trackSelectionIndex);
  }

  public long getChunkDuration(int chunkIndex) {
    return chunkIndex == getChunkCount() - 1 ? lastChunkDurationUs : chunkDurationUs;
  }

  public long getStartTime(int chunkIndex) {
    return chunkIndex * chunkDurationUs;
  }

  public int getChunkIndexByPosition(long positionUs) {
    return (int) (positionUs / chunkDurationUs);
  }

}
