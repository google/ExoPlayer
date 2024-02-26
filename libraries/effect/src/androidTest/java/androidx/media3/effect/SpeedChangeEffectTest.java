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
package androidx.media3.effect;

import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.test.utils.VideoFrameProcessorTestRunner.createTimestampIterator;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.test.utils.TestSpeedProvider;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner.OnOutputFrameAvailableForRenderingListener;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Tests for {@link SpeedChangeEffect}. */
@RunWith(AndroidJUnit4.class)
public class SpeedChangeEffectTest {

  @Rule public final TestName testName = new TestName();

  private static final String IMAGE_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/original.png";

  @Test
  public void increaseSpeed_outputsFramesAtTheCorrectPresentationTimesUs() throws Exception {
    List<Long> outputPresentationTimesUs = new ArrayList<>();
    VideoFrameProcessorTestRunner videoFrameProcessorTestRunner =
        getVideoFrameProcessorTestRunner(
            testName.getMethodName(), new SpeedChangeEffect(2), outputPresentationTimesUs::add);

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(IMAGE_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 5);
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(outputPresentationTimesUs)
        .containsExactly(0L, 100_000L, 200_000L, 300_000L, 400_000L)
        .inOrder();
  }

  @Test
  public void decreaseSpeed_outputsFramesAtTheCorrectPresentationTimesUs() throws Exception {
    List<Long> outputPresentationTimesUs = new ArrayList<>();
    VideoFrameProcessorTestRunner videoFrameProcessorTestRunner =
        getVideoFrameProcessorTestRunner(
            testName.getMethodName(), new SpeedChangeEffect(0.5f), outputPresentationTimesUs::add);

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(IMAGE_PATH),
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 5);
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(outputPresentationTimesUs)
        .containsExactly(0L, 400_000L, 800_000L, 1_200_000L, 1_600_000L)
        .inOrder();
  }

  @Test
  public void variableSpeedChange_outputsFramesAtTheCorrectPresentationTimesUs() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 1_500_000, 3_000_000},
            /* speeds= */ new float[] {1, 2, 1});
    List<Long> outputPresentationTimesUs = new ArrayList<>();
    VideoFrameProcessorTestRunner videoFrameProcessorTestRunner =
        getVideoFrameProcessorTestRunner(
            testName.getMethodName(),
            new SpeedChangeEffect(speedProvider),
            outputPresentationTimesUs::add);

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(IMAGE_PATH),
        /* durationUs= */ 5_000_000L,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(IMAGE_PATH),
        /* durationUs= */ 5_000_000L,
        /* offsetToAddUs= */ 5_000_000L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();

    ImmutableList<Long> firstStreamExpectedTimestamps =
        ImmutableList.of(0L, 1_000_000L, 1_750_000L, 2_250_000L, 3_250_000L);
    ImmutableList<Long> secondStreamExpectedTimestamps =
        ImmutableList.of(5_000_000L, 6_000_000L, 6_750_000L, 7_250_000L, 8_250_000L);
    ImmutableList<Long> allExpectedTimestamps =
        new ImmutableList.Builder<Long>()
            .addAll(firstStreamExpectedTimestamps)
            .addAll(secondStreamExpectedTimestamps)
            .build();
    assertThat(outputPresentationTimesUs)
        .containsExactlyElementsIn(allExpectedTimestamps)
        .inOrder();
  }

  @Test
  public void
      variableSpeedChange_multipleSpeedChangesBetweenFrames_outputsFramesAtTheCorrectPresentationTimesUs()
          throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 1_000_000, 2_000_000},
            /* speeds= */ new float[] {4, 2, 1});
    List<Long> outputPresentationTimesUs = new ArrayList<>();
    VideoFrameProcessorTestRunner videoFrameProcessorTestRunner =
        getVideoFrameProcessorTestRunner(
            testName.getMethodName(),
            new SpeedChangeEffect(speedProvider),
            outputPresentationTimesUs::add);
    Bitmap bitmap = readBitmap(IMAGE_PATH);
    ImmutableList<Long> inputTimestamps = ImmutableList.of(0L, 4_000_000L, 5_000_000L);

    videoFrameProcessorTestRunner.queueInputBitmaps(
        bitmap.getWidth(),
        bitmap.getHeight(),
        Pair.create(bitmap, createTimestampIterator(inputTimestamps)));
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(outputPresentationTimesUs).containsExactly(0L, 2_750_000L, 3_750_000L).inOrder();
  }

  private static VideoFrameProcessorTestRunner getVideoFrameProcessorTestRunner(
      String testId,
      GlEffect speedChangeEffect,
      OnOutputFrameAvailableForRenderingListener onOutputFrameAvailableForRenderingListener)
      throws VideoFrameProcessingException {
    VideoFrameProcessor.Factory videoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder().build();
    ImmutableList<Effect> effects = ImmutableList.of(speedChangeEffect);
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
        .setEffects(effects)
        .setOnOutputFrameAvailableForRenderingListener(onOutputFrameAvailableForRenderingListener)
        .build();
  }
}
