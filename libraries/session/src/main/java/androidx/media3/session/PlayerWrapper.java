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
import static androidx.media3.common.util.Util.msToUs;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_MEDIA_ID_COMPAT;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT;
import static androidx.media3.session.MediaUtils.intersect;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.media.VolumeProviderCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A wrapper of {@link Player} given by constructor of {@link MediaSession} or {@link
 * MediaSession#setPlayer(Player)}. Use this wrapper for extra checks before calling methods and/or
 * overriding the behavior.
 */
/* package */ class PlayerWrapper extends ForwardingPlayer {

  private static final int STATUS_CODE_SUCCESS_COMPAT = -1;

  private final boolean playIfSuppressed;

  private int legacyStatusCode;
  @Nullable private String legacyErrorMessage;
  @Nullable private Bundle legacyErrorExtras;
  private ImmutableList<CommandButton> customLayout;
  private SessionCommands availableSessionCommands;
  private Commands availablePlayerCommands;

  public PlayerWrapper(
      Player player,
      boolean playIfSuppressed,
      ImmutableList<CommandButton> customLayout,
      SessionCommands availableSessionCommands,
      Commands availablePlayerCommands) {
    super(player);
    this.playIfSuppressed = playIfSuppressed;
    this.customLayout = customLayout;
    this.availableSessionCommands = availableSessionCommands;
    this.availablePlayerCommands = availablePlayerCommands;
    legacyStatusCode = STATUS_CODE_SUCCESS_COMPAT;
  }

  public void setAvailableCommands(
      SessionCommands availableSessionCommands, Commands availablePlayerCommands) {
    this.availableSessionCommands = availableSessionCommands;
    this.availablePlayerCommands = availablePlayerCommands;
  }

  public SessionCommands getAvailableSessionCommands() {
    return availableSessionCommands;
  }

  public Commands getAvailablePlayerCommands() {
    return availablePlayerCommands;
  }

  public void setCustomLayout(ImmutableList<CommandButton> customLayout) {
    this.customLayout = customLayout;
  }

  /* package */ ImmutableList<CommandButton> getCustomLayout() {
    return customLayout;
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

  public void playIfCommandAvailable() {
    if (isCommandAvailable(COMMAND_PLAY_PAUSE)) {
      play();
    }
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

  public void prepareIfCommandAvailable() {
    if (isCommandAvailable(COMMAND_PREPARE)) {
      prepare();
    }
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
  public void seekToDefaultPosition() {
    verifyApplicationThread();
    super.seekToDefaultPosition();
  }

  public void seekToDefaultPositionIfCommandAvailable() {
    if (isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) {
      seekToDefaultPosition();
    }
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

  public long getDurationWithCommandCheck() {
    return isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM) ? getDuration() : C.TIME_UNSET;
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

  public AudioAttributes getAudioAttributesWithCommandCheck() {
    return isCommandAvailable(COMMAND_GET_AUDIO_ATTRIBUTES)
        ? getAudioAttributes()
        : AudioAttributes.DEFAULT;
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

  @Override
  public void replaceMediaItem(int index, MediaItem mediaItem) {
    verifyApplicationThread();
    super.replaceMediaItem(index, mediaItem);
  }

  @Override
  public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    verifyApplicationThread();
    super.replaceMediaItems(fromIndex, toIndex, mediaItems);
  }

  @SuppressWarnings("deprecation") // Forwarding deprecated call
  @Deprecated
  @Override
  public boolean hasPrevious() {
    verifyApplicationThread();
    return super.hasPrevious();
  }

  @SuppressWarnings("deprecation") // Forwarding deprecated call
  @Deprecated
  @Override
  public boolean hasNext() {
    verifyApplicationThread();
    return super.hasNext();
  }

  @SuppressWarnings("deprecation") // Forwarding deprecated call
  @Deprecated
  @Override
  public boolean hasPreviousWindow() {
    verifyApplicationThread();
    return super.hasPreviousWindow();
  }

  @SuppressWarnings("deprecation") // Forwarding deprecated call
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

  @SuppressWarnings("deprecation") // Forwarding deprecated call
  @Deprecated
  @Override
  public void previous() {
    verifyApplicationThread();
    super.previous();
  }

  @SuppressWarnings("deprecation") // Forwarding deprecated call
  @Deprecated
  @Override
  public void next() {
    verifyApplicationThread();
    super.next();
  }

  @SuppressWarnings("deprecation") // Forwarding deprecated call
  @Deprecated
  @Override
  public void seekToPreviousWindow() {
    verifyApplicationThread();
    super.seekToPreviousWindow();
  }

  @SuppressWarnings("deprecation") // Forwarding deprecated call
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

  public Timeline getCurrentTimelineWithCommandCheck() {
    if (isCommandAvailable(COMMAND_GET_TIMELINE)) {
      return getCurrentTimeline();
    } else if (isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      return new CurrentMediaItemOnlyTimeline(this);
    }
    return Timeline.EMPTY;
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    verifyApplicationThread();
    return super.getPlaylistMetadata();
  }

  public MediaMetadata getPlaylistMetadataWithCommandCheck() {
    return isCommandAvailable(Player.COMMAND_GET_METADATA)
        ? getPlaylistMetadata()
        : MediaMetadata.EMPTY;
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

  @Nullable
  public MediaItem getCurrentMediaItemWithCommandCheck() {
    return isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM) ? getCurrentMediaItem() : null;
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

  @SuppressWarnings("deprecation") // Forwarding deprecated call
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

  @SuppressWarnings("deprecation") // Forwarding deprecated call
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

  @SuppressWarnings("deprecation") // Forwarding deprecated call
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

  public float getVolumeWithCommandCheck() {
    return isCommandAvailable(COMMAND_GET_VOLUME) ? getVolume() : 0;
  }

  @Override
  public void setVolume(float volume) {
    verifyApplicationThread();
    super.setVolume(volume);
  }

  @Override
  public CueGroup getCurrentCues() {
    verifyApplicationThread();
    return super.getCurrentCues();
  }

  public CueGroup getCurrentCuesWithCommandCheck() {
    return isCommandAvailable(COMMAND_GET_TEXT) ? getCurrentCues() : CueGroup.EMPTY_TIME_ZERO;
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

  public int getDeviceVolumeWithCommandCheck() {
    return isCommandAvailable(COMMAND_GET_DEVICE_VOLUME) ? getDeviceVolume() : 0;
  }

  @Override
  public boolean isDeviceMuted() {
    verifyApplicationThread();
    return super.isDeviceMuted();
  }

  public boolean isDeviceMutedWithCommandCheck() {
    return isCommandAvailable(Player.COMMAND_GET_DEVICE_VOLUME) && isDeviceMuted();
  }

  /**
   * @deprecated Use {@link #setDeviceVolume(int, int)} instead.
   */
  @SuppressWarnings("deprecation") // Forwarding to deprecated method
  @Deprecated
  @Override
  public void setDeviceVolume(int volume) {
    verifyApplicationThread();
    super.setDeviceVolume(volume);
  }

  @Override
  public void setDeviceVolume(int volume, @C.VolumeFlags int flags) {
    verifyApplicationThread();
    super.setDeviceVolume(volume, flags);
  }

  /**
   * @deprecated Use {@link #increaseDeviceVolume(int)} instead.
   */
  @SuppressWarnings("deprecation") // Forwarding to deprecated method
  @Deprecated
  @Override
  public void increaseDeviceVolume() {
    verifyApplicationThread();
    super.increaseDeviceVolume();
  }

  @Override
  public void increaseDeviceVolume(@C.VolumeFlags int flags) {
    verifyApplicationThread();
    super.increaseDeviceVolume(flags);
  }

  /**
   * @deprecated Use {@link #decreaseDeviceVolume(int)} instead.
   */
  @SuppressWarnings("deprecation") // Forwarding to deprecated method
  @Deprecated
  @Override
  public void decreaseDeviceVolume() {
    verifyApplicationThread();
    super.decreaseDeviceVolume();
  }

  @Override
  public void decreaseDeviceVolume(@C.VolumeFlags int flags) {
    verifyApplicationThread();
    super.decreaseDeviceVolume(flags);
  }

  /**
   * @deprecated Use {@link #setDeviceMuted(boolean, int)} instead.
   */
  @SuppressWarnings("deprecation") // Forwarding to deprecated method
  @Deprecated
  @Override
  public void setDeviceMuted(boolean muted) {
    verifyApplicationThread();
    super.setDeviceMuted(muted);
  }

  @Override
  public void setDeviceMuted(boolean muted, @C.VolumeFlags int flags) {
    verifyApplicationThread();
    super.setDeviceMuted(muted, flags);
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

  public MediaMetadata getMediaMetadataWithCommandCheck() {
    return isCommandAvailable(COMMAND_GET_METADATA) ? getMediaMetadata() : MediaMetadata.EMPTY;
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

  @Override
  public void seekToPrevious() {
    verifyApplicationThread();
    super.seekToPrevious();
  }

  @Override
  public long getMaxSeekToPreviousPosition() {
    verifyApplicationThread();
    return super.getMaxSeekToPreviousPosition();
  }

  @Override
  public void seekToNext() {
    verifyApplicationThread();
    super.seekToNext();
  }

  @Override
  public Tracks getCurrentTracks() {
    verifyApplicationThread();
    return super.getCurrentTracks();
  }

  public Tracks getCurrentTracksWithCommandCheck() {
    return isCommandAvailable(COMMAND_GET_TRACKS) ? getCurrentTracks() : Tracks.EMPTY;
  }

  @Nullable
  @Override
  public Object getCurrentManifest() {
    verifyApplicationThread();
    return super.getCurrentManifest();
  }

  @Override
  public int getCurrentPeriodIndex() {
    verifyApplicationThread();
    return super.getCurrentPeriodIndex();
  }

  @Override
  public boolean isCurrentMediaItemDynamic() {
    verifyApplicationThread();
    return super.isCurrentMediaItemDynamic();
  }

  @Override
  public boolean isCurrentMediaItemLive() {
    verifyApplicationThread();
    return super.isCurrentMediaItemLive();
  }

  @Override
  public boolean isCurrentMediaItemSeekable() {
    verifyApplicationThread();
    return super.isCurrentMediaItemSeekable();
  }

  @Override
  public Size getSurfaceSize() {
    verifyApplicationThread();
    return super.getSurfaceSize();
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
        LegacyConversions.convertToPlaybackStateCompatState(/* player= */ this, playIfSuppressed);
    // Always advertise ACTION_SET_RATING.
    long actions = PlaybackStateCompat.ACTION_SET_RATING;
    Commands availableCommands = intersect(availablePlayerCommands, getAvailableCommands());
    for (int i = 0; i < availableCommands.size(); i++) {
      actions |= convertCommandToPlaybackStateActions(availableCommands.get(i));
    }
    long queueItemId =
        isCommandAvailable(COMMAND_GET_TIMELINE)
            ? LegacyConversions.convertToQueueItemId(getCurrentMediaItemIndex())
            : MediaSessionCompat.QueueItem.UNKNOWN_ID;
    float playbackSpeed = getPlaybackParameters().speed;
    float sessionPlaybackSpeed = isPlaying() ? playbackSpeed : 0f;
    Bundle extras = new Bundle();
    extras.putFloat(EXTRAS_KEY_PLAYBACK_SPEED_COMPAT, playbackSpeed);
    @Nullable MediaItem currentMediaItem = getCurrentMediaItemWithCommandCheck();
    if (currentMediaItem != null && !MediaItem.DEFAULT_MEDIA_ID.equals(currentMediaItem.mediaId)) {
      extras.putString(EXTRAS_KEY_MEDIA_ID_COMPAT, currentMediaItem.mediaId);
    }
    boolean canReadPositions = isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM);
    long compatPosition =
        canReadPositions ? getCurrentPosition() : PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
    long compatBufferedPosition = canReadPositions ? getBufferedPosition() : 0;
    PlaybackStateCompat.Builder builder =
        new PlaybackStateCompat.Builder()
            .setState(state, compatPosition, sessionPlaybackSpeed, SystemClock.elapsedRealtime())
            .setActions(actions)
            .setActiveQueueItemId(queueItemId)
            .setBufferedPosition(compatBufferedPosition)
            .setExtras(extras);

    for (int i = 0; i < customLayout.size(); i++) {
      CommandButton commandButton = customLayout.get(i);
      if (commandButton.sessionCommand != null) {
        SessionCommand sessionCommand = commandButton.sessionCommand;
        if (sessionCommand.commandCode == SessionCommand.COMMAND_CODE_CUSTOM
            && CommandButton.isEnabled(
                commandButton, availableSessionCommands, availablePlayerCommands)) {
          builder.addCustomAction(
              new PlaybackStateCompat.CustomAction.Builder(
                      sessionCommand.customAction,
                      commandButton.displayName,
                      commandButton.iconResId)
                  .setExtras(sessionCommand.customExtras)
                  .build());
        }
      }
    }
    if (playerError != null) {
      builder.setErrorMessage(
          PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, Util.castNonNull(playerError.getMessage()));
    }
    return builder.build();
  }

  @Nullable
  @SuppressWarnings("deprecation") // Backwards compatibility with old volume commands
  public VolumeProviderCompat createVolumeProviderCompat() {
    if (getDeviceInfo().playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) {
      return null;
    }
    Commands availableCommands = getAvailableCommands();
    int volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_FIXED;
    if (availableCommands.containsAny(
        COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
      volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_RELATIVE;
      if (availableCommands.containsAny(
          COMMAND_SET_DEVICE_VOLUME, COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)) {
        volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
      }
    }
    Handler handler = new Handler(getApplicationLooper());
    int currentVolume = getDeviceVolumeWithCommandCheck();
    int legacyVolumeFlag = C.VOLUME_FLAG_SHOW_UI;
    DeviceInfo deviceInfo = getDeviceInfo();
    return new VolumeProviderCompat(
        volumeControlType, deviceInfo.maxVolume, currentVolume, deviceInfo.routingControllerId) {
      @Override
      public void onSetVolumeTo(int volume) {
        postOrRun(
            handler,
            () -> {
              if (!isCommandAvailable(COMMAND_SET_DEVICE_VOLUME)
                  && !isCommandAvailable(COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)) {
                return;
              }
              if (isCommandAvailable(COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)) {
                setDeviceVolume(volume, legacyVolumeFlag);
              } else {
                setDeviceVolume(volume);
              }
            });
      }

      @Override
      public void onAdjustVolume(int direction) {
        postOrRun(
            handler,
            () -> {
              if (!isCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME)
                  && !isCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
                return;
              }
              switch (direction) {
                case AudioManager.ADJUST_RAISE:
                  if (isCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
                    increaseDeviceVolume(legacyVolumeFlag);
                  } else {
                    increaseDeviceVolume();
                  }
                  break;
                case AudioManager.ADJUST_LOWER:
                  if (isCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
                    decreaseDeviceVolume(legacyVolumeFlag);
                  } else {
                    decreaseDeviceVolume();
                  }
                  break;
                case AudioManager.ADJUST_MUTE:
                  if (isCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
                    setDeviceMuted(true, legacyVolumeFlag);
                  } else {
                    setDeviceMuted(true);
                  }
                  break;
                case AudioManager.ADJUST_UNMUTE:
                  if (isCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
                    setDeviceMuted(false, legacyVolumeFlag);
                  } else {
                    setDeviceMuted(false);
                  }
                  break;
                case AudioManager.ADJUST_TOGGLE_MUTE:
                  if (isCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
                    setDeviceMuted(!isDeviceMutedWithCommandCheck(), legacyVolumeFlag);
                  } else {
                    setDeviceMuted(!isDeviceMutedWithCommandCheck());
                  }
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
    boolean canAccessCurrentMediaItem = isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM);
    boolean canAccessTimeline = isCommandAvailable(COMMAND_GET_TIMELINE);
    return new PositionInfo(
        /* windowUid= */ null,
        canAccessTimeline ? getCurrentMediaItemIndex() : 0,
        canAccessCurrentMediaItem ? getCurrentMediaItem() : null,
        /* periodUid= */ null,
        canAccessTimeline ? getCurrentPeriodIndex() : 0,
        canAccessCurrentMediaItem ? getCurrentPosition() : 0,
        canAccessCurrentMediaItem ? getContentPosition() : 0,
        canAccessCurrentMediaItem ? getCurrentAdGroupIndex() : C.INDEX_UNSET,
        canAccessCurrentMediaItem ? getCurrentAdIndexInAdGroup() : C.INDEX_UNSET);
  }

  /**
   * Creates a {@link SessionPositionInfo} of this player for Bundling.
   *
   * <p>This excludes window uid and period uid that wouldn't be preserved when bundling.
   */
  public SessionPositionInfo createSessionPositionInfoForBundling() {
    boolean canAccessCurrentMediaItem = isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM);
    return new SessionPositionInfo(
        createPositionInfoForBundling(),
        canAccessCurrentMediaItem && isPlayingAd(),
        /* eventTimeMs= */ SystemClock.elapsedRealtime(),
        canAccessCurrentMediaItem ? getDuration() : C.TIME_UNSET,
        canAccessCurrentMediaItem ? getBufferedPosition() : 0,
        canAccessCurrentMediaItem ? getBufferedPercentage() : 0,
        canAccessCurrentMediaItem ? getTotalBufferedDuration() : 0,
        canAccessCurrentMediaItem ? getCurrentLiveOffset() : C.TIME_UNSET,
        canAccessCurrentMediaItem ? getContentDuration() : C.TIME_UNSET,
        canAccessCurrentMediaItem ? getContentBufferedPosition() : 0);
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
        getCurrentTimelineWithCommandCheck(),
        PlayerInfo.TIMELINE_CHANGE_REASON_DEFAULT,
        getPlaylistMetadataWithCommandCheck(),
        getVolumeWithCommandCheck(),
        getAudioAttributesWithCommandCheck(),
        getCurrentCuesWithCommandCheck(),
        getDeviceInfo(),
        getDeviceVolumeWithCommandCheck(),
        isDeviceMutedWithCommandCheck(),
        getPlayWhenReady(),
        PlayerInfo.PLAY_WHEN_READY_CHANGE_REASON_DEFAULT,
        getPlaybackSuppressionReason(),
        getPlaybackState(),
        isPlaying(),
        isLoading(),
        getMediaMetadataWithCommandCheck(),
        getSeekBackIncrement(),
        getSeekForwardIncrement(),
        getMaxSeekToPreviousPosition(),
        getCurrentTracksWithCommandCheck(),
        getTrackSelectionParameters());
  }

  private void verifyApplicationThread() {
    checkState(Looper.myLooper() == getApplicationLooper());
  }

  @SuppressWarnings("deprecation") // Uses deprecated PlaybackStateCompat actions.
  private static long convertCommandToPlaybackStateActions(@Command int command) {
    switch (command) {
      case Player.COMMAND_PLAY_PAUSE:
        return PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PLAY_PAUSE;
      case Player.COMMAND_PREPARE:
        return PlaybackStateCompat.ACTION_PREPARE;
      case Player.COMMAND_SEEK_BACK:
        return PlaybackStateCompat.ACTION_REWIND;
      case Player.COMMAND_SEEK_FORWARD:
        return PlaybackStateCompat.ACTION_FAST_FORWARD;
      case Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM:
        return PlaybackStateCompat.ACTION_SEEK_TO;
      case Player.COMMAND_SEEK_TO_MEDIA_ITEM:
        return PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
      case Player.COMMAND_SEEK_TO_NEXT:
      case Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM:
        return PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
      case Player.COMMAND_SEEK_TO_PREVIOUS:
      case Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM:
        return PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
      case Player.COMMAND_SET_MEDIA_ITEM:
        return PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            | PlaybackStateCompat.ACTION_PLAY_FROM_URI
            | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
            | PlaybackStateCompat.ACTION_PREPARE_FROM_URI;
      case Player.COMMAND_SET_REPEAT_MODE:
        return PlaybackStateCompat.ACTION_SET_REPEAT_MODE;
      case Player.COMMAND_SET_SPEED_AND_PITCH:
        return PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED;
      case Player.COMMAND_SET_SHUFFLE_MODE:
        return PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
            | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED;
      case Player.COMMAND_STOP:
        return PlaybackStateCompat.ACTION_STOP;
      case Player.COMMAND_ADJUST_DEVICE_VOLUME:
      case Player.COMMAND_CHANGE_MEDIA_ITEMS:
        // TODO(b/227346735): Handle this through
        // MediaSessionCompat.setFlags(FLAG_HANDLES_QUEUE_COMMANDS)
      case Player.COMMAND_GET_AUDIO_ATTRIBUTES:
      case Player.COMMAND_GET_CURRENT_MEDIA_ITEM:
      case Player.COMMAND_GET_DEVICE_VOLUME:
      case Player.COMMAND_GET_METADATA:
      case Player.COMMAND_GET_TEXT:
      case Player.COMMAND_GET_TIMELINE:
      case Player.COMMAND_GET_TRACKS:
      case Player.COMMAND_GET_VOLUME:
      case Player.COMMAND_INVALID:
      case Player.COMMAND_SEEK_TO_DEFAULT_POSITION:
      case Player.COMMAND_SET_DEVICE_VOLUME:
      case Player.COMMAND_SET_PLAYLIST_METADATA:
      case Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS:
      case Player.COMMAND_SET_VIDEO_SURFACE:
      case Player.COMMAND_SET_VOLUME:
      default:
        return 0;
    }
  }

  private static final class CurrentMediaItemOnlyTimeline extends Timeline {

    private static final Object UID = new Object();

    @Nullable private final MediaItem mediaItem;
    private final boolean isSeekable;
    private final boolean isDynamic;
    @Nullable private final MediaItem.LiveConfiguration liveConfiguration;
    private final long durationUs;

    public CurrentMediaItemOnlyTimeline(PlayerWrapper player) {
      mediaItem = player.getCurrentMediaItem();
      isSeekable = player.isCurrentMediaItemSeekable();
      isDynamic = player.isCurrentMediaItemDynamic();
      liveConfiguration =
          player.isCurrentMediaItemLive() ? MediaItem.LiveConfiguration.UNSET : null;
      durationUs = msToUs(player.getContentDuration());
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      window.set(
          UID,
          mediaItem,
          /* manifest= */ null,
          /* presentationStartTimeMs= */ C.TIME_UNSET,
          /* windowStartTimeMs= */ C.TIME_UNSET,
          /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
          isSeekable,
          isDynamic,
          liveConfiguration,
          /* defaultPositionUs= */ 0,
          durationUs,
          /* firstPeriodIndex= */ 0,
          /* lastPeriodIndex= */ 0,
          /* positionInFirstPeriodUs= */ 0);
      return window;
    }

    @Override
    public int getPeriodCount() {
      return 1;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      period.set(
          /* id= */ UID,
          /* uid= */ UID,
          /* windowIndex= */ 0,
          durationUs,
          /* positionInWindowUs= */ 0);
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return UID.equals(uid) ? 0 : C.INDEX_UNSET;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      return UID;
    }
  }
}
