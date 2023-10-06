/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.text;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.extractor.text.CuesWithTiming;
import com.google.common.collect.ImmutableList;

/**
 * A {@code CuesResolver} maps from time to the subtitle cues that should be shown.
 *
 * <p>It also exposes methods for querying when the next and previous change in subtitles is.
 *
 * <p>Different implementations may provide different resolution algorithms.
 */
/* package */ interface CuesResolver {

  /**
   * Adds {@code cues} to this instance, returning whether this changes the cues displayed at {@code
   * currentPositionUs}.
   */
  boolean addCues(CuesWithTiming cues, long currentPositionUs);

  /**
   * Returns the {@linkplain Cue cues} that should be shown at time {@code timeUs}.
   *
   * @param timeUs The time to query, in microseconds.
   * @return The cues that should be shown, ordered by ascending priority for compatibility with
   *     {@link CueGroup#cues}.
   */
  ImmutableList<Cue> getCuesAtTimeUs(long timeUs);

  /**
   * Discards all cues that won't be shown at or after {@code timeUs}.
   *
   * @param timeUs The time to discard cues before, in microseconds.
   */
  void discardCuesBeforeTimeUs(long timeUs);

  /**
   * Returns the time, in microseconds, of the change in {@linkplain #getCuesAtTimeUs(long) cue
   * output} at or before {@code timeUs}.
   *
   * <p>If there's no change before {@code timeUs}, returns {@link C#TIME_UNSET}.
   */
  long getPreviousCueChangeTimeUs(long timeUs);

  /**
   * Returns the time, in microseconds, of the next change in {@linkplain #getCuesAtTimeUs(long) cue
   * output} after {@code timeUs} (exclusive).
   *
   * <p>If there's no change after {@code timeUs}, returns {@link C#TIME_END_OF_SOURCE}.
   */
  long getNextCueChangeTimeUs(long timeUs);

  /** Clears all cues that have been added to this instance. */
  void clear();
}
