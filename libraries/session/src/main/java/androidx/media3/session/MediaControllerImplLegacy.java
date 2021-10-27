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

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID;
import static androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK;
import static androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED;
import static androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION;
import static androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYBACK_PARAMETERS_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYER_ERROR;
import static androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static androidx.media3.common.Player.EVENT_TIMELINE_CHANGED;
import static androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK;
import static androidx.media3.common.Player.PLAYBACK_SUPPRESSION_REASON_NONE;
import static androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
import static androidx.media3.common.Player.STATE_IDLE;
import static androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.session.MediaConstants.ARGUMENT_CAPTIONING_ENABLED;
import static androidx.media3.session.MediaConstants.MEDIA_URI_QUERY_ID;
import static androidx.media3.session.MediaConstants.MEDIA_URI_QUERY_QUERY;
import static androidx.media3.session.MediaConstants.MEDIA_URI_QUERY_URI;
import static androidx.media3.session.MediaConstants.MEDIA_URI_SET_MEDIA_URI_PREFIX;
import static androidx.media3.session.MediaConstants.SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED;
import static androidx.media3.session.MediaConstants.SESSION_COMMAND_ON_EXTRAS_CHANGED;
import static androidx.media3.session.MediaUtils.POSITION_DIFF_TOLERANCE_MS;
import static androidx.media3.session.MediaUtils.calculateBufferedPercentage;
import static androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED;
import static androidx.media3.session.SessionResult.RESULT_SUCCESS;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.PendingIntent;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.IllegalSeekPositionException;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Commands;
import androidx.media3.common.Player.DiscontinuityReason;
import androidx.media3.common.Player.Events;
import androidx.media3.common.Player.Listener;
import androidx.media3.common.Player.MediaItemTransitionReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Player.State;
import androidx.media3.common.Rating;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ListenerSet;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/* package */ class MediaControllerImplLegacy implements MediaController.MediaControllerImpl {

  private static final String TAG = "MCImplLegacy";

  private static final long AGGREGATES_CALLBACKS_WITHIN_TIMEOUT_MS = 500L;
  private static final int VOLUME_FLAGS = AudioManager.FLAG_SHOW_UI;

  final Context context;

  private final SessionToken token;

  final MediaController instance;

  private final ListenerSet<Listener> listeners;

  private final ControllerCompatCallback controllerCompatCallback;

  @Nullable private MediaControllerCompat controllerCompat;

  @Nullable private MediaBrowserCompat browserCompat;

  private boolean released;

  private boolean connected;

  @Nullable private SetMediaUriRequest pendingSetMediaUriRequest;

  private LegacyPlayerInfo legacyPlayerInfo;

  private LegacyPlayerInfo pendingLegacyPlayerInfo;

  private ControllerInfo controllerInfo;

  public MediaControllerImplLegacy(Context context, MediaController instance, SessionToken token) {
    // Initialize default values.
    legacyPlayerInfo = new LegacyPlayerInfo();
    pendingLegacyPlayerInfo = new LegacyPlayerInfo();
    controllerInfo = new ControllerInfo();
    listeners =
        new ListenerSet<>(
            instance.getApplicationLooper(),
            Clock.DEFAULT,
            (listener, flags) -> listener.onEvents(instance, new Events(flags)));

    // Initialize members.
    this.context = context;
    this.instance = instance;
    controllerCompatCallback = new ControllerCompatCallback();
    this.token = token;

    if (this.token.getType() == SessionToken.TYPE_SESSION) {
      browserCompat = null;
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
    if (controllerInfo.playerInfo.playbackState != STATE_IDLE) {
      maskedPlayerInfo =
          maskedPlayerInfo.copyWithPlaybackState(
              STATE_IDLE, /* playerError= */ controllerInfo.playerInfo.playerError);
    }
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ C.INDEX_UNSET,
        /* mediaItemTransitionReason= */ C.INDEX_UNSET);

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
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            controllerInfo.playerInfo.copyWithPlayWhenReady(
                /* playWhenReady= */ true,
                PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                PLAYBACK_SUPPRESSION_REASON_NONE),
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ C.INDEX_UNSET,
        /* mediaItemTransitionReason= */ C.INDEX_UNSET);

    if (pendingSetMediaUriRequest == null) {
      controllerCompat.getTransportControls().play();
    } else {
      switch (pendingSetMediaUriRequest.type) {
        case MEDIA_URI_QUERY_ID:
          controllerCompat
              .getTransportControls()
              .playFromMediaId(pendingSetMediaUriRequest.value, pendingSetMediaUriRequest.extras);
          break;
        case MEDIA_URI_QUERY_QUERY:
          controllerCompat
              .getTransportControls()
              .playFromSearch(pendingSetMediaUriRequest.value, pendingSetMediaUriRequest.extras);
          break;
        case MEDIA_URI_QUERY_URI:
          controllerCompat
              .getTransportControls()
              .playFromUri(
                  Uri.parse(pendingSetMediaUriRequest.value), pendingSetMediaUriRequest.extras);
          break;
        default:
          throw new AssertionError("Unexpected type " + pendingSetMediaUriRequest.type);
      }
      pendingSetMediaUriRequest.result.set(new SessionResult(RESULT_SUCCESS));
      pendingSetMediaUriRequest = null;
    }
  }

  @Override
  public void pause() {
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            controllerInfo.playerInfo.copyWithPlayWhenReady(
                /* playWhenReady= */ false,
                PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                PLAYBACK_SUPPRESSION_REASON_NONE),
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ C.INDEX_UNSET,
        /* mediaItemTransitionReason= */ C.INDEX_UNSET);

    controllerCompat.getTransportControls().pause();
  }

  @Override
  public void prepare() {
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            controllerInfo.playerInfo.copyWithPlaybackState(
                controllerInfo.playerInfo.timeline.isEmpty()
                    ? Player.STATE_ENDED
                    : Player.STATE_BUFFERING,
                /* playerError= */ null),
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ C.INDEX_UNSET,
        /* mediaItemTransitionReason= */ C.INDEX_UNSET);

    if (pendingSetMediaUriRequest == null) {
      controllerCompat.getTransportControls().prepare();
    } else {
      switch (pendingSetMediaUriRequest.type) {
        case MEDIA_URI_QUERY_ID:
          controllerCompat
              .getTransportControls()
              .prepareFromMediaId(
                  pendingSetMediaUriRequest.value, pendingSetMediaUriRequest.extras);
          break;
        case MEDIA_URI_QUERY_QUERY:
          controllerCompat
              .getTransportControls()
              .prepareFromSearch(pendingSetMediaUriRequest.value, pendingSetMediaUriRequest.extras);
          break;
        case MEDIA_URI_QUERY_URI:
          controllerCompat
              .getTransportControls()
              .prepareFromUri(
                  Uri.parse(pendingSetMediaUriRequest.value), pendingSetMediaUriRequest.extras);
          break;
        default:
          throw new AssertionError("Unexpected type " + pendingSetMediaUriRequest.type);
      }
      pendingSetMediaUriRequest.result.set(new SessionResult(RESULT_SUCCESS));
      pendingSetMediaUriRequest = null;
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
    int currentMediaItemIndex = getCurrentMediaItemIndex();
    Timeline currentTimeline = controllerInfo.playerInfo.timeline;
    if (currentMediaItemIndex != mediaItemIndex
        && (mediaItemIndex < 0 || mediaItemIndex >= currentTimeline.getWindowCount())) {
      throw new IllegalSeekPositionException(currentTimeline, mediaItemIndex, positionMs);
    }
    if (isPlayingAd()) {
      return;
    }
    int newMediaItemIndex = currentMediaItemIndex;
    @MediaItemTransitionReason int mediaItemTransitionReason = C.INDEX_UNSET;
    if (mediaItemIndex != currentMediaItemIndex) {
      QueueTimeline queueTimeline = (QueueTimeline) controllerInfo.playerInfo.timeline;
      long queueId = queueTimeline.getQueueId(mediaItemIndex);
      if (queueId != QueueItem.UNKNOWN_ID) {
        controllerCompat.getTransportControls().skipToQueueItem(queueId);
        newMediaItemIndex = mediaItemIndex;
        mediaItemTransitionReason = MEDIA_ITEM_TRANSITION_REASON_SEEK;
      } else {
        Log.w(
            TAG,
            "Cannot seek to new media item due to the missing queue Id at media item,"
                + " mediaItemIndex="
                + mediaItemIndex);
      }
    }
    @DiscontinuityReason int discontinuityReason;
    long currentPositionMs = getCurrentPosition();
    long newPositionMs;
    if (positionMs == C.TIME_UNSET) {
      newPositionMs = currentPositionMs;
      discontinuityReason = C.INDEX_UNSET;
    } else {
      controllerCompat.getTransportControls().seekTo(positionMs);
      newPositionMs = positionMs;
      discontinuityReason = DISCONTINUITY_REASON_SEEK;
    }

    long newDurationMs;
    long newBufferedPositionMs;
    int newBufferedPercentage;
    long newTotalBufferedDurationMs;
    if (mediaItemTransitionReason == C.INDEX_UNSET) {
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
            newPositionMs);
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
            controllerInfo.customLayout);
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
    return controllerInfo.playerInfo.sessionPositionInfo.positionInfo.positionMs;
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
        legacyPlayerInfo.mediaMetadataCompat.getString(METADATA_KEY_MEDIA_ID);
    if (mediaId.equals(currentMediaItemMediaId)) {
      controllerCompat.getTransportControls().setRating(MediaUtils.convertToRatingCompat(rating));
    }
    return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
  }

  @Override
  public ListenableFuture<SessionResult> setRating(Rating rating) {
    controllerCompat.getTransportControls().setRating(MediaUtils.convertToRatingCompat(rating));
    return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
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
              controllerInfo.customLayout);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ C.INDEX_UNSET,
          /* mediaItemTransitionReason= */ C.INDEX_UNSET);
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
              controllerInfo.customLayout);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ C.INDEX_UNSET,
          /* mediaItemTransitionReason= */ C.INDEX_UNSET);
    }

    controllerCompat.getTransportControls().setPlaybackSpeed(speed);
  }

  @Override
  public ListenableFuture<SessionResult> sendCustomCommand(SessionCommand command, Bundle args) {
    if (controllerInfo.availableSessionCommands.contains(command)) {
      controllerCompat.getTransportControls().sendCustomAction(command.customAction, args);
      return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
    }
    SettableFuture<SessionResult> result = SettableFuture.create();
    ResultReceiver cb =
        new ResultReceiver(instance.applicationHandler) {
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
  public void setMediaItem(MediaItem unusedMediaItem) {
    Log.w(TAG, "Session doesn't support setting media items");
  }

  @Override
  public void setMediaItem(MediaItem unusedMediaItem, long unusedStartPositionMs) {
    Log.w(TAG, "Session doesn't support setting media items");
  }

  @Override
  public void setMediaItem(MediaItem unusedMediaItem, boolean unusedResetPosition) {
    Log.w(TAG, "Session doesn't support setting media items");
  }

  @Override
  public void setMediaItems(List<MediaItem> unusedMediaItems) {
    Log.w(TAG, "Session doesn't support setting media items");
  }

  @Override
  public void setMediaItems(List<MediaItem> unusedMediaItems, boolean unusedResetPosition) {
    Log.w(TAG, "Session doesn't support setting media items");
  }

  @Override
  public void setMediaItems(
      List<MediaItem> unusedMediaItems, int unusedStartIndex, long unusedStartPositionMs) {
    Log.w(TAG, "Session doesn't support setting media items");
  }

  @Override
  public ListenableFuture<SessionResult> setMediaUri(Uri uri, Bundle extras) {
    if (pendingSetMediaUriRequest != null) {
      Log.w(
          TAG,
          "SetMediaUri() is called multiple times without prepare() nor play()."
              + " Previous call will be skipped.");
      pendingSetMediaUriRequest.result.set(new SessionResult(RESULT_INFO_SKIPPED));
      pendingSetMediaUriRequest = null;
    }
    SettableFuture<SessionResult> result = SettableFuture.create();
    if (uri.toString().startsWith(MEDIA_URI_SET_MEDIA_URI_PREFIX)
        && uri.getQueryParameterNames().size() == 1) {
      String queryParameterName = uri.getQueryParameterNames().iterator().next();
      if (TextUtils.equals(queryParameterName, MEDIA_URI_QUERY_ID)
          || TextUtils.equals(queryParameterName, MEDIA_URI_QUERY_QUERY)
          || TextUtils.equals(queryParameterName, MEDIA_URI_QUERY_URI)) {
        pendingSetMediaUriRequest =
            new SetMediaUriRequest(
                queryParameterName, uri.getQueryParameter(queryParameterName), extras, result);
      }
    }
    if (pendingSetMediaUriRequest == null) {
      pendingSetMediaUriRequest =
          new SetMediaUriRequest(MEDIA_URI_QUERY_URI, uri.toString(), extras, result);
    }
    return result;
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
    if (mediaItems.isEmpty()) {
      return;
    }
    index = min(index, getCurrentTimeline().getWindowCount());

    QueueTimeline queueTimeline = (QueueTimeline) controllerInfo.playerInfo.timeline;
    QueueTimeline newQueueTimeline = queueTimeline.copyWithNewMediaItems(index, mediaItems);
    int currentMediaItemIndex = getCurrentMediaItemIndex();
    int newCurrentMediaItemIndex =
        calculateCurrentItemIndexAfterAddItems(currentMediaItemIndex, index, mediaItems.size());
    PlayerInfo maskedPlayerInfo =
        controllerInfo.playerInfo.copyWithTimeline(newQueueTimeline, newCurrentMediaItemIndex);
    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ C.INDEX_UNSET,
        /* mediaItemTransitionReason= */ C.INDEX_UNSET);

    for (int i = 0; i < mediaItems.size(); i++) {
      MediaItem mediaItem = mediaItems.get(i);
      controllerCompat.addQueueItem(
          MediaUtils.convertToMediaDescriptionCompat(mediaItem), index + i);
    }
  }

  @Override
  public void removeMediaItem(int index) {
    removeMediaItems(/* fromIndex= */ index, /* toIndex= */ index + 1);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    int windowCount = getCurrentTimeline().getWindowCount();
    toIndex = min(toIndex, windowCount);
    if (fromIndex >= toIndex) {
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
      Log.w(
          TAG,
          "Currently playing item is removed. Assumes item at "
              + newCurrentMediaItemIndex
              + " is the"
              + " new current item");
    }
    PlayerInfo maskedPlayerInfo =
        controllerInfo.playerInfo.copyWithTimeline(newQueueTimeline, newCurrentMediaItemIndex);

    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ C.INDEX_UNSET,
        /* mediaItemTransitionReason= */ C.INDEX_UNSET);

    for (int i = fromIndex; i < toIndex && i < legacyPlayerInfo.queue.size(); i++) {
      controllerCompat.removeQueueItem(legacyPlayerInfo.queue.get(i).getDescription());
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
    QueueTimeline queueTimeline = (QueueTimeline) controllerInfo.playerInfo.timeline;
    int size = queueTimeline.getWindowCount();
    toIndex = min(toIndex, size);
    if (fromIndex >= toIndex) {
      return;
    }
    int moveItemsSize = toIndex - fromIndex;
    int lastItemIndexAfterRemove = size - moveItemsSize - 1;
    newIndex = min(newIndex, lastItemIndexAfterRemove);

    int currentMediaItemIndex = getCurrentMediaItemIndex();
    int currentMediaItemIndexAfterRemove =
        calculateCurrentItemIndexAfterRemoveItems(currentMediaItemIndex, fromIndex, toIndex);
    if (currentMediaItemIndexAfterRemove == C.INDEX_UNSET) {
      currentMediaItemIndexAfterRemove =
          Util.constrainValue(fromIndex, /* min= */ 0, /* toIndex= */ lastItemIndexAfterRemove);
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
        controllerInfo.playerInfo.copyWithTimeline(newQueueTimeline, newCurrentMediaItemIndex);

    ControllerInfo maskedControllerInfo =
        new ControllerInfo(
            maskedPlayerInfo,
            controllerInfo.availableSessionCommands,
            controllerInfo.availablePlayerCommands,
            controllerInfo.customLayout);
    updateStateMaskedControllerInfo(
        maskedControllerInfo,
        /* discontinuityReason= */ C.INDEX_UNSET,
        /* mediaItemTransitionReason= */ C.INDEX_UNSET);

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
  @RepeatMode
  public int getRepeatMode() {
    return controllerInfo.playerInfo.repeatMode;
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    @RepeatMode int currentRepeatMode = getRepeatMode();
    if (repeatMode != currentRepeatMode) {
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithRepeatMode(repeatMode),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ C.INDEX_UNSET,
          /* mediaItemTransitionReason= */ C.INDEX_UNSET);
    }

    controllerCompat
        .getTransportControls()
        .setRepeatMode(MediaUtils.convertToPlaybackStateCompatRepeatMode(repeatMode));
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
              controllerInfo.customLayout);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ C.INDEX_UNSET,
          /* mediaItemTransitionReason= */ C.INDEX_UNSET);
    }

    controllerCompat
        .getTransportControls()
        .setShuffleMode(MediaUtils.convertToPlaybackStateCompatShuffleMode(shuffleModeEnabled));
  }

  @Override
  public VideoSize getVideoSize() {
    Log.w(TAG, "Session doesn't support getting VideoSize");
    return VideoSize.UNKNOWN;
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
  public List<Cue> getCurrentCues() {
    Log.w(TAG, "Session doesn't support getting Cue");
    return ImmutableList.of();
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

  @Override
  public void setDeviceVolume(int volume) {
    DeviceInfo deviceInfo = getDeviceInfo();
    int minVolume = deviceInfo.minVolume;
    int maxVolume = deviceInfo.maxVolume;
    if (minVolume <= volume && volume <= maxVolume) {
      boolean isDeviceMuted = isDeviceMuted();
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithDeviceVolume(volume, isDeviceMuted),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ C.INDEX_UNSET,
          /* mediaItemTransitionReason= */ C.INDEX_UNSET);
    }

    controllerCompat.setVolumeTo(volume, VOLUME_FLAGS);
  }

  @Override
  public void increaseDeviceVolume() {
    int volume = getDeviceVolume();
    int maxVolume = getDeviceInfo().maxVolume;
    if (volume + 1 <= maxVolume) {
      boolean isDeviceMuted = isDeviceMuted();

      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithDeviceVolume(volume + 1, isDeviceMuted),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ C.INDEX_UNSET,
          /* mediaItemTransitionReason= */ C.INDEX_UNSET);
    }
    controllerCompat.adjustVolume(AudioManager.ADJUST_RAISE, VOLUME_FLAGS);
  }

  @Override
  public void decreaseDeviceVolume() {
    int volume = getDeviceVolume();
    int minVolume = getDeviceInfo().minVolume;

    if (volume - 1 >= minVolume) {
      boolean isDeviceMuted = isDeviceMuted();
      ControllerInfo maskedControllerInfo =
          new ControllerInfo(
              controllerInfo.playerInfo.copyWithDeviceVolume(volume - 1, isDeviceMuted),
              controllerInfo.availableSessionCommands,
              controllerInfo.availablePlayerCommands,
              controllerInfo.customLayout);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ C.INDEX_UNSET,
          /* mediaItemTransitionReason= */ C.INDEX_UNSET);
    }
    controllerCompat.adjustVolume(AudioManager.ADJUST_LOWER, VOLUME_FLAGS);
  }

  @Override
  public void setDeviceMuted(boolean muted) {
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
              controllerInfo.customLayout);
      updateStateMaskedControllerInfo(
          maskedControllerInfo,
          /* discontinuityReason= */ C.INDEX_UNSET,
          /* mediaItemTransitionReason= */ C.INDEX_UNSET);
    }

    int direction = muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
    controllerCompat.adjustVolume(direction, VOLUME_FLAGS);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (playWhenReady) {
      play();
    } else {
      pause();
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
    return PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  @Override
  @State
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

  // Should be used without a lock to prevent potential deadlock.
  void onConnectedNotLocked() {
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
            controllerCompat.getShuffleMode());
    handleNewLegacyParameters(/* notifyConnected= */ true, newLegacyPlayerInfo);
  }

  private void connectToSession(MediaSessionCompat.Token sessionCompatToken) {
    instance.runOnApplicationLooper(
        () -> {
          controllerCompat = new MediaControllerCompat(context, sessionCompatToken);
          // Note: registerCallback() will invoke MediaControllerCompat.Callback#onSessionReady()
          // if the controller is already ready.
          controllerCompat.registerCallback(controllerCompatCallback, instance.applicationHandler);
        });
    // Post a runnable to prevent callbacks from being called by onConnectedNotLocked()
    // before the constructor returns (b/196941334).
    instance.applicationHandler.post(
        () -> {
          if (!controllerCompat.isSessionReady()) {
            // If the session not ready here, then call onConnectedNotLocked() immediately. The
            // session may be a framework MediaSession and we cannot know whether it can be ready
            // later.
            onConnectedNotLocked();
          }
        });
  }

  private void connectToService() {
    instance.runOnApplicationLooper(
        () -> {
          // BrowserCompat can only be used on the thread that it's created.
          // Create it on the application looper to respect that.
          browserCompat =
              new MediaBrowserCompat(
                  context, token.getComponentName(), new ConnectionCallback(), null);
          browserCompat.connect();
        });
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
            controllerCompat.getFlags(),
            controllerCompat.isSessionReady(),
            controllerCompat.getRatingType(),
            instance.timeDiffMs);
    Pair<Integer, Integer> reasons =
        calculateDiscontinuityAndTransitionReason(
            legacyPlayerInfo,
            controllerInfo,
            newLegacyPlayerInfo,
            newControllerInfo,
            instance.timeDiffMs);
    updateControllerInfo(
        notifyConnected,
        newLegacyPlayerInfo,
        newControllerInfo,
        /* discontinuityReason= */ reasons.first,
        /* mediaItemTransitionReason= */ reasons.second);
  }

  private void updateStateMaskedControllerInfo(
      ControllerInfo newControllerInfo,
      @Player.DiscontinuityReason int discontinuityReason,
      @Player.MediaItemTransitionReason int mediaItemTransitionReason) {
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

  private void updateControllerInfo(
      boolean notifyConnected,
      LegacyPlayerInfo newLegacyPlayerInfo,
      ControllerInfo newControllerInfo,
      @Player.DiscontinuityReason int discontinuityReason,
      @Player.MediaItemTransitionReason int mediaItemTransitionReason) {
    LegacyPlayerInfo oldLegacyPlayerInfo = legacyPlayerInfo;
    ControllerInfo oldControllerInfo = controllerInfo;
    if (legacyPlayerInfo != newLegacyPlayerInfo) {
      legacyPlayerInfo = new LegacyPlayerInfo(newLegacyPlayerInfo);
    }
    pendingLegacyPlayerInfo = legacyPlayerInfo;
    controllerInfo = newControllerInfo;

    if (notifyConnected) {
      instance.notifyAccepted();
      if (!oldControllerInfo.customLayout.equals(newControllerInfo.customLayout)) {
        instance.notifyControllerListener(
            listener ->
                ignoreFuture(listener.onSetCustomLayout(instance, newControllerInfo.customLayout)));
      }
      return;
    }
    if (!oldControllerInfo.playerInfo.timeline.equals(newControllerInfo.playerInfo.timeline)) {
      listeners.queueEvent(
          EVENT_TIMELINE_CHANGED,
          (listener) ->
              listener.onTimelineChanged(
                  newControllerInfo.playerInfo.timeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    }
    if (!Util.areEqual(oldLegacyPlayerInfo.queueTitle, newLegacyPlayerInfo.queueTitle)) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          C.INDEX_UNSET,
          (listener) ->
              listener.onPlaylistMetadataChanged(newControllerInfo.playerInfo.playlistMetadata));
    }
    if (discontinuityReason != C.INDEX_UNSET) {
      listeners.queueEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          (listener) ->
              listener.onPositionDiscontinuity(
                  oldControllerInfo.playerInfo.sessionPositionInfo.positionInfo,
                  newControllerInfo.playerInfo.sessionPositionInfo.positionInfo,
                  discontinuityReason));
    }
    if (mediaItemTransitionReason != C.INDEX_UNSET) {
      listeners.queueEvent(
          EVENT_MEDIA_ITEM_TRANSITION,
          (listener) ->
              listener.onMediaItemTransition(
                  newControllerInfo.playerInfo.getCurrentMediaItem(), mediaItemTransitionReason));
    }
    if (!MediaUtils.areEqualError(
        oldLegacyPlayerInfo.playbackStateCompat, newLegacyPlayerInfo.playbackStateCompat)) {
      PlaybackException error =
          MediaUtils.convertToPlaybackException(newLegacyPlayerInfo.playbackStateCompat);
      listeners.queueEvent(EVENT_PLAYER_ERROR, (listener) -> listener.onPlayerErrorChanged(error));
      if (error != null) {
        listeners.queueEvent(EVENT_PLAYER_ERROR, (listener) -> listener.onPlayerError(error));
      }
    }
    if (oldLegacyPlayerInfo.mediaMetadataCompat != newLegacyPlayerInfo.mediaMetadataCompat) {
      listeners.queueEvent(
          EVENT_MEDIA_METADATA_CHANGED,
          (listener) -> listener.onMediaMetadataChanged(controllerInfo.playerInfo.mediaMetadata));
    }
    if (oldControllerInfo.playerInfo.playbackState != newControllerInfo.playerInfo.playbackState) {
      listeners.queueEvent(
          EVENT_PLAYBACK_STATE_CHANGED,
          (listener) ->
              listener.onPlaybackStateChanged(newControllerInfo.playerInfo.playbackState));
    }
    if (oldControllerInfo.playerInfo.playWhenReady != newControllerInfo.playerInfo.playWhenReady) {
      listeners.queueEvent(
          EVENT_PLAY_WHEN_READY_CHANGED,
          (listener) ->
              listener.onPlayWhenReadyChanged(
                  newControllerInfo.playerInfo.playWhenReady,
                  Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE));
    }
    if (oldControllerInfo.playerInfo.isPlaying != newControllerInfo.playerInfo.isPlaying) {
      listeners.queueEvent(
          EVENT_IS_PLAYING_CHANGED,
          (listener) -> listener.onIsPlayingChanged(newControllerInfo.playerInfo.isPlaying));
    }
    if (!oldControllerInfo.playerInfo.playbackParameters.equals(
        newControllerInfo.playerInfo.playbackParameters)) {
      listeners.queueEvent(
          EVENT_PLAYBACK_PARAMETERS_CHANGED,
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
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          C.INDEX_UNSET,
          (listener) ->
              listener.onAudioAttributesChanged(newControllerInfo.playerInfo.audioAttributes));
    }
    if (!oldControllerInfo.playerInfo.deviceInfo.equals(newControllerInfo.playerInfo.deviceInfo)) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          C.INDEX_UNSET,
          (listener) -> listener.onDeviceInfoChanged(newControllerInfo.playerInfo.deviceInfo));
    }
    if (oldControllerInfo.playerInfo.deviceVolume != newControllerInfo.playerInfo.deviceVolume
        || oldControllerInfo.playerInfo.deviceMuted != newControllerInfo.playerInfo.deviceMuted) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          C.INDEX_UNSET,
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
      instance.notifyControllerListener(
          listener ->
              listener.onAvailableSessionCommandsChanged(
                  instance, newControllerInfo.availableSessionCommands));
    }
    if (!oldControllerInfo.customLayout.equals(newControllerInfo.customLayout)) {
      instance.notifyControllerListener(
          listener ->
              ignoreFuture(listener.onSetCustomLayout(instance, newControllerInfo.customLayout)));
    }
    listeners.flushEvents();
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
      instance.release();
    }

    @Override
    public void onConnectionFailed() {
      instance.release();
    }
  }

  private final class ControllerCompatCallback extends MediaControllerCompat.Callback {

    private static final int MSG_HANDLE_PENDING_UPDATES = 1;

    private final Handler pendingChangesHandler;

    public ControllerCompatCallback() {
      pendingChangesHandler =
          new Handler(
              instance.applicationHandler.getLooper(),
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
        onConnectedNotLocked();
      } else {
        // Handle the case when extra binder is available after connectToSession().
        // Initial values are notified already, so only notify values that are available when
        // session is ready.
        pendingLegacyPlayerInfo =
            pendingLegacyPlayerInfo.copyWithExtraBinderGetters(
                convertToSafePlaybackStateCompat(controllerCompat.getPlaybackState()),
                controllerCompat.getShuffleMode(),
                controllerCompat.getRepeatMode());
        boolean isCaptioningEnabled = controllerCompat.isCaptioningEnabled();
        onCaptioningEnabledChanged(isCaptioningEnabled);

        pendingChangesHandler.removeMessages(MSG_HANDLE_PENDING_UPDATES);
        handleNewLegacyParameters(/* notifyConnected= */ true, pendingLegacyPlayerInfo);
      }
    }

    @Override
    public void onSessionDestroyed() {
      instance.release();
    }

    @Override
    public void onSessionEvent(String event, Bundle extras) {
      instance.notifyControllerListener(
          listener ->
              ignoreFuture(
                  listener.onCustomCommand(
                      instance, new SessionCommand(event, /* extras= */ Bundle.EMPTY), extras)));
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
      instance.notifyControllerListener(
          listener ->
              ignoreFuture(
                  listener.onCustomCommand(
                      instance,
                      new SessionCommand(
                          SESSION_COMMAND_ON_EXTRAS_CHANGED, /* extras= */ Bundle.EMPTY),
                      extras)));
    }

    @Override
    public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo newPlaybackInfo) {
      pendingLegacyPlayerInfo = pendingLegacyPlayerInfo.copyWithPlaybackInfoCompat(newPlaybackInfo);
      startWaitingForPendingChanges();
    }

    @Override
    public void onCaptioningEnabledChanged(boolean enabled) {
      instance.notifyControllerListener(
          listener -> {
            Bundle args = new Bundle();
            args.putBoolean(ARGUMENT_CAPTIONING_ENABLED, enabled);
            ignoreFuture(
                listener.onCustomCommand(
                    instance,
                    new SessionCommand(
                        SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED, /* extras= */ Bundle.EMPTY),
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
      long sessionFlags,
      boolean isSessionReady,
      @RatingCompat.Style int ratingType,
      long timeDiffMs) {
    QueueTimeline currentTimeline;
    MediaMetadata mediaMetadata;
    int currentMediaItemIndex;
    MediaMetadata playlistMetadata;
    @RepeatMode int repeatMode;
    boolean shuffleModeEnabled;
    SessionCommands availableSessionCommands;
    Commands availablePlayerCommands;
    ImmutableList<CommandButton> customLayout;

    boolean isQueueChanged = oldLegacyPlayerInfo.queue != newLegacyPlayerInfo.queue;
    currentTimeline =
        isQueueChanged
            ? QueueTimeline.create(newLegacyPlayerInfo.queue)
            : new QueueTimeline((QueueTimeline) oldControllerInfo.playerInfo.timeline);

    boolean isMetadataCompatChanged =
        oldLegacyPlayerInfo.mediaMetadataCompat != newLegacyPlayerInfo.mediaMetadataCompat
            || initialUpdate;
    long oldActiveQueueId = getActiveQueueId(oldLegacyPlayerInfo.playbackStateCompat);
    long newActiveQueueId = getActiveQueueId(newLegacyPlayerInfo.playbackStateCompat);
    boolean isCurrentActiveQueueIdChanged = (oldActiveQueueId != newActiveQueueId) || initialUpdate;
    if (isMetadataCompatChanged || isCurrentActiveQueueIdChanged || isQueueChanged) {
      currentMediaItemIndex = findQueueItemIndex(newLegacyPlayerInfo.queue, newActiveQueueId);
      boolean hasMediaMetadataCompat = newLegacyPlayerInfo.mediaMetadataCompat != null;
      if (hasMediaMetadataCompat && isMetadataCompatChanged) {
        mediaMetadata =
            MediaUtils.convertToMediaMetadata(newLegacyPlayerInfo.mediaMetadataCompat, ratingType);
      } else if (!hasMediaMetadataCompat && isCurrentActiveQueueIdChanged) {
        mediaMetadata =
            (currentMediaItemIndex == C.INDEX_UNSET)
                ? MediaMetadata.EMPTY
                : MediaUtils.convertToMediaMetadata(
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
              MediaUtils.convertToMediaItem(newLegacyPlayerInfo.mediaMetadataCompat, ratingType);
          currentTimeline = currentTimeline.copyWithFakeMediaItem(fakeMediaItem);
          currentMediaItemIndex = currentTimeline.getWindowCount() - 1;
        } else {
          currentTimeline = currentTimeline.copyWithFakeMediaItem(/* fakeMediaItem= */ null);
          // Shouldn't be C.INDEX_UNSET to make getCurrentMediaItemIndex() return masked index.
          // In other words, this index is either the currently playing media item index or the
          // would-be playing index when playing.
          currentMediaItemIndex = 0;
        }
      } else if (currentMediaItemIndex != C.INDEX_UNSET) {
        currentTimeline = currentTimeline.copyWithFakeMediaItem(/* fakeMediaItem= */ null);
        if (hasMediaMetadataCompat) {
          MediaItem mediaItem =
              MediaUtils.convertToMediaItem(
                  currentTimeline.getMediaItemAt(currentMediaItemIndex).mediaId,
                  newLegacyPlayerInfo.mediaMetadataCompat,
                  ratingType);
          currentTimeline =
              currentTimeline.copyWithNewMediaItem(
                  /* replaceIndex= */ currentMediaItemIndex, mediaItem);
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
            : MediaUtils.convertToMediaMetadata(newLegacyPlayerInfo.queueTitle);
    repeatMode = MediaUtils.convertToRepeatMode(newLegacyPlayerInfo.repeatMode);
    shuffleModeEnabled = MediaUtils.convertToShuffleModeEnabled(newLegacyPlayerInfo.shuffleMode);
    if (oldLegacyPlayerInfo.playbackStateCompat != newLegacyPlayerInfo.playbackStateCompat) {
      availableSessionCommands =
          MediaUtils.convertToSessionCommands(
              newLegacyPlayerInfo.playbackStateCompat, isSessionReady);
      customLayout = MediaUtils.convertToCustomLayout(newLegacyPlayerInfo.playbackStateCompat);
    } else {
      availableSessionCommands = oldControllerInfo.availableSessionCommands;
      customLayout = oldControllerInfo.customLayout;
    }
    // Note: Sets the available player command here although it can be obtained before session is
    // ready. It's to follow the decision on MediaController to disallow any commands before
    // connection is made.
    availablePlayerCommands =
        (oldControllerInfo.availablePlayerCommands == Commands.EMPTY)
            ? MediaUtils.convertToPlayerCommands(sessionFlags, isSessionReady)
            : oldControllerInfo.availablePlayerCommands;

    PlaybackException playerError =
        MediaUtils.convertToPlaybackException(newLegacyPlayerInfo.playbackStateCompat);

    long durationMs = MediaUtils.convertToDurationMs(newLegacyPlayerInfo.mediaMetadataCompat);
    long currentPositionMs =
        MediaUtils.convertToCurrentPositionMs(
            newLegacyPlayerInfo.playbackStateCompat,
            newLegacyPlayerInfo.mediaMetadataCompat,
            timeDiffMs);
    long bufferedPositionMs =
        MediaUtils.convertToBufferedPositionMs(
            newLegacyPlayerInfo.playbackStateCompat,
            newLegacyPlayerInfo.mediaMetadataCompat,
            timeDiffMs);
    int bufferedPercentage =
        MediaUtils.convertToBufferedPercentage(
            newLegacyPlayerInfo.playbackStateCompat,
            newLegacyPlayerInfo.mediaMetadataCompat,
            timeDiffMs);
    long totalBufferedDurationMs =
        MediaUtils.convertToTotalBufferedDurationMs(
            newLegacyPlayerInfo.playbackStateCompat,
            newLegacyPlayerInfo.mediaMetadataCompat,
            timeDiffMs);
    boolean isPlayingAd = MediaUtils.convertToIsPlayingAd(newLegacyPlayerInfo.mediaMetadataCompat);
    PlaybackParameters playbackParameters =
        MediaUtils.convertToPlaybackParameters(newLegacyPlayerInfo.playbackStateCompat);
    AudioAttributes audioAttributes =
        MediaUtils.convertToAudioAttributes(newLegacyPlayerInfo.playbackInfoCompat);
    boolean playWhenReady =
        MediaUtils.convertToPlayWhenReady(newLegacyPlayerInfo.playbackStateCompat);
    @State
    int playbackState =
        MediaUtils.convertToPlaybackState(
            newLegacyPlayerInfo.playbackStateCompat,
            newLegacyPlayerInfo.mediaMetadataCompat,
            timeDiffMs);
    boolean isPlaying = MediaUtils.convertToIsPlaying(newLegacyPlayerInfo.playbackStateCompat);
    DeviceInfo deviceInfo = MediaUtils.convertToDeviceInfo(newLegacyPlayerInfo.playbackInfoCompat);
    int deviceVolume = MediaUtils.convertToDeviceVolume(newLegacyPlayerInfo.playbackInfoCompat);
    boolean deviceMuted = MediaUtils.convertToIsDeviceMuted(newLegacyPlayerInfo.playbackInfoCompat);
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

  // Calculate position discontinuity reason and media item transition reason outside of the state
  // masking.
  private static Pair<Integer, Integer> calculateDiscontinuityAndTransitionReason(
      LegacyPlayerInfo oldLegacyPlayerInfo,
      ControllerInfo oldControllerInfo,
      LegacyPlayerInfo newLegacyPlayerInfo,
      ControllerInfo newControllerInfo,
      long timeDiffMs) {
    @Player.DiscontinuityReason int discontinuityReason;
    @Player.MediaItemTransitionReason int mediaItemTransitionReason;
    boolean isOldTimelineEmpty = oldControllerInfo.playerInfo.timeline.isEmpty();
    boolean isNewTimelineEmpty = newControllerInfo.playerInfo.timeline.isEmpty();
    int newCurrentMediaItemIndex =
        newControllerInfo.playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex;
    if (isOldTimelineEmpty && isNewTimelineEmpty) {
      // Still empty Timelines.
      discontinuityReason = C.INDEX_UNSET;
      mediaItemTransitionReason = C.INDEX_UNSET;
    } else if (isOldTimelineEmpty && !isNewTimelineEmpty) {
      // A new timeline has been set.
      discontinuityReason = Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
      mediaItemTransitionReason = Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
    } else {
      MediaItem oldCurrentMediaItem =
          checkStateNotNull(oldControllerInfo.playerInfo.getCurrentMediaItem());
      int oldCurrentMediaItemIndexInNewTimeline =
          ((QueueTimeline) newControllerInfo.playerInfo.timeline).findIndexOf(oldCurrentMediaItem);
      if (oldCurrentMediaItemIndexInNewTimeline == C.INDEX_UNSET) {
        // Old current item is removed.
        discontinuityReason = Player.DISCONTINUITY_REASON_REMOVE;
        mediaItemTransitionReason = Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
      } else if (oldCurrentMediaItemIndexInNewTimeline == newCurrentMediaItemIndex) {
        // Current item is the same.
        long oldCurrentPosition =
            MediaUtils.convertToCurrentPositionMs(
                oldLegacyPlayerInfo.playbackStateCompat,
                oldLegacyPlayerInfo.mediaMetadataCompat,
                timeDiffMs);
        long newCurrentPosition =
            MediaUtils.convertToCurrentPositionMs(
                newLegacyPlayerInfo.playbackStateCompat,
                newLegacyPlayerInfo.mediaMetadataCompat,
                timeDiffMs);
        if (newCurrentPosition == 0
            && newControllerInfo.playerInfo.repeatMode == Player.REPEAT_MODE_ONE) {
          // If the position is reset, then it's probably repeating the same media item.
          discontinuityReason = Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
          mediaItemTransitionReason = Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT;
        } else if (Math.abs(oldCurrentPosition - newCurrentPosition) > POSITION_DIFF_TOLERANCE_MS) {
          // Unexpected position discontinuity within the same media item.
          discontinuityReason = Player.DISCONTINUITY_REASON_INTERNAL;
          mediaItemTransitionReason = C.INDEX_UNSET;
        } else {
          discontinuityReason = C.INDEX_UNSET;
          mediaItemTransitionReason = C.INDEX_UNSET;
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
      @RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      SessionCommands availableSessionCommands,
      Commands availablePlayerCommands,
      ImmutableList<CommandButton> customLayout,
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
        createPositionInfo(currentMediaItemIndex, currentMediaItem, currentPositionMs);

    SessionPositionInfo sessionPositionInfo =
        new SessionPositionInfo(
            /* positionInfo= */ positionInfo,
            /* isPlayingAd= */ isPlayingAd,
            /* eventTimeMs= */ C.TIME_UNSET,
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
            /* mediaItemTransitionReason= */ C.INDEX_UNSET,
            /* sessionPositionInfo= */ sessionPositionInfo,
            /* oldPositionInfo= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
            /* newPositionInfo= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
            /* discontinuityReason= */ C.INDEX_UNSET,
            /* playbackParameters= */ playbackParameters,
            /* repeatMode= */ repeatMode,
            /* shuffleModeEnabled= */ shuffleModeEnabled,
            /* videoSize= */ VideoSize.UNKNOWN,
            /* timeline= */ currentTimeline,
            /* playlistMetadata= */ playlistMetadata,
            /* volume= */ deviceVolume,
            /* audioAttributes= */ audioAttributes,
            /* cues= */ Collections.emptyList(),
            /* deviceInfo= */ deviceInfo,
            /* deviceVolume= */ deviceVolume,
            /* deviceMuted= */ deviceMuted,
            /* playWhenReady= */ playWhenReady,
            /* playWhenReadyChangedReason= */ C.INDEX_UNSET,
            /* playbackSuppressionReason= */ C.INDEX_UNSET,
            /* playbackState= */ playbackState,
            /* isPlaying= */ isPlaying,
            /* isLoading= */ false,
            /* mediaMetadata= */ mediaMetadata,
            seekBackIncrementMs,
            seekForwardIncrementMs,
            /* maxSeekToPreviousPositionMs= */ 0L,
            /* parameters= */ TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT);

    return new ControllerInfo(
        playerInfo, availableSessionCommands, availablePlayerCommands, customLayout);
  }

  private static PositionInfo createPositionInfo(
      int mediaItemIndex, @Nullable MediaItem mediaItem, long currentPositionMs) {
    return new PositionInfo(
        /* windowUid= */ null,
        /* mediaItemIndex= */ mediaItemIndex,
        mediaItem,
        /* periodUid= */ null,
        /* periodIndex= */ mediaItemIndex,
        /* positionMs= */ currentPositionMs,
        /* contentPositionMs= */ currentPositionMs,
        /* adGroupIndex= */ C.INDEX_UNSET,
        /* adIndexInAdGroup= */ C.INDEX_UNSET);
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

  private static final class SetMediaUriRequest {

    public final String type;
    public final String value;
    public final Bundle extras;
    public final SettableFuture<SessionResult> result;

    public SetMediaUriRequest(
        String type, String value, Bundle extras, SettableFuture<SessionResult> result) {
      this.type = type;
      this.value = value;
      this.extras = extras;
      this.result = result;
    }
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

    public LegacyPlayerInfo() {
      playbackInfoCompat = null;
      playbackStateCompat = null;
      mediaMetadataCompat = null;
      queue = Collections.emptyList();
      queueTitle = null;
      repeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
      shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_NONE;
    }

    public LegacyPlayerInfo(
        @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat,
        @Nullable PlaybackStateCompat playbackStateCompat,
        @Nullable MediaMetadataCompat mediaMetadataCompat,
        List<QueueItem> queue,
        @Nullable CharSequence queueTitle,
        @PlaybackStateCompat.RepeatMode int repeatMode,
        @PlaybackStateCompat.ShuffleMode int shuffleMode) {
      this.playbackInfoCompat = playbackInfoCompat;
      this.playbackStateCompat = playbackStateCompat;
      this.mediaMetadataCompat = mediaMetadataCompat;
      this.queue = checkNotNull(queue);
      this.queueTitle = queueTitle;
      this.repeatMode = repeatMode;
      this.shuffleMode = shuffleMode;
    }

    public LegacyPlayerInfo(LegacyPlayerInfo other) {
      playbackInfoCompat = other.playbackInfoCompat;
      playbackStateCompat = other.playbackStateCompat;
      mediaMetadataCompat = other.mediaMetadataCompat;
      queue = other.queue;
      queueTitle = other.queueTitle;
      repeatMode = other.repeatMode;
      shuffleMode = other.shuffleMode;
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
          shuffleMode);
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
          shuffleMode);
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
          shuffleMode);
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
          shuffleMode);
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
          shuffleMode);
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
          shuffleMode);
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
          shuffleMode);
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
          shuffleMode);
    }
  }

  private static class ControllerInfo {

    public final PlayerInfo playerInfo;
    public final SessionCommands availableSessionCommands;
    public final Commands availablePlayerCommands;
    public final ImmutableList<CommandButton> customLayout;

    public ControllerInfo() {
      playerInfo = PlayerInfo.DEFAULT.copyWithTimeline(QueueTimeline.DEFAULT);
      availableSessionCommands = SessionCommands.EMPTY;
      availablePlayerCommands = Commands.EMPTY;
      customLayout = ImmutableList.of();
    }

    public ControllerInfo(
        PlayerInfo playerInfo,
        SessionCommands availableSessionCommands,
        Commands availablePlayerCommands,
        ImmutableList<CommandButton> customLayout) {
      this.playerInfo = playerInfo;
      this.availableSessionCommands = availableSessionCommands;
      this.availablePlayerCommands = availablePlayerCommands;
      this.customLayout = customLayout;
    }
  }
}
