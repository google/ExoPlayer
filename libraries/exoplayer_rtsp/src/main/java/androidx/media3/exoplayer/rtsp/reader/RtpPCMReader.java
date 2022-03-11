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

import androidx.media3.common.C;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses byte stream carried on RTP packets, and extracts PCM frames. Refer to RFC3551 for more
 * details.
 */
/* package */ public final class RtpPCMReader implements RtpPayloadReader {

  private final RtpPayloadFormat payloadFormat;
  private @MonotonicNonNull TrackOutput trackOutput;
  private long firstReceivedTimestamp;
  private long startTimeOffsetUs;

  public RtpPCMReader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_AUDIO);
    trackOutput.format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {
    checkState(firstReceivedTimestamp == C.TIME_UNSET);
    firstReceivedTimestamp = timestamp;
  }

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker) {
    long sampleTimeUs =
        toSampleUs(startTimeOffsetUs, timestamp, firstReceivedTimestamp, payloadFormat.clockRate);
    int size = data.bytesLeft();
    trackOutput.sampleData(data, size);
    trackOutput
        .sampleMetadata(
            /* timeUs= */ sampleTimeUs,
            /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
            /* size= */ size,
            /* offset= */ 0,
            /* cryptoData= */ null);
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    startTimeOffsetUs = timeUs;
  }

  /**
   * Returns the correct sample time from RTP timestamp, accounting for the given sampling rate.
   */
  private static long toSampleUs(
      long startTimeOffsetUs, long rtpTimestamp, long firstReceivedRtpTimestamp, int sampleRate) {
    return startTimeOffsetUs
        + Util.scaleLargeTimestamp(
        rtpTimestamp - firstReceivedRtpTimestamp,
        /* multiplier= */ C.MICROS_PER_SECOND,
        /* divisor= */ sampleRate);
  }
}
