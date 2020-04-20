/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.ads;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

import android.net.Uri;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSource.MediaSourceCaller;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.ads.AdsLoader.AdViewProvider;
import com.google.android.exoplayer2.source.ads.AdsLoader.EventListener;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.upstream.Allocator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.annotation.LooperMode;

/** Unit tests for {@link AdsMediaSource}. */
@RunWith(AndroidJUnit4.class)
@LooperMode(PAUSED)
public final class AdsMediaSourceTest {

  private static final long PREROLL_AD_DURATION_US = 10 * C.MICROS_PER_SECOND;
  private static final Timeline PREROLL_AD_TIMELINE =
      new SinglePeriodTimeline(
          PREROLL_AD_DURATION_US,
          /* isSeekable= */ true,
          /* isDynamic= */ false,
          /* isLive= */ false);
  private static final Object PREROLL_AD_PERIOD_UID =
      PREROLL_AD_TIMELINE.getUidOfPeriod(/* periodIndex= */ 0);

  private static final long CONTENT_DURATION_US = 30 * C.MICROS_PER_SECOND;
  private static final Timeline CONTENT_TIMELINE =
      new SinglePeriodTimeline(
          CONTENT_DURATION_US, /* isSeekable= */ true, /* isDynamic= */ false, /* isLive= */ false);
  private static final Object CONTENT_PERIOD_UID =
      CONTENT_TIMELINE.getUidOfPeriod(/* periodIndex= */ 0);

