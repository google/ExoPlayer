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
import static androidx.media3.test.utils.VideoFrameProcessorTestRunner.createTimestampIterator;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Tests for frame queuing and output in {@link DefaultVideoFrameProcessor} given image input. */
@RunWith(AndroidJUnit4.class)
public class DefaultVideoFrameProcessorImageFrameOutputTest {
  @Rule public final TestName testName = new TestName();

  private static final String ORIGINAL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/original.png";
  private static final String SCALE_WIDE_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/scale_wide.png";
  private static final String BITMAP_OVERLAY_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/overlay_bitmap_FrameProcessor.png";

  private String testId;
  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;
  private @MonotonicNonNull AtomicInteger framesProduced;

  @Before
  @EnsuresNonNull({"framesProduced"})
  public void setUp() {
    framesProduced = new AtomicInteger();
    testId = testName.getMethodName();
  }

  @After
  public void release() {
    checkNotNull(videoFrameProcessorTestRunner).release();
  }

  @Test
  @RequiresNonNull({"framesProduced"})
  public void imageInput_queueThreeBitmaps_outputsCorrectNumberOfFrames() throws Exception {
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 2);
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(SCALE_WIDE_PNG_ASSET_PATH),
        2 * C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 3);
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH),
        3 * C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 4);
    videoFrameProcessorTestRunner.endFrameProcessing();

    int actualFrameCount = framesProduced.get();
    assertThat(actualFrameCount).isEqualTo(/* expected= */ 20);
  }

  @Test
  @RequiresNonNull({"framesProduced"})
  public void imageInput_queueTwentyBitmaps_outputsCorrectNumberOfFrames() throws Exception {
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();

    for (int i = 0; i < 20; i++) {
      videoFrameProcessorTestRunner.queueInputBitmap(
          readBitmap(ORIGINAL_PNG_ASSET_PATH),
          /* durationUs= */ C.MICROS_PER_SECOND,
          /* offsetToAddUs= */ 0L,
          /* frameRate= */ 1);
    }
    videoFrameProcessorTestRunner.endFrameProcessing();

    int actualFrameCount = framesProduced.get();
    assertThat(actualFrameCount).isEqualTo(/* expected= */ 20);
  }

  @Test
  @RequiresNonNull({"framesProduced"})
  public void imageInput_queueOneWithStartOffset_outputsFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    Queue<Long> actualPresentationTimesUs = new ConcurrentLinkedQueue<>();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOnOutputFrameAvailableForRenderingListener(actualPresentationTimesUs::add)
            .build();

    long offsetUs = 1_000_000L;
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ offsetUs,
        /* frameRate= */ 2);
    videoFrameProcessorTestRunner.endFrameProcessing();
    assertThat(actualPresentationTimesUs)
        .containsExactly(offsetUs, offsetUs + C.MICROS_PER_SECOND / 2)
        .inOrder();
  }

  @Test
  @RequiresNonNull({"framesProduced"})
  public void imageInput_queueWithStartOffsets_outputsFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    Queue<Long> actualPresentationTimesUs = new ConcurrentLinkedQueue<>();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOnOutputFrameAvailableForRenderingListener(actualPresentationTimesUs::add)
            .build();

    long offsetUs1 = 1_000_000L;
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ offsetUs1,
        /* frameRate= */ 2);
    long offsetUs2 = 2_000_000L;
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(SCALE_WIDE_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ offsetUs2,
        /* frameRate= */ 2);
    videoFrameProcessorTestRunner.endFrameProcessing();
    assertThat(actualPresentationTimesUs)
        .containsExactly(
            offsetUs1,
            offsetUs1 + C.MICROS_PER_SECOND / 2,
            offsetUs2,
            offsetUs2 + C.MICROS_PER_SECOND / 2)
        .inOrder();
  }

  @Test
  @RequiresNonNull({"framesProduced"})
  public void queueBitmapsWithTimestamps_outputsFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    Queue<Long> actualPresentationTimesUs = new ConcurrentLinkedQueue<>();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOnOutputFrameAvailableForRenderingListener(actualPresentationTimesUs::add)
            .build();
    Bitmap bitmap1 = readBitmap(ORIGINAL_PNG_ASSET_PATH);
    Bitmap bitmap2 = readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH);
    Long offset1 = 0L;
    Long offset2 = C.MICROS_PER_SECOND;
    Long offset3 = 2 * C.MICROS_PER_SECOND;

    videoFrameProcessorTestRunner.queueInputBitmaps(
        bitmap1.getWidth(),
        bitmap1.getHeight(),
        Pair.create(bitmap1, createTimestampIterator(ImmutableList.of(offset1))),
        Pair.create(bitmap2, createTimestampIterator(ImmutableList.of(offset2, offset3))));
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(actualPresentationTimesUs).containsExactly(offset1, offset2, offset3).inOrder();
  }

  private VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory.Builder().build())
        .setOnOutputFrameAvailableForRenderingListener(
            unused -> checkNotNull(framesProduced).incrementAndGet());
  }
}
