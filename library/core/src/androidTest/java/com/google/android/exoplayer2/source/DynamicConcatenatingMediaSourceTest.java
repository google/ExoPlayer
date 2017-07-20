/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.Listener;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import java.io.IOException;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 * Unit tests for {@link DynamicConcatenatingMediaSource}
 */
public final class DynamicConcatenatingMediaSourceTest extends TestCase {

  private static final int TIMEOUT_MS = 10000;

  private Timeline timeline;
  private boolean timelineUpdated;

  public void testPlaylistChangesAfterPreparation() throws InterruptedException {
    timeline = null;
    FakeMediaSource[] childSources = createMediaSources(7);
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    prepareAndListenToTimelineUpdates(mediaSource);
    waitForTimelineUpdate();
    TimelineAsserts.assertEmpty(timeline);

    // Add first source.
    mediaSource.addMediaSource(childSources[0]);
    waitForTimelineUpdate();
    assertNotNull(timeline);
    TimelineAsserts.assertPeriodCounts(timeline, 1);
    TimelineAsserts.assertWindowIds(timeline, 111);

    // Add at front of queue.
    mediaSource.addMediaSource(0, childSources[1]);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 1);
    TimelineAsserts.assertWindowIds(timeline, 222, 111);

    // Add at back of queue.
    mediaSource.addMediaSource(childSources[2]);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 111, 333);

    // Add in the middle.
    mediaSource.addMediaSource(1, childSources[3]);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 333);

    // Add bulk.
    mediaSource.addMediaSources(3, Arrays.asList((MediaSource) childSources[4],
        (MediaSource) childSources[5], (MediaSource) childSources[6]));
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 5, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 555, 666, 777, 333);

    // Move sources.
    mediaSource.moveMediaSource(2, 3);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 5, 1, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 555, 111, 666, 777, 333);
    mediaSource.moveMediaSource(3, 2);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 5, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 555, 666, 777, 333);
    mediaSource.moveMediaSource(0, 6);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 4, 1, 5, 6, 7, 3, 2);
    TimelineAsserts.assertWindowIds(timeline, 444, 111, 555, 666, 777, 333, 222);
    mediaSource.moveMediaSource(6, 0);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 5, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 555, 666, 777, 333);

    // Remove in the middle.
    mediaSource.removeMediaSource(3);
    waitForTimelineUpdate();
    mediaSource.removeMediaSource(3);
    waitForTimelineUpdate();
    mediaSource.removeMediaSource(3);
    waitForTimelineUpdate();
    mediaSource.removeMediaSource(1);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 111, 333);
    for (int i = 3; i <= 6; i++) {
      childSources[i].assertReleased();
    }

    // Assert correct next and previous indices behavior after some insertions and removals.
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, 1, 2, 0);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, 2, 0, 1);

    // Remove at front of queue.
    mediaSource.removeMediaSource(0);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 111, 333);
    childSources[1].assertReleased();

    // Remove at back of queue.
    mediaSource.removeMediaSource(1);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 1);
    TimelineAsserts.assertWindowIds(timeline, 111);
    childSources[2].assertReleased();

    // Remove last source.
    mediaSource.removeMediaSource(0);
    waitForTimelineUpdate();
    TimelineAsserts.assertEmpty(timeline);
    childSources[3].assertReleased();
  }

  public void testPlaylistChangesBeforePreparation() throws InterruptedException {
    timeline = null;
    FakeMediaSource[] childSources = createMediaSources(4);
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    mediaSource.addMediaSource(childSources[0]);
    mediaSource.addMediaSource(childSources[1]);
    mediaSource.addMediaSource(0, childSources[2]);
    mediaSource.moveMediaSource(0, 2);
    mediaSource.removeMediaSource(0);
    mediaSource.moveMediaSource(1, 0);
    mediaSource.addMediaSource(1, childSources[3]);
    assertNull(timeline);

    prepareAndListenToTimelineUpdates(mediaSource);
    waitForTimelineUpdate();
    assertNotNull(timeline);
    TimelineAsserts.assertPeriodCounts(timeline, 3, 4, 2);
    TimelineAsserts.assertWindowIds(timeline, 333, 444, 222);

    mediaSource.releaseSource();
    for (int i = 1; i < 4; i++) {
      childSources[i].assertReleased();
    }
  }

  public void testPlaylistWithLazyMediaSource() throws InterruptedException {
    timeline = null;
    FakeMediaSource[] childSources = createMediaSources(2);
    LazyMediaSource[] lazySources = new LazyMediaSource[4];
    for (int i = 0; i < 4; i++) {
      lazySources[i] = new LazyMediaSource();
    }

    //Add lazy sources before preparation
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    mediaSource.addMediaSource(lazySources[0]);
    mediaSource.addMediaSource(0, childSources[0]);
    mediaSource.removeMediaSource(1);
    mediaSource.addMediaSource(1, lazySources[1]);
    assertNull(timeline);
    prepareAndListenToTimelineUpdates(mediaSource);
    waitForTimelineUpdate();
    assertNotNull(timeline);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1);
    TimelineAsserts.assertWindowIds(timeline, 111, null);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, true);

    lazySources[1].triggerTimelineUpdate(createFakeTimeline(8));
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 9);
    TimelineAsserts.assertWindowIds(timeline, 111, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, false);

    //Add lazy sources after preparation
    mediaSource.addMediaSource(1, lazySources[2]);
    waitForTimelineUpdate();
    mediaSource.addMediaSource(2, childSources[1]);
    waitForTimelineUpdate();
    mediaSource.addMediaSource(0, lazySources[3]);
    waitForTimelineUpdate();
    mediaSource.removeMediaSource(2);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 2, 9);
    TimelineAsserts.assertWindowIds(timeline, null, 111, 222, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, true, false, false, false);

    lazySources[3].triggerTimelineUpdate(createFakeTimeline(7));
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 8, 1, 2, 9);
    TimelineAsserts.assertWindowIds(timeline, 888, 111, 222, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, false, false, false);

    mediaSource.releaseSource();
    childSources[0].assertReleased();
    childSources[1].assertReleased();
  }

  public void testIllegalArguments() {
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    MediaSource validSource = new FakeMediaSource(createFakeTimeline(1), null);

    // Null sources.
    try {
      mediaSource.addMediaSource(null);
      fail("Null mediaSource not allowed.");
    } catch (NullPointerException e) {
      // Expected.
    }

    MediaSource[] mediaSources = { validSource, null };
    try {
      mediaSource.addMediaSources(Arrays.asList(mediaSources));
      fail("Null mediaSource not allowed.");
    } catch (NullPointerException e) {
      // Expected.
    }

    // Duplicate sources.
    mediaSource.addMediaSource(validSource);
    try {
      mediaSource.addMediaSource(validSource);
      fail("Duplicate mediaSource not allowed.");
    } catch (IllegalArgumentException e) {
      // Expected.
    }

    mediaSources = new MediaSource[] {
        new FakeMediaSource(createFakeTimeline(2), null), validSource };
    try {
      mediaSource.addMediaSources(Arrays.asList(mediaSources));
      fail("Duplicate mediaSource not allowed.");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  private void prepareAndListenToTimelineUpdates(MediaSource mediaSource) {
    mediaSource.prepareSource(new StubExoPlayer(), true, new Listener() {
      @Override
      public void onSourceInfoRefreshed(Timeline newTimeline, Object manifest) {
        timeline = newTimeline;
        synchronized (DynamicConcatenatingMediaSourceTest.this) {
          timelineUpdated = true;
          DynamicConcatenatingMediaSourceTest.this.notify();
        }
      }
    });
  }

  private synchronized void waitForTimelineUpdate() throws InterruptedException {
    long timeoutMs = System.currentTimeMillis() + TIMEOUT_MS;
    while (!timelineUpdated) {
      wait(TIMEOUT_MS);
      if (System.currentTimeMillis() >= timeoutMs) {
        fail("No timeline update occurred within timeout.");
      }
    }
    timelineUpdated = false;
  }

  private static FakeMediaSource[] createMediaSources(int count) {
    FakeMediaSource[] sources = new FakeMediaSource[count];
    for (int i = 0; i < count; i++) {
      sources[i] = new FakeMediaSource(createFakeTimeline(i), null);
    }
    return sources;
  }

  private static FakeTimeline createFakeTimeline(int index) {
    return new FakeTimeline(new TimelineWindowDefinition(index + 1, (index + 1) * 111));
  }

  private static class LazyMediaSource implements MediaSource {

    private Listener listener;

    public void triggerTimelineUpdate(Timeline timeline) {
      listener.onSourceInfoRefreshed(timeline, null);
    }

    @Override
    public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
      this.listener = listener;
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
      return null;
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
    }

    @Override
    public void releaseSource() {
    }

  }

  /**
   * Stub ExoPlayer which only accepts custom messages and runs them on a separate handler thread.
   */
  private static class StubExoPlayer implements ExoPlayer, Handler.Callback {

    private final Handler handler;

    public StubExoPlayer() {
      HandlerThread handlerThread = new HandlerThread("StubExoPlayerThread");
      handlerThread.start();
      handler = new Handler(handlerThread.getLooper(), this);
    }

    @Override
    public Looper getPlaybackLooper() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addListener(Player.EventListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeListener(Player.EventListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getPlaybackState() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void prepare(MediaSource mediaSource) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean getPlayWhenReady() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setRepeatMode(@RepeatMode int repeatMode) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getRepeatMode() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLoading() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekToDefaultPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekToDefaultPosition(int windowIndex) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekTo(long positionMs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekTo(int windowIndex, long positionMs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void release() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void sendMessages(ExoPlayerMessage... messages) {
      handler.obtainMessage(0, messages).sendToTarget();
    }

    @Override
    public void blockingSendMessages(ExoPlayerMessage... messages) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getRendererCount() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getRendererType(int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
      throw new UnsupportedOperationException();
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object getCurrentManifest() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Timeline getCurrentTimeline() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCurrentPeriodIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCurrentWindowIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getDuration() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getCurrentPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getBufferedPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getBufferedPercentage() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCurrentWindowDynamic() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCurrentWindowSeekable() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPlayingAd() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCurrentAdGroupIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getContentPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean handleMessage(Message msg) {
      ExoPlayerMessage[] messages = (ExoPlayerMessage[]) msg.obj;
      for (ExoPlayerMessage message : messages) {
        try {
          message.target.handleMessage(message.messageType, message.message);
        } catch (ExoPlaybackException e) {
          fail("Unexpected ExoPlaybackException.");
        }
      }
      return true;
    }
  }

}
