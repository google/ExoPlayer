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
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.util.Assertions;
import java.util.List;

/**
 * Wraps an ExoPlayer {@link Player} instance and provides methods and notifies events like those in
 * the {@link SessionPlayer} API.
 */
/* package */ final class PlayerWrapper {

  /** Listener for player wrapper events. */
  public interface Listener {
    /**
     * Called when the player state is changed.
     *
     * <p>This method will be called at first if multiple events should be notified at once.
     */
    void onPlayerStateChanged(/* @SessionPlayer.PlayerState */ int playerState);

    /** Called when the player is prepared. */
    void onPrepared(MediaItem mediaItem, int bufferingPercentage);

    /** Called when a seek request has completed. */
    void onSeekCompleted();

    /** Called when the player rebuffers. */
    void onBufferingStarted(MediaItem mediaItem);

    /** Called when the player becomes ready again after rebuffering. */
    void onBufferingEnded(MediaItem mediaItem, int bufferingPercentage);

    /** Called periodically with the player's buffered position as a percentage. */
    void onBufferingUpdate(MediaItem mediaItem, int bufferingPercentage);

    /** Called when current media item is changed. */
    void onCurrentMediaItemChanged(MediaItem mediaItem);

    /** Called when playback of the item list has ended. */
    void onPlaybackEnded();

    /** Called when the player encounters an error. */
    void onError(@Nullable MediaItem mediaItem);

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
  private final PlaylistManager playlistManager;
  private final PlaybackPreparer playbackPreparer;
  private final ControlDispatcher controlDispatcher;
  private final ComponentListener componentListener;

  private boolean prepared;
  private boolean rebuffering;
  private int currentWindowIndex;

  /**
   * Creates a new ExoPlayer wrapper.
   *
   * @param listener A listener for player wrapper events.
   * @param player The player to handle commands
   * @param playlistManager The playlist manager to handle playlist commands
   * @param playbackPreparer The playback preparer to prepare
   * @param controlDispatcher A {@link ControlDispatcher} that should be used for dispatching
   *     changes to the player.
   */
  PlayerWrapper(
      Listener listener,
      Player player,
      PlaylistManager playlistManager,
      PlaybackPreparer playbackPreparer,
      ControlDispatcher controlDispatcher) {
    this.listener = listener;
    this.player = player;
    this.playlistManager = playlistManager;
    this.playbackPreparer = playbackPreparer;
    this.controlDispatcher = controlDispatcher;

    componentListener = new ComponentListener();
    player.addListener(componentListener);
    @Nullable Player.AudioComponent audioComponent = player.getAudioComponent();
    if (audioComponent != null) {
      audioComponent.addAudioListener(componentListener);
    }

    handler = new PlayerHandler(player.getApplicationLooper());
    pollBufferRunnable = new PollBufferRunnable();

    currentWindowIndex = C.INDEX_UNSET;
  }

  public boolean setMediaItem(MediaItem mediaItem) {
    boolean handled = playlistManager.setMediaItem(player, mediaItem);
    if (handled) {
      currentWindowIndex = playlistManager.getCurrentMediaItemIndex(player);
    }
    return handled;
  }

  public boolean setPlaylist(List<MediaItem> playlist, @Nullable MediaMetadata metadata) {
    boolean handled = playlistManager.setPlaylist(player, playlist, metadata);
    if (handled) {
      currentWindowIndex = playlistManager.getCurrentMediaItemIndex(player);
    }
    return handled;
  }

  public boolean addPlaylistItem(int index, MediaItem item) {
    return playlistManager.addPlaylistItem(player, index, item);
  }

  public boolean removePlaylistItem(@IntRange(from = 0) int index) {
    return playlistManager.removePlaylistItem(player, index);
  }

  public boolean replacePlaylistItem(int index, MediaItem item) {
    return playlistManager.replacePlaylistItem(player, index, item);
  }

  public boolean skipToPreviousPlaylistItem() {
    return playlistManager.skipToPreviousPlaylistItem(player, controlDispatcher);
  }

  public boolean skipToNextPlaylistItem() {
    return playlistManager.skipToNextPlaylistItem(player, controlDispatcher);
  }

  public boolean skipToPlaylistItem(@IntRange(from = 0) int index) {
    return playlistManager.skipToPlaylistItem(player, controlDispatcher, index);
  }

  public boolean updatePlaylistMetadata(@Nullable MediaMetadata metadata) {
    return playlistManager.updatePlaylistMetadata(player, metadata);
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
  public List<MediaItem> getPlaylist() {
    return playlistManager.getPlaylist(player);
  }

  @Nullable
  public MediaMetadata getPlaylistMetadata() {
    return playlistManager.getPlaylistMetadata(player);
  }

  public int getRepeatMode() {
    return Utils.getRepeatMode(player.getRepeatMode());
  }

  public int getShuffleMode() {
    return Utils.getShuffleMode(player.getShuffleModeEnabled());
  }

  public int getCurrentMediaItemIndex() {
    return playlistManager.getCurrentMediaItemIndex(player);
  }

  public int getPreviousMediaItemIndex() {
    return player.getPreviousWindowIndex();
  }

  public int getNextMediaItemIndex() {
    return player.getNextWindowIndex();
  }

  @Nullable
  public MediaItem getCurrentMediaItem() {
    return playlistManager.getCurrentMediaItem(player);
  }

  public boolean prepare() {
    if (prepared) {
      return false;
    }
    playbackPreparer.preparePlayback();
    return true;
  }

  public boolean play() {
    if (player.getPlaybackState() == Player.STATE_ENDED) {
      int currentWindowIndex = playlistManager.getCurrentMediaItemIndex(player);
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
    int currentWindowIndex = playlistManager.getCurrentMediaItemIndex(player);
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

  public boolean setAudioAttributes(AudioAttributesCompat audioAttributes) {
    Player.AudioComponent audioComponent = Assertions.checkStateNotNull(player.getAudioComponent());
    audioComponent.setAudioAttributes(
        Utils.getAudioAttributes(audioAttributes), /* handleAudioFocus= */ true);
    return true;
  }

  public AudioAttributesCompat getAudioAttributes() {
    @Nullable Player.AudioComponent audioComponent = player.getAudioComponent();
    return Utils.getAudioAttributesCompat(
        audioComponent != null ? audioComponent.getAudioAttributes() : AudioAttributes.DEFAULT);
  }

  public boolean setPlaybackSpeed(float playbackSpeed) {
    player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
    return true;
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
    @Nullable List<MediaItem> playlist = getPlaylist();
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
    int currentWindowIndex = playlistManager.getCurrentMediaItemIndex(player);
    if (this.currentWindowIndex != currentWindowIndex) {
      this.currentWindowIndex = currentWindowIndex;
      MediaItem currentMediaItem =
          Assertions.checkNotNull(playlistManager.getCurrentMediaItem(player));
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

  private void handleTimelineChanged() {
    playlistManager.onTimelineChanged(player);
    listener.onPlaylistChanged();
  }

  private void handleAudioAttributesChanged(AudioAttributes audioAttributes) {
    listener.onAudioAttributesChanged(Utils.getAudioAttributesCompat(audioAttributes));
  }

  private void updateBufferingAndScheduleNextPollBuffer() {
    MediaItem mediaItem = Assertions.checkNotNull(getCurrentMediaItem());
    listener.onBufferingUpdate(mediaItem, player.getBufferedPercentage());
    handler.removeCallbacks(pollBufferRunnable);
    handler.postDelayed(pollBufferRunnable, POLL_BUFFER_INTERVAL_MS);
  }

  private void maybeNotifyBufferingEvents() {
    MediaItem mediaItem = Assertions.checkNotNull(getCurrentMediaItem());
    if (prepared && !rebuffering) {
      rebuffering = true;
      listener.onBufferingStarted(mediaItem);
    }
  }

  private void maybeNotifyReadyEvents() {
    MediaItem mediaItem = Assertions.checkNotNull(getCurrentMediaItem());
    boolean prepareComplete = !prepared;
    if (prepareComplete) {
      prepared = true;
      handlePositionDiscontinuity(Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
      listener.onPlayerStateChanged(SessionPlayer.PLAYER_STATE_PAUSED);
      listener.onPrepared(mediaItem, player.getBufferedPercentage());
    }
    if (rebuffering) {
      rebuffering = false;
      listener.onBufferingEnded(mediaItem, player.getBufferedPercentage());
    }
  }

  private void maybeNotifyEndedEvents() {
    if (player.getPlayWhenReady()) {
      listener.onPlayerStateChanged(SessionPlayer.PLAYER_STATE_PAUSED);
      listener.onPlaybackEnded();
      player.setPlayWhenReady(false);
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
      handleTimelineChanged();
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
