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
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.Player.TimelineChangeReason;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.DefaultMediaItemConverter;
import com.google.android.exoplayer2.ext.cast.MediaItem;
import com.google.android.exoplayer2.ext.cast.MediaItemConverter;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastContext;
import java.util.ArrayList;
import java.util.Map;

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

  private final PlayerView localPlayerView;
  private final PlayerControlView castControlView;
  private final DefaultTrackSelector trackSelector;
  private final SimpleExoPlayer exoPlayer;
  private final CastPlayer castPlayer;
  private final ArrayList<MediaItem> mediaQueue;
  private final Listener listener;
  private final ConcatenatingMediaSource concatenatingMediaSource;
  private final MediaItemConverter mediaItemConverter;

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
    concatenatingMediaSource = new ConcatenatingMediaSource();
    mediaItemConverter = new DefaultMediaItemConverter();

    trackSelector = new DefaultTrackSelector(context);
    exoPlayer = new SimpleExoPlayer.Builder(context).setTrackSelector(trackSelector).build();
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
    if (currentPlayer == castPlayer) {
      castPlayer.addItems(mediaItemConverter.toMediaQueueItem(item));
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
      return false;
    }
    concatenatingMediaSource.removeMediaSource(itemIndex);
    if (currentPlayer == castPlayer) {
      if (castPlayer.getPlaybackState() != Player.STATE_IDLE) {
        Timeline castTimeline = castPlayer.getCurrentTimeline();
        if (castTimeline.getPeriodCount() <= itemIndex) {
          return false;
        }
        castPlayer.removeItem((int) castTimeline.getPeriod(itemIndex, new Period()).id);
      }
    }
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
   * @param toIndex The target index of the item in the queue.
   * @return Whether the item move was successful.
   */
  public boolean moveItem(MediaItem item, int toIndex) {
    int fromIndex = mediaQueue.indexOf(item);
    if (fromIndex == -1) {
      return false;
    }
    // Player update.
    concatenatingMediaSource.moveMediaSource(fromIndex, toIndex);
    if (currentPlayer == castPlayer && castPlayer.getPlaybackState() != Player.STATE_IDLE) {
      Timeline castTimeline = castPlayer.getCurrentTimeline();
      int periodCount = castTimeline.getPeriodCount();
      if (periodCount <= fromIndex || periodCount <= toIndex) {
        return false;
      }
      int elementId = (int) castTimeline.getPeriod(fromIndex, new Period()).id;
      castPlayer.moveItem(elementId, toIndex);
    }

    mediaQueue.add(toIndex, mediaQueue.remove(fromIndex));

    // Index update.
    if (fromIndex == currentItemIndex) {
      maybeSetCurrentItemAndNotify(toIndex);
    } else if (fromIndex < currentItemIndex && toIndex >= currentItemIndex) {
      maybeSetCurrentItemAndNotify(currentItemIndex - 1);
    } else if (fromIndex > currentItemIndex && toIndex <= currentItemIndex) {
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
    concatenatingMediaSource.clear();
    castPlayer.setSessionAvailabilityListener(null);
    castPlayer.release();
    localPlayerView.setPlayer(null);
    exoPlayer.release();
  }

  // Player.EventListener implementation.

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
    updateCurrentItemIndex();
  }

  @Override
  public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
    updateCurrentItemIndex();
  }

  @Override
  public void onTimelineChanged(Timeline timeline, @TimelineChangeReason int reason) {
    updateCurrentItemIndex();
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
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
    if (currentPlayer == exoPlayer) {
      exoPlayer.prepare(concatenatingMediaSource);
    }

    // Playback transition.
    if (windowIndex != C.INDEX_UNSET) {
      setCurrentItem(windowIndex, playbackPositionMs, playWhenReady);
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
    if (currentPlayer == castPlayer && castPlayer.getCurrentTimeline().isEmpty()) {
      MediaQueueItem[] items = new MediaQueueItem[mediaQueue.size()];
      for (int i = 0; i < items.length; i++) {
        items[i] = mediaItemConverter.toMediaQueueItem(mediaQueue.get(i));
      }
      castPlayer.loadItems(items, itemIndex, positionMs, Player.REPEAT_MODE_OFF);
    } else {
      currentPlayer.seekTo(itemIndex, positionMs);
      currentPlayer.setPlayWhenReady(playWhenReady);
    }
  }

  private void maybeSetCurrentItemAndNotify(int currentItemIndex) {
    if (this.currentItemIndex != currentItemIndex) {
      int oldIndex = this.currentItemIndex;
      this.currentItemIndex = currentItemIndex;
      listener.onQueuePositionChanged(oldIndex, currentItemIndex);
    }
  }

  private MediaSource buildMediaSource(MediaItem item) {
    Uri uri = item.uri;
    String mimeType = item.mimeType;
    if (mimeType == null) {
      throw new IllegalArgumentException("mimeType is required");
    }

    DrmSessionManager<ExoMediaCrypto> drmSessionManager =
        DrmSessionManager.getDummyDrmSessionManager();
    MediaItem.DrmConfiguration drmConfiguration = item.drmConfiguration;
    if (drmConfiguration != null && Util.SDK_INT >= 18) {
      String licenseServerUrl =
          drmConfiguration.licenseUri != null ? drmConfiguration.licenseUri.toString() : "";
      HttpMediaDrmCallback drmCallback =
          new HttpMediaDrmCallback(licenseServerUrl, DATA_SOURCE_FACTORY);
      for (Map.Entry<String, String> requestHeader : drmConfiguration.requestHeaders.entrySet()) {
        drmCallback.setKeyRequestProperty(requestHeader.getKey(), requestHeader.getValue());
      }
      drmSessionManager =
          new DefaultDrmSessionManager.Builder()
              .setMultiSession(/* multiSession= */ true)
              .setUuidAndExoMediaDrmProvider(
                  drmConfiguration.uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
              .build(drmCallback);
    }

    MediaSource createdMediaSource;
    switch (mimeType) {
      case DemoUtil.MIME_TYPE_SS:
        createdMediaSource =
            new SsMediaSource.Factory(DATA_SOURCE_FACTORY)
                .setDrmSessionManager(drmSessionManager)
                .createMediaSource(uri);
        break;
      case DemoUtil.MIME_TYPE_DASH:
        createdMediaSource =
            new DashMediaSource.Factory(DATA_SOURCE_FACTORY)
                .setDrmSessionManager(drmSessionManager)
                .createMediaSource(uri);
        break;
      case DemoUtil.MIME_TYPE_HLS:
        createdMediaSource =
            new HlsMediaSource.Factory(DATA_SOURCE_FACTORY)
                .setDrmSessionManager(drmSessionManager)
                .createMediaSource(uri);
        break;
      case DemoUtil.MIME_TYPE_VIDEO_MP4:
        createdMediaSource =
            new ProgressiveMediaSource.Factory(DATA_SOURCE_FACTORY)
                .setDrmSessionManager(drmSessionManager)
                .createMediaSource(uri);
        break;
      default:
        throw new IllegalArgumentException("mimeType is unsupported: " + mimeType);
    }
    return createdMediaSource;
  }
}
