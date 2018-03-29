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
import static org.junit.Assert.fail;

import android.view.Surface;
import com.google.android.exoplayer2.Player.DefaultEventListener;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.ActionSchedule.PlayerRunnable;
import com.google.android.exoplayer2.testutil.ActionSchedule.PlayerTarget;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner.Builder;
import com.google.android.exoplayer2.testutil.FakeMediaClockRenderer;
import com.google.android.exoplayer2.testutil.FakeMediaPeriod;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeRenderer;
import com.google.android.exoplayer2.testutil.FakeShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.FakeTrackSelection;
import com.google.android.exoplayer2.testutil.FakeTrackSelector;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.upstream.Allocator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit test for {@link ExoPlayer}. */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public final class ExoPlayerTest {

  /**
   * For tests that rely on the player transitioning to the ended state, the duration in
   * milliseconds after starting the player before the test will time out. This is to catch cases
   * where the player under test is not making progress, in which case the test should fail.
   */
  private static final int TIMEOUT_MS = 10000;

  /**
   * Tests playback of a source that exposes an empty timeline. Playback is expected to end without
   * error.
   */
  @Test
  public void testPlayEmptyTimeline() throws Exception {
    Timeline timeline = Timeline.EMPTY;
    FakeRenderer renderer = new FakeRenderer();
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setTimeline(timeline)
            .setRenderers(renderer)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertNoPositionDiscontinuities();
    testRunner.assertTimelinesEqual(timeline);
    assertThat(renderer.formatReadCount).isEqualTo(0);
    assertThat(renderer.sampleBufferReadCount).isEqualTo(0);
    assertThat(renderer.isEnded).isFalse();
  }

  /** Tests playback of a source that exposes a single period. */
  @Test
  public void testPlaySinglePeriodTimeline() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    Object manifest = new Object();
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setTimeline(timeline)
            .setManifest(manifest)
            .setRenderers(renderer)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertNoPositionDiscontinuities();
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertManifestsEqual(manifest);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
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
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
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
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    Integer[] expectedReasons = new Integer[99];
    Arrays.fill(expectedReasons, Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    testRunner.assertPositionDiscontinuityReasonsEqual(expectedReasons);
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
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
    TimelineWindowDefinition windowDefinition =
        new TimelineWindowDefinition(
            /* isSeekable= */ false, /* isDynamic= */ false, /* durationUs= */ 100_000);
    Timeline timeline = new FakeTimeline(windowDefinition, windowDefinition, windowDefinition);
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
          public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
            return PlaybackParameters.DEFAULT;
          }

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
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    testRunner.assertTimelinesEqual(timeline);
    assertThat(audioRenderer.positionResetCount).isEqualTo(1);
    assertThat(videoRenderer.isEnded).isTrue();
    assertThat(audioRenderer.isEnded).isTrue();
  }

  @Test
  public void testRepreparationGivesFreshSourceInfo() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    Object firstSourceManifest = new Object();
    MediaSource firstSource =
        new FakeMediaSource(timeline, firstSourceManifest, Builder.VIDEO_FORMAT);
    final CountDownLatch queuedSourceInfoCountDownLatch = new CountDownLatch(1);
    final CountDownLatch completePreparationCountDownLatch = new CountDownLatch(1);
    MediaSource secondSource =
        new FakeMediaSource(timeline, new Object(), Builder.VIDEO_FORMAT) {
          @Override
          public synchronized void prepareSourceInternal(
              ExoPlayer player, boolean isTopLevelSource) {
            super.prepareSourceInternal(player, isTopLevelSource);
            // We've queued a source info refresh on the playback thread's event queue. Allow the
            // test thread to prepare the player with the third source, and block this thread (the
            // playback thread) until the test thread's call to prepare() has returned.
            queuedSourceInfoCountDownLatch.countDown();
            try {
              completePreparationCountDownLatch.await();
            } catch (InterruptedException e) {
              throw new IllegalStateException(e);
            }
          }
        };
    Object thirdSourceManifest = new Object();
    MediaSource thirdSource =
        new FakeMediaSource(timeline, thirdSourceManifest, Builder.VIDEO_FORMAT);

    // Prepare the player with a source with the first manifest and a non-empty timeline. Prepare
    // the player again with a source and a new manifest, which will never be exposed. Allow the
    // test thread to prepare the player with a third source, and block the playback thread until
    // the test thread's call to prepare() has returned.
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRepreparation")
            .waitForTimelineChanged(timeline)
            .prepareSource(secondSource)
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      queuedSourceInfoCountDownLatch.await();
                    } catch (InterruptedException e) {
                      // Ignore.
                    }
                  }
                })
            .prepareSource(thirdSource)
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    completePreparationCountDownLatch.countDown();
                  }
                })
            .build();
    ExoPlayerTestRunner testRunner =
        new Builder()
            .setMediaSource(firstSource)
            .setRenderers(renderer)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertNoPositionDiscontinuities();
    // The first source's preparation completed with a non-empty timeline. When the player was
    // re-prepared with the second source, it immediately exposed an empty timeline, but the source
    // info refresh from the second source was suppressed as we re-prepared with the third source.
    testRunner.assertTimelinesEqual(timeline, Timeline.EMPTY, timeline);
    testRunner.assertManifestsEqual(firstSourceManifest, null, thirdSourceManifest);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PREPARED,
        Player.TIMELINE_CHANGE_REASON_RESET,
        Player.TIMELINE_CHANGE_REASON_PREPARED);
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
            .waitForTimelineChanged(timeline)
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
            .build()
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
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void testShuffleModeEnabledChanges() throws Exception {
    Timeline fakeTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource[] fakeMediaSources = {
      new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT)
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
            .setMediaSource(mediaSource)
            .setRenderers(renderer)
            .setActionSchedule(actionSchedule)
            .build()
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
    final FakeMediaSource fakeMediaSource =
        new FakeMediaSource(fakeTimeline, /* manifest= */ null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testAdGroupWithLoadErrorIsSkipped")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    fakeMediaSource.setNewSourceInfo(adErrorTimeline, null);
                  }
                })
            .waitForTimelineChanged(adErrorTimeline)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSource(fakeMediaSource)
            .setActionSchedule(actionSchedule)
            .build()
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
        .build()
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
            .play()
            .waitForPositionDiscontinuity()
            // Seek twice in concession, expecting the first seek to be replaced (and thus except
            // only on seek processed callback).
            .seek(5)
            .seek(60)
            .build();
    final List<Integer> playbackStatesWhenSeekProcessed = new ArrayList<>();
    EventListener eventListener =
        new DefaultEventListener() {
          private int currentPlaybackState = Player.STATE_IDLE;

          @Override
          public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
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
            .build()
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
  public void testSeekProcessedCalledWithIllegalSeekPosition() throws Exception {
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekProcessedCalledWithIllegalSeekPosition")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Cause an illegal seek exception by seeking to an invalid position while the media
            // source is still being prepared and the player doesn't immediately know it will fail.
            // Because the media source prepares immediately, the exception will be thrown when the
            // player processed the seek.
            .seek(/* windowIndex= */ 100, /* positionMs= */ 0)
            .waitForPlaybackState(Player.STATE_IDLE)
            .build();
    final boolean[] onSeekProcessedCalled = new boolean[1];
    EventListener listener =
        new DefaultEventListener() {
          @Override
          public void onSeekProcessed() {
            onSeekProcessedCalled[0] = true;
          }
        };
    ExoPlayerTestRunner testRunner =
        new Builder().setActionSchedule(actionSchedule).setEventListener(listener).build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    assertThat(onSeekProcessedCalled[0]).isTrue();
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
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
  }

  @Test
  public void testSeekDiscontinuityWithAdjustment() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT) {
          @Override
          protected FakeMediaPeriod createFakeMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              EventDispatcher eventDispatcher) {
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
            .setMediaSource(mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_SEEK, Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT);
  }

  @Test
  public void testInternalDiscontinuityAtNewPosition() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT) {
          @Override
          protected FakeMediaPeriod createFakeMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              EventDispatcher eventDispatcher) {
            FakeMediaPeriod mediaPeriod = new FakeMediaPeriod(trackGroupArray, eventDispatcher);
            mediaPeriod.setDiscontinuityPositionUs(10);
            return mediaPeriod;
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSource(mediaSource)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_INTERNAL);
  }

  @Test
  public void testInternalDiscontinuityAtInitialPosition() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT) {
          @Override
          protected FakeMediaPeriod createFakeMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              EventDispatcher eventDispatcher) {
            FakeMediaPeriod mediaPeriod = new FakeMediaPeriod(trackGroupArray, eventDispatcher);
            mediaPeriod.setDiscontinuityPositionUs(0);
            return mediaPeriod;
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSource(mediaSource)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    // If the position is unchanged we do not expect the discontinuity to be reported externally.
    testRunner.assertNoPositionDiscontinuities();
  }

  @Test
  public void testAllActivatedTrackSelectionAreReleasedForSinglePeriod() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    FakeTrackSelector trackSelector = new FakeTrackSelector();

    new Builder()
        .setMediaSource(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getSelectedTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made once (1 period).
    // Track selections are not reused, so there are 2 track selections made.
    assertThat(createdTrackSelections).hasSize(2);
    // There should be 2 track selections enabled in total.
    assertThat(numSelectionsEnabled).isEqualTo(2);
  }

  @Test
  public void testAllActivatedTrackSelectionAreReleasedForMultiPeriods() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    FakeTrackSelector trackSelector = new FakeTrackSelector();

    new Builder()
        .setMediaSource(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getSelectedTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice (2 periods).
    // Track selections are not reused, so there are 4 track selections made.
    assertThat(createdTrackSelections).hasSize(4);
    // There should be 4 track selections enabled in total.
    assertThat(numSelectionsEnabled).isEqualTo(4);
  }

  @Test
  public void testAllActivatedTrackSelectionAreReleasedWhenTrackSelectionsAreRemade()
      throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    final FakeTrackSelector trackSelector = new FakeTrackSelector();
    ActionSchedule disableTrackAction =
        new ActionSchedule.Builder("testChangeTrackSelection")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    trackSelector.setRendererDisabled(0, true);
                  }
                })
            .play()
            .build();

    new Builder()
        .setMediaSource(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .setActionSchedule(disableTrackAction)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getSelectedTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice.
    // Track selections are not reused, so there are 4 track selections made.
    assertThat(createdTrackSelections).hasSize(4);
    // Initially there are 2 track selections enabled.
    // The second time one renderer is disabled, so only 1 track selection should be enabled.
    assertThat(numSelectionsEnabled).isEqualTo(3);
  }

  @Test
  public void testAllActivatedTrackSelectionAreReleasedWhenTrackSelectionsAreUsed()
      throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    final FakeTrackSelector trackSelector = new FakeTrackSelector(/* reuse track selection */ true);
    ActionSchedule disableTrackAction =
        new ActionSchedule.Builder("testReuseTrackSelection")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    trackSelector.setRendererDisabled(0, true);
                  }
                })
            .play()
            .build();

    new Builder()
        .setMediaSource(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .setActionSchedule(disableTrackAction)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getSelectedTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice.
    // TrackSelections are reused, so there are only 2 track selections made for 2 renderers.
    assertThat(createdTrackSelections).hasSize(2);
    // Initially there are 2 track selections enabled.
    // The second time one renderer is disabled, so only 1 track selection should be enabled.
    assertThat(numSelectionsEnabled).isEqualTo(3);
  }

  @Test
  public void testDynamicTimelineChangeReason() throws Exception {
    Timeline timeline1 = new FakeTimeline(new TimelineWindowDefinition(false, false, 100000));
    final Timeline timeline2 = new FakeTimeline(new TimelineWindowDefinition(false, false, 20000));
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline1, null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testDynamicTimelineChangeReason")
            .pause()
            .waitForTimelineChanged(timeline1)
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    mediaSource.setNewSourceInfo(timeline2, null);
                  }
                })
            .waitForTimelineChanged(timeline2)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSource(mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline1, timeline2);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PREPARED, Player.TIMELINE_CHANGE_REASON_DYNAMIC);
  }

  @Test
  public void testRepreparationWithPositionResetAndShufflingUsesFirstPeriod() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 100000));
    ConcatenatingMediaSource firstMediaSource =
        new ConcatenatingMediaSource(
            /* isAtomic= */ false,
            new FakeShuffleOrder(/* length= */ 2),
            new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
            new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT));
    ConcatenatingMediaSource secondMediaSource =
        new ConcatenatingMediaSource(
            /* isAtomic= */ false,
            new FakeShuffleOrder(/* length= */ 2),
            new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
            new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT));
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRepreparationWithShuffle")
            // Wait for first preparation and enable shuffling. Plays period 0.
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .setShuffleModeEnabled(true)
            // Reprepare with second media source (keeping state, but with position reset).
            // Plays period 1 and 0 because of the reversed fake shuffle order.
            .prepareSource(secondMediaSource, /* resetPosition= */ true, /* resetState= */ false)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSource(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
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
        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1), null, Builder.VIDEO_FORMAT) {
          @Override
          protected FakeMediaPeriod createFakeMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              EventDispatcher eventDispatcher) {
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
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      createPeriodCalledCountDownLatch.await();
                    } catch (InterruptedException e) {
                      throw new IllegalStateException(e);
                    }
                  }
                })
            // Set playback parameters (while the fake media period is not yet prepared).
            .setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f, /* pitch= */ 2f))
            // Complete preparation of the fake media period.
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    fakeMediaPeriodHolder[0].setPreparationComplete();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder()
        .setMediaSource(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
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
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
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
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
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
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline, Timeline.EMPTY);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PREPARED, Player.TIMELINE_CHANGE_REASON_RESET);
    testRunner.assertNoPositionDiscontinuities();
    assertThat(positionHolder[0]).isEqualTo(0);
  }

  @Test
  public void testStopWithoutResetReleasesMediaSource() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, /* manifest= */ null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testStopReleasesMediaSource")
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ false)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS);
    mediaSource.assertReleased();
    testRunner.blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void testStopWithResetReleasesMediaSource() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, /* manifest= */ null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testStopReleasesMediaSource")
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ true)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS);
    mediaSource.assertReleased();
    testRunner.blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void testRepreparationDoesNotResetAfterStopWithReset() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondSource = new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testRepreparationAfterStop")
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ true)
            .waitForPlaybackState(Player.STATE_IDLE)
            .prepareSource(secondSource)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .setExpectedPlayerEndedCount(2)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline, Timeline.EMPTY, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PREPARED,
        Player.TIMELINE_CHANGE_REASON_RESET,
        Player.TIMELINE_CHANGE_REASON_PREPARED);
    testRunner.assertNoPositionDiscontinuities();
  }

  @Test
  public void testSeekBeforeRepreparationPossibleAfterStopWithReset() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource secondSource = new FakeMediaSource(secondTimeline, null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSeekAfterStopWithReset")
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ true)
            .waitForPlaybackState(Player.STATE_IDLE)
            // If we were still using the first timeline, this would throw.
            .seek(/* windowIndex= */ 1, /* positionMs= */ 0)
            .prepareSource(secondSource, /* resetPosition= */ false, /* resetState= */ true)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .setExpectedPlayerEndedCount(2)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline, Timeline.EMPTY, secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PREPARED,
        Player.TIMELINE_CHANGE_REASON_RESET,
        Player.TIMELINE_CHANGE_REASON_PREPARED);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
    testRunner.assertPlayedPeriodIndices(0, 1);
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
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(Timeline.EMPTY);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
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
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
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
            .prepareSource(
                new FakeMediaSource(timeline, /* manifest= */ null),
                /* resetPosition= */ true,
                /* resetState= */ false)
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    testRunner.assertTimelinesEqual(timeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PREPARED, Player.TIMELINE_CHANGE_REASON_PREPARED);
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
            .prepareSource(
                new FakeMediaSource(timeline, /* manifest= */ null),
                /* resetPosition= */ false,
                /* resetState= */ false)
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
            .build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    testRunner.assertTimelinesEqual(timeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PREPARED, Player.TIMELINE_CHANGE_REASON_PREPARED);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
    assertThat(positionHolder[0]).isEqualTo(50);
    assertThat(positionHolder[1]).isEqualTo(50);
  }

  @Test
  public void testPlaybackErrorDuringSourceInfoRefreshStillUpdatesTimeline() throws Exception {
    final Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final FakeMediaSource mediaSource =
        new FakeMediaSource(/* timeline= */ null, /* manifest= */ null);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testPlaybackErrorDuringSourceInfoRefreshStillUpdatesTimeline")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Cause an internal exception by seeking to an invalid position while the media source
            // is still being prepared. The error will be thrown while the player handles the new
            // source info.
            .seek(/* windowIndex= */ 100, /* positionMs= */ 0)
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    mediaSource.setNewSourceInfo(timeline, /* newManifest= */ null);
                  }
                })
            .waitForPlaybackState(Player.STATE_IDLE)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSource(mediaSource)
            .setActionSchedule(actionSchedule)
            .build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
  }

  @Test
  public void testPlaybackErrorAndReprepareDoesNotResetPosition() throws Exception {
    final Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    final long[] positionHolder = new long[3];
    final int[] windowIndexHolder = new int[3];
    final FakeMediaSource secondMediaSource =
        new FakeMediaSource(/* timeline= */ null, /* manifest= */ null);
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
            .prepareSource(secondMediaSource, /* resetPosition= */ false, /* resetState= */ false)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    // Position while repreparing.
                    positionHolder[1] = player.getCurrentPosition();
                    windowIndexHolder[1] = player.getCurrentWindowIndex();
                    secondMediaSource.setNewSourceInfo(timeline, /* newManifest= */ null);
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
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build();
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
  public void testPlaybackErrorTwiceStillKeepsTimeline() throws Exception {
    final Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final FakeMediaSource mediaSource2 =
        new FakeMediaSource(/* timeline= */ null, /* manifest= */ null);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testPlaybackErrorDoesNotResetPosition")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForPlaybackState(Player.STATE_IDLE)
            .prepareSource(mediaSource2, /* resetPosition= */ false, /* resetState= */ false)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForPlaybackState(Player.STATE_IDLE)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    testRunner.assertTimelinesEqual(timeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PREPARED, Player.TIMELINE_CHANGE_REASON_PREPARED);
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
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs >= 50).isTrue();
  }

  @Test
  public void testSendMessagesAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForTimelineChanged(timeline)
            .sendMessage(target, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs >= 50).isTrue();
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
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target50.positionMs >= 50).isTrue();
    assertThat(target80.positionMs >= 80).isTrue();
    assertThat(target80.positionMs).isAtLeast(target50.positionMs);
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
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target1.positionMs >= 50).isTrue();
    assertThat(target2.positionMs >= 50).isTrue();
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
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs >= 50).isTrue();
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
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(targetStartFirstPeriod, /* windowIndex= */ 0, /* positionMs= */ 0)
            .sendMessage(targetEndMiddlePeriod, /* windowIndex= */ 0, /* positionMs= */ duration1Ms)
            .sendMessage(targetStartMiddlePeriod, /* windowIndex= */ 1, /* positionMs= */ 0)
            .sendMessage(targetEndLastPeriod, /* windowIndex= */ 1, /* positionMs= */ duration2Ms)
            .play()
            // Add additional prepare at end and wait until it's processed to ensure that
            // messages sent at end of playback are received before test ends.
            .waitForPlaybackState(Player.STATE_ENDED)
            .prepareSource(
                new FakeMediaSource(timeline, null),
                /* resetPosition= */ false,
                /* resetState= */ true)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
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
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs >= 50).isTrue();
  }

  @Test
  public void testSendMessagesSeekOnDeliveryTimeAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .waitForTimelineChanged(timeline)
            .seek(/* positionMs= */ 50)
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs >= 50).isTrue();
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
        .build()
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
            .waitForTimelineChanged(timeline)
            .seek(/* positionMs= */ 51)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
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
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.messageCount).isEqualTo(1);
    assertThat(target.positionMs >= 50).isTrue();
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
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.messageCount).isEqualTo(2);
    assertThat(target.positionMs >= 50).isTrue();
  }

  @Test
  public void testSendMessagesMoveCurrentWindowIndex() throws Exception {
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0));
    final Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0));
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForTimelineChanged(timeline)
            .sendMessage(target, /* positionMs= */ 50)
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    mediaSource.setNewSourceInfo(secondTimeline, null);
                  }
                })
            .waitForTimelineChanged(secondTimeline)
            .play()
            .build();
    new Builder()
        .setMediaSource(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs >= 50).isTrue();
    assertThat(target.windowIndex).isEqualTo(1);
  }

  @Test
  public void testSendMessagesMultiWindowDuringPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* windowIndex = */ 2, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.windowIndex).isEqualTo(2);
    assertThat(target.positionMs >= 50).isTrue();
  }

  @Test
  public void testSendMessagesMultiWindowAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForTimelineChanged(timeline)
            .sendMessage(target, /* windowIndex = */ 2, /* positionMs= */ 50)
            .play()
            .build();
    new Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.windowIndex).isEqualTo(2);
    assertThat(target.positionMs >= 50).isTrue();
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
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSendMessages")
            .pause()
            .waitForTimelineChanged(timeline)
            .sendMessage(target, /* windowIndex = */ 1, /* positionMs= */ 50)
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    mediaSource.setNewSourceInfo(secondTimeline, null);
                  }
                })
            .waitForTimelineChanged(secondTimeline)
            .seek(/* windowIndex= */ 0, /* positionMs= */ 0)
            .play()
            .build();
    new Builder()
        .setMediaSource(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs >= 50).isTrue();
    assertThat(target.windowIndex).isEqualTo(0);
  }

  @Test
  public void testSendMessagesNonLinearPeriodOrder() throws Exception {
    Timeline fakeTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource[] fakeMediaSources = {
      new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT)
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
        .setMediaSource(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target1.windowIndex).isEqualTo(0);
    assertThat(target2.windowIndex).isEqualTo(1);
    assertThat(target3.windowIndex).isEqualTo(2);
  }

  @Test
  public void testSetAndSwitchSurface() throws Exception {
    final List<Integer> rendererMessages = new ArrayList<>();
    Renderer videoRenderer =
        new FakeRenderer(Builder.VIDEO_FORMAT) {
          @Override
          public void handleMessage(int what, Object object) throws ExoPlaybackException {
            super.handleMessage(what, object);
            rendererMessages.add(what);
          }
        };
    ActionSchedule actionSchedule =
        addSurfaceSwitch(new ActionSchedule.Builder("testSetAndSwitchSurface")).build();
    new ExoPlayerTestRunner.Builder()
        .setRenderers(videoRenderer)
        .setActionSchedule(actionSchedule)
        .build()
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
        .build()
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
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline1, /* manifest= */ null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testTimelineUpdateDropsPeriods")
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            // Ensure next period is pre-buffered by playing until end of first period.
            .playUntilPosition(
                /* windowIndex= */ 0,
                /* positionMs= */ C.usToMs(TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US))
            .executeRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    mediaSource.setNewSourceInfo(timeline2, /* newManifest= */ null);
                  }
                })
            .waitForTimelineChanged(timeline2)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setMediaSource(mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlayedPeriodIndices(0, 1);
    // Assert that the second period was re-created from the new timeline.
    assertThat(mediaSource.getCreatedMediaPeriods())
        .containsExactly(
            new MediaPeriodId(/* periodIndex= */ 0, /* windowSequenceNumber= */ 0),
            new MediaPeriodId(/* periodIndex= */ 1, /* windowSequenceNumber= */ 1),
            new MediaPeriodId(/* periodIndex= */ 1, /* windowSequenceNumber= */ 2))
        .inOrder();
  }

  // Internal methods.

  private static ActionSchedule.Builder addSurfaceSwitch(ActionSchedule.Builder builder) {
    final Surface surface1 = new Surface(null);
    final Surface surface2 = new Surface(null);
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
    public void handleMessage(SimpleExoPlayer player, int messageType, Object message) {
      windowIndex = player.getCurrentWindowIndex();
      positionMs = player.getCurrentPosition();
      messageCount++;
    }
  }
}
