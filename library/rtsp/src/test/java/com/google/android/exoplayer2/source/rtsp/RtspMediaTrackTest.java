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
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_RTPMAP;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AacUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtspMediaTrack}. */
@RunWith(AndroidJUnit4.class)
public class RtspMediaTrackTest {

  @Test
  public void generatePayloadFormat_withH264MediaDescription_succeeds() throws Exception {
    MediaDescription mediaDescription =
        new MediaDescription.Builder(MEDIA_TYPE_VIDEO, 0, RTP_AVP_PROFILE, 96)
            .setConnection("IN IP4 0.0.0.0")
            .setBitrate(500_000)
            .addAttribute(ATTR_RTPMAP, "96 H264/90000")
            .addAttribute(
                ATTR_FMTP,
                "96 packetization-mode=1;profile-level-id=64001F;sprop-parameter-sets=Z2QAH6zZQPARabIAAAMACAAAAwGcHjBjLA==,aOvjyyLA")
            .addAttribute(ATTR_CONTROL, "track1")
            .build();

    RtpPayloadFormat format = RtspMediaTrack.generatePayloadFormat(mediaDescription);
    RtpPayloadFormat expectedFormat =
        new RtpPayloadFormat(
            new Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H264)
                .setAverageBitrate(500_000)
                .setPixelWidthHeightRatio(1.0f)
                .setHeight(544)
                .setWidth(960)
                .setCodecs("avc1.64001F")
                .setInitializationData(
                    ImmutableList.of(
                        new byte[] {
                          0, 0, 0, 1, 103, 100, 0, 31, -84, -39, 64, -16, 17, 105, -78, 0, 0, 3, 0,
                          8, 0, 0, 3, 1, -100, 30, 48, 99, 44
                        },
                        new byte[] {0, 0, 0, 1, 104, -21, -29, -53, 34, -64}))
                .build(),
            /* rtpPayloadType= */ 96,
            /* clockRate= */ 90_000,
            /* fmtpParameters= */ ImmutableMap.of(
                "packetization-mode", "1",
                "profile-level-id", "64001F",
                "sprop-parameter-sets", "Z2QAH6zZQPARabIAAAMACAAAAwGcHjBjLA==,aOvjyyLA"));

