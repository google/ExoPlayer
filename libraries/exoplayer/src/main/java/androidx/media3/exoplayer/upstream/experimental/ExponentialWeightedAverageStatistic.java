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

import static androidx.media3.exoplayer.upstream.experimental.BandwidthEstimator.ESTIMATE_NOT_AVAILABLE;

import androidx.media3.common.util.UnstableApi;

/** A {@link BandwidthStatistic} that calculates estimates using an exponential weighted average. */
@UnstableApi
public class ExponentialWeightedAverageStatistic implements BandwidthStatistic {

  /** The default smoothing factor. */
  public static final double DEFAULT_SMOOTHING_FACTOR = 0.9999;

  private final double smoothingFactor;

  private long bitrateEstimate;

  /** Creates an instance with {@link #DEFAULT_SMOOTHING_FACTOR}. */
  public ExponentialWeightedAverageStatistic() {
    this(DEFAULT_SMOOTHING_FACTOR);
  }

  /**
   * Creates an instance.
   *
   * @param smoothingFactor The exponential smoothing factor.
   */
  public ExponentialWeightedAverageStatistic(double smoothingFactor) {
    this.smoothingFactor = smoothingFactor;
    bitrateEstimate = ESTIMATE_NOT_AVAILABLE;
  }

  @Override
  public void addSample(long bytes, long durationUs) {
    long bitrate = bytes * 8_000_000 / durationUs;
    if (bitrateEstimate == ESTIMATE_NOT_AVAILABLE) {
      bitrateEstimate = bitrate;
      return;
    }
    // Weight smoothing factor by sqrt(bytes).
    double factor = Math.pow(smoothingFactor, Math.sqrt((double) bytes));
    bitrateEstimate = (long) (factor * bitrateEstimate + (1f - factor) * bitrate);
  }

  @Override
  public long getBandwidthEstimate() {
    return bitrateEstimate;
  }

  @Override
  public void reset() {
    bitrateEstimate = ESTIMATE_NOT_AVAILABLE;
  }
}
