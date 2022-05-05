/*
 * Copyright 2020 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.session.MediaConstants.STATUS_CODE_SUCCESS_COMPAT;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.media.VolumeProviderCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import java.util.List;

/**
 * A wrapper of {@link Player} given by constructor of {@link MediaSession} or {@link
 * MediaSession#setPlayer(Player)}. Use this wrapper for extra checks before calling methods and/or
 * overriding the behavior.
 */
/* package */ class PlayerWrapper extends ForwardingPlayer {

  private int legacyStatusCode;
  @Nullable private String legacyErrorMessage;
  @Nullable private Bundle legacyErrorExtras;

  public PlayerWrapper(Player player) {
    super(player);
    legacyStatusCode = STATUS_CODE_SUCCESS_COMPAT;
  }

  /**
   * Sets the legacy error code.
   *
   * <p>This sets the legacy {@link PlaybackStateCompat} to {@link PlaybackStateCompat#STATE_ERROR}
   * and calls {@link PlaybackStateCompat.Builder#setErrorMessage(int, CharSequence)} and {@link
   * PlaybackStateCompat.Builder#setExtras(Bundle)} with the given arguments.
   *
   * <p>Use {@link #clearLegacyErrorStatus()} to clear the error state and to resume to the actual
   * playback state reflecting the player.
   *
   * @param errorCode The legacy error code.
   * @param errorMessage The legacy error message.
   * @param extras The extras.
   */
  public void setLegacyErrorStatus(int errorCode, String errorMessage, Bundle extras) {
    checkState(errorCode != STATUS_CODE_SUCCESS_COMPAT);
    legacyStatusCode = errorCode;
    legacyErrorMessage = errorMessage;
    legacyErrorExtras = extras;
  }

  /** Returns the legacy status code. */
  public int getLegacyStatusCode() {
    return legacyStatusCode;
  }

  /** Clears the legacy error status. */
  public void clearLegacyErrorStatus() {
    legacyStatusCode = STATUS_CODE_SUCCESS_COMPAT;
    legacyErrorMessage = null;
    legacyErrorExtras = null;
  }

  @Override
  public void addListener(Listener listener) {
    verifyApplicationThread();
    super.addListener(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    verifyApplicationThread();
    super.removeListener(listener);
  }

  @Override
  @Nullable
  public PlaybackException getPlayerError() {
    verifyApplicationThread();
    return super.getPlayerError();
  }

  @Override
  public void play() {
    verifyApplicationThread();
    super.play();
  }

  @Override
  public void pause() {
    verifyApplicationThread();
    super.pause();
  }

  @Override
  public void prepare() {
    verifyApplicationThread();
    super.prepare();
  }

  @Override
  public void stop() {
    verifyApplicationThread();
    super.stop();
  }

  @Override
  public void release() {
    verifyApplicationThread();
    super.release();
  }

  @Override
  public void seekToDefaultPosition(int mediaItemIndex) {
    verifyApplicationThread();
    super.seekToDefaultPosition(mediaItemIndex);
  }

  @Override
  public void seekTo(long positionMs) {
    verifyApplicationThread();
    super.seekTo(positionMs);
  }

  @Override
  public void seekTo(int mediaItemIndex, long positionMs) {
    verifyApplicationThread();
    super.seekTo(mediaItemIndex, positionMs);
  }

  @Override
  public long getSeekBackIncrement() {
    verifyApplicationThread();
    return super.getSeekBackIncrement();
  }

  @Override
  public void seekBack() {
    verifyApplicationThread();
    super.seekBack();
  }

  @Override
  public long getSeekForwardIncrement() {
    verifyApplicationThread();
    return super.getSeekForwardIncrement();
  }

  @Override
  public void seekForward() {
    verifyApplicationThread();
    super.seekForward();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    verifyApplicationThread();
    super.setPlaybackParameters(playbackParameters);
  }

  @Override
  public void setPlaybackSpeed(float playbackSpeed) {
    verifyApplicationThread();
    super.setPlaybackSpeed(playbackSpeed);
  }

  @Override
  public long getCurrentPosition() {
    verifyApplicationThread();
    return super.getCurrentPosition();
  }

  @Override
  public long getDuration() {
    verifyApplicationThread();
    return super.getDuration();
  }

  @Override
  public long getBufferedPosition() {
    verifyApplicationThread();
    return super.getBufferedPosition();
  }

  @Override
  public int getBufferedPercentage() {
    verifyApplicationThread();
    return super.getBufferedPercentage();
  }

  @Override
  public long getTotalBufferedDuration() {
    verifyApplicationThread();
    return super.getTotalBufferedDuration();
  }

  @Override
  public long getCurrentLiveOffset() {
    verifyApplicationThread();
    return super.getCurrentLiveOffset();
  }

  @Override
  public long getContentDuration() {
    verifyApplicationThread();
    return super.getContentDuration();
  }

  @Override
  public long getContentPosition() {
    verifyApplicationThread();
    return super.getContentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    verifyApplicationThread();
    return super.getContentBufferedPosition();
  }

  @Override
  public boolean isPlayingAd() {
    verifyApplicationThread();
    return super.isPlayingAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    verifyApplicationThread();
    return super.getCurrentAdGroupIndex();
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    verifyApplicationThread();
    return super.getCurrentAdIndexInAdGroup();
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    verifyApplicationThread();
    return super.getPlaybackParameters();
  }

  @Override
  public VideoSize getVideoSize() {
    verifyApplicationThread();
    return super.getVideoSize();
  }

  @Override
  public void clearVideoSurface() {
    verifyApplicationThread();
    super.clearVideoSurface();
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    super.clearVideoSurface(surface);
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    super.setVideoSurface(surface);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    super.setVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    super.clearVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    super.setVideoSurfaceView(surfaceView);
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    super.clearVideoSurfaceView(surfaceView);
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    super.setVideoTextureView(textureView);
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    super.clearVideoTextureView(textureView);
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    verifyApplicationThread();
    return super.getAudioAttributes();
  }

  @Override
  public void setMediaItem(MediaItem mediaItem) {
    verifyApplicationThread();
    super.setMediaItem(mediaItem);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    verifyApplicationThread();
    super.setMediaItem(mediaItem, startPositionMs);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    verifyApplicationThread();
    super.setMediaItem(mediaItem, resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
    verifyApplicationThread();
    super.setMediaItems(mediaItems);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    verifyApplicationThread();
    super.setMediaItems(mediaItems, resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    verifyApplicationThread();
    super.setMediaItems(mediaItems, startIndex, startPositionMs);
  }

  @Override
  public void addMediaItem(MediaItem mediaItem) {
    verifyApplicationThread();
    super.addMediaItem(mediaItem);
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    verifyApplicationThread();
    super.addMediaItem(index, mediaItem);
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    verifyApplicationThread();
    super.addMediaItems(mediaItems);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    verifyApplicationThread();
    super.addMediaItems(index, mediaItems);
  }

  @Override
  public void clearMediaItems() {
    verifyApplicationThread();
    super.clearMediaItems();
  }

  @Override
  public void removeMediaItem(int index) {
    verifyApplicationThread();
    super.removeMediaItem(index);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    verifyApplicationThread();
    super.removeMediaItems(fromIndex, toIndex);
  }

  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    verifyApplicationThread();
    super.moveMediaItem(currentIndex, newIndex);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    verifyApplicationThread();
    super.moveMediaItems(fromIndex, toIndex, newIndex);
  }

  @Deprecated
  @Override
  public boolean hasPrevious() {
    verifyApplicationThread();
    return super.hasPrevious();
  }

  @Deprecated
  @Override
  public boolean hasNext() {
    verifyApplicationThread();
    return super.hasNext();
  }

  @Deprecated
  @Override
  public boolean hasPreviousWindow() {
    verifyApplicationThread();
    return super.hasPreviousWindow();
  }

  @Deprecated
  @Override
  public boolean hasNextWindow() {
    verifyApplicationThread();
    return super.hasNextWindow();
  }

  @Override
  public boolean hasPreviousMediaItem() {
    verifyApplicationThread();
    return super.hasPreviousMediaItem();
  }

  @Override
  public boolean hasNextMediaItem() {
    verifyApplicationThread();
    return super.hasNextMediaItem();
  }

  @Deprecated
  @Override
  public void previous() {
    verifyApplicationThread();
    super.previous();
  }

  @Deprecated
  @Override
  public void next() {
    verifyApplicationThread();
    super.next();
  }

  @Deprecated
  @Override
  public void seekToPreviousWindow() {
    verifyApplicationThread();
    super.seekToPreviousWindow();
  }

  @Deprecated
  @Override
  public void seekToNextWindow() {
    verifyApplicationThread();
    super.seekToNextWindow();
  }

  @Override
  public void seekToPreviousMediaItem() {
    verifyApplicationThread();
    super.seekToPreviousMediaItem();
  }

  @Override
  public void seekToNextMediaItem() {
    verifyApplicationThread();
    super.seekToNextMediaItem();
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata playlistMetadata) {
    verifyApplicationThread();
    super.setPlaylistMetadata(playlistMetadata);
  }

  @Override
  public void setRepeatMode(int repeatMode) {
    verifyApplicationThread();
    super.setRepeatMode(repeatMode);
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    verifyApplicationThread();
    super.setShuffleModeEnabled(shuffleModeEnabled);
  }

  @Override
  public Timeline getCurrentTimeline() {
    verifyApplicationThread();
    return super.getCurrentTimeline();
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    verifyApplicationThread();
    return super.getPlaylistMetadata();
  }

  @Override
  public int getRepeatMode() {
    verifyApplicationThread();
    return super.getRepeatMode();
  }

  @Override
  public boolean getShuffleModeEnabled() {
    verifyApplicationThread();
    return super.getShuffleModeEnabled();
  }

  @Override
  @Nullable
  public MediaItem getCurrentMediaItem() {
    verifyApplicationThread();
    return super.getCurrentMediaItem();
  }

  @Override
  public int getMediaItemCount() {
    verifyApplicationThread();
    return super.getMediaItemCount();
  }

  @Override
  public MediaItem getMediaItemAt(int index) {
    verifyApplicationThread();
    return super.getMediaItemAt(index);
  }

  @Deprecated
  @Override
  public int getCurrentWindowIndex() {
    verifyApplicationThread();
    return super.getCurrentWindowIndex();
  }

  @Override
  public int getCurrentMediaItemIndex() {
    verifyApplicationThread();
    return super.getCurrentMediaItemIndex();
  }

  @Deprecated
  @Override
  public int getPreviousWindowIndex() {
    verifyApplicationThread();
    return super.getPreviousWindowIndex();
  }

  @Override
  public int getPreviousMediaItemIndex() {
    verifyApplicationThread();
    return super.getPreviousMediaItemIndex();
  }

  @Deprecated
  @Override
  public int getNextWindowIndex() {
    verifyApplicationThread();
    return super.getNextWindowIndex();
  }

  @Override
  public int getNextMediaItemIndex() {
    verifyApplicationThread();
    return super.getNextMediaItemIndex();
  }

  @Override
  public float getVolume() {
    verifyApplicationThread();
    return super.getVolume();
  }

  @Override
  public void setVolume(float volume) {
    verifyApplicationThread();
    super.setVolume(volume);
  }

  @Override
  public List<Cue> getCurrentCues() {
    verifyApplicationThread();
    return super.getCurrentCues();
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    verifyApplicationThread();
    return super.getDeviceInfo();
  }

  @Override
  public int getDeviceVolume() {
    verifyApplicationThread();
    return super.getDeviceVolume();
  }

  @Override
  public boolean isDeviceMuted() {
    verifyApplicationThread();
    return super.isDeviceMuted();
  }

  @Override
  public void setDeviceVolume(int volume) {
    verifyApplicationThread();
    super.setDeviceVolume(volume);
  }

  @Override
  public void increaseDeviceVolume() {
    verifyApplicationThread();
    super.increaseDeviceVolume();
  }

  @Override
  public void decreaseDeviceVolume() {
    verifyApplicationThread();
    super.decreaseDeviceVolume();
  }

  @Override
  public void setDeviceMuted(boolean muted) {
    verifyApplicationThread();
    super.setDeviceMuted(muted);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThread();
    super.setPlayWhenReady(playWhenReady);
  }

  @Override
  public boolean getPlayWhenReady() {
    verifyApplicationThread();
    return super.getPlayWhenReady();
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    verifyApplicationThread();
    return super.getPlaybackSuppressionReason();
  }

  @Override
  @State
  public int getPlaybackState() {
    verifyApplicationThread();
    return super.getPlaybackState();
  }

  @Override
  public boolean isPlaying() {
    verifyApplicationThread();
    return super.isPlaying();
  }

  @Override
  public boolean isLoading() {
    verifyApplicationThread();
    return super.isLoading();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    verifyApplicationThread();
    return super.getMediaMetadata();
  }

  @Override
  public boolean isCommandAvailable(@Command int command) {
    verifyApplicationThread();
    return super.isCommandAvailable(command);
  }

  @Override
  public Commands getAvailableCommands() {
    verifyApplicationThread();
    return super.getAvailableCommands();
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    verifyApplicationThread();
    return super.getTrackSelectionParameters();
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    verifyApplicationThread();
    super.setTrackSelectionParameters(parameters);
  }

  public PlaybackStateCompat createPlaybackStateCompat() {
    if (legacyStatusCode != STATUS_CODE_SUCCESS_COMPAT) {
      return new PlaybackStateCompat.Builder()
          .setState(
              PlaybackStateCompat.STATE_ERROR,
              /* position= */ PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
              /* playbackSpeed= */ 0,
              /* updateTime= */ SystemClock.elapsedRealtime())
          .setActions(0)
          .setBufferedPosition(0)
          .setErrorMessage(legacyStatusCode, checkNotNull(legacyErrorMessage))
          .setExtras(checkNotNull(legacyErrorExtras))
          .build();
    }
    @Nullable PlaybackException playerError = getPlayerError();
    int state =
        MediaUtils.convertToPlaybackStateCompatState(
            playerError, getPlaybackState(), getPlayWhenReady(), isPlaying());
    long allActions =
        PlaybackStateCompat.ACTION_STOP
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_REWIND
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_FAST_FORWARD
            | PlaybackStateCompat.ACTION_SET_RATING
            | PlaybackStateCompat.ACTION_SEEK_TO
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
            | PlaybackStateCompat.ACTION_PLAY_FROM_URI
            | PlaybackStateCompat.ACTION_PREPARE
            | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
            | PlaybackStateCompat.ACTION_PREPARE_FROM_URI
            | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
            | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
            | PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED;
    long queueItemId = MediaUtils.convertToQueueItemId(getCurrentMediaItemIndex());
    PlaybackStateCompat.Builder builder =
        new PlaybackStateCompat.Builder()
            .setState(
                state,
                getCurrentPosition(),
                getPlaybackParameters().speed,
                SystemClock.elapsedRealtime())
            .setActions(allActions)
            .setActiveQueueItemId(queueItemId)
            .setBufferedPosition(getBufferedPosition());
    if (playerError != null) {
      builder.setErrorMessage(
          PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, Util.castNonNull(playerError.getMessage()));
    }
    return builder.build();
  }

  @Nullable
  public VolumeProviderCompat createVolumeProviderCompat() {
    if (getDeviceInfo().playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) {
      return null;
    }
    Commands availableCommands = getAvailableCommands();
    int volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_FIXED;
    if (availableCommands.contains(COMMAND_ADJUST_DEVICE_VOLUME)) {
      volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_RELATIVE;
      if (availableCommands.contains(COMMAND_SET_DEVICE_VOLUME)) {
        volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
      }
    }
    Handler handler = new Handler(getApplicationLooper());
    return new VolumeProviderCompat(
        volumeControlType, getDeviceInfo().maxVolume, getDeviceVolume()) {
      @Override
      public void onSetVolumeTo(int volume) {
        postOrRun(handler, () -> setDeviceVolume(volume));
      }

      @Override
      public void onAdjustVolume(int direction) {
        postOrRun(
            handler,
            () -> {
              switch (direction) {
                case AudioManager.ADJUST_RAISE:
                  increaseDeviceVolume();
                  break;
                case AudioManager.ADJUST_LOWER:
                  decreaseDeviceVolume();
                  break;
                case AudioManager.ADJUST_MUTE:
                  setDeviceMuted(true);
                  break;
                case AudioManager.ADJUST_UNMUTE:
                  setDeviceMuted(false);
                  break;
                case AudioManager.ADJUST_TOGGLE_MUTE:
                  setDeviceMuted(!isDeviceMuted());
                  break;
                default:
                  Log.w(
                      "VolumeProviderCompat",
                      "onAdjustVolume: Ignoring unknown direction: " + direction);
                  break;
              }
            });
      }
    };
  }

  /**
   * Creates a {@link PositionInfo} of this player for Bundling.
   *
   * <p>This excludes window uid and period uid that wouldn't be preserved when bundling.
   */
  public PositionInfo createPositionInfoForBundling() {
    return new PositionInfo(
        /* windowUid= */ null,
        getCurrentMediaItemIndex(),
        getCurrentMediaItem(),
        /* periodUid= */ null,
        getCurrentPeriodIndex(),
        getCurrentPosition(),
        getContentPosition(),
        getCurrentAdGroupIndex(),
        getCurrentAdIndexInAdGroup());
  }

  /**
   * Creates a {@link SessionPositionInfo} of this player for Bundling.
   *
   * <p>This excludes window uid and period uid that wouldn't be preserved when bundling.
   */
  public SessionPositionInfo createSessionPositionInfoForBundling() {
    return new SessionPositionInfo(
        createPositionInfoForBundling(),
        isPlayingAd(),
        /* eventTimeMs= */ SystemClock.elapsedRealtime(),
        getDuration(),
        getBufferedPosition(),
        getBufferedPercentage(),
        getTotalBufferedDuration(),
        getCurrentLiveOffset(),
        getContentDuration(),
        getContentBufferedPosition());
  }

  public PlayerInfo createPlayerInfoForBundling() {
    return new PlayerInfo(
        getPlayerError(),
        PlayerInfo.MEDIA_ITEM_TRANSITION_REASON_DEFAULT,
        createSessionPositionInfoForBundling(),
        createPositionInfoForBundling(),
        createPositionInfoForBundling(),
        PlayerInfo.DISCONTINUITY_REASON_DEFAULT,
        getPlaybackParameters(),
        getRepeatMode(),
        getShuffleModeEnabled(),
        getVideoSize(),
        getCurrentTimeline(),
        getPlaylistMetadata(),
        getVolume(),
        getAudioAttributes(),
        getCurrentCues(),
        getDeviceInfo(),
        getDeviceVolume(),
        isDeviceMuted(),
        getPlayWhenReady(),
        PlayerInfo.PLAY_WHEN_READY_CHANGE_REASON_DEFAULT,
        getPlaybackSuppressionReason(),
        getPlaybackState(),
        isPlaying(),
        isLoading(),
        getMediaMetadata(),
        getSeekBackIncrement(),
        getSeekForwardIncrement(),
        getMaxSeekToPreviousPosition(),
        getTrackSelectionParameters());
  }

  private void verifyApplicationThread() {
    checkState(Looper.myLooper() == getApplicationLooper());
  }
}
