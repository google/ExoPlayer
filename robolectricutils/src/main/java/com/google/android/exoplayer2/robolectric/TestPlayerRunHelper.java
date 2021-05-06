/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.exoplayer2.robolectric;

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runMainLooperUntil;
import static com.google.common.truth.Truth.assertThat;

import android.os.Looper;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Helper methods to block the calling thread until the provided {@link SimpleExoPlayer} instance
 * reaches a particular state.
 */
public class TestPlayerRunHelper {

  private TestPlayerRunHelper() {}

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getPlaybackState()} matches the
   * expected state.
   *
   * @param player The {@link Player}.
   * @param expectedState The expected {@link Player.State}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPlaybackState(Player player, @Player.State int expectedState)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (player.getPlaybackState() == expectedState) {
      return;
    }
    AtomicBoolean receivedExpectedState = new AtomicBoolean(false);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int state) {
            if (state == expectedState) {
              receivedExpectedState.set(true);
            }
          }
        };
    player.addListener(listener);
    runMainLooperUntil(receivedExpectedState::get);
    player.removeListener(listener);
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getPlayWhenReady()} matches the
   * expected value.
   *
   * @param player The {@link Player}.
   * @param expectedPlayWhenReady The expected value for {@link Player#getPlayWhenReady()}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPlayWhenReady(Player player, boolean expectedPlayWhenReady)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (player.getPlayWhenReady() == expectedPlayWhenReady) {
      return;
    }
    AtomicBoolean receivedExpectedPlayWhenReady = new AtomicBoolean(false);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (playWhenReady == expectedPlayWhenReady) {
              receivedExpectedPlayWhenReady.set(true);
            }
            player.removeListener(this);
          }
        };
    player.addListener(listener);
    runMainLooperUntil(receivedExpectedPlayWhenReady::get);
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getCurrentTimeline()} matches the
   * expected timeline.
   *
   * @param player The {@link Player}.
   * @param expectedTimeline The expected {@link Timeline}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilTimelineChanged(Player player, Timeline expectedTimeline)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (expectedTimeline.equals(player.getCurrentTimeline())) {
      return;
    }
    AtomicBoolean receivedExpectedTimeline = new AtomicBoolean(false);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            if (expectedTimeline.equals(timeline)) {
              receivedExpectedTimeline.set(true);
            }
            player.removeListener(this);
          }
        };
    player.addListener(listener);
    runMainLooperUntil(receivedExpectedTimeline::get);
  }

  /**
   * Runs tasks of the main {@link Looper} until a timeline change occurred.
   *
   * @param player The {@link Player}.
   * @return The new {@link Timeline}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static Timeline runUntilTimelineChanged(Player player) throws TimeoutException {
    verifyMainTestThread(player);
    AtomicReference<Timeline> receivedTimeline = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            receivedTimeline.set(timeline);
            player.removeListener(this);
          }
        };
    player.addListener(listener);
    runMainLooperUntil(() -> receivedTimeline.get() != null);
    return receivedTimeline.get();
  }

  /**
   * Runs tasks of the main {@link Looper} until a {@link
   * Player.Listener#onPositionDiscontinuity(Player.PositionInfo, Player.PositionInfo, int)}
   * callback with the specified {@link Player.DiscontinuityReason} occurred.
   *
   * @param player The {@link Player}.
   * @param expectedReason The expected {@link Player.DiscontinuityReason}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPositionDiscontinuity(
      Player player, @Player.DiscontinuityReason int expectedReason) throws TimeoutException {
    verifyMainTestThread(player);
    AtomicBoolean receivedCallback = new AtomicBoolean(false);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
            if (reason == expectedReason) {
              receivedCallback.set(true);
              player.removeListener(this);
            }
          }
        };
    player.addListener(listener);
    runMainLooperUntil(receivedCallback::get);
  }

  /**
   * Runs tasks of the main {@link Looper} until a player error occurred.
   *
   * @param player The {@link Player}.
   * @return The raised {@link ExoPlaybackException}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static ExoPlaybackException runUntilError(Player player) throws TimeoutException {
    verifyMainTestThread(player);
    AtomicReference<ExoPlaybackException> receivedError = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlayerError(ExoPlaybackException error) {
            receivedError.set(error);
            player.removeListener(this);
          }
        };
    player.addListener(listener);
    runMainLooperUntil(() -> receivedError.get() != null);
    return receivedError.get();
  }

  /**
   * Runs tasks of the main {@link Looper} until a {@link
   * ExoPlayer.AudioOffloadListener#onExperimentalOffloadSchedulingEnabledChanged} callback
   * occurred.
   *
   * @param player The {@link Player}.
   * @return The new offloadSchedulingEnabled state.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static boolean runUntilReceiveOffloadSchedulingEnabledNewState(ExoPlayer player)
      throws TimeoutException {
    verifyMainTestThread(player);
    AtomicReference<@NullableType Boolean> offloadSchedulingEnabledReceiver =
        new AtomicReference<>();
    ExoPlayer.AudioOffloadListener listener =
        new ExoPlayer.AudioOffloadListener() {
          @Override
          public void onExperimentalOffloadSchedulingEnabledChanged(
              boolean offloadSchedulingEnabled) {
            offloadSchedulingEnabledReceiver.set(offloadSchedulingEnabled);
          }
        };
    player.addAudioOffloadListener(listener);
    runMainLooperUntil(() -> offloadSchedulingEnabledReceiver.get() != null);
    return Assertions.checkNotNull(offloadSchedulingEnabledReceiver.get());
  }

  /**
   * Runs tasks of the main {@link Looper} until a {@link
   * ExoPlayer.AudioOffloadListener#onExperimentalSleepingForOffloadChanged(boolean)} callback
   * occurred.
   *
   * @param player The {@link Player}.
   * @param expectedSleepForOffload The expected sleep of offload state.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilSleepingForOffload(ExoPlayer player, boolean expectedSleepForOffload)
      throws TimeoutException {
    verifyMainTestThread(player);
    AtomicBoolean receiverCallback = new AtomicBoolean(false);
    ExoPlayer.AudioOffloadListener listener =
        new ExoPlayer.AudioOffloadListener() {
          @Override
          public void onExperimentalSleepingForOffloadChanged(boolean sleepingForOffload) {
            if (sleepingForOffload == expectedSleepForOffload) {
              receiverCallback.set(true);
            }
          }
        };
    player.addAudioOffloadListener(listener);
    runMainLooperUntil(
        () -> { // Make sure progress is being made, see [internal: b/170387438#comment2]
          assertThat(player.getPlayerError()).isNull();
          assertThat(player.getPlayWhenReady()).isTrue();
          assertThat(player.getPlaybackState()).isAnyOf(Player.STATE_BUFFERING, Player.STATE_READY);
          return receiverCallback.get();
        });
  }

  /**
   * Runs tasks of the main {@link Looper} until the {@link VideoListener#onRenderedFirstFrame}
   * callback has been called.
   *
   * @param player The {@link Player}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilRenderedFirstFrame(SimpleExoPlayer player) throws TimeoutException {
    verifyMainTestThread(player);
    AtomicBoolean receivedCallback = new AtomicBoolean(false);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRenderedFirstFrame() {
            receivedCallback.set(true);
            player.removeListener(this);
          }
        };
    player.addListener(listener);
    runMainLooperUntil(receivedCallback::get);
  }

  /**
   * Calls {@link Player#play()}, runs tasks of the main {@link Looper} until the {@code player}
   * reaches the specified position and then pauses the {@code player}.
   *
   * @param player The {@link Player}.
   * @param windowIndex The window.
   * @param positionMs The position within the window, in milliseconds.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void playUntilPosition(ExoPlayer player, int windowIndex, long positionMs)
      throws TimeoutException {
    verifyMainTestThread(player);
    Looper applicationLooper = Util.getCurrentOrMainLooper();
    AtomicBoolean messageHandled = new AtomicBoolean(false);
    player
        .createMessage(
            (messageType, payload) -> {
              // Block playback thread until pause command has been sent from test thread.
              ConditionVariable blockPlaybackThreadCondition = new ConditionVariable();
              player
                  .getClock()
                  .createHandler(applicationLooper, /* callback= */ null)
                  .post(
                      () -> {
                        player.pause();
                        messageHandled.set(true);
                        blockPlaybackThreadCondition.open();
                      });
              try {
                player.getClock().onThreadBlocked();
                blockPlaybackThreadCondition.block();
              } catch (InterruptedException e) {
                // Ignore.
              }
            })
        .setPosition(windowIndex, positionMs)
        .send();
    player.play();
    runMainLooperUntil(messageHandled::get);
  }

  /**
   * Calls {@link Player#play()}, runs tasks of the main {@link Looper} until the {@code player}
   * reaches the specified window and then pauses the {@code player}.
   *
   * @param player The {@link Player}.
   * @param windowIndex The window.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void playUntilStartOfWindow(ExoPlayer player, int windowIndex)
      throws TimeoutException {
    playUntilPosition(player, windowIndex, /* positionMs= */ 0);
  }

  /**
   * Runs tasks of the main {@link Looper} until the player completely handled all previously issued
   * commands on the internal playback thread.
   *
   * @param player The {@link Player}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPendingCommandsAreFullyHandled(ExoPlayer player)
      throws TimeoutException {
    verifyMainTestThread(player);
    // Send message to player that will arrive after all other pending commands. Thus, the message
    // execution on the app thread will also happen after all other pending command
    // acknowledgements have arrived back on the app thread.
    AtomicBoolean receivedMessageCallback = new AtomicBoolean(false);
    player
        .createMessage((type, data) -> receivedMessageCallback.set(true))
        .setLooper(Util.getCurrentOrMainLooper())
        .send();
    runMainLooperUntil(receivedMessageCallback::get);
  }

  private static void verifyMainTestThread(Player player) {
    if (Looper.myLooper() != Looper.getMainLooper()
        || player.getApplicationLooper() != Looper.getMainLooper()) {
      throw new IllegalStateException();
    }
  }
}
