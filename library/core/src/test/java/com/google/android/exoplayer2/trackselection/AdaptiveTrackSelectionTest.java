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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit test for {@link AdaptiveTrackSelection}. */
@RunWith(RobolectricTestRunner.class)
public final class AdaptiveTrackSelectionTest {

  private static final MediaChunkIterator[] THREE_EMPTY_MEDIA_CHUNK_ITERATORS =
      new MediaChunkIterator[] {
        MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY
      };

  @Mock private BandwidthMeter mockBandwidthMeter;
  private FakeClock fakeClock;

  private AdaptiveTrackSelection adaptiveTrackSelection;

  @Before
  public void setUp() {
    initMocks(this);
    fakeClock = new FakeClock(0);
  }

  @Test
  public void testFactoryUsesInitiallyProvidedBandwidthMeter() {
    BandwidthMeter initialBandwidthMeter = mock(BandwidthMeter.class);
    BandwidthMeter injectedBandwidthMeter = mock(BandwidthMeter.class);
    Format format = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    @SuppressWarnings("deprecation")
    AdaptiveTrackSelection adaptiveTrackSelection =
        new AdaptiveTrackSelection.Factory(initialBandwidthMeter)
            .createTrackSelection(new TrackGroup(format), injectedBandwidthMeter, /* tracks= */ 0);
    adaptiveTrackSelection.updateSelectedTrack(
        /* playbackPositionUs= */ 0,
        /* bufferedDurationUs= */ 0,
        /* availableDurationUs= */ C.TIME_UNSET,
        /* queue= */ Collections.emptyList(),
        /* mediaChunkIterators= */ new MediaChunkIterator[] {MediaChunkIterator.EMPTY});

    verify(initialBandwidthMeter, atLeastOnce()).getBitrateEstimate();
    verifyZeroInteractions(injectedBandwidthMeter);
  }

