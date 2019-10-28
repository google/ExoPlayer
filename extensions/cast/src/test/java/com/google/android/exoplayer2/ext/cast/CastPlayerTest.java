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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Player;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
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
    // Make the remote media client be initially paused (most common scenario).
    when(mockRemoteMediaClient.isPaused()).thenReturn(true);
    castPlayer = new CastPlayer(mockCastContext);
    castPlayer.addListener(mockListener);
    verify(mockRemoteMediaClient).addListener(listenerArgumentCaptor.capture());
    remoteMediaClientListener = listenerArgumentCaptor.getValue();
  }

  @Test
  public void testSetPlayWhenReady_masksLocalState() {
    when(mockRemoteMediaClient.play()).thenReturn(mockPendingResult);
    // Initially paused.
    assertThat(castPlayer.getPlayWhenReady()).isFalse();

    castPlayer.setPlayWhenReady(true);
    verify(mockPendingResult).setResultCallback(setResultCallbackArgumentCaptor.capture());
    assertThat(castPlayer.getPlayWhenReady()).isTrue();
    verify(mockListener).onPlayerStateChanged(true, Player.STATE_IDLE);

    // There is a status update in the middle, which should be hidden by masking.
    remoteMediaClientListener.onStatusUpdated();
    Mockito.verifyNoMoreInteractions(mockListener);

    // Upon result, the remoteMediaClient has updated it's state according to the play() call.
    when(mockRemoteMediaClient.isPaused()).thenReturn(false);
    setResultCallbackArgumentCaptor
        .getValue()
        .onResult(Mockito.mock(RemoteMediaClient.MediaChannelResult.class));
    Mockito.verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void testSetPlayWhenReadyMasking_updatesUponResultChange() {
    when(mockRemoteMediaClient.play()).thenReturn(mockPendingResult);
    // Initially paused.
    assertThat(castPlayer.getPlayWhenReady()).isFalse();

    castPlayer.setPlayWhenReady(true);
    verify(mockPendingResult).setResultCallback(setResultCallbackArgumentCaptor.capture());
    assertThat(castPlayer.getPlayWhenReady()).isTrue();
    verify(mockListener).onPlayerStateChanged(true, Player.STATE_IDLE);

    // Upon result, the remote media client is still paused. So the state should update.
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
}
