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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.Rect;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.IntDef;
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
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/** A mock implementation of {@link Player} for testing. */
public class MockPlayer implements Player {

  /** Player methods. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    METHOD_ADD_MEDIA_ITEM,
    METHOD_ADD_MEDIA_ITEMS,
    METHOD_ADD_MEDIA_ITEM_WITH_INDEX,
    METHOD_ADD_MEDIA_ITEMS_WITH_INDEX,
    METHOD_CLEAR_MEDIA_ITEMS,
    METHOD_DECREASE_DEVICE_VOLUME,
    METHOD_DECREASE_DEVICE_VOLUME_WITH_FLAGS,
    METHOD_INCREASE_DEVICE_VOLUME,
    METHOD_INCREASE_DEVICE_VOLUME_WITH_FLAGS,
    METHOD_MOVE_MEDIA_ITEM,
    METHOD_MOVE_MEDIA_ITEMS,
    METHOD_PAUSE,
    METHOD_PLAY,
    METHOD_PREPARE,
    METHOD_RELEASE,
    METHOD_REMOVE_MEDIA_ITEM,
    METHOD_REMOVE_MEDIA_ITEMS,
    METHOD_SEEK_BACK,
    METHOD_SEEK_FORWARD,
    METHOD_SEEK_TO,
    METHOD_SEEK_TO_DEFAULT_POSITION,
    METHOD_SEEK_TO_DEFAULT_POSITION_WITH_MEDIA_ITEM_INDEX,
    METHOD_SEEK_TO_NEXT,
    METHOD_SEEK_TO_NEXT_MEDIA_ITEM,
    METHOD_SEEK_TO_PREVIOUS,
    METHOD_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    METHOD_SEEK_TO_WITH_MEDIA_ITEM_INDEX,
    METHOD_SET_DEVICE_MUTED,
    METHOD_SET_DEVICE_MUTED_WITH_FLAGS,
    METHOD_SET_DEVICE_VOLUME,
    METHOD_SET_DEVICE_VOLUME_WITH_FLAGS,
    METHOD_SET_MEDIA_ITEM,
    METHOD_SET_MEDIA_ITEM_WITH_RESET_POSITION,
    METHOD_SET_MEDIA_ITEM_WITH_START_POSITION,
    METHOD_SET_MEDIA_ITEMS,
    METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION,
    METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX,
    METHOD_SET_PLAY_WHEN_READY,
    METHOD_SET_PLAYBACK_PARAMETERS,
    METHOD_SET_PLAYBACK_SPEED,
    METHOD_SET_PLAYLIST_METADATA,
    METHOD_SET_REPEAT_MODE,
    METHOD_SET_SHUFFLE_MODE,
    METHOD_SET_TRACK_SELECTION_PARAMETERS,
    METHOD_SET_VOLUME,
    METHOD_STOP,
    METHOD_REPLACE_MEDIA_ITEM,
    METHOD_REPLACE_MEDIA_ITEMS
  })
  public @interface Method {}

  /** Maps to {@link Player#addMediaItem(MediaItem)}. */
  public static final int METHOD_ADD_MEDIA_ITEM = 0;

  /** Maps to {@link Player#addMediaItems(List)}. */
  public static final int METHOD_ADD_MEDIA_ITEMS = 1;

  /** Maps to {@link Player#addMediaItem(int, MediaItem)}. */
  public static final int METHOD_ADD_MEDIA_ITEM_WITH_INDEX = 2;

  /** Maps to {@link Player#addMediaItems(int, List)}. */
  public static final int METHOD_ADD_MEDIA_ITEMS_WITH_INDEX = 3;

  /** Maps to {@link Player#clearMediaItems()}. */
  public static final int METHOD_CLEAR_MEDIA_ITEMS = 4;

  /** Maps to {@link Player#decreaseDeviceVolume()}. */
  public static final int METHOD_DECREASE_DEVICE_VOLUME = 5;

  /** Maps to {@link Player#increaseDeviceVolume()}. */
  public static final int METHOD_INCREASE_DEVICE_VOLUME = 6;

  /** Maps to {@link Player#moveMediaItem(int, int)}. */
  public static final int METHOD_MOVE_MEDIA_ITEM = 7;

  /** Maps to {@link Player#moveMediaItems(int, int, int)}. */
  public static final int METHOD_MOVE_MEDIA_ITEMS = 8;

  /** Maps to {@link Player#pause()}. */
  public static final int METHOD_PAUSE = 9;

  /** Maps to {@link Player#play()}. */
  public static final int METHOD_PLAY = 10;

  /** Maps to {@link Player#prepare()}. */
  public static final int METHOD_PREPARE = 11;

  /** Maps to {@link Player#release()}. */
  public static final int METHOD_RELEASE = 12;

  /** Maps to {@link Player#removeMediaItem(int)}. */
  public static final int METHOD_REMOVE_MEDIA_ITEM = 13;

