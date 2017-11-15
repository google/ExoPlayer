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
package com.google.android.exoplayer2.testutil;

import android.os.Looper;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

/**
 * An abstract {@link ExoPlayer} implementation that throws {@link UnsupportedOperationException}
 * from every method.
 */
public abstract class StubExoPlayer implements ExoPlayer {

  @Override
  public Looper getPlaybackLooper() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(Player.EventListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getPlaybackState() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void prepare(MediaSource mediaSource) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getPlayWhenReady() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getRepeatMode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getShuffleModeEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isLoading() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void seekToDefaultPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void seekToDefaultPosition(int windowIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void seekTo(long positionMs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void stop() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void release() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void sendMessages(ExoPlayerMessage... messages) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void blockingSendMessages(ExoPlayerMessage... messages) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getRendererCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getRendererType(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getCurrentManifest() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Timeline getCurrentTimeline() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentPeriodIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentWindowIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getNextWindowIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getPreviousWindowIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getDuration() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getCurrentPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getBufferedPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getBufferedPercentage() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentWindowDynamic() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentWindowSeekable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isPlayingAd() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getContentPosition() {
    throw new UnsupportedOperationException();
  }

}
