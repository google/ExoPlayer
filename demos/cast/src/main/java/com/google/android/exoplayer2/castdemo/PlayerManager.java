/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.net.Uri;
import android.support.annotation.Nullable;
import android.view.KeyEvent;
import android.view.View;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.Player.TimelineChangeReason;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ext.cast.DefaultCastSessionManager;
import com.google.android.exoplayer2.ext.cast.ExoCastPlayer;
import com.google.android.exoplayer2.ext.cast.MediaItem;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.gms.cast.framework.CastContext;
import java.util.ArrayList;

/** Manages players and an internal media queue for the Cast demo app. */
/* package */ class PlayerManager implements EventListener, SessionAvailabilityListener {

  /** Listener for events. */
  public interface Listener {

    /** Called when the currently played item of the media queue changes. */
    void onQueuePositionChanged(int previousIndex, int newIndex);

    /** Called when the media queue changes due to modifications not caused by this manager. */
    void onQueueContentsExternallyChanged();

    /** Called when an error occurs in the current player. */
    void onPlayerError();
  }

  private static final String TAG = "PlayerManager";
  private static final String USER_AGENT = "ExoCastDemoPlayer";
  private static final DefaultHttpDataSourceFactory DATA_SOURCE_FACTORY =
      new DefaultHttpDataSourceFactory(USER_AGENT);

  private final PlayerView localPlayerView;
  private final PlayerControlView castControlView;
  private final SimpleExoPlayer exoPlayer;
  private final ExoCastPlayer exoCastPlayer;
  private final ArrayList<MediaItem> mediaQueue;
  private final Listener listener;
  private final ConcatenatingMediaSource concatenatingMediaSource;

  private int currentItemIndex;
  private Player currentPlayer;

  /**
   * Creates a new manager for {@link SimpleExoPlayer} and {@link ExoCastPlayer}.
   *
   * @param listener A {@link Listener}.
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
    concatenatingMediaSource = new ConcatenatingMediaSource();

    DefaultTrackSelector trackSelector = new DefaultTrackSelector();
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
    exoPlayer = ExoPlayerFactory.newSimpleInstance(context, renderersFactory, trackSelector);
    exoPlayer.addListener(this);
    localPlayerView.setPlayer(exoPlayer);

    exoCastPlayer =
        new ExoCastPlayer(
            sessionManagerListener ->
                new DefaultCastSessionManager(castContext, sessionManagerListener));
    exoCastPlayer.addListener(this);
    exoCastPlayer.setSessionAvailabilityListener(this);
    castControlView.setPlayer(exoCastPlayer);

    setCurrentPlayer(exoCastPlayer.isCastSessionAvailable() ? exoCastPlayer : exoPlayer);
  }

  // Queue manipulation methods.

  /**
   * Plays a specified queue item in the current player.
   *
   * @param itemIndex The index of the item to play.
   */
  public void selectQueueItem(int itemIndex) {
    setCurrentItem(itemIndex, C.TIME_UNSET, true);
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
    concatenatingMediaSource.addMediaSource(buildMediaSource(item));
    if (currentPlayer == exoCastPlayer) {
      exoCastPlayer.addItemsToQueue(item);
    }
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
      // This may happen if another sender app removes items while this sender app is in "swiping
      // an item" state.
      return false;
    }
    concatenatingMediaSource.removeMediaSource(itemIndex);
    mediaQueue.remove(itemIndex);
    if (currentPlayer == exoCastPlayer) {
      exoCastPlayer.removeItemFromQueue(itemIndex);
    }
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
   * @param item The item to move. This method does nothing if {@code item} is not contained in the
   *     queue.
   * @param toIndex The target index of the item in the queue. If {@code toIndex} exceeds the last
   *     position in the queue, {@code toIndex} is clamped to match the largest possible value.
   * @return True if {@code item} was contained in the queue, and {@code toIndex} was a valid
   *     position. False otherwise.
   */
  public boolean moveItem(MediaItem item, int toIndex) {
    int indexOfItem = mediaQueue.indexOf(item);
    if (indexOfItem == -1) {
      // This may happen if another sender app removes items while this sender app is in "dragging
      // an item" state.
      return false;
    }
    int clampedToIndex = Math.min(toIndex, mediaQueue.size() - 1);
    mediaQueue.add(clampedToIndex, mediaQueue.remove(indexOfItem));
    concatenatingMediaSource.moveMediaSource(indexOfItem, clampedToIndex);
    if (currentPlayer == exoCastPlayer) {
      exoCastPlayer.moveItemInQueue(indexOfItem, clampedToIndex);
    }
    // Index update.
    maybeSetCurrentItemAndNotify(currentPlayer.getCurrentWindowIndex());
    return clampedToIndex == toIndex;
  }

  // Miscellaneous methods.

  public boolean dispatchKeyEvent(KeyEvent event) {
    if (currentPlayer == exoPlayer) {
      return localPlayerView.dispatchKeyEvent(event);
    } else /* currentPlayer == exoCastPlayer */ {
      return castControlView.dispatchKeyEvent(event);
    }
  }

  /** Releases the manager and the players that it holds. */
  public void release() {
    currentItemIndex = C.INDEX_UNSET;
    mediaQueue.clear();
    concatenatingMediaSource.clear();
    exoCastPlayer.setSessionAvailabilityListener(null);
    exoCastPlayer.release();
    localPlayerView.setPlayer(null);
    exoPlayer.release();
  }

  // Player.EventListener implementation.

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    updateCurrentItemIndex();
  }

  @Override
  public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
    updateCurrentItemIndex();
  }

  @Override
  public void onTimelineChanged(
      Timeline timeline, @Nullable Object manifest, @TimelineChangeReason int reason) {
    if (currentPlayer == exoCastPlayer && reason != Player.TIMELINE_CHANGE_REASON_RESET) {
      maybeUpdateLocalQueueWithRemoteQueueAndNotify();
    }
    updateCurrentItemIndex();
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    Log.e(TAG, "The player encountered an error.", error);
    listener.onPlayerError();
  }

  // CastPlayer.SessionAvailabilityListener implementation.

  @Override
  public void onCastSessionAvailable() {
    setCurrentPlayer(exoCastPlayer);
  }

  @Override
  public void onCastSessionUnavailable() {
    setCurrentPlayer(exoPlayer);
  }

  // Internal methods.

  private void maybeUpdateLocalQueueWithRemoteQueueAndNotify() {
    Assertions.checkState(currentPlayer == exoCastPlayer);
    boolean mediaQueuesMatch = mediaQueue.size() == exoCastPlayer.getQueueSize();
    for (int i = 0; mediaQueuesMatch && i < mediaQueue.size(); i++) {
      mediaQueuesMatch = mediaQueue.get(i).uuid.equals(exoCastPlayer.getQueueItem(i).uuid);
    }
    if (mediaQueuesMatch) {
      // The media queues match. Do nothing.
      return;
    }
    mediaQueue.clear();
    concatenatingMediaSource.clear();
    for (int i = 0; i < exoCastPlayer.getQueueSize(); i++) {
      MediaItem item = exoCastPlayer.getQueueItem(i);
      mediaQueue.add(item);
      concatenatingMediaSource.addMediaSource(buildMediaSource(item));
    }
    listener.onQueueContentsExternallyChanged();
  }

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
    } else /* currentPlayer == exoCastPlayer */ {
      localPlayerView.setVisibility(View.GONE);
      castControlView.show();
    }

    // Player state management.
    long playbackPositionMs = C.TIME_UNSET;
    int windowIndex = C.INDEX_UNSET;
    boolean playWhenReady = false;
    if (this.currentPlayer != null) {
      int playbackState = this.currentPlayer.getPlaybackState();
      if (playbackState != Player.STATE_ENDED) {
        playbackPositionMs = this.currentPlayer.getCurrentPosition();
        playWhenReady = this.currentPlayer.getPlayWhenReady();
        windowIndex = this.currentPlayer.getCurrentWindowIndex();
        if (windowIndex != currentItemIndex) {
          playbackPositionMs = C.TIME_UNSET;
          windowIndex = currentItemIndex;
        }
      }
      this.currentPlayer.stop(true);
    } else {
      // This is the initial setup. No need to save any state.
    }

    this.currentPlayer = currentPlayer;

    // Media queue management.
    boolean shouldSeekInNewCurrentPlayer;
    if (currentPlayer == exoPlayer) {
      exoPlayer.prepare(concatenatingMediaSource);
      shouldSeekInNewCurrentPlayer = true;
    } else /* currentPlayer == exoCastPlayer */ {
      if (exoCastPlayer.getPlaybackState() == Player.STATE_IDLE) {
        exoCastPlayer.prepare();
      }
      if (mediaQueue.isEmpty()) {
        // Casting started with no local queue. We take the receiver app's queue as our own.
        maybeUpdateLocalQueueWithRemoteQueueAndNotify();
        shouldSeekInNewCurrentPlayer = false;
      } else {
        // Casting started when the sender app had no queue. We just load our items into the
        // receiver app's queue. If the receiver had no items in its queue, we also seek to wherever
        // the sender app was playing.
        int currentExoCastPlayerState = exoCastPlayer.getPlaybackState();
        shouldSeekInNewCurrentPlayer =
            currentExoCastPlayerState == Player.STATE_IDLE
                || currentExoCastPlayerState == Player.STATE_ENDED;
        exoCastPlayer.addItemsToQueue(mediaQueue.toArray(new MediaItem[0]));
      }
    }

    // Playback transition.
    if (shouldSeekInNewCurrentPlayer && windowIndex != C.INDEX_UNSET) {
      setCurrentItem(windowIndex, playbackPositionMs, playWhenReady);
    } else if (getMediaQueueSize() > 0) {
      maybeSetCurrentItemAndNotify(currentPlayer.getCurrentWindowIndex());
    }
  }

  /**
   * Starts playback of the item at the given position.
   *
   * @param itemIndex The index of the item to play.
   * @param positionMs The position at which playback should start.
   * @param playWhenReady Whether the player should proceed when ready to do so.
   */
  private void setCurrentItem(int itemIndex, long positionMs, boolean playWhenReady) {
    maybeSetCurrentItemAndNotify(itemIndex);
    currentPlayer.seekTo(itemIndex, positionMs);
    if (currentPlayer.getPlaybackState() == Player.STATE_IDLE) {
      if (currentPlayer == exoCastPlayer) {
        exoCastPlayer.prepare();
      } else {
        exoPlayer.prepare(concatenatingMediaSource);
      }
    }
    currentPlayer.setPlayWhenReady(playWhenReady);
  }

  private void maybeSetCurrentItemAndNotify(int currentItemIndex) {
    if (this.currentItemIndex != currentItemIndex) {
      int oldIndex = this.currentItemIndex;
      this.currentItemIndex = currentItemIndex;
      listener.onQueuePositionChanged(oldIndex, currentItemIndex);
    }
  }

  private static MediaSource buildMediaSource(MediaItem item) {
    Uri uri = item.media.uri;
    switch (item.mimeType) {
      case DemoUtil.MIME_TYPE_SS:
        return new SsMediaSource.Factory(DATA_SOURCE_FACTORY).createMediaSource(uri);
      case DemoUtil.MIME_TYPE_DASH:
        return new DashMediaSource.Factory(DATA_SOURCE_FACTORY).createMediaSource(uri);
      case DemoUtil.MIME_TYPE_HLS:
        return new HlsMediaSource.Factory(DATA_SOURCE_FACTORY).createMediaSource(uri);
      case DemoUtil.MIME_TYPE_VIDEO_MP4:
        return new ProgressiveMediaSource.Factory(DATA_SOURCE_FACTORY).createMediaSource(uri);
      default:
        {
          throw new IllegalStateException("Unsupported type: " + item.mimeType);
        }
    }
  }
}
