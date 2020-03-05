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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Player;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
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
  private RemoteMediaClient.Listener remoteMediaClientListener;
  @Mock private RemoteMediaClient mockRemoteMediaClient;
  @Mock private MediaStatus mockMediaStatus;
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

  @Test
  public void testSetPlayWhenReady_masksRemoteState() {
    when(mockRemoteMediaClient.play()).thenReturn(mockPendingResult);
    assertThat(castPlayer.getPlayWhenReady()).isFalse();

    castPlayer.setPlayWhenReady(true);
    verify(mockPendingResult).setResultCallback(setResultCallbackArgumentCaptor.capture());
    assertThat(castPlayer.getPlayWhenReady()).isTrue();
    verify(mockListener).onPlayerStateChanged(true, Player.STATE_IDLE);

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

  @Test
  public void testSetPlayWhenReadyMasking_updatesUponResultChange() {
    when(mockRemoteMediaClient.play()).thenReturn(mockPendingResult);
    assertThat(castPlayer.getPlayWhenReady()).isFalse();

    castPlayer.setPlayWhenReady(true);
    verify(mockPendingResult).setResultCallback(setResultCallbackArgumentCaptor.capture());
    assertThat(castPlayer.getPlayWhenReady()).isTrue();
    verify(mockListener).onPlayerStateChanged(true, Player.STATE_IDLE);

    // Upon result, the remote media client is still paused. The state should reflect that.
    setResultCallbackArgumentCaptor
        .getValue()
        .onResult(Mockito.mock(RemoteMediaClient.MediaChannelResult.class));
    verify(mockListener).onPlayerStateChanged(false, Player.STATE_IDLE);
    assertThat(castPlayer.getPlayWhenReady()).isFalse();
  }

  @Test
  public void testPlayWhenReady_changesOnStatusUpdates() {
    assertThat(castPlayer.getPlayWhenReady()).isFalse();
    when(mockRemoteMediaClient.isPaused()).thenReturn(false);
    remoteMediaClientListener.onStatusUpdated();
    verify(mockListener).onPlayerStateChanged(true, Player.STATE_IDLE);
    assertThat(castPlayer.getPlayWhenReady()).isTrue();
  }

  @Test
  public void testSetRepeatMode_masksRemoteState() {
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
  public void testSetRepeatMode_updatesUponResultChange() {
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
  public void testRepeatMode_changesOnStatusUpdates() {
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_OFF);
    when(mockMediaStatus.getQueueRepeatMode()).thenReturn(MediaStatus.REPEAT_MODE_REPEAT_SINGLE);
    remoteMediaClientListener.onStatusUpdated();
    verify(mockListener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);
    assertThat(castPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ONE);
  }
}