  /** Maps to {@link Player#removeMediaItems(int, int)}. */
  public static final int METHOD_REMOVE_MEDIA_ITEMS = 14;

  /** Maps to {@link Player#seekBack()}. */
  public static final int METHOD_SEEK_BACK = 15;

  /** Maps to {@link Player#seekForward()}. */
  public static final int METHOD_SEEK_FORWARD = 16;

  /** Maps to {@link Player#seekTo(long)}. */
  public static final int METHOD_SEEK_TO = 17;

  /** Maps to {@link Player#seekToDefaultPosition()}. */
  public static final int METHOD_SEEK_TO_DEFAULT_POSITION = 18;

  /** Maps to {@link Player#seekToDefaultPosition(int)}. */
  public static final int METHOD_SEEK_TO_DEFAULT_POSITION_WITH_MEDIA_ITEM_INDEX = 19;

  /** Maps to {@link Player#seekToNext()}. */
  public static final int METHOD_SEEK_TO_NEXT = 20;

  /** Maps to {@link Player#seekToNextMediaItem()}. */
  public static final int METHOD_SEEK_TO_NEXT_MEDIA_ITEM = 21;

  /** Maps to {@link Player#seekToPrevious()}. */
  public static final int METHOD_SEEK_TO_PREVIOUS = 22;

  /** Maps to {@link Player#seekToPreviousMediaItem()}. */
  public static final int METHOD_SEEK_TO_PREVIOUS_MEDIA_ITEM = 23;

  /** Maps to {@link Player#seekTo(int, long)}. */
  public static final int METHOD_SEEK_TO_WITH_MEDIA_ITEM_INDEX = 24;

  /** Maps to {@link Player#setDeviceMuted(boolean)}. */
  public static final int METHOD_SET_DEVICE_MUTED = 25;

  /** Maps to {@link Player#setDeviceVolume(int)}. */
  public static final int METHOD_SET_DEVICE_VOLUME = 26;

  /** Maps to {@link Player#setMediaItem(MediaItem)}. */
  public static final int METHOD_SET_MEDIA_ITEM = 27;

  /** Maps to {@link Player#setMediaItem(MediaItem, boolean)}. */
  public static final int METHOD_SET_MEDIA_ITEM_WITH_RESET_POSITION = 28;

  /** Maps to {@link Player#setMediaItem(MediaItem, long)}. */
  public static final int METHOD_SET_MEDIA_ITEM_WITH_START_POSITION = 29;

  /** Maps to {@link Player#setMediaItems(List)}. */
  public static final int METHOD_SET_MEDIA_ITEMS = 30;

  /** Maps to {@link Player#setMediaItems(List, boolean)}. */
  public static final int METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION = 31;

  /** Maps to {@link Player#setMediaItems(List, int, long)}. */
  public static final int METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX = 32;

  /** Maps to {@link Player#setPlayWhenReady(boolean)}. */
  public static final int METHOD_SET_PLAY_WHEN_READY = 33;

  /** Maps to {@link Player#setPlaybackParameters(PlaybackParameters)}. */
  public static final int METHOD_SET_PLAYBACK_PARAMETERS = 34;

  /** Maps to {@link Player#setPlaybackSpeed(float)}. */
  public static final int METHOD_SET_PLAYBACK_SPEED = 35;

  /** Maps to {@link Player#setPlaylistMetadata(MediaMetadata)}. */
  public static final int METHOD_SET_PLAYLIST_METADATA = 36;

  /** Maps to {@link Player#setRepeatMode(int)}. */
  public static final int METHOD_SET_REPEAT_MODE = 37;

  /** Maps to {@link Player#setShuffleModeEnabled(boolean)}. */
  public static final int METHOD_SET_SHUFFLE_MODE = 38;

  /** Maps to {@link Player#setTrackSelectionParameters(TrackSelectionParameters)}. */
  public static final int METHOD_SET_TRACK_SELECTION_PARAMETERS = 39;

  /** Maps to {@link Player#setVolume(float)}. */
  public static final int METHOD_SET_VOLUME = 40;

  /** Maps to {@link Player#stop()}. */
  public static final int METHOD_STOP = 41;

  /** Maps to {@link Player#decreaseDeviceVolume(int)}. */
  public static final int METHOD_DECREASE_DEVICE_VOLUME_WITH_FLAGS = 42;

  /** Maps to {@link Player#increaseDeviceVolume(int)}. */
  public static final int METHOD_INCREASE_DEVICE_VOLUME_WITH_FLAGS = 43;

  /** Maps to {@link Player#setDeviceMuted(boolean, int)}. */
  public static final int METHOD_SET_DEVICE_MUTED_WITH_FLAGS = 44;

