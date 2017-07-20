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

import android.util.Pair;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.ExoPlayerWrapper;
import com.google.android.exoplayer2.testutil.FakeMediaClockRenderer;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeRenderer;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

  private static final Format TEST_VIDEO_FORMAT = Format.createVideoSampleFormat(null,
      MimeTypes.VIDEO_H264, null, Format.NO_VALUE, Format.NO_VALUE, 1280, 720, Format.NO_VALUE,
      null, null);
  private static final Format TEST_AUDIO_FORMAT =  Format.createAudioSampleFormat(null,
      MimeTypes.AUDIO_AAC, null, Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, null);

  /**
   * Tests playback of a source that exposes an empty timeline. Playback is expected to end without
   * error.
   */
  public void testPlayEmptyTimeline() throws Exception {
    ExoPlayerWrapper playerWrapper = new ExoPlayerWrapper();
    Timeline timeline = Timeline.EMPTY;
    MediaSource mediaSource = new FakeMediaSource(timeline, null);
    FakeRenderer renderer = new FakeRenderer();
    playerWrapper.setup(mediaSource, renderer);
    playerWrapper.blockUntilEnded(TIMEOUT_MS);
    assertEquals(0, playerWrapper.positionDiscontinuityCount);
    assertEquals(0, renderer.formatReadCount);
    assertEquals(0, renderer.bufferReadCount);
    assertFalse(renderer.isEnded);
    playerWrapper.assertSourceInfosEquals(Pair.create(timeline, null));
  }

  /**
   * Tests playback of a source that exposes a single period.
   */
  public void testPlaySinglePeriodTimeline() throws Exception {
    ExoPlayerWrapper playerWrapper = new ExoPlayerWrapper();
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(false, false, 0));
    Object manifest = new Object();
    MediaSource mediaSource = new FakeMediaSource(timeline, manifest, TEST_VIDEO_FORMAT);
    FakeRenderer renderer = new FakeRenderer(TEST_VIDEO_FORMAT);
    playerWrapper.setup(mediaSource, renderer);
    playerWrapper.blockUntilEnded(TIMEOUT_MS);
    assertEquals(0, playerWrapper.positionDiscontinuityCount);
    assertEquals(1, renderer.formatReadCount);
    assertEquals(1, renderer.bufferReadCount);
    assertTrue(renderer.isEnded);
    assertEquals(new TrackGroupArray(new TrackGroup(TEST_VIDEO_FORMAT)), playerWrapper.trackGroups);
    playerWrapper.assertSourceInfosEquals(Pair.create(timeline, manifest));
  }

  /**
   * Tests playback of a source that exposes three periods.
   */
  public void testPlayMultiPeriodTimeline() throws Exception {
    ExoPlayerWrapper playerWrapper = new ExoPlayerWrapper();
    Timeline timeline = new FakeTimeline(
        new TimelineWindowDefinition(false, false, 0),
        new TimelineWindowDefinition(false, false, 0),
        new TimelineWindowDefinition(false, false, 0));
    MediaSource mediaSource = new FakeMediaSource(timeline, null, TEST_VIDEO_FORMAT);
    FakeRenderer renderer = new FakeRenderer(TEST_VIDEO_FORMAT);
    playerWrapper.setup(mediaSource, renderer);
    playerWrapper.blockUntilEnded(TIMEOUT_MS);
    assertEquals(2, playerWrapper.positionDiscontinuityCount);
    assertEquals(3, renderer.formatReadCount);
    assertEquals(1, renderer.bufferReadCount);
    assertTrue(renderer.isEnded);
    playerWrapper.assertSourceInfosEquals(Pair.create(timeline, null));
  }

  /**
   * Tests that the player does not unnecessarily reset renderers when playing a multi-period
   * source.
   */
  public void testReadAheadToEndDoesNotResetRenderer() throws Exception {
    final ExoPlayerWrapper playerWrapper = new ExoPlayerWrapper();
    Timeline timeline = new FakeTimeline(
        new TimelineWindowDefinition(false, false, 10),
        new TimelineWindowDefinition(false, false, 10),
        new TimelineWindowDefinition(false, false, 10));
    MediaSource mediaSource = new FakeMediaSource(timeline, null, TEST_VIDEO_FORMAT,
        TEST_AUDIO_FORMAT);

    FakeRenderer videoRenderer = new FakeRenderer(TEST_VIDEO_FORMAT);
    FakeMediaClockRenderer audioRenderer = new FakeMediaClockRenderer(TEST_AUDIO_FORMAT) {

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
        // Allow playback to end once the final period is playing.
        return playerWrapper.positionDiscontinuityCount == 2;
      }

    };
    playerWrapper.setup(mediaSource, videoRenderer, audioRenderer);
    playerWrapper.blockUntilEnded(TIMEOUT_MS);
    assertEquals(2, playerWrapper.positionDiscontinuityCount);
    assertEquals(1, audioRenderer.positionResetCount);
    assertTrue(videoRenderer.isEnded);
    assertTrue(audioRenderer.isEnded);
    playerWrapper.assertSourceInfosEquals(Pair.create(timeline, null));
  }

  public void testRepreparationGivesFreshSourceInfo() throws Exception {
    ExoPlayerWrapper playerWrapper = new ExoPlayerWrapper();
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(false, false, 0));
    FakeRenderer renderer = new FakeRenderer(TEST_VIDEO_FORMAT);

    // Prepare the player with a source with the first manifest and a non-empty timeline
    Object firstSourceManifest = new Object();
    playerWrapper.setup(new FakeMediaSource(timeline, firstSourceManifest, TEST_VIDEO_FORMAT),
        renderer);
    playerWrapper.blockUntilSourceInfoRefreshed(TIMEOUT_MS);

    // Prepare the player again with a source and a new manifest, which will never be exposed.
    final CountDownLatch queuedSourceInfoCountDownLatch = new CountDownLatch(1);
    final CountDownLatch completePreparationCountDownLatch = new CountDownLatch(1);
    playerWrapper.prepare(new FakeMediaSource(timeline, new Object(), TEST_VIDEO_FORMAT) {
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
    });

    // Prepare the player again with a third source.
    queuedSourceInfoCountDownLatch.await();
    Object thirdSourceManifest = new Object();
    playerWrapper.prepare(new FakeMediaSource(timeline, thirdSourceManifest, TEST_VIDEO_FORMAT));
    completePreparationCountDownLatch.countDown();

    // Wait for playback to complete.
    playerWrapper.blockUntilEnded(TIMEOUT_MS);
    assertEquals(0, playerWrapper.positionDiscontinuityCount);
    assertEquals(1, renderer.formatReadCount);
    assertEquals(1, renderer.bufferReadCount);
    assertTrue(renderer.isEnded);
    assertEquals(new TrackGroupArray(new TrackGroup(TEST_VIDEO_FORMAT)), playerWrapper.trackGroups);

    // The first source's preparation completed with a non-empty timeline. When the player was
    // re-prepared with the second source, it immediately exposed an empty timeline, but the source
    // info refresh from the second source was suppressed as we re-prepared with the third source.
    playerWrapper.assertSourceInfosEquals(
        Pair.create(timeline, firstSourceManifest),
        Pair.create(Timeline.EMPTY, null),
        Pair.create(timeline, thirdSourceManifest));
  }

  public void testRepeatModeChanges() throws Exception {
    Timeline timeline = new FakeTimeline(
        new TimelineWindowDefinition(true, false, 100000),
        new TimelineWindowDefinition(true, false, 100000),
        new TimelineWindowDefinition(true, false, 100000));
    final int[] actionSchedule = { // 0 -> 1
        Player.REPEAT_MODE_ONE, // 1 -> 1
        Player.REPEAT_MODE_OFF, // 1 -> 2
        Player.REPEAT_MODE_ONE, // 2 -> 2
        Player.REPEAT_MODE_ALL, // 2 -> 0
        Player.REPEAT_MODE_ONE, // 0 -> 0
        -1, // 0 -> 0
        Player.REPEAT_MODE_OFF, // 0 -> 1
        -1, // 1 -> 2
        -1  // 2 -> ended
    };
    int[] expectedWindowIndices = {1, 1, 2, 2, 0, 0, 0, 1, 2};
    final LinkedList<Integer> windowIndices = new LinkedList<>();
    final CountDownLatch actionCounter = new CountDownLatch(actionSchedule.length);
    ExoPlayerWrapper playerWrapper = new ExoPlayerWrapper() {
      @Override
      @SuppressWarnings("ResourceType")
      public void onPositionDiscontinuity() {
        super.onPositionDiscontinuity();
        int actionIndex = actionSchedule.length - (int) actionCounter.getCount();
        if (actionSchedule[actionIndex] != -1) {
          player.setRepeatMode(actionSchedule[actionIndex]);
        }
        windowIndices.add(player.getCurrentWindowIndex());
        actionCounter.countDown();
      }
    };
    MediaSource mediaSource = new FakeMediaSource(timeline, null, TEST_VIDEO_FORMAT);
    FakeRenderer renderer = new FakeRenderer(TEST_VIDEO_FORMAT);
    playerWrapper.setup(mediaSource, renderer);
    boolean finished = actionCounter.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    playerWrapper.release();
    assertTrue("Test playback timed out waiting for action schedule to end.", finished);
    if (playerWrapper.exception != null) {
      throw playerWrapper.exception;
    }
    assertEquals(expectedWindowIndices.length, windowIndices.size());
    for (int i = 0; i < expectedWindowIndices.length; i++) {
      assertEquals(expectedWindowIndices[i], windowIndices.get(i).intValue());
    }
    assertEquals(9, playerWrapper.positionDiscontinuityCount);
    assertTrue(renderer.isEnded);
    playerWrapper.assertSourceInfosEquals(Pair.create(timeline, null));
  }

}
