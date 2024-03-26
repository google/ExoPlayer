/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.testutil.FakeMediaPeriod;
import com.google.android.exoplayer2.testutil.MediaSourceTestRunner;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

/** Unit tests for {@link MaskingMediaSource}. */
@RunWith(AndroidJUnit4.class)
public class MaskingMediaSourceTest {
  private static final MediaItem EMPTY_MEDIA_ITEM =
      new MediaItem.Builder().setUri(Uri.EMPTY).build();
  @Rule
  public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock
  private MediaPeriod mockMediaPeriod;


  class FakeDynamicTimelineMediaSource extends BaseMediaSource {

    @Override
    protected void prepareSourceInternal(TransferListener mediaTransferListener) {
    }

    @Override
    protected void releaseSourceInternal() {
    }

    @Override
    public MediaItem getMediaItem() {
      return EMPTY_MEDIA_ITEM;
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() {
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
      return mockMediaPeriod;
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {

    }

    public void setNewSourceInfo(Timeline liveTimeline) {
      refreshSourceInfo(liveTimeline);
    }
  }

  @Before
  public void setupMocks() {

  }

  @Test
  public void onChildSourceInfoRefreshed_withLiveTimeline_initialSeek() throws IOException {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(Uri.EMPTY).build();
    Timeline liveTimeline =
        new SinglePeriodTimeline(
            /* presentationStartTimeMs= */ 0,
            /* windowStartTimeMs= */ 0,
            /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
            /* periodDurationUs= */ 1000 * C.MICROS_PER_SECOND,
            /* windowDurationUs= */ 1000 * C.MICROS_PER_SECOND,
            /* windowPositionInPeriodUs= */ 0,
            /* windowDefaultStartPositionUs= */ 20 * C.MICROS_PER_SECOND,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* manifest= */ null,
            mediaItem,
            mediaItem.liveConfiguration);
    Object periodId = liveTimeline.getUidOfPeriod(0);
    FakeDynamicTimelineMediaSource mediaSource = new FakeDynamicTimelineMediaSource();
    MaskingMediaSource testedMediaSource = new MaskingMediaSource(mediaSource, true);
    MediaSourceTestRunner testRunner = new MediaSourceTestRunner(mediaSource, null);
    try {
      testRunner.runOnPlaybackThread(() -> {
        testedMediaSource.prepareSourceInternal(null);
      });

      // This is the sequence of calls when EPII:
      //  - lazy prepares the initial masked media source
      //  - updatePeriods() creates the first period
      //  - the Timeline update occurs with a live timeline with start position and duration

      testRunner.prepareSourceLazy();

      testRunner.runOnPlaybackThread(() -> {
        int startPositionUs = 20;   // TODO - if this value is 0, the test will fail, of course it should not.
        MaskingMediaPeriod period = testedMediaSource.createPeriod(new MediaSource.MediaPeriodId(periodId), null, startPositionUs);
        mediaSource.setNewSourceInfo(liveTimeline);
        assertThat(period.getPreparePositionOverrideUs()).isEqualTo(startPositionUs);
      });


      testRunner.releaseSource();
    } finally {
      testRunner.release();
    }
  }
}
