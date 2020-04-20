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

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link PlaybackStatsListener}. */
@RunWith(AndroidJUnit4.class)
public final class PlaybackStatsListenerTest {

  private static final AnalyticsListener.EventTime TEST_EVENT_TIME =
      new AnalyticsListener.EventTime(
          /* realtimeMs= */ 500,
          Timeline.EMPTY,
          /* windowIndex= */ 0,
          /* mediaPeriodId= */ null,
          /* eventPlaybackPositionMs= */ 0,
          /* currentPlaybackPositionMs= */ 0,
          /* totalBufferedDurationMs= */ 0);

  @Test
  public void playback_withKeepHistory_updatesStats() {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);

    playbackStatsListener.onPlaybackStateChanged(TEST_EVENT_TIME, Player.STATE_BUFFERING);
    playbackStatsListener.onPlaybackStateChanged(TEST_EVENT_TIME, Player.STATE_READY);
    playbackStatsListener.onPlaybackStateChanged(TEST_EVENT_TIME, Player.STATE_ENDED);

    @Nullable PlaybackStats playbackStats = playbackStatsListener.getPlaybackStats();
    assertThat(playbackStats).isNotNull();
    assertThat(playbackStats.endedCount).isEqualTo(1);
  }

  @Test
  public void playback_withoutKeepHistory_updatesStats() {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ false, /* callback= */ null);

    playbackStatsListener.onPlaybackStateChanged(TEST_EVENT_TIME, Player.STATE_BUFFERING);
    playbackStatsListener.onPlaybackStateChanged(TEST_EVENT_TIME, Player.STATE_READY);
    playbackStatsListener.onPlaybackStateChanged(TEST_EVENT_TIME, Player.STATE_ENDED);

    @Nullable PlaybackStats playbackStats = playbackStatsListener.getPlaybackStats();
    assertThat(playbackStats).isNotNull();
    assertThat(playbackStats.endedCount).isEqualTo(1);
  }
}
