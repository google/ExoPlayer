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

import com.google.android.exoplayer2.Timeline;
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
   * Starts preparation of the source.
   *
   * @param listener The listener for source events.
   */
  void prepareSource(Listener listener);

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
