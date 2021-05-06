/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.video.VideoSize;
import java.util.List;

/**
 * A {@link Player} that forwards operations to another {@link Player}. Applications can use this
 * class to suppress or modify specific operations, by overriding the respective methods.
 *
 * <p>An application can {@link #setDisabledCommands disable available commands}. When the wrapped
 * player advertises available commands, either with {@link Player#isCommandAvailable(int)} or with
 * {@link Listener#onAvailableCommandsChanged}, the disabled commands will be filtered out.
 */
public class ForwardingPlayer extends BasePlayer {
  private final Player player;
  private final Clock clock;
  @Nullable private ForwardingListener forwardingListener;

  private Commands disabledCommands;
  @Nullable private Commands unfilteredCommands;
  @Nullable private Commands filteredCommands;

  /** Creates a new instance that forwards all operations to {@code player}. */
  public ForwardingPlayer(Player player) {
    this(player, Clock.DEFAULT);
  }

  @VisibleForTesting
  /* package */ ForwardingPlayer(Player player, Clock clock) {
    this.player = player;
    this.clock = clock;
    this.disabledCommands = Commands.EMPTY;
  }

  /**
   * Sets the disabled {@link Commands}.
   *
   * <p>When querying for available commands with {@link #isCommandAvailable(int)}, or when the
   * wrapped player advertises available commands with {@link Listener#isCommandAvailable}, disabled
   * commands will be filtered out.
   */
  public void setDisabledCommands(Commands commands) {
    checkState(player.getApplicationLooper().equals(Looper.myLooper()));
    disabledCommands = commands;
    filteredCommands = null;
    if (forwardingListener != null) {
      forwardingListener.maybeAdvertiseAvailableCommands();
    }
  }

  /** Returns the disabled commands. */
  public Commands getDisabledCommands() {
    return disabledCommands;
  }

  @Override
  public Looper getApplicationLooper() {
    return player.getApplicationLooper();
  }

  @Override
  public void addListener(EventListener listener) {
    addListener(new EventListenerWrapper(listener));
  }

  @Override
  public void addListener(Listener listener) {
    if (forwardingListener == null) {
      forwardingListener = new ForwardingListener(this);
    }
    if (!forwardingListener.isRegistered()) {
      forwardingListener.registerTo(player);
    }
    forwardingListener.addListener(listener);
  }

  @Override
  public void removeListener(EventListener listener) {
    removeListener(new EventListenerWrapper(listener));
  }

  @Override
  public void removeListener(Listener listener) {
    if (forwardingListener == null) {
      return;
    }
    forwardingListener.removeListener(listener);
    if (!forwardingListener.hasListeners()) {
      forwardingListener.unregisterFrom(player);
    }
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
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    player.addMediaItems(index, mediaItems);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    player.moveMediaItems(fromIndex, toIndex, newIndex);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    player.removeMediaItems(fromIndex, toIndex);
  }

