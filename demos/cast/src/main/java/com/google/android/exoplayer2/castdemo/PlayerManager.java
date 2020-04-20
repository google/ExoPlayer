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
import android.view.View;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.Player.TimelineChangeReason;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.gms.cast.framework.CastContext;
import java.util.ArrayList;

/** Manages players and an internal media queue for the demo app. */
/* package */ class PlayerManager implements EventListener, SessionAvailabilityListener {

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

  private static final String USER_AGENT = "ExoCastDemoPlayer";
  private static final DefaultHttpDataSourceFactory DATA_SOURCE_FACTORY =
      new DefaultHttpDataSourceFactory(USER_AGENT);

  private final DefaultMediaSourceFactory defaultMediaSourceFactory;
  private final PlayerView localPlayerView;
  private final PlayerControlView castControlView;
  private final DefaultTrackSelector trackSelector;
  private final SimpleExoPlayer exoPlayer;
  private final CastPlayer castPlayer;
  private final ArrayList<MediaItem> mediaQueue;
  private final Listener listener;

  private TrackGroupArray lastSeenTrackGroupArray;
  private int currentItemIndex;
  private Player currentPlayer;

  /**
   * Creates a new manager for {@link SimpleExoPlayer} and {@link CastPlayer}.
   *
   * @param listener A {@link Listener} for queue position changes.
   * @param localPlayerView The {@link PlayerView} for local playback.
   * @param castControlView The {@link PlayerControlView} to control remote playback.
   * @param context A {@link Context}.
   * @param castContext The {@link CastContext}.
   */
  public PlayerManager(
      Listener listener,
      PlayerView localPlayerView,
      PlayerControlView castControlView,
      Context context,
      CastContext castContext) {
    this.listener = listener;
    this.localPlayerView = localPlayerView;
    this.castControlView = castControlView;
    mediaQueue = new ArrayList<>();
    currentItemIndex = C.INDEX_UNSET;

    trackSelector = new DefaultTrackSelector(context);
    exoPlayer = new SimpleExoPlayer.Builder(context).setTrackSelector(trackSelector).build();
    defaultMediaSourceFactory = DefaultMediaSourceFactory.newInstance(context, DATA_SOURCE_FACTORY);
    exoPlayer.addListener(this);
    localPlayerView.setPlayer(exoPlayer);

    castPlayer = new CastPlayer(castContext);
    castPlayer.addListener(this);
    castPlayer.setSessionAvailabilityListener(this);
    castControlView.setPlayer(castPlayer);

    setCurrentPlayer(castPlayer.isCastSessionAvailable() ? castPlayer : exoPlayer);
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
    if (currentPlayer == exoPlayer) {
      return localPlayerView.dispatchKeyEvent(event);
    } else /* currentPlayer == castPlayer */ {
      return castControlView.dispatchKeyEvent(event);
    }
  }

  /** Releases the manager and the players that it holds. */
  public void release() {
    currentItemIndex = C.INDEX_UNSET;
    mediaQueue.clear();
    castPlayer.setSessionAvailabilityListener(null);
    castPlayer.release();
    localPlayerView.setPlayer(null);
    exoPlayer.release();
  }

  // Player.EventListener implementation.

  @Override
  public void onPlaybackStateChanged(@Player.State int playbackState) {
    updateCurrentItemIndex();
  }

  @Override
  public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
    updateCurrentItemIndex();
  }

  @Override
  public void onTimelineChanged(@NonNull Timeline timeline, @TimelineChangeReason int reason) {
    updateCurrentItemIndex();
  }

  @Override
  public void onTracksChanged(
      @NonNull TrackGroupArray trackGroups, @NonNull TrackSelectionArray trackSelections) {
    if (currentPlayer == exoPlayer && trackGroups != lastSeenTrackGroupArray) {
      MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
          trackSelector.getCurrentMappedTrackInfo();
      if (mappedTrackInfo != null) {
        if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
            == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
          listener.onUnsupportedTrack(C.TRACK_TYPE_VIDEO);
        }
        if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO)
            == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
          listener.onUnsupportedTrack(C.TRACK_TYPE_AUDIO);
        }
      }
      lastSeenTrackGroupArray = trackGroups;
    }
  }

  // CastPlayer.SessionAvailabilityListener implementation.

  @Override
  public void onCastSessionAvailable() {
    setCurrentPlayer(castPlayer);
  }

  @Override
  public void onCastSessionUnavailable() {
    setCurrentPlayer(exoPlayer);
  }

  // Internal methods.

  private void updateCurrentItemIndex() {
    int playbackState = currentPlayer.getPlaybackState();
    maybeSetCurrentItemAndNotify(
        playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
            ? currentPlayer.getCurrentWindowIndex()
            : C.INDEX_UNSET);
  }

  private void setCurrentPlayer(Player currentPlayer) {
    if (this.currentPlayer == currentPlayer) {
      return;
    }

    // View management.
    if (currentPlayer == exoPlayer) {
      localPlayerView.setVisibility(View.VISIBLE);
      castControlView.hide();
    } else /* currentPlayer == castPlayer */ {
      localPlayerView.setVisibility(View.GONE);
      castControlView.show();
    }

    // Player state management.
    long playbackPositionMs = C.TIME_UNSET;
    int windowIndex = C.INDEX_UNSET;
    boolean playWhenReady = false;

    Player previousPlayer = this.currentPlayer;
    if (previousPlayer != null) {
      // Save state from the previous player.
      int playbackState = previousPlayer.getPlaybackState();
      if (playbackState != Player.STATE_ENDED) {
        playbackPositionMs = previousPlayer.getCurrentPosition();
        playWhenReady = previousPlayer.getPlayWhenReady();
        windowIndex = previousPlayer.getCurrentWindowIndex();
        if (windowIndex != currentItemIndex) {
          playbackPositionMs = C.TIME_UNSET;
          windowIndex = currentItemIndex;
        }
      }
      previousPlayer.stop(true);
    }

    this.currentPlayer = currentPlayer;

    // Media queue management.
    currentPlayer.setMediaItems(mediaQueue, windowIndex, playbackPositionMs);
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
