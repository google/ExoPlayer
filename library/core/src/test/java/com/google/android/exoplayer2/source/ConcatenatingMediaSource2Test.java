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
package com.google.android.exoplayer2.source;

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runMainLooperUntil;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilPlaybackState;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.max;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.TestExoPlayerBuilder;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

/** Unit tests for {@link ConcatenatingMediaSource2}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class ConcatenatingMediaSource2Test {

  @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
  public static ImmutableList<TestConfig> params() {
    ImmutableList.Builder<TestConfig> builder = ImmutableList.builder();

    // Full example with an offset in the initial window, MediaSource with multiple windows and
    // periods, and sources with ad insertion.
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ 123, /* adGroupTimesUs...= */ 0, 300_000)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
            .withAdDurationsUs(new long[][] {new long[] {2_000_000}, new long[] {4_000_000}});
    builder.add(
        new TestConfig(
            "initial_offset_multiple_windows_and_ads",
            buildConcatenatingMediaSource(
                buildMediaSource(
                    buildWindow(
                        /* periodCount= */ 2,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationMs= */ 1000,
                        /* defaultPositionMs= */ 123,
                        /* windowOffsetInFirstPeriodMs= */ 50),
                    buildWindow(
                        /* periodCount= */ 2,
                        /* isSeekable= */ false,
                        /* isDynamic= */ false,
                        /* durationMs= */ 2500)),
                buildMediaSource(
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationMs= */ 500,
                        adPlaybackState)),
                buildMediaSource(
                    buildWindow(
                        /* periodCount= */ 3,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationMs= */ 1800))),
            /* expectedAdDiscontinuities= */ 3,
            new ExpectedTimelineData(
                    /* isSeekable= */ false,
                    /* isDynamic= */ false,
                    /* defaultPositionMs= */ 123,
                    /* periodDurationsMs= */ new long[] {550, 500, 1250, 1250, 500, 600, 600, 600},
                    /* periodOffsetsInWindowMs= */ new long[] {
                      -50, 500, 1000, 2250, 3500, 4000, 4600, 5200
                    },
                    /* periodIsPlaceholder= */ new boolean[] {
                      false, false, false, false, false, false, false, false
                    },
                    /* windowDurationMs= */ 5800,
                    /* manifest= */ null)
                .withAdPlaybackState(/* periodIndex= */ 4, adPlaybackState)));

    builder.add(
        new TestConfig(
            "multipleMediaSource_sameManifest",
            buildConcatenatingMediaSource(
                buildMediaSource(
                    new Object[] {"manifest"},
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ true,
                        /* durationMs= */ 1000)),
                buildMediaSource(
                    new Object[] {"manifest"},
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ true,
                        /* durationMs= */ 1000))),
            /* expectedAdDiscontinuities= */ 0,
            new ExpectedTimelineData(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 0,
                /* periodDurationsMs= */ new long[] {1000, 1000},
                /* periodOffsetsInWindowMs= */ new long[] {0, 1000},
                /* periodIsPlaceholder= */ new boolean[] {false, false},
                /* windowDurationMs= */ 2000,
                /* manifest= */ "manifest")));

    builder.add(
        new TestConfig(
            "multipleMediaSource_differentManifest",
            buildConcatenatingMediaSource(
                buildMediaSource(
                    new Object[] {"manifest1"},
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ true,
                        /* durationMs= */ 1000)),
                buildMediaSource(
                    new Object[] {"manifest2"},
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ true,
                        /* durationMs= */ 1000))),
            /* expectedAdDiscontinuities= */ 0,
            new ExpectedTimelineData(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 0,
                /* periodDurationsMs= */ new long[] {1000, 1000},
                /* periodOffsetsInWindowMs= */ new long[] {0, 1000},
                /* periodIsPlaceholder= */ new boolean[] {false, false},
                /* windowDurationMs= */ 2000,
                /* manifest= */ null)));

    // Counter-example for isSeekable and isDynamic.
    builder.add(
        new TestConfig(
            "isSeekable_isDynamic_counter_example",
            buildConcatenatingMediaSource(
                buildMediaSource(
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationMs= */ 1000)),
                buildMediaSource(
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ true,
                        /* durationMs= */ 500))),
            /* expectedAdDiscontinuities= */ 0,
            new ExpectedTimelineData(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 0,
                /* periodDurationsMs= */ new long[] {1000, 500},
                /* periodOffsetsInWindowMs= */ new long[] {0, 1000},
                /* periodIsPlaceholder= */ new boolean[] {false, false},
                /* windowDurationMs= */ 1500,
                /* manifest= */ null)));

    // Unknown window and period durations.
    builder.add(
        new TestConfig(
            "unknown_window_and_period_durations",
            buildConcatenatingMediaSource(
                /* placeholderDurationMs= */ 420,
                buildMediaSource(
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ true,
                        /* durationMs= */ C.TIME_UNSET,
                        /* defaultPositionMs= */ 123,
                        /* windowOffsetInFirstPeriodMs= */ 50)),
                buildMediaSource(
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationMs= */ C.TIME_UNSET))),
            /* expectedAdDiscontinuities= */ 0,
            new ExpectedTimelineData(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 0,
                /* periodDurationsMs= */ new long[] {C.TIME_UNSET, C.TIME_UNSET},
                /* periodOffsetsInWindowMs= */ new long[] {0, 420},
                /* periodIsPlaceholder= */ new boolean[] {true, true},
                /* windowDurationMs= */ 840,
                /* manifest= */ null),
            new ExpectedTimelineData(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 123,
                /* periodDurationsMs= */ new long[] {C.TIME_UNSET, C.TIME_UNSET},
                /* periodOffsetsInWindowMs= */ new long[] {-50, 420},
                /* periodIsPlaceholder= */ new boolean[] {false, false},
                /* windowDurationMs= */ 840,
                /* manifest= */ null)));

    // Duplicate sources and nested concatenation.
    builder.add(
        new TestConfig(
            "duplicated_and_nested_sources",
            () -> {
              MediaSource duplicateSource =
                  buildMediaSource(
                          buildWindow(
                              /* periodCount= */ 2,
                              /* isSeekable= */ true,
                              /* isDynamic= */ false,
                              /* durationMs= */ 1000))
                      .get();
              Supplier<MediaSource> duplicateSourceSupplier = () -> duplicateSource;
              return buildConcatenatingMediaSource(
                      duplicateSourceSupplier,
                      buildConcatenatingMediaSource(
                          duplicateSourceSupplier, duplicateSourceSupplier),
                      buildConcatenatingMediaSource(
                          duplicateSourceSupplier, duplicateSourceSupplier),
                      duplicateSourceSupplier)
                  .get();
            },
            /* expectedAdDiscontinuities= */ 0,
            new ExpectedTimelineData(
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* defaultPositionMs= */ 0,
                /* periodDurationsMs= */ new long[] {
                  500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500
                },
                /* periodOffsetsInWindowMs= */ new long[] {
                  0, 500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500
                },
                /* periodIsPlaceholder= */ new boolean[] {
                  false, false, false, false, false, false, false, false, false, false, false, false
                },
                /* windowDurationMs= */ 6000,
                /* manifest= */ null)));

    // Concatenation with initial placeholder durations and delayed timeline updates.
    builder.add(
        new TestConfig(
            "initial_placeholder_and_delayed_preparation",
            buildConcatenatingMediaSource(
                /* placeholderDurationMs= */ 5000,
                buildMediaSource(
                    /* preparationDelayCount= */ 1,
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationMs= */ 4000,
                        /* defaultPositionMs= */ 123,
                        /* windowOffsetInFirstPeriodMs= */ 50)),
                buildMediaSource(
                    /* preparationDelayCount= */ 3,
                    buildWindow(
                        /* periodCount= */ 2,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationMs= */ 7000)),
                buildMediaSource(
                    /* preparationDelayCount= */ 2,
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ false,
                        /* isDynamic= */ false,
                        /* durationMs= */ 6000))),
            /* expectedAdDiscontinuities= */ 0,
            new ExpectedTimelineData(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 0,
                /* periodDurationsMs= */ new long[] {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET},
                /* periodOffsetsInWindowMs= */ new long[] {0, 5000, 10000},
                /* periodIsPlaceholder= */ new boolean[] {true, true, true},
                /* windowDurationMs= */ 15000,
                /* manifest= */ null),
            new ExpectedTimelineData(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 123,
                /* periodDurationsMs= */ new long[] {4050, C.TIME_UNSET, C.TIME_UNSET},
                /* periodOffsetsInWindowMs= */ new long[] {-50, 4000, 9000},
                /* periodIsPlaceholder= */ new boolean[] {false, true, true},
                /* windowDurationMs= */ 14000,
                /* manifest= */ null),
            new ExpectedTimelineData(
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 123,
                /* periodDurationsMs= */ new long[] {4050, C.TIME_UNSET, 6000},
                /* periodOffsetsInWindowMs= */ new long[] {-50, 4000, 9000},
                /* periodIsPlaceholder= */ new boolean[] {false, true, false},
                /* windowDurationMs= */ 15000,
                /* manifest= */ null),
            new ExpectedTimelineData(
                /* isSeekable= */ false,
                /* isDynamic= */ false,
                /* defaultPositionMs= */ 123,
                /* periodDurationsMs= */ new long[] {4050, 3500, 3500, 6000},
                /* periodOffsetsInWindowMs= */ new long[] {-50, 4000, 7500, 11000},
                /* periodIsPlaceholder= */ new boolean[] {false, false, false, false},
                /* windowDurationMs= */ 17000,
                /* manifest= */ null)));

    // Concatenation with initial placeholder durations and some immediate timeline updates.
    builder.add(
        new TestConfig(
            "initial_placeholder_and_immediate_partial_preparation",
            buildConcatenatingMediaSource(
                /* placeholderDurationMs= */ 5000,
                buildMediaSource(
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationMs= */ 4000,
                        /* defaultPositionMs= */ 123,
                        /* windowOffsetInFirstPeriodMs= */ 50)),
                buildMediaSource(
                    /* preparationDelayCount= */ 1,
                    buildWindow(
                        /* periodCount= */ 2,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationMs= */ 7000)),
                buildMediaSource(
                    buildWindow(
                        /* periodCount= */ 1,
                        /* isSeekable= */ false,
                        /* isDynamic= */ false,
                        /* durationMs= */ 6000))),
            /* expectedAdDiscontinuities= */ 0,
            new ExpectedTimelineData(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 0,
                /* periodDurationsMs= */ new long[] {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET},
                /* periodOffsetsInWindowMs= */ new long[] {0, 5000, 10000},
                /* periodIsPlaceholder= */ new boolean[] {true, true, true},
                /* windowDurationMs= */ 15000,
                /* manifest= */ null),
            new ExpectedTimelineData(
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* defaultPositionMs= */ 123,
                /* periodDurationsMs= */ new long[] {4050, C.TIME_UNSET, 6000},
                /* periodOffsetsInWindowMs= */ new long[] {-50, 4000, 9000},
                /* periodIsPlaceholder= */ new boolean[] {false, true, false},
                /* windowDurationMs= */ 15000,
                /* manifest= */ null),
            new ExpectedTimelineData(
                /* isSeekable= */ false,
                /* isDynamic= */ false,
                /* defaultPositionMs= */ 123,
                /* periodDurationsMs= */ new long[] {4050, 3500, 3500, 6000},
                /* periodOffsetsInWindowMs= */ new long[] {-50, 4000, 7500, 11000},
                /* periodIsPlaceholder= */ new boolean[] {false, false, false, false},
                /* windowDurationMs= */ 17000,
                /* manifest= */ null)));
    return builder.build();
  }

  @ParameterizedRobolectricTestRunner.Parameter public TestConfig config;

  private static final String TEST_MEDIA_ITEM_ID = "test_media_item_id";

  @Test
  public void prepareSource_reportsExpectedTimelines() throws Exception {
    MediaSource mediaSource = config.mediaSourceSupplier.get();
    ArrayList<Timeline> timelines = new ArrayList<>();
    mediaSource.prepareSource(
        (source, timeline) -> timelines.add(timeline),
        /* mediaTransferListener= */ null,
        PlayerId.UNSET);
    runMainLooperUntil(() -> timelines.size() == config.expectedTimelineData.size());

    for (int i = 0; i < config.expectedTimelineData.size(); i++) {
      Timeline timeline = timelines.get(i);
      ExpectedTimelineData expectedData = config.expectedTimelineData.get(i);
      assertThat(timeline.getWindowCount()).isEqualTo(1);
      assertThat(timeline.getPeriodCount()).isEqualTo(expectedData.periodDurationsMs.length);

      Timeline.Window window = timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window());
      assertThat(window.getDurationMs()).isEqualTo(expectedData.windowDurationMs);
      assertThat(window.isDynamic).isEqualTo(expectedData.isDynamic);
      assertThat(window.isSeekable).isEqualTo(expectedData.isSeekable);
      assertThat(window.getDefaultPositionMs()).isEqualTo(expectedData.defaultPositionMs);
      assertThat(window.getPositionInFirstPeriodMs())
          .isEqualTo(-expectedData.periodOffsetsInWindowMs[0]);
      assertThat(window.firstPeriodIndex).isEqualTo(0);
      assertThat(window.lastPeriodIndex).isEqualTo(expectedData.periodDurationsMs.length - 1);
      assertThat(window.uid).isEqualTo(Timeline.Window.SINGLE_WINDOW_UID);
      assertThat(window.mediaItem.mediaId).isEqualTo(TEST_MEDIA_ITEM_ID);
      assertThat(window.isPlaceholder).isFalse();
      assertThat(window.elapsedRealtimeEpochOffsetMs).isEqualTo(C.TIME_UNSET);
      assertThat(window.presentationStartTimeMs).isEqualTo(C.TIME_UNSET);
      assertThat(window.windowStartTimeMs).isEqualTo(C.TIME_UNSET);
      assertThat(window.liveConfiguration).isNull();
      assertThat(window.manifest).isEqualTo(expectedData.manifest);

      HashSet<Object> uidSet = new HashSet<>();
      for (int j = 0; j < timeline.getPeriodCount(); j++) {
        Timeline.Period period =
            timeline.getPeriod(/* periodIndex= */ j, new Timeline.Period(), /* setIds= */ true);
        assertThat(period.getDurationMs()).isEqualTo(expectedData.periodDurationsMs[j]);
        assertThat(period.windowIndex).isEqualTo(0);
        assertThat(period.getPositionInWindowMs())
            .isEqualTo(expectedData.periodOffsetsInWindowMs[j]);
        assertThat(period.isPlaceholder).isEqualTo(expectedData.periodIsPlaceholder[j]);
        uidSet.add(period.uid);
        assertThat(timeline.getIndexOfPeriod(period.uid)).isEqualTo(j);
        assertThat(timeline.getUidOfPeriod(j)).isEqualTo(period.uid);
        assertThat(timeline.getPeriodByUid(period.uid, new Timeline.Period())).isEqualTo(period);
      }
      assertThat(uidSet).hasSize(timeline.getPeriodCount());
    }
  }

  @Test
  public void prepareSource_afterRelease_reportsSameFinalTimeline() throws Exception {
    // Fully prepare source once.
    MediaSource mediaSource = config.mediaSourceSupplier.get();
    ArrayList<Timeline> timelines = new ArrayList<>();
    MediaSource.MediaSourceCaller caller = (source, timeline) -> timelines.add(timeline);
    mediaSource.prepareSource(caller, /* mediaTransferListener= */ null, PlayerId.UNSET);
    runMainLooperUntil(() -> timelines.size() == config.expectedTimelineData.size());

    // Release and re-prepare.
    mediaSource.releaseSource(caller);
    AtomicReference<Timeline> secondTimeline = new AtomicReference<>();
    MediaSource.MediaSourceCaller secondCaller = (source, timeline) -> secondTimeline.set(timeline);
    mediaSource.prepareSource(secondCaller, /* mediaTransferListener= */ null, PlayerId.UNSET);

    // Assert that we receive the same final timeline.
    runMainLooperUntil(() -> Iterables.getLast(timelines).equals(secondTimeline.get()));
  }

  @Test
  public void preparePeriod_reportsExpectedPeriodLoadEvents() throws Exception {
    // Prepare source and register listener.
    MediaSource mediaSource = config.mediaSourceSupplier.get();
    MediaSourceEventListener eventListener = mock(MediaSourceEventListener.class);
    mediaSource.addEventListener(new Handler(Looper.myLooper()), eventListener);
    ArrayList<Timeline> timelines = new ArrayList<>();
    mediaSource.prepareSource(
        (source, timeline) -> timelines.add(timeline),
        /* mediaTransferListener= */ null,
        PlayerId.UNSET);
    runMainLooperUntil(() -> timelines.size() == config.expectedTimelineData.size());

    // Iterate through all periods and ads. Create and prepare them twice, because the MediaSource
    // should support creating the same period more than once.
    ArrayList<MediaPeriod> mediaPeriods = new ArrayList<>();
    ArrayList<MediaSource.MediaPeriodId> mediaPeriodIds = new ArrayList<>();
    Timeline timeline = Iterables.getLast(timelines);
    for (int i = 0; i < timeline.getPeriodCount(); i++) {
      Timeline.Period period =
          timeline.getPeriod(/* periodIndex= */ i, new Timeline.Period(), /* setIds= */ true);
      MediaSource.MediaPeriodId mediaPeriodId =
          new MediaSource.MediaPeriodId(period.uid, /* windowSequenceNumber= */ 15);
      MediaPeriod mediaPeriod =
          mediaSource.createPeriod(mediaPeriodId, /* allocator= */ null, /* startPositionUs= */ 0);
      blockingPrepareMediaPeriod(mediaPeriod);
      mediaPeriods.add(mediaPeriod);
      mediaPeriodIds.add(mediaPeriodId);

      mediaPeriodId = mediaPeriodId.copyWithWindowSequenceNumber(/* windowSequenceNumber= */ 25);
      mediaPeriod =
          mediaSource.createPeriod(mediaPeriodId, /* allocator= */ null, /* startPositionUs= */ 0);
      blockingPrepareMediaPeriod(mediaPeriod);
      mediaPeriods.add(mediaPeriod);
      mediaPeriodIds.add(mediaPeriodId);

      for (int j = 0; j < period.getAdGroupCount(); j++) {
        for (int k = 0; k < period.getAdCountInAdGroup(j); k++) {
          mediaPeriodId =
              new MediaSource.MediaPeriodId(
                  period.uid,
                  /* adGroupIndex= */ j,
                  /* adIndexInAdGroup= */ k,
                  /* windowSequenceNumber= */ 35);
          mediaPeriod =
              mediaSource.createPeriod(
                  mediaPeriodId, /* allocator= */ null, /* startPositionUs= */ 0);
          blockingPrepareMediaPeriod(mediaPeriod);
          mediaPeriods.add(mediaPeriod);
          mediaPeriodIds.add(mediaPeriodId);

          mediaPeriodId =
              mediaPeriodId.copyWithWindowSequenceNumber(/* windowSequenceNumber= */ 45);
          mediaPeriod =
              mediaSource.createPeriod(
                  mediaPeriodId, /* allocator= */ null, /* startPositionUs= */ 0);
          blockingPrepareMediaPeriod(mediaPeriod);
          mediaPeriods.add(mediaPeriod);
          mediaPeriodIds.add(mediaPeriodId);
        }
      }
    }
    // Release all periods again.
    for (MediaPeriod mediaPeriod : mediaPeriods) {
      mediaSource.releasePeriod(mediaPeriod);
    }

    // Verify each load started and completed event is called with the correct mediaPeriodId.
    for (MediaSource.MediaPeriodId mediaPeriodId : mediaPeriodIds) {
      verify(eventListener)
          .onLoadStarted(
              /* windowIndex= */ eq(0),
              /* mediaPeriodId= */ eq(mediaPeriodId),
              /* loadEventInfo= */ any(),
              /* mediaLoadData= */ any());
      verify(eventListener)
          .onLoadCompleted(
              /* windowIndex= */ eq(0),
              /* mediaPeriodId= */ eq(mediaPeriodId),
              /* loadEventInfo= */ any(),
              /* mediaLoadData= */ any());
    }
  }

  @Test
  public void playback_fromDefaultPosition_startsFromCorrectPositionAndPlaysToEnd()
      throws Exception {
    ExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build();
    player.setMediaSource(config.mediaSourceSupplier.get());
    Player.Listener eventListener = mock(Player.Listener.class);
    player.addListener(eventListener);
    player.addAnalyticsListener(new EventLogger());

    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    long positionAfterPrepareMs = player.getCurrentPosition();
    boolean isDynamic = player.isCurrentMediaItemDynamic();
    if (!isDynamic) {
      // Dynamic streams never enter the ENDED state.
      player.play();
      runUntilPlaybackState(player, Player.STATE_ENDED);
    }
    player.release();

    ExpectedTimelineData expectedData = Iterables.getLast(config.expectedTimelineData);
    assertThat(positionAfterPrepareMs).isEqualTo(expectedData.defaultPositionMs);
    if (!isDynamic) {
      verify(
              eventListener,
              times(config.expectedAdDiscontinuities + expectedData.periodDurationsMs.length - 1))
          .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    }
  }

  @Test
  public void
      playback_fromSpecificPeriodPositionInFirstPeriod_startsFromCorrectPositionAndPlaysToEnd()
          throws Exception {
    ExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build();
    MediaSource mediaSource = config.mediaSourceSupplier.get();
    player.setMediaSource(mediaSource);
    Player.Listener eventListener = mock(Player.Listener.class);
    player.addListener(eventListener);
    player.addAnalyticsListener(new EventLogger());

    long startWindowPositionMs = 24;
    player.seekTo(startWindowPositionMs);
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    long windowPositionAfterPrepareMs = player.getCurrentPosition();
    boolean isDynamic = player.isCurrentMediaItemDynamic();
    if (!isDynamic) {
      // Dynamic streams never enter the ENDED state.
      player.play();
      runUntilPlaybackState(player, Player.STATE_ENDED);
    }
    player.release();

    ExpectedTimelineData expectedData = Iterables.getLast(config.expectedTimelineData);
    assertThat(windowPositionAfterPrepareMs).isEqualTo(startWindowPositionMs);
    if (!isDynamic) {
      verify(
              eventListener,
              times(expectedData.periodDurationsMs.length - 1 + config.expectedAdDiscontinuities))
          .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    }
  }

  @Test
  public void
      playback_fromSpecificPeriodPositionInSubsequentPeriod_startsFromCorrectPositionAndPlaysToEnd()
          throws Exception {
    Timeline.Period period = new Timeline.Period();
    Timeline.Window window = new Timeline.Window();
    ExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build();
    MediaSource mediaSource = config.mediaSourceSupplier.get();
    player.setMediaSource(mediaSource);
    Player.Listener eventListener = mock(Player.Listener.class);
    player.addListener(eventListener);
    player.addAnalyticsListener(new EventLogger());

    ExpectedTimelineData initialTimelineData = config.expectedTimelineData.get(0);
    int startPeriodIndex = max(1, initialTimelineData.periodDurationsMs.length - 2);
    long startPeriodPositionMs = 24;
    long startWindowPositionMs =
        initialTimelineData.periodOffsetsInWindowMs[startPeriodIndex] + startPeriodPositionMs;
    player.seekTo(startWindowPositionMs);
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    Timeline timeline = player.getCurrentTimeline();
    long windowPositionAfterPrepareMs = player.getContentPosition();
    Pair<Object, Long> periodPositionUs =
        timeline.getPeriodPositionUs(window, period, 0, Util.msToUs(windowPositionAfterPrepareMs));
    int periodIndexAfterPrepare = timeline.getIndexOfPeriod(periodPositionUs.first);
    long periodPositionAfterPrepareMs = Util.usToMs(periodPositionUs.second);
    boolean isDynamic = player.isCurrentMediaItemDynamic();
    if (!isDynamic) {
      // Dynamic streams never enter the ENDED state.
      player.play();
      runUntilPlaybackState(player, Player.STATE_ENDED);
    }
    player.release();

    ExpectedTimelineData expectedData = Iterables.getLast(config.expectedTimelineData);
    assertThat(periodPositionAfterPrepareMs).isEqualTo(startPeriodPositionMs);
    if (timeline.getPeriod(periodIndexAfterPrepare, period).getAdGroupCount() == 0) {
      assertThat(periodIndexAfterPrepare).isEqualTo(startPeriodIndex);
      if (!isDynamic) {
        verify(eventListener, times(expectedData.periodDurationsMs.length - startPeriodIndex - 1))
            .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
      }
    } else {
      // Seek beyond ad period: assert roll forward to un-played ad period.
      assertThat(periodIndexAfterPrepare).isLessThan(startPeriodIndex);
      verify(eventListener, atLeast(expectedData.periodDurationsMs.length - startPeriodIndex - 1))
          .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
      timeline.getPeriod(periodIndexAfterPrepare, period);
      assertThat(period.getAdGroupIndexForPositionUs(period.durationUs))
          .isNotEqualTo(C.INDEX_UNSET);
    }
  }

  private static void blockingPrepareMediaPeriod(MediaPeriod mediaPeriod) {
    ConditionVariable mediaPeriodPrepared = new ConditionVariable();
    mediaPeriod.prepare(
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            mediaPeriodPrepared.open();
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {
            mediaPeriod.continueLoading(/* positionUs= */ 0);
          }
        },
        /* positionUs= */ 0);
    mediaPeriodPrepared.block();
  }

  private static Supplier<MediaSource> buildConcatenatingMediaSource(
      Supplier<MediaSource>... sources) {
    return buildConcatenatingMediaSource(/* placeholderDurationMs= */ C.TIME_UNSET, sources);
  }

  private static Supplier<MediaSource> buildConcatenatingMediaSource(
      long placeholderDurationMs, Supplier<MediaSource>... sources) {
    return () -> {
      ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
      builder.setMediaItem(new MediaItem.Builder().setMediaId(TEST_MEDIA_ITEM_ID).build());
      for (Supplier<MediaSource> source : sources) {
        builder.add(source.get(), placeholderDurationMs);
      }
      return builder.build();
    };
  }

  private static Supplier<MediaSource> buildMediaSource(
      FakeTimeline.TimelineWindowDefinition... windows) {
    return buildMediaSource(/* preparationDelayCount= */ 0, windows);
  }

  private static Supplier<MediaSource> buildMediaSource(
      int preparationDelayCount, FakeTimeline.TimelineWindowDefinition... windows) {
    return buildMediaSource(preparationDelayCount, /* manifests= */ null, windows);
  }

  private static Supplier<MediaSource> buildMediaSource(
      Object[] manifests, FakeTimeline.TimelineWindowDefinition... windows) {
    return buildMediaSource(/* preparationDelayCount= */ 0, manifests, windows);
  }

  private static Supplier<MediaSource> buildMediaSource(
      int preparationDelayCount,
      @Nullable Object[] manifests,
      FakeTimeline.TimelineWindowDefinition... windows) {

    return () -> {
      // Simulate delay by repeatedly sending messages to self. This ensures that all other message
      // handling trigger by source preparation finishes before the new timeline update arrives.
      AtomicInteger delayCount = new AtomicInteger(10 * preparationDelayCount);
      return new FakeMediaSource(
          /* timeline= */ null,
          new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()) {
        @Override
        public synchronized void prepareSourceInternal(
            @Nullable TransferListener mediaTransferListener) {
          super.prepareSourceInternal(mediaTransferListener);
          Handler delayHandler = new Handler(Looper.myLooper());
          Runnable handleDelay =
              new Runnable() {
                @Override
                public void run() {
                  if (delayCount.getAndDecrement() == 0) {
                    setNewSourceInfo(
                        manifests != null
                            ? new FakeTimeline(manifests, windows)
                            : new FakeTimeline(windows));
                  } else {
                    delayHandler.post(this);
                  }
                }
              };
          delayHandler.post(handleDelay);
        }
      };
    };
  }

  private static FakeTimeline.TimelineWindowDefinition buildWindow(
      int periodCount, boolean isSeekable, boolean isDynamic, long durationMs) {
    return buildWindow(
        periodCount,
        isSeekable,
        isDynamic,
        durationMs,
        /* defaultPositionMs= */ 0,
        /* windowOffsetInFirstPeriodMs= */ 0);
  }

  private static FakeTimeline.TimelineWindowDefinition buildWindow(
      int periodCount,
      boolean isSeekable,
      boolean isDynamic,
      long durationMs,
      long defaultPositionMs,
      long windowOffsetInFirstPeriodMs) {
    return buildWindow(
        periodCount,
        isSeekable,
        isDynamic,
        durationMs,
        defaultPositionMs,
        windowOffsetInFirstPeriodMs,
        AdPlaybackState.NONE);
  }

  private static FakeTimeline.TimelineWindowDefinition buildWindow(
      int periodCount,
      boolean isSeekable,
      boolean isDynamic,
      long durationMs,
      AdPlaybackState adPlaybackState) {
    return buildWindow(
        periodCount,
        isSeekable,
        isDynamic,
        durationMs,
        /* defaultPositionMs= */ 0,
        /* windowOffsetInFirstPeriodMs= */ 0,
        adPlaybackState);
  }

  private static FakeTimeline.TimelineWindowDefinition buildWindow(
      int periodCount,
      boolean isSeekable,
      boolean isDynamic,
      long durationMs,
      long defaultPositionMs,
      long windowOffsetInFirstPeriodMs,
      AdPlaybackState adPlaybackState) {
    return new FakeTimeline.TimelineWindowDefinition(
        periodCount,
        /* id= */ new Object(),
        isSeekable,
        isDynamic,
        /* isLive= */ false,
        /* isPlaceholder= */ false,
        Util.msToUs(durationMs),
        Util.msToUs(defaultPositionMs),
        Util.msToUs(windowOffsetInFirstPeriodMs),
        ImmutableList.of(adPlaybackState),
        new MediaItem.Builder().setMediaId("").build());
  }

  private static final class TestConfig {

    public final Supplier<MediaSource> mediaSourceSupplier;
    public final ImmutableList<ExpectedTimelineData> expectedTimelineData;

    private final int expectedAdDiscontinuities;
    private final String tag;

    public TestConfig(
        String tag,
        Supplier<MediaSource> mediaSourceSupplier,
        int expectedAdDiscontinuities,
        ExpectedTimelineData... expectedTimelineData) {
      this.tag = tag;
      this.mediaSourceSupplier = mediaSourceSupplier;
      this.expectedTimelineData = ImmutableList.copyOf(expectedTimelineData);
      this.expectedAdDiscontinuities = expectedAdDiscontinuities;
    }

    @Override
    public String toString() {
      return tag;
    }
  }

  private static final class ExpectedTimelineData {

    public final boolean isSeekable;
    public final boolean isDynamic;
    public final long defaultPositionMs;
    public final long[] periodDurationsMs;
    public final long[] periodOffsetsInWindowMs;
    public final boolean[] periodIsPlaceholder;
    public final long windowDurationMs;
    public final AdPlaybackState[] adPlaybackState;
    @Nullable public final Object manifest;

    public ExpectedTimelineData(
        boolean isSeekable,
        boolean isDynamic,
        long defaultPositionMs,
        long[] periodDurationsMs,
        long[] periodOffsetsInWindowMs,
        boolean[] periodIsPlaceholder,
        long windowDurationMs,
        @Nullable Object manifest) {
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.defaultPositionMs = defaultPositionMs;
      this.periodDurationsMs = periodDurationsMs;
      this.periodOffsetsInWindowMs = periodOffsetsInWindowMs;
      this.periodIsPlaceholder = periodIsPlaceholder;
      this.windowDurationMs = windowDurationMs;
      this.adPlaybackState = new AdPlaybackState[periodDurationsMs.length];
      this.manifest = manifest;
    }

    @CanIgnoreReturnValue
    public ExpectedTimelineData withAdPlaybackState(
        int periodIndex, AdPlaybackState adPlaybackState) {
      this.adPlaybackState[periodIndex] = adPlaybackState;
      return this;
    }
  }
}
