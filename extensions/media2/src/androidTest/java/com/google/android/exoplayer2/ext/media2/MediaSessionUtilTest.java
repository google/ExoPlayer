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
package com.google.android.exoplayer2.ext.media2;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.NonNull;
import androidx.media2.common.SessionPlayer;
import androidx.media2.common.SessionPlayer.PlayerResult;
import androidx.media2.session.MediaSession;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.ext.media2.test.R;
import com.google.android.exoplayer2.util.Assertions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MediaSessionUtil} */
@RunWith(AndroidJUnit4.class)
public class MediaSessionUtilTest {
  private static final int PLAYER_STATE_CHANGE_WAIT_TIME_MS = 5_000;

  @Rule public final PlayerTestRule playerTestRule = new PlayerTestRule();

  @Test
  public void getSessionCompatToken_withMediaControllerCompat_returnsValidToken() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();

    SessionPlayerConnector sessionPlayerConnector = playerTestRule.getSessionPlayerConnector();
    MediaSession.SessionCallback sessionCallback =
        new SessionCallbackBuilder(context, sessionPlayerConnector).build();
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);
    ListenableFuture<PlayerResult> prepareResult = sessionPlayerConnector.prepare();
    CountDownLatch latch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        playerTestRule.getExecutor(),
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(@NonNull SessionPlayer player, int playerState) {
            if (playerState == SessionPlayer.PLAYER_STATE_PLAYING) {
              latch.countDown();
            }
          }
        });

    MediaSession session2 =
        new MediaSession.Builder(context, sessionPlayerConnector)
            .setSessionCallback(playerTestRule.getExecutor(), sessionCallback)
            .build();

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              try {
                MediaSessionCompat.Token token =
                    Assertions.checkNotNull(MediaSessionUtil.getSessionCompatToken(session2));
                MediaControllerCompat controllerCompat = new MediaControllerCompat(context, token);
                controllerCompat.getTransportControls().play();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
    assertThat(prepareResult.get(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS).getResultCode())
        .isEqualTo(PlayerResult.RESULT_SUCCESS);
    assertThat(latch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
  }
}
