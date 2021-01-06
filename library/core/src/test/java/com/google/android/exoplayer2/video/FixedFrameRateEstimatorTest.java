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

  private static final long getNsWithMsPrecision(long presentationTimeNs) {
    return (presentationTimeNs / 1000000) * 1000000;
  }
}
