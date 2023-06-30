/*
 * Copyright 2023 The Android Open Source Project
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

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_BITMAP;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.FrameDropEffect;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.media3.transformer.TextureBitmapReader;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to ensure {@link FrameDropEffect} outputs the correct frame associated with a chosen
 * timestamp.
 */
@RunWith(AndroidJUnit4.class)
public class FrameDropPixelTest {
  private static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";
  private static final String MEDIA3_TEST_PNG_ASSET_PATH =
      "media/bitmap/input_images/media3test.png";
  private static final String ROTATE_90_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/rotate90.png";
  private static final String SRGB_TO_ELECTRICAL_ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/srgb_to_electrical_original.png";
  private static final String SRGB_TO_ELECTRICAL_MEDIA3_TEST_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/srgb_to_electrical_media3test.png";

  private @MonotonicNonNull TextureBitmapReader textureBitmapReader;
  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;

  @EnsuresNonNull("textureBitmapReader")
  @Before
  public void setUp() {
    textureBitmapReader = new TextureBitmapReader();
  }

  @After
  public void tearDown() {
    checkNotNull(videoFrameProcessorTestRunner).release();
  }

  @RequiresNonNull("textureBitmapReader")
  @Test
  public void frameDrop_withDefaultStrategy_outputsCorrectFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    String testId =
        "frameDrop_withDefaultStrategy_outputsCorrectFramesAtTheCorrectPresentationTimesUs";
    videoFrameProcessorTestRunner =
        createDefaultFrameProcessorTestRunnerBuilder(
            testId, FrameDropEffect.createDefaultFrameDropEffect(/* targetFrameRate= */ 30));

    long expectedPresentationTimeUs1 = 0;
    long expectedPresentationTimeUs2 = 32_000;
    long expectedPresentationTimeUs3 = 71_000;
    Bitmap chosenBitmap1 = readBitmap(ORIGINAL_PNG_ASSET_PATH);
    Bitmap chosenBitmap2 = readBitmap(MEDIA3_TEST_PNG_ASSET_PATH);
    Bitmap droppedFrameBitmap = readBitmap(ROTATE_90_PNG_ASSET_PATH);
    queueOneFrameAt(chosenBitmap1, expectedPresentationTimeUs1);
    queueOneFrameAt(droppedFrameBitmap, /* presentationTimeUs= */ 16_000L);
    queueOneFrameAt(chosenBitmap2, expectedPresentationTimeUs2);
    queueOneFrameAt(droppedFrameBitmap, /* presentationTimeUs= */ 48_000L);
    queueOneFrameAt(droppedFrameBitmap, /* presentationTimeUs= */ 58_000L);
    queueOneFrameAt(chosenBitmap1, expectedPresentationTimeUs3);
    queueOneFrameAt(droppedFrameBitmap, /* presentationTimeUs= */ 86_000L);
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(textureBitmapReader.getOutputTimestamps())
        .containsExactly(
            expectedPresentationTimeUs1, expectedPresentationTimeUs2, expectedPresentationTimeUs3)
        .inOrder();
    assertThat(
            getBitmapAveragePixelAbsoluteDifferenceArgb8888(
                readBitmap(SRGB_TO_ELECTRICAL_ORIGINAL_PNG_ASSET_PATH),
                textureBitmapReader.getBitmap(expectedPresentationTimeUs1),
                testId))
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
    assertThat(
            getBitmapAveragePixelAbsoluteDifferenceArgb8888(
                readBitmap(SRGB_TO_ELECTRICAL_MEDIA3_TEST_PNG_ASSET_PATH),
                textureBitmapReader.getBitmap(expectedPresentationTimeUs2),
                testId))
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
    assertThat(
            getBitmapAveragePixelAbsoluteDifferenceArgb8888(
                readBitmap(SRGB_TO_ELECTRICAL_ORIGINAL_PNG_ASSET_PATH),
                textureBitmapReader.getBitmap(expectedPresentationTimeUs3),
                testId))
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @RequiresNonNull("textureBitmapReader")
  @Test
  public void frameDrop_withSimpleStrategy_outputsCorrectFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    String testId =
        "frameDrop_withSimpleStrategy_outputsCorrectFramesAtTheCorrectPresentationTimesUs";
    videoFrameProcessorTestRunner =
        createDefaultFrameProcessorTestRunnerBuilder(
            testId,
            FrameDropEffect.createSimpleFrameDropEffect(
                /* expectedFrameRate= */ 6, /* targetFrameRate= */ 2));
    long expectedPresentationTimeUs1 = 500_000;
    long expectedPresentationTimeUs2 = 1_500_000;
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 4);
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(MEDIA3_TEST_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ C.MICROS_PER_SECOND,
        /* frameRate= */ 2);
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(textureBitmapReader.getOutputTimestamps())
        .containsExactly(expectedPresentationTimeUs1, expectedPresentationTimeUs2)
        .inOrder();
    Bitmap actualBitmap1 = textureBitmapReader.getBitmap(expectedPresentationTimeUs1);
    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual1", actualBitmap1, /* path= */ null);
    Bitmap actualBitmap2 = textureBitmapReader.getBitmap(expectedPresentationTimeUs2);
    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual2", actualBitmap2, /* path= */ null);
    assertThat(
            getBitmapAveragePixelAbsoluteDifferenceArgb8888(
                readBitmap(SRGB_TO_ELECTRICAL_ORIGINAL_PNG_ASSET_PATH), actualBitmap1, testId))
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
    assertThat(
            getBitmapAveragePixelAbsoluteDifferenceArgb8888(
                readBitmap(SRGB_TO_ELECTRICAL_MEDIA3_TEST_PNG_ASSET_PATH), actualBitmap2, testId))
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @RequiresNonNull("textureBitmapReader")
  private VideoFrameProcessorTestRunner createDefaultFrameProcessorTestRunnerBuilder(
      String testId, FrameDropEffect frameDropEffect) throws VideoFrameProcessingException {
    VideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setTextureOutput(
                (outputTexture, presentationTimeUs, releaseOutputTextureCallback, token) ->
                    checkNotNull(textureBitmapReader)
                        .readBitmapAndReleaseTexture(
                            outputTexture, presentationTimeUs, releaseOutputTextureCallback),
                /* textureOutputCapacity= */ 1)
            .build();
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
        .setInputType(INPUT_TYPE_BITMAP)
        .setInputColorInfo(ColorInfo.SRGB_BT709_FULL)
        .setEffects(frameDropEffect)
        .build();
  }

  /**
   * Queues a {@link Bitmap} into the {@link VideoFrameProcessor} so that exactly one frame is
   * produced at the given {@code presentationTimeUs}.
   */
  private void queueOneFrameAt(Bitmap bitmap, long presentationTimeUs) {
    checkNotNull(videoFrameProcessorTestRunner)
        .queueInputBitmap(
            bitmap,
            /* durationUs= */ C.MICROS_PER_SECOND,
            /* offsetToAddUs= */ presentationTimeUs,
            /* frameRate= */ 1);
  }
}
