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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.rtsp.RtpPacket;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses an AMR byte stream carried on RTP packets and extracts individual samples. Interleaving
 * mode is not supported.
 */
/* package */ final class RtpAmrReader implements RtpPayloadReader {
  private static final String TAG = "RtpAmrReader";
  /**
   * The frame size in bytes, including header (1 byte), for each of the 16 frame types for AMR
   * narrow band.
   */
  private static final int[] frameSizeBytesByTypeNb = {
    13,
    14,
    16,
    18,
    20,
    21,
    27,
    32,
    6, // AMR SID
    7, // GSM-EFR SID
    6, // TDMA-EFR SID
    6, // PDC-EFR SID
    1, // Future use
    1, // Future use
    1, // Future use
    1 // No data
  };

  /**
   * The frame size in bytes, including header (1 byte), for each of the 16 frame types for AMR wide
   * band.
   */
  private static final int[] frameSizeBytesByTypeWb = {
    18,
    24,
    33,
    37,
    41,
    47,
    51,
    59,
    61,
    6, // AMR-WB SID
    1, // Future use
    1, // Future use
    1, // Future use
    1, // Future use
    1, // speech lost
    1 // No data
  };

  private final RtpPayloadFormat payloadFormat;
  private @MonotonicNonNull TrackOutput trackOutput;
  private long firstReceivedTimestamp;
  private int previousSequenceNumber;
  private long startTimeOffsetUs;
  private final int sampleRate;
  private boolean isWideBand;

  public RtpAmrReader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
    previousSequenceNumber = C.INDEX_UNSET;
    this.sampleRate = this.payloadFormat.clockRate;
    this.isWideBand = (payloadFormat.format.sampleMimeType == MimeTypes.AUDIO_AMR_WB);
  }

  // RtpPayloadReader implementation.
  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_AUDIO);
    trackOutput.format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {
    this.firstReceivedTimestamp = timestamp;
  }

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker) {
    // Check that this packet is in the sequence of the previous packet.
    if (previousSequenceNumber != C.INDEX_UNSET) {
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (sequenceNumber != expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d.",
                expectedSequenceNumber, sequenceNumber));
      }
    }
    checkNotNull(trackOutput);
    /**
     * AMR as RTP payload RFC4867 Section-4.2
     * +----------------+-------------------+----------------
     * | payload header | table of contents | speech data ...
     * +----------------+-------------------+----------------
     *
     * Payload header RFC4867 Section-4.4.1
     * As interleaving is not supported currently, our header won't contain ILL and ILP
     * +-+-+-+-+-+-+-+
     * | CMR |R|R|R|R|
     * +-+-+-+-+-+-+-+
     */
    // skip CMR and reserved bits
    data.skipBytes(1);
    // Loop over sampleSize to send multiple frames along with appropriate timestamp when compound
    // payload support is added
    int frameType = (data.peekUnsignedByte() >> 3) & 0x0f;
    int frameSize = getFrameSize(frameType, isWideBand);
    int sampleSize = data.bytesLeft();
    checkArgument(sampleSize == frameSize, "compound payload not supported currently");
    trackOutput.sampleData(data, sampleSize);
    long sampleTimeUs =
        toSampleTimeUs(startTimeOffsetUs, timestamp, firstReceivedTimestamp, sampleRate);
    trackOutput.sampleMetadata(
        sampleTimeUs,
        C.BUFFER_FLAG_KEY_FRAME,
        sampleSize,
        /* offset= */ 0,
        /* encryptionData= */ null);
    previousSequenceNumber = sequenceNumber;
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    startTimeOffsetUs = timeUs;
  }

  // Internal methods.

  private boolean isValidFrameType(int frameType) {
    if (frameType < 0 || frameType > 15) {
      return false;
    }
    // For wide band, type 10-13 are for future use.
    // For narrow band, type 12-14 are for future use.
    return isWideBand ? (frameType < 10 || frameType > 13) : (frameType < 12 || frameType > 14);
  }

  public int getFrameSize(int frameType, boolean isWideBand) {
    checkArgument(
        isValidFrameType(frameType),
        "Illegal AMR " + (isWideBand ? "WB" : "NB") + " frame type " + frameType);

    return isWideBand ? frameSizeBytesByTypeWb[frameType] : frameSizeBytesByTypeNb[frameType];
  }

  /** Returns the correct sample time from RTP timestamp, accounting for the AMR sampling rate. */
  private static long toSampleTimeUs(
      long startTimeOffsetUs, long rtpTimestamp, long firstReceivedRtpTimestamp, int sampleRate) {
    return startTimeOffsetUs
        + Util.scaleLargeTimestamp(
            rtpTimestamp - firstReceivedRtpTimestamp,
            /* multiplier= */ C.MICROS_PER_SECOND,
            /* divisor= */ sampleRate);
  }
}
