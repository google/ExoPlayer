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
package androidx.media3.session;

import static androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
import static androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT;
import static androidx.media3.common.Player.PLAYBACK_SUPPRESSION_REASON_NONE;
import static androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
import static androidx.media3.common.Player.STATE_IDLE;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.CheckResult;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.Bundleable;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.PlaybackSuppressionReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Player.State;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Assertions;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Information about the player that {@link MediaSession} uses to send its state to {@link
 * MediaController}.
 */
/* package */ class PlayerInfo implements Bundleable {

  private static final CueGroup EMPTY_CUE_GROUP =
      new CueGroup(ImmutableList.of(), /* presentationTimeUs= */ 0);

  public static class Builder {

    @Nullable private PlaybackException playerError;
    @Player.MediaItemTransitionReason private int mediaItemTransitionReason;
    private SessionPositionInfo sessionPositionInfo;
    private PositionInfo oldPositionInfo;
    private PositionInfo newPositionInfo;
    @Player.DiscontinuityReason private int discontinuityReason;
    private PlaybackParameters playbackParameters;
    @Player.RepeatMode private int repeatMode;
    private boolean shuffleModeEnabled;
    private Timeline timeline;
    private VideoSize videoSize;
    private MediaMetadata playlistMetadata;
    private float volume;
    private AudioAttributes audioAttributes;
    private CueGroup cueGroup;
    private DeviceInfo deviceInfo;
    private int deviceVolume;
    private boolean deviceMuted;
    private boolean playWhenReady;
    @Player.PlayWhenReadyChangeReason private int playWhenReadyChangedReason;
    private boolean isPlaying;
    private boolean isLoading;
    @PlaybackSuppressionReason private int playbackSuppressionReason;
    @State private int playbackState;
    private MediaMetadata mediaMetadata;
    private long seekBackIncrementMs;
    private long seekForwardIncrementMs;
    private long maxSeekToPreviousPositionMs;
    private Tracks currentTracks;
    private TrackSelectionParameters trackSelectionParameters;

    public Builder(PlayerInfo playerInfo) {
      playerError = playerInfo.playerError;
      mediaItemTransitionReason = playerInfo.mediaItemTransitionReason;
      sessionPositionInfo = playerInfo.sessionPositionInfo;
      oldPositionInfo = playerInfo.oldPositionInfo;
      newPositionInfo = playerInfo.newPositionInfo;
      discontinuityReason = playerInfo.discontinuityReason;
      playbackParameters = playerInfo.playbackParameters;
      repeatMode = playerInfo.repeatMode;
      shuffleModeEnabled = playerInfo.shuffleModeEnabled;
      timeline = playerInfo.timeline;
      videoSize = playerInfo.videoSize;
      playlistMetadata = playerInfo.playlistMetadata;
      volume = playerInfo.volume;
      audioAttributes = playerInfo.audioAttributes;
      cueGroup = playerInfo.cueGroup;
      deviceInfo = playerInfo.deviceInfo;
      deviceVolume = playerInfo.deviceVolume;
      deviceMuted = playerInfo.deviceMuted;
      playWhenReady = playerInfo.playWhenReady;
      playWhenReadyChangedReason = playerInfo.playWhenReadyChangedReason;
      isPlaying = playerInfo.isPlaying;
      isLoading = playerInfo.isLoading;
      playbackSuppressionReason = playerInfo.playbackSuppressionReason;
      playbackState = playerInfo.playbackState;
      mediaMetadata = playerInfo.mediaMetadata;
      seekBackIncrementMs = playerInfo.seekBackIncrementMs;
      seekForwardIncrementMs = playerInfo.seekForwardIncrementMs;
      maxSeekToPreviousPositionMs = playerInfo.maxSeekToPreviousPositionMs;
      currentTracks = playerInfo.currentTracks;
      trackSelectionParameters = playerInfo.trackSelectionParameters;
    }

    public Builder setPlayerError(@Nullable PlaybackException playerError) {
      this.playerError = playerError;
      return this;
    }

    public Builder setMediaItemTransitionReason(
        @Player.MediaItemTransitionReason int mediaItemTransitionReason) {
      this.mediaItemTransitionReason = mediaItemTransitionReason;
      return this;
    }

    public Builder setSessionPositionInfo(SessionPositionInfo sessionPositionInfo) {
      this.sessionPositionInfo = sessionPositionInfo;
      return this;
    }

    public Builder setOldPositionInfo(PositionInfo oldPositionInfo) {
      this.oldPositionInfo = oldPositionInfo;
      return this;
    }

    public Builder setNewPositionInfo(PositionInfo newPositionInfo) {
      this.newPositionInfo = newPositionInfo;
      return this;
    }

    public Builder setDiscontinuityReason(@Player.DiscontinuityReason int discontinuityReason) {
      this.discontinuityReason = discontinuityReason;
      return this;
    }

    public Builder setPlaybackParameters(PlaybackParameters playbackParameters) {
      this.playbackParameters = playbackParameters;
      return this;
    }

    public Builder setRepeatMode(@Player.RepeatMode int repeatMode) {
      this.repeatMode = repeatMode;
      return this;
    }

    public Builder setShuffleModeEnabled(boolean shuffleModeEnabled) {
      this.shuffleModeEnabled = shuffleModeEnabled;
      return this;
    }

    public Builder setTimeline(Timeline timeline) {
      this.timeline = timeline;
      return this;
    }

    public Builder setVideoSize(VideoSize videoSize) {
      this.videoSize = videoSize;
      return this;
    }

    public Builder setPlaylistMetadata(MediaMetadata playlistMetadata) {
      this.playlistMetadata = playlistMetadata;
      return this;
    }

    public Builder setVolume(@FloatRange(from = 0, to = 1) float volume) {
      this.volume = volume;
      return this;
    }

    public Builder setAudioAttributes(AudioAttributes audioAttributes) {
      this.audioAttributes = audioAttributes;
      return this;
    }

    public Builder setCues(CueGroup cueGroup) {
      this.cueGroup = cueGroup;
      return this;
    }

    public Builder setDeviceInfo(DeviceInfo deviceInfo) {
      this.deviceInfo = deviceInfo;
      return this;
    }

    public Builder setDeviceVolume(int deviceVolume) {
      this.deviceVolume = deviceVolume;
      return this;
    }

    public Builder setDeviceMuted(boolean deviceMuted) {
      this.deviceMuted = deviceMuted;
      return this;
    }

    public Builder setPlayWhenReady(boolean playWhenReady) {
      this.playWhenReady = playWhenReady;
      return this;
    }

    public Builder setPlayWhenReadyChangedReason(
        @Player.PlayWhenReadyChangeReason int playWhenReadyChangedReason) {
      this.playWhenReadyChangedReason = playWhenReadyChangedReason;
      return this;
    }

    public Builder setIsPlaying(boolean isPlaying) {
      this.isPlaying = isPlaying;
      return this;
    }

    public Builder setIsLoading(boolean isLoading) {
      this.isLoading = isLoading;
      return this;
    }

    public Builder setPlaybackSuppressionReason(
        @PlaybackSuppressionReason int playbackSuppressionReason) {
      this.playbackSuppressionReason = playbackSuppressionReason;
      return this;
    }

    public Builder setPlaybackState(@State int playbackState) {
      this.playbackState = playbackState;
      return this;
    }

    public Builder setMediaMetadata(MediaMetadata mediaMetadata) {
      this.mediaMetadata = mediaMetadata;
      return this;
    }

    public Builder setSeekBackIncrement(long seekBackIncrementMs) {
      this.seekBackIncrementMs = seekBackIncrementMs;
      return this;
    }

    public Builder setSeekForwardIncrement(long seekForwardIncrementMs) {
      this.seekForwardIncrementMs = seekForwardIncrementMs;
      return this;
    }

    public Builder setMaxSeekToPreviousPositionMs(long maxSeekToPreviousPositionMs) {
      this.maxSeekToPreviousPositionMs = maxSeekToPreviousPositionMs;
      return this;
    }

    public Builder setCurrentTracks(Tracks tracks) {
      currentTracks = tracks;
      return this;
    }

    public Builder setTrackSelectionParameters(TrackSelectionParameters parameters) {
      trackSelectionParameters = parameters;
      return this;
    }

    public PlayerInfo build() {
      Assertions.checkState(
          timeline.isEmpty()
              || sessionPositionInfo.positionInfo.mediaItemIndex < timeline.getWindowCount());
      return new PlayerInfo(
          playerError,
          mediaItemTransitionReason,
          sessionPositionInfo,
          oldPositionInfo,
          newPositionInfo,
          discontinuityReason,
          playbackParameters,
          repeatMode,
          shuffleModeEnabled,
          videoSize,
          timeline,
          playlistMetadata,
          volume,
          audioAttributes,
          cueGroup,
          deviceInfo,
          deviceVolume,
          deviceMuted,
          playWhenReady,
          playWhenReadyChangedReason,
          playbackSuppressionReason,
          playbackState,
          isPlaying,
          isLoading,
          mediaMetadata,
          seekBackIncrementMs,
          seekForwardIncrementMs,
          maxSeekToPreviousPositionMs,
          currentTracks,
          trackSelectionParameters);
    }
  }

  /** Default media item transition reason. */
  public static final int MEDIA_ITEM_TRANSITION_REASON_DEFAULT =
      MEDIA_ITEM_TRANSITION_REASON_REPEAT;

  /** Default discontinuity reason. */
  public static final int DISCONTINUITY_REASON_DEFAULT = DISCONTINUITY_REASON_AUTO_TRANSITION;

  /** Default play when ready change reason. */
  public static final int PLAY_WHEN_READY_CHANGE_REASON_DEFAULT =
      PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;

  public static final PlayerInfo DEFAULT =
      new PlayerInfo(
          /* playerError= */ null,
          MEDIA_ITEM_TRANSITION_REASON_DEFAULT,
          SessionPositionInfo.DEFAULT,
          /* oldPositionInfo= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
          /* newPositionInfo= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
          DISCONTINUITY_REASON_DEFAULT,
          PlaybackParameters.DEFAULT,
          Player.REPEAT_MODE_OFF,
          /* shuffleModeEnabled= */ false,
          VideoSize.UNKNOWN,
          Timeline.EMPTY,
          MediaMetadata.EMPTY,
          /* volume= */ 1f,
          AudioAttributes.DEFAULT,
          /* cueGroup = */ EMPTY_CUE_GROUP,
          DeviceInfo.UNKNOWN,
          /* deviceVolume= */ 0,
          /* deviceMuted= */ false,
          /* playWhenReady = */ false,
          PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
          PLAYBACK_SUPPRESSION_REASON_NONE,
          STATE_IDLE,
          /* isPlaying= */ false,
          /* isLoading= */ false,
          MediaMetadata.EMPTY,
          /* seekBackIncrementMs= */ 0,
          /* seekForwardIncrementMs= */ 0,
          /* maxSeekToPreviousPositionMs= */ 0,
          /* currentTracks= */ Tracks.EMPTY,
          TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT);

  @Nullable public final PlaybackException playerError;

  @Player.MediaItemTransitionReason public final int mediaItemTransitionReason;

  public final SessionPositionInfo sessionPositionInfo;

  public final PositionInfo oldPositionInfo;

  public final PositionInfo newPositionInfo;

  @Player.DiscontinuityReason public final int discontinuityReason;

  public final PlaybackParameters playbackParameters;

  @Player.RepeatMode public final int repeatMode;

  public final boolean shuffleModeEnabled;

  public final Timeline timeline;

  public final VideoSize videoSize;

  public final MediaMetadata playlistMetadata;

  public final float volume;

  public final AudioAttributes audioAttributes;

  public final CueGroup cueGroup;

  public final DeviceInfo deviceInfo;

  public final int deviceVolume;

  public final boolean deviceMuted;

  public final boolean playWhenReady;

  public final int playWhenReadyChangedReason;

  public final boolean isPlaying;

  public final boolean isLoading;

  @Player.PlaybackSuppressionReason public final int playbackSuppressionReason;

  @Player.State public final int playbackState;

  public final MediaMetadata mediaMetadata;

  public final long seekBackIncrementMs;

  public final long seekForwardIncrementMs;

  public final long maxSeekToPreviousPositionMs;

  public final Tracks currentTracks;

  public final TrackSelectionParameters trackSelectionParameters;

  @CheckResult
  public PlayerInfo copyWithPlayWhenReady(
      boolean playWhenReady,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangedReason,
      @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
    return new Builder(this)
        .setPlayWhenReady(playWhenReady)
        .setPlayWhenReadyChangedReason(playWhenReadyChangedReason)
        .setPlaybackSuppressionReason(playbackSuppressionReason)
        .setIsPlaying(isPlaying(playbackState, playWhenReady, playbackSuppressionReason))
        .build();
  }

  @CheckResult
  public PlayerInfo copyWithMediaItemTransitionReason(
      @Player.MediaItemTransitionReason int mediaItemTransitionReason) {
    return new Builder(this).setMediaItemTransitionReason(mediaItemTransitionReason).build();
  }

  @CheckResult
  public PlayerInfo copyWithPlayerError(PlaybackException playerError) {
    return new Builder(this).setPlayerError(playerError).build();
  }

  @CheckResult
  public PlayerInfo copyWithPlaybackState(
      @Player.State int playbackState, @Nullable PlaybackException playerError) {
    return new Builder(this)
        .setPlayerError(playerError)
        .setPlaybackState(playbackState)
        .setIsPlaying(isPlaying(playbackState, playWhenReady, playbackSuppressionReason))
        .build();
  }

  @CheckResult
  public PlayerInfo copyWithIsPlaying(boolean isPlaying) {
    return new Builder(this).setIsPlaying(isPlaying).build();
  }

  @CheckResult
  public PlayerInfo copyWithIsLoading(boolean isLoading) {
    return new Builder(this).setIsLoading(isLoading).build();
  }

  @CheckResult
  public PlayerInfo copyWithPlaybackParameters(PlaybackParameters playbackParameters) {
    return new Builder(this).setPlaybackParameters(playbackParameters).build();
  }

  @CheckResult
  public PlayerInfo copyWithPositionInfos(
      PositionInfo oldPositionInfo,
      PositionInfo newPositionInfo,
      @Player.DiscontinuityReason int discontinuityReason) {
    return new Builder(this)
        .setOldPositionInfo(oldPositionInfo)
        .setNewPositionInfo(newPositionInfo)
        .setDiscontinuityReason(discontinuityReason)
        .build();
  }

  @CheckResult
  public PlayerInfo copyWithSessionPositionInfo(SessionPositionInfo sessionPositionInfo) {
    return new Builder(this).setSessionPositionInfo(sessionPositionInfo).build();
  }

  @CheckResult
  public PlayerInfo copyWithTimeline(Timeline timeline) {
    return new Builder(this).setTimeline(timeline).build();
  }

  @CheckResult
  public PlayerInfo copyWithTimelineAndSessionPositionInfo(
      Timeline timeline, SessionPositionInfo sessionPositionInfo) {
    return new Builder(this)
        .setTimeline(timeline)
        .setSessionPositionInfo(sessionPositionInfo)
        .build();
  }

  @CheckResult
  public PlayerInfo copyWithTimelineAndMediaItemIndex(Timeline timeline, int mediaItemIndex) {
    return new Builder(this)
        .setTimeline(timeline)
        .setSessionPositionInfo(
            new SessionPositionInfo(
                new PositionInfo(
                    sessionPositionInfo.positionInfo.windowUid,
                    mediaItemIndex,
                    sessionPositionInfo.positionInfo.mediaItem,
                    sessionPositionInfo.positionInfo.periodUid,
                    sessionPositionInfo.positionInfo.periodIndex,
                    sessionPositionInfo.positionInfo.positionMs,
                    sessionPositionInfo.positionInfo.contentPositionMs,
                    sessionPositionInfo.positionInfo.adGroupIndex,
                    sessionPositionInfo.positionInfo.adIndexInAdGroup),
                sessionPositionInfo.isPlayingAd,
                /* eventTimeMs= */ SystemClock.elapsedRealtime(),
                sessionPositionInfo.durationMs,
                sessionPositionInfo.bufferedPositionMs,
                sessionPositionInfo.bufferedPercentage,
                sessionPositionInfo.totalBufferedDurationMs,
                sessionPositionInfo.currentLiveOffsetMs,
                sessionPositionInfo.contentDurationMs,
                sessionPositionInfo.contentBufferedPositionMs))
        .build();
  }

  @CheckResult
  public PlayerInfo copyWithPlaylistMetadata(MediaMetadata playlistMetadata) {
    return new Builder(this).setPlaylistMetadata(playlistMetadata).build();
  }

  @CheckResult
  public PlayerInfo copyWithRepeatMode(@Player.RepeatMode int repeatMode) {
    return new Builder(this).setRepeatMode(repeatMode).build();
  }

  @CheckResult
  public PlayerInfo copyWithShuffleModeEnabled(boolean shuffleModeEnabled) {
    return new Builder(this).setShuffleModeEnabled(shuffleModeEnabled).build();
  }

  @CheckResult
  public PlayerInfo copyWithAudioAttributes(AudioAttributes audioAttributes) {
    return new Builder(this).setAudioAttributes(audioAttributes).build();
  }

  @CheckResult
  public PlayerInfo copyWithVideoSize(VideoSize videoSize) {
    return new Builder(this).setVideoSize(videoSize).build();
  }

  @CheckResult
  public PlayerInfo copyWithVolume(@FloatRange(from = 0, to = 1) float volume) {
    return new Builder(this).setVolume(volume).build();
  }

  @CheckResult
  public PlayerInfo copyWithDeviceInfo(DeviceInfo deviceInfo) {
    return new Builder(this).setDeviceInfo(deviceInfo).build();
  }

  @CheckResult
  public PlayerInfo copyWithDeviceVolume(int deviceVolume, boolean deviceMuted) {
    return new Builder(this).setDeviceVolume(deviceVolume).setDeviceMuted(deviceMuted).build();
  }

  @CheckResult
  public PlayerInfo copyWithMediaMetadata(MediaMetadata mediaMetadata) {
    return new Builder(this).setMediaMetadata(mediaMetadata).build();
  }

  @CheckResult
  public PlayerInfo copyWithSeekBackIncrement(long seekBackIncrementMs) {
    return new Builder(this).setSeekBackIncrement(seekBackIncrementMs).build();
  }

  @CheckResult
  public PlayerInfo copyWithSeekForwardIncrement(long seekForwardIncrementMs) {
    return new Builder(this).setSeekForwardIncrement(seekForwardIncrementMs).build();
  }

  @CheckResult
  public PlayerInfo copyWithMaxSeekToPreviousPositionMs(long maxSeekToPreviousPositionMs) {
    return new Builder(this).setMaxSeekToPreviousPositionMs(maxSeekToPreviousPositionMs).build();
  }

  public PlayerInfo copyWithCurrentTracks(Tracks tracks) {
    return new Builder(this).setCurrentTracks(tracks).build();
  }

  @CheckResult
  public PlayerInfo copyWithTrackSelectionParameters(TrackSelectionParameters parameters) {
    return new Builder(this).setTrackSelectionParameters(parameters).build();
  }

  public PlayerInfo(
      @Nullable PlaybackException playerError,
      @Player.MediaItemTransitionReason int mediaItemTransitionReason,
      SessionPositionInfo sessionPositionInfo,
      PositionInfo oldPositionInfo,
      PositionInfo newPositionInfo,
      @Player.DiscontinuityReason int discontinuityReason,
      PlaybackParameters playbackParameters,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      VideoSize videoSize,
      Timeline timeline,
      MediaMetadata playlistMetadata,
      float volume,
      AudioAttributes audioAttributes,
      CueGroup cueGroup,
      DeviceInfo deviceInfo,
      int deviceVolume,
      boolean deviceMuted,
      boolean playWhenReady,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangedReason,
      @Player.PlaybackSuppressionReason int playbackSuppressionReason,
      @Player.State int playbackState,
      boolean isPlaying,
      boolean isLoading,
      MediaMetadata mediaMetadata,
      long seekBackIncrementMs,
      long seekForwardIncrementMs,
      long maxSeekToPreviousPositionMs,
      Tracks currentTracks,
      TrackSelectionParameters parameters) {
    this.playerError = playerError;
    this.mediaItemTransitionReason = mediaItemTransitionReason;
    this.sessionPositionInfo = sessionPositionInfo;
    this.oldPositionInfo = oldPositionInfo;
    this.newPositionInfo = newPositionInfo;
    this.discontinuityReason = discontinuityReason;
    this.playbackParameters = playbackParameters;
    this.repeatMode = repeatMode;
    this.shuffleModeEnabled = shuffleModeEnabled;
    this.videoSize = videoSize;
    this.timeline = timeline;
    this.playlistMetadata = playlistMetadata;
    this.volume = volume;
    this.audioAttributes = audioAttributes;
    this.cueGroup = cueGroup;
    this.deviceInfo = deviceInfo;
    this.deviceVolume = deviceVolume;
    this.deviceMuted = deviceMuted;
    this.playWhenReady = playWhenReady;
    this.playWhenReadyChangedReason = playWhenReadyChangedReason;
    this.playbackSuppressionReason = playbackSuppressionReason;
    this.playbackState = playbackState;
    this.isPlaying = isPlaying;
    this.isLoading = isLoading;
    this.mediaMetadata = mediaMetadata;
    this.seekBackIncrementMs = seekBackIncrementMs;
    this.seekForwardIncrementMs = seekForwardIncrementMs;
    this.maxSeekToPreviousPositionMs = maxSeekToPreviousPositionMs;
    this.currentTracks = currentTracks;
    this.trackSelectionParameters = parameters;
  }

  @Nullable
  public MediaItem getCurrentMediaItem() {
    return timeline.isEmpty()
        ? null
        : timeline.getWindow(sessionPositionInfo.positionInfo.mediaItemIndex, new Window())
            .mediaItem;
  }

  private boolean isPlaying(
      @State int playbackState,
      boolean playWhenReady,
      @PlaybackSuppressionReason int playbackSuppressionReason) {
    return playbackState == Player.STATE_READY
        && playWhenReady
        && playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    FIELD_PLAYBACK_PARAMETERS,
    FIELD_REPEAT_MODE,
    FIELD_SHUFFLE_MODE_ENABLED,
    FIELD_TIMELINE,
    FIELD_VIDEO_SIZE,
    FIELD_PLAYLIST_METADATA,
    FIELD_VOLUME,
    FIELD_AUDIO_ATTRIBUTES,
    FIELD_DEVICE_INFO,
    FIELD_DEVICE_VOLUME,
    FIELD_DEVICE_MUTED,
    FIELD_PLAY_WHEN_READY,
    FIELD_PLAY_WHEN_READY_CHANGED_REASON,
    FIELD_PLAYBACK_SUPPRESSION_REASON,
    FIELD_PLAYBACK_STATE,
    FIELD_IS_PLAYING,
    FIELD_IS_LOADING,
    FIELD_PLAYBACK_ERROR,
    FIELD_SESSION_POSITION_INFO,
    FIELD_MEDIA_ITEM_TRANSITION_REASON,
    FIELD_OLD_POSITION_INFO,
    FIELD_NEW_POSITION_INFO,
    FIELD_DISCONTINUITY_REASON,
    FIELD_CUE_GROUP,
    FIELD_MEDIA_METADATA,
    FIELD_SEEK_BACK_INCREMENT_MS,
    FIELD_SEEK_FORWARD_INCREMENT_MS,
    FIELD_MAX_SEEK_TO_PREVIOUS_POSITION_MS,
    FIELD_TRACK_SELECTION_PARAMETERS,
    FIELD_CURRENT_TRACKS,
  })
  private @interface FieldNumber {}

  private static final int FIELD_PLAYBACK_PARAMETERS = 1;
  private static final int FIELD_REPEAT_MODE = 2;
  private static final int FIELD_SHUFFLE_MODE_ENABLED = 3;
  private static final int FIELD_TIMELINE = 4;
  private static final int FIELD_VIDEO_SIZE = 5;
  private static final int FIELD_PLAYLIST_METADATA = 6;
  private static final int FIELD_VOLUME = 7;
  private static final int FIELD_AUDIO_ATTRIBUTES = 8;
  private static final int FIELD_DEVICE_INFO = 9;
  private static final int FIELD_DEVICE_VOLUME = 10;
  private static final int FIELD_DEVICE_MUTED = 11;
  private static final int FIELD_PLAY_WHEN_READY = 12;
  private static final int FIELD_PLAY_WHEN_READY_CHANGED_REASON = 13;
  private static final int FIELD_PLAYBACK_SUPPRESSION_REASON = 14;
  private static final int FIELD_PLAYBACK_STATE = 15;
  private static final int FIELD_IS_PLAYING = 16;
  private static final int FIELD_IS_LOADING = 17;
  private static final int FIELD_PLAYBACK_ERROR = 18;
  private static final int FIELD_SESSION_POSITION_INFO = 19;
  private static final int FIELD_MEDIA_ITEM_TRANSITION_REASON = 20;
  private static final int FIELD_OLD_POSITION_INFO = 21;
  private static final int FIELD_NEW_POSITION_INFO = 22;
  private static final int FIELD_DISCONTINUITY_REASON = 23;
  private static final int FIELD_CUE_GROUP = 24;
  private static final int FIELD_MEDIA_METADATA = 25;
  private static final int FIELD_SEEK_BACK_INCREMENT_MS = 26;
  private static final int FIELD_SEEK_FORWARD_INCREMENT_MS = 27;
  private static final int FIELD_MAX_SEEK_TO_PREVIOUS_POSITION_MS = 28;
  private static final int FIELD_TRACK_SELECTION_PARAMETERS = 29;
  private static final int FIELD_CURRENT_TRACKS = 30;
  // Next field key = 31

  public Bundle toBundle(
      boolean excludeMediaItems,
      boolean excludeMediaItemsMetadata,
      boolean excludeCues,
      boolean excludeTimeline,
      boolean excludeTracks) {
    Bundle bundle = new Bundle();
    if (playerError != null) {
      bundle.putBundle(keyForField(FIELD_PLAYBACK_ERROR), playerError.toBundle());
    }
    bundle.putInt(keyForField(FIELD_MEDIA_ITEM_TRANSITION_REASON), mediaItemTransitionReason);
    bundle.putBundle(keyForField(FIELD_SESSION_POSITION_INFO), sessionPositionInfo.toBundle());
    bundle.putBundle(keyForField(FIELD_OLD_POSITION_INFO), oldPositionInfo.toBundle());
    bundle.putBundle(keyForField(FIELD_NEW_POSITION_INFO), newPositionInfo.toBundle());
    bundle.putInt(keyForField(FIELD_DISCONTINUITY_REASON), discontinuityReason);
    bundle.putBundle(keyForField(FIELD_PLAYBACK_PARAMETERS), playbackParameters.toBundle());
    bundle.putInt(keyForField(FIELD_REPEAT_MODE), repeatMode);
    bundle.putBoolean(keyForField(FIELD_SHUFFLE_MODE_ENABLED), shuffleModeEnabled);
    if (!excludeTimeline) {
      bundle.putBundle(keyForField(FIELD_TIMELINE), timeline.toBundle(excludeMediaItems));
    }
    bundle.putBundle(keyForField(FIELD_VIDEO_SIZE), videoSize.toBundle());
    if (!excludeMediaItemsMetadata) {
      bundle.putBundle(keyForField(FIELD_PLAYLIST_METADATA), playlistMetadata.toBundle());
    }
    bundle.putFloat(keyForField(FIELD_VOLUME), volume);
    bundle.putBundle(keyForField(FIELD_AUDIO_ATTRIBUTES), audioAttributes.toBundle());
    if (!excludeCues) {
      bundle.putBundle(keyForField(FIELD_CUE_GROUP), cueGroup.toBundle());
    }
    bundle.putBundle(keyForField(FIELD_DEVICE_INFO), deviceInfo.toBundle());
    bundle.putInt(keyForField(FIELD_DEVICE_VOLUME), deviceVolume);
    bundle.putBoolean(keyForField(FIELD_DEVICE_MUTED), deviceMuted);
    bundle.putBoolean(keyForField(FIELD_PLAY_WHEN_READY), playWhenReady);
    bundle.putInt(keyForField(FIELD_PLAYBACK_SUPPRESSION_REASON), playbackSuppressionReason);
    bundle.putInt(keyForField(FIELD_PLAYBACK_STATE), playbackState);
    bundle.putBoolean(keyForField(FIELD_IS_PLAYING), isPlaying);
    bundle.putBoolean(keyForField(FIELD_IS_LOADING), isLoading);
    bundle.putBundle(
        keyForField(FIELD_MEDIA_METADATA),
        excludeMediaItems ? MediaMetadata.EMPTY.toBundle() : mediaMetadata.toBundle());
    bundle.putLong(keyForField(FIELD_SEEK_BACK_INCREMENT_MS), seekBackIncrementMs);
    bundle.putLong(keyForField(FIELD_SEEK_FORWARD_INCREMENT_MS), seekForwardIncrementMs);
    bundle.putLong(
        keyForField(FIELD_MAX_SEEK_TO_PREVIOUS_POSITION_MS), maxSeekToPreviousPositionMs);
    if (!excludeTracks) {
      bundle.putBundle(keyForField(FIELD_CURRENT_TRACKS), currentTracks.toBundle());
    }
    bundle.putBundle(
        keyForField(FIELD_TRACK_SELECTION_PARAMETERS), trackSelectionParameters.toBundle());

    return bundle;
  }

  @Override
  public Bundle toBundle() {
    return toBundle(
        /* excludeMediaItems= */ false,
        /* excludeMediaItemsMetadata= */ false,
        /* excludeCues= */ false,
        /* excludeTimeline= */ false,
        /* excludeTracks= */ false);
  }

  /** Object that can restore {@link PlayerInfo} from a {@link Bundle}. */
  public static final Creator<PlayerInfo> CREATOR = PlayerInfo::fromBundle;

  private static PlayerInfo fromBundle(Bundle bundle) {
    @Nullable Bundle playerErrorBundle = bundle.getBundle(keyForField(FIELD_PLAYBACK_ERROR));
    @Nullable
    PlaybackException playerError =
        playerErrorBundle == null ? null : PlaybackException.CREATOR.fromBundle(playerErrorBundle);
    int mediaItemTransitionReason =
        bundle.getInt(
            keyForField(FIELD_MEDIA_ITEM_TRANSITION_REASON), MEDIA_ITEM_TRANSITION_REASON_REPEAT);
    @Nullable
    Bundle sessionPositionInfoBundle = bundle.getBundle(keyForField(FIELD_SESSION_POSITION_INFO));
    SessionPositionInfo sessionPositionInfo =
        sessionPositionInfoBundle == null
            ? SessionPositionInfo.DEFAULT
            : SessionPositionInfo.CREATOR.fromBundle(sessionPositionInfoBundle);
    @Nullable Bundle oldPositionInfoBundle = bundle.getBundle(keyForField(FIELD_OLD_POSITION_INFO));
    PositionInfo oldPositionInfo =
        oldPositionInfoBundle == null
            ? SessionPositionInfo.DEFAULT_POSITION_INFO
            : PositionInfo.CREATOR.fromBundle(oldPositionInfoBundle);
    @Nullable Bundle newPositionInfoBundle = bundle.getBundle(keyForField(FIELD_NEW_POSITION_INFO));
    PositionInfo newPositionInfo =
        newPositionInfoBundle == null
            ? SessionPositionInfo.DEFAULT_POSITION_INFO
            : PositionInfo.CREATOR.fromBundle(newPositionInfoBundle);
    int discontinuityReason =
        bundle.getInt(
            keyForField(FIELD_DISCONTINUITY_REASON), DISCONTINUITY_REASON_AUTO_TRANSITION);
    @Nullable
    Bundle playbackParametersBundle = bundle.getBundle(keyForField(FIELD_PLAYBACK_PARAMETERS));
    PlaybackParameters playbackParameters =
        playbackParametersBundle == null
            ? PlaybackParameters.DEFAULT
            : PlaybackParameters.CREATOR.fromBundle(playbackParametersBundle);
    @Player.RepeatMode
    int repeatMode =
        bundle.getInt(keyForField(FIELD_REPEAT_MODE), /* defaultValue= */ Player.REPEAT_MODE_OFF);
    boolean shuffleModeEnabled =
        bundle.getBoolean(keyForField(FIELD_SHUFFLE_MODE_ENABLED), /* defaultValue= */ false);
    @Nullable Bundle timelineBundle = bundle.getBundle(keyForField(FIELD_TIMELINE));
    Timeline timeline =
        timelineBundle == null ? Timeline.EMPTY : Timeline.CREATOR.fromBundle(timelineBundle);
    @Nullable Bundle videoSizeBundle = bundle.getBundle(keyForField(FIELD_VIDEO_SIZE));
    VideoSize videoSize =
        videoSizeBundle == null ? VideoSize.UNKNOWN : VideoSize.CREATOR.fromBundle(videoSizeBundle);
    @Nullable
    Bundle playlistMetadataBundle = bundle.getBundle(keyForField(FIELD_PLAYLIST_METADATA));
    MediaMetadata playlistMetadata =
        playlistMetadataBundle == null
            ? MediaMetadata.EMPTY
            : MediaMetadata.CREATOR.fromBundle(playlistMetadataBundle);
    float volume = bundle.getFloat(keyForField(FIELD_VOLUME), /* defaultValue= */ 1);
    @Nullable Bundle audioAttributesBundle = bundle.getBundle(keyForField(FIELD_AUDIO_ATTRIBUTES));
    AudioAttributes audioAttributes =
        audioAttributesBundle == null
            ? AudioAttributes.DEFAULT
            : AudioAttributes.CREATOR.fromBundle(audioAttributesBundle);
    @Nullable Bundle cueGroupBundle = bundle.getBundle(keyForField(FIELD_CUE_GROUP));
    CueGroup cueGroup =
        cueGroupBundle == null ? EMPTY_CUE_GROUP : CueGroup.CREATOR.fromBundle(cueGroupBundle);
    @Nullable Bundle deviceInfoBundle = bundle.getBundle(keyForField(FIELD_DEVICE_INFO));
    DeviceInfo deviceInfo =
        deviceInfoBundle == null
            ? DeviceInfo.UNKNOWN
            : DeviceInfo.CREATOR.fromBundle(deviceInfoBundle);
    int deviceVolume = bundle.getInt(keyForField(FIELD_DEVICE_VOLUME), /* defaultValue= */ 0);
    boolean deviceMuted =
        bundle.getBoolean(keyForField(FIELD_DEVICE_MUTED), /* defaultValue= */ false);
    boolean playWhenReady =
        bundle.getBoolean(keyForField(FIELD_PLAY_WHEN_READY), /* defaultValue= */ false);
    int playWhenReadyChangedReason =
        bundle.getInt(
            keyForField(FIELD_PLAY_WHEN_READY_CHANGED_REASON),
            /* defaultValue= */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    @Player.PlaybackSuppressionReason
    int playbackSuppressionReason =
        bundle.getInt(
            keyForField(FIELD_PLAYBACK_SUPPRESSION_REASON),
            /* defaultValue= */ PLAYBACK_SUPPRESSION_REASON_NONE);
    @Player.State
    int playbackState =
        bundle.getInt(keyForField(FIELD_PLAYBACK_STATE), /* defaultValue= */ STATE_IDLE);
    boolean isPlaying = bundle.getBoolean(keyForField(FIELD_IS_PLAYING), /* defaultValue= */ false);
    boolean isLoading = bundle.getBoolean(keyForField(FIELD_IS_LOADING), /* defaultValue= */ false);
    @Nullable Bundle mediaMetadataBundle = bundle.getBundle(keyForField(FIELD_MEDIA_METADATA));
    MediaMetadata mediaMetadata =
        mediaMetadataBundle == null
            ? MediaMetadata.EMPTY
            : MediaMetadata.CREATOR.fromBundle(mediaMetadataBundle);
    long seekBackIncrementMs =
        bundle.getLong(keyForField(FIELD_SEEK_BACK_INCREMENT_MS), /* defaultValue= */ 0);
    long seekForwardIncrementMs =
        bundle.getLong(keyForField(FIELD_SEEK_FORWARD_INCREMENT_MS), /* defaultValue= */ 0);
    long maxSeekToPreviousPosition =
        bundle.getLong(keyForField(FIELD_MAX_SEEK_TO_PREVIOUS_POSITION_MS), /* defaultValue= */ 0);
    Bundle currentTracksBundle = bundle.getBundle(keyForField(FIELD_CURRENT_TRACKS));
    Tracks currentTracks =
        currentTracksBundle == null ? Tracks.EMPTY : Tracks.CREATOR.fromBundle(currentTracksBundle);
    @Nullable
    Bundle trackSelectionParametersBundle =
        bundle.getBundle(keyForField(FIELD_TRACK_SELECTION_PARAMETERS));
    TrackSelectionParameters trackSelectionParameters =
        trackSelectionParametersBundle == null
            ? TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
            : TrackSelectionParameters.fromBundle(trackSelectionParametersBundle);
    return new PlayerInfo(
        playerError,
        mediaItemTransitionReason,
        sessionPositionInfo,
        oldPositionInfo,
        newPositionInfo,
        discontinuityReason,
        playbackParameters,
        repeatMode,
        shuffleModeEnabled,
        videoSize,
        timeline,
        playlistMetadata,
        volume,
        audioAttributes,
        cueGroup,
        deviceInfo,
        deviceVolume,
        deviceMuted,
        playWhenReady,
        playWhenReadyChangedReason,
        playbackSuppressionReason,
        playbackState,
        isPlaying,
        isLoading,
        mediaMetadata,
        seekBackIncrementMs,
        seekForwardIncrementMs,
        maxSeekToPreviousPosition,
        currentTracks,
        trackSelectionParameters);
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
