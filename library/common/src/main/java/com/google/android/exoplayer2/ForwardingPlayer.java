/*
 * Copyright 2021 The Android Open Source Project
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

import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.video.VideoSize;
import java.util.List;

/**
 * A {@link Player} that forwards operations to another {@link Player}. Applications can use this
 * class to suppress or modify specific operations, by overriding the respective methods.
 */
public class ForwardingPlayer implements Player {

  private final Player player;

  /** Creates a new instance that forwards all operations to {@code player}. */
  public ForwardingPlayer(Player player) {
    this.player = player;
  }

  @Override
  public Looper getApplicationLooper() {
    return player.getApplicationLooper();
  }

  @Deprecated
  @Override
  public void addListener(EventListener listener) {
    player.addListener(new ForwardingEventListener(this, listener));
  }

  @Override
  public void addListener(Listener listener) {
    player.addListener(new ForwardingListener(this, listener));
  }

  @Deprecated
  @Override
  public void removeListener(EventListener listener) {
    player.removeListener(new ForwardingEventListener(this, listener));
  }

  @Override
  public void removeListener(Listener listener) {
    player.removeListener(new ForwardingListener(this, listener));
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
    player.setMediaItems(mediaItems);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    player.setMediaItems(mediaItems, resetPosition);
  }

  @Override
  public void setMediaItems(
      List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs) {
    player.setMediaItems(mediaItems, startWindowIndex, startPositionMs);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem) {
    player.setMediaItem(mediaItem);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    player.setMediaItem(mediaItem, startPositionMs);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    player.setMediaItem(mediaItem, resetPosition);
  }

