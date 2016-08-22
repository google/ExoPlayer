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
package com.google.android.exoplayer2.ui;

import android.widget.MediaController.MediaPlayerControl;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;

/**
 * An implementation of {@link MediaPlayerControl} for controlling an {@link ExoPlayer} instance.
 * This class is provided for convenience, however it is expected that most applications will
 * implement their own player controls and therefore not require this class.
 */
public class PlayerControl implements MediaPlayerControl {

  private final ExoPlayer player;

  /**
   * @param player The player to control.
   */
  public PlayerControl(ExoPlayer player) {
    this.player = player;
  }

  @Override
  public boolean canPause() {
    return true;
  }

  @Override
  public boolean canSeekBackward() {
    return true;
  }

  @Override
  public boolean canSeekForward() {
    return true;
  }

  /**
   * This is an unsupported operation.
   * <p>
   * Application of audio effects is dependent on the audio renderer used. When using
   * {@link MediaCodecAudioRenderer}, the recommended approach is to extend the class and override
   * {@link MediaCodecAudioRenderer#onAudioSessionId}.
   *
   * @throws UnsupportedOperationException Always thrown.
   */
  @Override
  public int getAudioSessionId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getBufferPercentage() {
    return player.getBufferedPercentage();
  }

  @Override
  public int getCurrentPosition() {
    long position = player.getCurrentPosition();
    return position == C.TIME_UNSET ? 0 : (int) position;
  }

  @Override
  public int getDuration() {
    long duration = player.getDuration();
    return duration == C.TIME_UNSET ? 0 : (int) duration;
  }

  @Override
  public boolean isPlaying() {
    return player.getPlayWhenReady();
  }

  @Override
  public void start() {
    player.setPlayWhenReady(true);
  }

  @Override
  public void pause() {
    player.setPlayWhenReady(false);
  }

  @Override
  public void seekTo(int timeMillis) {
    player.seekTo(timeMillis);
  }

}
