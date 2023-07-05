/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.muxer;

/**
 * Utilities for MP4 files.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class Mp4Utils {
  /**
   * The maximum length of boxes which have fixed sizes.
   *
   * <p>Technically, we'd know how long they actually are; this upper bound is much simpler to
   * produce though and we'll throw if we overflow anyway.
   */
  public static final int MAX_FIXED_LEAF_BOX_SIZE = 200;

  /**
   * The per-video timebase, used for durations in MVHD and TKHD even if the per-track timebase is
   * different (e.g. typically the sample rate for audio).
   */
  public static final long MVHD_TIMEBASE = 10_000L;

  private Mp4Utils() {}

  /** Converts microseconds to video units, using the provided timebase. */
  public static long vuFromUs(long timestampUs, long videoUnitTimebase) {
    return timestampUs * videoUnitTimebase / 1_000_000L; // (division for us to s conversion)
  }

  /** Converts video units to microseconds, using the provided timebase. */
  public static long usFromVu(long timestampVu, long videoUnitTimebase) {
    return timestampVu * 1_000_000L / videoUnitTimebase;
  }
}
