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
package com.google.android.exoplayer2.extractor.ogg;

import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;

/** Provides ogg/vorbis test data in bytes for unit tests. */
/* package */ final class OggTestData {

  public static FakeExtractorInput createInput(byte[] data, boolean simulateUnknownLength) {
    return new FakeExtractorInput.Builder()
        .setData(data)
        .setSimulateIOErrors(true)
        .setSimulateUnknownLength(simulateUnknownLength)
        .setSimulatePartialReads(true)
        .build();
  }

  public static byte[] buildOggHeader(
      int headerType, long granule, int pageSequenceCounter, int pageSegmentCount) {
    return TestUtil.createByteArray(
        0x4F,
        0x67,
        0x67,
        0x53, // Oggs.
        0x00, // Stream revision.
        headerType,
        (int) (granule) & 0xFF,
        (int) (granule >> 8) & 0xFF,
        (int) (granule >> 16) & 0xFF,
        (int) (granule >> 24) & 0xFF,
        (int) (granule >> 32) & 0xFF,
        (int) (granule >> 40) & 0xFF,
        (int) (granule >> 48) & 0xFF,
        (int) (granule >> 56) & 0xFF,
        0x00, // LSB of data serial number.
        0x10,
        0x00,
        0x00, // MSB of data serial number.
        (pageSequenceCounter) & 0xFF,
        (pageSequenceCounter >> 8) & 0xFF,
        (pageSequenceCounter >> 16) & 0xFF,
        (pageSequenceCounter >> 24) & 0xFF,
        0x00, // LSB of page checksum.
        0x00,
        0x10,
        0x00, // MSB of page checksum.
        pageSegmentCount);
  }

}
