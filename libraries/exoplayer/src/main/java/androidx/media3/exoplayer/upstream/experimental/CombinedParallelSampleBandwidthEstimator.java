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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.os.Handler;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * A {@link BandwidthEstimator} that captures a transfer sample each time all parallel transfers
 * end.
 */
@UnstableApi
public class CombinedParallelSampleBandwidthEstimator implements BandwidthEstimator {

  /** A builder to create {@link CombinedParallelSampleBandwidthEstimator} instances. */
  public static class Builder {
    private BandwidthStatistic bandwidthStatistic;
    private int minSamples;
    private long minBytesTransferred;
    private Clock clock;

    /** Creates a new builder instance. */
    public Builder() {
      bandwidthStatistic = new SlidingWeightedAverageBandwidthStatistic();
      clock = Clock.DEFAULT;
    }

    /**
     * Sets the {@link BandwidthStatistic} to be used by the estimator. By default, this is set to a
     * {@link SlidingWeightedAverageBandwidthStatistic}.
     *
     * @param bandwidthStatistic The {@link BandwidthStatistic}.
     * @return This builder for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setBandwidthStatistic(BandwidthStatistic bandwidthStatistic) {
      checkNotNull(bandwidthStatistic);
      this.bandwidthStatistic = bandwidthStatistic;
      return this;
    }

    /**
     * Sets a minimum threshold of samples that need to be taken before the estimator can return a
     * bandwidth estimate. By default, this is set to {@code 0}.
     *
     * @param minSamples The minimum number of samples.
     * @return This builder for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setMinSamples(int minSamples) {
      checkArgument(minSamples >= 0);
      this.minSamples = minSamples;
      return this;
    }

    /**
     * Sets a minimum threshold of bytes that need to be transferred before the estimator can return
     * a bandwidth estimate. By default, this is set to {@code 0}.
     *
     * @param minBytesTransferred The minimum number of transferred bytes.
     * @return This builder for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setMinBytesTransferred(long minBytesTransferred) {
      checkArgument(minBytesTransferred >= 0);
      this.minBytesTransferred = minBytesTransferred;
      return this;
    }

    /**
     * Sets the {@link Clock} used by the estimator. By default, this is set to {@link
     * Clock#DEFAULT}.
     *
     * @param clock The {@link Clock} to be used.
     * @return This builder for convenience.
     */
    @CanIgnoreReturnValue
    @VisibleForTesting
    /* package */ Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    public CombinedParallelSampleBandwidthEstimator build() {
      return new CombinedParallelSampleBandwidthEstimator(this);
    }
  }

  private final BandwidthStatistic bandwidthStatistic;
  private final int minSamples;
  private final long minBytesTransferred;
  private final BandwidthMeter.EventListener.EventDispatcher eventDispatcher;
  private final Clock clock;

  private int streamCount;
  private long sampleStartTimeMs;
  private long sampleBytesTransferred;
  private long bandwidthEstimate;
  private long lastReportedBandwidthEstimate;
  private int totalSamplesAdded;
  private long totalBytesTransferred;

  private CombinedParallelSampleBandwidthEstimator(Builder builder) {
    this.bandwidthStatistic = builder.bandwidthStatistic;
    this.minSamples = builder.minSamples;
    this.minBytesTransferred = builder.minBytesTransferred;
    this.clock = builder.clock;
    eventDispatcher = new BandwidthMeter.EventListener.EventDispatcher();
    bandwidthEstimate = ESTIMATE_NOT_AVAILABLE;
    lastReportedBandwidthEstimate = ESTIMATE_NOT_AVAILABLE;
  }

  @Override
  public void addEventListener(Handler eventHandler, BandwidthMeter.EventListener eventListener) {
    eventDispatcher.addListener(eventHandler, eventListener);
  }

  @Override
  public void removeEventListener(BandwidthMeter.EventListener eventListener) {
    eventDispatcher.removeListener(eventListener);
  }

  @Override
  public void onTransferInitializing(DataSource source) {}

  @Override
  public void onTransferStart(DataSource source) {
    if (streamCount == 0) {
      sampleStartTimeMs = clock.elapsedRealtime();
    }
    streamCount++;
  }

  @Override
  public void onBytesTransferred(DataSource source, int bytesTransferred) {
    sampleBytesTransferred += bytesTransferred;
    totalBytesTransferred += bytesTransferred;
  }

  @Override
  public void onTransferEnd(DataSource source) {
    checkState(streamCount > 0);
    streamCount--;
    if (streamCount > 0) {
      return;
    }
    long nowMs = clock.elapsedRealtime();
    long sampleElapsedTimeMs = (int) (nowMs - sampleStartTimeMs);
    if (sampleElapsedTimeMs > 0) {
      bandwidthStatistic.addSample(sampleBytesTransferred, sampleElapsedTimeMs * 1000);
      totalSamplesAdded++;
      if (totalSamplesAdded > minSamples && totalBytesTransferred > minBytesTransferred) {
        bandwidthEstimate = bandwidthStatistic.getBandwidthEstimate();
      }
      maybeNotifyBandwidthSample(
          (int) sampleElapsedTimeMs, sampleBytesTransferred, bandwidthEstimate);
      sampleBytesTransferred = 0;
    }
  }

  @Override
  public long getBandwidthEstimate() {
    return bandwidthEstimate;
  }

  @Override
  public void onNetworkTypeChange(long newBandwidthEstimate) {
    long nowMs = clock.elapsedRealtime();
    int sampleElapsedTimeMs = streamCount > 0 ? (int) (nowMs - sampleStartTimeMs) : 0;
    maybeNotifyBandwidthSample(sampleElapsedTimeMs, sampleBytesTransferred, newBandwidthEstimate);
    bandwidthStatistic.reset();
    bandwidthEstimate = ESTIMATE_NOT_AVAILABLE;
    sampleStartTimeMs = nowMs;
    sampleBytesTransferred = 0;
    totalSamplesAdded = 0;
    totalBytesTransferred = 0;
  }

  private void maybeNotifyBandwidthSample(
      int elapsedMs, long bytesTransferred, long bandwidthEstimate) {
    if ((bandwidthEstimate == ESTIMATE_NOT_AVAILABLE)
        || (elapsedMs == 0
            && bytesTransferred == 0
            && bandwidthEstimate == lastReportedBandwidthEstimate)) {
      return;
    }
    lastReportedBandwidthEstimate = bandwidthEstimate;
    eventDispatcher.bandwidthSample(elapsedMs, bytesTransferred, bandwidthEstimate);
  }
}
