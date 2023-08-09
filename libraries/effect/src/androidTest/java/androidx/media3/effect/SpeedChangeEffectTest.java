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
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
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
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";

  @Test
  public void changeSpeed_outputsFramesAtTheCorrectPresentationTimesUs() throws Exception {
    String testId = testName.getMethodName();
    VideoFrameProcessor.Factory videoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder().build();
    ImmutableList<Effect> effects = ImmutableList.of(new SpeedChangeEffect(2f));
    List<Long> outputPresentationTimesUs = new ArrayList<>();
    VideoFrameProcessorTestRunner.OnOutputFrameAvailableForRenderingListener
        onOutputFrameAvailableForRenderingListener = outputPresentationTimesUs::add;
    VideoFrameProcessorTestRunner videoFrameProcessorTestRunner =
        new VideoFrameProcessorTestRunner.Builder()
            .setTestId(testId)
            .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
            .setEffects(effects)
            .setOnOutputFrameAvailableForRenderingListener(
                onOutputFrameAvailableForRenderingListener)
            .build();

    videoFrameProcessorTestRunner.queueInputBitmap(
        readBitmap(IMAGE_PATH), C.MICROS_PER_SECOND, /* offsetToAddUs= */ 0L, /* frameRate= */ 5);
    videoFrameProcessorTestRunner.endFrameProcessing();

    assertThat(outputPresentationTimesUs)
        .containsExactly(0L, 100_000L, 200_000L, 300_000L, 400_000L)
        .inOrder();
  }
}
