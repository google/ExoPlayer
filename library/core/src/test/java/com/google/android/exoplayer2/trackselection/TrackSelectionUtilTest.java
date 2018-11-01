/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.trackselection;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.support.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.BaseMediaChunkIterator;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.upstream.DataSpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** {@link TrackSelectionUtil} tests. */
@RunWith(RobolectricTestRunner.class)
public class TrackSelectionUtilTest {

  public static final long MAX_DURATION_US = 30 * C.MICROS_PER_SECOND;

  @Test
  public void getAverageBitrate_emptyIterator_returnsNoValue() {
    assertThat(TrackSelectionUtil.getAverageBitrate(MediaChunkIterator.EMPTY, MAX_DURATION_US))
        .isEqualTo(Format.NO_VALUE);
  }

  @Test
  public void getAverageBitrate_oneChunk_returnsChunkBitrate() {
    long[] chunkTimeBoundariesSec = {12, 17};
    long[] chunkLengths = {10};

    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US)).isEqualTo(16);
  }

  @Test
  public void getAverageBitrate_multipleSameDurationChunks_returnsAverageChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5, 10};
    long[] chunkLengths = {10, 20};

    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US)).isEqualTo(24);
  }

  @Test
  public void getAverageBitrate_multipleDifferentDurationChunks_returnsAverageChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 30};
    long[] chunkLengths = {10, 20, 30};

    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US)).isEqualTo(16);
  }

  @Test
  public void getAverageBitrate_firstChunkLengthUnset_returnsNoValue() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 30};
    long[] chunkLengths = {C.LENGTH_UNSET, 20, 30};

    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US))
        .isEqualTo(Format.NO_VALUE);
  }

  @Test
  public void getAverageBitrate_secondChunkLengthUnset_returnsFirstChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 30};
    long[] chunkLengths = {10, C.LENGTH_UNSET, 30};

    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US)).isEqualTo(16);
  }

  @Test
  public void
      getAverageBitrate_chunksExceedingMaxDuration_returnsAverageChunkBitrateUpToMaxDuration() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 45, 50};
    long[] chunkLengths = {10, 20, 30, 100};

    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, 30 * C.MICROS_PER_SECOND))
        .isEqualTo(12);
  }

  @Test
  public void getAverageBitrate_zeroMaxDuration_returnsNoValue() {
    long[] chunkTimeBoundariesSec = {0, 5, 10};
    long[] chunkLengths = {10, 20};

    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, /* maxDurationUs= */ 0))
        .isEqualTo(Format.NO_VALUE);
  }

  @Test
  public void getAverageBitrates_noIterator_returnsEmptyArray() {
    assertThat(
            TrackSelectionUtil.getAverageBitrates(
                new MediaChunkIterator[0], new Format[0], MAX_DURATION_US))
        .hasLength(0);
  }

  @Test
  public void getAverageBitrates_emptyIterator_returnsNoValue() {
    int[] averageBitrates =
        TrackSelectionUtil.getAverageBitrates(
            new MediaChunkIterator[] {MediaChunkIterator.EMPTY},
            new Format[] {createFormatWithBitrate(10)},
            MAX_DURATION_US);

    assertThat(averageBitrates).asList().containsExactly(Format.NO_VALUE);
  }

  @Test
  public void getAverageBitrates_twoTracks_returnsAverageChunkBitrates() {
    FakeIterator iterator1 =
        new FakeIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 10}, /* chunkLengths= */ new long[] {10});
    FakeIterator iterator2 =
        new FakeIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5, 15, 30},
            /* chunkLengths= */ new long[] {10, 20, 30});

    int[] averageBitrates =
        TrackSelectionUtil.getAverageBitrates(
            new MediaChunkIterator[] {iterator1, iterator2},
            new Format[] {createFormatWithBitrate(10), createFormatWithBitrate(20)},
            MAX_DURATION_US);

    assertThat(averageBitrates).asList().containsExactly(8, 16).inOrder();
  }

  @Test
  public void getAverageBitrates_oneEmptyIteratorOneWithChunks_returnsEstimationForEmpty() {
    FakeIterator iterator1 =
        new FakeIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5}, /* chunkLengths= */ new long[] {10});
    Format format1 = createFormatWithBitrate(10);
    MediaChunkIterator iterator2 = MediaChunkIterator.EMPTY;
    Format format2 = createFormatWithBitrate(20);

    int[] averageBitrates =
        TrackSelectionUtil.getAverageBitrates(
            new MediaChunkIterator[] {iterator1, iterator2},
            new Format[] {format1, format2},
            MAX_DURATION_US);

    assertThat(averageBitrates).asList().containsExactly(16, 32).inOrder();
  }

  @Test
  public void getAverageBitrates_formatWithoutBitrate_returnsNoValueForEmpty() {
    FakeIterator iterator1 =
        new FakeIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5}, /* chunkLengths= */ new long[] {10});
    Format format1 = createFormatWithBitrate(10);
    MediaChunkIterator iterator2 = MediaChunkIterator.EMPTY;
    Format format2 = createFormatWithBitrate(Format.NO_VALUE);

    int[] averageBitrates =
        TrackSelectionUtil.getAverageBitrates(
            new MediaChunkIterator[] {iterator1, iterator2},
            new Format[] {format1, format2},
            MAX_DURATION_US);

    assertThat(averageBitrates).asList().containsExactly(16, Format.NO_VALUE).inOrder();
  }

  @NonNull
  private static Format createFormatWithBitrate(int bitrate) {
    return Format.createSampleFormat(null, null, null, bitrate, null);
  }

  private static final class FakeIterator extends BaseMediaChunkIterator {

    private final long[] chunkTimeBoundariesSec;
    private final long[] chunkLengths;

    public FakeIterator(long[] chunkTimeBoundariesSec, long[] chunkLengths) {
      super(/* fromIndex= */ 0, /* toIndex= */ chunkTimeBoundariesSec.length - 2);
      this.chunkTimeBoundariesSec = chunkTimeBoundariesSec;
      this.chunkLengths = chunkLengths;
    }

    @Override
    public DataSpec getDataSpec() {
      checkInBounds();
      return new DataSpec(
          Uri.EMPTY,
          /* absoluteStreamPosition= */ 0,
          chunkLengths[(int) getCurrentIndex()],
          /* key= */ null);
    }

    @Override
    public long getChunkStartTimeUs() {
      checkInBounds();
      return chunkTimeBoundariesSec[(int) getCurrentIndex()] * C.MICROS_PER_SECOND;
    }

    @Override
    public long getChunkEndTimeUs() {
      checkInBounds();
      return chunkTimeBoundariesSec[(int) getCurrentIndex() + 1] * C.MICROS_PER_SECOND;
    }
  }
}
