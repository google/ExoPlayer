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
package com.google.android.exoplayer2;

/**
 * Concatenates multiple {@link MediaSource}s.
 */
public final class ConcatenatingMediaSource implements MediaSource {

  private final MediaSource[] mediaSources;

  /**
   * @param mediaSources The {@link MediaSource}s to concatenate.
   */
  public ConcatenatingMediaSource(MediaSource... mediaSources) {
    this.mediaSources = mediaSources;
  }

  @Override
  public int getPeriodCount() {
    int sourceCount = 0;
    for (MediaSource mediaSource : mediaSources) {
      int count = mediaSource.getPeriodCount();
      if (count == MediaSource.UNKNOWN_PERIOD_COUNT) {
        return UNKNOWN_PERIOD_COUNT;
      }
      sourceCount += count;
    }
    return sourceCount;
  }

  @Override
  public MediaPeriod createPeriod(int index) {
    int sourceCount = 0;
    for (MediaSource mediaSource : mediaSources) {
      int count = mediaSource.getPeriodCount();
      if (count == MediaSource.UNKNOWN_PERIOD_COUNT || index < sourceCount + count) {
        return mediaSource.createPeriod(index - sourceCount);
      }
      sourceCount += count;
    }
    throw new IndexOutOfBoundsException();
  }

}
