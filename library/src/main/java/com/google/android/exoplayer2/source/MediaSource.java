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

import com.google.android.exoplayer2.source.MediaPeriod.Callback;
import com.google.android.exoplayer2.upstream.Allocator;
import java.io.IOException;

/**
 * A source of media consisting of one or more {@link MediaPeriod}s.
 */
public interface MediaSource {

  /**
   * Listener for source events.
   */
  interface Listener {

    /**
     * Called when manifest and/or timeline has been refreshed.
     *
     * @param timeline The source's timeline.
     * @param manifest The loaded manifest.
     */
    void onSourceInfoRefreshed(Timeline timeline, Object manifest);

  }

  /**
   * A position in the timeline.
   */
  final class Position {

    /**
     * A start position at the earliest time in the first period.
     */
    public static final Position DEFAULT = new Position(0, 0);

    /**
     * The index of the period containing the timeline position.
     */
    public final int periodIndex;

    /**
     * The position in microseconds within the period.
     */
    public final long positionUs;

    /**
     * Creates a new timeline position.
     *
     * @param periodIndex The index of the period containing the timeline position.
     * @param positionUs The position in microseconds within the period.
     */
    public Position(int periodIndex, long positionUs) {
      this.periodIndex = periodIndex;
      this.positionUs = positionUs;
    }

  }

  /**
   * Starts preparation of the source.
   *
   * @param listener The listener for source events.
   */
  void prepareSource(Listener listener);

  /**
   * Returns the period index to play in this source's new timeline, or
   * {@link Timeline#NO_PERIOD_INDEX} if the player should stop playback. The
   * {@code oldPlayingPeriodIndex} should be an index of a period in the old timeline that is no
   * longer present (based on its identifier) in the new timeline.
   *
   * @param oldPlayingPeriodIndex The period index that was being played in the old timeline.
   * @param oldTimeline The old timeline.
   * @return The new period index to play in this source's new timeline. Playback will resume from
   *     the default start position in the new period index.
   */
  int getNewPlayingPeriodIndex(int oldPlayingPeriodIndex, Timeline oldTimeline);

  /**
   * Returns the default {@link Position} that the player should play when when starting to play the
   * period at {@code index}, or {@code null} if the default position is not yet known.
   * <p>
   * For example, sources can return a {@link Position} with the passed period {@code index} to play
   * the period at {@code index} immediately after the period at {@code index - 1}. Sources
   * providing multi-period live streams may return the index and position of the live edge when
   * passed {@code index == 0} to play from the live edge.
   *
   * @param index The index of the requested period.
   * @return The default start position.
   */
  Position getDefaultStartPosition(int index);

  /**
   * Throws any pending error encountered while loading or refreshing source information.
   */
  void maybeThrowSourceInfoRefreshError() throws IOException;

  /**
   * Returns a {@link MediaPeriod} corresponding to the period at the specified index.
   * <p>
   * {@link Callback#onPrepared(MediaPeriod)} is called when the new period is prepared. If
   * preparation fails, {@link MediaPeriod#maybeThrowPrepareError()} will throw an
   * {@link IOException} if called on the returned instance.
   *
   * @param index The index of the period.
   * @param callback A callback to receive updates from the period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param positionUs The player's current playback position.
   * @return A new {@link MediaPeriod}.
   */
  MediaPeriod createPeriod(int index, Callback callback, Allocator allocator, long positionUs);

  /**
   * Releases the period.
   *
   * @param mediaPeriod The period to release.
   */
  void releasePeriod(MediaPeriod mediaPeriod);

  /**
   * Releases the source.
   * <p>
   * This method should be called when the source is no longer required. It may be called in any
   * state.
   */
  void releaseSource();

}
