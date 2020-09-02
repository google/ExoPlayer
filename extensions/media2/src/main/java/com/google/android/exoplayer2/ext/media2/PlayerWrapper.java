/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import static com.google.android.exoplayer2.util.Util.postOrRun;

import android.os.Handler;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.CallbackMediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps an ExoPlayer {@link Player} instance and provides methods and notifies events like those in
 * the {@link SessionPlayer} API.
 */
/* package */ final class PlayerWrapper {
  private static final String TAG = "PlayerWrapper";

  /** Listener for player wrapper events. */
  public interface Listener {
    /**
     * Called when the player state is changed.
     *
     * <p>This method will be called at first if multiple events should be notified at once.
     */
    void onPlayerStateChanged(/* @SessionPlayer.PlayerState */ int playerState);

    /** Called when the player is prepared. */
    void onPrepared(androidx.media2.common.MediaItem media2MediaItem, int bufferingPercentage);

    /** Called when a seek request has completed. */
    void onSeekCompleted();

    /** Called when the player rebuffers. */
    void onBufferingStarted(androidx.media2.common.MediaItem media2MediaItem);

    /** Called when the player becomes ready again after rebuffering. */
    void onBufferingEnded(
        androidx.media2.common.MediaItem media2MediaItem, int bufferingPercentage);

    /** Called periodically with the player's buffered position as a percentage. */
    void onBufferingUpdate(
        androidx.media2.common.MediaItem media2MediaItem, int bufferingPercentage);

    /** Called when current media item is changed. */
    void onCurrentMediaItemChanged(androidx.media2.common.MediaItem media2MediaItem);

    /** Called when playback of the item list has ended. */
    void onPlaybackEnded();

    /** Called when the player encounters an error. */
    void onError(@Nullable androidx.media2.common.MediaItem media2MediaItem);

    /** Called when the playlist is changed. */
    void onPlaylistChanged();

    /** Called when the shuffle mode is changed. */
    void onShuffleModeChanged(int shuffleMode);

    /** Called when the repeat mode is changed. */
    void onRepeatModeChanged(int repeatMode);

    /** Called when the audio attributes is changed. */
    void onAudioAttributesChanged(AudioAttributesCompat audioAttributes);

    /** Called when the playback speed is changed. */
    void onPlaybackSpeedChanged(float playbackSpeed);
  }

  private static final int POLL_BUFFER_INTERVAL_MS = 1000;

  private final Listener listener;
  private final Handler handler;
  private final Runnable pollBufferRunnable;

  private final Player player;
  private final MediaItemConverter mediaItemConverter;
  private final ComponentListener componentListener;

  @Nullable private MediaMetadata playlistMetadata;

  // These should be only updated in TimelineChanges.
  private final List<androidx.media2.common.MediaItem> media2Playlist;
  private final List<MediaItem> exoPlayerPlaylist;

  private ControlDispatcher controlDispatcher;
  private boolean prepared;
  private boolean rebuffering;
  private int currentWindowIndex;
  private boolean ignoreTimelineUpdates;

  /**
   * Creates a new ExoPlayer wrapper.
   *
   * @param listener A {@link Listener}.
   * @param player The {@link Player}.
   * @param mediaItemConverter The {@link MediaItemConverter}.
   */
  public PlayerWrapper(Listener listener, Player player, MediaItemConverter mediaItemConverter) {
    this.listener = listener;
    this.player = player;
    this.mediaItemConverter = mediaItemConverter;

    controlDispatcher = new DefaultControlDispatcher();
    componentListener = new ComponentListener();
    player.addListener(componentListener);
    @Nullable Player.AudioComponent audioComponent = player.getAudioComponent();
    if (audioComponent != null) {
      audioComponent.addAudioListener(componentListener);
    }

    handler = new Handler(player.getApplicationLooper());
    pollBufferRunnable = new PollBufferRunnable();

    media2Playlist = new ArrayList<>();
    exoPlayerPlaylist = new ArrayList<>();
    currentWindowIndex = C.INDEX_UNSET;

    prepared = player.getPlaybackState() != Player.STATE_IDLE;
    rebuffering = player.getPlaybackState() == Player.STATE_BUFFERING;

    updatePlaylist(player.getCurrentTimeline());
  }

  public void setControlDispatcher(ControlDispatcher controlDispatcher) {
    this.controlDispatcher = controlDispatcher;
  }

  public boolean setMediaItem(androidx.media2.common.MediaItem media2MediaItem) {
    return setPlaylist(Collections.singletonList(media2MediaItem), /* metadata= */ null);
  }

  public boolean setPlaylist(
      List<androidx.media2.common.MediaItem> playlist, @Nullable MediaMetadata metadata) {
    // Check for duplication.
    for (int i = 0; i < playlist.size(); i++) {
      androidx.media2.common.MediaItem media2MediaItem = playlist.get(i);
      Assertions.checkArgument(playlist.indexOf(media2MediaItem) == i);
    }

    this.playlistMetadata = metadata;
    List<MediaItem> exoPlayerMediaItems = new ArrayList<>();
    for (int i = 0; i < playlist.size(); i++) {
      androidx.media2.common.MediaItem media2MediaItem = playlist.get(i);
      MediaItem exoPlayerMediaItem =
          Assertions.checkNotNull(mediaItemConverter.convertToExoPlayerMediaItem(media2MediaItem));
      exoPlayerMediaItems.add(exoPlayerMediaItem);
    }

    player.setMediaItems(exoPlayerMediaItems, /* resetPosition= */ true);

    currentWindowIndex = getCurrentMediaItemIndex();
    return true;
  }

  public boolean addPlaylistItem(int index, androidx.media2.common.MediaItem media2MediaItem) {
    Assertions.checkArgument(!media2Playlist.contains(media2MediaItem));
    index = Util.constrainValue(index, 0, media2Playlist.size());

    MediaItem exoPlayerMediaItem =
        Assertions.checkNotNull(mediaItemConverter.convertToExoPlayerMediaItem(media2MediaItem));
    player.addMediaItem(index, exoPlayerMediaItem);
    return true;
  }

  public boolean removePlaylistItem(@IntRange(from = 0) int index) {
    player.removeMediaItem(index);
    return true;
  }

  public boolean replacePlaylistItem(int index, androidx.media2.common.MediaItem media2MediaItem) {
    Assertions.checkArgument(!media2Playlist.contains(media2MediaItem));
    index = Util.constrainValue(index, 0, media2Playlist.size());

    MediaItem exoPlayerMediaItemToAdd =
        Assertions.checkNotNull(mediaItemConverter.convertToExoPlayerMediaItem(media2MediaItem));

    ignoreTimelineUpdates = true;
    player.removeMediaItem(index);
    ignoreTimelineUpdates = false;
    player.addMediaItem(index, exoPlayerMediaItemToAdd);
    return true;
  }

  public boolean skipToPreviousPlaylistItem() {
    Timeline timeline = player.getCurrentTimeline();
    Assertions.checkState(!timeline.isEmpty());
    int previousWindowIndex = player.getPreviousWindowIndex();
    if (previousWindowIndex != C.INDEX_UNSET) {
      return controlDispatcher.dispatchSeekTo(player, previousWindowIndex, C.TIME_UNSET);
    }
    return false;
  }

  public boolean skipToNextPlaylistItem() {
    Timeline timeline = player.getCurrentTimeline();
    Assertions.checkState(!timeline.isEmpty());
    int nextWindowIndex = player.getNextWindowIndex();
    if (nextWindowIndex != C.INDEX_UNSET) {
      return controlDispatcher.dispatchSeekTo(player, nextWindowIndex, C.TIME_UNSET);
    }
    return false;
  }

  public boolean skipToPlaylistItem(@IntRange(from = 0) int index) {
    Timeline timeline = player.getCurrentTimeline();
    Assertions.checkState(!timeline.isEmpty());
    // Use checkState() instead of checkIndex() for throwing IllegalStateException.
    // checkIndex() throws IndexOutOfBoundsException which maps the RESULT_ERROR_BAD_VALUE
    // but RESULT_ERROR_INVALID_STATE with IllegalStateException is expected here.
    Assertions.checkState(0 <= index && index < timeline.getWindowCount());
    int windowIndex = player.getCurrentWindowIndex();
    if (windowIndex != index) {
      return controlDispatcher.dispatchSeekTo(player, index, C.TIME_UNSET);
    }
    return false;
  }

  public boolean updatePlaylistMetadata(@Nullable MediaMetadata metadata) {
    this.playlistMetadata = metadata;
    return true;
  }

  public boolean setRepeatMode(int repeatMode) {
    return controlDispatcher.dispatchSetRepeatMode(
        player, Utils.getExoPlayerRepeatMode(repeatMode));
  }

  public boolean setShuffleMode(int shuffleMode) {
    return controlDispatcher.dispatchSetShuffleModeEnabled(
        player, Utils.getExoPlayerShuffleMode(shuffleMode));
  }

  @Nullable
  public List<androidx.media2.common.MediaItem> getPlaylist() {
    return new ArrayList<>(media2Playlist);
  }

  @Nullable
  public MediaMetadata getPlaylistMetadata() {
    return playlistMetadata;
  }

  public int getRepeatMode() {
    return Utils.getRepeatMode(player.getRepeatMode());
  }

  public int getShuffleMode() {
    return Utils.getShuffleMode(player.getShuffleModeEnabled());
  }

  public int getCurrentMediaItemIndex() {
    return media2Playlist.isEmpty() ? C.INDEX_UNSET : player.getCurrentWindowIndex();
  }

  public int getPreviousMediaItemIndex() {
    return player.getPreviousWindowIndex();
  }

  public int getNextMediaItemIndex() {
    return player.getNextWindowIndex();
  }

  @Nullable
  public androidx.media2.common.MediaItem getCurrentMediaItem() {
    int index = getCurrentMediaItemIndex();
    return index == C.INDEX_UNSET ? null : media2Playlist.get(index);
  }

  public boolean prepare() {
    if (prepared) {
      return false;
    }
    player.prepare();
    return true;
  }

  public boolean play() {
    if (player.getPlaybackState() == Player.STATE_ENDED) {
      boolean seekHandled =
          controlDispatcher.dispatchSeekTo(
              player, player.getCurrentWindowIndex(), /* positionMs= */ 0);
      if (!seekHandled) {
        return false;
      }
    }
    boolean playWhenReady = player.getPlayWhenReady();
    int suppressReason = player.getPlaybackSuppressionReason();
    if (playWhenReady && suppressReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
      return false;
    }
    return controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ true);
  }

  public boolean pause() {
    boolean playWhenReady = player.getPlayWhenReady();
    int suppressReason = player.getPlaybackSuppressionReason();
    if (!playWhenReady && suppressReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
      return false;
    }
    return controlDispatcher.dispatchSetPlayWhenReady(player, /* playWhenReady= */ false);
  }

  public boolean seekTo(long position) {
    return controlDispatcher.dispatchSeekTo(player, player.getCurrentWindowIndex(), position);
  }

  public long getCurrentPosition() {
    return player.getCurrentPosition();
  }

  public long getDuration() {
    long duration = player.getDuration();
    return duration == C.TIME_UNSET ? SessionPlayer.UNKNOWN_TIME : duration;
  }

  public long getBufferedPosition() {
    return player.getBufferedPosition();
  }

  /* @SessionPlayer.PlayerState */
  private int getState() {
    if (hasError()) {
      return SessionPlayer.PLAYER_STATE_ERROR;
    }
    int state = player.getPlaybackState();
    boolean playWhenReady = player.getPlayWhenReady();
    switch (state) {
      case Player.STATE_IDLE:
        return SessionPlayer.PLAYER_STATE_IDLE;
      case Player.STATE_ENDED:
        return SessionPlayer.PLAYER_STATE_PAUSED;
      case Player.STATE_BUFFERING:
      case Player.STATE_READY:
        return playWhenReady
            ? SessionPlayer.PLAYER_STATE_PLAYING
            : SessionPlayer.PLAYER_STATE_PAUSED;
      default:
        throw new IllegalStateException();
    }
  }

  public void setAudioAttributes(AudioAttributesCompat audioAttributes) {
    Player.AudioComponent audioComponent = Assertions.checkStateNotNull(player.getAudioComponent());
    audioComponent.setAudioAttributes(
        Utils.getAudioAttributes(audioAttributes), /* handleAudioFocus= */ true);
  }

  public AudioAttributesCompat getAudioAttributes() {
    @Nullable Player.AudioComponent audioComponent = player.getAudioComponent();
    return Utils.getAudioAttributesCompat(
        audioComponent != null ? audioComponent.getAudioAttributes() : AudioAttributes.DEFAULT);
  }

  public void setPlaybackSpeed(float playbackSpeed) {
    player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
  }

  public float getPlaybackSpeed() {
    return player.getPlaybackParameters().speed;
  }

  public void reset() {
    controlDispatcher.dispatchStop(player, /* reset= */ true);
    prepared = false;
    rebuffering = false;
  }

  public void close() {
    handler.removeCallbacks(pollBufferRunnable);
    player.removeListener(componentListener);

    @Nullable Player.AudioComponent audioComponent = player.getAudioComponent();
    if (audioComponent != null) {
      audioComponent.removeAudioListener(componentListener);
    }
  }

  public boolean isCurrentMediaItemSeekable() {
    return getCurrentMediaItem() != null
        && !player.isPlayingAd()
        && player.isCurrentWindowSeekable();
  }

  public boolean canSkipToPlaylistItem() {
    @Nullable List<androidx.media2.common.MediaItem> playlist = getPlaylist();
    return playlist != null && playlist.size() > 1;
  }

  public boolean canSkipToPreviousPlaylistItem() {
    return player.hasPrevious();
  }

  public boolean canSkipToNextPlaylistItem() {
    return player.hasNext();
  }

  public boolean hasError() {
    return player.getPlayerError() != null;
  }

  private void handlePlayWhenReadyChanged() {
    listener.onPlayerStateChanged(getState());
  }

  private void handlePlayerStateChanged(@Player.State int state) {
    if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
      postOrRun(handler, pollBufferRunnable);
    } else {
      handler.removeCallbacks(pollBufferRunnable);
    }

    switch (state) {
      case Player.STATE_BUFFERING:
        maybeNotifyBufferingEvents();
        break;
      case Player.STATE_READY:
        maybeNotifyReadyEvents();
        break;
      case Player.STATE_ENDED:
        maybeNotifyEndedEvents();
        break;
      case Player.STATE_IDLE:
        // Do nothing.
        break;
      default:
        throw new IllegalStateException();
    }
  }

  private void handlePositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    int currentWindowIndex = getCurrentMediaItemIndex();
    if (this.currentWindowIndex != currentWindowIndex) {
      this.currentWindowIndex = currentWindowIndex;
      androidx.media2.common.MediaItem currentMediaItem =
          Assertions.checkNotNull(getCurrentMediaItem());
      listener.onCurrentMediaItemChanged(currentMediaItem);
    } else {
      listener.onSeekCompleted();
    }
  }

  private void handlePlayerError() {
    listener.onPlayerStateChanged(SessionPlayer.PLAYER_STATE_ERROR);
    listener.onError(getCurrentMediaItem());
  }

  private void handleRepeatModeChanged(@Player.RepeatMode int repeatMode) {
    listener.onRepeatModeChanged(Utils.getRepeatMode(repeatMode));
  }

  private void handleShuffleMode(boolean shuffleModeEnabled) {
    listener.onShuffleModeChanged(Utils.getShuffleMode(shuffleModeEnabled));
  }

  private void handlePlaybackParametersChanged(PlaybackParameters playbackParameters) {
    listener.onPlaybackSpeedChanged(playbackParameters.speed);
  }

  private void handleTimelineChanged(Timeline timeline) {
    if (ignoreTimelineUpdates) {
      return;
    }
    if (!isExoPlayerMediaItemsChanged(timeline)) {
      return;
    }
    updatePlaylist(timeline);
    listener.onPlaylistChanged();
  }

  // Check whether Timeline is changed by media item changes or not
  private boolean isExoPlayerMediaItemsChanged(Timeline timeline) {
    if (exoPlayerPlaylist.size() != timeline.getWindowCount()) {
      return true;
    }
    Timeline.Window window = new Timeline.Window();
    int windowCount = timeline.getWindowCount();
    for (int i = 0; i < windowCount; i++) {
      timeline.getWindow(i, window);
      if (!ObjectsCompat.equals(exoPlayerPlaylist.get(i), window.mediaItem)) {
        return true;
      }
    }
    return false;
  }

  private void updatePlaylist(Timeline timeline) {
    List<androidx.media2.common.MediaItem> media2MediaItemToBeRemoved =
        new ArrayList<>(media2Playlist);
    media2Playlist.clear();
    exoPlayerPlaylist.clear();

    Timeline.Window window = new Timeline.Window();
    int windowCount = timeline.getWindowCount();
    for (int i = 0; i < windowCount; i++) {
      timeline.getWindow(i, window);
      MediaItem exoPlayerMediaItem = window.mediaItem;
      androidx.media2.common.MediaItem media2MediaItem =
          Assertions.checkNotNull(mediaItemConverter.convertToMedia2MediaItem(exoPlayerMediaItem));
      exoPlayerPlaylist.add(exoPlayerMediaItem);
      media2Playlist.add(media2MediaItem);
      media2MediaItemToBeRemoved.remove(media2MediaItem);
    }

    for (androidx.media2.common.MediaItem item : media2MediaItemToBeRemoved) {
      releaseMediaItem(item);
    }
  }

  private void handleAudioAttributesChanged(AudioAttributes audioAttributes) {
    listener.onAudioAttributesChanged(Utils.getAudioAttributesCompat(audioAttributes));
  }

  private void updateBufferingAndScheduleNextPollBuffer() {
    androidx.media2.common.MediaItem media2MediaItem =
        Assertions.checkNotNull(getCurrentMediaItem());
    listener.onBufferingUpdate(media2MediaItem, player.getBufferedPercentage());
    handler.removeCallbacks(pollBufferRunnable);
    handler.postDelayed(pollBufferRunnable, POLL_BUFFER_INTERVAL_MS);
  }

  private void maybeNotifyBufferingEvents() {
    androidx.media2.common.MediaItem media2MediaItem =
        Assertions.checkNotNull(getCurrentMediaItem());
    if (prepared && !rebuffering) {
      rebuffering = true;
      listener.onBufferingStarted(media2MediaItem);
    }
  }

  private void maybeNotifyReadyEvents() {
    androidx.media2.common.MediaItem media2MediaItem =
        Assertions.checkNotNull(getCurrentMediaItem());
    boolean prepareComplete = !prepared;
    if (prepareComplete) {
      prepared = true;
      handlePositionDiscontinuity(Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
      listener.onPlayerStateChanged(SessionPlayer.PLAYER_STATE_PAUSED);
      listener.onPrepared(media2MediaItem, player.getBufferedPercentage());
    }
    if (rebuffering) {
      rebuffering = false;
      listener.onBufferingEnded(media2MediaItem, player.getBufferedPercentage());
    }
  }

  private void maybeNotifyEndedEvents() {
    if (player.getPlayWhenReady()) {
      listener.onPlayerStateChanged(SessionPlayer.PLAYER_STATE_PAUSED);
      listener.onPlaybackEnded();
      player.setPlayWhenReady(false);
    }
  }

  private void releaseMediaItem(androidx.media2.common.MediaItem media2MediaItem) {
    try {
      if (media2MediaItem instanceof CallbackMediaItem) {
        ((CallbackMediaItem) media2MediaItem).getDataSourceCallback().close();
      }
    } catch (IOException e) {
      Log.w(TAG, "Error releasing media item " + media2MediaItem, e);
    }
  }

  private final class ComponentListener implements Player.EventListener, AudioListener {

    // Player.EventListener implementation.

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
      handlePlayWhenReadyChanged();
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int state) {
      handlePlayerStateChanged(state);
    }

    @Override
    public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
      handlePositionDiscontinuity(reason);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      handlePlayerError();
    }

    @Override
    public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
      handleRepeatModeChanged(repeatMode);
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
      handleShuffleMode(shuffleModeEnabled);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      handlePlaybackParametersChanged(playbackParameters);
    }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
      handleTimelineChanged(timeline);
    }

    // AudioListener implementation.

    @Override
    public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
      handleAudioAttributesChanged(audioAttributes);
    }
  }

  private final class PollBufferRunnable implements Runnable {
    @Override
    public void run() {
      updateBufferingAndScheduleNextPollBuffer();
    }
  }
}
