/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.castdemo;

import android.content.Context;
import android.view.KeyEvent;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.TimelineChangeReason;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.ui.StyledPlayerControlView;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.gms.cast.framework.CastContext;
import java.util.ArrayList;

/** Manages players and an internal media queue for the demo app. */
/* package */ class PlayerManager implements Player.Listener, SessionAvailabilityListener {

  /** Listener for events. */
  interface Listener {

    /** Called when the currently played item of the media queue changes. */
    void onQueuePositionChanged(int previousIndex, int newIndex);

    /**
     * Called when a track of type {@code trackType} is not supported by the player.
     *
     * @param trackType One of the {@link C}{@code .TRACK_TYPE_*} constants.
     */
    void onUnsupportedTrack(int trackType);
  }

  private final Context context;
  private final StyledPlayerView playerView;
  private final Player localPlayer;
  private final CastPlayer castPlayer;
  private final ArrayList<MediaItem> mediaQueue;
  private final Listener listener;

  private Tracks lastSeenTracks;
  private int currentItemIndex;
  private Player currentPlayer;

  /**
   * Creates a new manager for {@link ExoPlayer} and {@link CastPlayer}.
   *
   * @param context A {@link Context}.
   * @param listener A {@link Listener} for queue position changes.
   * @param playerView The {@link StyledPlayerView} for playback.
   * @param castContext The {@link CastContext}.
   */
  public PlayerManager(
      Context context, Listener listener, StyledPlayerView playerView, CastContext castContext) {
    this.context = context;
    this.listener = listener;
    this.playerView = playerView;
    mediaQueue = new ArrayList<>();
    currentItemIndex = C.INDEX_UNSET;

    localPlayer = new ExoPlayer.Builder(context).build();
    localPlayer.addListener(this);

    castPlayer = new CastPlayer(castContext);
    castPlayer.addListener(this);
    castPlayer.setSessionAvailabilityListener(this);

    setCurrentPlayer(castPlayer.isCastSessionAvailable() ? castPlayer : localPlayer);
  }

  // Queue manipulation methods.

  /**
   * Plays a specified queue item in the current player.
   *
   * @param itemIndex The index of the item to play.
   */
  public void selectQueueItem(int itemIndex) {
    setCurrentItem(itemIndex);
  }

  /** Returns the index of the currently played item. */
  public int getCurrentItemIndex() {
    return currentItemIndex;
  }

  /**
   * Appends {@code item} to the media queue.
   *
   * @param item The {@link MediaItem} to append.
   */
  public void addItem(MediaItem item) {
    mediaQueue.add(item);
    currentPlayer.addMediaItem(item);
  }

  /** Returns the size of the media queue. */
  public int getMediaQueueSize() {
    return mediaQueue.size();
  }

  /**
   * Returns the item at the given index in the media queue.
   *
   * @param position The index of the item.
   * @return The item at the given index in the media queue.
   */
  public MediaItem getItem(int position) {
    return mediaQueue.get(position);
  }

  /**
   * Removes the item at the given index from the media queue.
   *
   * @param item The item to remove.
   * @return Whether the removal was successful.
   */
  public boolean removeItem(MediaItem item) {
    int itemIndex = mediaQueue.indexOf(item);
    if (itemIndex == -1) {
      return false;
    }
    currentPlayer.removeMediaItem(itemIndex);
    mediaQueue.remove(itemIndex);
    if (itemIndex == currentItemIndex && itemIndex == mediaQueue.size()) {
      maybeSetCurrentItemAndNotify(C.INDEX_UNSET);
    } else if (itemIndex < currentItemIndex) {
      maybeSetCurrentItemAndNotify(currentItemIndex - 1);
    }
    return true;
  }

  /**
   * Moves an item within the queue.
   *
   * @param item The item to move.
   * @param newIndex The target index of the item in the queue.
   * @return Whether the item move was successful.
   */
  public boolean moveItem(MediaItem item, int newIndex) {
    int fromIndex = mediaQueue.indexOf(item);
    if (fromIndex == -1) {
      return false;
    }

    // Player update.
    currentPlayer.moveMediaItem(fromIndex, newIndex);
    mediaQueue.add(newIndex, mediaQueue.remove(fromIndex));

    // Index update.
    if (fromIndex == currentItemIndex) {
      maybeSetCurrentItemAndNotify(newIndex);
    } else if (fromIndex < currentItemIndex && newIndex >= currentItemIndex) {
      maybeSetCurrentItemAndNotify(currentItemIndex - 1);
    } else if (fromIndex > currentItemIndex && newIndex <= currentItemIndex) {
      maybeSetCurrentItemAndNotify(currentItemIndex + 1);
    }

    return true;
  }

  /**
   * Dispatches a given {@link KeyEvent} to the corresponding view of the current player.
   *
   * @param event The {@link KeyEvent}.
   * @return Whether the event was handled by the target view.
   */
  public boolean dispatchKeyEvent(KeyEvent event) {
    return playerView.dispatchKeyEvent(event);
  }

  /** Releases the manager and the players that it holds. */
  public void release() {
    currentItemIndex = C.INDEX_UNSET;
    mediaQueue.clear();
    castPlayer.setSessionAvailabilityListener(null);
    castPlayer.release();
    playerView.setPlayer(null);
    localPlayer.release();
  }

  // Player.Listener implementation.

  @Override
  public void onPlaybackStateChanged(@Player.State int playbackState) {
    updateCurrentItemIndex();
  }

  @Override
  public void onPositionDiscontinuity(
      Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition,
      @DiscontinuityReason int reason) {
    updateCurrentItemIndex();
  }

  @Override
  public void onTimelineChanged(Timeline timeline, @TimelineChangeReason int reason) {
    updateCurrentItemIndex();
  }

  @Override
  public void onTracksChanged(Tracks tracks) {
    if (currentPlayer != localPlayer || tracks == lastSeenTracks) {
      return;
    }
    if (tracks.containsType(C.TRACK_TYPE_VIDEO)
        && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO, /* allowExceedsCapabilities= */ true)) {
      listener.onUnsupportedTrack(C.TRACK_TYPE_VIDEO);
    }
    if (tracks.containsType(C.TRACK_TYPE_AUDIO)
        && !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO, /* allowExceedsCapabilities= */ true)) {
      listener.onUnsupportedTrack(C.TRACK_TYPE_AUDIO);
    }
    lastSeenTracks = tracks;
  }

  // CastPlayer.SessionAvailabilityListener implementation.

  @Override
  public void onCastSessionAvailable() {
    setCurrentPlayer(castPlayer);
  }

  @Override
  public void onCastSessionUnavailable() {
    setCurrentPlayer(localPlayer);
  }

  // Internal methods.

  private void updateCurrentItemIndex() {
    int playbackState = currentPlayer.getPlaybackState();
    maybeSetCurrentItemAndNotify(
        playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
            ? currentPlayer.getCurrentMediaItemIndex()
            : C.INDEX_UNSET);
  }

  private void setCurrentPlayer(Player currentPlayer) {
    if (this.currentPlayer == currentPlayer) {
      return;
    }

    playerView.setPlayer(currentPlayer);
    playerView.setControllerHideOnTouch(currentPlayer == localPlayer);
    if (currentPlayer == castPlayer) {
      playerView.setControllerShowTimeoutMs(0);
      playerView.showController();
      playerView.setDefaultArtwork(
          ResourcesCompat.getDrawable(
              context.getResources(),
              R.drawable.ic_baseline_cast_connected_400,
              /* theme= */ null));
    } else { // currentPlayer == localPlayer
      playerView.setControllerShowTimeoutMs(StyledPlayerControlView.DEFAULT_SHOW_TIMEOUT_MS);
      playerView.setDefaultArtwork(null);
    }

    // Player state management.
    long playbackPositionMs = C.TIME_UNSET;
    int currentItemIndex = C.INDEX_UNSET;
    boolean playWhenReady = false;

    Player previousPlayer = this.currentPlayer;
    if (previousPlayer != null) {
      // Save state from the previous player.
      int playbackState = previousPlayer.getPlaybackState();
      if (playbackState != Player.STATE_ENDED) {
        playbackPositionMs = previousPlayer.getCurrentPosition();
        playWhenReady = previousPlayer.getPlayWhenReady();
        currentItemIndex = previousPlayer.getCurrentMediaItemIndex();
        if (currentItemIndex != this.currentItemIndex) {
          playbackPositionMs = C.TIME_UNSET;
          currentItemIndex = this.currentItemIndex;
        }
      }
      previousPlayer.stop();
      previousPlayer.clearMediaItems();
    }

    this.currentPlayer = currentPlayer;

    // Media queue management.
    currentPlayer.setMediaItems(mediaQueue, currentItemIndex, playbackPositionMs);
    currentPlayer.setPlayWhenReady(playWhenReady);
    currentPlayer.prepare();
  }

  /**
   * Starts playback of the item at the given index.
   *
   * @param itemIndex The index of the item to play.
   */
  private void setCurrentItem(int itemIndex) {
    maybeSetCurrentItemAndNotify(itemIndex);
    if (currentPlayer.getCurrentTimeline().getWindowCount() != mediaQueue.size()) {
      // This only happens with the cast player. The receiver app in the cast device clears the
      // timeline when the last item of the timeline has been played to end.
      currentPlayer.setMediaItems(mediaQueue, itemIndex, C.TIME_UNSET);
    } else {
      currentPlayer.seekTo(itemIndex, C.TIME_UNSET);
    }
    currentPlayer.setPlayWhenReady(true);
  }

  private void maybeSetCurrentItemAndNotify(int currentItemIndex) {
    if (this.currentItemIndex != currentItemIndex) {
      int oldIndex = this.currentItemIndex;
      this.currentItemIndex = currentItemIndex;
      listener.onQueuePositionChanged(oldIndex, currentItemIndex);
    }
  }
}
