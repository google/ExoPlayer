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

package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.testutil.TestUtil.runMainLooperUntil;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Utilities to write unit/integration tests with a SimpleExoPlayer instance that uses fake
 * components.
 */
public class TestExoPlayer {

  /** A builder of {@link SimpleExoPlayer} instances for testing. */
  public static class Builder {

    private final Context context;
    private Clock clock;
    private DefaultTrackSelector trackSelector;
    private LoadControl loadControl;
    private BandwidthMeter bandwidthMeter;
    @Nullable private Renderer[] renderers;
    @Nullable private RenderersFactory renderersFactory;
    private boolean useLazyPreparation;
    private @MonotonicNonNull Looper looper;

    public Builder(Context context) {
      this.context = context;
      clock = new AutoAdvancingFakeClock();
      trackSelector = new DefaultTrackSelector(context);
      loadControl = new DefaultLoadControl();
      bandwidthMeter = new DefaultBandwidthMeter.Builder(context).build();
      @Nullable Looper myLooper = Looper.myLooper();
      if (myLooper != null) {
        looper = myLooper;
      }
    }

    /**
     * Sets whether to use lazy preparation.
     *
     * @param useLazyPreparation Whether to use lazy preparation.
     * @return This builder.
     */
    public Builder setUseLazyPreparation(boolean useLazyPreparation) {
      this.useLazyPreparation = useLazyPreparation;
      return this;
    }

    /** Returns whether the player will use lazy preparation. */
    public boolean getUseLazyPreparation() {
      return useLazyPreparation;
    }

    /**
     * Sets a {@link DefaultTrackSelector}. The default value is a {@link DefaultTrackSelector} in
     * its initial configuration.
     *
     * @param trackSelector The {@link DefaultTrackSelector} to be used by the player.
     * @return This builder.
     */
    public Builder setTrackSelector(DefaultTrackSelector trackSelector) {
      Assertions.checkNotNull(trackSelector);
      this.trackSelector = trackSelector;
      return this;
    }

    /** Returns the track selector used by the player. */
    public DefaultTrackSelector getTrackSelector() {
      return trackSelector;
    }

    /**
     * Sets a {@link LoadControl} to be used by the player. The default value is a {@link
     * DefaultLoadControl}.
     *
     * @param loadControl The {@link LoadControl} to be used by the player.
     * @return This builder.
     */
    public Builder setLoadControl(LoadControl loadControl) {
      this.loadControl = loadControl;
      return this;
    }

    /** Returns the {@link LoadControl} that will be used by the player. */
    public LoadControl getLoadControl() {
      return loadControl;
    }

    /**
     * Sets the {@link BandwidthMeter}. The default value is a {@link DefaultBandwidthMeter} in its
     * default configuration.
     *
     * @param bandwidthMeter The {@link BandwidthMeter} to be used by the player.
     * @return This builder.
     */
    public Builder setBandwidthMeter(BandwidthMeter bandwidthMeter) {
      Assertions.checkNotNull(bandwidthMeter);
      this.bandwidthMeter = bandwidthMeter;
      return this;
    }

    /** Returns the bandwidth meter used by the player. */
    public BandwidthMeter getBandwidthMeter() {
      return bandwidthMeter;
    }

    /**
     * Sets the {@link Renderer}s. If not set, the player will use a {@link FakeVideoRenderer} and a
     * {@link FakeAudioRenderer}. Setting the renderers is not allowed after a call to {@link
     * #setRenderersFactory(RenderersFactory)}.
     *
     * @param renderers A list of {@link Renderer}s to be used by the player.
     * @return This builder.
     */
    public Builder setRenderers(Renderer... renderers) {
      assertThat(renderersFactory).isNull();
      this.renderers = renderers;
      return this;
    }

    /**
     * Returns the {@link Renderer Renderers} that have been set with {@link #setRenderers} or null
     * if no {@link Renderer Renderers} have been explicitly set. Note that these renderers may not
     * be the ones used by the built player, for example if a {@link #setRenderersFactory Renderer
     * factory} has been set.
     */
    @Nullable
    public Renderer[] getRenderers() {
      return renderers;
    }

