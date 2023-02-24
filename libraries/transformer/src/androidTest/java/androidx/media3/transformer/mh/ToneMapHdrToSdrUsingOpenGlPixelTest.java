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

import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.AndroidTestUtil.skipAndLogIfFormatsUnsupported;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.test.utils.DecodeOneFrameUtil;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
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
  private static final float MAXIMUM_DEVICE_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE = 5f;

  // This file is generated on a Pixel 7, because the emulator isn't able to decode HLG to generate
  // this file.
  private static final String TONE_MAP_HLG_TO_SDR_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/tone_map_hlg_to_sdr.png";
  // This file is generated on a Pixel 7, because the emulator isn't able to decode PQ to generate
  // this file.
  private static final String TONE_MAP_PQ_TO_SDR_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/tone_map_pq_to_sdr.png";

  /** Input HLG video of which we only use the first frame. */
  private static final String INPUT_HLG_MP4_ASSET_STRING = "media/mp4/hlg-1080p.mp4";
  /** Input PQ video of which we only use the first frame. */
  private static final String INPUT_PQ_MP4_ASSET_STRING = "media/mp4/hdr10-720p.mp4";

  private static final String SKIP_REASON_NO_OPENGL_UNDER_API_29 =
      "OpenGL-based HDR to SDR tone mapping is unsupported below API 29.";
  private static final String SKIP_REASON_NO_YUV = "Device lacks YUV extension support.";

  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;

  @After
  public void release() {
    if (videoFrameProcessorTestRunner != null) {
      videoFrameProcessorTestRunner.release();
    }
  }

  @Test
  public void toneMap_hlgFrame_matchesGoldenFile() throws Exception {
    String testId = "toneMap_hlgFrame_matchesGoldenFile";
    if (Util.SDK_INT < 29) {
      recordTestSkipped(getApplicationContext(), testId, SKIP_REASON_NO_OPENGL_UNDER_API_29);
      return;
    }
    if (!GlUtil.isYuvTargetExtensionSupported()) {
      recordTestSkipped(getApplicationContext(), testId, SKIP_REASON_NO_YUV);
      return;
    }
    if (skipAndLogIfFormatsUnsupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT,
        /* outputFormat= */ null)) {
      return;
    }
    ColorInfo hlgColor =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_HLG)
            .build();
    ColorInfo toneMapSdrColor =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT709)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_GAMMA_2_2)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setVideoAssetPath(INPUT_HLG_MP4_ASSET_STRING)
            .setInputColorInfo(hlgColor)
            .setOutputColorInfo(toneMapSdrColor)
            .build();
    Bitmap expectedBitmap = readBitmap(TONE_MAP_HLG_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap;
    try {
      actualBitmap = videoFrameProcessorTestRunner.processFirstFrameAndEnd();
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
    // TODO(b/239735341): Move this test to mobileharness testing.
    String testId = "toneMap_pqFrame_matchesGoldenFile";
    if (Util.SDK_INT < 29) {
      recordTestSkipped(getApplicationContext(), testId, SKIP_REASON_NO_OPENGL_UNDER_API_29);
      return;
    }
    if (!GlUtil.isYuvTargetExtensionSupported()) {
      recordTestSkipped(getApplicationContext(), testId, SKIP_REASON_NO_YUV);
      return;
    }
    if (skipAndLogIfFormatsUnsupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT,
        /* outputFormat= */ null)) {
      return;
    }
    ColorInfo pqColor =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build();
    ColorInfo toneMapSdrColor =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT709)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_GAMMA_2_2)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .setInputColorInfo(pqColor)
            .setOutputColorInfo(toneMapSdrColor)
            .build();
    Bitmap expectedBitmap = readBitmap(TONE_MAP_PQ_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap;
    try {
      actualBitmap = videoFrameProcessorTestRunner.processFirstFrameAndEnd();
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

  private VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory());
  }
}