  @Override
  public Commands getAvailableCommands() {
    Commands commands = player.getAvailableCommands();
    if (filteredCommands == null || !commands.equals(unfilteredCommands)) {
      filteredCommands = filterCommands(commands, disabledCommands);
      unfilteredCommands = commands;
    }
    return filteredCommands;
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

  @Nullable
  @Override
  public ExoPlaybackException getPlayerError() {
    return player.getPlayerError();
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
  public void seekTo(int windowIndex, long positionMs) {
    player.seekTo(windowIndex, positionMs);
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    player.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return player.getPlaybackParameters();
  }

  @Override
  @SuppressWarnings("deprecation") // Forwarding to deprecated method.
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

  @Override
  public List<Metadata> getCurrentStaticMetadata() {
    return player.getCurrentStaticMetadata();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    return player.getMediaMetadata();
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
  public long getTotalBufferedDuration() {
    return player.getTotalBufferedDuration();
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

  /**
   * Wraps a {@link Listener} and intercepts {@link EventListener#onAvailableCommandsChanged} in
   * order to filter disabled commands. All other operations are forwarded to the wrapped {@link
   * Listener}.
   */
  private static class ForwardingListener implements Listener {
    private final ForwardingPlayer player;
    private final ListenerSet<Listener> listeners;
    private boolean registered;
    private Commands lastReceivedCommands;
    private Commands lastAdvertisedCommands;

    public ForwardingListener(ForwardingPlayer forwardingPlayer) {
      this.player = forwardingPlayer;
      listeners =
          new ListenerSet<>(
              forwardingPlayer.player.getApplicationLooper(),
              forwardingPlayer.clock,
              (listener, flags) -> listener.onEvents(forwardingPlayer, new Events(flags)));
      lastReceivedCommands = Commands.EMPTY;
      lastAdvertisedCommands = Commands.EMPTY;
    }

    public void registerTo(Player player) {
      checkState(!registered);
      player.addListener(this);
      lastReceivedCommands = player.getAvailableCommands();
      lastAdvertisedCommands = lastReceivedCommands;
      registered = true;
    }

    public void unregisterFrom(Player player) {
      checkState(registered);
      player.removeListener(this);
      registered = false;
    }

    public boolean isRegistered() {
      return registered;
    }

    public void addListener(Listener listener) {
      listeners.add(listener);
    }

    public void removeListener(Listener listener) {
      listeners.remove(listener);
    }

    public boolean hasListeners() {
      return listeners.size() > 0;
    }

    // VideoListener callbacks
    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
      listeners.sendEvent(C.INDEX_UNSET, listener -> listener.onVideoSizeChanged(videoSize));
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onVideoSizeChanged(
        int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
      listeners.sendEvent(
          C.INDEX_UNSET,
          listener ->
              listener.onVideoSizeChanged(
                  width, height, unappliedRotationDegrees, pixelWidthHeightRatio));
    }

    @Override
    public void onSurfaceSizeChanged(int width, int height) {
      listeners.sendEvent(C.INDEX_UNSET, listener -> listener.onSurfaceSizeChanged(width, height));
    }

    @Override
    public void onRenderedFirstFrame() {
      listeners.sendEvent(C.INDEX_UNSET, Listener::onRenderedFirstFrame);
    }

    // AudioListener callbacks

    @Override
    public void onAudioSessionIdChanged(int audioSessionId) {
      listeners.sendEvent(
          C.INDEX_UNSET, listener -> listener.onAudioSessionIdChanged(audioSessionId));
    }

    @Override
    public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
      listeners.sendEvent(
          C.INDEX_UNSET, listener -> listener.onAudioAttributesChanged(audioAttributes));
    }

    @Override
    public void onVolumeChanged(float volume) {
      listeners.sendEvent(C.INDEX_UNSET, listener -> listener.onVolumeChanged(volume));
    }

    @Override
    public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
      listeners.sendEvent(
          C.INDEX_UNSET, listener -> listener.onSkipSilenceEnabledChanged(skipSilenceEnabled));
    }

    // TextOutput callbacks

    @Override
    public void onCues(List<Cue> cues) {
      listeners.sendEvent(C.INDEX_UNSET, listener -> listener.onCues(cues));
    }

    // MetadataOutput callbacks

    @Override
    public void onMetadata(Metadata metadata) {
      listeners.sendEvent(C.INDEX_UNSET, listener -> listener.onMetadata(metadata));
    }

    // DeviceListener callbacks

    @Override
    public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
      listeners.sendEvent(C.INDEX_UNSET, listener -> listener.onDeviceInfoChanged(deviceInfo));
    }

    @Override
    public void onDeviceVolumeChanged(int volume, boolean muted) {
      listeners.sendEvent(C.INDEX_UNSET, listener -> listener.onDeviceVolumeChanged(volume, muted));
    }

    // EventListener callbacks

    @Override
    public void onTimelineChanged(Timeline timeline, @TimelineChangeReason int reason) {
      listeners.sendEvent(
          EVENT_TIMELINE_CHANGED, listener -> listener.onTimelineChanged(timeline, reason));
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onTimelineChanged(
        Timeline timeline, @Nullable Object manifest, @TimelineChangeReason int reason) {
      listeners.sendEvent(
          EVENT_TIMELINE_CHANGED,
          listener -> listener.onTimelineChanged(timeline, manifest, reason));
    }

    @Override
    public void onMediaItemTransition(
        @Nullable MediaItem mediaItem, @MediaItemTransitionReason int reason) {
      listeners.sendEvent(
          EVENT_MEDIA_ITEM_TRANSITION,
          listener -> listener.onMediaItemTransition(mediaItem, reason));
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      listeners.sendEvent(
          EVENT_TRACKS_CHANGED, listener -> listener.onTracksChanged(trackGroups, trackSelections));
    }

    @Override
    public void onStaticMetadataChanged(List<Metadata> metadataList) {
      listeners.sendEvent(
          EVENT_STATIC_METADATA_CHANGED,
          listener -> listener.onStaticMetadataChanged(metadataList));
    }

    @Override
    public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
      listeners.sendEvent(
          EVENT_MEDIA_METADATA_CHANGED, listener -> listener.onMediaMetadataChanged(mediaMetadata));
    }

    @Override
    public void onIsLoadingChanged(boolean isLoading) {
      listeners.sendEvent(
          EVENT_IS_LOADING_CHANGED, listener -> listener.onIsLoadingChanged(isLoading));
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onLoadingChanged(boolean isLoading) {
      listeners.sendEvent(
          EVENT_IS_LOADING_CHANGED, listener -> listener.onLoadingChanged(isLoading));
    }

    @Override
    public void onAvailableCommandsChanged(Commands availableCommands) {
      lastReceivedCommands = availableCommands;
      maybeAdvertiseAvailableCommands();
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onPlayerStateChanged(boolean playWhenReady, @State int playbackState) {
      listeners.sendEvent(
          C.INDEX_UNSET, listener -> listener.onPlayerStateChanged(playWhenReady, playbackState));
    }

    @Override
    public void onPlaybackStateChanged(@State int state) {
      listeners.sendEvent(
          EVENT_PLAYBACK_STATE_CHANGED, listener -> listener.onPlaybackStateChanged(state));
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, @State int reason) {
      listeners.sendEvent(
          EVENT_PLAY_WHEN_READY_CHANGED,
          listener -> listener.onPlayWhenReadyChanged(playWhenReady, reason));
    }

    @Override
    public void onPlaybackSuppressionReasonChanged(
        @PlaybackSuppressionReason int playbackSuppressionReason) {
      listeners.sendEvent(
          EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
          listener -> listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason));
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
      listeners.sendEvent(
          EVENT_IS_PLAYING_CHANGED, listener -> listener.onIsPlayingChanged(isPlaying));
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode int repeatMode) {
      listeners.sendEvent(
          EVENT_REPEAT_MODE_CHANGED, listener -> listener.onRepeatModeChanged(repeatMode));
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
      listeners.sendEvent(
          EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          listener -> listener.onShuffleModeEnabledChanged(shuffleModeEnabled));
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      listeners.sendEvent(EVENT_PLAYER_ERROR, listener -> listener.onPlayerError(error));
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
      listeners.sendEvent(
          EVENT_POSITION_DISCONTINUITY, listener -> listener.onPositionDiscontinuity(reason));
    }

    @Override
    public void onPositionDiscontinuity(
        PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
      listeners.sendEvent(
          EVENT_POSITION_DISCONTINUITY,
          listener -> listener.onPositionDiscontinuity(oldPosition, newPosition, reason));
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      listeners.sendEvent(
          EVENT_PLAYBACK_PARAMETERS_CHANGED,
          listener -> listener.onPlaybackParametersChanged(playbackParameters));
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onSeekProcessed() {
      listeners.sendEvent(C.INDEX_UNSET, EventListener::onSeekProcessed);
    }

    @Override
    public void onEvents(Player player, Events events) {
      // Do nothing, individual callbacks will trigger this event on behalf of the forwarding
      // player.
    }

    public void maybeAdvertiseAvailableCommands() {
      Commands commandsToAdvertise = filterCommands(lastReceivedCommands, player.disabledCommands);
      if (!commandsToAdvertise.equals(lastAdvertisedCommands)) {
        lastAdvertisedCommands = commandsToAdvertise;
        listeners.sendEvent(
            EVENT_AVAILABLE_COMMANDS_CHANGED,
            listener -> listener.onAvailableCommandsChanged(commandsToAdvertise));
      }
    }
  }

  /**
   * Wraps an {@link EventListener} as a {@link Listener} so that it can be used by the {@link
   * ForwardingListener}.
   */
  private static class EventListenerWrapper implements Listener {
    private final EventListener listener;

    /** Wraps an {@link EventListener}. */
    public EventListenerWrapper(EventListener listener) {
      this.listener = listener;
    }

    // EventListener callbacks

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onTimelineChanged(
        Timeline timeline, @Nullable Object manifest, @TimelineChangeReason int reason) {
      listener.onTimelineChanged(timeline, manifest, reason);
    }

    @Override
    public void onTimelineChanged(Timeline timeline, @TimelineChangeReason int reason) {
      listener.onTimelineChanged(timeline, reason);
    }

    @Override
    public void onMediaItemTransition(
        @Nullable MediaItem mediaItem, @MediaItemTransitionReason int reason) {
      listener.onMediaItemTransition(mediaItem, reason);
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      listener.onTracksChanged(trackGroups, trackSelections);
    }

    @Override
    public void onStaticMetadataChanged(List<Metadata> metadataList) {
      listener.onStaticMetadataChanged(metadataList);
    }

    @Override
    public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
      listener.onMediaMetadataChanged(mediaMetadata);
    }

    @Override
    public void onIsLoadingChanged(boolean isLoading) {
      listener.onIsLoadingChanged(isLoading);
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onLoadingChanged(boolean isLoading) {
      listener.onLoadingChanged(isLoading);
    }

    @Override
    public void onAvailableCommandsChanged(Commands availableCommands) {
      listener.onAvailableCommandsChanged(availableCommands);
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onPlayerStateChanged(boolean playWhenReady, @State int playbackState) {
      listener.onPlayerStateChanged(playWhenReady, playbackState);
    }

    @Override
    public void onPlaybackStateChanged(@State int state) {
      listener.onPlaybackStateChanged(state);
    }

    @Override
    public void onPlayWhenReadyChanged(
        boolean playWhenReady, @PlayWhenReadyChangeReason int reason) {
      listener.onPlayWhenReadyChanged(playWhenReady, reason);
    }

    @Override
    public void onPlaybackSuppressionReasonChanged(
        @PlaybackSuppressionReason int playbackSuppressionReason) {
      listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
      listener.onIsPlayingChanged(isPlaying);
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode int repeatMode) {
      listener.onRepeatModeChanged(repeatMode);
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
      listener.onShuffleModeEnabledChanged(shuffleModeEnabled);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      listener.onPlayerError(error);
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
      listener.onPositionDiscontinuity(reason);
    }

    @Override
    public void onPositionDiscontinuity(
        PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
      listener.onPositionDiscontinuity(oldPosition, newPosition, reason);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      listener.onPlaybackParametersChanged(playbackParameters);
    }

    @Override
    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void onSeekProcessed() {
      listener.onSeekProcessed();
    }

    @Override
    public void onEvents(Player player, Events events) {
      listener.onEvents(player, events);
    }

    // Other Listener callbacks, they should never be invoked on this wrapper.

    @Override
    public void onMetadata(Metadata metadata) {
      throw new IllegalStateException();
    }

    @Override
    public void onCues(List<Cue> cues) {
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof EventListenerWrapper)) {
        return false;
      }

      EventListenerWrapper that = (EventListenerWrapper) o;
      return listener.equals(that.listener);
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }
  }

  /** Returns the remaining available commands after removing disabled commands. */
  private static Commands filterCommands(Commands availableCommands, Commands disabledCommands) {
    if (disabledCommands.size() == 0) {
      return availableCommands;
    }

    Commands.Builder builder = new Commands.Builder();
    for (int i = 0; i < availableCommands.size(); i++) {
      int command = availableCommands.get(i);
      builder.addIf(command, !disabledCommands.contains(command));
    }
    return builder.build();
  }
}