  /** Maps to {@link Player#setDeviceVolume(int, int)}. */
  public static final int METHOD_SET_DEVICE_VOLUME_WITH_FLAGS = 45;

  /** Maps to {@link Player#replaceMediaItem(int, MediaItem)}. */
  public static final int METHOD_REPLACE_MEDIA_ITEM = 46;

  /** Maps to {@link Player#replaceMediaItems(int, int, List)} . */
  public static final int METHOD_REPLACE_MEDIA_ITEMS = 47;

  /** Maps to {@link Player#setAudioAttributes(AudioAttributes, boolean)}. */
  public static final int METHOD_SET_AUDIO_ATTRIBUTES = 48;

  private final boolean changePlayerStateWithTransportControl;
  private final Looper applicationLooper;
  private final ArraySet<Listener> listeners = new ArraySet<>();
  private final ImmutableMap<@Method Integer, ConditionVariable> conditionVariables =
      createMethodConditionVariables();

  @Nullable PlaybackException playerError;
  public AudioAttributes audioAttributes;
  public long seekPositionMs;
  public int seekMediaItemIndex;
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
  public ArrayList<MediaItem> mediaItems;
  public boolean resetPosition;
  public int startMediaItemIndex;
  public long startPositionMs;
  public MediaMetadata playlistMetadata;
  public int index;
  public int fromIndex;
  public int toIndex;
  public int newIndex;
  public int currentPeriodIndex;
  public int currentMediaItemIndex;
  public @RepeatMode int repeatMode;
  public boolean shuffleModeEnabled;
  public VideoSize videoSize;
  public Size surfaceSize;
  @Nullable public Surface surface;
  @Nullable public SurfaceHolder surfaceHolder;
  @Nullable public SurfaceView surfaceView;
  @Nullable public TextureView textureView;
  public float volume;
  public CueGroup cueGroup;
  public DeviceInfo deviceInfo;
  public int deviceVolume;
  public boolean deviceMuted;
  public boolean playWhenReady;
  public @PlaybackSuppressionReason int playbackSuppressionReason;
  public @State int playbackState;
  public boolean isLoading;
  public MediaMetadata mediaMetadata;
  public Commands commands;
  public long seekBackIncrementMs;
  public long seekForwardIncrementMs;
  public long maxSeekToPreviousPositionMs;
  public TrackSelectionParameters trackSelectionParameters;
  public Tracks currentTracks;

  private MockPlayer(Builder builder) {
    changePlayerStateWithTransportControl = builder.changePlayerStateWithTransportControl;
    applicationLooper = builder.applicationLooper;

    playbackParameters = PlaybackParameters.DEFAULT;

    if (builder.itemCount > 0) {
      mediaItems = MediaTestUtils.createMediaItems(builder.itemCount);
      timeline = new PlaylistTimeline(mediaItems);
    } else {
      mediaItems = new ArrayList<>();
      timeline = Timeline.EMPTY;
    }

    // Sets default audio attributes to prevent setVolume() from being called with the play().
    audioAttributes = AudioAttributes.DEFAULT;

    playlistMetadata = MediaMetadata.EMPTY;
    index = C.INDEX_UNSET;
    fromIndex = C.INDEX_UNSET;
    toIndex = C.INDEX_UNSET;
    currentPeriodIndex = 0;
    currentMediaItemIndex = 0;
    repeatMode = Player.REPEAT_MODE_OFF;
    videoSize = VideoSize.UNKNOWN;
    surfaceSize = Size.UNKNOWN;
    volume = 1.0f;
    cueGroup = CueGroup.EMPTY_TIME_ZERO;
    deviceInfo = DeviceInfo.UNKNOWN;
    seekPositionMs = C.TIME_UNSET;
    seekMediaItemIndex = C.INDEX_UNSET;
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

    currentTracks = Tracks.EMPTY;
    trackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
  }

  @Override
  public void release() {
    checkNotNull(conditionVariables.get(METHOD_RELEASE)).open();
  }

