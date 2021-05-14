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

import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.TestExoPlayerBuilder;
import com.google.common.collect.ImmutableList;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.shadows.ShadowLooper;

/** Unit test for {@link PlaybackStatsListener}. */
@RunWith(AndroidJUnit4.class)
public final class PlaybackStatsListenerTest {

  private SimpleExoPlayer player;

  @Before
  public void setUp() {
    player = new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build();
  }

  @After
  public void tearDown() {
    player.release();
  }

  @Test
  public void events_duringInitialIdleState_dontCreateNewPlaybackStats() throws Exception {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);
    player.addAnalyticsListener(playbackStatsListener);

    player.seekTo(/* positionMs= */ 1234);
    runUntilPendingCommandsAreFullyHandled(player);
    player.setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f));
    runUntilPendingCommandsAreFullyHandled(player);
    player.play();
    runUntilPendingCommandsAreFullyHandled(player);

    assertThat(playbackStatsListener.getPlaybackStats()).isNull();
  }

  @Test
  public void stateChangeEvent_toEndedWithEmptyTimeline_doesNotCreateInitialPlaybackStats()
      throws Exception {
    PlaybackStatsListener.Callback callback = mock(PlaybackStatsListener.Callback.class);
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, callback);
    player.addAnalyticsListener(playbackStatsListener);

    player.prepare();
    runUntilPendingCommandsAreFullyHandled(player);

    assertThat(playbackStatsListener.getPlaybackStats()).isNull();
    verifyNoMoreInteractions(callback);
  }

  @Test
  public void timelineChangeEvent_toNonEmpty_createsInitialPlaybackStats() throws Exception {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);
    player.addAnalyticsListener(playbackStatsListener);

    player.setMediaItem(MediaItem.fromUri("http://test.org"));
    runUntilPendingCommandsAreFullyHandled(player);

    assertThat(playbackStatsListener.getPlaybackStats()).isNotNull();
  }

  @Test
  public void playback_withKeepHistory_updatesStats() throws Exception {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);
    player.addAnalyticsListener(playbackStatsListener);

    player.setMediaSource(new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1)));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    runUntilPendingCommandsAreFullyHandled(player);

    @Nullable PlaybackStats playbackStats = playbackStatsListener.getPlaybackStats();
    assertThat(playbackStats).isNotNull();
    assertThat(playbackStats.endedCount).isEqualTo(1);
  }

  @Test
  public void playback_withoutKeepHistory_updatesStats() throws Exception {
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ false, /* callback= */ null);
    player.addAnalyticsListener(playbackStatsListener);

    player.setMediaSource(new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1)));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    runUntilPendingCommandsAreFullyHandled(player);

    @Nullable PlaybackStats playbackStats = playbackStatsListener.getPlaybackStats();
    assertThat(playbackStats).isNotNull();
    assertThat(playbackStats.endedCount).isEqualTo(1);
  }

  @Test
  public void finishedSession_callsCallback() throws Exception {
    PlaybackStatsListener.Callback callback = mock(PlaybackStatsListener.Callback.class);
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, callback);
    player.addAnalyticsListener(playbackStatsListener);

    // Create session with some events and finish it by removing it from the playlist.
    player.setMediaSource(new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1)));
    player.prepare();
    runUntilPendingCommandsAreFullyHandled(player);
    verify(callback, never()).onPlaybackStatsReady(any(), any());
    player.clearMediaItems();
    runUntilPendingCommandsAreFullyHandled(player);

    verify(callback).onPlaybackStatsReady(any(), any());
  }

  @Test
  public void playlistClear_callsAllPendingCallbacks() throws Exception {
    PlaybackStatsListener.Callback callback = mock(PlaybackStatsListener.Callback.class);
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, callback);
    player.addAnalyticsListener(playbackStatsListener);

    MediaSource mediaSource = new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1));
    player.setMediaSources(ImmutableList.of(mediaSource, mediaSource));
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    // Play close to the end of the first item to ensure the second session is already created, but
    // the first one isn't finished yet.
    TestPlayerRunHelper.playUntilPosition(
        player, /* windowIndex= */ 0, /* positionMs= */ player.getDuration());
    runUntilPendingCommandsAreFullyHandled(player);
    player.clearMediaItems();
    ShadowLooper.idleMainLooper();

    ArgumentCaptor<AnalyticsListener.EventTime> eventTimeCaptor =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(callback, times(2)).onPlaybackStatsReady(eventTimeCaptor.capture(), any());
    assertThat(
            eventTimeCaptor.getAllValues().stream()
                .map(eventTime -> eventTime.windowIndex)
                .collect(Collectors.toList()))
        .containsExactly(0, 1);
  }

  @Test
  public void playerRelease_callsAllPendingCallbacks() throws Exception {
    PlaybackStatsListener.Callback callback = mock(PlaybackStatsListener.Callback.class);
    PlaybackStatsListener playbackStatsListener =
        new PlaybackStatsListener(/* keepHistory= */ true, callback);
    player.addAnalyticsListener(playbackStatsListener);

    MediaSource mediaSource = new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1));
    player.setMediaSources(ImmutableList.of(mediaSource, mediaSource));
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    // Play close to the end of the first item to ensure the second session is already created, but
    // the first one isn't finished yet.
    TestPlayerRunHelper.playUntilPosition(
        player, /* windowIndex= */ 0, /* positionMs= */ player.getDuration());
    runUntilPendingCommandsAreFullyHandled(player);
    player.release();
    ShadowLooper.idleMainLooper();

    ArgumentCaptor<AnalyticsListener.EventTime> eventTimeCaptor =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(callback, times(2)).onPlaybackStatsReady(eventTimeCaptor.capture(), any());
    assertThat(
            eventTimeCaptor.getAllValues().stream()
                .map(eventTime -> eventTime.windowIndex)
                .collect(Collectors.toList()))
        .containsExactly(0, 1);
  }
}
