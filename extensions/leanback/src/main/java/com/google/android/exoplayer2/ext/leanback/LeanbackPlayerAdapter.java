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
import android.support.annotation.Nullable;
import android.support.v17.leanback.R;
import android.support.v17.leanback.media.PlaybackGlueHost;
import android.support.v17.leanback.media.PlayerAdapter;
import android.support.v17.leanback.media.SurfaceHolderGlueHost;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
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
  private final Runnable updateProgressRunnable;

  private ControlDispatcher controlDispatcher;
  private ErrorMessageProvider<? super ExoPlaybackException> errorMessageProvider;
  private SurfaceHolderGlueHost surfaceHolderGlueHost;
  private boolean hasSurface;
  private boolean lastNotifiedPreparedState;

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
    controlDispatcher = new DefaultControlDispatcher();
    updateProgressRunnable = new Runnable() {
      @Override
      public void run() {
        Callback callback = getCallback();
        callback.onCurrentPositionChanged(LeanbackPlayerAdapter.this);
        callback.onBufferedPositionChanged(LeanbackPlayerAdapter.this);
        handler.postDelayed(this, updatePeriodMs);
      }
    };
  }

  /**
   * Sets the {@link ControlDispatcher}.
   *
   * @param controlDispatcher The {@link ControlDispatcher}, or null to use
   *     {@link DefaultControlDispatcher}.
   */
  public void setControlDispatcher(@Nullable ControlDispatcher controlDispatcher) {
    this.controlDispatcher = controlDispatcher == null ? new DefaultControlDispatcher()
        : controlDispatcher;
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

  // PlayerAdapter implementation.

  @Override
  public void onAttachedToHost(PlaybackGlueHost host) {
    if (host instanceof SurfaceHolderGlueHost) {
      surfaceHolderGlueHost = ((SurfaceHolderGlueHost) host);
      surfaceHolderGlueHost.setSurfaceHolderCallback(componentListener);
    }
    notifyStateChanged();
    player.addListener(componentListener);
    player.addVideoListener(componentListener);
  }

  @Override
  public void onDetachedFromHost() {
    player.removeListener(componentListener);
    player.removeVideoListener(componentListener);
    if (surfaceHolderGlueHost != null) {
      surfaceHolderGlueHost.setSurfaceHolderCallback(null);
      surfaceHolderGlueHost = null;
    }
    hasSurface = false;
    Callback callback = getCallback();
    callback.onBufferingStateChanged(this, false);
    callback.onPlayStateChanged(this);
    maybeNotifyPreparedStateChanged(callback);
  }

  @Override
  public void setProgressUpdatingEnabled(boolean enabled) {
    handler.removeCallbacks(updateProgressRunnable);
    if (enabled) {
      handler.post(updateProgressRunnable);
    }
  }

  @Override
  public boolean isPlaying() {
    int playbackState = player.getPlaybackState();
    return playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
        && player.getPlayWhenReady();
  }

  @Override
  public long getDuration() {
    long durationMs = player.getDuration();
    return durationMs == C.TIME_UNSET ? -1 : durationMs;
  }

  @Override
  public long getCurrentPosition() {
    return player.getPlaybackState() == Player.STATE_IDLE ? -1 : player.getCurrentPosition();
  }

  @Override
  public void play() {
    if (player.getPlaybackState() == Player.STATE_ENDED) {
      controlDispatcher.dispatchSeekTo(player, player.getCurrentWindowIndex(), C.TIME_UNSET);
    }
    if (controlDispatcher.dispatchSetPlayWhenReady(player, true)) {
      getCallback().onPlayStateChanged(this);
    }
  }

  @Override
  public void pause() {
    if (controlDispatcher.dispatchSetPlayWhenReady(player, false)) {
      getCallback().onPlayStateChanged(this);
    }
  }

  @Override
  public void seekTo(long positionMs) {
    controlDispatcher.dispatchSeekTo(player, player.getCurrentWindowIndex(), positionMs);
  }

  @Override
  public long getBufferedPosition() {
    return player.getBufferedPosition();
  }

  @Override
  public boolean isPrepared() {
    return player.getPlaybackState() != Player.STATE_IDLE
        && (surfaceHolderGlueHost == null || hasSurface);
  }

  // Internal methods.

  /* package */ void setVideoSurface(Surface surface) {
    hasSurface = surface != null;
    player.setVideoSurface(surface);
    maybeNotifyPreparedStateChanged(getCallback());
  }

  /* package */ void notifyStateChanged() {
    int playbackState = player.getPlaybackState();
    Callback callback = getCallback();
    maybeNotifyPreparedStateChanged(callback);
    callback.onPlayStateChanged(this);
    callback.onBufferingStateChanged(this, playbackState == Player.STATE_BUFFERING);
    if (playbackState == Player.STATE_ENDED) {
      callback.onPlayCompleted(this);
    }
  }

  private void maybeNotifyPreparedStateChanged(Callback callback) {
    boolean isPrepared = isPrepared();
    if (lastNotifiedPreparedState != isPrepared) {
      lastNotifiedPreparedState = isPrepared;
      callback.onPreparedStateChanged(this);
    }
  }

  private final class ComponentListener extends Player.DefaultEventListener implements
      SimpleExoPlayer.VideoListener, SurfaceHolder.Callback {

    // SurfaceHolder.Callback implementation.

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
      setVideoSurface(surfaceHolder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
      // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
      setVideoSurface(null);
    }

    // Player.EventListener implementation.

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      notifyStateChanged();
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
    public void onTimelineChanged(Timeline timeline, Object manifest) {
      Callback callback = getCallback();
      callback.onDurationChanged(LeanbackPlayerAdapter.this);
      callback.onCurrentPositionChanged(LeanbackPlayerAdapter.this);
      callback.onBufferedPositionChanged(LeanbackPlayerAdapter.this);
    }

    @Override
    public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
      Callback callback = getCallback();
      callback.onCurrentPositionChanged(LeanbackPlayerAdapter.this);
      callback.onBufferedPositionChanged(LeanbackPlayerAdapter.this);
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