  @Override
  public void stop() {
    checkNotNull(conditionVariables.get(METHOD_STOP)).open();
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
    checkNotNull(conditionVariables.get(METHOD_PLAY)).open();
    if (changePlayerStateWithTransportControl) {
      notifyPlayWhenReadyChanged(
          /* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    }
  }

  @Override
  public void pause() {
    checkNotNull(conditionVariables.get(METHOD_PAUSE)).open();
    if (changePlayerStateWithTransportControl) {
      notifyPlayWhenReadyChanged(
          /* playWhenReady= */ false, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    }
  }

  @Override
  public void prepare() {
    checkNotNull(conditionVariables.get(METHOD_PREPARE)).open();
    if (changePlayerStateWithTransportControl) {
      notifyPlaybackStateChanged(Player.STATE_READY);
    }
  }

  @Override
  public void seekToDefaultPosition() {
    checkNotNull(conditionVariables.get(METHOD_SEEK_TO_DEFAULT_POSITION)).open();
  }

  @Override
  public void seekToDefaultPosition(int mediaItemIndex) {
    checkArgument(mediaItemIndex >= 0);
    seekMediaItemIndex = mediaItemIndex;
    checkNotNull(conditionVariables.get(METHOD_SEEK_TO_DEFAULT_POSITION_WITH_MEDIA_ITEM_INDEX))
        .open();
  }

  @Override
  public void seekTo(long positionMs) {
    seekPositionMs = positionMs;
    checkNotNull(conditionVariables.get(METHOD_SEEK_TO)).open();
  }

  @Override
  public void seekTo(int mediaItemIndex, long positionMs) {
    checkArgument(mediaItemIndex >= 0);
    seekMediaItemIndex = mediaItemIndex;
    seekPositionMs = positionMs;
    checkNotNull(conditionVariables.get(METHOD_SEEK_TO_WITH_MEDIA_ITEM_INDEX)).open();
  }

  @Override
  public long getSeekBackIncrement() {
    return seekBackIncrementMs;
  }

  @Override
  public void seekBack() {
    checkNotNull(conditionVariables.get(METHOD_SEEK_BACK)).open();
  }

  @Override
  public long getSeekForwardIncrement() {
    return seekForwardIncrementMs;
  }

  @Override
  public void seekForward() {
    checkNotNull(conditionVariables.get(METHOD_SEEK_FORWARD)).open();
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

  /**
   * Changes the values returned from {@link #getPlayWhenReady()} and {@link
   * #getPlaybackSuppressionReason()}, and triggers {@link Player.Listener#onPlayWhenReadyChanged},
   * {@link Player.Listener#onPlaybackSuppressionReasonChanged} or {@link
   * Player.Listener#onIsPlayingChanged} as appropriate.
   */
  public void notifyPlayWhenReadyChanged(
      boolean playWhenReady, @PlaybackSuppressionReason int playbackSuppressionReason) {
    boolean playWhenReadyChanged = (this.playWhenReady != playWhenReady);
    boolean playbackSuppressionReasonChanged =
        (this.playbackSuppressionReason != playbackSuppressionReason);
    if (!playWhenReadyChanged && !playbackSuppressionReasonChanged) {
      return;
    }

    boolean wasPlaying = isPlaying();
    this.playWhenReady = playWhenReady;
    this.playbackSuppressionReason = playbackSuppressionReason;
    boolean isPlaying = isPlaying();
    for (Listener listener : listeners) {
      if (playWhenReadyChanged) {
        listener.onPlayWhenReadyChanged(
            playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
      }
      if (playbackSuppressionReasonChanged) {
        listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason);
      }
      if (isPlaying != wasPlaying) {
        listener.onIsPlayingChanged(isPlaying);
      }
    }
  }

  /**
   * Changes the value returned from {@link #getPlaybackState()} and triggers {@link
   * Player.Listener#onPlaybackStateChanged} and/or {@link Player.Listener#onIsPlayingChanged} as
   * appropriate.
   */
  public void notifyPlaybackStateChanged(@State int playbackState) {
    if (this.playbackState == playbackState) {
      return;
    }
    boolean wasPlaying = isPlaying();
    this.playbackState = playbackState;
    boolean isPlaying = isPlaying();
    for (Listener listener : listeners) {
      listener.onPlaybackStateChanged(playbackState);
      if (isPlaying != wasPlaying) {
        listener.onIsPlayingChanged(isPlaying);
      }
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
    this.playbackParameters = playbackParameters;
    checkNotNull(conditionVariables.get(METHOD_SET_PLAYBACK_PARAMETERS)).open();
  }

  @Override
  public void setPlaybackSpeed(float speed) {
    playbackParameters = new PlaybackParameters(speed);
    checkNotNull(conditionVariables.get(METHOD_SET_PLAYBACK_SPEED)).open();
  }

  @Override
  public float getVolume() {
    return volume;
  }

  @Override
  public void setVolume(float volume) {
    this.volume = volume;
    checkNotNull(conditionVariables.get(METHOD_SET_VOLUME)).open();
  }

  @Override
  public CueGroup getCurrentCues() {
    return cueGroup;
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

  @Deprecated
  @Override
  public void setDeviceVolume(int volume) {
    deviceVolume = volume;
    checkNotNull(conditionVariables.get(METHOD_SET_DEVICE_VOLUME)).open();
  }

  @Override
  public void setDeviceVolume(int volume, @C.VolumeFlags int flags) {
    deviceVolume = volume;
    checkNotNull(conditionVariables.get(METHOD_SET_DEVICE_VOLUME_WITH_FLAGS)).open();
  }

  @Deprecated
  @Override
  public void increaseDeviceVolume() {
    deviceVolume += 1;
    checkNotNull(conditionVariables.get(METHOD_INCREASE_DEVICE_VOLUME)).open();
  }

  @Override
  public void increaseDeviceVolume(@C.VolumeFlags int flags) {
    deviceVolume += 1;
    checkNotNull(conditionVariables.get(METHOD_INCREASE_DEVICE_VOLUME_WITH_FLAGS)).open();
  }

  @Deprecated
  @Override
  public void decreaseDeviceVolume() {
    deviceVolume -= 1;
    checkNotNull(conditionVariables.get(METHOD_DECREASE_DEVICE_VOLUME)).open();
  }

  @Override
  public void decreaseDeviceVolume(@C.VolumeFlags int flags) {
    deviceVolume -= 1;
    checkNotNull(conditionVariables.get(METHOD_DECREASE_DEVICE_VOLUME_WITH_FLAGS)).open();
  }

  @Deprecated
  @Override
  public void setDeviceMuted(boolean muted) {
    deviceMuted = muted;
    checkNotNull(conditionVariables.get(METHOD_SET_DEVICE_MUTED)).open();
  }

  @Override
  public void setDeviceMuted(boolean muted, @C.VolumeFlags int flags) {
    deviceMuted = muted;
    checkNotNull(conditionVariables.get(METHOD_SET_DEVICE_MUTED_WITH_FLAGS)).open();
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    this.audioAttributes = audioAttributes;
    checkNotNull(conditionVariables.get(METHOD_SET_AUDIO_ATTRIBUTES)).open();
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    this.playWhenReady = playWhenReady;
    checkNotNull(conditionVariables.get(METHOD_SET_PLAY_WHEN_READY)).open();
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady;
  }

  @Override
  public @PlaybackSuppressionReason int getPlaybackSuppressionReason() {
    return playbackSuppressionReason;
  }

  @Override
  public @State int getPlaybackState() {
    return playbackState;
  }

  @Override
  public boolean isPlaying() {
    return playWhenReady
        && playbackState == Player.STATE_READY
        && playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE;
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
    this.mediaItems = new ArrayList<>(ImmutableList.of(mediaItem));
    checkNotNull(conditionVariables.get(METHOD_SET_MEDIA_ITEM)).open();
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    this.mediaItems = new ArrayList<>(ImmutableList.of(mediaItem));
    this.startPositionMs = startPositionMs;
    checkNotNull(conditionVariables.get(METHOD_SET_MEDIA_ITEM_WITH_START_POSITION)).open();
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    this.mediaItems = new ArrayList<>(ImmutableList.of(mediaItem));
    this.resetPosition = resetPosition;
    checkNotNull(conditionVariables.get(METHOD_SET_MEDIA_ITEM_WITH_RESET_POSITION)).open();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
    this.mediaItems = new ArrayList<>(mediaItems);
    checkNotNull(conditionVariables.get(METHOD_SET_MEDIA_ITEMS)).open();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    this.mediaItems = new ArrayList<>(mediaItems);
    this.resetPosition = resetPosition;
    checkNotNull(conditionVariables.get(METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION)).open();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    this.mediaItems = new ArrayList<>(mediaItems);
    this.startMediaItemIndex = startIndex;
    this.startPositionMs = startPositionMs;
    checkNotNull(conditionVariables.get(METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX)).open();
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    return playlistMetadata;
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata playlistMetadata) {
    this.playlistMetadata = playlistMetadata;
    checkNotNull(conditionVariables.get(METHOD_SET_PLAYLIST_METADATA)).open();
  }

  /**
   * @deprecated Use {@link #isCurrentMediaItemDynamic()} instead.
   */
  @Deprecated
  @Override
  public boolean isCurrentWindowDynamic() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentMediaItemDynamic() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty()
        && timeline.getWindow(getCurrentMediaItemIndex(), new Timeline.Window()).isDynamic;
  }

  /**
   * @deprecated Use {@link #isCurrentMediaItemLive()} instead.
   */
  @Deprecated
  @Override
  public boolean isCurrentWindowLive() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentMediaItemLive() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty()
        && timeline.getWindow(getCurrentMediaItemIndex(), new Timeline.Window()).isLive();
  }

  /**
   * @deprecated Use {@link #isCurrentMediaItemSeekable()} instead.
   */
  @Deprecated
  @Override
  public boolean isCurrentWindowSeekable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCurrentMediaItemSeekable() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty()
        && timeline.getWindow(getCurrentMediaItemIndex(), new Timeline.Window()).isSeekable;
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
    return timeline.getWindowCount();
  }

  @Override
  public MediaItem getMediaItemAt(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getCurrentPeriodIndex() {
    return currentPeriodIndex;
  }

  /**
   * @deprecated Use {@link #getCurrentMediaItemIndex()} instead.
   */
  @Deprecated
  @Override
  public int getCurrentWindowIndex() {
    return getCurrentMediaItemIndex();
  }

  @Override
  public int getCurrentMediaItemIndex() {
    return currentMediaItemIndex;
  }

  /**
   * @deprecated Use {@link #getPreviousMediaItemIndex()} instead.
   */
  @Deprecated
  @Override
  public int getPreviousWindowIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getPreviousMediaItemIndex() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #getNextMediaItemIndex()} instead.
   */
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
    this.mediaItems.add(mediaItem);
    checkNotNull(conditionVariables.get(METHOD_ADD_MEDIA_ITEM)).open();
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    this.index = index;
    this.mediaItems.add(index, mediaItem);
    checkNotNull(conditionVariables.get(METHOD_ADD_MEDIA_ITEM_WITH_INDEX)).open();
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    this.mediaItems.addAll(mediaItems);
    checkNotNull(conditionVariables.get(METHOD_ADD_MEDIA_ITEMS)).open();
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    this.index = index;
    this.mediaItems.addAll(index, mediaItems);
    checkNotNull(conditionVariables.get(METHOD_ADD_MEDIA_ITEMS_WITH_INDEX)).open();
  }

  @Override
  public void removeMediaItem(int index) {
    this.index = index;
    this.mediaItems.remove(index);
    checkNotNull(conditionVariables.get(METHOD_REMOVE_MEDIA_ITEM)).open();
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    this.fromIndex = fromIndex;
    this.toIndex = toIndex;
    Util.removeRange(mediaItems, fromIndex, toIndex);
    checkNotNull(conditionVariables.get(METHOD_REMOVE_MEDIA_ITEMS)).open();
  }

  @Override
  public void clearMediaItems() {
    this.mediaItems.clear();
    checkNotNull(conditionVariables.get(METHOD_CLEAR_MEDIA_ITEMS)).open();
  }

  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    this.index = currentIndex;
    this.newIndex = newIndex;
    Util.moveItems(mediaItems, currentIndex, /* toIndex= */ currentIndex + 1, newIndex);
    checkNotNull(conditionVariables.get(METHOD_MOVE_MEDIA_ITEM)).open();
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    this.fromIndex = fromIndex;
    this.toIndex = toIndex;
    this.newIndex = newIndex;
    Util.moveItems(mediaItems, fromIndex, toIndex, newIndex);
    checkNotNull(conditionVariables.get(METHOD_MOVE_MEDIA_ITEMS)).open();
  }

