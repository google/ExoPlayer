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

import static androidx.media3.exoplayer.upstream.experimental.ExponentialWeightedAverageTimeToFirstByteEstimator.DEFAULT_SMOOTHING_FACTOR;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSpec;
import androidx.media3.test.utils.FakeClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ExponentialWeightedAverageTimeToFirstByteEstimator}. */
@RunWith(AndroidJUnit4.class)
public class ExponentialWeightedAverageTimeToFirstByteEstimatorTest {

  @Test
  public void timeToFirstByteEstimate_afterConstruction_notAvailable() {
    ExponentialWeightedAverageTimeToFirstByteEstimator estimator =
        new ExponentialWeightedAverageTimeToFirstByteEstimator();

    assertThat(estimator.getTimeToFirstByteEstimateUs()).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void timeToFirstByteEstimate_afterReset_notAvailable() {
    FakeClock clock = new FakeClock(0);
    ExponentialWeightedAverageTimeToFirstByteEstimator estimator =
        new ExponentialWeightedAverageTimeToFirstByteEstimator(DEFAULT_SMOOTHING_FACTOR, clock);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();

    // Initialize and start two transfers.
    estimator.onTransferInitializing(dataSpec);
    clock.advanceTime(10);
    estimator.onTransferStart(dataSpec);
    // Second transfer.
    estimator.onTransferInitializing(dataSpec);
    clock.advanceTime(10);
    estimator.onTransferStart(dataSpec);
    assertThat(estimator.getTimeToFirstByteEstimateUs()).isGreaterThan(0);
    estimator.reset();

    assertThat(estimator.getTimeToFirstByteEstimateUs()).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void timeToFirstByteEstimate_afterTwoSamples_returnsEstimate() {
    FakeClock clock = new FakeClock(0);
    ExponentialWeightedAverageTimeToFirstByteEstimator estimator =
        new ExponentialWeightedAverageTimeToFirstByteEstimator(DEFAULT_SMOOTHING_FACTOR, clock);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();

    // Initialize and start two transfers.
    estimator.onTransferInitializing(dataSpec);
    clock.advanceTime(10);
    estimator.onTransferStart(dataSpec);
    // Second transfer.
    estimator.onTransferInitializing(dataSpec);
    clock.advanceTime(5);
    estimator.onTransferStart(dataSpec);

    // (0.85 * 10ms) + (0.15 * 5ms) = 9.25ms => 9250us
    assertThat(estimator.getTimeToFirstByteEstimateUs()).isEqualTo(9250);
  }

  @Test
  public void timeToFirstByteEstimate_withUserDefinedSmoothingFactor_returnsEstimate() {
    FakeClock clock = new FakeClock(0);
    ExponentialWeightedAverageTimeToFirstByteEstimator estimator =
        new ExponentialWeightedAverageTimeToFirstByteEstimator(/* smoothingFactor= */ 0.9, clock);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();

    // Initialize and start two transfers.
    estimator.onTransferInitializing(dataSpec);
    clock.advanceTime(10);
    estimator.onTransferStart(dataSpec);
    // Second transfer.
    estimator.onTransferInitializing(dataSpec);
    clock.advanceTime(5);
    estimator.onTransferStart(dataSpec);

    // (0.9 * 10ms) + (0.1 * 5ms) = 9.5ms => 9500 us
    assertThat(estimator.getTimeToFirstByteEstimateUs()).isEqualTo(9500);
  }
}
