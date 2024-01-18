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
package androidx.media3.extractor.ts;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.DtsUtil;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.common.primitives.Ints;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Parses a continuous DTS or DTS UHD byte stream and extracts individual samples. */
@UnstableApi
public final class DtsReader implements ElementaryStreamReader {

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_CORE_HEADER = 1;
  private static final int STATE_FINDING_EXTSS_HEADER_SIZE = 2;
  private static final int STATE_READING_EXTSS_HEADER = 3;
  private static final int STATE_FINDING_UHD_HEADER_SIZE = 4;
  private static final int STATE_READING_UHD_HEADER = 5;
  private static final int STATE_READING_SAMPLE = 6;

  /** Size of core header, in bytes. */
  private static final int CORE_HEADER_SIZE = 18;

  /**
   * Maximum possible size of extension sub-stream header, in bytes. See See ETSI TS 102 114 V1.6.1
   * (2019-08) Section 7.5.2.
   */
  /* package */ static final int EXTSS_HEADER_SIZE_MAX = 4096;

  /**
   * Maximum size of DTS UHD(DTS:X) frame header, in bytes. See ETSI TS 103 491 V1.2.1 (2019-05)
   * Section 6.4.4.3.
   */
  /* package */ static final int FTOC_MAX_HEADER_SIZE = 5408;

  private final ParsableByteArray headerScratchBytes;

  /** The chunk ID is read in synchronized frames and re-used in non-synchronized frames. */
  private final AtomicInteger uhdAudioChunkId;

  @Nullable private final String language;
  private final @C.RoleFlags int roleFlags;

  private @MonotonicNonNull String formatId;
  private @MonotonicNonNull TrackOutput output;

  private int state;
  private int bytesRead;

  /** Used to find the header. */
  private int syncBytes;

  // Used when parsing the header.
  private long sampleDurationUs;
  private @MonotonicNonNull Format format;
  private int sampleSize;
  private @DtsUtil.FrameType int frameType;
  private int extensionSubstreamHeaderSize;
  private int uhdHeaderSize;

  // Used when reading the samples.
  private long timeUs;

  /**
   * Constructs a new reader for DTS elementary streams.
   *
   * @param language Track language.
   * @param roleFlags Track role flags.
   * @param maxHeaderSize Maximum size of the header in a frame.
   */
  public DtsReader(@Nullable String language, @C.RoleFlags int roleFlags, int maxHeaderSize) {
    headerScratchBytes = new ParsableByteArray(new byte[maxHeaderSize]);
    state = STATE_FINDING_SYNC;
    timeUs = C.TIME_UNSET;
    uhdAudioChunkId = new AtomicInteger();
    extensionSubstreamHeaderSize = C.LENGTH_UNSET;
    uhdHeaderSize = C.LENGTH_UNSET;
    this.language = language;
    this.roleFlags = roleFlags;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    syncBytes = 0;
    timeUs = C.TIME_UNSET;
    uhdAudioChunkId.set(0);
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    timeUs = pesTimeUs;
  }

