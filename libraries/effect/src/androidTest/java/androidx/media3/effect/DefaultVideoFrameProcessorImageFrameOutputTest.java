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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
  public static final String SCALE_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/scale_wide.png";
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
  public void imageInput_queueThreeBitmaps_outputsCorrectNumberOfFrames() throws Exception {
    String testId = "imageInput_queueThreeBitmaps_outputsCorrectNumberOfFrames";
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH), C.MICROS_PER_SECOND, /* frameRate= */ 2);
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(SCALE_WIDE_PNG_ASSET_PATH), 2 * C.MICROS_PER_SECOND, /* frameRate= */ 3);
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH), 3 * C.MICROS_PER_SECOND, /* frameRate= */ 4);
    videoFrameProcessorTestRunner.endFrameProcessingAndGetImage();

    int actualFrameCount = framesProduced.get();
    assertThat(actualFrameCount).isEqualTo(/* expected= */ 20);
  }

  @RequiresNonNull("framesProduced")
  @Test
  public void imageInput_queueTwentyBitmaps_outputsCorrectNumberOfFrames() throws Exception {
    String testId = "imageInput_queueTwentyBitmaps_outputsCorrectNumberOfFrames";
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
  public void
      imageInput_queueEndAndQueueAgain_outputsFirstSetOfFramesOnlyAtTheCorrectPresentationTimesUs()
          throws Exception {
    String testId =
        "imageInput_queueEndAndQueueAgain_outputsFirstSetOfFramesOnlyAtTheCorrectPresentationTimesUs";
    Queue<Long> actualPresentationTimesUs = new ConcurrentLinkedQueue<>();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOnOutputFrameAvailableListener(actualPresentationTimesUs::add)
            .build();

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* frameRate= */ 2);
    videoFrameProcessorTestRunner.endFrameProcessingAndGetImage();
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ 2 * C.MICROS_PER_SECOND,
        /* frameRate= */ 3);

    assertThat(actualPresentationTimesUs).containsExactly(0L, C.MICROS_PER_SECOND / 2).inOrder();
  }

  private VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory())
        .setIsInputTextureExternal(false)
        .setOnOutputFrameAvailableListener(
            unused -> checkNotNull(framesProduced).incrementAndGet());
  }
}
