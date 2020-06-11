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
package com.google.android.exoplayer2.ext.ima;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader.ImaFactory;
import com.google.android.exoplayer2.source.MaskingMediaSource.DummyTimeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException;
import com.google.android.exoplayer2.source.ads.SinglePeriodAdTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.robolectric.shadows.ShadowSystemClock;

/** Tests for {@link ImaAdsLoader}. */
@RunWith(AndroidJUnit4.class)
public final class ImaAdsLoaderTest {

  private static final long CONTENT_DURATION_US = 10 * C.MICROS_PER_SECOND;
  private static final Timeline CONTENT_TIMELINE =
      new FakeTimeline(
          new TimelineWindowDefinition(
              /* isSeekable= */ true, /* isDynamic= */ false, CONTENT_DURATION_US));
  private static final long CONTENT_PERIOD_DURATION_US =
      CONTENT_TIMELINE.getPeriod(/* periodIndex= */ 0, new Period()).durationUs;
  private static final Uri TEST_URI = Uri.EMPTY;
  private static final AdMediaInfo TEST_AD_MEDIA_INFO = new AdMediaInfo(TEST_URI.toString());
  private static final long TEST_AD_DURATION_US = 5 * C.MICROS_PER_SECOND;
  private static final Float[] PREROLL_CUE_POINTS_SECONDS = new Float[] {0f};

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private ImaSdkSettings mockImaSdkSettings;
  @Mock private AdsRenderingSettings mockAdsRenderingSettings;
  @Mock private AdDisplayContainer mockAdDisplayContainer;
  @Mock private AdsManager mockAdsManager;
  @Mock private AdsRequest mockAdsRequest;
  @Mock private AdsManagerLoadedEvent mockAdsManagerLoadedEvent;
  @Mock private com.google.ads.interactivemedia.v3.api.AdsLoader mockAdsLoader;
  @Mock private ImaFactory mockImaFactory;
  @Mock private AdPodInfo mockAdPodInfo;
  @Mock private Ad mockPrerollSingleAd;
  @Mock private AdEvent mockPostrollFetchErrorAdEvent;

  private ViewGroup adViewGroup;
  private View adOverlayView;
  private AdsLoader.AdViewProvider adViewProvider;
  private TestAdsLoaderListener adsLoaderListener;
  private FakePlayer fakeExoPlayer;
  private ImaAdsLoader imaAdsLoader;

  @Before
  public void setUp() {
    setupMocks();
    adViewGroup = new FrameLayout(ApplicationProvider.getApplicationContext());
    adOverlayView = new View(ApplicationProvider.getApplicationContext());
    adViewProvider =
        new AdsLoader.AdViewProvider() {
          @Override
          public ViewGroup getAdViewGroup() {
            return adViewGroup;
          }

          @Override
          public View[] getAdOverlayViews() {
            return new View[] {adOverlayView};
          }
        };
  }

  @After
  public void teardown() {
    if (imaAdsLoader != null) {
      imaAdsLoader.release();
    }
  }

  @Test
  public void builder_overridesPlayerType() {
    when(mockImaSdkSettings.getPlayerType()).thenReturn("test player type");
    setupPlayback(CONTENT_TIMELINE, PREROLL_CUE_POINTS_SECONDS);

    verify(mockImaSdkSettings).setPlayerType("google/exo.ext.ima");
  }

