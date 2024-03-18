/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.transformer.mh;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.readBitmap;
import static com.google.android.exoplayer2.testutil.VideoFrameProcessorTestRunner.VIDEO_FRAME_PROCESSING_WAIT_MS;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.recordTestSkipped;
import static com.google.android.exoplayer2.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsHdrEditing;
import static com.google.android.exoplayer2.transformer.mh.UnoptimizedGlEffect.NO_OP_EFFECT;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.video.ColorInfo.SDR_BT709_LIMITED;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.effect.BitmapOverlay;
import com.google.android.exoplayer2.effect.DefaultGlObjectsProvider;
import com.google.android.exoplayer2.effect.DefaultVideoFrameProcessor;
import com.google.android.exoplayer2.effect.GlTextureProducer;
import com.google.android.exoplayer2.effect.OverlayEffect;
import com.google.android.exoplayer2.testutil.BitmapPixelTestUtil;
import com.google.android.exoplayer2.testutil.TextureBitmapReader;
import com.google.android.exoplayer2.testutil.VideoFrameProcessorTestRunner;
import com.google.android.exoplayer2.transformer.AndroidTestUtil;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.GlObjectsProvider;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.json.JSONException;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Pixel test for video frame processing, outputting to a texture, via {@link
 * DefaultVideoFrameProcessor}.
 *
 * <p>Uses a {@link DefaultVideoFrameProcessor} to process one frame, and checks that the actual
 * output matches expected output, either from a golden file or from another edit.
 */
// TODO(b/263395272): Move this test to effects/mh tests, and remove @TestOnly dependencies.
@RunWith(AndroidJUnit4.class)
public final class DefaultVideoFrameProcessorTextureOutputPixelTest {
  private static final String ORIGINAL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/original.png";
  private static final String BITMAP_OVERLAY_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/overlay_bitmap_FrameProcessor.png";
  private static final String OVERLAY_PNG_ASSET_PATH = "media/png/media3test.png";
  private static final String ULTRA_HDR_ASSET_PATH = "media/jpeg/ultraHDR.jpg";

  private static final String ORIGINAL_HLG10_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/original_hlg10.png";
  private static final String ORIGINAL_HDR10_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/original_hdr10.png";
  private static final String ULTRA_HDR_TO_HLG_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/ultrahdr_to_hlg.png";
  private static final String ULTRA_HDR_TO_PQ_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/ultrahdr_to_pq.png";

  /** Input SDR video of which we only use the first frame. */
  private static final String INPUT_SDR_MP4_ASSET_STRING = "media/mp4/sample.mp4";

  /** Input PQ video of which we only use the first frame. */
  private static final String INPUT_PQ_MP4_ASSET_STRING = "media/mp4/hdr10-720p.mp4";

  /** Input HLG video of which we only use the first frame. */
  private static final String INPUT_HLG10_MP4_ASSET_STRING = "media/mp4/hlg-1080p.mp4";

  @Rule public final TestName testName = new TestName();

  private String testId;

  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @After
  public void release() {
    if (videoFrameProcessorTestRunner != null) {
      videoFrameProcessorTestRunner.release();
    }
  }

