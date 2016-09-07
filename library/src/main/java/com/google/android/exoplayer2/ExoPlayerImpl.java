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
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Assertions;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * An {@link ExoPlayer} implementation. Instances can be obtained from {@link ExoPlayerFactory}.
 */
/* package */ final class ExoPlayerImpl implements ExoPlayer {

  private static final String TAG = "ExoPlayerImpl";

  private final Handler eventHandler;
  private final ExoPlayerImplInternal internalPlayer;
  private final CopyOnWriteArraySet<EventListener> listeners;
  private final Timeline.Window window;
  private final Timeline.Period period;

  private boolean pendingInitialSeek;
  private boolean playWhenReady;
  private int playbackState;
  private int pendingSeekAcks;
  private boolean isLoading;
  private Timeline timeline;
  private Object manifest;

  // Playback information when there is no pending seek/set source operation.
  private PlaybackInfo playbackInfo;

  // Playback information when there is a pending seek/set source operation.
  private int maskingWindowIndex;
  private long maskingWindowPositionMs;

  /**
   * Constructs an instance. Must be called from a thread that has an associated {@link Looper}.
   *
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   */
  @SuppressLint("HandlerLeak")
  public ExoPlayerImpl(Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl) {
    Log.i(TAG, "Init " + ExoPlayerLibraryInfo.VERSION);
    Assertions.checkNotNull(renderers);
    Assertions.checkState(renderers.length > 0);
    this.playWhenReady = false;
    this.playbackState = STATE_IDLE;
    this.listeners = new CopyOnWriteArraySet<>();
    window = new Timeline.Window();
    period = new Timeline.Period();
    eventHandler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        ExoPlayerImpl.this.handleEvent(msg);
      }
    };
    playbackInfo = new ExoPlayerImplInternal.PlaybackInfo(0, 0);
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
  public void prepare(MediaSource mediaSource) {
    prepare(mediaSource, true);
  }

  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition) {
    timeline = null;
    internalPlayer.setMediaSource(mediaSource, resetPosition);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (this.playWhenReady != playWhenReady) {
      this.playWhenReady = playWhenReady;
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
  public boolean isLoading() {
    return isLoading;
  }

  @Override
  public void seekToDefaultPosition() {
    seekToDefaultPosition(getCurrentWindowIndex());
  }

  @Override
  public void seekToDefaultPosition(int windowIndex) {
    if (timeline == null) {
      maskingWindowIndex = windowIndex;
      maskingWindowPositionMs = C.TIME_UNSET;
      pendingInitialSeek = true;
    } else {
      Assertions.checkIndex(windowIndex, 0, timeline.getWindowCount());
      pendingSeekAcks++;
      maskingWindowIndex = windowIndex;
      maskingWindowPositionMs = 0;
      internalPlayer.seekTo(timeline.getWindow(windowIndex, window).firstPeriodIndex, C.TIME_UNSET);
    }
  }

  @Override
  public void seekTo(long positionMs) {
    seekTo(getCurrentWindowIndex(), positionMs);
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    if (positionMs == C.TIME_UNSET) {
      seekToDefaultPosition(windowIndex);
    } else if (timeline == null) {
      maskingWindowIndex = windowIndex;
      maskingWindowPositionMs = positionMs;
      pendingInitialSeek = true;
    } else {
      Assertions.checkIndex(windowIndex, 0, timeline.getWindowCount());
      pendingSeekAcks++;
      maskingWindowIndex = windowIndex;
      maskingWindowPositionMs = positionMs;
      timeline.getWindow(windowIndex, window);
      int periodIndex = window.firstPeriodIndex;
      long periodPositionMs = window.getPositionInFirstPeriodMs() + positionMs;
      long periodDurationMs = timeline.getPeriod(periodIndex, period).getDurationMs();
      while (periodDurationMs != C.TIME_UNSET && periodPositionMs >= periodDurationMs
          && periodIndex < window.lastPeriodIndex) {
        periodPositionMs -= periodDurationMs;
        periodDurationMs = timeline.getPeriod(++periodIndex, period).getDurationMs();
      }
      internalPlayer.seekTo(periodIndex, C.msToUs(periodPositionMs));
      for (EventListener listener : listeners) {
        listener.onPositionDiscontinuity();
      }
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
  public int getCurrentPeriodIndex() {
    return playbackInfo.periodIndex;
  }

  @Override
  public int getCurrentWindowIndex() {
    if (timeline == null || pendingSeekAcks > 0) {
      return maskingWindowIndex;
    } else {
      return timeline.getPeriod(playbackInfo.periodIndex, period).windowIndex;
    }
  }

  @Override
  public long getDuration() {
    if (timeline == null) {
      return C.TIME_UNSET;
    }
    return timeline.getWindow(getCurrentWindowIndex(), window).getDurationMs();
  }

  @Override
  public long getCurrentPosition() {
    if (timeline == null || pendingSeekAcks > 0) {
      return maskingWindowPositionMs;
    } else {
      timeline.getPeriod(playbackInfo.periodIndex, period);
      return period.getPositionInWindowMs() + C.usToMs(playbackInfo.positionUs);
    }
  }

  @Override
  public long getBufferedPosition() {
    // TODO - Implement this properly.
    if (timeline == null || pendingSeekAcks > 0) {
      return maskingWindowPositionMs;
    } else {
      int periodIndex = playbackInfo.periodIndex;
      timeline.getPeriod(periodIndex, period);
      int windowIndex = period.windowIndex;
      timeline.getWindow(windowIndex, window);
      if (window.firstPeriodIndex == periodIndex && window.lastPeriodIndex == periodIndex
          && window.getPositionInFirstPeriodUs() == 0
          && window.getDurationUs() == period.getDurationUs()) {
        return C.usToMs(playbackInfo.bufferedPositionUs);
      }
      return getCurrentPosition();
    }
  }

  @Override
  public int getBufferedPercentage() {
    if (timeline == null) {
      return 0;
    }
    long bufferedPosition = getBufferedPosition();
    long duration = getDuration();
    return (bufferedPosition == C.TIME_UNSET || duration == C.TIME_UNSET) ? 0
        : (int) (duration == 0 ? 100 : (bufferedPosition * 100) / duration);
  }

  @Override
  public Timeline getCurrentTimeline() {
    return timeline;
  }

  @Override
  public Object getCurrentManifest() {
    return manifest;
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
      case ExoPlayerImplInternal.MSG_SEEK_ACK: {
        if (--pendingSeekAcks == 0) {
          playbackInfo = (ExoPlayerImplInternal.PlaybackInfo) msg.obj;
          for (EventListener listener : listeners) {
            listener.onPositionDiscontinuity();
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_POSITION_DISCONTINUITY: {
        if (pendingSeekAcks == 0) {
          playbackInfo = (ExoPlayerImplInternal.PlaybackInfo) msg.obj;
          for (EventListener listener : listeners) {
            listener.onPositionDiscontinuity();
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_SOURCE_INFO_REFRESHED: {
        @SuppressWarnings("unchecked")
        Pair<Timeline, Object> timelineAndManifest = (Pair<Timeline, Object>) msg.obj;
        timeline = timelineAndManifest.first;
        manifest = timelineAndManifest.second;
        if (pendingInitialSeek) {
          pendingInitialSeek = false;
          seekTo(maskingWindowIndex, maskingWindowPositionMs);
        }
        for (EventListener listener : listeners) {
          listener.onTimelineChanged(timeline, manifest);
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
