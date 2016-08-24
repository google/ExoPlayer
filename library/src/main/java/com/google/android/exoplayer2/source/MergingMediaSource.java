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
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Merges multiple {@link MediaPeriod} instances.
 * <p>
 * The {@link MediaSource}s being merged must have final windows and an equal number of periods.
 */
public final class MergingMediaSource implements MediaSource {

  private static final int PERIOD_COUNT_UNSET = -1;

  private final MediaSource[] mediaSources;
  private final Timeline.Window window;

  private int periodCount;

  /**
   * @param mediaSources The {@link MediaSource}s to merge.
   */
  public MergingMediaSource(MediaSource... mediaSources) {
    this.mediaSources = mediaSources;
    window = new Timeline.Window();
    periodCount = PERIOD_COUNT_UNSET;
  }

  @Override
  public void prepareSource(final Listener listener) {
    mediaSources[0].prepareSource(new Listener() {
      @Override
      public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
        checkConsistentTimeline(timeline);
        // All source timelines must match.
        listener.onSourceInfoRefreshed(timeline, manifest);
      }
    });
    for (int i = 1; i < mediaSources.length; i++) {
      mediaSources[i].prepareSource(new Listener() {
        @Override
        public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
          checkConsistentTimeline(timeline);
        }
      });
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    for (MediaSource mediaSource : mediaSources) {
      mediaSource.maybeThrowSourceInfoRefreshError();
    }
  }

  @Override
  public MediaPeriod createPeriod(int index, Callback callback, Allocator allocator,
      long positionUs) {
    MediaPeriod[] periods = new MediaPeriod[mediaSources.length];
    // The periods are only referenced after they have all been prepared.
    MergingMediaPeriod mergingPeriod = new MergingMediaPeriod(callback, periods);
    for (int i = 0; i < periods.length; i++) {
      periods[i] = mediaSources[i].createPeriod(index, mergingPeriod, allocator, positionUs);
      Assertions.checkState(periods[i] != null, "Child source must not return null period");
    }
    return mergingPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MergingMediaPeriod mergingPeriod = (MergingMediaPeriod) mediaPeriod;
    for (int i = 0; i < mediaSources.length; i++) {
      mediaSources[i].releasePeriod(mergingPeriod.periods[i]);
    }
  }

  @Override
  public void releaseSource() {
    for (MediaSource mediaSource : mediaSources) {
      mediaSource.releaseSource();
    }
  }

  private void checkConsistentTimeline(Timeline timeline) {
    int windowCount = timeline.getWindowCount();
    for (int i = 0; i < windowCount; i++) {
      Assertions.checkArgument(!timeline.getWindow(i, window, false).isDynamic);
    }
    int periodCount = timeline.getPeriodCount();
    if (this.periodCount == PERIOD_COUNT_UNSET) {
      this.periodCount = periodCount;
    } else {
      Assertions.checkState(this.periodCount == periodCount);
    }
  }

}