  @Test
  public void noEffects_matchesGoldenFile() throws Exception {
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ null)) {
      return;
    }
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void noEffects_textureInput_matchesGoldenFile() throws Exception {
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ null)) {
      return;
    }
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_SDR_MP4_ASSET_STRING,
            SDR_BT709_LIMITED,
            ImmutableList.of());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void bitmapOverlay_matchesGoldenFile() throws Exception {
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ null)) {
      return;
    }
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new OverlayEffect(ImmutableList.of(bitmapOverlay)))
            .build();
    Bitmap expectedBitmap = readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH);
    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void bitmapOverlay_textureInput_matchesGoldenFile() throws Exception {
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ null)) {
      return;
    }
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
    ImmutableList<Effect> effects =
        ImmutableList.of(new OverlayEffect(ImmutableList.of(bitmapOverlay)));
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId, consumersBitmapReader, INPUT_SDR_MP4_ASSET_STRING, SDR_BT709_LIMITED, effects);
    Bitmap expectedBitmap = readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void noEffects_hlg10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
    assumeDeviceSupportsHdrEditing(testId, format);
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_HLG10_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HLG10_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hlg10TextureInput_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
    assumeDeviceSupportsHdrEditing(testId, format);
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_HLG10_MP4_ASSET_STRING,
            colorInfo,
            ImmutableList.of());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HLG10_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hlg10UltraHDRImageInput_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT);
    assumeDeviceSupportsUltraHdrEditing();
    ColorInfo outputColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_HLG)
            .setColorRange(C.COLOR_RANGE_FULL)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputColorInfo)
            .build();
    Bitmap originalBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_TO_HLG_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.queueInputBitmap(
        originalBitmap,
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hdr10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
    assumeDeviceSupportsHdrEditing(testId, format);
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HDR10_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hdr10TextureInput_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
    assumeDeviceSupportsHdrEditing(testId, format);
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_PQ_MP4_ASSET_STRING,
            colorInfo,
            ImmutableList.of());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HDR10_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hdr10UltraHDRImageInput_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10_FORMAT);
    assumeDeviceSupportsUltraHdrEditing();
    ColorInfo outputColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .setColorRange(C.COLOR_RANGE_FULL)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputColorInfo)
            .build();
    Bitmap originalBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_TO_PQ_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.queueInputBitmap(
        originalBitmap,
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hlg10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
    assumeDeviceSupportsHdrEditing(testId, format);
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_HLG10_MP4_ASSET_STRING)
            .setEffects(NO_OP_EFFECT)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HLG10_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hlg10TextureInput_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
    assumeDeviceSupportsHdrEditing(testId, format);
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_HLG10_MP4_ASSET_STRING,
            colorInfo,
            ImmutableList.of(NO_OP_EFFECT));
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HLG10_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hlg10UltraHDRImageInput_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10_FORMAT);
    assumeDeviceSupportsUltraHdrEditing();
    ColorInfo outputColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_HLG)
            .setColorRange(C.COLOR_RANGE_FULL)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputColorInfo)
            .setEffects(NO_OP_EFFECT)
            .build();
    Bitmap originalBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_TO_HLG_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.queueInputBitmap(
        originalBitmap,
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hdr10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
    assumeDeviceSupportsHdrEditing(testId, format);
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .setEffects(NO_OP_EFFECT)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HDR10_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hdr10TextureInput_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
    assumeDeviceSupportsHdrEditing(testId, format);
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_PQ_MP4_ASSET_STRING,
            colorInfo,
            ImmutableList.of(NO_OP_EFFECT));
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HDR10_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hdr10UltraHDRImageInput_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10_FORMAT);
    assumeDeviceSupportsUltraHdrEditing();
    ColorInfo outputColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .setColorRange(C.COLOR_RANGE_FULL)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputColorInfo)
            .setEffects(NO_OP_EFFECT)
            .build();
    Bitmap originalBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_TO_PQ_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.queueInputBitmap(
        originalBitmap,
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  private VideoFrameProcessorTestRunner getTexIdProducingFrameProcessorTestRunner(
      String testId,
      TextureBitmapReader consumersBitmapReader,
      String videoAssetPath,
      ColorInfo colorInfo,
      List<Effect> effects)
      throws VideoFrameProcessingException {
    TextureBitmapReader producersBitmapReader = new TextureBitmapReader();
    DefaultVideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setTextureOutput(
                (outputTextureProducer, outputTexture, presentationTimeUs, syncObject) -> {
                  try {
                    inputTextureIntoVideoFrameProcessor(
                        testId,
                        consumersBitmapReader,
                        colorInfo,
                        effects,
                        outputTexture,
                        outputTextureProducer,
                        presentationTimeUs,
                        syncObject);
                  } catch (Exception e) {
                    throw VideoFrameProcessingException.from(e, presentationTimeUs);
                  }
                },
                /* textureOutputCapacity= */ 1)
            .build();
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
        .setVideoAssetPath(videoAssetPath)
        .setOutputColorInfo(colorInfo)
        .setBitmapReader(producersBitmapReader)
        .build();
  }

  private void inputTextureIntoVideoFrameProcessor(
      String testId,
      TextureBitmapReader bitmapReader,
      ColorInfo colorInfo,
      List<Effect> effects,
      GlTextureInfo texture,
      GlTextureProducer textureProducer,
      long presentationTimeUs,
      long syncObject)
      throws Exception {
    GlObjectsProvider contextSharingGlObjectsProvider =
        new DefaultGlObjectsProvider(GlUtil.getCurrentContext());
    DefaultVideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setTextureOutput(
                (outputTextureProducer, outputTexture, presentationTimeUs1, unusedSyncObject) -> {
                  bitmapReader.readBitmap(outputTexture, presentationTimeUs1);
                  outputTextureProducer.releaseOutputTexture(presentationTimeUs1);
                },
                /* textureOutputCapacity= */ 1)
            .setGlObjectsProvider(contextSharingGlObjectsProvider)
            .build();
    videoFrameProcessorTestRunner =
        new VideoFrameProcessorTestRunner.Builder()
            .setTestId(testId)
            .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
            .setOutputColorInfo(colorInfo)
            .setBitmapReader(bitmapReader)
            .setEffects(effects)
            .build();
    GlUtil.awaitSyncObject(syncObject);
    videoFrameProcessorTestRunner.queueInputTexture(texture, presentationTimeUs, colorInfo);
    videoFrameProcessorTestRunner.endFrameProcessing(VIDEO_FRAME_PROCESSING_WAIT_MS / 2);
    textureProducer.releaseOutputTexture(presentationTimeUs);
  }

  private VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    TextureBitmapReader textureBitmapReader = new TextureBitmapReader();
    DefaultVideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setTextureOutput(
                (outputTextureProducer, outputTexture, presentationTimeUs, unusedSyncObject) -> {
                  textureBitmapReader.readBitmap(outputTexture, presentationTimeUs);
                  outputTextureProducer.releaseOutputTexture(presentationTimeUs);
                },
                /* textureOutputCapacity= */ 1)
            .build();
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
        .setVideoAssetPath(INPUT_SDR_MP4_ASSET_STRING)
        .setBitmapReader(textureBitmapReader);
  }

  private void assumeDeviceSupportsUltraHdrEditing() throws JSONException, IOException {
    if (Util.SDK_INT < 34) {
      recordTestSkipped(
          getApplicationContext(), testId, "Ultra HDR is not supported on this API level.");
      throw new AssumptionViolatedException("Ultra HDR is not supported on this API level.");
    }
  }
}
