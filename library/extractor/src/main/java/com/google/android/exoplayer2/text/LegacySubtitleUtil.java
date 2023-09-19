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
package com.google.android.exoplayer2.text;

import static java.lang.Math.max;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Consumer;
import java.util.List;

/**
 * Utility methods for working with legacy {@link Subtitle} objects.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public class LegacySubtitleUtil {

  private LegacySubtitleUtil() {}

  /**
   * Converts a {@link Subtitle} to a list of {@link CuesWithTiming} representing it, emitted to
   * {@code output}.
   *
   * <p>This may only be called with {@link Subtitle} instances where the first event is non-empty
   * and the last event is an empty cue list.
   */
  public static void toCuesWithTiming(
      Subtitle subtitle,
      SubtitleParser.OutputOptions outputOptions,
      Consumer<CuesWithTiming> output) {
    int startIndex =
        outputOptions.startTimeUs != C.TIME_UNSET
            ? max(subtitle.getNextEventTimeIndex(outputOptions.startTimeUs) - 1, 0)
            : 0;
    for (int i = startIndex; i < subtitle.getEventTimeCount(); i++) {
      outputSubtitleEvent(subtitle, i, output);
    }
    if (outputOptions.outputAllCues) {
      for (int i = 0; i < startIndex; i++) {
        outputSubtitleEvent(subtitle, i, output);
      }
    }
  }

  private static void outputSubtitleEvent(
      Subtitle subtitle, int eventIndex, Consumer<CuesWithTiming> output) {
    long startTimeUs = subtitle.getEventTime(eventIndex);
    List<Cue> cuesForThisStartTime = subtitle.getCues(startTimeUs);
    if (cuesForThisStartTime.isEmpty()) {
      // An empty cue list has already been implicitly encoded in the duration of the previous
      // sample.
      return;
    } else if (eventIndex == subtitle.getEventTimeCount() - 1) {
      // The last cue list must be empty
      throw new IllegalStateException();
    }
    // It's safe to inspect element i+1, because we already exited the loop above if
    // i == getEventTimeCount() - 1.
    long durationUs = subtitle.getEventTime(eventIndex + 1) - subtitle.getEventTime(eventIndex);
    output.accept(new CuesWithTiming(cuesForThisStartTime, startTimeUs, durationUs));
  }
}
