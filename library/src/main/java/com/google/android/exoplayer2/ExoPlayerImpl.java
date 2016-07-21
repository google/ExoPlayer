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
package com.google.android.exoplayer2;

import com.google.android.exoplayer2.ExoPlayerImplInternal.PlaybackInfo;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Assertions;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Concrete implementation of {@link ExoPlayer}.
 */
/* package */ final class ExoPlayerImpl implements ExoPlayer {

  private static final String TAG = "ExoPlayerImpl";

  private final Handler eventHandler;
  private final ExoPlayerImplInternal internalPlayer;
  private final CopyOnWriteArraySet<EventListener> listeners;

  private boolean playWhenReady;
  private int playbackState;
  private int pendingPlayWhenReadyAcks;
  private int pendingSeekAcks;
  private boolean isLoading;

  // Playback information when there is no pending seek/set source operation.
  private PlaybackInfo playbackInfo;

  // Playback information when there is a pending seek/set source operation.
  private int maskingPeriodIndex;
  private long maskingPositionMs;
  private long maskingDurationMs;

  /**
   * Constructs an instance. Must be invoked from a thread that has an associated {@link Looper}.
   *
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   */
  @SuppressLint("HandlerLeak")
  public ExoPlayerImpl(Renderer[] renderers, TrackSelector trackSelector,
      LoadControl loadControl) {
    Log.i(TAG, "Init " + ExoPlayerLibraryInfo.VERSION);
    Assertions.checkNotNull(renderers);
    Assertions.checkState(renderers.length > 0);
    this.playWhenReady = false;
    this.playbackState = STATE_IDLE;
    this.listeners = new CopyOnWriteArraySet<>();
    eventHandler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        ExoPlayerImpl.this.handleEvent(msg);
      }
    };
    internalPlayer = new ExoPlayerImplInternal(renderers, trackSelector, loadControl, playWhenReady,
        eventHandler);
    playbackInfo = new ExoPlayerImplInternal.PlaybackInfo(0);
  }

  @Override
  public void addListener(EventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(EventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public int getPlaybackState() {
    return playbackState;
  }

  @Override
  public void setMediaSource(MediaSource mediaSource) {
    internalPlayer.setMediaSource(mediaSource);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (this.playWhenReady != playWhenReady) {
      this.playWhenReady = playWhenReady;
      pendingPlayWhenReadyAcks++;
      internalPlayer.setPlayWhenReady(playWhenReady);
      for (EventListener listener : listeners) {
        listener.onPlayerStateChanged(playWhenReady, playbackState);
      }
    }
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady;
  }

  @Override
  public boolean isPlayWhenReadyCommitted() {
    return pendingPlayWhenReadyAcks == 0;
  }

  @Override
  public boolean isLoading() {
    return isLoading;
  }

  @Override
  public void seekTo(long positionMs) {
    seekTo(getCurrentPeriodIndex(), positionMs);
  }

  @Override
  public void seekTo(int periodIndex, long positionMs) {
    boolean periodChanging = periodIndex != getCurrentPeriodIndex();
    maskingPeriodIndex = periodIndex;
    maskingPositionMs = positionMs;
    maskingDurationMs = periodChanging ? ExoPlayer.UNKNOWN_TIME : getDuration();

    pendingSeekAcks++;
    internalPlayer.seekTo(periodIndex, positionMs * 1000);
    for (EventListener listener : listeners) {
      listener.onPositionDiscontinuity(periodIndex, positionMs);
    }
  }

  @Override
  public void stop() {
    internalPlayer.stop();
  }

  @Override
  public void release() {
    internalPlayer.release();
    eventHandler.removeCallbacksAndMessages(null);
  }

  @Override
  public void sendMessages(ExoPlayerMessage... messages) {
    internalPlayer.sendMessages(messages);
  }

  @Override
  public void blockingSendMessages(ExoPlayerMessage... messages) {
    internalPlayer.blockingSendMessages(messages);
  }

  @Override
  public long getDuration() {
    if (pendingSeekAcks == 0) {
      long durationUs = playbackInfo.durationUs;
      return durationUs == C.UNSET_TIME_US ? ExoPlayer.UNKNOWN_TIME : durationUs / 1000;
    } else {
      return maskingDurationMs;
    }
  }

  @Override
  public long getCurrentPosition() {
    return pendingSeekAcks == 0 ? playbackInfo.positionUs / 1000
        : maskingPositionMs;
  }

  @Override
  public int getCurrentPeriodIndex() {
    return pendingSeekAcks == 0 ? playbackInfo.periodIndex : maskingPeriodIndex;
  }

  @Override
  public long getBufferedPosition() {
    if (pendingSeekAcks == 0) {
      long bufferedPositionUs = playbackInfo.bufferedPositionUs;
      return bufferedPositionUs == C.END_OF_SOURCE_US ? getDuration() : bufferedPositionUs / 1000;
    } else {
      return maskingPositionMs;
    }
  }

  @Override
  public int getBufferedPercentage() {
    long bufferedPosition = getBufferedPosition();
    long duration = getDuration();
    return bufferedPosition == ExoPlayer.UNKNOWN_TIME || duration == ExoPlayer.UNKNOWN_TIME ? 0
        : (int) (duration == 0 ? 100 : (bufferedPosition * 100) / duration);
  }

  @Override
  public float getPlaybackSpeed() { return internalPlayer.getPlaybackSpeed(); }

  @Override
  public void setPlaybackSpeed(float speed) { internalPlayer.setPlaybackSpeed(speed); }

  // Not private so it can be called from an inner class without going through a thunk method.
  /* package */ void handleEvent(Message msg) {
    switch (msg.what) {
      case ExoPlayerImplInternal.MSG_STATE_CHANGED: {
        playbackState = msg.arg1;
        for (EventListener listener : listeners) {
          listener.onPlayerStateChanged(playWhenReady, playbackState);
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_LOADING_CHANGED: {
        isLoading = msg.arg1 != 0;
        for (EventListener listener : listeners) {
          listener.onLoadingChanged(isLoading);
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_SET_PLAY_WHEN_READY_ACK: {
        pendingPlayWhenReadyAcks--;
        if (pendingPlayWhenReadyAcks == 0) {
          for (EventListener listener : listeners) {
            listener.onPlayWhenReadyCommitted();
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_SEEK_ACK: {
        pendingSeekAcks--;
        break;
      }
      case ExoPlayerImplInternal.MSG_PERIOD_CHANGED: {
        playbackInfo = (ExoPlayerImplInternal.PlaybackInfo) msg.obj;
        if (pendingSeekAcks == 0) {
          for (EventListener listener : listeners) {
            listener.onPositionDiscontinuity(playbackInfo.periodIndex, 0);
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_ERROR: {
        ExoPlaybackException exception = (ExoPlaybackException) msg.obj;
        for (EventListener listener : listeners) {
          listener.onPlayerError(exception);
        }
        break;
      }
    }
  }

}
