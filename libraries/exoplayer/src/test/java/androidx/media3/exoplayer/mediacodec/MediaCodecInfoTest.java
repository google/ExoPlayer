/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.exoplayer.mediacodec;

import static androidx.media3.common.MimeTypes.AUDIO_AAC;
import static androidx.media3.common.MimeTypes.VIDEO_AV1;
import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_INITIALIZATION_DATA_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_MIME_TYPE_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_VIDEO_RESOLUTION_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_VIDEO_ROTATION_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_FLUSH;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_RECONFIGURATION;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MediaCodecInfo}. */
@RunWith(AndroidJUnit4.class)
public final class MediaCodecInfoTest {

  private static final Format FORMAT_H264_HD =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1024)
          .setHeight(768)
          .setInitializationData(ImmutableList.of(new byte[] {1, 0, 2, 4}))
          .build();
  private static final Format FORMAT_H264_4K =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(3840)
          .setHeight(2160)
          .setInitializationData(ImmutableList.of(new byte[] {3, 8, 4, 0}))
          .build();
  private static final Format FORMAT_AAC_STEREO =
      new Format.Builder()
          .setSampleMimeType(AUDIO_AAC)
          .setChannelCount(2)
          .setSampleRate(44100)
          .setAverageBitrate(2000)
          .setInitializationData(ImmutableList.of(new byte[] {4, 4, 1, 0, 0}))
          .build();
  private static final Format FORMAT_AAC_SURROUND =
      new Format.Builder()
          .setSampleMimeType(AUDIO_AAC)
          .setChannelCount(5)
          .setSampleRate(44100)
          .setAverageBitrate(5000)
          .setInitializationData(ImmutableList.of(new byte[] {4, 4, 1, 0, 0}))
          .build();

  @Test
  public void canReuseCodec_withDifferentMimeType_returnsNo() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ true);

    Format hdAv1Format = FORMAT_H264_HD.buildUpon().setSampleMimeType(VIDEO_AV1).build();
    assertThat(codecInfo.canReuseCodec(FORMAT_H264_HD, hdAv1Format))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_H264_HD,
                hdAv1Format,
                REUSE_RESULT_NO,
                DISCARD_REASON_MIME_TYPE_CHANGED));
  }

  @Test
  public void canReuseCodec_withRotation_returnsNo() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ true);

    Format hdRotatedFormat = FORMAT_H264_HD.buildUpon().setRotationDegrees(90).build();
    assertThat(codecInfo.canReuseCodec(FORMAT_H264_HD, hdRotatedFormat))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_H264_HD,
                hdRotatedFormat,
                REUSE_RESULT_NO,
                DISCARD_REASON_VIDEO_ROTATION_CHANGED));
  }

  @Test
  public void canReuseCodec_withResolutionChange_adaptiveCodec_returnsYesWithReconfiguration() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ true);

    assertThat(codecInfo.canReuseCodec(FORMAT_H264_HD, FORMAT_H264_4K))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_H264_HD,
                FORMAT_H264_4K,
                REUSE_RESULT_YES_WITH_RECONFIGURATION,
                /* discardReasons= */ 0));
  }

  @Test
  public void canReuseCodec_withResolutionChange_nonAdaptiveCodec_returnsNo() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    assertThat(codecInfo.canReuseCodec(FORMAT_H264_HD, FORMAT_H264_4K))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_H264_HD,
                FORMAT_H264_4K,
                REUSE_RESULT_NO,
                DISCARD_REASON_VIDEO_RESOLUTION_CHANGED));
  }

  @Test
  public void canReuseCodec_noResolutionChange_nonAdaptiveCodec_returnsYesWithReconfiguration() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format hdVariantFormat =
        FORMAT_H264_HD.buildUpon().setInitializationData(ImmutableList.of(new byte[] {0})).build();
    assertThat(codecInfo.canReuseCodec(FORMAT_H264_HD, hdVariantFormat))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_H264_HD,
                hdVariantFormat,
                REUSE_RESULT_YES_WITH_RECONFIGURATION,
                /* discardReasons= */ 0));
  }

  @Test
  public void canReuseCodec_hdrToSdr_returnsNo() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format hdrVariantFormat =
        FORMAT_H264_4K.buildUpon().setColorInfo(buildHdrColorInfo(C.COLOR_SPACE_BT601)).build();
    assertThat(codecInfo.canReuseCodec(hdrVariantFormat, FORMAT_H264_4K))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                hdrVariantFormat,
                FORMAT_H264_4K,
                REUSE_RESULT_NO,
                DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED));
  }

  @Test
  public void canReuseCodec_sdrToHdr_returnsNo() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format hdrVariantFormat =
        FORMAT_H264_4K.buildUpon().setColorInfo(buildHdrColorInfo(C.COLOR_SPACE_BT601)).build();
    assertThat(codecInfo.canReuseCodec(FORMAT_H264_4K, hdrVariantFormat))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_H264_4K,
                hdrVariantFormat,
                REUSE_RESULT_NO,
                DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED));
  }

  @Test
  public void canReuseCodec_hdrColorInfoChange_returnsNo() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format hdrVariantFormat1 =
        FORMAT_H264_4K.buildUpon().setColorInfo(buildHdrColorInfo(C.COLOR_SPACE_BT601)).build();
    Format hdrVariantFormat2 =
        FORMAT_H264_4K.buildUpon().setColorInfo(buildHdrColorInfo(C.COLOR_SPACE_BT709)).build();
    assertThat(codecInfo.canReuseCodec(hdrVariantFormat1, hdrVariantFormat2))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                hdrVariantFormat1,
                hdrVariantFormat2,
                REUSE_RESULT_NO,
                DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED));
  }

  @Test
  public void canReuseCodec_nullColorInfoToSdr_returnsYesWithoutReconfiguration() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format variantWithColorInfo =
        FORMAT_H264_4K.buildUpon().setColorInfo(ColorInfo.SDR_BT709_LIMITED).build();
    assertThat(codecInfo.canReuseCodec(FORMAT_H264_4K, variantWithColorInfo))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_H264_4K,
                variantWithColorInfo,
                DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION,
                /* discardReasons= */ 0));
  }

  @Test
  public void canReuseCodec_sdrToNullColorInfo_returnsYesWithoutReconfiguration() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format variantWithColorInfo =
        FORMAT_H264_4K.buildUpon().setColorInfo(ColorInfo.SDR_BT709_LIMITED).build();
    assertThat(codecInfo.canReuseCodec(variantWithColorInfo, FORMAT_H264_4K))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                variantWithColorInfo,
                FORMAT_H264_4K,
                DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION,
                /* discardReasons= */ 0));
  }

  @Test
  public void canReuseCodec_sdrToSdrWithPartialInformation_returnsYesWithoutReconfiguration() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format variantWithFullColorInfo =
        FORMAT_H264_4K.buildUpon().setColorInfo(ColorInfo.SDR_BT709_LIMITED).build();
    Format variantWithPartialColorInfo =
        FORMAT_H264_4K
            .buildUpon()
            .setColorInfo(
                ColorInfo.SDR_BT709_LIMITED.buildUpon().setColorTransfer(Format.NO_VALUE).build())
            .build();
    assertThat(codecInfo.canReuseCodec(variantWithFullColorInfo, variantWithPartialColorInfo))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                variantWithFullColorInfo,
                variantWithPartialColorInfo,
                DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION,
                /* discardReasons= */ 0));
  }

  @Test
  public void canReuseCodec_audioWithDifferentChannelCounts_returnsNo() {
    MediaCodecInfo codecInfo = buildAacCodecInfo();

    assertThat(codecInfo.canReuseCodec(FORMAT_AAC_STEREO, FORMAT_AAC_SURROUND))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_AAC_STEREO,
                FORMAT_AAC_SURROUND,
                REUSE_RESULT_NO,
                DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED));
  }

  @Test
  public void canReuseCodec_audioWithSameChannelCounts_returnsYesWithFlush() {
    MediaCodecInfo codecInfo = buildAacCodecInfo();

    Format stereoVariantFormat = FORMAT_AAC_STEREO.buildUpon().setAverageBitrate(100).build();
    assertThat(codecInfo.canReuseCodec(FORMAT_AAC_STEREO, stereoVariantFormat))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_AAC_STEREO,
                stereoVariantFormat,
                REUSE_RESULT_YES_WITH_FLUSH,
                /* discardReasons= */ 0));
  }

  @Test
  public void canReuseCodec_audioWithDifferentInitializationData_returnsNo() {
    MediaCodecInfo codecInfo = buildAacCodecInfo();

    Format stereoVariantFormat =
        FORMAT_AAC_STEREO
            .buildUpon()
            .setInitializationData(ImmutableList.of(new byte[] {0}))
            .build();
    assertThat(codecInfo.canReuseCodec(FORMAT_AAC_STEREO, stereoVariantFormat))
        .isEqualTo(
            new DecoderReuseEvaluation(
                codecInfo.name,
                FORMAT_AAC_STEREO,
                stereoVariantFormat,
                REUSE_RESULT_NO,
                DISCARD_REASON_INITIALIZATION_DATA_CHANGED));
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_withDifferentMimeType_returnsFalse() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ true);

    Format hdAv1Format = FORMAT_H264_HD.buildUpon().setSampleMimeType(VIDEO_AV1).build();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                FORMAT_H264_HD, hdAv1Format, /* isNewFormatComplete= */ true))
        .isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_withRotation_returnsFalse() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ true);

    Format hdRotatedFormat = FORMAT_H264_HD.buildUpon().setRotationDegrees(90).build();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                FORMAT_H264_HD, hdRotatedFormat, /* isNewFormatComplete= */ true))
        .isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_withResolutionChange_adaptiveCodec_returnsTrue() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ true);

    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                FORMAT_H264_HD, FORMAT_H264_4K, /* isNewFormatComplete= */ true))
        .isTrue();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_withResolutionChange_nonAdaptiveCodec_returnsFalse() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                FORMAT_H264_HD, FORMAT_H264_4K, /* isNewFormatComplete= */ true))
        .isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_noResolutionChange_nonAdaptiveCodec_returnsTrue() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format hdVariantFormat =
        FORMAT_H264_HD.buildUpon().setInitializationData(ImmutableList.of(new byte[] {0})).build();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                FORMAT_H264_HD, hdVariantFormat, /* isNewFormatComplete= */ true))
        .isTrue();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_colorInfoOmittedFromCompleteNewFormat_returnsFalse() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format hdrVariantFormat =
        FORMAT_H264_4K.buildUpon().setColorInfo(buildHdrColorInfo(C.COLOR_SPACE_BT601)).build();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                hdrVariantFormat, FORMAT_H264_4K, /* isNewFormatComplete= */ true))
        .isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_colorInfoOmittedFromIncompleteNewFormat_returnsTrue() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format hdrVariantFormat =
        FORMAT_H264_4K.buildUpon().setColorInfo(buildHdrColorInfo(C.COLOR_SPACE_BT601)).build();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                hdrVariantFormat, FORMAT_H264_4K, /* isNewFormatComplete= */ false))
        .isTrue();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_colorInfoOmittedFromOldFormat_returnsFalse() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format hdrVariantFormat =
        FORMAT_H264_4K.buildUpon().setColorInfo(buildHdrColorInfo(C.COLOR_SPACE_BT601)).build();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                FORMAT_H264_4K, hdrVariantFormat, /* isNewFormatComplete= */ true))
        .isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_colorInfoChange_returnsFalse() {
    MediaCodecInfo codecInfo = buildH264CodecInfo(/* adaptive= */ false);

    Format hdrVariantFormat1 =
        FORMAT_H264_4K.buildUpon().setColorInfo(buildHdrColorInfo(C.COLOR_SPACE_BT601)).build();
    Format hdrVariantFormat2 =
        FORMAT_H264_4K.buildUpon().setColorInfo(buildHdrColorInfo(C.COLOR_SPACE_BT709)).build();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                hdrVariantFormat1, hdrVariantFormat2, /* isNewFormatComplete= */ true))
        .isFalse();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                hdrVariantFormat1, hdrVariantFormat2, /* isNewFormatComplete= */ false))
        .isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_audioWithDifferentChannelCounts_returnsFalse() {
    MediaCodecInfo codecInfo = buildAacCodecInfo();

    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                FORMAT_AAC_STEREO, FORMAT_AAC_SURROUND, /* isNewFormatComplete= */ true))
        .isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_audioWithSameChannelCounts_returnsFalse() {
    MediaCodecInfo codecInfo = buildAacCodecInfo();

    Format stereoVariantFormat = FORMAT_AAC_STEREO.buildUpon().setAverageBitrate(100).build();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                FORMAT_AAC_STEREO, stereoVariantFormat, /* isNewFormatComplete= */ true))
        .isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void isSeamlessAdaptationSupported_audioWithDifferentInitializationData_returnsFalse() {
    MediaCodecInfo codecInfo = buildAacCodecInfo();

    Format stereoVariantFormat =
        FORMAT_AAC_STEREO
            .buildUpon()
            .setInitializationData(ImmutableList.of(new byte[] {0}))
            .build();
    assertThat(
            codecInfo.isSeamlessAdaptationSupported(
                FORMAT_AAC_STEREO, stereoVariantFormat, /* isNewFormatComplete= */ true))
        .isFalse();
  }

  private static MediaCodecInfo buildH264CodecInfo(boolean adaptive) {
    return new MediaCodecInfo(
        "h264",
        VIDEO_H264,
        VIDEO_H264,
        /* capabilities= */ null,
        /* hardwareAccelerated= */ true,
        /* softwareOnly= */ false,
        /* vendor= */ true,
        adaptive,
        /* tunneling= */ false,
        /* secure= */ false);
  }

  private static MediaCodecInfo buildAacCodecInfo() {
    return new MediaCodecInfo(
        "aac",
        AUDIO_AAC,
        AUDIO_AAC,
        /* capabilities= */ null,
        /* hardwareAccelerated= */ false,
        /* softwareOnly= */ true,
        /* vendor= */ false,
        /* adaptive= */ false,
        /* tunneling= */ false,
        /* secure= */ false);
  }

  private static ColorInfo buildHdrColorInfo(@C.ColorSpace int colorSpace) {
    return new ColorInfo.Builder()
        .setColorSpace(colorSpace)
        .setColorRange(C.COLOR_RANGE_FULL)
        .setColorTransfer(C.COLOR_TRANSFER_HLG)
        .build();
  }
}
