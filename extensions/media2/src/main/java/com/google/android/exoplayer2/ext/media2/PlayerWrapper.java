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

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.CallbackMediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
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
    void onPrepared(androidx.media2.common.MediaItem androidXMediaItem, int bufferingPercentage);

    /** Called when a seek request has completed. */
    void onSeekCompleted();

    /** Called when the player rebuffers. */
    void onBufferingStarted(androidx.media2.common.MediaItem androidXMediaItem);

    /** Called when the player becomes ready again after rebuffering. */
    void onBufferingEnded(
        androidx.media2.common.MediaItem androidXMediaItem, int bufferingPercentage);

    /** Called periodically with the player's buffered position as a percentage. */
    void onBufferingUpdate(
        androidx.media2.common.MediaItem androidXMediaItem, int bufferingPercentage);

    /** Called when current media item is changed. */
    void onCurrentMediaItemChanged(androidx.media2.common.MediaItem androidXMediaItem);

    /** Called when playback of the item list has ended. */
    void onPlaybackEnded();

    /** Called when the player encounters an error. */
    void onError(@Nullable androidx.media2.common.MediaItem androidXMediaItem);

    /** Called when the playlist is changed */
    void onPlaylistChanged();

    /** Called when the shuffle mode is changed */
    void onShuffleModeChanged(int shuffleMode);

    /** Called when the repeat mode is changed */
    void onRepeatModeChanged(int repeatMode);

    /** Called when the audio attributes is changed */
    void onAudioAttributesChanged(AudioAttributesCompat audioAttributes);

    /** Called when the playback speed is changed */
    void onPlaybackSpeedChanged(float playbackSpeed);
  }

  private static final int POLL_BUFFER_INTERVAL_MS = 1000;

  private final Listener listener;
  private final PlayerHandler handler;
  private final Runnable pollBufferRunnable;

  private final Player player;
  private final MediaItemConverter mediaItemConverter;
  private final ControlDispatcher controlDispatcher;
  private final ComponentListener componentListener;

  private final List<androidx.media2.common.MediaItem> cachedPlaylist;
  @Nullable private MediaMetadata playlistMetadata;
  private final List<MediaItem> cachedMediaItems;

  private boolean prepared;
  private boolean rebuffering;
  private int currentWindowIndex;
  private boolean loggedUnexpectedTimelineChanges;
  private boolean ignoreTimelineUpdates;

  /**
   * Creates a new ExoPlayer wrapper.
   *
   * @param listener A {@link Listener}.
   * @param player The {@link Player}.
   * @param mediaItemConverter The {@link MediaItemConverter}.
   * @param controlDispatcher A {@link ControlDispatcher}.
   */
  public PlayerWrapper(
      Listener listener,
      Player player,
      MediaItemConverter mediaItemConverter,
      ControlDispatcher controlDispatcher) {
    this.listener = listener;
    this.player = player;
    this.mediaItemConverter = mediaItemConverter;
    this.controlDispatcher = controlDispatcher;

    componentListener = new ComponentListener();
    player.addListener(componentListener);
    @Nullable Player.AudioComponent audioComponent = player.getAudioComponent();
    if (audioComponent != null) {
      audioComponent.addAudioListener(componentListener);
    }

    handler = new PlayerHandler(player.getApplicationLooper());
    pollBufferRunnable = new PollBufferRunnable();

    cachedPlaylist = new ArrayList<>();
    cachedMediaItems = new ArrayList<>();
    currentWindowIndex = C.INDEX_UNSET;
  }

  public boolean setMediaItem(androidx.media2.common.MediaItem androidXMediaItem) {
    return setPlaylist(Collections.singletonList(androidXMediaItem), /* metadata= */ null);
  }

  public boolean setPlaylist(
      List<androidx.media2.common.MediaItem> playlist, @Nullable MediaMetadata metadata) {
    // Check for duplication.
    for (int i = 0; i < playlist.size(); i++) {
      androidx.media2.common.MediaItem androidXMediaItem = playlist.get(i);
      Assertions.checkArgument(playlist.indexOf(androidXMediaItem) == i);
    }

    this.cachedPlaylist.clear();
    this.cachedPlaylist.addAll(playlist);
    this.playlistMetadata = metadata;
    this.cachedMediaItems.clear();
    List<MediaItem> exoplayerMediaItems = new ArrayList<>();
    for (int i = 0; i < playlist.size(); i++) {
      androidx.media2.common.MediaItem androidXMediaItem = playlist.get(i);
      MediaItem exoplayerMediaItem =
          Assertions.checkNotNull(
              mediaItemConverter.convertToExoPlayerMediaItem(androidXMediaItem));
      exoplayerMediaItems.add(exoplayerMediaItem);
    }
    this.cachedMediaItems.addAll(exoplayerMediaItems);

    player.setMediaItems(exoplayerMediaItems, /* resetPosition= */ true);

    currentWindowIndex = getCurrentMediaItemIndex();
    return true;
  }

  public boolean addPlaylistItem(int index, androidx.media2.common.MediaItem androidXMediaItem) {
    Assertions.checkArgument(!cachedPlaylist.contains(androidXMediaItem));
    index = Util.constrainValue(index, 0, cachedPlaylist.size());

    cachedPlaylist.add(index, androidXMediaItem);
    MediaItem exoplayerMediaItem =
        Assertions.checkNotNull(mediaItemConverter.convertToExoPlayerMediaItem(androidXMediaItem));
    cachedMediaItems.add(index, exoplayerMediaItem);
    player.addMediaItem(index, exoplayerMediaItem);
    return true;
  }

  public boolean removePlaylistItem(@IntRange(from = 0) int index) {
    androidx.media2.common.MediaItem androidXMediaItemToRemove = cachedPlaylist.remove(index);
    releaseMediaItem(androidXMediaItemToRemove);
    cachedMediaItems.remove(index);
    player.removeMediaItem(index);
    return true;
  }

  public boolean replacePlaylistItem(
      int index, androidx.media2.common.MediaItem androidXMediaItem) {
    Assertions.checkArgument(!cachedPlaylist.contains(androidXMediaItem));
    index = Util.constrainValue(index, 0, cachedPlaylist.size());

    androidx.media2.common.MediaItem androidXMediaItemToRemove = cachedPlaylist.get(index);
    cachedPlaylist.set(index, androidXMediaItem);
    releaseMediaItem(androidXMediaItemToRemove);
    MediaItem exoplayerMediaItemToAdd =
        Assertions.checkNotNull(mediaItemConverter.convertToExoPlayerMediaItem(androidXMediaItem));
    cachedMediaItems.set(index, exoplayerMediaItemToAdd);

    ignoreTimelineUpdates = true;
    player.removeMediaItem(index);
    ignoreTimelineUpdates = false;
    player.addMediaItem(index, exoplayerMediaItemToAdd);
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
  public List<androidx.media2.common.MediaItem> getCachedPlaylist() {
    return new ArrayList<>(cachedPlaylist);
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
    return cachedPlaylist.isEmpty() ? C.INDEX_UNSET : player.getCurrentWindowIndex();
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
    return (index != C.INDEX_UNSET) ? cachedPlaylist.get(index) : null;
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
      int currentWindowIndex = getCurrentMediaItemIndex();
      boolean seekHandled =
          controlDispatcher.dispatchSeekTo(player, currentWindowIndex, /* positionMs= */ 0);
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
    int currentWindowIndex = getCurrentMediaItemIndex();
    return controlDispatcher.dispatchSeekTo(player, currentWindowIndex, position);
  }

  public long getCurrentPosition() {
    Assertions.checkState(getState() != SessionPlayer.PLAYER_STATE_IDLE);
    return Math.max(0, player.getCurrentPosition());
  }

  public long getDuration() {
    Assertions.checkState(getState() != SessionPlayer.PLAYER_STATE_IDLE);
    long duration = player.getDuration();
    return duration == C.TIME_UNSET ? -1 : duration;
  }

  public long getBufferedPosition() {
    Assertions.checkState(getState() != SessionPlayer.PLAYER_STATE_IDLE);
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
    player.setPlaybackSpeed(playbackSpeed);
  }

  public float getPlaybackSpeed() {
    return player.getPlaybackSpeed();
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
    @Nullable List<androidx.media2.common.MediaItem> playlist = getCachedPlaylist();
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
      handler.postOrRun(pollBufferRunnable);
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

  private void handlePlaybackSpeedChanged(float playbackSpeed) {
    listener.onPlaybackSpeedChanged(playbackSpeed);
  }

  private void handleTimelineChanged(Timeline timeline) {
    if (ignoreTimelineUpdates) {
      return;
    }
    updateCachedPlaylistAndMediaItems(timeline);
    listener.onPlaylistChanged();
  }

  // Update cached playlist, if the ExoPlayer Player's Timeline is unexpectedly changed without
  // using SessionPlayer interface.
  private void updateCachedPlaylistAndMediaItems(Timeline currentTimeline) {
    // Check whether ExoPlayer media items are the same as expected.
    Timeline.Window window = new Timeline.Window();
    int windowCount = currentTimeline.getWindowCount();
    for (int i = 0; i < windowCount; i++) {
      currentTimeline.getWindow(i, window);
      if (i >= cachedMediaItems.size()
          || !ObjectsCompat.equals(cachedMediaItems.get(i), window.mediaItem)) {
        if (!loggedUnexpectedTimelineChanges) {
          Log.w(TAG, "Timeline was unexpectedly changed. Playlist will be rebuilt.");
          loggedUnexpectedTimelineChanges = true;
        }

        androidx.media2.common.MediaItem oldAndroidXMediaItem = cachedPlaylist.get(i);
        releaseMediaItem(oldAndroidXMediaItem);

        androidx.media2.common.MediaItem androidXMediaItem =
            Assertions.checkNotNull(
                mediaItemConverter.convertToAndroidXMediaItem(window.mediaItem));
        if (i < cachedMediaItems.size()) {
          cachedMediaItems.set(i, window.mediaItem);
          cachedPlaylist.set(i, androidXMediaItem);
        } else {
          cachedMediaItems.add(window.mediaItem);
          cachedPlaylist.add(androidXMediaItem);
        }
      }
    }
    if (cachedMediaItems.size() > windowCount) {
      if (!loggedUnexpectedTimelineChanges) {
        Log.w(TAG, "Timeline was unexpectedly changed. Playlist will be rebuilt.");
        loggedUnexpectedTimelineChanges = true;
      }
      while (cachedMediaItems.size() > windowCount) {
        cachedMediaItems.remove(windowCount);
        cachedPlaylist.remove(windowCount);
      }
    }
  }

  private void handleAudioAttributesChanged(AudioAttributes audioAttributes) {
    listener.onAudioAttributesChanged(Utils.getAudioAttributesCompat(audioAttributes));
  }

  private void updateBufferingAndScheduleNextPollBuffer() {
    androidx.media2.common.MediaItem androidXMediaItem =
        Assertions.checkNotNull(getCurrentMediaItem());
    listener.onBufferingUpdate(androidXMediaItem, player.getBufferedPercentage());
    handler.removeCallbacks(pollBufferRunnable);
    handler.postDelayed(pollBufferRunnable, POLL_BUFFER_INTERVAL_MS);
  }

  private void maybeNotifyBufferingEvents() {
    androidx.media2.common.MediaItem androidXMediaItem =
        Assertions.checkNotNull(getCurrentMediaItem());
    if (prepared && !rebuffering) {
      rebuffering = true;
      listener.onBufferingStarted(androidXMediaItem);
    }
  }

  private void maybeNotifyReadyEvents() {
    androidx.media2.common.MediaItem androidXMediaItem =
        Assertions.checkNotNull(getCurrentMediaItem());
    boolean prepareComplete = !prepared;
    if (prepareComplete) {
      prepared = true;
      handlePositionDiscontinuity(Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
      listener.onPlayerStateChanged(SessionPlayer.PLAYER_STATE_PAUSED);
      listener.onPrepared(androidXMediaItem, player.getBufferedPercentage());
    }
    if (rebuffering) {
      rebuffering = false;
      listener.onBufferingEnded(androidXMediaItem, player.getBufferedPercentage());
    }
  }

  private void maybeNotifyEndedEvents() {
    if (player.getPlayWhenReady()) {
      listener.onPlayerStateChanged(SessionPlayer.PLAYER_STATE_PAUSED);
      listener.onPlaybackEnded();
      player.setPlayWhenReady(false);
    }
  }

  private void releaseMediaItem(androidx.media2.common.MediaItem androidXMediaItem) {
    try {
      if (androidXMediaItem instanceof CallbackMediaItem) {
        ((CallbackMediaItem) androidXMediaItem).getDataSourceCallback().close();
      }
    } catch (IOException e) {
      Log.w(TAG, "Error releasing media item " + androidXMediaItem, e);
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
    public void onPlaybackSpeedChanged(float playbackSpeed) {
      handlePlaybackSpeedChanged(playbackSpeed);
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
