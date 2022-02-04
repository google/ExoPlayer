/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import java.io.IOException;

public class MockNalChunkPeeker extends NalChunkPeeker {
  private boolean skip;
  public MockNalChunkPeeker(int peakSize, boolean skip) {
    super(0, new FakeTrackOutput(false), new ChunkClock(1_000_000L, 24), peakSize);
    this.skip = skip;
  }

  @Override
  void processChunk(ExtractorInput input, int nalTypeOffset) throws IOException {

  }

  @Override
  boolean skip(byte nalType) {
    return skip;
  }
}
