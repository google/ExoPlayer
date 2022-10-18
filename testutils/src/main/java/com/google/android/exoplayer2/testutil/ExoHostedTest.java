/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertWithMessage;

import android.os.ConditionVariable;
import android.os.SystemClock;
import android.view.Surface;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.HostActivity.HostedTest;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link HostedTest} for {@link ExoPlayer} playback tests. */
public abstract class ExoHostedTest implements AnalyticsListener, HostedTest {

  static {
    // DefaultAudioSink is able to work around spurious timestamps reported by the platform (by
    // ignoring them). Disable this workaround, since we're interested in testing that the
    // underlying platform is behaving correctly.
    DefaultAudioSink.failOnSpuriousAudioTimestamp = true;
  }

  public static final long MAX_PLAYING_TIME_DISCREPANCY_MS = 5000;
  public static final long EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS = -2;
  public static final long EXPECTED_PLAYING_TIME_UNSET = -1;

  protected final String tag;

  private final boolean failOnPlayerError;
  private final long expectedPlayingTimeMs;
  private final DecoderCounters videoDecoderCounters;
  private final DecoderCounters audioDecoderCounters;
  private final ConditionVariable testFinished;

  @Nullable private ActionSchedule pendingSchedule;
  private @MonotonicNonNull ExoPlayer player;
  private @MonotonicNonNull HandlerWrapper actionHandler;
  private @MonotonicNonNull DefaultTrackSelector trackSelector;
  private @MonotonicNonNull Surface surface;
  private @MonotonicNonNull ExoPlaybackException playerError;

  private long totalPlayingTimeMs;
  private long lastPlayingStartTimeMs;
  private long sourceDurationMs;

  /**
   * @param tag A tag to use for logging.
   * @param fullPlaybackNoSeeking Whether the test will play the target media in full without
   *     seeking. If set to true, the test will assert that the total time spent playing the media
   *     was within {@link #MAX_PLAYING_TIME_DISCREPANCY_MS} of the media duration. If set to false,
   *     the test will not assert an expected playing time.
   */
  public ExoHostedTest(@Size(max = 23) String tag, boolean fullPlaybackNoSeeking) {
    this(
        tag,
        fullPlaybackNoSeeking
            ? EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS
            : EXPECTED_PLAYING_TIME_UNSET,
        true);
  }

  /**
   * @param tag A tag to use for logging.
   * @param expectedPlayingTimeMs The expected playing time. If set to a non-negative value, the
   *     test will assert that the total time spent playing the media was within {@link
   *     #MAX_PLAYING_TIME_DISCREPANCY_MS} of the specified value. {@link
   *     #EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS} should be passed to assert that the expected
   *     playing time equals the duration of the media being played. Else {@link
   *     #EXPECTED_PLAYING_TIME_UNSET} should be passed to indicate that the test should not assert
   *     an expected playing time.
   * @param failOnPlayerError Whether a player error should be considered a test failure.
   */
  public ExoHostedTest(
      @Size(max = 23) String tag, long expectedPlayingTimeMs, boolean failOnPlayerError) {
    this.tag = tag;
    this.expectedPlayingTimeMs = expectedPlayingTimeMs;
    this.failOnPlayerError = failOnPlayerError;
    this.testFinished = new ConditionVariable();
    this.videoDecoderCounters = new DecoderCounters();
    this.audioDecoderCounters = new DecoderCounters();
  }

  /**
   * Sets a schedule to be applied during the test.
   *
   * @param schedule The schedule.
   */
  public final void setSchedule(ActionSchedule schedule) {
    if (!isStarted()) {
      pendingSchedule = schedule;
    } else {
      schedule.start(player, trackSelector, surface, actionHandler, /* callback= */ null);
    }
  }

  // HostedTest implementation

  @Override
  public final void onStart(HostActivity host, Surface surface, FrameLayout overlayFrameLayout) {
    this.surface = surface;
    // Build the player.
    trackSelector = buildTrackSelector(host);
    player = buildExoPlayer(host, surface, trackSelector);
    player.play();
    player.addAnalyticsListener(this);
    player.addAnalyticsListener(new EventLogger(tag));
    // Schedule any pending actions.
    actionHandler =
        Clock.DEFAULT.createHandler(Util.getCurrentOrMainLooper(), /* callback= */ null);
    if (pendingSchedule != null) {
      pendingSchedule.start(player, trackSelector, surface, actionHandler, /* callback= */ null);
      pendingSchedule = null;
    }
    DrmSessionManager drmSessionManager = buildDrmSessionManager();
    player.setMediaSource(buildSource(host, drmSessionManager, overlayFrameLayout));
    player.prepare();
  }

  @Override
  public final boolean blockUntilStopped(long timeoutMs) {
    return testFinished.block(timeoutMs);
  }

