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

import static com.google.common.truth.Truth.assertThat;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Size;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.MimeTypes;
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

  @After
  public void tearDown() {
    ShadowMediaCodecList.reset();
    ShadowMediaCodec.clearCodecs();
    EncoderUtil.clearCachedEncoders();
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
  public void getSupportedResolution_findsThreeQuartersOfTheOriginalSize() {
    // The supported resolution will try to match the aspect ratio where possible.
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
  public void getSupportedResolution_findsTwoThirdsOfTheOriginalSize() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(MIME_TYPE);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(encoderInfo, MIME_TYPE, 2880, 1620);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1920);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1080);
  }

  @Test
  public void getSupportedResolution_findsHalfOfTheOriginalSize() {
    ImmutableList<MediaCodecInfo> supportedEncoders = EncoderUtil.getSupportedEncoders(MIME_TYPE);
    MediaCodecInfo encoderInfo = supportedEncoders.get(0);

    @Nullable
    Size closestSupportedResolution =
        EncoderUtil.getSupportedResolution(encoderInfo, MIME_TYPE, 2160, 3840);

    assertThat(closestSupportedResolution).isNotNull();
    assertThat(closestSupportedResolution.getWidth()).isEqualTo(1080);
    assertThat(closestSupportedResolution.getHeight()).isEqualTo(1920);
  }

  /**
   * @see EncoderUtil#getSupportedEncoderNamesForHdrEditing(String, ColorInfo)
   */
  @Config(sdk = {30, 31})
  @Test
  public void getSupportedEncoderNamesForHdrEditing_returnsEmptyList() {
    // This test is run on 30 and 31 because the tested logic differentiate at API31.
    // getSupportedEncoderNamesForHdrEditing returns an empty list for API < 31. It returns an empty
    // list for API >= 31 as well, because currently it is not possible to make ShadowMediaCodec
    // support HDR.
    assertThat(
            EncoderUtil.getSupportedEncoderNamesForHdrEditing(
                MIME_TYPE,
                new ColorInfo(
                    C.COLOR_SPACE_BT2020,
                    C.COLOR_RANGE_FULL,
                    C.COLOR_TRANSFER_HLG,
                    /* hdrStaticInfo= */ null)))
        .isEmpty();
  }
}