  private static final AdPlaybackState AD_PLAYBACK_STATE =
      new AdPlaybackState(/* adGroupTimesUs...= */ 0)
          .withContentDurationUs(CONTENT_DURATION_US)
          .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
          .withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, Uri.EMPTY)
          .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
          .withAdResumePositionUs(/* adResumePositionUs= */ 0);

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private FakeMediaSource contentMediaSource;
  private FakeMediaSource prerollAdMediaSource;
  @Mock private MediaSourceCaller mockMediaSourceCaller;
  private AdsMediaSource adsMediaSource;

  @Before
  public void setUp() {
    // Set up content and ad media sources, passing a null timeline so tests can simulate setting it
    // later.
    contentMediaSource = new FakeMediaSource(/* timeline= */ null);
    prerollAdMediaSource = new FakeMediaSource(/* timeline= */ null);
    MediaSourceFactory adMediaSourceFactory = mock(MediaSourceFactory.class);
    when(adMediaSourceFactory.createMediaSource(any(Uri.class))).thenReturn(prerollAdMediaSource);

    // Prepare the AdsMediaSource and capture its ads loader listener.
    AdsLoader mockAdsLoader = mock(AdsLoader.class);
    AdViewProvider mockAdViewProvider = mock(AdViewProvider.class);
    ArgumentCaptor<EventListener> eventListenerArgumentCaptor =
        ArgumentCaptor.forClass(AdsLoader.EventListener.class);
    adsMediaSource =
        new AdsMediaSource(
            contentMediaSource, adMediaSourceFactory, mockAdsLoader, mockAdViewProvider);
    adsMediaSource.prepareSource(mockMediaSourceCaller, /* mediaTransferListener= */ null);
    shadowOf(Looper.getMainLooper()).idle();
    verify(mockAdsLoader).start(eventListenerArgumentCaptor.capture(), eq(mockAdViewProvider));

    // Simulate loading a preroll ad.
    AdsLoader.EventListener adsLoaderEventListener = eventListenerArgumentCaptor.getValue();
    adsLoaderEventListener.onAdPlaybackState(AD_PLAYBACK_STATE);
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void createPeriod_preparesChildAdMediaSourceAndRefreshesSourceInfo() {
    contentMediaSource.setNewSourceInfo(CONTENT_TIMELINE, null);
    adsMediaSource.createPeriod(
        new MediaPeriodId(
            CONTENT_PERIOD_UID,
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0),
        mock(Allocator.class),
        /* startPositionUs= */ 0);
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(prerollAdMediaSource.isPrepared()).isTrue();
    verify(mockMediaSourceCaller)
        .onSourceInfoRefreshed(
            adsMediaSource, new SinglePeriodAdTimeline(CONTENT_TIMELINE, AD_PLAYBACK_STATE));
  }

  @Test
  public void createPeriod_preparesChildAdMediaSourceAndRefreshesSourceInfoWithAdMediaSourceInfo() {
    contentMediaSource.setNewSourceInfo(CONTENT_TIMELINE, null);
    adsMediaSource.createPeriod(
        new MediaPeriodId(
            CONTENT_PERIOD_UID,
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0),
        mock(Allocator.class),
        /* startPositionUs= */ 0);
    prerollAdMediaSource.setNewSourceInfo(PREROLL_AD_TIMELINE, null);
    shadowOf(Looper.getMainLooper()).idle();

    verify(mockMediaSourceCaller)
        .onSourceInfoRefreshed(
            adsMediaSource,
            new SinglePeriodAdTimeline(
                CONTENT_TIMELINE,
                AD_PLAYBACK_STATE.withAdDurationsUs(new long[][] {{PREROLL_AD_DURATION_US}})));
  }

  @Test
  public void createPeriod_createsChildPrerollAdMediaPeriod() {
    contentMediaSource.setNewSourceInfo(CONTENT_TIMELINE, null);
    adsMediaSource.createPeriod(
        new MediaPeriodId(
            CONTENT_PERIOD_UID,
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0),
        mock(Allocator.class),
        /* startPositionUs= */ 0);
    prerollAdMediaSource.setNewSourceInfo(PREROLL_AD_TIMELINE, null);
    shadowOf(Looper.getMainLooper()).idle();

    prerollAdMediaSource.assertMediaPeriodCreated(
        new MediaPeriodId(PREROLL_AD_PERIOD_UID, /* windowSequenceNumber= */ 0));
  }

  @Test
  public void createPeriod_createsChildContentMediaPeriod() {
    contentMediaSource.setNewSourceInfo(CONTENT_TIMELINE, null);
    shadowOf(Looper.getMainLooper()).idle();
    adsMediaSource.createPeriod(
        new MediaPeriodId(CONTENT_PERIOD_UID, /* windowSequenceNumber= */ 0),
        mock(Allocator.class),
        /* startPositionUs= */ 0);

    contentMediaSource.assertMediaPeriodCreated(
        new MediaPeriodId(CONTENT_PERIOD_UID, /* windowSequenceNumber= */ 0));
  }

  @Test
  public void releasePeriod_releasesChildMediaPeriodsAndSources() {
    contentMediaSource.setNewSourceInfo(CONTENT_TIMELINE, null);
    MediaPeriod prerollAdMediaPeriod =
        adsMediaSource.createPeriod(
            new MediaPeriodId(
                CONTENT_PERIOD_UID,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0),
            mock(Allocator.class),
            /* startPositionUs= */ 0);
    prerollAdMediaSource.setNewSourceInfo(PREROLL_AD_TIMELINE, null);
    shadowOf(Looper.getMainLooper()).idle();
    MediaPeriod contentMediaPeriod =
        adsMediaSource.createPeriod(
            new MediaPeriodId(CONTENT_PERIOD_UID, /* windowSequenceNumber= */ 0),
            mock(Allocator.class),
            /* startPositionUs= */ 0);
    adsMediaSource.releasePeriod(prerollAdMediaPeriod);

    prerollAdMediaSource.assertReleased();

    adsMediaSource.releasePeriod(contentMediaPeriod);
    adsMediaSource.releaseSource(mockMediaSourceCaller);
    shadowOf(Looper.getMainLooper()).idle();
    prerollAdMediaSource.assertReleased();
    contentMediaSource.assertReleased();
  }
}
