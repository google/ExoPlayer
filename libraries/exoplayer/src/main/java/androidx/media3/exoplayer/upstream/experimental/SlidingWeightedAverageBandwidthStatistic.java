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

import static androidx.media3.common.util.Util.castNonNull;

import androidx.annotation.VisibleForTesting;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link BandwidthStatistic} that calculates estimates based on a sliding window weighted
 * average.
 */
@UnstableApi
public class SlidingWeightedAverageBandwidthStatistic implements BandwidthStatistic {

  /** Represents a bandwidth sample. */
  public static class Sample {
    /** The sample bitrate. */
    public final long bitrate;

    /** The sample weight. */
    public final double weight;

    /**
     * The time this sample was added, in milliseconds. Timestamps should come from the same source,
     * so that samples can reliably be ordered in time. It is suggested to use {@link
     * Clock#elapsedRealtime()}.
     */
    public final long timeAddedMs;

    /** Creates a new sample. */
    public Sample(long bitrate, double weight, long timeAddedMs) {
      this.bitrate = bitrate;
      this.weight = weight;
      this.timeAddedMs = timeAddedMs;
    }
  }

  /** An interface to decide if samples need to be evicted from the estimator. */
  public interface SampleEvictionFunction {
    /**
     * Whether the sample at the front of the queue needs to be evicted. Called before adding a next
     * sample.
     *
     * @param samples A queue of samples, ordered by {@link Sample#timeAddedMs}. The oldest sample
     *     is at front of the queue. The queue must not be modified.
     */
    boolean shouldEvictSample(Deque<Sample> samples);
  }

  /** Gets a {@link SampleEvictionFunction} that maintains up to {@code maxSamplesCount} samples. */
  public static SampleEvictionFunction getMaxCountEvictionFunction(long maxSamplesCount) {
    return (samples) -> samples.size() >= maxSamplesCount;
  }

  /** Gets a {@link SampleEvictionFunction} that maintains samples up to {@code maxAgeMs}. */
  public static SampleEvictionFunction getAgeBasedEvictionFunction(long maxAgeMs) {
    return getAgeBasedEvictionFunction(maxAgeMs, Clock.DEFAULT);
  }

  @VisibleForTesting
  /* package */ static SampleEvictionFunction getAgeBasedEvictionFunction(
      long maxAgeMs, Clock clock) {
    return (samples) -> {
      if (samples.isEmpty()) {
        return false;
      }
      return castNonNull(samples.peek()).timeAddedMs + maxAgeMs < clock.elapsedRealtime();
    };
  }

  /** The default maximum number of samples. */
  public static final int DEFAULT_MAX_SAMPLES_COUNT = 10;

  private final ArrayDeque<Sample> samples;
  private final SampleEvictionFunction sampleEvictionFunction;
  private final Clock clock;

  private double bitrateWeightProductSum;
  private double weightSum;

  /** Creates an instance that keeps up to {@link #DEFAULT_MAX_SAMPLES_COUNT} samples. */
  public SlidingWeightedAverageBandwidthStatistic() {
    this(getMaxCountEvictionFunction(DEFAULT_MAX_SAMPLES_COUNT));
  }

  /**
   * Creates an instance.
   *
   * @param sampleEvictionFunction The {@link SampleEvictionFunction} deciding whether to drop
   *     samples when new samples are added.
   */
  public SlidingWeightedAverageBandwidthStatistic(SampleEvictionFunction sampleEvictionFunction) {
    this(sampleEvictionFunction, Clock.DEFAULT);
  }

  /**
   * Creates an instance.
   *
   * @param sampleEvictionFunction The {@link SampleEvictionFunction} deciding whether to drop
   *     samples when new samples are added.
   * @param clock The {@link Clock} used.
   */
  @VisibleForTesting
  /* package */ SlidingWeightedAverageBandwidthStatistic(
      SampleEvictionFunction sampleEvictionFunction, Clock clock) {
    this.samples = new ArrayDeque<>();
    this.sampleEvictionFunction = sampleEvictionFunction;
    this.clock = clock;
  }

  @Override
  public void addSample(long bytes, long durationUs) {
    while (sampleEvictionFunction.shouldEvictSample(samples)) {
      Sample sample = samples.remove();
      bitrateWeightProductSum -= sample.bitrate * sample.weight;
      weightSum -= sample.weight;
    }

    double weight = Math.sqrt((double) bytes);
    long bitrate = bytes * 8_000_000 / durationUs;
    Sample sample = new Sample(bitrate, weight, clock.elapsedRealtime());
    samples.add(sample);
    bitrateWeightProductSum += sample.bitrate * sample.weight;
    weightSum += sample.weight;
  }

  @Override
  public long getBandwidthEstimate() {
    if (samples.isEmpty()) {
      return BandwidthEstimator.ESTIMATE_NOT_AVAILABLE;
    }

    return (long) (bitrateWeightProductSum / weightSum);
  }

  @Override
  public void reset() {
    samples.clear();
    bitrateWeightProductSum = 0;
    weightSum = 0;
  }
}
