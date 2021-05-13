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
package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.session.MediaUtils.createPlayerCommandsWithAllCommands;

import android.os.Looper;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** A mock implementation of {@link SessionPlayer} for testing. */
public class MockPlayer implements SessionPlayer {

  @NonNull public final CountDownLatch countDownLatch;
  private final boolean changePlayerStateWithTransportControl;
  @NonNull private final Looper applicationLooper;
  private final ArraySet<PlayerCallback> callbacks = new ArraySet<>();

  @Nullable ExoPlaybackException playerError;
  @NonNull public AudioAttributes audioAttributes;
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
  public List<MediaItem> mediaItems;
  public boolean resetPosition;
  public int startWindowIndex;
  public long startPositionMs;
  public MediaMetadata playlistMetadata;
  @Nullable public MediaItem currentMediaItem;
  public int index;
  public int fromIndex;
  public int toIndex;
  public int newIndex;
  public int currentPeriodIndex;
  public int currentWindowIndex;
  @RepeatMode public int repeatMode;
  public boolean shuffleModeEnabled;
  public VideoSize videoSize;
  @Nullable public Surface surface;
  public float volume;
  public DeviceInfo deviceInfo;
  public int deviceVolume;
  public boolean deviceMuted;
  public boolean playWhenReady;
  @PlaybackSuppressionReason public int playbackSuppressionReason;
  @State public int playbackState;
  public boolean isPlaying;
  public boolean isLoading;
  public Commands commands;

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
  public boolean setMediaItemsCalled;
  public boolean setPlaylistMetadataCalled;
  public boolean addMediaItemsCalled;
  public boolean removeMediaItemsCalled;
  public boolean moveMediaItemsCalled;
  public boolean previousCalled;
  public boolean nextCalled;
  public boolean setRepeatModeCalled;
  public boolean setShuffleModeCalled;
  public boolean setVolumeCalled;
  public boolean setDeviceVolumeCalled;
  public boolean increaseDeviceVolumeCalled;
  public boolean decreaseDeviceVolumeCalled;
  public boolean setDeviceMutedCalled;
  public boolean setPlayWhenReadyCalled;

