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
package com.google.android.exoplayer2.ext.ima;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import android.view.Surface;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.TimelineChangeReason;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.ExoHostedTest;
import com.google.android.exoplayer2.testutil.HostActivity;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Playback tests using {@link ImaAdsLoader}. */
@RunWith(AndroidJUnit4.class)
public final class ImaPlaybackTest {

  private static final String TAG = "ImaPlaybackTest";

  private static final long TIMEOUT_MS = 5 * 60 * C.MILLIS_PER_SECOND;

  private static final String CONTENT_URI_SHORT =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4";
  private static final String CONTENT_URI_LONG =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-25s.mp4";
  private static final AdId CONTENT = new AdId(C.INDEX_UNSET, C.INDEX_UNSET);

  @Rule public ActivityTestRule<HostActivity> testRule = new ActivityTestRule<>(HostActivity.class);

  @Test
  public void playbackWithPrerollAdTag_playsAdAndContent() throws Exception {
    String adsResponse =
        TestUtil.getString(/* context= */ testRule.getActivity(), "media/ad-responses/preroll.xml");
    AdId[] expectedAdIds = new AdId[] {ad(0), CONTENT};
    ImaHostedTest hostedTest =
        new ImaHostedTest(Uri.parse(CONTENT_URI_SHORT), adsResponse, expectedAdIds);

    testRule.getActivity().runTest(hostedTest, TIMEOUT_MS);
  }

  @Test
  public void playbackWithMidrolls_playsAdAndContent() throws Exception {
    String adsResponse =
        TestUtil.getString(
            /* context= */ testRule.getActivity(),
            "media/ad-responses/preroll_midroll6s_postroll.xml");
    AdId[] expectedAdIds = new AdId[] {ad(0), CONTENT, ad(1), CONTENT, ad(2), CONTENT};
    ImaHostedTest hostedTest =
        new ImaHostedTest(Uri.parse(CONTENT_URI_SHORT), adsResponse, expectedAdIds);

    testRule.getActivity().runTest(hostedTest, TIMEOUT_MS);
  }

  @Test
  public void playbackWithMidrolls1And7_playsAdsAndContent() throws Exception {
    String adsResponse =
        TestUtil.getString(
            /* context= */ testRule.getActivity(), "media/ad-responses/midroll1s_midroll7s.xml");
    AdId[] expectedAdIds = new AdId[] {CONTENT, ad(0), CONTENT, ad(1), CONTENT};
    ImaHostedTest hostedTest =
        new ImaHostedTest(Uri.parse(CONTENT_URI_SHORT), adsResponse, expectedAdIds);

    testRule.getActivity().runTest(hostedTest, TIMEOUT_MS);
  }

  @Test
  public void playbackWithMidrolls10And20WithSeekTo12_playsAdsAndContent() throws Exception {
    String adsResponse =
        TestUtil.getString(
            /* context= */ testRule.getActivity(), "media/ad-responses/midroll10s_midroll20s.xml");
    AdId[] expectedAdIds = new AdId[] {CONTENT, ad(0), CONTENT, ad(1), CONTENT};
    ImaHostedTest hostedTest =
        new ImaHostedTest(Uri.parse(CONTENT_URI_LONG), adsResponse, expectedAdIds);
    hostedTest.setSchedule(
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .seek(12 * C.MILLIS_PER_SECOND)
            .build());
    testRule.getActivity().runTest(hostedTest, TIMEOUT_MS);
  }

  @Ignore("The second ad doesn't preload so playback gets stuck. See [internal: b/155615925].")
  @Test
  public void playbackWithMidrolls10And20WithSeekTo18_playsAdsAndContent() throws Exception {
    String adsResponse =
        TestUtil.getString(
            /* context= */ testRule.getActivity(), "media/ad-responses/midroll10s_midroll20s.xml");
    AdId[] expectedAdIds = new AdId[] {CONTENT, ad(0), CONTENT, ad(1), CONTENT};
    ImaHostedTest hostedTest =
        new ImaHostedTest(Uri.parse(CONTENT_URI_LONG), adsResponse, expectedAdIds);
    hostedTest.setSchedule(
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .seek(18 * C.MILLIS_PER_SECOND)
            .build());
    testRule.getActivity().runTest(hostedTest, TIMEOUT_MS);
  }

