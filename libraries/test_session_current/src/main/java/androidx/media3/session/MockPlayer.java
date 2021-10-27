/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.media3.session;

import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroupArray;
import androidx.media3.common.TrackSelectionArray;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.TracksInfo;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** A mock implementation of {@link Player} for testing. */
@UnstableApi
public class MockPlayer implements Player {

  public final CountDownLatch countDownLatch;
  private final boolean changePlayerStateWithTransportControl;
  private final Looper applicationLooper;
  private final ArraySet<Listener> listeners = new ArraySet<>();

  @Nullable PlaybackException playerError;
  public AudioAttributes audioAttributes;
  public long seekPositionMs;
  public int seekWindowIndex;
  public long currentPosition;
  public long bufferedPosition;
  public long duration;
  public int bufferedPercentage;
  public long totalBufferedDuration;
  public long currentLiveOffset;
  public long contentPosition;
  public long contentDuration;
  public long contentBufferedPosition;
  public boolean isPlayingAd;
  public int currentAdGroupIndex;
  public int currentAdIndexInAdGroup;
  @Nullable public PlaybackParameters playbackParameters;
  public Timeline timeline;
  public MediaItem mediaItem;
  public List<MediaItem> mediaItems;
  public boolean resetPosition;
  public int startWindowIndex;
  public long startPositionMs;
  public MediaMetadata playlistMetadata;
  public int index;
  public int fromIndex;
  public int toIndex;
  public int newIndex;
  public int currentPeriodIndex;
  public int currentMediaItemIndex;
  @RepeatMode public int repeatMode;
  public boolean shuffleModeEnabled;
  public VideoSize videoSize;
  @Nullable public Surface surface;
  @Nullable public SurfaceHolder surfaceHolder;
  @Nullable public SurfaceView surfaceView;
  @Nullable public TextureView textureView;
  public float volume;
  public List<Cue> cues;
  public DeviceInfo deviceInfo;
  public int deviceVolume;
  public boolean deviceMuted;
  public boolean playWhenReady;
  @PlaybackSuppressionReason public int playbackSuppressionReason;
  @State public int playbackState;
  public boolean isPlaying;
  public boolean isLoading;
  public MediaMetadata mediaMetadata;
  public Commands commands;
  public long seekBackIncrementMs;
  public long seekForwardIncrementMs;
  public long maxSeekToPreviousPositionMs;
  public TrackSelectionParameters trackSelectionParameters;

  public boolean playCalled;
  public boolean pauseCalled;
  public boolean prepareCalled;
  public boolean stopCalled;
  public boolean releaseCalled;
  public boolean seekToDefaultPositionCalled;
  public boolean seekToDefaultPositionWithWindowIndexCalled;
  public boolean seekToCalled;
  public boolean seekToWithWindowIndexCalled;
  public boolean setPlaybackSpeedCalled;
  public boolean setPlaybackParametersCalled;
  public boolean setMediaItemCalled;
  public boolean setMediaItemWithStartPositionCalled;
  public boolean setMediaItemWithResetPositionCalled;
  public boolean setMediaItemsCalled;
  public boolean setMediaItemsWithResetPositionCalled;
  public boolean setMediaItemsWithStartWindowIndexCalled;
  public boolean setPlaylistMetadataCalled;
  public boolean addMediaItemCalled;
  public boolean addMediaItemWithIndexCalled;
  public boolean addMediaItemsCalled;
  public boolean addMediaItemsWithIndexCalled;
  public boolean removeMediaItemCalled;
  public boolean removeMediaItemsCalled;
  public boolean clearMediaItemsCalled;
  public boolean moveMediaItemCalled;
  public boolean moveMediaItemsCalled;
  public boolean seekToPreviousMediaItemCalled;
  public boolean seekToNextMediaItemCalled;
  public boolean seekToPreviousCalled;
  public boolean seekToNextCalled;
  public boolean setRepeatModeCalled;
  public boolean setShuffleModeCalled;
  public boolean setVolumeCalled;
  public boolean setDeviceVolumeCalled;
  public boolean increaseDeviceVolumeCalled;
  public boolean decreaseDeviceVolumeCalled;
  public boolean setDeviceMutedCalled;
  public boolean setPlayWhenReadyCalled;
  public boolean seekBackCalled;
  public boolean seekForwardCalled;
  public boolean setTrackSelectionParametersCalled;

