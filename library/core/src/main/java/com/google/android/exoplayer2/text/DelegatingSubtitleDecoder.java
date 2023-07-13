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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import java.util.List;

/**
 * Wrapper around a {@link SubtitleParser} that can be used instead of any current {@link
 * SimpleSubtitleDecoder} subclass. The main {@link #decode(byte[], int, boolean)} method will be
 * delegating the parsing of the data to the underlying {@link SubtitleParser} instance and its
 * {@link SubtitleParser#parse(byte[], int, int)} implementation.
 *
 * <p>Functionally, once each XXXDecoder class is refactored to be a XXXParser that implements
 * {@link SubtitleParser}, the following should be equivalent:
 *
 * <ul>
 *   <li>DelegatingSubtitleDecoder("XXX", new XXXParser())
 *   <li>XXXDecoder()
 * </ul>
 *
 * <p>Or in the case with initialization data:
 *
 * <ul>
 *   <li>DelegatingSubtitleDecoder("XXX", new XXXParser(initializationData))
 *   <li>XXXDecoder(initializationData)
 * </ul>
 *
 * <p>TODO(b/289983417): this will only be used in the old decoding flow (Decoder after SampleQueue)
 * while we maintain dual architecture. Once we fully migrate to the pre-SampleQueue flow, it can be
 * deprecated and later deleted.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class DelegatingSubtitleDecoder extends SimpleSubtitleDecoder {

  private static final Subtitle EMPTY = new SubtitleFromCuesWithTiming(ImmutableList.of());
  private final SubtitleParser subtitleParser;

  /* package */ DelegatingSubtitleDecoder(String name, SubtitleParser subtitleParser) {
    super(name);
    this.subtitleParser = subtitleParser;
  }

  @Override
  protected Subtitle decode(byte[] data, int length, boolean reset) {
    if (reset) {
      subtitleParser.reset();
    }
    @Nullable List<CuesWithTiming> cuesWithTiming = subtitleParser.parse(data);
    if (cuesWithTiming == null) {
      return EMPTY;
    }
    return new SubtitleFromCuesWithTiming(cuesWithTiming);
  }

  private static final class SubtitleFromCuesWithTiming implements Subtitle {

    private final ImmutableList<ImmutableList<Cue>> cuesListForUniqueStartTimes;
    private final long[] cuesStartTimesUs;

    /** Ordering of two CuesWithTiming objects based on their startTimeUs values. */
    private static final Ordering<CuesWithTiming> CUES_BY_START_TIME_ASCENDING =
        Ordering.natural().onResultOf(c -> normalizeUnsetStartTimeToZero(c.startTimeUs));

    SubtitleFromCuesWithTiming(List<CuesWithTiming> cuesWithTimingList) {
      this.cuesStartTimesUs = new long[cuesWithTimingList.size()];
      ImmutableList.Builder<ImmutableList<Cue>> cuesListForUniqueStartTimes =
          ImmutableList.builder();
      ImmutableList<CuesWithTiming> sortedCuesWithTimingList =
          ImmutableList.sortedCopyOf(CUES_BY_START_TIME_ASCENDING, cuesWithTimingList);
      for (int i = 0; i < sortedCuesWithTimingList.size(); i++) {
        cuesListForUniqueStartTimes.add(sortedCuesWithTimingList.get(i).cues);
        cuesStartTimesUs[i] =
            normalizeUnsetStartTimeToZero(sortedCuesWithTimingList.get(i).startTimeUs);
      }
      this.cuesListForUniqueStartTimes = cuesListForUniqueStartTimes.build();
    }

    @Override
    public int getNextEventTimeIndex(long timeUs) {
      int index =
          Util.binarySearchCeil(
              cuesStartTimesUs,
              /* value= */ timeUs,
              /* inclusive= */ false,
              /* stayInBounds= */ false);
      return index < cuesStartTimesUs.length ? index : C.INDEX_UNSET;
    }

    @Override
    public int getEventTimeCount() {
      return cuesStartTimesUs.length;
    }

    @Override
    public long getEventTime(int index) {
      return cuesStartTimesUs[index];
    }

    @Override
    public ImmutableList<Cue> getCues(long timeUs) {
      int index =
          Util.binarySearchFloor(
              cuesStartTimesUs,
              /* value= */ timeUs,
              /* inclusive= */ true,
              /* stayInBounds= */ false);
      if (index == -1) {
        // timeUs is earlier than the start of the first List<Cue> in cuesListForUniqueStartTimes.
        return ImmutableList.of();
      } else {
        return cuesListForUniqueStartTimes.get(index);
      }
    }

    // SubtitleParser can return CuesWithTiming with startTimeUs == TIME_UNSET, indicating the
    // start time should be derived from the surrounding sample timestamp. In the context of the
    // Subtitle interface, this means starting at zero, so we can just always interpret TIME_UNSET
    // as zero here.
    private static long normalizeUnsetStartTimeToZero(long startTime) {
      return startTime == C.TIME_UNSET ? 0 : startTime;
    }
  }
}
