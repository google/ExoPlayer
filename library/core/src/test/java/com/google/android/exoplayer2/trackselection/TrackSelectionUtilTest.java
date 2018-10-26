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
import com.google.android.exoplayer2.C;
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
  public void getAverageBitrate_emptyIterator_returnsUnsetLength() {
    assertThat(TrackSelectionUtil.getAverageBitrate(MediaChunkIterator.EMPTY, MAX_DURATION_US))
        .isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void getAverageBitrate_oneChunk_returnsChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5};
    long[] chunkLengths = {10};
    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);
    int expectedAverageBitrate =
        (int) (chunkLengths[0] * C.BITS_PER_BYTE / chunkTimeBoundariesSec[1]);
    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US))
        .isEqualTo(expectedAverageBitrate);
  }

  @Test
  public void getAverageBitrate_multipleSameDurationChunks_returnsAverageChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5, 10};
    long[] chunkLengths = {10, 20};
    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);
    long totalLength = chunkLengths[0] + chunkLengths[1];
    int expectedAverageBitrate = (int) (totalLength * C.BITS_PER_BYTE / chunkTimeBoundariesSec[2]);
    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US))
        .isEqualTo(expectedAverageBitrate);
  }

  @Test
  public void getAverageBitrate_multipleDifferentDurationChunks_returnsAverageChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 30};
    long[] chunkLengths = {10, 20, 30};
    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);
    long totalLength = chunkLengths[0] + chunkLengths[1] + chunkLengths[2];
    int expectedAverageBitrate = (int) (totalLength * C.BITS_PER_BYTE / chunkTimeBoundariesSec[3]);
    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US))
        .isEqualTo(expectedAverageBitrate);
  }

  @Test
  public void getAverageBitrate_firstChunkLengthUnset_returnsUnsetLength() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 30};
    long[] chunkLengths = {C.LENGTH_UNSET, 20, 30};
    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);
    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US))
        .isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void getAverageBitrate_secondChunkLengthUnset_returnsFirstChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 30};
    long[] chunkLengths = {10, C.LENGTH_UNSET, 30};
    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);
    int expectedAverageBitrate =
        (int) (chunkLengths[0] * C.BITS_PER_BYTE / chunkTimeBoundariesSec[1]);
    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US))
        .isEqualTo(expectedAverageBitrate);
  }

  @Test
  public void
      getAverageBitrate_chunksExceedingMaxDuration_returnsAverageChunkBitrateUpToMaxDuration() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 45, 50};
    long[] chunkLengths = {10, 20, 30, 100};
    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);
    // Just half of the third chunk is in the max duration
    long totalLength = chunkLengths[0] + chunkLengths[1] + chunkLengths[2] / 2;
    int expectedAverageBitrate =
        (int) (totalLength * C.BITS_PER_BYTE * C.MICROS_PER_SECOND / MAX_DURATION_US);
    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US))
        .isEqualTo(expectedAverageBitrate);
  }

  @Test
  public void getAverageBitrate_zeroMaxDuration_returnsUnsetLength() {
    long[] chunkTimeBoundariesSec = {0, 5, 10};
    long[] chunkLengths = {10, 20};
    FakeIterator iterator = new FakeIterator(chunkTimeBoundariesSec, chunkLengths);
    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, /* maxDurationUs= */ 0))
        .isEqualTo(C.LENGTH_UNSET);
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
