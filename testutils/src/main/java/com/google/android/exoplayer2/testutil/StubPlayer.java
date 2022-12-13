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
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BasePlayer;
import com.google.android.exoplayer2.DeviceInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.video.VideoSize;
import java.util.List;

/**
 * An abstract {@link Player} implementation that throws {@link UnsupportedOperationException} from
 * every method.
 */
public class StubPlayer extends BasePlayer {

  @Override
  public Looper getApplicationLooper() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(Listener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeListener(Listener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @State int getPlaybackState() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @PlaybackSuppressionReason int getPlaybackSuppressionReason() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public PlaybackException getPlayerError() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void prepare() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Commands getAvailableCommands() {
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
  public void seekTo(
      int mediaItemIndex,
      long positionMs,
      @Player.Command int seekCommand,
      boolean isRepeatingCurrentItem) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getSeekBackIncrement() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getSeekForwardIncrement() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getMaxSeekToPreviousPosition() {
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

  /**
   * @deprecated Use {@link #stop()} and {@link #clearMediaItems()} (if {@code reset} is true) or
   *     just {@link #stop()} (if {@code reset} is false). Any player error will be cleared when
   *     {@link #prepare() re-preparing} the player.
   */
  @Deprecated
  @Override
  public void stop(boolean reset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void release() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Tracks getCurrentTracks() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
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
  public int getCurrentMediaItemIndex() {
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
  public long getTotalBufferedDuration() {
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

  @Override
  public long getContentBufferedPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setVolume(float volume) {
    throw new UnsupportedOperationException();
  }

  @Override
  public float getVolume() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearVideoSurface() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VideoSize getVideoSize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Size getSurfaceSize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public CueGroup getCurrentCues() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getDeviceVolume() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDeviceMuted() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDeviceVolume(int volume) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void increaseDeviceVolume() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void decreaseDeviceVolume() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDeviceMuted(boolean muted) {
    throw new UnsupportedOperationException();
  }
}
