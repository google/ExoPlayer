/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;

/** Tests for {@link CastPlayer}. */
@RunWith(AndroidJUnit4.class)
public class CastPlayerTest {

  private CastPlayer castPlayer;

  @SuppressWarnings("deprecation")
  private RemoteMediaClient.Listener remoteMediaClientListener;

  @Mock private RemoteMediaClient mockRemoteMediaClient;
  @Mock private MediaStatus mockMediaStatus;
  @Mock private MediaInfo mockMediaInfo;
  @Mock private MediaQueue mockMediaQueue;
  @Mock private CastContext mockCastContext;
  @Mock private SessionManager mockSessionManager;
  @Mock private CastSession mockCastSession;
  @Mock private Player.EventListener mockListener;
  @Mock private PendingResult<RemoteMediaClient.MediaChannelResult> mockPendingResult;

  @Captor
  private ArgumentCaptor<ResultCallback<RemoteMediaClient.MediaChannelResult>>
      setResultCallbackArgumentCaptor;

  @Captor private ArgumentCaptor<RemoteMediaClient.Listener> listenerArgumentCaptor;

  @Captor private ArgumentCaptor<MediaQueueItem[]> queueItemsArgumentCaptor;

  @SuppressWarnings("deprecation")
  @Before
  public void setUp() {
    initMocks(this);
    when(mockCastContext.getSessionManager()).thenReturn(mockSessionManager);
    when(mockSessionManager.getCurrentCastSession()).thenReturn(mockCastSession);
    when(mockCastSession.getRemoteMediaClient()).thenReturn(mockRemoteMediaClient);
    when(mockRemoteMediaClient.getMediaStatus()).thenReturn(mockMediaStatus);
    when(mockRemoteMediaClient.getMediaQueue()).thenReturn(mockMediaQueue);
    when(mockMediaQueue.getItemIds()).thenReturn(new int[0]);
    // Make the remote media client present the same default values as ExoPlayer:
    when(mockRemoteMediaClient.isPaused()).thenReturn(true);
    when(mockMediaStatus.getQueueRepeatMode()).thenReturn(MediaStatus.REPEAT_MODE_REPEAT_OFF);
    castPlayer = new CastPlayer(mockCastContext);
    castPlayer.addListener(mockListener);
    verify(mockRemoteMediaClient).addListener(listenerArgumentCaptor.capture());
    remoteMediaClientListener = listenerArgumentCaptor.getValue();
  }

  @SuppressWarnings("deprecation")
  @Test
  public void setPlayWhenReady_masksRemoteState() {
    when(mockRemoteMediaClient.play()).thenReturn(mockPendingResult);
    assertThat(castPlayer.getPlayWhenReady()).isFalse();

    castPlayer.play();
    verify(mockPendingResult).setResultCallback(setResultCallbackArgumentCaptor.capture());
    assertThat(castPlayer.getPlayWhenReady()).isTrue();
    verify(mockListener).onPlayerStateChanged(true, Player.STATE_IDLE);
    verify(mockListener)
        .onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);

    // There is a status update in the middle, which should be hidden by masking.
    remoteMediaClientListener.onStatusUpdated();
    verifyNoMoreInteractions(mockListener);

    // Upon result, the remoteMediaClient has updated its state according to the play() call.
    when(mockRemoteMediaClient.isPaused()).thenReturn(false);
    setResultCallbackArgumentCaptor
        .getValue()
        .onResult(Mockito.mock(RemoteMediaClient.MediaChannelResult.class));
    verifyNoMoreInteractions(mockListener);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void setPlayWhenReadyMasking_updatesUponResultChange() {
    when(mockRemoteMediaClient.play()).thenReturn(mockPendingResult);
    assertThat(castPlayer.getPlayWhenReady()).isFalse();

    castPlayer.play();
    verify(mockPendingResult).setResultCallback(setResultCallbackArgumentCaptor.capture());
    assertThat(castPlayer.getPlayWhenReady()).isTrue();
    verify(mockListener).onPlayerStateChanged(true, Player.STATE_IDLE);
    verify(mockListener)
        .onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);

