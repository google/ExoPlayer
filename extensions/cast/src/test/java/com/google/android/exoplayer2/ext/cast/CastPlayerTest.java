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

import static com.google.android.exoplayer2.Player.COMMAND_ADJUST_DEVICE_VOLUME;
import static com.google.android.exoplayer2.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static com.google.android.exoplayer2.Player.COMMAND_GET_AUDIO_ATTRIBUTES;
import static com.google.android.exoplayer2.Player.COMMAND_GET_CURRENT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_GET_DEVICE_VOLUME;
import static com.google.android.exoplayer2.Player.COMMAND_GET_MEDIA_ITEMS;
import static com.google.android.exoplayer2.Player.COMMAND_GET_MEDIA_ITEMS_METADATA;
import static com.google.android.exoplayer2.Player.COMMAND_GET_TEXT;
import static com.google.android.exoplayer2.Player.COMMAND_GET_VOLUME;
import static com.google.android.exoplayer2.Player.COMMAND_PLAY_PAUSE;
import static com.google.android.exoplayer2.Player.COMMAND_PREPARE_STOP;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_DEFAULT_POSITION;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SET_DEVICE_VOLUME;
import static com.google.android.exoplayer2.Player.COMMAND_SET_REPEAT_MODE;
import static com.google.android.exoplayer2.Player.COMMAND_SET_SHUFFLE_MODE;
import static com.google.android.exoplayer2.Player.COMMAND_SET_SPEED_AND_PITCH;
import static com.google.android.exoplayer2.Player.COMMAND_SET_VIDEO_SURFACE;
import static com.google.android.exoplayer2.Player.COMMAND_SET_VOLUME;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_REMOVE;
import static com.google.android.exoplayer2.Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.net.Uri;
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
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;

/** Tests for {@link CastPlayer}. */
@RunWith(AndroidJUnit4.class)
public class CastPlayerTest {

  private CastPlayer castPlayer;
  private RemoteMediaClient.Callback remoteMediaClientCallback;

  @Mock private RemoteMediaClient mockRemoteMediaClient;
  @Mock private MediaStatus mockMediaStatus;
  @Mock private MediaQueue mockMediaQueue;
  @Mock private CastContext mockCastContext;
  @Mock private SessionManager mockSessionManager;
  @Mock private CastSession mockCastSession;
  @Mock private Player.Listener mockListener;
  @Mock private PendingResult<RemoteMediaClient.MediaChannelResult> mockPendingResult;

  @Captor
  private ArgumentCaptor<ResultCallback<RemoteMediaClient.MediaChannelResult>>
      setResultCallbackArgumentCaptor;

  @Captor private ArgumentCaptor<RemoteMediaClient.Callback> callbackArgumentCaptor;
  @Captor private ArgumentCaptor<MediaQueueItem[]> queueItemsArgumentCaptor;
  @Captor private ArgumentCaptor<MediaItem> mediaItemCaptor;

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
    verify(mockRemoteMediaClient).registerCallback(callbackArgumentCaptor.capture());
    remoteMediaClientCallback = callbackArgumentCaptor.getValue();
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
    remoteMediaClientCallback.onStatusUpdated();
    verifyNoMoreInteractions(mockListener);

    // Upon result, the remoteMediaClient has updated its state according to the play() call.
    when(mockRemoteMediaClient.isPaused()).thenReturn(false);
    setResultCallbackArgumentCaptor
        .getValue()
        .onResult(mock(RemoteMediaClient.MediaChannelResult.class));
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
        .onResult(mock(RemoteMediaClient.MediaChannelResult.class));
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
    remoteMediaClientCallback.onStatusUpdated();
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
    remoteMediaClientCallback.onStatusUpdated();
    verifyNoMoreInteractions(mockListener);

    // Upon result, the mediaStatus now exposes the new repeat mode.
    when(mockMediaStatus.getQueueRepeatMode()).thenReturn(MediaStatus.REPEAT_MODE_REPEAT_SINGLE);
    setResultCallbackArgumentCaptor
        .getValue()
        .onResult(mock(RemoteMediaClient.MediaChannelResult.class));
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
    remoteMediaClientCallback.onStatusUpdated();
    verifyNoMoreInteractions(mockListener);

    // Upon result, the repeat mode is ALL. The state should reflect that.
    setResultCallbackArgumentCaptor
        .getValue()
        .onResult(mock(RemoteMediaClient.MediaChannelResult.class));
    verify(mockListener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
  }

