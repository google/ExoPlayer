/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.UnreportedDiscontinuityException;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Log;

public class HlsCheckedSampleQueue extends SampleQueue {
  private static final String TAG = "HlsCheckedSampleQueue";

  private long lowestTimeUs = C.TIME_UNSET;
  private long highestTimeUs = C.TIME_UNSET;

  private HlsMediaChunk chunk;
  private boolean loggedFirst = false;

  HlsCheckedSampleQueue(Allocator allocator, DrmSessionManager drmSessionManager) {
    super(allocator, drmSessionManager);
  }

  void setCurrentLoadingChunk(HlsMediaChunk chunk) {
    double tolerance = (chunk.endTimeUs - chunk.startTimeUs) * 0.1;
    this.lowestTimeUs = chunk.startTimeUs;
    this.highestTimeUs = (long) (chunk.endTimeUs + tolerance);
    this.chunk = chunk;
    loggedFirst = false;
  }


  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset,  @Nullable CryptoData cryptoData) {
    if (lowestTimeUs != C.TIME_UNSET && timeUs < lowestTimeUs && ! loggedFirst) {
      Log.d(TAG, "sampleMetadata() - committed timeUs: " + timeUs + " is " + C.usToMs(lowestTimeUs - timeUs) + "ms less then segment start time.  chunk: " + chunk.dataSpec.uri);
      loggedFirst = true;
    }
    if (lowestTimeUs != C.TIME_UNSET && timeUs < (lowestTimeUs - C.msToUs(50_000))) {
      Log.d(TAG, "sampleMetadata() - committed timeUs: " + timeUs + " is " + C.usToMs(lowestTimeUs - timeUs) + "ms less (MUCH!) then segment start time.  chunk: " + chunk.dataSpec.uri);
      throw new UnreportedDiscontinuityException(timeUs, chunk.dataSpec.uri);
    }
    if (highestTimeUs != C.TIME_UNSET && timeUs > highestTimeUs) {
      Log.d(TAG, "sampleMetadata() - committed timeUs: " + timeUs + " is " + C.usToMs(lowestTimeUs - timeUs) + "ms greater then segment end time.  chunk: " + chunk.dataSpec.uri);
      throw new UnreportedDiscontinuityException(timeUs, chunk.dataSpec.uri);
    }
    super.sampleMetadata(timeUs, flags, size, offset, cryptoData);
  }

}
