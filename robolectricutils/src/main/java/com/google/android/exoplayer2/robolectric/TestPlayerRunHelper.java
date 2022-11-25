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
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.os.Looper;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Util;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Helper methods to block the calling thread until the provided {@link ExoPlayer} instance reaches
 * a particular state.
 */
public class TestPlayerRunHelper {

  private TestPlayerRunHelper() {}

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getPlaybackState()} matches the
   * expected state or a playback error occurs.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * @param player The {@link Player}.
   * @param expectedState The expected {@link Player.State}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPlaybackState(Player player, @Player.State int expectedState)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (player instanceof ExoPlayer) {
      verifyPlaybackThreadIsAlive((ExoPlayer) player);
    }
    runMainLooperUntil(
        () -> player.getPlaybackState() == expectedState || player.getPlayerError() != null);
    if (player.getPlayerError() != null) {
      throw new IllegalStateException(player.getPlayerError());
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getPlayWhenReady()} matches the
   * expected value or a playback error occurs.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * @param player The {@link Player}.
   * @param expectedPlayWhenReady The expected value for {@link Player#getPlayWhenReady()}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPlayWhenReady(Player player, boolean expectedPlayWhenReady)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (player instanceof ExoPlayer) {
      verifyPlaybackThreadIsAlive((ExoPlayer) player);
    }
    runMainLooperUntil(
        () ->
            player.getPlayWhenReady() == expectedPlayWhenReady || player.getPlayerError() != null);
    if (player.getPlayerError() != null) {
      throw new IllegalStateException(player.getPlayerError());
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#isLoading()} matches the expected
   * value or a playback error occurs.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * @param player The {@link Player}.
   * @param expectedIsLoading The expected value for {@link Player#isLoading()}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilIsLoading(Player player, boolean expectedIsLoading)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (player instanceof ExoPlayer) {
      verifyPlaybackThreadIsAlive((ExoPlayer) player);
    }
    runMainLooperUntil(
        () -> player.isLoading() == expectedIsLoading || player.getPlayerError() != null);
    if (player.getPlayerError() != null) {
      throw new IllegalStateException(player.getPlayerError());
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getCurrentTimeline()} matches the
   * expected timeline or a playback error occurs.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * @param player The {@link Player}.
   * @param expectedTimeline The expected {@link Timeline}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilTimelineChanged(Player player, Timeline expectedTimeline)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (player instanceof ExoPlayer) {
      verifyPlaybackThreadIsAlive((ExoPlayer) player);
    }
    runMainLooperUntil(
        () ->
            expectedTimeline.equals(player.getCurrentTimeline())
                || player.getPlayerError() != null);
    if (player.getPlayerError() != null) {
      throw new IllegalStateException(player.getPlayerError());
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until a timeline change or a playback error occurs.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * @param player The {@link Player}.
   * @return The new {@link Timeline}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static Timeline runUntilTimelineChanged(Player player) throws TimeoutException {
    verifyMainTestThread(player);
    AtomicReference<@NullableType Timeline> receivedTimeline = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            receivedTimeline.set(timeline);
          }
        };
    player.addListener(listener);
    runMainLooperUntil(() -> receivedTimeline.get() != null || player.getPlayerError() != null);
    player.removeListener(listener);
    if (player.getPlayerError() != null) {
      throw new IllegalStateException(player.getPlayerError());
    }
    return checkNotNull(receivedTimeline.get());
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link
   * Player.Listener#onPositionDiscontinuity(Player.PositionInfo, Player.PositionInfo, int)} is
   * called with the specified {@link Player.DiscontinuityReason} or a playback error occurs.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * @param player The {@link Player}.
   * @param expectedReason The expected {@link Player.DiscontinuityReason}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPositionDiscontinuity(
      Player player, @Player.DiscontinuityReason int expectedReason) throws TimeoutException {
    verifyMainTestThread(player);
    if (player instanceof ExoPlayer) {
      verifyPlaybackThreadIsAlive((ExoPlayer) player);
    }
    AtomicBoolean receivedCallback = new AtomicBoolean(false);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
            if (reason == expectedReason) {
              receivedCallback.set(true);
            }
          }
        };
    player.addListener(listener);
    runMainLooperUntil(() -> receivedCallback.get() || player.getPlayerError() != null);
    player.removeListener(listener);
    if (player.getPlayerError() != null) {
      throw new IllegalStateException(player.getPlayerError());
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until a player error occurs.
   *
   * @param player The {@link Player}.
   * @return The raised {@link ExoPlaybackException}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static ExoPlaybackException runUntilError(ExoPlayer player) throws TimeoutException {
    verifyMainTestThread(player);
    verifyPlaybackThreadIsAlive(player);

    runMainLooperUntil(() -> player.getPlayerError() != null);
    return checkNotNull(player.getPlayerError());
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link
   * ExoPlayer.AudioOffloadListener#onExperimentalSleepingForOffloadChanged(boolean)} is called or a
   * playback error occurs.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * @param player The {@link Player}.
   * @param expectedSleepForOffload The expected sleep of offload state.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilSleepingForOffload(ExoPlayer player, boolean expectedSleepForOffload)
      throws TimeoutException {
    verifyMainTestThread(player);
    verifyPlaybackThreadIsAlive(player);

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
    runMainLooperUntil(() -> receiverCallback.get() || player.getPlayerError() != null);
    if (player.getPlayerError() != null) {
      throw new IllegalStateException(player.getPlayerError());
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until the {@link Player.Listener#onRenderedFirstFrame}
   * callback is called or a playback error occurs.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}..
   *
   * @param player The {@link Player}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilRenderedFirstFrame(ExoPlayer player) throws TimeoutException {
    verifyMainTestThread(player);
    verifyPlaybackThreadIsAlive(player);

    AtomicBoolean receivedCallback = new AtomicBoolean(false);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRenderedFirstFrame() {
            receivedCallback.set(true);
          }
        };
    player.addListener(listener);
    runMainLooperUntil(() -> receivedCallback.get() || player.getPlayerError() != null);
    player.removeListener(listener);
    if (player.getPlayerError() != null) {
      throw new IllegalStateException(player.getPlayerError());
    }
  }

  /**
   * Calls {@link Player#play()}, runs tasks of the main {@link Looper} until the {@code player}
   * reaches the specified position or a playback error occurs, and then pauses the {@code player}.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * @param player The {@link Player}.
   * @param mediaItemIndex The index of the media item.
   * @param positionMs The position within the media item, in milliseconds.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void playUntilPosition(ExoPlayer player, int mediaItemIndex, long positionMs)
      throws TimeoutException {
    verifyMainTestThread(player);
    verifyPlaybackThreadIsAlive(player);
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
        .setPosition(mediaItemIndex, positionMs)
        .send();
    player.play();
    runMainLooperUntil(() -> messageHandled.get() || player.getPlayerError() != null);
    if (player.getPlayerError() != null) {
      throw new IllegalStateException(player.getPlayerError());
    }
  }

  /**
   * Calls {@link Player#play()}, runs tasks of the main {@link Looper} until the {@code player}
   * reaches the specified media item or a playback error occurs, and then pauses the {@code
   * player}.
   *
   * <p>If a playback error occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * @param player The {@link Player}.
   * @param mediaItemIndex The index of the media item.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void playUntilStartOfMediaItem(ExoPlayer player, int mediaItemIndex)
      throws TimeoutException {
    playUntilPosition(player, mediaItemIndex, /* positionMs= */ 0);
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
    verifyPlaybackThreadIsAlive(player);

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

  private static void verifyPlaybackThreadIsAlive(ExoPlayer player) {
    checkState(
        player.getPlaybackLooper().getThread().isAlive(),
        "Playback thread is not alive, has the player been released?");
  }
}
