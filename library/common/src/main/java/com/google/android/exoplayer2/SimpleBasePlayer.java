/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static com.google.android.exoplayer2.util.Util.usToMs;
import static java.lang.Math.max;

import android.os.Looper;
import android.os.SystemClock;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A base implementation for {@link Player} that reduces the number of methods to implement to a
 * minimum.
 *
 * <p>Implementation notes:
 *
 * <ul>
 *   <li>Subclasses must override {@link #getState()} to populate the current player state on
 *       request.
 *   <li>The {@link State} should set the {@linkplain State.Builder#setAvailableCommands available
 *       commands} to indicate which {@link Player} methods are supported.
 *   <li>All setter-like player methods (for example, {@link #setPlayWhenReady}) forward to
 *       overridable methods (for example, {@link #handleSetPlayWhenReady}) that can be used to
 *       handle these requests. These methods return a {@link ListenableFuture} to indicate when the
 *       request has been handled and is fully reflected in the values returned from {@link
 *       #getState}. This class will automatically request a state update once the request is done.
 *       If the state changes can be handled synchronously, these methods can return Guava's {@link
 *       Futures#immediateVoidFuture()}.
 *   <li>Subclasses can manually trigger state updates with {@link #invalidateState}, for example if
 *       something changes independent of {@link Player} method calls.
 * </ul>
 *
 * This base class handles various aspects of the player implementation to simplify the subclass:
 *
 * <ul>
 *   <li>The {@link State} can only be created with allowed combinations of state values, avoiding
 *       any invalid player states.
 *   <li>Only functionality that is declared as {@linkplain Player.Command available} needs to be
 *       implemented. Other methods are automatically ignored.
 *   <li>Listener handling and informing listeners of state changes is handled automatically.
 *   <li>The base class provides a framework for asynchronous handling of method calls. It changes
 *       the visible playback state immediately to the most likely outcome to ensure the
 *       user-visible state changes look like synchronous operations. The state is then updated
 *       again once the asynchronous method calls have been fully handled.
 * </ul>
 */
public abstract class SimpleBasePlayer extends BasePlayer {

  /** An immutable state description of the player. */
  protected static final class State {

    /** A builder for {@link State} objects. */
    public static final class Builder {

      private Commands availableCommands;
      private boolean playWhenReady;
      private @PlayWhenReadyChangeReason int playWhenReadyChangeReason;
      private @Player.State int playbackState;
      private @PlaybackSuppressionReason int playbackSuppressionReason;
      @Nullable private PlaybackException playerError;
      private @RepeatMode int repeatMode;
      private boolean shuffleModeEnabled;
      private boolean isLoading;
      private long seekBackIncrementMs;
      private long seekForwardIncrementMs;
      private long maxSeekToPreviousPositionMs;
      private PlaybackParameters playbackParameters;
      private TrackSelectionParameters trackSelectionParameters;
      private AudioAttributes audioAttributes;
      private float volume;
      private VideoSize videoSize;
      private CueGroup currentCues;
      private DeviceInfo deviceInfo;
      private int deviceVolume;
      private boolean isDeviceMuted;
      private int audioSessionId;
      private boolean skipSilenceEnabled;
      private Size surfaceSize;
      private boolean newlyRenderedFirstFrame;
      private Metadata timedMetadata;
      private ImmutableList<PlaylistItem> playlistItems;
      private Timeline timeline;
      private MediaMetadata playlistMetadata;
      private int currentMediaItemIndex;
      private int currentPeriodIndex;
      private int currentAdGroupIndex;
      private int currentAdIndexInAdGroup;
      private long contentPositionMs;
      private PositionSupplier contentPositionMsSupplier;
      private long adPositionMs;
      private PositionSupplier adPositionMsSupplier;
      private PositionSupplier contentBufferedPositionMsSupplier;
      private PositionSupplier adBufferedPositionMsSupplier;
      private PositionSupplier totalBufferedDurationMsSupplier;
      private boolean hasPositionDiscontinuity;
      private @Player.DiscontinuityReason int positionDiscontinuityReason;
      private long discontinuityPositionMs;

      /** Creates the builder. */
      public Builder() {
        availableCommands = Commands.EMPTY;
        playWhenReady = false;
        playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
        playbackState = Player.STATE_IDLE;
        playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE;
        playerError = null;
        repeatMode = Player.REPEAT_MODE_OFF;
        shuffleModeEnabled = false;
        isLoading = false;
        seekBackIncrementMs = C.DEFAULT_SEEK_BACK_INCREMENT_MS;
        seekForwardIncrementMs = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS;
        maxSeekToPreviousPositionMs = C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS;
        playbackParameters = PlaybackParameters.DEFAULT;
        trackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
        audioAttributes = AudioAttributes.DEFAULT;
        volume = 1f;
        videoSize = VideoSize.UNKNOWN;
        currentCues = CueGroup.EMPTY_TIME_ZERO;
        deviceInfo = DeviceInfo.UNKNOWN;
        deviceVolume = 0;
        isDeviceMuted = false;
        audioSessionId = C.AUDIO_SESSION_ID_UNSET;
        skipSilenceEnabled = false;
        surfaceSize = Size.UNKNOWN;
        newlyRenderedFirstFrame = false;
        timedMetadata = new Metadata(/* presentationTimeUs= */ C.TIME_UNSET);
        playlistItems = ImmutableList.of();
        timeline = Timeline.EMPTY;
        playlistMetadata = MediaMetadata.EMPTY;
        currentMediaItemIndex = 0;
        currentPeriodIndex = C.INDEX_UNSET;
        currentAdGroupIndex = C.INDEX_UNSET;
        currentAdIndexInAdGroup = C.INDEX_UNSET;
        contentPositionMs = C.TIME_UNSET;
        contentPositionMsSupplier = PositionSupplier.ZERO;
        adPositionMs = C.TIME_UNSET;
        adPositionMsSupplier = PositionSupplier.ZERO;
        contentBufferedPositionMsSupplier = PositionSupplier.ZERO;
        adBufferedPositionMsSupplier = PositionSupplier.ZERO;
        totalBufferedDurationMsSupplier = PositionSupplier.ZERO;
        hasPositionDiscontinuity = false;
        positionDiscontinuityReason = Player.DISCONTINUITY_REASON_INTERNAL;
        discontinuityPositionMs = 0;
      }

      private Builder(State state) {
        this.availableCommands = state.availableCommands;
        this.playWhenReady = state.playWhenReady;
        this.playWhenReadyChangeReason = state.playWhenReadyChangeReason;
        this.playbackState = state.playbackState;
        this.playbackSuppressionReason = state.playbackSuppressionReason;
        this.playerError = state.playerError;
        this.repeatMode = state.repeatMode;
        this.shuffleModeEnabled = state.shuffleModeEnabled;
        this.isLoading = state.isLoading;
        this.seekBackIncrementMs = state.seekBackIncrementMs;
        this.seekForwardIncrementMs = state.seekForwardIncrementMs;
        this.maxSeekToPreviousPositionMs = state.maxSeekToPreviousPositionMs;
        this.playbackParameters = state.playbackParameters;
        this.trackSelectionParameters = state.trackSelectionParameters;
        this.audioAttributes = state.audioAttributes;
        this.volume = state.volume;
        this.videoSize = state.videoSize;
        this.currentCues = state.currentCues;
        this.deviceInfo = state.deviceInfo;
        this.deviceVolume = state.deviceVolume;
        this.isDeviceMuted = state.isDeviceMuted;
        this.audioSessionId = state.audioSessionId;
        this.skipSilenceEnabled = state.skipSilenceEnabled;
        this.surfaceSize = state.surfaceSize;
        this.newlyRenderedFirstFrame = state.newlyRenderedFirstFrame;
        this.timedMetadata = state.timedMetadata;
        this.playlistItems = state.playlistItems;
        this.timeline = state.timeline;
        this.playlistMetadata = state.playlistMetadata;
        this.currentMediaItemIndex = state.currentMediaItemIndex;
        this.currentPeriodIndex = state.currentPeriodIndex;
        this.currentAdGroupIndex = state.currentAdGroupIndex;
        this.currentAdIndexInAdGroup = state.currentAdIndexInAdGroup;
        this.contentPositionMs = C.TIME_UNSET;
        this.contentPositionMsSupplier = state.contentPositionMsSupplier;
        this.adPositionMs = C.TIME_UNSET;
        this.adPositionMsSupplier = state.adPositionMsSupplier;
        this.contentBufferedPositionMsSupplier = state.contentBufferedPositionMsSupplier;
        this.adBufferedPositionMsSupplier = state.adBufferedPositionMsSupplier;
        this.totalBufferedDurationMsSupplier = state.totalBufferedDurationMsSupplier;
        this.hasPositionDiscontinuity = state.hasPositionDiscontinuity;
        this.positionDiscontinuityReason = state.positionDiscontinuityReason;
        this.discontinuityPositionMs = state.discontinuityPositionMs;
      }

      /**
       * Sets the available {@link Commands}.
       *
       * @param availableCommands The available {@link Commands}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAvailableCommands(Commands availableCommands) {
        this.availableCommands = availableCommands;
        return this;
      }

      /**
       * Sets whether playback should proceed when ready and not suppressed.
       *
       * @param playWhenReady Whether playback should proceed when ready and not suppressed.
       * @param playWhenReadyChangeReason The {@linkplain PlayWhenReadyChangeReason reason} for
       *     changing the value.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlayWhenReady(
          boolean playWhenReady, @PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
        this.playWhenReady = playWhenReady;
        this.playWhenReadyChangeReason = playWhenReadyChangeReason;
        return this;
      }

      /**
       * Sets the {@linkplain Player.State state} of the player.
       *
       * <p>If the {@linkplain #setPlaylist playlist} is empty, the state must be either {@link
       * Player#STATE_IDLE} or {@link Player#STATE_ENDED}.
       *
       * @param playbackState The {@linkplain Player.State state} of the player.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackState(@Player.State int playbackState) {
        this.playbackState = playbackState;
        return this;
      }

      /**
       * Sets the reason why playback is suppressed even if {@link #getPlayWhenReady()} is true.
       *
       * @param playbackSuppressionReason The {@link Player.PlaybackSuppressionReason} why playback
       *     is suppressed even if {@link #getPlayWhenReady()} is true.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackSuppressionReason(
          @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
        this.playbackSuppressionReason = playbackSuppressionReason;
        return this;
      }

      /**
       * Sets last error that caused playback to fail, or null if there was no error.
       *
       * <p>The {@linkplain #setPlaybackState playback state} must be set to {@link
       * Player#STATE_IDLE} while an error is set.
       *
       * @param playerError The last error that caused playback to fail, or null if there was no
       *     error.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlayerError(@Nullable PlaybackException playerError) {
        this.playerError = playerError;
        return this;
      }

      /**
       * Sets the {@link RepeatMode} used for playback.
       *
       * @param repeatMode The {@link RepeatMode} used for playback.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setRepeatMode(@Player.RepeatMode int repeatMode) {
        this.repeatMode = repeatMode;
        return this;
      }

      /**
       * Sets whether shuffling of media items is enabled.
       *
       * @param shuffleModeEnabled Whether shuffling of media items is enabled.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setShuffleModeEnabled(boolean shuffleModeEnabled) {
        this.shuffleModeEnabled = shuffleModeEnabled;
        return this;
      }

      /**
       * Sets whether the player is currently loading its source.
       *
       * <p>The player can not be marked as loading if the {@linkplain #setPlaybackState state} is
       * {@link Player#STATE_IDLE} or {@link Player#STATE_ENDED}.
       *
       * @param isLoading Whether the player is currently loading its source.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsLoading(boolean isLoading) {
        this.isLoading = isLoading;
        return this;
      }

      /**
       * Sets the {@link Player#seekBack()} increment in milliseconds.
       *
       * @param seekBackIncrementMs The {@link Player#seekBack()} increment in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setSeekBackIncrementMs(long seekBackIncrementMs) {
        this.seekBackIncrementMs = seekBackIncrementMs;
        return this;
      }

      /**
       * Sets the {@link Player#seekForward()} increment in milliseconds.
       *
       * @param seekForwardIncrementMs The {@link Player#seekForward()} increment in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setSeekForwardIncrementMs(long seekForwardIncrementMs) {
        this.seekForwardIncrementMs = seekForwardIncrementMs;
        return this;
      }

      /**
       * Sets the maximum position for which {@link #seekToPrevious()} seeks to the previous item,
       * in milliseconds.
       *
       * @param maxSeekToPreviousPositionMs The maximum position for which {@link #seekToPrevious()}
       *     seeks to the previous item, in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setMaxSeekToPreviousPositionMs(long maxSeekToPreviousPositionMs) {
        this.maxSeekToPreviousPositionMs = maxSeekToPreviousPositionMs;
        return this;
      }

      /**
       * Sets the currently active {@link PlaybackParameters}.
       *
       * @param playbackParameters The currently active {@link PlaybackParameters}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        return this;
      }

      /**
       * Sets the currently active {@link TrackSelectionParameters}.
       *
       * @param trackSelectionParameters The currently active {@link TrackSelectionParameters}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setTrackSelectionParameters(
          TrackSelectionParameters trackSelectionParameters) {
        this.trackSelectionParameters = trackSelectionParameters;
        return this;
      }

      /**
       * Sets the current {@link AudioAttributes}.
       *
       * @param audioAttributes The current {@link AudioAttributes}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAudioAttributes(AudioAttributes audioAttributes) {
        this.audioAttributes = audioAttributes;
        return this;
      }

      /**
       * Sets the current audio volume, with 0 being silence and 1 being unity gain (signal
       * unchanged).
       *
       * @param volume The current audio volume, with 0 being silence and 1 being unity gain (signal
       *     unchanged).
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setVolume(@FloatRange(from = 0, to = 1.0) float volume) {
        checkArgument(volume >= 0.0f && volume <= 1.0f);
        this.volume = volume;
        return this;
      }

      /**
       * Sets the current video size.
       *
       * @param videoSize The current video size.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setVideoSize(VideoSize videoSize) {
        this.videoSize = videoSize;
        return this;
      }

      /**
       * Sets the current {@linkplain CueGroup cues}.
       *
       * @param currentCues The current {@linkplain CueGroup cues}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setCurrentCues(CueGroup currentCues) {
        this.currentCues = currentCues;
        return this;
      }

      /**
       * Sets the {@link DeviceInfo}.
       *
       * @param deviceInfo The {@link DeviceInfo}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDeviceInfo(DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
        return this;
      }

      /**
       * Sets the current device volume.
       *
       * @param deviceVolume The current device volume.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDeviceVolume(@IntRange(from = 0) int deviceVolume) {
        checkArgument(deviceVolume >= 0);
        this.deviceVolume = deviceVolume;
        return this;
      }

      /**
       * Sets whether the device is muted.
       *
       * @param isDeviceMuted Whether the device is muted.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsDeviceMuted(boolean isDeviceMuted) {
        this.isDeviceMuted = isDeviceMuted;
        return this;
      }

      /**
       * Sets the audio session id.
       *
       * @param audioSessionId The audio session id.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAudioSessionId(int audioSessionId) {
        this.audioSessionId = audioSessionId;
        return this;
      }

      /**
       * Sets whether skipping silences in the audio stream is enabled.
       *
       * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setSkipSilenceEnabled(boolean skipSilenceEnabled) {
        this.skipSilenceEnabled = skipSilenceEnabled;
        return this;
      }

      /**
       * Sets the size of the surface onto which the video is being rendered.
       *
       * @param surfaceSize The surface size. Dimensions may be {@link C#LENGTH_UNSET} if unknown,
       *     or 0 if the video is not rendered onto a surface.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setSurfaceSize(Size surfaceSize) {
        this.surfaceSize = surfaceSize;
        return this;
      }

      /**
       * Sets whether a frame has been rendered for the first time since setting the surface, a
       * rendering reset, or since the stream being rendered was changed.
       *
       * <p>Note: As this will trigger a {@link Listener#onRenderedFirstFrame()} event, the flag
       * should only be set for the first {@link State} update after the first frame was rendered.
       *
       * @param newlyRenderedFirstFrame Whether the first frame was newly rendered.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setNewlyRenderedFirstFrame(boolean newlyRenderedFirstFrame) {
        this.newlyRenderedFirstFrame = newlyRenderedFirstFrame;
        return this;
      }

      /**
       * Sets the most recent timed {@link Metadata}.
       *
       * <p>Metadata with a {@link Metadata#presentationTimeUs} of {@link C#TIME_UNSET} will not be
       * forwarded to listeners.
       *
       * @param timedMetadata The most recent timed {@link Metadata}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setTimedMetadata(Metadata timedMetadata) {
        this.timedMetadata = timedMetadata;
        return this;
      }

      /**
       * Sets the playlist items.
       *
       * <p>All playlist items must have unique {@linkplain PlaylistItem.Builder#setUid UIDs}.
       *
       * @param playlistItems The list of playlist items.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaylist(List<PlaylistItem> playlistItems) {
        HashSet<Object> uids = new HashSet<>();
        for (int i = 0; i < playlistItems.size(); i++) {
          checkArgument(uids.add(playlistItems.get(i).uid));
        }
        this.playlistItems = ImmutableList.copyOf(playlistItems);
        this.timeline = new PlaylistTimeline(this.playlistItems);
        return this;
      }

      /**
       * Sets the playlist {@link MediaMetadata}.
       *
       * @param playlistMetadata The playlist {@link MediaMetadata}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaylistMetadata(MediaMetadata playlistMetadata) {
        this.playlistMetadata = playlistMetadata;
        return this;
      }

      /**
       * Sets the current media item index.
       *
       * <p>The media item index must be less than the number of {@linkplain #setPlaylist playlist
       * items}, if set.
       *
       * @param currentMediaItemIndex The current media item index.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setCurrentMediaItemIndex(int currentMediaItemIndex) {
        this.currentMediaItemIndex = currentMediaItemIndex;
        return this;
      }

      /**
       * Sets the current period index, or {@link C#INDEX_UNSET} to assume the first period of the
       * current playlist item is played.
       *
       * <p>The period index must be less than the total number of {@linkplain
       * PlaylistItem.Builder#setPeriods periods} in the playlist, if set, and the period at the
       * specified index must be part of the {@linkplain #setCurrentMediaItemIndex current playlist
       * item}.
       *
       * @param currentPeriodIndex The current period index, or {@link C#INDEX_UNSET} to assume the
       *     first period of the current playlist item is played.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setCurrentPeriodIndex(int currentPeriodIndex) {
        checkArgument(currentPeriodIndex == C.INDEX_UNSET || currentPeriodIndex >= 0);
        this.currentPeriodIndex = currentPeriodIndex;
        return this;
      }

      /**
       * Sets the current ad indices, or {@link C#INDEX_UNSET} if no ad is playing.
       *
       * <p>Either both indices need to be {@link C#INDEX_UNSET} or both are not {@link
       * C#INDEX_UNSET}.
       *
       * <p>Ads indices can only be set if there is a corresponding {@link AdPlaybackState} defined
       * in the current {@linkplain PlaylistItem.Builder#setPeriods period}.
       *
       * @param adGroupIndex The current ad group index, or {@link C#INDEX_UNSET} if no ad is
       *     playing.
       * @param adIndexInAdGroup The current ad index in the ad group, or {@link C#INDEX_UNSET} if
       *     no ad is playing.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setCurrentAd(int adGroupIndex, int adIndexInAdGroup) {
        checkArgument((adGroupIndex == C.INDEX_UNSET) == (adIndexInAdGroup == C.INDEX_UNSET));
        this.currentAdGroupIndex = adGroupIndex;
        this.currentAdIndexInAdGroup = adIndexInAdGroup;
        return this;
      }

      /**
       * Sets the current content playback position in milliseconds.
       *
       * <p>This position will be converted to an advancing {@link PositionSupplier} if the overall
       * state indicates an advancing playback position.
       *
       * @param positionMs The current content playback position in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setContentPositionMs(long positionMs) {
        this.contentPositionMs = positionMs;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the current content playback position in
       * milliseconds.
       *
       * <p>The supplier is expected to return the updated position on every call if the playback is
       * advancing, for example by using {@link PositionSupplier#getExtrapolating}.
       *
       * @param contentPositionMsSupplier The {@link PositionSupplier} for the current content
       *     playback position in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setContentPositionMs(PositionSupplier contentPositionMsSupplier) {
        this.contentPositionMs = C.TIME_UNSET;
        this.contentPositionMsSupplier = contentPositionMsSupplier;
        return this;
      }

      /**
       * Sets the current ad playback position in milliseconds. The * value is unused if no ad is
       * playing.
       *
       * <p>This position will be converted to an advancing {@link PositionSupplier} if the overall
       * state indicates an advancing ad playback position.
       *
       * @param positionMs The current ad playback position in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAdPositionMs(long positionMs) {
        this.adPositionMs = positionMs;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the current ad playback position in milliseconds. The
       * value is unused if no ad is playing.
       *
       * <p>The supplier is expected to return the updated position on every call if the playback is
       * advancing, for example by using {@link PositionSupplier#getExtrapolating}.
       *
       * @param adPositionMsSupplier The {@link PositionSupplier} for the current ad playback
       *     position in milliseconds. The value is unused if no ad is playing.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAdPositionMs(PositionSupplier adPositionMsSupplier) {
        this.adPositionMs = C.TIME_UNSET;
        this.adPositionMsSupplier = adPositionMsSupplier;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the estimated position up to which the currently
       * playing content is buffered, in milliseconds.
       *
       * @param contentBufferedPositionMsSupplier The {@link PositionSupplier} for the estimated
       *     position up to which the currently playing content is buffered, in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setContentBufferedPositionMs(
          PositionSupplier contentBufferedPositionMsSupplier) {
        this.contentBufferedPositionMsSupplier = contentBufferedPositionMsSupplier;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the estimated position up to which the currently
       * playing ad is buffered, in milliseconds. The value is unused if no ad is playing.
       *
       * @param adBufferedPositionMsSupplier The {@link PositionSupplier} for the estimated position
       *     up to which the currently playing ad is buffered, in milliseconds. The value is unused
       *     if no ad is playing.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAdBufferedPositionMs(PositionSupplier adBufferedPositionMsSupplier) {
        this.adBufferedPositionMsSupplier = adBufferedPositionMsSupplier;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the estimated total buffered duration in
       * milliseconds.
       *
       * @param totalBufferedDurationMsSupplier The {@link PositionSupplier} for the estimated total
       *     buffered duration in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setTotalBufferedDurationMs(PositionSupplier totalBufferedDurationMsSupplier) {
        this.totalBufferedDurationMsSupplier = totalBufferedDurationMsSupplier;
        return this;
      }

      /**
       * Signals that a position discontinuity happened since the last player update and sets the
       * reason for it.
       *
       * @param positionDiscontinuityReason The {@linkplain Player.DiscontinuityReason reason} for
       *     the discontinuity.
       * @param discontinuityPositionMs The position, in milliseconds, in the current content or ad
       *     from which playback continues after the discontinuity.
       * @return This builder.
       * @see #clearPositionDiscontinuity
       */
      @CanIgnoreReturnValue
      public Builder setPositionDiscontinuity(
          @Player.DiscontinuityReason int positionDiscontinuityReason,
          long discontinuityPositionMs) {
        this.hasPositionDiscontinuity = true;
        this.positionDiscontinuityReason = positionDiscontinuityReason;
        this.discontinuityPositionMs = discontinuityPositionMs;
        return this;
      }

      /**
       * Clears a previously set position discontinuity signal.
       *
       * @return This builder.
       * @see #hasPositionDiscontinuity
       */
      @CanIgnoreReturnValue
      public Builder clearPositionDiscontinuity() {
        this.hasPositionDiscontinuity = false;
        return this;
      }

      /** Builds the {@link State}. */
      public State build() {
        return new State(this);
      }
    }

    /** The available {@link Commands}. */
    public final Commands availableCommands;
    /** Whether playback should proceed when ready and not suppressed. */
    public final boolean playWhenReady;
    /** The last reason for changing {@link #playWhenReady}. */
    public final @PlayWhenReadyChangeReason int playWhenReadyChangeReason;
    /** The {@linkplain Player.State state} of the player. */
    public final @Player.State int playbackState;
    /** The reason why playback is suppressed even if {@link #getPlayWhenReady()} is true. */
    public final @PlaybackSuppressionReason int playbackSuppressionReason;
    /** The last error that caused playback to fail, or null if there was no error. */
    @Nullable public final PlaybackException playerError;
    /** The {@link RepeatMode} used for playback. */
    public final @RepeatMode int repeatMode;
    /** Whether shuffling of media items is enabled. */
    public final boolean shuffleModeEnabled;
    /** Whether the player is currently loading its source. */
    public final boolean isLoading;
    /** The {@link Player#seekBack()} increment in milliseconds. */
    public final long seekBackIncrementMs;
    /** The {@link Player#seekForward()} increment in milliseconds. */
    public final long seekForwardIncrementMs;
    /**
     * The maximum position for which {@link #seekToPrevious()} seeks to the previous item, in
     * milliseconds.
     */
    public final long maxSeekToPreviousPositionMs;
    /** The currently active {@link PlaybackParameters}. */
    public final PlaybackParameters playbackParameters;
    /** The currently active {@link TrackSelectionParameters}. */
    public final TrackSelectionParameters trackSelectionParameters;
    /** The current {@link AudioAttributes}. */
    public final AudioAttributes audioAttributes;
    /** The current audio volume, with 0 being silence and 1 being unity gain (signal unchanged). */
    @FloatRange(from = 0, to = 1.0)
    public final float volume;
    /** The current video size. */
    public final VideoSize videoSize;
    /** The current {@linkplain CueGroup cues}. */
    public final CueGroup currentCues;
    /** The {@link DeviceInfo}. */
    public final DeviceInfo deviceInfo;
    /** The current device volume. */
    @IntRange(from = 0)
    public final int deviceVolume;
    /** Whether the device is muted. */
    public final boolean isDeviceMuted;
    /** The audio session id. */
    public final int audioSessionId;
    /** Whether skipping silences in the audio stream is enabled. */
    public final boolean skipSilenceEnabled;
    /** The size of the surface onto which the video is being rendered. */
    public final Size surfaceSize;
    /**
     * Whether a frame has been rendered for the first time since setting the surface, a rendering
     * reset, or since the stream being rendered was changed.
     */
    public final boolean newlyRenderedFirstFrame;
    /** The most recent timed metadata. */
    public final Metadata timedMetadata;
    /** The playlist items. */
    public final ImmutableList<PlaylistItem> playlistItems;
    /** The {@link Timeline} derived from the {@linkplain #playlistItems playlist items}. */
    public final Timeline timeline;
    /** The playlist {@link MediaMetadata}. */
    public final MediaMetadata playlistMetadata;
    /** The current media item index. */
    public final int currentMediaItemIndex;
    /**
     * The current period index, or {@link C#INDEX_UNSET} to assume the first period of the current
     * playlist item is played.
     */
    public final int currentPeriodIndex;
    /** The current ad group index, or {@link C#INDEX_UNSET} if no ad is playing. */
    public final int currentAdGroupIndex;
    /** The current ad index in the ad group, or {@link C#INDEX_UNSET} if no ad is playing. */
    public final int currentAdIndexInAdGroup;
    /** The {@link PositionSupplier} for the current content playback position in milliseconds. */
    public final PositionSupplier contentPositionMsSupplier;
    /**
     * The {@link PositionSupplier} for the current ad playback position in milliseconds. The value
     * is unused if no ad is playing.
     */
    public final PositionSupplier adPositionMsSupplier;
    /**
     * The {@link PositionSupplier} for the estimated position up to which the currently playing
     * content is buffered, in milliseconds.
     */
    public final PositionSupplier contentBufferedPositionMsSupplier;
    /**
     * The {@link PositionSupplier} for the estimated position up to which the currently playing ad
     * is buffered, in milliseconds. The value is unused if no ad is playing.
     */
    public final PositionSupplier adBufferedPositionMsSupplier;
    /** The {@link PositionSupplier} for the estimated total buffered duration in milliseconds. */
    public final PositionSupplier totalBufferedDurationMsSupplier;
    /** Signals that a position discontinuity happened since the last update to the player. */
    public final boolean hasPositionDiscontinuity;
    /**
     * The {@linkplain Player.DiscontinuityReason reason} for the last position discontinuity. The
     * value is unused if {@link #hasPositionDiscontinuity} is {@code false}.
     */
    public final @Player.DiscontinuityReason int positionDiscontinuityReason;
    /**
     * The position, in milliseconds, in the current content or ad from which playback continued
     * after the discontinuity. The value is unused if {@link #hasPositionDiscontinuity} is {@code
     * false}.
     */
    public final long discontinuityPositionMs;

    private State(Builder builder) {
      if (builder.timeline.isEmpty()) {
        checkArgument(
            builder.playbackState == Player.STATE_IDLE
                || builder.playbackState == Player.STATE_ENDED);
      } else {
        checkArgument(builder.currentMediaItemIndex < builder.timeline.getWindowCount());
        if (builder.currentPeriodIndex != C.INDEX_UNSET) {
          checkArgument(builder.currentPeriodIndex < builder.timeline.getPeriodCount());
          checkArgument(
              builder.timeline.getPeriod(builder.currentPeriodIndex, new Timeline.Period())
                      .windowIndex
                  == builder.currentMediaItemIndex);
        }
        if (builder.currentAdGroupIndex != C.INDEX_UNSET) {
          int periodIndex =
              builder.currentPeriodIndex != C.INDEX_UNSET
                  ? builder.currentPeriodIndex
                  : builder.timeline.getWindow(builder.currentMediaItemIndex, new Timeline.Window())
                      .firstPeriodIndex;
          Timeline.Period period = builder.timeline.getPeriod(periodIndex, new Timeline.Period());
          checkArgument(builder.currentAdGroupIndex < period.getAdGroupCount());
          int adCountInGroup = period.getAdCountInAdGroup(builder.currentAdGroupIndex);
          if (adCountInGroup != C.LENGTH_UNSET) {
            checkArgument(builder.currentAdIndexInAdGroup < adCountInGroup);
          }
        }
      }
      if (builder.playerError != null) {
        checkArgument(builder.playbackState == Player.STATE_IDLE);
      }
      if (builder.playbackState == Player.STATE_IDLE
          || builder.playbackState == Player.STATE_ENDED) {
        checkArgument(!builder.isLoading);
      }
      PositionSupplier contentPositionMsSupplier = builder.contentPositionMsSupplier;
      if (builder.contentPositionMs != C.TIME_UNSET) {
        if (builder.currentAdGroupIndex == C.INDEX_UNSET
            && builder.playWhenReady
            && builder.playbackState == Player.STATE_READY
            && builder.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
          contentPositionMsSupplier =
              PositionSupplier.getExtrapolating(
                  builder.contentPositionMs, builder.playbackParameters.speed);
        } else {
          contentPositionMsSupplier = PositionSupplier.getConstant(builder.contentPositionMs);
        }
      }
      PositionSupplier adPositionMsSupplier = builder.adPositionMsSupplier;
      if (builder.adPositionMs != C.TIME_UNSET) {
        if (builder.currentAdGroupIndex != C.INDEX_UNSET
            && builder.playWhenReady
            && builder.playbackState == Player.STATE_READY
            && builder.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
          adPositionMsSupplier =
              PositionSupplier.getExtrapolating(builder.adPositionMs, /* playbackSpeed= */ 1f);
        } else {
          adPositionMsSupplier = PositionSupplier.getConstant(builder.adPositionMs);
        }
      }
      this.availableCommands = builder.availableCommands;
      this.playWhenReady = builder.playWhenReady;
      this.playWhenReadyChangeReason = builder.playWhenReadyChangeReason;
      this.playbackState = builder.playbackState;
      this.playbackSuppressionReason = builder.playbackSuppressionReason;
      this.playerError = builder.playerError;
      this.repeatMode = builder.repeatMode;
      this.shuffleModeEnabled = builder.shuffleModeEnabled;
      this.isLoading = builder.isLoading;
      this.seekBackIncrementMs = builder.seekBackIncrementMs;
      this.seekForwardIncrementMs = builder.seekForwardIncrementMs;
      this.maxSeekToPreviousPositionMs = builder.maxSeekToPreviousPositionMs;
      this.playbackParameters = builder.playbackParameters;
      this.trackSelectionParameters = builder.trackSelectionParameters;
      this.audioAttributes = builder.audioAttributes;
      this.volume = builder.volume;
      this.videoSize = builder.videoSize;
      this.currentCues = builder.currentCues;
      this.deviceInfo = builder.deviceInfo;
      this.deviceVolume = builder.deviceVolume;
      this.isDeviceMuted = builder.isDeviceMuted;
      this.audioSessionId = builder.audioSessionId;
      this.skipSilenceEnabled = builder.skipSilenceEnabled;
      this.surfaceSize = builder.surfaceSize;
      this.newlyRenderedFirstFrame = builder.newlyRenderedFirstFrame;
      this.timedMetadata = builder.timedMetadata;
      this.playlistItems = builder.playlistItems;
      this.timeline = builder.timeline;
      this.playlistMetadata = builder.playlistMetadata;
      this.currentMediaItemIndex = builder.currentMediaItemIndex;
      this.currentPeriodIndex = builder.currentPeriodIndex;
      this.currentAdGroupIndex = builder.currentAdGroupIndex;
      this.currentAdIndexInAdGroup = builder.currentAdIndexInAdGroup;
      this.contentPositionMsSupplier = contentPositionMsSupplier;
      this.adPositionMsSupplier = adPositionMsSupplier;
      this.contentBufferedPositionMsSupplier = builder.contentBufferedPositionMsSupplier;
      this.adBufferedPositionMsSupplier = builder.adBufferedPositionMsSupplier;
      this.totalBufferedDurationMsSupplier = builder.totalBufferedDurationMsSupplier;
      this.hasPositionDiscontinuity = builder.hasPositionDiscontinuity;
      this.positionDiscontinuityReason = builder.positionDiscontinuityReason;
      this.discontinuityPositionMs = builder.discontinuityPositionMs;
    }

    /** Returns a {@link Builder} pre-populated with the current state values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof State)) {
        return false;
      }
      State state = (State) o;
      return playWhenReady == state.playWhenReady
          && playWhenReadyChangeReason == state.playWhenReadyChangeReason
          && availableCommands.equals(state.availableCommands)
          && playbackState == state.playbackState
          && playbackSuppressionReason == state.playbackSuppressionReason
          && Util.areEqual(playerError, state.playerError)
          && repeatMode == state.repeatMode
          && shuffleModeEnabled == state.shuffleModeEnabled
          && isLoading == state.isLoading
          && seekBackIncrementMs == state.seekBackIncrementMs
          && seekForwardIncrementMs == state.seekForwardIncrementMs
          && maxSeekToPreviousPositionMs == state.maxSeekToPreviousPositionMs
          && playbackParameters.equals(state.playbackParameters)
          && trackSelectionParameters.equals(state.trackSelectionParameters)
          && audioAttributes.equals(state.audioAttributes)
          && volume == state.volume
          && videoSize.equals(state.videoSize)
          && currentCues.equals(state.currentCues)
          && deviceInfo.equals(state.deviceInfo)
          && deviceVolume == state.deviceVolume
          && isDeviceMuted == state.isDeviceMuted
          && audioSessionId == state.audioSessionId
          && skipSilenceEnabled == state.skipSilenceEnabled
          && surfaceSize.equals(state.surfaceSize)
          && newlyRenderedFirstFrame == state.newlyRenderedFirstFrame
          && timedMetadata.equals(state.timedMetadata)
          && playlistItems.equals(state.playlistItems)
          && playlistMetadata.equals(state.playlistMetadata)
          && currentMediaItemIndex == state.currentMediaItemIndex
          && currentPeriodIndex == state.currentPeriodIndex
          && currentAdGroupIndex == state.currentAdGroupIndex
          && currentAdIndexInAdGroup == state.currentAdIndexInAdGroup
          && contentPositionMsSupplier.equals(state.contentPositionMsSupplier)
          && adPositionMsSupplier.equals(state.adPositionMsSupplier)
          && contentBufferedPositionMsSupplier.equals(state.contentBufferedPositionMsSupplier)
          && adBufferedPositionMsSupplier.equals(state.adBufferedPositionMsSupplier)
          && totalBufferedDurationMsSupplier.equals(state.totalBufferedDurationMsSupplier)
          && hasPositionDiscontinuity == state.hasPositionDiscontinuity
          && positionDiscontinuityReason == state.positionDiscontinuityReason
          && discontinuityPositionMs == state.discontinuityPositionMs;
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + availableCommands.hashCode();
      result = 31 * result + (playWhenReady ? 1 : 0);
      result = 31 * result + playWhenReadyChangeReason;
      result = 31 * result + playbackState;
      result = 31 * result + playbackSuppressionReason;
      result = 31 * result + (playerError == null ? 0 : playerError.hashCode());
      result = 31 * result + repeatMode;
      result = 31 * result + (shuffleModeEnabled ? 1 : 0);
      result = 31 * result + (isLoading ? 1 : 0);
      result = 31 * result + (int) (seekBackIncrementMs ^ (seekBackIncrementMs >>> 32));
      result = 31 * result + (int) (seekForwardIncrementMs ^ (seekForwardIncrementMs >>> 32));
      result =
          31 * result + (int) (maxSeekToPreviousPositionMs ^ (maxSeekToPreviousPositionMs >>> 32));
      result = 31 * result + playbackParameters.hashCode();
      result = 31 * result + trackSelectionParameters.hashCode();
      result = 31 * result + audioAttributes.hashCode();
      result = 31 * result + Float.floatToRawIntBits(volume);
      result = 31 * result + videoSize.hashCode();
      result = 31 * result + currentCues.hashCode();
      result = 31 * result + deviceInfo.hashCode();
      result = 31 * result + deviceVolume;
      result = 31 * result + (isDeviceMuted ? 1 : 0);
      result = 31 * result + audioSessionId;
      result = 31 * result + (skipSilenceEnabled ? 1 : 0);
      result = 31 * result + surfaceSize.hashCode();
      result = 31 * result + (newlyRenderedFirstFrame ? 1 : 0);
      result = 31 * result + timedMetadata.hashCode();
      result = 31 * result + playlistItems.hashCode();
      result = 31 * result + playlistMetadata.hashCode();
      result = 31 * result + currentMediaItemIndex;
      result = 31 * result + currentPeriodIndex;
      result = 31 * result + currentAdGroupIndex;
      result = 31 * result + currentAdIndexInAdGroup;
      result = 31 * result + contentPositionMsSupplier.hashCode();
      result = 31 * result + adPositionMsSupplier.hashCode();
      result = 31 * result + contentBufferedPositionMsSupplier.hashCode();
      result = 31 * result + adBufferedPositionMsSupplier.hashCode();
      result = 31 * result + totalBufferedDurationMsSupplier.hashCode();
      result = 31 * result + (hasPositionDiscontinuity ? 1 : 0);
      result = 31 * result + positionDiscontinuityReason;
      result = 31 * result + (int) (discontinuityPositionMs ^ (discontinuityPositionMs >>> 32));
      return result;
    }
  }

  private static final class PlaylistTimeline extends Timeline {

    private final ImmutableList<PlaylistItem> playlistItems;
    private final int[] firstPeriodIndexByWindowIndex;
    private final int[] windowIndexByPeriodIndex;
    private final HashMap<Object, Integer> periodIndexByUid;

    public PlaylistTimeline(ImmutableList<PlaylistItem> playlistItems) {
      int playlistItemCount = playlistItems.size();
      this.playlistItems = playlistItems;
      this.firstPeriodIndexByWindowIndex = new int[playlistItemCount];
      int periodCount = 0;
      for (int i = 0; i < playlistItemCount; i++) {
        PlaylistItem playlistItem = playlistItems.get(i);
        firstPeriodIndexByWindowIndex[i] = periodCount;
        periodCount += getPeriodCountInPlaylistItem(playlistItem);
      }
      this.windowIndexByPeriodIndex = new int[periodCount];
      this.periodIndexByUid = new HashMap<>();
      int periodIndex = 0;
      for (int i = 0; i < playlistItemCount; i++) {
        PlaylistItem playlistItem = playlistItems.get(i);
        for (int j = 0; j < getPeriodCountInPlaylistItem(playlistItem); j++) {
          periodIndexByUid.put(playlistItem.getPeriodUid(j), periodIndex);
          windowIndexByPeriodIndex[periodIndex] = i;
          periodIndex++;
        }
      }
    }

    @Override
    public int getWindowCount() {
      return playlistItems.size();
    }

    @Override
    public int getNextWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
    }

    @Override
    public int getPreviousWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
    }

    @Override
    public int getLastWindowIndex(boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getLastWindowIndex(shuffleModeEnabled);
    }

    @Override
    public int getFirstWindowIndex(boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getFirstWindowIndex(shuffleModeEnabled);
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      return playlistItems
          .get(windowIndex)
          .getWindow(firstPeriodIndexByWindowIndex[windowIndex], window);
    }

    @Override
    public int getPeriodCount() {
      return windowIndexByPeriodIndex.length;
    }

    @Override
    public Period getPeriodByUid(Object periodUid, Period period) {
      int periodIndex = checkNotNull(periodIndexByUid.get(periodUid));
      return getPeriod(periodIndex, period, /* setIds= */ true);
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      int windowIndex = windowIndexByPeriodIndex[periodIndex];
      int periodIndexInWindow = periodIndex - firstPeriodIndexByWindowIndex[windowIndex];
      return playlistItems.get(windowIndex).getPeriod(windowIndex, periodIndexInWindow, period);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      @Nullable Integer index = periodIndexByUid.get(uid);
      return index == null ? C.INDEX_UNSET : index;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      int windowIndex = windowIndexByPeriodIndex[periodIndex];
      int periodIndexInWindow = periodIndex - firstPeriodIndexByWindowIndex[windowIndex];
      return playlistItems.get(windowIndex).getPeriodUid(periodIndexInWindow);
    }

    private static int getPeriodCountInPlaylistItem(PlaylistItem playlistItem) {
      return playlistItem.periods.isEmpty() ? 1 : playlistItem.periods.size();
    }
  }

  /**
   * An immutable description of a playlist item, containing both static setup information like
   * {@link MediaItem} and dynamic data that is generally read from the media like the duration.
   */
  protected static final class PlaylistItem {

    /** A builder for {@link PlaylistItem} objects. */
    public static final class Builder {

      private Object uid;
      private Tracks tracks;
      private MediaItem mediaItem;
      @Nullable private MediaMetadata mediaMetadata;
      @Nullable private Object manifest;
      @Nullable private MediaItem.LiveConfiguration liveConfiguration;
      private long presentationStartTimeMs;
      private long windowStartTimeMs;
      private long elapsedRealtimeEpochOffsetMs;
      private boolean isSeekable;
      private boolean isDynamic;
      private long defaultPositionUs;
      private long durationUs;
      private long positionInFirstPeriodUs;
      private boolean isPlaceholder;
      private ImmutableList<PeriodData> periods;

      /**
       * Creates the builder.
       *
       * @param uid The unique identifier of the playlist item within a playlist. This value will be
       *     set as {@link Timeline.Window#uid} for this item.
       */
      public Builder(Object uid) {
        this.uid = uid;
        tracks = Tracks.EMPTY;
        mediaItem = MediaItem.EMPTY;
        mediaMetadata = null;
        manifest = null;
        liveConfiguration = null;
        presentationStartTimeMs = C.TIME_UNSET;
        windowStartTimeMs = C.TIME_UNSET;
        elapsedRealtimeEpochOffsetMs = C.TIME_UNSET;
        isSeekable = false;
        isDynamic = false;
        defaultPositionUs = 0;
        durationUs = C.TIME_UNSET;
        positionInFirstPeriodUs = 0;
        isPlaceholder = false;
        periods = ImmutableList.of();
      }

      private Builder(PlaylistItem playlistItem) {
        this.uid = playlistItem.uid;
        this.tracks = playlistItem.tracks;
        this.mediaItem = playlistItem.mediaItem;
        this.mediaMetadata = playlistItem.mediaMetadata;
        this.manifest = playlistItem.manifest;
        this.liveConfiguration = playlistItem.liveConfiguration;
        this.presentationStartTimeMs = playlistItem.presentationStartTimeMs;
        this.windowStartTimeMs = playlistItem.windowStartTimeMs;
        this.elapsedRealtimeEpochOffsetMs = playlistItem.elapsedRealtimeEpochOffsetMs;
        this.isSeekable = playlistItem.isSeekable;
        this.isDynamic = playlistItem.isDynamic;
        this.defaultPositionUs = playlistItem.defaultPositionUs;
        this.durationUs = playlistItem.durationUs;
        this.positionInFirstPeriodUs = playlistItem.positionInFirstPeriodUs;
        this.isPlaceholder = playlistItem.isPlaceholder;
        this.periods = playlistItem.periods;
      }

      /**
       * Sets the unique identifier of this playlist item within a playlist.
       *
       * <p>This value will be set as {@link Timeline.Window#uid} for this item.
       *
       * @param uid The unique identifier of this playlist item within a playlist.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setUid(Object uid) {
        this.uid = uid;
        return this;
      }

      /**
       * Sets the {@link Tracks} of this playlist item.
       *
       * @param tracks The {@link Tracks} of this playlist item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setTracks(Tracks tracks) {
        this.tracks = tracks;
        return this;
      }

      /**
       * Sets the {@link MediaItem} for this playlist item.
       *
       * @param mediaItem The {@link MediaItem} for this playlist item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setMediaItem(MediaItem mediaItem) {
        this.mediaItem = mediaItem;
        return this;
      }

      /**
       * Sets the {@link MediaMetadata}.
       *
       * <p>This data includes static data from the {@link MediaItem#mediaMetadata MediaItem} and
       * the media's {@link Format#metadata Format}, as well any dynamic metadata that has been
       * parsed from the media. If null, the metadata is assumed to be the simple combination of the
       * {@link MediaItem#mediaMetadata MediaItem} metadata and the metadata of the selected {@link
       * Format#metadata Formats}.
       *
       * @param mediaMetadata The {@link MediaMetadata}, or null to assume that the metadata is the
       *     simple combination of the {@link MediaItem#mediaMetadata MediaItem} metadata and the
       *     metadata of the selected {@link Format#metadata Formats}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setMediaMetadata(@Nullable MediaMetadata mediaMetadata) {
        this.mediaMetadata = mediaMetadata;
        return this;
      }

      /**
       * Sets the manifest of the playlist item.
       *
       * @param manifest The manifest of the playlist item, or null if not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setManifest(@Nullable Object manifest) {
        this.manifest = manifest;
        return this;
      }

      /**
       * Sets the active {@link MediaItem.LiveConfiguration}, or null if the playlist item is not
       * live.
       *
       * @param liveConfiguration The active {@link MediaItem.LiveConfiguration}, or null if the
       *     playlist item is not live.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setLiveConfiguration(@Nullable MediaItem.LiveConfiguration liveConfiguration) {
        this.liveConfiguration = liveConfiguration;
        return this;
      }

      /**
       * Sets the start time of the live presentation.
       *
       * <p>This value can only be set to anything other than {@link C#TIME_UNSET} if the stream is
       * {@linkplain #setLiveConfiguration live}.
       *
       * @param presentationStartTimeMs The start time of the live presentation, in milliseconds
       *     since the Unix epoch, or {@link C#TIME_UNSET} if unknown or not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPresentationStartTimeMs(long presentationStartTimeMs) {
        this.presentationStartTimeMs = presentationStartTimeMs;
        return this;
      }

      /**
       * Sets the start time of the live window.
       *
       * <p>This value can only be set to anything other than {@link C#TIME_UNSET} if the stream is
       * {@linkplain #setLiveConfiguration live}. The value should also be greater or equal than the
       * {@linkplain #setPresentationStartTimeMs presentation start time}, if set.
       *
       * @param windowStartTimeMs The start time of the live window, in milliseconds since the Unix
       *     epoch, or {@link C#TIME_UNSET} if unknown or not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setWindowStartTimeMs(long windowStartTimeMs) {
        this.windowStartTimeMs = windowStartTimeMs;
        return this;
      }

      /**
       * Sets the offset between {@link SystemClock#elapsedRealtime()} and the time since the Unix
       * epoch according to the clock of the media origin server.
       *
       * <p>This value can only be set to anything other than {@link C#TIME_UNSET} if the stream is
       * {@linkplain #setLiveConfiguration live}.
       *
       * @param elapsedRealtimeEpochOffsetMs The offset between {@link
       *     SystemClock#elapsedRealtime()} and the time since the Unix epoch according to the clock
       *     of the media origin server, or {@link C#TIME_UNSET} if unknown or not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setElapsedRealtimeEpochOffsetMs(long elapsedRealtimeEpochOffsetMs) {
        this.elapsedRealtimeEpochOffsetMs = elapsedRealtimeEpochOffsetMs;
        return this;
      }

      /**
       * Sets whether it's possible to seek within this playlist item.
       *
       * @param isSeekable Whether it's possible to seek within this playlist item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsSeekable(boolean isSeekable) {
        this.isSeekable = isSeekable;
        return this;
      }

      /**
       * Sets whether this playlist item may change over time, for example a moving live window.
       *
       * @param isDynamic Whether this playlist item may change over time, for example a moving live
       *     window.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsDynamic(boolean isDynamic) {
        this.isDynamic = isDynamic;
        return this;
      }

      /**
       * Sets the default position relative to the start of the playlist item at which to begin
       * playback, in microseconds.
       *
       * <p>The default position must be less or equal to the {@linkplain #setDurationUs duration},
       * is set.
       *
       * @param defaultPositionUs The default position relative to the start of the playlist item at
       *     which to begin playback, in microseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDefaultPositionUs(long defaultPositionUs) {
        checkArgument(defaultPositionUs >= 0);
        this.defaultPositionUs = defaultPositionUs;
        return this;
      }

      /**
       * Sets the duration of the playlist item, in microseconds.
       *
       * <p>If both this duration and all {@linkplain #setPeriods period} durations are set, the sum
       * of this duration and the {@linkplain #setPositionInFirstPeriodUs offset in the first
       * period} must match the total duration of all periods.
       *
       * @param durationUs The duration of the playlist item, in microseconds, or {@link
       *     C#TIME_UNSET} if unknown.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDurationUs(long durationUs) {
        checkArgument(durationUs == C.TIME_UNSET || durationUs >= 0);
        this.durationUs = durationUs;
        return this;
      }

      /**
       * Sets the position of the start of this playlist item relative to the start of the first
       * period belonging to it, in microseconds.
       *
       * @param positionInFirstPeriodUs The position of the start of this playlist item relative to
       *     the start of the first period belonging to it, in microseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPositionInFirstPeriodUs(long positionInFirstPeriodUs) {
        checkArgument(positionInFirstPeriodUs >= 0);
        this.positionInFirstPeriodUs = positionInFirstPeriodUs;
        return this;
      }

      /**
       * Sets whether this playlist item contains placeholder information because the real
       * information has yet to be loaded.
       *
       * @param isPlaceholder Whether this playlist item contains placeholder information because
       *     the real information has yet to be loaded.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsPlaceholder(boolean isPlaceholder) {
        this.isPlaceholder = isPlaceholder;
        return this;
      }

      /**
       * Sets the list of {@linkplain PeriodData periods} in this playlist item.
       *
       * <p>All periods must have unique {@linkplain PeriodData.Builder#setUid UIDs} and only the
       * last period is allowed to have an unset {@linkplain PeriodData.Builder#setDurationUs
       * duration}.
       *
       * @param periods The list of {@linkplain PeriodData periods} in this playlist item, or an
       *     empty list to assume a single period without ads and the same duration as the playlist
       *     item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPeriods(List<PeriodData> periods) {
        int periodCount = periods.size();
        for (int i = 0; i < periodCount - 1; i++) {
          checkArgument(periods.get(i).durationUs != C.TIME_UNSET);
          for (int j = i + 1; j < periodCount; j++) {
            checkArgument(!periods.get(i).uid.equals(periods.get(j).uid));
          }
        }
        this.periods = ImmutableList.copyOf(periods);
        return this;
      }

      /** Builds the {@link PlaylistItem}. */
      public PlaylistItem build() {
        return new PlaylistItem(this);
      }
    }

    /** The unique identifier of this playlist item. */
    public final Object uid;
    /** The {@link Tracks} of this playlist item. */
    public final Tracks tracks;
    /** The {@link MediaItem} for this playlist item. */
    public final MediaItem mediaItem;
    /**
     * The {@link MediaMetadata}, including static data from the {@link MediaItem#mediaMetadata
     * MediaItem} and the media's {@link Format#metadata Format}, as well any dynamic metadata that
     * has been parsed from the media. If null, the metadata is assumed to be the simple combination
     * of the {@link MediaItem#mediaMetadata MediaItem} metadata and the metadata of the selected
     * {@link Format#metadata Formats}.
     */
    @Nullable public final MediaMetadata mediaMetadata;
    /** The manifest of the playlist item, or null if not applicable. */
    @Nullable public final Object manifest;
    /** The active {@link MediaItem.LiveConfiguration}, or null if the playlist item is not live. */
    @Nullable public final MediaItem.LiveConfiguration liveConfiguration;
    /**
     * The start time of the live presentation, in milliseconds since the Unix epoch, or {@link
     * C#TIME_UNSET} if unknown or not applicable.
     */
    public final long presentationStartTimeMs;
    /**
     * The start time of the live window, in milliseconds since the Unix epoch, or {@link
     * C#TIME_UNSET} if unknown or not applicable.
     */
    public final long windowStartTimeMs;
    /**
     * The offset between {@link SystemClock#elapsedRealtime()} and the time since the Unix epoch
     * according to the clock of the media origin server, or {@link C#TIME_UNSET} if unknown or not
     * applicable.
     */
    public final long elapsedRealtimeEpochOffsetMs;
    /** Whether it's possible to seek within this playlist item. */
    public final boolean isSeekable;
    /** Whether this playlist item may change over time, for example a moving live window. */
    public final boolean isDynamic;
    /**
     * The default position relative to the start of the playlist item at which to begin playback,
     * in microseconds.
     */
    public final long defaultPositionUs;
    /** The duration of the playlist item, in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public final long durationUs;
    /**
     * The position of the start of this playlist item relative to the start of the first period
     * belonging to it, in microseconds.
     */
    public final long positionInFirstPeriodUs;
    /**
     * Whether this playlist item contains placeholder information because the real information has
     * yet to be loaded.
     */
    public final boolean isPlaceholder;
    /**
     * The list of {@linkplain PeriodData periods} in this playlist item, or an empty list to assume
     * a single period without ads and the same duration as the playlist item.
     */
    public final ImmutableList<PeriodData> periods;

    private final long[] periodPositionInWindowUs;
    private final MediaMetadata combinedMediaMetadata;

    private PlaylistItem(Builder builder) {
      if (builder.liveConfiguration == null) {
        checkArgument(builder.presentationStartTimeMs == C.TIME_UNSET);
        checkArgument(builder.windowStartTimeMs == C.TIME_UNSET);
        checkArgument(builder.elapsedRealtimeEpochOffsetMs == C.TIME_UNSET);
      } else if (builder.presentationStartTimeMs != C.TIME_UNSET
          && builder.windowStartTimeMs != C.TIME_UNSET) {
        checkArgument(builder.windowStartTimeMs >= builder.presentationStartTimeMs);
      }
      int periodCount = builder.periods.size();
      if (builder.durationUs != C.TIME_UNSET) {
        checkArgument(builder.defaultPositionUs <= builder.durationUs);
      }
      this.uid = builder.uid;
      this.tracks = builder.tracks;
      this.mediaItem = builder.mediaItem;
      this.mediaMetadata = builder.mediaMetadata;
      this.manifest = builder.manifest;
      this.liveConfiguration = builder.liveConfiguration;
      this.presentationStartTimeMs = builder.presentationStartTimeMs;
      this.windowStartTimeMs = builder.windowStartTimeMs;
      this.elapsedRealtimeEpochOffsetMs = builder.elapsedRealtimeEpochOffsetMs;
      this.isSeekable = builder.isSeekable;
      this.isDynamic = builder.isDynamic;
      this.defaultPositionUs = builder.defaultPositionUs;
      this.durationUs = builder.durationUs;
      this.positionInFirstPeriodUs = builder.positionInFirstPeriodUs;
      this.isPlaceholder = builder.isPlaceholder;
      this.periods = builder.periods;
      periodPositionInWindowUs = new long[periods.size()];
      if (!periods.isEmpty()) {
        periodPositionInWindowUs[0] = -positionInFirstPeriodUs;
        for (int i = 0; i < periodCount - 1; i++) {
          periodPositionInWindowUs[i + 1] = periodPositionInWindowUs[i] + periods.get(i).durationUs;
        }
      }
      combinedMediaMetadata =
          mediaMetadata != null ? mediaMetadata : getCombinedMediaMetadata(mediaItem, tracks);
    }

    /** Returns a {@link Builder} pre-populated with the current values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof PlaylistItem)) {
        return false;
      }
      PlaylistItem playlistItem = (PlaylistItem) o;
      return this.uid.equals(playlistItem.uid)
          && this.tracks.equals(playlistItem.tracks)
          && this.mediaItem.equals(playlistItem.mediaItem)
          && Util.areEqual(this.mediaMetadata, playlistItem.mediaMetadata)
          && Util.areEqual(this.manifest, playlistItem.manifest)
          && Util.areEqual(this.liveConfiguration, playlistItem.liveConfiguration)
          && this.presentationStartTimeMs == playlistItem.presentationStartTimeMs
          && this.windowStartTimeMs == playlistItem.windowStartTimeMs
          && this.elapsedRealtimeEpochOffsetMs == playlistItem.elapsedRealtimeEpochOffsetMs
          && this.isSeekable == playlistItem.isSeekable
          && this.isDynamic == playlistItem.isDynamic
          && this.defaultPositionUs == playlistItem.defaultPositionUs
          && this.durationUs == playlistItem.durationUs
          && this.positionInFirstPeriodUs == playlistItem.positionInFirstPeriodUs
          && this.isPlaceholder == playlistItem.isPlaceholder
          && this.periods.equals(playlistItem.periods);
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + uid.hashCode();
      result = 31 * result + tracks.hashCode();
      result = 31 * result + mediaItem.hashCode();
      result = 31 * result + (mediaMetadata == null ? 0 : mediaMetadata.hashCode());
      result = 31 * result + (manifest == null ? 0 : manifest.hashCode());
      result = 31 * result + (liveConfiguration == null ? 0 : liveConfiguration.hashCode());
      result = 31 * result + (int) (presentationStartTimeMs ^ (presentationStartTimeMs >>> 32));
      result = 31 * result + (int) (windowStartTimeMs ^ (windowStartTimeMs >>> 32));
      result =
          31 * result
              + (int) (elapsedRealtimeEpochOffsetMs ^ (elapsedRealtimeEpochOffsetMs >>> 32));
      result = 31 * result + (isSeekable ? 1 : 0);
      result = 31 * result + (isDynamic ? 1 : 0);
      result = 31 * result + (int) (defaultPositionUs ^ (defaultPositionUs >>> 32));
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + (int) (positionInFirstPeriodUs ^ (positionInFirstPeriodUs >>> 32));
      result = 31 * result + (isPlaceholder ? 1 : 0);
      result = 31 * result + periods.hashCode();
      return result;
    }

    private Timeline.Window getWindow(int firstPeriodIndex, Timeline.Window window) {
      int periodCount = periods.isEmpty() ? 1 : periods.size();
      window.set(
          uid,
          mediaItem,
          manifest,
          presentationStartTimeMs,
          windowStartTimeMs,
          elapsedRealtimeEpochOffsetMs,
          isSeekable,
          isDynamic,
          liveConfiguration,
          defaultPositionUs,
          durationUs,
          firstPeriodIndex,
          /* lastPeriodIndex= */ firstPeriodIndex + periodCount - 1,
          positionInFirstPeriodUs);
      window.isPlaceholder = isPlaceholder;
      return window;
    }

    private Timeline.Period getPeriod(
        int windowIndex, int periodIndexInPlaylistItem, Timeline.Period period) {
      if (periods.isEmpty()) {
        period.set(
            /* id= */ uid,
            uid,
            windowIndex,
            /* durationUs= */ positionInFirstPeriodUs + durationUs,
            /* positionInWindowUs= */ 0,
            AdPlaybackState.NONE,
            isPlaceholder);
      } else {
        PeriodData periodData = periods.get(periodIndexInPlaylistItem);
        Object periodId = periodData.uid;
        Object periodUid = Pair.create(uid, periodId);
        period.set(
            periodId,
            periodUid,
            windowIndex,
            periodData.durationUs,
            periodPositionInWindowUs[periodIndexInPlaylistItem],
            periodData.adPlaybackState,
            periodData.isPlaceholder);
      }
      return period;
    }

    private Object getPeriodUid(int periodIndexInPlaylistItem) {
      if (periods.isEmpty()) {
        return uid;
      }
      Object periodId = periods.get(periodIndexInPlaylistItem).uid;
      return Pair.create(uid, periodId);
    }

    private static MediaMetadata getCombinedMediaMetadata(MediaItem mediaItem, Tracks tracks) {
      MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
      int trackGroupCount = tracks.getGroups().size();
      for (int i = 0; i < trackGroupCount; i++) {
        Tracks.Group group = tracks.getGroups().get(i);
        for (int j = 0; j < group.length; j++) {
          if (group.isTrackSelected(j)) {
            Format format = group.getTrackFormat(j);
            if (format.metadata != null) {
              for (int k = 0; k < format.metadata.length(); k++) {
                format.metadata.get(k).populateMediaMetadata(metadataBuilder);
              }
            }
          }
        }
      }
      return metadataBuilder.populate(mediaItem.mediaMetadata).build();
    }
  }

  /** Data describing the properties of a period inside a {@link PlaylistItem}. */
  protected static final class PeriodData {

    /** A builder for {@link PeriodData} objects. */
    public static final class Builder {

      private Object uid;
      private long durationUs;
      private AdPlaybackState adPlaybackState;
      private boolean isPlaceholder;

      /**
       * Creates the builder.
       *
       * @param uid The unique identifier of the period within its playlist item.
       */
      public Builder(Object uid) {
        this.uid = uid;
        this.durationUs = 0;
        this.adPlaybackState = AdPlaybackState.NONE;
        this.isPlaceholder = false;
      }

      private Builder(PeriodData periodData) {
        this.uid = periodData.uid;
        this.durationUs = periodData.durationUs;
        this.adPlaybackState = periodData.adPlaybackState;
        this.isPlaceholder = periodData.isPlaceholder;
      }

      /**
       * Sets the unique identifier of the period within its playlist item.
       *
       * @param uid The unique identifier of the period within its playlist item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setUid(Object uid) {
        this.uid = uid;
        return this;
      }

      /**
       * Sets the total duration of the period, in microseconds, or {@link C#TIME_UNSET} if unknown.
       *
       * <p>Only the last period in a playlist item can have an unknown duration.
       *
       * @param durationUs The total duration of the period, in microseconds, or {@link
       *     C#TIME_UNSET} if unknown.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDurationUs(long durationUs) {
        checkArgument(durationUs == C.TIME_UNSET || durationUs >= 0);
        this.durationUs = durationUs;
        return this;
      }

      /**
       * Sets the {@link AdPlaybackState}.
       *
       * @param adPlaybackState The {@link AdPlaybackState}, or {@link AdPlaybackState#NONE} if
       *     there are no ads.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAdPlaybackState(AdPlaybackState adPlaybackState) {
        this.adPlaybackState = adPlaybackState;
        return this;
      }

      /**
       * Sets whether this period contains placeholder information because the real information has
       * yet to be loaded
       *
       * @param isPlaceholder Whether this period contains placeholder information because the real
       *     information has yet to be loaded.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsPlaceholder(boolean isPlaceholder) {
        this.isPlaceholder = isPlaceholder;
        return this;
      }

      /** Builds the {@link PeriodData}. */
      public PeriodData build() {
        return new PeriodData(this);
      }
    }

    /** The unique identifier of the period within its playlist item. */
    public final Object uid;
    /**
     * The total duration of the period, in microseconds, or {@link C#TIME_UNSET} if unknown. Only
     * the last period in a playlist item can have an unknown duration.
     */
    public final long durationUs;
    /**
     * The {@link AdPlaybackState} of the period, or {@link AdPlaybackState#NONE} if there are no
     * ads.
     */
    public final AdPlaybackState adPlaybackState;
    /**
     * Whether this period contains placeholder information because the real information has yet to
     * be loaded.
     */
    public final boolean isPlaceholder;

    private PeriodData(Builder builder) {
      this.uid = builder.uid;
      this.durationUs = builder.durationUs;
      this.adPlaybackState = builder.adPlaybackState;
      this.isPlaceholder = builder.isPlaceholder;
    }

    /** Returns a {@link Builder} pre-populated with the current values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof PeriodData)) {
        return false;
      }
      PeriodData periodData = (PeriodData) o;
      return this.uid.equals(periodData.uid)
          && this.durationUs == periodData.durationUs
          && this.adPlaybackState.equals(periodData.adPlaybackState)
          && this.isPlaceholder == periodData.isPlaceholder;
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + uid.hashCode();
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + adPlaybackState.hashCode();
      result = 31 * result + (isPlaceholder ? 1 : 0);
      return result;
    }
  }

  /** A supplier for a position. */
  protected interface PositionSupplier {

    /** An instance returning a constant position of zero. */
    PositionSupplier ZERO = getConstant(/* positionMs= */ 0);

    /**
     * Returns an instance that returns a constant value.
     *
     * @param positionMs The constant position to return, in milliseconds.
     */
    static PositionSupplier getConstant(long positionMs) {
      return () -> positionMs;
    }

    /**
     * Returns an instance that extrapolates the provided position into the future.
     *
     * @param currentPositionMs The current position in milliseconds.
     * @param playbackSpeed The playback speed with which the position is assumed to increase.
     */
    static PositionSupplier getExtrapolating(long currentPositionMs, float playbackSpeed) {
      long startTimeMs = SystemClock.elapsedRealtime();
      return () -> {
        long currentTimeMs = SystemClock.elapsedRealtime();
        return currentPositionMs + (long) ((currentTimeMs - startTimeMs) * playbackSpeed);
      };
    }

    /** Returns the position. */
    long get();
  }

  /**
   * Position difference threshold below which we do not automatically report a position
   * discontinuity, in milliseconds.
   */
  private static final long POSITION_DISCONTINUITY_THRESHOLD_MS = 1000;

  private final ListenerSet<Listener> listeners;
  private final Looper applicationLooper;
  private final HandlerWrapper applicationHandler;
  private final HashSet<ListenableFuture<?>> pendingOperations;
  private final Timeline.Period period;

  private @MonotonicNonNull State state;

  /**
   * Creates the base class.
   *
   * @param applicationLooper The {@link Looper} that must be used for all calls to the player and
   *     that is used to call listeners on.
   */
  protected SimpleBasePlayer(Looper applicationLooper) {
    this(applicationLooper, Clock.DEFAULT);
  }

  /**
   * Creates the base class.
   *
   * @param applicationLooper The {@link Looper} that must be used for all calls to the player and
   *     that is used to call listeners on.
   * @param clock The {@link Clock} that will be used by the player.
   */
  protected SimpleBasePlayer(Looper applicationLooper, Clock clock) {
    this.applicationLooper = applicationLooper;
    applicationHandler = clock.createHandler(applicationLooper, /* callback= */ null);
    pendingOperations = new HashSet<>();
    period = new Timeline.Period();
    @SuppressWarnings("nullness:argument.type.incompatible") // Using this in constructor.
    ListenerSet<Player.Listener> listenerSet =
        new ListenerSet<>(
            applicationLooper,
            clock,
            (listener, flags) -> listener.onEvents(/* player= */ this, new Events(flags)));
    listeners = listenerSet;
  }

  @Override
  public final void addListener(Listener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    listeners.add(checkNotNull(listener));
  }

  @Override
  public final void removeListener(Listener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    checkNotNull(listener);
    listeners.remove(listener);
  }

  @Override
  public final Looper getApplicationLooper() {
    // Don't verify application thread. We allow calls to this method from any thread.
    return applicationLooper;
  }

  @Override
  public final Commands getAvailableCommands() {
    verifyApplicationThreadAndInitState();
    return state.availableCommands;
  }

  @Override
  public final void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThreadAndInitState();
    State state = this.state;
    if (!state.availableCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetPlayWhenReady(playWhenReady),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build());
  }

  @Override
  public final boolean getPlayWhenReady() {
    verifyApplicationThreadAndInitState();
    return state.playWhenReady;
  }

  @Override
  public final void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setMediaItems(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void addMediaItems(int index, List<MediaItem> mediaItems) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void removeMediaItems(int fromIndex, int toIndex) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void prepare() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  @Player.State
  public final int getPlaybackState() {
    verifyApplicationThreadAndInitState();
    return state.playbackState;
  }

  @Override
  public final int getPlaybackSuppressionReason() {
    verifyApplicationThreadAndInitState();
    return state.playbackSuppressionReason;
  }

  @Nullable
  @Override
  public final PlaybackException getPlayerError() {
    verifyApplicationThreadAndInitState();
    return state.playerError;
  }

  @Override
  public final void setRepeatMode(@Player.RepeatMode int repeatMode) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  @Player.RepeatMode
  public final int getRepeatMode() {
    verifyApplicationThreadAndInitState();
    return state.repeatMode;
  }

  @Override
  public final void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final boolean getShuffleModeEnabled() {
    verifyApplicationThreadAndInitState();
    return state.shuffleModeEnabled;
  }

  @Override
  public final boolean isLoading() {
    verifyApplicationThreadAndInitState();
    return state.isLoading;
  }

  @Override
  public final void seekTo(int mediaItemIndex, long positionMs) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final long getSeekBackIncrement() {
    verifyApplicationThreadAndInitState();
    return state.seekBackIncrementMs;
  }

  @Override
  public final long getSeekForwardIncrement() {
    verifyApplicationThreadAndInitState();
    return state.seekForwardIncrementMs;
  }

  @Override
  public final long getMaxSeekToPreviousPosition() {
    verifyApplicationThreadAndInitState();
    return state.maxSeekToPreviousPositionMs;
  }

  @Override
  public final void setPlaybackParameters(PlaybackParameters playbackParameters) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final PlaybackParameters getPlaybackParameters() {
    verifyApplicationThreadAndInitState();
    return state.playbackParameters;
  }

  @Override
  public final void stop() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void stop(boolean reset) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void release() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final Tracks getCurrentTracks() {
    verifyApplicationThreadAndInitState();
    return getCurrentTracksInternal(state);
  }

  @Override
  public final TrackSelectionParameters getTrackSelectionParameters() {
    verifyApplicationThreadAndInitState();
    return state.trackSelectionParameters;
  }

  @Override
  public final void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final MediaMetadata getMediaMetadata() {
    verifyApplicationThreadAndInitState();
    return getMediaMetadataInternal(state);
  }

  @Override
  public final MediaMetadata getPlaylistMetadata() {
    verifyApplicationThreadAndInitState();
    return state.playlistMetadata;
  }

  @Override
  public final void setPlaylistMetadata(MediaMetadata mediaMetadata) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final Timeline getCurrentTimeline() {
    verifyApplicationThreadAndInitState();
    return state.timeline;
  }

  @Override
  public final int getCurrentPeriodIndex() {
    verifyApplicationThreadAndInitState();
    return getCurrentPeriodIndexInternal(state, window);
  }

  @Override
  public final int getCurrentMediaItemIndex() {
    verifyApplicationThreadAndInitState();
    return state.currentMediaItemIndex;
  }

  @Override
  public final long getDuration() {
    verifyApplicationThreadAndInitState();
    if (isPlayingAd()) {
      state.timeline.getPeriod(getCurrentPeriodIndex(), period);
      long adDurationUs =
          period.getAdDurationUs(state.currentAdGroupIndex, state.currentAdIndexInAdGroup);
      return Util.usToMs(adDurationUs);
    }
    return getContentDuration();
  }

  @Override
  public final long getCurrentPosition() {
    verifyApplicationThreadAndInitState();
    return isPlayingAd() ? state.adPositionMsSupplier.get() : getContentPosition();
  }

  @Override
  public final long getBufferedPosition() {
    verifyApplicationThreadAndInitState();
    return isPlayingAd()
        ? max(state.adBufferedPositionMsSupplier.get(), state.adPositionMsSupplier.get())
        : getContentBufferedPosition();
  }

  @Override
  public final long getTotalBufferedDuration() {
    verifyApplicationThreadAndInitState();
    return state.totalBufferedDurationMsSupplier.get();
  }

  @Override
  public final boolean isPlayingAd() {
    verifyApplicationThreadAndInitState();
    return state.currentAdGroupIndex != C.INDEX_UNSET;
  }

  @Override
  public final int getCurrentAdGroupIndex() {
    verifyApplicationThreadAndInitState();
    return state.currentAdGroupIndex;
  }

  @Override
  public final int getCurrentAdIndexInAdGroup() {
    verifyApplicationThreadAndInitState();
    return state.currentAdIndexInAdGroup;
  }

  @Override
  public final long getContentPosition() {
    verifyApplicationThreadAndInitState();
    return state.contentPositionMsSupplier.get();
  }

  @Override
  public final long getContentBufferedPosition() {
    verifyApplicationThreadAndInitState();
    return max(
        state.contentBufferedPositionMsSupplier.get(), state.contentPositionMsSupplier.get());
  }

  @Override
  public final AudioAttributes getAudioAttributes() {
    verifyApplicationThreadAndInitState();
    return state.audioAttributes;
  }

  @Override
  public final void setVolume(float volume) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final float getVolume() {
    verifyApplicationThreadAndInitState();
    return state.volume;
  }

  @Override
  public final void clearVideoSurface() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void clearVideoSurface(@Nullable Surface surface) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setVideoSurface(@Nullable Surface surface) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setVideoTextureView(@Nullable TextureView textureView) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void clearVideoTextureView(@Nullable TextureView textureView) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final VideoSize getVideoSize() {
    verifyApplicationThreadAndInitState();
    return state.videoSize;
  }

  @Override
  public final Size getSurfaceSize() {
    verifyApplicationThreadAndInitState();
    return state.surfaceSize;
  }

  @Override
  public final CueGroup getCurrentCues() {
    verifyApplicationThreadAndInitState();
    return state.currentCues;
  }

  @Override
  public final DeviceInfo getDeviceInfo() {
    verifyApplicationThreadAndInitState();
    return state.deviceInfo;
  }

  @Override
  public final int getDeviceVolume() {
    verifyApplicationThreadAndInitState();
    return state.deviceVolume;
  }

  @Override
  public final boolean isDeviceMuted() {
    verifyApplicationThreadAndInitState();
    return state.isDeviceMuted;
  }

  @Override
  public final void setDeviceVolume(int volume) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void increaseDeviceVolume() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void decreaseDeviceVolume() {
    // TODO: implement.
    throw new IllegalStateException();
  }

  @Override
  public final void setDeviceMuted(boolean muted) {
    // TODO: implement.
    throw new IllegalStateException();
  }

  /**
   * Invalidates the current state.
   *
   * <p>Triggers a call to {@link #getState()} and informs listeners if the state changed.
   *
   * <p>Note that this may not have an immediate effect while there are still player methods being
   * handled asynchronously. The state will be invalidated automatically once these pending
   * synchronous operations are finished and there is no need to call this method again.
   */
  protected final void invalidateState() {
    verifyApplicationThreadAndInitState();
    if (!pendingOperations.isEmpty()) {
      return;
    }
    updateStateAndInformListeners(getState());
  }

  /**
   * Returns the current {@link State} of the player.
   *
   * <p>The {@link State} should include all {@linkplain
   * State.Builder#setAvailableCommands(Commands) available commands} indicating which player
   * methods are allowed to be called.
   *
   * <p>Note that this method won't be called while asynchronous handling of player methods is in
   * progress. This means that the implementation doesn't need to handle state changes caused by
   * these asynchronous operations until they are done and can return the currently known state
   * directly. The placeholder state used while these asynchronous operations are in progress can be
   * customized by overriding {@link #getPlaceholderState(State)} if required.
   */
  @ForOverride
  protected abstract State getState();

  /**
   * Returns the placeholder state used while a player method is handled asynchronously.
   *
   * <p>The {@code suggestedPlaceholderState} already contains the most likely state update, for
   * example setting {@link State#playWhenReady} to true if {@code player.setPlayWhenReady(true)} is
   * called, and an implementations only needs to override this method if it can determine a more
   * accurate placeholder state.
   *
   * @param suggestedPlaceholderState The suggested placeholder {@link State}, including the most
   *     likely outcome of handling all pending asynchronous operations.
   * @return The placeholder {@link State} to use while asynchronous operations are pending.
   */
  @ForOverride
  protected State getPlaceholderState(State suggestedPlaceholderState) {
    return suggestedPlaceholderState;
  }

  /**
   * Handles calls to set {@link State#playWhenReady}.
   *
   * <p>Will only be called if {@link Player.Command#COMMAND_PLAY_PAUSE} is available.
   *
   * @param playWhenReady The requested {@link State#playWhenReady}
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   * @see Player#setPlayWhenReady(boolean)
   * @see Player#play()
   * @see Player#pause()
   */
  @ForOverride
  protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
    throw new IllegalStateException();
  }

  @SuppressWarnings("deprecation") // Calling deprecated listener methods.
  @RequiresNonNull("state")
  private void updateStateAndInformListeners(State newState) {
    State previousState = state;
    // Assign new state immediately such that all getters return the right values, but use a
    // snapshot of the previous and new state so that listener invocations are triggered correctly.
    this.state = newState;

    boolean playWhenReadyChanged = previousState.playWhenReady != newState.playWhenReady;
    boolean playbackStateChanged = previousState.playbackState != newState.playbackState;
    Tracks previousTracks = getCurrentTracksInternal(previousState);
    Tracks newTracks = getCurrentTracksInternal(newState);
    MediaMetadata previousMediaMetadata = getMediaMetadataInternal(previousState);
    MediaMetadata newMediaMetadata = getMediaMetadataInternal(newState);
    int positionDiscontinuityReason =
        getPositionDiscontinuityReason(previousState, newState, window, period);
    boolean timelineChanged = !previousState.timeline.equals(newState.timeline);
    int mediaItemTransitionReason =
        getMediaItemTransitionReason(previousState, newState, positionDiscontinuityReason, window);

    if (timelineChanged) {
      @Player.TimelineChangeReason
      int timelineChangeReason =
          getTimelineChangeReason(previousState.playlistItems, newState.playlistItems);
      listeners.queueEvent(
          Player.EVENT_TIMELINE_CHANGED,
          listener -> listener.onTimelineChanged(newState.timeline, timelineChangeReason));
    }
    if (positionDiscontinuityReason != C.INDEX_UNSET) {
      PositionInfo previousPositionInfo =
          getPositionInfo(previousState, /* useDiscontinuityPosition= */ false, window, period);
      PositionInfo positionInfo =
          getPositionInfo(
              newState,
              /* useDiscontinuityPosition= */ state.hasPositionDiscontinuity,
              window,
              period);
      listeners.queueEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          listener -> {
            listener.onPositionDiscontinuity(positionDiscontinuityReason);
            listener.onPositionDiscontinuity(
                previousPositionInfo, positionInfo, positionDiscontinuityReason);
          });
    }
    if (mediaItemTransitionReason != C.INDEX_UNSET) {
      @Nullable
      MediaItem mediaItem =
          state.timeline.isEmpty()
              ? null
              : state.playlistItems.get(state.currentMediaItemIndex).mediaItem;
      listeners.queueEvent(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          listener -> listener.onMediaItemTransition(mediaItem, mediaItemTransitionReason));
    }
    if (!Util.areEqual(previousState.playerError, newState.playerError)) {
      listeners.queueEvent(
          Player.EVENT_PLAYER_ERROR,
          listener -> listener.onPlayerErrorChanged(newState.playerError));
      if (newState.playerError != null) {
        listeners.queueEvent(
            Player.EVENT_PLAYER_ERROR,
            listener -> listener.onPlayerError(castNonNull(newState.playerError)));
      }
    }
    if (!previousState.trackSelectionParameters.equals(newState.trackSelectionParameters)) {
      listeners.queueEvent(
          Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
          listener ->
              listener.onTrackSelectionParametersChanged(newState.trackSelectionParameters));
    }
    if (!previousTracks.equals(newTracks)) {
      listeners.queueEvent(
          Player.EVENT_TRACKS_CHANGED, listener -> listener.onTracksChanged(newTracks));
    }
    if (!previousMediaMetadata.equals(newMediaMetadata)) {
      listeners.queueEvent(
          EVENT_MEDIA_METADATA_CHANGED,
          listener -> listener.onMediaMetadataChanged(newMediaMetadata));
    }
    if (previousState.isLoading != newState.isLoading) {
      listeners.queueEvent(
          Player.EVENT_IS_LOADING_CHANGED,
          listener -> {
            listener.onLoadingChanged(newState.isLoading);
            listener.onIsLoadingChanged(newState.isLoading);
          });
    }
    if (playWhenReadyChanged || playbackStateChanged) {
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener ->
              listener.onPlayerStateChanged(newState.playWhenReady, newState.playbackState));
    }
    if (playbackStateChanged) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          listener -> listener.onPlaybackStateChanged(newState.playbackState));
    }
    if (playWhenReadyChanged
        || previousState.playWhenReadyChangeReason != newState.playWhenReadyChangeReason) {
      listeners.queueEvent(
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          listener ->
              listener.onPlayWhenReadyChanged(
                  newState.playWhenReady, newState.playWhenReadyChangeReason));
    }
    if (previousState.playbackSuppressionReason != newState.playbackSuppressionReason) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
          listener ->
              listener.onPlaybackSuppressionReasonChanged(newState.playbackSuppressionReason));
    }
    if (isPlaying(previousState) != isPlaying(newState)) {
      listeners.queueEvent(
          Player.EVENT_IS_PLAYING_CHANGED,
          listener -> listener.onIsPlayingChanged(isPlaying(newState)));
    }
    if (!previousState.playbackParameters.equals(newState.playbackParameters)) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
          listener -> listener.onPlaybackParametersChanged(newState.playbackParameters));
    }
    if (previousState.skipSilenceEnabled != newState.skipSilenceEnabled) {
      listeners.queueEvent(
          Player.EVENT_SKIP_SILENCE_ENABLED_CHANGED,
          listener -> listener.onSkipSilenceEnabledChanged(newState.skipSilenceEnabled));
    }
    if (previousState.repeatMode != newState.repeatMode) {
      listeners.queueEvent(
          Player.EVENT_REPEAT_MODE_CHANGED,
          listener -> listener.onRepeatModeChanged(newState.repeatMode));
    }
    if (previousState.shuffleModeEnabled != newState.shuffleModeEnabled) {
      listeners.queueEvent(
          Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          listener -> listener.onShuffleModeEnabledChanged(newState.shuffleModeEnabled));
    }
    if (previousState.seekBackIncrementMs != newState.seekBackIncrementMs) {
      listeners.queueEvent(
          Player.EVENT_SEEK_BACK_INCREMENT_CHANGED,
          listener -> listener.onSeekBackIncrementChanged(newState.seekBackIncrementMs));
    }
    if (previousState.seekForwardIncrementMs != newState.seekForwardIncrementMs) {
      listeners.queueEvent(
          Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED,
          listener -> listener.onSeekForwardIncrementChanged(newState.seekForwardIncrementMs));
    }
    if (previousState.maxSeekToPreviousPositionMs != newState.maxSeekToPreviousPositionMs) {
      listeners.queueEvent(
          Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED,
          listener ->
              listener.onMaxSeekToPreviousPositionChanged(newState.maxSeekToPreviousPositionMs));
    }
    if (!previousState.audioAttributes.equals(newState.audioAttributes)) {
      listeners.queueEvent(
          Player.EVENT_AUDIO_ATTRIBUTES_CHANGED,
          listener -> listener.onAudioAttributesChanged(newState.audioAttributes));
    }
    if (!previousState.videoSize.equals(newState.videoSize)) {
      listeners.queueEvent(
          Player.EVENT_VIDEO_SIZE_CHANGED,
          listener -> listener.onVideoSizeChanged(newState.videoSize));
    }
    if (!previousState.deviceInfo.equals(newState.deviceInfo)) {
      listeners.queueEvent(
          Player.EVENT_DEVICE_INFO_CHANGED,
          listener -> listener.onDeviceInfoChanged(newState.deviceInfo));
    }
    if (!previousState.playlistMetadata.equals(newState.playlistMetadata)) {
      listeners.queueEvent(
          Player.EVENT_PLAYLIST_METADATA_CHANGED,
          listener -> listener.onPlaylistMetadataChanged(newState.playlistMetadata));
    }
    if (previousState.audioSessionId != newState.audioSessionId) {
      listeners.queueEvent(
          Player.EVENT_AUDIO_SESSION_ID,
          listener -> listener.onAudioSessionIdChanged(newState.audioSessionId));
    }
    if (newState.newlyRenderedFirstFrame) {
      listeners.queueEvent(Player.EVENT_RENDERED_FIRST_FRAME, Listener::onRenderedFirstFrame);
    }
    if (!previousState.surfaceSize.equals(newState.surfaceSize)) {
      listeners.queueEvent(
          Player.EVENT_SURFACE_SIZE_CHANGED,
          listener ->
              listener.onSurfaceSizeChanged(
                  newState.surfaceSize.getWidth(), newState.surfaceSize.getHeight()));
    }
    if (previousState.volume != newState.volume) {
      listeners.queueEvent(
          Player.EVENT_VOLUME_CHANGED, listener -> listener.onVolumeChanged(newState.volume));
    }
    if (previousState.deviceVolume != newState.deviceVolume
        || previousState.isDeviceMuted != newState.isDeviceMuted) {
      listeners.queueEvent(
          Player.EVENT_DEVICE_VOLUME_CHANGED,
          listener ->
              listener.onDeviceVolumeChanged(newState.deviceVolume, newState.isDeviceMuted));
    }
    if (!previousState.currentCues.equals(newState.currentCues)) {
      listeners.queueEvent(
          Player.EVENT_CUES,
          listener -> {
            listener.onCues(newState.currentCues.cues);
            listener.onCues(newState.currentCues);
          });
    }
    if (!previousState.timedMetadata.equals(newState.timedMetadata)
        && newState.timedMetadata.presentationTimeUs != C.TIME_UNSET) {
      listeners.queueEvent(
          Player.EVENT_METADATA, listener -> listener.onMetadata(newState.timedMetadata));
    }
    if (false /* TODO: add flag to know when a seek request has been resolved */) {
      listeners.queueEvent(/* eventFlag= */ C.INDEX_UNSET, Listener::onSeekProcessed);
    }
    if (!previousState.availableCommands.equals(newState.availableCommands)) {
      listeners.queueEvent(
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
          listener -> listener.onAvailableCommandsChanged(newState.availableCommands));
    }
    listeners.flushEvents();
  }

  @EnsuresNonNull("state")
  private void verifyApplicationThreadAndInitState() {
    if (Thread.currentThread() != applicationLooper.getThread()) {
      String message =
          Util.formatInvariant(
              "Player is accessed on the wrong thread.\n"
                  + "Current thread: '%s'\n"
                  + "Expected thread: '%s'\n"
                  + "See https://exoplayer.dev/issues/player-accessed-on-wrong-thread",
              Thread.currentThread().getName(), applicationLooper.getThread().getName());
      throw new IllegalStateException(message);
    }
    if (state == null) {
      // First time accessing state.
      state = getState();
    }
  }

  @RequiresNonNull("state")
  private void updateStateForPendingOperation(
      ListenableFuture<?> pendingOperation, Supplier<State> placeholderStateSupplier) {
    if (pendingOperation.isDone() && pendingOperations.isEmpty()) {
      updateStateAndInformListeners(getState());
    } else {
      pendingOperations.add(pendingOperation);
      State suggestedPlaceholderState = placeholderStateSupplier.get();
      updateStateAndInformListeners(getPlaceholderState(suggestedPlaceholderState));
      pendingOperation.addListener(
          () -> {
            castNonNull(state); // Already checked by method @RequiresNonNull pre-condition.
            pendingOperations.remove(pendingOperation);
            if (pendingOperations.isEmpty()) {
              updateStateAndInformListeners(getState());
            }
          },
          this::postOrRunOnApplicationHandler);
    }
  }

  private void postOrRunOnApplicationHandler(Runnable runnable) {
    if (applicationHandler.getLooper() == Looper.myLooper()) {
      runnable.run();
    } else {
      applicationHandler.post(runnable);
    }
  }

  private static boolean isPlaying(State state) {
    return state.playWhenReady
        && state.playbackState == Player.STATE_READY
        && state.playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  private static Tracks getCurrentTracksInternal(State state) {
    return state.playlistItems.isEmpty()
        ? Tracks.EMPTY
        : state.playlistItems.get(state.currentMediaItemIndex).tracks;
  }

  private static MediaMetadata getMediaMetadataInternal(State state) {
    return state.playlistItems.isEmpty()
        ? MediaMetadata.EMPTY
        : state.playlistItems.get(state.currentMediaItemIndex).combinedMediaMetadata;
  }

  private static int getCurrentPeriodIndexInternal(State state, Timeline.Window window) {
    if (state.currentPeriodIndex != C.INDEX_UNSET) {
      return state.currentPeriodIndex;
    }
    if (state.timeline.isEmpty()) {
      return state.currentMediaItemIndex;
    }
    return state.timeline.getWindow(state.currentMediaItemIndex, window).firstPeriodIndex;
  }

  private static @Player.TimelineChangeReason int getTimelineChangeReason(
      List<PlaylistItem> previousPlaylist, List<PlaylistItem> newPlaylist) {
    if (previousPlaylist.size() != newPlaylist.size()) {
      return Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
    }
    for (int i = 0; i < previousPlaylist.size(); i++) {
      if (!previousPlaylist.get(i).uid.equals(newPlaylist.get(i).uid)) {
        return Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
      }
    }
    return Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE;
  }

  private static int getPositionDiscontinuityReason(
      State previousState, State newState, Timeline.Window window, Timeline.Period period) {
    if (newState.hasPositionDiscontinuity) {
      // We were asked to report a discontinuity.
      return newState.positionDiscontinuityReason;
    }
    if (previousState.playlistItems.isEmpty()) {
      // First change from an empty timeline is not reported as a discontinuity.
      return C.INDEX_UNSET;
    }
    if (newState.playlistItems.isEmpty()) {
      // The playlist became empty.
      return Player.DISCONTINUITY_REASON_REMOVE;
    }
    Object previousPeriodUid =
        previousState.timeline.getUidOfPeriod(getCurrentPeriodIndexInternal(previousState, window));
    Object newPeriodUid =
        newState.timeline.getUidOfPeriod(getCurrentPeriodIndexInternal(newState, window));
    if (!newPeriodUid.equals(previousPeriodUid)
        || previousState.currentAdGroupIndex != newState.currentAdGroupIndex
        || previousState.currentAdIndexInAdGroup != newState.currentAdIndexInAdGroup) {
      // The current period or ad inside a period changed.
      if (newState.timeline.getIndexOfPeriod(previousPeriodUid) == C.INDEX_UNSET) {
        // The previous period no longer exists.
        return Player.DISCONTINUITY_REASON_REMOVE;
      }
      // Check if reached the previous period's or ad's duration to assume an auto-transition.
      long previousPositionMs =
          getCurrentPeriodOrAdPositionMs(previousState, previousPeriodUid, period);
      long previousDurationMs = getPeriodOrAdDurationMs(previousState, previousPeriodUid, period);
      return previousDurationMs != C.TIME_UNSET && previousPositionMs >= previousDurationMs
          ? Player.DISCONTINUITY_REASON_AUTO_TRANSITION
          : Player.DISCONTINUITY_REASON_SKIP;
    }
    // We are in the same content period or ad. Check if the position deviates more than a
    // reasonable threshold from the previous one.
    long previousPositionMs =
        getCurrentPeriodOrAdPositionMs(previousState, previousPeriodUid, period);
    long newPositionMs = getCurrentPeriodOrAdPositionMs(newState, newPeriodUid, period);
    if (Math.abs(previousPositionMs - newPositionMs) < POSITION_DISCONTINUITY_THRESHOLD_MS) {
      return C.INDEX_UNSET;
    }
    // Check if we previously reached the end of the item to assume an auto-repetition.
    long previousDurationMs = getPeriodOrAdDurationMs(previousState, previousPeriodUid, period);
    return previousDurationMs != C.TIME_UNSET && previousPositionMs >= previousDurationMs
        ? Player.DISCONTINUITY_REASON_AUTO_TRANSITION
        : Player.DISCONTINUITY_REASON_INTERNAL;
  }

  private static long getCurrentPeriodOrAdPositionMs(
      State state, Object currentPeriodUid, Timeline.Period period) {
    return state.currentAdGroupIndex != C.INDEX_UNSET
        ? state.adPositionMsSupplier.get()
        : state.contentPositionMsSupplier.get()
            - state.timeline.getPeriodByUid(currentPeriodUid, period).getPositionInWindowMs();
  }

  private static long getPeriodOrAdDurationMs(
      State state, Object currentPeriodUid, Timeline.Period period) {
    state.timeline.getPeriodByUid(currentPeriodUid, period);
    long periodOrAdDurationUs =
        state.currentAdGroupIndex == C.INDEX_UNSET
            ? period.durationUs
            : period.getAdDurationUs(state.currentAdGroupIndex, state.currentAdIndexInAdGroup);
    return usToMs(periodOrAdDurationUs);
  }

  private static PositionInfo getPositionInfo(
      State state,
      boolean useDiscontinuityPosition,
      Timeline.Window window,
      Timeline.Period period) {
    @Nullable Object windowUid = null;
    @Nullable Object periodUid = null;
    int mediaItemIndex = state.currentMediaItemIndex;
    int periodIndex = C.INDEX_UNSET;
    @Nullable MediaItem mediaItem = null;
    if (!state.timeline.isEmpty()) {
      periodIndex = getCurrentPeriodIndexInternal(state, window);
      periodUid = state.timeline.getPeriod(periodIndex, period, /* setIds= */ true).uid;
      windowUid = state.timeline.getWindow(mediaItemIndex, window).uid;
      mediaItem = window.mediaItem;
    }
    long contentPositionMs;
    long positionMs;
    if (useDiscontinuityPosition) {
      positionMs = state.discontinuityPositionMs;
      contentPositionMs =
          state.currentAdGroupIndex == C.INDEX_UNSET
              ? positionMs
              : state.contentPositionMsSupplier.get();
    } else {
      contentPositionMs = state.contentPositionMsSupplier.get();
      positionMs =
          state.currentAdGroupIndex != C.INDEX_UNSET
              ? state.adPositionMsSupplier.get()
              : contentPositionMs;
    }
    return new PositionInfo(
        windowUid,
        mediaItemIndex,
        mediaItem,
        periodUid,
        periodIndex,
        positionMs,
        contentPositionMs,
        state.currentAdGroupIndex,
        state.currentAdIndexInAdGroup);
  }

  private static int getMediaItemTransitionReason(
      State previousState,
      State newState,
      int positionDiscontinuityReason,
      Timeline.Window window) {
    Timeline previousTimeline = previousState.timeline;
    Timeline newTimeline = newState.timeline;
    if (newTimeline.isEmpty() && previousTimeline.isEmpty()) {
      return C.INDEX_UNSET;
    } else if (newTimeline.isEmpty() != previousTimeline.isEmpty()) {
      return MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
    }
    Object previousWindowUid =
        previousState.timeline.getWindow(previousState.currentMediaItemIndex, window).uid;
    Object newWindowUid = newState.timeline.getWindow(newState.currentMediaItemIndex, window).uid;
    if (!previousWindowUid.equals(newWindowUid)) {
      if (positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
        return MEDIA_ITEM_TRANSITION_REASON_AUTO;
      } else if (positionDiscontinuityReason == DISCONTINUITY_REASON_SEEK) {
        return MEDIA_ITEM_TRANSITION_REASON_SEEK;
      } else {
        return MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
      }
    }
    // Only mark changes within the current item as a transition if we are repeating automatically
    // or via a seek to next/previous.
    if (positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION
        && previousState.contentPositionMsSupplier.get()
            > newState.contentPositionMsSupplier.get()) {
      return MEDIA_ITEM_TRANSITION_REASON_REPEAT;
    }
    if (positionDiscontinuityReason == DISCONTINUITY_REASON_SEEK
        && /* TODO: mark repetition seeks to detect this case */ false) {
      return MEDIA_ITEM_TRANSITION_REASON_SEEK;
    }
    return C.INDEX_UNSET;
  }
}
