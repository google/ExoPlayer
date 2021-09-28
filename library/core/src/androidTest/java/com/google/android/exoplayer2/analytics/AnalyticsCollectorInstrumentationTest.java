/*
 * Copyright 2021 The Android Open Source Project
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
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;

import android.app.Instrumentation;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** Instrumentation tests for the {@link AnalyticsCollector} */
@RunWith(AndroidJUnit4.class)
public class AnalyticsCollectorInstrumentationTest {

  /**
   * This is a regression test against [internal ref: b/195396384]. The root cause of the regression
   * was an introduction of an additional {@link AnalyticsListener#onEvents} callback inside {@link
   * AnalyticsCollector} for callbacks that are queued during {@link Player#release}. The additional
   * callback was called before {@link AnalyticsListener#onPlayerReleased} but its associated event
   * real-time timestamps were greater than the real-time timestamp of {@link
   * AnalyticsListener#onPlayerReleased}. As a result, the {@link AnalyticsListener#onEvents} that
   * contained {@link AnalyticsListener#EVENT_PLAYER_RELEASED} had a real-time timestamp that was
   * smaller than the timestamps of the previously forwarded events. That broke the {@link
   * PlaybackStatsListener} which assumed that real-time event timestamps are always increasing.
   *
   * <p>The regression was easily reproduced in the demo app with a {@link PlaybackStatsListener}
   * attached to the player and pressing back while the app was playing a video. That would make the
   * app call {@link Player#release} while the player was playing, and it would throw an exception
   * from the {@link PlaybackStatsListener}.
   *
   * <p>This test starts playback of an item and calls {@link Player#release} while the player is
   * playing. The test uses a custom {@link Renderer} that queues a callback to be handled after
   * {@link Player#release} completes. The test asserts that {@link
   * AnalyticsListener#onPlayerReleased} is called after any callbacks queued during {@link
   * Player#release} and its real-time timestamp is greater that the timestamps of the other
   * callbacks.
   */
  @Test
  public void releasePlayer_whilePlaying_onPlayerReleasedIsForwardedLast() throws Exception {
    AtomicLong playerReleaseTimeMs = new AtomicLong(C.TIME_UNSET);
    AtomicReference<PlaybackException> playerError = new AtomicReference<>();
    AtomicReference<SimpleExoPlayer> player = new AtomicReference<>();
    ConditionVariable playerReleasedEventArrived = new ConditionVariable();
    AnalyticsListener analyticsListener =
        spy(new TestAnalyticsListener(playerReleasedEventArrived));
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    instrumentation.runOnMainSync(
        () -> {
          player.set(new ExoPlayer.Builder(instrumentation.getContext()).build());
          player
              .get()
              .addListener(
                  new Player.Listener() {
                    @Override
                    public void onPlayerError(PlaybackException error) {
                      playerError.set(error);
                      player.get().release();
                    }
                  });
          player.get().addAnalyticsListener(analyticsListener);
          player.get().setMediaItem(MediaItem.fromUri("asset:///media/mp4/preroll-5s.mp4"));
          player.get().prepare();
          player.get().play();
        });
    waitUntilPosition(player.get(), /* positionMs= */ 1000);
    instrumentation.runOnMainSync(
        () -> {
          player.get().release();
          playerReleaseTimeMs.set(Clock.DEFAULT.elapsedRealtime());
        });
    playerReleasedEventArrived.block();

    assertThat(playerError.get()).isNull();
    assertThat(playerReleaseTimeMs).isNotEqualTo(C.TIME_UNSET);
    InOrder inOrder = inOrder(analyticsListener);
    ArgumentCaptor<AnalyticsListener.EventTime> onVideoDecoderReleasedArgumentCaptor =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    inOrder
        .verify(analyticsListener)
        .onVideoDecoderReleased(onVideoDecoderReleasedArgumentCaptor.capture(), any());
    assertThat(onVideoDecoderReleasedArgumentCaptor.getValue().realtimeMs)
        .isGreaterThan(playerReleaseTimeMs.get());
    inOrder
        .verify(analyticsListener)
        .onEvents(
            same(player.get()),
            argThat(events -> events.contains(AnalyticsListener.EVENT_VIDEO_DECODER_RELEASED)));
    ArgumentCaptor<AnalyticsListener.EventTime> onPlayerReleasedArgumentCaptor =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    inOrder.verify(analyticsListener).onPlayerReleased(onPlayerReleasedArgumentCaptor.capture());
    assertThat(onPlayerReleasedArgumentCaptor.getAllValues()).hasSize(1);
    assertThat(onPlayerReleasedArgumentCaptor.getValue().realtimeMs)
        .isGreaterThan(onVideoDecoderReleasedArgumentCaptor.getValue().realtimeMs);
    inOrder
        .verify(analyticsListener)
        .onEvents(
            same(player.get()),
            argThat(events -> events.contains(AnalyticsListener.EVENT_PLAYER_RELEASED)));
  }

  private static void waitUntilPosition(SimpleExoPlayer simpleExoPlayer, long positionMs)
      throws Exception {
    ConditionVariable conditionVariable = new ConditionVariable();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              simpleExoPlayer.addListener(
                  new Player.Listener() {
                    @Override
                    public void onPlayerError(PlaybackException error) {
                      conditionVariable.open();
                    }
                  });
              simpleExoPlayer
                  .createMessage((messageType, payload) -> conditionVariable.open())
                  .setPosition(positionMs)
                  .send();
            });
    conditionVariable.block();
  }

  /**
   * An {@link AnalyticsListener} that blocks the thread on {@link
   * AnalyticsListener#onVideoDecoderReleased} for at least {@code 1ms} and unblocks a {@link
   * ConditionVariable} when an {@link AnalyticsListener#EVENT_PLAYER_RELEASED} arrives. The class
   * is public and non-final so we can use it with {@link org.mockito.Mockito#spy}.
   */
  public static class TestAnalyticsListener implements AnalyticsListener {
    private final ConditionVariable playerReleasedEventArrived;

    public TestAnalyticsListener(ConditionVariable playerReleasedEventArrived) {
      this.playerReleasedEventArrived = playerReleasedEventArrived;
    }

    @Override
    public void onVideoDecoderReleased(EventTime eventTime, String decoderName) {
      // Sleep for 1 ms so that the elapsedRealtime when the subsequent events
      // are greater by at least 1 ms.
      long startTimeMs = Clock.DEFAULT.elapsedRealtime();
      try {
        while (startTimeMs + 1 > Clock.DEFAULT.elapsedRealtime()) {
          Thread.sleep(1);
        }
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public void onEvents(Player player, Events events) {
      if (events.contains(AnalyticsListener.EVENT_PLAYER_RELEASED)) {
        playerReleasedEventArrived.open();
      }
    }
  }
}
