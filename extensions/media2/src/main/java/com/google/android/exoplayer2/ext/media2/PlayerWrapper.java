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

    /** Called when the player starts buffering. */
    void onBufferingStarted(androidx.media2.common.MediaItem media2MediaItem);

    /** Called when the player becomes ready again after buffering started. */
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
  private int sessionPlayerState;
  private boolean prepared;
  @Nullable private androidx.media2.common.MediaItem bufferingItem;
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

    handler = new Handler(player.getApplicationLooper());
    pollBufferRunnable = new PollBufferRunnable();

    media2Playlist = new ArrayList<>();
    exoPlayerPlaylist = new ArrayList<>();
    currentWindowIndex = C.INDEX_UNSET;
    updatePlaylist(player.getCurrentTimeline());

    sessionPlayerState = evaluateSessionPlayerState();
    @Player.State int playbackState = player.getPlaybackState();
    prepared = playbackState != Player.STATE_IDLE;
    if (playbackState == Player.STATE_BUFFERING) {
      bufferingItem = getCurrentMediaItem();
    }
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
    if (player.getMediaItemCount() <= index) {
      return false;
    }
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

  public boolean movePlaylistItem(
      @IntRange(from = 0) int fromIndex, @IntRange(from = 0) int toIndex) {
    int itemCount = player.getMediaItemCount();
    if (!(fromIndex < itemCount && toIndex < itemCount)) {
      return false;
    }
    if (fromIndex == toIndex) {
      return true;
    }
    player.moveMediaItem(fromIndex, toIndex);
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
  private int evaluateSessionPlayerState() {
    if (hasError()) {
      return SessionPlayer.PLAYER_STATE_ERROR;
    }
    int state = player.getPlaybackState();
    boolean playWhenReady = player.getPlayWhenReady();
    switch (state) {
      case Player.STATE_IDLE:
        return SessionPlayer.PLAYER_STATE_IDLE;
      case Player.STATE_ENDED:
        return player.getCurrentMediaItem() == null
            ? SessionPlayer.PLAYER_STATE_IDLE
            : SessionPlayer.PLAYER_STATE_PAUSED;
      case Player.STATE_BUFFERING:
      case Player.STATE_READY:
        return playWhenReady
            ? SessionPlayer.PLAYER_STATE_PLAYING
            : SessionPlayer.PLAYER_STATE_PAUSED;
      default:
        throw new IllegalStateException();
    }
  }

  private void updateSessionPlayerState() {
    int newState = evaluateSessionPlayerState();
    if (sessionPlayerState != newState) {
      sessionPlayerState = newState;
      listener.onPlayerStateChanged(newState);
      if (newState == SessionPlayer.PLAYER_STATE_ERROR) {
        listener.onError(getCurrentMediaItem());
      }
    }
  }

  private void updateBufferingState(boolean isBuffering) {
    if (isBuffering) {
      androidx.media2.common.MediaItem curMediaItem = getCurrentMediaItem();
      if (prepared && (bufferingItem == null || !bufferingItem.equals(curMediaItem))) {
        bufferingItem = getCurrentMediaItem();
        listener.onBufferingStarted(Assertions.checkNotNull(bufferingItem));
      }
    } else if (bufferingItem != null) {
      listener.onBufferingEnded(bufferingItem, player.getBufferedPercentage());
      bufferingItem = null;
    }
  }

  private void handlePlayerStateChanged() {
    updateSessionPlayerState();

    int playbackState = player.getPlaybackState();
    handler.removeCallbacks(pollBufferRunnable);

    switch (playbackState) {
      case Player.STATE_IDLE:
        prepared = false;
        updateBufferingState(/* isBuffering= */ false);
        break;
      case Player.STATE_BUFFERING:
        updateBufferingState(/* isBuffering= */ true);
        postOrRun(handler, pollBufferRunnable);
        break;
      case Player.STATE_READY:
        if (!prepared) {
          prepared = true;
          handlePositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
          listener.onPrepared(
              Assertions.checkNotNull(getCurrentMediaItem()), player.getBufferedPercentage());
        }
        updateBufferingState(/* isBuffering= */ false);
        postOrRun(handler, pollBufferRunnable);
        break;
      case Player.STATE_ENDED:
        if (player.getCurrentMediaItem() != null) {
          listener.onPlaybackEnded();
        }
        player.setPlayWhenReady(false);
        updateBufferingState(/* isBuffering= */ false);
        break;
    }
  }

  public void setAudioAttributes(AudioAttributesCompat audioAttributes) {
    // Player interface doesn't support setting audio attributes.
  }

  public AudioAttributesCompat getAudioAttributes() {
    AudioAttributes audioAttributes = AudioAttributes.DEFAULT;
    if (player.isCommandAvailable(Player.COMMAND_GET_AUDIO_ATTRIBUTES)) {
      audioAttributes = player.getAudioAttributes();
    }
    return Utils.getAudioAttributesCompat(audioAttributes);
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
    bufferingItem = null;
  }

  public void close() {
    handler.removeCallbacks(pollBufferRunnable);
    player.removeListener(componentListener);
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

  private void handlePositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    int currentWindowIndex = getCurrentMediaItemIndex();
    if (this.currentWindowIndex != currentWindowIndex) {
      this.currentWindowIndex = currentWindowIndex;
      if (currentWindowIndex != C.INDEX_UNSET) {
        androidx.media2.common.MediaItem currentMediaItem =
            Assertions.checkNotNull(getCurrentMediaItem());
        listener.onCurrentMediaItemChanged(currentMediaItem);
      }
    } else {
      listener.onSeekCompleted();
    }
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
      if (!Util.areEqual(exoPlayerPlaylist.get(i), window.mediaItem)) {
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

  private void updateBufferingAndScheduleNextPollBuffer() {
    androidx.media2.common.MediaItem media2MediaItem =
        Assertions.checkNotNull(getCurrentMediaItem());
    listener.onBufferingUpdate(media2MediaItem, player.getBufferedPercentage());
    handler.removeCallbacks(pollBufferRunnable);
    handler.postDelayed(pollBufferRunnable, POLL_BUFFER_INTERVAL_MS);
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

  private final class ComponentListener implements Player.Listener {

    // Player.EventListener implementation.

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
      updateSessionPlayerState();
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int state) {
      handlePlayerStateChanged();
    }

    @Override
    public void onPositionDiscontinuity(
        Player.PositionInfo oldPosition,
        Player.PositionInfo newPosition,
        @Player.DiscontinuityReason int reason) {
      handlePositionDiscontinuity(reason);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      updateSessionPlayerState();
    }

    @Override
    public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
      listener.onRepeatModeChanged(Utils.getRepeatMode(repeatMode));
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
      listener.onShuffleModeChanged(Utils.getShuffleMode(shuffleModeEnabled));
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      listener.onPlaybackSpeedChanged(playbackParameters.speed);
    }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
      if (ignoreTimelineUpdates) {
        return;
      }
      if (!isExoPlayerMediaItemsChanged(timeline)) {
        return;
      }
      updatePlaylist(timeline);
      listener.onPlaylistChanged();
    }

    // AudioListener implementation.

    @Override
    public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
      listener.onAudioAttributesChanged(Utils.getAudioAttributesCompat(audioAttributes));
    }
  }

  private final class PollBufferRunnable implements Runnable {
    @Override
    public void run() {
      updateBufferingAndScheduleNextPollBuffer();
    }
  }
}