  @Test
  public void testSelectInitialIndexUseMaxInitialBitrateIfNoBandwidthEstimate() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L);
    adaptiveTrackSelection = adaptiveTrackSelection(trackGroup);

    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format2);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void testSelectInitialIndexUseBandwidthEstimateIfAvailable() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(500L);
    adaptiveTrackSelection = adaptiveTrackSelection(trackGroup);

    assertThat(adaptiveTrackSelection.getSelectedFormat()).isEqualTo(format1);
    assertThat(adaptiveTrackSelection.getSelectionReason()).isEqualTo(C.SELECTION_REASON_INITIAL);
  }

  @Test
  public void testUpdateSelectedTrackDoNotSwitchUpIfNotBufferedEnough() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    // The second measurement onward returns 2000L, which prompts the track selection to switch up
    // if possible.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L, 2000L);
    adaptiveTrackSelection =
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
  public void testUpdateSelectedTrackSwitchUpIfBufferedEnough() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    // The second measurement onward returns 2000L, which prompts the track selection to switch up
    // if possible.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L, 2000L);
    adaptiveTrackSelection =
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
  public void testUpdateSelectedTrackDoNotSwitchDownIfBufferedEnough() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    // The second measurement onward returns 500L, which prompts the track selection to switch down
    // if necessary.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L, 500L);
    adaptiveTrackSelection =
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
  public void testUpdateSelectedTrackSwitchDownIfNotBufferedEnough() {
    Format format1 = videoFormat(/* bitrate= */ 500, /* width= */ 320, /* height= */ 240);
    Format format2 = videoFormat(/* bitrate= */ 1000, /* width= */ 640, /* height= */ 480);
    Format format3 = videoFormat(/* bitrate= */ 2000, /* width= */ 960, /* height= */ 720);
    TrackGroup trackGroup = new TrackGroup(format1, format2, format3);

    // The second measurement onward returns 500L, which prompts the track selection to switch down
    // if necessary.
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L, 500L);
    adaptiveTrackSelection =
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
  public void testEvaluateQueueSizeReturnQueueSizeIfBandwidthIsNotImproved() {
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
    adaptiveTrackSelection = adaptiveTrackSelection(trackGroup);

    int size = adaptiveTrackSelection.evaluateQueueSize(0, queue);
    assertThat(size).isEqualTo(3);
  }

  @Test
  public void testEvaluateQueueSizeDoNotReevaluateUntilAfterMinTimeBetweenBufferReevaluation() {
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
    adaptiveTrackSelection =
        adaptiveTrackSelectionWithMinTimeBetweenBufferReevaluationMs(
            trackGroup,
            /* durationToRetainAfterDiscardMs= */ 15_000,
            /* minTimeBetweenBufferReevaluationMs= */ 2000);

    int initialQueueSize = adaptiveTrackSelection.evaluateQueueSize(0, queue);

    fakeClock.advanceTime(1999);
    when(mockBandwidthMeter.getBitrateEstimate()).thenReturn(1000L);

    // When bandwidth estimation is updated, we can discard chunks at the end of the queue now.
    // However, since min duration between buffer reevaluation = 2000, we will not reevaluate
    // queue size if time now is only 1999 ms after last buffer reevaluation.
    int newSize = adaptiveTrackSelection.evaluateQueueSize(0, queue);
    assertThat(newSize).isEqualTo(initialQueueSize);
  }

  @Test
  public void testEvaluateQueueSizeRetainMoreThanMinimumDurationAfterDiscard() {
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
    adaptiveTrackSelection =
        adaptiveTrackSelectionWithMinTimeBetweenBufferReevaluationMs(
            trackGroup,
            /* durationToRetainAfterDiscardMs= */ 15_000,
            /* minTimeBetweenBufferReevaluationMs= */ 2000);

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

  private AdaptiveTrackSelection adaptiveTrackSelection(TrackGroup trackGroup) {
    return new AdaptiveTrackSelection(
        trackGroup,
        selectedAllTracksInGroup(trackGroup),
        mockBandwidthMeter,
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
        AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
        /* bandwidthFraction= */ 1.0f,
        AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
        AdaptiveTrackSelection.DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS,
        fakeClock);
  }

  private AdaptiveTrackSelection adaptiveTrackSelectionWithMinDurationForQualityIncreaseMs(
      TrackGroup trackGroup, long minDurationForQualityIncreaseMs) {
    return new AdaptiveTrackSelection(
        trackGroup,
        selectedAllTracksInGroup(trackGroup),
        mockBandwidthMeter,
        minDurationForQualityIncreaseMs,
        AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
        /* bandwidthFraction= */ 1.0f,
        AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
        AdaptiveTrackSelection.DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS,
        fakeClock);
  }

  private AdaptiveTrackSelection adaptiveTrackSelectionWithMaxDurationForQualityDecreaseMs(
      TrackGroup trackGroup, long maxDurationForQualityDecreaseMs) {
    return new AdaptiveTrackSelection(
        trackGroup,
        selectedAllTracksInGroup(trackGroup),
        mockBandwidthMeter,
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
        maxDurationForQualityDecreaseMs,
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
        /* bandwidthFraction= */ 1.0f,
        AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
        AdaptiveTrackSelection.DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS,
        fakeClock);
  }

  private AdaptiveTrackSelection adaptiveTrackSelectionWithMinTimeBetweenBufferReevaluationMs(
      TrackGroup trackGroup,
      long durationToRetainAfterDiscardMs,
      long minTimeBetweenBufferReevaluationMs) {
    return new AdaptiveTrackSelection(
        trackGroup,
        selectedAllTracksInGroup(trackGroup),
        mockBandwidthMeter,
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
        AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
        durationToRetainAfterDiscardMs,
        /* bandwidthFraction= */ 1.0f,
        AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
        minTimeBetweenBufferReevaluationMs,
        fakeClock);
  }

  private int[] selectedAllTracksInGroup(TrackGroup trackGroup) {
    int[] listIndices = new int[trackGroup.length];
    for (int i = 0; i < trackGroup.length; i++) {
      listIndices[i] = i;
    }
    return listIndices;
  }

  private static Format videoFormat(int bitrate, int width, int height) {
    return Format.createVideoSampleFormat(
        /* id= */ null,
        /* sampleMimeType= */ MimeTypes.VIDEO_H264,
        /* codecs= */ null,
        /* bitrate= */ bitrate,
        /* maxInputSize= */ Format.NO_VALUE,
        /* width= */ width,
        /* height= */ height,
        /* frameRate= */ Format.NO_VALUE,
        /* initializationData= */ null,
        /* drmInitData= */ null);
  }

  private static final class FakeMediaChunk extends MediaChunk {

    private static final DataSource DATA_SOURCE = new DefaultHttpDataSource("TEST_AGENT", null);

    public FakeMediaChunk(Format trackFormat, long startTimeUs, long endTimeUs) {
      super(
          DATA_SOURCE,
          new DataSpec(Uri.EMPTY),
          trackFormat,
          C.SELECTION_REASON_ADAPTIVE,
          null,
          startTimeUs,
          endTimeUs,
          0);
    }

    @Override
    public void cancelLoad() {
      // Do nothing.
    }

    @Override
    public void load() throws IOException, InterruptedException {
      // Do nothing.
    }

    @Override
    public boolean isLoadCompleted() {
      return true;
    }
  }
}
