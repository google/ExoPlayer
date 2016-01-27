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
package com.google.android.exoplayer;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Concrete implementation of {@link ExoPlayer}.
 */
/* package */ final class ExoPlayerImpl implements ExoPlayer {

  private static final String TAG = "ExoPlayerImpl";

  private final Handler eventHandler;
  private final ExoPlayerImplInternal internalPlayer;
  private final CopyOnWriteArraySet<Listener> listeners;
  private final MediaFormat[][] trackFormats;
  private final int[] selectedTrackIndices;

  private boolean playWhenReady;
  private int playbackState;
  private int pendingPlayWhenReadyAcks;

    // The higher the number - the higher the quality of fade
    // and it will consume more CPU.
    private double volumeAlterationsPerSecond = 30;

    private double fadeDurationSeconds = 3;
    private double fadeVelocity = 2;

    private double mFromVolume = 0;
    private double mToVolume = 0;

    private int currentStep;

  /**
   * Constructs an instance. Must be invoked from a thread that has an associated {@link Looper}.
   *
   * @param rendererCount The number of {@link TrackRenderer}s that will be passed to
   *     {@link #prepare(TrackRenderer[])}.
   * @param minBufferMs A minimum duration of data that must be buffered for playback to start
   *     or resume following a user action such as a seek.
   * @param minRebufferMs A minimum duration of data that must be buffered for playback to resume
   *     after a player invoked rebuffer (i.e. a rebuffer that occurs due to buffer depletion, and
   *     not due to a user action such as starting playback or seeking).
   */
  @SuppressLint("HandlerLeak")
  public ExoPlayerImpl(int rendererCount, int minBufferMs, int minRebufferMs) {
    Log.i(TAG, "Init " + ExoPlayerLibraryInfo.VERSION);
    this.playWhenReady = false;
    this.playbackState = STATE_IDLE;
    this.listeners = new CopyOnWriteArraySet<>();
    this.trackFormats = new MediaFormat[rendererCount][];
    this.selectedTrackIndices = new int[rendererCount];
    eventHandler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        ExoPlayerImpl.this.handleEvent(msg);
      }
    };
    internalPlayer = new ExoPlayerImplInternal(eventHandler, playWhenReady, selectedTrackIndices,
        minBufferMs, minRebufferMs);
  }

  @Override
  public Looper getPlaybackLooper() {
    return internalPlayer.getPlaybackLooper();
  }

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public int getPlaybackState() {
    return playbackState;
  }

  @Override
  public void prepare(TrackRenderer... renderers) {
    Arrays.fill(trackFormats, null);
    internalPlayer.prepare(renderers);
  }

  @Override
  public int getTrackCount(int rendererIndex) {
    return trackFormats[rendererIndex] != null ? trackFormats[rendererIndex].length : 0;
  }

  @Override
  public MediaFormat getTrackFormat(int rendererIndex, int trackIndex) {
    return trackFormats[rendererIndex][trackIndex];
  }

  @Override
  public void setSelectedTrack(int rendererIndex, int trackIndex) {
    if (selectedTrackIndices[rendererIndex] != trackIndex) {
      selectedTrackIndices[rendererIndex] = trackIndex;
      internalPlayer.setRendererSelectedTrack(rendererIndex, trackIndex);
    }
  }

  @Override
  public int getSelectedTrack(int rendererIndex) {
    return selectedTrackIndices[rendererIndex];
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (this.playWhenReady != playWhenReady) {
      this.playWhenReady = playWhenReady;
      pendingPlayWhenReadyAcks++;
      internalPlayer.setPlayWhenReady(playWhenReady);
      for (Listener listener : listeners) {
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
  public void seekTo(long positionMs) {
    internalPlayer.seekTo(positionMs);
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
  public void fadeIn(TrackRenderer audioRenderer, float fromVolume, float maxDeviceVolume, double duration, double velocity, FadeCallback onFinishFadeCallback) {
      fade(audioRenderer, correctFromVolumeValue(fromVolume, maxDeviceVolume) / maxDeviceVolume, 1, duration, velocity, onFinishFadeCallback);
  }

  @Override
  public void fadeOut(TrackRenderer audioRenderer, float fromVolume, float maxDeviceVolume, double duration, double velocity, FadeCallback onFinishFadeCallback) {
      fade(audioRenderer, correctFromVolumeValue(fromVolume, maxDeviceVolume) / maxDeviceVolume, 0, duration, velocity, onFinishFadeCallback);
  }

    private void fade(final TrackRenderer audioRenderer, float fromVolume, double toVolume, double duration, double velocity, final FadeCallback onFinishFadeCallback) {

        final Handler fadeHandler = new Handler();

        this.mFromVolume = checkValueBetween0and1((double) fromVolume);
        this.mToVolume = checkValueBetween0and1(toVolume);
        this.fadeDurationSeconds = duration;
        this.fadeVelocity = velocity;
        this.currentStep = 0;

        internalPlayer.sendMessage(audioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME, (float) mFromVolume);

        fadeHandler.post(new Runnable() {

            @Override
            public void run() {

                double currentTimeFrom0To1 = timeFrom0To1(currentStep, fadeDurationSeconds);
                double newVolume;
                double volumeMultiplier;

                //Calculate new volume depending of fade in or fade out using logarithmic formulas.
                if (mFromVolume < mToVolume) {
                    volumeMultiplier = Math.exp(fadeVelocity * (currentTimeFrom0To1 - 1)) * currentTimeFrom0To1;
                    newVolume = mFromVolume + (mToVolume - mFromVolume) * volumeMultiplier;

                } else {
                    volumeMultiplier = Math.exp(-fadeVelocity * currentTimeFrom0To1) * (1 - currentTimeFrom0To1);
                    newVolume = mToVolume - (mToVolume - mFromVolume) * volumeMultiplier;
                }

                internalPlayer.sendMessage(audioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME, (float) newVolume);

                currentStep++;

                if (!timerShouldStop()) {
                    long delay = (long) ((1 / volumeAlterationsPerSecond) * 1000);
                    fadeHandler.postDelayed(this, delay);
                } else {
                    if (onFinishFadeCallback != null) {
                        onFinishFadeCallback.onFadeFinished();
                    }
                }
            }
        });
    }

    // Assure than the from volume value is between the minimum and the maximum values
    private float correctFromVolumeValue(float fromVolume, float maxVolume) {
        return Math.min(Math.max(fromVolume, 0), maxVolume);
    }

    private boolean timerShouldStop() {
        double totalSteps = fadeDurationSeconds * volumeAlterationsPerSecond;
        return currentStep > totalSteps;
    }

    private double timeFrom0To1(int currentStep, double duration) {

        double totalSteps = duration * volumeAlterationsPerSecond;
        double result = currentStep / totalSteps;

        result = checkValueBetween0and1(result);

        return result;
    }

    private double checkValueBetween0and1(double value) {
        if(value < 0 ){
            return 0;
        }else if(value > 1){
            return 1;
        }
        return value;
    }

  @Override
  public void sendMessage(ExoPlayerComponent target, int messageType, Object message) {
    internalPlayer.sendMessage(target, messageType, message);
  }

  @Override
  public void blockingSendMessage(ExoPlayerComponent target, int messageType, Object message) {
    internalPlayer.blockingSendMessage(target, messageType, message);
  }

  @Override
  public long getDuration() {
    return internalPlayer.getDuration();
  }

  @Override
  public long getCurrentPosition() {
    return internalPlayer.getCurrentPosition();
  }

  @Override
  public long getBufferedPosition() {
    return internalPlayer.getBufferedPosition();
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
      case ExoPlayerImplInternal.MSG_PREPARED: {
        System.arraycopy(msg.obj, 0, trackFormats, 0, trackFormats.length);
        playbackState = msg.arg1;
        for (Listener listener : listeners) {
          listener.onPlayerStateChanged(playWhenReady, playbackState);
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_STATE_CHANGED: {
        playbackState = msg.arg1;
        for (Listener listener : listeners) {
          listener.onPlayerStateChanged(playWhenReady, playbackState);
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_SET_PLAY_WHEN_READY_ACK: {
        pendingPlayWhenReadyAcks--;
        if (pendingPlayWhenReadyAcks == 0) {
          for (Listener listener : listeners) {
            listener.onPlayWhenReadyCommitted();
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_ERROR: {
        ExoPlaybackException exception = (ExoPlaybackException) msg.obj;
        for (Listener listener : listeners) {
          listener.onPlayerError(exception);
        }
        break;
      }
    }
  }

}
