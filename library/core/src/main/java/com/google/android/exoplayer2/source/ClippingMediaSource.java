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
package com.google.android.exoplayer2.source;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * {@link MediaSource} that wraps a source and clips its timeline based on specified start/end
 * positions. The wrapped source must consist of a single period.
 */
public final class ClippingMediaSource extends CompositeMediaSource<Void> {

  /** Thrown when a {@link ClippingMediaSource} cannot clip its wrapped source. */
  public static final class IllegalClippingException extends IOException {

    /** The reason clipping failed. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({REASON_INVALID_PERIOD_COUNT, REASON_NOT_SEEKABLE_TO_START, REASON_START_EXCEEDS_END})
    public @interface Reason {}
    /** The wrapped source doesn't consist of a single period. */
    public static final int REASON_INVALID_PERIOD_COUNT = 0;
    /** The wrapped source is not seekable and a non-zero clipping start position was specified. */
    public static final int REASON_NOT_SEEKABLE_TO_START = 1;
    /** The wrapped source ends before the specified clipping start position. */
    public static final int REASON_START_EXCEEDS_END = 2;

    /** The reason clipping failed. */
    public final @Reason int reason;

    /**
     * @param reason The reason clipping failed.
     */
    public IllegalClippingException(@Reason int reason) {
      super("Illegal clipping: " + getReasonDescription(reason));
      this.reason = reason;
    }

    private static String getReasonDescription(@Reason int reason) {
      switch (reason) {
        case REASON_INVALID_PERIOD_COUNT:
          return "invalid period count";
        case REASON_NOT_SEEKABLE_TO_START:
          return "not seekable to start";
        case REASON_START_EXCEEDS_END:
          return "start exceeds end";
        default:
          return "unknown";
      }
    }
  }

  private final MediaSource mediaSource;
  private final long startUs;
  private final long endUs;
  private final boolean enableInitialDiscontinuity;
  private final ArrayList<ClippingMediaPeriod> mediaPeriods;

  private IllegalClippingException clippingError;
  private long periodStartUs;
  private long periodEndUs;

  /**
   * Creates a new clipping source that wraps the specified source.
   *
   * @param mediaSource The single-period source to wrap.
   * @param startPositionUs The start position within {@code mediaSource}'s window at which to start
   *     providing samples, in microseconds.
   * @param endPositionUs The end position within {@code mediaSource}'s window at which to stop
   *     providing samples, in microseconds. Specify {@link C#TIME_END_OF_SOURCE} to provide samples
   *     from the specified start point up to the end of the source. Specifying a position that
   *     exceeds the {@code mediaSource}'s duration will also result in the end of the source not
   *     being clipped.
   */
  public ClippingMediaSource(MediaSource mediaSource, long startPositionUs, long endPositionUs) {
    this(mediaSource, startPositionUs, endPositionUs, true);
  }

  /**
   * Creates a new clipping source that wraps the specified source.
   * <p>
   * If the start point is guaranteed to be a key frame, pass {@code false} to
   * {@code enableInitialPositionDiscontinuity} to suppress an initial discontinuity when a period
   * is first read from.
   *
   * @param mediaSource The single-period source to wrap.
   * @param startPositionUs The start position within {@code mediaSource}'s timeline at which to
   *     start providing samples, in microseconds.
   * @param endPositionUs The end position within {@code mediaSource}'s timeline at which to stop
   *     providing samples, in microseconds. Specify {@link C#TIME_END_OF_SOURCE} to provide samples
   *     from the specified start point up to the end of the source. Specifying a position that
   *     exceeds the {@code mediaSource}'s duration will also result in the end of the source not
   *     being clipped.
   * @param enableInitialDiscontinuity Whether the initial discontinuity should be enabled.
   */
  public ClippingMediaSource(MediaSource mediaSource, long startPositionUs, long endPositionUs,
      boolean enableInitialDiscontinuity) {
    Assertions.checkArgument(startPositionUs >= 0);
    this.mediaSource = Assertions.checkNotNull(mediaSource);
    startUs = startPositionUs;
    endUs = endPositionUs;
    this.enableInitialDiscontinuity = enableInitialDiscontinuity;
    mediaPeriods = new ArrayList<>();
  }

