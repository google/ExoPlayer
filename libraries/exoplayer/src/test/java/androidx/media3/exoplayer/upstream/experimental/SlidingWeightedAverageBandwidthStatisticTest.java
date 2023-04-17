/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.exoplayer.upstream.experimental;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.test.utils.FakeClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SlidingWeightedAverageBandwidthStatistic}. */
@RunWith(AndroidJUnit4.class)
public class SlidingWeightedAverageBandwidthStatisticTest {

  @Test
  public void getBandwidthEstimate_afterConstruction_returnsNoEstimate() {
    SlidingWeightedAverageBandwidthStatistic statistic =
        new SlidingWeightedAverageBandwidthStatistic();

    assertThat(statistic.getBandwidthEstimate())
        .isEqualTo(BandwidthEstimator.ESTIMATE_NOT_AVAILABLE);
  }

  @Test
  public void getBandwidthEstimate_oneSample_returnsEstimate() {
    SlidingWeightedAverageBandwidthStatistic statistic =
        new SlidingWeightedAverageBandwidthStatistic();

    statistic.addSample(/* bytes= */ 10, /* durationUs= */ 10);

    assertThat(statistic.getBandwidthEstimate()).isEqualTo(8_000_000);
  }

  @Test
  public void getBandwidthEstimate_multipleSamples_returnsEstimate() {
    SlidingWeightedAverageBandwidthStatistic statistic =
        new SlidingWeightedAverageBandwidthStatistic();

    // Transfer bytes are chosen so that their weights (square root) is exactly an integer.
    statistic.addSample(/* bytes= */ 400, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 100, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 64, /* durationUs= */ 10);

    assertThat(statistic.getBandwidthEstimate()).isEqualTo(200252631);
  }

  @Test
  public void getBandwidthEstimate_calledMultipleTimes_returnsSameEstimate() {
    SlidingWeightedAverageBandwidthStatistic statistic =
        new SlidingWeightedAverageBandwidthStatistic();

    // Transfer bytes chosen so that their weight (sqrt) is an integer.
    statistic.addSample(/* bytes= */ 400, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 100, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 64, /* durationUs= */ 10);

    assertThat(statistic.getBandwidthEstimate()).isEqualTo(200_252_631);
    assertThat(statistic.getBandwidthEstimate()).isEqualTo(200_252_631);
  }

  @Test
  public void defaultConstructor_estimatorKeepsTenSamples() {
    SlidingWeightedAverageBandwidthStatistic statistic =
        new SlidingWeightedAverageBandwidthStatistic();

    // Add 12 samples, the first two should be discarded
    statistic.addSample(/* bytes= */ 4, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 9, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 16, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 25, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 36, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 49, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 64, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 81, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 100, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 121, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 144, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 169, /* durationUs= */ 10);

    assertThat(statistic.getBandwidthEstimate()).isEqualTo(77_600_000);
  }

  @Test
  public void constructorSetsMaxSamples_estimatorKeepsDefinedSamples() {
    FakeClock fakeClock = new FakeClock(0);
    SlidingWeightedAverageBandwidthStatistic statistic =
        new SlidingWeightedAverageBandwidthStatistic(
            SlidingWeightedAverageBandwidthStatistic.getMaxCountEvictionFunction(2), fakeClock);

    statistic.addSample(/* bytes= */ 10, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 10, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 5, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 5, /* durationUs= */ 10);

    assertThat(statistic.getBandwidthEstimate()).isEqualTo(4_000_000);
  }

  @Test
  public void reset_withSamplesAdded_returnsNoEstimate() {
    SlidingWeightedAverageBandwidthStatistic statistic =
        new SlidingWeightedAverageBandwidthStatistic();

    statistic.addSample(/* bytes= */ 10, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 10, /* durationUs= */ 10);
    statistic.reset();

    assertThat(statistic.getBandwidthEstimate())
        .isEqualTo(BandwidthEstimator.ESTIMATE_NOT_AVAILABLE);
  }

  @Test
  public void ageBasedSampleEvictionFunction_dropsOldSamples() {
    // Create an estimator that keeps samples up to 15 seconds old.
    FakeClock fakeClock = new FakeClock(0);
    SlidingWeightedAverageBandwidthStatistic estimator =
        new SlidingWeightedAverageBandwidthStatistic(
            SlidingWeightedAverageBandwidthStatistic.getAgeBasedEvictionFunction(15_000),
            fakeClock);

    // Add sample at time = 0.99 seconds.
    fakeClock.advanceTime(999);
    estimator.addSample(/* bytes= */ 10, /* durationUs= */ 10);
    // Add sample at time = 1 seconds.
    fakeClock.advanceTime(1);
    estimator.addSample(/* bytes= */ 5, /* durationUs= */ 10);
    // Add sample at time = 5 seconds.
    fakeClock.advanceTime(4_000);
    estimator.addSample(/* bytes= */ 5, /* durationUs= */ 10);
    // Add sample at time = 16 seconds, first sample should be dropped, but second sample should
    // remain.
    fakeClock.advanceTime(11_000);
    estimator.addSample(/* bytes= */ 5, /* durationUs= */ 10);

    assertThat(estimator.getBandwidthEstimate()).isEqualTo(4_000_000);
  }

  @Test
  public void ageBasedSampleEvictionFunction_dropsOldSamples_onlyWhenAddingSamples() {
    // Create an estimator that keeps samples up to 5 seconds old.
    FakeClock fakeClock = new FakeClock(0);
    SlidingWeightedAverageBandwidthStatistic estimator =
        new SlidingWeightedAverageBandwidthStatistic(
            SlidingWeightedAverageBandwidthStatistic.getAgeBasedEvictionFunction(5_000), fakeClock);

    // Add sample at time = 0 seconds.
    estimator.addSample(/* bytes= */ 16, /* durationUs= */ 10);
    // Add sample at time = 4 seconds.
    fakeClock.advanceTime(4_000);
    estimator.addSample(/* bytes= */ 9, /* durationUs= */ 10);
    // Advance clock to 10 seconds, samples should remain
    fakeClock.advanceTime(6_000);

    assertThat(estimator.getBandwidthEstimate()).isEqualTo(10_400_000);
  }
}
