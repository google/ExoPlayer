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
package com.google.android.exoplayer2.playbacktests.util;

import com.google.android.exoplayer2.CodecCounters;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioTrack;
import com.google.android.exoplayer2.playbacktests.util.HostActivity.HostedTest;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import junit.framework.Assert;

/**
 * A {@link HostedTest} for {@link ExoPlayer} playback tests.
 */
public abstract class ExoHostedTest implements HostedTest, ExoPlayer.EventListener,
    SimpleExoPlayer.DebugListener {

  static {
    // ExoPlayer's AudioTrack class is able to work around spurious timestamps reported by the
    // platform (by ignoring them). Disable this workaround, since we're interested in testing
    // that the underlying platform is behaving correctly.
    AudioTrack.failOnSpuriousAudioTimestamp = true;
  }

  public static final long MAX_PLAYING_TIME_DISCREPANCY_MS = 2000;
  public static final long EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS = -1;
  public static final long EXPECTED_PLAYING_TIME_UNSET = -2;

  private final String tag;
  private final boolean failOnPlayerError;
  private final long expectedPlayingTimeMs;
  private final CodecCounters videoCodecCounters;
  private final CodecCounters audioCodecCounters;

  private ActionSchedule pendingSchedule;
  private Handler actionHandler;
  private MappingTrackSelector trackSelector;
  private SimpleExoPlayer player;
  private ExoPlaybackException playerError;
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
        : EXPECTED_PLAYING_TIME_UNSET, false);
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
   * @param failOnPlayerError True if a player error should be considered a test failure. False
   *     otherwise.
   */
  public ExoHostedTest(String tag, long expectedPlayingTimeMs, boolean failOnPlayerError) {
    this.tag = tag;
    this.expectedPlayingTimeMs = expectedPlayingTimeMs;
    this.failOnPlayerError = failOnPlayerError;
    videoCodecCounters = new CodecCounters();
    audioCodecCounters = new CodecCounters();
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
      schedule.start(player, trackSelector, actionHandler);
    }
  }

  // HostedTest implementation

  @Override
  public final void onStart(HostActivity host, Surface surface) {
    // Build the player.
    trackSelector = buildTrackSelector(host);
    player = buildExoPlayer(host, surface, trackSelector);
    DataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(host, Util
        .getUserAgent(host, "ExoPlayerPlaybackTests"));
    BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
    player.setMediaSource(buildSource(host, dataSourceFactory, bandwidthMeter));
    player.addListener(this);
    player.setDebugListener(this);
    player.setPlayWhenReady(true);
    actionHandler = new Handler();
    // Schedule any pending actions.
    if (pendingSchedule != null) {
      pendingSchedule.start(player, trackSelector, actionHandler);
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
    logMetrics(audioCodecCounters, videoCodecCounters);
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
    assertPassed(audioCodecCounters, videoCodecCounters);
  }

  // ExoPlayer.EventListener

  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public final void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    Log.d(tag, "state [" + playWhenReady + ", " + playbackState + "]");
    playerWasPrepared |= playbackState != ExoPlayer.STATE_IDLE;
    if (playbackState == ExoPlayer.STATE_ENDED
        || (playbackState == ExoPlayer.STATE_IDLE && playerWasPrepared)) {
      playerFinished = true;
    }
    boolean playing = playWhenReady && playbackState == ExoPlayer.STATE_READY;
    if (!this.playing && playing) {
      lastPlayingStartTimeMs = SystemClock.elapsedRealtime();
    } else if (this.playing && !playing) {
      totalPlayingTimeMs += SystemClock.elapsedRealtime() - lastPlayingStartTimeMs;
    }
    this.playing = playing;
  }

  @Override
  public final void onPlayerError(ExoPlaybackException error) {
    playerWasPrepared = true;
    playerError = error;
    onPlayerErrorInternal(error);
  }

  @Override
  public final void onPlayWhenReadyCommitted() {
    // Do nothing.
  }

  @Override
  public final void onPositionDiscontinuity(int periodIndex, long positionMs) {
    // Do nothing.
  }

  // SimpleExoPlayer.DebugListener

  @Override
  public void onAudioEnabled(CodecCounters counters) {
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
  public void onAudioFormatChanged(Format format) {
    Log.d(tag, "audioFormatChanged [" + format.id + "]");
  }

  @Override
  public void onAudioDisabled(CodecCounters counters) {
    Log.d(tag, "audioDisabled");
    audioCodecCounters.merge(counters);
  }

  @Override
  public void onVideoEnabled(CodecCounters counters) {
    Log.d(tag, "videoEnabled");
  }

  @Override
  public void onVideoDecoderInitialized(String decoderName, long elapsedRealtimeMs,
      long initializationDurationMs) {
    Log.d(tag, "videoDecoderInitialized [" + decoderName + "]");
  }

  @Override
  public void onVideoFormatChanged(Format format) {
    Log.d(tag, "videoFormatChanged [" + format.id + "]");
  }

  @Override
  public void onVideoDisabled(CodecCounters counters) {
    Log.d(tag, "videoDisabled");
    videoCodecCounters.merge(counters);
  }

  @Override
  public void onDroppedFrames(int count, long elapsed) {
    Log.d(tag, "droppedFrames [" + count + "]");
  }

  @Override
  public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    Log.e(tag, "audioTrackUnderrun [" + bufferSize + ", " + bufferSizeMs + ", "
        + elapsedSinceLastFeedMs + "]", null);
  }

  // Internal logic

  @SuppressWarnings("unused")
  protected MappingTrackSelector buildTrackSelector(HostActivity host) {
    return new DefaultTrackSelector(null);
  }

  @SuppressWarnings("unused")
  protected SimpleExoPlayer buildExoPlayer(HostActivity host, Surface surface,
      MappingTrackSelector trackSelector) {
    SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(host, trackSelector);
    player.setSurface(surface);
    return player;
  }

  @SuppressWarnings("unused")
  protected abstract MediaSource buildSource(HostActivity host, DataSourceFactory dataSourceFactory,
      BandwidthMeter bandwidthMeter);

  @SuppressWarnings("unused")
  protected void onPlayerErrorInternal(ExoPlaybackException error) {
    // Do nothing. Interested subclasses may override.
  }

  protected void logMetrics(CodecCounters audioCounters, CodecCounters videoCounters) {
    // Do nothing. Subclasses may override to log metrics.
  }

  protected void assertPassed(CodecCounters audioCounters, CodecCounters videoCounters) {
    // Do nothing. Subclasses may override to add additional assertions.
  }

}
