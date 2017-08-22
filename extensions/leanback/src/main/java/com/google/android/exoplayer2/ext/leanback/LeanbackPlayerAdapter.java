/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.leanback;

import android.content.Context;
import android.os.Handler;
import android.support.v17.leanback.R;
import android.support.v17.leanback.media.PlaybackGlueHost;
import android.support.v17.leanback.media.PlayerAdapter;
import android.support.v17.leanback.media.SurfaceHolderGlueHost;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.ErrorMessageProvider;

/**
 * Leanback {@code PlayerAdapter} implementation for {@link SimpleExoPlayer}.
 */
public final class LeanbackPlayerAdapter extends PlayerAdapter {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.leanback");
  }

  private final Context context;
  private final SimpleExoPlayer player;
  private final Handler handler;
  private final ComponentListener componentListener;
  private final Runnable updatePlayerRunnable;

  private ErrorMessageProvider<? super ExoPlaybackException> errorMessageProvider;
  private SurfaceHolderGlueHost surfaceHolderGlueHost;
  private boolean initialized;
  private boolean hasSurface;
  private boolean isBuffering;

  /**
   * Builds an instance. Note that the {@code PlayerAdapter} does not manage the lifecycle of the
   * {@link SimpleExoPlayer} instance. The caller remains responsible for releasing the player when
   * it's no longer required.
   *
   * @param context The current context (activity).
   * @param player Instance of your exoplayer that needs to be configured.
   * @param updatePeriodMs The delay between player control updates, in milliseconds.
   */
  public LeanbackPlayerAdapter(Context context, SimpleExoPlayer player, final int updatePeriodMs) {
    this.context = context;
    this.player = player;
    handler = new Handler();
    componentListener = new ComponentListener();
    updatePlayerRunnable = new Runnable() {
      @Override
      public void run() {
        Callback callback = getCallback();
        callback.onCurrentPositionChanged(LeanbackPlayerAdapter.this);
        callback.onBufferedPositionChanged(LeanbackPlayerAdapter.this);
        handler.postDelayed(this, updatePeriodMs);
      }
    };
  }

  @Override
  public void onAttachedToHost(PlaybackGlueHost host) {
    if (host instanceof SurfaceHolderGlueHost) {
      surfaceHolderGlueHost = ((SurfaceHolderGlueHost) host);
      surfaceHolderGlueHost.setSurfaceHolderCallback(componentListener);
    }
    notifyListeners();
    player.addListener(componentListener);
    player.addVideoListener(componentListener);
  }

  private void notifyListeners() {
    boolean oldIsPrepared = isPrepared();
    int playbackState = player.getPlaybackState();
    boolean isInitialized = playbackState != Player.STATE_IDLE;
    isBuffering = playbackState == Player.STATE_BUFFERING;
    boolean hasEnded = playbackState == Player.STATE_ENDED;

    initialized = isInitialized;
    Callback callback = getCallback();
    if (oldIsPrepared != isPrepared()) {
      callback.onPreparedStateChanged(this);
    }
    callback.onPlayStateChanged(this);
    callback.onBufferingStateChanged(this, isBuffering || !initialized);
    if (hasEnded) {
      callback.onPlayCompleted(this);
    }
  }

  /**
   * Sets the optional {@link ErrorMessageProvider}.
   *
   * @param errorMessageProvider The {@link ErrorMessageProvider}.
   */
  public void setErrorMessageProvider(
      ErrorMessageProvider<? super ExoPlaybackException> errorMessageProvider) {
    this.errorMessageProvider = errorMessageProvider;
  }

  @Override
  public void onDetachedFromHost() {
    player.removeListener(componentListener);
    player.removeVideoListener(componentListener);
    if (surfaceHolderGlueHost != null) {
      surfaceHolderGlueHost.setSurfaceHolderCallback(null);
      surfaceHolderGlueHost = null;
    }
    initialized = false;
    hasSurface = false;
    Callback callback = getCallback();
    callback.onBufferingStateChanged(this, false);
    callback.onPlayStateChanged(this);
    callback.onPreparedStateChanged(this);
  }

  @Override
  public void setProgressUpdatingEnabled(final boolean enabled) {
    handler.removeCallbacks(updatePlayerRunnable);
    if (enabled) {
      handler.post(updatePlayerRunnable);
    }
  }

  @Override
  public boolean isPlaying() {
    return initialized && player.getPlayWhenReady();
  }

  @Override
  public long getDuration() {
    long durationMs = player.getDuration();
    return durationMs != C.TIME_UNSET ? durationMs : -1;
  }

  @Override
  public long getCurrentPosition() {
    return initialized ? player.getCurrentPosition() : -1;
  }

  @Override
  public void play() {
    if (player.getPlaybackState() == Player.STATE_ENDED) {
      player.seekToDefaultPosition();
    }
    player.setPlayWhenReady(true);
    getCallback().onPlayStateChanged(this);
  }

  @Override
  public void pause() {
    player.setPlayWhenReady(false);
    getCallback().onPlayStateChanged(this);
  }

  @Override
  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  @Override
  public long getBufferedPosition() {
    return player.getBufferedPosition();
  }

  @Override
  public boolean isPrepared() {
    return initialized && (surfaceHolderGlueHost == null || hasSurface);
  }

  private void setVideoSurface(Surface surface) {
    hasSurface = surface != null;
    player.setVideoSurface(surface);
    getCallback().onPreparedStateChanged(this);
  }

  private final class ComponentListener implements Player.EventListener,
      SimpleExoPlayer.VideoListener, SurfaceHolder.Callback {

    // SurfaceHolder.Callback implementation.

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
      setVideoSurface(surfaceHolder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
      // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
      setVideoSurface(null);
    }

    // Player.EventListener implementation.

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      notifyListeners();
    }

    @Override
    public void onPlayerError(ExoPlaybackException exception) {
      Callback callback = getCallback();
      if (errorMessageProvider != null) {
        Pair<Integer, String> errorMessage = errorMessageProvider.getErrorMessage(exception);
        callback.onError(LeanbackPlayerAdapter.this, errorMessage.first, errorMessage.second);
      } else {
        callback.onError(LeanbackPlayerAdapter.this, exception.type, context.getString(
            R.string.lb_media_player_error, exception.type, exception.rendererIndex));
      }
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
      // Do nothing.
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
      Callback callback = getCallback();
      callback.onDurationChanged(LeanbackPlayerAdapter.this);
      callback.onCurrentPositionChanged(LeanbackPlayerAdapter.this);
      callback.onBufferedPositionChanged(LeanbackPlayerAdapter.this);
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      // Do nothing.
    }

    @Override
    public void onPositionDiscontinuity() {
      Callback callback = getCallback();
      callback.onCurrentPositionChanged(LeanbackPlayerAdapter.this);
      callback.onBufferedPositionChanged(LeanbackPlayerAdapter.this);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters params) {
      // Do nothing.
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
      // Do nothing.
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
      // Do nothing.
    }

    // SimpleExoplayerView.Callback implementation.

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio) {
      getCallback().onVideoSizeChanged(LeanbackPlayerAdapter.this, width, height);
    }

    @Override
    public void onRenderedFirstFrame() {
      // Do nothing.
    }

  }

}

