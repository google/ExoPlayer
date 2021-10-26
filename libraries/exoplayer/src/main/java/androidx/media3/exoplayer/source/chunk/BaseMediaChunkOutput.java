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
package androidx.media3.exoplayer.source.chunk;

import androidx.media3.common.C;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.SampleQueue;
import androidx.media3.exoplayer.source.chunk.ChunkExtractor.TrackOutputProvider;
import androidx.media3.extractor.DummyTrackOutput;
import androidx.media3.extractor.TrackOutput;

/**
 * A {@link TrackOutputProvider} that provides {@link TrackOutput TrackOutputs} based on a
 * predefined mapping from track type to output.
 */
@UnstableApi
public final class BaseMediaChunkOutput implements TrackOutputProvider {

  private static final String TAG = "BaseMediaChunkOutput";

  private final @C.TrackType int[] trackTypes;
  private final SampleQueue[] sampleQueues;

  /**
   * @param trackTypes The track types of the individual track outputs.
   * @param sampleQueues The individual sample queues.
   */
  public BaseMediaChunkOutput(int[] trackTypes, SampleQueue[] sampleQueues) {
    this.trackTypes = trackTypes;
    this.sampleQueues = sampleQueues;
  }

  @Override
  public TrackOutput track(int id, @C.TrackType int type) {
    for (int i = 0; i < trackTypes.length; i++) {
      if (type == trackTypes[i]) {
        return sampleQueues[i];
      }
    }
    Log.e(TAG, "Unmatched track of type: " + type);
    return new DummyTrackOutput();
  }

  /** Returns the current absolute write indices of the individual sample queues. */
  public int[] getWriteIndices() {
    int[] writeIndices = new int[sampleQueues.length];
    for (int i = 0; i < sampleQueues.length; i++) {
      writeIndices[i] = sampleQueues[i].getWriteIndex();
    }
    return writeIndices;
  }

  /**
   * Sets an offset that will be added to the timestamps (and sub-sample timestamps) of samples
   * subsequently written to the sample queues.
   */
  public void setSampleOffsetUs(long sampleOffsetUs) {
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.setSampleOffsetUs(sampleOffsetUs);
    }
  }
}
