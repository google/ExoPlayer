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

import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner.Builder;
import com.google.android.exoplayer2.testutil.FakeMediaClockRenderer;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeRenderer;
import com.google.android.exoplayer2.testutil.FakeShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
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
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline).setRenderers(renderer)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityCount(0);
    testRunner.assertTimelinesEqual();
    assertEquals(0, renderer.formatReadCount);
    assertEquals(0, renderer.bufferReadCount);
    assertFalse(renderer.isEnded);
  }

  /**
   * Tests playback of a source that exposes a single period.
   */
  public void testPlaySinglePeriodTimeline() throws Exception {
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(false, false, 0));
    Object manifest = new Object();
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline).setManifest(manifest).setRenderers(renderer)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityCount(0);
    testRunner.assertTimelinesEqual(timeline);
    testRunner.assertManifestsEqual(manifest);
    testRunner.assertTrackGroupsEqual(new TrackGroupArray(new TrackGroup(Builder.VIDEO_FORMAT)));
    assertEquals(1, renderer.formatReadCount);
    assertEquals(1, renderer.bufferReadCount);
    assertTrue(renderer.isEnded);
  }

  /**
   * Tests playback of a source that exposes three periods.
   */
  public void testPlayMultiPeriodTimeline() throws Exception {
    Timeline timeline = new FakeTimeline(
        new TimelineWindowDefinition(false, false, 0),
        new TimelineWindowDefinition(false, false, 0),
        new TimelineWindowDefinition(false, false, 0));
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ExoPlayerTestRunner testRunner = new ExoPlayerTestRunner.Builder()
        .setTimeline(timeline).setRenderers(renderer)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityCount(2);
    testRunner.assertTimelinesEqual(timeline);
    assertEquals(3, renderer.formatReadCount);
    assertEquals(1, renderer.bufferReadCount);
    assertTrue(renderer.isEnded);
  }

  /**
   * Tests that the player does not unnecessarily reset renderers when playing a multi-period
   * source.
   */
  public void testReadAheadToEndDoesNotResetRenderer() throws Exception {
    Timeline timeline = new FakeTimeline(
        new TimelineWindowDefinition(false, false, 10),
        new TimelineWindowDefinition(false, false, 10),
        new TimelineWindowDefinition(false, false, 10));
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
    testRunner.assertPositionDiscontinuityCount(2);
    testRunner.assertTimelinesEqual(timeline);
    assertEquals(1, audioRenderer.positionResetCount);
    assertTrue(videoRenderer.isEnded);
    assertTrue(audioRenderer.isEnded);
  }

  public void testRepreparationGivesFreshSourceInfo() throws Exception {
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(false, false, 0));
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
    testRunner.assertPositionDiscontinuityCount(0);
    // The first source's preparation completed with a non-empty timeline. When the player was
    // re-prepared with the second source, it immediately exposed an empty timeline, but the source
    // info refresh from the second source was suppressed as we re-prepared with the third source.
    testRunner.assertTimelinesEqual(timeline, Timeline.EMPTY, timeline);
    testRunner.assertManifestsEqual(firstSourceManifest, null, thirdSourceManifest);
    testRunner.assertTrackGroupsEqual(new TrackGroupArray(new TrackGroup(Builder.VIDEO_FORMAT)));
    assertEquals(1, renderer.formatReadCount);
    assertEquals(1, renderer.bufferReadCount);
    assertTrue(renderer.isEnded);
  }

  public void testRepeatModeChanges() throws Exception {
    Timeline timeline = new FakeTimeline(
        new TimelineWindowDefinition(true, false, 100000),
        new TimelineWindowDefinition(true, false, 100000),
        new TimelineWindowDefinition(true, false, 100000));
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
    testRunner.assertTimelinesEqual(timeline);
    assertTrue(renderer.isEnded);
  }

  public void testShuffleModeEnabledChanges() throws Exception {
    Timeline fakeTimeline = new FakeTimeline(new TimelineWindowDefinition(true, false, 100000));
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
    assertTrue(renderer.isEnded);
  }

  public void testPeriodHoldersReleasedAfterSeekWithRepeatModeAll() throws Exception {
    Timeline fakeTimeline = new FakeTimeline(new TimelineWindowDefinition(true, false, 100000));
    FakeRenderer renderer = new FakeRenderer(Builder.VIDEO_FORMAT);
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testPeriodHoldersReleased")
        .setRepeatMode(Player.REPEAT_MODE_ALL)
        .waitForPositionDiscontinuity()
        .seek(0) // Seek with repeat mode set to REPEAT_MODE_ALL.
        .waitForPositionDiscontinuity()
        .setRepeatMode(Player.REPEAT_MODE_OFF) // Turn off repeat so that playback can finish.
        .build();
    new ExoPlayerTestRunner.Builder()
        .setTimeline(fakeTimeline).setRenderers(renderer).setActionSchedule(actionSchedule)
        .build().start().blockUntilEnded(TIMEOUT_MS);
    assertTrue(renderer.isEnded);
  }

  public void testSeekProcessedCallback() throws Exception {
    Timeline timeline = new FakeTimeline(
        new TimelineWindowDefinition(true, false, 100000),
        new TimelineWindowDefinition(true, false, 100000));
    ActionSchedule actionSchedule = new ActionSchedule.Builder("testSeekProcessedCallback")
        // Initial seek before timeline preparation finished.
        .pause().seek(10).waitForPlaybackState(Player.STATE_READY)
        // Re-seek to same position, start playback and wait until playback reaches second window.
        .seek(10).play().waitForPositionDiscontinuity()
        // Seek twice in concession, expecting the first seek to be replaced.
        .seek(5).seek(60).build();
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
    assertEquals(3, playbackStatesWhenSeekProcessed.size());
    assertEquals(Player.STATE_BUFFERING, (int) playbackStatesWhenSeekProcessed.get(0));
    assertEquals(Player.STATE_READY, (int) playbackStatesWhenSeekProcessed.get(1));
    assertEquals(Player.STATE_BUFFERING, (int) playbackStatesWhenSeekProcessed.get(2));
  }

}
