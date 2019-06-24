/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ARGS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_INDEX;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ITEMS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_UUID;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_UUIDS;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/** Unit test for {@link ExoCastPlayer}. */
@RunWith(AndroidJUnit4.class)
public class ExoCastPlayerTest {

  private static final long MOCK_SEQUENCE_NUMBER = 1;
  private ExoCastPlayer player;
  private MediaItem.Builder itemBuilder;
  private CastSessionManager.StateListener receiverAppStateListener;
  private FakeClock clock;
  @Mock private CastSessionManager sessionManager;
  @Mock private SessionAvailabilityListener sessionAvailabilityListener;
  @Mock private Player.EventListener playerEventListener;

  @Before
  public void setUp() {
    initMocks(this);
    clock = new FakeClock(/* initialTimeMs= */ 0);
    player =
        new ExoCastPlayer(
            listener -> {
              receiverAppStateListener = listener;
              return sessionManager;
            },
            clock);
    player.addListener(playerEventListener);
    itemBuilder = new MediaItem.Builder();
  }

  @Test
  public void exoCastPlayer_startsAndStopsSessionManager() {
    // The session manager should have been started when setting up, with the creation of
    // ExoCastPlayer.
    verify(sessionManager).start();
    verifyNoMoreInteractions(sessionManager);
    player.release();
    verify(sessionManager).stopTrackingSession();
    verifyNoMoreInteractions(sessionManager);
  }

  @Test
  public void exoCastPlayer_propagatesSessionStatus() {
    player.setSessionAvailabilityListener(sessionAvailabilityListener);
    verify(sessionAvailabilityListener, never()).onCastSessionAvailable();
    receiverAppStateListener.onCastSessionAvailable();
    verify(sessionAvailabilityListener).onCastSessionAvailable();
    verifyNoMoreInteractions(sessionAvailabilityListener);
    receiverAppStateListener.onCastSessionUnavailable();
    verify(sessionAvailabilityListener).onCastSessionUnavailable();
    verifyNoMoreInteractions(sessionAvailabilityListener);
  }

  @Test
  public void addItemsToQueue_producesExpectedMessages() throws JSONException {
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    MediaItem item3 = itemBuilder.setUuid(toUuid(3)).build();
    MediaItem item4 = itemBuilder.setUuid(toUuid(4)).build();
    MediaItem item5 = itemBuilder.setUuid(toUuid(5)).build();

    player.addItemsToQueue(item1, item2);
    assertMediaItemQueue(item1, item2);

    player.addItemsToQueue(1, item3, item4);
    assertMediaItemQueue(item1, item3, item4, item2);

    player.addItemsToQueue(item5);
    assertMediaItemQueue(item1, item3, item4, item2, item5);

    ArgumentCaptor<ExoCastMessage> messageCaptor = ArgumentCaptor.forClass(ExoCastMessage.class);
    verify(sessionManager, times(3)).send(messageCaptor.capture());
    assertMessageAddsItems(
        /* message= */ messageCaptor.getAllValues().get(0),
        /* index= */ C.INDEX_UNSET,
        Arrays.asList(item1, item2));
    assertMessageAddsItems(
        /* message= */ messageCaptor.getAllValues().get(1),
        /* index= */ 1,
        Arrays.asList(item3, item4));
    assertMessageAddsItems(
        /* message= */ messageCaptor.getAllValues().get(2),
        /* index= */ C.INDEX_UNSET,
        Collections.singletonList(item5));
  }

