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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.extractor.DefaultTrackOutput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.upstream.Allocator;

import android.util.SparseArray;

/**
 * An {@link ExtractorOutput} for HLS playbacks.
 */
/* package */ final class HlsOutput implements ExtractorOutput {

  private final Allocator allocator;
  private final SparseArray<DefaultTrackOutput> sampleQueues = new SparseArray<>();

  private boolean prepared;
  private DefaultTrackOutput[] trackOutputArray;
  private volatile boolean tracksBuilt;

  public HlsOutput(Allocator allocator) {
    this.allocator = allocator;
  }

  // Called by the consuming thread.

  /**
   * Prepares the output, or does nothing if the output is already prepared.
   *
   * @return True if the output is prepared, false otherwise.
   */
  public boolean prepare() {
    if (prepared) {
      return true;
    } else if (!tracksBuilt) {
      return false;
    } else {
      if (trackOutputArray == null) {
        trackOutputArray = new DefaultTrackOutput[sampleQueues.size()];
        for (int i = 0; i < trackOutputArray.length; i++) {
          trackOutputArray[i] = sampleQueues.valueAt(i);
        }
      }
      for (DefaultTrackOutput sampleQueue : trackOutputArray) {
        if (sampleQueue.getUpstreamFormat() == null) {
          return false;
        }
      }
      prepared = true;
      return true;
    }
  }

  /**
   * Returns the array of track outputs, or null if the output is not yet prepared.
   */
  public DefaultTrackOutput[] getTrackOutputs() {
    return trackOutputArray;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Clears all track outputs.
   */
  public void clear() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).clear();
    }
  }

  /**
   * Indicates to all track outputs that they should splice in subsequently queued samples.
   */
  public void splice() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).splice();
    }
  }

  // ExtractorOutput implementation. Called by the loading thread.

  @Override
  public DefaultTrackOutput track(int id) {
    if (sampleQueues.indexOfKey(id) >= 0) {
      return sampleQueues.get(id);
    }
    DefaultTrackOutput trackOutput = new DefaultTrackOutput(allocator);
    sampleQueues.put(id, trackOutput);
    return trackOutput;
  }

  @Override
  public void endTracks() {
    tracksBuilt = true;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    // Do nothing.
  }

}