  @Override
  public void replaceMediaItem(int index, MediaItem mediaItem) {
    this.index = index;
    this.mediaItems.set(index, mediaItem);
    checkNotNull(conditionVariables.get(METHOD_REPLACE_MEDIA_ITEM)).open();
  }

  @Override
  public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    this.fromIndex = fromIndex;
    this.toIndex = toIndex;
    this.mediaItems.addAll(toIndex, mediaItems);
    Util.removeRange(this.mediaItems, fromIndex, toIndex);
    checkNotNull(conditionVariables.get(METHOD_REPLACE_MEDIA_ITEMS)).open();
  }

  /**
   * @deprecated Use {@link #hasPreviousMediaItem()} instead.
   */
  @Deprecated
  @Override
  public boolean hasPrevious() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #hasNextMediaItem()} instead.
   */
  @Deprecated
  @Override
  public boolean hasNext() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #hasPreviousMediaItem()} instead.
   */
  @Deprecated
  @Override
  public boolean hasPreviousWindow() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #hasNextMediaItem()} instead.
   */
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

  /**
   * @deprecated Use {@link #seekToPreviousMediaItem()} instead.
   */
  @Deprecated
  @Override
  public void previous() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #seekToNextMediaItem()} instead.
   */
  @Deprecated
  @Override
  public void next() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #seekToPreviousMediaItem()} instead.
   */
  @Deprecated
  @Override
  public void seekToPreviousWindow() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #seekToNextMediaItem()} instead.
   */
  @Deprecated
  @Override
  public void seekToNextWindow() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void seekToPreviousMediaItem() {
    checkNotNull(conditionVariables.get(METHOD_SEEK_TO_PREVIOUS_MEDIA_ITEM)).open();
  }

  @Override
  public void seekToNextMediaItem() {
    checkNotNull(conditionVariables.get(METHOD_SEEK_TO_NEXT_MEDIA_ITEM)).open();
  }

  @Override
  public void seekToPrevious() {
    checkNotNull(conditionVariables.get(METHOD_SEEK_TO_PREVIOUS)).open();
  }

  @Override
  public long getMaxSeekToPreviousPosition() {
    return maxSeekToPreviousPositionMs;
  }

  @Override
  public void seekToNext() {
    checkNotNull(conditionVariables.get(METHOD_SEEK_TO_NEXT)).open();
  }

  @Override
  public int getRepeatMode() {
    return repeatMode;
  }

  @Override
  public void setRepeatMode(int repeatMode) {
    this.repeatMode = repeatMode;
    checkNotNull(conditionVariables.get(METHOD_SET_REPEAT_MODE)).open();
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return shuffleModeEnabled;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    this.shuffleModeEnabled = shuffleModeEnabled;
    checkNotNull(conditionVariables.get(METHOD_SET_SHUFFLE_MODE)).open();
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
    return videoSize;
  }

  @Override
  public Size getSurfaceSize() {
    return surfaceSize;
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
      maybeUpdateSurfaceSize(/* width= */ 0, /* height= */ 0);
    }
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    this.surface = surface;
    int newSurfaceSize = surface == null ? 0 : C.LENGTH_UNSET;
    maybeUpdateSurfaceSize(/* width= */ newSurfaceSize, /* height= */ newSurfaceSize);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    this.surfaceHolder = surfaceHolder;
    if (surfaceHolder == null || surfaceHolder.getSurfaceFrame() == null) {
      maybeUpdateSurfaceSize(/* width= */ 0, /* height= */ 0);
    } else {
      Rect rect = surfaceHolder.getSurfaceFrame();
      maybeUpdateSurfaceSize(rect.width(), rect.height());
    }
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
      this.surfaceHolder = null;
      maybeUpdateSurfaceSize(/* width= */ 0, /* height= */ 0);
    }
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    this.surfaceView = surfaceView;
    if (surfaceView == null
        || surfaceView.getHolder() == null
        || surfaceView.getHolder().getSurfaceFrame() == null) {
      maybeUpdateSurfaceSize(/* width= */ 0, /* height= */ 0);
    } else {
      Rect rect = surfaceView.getHolder().getSurfaceFrame();
      maybeUpdateSurfaceSize(rect.width(), rect.height());
    }
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    if (surfaceView != null && surfaceView == this.surfaceView) {
      this.surfaceView = null;
      maybeUpdateSurfaceSize(/* width= */ 0, /* height= */ 0);
    }
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    this.textureView = textureView;
    if (textureView != null) {
      maybeUpdateSurfaceSize(textureView.getWidth(), textureView.getHeight());
    }
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    if (textureView != null && textureView == this.textureView) {
      this.textureView = null;
      maybeUpdateSurfaceSize(/* width= */ 0, /* height= */ 0);
    }
  }

  @Override
  public Tracks getCurrentTracks() {
    return currentTracks;
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    return trackSelectionParameters;
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    trackSelectionParameters = parameters;
    checkNotNull(conditionVariables.get(METHOD_SET_TRACK_SELECTION_PARAMETERS)).open();
  }

  public boolean surfaceExists() {
    return surface != null;
  }

  public void notifyDeviceVolumeChanged() {
    for (Listener listener : listeners) {
      listener.onDeviceVolumeChanged(deviceVolume, deviceMuted);
    }
  }

  public void notifyVolumeChanged() {
    for (Listener listener : listeners) {
      listener.onVolumeChanged(volume);
    }
  }

  @SuppressWarnings("deprecation") // Implementing and calling deprecated listener method.
  public void notifyCuesChanged() {
    for (Listener listener : listeners) {
      listener.onCues(cueGroup.cues);
      listener.onCues(cueGroup);
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

  public void notifyTracksChanged() {
    for (Listener listener : listeners) {
      listener.onTracksChanged(currentTracks);
    }
  }

  @Override
  public Looper getApplicationLooper() {
    return applicationLooper;
  }

  /** Returns whether {@code method} has been called at least once. */
  public boolean hasMethodBeenCalled(@Method int method) {
    return checkNotNull(conditionVariables.get(method)).isOpen();
  }

  /**
   * Awaits up to {@code timeOutMs} until {@code method} is called, otherwise throws a {@link
   * TimeoutException}.
   */
  public void awaitMethodCalled(@Method int method, long timeOutMs)
      throws TimeoutException, InterruptedException {
    if (!checkNotNull(conditionVariables.get(method)).block(timeOutMs)) {
      throw new TimeoutException(
          Util.formatInvariant("Method %d not called after %d ms", method, timeOutMs));
    }
  }

  private void maybeUpdateSurfaceSize(int width, int height) {
    if (width != surfaceSize.getWidth() || height != surfaceSize.getHeight()) {
      surfaceSize = new Size(width, height);
    }
  }

  private static ImmutableMap<@Method Integer, ConditionVariable> createMethodConditionVariables() {
    return new ImmutableMap.Builder<@Method Integer, ConditionVariable>()
        .put(METHOD_ADD_MEDIA_ITEM, new ConditionVariable())
        .put(METHOD_ADD_MEDIA_ITEMS, new ConditionVariable())
        .put(METHOD_ADD_MEDIA_ITEM_WITH_INDEX, new ConditionVariable())
        .put(METHOD_ADD_MEDIA_ITEMS_WITH_INDEX, new ConditionVariable())
        .put(METHOD_CLEAR_MEDIA_ITEMS, new ConditionVariable())
        .put(METHOD_DECREASE_DEVICE_VOLUME, new ConditionVariable())
        .put(METHOD_DECREASE_DEVICE_VOLUME_WITH_FLAGS, new ConditionVariable())
        .put(METHOD_INCREASE_DEVICE_VOLUME, new ConditionVariable())
        .put(METHOD_INCREASE_DEVICE_VOLUME_WITH_FLAGS, new ConditionVariable())
        .put(METHOD_MOVE_MEDIA_ITEM, new ConditionVariable())
        .put(METHOD_MOVE_MEDIA_ITEMS, new ConditionVariable())
        .put(METHOD_PAUSE, new ConditionVariable())
        .put(METHOD_PLAY, new ConditionVariable())
        .put(METHOD_PREPARE, new ConditionVariable())
        .put(METHOD_RELEASE, new ConditionVariable())
        .put(METHOD_REMOVE_MEDIA_ITEM, new ConditionVariable())
        .put(METHOD_REMOVE_MEDIA_ITEMS, new ConditionVariable())
        .put(METHOD_SEEK_BACK, new ConditionVariable())
        .put(METHOD_SEEK_FORWARD, new ConditionVariable())
        .put(METHOD_SEEK_TO, new ConditionVariable())
        .put(METHOD_SEEK_TO_DEFAULT_POSITION, new ConditionVariable())
        .put(METHOD_SEEK_TO_DEFAULT_POSITION_WITH_MEDIA_ITEM_INDEX, new ConditionVariable())
        .put(METHOD_SEEK_TO_NEXT, new ConditionVariable())
        .put(METHOD_SEEK_TO_NEXT_MEDIA_ITEM, new ConditionVariable())
        .put(METHOD_SEEK_TO_PREVIOUS, new ConditionVariable())
        .put(METHOD_SEEK_TO_PREVIOUS_MEDIA_ITEM, new ConditionVariable())
        .put(METHOD_SEEK_TO_WITH_MEDIA_ITEM_INDEX, new ConditionVariable())
        .put(METHOD_SET_DEVICE_MUTED, new ConditionVariable())
        .put(METHOD_SET_DEVICE_MUTED_WITH_FLAGS, new ConditionVariable())
        .put(METHOD_SET_DEVICE_VOLUME, new ConditionVariable())
        .put(METHOD_SET_DEVICE_VOLUME_WITH_FLAGS, new ConditionVariable())
        .put(METHOD_SET_MEDIA_ITEM, new ConditionVariable())
        .put(METHOD_SET_MEDIA_ITEM_WITH_RESET_POSITION, new ConditionVariable())
        .put(METHOD_SET_MEDIA_ITEM_WITH_START_POSITION, new ConditionVariable())
        .put(METHOD_SET_MEDIA_ITEMS, new ConditionVariable())
        .put(METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, new ConditionVariable())
        .put(METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX, new ConditionVariable())
        .put(METHOD_SET_PLAY_WHEN_READY, new ConditionVariable())
        .put(METHOD_SET_PLAYBACK_PARAMETERS, new ConditionVariable())
        .put(METHOD_SET_PLAYBACK_SPEED, new ConditionVariable())
        .put(METHOD_SET_PLAYLIST_METADATA, new ConditionVariable())
        .put(METHOD_SET_REPEAT_MODE, new ConditionVariable())
        .put(METHOD_SET_SHUFFLE_MODE, new ConditionVariable())
        .put(METHOD_SET_TRACK_SELECTION_PARAMETERS, new ConditionVariable())
        .put(METHOD_SET_VOLUME, new ConditionVariable())
        .put(METHOD_SET_AUDIO_ATTRIBUTES, new ConditionVariable())
        .put(METHOD_STOP, new ConditionVariable())
        .put(METHOD_REPLACE_MEDIA_ITEM, new ConditionVariable())
        .put(METHOD_REPLACE_MEDIA_ITEMS, new ConditionVariable())
        .buildOrThrow();
  }

  /** Builder for {@link MockPlayer}. */
  public static final class Builder {

    private boolean changePlayerStateWithTransportControl;
    private Looper applicationLooper;
    private int itemCount;

    public Builder() {
      applicationLooper = Util.getCurrentOrMainLooper();
    }

    @CanIgnoreReturnValue
    public Builder setChangePlayerStateWithTransportControl(
        boolean changePlayerStateWithTransportControl) {
      this.changePlayerStateWithTransportControl = changePlayerStateWithTransportControl;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setApplicationLooper(Looper applicationLooper) {
      this.applicationLooper = applicationLooper;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setMediaItems(int itemCount) {
      this.itemCount = itemCount;
      return this;
    }

    public MockPlayer build() {
      return new MockPlayer(this);
    }
  }
}