  @Test
  public void start_setsAdUiViewGroup() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    verify(mockAdDisplayContainer, atLeastOnce()).setAdContainer(adViewGroup);
    verify(mockAdDisplayContainer, atLeastOnce()).registerVideoControlsOverlay(adOverlayView);
  }

  @Test
  public void start_withPlaceholderContent_initializedAdsLoader() {
    Timeline placeholderTimeline = new DummyTimeline(MediaItem.fromUri(Uri.EMPTY));
    setupPlayback(placeholderTimeline, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    // We'll only create the rendering settings when initializing the ads loader.
    verify(mockImaFactory).createAdsRenderingSettings();
  }

  @Test
  public void start_updatesAdPlaybackState() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ 0)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US));
  }

  @Test
  public void startAfterRelease() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.release();
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
  }

  @Test
  public void startAndCallbacksAfterRelease() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.release();
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
    fakeExoPlayer.setPlayingContentPosition(/* position= */ 0);
    fakeExoPlayer.setState(Player.STATE_READY, true);

    // If callbacks are invoked there is no crash.
    // Note: we can't currently call getContentProgress/getAdProgress as a VerifyError is thrown
    // when using Robolectric and accessing VideoProgressUpdate.VIDEO_TIME_NOT_READY, due to the IMA
    // SDK being proguarded.
    imaAdsLoader.requestAds(adViewGroup);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.LOADED, mockPrerollSingleAd));
    imaAdsLoader.loadAd(TEST_AD_MEDIA_INFO, mockAdPodInfo);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.CONTENT_PAUSE_REQUESTED, mockPrerollSingleAd));
    imaAdsLoader.playAd(TEST_AD_MEDIA_INFO);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.STARTED, mockPrerollSingleAd));
    imaAdsLoader.pauseAd(TEST_AD_MEDIA_INFO);
    imaAdsLoader.stopAd(TEST_AD_MEDIA_INFO);
    imaAdsLoader.onPlayerError(ExoPlaybackException.createForSource(new IOException()));
    imaAdsLoader.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.CONTENT_RESUME_REQUESTED, /* ad= */ null));
    imaAdsLoader.handlePrepareError(
        /* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, new IOException());
  }

  @Test
  public void playback_withPrerollAd_marksAdAsPlayed() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_CUE_POINTS_SECONDS);

    // Load the preroll ad.
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.LOADED, mockPrerollSingleAd));
    imaAdsLoader.loadAd(TEST_AD_MEDIA_INFO, mockAdPodInfo);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.CONTENT_PAUSE_REQUESTED, mockPrerollSingleAd));

    // Play the preroll ad.
    imaAdsLoader.playAd(TEST_AD_MEDIA_INFO);
    fakeExoPlayer.setPlayingAdPosition(
        /* adGroupIndex= */ 0,
        /* adIndexInAdGroup= */ 0,
        /* position= */ 0,
        /* contentPosition= */ 0);
    fakeExoPlayer.setState(Player.STATE_READY, true);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.STARTED, mockPrerollSingleAd));
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.FIRST_QUARTILE, mockPrerollSingleAd));
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.MIDPOINT, mockPrerollSingleAd));
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.THIRD_QUARTILE, mockPrerollSingleAd));

    // Play the content.
    fakeExoPlayer.setPlayingContentPosition(0);
    imaAdsLoader.stopAd(TEST_AD_MEDIA_INFO);
    imaAdsLoader.onAdEvent(getAdEvent(AdEventType.CONTENT_RESUME_REQUESTED, /* ad= */ null));

    // Verify that the preroll ad has been marked as played.
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ 0)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US)
                .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                .withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, /* uri= */ TEST_URI)
                .withAdDurationsUs(new long[][] {{TEST_AD_DURATION_US}})
                .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
                .withAdResumePositionUs(/* adResumePositionUs= */ 0));
  }

  @Test
  public void playback_withPostrollFetchError_marksAdAsInErrorState() {
    setupPlayback(CONTENT_TIMELINE, new Float[] {-1f});

    // Simulate loading an empty postroll ad.
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
    imaAdsLoader.onAdEvent(mockPostrollFetchErrorAdEvent);

    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ C.TIME_END_OF_SOURCE)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US)
                .withAdDurationsUs(new long[][] {{TEST_AD_DURATION_US}})
                .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                .withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0));
  }

  @Test
  public void playback_withAdNotPreloadingBeforeTimeout_hasNoError() {
    // Simulate an ad at 2 seconds.
    long adGroupPositionInWindowUs = 2 * C.MICROS_PER_SECOND;
    long adGroupTimeUs =
        adGroupPositionInWindowUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE,
        new Float[] {(float) adGroupTimeUs / C.MICROS_PER_SECOND});

    // Advance playback to just before the midroll and simulate buffering.
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
    fakeExoPlayer.setPlayingContentPosition(C.usToMs(adGroupPositionInWindowUs));
    fakeExoPlayer.setState(Player.STATE_BUFFERING, /* playWhenReady= */ true);
    // Advance before the timeout and simulating polling content progress.
    ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
    imaAdsLoader.getContentProgress();

    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ adGroupTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US));
  }

  @Test
  public void playback_withAdNotPreloadingAfterTimeout_hasErrorAdGroup() {
    // Simulate an ad at 2 seconds.
    long adGroupPositionInWindowUs = 2 * C.MICROS_PER_SECOND;
    long adGroupTimeUs =
        adGroupPositionInWindowUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE,
        new Float[] {(float) adGroupTimeUs / C.MICROS_PER_SECOND});

    // Advance playback to just before the midroll and simulate buffering.
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
    fakeExoPlayer.setPlayingContentPosition(C.usToMs(adGroupPositionInWindowUs));
    fakeExoPlayer.setState(Player.STATE_BUFFERING, /* playWhenReady= */ true);
    // Advance past the timeout and simulate polling content progress.
    ShadowSystemClock.advanceBy(Duration.ofSeconds(5));
    imaAdsLoader.getContentProgress();

    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ adGroupTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US)
                .withAdDurationsUs(new long[][] {{TEST_AD_DURATION_US}})
                .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                .withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0));
  }

  @Test
  public void resumePlaybackBeforeMidroll_playsPreroll() {
    long midrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long midrollPeriodTimeUs =
        midrollWindowTimeUs + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE, new Float[] {0f, (float) midrollPeriodTimeUs / C.MICROS_PER_SECOND});

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(midrollWindowTimeUs) - 1_000);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    verify(mockAdsRenderingSettings, never()).setPlayAdsAfterTime(anyDouble());
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ 0, midrollPeriodTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US));
  }

  @Test
  public void resumePlaybackAtMidroll_skipsPreroll() {
    long midrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long midrollPeriodTimeUs =
        midrollWindowTimeUs + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE, new Float[] {0f, (float) midrollPeriodTimeUs / C.MICROS_PER_SECOND});

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(midrollWindowTimeUs));
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    ArgumentCaptor<Double> playAdsAfterTimeCaptor = ArgumentCaptor.forClass(Double.class);
    verify(mockAdsRenderingSettings).setPlayAdsAfterTime(playAdsAfterTimeCaptor.capture());
    double expectedPlayAdsAfterTimeUs = midrollPeriodTimeUs / 2d;
    assertThat(playAdsAfterTimeCaptor.getValue())
        .isWithin(0.1)
        .of(expectedPlayAdsAfterTimeUs / C.MICROS_PER_SECOND);
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ 0, midrollPeriodTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US)
                .withSkippedAdGroup(/* adGroupIndex= */ 0));
  }

  @Test
  public void resumePlaybackAfterMidroll_skipsPreroll() {
    long midrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long midrollPeriodTimeUs =
        midrollWindowTimeUs + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE, new Float[] {0f, (float) midrollPeriodTimeUs / C.MICROS_PER_SECOND});

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(midrollWindowTimeUs) + 1_000);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    ArgumentCaptor<Double> playAdsAfterTimeCaptor = ArgumentCaptor.forClass(Double.class);
    verify(mockAdsRenderingSettings).setPlayAdsAfterTime(playAdsAfterTimeCaptor.capture());
    double expectedPlayAdsAfterTimeUs = midrollPeriodTimeUs / 2d;
    assertThat(playAdsAfterTimeCaptor.getValue())
        .isWithin(0.1)
        .of(expectedPlayAdsAfterTimeUs / C.MICROS_PER_SECOND);
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ 0, midrollPeriodTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US)
                .withSkippedAdGroup(/* adGroupIndex= */ 0));
  }

  @Test
  public void resumePlaybackBeforeSecondMidroll_playsFirstMidroll() {
    long firstMidrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long firstMidrollPeriodTimeUs =
        firstMidrollWindowTimeUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    long secondMidrollWindowTimeUs = 4 * C.MICROS_PER_SECOND;
    long secondMidrollPeriodTimeUs =
        secondMidrollWindowTimeUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE,
        new Float[] {
          (float) firstMidrollPeriodTimeUs / C.MICROS_PER_SECOND,
          (float) secondMidrollPeriodTimeUs / C.MICROS_PER_SECOND
        });

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(secondMidrollWindowTimeUs) - 1_000);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    verify(mockAdsRenderingSettings, never()).setPlayAdsAfterTime(anyDouble());
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(
                    /* adGroupTimesUs...= */ firstMidrollPeriodTimeUs, secondMidrollPeriodTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US));
  }

  @Test
  public void resumePlaybackAtSecondMidroll_skipsFirstMidroll() {
    long firstMidrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long firstMidrollPeriodTimeUs =
        firstMidrollWindowTimeUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    long secondMidrollWindowTimeUs = 4 * C.MICROS_PER_SECOND;
    long secondMidrollPeriodTimeUs =
        secondMidrollWindowTimeUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE,
        new Float[] {
          (float) firstMidrollPeriodTimeUs / C.MICROS_PER_SECOND,
          (float) secondMidrollPeriodTimeUs / C.MICROS_PER_SECOND
        });

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(secondMidrollWindowTimeUs));
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    ArgumentCaptor<Double> playAdsAfterTimeCaptor = ArgumentCaptor.forClass(Double.class);
    verify(mockAdsRenderingSettings).setPlayAdsAfterTime(playAdsAfterTimeCaptor.capture());
    double expectedPlayAdsAfterTimeUs = (firstMidrollPeriodTimeUs + secondMidrollPeriodTimeUs) / 2d;
    assertThat(playAdsAfterTimeCaptor.getValue())
        .isWithin(0.1)
        .of(expectedPlayAdsAfterTimeUs / C.MICROS_PER_SECOND);
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(
                    /* adGroupTimesUs...= */ firstMidrollPeriodTimeUs, secondMidrollPeriodTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US)
                .withSkippedAdGroup(/* adGroupIndex= */ 0));
  }

  @Test
  public void resumePlaybackBeforeMidroll_withoutPlayAdBeforeStartPosition_skipsPreroll() {
    long midrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long midrollPeriodTimeUs =
        midrollWindowTimeUs + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE,
        new Float[] {0f, (float) midrollPeriodTimeUs / C.MICROS_PER_SECOND},
        new ImaAdsLoader.Builder(ApplicationProvider.getApplicationContext())
            .setPlayAdBeforeStartPosition(false)
            .setImaFactory(mockImaFactory)
            .setImaSdkSettings(mockImaSdkSettings)
            .buildForAdTag(TEST_URI));

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(midrollWindowTimeUs) - 1_000);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    ArgumentCaptor<Double> playAdsAfterTimeCaptor = ArgumentCaptor.forClass(Double.class);
    verify(mockAdsRenderingSettings).setPlayAdsAfterTime(playAdsAfterTimeCaptor.capture());
    double expectedPlayAdsAfterTimeUs = midrollPeriodTimeUs / 2d;
    assertThat(playAdsAfterTimeCaptor.getValue())
        .isWithin(0.1d)
        .of(expectedPlayAdsAfterTimeUs / C.MICROS_PER_SECOND);
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ 0, midrollPeriodTimeUs)
                .withSkippedAdGroup(/* adGroupIndex= */ 0)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US));
  }

  @Test
  public void resumePlaybackAtMidroll_withoutPlayAdBeforeStartPosition_skipsPreroll() {
    long midrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long midrollPeriodTimeUs =
        midrollWindowTimeUs + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE,
        new Float[] {0f, (float) midrollPeriodTimeUs / C.MICROS_PER_SECOND},
        new ImaAdsLoader.Builder(ApplicationProvider.getApplicationContext())
            .setPlayAdBeforeStartPosition(false)
            .setImaFactory(mockImaFactory)
            .setImaSdkSettings(mockImaSdkSettings)
            .buildForAdTag(TEST_URI));

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(midrollWindowTimeUs));
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    ArgumentCaptor<Double> playAdsAfterTimeCaptor = ArgumentCaptor.forClass(Double.class);
    verify(mockAdsRenderingSettings).setPlayAdsAfterTime(playAdsAfterTimeCaptor.capture());
    double expectedPlayAdsAfterTimeUs = midrollPeriodTimeUs / 2d;
    assertThat(playAdsAfterTimeCaptor.getValue())
        .isWithin(0.1d)
        .of(expectedPlayAdsAfterTimeUs / C.MICROS_PER_SECOND);
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ 0, midrollPeriodTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US)
                .withSkippedAdGroup(/* adGroupIndex= */ 0));
  }

  @Test
  public void resumePlaybackAfterMidroll_withoutPlayAdBeforeStartPosition_skipsMidroll() {
    long midrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long midrollPeriodTimeUs =
        midrollWindowTimeUs + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE,
        new Float[] {0f, (float) midrollPeriodTimeUs / C.MICROS_PER_SECOND},
        new ImaAdsLoader.Builder(ApplicationProvider.getApplicationContext())
            .setPlayAdBeforeStartPosition(false)
            .setImaFactory(mockImaFactory)
            .setImaSdkSettings(mockImaSdkSettings)
            .buildForAdTag(TEST_URI));

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(midrollWindowTimeUs) + 1_000);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    verify(mockAdsManager).destroy();
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(/* adGroupTimesUs...= */ 0, midrollPeriodTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US)
                .withSkippedAdGroup(/* adGroupIndex= */ 0)
                .withSkippedAdGroup(/* adGroupIndex= */ 1));
  }

  @Test
  public void
      resumePlaybackBeforeSecondMidroll_withoutPlayAdBeforeStartPosition_skipsFirstMidroll() {
    long firstMidrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long firstMidrollPeriodTimeUs =
        firstMidrollWindowTimeUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    long secondMidrollWindowTimeUs = 4 * C.MICROS_PER_SECOND;
    long secondMidrollPeriodTimeUs =
        secondMidrollWindowTimeUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE,
        new Float[] {
          (float) firstMidrollPeriodTimeUs / C.MICROS_PER_SECOND,
          (float) secondMidrollPeriodTimeUs / C.MICROS_PER_SECOND
        },
        new ImaAdsLoader.Builder(ApplicationProvider.getApplicationContext())
            .setPlayAdBeforeStartPosition(false)
            .setImaFactory(mockImaFactory)
            .setImaSdkSettings(mockImaSdkSettings)
            .buildForAdTag(TEST_URI));

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(secondMidrollWindowTimeUs) - 1_000);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    ArgumentCaptor<Double> playAdsAfterTimeCaptor = ArgumentCaptor.forClass(Double.class);
    verify(mockAdsRenderingSettings).setPlayAdsAfterTime(playAdsAfterTimeCaptor.capture());
    double expectedPlayAdsAfterTimeUs = (firstMidrollPeriodTimeUs + secondMidrollPeriodTimeUs) / 2d;
    assertThat(playAdsAfterTimeCaptor.getValue())
        .isWithin(0.1d)
        .of(expectedPlayAdsAfterTimeUs / C.MICROS_PER_SECOND);
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(
                    /* adGroupTimesUs...= */ firstMidrollPeriodTimeUs, secondMidrollPeriodTimeUs)
                .withSkippedAdGroup(/* adGroupIndex= */ 0)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US));
  }

  @Test
  public void resumePlaybackAtSecondMidroll_withoutPlayAdBeforeStartPosition_skipsFirstMidroll() {
    long firstMidrollWindowTimeUs = 2 * C.MICROS_PER_SECOND;
    long firstMidrollPeriodTimeUs =
        firstMidrollWindowTimeUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    long secondMidrollWindowTimeUs = 4 * C.MICROS_PER_SECOND;
    long secondMidrollPeriodTimeUs =
        secondMidrollWindowTimeUs
            + TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    setupPlayback(
        CONTENT_TIMELINE,
        new Float[] {
          (float) firstMidrollPeriodTimeUs / C.MICROS_PER_SECOND,
          (float) secondMidrollPeriodTimeUs / C.MICROS_PER_SECOND
        },
        new ImaAdsLoader.Builder(ApplicationProvider.getApplicationContext())
            .setPlayAdBeforeStartPosition(false)
            .setImaFactory(mockImaFactory)
            .setImaSdkSettings(mockImaSdkSettings)
            .buildForAdTag(TEST_URI));

    fakeExoPlayer.setPlayingContentPosition(C.usToMs(secondMidrollWindowTimeUs));
    imaAdsLoader.start(adsLoaderListener, adViewProvider);

    ArgumentCaptor<Double> playAdsAfterTimeCaptor = ArgumentCaptor.forClass(Double.class);
    verify(mockAdsRenderingSettings).setPlayAdsAfterTime(playAdsAfterTimeCaptor.capture());
    double expectedPlayAdsAfterTimeUs = (firstMidrollPeriodTimeUs + secondMidrollPeriodTimeUs) / 2d;
    assertThat(playAdsAfterTimeCaptor.getValue())
        .isWithin(0.1d)
        .of(expectedPlayAdsAfterTimeUs / C.MICROS_PER_SECOND);
    assertThat(adsLoaderListener.adPlaybackState)
        .isEqualTo(
            new AdPlaybackState(
                    /* adGroupTimesUs...= */ firstMidrollPeriodTimeUs, secondMidrollPeriodTimeUs)
                .withContentDurationUs(CONTENT_PERIOD_DURATION_US)
                .withSkippedAdGroup(/* adGroupIndex= */ 0));
  }

  @Test
  public void stop_unregistersAllVideoControlOverlays() {
    setupPlayback(CONTENT_TIMELINE, PREROLL_CUE_POINTS_SECONDS);
    imaAdsLoader.start(adsLoaderListener, adViewProvider);
    imaAdsLoader.requestAds(adViewGroup);
    imaAdsLoader.stop();

    InOrder inOrder = inOrder(mockAdDisplayContainer);
    inOrder.verify(mockAdDisplayContainer).registerVideoControlsOverlay(adOverlayView);
    inOrder.verify(mockAdDisplayContainer).unregisterAllVideoControlsOverlays();
  }

  private void setupPlayback(Timeline contentTimeline, Float[] cuePoints) {
    setupPlayback(
        contentTimeline,
        cuePoints,
        new ImaAdsLoader.Builder(ApplicationProvider.getApplicationContext())
            .setImaFactory(mockImaFactory)
            .setImaSdkSettings(mockImaSdkSettings)
            .buildForAdTag(TEST_URI));
  }

  private void setupPlayback(
      Timeline contentTimeline, Float[] cuePoints, ImaAdsLoader imaAdsLoader) {
    fakeExoPlayer = new FakePlayer();
    adsLoaderListener = new TestAdsLoaderListener(fakeExoPlayer, contentTimeline);
    when(mockAdsManager.getAdCuePoints()).thenReturn(Arrays.asList(cuePoints));
    this.imaAdsLoader = imaAdsLoader;
    imaAdsLoader.setPlayer(fakeExoPlayer);
  }

  private void setupMocks() {
    ArgumentCaptor<Object> userRequestContextCaptor = ArgumentCaptor.forClass(Object.class);
    doNothing().when(mockAdsRequest).setUserRequestContext(userRequestContextCaptor.capture());
    when(mockAdsRequest.getUserRequestContext())
        .thenAnswer(invocation -> userRequestContextCaptor.getValue());
    List<com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener> adsLoadedListeners =
        new ArrayList<>();
    doAnswer(
            invocation -> {
              adsLoadedListeners.add(invocation.getArgument(0));
              return null;
            })
        .when(mockAdsLoader)
        .addAdsLoadedListener(any());
    doAnswer(
            invocation -> {
              adsLoadedListeners.remove(invocation.getArgument(0));
              return null;
            })
        .when(mockAdsLoader)
        .removeAdsLoadedListener(any());
    when(mockAdsManagerLoadedEvent.getAdsManager()).thenReturn(mockAdsManager);
    when(mockAdsManagerLoadedEvent.getUserRequestContext())
        .thenAnswer(invocation -> mockAdsRequest.getUserRequestContext());
    doAnswer(
            (Answer<Object>)
                invocation -> {
                  for (com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener listener :
                      adsLoadedListeners) {
                    listener.onAdsManagerLoaded(mockAdsManagerLoadedEvent);
                  }
                  return null;
                })
        .when(mockAdsLoader)
        .requestAds(mockAdsRequest);

    when(mockImaFactory.createAdDisplayContainer()).thenReturn(mockAdDisplayContainer);
    when(mockImaFactory.createAdsRenderingSettings()).thenReturn(mockAdsRenderingSettings);
    when(mockImaFactory.createAdsRequest()).thenReturn(mockAdsRequest);
    when(mockImaFactory.createAdsLoader(any(), any(), any())).thenReturn(mockAdsLoader);

    when(mockAdPodInfo.getPodIndex()).thenReturn(0);
    when(mockAdPodInfo.getTotalAds()).thenReturn(1);
    when(mockAdPodInfo.getAdPosition()).thenReturn(1);

    when(mockPrerollSingleAd.getAdPodInfo()).thenReturn(mockAdPodInfo);

    when(mockPostrollFetchErrorAdEvent.getType()).thenReturn(AdEventType.AD_BREAK_FETCH_ERROR);
    when(mockPostrollFetchErrorAdEvent.getAdData())
        .thenReturn(ImmutableMap.of("adBreakTime", "-1"));
  }

  private static AdEvent getAdEvent(AdEventType adEventType, @Nullable Ad ad) {
    return new AdEvent() {
      @Override
      public AdEventType getType() {
        return adEventType;
      }

      @Override
      @Nullable
      public Ad getAd() {
        return ad;
      }

      @Override
      public Map<String, String> getAdData() {
        return Collections.emptyMap();
      }
    };
  }

  /** Ad loader event listener that forwards ad playback state to a fake player. */
  private static final class TestAdsLoaderListener implements AdsLoader.EventListener {

    private final FakePlayer fakeExoPlayer;
    private final Timeline contentTimeline;

    public AdPlaybackState adPlaybackState;

    public TestAdsLoaderListener(FakePlayer fakeExoPlayer, Timeline contentTimeline) {
      this.fakeExoPlayer = fakeExoPlayer;
      this.contentTimeline = contentTimeline;
    }

    @Override
    public void onAdPlaybackState(AdPlaybackState adPlaybackState) {
      long[][] adDurationsUs = new long[adPlaybackState.adGroupCount][];
      for (int adGroupIndex = 0; adGroupIndex < adPlaybackState.adGroupCount; adGroupIndex++) {
        adDurationsUs[adGroupIndex] = new long[adPlaybackState.adGroups[adGroupIndex].uris.length];
        Arrays.fill(adDurationsUs[adGroupIndex], TEST_AD_DURATION_US);
      }
      adPlaybackState = adPlaybackState.withAdDurationsUs(adDurationsUs);
      this.adPlaybackState = adPlaybackState;
      fakeExoPlayer.updateTimeline(
          new SinglePeriodAdTimeline(contentTimeline, adPlaybackState),
          Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    }

    @Override
    public void onAdLoadError(AdLoadException error, DataSpec dataSpec) {
      assertThat(error.type).isNotEqualTo(AdLoadException.TYPE_UNEXPECTED);
    }

    @Override
    public void onAdClicked() {
      // Do nothing.
    }

    @Override
    public void onAdTapped() {
      // Do nothing.
    }
  }
}
