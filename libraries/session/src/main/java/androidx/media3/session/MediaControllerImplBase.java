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

import static androidx.media3.common.Player.COMMAND_ADJUST_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_PREPARE;
import static androidx.media3.common.Player.COMMAND_SEEK_BACK;
import static androidx.media3.common.Player.COMMAND_SEEK_FORWARD;
import static androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_DEFAULT_POSITION;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEMS_METADATA;
import static androidx.media3.common.Player.COMMAND_SET_REPEAT_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SHUFFLE_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SPEED_AND_PITCH;
import static androidx.media3.common.Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS;
import static androidx.media3.common.Player.COMMAND_SET_VIDEO_SURFACE;
import static androidx.media3.common.Player.COMMAND_SET_VOLUME;
import static androidx.media3.common.Player.COMMAND_STOP;
import static androidx.media3.common.Player.DISCONTINUITY_REASON_INTERNAL;
import static androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE;
import static androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK;
import static androidx.media3.common.Player.EVENT_AVAILABLE_COMMANDS_CHANGED;
import static androidx.media3.common.Player.EVENT_IS_LOADING_CHANGED;
import static androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED;
import static androidx.media3.common.Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED;
import static androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION;
import static androidx.media3.common.Player.EVENT_PLAYBACK_PARAMETERS_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYER_ERROR;
import static androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY;
import static androidx.media3.common.Player.EVENT_REPEAT_MODE_CHANGED;
import static androidx.media3.common.Player.EVENT_SEEK_BACK_INCREMENT_CHANGED;
import static androidx.media3.common.Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED;
import static androidx.media3.common.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED;
import static androidx.media3.common.Player.EVENT_TIMELINE_CHANGED;
import static androidx.media3.common.Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED;
import static androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
import static androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT;
import static androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK;
import static androidx.media3.common.Player.PLAYBACK_SUPPRESSION_REASON_NONE;
import static androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
import static androidx.media3.common.Player.STATE_BUFFERING;
import static androidx.media3.common.Player.STATE_ENDED;
import static androidx.media3.common.Player.STATE_IDLE;
import static androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkIndex;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.usToMs;
import static androidx.media3.session.MediaUtils.calculateBufferedPercentage;
import static androidx.media3.session.MediaUtils.intersect;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_CUSTOM;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_SESSION_SET_MEDIA_URI;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_SESSION_SET_RATING;
import static androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED;
import static androidx.media3.session.SessionResult.RESULT_ERROR_SESSION_DISCONNECTED;
import static androidx.media3.session.SessionResult.RESULT_ERROR_UNKNOWN;
import static androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED;
import static androidx.media3.session.SessionToken.TYPE_SESSION;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.BundleListRetriever;
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
import androidx.media3.common.Player.PlayWhenReadyChangeReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Player.TimelineChangeReason;
import androidx.media3.common.Rating;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Period;
import androidx.media3.common.Timeline.RemotableTimeline;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.BundleableUtil;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ListenerSet;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaController.MediaControllerImpl;
import androidx.media3.session.SessionCommand.CommandCode;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.qual.NonNull;

