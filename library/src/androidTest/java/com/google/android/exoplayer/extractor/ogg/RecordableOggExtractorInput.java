/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.ogg;

/**
 * A {@link RecordableOggExtractorInput} with convenient methods to record an OGG byte stream.
 */
/* package */ final class RecordableOggExtractorInput extends RecordableExtractorInput {

  private long pageSequenceCounter;

  public RecordableOggExtractorInput(byte[] data, int writeOffset) {
    super(data, writeOffset);
    pageSequenceCounter = 1000;
  }

  public RecordableOggExtractorInput(int maxBytes) {
    this(new byte[maxBytes], 0);
  }

  /**
   * Syntax sugar to make tests more readable.
   *
   * @param laces the laces to record to the data.
   */
  protected void recordOggLaces(final byte[] laces) {
    record(laces);
  }

  /**
   * Syntax sugar to make tests more readable.
   *
   * @param packet the packet bytes to record to the data.
   */
  protected void recordOggPacket(final byte[] packet) {
    record(packet);
  }

  protected void recordOggHeader(final byte headerType, final long granule,
      final byte pageSegmentCount) {
    record((byte) 0x4F); // O
    record((byte) 0x67); // g
    record((byte) 0x67); // g
    record((byte) 0x53); // S
    record(STREAM_REVISION);
    record(headerType);
    recordGranulePosition(granule);
    record((byte) 0x00); // LSB of data serial number
    record((byte) 0x10);
    record((byte) 0x00);
    record((byte) 0x00); // MSB of data serial number
    recordPageSequenceCounter();
    record((byte) 0x00); // LSB of page checksum
    record((byte) 0x00);
    record((byte) 0x00);
    record((byte) 0x00); // MSB of page checksum
    record(pageSegmentCount); // 0 - 255
  }

  protected void recordGranulePosition(long granule) {
    record((byte) (granule & 0xFF));
    record((byte) ((granule >> 8) & 0xFF));
    record((byte) ((granule >> 16) & 0xFF));
    record((byte) ((granule >> 24) & 0xFF));
    record((byte) ((granule >> 32) & 0xFF));
    record((byte) ((granule >> 40) & 0xFF));
    record((byte) ((granule >> 48) & 0xFF));
    record((byte) ((granule >> 56) & 0xFF));
  }

  protected void recordPageSequenceCounter() {
    record((byte) (pageSequenceCounter & 0xFF));
    record((byte) ((pageSequenceCounter >> 8) & 0xFF));
    record((byte) ((pageSequenceCounter >> 16) & 0xFF));
    record((byte) ((pageSequenceCounter++ >> 24) & 0xFF));
  }

}
