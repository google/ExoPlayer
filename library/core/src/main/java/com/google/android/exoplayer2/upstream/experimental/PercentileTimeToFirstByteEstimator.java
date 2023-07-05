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
package com.google.android.exoplayer2.upstream.experimental;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.SlidingPercentile;
import com.google.android.exoplayer2.upstream.TimeToFirstByteEstimator;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of {@link TimeToFirstByteEstimator} that returns a configured percentile of a
 * sliding window of collected response times.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class PercentileTimeToFirstByteEstimator implements TimeToFirstByteEstimator {

  /** The default maximum number of samples. */
  public static final int DEFAULT_MAX_SAMPLES_COUNT = 10;

  /** The default percentile to return. */
  public static final float DEFAULT_PERCENTILE = 0.5f;

  private static final int MAX_DATA_SPECS = 10;

  private final LinkedHashMap<DataSpec, Long> initializedDataSpecs;
  private final SlidingPercentile slidingPercentile;
  private final float percentile;
  private final Clock clock;

  private boolean isEmpty;

  /**
   * Creates an instance that keeps up to {@link #DEFAULT_MAX_SAMPLES_COUNT} samples and returns the
   * {@link #DEFAULT_PERCENTILE} percentile.
   */
  public PercentileTimeToFirstByteEstimator() {
    this(DEFAULT_MAX_SAMPLES_COUNT, DEFAULT_PERCENTILE);
  }

  /**
   * Creates an instance.
   *
   * @param numberOfSamples The maximum number of samples to be kept in the sliding window.
   * @param percentile The percentile for estimating the time to the first byte.
   */
  public PercentileTimeToFirstByteEstimator(int numberOfSamples, float percentile) {
    this(numberOfSamples, percentile, Clock.DEFAULT);
  }

  /**
   * Creates an instance.
   *
   * @param numberOfSamples The maximum number of samples to be kept in the sliding window.
   * @param percentile The percentile for estimating the time to the first byte.
   * @param clock The {@link Clock} to use.
   */
  @VisibleForTesting
  /* package */ PercentileTimeToFirstByteEstimator(
      int numberOfSamples, float percentile, Clock clock) {
    checkArgument(numberOfSamples > 0 && percentile > 0 && percentile <= 1);
    this.percentile = percentile;
    this.clock = clock;
    initializedDataSpecs = new FixedSizeLinkedHashMap<>(/* maxSize= */ MAX_DATA_SPECS);
    slidingPercentile = new SlidingPercentile(/* maxWeight= */ numberOfSamples);
    isEmpty = true;
  }

  @Override
  public long getTimeToFirstByteEstimateUs() {
    return !isEmpty ? (long) slidingPercentile.getPercentile(percentile) : C.TIME_UNSET;
  }

  @Override
  public void reset() {
    slidingPercentile.reset();
    isEmpty = true;
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
    slidingPercentile.addSample(
        /* weight= */ 1,
        /* value= */ (float) (Util.msToUs(clock.elapsedRealtime()) - initializationStartUs));
    isEmpty = false;
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
