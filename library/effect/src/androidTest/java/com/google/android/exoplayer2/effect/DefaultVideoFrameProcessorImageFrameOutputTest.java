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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.readBitmap;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.VideoFrameProcessorTestRunner;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for frame queuing and output in {@link DefaultVideoFrameProcessor} given image input. */
@RunWith(AndroidJUnit4.class)
public class DefaultVideoFrameProcessorImageFrameOutputTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";
  public static final String WRAPPED_CROP_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/image_input_with_wrapped_crop.png";
  public static final String BITMAP_OVERLAY_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_bitmap_FrameProcessor.png";

  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;
  private @MonotonicNonNull AtomicInteger framesProduced;

  @EnsuresNonNull("framesProduced")
  @Before
  public void setUp() {
    framesProduced = new AtomicInteger();
  }

  @After
  public void release() {
    checkNotNull(videoFrameProcessorTestRunner).release();
  }

  @RequiresNonNull("framesProduced")
  @Test
  public void imageInput_queueThreeBitmaps_outputsAllFrames() throws Exception {
    String testId = "imageInput_withThreeBitmaps_outputsAllFrames";
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH), C.MICROS_PER_SECOND, /* frameRate= */ 2);
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(WRAPPED_CROP_PNG_ASSET_PATH), 2 * C.MICROS_PER_SECOND, /* frameRate= */ 3);
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH), 3 * C.MICROS_PER_SECOND, /* frameRate= */ 4);
    videoFrameProcessorTestRunner.endFrameProcessingAndGetImage();

    int actualFrameCount = framesProduced.get();
    assertThat(actualFrameCount).isEqualTo(/* expected= */ 20);
  }

  @RequiresNonNull("framesProduced")
  @Test
  public void imageInput_queueTwentyBitmaps_outputsAllFrames() throws Exception {
    String testId = "imageInput_queueTwentyBitmaps_outputsAllFrames";
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();

    for (int i = 0; i < 20; i++) {
      videoFrameProcessorTestRunner.queueInputBitmap(
          readBitmap(ORIGINAL_PNG_ASSET_PATH),
          /* durationUs= */ C.MICROS_PER_SECOND,
          /* frameRate= */ 1);
    }
    videoFrameProcessorTestRunner.endFrameProcessingAndGetImage();

    int actualFrameCount = framesProduced.get();
    assertThat(actualFrameCount).isEqualTo(/* expected= */ 20);
  }

  @RequiresNonNull("framesProduced")
  @Test
  public void imageInput_queueEndAndQueueAgain_outputsFirstSetOfFramesOnly() throws Exception {
    String testId = "imageInput_queueEndAndQueueAgain_outputsFirstSetOfFramesOnly";
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* frameRate= */ 2);
    videoFrameProcessorTestRunner.endFrameProcessingAndGetImage();
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ 2 * C.MICROS_PER_SECOND,
        /* frameRate= */ 3);

    int actualFrameCount = framesProduced.get();
    assertThat(actualFrameCount).isEqualTo(/* expected= */ 2);
  }

  private VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory())
        .setIsInputTextureExternal(false)
        .setOnFrameAvailableListener((unused) -> checkNotNull(framesProduced).incrementAndGet());
  }
}
