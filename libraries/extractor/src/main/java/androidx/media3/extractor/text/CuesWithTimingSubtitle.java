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

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A {@link Subtitle} backed by a list of {@link CuesWithTiming} instances. */
// TODO(b/181312195): Make this package-private when ExoplayerCuesDecoder is deleted.
@UnstableApi
public final class CuesWithTimingSubtitle implements Subtitle {

  private static final String TAG = "CuesWithTimingSubtitle";

  // eventCues and eventTimesUs are parallel collections. eventTimesUs is sorted in ascending
  // order, and eventCues.get(i) contains the the cues for the event at time eventTimesUs[i].
  // eventTimesUs may be longer than eventCues (with padding elements at the end).
  // eventCues.size() is the authoritative source for the number of events in this Subtitle.
  private final ImmutableList<ImmutableList<Cue>> eventCues;
  private final long[] eventTimesUs;

  /** Ordering of two CuesWithTiming objects based on their startTimeUs values. */
  private static final Ordering<CuesWithTiming> CUES_BY_START_TIME_ASCENDING =
      Ordering.natural().onResultOf(c -> normalizeUnsetStartTimeToZero(c.startTimeUs));

  public CuesWithTimingSubtitle(List<CuesWithTiming> cuesWithTimingList) {
    if (cuesWithTimingList.size() == 1) {
      CuesWithTiming cuesWithTiming = Iterables.getOnlyElement(cuesWithTimingList);
      long startTimeUs = normalizeUnsetStartTimeToZero(cuesWithTiming.startTimeUs);
      if (cuesWithTiming.durationUs == C.TIME_UNSET) {
        eventCues = ImmutableList.of(cuesWithTiming.cues);
        eventTimesUs = new long[] {startTimeUs};
      } else {
        eventCues = ImmutableList.of(cuesWithTiming.cues, ImmutableList.of());
        eventTimesUs = new long[] {startTimeUs, startTimeUs + cuesWithTiming.durationUs};
      }
      return;
    }

    eventTimesUs = new long[cuesWithTimingList.size() * 2];
    // Ensure that any unused slots at the end of eventTimesUs remain 'sorted' so don't mess
    // with the binary search.
    Arrays.fill(eventTimesUs, Long.MAX_VALUE);
    ArrayList<ImmutableList<Cue>> eventCues = new ArrayList<>();
    ImmutableList<CuesWithTiming> sortedCuesWithTimingList =
        ImmutableList.sortedCopyOf(CUES_BY_START_TIME_ASCENDING, cuesWithTimingList);
    int eventIndex = 0;
    for (int i = 0; i < sortedCuesWithTimingList.size(); i++) {
      CuesWithTiming cuesWithTiming = sortedCuesWithTimingList.get(i);

      long startTimeUs = normalizeUnsetStartTimeToZero(cuesWithTiming.startTimeUs);
      long endTimeUs = startTimeUs + cuesWithTiming.durationUs;
      if (eventIndex == 0 || eventTimesUs[eventIndex - 1] < startTimeUs) {
        eventTimesUs[eventIndex++] = startTimeUs;
        eventCues.add(cuesWithTiming.cues);
      } else if (eventTimesUs[eventIndex - 1] == startTimeUs
          && eventCues.get(eventIndex - 1).isEmpty()) {
        // The previous CuesWithTiming ends at the same time this one starts, so overwrite the
        // empty cue list with the cues from this one.
        eventCues.set(eventIndex - 1, cuesWithTiming.cues);
      } else {
        Log.w(TAG, "Truncating unsupported overlapping cues.");
        // The previous CuesWithTiming ends after this one starts, so overwrite the empty cue list
        // with the cues from this one.
        eventTimesUs[eventIndex - 1] = startTimeUs;
        eventCues.set(eventIndex - 1, cuesWithTiming.cues);
      }
      if (cuesWithTiming.durationUs != C.TIME_UNSET) {
        eventTimesUs[eventIndex++] = endTimeUs;
        eventCues.add(ImmutableList.of());
      }
    }
    this.eventCues = ImmutableList.copyOf(eventCues);
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    int index =
        Util.binarySearchCeil(
            eventTimesUs, /* value= */ timeUs, /* inclusive= */ false, /* stayInBounds= */ false);
    return index < eventCues.size() ? index : C.INDEX_UNSET;
  }

  @Override
  public int getEventTimeCount() {
    return eventCues.size();
  }

  @Override
  public long getEventTime(int index) {
    checkArgument(index < eventCues.size());
    return eventTimesUs[index];
  }

  @Override
  public ImmutableList<Cue> getCues(long timeUs) {
    int index =
        Util.binarySearchFloor(
            eventTimesUs, /* value= */ timeUs, /* inclusive= */ true, /* stayInBounds= */ false);
    return index == -1 ? ImmutableList.of() : eventCues.get(index);
  }

  // SubtitleParser can return CuesWithTiming with startTimeUs == TIME_UNSET, indicating the
  // start time should be derived from the surrounding sample timestamp. In the context of the
  // Subtitle interface, this means starting at zero, so we can just always interpret TIME_UNSET
  // as zero here.
  private static long normalizeUnsetStartTimeToZero(long startTime) {
    return startTime == C.TIME_UNSET ? 0 : startTime;
  }
}