  @Override
  public void consume(ParsableByteArray data) throws ParserException {
    checkStateNotNull(output); // Asserts that createTracks has been called.
    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SYNC:
          if (skipToNextSyncWord(data)) {
            if (frameType == DtsUtil.FRAME_TYPE_UHD_SYNC
                || frameType == DtsUtil.FRAME_TYPE_UHD_NON_SYNC) {
              state = STATE_FINDING_UHD_HEADER_SIZE;
            } else if (frameType == DtsUtil.FRAME_TYPE_CORE) {
              state = STATE_READING_CORE_HEADER;
            } else {
              state = STATE_FINDING_EXTSS_HEADER_SIZE;
            }
          }
          break;
        case STATE_READING_CORE_HEADER:
          if (continueRead(data, headerScratchBytes.getData(), CORE_HEADER_SIZE)) {
            parseCoreHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, CORE_HEADER_SIZE);
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_FINDING_EXTSS_HEADER_SIZE:
          // Read enough bytes to parse the header size information.
          if (continueRead(data, headerScratchBytes.getData(), /* targetLength= */ 7)) {
            extensionSubstreamHeaderSize =
                DtsUtil.parseDtsHdHeaderSize(headerScratchBytes.getData());
            state = STATE_READING_EXTSS_HEADER;
          }
          break;
        case STATE_READING_EXTSS_HEADER:
          if (continueRead(data, headerScratchBytes.getData(), extensionSubstreamHeaderSize)) {
            parseExtensionSubstreamHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, extensionSubstreamHeaderSize);
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_FINDING_UHD_HEADER_SIZE:
          // Read enough bytes to parse the header size information.
          if (continueRead(data, headerScratchBytes.getData(), /* targetLength= */ 6)) {
            uhdHeaderSize = DtsUtil.parseDtsUhdHeaderSize(headerScratchBytes.getData());
            // Adjust the array read position if data read is more than the actual header size.
            if (bytesRead > uhdHeaderSize) {
              int extraBytes = bytesRead - uhdHeaderSize;
              bytesRead -= extraBytes;
              data.setPosition(data.getPosition() - extraBytes);
            }
            state = STATE_READING_UHD_HEADER;
          }
          break;
        case STATE_READING_UHD_HEADER:
          if (continueRead(data, headerScratchBytes.getData(), uhdHeaderSize)) {
            parseUhdHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, uhdHeaderSize);
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_READING_SAMPLE:
          int bytesToRead = min(data.bytesLeft(), sampleSize - bytesRead);
          output.sampleData(data, bytesToRead);
          bytesRead += bytesToRead;
          if (bytesRead == sampleSize) {
            // packetStarted method must be called before consuming samples.
            checkState(timeUs != C.TIME_UNSET);
            output.sampleMetadata(
                timeUs,
                frameType == DtsUtil.FRAME_TYPE_UHD_NON_SYNC ? 0 : C.BUFFER_FLAG_KEY_FRAME,
                sampleSize,
                0,
                null);
            timeUs += sampleDurationUs;
            state = STATE_FINDING_SYNC;
          }
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  public void packetFinished(boolean isEndOfInput) {
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
   * Locates the next SYNC word value in the buffer, advancing the position to the byte that
   * immediately follows it. If SYNC was not located, the position is advanced to the limit.
   *
   * @param pesBuffer The buffer whose position should be advanced.
   * @return Whether SYNC word was found.
   */
  private boolean skipToNextSyncWord(ParsableByteArray pesBuffer) {
    while (pesBuffer.bytesLeft() > 0) {
      syncBytes <<= 8;
      syncBytes |= pesBuffer.readUnsignedByte();
      frameType = DtsUtil.getFrameType(syncBytes);
      if (frameType != DtsUtil.FRAME_TYPE_UNKNOWN) {
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
  private void parseCoreHeader() {
    byte[] frameData = headerScratchBytes.getData();
    if (format == null) {
      format = DtsUtil.parseDtsFormat(frameData, formatId, language, roleFlags, null);
      output.format(format);
    }
    sampleSize = DtsUtil.getDtsFrameSize(frameData);
    // In this class a sample is an access unit (frame in DTS), but the format's sample rate
    // specifies the number of PCM audio samples per second.
    sampleDurationUs =
        Ints.checkedCast(
            Util.sampleCountToDurationUs(
                DtsUtil.parseDtsAudioSampleCount(frameData), format.sampleRate));
  }

  /** Parses the DTS Extension Sub-stream header. */
  @RequiresNonNull("output")
  private void parseExtensionSubstreamHeader() throws ParserException {
    DtsUtil.DtsHeader dtsHeader = DtsUtil.parseDtsHdHeader(headerScratchBytes.getData());
    updateFormatWithDtsHeaderInfo(dtsHeader);
    sampleSize = dtsHeader.frameSize;
    sampleDurationUs = dtsHeader.frameDurationUs == C.TIME_UNSET ? 0 : dtsHeader.frameDurationUs;
  }

  /** Parses the UHD frame header. */
  @RequiresNonNull({"output"})
  private void parseUhdHeader() throws ParserException {
    DtsUtil.DtsHeader dtsHeader =
        DtsUtil.parseDtsUhdHeader(headerScratchBytes.getData(), uhdAudioChunkId);
    // Format updates will happen only in FTOC sync frames.
    if (frameType == DtsUtil.FRAME_TYPE_UHD_SYNC) {
      updateFormatWithDtsHeaderInfo(dtsHeader);
    }
    sampleSize = dtsHeader.frameSize;
    sampleDurationUs = dtsHeader.frameDurationUs == C.TIME_UNSET ? 0 : dtsHeader.frameDurationUs;
  }

  @RequiresNonNull({"output"})
  private void updateFormatWithDtsHeaderInfo(DtsUtil.DtsHeader dtsHeader) {
    if (dtsHeader.sampleRate == C.RATE_UNSET_INT || dtsHeader.channelCount == C.LENGTH_UNSET) {
      return;
    }
    if (format == null
        || dtsHeader.channelCount != format.channelCount
        || dtsHeader.sampleRate != format.sampleRate
        || !Util.areEqual(dtsHeader.mimeType, format.sampleMimeType)) {
      Format.Builder formatBuilder = format == null ? new Format.Builder() : format.buildUpon();
      format =
          formatBuilder
              .setId(formatId)
              .setSampleMimeType(dtsHeader.mimeType)
              .setChannelCount(dtsHeader.channelCount)
              .setSampleRate(dtsHeader.sampleRate)
              .setLanguage(language)
              .setRoleFlags(roleFlags)
              .build();
      output.format(format);
    }
  }
}