  private static AdId ad(int groupIndex) {
    return new AdId(groupIndex, /* indexInGroup= */ 0);
  }

  private static final class AdId {

    public final int groupIndex;
    public final int indexInGroup;

    public AdId(int groupIndex, int indexInGroup) {
      this.groupIndex = groupIndex;
      this.indexInGroup = indexInGroup;
    }

    @Override
    public String toString() {
      return "(" + groupIndex + ", " + indexInGroup + ')';
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      AdId that = (AdId) o;

      if (groupIndex != that.groupIndex) {
        return false;
      }
      return indexInGroup == that.indexInGroup;
    }

    @Override
    public int hashCode() {
      int result = groupIndex;
      result = 31 * result + indexInGroup;
      return result;
    }
  }

  private static final class ImaHostedTest extends ExoHostedTest implements Player.Listener {

    private final Uri contentUri;
    private final DataSpec adTagDataSpec;
    private final List<AdId> expectedAdIds;
    private final List<AdId> seenAdIds;
    private @MonotonicNonNull ImaAdsLoader imaAdsLoader;
    private @MonotonicNonNull SimpleExoPlayer player;

    private ImaHostedTest(Uri contentUri, String adsResponse, AdId... expectedAdIds) {
      // fullPlaybackNoSeeking is false as the playback lasts longer than the content source
      // duration due to ad playback, so the hosted test shouldn't assert the playing duration.
      super(ImaPlaybackTest.class.getSimpleName(), /* fullPlaybackNoSeeking= */ false);
      this.contentUri = contentUri;
      this.adTagDataSpec =
          new DataSpec(
              Util.getDataUriForString(/* mimeType= */ "text/xml", /* data= */ adsResponse));
      this.expectedAdIds = Arrays.asList(expectedAdIds);
      seenAdIds = new ArrayList<>();
    }

    @Override
    protected SimpleExoPlayer buildExoPlayer(
        HostActivity host, Surface surface, MappingTrackSelector trackSelector) {
      player = super.buildExoPlayer(host, surface, trackSelector);
      player.addAnalyticsListener(
          new AnalyticsListener() {
            @Override
            public void onTimelineChanged(EventTime eventTime, @TimelineChangeReason int reason) {
              maybeUpdateSeenAdIdentifiers();
            }

            @Override
            public void onPositionDiscontinuity(
                EventTime eventTime, @DiscontinuityReason int reason) {
              if (reason != Player.DISCONTINUITY_REASON_SEEK) {
                maybeUpdateSeenAdIdentifiers();
              }
            }
          });
      Context context = host.getApplicationContext();
      imaAdsLoader = new ImaAdsLoader.Builder(context).build();
      imaAdsLoader.setPlayer(player);
      return player;
    }

    @Override
    protected MediaSource buildSource(
        HostActivity host,
        DrmSessionManager drmSessionManager,
        FrameLayout overlayFrameLayout) {
      Context context = host.getApplicationContext();
      DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context);
      MediaSource contentMediaSource =
          new DefaultMediaSourceFactory(context).createMediaSource(MediaItem.fromUri(contentUri));
      return new AdsMediaSource(
          contentMediaSource,
          adTagDataSpec,
          /* adsId= */ adTagDataSpec.uri,
          new DefaultMediaSourceFactory(dataSourceFactory),
          Assertions.checkNotNull(imaAdsLoader),
          () -> overlayFrameLayout);
    }

    @Override
    protected void assertPassed(DecoderCounters audioCounters, DecoderCounters videoCounters) {
      assertThat(seenAdIds).isEqualTo(expectedAdIds);
    }

    private void maybeUpdateSeenAdIdentifiers() {
      if (Assertions.checkNotNull(player)
          .getCurrentTimeline()
          .getWindow(/* windowIndex= */ 0, new Window())
          .isPlaceholder) {
        // The window is still an initial placeholder so do nothing.
        return;
      }
      AdId adId = new AdId(player.getCurrentAdGroupIndex(), player.getCurrentAdIndexInAdGroup());
      if (seenAdIds.isEmpty() || !seenAdIds.get(seenAdIds.size() - 1).equals(adId)) {
        seenAdIds.add(adId);
      }
    }
  }
}
