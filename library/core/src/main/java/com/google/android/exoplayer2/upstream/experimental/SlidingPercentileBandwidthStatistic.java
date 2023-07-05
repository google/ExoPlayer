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

import static com.google.android.exoplayer2.upstream.experimental.BandwidthEstimator.ESTIMATE_NOT_AVAILABLE;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import com.google.android.exoplayer2.util.Util;
import java.util.ArrayDeque;
import java.util.TreeSet;

/**
 * A {@link BandwidthStatistic} that calculates estimates based on a sliding window weighted
 * percentile.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public class SlidingPercentileBandwidthStatistic implements BandwidthStatistic {

  /** The default maximum number of samples. */
  public static final int DEFAULT_MAX_SAMPLES_COUNT = 10;

  /** The default percentile to return. */
  public static final double DEFAULT_PERCENTILE = 0.5;

  private final int maxSampleCount;
  private final double percentile;
  private final ArrayDeque<Sample> samples;
  private final TreeSet<Sample> sortedSamples;

  private double weightSum;
  private long bitrateEstimate;

  /**
   * Creates an instance with a maximum of {@link #DEFAULT_MAX_SAMPLES_COUNT} samples, returning the
   * {@link #DEFAULT_PERCENTILE}.
   */
  public SlidingPercentileBandwidthStatistic() {
    this(DEFAULT_MAX_SAMPLES_COUNT, DEFAULT_PERCENTILE);
  }

  /**
   * Creates an instance.
   *
   * @param maxSampleCount The maximum number of samples.
   * @param percentile The percentile to return. Must be in the range of [0-1].
   */
  public SlidingPercentileBandwidthStatistic(int maxSampleCount, double percentile) {
    checkArgument(percentile >= 0 && percentile <= 1);
    this.maxSampleCount = maxSampleCount;
    this.percentile = percentile;
    this.samples = new ArrayDeque<>();
    this.sortedSamples = new TreeSet<>();
    this.bitrateEstimate = ESTIMATE_NOT_AVAILABLE;
  }

  @Override
  public void addSample(long bytes, long durationUs) {
    while (samples.size() >= maxSampleCount) {
      Sample removedSample = samples.remove();
      sortedSamples.remove(removedSample);
      weightSum -= removedSample.weight;
    }

    double weight = Math.sqrt((double) bytes);
    long bitrate = bytes * 8_000_000 / durationUs;
    Sample sample = new Sample(bitrate, weight);
    samples.add(sample);
    sortedSamples.add(sample);
    weightSum += weight;
    bitrateEstimate = calculateBitrateEstimate();
  }

  @Override
  public long getBandwidthEstimate() {
    return bitrateEstimate;
  }

  @Override
  public void reset() {
    samples.clear();
    sortedSamples.clear();
    weightSum = 0;
    bitrateEstimate = ESTIMATE_NOT_AVAILABLE;
  }

  private long calculateBitrateEstimate() {
    if (samples.isEmpty()) {
      return ESTIMATE_NOT_AVAILABLE;
    }
    double targetWeightSum = weightSum * percentile;
    double previousPartialWeightSum = 0;
    long previousSampleBitrate = 0;
    double nextPartialWeightSum = 0;
    for (Sample sample : sortedSamples) {
      // The percentile position of each sample is the middle of its weight. Hence, we need to add
      // half the weight to check whether the target percentile is before or after this sample.
      nextPartialWeightSum += sample.weight / 2;
      if (nextPartialWeightSum >= targetWeightSum) {
        if (previousSampleBitrate == 0) {
          return sample.bitrate;
        }
        // Interpolate between samples to get an estimate for the target percentile.
        double partialBitrateBetweenSamples =
            (sample.bitrate - previousSampleBitrate)
                * (targetWeightSum - previousPartialWeightSum)
                / (nextPartialWeightSum - previousPartialWeightSum);
        return previousSampleBitrate + (long) partialBitrateBetweenSamples;
      }
      previousSampleBitrate = sample.bitrate;
      previousPartialWeightSum = nextPartialWeightSum;
      nextPartialWeightSum += sample.weight / 2;
    }
    return previousSampleBitrate;
  }

  private static class Sample implements Comparable<Sample> {
    private final long bitrate;
    private final double weight;

    public Sample(long bitrate, double weight) {
      this.bitrate = bitrate;
      this.weight = weight;
    }

    @Override
    public int compareTo(Sample other) {
      return Util.compareLong(this.bitrate, other.bitrate);
    }
  }
}
