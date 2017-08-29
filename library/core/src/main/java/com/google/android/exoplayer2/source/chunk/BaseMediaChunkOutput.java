/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.chunk;

import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.exoplayer2.extractor.DummyTrackOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper.TrackOutputProvider;

/**
 * An output for {@link BaseMediaChunk}s.
 */
/* package */ final class BaseMediaChunkOutput implements TrackOutputProvider {

  private static final String TAG = "BaseMediaChunkOutput";

  private final int[] trackTypes;
  private final SampleQueue[] sampleQueues;

  /**
   * @param primaryTrackType The type of the primary track.
   * @param primarySampleQueue The primary track sample queues.
   * @param embeddedTrackTypes The types of any embedded tracks, or null.
   * @param embeddedSampleQueues The track sample queues for any embedded tracks, or null.
   */
  @SuppressWarnings("ConstantConditions")
  public BaseMediaChunkOutput(int primaryTrackType, SampleQueue primarySampleQueue,
      @Nullable int[] embeddedTrackTypes, @Nullable SampleQueue[] embeddedSampleQueues) {
    int embeddedTrackCount = embeddedTrackTypes == null ? 0 : embeddedTrackTypes.length;
    trackTypes = new int[1 + embeddedTrackCount];
    sampleQueues = new SampleQueue[1 + embeddedTrackCount];
    trackTypes[0] = primaryTrackType;
    sampleQueues[0] = primarySampleQueue;
    for (int i = 0; i < embeddedTrackCount; i++) {
      trackTypes[i + 1] = embeddedTrackTypes[i];
      sampleQueues[i + 1] = embeddedSampleQueues[i];
    }
  }

  @Override
  public TrackOutput track(int id, int type) {
    for (int i = 0; i < trackTypes.length; i++) {
      if (type == trackTypes[i]) {
        return sampleQueues[i];
      }
    }
    Log.e(TAG, "Unmatched track of type: " + type);
    return new DummyTrackOutput();
  }

  @Override
  public boolean isPrimaryTrack(int type) {
    return type == trackTypes[0];
  }

  /**
   * Returns the current absolute write indices of the individual sample queues.
   */
  public int[] getWriteIndices() {
    int[] writeIndices = new int[sampleQueues.length];
    for (int i = 0; i < sampleQueues.length; i++) {
      if (sampleQueues[i] != null) {
        writeIndices[i] = sampleQueues[i].getWriteIndex();
      }
    }
    return writeIndices;
  }

  /**
   * Sets an offset that will be added to the timestamps (and sub-sample timestamps) of samples
   * subsequently written to the sample queues.
   */
  public void setSampleOffsetUs(long sampleOffsetUs) {
    for (SampleQueue sampleQueue : sampleQueues) {
      if (sampleQueue != null) {
        sampleQueue.setSampleOffsetUs(sampleOffsetUs);
      }
    }
  }

}
