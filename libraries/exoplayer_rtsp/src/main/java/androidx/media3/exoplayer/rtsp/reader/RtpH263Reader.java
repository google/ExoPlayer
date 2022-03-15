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

import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.rtsp.RtpPacket;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses a H263 byte stream carried on RTP packets, and extracts H263 individual video frames as
 * defined in RFC4629.
 */
/* package */ final class RtpH263Reader implements RtpPayloadReader {
  private static final String TAG = "RtpH263Reader";

  private static final long MEDIA_CLOCK_FREQUENCY = 90_000;

  /** VOP unit type. */
  private static final int I_VOP = 0;

  private final RtpPayloadFormat payloadFormat;

  private @MonotonicNonNull TrackOutput trackOutput;

  private long firstReceivedTimestamp;
  private int previousSequenceNumber;
  /** The combined size of a sample that is fragmented into multiple RTP packets. */
  private int fragmentedSampleSizeBytes;
  private static int width;
  private static int height;
  private static boolean isKeyFrame;
  private boolean isOutputFormatSet;
  private long startTimeOffsetUs;

  /** Creates an instance. */
  public RtpH263Reader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
    previousSequenceNumber = C.INDEX_UNSET;
    fragmentedSampleSizeBytes = 0;
    isKeyFrame = false;
    isOutputFormatSet = false;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);
    castNonNull(trackOutput).format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {}

  @Override
  public void consume(ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker)
      throws ParserException {
    checkStateNotNull(trackOutput);

    // H263 Header Payload Header, RFC4629 Section 5.1.
    //    0                   1
    //    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //    |   RR    |P|V|   PLEN    |PEBIT|
    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    int currPosition = data.getPosition();
    int header = data.readUnsignedShort();
    boolean pBit = ((header & 0x400) == 0x400);

    // Check if optional Video Redundancy Coding or PLEN or PEBIT is present, RFC4629 Section 5.1.
    if ((header & 0x200) != 0 || (header & 0x1f8) != 0 || (header & 0x7) != 0) {
      Log.w(TAG, "Packet discarded due to (VRC != 0) or (PLEN != 0) or (PEBIT != 0)");
      return;
    }
    int startCodePayload = data.peekUnsignedByte() & 0xfc;
    if (pBit == true) {
      if (startCodePayload < 128) {
        Log.w(TAG, "Picture start Code (PSC) missing, Dropping packet.");
        return;
      } else {
        // Setting first two bytes of the start code. Refer RFC4629 Section 5.1.
        data.getData()[currPosition] = 0;
        data.getData()[currPosition + 1] = 0;
        data.setPosition(currPosition);
      }
    } else {
      // Check that this packet is in the sequence of the previous packet.
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (sequenceNumber != expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d."
                    + " Dropping packet.",
                expectedSequenceNumber, sequenceNumber));
        return;
      }
    }

    if (fragmentedSampleSizeBytes == 0) {
      getBufferFlagsAndResolutionFromVop(data, isOutputFormatSet);
      if (!isOutputFormatSet && isKeyFrame == true) {
        if (width != payloadFormat.format.width || height != payloadFormat.format.height) {
          trackOutput.format(
              payloadFormat.format.buildUpon().setWidth(width).setHeight(height).build());
        }
        isOutputFormatSet = true;
      }
    }
    int fragmentSize = data.bytesLeft();
    // Write the video sample.
    trackOutput.sampleData(data, fragmentSize);
    fragmentedSampleSizeBytes += fragmentSize;

    if (rtpMarker) {
      if (firstReceivedTimestamp == C.TIME_UNSET) {
        firstReceivedTimestamp = timestamp;
      }
      long timeUs = toSampleUs(startTimeOffsetUs, timestamp, firstReceivedTimestamp);
      trackOutput.sampleMetadata(
          timeUs,
          isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0,
          fragmentedSampleSizeBytes,
          /* offset= */ 0,
          /* encryptionData= */ null);
      fragmentedSampleSizeBytes = 0;
      isKeyFrame = false;
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
   * Parses VOP Coding type and resolution.
   */
  private void getBufferFlagsAndResolutionFromVop(ParsableByteArray data, boolean gotResolution) {
    // Search for SHORT_VIDEO_START_MARKER (0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0 0 0 0 0).
    int currPosition = data.getPosition();
    if (data.readUnsignedInt() >> 10 == 0x20) {
      int header = data.peekUnsignedByte();
      int vopType = ((header >> 1) & 0x01);
      if (!gotResolution && vopType == I_VOP) {
        int sourceFormat  = ((header >> 2) & 0x07);
        if (sourceFormat == 1) {
          width = 128;
          height = 96;
        } else {
          width = (short) (176 << (sourceFormat - 2));
          height = (short) (144 << (sourceFormat - 2));
        }
      }
      data.setPosition(currPosition);
      isKeyFrame = (vopType == I_VOP ? true : false);
      return;
    }
    data.setPosition(currPosition);
    isKeyFrame = false;
  }

  private static long toSampleUs(
      long startTimeOffsetUs, long rtpTimestamp, long firstReceivedRtpTimestamp) {
    return startTimeOffsetUs
        + Util.scaleLargeTimestamp(
            (rtpTimestamp - firstReceivedRtpTimestamp),
            /* multiplier= */ C.MICROS_PER_SECOND,
            /* divisor= */ MEDIA_CLOCK_FREQUENCY);
  }
}
