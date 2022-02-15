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
import androidx.media3.common.Format;
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
 * Parses a VP8 byte stream carried on RTP packets, and extracts VP8 individual video frames as
 * defined in RFC7741.
 */
/* package */ final class RtpVP8Reader implements RtpPayloadReader {
  private static final String TAG = "RtpVP8Reader";

  private static final long MEDIA_CLOCK_FREQUENCY = 90_000;

  private final RtpPayloadFormat payloadFormat;

  private @MonotonicNonNull TrackOutput trackOutput;
  @C.BufferFlags private int bufferFlags;

  private long firstReceivedTimestamp;
  private int previousSequenceNumber;
  /** The combined size of a sample that is fragmented into multiple RTP packets. */
  private int fragmentedSampleSizeBytes;
  private long startTimeOffsetUs;
  private boolean gotFirstPacketOfVP8Frame;
  private boolean isKeyFrame;
  private boolean isOutputFormatSet;

  /** Creates an instance. */
  public RtpVP8Reader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
    previousSequenceNumber = C.INDEX_UNSET;
    fragmentedSampleSizeBytes = 0;
    gotFirstPacketOfVP8Frame = false;
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

    if (parseVP8Descriptor(data, sequenceNumber)) {
      //  VP8 Payload Header, RFC7741 Section 4.3
      //  0 1 2 3 4 5 6 7
      //  +-+-+-+-+-+-+-+-+
      //  |Size0|H| VER |P|
      //  +-+-+-+-+-+-+-+-+
      // P: Inverse key frame flag.
      if (fragmentedSampleSizeBytes == 0 && gotFirstPacketOfVP8Frame) {
        isKeyFrame = (data.peekUnsignedByte() & 0x01) == 0;
      }
      if (!isOutputFormatSet) {
        // Parsing frame data to get width and height, RFC6386 Section 9.1
        int currPosition = data.getPosition();
        data.setPosition(currPosition + 6);
        int width = data.readLittleEndianUnsignedShort() & 0x3fff;
        int height = data.readLittleEndianUnsignedShort() & 0x3fff;
        data.setPosition(currPosition);

        if (width != payloadFormat.format.width || height != payloadFormat.format.height) {
          Format.Builder formatBuilder = new Format.Builder();
          if (payloadFormat.format.bitrate > 0) {
            formatBuilder.setAverageBitrate(payloadFormat.format.bitrate);
          }
          formatBuilder.setSampleMimeType(payloadFormat.format.sampleMimeType);
          formatBuilder.setWidth(width).setHeight(height);
          trackOutput.format(formatBuilder.build());
        }
        isOutputFormatSet = true;
      }

      int fragmentSize = data.bytesLeft();
      // Write the video sample
      trackOutput.sampleData(data, fragmentSize);
      fragmentedSampleSizeBytes += fragmentSize;

      if (rtpMarker) {
        if (firstReceivedTimestamp == C.TIME_UNSET) {
          firstReceivedTimestamp = timestamp;
        }
        bufferFlags = isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0;
        long timeUs = toSampleUs(startTimeOffsetUs, timestamp, firstReceivedTimestamp);
        trackOutput.sampleMetadata(
            timeUs,
            bufferFlags,
            fragmentedSampleSizeBytes,
            /* offset= */ 0,
            /* encryptionData= */ null);
        fragmentedSampleSizeBytes = 0;
        gotFirstPacketOfVP8Frame = false;
      }
      previousSequenceNumber = sequenceNumber;
    }
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    fragmentedSampleSizeBytes = 0;
    startTimeOffsetUs = timeUs;
  }

  // Internal methods.
  private boolean parseVP8Descriptor(ParsableByteArray payload, int packetSequenceNumber) {
    // VP8 Payload Descriptor, RFC7741 Section 4.2
    //       0 1 2 3 4 5 6 7
    //       +-+-+-+-+-+-+-+-+
    //       |X|R|N|S|R| PID | (REQUIRED)
    //       +-+-+-+-+-+-+-+-+
    //  X:   |I|L|T|K| RSV   | (OPTIONAL)
    //       +-+-+-+-+-+-+-+-+
    //  I:   |M| PictureID   | (OPTIONAL)
    //       +-+-+-+-+-+-+-+-+
    //  L:   |   TL0PICIDX   | (OPTIONAL)
    //       +-+-+-+-+-+-+-+-+
    //  T/K: |TID|Y| KEYIDX  | (OPTIONAL)
    //       +-+-+-+-+-+-+-+-+

    int header = payload.readUnsignedByte();
    if (!gotFirstPacketOfVP8Frame) {
      // For start of VP8 partition S=1 and PID=0 as per RFC7741 Section 4.2
      if ((header & 0x17) != 0x10) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "first payload octet of the RTP packet is not the beginning of a new VP8 "
                    + "partition, Dropping current packet"));
        return false;
      }
      gotFirstPacketOfVP8Frame = true;
    } else {
      // Check that this packet is in the sequence of the previous packet.
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (packetSequenceNumber != expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d."
                    + " Dropping packet.",
                expectedSequenceNumber, packetSequenceNumber));
        return false;
      }
    }

    // Check if optional X header is present
    if ((header & 0x80) != 0) {
      int xHeader = payload.readUnsignedByte();

      // Check if optional I header present
      if ((xHeader & 0x80) != 0) {
        int iHeader = payload.readUnsignedByte();
        if ((iHeader & 0x80) != 0) {
          payload.skipBytes(1);
          Log.i(TAG, "15 bits PictureID");
        } else {
          Log.i(TAG, "7 bits PictureID");
        }
      }

      // Check if optional L header present
      if ((xHeader & 0x40) != 0) {
        payload.skipBytes(1);
      }

      // Check if optional T or K header(s) present
      if ((xHeader & 0x20) != 0 || (xHeader & 0x10) != 0) {
        payload.skipBytes(1);
      }
    }
    return true;
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
