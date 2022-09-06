/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.exoplayer.rtsp.reader;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.exoplayer.rtsp.reader.RtpReaderUtils.toSampleTimeUs;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.rtsp.RtpPacket;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses an MP4A-LATM byte stream carried on RTP packets, and extracts MP4A-LATM Access Units.
 * Refer to RFC3016 for more details.
 */
@UnstableApi
/* package */ final class RtpMp4aReader implements RtpPayloadReader {
  private static final String TAG = "RtpMp4aLatmReader";

  private static final String PARAMETER_MP4A_CONFIG = "config";

  private final RtpPayloadFormat payloadFormat;
  private @MonotonicNonNull TrackOutput trackOutput;
  private long firstReceivedTimestamp;
  private int previousSequenceNumber;
  /** The combined size of a sample that is fragmented into multiple subFrames. */
  private int fragmentedSampleSizeBytes;
  private long startTimeOffsetUs;
  private long sampleTimeUsOfFragmentedSample;
  private int numSubFrames;

  /** Creates an instance. */
  public RtpMp4aReader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
    previousSequenceNumber = C.INDEX_UNSET;
    fragmentedSampleSizeBytes = 0;
    // The start time offset must be 0 until the first seek.
    startTimeOffsetUs = 0;
    sampleTimeUsOfFragmentedSample = C.TIME_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);
    castNonNull(trackOutput).format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {
    checkState(firstReceivedTimestamp == C.TIME_UNSET);
    firstReceivedTimestamp = timestamp;
    try {
      numSubFrames = getNumOfSubframesFromMpeg4AudioConfig(payloadFormat.fmtpParameters);
    } catch (ParserException e) {
      e.printStackTrace();
    }
}

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker)
      throws ParserException {
    checkStateNotNull(trackOutput);

    int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
    if(fragmentedSampleSizeBytes > 0 && expectedSequenceNumber < sequenceNumber) {
      outputSampleMetadataForFragmentedPackets();
    }
    int sampleOffset = 0;
    for (int subFrame = 0; subFrame <= numSubFrames; subFrame++) {
      int sampleLength = 0;

      /* Each subframe starts with a variable length encoding */
      for (; sampleOffset < data.bytesLeft(); sampleOffset++) {
        sampleLength += data.getData()[sampleOffset] & 0xff;
        if ((data.getData()[sampleOffset] & 0xff) != 0xff) {
          break;
        }
      }
      sampleOffset++;
      data.setPosition(sampleOffset);

      // Write the audio sample
      trackOutput.sampleData(data, sampleLength);
      sampleOffset += sampleLength;
      fragmentedSampleSizeBytes += sampleLength;
    }
    sampleTimeUsOfFragmentedSample = toSampleTimeUs(startTimeOffsetUs, timestamp,
        firstReceivedTimestamp, payloadFormat.clockRate);
    if (rtpMarker) {
      outputSampleMetadataForFragmentedPackets();
    }
    previousSequenceNumber = sequenceNumber;
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    fragmentedSampleSizeBytes = 0;
    startTimeOffsetUs = timeUs;
  }

  // Internal methods.

  /**
   * Parses an MPEG-4 Audio Stream Mux configuration, as defined in ISO/IEC14496-3. FMTP attribute
   * contains config which is a byte array containing the MPEG-4 Audio Stream Mux configuration to
   * parse.
   *
   * @param fmtpAttributes The format parameters, mapped from the SDP FMTP attribute.
   * @return The number of subframes.
   * @throws ParserException If the MPEG-4 Audio Stream Mux configuration cannot be parsed due to
   *     unsupported audioMuxVersion.
   */
  private static Integer getNumOfSubframesFromMpeg4AudioConfig(
      ImmutableMap<String, String> fmtpAttributes) throws ParserException {
    @Nullable String configInput = fmtpAttributes.get(PARAMETER_MP4A_CONFIG);
    int numSubFrames = 0;
    if (configInput != null && configInput.length() % 2 == 0) {
      byte[] configBuffer = Util.getBytesFromHexString(configInput);
      ParsableBitArray scratchBits = new ParsableBitArray(configBuffer);
      int audioMuxVersion = scratchBits.readBits(1);
      if (audioMuxVersion == 0) {
        checkArgument(scratchBits.readBits(1) == 1, "Invalid allStreamsSameTimeFraming.");
        numSubFrames = scratchBits.readBits(6);
        checkArgument(scratchBits.readBits(4) == 0, "Invalid numProgram.");
        checkArgument(scratchBits.readBits(3) == 0, "Invalid numLayer.");
      } else {
        throw ParserException.createForMalformedDataOfUnknownType(
            "unsupported audio mux version: " + audioMuxVersion, null);
      }
    }
    return numSubFrames;
  }

  /**
   * Outputs sample metadata.
   *
   * <p>Call this method only when receiving a end of Mpeg4 partition
   */
  private void outputSampleMetadataForFragmentedPackets() {
    trackOutput.sampleMetadata(
        sampleTimeUsOfFragmentedSample,
        C.BUFFER_FLAG_KEY_FRAME,
        fragmentedSampleSizeBytes,
        /* offset= */ 0,
        /* cryptoData= */ null);
    fragmentedSampleSizeBytes = 0;
    sampleTimeUsOfFragmentedSample = C.TIME_UNSET;
  }
}
