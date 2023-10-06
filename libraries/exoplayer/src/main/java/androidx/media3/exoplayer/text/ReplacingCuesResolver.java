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

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.extractor.text.CuesWithTiming;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;

/**
 * A {@link CuesResolver} which resolves each time to at most one {@link CuesWithTiming} instance.
 *
 * <p>Each {@link CuesWithTiming} is used from its {@linkplain CuesWithTiming#startTimeUs start
 * time} to its {@linkplain CuesWithTiming#endTimeUs end time}, or the start time of the next
 * instance if sooner (or the end time is {@link C#TIME_UNSET}).
 *
 * <p>If the last {@link CuesWithTiming} has an {@linkplain C#TIME_UNSET unset} end time, its used
 * until the end of the playback.
 */
// TODO: b/181312195 - Add memoization
/* package */ final class ReplacingCuesResolver implements CuesResolver {

  /** Sorted by {@link CuesWithTiming#startTimeUs} ascending. */
  private final ArrayList<CuesWithTiming> cuesWithTimingList;

  public ReplacingCuesResolver() {
    cuesWithTimingList = new ArrayList<>();
  }

  @Override
  public boolean addCues(CuesWithTiming cues, long currentPositionUs) {
    checkArgument(cues.startTimeUs != C.TIME_UNSET);
    boolean cuesAreShownAtCurrentTime =
        cues.startTimeUs <= currentPositionUs
            && (cues.endTimeUs == C.TIME_UNSET || currentPositionUs < cues.endTimeUs);
    for (int i = cuesWithTimingList.size() - 1; i >= 0; i--) {
      if (cues.startTimeUs >= cuesWithTimingList.get(i).startTimeUs) {
        cuesWithTimingList.add(i + 1, cues);
        return cuesAreShownAtCurrentTime;
      } else if (cuesWithTimingList.get(i).startTimeUs <= currentPositionUs) {
        // There's a cue that starts after the new cues, but before the current time, meaning
        // the new cues will not be displayed at the current time.
        cuesAreShownAtCurrentTime = false;
      }
    }
    cuesWithTimingList.add(0, cues);
    return cuesAreShownAtCurrentTime;
  }

  @Override
  public ImmutableList<Cue> getCuesAtTimeUs(long timeUs) {
    int indexStartingAfterTimeUs = getIndexOfCuesStartingAfter(timeUs);
    if (indexStartingAfterTimeUs == 0) {
      // Either the first cue starts after timeUs, or the cues list is empty.
      return ImmutableList.of();
    }
    CuesWithTiming cues = cuesWithTimingList.get(indexStartingAfterTimeUs - 1);
    return cues.endTimeUs == C.TIME_UNSET || timeUs < cues.endTimeUs
        ? cues.cues
        : ImmutableList.of();
  }

  @Override
  public void discardCuesBeforeTimeUs(long timeUs) {
    int indexToDiscardTo = getIndexOfCuesStartingAfter(timeUs);
    if (indexToDiscardTo > 0) {
      cuesWithTimingList.subList(0, indexToDiscardTo).clear();
    }
  }

  @Override
  public long getPreviousCueChangeTimeUs(long timeUs) {
    if (cuesWithTimingList.isEmpty() || timeUs < cuesWithTimingList.get(0).startTimeUs) {
      return C.TIME_UNSET;
    }

    for (int i = 1; i < cuesWithTimingList.size(); i++) {
      long nextCuesStartTimeUs = cuesWithTimingList.get(i).startTimeUs;
      if (timeUs == nextCuesStartTimeUs) {
        return nextCuesStartTimeUs;
      }
      if (timeUs < nextCuesStartTimeUs) {
        CuesWithTiming cues = cuesWithTimingList.get(i - 1);
        return cues.endTimeUs != C.TIME_UNSET && cues.endTimeUs <= timeUs
            ? cues.endTimeUs
            : cues.startTimeUs;
      }
    }
    CuesWithTiming lastCues = Iterables.getLast(cuesWithTimingList);
    return lastCues.endTimeUs == C.TIME_UNSET || timeUs < lastCues.endTimeUs
        ? lastCues.startTimeUs
        : lastCues.endTimeUs;
  }

  @Override
  public long getNextCueChangeTimeUs(long timeUs) {
    if (cuesWithTimingList.isEmpty()) {
      return C.TIME_END_OF_SOURCE;
    }
    if (timeUs < cuesWithTimingList.get(0).startTimeUs) {
      return cuesWithTimingList.get(0).startTimeUs;
    }

    for (int i = 1; i < cuesWithTimingList.size(); i++) {
      CuesWithTiming cues = cuesWithTimingList.get(i);
      if (timeUs < cues.startTimeUs) {
        CuesWithTiming previousCues = cuesWithTimingList.get(i - 1);
        return previousCues.endTimeUs != C.TIME_UNSET
                && previousCues.endTimeUs > timeUs
                && previousCues.endTimeUs < cues.startTimeUs
            ? previousCues.endTimeUs
            : cues.startTimeUs;
      }
    }
    CuesWithTiming lastCues = Iterables.getLast(cuesWithTimingList);
    return lastCues.endTimeUs != C.TIME_UNSET && timeUs < lastCues.endTimeUs
        ? lastCues.endTimeUs
        : C.TIME_END_OF_SOURCE;
  }

  @Override
  public void clear() {
    cuesWithTimingList.clear();
  }

  /**
   * Returns the index of the first {@link CuesWithTiming} in {@link #cuesWithTimingList} where
   * {@link CuesWithTiming#startTimeUs} is strictly less than {@code timeUs}.
   *
   * <p>Returns the size of {@link #cuesWithTimingList} if all cues are before timeUs
   */
  private int getIndexOfCuesStartingAfter(long timeUs) {
    for (int i = 0; i < cuesWithTimingList.size(); i++) {
      if (timeUs < cuesWithTimingList.get(i).startTimeUs) {
        return i;
      }
    }
    return cuesWithTimingList.size();
  }
}