    /**
     * Sets the {@link RenderersFactory}. The default factory creates all renderers set by {@link
     * #setRenderers(Renderer...)}. Setting the renderer factory is not allowed after a call to
     * {@link #setRenderers(Renderer...)}.
     *
     * @param renderersFactory A {@link RenderersFactory} to be used by the player.
     * @return This builder.
     */
    public Builder setRenderersFactory(RenderersFactory renderersFactory) {
      assertThat(renderers).isNull();
      this.renderersFactory = renderersFactory;
      return this;
    }

    /**
     * Returns the {@link RenderersFactory} that has been set with {@link #setRenderersFactory} or
     * null if no factory has been explicitly set.
     */
    @Nullable
    public RenderersFactory getRenderersFactory() {
      return renderersFactory;
    }

    /**
     * Sets the {@link Clock} to be used by the player. The default value is a {@link
     * AutoAdvancingFakeClock}.
     *
     * @param clock A {@link Clock} to be used by the player.
     * @return This builder.
     */
    public Builder setClock(Clock clock) {
      assertThat(clock).isNotNull();
      this.clock = clock;
      return this;
    }

    /** Returns the clock used by the player. */
    public Clock getClock() {
      return clock;
    }

    /**
     * Sets the {@link Looper} to be used by the player.
     *
     * @param looper The {@link Looper} to be used by the player.
     * @return This builder.
     */
    public Builder setLooper(Looper looper) {
      this.looper = looper;
      return this;
    }

    /**
     * Returns the {@link Looper} that will be used by the player, or null if no {@link Looper} has
     * been set yet and no default is available.
     */
    @Nullable
    public Looper getLooper() {
      return looper;
    }

    /**
     * Builds an {@link SimpleExoPlayer} using the provided values or their defaults.
     *
     * @return The built {@link ExoPlayerTestRunner}.
     */
    public SimpleExoPlayer build() {
      Assertions.checkNotNull(
          looper, "TestExoPlayer builder run on a thread without Looper and no Looper specified.");
      // Do not update renderersFactory and renderers here, otherwise their getters may
      // return different values before and after build() is called, making them confusing.
      RenderersFactory playerRenderersFactory = renderersFactory;
      if (playerRenderersFactory == null) {
        playerRenderersFactory =
            (eventHandler,
                videoRendererEventListener,
                audioRendererEventListener,
                textRendererOutput,
                metadataRendererOutput) ->
                renderers != null
                    ? renderers
                    : new Renderer[] {
                      new FakeVideoRenderer(eventHandler, videoRendererEventListener),
                      new FakeAudioRenderer(eventHandler, audioRendererEventListener)
                    };
      }

      return new SimpleExoPlayer.Builder(context, playerRenderersFactory)
          .setTrackSelector(trackSelector)
          .setLoadControl(loadControl)
          .setBandwidthMeter(bandwidthMeter)
          .setAnalyticsCollector(new AnalyticsCollector(clock))
          .setClock(clock)
          .setUseLazyPreparation(useLazyPreparation)
          .setLooper(looper)
          .build();
    }
  }