@SuppressWarnings("FutureReturnValueIgnored") // TODO(b/138091975): Not to ignore if feasible
/* package */ class MediaControllerImplBase implements MediaControllerImpl {

  public static final String TAG = "MCImplBase";

  protected final MediaController instance;
  protected final SequencedFutureManager sequencedFutureManager;
  protected final MediaControllerStub controllerStub;

  private final Context context;
  private final SessionToken token;
  private final Bundle connectionHints;
  private final IBinder.DeathRecipient deathRecipient;
  private final SurfaceCallback surfaceCallback;
  private final ListenerSet<Listener> listeners;
  private final FlushCommandQueueHandler flushCommandQueueHandler;

  @Nullable private SessionToken connectedToken;
  @Nullable private SessionServiceConnection serviceConnection;
  private boolean released;
  private PlayerInfo playerInfo;
  @Nullable private PendingIntent sessionActivity;
  private SessionCommands sessionCommands;
  private Commands playerCommandsFromSession;
  private Commands playerCommandsFromPlayer;
  private Commands intersectedPlayerCommands;
  @Nullable private Surface videoSurface;
  @Nullable private SurfaceHolder videoSurfaceHolder;
  @Nullable private TextureView videoTextureView;
  private int surfaceWidth;
  private int surfaceHeight;
  @Nullable private IMediaSession iSession;
  private long lastReturnedContentPositionMs;
  private long lastSetPlayWhenReadyCalledTimeMs;

  public MediaControllerImplBase(
      Context context, MediaController instance, SessionToken token, Bundle connectionHints) {
    // Initialize default values.
    playerInfo = PlayerInfo.DEFAULT;
    sessionCommands = SessionCommands.EMPTY;
    playerCommandsFromSession = Commands.EMPTY;
    playerCommandsFromPlayer = Commands.EMPTY;
    intersectedPlayerCommands = Commands.EMPTY;
    listeners =
        new ListenerSet<>(
            instance.getApplicationLooper(),
            Clock.DEFAULT,
            (listener, flags) -> listener.onEvents(instance, new Events(flags)));

    // Initialize members
    this.instance = instance;
    checkNotNull(context, "context must not be null");
    checkNotNull(token, "token must not be null");
    this.context = context;
    sequencedFutureManager = new SequencedFutureManager();
    controllerStub = new MediaControllerStub(this);
    this.token = token;
    this.connectionHints = connectionHints;
    deathRecipient =
        () ->
            MediaControllerImplBase.this.instance.runOnApplicationLooper(
                MediaControllerImplBase.this.instance::release);
    surfaceCallback = new SurfaceCallback();

    serviceConnection =
        (this.token.getType() == TYPE_SESSION)
            ? null
            : new SessionServiceConnection(connectionHints);
    flushCommandQueueHandler = new FlushCommandQueueHandler(instance.getApplicationLooper());
    lastReturnedContentPositionMs = C.TIME_UNSET;
    lastSetPlayWhenReadyCalledTimeMs = C.TIME_UNSET;
  }

  @Override
  public void connect() {
    boolean connectionRequested;
    if (this.token.getType() == TYPE_SESSION) {
      // Session
      serviceConnection = null;
      connectionRequested = requestConnectToSession(connectionHints);
    } else {
      serviceConnection = new SessionServiceConnection(connectionHints);
      connectionRequested = requestConnectToService();
    }
    if (!connectionRequested) {
      this.instance.runOnApplicationLooper(MediaControllerImplBase.this.instance::release);
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
    if (!isPlayerCommandAvailable(COMMAND_STOP)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_STOP,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.stop(controllerStub, seq);
          }
        });

    playerInfo =
        playerInfo.copyWithSessionPositionInfo(
            new SessionPositionInfo(
                playerInfo.sessionPositionInfo.positionInfo,
                playerInfo.sessionPositionInfo.isPlayingAd,
                /* eventTimeMs= */ SystemClock.elapsedRealtime(),
                playerInfo.sessionPositionInfo.durationMs,
                /* bufferedPositionMs= */ playerInfo.sessionPositionInfo.positionInfo.positionMs,
                /* bufferedPercentage= */ calculateBufferedPercentage(
                    playerInfo.sessionPositionInfo.positionInfo.positionMs,
                    playerInfo.sessionPositionInfo.durationMs),
                /* totalBufferedDurationMs= */ 0,
                playerInfo.sessionPositionInfo.currentLiveOffsetMs,
                playerInfo.sessionPositionInfo.contentDurationMs,
                /* contentBufferedPositionMs= */ playerInfo
                    .sessionPositionInfo
                    .positionInfo
                    .positionMs));

    if (playerInfo.playbackState != STATE_IDLE) {
      playerInfo =
          playerInfo.copyWithPlaybackState(STATE_IDLE, /* playerError= */ playerInfo.playerError);
      listeners.queueEvent(
          EVENT_PLAYBACK_STATE_CHANGED, listener -> listener.onPlaybackStateChanged(STATE_IDLE));
      listeners.flushEvents();
    }
  }

  @Override
  public void release() {
    @Nullable IMediaSession iSession = this.iSession;
    if (released) {
      return;
    }
    released = true;
    if (serviceConnection != null) {
      context.unbindService(serviceConnection);
      serviceConnection = null;
    }
    connectedToken = null;
    flushCommandQueueHandler.removeCallbacksAndMessages(/* token= */ null);
    this.iSession = null;
    controllerStub.destroy();
    if (iSession != null) {
      int seq = sequencedFutureManager.obtainNextSequenceNumber();
      try {
        iSession.asBinder().unlinkToDeath(deathRecipient, 0);
        iSession.release(controllerStub, seq);
      } catch (RemoteException e) {
        // No-op.
      }
    }
    sequencedFutureManager.release();
    listeners.release();
  }

  @Override
  @Nullable
  public SessionToken getConnectedToken() {
    return connectedToken;
  }

  @Override
  public boolean isConnected() {
    return iSession != null;
  }

  /* package */ boolean isReleased() {
    return released;
  }

  /* @FunctionalInterface */
  private interface RemoteSessionTask {
    void run(IMediaSession iSession, int seq) throws RemoteException;
  }

  private ListenableFuture<SessionResult> dispatchRemoteSessionTaskWithPlayerCommand(
      @Player.Command int command, RemoteSessionTask task) {
    if (command != COMMAND_SET_VIDEO_SURFACE) {
      flushCommandQueueHandler.sendFlushCommandQueueMessage();
    }
    return dispatchRemoteSessionTask(iSession, task);
  }

  private ListenableFuture<SessionResult> dispatchRemoteSessionTaskWithSessionCommand(
      @CommandCode int commandCode, RemoteSessionTask task) {
    return dispatchRemoteSessionTaskWithSessionCommandInternal(
        commandCode, /* sessionCommand= */ null, task);
  }

  private ListenableFuture<SessionResult> dispatchRemoteSessionTaskWithSessionCommand(
      SessionCommand sessionCommand, RemoteSessionTask task) {
    return dispatchRemoteSessionTaskWithSessionCommandInternal(
        COMMAND_CODE_CUSTOM, sessionCommand, task);
  }

  private ListenableFuture<SessionResult> dispatchRemoteSessionTaskWithSessionCommandInternal(
      @CommandCode int commandCode, SessionCommand sessionCommand, RemoteSessionTask task) {
    return dispatchRemoteSessionTask(
        sessionCommand != null
            ? getSessionInterfaceWithSessionCommandIfAble(sessionCommand)
            : getSessionInterfaceWithSessionCommandIfAble(commandCode),
        task);
  }

  private ListenableFuture<SessionResult> dispatchRemoteSessionTask(
      IMediaSession iSession, RemoteSessionTask task) {
    if (iSession != null) {
      SequencedFutureManager.SequencedFuture<SessionResult> result =
          sequencedFutureManager.createSequencedFuture(new SessionResult(RESULT_INFO_SKIPPED));
      try {
        task.run(iSession, result.getSequenceNumber());
      } catch (RemoteException e) {
        Log.w(TAG, "Cannot connect to the service or the session is gone", e);
        result.set(new SessionResult(RESULT_ERROR_SESSION_DISCONNECTED));
      }
      return result;
    } else {
      // Don't create Future with SequencedFutureManager.
      // Otherwise session would receive discontinued sequence number, and it would make
      // future work item 'keeping call sequence when session execute commands' impossible.
      return Futures.immediateFuture(new SessionResult(RESULT_ERROR_PERMISSION_DENIED));
    }
  }

  @Override
  public void play() {
    if (!isPlayerCommandAvailable(COMMAND_PLAY_PAUSE)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_PLAY_PAUSE,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.play(controllerStub, seq);
          }
        });

    setPlayWhenReady(
        /* playWhenReady= */ true,
        PLAYBACK_SUPPRESSION_REASON_NONE,
        PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
  }

  @Override
  public void pause() {
    if (!isPlayerCommandAvailable(COMMAND_PLAY_PAUSE)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_PLAY_PAUSE,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.pause(controllerStub, seq);
          }
        });

    setPlayWhenReady(
        /* playWhenReady= */ false,
        PLAYBACK_SUPPRESSION_REASON_NONE,
        PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
  }

  @Override
  public void prepare() {
    if (!isPlayerCommandAvailable(COMMAND_PREPARE)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_PREPARE,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.prepare(controllerStub, seq);
          }
        });

    if (playerInfo.playbackState == Player.STATE_IDLE) {
      PlayerInfo playerInfo =
          this.playerInfo.copyWithPlaybackState(
              this.playerInfo.timeline.isEmpty() ? Player.STATE_ENDED : Player.STATE_BUFFERING,
              /* playerError= */ null);

      updatePlayerInfo(
          playerInfo,
          /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
          /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
          /* positionDiscontinuity= */ false,
          /* ignored */ DISCONTINUITY_REASON_INTERNAL,
          /* mediaItemTransition= */ false,
          /* ignored */ MEDIA_ITEM_TRANSITION_REASON_REPEAT);
    }
  }

  @Override
  public void seekToDefaultPosition() {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_TO_DEFAULT_POSITION)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_DEFAULT_POSITION,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.seekToDefaultPosition(controllerStub, seq);
          }
        });

    seekToInternal(getCurrentMediaItemIndex(), /* positionMs= */ C.TIME_UNSET);
  }

  @Override
  public void seekToDefaultPosition(int mediaItemIndex) {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_TO_MEDIA_ITEM)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_MEDIA_ITEM,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.seekToDefaultPositionWithMediaItemIndex(controllerStub, seq, mediaItemIndex);
          }
        });

    seekToInternal(mediaItemIndex, /* positionMs= */ C.TIME_UNSET);
  }

  @Override
  public void seekTo(long positionMs) {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.seekTo(controllerStub, seq, positionMs);
          }
        });

    seekToInternal(getCurrentMediaItemIndex(), positionMs);
  }

  @Override
  public void seekTo(int mediaItemIndex, long positionMs) {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_TO_MEDIA_ITEM)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_MEDIA_ITEM,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.seekToWithMediaItemIndex(controllerStub, seq, mediaItemIndex, positionMs);
          }
        });

    seekToInternal(mediaItemIndex, positionMs);
  }

  @Override
  public long getSeekBackIncrement() {
    return playerInfo.seekBackIncrementMs;
  }

  @Override
  public void seekBack() {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_BACK)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_BACK, (iSession, seq) -> iSession.seekBack(controllerStub, seq));

    seekToInternalByOffset(-getSeekBackIncrement());
  }

  @Override
  public long getSeekForwardIncrement() {
    return playerInfo.seekForwardIncrementMs;
  }

  @Override
  public void seekForward() {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_FORWARD)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_FORWARD, (iSession, seq) -> iSession.seekForward(controllerStub, seq));

    seekToInternalByOffset(getSeekForwardIncrement());
  }

  @Override
  public PendingIntent getSessionActivity() {
    return sessionActivity;
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (!isPlayerCommandAvailable(COMMAND_PLAY_PAUSE)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_PLAY_PAUSE,
        (iSession, seq) -> iSession.setPlayWhenReady(controllerStub, seq, playWhenReady));

    setPlayWhenReady(
        playWhenReady,
        PLAYBACK_SUPPRESSION_REASON_NONE,
        PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
  }

  @Override
  public boolean getPlayWhenReady() {
    return playerInfo.playWhenReady;
  }

  @Override
  @Player.PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    return playerInfo.playbackSuppressionReason;
  }

  @Override
  @Nullable
  public PlaybackException getPlayerError() {
    return playerInfo.playerError;
  }

  @Override
  @Player.State
  public int getPlaybackState() {
    return playerInfo.playbackState;
  }

  @Override
  public boolean isPlaying() {
    return playerInfo.isPlaying;
  }

  @Override
  public boolean isLoading() {
    return playerInfo.isLoading;
  }

  @Override
  public long getDuration() {
    return playerInfo.sessionPositionInfo.durationMs;
  }

  @Override
  public long getCurrentPosition() {
    if (!playerInfo.isPlaying) {
      return playerInfo.sessionPositionInfo.positionInfo.positionMs;
    }
    long elapsedTimeMs =
        (instance.getTimeDiffMs() != C.TIME_UNSET)
            ? instance.getTimeDiffMs()
            : SystemClock.elapsedRealtime() - playerInfo.sessionPositionInfo.eventTimeMs;
    long estimatedPositionMs =
        playerInfo.sessionPositionInfo.positionInfo.positionMs
            + (long) (elapsedTimeMs * playerInfo.playbackParameters.speed);
    return playerInfo.sessionPositionInfo.durationMs == C.TIME_UNSET
        ? estimatedPositionMs
        : Math.min(estimatedPositionMs, playerInfo.sessionPositionInfo.durationMs);
  }

  @Override
  public long getBufferedPosition() {
    return playerInfo.sessionPositionInfo.bufferedPositionMs;
  }

  @Override
  public int getBufferedPercentage() {
    return playerInfo.sessionPositionInfo.bufferedPercentage;
  }

  @Override
  public long getTotalBufferedDuration() {
    return playerInfo.sessionPositionInfo.totalBufferedDurationMs;
  }

  @Override
  public long getCurrentLiveOffset() {
    return playerInfo.sessionPositionInfo.currentLiveOffsetMs;
  }

  @Override
  public long getContentDuration() {
    return playerInfo.sessionPositionInfo.contentDurationMs;
  }

  @Override
  public long getContentPosition() {
    boolean receivedUpdatedPositionInfo =
        lastSetPlayWhenReadyCalledTimeMs < playerInfo.sessionPositionInfo.eventTimeMs;
    if (!playerInfo.isPlaying || playerInfo.sessionPositionInfo.isPlayingAd) {
      if (receivedUpdatedPositionInfo || lastReturnedContentPositionMs == C.TIME_UNSET) {
        lastReturnedContentPositionMs =
            playerInfo.sessionPositionInfo.positionInfo.contentPositionMs;
      }
      return lastReturnedContentPositionMs;
    }

    if (!receivedUpdatedPositionInfo && lastReturnedContentPositionMs != C.TIME_UNSET) {
      // We need an updated content position to make a new position estimation.
      return lastReturnedContentPositionMs;
    }

    long elapsedTimeMs =
        (instance.getTimeDiffMs() != C.TIME_UNSET)
            ? instance.getTimeDiffMs()
            : SystemClock.elapsedRealtime() - playerInfo.sessionPositionInfo.eventTimeMs;
    long estimatedPositionMs =
        playerInfo.sessionPositionInfo.positionInfo.contentPositionMs
            + (long) (elapsedTimeMs * playerInfo.playbackParameters.speed);
    if (playerInfo.sessionPositionInfo.contentDurationMs != C.TIME_UNSET) {
      estimatedPositionMs =
          Math.min(estimatedPositionMs, playerInfo.sessionPositionInfo.contentDurationMs);
    }
    lastReturnedContentPositionMs = estimatedPositionMs;
    return lastReturnedContentPositionMs;
  }

  @Override
  public long getContentBufferedPosition() {
    return playerInfo.sessionPositionInfo.contentBufferedPositionMs;
  }

  @Override
  public boolean isPlayingAd() {
    return playerInfo.sessionPositionInfo.isPlayingAd;
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return playerInfo.sessionPositionInfo.positionInfo.adGroupIndex;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return playerInfo.sessionPositionInfo.positionInfo.adIndexInAdGroup;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    if (!isPlayerCommandAvailable(COMMAND_SET_SPEED_AND_PITCH)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_SPEED_AND_PITCH,
        (iSession, seq) ->
            iSession.setPlaybackParameters(controllerStub, seq, playbackParameters.toBundle()));

    if (!playerInfo.playbackParameters.equals(playbackParameters)) {
      playerInfo = playerInfo.copyWithPlaybackParameters(playbackParameters);

      listeners.queueEvent(
          EVENT_PLAYBACK_PARAMETERS_CHANGED,
          listener -> listener.onPlaybackParametersChanged(playbackParameters));
      listeners.flushEvents();
    }
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playerInfo.playbackParameters;
  }

  @Override
  public void setPlaybackSpeed(float speed) {
    if (!isPlayerCommandAvailable(COMMAND_SET_SPEED_AND_PITCH)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_SPEED_AND_PITCH,
        (iSession, seq) -> iSession.setPlaybackSpeed(controllerStub, seq, speed));

    if (playerInfo.playbackParameters.speed != speed) {
      PlaybackParameters newPlaybackParameters = playerInfo.playbackParameters.withSpeed(speed);
      playerInfo = playerInfo.copyWithPlaybackParameters(newPlaybackParameters);

      listeners.queueEvent(
          EVENT_PLAYBACK_PARAMETERS_CHANGED,
          listener -> listener.onPlaybackParametersChanged(newPlaybackParameters));
      listeners.flushEvents();
    }
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    return playerInfo.audioAttributes;
  }

  @Override
  public ListenableFuture<SessionResult> setRating(String mediaId, Rating rating) {
    return dispatchRemoteSessionTaskWithSessionCommand(
        COMMAND_CODE_SESSION_SET_RATING,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.setRatingWithMediaId(controllerStub, seq, mediaId, rating.toBundle());
          }
        });
  }

  @Override
  public ListenableFuture<SessionResult> setRating(Rating rating) {
    return dispatchRemoteSessionTaskWithSessionCommand(
        COMMAND_CODE_SESSION_SET_RATING,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.setRating(controllerStub, seq, rating.toBundle());
          }
        });
  }

  @Override
  public ListenableFuture<SessionResult> sendCustomCommand(SessionCommand command, Bundle args) {
    return dispatchRemoteSessionTaskWithSessionCommand(
        command,
        (iSession, seq) -> iSession.onCustomCommand(controllerStub, seq, command.toBundle(), args));
  }

  @Override
  public Timeline getCurrentTimeline() {
    return playerInfo.timeline;
  }

  @Override
  public void setMediaItem(MediaItem mediaItem) {
    if (!isPlayerCommandAvailable(COMMAND_SET_MEDIA_ITEM)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_MEDIA_ITEM,
        (iSession, seq) -> iSession.setMediaItem(controllerStub, seq, mediaItem.toBundle()));

    setMediaItemsInternal(
        Collections.singletonList(mediaItem),
        /* startIndex= */ C.INDEX_UNSET,
        /* startPositionMs= */ C.TIME_UNSET,
        /* resetToDefaultPosition= */ false);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    if (!isPlayerCommandAvailable(COMMAND_SET_MEDIA_ITEM)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_MEDIA_ITEM,
        (iSession, seq) ->
            iSession.setMediaItemWithStartPosition(
                controllerStub, seq, mediaItem.toBundle(), startPositionMs));

    setMediaItemsInternal(
        Collections.singletonList(mediaItem),
        /* startIndex= */ C.INDEX_UNSET,
        /* startPositionMs= */ startPositionMs,
        /* resetToDefaultPosition= */ false);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    if (!isPlayerCommandAvailable(COMMAND_SET_MEDIA_ITEM)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_MEDIA_ITEM,
        (iSession, seq) ->
            iSession.setMediaItemWithResetPosition(
                controllerStub, seq, mediaItem.toBundle(), resetPosition));

    setMediaItemsInternal(
        Collections.singletonList(mediaItem),
        /* startIndex= */ C.INDEX_UNSET,
        /* startPositionMs= */ C.TIME_UNSET,
        /* resetToDefaultPosition= */ resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) ->
            iSession.setMediaItems(
                controllerStub,
                seq,
                new BundleListRetriever(BundleableUtil.toBundleList(mediaItems))));

    setMediaItemsInternal(
        mediaItems,
        /* startIndex= */ C.INDEX_UNSET,
        /* startPositionMs= */ C.TIME_UNSET,
        /* resetToDefaultPosition= */ false);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) ->
            iSession.setMediaItemsWithResetPosition(
                controllerStub,
                seq,
                new BundleListRetriever(BundleableUtil.toBundleList(mediaItems)),
                resetPosition));

    setMediaItemsInternal(
        mediaItems,
        /* startIndex= */ C.INDEX_UNSET,
        /* startPositionMs= */ C.TIME_UNSET,
        /* resetToDefaultPosition= */ resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) ->
            iSession.setMediaItemsWithStartIndex(
                controllerStub,
                seq,
                new BundleListRetriever(BundleableUtil.toBundleList(mediaItems)),
                startIndex,
                startPositionMs));

    setMediaItemsInternal(
        mediaItems, startIndex, startPositionMs, /* resetToDefaultPosition= */ false);
  }

  @Override
  public ListenableFuture<SessionResult> setMediaUri(Uri uri, Bundle extras) {
    return dispatchRemoteSessionTaskWithSessionCommand(
        COMMAND_CODE_SESSION_SET_MEDIA_URI,
        (RemoteSessionTask)
            (iSession, seq) -> iSession.setMediaUri(controllerStub, seq, uri, extras));
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata playlistMetadata) {
    if (!isPlayerCommandAvailable(COMMAND_SET_MEDIA_ITEMS_METADATA)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_MEDIA_ITEMS_METADATA,
        (iSession, seq) ->
            iSession.setPlaylistMetadata(controllerStub, seq, playlistMetadata.toBundle()));

    if (!playerInfo.playlistMetadata.equals(playlistMetadata)) {
      playerInfo = playerInfo.copyWithPlaylistMetadata(playlistMetadata);

      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onPlaylistMetadataChanged(playlistMetadata));
      listeners.flushEvents();
    }
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    return playerInfo.playlistMetadata;
  }

  @Override
  public void addMediaItem(MediaItem mediaItem) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) -> iSession.addMediaItem(controllerStub, seq, mediaItem.toBundle()));

    addMediaItemsInternal(
        getCurrentTimeline().getWindowCount(), Collections.singletonList(mediaItem));
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) ->
            iSession.addMediaItemWithIndex(controllerStub, seq, index, mediaItem.toBundle()));

    addMediaItemsInternal(index, Collections.singletonList(mediaItem));
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) ->
            iSession.addMediaItems(
                controllerStub,
                seq,
                new BundleListRetriever(BundleableUtil.toBundleList(mediaItems))));

    addMediaItemsInternal(getCurrentTimeline().getWindowCount(), mediaItems);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) ->
            iSession.addMediaItemsWithIndex(
                controllerStub,
                seq,
                index,
                new BundleListRetriever(BundleableUtil.toBundleList(mediaItems))));

    addMediaItemsInternal(index, mediaItems);
  }

  private void addMediaItemsInternal(int index, List<MediaItem> mediaItems) {
    if (mediaItems.isEmpty()) {
      return;
    }
    // Add media items to the end of the timeline if the index exceeds the window count.
    index = min(index, playerInfo.timeline.getWindowCount());

    Timeline oldTimeline = playerInfo.timeline;
    List<Window> newWindows = new ArrayList<>();
    List<Period> newPeriods = new ArrayList<>();
    for (int i = 0; i < oldTimeline.getWindowCount(); i++) {
      newWindows.add(oldTimeline.getWindow(i, new Window()));
    }
    for (int i = 0; i < mediaItems.size(); i++) {
      newWindows.add(i + index, createNewWindow(mediaItems.get(i)));
    }
    rebuildPeriods(oldTimeline, newWindows, newPeriods);
    Timeline newTimeline = createMaskingTimeline(newWindows, newPeriods);

    int newMediaItemIndex;
    int newPeriodIndex;
    if (playerInfo.timeline.isEmpty()) {
      newMediaItemIndex = 0;
      newPeriodIndex = 0;
    } else {
      newMediaItemIndex =
          (playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex >= index)
              ? playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex + mediaItems.size()
              : playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex;
      newPeriodIndex =
          (playerInfo.sessionPositionInfo.positionInfo.periodIndex >= index)
              ? playerInfo.sessionPositionInfo.positionInfo.periodIndex + mediaItems.size()
              : playerInfo.sessionPositionInfo.positionInfo.periodIndex;
    }
    PlayerInfo newPlayerInfo =
        maskTimelineAndPositionInfo(
            playerInfo,
            newTimeline,
            newMediaItemIndex,
            newPeriodIndex,
            DISCONTINUITY_REASON_INTERNAL);
    updatePlayerInfo(
        newPlayerInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* mediaItemTransition= */ oldTimeline.isEmpty(),
        MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
  }

  @Override
  public void removeMediaItem(int index) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) -> iSession.removeMediaItem(controllerStub, seq, index));

    removeMediaItemsInternal(/* fromIndex= */ index, /* toIndex= */ index + 1);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) -> iSession.removeMediaItems(controllerStub, seq, fromIndex, toIndex));

    removeMediaItemsInternal(fromIndex, toIndex);
  }

  @Override
  public void clearMediaItems() {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) -> iSession.clearMediaItems(controllerStub, seq));

    removeMediaItemsInternal(/* fromIndex= */ 0, /* toIndex= */ Integer.MAX_VALUE);
  }

  private void removeMediaItemsInternal(int fromIndex, int toIndex) {
    int clippedToIndex = min(toIndex, playerInfo.timeline.getWindowCount());

    checkArgument(
        fromIndex >= 0
            && clippedToIndex >= fromIndex
            && clippedToIndex <= playerInfo.timeline.getWindowCount());

    Timeline oldTimeline = playerInfo.timeline;

    List<Window> newWindows = new ArrayList<>();
    List<Period> newPeriods = new ArrayList<>();
    for (int i = 0; i < oldTimeline.getWindowCount(); i++) {
      if (i < fromIndex || i >= clippedToIndex) {
        newWindows.add(oldTimeline.getWindow(i, new Window()));
      }
    }
    rebuildPeriods(oldTimeline, newWindows, newPeriods);
    Timeline newTimeline = createMaskingTimeline(newWindows, newPeriods);

    int oldMediaItemIndex = getCurrentMediaItemIndex();
    int newMediaItemIndex = oldMediaItemIndex;
    int oldPeriodIndex = playerInfo.sessionPositionInfo.positionInfo.periodIndex;
    int newPeriodIndex = oldPeriodIndex;
    boolean currentItemRemoved =
        getCurrentMediaItemIndex() >= fromIndex && getCurrentMediaItemIndex() < clippedToIndex;
    Window window = new Window();
    if (oldTimeline.isEmpty()) {
      // No masking required. Just forwarding command to session.
    } else {
      if (newTimeline.isEmpty()) {
        newMediaItemIndex = C.INDEX_UNSET;
        newPeriodIndex = 0;
      } else {
        if (currentItemRemoved) {
          int oldNextMediaItemIndex =
              resolveSubsequentMediaItemIndex(
                  getRepeatMode(),
                  getShuffleModeEnabled(),
                  oldMediaItemIndex,
                  oldTimeline,
                  fromIndex,
                  toIndex);
          if (oldNextMediaItemIndex == C.INDEX_UNSET) {
            newMediaItemIndex = newTimeline.getFirstWindowIndex(getShuffleModeEnabled());
          } else if (oldNextMediaItemIndex >= clippedToIndex) {
            newMediaItemIndex = oldNextMediaItemIndex - (clippedToIndex - fromIndex);
          } else {
            newMediaItemIndex = oldNextMediaItemIndex;
          }
          newPeriodIndex = newTimeline.getWindow(newMediaItemIndex, window).firstPeriodIndex;
        } else if (oldMediaItemIndex >= clippedToIndex) {
          newMediaItemIndex -= (clippedToIndex - fromIndex);
          newPeriodIndex =
              getNewPeriodIndexWithoutRemovedPeriods(
                  oldTimeline, oldPeriodIndex, fromIndex, clippedToIndex);
        }
      }

      PlayerInfo newPlayerInfo;
      if (currentItemRemoved) {
        PositionInfo newPositionInfo;
        if (newMediaItemIndex == C.INDEX_UNSET) {
          newPositionInfo = SessionPositionInfo.DEFAULT_POSITION_INFO;
          newPlayerInfo =
              maskTimelineAndPositionInfo(
                  playerInfo,
                  newTimeline,
                  newPositionInfo,
                  SessionPositionInfo.DEFAULT,
                  DISCONTINUITY_REASON_REMOVE);
        } else {
          Window newWindow = newTimeline.getWindow(newMediaItemIndex, new Window());
          long defaultPositionMs = newWindow.getDefaultPositionMs();
          long durationMs = newWindow.getDurationMs();
          newPositionInfo =
              new PositionInfo(
                  /* windowUid= */ null,
                  newMediaItemIndex,
                  newWindow.mediaItem,
                  /* periodUid= */ null,
                  newPeriodIndex,
                  /* positionMs= */ defaultPositionMs,
                  /* contentPositionMs= */ defaultPositionMs,
                  /* adGroupIndex= */ C.INDEX_UNSET,
                  /* adIndexInAdGroup= */ C.INDEX_UNSET);
          newPlayerInfo =
              maskTimelineAndPositionInfo(
                  playerInfo,
                  newTimeline,
                  newPositionInfo,
                  new SessionPositionInfo(
                      newPositionInfo,
                      /* isPlayingAd= */ false,
                      /* eventTimeMs= */ SystemClock.elapsedRealtime(),
                      /* durationMs= */ durationMs,
                      /* bufferedPositionMs= */ defaultPositionMs,
                      /* bufferedPercentage= */ calculateBufferedPercentage(
                          defaultPositionMs, durationMs),
                      /* totalBufferedDurationMs= */ 0,
                      /* currentLiveOffsetMs= */ C.TIME_UNSET,
                      /* contentDurationMs= */ durationMs,
                      /* contentBufferedPositionMs= */ defaultPositionMs),
                  DISCONTINUITY_REASON_REMOVE);
        }
      } else {
        newPlayerInfo =
            maskTimelineAndPositionInfo(
                playerInfo,
                newTimeline,
                newMediaItemIndex,
                newPeriodIndex,
                DISCONTINUITY_REASON_REMOVE);
      }

      // Player transitions to STATE_ENDED if the current index is part of the removed tail.
      final boolean transitionsToEnded =
          newPlayerInfo.playbackState != STATE_IDLE
              && newPlayerInfo.playbackState != STATE_ENDED
              && fromIndex < clippedToIndex
              && clippedToIndex == oldTimeline.getWindowCount()
              && getCurrentMediaItemIndex() >= fromIndex;
      if (transitionsToEnded) {
        newPlayerInfo = newPlayerInfo.copyWithPlaybackState(STATE_ENDED, /* playerError= */ null);
      }

      updatePlayerInfo(
          newPlayerInfo,
          /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
          /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
          /* positionDiscontinuity= */ currentItemRemoved,
          DISCONTINUITY_REASON_REMOVE,
          /* mediaItemTransition*/ playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex
                  >= fromIndex
              && playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex < clippedToIndex,
          MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    }
  }

  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    checkArgument(
        currentIndex >= 0 && currentIndex < playerInfo.timeline.getWindowCount() && newIndex >= 0);

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) -> iSession.moveMediaItem(controllerStub, seq, currentIndex, newIndex));

    int clippedNewIndex = min(newIndex, playerInfo.timeline.getWindowCount() - 1);

    moveMediaItemsInternal(
        /* fromIndex= */ currentIndex, /* toIndex= */ currentIndex + 1, clippedNewIndex);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    if (!isPlayerCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
      return;
    }

    checkArgument(
        fromIndex >= 0
            && fromIndex <= toIndex
            && toIndex <= playerInfo.timeline.getWindowCount()
            && newIndex >= 0);

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        (iSession, seq) ->
            iSession.moveMediaItems(controllerStub, seq, fromIndex, toIndex, newIndex));

    int clippedNewIndex =
        min(newIndex, playerInfo.timeline.getWindowCount() - (toIndex - fromIndex));

    moveMediaItemsInternal(fromIndex, toIndex, clippedNewIndex);
  }

  @Override
  public int getCurrentPeriodIndex() {
    return playerInfo.sessionPositionInfo.positionInfo.periodIndex;
  }

  @Override
  public int getCurrentMediaItemIndex() {
    return playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex == C.INDEX_UNSET
        ? 0
        : playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex;
  }

  // TODO(b/184479406): Get the index directly from Player rather than Timeline.
  @Override
  public int getPreviousMediaItemIndex() {
    return playerInfo.timeline.isEmpty()
        ? C.INDEX_UNSET
        : playerInfo.timeline.getPreviousWindowIndex(
            getCurrentMediaItemIndex(),
            convertRepeatModeForNavigation(playerInfo.repeatMode),
            playerInfo.shuffleModeEnabled);
  }

  // TODO(b/184479406): Get the index directly from Player rather than Timeline.
  @Override
  public int getNextMediaItemIndex() {
    return playerInfo.timeline.isEmpty()
        ? C.INDEX_UNSET
        : playerInfo.timeline.getNextWindowIndex(
            getCurrentMediaItemIndex(),
            convertRepeatModeForNavigation(playerInfo.repeatMode),
            playerInfo.shuffleModeEnabled);
  }

  @Override
  public boolean hasPreviousMediaItem() {
    return getPreviousMediaItemIndex() != C.INDEX_UNSET;
  }

  @Override
  public boolean hasNextMediaItem() {
    return getNextMediaItemIndex() != C.INDEX_UNSET;
  }

  @Override
  public void seekToPreviousMediaItem() {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        (iSession, seq) -> iSession.seekToPreviousMediaItem(controllerStub, seq));

    if (getPreviousMediaItemIndex() != C.INDEX_UNSET) {
      seekToInternal(getPreviousMediaItemIndex(), /* positionMs= */ C.TIME_UNSET);
    }
  }

  @Override
  public void seekToNextMediaItem() {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        (iSession, seq) -> iSession.seekToNextMediaItem(controllerStub, seq));

    if (getNextMediaItemIndex() != C.INDEX_UNSET) {
      seekToInternal(getNextMediaItemIndex(), /* positionMs= */ C.TIME_UNSET);
    }
  }

  @Override
  public void seekToPrevious() {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_TO_PREVIOUS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_PREVIOUS, (iSession, seq) -> iSession.seekToPrevious(controllerStub, seq));

    Timeline timeline = getCurrentTimeline();
    if (timeline.isEmpty() || isPlayingAd()) {
      return;
    }
    boolean hasPreviousMediaItem = hasPreviousMediaItem();
    Window window = timeline.getWindow(getCurrentMediaItemIndex(), new Window());
    if (window.isDynamic && window.isLive()) {
      if (hasPreviousMediaItem) {
        seekToInternal(getPreviousMediaItemIndex(), /* positionMs= */ C.TIME_UNSET);
      }
    } else if (hasPreviousMediaItem && getCurrentPosition() <= getMaxSeekToPreviousPosition()) {
      seekToInternal(getPreviousMediaItemIndex(), /* positionMs= */ C.TIME_UNSET);
    } else {
      seekToInternal(getCurrentMediaItemIndex(), /* positionMs= */ 0);
    }
  }

  @Override
  public long getMaxSeekToPreviousPosition() {
    return playerInfo.maxSeekToPreviousPositionMs;
  }

  @Override
  public void seekToNext() {
    if (!isPlayerCommandAvailable(COMMAND_SEEK_TO_NEXT)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_NEXT, (iSession, seq) -> iSession.seekToNext(controllerStub, seq));

    Timeline timeline = getCurrentTimeline();
    if (timeline.isEmpty() || isPlayingAd()) {
      return;
    }
    if (hasNextMediaItem()) {
      seekToInternal(getNextMediaItemIndex(), /* positionMs= */ C.TIME_UNSET);
    } else {
      Window window = timeline.getWindow(getCurrentMediaItemIndex(), new Window());
      if (window.isDynamic && window.isLive()) {
        seekToInternal(getCurrentMediaItemIndex(), /* positionMs= */ C.TIME_UNSET);
      }
    }
  }

  @Override
  public int getRepeatMode() {
    return playerInfo.repeatMode;
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    if (!isPlayerCommandAvailable(COMMAND_SET_REPEAT_MODE)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_REPEAT_MODE,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.setRepeatMode(controllerStub, seq, repeatMode);
          }
        });

    if (playerInfo.repeatMode != repeatMode) {
      playerInfo = playerInfo.copyWithRepeatMode(repeatMode);

      listeners.queueEvent(
          EVENT_REPEAT_MODE_CHANGED, listener -> listener.onRepeatModeChanged(repeatMode));
      listeners.flushEvents();
    }
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return playerInfo.shuffleModeEnabled;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    if (!isPlayerCommandAvailable(COMMAND_SET_SHUFFLE_MODE)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_SHUFFLE_MODE,
        new RemoteSessionTask() {
          @Override
          public void run(IMediaSession iSession, int seq) throws RemoteException {
            iSession.setShuffleModeEnabled(controllerStub, seq, shuffleModeEnabled);
          }
        });

    if (playerInfo.shuffleModeEnabled != shuffleModeEnabled) {
      playerInfo = playerInfo.copyWithShuffleModeEnabled(shuffleModeEnabled);

      listeners.queueEvent(
          EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          listener -> listener.onShuffleModeEnabledChanged(shuffleModeEnabled));
      listeners.flushEvents();
    }
  }

  @Override
  public CueGroup getCurrentCues() {
    return playerInfo.cueGroup;
  }

  @Override
  public float getVolume() {
    return playerInfo.volume;
  }

  @Override
  public void setVolume(float volume) {
    if (!isPlayerCommandAvailable(COMMAND_SET_VOLUME)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_VOLUME, (iSession, seq) -> iSession.setVolume(controllerStub, seq, volume));

    if (playerInfo.volume != volume) {
      playerInfo = playerInfo.copyWithVolume(volume);

      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET, listener -> listener.onVolumeChanged(volume));
      listeners.flushEvents();
    }
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    return playerInfo.deviceInfo;
  }

  @Override
  public int getDeviceVolume() {
    return playerInfo.deviceVolume;
  }

  @Override
  public boolean isDeviceMuted() {
    return playerInfo.deviceMuted;
  }

  @Override
  public void setDeviceVolume(int volume) {
    if (!isPlayerCommandAvailable(COMMAND_SET_DEVICE_VOLUME)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_DEVICE_VOLUME,
        (iSession, seq) -> iSession.setDeviceVolume(controllerStub, seq, volume));

    if (playerInfo.deviceVolume != volume) {
      playerInfo = playerInfo.copyWithDeviceVolume(volume, playerInfo.deviceMuted);

      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onDeviceVolumeChanged(volume, playerInfo.deviceMuted));
      listeners.flushEvents();
    }
  }

  @Override
  public void increaseDeviceVolume() {
    if (!isPlayerCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_ADJUST_DEVICE_VOLUME,
        (iSession, seq) -> iSession.increaseDeviceVolume(controllerStub, seq));

    int newDeviceVolume = playerInfo.deviceVolume + 1;
    if (newDeviceVolume <= getDeviceInfo().maxVolume) {
      playerInfo = playerInfo.copyWithDeviceVolume(newDeviceVolume, playerInfo.deviceMuted);

      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onDeviceVolumeChanged(newDeviceVolume, playerInfo.deviceMuted));
      listeners.flushEvents();
    }
  }

  @Override
  public void decreaseDeviceVolume() {
    if (!isPlayerCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_ADJUST_DEVICE_VOLUME,
        (iSession, seq) -> iSession.decreaseDeviceVolume(controllerStub, seq));

    int newDeviceVolume = playerInfo.deviceVolume - 1;
    if (newDeviceVolume >= getDeviceInfo().minVolume) {
      playerInfo = playerInfo.copyWithDeviceVolume(newDeviceVolume, playerInfo.deviceMuted);

      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onDeviceVolumeChanged(newDeviceVolume, playerInfo.deviceMuted));
      listeners.flushEvents();
    }
  }

  @Override
  public void setDeviceMuted(boolean muted) {
    if (!isPlayerCommandAvailable(COMMAND_SET_DEVICE_VOLUME)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_DEVICE_VOLUME,
        (iSession, seq) -> iSession.setDeviceMuted(controllerStub, seq, muted));

    if (playerInfo.deviceMuted != muted) {
      playerInfo = playerInfo.copyWithDeviceVolume(playerInfo.deviceVolume, muted);

      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onDeviceVolumeChanged(playerInfo.deviceVolume, muted));
      listeners.flushEvents();
    }
  }

  @Override
  public VideoSize getVideoSize() {
    return playerInfo.videoSize;
  }

  @Override
  public void clearVideoSurface() {
    if (!isPlayerCommandAvailable(COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }

    clearSurfacesAndCallbacks();
    dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(/* surface= */ null);
    maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    if (!isPlayerCommandAvailable(COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }

    if (surface == null || videoSurface != surface) {
      return;
    }
    clearVideoSurface();
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    if (!isPlayerCommandAvailable(COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }

    clearSurfacesAndCallbacks();
    videoSurface = surface;
    dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(surface);
    int newSurfaceSize = surface == null ? 0 : C.LENGTH_UNSET;
    maybeNotifySurfaceSizeChanged(/* width= */ newSurfaceSize, /* height= */ newSurfaceSize);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    if (!isPlayerCommandAvailable(COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }

    if (surfaceHolder == null) {
      clearVideoSurface();
      return;
    }

    if (videoSurfaceHolder == surfaceHolder) {
      return;
    }
    clearSurfacesAndCallbacks();
    videoSurfaceHolder = surfaceHolder;
    videoSurfaceHolder.addCallback(surfaceCallback);

    @Nullable Surface surface = surfaceHolder.getSurface();
    if (surface != null && surface.isValid()) {
      videoSurface = surface;
      dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(surface);
      Rect surfaceSize = surfaceHolder.getSurfaceFrame();
      maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
    } else {
      videoSurface = null;
      dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(/* surface= */ null);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    }
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    if (!isPlayerCommandAvailable(COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }

    if (surfaceHolder == null || videoSurfaceHolder != surfaceHolder) {
      return;
    }
    clearVideoSurface();
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    if (!isPlayerCommandAvailable(COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }

    @Nullable SurfaceHolder surfaceHolder = surfaceView == null ? null : surfaceView.getHolder();
    setVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    if (!isPlayerCommandAvailable(COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }

    @Nullable SurfaceHolder surfaceHolder = surfaceView == null ? null : surfaceView.getHolder();
    clearVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    if (!isPlayerCommandAvailable(COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }

    if (textureView == null) {
      clearVideoSurface();
      return;
    }

    if (videoTextureView == textureView) {
      return;
    }

    clearSurfacesAndCallbacks();
    videoTextureView = textureView;
    videoTextureView.setSurfaceTextureListener(surfaceCallback);

    @Nullable SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
    if (surfaceTexture == null) {
      dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(/* surface= */ null);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    } else {
      videoSurface = new Surface(surfaceTexture);
      dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(videoSurface);
      maybeNotifySurfaceSizeChanged(textureView.getWidth(), textureView.getHeight());
    }
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    if (!isPlayerCommandAvailable(COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }

    if (textureView == null || videoTextureView != textureView) {
      return;
    }
    clearVideoSurface();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    return playerInfo.mediaMetadata;
  }

  @Override
  public Commands getAvailableCommands() {
    return intersectedPlayerCommands;
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    return playerInfo.trackSelectionParameters;
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    if (!isPlayerCommandAvailable(COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
      return;
    }

    dispatchRemoteSessionTaskWithPlayerCommand(
        COMMAND_SET_TRACK_SELECTION_PARAMETERS,
        (iSession, seq) ->
            iSession.setTrackSelectionParameters(controllerStub, seq, parameters.toBundle()));

    if (parameters != playerInfo.trackSelectionParameters) {
      playerInfo = playerInfo.copyWithTrackSelectionParameters(parameters);

      listeners.queueEvent(
          EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
          listener -> listener.onTrackSelectionParametersChanged(parameters));
      listeners.flushEvents();
    }
  }

  @Override
  public SessionCommands getAvailableSessionCommands() {
    return sessionCommands;
  }

  @Override
  public Context getContext() {
    return context;
  }

  @Override
  @Nullable
  public MediaBrowserCompat getBrowserCompat() {
    return null;
  }

  private Timeline createMaskingTimeline(List<Window> windows, List<Period> periods) {
    return new RemotableTimeline(
        new ImmutableList.Builder<Window>().addAll(windows).build(),
        new ImmutableList.Builder<Period>().addAll(periods).build(),
        MediaUtils.generateUnshuffledIndices(windows.size()));
  }

  private void setMediaItemsInternal(
      List<MediaItem> mediaItems,
      int startIndex,
      long startPositionMs,
      boolean resetToDefaultPosition) {
    List<Window> windows = new ArrayList<>();
    List<Period> periods = new ArrayList<>();
    for (int i = 0; i < mediaItems.size(); i++) {
      windows.add(MediaUtils.convertToWindow(mediaItems.get(i), i));
      periods.add(MediaUtils.convertToPeriod(i));
    }

    Timeline newTimeline = createMaskingTimeline(windows, periods);
    if (!newTimeline.isEmpty() && startIndex >= newTimeline.getWindowCount()) {
      throw new IllegalSeekPositionException(newTimeline, startIndex, startPositionMs);
    }

    if (resetToDefaultPosition) {
      startIndex = newTimeline.getFirstWindowIndex(playerInfo.shuffleModeEnabled);
      startPositionMs = C.TIME_UNSET;
    } else if (startIndex == C.INDEX_UNSET) {
      startIndex = playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex;
      startPositionMs = playerInfo.sessionPositionInfo.positionInfo.positionMs;
    }
    PositionInfo newPositionInfo;
    SessionPositionInfo newSessionPositionInfo;
    @Nullable PeriodInfo periodInfo = getPeriodInfo(newTimeline, startIndex, startPositionMs);
    if (periodInfo == null) {
      // Timeline is empty.
      newPositionInfo =
          new PositionInfo(
              /* windowUid= */ null,
              startIndex,
              /* mediaItem= */ null,
              /* periodUid= */ null,
              /* periodIndex= */ 0,
              /* positionMs= */ startPositionMs == C.TIME_UNSET ? 0 : startPositionMs,
              /* contentPositionMs= */ startPositionMs == C.TIME_UNSET ? 0 : startPositionMs,
              /* adGroupIndex= */ C.INDEX_UNSET,
              /* adIndexInAdGroup= */ C.INDEX_UNSET);
      newSessionPositionInfo =
          new SessionPositionInfo(
              newPositionInfo,
              /* isPlayingAd= */ false,
              /* eventTimeMs= */ SystemClock.elapsedRealtime(),
              /* durationMs= */ C.TIME_UNSET,
              /* bufferedPositionMs= */ startPositionMs == C.TIME_UNSET ? 0 : startPositionMs,
              /* bufferedPercentage= */ 0,
              /* totalBufferedDurationMs= */ 0,
              /* currentLiveOffsetMs= */ C.TIME_UNSET,
              /* contentDurationMs= */ C.TIME_UNSET,
              /* contentBufferedPositionMs= */ startPositionMs == C.TIME_UNSET
                  ? 0
                  : startPositionMs);
    } else {
      newPositionInfo =
          new PositionInfo(
              /* windowUid= */ null,
              startIndex,
              mediaItems.get(startIndex),
              /* periodUid= */ null,
              periodInfo.index,
              /* positionMs= */ usToMs(periodInfo.periodPositionUs),
              /* contentPositionMs= */ usToMs(periodInfo.periodPositionUs),
              /* adGroupIndex= */ C.INDEX_UNSET,
              /* adIndexInAdGroup= */ C.INDEX_UNSET);
      newSessionPositionInfo =
          new SessionPositionInfo(
              newPositionInfo,
              /* isPlayingAd= */ false,
              /* eventTimeMs= */ SystemClock.elapsedRealtime(),
              /* durationMs= */ C.TIME_UNSET,
              /* bufferedPositionMs= */ usToMs(periodInfo.periodPositionUs),
              /* bufferedPercentage= */ 0,
              /* totalBufferedDurationMs= */ 0,
              /* currentLiveOffsetMs= */ C.TIME_UNSET,
              /* contentDurationMs= */ C.TIME_UNSET,
              /* contentBufferedPositionMs= */ usToMs(periodInfo.periodPositionUs));
    }
    PlayerInfo newPlayerInfo =
        maskTimelineAndPositionInfo(
            playerInfo,
            newTimeline,
            newPositionInfo,
            newSessionPositionInfo,
            DISCONTINUITY_REASON_REMOVE);

    // Mask the playback state.
    int maskingPlaybackState = newPlayerInfo.playbackState;
    if (startIndex != C.INDEX_UNSET && newPlayerInfo.playbackState != STATE_IDLE) {
      if (newTimeline.isEmpty() || startIndex >= newTimeline.getWindowCount()) {
        // Setting an empty timeline or invalid seek transitions to ended.
        maskingPlaybackState = STATE_ENDED;
      } else {
        maskingPlaybackState = STATE_BUFFERING;
      }
    }
    newPlayerInfo =
        newPlayerInfo.copyWithPlaybackState(maskingPlaybackState, playerInfo.playerError);

    updatePlayerInfo(
        newPlayerInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* positionDiscontinuity= */ !playerInfo.timeline.isEmpty(),
        DISCONTINUITY_REASON_REMOVE,
        /* mediaItemTransition= */ !playerInfo.timeline.isEmpty()
            || !newPlayerInfo.timeline.isEmpty(),
        MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
  }

  private void moveMediaItemsInternal(int fromIndex, int toIndex, int newIndex) {
    if (fromIndex == 0 && toIndex == playerInfo.timeline.getWindowCount()) {
      return;
    }

    Timeline oldTimeline = playerInfo.timeline;

    List<Window> newWindows = new ArrayList<>();
    List<Period> newPeriods = new ArrayList<>();

    for (int i = 0; i < oldTimeline.getWindowCount(); i++) {
      newWindows.add(oldTimeline.getWindow(i, new Window()));
    }
    Util.moveItems(newWindows, fromIndex, toIndex, newIndex);
    rebuildPeriods(oldTimeline, newWindows, newPeriods);
    Timeline newTimeline = createMaskingTimeline(newWindows, newPeriods);

    if (!newTimeline.isEmpty()) {
      int oldWindowIndex = getCurrentMediaItemIndex();
      int newWindowIndex = oldWindowIndex;
      if (oldWindowIndex >= fromIndex && oldWindowIndex < toIndex) {
        // if old window index was part of items that should be moved.
        newWindowIndex = (oldWindowIndex - fromIndex) + newIndex;
      } else {
        if (toIndex <= oldWindowIndex && newIndex > oldWindowIndex) {
          // if items were moved from before the old window index to after the old window index.
          newWindowIndex = oldWindowIndex - (toIndex - fromIndex);
        } else if (toIndex > oldWindowIndex && newIndex <= oldWindowIndex) {
          // if items were moved from after the old window index to before the old window index.
          newWindowIndex = oldWindowIndex + (toIndex - fromIndex);
        }
      }
      Window window = new Window();
      int oldPeriodIndex = playerInfo.sessionPositionInfo.positionInfo.periodIndex;
      int deltaFromFirstPeriodIndex =
          oldPeriodIndex - oldTimeline.getWindow(oldWindowIndex, window).firstPeriodIndex;
      int newPeriodIndex =
          newTimeline.getWindow(newWindowIndex, window).firstPeriodIndex
              + deltaFromFirstPeriodIndex;
      PlayerInfo newPlayerInfo =
          maskTimelineAndPositionInfo(
              playerInfo,
              newTimeline,
              newWindowIndex,
              newPeriodIndex,
              DISCONTINUITY_REASON_INTERNAL);

      updatePlayerInfo(
          newPlayerInfo,
          /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
          /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
          /* positionDiscontinuity= */ false,
          /* ignored */ DISCONTINUITY_REASON_INTERNAL,
          /* mediaItemTransition= */ false,
          /* ignored */ MEDIA_ITEM_TRANSITION_REASON_REPEAT);
    }
  }

  private void seekToInternalByOffset(long offsetMs) {
    long positionMs = getCurrentPosition() + offsetMs;
    long durationMs = getDuration();
    if (durationMs != C.TIME_UNSET) {
      positionMs = min(positionMs, durationMs);
    }
    positionMs = max(positionMs, 0);
    seekToInternal(getCurrentMediaItemIndex(), positionMs);
  }

  private void seekToInternal(int windowIndex, long positionMs) {
    Timeline timeline = playerInfo.timeline;
    if (windowIndex < 0 || (!timeline.isEmpty() && windowIndex >= timeline.getWindowCount())) {
      throw new IllegalSeekPositionException(timeline, windowIndex, positionMs);
    }

    if (isPlayingAd()) {
      return;
    }

    @Player.State
    int newPlaybackState =
        getPlaybackState() == Player.STATE_IDLE ? Player.STATE_IDLE : Player.STATE_BUFFERING;
    PlayerInfo newPlayerInfo =
        playerInfo.copyWithPlaybackState(newPlaybackState, playerInfo.playerError);
    @Nullable PeriodInfo periodInfo = getPeriodInfo(timeline, windowIndex, positionMs);
    if (periodInfo == null) {
      // Timeline is empty.
      PositionInfo newPositionInfo =
          new PositionInfo(
              /* windowUid= */ null,
              windowIndex,
              /* mediaItem= */ null,
              /* periodUid= */ null,
              /* periodIndex= */ 0,
              /* positionMs= */ positionMs == C.TIME_UNSET ? 0 : positionMs,
              /* contentPositionMs= */ positionMs == C.TIME_UNSET ? 0 : positionMs,
              /* adGroupIndex= */ C.INDEX_UNSET,
              /* adIndexInAdGroup= */ C.INDEX_UNSET);
      newPlayerInfo =
          maskTimelineAndPositionInfo(
              playerInfo,
              playerInfo.timeline,
              newPositionInfo,
              new SessionPositionInfo(
                  newPositionInfo,
                  playerInfo.sessionPositionInfo.isPlayingAd,
                  /* eventTimeMs= */ SystemClock.elapsedRealtime(),
                  playerInfo.sessionPositionInfo.durationMs,
                  /* bufferedPositionMs= */ positionMs == C.TIME_UNSET ? 0 : positionMs,
                  /* bufferedPercentage= */ 0,
                  /* totalBufferedDurationMs= */ 0,
                  playerInfo.sessionPositionInfo.currentLiveOffsetMs,
                  playerInfo.sessionPositionInfo.contentDurationMs,
                  /* contentBufferedPositionMs= */ positionMs == C.TIME_UNSET ? 0 : positionMs),
              DISCONTINUITY_REASON_SEEK);
    } else {
      newPlayerInfo = maskPositionInfo(newPlayerInfo, timeline, periodInfo);
    }
    boolean mediaItemTransition =
        !playerInfo.timeline.isEmpty()
            && newPlayerInfo.sessionPositionInfo.positionInfo.mediaItemIndex
                != playerInfo.sessionPositionInfo.positionInfo.mediaItemIndex;
    boolean positionDiscontinuity =
        mediaItemTransition
            || newPlayerInfo.sessionPositionInfo.positionInfo.positionMs
                != playerInfo.sessionPositionInfo.positionInfo.positionMs;
    if (!positionDiscontinuity) {
      return;
    }
    updatePlayerInfo(
        newPlayerInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        positionDiscontinuity,
        /* positionDiscontinuityReason= */ DISCONTINUITY_REASON_SEEK,
        mediaItemTransition,
        MEDIA_ITEM_TRANSITION_REASON_SEEK);
  }

  private void setPlayWhenReady(
      boolean playWhenReady,
      @Player.PlaybackSuppressionReason int playbackSuppressionReason,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
    if (playerInfo.playWhenReady == playWhenReady
        && playerInfo.playbackSuppressionReason == playbackSuppressionReason) {
      return;
    }

    // Stop estimating content position until a new positionInfo arrives from the player
    lastSetPlayWhenReadyCalledTimeMs = SystemClock.elapsedRealtime();
    PlayerInfo playerInfo =
        this.playerInfo.copyWithPlayWhenReady(
            playWhenReady, playWhenReadyChangeReason, playbackSuppressionReason);
    updatePlayerInfo(
        playerInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        playWhenReadyChangeReason,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* mediaItemTransition= */ false,
        /* ignored */ MEDIA_ITEM_TRANSITION_REASON_REPEAT);
  }

  private void updatePlayerInfo(
      PlayerInfo newPlayerInfo,
      @TimelineChangeReason int timelineChangeReason,
      @PlayWhenReadyChangeReason int playWhenReadyChangeReason,
      boolean positionDiscontinuity,
      @DiscontinuityReason int positionDiscontinuityReason,
      boolean mediaItemTransition,
      @MediaItemTransitionReason int mediaItemTransitionReason) {
    // Assign player info immediately such that all getters return the right values, but keep
    // snapshot of previous and new states so that listener invocations are triggered correctly.
    PlayerInfo oldPlayerInfo = this.playerInfo;
    this.playerInfo = newPlayerInfo;

    if (mediaItemTransition) {
      listeners.queueEvent(
          EVENT_MEDIA_ITEM_TRANSITION,
          listener ->
              listener.onMediaItemTransition(
                  newPlayerInfo.getCurrentMediaItem(), mediaItemTransitionReason));
    }
    if (positionDiscontinuity) {
      listeners.queueEvent(
          EVENT_POSITION_DISCONTINUITY,
          listener ->
              listener.onPositionDiscontinuity(
                  newPlayerInfo.oldPositionInfo,
                  newPlayerInfo.newPositionInfo,
                  positionDiscontinuityReason));
    }
    if (!oldPlayerInfo.timeline.equals(newPlayerInfo.timeline)) {
      listeners.queueEvent(
          Player.EVENT_TIMELINE_CHANGED,
          listener -> listener.onTimelineChanged(newPlayerInfo.timeline, timelineChangeReason));
    }
    if (oldPlayerInfo.playbackState != newPlayerInfo.playbackState) {
      listeners.queueEvent(
          EVENT_PLAYBACK_STATE_CHANGED,
          listener -> listener.onPlaybackStateChanged(newPlayerInfo.playbackState));
    }
    if (oldPlayerInfo.playWhenReady != newPlayerInfo.playWhenReady) {
      listeners.queueEvent(
          EVENT_PLAY_WHEN_READY_CHANGED,
          listener ->
              listener.onPlayWhenReadyChanged(
                  newPlayerInfo.playWhenReady, playWhenReadyChangeReason));
    }
    if (oldPlayerInfo.playbackSuppressionReason != newPlayerInfo.playbackSuppressionReason) {
      listeners.queueEvent(
          EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
          listener ->
              listener.onPlaybackSuppressionReasonChanged(newPlayerInfo.playbackSuppressionReason));
    }
    if (oldPlayerInfo.isPlaying != newPlayerInfo.isPlaying) {
      listeners.queueEvent(
          EVENT_IS_PLAYING_CHANGED,
          listener -> listener.onIsPlayingChanged(newPlayerInfo.isPlaying));
    }
    listeners.flushEvents();
  }

  private boolean requestConnectToService() {
    int flags =
        Util.SDK_INT >= 29
            ? Context.BIND_AUTO_CREATE | Context.BIND_INCLUDE_CAPABILITIES
            : Context.BIND_AUTO_CREATE;

    // Service. Needs to get fresh binder whenever connection is needed.
    Intent intent = new Intent(MediaSessionService.SERVICE_INTERFACE);
    intent.setClassName(token.getPackageName(), token.getServiceName());

    // Use bindService() instead of startForegroundService() to start session service for three
    // reasons.
    // 1. Prevent session service owner's stopSelf() from destroying service.
    //    With the startForegroundService(), service's call of stopSelf() will trigger immediate
    //    onDestroy() calls on the main thread even when onConnect() is running in another
    //    thread.
    // 2. Minimize APIs for developers to take care about.
    //    With bindService(), developers only need to take care about Service.onBind()
    //    but Service.onStartCommand() should be also taken care about with the
    //    startForegroundService().
    // 3. Future support for UI-less playback
    //    If a service wants to keep running, it should be either foreground service or
    //    bound service. But there had been request for the feature for system apps
    //    and using bindService() will be better fit with it.
    boolean result = context.bindService(intent, serviceConnection, flags);
    if (!result) {
      Log.w(TAG, "bind to " + token + " failed");
      return false;
    }
    return true;
  }

  private boolean requestConnectToSession(Bundle connectionHints) {
    IMediaSession iSession =
        IMediaSession.Stub.asInterface((IBinder) checkStateNotNull(token.getBinder()));
    int seq = sequencedFutureManager.obtainNextSequenceNumber();
    ConnectionRequest request =
        new ConnectionRequest(context.getPackageName(), Process.myPid(), connectionHints);
    try {
      iSession.connect(controllerStub, seq, request.toBundle());
    } catch (RemoteException e) {
      Log.w(TAG, "Failed to call connection request.", e);
      return false;
    }
    return true;
  }

  private void dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(@Nullable Surface surface) {
    Future<SessionResult> future =
        dispatchRemoteSessionTaskWithPlayerCommand(
            COMMAND_SET_VIDEO_SURFACE,
            (iSession, seq) -> iSession.setVideoSurface(controllerStub, seq, surface));

    try {
      MediaUtils.getFutureResult(future, /* timeoutMs= */ 3_000);
    } catch (ExecutionException e) {
      // Never happens because future.setException will not be called.
      throw new IllegalStateException(e);
    } catch (TimeoutException e) {
      Log.w(TAG, "set/clearVideoSurface takes too long on the session side.", e);
      // TODO(b/188888693): Let developers know the failure in their code.
    }
  }

  private void clearSurfacesAndCallbacks() {
    if (videoTextureView != null) {
      videoTextureView.setSurfaceTextureListener(null);
      videoTextureView = null;
    }
    if (videoSurfaceHolder != null) {
      videoSurfaceHolder.removeCallback(surfaceCallback);
      videoSurfaceHolder = null;
    }
    if (videoSurface != null) {
      videoSurface = null;
    }
  }

  private void maybeNotifySurfaceSizeChanged(int width, int height) {
    if (surfaceWidth != width || surfaceHeight != height) {
      surfaceWidth = width;
      surfaceHeight = height;

      // TODO(b/187152483): Set proper event code when available.
      listeners.sendEvent(
          /* eventFlag= */ C.INDEX_UNSET, listener -> listener.onSurfaceSizeChanged(width, height));
    }
  }

  /** Returns session interface if the controller can send the predefined command. */
  @Nullable
  IMediaSession getSessionInterfaceWithSessionCommandIfAble(@CommandCode int commandCode) {
    checkArgument(commandCode != COMMAND_CODE_CUSTOM);
    if (!sessionCommands.contains(commandCode)) {
      Log.w(TAG, "Controller isn't allowed to call command, commandCode=" + commandCode);
      return null;
    }
    return iSession;
  }

  /** Returns session interface if the controller can send the custom command. */
  @Nullable
  IMediaSession getSessionInterfaceWithSessionCommandIfAble(SessionCommand command) {
    checkArgument(command.commandCode == COMMAND_CODE_CUSTOM);
    if (!sessionCommands.contains(command)) {
      Log.w(TAG, "Controller isn't allowed to call custom session command:" + command.customAction);
      return null;
    }
    return iSession;
  }

  void notifyPeriodicSessionPositionInfoChanged(SessionPositionInfo sessionPositionInfo) {
    if (!isConnected()) {
      return;
    }
    updateSessionPositionInfoIfNeeded(sessionPositionInfo);
  }

  <T extends @NonNull Object> void setFutureResult(int seq, T futureResult) {
    sequencedFutureManager.setFutureResult(seq, futureResult);
  }

  void onConnected(ConnectionState result) {
    if (iSession != null) {
      Log.e(
          TAG,
          "Cannot be notified about the connection result many times."
              + " Probably a bug or malicious app.");
      instance.release();
      return;
    }
    iSession = result.sessionBinder;
    sessionActivity = result.sessionActivity;
    sessionCommands = result.sessionCommands;
    playerCommandsFromSession = result.playerCommandsFromSession;
    playerCommandsFromPlayer = result.playerCommandsFromPlayer;
    intersectedPlayerCommands = intersect(playerCommandsFromSession, playerCommandsFromPlayer);
    playerInfo = result.playerInfo;
    try {
      // Implementation for the local binder is no-op,
      // so can be used without worrying about deadlock.
      result.sessionBinder.asBinder().linkToDeath(deathRecipient, 0);
    } catch (RemoteException e) {
      instance.release();
      return;
    }
    connectedToken =
        new SessionToken(
            token.getUid(),
            TYPE_SESSION,
            result.version,
            token.getPackageName(),
            result.sessionBinder,
            result.tokenExtras);
    instance.notifyAccepted();
  }

  private void sendControllerResult(int seq, SessionResult result) {
    IMediaSession iSession = this.iSession;
    if (iSession == null) {
      return;
    }
    try {
      iSession.onControllerResult(controllerStub, seq, result.toBundle());
    } catch (RemoteException e) {
      Log.w(TAG, "Error in sending");
    }
  }

  private void sendControllerResultWhenReady(int seq, ListenableFuture<SessionResult> future) {
    future.addListener(
        () -> {
          SessionResult result;
          try {
            result = checkNotNull(future.get(), "SessionResult must not be null");
          } catch (CancellationException unused) {
            result = new SessionResult(RESULT_INFO_SKIPPED);
          } catch (ExecutionException | InterruptedException unused) {
            result = new SessionResult(RESULT_ERROR_UNKNOWN);
          }
          sendControllerResult(seq, result);
        },
        MoreExecutors.directExecutor());
  }

  void onCustomCommand(int seq, SessionCommand command, Bundle args) {
    if (!isConnected()) {
      return;
    }
    instance.notifyControllerListener(
        listener -> {
          ListenableFuture<SessionResult> future =
              checkNotNull(
                  listener.onCustomCommand(instance, command, args),
                  "ControllerCallback#onCustomCommand() must not return null");
          sendControllerResultWhenReady(seq, future);
        });
  }

  @SuppressWarnings("deprecation") // Implementing and calling deprecated listener method.
  void onPlayerInfoChanged(
      PlayerInfo newPlayerInfo,
      @TimelineChangeReason int timelineChangedReason,
      boolean isTimelineExcluded) {
    if (!isConnected()) {
      return;
    }
    PlayerInfo oldPlayerInfo = playerInfo;
    playerInfo = newPlayerInfo;
    if (isTimelineExcluded) {
      playerInfo = playerInfo.copyWithTimeline(oldPlayerInfo.timeline);
    }
    PlaybackException oldPlayerError = oldPlayerInfo.playerError;
    PlaybackException playerError = playerInfo.playerError;
    boolean errorsMatch =
        oldPlayerError == playerError
            || (oldPlayerError != null && oldPlayerError.errorInfoEquals(playerError));
    if (!errorsMatch) {
      listeners.queueEvent(
          EVENT_PLAYER_ERROR, listener -> listener.onPlayerErrorChanged(playerInfo.playerError));
      if (playerInfo.playerError != null) {
        listeners.queueEvent(
            EVENT_PLAYER_ERROR, listener -> listener.onPlayerError(playerInfo.playerError));
      }
    }
    MediaItem oldCurrentMediaItem = oldPlayerInfo.getCurrentMediaItem();
    MediaItem currentMediaItem = playerInfo.getCurrentMediaItem();
    if (!Util.areEqual(oldCurrentMediaItem, currentMediaItem)) {
      listeners.queueEvent(
          EVENT_MEDIA_ITEM_TRANSITION,
          listener ->
              listener.onMediaItemTransition(
                  currentMediaItem, playerInfo.mediaItemTransitionReason));
    }
    if (!Util.areEqual(oldPlayerInfo.playbackParameters, playerInfo.playbackParameters)) {
      listeners.queueEvent(
          EVENT_PLAYBACK_PARAMETERS_CHANGED,
          listener -> listener.onPlaybackParametersChanged(playerInfo.playbackParameters));
    }
    if (oldPlayerInfo.repeatMode != playerInfo.repeatMode) {
      listeners.queueEvent(
          EVENT_REPEAT_MODE_CHANGED,
          listener -> listener.onRepeatModeChanged(playerInfo.repeatMode));
    }
    if (oldPlayerInfo.shuffleModeEnabled != playerInfo.shuffleModeEnabled) {
      listeners.queueEvent(
          EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          listener -> listener.onShuffleModeEnabledChanged(playerInfo.shuffleModeEnabled));
    }
    if (!isTimelineExcluded && !Util.areEqual(oldPlayerInfo.timeline, playerInfo.timeline)) {
      listeners.queueEvent(
          EVENT_TIMELINE_CHANGED,
          listener -> listener.onTimelineChanged(playerInfo.timeline, timelineChangedReason));
    }
    if (!Util.areEqual(oldPlayerInfo.playlistMetadata, playerInfo.playlistMetadata)) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onPlaylistMetadataChanged(playerInfo.playlistMetadata));
    }
    if (oldPlayerInfo.volume != playerInfo.volume) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET, listener -> listener.onVolumeChanged(playerInfo.volume));
    }
    if (!Util.areEqual(oldPlayerInfo.audioAttributes, playerInfo.audioAttributes)) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onAudioAttributesChanged(playerInfo.audioAttributes));
    }
    if (!oldPlayerInfo.cueGroup.cues.equals(playerInfo.cueGroup.cues)) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET, listener -> listener.onCues(playerInfo.cueGroup.cues));
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET, listener -> listener.onCues(playerInfo.cueGroup));
    }
    if (!Util.areEqual(oldPlayerInfo.deviceInfo, playerInfo.deviceInfo)) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onDeviceInfoChanged(playerInfo.deviceInfo));
    }
    if (oldPlayerInfo.deviceVolume != playerInfo.deviceVolume
        || oldPlayerInfo.deviceMuted != playerInfo.deviceMuted) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener ->
              listener.onDeviceVolumeChanged(playerInfo.deviceVolume, playerInfo.deviceMuted));
    }
    if (oldPlayerInfo.playWhenReady != playerInfo.playWhenReady) {
      listeners.queueEvent(
          EVENT_PLAY_WHEN_READY_CHANGED,
          listener ->
              listener.onPlayWhenReadyChanged(
                  playerInfo.playWhenReady, playerInfo.playWhenReadyChangedReason));
    }
    if (oldPlayerInfo.playbackSuppressionReason != playerInfo.playbackSuppressionReason) {
      listeners.queueEvent(
          EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
          listener ->
              listener.onPlaybackSuppressionReasonChanged(playerInfo.playbackSuppressionReason));
    }
    if (oldPlayerInfo.playbackState != playerInfo.playbackState) {
      listeners.queueEvent(
          EVENT_PLAYBACK_STATE_CHANGED,
          listener -> listener.onPlaybackStateChanged(playerInfo.playbackState));
    }
    if (oldPlayerInfo.isPlaying != playerInfo.isPlaying) {
      listeners.queueEvent(
          EVENT_IS_PLAYING_CHANGED, listener -> listener.onIsPlayingChanged(playerInfo.isPlaying));
    }
    if (oldPlayerInfo.isLoading != playerInfo.isLoading) {
      listeners.queueEvent(
          EVENT_IS_LOADING_CHANGED, listener -> listener.onIsLoadingChanged(playerInfo.isLoading));
    }
    if (!Util.areEqual(oldPlayerInfo.videoSize, playerInfo.videoSize)) {
      // TODO(b/187152483): Set proper event code when available.
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener -> listener.onVideoSizeChanged(playerInfo.videoSize));
    }
    if (!Util.areEqual(oldPlayerInfo.oldPositionInfo, playerInfo.oldPositionInfo)
        || !Util.areEqual(oldPlayerInfo.newPositionInfo, playerInfo.newPositionInfo)) {
      listeners.queueEvent(
          EVENT_POSITION_DISCONTINUITY,
          listener ->
              listener.onPositionDiscontinuity(
                  playerInfo.oldPositionInfo,
                  playerInfo.newPositionInfo,
                  playerInfo.discontinuityReason));
    }
    if (!Util.areEqual(oldPlayerInfo.mediaMetadata, playerInfo.mediaMetadata)) {
      listeners.queueEvent(
          Player.EVENT_MEDIA_METADATA_CHANGED,
          listener -> listener.onMediaMetadataChanged(playerInfo.mediaMetadata));
    }
    if (oldPlayerInfo.seekBackIncrementMs != playerInfo.seekBackIncrementMs) {
      listeners.queueEvent(
          EVENT_SEEK_BACK_INCREMENT_CHANGED,
          listener -> listener.onSeekBackIncrementChanged(playerInfo.seekBackIncrementMs));
    }
    if (oldPlayerInfo.seekForwardIncrementMs != playerInfo.seekForwardIncrementMs) {
      listeners.queueEvent(
          EVENT_SEEK_FORWARD_INCREMENT_CHANGED,
          listener -> listener.onSeekForwardIncrementChanged(playerInfo.seekForwardIncrementMs));
    }
    if (oldPlayerInfo.maxSeekToPreviousPositionMs != newPlayerInfo.maxSeekToPreviousPositionMs) {
      listeners.queueEvent(
          EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED,
          listener ->
              listener.onMaxSeekToPreviousPositionChanged(
                  newPlayerInfo.maxSeekToPreviousPositionMs));
    }
    if (!Util.areEqual(
        oldPlayerInfo.trackSelectionParameters, newPlayerInfo.trackSelectionParameters)) {
      listeners.queueEvent(
          EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
          listener ->
              listener.onTrackSelectionParametersChanged(newPlayerInfo.trackSelectionParameters));
    }
    listeners.flushEvents();
  }

  void onAvailableCommandsChangedFromSession(
      SessionCommands sessionCommands, Commands playerCommands) {
    if (!isConnected()) {
      return;
    }
    boolean playerCommandsChanged = !Util.areEqual(playerCommandsFromSession, playerCommands);
    boolean sessionCommandsChanged = !Util.areEqual(this.sessionCommands, sessionCommands);
    if (!playerCommandsChanged && !sessionCommandsChanged) {
      return;
    }
    boolean intersectedPlayerCommandsChanged = false;
    if (playerCommandsChanged) {
      playerCommandsFromSession = playerCommands;
      Commands prevIntersectedPlayerCommands = intersectedPlayerCommands;
      intersectedPlayerCommands = intersect(playerCommandsFromSession, playerCommandsFromPlayer);
      intersectedPlayerCommandsChanged =
          !Util.areEqual(intersectedPlayerCommands, prevIntersectedPlayerCommands);
    }
    if (sessionCommandsChanged) {
      this.sessionCommands = sessionCommands;
    }
    if (intersectedPlayerCommandsChanged) {
      listeners.sendEvent(
          EVENT_AVAILABLE_COMMANDS_CHANGED,
          listener -> listener.onAvailableCommandsChanged(intersectedPlayerCommands));
    }
    if (sessionCommandsChanged) {
      instance.notifyControllerListener(
          listener -> listener.onAvailableSessionCommandsChanged(instance, sessionCommands));
    }
  }

  void onAvailableCommandsChangedFromPlayer(Commands commandsFromPlayer) {
    if (!isConnected()) {
      return;
    }
    if (Util.areEqual(playerCommandsFromPlayer, commandsFromPlayer)) {
      return;
    }
    playerCommandsFromPlayer = commandsFromPlayer;
    Commands prevIntersectedPlayerCommands = intersectedPlayerCommands;
    intersectedPlayerCommands = intersect(playerCommandsFromSession, playerCommandsFromPlayer);
    boolean intersectedPlayerCommandsChanged =
        !Util.areEqual(intersectedPlayerCommands, prevIntersectedPlayerCommands);
    if (intersectedPlayerCommandsChanged) {
      listeners.sendEvent(
          EVENT_AVAILABLE_COMMANDS_CHANGED,
          listener -> listener.onAvailableCommandsChanged(intersectedPlayerCommands));
    }
  }

  void onSetCustomLayout(int seq, List<CommandButton> layout) {
    if (!isConnected()) {
      return;
    }
    List<CommandButton> validatedCustomLayout = new ArrayList<>();
    for (int i = 0; i < layout.size(); i++) {
      CommandButton button = layout.get(i);
      if (intersectedPlayerCommands.contains(button.playerCommand)
          || (button.sessionCommand != null && sessionCommands.contains(button.sessionCommand))
          || (button.playerCommand != Player.COMMAND_INVALID
              && sessionCommands.contains(button.playerCommand))) {
        validatedCustomLayout.add(button);
      }
    }
    instance.notifyControllerListener(
        listener -> {
          ListenableFuture<SessionResult> future =
              checkNotNull(
                  listener.onSetCustomLayout(instance, validatedCustomLayout),
                  "MediaController.Listener#onSetCustomLayout() must not return null");
          sendControllerResultWhenReady(seq, future);
        });
  }

  public void onExtrasChanged(Bundle extras) {
    if (!isConnected()) {
      return;
    }
    instance.notifyControllerListener(listener -> listener.onExtrasChanged(instance, extras));
  }

  public void onRenderedFirstFrame() {
    listeners.sendEvent(/* eventFlag= */ C.INDEX_UNSET, Listener::onRenderedFirstFrame);
  }

  private void updateSessionPositionInfoIfNeeded(SessionPositionInfo sessionPositionInfo) {
    if (playerInfo.sessionPositionInfo.eventTimeMs < sessionPositionInfo.eventTimeMs) {
      playerInfo = playerInfo.copyWithSessionPositionInfo(sessionPositionInfo);
    }
  }

  @RepeatMode
  private static int convertRepeatModeForNavigation(@RepeatMode int repeatMode) {
    return repeatMode == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_OFF : repeatMode;
  }

  private boolean isPlayerCommandAvailable(@Player.Command int command) {
    if (!intersectedPlayerCommands.contains(command)) {
      Log.w(TAG, "Controller isn't allowed to call command= " + command);
      return false;
    }
    return true;
  }

  private PlayerInfo maskPositionInfo(
      PlayerInfo playerInfo, Timeline timeline, PeriodInfo periodInfo) {
    int oldPeriodIndex = playerInfo.sessionPositionInfo.positionInfo.periodIndex;
    int newPeriodIndex = periodInfo.index;
    Period oldPeriod = new Period();
    timeline.getPeriod(oldPeriodIndex, oldPeriod);
    Period newPeriod = new Period();
    timeline.getPeriod(newPeriodIndex, newPeriod);
    boolean playingPeriodChanged = oldPeriodIndex != newPeriodIndex;
    long newPositionUs = periodInfo.periodPositionUs;
    long oldPositionUs = Util.msToUs(getCurrentPosition()) - oldPeriod.getPositionInWindowUs();

    if (!playingPeriodChanged && newPositionUs == oldPositionUs) {
      // Period position remains unchanged.
      return playerInfo;
    }

    checkState(!isAd(newPeriod, newPositionUs));

    PositionInfo oldPositionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            oldPeriod.windowIndex,
            playerInfo.sessionPositionInfo.positionInfo.mediaItem,
            /* periodUid= */ null,
            oldPeriodIndex,
            /* positionMs= */ usToMs(oldPeriod.positionInWindowUs + oldPositionUs),
            /* contentPositionMs= */ usToMs(oldPeriod.positionInWindowUs + oldPositionUs),
            playerInfo.sessionPositionInfo.positionInfo.adGroupIndex,
            playerInfo.sessionPositionInfo.positionInfo.adIndexInAdGroup);

    timeline.getPeriod(newPeriodIndex, newPeriod);
    Window newWindow = new Window();
    timeline.getWindow(newPeriod.windowIndex, newWindow);
    PositionInfo newPositionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            newPeriod.windowIndex,
            newWindow.mediaItem,
            /* periodUid= */ null,
            newPeriodIndex,
            /* positionMs= */ usToMs(newPeriod.positionInWindowUs + newPositionUs),
            /* contentPositionMs= */ usToMs(newPeriod.positionInWindowUs + newPositionUs),
            playerInfo.sessionPositionInfo.positionInfo.adGroupIndex,
            playerInfo.sessionPositionInfo.positionInfo.adIndexInAdGroup);
    playerInfo =
        playerInfo.copyWithPositionInfos(
            oldPositionInfo, newPositionInfo, DISCONTINUITY_REASON_SEEK);

    if (playingPeriodChanged || newPositionUs < oldPositionUs) {
      // The playing period changes or a backwards seek within the playing period occurs.
      playerInfo =
          playerInfo.copyWithSessionPositionInfo(
              new SessionPositionInfo(
                  newPositionInfo,
                  /* isPlayingAd= */ false,
                  /* eventTimeMs= */ SystemClock.elapsedRealtime(),
                  newWindow.getDurationMs(),
                  /* bufferedPositionMs= */ usToMs(newPeriod.positionInWindowUs + newPositionUs),
                  /* bufferedPercentage= */ calculateBufferedPercentage(
                      /* bufferedPositionMs= */ usToMs(
                          newPeriod.positionInWindowUs + newPositionUs),
                      newWindow.getDurationMs()),
                  /* totalBufferedDurationMs= */ 0,
                  /* currentLiveOffsetMs= */ C.TIME_UNSET,
                  /* contentDurationMs= */ C.TIME_UNSET,
                  /* contentBufferedPositionMs= */ usToMs(
                      newPeriod.positionInWindowUs + newPositionUs)));
    } else {
      // A forward seek within the playing period (timeline did not change).
      long maskedTotalBufferedDurationUs =
          max(
              0,
              Util.msToUs(playerInfo.sessionPositionInfo.totalBufferedDurationMs)
                  - (newPositionUs - oldPositionUs));
      long maskedBufferedPositionUs = newPositionUs + maskedTotalBufferedDurationUs;

      playerInfo =
          playerInfo.copyWithSessionPositionInfo(
              new SessionPositionInfo(
                  newPositionInfo,
                  /* isPlayingAd= */ false,
                  /* eventTimeMs= */ SystemClock.elapsedRealtime(),
                  newWindow.getDurationMs(),
                  /* bufferedPositionMs= */ usToMs(maskedBufferedPositionUs),
                  /* bufferedPercentage= */ calculateBufferedPercentage(
                      usToMs(maskedBufferedPositionUs), newWindow.getDurationMs()),
                  /* totalBufferedDurationMs= */ usToMs(maskedTotalBufferedDurationUs),
                  /* currentLiveOffsetMs= */ C.TIME_UNSET,
                  /* contentDurationMs= */ C.TIME_UNSET,
                  /* contentBufferedPositionMs= */ usToMs(maskedBufferedPositionUs)));
    }
    return playerInfo;
  }

  @Nullable
  private PeriodInfo getPeriodInfo(Timeline timeline, int windowIndex, long windowPositionMs) {
    if (timeline.isEmpty()) {
      return null;
    }
    Window window = new Window();
    Period period = new Period();
    if (windowIndex == C.INDEX_UNSET || windowIndex >= timeline.getWindowCount()) {
      // Use default position of timeline if window index still unset or if a previous initial seek
      // now turns out to be invalid.
      windowIndex = timeline.getFirstWindowIndex(getShuffleModeEnabled());
      windowPositionMs = timeline.getWindow(windowIndex, window).getDefaultPositionMs();
    }
    return getPeriodInfo(timeline, window, period, windowIndex, Util.msToUs(windowPositionMs));
  }

  private boolean isAd(Period period, long periodPosition) {
    return period.getAdGroupIndexForPositionUs(periodPosition) != C.INDEX_UNSET;
  }

  @Nullable
  private PeriodInfo getPeriodInfo(
      Timeline timeline, Window window, Period period, int windowIndex, long windowPositionUs) {
    checkIndex(windowIndex, 0, timeline.getWindowCount());
    timeline.getWindow(windowIndex, window);
    if (windowPositionUs == C.TIME_UNSET) {
      windowPositionUs = window.getDefaultPositionUs();
      if (windowPositionUs == C.TIME_UNSET) {
        return null;
      }
    }
    int periodIndex = window.firstPeriodIndex;
    timeline.getPeriod(periodIndex, period);
    while (periodIndex < window.lastPeriodIndex
        && period.positionInWindowUs != windowPositionUs
        && timeline.getPeriod(periodIndex + 1, period).positionInWindowUs <= windowPositionUs) {
      periodIndex++;
    }
    timeline.getPeriod(periodIndex, period);
    long periodPositionUs = windowPositionUs - period.positionInWindowUs;
    return new PeriodInfo(periodIndex, periodPositionUs);
  }

  private PlayerInfo maskTimelineAndPositionInfo(
      PlayerInfo playerInfo,
      Timeline timeline,
      int newMediaItemIndex,
      int newPeriodIndex,
      int discontinuityReason) {
    PositionInfo newPositionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            newMediaItemIndex,
            timeline.getWindow(newMediaItemIndex, new Window()).mediaItem,
            /* periodUid= */ null,
            newPeriodIndex,
            playerInfo.sessionPositionInfo.positionInfo.positionMs,
            playerInfo.sessionPositionInfo.positionInfo.contentPositionMs,
            playerInfo.sessionPositionInfo.positionInfo.adGroupIndex,
            playerInfo.sessionPositionInfo.positionInfo.adIndexInAdGroup);
    return maskTimelineAndPositionInfo(
        playerInfo,
        timeline,
        newPositionInfo,
        new SessionPositionInfo(
            newPositionInfo,
            playerInfo.sessionPositionInfo.isPlayingAd,
            /* eventTimeMs= */ SystemClock.elapsedRealtime(),
            playerInfo.sessionPositionInfo.durationMs,
            playerInfo.sessionPositionInfo.bufferedPositionMs,
            playerInfo.sessionPositionInfo.bufferedPercentage,
            playerInfo.sessionPositionInfo.totalBufferedDurationMs,
            playerInfo.sessionPositionInfo.currentLiveOffsetMs,
            playerInfo.sessionPositionInfo.contentDurationMs,
            playerInfo.sessionPositionInfo.contentBufferedPositionMs),
        discontinuityReason);
  }

  private PlayerInfo maskTimelineAndPositionInfo(
      PlayerInfo playerInfo,
      Timeline timeline,
      PositionInfo newPositionInfo,
      SessionPositionInfo newSessionPositionInfo,
      int discontinuityReason) {
    playerInfo =
        new PlayerInfo.Builder(playerInfo)
            .setTimeline(timeline)
            .setOldPositionInfo(playerInfo.sessionPositionInfo.positionInfo)
            .setNewPositionInfo(newPositionInfo)
            .setSessionPositionInfo(newSessionPositionInfo)
            .setDiscontinuityReason(discontinuityReason)
            .build();
    return playerInfo;
  }

  private Period getPeriodWithNewWindowIndex(Timeline timeline, int periodIndex, int windowIndex) {
    Period period = new Period();
    timeline.getPeriod(periodIndex, period);
    period.windowIndex = windowIndex;
    return period;
  }

  private int getNewPeriodIndexWithoutRemovedPeriods(
      Timeline timeline, int oldPeriodIndex, int fromIndex, int toIndex) {
    if (oldPeriodIndex == C.INDEX_UNSET) {
      return oldPeriodIndex;
    }
    int newPeriodIndex = oldPeriodIndex;
    for (int i = fromIndex; i < toIndex; i++) {
      Window window = new Window();
      timeline.getWindow(i, window);
      int size = window.lastPeriodIndex - window.firstPeriodIndex + 1;
      newPeriodIndex -= size;
    }
    return newPeriodIndex;
  }

  private static Window createNewWindow(MediaItem mediaItem) {
    return new Window()
        .set(
            /* uid= */ 0,
            mediaItem,
            /* manifest= */ null,
            /* presentationStartTimeMs= */ 0,
            /* windowStartTimeMs= */ 0,
            /* elapsedRealtimeEpochOffsetMs= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* liveConfiguration= */ null,
            /* defaultPositionUs= */ 0,
            /* durationUs= */ C.TIME_UNSET,
            /* firstPeriodIndex= */ C.INDEX_UNSET,
            /* lastPeriodIndex= */ C.INDEX_UNSET,
            /* positionInFirstPeriodUs= */ 0);
  }

  private static Period createNewPeriod(int windowIndex) {
    return new Period()
        .set(
            /* id= */ null,
            /* uid= */ null,
            windowIndex,
            /* durationUs= */ C.TIME_UNSET,
            /* positionInWindowUs= */ 0,
            /* adPlaybackState= */ AdPlaybackState.NONE,
            /* isPlaceholder= */ true);
  }

  private void rebuildPeriods(
      Timeline oldTimeline, List<Window> newWindows, List<Period> newPeriods) {
    for (int i = 0; i < newWindows.size(); i++) {
      Window window = newWindows.get(i);
      int firstPeriodIndex = window.firstPeriodIndex;
      int lastPeriodIndex = window.lastPeriodIndex;
      if (firstPeriodIndex == C.INDEX_UNSET || lastPeriodIndex == C.INDEX_UNSET) {
        window.firstPeriodIndex = newPeriods.size();
        window.lastPeriodIndex = newPeriods.size();
        newPeriods.add(createNewPeriod(i));
      } else {
        window.firstPeriodIndex = newPeriods.size();
        window.lastPeriodIndex = newPeriods.size() + (lastPeriodIndex - firstPeriodIndex);
        for (int j = firstPeriodIndex; j <= lastPeriodIndex; j++) {
          newPeriods.add(
              getPeriodWithNewWindowIndex(oldTimeline, /* periodIndex= */ j, /* windowIndex= */ i));
        }
      }
    }
  }

  private static int resolveSubsequentMediaItemIndex(
      @RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      int oldMediaItemIndex,
      Timeline oldTimeline,
      int fromIndex,
      int toIndex) {
    int newMediaItemIndex = C.INDEX_UNSET;
    int maxIterations = oldTimeline.getWindowCount();
    for (int i = 0; i < maxIterations; i++) {
      oldMediaItemIndex =
          oldTimeline.getNextWindowIndex(oldMediaItemIndex, repeatMode, shuffleModeEnabled);
      if (oldMediaItemIndex == C.INDEX_UNSET) {
        // We've reached the end of the old timeline.
        break;
      }
      if (oldMediaItemIndex < fromIndex || oldMediaItemIndex >= toIndex) {
        newMediaItemIndex = oldMediaItemIndex;
        break;
      }
    }
    return newMediaItemIndex;
  }

  // This will be called on the main thread.
  private class SessionServiceConnection implements ServiceConnection {

    private final Bundle connectionHints;

    public SessionServiceConnection(Bundle connectionHints) {
      this.connectionHints = connectionHints;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      boolean connectionRequested = false;
      try {
        // Note that it's always main-thread.
        if (!token.getPackageName().equals(name.getPackageName())) {
          Log.e(
              TAG,
              "Expected connection to "
                  + token.getPackageName()
                  + " but is"
                  + " connected to "
                  + name);
          return;
        }
        IMediaSessionService iService = IMediaSessionService.Stub.asInterface(service);
        if (iService == null) {
          Log.e(TAG, "Service interface is missing.");
          return;
        }
        ConnectionRequest request =
            new ConnectionRequest(getContext().getPackageName(), Process.myPid(), connectionHints);
        iService.connect(controllerStub, request.toBundle());
        connectionRequested = true;
      } catch (RemoteException e) {
        Log.w(TAG, "Service " + name + " has died prematurely");
      } finally {
        if (!connectionRequested) {
          instance.runOnApplicationLooper(instance::release);
        }
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      // Temporal lose of the binding because of the service crash. System will automatically
      // rebind, but we'd better to release() here. Otherwise ControllerCallback#onConnected()
      // would be called multiple times, and the controller would be connected to the
      // different session everytime.
      instance.runOnApplicationLooper(instance::release);
    }

    @Override
    public void onBindingDied(ComponentName name) {
      // Permanent lose of the binding because of the service package update or removed.
      // This SessionServiceRecord will be removed accordingly, but forget session binder here
      // for sure.
      instance.runOnApplicationLooper(instance::release);
    }
  }

  private class SurfaceCallback
      implements SurfaceHolder.Callback, TextureView.SurfaceTextureListener {

    // SurfaceHolder.Callback implementation

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      if (videoSurfaceHolder != holder) {
        return;
      }
      videoSurface = holder.getSurface();
      dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(videoSurface);
      Rect surfaceSize = holder.getSurfaceFrame();
      maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      if (videoSurfaceHolder != holder) {
        return;
      }
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      if (videoSurfaceHolder != holder) {
        return;
      }
      videoSurface = null;
      dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(/* surface= */ null);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    }

    // TextureView.SurfaceTextureListener implementation

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
      if (videoTextureView == null || videoTextureView.getSurfaceTexture() != surfaceTexture) {
        return;
      }
      videoSurface = new Surface(surfaceTexture);
      dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(videoSurface);
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
      if (videoTextureView == null || videoTextureView.getSurfaceTexture() != surfaceTexture) {
        return;
      }
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
      if (videoTextureView == null || videoTextureView.getSurfaceTexture() != surfaceTexture) {
        return true;
      }
      videoSurface = null;
      dispatchRemoteSetVideoSurfaceTaskAndWaitForFuture(/* surface= */ null);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
      // Do nothing.
    }
  }

  private class FlushCommandQueueHandler extends Handler {

    private static final int MSG_FLUSH_COMMAND_QUEUE = 1;

    public FlushCommandQueueHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_FLUSH_COMMAND_QUEUE) {
        try {
          iSession.flushCommandQueue(controllerStub);
        } catch (RemoteException e) {
          Log.w(TAG, "Error in sending flushCommandQueue");
        }
      }
    }

    public void sendFlushCommandQueueMessage() {
      if (iSession != null && !hasMessages(MSG_FLUSH_COMMAND_QUEUE)) {
        // Send message to notify the end of the transaction. It will be handled when the current
        // looper iteration is over.
        sendEmptyMessage(MSG_FLUSH_COMMAND_QUEUE);
      }
    }
  }

  private static final class PeriodInfo {
    private final int index;
    private final long periodPositionUs;

    public PeriodInfo(int index, long periodPositionUs) {
      this.index = index;
      this.periodPositionUs = periodPositionUs;
    }
  }
}