  @Override
  public final boolean forceStop() {
    return stopTest();
  }

  @Override
  public final void onFinished() {
    if (failOnPlayerError && playerError != null) {
      throw new RuntimeException(playerError);
    }
    logMetrics(audioDecoderCounters, videoDecoderCounters);
    if (expectedPlayingTimeMs != EXPECTED_PLAYING_TIME_UNSET) {
      long playingTimeToAssertMs =
          expectedPlayingTimeMs == EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS
              ? sourceDurationMs
              : expectedPlayingTimeMs;
      // Assert that the playback spanned the correct duration of time.
      long minAllowedActualPlayingTimeMs = playingTimeToAssertMs - MAX_PLAYING_TIME_DISCREPANCY_MS;
      long maxAllowedActualPlayingTimeMs = playingTimeToAssertMs + MAX_PLAYING_TIME_DISCREPANCY_MS;
      assertWithMessage(
              "Total playing time: %sms. Expected: %sms", totalPlayingTimeMs, playingTimeToAssertMs)
          .that(
              minAllowedActualPlayingTimeMs <= totalPlayingTimeMs
                  && totalPlayingTimeMs <= maxAllowedActualPlayingTimeMs)
          .isTrue();
    }
    // Make any additional assertions.
    assertPassed(audioDecoderCounters, videoDecoderCounters);
  }

  // AnalyticsListener

  @Override
  public void onEvents(Player player, Events events) {
    if (events.contains(EVENT_IS_PLAYING_CHANGED)) {
      if (player.isPlaying()) {
        lastPlayingStartTimeMs = SystemClock.elapsedRealtime();
      } else {
        totalPlayingTimeMs += SystemClock.elapsedRealtime() - lastPlayingStartTimeMs;
      }
    }
    if (events.contains(EVENT_PLAYER_ERROR)) {
      // The exception is guaranteed to be an ExoPlaybackException because the underlying player is
      // an ExoPlayer instance.
      playerError = (ExoPlaybackException) checkNotNull(player.getPlayerError());
      onPlayerErrorInternal(playerError);
    }
    if (events.contains(EVENT_PLAYBACK_STATE_CHANGED)) {
      @Player.State int playbackState = player.getPlaybackState();
      if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
        stopTest();
      }
    }
  }

  @Override
  public void onAudioDisabled(EventTime eventTime, DecoderCounters decoderCounters) {
    audioDecoderCounters.merge(decoderCounters);
  }

  @Override
  public void onVideoDisabled(EventTime eventTime, DecoderCounters decoderCounters) {
    videoDecoderCounters.merge(decoderCounters);
  }

  // Internal logic

  private boolean stopTest() {
    if (!isStarted()) {
      return false;
    }
    actionHandler.removeCallbacksAndMessages(null);
    sourceDurationMs = player.getDuration();
    player.release();
    // We post opening of the finished condition so that any events posted to the main thread as a
    // result of player.release() are guaranteed to be handled before the test returns.
    actionHandler.post(testFinished::open);
    return true;
  }

  protected DrmSessionManager buildDrmSessionManager() {
    // Do nothing. Interested subclasses may override.
    return DrmSessionManager.DRM_UNSUPPORTED;
  }

  protected DefaultTrackSelector buildTrackSelector(HostActivity host) {
    return new DefaultTrackSelector(host);
  }

  protected ExoPlayer buildExoPlayer(
      HostActivity host, Surface surface, MappingTrackSelector trackSelector) {
    DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(host);
    renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
    renderersFactory.setAllowedVideoJoiningTimeMs(/* allowedVideoJoiningTimeMs= */ 0);
    ExoPlayer player =
        new ExoPlayer.Builder(host, renderersFactory).setTrackSelector(trackSelector).build();
    player.setVideoSurface(surface);
    return player;
  }

  protected abstract MediaSource buildSource(
      HostActivity host, DrmSessionManager drmSessionManager, FrameLayout overlayFrameLayout);

  protected void onPlayerErrorInternal(ExoPlaybackException error) {
    // Do nothing. Interested subclasses may override.
  }

  protected void logMetrics(DecoderCounters audioCounters, DecoderCounters videoCounters) {
    // Do nothing. Subclasses may override to log metrics.
  }

  protected void assertPassed(DecoderCounters audioCounters, DecoderCounters videoCounters) {
    // Do nothing. Subclasses may override to add additional assertions.
  }

  @EnsuresNonNullIf(
      result = true,
      expression = {"player", "actionHandler", "trackSelector", "surface"})
  private boolean isStarted() {
    if (player == null) {
      return false;
    }
    Util.castNonNull(actionHandler);
    Util.castNonNull(trackSelector);
    Util.castNonNull(surface);
    return true;
  }
}
