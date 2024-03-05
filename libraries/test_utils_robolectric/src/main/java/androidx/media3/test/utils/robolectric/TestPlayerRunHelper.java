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

package androidx.media3.test.utils.robolectric;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil;

import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.test.utils.ThreadTestUtil;
import com.google.common.base.Supplier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Helper methods to block the calling thread until the provided {@link ExoPlayer} instance reaches
 * a particular state.
 *
 * <p>This class has two usage modes:
 *
 * <ul>
 *   <li>Fluent method chaining, e.g. {@code
 *       run(player).ignoringNonFatalErrors().untilState(STATE_ENDED)}.
 *   <li>Single method call, e.g. {@code runUntilPlaybackState(player, STATE_ENDED)}.
 * </ul>
 *
 * <p>New usages should prefer the fluent method chaining, and new functionality will only be added
 * to this form. The older single methods will be kept for backwards compatibility.
 */
@UnstableApi
public final class TestPlayerRunHelper {

  private TestPlayerRunHelper() {}

  /**
   * Intermediate type that allows callers to run the main {@link Looper} until certain conditions
   * are met.
   *
   * <p>If an error occurs while a {@code untilXXX(...)} method is waiting for the condition to
   * become true, most methods will throw that error (exceptions to this are documented on specific
   * methods below). Use {@link #ignoringNonFatalErrors()} to ignore non-fatal errors and only fail
   * on {@linkplain Player#getPlayerError() fatal playback errors}.
   *
   * <p>Instances of this class should only be used for a single {@code untilXXX()} invocation and
   * not be re-used.
   */
  public static class PlayerRunResult {
    private final Player player;
    private final boolean throwNonFatalErrors;

    protected final boolean playBeforeWaiting;

    protected boolean hasBeenUsed;

    /**
     * Constructs a new instance.
     *
     * @param player The player to interact with.
     * @param playBeforeWaiting Whether to call {@link Player#play()} before waiting for the chosen
     *     condition.
     * @param throwNonFatalErrors Whether to throw non-fatal errors passed to {@link
     *     AnalyticsListener}.
     */
    // This constructor is deliberately private to prevent subclassing outside TestPlayerRunHelper.
    private PlayerRunResult(Player player, boolean playBeforeWaiting, boolean throwNonFatalErrors) {
      verifyMainTestThread(player);
      if (player instanceof ExoPlayer) {
        verifyPlaybackThreadIsAlive((ExoPlayer) player);
      }
      this.player = player;
      this.playBeforeWaiting = playBeforeWaiting;
      this.throwNonFatalErrors = throwNonFatalErrors;
    }

    /**
     * Runs tasks of the main {@link Looper} until {@link Player#getPlaybackState()} matches the
     * expected state or an error occurs.
     *
     * @throws TimeoutException If the {@linkplain RobolectricUtil#DEFAULT_TIMEOUT_MS default
     *     timeout} is exceeded.
     */
    public final void untilState(@Player.State int expectedState) throws Exception {
      runUntil(() -> player.getPlaybackState() == expectedState);
    }

    /**
     * Runs tasks of the main {@link Looper} until {@link Player#getPlayWhenReady()} matches the
     * expected value or an error occurs.
     *
     * @throws TimeoutException If the {@linkplain RobolectricUtil#DEFAULT_TIMEOUT_MS default
     *     timeout} is exceeded.
     */
    public final void untilPlayWhenReadyIs(boolean expectedPlayWhenReady) throws Exception {
      runUntil(() -> player.getPlayWhenReady() == expectedPlayWhenReady);
    }

    /**
     * Runs tasks of the main {@link Looper} until {@link Player#isLoading()} matches the expected
     * value or an error occurs.
     *
     * @throws TimeoutException If the {@linkplain RobolectricUtil#DEFAULT_TIMEOUT_MS default
     *     timeout} is exceeded.
     */
    public final void untilLoadingIs(boolean expectedIsLoading) throws Exception {
      runUntil(() -> player.isLoading() == expectedIsLoading);
    }

