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
package com.google.android.exoplayer2.upstream.experimental;

/**
 * The interface for different bandwidth estimation statistics.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface BandwidthStatistic {

  /**
   * Adds a transfer sample to the statistic.
   *
   * @param bytes The number of bytes transferred.
   * @param durationUs The duration of the transfer, in microseconds.
   */
  void addSample(long bytes, long durationUs);

  /**
   * Returns the bandwidth estimate in bits per second, or {@link
   * BandwidthEstimator#ESTIMATE_NOT_AVAILABLE} if there is no estimate available yet.
   */
  long getBandwidthEstimate();

  /**
   * Resets the statistic. The statistic should drop all samples and reset to its initial state,
   * similar to right after construction.
   */
  void reset();
}
