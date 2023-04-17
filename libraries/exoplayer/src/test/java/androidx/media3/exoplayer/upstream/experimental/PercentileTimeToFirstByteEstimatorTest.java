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
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSpec;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowSystemClock;

/** Unit tests for {@link PercentileTimeToFirstByteEstimator}. */
@RunWith(AndroidJUnit4.class)
public class PercentileTimeToFirstByteEstimatorTest {

  private PercentileTimeToFirstByteEstimator percentileTimeToResponseEstimator;

  @Before
  public void setUp() {
    percentileTimeToResponseEstimator =
        new PercentileTimeToFirstByteEstimator(/* numberOfSamples= */ 5, /* percentile= */ 0.5f);
  }

  @Test
  public void constructor_invalidNumberOfSamples_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PercentileTimeToFirstByteEstimator(
                /* numberOfSamples= */ 0, /* percentile= */ .2f));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PercentileTimeToFirstByteEstimator(
                /* numberOfSamples= */ -123, /* percentile= */ .2f));
  }

  @Test
  public void constructor_invalidPercentile_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PercentileTimeToFirstByteEstimator(
                /* numberOfSamples= */ 11, /* percentile= */ .0f));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PercentileTimeToFirstByteEstimator(
                /* numberOfSamples= */ 11, /* percentile= */ 1.1f));
  }

  @Test
  public void getTimeToRespondEstimateUs_noSamples_returnsTimeUnset() {
    assertThat(percentileTimeToResponseEstimator.getTimeToFirstByteEstimateUs())
        .isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getTimeToRespondEstimateUs_medianOfOddNumberOfSamples_returnsCenterSampleValue() {
    DataSpec dataSpec = new DataSpec(Uri.EMPTY);

    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(10));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(20));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(30));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(40));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(50));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);

    assertThat(percentileTimeToResponseEstimator.getTimeToFirstByteEstimateUs()).isEqualTo(30_000);
  }

  @Test
  public void
      getTimeToRespondEstimateUs_medianOfEvenNumberOfSamples_returnsLastSampleOfFirstHalfValue() {
    PercentileTimeToFirstByteEstimator percentileTimeToResponseEstimator =
        new PercentileTimeToFirstByteEstimator(/* numberOfSamples= */ 12, /* percentile= */ 0.5f);
    DataSpec dataSpec = new DataSpec(Uri.EMPTY);

    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(10));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(20));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(30));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(40));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);

    assertThat(percentileTimeToResponseEstimator.getTimeToFirstByteEstimateUs()).isEqualTo(20_000);
  }

  @Test
  public void getTimeToRespondEstimateUs_slidingMedian_returnsCenterSampleValue() {
    DataSpec dataSpec = new DataSpec(Uri.EMPTY);

    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(10));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(20));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(30));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(40));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(50));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(60));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(70));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);

    assertThat(percentileTimeToResponseEstimator.getTimeToFirstByteEstimateUs()).isEqualTo(50_000);
  }

  @Test
  public void reset_clearsTheSlidingWindows() {
    DataSpec dataSpec = new DataSpec(Uri.EMPTY);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(10));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);
    percentileTimeToResponseEstimator.onTransferInitializing(dataSpec);
    ShadowSystemClock.advanceBy(Duration.ofMillis(10));
    percentileTimeToResponseEstimator.onTransferStart(dataSpec);

    percentileTimeToResponseEstimator.reset();

    assertThat(percentileTimeToResponseEstimator.getTimeToFirstByteEstimateUs())
        .isEqualTo(C.TIME_UNSET);
  }
}