    assertThat(format).isEqualTo(expectedFormat);
  }

  @Test
  public void generatePayloadFormat_withAacMediaDescription_succeeds() throws Exception {
    MediaDescription mediaDescription =
        new MediaDescription.Builder(MEDIA_TYPE_AUDIO, 0, RTP_AVP_PROFILE, 97)
            .setConnection("IN IP4 0.0.0.0")
            .setBitrate(96_000)
            .addAttribute(ATTR_RTPMAP, "97 MPEG4-GENERIC/44100")
            .addAttribute(
                ATTR_FMTP,
                "97 streamtype=5; profile-level-id=1; mode=AAC-hbr; sizelength=13; indexlength=3;"
                    + " indexdeltalength=3; config=1208")
            .addAttribute(ATTR_CONTROL, "track2")
            .build();

    RtpPayloadFormat format = RtspMediaTrack.generatePayloadFormat(mediaDescription);
    RtpPayloadFormat expectedFormat =
        new RtpPayloadFormat(
            new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setChannelCount(1)
                .setSampleRate(44100)
                .setAverageBitrate(96_000)
                .setCodecs("mp4a.40.1")
                .setInitializationData(
                    ImmutableList.of(
                        AacUtil.buildAacLcAudioSpecificConfig(
                            /* sampleRate= */ 44_100, /* channelCount= */ 1)))
                .build(),
            /* rtpPayloadType= */ 97,
            /* clockRate= */ 44_100,
            /* fmtpParameters= */ new ImmutableMap.Builder<String, String>()
                .put("streamtype", "5")
                .put("profile-level-id", "1")
                .put("mode", "AAC-hbr")
                .put("sizelength", "13")
                .put("indexlength", "3")
                .put("indexdeltalength", "3")
                .put("config", "1208")
                .build());

    assertThat(format).isEqualTo(expectedFormat);
  }

  @Test
  public void generatePayloadFormat_withAc3MediaDescriptionWithDefaultChannelCount_succeeds()
      throws Exception {
    MediaDescription mediaDescription =
        new MediaDescription.Builder(MEDIA_TYPE_AUDIO, 0, RTP_AVP_PROFILE, 97)
            .setConnection("IN IP4 0.0.0.0")
            .setBitrate(48_000)
            .addAttribute(ATTR_RTPMAP, "97 AC3/48000")
            .addAttribute(ATTR_CONTROL, "track2")
            .build();

    RtpPayloadFormat format = RtspMediaTrack.generatePayloadFormat(mediaDescription);
    RtpPayloadFormat expectedFormat =
        new RtpPayloadFormat(
            new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AC3)
                .setChannelCount(6)
                .setSampleRate(48000)
                .setAverageBitrate(48_000)
                .build(),
            /* rtpPayloadType= */ 97,
            /* clockRate= */ 48000,
            /* fmtpParameters= */ ImmutableMap.of());

    assertThat(format).isEqualTo(expectedFormat);
  }

  @Test
  public void generatePayloadFormat_withAc3MediaDescription_succeeds() throws Exception {
    MediaDescription mediaDescription =
        new MediaDescription.Builder(MEDIA_TYPE_AUDIO, 0, RTP_AVP_PROFILE, 97)
            .setConnection("IN IP4 0.0.0.0")
            .setBitrate(48_000)
            .addAttribute(ATTR_RTPMAP, "97 AC3/48000/2")
            .addAttribute(ATTR_CONTROL, "track2")
            .build();

    RtpPayloadFormat format = RtspMediaTrack.generatePayloadFormat(mediaDescription);
    RtpPayloadFormat expectedFormat =
        new RtpPayloadFormat(
            new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AC3)
                .setChannelCount(2)
                .setSampleRate(48000)
                .setAverageBitrate(48_000)
                .build(),
            /* rtpPayloadType= */ 97,
            /* clockRate= */ 48000,
            /* fmtpParameters= */ ImmutableMap.of());

    assertThat(format).isEqualTo(expectedFormat);
  }

  @Test
  public void
      generatePayloadFormat_withAacMediaDescriptionMissingFmtpAttribute_throwsIllegalArgumentException() {
    MediaDescription mediaDescription =
        new MediaDescription.Builder(MEDIA_TYPE_AUDIO, 0, RTP_AVP_PROFILE, 97)
            .setConnection("IN IP4 0.0.0.0")
            .setBitrate(96_000)
            .addAttribute(ATTR_RTPMAP, "97 MPEG4-GENERIC/44100")
            .addAttribute(ATTR_CONTROL, "track2")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> RtspMediaTrack.generatePayloadFormat(mediaDescription));
  }

  @Test
  public void
      generatePayloadFormat_withMediaDescriptionMissingProfileLevel_throwsIllegalArgumentException() {
    MediaDescription mediaDescription =
        new MediaDescription.Builder(MEDIA_TYPE_AUDIO, 0, RTP_AVP_PROFILE, 97)
            .setConnection("IN IP4 0.0.0.0")
            .setBitrate(96_000)
            .addAttribute(ATTR_RTPMAP, "97 MPEG4-GENERIC/44100")
            .addAttribute(
                ATTR_FMTP,
                "97 streamtype=5;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=1208")
            .addAttribute(ATTR_CONTROL, "track2")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> RtspMediaTrack.generatePayloadFormat(mediaDescription));
  }

  @Test
  public void
      generatePayloadFormat_withH264MediaDescriptionMissingFmtpAttribute_throwsIllegalArgumentException() {
    MediaDescription mediaDescription =
        new MediaDescription.Builder(MEDIA_TYPE_VIDEO, 0, RTP_AVP_PROFILE, 96)
            .setConnection("IN IP4 0.0.0.0")
            .setBitrate(500_000)
            .addAttribute(ATTR_RTPMAP, "96 H264/90000")
            .addAttribute(ATTR_CONTROL, "track1")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> RtspMediaTrack.generatePayloadFormat(mediaDescription));
  }

  @Test
  public void
      generatePayloadFormat_withH264MediaDescriptionMissingProfileLevel_throwsIllegalArgumentException() {
    MediaDescription mediaDescription =
        new MediaDescription.Builder(MEDIA_TYPE_VIDEO, 0, RTP_AVP_PROFILE, 96)
            .setConnection("IN IP4 0.0.0.0")
            .setBitrate(500_000)
            .addAttribute(ATTR_RTPMAP, "96 H264/90000")
            .addAttribute(
                ATTR_FMTP,
                "96 packetization-mode=1;sprop-parameter-sets=Z2QAH6zZQPARabIAAAMACAAAAwGcHjBjLA==,aOvjyyLA")
            .addAttribute(ATTR_CONTROL, "track1")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> RtspMediaTrack.generatePayloadFormat(mediaDescription));
  }

  @Test
  public void
      generatePayloadFormat_withH264MediaDescriptionMissingSpropParameter_throwsIllegalArgumentException() {
    MediaDescription mediaDescription =
        new MediaDescription.Builder(MEDIA_TYPE_VIDEO, 0, RTP_AVP_PROFILE, 96)
            .setConnection("IN IP4 0.0.0.0")
            .setBitrate(500_000)
            .addAttribute(ATTR_RTPMAP, "96 H264/90000")
            .addAttribute(ATTR_FMTP, "96 packetization-mode=1;profile-level-id=64001F")
            .addAttribute(ATTR_CONTROL, "track1")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> RtspMediaTrack.generatePayloadFormat(mediaDescription));
  }
}
