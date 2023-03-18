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
package com.google.android.exoplayer2.extractor.ts;

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.DtsUtil;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Parses a continuous DTS byte stream and extracts individual samples. */
public final class DtsReader implements ElementaryStreamReader {

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_CORE_HEADER = 1;
  private static final int STATE_READING_EXTSS_HEADER = 2;
  private static final int STATE_FIND_EXTSS_HEADER_SIZE = 3;
  private static final int STATE_READING_SAMPLE = 4;

  private static final int CORE_HEADER_SIZE = 18;
  /** Maximum possible size of extension sub-stream header
   * See See ETSI TS 102 114 V1.6.1 (2019-08) Section 7.5.2
   * */
  private static final int EXTSS_HEADER_SIZE_MAX = 4096;
  /** Minimum number of bytes required for parse and extract header size information */
  private static final int EXTSS_HEADER_SIZE_MIN = 10;

  private final ParsableByteArray headerScratchBytes;
  @Nullable private final String language;

  private @MonotonicNonNull String formatId;
  private @MonotonicNonNull TrackOutput output;

  private int state;
  private int bytesRead;

  // Used to find the header.
  private int syncBytes;

  // Used when parsing the header.
  private long sampleDurationUs;
  private @MonotonicNonNull Format format;
  private int sampleSize;
  private boolean isCoreSync;
  private int headerSizeToRead;

  // Used when reading the samples.
  private long timeUs;

  /**
   * Constructs a new reader for DTS elementary streams.
   *
   * @param language Track language.
   */
  public DtsReader(@Nullable String language) {
    // The extension sub-stream header size can be up to 4KB
    headerScratchBytes = new ParsableByteArray(new byte[EXTSS_HEADER_SIZE_MAX]);
    state = STATE_FINDING_SYNC;
    timeUs = C.TIME_UNSET;
    this.language = language;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    syncBytes = 0;
    timeUs = C.TIME_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    if (pesTimeUs != C.TIME_UNSET) {
      timeUs = pesTimeUs;
    }
  }

  @Override
  public void consume(ParsableByteArray data) {
    Assertions.checkStateNotNull(output); // Asserts that createTracks has been called.
    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SYNC:
          if (skipToNextSync(data)) {
            if (isCoreSync) {
              state = STATE_READING_CORE_HEADER;
            } else {
              state = STATE_FIND_EXTSS_HEADER_SIZE;
            }
          }
          break;
        case STATE_READING_CORE_HEADER:
          if (continueRead(data, headerScratchBytes.getData(), CORE_HEADER_SIZE)) {
            parseHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, CORE_HEADER_SIZE);
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_FIND_EXTSS_HEADER_SIZE:
          // Read enough bytes to parse the header size information.
          if (continueRead(data, headerScratchBytes.getData(), EXTSS_HEADER_SIZE_MIN)) {
            findExtssHeaderSize();
            state = STATE_READING_EXTSS_HEADER;
          }
          break;
        case STATE_READING_EXTSS_HEADER:
          if (continueRead(data, headerScratchBytes.getData(), headerSizeToRead)) {
            parseExtssHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, headerSizeToRead);
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_READING_SAMPLE:
          int bytesToRead = min(data.bytesLeft(), sampleSize - bytesRead);
          output.sampleData(data, bytesToRead);
          bytesRead += bytesToRead;
          if (bytesRead == sampleSize) {
            if (timeUs != C.TIME_UNSET) {
              output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
              timeUs += sampleDurationUs;
            }
            state = STATE_FINDING_SYNC;
          }
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
   * that the data should be written into {@code target} starting from an offset of zero.
   *
   * @param source The source from which to read.
   * @param target The target into which data is to be read.
   * @param targetLength The target length of the read.
   * @return Whether the target length was reached.
   */
  private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
    int bytesToRead = min(source.bytesLeft(), targetLength - bytesRead);
    source.readBytes(target, bytesRead, bytesToRead);
    bytesRead += bytesToRead;
    return bytesRead == targetLength;
  }

  /**
   * Locates the next SYNC value in the buffer, advancing the position to the byte that immediately
   * follows it. If SYNC was not located, the position is advanced to the limit.
   *
   * @param pesBuffer The buffer whose position should be advanced.
   * @return Whether SYNC was found.
   */
  private boolean skipToNextSync(ParsableByteArray pesBuffer) {
    while (pesBuffer.bytesLeft() > 0) {
      syncBytes <<= 8;
      syncBytes |= pesBuffer.readUnsignedByte();
      isCoreSync = DtsUtil.isSyncWord(syncBytes);
      if (isCoreSync || DtsUtil.isExtssSyncWord(syncBytes)) {
        byte[] headerData = headerScratchBytes.getData();
        headerData[0] = (byte) ((syncBytes >> 24) & 0xFF);
        headerData[1] = (byte) ((syncBytes >> 16) & 0xFF);
        headerData[2] = (byte) ((syncBytes >> 8) & 0xFF);
        headerData[3] = (byte) (syncBytes & 0xFF);
        bytesRead = 4;
        syncBytes = 0;
        return true;
      }
    }
    return false;
  }

  /** Parses the DTS Core Sub-stream header. */
  @RequiresNonNull("output")
  private void parseHeader() {
    byte[] frameData = headerScratchBytes.getData();
    if (format == null) {
      format = DtsUtil.parseDtsFormat(frameData, formatId, language, null);
      output.format(format);
    }
    sampleSize = DtsUtil.getDtsFrameSize(frameData);
    // In this class a sample is an access unit (frame in DTS), but the format's sample rate
    // specifies the number of PCM audio samples per second.
    sampleDurationUs =
        (int)
            (C.MICROS_PER_SECOND * DtsUtil.parseDtsAudioSampleCount(frameData) / format.sampleRate);
  }

  /** Find the actual size of DTS Extension Sub-stream header. */
  private void findExtssHeaderSize() {
    byte[] frameData = new byte[EXTSS_HEADER_SIZE_MIN];
    headerScratchBytes.setPosition(0);
    headerScratchBytes.readBytes(frameData, 0, EXTSS_HEADER_SIZE_MIN);
    headerSizeToRead = DtsUtil.parseDtsHdHeaderSize(frameData);
    if (headerSizeToRead < bytesRead) { // Already read more data than the actual header size
      headerSizeToRead = bytesRead; // Setting target read length equal to bytesRead
    }
  }

  /** Parses the DTS Extension Sub-stream header. */
  @RequiresNonNull("output")
  private void parseExtssHeader() {
    byte[] frameData = headerScratchBytes.getData();
    DtsUtil.DtsFormatInfo formatInfo = DtsUtil.parseDtsHdFormat(frameData);
    if (format == null
        || formatInfo.channelCount != format.channelCount
        || formatInfo.sampleRate != format.sampleRate
        || !Util.areEqual(formatInfo.mimeType, format.sampleMimeType)) {
      format = new Format.Builder()
          .setId(formatId)
          .setSampleMimeType(formatInfo.mimeType)
          .setChannelCount(formatInfo.channelCount)
          .setSampleRate(formatInfo.sampleRate)
          .setDrmInitData(null)
          .setLanguage(language)
          .build();
      output.format(format);
    }
    sampleSize = formatInfo.frameSize;
    // In this class a sample is an access unit (frame in DTS), but the format's sample rate
    // specifies the number of PCM audio samples per second.
    sampleDurationUs = (int)(C.MICROS_PER_SECOND * formatInfo.sampleCount / format.sampleRate);
  }
}