  private MockPlayer(Builder builder) {
    countDownLatch = new CountDownLatch(builder.latchCount);
    changePlayerStateWithTransportControl = builder.changePlayerStateWithTransportControl;
    applicationLooper = builder.applicationLooper;

    playbackParameters = PlaybackParameters.DEFAULT;

    if (builder.itemCount > 0) {
      mediaItems = MediaTestUtils.createMediaItems(builder.itemCount);
      timeline = new PlaylistTimeline(mediaItems);
    } else {
      mediaItems = ImmutableList.of();
      timeline = Timeline.EMPTY;
    }

    // Sets default audio attributes to prevent setVolume() from being called with the play().
    audioAttributes = AudioAttributes.DEFAULT;

    mediaItem = MediaItem.EMPTY;
    playlistMetadata = MediaMetadata.EMPTY;
    index = C.INDEX_UNSET;
    fromIndex = C.INDEX_UNSET;
    toIndex = C.INDEX_UNSET;
    currentPeriodIndex = 0;
    currentMediaItemIndex = 0;
    repeatMode = Player.REPEAT_MODE_OFF;
    videoSize = VideoSize.UNKNOWN;
    volume = 1.0f;
    cues = ImmutableList.of();
    deviceInfo = DeviceInfo.UNKNOWN;
    seekPositionMs = C.TIME_UNSET;
    seekWindowIndex = C.INDEX_UNSET;
    mediaMetadata = MediaMetadata.EMPTY;

    currentPosition = 0;
    bufferedPosition = 0;
    duration = C.TIME_UNSET;
    currentLiveOffset = C.TIME_UNSET;
    contentDuration = C.TIME_UNSET;
    contentPosition = 0;
    contentBufferedPosition = 0;
    currentAdGroupIndex = C.INDEX_UNSET;
    currentAdIndexInAdGroup = C.INDEX_UNSET;

    // Invalid playbackState throws assertion error.
    playbackState = Player.STATE_IDLE;

    commands = new Player.Commands.Builder().addAllCommands().build();

    trackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
  }

  @Override
  public void release() {
    releaseCalled = true;
  }

  @Override
  public void stop() {
    stopCalled = true;
    countDownLatch.countDown();
  }

