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

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_BITMAP;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FrameDropEffect}. */
@RunWith(AndroidJUnit4.class)
public class FrameDropTest {
  private static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";

  private static final String SCALE_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/scale_wide.png";

  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;

  private @MonotonicNonNull Queue<Long> actualPresentationTimesUs;

  @EnsuresNonNull("actualPresentationTimesUs")
  @Before
  public void setUp() {
    actualPresentationTimesUs = new ConcurrentLinkedQueue<>();
  }

  @After
  public void release() {
    checkNotNull(videoFrameProcessorTestRunner).release();
  }

  // TODO: b/536973872 - When support for testing multiple frames in the output, test whether the
  //  correct frames comes out.
  @RequiresNonNull("actualPresentationTimesUs")
  @Test
  public void frameDrop_withDefaultStrategy_outputsFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    String testId = "frameDrop_withDefaultStrategy_outputsFramesAtTheCorrectPresentationTimesUs";
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(
                testId, FrameDropEffect.createDefaultFrameDropEffect(/* targetFrameRate= */ 30))
            .setOnOutputFrameAvailableForRenderingListener(actualPresentationTimesUs::add)
            .build();

    ImmutableList<Integer> timestampsMs = ImmutableList.of(0, 16, 32, 48, 58, 71, 86);
    for (int timestampMs : timestampsMs) {
      videoFrameProcessorTestRunner.queueInputBitmap(
          readBitmap(ORIGINAL_PNG_ASSET_PATH),
          /* durationUs= */ C.MICROS_PER_SECOND,
          /* offsetToAddUs= */ timestampMs * 1000L,
          /* frameRate= */ 1);
    }
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(actualPresentationTimesUs).containsExactly(0L, 32_000L, 71_000L).inOrder();
  }

  @RequiresNonNull("actualPresentationTimesUs")
  @Test
  public void frameDrop_withSimpleStrategy_outputsFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    String testId = "frameDrop_withSimpleStrategy_outputsFramesAtTheCorrectPresentationTimesUs";
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(
                testId,
                FrameDropEffect.createSimpleFrameDropEffect(
                    /* expectedFrameRate= */ 6, /* targetFrameRate= */ 2))
            .build();

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 4);
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(SCALE_WIDE_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ C.MICROS_PER_SECOND,
        /* frameRate= */ 2);
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(actualPresentationTimesUs).containsExactly(500_000L, 1_500_000L).inOrder();
  }

  @RequiresNonNull("actualPresentationTimesUs")
  @Test
  public void frameDrop_withSimpleStrategy_outputsAllFrames() throws Exception {
    String testId = "frameDrop_withSimpleStrategy_outputsCorrectNumberOfFrames";
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(
                testId,
                FrameDropEffect.createSimpleFrameDropEffect(
                    /* expectedFrameRate= */ 3, /* targetFrameRate= */ 3))
            .build();

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 3);
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(actualPresentationTimesUs).containsExactly(0L, 333_333L, 666_667L).inOrder();
  }

  @RequiresNonNull("actualPresentationTimesUs")
  private VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId, FrameDropEffect frameDropEffect) {
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory.Builder().build())
        .setInputType(INPUT_TYPE_BITMAP)
        .setInputColorInfo(ColorInfo.SRGB_BT709_FULL)
        .setEffects(frameDropEffect)
        .setOnOutputFrameAvailableForRenderingListener(actualPresentationTimesUs::add);
  }
}
