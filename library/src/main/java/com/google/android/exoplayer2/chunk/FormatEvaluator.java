/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.chunk;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

import java.util.List;
import java.util.Random;

/**
 * Selects from a number of available formats during playback.
 */
public interface FormatEvaluator {
  
  /**
   * A trigger for a load whose reason is unknown or unspecified.
   */
  int TRIGGER_UNKNOWN = 0;
  /**
   * A trigger for a load triggered by an initial format selection.
   */
  int TRIGGER_INITIAL = 1;
  /**
   * A trigger for a load triggered by a user initiated format selection.
   */
  int TRIGGER_MANUAL = 2;
  /**
   * A trigger for a load triggered by an adaptive format selection.
   */
  int TRIGGER_ADAPTIVE = 3;
  /**
   * A trigger for a load triggered whilst in a trick play mode.
   */
  int TRIGGER_TRICK_PLAY = 4;
  /**
   * Applications or extensions may define custom {@code TRIGGER_*} constants greater than or equal
   * to this value.
   */
  int TRIGGER_CUSTOM_BASE = 10000;
  
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
   * initial evaluation), the most recent trigger {@link #TRIGGER_INITIAL} for an initial
   * evaluation) and the most recent evaluation data (null for an initial evaluation).
   *
   * @param bufferedDurationUs The duration of media currently buffered in microseconds.
   * @param blacklistFlags An array whose length is equal to the number of available formats. A
   *     {@code true} element indicates that a format is currently blacklisted and should not be
   *     selected by the evaluation. At least one element must be {@code false}.
   * @param evaluation The evaluation to be updated.
   */
  void evaluateFormat(long bufferedDurationUs, boolean[] blacklistFlags,
      Evaluation evaluation);

  /**
   * Evaluates whether to discard {@link MediaChunk}s from the queue.
   *
   * @param playbackPositionUs The current playback position in microseconds.
   * @param queue The queue of buffered {@link MediaChunk}s.
   * @param blacklistFlags An array whose length is equal to the number of available formats. A
   *     {@code true} element indicates that a format is currently blacklisted and should not be
   *     selected by the evaluation. At least one element must be {@code false}.
   * @return The preferred queue size.
   */
  int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue,
      boolean[] blacklistFlags);

  /**
   * A format evaluation.
   */
  final class Evaluation {

    /**
     * The selected format.
     */
    public Format format;

    /**
     * The sticky reason for the format selection.
     */
    public int trigger;

    /**
     * Sticky optional data relating to the evaluation.
     */
    public Object data;

    public Evaluation() {
      trigger = TRIGGER_INITIAL;
    }

  }

  /**
   * Selects randomly between the available formats, excluding those that are blacklisted.
   */
  final class RandomEvaluator implements FormatEvaluator {

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
    public void evaluateFormat(long bufferedDurationUs, boolean[] blacklistFlags,
        Evaluation evaluation) {
      // Count the number of non-blacklisted formats.
      int nonBlacklistedFormatCount = 0;
      for (boolean blacklistFlag : blacklistFlags) {
        if (!blacklistFlag) {
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
        evaluation.trigger = TRIGGER_ADAPTIVE;
      }
      evaluation.format = newFormat;
    }

    @Override
    public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue,
        boolean[] blacklistFlags) {
      return queue.size();
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
  final class AdaptiveEvaluator implements FormatEvaluator {

    private static final int DEFAULT_MAX_INITIAL_BITRATE = 800000;

    private static final int DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10000;
    private static final int DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 25000;
    private static final int DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 25000;
    private static final float DEFAULT_BANDWIDTH_FRACTION = 0.75f;

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
    public void evaluateFormat(long bufferedDurationUs, boolean[] blacklistFlags,
        Evaluation evaluation) {
      Format current = evaluation.format;
      Format selected = determineIdealFormat(formats, blacklistFlags,
          bandwidthMeter.getBitrateEstimate());
      if (current != null && isEnabledFormat(current, blacklistFlags)) {
        if (selected.bitrate > current.bitrate
            && bufferedDurationUs < minDurationForQualityIncreaseUs) {
          // The ideal format is a higher quality, but we have insufficient buffer to safely switch
          // up. Defer switching up for now.
          selected = current;
        } else if (selected.bitrate < current.bitrate
            && bufferedDurationUs >= maxDurationForQualityDecreaseUs) {
          // The ideal format is a lower quality, but we have sufficient buffer to defer switching
          // down for now.
          selected = current;
        }
      }
      if (current != null && selected != current) {
        evaluation.trigger = TRIGGER_ADAPTIVE;
      }
      evaluation.format = selected;
    }

    @Override
    public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue,
        boolean[] blacklistFlags) {
      if (queue.isEmpty()) {
        return 0;
      }
      int queueSize = queue.size();
      long bufferedDurationUs = queue.get(queueSize - 1).endTimeUs - playbackPositionUs;
      if (bufferedDurationUs < minDurationToRetainAfterDiscardUs) {
        return queueSize;
      }
      Format current = queue.get(queueSize - 1).format;
      Format ideal = determineIdealFormat(formats, blacklistFlags,
          bandwidthMeter.getBitrateEstimate());
      if (ideal.bitrate <= current.bitrate) {
        return queueSize;
      }
      // Discard from the first SD chunk beyond minDurationToRetainAfterDiscardUs whose resolution
      // and bitrate are both lower than the ideal format.
      for (int i = 0; i < queueSize; i++) {
        MediaChunk thisChunk = queue.get(i);
        long durationBeforeThisSegmentUs = thisChunk.startTimeUs - playbackPositionUs;
        if (durationBeforeThisSegmentUs >= minDurationToRetainAfterDiscardUs
            && thisChunk.format.bitrate < ideal.bitrate
            && thisChunk.format.height < ideal.height
            && thisChunk.format.height < 720
            && thisChunk.format.width < 1280) {
          // Discard chunks from this one onwards.
          return i;
        }
      }
      return queueSize;
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

    private boolean isEnabledFormat(Format format, boolean[] blacklistFlags) {
      for (int i = 0; i < formats.length; i++) {
        if (format == formats[i]) {
          return !blacklistFlags[i];
        }
      }
      return false;
    }

  }

}
