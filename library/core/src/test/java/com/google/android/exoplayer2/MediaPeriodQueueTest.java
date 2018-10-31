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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import android.net.Uri;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.SinglePeriodAdTimeline;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.Allocator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link MediaPeriodQueue}. */
@RunWith(RobolectricTestRunner.class)
public final class MediaPeriodQueueTest {

  private static final long CONTENT_DURATION_US = 30 * C.MICROS_PER_SECOND;
  private static final long FIRST_AD_START_TIME_US = 10 * C.MICROS_PER_SECOND;
  private static final long SECOND_AD_START_TIME_US = 20 * C.MICROS_PER_SECOND;

  private static final Timeline CONTENT_TIMELINE =
      new SinglePeriodTimeline(CONTENT_DURATION_US, /* isSeekable= */ true, /* isDynamic= */ false);
  private static final Uri AD_URI = Uri.EMPTY;

  private MediaPeriodQueue mediaPeriodQueue;
  private AdPlaybackState adPlaybackState;
  private Timeline timeline;
  private Object periodUid;

  private PlaybackInfo playbackInfo;
  private RendererCapabilities[] rendererCapabilities;
  private TrackSelector trackSelector;
  private Allocator allocator;
  private MediaSource mediaSource;

  @Before
  public void setUp() {
    mediaPeriodQueue = new MediaPeriodQueue();
    mediaSource = mock(MediaSource.class);
    rendererCapabilities = new RendererCapabilities[0];
    trackSelector = mock(TrackSelector.class);
    allocator = mock(Allocator.class);
  }