  @Deprecated
  @Override
  public void stop(boolean reset) {
    throw new UnsupportedOperationException();
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
  @Nullable
  public PlaybackException getPlayerError() {
    return playerError;
  }

  @Override
  public void play() {
    playCalled = true;
    countDownLatch.countDown();
    if (changePlayerStateWithTransportControl) {
      notifyPlayWhenReadyChanged(
          /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    }
  }

  @Override
  public void pause() {
    pauseCalled = true;
    countDownLatch.countDown();
    if (changePlayerStateWithTransportControl) {
      notifyPlayWhenReadyChanged(
          /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    }
  }

  @Override
  public void prepare() {
    prepareCalled = true;
    countDownLatch.countDown();
    if (changePlayerStateWithTransportControl) {
      notifyPlaybackStateChanged(Player.STATE_READY);
    }
  }

  @Override
  public void seekToDefaultPosition() {
    seekToDefaultPositionCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public void seekToDefaultPosition(int mediaItemIndex) {
    seekToDefaultPositionWithWindowIndexCalled = true;
    seekWindowIndex = mediaItemIndex;
    countDownLatch.countDown();
  }

  @Override
  public void seekTo(long positionMs) {
    seekToCalled = true;
    seekPositionMs = positionMs;
    countDownLatch.countDown();
  }

  @Override
  public void seekTo(int mediaItemIndex, long positionMs) {
    seekToWithWindowIndexCalled = true;
    seekWindowIndex = mediaItemIndex;
    seekPositionMs = positionMs;
    countDownLatch.countDown();
  }

  @Override
  public long getSeekBackIncrement() {
    return seekBackIncrementMs;
  }

  @Override
  public void seekBack() {
    seekBackCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public long getSeekForwardIncrement() {
    return seekForwardIncrementMs;
  }

  @Override
  public void seekForward() {
    seekForwardCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public long getCurrentPosition() {
    return currentPosition;
  }

  @Override
  public long getBufferedPosition() {
    return bufferedPosition;
  }

  @Override
  public boolean isPlayingAd() {
    return isPlayingAd;
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return currentAdGroupIndex;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return currentAdIndexInAdGroup;
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters == null ? PlaybackParameters.DEFAULT : playbackParameters;
  }

  @Override
  public long getDuration() {
    return duration;
  }

  @Override
  public int getBufferedPercentage() {
    return bufferedPercentage;
  }

  @Override
  public long getTotalBufferedDuration() {
    return totalBufferedDuration;
  }

  @Override
  public long getCurrentLiveOffset() {
    return currentLiveOffset;
  }

  @Override
  public long getContentDuration() {
    return contentDuration;
  }

  @Override
  public long getContentPosition() {
    return contentPosition;
  }

  @Override
  public long getContentBufferedPosition() {
    return contentBufferedPosition;
  }

  @Override
  public boolean isCommandAvailable(int command) {
    return commands.contains(command);
  }

  @Override
  public boolean canAdvertiseSession() {
    return true;
  }

  @Override
  public Commands getAvailableCommands() {
    return commands;
  }

  public void notifyPlayerError(@Nullable PlaybackException playerError) {
    if (this.playerError == playerError) {
      return;
    }
    this.playerError = playerError;
    for (Listener listener : listeners) {
      listener.onPlayerErrorChanged(playerError);
    }
    if (playerError == null) {
      return;
    }
    for (Listener listener : listeners) {
      listener.onPlayerError(playerError);
    }
  }

  public void notifyAvailableCommandsChanged(Commands commands) {
    if (Util.areEqual(this.commands, commands)) {
      return;
    }
    this.commands = commands;
    for (Listener listener : listeners) {
      listener.onAvailableCommandsChanged(commands);
    }
  }

  public void notifyPlayWhenReadyChanged(
      boolean playWhenReady, @PlayWhenReadyChangeReason int reason) {
    boolean playWhenReadyChanged = (this.playWhenReady != playWhenReady);
    boolean playbackSuppressionReasonChanged = (this.playbackSuppressionReason != reason);
    if (!playWhenReadyChanged && !playbackSuppressionReasonChanged) {
      return;
    }

    this.playWhenReady = playWhenReady;
    this.playbackSuppressionReason = reason;
    for (Listener listener : listeners) {
      if (playWhenReadyChanged) {
        listener.onPlayWhenReadyChanged(
            playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
      }
      if (playbackSuppressionReasonChanged) {
        listener.onPlaybackSuppressionReasonChanged(reason);
      }
    }
  }

  public void notifyPlaybackStateChanged(@State int playbackState) {
    if (this.playbackState == playbackState) {
      return;
    }
    this.playbackState = playbackState;
    for (Listener listener : listeners) {
      listener.onPlaybackStateChanged(playbackState);
    }
  }

  public void notifyIsPlayingChanged(boolean isPlaying) {
    if (this.isPlaying == isPlaying) {
      return;
    }
    this.isPlaying = isPlaying;
    for (Listener listener : listeners) {
      listener.onIsPlayingChanged(isPlaying);
    }
  }

  public void notifyIsLoadingChanged(boolean isLoading) {
    if (this.isLoading == isLoading) {
      return;
    }
    this.isLoading = isLoading;
    for (Listener listener : listeners) {
      listener.onIsLoadingChanged(isLoading);
    }
  }

  public void notifyPositionDiscontinuity(
      PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
    for (Listener listener : listeners) {
      listener.onPositionDiscontinuity(oldPosition, newPosition, reason);
    }
  }

  public void notifyMediaItemTransition(
      @Nullable MediaItem mediaItem, @MediaItemTransitionReason int reason) {
    for (Listener listener : listeners) {
      listener.onMediaItemTransition(mediaItem, reason);
    }
  }

  public void notifyPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    if (Util.areEqual(this.playbackParameters, playbackParameters)) {
      return;
    }
    this.playbackParameters = playbackParameters;
    for (Listener listener : listeners) {
      listener.onPlaybackParametersChanged(
          playbackParameters == null ? PlaybackParameters.DEFAULT : playbackParameters);
    }
  }

  public void notifyAudioAttributesChanged(AudioAttributes attrs) {
    for (Listener listener : listeners) {
      listener.onAudioAttributesChanged(attrs);
    }
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    return audioAttributes;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    setPlaybackParametersCalled = true;
    this.playbackParameters = playbackParameters;
    countDownLatch.countDown();
  }

  @Override
  public void setPlaybackSpeed(float speed) {
    setPlaybackSpeedCalled = true;
    playbackParameters = new PlaybackParameters(speed);
    countDownLatch.countDown();
  }

  @Override
  public float getVolume() {
    return volume;
  }

  @Override
  public void setVolume(float volume) {
    setVolumeCalled = true;
    this.volume = volume;
    countDownLatch.countDown();
  }

  @Override
  public List<Cue> getCurrentCues() {
    return cues;
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    return deviceInfo;
  }

  @Override
  public int getDeviceVolume() {
    return deviceVolume;
  }

  @Override
  public boolean isDeviceMuted() {
    return deviceMuted;
  }

  @Override
  public void setDeviceVolume(int volume) {
    setDeviceVolumeCalled = true;
    deviceVolume = volume;
    countDownLatch.countDown();
  }

  @Override
  public void increaseDeviceVolume() {
    increaseDeviceVolumeCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public void decreaseDeviceVolume() {
    decreaseDeviceVolumeCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public void setDeviceMuted(boolean muted) {
    setDeviceMutedCalled = true;
    deviceMuted = muted;
    countDownLatch.countDown();
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    this.setPlayWhenReadyCalled = true;
    this.playWhenReady = playWhenReady;
    countDownLatch.countDown();
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady;
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    return playbackSuppressionReason;
  }

  @Override
  @State
  public int getPlaybackState() {
    return playbackState;
  }

  @Override
  public boolean isPlaying() {
    return isPlaying;
  }

  @Override
  public boolean isLoading() {
    return isLoading;
  }

  /////////////////////////////////////////////////////////////////////////////////
  // Playlist APIs
  /////////////////////////////////////////////////////////////////////////////////

  @Override
  public Object getCurrentManifest() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Timeline getCurrentTimeline() {
    return timeline;
  }

  @Override
  public void setMediaItem(MediaItem mediaItem) {
    setMediaItemCalled = true;
    this.mediaItem = mediaItem;
    countDownLatch.countDown();
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    setMediaItemWithStartPositionCalled = true;
    this.mediaItem = mediaItem;
    this.startPositionMs = startPositionMs;
    countDownLatch.countDown();
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    setMediaItemWithResetPositionCalled = true;
    this.mediaItem = mediaItem;
    this.resetPosition = resetPosition;
    countDownLatch.countDown();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
    setMediaItemsCalled = true;
    this.mediaItems = mediaItems;
    countDownLatch.countDown();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    setMediaItemsWithResetPositionCalled = true;
    this.mediaItems = mediaItems;
    this.resetPosition = resetPosition;
    countDownLatch.countDown();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    setMediaItemsWithStartWindowIndexCalled = true;
    this.mediaItems = mediaItems;
    this.startWindowIndex = startIndex;
    this.startPositionMs = startPositionMs;
    countDownLatch.countDown();
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    return playlistMetadata;
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata playlistMetadata) {
    setPlaylistMetadataCalled = true;
    this.playlistMetadata = playlistMetadata;
    countDownLatch.countDown();
  }

  @Deprecated
  @Override
  public boolean isCurrentWindowDynamic() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentMediaItemDynamic() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public boolean isCurrentWindowLive() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentMediaItemLive() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public boolean isCurrentWindowSeekable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentMediaItemSeekable() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public MediaItem getCurrentMediaItem() {
    if (currentMediaItemIndex >= 0 && currentMediaItemIndex < timeline.getWindowCount()) {
      return timeline.getWindow(currentMediaItemIndex, new Timeline.Window()).mediaItem;
    }
    return null;
  }

  @Override
  public int getMediaItemCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MediaItem getMediaItemAt(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentPeriodIndex() {
    return currentPeriodIndex;
  }

  @Deprecated
  @Override
  public int getCurrentWindowIndex() {
    return getCurrentMediaItemIndex();
  }

  @Override
  public int getCurrentMediaItemIndex() {
    return currentMediaItemIndex;
  }

  @Deprecated
  @Override
  public int getPreviousWindowIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getPreviousMediaItemIndex() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public int getNextWindowIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getNextMediaItemIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMediaItem(MediaItem mediaItem) {
    addMediaItemCalled = true;
    this.mediaItem = mediaItem;
    countDownLatch.countDown();
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    addMediaItemWithIndexCalled = true;
    this.index = index;
    this.mediaItem = mediaItem;
    countDownLatch.countDown();
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    addMediaItemsCalled = true;
    this.mediaItems = mediaItems;
    countDownLatch.countDown();
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    addMediaItemsWithIndexCalled = true;
    this.index = index;
    this.mediaItems = mediaItems;
    countDownLatch.countDown();
  }

  @Override
  public void removeMediaItem(int index) {
    removeMediaItemCalled = true;
    this.index = index;
    countDownLatch.countDown();
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    removeMediaItemsCalled = true;
    this.fromIndex = fromIndex;
    this.toIndex = toIndex;
    countDownLatch.countDown();
  }

  @Override
  public void clearMediaItems() {
    clearMediaItemsCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    moveMediaItemCalled = true;
    this.index = currentIndex;
    this.newIndex = newIndex;
    countDownLatch.countDown();
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    moveMediaItemsCalled = true;
    this.fromIndex = fromIndex;
    this.toIndex = toIndex;
    this.newIndex = newIndex;
    countDownLatch.countDown();
  }

  @Deprecated
  @Override
  public boolean hasPrevious() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public boolean hasNext() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public boolean hasPreviousWindow() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public boolean hasNextWindow() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasPreviousMediaItem() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasNextMediaItem() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public void previous() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public void next() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  public void seekToPreviousWindow() {
    // TODO(b/202157117): Throw UnsupportedOperationException when all callers are migrated.
    seekToPreviousMediaItem();
  }

  @Deprecated
  @Override
  public void seekToNextWindow() {
    // TODO(b/202157117): Throw UnsupportedOperationException when all callers are migrated.
    seekToNextMediaItem();
  }

  @Override
  public void seekToPreviousMediaItem() {
    seekToPreviousMediaItemCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public void seekToNextMediaItem() {
    seekToNextMediaItemCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public void seekToPrevious() {
    seekToPreviousCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public long getMaxSeekToPreviousPosition() {
    return maxSeekToPreviousPositionMs;
  }

  @Override
  public void seekToNext() {
    seekToNextCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public int getRepeatMode() {
    return repeatMode;
  }

  @Override
  public void setRepeatMode(int repeatMode) {
    setRepeatModeCalled = true;
    this.repeatMode = repeatMode;
    countDownLatch.countDown();
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return shuffleModeEnabled;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    setShuffleModeCalled = true;
    this.shuffleModeEnabled = shuffleModeEnabled;
    countDownLatch.countDown();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    return mediaMetadata;
  }

  public void notifyShuffleModeEnabledChanged() {
    boolean shuffleModeEnabled = this.shuffleModeEnabled;
    for (Listener listener : listeners) {
      listener.onShuffleModeEnabledChanged(shuffleModeEnabled);
    }
  }

  public void notifyRepeatModeChanged() {
    int repeatMode = this.repeatMode;
    for (Listener listener : listeners) {
      listener.onRepeatModeChanged(repeatMode);
    }
  }

  public void notifySeekBackIncrementChanged() {
    long seekBackIncrementMs = this.seekBackIncrementMs;
    for (Listener listener : listeners) {
      listener.onSeekBackIncrementChanged(seekBackIncrementMs);
    }
  }

  public void notifySeekForwardIncrementChanged() {
    long seekForwardIncrementMs = this.seekForwardIncrementMs;
    for (Listener listener : listeners) {
      listener.onSeekForwardIncrementChanged(seekForwardIncrementMs);
    }
  }

  public void notifyTimelineChanged(@TimelineChangeReason int reason) {
    Timeline timeline = this.timeline;
    for (Listener listener : listeners) {
      listener.onTimelineChanged(timeline, reason);
    }
  }

  public void notifyPlaylistMetadataChanged() {
    MediaMetadata metadata = playlistMetadata;
    for (Listener listener : listeners) {
      listener.onPlaylistMetadataChanged(metadata);
    }
  }

  @Override
  public VideoSize getVideoSize() {
    if (videoSize == null) {
      videoSize = VideoSize.UNKNOWN;
    }
    return videoSize;
  }

  public void notifyVideoSizeChanged(VideoSize videoSize) {
    for (Listener listener : listeners) {
      listener.onVideoSizeChanged(videoSize);
    }
  }

  @Override
  public void clearVideoSurface() {
    surface = null;
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    if (surface != null && surface == this.surface) {
      this.surface = null;
    }
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    this.surface = surface;
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    this.surfaceHolder = surfaceHolder;
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
      this.surfaceHolder = null;
    }
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    this.surfaceView = surfaceView;
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    if (surfaceView != null && surfaceView == this.surfaceView) {
      this.surfaceView = null;
    }
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    this.textureView = textureView;
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    if (textureView != null && textureView == this.textureView) {
      this.textureView = null;
    }
  }

  public boolean surfaceExists() {
    return surface != null;
  }

  public void notifyDeviceVolumeChanged() {
    for (Listener listener : listeners) {
      listener.onDeviceVolumeChanged(deviceVolume, deviceMuted);
    }
  }

  public void notifyCuesChanged() {
    for (Listener listener : listeners) {
      listener.onCues(cues);
    }
  }

  public void notifyDeviceInfoChanged() {
    for (Listener listener : listeners) {
      listener.onDeviceInfoChanged(deviceInfo);
    }
  }

  public void notifyMediaMetadataChanged() {
    for (Listener listener : listeners) {
      listener.onMediaMetadataChanged(mediaMetadata);
    }
  }

  public void notifyRenderedFirstFrame() {
    for (Listener listener : listeners) {
      listener.onRenderedFirstFrame();
    }
  }

  public void notifyMaxSeekToPreviousPositionChanged() {
    for (Listener listener : listeners) {
      listener.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs);
    }
  }

  public void notifyTrackSelectionParametersChanged() {
    for (Listener listener : listeners) {
      listener.onTrackSelectionParametersChanged(trackSelectionParameters);
    }
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
  public TracksInfo getCurrentTracksInfo() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    return trackSelectionParameters;
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    setTrackSelectionParametersCalled = true;
    trackSelectionParameters = parameters;
    countDownLatch.countDown();
  }

  @Override
  public Looper getApplicationLooper() {
    return applicationLooper;
  }

  /** Builder for {@link MockPlayer}. */
  public static final class Builder {

    private int latchCount;
    private boolean changePlayerStateWithTransportControl;
    private Looper applicationLooper;
    private int itemCount;

    public Builder() {
      applicationLooper = Util.getCurrentOrMainLooper();
    }

    public Builder setLatchCount(int latchCount) {
      this.latchCount = latchCount;
      return this;
    }

    public Builder setChangePlayerStateWithTransportControl(
        boolean changePlayerStateWithTransportControl) {
      this.changePlayerStateWithTransportControl = changePlayerStateWithTransportControl;
      return this;
    }

    public Builder setApplicationLooper(Looper applicationLooper) {
      this.applicationLooper = applicationLooper;
      return this;
    }

    public Builder setMediaItems(int itemCount) {
      this.itemCount = itemCount;
      return this;
    }

    public MockPlayer build() {
      return new MockPlayer(this);
    }
  }
}
