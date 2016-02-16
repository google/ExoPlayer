/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.chunk;

import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.upstream.BandwidthMeter;

import java.util.List;
import java.util.Random;

/**
 * Selects from a number of available formats during playback.
 */
public interface FormatEvaluator {

  /**
   * Enables the evaluator.
   *
   * @param formats The formats from which to select, ordered by decreasing bandwidth.
   */
  void enable(Format[] formats);

  /**
   * Disables the evaluator.
   */
  void disable();

  /**
   * Update the supplied evaluation.
   * <p>
   * When invoked, {@code evaluation} must contain the currently selected format (null for an
   * initial evaluation), the most recent trigger (@link Chunk#TRIGGER_INITIAL} for an initial
   * evaluation) and the size of {@code queue}. The invocation will update the format and trigger,
   * and may also reduce {@link Evaluation#queueSize} to indicate that chunks should be discarded
   * from the end of the queue to allow re-buffering in a different format. The evaluation will
   * always retain the first chunk in the queue, if there is one.
   *
   * @param queue A read only representation of currently buffered chunks. Must not be empty unless
   *     the evaluation is at the start of playback or immediately follows a seek. All but the first
   *     chunk may be discarded. A caller may pass a singleton list containing only the most
   *     recently buffered chunk in the case that it does not support discarding of chunks.
   * @param playbackPositionUs The current playback position in microseconds.
   * @param switchingOverlapUs If switching format requires downloading overlapping media then this
   *     is the duration of the required overlap in microseconds. 0 otherwise.
   * @param blacklistFlags An array whose length is equal to the number of available formats. A
   *     {@code true} element indicates that a format is currently blacklisted and should not be
   *     selected by the evaluation. At least one element must be {@code false}.
   * @param evaluation The evaluation to be updated.
   */
  void evaluate(List<? extends MediaChunk> queue, long playbackPositionUs,
      long switchingOverlapUs, boolean[] blacklistFlags, Evaluation evaluation);

  /**
   * A format evaluation.
   */
  public static final class Evaluation {

    /**
     * The selected format.
     */
    public Format format;

    /**
     * The sticky reason for the format selection.
     */
    public int trigger;

    /**
     * The desired size of the queue.
     */
    public int queueSize;

    public Evaluation() {
      trigger = Chunk.TRIGGER_INITIAL;
    }

    /**
     * Clears {@link #format} and sets {@link #trigger} to {@link Chunk#TRIGGER_INITIAL}.
     */
    public void clear() {
      format = null;
      trigger = Chunk.TRIGGER_INITIAL;
    }

  }

  /**
   * Selects randomly between the available formats, excluding those that are blacklisted.
   */
  public static final class RandomEvaluator implements FormatEvaluator {

    private final Random random;

    private Format[] formats;

    public RandomEvaluator() {
      this.random = new Random();
    }

    /**
     * @param seed A seed for the underlying random number generator.
     */
    public RandomEvaluator(int seed) {
      this.random = new Random(seed);
    }

    @Override
    public void enable(Format[] formats) {
      this.formats = formats;
    }

    @Override
    public void disable() {
      formats = null;
    }

    @Override
    public void evaluate(List<? extends MediaChunk> queue, long playbackPositionUs,
        long switchingOverlapUs, boolean[] blacklistFlags, Evaluation evaluation) {
      // Count the number of non-blacklisted formats.
      int nonBlacklistedFormatCount = 0;
      for (int i = 0; i < blacklistFlags.length; i++) {
        if (!blacklistFlags[i]) {
          nonBlacklistedFormatCount++;
        }
      }

      int formatIndex = random.nextInt(nonBlacklistedFormatCount);
      if (nonBlacklistedFormatCount != formats.length) {
        // Adjust the format index to account for blacklisted formats.
        nonBlacklistedFormatCount = 0;
        for (int i = 0; i < blacklistFlags.length; i++) {
          if (!blacklistFlags[i] && formatIndex == nonBlacklistedFormatCount++) {
            formatIndex = i;
            break;
          }
        }
      }
      Format newFormat = formats[formatIndex];
      if (evaluation.format != null && evaluation.format != newFormat) {
        evaluation.trigger = Chunk.TRIGGER_ADAPTIVE;
      }
      evaluation.format = newFormat;
    }

  }

  /**
   * An adaptive evaluator for video formats, which attempts to select the best quality possible
   * given the current network conditions and state of the buffer.
   * <p>
   * This implementation should be used for video only, and should not be used for audio. It is a
   * reference implementation only. It is recommended that application developers implement their
   * own adaptive evaluator to more precisely suit their use case.
   */
  public static final class AdaptiveEvaluator implements FormatEvaluator {

    public static final int DEFAULT_MAX_INITIAL_BITRATE = 800000;

    public static final int DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10000;
    public static final int DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 25000;
    public static final int DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 25000;
    public static final float DEFAULT_BANDWIDTH_FRACTION = 0.75f;

    private final BandwidthMeter bandwidthMeter;
    private final int maxInitialBitrate;
    private final long minDurationForQualityIncreaseUs;
    private final long maxDurationForQualityDecreaseUs;
    private final long minDurationToRetainAfterDiscardUs;
    private final float bandwidthFraction;

