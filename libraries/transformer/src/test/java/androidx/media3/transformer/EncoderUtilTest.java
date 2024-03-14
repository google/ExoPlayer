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

package androidx.media3.transformer;

import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static com.google.common.truth.Truth.assertThat;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Size;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodec;
import org.robolectric.shadows.ShadowMediaCodecList;

/**
 * Unit test for {@link EncoderUtil}.
 *
 * <p>See {@link androidx.media3.exoplayer.mediacodec.MediaCodecUtil#maxH264DecodableFrameSize()}
 * for information on how MediaCodec determines frame sizes.
 */
@RunWith(AndroidJUnit4.class)
public class EncoderUtilTest {
  @Before
  public void setUp() {
    MediaFormat avcFormat = new MediaFormat();
    avcFormat.setString(MediaFormat.KEY_MIME, VIDEO_H264);
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

  @After
  public void tearDown() {
    ShadowMediaCodecList.reset();
    ShadowMediaCodec.clearCodecs();
    EncoderUtil.clearCachedEncoders();
  }

  @Test
  public void getSupportedResolution_withSupportedResolution_succeeds() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(VIDEO_H264);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(
            encoderInfo, VIDEO_H264, /* width= */ 1920, /* height= */ 1080);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1920);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1080);
  }

  @Test
  public void getSupportedResolution_withUnalignedSize_findsMostCloselySupportedResolution() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(VIDEO_H264);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(
            encoderInfo, VIDEO_H264, /* width= */ 1919, /* height= */ 1081);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1920);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1080);
  }

  @Test
  public void getSupportedResolution_findsThreeQuartersOfTheOriginalSize() {
    // The supported resolution will try to match the aspect ratio where possible.
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(VIDEO_H264);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(
            encoderInfo, VIDEO_H264, /* width= */ 1920, /* height= */ 1920);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1440);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1440);
  }

  @Test
  public void getSupportedResolution_findsTwoThirdsOfTheOriginalSize() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(VIDEO_H264);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(
            encoderInfo, VIDEO_H264, /* width= */ 2880, /* height= */ 1620);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1920);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1080);
  }

  @Test
  public void getSupportedResolution_findsHalfOfTheOriginalSize() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(VIDEO_H264);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(
            encoderInfo, VIDEO_H264, /* width= */ 2160, /* height= */ 3840);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1080);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1920);
  }

  @Test
  public void getSupportedResolution_findsOneQuarterOfTheOriginalSize() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(VIDEO_H264);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(
            encoderInfo, VIDEO_H264, /* width= */ 7680, /* height= */ 4320);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1920);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1080);
  }

  @Test
  public void getSupportedResolution_requestedReallyLarge_matchesAspectRatio() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(VIDEO_H264);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(
            encoderInfo, VIDEO_H264, /* width= */ 7500, /* height= */ 5000);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(
            (double) closestSupportedResolution.getWidth() / closestSupportedResolution.getHeight())
        .isEqualTo(7500.0 / 5000);
  }

  /**
   * @see EncoderUtil#getSupportedEncodersForHdrEditing(String, ColorInfo)
   */
  @Config(sdk = {30, 31})
  @Test
  public void getSupportedEncodersForHdrEditing_returnsEmptyList() {
    // This test is run on API 30 and 31 because the tested logic differentiates at API 31.
    // getSupportedEncodersForHdrEditing returns an empty list for API < 31. It returns an empty
    // list for API >= 31 as well, because currently it is not possible to make ShadowMediaCodec
    // support HDR.
    assertThat(
            EncoderUtil.getSupportedEncodersForHdrEditing(
                VIDEO_H264,
                new ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_FULL)
                    .setColorTransfer(C.COLOR_TRANSFER_HLG)
                    .build()))
        .isEmpty();
  }
}
