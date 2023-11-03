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
import static androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.CheckResult;
import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
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
import androidx.media3.common.util.BundleUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Information about the player that {@link MediaSession} uses to send its state to {@link
 * MediaController}.
 */
/* package */ class PlayerInfo implements Bundleable {

  /**
   * Holds information about what properties of the {@link PlayerInfo} have been excluded when sent
   * to the controller.
   */
  public static class BundlingExclusions implements Bundleable {

    /** Bundling exclusions with no exclusions. */
    public static final BundlingExclusions NONE =
        new BundlingExclusions(
            /* isTimelineExcluded= */ false, /* areCurrentTracksExcluded= */ false);

    /** Whether the {@linkplain PlayerInfo#timeline timeline} is excluded. */
    public final boolean isTimelineExcluded;

    /** Whether the {@linkplain PlayerInfo#currentTracks current tracks} are excluded. */
    public final boolean areCurrentTracksExcluded;

    /** Creates a new instance. */
    public BundlingExclusions(boolean isTimelineExcluded, boolean areCurrentTracksExcluded) {
      this.isTimelineExcluded = isTimelineExcluded;
      this.areCurrentTracksExcluded = areCurrentTracksExcluded;
    }

    // Bundleable implementation.

    private static final String FIELD_IS_TIMELINE_EXCLUDED = Util.intToStringMaxRadix(0);
    private static final String FIELD_ARE_CURRENT_TRACKS_EXCLUDED = Util.intToStringMaxRadix(1);

    // Next field key = 2

    @UnstableApi
    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putBoolean(FIELD_IS_TIMELINE_EXCLUDED, isTimelineExcluded);
      bundle.putBoolean(FIELD_ARE_CURRENT_TRACKS_EXCLUDED, areCurrentTracksExcluded);
      return bundle;
    }

    /**
     * @deprecated Use {@link #fromBundle} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation") // Deprecated instance of deprecated class
    public static final Creator<BundlingExclusions> CREATOR = BundlingExclusions::fromBundle;

    /** Restores a {@code BundlingExclusions} from a {@link Bundle}. */
    public static BundlingExclusions fromBundle(Bundle bundle) {
      return new BundlingExclusions(
          bundle.getBoolean(FIELD_IS_TIMELINE_EXCLUDED, /* defaultValue= */ false),
          bundle.getBoolean(FIELD_ARE_CURRENT_TRACKS_EXCLUDED, /* defaultValue= */ false));
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof BundlingExclusions)) {
        return false;
      }
      BundlingExclusions that = (BundlingExclusions) o;
      return isTimelineExcluded == that.isTimelineExcluded
          && areCurrentTracksExcluded == that.areCurrentTracksExcluded;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(isTimelineExcluded, areCurrentTracksExcluded);
    }
  }

  public static class Builder {

    @Nullable private PlaybackException playerError;
    private @Player.MediaItemTransitionReason int mediaItemTransitionReason;
    private SessionPositionInfo sessionPositionInfo;
    private PositionInfo oldPositionInfo;
    private PositionInfo newPositionInfo;
    private @Player.DiscontinuityReason int discontinuityReason;
    private PlaybackParameters playbackParameters;
    private @Player.RepeatMode int repeatMode;
    private boolean shuffleModeEnabled;
    private Timeline timeline;
    private @Player.TimelineChangeReason int timelineChangeReason;
    private VideoSize videoSize;
    private MediaMetadata playlistMetadata;
    private float volume;
    private AudioAttributes audioAttributes;
    private CueGroup cueGroup;
    private DeviceInfo deviceInfo;
    private int deviceVolume;
    private boolean deviceMuted;
    private boolean playWhenReady;
    private @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason;
    private boolean isPlaying;
    private boolean isLoading;
    private @PlaybackSuppressionReason int playbackSuppressionReason;
    private @State int playbackState;
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
      timelineChangeReason = playerInfo.timelineChangeReason;
      videoSize = playerInfo.videoSize;
      playlistMetadata = playerInfo.playlistMetadata;
      volume = playerInfo.volume;
      audioAttributes = playerInfo.audioAttributes;
      cueGroup = playerInfo.cueGroup;
      deviceInfo = playerInfo.deviceInfo;
      deviceVolume = playerInfo.deviceVolume;
      deviceMuted = playerInfo.deviceMuted;
      playWhenReady = playerInfo.playWhenReady;
      playWhenReadyChangeReason = playerInfo.playWhenReadyChangeReason;
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

    @CanIgnoreReturnValue
    public Builder setPlayerError(@Nullable PlaybackException playerError) {
      this.playerError = playerError;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setMediaItemTransitionReason(
        @Player.MediaItemTransitionReason int mediaItemTransitionReason) {
      this.mediaItemTransitionReason = mediaItemTransitionReason;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSessionPositionInfo(SessionPositionInfo sessionPositionInfo) {
      this.sessionPositionInfo = sessionPositionInfo;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setOldPositionInfo(PositionInfo oldPositionInfo) {
      this.oldPositionInfo = oldPositionInfo;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setNewPositionInfo(PositionInfo newPositionInfo) {
      this.newPositionInfo = newPositionInfo;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setDiscontinuityReason(@Player.DiscontinuityReason int discontinuityReason) {
      this.discontinuityReason = discontinuityReason;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPlaybackParameters(PlaybackParameters playbackParameters) {
      this.playbackParameters = playbackParameters;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setRepeatMode(@Player.RepeatMode int repeatMode) {
      this.repeatMode = repeatMode;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setShuffleModeEnabled(boolean shuffleModeEnabled) {
      this.shuffleModeEnabled = shuffleModeEnabled;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTimeline(Timeline timeline) {
      this.timeline = timeline;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTimelineChangeReason(@Player.TimelineChangeReason int timelineChangeReason) {
      this.timelineChangeReason = timelineChangeReason;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setVideoSize(VideoSize videoSize) {
      this.videoSize = videoSize;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPlaylistMetadata(MediaMetadata playlistMetadata) {
      this.playlistMetadata = playlistMetadata;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setVolume(@FloatRange(from = 0, to = 1) float volume) {
      this.volume = volume;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAudioAttributes(AudioAttributes audioAttributes) {
      this.audioAttributes = audioAttributes;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCues(CueGroup cueGroup) {
      this.cueGroup = cueGroup;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setDeviceInfo(DeviceInfo deviceInfo) {
      this.deviceInfo = deviceInfo;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setDeviceVolume(int deviceVolume) {
      this.deviceVolume = deviceVolume;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setDeviceMuted(boolean deviceMuted) {
      this.deviceMuted = deviceMuted;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPlayWhenReady(boolean playWhenReady) {
      this.playWhenReady = playWhenReady;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPlayWhenReadyChangeReason(
        @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
      this.playWhenReadyChangeReason = playWhenReadyChangeReason;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIsPlaying(boolean isPlaying) {
      this.isPlaying = isPlaying;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIsLoading(boolean isLoading) {
      this.isLoading = isLoading;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPlaybackSuppressionReason(
        @PlaybackSuppressionReason int playbackSuppressionReason) {
      this.playbackSuppressionReason = playbackSuppressionReason;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPlaybackState(@State int playbackState) {
      this.playbackState = playbackState;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setMediaMetadata(MediaMetadata mediaMetadata) {
      this.mediaMetadata = mediaMetadata;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSeekBackIncrement(long seekBackIncrementMs) {
      this.seekBackIncrementMs = seekBackIncrementMs;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSeekForwardIncrement(long seekForwardIncrementMs) {
      this.seekForwardIncrementMs = seekForwardIncrementMs;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setMaxSeekToPreviousPositionMs(long maxSeekToPreviousPositionMs) {
      this.maxSeekToPreviousPositionMs = maxSeekToPreviousPositionMs;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCurrentTracks(Tracks tracks) {
      currentTracks = tracks;
      return this;
    }

    @CanIgnoreReturnValue
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
          timelineChangeReason,
          playlistMetadata,
          volume,
          audioAttributes,
          cueGroup,
          deviceInfo,
          deviceVolume,
          deviceMuted,
          playWhenReady,
          playWhenReadyChangeReason,
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

  /** Default timeline change reason. */
  public static final int TIMELINE_CHANGE_REASON_DEFAULT = TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;

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
          TIMELINE_CHANGE_REASON_DEFAULT,
          MediaMetadata.EMPTY,
          /* volume= */ 1f,
          AudioAttributes.DEFAULT,
          CueGroup.EMPTY_TIME_ZERO,
          DeviceInfo.UNKNOWN,
          /* deviceVolume= */ 0,
          /* deviceMuted= */ false,
          /* playWhenReady= */ false,
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

  public final @Player.MediaItemTransitionReason int mediaItemTransitionReason;

  public final SessionPositionInfo sessionPositionInfo;

  public final PositionInfo oldPositionInfo;

  public final PositionInfo newPositionInfo;

  public final @Player.DiscontinuityReason int discontinuityReason;

  public final PlaybackParameters playbackParameters;

  public final @Player.RepeatMode int repeatMode;

  public final boolean shuffleModeEnabled;

  public final Timeline timeline;

  public final @Player.TimelineChangeReason int timelineChangeReason;

  public final VideoSize videoSize;

  public final MediaMetadata playlistMetadata;

  public final float volume;

  public final AudioAttributes audioAttributes;

  public final CueGroup cueGroup;

  public final DeviceInfo deviceInfo;

  public final int deviceVolume;

  public final boolean deviceMuted;

  public final boolean playWhenReady;

  public final int playWhenReadyChangeReason;

  public final boolean isPlaying;

  public final boolean isLoading;

  public final @Player.PlaybackSuppressionReason int playbackSuppressionReason;

  public final @Player.State int playbackState;

  public final MediaMetadata mediaMetadata;

  public final long seekBackIncrementMs;

  public final long seekForwardIncrementMs;

  public final long maxSeekToPreviousPositionMs;

  public final Tracks currentTracks;

  public final TrackSelectionParameters trackSelectionParameters;

  @CheckResult
  public PlayerInfo copyWithPlayWhenReady(
      boolean playWhenReady,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason,
      @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
    return new Builder(this)
        .setPlayWhenReady(playWhenReady)
        .setPlayWhenReadyChangeReason(playWhenReadyChangeReason)
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
      Timeline timeline,
      SessionPositionInfo sessionPositionInfo,
      @Player.TimelineChangeReason int timelineChangeReason) {
    return new Builder(this)
        .setTimeline(timeline)
        .setSessionPositionInfo(sessionPositionInfo)
        .setTimelineChangeReason(timelineChangeReason)
        .build();
  }

  @CheckResult
  public PlayerInfo copyWithTimelineAndMediaItemIndex(
      Timeline timeline,
      int mediaItemIndex,
      @Player.TimelineChangeReason int timelineChangeReason) {
    return new Builder(this)
        .setTimeline(timeline)
        .setTimelineChangeReason(timelineChangeReason)
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
                sessionPositionInfo.eventTimeMs,
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
      @Player.TimelineChangeReason int timelineChangeReason,
      MediaMetadata playlistMetadata,
      float volume,
      AudioAttributes audioAttributes,
      CueGroup cueGroup,
      DeviceInfo deviceInfo,
      int deviceVolume,
      boolean deviceMuted,
      boolean playWhenReady,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason,
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
    this.timelineChangeReason = timelineChangeReason;
    this.playlistMetadata = playlistMetadata;
    this.volume = volume;
    this.audioAttributes = audioAttributes;
    this.cueGroup = cueGroup;
    this.deviceInfo = deviceInfo;
    this.deviceVolume = deviceVolume;
    this.deviceMuted = deviceMuted;
    this.playWhenReady = playWhenReady;
    this.playWhenReadyChangeReason = playWhenReadyChangeReason;
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

  private static final String FIELD_PLAYBACK_PARAMETERS = Util.intToStringMaxRadix(1);
  private static final String FIELD_REPEAT_MODE = Util.intToStringMaxRadix(2);
  private static final String FIELD_SHUFFLE_MODE_ENABLED = Util.intToStringMaxRadix(3);
  private static final String FIELD_TIMELINE = Util.intToStringMaxRadix(4);
  private static final String FIELD_VIDEO_SIZE = Util.intToStringMaxRadix(5);
  private static final String FIELD_PLAYLIST_METADATA = Util.intToStringMaxRadix(6);
  private static final String FIELD_VOLUME = Util.intToStringMaxRadix(7);
  private static final String FIELD_AUDIO_ATTRIBUTES = Util.intToStringMaxRadix(8);
  private static final String FIELD_DEVICE_INFO = Util.intToStringMaxRadix(9);
  private static final String FIELD_DEVICE_VOLUME = Util.intToStringMaxRadix(10);
  private static final String FIELD_DEVICE_MUTED = Util.intToStringMaxRadix(11);
  private static final String FIELD_PLAY_WHEN_READY = Util.intToStringMaxRadix(12);
  private static final String FIELD_PLAY_WHEN_READY_CHANGE_REASON = Util.intToStringMaxRadix(13);
  private static final String FIELD_PLAYBACK_SUPPRESSION_REASON = Util.intToStringMaxRadix(14);
  private static final String FIELD_PLAYBACK_STATE = Util.intToStringMaxRadix(15);
  private static final String FIELD_IS_PLAYING = Util.intToStringMaxRadix(16);
  private static final String FIELD_IS_LOADING = Util.intToStringMaxRadix(17);
  private static final String FIELD_PLAYBACK_ERROR = Util.intToStringMaxRadix(18);
  @VisibleForTesting static final String FIELD_SESSION_POSITION_INFO = Util.intToStringMaxRadix(19);
  private static final String FIELD_MEDIA_ITEM_TRANSITION_REASON = Util.intToStringMaxRadix(20);
  @VisibleForTesting static final String FIELD_OLD_POSITION_INFO = Util.intToStringMaxRadix(21);
  @VisibleForTesting static final String FIELD_NEW_POSITION_INFO = Util.intToStringMaxRadix(22);
  private static final String FIELD_DISCONTINUITY_REASON = Util.intToStringMaxRadix(23);
  private static final String FIELD_CUE_GROUP = Util.intToStringMaxRadix(24);
  private static final String FIELD_MEDIA_METADATA = Util.intToStringMaxRadix(25);
  private static final String FIELD_SEEK_BACK_INCREMENT_MS = Util.intToStringMaxRadix(26);
  private static final String FIELD_SEEK_FORWARD_INCREMENT_MS = Util.intToStringMaxRadix(27);
  private static final String FIELD_MAX_SEEK_TO_PREVIOUS_POSITION_MS = Util.intToStringMaxRadix(28);
  private static final String FIELD_TRACK_SELECTION_PARAMETERS = Util.intToStringMaxRadix(29);
  private static final String FIELD_CURRENT_TRACKS = Util.intToStringMaxRadix(30);
  private static final String FIELD_TIMELINE_CHANGE_REASON = Util.intToStringMaxRadix(31);
  private static final String FIELD_IN_PROCESS_BINDER = Util.intToStringMaxRadix(32);

  // Next field key = 33

  /**
   * Returns a copy of this player info, filtered by the specified available commands.
   *
   * <p>The filtered fields are reset to their default values.
   *
   * @param availableCommands The available {@link Player.Commands} used to filter values.
   * @param excludeTimeline Whether to filter the {@link #timeline} even if {@link
   *     Player#COMMAND_GET_TIMELINE} is available.
   * @param excludeTracks Whether to filter the {@link #currentTracks} even if {@link
   *     Player#COMMAND_GET_TRACKS} is available.
   * @return The filtered player info.
   */
  public PlayerInfo filterByAvailableCommands(
      Player.Commands availableCommands, boolean excludeTimeline, boolean excludeTracks) {
    PlayerInfo.Builder builder = new Builder(this);
    boolean canAccessCurrentMediaItem =
        availableCommands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM);
    boolean canAccessTimeline = availableCommands.contains(Player.COMMAND_GET_TIMELINE);
    builder.setSessionPositionInfo(
        sessionPositionInfo.filterByAvailableCommands(
            canAccessCurrentMediaItem, canAccessTimeline));
    builder.setOldPositionInfo(
        oldPositionInfo.filterByAvailableCommands(canAccessCurrentMediaItem, canAccessTimeline));
    builder.setNewPositionInfo(
        newPositionInfo.filterByAvailableCommands(canAccessCurrentMediaItem, canAccessTimeline));
    if (!canAccessTimeline && canAccessCurrentMediaItem && !timeline.isEmpty()) {
      builder.setTimeline(
          timeline.copyWithSingleWindow(sessionPositionInfo.positionInfo.mediaItemIndex));
    } else if (excludeTimeline || !canAccessTimeline) {
      builder.setTimeline(Timeline.EMPTY);
    }
    if (!availableCommands.contains(Player.COMMAND_GET_METADATA)) {
      builder.setPlaylistMetadata(MediaMetadata.EMPTY);
    }
    if (!availableCommands.contains(Player.COMMAND_GET_VOLUME)) {
      builder.setVolume(1);
    }
    if (!availableCommands.contains(Player.COMMAND_GET_AUDIO_ATTRIBUTES)) {
      builder.setAudioAttributes(AudioAttributes.DEFAULT);
    }
    if (!availableCommands.contains(Player.COMMAND_GET_TEXT)) {
      builder.setCues(CueGroup.EMPTY_TIME_ZERO);
    }
    if (!availableCommands.contains(Player.COMMAND_GET_DEVICE_VOLUME)) {
      builder.setDeviceVolume(0).setDeviceMuted(false);
    }
    if (!availableCommands.contains(Player.COMMAND_GET_METADATA)) {
      builder.setMediaMetadata(MediaMetadata.EMPTY);
    }
    if (excludeTracks || !availableCommands.contains(Player.COMMAND_GET_TRACKS)) {
      builder.setCurrentTracks(Tracks.EMPTY);
    }
    return builder.build();
  }

  /**
   * Returns a {@link Bundle} that stores a direct object reference to this class for in-process
   * sharing.
   */
  public Bundle toBundleInProcess() {
    Bundle bundle = new Bundle();
    BundleUtil.putBinder(bundle, FIELD_IN_PROCESS_BINDER, new InProcessBinder());
    return bundle;
  }

  @Override
  public Bundle toBundle() {
    return toBundleForRemoteProcess(Integer.MAX_VALUE);
  }

  public Bundle toBundleForRemoteProcess(int controllerInterfaceVersion) {
    Bundle bundle = new Bundle();
    if (playerError != null) {
      bundle.putBundle(FIELD_PLAYBACK_ERROR, playerError.toBundle());
    }
    if (mediaItemTransitionReason != MEDIA_ITEM_TRANSITION_REASON_DEFAULT) {
      bundle.putInt(FIELD_MEDIA_ITEM_TRANSITION_REASON, mediaItemTransitionReason);
    }
    if (controllerInterfaceVersion < 3
        || !sessionPositionInfo.equals(SessionPositionInfo.DEFAULT)) {
      bundle.putBundle(
          FIELD_SESSION_POSITION_INFO, sessionPositionInfo.toBundle(controllerInterfaceVersion));
    }
    if (controllerInterfaceVersion < 3
        || !SessionPositionInfo.DEFAULT_POSITION_INFO.equalsForBundling(oldPositionInfo)) {
      bundle.putBundle(
          FIELD_OLD_POSITION_INFO, oldPositionInfo.toBundle(controllerInterfaceVersion));
    }
    if (controllerInterfaceVersion < 3
        || !SessionPositionInfo.DEFAULT_POSITION_INFO.equalsForBundling(newPositionInfo)) {
      bundle.putBundle(
          FIELD_NEW_POSITION_INFO, newPositionInfo.toBundle(controllerInterfaceVersion));
    }
    if (discontinuityReason != DISCONTINUITY_REASON_DEFAULT) {
      bundle.putInt(FIELD_DISCONTINUITY_REASON, discontinuityReason);
    }
    if (!playbackParameters.equals(PlaybackParameters.DEFAULT)) {
      bundle.putBundle(FIELD_PLAYBACK_PARAMETERS, playbackParameters.toBundle());
    }
    if (repeatMode != Player.REPEAT_MODE_OFF) {
      bundle.putInt(FIELD_REPEAT_MODE, repeatMode);
    }
    if (shuffleModeEnabled) {
      bundle.putBoolean(FIELD_SHUFFLE_MODE_ENABLED, shuffleModeEnabled);
    }
    if (!timeline.equals(Timeline.EMPTY)) {
      bundle.putBundle(FIELD_TIMELINE, timeline.toBundle());
    }
    if (timelineChangeReason != TIMELINE_CHANGE_REASON_DEFAULT) {
      bundle.putInt(FIELD_TIMELINE_CHANGE_REASON, timelineChangeReason);
    }
    if (!videoSize.equals(VideoSize.UNKNOWN)) {
      bundle.putBundle(FIELD_VIDEO_SIZE, videoSize.toBundle());
    }
    if (!playlistMetadata.equals(MediaMetadata.EMPTY)) {
      bundle.putBundle(FIELD_PLAYLIST_METADATA, playlistMetadata.toBundle());
    }
    if (volume != 1) {
      bundle.putFloat(FIELD_VOLUME, volume);
    }
    if (!audioAttributes.equals(AudioAttributes.DEFAULT)) {
      bundle.putBundle(FIELD_AUDIO_ATTRIBUTES, audioAttributes.toBundle());
    }
    if (!cueGroup.equals(CueGroup.EMPTY_TIME_ZERO)) {
      bundle.putBundle(FIELD_CUE_GROUP, cueGroup.toBundle());
    }
    if (!deviceInfo.equals(DeviceInfo.UNKNOWN)) {
      bundle.putBundle(FIELD_DEVICE_INFO, deviceInfo.toBundle());
    }
    if (deviceVolume != 0) {
      bundle.putInt(FIELD_DEVICE_VOLUME, deviceVolume);
    }
    if (deviceMuted) {
      bundle.putBoolean(FIELD_DEVICE_MUTED, deviceMuted);
    }
    if (playWhenReady) {
      bundle.putBoolean(FIELD_PLAY_WHEN_READY, playWhenReady);
    }
    if (playWhenReadyChangeReason != PLAY_WHEN_READY_CHANGE_REASON_DEFAULT) {
      bundle.putInt(FIELD_PLAY_WHEN_READY_CHANGE_REASON, playWhenReadyChangeReason);
    }
    if (playbackSuppressionReason != PLAYBACK_SUPPRESSION_REASON_NONE) {
      bundle.putInt(FIELD_PLAYBACK_SUPPRESSION_REASON, playbackSuppressionReason);
    }
    if (playbackState != STATE_IDLE) {
      bundle.putInt(FIELD_PLAYBACK_STATE, playbackState);
    }
    if (isPlaying) {
      bundle.putBoolean(FIELD_IS_PLAYING, isPlaying);
    }
    if (isLoading) {
      bundle.putBoolean(FIELD_IS_LOADING, isLoading);
    }
    if (!mediaMetadata.equals(MediaMetadata.EMPTY)) {
      bundle.putBundle(FIELD_MEDIA_METADATA, mediaMetadata.toBundle());
    }
    if (seekBackIncrementMs != 0) {
      bundle.putLong(FIELD_SEEK_BACK_INCREMENT_MS, seekBackIncrementMs);
    }
    if (seekForwardIncrementMs != 0) {
      bundle.putLong(FIELD_SEEK_FORWARD_INCREMENT_MS, seekForwardIncrementMs);
    }
    if (maxSeekToPreviousPositionMs != 0) {
      bundle.putLong(FIELD_MAX_SEEK_TO_PREVIOUS_POSITION_MS, maxSeekToPreviousPositionMs);
    }
    if (!currentTracks.equals(Tracks.EMPTY)) {
      bundle.putBundle(FIELD_CURRENT_TRACKS, currentTracks.toBundle());
    }
    if (!trackSelectionParameters.equals(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT)) {
      bundle.putBundle(FIELD_TRACK_SELECTION_PARAMETERS, trackSelectionParameters.toBundle());
    }
    return bundle;
  }

  /**
   * Object that can restore {@link PlayerInfo} from a {@link Bundle}.
   *
   * @deprecated Use {@link #fromBundle} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation") // Deprecated instance of deprecated class
  public static final Creator<PlayerInfo> CREATOR = PlayerInfo::fromBundle;

  /** Restores a {@code PlayerInfo} from a {@link Bundle}. */
  public static PlayerInfo fromBundle(Bundle bundle) {
    @Nullable IBinder inProcessBinder = BundleUtil.getBinder(bundle, FIELD_IN_PROCESS_BINDER);
    if (inProcessBinder instanceof InProcessBinder) {
      return ((InProcessBinder) inProcessBinder).getPlayerInfo();
    }
    @Nullable Bundle playerErrorBundle = bundle.getBundle(FIELD_PLAYBACK_ERROR);
    @Nullable
    PlaybackException playerError =
        playerErrorBundle == null ? null : PlaybackException.fromBundle(playerErrorBundle);
    int mediaItemTransitionReason =
        bundle.getInt(FIELD_MEDIA_ITEM_TRANSITION_REASON, MEDIA_ITEM_TRANSITION_REASON_DEFAULT);
    @Nullable Bundle sessionPositionInfoBundle = bundle.getBundle(FIELD_SESSION_POSITION_INFO);
    SessionPositionInfo sessionPositionInfo =
        sessionPositionInfoBundle == null
            ? SessionPositionInfo.DEFAULT
            : SessionPositionInfo.fromBundle(sessionPositionInfoBundle);
    @Nullable Bundle oldPositionInfoBundle = bundle.getBundle(FIELD_OLD_POSITION_INFO);
    PositionInfo oldPositionInfo =
        oldPositionInfoBundle == null
            ? SessionPositionInfo.DEFAULT_POSITION_INFO
            : PositionInfo.fromBundle(oldPositionInfoBundle);
    @Nullable Bundle newPositionInfoBundle = bundle.getBundle(FIELD_NEW_POSITION_INFO);
    PositionInfo newPositionInfo =
        newPositionInfoBundle == null
            ? SessionPositionInfo.DEFAULT_POSITION_INFO
            : PositionInfo.fromBundle(newPositionInfoBundle);
    int discontinuityReason =
        bundle.getInt(FIELD_DISCONTINUITY_REASON, DISCONTINUITY_REASON_DEFAULT);
    @Nullable Bundle playbackParametersBundle = bundle.getBundle(FIELD_PLAYBACK_PARAMETERS);
    PlaybackParameters playbackParameters =
        playbackParametersBundle == null
            ? PlaybackParameters.DEFAULT
            : PlaybackParameters.fromBundle(playbackParametersBundle);
    @Player.RepeatMode
    int repeatMode = bundle.getInt(FIELD_REPEAT_MODE, /* defaultValue= */ Player.REPEAT_MODE_OFF);
    boolean shuffleModeEnabled =
        bundle.getBoolean(FIELD_SHUFFLE_MODE_ENABLED, /* defaultValue= */ false);
    @Nullable Bundle timelineBundle = bundle.getBundle(FIELD_TIMELINE);
    Timeline timeline =
        timelineBundle == null ? Timeline.EMPTY : Timeline.fromBundle(timelineBundle);
    int timelineChangeReason =
        bundle.getInt(
            FIELD_TIMELINE_CHANGE_REASON, /* defaultValue= */ TIMELINE_CHANGE_REASON_DEFAULT);
    @Nullable Bundle videoSizeBundle = bundle.getBundle(FIELD_VIDEO_SIZE);
    VideoSize videoSize =
        videoSizeBundle == null ? VideoSize.UNKNOWN : VideoSize.fromBundle(videoSizeBundle);
    @Nullable Bundle playlistMetadataBundle = bundle.getBundle(FIELD_PLAYLIST_METADATA);
    MediaMetadata playlistMetadata =
        playlistMetadataBundle == null
            ? MediaMetadata.EMPTY
            : MediaMetadata.fromBundle(playlistMetadataBundle);
    float volume = bundle.getFloat(FIELD_VOLUME, /* defaultValue= */ 1);
    @Nullable Bundle audioAttributesBundle = bundle.getBundle(FIELD_AUDIO_ATTRIBUTES);
    AudioAttributes audioAttributes =
        audioAttributesBundle == null
            ? AudioAttributes.DEFAULT
            : AudioAttributes.fromBundle(audioAttributesBundle);
    @Nullable Bundle cueGroupBundle = bundle.getBundle(FIELD_CUE_GROUP);
    CueGroup cueGroup =
        cueGroupBundle == null ? CueGroup.EMPTY_TIME_ZERO : CueGroup.fromBundle(cueGroupBundle);
    @Nullable Bundle deviceInfoBundle = bundle.getBundle(FIELD_DEVICE_INFO);
    DeviceInfo deviceInfo =
        deviceInfoBundle == null ? DeviceInfo.UNKNOWN : DeviceInfo.fromBundle(deviceInfoBundle);
    int deviceVolume = bundle.getInt(FIELD_DEVICE_VOLUME, /* defaultValue= */ 0);
    boolean deviceMuted = bundle.getBoolean(FIELD_DEVICE_MUTED, /* defaultValue= */ false);
    boolean playWhenReady = bundle.getBoolean(FIELD_PLAY_WHEN_READY, /* defaultValue= */ false);
    int playWhenReadyChangeReason =
        bundle.getInt(
            FIELD_PLAY_WHEN_READY_CHANGE_REASON,
            /* defaultValue= */ PLAY_WHEN_READY_CHANGE_REASON_DEFAULT);
    @Player.PlaybackSuppressionReason
    int playbackSuppressionReason =
        bundle.getInt(
            FIELD_PLAYBACK_SUPPRESSION_REASON,
            /* defaultValue= */ PLAYBACK_SUPPRESSION_REASON_NONE);
    @Player.State
    int playbackState = bundle.getInt(FIELD_PLAYBACK_STATE, /* defaultValue= */ STATE_IDLE);
    boolean isPlaying = bundle.getBoolean(FIELD_IS_PLAYING, /* defaultValue= */ false);
    boolean isLoading = bundle.getBoolean(FIELD_IS_LOADING, /* defaultValue= */ false);
    @Nullable Bundle mediaMetadataBundle = bundle.getBundle(FIELD_MEDIA_METADATA);
    MediaMetadata mediaMetadata =
        mediaMetadataBundle == null
            ? MediaMetadata.EMPTY
            : MediaMetadata.fromBundle(mediaMetadataBundle);
    long seekBackIncrementMs = bundle.getLong(FIELD_SEEK_BACK_INCREMENT_MS, /* defaultValue= */ 0);
    long seekForwardIncrementMs =
        bundle.getLong(FIELD_SEEK_FORWARD_INCREMENT_MS, /* defaultValue= */ 0);
    long maxSeekToPreviousPosition =
        bundle.getLong(FIELD_MAX_SEEK_TO_PREVIOUS_POSITION_MS, /* defaultValue= */ 0);
    Bundle currentTracksBundle = bundle.getBundle(FIELD_CURRENT_TRACKS);
    Tracks currentTracks =
        currentTracksBundle == null ? Tracks.EMPTY : Tracks.fromBundle(currentTracksBundle);
    @Nullable
    Bundle trackSelectionParametersBundle = bundle.getBundle(FIELD_TRACK_SELECTION_PARAMETERS);
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
        timelineChangeReason,
        playlistMetadata,
        volume,
        audioAttributes,
        cueGroup,
        deviceInfo,
        deviceVolume,
        deviceMuted,
        playWhenReady,
        playWhenReadyChangeReason,
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

  private final class InProcessBinder extends Binder {
    public PlayerInfo getPlayerInfo() {
      return PlayerInfo.this;
    }
  }
}