  @Override
  public void addMediaItem(MediaItem mediaItem) {
    player.addMediaItem(mediaItem);
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    player.addMediaItem(index, mediaItem);
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    player.addMediaItems(mediaItems);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    player.addMediaItems(index, mediaItems);
  }

  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    player.moveMediaItem(currentIndex, newIndex);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    player.moveMediaItems(fromIndex, toIndex, newIndex);
  }

  @Override
  public void removeMediaItem(int index) {
    player.removeMediaItem(index);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    player.removeMediaItems(fromIndex, toIndex);
  }

  @Override
  public void clearMediaItems() {
    player.clearMediaItems();
  }

  @Override
  public boolean isCommandAvailable(@Command int command) {
    return player.isCommandAvailable(command);
  }

  @Override
  public Commands getAvailableCommands() {
    return player.getAvailableCommands();
  }

  @Override
  public void prepare() {
    player.prepare();
  }

  @Override
  public int getPlaybackState() {
    return player.getPlaybackState();
  }

  @Override
  public int getPlaybackSuppressionReason() {
    return player.getPlaybackSuppressionReason();
  }

  @Override
  public boolean isPlaying() {
    return player.isPlaying();
  }

  @Nullable
  @Override
  public PlaybackException getPlayerError() {
    return player.getPlayerError();
  }

  @Override
  public void play() {
    player.play();
  }

  @Override
  public void pause() {
    player.pause();
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    player.setPlayWhenReady(playWhenReady);
  }

  @Override
  public boolean getPlayWhenReady() {
    return player.getPlayWhenReady();
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    player.setRepeatMode(repeatMode);
  }

  @Override
  public int getRepeatMode() {
    return player.getRepeatMode();
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    player.setShuffleModeEnabled(shuffleModeEnabled);
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return player.getShuffleModeEnabled();
  }

  @Override
  public boolean isLoading() {
    return player.isLoading();
  }

  @Override
  public void seekToDefaultPosition() {
    player.seekToDefaultPosition();
  }

  @Override
  public void seekToDefaultPosition(int windowIndex) {
    player.seekToDefaultPosition(windowIndex);
  }

  @Override
  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    player.seekTo(windowIndex, positionMs);
  }

  @Override
  public long getSeekBackIncrement() {
    return player.getSeekBackIncrement();
  }

  @Override
  public void seekBack() {
    player.seekBack();
  }

  @Override
  public long getSeekForwardIncrement() {
    return player.getSeekForwardIncrement();
  }

  @Override
  public void seekForward() {
    player.seekForward();
  }

  @Deprecated
  @Override
  public boolean hasPrevious() {
    return player.hasPrevious();
  }

  @Override
  public boolean hasPreviousWindow() {
    return player.hasPreviousWindow();
  }

  @Deprecated
  @Override
  public void previous() {
    player.previous();
  }

  @Override
  public void seekToPreviousWindow() {
    player.seekToPreviousWindow();
  }

  @Override
  public void seekToPrevious() {
    player.seekToPrevious();
  }

  @Override
  public int getMaxSeekToPreviousPosition() {
    return player.getMaxSeekToPreviousPosition();
  }

  @Deprecated
  @Override
  public boolean hasNext() {
    return player.hasNext();
  }

  @Override
  public boolean hasNextWindow() {
    return player.hasNextWindow();
  }

  @Deprecated
  @Override
  public void next() {
    player.next();
  }

  @Override
  public void seekToNextWindow() {
    player.seekToNextWindow();
  }

  @Override
  public void seekToNext() {
    player.seekToNext();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    player.setPlaybackParameters(playbackParameters);
  }

  @Override
  public void setPlaybackSpeed(float speed) {
    player.setPlaybackSpeed(speed);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return player.getPlaybackParameters();
  }

  @Override
  public void stop() {
    player.stop();
  }

  @Deprecated
  @Override
  public void stop(boolean reset) {
    player.stop(reset);
  }

  @Override
  public void release() {
    player.release();
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return player.getCurrentTrackGroups();
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return player.getCurrentTrackSelections();
  }

  @Deprecated
  @Override
  public List<Metadata> getCurrentStaticMetadata() {
    return player.getCurrentStaticMetadata();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    return player.getMediaMetadata();
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    return player.getPlaylistMetadata();
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
    player.setPlaylistMetadata(mediaMetadata);
  }

  @Nullable
  @Override
  public Object getCurrentManifest() {
    return player.getCurrentManifest();
  }

  @Override
  public Timeline getCurrentTimeline() {
    return player.getCurrentTimeline();
  }

  @Override
  public int getCurrentPeriodIndex() {
    return player.getCurrentPeriodIndex();
  }

  @Override
  public int getCurrentWindowIndex() {
    return player.getCurrentWindowIndex();
  }

  @Override
  public int getNextWindowIndex() {
    return player.getNextWindowIndex();
  }

  @Override
  public int getPreviousWindowIndex() {
    return player.getPreviousWindowIndex();
  }

  @Nullable
  @Override
  public MediaItem getCurrentMediaItem() {
    return player.getCurrentMediaItem();
  }

  @Override
  public int getMediaItemCount() {
    return player.getMediaItemCount();
  }

  @Override
  public MediaItem getMediaItemAt(int index) {
    return player.getMediaItemAt(index);
  }

  @Override
  public long getDuration() {
    return player.getDuration();
  }

  @Override
  public long getCurrentPosition() {
    return player.getCurrentPosition();
  }

  @Override
  public long getBufferedPosition() {
    return player.getBufferedPosition();
  }

  @Override
  public int getBufferedPercentage() {
    return player.getBufferedPercentage();
  }

  @Override
  public long getTotalBufferedDuration() {
    return player.getTotalBufferedDuration();
  }

  @Override
  public boolean isCurrentWindowDynamic() {
    return player.isCurrentWindowDynamic();
  }

  @Override
  public boolean isCurrentWindowLive() {
    return player.isCurrentWindowLive();
  }

  @Override
  public long getCurrentLiveOffset() {
    return player.getCurrentLiveOffset();
  }

  @Override
  public boolean isCurrentWindowSeekable() {
    return player.isCurrentWindowSeekable();
  }

  @Override
  public boolean isPlayingAd() {
    return player.isPlayingAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return player.getCurrentAdGroupIndex();
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return player.getCurrentAdIndexInAdGroup();
  }

  @Override
  public long getContentDuration() {
    return player.getContentDuration();
  }

  @Override
  public long getContentPosition() {
    return player.getContentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    return player.getContentBufferedPosition();
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    return player.getAudioAttributes();
  }

  @Override
  public void setVolume(float audioVolume) {
    player.setVolume(audioVolume);
  }

  @Override
  public float getVolume() {
    return player.getVolume();
  }

  @Override
  public VideoSize getVideoSize() {
    return player.getVideoSize();
  }

  @Override
  public void clearVideoSurface() {
    player.clearVideoSurface();
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    player.clearVideoSurface(surface);
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    player.setVideoSurface(surface);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    player.setVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    player.clearVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    player.setVideoSurfaceView(surfaceView);
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    player.clearVideoSurfaceView(surfaceView);
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    player.setVideoTextureView(textureView);
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    player.clearVideoTextureView(textureView);
  }

  @Override
  public List<Cue> getCurrentCues() {
    return player.getCurrentCues();
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    return player.getDeviceInfo();
  }

  @Override
  public int getDeviceVolume() {
    return player.getDeviceVolume();
  }

  @Override
  public boolean isDeviceMuted() {
    return player.isDeviceMuted();
  }

  @Override
  public void setDeviceVolume(int volume) {
    player.setDeviceVolume(volume);
  }

  @Override
  public void increaseDeviceVolume() {
    player.increaseDeviceVolume();
  }

  @Override
  public void decreaseDeviceVolume() {
    player.decreaseDeviceVolume();
  }

  @Override
  public void setDeviceMuted(boolean muted) {
    player.setDeviceMuted(muted);
  }

  /** Returns the {@link Player} to which operations are forwarded. */
  public Player getWrappedPlayer() {
    return player;
  }

  @SuppressWarnings("deprecation") // Use of deprecated type for backwards compatibility.
  private static class ForwardingEventListener implements EventListener {

    private final ForwardingPlayer forwardingPlayer;
    private final EventListener eventListener;

    private ForwardingEventListener(
        ForwardingPlayer forwardingPlayer, EventListener eventListener) {
      this.forwardingPlayer = forwardingPlayer;
      this.eventListener = eventListener;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, @TimelineChangeReason int reason) {
      eventListener.onTimelineChanged(timeline, reason);
    }

    @Override
    public void onMediaItemTransition(
        @Nullable MediaItem mediaItem, @MediaItemTransitionReason int reason) {
      eventListener.onMediaItemTransition(mediaItem, reason);
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      eventListener.onTracksChanged(trackGroups, trackSelections);
    }

    @Deprecated
    @Override
    public void onStaticMetadataChanged(List<Metadata> metadataList) {
      eventListener.onStaticMetadataChanged(metadataList);
    }

    @Override
    public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
      eventListener.onMediaMetadataChanged(mediaMetadata);
    }

    @Override
    public void onPlaylistMetadataChanged(MediaMetadata mediaMetadata) {
      eventListener.onPlaylistMetadataChanged(mediaMetadata);
    }

    @Override
    public void onIsLoadingChanged(boolean isLoading) {
      eventListener.onIsLoadingChanged(isLoading);
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
      eventListener.onIsLoadingChanged(isLoading);
    }

    @Override
    public void onAvailableCommandsChanged(Commands availableCommands) {
      eventListener.onAvailableCommandsChanged(availableCommands);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, @State int playbackState) {
      eventListener.onPlayerStateChanged(playWhenReady, playbackState);
    }

    @Override
    public void onPlaybackStateChanged(@State int playbackState) {
      eventListener.onPlaybackStateChanged(playbackState);
    }

    @Override
    public void onPlayWhenReadyChanged(
        boolean playWhenReady, @PlayWhenReadyChangeReason int reason) {
      eventListener.onPlayWhenReadyChanged(playWhenReady, reason);
    }

    @Override
    public void onPlaybackSuppressionReasonChanged(
        @PlayWhenReadyChangeReason int playbackSuppressionReason) {
      eventListener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
      eventListener.onIsPlayingChanged(isPlaying);
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode int repeatMode) {
      eventListener.onRepeatModeChanged(repeatMode);
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
      eventListener.onShuffleModeEnabledChanged(shuffleModeEnabled);
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      eventListener.onPlayerError(error);
    }

    @Override
    public void onPlayerErrorChanged(@Nullable PlaybackException error) {
      eventListener.onPlayerErrorChanged(error);
    }

    @Override
    public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
      eventListener.onPositionDiscontinuity(reason);
    }

    @Override
    public void onPositionDiscontinuity(
        PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
      eventListener.onPositionDiscontinuity(oldPosition, newPosition, reason);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      eventListener.onPlaybackParametersChanged(playbackParameters);
    }

    @Override
    public void onSeekBackIncrementChanged(long seekBackIncrementMs) {
      eventListener.onSeekBackIncrementChanged(seekBackIncrementMs);
    }

    @Override
    public void onSeekForwardIncrementChanged(long seekForwardIncrementMs) {
      eventListener.onSeekForwardIncrementChanged(seekForwardIncrementMs);
    }

    @Override
    public void onMaxSeekToPreviousPositionChanged(int maxSeekToPreviousPositionMs) {
      eventListener.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs);
    }

    @Override
    public void onSeekProcessed() {
      eventListener.onSeekProcessed();
    }

    @Override
    public void onEvents(Player player, Events events) {
      // Replace player with forwarding player.
      eventListener.onEvents(forwardingPlayer, events);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ForwardingEventListener)) {
        return false;
      }

      ForwardingEventListener that = (ForwardingEventListener) o;

      if (!forwardingPlayer.equals(that.forwardingPlayer)) {
        return false;
      }
      return eventListener.equals(that.eventListener);
    }

    @Override
    public int hashCode() {
      int result = forwardingPlayer.hashCode();
      result = 31 * result + eventListener.hashCode();
      return result;
    }
  }

  private static final class ForwardingListener extends ForwardingEventListener
      implements Listener {

    private final Listener listener;

    public ForwardingListener(ForwardingPlayer forwardingPlayer, Listener listener) {
      super(forwardingPlayer, listener);
      this.listener = listener;
    }

    // VideoListener methods.

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
      listener.onVideoSizeChanged(videoSize);
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onVideoSizeChanged(
        int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
      listener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
    }

    @Override
    public void onSurfaceSizeChanged(int width, int height) {
      listener.onSurfaceSizeChanged(width, height);
    }

    @Override
    public void onRenderedFirstFrame() {
      listener.onRenderedFirstFrame();
    }

    // AudioListener methods

    @Override
    public void onAudioSessionIdChanged(int audioSessionId) {
      listener.onAudioSessionIdChanged(audioSessionId);
    }

    @Override
    public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
      listener.onAudioAttributesChanged(audioAttributes);
    }

    @Override
    public void onVolumeChanged(float volume) {
      listener.onVolumeChanged(volume);
    }

    @Override
    public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
      listener.onSkipSilenceEnabledChanged(skipSilenceEnabled);
    }

    // TextOutput methods.

    @Override
    public void onCues(List<Cue> cues) {
      listener.onCues(cues);
    }

    // MetadataOutput methods.

    @Override
    public void onMetadata(Metadata metadata) {
      listener.onMetadata(metadata);
    }

    // DeviceListener callbacks

    @Override
    public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
      listener.onDeviceInfoChanged(deviceInfo);
    }

    @Override
    public void onDeviceVolumeChanged(int volume, boolean muted) {
      listener.onDeviceVolumeChanged(volume, muted);
    }
  }
}
