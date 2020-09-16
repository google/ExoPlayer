/*
 * Copyright (C) 2017 The Android Open Source Project
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
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.testutil.FakeMediaChunk;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit test for {@link AdaptiveTrackSelection}. */
@RunWith(AndroidJUnit4.class)
public final class AdaptiveTrackSelectionTest {

  private static final MediaChunkIterator[] THREE_EMPTY_MEDIA_CHUNK_ITERATORS =
      new MediaChunkIterator[] {
        MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY
      };

  @Mock private BandwidthMeter mockBandwidthMeter;
  private FakeClock fakeClock;

  @Before
  public void setUp() {
    initMocks(this);
    fakeClock = new FakeClock(0);
  }

  @Test
  public void selectInitialIndexUseMaxInitialBitrateIfNoBandwidthEstimate() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L);
    AdaptiveTrackSelection adaptiveTrackSelection = adaptiveTrackSelection(trackGroup);

    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void selectInitialIndexUseBandwidthEstimateIfAvailable() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(500L);
    AdaptiveTrackSelection adaptiveTrackSelection = adaptiveTrackSelection(trackGroup);

    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format1);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void updateSelectedTrackDoNotSwitchUpIfNotBufferedEnough() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    // The second measurement onward returns 2000L, which prompts the track selection to switch up
    // if possible.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L, 2000L);
    AdaptiveTrackSelection adaptiveTrackSelection =
        adaptiveTrackSelectionWithMinDurationForQualityIncreaseMs(
            trackGroup, /* minDurationForQualityIncreaseMs= */ 10_000);

    adaptiveTrackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ 9_999_000,
        /* availableDurationUs= */ C.TIME_UNSET,
        /* queue= */ Collections.emptyList(),
        /* mediaChunkIterators= */ THREE_EMPTY_MEDIA_CHUNK_ITERATORS);

    // When bandwidth estimation is updated to 2000L, we can switch up to use a higher bitrate
    // format. However, since we only buffered 9_999_000 us, which is smaller than
    // minDurationForQualityIncreaseMs, we should defer switch up.
    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void updateSelectedTrackSwitchUpIfBufferedEnough() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    // The second measurement onward returns 2000L, which prompts the track selection to switch up
    // if possible.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L, 2000L);
    AdaptiveTrackSelection adaptiveTrackSelection =
        adaptiveTrackSelectionWithMinDurationForQualityIncreaseMs(
            trackGroup, /* minDurationForQualityIncreaseMs= */ 10_000);

    adaptiveTrackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ 10_000_000,
        /* availableDurationUs= */ C.TIME_UNSET,
        /* queue= */ Collections.emptyList(),
        /* mediaChunkIterators= */ THREE_EMPTY_MEDIA_CHUNK_ITERATORS);

    // When bandwidth estimation is updated to 2000L, we can switch up to use a higher bitrate
    // format. When we have buffered enough (10_000_000 us, which is equal to
    // minDurationForQualityIncreaseMs), we should switch up now.
    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format3);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_ADAPTIVE);
  }

  @Test
  public void updateSelectedTrackDoNotSwitchDownIfBufferedEnough() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    // The second measurement onward returns 500L, which prompts the track selection to switch down
    // if necessary.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L, 500L);
    AdaptiveTrackSelection adaptiveTrackSelection =
        adaptiveTrackSelectionWithMaxDurationForQualityDecreaseMs(
            trackGroup, /* maxDurationForQualityDecreaseMs= */ 25_000);

    adaptiveTrackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ 25_000_000,
        /* availableDurationUs= */ C.TIME_UNSET,
        /* queue= */ Collections.emptyList(),
        /* mediaChunkIterators= */ THREE_EMPTY_MEDIA_CHUNK_ITERATORS);

    // When bandwidth estimation is updated to 500L, we should switch down to use a lower bitrate
    // format. However, since we have enough buffer at higher quality (25_000_000 us, which is equal
    // to maxDurationForQualityDecreaseMs), we should defer switch down.
    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void updateSelectedTrackSwitchDownIfNotBufferedEnough() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    // The second measurement onward returns 500L, which prompts the track selection to switch down
    // if necessary.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L, 500L);
    AdaptiveTrackSelection adaptiveTrackSelection =
        adaptiveTrackSelectionWithMaxDurationForQualityDecreaseMs(
            trackGroup, /* maxDurationForQualityDecreaseMs= */ 25_000);

    adaptiveTrackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ 24_999_000,
        /* availableDurationUs= */ C.TIME_UNSET,
        /* queue= */ Collections.emptyList(),
        /* mediaChunkIterators= */ THREE_EMPTY_MEDIA_CHUNK_ITERATORS);

    // When bandwidth estimation is updated to 500L, we should switch down to use a lower bitrate
    // format. When we don't have enough buffer at higher quality (24_999_000 us is smaller than
    // maxDurationForQualityDecreaseMs), we should switch down now.
    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format1);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_ADAPTIVE);
  }

  @Test
  public void evaluateQueueSizeReturnQueueSizeIfBandwidthIsNotImproved() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    FakeMediaChunk chunk1 =
        new FakeMediaChunk(format1, /* startTimeUs= */ 0, /* endTimeUs= */ 10_000_000);
    FakeMediaChunk chunk2 =
        new FakeMediaChunk(format1, /* startTimeUs= */ 10_000_000, /* endTimeUs= */ 20_000_000);
    FakeMediaChunk chunk3 =
        new FakeMediaChunk(format1, /* startTimeUs= */ 20_000_000, /* endTimeUs= */ 30_000_000);
    List<FakeMediaChunk> queue = new ArrayList<>();
    queue.add(chunk1);
    queue.add(chunk2);
    queue.add(chunk3);

    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(500L);
    AdaptiveTrackSelection adaptiveTrackSelection = adaptiveTrackSelection(trackGroup);

    int size = adaptiveTrackSelection.evaluateQueueSize(0, queue);
    assertThat(size).isEqualTo(3);
  }

  @Test
  public void evaluateQueueSizeDoNotReevaluateUntilAfterMinTimeBetweenBufferReevaluation() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    FakeMediaChunk chunk1 =
        new FakeMediaChunk(format1, /* startTimeUs= */ 0, /* endTimeUs= */ 10_000_000);
    FakeMediaChunk chunk2 =
        new FakeMediaChunk(format1, /* startTimeUs= */ 10_000_000, /* endTimeUs= */ 20_000_000);
    FakeMediaChunk chunk3 =
        new FakeMediaChunk(format1, /* startTimeUs= */ 20_000_000, /* endTimeUs= */ 30_000_000);
    List<FakeMediaChunk> queue = new ArrayList<>();
    queue.add(chunk1);
    queue.add(chunk2);
    queue.add(chunk3);

    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(500L);
    AdaptiveTrackSelection adaptiveTrackSelection =
        adaptiveTrackSelectionWithMinTimeBetweenBufferReevaluationMs(
            trackGroup, /* durationToRetainAfterDiscardMs= */ 15_000);

    int initialQueueSize = adaptiveTrackSelection.evaluateQueueSize(0, queue);

    fakeClock.advanceTime(999);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L);

    // When the bandwidth estimation is updated, we should be able to discard chunks from the end of
    // the queue. However, since the duration since the last evaluation (999ms) is less than 1000ms,
    // we will not reevaluate the queue size and should not discard chunks.
    int newSize = adaptiveTrackSelection.evaluateQueueSize(/* playbackPositionUs= */ 0, queue);
    assertThat(newSize).isEqualTo(initialQueueSize);

    // Verify that the comment above is correct.
    fakeClock.advanceTime(1);
    newSize = adaptiveTrackSelection.evaluateQueueSize(/* playbackPositionUs= */ 0, queue);
    assertThat(newSize).isLessThan(initialQueueSize);
  }

  @Test
  public void evaluateQueueSizeRetainMoreThanMinimumDurationAfterDiscard() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    FakeMediaChunk chunk1 =
        new FakeMediaChunk(format1, /* startTimeUs= */ 0, /* endTimeUs= */ 10_000_000);
    FakeMediaChunk chunk2 =
        new FakeMediaChunk(format1, /* startTimeUs= */ 10_000_000, /* endTimeUs= */ 20_000_000);
    FakeMediaChunk chunk3 =
        new FakeMediaChunk(format1, /* startTimeUs= */ 20_000_000, /* endTimeUs= */ 30_000_000);
    List<FakeMediaChunk> queue = new ArrayList<>();
    queue.add(chunk1);
    queue.add(chunk2);
    queue.add(chunk3);

    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(500L);
    AdaptiveTrackSelection adaptiveTrackSelection =
        adaptiveTrackSelectionWithMinTimeBetweenBufferReevaluationMs(
            trackGroup, /* durationToRetainAfterDiscardMs= */ 15_000);

    int initialQueueSize = adaptiveTrackSelection.evaluateQueueSize(0, queue);
    assertThat(initialQueueSize).isEqualTo(3);

    fakeClock.advanceTime(2000);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L);

    // When bandwidth estimation is updated and time has advanced enough, we can discard chunks at
    // the end of the queue now.
    // However, since duration to retain after discard = 15 000 ms, we need to retain at least the
    // first 2 chunks
    int newSize = adaptiveTrackSelection.evaluateQueueSize(0, queue);
    assertThat(newSize).isEqualTo(2);
  }

  @Test
  public void updateSelectedTrack_usesFormatOfLastChunkInTheQueueForSelection() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    TrackGroup trackGroup = new TrackGroup(format1, format2);
    AdaptiveTrackSelection adaptiveTrackSelection =
        new AdaptiveTrackSelection.Factory(
                /* minDurationForQualityIncreaseMs= */ 10_000,
                /* maxDurationForQualityDecreaseMs= */ 10_000,
                /* minDurationToRetainAfterDiscardMs= */ 25_000,
                /* bandwidthFraction= */ 1f)
            .createAdaptiveTrackSelection(
                trackGroup,
                mockBandwidthMeter,
                /* tracks= */ new int[] {0, 1},
                /* totalFixedTrackBandwidth= */ 0);

    // Make initial selection.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L);
    prepareTrackSelection(adaptiveTrackSelection);

    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);

    // Ensure that track selection wants to switch down due to low bandwidth.
    FakeMediaChunk chunk1 =
        new FakeMediaChunk(
            format2, /* startTimeUs= */ 0, /* endTimeUs= */ 2_000_000, C.SELECTION_REASON_INITIAL);
    FakeMediaChunk chunk2 =
        new FakeMediaChunk(
            format2,
            /* startTimeUs= */ 2_000_000,
            /* endTimeUs= */ 4_000_000,
            C.SELECTION_REASON_INITIAL);
    List<FakeMediaChunk> queue = ImmutableList.of(chunk1, chunk2);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(500L);
    adaptiveTrackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ 4_000_000,
        /* availableDurationUs= */ C.TIME_UNSET,
        queue,
        /* mediaChunkIterators= */ THREE_EMPTY_MEDIA_CHUNK_ITERATORS);

    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format1);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_ADAPTIVE);

    // Assert that an improved bandwidth selects the last chunk's format and ignores the previous
    // decision. Switching up from the previous decision wouldn't be possible yet because the
    // buffered duration is less than minDurationForQualityIncreaseMs.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L);
    adaptiveTrackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ 4_000_000,
        /* availableDurationUs= */ C.TIME_UNSET,
        queue,
        /* mediaChunkIterators= */ THREE_EMPTY_MEDIA_CHUNK_ITERATORS);

    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void updateSelectedTrack_withQueueOfUnknownFormats_doesntThrow() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    TrackGroup trackGroup = new TrackGroup(format1, format2);
    AdaptiveTrackSelection adaptiveTrackSelection =
        prepareTrackSelection(adaptiveTrackSelection(trackGroup));
    Format unknownFormat = videoFormat(/* bitrate= */ 42, /* width= */ 300, /* height= */ 123);
    FakeMediaChunk chunk =
        new FakeMediaChunk(unknownFormat, /* startTimeUs= */ 0, /* endTimeUs= */ 2_000_000);
    List<FakeMediaChunk> queue = ImmutableList.of(chunk);

    adaptiveTrackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ 2_000_000,
        /* availableDurationUs= */ C.TIME_UNSET,
        queue,
        /* mediaChunkIterators= */ THREE_EMPTY_MEDIA_CHUNK_ITERATORS);

    assertThat(adaptiveTrackSelection.getSelectedFormat()).isAnyOf(format1, format2);
  }

  private AdaptiveTrackSelection adaptiveTrackSelection(TrackGroup trackGroup) {
    return adaptiveTrackSelectionWithMinDurationForQualityIncreaseMs(
        trackGroup, AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS);
  }

  private AdaptiveTrackSelection adaptiveTrackSelectionWithMinDurationForQualityIncreaseMs(
      TrackGroup trackGroup, long minDurationForQualityIncreaseMs) {
    return prepareTrackSelection(
        new AdaptiveTrackSelection(
            trackGroup,
            selectedAllTracksInGroup(trackGroup),
            mockBandwidthMeter,
            /* reservedBandwidth= */ 0,
            minDurationForQualityIncreaseMs,
            AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
            /* bandwidthFraction= */ 1.0f,
            AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
            fakeClock));
  }

  private AdaptiveTrackSelection adaptiveTrackSelectionWithMaxDurationForQualityDecreaseMs(
      TrackGroup trackGroup, long maxDurationForQualityDecreaseMs) {
    return prepareTrackSelection(
        new AdaptiveTrackSelection(
            trackGroup,
            selectedAllTracksInGroup(trackGroup),
            mockBandwidthMeter,
            /* reservedBandwidth= */ 0,
            AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            maxDurationForQualityDecreaseMs,
            AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
            /* bandwidthFraction= */ 1.0f,
            AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
            fakeClock));
  }

  private AdaptiveTrackSelection adaptiveTrackSelectionWithMinTimeBetweenBufferReevaluationMs(
      TrackGroup trackGroup, long durationToRetainAfterDiscardMs) {
    return prepareTrackSelection(
        new AdaptiveTrackSelection(
            trackGroup,
            selectedAllTracksInGroup(trackGroup),
            mockBandwidthMeter,
            /* reservedBandwidth= */ 0,
            AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            durationToRetainAfterDiscardMs,
            /* bandwidthFraction= */ 1.0f,
            AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
            fakeClock));
  }

  private AdaptiveTrackSelection prepareTrackSelection(
      AdaptiveTrackSelection adaptiveTrackSelection) {
    adaptiveTrackSelection.enable();
    adaptiveTrackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ 0,
        /* availableDurationUs= */ C.TIME_UNSET,
        /* queue= */ Collections.emptyList(),
        /* mediaChunkIterators= */ THREE_EMPTY_MEDIA_CHUNK_ITERATORS);
    return adaptiveTrackSelection;
  }

  private int[] selectedAllTracksInGroup(TrackGroup trackGroup) {
    int[] listIndices = new int[trackGroup.length];
    for (int i = 0; i < trackGroup.length; i++) {
      listIndices[i] = i;
    }
    return listIndices;
  }

  private static Format videoFormat(int bitrate, int width, int height) {
    return new Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setAverageBitrate(bitrate)
        .setWidth(width)
        .setHeight(height)
        .build();
  }
}
