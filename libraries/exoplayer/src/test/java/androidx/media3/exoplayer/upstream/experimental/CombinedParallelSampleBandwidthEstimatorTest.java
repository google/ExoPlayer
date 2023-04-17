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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.Looper;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.FakeDataSource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.shadows.ShadowLooper;

/** Unite tests for the {@link CombinedParallelSampleBandwidthEstimator}. */
@RunWith(AndroidJUnit4.class)
public class CombinedParallelSampleBandwidthEstimatorTest {

  @Test
  public void builder_setNegativeMinSamples_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CombinedParallelSampleBandwidthEstimator.Builder().setMinSamples(-1));
  }

  @Test
  public void builder_setNegativeMinBytesTransferred_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CombinedParallelSampleBandwidthEstimator.Builder().setMinBytesTransferred(-1));
  }

  @Test
  public void transferEvents_singleTransfer_providesOneSample() {
    FakeClock fakeClock = new FakeClock(0);
    CombinedParallelSampleBandwidthEstimator estimator =
        new CombinedParallelSampleBandwidthEstimator.Builder().setClock(fakeClock).build();
    BandwidthMeter.EventListener eventListener = Mockito.mock(BandwidthMeter.EventListener.class);
    estimator.addEventListener(new Handler(Looper.getMainLooper()), eventListener);
    DataSource source = new FakeDataSource();

    estimator.onTransferInitializing(source);
    fakeClock.advanceTime(10);
    estimator.onTransferStart(source);
    fakeClock.advanceTime(10);
    estimator.onBytesTransferred(source, /* bytesTransferred= */ 200);
    fakeClock.advanceTime(10);
    estimator.onTransferEnd(source);
    ShadowLooper.idleMainLooper();

    assertThat(estimator.getBandwidthEstimate()).isEqualTo(80_000);
    verify(eventListener).onBandwidthSample(20, 200, 80_000);
  }

  @Test
  public void transferEvents_twoParallelTransfers_providesOneSample() {
    FakeClock fakeClock = new FakeClock(0);
    CombinedParallelSampleBandwidthEstimator estimator =
        new CombinedParallelSampleBandwidthEstimator.Builder().setClock(fakeClock).build();
    BandwidthMeter.EventListener eventListener = Mockito.mock(BandwidthMeter.EventListener.class);
    estimator.addEventListener(new Handler(Looper.getMainLooper()), eventListener);
    DataSource source1 = new FakeDataSource();
    DataSource source2 = new FakeDataSource();

    // At time = 10 ms, source1 starts.
    fakeClock.advanceTime(10);
    estimator.onTransferInitializing(source1);
    estimator.onTransferStart(source1);
    // At time 20 ms, source1 reports 200 bytes.
    fakeClock.advanceTime(10);
    estimator.onBytesTransferred(source1, /* bytesTransferred= */ 200);
    // At time = 30 ms, source2 starts.
    fakeClock.advanceTime(10);
    estimator.onTransferInitializing(source2);
    estimator.onTransferStart(source2);
    // At time = 40 ms, both sources report 100 bytes each.
    fakeClock.advanceTime(10);
    estimator.onBytesTransferred(source1, /* bytesTransferred= */ 100);
    estimator.onBytesTransferred(source2, /* bytesTransferred= */ 100);
    // At time = 50 ms, source1 transfer completes. At this point, 400 bytes have been transferred
    // in total between times 10 and 50 ms.
    fakeClock.advanceTime(10);
    estimator.onTransferEnd(source1);
    ShadowLooper.idleMainLooper();

    // Verify no update has been made yet.
    verify(eventListener, never()).onBandwidthSample(anyInt(), anyLong(), anyLong());

    // At time = 60 ms, source2 reports 160 bytes.
    fakeClock.advanceTime(10);
    estimator.onBytesTransferred(source2, /* bytesTransferred= */ 160);
    // At time = 70 ms second transfer completes. At this time, 160 bytes have been
    // transferred between times 50 and 70 ms.
    fakeClock.advanceTime(10);
    estimator.onTransferEnd(source2);
    ShadowLooper.idleMainLooper();

    assertThat(estimator.getBandwidthEstimate()).isEqualTo(74_666);
    verify(eventListener).onBandwidthSample(60, 560, 74_666);
    verifyNoMoreInteractions(eventListener);
  }

  @Test
  public void onNetworkTypeChange_notifiesListener() {
    FakeClock fakeClock = new FakeClock(0);
    CombinedParallelSampleBandwidthEstimator estimator =
        new CombinedParallelSampleBandwidthEstimator.Builder().setClock(fakeClock).build();
    BandwidthMeter.EventListener eventListener = Mockito.mock(BandwidthMeter.EventListener.class);
    estimator.addEventListener(new Handler(Looper.getMainLooper()), eventListener);

    estimator.onNetworkTypeChange(100);
    ShadowLooper.idleMainLooper();

    verify(eventListener).onBandwidthSample(0, 0, 100);
  }

  @Test
  public void minSamplesSet_doesNotReturnEstimateBefore() {
    FakeDataSource source = new FakeDataSource();
    FakeClock fakeClock = new FakeClock(0);
    BandwidthStatistic mockStatistic = mock(BandwidthStatistic.class);
    when(mockStatistic.getBandwidthEstimate()).thenReturn(1234L);
    CombinedParallelSampleBandwidthEstimator estimator =
        new CombinedParallelSampleBandwidthEstimator.Builder()
            .setBandwidthStatistic(mockStatistic)
            .setMinSamples(1)
            .setClock(fakeClock)
            .build();

    // First sample.
    estimator.onTransferInitializing(source);
    estimator.onTransferStart(source);
    fakeClock.advanceTime(10);
    estimator.onBytesTransferred(source, /* bytesTransferred= */ 100);
    fakeClock.advanceTime(10);
    estimator.onTransferEnd(source);
    assertThat(estimator.getBandwidthEstimate())
        .isEqualTo(BandwidthEstimator.ESTIMATE_NOT_AVAILABLE);
    // Second sample.
    fakeClock.advanceTime(10);
    estimator.onTransferInitializing(source);
    estimator.onTransferStart(source);
    fakeClock.advanceTime(10);
    estimator.onBytesTransferred(source, /* bytesTransferred= */ 100);
    fakeClock.advanceTime(10);
    estimator.onTransferEnd(source);

    assertThat(estimator.getBandwidthEstimate()).isEqualTo(1234L);
  }

  @Test
  public void minBytesTransferredSet_doesNotReturnEstimateBefore() {
    FakeDataSource source = new FakeDataSource();
    FakeClock fakeClock = new FakeClock(0);
    BandwidthStatistic mockStatistic = mock(BandwidthStatistic.class);
    when(mockStatistic.getBandwidthEstimate()).thenReturn(1234L);
    CombinedParallelSampleBandwidthEstimator estimator =
        new CombinedParallelSampleBandwidthEstimator.Builder()
            .setBandwidthStatistic(mockStatistic)
            .setMinBytesTransferred(500)
            .setClock(fakeClock)
            .build();

    // First sample transfers 499 bytes.
    estimator.onTransferInitializing(source);
    estimator.onTransferStart(source);
    fakeClock.advanceTime(10);
    estimator.onBytesTransferred(source, /* bytesTransferred= */ 499);
    fakeClock.advanceTime(10);
    estimator.onTransferEnd(source);
    assertThat(estimator.getBandwidthEstimate())
        .isEqualTo(BandwidthEstimator.ESTIMATE_NOT_AVAILABLE);
    // Second sample transfers 100 bytes.
    fakeClock.advanceTime(10);
    estimator.onTransferInitializing(source);
    estimator.onTransferStart(source);
    fakeClock.advanceTime(10);
    estimator.onBytesTransferred(source, /* bytesTransferred= */ 100);
    fakeClock.advanceTime(10);
    estimator.onTransferEnd(source);

    assertThat(estimator.getBandwidthEstimate()).isEqualTo(1234L);
  }
}
