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
package com.google.android.exoplayer.hls.parser;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * Facilitates the extraction of AAC samples from elementary audio files formatted as AAC with ADTS
 * headers.
 */
public class AdtsExtractor extends HlsExtractor {

  private static final int MAX_PACKET_SIZE = 200;

  private final long firstSampleTimestamp;
  private final ParsableByteArray packetBuffer;
  private final AdtsReader adtsReader;

  // Accessed only by the loading thread.
  private boolean firstPacket;
  // Accessed by both the loading and consuming threads.
  private volatile boolean prepared;

  public AdtsExtractor(boolean shouldSpliceIn, long firstSampleTimestamp, BufferPool bufferPool) {
    super(shouldSpliceIn);
    this.firstSampleTimestamp = firstSampleTimestamp;
    packetBuffer = new ParsableByteArray(MAX_PACKET_SIZE);
    adtsReader = new AdtsReader(bufferPool);
    firstPacket = true;
  }

  @Override
  public int getTrackCount() {
    Assertions.checkState(prepared);
    return 1;
  }

  @Override
  public MediaFormat getFormat(int track) {
    Assertions.checkState(prepared);
    return adtsReader.getMediaFormat();
  }

  @Override
  public boolean isPrepared() {
    return prepared;
  }

  @Override
  public void release() {
    adtsReader.release();
  }

  @Override
  public long getLargestSampleTimestamp() {
    return adtsReader.getLargestParsedTimestampUs();
  }

  @Override
  public boolean getSample(int track, SampleHolder holder) {
    Assertions.checkState(prepared);
    Assertions.checkState(track == 0);
    return adtsReader.getSample(holder);
  }

  @Override
  public void discardUntil(int track, long timeUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(track == 0);
    adtsReader.discardUntil(timeUs);
  }

  @Override
  public boolean hasSamples(int track) {
    Assertions.checkState(prepared);
    Assertions.checkState(track == 0);
    return !adtsReader.isEmpty();
  }

  @Override
  public int read(DataSource dataSource) throws IOException {
    int bytesRead = dataSource.read(packetBuffer.data, 0, MAX_PACKET_SIZE);
    if (bytesRead == -1) {
      return -1;
    }

    packetBuffer.setPosition(0);
    packetBuffer.setLimit(bytesRead);

    // TODO: Make it possible for adtsReader to consume the dataSource directly, so that it becomes
    // unnecessary to copy the data through packetBuffer.
    adtsReader.consume(packetBuffer, firstSampleTimestamp, firstPacket);
    firstPacket = false;
    if (!prepared) {
      prepared = adtsReader.hasMediaFormat();
    }
    return bytesRead;
  }

  @Override
  protected SampleQueue getSampleQueue(int track) {
    Assertions.checkState(track == 0);
    return adtsReader;
  }

}
