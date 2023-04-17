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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ExponentialWeightedAverageStatistic}. */
@RunWith(AndroidJUnit4.class)
public class ExponentialWeightedAverageStatisticTest {

  @Test
  public void getBandwidthEstimate_afterConstruction_returnsNoEstimate() {
    ExponentialWeightedAverageStatistic statistic = new ExponentialWeightedAverageStatistic();

    assertThat(statistic.getBandwidthEstimate())
        .isEqualTo(BandwidthEstimator.ESTIMATE_NOT_AVAILABLE);
  }

  @Test
  public void getBandwidthEstimate_oneSample_returnsEstimate() {
    ExponentialWeightedAverageStatistic statistic = new ExponentialWeightedAverageStatistic();

    statistic.addSample(/* bytes= */ 10, /* durationUs= */ 10);

    assertThat(statistic.getBandwidthEstimate()).isEqualTo(8_000_000);
  }

  @Test
  public void getBandwidthEstimate_multipleSamples_returnsEstimate() {
    ExponentialWeightedAverageStatistic statistic =
        new ExponentialWeightedAverageStatistic(/* smoothingFactor= */ 0.9999);

    // Transfer bytes are chosen so that their weights (square root) is exactly an integer.
    statistic.addSample(/* bytes= */ 400, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 100, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 64, /* durationUs= */ 10);

    assertThat(statistic.getBandwidthEstimate()).isEqualTo(319545334);
  }

  @Test
  public void getBandwidthEstimate_calledMultipleTimes_returnsSameEstimate() {
    ExponentialWeightedAverageStatistic statistic =
        new ExponentialWeightedAverageStatistic(/* smoothingFactor= */ 0.9999);

    // Transfer bytes chosen so that their weight (sqrt) is an integer.
    statistic.addSample(/* bytes= */ 400, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 100, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 64, /* durationUs= */ 10);

    assertThat(statistic.getBandwidthEstimate()).isEqualTo(319545334);
    assertThat(statistic.getBandwidthEstimate()).isEqualTo(319545334);
  }

  @Test
  public void reset_withSamplesAdded_returnsNoEstimate() {
    ExponentialWeightedAverageStatistic statistic = new ExponentialWeightedAverageStatistic();

    statistic.addSample(/* bytes= */ 10, /* durationUs= */ 10);
    statistic.addSample(/* bytes= */ 10, /* durationUs= */ 10);
    statistic.reset();

    assertThat(statistic.getBandwidthEstimate())
        .isEqualTo(BandwidthEstimator.ESTIMATE_NOT_AVAILABLE);
  }
}