    /**
     * Runs tasks of the main {@link Looper} until a timeline change or an error occurs.
     *
     * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
     *     exceeded.
     */
    public final Timeline untilTimelineChanges() throws Exception {
      AtomicReference<@NullableType Timeline> receivedTimeline = new AtomicReference<>();
      Player.Listener listener =
          new Player.Listener() {
            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {
              receivedTimeline.set(timeline);
            }
          };
      player.addListener(listener);
      try {
        runUntil(() -> receivedTimeline.get() != null);
        return checkNotNull(receivedTimeline.get());
      } finally {
        player.removeListener(listener);
      }
    }

    /**
     * Runs tasks of the main {@link Looper} until {@link Player#getCurrentTimeline()} matches the
     * expected timeline or an error occurs.
     *
     * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
     *     exceeded.
     */
    public final void untilTimelineChangesTo(Timeline expectedTimeline) throws Exception {
      runUntil(() -> expectedTimeline.equals(player.getCurrentTimeline()));
    }

    /**
     * Runs tasks of the main {@link Looper} until {@link
     * Player.Listener#onPositionDiscontinuity(Player.PositionInfo, Player.PositionInfo, int)} is
     * called with the specified {@link Player.DiscontinuityReason} or an error occurs.
     *
     * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
     *     exceeded.
     */
    public final void untilPositionDiscontinuityWithReason(
        @Player.DiscontinuityReason int expectedReason) throws Exception {
      AtomicBoolean receivedExpectedDiscontinuityReason = new AtomicBoolean(false);
      Player.Listener listener =
          new Player.Listener() {
            @Override
            public void onPositionDiscontinuity(
                Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
              if (reason == expectedReason) {
                receivedExpectedDiscontinuityReason.set(true);
              }
            }
          };
      player.addListener(listener);
      try {
        runUntil(receivedExpectedDiscontinuityReason::get);
      } finally {
        player.removeListener(listener);
      }
    }

    /**
     * Runs tasks of the main {@link Looper} until a player error occurs.
     *
     * <p>Non-fatal errors are always ignored.
     *
     * @return The raised {@link PlaybackException}.
     * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
     *     exceeded.
     */
    public PlaybackException untilPlayerError() throws TimeoutException {
      checkState(!hasBeenUsed);
      hasBeenUsed = true;
      runMainLooperUntil(() -> player.getPlayerError() != null);
      return checkNotNull(player.getPlayerError());
    }

