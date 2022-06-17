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

package com.google.android.exoplayer2.transformer;

import static com.google.common.truth.Truth.assertThat;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Size;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodecList;

/** Unit test for {@link EncoderUtil}. */
@RunWith(AndroidJUnit4.class)
public class EncoderUtilTest {
  private static final String MIME_TYPE = MimeTypes.VIDEO_H264;

  @Before
  public void setUp() {
    MediaFormat avcFormat = new MediaFormat();
    avcFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_AVC);
    MediaCodecInfo.CodecProfileLevel profileLevel = new MediaCodecInfo.CodecProfileLevel();
    profileLevel.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
    // Using Level4 gives us 8192 16x16 blocks. If using width 1920 uses 120 blocks, 8192 / 120 = 68
    // blocks will be left for encoding height 1088.
    profileLevel.level = MediaCodecInfo.CodecProfileLevel.AVCLevel4;

    ShadowMediaCodecList.addCodec(
        MediaCodecInfoBuilder.newBuilder()
            .setName("test.transformer.avc.encoder")
            .setIsEncoder(true)
            .setCapabilities(
                MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                    .setMediaFormat(avcFormat)
                    .setIsEncoder(true)
                    .setColorFormats(
                        new int[] {MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible})
                    .setProfileLevels(new MediaCodecInfo.CodecProfileLevel[] {profileLevel})
                    .build())
            .build());
  }

  @Test
  public void getSupportedResolution_withSupportedResolution_succeeds() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(MIME_TYPE);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(encoderInfo, MIME_TYPE, 1920, 1080);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1920);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1080);
  }

  @Test
  public void getSupportedResolution_withUnalignedSize_findsMostCloselySupportedResolution() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(MIME_TYPE);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(encoderInfo, MIME_TYPE, 1919, 1081);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1920);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1080);
  }

  @Test
  public void getSupportedResolution_withWidthTooBig_findsTwoThirdsOfTheOriginalSize() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(MIME_TYPE);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(encoderInfo, MIME_TYPE, 1920, 1920);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1440);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1440);
  }

  @Test
  public void getSupportedResolution_withWidthTooBig2_findsHalfOfTheOriginalSize() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(MIME_TYPE);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(encoderInfo, MIME_TYPE, 3840, 2160);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1920);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1080);
  }
}
