/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.playbacktests.util;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer.DecoderInitializationException;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.audio.AudioTrack.InitializationException;
import com.google.android.exoplayer.audio.AudioTrack.WriteException;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.hls.HlsSampleSource;

import android.media.MediaCodec.CryptoException;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Logs information reported by an {@link ExoPlayer} instance and various player components.
 */
public final class LogcatLogger implements ExoPlayer.Listener,
    MediaCodecVideoTrackRenderer.EventListener, MediaCodecAudioTrackRenderer.EventListener,
    ChunkSampleSource.EventListener, HlsSampleSource.EventListener {

  private static final NumberFormat TIME_FORMAT;
  static {
    TIME_FORMAT = NumberFormat.getInstance(Locale.US);
    TIME_FORMAT.setMinimumFractionDigits(2);
    TIME_FORMAT.setMaximumFractionDigits(2);
  }

  private final String tag;
  private final ExoPlayer player;

  /**
   * @param tag A tag to use for logging.
   * @param player The player.
   */
  public LogcatLogger(String tag, ExoPlayer player) {
    this.tag = tag;
    this.player = player;
    player.addListener(this);
  }

  // ExoPlayer.Listener.

  @Override
  public final void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    Log.i(tag, "Player state: " + getTimeString(player.getCurrentPosition()) + ", "
        + playWhenReady + ", " + getStateString(playbackState));
  }

  @Override
  public final void onPlayerError(ExoPlaybackException e) {
    Log.e(tag, "Player failed", e);
  }

  @Override
  public void onPlayWhenReadyCommitted() {}

  // Component listeners.

  @Override
  public void onDecoderInitializationError(DecoderInitializationException e) {
    Log.e(tag, "Decoder initialization error", e);
  }

  @Override
  public void onCryptoError(CryptoException e) {
    Log.e(tag, "Crypto error", e);
  }

  @Override
  public void onLoadError(int sourceId, IOException e) {
    Log.e(tag, "Load error (" + sourceId + ")", e);
  }

  @Override
  public void onAudioTrackInitializationError(InitializationException e) {
    Log.e(tag, "Audio track initialization error", e);
  }

  @Override
  public void onAudioTrackWriteError(WriteException e) {
    Log.e(tag, "Audio track write error", e);
  }

  @Override
  public void onDroppedFrames(int count, long elapsed) {
    Log.w(tag, "Dropped frames (" + count + ")");
  }

  @Override
  public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
      long initializationDurationMs) {
    Log.i(tag, "Initialized decoder: " + decoderName);
  }

  @Override
  public void onDownstreamFormatChanged(int sourceId, Format format, int trigger,
      long mediaTimeMs) {
    Log.i(tag, "Downstream format changed (" + sourceId + "): " + format.id);
  }

  @Override
  public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
      float pixelWidthHeightRatio) {
    Log.i(tag, "Video size changed: " + width + "x" + height);
  }

  @Override
  public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format,
      long mediaStartTimeMs, long mediaEndTimeMs) {}

  @Override
  public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger,
      Format format, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs,
      long loadDurationMs) {}

  @Override
  public void onLoadCanceled(int sourceId, long bytesLoaded) {}

  @Override
  public void onUpstreamDiscarded(int sourceId, long mediaStartTimeMs, long mediaEndTimeMs) {}

  @Override
  public void onDrawnToSurface(Surface surface) {}

  private static String getStateString(int state) {
    switch (state) {
      case ExoPlayer.STATE_BUFFERING:
        return "B";
      case ExoPlayer.STATE_ENDED:
        return "E";
      case ExoPlayer.STATE_IDLE:
        return "I";
      case ExoPlayer.STATE_PREPARING:
        return "P";
      case ExoPlayer.STATE_READY:
        return "R";
      default:
        return "?";
    }
  }

  private static String getTimeString(long timeMs) {
    return TIME_FORMAT.format((timeMs) / 1000f);
  }

}