  @Test
  public void repeatMode_changesOnStatusUpdates() {
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_OFF);
    when(mockMediaStatus.getQueueRepeatMode()).thenReturn(MediaStatus.REPEAT_MODE_REPEAT_SINGLE);
    remoteMediaClientCallback.onStatusUpdated();
    verify(mockListener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ONE);
  }

  @Test
  public void setMediaItems_callsRemoteMediaClient() {
    List<MediaItem> mediaItems = new ArrayList<>();
    String uri1 = "http://www.google.com/video1";
    String uri2 = "http://www.google.com/video2";
    mediaItems.add(
        new MediaItem.Builder().setUri(uri1).setMimeType(MimeTypes.APPLICATION_MPD).build());
    mediaItems.add(
        new MediaItem.Builder().setUri(uri2).setMimeType(MimeTypes.APPLICATION_MP4).build());

    castPlayer.setMediaItems(mediaItems, /* startWindowIndex= */ 1, /* startPositionMs= */ 2000L);

    verify(mockRemoteMediaClient)
        .queueLoad(queueItemsArgumentCaptor.capture(), eq(1), anyInt(), eq(2000L), any());
    MediaQueueItem[] mediaQueueItems = queueItemsArgumentCaptor.getValue();
    assertThat(mediaQueueItems[0].getMedia().getContentId()).isEqualTo(uri1);
    assertThat(mediaQueueItems[1].getMedia().getContentId()).isEqualTo(uri2);
  }

  @Test
  public void setMediaItems_doNotReset_callsRemoteMediaClient() {
    MediaItem.Builder builder = new MediaItem.Builder();
    List<MediaItem> mediaItems = new ArrayList<>();
    String uri1 = "http://www.google.com/video1";
    String uri2 = "http://www.google.com/video2";
    mediaItems.add(builder.setUri(uri1).setMimeType(MimeTypes.APPLICATION_MPD).build());
    mediaItems.add(builder.setUri(uri2).setMimeType(MimeTypes.APPLICATION_MP4).build());
    int startWindowIndex = C.INDEX_UNSET;
    long startPositionMs = 2000L;

    castPlayer.setMediaItems(mediaItems, startWindowIndex, startPositionMs);

    verify(mockRemoteMediaClient)
        .queueLoad(queueItemsArgumentCaptor.capture(), eq(0), anyInt(), eq(0L), any());

    MediaQueueItem[] mediaQueueItems = queueItemsArgumentCaptor.getValue();
    assertThat(mediaQueueItems[0].getMedia().getContentId()).isEqualTo(uri1);
    assertThat(mediaQueueItems[1].getMedia().getContentId()).isEqualTo(uri2);
  }

  @SuppressWarnings("deprecation") // Verifies deprecated callback being called correctly.
  @Test
  public void setMediaItems_replaceExistingPlaylist_notifiesMediaItemTransition() {
    List<MediaItem> firstPlaylist = new ArrayList<>();
    String uri1 = "http://www.google.com/video1";
    String uri2 = "http://www.google.com/video2";
    firstPlaylist.add(
        new MediaItem.Builder().setUri(uri1).setMimeType(MimeTypes.APPLICATION_MPD).build());
    firstPlaylist.add(
        new MediaItem.Builder().setUri(uri2).setMimeType(MimeTypes.APPLICATION_MP4).build());
    ImmutableList<MediaItem> secondPlaylist =
        ImmutableList.of(
            new MediaItem.Builder()
                .setUri(Uri.EMPTY)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .build());

    castPlayer.setMediaItems(
        firstPlaylist, /* startWindowIndex= */ 1, /* startPositionMs= */ 2000L);
    updateTimeLine(
        firstPlaylist, /* mediaQueueItemIds= */ new int[] {1, 2}, /* currentItemId= */ 2);
    // Replacing existing playlist.
    castPlayer.setMediaItems(
        secondPlaylist, /* startWindowIndex= */ 0, /* startPositionMs= */ 1000L);
    updateTimeLine(secondPlaylist, /* mediaQueueItemIds= */ new int[] {3}, /* currentItemId= */ 3);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener, times(2))
        .onMediaItemTransition(
            mediaItemCaptor.capture(), eq(MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder.verify(mockListener, never()).onMediaItemTransition(any(), anyInt());
    assertThat(mediaItemCaptor.getAllValues().get(1).playbackProperties.tag).isEqualTo(3);
  }

  @SuppressWarnings("deprecation") // Verifies deprecated callback being called correctly.
  @Test
  public void setMediaItems_replaceExistingPlaylist_notifiesPositionDiscontinuity() {
    List<MediaItem> firstPlaylist = new ArrayList<>();
    String uri1 = "http://www.google.com/video1";
    String uri2 = "http://www.google.com/video2";
    firstPlaylist.add(
        new MediaItem.Builder().setUri(uri1).setMimeType(MimeTypes.APPLICATION_MPD).build());
    firstPlaylist.add(
        new MediaItem.Builder().setUri(uri2).setMimeType(MimeTypes.APPLICATION_MP4).build());
    ImmutableList<MediaItem> secondPlaylist =
        ImmutableList.of(
            new MediaItem.Builder()
                .setUri(Uri.EMPTY)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .build());

    castPlayer.setMediaItems(
        firstPlaylist, /* startWindowIndex= */ 1, /* startPositionMs= */ 2000L);
    updateTimeLine(
        firstPlaylist,
        /* mediaQueueItemIds= */ new int[] {1, 2},
        /* currentItemId= */ 2,
        /* streamTypes= */ new int[] {
          MediaInfo.STREAM_TYPE_BUFFERED, MediaInfo.STREAM_TYPE_BUFFERED
        },
        /* durationsMs= */ new long[] {20_000, 20_000},
        /* positionMs= */ 2000L);
    // Replacing existing playlist.
    castPlayer.setMediaItems(
        secondPlaylist, /* startWindowIndex= */ 0, /* startPositionMs= */ 1000L);
    updateTimeLine(
        secondPlaylist,
        /* mediaQueueItemIds= */ new int[] {3},
        /* currentItemId= */ 3,
        /* streamTypes= */ new int[] {MediaInfo.STREAM_TYPE_BUFFERED},
        /* durationsMs= */ new long[] {20_000},
        /* positionMs= */ 1000L);

    Player.PositionInfo oldPosition =
        new Player.PositionInfo(
            /* windowUid= */ 2,
            /* windowIndex= */ 1,
            /* periodUid= */ 2,
            /* periodIndex= */ 1,
            /* positionMs= */ 2000,
            /* contentPositionMs= */ 2000,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    Player.PositionInfo newPosition =
        new Player.PositionInfo(
            /* windowUid= */ 3,
            /* windowIndex= */ 0,
            /* periodUid= */ 3,
            /* periodIndex= */ 0,
            /* positionMs= */ 1000,
            /* contentPositionMs= */ 1000,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder.verify(mockListener).onPositionDiscontinuity(eq(DISCONTINUITY_REASON_REMOVE));
    inOrder
        .verify(mockListener)
        .onPositionDiscontinuity(eq(oldPosition), eq(newPosition), eq(DISCONTINUITY_REASON_REMOVE));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
  }

  @Test
  public void addMediaItems_callsRemoteMediaClient() {
    MediaItem.Builder builder = new MediaItem.Builder();
    List<MediaItem> mediaItems = new ArrayList<>();
    String uri1 = "http://www.google.com/video1";
    String uri2 = "http://www.google.com/video2";
    mediaItems.add(builder.setUri(uri1).setMimeType(MimeTypes.APPLICATION_MPD).build());
    mediaItems.add(builder.setUri(uri2).setMimeType(MimeTypes.APPLICATION_MP4).build());

    castPlayer.addMediaItems(mediaItems);

    verify(mockRemoteMediaClient)
        .queueInsertItems(
            queueItemsArgumentCaptor.capture(), eq(MediaQueueItem.INVALID_ITEM_ID), any());

    MediaQueueItem[] mediaQueueItems = queueItemsArgumentCaptor.getValue();
    assertThat(mediaQueueItems[0].getMedia().getContentId()).isEqualTo(uri1);
    assertThat(mediaQueueItems[1].getMedia().getContentId()).isEqualTo(uri2);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void addMediaItems_insertAtIndex_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 2);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);
    String uri = "http://www.google.com/video3";
    MediaItem anotherMediaItem =
        new MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_MPD).build();

    // Add another on position 1
    int index = 1;
    castPlayer.addMediaItems(index, Collections.singletonList(anotherMediaItem));

    verify(mockRemoteMediaClient)
        .queueInsertItems(
            queueItemsArgumentCaptor.capture(),
            eq((int) mediaItems.get(index).playbackProperties.tag),
            any());

    MediaQueueItem[] mediaQueueItems = queueItemsArgumentCaptor.getValue();
    assertThat(mediaQueueItems[0].getMedia().getContentId()).isEqualTo(uri);
  }

  @Test
  public void moveMediaItem_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItem(/* currentIndex= */ 1, /* newIndex= */ 2);

    verify(mockRemoteMediaClient)
        .queueReorderItems(new int[] {2}, /* insertBeforeItemId= */ 4, /* customData= */ null);
  }

  @Test
  public void moveMediaItem_toBegin_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItem(/* currentIndex= */ 1, /* newIndex= */ 0);

    verify(mockRemoteMediaClient)
        .queueReorderItems(new int[] {2}, /* insertBeforeItemId= */ 1, /* customData= */ null);
  }

  @Test
  public void moveMediaItem_toEnd_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

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
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 3, /* newIndex= */ 1);

    verify(mockRemoteMediaClient)
        .queueReorderItems(
            new int[] {1, 2, 3}, /* insertBeforeItemId= */ 5, /* customData= */ null);
  }

  @Test
  public void moveMediaItems_toBeginning_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 4, /* newIndex= */ 0);

    verify(mockRemoteMediaClient)
        .queueReorderItems(
            new int[] {2, 3, 4}, /* insertBeforeItemId= */ 1, /* customData= */ null);
  }

  @Test
  public void moveMediaItems_toEnd_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

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
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 1, /* newIndex= */ 0);

    verify(mockRemoteMediaClient, never()).queueReorderItems(any(), anyInt(), any());
  }

  @Test
  public void moveMediaItems_noMove_doesNotCallRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 3, /* newIndex= */ 1);

    verify(mockRemoteMediaClient, never()).queueReorderItems(any(), anyInt(), any());
  }

  @Test
  public void removeMediaItems_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

    castPlayer.removeMediaItems(/* fromIndex= */ 1, /* toIndex= */ 4);

    verify(mockRemoteMediaClient).queueRemoveItems(new int[] {2, 3, 4}, /* customData= */ null);
  }

  @Test
  public void clearMediaItems_callsRemoteMediaClient() {
    int[] mediaQueueItemIds = createMediaQueueItemIds(/* numberOfIds= */ 5);
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);
    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

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

    addMediaItemsAndUpdateTimeline(mediaItems, mediaQueueItemIds);

    Timeline currentTimeline = castPlayer.getCurrentTimeline();
    for (int i = 0; i < mediaItems.size(); i++) {
      assertThat(currentTimeline.getWindow(/* windowIndex= */ i, window).uid)
          .isEqualTo(mediaItems.get(i).playbackProperties.tag);
    }
  }

  @Test
  public void addMediaItems_notifiesMediaItemTransition() {
    MediaItem mediaItem = createMediaItem(/* mediaQueueItemId= */ 1);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem);
    int[] mediaQueueItemIds = new int[] {1};

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener)
        .onMediaItemTransition(
            mediaItemCaptor.capture(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder.verify(mockListener, never()).onMediaItemTransition(any(), anyInt());
    assertThat(mediaItemCaptor.getValue().playbackProperties.tag)
        .isEqualTo(mediaItem.playbackProperties.tag);
  }

  @Test
  public void clearMediaItems_notifiesMediaItemTransition() {
    int[] mediaQueueItemIds = new int[] {1, 2};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    castPlayer.clearMediaItems();
    updateTimeLine(
        /* mediaItems= */ ImmutableList.of(),
        /* mediaQueueItemIds= */ new int[0],
        /* currentItemId= */ C.INDEX_UNSET);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockListener)
        .onMediaItemTransition(
            /* mediaItem= */ null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    inOrder.verify(mockListener, never()).onMediaItemTransition(any(), anyInt());
  }

  @Test
  @SuppressWarnings("deprecation") // Mocks deprecated method used by the CastPlayer.
  public void clearMediaItems_notifiesPositionDiscontinuity() {
    int[] mediaQueueItemIds = new int[] {1, 2};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(
        mediaItems,
        mediaQueueItemIds,
        /* currentItemId= */ 1,
        new int[] {MediaInfo.STREAM_TYPE_BUFFERED, MediaInfo.STREAM_TYPE_BUFFERED},
        /* durationsMs= */ new long[] {20_000L, 30_000L},
        /* positionMs= */ 1234);
    castPlayer.clearMediaItems();
    updateTimeLine(
        /* mediaItems= */ ImmutableList.of(),
        /* mediaQueueItemIds= */ new int[0],
        /* currentItemId= */ C.INDEX_UNSET,
        new int[] {MediaInfo.STREAM_TYPE_BUFFERED},
        /* durationsMs= */ new long[] {20_000L},
        /* positionMs= */ 0);

    Player.PositionInfo oldPosition =
        new Player.PositionInfo(
            /* windowUid= */ 1,
            /* windowIndex= */ 0,
            /* periodUid= */ 1,
            /* periodIndex= */ 0,
            /* positionMs= */ 1234,
            /* contentPositionMs= */ 1234,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    Player.PositionInfo newPosition =
        new Player.PositionInfo(
            /* windowUid= */ null,
            /* windowIndex= */ 0,
            /* periodUid= */ null,
            /* periodIndex= */ 0,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder.verify(mockListener).onPositionDiscontinuity(eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder
        .verify(mockListener)
        .onPositionDiscontinuity(
            eq(oldPosition), eq(newPosition), eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
  }

  @Test
  public void removeCurrentMediaItem_notifiesMediaItemTransition() {
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem1, mediaItem2);
    int[] mediaQueueItemIds = new int[] {1, 2};

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    castPlayer.removeMediaItem(/* index= */ 0);
    // Update with the new timeline after removal.
    updateTimeLine(
        ImmutableList.of(mediaItem2),
        /* mediaQueueItemIds= */ new int[] {2},
        /* currentItemId= */ 2);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener, times(2))
        .onMediaItemTransition(
            mediaItemCaptor.capture(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder.verify(mockListener, never()).onMediaItemTransition(any(), anyInt());
    assertThat(mediaItemCaptor.getAllValues().get(0).playbackProperties.tag)
        .isEqualTo(mediaItem1.playbackProperties.tag);
    assertThat(mediaItemCaptor.getAllValues().get(1).playbackProperties.tag)
        .isEqualTo(mediaItem2.playbackProperties.tag);
  }

  @Test
  @SuppressWarnings("deprecation") // Mocks deprecated method used by the CastPlayer.
  public void removeCurrentMediaItem_notifiesPositionDiscontinuity() {
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem1, mediaItem2);
    int[] mediaQueueItemIds = new int[] {1, 2};

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(
        mediaItems,
        mediaQueueItemIds,
        /* currentItemId= */ 1,
        new int[] {MediaInfo.STREAM_TYPE_BUFFERED, MediaInfo.STREAM_TYPE_BUFFERED},
        /* durationsMs= */ new long[] {20_000L, 30_000L},
        /* positionMs= */ 1234);
    castPlayer.removeMediaItem(/* index= */ 0);
    // Update with the new timeline after removal.
    updateTimeLine(
        ImmutableList.of(mediaItem2),
        /* mediaQueueItemIds= */ new int[] {2},
        /* currentItemId= */ 2,
        new int[] {MediaInfo.STREAM_TYPE_BUFFERED},
        /* durationsMs= */ new long[] {20_000L},
        /* positionMs= */ 0);

    Player.PositionInfo oldPosition =
        new Player.PositionInfo(
            /* windowUid= */ 1,
            /* windowIndex= */ 0,
            /* periodUid= */ 1,
            /* periodIndex= */ 0,
            /* positionMs= */ 1234,
            /* contentPositionMs= */ 1234,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    Player.PositionInfo newPosition =
        new Player.PositionInfo(
            /* windowUid= */ 2,
            /* windowIndex= */ 0,
            /* periodUid= */ 2,
            /* periodIndex= */ 0,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder.verify(mockListener).onPositionDiscontinuity(eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder
        .verify(mockListener)
        .onPositionDiscontinuity(
            eq(oldPosition), eq(newPosition), eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
  }

  @Test
  public void removeCurrentMediaItem_byRemoteClient_notifiesMediaItemTransition() {
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem1, mediaItem2);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, new int[] {1, 2}, /* currentItemId= */ 1);
    // Update with the new timeline after removal on the device.
    updateTimeLine(
        ImmutableList.of(mediaItem2),
        /* mediaQueueItemIds= */ new int[] {2},
        /* currentItemId= */ 2);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener, times(2))
        .onMediaItemTransition(
            mediaItemCaptor.capture(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder.verify(mockListener, never()).onMediaItemTransition(any(), anyInt());
    List<MediaItem> capturedMediaItems = mediaItemCaptor.getAllValues();
    assertThat(capturedMediaItems.get(0).playbackProperties.tag)
        .isEqualTo(mediaItem1.playbackProperties.tag);
    assertThat(capturedMediaItems.get(1).playbackProperties.tag)
        .isEqualTo(mediaItem2.playbackProperties.tag);
  }

  @Test
  @SuppressWarnings("deprecation") // Mocks deprecated method used by the CastPlayer.
  public void removeCurrentMediaItem_byRemoteClient_notifiesPositionDiscontinuity() {
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem1, mediaItem2);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(
        mediaItems,
        new int[] {1, 2},
        /* currentItemId= */ 1,
        new int[] {MediaInfo.STREAM_TYPE_BUFFERED, MediaInfo.STREAM_TYPE_BUFFERED},
        /* durationsMs= */ new long[] {20_000L, 30_000L},
        /* positionMs= */ 1234);
    // Update with the new timeline after removal on the device.
    updateTimeLine(
        ImmutableList.of(mediaItem2),
        /* mediaQueueItemIds= */ new int[] {2},
        /* currentItemId= */ 2,
        new int[] {MediaInfo.STREAM_TYPE_BUFFERED},
        /* durationsMs= */ new long[] {30_000L},
        /* positionMs= */ 0);

    Player.PositionInfo oldPosition =
        new Player.PositionInfo(
            /* windowUid= */ 1,
            /* windowIndex= */ 0,
            /* periodUid= */ 1,
            /* periodIndex= */ 0,
            /* positionMs= */ 0, // position at which we receive the timeline change
            /* contentPositionMs= */ 0, // position at which we receive the timeline change
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    Player.PositionInfo newPosition =
        new Player.PositionInfo(
            /* windowUid= */ 2,
            /* windowIndex= */ 0,
            /* periodUid= */ 2,
            /* periodIndex= */ 0,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder.verify(mockListener).onPositionDiscontinuity(eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder
        .verify(mockListener)
        .onPositionDiscontinuity(
            eq(oldPosition), eq(newPosition), eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
  }

  @Test
  public void removeNonCurrentMediaItem_doesNotNotifyMediaItemTransition() {
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem1, mediaItem2);
    int[] mediaQueueItemIds = new int[] {1, 2};

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    castPlayer.removeMediaItem(/* index= */ 1);
    updateTimeLine(
        ImmutableList.of(mediaItem1),
        /* mediaQueueItemIds= */ new int[] {1},
        /* currentItemId= */ 1);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder.verify(mockListener, never()).onMediaItemTransition(any(), anyInt());
  }

  @Test
  @SuppressWarnings("deprecation") // Mocks deprecated method used by the CastPlayer.
  public void removeNonCurrentMediaItem_doesNotNotifyPositionDiscontinuity() {
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem1, mediaItem2);
    int[] mediaQueueItemIds = new int[] {1, 2};

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    castPlayer.removeMediaItem(/* index= */ 1);
    updateTimeLine(
        ImmutableList.of(mediaItem1),
        /* mediaQueueItemIds= */ new int[] {1},
        /* currentItemId= */ 1);

    verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
  }

  @Test
  public void seekTo_otherWindow_notifiesMediaItemTransition() {
    when(mockRemoteMediaClient.queueJumpToItem(anyInt(), anyLong(), eq(null)))
        .thenReturn(mockPendingResult);
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem1, mediaItem2);
    int[] mediaQueueItemIds = new int[] {1, 2};

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    castPlayer.seekTo(/* windowIndex= */ 1, /* positionMs= */ 1234);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockListener)
        .onMediaItemTransition(
            mediaItemCaptor.capture(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    assertThat(mediaItemCaptor.getValue().playbackProperties.tag)
        .isEqualTo(mediaItem2.playbackProperties.tag);
  }

  @Test
  @SuppressWarnings("deprecation") // Mocks deprecated method used by the CastPlayer.
  public void seekTo_otherWindow_notifiesPositionDiscontinuity() {
    when(mockRemoteMediaClient.queueJumpToItem(anyInt(), anyLong(), eq(null)))
        .thenReturn(mockPendingResult);
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem1, mediaItem2);
    int[] mediaQueueItemIds = new int[] {1, 2};

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    castPlayer.seekTo(/* windowIndex= */ 1, /* positionMs= */ 1234);

    Player.PositionInfo oldPosition =
        new Player.PositionInfo(
            /* windowUid= */ 1,
            /* windowIndex= */ 0,
            /* periodUid= */ 1,
            /* periodIndex= */ 0,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    Player.PositionInfo newPosition =
        new Player.PositionInfo(
            /* windowUid= */ 2,
            /* windowIndex= */ 1,
            /* periodUid= */ 2,
            /* periodIndex= */ 1,
            /* positionMs= */ 1234,
            /* contentPositionMs= */ 1234,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder.verify(mockListener).onPositionDiscontinuity(eq(Player.DISCONTINUITY_REASON_SEEK));
    inOrder
        .verify(mockListener)
        .onPositionDiscontinuity(
            eq(oldPosition), eq(newPosition), eq(Player.DISCONTINUITY_REASON_SEEK));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
  }

  @Test
  @SuppressWarnings("deprecation") // Mocks deprecated method used by the CastPlayer.
  public void seekTo_sameWindow_doesNotNotifyMediaItemTransition() {
    when(mockRemoteMediaClient.seek(anyLong())).thenReturn(mockPendingResult);
    int[] mediaQueueItemIds = new int[] {1, 2};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    castPlayer.seekTo(/* windowIndex= */ 0, /* positionMs= */ 1234);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder.verify(mockListener, never()).onMediaItemTransition(any(), anyInt());
  }

  @Test
  @SuppressWarnings("deprecation") // Mocks deprecated method used by the CastPlayer.
  public void seekTo_sameWindow_notifiesPositionDiscontinuity() {
    when(mockRemoteMediaClient.seek(anyLong())).thenReturn(mockPendingResult);
    int[] mediaQueueItemIds = new int[] {1, 2};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    castPlayer.seekTo(/* windowIndex= */ 0, /* positionMs= */ 1234);

    Player.PositionInfo oldPosition =
        new Player.PositionInfo(
            /* windowUid= */ 1,
            /* windowIndex= */ 0,
            /* periodUid= */ 1,
            /* periodIndex= */ 0,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    Player.PositionInfo newPosition =
        new Player.PositionInfo(
            /* windowUid= */ 1,
            /* windowIndex= */ 0,
            /* periodUid= */ 1,
            /* periodIndex= */ 0,
            /* positionMs= */ 1234,
            /* contentPositionMs= */ 1234,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder.verify(mockListener).onPositionDiscontinuity(eq(Player.DISCONTINUITY_REASON_SEEK));
    inOrder
        .verify(mockListener)
        .onPositionDiscontinuity(
            eq(oldPosition), eq(newPosition), eq(Player.DISCONTINUITY_REASON_SEEK));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
  }

  @Test
  public void autoTransition_notifiesMediaItemTransition() {
    int[] mediaQueueItemIds = new int[] {1, 2};
    // When the remote Cast player transitions to an item that wasn't played before, the media state
    // delivers the duration for that media item which updates the timeline accordingly.
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 2);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockListener)
        .onMediaItemTransition(
            mediaItemCaptor.capture(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO));
    inOrder.verify(mockListener, never()).onMediaItemTransition(any(), anyInt());
    assertThat(mediaItemCaptor.getValue().playbackProperties.tag).isEqualTo(2);
  }

  @Test
  @SuppressWarnings("deprecation") // Mocks deprecated method used by the CastPlayer.
  public void autoTransition_notifiesPositionDiscontinuity() {
    int[] mediaQueueItemIds = new int[] {1, 2};
    int[] streamTypes = {MediaInfo.STREAM_TYPE_BUFFERED, MediaInfo.STREAM_TYPE_BUFFERED};
    long[] durationsFirstMs = {12500, C.TIME_UNSET};
    // When the remote Cast player transitions to an item that wasn't played before, the media state
    // delivers the duration for that media item which updates the timeline accordingly.
    long[] durationsSecondMs = {12500, 22000};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(
        mediaItems,
        mediaQueueItemIds,
        /* currentItemId= */ 1,
        /* streamTypes= */ streamTypes,
        /* durationsMs= */ durationsFirstMs,
        /* positionMs= */ C.TIME_UNSET);
    updateTimeLine(
        mediaItems,
        mediaQueueItemIds,
        /* currentItemId= */ 2,
        /* streamTypes= */ streamTypes,
        /* durationsMs= */ durationsSecondMs,
        /* positionMs= */ C.TIME_UNSET);

    Player.PositionInfo oldPosition =
        new Player.PositionInfo(
            /* windowUid= */ 1,
            /* windowIndex= */ 0,
            /* periodUid= */ 1,
            /* periodIndex= */ 0,
            /* positionMs= */ 12500,
            /* contentPositionMs= */ 12500,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    Player.PositionInfo newPosition =
        new Player.PositionInfo(
            /* windowUid= */ 2,
            /* windowIndex= */ 1,
            /* periodUid= */ 2,
            /* periodIndex= */ 1,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener)
        .onPositionDiscontinuity(eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(mockListener)
        .onPositionDiscontinuity(
            eq(oldPosition), eq(newPosition), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
  }

  @Test
  public void isCommandAvailable_isTrueForAvailableCommands() {
    int[] mediaQueueItemIds = new int[] {1, 2};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);

    assertThat(castPlayer.isCommandAvailable(COMMAND_PLAY_PAUSE)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_PREPARE_STOP)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SEEK_TO_DEFAULT_POSITION)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SEEK_TO_MEDIA_ITEM)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SET_SPEED_AND_PITCH)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SET_SHUFFLE_MODE)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SET_REPEAT_MODE)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_GET_MEDIA_ITEMS)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_GET_MEDIA_ITEMS_METADATA)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)).isTrue();
    assertThat(castPlayer.isCommandAvailable(COMMAND_GET_AUDIO_ATTRIBUTES)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_GET_VOLUME)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_GET_DEVICE_VOLUME)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SET_VOLUME)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SET_DEVICE_VOLUME)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_SET_VIDEO_SURFACE)).isFalse();
    assertThat(castPlayer.isCommandAvailable(COMMAND_GET_TEXT)).isFalse();
  }

  @Test
  public void isCommandAvailable_duringUnseekableItem_isFalseForSeekInCurrent() {
    MediaItem mediaItem = createMediaItem(/* mediaQueueItemId= */ 1);
    List<MediaItem> mediaItems = ImmutableList.of(mediaItem);
    int[] mediaQueueItemIds = new int[] {1};
    int[] streamTypes = new int[] {MediaInfo.STREAM_TYPE_LIVE};
    long[] durationsMs = new long[] {C.TIME_UNSET};

    castPlayer.addMediaItem(mediaItem);
    updateTimeLine(
        mediaItems,
        mediaQueueItemIds,
        /* currentItemId= */ 1,
        streamTypes,
        durationsMs,
        /* positionMs= */ C.TIME_UNSET);

    assertThat(castPlayer.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)).isFalse();
  }

  @Test
  public void seekTo_nextWindow_notifiesAvailableCommandsChanged() {
    when(mockRemoteMediaClient.queueJumpToItem(anyInt(), anyLong(), eq(null)))
        .thenReturn(mockPendingResult);
    Player.Commands commandsWithSeekInCurrentAndToNext =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
    Player.Commands commandsWithSeekInCurrentAndToPrevious =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    Player.Commands commandsWithSeekAnywhere =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    int[] mediaQueueItemIds = new int[] {1, 2, 3, 4};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToNext);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.seekTo(/* windowIndex= */ 1, /* positionMs= */ 0);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekAnywhere);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    castPlayer.seekTo(/* windowIndex= */ 2, /* positionMs= */ 0);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    castPlayer.seekTo(/* windowIndex= */ 3, /* positionMs= */ 0);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToPrevious);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());
  }

  @Test
  public void seekTo_previousWindow_notifiesAvailableCommandsChanged() {
    when(mockRemoteMediaClient.queueJumpToItem(anyInt(), anyLong(), eq(null)))
        .thenReturn(mockPendingResult);
    Player.Commands commandsWithSeekInCurrentAndToNext =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
    Player.Commands commandsWithSeekInCurrentAndToPrevious =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    Player.Commands commandsWithSeekAnywhere =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    int[] mediaQueueItemIds = new int[] {1, 2, 3, 4};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 4);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToPrevious);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.seekTo(/* windowIndex= */ 2, /* positionMs= */ 0);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekAnywhere);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    castPlayer.seekTo(/* windowIndex= */ 1, /* positionMs= */ 0);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    castPlayer.seekTo(/* windowIndex= */ 0, /* positionMs= */ 0);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToNext);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());
  }

  @Test
  @SuppressWarnings("deprecation") // Mocks deprecated method used by the CastPlayer.
  public void seekTo_sameWindow_doesNotNotifyAvailableCommandsChanged() {
    when(mockRemoteMediaClient.seek(anyLong())).thenReturn(mockPendingResult);
    Player.Commands commandsWithSeekInCurrent =
        createWithPermanentCommands(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    int[] mediaQueueItemIds = new int[] {1};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrent);

    castPlayer.seekTo(/* windowIndex= */ 0, /* positionMs= */ 200);
    castPlayer.seekTo(/* windowIndex= */ 0, /* positionMs= */ 100);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());
  }

  @Test
  public void addMediaItem_atTheEnd_notifiesAvailableCommandsChanged() {
    Player.Commands commandsWithSeekInCurrent =
        createWithPermanentCommands(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    Player.Commands commandsWithSeekInCurrentAndToNext =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    MediaItem mediaItem3 = createMediaItem(/* mediaQueueItemId= */ 3);

    castPlayer.addMediaItem(mediaItem1);
    updateTimeLine(
        ImmutableList.of(mediaItem1),
        /* mediaQueueItemIds= */ new int[] {1},
        /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrent);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.addMediaItem(mediaItem2);
    updateTimeLine(
        ImmutableList.of(mediaItem1, mediaItem2),
        /* mediaQueueItemIds= */ new int[] {1, 2},
        /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToNext);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    castPlayer.addMediaItem(mediaItem3);
    updateTimeLine(
        ImmutableList.of(mediaItem1, mediaItem2, mediaItem3),
        /* mediaQueueItemIds= */ new int[] {1, 2, 3},
        /* currentItemId= */ 1);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());
  }

  @Test
  public void addMediaItem_atTheStart_notifiesAvailableCommandsChanged() {
    Player.Commands commandsWithSeekInCurrent =
        createWithPermanentCommands(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    Player.Commands commandsWithSeekInCurrentAndToPrevious =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    MediaItem mediaItem3 = createMediaItem(/* mediaQueueItemId= */ 3);

    castPlayer.addMediaItem(mediaItem1);
    updateTimeLine(
        ImmutableList.of(mediaItem1),
        /* mediaQueueItemIds= */ new int[] {1},
        /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrent);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.addMediaItem(/* index= */ 0, mediaItem2);
    updateTimeLine(
        ImmutableList.of(mediaItem2, mediaItem1),
        /* mediaQueueItemIds= */ new int[] {2, 1},
        /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToPrevious);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    castPlayer.addMediaItem(/* index= */ 0, mediaItem3);
    updateTimeLine(
        ImmutableList.of(mediaItem3, mediaItem2, mediaItem1),
        /* mediaQueueItemIds= */ new int[] {3, 2, 1},
        /* currentItemId= */ 1);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());
  }

  @Test
  public void removeMediaItem_atTheEnd_notifiesAvailableCommandsChanged() {
    Player.Commands commandsWithoutSeek = createWithPermanentCommands();
    Player.Commands commandsWithSeekInCurrent =
        createWithPermanentCommands(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    Player.Commands commandsWithSeekInCurrentAndToNext =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    MediaItem mediaItem3 = createMediaItem(/* mediaQueueItemId= */ 3);

    castPlayer.addMediaItems(ImmutableList.of(mediaItem1, mediaItem2, mediaItem3));
    updateTimeLine(
        ImmutableList.of(mediaItem1, mediaItem2, mediaItem3),
        /* mediaQueueItemIds= */ new int[] {1, 2, 3},
        /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToNext);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.removeMediaItem(/* index= */ 2);
    updateTimeLine(
        ImmutableList.of(mediaItem1, mediaItem2),
        /* mediaQueueItemIds= */ new int[] {1, 2},
        /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.removeMediaItem(/* index= */ 1);
    updateTimeLine(
        ImmutableList.of(mediaItem1),
        /* mediaQueueItemIds= */ new int[] {1},
        /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrent);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    castPlayer.removeMediaItem(/* index= */ 0);
    updateTimeLine(
        ImmutableList.of(),
        /* mediaQueueItemIds= */ new int[0],
        /* currentItemId= */ C.INDEX_UNSET);
    verify(mockListener).onAvailableCommandsChanged(commandsWithoutSeek);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());
  }

  @Test
  public void removeMediaItem_atTheStart_notifiesAvailableCommandsChanged() {
    when(mockRemoteMediaClient.queueJumpToItem(anyInt(), anyLong(), eq(null)))
        .thenReturn(mockPendingResult);
    Player.Commands commandsWithoutSeek = createWithPermanentCommands();
    Player.Commands commandsWithSeekInCurrent =
        createWithPermanentCommands(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    Player.Commands commandsWithSeekInCurrentAndToPrevious =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);
    MediaItem mediaItem3 = createMediaItem(/* mediaQueueItemId= */ 3);

    castPlayer.addMediaItems(ImmutableList.of(mediaItem1, mediaItem2, mediaItem3));
    updateTimeLine(
        ImmutableList.of(mediaItem1, mediaItem2, mediaItem3),
        /* mediaQueueItemIds= */ new int[] {1, 2, 3},
        /* currentItemId= */ 3);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToPrevious);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.removeMediaItem(/* index= */ 0);
    updateTimeLine(
        ImmutableList.of(mediaItem2, mediaItem3),
        /* mediaQueueItemIds= */ new int[] {2, 3},
        /* currentItemId= */ 3);
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.removeMediaItem(/* index= */ 0);
    updateTimeLine(
        ImmutableList.of(mediaItem3),
        /* mediaQueueItemIds= */ new int[] {3},
        /* currentItemId= */ 3);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrent);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    castPlayer.removeMediaItem(/* index= */ 0);
    updateTimeLine(
        ImmutableList.of(),
        /* mediaQueueItemIds= */ new int[0],
        /* currentItemId= */ C.INDEX_UNSET);
    verify(mockListener).onAvailableCommandsChanged(commandsWithoutSeek);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());
  }

  @Test
  public void removeMediaItem_current_notifiesAvailableCommandsChanged() {
    Player.Commands commandsWithSeekInCurrent =
        createWithPermanentCommands(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    Player.Commands commandsWithSeekInCurrentAndToNext =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
    MediaItem mediaItem1 = createMediaItem(/* mediaQueueItemId= */ 1);
    MediaItem mediaItem2 = createMediaItem(/* mediaQueueItemId= */ 2);

    castPlayer.addMediaItems(ImmutableList.of(mediaItem1, mediaItem2));
    updateTimeLine(
        ImmutableList.of(mediaItem1, mediaItem2),
        /* mediaQueueItemIds= */ new int[] {1, 2},
        /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToNext);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.removeMediaItem(/* index= */ 0);
    updateTimeLine(
        ImmutableList.of(mediaItem2),
        /* mediaQueueItemIds= */ new int[] {2},
        /* currentItemId= */ 2);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrent);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());
  }

  @Test
  public void setRepeatMode_all_notifiesAvailableCommandsChanged() {
    when(mockRemoteMediaClient.queueSetRepeatMode(anyInt(), eq(null)))
        .thenReturn(mockPendingResult);
    Player.Commands commandsWithSeekInCurrent =
        createWithPermanentCommands(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    Player.Commands commandsWithSeekAnywhere =
        createWithPermanentCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    int[] mediaQueueItemIds = new int[] {1};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrent);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekAnywhere);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());
  }

  @Test
  public void setRepeatMode_one_doesNotNotifyAvailableCommandsChanged() {
    when(mockRemoteMediaClient.queueSetRepeatMode(anyInt(), eq(null)))
        .thenReturn(mockPendingResult);
    Player.Commands commandsWithSeekInCurrent =
        createWithPermanentCommands(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    int[] mediaQueueItemIds = new int[] {1};
    List<MediaItem> mediaItems = createMediaItems(mediaQueueItemIds);

    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrent);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    castPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
    verify(mockListener).onAvailableCommandsChanged(any());
  }

  private int[] createMediaQueueItemIds(int numberOfIds) {
    int[] mediaQueueItemIds = new int[numberOfIds];
    for (int i = 0; i < numberOfIds; i++) {
      mediaQueueItemIds[i] = i + 1;
    }
    return mediaQueueItemIds;
  }

  private List<MediaItem> createMediaItems(int[] mediaQueueItemIds) {
    List<MediaItem> mediaItems = new ArrayList<>();
    for (int mediaQueueItemId : mediaQueueItemIds) {
      mediaItems.add(createMediaItem(mediaQueueItemId));
    }
    return mediaItems;
  }

  private MediaItem createMediaItem(int mediaQueueItemId) {
    return new MediaItem.Builder()
        .setUri("http://www.google.com/video" + mediaQueueItemId)
        .setMimeType(MimeTypes.APPLICATION_MPD)
        .setTag(mediaQueueItemId)
        .build();
  }

  private void addMediaItemsAndUpdateTimeline(List<MediaItem> mediaItems, int[] mediaQueueItemIds) {
    Assertions.checkState(mediaItems.size() == mediaQueueItemIds.length);
    castPlayer.addMediaItems(mediaItems);
    updateTimeLine(mediaItems, mediaQueueItemIds, /* currentItemId= */ 1);
  }

  private void updateTimeLine(
      List<MediaItem> mediaItems, int[] mediaQueueItemIds, int currentItemId) {
    int[] streamTypes = new int[mediaItems.size()];
    Arrays.fill(streamTypes, MediaInfo.STREAM_TYPE_BUFFERED);
    long[] durationsMs = new long[mediaItems.size()];
    updateTimeLine(
        mediaItems,
        mediaQueueItemIds,
        currentItemId,
        streamTypes,
        durationsMs,
        /* positionMs= */ C.TIME_UNSET);
  }

  private void updateTimeLine(
      List<MediaItem> mediaItems,
      int[] mediaQueueItemIds,
      int currentItemId,
      int[] streamTypes,
      long[] durationsMs,
      long positionMs) {
    // Set up mocks to allow the player to update the timeline.
    List<MediaQueueItem> queueItems = new ArrayList<>();
    for (int i = 0; i < mediaQueueItemIds.length; i++) {
      MediaItem mediaItem = mediaItems.get(i);
      int mediaQueueItemId = mediaQueueItemIds[i];
      int streamType = streamTypes[i];
      long durationMs = durationsMs[i];
      MediaInfo.Builder mediaInfoBuilder =
          new MediaInfo.Builder(mediaItem.playbackProperties.uri.toString())
              .setStreamType(streamType)
              .setContentType(mediaItem.playbackProperties.mimeType);
      if (durationMs != C.TIME_UNSET) {
        mediaInfoBuilder.setStreamDuration(durationMs);
      }
      MediaInfo mediaInfo = mediaInfoBuilder.build();
      MediaQueueItem mediaQueueItem = mock(MediaQueueItem.class);
      when(mediaQueueItem.getItemId()).thenReturn(mediaQueueItemId);
      when(mediaQueueItem.getMedia()).thenReturn(mediaInfo);
      queueItems.add(mediaQueueItem);
      if (mediaQueueItemId == currentItemId) {
        when(mockRemoteMediaClient.getCurrentItem()).thenReturn(mediaQueueItem);
        when(mockMediaStatus.getMediaInfo()).thenReturn(mediaInfo);
      }
    }
    if (positionMs != C.TIME_UNSET) {
      when(mockRemoteMediaClient.getApproximateStreamPosition()).thenReturn(positionMs);
    }
    when(mockMediaQueue.getItemIds()).thenReturn(mediaQueueItemIds);
    when(mockMediaStatus.getQueueItems()).thenReturn(queueItems);
    when(mockMediaStatus.getCurrentItemId())
        .thenReturn(currentItemId == C.INDEX_UNSET ? 0 : currentItemId);

    // Call listener to update the timeline of the player.
    remoteMediaClientCallback.onStatusUpdated();
  }

  private static Player.Commands createWithPermanentCommands(
      @Player.Command int... additionalCommands) {
    Player.Commands.Builder builder = new Player.Commands.Builder();
    builder.addAll(CastPlayer.PERMANENT_AVAILABLE_COMMANDS);
    builder.addAll(additionalCommands);
    return builder.build();
  }
}