  @Test
  public void addItemsToQueue_masksRemoteUpdates() {
    player.prepare();
    when(sessionManager.send(any(ExoCastMessage.class))).thenReturn(3L);
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    MediaItem item3 = itemBuilder.setUuid(toUuid(3)).build();
    MediaItem item4 = itemBuilder.setUuid(toUuid(4)).build();

    player.addItemsToQueue(item1, item2);
    assertMediaItemQueue(item1, item2);

    // Should be ignored due to a lower sequence number.
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 2)
            .setItems(Arrays.asList(item3, item4))
            .build());

    // Should override the current state.
    assertMediaItemQueue(item1, item2);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 3)
            .setItems(Arrays.asList(item3, item4))
            .build());

    assertMediaItemQueue(item3, item4);
  }

  @Test
  public void addItemsToQueue_masksWindowIndexAsExpected() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.build(), itemBuilder.build(), itemBuilder.build());
    player.seekTo(/* windowIndex= */ 2, /* positionMs= */ 500);

    assertThat(player.getCurrentWindowIndex()).isEqualTo(2);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(2);
    player.addItemsToQueue(/* optionalIndex= */ 0, itemBuilder.build());
    assertThat(player.getCurrentWindowIndex()).isEqualTo(3);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(3);

    player.addItemsToQueue(itemBuilder.build());
    assertThat(player.getCurrentWindowIndex()).isEqualTo(3);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(3);
  }

  @Test
  public void addItemsToQueue_doesNotAddDuplicateUuids() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.setUuid(toUuid(1)).build());
    assertThat(player.getQueueSize()).isEqualTo(1);
    player.addItemsToQueue(
        itemBuilder.setUuid(toUuid(1)).build(), itemBuilder.setUuid(toUuid(2)).build());
    assertThat(player.getQueueSize()).isEqualTo(2);
    try {
      player.addItemsToQueue(
          itemBuilder.setUuid(toUuid(3)).build(), itemBuilder.setUuid(toUuid(3)).build());
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void moveItemInQueue_behavesAsExpected() throws JSONException {
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    MediaItem item3 = itemBuilder.setUuid(toUuid(3)).build();
    player.addItemsToQueue(item1, item2, item3);
    assertMediaItemQueue(item1, item2, item3);
    player.moveItemInQueue(/* index= */ 0, /* newIndex= */ 2);
    assertMediaItemQueue(item2, item3, item1);
    player.moveItemInQueue(/* index= */ 1, /* newIndex= */ 1);
    assertMediaItemQueue(item2, item3, item1);
    player.moveItemInQueue(/* index= */ 1, /* newIndex= */ 0);
    assertMediaItemQueue(item3, item2, item1);

    ArgumentCaptor<ExoCastMessage> messageCaptor = ArgumentCaptor.forClass(ExoCastMessage.class);
    verify(sessionManager, times(4)).send(messageCaptor.capture());
    // First sent message is an "add" message.
    assertMessageMovesItem(
        /* message= */ messageCaptor.getAllValues().get(1), item1, /* index= */ 2);
    assertMessageMovesItem(
        /* message= */ messageCaptor.getAllValues().get(2), item3, /* index= */ 1);
    assertMessageMovesItem(
        /* message= */ messageCaptor.getAllValues().get(3), item3, /* index= */ 0);
  }

  @Test
  public void moveItemInQueue_moveBeforeToAfter_masksWindowIndexAsExpected() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.build(), itemBuilder.build(), itemBuilder.build());
    player.seekTo(/* windowIndex= */ 1, /* positionMs= */ 500);

    assertThat(player.getCurrentWindowIndex()).isEqualTo(1);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(1);
    player.moveItemInQueue(/* index= */ 0, /* newIndex= */ 1);
    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(0);
  }

  @Test
  public void moveItemInQueue_moveAfterToBefore_masksWindowIndexAsExpected() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.build(), itemBuilder.build(), itemBuilder.build());
    player.seekTo(/* windowIndex= */ 0, /* positionMs= */ 500);

    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(0);
    player.moveItemInQueue(/* index= */ 1, /* newIndex= */ 0);
    assertThat(player.getCurrentWindowIndex()).isEqualTo(1);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(1);
  }

  @Test
  public void moveItemInQueue_moveCurrent_masksWindowIndexAsExpected() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.build(), itemBuilder.build(), itemBuilder.build());
    player.seekTo(/* windowIndex= */ 0, /* positionMs= */ 500);

    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(0);
    player.moveItemInQueue(/* index= */ 0, /* newIndex= */ 2);
    assertThat(player.getCurrentWindowIndex()).isEqualTo(2);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(2);
  }

  @Test
  public void removeItemsFromQueue_masksMediaQueue() throws JSONException {
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    MediaItem item3 = itemBuilder.setUuid(toUuid(3)).build();
    MediaItem item4 = itemBuilder.setUuid(toUuid(4)).build();
    MediaItem item5 = itemBuilder.setUuid(toUuid(5)).build();
    player.addItemsToQueue(item1, item2, item3, item4, item5);
    assertMediaItemQueue(item1, item2, item3, item4, item5);

    player.removeItemFromQueue(2);
    assertMediaItemQueue(item1, item2, item4, item5);

    player.removeRangeFromQueue(1, 3);
    assertMediaItemQueue(item1, item5);

    player.clearQueue();
    assertMediaItemQueue();

    ArgumentCaptor<ExoCastMessage> messageCaptor = ArgumentCaptor.forClass(ExoCastMessage.class);
    verify(sessionManager, times(4)).send(messageCaptor.capture());
    // First sent message is an "add" message.
    assertMessageRemovesItems(
        messageCaptor.getAllValues().get(1), Collections.singletonList(item3));
    assertMessageRemovesItems(messageCaptor.getAllValues().get(2), Arrays.asList(item2, item4));
    assertMessageRemovesItems(messageCaptor.getAllValues().get(3), Arrays.asList(item1, item5));
  }

  @Test
  public void removeRangeFromQueue_beforeCurrentItem_masksWindowIndexAsExpected() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.build(), itemBuilder.build(), itemBuilder.build());
    player.seekTo(/* windowIndex= */ 2, /* positionMs= */ 500);

    assertThat(player.getCurrentWindowIndex()).isEqualTo(2);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(2);
    player.removeRangeFromQueue(/* indexFrom= */ 0, /* indexExclusiveTo= */ 2);
    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(0);
  }

  @Test
  public void removeRangeFromQueue_currentItem_masksWindowIndexAsExpected() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.build(), itemBuilder.build(), itemBuilder.build());
    player.seekTo(/* windowIndex= */ 1, /* positionMs= */ 500);

    assertThat(player.getCurrentWindowIndex()).isEqualTo(1);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(1);
    player.removeRangeFromQueue(/* indexFrom= */ 0, /* indexExclusiveTo= */ 2);
    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(0);
  }

  @Test
  public void removeRangeFromQueue_currentItemWhichIsLast_transitionsToEnded() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.build(), itemBuilder.build(), itemBuilder.build());
    player.seekTo(/* windowIndex= */ 1, /* positionMs= */ 500);

    assertThat(player.getCurrentWindowIndex()).isEqualTo(1);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(1);
    player.removeRangeFromQueue(/* indexFrom= */ 1, /* indexExclusiveTo= */ 3);
    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(0);
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_ENDED);
  }

  @Test
  public void clearQueue_resetsPlaybackPosition() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.build(), itemBuilder.build(), itemBuilder.build());
    player.seekTo(/* windowIndex= */ 1, /* positionMs= */ 500);

    assertThat(player.getCurrentWindowIndex()).isEqualTo(1);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(1);
    player.clearQueue();
    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(0);
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_ENDED);
  }

  @Test
  public void prepare_emptyQueue_transitionsToEnded() {
    player.prepare();
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_ENDED);
    verify(playerEventListener).onPlayerStateChanged(/* playWhenReady=*/ false, Player.STATE_ENDED);
  }

  @Test
  public void prepare_withQueue_transitionsToBuffering() {
    player.addItemsToQueue(itemBuilder.build());
    player.prepare();
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_BUFFERING);
    verify(playerEventListener)
        .onPlayerStateChanged(/* playWhenReady=*/ false, Player.STATE_BUFFERING);
    ArgumentCaptor<Timeline> argumentCaptor = ArgumentCaptor.forClass(Timeline.class);
    verify(playerEventListener)
        .onTimelineChanged(
            argumentCaptor.capture(),
            /* manifest= */ isNull(),
            eq(Player.TIMELINE_CHANGE_REASON_PREPARED));
    assertThat(argumentCaptor.getValue().getWindowCount()).isEqualTo(1);
  }

  @Test
  public void stop_withoutReset_leavesCurrentTimeline() {
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_IDLE);
    player.addItemsToQueue(itemBuilder.setUuid(toUuid(1)).build());
    player.prepare();
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_BUFFERING);
    player.seekTo(/* windowIndex= */ 0, /* positionMs= */ C.TIME_UNSET);
    verify(playerEventListener)
        .onPlayerStateChanged(/* playWhenReady =*/ false, Player.STATE_BUFFERING);
    verify(playerEventListener).onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
    player.stop(/* reset= */ false);
    verify(playerEventListener).onPlayerStateChanged(/* playWhenReady =*/ false, Player.STATE_IDLE);

    ArgumentCaptor<Timeline> argumentCaptor = ArgumentCaptor.forClass(Timeline.class);
    // Update for prepare.
    verify(playerEventListener)
        .onTimelineChanged(
            argumentCaptor.capture(),
            /* manifest= */ isNull(),
            eq(Player.TIMELINE_CHANGE_REASON_PREPARED));
    assertThat(argumentCaptor.getValue().getWindowCount()).isEqualTo(1);

    // Update for stop.
    verifyNoMoreInteractions(playerEventListener);
    assertThat(player.getCurrentTimeline().getWindowCount()).isEqualTo(1);
  }

  @Test
  public void stop_withReset_clearsQueue() {
    player.prepare();
    player.addItemsToQueue(itemBuilder.setUuid(toUuid(1)).build());
    verify(playerEventListener)
        .onTimelineChanged(
            any(Timeline.class), isNull(), eq(Player.TIMELINE_CHANGE_REASON_DYNAMIC));
    player.seekTo(/* windowIndex= */ 0, /* positionMs= */ C.TIME_UNSET);
    verify(playerEventListener)
        .onPlayerStateChanged(/* playWhenReady =*/ false, Player.STATE_BUFFERING);
    player.stop(/* reset= */ true);
    verify(playerEventListener).onPlayerStateChanged(/* playWhenReady =*/ false, Player.STATE_IDLE);

    // Update for add.
    ArgumentCaptor<Timeline> argumentCaptor = ArgumentCaptor.forClass(Timeline.class);
    verify(playerEventListener)
        .onTimelineChanged(
            argumentCaptor.capture(),
            /* manifest= */ isNull(),
            eq(Player.TIMELINE_CHANGE_REASON_DYNAMIC));
    assertThat(argumentCaptor.getValue().getWindowCount()).isEqualTo(1);

    // Update for stop.
    verify(playerEventListener)
        .onTimelineChanged(
            argumentCaptor.capture(),
            /* manifest= */ isNull(),
            eq(Player.TIMELINE_CHANGE_REASON_RESET));
    assertThat(argumentCaptor.getValue().getWindowCount()).isEqualTo(0);

    assertThat(player.getCurrentTimeline().isEmpty()).isTrue();
  }

  @Test
  public void getCurrentTimeline_masksRemoteUpdates() {
    player.prepare();
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    assertThat(player.getCurrentTimeline().isEmpty()).isTrue();
    player.addItemsToQueue(item1, item2);

    ArgumentCaptor<Timeline> messageCaptor = ArgumentCaptor.forClass(Timeline.class);
    verify(playerEventListener)
        .onTimelineChanged(
            messageCaptor.capture(),
            /* manifest= */ isNull(),
            eq(Player.TIMELINE_CHANGE_REASON_DYNAMIC));
    Timeline reportedTimeline = messageCaptor.getValue();
    assertThat(reportedTimeline).isSameInstanceAs(player.getCurrentTimeline());
    assertThat(reportedTimeline.getWindowCount()).isEqualTo(2);
    assertThat(reportedTimeline.getWindow(/* windowIndex= */ 0, new Timeline.Window()).durationUs)
        .isEqualTo(C.TIME_UNSET);
    assertThat(reportedTimeline.getWindow(/* windowIndex= */ 1, new Timeline.Window()).durationUs)
        .isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getCurrentTimeline_exposesReceiverState() {
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 1)
            .setPlaybackState(Player.STATE_BUFFERING)
            .setItems(Arrays.asList(item1, item2))
            .setShuffleOrder(Arrays.asList(1, 0))
            .build());
    ArgumentCaptor<Timeline> messageCaptor = ArgumentCaptor.forClass(Timeline.class);
    verify(playerEventListener)
        .onTimelineChanged(
            messageCaptor.capture(),
            /* manifest= */ isNull(),
            eq(Player.TIMELINE_CHANGE_REASON_DYNAMIC));
    Timeline reportedTimeline = messageCaptor.getValue();
    assertThat(reportedTimeline).isSameInstanceAs(player.getCurrentTimeline());
    assertThat(reportedTimeline.getWindowCount()).isEqualTo(2);
    assertThat(reportedTimeline.getWindow(/* windowIndex= */ 0, new Timeline.Window()).durationUs)
        .isEqualTo(C.TIME_UNSET);
    assertThat(reportedTimeline.getWindow(/* windowIndex= */ 1, new Timeline.Window()).durationUs)
        .isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void timelineUpdateFromReceiver_matchesLocalState_doesNotCallEventLsitener() {
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    MediaItem item3 = itemBuilder.setUuid(toUuid(3)).build();
    MediaItem item4 = itemBuilder.setUuid(toUuid(4)).build();

    MediaItemInfo.Period period1 =
        new MediaItemInfo.Period("id1", /* durationUs= */ 1000000L, /* positionInWindowUs= */ 0);
    MediaItemInfo.Period period2 =
        new MediaItemInfo.Period(
            "id2", /* durationUs= */ 1000000L, /* positionInWindowUs= */ 1000000L);
    MediaItemInfo.Period period3 =
        new MediaItemInfo.Period(
            "id3", /* durationUs= */ 1000000L, /* positionInWindowUs= */ 2000000L);
    HashMap<UUID, MediaItemInfo> mediaItemInfoMap1 = new HashMap<>();
    mediaItemInfoMap1.put(
        toUuid(1),
        new MediaItemInfo(
            /* windowDurationUs= */ 3000L,
            /* defaultStartPositionUs= */ 10,
            /* periods= */ Arrays.asList(period1, period2, period3),
            /* positionInFirstPeriodUs= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ false));
    mediaItemInfoMap1.put(
        toUuid(3),
        new MediaItemInfo(
            /* windowDurationUs= */ 2000L,
            /* defaultStartPositionUs= */ 10,
            /* periods= */ Arrays.asList(period1, period2),
            /* positionInFirstPeriodUs= */ 500,
            /* isSeekable= */ true,
            /* isDynamic= */ false));

    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(1)
            .setPlaybackState(Player.STATE_BUFFERING)
            .setItems(Arrays.asList(item1, item2, item3, item4))
            .setShuffleOrder(Arrays.asList(1, 0, 2, 3))
            .setMediaItemsInformation(mediaItemInfoMap1)
            .build());
    verify(playerEventListener)
        .onTimelineChanged(
            any(), /* manifest= */ isNull(), eq(Player.TIMELINE_CHANGE_REASON_DYNAMIC));
    verify(playerEventListener)
        .onPlayerStateChanged(
            /* playWhenReady= */ false, /* playbackState= */ Player.STATE_BUFFERING);

    HashMap<UUID, MediaItemInfo> mediaItemInfoMap2 = new HashMap<>(mediaItemInfoMap1);
    mediaItemInfoMap2.put(
        toUuid(5),
        new MediaItemInfo(
            /* windowDurationUs= */ 5,
            /* defaultStartPositionUs= */ 0,
            /* periods= */ Arrays.asList(period1, period2),
            /* positionInFirstPeriodUs= */ 500,
            /* isSeekable= */ true,
            /* isDynamic= */ false));

    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(1).setMediaItemsInformation(mediaItemInfoMap2).build());
    verifyNoMoreInteractions(playerEventListener);
  }

  @Test
  public void getPeriodIndex_producesExpectedOutput() {
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    MediaItem item3 = itemBuilder.setUuid(toUuid(3)).build();
    MediaItem item4 = itemBuilder.setUuid(toUuid(4)).build();

    MediaItemInfo.Period period1 =
        new MediaItemInfo.Period("id1", /* durationUs= */ 1000000L, /* positionInWindowUs= */ 0);
    MediaItemInfo.Period period2 =
        new MediaItemInfo.Period(
            "id2", /* durationUs= */ 1000000L, /* positionInWindowUs= */ 1000000L);
    MediaItemInfo.Period period3 =
        new MediaItemInfo.Period(
            "id3", /* durationUs= */ 1000000L, /* positionInWindowUs= */ 2000000L);
    HashMap<UUID, MediaItemInfo> mediaItemInfoMap = new HashMap<>();
    mediaItemInfoMap.put(
        toUuid(1),
        new MediaItemInfo(
            /* windowDurationUs= */ 3000L,
            /* defaultStartPositionUs= */ 10,
            /* periods= */ Arrays.asList(period1, period2, period3),
            /* positionInFirstPeriodUs= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ false));
    mediaItemInfoMap.put(
        toUuid(3),
        new MediaItemInfo(
            /* windowDurationUs= */ 2000L,
            /* defaultStartPositionUs= */ 10,
            /* periods= */ Arrays.asList(period1, period2),
            /* positionInFirstPeriodUs= */ 500,
            /* isSeekable= */ true,
            /* isDynamic= */ false));

    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 1L)
            .setPlaybackState(Player.STATE_BUFFERING)
            .setItems(Arrays.asList(item1, item2, item3, item4))
            .setShuffleOrder(Arrays.asList(1, 0, 3, 2))
            .setMediaItemsInformation(mediaItemInfoMap)
            .setPlaybackPosition(
                /* currentPlayingItemUuid= */ item3.uuid,
                /* currentPlayingPeriodId= */ "id2",
                /* currentPlaybackPositionMs= */ 500L)
            .build());

    assertThat(player.getCurrentPeriodIndex()).isEqualTo(5);
    player.seekTo(/* windowIndex= */ 1, /* positionMs= */ 0L);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(3);
    player.seekTo(/* windowIndex= */ 0, /* positionMs= */ 1500L);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(1);
  }

  @Test
  public void exoCastPlayer_propagatesPlayerStateFromReceiver() {
    ReceiverAppStateUpdate.Builder builder =
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 1);

    // The first idle state update should be discarded, since it matches the current state.
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        builder.setPlaybackState(Player.STATE_IDLE).build());
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        builder.setPlaybackState(Player.STATE_BUFFERING).build());
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        builder.setPlaybackState(Player.STATE_READY).build());
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        builder.setPlaybackState(Player.STATE_ENDED).build());
    ArgumentCaptor<Integer> messageCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(playerEventListener, times(3))
        .onPlayerStateChanged(/* playWhenReady= */ eq(false), messageCaptor.capture());
    List<Integer> states = messageCaptor.getAllValues();
    assertThat(states).hasSize(3);
    assertThat(states)
        .isEqualTo(Arrays.asList(Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED));
  }

  @Test
  public void setPlayWhenReady_changedLocally_notifiesListeners() {
    player.setPlayWhenReady(false);
    verify(playerEventListener, never()).onPlayerStateChanged(false, Player.STATE_IDLE);
    player.setPlayWhenReady(true);
    verify(playerEventListener).onPlayerStateChanged(true, Player.STATE_IDLE);
    player.setPlayWhenReady(false);
    verify(playerEventListener).onPlayerStateChanged(false, Player.STATE_IDLE);
  }

  @Test
  public void setPlayWhenReady_changedRemotely_notifiesListeners() {
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 0).setPlayWhenReady(true).build());
    verify(playerEventListener)
        .onPlayerStateChanged(/* playWhenReady= */ true, /* playbackState= */ Player.STATE_IDLE);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 0).setPlayWhenReady(true).build());
    verifyNoMoreInteractions(playerEventListener);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 0).setPlayWhenReady(false).build());
    verify(playerEventListener)
        .onPlayerStateChanged(/* playWhenReady= */ false, /* playbackState= */ Player.STATE_IDLE);
    verifyNoMoreInteractions(playerEventListener);
  }

  @Test
  public void getPlayWhenReady_masksRemoteUpdates() {
    when(sessionManager.send(any(ExoCastMessage.class))).thenReturn(3L);
    player.setPlayWhenReady(true);
    verify(playerEventListener)
        .onPlayerStateChanged(/* playWhenReady= */ true, /* playbackState= */ Player.STATE_IDLE);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 2).setPlayWhenReady(false).build());
    verifyNoMoreInteractions(playerEventListener);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 3).setPlayWhenReady(true).build());
    verifyNoMoreInteractions(playerEventListener);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 3).setPlayWhenReady(false).build());
    verify(playerEventListener)
        .onPlayerStateChanged(/* playWhenReady= */ false, /* playbackState= */ Player.STATE_IDLE);
  }

  @Test
  public void setRepeatMode_changedLocally_notifiesListeners() {
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    verifyNoMoreInteractions(playerEventListener);
    player.setRepeatMode(Player.REPEAT_MODE_ONE);
    verify(playerEventListener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);
    player.setRepeatMode(Player.REPEAT_MODE_ONE);
    verifyNoMoreInteractions(playerEventListener);
  }

  @Test
  public void setRepeatMode_changedRemotely_notifiesListeners() {
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 0)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .build());
    verify(playerEventListener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ONE);
  }

  @Test
  public void getRepeatMode_masksRemoteUpdates() {
    when(sessionManager.send(any(ExoCastMessage.class))).thenReturn(3L);
    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
    verify(playerEventListener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 2)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .build());
    verifyNoMoreInteractions(playerEventListener);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 3)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .build());
    verify(playerEventListener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);
  }

  @Test
  public void getPlaybackPosition_withStateChanges_producesExpectedOutput() {
    UUID uuid = toUuid(1);
    HashMap<UUID, MediaItemInfo> mediaItemInfoMap = new HashMap<>();

    MediaItemInfo.Period period1 = new MediaItemInfo.Period("id1", 1000L, 0);
    MediaItemInfo.Period period2 = new MediaItemInfo.Period("id2", 1000L, 0);
    MediaItemInfo.Period period3 = new MediaItemInfo.Period("id3", 1000L, 0);
    mediaItemInfoMap.put(
        uuid,
        new MediaItemInfo(
            /* windowDurationUs= */ 1000L,
            /* defaultStartPositionUs= */ 10,
            /* periods= */ Arrays.asList(period1, period2, period3),
            /* positionInFirstPeriodUs= */ 500,
            /* isSeekable= */ true,
            /* isDynamic= */ false));

    when(sessionManager.send(any(ExoCastMessage.class))).thenReturn(1L);
    player.addItemsToQueue(itemBuilder.setUuid(uuid).build());
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 1)
            .setPlaybackState(Player.STATE_BUFFERING)
            .setMediaItemsInformation(mediaItemInfoMap)
            .setPlaybackPosition(uuid, "id2", /* currentPlaybackPositionMs= */ 1000L)
            .build());
    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPosition()).isEqualTo(1000L);
    clock.advanceTime(/* timeDiffMs= */ 1L);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 1)
            .setPlaybackState(Player.STATE_READY)
            .build());
    // Play when ready is still false, so position should not change.
    assertThat(player.getCurrentPosition()).isEqualTo(1000L);
    player.setPlayWhenReady(true);
    clock.advanceTime(1);
    assertThat(player.getCurrentPosition()).isEqualTo(1001L);
    clock.advanceTime(1);
    assertThat(player.getCurrentPosition()).isEqualTo(1002L);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 1)
            .setPlaybackState(Player.STATE_BUFFERING)
            .setMediaItemsInformation(mediaItemInfoMap)
            .setPlaybackPosition(uuid, "id2", /* currentPlaybackPositionMs= */ 1010L)
            .build());
    clock.advanceTime(1);
    assertThat(player.getCurrentPosition()).isEqualTo(1010L);
    clock.advanceTime(1);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 1)
            .setPlaybackState(Player.STATE_READY)
            .setMediaItemsInformation(mediaItemInfoMap)
            .setPlaybackPosition(uuid, "id2", /* currentPlaybackPositionMs= */ 1011L)
            .build());
    clock.advanceTime(10);
    assertThat(player.getCurrentPosition()).isEqualTo(1021L);
  }

  @Test
  public void getPlaybackPosition_withNonDefaultPlaybackSpeed_producesExpectedOutput() {
    MediaItem item = itemBuilder.setUuid(toUuid(1)).build();
    MediaItemInfo info =
        new MediaItemInfo(
            /* windowDurationUs= */ 10000000,
            /* defaultStartPositionUs= */ 3000000,
            /* periods= */ Collections.singletonList(
                new MediaItemInfo.Period(
                    /* id= */ "id", /* durationUs= */ 10000000, /* positionInWindowUs= */ 0)),
            /* positionInFirstPeriodUs= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ false);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 1)
            .setMediaItemsInformation(Collections.singletonMap(toUuid(1), info))
            .setShuffleOrder(Collections.singletonList(0))
            .setItems(Collections.singletonList(item))
            .setPlaybackPosition(
                toUuid(1), /* currentPlayingPeriodId= */ "id", /* currentPlaybackPositionMs= */ 20L)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(true)
            .build());
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(0);
    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPosition()).isEqualTo(20);
    clock.advanceTime(10);
    assertThat(player.getCurrentPosition()).isEqualTo(30);
    clock.advanceTime(10);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(1)
            .setPlaybackPosition(
                toUuid(1), /* currentPlayingPeriodId= */ "id", /* currentPlaybackPositionMs= */ 40L)
            .setPlaybackParameters(new PlaybackParameters(2))
            .build());
    clock.advanceTime(10);
    assertThat(player.getCurrentPosition()).isEqualTo(60);
  }

  @Test
  public void positionChanges_notifiesDiscontinuities() {
    UUID uuid = toUuid(1);
    HashMap<UUID, MediaItemInfo> mediaItemInfoMap = new HashMap<>();

    MediaItemInfo.Period period1 = new MediaItemInfo.Period("id1", 1000L, 0);
    MediaItemInfo.Period period2 = new MediaItemInfo.Period("id2", 1000L, 0);
    MediaItemInfo.Period period3 = new MediaItemInfo.Period("id3", 1000L, 0);
    mediaItemInfoMap.put(
        uuid,
        new MediaItemInfo(
            /* windowDurationUs= */ 1000L,
            /* defaultStartPositionUs= */ 10,
            /* periods= */ Arrays.asList(period1, period2, period3),
            /* positionInFirstPeriodUs= */ 500,
            /* isSeekable= */ true,
            /* isDynamic= */ false));

    player.addItemsToQueue(itemBuilder.setUuid(uuid).build());
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 1)
            .setPlaybackState(Player.STATE_BUFFERING)
            .setMediaItemsInformation(mediaItemInfoMap)
            .setPlaybackPosition(uuid, "id2", /* currentPlaybackPositionMs= */ 1000L)
            .setDiscontinuityReason(Player.DISCONTINUITY_REASON_SEEK)
            .build());
    verify(playerEventListener).onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
    player.seekTo(/* windowIndex= */ 0, /* positionMs= */ 999);
    verify(playerEventListener, times(2)).onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
  }

  @Test
  public void setShuffleModeEnabled_changedLocally_notifiesListeners() {
    player.setShuffleModeEnabled(true);
    verify(playerEventListener).onShuffleModeEnabledChanged(true);
    player.setShuffleModeEnabled(true);
    verifyNoMoreInteractions(playerEventListener);
  }

  @Test
  public void setShuffleModeEnabled_changedRemotely_notifiesListeners() {
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 0)
            .setShuffleModeEnabled(true)
            .build());
    verify(playerEventListener).onShuffleModeEnabledChanged(true);
    assertThat(player.getShuffleModeEnabled()).isTrue();
  }

  @Test
  public void getShuffleMode_masksRemoteUpdates() {
    when(sessionManager.send(any(ExoCastMessage.class))).thenReturn(3L);
    player.setShuffleModeEnabled(true);
    assertThat(player.getShuffleModeEnabled()).isTrue();
    verify(playerEventListener).onShuffleModeEnabledChanged(true);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 2)
            .setShuffleModeEnabled(false)
            .build());
    verifyNoMoreInteractions(playerEventListener);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 3)
            .setShuffleModeEnabled(false)
            .build());
    verify(playerEventListener).onShuffleModeEnabledChanged(false);
    assertThat(player.getShuffleModeEnabled()).isFalse();
  }

  @Test
  public void seekTo_inIdle_doesNotChangePlaybackState() {
    player.prepare();
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_ENDED);
    player.addItemsToQueue(itemBuilder.build(), itemBuilder.build());
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_ENDED);
    player.seekTo(/* windowIndex= */ 0, /* positionMs= */ 0);
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_BUFFERING);
    player.stop(false);
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_IDLE);
    player.seekTo(/* windowIndex= */ 0, /* positionMs= */ 0);
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_IDLE);
  }

  @Test
  public void seekTo_withTwoItems_producesExpectedMessage() {
    player.prepare();
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    player.addItemsToQueue(item1, item2);
    player.seekTo(/* windowIndex= */ 1, /* positionMs= */ 1000);
    ArgumentCaptor<ExoCastMessage> messageCaptor = ArgumentCaptor.forClass(ExoCastMessage.class);
    verify(sessionManager, times(3)).send(messageCaptor.capture());
    // Messages should be prepare, add and seek.
    ExoCastMessage.SeekTo seekToMessage =
        (ExoCastMessage.SeekTo) messageCaptor.getAllValues().get(2);
    assertThat(seekToMessage.positionMs).isEqualTo(1000);
    assertThat(seekToMessage.uuid).isEqualTo(toUuid(2));
  }

  @Test
  public void seekTo_masksRemoteUpdates() {
    player.prepare();
    when(sessionManager.send(any(ExoCastMessage.class))).thenReturn(3L);
    MediaItem item1 = itemBuilder.setUuid(toUuid(1)).build();
    MediaItem item2 = itemBuilder.setUuid(toUuid(2)).build();
    player.addItemsToQueue(item1, item2);
    player.seekTo(/* windowIndex= */ 1, /* positionMs= */ 1000L);
    verify(playerEventListener).onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
    verify(playerEventListener)
        .onPlayerStateChanged(
            /* playWhenReady= */ false, /* playbackState= */ Player.STATE_BUFFERING);
    assertThat(player.getCurrentWindowIndex()).isEqualTo(1);
    assertThat(player.getCurrentPosition()).isEqualTo(1000);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 2)
            .setPlaybackPosition(toUuid(1), "id", 500L)
            .build());
    assertThat(player.getCurrentWindowIndex()).isEqualTo(1);
    assertThat(player.getCurrentPosition()).isEqualTo(1000);
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 3)
            .setPlaybackPosition(toUuid(1), "id", 500L)
            .setDiscontinuityReason(Player.DISCONTINUITY_REASON_SEEK)
            .build());
    verify(playerEventListener, times(2)).onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
    assertThat(player.getCurrentWindowIndex()).isEqualTo(0);
    assertThat(player.getCurrentPosition()).isEqualTo(500);
  }

  @Test
  public void setPlaybackParameters_producesExpectedMessage() {
    PlaybackParameters playbackParameters =
        new PlaybackParameters(/* speed= */ .5f, /* pitch= */ .25f, /* skipSilence= */ true);
    player.setPlaybackParameters(playbackParameters);
    ArgumentCaptor<ExoCastMessage> messageCaptor = ArgumentCaptor.forClass(ExoCastMessage.class);
    verify(sessionManager).send(messageCaptor.capture());
    ExoCastMessage.SetPlaybackParameters message =
        (ExoCastMessage.SetPlaybackParameters) messageCaptor.getValue();
    assertThat(message.playbackParameters).isEqualTo(playbackParameters);
  }

  @Test
  public void getTrackSelectionParameters_doesNotOverrideUnexpectedFields() {
    when(sessionManager.send(any(ExoCastMessage.class))).thenReturn(3L);
    DefaultTrackSelector.Parameters parameters =
        DefaultTrackSelector.Parameters.DEFAULT
            .buildUpon()
            .setPreferredAudioLanguage("spa")
            .setMaxVideoSize(/* maxVideoWidth= */ 3, /* maxVideoHeight= */ 3)
            .build();
    player.setTrackSelectionParameters(parameters);
    TrackSelectionParameters returned =
        TrackSelectionParameters.DEFAULT.buildUpon().setPreferredAudioLanguage("deu").build();
    receiverAppStateListener.onStateUpdateFromReceiverApp(
        ReceiverAppStateUpdate.builder(/* sequenceNumber= */ 3)
            .setTrackSelectionParameters(returned)
            .build());
    DefaultTrackSelector.Parameters result =
        (DefaultTrackSelector.Parameters) player.getTrackSelectionParameters();
    assertThat(result.preferredAudioLanguage).isEqualTo("deu");
    assertThat(result.maxVideoHeight).isEqualTo(3);
    assertThat(result.maxVideoWidth).isEqualTo(3);
  }

  @Test
  public void testExoCast_getRendererType() {
    assertThat(player.getRendererCount()).isEqualTo(4);
    assertThat(player.getRendererType(/* index= */ 0)).isEqualTo(C.TRACK_TYPE_VIDEO);
    assertThat(player.getRendererType(/* index= */ 1)).isEqualTo(C.TRACK_TYPE_AUDIO);
    assertThat(player.getRendererType(/* index= */ 2)).isEqualTo(C.TRACK_TYPE_TEXT);
    assertThat(player.getRendererType(/* index= */ 3)).isEqualTo(C.TRACK_TYPE_METADATA);
  }

  private static UUID toUuid(long lowerBits) {
    return new UUID(0, lowerBits);
  }

  private void assertMediaItemQueue(MediaItem... mediaItemQueue) {
    assertThat(player.getQueueSize()).isEqualTo(mediaItemQueue.length);
    for (int i = 0; i < mediaItemQueue.length; i++) {
      assertThat(player.getQueueItem(i).uuid).isEqualTo(mediaItemQueue[i].uuid);
    }
  }

  private static void assertMessageAddsItems(
      ExoCastMessage message, int index, List<MediaItem> mediaItems) throws JSONException {
    assertThat(message.method).isEqualTo(ExoCastConstants.METHOD_ADD_ITEMS);
    JSONObject args =
        new JSONObject(message.toJsonString(MOCK_SEQUENCE_NUMBER)).getJSONObject(KEY_ARGS);
    if (index != C.INDEX_UNSET) {
      assertThat(args.getInt(KEY_INDEX)).isEqualTo(index);
    } else {
      assertThat(args.has(KEY_INDEX)).isFalse();
    }
    JSONArray itemsAsJson = args.getJSONArray(KEY_ITEMS);
    assertThat(ReceiverAppStateUpdate.toMediaItemArrayList(itemsAsJson)).isEqualTo(mediaItems);
  }

  private static void assertMessageMovesItem(ExoCastMessage message, MediaItem item, int index)
      throws JSONException {
    assertThat(message.method).isEqualTo(ExoCastConstants.METHOD_MOVE_ITEM);
    JSONObject args =
        new JSONObject(message.toJsonString(MOCK_SEQUENCE_NUMBER)).getJSONObject(KEY_ARGS);
    assertThat(args.getString(KEY_UUID)).isEqualTo(item.uuid.toString());
    assertThat(args.getInt(KEY_INDEX)).isEqualTo(index);
  }

  private static void assertMessageRemovesItems(ExoCastMessage message, List<MediaItem> items)
      throws JSONException {
    assertThat(message.method).isEqualTo(ExoCastConstants.METHOD_REMOVE_ITEMS);
    JSONObject args =
        new JSONObject(message.toJsonString(MOCK_SEQUENCE_NUMBER)).getJSONObject(KEY_ARGS);
    JSONArray uuidsAsJson = args.getJSONArray(KEY_UUIDS);
    for (int i = 0; i < uuidsAsJson.length(); i++) {
      assertThat(uuidsAsJson.getString(i)).isEqualTo(items.get(i).uuid.toString());
    }
  }
}
