/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import static com.google.android.exoplayer2.video.FixedFrameRateEstimator.CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC;
import static com.google.android.exoplayer2.video.FixedFrameRateEstimator.MAX_MATCHING_FRAME_DIFFERENCE_NS;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link FixedFrameRateEstimator}. */
@RunWith(AndroidJUnit4.class)
public final class FixedFrameRateEstimatorTest {

  @Test
  public void fixedFrameRate_withSingleOutlier_syncsAndResyncs() {
    long frameDurationNs = 33_333_333;
    FixedFrameRateEstimator estimator = new FixedFrameRateEstimator();

    // Initial frame.
    long framePresentationTimestampNs = 0;
    estimator.onNextFrame(framePresentationTimestampNs);

    assertThat(estimator.isSynced()).isFalse();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);

    // Frames with consistent durations, working toward establishing sync.
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC - 1; i++) {
      framePresentationTimestampNs += frameDurationNs;
      estimator.onNextFrame(framePresentationTimestampNs);

      assertThat(estimator.isSynced()).isFalse();
      assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);
    }

    // This frame should establish sync.
    framePresentationTimestampNs += frameDurationNs;
    estimator.onNextFrame(framePresentationTimestampNs);

    assertThat(estimator.isSynced()).isTrue();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(frameDurationNs);

    framePresentationTimestampNs += frameDurationNs;
    // Make the frame duration just shorter enough to lose sync.
    framePresentationTimestampNs -= MAX_MATCHING_FRAME_DIFFERENCE_NS + 1;
    estimator.onNextFrame(framePresentationTimestampNs);

    assertThat(estimator.isSynced()).isFalse();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);

    // Frames with consistent durations, working toward re-establishing sync.
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC - 1; i++) {
      framePresentationTimestampNs += frameDurationNs;
      estimator.onNextFrame(framePresentationTimestampNs);

      assertThat(estimator.isSynced()).isFalse();
      assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);
    }

    // This frame should re-establish sync.
    framePresentationTimestampNs += frameDurationNs;
    estimator.onNextFrame(framePresentationTimestampNs);

    assertThat(estimator.isSynced()).isTrue();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(frameDurationNs);
  }

  @Test
  public void fixedFrameRate_withOutlierFirstFrameDuration_syncs() {
    long frameDurationNs = 33_333_333;
    FixedFrameRateEstimator estimator = new FixedFrameRateEstimator();

    // Initial frame with double duration.
    long framePresentationTimestampNs = 0;
    estimator.onNextFrame(framePresentationTimestampNs);

    framePresentationTimestampNs += frameDurationNs * 2;
    estimator.onNextFrame(framePresentationTimestampNs);

    assertThat(estimator.isSynced()).isFalse();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);

    // Frames with consistent durations, working toward establishing sync.
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC - 1; i++) {
      framePresentationTimestampNs += frameDurationNs;
      estimator.onNextFrame(framePresentationTimestampNs);

      assertThat(estimator.isSynced()).isFalse();
      assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);
    }

    // This frame should establish sync.
    framePresentationTimestampNs += frameDurationNs;
    estimator.onNextFrame(framePresentationTimestampNs);
  }

  @Test
  public void newFixedFrameRate_resyncs() {
    long frameDurationNs = 33_333_333;
    FixedFrameRateEstimator estimator = new FixedFrameRateEstimator();

    long framePresentationTimestampNs = 0;
    estimator.onNextFrame(framePresentationTimestampNs);
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC; i++) {
      framePresentationTimestampNs += frameDurationNs;
      estimator.onNextFrame(framePresentationTimestampNs);
    }

    assertThat(estimator.isSynced()).isTrue();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(frameDurationNs);

    // Frames durations are halved from this point.
    long halfFrameRateDuration = frameDurationNs / 2;

    // Frames with consistent durations, working toward establishing new sync.
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC - 1; i++) {
      framePresentationTimestampNs += halfFrameRateDuration;
      estimator.onNextFrame(framePresentationTimestampNs);

      assertThat(estimator.isSynced()).isFalse();
      assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);
    }

    // This frame should establish sync.
    framePresentationTimestampNs += halfFrameRateDuration;
    estimator.onNextFrame(framePresentationTimestampNs);

    assertThat(estimator.isSynced()).isTrue();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(halfFrameRateDuration);
  }

  @Test
  public void fixedFrameRate_withMillisecondPrecision_syncs() {
    long frameDurationNs = 33_333_333;
    FixedFrameRateEstimator estimator = new FixedFrameRateEstimator();

    // Initial frame.
    long framePresentationTimestampNs = 0;
    estimator.onNextFrame(getNsWithMsPrecision(framePresentationTimestampNs));

    assertThat(estimator.isSynced()).isFalse();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);

    // Frames with consistent durations, working toward establishing sync.
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC - 1; i++) {
      framePresentationTimestampNs += frameDurationNs;
      estimator.onNextFrame(getNsWithMsPrecision(framePresentationTimestampNs));

      assertThat(estimator.isSynced()).isFalse();
      assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);
    }

    // This frame should establish sync.
    framePresentationTimestampNs += frameDurationNs;
    estimator.onNextFrame(getNsWithMsPrecision(framePresentationTimestampNs));

    assertThat(estimator.isSynced()).isTrue();
    // The estimated frame duration should be strictly better than millisecond precision.
    long estimatedFrameDurationNs = estimator.getFrameDurationNs();
    long estimatedFrameDurationErrorNs = Math.abs(estimatedFrameDurationNs - frameDurationNs);
    assertThat(estimatedFrameDurationErrorNs).isLessThan(1000000);
  }

  @Test
  public void variableFrameRate_doesNotSync() {
    long frameDurationNs = 33_333_333;
    FixedFrameRateEstimator estimator = new FixedFrameRateEstimator();

    // Initial frame.
    long framePresentationTimestampNs = 0;
    estimator.onNextFrame(framePresentationTimestampNs);

    assertThat(estimator.isSynced()).isFalse();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);

    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC * 10; i++) {
      framePresentationTimestampNs += frameDurationNs;
      // Adjust a frame that's just different enough, just often enough to prevent sync.
      if ((i % CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC) == 0) {
        framePresentationTimestampNs += MAX_MATCHING_FRAME_DIFFERENCE_NS + 1;
      }
      estimator.onNextFrame(framePresentationTimestampNs);

      assertThat(estimator.isSynced()).isFalse();
      assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);
    }
  }

  @Test
  public void newFixedFrameRate_withFormatFrameRateChange_resyncs() {
    long frameDurationNs = 33_333_333;
    float frameRate = (float) C.NANOS_PER_SECOND / frameDurationNs;
    FixedFrameRateEstimator estimator = new FixedFrameRateEstimator();
    estimator.onFormatChanged(frameRate);

    long framePresentationTimestampNs = 0;
    estimator.onNextFrame(framePresentationTimestampNs);
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC; i++) {
      framePresentationTimestampNs += frameDurationNs;
      estimator.onNextFrame(framePresentationTimestampNs);
    }

    assertThat(estimator.isSynced()).isTrue();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(frameDurationNs);

    // Frames durations are halved from this point.
    long halfFrameDuration = frameDurationNs * 2;
    float doubleFrameRate = (float) C.NANOS_PER_SECOND / halfFrameDuration;
    estimator.onFormatChanged(doubleFrameRate);

    // Format frame rate change should cause immediate sync loss.
    assertThat(estimator.isSynced()).isFalse();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);

    // Frames with consistent durations, working toward establishing new sync.
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC; i++) {
      framePresentationTimestampNs += halfFrameDuration;
      estimator.onNextFrame(framePresentationTimestampNs);

      assertThat(estimator.isSynced()).isFalse();
      assertThat(estimator.getFrameDurationNs()).isEqualTo(C.TIME_UNSET);
    }

    // This frame should establish sync.
    framePresentationTimestampNs += halfFrameDuration;
    estimator.onNextFrame(framePresentationTimestampNs);

    assertThat(estimator.isSynced()).isTrue();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(halfFrameDuration);
  }

  @Test
  public void smallFrameRateChange_withoutFormatFrameRateChange_keepsSyncAndAdjustsEstimate() {
    long frameDurationNs = 33_333_333; // 30 fps
    float roundedFrameRate = 30;
    FixedFrameRateEstimator estimator = new FixedFrameRateEstimator();
    estimator.onFormatChanged(roundedFrameRate);

    long framePresentationTimestampNs = 0;
    estimator.onNextFrame(framePresentationTimestampNs);
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC; i++) {
      framePresentationTimestampNs += frameDurationNs;
      estimator.onNextFrame(framePresentationTimestampNs);
    }

    assertThat(estimator.isSynced()).isTrue();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(frameDurationNs);

    long newFrameDurationNs = 33_366_667; // 30 * (1000/1001) = 29.97 fps
    estimator.onFormatChanged(roundedFrameRate); // Format frame rate is unchanged.

    // Previous estimate should remain valid for now because neither format specified a duration.
    assertThat(estimator.isSynced()).isTrue();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(frameDurationNs);

    // The estimate should start moving toward the new frame duration. If should not lose sync
    // because the change in frame rate is very small.
    for (int i = 0; i < CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC - 1; i++) {
      framePresentationTimestampNs += newFrameDurationNs;
      estimator.onNextFrame(framePresentationTimestampNs);

      assertThat(estimator.isSynced()).isTrue();
      assertThat(estimator.getFrameDurationNs()).isGreaterThan(frameDurationNs);
      assertThat(estimator.getFrameDurationNs()).isLessThan(newFrameDurationNs);
    }

    framePresentationTimestampNs += newFrameDurationNs;
    estimator.onNextFrame(framePresentationTimestampNs);

    // Frames with the previous frame duration should now be excluded from the estimate, so the
    // estimate should become exact.
    assertThat(estimator.isSynced()).isTrue();
    assertThat(estimator.getFrameDurationNs()).isEqualTo(newFrameDurationNs);
  }

  private static final long getNsWithMsPrecision(long presentationTimeNs) {
    return (presentationTimeNs / 1000000) * 1000000;
  }
}
