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

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import com.google.android.exoplayer2.ExoPlayerImplInternal.PlaybackInfo;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.Timeline;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Assertions;
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
  private Timeline timeline;
  private Object manifest;

  // Playback information when there is no pending seek/set source operation.
  private PlaybackInfo playbackInfo;

  // Playback information when there is a pending seek/set source operation.
  private int maskingPeriodIndex;
  private long maskingPositionMs;

  /**
   * Constructs an instance. Must be called from a thread that has an associated {@link Looper}.
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
    playbackInfo = new ExoPlayerImplInternal.PlaybackInfo(0);
    internalPlayer = new ExoPlayerImplInternal(renderers, trackSelector, loadControl, playWhenReady,
        eventHandler, playbackInfo);
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
    setMediaSource(mediaSource, true);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
    timeline = null;
    internalPlayer.setMediaSource(mediaSource, resetPosition);
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
    boolean seekToDefaultPosition = positionMs == ExoPlayer.UNKNOWN_TIME;
    maskingPeriodIndex = periodIndex;
    maskingPositionMs = seekToDefaultPosition ? 0 : positionMs;
    pendingSeekAcks++;
    internalPlayer.seekTo(periodIndex, seekToDefaultPosition ? C.UNSET_TIME_US : positionMs * 1000);
    if (!seekToDefaultPosition) {
      for (EventListener listener : listeners) {
        listener.onPositionDiscontinuity(periodIndex, positionMs);
      }
    }
  }

  @Override
  public void seekToDefaultPosition(int periodIndex) {
    seekTo(periodIndex, ExoPlayer.UNKNOWN_TIME);
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
    if (timeline == null) {
      return UNKNOWN_TIME;
    }
    return timeline.getPeriodDurationMs(getCurrentPeriodIndex());
  }

  @Override
  public long getCurrentPosition() {
    return pendingSeekAcks > 0 ? maskingPositionMs
        : playbackInfo.positionUs == C.UNSET_TIME_US ? 0 : (playbackInfo.positionUs / 1000);
  }

  @Override
  public int getCurrentPeriodIndex() {
    return pendingSeekAcks == 0 ? playbackInfo.periodIndex : maskingPeriodIndex;
  }

  @Override
  public Timeline getCurrentTimeline() {
    return timeline;
  }

  @Override
  public Object getCurrentManifest() {
    return manifest;
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
        if (--pendingSeekAcks == 0) {
          long positionMs = playbackInfo.startPositionUs == C.UNSET_TIME_US ? 0
              : playbackInfo.startPositionUs / 1000;
          if (playbackInfo.periodIndex != maskingPeriodIndex || positionMs != maskingPositionMs) {
            for (EventListener listener : listeners) {
              listener.onPositionDiscontinuity(playbackInfo.periodIndex, positionMs);
            }
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_POSITION_DISCONTINUITY: {
        playbackInfo = (ExoPlayerImplInternal.PlaybackInfo) msg.obj;
        if (pendingSeekAcks == 0) {
          for (EventListener listener : listeners) {
            listener.onPositionDiscontinuity(playbackInfo.periodIndex,
                playbackInfo.startPositionUs / 1000);
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_SOURCE_INFO_REFRESHED: {
        @SuppressWarnings("unchecked")
        Pair<Timeline, Object> timelineAndManifest = (Pair<Timeline, Object>) msg.obj;
        timeline = timelineAndManifest.first;
        manifest = timelineAndManifest.second;
        for (EventListener listener : listeners) {
          listener.onSourceInfoRefreshed(timeline, manifest);
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
