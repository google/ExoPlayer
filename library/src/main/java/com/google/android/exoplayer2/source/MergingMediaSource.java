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

/**
 * Merges multiple {@link MediaPeriod} instances.
 * <p>
 * The {@link MediaSource}s being merged must have known and equal period counts, and may not return
 * {@code null} from {@link #createPeriod(int)}.
 */
public final class MergingMediaSource implements MediaSource {

  private final MediaSource[] mediaSources;
  private final int periodCount;

  /**
   * @param mediaSources The {@link MediaSource}s to merge.
   */
  public MergingMediaSource(MediaSource... mediaSources) {
    this.mediaSources = mediaSources;
    periodCount = mediaSources[0].getPeriodCount();
    Assertions.checkState(periodCount != UNKNOWN_PERIOD_COUNT,
        "Child sources must have known period counts");
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkState(mediaSource.getPeriodCount() == periodCount,
          "Child sources must have equal period counts");
    }
  }

  @Override
  public void prepareSource() {
    for (MediaSource mediaSource : mediaSources) {
      mediaSource.prepareSource();
    }
  }

  @Override
  public int getPeriodCount() {
    return periodCount;
  }

  @Override
  public MediaPeriod createPeriod(int index) {
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

}
