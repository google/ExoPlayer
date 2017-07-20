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

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioTrack;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.HostActivity.HostedTest;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import junit.framework.Assert;

/**
 * A {@link HostedTest} for {@link ExoPlayer} playback tests.
 */
public abstract class ExoHostedTest implements HostedTest, Player.EventListener,
    AudioRendererEventListener, VideoRendererEventListener {

  static {
    // ExoPlayer's AudioTrack class is able to work around spurious timestamps reported by the
    // platform (by ignoring them). Disable this workaround, since we're interested in testing
    // that the underlying platform is behaving correctly.
    AudioTrack.failOnSpuriousAudioTimestamp = true;
  }

  public static final long MAX_PLAYING_TIME_DISCREPANCY_MS = 2000;
  public static final long EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS = -2;
  public static final long EXPECTED_PLAYING_TIME_UNSET = -1;

  protected final String tag;

  private final boolean failOnPlayerError;
  private final long expectedPlayingTimeMs;
  private final DecoderCounters videoDecoderCounters;
  private final DecoderCounters audioDecoderCounters;

  private ActionSchedule pendingSchedule;
  private Handler actionHandler;
  private MappingTrackSelector trackSelector;
  private SimpleExoPlayer player;
  private Surface surface;
  private ExoPlaybackException playerError;
  private Player.EventListener playerEventListener;
  private boolean playerWasPrepared;
  private boolean playerFinished;
  private boolean playing;
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
  public ExoHostedTest(String tag, boolean fullPlaybackNoSeeking) {
    this(tag, fullPlaybackNoSeeking ? EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS
        : EXPECTED_PLAYING_TIME_UNSET, true);
  }

  /**
   * @param tag A tag to use for logging.
   * @param expectedPlayingTimeMs The expected playing time. If set to a non-negative value, the
   *     test will assert that the total time spent playing the media was within
   *     {@link #MAX_PLAYING_TIME_DISCREPANCY_MS} of the specified value.
   *     {@link #EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS} should be passed to assert that the
   *     expected playing time equals the duration of the media being played. Else
   *     {@link #EXPECTED_PLAYING_TIME_UNSET} should be passed to indicate that the test should not
   *     assert an expected playing time.
   * @param failOnPlayerError Whether a player error should be considered a test failure.
   */
  public ExoHostedTest(String tag, long expectedPlayingTimeMs, boolean failOnPlayerError) {
    this.tag = tag;
    this.expectedPlayingTimeMs = expectedPlayingTimeMs;
    this.failOnPlayerError = failOnPlayerError;
    videoDecoderCounters = new DecoderCounters();
    audioDecoderCounters = new DecoderCounters();
  }

  /**
   * Sets a schedule to be applied during the test.
   *
   * @param schedule The schedule.
   */
  public final void setSchedule(ActionSchedule schedule) {
    if (player == null) {
      pendingSchedule = schedule;
    } else {
      schedule.start(player, trackSelector, surface, actionHandler);
    }
  }

  /**
   * Sets an {@link Player.EventListener} to listen for ExoPlayer events during the test.
   */
  public final void setEventListener(Player.EventListener eventListener) {
    this.playerEventListener = eventListener;
    if (player != null) {
      player.addListener(eventListener);
    }
  }

  // HostedTest implementation

  @Override
  public final void onStart(HostActivity host, Surface surface) {
    this.surface = surface;
    // Build the player.
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
    trackSelector = buildTrackSelector(host, bandwidthMeter);
    String userAgent = "ExoPlayerPlaybackTests";
    DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = buildDrmSessionManager(userAgent);
    player = buildExoPlayer(host, surface, trackSelector, drmSessionManager);
    player.prepare(buildSource(host, Util.getUserAgent(host, userAgent), bandwidthMeter));
    if (playerEventListener != null) {
      player.addListener(playerEventListener);
    }
    player.addListener(this);
    player.setAudioDebugListener(this);
    player.setVideoDebugListener(this);
    player.setPlayWhenReady(true);
    actionHandler = new Handler();
    // Schedule any pending actions.
    if (pendingSchedule != null) {
      pendingSchedule.start(player, trackSelector, surface, actionHandler);
      pendingSchedule = null;
    }
  }

  @Override
  public final boolean canStop() {
    return playerFinished;
  }

  @Override
  public final void onStop() {
    actionHandler.removeCallbacksAndMessages(null);
    sourceDurationMs = player.getDuration();
    player.release();
    player = null;
  }

  @Override
  public final void onFinished() {
    if (failOnPlayerError && playerError != null) {
      throw new Error(playerError);
    }
    logMetrics(audioDecoderCounters, videoDecoderCounters);
    if (expectedPlayingTimeMs != EXPECTED_PLAYING_TIME_UNSET) {
      long playingTimeToAssertMs = expectedPlayingTimeMs == EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS
          ? sourceDurationMs : expectedPlayingTimeMs;
      // Assert that the playback spanned the correct duration of time.
      long minAllowedActualPlayingTimeMs = playingTimeToAssertMs - MAX_PLAYING_TIME_DISCREPANCY_MS;
      long maxAllowedActualPlayingTimeMs = playingTimeToAssertMs + MAX_PLAYING_TIME_DISCREPANCY_MS;
      Assert.assertTrue("Total playing time: " + totalPlayingTimeMs + ". Expected: "
          + playingTimeToAssertMs, minAllowedActualPlayingTimeMs <= totalPlayingTimeMs
          && totalPlayingTimeMs <= maxAllowedActualPlayingTimeMs);
    }
    // Make any additional assertions.
    assertPassed(audioDecoderCounters, videoDecoderCounters);
  }

  // Player.EventListener

  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    // Do nothing.
  }

  @Override
  public final void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    Log.d(tag, "state [" + playWhenReady + ", " + playbackState + "]");
    playerWasPrepared |= playbackState != Player.STATE_IDLE;
    if (playbackState == Player.STATE_ENDED
        || (playbackState == Player.STATE_IDLE && playerWasPrepared)) {
      playerFinished = true;
    }
    boolean playing = playWhenReady && playbackState == Player.STATE_READY;
    if (!this.playing && playing) {
      lastPlayingStartTimeMs = SystemClock.elapsedRealtime();
    } else if (this.playing && !playing) {
      totalPlayingTimeMs += SystemClock.elapsedRealtime() - lastPlayingStartTimeMs;
    }
    this.playing = playing;
  }

  @Override
  public void onRepeatModeChanged(int repeatMode) {
    // Do nothing.
  }

  @Override
  public final void onPlayerError(ExoPlaybackException error) {
    playerWasPrepared = true;
    playerError = error;
    onPlayerErrorInternal(error);
  }

  @Override
  public final void onPositionDiscontinuity() {
    // Do nothing.
  }

  @Override
  public final void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    // Do nothing.
  }

  @Override
  public final void onTimelineChanged(Timeline timeline, Object manifest) {
    // Do nothing.
  }

  // AudioRendererEventListener

  @Override
  public void onAudioEnabled(DecoderCounters counters) {
    Log.d(tag, "audioEnabled");
  }

  @Override
  public void onAudioSessionId(int audioSessionId) {
    Log.d(tag, "audioSessionId [" + audioSessionId + "]");
  }

  @Override
  public void onAudioDecoderInitialized(String decoderName, long elapsedRealtimeMs,
      long initializationDurationMs) {
    Log.d(tag, "audioDecoderInitialized [" + decoderName + "]");
  }

  @Override
  public void onAudioInputFormatChanged(Format format) {
    Log.d(tag, "audioFormatChanged [" + Format.toLogString(format) + "]");
  }

  @Override
  public void onAudioDisabled(DecoderCounters counters) {
    Log.d(tag, "audioDisabled");
    audioDecoderCounters.merge(counters);
  }

  @Override
  public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    Log.e(tag, "audioTrackUnderrun [" + bufferSize + ", " + bufferSizeMs + ", "
        + elapsedSinceLastFeedMs + "]", null);
  }

  // VideoRendererEventListener

  @Override
  public void onVideoEnabled(DecoderCounters counters) {
    Log.d(tag, "videoEnabled");
  }

  @Override
  public void onVideoDecoderInitialized(String decoderName, long elapsedRealtimeMs,
      long initializationDurationMs) {
    Log.d(tag, "videoDecoderInitialized [" + decoderName + "]");
  }

  @Override
  public void onVideoInputFormatChanged(Format format) {
    Log.d(tag, "videoFormatChanged [" + Format.toLogString(format) + "]");
  }

  @Override
  public void onVideoDisabled(DecoderCounters counters) {
    Log.d(tag, "videoDisabled");
    videoDecoderCounters.merge(counters);
  }

  @Override
  public void onDroppedFrames(int count, long elapsed) {
    Log.d(tag, "droppedFrames [" + count + "]");
  }

  @Override
  public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
      float pixelWidthHeightRatio) {
    // Do nothing.
  }

  @Override
  public void onRenderedFirstFrame(Surface surface) {
    // Do nothing.
  }

  // Internal logic

  protected DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(String userAgent) {
    // Do nothing. Interested subclasses may override.
    return null;
  }

  @SuppressWarnings("unused")
  protected MappingTrackSelector buildTrackSelector(HostActivity host,
      BandwidthMeter bandwidthMeter) {
    return new DefaultTrackSelector(new AdaptiveTrackSelection.Factory(bandwidthMeter));
  }

  @SuppressWarnings("unused")
  protected SimpleExoPlayer buildExoPlayer(HostActivity host, Surface surface,
      MappingTrackSelector trackSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(host, drmSessionManager,
        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF, 0);
    SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
    player.setVideoSurface(surface);
    return player;
  }

  @SuppressWarnings("unused")
  protected abstract MediaSource buildSource(HostActivity host, String userAgent,
      TransferListener<? super DataSource> mediaTransferListener);

  @SuppressWarnings("unused")
  protected void onPlayerErrorInternal(ExoPlaybackException error) {
    // Do nothing. Interested subclasses may override.
  }

  protected void logMetrics(DecoderCounters audioCounters, DecoderCounters videoCounters) {
    // Do nothing. Subclasses may override to log metrics.
  }

  protected void assertPassed(DecoderCounters audioCounters, DecoderCounters videoCounters) {
    // Do nothing. Subclasses may override to add additional assertions.
  }

}
