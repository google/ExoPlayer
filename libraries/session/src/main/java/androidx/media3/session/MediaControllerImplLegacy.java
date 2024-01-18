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
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.session.MediaUtils.calculateBufferedPercentage;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.media.VolumeProviderCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Commands;
import androidx.media3.common.Player.Events;
import androidx.media3.common.Player.Listener;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Rating;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ListenerSet;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import androidx.media3.session.LegacyConversions.ConversionException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.initialization.qual.UnderInitialization;

/* package */ class MediaControllerImplLegacy implements MediaController.MediaControllerImpl {

  private static final String TAG = "MCImplLegacy";

  private static final long AGGREGATES_CALLBACKS_WITHIN_TIMEOUT_MS = 500L;

  /* package */ final Context context;
  private final MediaController instance;

  private final SessionToken token;
  private final ListenerSet<Listener> listeners;
  private final ControllerCompatCallback controllerCompatCallback;
  private final BitmapLoader bitmapLoader;

  @Nullable private MediaControllerCompat controllerCompat;
  @Nullable private MediaBrowserCompat browserCompat;
  private boolean released;
  private boolean connected;
  private LegacyPlayerInfo legacyPlayerInfo;
  private LegacyPlayerInfo pendingLegacyPlayerInfo;
  private ControllerInfo controllerInfo;
  private long currentPositionMs;
  private long lastSetPlayWhenReadyCalledTimeMs;

  public MediaControllerImplLegacy(
      Context context,
      @UnderInitialization MediaController instance,
      SessionToken token,
      Looper applicationLooper,
      BitmapLoader bitmapLoader) {
    // Initialize default values.
    legacyPlayerInfo = new LegacyPlayerInfo();
    pendingLegacyPlayerInfo = new LegacyPlayerInfo();
    controllerInfo = new ControllerInfo();
    listeners =
        new ListenerSet<>(
            applicationLooper,
            Clock.DEFAULT,
            (listener, flags) -> listener.onEvents(getInstance(), new Events(flags)));

    // Initialize members.
    this.context = context;
    this.instance = instance;
    controllerCompatCallback = new ControllerCompatCallback(applicationLooper);
    this.token = token;
    this.bitmapLoader = bitmapLoader;
    currentPositionMs = C.TIME_UNSET;
    lastSetPlayWhenReadyCalledTimeMs = C.TIME_UNSET;
  }

  /* package */ MediaController getInstance() {
    return instance;
  }

  @Override
  public void connect(@UnderInitialization MediaControllerImplLegacy this) {
    if (this.token.getType() == SessionToken.TYPE_SESSION) {
      connectToSession((MediaSessionCompat.Token) checkStateNotNull(this.token.getBinder()));
    } else {
      connectToService();
    }
  }

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public void stop() {
    if (controllerInfo.playerInfo.playbackState == Player.STATE_IDLE) {
      return;
    }
    PlayerInfo maskedPlayerInfo =
        controllerInfo.playerInfo.copyWithSessionPositionInfo(
            createSessionPositionInfo(
                controllerInfo.playerInfo.sessionPositionInfo.positionInfo,
                /* isPlayingAd= */ false,
                controllerInfo.playerInfo.sessionPositionInfo.durationMs,
                /* bufferedPositionMs= */ controllerInfo
                    .playerInfo
                    .sessionPositionInfo
                    .positionInfo
                    .positionMs,
                /* bufferedPercentage= */ calculateBufferedPercentage(
                    controllerInfo.playerInfo.sessionPositionInfo.positionInfo.positionMs,
                    controllerInfo.playerInfo.sessionPositionInfo.durationMs),
                /* totalBufferedDurationMs= */ 0));
    if (controllerInfo.playerInfo.playbackState != Player.STATE_IDLE) {
      maskedPlayerInfo =
          maskedPlayerInfo.copyWithPlaybackState(
              Player.STATE_IDLE, /* playerError= */ controllerInfo.playerInfo.playerError);
    }
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout,
            controllerInfo.sessionExtras);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ null,
        /* mediaItemTransitionReason= */ null);

    controllerCompat.getTransportControls().stop();
  }

  @Override
  public void release() {
    if (released) {
      return;
    }
    released = true;

    if (browserCompat != null) {
      browserCompat.disconnect();
      browserCompat = null;
    }
    if (controllerCompat != null) {
      controllerCompat.unregisterCallback(controllerCompatCallback);
      controllerCompatCallback.release();
      controllerCompat = null;
    }
    connected = false;
    listeners.release();
  }

  @Override
  @Nullable
  public SessionToken getConnectedToken() {
    return connected ? token : null;
  }

  @Override
  public boolean isConnected() {
    return connected;
  }

  @Override
  public void play() {
    setPlayWhenReady(true);
  }

  @Override
  public void pause() {
    setPlayWhenReady(false);
  }

  @Override
  public void prepare() {
    if (controllerInfo.playerInfo.playbackState != Player.STATE_IDLE) {
      return;
    }
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            controllerInfo.playerInfo.copyWithPlaybackState(
                controllerInfo.playerInfo.timeline.isEmpty()
                    ? Player.STATE_ENDED
                    : Player.STATE_BUFFERING,
                /* playerError= */ null),
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout,
            controllerInfo.sessionExtras);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ null,
        /* mediaItemTransitionReason= */ null);

    if (hasMedia()) {
      initializeLegacyPlaylist();
    }
  }

  @Override
  public void seekToDefaultPosition() {
    seekToInternal(getCurrentMediaItemIndex(), /* positionMs= */ 0);
  }

  @Override
  public void seekToDefaultPosition(int mediaItemIndex) {
    seekToInternal(mediaItemIndex, /* positionMs= */ 0);
  }

  @Override
  public void seekTo(long positionMs) {
    seekToInternal(getCurrentMediaItemIndex(), positionMs);
  }

  @Override
  public void seekTo(int mediaItemIndex, long positionMs) {
    seekToInternal(mediaItemIndex, positionMs);
  }

  private void seekToInternal(int mediaItemIndex, long positionMs) {
    checkArgument(mediaItemIndex >= 0);
    int currentMediaItemIndex = getCurrentMediaItemIndex();
    Timeline currentTimeline = controllerInfo.playerInfo.timeline;
    if ((!currentTimeline.isEmpty() && mediaItemIndex >= currentTimeline.getWindowCount())
        || isPlayingAd()) {
      return;
    }
    int newMediaItemIndex = currentMediaItemIndex;
    @Nullable
    @Player.MediaItemTransitionReason
    Integer mediaItemTransitionReason = null;
    if (mediaItemIndex != currentMediaItemIndex) {
      QueueTimeline queueTimeline = (QueueTimeline) controllerInfo.playerInfo.timeline;
      long queueId = queueTimeline.getQueueId(mediaItemIndex);
      if (queueId != QueueItem.UNKNOWN_ID) {
        controllerCompat.getTransportControls().skipToQueueItem(queueId);
        newMediaItemIndex = mediaItemIndex;
        mediaItemTransitionReason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK;
      } else {
        Log.w(
            TAG,
            "Cannot seek to new media item due to the missing queue Id at media item,"
                + " mediaItemIndex="
                + mediaItemIndex);
      }
    }
    @Nullable
    @Player.DiscontinuityReason
    Integer discontinuityReason;
    long currentPositionMs = getCurrentPosition();
    long newPositionMs;
    if (positionMs == C.TIME_UNSET) {
      newPositionMs = currentPositionMs;
      discontinuityReason = null;
    } else {
      controllerCompat.getTransportControls().seekTo(positionMs);
      newPositionMs = positionMs;
      discontinuityReason = Player.DISCONTINUITY_REASON_SEEK;
    }

    long newDurationMs;
    long newBufferedPositionMs;
    int newBufferedPercentage;
    long newTotalBufferedDurationMs;
    if (mediaItemTransitionReason == null) {
      // Follows the ExoPlayerImpl's state masking for seek within the current item.
      long oldBufferedPositionMs = getBufferedPosition();
      newDurationMs = getDuration();
      newBufferedPositionMs =
          (newPositionMs < currentPositionMs)
              ? newPositionMs
              : max(newPositionMs, oldBufferedPositionMs);
      newBufferedPercentage =
          (newDurationMs == C.TIME_UNSET)
              ? 0
              : (int) (newBufferedPositionMs * 100L / newDurationMs);
      newTotalBufferedDurationMs = newBufferedPositionMs - newPositionMs;
    } else {
      newDurationMs = C.TIME_UNSET;
      newBufferedPositionMs = 0L;
      newBufferedPercentage = 0;
      newTotalBufferedDurationMs = 0L;
    }
    PositionInfo positionInfo =
        createPositionInfo(
            newMediaItemIndex,
            !currentTimeline.isEmpty()
                ? currentTimeline.getWindow(newMediaItemIndex, new Window()).mediaItem
                : null,
            newPositionMs,
            /* isPlayingAd= */ false);
    PlayerInfo maskedPlayerInfo =
        controllerInfo.playerInfo.copyWithSessionPositionInfo(
            createSessionPositionInfo(
                positionInfo,
                /* isPlayingAd= */ false,
                newDurationMs,
                newBufferedPositionMs,
                newBufferedPercentage,
                newTotalBufferedDurationMs));
    if (maskedPlayerInfo.playbackState != Player.STATE_IDLE) {
      maskedPlayerInfo =
          maskedPlayerInfo.copyWithPlaybackState(Player.STATE_BUFFERING, /* playerError= */ null);
    }
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout,
            controllerInfo.sessionExtras);
    updateStateMaskedControllerInfo(
        maskedControllerInfo, discontinuityReason, mediaItemTransitionReason);
  }

  @Override
  public long getSeekBackIncrement() {
    return controllerInfo.playerInfo.seekBackIncrementMs;
  }

  @Override
  public void seekBack() {
    // To be consistent with handling KEYCODE_MEDIA_REWIND in MediaSessionCompat
    controllerCompat.getTransportControls().rewind();
  }

  @Override
  public long getSeekForwardIncrement() {
    return controllerInfo.playerInfo.seekForwardIncrementMs;
  }

  @Override
  public void seekForward() {
    // To be consistent with handling KEYCODE_MEDIA_FAST_FORWARD in MediaSessionCompat
    controllerCompat.getTransportControls().fastForward();
  }

  @Override
  @Nullable
  public PendingIntent getSessionActivity() {
    return controllerCompat.getSessionActivity();
  }

  @Override
  public ImmutableList<CommandButton> getCustomLayout() {
    return controllerInfo.customLayout;
  }

  @Override
  public Bundle getSessionExtras() {
    return controllerInfo.sessionExtras;
  }

  @Override
  @Nullable
  public PlaybackException getPlayerError() {
    return controllerInfo.playerInfo.playerError;
  }

  @Override
  public long getDuration() {
    return controllerInfo.playerInfo.sessionPositionInfo.durationMs;
  }

  @Override
  public long getCurrentPosition() {
    currentPositionMs =
        MediaUtils.getUpdatedCurrentPositionMs(
            controllerInfo.playerInfo,
            currentPositionMs,
            lastSetPlayWhenReadyCalledTimeMs,
            getInstance().getTimeDiffMs());
    return currentPositionMs;
  }

  @Override
  public long getBufferedPosition() {
    return controllerInfo.playerInfo.sessionPositionInfo.bufferedPositionMs;
  }

  @Override
  public int getBufferedPercentage() {
    return controllerInfo.playerInfo.sessionPositionInfo.bufferedPercentage;
  }

  @Override
  public long getTotalBufferedDuration() {
    return controllerInfo.playerInfo.sessionPositionInfo.totalBufferedDurationMs;
  }

  @Override
  public long getCurrentLiveOffset() {
    // We can't know whether the content is live or not.
    return C.TIME_UNSET;
  }

  @Override
  public long getContentDuration() {
    return getDuration();
  }

  @Override
  public long getContentPosition() {
    return getCurrentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    return getBufferedPosition();
  }

  @Override
  public boolean isPlayingAd() {
    return controllerInfo.playerInfo.sessionPositionInfo.isPlayingAd;
  }

  @Override
  public int getCurrentAdGroupIndex() {
    // Not supported
    return C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    // Not supported
    return C.INDEX_UNSET;
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return controllerInfo.playerInfo.playbackParameters;
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    return controllerInfo.playerInfo.audioAttributes;
  }

  @Override
  public ListenableFuture<SessionResult> setRating(String mediaId, Rating rating) {
    @Nullable
    String currentMediaItemMediaId =
        legacyPlayerInfo.mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
    if (mediaId.equals(currentMediaItemMediaId)) {
      controllerCompat
          .getTransportControls()
          .setRating(LegacyConversions.convertToRatingCompat(rating));
    }
    return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
  }

  @Override
  public ListenableFuture<SessionResult> setRating(Rating rating) {
    controllerCompat
        .getTransportControls()
        .setRating(LegacyConversions.convertToRatingCompat(rating));
    return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    PlaybackParameters currentPlaybackParameters = getPlaybackParameters();
    if (!playbackParameters.equals(currentPlaybackParameters)) {
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithPlaybackParameters(playbackParameters),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout,
              controllerInfo.sessionExtras);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ null,
          /* mediaItemTransitionReason= */ null);
    }

    controllerCompat.getTransportControls().setPlaybackSpeed(playbackParameters.speed);
  }

  @Override
  public void setPlaybackSpeed(float speed) {
    PlaybackParameters currentPlaybackParameters = getPlaybackParameters();
    if (speed != currentPlaybackParameters.speed) {
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithPlaybackParameters(new PlaybackParameters(speed)),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout,
              controllerInfo.sessionExtras);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ null,
          /* mediaItemTransitionReason= */ null);
    }

    controllerCompat.getTransportControls().setPlaybackSpeed(speed);
  }

  @Override
  public ListenableFuture<SessionResult> sendCustomCommand(SessionCommand command, Bundle args) {
    if (controllerInfo.availableSessionCommands.contains(command)) {
      controllerCompat.getTransportControls().sendCustomAction(command.customAction, args);
      return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
    }
    SettableFuture<SessionResult> result = SettableFuture.create();
    ResultReceiver cb =
        new ResultReceiver(getInstance().applicationHandler) {
          @Override
          protected void onReceiveResult(int resultCode, Bundle resultData) {
            result.set(
                new SessionResult(
                    resultCode, /* extras= */ resultData == null ? Bundle.EMPTY : resultData));
          }
        };
    controllerCompat.sendCommand(command.customAction, args, cb);
    return result;
  }

  @Override
  public Timeline getCurrentTimeline() {
    return controllerInfo.playerInfo.timeline;
  }

  @Override
  public void setMediaItem(MediaItem mediaItem) {
    setMediaItem(mediaItem, /* startPositionMs= */ C.TIME_UNSET);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    setMediaItems(ImmutableList.of(mediaItem), /* startIndex= */ 0, startPositionMs);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    setMediaItem(mediaItem);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
    setMediaItems(mediaItems, /* startIndex= */ 0, /* startPositionMs= */ C.TIME_UNSET);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    setMediaItems(mediaItems);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    if (mediaItems.isEmpty()) {
      clearMediaItems();
      return;
    }
    QueueTimeline newQueueTimeline =
        QueueTimeline.DEFAULT.copyWithNewMediaItems(/* index= */ 0, mediaItems);
    if (startPositionMs == C.TIME_UNSET) {
      // Assume a default start position of 0 until we know more.
      startPositionMs = 0;
    }
    PlayerInfo maskedPlayerInfo =
        controllerInfo.playerInfo.copyWithTimelineAndSessionPositionInfo(
            newQueueTimeline,
            createSessionPositionInfo(
                createPositionInfo(
                    startIndex,
                    mediaItems.get(startIndex),
                    startPositionMs,
                    /* isPlayingAd= */ false),
                /* isPlayingAd= */ false,
                /* durationMs= */ C.TIME_UNSET,
                /* bufferedPositionMs= */ 0,
                /* bufferedPercentage= */ 0,
                /* totalBufferedDurationMs= */ 0),
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout,
            controllerInfo.sessionExtras);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ null,
        /* mediaItemTransitionReason= */ null);
    if (isPrepared()) {
      initializeLegacyPlaylist();
    }
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata playlistMetadata) {
    Log.w(TAG, "Session doesn't support setting playlist metadata");
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    return controllerInfo.playerInfo.playlistMetadata;
  }

  @Override
  public void addMediaItem(MediaItem mediaItem) {
    addMediaItems(Integer.MAX_VALUE, Collections.singletonList(mediaItem));
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    addMediaItems(index, Collections.singletonList(mediaItem));
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    addMediaItems(Integer.MAX_VALUE, mediaItems);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    checkArgument(index >= 0);
    if (mediaItems.isEmpty()) {
      return;
    }
    QueueTimeline queueTimeline = (QueueTimeline) controllerInfo.playerInfo.timeline;
    if (queueTimeline.isEmpty()) {
      // Handle initial items in setMediaItems to ensure initial legacy session commands are called.
      setMediaItems(mediaItems);
      return;
    }

    index = min(index, getCurrentTimeline().getWindowCount());
    QueueTimeline newQueueTimeline = queueTimeline.copyWithNewMediaItems(index, mediaItems);
    int currentMediaItemIndex = getCurrentMediaItemIndex();
    int newCurrentMediaItemIndex =
        calculateCurrentItemIndexAfterAddItems(currentMediaItemIndex, index, mediaItems.size());
    PlayerInfo maskedPlayerInfo =
        controllerInfo.playerInfo.copyWithTimelineAndMediaItemIndex(
            newQueueTimeline,
            newCurrentMediaItemIndex,
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout,
            controllerInfo.sessionExtras);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ null,
        /* mediaItemTransitionReason= */ null);

    if (isPrepared()) {
      addQueueItems(mediaItems, index);
    }
  }

  @Override
  public void removeMediaItem(int index) {
    removeMediaItems(/* fromIndex= */ index, /* toIndex= */ index + 1);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    checkArgument(fromIndex >= 0 && toIndex >= fromIndex);
    int windowCount = getCurrentTimeline().getWindowCount();
    toIndex = min(toIndex, windowCount);
    if (fromIndex >= windowCount || fromIndex == toIndex) {
      return;
    }

    QueueTimeline queueTimeline = (QueueTimeline) controllerInfo.playerInfo.timeline;
    QueueTimeline newQueueTimeline = queueTimeline.copyWithRemovedMediaItems(fromIndex, toIndex);
    int currentMediaItemIndex = getCurrentMediaItemIndex();
    int newCurrentMediaItemIndex =
        calculateCurrentItemIndexAfterRemoveItems(currentMediaItemIndex, fromIndex, toIndex);
    if (newCurrentMediaItemIndex == C.INDEX_UNSET) {
      newCurrentMediaItemIndex =
          Util.constrainValue(fromIndex, /* min= */ 0, newQueueTimeline.getWindowCount() - 1);
      // TODO: b/302114474 - This also needs to reset the current position.
      Log.w(
          TAG,
          "Currently playing item is removed. Assumes item at "
              + newCurrentMediaItemIndex
              + " is the"
              + " new current item");
    }
    PlayerInfo maskedPlayerInfo =
        controllerInfo.playerInfo.copyWithTimelineAndMediaItemIndex(
            newQueueTimeline,
            newCurrentMediaItemIndex,
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout,
            controllerInfo.sessionExtras);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ null,
        /* mediaItemTransitionReason= */ null);

    if (isPrepared()) {
      for (int i = fromIndex; i < toIndex && i < legacyPlayerInfo.queue.size(); i++) {
        controllerCompat.removeQueueItem(legacyPlayerInfo.queue.get(i).getDescription());
      }
    }
  }

  @Override
  public void clearMediaItems() {
    removeMediaItems(/* fromIndex= */ 0, /* toIndex= */ Integer.MAX_VALUE);
  }

  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    moveMediaItems(/* fromIndex= */ currentIndex, /* toIndex= */ currentIndex + 1, newIndex);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    checkArgument(fromIndex >= 0 && fromIndex <= toIndex && newIndex >= 0);
    QueueTimeline queueTimeline = (QueueTimeline) controllerInfo.playerInfo.timeline;
    int size = queueTimeline.getWindowCount();
    toIndex = min(toIndex, size);
    int moveItemsSize = toIndex - fromIndex;
    int lastItemIndexAfterRemove = size - moveItemsSize - 1;
    newIndex = min(newIndex, lastItemIndexAfterRemove + 1);
    if (fromIndex >= size || fromIndex == toIndex || fromIndex == newIndex) {
      return;
    }

    int currentMediaItemIndex = getCurrentMediaItemIndex();
    int currentMediaItemIndexAfterRemove =
        calculateCurrentItemIndexAfterRemoveItems(currentMediaItemIndex, fromIndex, toIndex);
    if (currentMediaItemIndexAfterRemove == C.INDEX_UNSET) {
      currentMediaItemIndexAfterRemove =
          Util.constrainValue(fromIndex, /* min= */ 0, /* max= */ lastItemIndexAfterRemove);
      Log.w(
          TAG,
          "Currently playing item will be removed and added back to mimic move."
              + " Assumes item at "
              + currentMediaItemIndexAfterRemove
              + " would be the new current item");
    }
    int newCurrentMediaItemIndex =
        calculateCurrentItemIndexAfterAddItems(
            currentMediaItemIndexAfterRemove, newIndex, moveItemsSize);

    QueueTimeline newQueueTimeline =
        queueTimeline.copyWithMovedMediaItems(fromIndex, toIndex, newIndex);
    PlayerInfo maskedPlayerInfo =
        controllerInfo.playerInfo.copyWithTimelineAndMediaItemIndex(
            newQueueTimeline,
            newCurrentMediaItemIndex,
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout,
            controllerInfo.sessionExtras);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ null,
        /* mediaItemTransitionReason= */ null);

    if (isPrepared()) {
      ArrayList<QueueItem> moveItems = new ArrayList<>();
      for (int i = 0; i < (toIndex - fromIndex); i++) {
        moveItems.add(legacyPlayerInfo.queue.get(fromIndex));
        controllerCompat.removeQueueItem(legacyPlayerInfo.queue.get(fromIndex).getDescription());
      }
      for (int i = 0; i < moveItems.size(); i++) {
        QueueItem item = moveItems.get(i);
        controllerCompat.addQueueItem(item.getDescription(), i + newIndex);
      }
    }
  }

  @Override
  public void replaceMediaItem(int index, MediaItem mediaItem) {
    replaceMediaItems(
        /* fromIndex= */ index, /* toIndex= */ index + 1, ImmutableList.of(mediaItem));
  }

  @Override
  public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    checkArgument(fromIndex >= 0 && fromIndex <= toIndex);
    QueueTimeline queueTimeline = (QueueTimeline) controllerInfo.playerInfo.timeline;
    int size = queueTimeline.getWindowCount();
    if (fromIndex > size) {
      return;
    }
    toIndex = min(toIndex, size);
    addMediaItems(toIndex, mediaItems);
    removeMediaItems(fromIndex, toIndex);
  }

  @Override
  public int getCurrentPeriodIndex() {
    return getCurrentMediaItemIndex();
  }

  @Override
  public int getCurrentMediaItemIndex() {
    return controllerInfo.playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex;
  }

  @Override
  public int getPreviousMediaItemIndex() {
    return C.INDEX_UNSET;
  }

  @Override
  public int getNextMediaItemIndex() {
    return C.INDEX_UNSET;
  }

  @Override
  public boolean hasPreviousMediaItem() {
    return connected;
  }

  @Override
  public boolean hasNextMediaItem() {
    return connected;
  }

  @Override
  public void seekToPreviousMediaItem() {
    // Intentionally don't do state masking when current media item index is uncertain.
    controllerCompat.getTransportControls().skipToPrevious();
  }

  @Override
  public void seekToNextMediaItem() {
    // Intentionally don't do state masking when current media item index is uncertain.
    controllerCompat.getTransportControls().skipToNext();
  }

  @Override
  public void seekToPrevious() {
    // Intentionally don't do state masking when current media item index is uncertain.
    controllerCompat.getTransportControls().skipToPrevious();
  }

  @Override
  public void seekToNext() {
    // Intentionally don't do state masking when current media item index is uncertain.
    controllerCompat.getTransportControls().skipToNext();
  }

  @Override
  public long getMaxSeekToPreviousPosition() {
    return 0L;
  }

  @Override
  @Player.RepeatMode
  public int getRepeatMode() {
    return controllerInfo.playerInfo.repeatMode;
  }

  @Override
  public void setRepeatMode(@Player.RepeatMode int repeatMode) {
    @Player.RepeatMode int currentRepeatMode = getRepeatMode();
    if (repeatMode != currentRepeatMode) {
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithRepeatMode(repeatMode),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout,
              controllerInfo.sessionExtras);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ null,
          /* mediaItemTransitionReason= */ null);
    }

    controllerCompat
        .getTransportControls()
        .setRepeatMode(LegacyConversions.convertToPlaybackStateCompatRepeatMode(repeatMode));
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return controllerInfo.playerInfo.shuffleModeEnabled;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    boolean isCurrentShuffleModeEnabled = getShuffleModeEnabled();
    if (shuffleModeEnabled != isCurrentShuffleModeEnabled) {
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithShuffleModeEnabled(shuffleModeEnabled),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout,
              controllerInfo.sessionExtras);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ null,
          /* mediaItemTransitionReason= */ null);
    }

    controllerCompat
        .getTransportControls()
        .setShuffleMode(
            LegacyConversions.convertToPlaybackStateCompatShuffleMode(shuffleModeEnabled));
  }

  @Override
  public VideoSize getVideoSize() {
    Log.w(TAG, "Session doesn't support getting VideoSize");
    return VideoSize.UNKNOWN;
  }

  @Override
  public Size getSurfaceSize() {
    Log.w(TAG, "Session doesn't support getting VideoSurfaceSize");
    return Size.UNKNOWN;
  }

  @Override
  public void clearVideoSurface() {
    Log.w(TAG, "Session doesn't support clearing Surface");
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    Log.w(TAG, "Session doesn't support clearing Surface");
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    Log.w(TAG, "Session doesn't support setting Surface");
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    Log.w(TAG, "Session doesn't support setting SurfaceHolder");
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    Log.w(TAG, "Session doesn't support clearing SurfaceHolder");
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    Log.w(TAG, "Session doesn't support setting SurfaceView");
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    Log.w(TAG, "Session doesn't support clearing SurfaceView");
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    Log.w(TAG, "Session doesn't support setting TextureView");
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    Log.w(TAG, "Session doesn't support clearing TextureView");
  }

  @Override
  public CueGroup getCurrentCues() {
    Log.w(TAG, "Session doesn't support getting Cue");
    return CueGroup.EMPTY_TIME_ZERO;
  }

  @Override
  public float getVolume() {
    return 1;
  }

  @Override
  public void setVolume(float volume) {
    Log.w(TAG, "Session doesn't support setting player volume");
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    return controllerInfo.playerInfo.deviceInfo;
  }

  @Override
  public int getDeviceVolume() {
    return controllerInfo.playerInfo.deviceVolume;
  }

  @Override
  public boolean isDeviceMuted() {
    return controllerInfo.playerInfo.deviceMuted;
  }

  /**
   * @deprecated Use {@link #setDeviceVolume(int, int)} instead.
   */
  @Deprecated
  @Override
  public void setDeviceVolume(int volume) {
    setDeviceVolume(volume, C.VOLUME_FLAG_SHOW_UI);
  }

  @Override
  public void setDeviceVolume(int volume, @C.VolumeFlags int flags) {
    DeviceInfo deviceInfo = getDeviceInfo();
    int minVolume = deviceInfo.minVolume;
    int maxVolume = deviceInfo.maxVolume;
    if (minVolume <= volume && (maxVolume == 0 || volume <= maxVolume)) {
      boolean isDeviceMuted = isDeviceMuted();
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithDeviceVolume(volume, isDeviceMuted),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout,
              controllerInfo.sessionExtras);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ null,
          /* mediaItemTransitionReason= */ null);
    }

    controllerCompat.setVolumeTo(volume, flags);
  }

  /**
   * @deprecated Use {@link #increaseDeviceVolume(int)} instead.
   */
  @Deprecated
  @Override
  public void increaseDeviceVolume() {
    increaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI);
  }

  @Override
  public void increaseDeviceVolume(@C.VolumeFlags int flags) {
    int volume = getDeviceVolume();
    int maxVolume = getDeviceInfo().maxVolume;
    if (maxVolume == 0 || volume + 1 <= maxVolume) {
      boolean isDeviceMuted = isDeviceMuted();

      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithDeviceVolume(volume + 1, isDeviceMuted),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout,
              controllerInfo.sessionExtras);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ null,
          /* mediaItemTransitionReason= */ null);
    }
    controllerCompat.adjustVolume(AudioManager.ADJUST_RAISE, flags);
  }

  /**
   * @deprecated Use {@link #decreaseDeviceVolume(int)} instead.
   */
  @Deprecated
  @Override
  public void decreaseDeviceVolume() {
    decreaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI);
  }

  @Override
  public void decreaseDeviceVolume(@C.VolumeFlags int flags) {
    int volume = getDeviceVolume();
    int minVolume = getDeviceInfo().minVolume;

    if (volume - 1 >= minVolume) {
      boolean isDeviceMuted = isDeviceMuted();
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithDeviceVolume(volume - 1, isDeviceMuted),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout,
              controllerInfo.sessionExtras);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ null,
          /* mediaItemTransitionReason= */ null);
    }
    controllerCompat.adjustVolume(AudioManager.ADJUST_LOWER, flags);
  }

  /**
   * @deprecated Use {@link #setDeviceMuted(boolean, int)} instead.
   */
  @Deprecated
  @Override
  public void setDeviceMuted(boolean muted) {
    setDeviceMuted(muted, C.VOLUME_FLAG_SHOW_UI);
  }

  @Override
  public void setDeviceMuted(boolean muted, @C.VolumeFlags int flags) {
    if (Util.SDK_INT < 23) {
      Log.w(TAG, "Session doesn't support setting mute state at API level less than 23");
      return;
    }

    boolean isMuted = isDeviceMuted();
    if (muted != isMuted) {
      int volume = getDeviceVolume();
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithDeviceVolume(volume, muted),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout,
              controllerInfo.sessionExtras);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ null,
          /* mediaItemTransitionReason= */ null);
    }

    int direction = muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
    controllerCompat.adjustVolume(direction, flags);
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    Log.w(TAG, "Legacy session doesn't support setting audio attributes remotely");
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (controllerInfo.playerInfo.playWhenReady == playWhenReady) {
      return;
    }
    // Update position and then stop estimating until a new positionInfo arrives from the session.
    currentPositionMs =
        MediaUtils.getUpdatedCurrentPositionMs(
            controllerInfo.playerInfo,
            currentPositionMs,
            lastSetPlayWhenReadyCalledTimeMs,
            getInstance().getTimeDiffMs());
    lastSetPlayWhenReadyCalledTimeMs = SystemClock.elapsedRealtime();
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            controllerInfo.playerInfo.copyWithPlayWhenReady(
                playWhenReady,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                Player.PLAYBACK_SUPPRESSION_REASON_NONE),
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout,
            controllerInfo.sessionExtras);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ null,
        /* mediaItemTransitionReason= */ null);

    if (isPrepared() && hasMedia()) {
      if (playWhenReady) {
        controllerCompat.getTransportControls().play();
      } else {
        controllerCompat.getTransportControls().pause();
      }
    }
  }

  @Override
  public boolean getPlayWhenReady() {
    return controllerInfo.playerInfo.playWhenReady;
  }

  @Override
  @Player.PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    // Not supported.
    return Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  @Override
  @Player.State
  public int getPlaybackState() {
    return controllerInfo.playerInfo.playbackState;
  }

  @Override
  public boolean isPlaying() {
    return controllerInfo.playerInfo.isPlaying;
  }

  @Override
  public boolean isLoading() {
    return false;
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    @Nullable MediaItem mediaItem = controllerInfo.playerInfo.getCurrentMediaItem();
    return mediaItem == null ? MediaMetadata.EMPTY : mediaItem.mediaMetadata;
  }

  @Override
  public Commands getAvailableCommands() {
    return controllerInfo.availablePlayerCommands;
  }

  @Override
  public Tracks getCurrentTracks() {
    return Tracks.EMPTY;
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {}

  @Override
  public SessionCommands getAvailableSessionCommands() {
    return controllerInfo.availableSessionCommands;
  }

  @Override
  public Context getContext() {
    return context;
  }

  @Override
  @Nullable
  public MediaBrowserCompat getBrowserCompat() {
    return browserCompat;
  }

  @Nullable
  @Override
  public IMediaController getBinder() {
    return null;
  }

  void onConnected() {
    if (released || connected) {
      return;
    }
    connected = true;
    LegacyPlayerInfo newLegacyPlayerInfo =
        new LegacyPlayerInfo(
            controllerCompat.getPlaybackInfo(),
            convertToSafePlaybackStateCompat(controllerCompat.getPlaybackState()),
            controllerCompat.getMetadata(),
            convertToNonNullQueueItemList(controllerCompat.getQueue()),
            controllerCompat.getQueueTitle(),
            controllerCompat.getRepeatMode(),
            controllerCompat.getShuffleMode(),
            controllerCompat.getExtras());
    handleNewLegacyParameters(/* notifyConnected= */ true, newLegacyPlayerInfo);
  }

  private void connectToSession(MediaSessionCompat.Token sessionCompatToken) {
    getInstance()
        .runOnApplicationLooper(
            () -> {
              controllerCompat = new MediaControllerCompat(context, sessionCompatToken);
              // Note: registerCallback() will invoke
              // MediaControllerCompat.Callback#onSessionReady()
              // if the controller is already ready.
              controllerCompat.registerCallback(
                  controllerCompatCallback, getInstance().applicationHandler);
            });
    // Post a runnable to prevent callbacks from being called by onConnected()
    // before the constructor returns (b/196941334).
    getInstance()
        .applicationHandler
        .post(
            () -> {
              if (!controllerCompat.isSessionReady()) {
                // If the session not ready here, then call onConnected() immediately. The session
                // may be a framework MediaSession and we cannot know whether it can be ready later.
                onConnected();
              }
            });
  }

  private void connectToService() {
    getInstance()
        .runOnApplicationLooper(
            () -> {
              // BrowserCompat can only be used on the thread that it's created.
              // Create it on the application looper to respect that.
              browserCompat =
                  new MediaBrowserCompat(
                      context, token.getComponentName(), new ConnectionCallback(), null);
              browserCompat.connect();
            });
  }

  private boolean isPrepared() {
    return controllerInfo.playerInfo.playbackState != Player.STATE_IDLE;
  }

  private boolean hasMedia() {
    return !controllerInfo.playerInfo.timeline.isEmpty();
  }

  private void initializeLegacyPlaylist() {
    Window window = new Window();
    checkState(isPrepared() && hasMedia());
    QueueTimeline queueTimeline = (QueueTimeline) controllerInfo.playerInfo.timeline;
    // Set the current item first as these calls are expected to replace the current playlist.
    int currentIndex = controllerInfo.playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex;
    MediaItem currentMediaItem = queueTimeline.getWindow(currentIndex, window).mediaItem;
    if (queueTimeline.getQueueId(currentIndex) != QueueItem.UNKNOWN_ID) {
      // Current item is already known to the session. Just prepare or play.
      if (controllerInfo.playerInfo.playWhenReady) {
        controllerCompat.getTransportControls().play();
      } else {
        controllerCompat.getTransportControls().prepare();
      }
    } else if (currentMediaItem.requestMetadata.mediaUri != null) {
      if (controllerInfo.playerInfo.playWhenReady) {
        controllerCompat
            .getTransportControls()
            .playFromUri(
                currentMediaItem.requestMetadata.mediaUri,
                getOrEmptyBundle(currentMediaItem.requestMetadata.extras));
      } else {
        controllerCompat
            .getTransportControls()
            .prepareFromUri(
                currentMediaItem.requestMetadata.mediaUri,
                getOrEmptyBundle(currentMediaItem.requestMetadata.extras));
      }
    } else if (currentMediaItem.requestMetadata.searchQuery != null) {
      if (controllerInfo.playerInfo.playWhenReady) {
        controllerCompat
            .getTransportControls()
            .playFromSearch(
                currentMediaItem.requestMetadata.searchQuery,
                getOrEmptyBundle(currentMediaItem.requestMetadata.extras));
      } else {
        controllerCompat
            .getTransportControls()
            .prepareFromSearch(
                currentMediaItem.requestMetadata.searchQuery,
                getOrEmptyBundle(currentMediaItem.requestMetadata.extras));
      }
    } else {
      if (controllerInfo.playerInfo.playWhenReady) {
        controllerCompat
            .getTransportControls()
            .playFromMediaId(
                currentMediaItem.mediaId,
                getOrEmptyBundle(currentMediaItem.requestMetadata.extras));
      } else {
        controllerCompat
            .getTransportControls()
            .prepareFromMediaId(
                currentMediaItem.mediaId,
                getOrEmptyBundle(currentMediaItem.requestMetadata.extras));
      }
    }
    // Seek to non-zero start positon if needed.
    if (controllerInfo.playerInfo.sessionPositionInfo.positionInfo.positionMs != 0) {
      controllerCompat
          .getTransportControls()
          .seekTo(controllerInfo.playerInfo.sessionPositionInfo.positionInfo.positionMs);
    }
    // Add all other items to the playlist if supported.
    if (getAvailableCommands().contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
      List<MediaItem> adjustedMediaItems = new ArrayList<>();
      for (int i = 0; i < queueTimeline.getWindowCount(); i++) {
        if (i == currentIndex || queueTimeline.getQueueId(i) != QueueItem.UNKNOWN_ID) {
          // Skip the current item (added above) and all items already known to the session.
          continue;
        }
        adjustedMediaItems.add(queueTimeline.getWindow(/* windowIndex= */ i, window).mediaItem);
      }
      addQueueItems(adjustedMediaItems, /* startIndex= */ 0);
    }
  }

  private void addQueueItems(List<MediaItem> mediaItems, int startIndex) {
    List<@NullableType ListenableFuture<Bitmap>> bitmapFutures = new ArrayList<>();
    final AtomicInteger resultCount = new AtomicInteger(0);
    Runnable handleBitmapFuturesTask =
        () -> {
          int completedBitmapFutureCount = resultCount.incrementAndGet();
          if (completedBitmapFutureCount == mediaItems.size()) {
            handleBitmapFuturesAllCompletedAndAddQueueItems(
                bitmapFutures, mediaItems, /* startIndex= */ startIndex);
          }
        };

    for (int i = 0; i < mediaItems.size(); i++) {
      MediaItem mediaItem = mediaItems.get(i);
      MediaMetadata metadata = mediaItem.mediaMetadata;
      if (metadata.artworkData == null) {
        bitmapFutures.add(null);
        handleBitmapFuturesTask.run();
      } else {
        ListenableFuture<Bitmap> bitmapFuture = bitmapLoader.decodeBitmap(metadata.artworkData);
        bitmapFutures.add(bitmapFuture);
        bitmapFuture.addListener(handleBitmapFuturesTask, getInstance().applicationHandler::post);
      }
    }
  }

  private void handleBitmapFuturesAllCompletedAndAddQueueItems(
      List<@NullableType ListenableFuture<Bitmap>> bitmapFutures,
      List<MediaItem> mediaItems,
      int startIndex) {
    for (int i = 0; i < bitmapFutures.size(); i++) {
      @Nullable ListenableFuture<Bitmap> future = bitmapFutures.get(i);
      @Nullable Bitmap bitmap = null;
      if (future != null) {
        try {
          bitmap = Futures.getDone(future);
        } catch (CancellationException | ExecutionException e) {
          Log.d(TAG, "Failed to get bitmap", e);
        }
      }
      controllerCompat.addQueueItem(
          LegacyConversions.convertToMediaDescriptionCompat(mediaItems.get(i), bitmap),
          /* index= */ startIndex + i);
    }
  }

  private void handleNewLegacyParameters(
      boolean notifyConnected, LegacyPlayerInfo newLegacyPlayerInfo) {
    if (released || !connected) {
      // Prevent calls from pendingChangesHandler after released()
      return;
    }
    ControllerInfo newControllerInfo =
        buildNewControllerInfo(
            notifyConnected,
            legacyPlayerInfo,
            controllerInfo,
            newLegacyPlayerInfo,
            controllerCompat.getPackageName(),
            controllerCompat.getFlags(),
            controllerCompat.isSessionReady(),
            controllerCompat.getRatingType(),
            getInstance().getTimeDiffMs(),
            getRoutingControllerId(controllerCompat));
    Pair<@NullableType Integer, @NullableType Integer> reasons =
        calculateDiscontinuityAndTransitionReason(
            legacyPlayerInfo,
            controllerInfo,
            newLegacyPlayerInfo,
            newControllerInfo,
            getInstance().getTimeDiffMs());
    updateControllerInfo(
        notifyConnected,
        newLegacyPlayerInfo,
        newControllerInfo,
        /* discontinuityReason= */ reasons.first,
        /* mediaItemTransitionReason= */ reasons.second);
  }

  private void updateStateMaskedControllerInfo(
      ControllerInfo newControllerInfo,
      @Nullable @Player.DiscontinuityReason Integer discontinuityReason,
      @Nullable @Player.MediaItemTransitionReason Integer mediaItemTransitionReason) {
    // Safe to pass legacyPlayerInfo without updating.
    // updateControllerInfo() takes LegacyPlayerInfo just to easily detect some changes in the
    // controller info, and LegacyPlayerInfo isn't used for values that state-masking may be
    // applied.
    updateControllerInfo(
        /* notifyConnected= */ false,
        legacyPlayerInfo,
        newControllerInfo,
        discontinuityReason,
        mediaItemTransitionReason);
  }

  // Calling deprecated listener callback method for backwards compatibility.
  @SuppressWarnings("deprecation")
  private void updateControllerInfo(
      boolean notifyConnected,
      LegacyPlayerInfo newLegacyPlayerInfo,
      ControllerInfo newControllerInfo,
      @Nullable @Player.DiscontinuityReason Integer discontinuityReason,
      @Nullable @Player.MediaItemTransitionReason Integer mediaItemTransitionReason) {
    LegacyPlayerInfo oldLegacyPlayerInfo = legacyPlayerInfo;
    ControllerInfo oldControllerInfo = controllerInfo;
    if (legacyPlayerInfo != newLegacyPlayerInfo) {
      legacyPlayerInfo = new LegacyPlayerInfo(newLegacyPlayerInfo);
    }
    pendingLegacyPlayerInfo = legacyPlayerInfo;
    controllerInfo = newControllerInfo;

    if (notifyConnected) {
      getInstance().notifyAccepted();
      if (!oldControllerInfo.customLayout.equals(newControllerInfo.customLayout)) {
        getInstance()
            .notifyControllerListener(
                listener -> {
                  ignoreFuture(
                      listener.onSetCustomLayout(getInstance(), newControllerInfo.customLayout));
                  listener.onCustomLayoutChanged(getInstance(), newControllerInfo.customLayout);
                });
      }
      return;
    }
    if (!oldControllerInfo.playerInfo.timeline.equals(newControllerInfo.playerInfo.timeline)) {
      listeners.queueEvent(
          Player.EVENT_TIMELINE_CHANGED,
          (listener) ->
              listener.onTimelineChanged(
                  newControllerInfo.playerInfo.timeline,
                  newControllerInfo.playerInfo.timelineChangeReason));
    }
    if (!Util.areEqual(oldLegacyPlayerInfo.queueTitle, newLegacyPlayerInfo.queueTitle)) {
      listeners.queueEvent(
          Player.EVENT_PLAYLIST_METADATA_CHANGED,
          (listener) ->
              listener.onPlaylistMetadataChanged(newControllerInfo.playerInfo.playlistMetadata));
    }
    if (discontinuityReason != null) {
      listeners.queueEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          (listener) ->
              listener.onPositionDiscontinuity(
                  oldControllerInfo.playerInfo.sessionPositionInfo.positionInfo,
                  newControllerInfo.playerInfo.sessionPositionInfo.positionInfo,
                  discontinuityReason));
    }
    if (mediaItemTransitionReason != null) {
      listeners.queueEvent(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          (listener) ->
              listener.onMediaItemTransition(
                  newControllerInfo.playerInfo.getCurrentMediaItem(), mediaItemTransitionReason));
    }
    if (!MediaUtils.areEqualError(
        oldLegacyPlayerInfo.playbackStateCompat, newLegacyPlayerInfo.playbackStateCompat)) {
      PlaybackException error =
          LegacyConversions.convertToPlaybackException(newLegacyPlayerInfo.playbackStateCompat);
      listeners.queueEvent(
          Player.EVENT_PLAYER_ERROR, (listener) -> listener.onPlayerErrorChanged(error));
      if (error != null) {
        listeners.queueEvent(
            Player.EVENT_PLAYER_ERROR, (listener) -> listener.onPlayerError(error));
      }
    }
    if (oldLegacyPlayerInfo.mediaMetadataCompat != newLegacyPlayerInfo.mediaMetadataCompat) {
      listeners.queueEvent(
          Player.EVENT_MEDIA_METADATA_CHANGED,
          (listener) -> listener.onMediaMetadataChanged(controllerInfo.playerInfo.mediaMetadata));
    }
    if (oldControllerInfo.playerInfo.playbackState != newControllerInfo.playerInfo.playbackState) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          (listener) ->
              listener.onPlaybackStateChanged(newControllerInfo.playerInfo.playbackState));
    }
    if (oldControllerInfo.playerInfo.playWhenReady != newControllerInfo.playerInfo.playWhenReady) {
      listeners.queueEvent(
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          (listener) ->
              listener.onPlayWhenReadyChanged(
                  newControllerInfo.playerInfo.playWhenReady,
                  Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE));
    }
    if (oldControllerInfo.playerInfo.isPlaying != newControllerInfo.playerInfo.isPlaying) {
      listeners.queueEvent(
          Player.EVENT_IS_PLAYING_CHANGED,
          (listener) -> listener.onIsPlayingChanged(newControllerInfo.playerInfo.isPlaying));
    }
    if (!oldControllerInfo.playerInfo.playbackParameters.equals(
        newControllerInfo.playerInfo.playbackParameters)) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
          (listener) ->
              listener.onPlaybackParametersChanged(
                  newControllerInfo.playerInfo.playbackParameters));
    }
    if (oldControllerInfo.playerInfo.repeatMode != newControllerInfo.playerInfo.repeatMode) {
      listeners.queueEvent(
          Player.EVENT_REPEAT_MODE_CHANGED,
          (listener) -> listener.onRepeatModeChanged(newControllerInfo.playerInfo.repeatMode));
    }
    if (oldControllerInfo.playerInfo.shuffleModeEnabled
        != newControllerInfo.playerInfo.shuffleModeEnabled) {
      listeners.queueEvent(
          Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          (listener) ->
              listener.onShuffleModeEnabledChanged(
                  newControllerInfo.playerInfo.shuffleModeEnabled));
    }
    if (!oldControllerInfo.playerInfo.audioAttributes.equals(
        newControllerInfo.playerInfo.audioAttributes)) {
      listeners.queueEvent(
          Player.EVENT_AUDIO_ATTRIBUTES_CHANGED,
          (listener) ->
              listener.onAudioAttributesChanged(newControllerInfo.playerInfo.audioAttributes));
    }
    if (!oldControllerInfo.playerInfo.deviceInfo.equals(newControllerInfo.playerInfo.deviceInfo)) {
      listeners.queueEvent(
          Player.EVENT_DEVICE_INFO_CHANGED,
          (listener) -> listener.onDeviceInfoChanged(newControllerInfo.playerInfo.deviceInfo));
    }
    if (oldControllerInfo.playerInfo.deviceVolume != newControllerInfo.playerInfo.deviceVolume
        || oldControllerInfo.playerInfo.deviceMuted != newControllerInfo.playerInfo.deviceMuted) {
      listeners.queueEvent(
          Player.EVENT_DEVICE_VOLUME_CHANGED,
          (listener) ->
              listener.onDeviceVolumeChanged(
                  newControllerInfo.playerInfo.deviceVolume,
                  newControllerInfo.playerInfo.deviceMuted));
    }
    if (!oldControllerInfo.availablePlayerCommands.equals(
        newControllerInfo.availablePlayerCommands)) {
      listeners.queueEvent(
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
          (listener) ->
              listener.onAvailableCommandsChanged(newControllerInfo.availablePlayerCommands));
    }
    if (!oldControllerInfo.availableSessionCommands.equals(
        newControllerInfo.availableSessionCommands)) {
      getInstance()
          .notifyControllerListener(
              listener ->
                  listener.onAvailableSessionCommandsChanged(
                      getInstance(), newControllerInfo.availableSessionCommands));
    }
    if (!oldControllerInfo.customLayout.equals(newControllerInfo.customLayout)) {
      getInstance()
          .notifyControllerListener(
              listener -> {
                ignoreFuture(
                    listener.onSetCustomLayout(getInstance(), newControllerInfo.customLayout));
                listener.onCustomLayoutChanged(getInstance(), newControllerInfo.customLayout);
              });
    }
    listeners.flushEvents();
  }

  @Nullable
  private static String getRoutingControllerId(MediaControllerCompat controllerCompat) {
    if (Util.SDK_INT < 30) {
      return null;
    }
    android.media.session.MediaController fwkController =
        (android.media.session.MediaController) controllerCompat.getMediaController();
    @Nullable
    android.media.session.MediaController.PlaybackInfo playbackInfo =
        fwkController.getPlaybackInfo();
    if (playbackInfo == null) {
      return null;
    }
    return playbackInfo.getVolumeControlId();
  }

  private static <T> void ignoreFuture(Future<T> unused) {
    // Ignore return value of the future because legacy session cannot get result back.
  }

  private class ConnectionCallback extends MediaBrowserCompat.ConnectionCallback {

    @Override
    public void onConnected() {
      MediaBrowserCompat browser = getBrowserCompat();
      if (browser != null) {
        connectToSession(browser.getSessionToken());
      }
    }

    @Override
    public void onConnectionSuspended() {
      getInstance().release();
    }

    @Override
    public void onConnectionFailed() {
      getInstance().release();
    }
  }

  private final class ControllerCompatCallback extends MediaControllerCompat.Callback {

    private static final int MSG_HANDLE_PENDING_UPDATES = 1;

    private final Handler pendingChangesHandler;

    public ControllerCompatCallback(Looper applicationLooper) {
      pendingChangesHandler =
          new Handler(
              applicationLooper,
              (msg) -> {
                if (msg.what == MSG_HANDLE_PENDING_UPDATES) {
                  handleNewLegacyParameters(/* notifyConnected= */ false, pendingLegacyPlayerInfo);
                }
                return true;
              });
    }

    public void release() {
      pendingChangesHandler.removeCallbacksAndMessages(/* token= */ null);
    }

    @Override
    public void onSessionReady() {
      if (!connected) {
        onConnected();
      } else {
        // Handle the case when extra binder is available after connectToSession().
        // Initial values are notified already, so only notify values that are available when
        // session is ready.
        pendingLegacyPlayerInfo =
            pendingLegacyPlayerInfo.copyWithExtraBinderGetters(
                convertToSafePlaybackStateCompat(controllerCompat.getPlaybackState()),
                controllerCompat.getRepeatMode(),
                controllerCompat.getShuffleMode());
        boolean isCaptioningEnabled = controllerCompat.isCaptioningEnabled();
        onCaptioningEnabledChanged(isCaptioningEnabled);

        pendingChangesHandler.removeMessages(MSG_HANDLE_PENDING_UPDATES);
        handleNewLegacyParameters(/* notifyConnected= */ false, pendingLegacyPlayerInfo);
      }
    }

    @Override
    public void onSessionDestroyed() {
      getInstance().release();
    }

    @Override
    public void onSessionEvent(String event, Bundle extras) {
      getInstance()
          .notifyControllerListener(
              listener ->
                  ignoreFuture(
                      listener.onCustomCommand(
                          getInstance(),
                          new SessionCommand(event, /* extras= */ Bundle.EMPTY),
                          extras)));
    }

    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {
      pendingLegacyPlayerInfo =
          pendingLegacyPlayerInfo.copyWithPlaybackStateCompat(
              convertToSafePlaybackStateCompat(state));
      startWaitingForPendingChanges();
    }

    @Override
    public void onMetadataChanged(MediaMetadataCompat metadata) {
      pendingLegacyPlayerInfo = pendingLegacyPlayerInfo.copyWithMediaMetadataCompat(metadata);
      startWaitingForPendingChanges();
    }

    @Override
    public void onQueueChanged(@Nullable List<@NullableType QueueItem> queue) {
      pendingLegacyPlayerInfo =
          pendingLegacyPlayerInfo.copyWithQueue(convertToNonNullQueueItemList(queue));
      startWaitingForPendingChanges();
    }

    @Override
    public void onQueueTitleChanged(CharSequence title) {
      pendingLegacyPlayerInfo = pendingLegacyPlayerInfo.copyWithQueueTitle(title);
      startWaitingForPendingChanges();
    }

    @Override
    public void onExtrasChanged(Bundle extras) {
      controllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo,
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout,
              extras);
      getInstance()
          .notifyControllerListener(listener -> listener.onExtrasChanged(getInstance(), extras));
    }

    @Override
    public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo newPlaybackInfo) {
      pendingLegacyPlayerInfo = pendingLegacyPlayerInfo.copyWithPlaybackInfoCompat(newPlaybackInfo);
      startWaitingForPendingChanges();
    }

    @Override
    public void onCaptioningEnabledChanged(boolean enabled) {
      getInstance()
          .notifyControllerListener(
              listener -> {
                Bundle args = new Bundle();
                args.putBoolean(MediaConstants.ARGUMENT_CAPTIONING_ENABLED, enabled);
                ignoreFuture(
                    listener.onCustomCommand(
                        getInstance(),
                        new SessionCommand(
                            MediaConstants.SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED,
                            /* extras= */ Bundle.EMPTY),
                        args));
              });
    }

    @Override
    public void onRepeatModeChanged(
        @PlaybackStateCompat.RepeatMode int playbackStateCompatRepeatMode) {
      pendingLegacyPlayerInfo =
          pendingLegacyPlayerInfo.copyWithRepeatMode(playbackStateCompatRepeatMode);
      startWaitingForPendingChanges();
    }

    @Override
    public void onShuffleModeChanged(
        @PlaybackStateCompat.ShuffleMode int playbackStateCompatShuffleMode) {
      pendingLegacyPlayerInfo =
          pendingLegacyPlayerInfo.copyWithShuffleMode(playbackStateCompatShuffleMode);
      startWaitingForPendingChanges();
    }

    private void startWaitingForPendingChanges() {
      if (pendingChangesHandler.hasMessages(MSG_HANDLE_PENDING_UPDATES)) {
        return;
      }
      pendingChangesHandler.sendEmptyMessageDelayed(
          MSG_HANDLE_PENDING_UPDATES, AGGREGATES_CALLBACKS_WITHIN_TIMEOUT_MS);
    }
  }

  private static ControllerInfo buildNewControllerInfo(
      boolean initialUpdate,
      LegacyPlayerInfo oldLegacyPlayerInfo,
      ControllerInfo oldControllerInfo,
      LegacyPlayerInfo newLegacyPlayerInfo,
      String sessionPackageName,
      long sessionFlags,
      boolean isSessionReady,
      @RatingCompat.Style int ratingType,
      long timeDiffMs,
      @Nullable String routingControllerId) {
    QueueTimeline currentTimeline;
    MediaMetadata mediaMetadata;
    int currentMediaItemIndex;
    MediaMetadata playlistMetadata;
    @Player.RepeatMode int repeatMode;
    boolean shuffleModeEnabled;
    SessionCommands availableSessionCommands;
    Commands availablePlayerCommands;
    ImmutableList<CommandButton> customLayout;

    boolean isQueueChanged = oldLegacyPlayerInfo.queue != newLegacyPlayerInfo.queue;
    currentTimeline =
        isQueueChanged
            ? QueueTimeline.create(newLegacyPlayerInfo.queue)
            : ((QueueTimeline) oldControllerInfo.playerInfo.timeline).copy();

    boolean isMetadataCompatChanged =
        oldLegacyPlayerInfo.mediaMetadataCompat != newLegacyPlayerInfo.mediaMetadataCompat
            || initialUpdate;
    long oldActiveQueueId = getActiveQueueId(oldLegacyPlayerInfo.playbackStateCompat);
    long newActiveQueueId = getActiveQueueId(newLegacyPlayerInfo.playbackStateCompat);
    boolean isCurrentActiveQueueIdChanged = (oldActiveQueueId != newActiveQueueId) || initialUpdate;
    long durationMs =
        LegacyConversions.convertToDurationMs(newLegacyPlayerInfo.mediaMetadataCompat);
    if (isMetadataCompatChanged || isCurrentActiveQueueIdChanged || isQueueChanged) {
      currentMediaItemIndex = findQueueItemIndex(newLegacyPlayerInfo.queue, newActiveQueueId);
      boolean hasMediaMetadataCompat = newLegacyPlayerInfo.mediaMetadataCompat != null;
      if (hasMediaMetadataCompat && isMetadataCompatChanged) {
        mediaMetadata =
            LegacyConversions.convertToMediaMetadata(
                newLegacyPlayerInfo.mediaMetadataCompat, ratingType);
      } else if (!hasMediaMetadataCompat && isCurrentActiveQueueIdChanged) {
        mediaMetadata =
            (currentMediaItemIndex == C.INDEX_UNSET)
                ? MediaMetadata.EMPTY
                : LegacyConversions.convertToMediaMetadata(
                    newLegacyPlayerInfo.queue.get(currentMediaItemIndex).getDescription(),
                    ratingType);
      } else {
        mediaMetadata = oldControllerInfo.playerInfo.mediaMetadata;
      }
      if (currentMediaItemIndex == C.INDEX_UNSET && isMetadataCompatChanged) {
        if (hasMediaMetadataCompat) {
          Log.w(
              TAG,
              "Adding a fake MediaItem at the end of the list because there's no QueueItem with"
                  + " the active queue id and current Timeline should have currently playing"
                  + " MediaItem.");
          MediaItem fakeMediaItem =
              LegacyConversions.convertToMediaItem(
                  newLegacyPlayerInfo.mediaMetadataCompat, ratingType);
          currentTimeline = currentTimeline.copyWithFakeMediaItem(fakeMediaItem, durationMs);
          currentMediaItemIndex = currentTimeline.getWindowCount() - 1;
        } else {
          currentTimeline = currentTimeline.copyWithClearedFakeMediaItem();
          // Shouldn't be C.INDEX_UNSET to make getCurrentMediaItemIndex() return masked index.
          // In other words, this index is either the currently playing media item index or the
          // would-be playing index when playing.
          currentMediaItemIndex = 0;
        }
      } else if (currentMediaItemIndex != C.INDEX_UNSET) {
        currentTimeline = currentTimeline.copyWithClearedFakeMediaItem();
        if (hasMediaMetadataCompat) {
          MediaItem mediaItem =
              LegacyConversions.convertToMediaItem(
                  checkNotNull(currentTimeline.getMediaItemAt(currentMediaItemIndex)).mediaId,
                  newLegacyPlayerInfo.mediaMetadataCompat,
                  ratingType);
          currentTimeline =
              currentTimeline.copyWithNewMediaItem(
                  /* replaceIndex= */ currentMediaItemIndex, mediaItem, durationMs);
        }
      } else {
        // There's queue, but no valid queue item ID nor current media item metadata.
        // Fallback to use 0 as current media item index to mask current item index.
        currentMediaItemIndex = 0;
      }
    } else {
      currentMediaItemIndex =
          oldControllerInfo.playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex;
      mediaMetadata = oldControllerInfo.playerInfo.mediaMetadata;
    }

    playlistMetadata =
        oldLegacyPlayerInfo.queueTitle == newLegacyPlayerInfo.queueTitle
            ? oldControllerInfo.playerInfo.playlistMetadata
            : LegacyConversions.convertToMediaMetadata(newLegacyPlayerInfo.queueTitle);
    repeatMode = LegacyConversions.convertToRepeatMode(newLegacyPlayerInfo.repeatMode);
    shuffleModeEnabled =
        LegacyConversions.convertToShuffleModeEnabled(newLegacyPlayerInfo.shuffleMode);
    if (oldLegacyPlayerInfo.playbackStateCompat != newLegacyPlayerInfo.playbackStateCompat) {
      availableSessionCommands =
          LegacyConversions.convertToSessionCommands(
              newLegacyPlayerInfo.playbackStateCompat, isSessionReady);
      customLayout =
          LegacyConversions.convertToCustomLayout(newLegacyPlayerInfo.playbackStateCompat);
    } else {
      availableSessionCommands = oldControllerInfo.availableSessionCommands;
      customLayout = oldControllerInfo.customLayout;
    }
    // Note: Sets the available player command here although it can be obtained before session is
    // ready. It's to follow the decision on MediaController to disallow any commands before
    // connection is made.
    int volumeControlType =
        newLegacyPlayerInfo.playbackInfoCompat != null
            ? newLegacyPlayerInfo.playbackInfoCompat.getVolumeControl()
            : VolumeProviderCompat.VOLUME_CONTROL_FIXED;
    availablePlayerCommands =
        LegacyConversions.convertToPlayerCommands(
            newLegacyPlayerInfo.playbackStateCompat,
            volumeControlType,
            sessionFlags,
            isSessionReady);

    PlaybackException playerError =
        LegacyConversions.convertToPlaybackException(newLegacyPlayerInfo.playbackStateCompat);

    long currentPositionMs =
        LegacyConversions.convertToCurrentPositionMs(
            newLegacyPlayerInfo.playbackStateCompat,
            newLegacyPlayerInfo.mediaMetadataCompat,
            timeDiffMs);
    long bufferedPositionMs =
        LegacyConversions.convertToBufferedPositionMs(
            newLegacyPlayerInfo.playbackStateCompat,
            newLegacyPlayerInfo.mediaMetadataCompat,
            timeDiffMs);
    int bufferedPercentage =
        LegacyConversions.convertToBufferedPercentage(
            newLegacyPlayerInfo.playbackStateCompat,
            newLegacyPlayerInfo.mediaMetadataCompat,
            timeDiffMs);
    long totalBufferedDurationMs =
        LegacyConversions.convertToTotalBufferedDurationMs(
            newLegacyPlayerInfo.playbackStateCompat,
            newLegacyPlayerInfo.mediaMetadataCompat,
            timeDiffMs);
    boolean isPlayingAd =
        LegacyConversions.convertToIsPlayingAd(newLegacyPlayerInfo.mediaMetadataCompat);
    PlaybackParameters playbackParameters =
        LegacyConversions.convertToPlaybackParameters(newLegacyPlayerInfo.playbackStateCompat);
    AudioAttributes audioAttributes =
        LegacyConversions.convertToAudioAttributes(newLegacyPlayerInfo.playbackInfoCompat);
    boolean playWhenReady =
        LegacyConversions.convertToPlayWhenReady(newLegacyPlayerInfo.playbackStateCompat);
    @Player.State int playbackState;
    try {
      playbackState =
          LegacyConversions.convertToPlaybackState(
              newLegacyPlayerInfo.playbackStateCompat,
              newLegacyPlayerInfo.mediaMetadataCompat,
              timeDiffMs);
    } catch (ConversionException e) {
      Log.e(
          TAG,
          format(
              "Received invalid playback state %s from package %s. Keeping the previous state.",
              newLegacyPlayerInfo.playbackStateCompat.getState(), sessionPackageName));
      playbackState = oldControllerInfo.playerInfo.playbackState;
    }
    boolean isPlaying =
        LegacyConversions.convertToIsPlaying(newLegacyPlayerInfo.playbackStateCompat);
    DeviceInfo deviceInfo =
        LegacyConversions.convertToDeviceInfo(
            newLegacyPlayerInfo.playbackInfoCompat, routingControllerId);
    int deviceVolume =
        LegacyConversions.convertToDeviceVolume(newLegacyPlayerInfo.playbackInfoCompat);
    boolean deviceMuted =
        LegacyConversions.convertToIsDeviceMuted(newLegacyPlayerInfo.playbackInfoCompat);
    long seekBackIncrementMs = oldControllerInfo.playerInfo.seekBackIncrementMs;
    long seekForwardIncrementMs = oldControllerInfo.playerInfo.seekForwardIncrementMs;

    return createControllerInfo(
        currentTimeline,
        mediaMetadata,
        currentMediaItemIndex,
        playlistMetadata,
        repeatMode,
        shuffleModeEnabled,
        availableSessionCommands,
        availablePlayerCommands,
        customLayout,
        newLegacyPlayerInfo.sessionExtras,
        playerError,
        durationMs,
        currentPositionMs,
        bufferedPositionMs,
        bufferedPercentage,
        totalBufferedDurationMs,
        isPlayingAd,
        playbackParameters,
        audioAttributes,
        playWhenReady,
        playbackState,
        isPlaying,
        deviceInfo,
        deviceVolume,
        deviceMuted,
        seekBackIncrementMs,
        seekForwardIncrementMs);
  }

  /**
   * Calculate position discontinuity reason and media item transition reason outside of the state
   * masking.
   *
   * <p>The returned reasons may be null if they can't be determined.
   */
  private static Pair<@NullableType Integer, @NullableType Integer>
      calculateDiscontinuityAndTransitionReason(
          LegacyPlayerInfo oldLegacyPlayerInfo,
          ControllerInfo oldControllerInfo,
          LegacyPlayerInfo newLegacyPlayerInfo,
          ControllerInfo newControllerInfo,
          long timeDiffMs) {
    @Nullable
    @Player.DiscontinuityReason
    Integer discontinuityReason;
    @Nullable
    @Player.MediaItemTransitionReason
    Integer mediaItemTransitionReason;
    boolean isOldTimelineEmpty = oldControllerInfo.playerInfo.timeline.isEmpty();
    boolean isNewTimelineEmpty = newControllerInfo.playerInfo.timeline.isEmpty();
    if (isOldTimelineEmpty && isNewTimelineEmpty) {
      // Still empty Timelines.
      discontinuityReason = null;
      mediaItemTransitionReason = null;
    } else if (isOldTimelineEmpty && !isNewTimelineEmpty) {
      // A new timeline has been set.
      discontinuityReason = Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
      mediaItemTransitionReason = Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
    } else {
      MediaItem oldCurrentMediaItem =
          checkStateNotNull(oldControllerInfo.playerInfo.getCurrentMediaItem());
      boolean oldCurrentMediaItemExistsInNewTimeline =
          ((QueueTimeline) newControllerInfo.playerInfo.timeline).contains(oldCurrentMediaItem);
      if (!oldCurrentMediaItemExistsInNewTimeline) {
        // Old current item is removed.
        discontinuityReason = Player.DISCONTINUITY_REASON_REMOVE;
        mediaItemTransitionReason = Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
      } else if (oldCurrentMediaItem.equals(newControllerInfo.playerInfo.getCurrentMediaItem())) {
        // Current item is the same.
        long oldCurrentPosition =
            LegacyConversions.convertToCurrentPositionMs(
                oldLegacyPlayerInfo.playbackStateCompat,
                oldLegacyPlayerInfo.mediaMetadataCompat,
                timeDiffMs);
        long newCurrentPosition =
            LegacyConversions.convertToCurrentPositionMs(
                newLegacyPlayerInfo.playbackStateCompat,
                newLegacyPlayerInfo.mediaMetadataCompat,
                timeDiffMs);
        if (newCurrentPosition == 0
            && newControllerInfo.playerInfo.repeatMode == Player.REPEAT_MODE_ONE) {
          // If the position is reset, then it's probably repeating the same media item.
          discontinuityReason = Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
          mediaItemTransitionReason = Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT;
        } else if (Math.abs(oldCurrentPosition - newCurrentPosition)
            > MediaUtils.POSITION_DIFF_TOLERANCE_MS) {
          // Unexpected position discontinuity within the same media item.
          discontinuityReason = Player.DISCONTINUITY_REASON_INTERNAL;
          mediaItemTransitionReason = null;
        } else {
          discontinuityReason = null;
          mediaItemTransitionReason = null;
        }
      } else {
        // Old current item still exists, but it's not the new current media item anymore.
        // If this controller didn't cause index change, then it's auto transition.
        discontinuityReason = Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
        mediaItemTransitionReason = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;
      }
    }
    return Pair.create(discontinuityReason, mediaItemTransitionReason);
  }

  private static List<QueueItem> convertToNonNullQueueItemList(
      @Nullable List<@NullableType QueueItem> queue) {
    return queue == null ? Collections.emptyList() : MediaUtils.removeNullElements(queue);
  }

  @Nullable
  private static PlaybackStateCompat convertToSafePlaybackStateCompat(
      @Nullable PlaybackStateCompat state) {
    if (state == null) {
      return null;
    }
    if (state.getPlaybackSpeed() <= 0) {
      Log.w(
          TAG, "Adjusting playback speed to 1.0f because negative playback speed isn't supported.");
      return new PlaybackStateCompat.Builder(state)
          .setState(
              state.getState(),
              state.getPosition(),
              /* playbackSpeed= */ 1.0f,
              state.getLastPositionUpdateTime())
          .build();
    }
    return state;
  }

  private static Bundle getOrEmptyBundle(@Nullable Bundle bundle) {
    return bundle == null ? Bundle.EMPTY : bundle;
  }

  private static long getActiveQueueId(@Nullable PlaybackStateCompat playbackStateCompat) {
    return playbackStateCompat == null
        ? QueueItem.UNKNOWN_ID
        : playbackStateCompat.getActiveQueueItemId();
  }

  private static int findQueueItemIndex(@Nullable List<QueueItem> queue, long queueItemId) {
    if (queue == null || queueItemId == QueueItem.UNKNOWN_ID) {
      return C.INDEX_UNSET;
    }
    for (int i = 0; i < queue.size(); i++) {
      if (queue.get(i).getQueueId() == queueItemId) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  private static int calculateCurrentItemIndexAfterAddItems(
      int currentItemIndex, int addToIndex, int addItemsSize) {
    return (currentItemIndex < addToIndex) ? currentItemIndex : currentItemIndex + addItemsSize;
  }

  // Returns C.INDEX_UNSET when current item is removed.
  private static int calculateCurrentItemIndexAfterRemoveItems(
      int currentItemIndex, int removeFromIndex, int removeToIndex) {
    int removeItemsSize = removeToIndex - removeFromIndex;
    if (currentItemIndex < removeFromIndex) {
      return currentItemIndex;
    } else if (currentItemIndex < removeToIndex) {
      return C.INDEX_UNSET;
    }
    return currentItemIndex - removeItemsSize;
  }

  private static ControllerInfo createControllerInfo(
      QueueTimeline currentTimeline,
      MediaMetadata mediaMetadata,
      int currentMediaItemIndex,
      MediaMetadata playlistMetadata,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      SessionCommands availableSessionCommands,
      Commands availablePlayerCommands,
      ImmutableList<CommandButton> customLayout,
      Bundle sessionExtras,
      @Nullable PlaybackException playerError,
      long durationMs,
      long currentPositionMs,
      long bufferedPositionMs,
      int bufferedPercentage,
      long totalBufferedDurationMs,
      boolean isPlayingAd,
      PlaybackParameters playbackParameters,
      AudioAttributes audioAttributes,
      boolean playWhenReady,
      int playbackState,
      boolean isPlaying,
      DeviceInfo deviceInfo,
      int deviceVolume,
      boolean deviceMuted,
      long seekBackIncrementMs,
      long seekForwardIncrementMs) {

    @Nullable MediaItem currentMediaItem = currentTimeline.getMediaItemAt(currentMediaItemIndex);
    PositionInfo positionInfo =
        createPositionInfo(currentMediaItemIndex, currentMediaItem, currentPositionMs, isPlayingAd);

    SessionPositionInfo sessionPositionInfo =
        new SessionPositionInfo(
            /* positionInfo= */ positionInfo,
            /* isPlayingAd= */ isPlayingAd,
            /* eventTimeMs= */ SystemClock.elapsedRealtime(),
            /* durationMs= */ durationMs,
            /* bufferedPositionMs= */ bufferedPositionMs,
            /* bufferedPercentage= */ bufferedPercentage,
            /* totalBufferedDurationMs= */ totalBufferedDurationMs,
            /* currentLiveOffsetMs= */ C.TIME_UNSET,
            /* contentDurationMs= */ durationMs,
            /* contentBufferedPositionMs= */ bufferedPositionMs);

    PlayerInfo playerInfo =
        new PlayerInfo(
            /* playerError= */ playerError,
            /* mediaItemTransitionReason= */ PlayerInfo.MEDIA_ITEM_TRANSITION_REASON_DEFAULT,
            /* sessionPositionInfo= */ sessionPositionInfo,
            /* oldPositionInfo= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
            /* newPositionInfo= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
            /* discontinuityReason= */ PlayerInfo.DISCONTINUITY_REASON_DEFAULT,
            /* playbackParameters= */ playbackParameters,
            /* repeatMode= */ repeatMode,
            /* shuffleModeEnabled= */ shuffleModeEnabled,
            /* videoSize= */ VideoSize.UNKNOWN,
            /* timeline= */ currentTimeline,
            /* timelineChangeReason= */ PlayerInfo.TIMELINE_CHANGE_REASON_DEFAULT,
            /* playlistMetadata= */ playlistMetadata,
            /* volume= */ 1.0f,
            /* audioAttributes= */ audioAttributes,
            /* cueGroup= */ CueGroup.EMPTY_TIME_ZERO,
            /* deviceInfo= */ deviceInfo,
            /* deviceVolume= */ deviceVolume,
            /* deviceMuted= */ deviceMuted,
            /* playWhenReady= */ playWhenReady,
            /* playWhenReadyChangeReason= */ PlayerInfo.PLAY_WHEN_READY_CHANGE_REASON_DEFAULT,
            /* playbackSuppressionReason= */ Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            /* playbackState= */ playbackState,
            /* isPlaying= */ isPlaying,
            /* isLoading= */ false,
            /* mediaMetadata= */ mediaMetadata,
            seekBackIncrementMs,
            seekForwardIncrementMs,
            /* maxSeekToPreviousPositionMs= */ 0L,
            /* currentTracks= */ Tracks.EMPTY,
            /* parameters= */ TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT);

    return new ControllerInfo(
        playerInfo, availableSessionCommands, availablePlayerCommands, customLayout, sessionExtras);
  }

  private static PositionInfo createPositionInfo(
      int mediaItemIndex,
      @Nullable MediaItem mediaItem,
      long currentPositionMs,
      boolean isPlayingAd) {
    return new PositionInfo(
        /* windowUid= */ null,
        /* mediaItemIndex= */ mediaItemIndex,
        mediaItem,
        /* periodUid= */ null,
        /* periodIndex= */ mediaItemIndex,
        /* positionMs= */ currentPositionMs,
        /* contentPositionMs= */ currentPositionMs,
        /* adGroupIndex= */ isPlayingAd ? 0 : C.INDEX_UNSET,
        /* adIndexInAdGroup= */ isPlayingAd ? 0 : C.INDEX_UNSET);
  }

  private static SessionPositionInfo createSessionPositionInfo(
      PositionInfo positionInfo,
      boolean isPlayingAd,
      long durationMs,
      long bufferedPositionMs,
      int bufferedPercentage,
      long totalBufferedDurationMs) {
    return new SessionPositionInfo(
        /* positionInfo= */ positionInfo,
        /* isPlayingAd= */ isPlayingAd,
        /* eventTimeMs= */ SystemClock.elapsedRealtime(),
        /* durationMs= */ durationMs,
        /* bufferedPositionMs= */ bufferedPositionMs,
        /* bufferedPercentage= */ bufferedPercentage,
        /* totalBufferedDurationMs= */ totalBufferedDurationMs,
        /* currentLiveOffsetMs= */ C.TIME_UNSET,
        /* contentDurationMs= */ durationMs,
        /* contentBufferedPositionMs= */ bufferedPositionMs);
  }

  // Media 1.0 variables
  private static final class LegacyPlayerInfo {

    @Nullable public final MediaControllerCompat.PlaybackInfo playbackInfoCompat;
    @Nullable public final PlaybackStateCompat playbackStateCompat;
    @Nullable public final MediaMetadataCompat mediaMetadataCompat;
    public final List<QueueItem> queue;
    @Nullable public final CharSequence queueTitle;
    @PlaybackStateCompat.RepeatMode public final int repeatMode;
    @PlaybackStateCompat.ShuffleMode public final int shuffleMode;
    public final Bundle sessionExtras;

    public LegacyPlayerInfo() {
      playbackInfoCompat = null;
      playbackStateCompat = null;
      mediaMetadataCompat = null;
      queue = Collections.emptyList();
      queueTitle = null;
      repeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
      shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_NONE;
      sessionExtras = Bundle.EMPTY;
    }

    public LegacyPlayerInfo(
        @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat,
        @Nullable PlaybackStateCompat playbackStateCompat,
        @Nullable MediaMetadataCompat mediaMetadataCompat,
        List<QueueItem> queue,
        @Nullable CharSequence queueTitle,
        @PlaybackStateCompat.RepeatMode int repeatMode,
        @PlaybackStateCompat.ShuffleMode int shuffleMode,
        @Nullable Bundle sessionExtras) {
      this.playbackInfoCompat = playbackInfoCompat;
      this.playbackStateCompat = playbackStateCompat;
      this.mediaMetadataCompat = mediaMetadataCompat;
      this.queue = checkNotNull(queue);
      this.queueTitle = queueTitle;
      this.repeatMode = repeatMode;
      this.shuffleMode = shuffleMode;
      this.sessionExtras = sessionExtras != null ? sessionExtras : Bundle.EMPTY;
    }

    public LegacyPlayerInfo(LegacyPlayerInfo other) {
      playbackInfoCompat = other.playbackInfoCompat;
      playbackStateCompat = other.playbackStateCompat;
      mediaMetadataCompat = other.mediaMetadataCompat;
      queue = other.queue;
      queueTitle = other.queueTitle;
      repeatMode = other.repeatMode;
      shuffleMode = other.shuffleMode;
      sessionExtras = other.sessionExtras;
    }

    @CheckResult
    public LegacyPlayerInfo copyWithExtraBinderGetters(
        @Nullable PlaybackStateCompat playbackStateCompat,
        @PlaybackStateCompat.RepeatMode int repeatMode,
        @PlaybackStateCompat.ShuffleMode int shuffleMode) {
      return new LegacyPlayerInfo(
          playbackInfoCompat,
          playbackStateCompat,
          mediaMetadataCompat,
          queue,
          queueTitle,
          repeatMode,
          shuffleMode,
          sessionExtras);
    }

    @CheckResult
    public LegacyPlayerInfo copyWithPlaybackStateCompat(
        @Nullable PlaybackStateCompat playbackStateCompat) {
      return new LegacyPlayerInfo(
          playbackInfoCompat,
          playbackStateCompat,
          mediaMetadataCompat,
          queue,
          queueTitle,
          repeatMode,
          shuffleMode,
          sessionExtras);
    }

    @CheckResult
    public LegacyPlayerInfo copyWithMediaMetadataCompat(
        @Nullable MediaMetadataCompat mediaMetadataCompat) {
      return new LegacyPlayerInfo(
          playbackInfoCompat,
          playbackStateCompat,
          mediaMetadataCompat,
          queue,
          queueTitle,
          repeatMode,
          shuffleMode,
          sessionExtras);
    }

    @CheckResult
    public LegacyPlayerInfo copyWithQueue(List<QueueItem> queue) {
      return new LegacyPlayerInfo(
          playbackInfoCompat,
          playbackStateCompat,
          mediaMetadataCompat,
          queue,
          queueTitle,
          repeatMode,
          shuffleMode,
          sessionExtras);
    }

    @CheckResult
    public LegacyPlayerInfo copyWithQueueTitle(@Nullable CharSequence queueTitle) {
      return new LegacyPlayerInfo(
          playbackInfoCompat,
          playbackStateCompat,
          mediaMetadataCompat,
          queue,
          queueTitle,
          repeatMode,
          shuffleMode,
          sessionExtras);
    }

    @CheckResult
    public LegacyPlayerInfo copyWithPlaybackInfoCompat(
        @Nullable MediaControllerCompat.PlaybackInfo playbackInfo) {
      return new LegacyPlayerInfo(
          playbackInfo,
          playbackStateCompat,
          mediaMetadataCompat,
          queue,
          queueTitle,
          repeatMode,
          shuffleMode,
          sessionExtras);
    }

    @CheckResult
    public LegacyPlayerInfo copyWithRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) {
      return new LegacyPlayerInfo(
          playbackInfoCompat,
          playbackStateCompat,
          mediaMetadataCompat,
          queue,
          queueTitle,
          repeatMode,
          shuffleMode,
          sessionExtras);
    }

    @CheckResult
    public LegacyPlayerInfo copyWithShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
      return new LegacyPlayerInfo(
          playbackInfoCompat,
          playbackStateCompat,
          mediaMetadataCompat,
          queue,
          queueTitle,
          repeatMode,
          shuffleMode,
          sessionExtras);
    }
  }

  private static class ControllerInfo {

    public final PlayerInfo playerInfo;
    public final SessionCommands availableSessionCommands;
    public final Commands availablePlayerCommands;
    public final ImmutableList<CommandButton> customLayout;
    public final Bundle sessionExtras;

    public ControllerInfo() {
      playerInfo = PlayerInfo.DEFAULT.copyWithTimeline(QueueTimeline.DEFAULT);
      availableSessionCommands = SessionCommands.EMPTY;
      availablePlayerCommands = Commands.EMPTY;
      customLayout = ImmutableList.of();
      sessionExtras = Bundle.EMPTY;
    }

    public ControllerInfo(
        PlayerInfo playerInfo,
        SessionCommands availableSessionCommands,
        Commands availablePlayerCommands,
        ImmutableList<CommandButton> customLayout,
        Bundle sessionExtras) {
      this.playerInfo = playerInfo;
      this.availableSessionCommands = availableSessionCommands;
      this.availablePlayerCommands = availablePlayerCommands;
      this.customLayout = customLayout;
      this.sessionExtras = sessionExtras;
    }
  }
}