  private TestExoPlayer() {}

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getPlaybackState()} matches the
   * expected state.
   *
   * @param player The {@link Player}.
   * @param expectedState The expected {@link Player.State}.
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPlaybackState(Player player, @Player.State int expectedState)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (player.getPlaybackState() == expectedState) {
      return;
    }
    AtomicBoolean receivedExpectedState = new AtomicBoolean(false);
    Player.EventListener listener =
        new Player.EventListener() {
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
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPlayWhenReady(Player player, boolean expectedPlayWhenReady)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (player.getPlayWhenReady() == expectedPlayWhenReady) {
      return;
    }
    AtomicBoolean receivedExpectedPlayWhenReady = new AtomicBoolean(false);
    Player.EventListener listener =
        new Player.EventListener() {
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
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilTimelineChanged(Player player, Timeline expectedTimeline)
      throws TimeoutException {
    verifyMainTestThread(player);
    if (expectedTimeline.equals(player.getCurrentTimeline())) {
      return;
    }
    AtomicBoolean receivedExpectedTimeline = new AtomicBoolean(false);
    Player.EventListener listener =
        new Player.EventListener() {
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
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static Timeline runUntilTimelineChanged(Player player) throws TimeoutException {
    verifyMainTestThread(player);
    AtomicReference<Timeline> receivedTimeline = new AtomicReference<>();
    Player.EventListener listener =
        new Player.EventListener() {
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
   * Player.EventListener#onPositionDiscontinuity} callback with the specified {@link
   * Player.DiscontinuityReason} occurred.
   *
   * @param player The {@link Player}.
   * @param expectedReason The expected {@link Player.DiscontinuityReason}.
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilPositionDiscontinuity(
      Player player, @Player.DiscontinuityReason int expectedReason) throws TimeoutException {
    verifyMainTestThread(player);
    AtomicBoolean receivedCallback = new AtomicBoolean(false);
    Player.EventListener listener =
        new Player.EventListener() {
          @Override
          public void onPositionDiscontinuity(int reason) {
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
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static ExoPlaybackException runUntilError(Player player) throws TimeoutException {
    verifyMainTestThread(player);
    AtomicReference<ExoPlaybackException> receivedError = new AtomicReference<>();
    Player.EventListener listener =
        new Player.EventListener() {
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
   * Player.EventListener#onExperimentalOffloadSchedulingEnabledChanged} callback occurred.
   *
   * @param player The {@link Player}.
   * @return The new offloadSchedulingEnabled state.
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static boolean runUntilReceiveOffloadSchedulingEnabledNewState(Player player)
      throws TimeoutException {
    verifyMainTestThread(player);
    AtomicReference<@NullableType Boolean> offloadSchedulingEnabledReceiver =
        new AtomicReference<>();
    Player.EventListener listener =
        new Player.EventListener() {
          @Override
          public void onExperimentalOffloadSchedulingEnabledChanged(
              boolean offloadSchedulingEnabled) {
            offloadSchedulingEnabledReceiver.set(offloadSchedulingEnabled);
          }
        };
    player.addListener(listener);
    runMainLooperUntil(() -> offloadSchedulingEnabledReceiver.get() != null);
    return Assertions.checkNotNull(offloadSchedulingEnabledReceiver.get());
  }

  /**
   * Runs tasks of the main {@link Looper} until the {@link VideoListener#onRenderedFirstFrame}
   * callback has been called.
   *
   * @param player The {@link Player}.
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void runUntilRenderedFirstFrame(SimpleExoPlayer player) throws TimeoutException {
    verifyMainTestThread(player);
    AtomicBoolean receivedCallback = new AtomicBoolean(false);
    VideoListener listener =
        new VideoListener() {
          @Override
          public void onRenderedFirstFrame() {
            receivedCallback.set(true);
            player.removeVideoListener(this);
          }
        };
    player.addVideoListener(listener);
    runMainLooperUntil(receivedCallback::get);
  }

  /**
   * Calls {@link Player#play()}, runs tasks of the main {@link Looper} until the {@code player}
   * reaches the specified position and then pauses the {@code player}.
   *
   * @param player The {@link Player}.
   * @param windowIndex The window.
   * @param positionMs The position within the window, in milliseconds.
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   */
  public static void playUntilPosition(ExoPlayer player, int windowIndex, long positionMs)
      throws TimeoutException {
    verifyMainTestThread(player);
    Handler testHandler = Util.createHandlerForCurrentOrMainLooper();

    AtomicBoolean messageHandled = new AtomicBoolean();
    player
        .createMessage(
            (messageType, payload) -> {
              // Block playback thread until pause command has been sent from test thread.
              ConditionVariable blockPlaybackThreadCondition = new ConditionVariable();
              testHandler.post(
                  () -> {
                    player.pause();
                    messageHandled.set(true);
                    blockPlaybackThreadCondition.open();
                  });
              try {
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
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
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
   * @throws TimeoutException If the {@link TestUtil#DEFAULT_TIMEOUT_MS default timeout} is
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
        .setHandler(Util.createHandlerForCurrentOrMainLooper())
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
