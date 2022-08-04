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

import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.exoplayer.rtsp.reader.RtpReaderUtils.toSampleTimeUs;

import androidx.media3.common.C;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.rtsp.RtpPacket;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import com.google.common.primitives.Bytes;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses an MPEG4 byte stream carried on RTP packets, and extracts MPEG4 Access Units. Refer to
 * RFC6416 for more details.
 */
@UnstableApi
/* package */ final class RtpMpeg4Reader implements RtpPayloadReader {
  private static final String TAG = "RtpMpeg4Reader";

  private static final int MEDIA_CLOCK_FREQUENCY = 90_000;

  /** VOP (Video Object Plane) unit type. */
  private static final int I_VOP = 0;

  private final RtpPayloadFormat payloadFormat;
  private @MonotonicNonNull TrackOutput trackOutput;
  private @C.BufferFlags int bufferFlags;

  /**
   * First received RTP timestamp. All RTP timestamps are dimension-less, the time base is defined
   * by {@link #MEDIA_CLOCK_FREQUENCY}.
   */
  private long firstReceivedTimestamp;

  private int previousSequenceNumber;
  private long startTimeOffsetUs;
  private int sampleLength;

  /** Creates an instance. */
  public RtpMpeg4Reader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
    previousSequenceNumber = C.INDEX_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);
    castNonNull(trackOutput).format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {}

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker) {
    checkStateNotNull(trackOutput);
    // Check that this packet is in the sequence of the previous packet.
    if (previousSequenceNumber != C.INDEX_UNSET) {
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (sequenceNumber != expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d."
                    + " Dropping packet.",
                expectedSequenceNumber, sequenceNumber));
      }
    }

    // Parse VOP Type and get the buffer flags
    int limit = data.bytesLeft();
    trackOutput.sampleData(data, limit);
    if (sampleLength == 0) {
      bufferFlags = getBufferFlagsFromVop(data);
    }
    sampleLength += limit;

    // RTP marker indicates the last packet carrying a VOP.
    if (rtpMarker) {
      if (firstReceivedTimestamp == C.TIME_UNSET) {
        firstReceivedTimestamp = timestamp;
      }

      long timeUs =
          toSampleTimeUs(
              startTimeOffsetUs, timestamp, firstReceivedTimestamp, MEDIA_CLOCK_FREQUENCY);
      trackOutput.sampleMetadata(timeUs, bufferFlags, sampleLength, 0, null);
      sampleLength = 0;
    }
    previousSequenceNumber = sequenceNumber;
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    startTimeOffsetUs = timeUs;
    sampleLength = 0;
  }

  // Internal methods.

  /**
   * Returns VOP (Video Object Plane) Coding type.
   *
   * <p>Sets {@link #bufferFlags} according to the VOP Coding type.
   */
  private static @C.BufferFlags int getBufferFlagsFromVop(ParsableByteArray data) {
    // search for VOP_START_CODE (00 00 01 B6)
    byte[] inputData = data.getData();
    byte[] startCode = new byte[] {0x0, 0x0, 0x1, (byte) 0xB6};
    int vopStartCodePos = Bytes.indexOf(inputData, startCode);
    if (vopStartCodePos != -1) {
      data.setPosition(vopStartCodePos + 4);
      int vopType = data.peekUnsignedByte() >> 6;
      return vopType == I_VOP ? C.BUFFER_FLAG_KEY_FRAME : 0;
    }
    return 0;
  }
}
