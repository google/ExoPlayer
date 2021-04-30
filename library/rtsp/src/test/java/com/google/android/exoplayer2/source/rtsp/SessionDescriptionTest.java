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

import static com.google.android.exoplayer2.source.rtsp.MediaDescription.MEDIA_TYPE_AUDIO;
import static com.google.android.exoplayer2.source.rtsp.MediaDescription.MEDIA_TYPE_VIDEO;
import static com.google.android.exoplayer2.source.rtsp.MediaDescription.RTP_AVP_PROFILE;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_CONTROL;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_FMTP;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_RANGE;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_RTPMAP;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_TOOL;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_TYPE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SessionDescription}. */
@RunWith(AndroidJUnit4.class)
public class SessionDescriptionTest {

  @Test
  public void parse_sdpString_succeeds() throws Exception {
    String testMediaSdpInfo =
        "v=0\r\n"
            + "o=MNobody 2890844526 2890842807 IN IP4 192.0.2.46\r\n"
            + "s=SDP Seminar\r\n"
            + "i=A Seminar on the session description protocol\r\n"
            + "u=http://www.example.com/lectures/sdp.ps\r\n"
            + "e=seminar@example.com (Seminar Management)\r\n"
            + "c=IN IP4 0.0.0.0\r\n"
            + "a=control:*\r\n"
            + "t=2873397496 2873404696\r\n"
            + "m=audio 3456 RTP/AVP 0\r\n"
            + "a=control:audio\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n"
            + "a=3GPP-Adaption-Support:1\r\n"
            + "m=video 2232 RTP/AVP 31\r\n"
            + "a=control:video\r\n"
            + "a=rtpmap:31 H261/90000\r\n";

    SessionDescription sessionDescription = SessionDescriptionParser.parse(testMediaSdpInfo);

    SessionDescription expectedSession =
        new SessionDescription.Builder()
            .setOrigin("MNobody 2890844526 2890842807 IN IP4 192.0.2.46")
            .setSessionName("SDP Seminar")
            .setSessionInfo("A Seminar on the session description protocol")
            .setUri(Uri.parse("http://www.example.com/lectures/sdp.ps"))
            .setEmailAddress("seminar@example.com (Seminar Management)")
            .setConnection("IN IP4 0.0.0.0")
            .setTiming("2873397496 2873404696")
            .addAttribute(ATTR_CONTROL, "*")
            .addMediaDescription(
                new MediaDescription.Builder(MEDIA_TYPE_AUDIO, 3456, RTP_AVP_PROFILE, 0)
                    .addAttribute(ATTR_CONTROL, "audio")
                    .addAttribute(ATTR_RTPMAP, "0 PCMU/8000")
                    .addAttribute("3GPP-Adaption-Support", "1")
                    .build())
            .addMediaDescription(
                new MediaDescription.Builder(MEDIA_TYPE_VIDEO, 2232, RTP_AVP_PROFILE, 31)
                    .addAttribute(ATTR_CONTROL, "video")
                    .addAttribute(ATTR_RTPMAP, "31 H261/90000")
                    .build())
            .build();

    assertThat(sessionDescription).isEqualTo(expectedSession);
  }

  @Test
  public void parse_sdpString2_succeeds() throws Exception {
    String testMediaSdpInfo =
        "v=0\r\n"
            + "o=- 1600785369059721 1 IN IP4 192.168.2.176\r\n"
            + "s=video+audio, streamed by ExoPlayer\r\n"
            + "i=test.mkv\r\n"
            + "t=0 0\r\n"
            + "a=tool:ExoPlayer\r\n"
            + "a=type:broadcast\r\n"
            + "a=control:*\r\n"
            + "a=range:npt=0-30.102\r\n"
            + "m=video 0 RTP/AVP 96\r\n"
            + "c=IN IP4 0.0.0.0\r\n"
            + "b=AS:500\r\n"
            + "a=rtpmap:96 H264/90000\r\n"
            + "a=fmtp:96"
            + " packetization-mode=1;profile-level-id=64001F;sprop-parameter-sets=Z2QAH6zZQPARabIAAAMACAAAAwGcHjBjLA==,aOvjyyLAAA==\r\n"
            + "a=control:track1\r\n"
            + "m=audio 0 RTP/AVP 97\r\n"
            + "c=IN IP4 0.0.0.0\r\n"
            + "b=AS:96\r\n"
            + "a=rtpmap:97 MPEG4-GENERIC/44100\r\n"
            + "a=fmtp:97"
            + " streamtype=5;profile-level-id=1;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=1208\r\n"
            + "a=control:track2\r\n";

    SessionDescription sessionDescription = SessionDescriptionParser.parse(testMediaSdpInfo);

    SessionDescription expectedSession =
        new SessionDescription.Builder()
            .setOrigin("- 1600785369059721 1 IN IP4 192.168.2.176")
            .setSessionName("video+audio, streamed by ExoPlayer")
            .setSessionInfo("test.mkv")
            .setTiming("0 0")
            .addAttribute(ATTR_TOOL, "ExoPlayer")
            .addAttribute(ATTR_TYPE, "broadcast")
            .addAttribute(ATTR_CONTROL, "*")
            .addAttribute(ATTR_RANGE, "npt=0-30.102")
            .addMediaDescription(
                new MediaDescription.Builder(MEDIA_TYPE_VIDEO, 0, RTP_AVP_PROFILE, 96)
                    .setConnection("IN IP4 0.0.0.0")
                    .setBitrate(500_000)
                    .addAttribute(ATTR_RTPMAP, "96 H264/90000")
                    .addAttribute(
                        ATTR_FMTP,
                        "96 packetization-mode=1;profile-level-id=64001F;sprop-parameter-sets=Z2QAH6zZQPARabIAAAMACAAAAwGcHjBjLA==,aOvjyyLAAA==")
                    .addAttribute(ATTR_CONTROL, "track1")
                    .build())
            .addMediaDescription(
                new MediaDescription.Builder(MEDIA_TYPE_AUDIO, 0, RTP_AVP_PROFILE, 97)
                    .setConnection("IN IP4 0.0.0.0")
                    .setBitrate(96_000)
                    .addAttribute(ATTR_RTPMAP, "97 MPEG4-GENERIC/44100")
                    .addAttribute(
                        ATTR_FMTP,
                        "97 streamtype=5;profile-level-id=1;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=1208")
                    .addAttribute(ATTR_CONTROL, "track2")
                    .build())
            .build();

    assertThat(sessionDescription).isEqualTo(expectedSession);
  }

  @Test
  public void buildMediaDescription_withInvalidRtpmapAttribute_throwsIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new MediaDescription.Builder(
                    MEDIA_TYPE_AUDIO, /* port= */ 0, RTP_AVP_PROFILE, /* payloadType= */ 97)
                .addAttribute(ATTR_RTPMAP, "AF AC3/44100")
                .build());
  }

  @Test
  public void buildMediaDescription_withInvalidRtpmapAttribute2_throwsIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new MediaDescription.Builder(
                    MEDIA_TYPE_AUDIO, /* port= */ 0, RTP_AVP_PROFILE, /* payloadType= */ 97)
                .addAttribute(ATTR_RTPMAP, "97 AC3/441A0")
                .build());
  }
}