    // Upon result, the remote media client is still paused. The state should reflect that.
    setResultCallbackArgumentCaptor
        .getValue()
        .onResult(Mockito.mock(RemoteMediaClient.MediaChannelResult.class));
    verify(mockListener).onPlayerStateChanged(false, Player.STATE_IDLE);
    verify(mockListener).onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
    assertThat(castPlayer.getPlayWhenReady()).isFalse();
  }

  @SuppressWarnings("deprecation")
  @Test
  public void setPlayWhenReady_correctChangeReasonOnPause() {
    when(mockRemoteMediaClient.play()).thenReturn(mockPendingResult);
    when(mockRemoteMediaClient.pause()).thenReturn(mockPendingResult);
    castPlayer.play();
    assertThat(castPlayer.getPlayWhenReady()).isTrue();
    verify(mockListener).onPlayerStateChanged(true, Player.STATE_IDLE);
    verify(mockListener)
        .onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);

    castPlayer.pause();
    assertThat(castPlayer.getPlayWhenReady()).isFalse();
    verify(mockListener).onPlayerStateChanged(false, Player.STATE_IDLE);
    verify(mockListener)
        .onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void playWhenReady_changesOnStatusUpdates() {
    assertThat(castPlayer.getPlayWhenReady()).isFalse();
    when(mockRemoteMediaClient.isPaused()).thenReturn(false);
    remoteMediaClientListener.onStatusUpdated();
    verify(mockListener).onPlayerStateChanged(true, Player.STATE_IDLE);
    verify(mockListener).onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
    assertThat(castPlayer.getPlayWhenReady()).isTrue();
  }

  @Test
  public void setRepeatMode_masksRemoteState() {
    when(mockRemoteMediaClient.queueSetRepeatMode(anyInt(), any())).thenReturn(mockPendingResult);
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_OFF);

    castPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
    verify(mockPendingResult).setResultCallback(setResultCallbackArgumentCaptor.capture());
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ONE);
    verify(mockListener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);

    // There is a status update in the middle, which should be hidden by masking.
    when(mockMediaStatus.getQueueRepeatMode()).thenReturn(MediaStatus.REPEAT_MODE_REPEAT_ALL);
    remoteMediaClientListener.onStatusUpdated();
    verifyNoMoreInteractions(mockListener);

    // Upon result, the mediaStatus now exposes the new repeat mode.
    when(mockMediaStatus.getQueueRepeatMode()).thenReturn(MediaStatus.REPEAT_MODE_REPEAT_SINGLE);
    setResultCallbackArgumentCaptor
        .getValue()
        .onResult(Mockito.mock(RemoteMediaClient.MediaChannelResult.class));
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void setRepeatMode_updatesUponResultChange() {
    when(mockRemoteMediaClient.queueSetRepeatMode(anyInt(), any())).thenReturn(mockPendingResult);

    castPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
    verify(mockPendingResult).setResultCallback(setResultCallbackArgumentCaptor.capture());
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ONE);
    verify(mockListener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);

    // There is a status update in the middle, which should be hidden by masking.
    when(mockMediaStatus.getQueueRepeatMode()).thenReturn(MediaStatus.REPEAT_MODE_REPEAT_ALL);
    remoteMediaClientListener.onStatusUpdated();
    verifyNoMoreInteractions(mockListener);

    // Upon result, the repeat mode is ALL. The state should reflect that.
    setResultCallbackArgumentCaptor
        .getValue()
        .onResult(Mockito.mock(RemoteMediaClient.MediaChannelResult.class));
    verify(mockListener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
  }

  @Test
  public void repeatMode_changesOnStatusUpdates() {
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_OFF);
    when(mockMediaStatus.getQueueRepeatMode()).thenReturn(MediaStatus.REPEAT_MODE_REPEAT_SINGLE);
    remoteMediaClientListener.onStatusUpdated();
    verify(mockListener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ONE);
  }

  @Test
  public void setMediaItems_callsRemoteMediaClient() {
    List<MediaItem> mediaItems = new ArrayList<>();
    String sourceUri1 = "http://www.google.com/video1";
    String sourceUri2 = "http://www.google.com/video2";
    mediaItems.add(
        new MediaItem.Builder()
            .setSourceUri(sourceUri1)
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build());
    mediaItems.add(
        new MediaItem.Builder()
            .setSourceUri(sourceUri2)
            .setMimeType(MimeTypes.APPLICATION_MP4)
            .build());

    castPlayer.setMediaItems(mediaItems, /* startWindowIndex= */ 1, /* startPositionMs= */ 2000L);

    verify(mockRemoteMediaClient)
        .queueLoad(queueItemsArgumentCaptor.capture(), eq(1), anyInt(), eq(2000L), any());
    MediaQueueItem[] mediaQueueItems = queueItemsArgumentCaptor.getValue();
    assertThat(mediaQueueItems[0].getMedia().getContentId()).isEqualTo(sourceUri1);
    assertThat(mediaQueueItems[1].getMedia().getContentId()).isEqualTo(sourceUri2);
  }

  @Test
  public void setMediaItems_doNotReset_callsRemoteMediaClient() {
    MediaItem.Builder builder = new MediaItem.Builder();
    List<MediaItem> mediaItems = new ArrayList<>();
    String sourceUri1 = "http://www.google.com/video1";
    String sourceUri2 = "http://www.google.com/video2";
    mediaItems.add(builder.setSourceUri(sourceUri1).setMimeType(MimeTypes.APPLICATION_MPD).build());
    mediaItems.add(builder.setSourceUri(sourceUri2).setMimeType(MimeTypes.APPLICATION_MP4).build());
    int startWindowIndex = C.INDEX_UNSET;
    long startPositionMs = 2000L;

    castPlayer.setMediaItems(mediaItems, startWindowIndex, startPositionMs);

    verify(mockRemoteMediaClient)
        .queueLoad(queueItemsArgumentCaptor.capture(), eq(0), anyInt(), eq(0L), any());

    MediaQueueItem[] mediaQueueItems = queueItemsArgumentCaptor.getValue();
    assertThat(mediaQueueItems[0].getMedia().getContentId()).isEqualTo(sourceUri1);
    assertThat(mediaQueueItems[1].getMedia().getContentId()).isEqualTo(sourceUri2);
  }

  @Test
  public void addMediaItems_callsRemoteMediaClient() {
    MediaItem.Builder builder = new MediaItem.Builder();
    List<MediaItem> mediaItems = new ArrayList<>();
    String sourceUri1 = "http://www.google.com/video1";
    String sourceUri2 = "http://www.google.com/video2";
    mediaItems.add(builder.setSourceUri(sourceUri1).setMimeType(MimeTypes.APPLICATION_MPD).build());
    mediaItems.add(builder.setSourceUri(sourceUri2).setMimeType(MimeTypes.APPLICATION_MP4).build());

    castPlayer.addMediaItems(mediaItems);

    verify(mockRemoteMediaClient)
        .queueInsertItems(
            queueItemsArgumentCaptor.capture(), eq(MediaQueueItem.INVALID_ITEM_ID), any());

    MediaQueueItem[] mediaQueueItems = queueItemsArgumentCaptor.getValue();
    assertThat(mediaQueueItems[0].getMedia().getContentId()).isEqualTo(sourceUri1);
    assertThat(mediaQueueItems[1].getMedia().getContentId()).isEqualTo(sourceUri2);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void addMediaItems_insertAtIndex_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 2);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);
    String sourceUri = "http://www.google.com/video3";
    MediaItem anotherMediaItem =
        new MediaItem.Builder()
            .setSourceUri(sourceUri)
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build();

    // Add another on position 1
    int index = 1;
    castPlayer.addMediaItems(index, Collections.singletonList(anotherMediaItem));

    verify(mockRemoteMediaClient)
        .queueInsertItems(
            queueItemsArgumentCaptor.capture(),
            eq((int) mediaItems.get(index).playbackProperties.tag),
            any());

    MediaQueueItem[] mediaQueueItems = queueItemsArgumentCaptor.getValue();
    assertThat(mediaQueueItems[0].getMedia().getContentId()).isEqualTo(sourceUri);
  }

  @Test
  public void moveMediaItem_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItem(/* currentIndex= */ 1, /* newIndex= */ 2);

    verify(mockRemoteMediaClient)
        .queueReorderItems(new int[] {2}, /* insertBeforeItemId= */ 4, /* customData= */ null);
  }

  @Test
  public void moveMediaItem_toBegin_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItem(/* currentIndex= */ 1, /* newIndex= */ 0);

    verify(mockRemoteMediaClient)
        .queueReorderItems(new int[] {2}, /* insertBeforeItemId= */ 1, /* customData= */ null);
  }

  @Test
  public void moveMediaItem_toEnd_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItem(/* currentIndex= */ 1, /* newIndex= */ 4);

    verify(mockRemoteMediaClient)
        .queueReorderItems(
            new int[] {2},
            /* insertBeforeItemId= */ MediaQueueItem.INVALID_ITEM_ID,
            /* customData= */ null);
  }

  @Test
  public void moveMediaItems_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 3, /* newIndex= */ 1);

    verify(mockRemoteMediaClient)
        .queueReorderItems(
            new int[] {1, 2, 3}, /* insertBeforeItemId= */ 5, /* customData= */ null);
  }

  @Test
  public void moveMediaItems_toBeginning_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 4, /* newIndex= */ 0);

    verify(mockRemoteMediaClient)
        .queueReorderItems(
            new int[] {2, 3, 4}, /* insertBeforeItemId= */ 1, /* customData= */ null);
  }

  @Test
  public void moveMediaItems_toEnd_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 2, /* newIndex= */ 3);

    verify(mockRemoteMediaClient)
        .queueReorderItems(
            new int[] {1, 2},
            /* insertBeforeItemId= */ MediaQueueItem.INVALID_ITEM_ID,
            /* customData= */ null);
  }

  @Test
  public void moveMediaItems_noItems_doesNotCallRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 1, /* newIndex= */ 0);

    verify(mockRemoteMediaClient, never()).queueReorderItems(any(), anyInt(), any());
  }

  @Test
  public void moveMediaItems_noMove_doesNotCallRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 3, /* newIndex= */ 1);

    verify(mockRemoteMediaClient, never()).queueReorderItems(any(), anyInt(), any());
  }

  @Test
  public void removeMediaItems_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.removeMediaItems(/* fromIndex= */ 1, /* toIndex= */ 4);

    verify(mockRemoteMediaClient).queueRemoveItems(new int[] {2, 3, 4}, /* customData= */ null);
  }

  @Test
  public void clearMediaItems_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    fillTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.clearMediaItems();

    verify(mockRemoteMediaClient)
        .queueRemoveItems(new int[] {1, 2, 3, 4, 5}, /* customData= */ null);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void addMediaItems_fillsTimeline() {
    Timeline.Window window = new Timeline.Window();
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    fillTimeline(mediaItems, mediaQueueItemIds);

    Timeline currentTimeline = castPlayer.getCurrentTimeline();
    for (int i = 0; i < mediaItems.size(); i++) {
      assertThat(currentTimeline.getWindow(/* windowIndex= */ i, window).uid)
          .isEqualTo(mediaItems.get(i).playbackProperties.tag);
    }
  }

  private int[] createMediaQueueItemIds(int numberOfIds) {
    int[] mediaQueueItemIds = new int[numberOfIds];
    for (int i = 0; i < numberOfIds; i++) {
      mediaQueueItemIds[i] = i + 1;
    }
    return mediaQueueItemIds;
  }

  private List<MediaItem> createMediaItems(int[] mediaQueueItemIds) {
    MediaItem.Builder builder = new MediaItem.Builder();
    List<MediaItem> mediaItems = new ArrayList<>();
    for (int mediaQueueItemId : mediaQueueItemIds) {
      MediaItem mediaItem =
          builder
              .setSourceUri("http://www.google.com/video" + mediaQueueItemId)
              .setMimeType(MimeTypes.APPLICATION_MPD)
              .setTag(mediaQueueItemId)
              .build();
      mediaItems.add(mediaItem);
    }
    return mediaItems;
  }

  private void fillTimeline(List<MediaItem> mediaItems, int[] mediaQueueItemIds) {
    Assertions.checkState(mediaItems.size() == mediaQueueItemIds.length);
    List<MediaQueueItem> queueItems = new ArrayList<>();
    DefaultMediaItemConverter converter = new DefaultMediaItemConverter();
    for (MediaItem mediaItem : mediaItems) {
      queueItems.add(converter.toMediaQueueItem(mediaItem));
    }

    // Set up mocks to allow the player to update the timeline.
    when(mockMediaQueue.getItemIds()).thenReturn(mediaQueueItemIds);
    when(mockMediaStatus.getCurrentItemId()).thenReturn(1);
    when(mockMediaStatus.getMediaInfo()).thenReturn(mockMediaInfo);
    when(mockMediaInfo.getStreamType()).thenReturn(MediaInfo.STREAM_TYPE_NONE);
    when(mockMediaStatus.getQueueItems()).thenReturn(queueItems);

    castPlayer.addMediaItems(mediaItems);
    // Call listener to update the timeline of the player.
    remoteMediaClientListener.onQueueStatusUpdated();
  }
}
