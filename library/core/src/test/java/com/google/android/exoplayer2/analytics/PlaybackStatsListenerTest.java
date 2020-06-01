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
package com.google.android.exoplayer2.analytics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link PlaybackStatsListener}. */
@RunWith(AndroidJUnit4.class)
public final class PlaybackStatsListenerTest {

  private static final AnalyticsListener.EventTime EMPTY_TIMELINE_EVENT_TIME =
      new AnalyticsListener.EventTime(
          /* realtimeMs= */ 500,
          Timeline.EMPTY,
          /* windowIndex= */ 0,
          /* mediaPeriodId= */ null,
          /* eventPlaybackPositionMs= */ 0,
          /* currentPlaybackPositionMs= */ 0,
          /* totalBufferedDurationMs= */ 0);
  private static final Timeline TEST_TIMELINE = new FakeTimeline(/* windowCount= */ 1);
  private static final AnalyticsListener.EventTime TEST_EVENT_TIME =
      new AnalyticsListener.EventTime(
          /* realtimeMs= */ 500,
          TEST_TIMELINE,
          /* windowIndex= */ 0,
          new MediaSource.MediaPeriodId(
              TEST_TIMELINE.getPeriod(
                  /* periodIndex= */ 0, new Timeline.Period(), /* setIds= */ true)
                  .uid,
              /* windowSequenceNumber= */ 42),
          /* eventPlaybackPositionMs= */ 123,
          /* currentPlaybackPositionMs= */ 123,
          /* totalBufferedDurationMs= */ 456);

  @Test
  public void stateChangeEvent_toNonIdle_createsInitialPlaybackStats() {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);

    playbackStatsListener.onPlayerStateChanged(
        EMPTY_TIMELINE_EVENT_TIME, /* playWhenReady= */ false, Player.STATE_BUFFERING);

    assertThat(playbackStatsListener.getPlaybackStats()).isNotNull();
  }

  @Test
  public void timelineChangeEvent_toNonEmpty_createsInitialPlaybackStats() {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);

    playbackStatsListener.onTimelineChanged(TEST_EVENT_TIME, Player.TIMELINE_CHANGE_REASON_DYNAMIC);

    assertThat(playbackStatsListener.getPlaybackStats()).isNotNull();
  }

  @Test
  public void playback_withKeepHistory_updatesStats() {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);

    playbackStatsListener.onPlayerStateChanged(
        TEST_EVENT_TIME, /* playWhenReady= */ true, Player.STATE_BUFFERING);
    playbackStatsListener.onPlayerStateChanged(
        TEST_EVENT_TIME, /* playWhenReady= */ true, Player.STATE_READY);
    playbackStatsListener.onPlayerStateChanged(
        TEST_EVENT_TIME, /* playWhenReady= */ true, Player.STATE_ENDED);

    @Nullable PlaybackStats playbackStats = playbackStatsListener.getPlaybackStats();
    assertThat(playbackStats).isNotNull();
    assertThat(playbackStats.endedCount).isEqualTo(1);
  }

  @Test
  public void playback_withoutKeepHistory_updatesStats() {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ false, /* callback= */ null);

    playbackStatsListener.onPlayerStateChanged(
        TEST_EVENT_TIME, /* playWhenReady= */ true, Player.STATE_BUFFERING);
    playbackStatsListener.onPlayerStateChanged(
        TEST_EVENT_TIME, /* playWhenReady= */ true, Player.STATE_READY);
    playbackStatsListener.onPlayerStateChanged(
        TEST_EVENT_TIME, /* playWhenReady= */ true, Player.STATE_ENDED);

    @Nullable PlaybackStats playbackStats = playbackStatsListener.getPlaybackStats();
    assertThat(playbackStats).isNotNull();
    assertThat(playbackStats.endedCount).isEqualTo(1);
  }

  @Test
  public void finishedSession_callsCallback() {
    PlaybackStatsListener.Callback callback = mock(PlaybackStatsListener.Callback.class);
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, callback);

    // Create session with an event and finish it by simulating removal from playlist.
    playbackStatsListener.onPlayerStateChanged(
        TEST_EVENT_TIME, /* playWhenReady= */ false, Player.STATE_BUFFERING);
    verify(callback, never()).onPlaybackStatsReady(any(), any());
    playbackStatsListener.onTimelineChanged(
        EMPTY_TIMELINE_EVENT_TIME, Player.TIMELINE_CHANGE_REASON_DYNAMIC);

    verify(callback).onPlaybackStatsReady(eq(TEST_EVENT_TIME), any());
  }

  @Test
  public void finishAllSessions_callsAllPendingCallbacks() {
    AnalyticsListener.EventTime eventTimeWindow0 =
        new AnalyticsListener.EventTime(
            /* realtimeMs= */ 0,
            Timeline.EMPTY,
            /* windowIndex= */ 0,
            /* mediaPeriodId= */ null,
            /* eventPlaybackPositionMs= */ 0,
            /* currentPlaybackPositionMs= */ 0,
            /* totalBufferedDurationMs= */ 0);
    AnalyticsListener.EventTime eventTimeWindow1 =
        new AnalyticsListener.EventTime(
            /* realtimeMs= */ 0,
            Timeline.EMPTY,
            /* windowIndex= */ 1,
            /* mediaPeriodId= */ null,
            /* eventPlaybackPositionMs= */ 0,
            /* currentPlaybackPositionMs= */ 0,
            /* totalBufferedDurationMs= */ 0);
    PlaybackStatsListener.Callback callback = mock(PlaybackStatsListener.Callback.class);
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, callback);
    playbackStatsListener.onPlayerStateChanged(
        eventTimeWindow0, /* playWhenReady= */ false, Player.STATE_BUFFERING);
    playbackStatsListener.onPlayerStateChanged(
        eventTimeWindow1, /* playWhenReady= */ false, Player.STATE_BUFFERING);

    playbackStatsListener.finishAllSessions();

    verify(callback, times(2)).onPlaybackStatsReady(any(), any());
    verify(callback).onPlaybackStatsReady(eq(eventTimeWindow0), any());
    verify(callback).onPlaybackStatsReady(eq(eventTimeWindow1), any());
  }

  @Test
  public void finishAllSessions_doesNotCallCallbackAgainWhenSessionWouldBeAutomaticallyFinished() {
    PlaybackStatsListener.Callback callback = mock(PlaybackStatsListener.Callback.class);
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, callback);
    playbackStatsListener.onPlayerStateChanged(
        TEST_EVENT_TIME, /* playWhenReady= */ false, Player.STATE_BUFFERING);
    SystemClock.setCurrentTimeMillis(TEST_EVENT_TIME.realtimeMs + 100);

    playbackStatsListener.finishAllSessions();
    // Simulate removing the playback item to ensure the session would finish if it hadn't already.
    playbackStatsListener.onTimelineChanged(
        EMPTY_TIMELINE_EVENT_TIME, Player.TIMELINE_CHANGE_REASON_DYNAMIC);

    verify(callback).onPlaybackStatsReady(any(), any());
  }
}
