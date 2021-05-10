/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.Math.min;

import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.BasePlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.cast.framework.media.RemoteMediaClient.MediaChannelResult;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * {@link Player} implementation that communicates with a Cast receiver app.
 *
 * <p>The behavior of this class depends on the underlying Cast session, which is obtained from the
 * injected {@link CastContext}. To keep track of the session, {@link #isCastSessionAvailable()} can
 * be queried and {@link SessionAvailabilityListener} can be implemented and attached to the player.
 *
 * <p>If no session is available, the player state will remain unchanged and calls to methods that
 * alter it will be ignored. Querying the player state is possible even when no session is
 * available, in which case, the last observed receiver app state is reported.
 *
 * <p>Methods should be called on the application's main thread.
 */
public final class CastPlayer extends BasePlayer {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.cast");
  }

  @VisibleForTesting
  /* package */ static final Commands PERMANENT_AVAILABLE_COMMANDS =
      new Commands.Builder()
          .addAll(
              COMMAND_PLAY_PAUSE,
              COMMAND_PREPARE_STOP,
              COMMAND_SEEK_TO_DEFAULT_POSITION,
              COMMAND_SEEK_TO_MEDIA_ITEM,
              COMMAND_SET_REPEAT_MODE,
              COMMAND_GET_CURRENT_MEDIA_ITEM,
              COMMAND_GET_MEDIA_ITEMS,
              COMMAND_GET_MEDIA_ITEMS_METADATA,
              COMMAND_CHANGE_MEDIA_ITEMS)
          .build();

  private static final String TAG = "CastPlayer";

  private static final int RENDERER_COUNT = 3;
  private static final int RENDERER_INDEX_VIDEO = 0;
  private static final int RENDERER_INDEX_AUDIO = 1;
  private static final int RENDERER_INDEX_TEXT = 2;
  private static final long PROGRESS_REPORT_PERIOD_MS = 1000;
  private static final TrackSelectionArray EMPTY_TRACK_SELECTION_ARRAY =
      new TrackSelectionArray(null, null, null);
  private static final long[] EMPTY_TRACK_ID_ARRAY = new long[0];

  private final CastContext castContext;
  private final MediaItemConverter mediaItemConverter;
  // TODO: Allow custom implementations of CastTimelineTracker.
  private final CastTimelineTracker timelineTracker;
  private final Timeline.Period period;

  // Result callbacks.
  private final StatusListener statusListener;
  private final SeekResultCallback seekResultCallback;

  // Listeners and notification.
  private final ListenerSet<Player.EventListener> listeners;
  @Nullable private SessionAvailabilityListener sessionAvailabilityListener;

  // Internal state.
  private final StateHolder<Boolean> playWhenReady;
  private final StateHolder<Integer> repeatMode;
  @Nullable private RemoteMediaClient remoteMediaClient;
  private CastTimeline currentTimeline;
  private TrackGroupArray currentTrackGroups;
  private TrackSelectionArray currentTrackSelection;
  private Commands availableCommands;
  @Player.State private int playbackState;
  private int currentWindowIndex;
  private long lastReportedPositionMs;
  private int pendingSeekCount;
  private int pendingSeekWindowIndex;
  private long pendingSeekPositionMs;
  @Nullable private PositionInfo pendingMediaItemRemovalPosition;

  /**
   * Creates a new cast player that uses a {@link DefaultMediaItemConverter}.
   *
   * @param castContext The context from which the cast session is obtained.
   */
  public CastPlayer(CastContext castContext) {
    this(castContext, new DefaultMediaItemConverter());
  }

  /**
   * Creates a new cast player.
   *
   * @param castContext The context from which the cast session is obtained.
   * @param mediaItemConverter The {@link MediaItemConverter} to use.
   */
  public CastPlayer(CastContext castContext, MediaItemConverter mediaItemConverter) {
    this.castContext = castContext;
    this.mediaItemConverter = mediaItemConverter;
    timelineTracker = new CastTimelineTracker();
    period = new Timeline.Period();
    statusListener = new StatusListener();
    seekResultCallback = new SeekResultCallback();
    listeners =
        new ListenerSet<>(
            Looper.getMainLooper(),
            Clock.DEFAULT,
            (listener, flags) -> listener.onEvents(/* player= */ this, new Events(flags)));
    playWhenReady = new StateHolder<>(false);
    repeatMode = new StateHolder<>(REPEAT_MODE_OFF);
    playbackState = STATE_IDLE;
    currentTimeline = CastTimeline.EMPTY_CAST_TIMELINE;
    currentTrackGroups = TrackGroupArray.EMPTY;
    currentTrackSelection = EMPTY_TRACK_SELECTION_ARRAY;
    availableCommands = new Commands.Builder().addAll(PERMANENT_AVAILABLE_COMMANDS).build();
    pendingSeekWindowIndex = C.INDEX_UNSET;
    pendingSeekPositionMs = C.TIME_UNSET;

    SessionManager sessionManager = castContext.getSessionManager();
    sessionManager.addSessionManagerListener(statusListener, CastSession.class);
    CastSession session = sessionManager.getCurrentCastSession();
    setRemoteMediaClient(session != null ? session.getRemoteMediaClient() : null);
    updateInternalStateAndNotifyIfChanged();
  }

  // Media Queue manipulation methods.

  /** @deprecated Use {@link #setMediaItems(List, int, long)} instead. */
  @Deprecated
  @Nullable
  public PendingResult<MediaChannelResult> loadItem(MediaQueueItem item, long positionMs) {
    return setMediaItemsInternal(
        new MediaQueueItem[] {item}, /* startWindowIndex= */ 0, positionMs, repeatMode.value);
  }

  /**
   * @deprecated Use {@link #setMediaItems(List, int, long)} and {@link #setRepeatMode(int)}
   *     instead.
   */
  @Deprecated
  @Nullable
  public PendingResult<MediaChannelResult> loadItems(
      MediaQueueItem[] items, int startIndex, long positionMs, @RepeatMode int repeatMode) {
    return setMediaItemsInternal(items, startIndex, positionMs, repeatMode);
  }

  /** @deprecated Use {@link #addMediaItems(List)} instead. */
  @Deprecated
  @Nullable
  public PendingResult<MediaChannelResult> addItems(MediaQueueItem... items) {
    return addMediaItemsInternal(items, MediaQueueItem.INVALID_ITEM_ID);
  }

  /** @deprecated Use {@link #addMediaItems(int, List)} instead. */
  @Deprecated
  @Nullable
  public PendingResult<MediaChannelResult> addItems(int periodId, MediaQueueItem... items) {
    if (periodId == MediaQueueItem.INVALID_ITEM_ID
        || currentTimeline.getIndexOfPeriod(periodId) != C.INDEX_UNSET) {
      return addMediaItemsInternal(items, periodId);
    }
    return null;
  }

  /** @deprecated Use {@link #removeMediaItem(int)} instead. */
  @Deprecated
  @Nullable
  public PendingResult<MediaChannelResult> removeItem(int periodId) {
    if (currentTimeline.getIndexOfPeriod(periodId) != C.INDEX_UNSET) {
      return removeMediaItemsInternal(new int[] {periodId});
    }
    return null;
  }

  /** @deprecated Use {@link #moveMediaItem(int, int)} instead. */
  @Deprecated
  @Nullable
  public PendingResult<MediaChannelResult> moveItem(int periodId, int newIndex) {
    Assertions.checkArgument(newIndex >= 0 && newIndex < currentTimeline.getWindowCount());
    int fromIndex = currentTimeline.getIndexOfPeriod(periodId);
    if (fromIndex != C.INDEX_UNSET && fromIndex != newIndex) {
      return moveMediaItemsInternal(new int[] {periodId}, fromIndex, newIndex);
    }
    return null;
  }

  /**
   * Returns the item that corresponds to the period with the given id, or null if no media queue or
   * period with id {@code periodId} exist.
   *
   * @param periodId The id of the period ({@link #getCurrentTimeline}) that corresponds to the item
   *     to get.
   * @return The item that corresponds to the period with the given id, or null if no media queue or
   *     period with id {@code periodId} exist.
   */
  @Nullable
  public MediaQueueItem getItem(int periodId) {
    MediaStatus mediaStatus = getMediaStatus();
    return mediaStatus != null && currentTimeline.getIndexOfPeriod(periodId) != C.INDEX_UNSET
        ? mediaStatus.getItemById(periodId)
        : null;
  }

  // CastSession methods.

  /** Returns whether a cast session is available. */
  public boolean isCastSessionAvailable() {
    return remoteMediaClient != null;
  }

  /**
   * Sets a listener for updates on the cast session availability.
   *
   * @param listener The {@link SessionAvailabilityListener}, or null to clear the listener.
   */
  public void setSessionAvailabilityListener(@Nullable SessionAvailabilityListener listener) {
    sessionAvailabilityListener = listener;
  }

  // Player implementation.

  @Override
  public Looper getApplicationLooper() {
    return Looper.getMainLooper();
  }

  @Override
  public void addListener(Listener listener) {
    EventListener eventListener = listener;
    addListener(eventListener);
  }

  @Override
  public void addListener(EventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    EventListener eventListener = listener;
    removeListener(eventListener);
  }

  @Override
  public void removeListener(EventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    int windowIndex = resetPosition ? 0 : getCurrentWindowIndex();
    long startPositionMs = resetPosition ? C.TIME_UNSET : getContentPosition();
    setMediaItems(mediaItems, windowIndex, startPositionMs);
  }

  @Override
  public void setMediaItems(
      List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs) {
    setMediaItemsInternal(
        toMediaQueueItems(mediaItems), startWindowIndex, startPositionMs, repeatMode.value);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    Assertions.checkArgument(index >= 0);
    int uid = MediaQueueItem.INVALID_ITEM_ID;
    if (index < currentTimeline.getWindowCount()) {
      uid = (int) currentTimeline.getWindow(/* windowIndex= */ index, window).uid;
    }
    addMediaItemsInternal(toMediaQueueItems(mediaItems), uid);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    Assertions.checkArgument(
        fromIndex >= 0
            && fromIndex <= toIndex
            && toIndex <= currentTimeline.getWindowCount()
            && newIndex >= 0
            && newIndex < currentTimeline.getWindowCount());
    newIndex = min(newIndex, currentTimeline.getWindowCount() - (toIndex - fromIndex));
    if (fromIndex == toIndex || fromIndex == newIndex) {
      // Do nothing.
      return;
    }
    int[] uids = new int[toIndex - fromIndex];
    for (int i = 0; i < uids.length; i++) {
      uids[i] = (int) currentTimeline.getWindow(/* windowIndex= */ i + fromIndex, window).uid;
    }
    moveMediaItemsInternal(uids, fromIndex, newIndex);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    Assertions.checkArgument(fromIndex >= 0 && toIndex >= fromIndex);
    toIndex = min(toIndex, currentTimeline.getWindowCount());
    if (fromIndex == toIndex) {
      // Do nothing.
      return;
    }
    int[] uids = new int[toIndex - fromIndex];
    for (int i = 0; i < uids.length; i++) {
      uids[i] = (int) currentTimeline.getWindow(/* windowIndex= */ i + fromIndex, window).uid;
    }
    removeMediaItemsInternal(uids);
  }

  @Override
  public Commands getAvailableCommands() {
    return availableCommands;
  }

  @Override
  public void prepare() {
    // Do nothing.
  }

  @Override
  @Player.State
  public int getPlaybackState() {
    return playbackState;
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    return Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlayerError() {
    return null;
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (remoteMediaClient == null) {
      return;
    }
    // We update the local state and send the message to the receiver app, which will cause the
    // operation to be perceived as synchronous by the user. When the operation reports a result,
    // the local state will be updated to reflect the state reported by the Cast SDK.
    setPlayerStateAndNotifyIfChanged(
        playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST, playbackState);
    listeners.flushEvents();
    PendingResult<MediaChannelResult> pendingResult =
        playWhenReady ? remoteMediaClient.play() : remoteMediaClient.pause();
    this.playWhenReady.pendingResultCallback =
        new ResultCallback<MediaChannelResult>() {
          @Override
          public void onResult(MediaChannelResult mediaChannelResult) {
            if (remoteMediaClient != null) {
              updatePlayerStateAndNotifyIfChanged(this);
              listeners.flushEvents();
            }
          }
        };
    pendingResult.setResultCallback(this.playWhenReady.pendingResultCallback);
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady.value;
  }

  // We still call Listener#onSeekProcessed() for backwards compatibility with listeners that
  // don't implement onPositionDiscontinuity().
  @SuppressWarnings("deprecation")
  @Override
  public void seekTo(int windowIndex, long positionMs) {
    MediaStatus mediaStatus = getMediaStatus();
    // We assume the default position is 0. There is no support for seeking to the default position
    // in RemoteMediaClient.
    positionMs = positionMs != C.TIME_UNSET ? positionMs : 0;
    if (mediaStatus != null) {
      if (getCurrentWindowIndex() != windowIndex) {
        remoteMediaClient
            .queueJumpToItem(
                (int) currentTimeline.getPeriod(windowIndex, period).uid, positionMs, null)
            .setResultCallback(seekResultCallback);
      } else {
        remoteMediaClient.seek(positionMs).setResultCallback(seekResultCallback);
      }
      PositionInfo oldPosition = getCurrentPositionInfo();
      pendingSeekCount++;
      pendingSeekWindowIndex = windowIndex;
      pendingSeekPositionMs = positionMs;
      PositionInfo newPosition = getCurrentPositionInfo();
      listeners.queueEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          listener -> {
            listener.onPositionDiscontinuity(DISCONTINUITY_REASON_SEEK);
            listener.onPositionDiscontinuity(oldPosition, newPosition, DISCONTINUITY_REASON_SEEK);
          });
      if (oldPosition.windowIndex != newPosition.windowIndex) {
        // TODO(internal b/182261884): queue `onMediaItemTransition` event when the media item is
        // repeated.
        MediaItem mediaItem = getCurrentTimeline().getWindow(windowIndex, window).mediaItem;
        listeners.queueEvent(
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            listener ->
                listener.onMediaItemTransition(mediaItem, MEDIA_ITEM_TRANSITION_REASON_SEEK));
      }
      updateAvailableCommandsAndNotifyIfChanged();
    } else if (pendingSeekCount == 0) {
      listeners.queueEvent(/* eventFlag= */ C.INDEX_UNSET, EventListener::onSeekProcessed);
    }
    listeners.flushEvents();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    // Unsupported by the RemoteMediaClient API. Do nothing.
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return PlaybackParameters.DEFAULT;
  }

  @Override
  public void stop(boolean reset) {
    playbackState = STATE_IDLE;
    if (remoteMediaClient != null) {
      // TODO(b/69792021): Support or emulate stop without position reset.
      remoteMediaClient.stop();
    }
  }

  @Override
  public void release() {
    SessionManager sessionManager = castContext.getSessionManager();
    sessionManager.removeSessionManagerListener(statusListener, CastSession.class);
    sessionManager.endCurrentSession(false);
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    if (remoteMediaClient == null) {
      return;
    }
    // We update the local state and send the message to the receiver app, which will cause the
    // operation to be perceived as synchronous by the user. When the operation reports a result,
    // the local state will be updated to reflect the state reported by the Cast SDK.
    setRepeatModeAndNotifyIfChanged(repeatMode);
    listeners.flushEvents();
    PendingResult<MediaChannelResult> pendingResult =
        remoteMediaClient.queueSetRepeatMode(getCastRepeatMode(repeatMode), /* jsonObject= */ null);
    this.repeatMode.pendingResultCallback =
        new ResultCallback<MediaChannelResult>() {
          @Override
          public void onResult(MediaChannelResult mediaChannelResult) {
            if (remoteMediaClient != null) {
              updateRepeatModeAndNotifyIfChanged(this);
              listeners.flushEvents();
            }
          }
        };
    pendingResult.setResultCallback(this.repeatMode.pendingResultCallback);
  }

  @Override
  @RepeatMode
  public int getRepeatMode() {
    return repeatMode.value;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    // TODO: Support shuffle mode.
  }

  @Override
  public boolean getShuffleModeEnabled() {
    // TODO: Support shuffle mode.
    return false;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return currentTrackSelection;
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return currentTrackGroups;
  }

  @Override
  public ImmutableList<Metadata> getCurrentStaticMetadata() {
    // CastPlayer does not currently support metadata.
    return ImmutableList.of();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    // CastPlayer does not currently support metadata.
    return MediaMetadata.EMPTY;
  }

  @Override
  public Timeline getCurrentTimeline() {
    return currentTimeline;
  }

  @Override
  public int getCurrentPeriodIndex() {
    return getCurrentWindowIndex();
  }

  @Override
  public int getCurrentWindowIndex() {
    return pendingSeekWindowIndex != C.INDEX_UNSET ? pendingSeekWindowIndex : currentWindowIndex;
  }

  // TODO: Fill the cast timeline information with ProgressListener's duration updates.
  // See [Internal: b/65152553].
  @Override
  public long getDuration() {
    return getContentDuration();
  }

  @Override
  public long getCurrentPosition() {
    return pendingSeekPositionMs != C.TIME_UNSET
        ? pendingSeekPositionMs
        : remoteMediaClient != null
            ? remoteMediaClient.getApproximateStreamPosition()
            : lastReportedPositionMs;
  }

  @Override
  public long getBufferedPosition() {
    return getCurrentPosition();
  }

  @Override
  public long getTotalBufferedDuration() {
    long bufferedPosition = getBufferedPosition();
    long currentPosition = getCurrentPosition();
    return bufferedPosition == C.TIME_UNSET || currentPosition == C.TIME_UNSET
        ? 0
        : bufferedPosition - currentPosition;
  }

  @Override
  public boolean isPlayingAd() {
    return false;
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return C.INDEX_UNSET;
  }

  @Override
  public boolean isLoading() {
    return false;
  }

  @Override
  public long getContentPosition() {
    return getCurrentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    return getBufferedPosition();
  }

  /** This method is not supported and returns {@link AudioAttributes#DEFAULT}. */
  @Override
  public AudioAttributes getAudioAttributes() {
    return AudioAttributes.DEFAULT;
  }

  /** This method is not supported and does nothing. */
  @Override
  public void setVolume(float audioVolume) {}

  /** This method is not supported and returns 1. */
  @Override
  public float getVolume() {
    return 1;
  }

  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoSurface() {}

  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoSurface(@Nullable Surface surface) {}

  /** This method is not supported and does nothing. */
  @Override
  public void setVideoSurface(@Nullable Surface surface) {}

  /** This method is not supported and does nothing. */
  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {}

  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {}

  /** This method is not supported and does nothing. */
  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {}

  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {}

  /** This method is not supported and does nothing. */
  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {}
  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {}

  /** This method is not supported and returns {@link VideoSize#UNKNOWN}. */
  @Override
  public VideoSize getVideoSize() {
    return VideoSize.UNKNOWN;
  }

  /** This method is not supported and returns an empty list. */
  @Override
  public ImmutableList<Cue> getCurrentCues() {
    return ImmutableList.of();
  }

  /** This method is not supported and always returns {@link DeviceInfo#UNKNOWN}. */
  @Override
  public DeviceInfo getDeviceInfo() {
    return DeviceInfo.UNKNOWN;
  }

  /** This method is not supported and always returns {@code 0}. */
  @Override
  public int getDeviceVolume() {
    return 0;
  }

  /** This method is not supported and always returns {@code false}. */
  @Override
  public boolean isDeviceMuted() {
    return false;
  }

  /** This method is not supported and does nothing. */
  @Override
  public void setDeviceVolume(int volume) {}

  /** This method is not supported and does nothing. */
  @Override
  public void increaseDeviceVolume() {}

  /** This method is not supported and does nothing. */
  @Override
  public void decreaseDeviceVolume() {}

  /** This method is not supported and does nothing. */
  @Override
  public void setDeviceMuted(boolean muted) {}

  // Internal methods.

  // Call deprecated callbacks.
  @SuppressWarnings("deprecation")
  private void updateInternalStateAndNotifyIfChanged() {
    if (remoteMediaClient == null) {
      // There is no session. We leave the state of the player as it is now.
      return;
    }
    int oldWindowIndex = this.currentWindowIndex;
    @Nullable
    Object oldPeriodUid =
        !getCurrentTimeline().isEmpty()
            ? getCurrentTimeline().getPeriod(oldWindowIndex, period, /* setIds= */ true).uid
            : null;
    boolean wasPlaying = playbackState == Player.STATE_READY && playWhenReady.value;
    updatePlayerStateAndNotifyIfChanged(/* resultCallback= */ null);
    boolean isPlaying = playbackState == Player.STATE_READY && playWhenReady.value;
    if (wasPlaying != isPlaying) {
      listeners.queueEvent(
          Player.EVENT_IS_PLAYING_CHANGED, listener -> listener.onIsPlayingChanged(isPlaying));
    }
    updateRepeatModeAndNotifyIfChanged(/* resultCallback= */ null);
    boolean playingPeriodChangedByTimelineChange = updateTimelineAndNotifyIfChanged();
    Timeline currentTimeline = getCurrentTimeline();
    currentWindowIndex = fetchCurrentWindowIndex(remoteMediaClient, currentTimeline);
    @Nullable
    Object currentPeriodUid =
        !currentTimeline.isEmpty()
            ? currentTimeline.getPeriod(currentWindowIndex, period, /* setIds= */ true).uid
            : null;
    if (!playingPeriodChangedByTimelineChange
        && !Util.areEqual(oldPeriodUid, currentPeriodUid)
        && pendingSeekCount == 0) {
      // Report discontinuity and media item auto transition.
      currentTimeline.getPeriod(oldWindowIndex, period, /* setIds= */ true);
      currentTimeline.getWindow(oldWindowIndex, window);
      long windowDurationMs = window.getDurationMs();
      PositionInfo oldPosition =
          new PositionInfo(
              window.uid,
              period.windowIndex,
              period.uid,
              period.windowIndex,
              /* positionMs= */ windowDurationMs,
              /* contentPositionMs= */ windowDurationMs,
              /* adGroupIndex= */ C.INDEX_UNSET,
              /* adIndexInAdGroup= */ C.INDEX_UNSET);
      currentTimeline.getPeriod(currentWindowIndex, period, /* setIds= */ true);
      currentTimeline.getWindow(currentWindowIndex, window);
      PositionInfo newPosition =
          new PositionInfo(
              window.uid,
              period.windowIndex,
              period.uid,
              period.windowIndex,
              /* positionMs= */ window.getDefaultPositionMs(),
              /* contentPositionMs= */ window.getDefaultPositionMs(),
              /* adGroupIndex= */ C.INDEX_UNSET,
              /* adIndexInAdGroup= */ C.INDEX_UNSET);
      listeners.queueEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          listener -> {
            listener.onPositionDiscontinuity(DISCONTINUITY_REASON_AUTO_TRANSITION);
            listener.onPositionDiscontinuity(
                oldPosition, newPosition, DISCONTINUITY_REASON_AUTO_TRANSITION);
          });
      listeners.queueEvent(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          listener ->
              listener.onMediaItemTransition(
                  getCurrentMediaItem(), MEDIA_ITEM_TRANSITION_REASON_AUTO));
    }
    if (updateTracksAndSelectionsAndNotifyIfChanged()) {
      listeners.queueEvent(
          Player.EVENT_TRACKS_CHANGED,
          listener -> listener.onTracksChanged(currentTrackGroups, currentTrackSelection));
    }
    updateAvailableCommandsAndNotifyIfChanged();
    listeners.flushEvents();
  }

  /**
   * Updates {@link #playWhenReady} and {@link #playbackState} to match the Cast {@code
   * remoteMediaClient} state, and notifies listeners of any state changes.
   *
   * <p>This method will only update values whose {@link StateHolder#pendingResultCallback} matches
   * the given {@code resultCallback}.
   */
  @RequiresNonNull("remoteMediaClient")
  private void updatePlayerStateAndNotifyIfChanged(@Nullable ResultCallback<?> resultCallback) {
    boolean newPlayWhenReadyValue = playWhenReady.value;
    if (playWhenReady.acceptsUpdate(resultCallback)) {
      newPlayWhenReadyValue = !remoteMediaClient.isPaused();
      playWhenReady.clearPendingResultCallback();
    }
    @PlayWhenReadyChangeReason
    int playWhenReadyChangeReason =
        newPlayWhenReadyValue != playWhenReady.value
            ? PLAY_WHEN_READY_CHANGE_REASON_REMOTE
            : PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
    // We do not mask the playback state, so try setting it regardless of the playWhenReady masking.
    setPlayerStateAndNotifyIfChanged(
        newPlayWhenReadyValue, playWhenReadyChangeReason, fetchPlaybackState(remoteMediaClient));
  }

  @RequiresNonNull("remoteMediaClient")
  private void updateRepeatModeAndNotifyIfChanged(@Nullable ResultCallback<?> resultCallback) {
    if (repeatMode.acceptsUpdate(resultCallback)) {
      setRepeatModeAndNotifyIfChanged(fetchRepeatMode(remoteMediaClient));
      repeatMode.clearPendingResultCallback();
    }
  }

  /**
   * Updates the timeline and notifies {@link Player.Listener event listeners} if required.
   *
   * @return Whether the timeline change has caused a change of the period currently being played.
   */
  @SuppressWarnings("deprecation") // Calling deprecated listener method.
  private boolean updateTimelineAndNotifyIfChanged() {
    Timeline oldTimeline = currentTimeline;
    int oldWindowIndex = currentWindowIndex;
    boolean playingPeriodChanged = false;
    if (updateTimeline()) {
      // TODO: Differentiate TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED and
      //     TIMELINE_CHANGE_REASON_SOURCE_UPDATE [see internal: b/65152553].
      Timeline timeline = currentTimeline;
      // Call onTimelineChanged.
      listeners.queueEvent(
          Player.EVENT_TIMELINE_CHANGED,
          listener -> {
            listener.onTimelineChanged(
                timeline, /* manifest= */ null, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
            listener.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
          });

      // Call onPositionDiscontinuity if required.
      Timeline currentTimeline = getCurrentTimeline();
      boolean playingPeriodRemoved = false;
      if (!oldTimeline.isEmpty()) {
        Object oldPeriodUid =
            castNonNull(oldTimeline.getPeriod(oldWindowIndex, period, /* setIds= */ true).uid);
        playingPeriodRemoved = currentTimeline.getIndexOfPeriod(oldPeriodUid) == C.INDEX_UNSET;
      }
      if (playingPeriodRemoved) {
        PositionInfo oldPosition;
        if (pendingMediaItemRemovalPosition != null) {
          oldPosition = pendingMediaItemRemovalPosition;
          pendingMediaItemRemovalPosition = null;
        } else {
          // If the media item has been removed by another client, we don't know the removal
          // position. We use the current position as a fallback.
          oldTimeline.getPeriod(oldWindowIndex, period, /* setIds= */ true);
          oldTimeline.getWindow(period.windowIndex, window);
          oldPosition =
              new PositionInfo(
                  window.uid,
                  period.windowIndex,
                  period.uid,
                  period.windowIndex,
                  getCurrentPosition(),
                  getContentPosition(),
                  /* adGroupIndex= */ C.INDEX_UNSET,
                  /* adIndexInAdGroup= */ C.INDEX_UNSET);
        }
        PositionInfo newPosition = getCurrentPositionInfo();
        listeners.queueEvent(
            Player.EVENT_POSITION_DISCONTINUITY,
            listener -> {
              listener.onPositionDiscontinuity(DISCONTINUITY_REASON_REMOVE);
              listener.onPositionDiscontinuity(
                  oldPosition, newPosition, DISCONTINUITY_REASON_REMOVE);
            });
      }

      // Call onMediaItemTransition if required.
      playingPeriodChanged =
          currentTimeline.isEmpty() != oldTimeline.isEmpty() || playingPeriodRemoved;
      if (playingPeriodChanged) {
        listeners.queueEvent(
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            listener ->
                listener.onMediaItemTransition(
                    getCurrentMediaItem(), MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
      }
      updateAvailableCommandsAndNotifyIfChanged();
    }
    return playingPeriodChanged;
  }

  /**
   * Updates the current timeline. The current window index may change as a result.
   *
   * @return Whether the current timeline has changed.
   */
  private boolean updateTimeline() {
    CastTimeline oldTimeline = currentTimeline;
    MediaStatus status = getMediaStatus();
    currentTimeline =
        status != null
            ? timelineTracker.getCastTimeline(remoteMediaClient)
            : CastTimeline.EMPTY_CAST_TIMELINE;
    boolean timelineChanged = !oldTimeline.equals(currentTimeline);
    if (timelineChanged) {
      currentWindowIndex = fetchCurrentWindowIndex(remoteMediaClient, currentTimeline);
    }
    return timelineChanged;
  }

  /** Updates the internal tracks and selection and returns whether they have changed. */
  private boolean updateTracksAndSelectionsAndNotifyIfChanged() {
    if (remoteMediaClient == null) {
      // There is no session. We leave the state of the player as it is now.
      return false;
    }

    MediaStatus mediaStatus = getMediaStatus();
    MediaInfo mediaInfo = mediaStatus != null ? mediaStatus.getMediaInfo() : null;
    List<MediaTrack> castMediaTracks = mediaInfo != null ? mediaInfo.getMediaTracks() : null;
    if (castMediaTracks == null || castMediaTracks.isEmpty()) {
      boolean hasChanged = !currentTrackGroups.isEmpty();
      currentTrackGroups = TrackGroupArray.EMPTY;
      currentTrackSelection = EMPTY_TRACK_SELECTION_ARRAY;
      return hasChanged;
    }
    long[] activeTrackIds = mediaStatus.getActiveTrackIds();
    if (activeTrackIds == null) {
      activeTrackIds = EMPTY_TRACK_ID_ARRAY;
    }

    TrackGroup[] trackGroups = new TrackGroup[castMediaTracks.size()];
    TrackSelection[] trackSelections = new TrackSelection[RENDERER_COUNT];
    for (int i = 0; i < castMediaTracks.size(); i++) {
      MediaTrack mediaTrack = castMediaTracks.get(i);
      trackGroups[i] = new TrackGroup(CastUtils.mediaTrackToFormat(mediaTrack));

      long id = mediaTrack.getId();
      int trackType = MimeTypes.getTrackType(mediaTrack.getContentType());
      int rendererIndex = getRendererIndexForTrackType(trackType);
      if (isTrackActive(id, activeTrackIds)
          && rendererIndex != C.INDEX_UNSET
          && trackSelections[rendererIndex] == null) {
        trackSelections[rendererIndex] = new CastTrackSelection(trackGroups[i]);
      }
    }
    TrackGroupArray newTrackGroups = new TrackGroupArray(trackGroups);
    TrackSelectionArray newTrackSelections = new TrackSelectionArray(trackSelections);

    if (!newTrackGroups.equals(currentTrackGroups)
        || !newTrackSelections.equals(currentTrackSelection)) {
      currentTrackSelection = new TrackSelectionArray(trackSelections);
      currentTrackGroups = new TrackGroupArray(trackGroups);
      return true;
    }
    return false;
  }

  private void updateAvailableCommandsAndNotifyIfChanged() {
    Commands previousAvailableCommands = availableCommands;
    availableCommands = getAvailableCommands(PERMANENT_AVAILABLE_COMMANDS);
    if (!availableCommands.equals(previousAvailableCommands)) {
      listeners.queueEvent(
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
          listener -> listener.onAvailableCommandsChanged(availableCommands));
    }
  }

  @Nullable
  private PendingResult<MediaChannelResult> setMediaItemsInternal(
      MediaQueueItem[] mediaQueueItems,
      int startWindowIndex,
      long startPositionMs,
      @RepeatMode int repeatMode) {
    if (remoteMediaClient == null || mediaQueueItems.length == 0) {
      return null;
    }
    startPositionMs = startPositionMs == C.TIME_UNSET ? 0 : startPositionMs;
    if (startWindowIndex == C.INDEX_UNSET) {
      startWindowIndex = getCurrentWindowIndex();
      startPositionMs = getCurrentPosition();
    }
    Timeline currentTimeline = getCurrentTimeline();
    if (!currentTimeline.isEmpty()) {
      pendingMediaItemRemovalPosition = getCurrentPositionInfo();
    }
    return remoteMediaClient.queueLoad(
        mediaQueueItems,
        min(startWindowIndex, mediaQueueItems.length - 1),
        getCastRepeatMode(repeatMode),
        startPositionMs,
        /* customData= */ null);
  }

  @Nullable
  private PendingResult<MediaChannelResult> addMediaItemsInternal(MediaQueueItem[] items, int uid) {
    if (remoteMediaClient == null || getMediaStatus() == null) {
      return null;
    }
    return remoteMediaClient.queueInsertItems(items, uid, /* customData= */ null);
  }

  @Nullable
  private PendingResult<MediaChannelResult> moveMediaItemsInternal(
      int[] uids, int fromIndex, int newIndex) {
    if (remoteMediaClient == null || getMediaStatus() == null) {
      return null;
    }
    int insertBeforeIndex = fromIndex < newIndex ? newIndex + uids.length : newIndex;
    int insertBeforeItemId = MediaQueueItem.INVALID_ITEM_ID;
    if (insertBeforeIndex < currentTimeline.getWindowCount()) {
      insertBeforeItemId = (int) currentTimeline.getWindow(insertBeforeIndex, window).uid;
    }
    return remoteMediaClient.queueReorderItems(uids, insertBeforeItemId, /* customData= */ null);
  }

  @Nullable
  private PendingResult<MediaChannelResult> removeMediaItemsInternal(int[] uids) {
    if (remoteMediaClient == null || getMediaStatus() == null) {
      return null;
    }
    Timeline timeline = getCurrentTimeline();
    if (!timeline.isEmpty()) {
      Object periodUid =
          castNonNull(timeline.getPeriod(getCurrentPeriodIndex(), period, /* setIds= */ true).uid);
      for (int uid : uids) {
        if (periodUid.equals(uid)) {
          pendingMediaItemRemovalPosition = getCurrentPositionInfo();
          break;
        }
      }
    }
    return remoteMediaClient.queueRemoveItems(uids, /* customData= */ null);
  }

  private PositionInfo getCurrentPositionInfo() {
    Timeline currentTimeline = getCurrentTimeline();
    @Nullable
    Object newPeriodUid =
        !currentTimeline.isEmpty()
            ? currentTimeline.getPeriod(getCurrentPeriodIndex(), period, /* setIds= */ true).uid
            : null;
    @Nullable
    Object newWindowUid =
        newPeriodUid != null ? currentTimeline.getWindow(period.windowIndex, window).uid : null;
    return new PositionInfo(
        newWindowUid,
        getCurrentWindowIndex(),
        newPeriodUid,
        getCurrentPeriodIndex(),
        getCurrentPosition(),
        getContentPosition(),
        /* adGroupIndex= */ C.INDEX_UNSET,
        /* adIndexInAdGroup= */ C.INDEX_UNSET);
  }

  private void setRepeatModeAndNotifyIfChanged(@Player.RepeatMode int repeatMode) {
    if (this.repeatMode.value != repeatMode) {
      this.repeatMode.value = repeatMode;
      listeners.queueEvent(
          Player.EVENT_REPEAT_MODE_CHANGED, listener -> listener.onRepeatModeChanged(repeatMode));
      updateAvailableCommandsAndNotifyIfChanged();
    }
  }

  @SuppressWarnings("deprecation")
  private void setPlayerStateAndNotifyIfChanged(
      boolean playWhenReady,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason,
      @Player.State int playbackState) {
    boolean playWhenReadyChanged = this.playWhenReady.value != playWhenReady;
    boolean playbackStateChanged = this.playbackState != playbackState;
    if (playWhenReadyChanged || playbackStateChanged) {
      this.playbackState = playbackState;
      this.playWhenReady.value = playWhenReady;
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onPlayerStateChanged(playWhenReady, playbackState));
      if (playbackStateChanged) {
        listeners.queueEvent(
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            listener -> listener.onPlaybackStateChanged(playbackState));
      }
      if (playWhenReadyChanged) {
        listeners.queueEvent(
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            listener -> listener.onPlayWhenReadyChanged(playWhenReady, playWhenReadyChangeReason));
      }
    }
  }

  private void setRemoteMediaClient(@Nullable RemoteMediaClient remoteMediaClient) {
    if (this.remoteMediaClient == remoteMediaClient) {
      // Do nothing.
      return;
    }
    if (this.remoteMediaClient != null) {
      this.remoteMediaClient.unregisterCallback(statusListener);
      this.remoteMediaClient.removeProgressListener(statusListener);
    }
    this.remoteMediaClient = remoteMediaClient;
    if (remoteMediaClient != null) {
      if (sessionAvailabilityListener != null) {
        sessionAvailabilityListener.onCastSessionAvailable();
      }
      remoteMediaClient.registerCallback(statusListener);
      remoteMediaClient.addProgressListener(statusListener, PROGRESS_REPORT_PERIOD_MS);
      updateInternalStateAndNotifyIfChanged();
    } else {
      updateTimelineAndNotifyIfChanged();
      if (sessionAvailabilityListener != null) {
        sessionAvailabilityListener.onCastSessionUnavailable();
      }
    }
  }

  @Nullable
  private MediaStatus getMediaStatus() {
    return remoteMediaClient != null ? remoteMediaClient.getMediaStatus() : null;
  }

  /**
   * Retrieves the playback state from {@code remoteMediaClient} and maps it into a {@link Player}
   * state
   */
  private static int fetchPlaybackState(RemoteMediaClient remoteMediaClient) {
    int receiverAppStatus = remoteMediaClient.getPlayerState();
    switch (receiverAppStatus) {
      case MediaStatus.PLAYER_STATE_BUFFERING:
        return STATE_BUFFERING;
      case MediaStatus.PLAYER_STATE_PLAYING:
      case MediaStatus.PLAYER_STATE_PAUSED:
        return STATE_READY;
      case MediaStatus.PLAYER_STATE_IDLE:
      case MediaStatus.PLAYER_STATE_UNKNOWN:
      default:
        return STATE_IDLE;
    }
  }

  /**
   * Retrieves the repeat mode from {@code remoteMediaClient} and maps it into a {@link
   * Player.RepeatMode}.
   */
  @RepeatMode
  private static int fetchRepeatMode(RemoteMediaClient remoteMediaClient) {
    MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
    if (mediaStatus == null) {
      // No media session active, yet.
      return REPEAT_MODE_OFF;
    }
    int castRepeatMode = mediaStatus.getQueueRepeatMode();
    switch (castRepeatMode) {
      case MediaStatus.REPEAT_MODE_REPEAT_SINGLE:
        return REPEAT_MODE_ONE;
      case MediaStatus.REPEAT_MODE_REPEAT_ALL:
      case MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE:
        return REPEAT_MODE_ALL;
      case MediaStatus.REPEAT_MODE_REPEAT_OFF:
        return REPEAT_MODE_OFF;
      default:
        throw new IllegalStateException();
    }
  }

  private static int fetchCurrentWindowIndex(
      @Nullable RemoteMediaClient remoteMediaClient, Timeline timeline) {
    if (remoteMediaClient == null) {
      return 0;
    }

    int currentWindowIndex = C.INDEX_UNSET;
    @Nullable MediaQueueItem currentItem = remoteMediaClient.getCurrentItem();
    if (currentItem != null) {
      currentWindowIndex = timeline.getIndexOfPeriod(currentItem.getItemId());
    }
    if (currentWindowIndex == C.INDEX_UNSET) {
      // The timeline is empty. Fall back to index 0, which is what ExoPlayer would do.
      currentWindowIndex = 0;
    }
    return currentWindowIndex;
  }

  private static boolean isTrackActive(long id, long[] activeTrackIds) {
    for (long activeTrackId : activeTrackIds) {
      if (activeTrackId == id) {
        return true;
      }
    }
    return false;
  }

  private static int getRendererIndexForTrackType(int trackType) {
    return trackType == C.TRACK_TYPE_VIDEO
        ? RENDERER_INDEX_VIDEO
        : trackType == C.TRACK_TYPE_AUDIO
            ? RENDERER_INDEX_AUDIO
            : trackType == C.TRACK_TYPE_TEXT ? RENDERER_INDEX_TEXT : C.INDEX_UNSET;
  }

  private static int getCastRepeatMode(@RepeatMode int repeatMode) {
    switch (repeatMode) {
      case REPEAT_MODE_ONE:
        return MediaStatus.REPEAT_MODE_REPEAT_SINGLE;
      case REPEAT_MODE_ALL:
        return MediaStatus.REPEAT_MODE_REPEAT_ALL;
      case REPEAT_MODE_OFF:
        return MediaStatus.REPEAT_MODE_REPEAT_OFF;
      default:
        throw new IllegalArgumentException();
    }
  }

  private MediaQueueItem[] toMediaQueueItems(List<MediaItem> mediaItems) {
    MediaQueueItem[] mediaQueueItems = new MediaQueueItem[mediaItems.size()];
    for (int i = 0; i < mediaItems.size(); i++) {
      mediaQueueItems[i] = mediaItemConverter.toMediaQueueItem(mediaItems.get(i));
    }
    return mediaQueueItems;
  }

  // Internal classes.

  private final class StatusListener extends RemoteMediaClient.Callback
      implements SessionManagerListener<CastSession>, RemoteMediaClient.ProgressListener {

    // RemoteMediaClient.ProgressListener implementation.

    @Override
    public void onProgressUpdated(long progressMs, long unusedDurationMs) {
      lastReportedPositionMs = progressMs;
    }

    // RemoteMediaClient.Callback implementation.

    @Override
    public void onStatusUpdated() {
      updateInternalStateAndNotifyIfChanged();
    }

    @Override
    public void onMetadataUpdated() {}

    @Override
    public void onQueueStatusUpdated() {
      updateTimelineAndNotifyIfChanged();
      listeners.flushEvents();
    }

    @Override
    public void onPreloadStatusUpdated() {}

    @Override
    public void onSendingRemoteMediaRequest() {}

    @Override
    public void onAdBreakStatusUpdated() {}

    // SessionManagerListener implementation.

    @Override
    public void onSessionStarted(CastSession castSession, String s) {
      setRemoteMediaClient(castSession.getRemoteMediaClient());
    }

    @Override
    public void onSessionResumed(CastSession castSession, boolean b) {
      setRemoteMediaClient(castSession.getRemoteMediaClient());
    }

    @Override
    public void onSessionEnded(CastSession castSession, int i) {
      setRemoteMediaClient(null);
    }

    @Override
    public void onSessionSuspended(CastSession castSession, int i) {
      setRemoteMediaClient(null);
    }

    @Override
    public void onSessionResumeFailed(CastSession castSession, int statusCode) {
      Log.e(
          TAG,
          "Session resume failed. Error code "
              + statusCode
              + ": "
              + CastUtils.getLogString(statusCode));
    }

    @Override
    public void onSessionStarting(CastSession castSession) {
      // Do nothing.
    }

    @Override
    public void onSessionStartFailed(CastSession castSession, int statusCode) {
      Log.e(
          TAG,
          "Session start failed. Error code "
              + statusCode
              + ": "
              + CastUtils.getLogString(statusCode));
    }

    @Override
    public void onSessionEnding(CastSession castSession) {
      // Do nothing.
    }

    @Override
    public void onSessionResuming(CastSession castSession, String s) {
      // Do nothing.
    }
  }

  private final class SeekResultCallback implements ResultCallback<MediaChannelResult> {

    // We still call Listener#onSeekProcessed() for backwards compatibility with listeners that
    // don't implement onPositionDiscontinuity().
    @SuppressWarnings("deprecation")
    @Override
    public void onResult(MediaChannelResult result) {
      int statusCode = result.getStatus().getStatusCode();
      if (statusCode != CastStatusCodes.SUCCESS && statusCode != CastStatusCodes.REPLACED) {
        Log.e(
            TAG,
            "Seek failed. Error code " + statusCode + ": " + CastUtils.getLogString(statusCode));
      }
      if (--pendingSeekCount == 0) {
        currentWindowIndex = pendingSeekWindowIndex;
        pendingSeekWindowIndex = C.INDEX_UNSET;
        pendingSeekPositionMs = C.TIME_UNSET;
        listeners.sendEvent(/* eventFlag= */ C.INDEX_UNSET, EventListener::onSeekProcessed);
      }
    }
  }

  /** Holds the value and the masking status of a specific part of the {@link CastPlayer} state. */
  private static final class StateHolder<T> {

    /** The user-facing value of a specific part of the {@link CastPlayer} state. */
    public T value;

    /**
     * If {@link #value} is being masked, holds the result callback for the operation that triggered
     * the masking. Or null if {@link #value} is not being masked.
     */
    @Nullable public ResultCallback<MediaChannelResult> pendingResultCallback;

    public StateHolder(T initialValue) {
      value = initialValue;
    }

    public void clearPendingResultCallback() {
      pendingResultCallback = null;
    }

    /**
     * Returns whether this state holder accepts updates coming from the given result callback.
     *
     * <p>A null {@code resultCallback} means that the update is a regular receiver state update, in
     * which case the update will only be accepted if {@link #value} is not being masked. If {@link
     * #value} is being masked, the update will only be accepted if {@code resultCallback} is the
     * same as the {@link #pendingResultCallback}.
     *
     * @param resultCallback A result callback. May be null if the update comes from a regular
     *     receiver status update.
     */
    public boolean acceptsUpdate(@Nullable ResultCallback<?> resultCallback) {
      return pendingResultCallback == resultCallback;
    }
  }
}
