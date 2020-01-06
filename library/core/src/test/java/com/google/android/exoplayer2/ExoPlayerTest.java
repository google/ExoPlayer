/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Looper;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.CompositeMediaSource;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MaskingMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.ActionSchedule.PlayerRunnable;
import com.google.android.exoplayer2.testutil.ActionSchedule.PlayerTarget;
import com.google.android.exoplayer2.testutil.AutoAdvancingFakeClock;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner.Builder;
import com.google.android.exoplayer2.testutil.FakeAdaptiveDataSet;
import com.google.android.exoplayer2.testutil.FakeAdaptiveMediaSource;
import com.google.android.exoplayer2.testutil.FakeChunkSource;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.FakeMediaClockRenderer;
import com.google.android.exoplayer2.testutil.FakeMediaPeriod;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeRenderer;
import com.google.android.exoplayer2.testutil.FakeShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.FakeTrackSelection;
import com.google.android.exoplayer2.testutil.FakeTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAudioManager;

/** Unit test for {@link ExoPlayer}. */
@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class ExoPlayerTest {

  /**
   * For tests that rely on the player transitioning to the ended state, the duration in
   * milliseconds after starting the player before the test will time out. This is to catch cases
   * where the player under test is not making progress, in which case the test should fail.
   */
  private static final int TIMEOUT_MS = 10000;

  private Context context;
  private Timeline dummyTimeline;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    dummyTimeline = new MaskingMediaSource.DummyTimeline(/* tag= */ 0);
  }

  /**
   * Tests playback of a source that exposes an empty timeline. Playback is expected to end without
   * error.
   */
  @Test
  public void testPlayEmptyTimeline() throws Exception {
    Timeline timeline = Timeline.EMPTY;
    Timeline expectedMaskingTimeline = new MaskingMediaSource.DummyTimeline(/* tag= */ null);
    FakeRenderer renderer = new FakeRenderer();
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setTimeline(timeline)
            .setRenderers(renderer)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertNoPositionDiscontinuities();
    testRunner.assertTimelinesSame(expectedMaskingTimeline, Timeline.EMPTY);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(renderer.formatReadCount).isEqualTo(0);
    assertThat(renderer.sampleBufferReadCount).isEqualTo(0);
    assertThat(renderer.isEnded).isFalse();
  }

  /** Tests playback of a source that exposes a single period. */
  @Test
  public void testPlaySinglePeriodTimeline() throws Exception {
    Object manifest = new Object();
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1, manifest);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setTimeline(timeline)
            .setManifest(manifest)
            .setRenderers(renderer)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertNoPositionDiscontinuities();
    testRunner.assertTimelinesSame(dummyTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertTrackGroupsEqual(new TrackGroupArray(new TrackGroup(Builder.VIDEO_FORMAT)));
    assertThat(renderer.formatReadCount).isEqualTo(1);
    assertThat(renderer.sampleBufferReadCount).isEqualTo(1);
    assertThat(renderer.isEnded).isTrue();
  }

  /** Tests playback of a source that exposes three periods. */
  @Test
  public void testPlayMultiPeriodTimeline() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setTimeline(timeline)
            .setRenderers(renderer)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    testRunner.assertTimelinesSame(new FakeMediaSource.InitialTimeline(timeline), timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(renderer.formatReadCount).isEqualTo(3);
    assertThat(renderer.sampleBufferReadCount).isEqualTo(3);
    assertThat(renderer.isEnded).isTrue();
  }

  /** Tests playback of periods with very short duration. */
  @Test
  public void testPlayShortDurationPeriods() throws Exception {
    // TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US / 100 = 1000 us per period.
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 100, /* id= */ 0));
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setTimeline(timeline)
            .setRenderers(renderer)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    Integer[] expectedReasons = new Integer[99];
    Arrays.fill(expectedReasons, Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    testRunner.assertPositionDiscontinuityReasonsEqual(expectedReasons);
    testRunner.assertTimelinesSame(dummyTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(renderer.formatReadCount).isEqualTo(100);
    assertThat(renderer.sampleBufferReadCount).isEqualTo(100);
    assertThat(renderer.isEnded).isTrue();
  }

  /**
   * Tests that the player does not unnecessarily reset renderers when playing a multi-period
   * source.
   */
  @Test
  public void testReadAheadToEndDoesNotResetRenderer() throws Exception {
    // Use sufficiently short periods to ensure the player attempts to read all at once.
    TimelineWindowDefinition windowDefinition0 =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 0,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* durationUs= */ 100_000);
    TimelineWindowDefinition windowDefinition1 =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* durationUs= */ 100_000);
    TimelineWindowDefinition windowDefinition2 =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 2,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* durationUs= */ 100_000);
    Timeline timeline = new FakeTimeline(windowDefinition0, windowDefinition1, windowDefinition2);
    final FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeMediaClockRenderer audioRenderer =
        new FakeMediaClockRenderer(Builder.AUDIO_FORMAT) {

          @Override
          public long getPositionUs() {
            // Simulate the playback position lagging behind the reading position: the renderer
            // media clock position will be the start of the timeline until the stream is set to be
            // final, at which point it jumps to the end of the timeline allowing the playing period
            // to advance.
            return isCurrentStreamFinal() ? 30 : 0;
          }

          @Override
          public void setPlaybackParameters(PlaybackParameters playbackParameters) {}

          @Override
          public PlaybackParameters getPlaybackParameters() {
            return PlaybackParameters.DEFAULT;
          }

          @Override
          public boolean isEnded() {
            return videoRenderer.isEnded();
          }
        };
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setTimeline(timeline)
            .setRenderers(videoRenderer, audioRenderer)
            .setSupportedFormats(Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    testRunner.assertTimelinesSame(new FakeMediaSource.InitialTimeline(timeline), timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(audioRenderer.positionResetCount).isEqualTo(1);
    assertThat(videoRenderer.isEnded).isTrue();
    assertThat(audioRenderer.isEnded).isTrue();
  }

  @Test
  public void testResettingMediaSourcesGivesFreshSourceInfo() throws Exception {
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    Timeline firstTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, 1000_000_000));
    MediaSource firstSource = new FakeMediaSource(firstTimeline, Builder.VIDEO_FORMAT);
    final CountDownLatch queuedSourceInfoCountDownLatch = new CountDownLatch(1);
    final CountDownLatch completePreparationCountDownLatch = new CountDownLatch(1);

    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondSource =
        new FakeMediaSource(secondTimeline, Builder.VIDEO_FORMAT) {
          @Override
          public synchronized void prepareSourceInternal(
              @Nullable TransferListener mediaTransferListener) {
            super.prepareSourceInternal(mediaTransferListener);
            // We've queued a source info refresh on the playback thread's event queue. Allow the
            // test thread to set the third source to the playlist, and block this thread (the
            // playback thread) until the test thread's call to setMediaSources() has returned.
            queuedSourceInfoCountDownLatch.countDown();
            try {
              completePreparationCountDownLatch.await();
            } catch (InterruptedException e) {
              throw new IllegalStateException(e);
            }
          }
        };
    Object thirdSourceManifest = new Object();
    Timeline thirdTimeline = new FakeTimeline(/* windowCount= */ 1, thirdSourceManifest);
    MediaSource thirdSource = new FakeMediaSource(thirdTimeline, Builder.VIDEO_FORMAT);

    // Prepare the player with a source with the first manifest and a non-empty timeline. Prepare
    // the player again with a source and a new manifest, which will never be exposed. Allow the
    // test thread to set a third source, and block the playback thread until the test thread's call
    // to setMediaSources() has returned.
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testResettingMediaSourcesGivesFreshSourceInfo")
            .waitForTimelineChanged(
                firstTimeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .setMediaSources(secondSource)
            .executeRunnable(
                () -> {
                  try {
                    queuedSourceInfoCountDownLatch.await();
                  } catch (InterruptedException e) {
                    // Ignore.
                  }
                })
            .setMediaSources(thirdSource)
            .executeRunnable(completePreparationCountDownLatch::countDown)
            .build();
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setMediaSources(firstSource)
            .setRenderers(renderer)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertNoPositionDiscontinuities();
    // The first source's preparation completed with a real timeline. When the second source was
    // prepared, it immediately exposed a dummy timeline, but the source info refresh from the
    // second source was suppressed as we replace it with the third source before the update
    // arrives.
    testRunner.assertTimelinesSame(
        dummyTimeline, firstTimeline, dummyTimeline, dummyTimeline, thirdTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertTrackGroupsEqual(new TrackGroupArray(new TrackGroup(Builder.VIDEO_FORMAT)));
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void testRepeatModeChanges() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRepeatMode")
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .playUntilStartOfWindow(/* windowIndex= */ 1)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .playUntilStartOfWindow(/* windowIndex= */ 1)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .playUntilStartOfWindow(/* windowIndex= */ 2)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .playUntilStartOfWindow(/* windowIndex= */ 2)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .playUntilStartOfWindow(/* windowIndex= */ 0)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .playUntilStartOfWindow(/* windowIndex= */ 0)
            .playUntilStartOfWindow(/* windowIndex= */ 0)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setRenderers(renderer)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlayedPeriodIndices(0, 1, 1, 2, 2, 0, 0, 0, 1, 2);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    testRunner.assertTimelinesSame(new FakeMediaSource.InitialTimeline(timeline), timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void testShuffleModeEnabledChanges() throws Exception {
    Timeline fakeTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource[] fakeMediaSources = {
      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT)
    };
    ConcatenatingMediaSource mediaSource =
        new ConcatenatingMediaSource(false, new FakeShuffleOrder(3), fakeMediaSources);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testShuffleModeEnabled")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .playUntilStartOfWindow(/* windowIndex= */ 1)
            .setShuffleModeEnabled(true)
            .playUntilStartOfWindow(/* windowIndex= */ 1)
            .setShuffleModeEnabled(false)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(mediaSource)
            .setRenderers(renderer)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlayedPeriodIndices(0, 1, 0, 2, 1, 2);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void testAdGroupWithLoadErrorIsSkipped() throws Exception {
    AdPlaybackState initialAdPlaybackState =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 1, /* adGroupTimesUs= */ 5 * C.MICROS_PER_SECOND);
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ C.MICROS_PER_SECOND,
                initialAdPlaybackState));
    AdPlaybackState errorAdPlaybackState = initialAdPlaybackState.withAdLoadError(0, 0);
    final Timeline adErrorTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ C.MICROS_PER_SECOND,
                errorAdPlaybackState));
    final FakeMediaSource fakeMediaSource = new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testAdGroupWithLoadErrorIsSkipped")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(() -> fakeMediaSource.setNewSourceInfo(adErrorTimeline, null))
            .waitForTimelineChanged(
                adErrorTimeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(fakeMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    // There is still one discontinuity from content to content for the failed ad insertion.
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_AD_INSERTION);
  }

  @Test
  public void testPeriodHoldersReleasedAfterSeekWithRepeatModeAll() throws Exception {
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testPeriodHoldersReleased")
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .waitForPositionDiscontinuity()
            .seek(0) // Seek with repeat mode set to Player.REPEAT_MODE_ALL.
            .waitForPositionDiscontinuity()
            .setRepeatMode(Player.REPEAT_MODE_OFF) // Turn off repeat so that playback can finish.
            .build();
    new Builder()
        .setRenderers(renderer)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void testSeekProcessedCallback() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekProcessedCallback")
            // Initial seek. Expect immediate seek processed.
            .pause()
            .seek(5)
            .waitForSeekProcessed()
            // Multiple overlapping seeks while the player is still preparing. Expect only one seek
            // processed.
            .seek(2)
            .seek(10)
            // Wait until media source prepared and re-seek to same position. Expect a seek
            // processed while still being in STATE_READY.
            .waitForPlaybackState(Player.STATE_READY)
            .seek(10)
            // Start playback and wait until playback reaches second window.
            .playUntilStartOfWindow(/* windowIndex= */ 1)
            // Seek twice in concession, expecting the first seek to be replaced (and thus except
            // only on seek processed callback).
            .seek(5)
            .seek(60)
            .play()
            .build();
    final List<Integer> playbackStatesWhenSeekProcessed = new ArrayList<>();
    EventListener eventListener =
        new EventListener() {
          private int currentPlaybackState = Player.STATE_IDLE;

          @Override
          public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
            currentPlaybackState = playbackState;
          }

          @Override
          public void onSeekProcessed() {
            playbackStatesWhenSeekProcessed.add(currentPlaybackState);
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setEventListener(eventListener)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_SEEK,
        Player.DISCONTINUITY_REASON_SEEK,
        Player.DISCONTINUITY_REASON_SEEK,
        Player.DISCONTINUITY_REASON_SEEK,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_SEEK,
        Player.DISCONTINUITY_REASON_SEEK);
    assertThat(playbackStatesWhenSeekProcessed)
        .containsExactly(
            Player.STATE_BUFFERING,
            Player.STATE_BUFFERING,
            Player.STATE_READY,
            Player.STATE_BUFFERING)
        .inOrder();
  }

  @Test
  public void testIllegalSeekPositionDoesThrow() throws Exception {
    final IllegalSeekPositionException[] exception = new IllegalSeekPositionException[1];
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testIllegalSeekPositionDoesThrow")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    try {
                      player.seekTo(/* windowIndex= */ 100, /* positionMs= */ 0);
                    } catch (IllegalSeekPositionException e) {
                      exception[0] = e;
                    }
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    new Builder()
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(exception[0]).isNotNull();
  }

  @Test
  public void testSeekDiscontinuity() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekDiscontinuity").seek(10).build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
  }

  @Test
  public void testSeekDiscontinuityWithAdjustment() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, Builder.VIDEO_FORMAT) {
          @Override
          protected FakeMediaPeriod createFakeMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              EventDispatcher eventDispatcher,
              @Nullable TransferListener transferListener) {
            FakeMediaPeriod mediaPeriod = new FakeMediaPeriod(trackGroupArray, eventDispatcher);
            mediaPeriod.setSeekToUsOffset(10);
            return mediaPeriod;
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekDiscontinuityAdjust")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .seek(10)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_SEEK, Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT);
  }

  @Test
  public void testInternalDiscontinuityAtNewPosition() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, Builder.VIDEO_FORMAT) {
          @Override
          protected FakeMediaPeriod createFakeMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              EventDispatcher eventDispatcher,
              @Nullable TransferListener transferListener) {
            FakeMediaPeriod mediaPeriod = new FakeMediaPeriod(trackGroupArray, eventDispatcher);
            mediaPeriod.setDiscontinuityPositionUs(10);
            return mediaPeriod;
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(mediaSource)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_INTERNAL);
  }

  @Test
  public void testInternalDiscontinuityAtInitialPosition() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, Builder.VIDEO_FORMAT) {
          @Override
          protected FakeMediaPeriod createFakeMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              EventDispatcher eventDispatcher,
              @Nullable TransferListener transferListener) {
            FakeMediaPeriod mediaPeriod = new FakeMediaPeriod(trackGroupArray, eventDispatcher);
            mediaPeriod.setDiscontinuityPositionUs(0);
            return mediaPeriod;
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(mediaSource)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    // If the position is unchanged we do not expect the discontinuity to be reported externally.
    testRunner.assertNoPositionDiscontinuities();
  }

  @Test
  public void testAllActivatedTrackSelectionAreReleasedForSinglePeriod() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    FakeTrackSelector trackSelector = new FakeTrackSelector();

    new Builder()
        .setMediaSources(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getAllTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made once (1 period).
    assertThat(createdTrackSelections).hasSize(2);
    assertThat(numSelectionsEnabled).isEqualTo(2);
  }

  @Test
  public void testAllActivatedTrackSelectionAreReleasedForMultiPeriods() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    FakeTrackSelector trackSelector = new FakeTrackSelector();

    new Builder()
        .setMediaSources(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getAllTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice (2 periods).
    assertThat(createdTrackSelections).hasSize(4);
    assertThat(numSelectionsEnabled).isEqualTo(4);
  }

  @Test
  public void testAllActivatedTrackSelectionAreReleasedWhenTrackSelectionsAreRemade()
      throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    final FakeTrackSelector trackSelector = new FakeTrackSelector();
    ActionSchedule disableTrackAction =
        new ActionSchedule.Builder("testChangeTrackSelection")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .disableRenderer(0)
            .play()
            .build();

    new Builder()
        .setMediaSources(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .setActionSchedule(disableTrackAction)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getAllTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice. The second time one renderer is
    // disabled, so only one out of the two track selections is enabled.
    assertThat(createdTrackSelections).hasSize(3);
    assertThat(numSelectionsEnabled).isEqualTo(3);
  }

  @Test
  public void testAllActivatedTrackSelectionAreReleasedWhenTrackSelectionsAreReused()
      throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    final FakeTrackSelector trackSelector = new FakeTrackSelector(/* reuse track selection */ true);
    ActionSchedule disableTrackAction =
        new ActionSchedule.Builder("testReuseTrackSelection")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .disableRenderer(0)
            .play()
            .build();

    new Builder()
        .setMediaSources(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .setActionSchedule(disableTrackAction)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getAllTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice. The second time one renderer is
    // disabled, and the selector re-uses the previous selection for the enabled renderer. So we
    // expect two track selections, one of which will have been enabled twice.
    assertThat(createdTrackSelections).hasSize(2);
    assertThat(numSelectionsEnabled).isEqualTo(3);
  }

  @Test
  public void testDynamicTimelineChangeReason() throws Exception {
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(false, false, 100000));
    final Timeline timeline2 = new FakeTimeline(new TimelineWindowDefinition(false, false, 20000));
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testDynamicTimelineChangeReason")
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(() -> mediaSource.setNewSourceInfo(timeline2, null))
            .waitForTimelineChanged(
                timeline2, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesSame(dummyTimeline, timeline, timeline2);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testResetMediaSourcesWithPositionResetAndShufflingUsesFirstPeriod() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 100000));
    ConcatenatingMediaSource firstMediaSource =
        new ConcatenatingMediaSource(
            /* isAtomic= */ false,
            new FakeShuffleOrder(/* length= */ 2),
            new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT),
            new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT));
    ConcatenatingMediaSource secondMediaSource =
        new ConcatenatingMediaSource(
            /* isAtomic= */ false,
            new FakeShuffleOrder(/* length= */ 2),
            new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT),
            new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT));
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testResetMediaSourcesWithPositionResetAndShufflingUsesFirstPeriod")
            // Wait for first preparation and enable shuffling. Plays period 0.
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .setShuffleModeEnabled(true)
            // Set the second media source (with position reset).
            // Plays period 1 and 0 because of the reversed fake shuffle order.
            .setMediaSources(/* resetPosition= */ true, secondMediaSource)
            .play()
            .waitForPositionDiscontinuity()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlayedPeriodIndices(0, 1, 0);
  }

  @Test
  public void testSetPlaybackParametersBeforePreparationCompletesSucceeds() throws Exception {
    // Test that no exception is thrown when playback parameters are updated between creating a
    // period and preparation of the period completing.
    final CountDownLatch createPeriodCalledCountDownLatch = new CountDownLatch(1);
    final FakeMediaPeriod[] fakeMediaPeriodHolder = new FakeMediaPeriod[1];
    MediaSource mediaSource =
        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1), Builder.VIDEO_FORMAT) {
          @Override
          protected FakeMediaPeriod createFakeMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              EventDispatcher eventDispatcher,
              @Nullable TransferListener transferListener) {
            // Defer completing preparation of the period until playback parameters have been set.
            fakeMediaPeriodHolder[0] =
                new FakeMediaPeriod(trackGroupArray, eventDispatcher, /* deferOnPrepared= */ true);
            createPeriodCalledCountDownLatch.countDown();
            return fakeMediaPeriodHolder[0];
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSetPlaybackParametersBeforePreparationCompletesSucceeds")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Block until createPeriod has been called on the fake media source.
            .executeRunnable(
                () -> {
                  try {
                    createPeriodCalledCountDownLatch.await();
                  } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                  }
                })
            // Set playback parameters (while the fake media period is not yet prepared).
            .setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f, /* pitch= */ 2f))
            // Complete preparation of the fake media period.
            .executeRunnable(() -> fakeMediaPeriodHolder[0].setPreparationComplete())
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void testStopDoesNotResetPosition() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final long[] positionHolder = new long[1];
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testStopDoesNotResetPosition")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 50)
            .stop()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    positionHolder[0] = player.getCurrentPosition();
                  }
                })
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesSame(dummyTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertNoPositionDiscontinuities();
    assertThat(positionHolder[0]).isAtLeast(50L);
  }

  @Test
  public void testStopWithoutResetDoesNotResetPosition() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final long[] positionHolder = new long[1];
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testStopWithoutResetDoesNotReset")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 50)
            .stop(/* reset= */ false)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    positionHolder[0] = player.getCurrentPosition();
                  }
                })
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesSame(dummyTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertNoPositionDiscontinuities();
    assertThat(positionHolder[0]).isAtLeast(50L);
  }

  @Test
  public void testStopWithResetDoesResetPosition() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final long[] positionHolder = new long[1];
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testStopWithResetDoesReset")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 50)
            .stop(/* reset= */ true)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    positionHolder[0] = player.getCurrentPosition();
                  }
                })
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesSame(dummyTimeline, timeline, Timeline.EMPTY);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    testRunner.assertNoPositionDiscontinuities();
    assertThat(positionHolder[0]).isEqualTo(0);
  }

  @Test
  public void testStopWithoutResetReleasesMediaSource() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testStopReleasesMediaSource")
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ false)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS);
    mediaSource.assertReleased();
    testRunner.blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void testStopWithResetReleasesMediaSource() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testStopReleasesMediaSource")
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ true)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS);
    mediaSource.assertReleased();
    testRunner.blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void testSettingNewStartPositionPossibleAfterStopWithReset() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource secondSource = new FakeMediaSource(secondTimeline, Builder.VIDEO_FORMAT);
    AtomicInteger windowIndexAfterStop = new AtomicInteger();
    AtomicLong positionAfterStop = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSettingNewStartPositionPossibleAfterStopWithReset")
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ true)
            .waitForPlaybackState(Player.STATE_IDLE)
            .seek(/* windowIndex= */ 1, /* positionMs= */ 1000)
            .setMediaSources(secondSource)
            .prepare()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    windowIndexAfterStop.set(player.getCurrentWindowIndex());
                    positionAfterStop.set(player.getCurrentPosition());
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .setExpectedPlayerEndedCount(2)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE,
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_IDLE,
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_ENDED);
    testRunner.assertTimelinesSame(
        dummyTimeline,
        timeline,
        Timeline.EMPTY,
        new FakeMediaSource.InitialTimeline(secondTimeline),
        secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, // stop(true)
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(windowIndexAfterStop.get()).isEqualTo(1);
    assertThat(positionAfterStop.get()).isAtLeast(1000L);
    testRunner.assertPlayedPeriodIndices(0, 1);
  }

  @Test
  public void testResetPlaylistWithPreviousPosition() throws Exception {
    Object firstWindowId = new Object();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ firstWindowId));
    Timeline firstExpectedMaskingTimeline =
        new MaskingMediaSource.DummyTimeline(/* tag= */ firstWindowId);
    Object secondWindowId = new Object();
    Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ secondWindowId));
    Timeline secondExpectedMaskingTimeline =
        new MaskingMediaSource.DummyTimeline(/* tag= */ secondWindowId);
    MediaSource secondSource = new FakeMediaSource(secondTimeline);
    AtomicLong positionAfterReprepare = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testResetPlaylistWithPreviousPosition")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 2000)
            .setMediaSources(/* windowIndex= */ 0, /* positionMs= */ 2000, secondSource)
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    positionAfterReprepare.set(player.getCurrentPosition());
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    testRunner.assertTimelinesSame(
        firstExpectedMaskingTimeline, timeline, secondExpectedMaskingTimeline, secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(positionAfterReprepare.get()).isAtLeast(2000L);
  }

  @Test
  public void testResetPlaylistStartsFromDefaultPosition() throws Exception {
    Object firstWindowId = new Object();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ firstWindowId));
    Timeline firstExpectedDummyTimeline =
        new MaskingMediaSource.DummyTimeline(/* tag= */ firstWindowId);
    Object secondWindowId = new Object();
    Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ secondWindowId));
    Timeline secondExpectedDummyTimeline =
        new MaskingMediaSource.DummyTimeline(/* tag= */ secondWindowId);
    MediaSource secondSource = new FakeMediaSource(secondTimeline);
    AtomicLong positionAfterReprepare = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testResetPlaylistStartsFromDefaultPosition")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 2000)
            .setMediaSources(/* resetPosition= */ true, secondSource)
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    positionAfterReprepare.set(player.getCurrentPosition());
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    testRunner.assertTimelinesSame(
        firstExpectedDummyTimeline, timeline, secondExpectedDummyTimeline, secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(positionAfterReprepare.get()).isEqualTo(0L);
  }

  @Test
  public void testResetPlaylistWithoutResettingPositionStartsFromOldPosition() throws Exception {
    Object firstWindowId = new Object();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ firstWindowId));
    Timeline firstExpectedDummyTimeline =
        new MaskingMediaSource.DummyTimeline(/* tag= */ firstWindowId);
    Object secondWindowId = new Object();
    Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ secondWindowId));
    Timeline secondExpectedDummyTimeline =
        new MaskingMediaSource.DummyTimeline(/* tag= */ secondWindowId);
    MediaSource secondSource = new FakeMediaSource(secondTimeline);
    AtomicLong positionAfterReprepare = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testResetPlaylistWithoutResettingPositionStartsFromOldPosition")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 2000)
            .setMediaSources(secondSource)
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    positionAfterReprepare.set(player.getCurrentPosition());
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    testRunner.assertTimelinesSame(
        firstExpectedDummyTimeline, timeline, secondExpectedDummyTimeline, secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(positionAfterReprepare.get()).isAtLeast(2000L);
  }

  @Test
  public void testStopDuringPreparationOverwritesPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testStopOverwritesPrepare")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .seek(0)
            .stop(true)
            .waitForSeekProcessed()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesSame(dummyTimeline, Timeline.EMPTY);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
  }

  @Test
  public void testStopAndSeekAfterStopDoesNotResetTimeline() throws Exception {
    // Combining additional stop and seek after initial stop in one test to get the seek processed
    // callback which ensures that all operations have been processed by the player.
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testStopTwice")
            .waitForPlaybackState(Player.STATE_READY)
            .stop(false)
            .stop(false)
            .seek(0)
            .waitForSeekProcessed()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesSame(dummyTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
  }

  @Test
  public void testReprepareAfterPlaybackError() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testReprepareAfterPlaybackError")
            .waitForPlaybackState(Player.STATE_READY)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForPlaybackState(Player.STATE_IDLE)
            .prepare()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context);
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    testRunner.assertTimelinesSame(dummyTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testSeekAndReprepareAfterPlaybackError() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final long[] positionHolder = new long[2];
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testReprepareAfterPlaybackError")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForPlaybackState(Player.STATE_IDLE)
            .seek(/* positionMs= */ 50)
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    positionHolder[0] = player.getCurrentPosition();
                  }
                })
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    positionHolder[1] = player.getCurrentPosition();
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context);
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    testRunner.assertTimelinesSame(dummyTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
    assertThat(positionHolder[0]).isEqualTo(50);
    assertThat(positionHolder[1]).isEqualTo(50);
  }

  @Test
  public void
      testInvalidSeekPositionAfterSourceInfoRefreshWithShuffleModeEnabledUsesCorrectFirstPeriod()
          throws Exception {
    FakeMediaSource mediaSource = new FakeMediaSource(new FakeTimeline(/* windowCount= */ 2));
    AtomicInteger windowIndexAfterUpdate = new AtomicInteger();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testInvalidSeekPositionSourceInfoRefreshUsesCorrectFirstPeriod")
            .setShuffleOrder(new FakeShuffleOrder(/* length= */ 0))
            .setShuffleModeEnabled(true)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Seeking to an invalid position will end playback.
            .seek(
                /* windowIndex= */ 100, /* positionMs= */ 0, /* catchIllegalSeekException= */ true)
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    windowIndexAfterUpdate.set(player.getCurrentWindowIndex());
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(windowIndexAfterUpdate.get()).isEqualTo(1);
  }

  @Test
  public void testRestartAfterEmptyTimelineWithShuffleModeEnabledUsesCorrectFirstPeriod()
      throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    FakeMediaSource mediaSource = new FakeMediaSource(timeline);
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(/* isAtomic= */ false, new FakeShuffleOrder(0));
    AtomicInteger windowIndexAfterAddingSources = new AtomicInteger();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testRestartAfterEmptyTimelineWithShuffleModeEnabledUsesCorrectFirstPeriod")
            .setShuffleModeEnabled(true)
            // Preparing with an empty media source will transition to ended state.
            .waitForPlaybackState(Player.STATE_ENDED)
            // Add two sources at once such that the default start position in the shuffled order
            // will be the second source.
            .executeRunnable(
                () ->
                    concatenatingMediaSource.addMediaSources(
                        Arrays.asList(mediaSource, mediaSource)))
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    windowIndexAfterAddingSources.set(player.getCurrentWindowIndex());
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSources(concatenatingMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(windowIndexAfterAddingSources.get()).isEqualTo(1);
  }

  @Test
  public void testPlaybackErrorAndReprepareDoesNotResetPosition() throws Exception {
    final Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    final long[] positionHolder = new long[3];
    final int[] windowIndexHolder = new int[3];
    final FakeMediaSource firstMediaSource = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testPlaybackErrorDoesNotResetPosition")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* windowIndex= */ 1, /* positionMs= */ 500)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForPlaybackState(Player.STATE_IDLE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Position while in error state
                    positionHolder[0] = player.getCurrentPosition();
                    windowIndexHolder[0] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Position while repreparing.
                    positionHolder[1] = player.getCurrentPosition();
                    windowIndexHolder[1] = player.getCurrentWindowIndex();
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Position after repreparation finished.
                    positionHolder[2] = player.getCurrentPosition();
                    windowIndexHolder[2] = player.getCurrentWindowIndex();
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context);
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    assertThat(positionHolder[0]).isAtLeast(500L);
    assertThat(positionHolder[1]).isEqualTo(positionHolder[0]);
    assertThat(positionHolder[2]).isEqualTo(positionHolder[0]);
    assertThat(windowIndexHolder[0]).isEqualTo(1);
    assertThat(windowIndexHolder[1]).isEqualTo(1);
    assertThat(windowIndexHolder[2]).isEqualTo(1);
  }

  @Test
  public void testSeekAfterPlaybackError() throws Exception {
    final Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    final long[] positionHolder = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    final int[] windowIndexHolder = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    final FakeMediaSource firstMediaSource = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekAfterPlaybackError")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* windowIndex= */ 1, /* positionMs= */ 500)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForPlaybackState(Player.STATE_IDLE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Position while in error state
                    positionHolder[0] = player.getCurrentPosition();
                    windowIndexHolder[0] = player.getCurrentWindowIndex();
                  }
                })
            .seek(/* windowIndex= */ 0, /* positionMs= */ C.TIME_UNSET)
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Position while in error state
                    positionHolder[1] = player.getCurrentPosition();
                    windowIndexHolder[1] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Position after prepare.
                    positionHolder[2] = player.getCurrentPosition();
                    windowIndexHolder[2] = player.getCurrentWindowIndex();
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context);
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    assertThat(positionHolder[0]).isAtLeast(500L);
    assertThat(positionHolder[1]).isEqualTo(0L);
    assertThat(positionHolder[2]).isEqualTo(0L);
    assertThat(windowIndexHolder[0]).isEqualTo(1);
    assertThat(windowIndexHolder[1]).isEqualTo(0);
    assertThat(windowIndexHolder[2]).isEqualTo(0);
  }

  @Test
  public void playbackErrorAndReprepareWithPositionResetKeepsWindowSequenceNumber()
      throws Exception {
    FakeMediaSource mediaSource = new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1));
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("playbackErrorWithResetKeepsWindowSequenceNumber")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForPlaybackState(Player.STATE_IDLE)
            .seek(0, C.TIME_UNSET)
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .play()
            .build();
    HashSet<Long> reportedWindowSequenceNumbers = new HashSet<>();
    AnalyticsListener listener =
        new AnalyticsListener() {
          @Override
          public void onPlayerStateChanged(
              EventTime eventTime, boolean playWhenReady, int playbackState) {
            if (eventTime.mediaPeriodId != null) {
              reportedWindowSequenceNumbers.add(eventTime.mediaPeriodId.windowSequenceNumber);
            }
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .setAnalyticsListener(listener)
            .build(context);
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    assertThat(reportedWindowSequenceNumbers).hasSize(1);
  }

  @Test
  public void testPlaybackErrorTwiceStillKeepsTimeline() throws Exception {
    final Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final FakeMediaSource mediaSource2 = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testPlaybackErrorDoesNotResetPosition")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForPlaybackState(Player.STATE_IDLE)
            .setMediaSources(/* resetPosition= */ false, mediaSource2)
            .prepare()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .waitForPlaybackState(Player.STATE_IDLE)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context);
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    testRunner.assertTimelinesSame(dummyTimeline, timeline, dummyTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testSendMessagesDuringPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void testSendMessagesAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void testMultipleSendMessages() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target50 = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget target80 = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target80, /* positionMs= */ 80)
            .sendMessage(target50, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target50.positionMs).isAtLeast(50L);
    assertThat(target80.positionMs).isAtLeast(80L);
    assertThat(target80.positionMs).isAtLeast(target50.positionMs);
  }

  @Test
  public void testSendMessagesFromStartPositionOnlyOnce() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    AtomicInteger counter = new AtomicInteger();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessagesFromStartPositionOnlyOnce")
            .waitForTimelineChanged()
            .pause()
            .sendMessage(
                (messageType, payload) -> {
                  counter.getAndIncrement();
                },
                /* windowIndex= */ 0,
                /* positionMs= */ 2000,
                /* deleteAfterDelivery= */ false)
            .seek(/* positionMs= */ 2000)
            .delay(/* delayMs= */ 2000)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  public void testMultipleSendMessagesAtSameTime() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target1 = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget target2 = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target1, /* positionMs= */ 50)
            .sendMessage(target2, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target1.positionMs).isAtLeast(50L);
    assertThat(target2.positionMs).isAtLeast(50L);
  }

  @Test
  public void testSendMessagesMultiPeriodResolution() throws Exception {
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 10, /* id= */ 0));
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void testSendMessagesAtStartAndEndOfPeriod() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    PositionGrabbingMessageTarget targetStartFirstPeriod = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget targetEndMiddlePeriod = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget targetStartMiddlePeriod = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget targetEndLastPeriod = new PositionGrabbingMessageTarget();
    long duration1Ms = timeline.getWindow(0, new Window()).getDurationMs();
    long duration2Ms = timeline.getWindow(1, new Window()).getDurationMs();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessagesAtStartAndEndOfPeriod")
            .sendMessage(targetStartFirstPeriod, /* windowIndex= */ 0, /* positionMs= */ 0)
            .sendMessage(targetEndMiddlePeriod, /* windowIndex= */ 0, /* positionMs= */ duration1Ms)
            .sendMessage(targetStartMiddlePeriod, /* windowIndex= */ 1, /* positionMs= */ 0)
            .sendMessage(targetEndLastPeriod, /* windowIndex= */ 1, /* positionMs= */ duration2Ms)
            .waitForMessage(targetEndLastPeriod)
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(targetStartFirstPeriod.windowIndex).isEqualTo(0);
    assertThat(targetStartFirstPeriod.positionMs).isAtLeast(0L);
    assertThat(targetEndMiddlePeriod.windowIndex).isEqualTo(0);
    assertThat(targetEndMiddlePeriod.positionMs).isAtLeast(duration1Ms);
    assertThat(targetStartMiddlePeriod.windowIndex).isEqualTo(1);
    assertThat(targetStartMiddlePeriod.positionMs).isAtLeast(0L);
    assertThat(targetEndLastPeriod.windowIndex).isEqualTo(1);
    assertThat(targetEndLastPeriod.positionMs).isAtLeast(duration2Ms);
  }

  @Test
  public void testSendMessagesSeekOnDeliveryTimeDuringPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .seek(/* positionMs= */ 50)
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void testSendMessagesSeekOnDeliveryTimeAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .waitForTimelineChanged(
                timeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .seek(/* positionMs= */ 50)
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void testSendMessagesSeekAfterDeliveryTimeDuringPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .seek(/* positionMs= */ 51)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isEqualTo(C.POSITION_UNSET);
  }

  @Test
  public void testSendMessagesSeekAfterDeliveryTimeAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .sendMessage(target, /* positionMs= */ 50)
            .waitForTimelineChanged(
                timeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .seek(/* positionMs= */ 51)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isEqualTo(C.POSITION_UNSET);
  }

  @Test
  public void testSendMessagesRepeatDoesNotRepost() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .play()
            .waitForPositionDiscontinuity()
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.messageCount).isEqualTo(1);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void testSendMessagesRepeatWithoutDeletingDoesRepost() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(
                target,
                /* windowIndex= */ 0,
                /* positionMs= */ 50,
                /* deleteAfterDelivery= */ false)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 1)
            .playUntilStartOfWindow(/* windowIndex= */ 0)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.messageCount).isEqualTo(2);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void testSendMessagesMoveCurrentWindowIndex() throws Exception {
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0));
    final Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0));
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline, Builder.VIDEO_FORMAT);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* positionMs= */ 50)
            .executeRunnable(() -> mediaSource.setNewSourceInfo(secondTimeline, null))
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .play()
            .build();
    new Builder()
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
    assertThat(target.windowIndex).isEqualTo(1);
  }

  @Test
  public void testSendMessagesMultiWindowDuringPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* windowIndex = */ 2, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.windowIndex).isEqualTo(2);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void testSendMessagesMultiWindowAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* windowIndex = */ 2, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.windowIndex).isEqualTo(2);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void testSendMessagesMoveWindowIndex() throws Exception {
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1));
    final Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0));
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline, Builder.VIDEO_FORMAT);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* windowIndex = */ 1, /* positionMs= */ 50)
            .executeRunnable(() -> mediaSource.setNewSourceInfo(secondTimeline, null))
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .seek(/* windowIndex= */ 0, /* positionMs= */ 0)
            .play()
            .build();
    new Builder()
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
    assertThat(target.windowIndex).isEqualTo(0);
  }

  @Test
  public void testSendMessagesNonLinearPeriodOrder() throws Exception {
    Timeline fakeTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource[] fakeMediaSources = {
      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT)
    };
    ConcatenatingMediaSource mediaSource =
        new ConcatenatingMediaSource(false, new FakeShuffleOrder(3), fakeMediaSources);
    PositionGrabbingMessageTarget target1 = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget target2 = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget target3 = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .sendMessage(target1, /* windowIndex = */ 0, /* positionMs= */ 50)
            .sendMessage(target2, /* windowIndex = */ 1, /* positionMs= */ 50)
            .sendMessage(target3, /* windowIndex = */ 2, /* positionMs= */ 50)
            .setShuffleModeEnabled(true)
            .seek(/* windowIndex= */ 2, /* positionMs= */ 0)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target1.windowIndex).isEqualTo(0);
    assertThat(target2.windowIndex).isEqualTo(1);
    assertThat(target3.windowIndex).isEqualTo(2);
  }

  @Test
  public void testCancelMessageBeforeDelivery() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    final AtomicReference<PlayerMessage> message = new AtomicReference<>();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testCancelMessage")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    message.set(
                        player.createMessage(target).setPosition(/* positionMs= */ 50).send());
                  }
                })
            // Play a bit to ensure message arrived in internal player.
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 30)
            .executeRunnable(() -> message.get().cancel())
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(message.get().isCanceled()).isTrue();
    assertThat(target.messageCount).isEqualTo(0);
  }

  @Test
  public void testCancelRepeatedMessageAfterDelivery() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    final AtomicReference<PlayerMessage> message = new AtomicReference<>();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testCancelMessage")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    message.set(
                        player
                            .createMessage(target)
                            .setPosition(/* positionMs= */ 50)
                            .setDeleteAfterDelivery(/* deleteAfterDelivery= */ false)
                            .send());
                  }
                })
            // Play until the message has been delivered.
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 51)
            // Seek back, cancel the message, and play past the same position again.
            .seek(/* positionMs= */ 0)
            .executeRunnable(() -> message.get().cancel())
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(message.get().isCanceled()).isTrue();
    assertThat(target.messageCount).isEqualTo(1);
  }

  @Test
  public void testSetAndSwitchSurface() throws Exception {
    final List<Integer> rendererMessages = new ArrayList<>();
    Renderer videoRenderer =
        new FakeRenderer(Builder.VIDEO_FORMAT) {
          @Override
          public void handleMessage(int what, @Nullable Object object) throws ExoPlaybackException {
            super.handleMessage(what, object);
            rendererMessages.add(what);
          }
        };
    ActionSchedule actionSchedule =
        addSurfaceSwitch(new ActionSchedule.Builder("testSetAndSwitchSurface")).build();
    new ExoPlayerTestRunner.Builder()
        .setRenderers(videoRenderer)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(Collections.frequency(rendererMessages, C.MSG_SET_SURFACE)).isEqualTo(2);
  }

  @Test
  public void testSwitchSurfaceOnEndedState() throws Exception {
    ActionSchedule.Builder scheduleBuilder =
        new ActionSchedule.Builder("testSwitchSurfaceOnEndedState")
            .waitForPlaybackState(Player.STATE_ENDED);
    ActionSchedule waitForEndedAndSwitchSchedule = addSurfaceSwitch(scheduleBuilder).build();
    new ExoPlayerTestRunner.Builder()
        .setTimeline(Timeline.EMPTY)
        .setActionSchedule(waitForEndedAndSwitchSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void testTimelineUpdateDropsPrebufferedPeriods() throws Exception {
    Timeline timeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 2));
    final Timeline timeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 3));
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline1, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testTimelineUpdateDropsPeriods")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            // Ensure next period is pre-buffered by playing until end of first period.
            .playUntilPosition(
                /* windowIndex= */ 0,
                /* positionMs= */ C.usToMs(TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US))
            .executeRunnable(() -> mediaSource.setNewSourceInfo(timeline2, /* newManifest= */ null))
            .waitForTimelineChanged(
                timeline2, /* expectedReason */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertPlayedPeriodIndices(0, 1);
    // Assert that the second period was re-created from the new timeline.
    assertThat(mediaSource.getCreatedMediaPeriods()).hasSize(3);
    assertThat(mediaSource.getCreatedMediaPeriods().get(0).periodUid)
        .isEqualTo(timeline1.getUidOfPeriod(/* periodIndex= */ 0));
    assertThat(mediaSource.getCreatedMediaPeriods().get(1).periodUid)
        .isEqualTo(timeline1.getUidOfPeriod(/* periodIndex= */ 1));
    assertThat(mediaSource.getCreatedMediaPeriods().get(2).periodUid)
        .isEqualTo(timeline2.getUidOfPeriod(/* periodIndex= */ 1));
    assertThat(mediaSource.getCreatedMediaPeriods().get(1).windowSequenceNumber)
        .isGreaterThan(mediaSource.getCreatedMediaPeriods().get(0).windowSequenceNumber);
    assertThat(mediaSource.getCreatedMediaPeriods().get(2).windowSequenceNumber)
        .isGreaterThan(mediaSource.getCreatedMediaPeriods().get(1).windowSequenceNumber);
  }

  @Test
  public void testRepeatedSeeksToUnpreparedPeriodInSameWindowKeepsWindowSequenceNumber()
      throws Exception {
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 2,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10 * C.MICROS_PER_SECOND));
    FakeMediaSource mediaSource = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekToUnpreparedPeriod")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .seek(/* windowIndex= */ 0, /* positionMs= */ 9999)
            .waitForSeekProcessed()
            .seek(/* windowIndex= */ 0, /* positionMs= */ 1)
            .waitForSeekProcessed()
            .seek(/* windowIndex= */ 0, /* positionMs= */ 9999)
            .waitForSeekProcessed()
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilEnded(TIMEOUT_MS);

    testRunner.assertPlayedPeriodIndices(0, 1, 0, 1);
    assertThat(mediaSource.getCreatedMediaPeriods())
        .containsAtLeast(
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0),
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 0));
    assertThat(mediaSource.getCreatedMediaPeriods())
        .doesNotContain(
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 1));
  }

  @Test
  public void testInvalidSeekFallsBackToSubsequentPeriodOfTheRemovedPeriod() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    CountDownLatch sourceReleasedCountDownLatch = new CountDownLatch(/* count= */ 1);
    MediaSource mediaSourceToRemove =
        new FakeMediaSource(timeline) {
          @Override
          protected void releaseSourceInternal() {
            super.releaseSourceInternal();
            sourceReleasedCountDownLatch.countDown();
          }
        };
    ConcatenatingMediaSource mediaSource =
        new ConcatenatingMediaSource(mediaSourceToRemove, new FakeMediaSource(timeline));
    final int[] windowCount = {C.INDEX_UNSET};
    final long[] position = {C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testInvalidSeekFallsBackToSubsequentPeriodOfTheRemovedPeriod")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    mediaSource.removeMediaSource(/* index= */ 0);
                    try {
                      // Wait until the source to be removed is released on the playback thread. So
                      // the timeline in EPII has one window only, but the update here in EPI is
                      // stuck until we finished our executable here. So when seeking below, we will
                      // seek in the timeline which still has two windows in EPI, but when the seek
                      // arrives in EPII the actual timeline has one window only. Hence it tries to
                      // find the subsequent period of the removed period and finds it.
                      sourceReleasedCountDownLatch.await();
                    } catch (InterruptedException e) {
                      throw new IllegalStateException(e);
                    }
                    player.seekTo(/* windowIndex= */ 0, /* positionMs= */ 1000L);
                  }
                })
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    windowCount[0] = player.getCurrentTimeline().getWindowCount();
                    position[0] = player.getCurrentPosition();
                  }
                })
            .play()
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    // Expect the first window to be the current.
    assertThat(windowCount[0]).isEqualTo(1);
    // Expect the position to be in the default position.
    assertThat(position[0]).isEqualTo(0L);
  }

  @Test
  public void testRecursivePlayerChangesReportConsistentValuesForAllListeners() throws Exception {
    // We add two listeners to the player. The first stops the player as soon as it's ready and both
    // record the state change events they receive.
    final AtomicReference<Player> playerReference = new AtomicReference<>();
    final List<Integer> eventListener1States = new ArrayList<>();
    final List<Integer> eventListener2States = new ArrayList<>();
    final EventListener eventListener1 =
        new EventListener() {
          @Override
          public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
            eventListener1States.add(playbackState);
            if (playbackState == Player.STATE_READY) {
              playerReference.get().stop(/* reset= */ true);
            }
          }
        };
    final EventListener eventListener2 =
        new EventListener() {
          @Override
          public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
            eventListener2States.add(playbackState);
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRecursivePlayerChanges")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(eventListener1);
                    player.addListener(eventListener2);
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder()
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(eventListener1States)
        .containsExactly(Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_IDLE)
        .inOrder();
    assertThat(eventListener2States)
        .containsExactly(Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_IDLE)
        .inOrder();
  }

  @Test
  public void testRecursivePlayerChangesAreReportedInCorrectOrder() throws Exception {
    // The listener stops the player as soon as it's ready (which should report a timeline and state
    // change) and sets playWhenReady to false when the timeline callback is received.
    final AtomicReference<Player> playerReference = new AtomicReference<>();
    final List<Boolean> eventListenerPlayWhenReady = new ArrayList<>();
    final List<Integer> eventListenerStates = new ArrayList<>();
    final EventListener eventListener =
        new EventListener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            if (timeline.isEmpty()) {
              playerReference.get().pause();
            }
          }

          @Override
          public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
            eventListenerPlayWhenReady.add(playWhenReady);
            eventListenerStates.add(playbackState);
            if (playbackState == Player.STATE_READY) {
              playerReference.get().stop(/* reset= */ true);
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRecursivePlayerChanges")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(eventListener);
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder()
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(eventListenerStates)
        .containsExactly(
            Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_IDLE, Player.STATE_IDLE)
        .inOrder();
    assertThat(eventListenerPlayWhenReady).containsExactly(true, true, true, false).inOrder();
  }

  @Test
  public void testRecursiveTimelineChangeInStopAreReportedInCorrectOrder() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 2);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 3);
    final AtomicReference<ExoPlayer> playerReference = new AtomicReference<>();
    FakeMediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final EventListener eventListener =
        new EventListener() {
          @Override
          public void onPlayerStateChanged(boolean playWhenReady, int state) {
            if (state == Player.STATE_IDLE) {
              playerReference.get().setMediaSource(secondMediaSource);
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRecursiveTimelineChangeInStopAreReportedInCorrectOrder")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(eventListener);
                  }
                })
            // Ensure there are no further pending callbacks.
            .delay(1)
            .stop(/* reset= */ true)
            .waitForPlaybackState(Player.STATE_IDLE)
            .prepare()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setActionSchedule(actionSchedule)
            .setTimeline(firstTimeline)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    exoPlayerTestRunner.assertTimelinesSame(
        new FakeMediaSource.InitialTimeline(firstTimeline),
        firstTimeline,
        Timeline.EMPTY,
        new FakeMediaSource.InitialTimeline(secondTimeline),
        secondTimeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testClippedLoopedPeriodsArePlayedFully() throws Exception {
    long startPositionUs = 300_000;
    long expectedDurationUs = 700_000;
    MediaSource mediaSource =
        new ClippingMediaSource(
            new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1)),
            startPositionUs,
            startPositionUs + expectedDurationUs);
    Clock clock = new AutoAdvancingFakeClock();
    AtomicReference<Player> playerReference = new AtomicReference<>();
    AtomicLong positionAtDiscontinuityMs = new AtomicLong(C.TIME_UNSET);
    AtomicLong clockAtStartMs = new AtomicLong(C.TIME_UNSET);
    AtomicLong clockAtDiscontinuityMs = new AtomicLong(C.TIME_UNSET);
    EventListener eventListener =
        new EventListener() {
          @Override
          public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
            if (playbackState == Player.STATE_READY && clockAtStartMs.get() == C.TIME_UNSET) {
              clockAtStartMs.set(clock.elapsedRealtime());
            }
          }

          @Override
          public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
            if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
              positionAtDiscontinuityMs.set(playerReference.get().getCurrentPosition());
              clockAtDiscontinuityMs.set(clock.elapsedRealtime());
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testClippedLoopedPeriodsArePlayedFully")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(eventListener);
                  }
                })
            .pause()
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .waitForPlaybackState(Player.STATE_READY)
            // Play until the media repeats once.
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 1)
            .playUntilStartOfWindow(/* windowIndex= */ 0)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder()
        .setClock(clock)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(positionAtDiscontinuityMs.get()).isAtLeast(0L);
    assertThat(clockAtDiscontinuityMs.get() - clockAtStartMs.get())
        .isAtLeast(C.usToMs(expectedDurationUs));
  }

  @Test
  public void testUpdateTrackSelectorThenSeekToUnpreparedPeriod_returnsEmptyTrackGroups()
      throws Exception {
    // Use unset duration to prevent pre-loading of the second window.
    Timeline timelineUnsetDuration =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ C.TIME_UNSET));
    Timeline timelineSetDuration = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(timelineUnsetDuration, Builder.VIDEO_FORMAT),
            new FakeMediaSource(timelineSetDuration, Builder.AUDIO_FORMAT));
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testUpdateTrackSelectorThenSeekToUnpreparedPeriod")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .seek(/* windowIndex= */ 1, /* positionMs= */ 0)
            .play()
            .build();
    List<TrackGroupArray> trackGroupsList = new ArrayList<>();
    List<TrackSelectionArray> trackSelectionsList = new ArrayList<>();
    new Builder()
        .setMediaSources(mediaSource)
        .setSupportedFormats(Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT)
        .setActionSchedule(actionSchedule)
        .setEventListener(
            new EventListener() {
              @Override
              public void onTracksChanged(
                  TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
                trackGroupsList.add(trackGroups);
                trackSelectionsList.add(trackSelections);
              }
            })
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(trackGroupsList).hasSize(3);
    // First track groups of the 1st period are reported.
    // Then the seek to an unprepared period will result in empty track groups and selections being
    // returned.
    // Then the track groups of the 2nd period are reported.
    assertThat(trackGroupsList.get(0).get(0).getFormat(0)).isEqualTo(Builder.VIDEO_FORMAT);
    assertThat(trackGroupsList.get(1)).isEqualTo(TrackGroupArray.EMPTY);
    assertThat(trackSelectionsList.get(1).get(0)).isNull();
    assertThat(trackGroupsList.get(2).get(0).getFormat(0)).isEqualTo(Builder.AUDIO_FORMAT);
  }

  @Test
  public void testSecondMediaSourceInPlaylistOnlyThrowsWhenPreviousPeriodIsFullyRead()
      throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10 * C.MICROS_PER_SECOND));
    MediaSource workingMediaSource = new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT);
    MediaSource failingMediaSource =
        new FakeMediaSource(/* timeline= */ null, Builder.VIDEO_FORMAT) {
          @Override
          public void maybeThrowSourceInfoRefreshError() throws IOException {
            throw new IOException();
          }
        };
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(workingMediaSource, failingMediaSource);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setMediaSources(concatenatingMediaSource)
            .setRenderers(renderer)
            .build(context);
    try {
      testRunner.start().blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    assertThat(renderer.sampleBufferReadCount).isAtLeast(1);
    assertThat(renderer.hasReadStreamToEnd()).isTrue();
  }

  @Test
  public void
      testDynamicallyAddedSecondMediaSourceInPlaylistOnlyThrowsWhenPreviousPeriodIsFullyRead()
          throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10 * C.MICROS_PER_SECOND));
    MediaSource workingMediaSource = new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT);
    MediaSource failingMediaSource =
        new FakeMediaSource(/* timeline= */ null, Builder.VIDEO_FORMAT) {
          @Override
          public void maybeThrowSourceInfoRefreshError() throws IOException {
            throw new IOException();
          }
        };
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(workingMediaSource);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testFailingSecondMediaSourceInPlaylistOnlyThrowsLater")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(() -> concatenatingMediaSource.addMediaSource(failingMediaSource))
            .play()
            .build();
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setMediaSources(concatenatingMediaSource)
            .setActionSchedule(actionSchedule)
            .setRenderers(renderer)
            .build(context);
    try {
      testRunner.start().blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    assertThat(renderer.sampleBufferReadCount).isAtLeast(1);
    assertThat(renderer.hasReadStreamToEnd()).isTrue();
  }

  @Test
  public void removingLoopingLastPeriodFromPlaylistDoesNotThrow() throws Exception {
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 100_000));
    MediaSource mediaSource = new FakeMediaSource(timeline);
    ConcatenatingMediaSource concatenatingMediaSource = new ConcatenatingMediaSource(mediaSource);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("removingLoopingLastPeriodFromPlaylistDoesNotThrow")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            // Play almost to end to ensure the current period is fully buffered.
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 90)
            // Enable repeat mode to trigger the creation of new media periods.
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            // Remove the media source.
            .executeRunnable(concatenatingMediaSource::clear)
            .build();
    new Builder()
        .setMediaSources(concatenatingMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void seekToUnpreparedWindowWithNonZeroOffsetInConcatenationStartsAtCorrectPosition()
      throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    FakeMediaSource mediaSource = new FakeMediaSource(/* timeline= */ null);
    MediaSource clippedMediaSource =
        new ClippingMediaSource(
            mediaSource,
            /* startPositionUs= */ 3 * C.MICROS_PER_SECOND,
            /* endPositionUs= */ C.TIME_END_OF_SOURCE);
    MediaSource concatenatedMediaSource = new ConcatenatingMediaSource(clippedMediaSource);
    AtomicLong positionWhenReady = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("seekToUnpreparedWindowWithNonZeroOffsetInConcatenation")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .seek(/* positionMs= */ 10)
            .waitForSeekProcessed()
            .executeRunnable(() -> mediaSource.setNewSourceInfo(timeline, /* newManifest= */ null))
            .waitForTimelineChanged()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    positionWhenReady.set(player.getContentPosition());
                  }
                })
            .play()
            .build();
    new Builder()
        .setMediaSources(concatenatedMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(positionWhenReady.get()).isEqualTo(10);
  }

  @Test
  public void seekToUnpreparedWindowWithMultiplePeriodsInConcatenationStartsAtCorrectPeriod()
      throws Exception {
    long periodDurationMs = 5000;
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount =*/ 2,
                /* id= */ new Object(),
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 2 * periodDurationMs * 1000));
    FakeMediaSource mediaSource = new FakeMediaSource(/* timeline= */ null);
    MediaSource concatenatedMediaSource = new ConcatenatingMediaSource(mediaSource);
    AtomicInteger periodIndexWhenReady = new AtomicInteger();
    AtomicLong positionWhenReady = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("seekToUnpreparedWindowWithMultiplePeriodsInConcatenation")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Seek 10ms into the second period.
            .seek(/* positionMs= */ periodDurationMs + 10)
            .waitForSeekProcessed()
            .executeRunnable(() -> mediaSource.setNewSourceInfo(timeline, /* newManifest= */ null))
            .waitForTimelineChanged()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    periodIndexWhenReady.set(player.getCurrentPeriodIndex());
                    positionWhenReady.set(player.getContentPosition());
                  }
                })
            .play()
            .build();
    new Builder()
        .setMediaSources(concatenatedMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(periodIndexWhenReady.get()).isEqualTo(1);
    assertThat(positionWhenReady.get()).isEqualTo(periodDurationMs + 10);
  }

  @Test
  public void periodTransitionReportsCorrectBufferedPosition() throws Exception {
    int periodCount = 3;
    long periodDurationUs = 5 * C.MICROS_PER_SECOND;
    long windowDurationUs = periodCount * periodDurationUs;
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                periodCount,
                /* id= */ new Object(),
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                windowDurationUs));
    AtomicReference<Player> playerReference = new AtomicReference<>();
    AtomicLong bufferedPositionAtFirstDiscontinuityMs = new AtomicLong(C.TIME_UNSET);
    EventListener eventListener =
        new EventListener() {
          @Override
          public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
            if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
              if (bufferedPositionAtFirstDiscontinuityMs.get() == C.TIME_UNSET) {
                bufferedPositionAtFirstDiscontinuityMs.set(
                    playerReference.get().getBufferedPosition());
              }
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("periodTransitionReportsCorrectBufferedPosition")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(eventListener);
                  }
                })
            .pause()
            // Wait until all periods are fully buffered.
            .waitForIsLoading(/* targetIsLoading= */ true)
            .waitForIsLoading(/* targetIsLoading= */ false)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(bufferedPositionAtFirstDiscontinuityMs.get()).isEqualTo(C.usToMs(windowDurationUs));
  }

  @Test
  public void contentWithInitialSeekPositionAfterPrerollAdStartsAtSeekPosition() throws Exception {
    AdPlaybackState adPlaybackState =
        FakeTimeline.createAdPlaybackState(/* adsPerAdGroup= */ 3, /* adGroupTimesUs= */ 0)
            .withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, Uri.parse("https://ad1"))
            .withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1, Uri.parse("https://ad2"))
            .withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2, Uri.parse("https://ad3"));
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10_000_000,
                adPlaybackState));
    final FakeMediaSource fakeMediaSource = new FakeMediaSource(fakeTimeline);
    AtomicReference<Player> playerReference = new AtomicReference<>();
    AtomicLong contentStartPositionMs = new AtomicLong(C.TIME_UNSET);
    EventListener eventListener =
        new EventListener() {
          @Override
          public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
            if (reason == Player.DISCONTINUITY_REASON_AD_INSERTION) {
              contentStartPositionMs.set(playerReference.get().getContentPosition());
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("contentWithInitialSeekAfterPrerollAd")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(eventListener);
                  }
                })
            .seek(/* positionMs= */ 5_000)
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSources(fakeMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(contentStartPositionMs.get()).isAtLeast(5_000L);
  }

  @Test
  public void setPlaybackParametersConsecutivelyNotifiesListenerForEveryChangeOnce()
      throws Exception {
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("setPlaybackParametersNotifiesListenerForEveryChangeOnce")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .setPlaybackParameters(new PlaybackParameters(1.1f))
            .setPlaybackParameters(new PlaybackParameters(1.2f))
            .setPlaybackParameters(new PlaybackParameters(1.3f))
            .play()
            .build();
    List<PlaybackParameters> reportedPlaybackParameters = new ArrayList<>();
    EventListener listener =
        new EventListener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            reportedPlaybackParameters.add(playbackParameters);
          }
        };
    new ExoPlayerTestRunner.Builder()
        .setActionSchedule(actionSchedule)
        .setEventListener(listener)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(reportedPlaybackParameters)
        .containsExactly(
            new PlaybackParameters(1.1f),
            new PlaybackParameters(1.2f),
            new PlaybackParameters(1.3f))
        .inOrder();
  }

  @Test
  public void
      setUnsupportedPlaybackParametersConsecutivelyNotifiesListenerForEveryChangeOnceAndResetsOnceHandled()
          throws Exception {
    Renderer renderer =
        new FakeMediaClockRenderer(Builder.AUDIO_FORMAT) {
          @Override
          public long getPositionUs() {
            return 0;
          }

          @Override
          public void setPlaybackParameters(PlaybackParameters playbackParameters) {}

          @Override
          public PlaybackParameters getPlaybackParameters() {
            return PlaybackParameters.DEFAULT;
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("setUnsupportedPlaybackParametersNotifiesListenersCorrectly")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .setPlaybackParameters(new PlaybackParameters(1.1f))
            .setPlaybackParameters(new PlaybackParameters(1.2f))
            .setPlaybackParameters(new PlaybackParameters(1.3f))
            .play()
            .build();
    List<PlaybackParameters> reportedPlaybackParameters = new ArrayList<>();
    EventListener listener =
        new EventListener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            reportedPlaybackParameters.add(playbackParameters);
          }
        };
    new ExoPlayerTestRunner.Builder()
        .setSupportedFormats(Builder.AUDIO_FORMAT)
        .setRenderers(renderer)
        .setActionSchedule(actionSchedule)
        .setEventListener(listener)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(reportedPlaybackParameters)
        .containsExactly(
            new PlaybackParameters(1.1f),
            new PlaybackParameters(1.2f),
            new PlaybackParameters(1.3f),
            PlaybackParameters.DEFAULT)
        .inOrder();
  }

  @Test
  public void simplePlaybackHasNoPlaybackSuppression() throws Exception {
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("simplePlaybackHasNoPlaybackSuppression")
            .play()
            .waitForPlaybackState(Player.STATE_READY)
            .pause()
            .play()
            .build();
    AtomicBoolean seenPlaybackSuppression = new AtomicBoolean();
    EventListener listener =
        new EventListener() {
          @Override
          public void onPlaybackSuppressionReasonChanged(
              @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
            seenPlaybackSuppression.set(true);
          }
        };
    new ExoPlayerTestRunner.Builder()
        .setActionSchedule(actionSchedule)
        .setEventListener(listener)
        .build(context)
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(seenPlaybackSuppression.get()).isFalse();
  }

  @Test
  public void audioFocusDenied() throws Exception {
    ShadowAudioManager shadowAudioManager = shadowOf(context.getSystemService(AudioManager.class));
    shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);

    PlayerStateGrabber playerStateGrabber = new PlayerStateGrabber();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("audioFocusDenied")
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .play()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(playerStateGrabber)
            .build();
    AtomicBoolean seenPlaybackSuppression = new AtomicBoolean();
    EventListener listener =
        new EventListener() {
          @Override
          public void onPlaybackSuppressionReasonChanged(
              @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
            seenPlaybackSuppression.set(true);
          }
        };
    new ExoPlayerTestRunner.Builder()
        .setActionSchedule(actionSchedule)
        .setEventListener(listener)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS);

    assertThat(playerStateGrabber.playWhenReady).isFalse();
    assertThat(seenPlaybackSuppression.get()).isFalse();
  }

  @Test
  public void testDelegatingMediaSourceApproach() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 10_000));
    final ConcatenatingMediaSource underlyingSource = new ConcatenatingMediaSource();
    CompositeMediaSource<Void> delegatingMediaSource =
        new CompositeMediaSource<Void>() {
          @Override
          public void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
            super.prepareSourceInternal(mediaTransferListener);
            underlyingSource.addMediaSource(
                new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT));
            underlyingSource.addMediaSource(
                new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT));
            prepareChildSource(null, underlyingSource);
          }

          @Override
          public MediaPeriod createPeriod(
              MediaPeriodId id, Allocator allocator, long startPositionUs) {
            return underlyingSource.createPeriod(id, allocator, startPositionUs);
          }

          @Override
          public void releasePeriod(MediaPeriod mediaPeriod) {
            underlyingSource.releasePeriod(mediaPeriod);
          }

          @Override
          protected void onChildSourceInfoRefreshed(
              Void id, MediaSource mediaSource, Timeline timeline) {
            refreshSourceInfo(timeline);
          }

          @Override
          public boolean isSingleWindow() {
            return false;
          }

          @Nullable
          @Override
          public Timeline getInitialTimeline() {
            return Timeline.EMPTY;
          }
        };
    int[] currentWindowIndices = new int[1];
    long[] currentPlaybackPositions = new long[1];
    long[] windowCounts = new long[1];
    int seekToWindowIndex = 1;
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testDelegatingMediaSourceApproach")
            .seek(/* windowIndex= */ 1, /* positionMs= */ 5000)
            .waitForSeekProcessed()
            .waitForTimelineChanged(
                /* expectedTimeline= */ null, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    currentPlaybackPositions[0] = player.getCurrentPosition();
                    windowCounts[0] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setMediaSources(delegatingMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertArrayEquals(new long[] {2}, windowCounts);
    assertArrayEquals(new int[] {seekToWindowIndex}, currentWindowIndices);
    assertArrayEquals(new long[] {5_000}, currentPlaybackPositions);
  }

  @Test
  public void testSeekTo_windowIndexIsReset_deprecated() throws Exception {
    FakeTimeline fakeTimeline = new FakeTimeline(/* windowCount= */ 1);
    FakeMediaSource mediaSource = new FakeMediaSource(fakeTimeline);
    LoopingMediaSource loopingMediaSource = new LoopingMediaSource(mediaSource, 2);
    final int[] windowIndex = {C.INDEX_UNSET};
    final long[] positionMs = {C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekTo_windowIndexIsReset_deprecated")
            .pause()
            .seek(/* windowIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .playUntilPosition(/* windowIndex= */ 1, /* positionMs= */ 5000)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    //noinspection deprecation
                    player.prepare(mediaSource);
                    player.seekTo(/* positionMs= */ 5000);
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    windowIndex[0] = player.getCurrentWindowIndex();
                    positionMs[0] = player.getCurrentPosition();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSources(loopingMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS);

    assertThat(windowIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isAtLeast(5000L);
  }

  @Test
  public void testSeekTo_windowIndexIsReset() throws Exception {
    FakeTimeline fakeTimeline = new FakeTimeline(/* windowCount= */ 1);
    FakeMediaSource mediaSource = new FakeMediaSource(fakeTimeline);
    LoopingMediaSource loopingMediaSource = new LoopingMediaSource(mediaSource, 2);
    final int[] windowIndex = {C.INDEX_UNSET};
    final long[] positionMs = {C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekTo_windowIndexIsReset")
            .pause()
            .seek(/* windowIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .playUntilPosition(/* windowIndex= */ 1, /* positionMs= */ 5000)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    player.setMediaSource(mediaSource, /* startPositionMs= */ 5000);
                    player.prepare();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    windowIndex[0] = player.getCurrentWindowIndex();
                    positionMs[0] = player.getCurrentPosition();
                  }
                })
            .build();
    new Builder()
        .setMediaSources(loopingMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS);

    assertThat(windowIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isAtLeast(5000L);
  }

  @Test
  public void becomingNoisyIgnoredIfBecomingNoisyHandlingIsDisabled() throws Exception {
    CountDownLatch becomingNoisyHandlingDisabled = new CountDownLatch(1);
    CountDownLatch becomingNoisyDelivered = new CountDownLatch(1);
    PlayerStateGrabber playerStateGrabber = new PlayerStateGrabber();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("becomingNoisyIgnoredIfBecomingNoisyHandlingIsDisabled")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    player.setHandleAudioBecomingNoisy(false);
                    becomingNoisyHandlingDisabled.countDown();

                    // Wait for the broadcast to be delivered from the main thread.
                    try {
                      becomingNoisyDelivered.await();
                    } catch (InterruptedException e) {
                      throw new IllegalStateException(e);
                    }
                  }
                })
            .delay(1) // Handle pending messages on the playback thread.
            .executeRunnable(playerStateGrabber)
            .build();

    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder().setActionSchedule(actionSchedule).build(context).start();
    becomingNoisyHandlingDisabled.await();
    deliverBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    becomingNoisyDelivered.countDown();

    testRunner.blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
    assertThat(playerStateGrabber.playWhenReady).isTrue();
  }

  @Test
  public void pausesWhenBecomingNoisyIfBecomingNoisyHandlingIsEnabled() throws Exception {
    CountDownLatch becomingNoisyHandlingEnabled = new CountDownLatch(1);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("pausesWhenBecomingNoisyIfBecomingNoisyHandlingIsEnabled")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    player.setHandleAudioBecomingNoisy(true);
                    becomingNoisyHandlingEnabled.countDown();
                  }
                })
            .waitForPlayWhenReady(false) // Becoming noisy should set playWhenReady = false
            .play()
            .build();

    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder().setActionSchedule(actionSchedule).build(context).start();
    becomingNoisyHandlingEnabled.await();
    deliverBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

    // If the player fails to handle becoming noisy, blockUntilActionScheduleFinished will time out
    // and throw, causing the test to fail.
    testRunner.blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void loadControlNeverWantsToLoadOrPlay_playbackDoesNotGetStuck() throws Exception {
    LoadControl neverLoadingOrPlayingLoadControl =
        new DefaultLoadControl() {
          @Override
          public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
            return false;
          }

          @Override
          public boolean shouldStartPlayback(
              long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
            return false;
          }
        };

    // Use chunked data to ensure the player actually needs to continue loading and playing.
    FakeAdaptiveDataSet.Factory dataSetFactory =
        new FakeAdaptiveDataSet.Factory(
            /* chunkDurationUs= */ 500_000, /* bitratePercentStdDev= */ 10.0);
    MediaSource chunkedMediaSource =
        new FakeAdaptiveMediaSource(
            new FakeTimeline(/* windowCount= */ 1),
            new TrackGroupArray(new TrackGroup(Builder.VIDEO_FORMAT)),
            new FakeChunkSource.Factory(dataSetFactory, new FakeDataSource.Factory()));

    new ExoPlayerTestRunner.Builder()
        .setLoadControl(neverLoadingOrPlayingLoadControl)
        .setMediaSources(chunkedMediaSource)
        .build(context)
        .start()
        // This throws if playback doesn't finish within timeout.
        .blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void loadControlNeverWantsToPlay_playbackDoesNotGetStuck() throws Exception {
    LoadControl neverLoadingOrPlayingLoadControl =
        new DefaultLoadControl() {
          @Override
          public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
            return true;
          }

          @Override
          public boolean shouldStartPlayback(
              long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
            return false;
          }
        };

    // Use chunked data to ensure the player actually needs to continue loading and playing.
    FakeAdaptiveDataSet.Factory dataSetFactory =
        new FakeAdaptiveDataSet.Factory(
            /* chunkDurationUs= */ 500_000, /* bitratePercentStdDev= */ 10.0);
    MediaSource chunkedMediaSource =
        new FakeAdaptiveMediaSource(
            new FakeTimeline(/* windowCount= */ 1),
            new TrackGroupArray(new TrackGroup(Builder.VIDEO_FORMAT)),
            new FakeChunkSource.Factory(dataSetFactory, new FakeDataSource.Factory()));

    new ExoPlayerTestRunner.Builder()
        .setLoadControl(neverLoadingOrPlayingLoadControl)
        .setMediaSources(chunkedMediaSource)
        .build(context)
        .start()
        // This throws if playback doesn't finish within timeout.
        .blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void testMoveMediaItem() throws Exception {
    TimelineWindowDefinition firstWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ C.msToUs(10000));
    TimelineWindowDefinition secondWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 2,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ C.msToUs(10000));
    Timeline timeline1 = new FakeTimeline(firstWindowDefinition);
    Timeline timeline2 = new FakeTimeline(secondWindowDefinition);
    MediaSource mediaSource1 = new FakeMediaSource(timeline1);
    MediaSource mediaSource2 = new FakeMediaSource(timeline2);
    Timeline expectedDummyTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 1,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE),
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 2,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE));
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testMoveMediaItem")
            .waitForTimelineChanged(
                /* expectedTimeline= */ null, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .moveMediaItem(/* currentIndex= */ 0, /* newIndex= */ 1)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setMediaSources(mediaSource1, mediaSource2)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    Timeline expectedRealTimeline = new FakeTimeline(firstWindowDefinition, secondWindowDefinition);
    Timeline expectedRealTimelineAfterMove =
        new FakeTimeline(secondWindowDefinition, firstWindowDefinition);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    exoPlayerTestRunner.assertTimelinesSame(
        expectedDummyTimeline, expectedRealTimeline, expectedRealTimelineAfterMove);
  }

  @Test
  public void testRemoveMediaItem() throws Exception {
    TimelineWindowDefinition firstWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ C.msToUs(10000));
    TimelineWindowDefinition secondWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 2,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ C.msToUs(10000));
    TimelineWindowDefinition thirdWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 3,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ C.msToUs(10000));
    Timeline timeline1 = new FakeTimeline(firstWindowDefinition);
    Timeline timeline2 = new FakeTimeline(secondWindowDefinition);
    Timeline timeline3 = new FakeTimeline(thirdWindowDefinition);
    MediaSource mediaSource1 = new FakeMediaSource(timeline1);
    MediaSource mediaSource2 = new FakeMediaSource(timeline2);
    MediaSource mediaSource3 = new FakeMediaSource(timeline3);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRemoveMediaItems")
            .waitForPlaybackState(Player.STATE_READY)
            .removeMediaItem(/* index= */ 0)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setMediaSources(mediaSource1, mediaSource2, mediaSource3)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    Timeline expectedDummyTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 1,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE),
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 2,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE),
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 3,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE));
    Timeline expectedRealTimeline =
        new FakeTimeline(firstWindowDefinition, secondWindowDefinition, thirdWindowDefinition);
    Timeline expectedRealTimelineAfterRemove =
        new FakeTimeline(secondWindowDefinition, thirdWindowDefinition);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    exoPlayerTestRunner.assertTimelinesSame(
        expectedDummyTimeline, expectedRealTimeline, expectedRealTimelineAfterRemove);
  }

  @Test
  public void testRemoveMediaItems() throws Exception {
    TimelineWindowDefinition firstWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ C.msToUs(10000));
    TimelineWindowDefinition secondWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 2,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ C.msToUs(10000));
    TimelineWindowDefinition thirdWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 3,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ C.msToUs(10000));
    Timeline timeline1 = new FakeTimeline(firstWindowDefinition);
    Timeline timeline2 = new FakeTimeline(secondWindowDefinition);
    Timeline timeline3 = new FakeTimeline(thirdWindowDefinition);
    MediaSource mediaSource1 = new FakeMediaSource(timeline1);
    MediaSource mediaSource2 = new FakeMediaSource(timeline2);
    MediaSource mediaSource3 = new FakeMediaSource(timeline3);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRemoveMediaItems")
            .waitForPlaybackState(Player.STATE_READY)
            .removeMediaItems(/* fromIndex= */ 1, /* toIndex= */ 3)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setMediaSources(mediaSource1, mediaSource2, mediaSource3)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    Timeline expectedDummyTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 1,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE),
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 2,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE),
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 3,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE));
    Timeline expectedRealTimeline =
        new FakeTimeline(firstWindowDefinition, secondWindowDefinition, thirdWindowDefinition);
    Timeline expectedRealTimelineAfterRemove = new FakeTimeline(firstWindowDefinition);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    exoPlayerTestRunner.assertTimelinesSame(
        expectedDummyTimeline, expectedRealTimeline, expectedRealTimelineAfterRemove);
  }

  @Test
  public void testClearMediaItems() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testClearMediaItems")
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame(dummyTimeline, timeline, Timeline.EMPTY);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item set (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* playlist cleared */);
  }

  @Test
  public void testMultipleModificationWithRecursiveListenerInvocations() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource = new FakeMediaSource(timeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testMultipleModificationWithRecursiveListenerInvocations")
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .clearMediaItems()
            .setMediaSources(secondMediaSource)
            .waitForTimelineChanged()
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertTimelinesSame(
        dummyTimeline,
        timeline,
        Timeline.EMPTY,
        new FakeMediaSource.InitialTimeline(secondTimeline),
        secondTimeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testModifyPlaylistUnprepared_remainsInIdle_needsPrepareForBuffering()
      throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    int[] playbackStates = new int[4];
    int[] timelineWindowCounts = new int[4];
    int[] maskingPlaybackState = {C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testModifyPlaylistUnprepared_remainsInIdle_needsPrepareForBuffering")
            .waitForTimelineChanged(dummyTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 0, playbackStates, timelineWindowCounts))
            .clearMediaItems()
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 1, playbackStates, timelineWindowCounts))
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    player.setMediaSource(firstMediaSource, /* startPositionMs= */ 1000);
                    maskingPlaybackState[0] = player.getPlaybackState();
                  }
                })
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 2, playbackStates, timelineWindowCounts))
            .addMediaSources(secondMediaSource)
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 3, playbackStates, timelineWindowCounts))
            .seek(/* windowIndex= */ 1, /* positionMs= */ 2000)
            .waitForSeekProcessed()
            .prepare()
            // The first expected buffering state arrives after prepare but not before.
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_READY)
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setMediaSources(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    assertArrayEquals(
        new int[] {Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE},
        playbackStates);
    assertArrayEquals(new int[] {1, 0, 1, 2}, timelineWindowCounts);
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE,
        Player.STATE_BUFFERING /* first buffering state after prepare */,
        Player.STATE_READY,
        Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* initial setMediaSources */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* clear */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* set media items */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* add media items */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source update after prepare */);
    Timeline expectedSecondDummyTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE),
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ false,
                /* durationUs= */ C.TIME_UNSET,
                AdPlaybackState.NONE));
    Timeline expectedSecondRealTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10_000_000),
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10_000_000));
    exoPlayerTestRunner.assertTimelinesSame(
        dummyTimeline,
        Timeline.EMPTY,
        dummyTimeline,
        expectedSecondDummyTimeline,
        expectedSecondRealTimeline);
    assertArrayEquals(new int[] {Player.STATE_IDLE}, maskingPlaybackState);
  }

  @Test
  public void testModifyPlaylistPrepared_remainsInEnded_needsSeekForBuffering() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    FakeMediaSource secondMediaSource = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testModifyPlaylistPrepared_remainsInEnded_needsSeekForBuffering")
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .waitForPlaybackState(Player.STATE_ENDED)
            .addMediaSources(secondMediaSource) // add must not transition to buffering
            .waitForTimelineChanged()
            .clearMediaItems() // clear must remain in ended
            .addMediaSources(secondMediaSource) // add again to be able to test the seek
            .waitForTimelineChanged()
            .seek(/* positionMs= */ 2_000) // seek must transition to buffering
            .waitForSeekProcessed()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_READY)
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setExpectedPlayerEndedCount(/* expectedPlayerEndedCount= */ 2)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE,
        Player.STATE_BUFFERING, // first buffering
        Player.STATE_READY,
        Player.STATE_ENDED, // clear playlist
        Player.STATE_BUFFERING, // second buffering after seek
        Player.STATE_READY,
        Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame(
        dummyTimeline,
        timeline,
        Timeline.EMPTY,
        dummyTimeline,
        timeline,
        Timeline.EMPTY,
        dummyTimeline,
        timeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item set (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* playlist cleared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media items added */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* playlist cleared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media items added */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */);
  }

  @Test
  public void testStopWithNoReset_modifyingPlaylistRemainsInIdleState_needsPrepareForBuffering()
      throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    FakeMediaSource secondMediaSource = new FakeMediaSource(timeline);
    int[] playbackStateHolder = new int[3];
    int[] windowCountHolder = new int[3];
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testStopWithNoReset_modifyingPlaylistRemainsInIdleState_needsPrepareForBuffering")
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ false)
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 0, playbackStateHolder, windowCountHolder))
            .clearMediaItems()
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 1, playbackStateHolder, windowCountHolder))
            .addMediaSources(secondMediaSource)
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 2, playbackStateHolder, windowCountHolder))
            .prepare()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_READY)
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    assertArrayEquals(
        new int[] {Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE}, playbackStateHolder);
    assertArrayEquals(new int[] {1, 0, 1}, windowCountHolder);
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE,
        Player.STATE_BUFFERING, // first buffering
        Player.STATE_READY,
        Player.STATE_IDLE, // stop
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame(
        dummyTimeline, timeline, Timeline.EMPTY, dummyTimeline, timeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item set (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, /* source prepared */
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* clear media items */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item add (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */);
  }

  @Test
  public void testPrepareWithInvalidInitialSeek_expectEndedImmediately() throws Exception {
    final int[] currentWindowIndices = {C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testPrepareWithInvalidInitialSeek_expectEnded")
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .skipSettingMediaSources()
            .initialSeek(/* windowIndex= */ 1, C.TIME_UNSET)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertPlaybackStatesEqual(Player.STATE_IDLE, Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame();
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual();
    assertArrayEquals(new int[] {1}, currentWindowIndices);
  }

  @Test
  public void testPrepareWhenAlreadyPreparedIsANoop() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testPrepareWhenAlreadyPreparedIsANoop")
            .waitForPlaybackState(Player.STATE_READY)
            .prepare()
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame(dummyTimeline, timeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item set (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */);
  }

  @Test
  public void testSeekToIndexLargerThanNumberOfPlaylistItems() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 10_000));
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(
            /* isAtomic= */ false,
            new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT),
            new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT));
    int[] currentWindowIndices = new int[1];
    long[] currentPlaybackPositions = new long[1];
    int seekToWindowIndex = 1;
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekToIndexLargerThanNumberOfPlaylistItems")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    currentPlaybackPositions[0] = player.getCurrentPosition();
                  }
                })
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSources(concatenatingMediaSource)
            .initialSeek(seekToWindowIndex, 5000)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new long[] {5_000}, currentPlaybackPositions);
    assertArrayEquals(new int[] {seekToWindowIndex}, currentWindowIndices);
  }

  @Test
  public void testSeekToIndexWithEmptyMultiWindowMediaSource() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 10_000));
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(/* isAtomic= */ false);
    int[] currentWindowIndices = new int[2];
    long[] currentPlaybackPositions = new long[2];
    long[] windowCounts = new long[2];
    int seekToWindowIndex = 1;
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekToIndexWithEmptyMultiWindowMediaSource")
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    currentPlaybackPositions[0] = player.getCurrentPosition();
                    windowCounts[0] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .executeRunnable(
                () -> {
                  concatenatingMediaSource.addMediaSource(
                      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT));
                  concatenatingMediaSource.addMediaSource(
                      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT));
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                    currentPlaybackPositions[1] = player.getCurrentPosition();
                    windowCounts[1] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSources(concatenatingMediaSource)
        .initialSeek(seekToWindowIndex, 5000)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new long[] {0, 2}, windowCounts);
    assertArrayEquals(new int[] {seekToWindowIndex, seekToWindowIndex}, currentWindowIndices);
    assertArrayEquals(new long[] {5_000, 5_000}, currentPlaybackPositions);
  }

  @Test
  public void testEmptyMultiWindowMediaSource_doesNotEnterBufferState() throws Exception {
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(/* isAtomic= */ false);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testEmptyMultiWindowMediaSource_doesNotEnterBufferState")
            .waitForTimelineChanged()
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setMediaSources(concatenatingMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    exoPlayerTestRunner.assertPlaybackStatesEqual(1, 4);
  }

  @Test
  public void testSeekToIndexWithEmptyMultiWindowMediaSource_usesLazyPreparation()
      throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 10_000));
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(/* isAtomic= */ false);
    int[] currentWindowIndices = new int[2];
    long[] currentPlaybackPositions = new long[2];
    long[] windowCounts = new long[2];
    int seekToWindowIndex = 1;
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekToIndexWithEmptyMultiWindowMediaSource")
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    currentPlaybackPositions[0] = player.getCurrentPosition();
                    windowCounts[0] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .executeRunnable(
                () -> {
                  concatenatingMediaSource.addMediaSource(
                      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT));
                  concatenatingMediaSource.addMediaSource(
                      new FakeMediaSource(fakeTimeline, Builder.VIDEO_FORMAT));
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                    currentPlaybackPositions[1] = player.getCurrentPosition();
                    windowCounts[1] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSources(concatenatingMediaSource)
        .setUseLazyPreparation(/* useLazyPreparation= */ true)
        .initialSeek(seekToWindowIndex, 5000)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new long[] {0, 2}, windowCounts);
    assertArrayEquals(new int[] {seekToWindowIndex, seekToWindowIndex}, currentWindowIndices);
    assertArrayEquals(new long[] {5_000, 5_000}, currentPlaybackPositions);
  }

  @Test
  public void testSetMediaSources_empty_whenEmpty_correctMaskingWindowIndex() throws Exception {
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSetMediaSources_empty_whenEmpty_correctMaskingWindowIndex")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    List<MediaSource> listOfTwo = new ArrayList<>();
                    listOfTwo.add(secondMediaSource);
                    listOfTwo.add(secondMediaSource);
                    player.addMediaSources(/* index= */ 0, listOfTwo);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .setMediaSources(new ConcatenatingMediaSource())
        .setActionSchedule(actionSchedule)
        .build(context)
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {0, 0, 0}, currentWindowIndices);
  }

  @Test
  public void testSetMediaSources_empty_whenEmpty_validInitialSeek_correctMaskingWindowIndex()
      throws Exception {
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_empty_whenEmpty_validInitialSeek_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    List<MediaSource> listOfTwo = new ArrayList<>();
                    listOfTwo.add(secondMediaSource);
                    listOfTwo.add(secondMediaSource);
                    player.addMediaSources(/* index= */ 0, listOfTwo);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .initialSeek(/* windowIndex= */ 1, C.TIME_UNSET)
        .setMediaSources(new ConcatenatingMediaSource())
        .setActionSchedule(actionSchedule)
        .build(context)
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 1, 1}, currentWindowIndices);
  }

  @Test
  public void testSetMediaSources_empty_whenEmpty_invalidInitialSeek_correctMaskingWindowIndex()
      throws Exception {
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_empty_whenEmpty_invalidInitialSeek_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    List<MediaSource> listOfTwo = new ArrayList<>();
                    listOfTwo.add(secondMediaSource);
                    listOfTwo.add(secondMediaSource);
                    player.addMediaSources(/* index= */ 0, listOfTwo);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .initialSeek(/* windowIndex= */ 4, C.TIME_UNSET)
        .setMediaSources(new ConcatenatingMediaSource())
        .setActionSchedule(actionSchedule)
        .build(context)
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {4, 0, 0}, currentWindowIndices);
  }

  @Test
  public void testSetMediaSources_whenEmpty_correctMaskingWindowIndex() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSetMediaSources_whenEmpty_correctMaskingWindowIndex")
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Increase current window index.
                    player.addMediaSource(/* index= */ 0, secondMediaSource);
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Current window index is unchanged.
                    player.addMediaSource(/* index= */ 2, secondMediaSource);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    MediaSource mediaSource =
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1));
                    ConcatenatingMediaSource concatenatingMediaSource =
                        new ConcatenatingMediaSource(mediaSource, mediaSource, mediaSource);
                    // Increase current window with multi window source.
                    player.addMediaSource(/* index= */ 0, concatenatingMediaSource);
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    ConcatenatingMediaSource concatenatingMediaSource =
                        new ConcatenatingMediaSource();
                    // Current window index is unchanged when adding empty source.
                    player.addMediaSource(/* index= */ 0, concatenatingMediaSource);
                    currentWindowIndices[3] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .setMediaSources(firstMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 1, 4, 4}, currentWindowIndices);
  }

  @Test
  public void testSetMediaSources_whenEmpty_validInitialSeek_correctMaskingWindowIndex()
      throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, new Object());
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_whenEmpty_validInitialSeek_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    // Increase current window index.
                    player.addMediaSource(/* index= */ 0, secondMediaSource);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .initialSeek(/* windowIndex= */ 1, C.TIME_UNSET)
        .setMediaSources(firstMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 2, 2}, currentWindowIndices);
  }

  @Test
  public void testSetMediaSources_whenEmpty_invalidInitialSeek_correctMaskingWindowIndex()
      throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, new Object());
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_whenEmpty_invalidInitialSeek_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    // Increase current window index.
                    player.addMediaSource(/* index= */ 0, secondMediaSource);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    new Builder()
        .initialSeek(/* windowIndex= */ 1, C.TIME_UNSET)
        .setMediaSources(firstMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {0, 1, 1}, currentWindowIndices);
  }

  @Test
  public void testSetMediaSources_correctMaskingWindowIndex() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, new Object());
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSetMediaSources_correctMaskingWindowIndex")
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    // Increase current window index.
                    player.addMediaSource(/* index= */ 0, secondMediaSource);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .setMediaSources(firstMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {0, 1, 1}, currentWindowIndices);
  }

  @Test
  public void testSetMediaSources_whenIdle_correctMaskingPlaybackState() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1, 1L);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, 2L);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] maskingPlaybackStates = new int[4];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSetMediaSources_whenIdle_correctMaskingPlaybackState")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set empty media item with no seek.
                    player.setMediaSource(new ConcatenatingMediaSource());
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set media item with an implicit seek to the current position.
                    player.setMediaSource(firstMediaSource);
                    maskingPlaybackStates[1] = player.getPlaybackState();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set media item with an explicit seek.
                    player.setMediaSource(secondMediaSource, /* startPositionMs= */ C.TIME_UNSET);
                    maskingPlaybackStates[2] = player.getPlaybackState();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set empty media item with an explicit seek.
                    player.setMediaSource(
                        new ConcatenatingMediaSource(), /* startPositionMs= */ C.TIME_UNSET);
                    maskingPlaybackStates[3] = player.getPlaybackState();
                  }
                })
            .prepare()
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .skipSettingMediaSources()
            .setActionSchedule(actionSchedule)
            .build(context)
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(Player.STATE_IDLE, Player.STATE_ENDED);
    assertArrayEquals(
        new int[] {Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE},
        maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void testSetMediaSources_whenIdle_invalidSeek_correctMaskingPlaybackState()
      throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_whenIdle_invalidSeek_correctMaskingPlaybackState")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set a media item with an implicit seek to the current position which is
                    // invalid in the new timeline.
                    player.setMediaSource(
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1, 1L)));
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .initialSeek(/* windowIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .setMediaSources(new ConcatenatingMediaSource())
            .setActionSchedule(actionSchedule)
            .build(context)
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_IDLE}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testSetMediaSources_whenIdle_noSeek_correctMaskingPlaybackState() throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_whenIdle_noSeek_correctMaskingPlaybackState")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set media item with no seek.
                    player.setMediaSource(
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1)));
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .skipSettingMediaSources()
            .setActionSchedule(actionSchedule)
            .build(context)
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_IDLE}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testSetMediaSources_whenIdle_noSeekEmpty_correctMaskingPlaybackState()
      throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_whenIdle_noSeekEmpty_correctMaskingPlaybackState")
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set an empty media item with no seek.
                    player.setMediaSource(new ConcatenatingMediaSource());
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .setMediaSources(new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1)))
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .skipSettingMediaSources()
            .setActionSchedule(actionSchedule)
            .build(context)
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_IDLE}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testSetMediaSources_whenEnded_correctMaskingPlaybackState() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1, 1L);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, 2L);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] maskingPlaybackStates = new int[4];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSetMediaSources_whenEnded_correctMaskingPlaybackState")
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set empty media item with an implicit seek to the current position.
                    player.setMediaSource(new ConcatenatingMediaSource());
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set empty media item with an explicit seek.
                    player.setMediaSource(
                        new ConcatenatingMediaSource(), /* startPositionMs= */ C.TIME_UNSET);
                    maskingPlaybackStates[1] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set media item with an implicit seek to the current position.
                    player.setMediaSource(firstMediaSource);
                    maskingPlaybackStates[2] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set media item with an explicit seek.
                    player.setMediaSource(secondMediaSource, /* startPositionMs= */ C.TIME_UNSET);
                    maskingPlaybackStates[3] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setExpectedPlayerEndedCount(/* expectedPlayerEndedCount= */ 3)
            .setMediaSources(new ConcatenatingMediaSource())
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE,
        Player.STATE_ENDED,
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_ENDED,
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_ENDED);
    assertArrayEquals(
        new int[] {
          Player.STATE_ENDED, Player.STATE_ENDED, Player.STATE_BUFFERING, Player.STATE_BUFFERING
        },
        maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testSetMediaSources_whenEnded_invalidSeek_correctMaskingPlaybackState()
      throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_whenEnded_invalidSeek_correctMaskingPlaybackState")
            .waitForSeekProcessed()
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set media item with an invalid implicit seek to the current position.
                    player.setMediaSource(
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1, 1L)),
                        /* resetPosition= */ false);
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .initialSeek(/* windowIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .setMediaSources(new ConcatenatingMediaSource())
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(Player.STATE_IDLE, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testSetMediaSources_whenEnded_noSeek_correctMaskingPlaybackState() throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_whenEnded_noSeek_correctMaskingPlaybackState")
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set media item with no seek (keep current position).
                    player.setMediaSource(
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1)),
                        /* resetPosition= */ false);
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testSetMediaSources_whenEnded_noSeekEmpty_correctMaskingPlaybackState()
      throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_whenEnded_noSeekEmpty_correctMaskingPlaybackState")
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set an empty media item with no seek.
                    player.setMediaSource(new ConcatenatingMediaSource());
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void testSetMediaSources_whenPrepared_correctMaskingPlaybackState() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1, 1L);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, 2L);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] maskingPlaybackStates = new int[4];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSetMediaSources_whenPrepared_correctMaskingPlaybackState")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set empty media item with an implicit seek to current position.
                    player.setMediaSource(
                        new ConcatenatingMediaSource(), /* resetPosition= */ false);
                    // Expect masking state is ended,
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .setMediaSources(/* windowIndex= */ 0, /* positionMs= */ C.TIME_UNSET, firstMediaSource)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set empty media item with an explicit seek.
                    player.setMediaSource(
                        new ConcatenatingMediaSource(), /* startPositionMs= */ C.TIME_UNSET);
                    // Expect masking state is ended,
                    maskingPlaybackStates[1] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .setMediaSources(/* windowIndex= */ 0, /* positionMs= */ C.TIME_UNSET, firstMediaSource)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set media item with an explicit seek.
                    player.setMediaSource(secondMediaSource, /* startPositionMs= */ C.TIME_UNSET);
                    // Expect masking state is buffering,
                    maskingPlaybackStates[2] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .setMediaSources(/* windowIndex= */ 0, /* positionMs= */ C.TIME_UNSET, firstMediaSource)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Set media item with an implicit seek to the current position.
                    player.setMediaSource(secondMediaSource, /* resetPosition= */ false);
                    // Expect masking state is buffering,
                    maskingPlaybackStates[3] = player.getPlaybackState();
                  }
                })
            .play()
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setExpectedPlayerEndedCount(/* expectedPlayerEndedCount= */ 3)
            .setMediaSources(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE,
        Player.STATE_IDLE, // Pause.
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready after initial prepare.
        Player.STATE_ENDED, // Ended after setting empty source without seek.
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready again after re-setting source.
        Player.STATE_ENDED, // Ended after setting empty source with seek.
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready again after re-setting source.
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready after setting media item with seek.
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready again after re-setting source.
        Player.STATE_BUFFERING,
        Player.STATE_BUFFERING, // Play.
        Player.STATE_READY, // Ready after setting media item without seek.
        Player.STATE_ENDED);
    assertArrayEquals(
        new int[] {
          Player.STATE_ENDED, Player.STATE_ENDED, Player.STATE_BUFFERING, Player.STATE_BUFFERING
        },
        maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Initial source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, // Empty source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Reset source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, // Empty source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Reset source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Set source with seek.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Reset source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE); // Set source without seek.
  }

  @Test
  public void testSetMediaSources_whenPrepared_invalidSeek_correctMaskingPlaybackState()
      throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1, 1L);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testSetMediaSources_whenPrepared_invalidSeek_correctMaskingPlaybackState")
            .pause()
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // An implicit, invalid seek picking up the position set by the initial seek.
                    player.setMediaSource(firstMediaSource, /* resetPosition= */ false);
                    // Expect masking state is ended,
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .setMediaSources(/* windowIndex= */ 0, /* positionMs= */ C.TIME_UNSET, firstMediaSource)
            .waitForPlaybackState(Player.STATE_READY)
            .play()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setExpectedPlayerEndedCount(/* expectedPlayerEndedCount= */ 2)
            .initialSeek(/* windowIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .setMediaSources(new ConcatenatingMediaSource())
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE,
        Player.STATE_IDLE, // Pause.
        Player.STATE_ENDED, // Empty source has been prepared.
        Player.STATE_BUFFERING, // After setting another source.
        Player.STATE_READY,
        Player.STATE_READY, // Play.
        Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void testAddMediaSources_skipSettingMediaItems_validInitialSeek_correctMaskingWindowIndex()
      throws Exception {
    final int[] currentWindowIndices = new int[5];
    Arrays.fill(currentWindowIndices, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testAddMediaSources_skipSettingMediaItems_validInitialSeek_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    player.addMediaSource(/* index= */ 0, new ConcatenatingMediaSource());
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                    player.addMediaSource(
                        /* index= */ 0,
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 2)));
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                    player.addMediaSource(
                        /* index= */ 0,
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1)));
                    currentWindowIndices[3] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[4] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .skipSettingMediaSources()
        .initialSeek(/* windowIndex= */ 1, C.TIME_UNSET)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 1, 1, 2, 2}, currentWindowIndices);
  }

  @Test
  public void
      testAddMediaSources_skipSettingMediaItems_invalidInitialSeek_correctMaskingWindowIndex()
          throws Exception {
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testAddMediaSources_skipSettingMediaItems_invalidInitialSeek_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    player.addMediaSource(secondMediaSource);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .skipSettingMediaSources()
        .initialSeek(/* windowIndex= */ 1, C.TIME_UNSET)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 0, 0}, currentWindowIndices);
  }

  @Test
  public void testMoveMediaItems_correctMaskingWindowIndex() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(timeline);
    MediaSource secondMediaSource = new FakeMediaSource(timeline);
    MediaSource thirdMediaSource = new FakeMediaSource(timeline);
    final int[] currentWindowIndices = {
      C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET
    };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testMoveMediaItems_correctMaskingWindowIndex")
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Move the current item down in the playlist.
                    player.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 2, /* newIndex= */ 1);
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Move the current item up in the playlist.
                    player.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 3, /* newIndex= */ 0);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .seek(/* windowIndex= */ 2, C.TIME_UNSET)
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Move items from before to behind the current item.
                    player.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 2, /* newIndex= */ 1);
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Move items from behind to before the current item.
                    player.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 3, /* newIndex= */ 0);
                    currentWindowIndices[3] = player.getCurrentWindowIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Move items from before to before the current item.
                    // No change in currentWindowIndex.
                    player.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 1, /* newIndex= */ 1);
                    currentWindowIndices[4] = player.getCurrentWindowIndex();
                  }
                })
            .seek(/* windowIndex= */ 0, C.TIME_UNSET)
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Move items from behind to behind the current item.
                    // No change in currentWindowIndex.
                    player.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 2, /* newIndex= */ 2);
                    currentWindowIndices[5] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .setMediaSources(firstMediaSource, secondMediaSource, thirdMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 0, 0, 2, 2, 0}, currentWindowIndices);
  }

  @Test
  public void testMoveMediaItems_unprepared_correctMaskingWindowIndex() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testMoveMediaItems_unprepared_correctMaskingWindowIndex")
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Increase current window index.
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    player.moveMediaItem(/* currentIndex= */ 0, /* newIndex= */ 1);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .setMediaSources(firstMediaSource, secondMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {0, 1, 1}, currentWindowIndices);
  }

  @Test
  public void testRemoveMediaItems_correctMaskingWindowIndex() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRemoveMediaItems_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Decrease current window index.
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    player.removeMediaItem(/* index= */ 0);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    new Builder()
        .initialSeek(/* windowIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
        .setMediaSources(firstMediaSource, secondMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 0}, currentWindowIndices);
  }

  @Test
  public void testRemoveMediaItems_currentItemRemoved_correctMaskingWindowIndex() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] currentPositions = {C.TIME_UNSET, C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testRemoveMediaItems_CurrentItemRemoved_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Remove the current item.
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    currentPositions[0] = player.getCurrentPosition();
                    player.removeMediaItem(/* index= */ 1);
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                    currentPositions[1] = player.getCurrentPosition();
                  }
                })
            .build();
    new Builder()
        .initialSeek(/* windowIndex= */ 1, /* positionMs= */ 5000)
        .setMediaSources(firstMediaSource, secondMediaSource, firstMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 1}, currentWindowIndices);
    assertThat(currentPositions[0]).isAtLeast(5000L);
    assertThat(currentPositions[1]).isEqualTo(0);
  }

  @Test
  public void testRemoveMediaItems_currentItemRemovedThatIsTheLast_correctMasking()
      throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1, 1L);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, 2L);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    Timeline thirdTimeline = new FakeTimeline(/* windowCount= */ 1, 3L);
    MediaSource thirdMediaSource = new FakeMediaSource(thirdTimeline);
    Timeline fourthTimeline = new FakeTimeline(/* windowCount= */ 1, 3L);
    MediaSource fourthMediaSource = new FakeMediaSource(fourthTimeline);
    final int[] currentWindowIndices = new int[9];
    Arrays.fill(currentWindowIndices, C.INDEX_UNSET);
    final int[] maskingPlaybackStates = new int[4];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testRemoveMediaItems_CurrentItemRemovedThatIsTheLast_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Expect the current window index to be 2 after seek.
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    player.removeMediaItem(/* index= */ 2);
                    // Expect the current window index to be 0
                    // (default position of timeline after not finding subsequent period).
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                    // Transition to ENDED.
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Expects the current window index still on 0.
                    currentWindowIndices[2] = player.getCurrentWindowIndex();
                    // Insert an item at begin when the playlist is not empty.
                    player.addMediaSource(/* index= */ 0, thirdMediaSource);
                    // Expects the current window index to be (0 + 1) after insertion at begin.
                    currentWindowIndices[3] = player.getCurrentWindowIndex();
                    // Remains in ENDED.
                    maskingPlaybackStates[1] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[4] = player.getCurrentWindowIndex();
                    // Implicit seek to the current window index, which is out of bounds in new
                    // timeline.
                    player.setMediaSource(fourthMediaSource, /* resetPosition= */ false);
                    // 0 after reset.
                    currentWindowIndices[5] = player.getCurrentWindowIndex();
                    // Invalid seek, so we remain in ENDED.
                    maskingPlaybackStates[2] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[6] = player.getCurrentWindowIndex();
                    // Explicit seek to (0, C.TIME_UNSET). Player transitions to BUFFERING.
                    player.setMediaSource(fourthMediaSource, /* startPositionMs= */ 5000);
                    // 0 after explicit seek.
                    currentWindowIndices[7] = player.getCurrentWindowIndex();
                    // Transitions from ENDED to BUFFERING after explicit seek.
                    maskingPlaybackStates[3] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Check whether actual window index is equal masking index from above.
                    currentWindowIndices[8] = player.getCurrentWindowIndex();
                  }
                })
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .initialSeek(/* windowIndex= */ 2, /* positionMs= */ C.TIME_UNSET)
            .setExpectedPlayerEndedCount(2)
            .setMediaSources(firstMediaSource, secondMediaSource, thirdMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first window.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE,
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready after initial prepare.
        Player.STATE_ENDED, // ended after removing current window index
        Player.STATE_BUFFERING, // buffers after set items with seek
        Player.STATE_READY,
        Player.STATE_ENDED);
    assertArrayEquals(
        new int[] {
          Player.STATE_ENDED, // ended after removing current window index
          Player.STATE_ENDED, // adding items does not change state
          Player.STATE_ENDED, // set items with seek to current position.
          Player.STATE_BUFFERING
        }, // buffers after set items with seek
        maskingPlaybackStates);
    assertArrayEquals(new int[] {2, 0, 0, 1, 1, 0, 0, 0, 0}, currentWindowIndices);
  }

  @Test
  public void testRemoveMediaItems_removeTailWithCurrentWindow_whenIdle_finishesPlayback()
      throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, new Object());
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(
                "testRemoveMediaItems_removeTailWithCurrentWindow_whenIdle_finishesPlayback")
            .seek(/* windowIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .waitForSeekProcessed()
            .removeMediaItem(/* index= */ 1)
            .prepare()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new Builder()
            .setMediaSources(firstMediaSource, secondMediaSource)
            .setActionSchedule(actionSchedule)
            .build(context)
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
  }

  @Test
  public void testClearMediaItems_correctMasking() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET};
    final int[] maskingPlaybackState = {C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testClearMediaItems_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    player.clearMediaItems();
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                    maskingPlaybackState[0] = player.getPlaybackState();
                  }
                })
            .build();
    new Builder()
        .initialSeek(/* windowIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
        .setMediaSources(firstMediaSource, secondMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 0}, currentWindowIndices);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackState);
  }

  @Test
  public void testClearMediaItems_unprepared_correctMaskingWindowIndex_notEnded() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentWindowIndices = {C.INDEX_UNSET, C.INDEX_UNSET};
    final int[] currentStates = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testClearMediaItems_correctMaskingWindowIndex")
            .waitForSeekProcessed()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    currentWindowIndices[0] = player.getCurrentWindowIndex();
                    currentStates[0] = player.getPlaybackState();
                    player.clearMediaItems();
                    currentWindowIndices[1] = player.getCurrentWindowIndex();
                    currentStates[1] = player.getPlaybackState();
                  }
                })
            .prepare()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Transitions to ended when prepared with zero media items.
                    currentStates[2] = player.getPlaybackState();
                  }
                })
            .build();
    new Builder()
        .initialSeek(/* windowIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
        .setMediaSources(firstMediaSource, secondMediaSource)
        .setActionSchedule(actionSchedule)
        .build(context)
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(
        new int[] {Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_ENDED}, currentStates);
    assertArrayEquals(new int[] {1, 0}, currentWindowIndices);
  }

  // Internal methods.

  private static ActionSchedule.Builder addSurfaceSwitch(ActionSchedule.Builder builder) {
    final Surface surface1 = new Surface(new SurfaceTexture(/* texName= */ 0));
    final Surface surface2 = new Surface(new SurfaceTexture(/* texName= */ 1));
    return builder
        .executeRunnable(
            new PlayerRunnable() {
              @Override
              public void run(SimpleExoPlayer player) {
                player.setVideoSurface(surface1);
              }
            })
        .executeRunnable(
            new PlayerRunnable() {
              @Override
              public void run(SimpleExoPlayer player) {
                player.setVideoSurface(surface2);
              }
            });
  }

  private static void deliverBroadcast(Intent intent) {
    ApplicationProvider.getApplicationContext().sendBroadcast(intent);
    shadowOf(Looper.getMainLooper()).idle();
  }

  // Internal classes.

  private static final class PositionGrabbingMessageTarget extends PlayerTarget {

    public int windowIndex;
    public long positionMs;
    public int messageCount;

    public PositionGrabbingMessageTarget() {
      windowIndex = C.INDEX_UNSET;
      positionMs = C.POSITION_UNSET;
    }

    @Override
    public void handleMessage(SimpleExoPlayer player, int messageType, @Nullable Object message) {
      if (player != null) {
        windowIndex = player.getCurrentWindowIndex();
        positionMs = player.getCurrentPosition();
      }
      messageCount++;
    }
  }

  private static final class PlayerStateGrabber extends PlayerRunnable {

    public boolean playWhenReady;
    @Player.State public int playbackState;
    @Nullable public Timeline timeline;

    @Override
    public void run(SimpleExoPlayer player) {
      playWhenReady = player.getPlayWhenReady();
      playbackState = player.getPlaybackState();
      timeline = player.getCurrentTimeline();
    }
  }
  /**
   * Provides a wrapper for a {@link Runnable} which does collect playback states and window counts.
   * Can be used with {@link ActionSchedule.Builder#executeRunnable(Runnable)} to verify that a
   * playback state did not change and hence no observable callback is called.
   *
   * <p>This is specifically useful in cases when the test may end before a given state arrives or
   * when an action of the action schedule might execute before a callback is called.
   */
  public static class PlaybackStateCollector extends PlayerRunnable {

    private final int[] playbackStates;
    private final int[] timelineWindowCount;
    private final int index;

    /**
     * Creates the collector.
     *
     * @param index The index to populate.
     * @param playbackStates An array of playback states to populate.
     * @param timelineWindowCount An array of window counts to populate.
     */
    public PlaybackStateCollector(int index, int[] playbackStates, int[] timelineWindowCount) {
      Assertions.checkArgument(playbackStates.length > index && timelineWindowCount.length > index);
      this.playbackStates = playbackStates;
      this.timelineWindowCount = timelineWindowCount;
      this.index = index;
    }

    @Override
    public void run(SimpleExoPlayer player) {
      playbackStates[index] = player.getPlaybackState();
      timelineWindowCount[index] = player.getCurrentTimeline().getWindowCount();
    }
  }
}
