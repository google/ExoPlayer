/*
 * Copyright 2021 The Android Open Source Project
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

import static com.google.android.exoplayer2.C.DATA_TYPE_MEDIA;
import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runMainLooperUntil;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.playUntilPosition;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilPlaybackState;
import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.robolectric.PlaybackOutput;
import com.google.android.exoplayer2.robolectric.ShadowMediaCodecConfig;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.testutil.CapturingRenderersFactory;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ServerSideAdInsertionMediaSource}. */
@RunWith(AndroidJUnit4.class)
public final class ServerSideAdInsertionMediaSourceTest {

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  private static final String TEST_ASSET = "asset:///media/mp4/sample.mp4";
  private static final String TEST_ASSET_DUMP = "playbackdumps/mp4/ssai-sample.mp4.dump";

  @Test
  public void timeline_vodSinglePeriod_containsAdsDefinedInAdPlaybackState() throws Exception {
    FakeTimeline wrappedTimeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10_000_000,
                /* defaultPositionUs= */ 3_000_000,
                /* windowOffsetInFirstPeriodUs= */ 42_000_000L,
                AdPlaybackState.NONE));
    ServerSideAdInsertionMediaSource mediaSource =
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(wrappedTimeline), /* adPlaybackStateUpdater= */ null);
    // Test with one ad group before the window, and the window starting within the second ad group.
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ new Object(), /* adGroupTimesUs...= */
                15_000_000,
                41_500_000,
                42_200_000)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, /* adDurationsUs...= */ 500_000)
            .withAdDurationsUs(/* adGroupIndex= */ 1, /* adDurationsUs...= */ 300_000, 100_000)
            .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 400_000)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, /* contentResumeOffsetUs= */ 100_000)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 1, /* contentResumeOffsetUs= */ 400_000)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 200_000);
    AtomicReference<Timeline> timelineReference = new AtomicReference<>();
    mediaSource.setAdPlaybackStates(
        ImmutableMap.of(new Pair<>(0, 0), adPlaybackState), wrappedTimeline);

    mediaSource.prepareSource(
        (source, timeline) -> timelineReference.set(timeline),
        /* mediaTransferListener= */ null,
        PlayerId.UNSET);
    runMainLooperUntil(() -> timelineReference.get() != null);

    Timeline timeline = timelineReference.get();
    assertThat(timeline.getPeriodCount()).isEqualTo(1);
    Timeline.Period period = timeline.getPeriod(/* periodIndex= */ 0, new Timeline.Period());
    assertThat(period.getAdGroupCount()).isEqualTo(3);
    assertThat(period.getAdCountInAdGroup(/* adGroupIndex= */ 0)).isEqualTo(1);
    assertThat(period.getAdCountInAdGroup(/* adGroupIndex= */ 1)).isEqualTo(2);
    assertThat(period.getAdCountInAdGroup(/* adGroupIndex= */ 2)).isEqualTo(1);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 0)).isEqualTo(15_000_000);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 1)).isEqualTo(41_500_000);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 2)).isEqualTo(42_200_000);
    assertThat(period.getAdDurationUs(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
        .isEqualTo(500_000);
    assertThat(period.getAdDurationUs(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0))
        .isEqualTo(300_000);
    assertThat(period.getAdDurationUs(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1))
        .isEqualTo(100_000);
    assertThat(period.getAdDurationUs(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0))
        .isEqualTo(400_000);
    assertThat(period.getContentResumeOffsetUs(/* adGroupIndex= */ 0)).isEqualTo(100_000);
    assertThat(period.getContentResumeOffsetUs(/* adGroupIndex= */ 1)).isEqualTo(400_000);
    assertThat(period.getContentResumeOffsetUs(/* adGroupIndex= */ 2)).isEqualTo(200_000);
    // windowDurationUs + windowOffsetInFirstPeriodUs - sum(adDurations) + sum(contentResumeOffsets)
    assertThat(period.getDurationUs()).isEqualTo(51_400_000);
    // positionInWindowUs + sum(adDurationsBeforeWindow) - sum(contentResumeOffsetsBeforeWindow)
    assertThat(period.getPositionInWindowUs()).isEqualTo(-41_600_000);
    Timeline.Window window = timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window());
    assertThat(window.positionInFirstPeriodUs).isEqualTo(41_600_000);
    // windowDurationUs - sum(adDurationsInWindow) + sum(applicableContentResumeOffsetUs)
    assertThat(window.durationUs).isEqualTo(9_800_000);
  }

  @Test
  public void createPeriod_unpreparedAdMediaPeriodImplReplacesContentPeriod_adPeriodNotSelected()
      throws Exception {
    DefaultAllocator allocator =
        new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024);
    MediaPeriod.Callback callback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {}

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId").withLivePostrollPlaceholderAppended();
    FakeTimeline wrappedTimeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10_000_000L,
                /* defaultPositionUs= */ 3_000_000L,
                /* windowOffsetInFirstPeriodUs= */ 0L,
                AdPlaybackState.NONE));
    ServerSideAdInsertionMediaSource mediaSource =
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(wrappedTimeline), /* adPlaybackStateUpdater= */ null);
    AtomicReference<Timeline> timelineReference = new AtomicReference<>();
    AtomicReference<MediaSource.MediaPeriodId> mediaPeriodIdReference = new AtomicReference<>();
    mediaSource.setAdPlaybackStates(
        ImmutableMap.of(new Pair<>(0, 0), adPlaybackState), wrappedTimeline);
    mediaSource.addEventListener(
        new Handler(Util.getCurrentOrMainLooper()),
        new MediaSourceEventListener() {
          @Override
          public void onDownstreamFormatChanged(
              int windowIndex,
              @Nullable MediaSource.MediaPeriodId mediaPeriodId,
              MediaLoadData mediaLoadData) {
            mediaPeriodIdReference.set(mediaPeriodId);
          }
        });
    mediaSource.prepareSource(
        (source, timeline) -> timelineReference.set(timeline),
        /* mediaTransferListener= */ null,
        PlayerId.UNSET);
    runMainLooperUntil(() -> timelineReference.get() != null);
    Timeline firstTimeline = timelineReference.get();
    MediaSource.MediaPeriodId mediaPeriodId1 =
        new MediaSource.MediaPeriodId(
            new Pair<>(0, 0), /* windowSequenceNumber= */ 0L, /* nextAdGroupIndex= */ 0);
    MediaSource.MediaPeriodId mediaPeriodId2 =
        new MediaSource.MediaPeriodId(
            new Pair<>(0, 0),
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0L);

    // Create and prepare the first period.
    MediaPeriod mediaPeriod1 =
        mediaSource.createPeriod(mediaPeriodId1, allocator, /* startPositionUs= */ 0L);
    mediaPeriod1.prepare(callback, /* positionUs= */ 0L);

    // Update the playback state to turn the content period into an ad period.
    adPlaybackState =
        adPlaybackState
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ 0L)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true)
            .withAdCount(/* adGroupIndex= */ 0, 1)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 10_000_000L)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 10_000_000L);
    mediaSource.setAdPlaybackStates(
        ImmutableMap.of(new Pair<>(0, 0), adPlaybackState), wrappedTimeline);
    runMainLooperUntil(() -> !timelineReference.get().equals(firstTimeline));

    // Create the second period that is tied to the same SharedMediaPeriod internally.
    mediaSource.createPeriod(mediaPeriodId2, allocator, /* startPositionUs= */ 0L);

    // Issue a onDownstreamFormatChanged event for mediaPeriodId1. The SharedPeriod selects in
    // `getMediaPeriodForEvent` from the following `MediaPeriodImpl`s for
    // MediaLoadData.mediaStartTimeMs=0 to 10_000_00.
    // [
    //    isPrepared: true,
    //    startPositionMs: 0,
    //    endPositionMs: 0,
    //    adGroupIndex: -1,
    //    adIndexInAdGroup: -1,
    //    nextAdGroupIndex: 0,
    // ],
    // [
    //    isPrepared: false,
    //    startPositionMs: 0,
    //    endPositionMs: 10_000_000,
    //    adGroupIndex: 0,
    //    adIndexInAdGroup: 0,
    //    nextAdGroupIndex: -1,
    // ]
    MediaLoadData mediaLoadData =
        new MediaLoadData(
            /* dataType= */ DATA_TYPE_MEDIA,
            C.TRACK_TYPE_VIDEO,
            new Format.Builder().build(),
            C.SELECTION_REASON_INITIAL,
            /* trackSelectionData= */ null,
            /* mediaStartTimeMs= */ 123L,
            /* mediaEndTimeMs= */ 10_000_000L);
    mediaSource.onDownstreamFormatChanged(/* windowIndex= */ 0, mediaPeriodId1, mediaLoadData);
    runMainLooperUntil(
        () -> mediaPeriodId1.equals(mediaPeriodIdReference.get()),
        /* timeoutMs= */ 500L,
        Clock.DEFAULT);

    assertThat(mediaPeriodIdReference.get()).isEqualTo(mediaPeriodId1);
  }

  @Test
  public void timeline_liveSinglePeriodWithUnsetPeriodDuration_containsAdsDefinedInAdPlaybackState()
      throws Exception {
    Timeline wrappedTimeline =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ C.TIME_UNSET,
            /* windowDurationUs= */ 10_000_000,
            /* windowPositionInPeriodUs= */ 42_000_000L,
            /* windowDefaultStartPositionUs= */ 3_000_000,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            /* mediaItem= */ MediaItem.EMPTY);
    ServerSideAdInsertionMediaSource mediaSource =
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(wrappedTimeline), /* adPlaybackStateUpdater= */ null);
    // Test with one ad group before the window, and the window starting within the second ad group.
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ new Object(), /* adGroupTimesUs...= */
                15_000_000,
                41_500_000,
                42_200_000)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, /* adDurationsUs...= */ 500_000)
            .withAdDurationsUs(/* adGroupIndex= */ 1, /* adDurationsUs...= */ 300_000, 100_000)
            .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 400_000)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, /* contentResumeOffsetUs= */ 100_000)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 1, /* contentResumeOffsetUs= */ 400_000)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 200_000);
    AtomicReference<Timeline> timelineReference = new AtomicReference<>();
    mediaSource.setAdPlaybackStates(
        ImmutableMap.of(
            wrappedTimeline.getPeriod(
                    /* periodIndex= */ 0, new Timeline.Period(), /* setIds= */ true)
                .uid,
            adPlaybackState),
        wrappedTimeline);

    mediaSource.prepareSource(
        (source, timeline) -> timelineReference.set(timeline),
        /* mediaTransferListener= */ null,
        PlayerId.UNSET);
    runMainLooperUntil(() -> timelineReference.get() != null);

    Timeline timeline = timelineReference.get();
    assertThat(timeline.getPeriodCount()).isEqualTo(1);
    Timeline.Period period = timeline.getPeriod(/* periodIndex= */ 0, new Timeline.Period());
    assertThat(period.getAdGroupCount()).isEqualTo(3);
    assertThat(period.getAdCountInAdGroup(/* adGroupIndex= */ 0)).isEqualTo(1);
    assertThat(period.getAdCountInAdGroup(/* adGroupIndex= */ 1)).isEqualTo(2);
    assertThat(period.getAdCountInAdGroup(/* adGroupIndex= */ 2)).isEqualTo(1);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 0)).isEqualTo(15_000_000);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 1)).isEqualTo(41_500_000);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 2)).isEqualTo(42_200_000);
    assertThat(period.getAdDurationUs(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
        .isEqualTo(500_000);
    assertThat(period.getAdDurationUs(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0))
        .isEqualTo(300_000);
    assertThat(period.getAdDurationUs(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1))
        .isEqualTo(100_000);
    assertThat(period.getAdDurationUs(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0))
        .isEqualTo(400_000);
    assertThat(period.getContentResumeOffsetUs(/* adGroupIndex= */ 0)).isEqualTo(100_000);
    assertThat(period.getContentResumeOffsetUs(/* adGroupIndex= */ 1)).isEqualTo(400_000);
    assertThat(period.getContentResumeOffsetUs(/* adGroupIndex= */ 2)).isEqualTo(200_000);
    assertThat(period.getDurationUs()).isEqualTo(C.TIME_UNSET);
    // positionInWindowUs + sum(adDurationsBeforeWindow) - sum(contentResumeOffsetsBeforeWindow)
    assertThat(period.getPositionInWindowUs()).isEqualTo(-41_600_000);
    Timeline.Window window = timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window());
    assertThat(window.positionInFirstPeriodUs).isEqualTo(41_600_000);
    // windowDurationUs - sum(adDurationsInWindow) + sum(applicableContentResumeOffsetUs)
    assertThat(window.durationUs).isEqualTo(9_800_000);
  }

  @Test
  public void timeline_missingAdPlaybackStateByPeriodUid_isAssertedAndThrows() {
    FakeMediaSource contentSource = new FakeMediaSource();
    ServerSideAdInsertionMediaSource mediaSource =
        new ServerSideAdInsertionMediaSource(contentSource, /* adPlaybackStateUpdater= */ null);
    // The map of adPlaybackStates does not contain a valid period UID as key.
    mediaSource.setAdPlaybackStates(
        ImmutableMap.of(new Object(), new AdPlaybackState(/* adsId= */ new Object())),
        contentSource.getInitialTimeline());

    Assert.assertThrows(
        IllegalStateException.class,
        () ->
            mediaSource.prepareSource(
                (source, timeline) -> {
                  /* Do nothing. */
                },
                /* mediaTransferListener= */ null,
                PlayerId.UNSET));
  }

  @Test
  public void playbackWithPredefinedAds_playsSuccessfulWithoutRendererResets() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory renderersFactory = new CapturingRenderersFactory(context);
    ExoPlayer player =
        new ExoPlayer.Builder(context, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, renderersFactory);

    AdPlaybackState adPlaybackState = new AdPlaybackState(/* adsId= */ new Object());
    adPlaybackState =
        addAdGroupToAdPlaybackState(
            adPlaybackState,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 200_000);
    adPlaybackState =
        addAdGroupToAdPlaybackState(
            adPlaybackState,
            /* fromPositionUs= */ 400_000,
            /* contentResumeOffsetUs= */ 1_000_000,
            /* adDurationsUs...= */ 300_000);
    AdPlaybackState firstAdPlaybackState =
        addAdGroupToAdPlaybackState(
            adPlaybackState,
            /* fromPositionUs= */ 900_000,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 100_000);

    AtomicReference<ServerSideAdInsertionMediaSource> mediaSourceRef = new AtomicReference<>();
    mediaSourceRef.set(
        new ServerSideAdInsertionMediaSource(
            new DefaultMediaSourceFactory(context).createMediaSource(MediaItem.fromUri(TEST_ASSET)),
            contentTimeline -> {
              Object periodUid =
                  checkNotNull(
                      contentTimeline.getPeriod(
                              /* periodIndex= */ 0, new Timeline.Period(), /* setIds= */ true)
                          .uid);
              mediaSourceRef
                  .get()
                  .setAdPlaybackStates(
                      ImmutableMap.of(periodUid, firstAdPlaybackState), contentTimeline);
              return true;
            }));

    AnalyticsListener listener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(listener);
    player.setMediaSource(mediaSourceRef.get());
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Assert all samples have been played.
    DumpFileAsserts.assertOutput(context, playbackOutput, TEST_ASSET_DUMP);
    // Assert playback has been reported with ads: [ad0][content][ad1][content][ad2][content]
    // 6*2(audio+video) format changes, 5 discontinuities between parts.
    verify(listener, times(5))
        .onPositionDiscontinuity(
            any(), any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    verify(listener, times(12)).onDownstreamFormatChanged(any(), any());
    // Assert renderers played through without reset (=decoders have been enabled only once).
    verify(listener).onVideoEnabled(any(), any());
    verify(listener).onAudioEnabled(any(), any());
    // Assert playback progression was smooth (=no unexpected delays that cause audio to underrun)
    verify(listener, never()).onAudioUnderrun(any(), anyInt(), anyLong(), anyLong());
  }

  @Test
  public void playbackWithNewlyInsertedAds_playsSuccessfulWithoutRendererResets() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    AtomicReference<Object> periodUid = new AtomicReference<>();
    CapturingRenderersFactory renderersFactory = new CapturingRenderersFactory(context);
    ExoPlayer player =
        new ExoPlayer.Builder(context, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, renderersFactory);

    AdPlaybackState firstAdPlaybackState =
        addAdGroupToAdPlaybackState(
            new AdPlaybackState(/* adsId= */ new Object()),
            /* fromPositionUs= */ 900_000,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 100_000);
    AtomicReference<ServerSideAdInsertionMediaSource> mediaSourceRef = new AtomicReference<>();
    ArrayList<Timeline> contentTimelines = new ArrayList<>();
    mediaSourceRef.set(
        new ServerSideAdInsertionMediaSource(
            new DefaultMediaSourceFactory(context).createMediaSource(MediaItem.fromUri(TEST_ASSET)),
            /* adPlaybackStateUpdater= */ contentTimeline -> {
              periodUid.set(
                  checkNotNull(
                      contentTimeline.getPeriod(
                              /* periodIndex= */ 0, new Timeline.Period(), /* setIds= */ true)
                          .uid));
              contentTimelines.add(contentTimeline);
              mediaSourceRef
                  .get()
                  .setAdPlaybackStates(
                      ImmutableMap.of(periodUid.get(), firstAdPlaybackState), contentTimeline);
              return true;
            }));
    AnalyticsListener listener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(listener);
    player.setMediaSource(mediaSourceRef.get());
    player.prepare();

    // Add ad at the current playback position during playback.
    runUntilPlaybackState(player, Player.STATE_READY);
    AdPlaybackState secondAdPlaybackState =
        addAdGroupToAdPlaybackState(
            firstAdPlaybackState,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 500_000);
    mediaSourceRef
        .get()
        .setAdPlaybackStates(
            ImmutableMap.of(periodUid.get(), secondAdPlaybackState), contentTimelines.get(1));
    runUntilPendingCommandsAreFullyHandled(player);

    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Assert all samples have been played.
    DumpFileAsserts.assertOutput(context, playbackOutput, TEST_ASSET_DUMP);
    assertThat(contentTimelines).hasSize(2);
    // Assert playback has been reported with ads: [content][ad0][content][ad1][content]
    // 5*2(audio+video) format changes, 4 discontinuities between parts.
    verify(listener, times(4))
        .onPositionDiscontinuity(
            any(), any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    verify(listener, times(10)).onDownstreamFormatChanged(any(), any());
    // Assert renderers played through without reset (=decoders have been enabled only once).
    verify(listener).onVideoEnabled(any(), any());
    verify(listener).onAudioEnabled(any(), any());
    // Assert playback progression was smooth (=no unexpected delays that cause audio to underrun)
    verify(listener, never()).onAudioUnderrun(any(), anyInt(), anyLong(), anyLong());
  }

  @Test
  public void playbackWithAdditionalAdsInAdGroup_playsSuccessfulWithoutRendererResets()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    AtomicReference<Object> periodUid = new AtomicReference<>();
    CapturingRenderersFactory renderersFactory = new CapturingRenderersFactory(context);
    ExoPlayer player =
        new ExoPlayer.Builder(context, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, renderersFactory);

    AdPlaybackState firstAdPlaybackState =
        addAdGroupToAdPlaybackState(
            new AdPlaybackState(/* adsId= */ new Object()),
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 500_000);
    AtomicReference<ServerSideAdInsertionMediaSource> mediaSourceRef = new AtomicReference<>();
    ArrayList<Timeline> contentTimelines = new ArrayList<>();
    mediaSourceRef.set(
        new ServerSideAdInsertionMediaSource(
            new DefaultMediaSourceFactory(context).createMediaSource(MediaItem.fromUri(TEST_ASSET)),
            /* adPlaybackStateUpdater= */ contentTimeline -> {
              contentTimelines.add(contentTimeline);
              if (periodUid.get() == null) {
                periodUid.set(
                    checkNotNull(
                        contentTimeline.getPeriod(
                                /* periodIndex= */ 0, new Timeline.Period(), /* setIds= */ true)
                            .uid));
                mediaSourceRef
                    .get()
                    .setAdPlaybackStates(
                        ImmutableMap.of(periodUid.get(), firstAdPlaybackState), contentTimeline);
              }
              return true;
            }));

    AnalyticsListener listener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(listener);
    player.setMediaSource(mediaSourceRef.get());
    player.prepare();

    // Wait until playback is ready with first ad and then replace by 3 ads.
    runUntilPlaybackState(player, Player.STATE_READY);
    AdPlaybackState secondAdPlaybackState =
        firstAdPlaybackState
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3)
            .withAdDurationsUs(
                /* adGroupIndex= */ 0, /* adDurationsUs...= */ 50_000, 250_000, 200_000);
    mediaSourceRef
        .get()
        .setAdPlaybackStates(
            ImmutableMap.of(periodUid.get(), secondAdPlaybackState), contentTimelines.get(1));
    runUntilPendingCommandsAreFullyHandled(player);

    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Assert all samples have been played.
    DumpFileAsserts.assertOutput(context, playbackOutput, TEST_ASSET_DUMP);
    assertThat(contentTimelines).hasSize(2);
    // Assert playback has been reported with ads: [ad0][ad1][ad2][content]
    // 4*2(audio+video) format changes, 3 discontinuities between parts.
    verify(listener, times(3))
        .onPositionDiscontinuity(
            any(), any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    verify(listener, times(8)).onDownstreamFormatChanged(any(), any());
    // Assert renderers played through without reset (=decoders have been enabled only once).
    verify(listener).onVideoEnabled(any(), any());
    verify(listener).onAudioEnabled(any(), any());
    // Assert playback progression was smooth (=no unexpected delays that cause audio to underrun)
    verify(listener, never()).onAudioUnderrun(any(), anyInt(), anyLong(), anyLong());
  }

  @Test
  public void playbackWithSeek_isHandledCorrectly() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    ExoPlayer player =
        new ExoPlayer.Builder(context).setClock(new FakeClock(/* isAutoAdvancing= */ true)).build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));

    AdPlaybackState adPlaybackState = new AdPlaybackState(/* adsId= */ new Object());
    adPlaybackState =
        addAdGroupToAdPlaybackState(
            adPlaybackState,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 100_000);
    adPlaybackState =
        addAdGroupToAdPlaybackState(
            adPlaybackState,
            /* fromPositionUs= */ 600_000,
            /* contentResumeOffsetUs= */ 1_000_000,
            /* adDurationsUs...= */ 100_000);
    AdPlaybackState firstAdPlaybackState =
        addAdGroupToAdPlaybackState(
            adPlaybackState,
            /* fromPositionUs= */ 900_000,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 100_000);

    AtomicReference<ServerSideAdInsertionMediaSource> mediaSourceRef = new AtomicReference<>();
    mediaSourceRef.set(
        new ServerSideAdInsertionMediaSource(
            new DefaultMediaSourceFactory(context).createMediaSource(MediaItem.fromUri(TEST_ASSET)),
            /* adPlaybackStateUpdater= */ contentTimeline -> {
              Object periodUid =
                  checkNotNull(
                      contentTimeline.getPeriod(
                              /* periodIndex= */ 0, new Timeline.Period(), /* setIds= */ true)
                          .uid);
              mediaSourceRef
                  .get()
                  .setAdPlaybackStates(
                      ImmutableMap.of(periodUid, firstAdPlaybackState), contentTimeline);
              return true;
            }));

    AnalyticsListener listener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(listener);
    player.setMediaSource(mediaSourceRef.get());
    player.prepare();
    // Play to the first content part, then seek past the midroll.
    playUntilPosition(player, /* mediaItemIndex= */ 0, /* positionMs= */ 100);
    player.seekTo(/* positionMs= */ 1_600);
    runUntilPendingCommandsAreFullyHandled(player);
    long positionAfterSeekMs = player.getCurrentPosition();
    long contentPositionAfterSeekMs = player.getContentPosition();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Assert playback has been reported with ads: [ad0][content] seek [ad1][content][ad2][content]
    // 6*2(audio+video) format changes, 4 auto-transitions between parts, 1 seek with adjustment.
    verify(listener, times(4))
        .onPositionDiscontinuity(
            any(), any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    verify(listener, times(1))
        .onPositionDiscontinuity(any(), any(), any(), eq(Player.DISCONTINUITY_REASON_SEEK));
    verify(listener, times(1))
        .onPositionDiscontinuity(
            any(), any(), any(), eq(Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT));
    verify(listener, times(12)).onDownstreamFormatChanged(any(), any());
    assertThat(contentPositionAfterSeekMs).isEqualTo(1_600);
    assertThat(positionAfterSeekMs).isEqualTo(0); // Beginning of second ad.
    // Assert renderers played through without reset, except for the seek.
    verify(listener, times(2)).onVideoEnabled(any(), any());
    verify(listener, times(2)).onAudioEnabled(any(), any());
    // Assert playback progression was smooth (=no unexpected delays that cause audio to underrun)
    verify(listener, never()).onAudioUnderrun(any(), anyInt(), anyLong(), anyLong());
  }
}