  @Test
  public void testGetNextMediaPeriodInfo_withoutAds_returnsLastMediaPeriodInfo() {
    setupInitialTimeline(/* initialPositionUs= */ 0);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isLast= */ true);
  }

  @Test
  public void testGetNextMediaPeriodInfo_withPrerollAd_returnsCorrectMediaPeriodInfos() {
    setupInitialTimeline(/* initialPositionUs= */ 0, /* adGroupTimesUs= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    assertNextMediaPeriodInfoIsAd(/* adGroupIndex= */ 0, /* contentPositionUs= */ 0);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isLast= */ true);
  }

  @Test
  public void testGetNextMediaPeriodInfo_withMidrollAds_returnsCorrectMediaPeriodInfos() {
    setupInitialTimeline(
        /* initialPositionUs= */ 0,
        /* adGroupTimesUs= */ FIRST_AD_START_TIME_US,
        SECOND_AD_START_TIME_US);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ 0,
        /* endPositionUs= */ FIRST_AD_START_TIME_US,
        /* durationUs= */ FIRST_AD_START_TIME_US,
        /* isLast= */ false);
    // The next media period info should be null as we haven't loaded the ad yet.
    advance();
    assertNull(getNextMediaPeriodInfo());
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    assertNextMediaPeriodInfoIsAd(
        /* adGroupIndex= */ 0, /* contentPositionUs= */ FIRST_AD_START_TIME_US);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ FIRST_AD_START_TIME_US,
        /* endPositionUs= */ SECOND_AD_START_TIME_US,
        /* durationUs= */ SECOND_AD_START_TIME_US,
        /* isLast= */ false);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    assertNextMediaPeriodInfoIsAd(
        /* adGroupIndex= */ 1, /* contentPositionUs= */ SECOND_AD_START_TIME_US);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ SECOND_AD_START_TIME_US,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isLast= */ true);
  }

  @Test
  public void testGetNextMediaPeriodInfo_withMidrollAndPostroll_returnsCorrectMediaPeriodInfos() {
    setupInitialTimeline(
        /* initialPositionUs= */ 0,
        /* adGroupTimesUs= */ FIRST_AD_START_TIME_US,
        C.TIME_END_OF_SOURCE);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ 0,
        /* endPositionUs= */ FIRST_AD_START_TIME_US,
        /* durationUs= */ FIRST_AD_START_TIME_US,
        /* isLast= */ false);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    assertNextMediaPeriodInfoIsAd(
        /* adGroupIndex= */ 0, /* contentPositionUs= */ FIRST_AD_START_TIME_US);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ FIRST_AD_START_TIME_US,
        /* endPositionUs= */ C.TIME_END_OF_SOURCE,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isLast= */ false);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    assertNextMediaPeriodInfoIsAd(
        /* adGroupIndex= */ 1, /* contentPositionUs= */ CONTENT_DURATION_US);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ CONTENT_DURATION_US,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isLast= */ true);
  }

  @Test
  public void testGetNextMediaPeriodInfo_withPostrollLoadError_returnsEmptyFinalMediaPeriodInfo() {
    setupInitialTimeline(/* initialPositionUs= */ 0, /* adGroupTimesUs= */ C.TIME_END_OF_SOURCE);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_END_OF_SOURCE,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isLast= */ false);
    advance();
    setAdGroupFailedToLoad(/* adGroupIndex= */ 0);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* startPositionUs= */ CONTENT_DURATION_US,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isLast= */ true);
  }

  private void setupInitialTimeline(long initialPositionUs, long... adGroupTimesUs) {
    adPlaybackState =
        new AdPlaybackState(adGroupTimesUs).withContentDurationUs(CONTENT_DURATION_US);
    timeline = new SinglePeriodAdTimeline(CONTENT_TIMELINE, adPlaybackState);
    periodUid = timeline.getUidOfPeriod(/* periodIndex= */ 0);
    mediaPeriodQueue.setTimeline(timeline);
    playbackInfo =
        new PlaybackInfo(
            timeline,
            /* manifest= */ null,
            mediaPeriodQueue.resolveMediaPeriodIdForAds(periodUid, initialPositionUs),
            /* startPositionUs= */ 0,
            /* contentPositionUs= */ 0,
            Player.STATE_READY,
            /* isLoading= */ false,
            /* trackGroups= */ null,
            /* trackSelectorResult= */ null,
            /* loadingMediaPeriodId= */ null,
            /* bufferedPositionUs= */ 0,
            /* totalBufferedDurationUs= */ 0,
            /* positionUs= */ 0);
  }

  private void advance() {
    mediaPeriodQueue.enqueueNextMediaPeriod(
        rendererCapabilities, trackSelector, allocator, mediaSource, getNextMediaPeriodInfo());
    mediaPeriodQueue.advancePlayingPeriod();
  }

  private MediaPeriodInfo getNextMediaPeriodInfo() {
    return mediaPeriodQueue.getNextMediaPeriodInfo(/* rendererPositionUs= */ 0, playbackInfo);
  }

  private void setAdGroupLoaded(int adGroupIndex) {
    adPlaybackState =
        adPlaybackState
            .withAdCount(adGroupIndex, /* adCount= */ 1)
            .withAdUri(adGroupIndex, /* adIndexInAdGroup= */ 0, AD_URI);
    updateTimeline();
  }

  private void setAdGroupFailedToLoad(int adGroupIndex) {
    adPlaybackState =
        adPlaybackState
            .withAdCount(adGroupIndex, /* adCount= */ 1)
            .withAdLoadError(adGroupIndex, /* adIndexInAdGroup= */ 0);
    updateTimeline();
  }

  private void updateTimeline() {
    timeline = new SinglePeriodAdTimeline(CONTENT_TIMELINE, adPlaybackState);
    mediaPeriodQueue.setTimeline(timeline);
  }

  private void assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
      long startPositionUs, long endPositionUs, long durationUs, boolean isLast) {
    assertThat(getNextMediaPeriodInfo())
        .isEqualTo(
            new MediaPeriodInfo(
                new MediaPeriodId(periodUid, /* windowSequenceNumber= */ 0, endPositionUs),
                startPositionUs,
                /* contentPositionUs= */ C.TIME_UNSET,
                durationUs,
                /* isLastInTimelinePeriod= */ isLast,
                /* isFinal= */ isLast));
  }

  private void assertNextMediaPeriodInfoIsAd(int adGroupIndex, long contentPositionUs) {
    assertThat(getNextMediaPeriodInfo())
        .isEqualTo(
            new MediaPeriodInfo(
                new MediaPeriodId(
                    periodUid,
                    adGroupIndex,
                    /* adIndexInAdGroup= */ 0,
                    /* windowSequenceNumber= */ 0),
                /* startPositionUs= */ 0,
                contentPositionUs,
                /* durationUs= */ C.TIME_UNSET,
                /* isLastInTimelinePeriod= */ false,
                /* isFinal= */ false));
  }
}
