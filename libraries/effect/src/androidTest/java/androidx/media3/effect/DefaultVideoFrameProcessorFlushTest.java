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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmapUnpremultipliedAlpha;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import androidx.media3.common.C;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Test for {@link DefaultVideoFrameProcessor} flushing. */
@RunWith(AndroidJUnit4.class)
public class DefaultVideoFrameProcessorFlushTest {
  private static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/input_images/media3test_srgb.png";

  @Rule public final TestName testName = new TestName();

  private int outputFrameCount;
  private @MonotonicNonNull String testId;
  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;

  @Before
  @EnsuresNonNull({"testId"})
  public void setUp() {
    testId = testName.getMethodName();
  }

  @After
  public void release() {
    checkNotNull(videoFrameProcessorTestRunner).release();
  }

  @Test
  @RequiresNonNull({"testId"})
  public void imageInput_flushBeforeInput_throwsException() throws Exception {
    videoFrameProcessorTestRunner = createDefaultVideoFrameProcessorTestRunner(testId);

    assertThrows(IllegalStateException.class, videoFrameProcessorTestRunner::flush);
  }

  // This tests a condition that is difficult to synchronize, and is subject to a race condition. It
  // may flake/fail if any queued frames are processed in the VideoFrameProcessor thread, before
  // flush begins and cancels these pending frames. However, this is better than not testing this
  // behavior at all, and in practice has succeeded every time on a 1000-time run.
  // TODO: b/302695659 - Make this test more deterministic.
  @Test
  @RequiresNonNull({"testId"})
  public void imageInput_flushRightAfterInput_outputsPartialFrames() throws Exception {
    videoFrameProcessorTestRunner = createDefaultVideoFrameProcessorTestRunner(testId);
    Bitmap bitmap = readBitmapUnpremultipliedAlpha(ORIGINAL_PNG_ASSET_PATH);
    int inputFrameCount = 3;

    videoFrameProcessorTestRunner.queueInputBitmap(
        bitmap,
        /* durationUs= */ inputFrameCount * C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.flush();
    videoFrameProcessorTestRunner.endFrameProcessing();

    // This assertion is subject to flaking, per test comments. If it flakes, consider increasing
    // inputFrameCount.
    assertThat(outputFrameCount).isLessThan(inputFrameCount);
  }

  @Test
  @RequiresNonNull({"testId"})
  public void imageInput_flushAfterAllFramesOutput_outputsAllFrames() throws Exception {
    videoFrameProcessorTestRunner = createDefaultVideoFrameProcessorTestRunner(testId);
    Bitmap bitmap = readBitmapUnpremultipliedAlpha(ORIGINAL_PNG_ASSET_PATH);
    int inputFrameCount = 3;

    videoFrameProcessorTestRunner.queueInputBitmap(
        bitmap,
        /* durationUs= */ inputFrameCount * C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();
    videoFrameProcessorTestRunner.flush();

    assertThat(outputFrameCount).isEqualTo(inputFrameCount);
  }

  private VideoFrameProcessorTestRunner createDefaultVideoFrameProcessorTestRunner(String testId)
      throws VideoFrameProcessingException {
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory.Builder().build())
        .setOnOutputFrameAvailableForRenderingListener(unused -> outputFrameCount++)
        .build();
  }
}
