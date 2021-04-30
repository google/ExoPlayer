/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;

/**
 * Represents an RTSP track's timing info, included as {@link RtspHeaders#RTP_INFO} in an RTSP PLAY
 * response (RFC2326 Section 12.33).
 *
 * <p>The fields {@link #rtpTimestamp} and {@link #sequenceNumber} will not both be {@code null}.
 */
/* package */ final class RtspTrackTiming {

  /**
   * Parses the RTP-Info header into a list of {@link RtspTrackTiming RtspTrackTimings}.
   *
   * <p>The syntax of the RTP-Info (RFC2326 Section 12.33):
   *
   * <pre>
   *   RTP-Info        = "RTP-Info" ":" 1#stream-url 1*parameter
   *   stream-url      = "url" "=" url
   *   parameter       = ";" "seq" "=" 1*DIGIT
   *                   | ";" "rtptime" "=" 1*DIGIT
   * </pre>
   *
   * <p>Examples from RFC2326:
   *
   * <pre>
   *   RTP-Info:url=rtsp://foo.com/bar.file; seq=232433;rtptime=972948234
   *   RTP-Info:url=rtsp://foo.com/bar.avi/streamid=0;seq=45102,
   *            url=rtsp://foo.com/bar.avi/streamid=1;seq=30211
   * </pre>
   *
   * @param rtpInfoString The value of the RTP-Info header, with header name (RTP-Info) removed.
   * @return A list of parsed {@link RtspTrackTiming}.
   * @throws ParserException If parsing failed.
   */
  public static ImmutableList<RtspTrackTiming> parseTrackTiming(String rtpInfoString)
      throws ParserException {

    ImmutableList.Builder<RtspTrackTiming> listBuilder = new ImmutableList.Builder<>();
    for (String perTrackTimingString : Util.split(rtpInfoString, ",")) {
      long rtpTime = C.TIME_UNSET;
      int sequenceNumber = C.INDEX_UNSET;
      @Nullable Uri uri = null;

      for (String attributePair : Util.split(perTrackTimingString, ";")) {
        try {
          String[] attributes = Util.splitAtFirst(attributePair, "=");
          String attributeName = attributes[0];
          String attributeValue = attributes[1];

          switch (attributeName) {
            case "url":
              uri = Uri.parse(attributeValue);
              break;
            case "seq":
              sequenceNumber = Integer.parseInt(attributeValue);
              break;
            case "rtptime":
              rtpTime = Long.parseLong(attributeValue);
              break;
            default:
              throw new ParserException();
          }
        } catch (Exception e) {
          throw new ParserException(attributePair, e);
        }
      }

      if (uri == null
          || uri.getScheme() == null // Checks if the URI is a URL.
          || (sequenceNumber == C.INDEX_UNSET && rtpTime == C.TIME_UNSET)) {
        throw new ParserException(perTrackTimingString);
      }

      listBuilder.add(new RtspTrackTiming(rtpTime, sequenceNumber, uri));
    }
    return listBuilder.build();
  }

  /** The timestamp of the next RTP packet, {@link C#TIME_UNSET} if not present. */
  public final long rtpTimestamp;
  /** The sequence number of the next RTP packet, {@link C#INDEX_UNSET} if not present. */
  public final int sequenceNumber;
  /** The {@link Uri} that identifies a matching {@link RtspMediaTrack}. */
  public final Uri uri;

  private RtspTrackTiming(long rtpTimestamp, int sequenceNumber, Uri uri) {
    this.rtpTimestamp = rtpTimestamp;
    this.sequenceNumber = sequenceNumber;
    this.uri = uri;
  }
}
