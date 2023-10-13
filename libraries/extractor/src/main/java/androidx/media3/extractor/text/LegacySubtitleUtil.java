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
package androidx.media3.extractor.text;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.SubtitleParser.OutputOptions;
import java.util.List;

/** Utility methods for working with legacy {@link Subtitle} objects. */
@UnstableApi
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
      Subtitle subtitle, OutputOptions outputOptions, Consumer<CuesWithTiming> output) {
    int startIndex = getStartIndex(subtitle, outputOptions);
    boolean startedInMiddleOfCue = false;
    if (outputOptions.startTimeUs != C.TIME_UNSET) {
      List<Cue> cuesAtStartTime = subtitle.getCues(outputOptions.startTimeUs);
      long firstEventTimeUs = subtitle.getEventTime(startIndex);
      if (!cuesAtStartTime.isEmpty()
          && startIndex < subtitle.getEventTimeCount()
          && outputOptions.startTimeUs < firstEventTimeUs) {
        output.accept(
            new CuesWithTiming(
                cuesAtStartTime,
                outputOptions.startTimeUs,
                firstEventTimeUs - outputOptions.startTimeUs));
        startedInMiddleOfCue = true;
      }
    }
    for (int i = startIndex; i < subtitle.getEventTimeCount(); i++) {
      outputSubtitleEvent(subtitle, i, output);
    }
    if (outputOptions.outputAllCues) {
      int endIndex = startedInMiddleOfCue ? startIndex - 1 : startIndex;
      for (int i = 0; i < endIndex; i++) {
        outputSubtitleEvent(subtitle, i, output);
      }
      if (startedInMiddleOfCue) {
        output.accept(
            new CuesWithTiming(
                subtitle.getCues(outputOptions.startTimeUs),
                subtitle.getEventTime(endIndex),
                outputOptions.startTimeUs - subtitle.getEventTime(endIndex)));
      }
    }
  }

  private static int getStartIndex(Subtitle subtitle, OutputOptions outputOptions) {
    if (outputOptions.startTimeUs == C.TIME_UNSET) {
      return 0;
    }
    int nextEventTimeIndex = subtitle.getNextEventTimeIndex(outputOptions.startTimeUs);
    if (nextEventTimeIndex == C.INDEX_UNSET) {
      return subtitle.getEventTimeCount();
    }
    if (nextEventTimeIndex > 0
        && subtitle.getEventTime(nextEventTimeIndex - 1) == outputOptions.startTimeUs) {
      nextEventTimeIndex--;
    }
    return nextEventTimeIndex;
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