  @Override
  public void prepareSourceInternal(ExoPlayer player, boolean isTopLevelSource) {
    super.prepareSourceInternal(player, isTopLevelSource);
    prepareChildSource(/* id= */ null, mediaSource);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    if (clippingError != null) {
      throw clippingError;
    }
    super.maybeThrowSourceInfoRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    ClippingMediaPeriod mediaPeriod = new ClippingMediaPeriod(
        mediaSource.createPeriod(id, allocator), enableInitialDiscontinuity);
    mediaPeriods.add(mediaPeriod);
    mediaPeriod.setClipping(periodStartUs, periodEndUs);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    Assertions.checkState(mediaPeriods.remove(mediaPeriod));
    mediaSource.releasePeriod(((ClippingMediaPeriod) mediaPeriod).mediaPeriod);
  }

  @Override
  public void releaseSourceInternal() {
    super.releaseSourceInternal();
    clippingError = null;
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      Void id, MediaSource mediaSource, Timeline timeline, @Nullable Object manifest) {
    if (clippingError != null) {
      return;
    }
    ClippingTimeline clippingTimeline;
    try {
      clippingTimeline = new ClippingTimeline(timeline, startUs, endUs);
    } catch (IllegalClippingException e) {
      clippingError = e;
      return;
    }
    refreshSourceInfo(clippingTimeline, manifest);
    long windowPositionInPeriodUs =
        timeline
            .getWindow(/* windowIndex= */ 0, new Timeline.Window())
            .getPositionInFirstPeriodUs();
    periodStartUs = windowPositionInPeriodUs + startUs;
    periodEndUs =
        endUs == C.TIME_END_OF_SOURCE ? C.TIME_END_OF_SOURCE : windowPositionInPeriodUs + endUs;
    int count = mediaPeriods.size();
    for (int i = 0; i < count; i++) {
      mediaPeriods.get(i).setClipping(periodStartUs, periodEndUs);
    }
  }

  /**
   * Provides a clipped view of a specified timeline.
   */
  private static final class ClippingTimeline extends ForwardingTimeline {

    private final long startUs;
    private final long endUs;
    private final long durationUs;

    /**
     * Creates a new clipping timeline that wraps the specified timeline.
     *
     * @param timeline The timeline to clip.
     * @param startUs The number of microseconds to clip from the start of {@code timeline}.
     * @param endUs The end position in microseconds for the clipped timeline relative to the start
     *     of {@code timeline}, or {@link C#TIME_END_OF_SOURCE} to clip no samples from the end.
     * @throws IllegalClippingException If the timeline could not be clipped.
     */
    public ClippingTimeline(Timeline timeline, long startUs, long endUs)
        throws IllegalClippingException {
      super(timeline);
      if (timeline.getPeriodCount() != 1) {
        throw new IllegalClippingException(IllegalClippingException.REASON_INVALID_PERIOD_COUNT);
      }
      Window window = timeline.getWindow(0, new Window(), false);
      long resolvedEndUs = endUs == C.TIME_END_OF_SOURCE ? window.durationUs : endUs;
      if (window.durationUs != C.TIME_UNSET) {
        if (resolvedEndUs > window.durationUs) {
          resolvedEndUs = window.durationUs;
        }
        if (startUs != 0 && !window.isSeekable) {
          throw new IllegalClippingException(IllegalClippingException.REASON_NOT_SEEKABLE_TO_START);
        }
        if (startUs > resolvedEndUs) {
          throw new IllegalClippingException(IllegalClippingException.REASON_START_EXCEEDS_END);
        }
      }
      this.startUs = startUs;
      this.endUs = resolvedEndUs;
      durationUs = resolvedEndUs == C.TIME_UNSET ? C.TIME_UNSET : (resolvedEndUs - startUs);
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds,
        long defaultPositionProjectionUs) {
      timeline.getWindow(/* windowIndex= */ 0, window, setIds, defaultPositionProjectionUs);
      window.positionInFirstPeriodUs = 0;
      window.durationUs = durationUs;
      if (window.defaultPositionUs != C.TIME_UNSET) {
        window.defaultPositionUs = Math.max(window.defaultPositionUs, startUs);
        window.defaultPositionUs = endUs == C.TIME_UNSET ? window.defaultPositionUs
            : Math.min(window.defaultPositionUs, endUs);
        window.defaultPositionUs -= startUs;
      }
      long startMs = C.usToMs(startUs);
      if (window.presentationStartTimeMs != C.TIME_UNSET) {
        window.presentationStartTimeMs += startMs;
      }
      if (window.windowStartTimeMs != C.TIME_UNSET) {
        window.windowStartTimeMs += startMs;
      }
      return window;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      timeline.getPeriod(/* periodIndex= */ 0, period, setIds);
      return period.set(
          period.id, period.uid, /* windowIndex= */ 0, durationUs, /* positionInWindowUs= */ 0);
    }

  }

}
