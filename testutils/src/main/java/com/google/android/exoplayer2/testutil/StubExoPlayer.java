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
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.video.VideoSize;
import java.util.List;

/**
 * An abstract {@link ExoPlayer} implementation that throws {@link UnsupportedOperationException}
 * from every method.
 */
public class StubExoPlayer extends BasePlayer implements ExoPlayer {

  @Override
  public AudioComponent getAudioComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VideoComponent getVideoComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TextComponent getTextComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MetadataComponent getMetadataComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DeviceComponent getDeviceComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Looper getPlaybackLooper() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Looper getApplicationLooper() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Clock getClock() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(Listener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(Player.EventListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeListener(Listener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addAudioOffloadListener(AudioOffloadListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeAudioOffloadListener(AudioOffloadListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  @State
  public int getPlaybackState() {
    throw new UnsupportedOperationException();
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ExoPlaybackException getPlayerError() {
    throw new UnsupportedOperationException();
  }

  /** @deprecated Use {@link #prepare()} instead. */
  @Deprecated
  @Override
  public void retry() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource)} and {@link ExoPlayer#prepare()} instead.
   */
  @Deprecated
  @Override
  public void prepare() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource)} and {@link ExoPlayer#prepare()} instead.
   */
  @Deprecated
  @Override
  public void prepare(MediaSource mediaSource) {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource, boolean)} and {@link ExoPlayer#prepare()}
   *     instead.
   */
  @Deprecated
  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaItems(
      List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaSource(MediaSource mediaSource) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, long startPositionMs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMediaSources(
      List<MediaSource> mediaSources, int startWindowIndex, long startPositionMs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMediaSource(MediaSource mediaSource) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMediaSource(int index, MediaSource mediaSource) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMediaSources(List<MediaSource> mediaSources) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMediaSources(int index, List<MediaSource> mediaSources) {
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
  public void setShuffleOrder(ShuffleOrder shuffleOrder) {
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
  public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SeekParameters getSeekParameters() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void stop(boolean reset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void release() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PlayerMessage createMessage(PlayerMessage.Target target) {
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
  @Nullable
  public TrackSelector getTrackSelector() {
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
  public List<Metadata> getCurrentStaticMetadata() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
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
  public void setVolume(float audioVolume) {
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
  public List<Cue> getCurrentCues() {
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

  @Override
  public void setForegroundMode(boolean foregroundMode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getPauseAtEndOfMediaItems() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void experimentalSetOffloadSchedulingEnabled(boolean offloadSchedulingEnabled) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean experimentalIsSleepingForOffload() {
    throw new UnsupportedOperationException();
  }
}
