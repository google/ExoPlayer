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
package androidx.media3.transformer.mh;

import static androidx.media3.common.MimeTypes.VIDEO_H265;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsOpenGlToneMapping;
import static androidx.media3.transformer.mh.UnoptimizedGlEffect.NO_OP_EFFECT;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.test.utils.DecodeOneFrameUtil;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Instrumentation pixel-test for HDR to SDR tone-mapping via {@link DefaultVideoFrameProcessor}.
 *
 * <p>Uses a {@link DefaultVideoFrameProcessor} to process one frame, and checks that the actual
 * output matches expected output, either from a golden file or from another edit.
 */
// TODO(b/263395272): Move this test to effects/mh tests.
@RunWith(AndroidJUnit4.class)
public final class ToneMapHdrToSdrUsingOpenGlPixelTest {
  private static final String TAG = "ToneMapHdrToSdrGl";

  /**
   * Maximum allowed average pixel difference between the expected and actual edited images in
   * on-device pixel difference-based tests. The value is chosen so that differences in behavior
   * across codec/OpenGL versions don't affect whether the test passes for most devices, but
   * substantial distortions introduced by changes in tested components will cause the test to fail.
   */
  private static final float MAXIMUM_DEVICE_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE =
      !Ascii.equalsIgnoreCase(Util.MODEL, "dn2103") && !Ascii.equalsIgnoreCase(Util.MODEL, "v2059")
          ? 6f
          : 7f;

  // This file is generated on a Pixel 7, because the emulator isn't able to decode HLG to generate
  // this file.
  private static final String TONE_MAP_HLG_TO_SDR_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/tone_map_hlg_to_sdr.png";
  // This file is generated on a Pixel 7, because the emulator isn't able to decode PQ to generate
  // this file.
  private static final String TONE_MAP_PQ_TO_SDR_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/tone_map_pq_to_sdr.png";

  /** Input HLG video of which we only use the first frame. */
  private static final String HLG_ASSET_STRING = "media/mp4/hlg10-color-test.mp4";

  private static final Format HLG_ASSET_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H265)
          .setWidth(1920)
          .setHeight(1080)
          .setFrameRate(30.000f)
          .setColorInfo(
              new ColorInfo.Builder()
                  .setColorSpace(C.COLOR_SPACE_BT2020)
                  .setColorRange(C.COLOR_RANGE_LIMITED)
                  .setColorTransfer(C.COLOR_TRANSFER_HLG)
                  .build())
          .setCodecs("hvc1.2.4.L153")
          .build();

  /** Input PQ video of which we only use the first frame. */
  private static final String PQ_ASSET_STRING = "media/mp4/hdr10plus-color-test.mp4";

  public static final Format PQ_ASSET_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H265)
          .setWidth(3840)
          .setHeight(2160)
          .setFrameRate(29.024f)
          .setColorInfo(
              new ColorInfo.Builder()
                  .setColorSpace(C.COLOR_SPACE_BT2020)
                  .setColorRange(C.COLOR_RANGE_LIMITED)
                  .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                  .build())
          .setCodecs("hvc1.2.4.L153")
          .build();

  private static final ColorInfo TONE_MAP_SDR_COLOR =
      new ColorInfo.Builder()
          .setColorSpace(C.COLOR_SPACE_BT709)
          .setColorRange(C.COLOR_RANGE_LIMITED)
          .setColorTransfer(C.COLOR_TRANSFER_GAMMA_2_2)
          .build();

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
  public void toneMap_hlgFrame_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(testId, HLG_ASSET_FORMAT);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setVideoAssetPath(HLG_ASSET_STRING)
            .setOutputColorInfo(TONE_MAP_SDR_COLOR)
            .build();
    Bitmap expectedBitmap = readBitmap(TONE_MAP_HLG_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap;
    try {
      videoFrameProcessorTestRunner.processFirstFrameAndEnd();
      actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();
    } catch (UnsupportedOperationException e) {
      if (e.getMessage() != null
          && e.getMessage().equals(DecodeOneFrameUtil.NO_DECODER_SUPPORT_ERROR_STRING)) {
        recordTestSkipped(
            getApplicationContext(),
            testId,
            /* reason= */ DecodeOneFrameUtil.NO_DECODER_SUPPORT_ERROR_STRING);
        return;
      } else {
        throw e;
      }
    }

    Log.i(TAG, "Successfully tone mapped.");
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_DEVICE_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void toneMapWithNoOpEffect_hlgFrame_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(testId, HLG_ASSET_FORMAT);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setVideoAssetPath(HLG_ASSET_STRING)
            .setOutputColorInfo(TONE_MAP_SDR_COLOR)
            .setEffects(ImmutableList.of(NO_OP_EFFECT))
            .build();
    Bitmap expectedBitmap = readBitmap(TONE_MAP_HLG_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap;
    try {
      videoFrameProcessorTestRunner.processFirstFrameAndEnd();
      actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();
    } catch (UnsupportedOperationException e) {
      if (e.getMessage() != null
          && e.getMessage().equals(DecodeOneFrameUtil.NO_DECODER_SUPPORT_ERROR_STRING)) {
        recordTestSkipped(
            getApplicationContext(),
            testId,
            /* reason= */ DecodeOneFrameUtil.NO_DECODER_SUPPORT_ERROR_STRING);
        return;
      } else {
        throw e;
      }
    }

    Log.i(TAG, "Successfully tone mapped.");
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_DEVICE_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void toneMap_pqFrame_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(testId, PQ_ASSET_FORMAT);

    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setVideoAssetPath(PQ_ASSET_STRING)
            .setOutputColorInfo(TONE_MAP_SDR_COLOR)
            .build();
    Bitmap expectedBitmap = readBitmap(TONE_MAP_PQ_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap;
    try {
      videoFrameProcessorTestRunner.processFirstFrameAndEnd();
      actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();
    } catch (UnsupportedOperationException e) {
      if (e.getMessage() != null
          && e.getMessage().equals(DecodeOneFrameUtil.NO_DECODER_SUPPORT_ERROR_STRING)) {
        recordTestSkipped(
            getApplicationContext(),
            testId,
            /* reason= */ DecodeOneFrameUtil.NO_DECODER_SUPPORT_ERROR_STRING);
        return;
      } else {
        throw e;
      }
    }

    Log.i(TAG, "Successfully tone mapped.");
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_DEVICE_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void toneMapWithNoOpEffect_pqFrame_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(testId, PQ_ASSET_FORMAT);

    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setVideoAssetPath(PQ_ASSET_STRING)
            .setOutputColorInfo(TONE_MAP_SDR_COLOR)
            .setEffects(ImmutableList.of(NO_OP_EFFECT))
            .build();
    Bitmap expectedBitmap = readBitmap(TONE_MAP_PQ_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap;
    try {
      videoFrameProcessorTestRunner.processFirstFrameAndEnd();
      actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();
    } catch (UnsupportedOperationException e) {
      if (e.getMessage() != null
          && e.getMessage().equals(DecodeOneFrameUtil.NO_DECODER_SUPPORT_ERROR_STRING)) {
        recordTestSkipped(
            getApplicationContext(),
            testId,
            /* reason= */ DecodeOneFrameUtil.NO_DECODER_SUPPORT_ERROR_STRING);
        return;
      } else {
        throw e;
      }
    }

    Log.i(TAG, "Successfully tone mapped.");
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_DEVICE_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  private static VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory.Builder().build());
  }
}
