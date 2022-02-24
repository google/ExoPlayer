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
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Joiner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Utility methods for RTSP tests. */
/* package */ final class RtspTestUtils {

  private static final String TEST_BASE_URI = "rtsp://localhost:%d/test";
  private static final String TEST_BASE_URI_WITH_USER_INFO = "rtsp://%s:%s@localhost:%d/test";
  private static final String RTP_TIME_FORMAT = "url=rtsp://localhost/test/%s;seq=%d;rtptime=%d";

  /** RTSP error Method Not Allowed (RFC2326 Section 7.1.1). */
  public static final RtspResponse RTSP_ERROR_METHOD_NOT_ALLOWED =
      new RtspResponse(454, RtspHeaders.EMPTY);

  /**
   * Parses and returns an {@link RtpPacketStreamDump} from the file identified by {@code filepath}.
   *
   * <p>See {@link RtpPacketStreamDump#parse} for details on the dump file format.
   */
  public static RtpPacketStreamDump readRtpPacketStreamDump(String filepath) throws IOException {
    return RtpPacketStreamDump.parse(
        TestUtil.getString(ApplicationProvider.getApplicationContext(), filepath));
  }

  /** Returns an {@link RtspResponse} with a SDP message body. */
  public static RtspResponse newDescribeResponseWithSdpMessage(
      String sessionDescription, List<RtpPacketStreamDump> rtpPacketStreamDumps, Uri requestedUri) {

    StringBuilder sdpMessageBuilder = new StringBuilder(sessionDescription);
    for (RtpPacketStreamDump rtpPacketStreamDump : rtpPacketStreamDumps) {
      sdpMessageBuilder.append(rtpPacketStreamDump.mediaDescription).append("\r\n");
    }
    String sdpMessage = sdpMessageBuilder.toString();

    return new RtspResponse(
        200,
        new RtspHeaders.Builder()
            .add(RtspHeaders.CONTENT_BASE, requestedUri.toString())
            .add(
                RtspHeaders.CONTENT_LENGTH,
                String.valueOf(sdpMessage.getBytes(RtspMessageChannel.CHARSET).length))
            .build(),
        /* messageBody= */ sdpMessage);
  }

  /** Returns the test RTSP {@link Uri}. */
  public static Uri getTestUri(int serverRtspPortNumber) {
    return Uri.parse(Util.formatInvariant(TEST_BASE_URI, serverRtspPortNumber));
  }

  /** Returns the test RTSP {@link Uri} with user info. */
  public static Uri getTestUriWithUserInfo(
      String username, String password, int serverRtspPortNumber) {
    return Uri.parse(
        Util.formatInvariant(
            TEST_BASE_URI_WITH_USER_INFO, username, password, serverRtspPortNumber));
  }

  public static String getRtpInfoForDumps(List<RtpPacketStreamDump> rtpPacketStreamDumps) {
    ArrayList<String> rtpInfos = new ArrayList<>(rtpPacketStreamDumps.size());
    for (RtpPacketStreamDump rtpPacketStreamDump : rtpPacketStreamDumps) {
      rtpInfos.add(
          Util.formatInvariant(
              RTP_TIME_FORMAT,
              rtpPacketStreamDump.trackName,
              rtpPacketStreamDump.firstSequenceNumber,
              rtpPacketStreamDump.firstTimestamp));
    }
    return Joiner.on(",").join(rtpInfos);
  }

  private RtspTestUtils() {}
}