    private Format[] formats;

    /**
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     */
    public AdaptiveEvaluator(BandwidthMeter bandwidthMeter) {
      this (bandwidthMeter, DEFAULT_MAX_INITIAL_BITRATE,
          DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
          DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
          DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS, DEFAULT_BANDWIDTH_FRACTION);
    }

    /**
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     * @param maxInitialBitrate The maximum bitrate in bits per second that should be assumed
     *     when bandwidthMeter cannot provide an estimate due to playback having only just started.
     * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for
     *     the evaluator to consider switching to a higher quality format.
     * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for
     *     the evaluator to consider switching to a lower quality format.
     * @param minDurationToRetainAfterDiscardMs When switching to a significantly higher quality
     *     format, the evaluator may discard some of the media that it has already buffered at the
     *     lower quality, so as to switch up to the higher quality faster. This is the minimum
     *     duration of media that must be retained at the lower quality.
     * @param bandwidthFraction The fraction of the available bandwidth that the evaluator should
     *     consider available for use. Setting to a value less than 1 is recommended to account
     *     for inaccuracies in the bandwidth estimator.
     */
    public AdaptiveEvaluator(BandwidthMeter bandwidthMeter,
        int maxInitialBitrate,
        int minDurationForQualityIncreaseMs,
        int maxDurationForQualityDecreaseMs,
        int minDurationToRetainAfterDiscardMs,
        float bandwidthFraction) {
      this.bandwidthMeter = bandwidthMeter;
      this.maxInitialBitrate = maxInitialBitrate;
      this.minDurationForQualityIncreaseUs = minDurationForQualityIncreaseMs * 1000L;
      this.maxDurationForQualityDecreaseUs = maxDurationForQualityDecreaseMs * 1000L;
      this.minDurationToRetainAfterDiscardUs = minDurationToRetainAfterDiscardMs * 1000L;
      this.bandwidthFraction = bandwidthFraction;
    }

    @Override
    public void enable(Format[] formats) {
      this.formats = formats;
    }

    @Override
    public void disable() {
      formats = null;
    }

    @Override
    public void evaluate(List<? extends MediaChunk> queue, long playbackPositionUs,
        long switchingOverlapUs, boolean[] blacklistFlags, Evaluation evaluation) {
      long bufferedDurationUs = queue.isEmpty() ? 0
          : queue.get(queue.size() - 1).endTimeUs - playbackPositionUs;
      if (switchingOverlapUs > 0) {
        bufferedDurationUs = Math.max(0, bufferedDurationUs - switchingOverlapUs);
      }
      Format current = evaluation.format;
      Format ideal = determineIdealFormat(formats, blacklistFlags,
          bandwidthMeter.getBitrateEstimate());
      boolean isHigher = ideal != null && current != null && ideal.bitrate > current.bitrate;
      boolean isLower = ideal != null && current != null && ideal.bitrate < current.bitrate;
      if (isHigher) {
        if (bufferedDurationUs < minDurationForQualityIncreaseUs) {
          // The ideal format is a higher quality, but we have insufficient buffer to
          // safely switch up. Defer switching up for now.
          ideal = current;
        } else if (bufferedDurationUs >= minDurationToRetainAfterDiscardUs) {
          // We're switching from an SD stream to a stream of higher resolution. Consider
          // discarding already buffered media chunks. Specifically, discard media chunks starting
          // from the first one that is of lower bandwidth, lower resolution and that is not HD.
          for (int i = 1; i < queue.size(); i++) {
            MediaChunk thisChunk = queue.get(i);
            long durationBeforeThisSegmentUs = thisChunk.startTimeUs - playbackPositionUs;
            if (durationBeforeThisSegmentUs >= minDurationToRetainAfterDiscardUs
                && thisChunk.format.bitrate < ideal.bitrate
                && thisChunk.format.height < ideal.height
                && thisChunk.format.height < 720
                && thisChunk.format.width < 1280) {
              // Discard chunks from this one onwards.
              evaluation.queueSize = i;
              break;
            }
          }
        }
      } else if (isLower && current != null
        && bufferedDurationUs >= maxDurationForQualityDecreaseUs) {
        // The ideal format is a lower quality, but we have sufficient buffer to defer switching
        // down for now.
        ideal = current;
      }
      if (current != null && ideal != current) {
        evaluation.trigger = Chunk.TRIGGER_ADAPTIVE;
      }
      evaluation.format = ideal;
    }

    /**
     * Compute the ideal format ignoring buffer health.
     */
    private Format determineIdealFormat(Format[] formats, boolean[] blacklistFlags,
        long bitrateEstimate) {
      int lowestBitrateNonBlacklistedIndex = 0;
      long effectiveBitrate = bitrateEstimate == BandwidthMeter.NO_ESTIMATE
          ? maxInitialBitrate : (long) (bitrateEstimate * bandwidthFraction);
      for (int i = 0; i < formats.length; i++) {
        Format format = formats[i];
        if (!blacklistFlags[i]) {
          if (format.bitrate <= effectiveBitrate) {
            return format;
          } else {
            lowestBitrateNonBlacklistedIndex = i;
          }
        }
      }
      return formats[lowestBitrateNonBlacklistedIndex];
    }

  }

}
