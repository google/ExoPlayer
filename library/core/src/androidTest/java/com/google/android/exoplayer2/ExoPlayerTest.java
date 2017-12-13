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

import com.google.android.exoplayer2.Player.DefaultEventListener;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.ActionSchedule;
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
import com.google.android.exoplayer2.upstream.Allocator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import junit.framework.TestCase;

/**
 * Unit test for {@link ExoPlayer}.
 */
public final class ExoPlayerTest extends TestCase {

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
  public void testPlayEmptyTimeline() throws Exception {
    Timeline timeline = Timeline.EMPTY;
    FakeRenderer renderer = new FakeRenderer();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder()
            .setTimeline(timeline)
            .setRenderers(renderer)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertNoPositionDiscontinuities();
    testRunner.assertTimelinesEqual(timeline);
    assertEquals(0, renderer.formatReadCount);
    assertEquals(0, renderer.bufferReadCount);
    assertFalse(renderer.isEnded);
  }

  /**
   * Tests playback of a source that exposes a single period.
   */
  public void testPlaySinglePeriodTimeline() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    Object manifest = new Object();
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline).setManifest(manifest).setRenderers(renderer)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertNoPositionDiscontinuities();
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertManifestsEqual(manifest);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
    testRunner.assertTrackGroupsEqual(new TrackGroupArray(new TrackGroup(Builder.VIDEO_FORMAT)));
    assertEquals(1, renderer.formatReadCount);
    assertEquals(1, renderer.bufferReadCount);
    assertTrue(renderer.isEnded);
  }

  /**
   * Tests playback of a source that exposes three periods.
   */
  public void testPlayMultiPeriodTimeline() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline).setRenderers(renderer)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
    assertEquals(3, renderer.formatReadCount);
    assertEquals(1, renderer.bufferReadCount);
    assertTrue(renderer.isEnded);
  }

  /**
   * Tests that the player does not unnecessarily reset renderers when playing a multi-period
   * source.
   */
  public void testReadAheadToEndDoesNotResetRenderer() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    final FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeMediaClockRenderer audioRenderer = new FakeMediaClockRenderer(Builder.AUDIO_FORMAT) {

      @Override
      public long getPositionUs() {
        // Simulate the playback position lagging behind the reading position: the renderer media
        // clock position will be the start of the timeline until the stream is set to be final, at
        // which point it jumps to the end of the timeline allowing the playing period to advance.
        // TODO: Avoid hard-coding ExoPlayerImplInternal.RENDERER_TIMESTAMP_OFFSET_US.
        return isCurrentStreamFinal() ? 60000030 : 60000000;
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
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline).setRenderers(videoRenderer, audioRenderer)
        .setSupportedFormats(Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    testRunner.assertTimelinesEqual(timeline);
    assertEquals(1, audioRenderer.positionResetCount);
    assertTrue(videoRenderer.isEnded);
    assertTrue(audioRenderer.isEnded);
  }

  public void testRepreparationGivesFreshSourceInfo() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    Object firstSourceManifest = new Object();
    MediaSource firstSource = new FakeMediaSource(timeline, firstSourceManifest,
        Builder.VIDEO_FORMAT);
    final CountDownLatch queuedSourceInfoCountDownLatch = new CountDownLatch(1);
    final CountDownLatch completePreparationCountDownLatch = new CountDownLatch(1);
    MediaSource secondSource = new FakeMediaSource(timeline, new Object(), Builder.VIDEO_FORMAT) {
      @Override
      public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
        super.prepareSource(player, isTopLevelSource, listener);
        // We've queued a source info refresh on the playback thread's event queue. Allow the test
        // thread to prepare the player with the third source, and block this thread (the playback
        // thread) until the test thread's call to prepare() has returned.
        queuedSourceInfoCountDownLatch.countDown();
        try {
          completePreparationCountDownLatch.await();
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
      }
    };
    Object thirdSourceManifest = new Object();
    MediaSource thirdSource = new FakeMediaSource(timeline, thirdSourceManifest,
        Builder.VIDEO_FORMAT);

    // Prepare the player with a source with the first manifest and a non-empty timeline. Prepare
    // the player again with a source and a new manifest, which will never be exposed. Allow the
    // test thread to prepare the player with a third source, and block the playback thread until
    // the test thread's call to prepare() has returned.
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testRepreparation")
        .waitForTimelineChanged(timeline)
        .prepareSource(secondSource)
        .executeRunnable(new Runnable() {
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
        .executeRunnable(new Runnable() {
          @Override
          public void run() {
            completePreparationCountDownLatch.countDown();
          }
        })
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setMediaSource(firstSource).setRenderers(renderer).setActionSchedule(actionSchedule)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertNoPositionDiscontinuities();
    // The first source's preparation completed with a non-empty timeline. When the player was
    // re-prepared with the second source, it immediately exposed an empty timeline, but the source
    // info refresh from the second source was suppressed as we re-prepared with the third source.
    testRunner.assertTimelinesEqual(timeline, Timeline.EMPTY, timeline);
    testRunner.assertManifestsEqual(firstSourceManifest, null, thirdSourceManifest);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED,
        Player.TIMELINE_CHANGE_REASON_RESET, Player.TIMELINE_CHANGE_REASON_PREPARED);
    testRunner.assertTrackGroupsEqual(new TrackGroupArray(new TrackGroup(Builder.VIDEO_FORMAT)));
    assertEquals(1, renderer.formatReadCount);
    assertEquals(1, renderer.bufferReadCount);
    assertTrue(renderer.isEnded);
  }

  public void testRepeatModeChanges() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testRepeatMode") // 0 -> 1
        .waitForPositionDiscontinuity().setRepeatMode(Player.REPEAT_MODE_ONE) // 1 -> 1
        .waitForPositionDiscontinuity().setRepeatMode(Player.REPEAT_MODE_OFF) // 1 -> 2
        .waitForPositionDiscontinuity().setRepeatMode(Player.REPEAT_MODE_ONE) // 2 -> 2
        .waitForPositionDiscontinuity().setRepeatMode(Player.REPEAT_MODE_ALL) // 2 -> 0
        .waitForPositionDiscontinuity().setRepeatMode(Player.REPEAT_MODE_ONE) // 0 -> 0
        .waitForPositionDiscontinuity()                                       // 0 -> 0
        .waitForPositionDiscontinuity().setRepeatMode(Player.REPEAT_MODE_OFF) // 0 -> end
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline).setRenderers(renderer).setActionSchedule(actionSchedule)
        .build().start().blockUntilEnded(TIMEOUT_MS);
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
    assertTrue(renderer.isEnded);
  }

  public void testShuffleModeEnabledChanges() throws Exception {
    Timeline fakeTimeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource[] fakeMediaSources = {
        new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
        new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
        new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT)
    };
    ConcatenatingMediaSource mediaSource = new ConcatenatingMediaSource(false,
        new FakeShuffleOrder(3), fakeMediaSources);
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testShuffleModeEnabled")
        .setRepeatMode(Player.REPEAT_MODE_ALL).waitForPositionDiscontinuity() // 0 -> 1
        .setShuffleModeEnabled(true).waitForPositionDiscontinuity()           // 1 -> 0
        .waitForPositionDiscontinuity().waitForPositionDiscontinuity()        // 0 -> 2 -> 1
        .setShuffleModeEnabled(false).setRepeatMode(Player.REPEAT_MODE_OFF)   // 1 -> 2 -> end
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setMediaSource(mediaSource).setRenderers(renderer).setActionSchedule(actionSchedule)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlayedPeriodIndices(0, 1, 0, 2, 1, 2);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION,
        Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    assertTrue(renderer.isEnded);
  }

  public void testPeriodHoldersReleasedAfterSeekWithRepeatModeAll() throws Exception {
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testPeriodHoldersReleased")
        .setRepeatMode(Player.REPEAT_MODE_ALL)
        .waitForPositionDiscontinuity()
        .seek(0) // Seek with repeat mode set to REPEAT_MODE_ALL.
        .waitForPositionDiscontinuity()
        .setRepeatMode(Player.REPEAT_MODE_OFF) // Turn off repeat so that playback can finish.
        .build();
    new ExoPlayerTestRunner.Builder()
        .setRenderers(renderer).setActionSchedule(actionSchedule)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    assertTrue(renderer.isEnded);
  }

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
    Player.EventListener eventListener = new Player.DefaultEventListener() {
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
    new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline).setEventListener(eventListener).setActionSchedule(actionSchedule)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    assertEquals(4, playbackStatesWhenSeekProcessed.size());
    assertEquals(Player.STATE_BUFFERING, (int) playbackStatesWhenSeekProcessed.get(0));
    assertEquals(Player.STATE_BUFFERING, (int) playbackStatesWhenSeekProcessed.get(1));
    assertEquals(Player.STATE_READY, (int) playbackStatesWhenSeekProcessed.get(2));
    assertEquals(Player.STATE_BUFFERING, (int) playbackStatesWhenSeekProcessed.get(3));
  }

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
        new ExoPlayerTestRunner.Builder()
            .setActionSchedule(actionSchedule)
            .setEventListener(listener)
            .build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    assertTrue(onSeekProcessedCalled[0]);
  }

  public void testSeekDiscontinuity() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testSeekDiscontinuity")
        .seek(10).build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder().setTimeline(timeline)
        .setActionSchedule(actionSchedule).build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
  }

  public void testSeekDiscontinuityWithAdjustment() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource = new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT) {
      @Override
      protected FakeMediaPeriod createFakeMediaPeriod(MediaPeriodId id,
          TrackGroupArray trackGroupArray, Allocator allocator) {
        FakeMediaPeriod mediaPeriod = new FakeMediaPeriod(trackGroupArray);
        mediaPeriod.setSeekToUsOffset(10);
        return mediaPeriod;
      }
    };
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testSeekDiscontinuityAdjust")
        .waitForPlaybackState(Player.STATE_READY).seek(10).build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder().setMediaSource(mediaSource)
        .setActionSchedule(actionSchedule).build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK,
        Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT);
  }

  public void testInternalDiscontinuityAtNewPosition() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource = new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT) {
      @Override
      protected FakeMediaPeriod createFakeMediaPeriod(MediaPeriodId id,
          TrackGroupArray trackGroupArray, Allocator allocator) {
        FakeMediaPeriod mediaPeriod = new FakeMediaPeriod(trackGroupArray);
        mediaPeriod.setDiscontinuityPositionUs(10);
        return mediaPeriod;
      }
    };
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder().setMediaSource(mediaSource)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_INTERNAL);
  }

  public void testInternalDiscontinuityAtInitialPosition() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource = new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT) {
      @Override
      protected FakeMediaPeriod createFakeMediaPeriod(MediaPeriodId id,
          TrackGroupArray trackGroupArray, Allocator allocator) {
        FakeMediaPeriod mediaPeriod = new FakeMediaPeriod(trackGroupArray);
        mediaPeriod.setDiscontinuityPositionUs(0);
        return mediaPeriod;
      }
    };
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder().setMediaSource(mediaSource)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    // If the position is unchanged we do not expect the discontinuity to be reported externally.
    testRunner.assertNoPositionDiscontinuities();
  }

  public void testAllActivatedTrackSelectionAreReleasedForSinglePeriod() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    FakeTrackSelector trackSelector = new FakeTrackSelector();

    new ExoPlayerTestRunner.Builder()
        .setMediaSource(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .build().start().blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getSelectedTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertFalse(trackSelection.isEnabled);
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made once (1 period).
    // Track selections are not reused, so there are 2 track selections made.
    assertEquals(2, createdTrackSelections.size());
    // There should be 2 track selections enabled in total.
    assertEquals(2, numSelectionsEnabled);
  }

  public void testAllActivatedTrackSelectionAreReleasedForMultiPeriods() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    FakeTrackSelector trackSelector = new FakeTrackSelector();

    new ExoPlayerTestRunner.Builder()
        .setMediaSource(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .build().start().blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getSelectedTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertFalse(trackSelection.isEnabled);
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice (2 periods).
    // Track selections are not reused, so there are 4 track selections made.
    assertEquals(4, createdTrackSelections.size());
    // There should be 4 track selections enabled in total.
    assertEquals(4, numSelectionsEnabled);
  }

  public void testAllActivatedTrackSelectionAreReleasedWhenTrackSelectionsAreRemade()
      throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    final FakeTrackSelector trackSelector = new FakeTrackSelector();
    ActionSchedule disableTrackAction = new ActionSchedule.Builder("testChangeTrackSelection")
        .waitForPlaybackState(Player.STATE_READY)
        .executeRunnable(new Runnable() {
          @Override
          public void run() {
            trackSelector.setRendererDisabled(0, true);
          }
        }).build();

    new ExoPlayerTestRunner.Builder()
        .setMediaSource(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .setActionSchedule(disableTrackAction)
        .build().start().blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getSelectedTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertFalse(trackSelection.isEnabled);
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice.
    // Track selections are not reused, so there are 4 track selections made.
    assertEquals(4, createdTrackSelections.size());
    // Initially there are 2 track selections enabled.
    // The second time one renderer is disabled, so only 1 track selection should be enabled.
    assertEquals(3, numSelectionsEnabled);
  }

  public void testAllActivatedTrackSelectionAreReleasedWhenTrackSelectionsAreUsed()
      throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource mediaSource =
        new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT, Builder.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    FakeRenderer audioRenderer = new FakeRenderer(Builder.AUDIO_FORMAT);
    final FakeTrackSelector trackSelector = new FakeTrackSelector(/* reuse track selection */ true);
    ActionSchedule disableTrackAction = new ActionSchedule.Builder("testReuseTrackSelection")
        .waitForPlaybackState(Player.STATE_READY)
        .executeRunnable(new Runnable() {
          @Override
          public void run() {
            trackSelector.setRendererDisabled(0, true);
          }
        }).build();

    new ExoPlayerTestRunner.Builder()
        .setMediaSource(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .setActionSchedule(disableTrackAction)
        .build().start().blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getSelectedTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertFalse(trackSelection.isEnabled);
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice.
    // TrackSelections are reused, so there are only 2 track selections made for 2 renderers.
    assertEquals(2, createdTrackSelections.size());
    // Initially there are 2 track selections enabled.
    // The second time one renderer is disabled, so only 1 track selection should be enabled.
    assertEquals(3, numSelectionsEnabled);
  }

  public void testDynamicTimelineChangeReason() throws Exception {
    Timeline timeline1 = new FakeTimeline(new TimelineWindowDefinition(false, false, 100000));
    final Timeline timeline2 = new FakeTimeline(new TimelineWindowDefinition(false, false, 20000));
    final FakeMediaSource mediaSource = new FakeMediaSource(timeline1, null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testDynamicTimelineChangeReason")
        .waitForTimelineChanged(timeline1)
        .executeRunnable(new Runnable() {
          @Override
          public void run() {
            mediaSource.setNewSourceInfo(timeline2, null);
          }
        })
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setMediaSource(mediaSource).setActionSchedule(actionSchedule)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline1, timeline2);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED,
        Player.TIMELINE_CHANGE_REASON_DYNAMIC);
  }

  public void testRepreparationWithPositionResetAndShufflingUsesFirstPeriod() throws Exception {
    Timeline fakeTimeline = new FakeTimeline(new TimelineWindowDefinition(/* isSeekable= */ true,
        /* isDynamic= */ false, /* durationUs= */ 100000));
    ConcatenatingMediaSource firstMediaSource = new ConcatenatingMediaSource(/* isAtomic= */ false,
        new FakeShuffleOrder(/* length= */ 2),
        new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
        new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT)
    );
    ConcatenatingMediaSource secondMediaSource = new ConcatenatingMediaSource(/* isAtomic= */ false,
        new FakeShuffleOrder(/* length= */ 2),
        new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT),
        new FakeMediaSource(fakeTimeline, null, Builder.VIDEO_FORMAT)
    );
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testRepreparationWithShuffle")
        // Wait for first preparation and enable shuffling. Plays period 0.
        .waitForPlaybackState(Player.STATE_READY).setShuffleModeEnabled(true)
        // Reprepare with second media source (keeping state, but with position reset).
        // Plays period 1 and 0 because of the reversed fake shuffle order.
        .prepareSource(secondMediaSource, /* resetPosition= */ true, /* resetState= */ false)
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setMediaSource(firstMediaSource).setActionSchedule(actionSchedule)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlayedPeriodIndices(0, 1, 0);
  }

  public void testSetPlaybackParametersBeforePreparationCompletesSucceeds() throws Exception {
    // Test that no exception is thrown when playback parameters are updated between creating a
    // period and preparation of the period completing.
    final CountDownLatch createPeriodCalledCountDownLatch = new CountDownLatch(1);
    final FakeMediaPeriod[] fakeMediaPeriodHolder = new FakeMediaPeriod[1];
    MediaSource mediaSource =
        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1), null, Builder.VIDEO_FORMAT) {
          @Override
          protected FakeMediaPeriod createFakeMediaPeriod(
              MediaPeriodId id, TrackGroupArray trackGroupArray, Allocator allocator) {
            // Defer completing preparation of the period until playback parameters have been set.
            fakeMediaPeriodHolder[0] =
                new FakeMediaPeriod(trackGroupArray, /* deferOnPrepared= */ true);
            createPeriodCalledCountDownLatch.countDown();
            return fakeMediaPeriodHolder[0];
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testSetPlaybackParametersBeforePreparationCompletesSucceeds")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Block until createPeriod has been called on the fake media source.
            .executeRunnable(new Runnable() {
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
            .setPlaybackParameters(new PlaybackParameters(2f, 2f))
            // Complete preparation of the fake media period.
            .executeRunnable(new Runnable() {
              @Override
              public void run() {
                fakeMediaPeriodHolder[0].setPreparationComplete();
              }
            })
            .build();
    new ExoPlayerTestRunner.Builder().setMediaSource(mediaSource).setActionSchedule(actionSchedule)
        .build().start().blockUntilEnded(TIMEOUT_MS);
  }

  public void testStopDoesNotResetPosition() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testStopDoesNotResetPosition")
        .waitForPlaybackState(Player.STATE_READY)
        .stop()
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
    testRunner.assertNoPositionDiscontinuities();
  }

  public void testStopWithoutResetDoesNotResetPosition() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testStopWithoutResetDoesNotReset")
        .waitForPlaybackState(Player.STATE_READY)
        .stop(/* reset= */ false)
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED);
    testRunner.assertNoPositionDiscontinuities();
  }

  public void testStopWithResetDoesResetPosition() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testStopWithResetDoesReset")
        .waitForPlaybackState(Player.STATE_READY)
        .stop(/* reset= */ true)
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline, Timeline.EMPTY);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED,
        Player.TIMELINE_CHANGE_REASON_RESET);
    testRunner.assertNoPositionDiscontinuities();
  }

  public void testStopWithoutResetReleasesMediaSource() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, /* manifest= */ null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testStopReleasesMediaSource")
        .waitForPlaybackState(Player.STATE_READY)
        .stop(/* reset= */ false)
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS);
    mediaSource.assertReleased();
    testRunner.blockUntilEnded(TIMEOUT_MS);
  }

  public void testStopWithResetReleasesMediaSource() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, /* manifest= */ null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testStopReleasesMediaSource")
        .waitForPlaybackState(Player.STATE_READY)
        .stop(/* reset= */ true)
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS);
    mediaSource.assertReleased();
    testRunner.blockUntilEnded(TIMEOUT_MS);
  }

  public void testRepreparationDoesNotResetAfterStopWithReset() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    MediaSource secondSource = new FakeMediaSource(timeline, null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testRepreparationAfterStop")
        .waitForPlaybackState(Player.STATE_READY)
        .stop(/* reset= */ true)
        .waitForPlaybackState(Player.STATE_IDLE)
        .prepareSource(secondSource)
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .setExpectedPlayerEndedCount(2)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline, Timeline.EMPTY, timeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED,
        Player.TIMELINE_CHANGE_REASON_RESET, Player.TIMELINE_CHANGE_REASON_PREPARED);
    testRunner.assertNoPositionDiscontinuities();
  }

  public void testSeekBeforeRepreparationPossibleAfterStopWithReset() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource secondSource = new FakeMediaSource(secondTimeline, null, Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testSeekAfterStopWithReset")
        .waitForPlaybackState(Player.STATE_READY)
        .stop(/* reset= */ true)
        .waitForPlaybackState(Player.STATE_IDLE)
        // If we were still using the first timeline, this would throw.
        .seek(/* windowIndex= */ 1, /* positionMs= */ 0)
        .prepareSource(secondSource, /* resetPosition= */ false, /* resetState= */ true)
        .build();
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .setExpectedPlayerEndedCount(2)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesEqual(timeline, Timeline.EMPTY, secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(Player.TIMELINE_CHANGE_REASON_PREPARED,
        Player.TIMELINE_CHANGE_REASON_RESET, Player.TIMELINE_CHANGE_REASON_PREPARED);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
    testRunner.assertPlayedPeriodIndices(0, 1);
  }

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

  public void testReprepareAfterPlaybackError() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 1);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder("testReprepareAfterPlaybackError")
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Cause an internal exception by seeking to an invalid position while the media source
            // is still being prepared and the player doesn't immediately know it will fail.
            .seek(/* windowIndex= */ 100, /* positionMs= */ 0)
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
                    mediaSource.setNewSourceInfo(timeline, /* manifest= */ null);
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
}