    /**
     * Runs tasks of the main {@link Looper} until {@link Player.Listener#onRenderedFirstFrame} is
     * called or an error occurs.
     *
     * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
     *     exceeded.
     */
    public void untilFirstFrameIsRendered() throws Exception {
      AtomicBoolean receivedFirstFrameRenderedCallback = new AtomicBoolean(false);
      Player.Listener listener =
          new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
              receivedFirstFrameRenderedCallback.set(true);
            }
          };
      player.addListener(listener);
      try {
        runUntil(receivedFirstFrameRenderedCallback::get);
      } finally {
        player.removeListener(listener);
      }
    }

    /**
     * Returns a new instance where the {@code untilXXX(...)} methods ignore non-fatal errors.
     *
     * <p>A fatal error is defined as an error that is passed to {@link
     * Player.Listener#onPlayerError(PlaybackException)} and results in the player transitioning to
     * {@link Player#STATE_IDLE}. A non-fatal error is defined as an error that is passed to any
     * other callback (e.g. {@link AnalyticsListener#onLoadError}).
     */
    public PlayerRunResult ignoringNonFatalErrors() {
      checkState(!hasBeenUsed);
      hasBeenUsed = true;
      return new PlayerRunResult(player, playBeforeWaiting, /* throwNonFatalErrors= */ false);
    }

    /** Runs the main {@link Looper} until {@code predicate} returns true or an error occurs. */
    protected final void runUntil(Supplier<Boolean> predicate) throws Exception {
      checkState(!hasBeenUsed);
      hasBeenUsed = true;
      ErrorListener errorListener = new ErrorListener(throwNonFatalErrors);
      if (player instanceof ExoPlayer) {
        ExoPlayer exoplayer = (ExoPlayer) player;
        exoplayer.addAnalyticsListener(errorListener);
      }
      player.addListener(errorListener);
      if (playBeforeWaiting) {
        player.play();
      }
      try {
        runMainLooperUntil(() -> predicate.get() || errorListener.hasFatalError());
      } finally {
        player.removeListener(errorListener);
        if (player instanceof ExoPlayer) {
          ((ExoPlayer) player).removeAnalyticsListener(errorListener);
        }
      }
      errorListener.maybeThrow();
    }
  }

  /**
   * An {@link ExoPlayer} specific subclass of {@link PlayerRunResult}, giving access to conditions
   * that only make sense for the {@link ExoPlayer} interface.
   */
  public static final class ExoPlayerRunResult extends PlayerRunResult {

    private final ExoPlayer player;

    private ExoPlayerRunResult(
        ExoPlayer player, boolean playBeforeWaiting, boolean throwNonFatalErrors) {
      super(player, playBeforeWaiting, throwNonFatalErrors);
      this.player = player;
    }

    @Override
    public ExoPlaybackException untilPlayerError() throws TimeoutException {
      return (ExoPlaybackException) super.untilPlayerError();
    }

    /**
     * Runs tasks of the main {@link Looper} until {@link ExoPlayer#isSleepingForOffload()} matches
     * the expected value, or an error occurs.
     *
     * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
     *     exceeded.
     */
    public void untilSleepingForOffloadBecomes(boolean expectedSleepingForOffload)
        throws Exception {
      AtomicBoolean receivedExpectedValue = new AtomicBoolean(false);
      ExoPlayer.AudioOffloadListener listener =
          new ExoPlayer.AudioOffloadListener() {
            @Override
            public void onSleepingForOffloadChanged(boolean sleepingForOffload) {
              if (sleepingForOffload == expectedSleepingForOffload) {
                receivedExpectedValue.set(true);
              }
            }
          };
      player.addAudioOffloadListener(listener);
      try {
        runUntil(receivedExpectedValue::get);
      } finally {
        player.removeAudioOffloadListener(listener);
      }
    }

    /**
     * Runs tasks of the main {@link Looper} until playback reaches the specified position or an
     * error occurs.
     *
     * <p>The playback thread is automatically blocked from making further progress after reaching
     * this position and will only be unblocked by other {@code run()/play().untilXXX(...)} method
     * chains, custom {@link RobolectricUtil#runMainLooperUntil} conditions, or an explicit {@link
     * ThreadTestUtil#unblockThreadsWaitingForProgressOnCurrentLooper()} on the main thread.
     *
     * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
     *     exceeded.
     */
    public void untilPosition(int mediaItemIndex, long positionMs) throws Exception {
      checkState(!hasBeenUsed);
      hasBeenUsed = true;
      Looper applicationLooper = Util.getCurrentOrMainLooper();
      AtomicBoolean messageHandled = new AtomicBoolean(false);
      player
          .createMessage(
              (messageType, payload) -> {
                // Block playback thread until the main app thread is able to trigger further
                // actions.
                ConditionVariable blockPlaybackThreadCondition = new ConditionVariable();
                ThreadTestUtil.registerThreadIsBlockedUntilProgressOnLooper(
                    blockPlaybackThreadCondition, applicationLooper);
                player
                    .getClock()
                    .createHandler(applicationLooper, /* callback= */ null)
                    .post(() -> messageHandled.set(true));
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
     * Runs tasks of the main {@link Looper} until playback reaches the specified media item or a
     * playback error occurs.
     *
     * <p>The playback thread is automatically blocked from making further progress after reaching
     * the media item and will only be unblocked by other {@code run()/play().untilXXX(...)} method
     * chains, custom {@link RobolectricUtil#runMainLooperUntil} conditions, or an explicit {@link
     * ThreadTestUtil#unblockThreadsWaitingForProgressOnCurrentLooper()} on the main thread.
     *
     * @param mediaItemIndex The index of the media item.
     * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
     *     exceeded.
     */
    public void untilStartOfMediaItem(int mediaItemIndex) throws Exception {
      untilPosition(mediaItemIndex, /* positionMs= */ 0);
    }

    /**
     * Runs tasks of the main {@link Looper} until the player completely handled all previously
     * issued commands on the internal playback thread.
     *
     * <p>Both fatal and non-fatal errors are always ignored.
     *
     * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
     *     exceeded.
     */
    public void untilPendingCommandsAreFullyHandled() throws Exception {
      checkState(!hasBeenUsed);
      hasBeenUsed = true;
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

    @Override
    public ExoPlayerRunResult ignoringNonFatalErrors() {
      checkState(!hasBeenUsed);
      hasBeenUsed = true;
      return new ExoPlayerRunResult(player, playBeforeWaiting, /* throwNonFatalErrors= */ false);
    }
  }

  /**
   * Entry point for a fluent "wait for condition X" assertion.
   *
   * <p>Callers can use the returned {@link PlayerRunResult} to run the main {@link Looper} until
   * certain conditions are met.
   */
  public static PlayerRunResult run(Player player) {
    return new PlayerRunResult(
        player, /* playBeforeWaiting= */ false, /* throwNonFatalErrors= */ true);
  }

  /**
   * Entry point for a fluent "wait for condition X" assertion.
   *
   * <p>Callers can use the returned {@link ExoPlayerRunResult} to run the main {@link Looper} until
   * certain conditions are met.
   */
  public static ExoPlayerRunResult run(ExoPlayer player) {
    return new ExoPlayerRunResult(
        player, /* playBeforeWaiting= */ false, /* throwNonFatalErrors= */ true);
  }

  /**
   * Entry point for a fluent "start playback and wait for condition X" assertion.
   *
   * <p>Callers can use the returned {@link PlayerRunResult} to run the main {@link Looper} until
   * certain conditions are met.
   *
   * <p>This is the same as {@link #run(Player)} but ensures {@link Player#play()} is called before
   * waiting in subsequent {@code untilXXX(...)} methods.
   */
  public static PlayerRunResult play(Player player) {
    return new PlayerRunResult(
        player, /* playBeforeWaiting= */ true, /* throwNonFatalErrors= */ true);
  }

  /**
   * Entry point for a fluent "start playback and wait for condition X" assertion.
   *
   * <p>Callers can use the returned {@link ExoPlayerRunResult} to run the main {@link Looper} until
   * certain conditions are met.
   *
   * <p>This is the same as {@link #run(ExoPlayer)} but ensures {@link ExoPlayer#play()} is called
   * before waiting in subsequent {@code untilXXX(...)} methods.
   */
  public static ExoPlayerRunResult play(ExoPlayer player) {
    return new ExoPlayerRunResult(
        player, /* playBeforeWaiting= */ true, /* throwNonFatalErrors= */ true);
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getPlaybackState()} matches the
   * expected state or an error occurs.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(Player)} and {@link PlayerRunResult#untilState(int)}.
   *
   * @param player The {@link Player}.
   * @param expectedState The expected {@link Player.State}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPlaybackState(Player player, @Player.State int expectedState)
      throws TimeoutException {
    try {
      run(player).untilState(expectedState);
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getPlayWhenReady()} matches the
   * expected value or an error occurs.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(Player)} and {@link
   * PlayerRunResult#untilPlayWhenReadyIs(boolean)}.
   *
   * @param player The {@link Player}.
   * @param expectedPlayWhenReady The expected value for {@link Player#getPlayWhenReady()}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPlayWhenReady(Player player, boolean expectedPlayWhenReady)
      throws TimeoutException {
    try {
      run(player).untilPlayWhenReadyIs(expectedPlayWhenReady);
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#isLoading()} matches the expected
   * value or an error occurs.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(Player)} and {@link
   * PlayerRunResult#untilLoadingIs(boolean)}.
   *
   * @param player The {@link Player}.
   * @param expectedIsLoading The expected value for {@link Player#isLoading()}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilIsLoading(Player player, boolean expectedIsLoading)
      throws TimeoutException {
    try {
      run(player).untilLoadingIs(expectedIsLoading);
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getCurrentTimeline()} matches the
   * expected timeline or an error occurs.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(Player)} and {@link
   * PlayerRunResult#untilTimelineChangesTo(Timeline)}.
   *
   * @param player The {@link Player}.
   * @param expectedTimeline The expected {@link Timeline}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilTimelineChanged(Player player, Timeline expectedTimeline)
      throws TimeoutException {
    try {
      run(player).untilTimelineChangesTo(expectedTimeline);
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until a timeline change or an error occurs.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(Player)} and {@link
   * PlayerRunResult#untilTimelineChanges()}.
   *
   * @param player The {@link Player}.
   * @return The new {@link Timeline}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static Timeline runUntilTimelineChanged(Player player) throws TimeoutException {
    try {
      return run(player).untilTimelineChanges();
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link
   * Player.Listener#onPositionDiscontinuity(Player.PositionInfo, Player.PositionInfo, int)} is
   * called with the specified {@link Player.DiscontinuityReason} or an error occurs.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(Player)} and {@link
   * PlayerRunResult#untilPositionDiscontinuityWithReason(int)}.
   *
   * @param player The {@link Player}.
   * @param expectedReason The expected {@link Player.DiscontinuityReason}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPositionDiscontinuity(
      Player player, @Player.DiscontinuityReason int expectedReason) throws TimeoutException {
    try {
      run(player).untilPositionDiscontinuityWithReason(expectedReason);
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until a player error occurs.
   *
   * <p>Non-fatal errors are ignored.
   *
   * <p>New usages should prefer {@link #run(ExoPlayer)} and {@link
   * ExoPlayerRunResult#untilPlayerError()}.
   *
   * @param player The {@link Player}.
   * @return The raised {@link ExoPlaybackException}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static ExoPlaybackException runUntilError(ExoPlayer player) throws TimeoutException {
    return run(player).untilPlayerError();
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link ExoPlayer#isSleepingForOffload()} matches
   * the expected value, or an error occurs.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(ExoPlayer)} and {@link
   * ExoPlayerRunResult#untilSleepingForOffloadBecomes(boolean)}.
   *
   * @param player The {@link Player}.
   * @param expectedSleepForOffload The expected sleep of offload state.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilSleepingForOffload(ExoPlayer player, boolean expectedSleepForOffload)
      throws TimeoutException {
    try {
      run(player).untilSleepingForOffloadBecomes(expectedSleepForOffload);
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player.Listener#onRenderedFirstFrame} is
   * called or an error occurs.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(Player)} and {@link
   * PlayerRunResult#untilFirstFrameIsRendered()}.
   *
   * @param player The {@link Player}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilRenderedFirstFrame(ExoPlayer player) throws TimeoutException {
    try {
      run(player).untilFirstFrameIsRendered();
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Calls {@link Player#play()} then runs tasks of the main {@link Looper} until the {@code player}
   * reaches the specified position or an error occurs.
   *
   * <p>The playback thread is automatically blocked from making further progress after reaching
   * this position and will only be unblocked by other {@code runUntil/playUntil...} methods, custom
   * {@link RobolectricUtil#runMainLooperUntil} conditions or an explicit {@link
   * ThreadTestUtil#unblockThreadsWaitingForProgressOnCurrentLooper()} on the main thread.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(ExoPlayer)} and {@link
   * ExoPlayerRunResult#untilPosition(int, long)}.
   *
   * @param player The {@link Player}.
   * @param mediaItemIndex The index of the media item.
   * @param positionMs The position within the media item, in milliseconds.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void playUntilPosition(ExoPlayer player, int mediaItemIndex, long positionMs)
      throws TimeoutException {
    try {
      play(player).untilPosition(mediaItemIndex, positionMs);
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Calls {@link Player#play()} then runs tasks of the main {@link Looper} until the {@code player}
   * reaches the specified media item or a playback error occurs.
   *
   * <p>The playback thread is automatically blocked from making further progress after reaching the
   * media item and will only be unblocked by other {@code runUntil/playUntil...} methods, custom
   * {@link RobolectricUtil#runMainLooperUntil} conditions or an explicit {@link
   * ThreadTestUtil#unblockThreadsWaitingForProgressOnCurrentLooper()} on the main thread.
   *
   * <p>If a checked exception occurs it will be thrown wrapped in an {@link IllegalStateException}.
   *
   * <p>New usages should prefer {@link #run(ExoPlayer)} and {@link
   * ExoPlayerRunResult#untilStartOfMediaItem(int)}.
   *
   * @param player The {@link Player}.
   * @param mediaItemIndex The index of the media item.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void playUntilStartOfMediaItem(ExoPlayer player, int mediaItemIndex)
      throws TimeoutException {
    try {
      play(player).untilStartOfMediaItem(mediaItemIndex);
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs tasks of the main {@link Looper} until the player completely handled all previously issued
   * commands on the internal playback thread.
   *
   * <p>Both fatal and non-fatal errors are ignored.
   *
   * @param player The {@link Player}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPendingCommandsAreFullyHandled(ExoPlayer player)
      throws TimeoutException {
    try {
      run(player).untilPendingCommandsAreFullyHandled();
    } catch (RuntimeException | TimeoutException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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

  /**
   * A {@link Player.Listener} and {@link AnalyticsListener} that records errors.
   *
   * <p>All methods must be called on {@link Player#getApplicationLooper()}.
   */
  private static final class ErrorListener implements AnalyticsListener, Player.Listener {

    @Nullable private final List<Exception> nonFatalErrors;
    private @MonotonicNonNull Exception fatalError;

    public ErrorListener(boolean throwNonFatalErrors) {
      if (throwNonFatalErrors) {
        nonFatalErrors = new ArrayList<>();
      } else {
        nonFatalErrors = null;
      }
    }

    public boolean hasFatalError() {
      return fatalError != null;
    }

    public void maybeThrow() throws Exception {
      if (fatalError != null) {
        throw fatalError;
      }
      if (nonFatalErrors != null && !nonFatalErrors.isEmpty()) {
        IllegalStateException ise =
            new IllegalStateException(
                "Non-fatal errors detected. Attach an EventLogger and redirect logcat with"
                    + " ShadowLog.stream to see full details.");
        for (Exception nonFatalError : nonFatalErrors) {
          ise.addSuppressed(nonFatalError);
        }
        throw ise;
      }
    }

    // Player.Listener impl

    @Override
    public void onPlayerError(PlaybackException error) {
      fatalError = error;
    }

    // AnalyticsListener impl

    @Override
    public void onLoadError(
        EventTime eventTime,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      if (nonFatalErrors != null) {
        nonFatalErrors.add(error);
      }
    }

    @Override
    public void onAudioSinkError(EventTime eventTime, Exception audioSinkError) {
      if (nonFatalErrors != null) {
        nonFatalErrors.add(audioSinkError);
      }
    }

    @Override
    public void onAudioCodecError(EventTime eventTime, Exception audioCodecError) {
      if (nonFatalErrors != null) {
        nonFatalErrors.add(audioCodecError);
      }
    }

    @Override
    public void onVideoCodecError(EventTime eventTime, Exception videoCodecError) {
      if (nonFatalErrors != null) {
        nonFatalErrors.add(videoCodecError);
      }
    }

    @Override
    public void onDrmSessionManagerError(EventTime eventTime, Exception error) {
      if (nonFatalErrors != null) {
        nonFatalErrors.add(error);
      }
    }
  }
}
