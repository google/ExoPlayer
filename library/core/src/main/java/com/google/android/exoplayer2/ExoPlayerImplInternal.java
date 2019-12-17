/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.DefaultMediaClock.PlaybackParameterListener;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Implements the internal behavior of {@link ExoPlayerImpl}. */
/* package */ final class ExoPlayerImplInternal
    implements Handler.Callback,
        MediaPeriod.Callback,
        TrackSelector.InvalidationListener,
        Playlist.PlaylistInfoRefreshListener,
        PlaybackParameterListener,
        PlayerMessage.Sender {

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_PLAYBACK_INFO_CHANGED = 0;
  public static final int MSG_PLAYBACK_PARAMETERS_CHANGED = 1;

  // Internal messages
  private static final int MSG_PREPARE = 0;
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_SET_PLAYBACK_PARAMETERS = 4;
  private static final int MSG_SET_SEEK_PARAMETERS = 5;
  private static final int MSG_STOP = 6;
  private static final int MSG_RELEASE = 7;
  private static final int MSG_PERIOD_PREPARED = 8;
  private static final int MSG_SOURCE_CONTINUE_LOADING_REQUESTED = 9;
  private static final int MSG_TRACK_SELECTION_INVALIDATED = 10;
  private static final int MSG_SET_REPEAT_MODE = 11;
  private static final int MSG_SET_SHUFFLE_ENABLED = 12;
  private static final int MSG_SET_FOREGROUND_MODE = 13;
  private static final int MSG_SEND_MESSAGE = 14;
  private static final int MSG_SEND_MESSAGE_TO_TARGET_THREAD = 15;
  private static final int MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL = 16;
  private static final int MSG_SET_MEDIA_SOURCES = 17;
  private static final int MSG_ADD_MEDIA_SOURCES = 18;
  private static final int MSG_MOVE_MEDIA_SOURCES = 19;
  private static final int MSG_REMOVE_MEDIA_SOURCES = 20;
  private static final int MSG_SET_SHUFFLE_ORDER = 21;
  private static final int MSG_PLAYLIST_UPDATE_REQUESTED = 22;

  private static final int ACTIVE_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;

  private final Renderer[] renderers;
  private final RendererCapabilities[] rendererCapabilities;
  private final TrackSelector trackSelector;
  private final TrackSelectorResult emptyTrackSelectorResult;
  private final LoadControl loadControl;
  private final BandwidthMeter bandwidthMeter;
  private final HandlerWrapper handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;
  private final Timeline.Window window;
  private final Timeline.Period period;
  private final long backBufferDurationUs;
  private final boolean retainBackBufferFromKeyframe;
  private final DefaultMediaClock mediaClock;
  private final PlaybackInfoUpdate playbackInfoUpdate;
  private final ArrayList<PendingMessageInfo> pendingMessages;
  private final Clock clock;
  private final MediaPeriodQueue queue;
  private final Playlist playlist;

  @SuppressWarnings("unused")
  private SeekParameters seekParameters;

  private PlaybackInfo playbackInfo;
  private Renderer[] enabledRenderers;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private boolean shouldContinueLoading;
  @Player.RepeatMode private int repeatMode;
  private boolean shuffleModeEnabled;
  private boolean foregroundMode;

  @Nullable private SeekPosition pendingInitialSeekPosition;
  private long rendererPositionUs;
  private int nextPendingMessageIndex;
  private boolean deliverPendingMessageAtStartPositionRequired;

  private long releaseTimeoutMs;

  public ExoPlayerImplInternal(
      Renderer[] renderers,
      TrackSelector trackSelector,
      TrackSelectorResult emptyTrackSelectorResult,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      boolean playWhenReady,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      @Nullable AnalyticsCollector analyticsCollector,
      Handler eventHandler,
      Clock clock) {
    this.renderers = renderers;
    this.trackSelector = trackSelector;
    this.emptyTrackSelectorResult = emptyTrackSelectorResult;
    this.loadControl = loadControl;
    this.bandwidthMeter = bandwidthMeter;
    this.playWhenReady = playWhenReady;
    this.repeatMode = repeatMode;
    this.shuffleModeEnabled = shuffleModeEnabled;
    this.eventHandler = eventHandler;
    this.clock = clock;
    this.queue = new MediaPeriodQueue();

    backBufferDurationUs = loadControl.getBackBufferDurationUs();
    retainBackBufferFromKeyframe = loadControl.retainBackBufferFromKeyframe();

    seekParameters = SeekParameters.DEFAULT;
    playbackInfo =
        PlaybackInfo.createDummy(/* startPositionUs= */ C.TIME_UNSET, emptyTrackSelectorResult);
    playbackInfoUpdate = new PlaybackInfoUpdate();
    rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      renderers[i].setIndex(i);
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    mediaClock = new DefaultMediaClock(this, clock);
    pendingMessages = new ArrayList<>();
    enabledRenderers = new Renderer[0];
    window = new Timeline.Window();
    period = new Timeline.Period();
    trackSelector.init(/* listener= */ this, bandwidthMeter);

    // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
    // not normally change to this priority" is incorrect.
    internalPlaybackThread =
        new HandlerThread("ExoPlayerImplInternal:Handler", Process.THREAD_PRIORITY_AUDIO);
    internalPlaybackThread.start();
    handler = clock.createHandler(internalPlaybackThread.getLooper(), this);
    deliverPendingMessageAtStartPositionRequired = true;
    playlist = new Playlist(this);
    if (analyticsCollector != null) {
      playlist.setAnalyticsCollector(eventHandler, analyticsCollector);
    }
  }

  public void experimental_setReleaseTimeoutMs(long releaseTimeoutMs) {
    this.releaseTimeoutMs = releaseTimeoutMs;
  }

  public void prepare() {
    handler.obtainMessage(MSG_PREPARE).sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void setRepeatMode(@Player.RepeatMode int repeatMode) {
    handler.obtainMessage(MSG_SET_REPEAT_MODE, repeatMode, 0).sendToTarget();
  }

  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    handler.obtainMessage(MSG_SET_SHUFFLE_ENABLED, shuffleModeEnabled ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(Timeline timeline, int windowIndex, long positionUs) {
    handler
        .obtainMessage(MSG_SEEK_TO, new SeekPosition(timeline, windowIndex, positionUs))
        .sendToTarget();
  }

  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    handler.obtainMessage(MSG_SET_PLAYBACK_PARAMETERS, playbackParameters).sendToTarget();
  }

  public void setSeekParameters(SeekParameters seekParameters) {
    handler.obtainMessage(MSG_SET_SEEK_PARAMETERS, seekParameters).sendToTarget();
  }

  public void stop(boolean reset) {
    handler.obtainMessage(MSG_STOP, reset ? 1 : 0, 0).sendToTarget();
  }

  public void setMediaSources(
      List<Playlist.MediaSourceHolder> mediaSources,
      int windowIndex,
      long positionUs,
      ShuffleOrder shuffleOrder) {
    handler
        .obtainMessage(
            MSG_SET_MEDIA_SOURCES,
            new PlaylistUpdateMessage(mediaSources, shuffleOrder, windowIndex, positionUs))
        .sendToTarget();
  }

  public void addMediaSources(
      int index, List<Playlist.MediaSourceHolder> mediaSources, ShuffleOrder shuffleOrder) {
    handler
        .obtainMessage(
            MSG_ADD_MEDIA_SOURCES,
            index,
            /* ignored */ 0,
            new PlaylistUpdateMessage(
                mediaSources,
                shuffleOrder,
                /* windowIndex= */ C.INDEX_UNSET,
                /* positionUs= */ C.TIME_UNSET))
        .sendToTarget();
  }

  public void removeMediaSources(int fromIndex, int toIndex, ShuffleOrder shuffleOrder) {
    handler
        .obtainMessage(MSG_REMOVE_MEDIA_SOURCES, fromIndex, toIndex, shuffleOrder)
        .sendToTarget();
  }

  public void moveMediaSources(
      int fromIndex, int toIndex, int newFromIndex, ShuffleOrder shuffleOrder) {
    MoveMediaItemsMessage moveMediaItemsMessage =
        new MoveMediaItemsMessage(fromIndex, toIndex, newFromIndex, shuffleOrder);
    handler.obtainMessage(MSG_MOVE_MEDIA_SOURCES, moveMediaItemsMessage).sendToTarget();
  }

  public void setShuffleOrder(ShuffleOrder shuffleOrder) {
    handler.obtainMessage(MSG_SET_SHUFFLE_ORDER, shuffleOrder).sendToTarget();
  }

  @Override
  public synchronized void sendMessage(PlayerMessage message) {
    if (released || !internalPlaybackThread.isAlive()) {
      Log.w(TAG, "Ignoring messages sent after release.");
      message.markAsProcessed(/* isDelivered= */ false);
      return;
    }
    handler.obtainMessage(MSG_SEND_MESSAGE, message).sendToTarget();
  }

  public synchronized void setForegroundMode(boolean foregroundMode) {
    if (released || !internalPlaybackThread.isAlive()) {
      return;
    }
    if (foregroundMode) {
      handler.obtainMessage(MSG_SET_FOREGROUND_MODE, /* foregroundMode */ 1, 0).sendToTarget();
    } else {
      AtomicBoolean processedFlag = new AtomicBoolean();
      handler
          .obtainMessage(MSG_SET_FOREGROUND_MODE, /* foregroundMode */ 0, 0, processedFlag)
          .sendToTarget();
      boolean wasInterrupted = false;
      while (!processedFlag.get()) {
        try {
          wait();
        } catch (InterruptedException e) {
          wasInterrupted = true;
        }
      }
      if (wasInterrupted) {
        // Restore the interrupted status.
        Thread.currentThread().interrupt();
      }
    }
  }

  public synchronized boolean release() {
    if (released || !internalPlaybackThread.isAlive()) {
      return true;
    }

    handler.sendEmptyMessage(MSG_RELEASE);
    try {
      if (releaseTimeoutMs > 0) {
        waitUntilReleased(releaseTimeoutMs);
      } else {
        waitUntilReleased();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return released;
  }

  public Looper getPlaybackLooper() {
    return internalPlaybackThread.getLooper();
  }

  // Playlist.PlaylistInfoRefreshListener implementation.

  @Override
  public void onPlaylistUpdateRequested() {
    handler.sendEmptyMessage(MSG_PLAYLIST_UPDATE_REQUESTED);
  }

  // MediaPeriod.Callback implementation.

  @Override
  public void onPrepared(MediaPeriod source) {
    handler.obtainMessage(MSG_PERIOD_PREPARED, source).sendToTarget();
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod source) {
    handler.obtainMessage(MSG_SOURCE_CONTINUE_LOADING_REQUESTED, source).sendToTarget();
  }

  // TrackSelector.InvalidationListener implementation.

  @Override
  public void onTrackSelectionsInvalidated() {
    handler.sendEmptyMessage(MSG_TRACK_SELECTION_INVALIDATED);
  }

  // DefaultMediaClock.PlaybackParameterListener implementation.

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    sendPlaybackParametersChangedInternal(playbackParameters, /* acknowledgeCommand= */ false);
  }

  // Handler.Callback implementation.

  @Override
  @SuppressWarnings("unchecked")
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_PREPARE:
          prepareInternal();
          break;
        case MSG_SET_PLAY_WHEN_READY:
          setPlayWhenReadyInternal(msg.arg1 != 0);
          break;
        case MSG_SET_REPEAT_MODE:
          setRepeatModeInternal(msg.arg1);
          break;
        case MSG_SET_SHUFFLE_ENABLED:
          setShuffleModeEnabledInternal(msg.arg1 != 0);
          break;
        case MSG_DO_SOME_WORK:
          doSomeWork();
          break;
        case MSG_SEEK_TO:
          seekToInternal((SeekPosition) msg.obj);
          break;
        case MSG_SET_PLAYBACK_PARAMETERS:
          setPlaybackParametersInternal((PlaybackParameters) msg.obj);
          break;
        case MSG_SET_SEEK_PARAMETERS:
          setSeekParametersInternal((SeekParameters) msg.obj);
          break;
        case MSG_SET_FOREGROUND_MODE:
          setForegroundModeInternal(
              /* foregroundMode= */ msg.arg1 != 0, /* processedFlag= */ (AtomicBoolean) msg.obj);
          break;
        case MSG_STOP:
          stopInternal(
              /* forceResetRenderers= */ false,
              /* resetPositionAndState= */ msg.arg1 != 0,
              /* acknowledgeStop= */ true);
          break;
        case MSG_PERIOD_PREPARED:
          handlePeriodPrepared((MediaPeriod) msg.obj);
          break;
        case MSG_SOURCE_CONTINUE_LOADING_REQUESTED:
          handleContinueLoadingRequested((MediaPeriod) msg.obj);
          break;
        case MSG_TRACK_SELECTION_INVALIDATED:
          reselectTracksInternal();
          break;
        case MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL:
          handlePlaybackParameters(
              (PlaybackParameters) msg.obj, /* acknowledgeCommand= */ msg.arg1 != 0);
          break;
        case MSG_SEND_MESSAGE:
          sendMessageInternal((PlayerMessage) msg.obj);
          break;
        case MSG_SEND_MESSAGE_TO_TARGET_THREAD:
          sendMessageToTargetThread((PlayerMessage) msg.obj);
          break;
        case MSG_SET_MEDIA_SOURCES:
          setMediaItemsInternal((PlaylistUpdateMessage) msg.obj);
          break;
        case MSG_ADD_MEDIA_SOURCES:
          addMediaItemsInternal((PlaylistUpdateMessage) msg.obj, msg.arg1);
          break;
        case MSG_MOVE_MEDIA_SOURCES:
          moveMediaItemsInternal((MoveMediaItemsMessage) msg.obj);
          break;
        case MSG_REMOVE_MEDIA_SOURCES:
          removeMediaItemsInternal(msg.arg1, msg.arg2, (ShuffleOrder) msg.obj);
          break;
        case MSG_SET_SHUFFLE_ORDER:
          setShuffleOrderInternal((ShuffleOrder) msg.obj);
          break;
        case MSG_PLAYLIST_UPDATE_REQUESTED:
          playlistUpdateRequestedInternal();
          break;
        case MSG_RELEASE:
          releaseInternal();
          // Return immediately to not send playback info updates after release.
          return true;
        default:
          return false;
      }
      maybeNotifyPlaybackInfoChanged();
    } catch (ExoPlaybackException e) {
      Log.e(TAG, getExoPlaybackExceptionMessage(e), e);
      stopInternal(
          /* forceResetRenderers= */ true,
          /* resetPositionAndState= */ false,
          /* acknowledgeStop= */ false);
      playbackInfo = playbackInfo.copyWithPlaybackError(e);
      maybeNotifyPlaybackInfoChanged();
    } catch (IOException e) {
      Log.e(TAG, "Source error.", e);
      stopInternal(
          /* forceResetRenderers= */ false,
          /* resetPositionAndState= */ false,
          /* acknowledgeStop= */ false);
      playbackInfo = playbackInfo.copyWithPlaybackError(ExoPlaybackException.createForSource(e));
      maybeNotifyPlaybackInfoChanged();
    } catch (RuntimeException | OutOfMemoryError e) {
      Log.e(TAG, "Internal runtime error.", e);
      ExoPlaybackException error =
          e instanceof OutOfMemoryError
              ? ExoPlaybackException.createForOutOfMemoryError((OutOfMemoryError) e)
              : ExoPlaybackException.createForUnexpected((RuntimeException) e);
      stopInternal(
          /* forceResetRenderers= */ true,
          /* resetPositionAndState= */ false,
          /* acknowledgeStop= */ false);
      playbackInfo = playbackInfo.copyWithPlaybackError(error);
      maybeNotifyPlaybackInfoChanged();
    }
    return true;
  }

  // Private methods.

  private String getExoPlaybackExceptionMessage(ExoPlaybackException e) {
    if (e.type != ExoPlaybackException.TYPE_RENDERER) {
      return "Playback error.";
    }
    return "Renderer error: index="
        + e.rendererIndex
        + ", type="
        + Util.getTrackTypeString(renderers[e.rendererIndex].getTrackType())
        + ", format="
        + e.rendererFormat
        + ", rendererSupport="
        + RendererCapabilities.getFormatSupportString(e.rendererFormatSupport);
  }

  /**
   * Blocks the current thread until {@link #releaseInternal()} is executed on the playback Thread.
   *
   * <p>If the current thread is interrupted while waiting for {@link #releaseInternal()} to
   * complete, this method will delay throwing the {@link InterruptedException} to ensure that the
   * underlying resources have been released, and will an {@link InterruptedException} <b>after</b>
   * {@link #releaseInternal()} is complete.
   *
   * @throws {@link InterruptedException} if the current Thread was interrupted while waiting for
   *     {@link #releaseInternal()} to complete.
   */
  private synchronized void waitUntilReleased() throws InterruptedException {
    InterruptedException interruptedException = null;
    while (!released) {
      try {
        wait();
      } catch (InterruptedException e) {
        interruptedException = e;
      }
    }

    if (interruptedException != null) {
      throw interruptedException;
    }
  }

  /**
   * Blocks the current thread until {@link #releaseInternal()} is performed on the playback Thread
   * or the specified amount of time has elapsed.
   *
   * <p>If the current thread is interrupted while waiting for {@link #releaseInternal()} to
   * complete, this method will delay throwing the {@link InterruptedException} to ensure that the
   * underlying resources have been released or the operation timed out, and will throw an {@link
   * InterruptedException} afterwards.
   *
   * @param timeoutMs the time in milliseconds to wait for {@link #releaseInternal()} to complete.
   * @throws {@link InterruptedException} if the current Thread was interrupted while waiting for
   *     {@link #releaseInternal()} to complete.
   */
  private synchronized void waitUntilReleased(long timeoutMs) throws InterruptedException {
    long deadlineMs = clock.elapsedRealtime() + timeoutMs;
    long remainingMs = timeoutMs;
    InterruptedException interruptedException = null;
    while (!released && remainingMs > 0) {
      try {
        wait(remainingMs);
      } catch (InterruptedException e) {
        interruptedException = e;
      }
      remainingMs = deadlineMs - clock.elapsedRealtime();
    }

    if (interruptedException != null) {
      throw interruptedException;
    }
  }

  private void setState(int state) {
    if (playbackInfo.playbackState != state) {
      playbackInfo = playbackInfo.copyWithPlaybackState(state);
    }
  }

  private void maybeNotifyPlaybackInfoChanged() {
    if (playbackInfoUpdate.hasPendingUpdate(playbackInfo)) {
      eventHandler
          .obtainMessage(
              MSG_PLAYBACK_INFO_CHANGED,
              playbackInfoUpdate.operationAcks,
              playbackInfoUpdate.positionDiscontinuity
                  ? playbackInfoUpdate.discontinuityReason
                  : C.INDEX_UNSET,
              playbackInfo)
          .sendToTarget();
      playbackInfoUpdate.reset(playbackInfo);
    }
  }

  private void prepareInternal() {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    resetInternal(
        /* resetRenderers= */ false,
        /* resetPosition= */ false,
        /* releasePlaylist= */ false,
        /* clearPlaylist= */ false,
        /* resetError= */ true);
    loadControl.onPrepared();
    setState(playbackInfo.timeline.isEmpty() ? Player.STATE_ENDED : Player.STATE_BUFFERING);
    playlist.prepare(bandwidthMeter.getTransferListener());
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void setMediaItemsInternal(PlaylistUpdateMessage playlistUpdateMessage)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    if (playlistUpdateMessage.windowIndex != C.INDEX_UNSET) {
      pendingInitialSeekPosition =
          new SeekPosition(
              new Playlist.PlaylistTimeline(
                  playlistUpdateMessage.mediaSourceHolders, playlistUpdateMessage.shuffleOrder),
              playlistUpdateMessage.windowIndex,
              playlistUpdateMessage.positionUs);
    }
    Timeline timeline =
        playlist.setMediaSources(
            playlistUpdateMessage.mediaSourceHolders, playlistUpdateMessage.shuffleOrder);
    handlePlaylistInfoRefreshed(timeline);
  }

  private void addMediaItemsInternal(PlaylistUpdateMessage addMessage, int insertionIndex)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline =
        playlist.addMediaSources(
            insertionIndex == C.INDEX_UNSET ? playlist.getSize() : insertionIndex,
            addMessage.mediaSourceHolders,
            addMessage.shuffleOrder);
    handlePlaylistInfoRefreshed(timeline);
  }

  private void moveMediaItemsInternal(MoveMediaItemsMessage moveMediaItemsMessage)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline =
        playlist.moveMediaSourceRange(
            moveMediaItemsMessage.fromIndex,
            moveMediaItemsMessage.toIndex,
            moveMediaItemsMessage.newFromIndex,
            moveMediaItemsMessage.shuffleOrder);
    handlePlaylistInfoRefreshed(timeline);
  }

  private void removeMediaItemsInternal(int fromIndex, int toIndex, ShuffleOrder shuffleOrder)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline = playlist.removeMediaSourceRange(fromIndex, toIndex, shuffleOrder);
    handlePlaylistInfoRefreshed(timeline);
  }

  private void playlistUpdateRequestedInternal() throws ExoPlaybackException {
    handlePlaylistInfoRefreshed(playlist.createTimeline());
  }

  private void setShuffleOrderInternal(ShuffleOrder shuffleOrder) throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline = playlist.setShuffleOrder(shuffleOrder);
    handlePlaylistInfoRefreshed(timeline);
  }

  private void setPlayWhenReadyInternal(boolean playWhenReady) throws ExoPlaybackException {
    rebuffering = false;
    this.playWhenReady = playWhenReady;
    if (!playWhenReady) {
      stopRenderers();
      updatePlaybackPositions();
    } else {
      if (playbackInfo.playbackState == Player.STATE_READY) {
        startRenderers();
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      } else if (playbackInfo.playbackState == Player.STATE_BUFFERING) {
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    }
  }

  private void setRepeatModeInternal(@Player.RepeatMode int repeatMode)
      throws ExoPlaybackException {
    this.repeatMode = repeatMode;
    if (!queue.updateRepeatMode(repeatMode)) {
      seekToCurrentPosition(/* sendDiscontinuity= */ true);
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private void setShuffleModeEnabledInternal(boolean shuffleModeEnabled)
      throws ExoPlaybackException {
    this.shuffleModeEnabled = shuffleModeEnabled;
    if (!queue.updateShuffleModeEnabled(shuffleModeEnabled)) {
      seekToCurrentPosition(/* sendDiscontinuity= */ true);
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private void seekToCurrentPosition(boolean sendDiscontinuity) throws ExoPlaybackException {
    // Renderers may have read from a period that's been removed. Seek back to the current
    // position of the playing period to make sure none of the removed period is played.
    MediaPeriodId periodId = queue.getPlayingPeriod().info.id;
    long newPositionUs =
        seekToPeriodPosition(
            periodId,
            playbackInfo.positionUs,
            /* forceDisableRenderers= */ true,
            /* forceBufferingState= */ false);
    if (newPositionUs != playbackInfo.positionUs) {
      playbackInfo = copyWithNewPosition(periodId, newPositionUs, playbackInfo.contentPositionUs);
      if (sendDiscontinuity) {
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
      }
    }
  }

  private void startRenderers() throws ExoPlaybackException {
    rebuffering = false;
    mediaClock.start();
    for (Renderer renderer : enabledRenderers) {
      renderer.start();
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    mediaClock.stop();
    for (Renderer renderer : enabledRenderers) {
      ensureStopped(renderer);
    }
  }

  private void updatePlaybackPositions() throws ExoPlaybackException {
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (playingPeriodHolder == null) {
      return;
    }

    // Update the playback position.
    long discontinuityPositionUs =
        playingPeriodHolder.prepared
            ? playingPeriodHolder.mediaPeriod.readDiscontinuity()
            : C.TIME_UNSET;
    if (discontinuityPositionUs != C.TIME_UNSET) {
      resetRendererPosition(discontinuityPositionUs);
      // A MediaPeriod may report a discontinuity at the current playback position to ensure the
      // renderers are flushed. Only report the discontinuity externally if the position changed.
      if (discontinuityPositionUs != playbackInfo.positionUs) {
        playbackInfo =
            copyWithNewPosition(
                playbackInfo.periodId, discontinuityPositionUs, playbackInfo.contentPositionUs);
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
      }
    } else {
      rendererPositionUs =
          mediaClock.syncAndGetPositionUs(
              /* isReadingAhead= */ playingPeriodHolder != queue.getReadingPeriod());
      long periodPositionUs = playingPeriodHolder.toPeriodTime(rendererPositionUs);
      maybeTriggerPendingMessages(playbackInfo.positionUs, periodPositionUs);
      playbackInfo.positionUs = periodPositionUs;
    }

    // Update the buffered position and total buffered duration.
    MediaPeriodHolder loadingPeriod = queue.getLoadingPeriod();
    playbackInfo.bufferedPositionUs = loadingPeriod.getBufferedPositionUs();
    playbackInfo.totalBufferedDurationUs = getTotalBufferedDurationUs();
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = clock.uptimeMillis();
    updatePeriods();

    if (playbackInfo.playbackState == Player.STATE_IDLE
        || playbackInfo.playbackState == Player.STATE_ENDED) {
      // Remove all messages. Prepare (in case of IDLE) or seek (in case of ENDED) will resume.
      handler.removeMessages(MSG_DO_SOME_WORK);
      return;
    }

    @Nullable MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (playingPeriodHolder == null) {
      // We're still waiting until the playing period is available.
      scheduleNextWork(operationStartTimeMs, ACTIVE_INTERVAL_MS);
      return;
    }

    TraceUtil.beginSection("doSomeWork");

    updatePlaybackPositions();

    boolean renderersEnded = true;
    boolean renderersAllowPlayback = true;
    if (playingPeriodHolder.prepared) {
      long rendererPositionElapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;
      playingPeriodHolder.mediaPeriod.discardBuffer(
          playbackInfo.positionUs - backBufferDurationUs, retainBackBufferFromKeyframe);
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        if (renderer.getState() == Renderer.STATE_DISABLED) {
          continue;
        }
        // TODO: Each renderer should return the maximum delay before which it wishes to be called
        // again. The minimum of these values should then be used as the delay before the next
        // invocation of this method.
        renderer.render(rendererPositionUs, rendererPositionElapsedRealtimeUs);
        renderersEnded = renderersEnded && renderer.isEnded();
        // Determine whether the renderer allows playback to continue. Playback can continue if the
        // renderer is ready or ended. Also continue playback if the renderer is reading ahead into
        // the next stream or is waiting for the next stream. This is to avoid getting stuck if
        // tracks in the current period have uneven durations and are still being read by another
        // renderer. See: https://github.com/google/ExoPlayer/issues/1874.
        boolean isReadingAhead = playingPeriodHolder.sampleStreams[i] != renderer.getStream();
        boolean isWaitingForNextStream =
            !isReadingAhead
                && playingPeriodHolder.getNext() != null
                && renderer.hasReadStreamToEnd();
        boolean allowsPlayback =
            isReadingAhead || isWaitingForNextStream || renderer.isReady() || renderer.isEnded();
        renderersAllowPlayback = renderersAllowPlayback && allowsPlayback;
        if (!allowsPlayback) {
          renderer.maybeThrowStreamError();
        }
      }
    } else {
      playingPeriodHolder.mediaPeriod.maybeThrowPrepareError();
    }

    long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
    if (renderersEnded
        && playingPeriodHolder.prepared
        && (playingPeriodDurationUs == C.TIME_UNSET
            || playingPeriodDurationUs <= playbackInfo.positionUs)
        && playingPeriodHolder.info.isFinal) {
      setState(Player.STATE_ENDED);
      stopRenderers();
    } else if (playbackInfo.playbackState == Player.STATE_BUFFERING
        && shouldTransitionToReadyState(renderersAllowPlayback)) {
      setState(Player.STATE_READY);
      if (playWhenReady) {
        startRenderers();
      }
    } else if (playbackInfo.playbackState == Player.STATE_READY
        && !(enabledRenderers.length == 0 ? isTimelineReady() : renderersAllowPlayback)) {
      rebuffering = playWhenReady;
      setState(Player.STATE_BUFFERING);
      stopRenderers();
    }

    if (playbackInfo.playbackState == Player.STATE_BUFFERING) {
      for (Renderer renderer : enabledRenderers) {
        renderer.maybeThrowStreamError();
      }
    }

    if ((playWhenReady && playbackInfo.playbackState == Player.STATE_READY)
        || playbackInfo.playbackState == Player.STATE_BUFFERING) {
      scheduleNextWork(operationStartTimeMs, ACTIVE_INTERVAL_MS);
    } else if (enabledRenderers.length != 0 && playbackInfo.playbackState != Player.STATE_ENDED) {
      scheduleNextWork(operationStartTimeMs, IDLE_INTERVAL_MS);
    } else {
      handler.removeMessages(MSG_DO_SOME_WORK);
    }

    TraceUtil.endSection();
  }

  private void scheduleNextWork(long thisOperationStartTimeMs, long intervalMs) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    handler.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + intervalMs);
  }

  private void seekToInternal(SeekPosition seekPosition) throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);

    MediaPeriodId periodId;
    long periodPositionUs;
    long contentPositionUs;
    boolean seekPositionAdjusted;
    @Nullable
    Pair<Object, Long> resolvedSeekPosition =
        resolveSeekPosition(seekPosition, /* trySubsequentPeriods= */ true);
    if (resolvedSeekPosition == null) {
      // The seek position was valid for the timeline that it was performed into, but the
      // timeline has changed or is not ready and a suitable seek position could not be resolved.
      periodId = getDummyFirstMediaPeriodForAds();
      contentPositionUs = C.TIME_UNSET;
      periodPositionUs = C.TIME_UNSET;
      seekPositionAdjusted = true;
    } else {
      // Update the resolved seek position to take ads into account.
      Object periodUid = resolvedSeekPosition.first;
      contentPositionUs = resolvedSeekPosition.second;
      periodId = queue.resolveMediaPeriodIdForAds(periodUid, contentPositionUs);
      if (periodId.isAd()) {
        periodPositionUs = 0;
        seekPositionAdjusted = true;
      } else {
        periodPositionUs = resolvedSeekPosition.second;
        seekPositionAdjusted = seekPosition.windowPositionUs == C.TIME_UNSET;
      }
    }

    try {
      if (playbackInfo.timeline.isEmpty() || !playlist.isPrepared()) {
        // Save seek position for later, as we are still waiting for a prepared source.
        pendingInitialSeekPosition = seekPosition;
      } else if (periodPositionUs == C.TIME_UNSET) {
        // End playback, as we didn't manage to find a valid seek position.
        setState(Player.STATE_ENDED);
        resetInternal(
            /* resetRenderers= */ false,
            /* resetPosition= */ true,
            /* releasePlaylist= */ false,
            /* clearPlaylist= */ false,
            /* resetError= */ true);
      } else {
        // Execute the seek in the current media periods.
        long newPeriodPositionUs = periodPositionUs;
        if (periodId.equals(playbackInfo.periodId)) {
          MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
          if (playingPeriodHolder != null
              && playingPeriodHolder.prepared
              && newPeriodPositionUs != 0) {
            newPeriodPositionUs =
                playingPeriodHolder.mediaPeriod.getAdjustedSeekPositionUs(
                    newPeriodPositionUs, seekParameters);
          }
          if (C.usToMs(newPeriodPositionUs) == C.usToMs(playbackInfo.positionUs)) {
            // Seek will be performed to the current position. Do nothing.
            periodPositionUs = playbackInfo.positionUs;
            return;
          }
        }
        newPeriodPositionUs =
            seekToPeriodPosition(
                periodId,
                newPeriodPositionUs,
                /* forceBufferingState= */ playbackInfo.playbackState == Player.STATE_ENDED);
        seekPositionAdjusted |= periodPositionUs != newPeriodPositionUs;
        periodPositionUs = newPeriodPositionUs;
      }
    } finally {
      playbackInfo = copyWithNewPosition(periodId, periodPositionUs, contentPositionUs);
      if (seekPositionAdjusted) {
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT);
      }
    }
  }

  private long seekToPeriodPosition(
      MediaPeriodId periodId, long periodPositionUs, boolean forceBufferingState)
      throws ExoPlaybackException {
    // Force disable renderers if they are reading from a period other than the one being played.
    return seekToPeriodPosition(
        periodId,
        periodPositionUs,
        queue.getPlayingPeriod() != queue.getReadingPeriod(),
        forceBufferingState);
  }

  private long seekToPeriodPosition(
      MediaPeriodId periodId,
      long periodPositionUs,
      boolean forceDisableRenderers,
      boolean forceBufferingState)
      throws ExoPlaybackException {
    stopRenderers();
    rebuffering = false;
    if (forceBufferingState || playbackInfo.playbackState == Player.STATE_READY) {
      setState(Player.STATE_BUFFERING);
    }

    // Clear the timeline, but keep the requested period if it is already prepared.
    MediaPeriodHolder oldPlayingPeriodHolder = queue.getPlayingPeriod();
    MediaPeriodHolder newPlayingPeriodHolder = oldPlayingPeriodHolder;
    while (newPlayingPeriodHolder != null) {
      if (periodId.equals(newPlayingPeriodHolder.info.id) && newPlayingPeriodHolder.prepared) {
        queue.removeAfter(newPlayingPeriodHolder);
        break;
      }
      newPlayingPeriodHolder = queue.advancePlayingPeriod();
    }

    // Disable all renderers if the period being played is changing, if the seek results in negative
    // renderer timestamps, or if forced.
    if (forceDisableRenderers
        || oldPlayingPeriodHolder != newPlayingPeriodHolder
        || (newPlayingPeriodHolder != null
            && newPlayingPeriodHolder.toRendererTime(periodPositionUs) < 0)) {
      for (Renderer renderer : enabledRenderers) {
        disableRenderer(renderer);
      }
      enabledRenderers = new Renderer[0];
      oldPlayingPeriodHolder = null;
      if (newPlayingPeriodHolder != null) {
        newPlayingPeriodHolder.setRendererOffset(/* rendererPositionOffsetUs= */ 0);
      }
    }

    // Update the holders.
    if (newPlayingPeriodHolder != null) {
      updatePlayingPeriodRenderers(oldPlayingPeriodHolder);
      if (newPlayingPeriodHolder.hasEnabledTracks) {
        periodPositionUs = newPlayingPeriodHolder.mediaPeriod.seekToUs(periodPositionUs);
        newPlayingPeriodHolder.mediaPeriod.discardBuffer(
            periodPositionUs - backBufferDurationUs, retainBackBufferFromKeyframe);
      }
      resetRendererPosition(periodPositionUs);
      maybeContinueLoading();
    } else {
      queue.clear(/* keepFrontPeriodUid= */ true);
      // New period has not been prepared.
      playbackInfo =
          playbackInfo.copyWithTrackInfo(TrackGroupArray.EMPTY, emptyTrackSelectorResult);
      resetRendererPosition(periodPositionUs);
    }

    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    return periodPositionUs;
  }

  private void resetRendererPosition(long periodPositionUs) throws ExoPlaybackException {
    MediaPeriodHolder playingMediaPeriod = queue.getPlayingPeriod();
    rendererPositionUs =
        playingMediaPeriod == null
            ? periodPositionUs
            : playingMediaPeriod.toRendererTime(periodPositionUs);
    mediaClock.resetPosition(rendererPositionUs);
    for (Renderer renderer : enabledRenderers) {
      renderer.resetPosition(rendererPositionUs);
    }
    notifyTrackSelectionDiscontinuity();
  }

  private void setPlaybackParametersInternal(PlaybackParameters playbackParameters) {
    mediaClock.setPlaybackParameters(playbackParameters);
    sendPlaybackParametersChangedInternal(
        mediaClock.getPlaybackParameters(), /* acknowledgeCommand= */ true);
  }

  private void setSeekParametersInternal(SeekParameters seekParameters) {
    this.seekParameters = seekParameters;
  }

  private void setForegroundModeInternal(
      boolean foregroundMode, @Nullable AtomicBoolean processedFlag) {
    if (this.foregroundMode != foregroundMode) {
      this.foregroundMode = foregroundMode;
      if (!foregroundMode) {
        for (Renderer renderer : renderers) {
          if (renderer.getState() == Renderer.STATE_DISABLED) {
            renderer.reset();
          }
        }
      }
    }
    if (processedFlag != null) {
      synchronized (this) {
        processedFlag.set(true);
        notifyAll();
      }
    }
  }

  private void stopInternal(
      boolean forceResetRenderers, boolean resetPositionAndState, boolean acknowledgeStop) {
    resetInternal(
        /* resetRenderers= */ forceResetRenderers || !foregroundMode,
        /* resetPosition= */ resetPositionAndState,
        /* releasePlaylist= */ true,
        /* clearPlaylist= */ resetPositionAndState,
        /* resetError= */ resetPositionAndState);
    playbackInfoUpdate.incrementPendingOperationAcks(acknowledgeStop ? 1 : 0);
    loadControl.onStopped();
    setState(Player.STATE_IDLE);
  }

  private void releaseInternal() {
    resetInternal(
        /* resetRenderers= */ true,
        /* resetPosition= */ true,
        /* releasePlaylist= */ true,
        /* clearPlaylist= */ true,
        /* resetError= */ false);
    loadControl.onReleased();
    setState(Player.STATE_IDLE);
    internalPlaybackThread.quit();
    synchronized (this) {
      released = true;
      notifyAll();
    }
  }

  private void resetInternal(
      boolean resetRenderers,
      boolean resetPosition,
      boolean releasePlaylist,
      boolean clearPlaylist,
      boolean resetError) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    rebuffering = false;
    mediaClock.stop();
    rendererPositionUs = 0;
    for (Renderer renderer : enabledRenderers) {
      try {
        disableRenderer(renderer);
      } catch (ExoPlaybackException | RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Disable failed.", e);
      }
    }
    if (resetRenderers) {
      for (Renderer renderer : renderers) {
        try {
          renderer.reset();
        } catch (RuntimeException e) {
          // There's nothing we can do.
          Log.e(TAG, "Reset failed.", e);
        }
      }
    }
    enabledRenderers = new Renderer[0];

    if (resetPosition) {
      pendingInitialSeekPosition = null;
    } else if (clearPlaylist) {
      // When clearing the playlist, also reset the period-based PlaybackInfo position and convert
      // existing position to initial seek instead.
      resetPosition = true;
      if (pendingInitialSeekPosition == null && !playbackInfo.timeline.isEmpty()) {
        playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period);
        long windowPositionUs = playbackInfo.positionUs + period.getPositionInWindowUs();
        pendingInitialSeekPosition =
            new SeekPosition(Timeline.EMPTY, period.windowIndex, windowPositionUs);
      }
    }

    queue.clear(/* keepFrontPeriodUid= */ !clearPlaylist);
    shouldContinueLoading = false;
    Timeline timeline = playbackInfo.timeline;
    if (clearPlaylist) {
      timeline = playlist.clear(/* shuffleOrder= */ null);
      queue.setTimeline(timeline);
      for (PendingMessageInfo pendingMessageInfo : pendingMessages) {
        pendingMessageInfo.message.markAsProcessed(/* isDelivered= */ false);
      }
      pendingMessages.clear();
      nextPendingMessageIndex = 0;
    }
    MediaPeriodId mediaPeriodId = playbackInfo.periodId;
    long contentPositionUs = playbackInfo.contentPositionUs;
    if (resetPosition) {
      mediaPeriodId =
          timeline.isEmpty()
              ? playbackInfo.getDummyPeriodForEmptyTimeline()
              : getDummyFirstMediaPeriodForAds();
      contentPositionUs = C.TIME_UNSET;
    }
    // Set the start position to TIME_UNSET so that a subsequent seek to 0 isn't ignored.
    long startPositionUs = resetPosition ? C.TIME_UNSET : playbackInfo.positionUs;
    playbackInfo =
        new PlaybackInfo(
            timeline,
            mediaPeriodId,
            startPositionUs,
            contentPositionUs,
            playbackInfo.playbackState,
            resetError ? null : playbackInfo.playbackError,
            /* isLoading= */ false,
            clearPlaylist ? TrackGroupArray.EMPTY : playbackInfo.trackGroups,
            clearPlaylist ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult,
            mediaPeriodId,
            startPositionUs,
            /* totalBufferedDurationUs= */ 0,
            startPositionUs);
    if (releasePlaylist) {
      playlist.release();
    }
  }

  private MediaPeriodId getDummyFirstMediaPeriodForAds() {
    MediaPeriodId dummyFirstMediaPeriodId =
        playbackInfo.getDummyFirstMediaPeriodId(shuffleModeEnabled, window, period);
    if (!playbackInfo.timeline.isEmpty()) {
      // add ad metadata if any and propagate the window sequence number to new period id.
      dummyFirstMediaPeriodId =
          queue.resolveMediaPeriodIdForAds(dummyFirstMediaPeriodId.periodUid, /* positionUs= */ 0);
    }
    return dummyFirstMediaPeriodId;
  }

  private void sendMessageInternal(PlayerMessage message) throws ExoPlaybackException {
    if (message.getPositionMs() == C.TIME_UNSET) {
      // If no delivery time is specified, trigger immediate message delivery.
      sendMessageToTarget(message);
    } else if (playbackInfo.timeline.isEmpty()) {
      // Still waiting for initial timeline to resolve position.
      pendingMessages.add(new PendingMessageInfo(message));
    } else {
      PendingMessageInfo pendingMessageInfo = new PendingMessageInfo(message);
      if (resolvePendingMessagePosition(pendingMessageInfo)) {
        pendingMessages.add(pendingMessageInfo);
        // Ensure new message is inserted according to playback order.
        Collections.sort(pendingMessages);
      } else {
        message.markAsProcessed(/* isDelivered= */ false);
      }
    }
  }

  private void sendMessageToTarget(PlayerMessage message) throws ExoPlaybackException {
    if (message.getHandler().getLooper() == handler.getLooper()) {
      deliverMessage(message);
      if (playbackInfo.playbackState == Player.STATE_READY
          || playbackInfo.playbackState == Player.STATE_BUFFERING) {
        // The message may have caused something to change that now requires us to do work.
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    } else {
      handler.obtainMessage(MSG_SEND_MESSAGE_TO_TARGET_THREAD, message).sendToTarget();
    }
  }

  private void sendMessageToTargetThread(final PlayerMessage message) {
    Handler handler = message.getHandler();
    if (!handler.getLooper().getThread().isAlive()) {
      Log.w("TAG", "Trying to send message on a dead thread.");
      message.markAsProcessed(/* isDelivered= */ false);
      return;
    }
    handler.post(
        () -> {
          try {
            deliverMessage(message);
          } catch (ExoPlaybackException e) {
            Log.e(TAG, "Unexpected error delivering message on external thread.", e);
            throw new RuntimeException(e);
          }
        });
  }

  private void deliverMessage(PlayerMessage message) throws ExoPlaybackException {
    if (message.isCanceled()) {
      return;
    }
    try {
      message.getTarget().handleMessage(message.getType(), message.getPayload());
    } finally {
      message.markAsProcessed(/* isDelivered= */ true);
    }
  }

  private void resolvePendingMessagePositions() {
    for (int i = pendingMessages.size() - 1; i >= 0; i--) {
      if (!resolvePendingMessagePosition(pendingMessages.get(i))) {
        // Unable to resolve a new position for the message. Remove it.
        pendingMessages.get(i).message.markAsProcessed(/* isDelivered= */ false);
        pendingMessages.remove(i);
      }
    }
    // Re-sort messages by playback order.
    Collections.sort(pendingMessages);
  }

  private boolean resolvePendingMessagePosition(PendingMessageInfo pendingMessageInfo) {
    if (pendingMessageInfo.resolvedPeriodUid == null) {
      // Position is still unresolved. Try to find window in current timeline.
      Pair<Object, Long> periodPosition =
          resolveSeekPosition(
              new SeekPosition(
                  pendingMessageInfo.message.getTimeline(),
                  pendingMessageInfo.message.getWindowIndex(),
                  C.msToUs(pendingMessageInfo.message.getPositionMs())),
              /* trySubsequentPeriods= */ false);
      if (periodPosition == null) {
        return false;
      }
      pendingMessageInfo.setResolvedPosition(
          playbackInfo.timeline.getIndexOfPeriod(periodPosition.first),
          periodPosition.second,
          periodPosition.first);
    } else {
      // Position has been resolved for a previous timeline. Try to find the updated period index.
      int index = playbackInfo.timeline.getIndexOfPeriod(pendingMessageInfo.resolvedPeriodUid);
      if (index == C.INDEX_UNSET) {
        return false;
      }
      pendingMessageInfo.resolvedPeriodIndex = index;
    }
    return true;
  }

  private void maybeTriggerPendingMessages(long oldPeriodPositionUs, long newPeriodPositionUs)
      throws ExoPlaybackException {
    if (pendingMessages.isEmpty() || playbackInfo.periodId.isAd()) {
      return;
    }
    // If this is the first call from the start position, include oldPeriodPositionUs in potential
    // trigger positions, but make sure we deliver it only once.
    if (playbackInfo.startPositionUs == oldPeriodPositionUs
        && deliverPendingMessageAtStartPositionRequired) {
      oldPeriodPositionUs--;
    }
    deliverPendingMessageAtStartPositionRequired = false;

    // Correct next index if necessary (e.g. after seeking, timeline changes, or new messages)
    int currentPeriodIndex =
        playbackInfo.timeline.getIndexOfPeriod(playbackInfo.periodId.periodUid);
    PendingMessageInfo previousInfo =
        nextPendingMessageIndex > 0 ? pendingMessages.get(nextPendingMessageIndex - 1) : null;
    while (previousInfo != null
        && (previousInfo.resolvedPeriodIndex > currentPeriodIndex
            || (previousInfo.resolvedPeriodIndex == currentPeriodIndex
                && previousInfo.resolvedPeriodTimeUs > oldPeriodPositionUs))) {
      nextPendingMessageIndex--;
      previousInfo =
          nextPendingMessageIndex > 0 ? pendingMessages.get(nextPendingMessageIndex - 1) : null;
    }
    PendingMessageInfo nextInfo =
        nextPendingMessageIndex < pendingMessages.size()
            ? pendingMessages.get(nextPendingMessageIndex)
            : null;
    while (nextInfo != null
        && nextInfo.resolvedPeriodUid != null
        && (nextInfo.resolvedPeriodIndex < currentPeriodIndex
            || (nextInfo.resolvedPeriodIndex == currentPeriodIndex
                && nextInfo.resolvedPeriodTimeUs <= oldPeriodPositionUs))) {
      nextPendingMessageIndex++;
      nextInfo =
          nextPendingMessageIndex < pendingMessages.size()
              ? pendingMessages.get(nextPendingMessageIndex)
              : null;
    }
    // Check if any message falls within the covered time span.
    while (nextInfo != null
        && nextInfo.resolvedPeriodUid != null
        && nextInfo.resolvedPeriodIndex == currentPeriodIndex
        && nextInfo.resolvedPeriodTimeUs > oldPeriodPositionUs
        && nextInfo.resolvedPeriodTimeUs <= newPeriodPositionUs) {
      try {
        sendMessageToTarget(nextInfo.message);
      } finally {
        if (nextInfo.message.getDeleteAfterDelivery() || nextInfo.message.isCanceled()) {
          pendingMessages.remove(nextPendingMessageIndex);
        } else {
          nextPendingMessageIndex++;
        }
      }
      nextInfo =
          nextPendingMessageIndex < pendingMessages.size()
              ? pendingMessages.get(nextPendingMessageIndex)
              : null;
    }
  }

  private void ensureStopped(Renderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == Renderer.STATE_STARTED) {
      renderer.stop();
    }
  }

  private void disableRenderer(Renderer renderer) throws ExoPlaybackException {
    mediaClock.onRendererDisabled(renderer);
    ensureStopped(renderer);
    renderer.disable();
  }

  private void reselectTracksInternal() throws ExoPlaybackException {
    float playbackSpeed = mediaClock.getPlaybackParameters().speed;
    // Reselect tracks on each period in turn, until the selection changes.
    MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    boolean selectionsChangedForReadPeriod = true;
    TrackSelectorResult newTrackSelectorResult;
    while (true) {
      if (periodHolder == null || !periodHolder.prepared) {
        // The reselection did not change any prepared periods.
        return;
      }
      newTrackSelectorResult = periodHolder.selectTracks(playbackSpeed, playbackInfo.timeline);
      if (!newTrackSelectorResult.isEquivalent(periodHolder.getTrackSelectorResult())) {
        // Selected tracks have changed for this period.
        break;
      }
      if (periodHolder == readingPeriodHolder) {
        // The track reselection didn't affect any period that has been read.
        selectionsChangedForReadPeriod = false;
      }
      periodHolder = periodHolder.getNext();
    }

    if (selectionsChangedForReadPeriod) {
      // Update streams and rebuffer for the new selection, recreating all streams if reading ahead.
      MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
      boolean recreateStreams = queue.removeAfter(playingPeriodHolder);

      boolean[] streamResetFlags = new boolean[renderers.length];
      long periodPositionUs =
          playingPeriodHolder.applyTrackSelection(
              newTrackSelectorResult, playbackInfo.positionUs, recreateStreams, streamResetFlags);
      if (playbackInfo.playbackState != Player.STATE_ENDED
          && periodPositionUs != playbackInfo.positionUs) {
        playbackInfo =
            copyWithNewPosition(
                playbackInfo.periodId, periodPositionUs, playbackInfo.contentPositionUs);
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
        resetRendererPosition(periodPositionUs);
      }

      int enabledRendererCount = 0;
      boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
        SampleStream sampleStream = playingPeriodHolder.sampleStreams[i];
        if (sampleStream != null) {
          enabledRendererCount++;
        }
        if (rendererWasEnabledFlags[i]) {
          if (sampleStream != renderer.getStream()) {
            // We need to disable the renderer.
            disableRenderer(renderer);
          } else if (streamResetFlags[i]) {
            // The renderer will continue to consume from its current stream, but needs to be reset.
            renderer.resetPosition(rendererPositionUs);
          }
        }
      }
      playbackInfo =
          playbackInfo.copyWithTrackInfo(
              playingPeriodHolder.getTrackGroups(), playingPeriodHolder.getTrackSelectorResult());
      enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
    } else {
      // Release and re-prepare/buffer periods after the one whose selection changed.
      queue.removeAfter(periodHolder);
      if (periodHolder.prepared) {
        long loadingPeriodPositionUs =
            Math.max(
                periodHolder.info.startPositionUs, periodHolder.toPeriodTime(rendererPositionUs));
        periodHolder.applyTrackSelection(newTrackSelectorResult, loadingPeriodPositionUs, false);
      }
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ true);
    if (playbackInfo.playbackState != Player.STATE_ENDED) {
      maybeContinueLoading();
      updatePlaybackPositions();
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
  }

  private void updateTrackSelectionPlaybackSpeed(float playbackSpeed) {
    MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
    while (periodHolder != null) {
      TrackSelection[] trackSelections = periodHolder.getTrackSelectorResult().selections.getAll();
      for (TrackSelection trackSelection : trackSelections) {
        if (trackSelection != null) {
          trackSelection.onPlaybackSpeed(playbackSpeed);
        }
      }
      periodHolder = periodHolder.getNext();
    }
  }

  private void notifyTrackSelectionDiscontinuity() {
    MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
    while (periodHolder != null) {
      TrackSelection[] trackSelections = periodHolder.getTrackSelectorResult().selections.getAll();
      for (TrackSelection trackSelection : trackSelections) {
        if (trackSelection != null) {
          trackSelection.onDiscontinuity();
        }
      }
      periodHolder = periodHolder.getNext();
    }
  }

  private boolean shouldTransitionToReadyState(boolean renderersReadyOrEnded) {
    if (enabledRenderers.length == 0) {
      // If there are no enabled renderers, determine whether we're ready based on the timeline.
      return isTimelineReady();
    }
    if (!renderersReadyOrEnded) {
      return false;
    }
    if (!playbackInfo.isLoading) {
      // Renderers are ready and we're not loading. Transition to ready, since the alternative is
      // getting stuck waiting for additional media that's not being loaded.
      return true;
    }
    // Renderers are ready and we're loading. Ask the LoadControl whether to transition.
    MediaPeriodHolder loadingHolder = queue.getLoadingPeriod();
    boolean bufferedToEnd = loadingHolder.isFullyBuffered() && loadingHolder.info.isFinal;
    return bufferedToEnd
        || loadControl.shouldStartPlayback(
            getTotalBufferedDurationUs(), mediaClock.getPlaybackParameters().speed, rebuffering);
  }

  private boolean isTimelineReady() {
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
    return playingPeriodHolder.prepared
        && (playingPeriodDurationUs == C.TIME_UNSET
            || playbackInfo.positionUs < playingPeriodDurationUs);
  }

  private void maybeThrowSourceInfoRefreshError() throws IOException {
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    if (loadingPeriodHolder != null) {
      // Defer throwing until we read all available media periods.
      for (Renderer renderer : enabledRenderers) {
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }
    }
    playlist.maybeThrowSourceInfoRefreshError();
  }

  private void handlePlaylistInfoRefreshed(Timeline timeline) throws ExoPlaybackException {
    Timeline oldTimeline = playbackInfo.timeline;
    queue.setTimeline(timeline);
    playbackInfo = playbackInfo.copyWithTimeline(timeline);
    resolvePendingMessagePositions();
    if (timeline.isEmpty()) {
      @Nullable SeekPosition pendingInitialSeekPosition = this.pendingInitialSeekPosition;
      handleEndOfPlaylist();
      // Retain seek position if any.
      this.pendingInitialSeekPosition = pendingInitialSeekPosition;
      return;
    }
    MediaPeriodId oldPeriodId = playbackInfo.periodId;
    Object newPeriodUid = oldPeriodId.periodUid;
    long oldContentPositionUs =
        oldPeriodId.isAd() ? playbackInfo.contentPositionUs : playbackInfo.positionUs;
    long newContentPositionUs = oldContentPositionUs;
    boolean forceBufferingState = false;
    if (pendingInitialSeekPosition != null) {
      // Resolve initial seek position.
      @Nullable
      Pair<Object, Long> periodPosition =
          resolveSeekPosition(pendingInitialSeekPosition, /* trySubsequentPeriods= */ true);
      if (periodPosition == null) {
        // The initial seek in the empty old timeline is invalid in the new timeline.
        handleEndOfPlaylist();
        // Use the period resulting from the reset.
        newPeriodUid = playbackInfo.periodId.periodUid;
        newContentPositionUs = C.TIME_UNSET;
      } else {
        // The pending seek has been resolved successfully in the new timeline.
        newPeriodUid = periodPosition.first;
        newContentPositionUs =
            pendingInitialSeekPosition.windowPositionUs == C.TIME_UNSET
                ? C.TIME_UNSET
                : periodPosition.second;
        forceBufferingState = playbackInfo.playbackState == Player.STATE_ENDED;
      }
      pendingInitialSeekPosition = null;
    } else if (oldTimeline.isEmpty()) {
      // Resolve to default position if the old timeline is empty and no seek is requested above.
      Pair<Object, Long> defaultPosition =
          getPeriodPosition(
              timeline,
              timeline.getFirstWindowIndex(shuffleModeEnabled),
              /* windowPositionUs= */ C.TIME_UNSET);
      newPeriodUid = defaultPosition.first;
      newContentPositionUs = C.TIME_UNSET;
    } else if (timeline.getIndexOfPeriod(newPeriodUid) == C.INDEX_UNSET) {
      // The current period isn't in the new timeline. Attempt to resolve a subsequent period whose
      // window we can restart from.
      @Nullable
      Object subsequentPeriodUid =
          resolveSubsequentPeriod(
              window, period, repeatMode, shuffleModeEnabled, newPeriodUid, oldTimeline, timeline);
      if (subsequentPeriodUid == null) {
        // We failed to resolve a suitable restart position but the timeline is not empty.
        handleEndOfPlaylist();
        // Use period and position resulting from the reset.
        newPeriodUid = playbackInfo.periodId.periodUid;
        newContentPositionUs = C.TIME_UNSET;
      } else {
        // We resolved a subsequent period. Start at the default position in the corresponding
        // window.
        Pair<Object, Long> defaultPosition =
            getPeriodPosition(
                timeline,
                timeline.getPeriodByUid(subsequentPeriodUid, period).windowIndex,
                C.TIME_UNSET);
        newPeriodUid = defaultPosition.first;
        newContentPositionUs = C.TIME_UNSET;
      }
    }

    // Ensure ad insertion metadata is up to date.
    long contentPositionForAdResolution = newContentPositionUs;
    if (contentPositionForAdResolution == C.TIME_UNSET) {
      contentPositionForAdResolution =
          timeline.getWindow(timeline.getPeriodByUid(newPeriodUid, period).windowIndex, window)
              .defaultPositionUs;
    }
    MediaPeriodId periodIdWithAds =
        queue.resolveMediaPeriodIdForAds(newPeriodUid, contentPositionForAdResolution);
    boolean oldAndNewPeriodIdAreSame =
        oldPeriodId.periodUid.equals(newPeriodUid)
            && !oldPeriodId.isAd()
            && !periodIdWithAds.isAd();
    // Drop update if we keep playing the same content (MediaPeriod.periodUid are identical) and
    // only MediaPeriodId.nextAdGroupIndex may have changed. This postpones a potential
    // discontinuity until we reach the former next ad group position.
    MediaPeriodId newPeriodId = oldAndNewPeriodIdAreSame ? oldPeriodId : periodIdWithAds;

    if (oldPeriodId.equals(newPeriodId) && oldContentPositionUs == newContentPositionUs) {
      // We can keep the current playing period. Update the rest of the queued periods.
      if (!queue.updateQueuedPeriods(rendererPositionUs, getMaxRendererReadPositionUs())) {
        seekToCurrentPosition(/* sendDiscontinuity= */ false);
      }
    } else {
      // Something changed. Seek to new start position.
      @Nullable MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
      if (periodHolder != null) {
        // Update the new playing media period info if it already exists.
        while (periodHolder.getNext() != null) {
          periodHolder = periodHolder.getNext();
          if (periodHolder.info.id.equals(newPeriodId)) {
            periodHolder.info = queue.getUpdatedMediaPeriodInfo(periodHolder.info);
          }
        }
      }
      long newPositionUs = newPeriodId.isAd() ? 0 : newContentPositionUs;
      if (!newPeriodId.isAd() && newContentPositionUs == C.TIME_UNSET) {
        // Get the default position for the first new period that is not an ad.
        int windowIndex = timeline.getPeriodByUid(newPeriodId.periodUid, period).windowIndex;
        newContentPositionUs = timeline.getWindow(windowIndex, window).getDefaultPositionUs();
        newPositionUs = newContentPositionUs;
      }
      // Actually do the seek.
      long seekedToPositionUs =
          seekToPeriodPosition(newPeriodId, newPositionUs, forceBufferingState);
      playbackInfo = copyWithNewPosition(newPeriodId, seekedToPositionUs, newContentPositionUs);
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private long getMaxRendererReadPositionUs() {
    MediaPeriodHolder readingHolder = queue.getReadingPeriod();
    if (readingHolder == null) {
      return 0;
    }
    long maxReadPositionUs = readingHolder.getRendererOffset();
    if (!readingHolder.prepared) {
      return maxReadPositionUs;
    }
    for (int i = 0; i < renderers.length; i++) {
      if (renderers[i].getState() == Renderer.STATE_DISABLED
          || renderers[i].getStream() != readingHolder.sampleStreams[i]) {
        // Ignore disabled renderers and renderers with sample streams from previous periods.
        continue;
      }
      long readingPositionUs = renderers[i].getReadingPositionUs();
      if (readingPositionUs == C.TIME_END_OF_SOURCE) {
        return C.TIME_END_OF_SOURCE;
      } else {
        maxReadPositionUs = Math.max(readingPositionUs, maxReadPositionUs);
      }
    }
    return maxReadPositionUs;
  }

  private void handleEndOfPlaylist() {
    if (playbackInfo.playbackState != Player.STATE_IDLE) {
      setState(Player.STATE_ENDED);
    }
    // Reset, but retain the playlist so that it can still resume after a seek or be modified.
    resetInternal(
        /* resetRenderers= */ false,
        /* resetPosition= */ true,
        /* releasePlaylist= */ false,
        /* clearPlaylist= */ false,
        /* resetError= */ true);
  }

  /**
   * Converts a {@link SeekPosition} into the corresponding (periodUid, periodPositionUs) for the
   * internal timeline.
   *
   * @param seekPosition The position to resolve.
   * @param trySubsequentPeriods Whether the position can be resolved to a subsequent matching
   *     period if the original period is no longer available.
   * @return The resolved position, or null if resolution was not successful.
   * @throws IllegalSeekPositionException If the window index of the seek position is outside the
   *     bounds of the timeline.
   */
  @Nullable
  private Pair<Object, Long> resolveSeekPosition(
      SeekPosition seekPosition, boolean trySubsequentPeriods) {
    Timeline timeline = playbackInfo.timeline;
    Timeline seekTimeline = seekPosition.timeline;
    if (timeline.isEmpty()) {
      // We don't have a valid timeline yet, so we can't resolve the position.
      return null;
    }
    if (seekTimeline.isEmpty()) {
      // The application performed a blind seek with an empty timeline (most likely based on
      // knowledge of what the future timeline will be). Use the internal timeline.
      seekTimeline = timeline;
    }
    // Map the SeekPosition to a position in the corresponding timeline.
    Pair<Object, Long> periodPosition;
    try {
      periodPosition =
          seekTimeline.getPeriodPosition(
              window, period, seekPosition.windowIndex, seekPosition.windowPositionUs);
    } catch (IndexOutOfBoundsException e) {
      // The window index of the seek position was outside the bounds of the timeline.
      return null;
    }
    if (timeline == seekTimeline) {
      // Our internal timeline is the seek timeline, so the mapped position is correct.
      return periodPosition;
    }
    // Attempt to find the mapped period in the internal timeline.
    int periodIndex = timeline.getIndexOfPeriod(periodPosition.first);
    if (periodIndex != C.INDEX_UNSET) {
      // We successfully located the period in the internal timeline.
      return periodPosition;
    }
    if (trySubsequentPeriods) {
      // Try and find a subsequent period from the seek timeline in the internal timeline.
      @Nullable
      Object periodUid =
          resolveSubsequentPeriod(
              window,
              period,
              repeatMode,
              shuffleModeEnabled,
              periodPosition.first,
              seekTimeline,
              timeline);
      if (periodUid != null) {
        // We found one. Use the default position of the corresponding window.
        return getPeriodPosition(
            timeline, timeline.getPeriodByUid(periodUid, period).windowIndex, C.TIME_UNSET);
      }
    }
    // We didn't find one. Give up.
    return null;
  }

  /**
   * Calls {@link Timeline#getPeriodPosition(Timeline.Window, Timeline.Period, int, long)} using the
   * current timeline.
   */
  private Pair<Object, Long> getPeriodPosition(
      Timeline timeline, int windowIndex, long windowPositionUs) {
    return timeline.getPeriodPosition(window, period, windowIndex, windowPositionUs);
  }

  private void updatePeriods() throws ExoPlaybackException, IOException {
    if (playbackInfo.timeline.isEmpty() || !playlist.isPrepared()) {
      // We're waiting to get information about periods.
      playlist.maybeThrowSourceInfoRefreshError();
      return;
    }
    maybeUpdateLoadingPeriod();
    maybeUpdateReadingPeriod();
    maybeUpdatePlayingPeriod();
  }

  private void maybeUpdateLoadingPeriod() throws ExoPlaybackException, IOException {
    queue.reevaluateBuffer(rendererPositionUs);
    if (queue.shouldLoadNextMediaPeriod()) {
      MediaPeriodInfo info = queue.getNextMediaPeriodInfo(rendererPositionUs, playbackInfo);
      if (info == null) {
        maybeThrowSourceInfoRefreshError();
      } else {
        MediaPeriodHolder mediaPeriodHolder =
            queue.enqueueNextMediaPeriodHolder(
                rendererCapabilities,
                trackSelector,
                loadControl.getAllocator(),
                playlist,
                info,
                emptyTrackSelectorResult);
        mediaPeriodHolder.mediaPeriod.prepare(this, info.startPositionUs);
        if (queue.getPlayingPeriod() == mediaPeriodHolder) {
          resetRendererPosition(mediaPeriodHolder.getStartPositionRendererTime());
        }
        handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
      }
    }
    if (shouldContinueLoading) {
      shouldContinueLoading = isLoadingPossible();
      updateIsLoading();
    } else {
      maybeContinueLoading();
    }
  }

  private void maybeUpdateReadingPeriod() throws ExoPlaybackException {
    @Nullable MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    if (readingPeriodHolder == null) {
      return;
    }

    if (readingPeriodHolder.getNext() == null) {
      // We don't have a successor to advance the reading period to.
      if (readingPeriodHolder.info.isFinal) {
        for (int i = 0; i < renderers.length; i++) {
          Renderer renderer = renderers[i];
          SampleStream sampleStream = readingPeriodHolder.sampleStreams[i];
          // Defer setting the stream as final until the renderer has actually consumed the whole
          // stream in case of playlist changes that cause the stream to be no longer final.
          if (sampleStream != null
              && renderer.getStream() == sampleStream
              && renderer.hasReadStreamToEnd()) {
            renderer.setCurrentStreamFinal();
          }
        }
      }
      return;
    }

    if (!hasReadingPeriodFinishedReading()) {
      return;
    }

    if (!readingPeriodHolder.getNext().prepared) {
      // The successor is not prepared yet.
      return;
    }

    TrackSelectorResult oldTrackSelectorResult = readingPeriodHolder.getTrackSelectorResult();
    readingPeriodHolder = queue.advanceReadingPeriod();
    TrackSelectorResult newTrackSelectorResult = readingPeriodHolder.getTrackSelectorResult();

    if (readingPeriodHolder.mediaPeriod.readDiscontinuity() != C.TIME_UNSET) {
      // The new period starts with a discontinuity, so the renderers will play out all data, then
      // be disabled and re-enabled when they start playing the next period.
      setAllRendererStreamsFinal();
      return;
    }
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      boolean rendererWasEnabled = oldTrackSelectorResult.isRendererEnabled(i);
      if (rendererWasEnabled && !renderer.isCurrentStreamFinal()) {
        // The renderer is enabled and its stream is not final, so we still have a chance to replace
        // the sample streams.
        TrackSelection newSelection = newTrackSelectorResult.selections.get(i);
        boolean newRendererEnabled = newTrackSelectorResult.isRendererEnabled(i);
        boolean isNoSampleRenderer = rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE;
        RendererConfiguration oldConfig = oldTrackSelectorResult.rendererConfigurations[i];
        RendererConfiguration newConfig = newTrackSelectorResult.rendererConfigurations[i];
        if (newRendererEnabled && newConfig.equals(oldConfig) && !isNoSampleRenderer) {
          // Replace the renderer's SampleStream so the transition to playing the next period can
          // be seamless.
          // This should be avoided for no-sample renderer, because skipping ahead for such
          // renderer doesn't have any benefit (the renderer does not consume the sample stream),
          // and it will change the provided rendererOffsetUs while the renderer is still
          // rendering from the playing media period.
          Format[] formats = getFormats(newSelection);
          renderer.replaceStream(
              formats,
              readingPeriodHolder.sampleStreams[i],
              readingPeriodHolder.getRendererOffset());
        } else {
          // The renderer will be disabled when transitioning to playing the next period, because
          // there's no new selection, or because a configuration change is required, or because
          // it's a no-sample renderer for which rendererOffsetUs should be updated only when
          // starting to play the next period. Mark the SampleStream as final to play out any
          // remaining data.
          renderer.setCurrentStreamFinal();
        }
      }
    }
  }

  private void maybeUpdatePlayingPeriod() throws ExoPlaybackException {
    boolean advancedPlayingPeriod = false;
    while (shouldAdvancePlayingPeriod()) {
      if (advancedPlayingPeriod) {
        // If we advance more than one period at a time, notify listeners after each update.
        maybeNotifyPlaybackInfoChanged();
      }
      MediaPeriodHolder oldPlayingPeriodHolder = queue.getPlayingPeriod();
      if (oldPlayingPeriodHolder == queue.getReadingPeriod()) {
        // The reading period hasn't advanced yet, so we can't seamlessly replace the SampleStreams
        // anymore and need to re-enable the renderers. Set all current streams final to do that.
        setAllRendererStreamsFinal();
      }
      MediaPeriodHolder newPlayingPeriodHolder = queue.advancePlayingPeriod();
      updatePlayingPeriodRenderers(oldPlayingPeriodHolder);
      playbackInfo =
          copyWithNewPosition(
              newPlayingPeriodHolder.info.id,
              newPlayingPeriodHolder.info.startPositionUs,
              newPlayingPeriodHolder.info.contentPositionUs);
      int discontinuityReason =
          oldPlayingPeriodHolder.info.isLastInTimelinePeriod
              ? Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
              : Player.DISCONTINUITY_REASON_AD_INSERTION;
      playbackInfoUpdate.setPositionDiscontinuity(discontinuityReason);
      updatePlaybackPositions();
      advancedPlayingPeriod = true;
    }
  }

  private boolean shouldAdvancePlayingPeriod() {
    if (!playWhenReady) {
      return false;
    }
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (playingPeriodHolder == null) {
      return false;
    }
    MediaPeriodHolder nextPlayingPeriodHolder = playingPeriodHolder.getNext();
    if (nextPlayingPeriodHolder == null) {
      return false;
    }
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    if (playingPeriodHolder == readingPeriodHolder && !hasReadingPeriodFinishedReading()) {
      return false;
    }
    return rendererPositionUs >= nextPlayingPeriodHolder.getStartPositionRendererTime();
  }

  private boolean hasReadingPeriodFinishedReading() {
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    if (!readingPeriodHolder.prepared) {
      return false;
    }
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      SampleStream sampleStream = readingPeriodHolder.sampleStreams[i];
      if (renderer.getStream() != sampleStream
          || (sampleStream != null && !renderer.hasReadStreamToEnd())) {
        // The current reading period is still being read by at least one renderer.
        return false;
      }
    }
    return true;
  }

  private void setAllRendererStreamsFinal() {
    for (Renderer renderer : renderers) {
      if (renderer.getStream() != null) {
        renderer.setCurrentStreamFinal();
      }
    }
  }

  private void handlePeriodPrepared(MediaPeriod mediaPeriod) throws ExoPlaybackException {
    if (!queue.isLoading(mediaPeriod)) {
      // Stale event.
      return;
    }
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    loadingPeriodHolder.handlePrepared(
        mediaClock.getPlaybackParameters().speed, playbackInfo.timeline);
    updateLoadControlTrackSelection(
        loadingPeriodHolder.getTrackGroups(), loadingPeriodHolder.getTrackSelectorResult());
    if (loadingPeriodHolder == queue.getPlayingPeriod()) {
      // This is the first prepared period, so update the position and the renderers.
      resetRendererPosition(loadingPeriodHolder.info.startPositionUs);
      updatePlayingPeriodRenderers(/* oldPlayingPeriodHolder= */ null);
    }
    maybeContinueLoading();
  }

  private void handleContinueLoadingRequested(MediaPeriod mediaPeriod) {
    if (!queue.isLoading(mediaPeriod)) {
      // Stale event.
      return;
    }
    queue.reevaluateBuffer(rendererPositionUs);
    maybeContinueLoading();
  }

  private void handlePlaybackParameters(
      PlaybackParameters playbackParameters, boolean acknowledgeCommand)
      throws ExoPlaybackException {
    eventHandler
        .obtainMessage(
            MSG_PLAYBACK_PARAMETERS_CHANGED, acknowledgeCommand ? 1 : 0, 0, playbackParameters)
        .sendToTarget();
    updateTrackSelectionPlaybackSpeed(playbackParameters.speed);
    for (Renderer renderer : renderers) {
      if (renderer != null) {
        renderer.setOperatingRate(playbackParameters.speed);
      }
    }
  }

  private void maybeContinueLoading() {
    shouldContinueLoading = shouldContinueLoading();
    if (shouldContinueLoading) {
      queue.getLoadingPeriod().continueLoading(rendererPositionUs);
    }
    updateIsLoading();
  }

  private boolean shouldContinueLoading() {
    if (!isLoadingPossible()) {
      return false;
    }
    long bufferedDurationUs =
        getTotalBufferedDurationUs(queue.getLoadingPeriod().getNextLoadPositionUs());
    if (bufferedDurationUs < 500_000) {
      // Prevent loading from getting stuck even if LoadControl.shouldContinueLoading returns false
      // when the buffer is empty or almost empty. We can't compare against 0 to account for small
      // differences between the renderer position and buffered position in the media at the point
      // where playback gets stuck.
      return true;
    }
    float playbackSpeed = mediaClock.getPlaybackParameters().speed;
    return loadControl.shouldContinueLoading(bufferedDurationUs, playbackSpeed);
  }

  private boolean isLoadingPossible() {
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    if (loadingPeriodHolder == null) {
      return false;
    }
    long nextLoadPositionUs = loadingPeriodHolder.getNextLoadPositionUs();
    if (nextLoadPositionUs == C.TIME_END_OF_SOURCE) {
      return false;
    }
    return true;
  }

  private void updateIsLoading() {
    MediaPeriodHolder loadingPeriod = queue.getLoadingPeriod();
    boolean isLoading =
        shouldContinueLoading || (loadingPeriod != null && loadingPeriod.mediaPeriod.isLoading());
    if (isLoading != playbackInfo.isLoading) {
      playbackInfo = playbackInfo.copyWithIsLoading(isLoading);
    }
  }

  private PlaybackInfo copyWithNewPosition(
      MediaPeriodId mediaPeriodId, long positionUs, long contentPositionUs) {
    deliverPendingMessageAtStartPositionRequired = true;
    return playbackInfo.copyWithNewPosition(
        mediaPeriodId, positionUs, contentPositionUs, getTotalBufferedDurationUs());
  }

  @SuppressWarnings("ParameterNotNullable")
  private void updatePlayingPeriodRenderers(@Nullable MediaPeriodHolder oldPlayingPeriodHolder)
      throws ExoPlaybackException {
    MediaPeriodHolder newPlayingPeriodHolder = queue.getPlayingPeriod();
    if (newPlayingPeriodHolder == null || oldPlayingPeriodHolder == newPlayingPeriodHolder) {
      return;
    }
    int enabledRendererCount = 0;
    boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
      if (newPlayingPeriodHolder.getTrackSelectorResult().isRendererEnabled(i)) {
        enabledRendererCount++;
      }
      if (rendererWasEnabledFlags[i]
          && (!newPlayingPeriodHolder.getTrackSelectorResult().isRendererEnabled(i)
              || (renderer.isCurrentStreamFinal()
                  && renderer.getStream() == oldPlayingPeriodHolder.sampleStreams[i]))) {
        // The renderer should be disabled before playing the next period, either because it's not
        // needed to play the next period, or because we need to re-enable it as its current stream
        // is final and it's not reading ahead.
        disableRenderer(renderer);
      }
    }
    playbackInfo =
        playbackInfo.copyWithTrackInfo(
            newPlayingPeriodHolder.getTrackGroups(),
            newPlayingPeriodHolder.getTrackSelectorResult());
    enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
  }

  private void enableRenderers(boolean[] rendererWasEnabledFlags, int totalEnabledRendererCount)
      throws ExoPlaybackException {
    enabledRenderers = new Renderer[totalEnabledRendererCount];
    int enabledRendererCount = 0;
    TrackSelectorResult trackSelectorResult = queue.getPlayingPeriod().getTrackSelectorResult();
    // Reset all disabled renderers before enabling any new ones. This makes sure resources released
    // by the disabled renderers will be available to renderers that are being enabled.
    for (int i = 0; i < renderers.length; i++) {
      if (!trackSelectorResult.isRendererEnabled(i)) {
        renderers[i].reset();
      }
    }
    // Enable the renderers.
    for (int i = 0; i < renderers.length; i++) {
      if (trackSelectorResult.isRendererEnabled(i)) {
        enableRenderer(i, rendererWasEnabledFlags[i], enabledRendererCount++);
      }
    }
  }

  private void enableRenderer(
      int rendererIndex, boolean wasRendererEnabled, int enabledRendererIndex)
      throws ExoPlaybackException {
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    Renderer renderer = renderers[rendererIndex];
    enabledRenderers[enabledRendererIndex] = renderer;
    if (renderer.getState() == Renderer.STATE_DISABLED) {
      TrackSelectorResult trackSelectorResult = playingPeriodHolder.getTrackSelectorResult();
      RendererConfiguration rendererConfiguration =
          trackSelectorResult.rendererConfigurations[rendererIndex];
      TrackSelection newSelection = trackSelectorResult.selections.get(rendererIndex);
      Format[] formats = getFormats(newSelection);
      // The renderer needs enabling with its new track selection.
      boolean playing = playWhenReady && playbackInfo.playbackState == Player.STATE_READY;
      // Consider as joining only if the renderer was previously disabled.
      boolean joining = !wasRendererEnabled && playing;
      // Enable the renderer.
      renderer.enable(
          rendererConfiguration,
          formats,
          playingPeriodHolder.sampleStreams[rendererIndex],
          rendererPositionUs,
          joining,
          playingPeriodHolder.getRendererOffset());
      mediaClock.onRendererEnabled(renderer);
      // Start the renderer if playing.
      if (playing) {
        renderer.start();
      }
    }
  }

  private void handleLoadingMediaPeriodChanged(boolean loadingTrackSelectionChanged) {
    MediaPeriodHolder loadingMediaPeriodHolder = queue.getLoadingPeriod();
    MediaPeriodId loadingMediaPeriodId =
        loadingMediaPeriodHolder == null ? playbackInfo.periodId : loadingMediaPeriodHolder.info.id;
    boolean loadingMediaPeriodChanged =
        !playbackInfo.loadingMediaPeriodId.equals(loadingMediaPeriodId);
    if (loadingMediaPeriodChanged) {
      playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(loadingMediaPeriodId);
    }
    playbackInfo.bufferedPositionUs =
        loadingMediaPeriodHolder == null
            ? playbackInfo.positionUs
            : loadingMediaPeriodHolder.getBufferedPositionUs();
    playbackInfo.totalBufferedDurationUs = getTotalBufferedDurationUs();
    if ((loadingMediaPeriodChanged || loadingTrackSelectionChanged)
        && loadingMediaPeriodHolder != null
        && loadingMediaPeriodHolder.prepared) {
      updateLoadControlTrackSelection(
          loadingMediaPeriodHolder.getTrackGroups(),
          loadingMediaPeriodHolder.getTrackSelectorResult());
    }
  }

  private long getTotalBufferedDurationUs() {
    return getTotalBufferedDurationUs(playbackInfo.bufferedPositionUs);
  }

  private long getTotalBufferedDurationUs(long bufferedPositionInLoadingPeriodUs) {
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    if (loadingPeriodHolder == null) {
      return 0;
    }
    long totalBufferedDurationUs =
        bufferedPositionInLoadingPeriodUs - loadingPeriodHolder.toPeriodTime(rendererPositionUs);
    return Math.max(0, totalBufferedDurationUs);
  }

  private void updateLoadControlTrackSelection(
      TrackGroupArray trackGroups, TrackSelectorResult trackSelectorResult) {
    loadControl.onTracksSelected(renderers, trackGroups, trackSelectorResult.selections);
  }

  private void sendPlaybackParametersChangedInternal(
      PlaybackParameters playbackParameters, boolean acknowledgeCommand) {
    handler
        .obtainMessage(
            MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL,
            acknowledgeCommand ? 1 : 0,
            0,
            playbackParameters)
        .sendToTarget();
  }

  /**
   * Given a period index into an old timeline, finds the first subsequent period that also exists
   * in a new timeline. The uid of this period in the new timeline is returned.
   *
   * @param window A {@link Timeline.Window} to be used internally.
   * @param period A {@link Timeline.Period} to be used internally.
   * @param repeatMode The repeat mode to use.
   * @param shuffleModeEnabled Whether the shuffle mode is enabled.
   * @param oldPeriodUid The index of the period in the old timeline.
   * @param oldTimeline The old timeline.
   * @param newTimeline The new timeline.
   * @return The uid in the new timeline of the first subsequent period, or null if no such period
   *     was found.
   */
  /* package */ static @Nullable Object resolveSubsequentPeriod(
      Timeline.Window window,
      Timeline.Period period,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Object oldPeriodUid,
      Timeline oldTimeline,
      Timeline newTimeline) {
    int oldPeriodIndex = oldTimeline.getIndexOfPeriod(oldPeriodUid);
    int newPeriodIndex = C.INDEX_UNSET;
    int maxIterations = oldTimeline.getPeriodCount();
    for (int i = 0; i < maxIterations && newPeriodIndex == C.INDEX_UNSET; i++) {
      oldPeriodIndex =
          oldTimeline.getNextPeriodIndex(
              oldPeriodIndex, period, window, repeatMode, shuffleModeEnabled);
      if (oldPeriodIndex == C.INDEX_UNSET) {
        // We've reached the end of the old timeline.
        break;
      }
      newPeriodIndex = newTimeline.getIndexOfPeriod(oldTimeline.getUidOfPeriod(oldPeriodIndex));
    }
    return newPeriodIndex == C.INDEX_UNSET ? null : newTimeline.getUidOfPeriod(newPeriodIndex);
  }

  private static Format[] getFormats(TrackSelection newSelection) {
    // Build an array of formats contained by the selection.
    int length = newSelection != null ? newSelection.length() : 0;
    Format[] formats = new Format[length];
    for (int i = 0; i < length; i++) {
      formats[i] = newSelection.getFormat(i);
    }
    return formats;
  }

  private static final class SeekPosition {

    public final Timeline timeline;
    public final int windowIndex;
    public final long windowPositionUs;

    public SeekPosition(Timeline timeline, int windowIndex, long windowPositionUs) {
      this.timeline = timeline;
      this.windowIndex = windowIndex;
      this.windowPositionUs = windowPositionUs;
    }
  }

  private static final class PendingMessageInfo implements Comparable<PendingMessageInfo> {

    public final PlayerMessage message;

    public int resolvedPeriodIndex;
    public long resolvedPeriodTimeUs;
    @Nullable public Object resolvedPeriodUid;

    public PendingMessageInfo(PlayerMessage message) {
      this.message = message;
    }

    public void setResolvedPosition(int periodIndex, long periodTimeUs, Object periodUid) {
      resolvedPeriodIndex = periodIndex;
      resolvedPeriodTimeUs = periodTimeUs;
      resolvedPeriodUid = periodUid;
    }

    @Override
    public int compareTo(PendingMessageInfo other) {
      if ((resolvedPeriodUid == null) != (other.resolvedPeriodUid == null)) {
        // PendingMessageInfos with a resolved period position are always smaller.
        return resolvedPeriodUid != null ? -1 : 1;
      }
      if (resolvedPeriodUid == null) {
        // Don't sort message with unresolved positions.
        return 0;
      }
      // Sort resolved media times by period index and then by period position.
      int comparePeriodIndex = resolvedPeriodIndex - other.resolvedPeriodIndex;
      if (comparePeriodIndex != 0) {
        return comparePeriodIndex;
      }
      return Util.compareLong(resolvedPeriodTimeUs, other.resolvedPeriodTimeUs);
    }
  }

  private static final class PlaylistUpdateMessage {

    private final List<Playlist.MediaSourceHolder> mediaSourceHolders;
    private final ShuffleOrder shuffleOrder;
    private final int windowIndex;
    private final long positionUs;

    private PlaylistUpdateMessage(
        List<Playlist.MediaSourceHolder> mediaSourceHolders,
        ShuffleOrder shuffleOrder,
        int windowIndex,
        long positionUs) {
      this.mediaSourceHolders = mediaSourceHolders;
      this.shuffleOrder = shuffleOrder;
      this.windowIndex = windowIndex;
      this.positionUs = positionUs;
    }
  }

  private static class MoveMediaItemsMessage {

    public final int fromIndex;
    public final int toIndex;
    public final int newFromIndex;
    public final ShuffleOrder shuffleOrder;

    public MoveMediaItemsMessage(
        int fromIndex, int toIndex, int newFromIndex, ShuffleOrder shuffleOrder) {
      this.fromIndex = fromIndex;
      this.toIndex = toIndex;
      this.newFromIndex = newFromIndex;
      this.shuffleOrder = shuffleOrder;
    }
  }

  private static final class PlaybackInfoUpdate {

    private PlaybackInfo lastPlaybackInfo;
    private int operationAcks;
    private boolean positionDiscontinuity;
    @DiscontinuityReason private int discontinuityReason;

    public boolean hasPendingUpdate(PlaybackInfo playbackInfo) {
      return playbackInfo != lastPlaybackInfo || operationAcks > 0 || positionDiscontinuity;
    }

    public void reset(PlaybackInfo playbackInfo) {
      lastPlaybackInfo = playbackInfo;
      operationAcks = 0;
      positionDiscontinuity = false;
    }

    public void incrementPendingOperationAcks(int operationAcks) {
      this.operationAcks += operationAcks;
    }

    public void setPositionDiscontinuity(@DiscontinuityReason int discontinuityReason) {
      if (positionDiscontinuity
          && this.discontinuityReason != Player.DISCONTINUITY_REASON_INTERNAL) {
        // We always prefer non-internal discontinuity reasons. We also assume that we won't report
        // more than one non-internal discontinuity per message iteration.
        Assertions.checkArgument(discontinuityReason == Player.DISCONTINUITY_REASON_INTERNAL);
        return;
      }
      positionDiscontinuity = true;
      this.discontinuityReason = discontinuityReason;
    }
  }
}
