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
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.ErrorMessageProvider;

/**
 * Leanback {@link PlayerAdapter} implementation for {@link SimpleExoPlayer}.
 */
public final class LeanbackPlayerAdapter extends PlayerAdapter {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.leanback");
  }

  private final Context context;
  private final SimpleExoPlayer player;
  private final Handler handler;
  private final Runnable updatePlayerRunnable = new Runnable() {
      @Override
      public void run() {
          getCallback().onCurrentPositionChanged(LeanbackPlayerAdapter.this);
          getCallback().onBufferedPositionChanged(LeanbackPlayerAdapter.this);
          handler.postDelayed(this, updatePeriod);
      }
  };

  private SurfaceHolderGlueHost surfaceHolderGlueHost;
  private boolean initialized;
  private boolean hasSurface;
  private boolean isBuffering;
  private ErrorMessageProvider<? super ExoPlaybackException> errorMessageProvider;
  private final int updatePeriod;
  private final ExoPlayerEventListenerImpl exoPlayerListener = new ExoPlayerEventListenerImpl();
  private final SimpleExoPlayer.VideoListener videoListener = new SimpleExoPlayer.VideoListener() {
        @Override
      public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
          float pixelWidthHeightRatio) {
            getCallback().onVideoSizeChanged(LeanbackPlayerAdapter.this, width, height);
      }

      @Override
      public void onRenderedFirstFrame() {
      }
  };

  /**
   * Constructor.
   * Users are responsible for managing {@link SimpleExoPlayer} lifecycle. You must
   * stop/release the player once you're done playing the media.
   *
   * @param context The current context (activity).
   * @param player Instance of your exoplayer that needs to be configured.
   */
  public LeanbackPlayerAdapter(Context context, SimpleExoPlayer player, int updatePeriod) {
    this.context = context;
    this.player = player;
    this.handler = new Handler();
    this.updatePeriod = updatePeriod;
  }

  @Override
  public void onAttachedToHost(PlaybackGlueHost host) {
    if (host instanceof SurfaceHolderGlueHost) {
      surfaceHolderGlueHost = ((SurfaceHolderGlueHost) host);
      surfaceHolderGlueHost.setSurfaceHolderCallback(new VideoPlayerSurfaceHolderCallback());
    }
    notifyListeners();
    player.addListener(exoPlayerListener);
    player.addVideoListener(videoListener);
  }

  private void notifyListeners() {
    boolean oldIsPrepared = isPrepared();
    int playbackState = player.getPlaybackState();
    boolean isInitialized = playbackState != Player.STATE_IDLE;
    isBuffering = playbackState == Player.STATE_BUFFERING;
    boolean hasEnded = playbackState == Player.STATE_ENDED;

    initialized = isInitialized;
    if (oldIsPrepared != isPrepared()) {
      getCallback().onPreparedStateChanged(this);
    }

    getCallback().onPlayStateChanged(this);
    getCallback().onBufferingStateChanged(this, isBuffering || !initialized);

    if (hasEnded) {
      getCallback().onPlayCompleted(this);
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
    player.removeListener(exoPlayerListener);
    player.removeVideoListener(videoListener);
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
    if (!enabled) {
      return;
    }
    handler.postDelayed(updatePlayerRunnable, updatePeriod);
  }

  @Override
  public boolean isPlaying() {
    return initialized && player.getPlayWhenReady();
  }

  @Override
  public long getDuration() {
    long duration = player.getDuration();
    return duration != C.TIME_UNSET ? duration : -1;
  }

  @Override
  public long getCurrentPosition() {
    return initialized ? player.getCurrentPosition() : -1;
  }

  @Override
  public void play() {
    if (player.getPlaybackState() == Player.STATE_ENDED) {
      seekTo(0);
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

  /**
   * @return True if ExoPlayer is ready and got a SurfaceHolder if
   * {@link PlaybackGlueHost} provides SurfaceHolder.
   */
  @Override
  public boolean isPrepared() {
    return initialized && (surfaceHolderGlueHost == null || hasSurface);
  }

  private void setVideoSurface(Surface surface) {
    hasSurface = surface != null;
    player.setVideoSurface(surface);
    getCallback().onPreparedStateChanged(this);
  }

  /**
   * Implements {@link SurfaceHolder.Callback} that can then be set on the {@link PlaybackGlueHost}.
   */
  private final class VideoPlayerSurfaceHolderCallback implements SurfaceHolder.Callback {
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
      setVideoSurface(surfaceHolder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
      setVideoSurface(null);
    }
  }

  private final class ExoPlayerEventListenerImpl implements ExoPlayer.EventListener {

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      LeanbackPlayerAdapter.this.notifyListeners();
    }

    @Override
    public void onPlayerError(ExoPlaybackException exception) {
      if (errorMessageProvider != null) {
        Pair<Integer, String> message = errorMessageProvider.getErrorMessage(exception);
        if (message != null) {
          getCallback().onError(LeanbackPlayerAdapter.this,
              message.first,
              message.second);
            return;
        }
      }
      getCallback().onError(LeanbackPlayerAdapter.this,
          exception.type,
          context.getString(R.string.lb_media_player_error,
          exception.type,
          exception.rendererIndex));
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
      getCallback().onDurationChanged(LeanbackPlayerAdapter.this);
      getCallback().onCurrentPositionChanged(LeanbackPlayerAdapter.this);
      getCallback().onBufferedPositionChanged(LeanbackPlayerAdapter.this);
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    }

    @Override
    public void onPositionDiscontinuity() {
      getCallback().onCurrentPositionChanged(LeanbackPlayerAdapter.this);
      getCallback().onBufferedPositionChanged(LeanbackPlayerAdapter.this);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters params) {
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
    }
  }
}