  private MockPlayer(Builder builder) {
    countDownLatch = new CountDownLatch(builder.latchCount);
    changePlayerStateWithTransportControl = builder.changePlayerStateWithTransportControl;
    applicationLooper = builder.applicationLooper;

    playbackParameters = PlaybackParameters.DEFAULT;

    // Sets default audio attributes to prevent setVolume() from being called with the play().
    audioAttributes = AudioAttributes.DEFAULT;

    timeline = Timeline.EMPTY;
    playlistMetadata = MediaMetadata.EMPTY;
    index = C.INDEX_UNSET;
    fromIndex = C.INDEX_UNSET;
    toIndex = C.INDEX_UNSET;
    currentPeriodIndex = C.INDEX_UNSET;
    currentWindowIndex = C.INDEX_UNSET;
    repeatMode = Player.REPEAT_MODE_OFF;
    videoSize = VideoSize.UNKNOWN;
    volume = 1.0f;
    deviceInfo = DeviceInfo.UNKNOWN;
    seekPositionMs = C.TIME_UNSET;
    seekWindowIndex = C.INDEX_UNSET;

    currentPosition = C.TIME_UNSET;
    duration = C.TIME_UNSET;
    currentLiveOffset = C.TIME_UNSET;
    contentDuration = C.TIME_UNSET;
    contentPosition = C.TIME_UNSET;
    contentBufferedPosition = C.TIME_UNSET;
    currentAdGroupIndex = C.INDEX_UNSET;
    currentAdIndexInAdGroup = C.INDEX_UNSET;

    // Invalid playbackState throws assertion error.
    playbackState = Player.STATE_IDLE;

    commands = createPlayerCommandsWithAllCommands();
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
  public void addListener(@NonNull PlayerCallback callback) {
    callbacks.add(callback);
  }

  @Override
  public void removeListener(@NonNull PlayerCallback callback) {
    callbacks.remove(callback);
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlayerError() {
    return playerError;
  }

  @Override
  public void play() {
    playCalled = true;
    countDownLatch.countDown();
    if (changePlayerStateWithTransportControl) {
      notifyPlayWhenReadyChanged(
          /* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    }
  }

  @Override
  public void pause() {
    pauseCalled = true;
    countDownLatch.countDown();
    if (changePlayerStateWithTransportControl) {
      notifyPlayWhenReadyChanged(
          /* playWhenReady= */ false, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
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
  public void seekToDefaultPosition(int windowIndex) {
    seekToDefaultPositionWithWindowIndexCalled = true;
    seekWindowIndex = windowIndex;
    countDownLatch.countDown();
  }

  @Override
  public void seekTo(long positionMs) {
    seekToCalled = true;
    seekPositionMs = positionMs;
    countDownLatch.countDown();
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    seekToWithWindowIndexCalled = true;
    seekWindowIndex = windowIndex;
    seekPositionMs = positionMs;
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
  public Commands getAvailableCommands() {
    return commands;
  }

  public void notifyPlayerError(@Nullable ExoPlaybackException playerError) {
    if (this.playerError == playerError) {
      return;
    }
    this.playerError = playerError;
    // TODO(b/184262323): Remove this check when migrating to onPlayerErrorChanged() that takes
    //                    nullable error.
    if (playerError == null) {
      return;
    }
    for (PlayerCallback callback : callbacks) {
      callback.onPlayerError(playerError);
    }
  }

  public void notifyAvailableCommandsChanged(Commands commands) {
    if (Util.areEqual(this.commands, commands)) {
      return;
    }
    this.commands = commands;
    for (PlayerCallback callback : callbacks) {
      callback.onAvailableCommandsChanged(commands);
    }
  }

  public void notifyPlayWhenReadyChanged(
      boolean playWhenReady, @PlaybackSuppressionReason int reason) {
    boolean playWhenReadyChanged = (this.playWhenReady != playWhenReady);
    boolean playbackSuppressionReasonChanged = (this.playbackSuppressionReason != reason);
    if (!playWhenReadyChanged && !playbackSuppressionReasonChanged) {
      return;
    }

    this.playWhenReady = playWhenReady;
    this.playbackSuppressionReason = reason;
    for (PlayerCallback callback : callbacks) {
      if (playWhenReadyChanged) {
        callback.onPlayWhenReadyChanged(
            playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
      }
      if (playbackSuppressionReasonChanged) {
        callback.onPlaybackSuppressionReasonChanged(reason);
      }
    }
  }

  public void notifyPlaybackStateChanged(@State int playbackState) {
    if (this.playbackState == playbackState) {
      return;
    }
    this.playbackState = playbackState;
    for (PlayerCallback callback : callbacks) {
      callback.onPlaybackStateChanged(playbackState);
    }
  }

  public void notifyIsPlayingChanged(boolean isPlaying) {
    if (this.isPlaying == isPlaying) {
      return;
    }
    this.isPlaying = isPlaying;
    for (PlayerCallback callback : callbacks) {
      callback.onIsPlayingChanged(isPlaying);
    }
  }

  public void notifyIsLoadingChanged(boolean isLoading) {
    if (this.isLoading == isLoading) {
      return;
    }
    this.isLoading = isLoading;
    for (PlayerCallback callback : callbacks) {
      callback.onIsLoadingChanged(isLoading);
    }
  }

  public void notifyPositionDiscontinuity(
      PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
    for (PlayerCallback callback : callbacks) {
      callback.onPositionDiscontinuity(oldPosition, newPosition, reason);
    }
  }

  public void notifyMediaItemTransition(
      @Nullable MediaItem mediaItem, @MediaItemTransitionReason int reason) {
    for (PlayerCallback callback : callbacks) {
      callback.onMediaItemTransition(mediaItem, reason);
    }
  }

  public void notifyPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    if (Util.areEqual(this.playbackParameters, playbackParameters)) {
      return;
    }
    this.playbackParameters = playbackParameters;
    for (PlayerCallback callback : callbacks) {
      callback.onPlaybackParametersChanged(
          playbackParameters == null ? PlaybackParameters.DEFAULT : playbackParameters);
    }
  }

  public void notifyAudioAttributesChanged(@NonNull AudioAttributes attrs) {
    for (PlayerCallback callback : callbacks) {
      callback.onAudioAttributesChanged(attrs);
    }
  }

  @Override
  @NonNull
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
  @NonNull
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
    setMediaItems(Collections.singletonList(mediaItem));
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    setMediaItems(Collections.singletonList(mediaItem), /* startWindowIndex= */ 0, startPositionMs);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    setMediaItems(Collections.singletonList(mediaItem), resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
    setMediaItems(mediaItems, /* resetPosition= */ true);
  }

  @Override
  public void setMediaItems(@NonNull List<MediaItem> mediaItems, boolean resetPosition) {
    setMediaItemsCalled = true;
    this.mediaItems = mediaItems;
    this.resetPosition = resetPosition;
    countDownLatch.countDown();
  }

  @Override
  public void setMediaItems(
      List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs) {
    setMediaItemsCalled = true;
    this.mediaItems = mediaItems;
    this.startWindowIndex = startWindowIndex;
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

  @Override
  public boolean isCurrentWindowDynamic() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentWindowLive() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentWindowSeekable() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public MediaItem getCurrentMediaItem() {
    return currentMediaItem;
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

  @Override
  public int getCurrentWindowIndex() {
    return currentWindowIndex;
  }

  @Override
  public int getPreviousWindowIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getNextWindowIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMediaItem(MediaItem mediaItem) {
    addMediaItems(Collections.singletonList(mediaItem));
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    addMediaItems(index, Collections.singletonList(mediaItem));
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    addMediaItems(/* index= */ Integer.MAX_VALUE, mediaItems);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    addMediaItemsCalled = true;
    this.index = index;
    this.mediaItems = mediaItems;
    countDownLatch.countDown();
  }

  @Override
  public void removeMediaItem(int index) {
    removeMediaItems(/* fromIndex= */ index, /* toIndex= */ index + 1);
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
    removeMediaItems(/* fromIndex= */ 0, /* toIndex= */ Integer.MAX_VALUE);
  }

  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    if (currentIndex != newIndex) {
      moveMediaItems(/* fromIndex= */ currentIndex, /* toIndex= */ currentIndex + 1, newIndex);
    }
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    moveMediaItemsCalled = true;
    this.fromIndex = fromIndex;
    this.toIndex = toIndex;
    this.newIndex = newIndex;
    countDownLatch.countDown();
  }

  @Override
  public boolean hasPrevious() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasNext() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void previous() {
    previousCalled = true;
    countDownLatch.countDown();
  }

  @Override
  public void next() {
    nextCalled = true;
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

  public void notifyShuffleModeEnabledChanged() {
    boolean shuffleModeEnabled = this.shuffleModeEnabled;
    for (PlayerCallback callback : callbacks) {
      callback.onShuffleModeEnabledChanged(shuffleModeEnabled);
    }
  }

  public void notifyRepeatModeChanged() {
    int repeatMode = this.repeatMode;
    for (PlayerCallback callback : callbacks) {
      callback.onRepeatModeChanged(repeatMode);
    }
  }

  public void notifyTimelineChanged(@TimelineChangeReason int reason) {
    Timeline timeline = this.timeline;
    for (PlayerCallback callback : callbacks) {
      callback.onTimelineChanged(timeline, reason);
    }
  }

  public void notifyPlaylistMetadataChanged() {
    MediaMetadata metadata = playlistMetadata;
    for (PlayerCallback callback : callbacks) {
      callback.onPlaylistMetadataChanged(metadata);
    }
  }

  @Override
  @NonNull
  public VideoSize getVideoSize() {
    if (videoSize == null) {
      videoSize = VideoSize.UNKNOWN;
    }
    return videoSize;
  }

  public void notifyVideoSizeChanged(@NonNull VideoSize videoSize) {
    for (PlayerCallback callback : callbacks) {
      callback.onVideoSizeChanged(videoSize);
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

  public boolean surfaceExists() {
    return surface != null;
  }

  public void notifyDeviceVolumeChanged() {
    for (PlayerCallback callback : callbacks) {
      callback.onDeviceVolumeChanged(deviceVolume, deviceMuted);
    }
  }

  public void notifyDeviceInfoChanged() {
    for (PlayerCallback callback : callbacks) {
      callback.onDeviceInfoChanged(deviceInfo);
    }
  }

  @Override
  @NonNull
  public Looper getApplicationLooper() {
    return applicationLooper;
  }

  /** Builder for {@link MockPlayer}. */
  public static final class Builder {

    private int latchCount;
    private boolean changePlayerStateWithTransportControl;
    private Looper applicationLooper;

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

    public MockPlayer build() {
      return new MockPlayer(this);
    }
  }
}
