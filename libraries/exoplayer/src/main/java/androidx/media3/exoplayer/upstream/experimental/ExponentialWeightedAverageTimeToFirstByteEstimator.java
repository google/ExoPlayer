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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.upstream.TimeToFirstByteEstimator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Implementation of {@link TimeToFirstByteEstimator} based on exponential weighted average. */
@UnstableApi
public final class ExponentialWeightedAverageTimeToFirstByteEstimator
    implements TimeToFirstByteEstimator {

  /** The default smoothing factor. */
  public static final double DEFAULT_SMOOTHING_FACTOR = 0.85;

  private static final int MAX_DATA_SPECS = 10;

  private final LinkedHashMap<DataSpec, Long> initializedDataSpecs;
  private final double smoothingFactor;
  private final Clock clock;

  private long estimateUs;

  /** Creates an instance using the {@link #DEFAULT_SMOOTHING_FACTOR}. */
  public ExponentialWeightedAverageTimeToFirstByteEstimator() {
    this(DEFAULT_SMOOTHING_FACTOR, Clock.DEFAULT);
  }

  /**
   * Creates an instance.
   *
   * @param smoothingFactor The exponential weighted average smoothing factor.
   */
  public ExponentialWeightedAverageTimeToFirstByteEstimator(double smoothingFactor) {
    this(smoothingFactor, Clock.DEFAULT);
  }

  /**
   * Creates an instance.
   *
   * @param smoothingFactor The exponential weighted average smoothing factor.
   * @param clock The {@link Clock} used for calculating time samples.
   */
  @VisibleForTesting
  /* package */ ExponentialWeightedAverageTimeToFirstByteEstimator(
      double smoothingFactor, Clock clock) {
    this.smoothingFactor = smoothingFactor;
    this.clock = clock;
    initializedDataSpecs = new FixedSizeLinkedHashMap<>(/* maxSize= */ MAX_DATA_SPECS);
    estimateUs = C.TIME_UNSET;
  }

  @Override
  public long getTimeToFirstByteEstimateUs() {
    return estimateUs;
  }

  @Override
  public void reset() {
    estimateUs = C.TIME_UNSET;
  }

  @Override
  public void onTransferInitializing(DataSpec dataSpec) {
    // Remove to make sure insertion order is updated in case the key already exists.
    initializedDataSpecs.remove(dataSpec);
    initializedDataSpecs.put(dataSpec, Util.msToUs(clock.elapsedRealtime()));
  }

  @Override
  public void onTransferStart(DataSpec dataSpec) {
    @Nullable Long initializationStartUs = initializedDataSpecs.remove(dataSpec);
    if (initializationStartUs == null) {
      return;
    }

    long timeToStartSampleUs = Util.msToUs(clock.elapsedRealtime()) - initializationStartUs;
    if (estimateUs == C.TIME_UNSET) {
      estimateUs = timeToStartSampleUs;
    } else {
      estimateUs =
          (long) (smoothingFactor * estimateUs + (1d - smoothingFactor) * timeToStartSampleUs);
    }
  }

  private static class FixedSizeLinkedHashMap<K, V> extends LinkedHashMap<K, V> {

    private final int maxSize;

    public FixedSizeLinkedHashMap(int maxSize) {
      this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      return size() > maxSize;
    }
  }
}
