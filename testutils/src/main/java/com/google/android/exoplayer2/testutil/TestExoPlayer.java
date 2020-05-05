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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
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
import com.google.android.exoplayer2.util.Supplier;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Utilities to write unit/integration tests with a SimpleExoPlayer instance that uses fake
 * components.
 */
public class TestExoPlayer {

  /**
   * The default timeout applied when calling one of the {@code runUntil} methods. This timeout
   * should be sufficient for any condition using a Robolectric test.
   */
  public static final long DEFAULT_TIMEOUT_MS = 10_000;

  /** Reflectively call Robolectric ShadowLooper#runOneTask. */
  private static final Object shadowLooper;

  private static final Method runOneTaskMethod;

  static {
    try {
      Class<?> clazz = Class.forName("org.robolectric.Shadows");
      Method shadowOfMethod =
          Assertions.checkNotNull(clazz.getDeclaredMethod("shadowOf", Looper.class));
      shadowLooper =
          Assertions.checkNotNull(shadowOfMethod.invoke(new Object(), Looper.getMainLooper()));
      runOneTaskMethod = shadowLooper.getClass().getDeclaredMethod("runOneTask");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

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
    private boolean throwWhenStuckBuffering;
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
     * Sets whether the player should throw when it detects it's stuck buffering.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     *
     * @param throwWhenStuckBuffering Whether to throw when the player detects it's stuck buffering.
     * @return This builder.
     */
    public Builder experimental_setThrowWhenStuckBuffering(boolean throwWhenStuckBuffering) {
      this.throwWhenStuckBuffering = throwWhenStuckBuffering;
      return this;
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
          .experimental_setThrowWhenStuckBuffering(throwWhenStuckBuffering)
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
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS default timeout} is exceeded.
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
    runUntil(receivedExpectedState::get);
    player.removeListener(listener);
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getPlayWhenReady()} matches the
   * expected value.
   *
   * @param player The {@link Player}.
   * @param expectedPlayWhenReady The expected value for {@link Player#getPlayWhenReady()}.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS default timeout} is exceeded.
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
    runUntil(receivedExpectedPlayWhenReady::get);
  }

  /**
   * Runs tasks of the main {@link Looper} until {@link Player#getCurrentTimeline()} matches the
   * expected timeline.
   *
   * @param player The {@link Player}.
   * @param expectedTimeline The expected {@link Timeline}.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS default timeout} is exceeded.
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
    runUntil(receivedExpectedTimeline::get);
  }

  /**
   * Runs tasks of the main {@link Looper} until a timeline change occurred.
   *
   * @param player The {@link Player}.
   * @return The new {@link Timeline}.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS default timeout} is exceeded.
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
    runUntil(() -> receivedTimeline.get() != null);
    return receivedTimeline.get();
  }

  /**
   * Runs tasks of the main {@link Looper} until a {@link
   * Player.EventListener#onPositionDiscontinuity} callback with the specified {@link
   * Player.DiscontinuityReason} occurred.
   *
   * @param player The {@link Player}.
   * @param expectedReason The expected {@link Player.DiscontinuityReason}.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS default timeout} is exceeded.
   */
  public static void runUntilPositionDiscontinuity(
      Player player, @Player.DiscontinuityReason int expectedReason) throws TimeoutException {
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
    runUntil(receivedCallback::get);
  }

  /**
   * Runs tasks of the main {@link Looper} until a player error occurred.
   *
   * @param player The {@link Player}.
   * @return The raised {@link ExoPlaybackException}.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS default timeout} is exceeded.
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
    runUntil(() -> receivedError.get() != null);
    return receivedError.get();
  }

  /**
   * Runs tasks of the main {@link Looper} until the {@link VideoListener#onRenderedFirstFrame}
   * callback has been called.
   *
   * @param player The {@link Player}.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS default timeout} is exceeded.
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
    runUntil(receivedCallback::get);
  }

  /**
   * Runs tasks of the main {@link Looper} until the player completely handled all previously issued
   * commands on the internal playback thread.
   *
   * @param player The {@link Player}.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS default timeout} is exceeded.
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
        .setHandler(Util.createHandler())
        .send();
    runUntil(receivedMessageCallback::get);
  }

  /**
   * Runs tasks of the main {@link Looper} until the {@code condition} returns {@code true}.
   *
   * @param condition The condition.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS} is exceeded.
   */
  public static void runUntil(Supplier<Boolean> condition) throws TimeoutException {
    runUntil(condition, DEFAULT_TIMEOUT_MS, Clock.DEFAULT);
  }

  /**
   * Runs tasks of the main {@link Looper} until the {@code condition} returns {@code true}.
   *
   * @param condition The condition.
   * @param timeoutMs The timeout in milliseconds.
   * @param clock The {@link Clock} to measure the timeout.
   * @throws TimeoutException If the {@code timeoutMs timeout} is exceeded.
   */
  public static void runUntil(Supplier<Boolean> condition, long timeoutMs, Clock clock)
      throws TimeoutException {
    verifyMainTestThread();
    try {
      long timeoutTimeMs = clock.currentTimeMillis() + timeoutMs;
      while (!condition.get()) {
        if (clock.currentTimeMillis() >= timeoutTimeMs) {
          throw new TimeoutException();
        }
        runOneTaskMethod.invoke(shadowLooper);
      }
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void verifyMainTestThread(Player player) {
    if (Looper.myLooper() != Looper.getMainLooper()
        || player.getApplicationLooper() != Looper.getMainLooper()) {
      throw new IllegalStateException();
    }
  }

  private static void verifyMainTestThread() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      throw new IllegalStateException();
    }
  }
}
