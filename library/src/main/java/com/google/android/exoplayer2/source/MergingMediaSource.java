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

import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Merges multiple {@link MediaPeriod} instances.
 * <p>
 * The {@link MediaSource}s being merged must have final timelines and equal period counts.
 */
public final class MergingMediaSource implements MediaSource {

  private final MediaSource[] mediaSources;

  private int periodCount;

  /**
   * @param mediaSources The {@link MediaSource}s to merge.
   */
  public MergingMediaSource(MediaSource... mediaSources) {
    this.mediaSources = mediaSources;
    periodCount = -1;
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
  public int getNewPlayingPeriodIndex(int oldPlayingPeriodIndex, Timeline oldTimeline) {
    return mediaSources[0].getNewPlayingPeriodIndex(oldPlayingPeriodIndex, oldTimeline);
  }

  @Override
  public Position getDefaultStartPosition(int index) {
    return mediaSources[0].getDefaultStartPosition(index);
  }

  @Override
  public MediaPeriod createPeriod(int index) throws IOException {
    MediaPeriod[] periods = new MediaPeriod[mediaSources.length];
    for (int i = 0; i < periods.length; i++) {
      periods[i] = mediaSources[i].createPeriod(index);
      Assertions.checkState(periods[i] != null, "Child source must not return null period");
    }
    return new MergingMediaPeriod(periods);
  }

  @Override
  public void releaseSource() {
    for (MediaSource mediaSource : mediaSources) {
      mediaSource.releaseSource();
    }
  }

  private void checkConsistentTimeline(Timeline timeline) {
    Assertions.checkArgument(timeline.isFinal());
    int periodCount = timeline.getPeriodCount();
    if (this.periodCount == -1) {
      this.periodCount = periodCount;
    } else {
      Assertions.checkState(this.periodCount == periodCount);
    }
  }

}
