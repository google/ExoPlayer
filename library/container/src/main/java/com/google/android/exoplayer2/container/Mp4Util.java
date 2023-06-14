/*
 * Copyright 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.container;

/**
 * Utilities for MP4 container.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class Mp4Util {
  private static final int UNIX_EPOCH_TO_MP4_TIME_DELTA_SECONDS =
      ((1970 - 1904) * 365 + 17 /* leap year */) * (24 * 60 * 60);

  private Mp4Util() {}

  /**
   * Returns an MP4 timestamp (in seconds since midnight, January 1, 1904) from a Unix epoch
   * timestamp (in milliseconds since midnight, January 1, 1970).
   */
  public static long unixTimeToMp4TimeSeconds(long unixTimestampMs) {
    return (unixTimestampMs / 1000L + UNIX_EPOCH_TO_MP4_TIME_DELTA_SECONDS);
  }

  /**
   * Returns a Unix epoch timestamp (in milliseconds since midnight, January 1, 1970) from an MP4
   * timestamp (in seconds since midnight, January 1, 1904).
   */
  public static long mp4TimeToUnixTimeMs(long mp4TimestampSeconds) {
    return (mp4TimestampSeconds - UNIX_EPOCH_TO_MP4_TIME_DELTA_SECONDS) * 1000L;
  }
}
