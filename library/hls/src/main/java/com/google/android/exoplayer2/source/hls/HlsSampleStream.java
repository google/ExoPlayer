/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream;
import java.io.IOException;

/**
 * {@link SampleStream} for a particular sample queue in HLS.
 */
/* package */ final class HlsSampleStream implements SampleStream {

  private final int trackGroupIndex;
  private final HlsSampleStreamWrapper sampleStreamWrapper;
  private int sampleQueueIndex;

  public HlsSampleStream(HlsSampleStreamWrapper sampleStreamWrapper, int trackGroupIndex) {
    this.sampleStreamWrapper = sampleStreamWrapper;
    this.trackGroupIndex = trackGroupIndex;
    sampleQueueIndex = C.INDEX_UNSET;
  }

  public void unbindSampleQueue() {
    if (sampleQueueIndex != C.INDEX_UNSET) {
      sampleStreamWrapper.unbindSampleQueue(trackGroupIndex);
      sampleQueueIndex = C.INDEX_UNSET;
    }
  }

  // SampleStream implementation.

  @Override
  public boolean isReady() {
    return ensureBoundSampleQueue() && sampleStreamWrapper.isReady(sampleQueueIndex);
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (!ensureBoundSampleQueue() && sampleStreamWrapper.isMappingFinished()) {
      throw new SampleQueueMappingException(
          sampleStreamWrapper.getTrackGroups().get(trackGroupIndex).getFormat(0).sampleMimeType);
    }
    sampleStreamWrapper.maybeThrowError();
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean requireFormat) {
    if (!ensureBoundSampleQueue()) {
      return C.RESULT_NOTHING_READ;
    }
    return sampleStreamWrapper.readData(sampleQueueIndex, formatHolder, buffer, requireFormat);
  }

  @Override
  public int skipData(long positionUs) {
    if (!ensureBoundSampleQueue()) {
      return 0;
    }
    return sampleStreamWrapper.skipData(sampleQueueIndex, positionUs);
  }

  // Internal methods.

  private boolean ensureBoundSampleQueue() {
    if (sampleQueueIndex != C.INDEX_UNSET) {
      return true;
    }
    sampleQueueIndex = sampleStreamWrapper.bindSampleQueueToSampleStream(trackGroupIndex);
    return sampleQueueIndex != C.INDEX_UNSET;
  }
}
