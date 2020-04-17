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
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.DefaultMediaClock.PlaybackSpeedListener;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason;
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason;
import com.google.android.exoplayer2.Player.RepeatMode;
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
        MediaSourceList.MediaSourceListInfoRefreshListener,
        PlaybackSpeedListener,
        PlayerMessage.Sender {

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_PLAYBACK_INFO_CHANGED = 0;
  public static final int MSG_PLAYBACK_SPEED_CHANGED = 1;

  // Internal messages
  private static final int MSG_PREPARE = 0;
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_SET_PLAYBACK_SPEED = 4;
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
  private static final int MSG_PLAYBACK_SPEED_CHANGED_INTERNAL = 16;
  private static final int MSG_SET_MEDIA_SOURCES = 17;
  private static final int MSG_ADD_MEDIA_SOURCES = 18;
  private static final int MSG_MOVE_MEDIA_SOURCES = 19;
  private static final int MSG_REMOVE_MEDIA_SOURCES = 20;
  private static final int MSG_SET_SHUFFLE_ORDER = 21;
  private static final int MSG_PLAYLIST_UPDATE_REQUESTED = 22;
  private static final int MSG_SET_PAUSE_AT_END_OF_WINDOW = 23;

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
  private final ArrayList<PendingMessageInfo> pendingMessages;
  private final Clock clock;
  private final MediaPeriodQueue queue;
  private final MediaSourceList mediaSourceList;

  @SuppressWarnings("unused")
  private SeekParameters seekParameters;

  private PlaybackInfo playbackInfo;
  private PlaybackInfoUpdate playbackInfoUpdate;
  private boolean released;
  private boolean pauseAtEndOfWindow;
  private boolean pendingPauseAtEndOfPeriod;
  private boolean rebuffering;
  private boolean shouldContinueLoading;
  @Player.RepeatMode private int repeatMode;
  private boolean shuffleModeEnabled;
  private boolean foregroundMode;

  private int enabledRendererCount;
  @Nullable private SeekPosition pendingInitialSeekPosition;
  private long rendererPositionUs;
  private int nextPendingMessageIndex;
  private boolean deliverPendingMessageAtStartPositionRequired;

  private long releaseTimeoutMs;
  private boolean throwWhenStuckBuffering;

  public ExoPlayerImplInternal(
      Renderer[] renderers,
      TrackSelector trackSelector,
      TrackSelectorResult emptyTrackSelectorResult,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
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
    this.repeatMode = repeatMode;
    this.shuffleModeEnabled = shuffleModeEnabled;
    this.eventHandler = eventHandler;
    this.clock = clock;
    this.queue = new MediaPeriodQueue();

    backBufferDurationUs = loadControl.getBackBufferDurationUs();
    retainBackBufferFromKeyframe = loadControl.retainBackBufferFromKeyframe();

    seekParameters = SeekParameters.DEFAULT;
    playbackInfo = PlaybackInfo.createDummy(emptyTrackSelectorResult);
    playbackInfoUpdate = new PlaybackInfoUpdate(playbackInfo);
    rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      renderers[i].setIndex(i);
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    mediaClock = new DefaultMediaClock(this, clock);
    pendingMessages = new ArrayList<>();
    window = new Timeline.Window();
    period = new Timeline.Period();
    trackSelector.init(/* listener= */ this, bandwidthMeter);

    // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
    // not normally change to this priority" is incorrect.
    internalPlaybackThread = new HandlerThread("ExoPlayer:Playback", Process.THREAD_PRIORITY_AUDIO);
    internalPlaybackThread.start();
    handler = clock.createHandler(internalPlaybackThread.getLooper(), this);
    deliverPendingMessageAtStartPositionRequired = true;
    mediaSourceList = new MediaSourceList(this);
    if (analyticsCollector != null) {
      mediaSourceList.setAnalyticsCollector(eventHandler, analyticsCollector);
    }
  }

  public void experimental_setReleaseTimeoutMs(long releaseTimeoutMs) {
    this.releaseTimeoutMs = releaseTimeoutMs;
  }

  public void experimental_throwWhenStuckBuffering() {
    throwWhenStuckBuffering = true;
  }

  public void prepare() {
    handler.obtainMessage(MSG_PREPARE).sendToTarget();
  }

  public void setPlayWhenReady(
      boolean playWhenReady, @PlaybackSuppressionReason int playbackSuppressionReason) {
    handler
        .obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, playbackSuppressionReason)
        .sendToTarget();
  }

  public void setPauseAtEndOfWindow(boolean pauseAtEndOfWindow) {
    handler
        .obtainMessage(MSG_SET_PAUSE_AT_END_OF_WINDOW, pauseAtEndOfWindow ? 1 : 0, /* ignored */ 0)
        .sendToTarget();
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

  public void setPlaybackSpeed(float playbackSpeed) {
    handler.obtainMessage(MSG_SET_PLAYBACK_SPEED, playbackSpeed).sendToTarget();
  }

  public void setSeekParameters(SeekParameters seekParameters) {
    handler.obtainMessage(MSG_SET_SEEK_PARAMETERS, seekParameters).sendToTarget();
  }

  public void stop(boolean reset) {
    handler.obtainMessage(MSG_STOP, reset ? 1 : 0, 0).sendToTarget();
  }

  public void setMediaSources(
      List<MediaSourceList.MediaSourceHolder> mediaSources,
      int windowIndex,
      long positionUs,
      ShuffleOrder shuffleOrder) {
    handler
        .obtainMessage(
            MSG_SET_MEDIA_SOURCES,
            new MediaSourceListUpdateMessage(mediaSources, shuffleOrder, windowIndex, positionUs))
        .sendToTarget();
  }

  public void addMediaSources(
      int index, List<MediaSourceList.MediaSourceHolder> mediaSources, ShuffleOrder shuffleOrder) {
    handler
        .obtainMessage(
            MSG_ADD_MEDIA_SOURCES,
            index,
            /* ignored */ 0,
            new MediaSourceListUpdateMessage(
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

  // DefaultMediaClock.PlaybackSpeedListener implementation.

  @Override
  public void onPlaybackSpeedChanged(float playbackSpeed) {
    sendPlaybackSpeedChangedInternal(playbackSpeed, /* acknowledgeCommand= */ false);
  }

  // Handler.Callback implementation.

  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_PREPARE:
          prepareInternal();
          break;
        case MSG_SET_PLAY_WHEN_READY:
          setPlayWhenReadyInternal(
              /* playWhenReady= */ msg.arg1 != 0,
              /* playbackSuppressionReason= */ msg.arg2,
              /* operationAck= */ true,
              Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
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
        case MSG_SET_PLAYBACK_SPEED:
          setPlaybackSpeedInternal((Float) msg.obj);
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
        case MSG_PLAYBACK_SPEED_CHANGED_INTERNAL:
          handlePlaybackSpeed((Float) msg.obj, /* acknowledgeCommand= */ msg.arg1 != 0);
          break;
        case MSG_SEND_MESSAGE:
          sendMessageInternal((PlayerMessage) msg.obj);
          break;
        case MSG_SEND_MESSAGE_TO_TARGET_THREAD:
          sendMessageToTargetThread((PlayerMessage) msg.obj);
          break;
        case MSG_SET_MEDIA_SOURCES:
          setMediaItemsInternal((MediaSourceListUpdateMessage) msg.obj);
          break;
        case MSG_ADD_MEDIA_SOURCES:
          addMediaItemsInternal((MediaSourceListUpdateMessage) msg.obj, msg.arg1);
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
          mediaSourceListUpdateRequestedInternal();
          break;
        case MSG_SET_PAUSE_AT_END_OF_WINDOW:
          setPauseAtEndOfWindowInternal(msg.arg1 != 0);
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
      Log.e(TAG, "Playback error", e);
      stopInternal(
          /* forceResetRenderers= */ true,
          /* resetPositionAndState= */ false,
          /* acknowledgeStop= */ false);
      playbackInfo = playbackInfo.copyWithPlaybackError(e);
      maybeNotifyPlaybackInfoChanged();
    } catch (IOException e) {
      ExoPlaybackException error = ExoPlaybackException.createForSource(e);
      Log.e(TAG, "Playback error", error);
      stopInternal(
          /* forceResetRenderers= */ false,
          /* resetPositionAndState= */ false,
          /* acknowledgeStop= */ false);
      playbackInfo = playbackInfo.copyWithPlaybackError(error);
      maybeNotifyPlaybackInfoChanged();
    } catch (RuntimeException | OutOfMemoryError e) {
      ExoPlaybackException error =
          e instanceof OutOfMemoryError
              ? ExoPlaybackException.createForOutOfMemoryError((OutOfMemoryError) e)
              : ExoPlaybackException.createForUnexpected((RuntimeException) e);
      Log.e(TAG, "Playback error", error);
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
    playbackInfoUpdate.setPlaybackInfo(playbackInfo);
    if (playbackInfoUpdate.hasPendingChange) {
      eventHandler.obtainMessage(MSG_PLAYBACK_INFO_CHANGED, playbackInfoUpdate).sendToTarget();
      playbackInfoUpdate = new PlaybackInfoUpdate(playbackInfo);
    }
  }

  private void prepareInternal() {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    resetInternal(
        /* resetRenderers= */ false,
        /* resetPosition= */ false,
        /* releaseMediaSourceList= */ false,
        /* clearMediaSourceList= */ false,
        /* resetError= */ true);
    loadControl.onPrepared();
    setState(playbackInfo.timeline.isEmpty() ? Player.STATE_ENDED : Player.STATE_BUFFERING);
    mediaSourceList.prepare(bandwidthMeter.getTransferListener());
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void setMediaItemsInternal(MediaSourceListUpdateMessage mediaSourceListUpdateMessage)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    if (mediaSourceListUpdateMessage.windowIndex != C.INDEX_UNSET) {
      pendingInitialSeekPosition =
          new SeekPosition(
              new MediaSourceList.PlaylistTimeline(
                  mediaSourceListUpdateMessage.mediaSourceHolders,
                  mediaSourceListUpdateMessage.shuffleOrder),
              mediaSourceListUpdateMessage.windowIndex,
              mediaSourceListUpdateMessage.positionUs);
    }
    Timeline timeline =
        mediaSourceList.setMediaSources(
            mediaSourceListUpdateMessage.mediaSourceHolders,
            mediaSourceListUpdateMessage.shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline);
  }

  private void addMediaItemsInternal(MediaSourceListUpdateMessage addMessage, int insertionIndex)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline =
        mediaSourceList.addMediaSources(
            insertionIndex == C.INDEX_UNSET ? mediaSourceList.getSize() : insertionIndex,
            addMessage.mediaSourceHolders,
            addMessage.shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline);
  }

  private void moveMediaItemsInternal(MoveMediaItemsMessage moveMediaItemsMessage)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline =
        mediaSourceList.moveMediaSourceRange(
            moveMediaItemsMessage.fromIndex,
            moveMediaItemsMessage.toIndex,
            moveMediaItemsMessage.newFromIndex,
            moveMediaItemsMessage.shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline);
  }

  private void removeMediaItemsInternal(int fromIndex, int toIndex, ShuffleOrder shuffleOrder)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline = mediaSourceList.removeMediaSourceRange(fromIndex, toIndex, shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline);
  }

  private void mediaSourceListUpdateRequestedInternal() throws ExoPlaybackException {
    handleMediaSourceListInfoRefreshed(mediaSourceList.createTimeline());
  }

  private void setShuffleOrderInternal(ShuffleOrder shuffleOrder) throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline = mediaSourceList.setShuffleOrder(shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline);
  }

  private void setPlayWhenReadyInternal(
      boolean playWhenReady,
      @PlaybackSuppressionReason int playbackSuppressionReason,
      boolean operationAck,
      @Player.PlayWhenReadyChangeReason int reason)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(operationAck ? 1 : 0);
    playbackInfoUpdate.setPlayWhenReadyChangeReason(reason);
    playbackInfo = playbackInfo.copyWithPlayWhenReady(playWhenReady, playbackSuppressionReason);
    rebuffering = false;
    if (!shouldPlayWhenReady()) {
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

  private void setPauseAtEndOfWindowInternal(boolean pauseAtEndOfWindow)
      throws ExoPlaybackException {
    this.pauseAtEndOfWindow = pauseAtEndOfWindow;
    if (queue.getReadingPeriod() != queue.getPlayingPeriod()) {
      seekToCurrentPosition(/* sendDiscontinuity= */ true);
    }
    resetPendingPauseAtEndOfPeriod();
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private void setRepeatModeInternal(@Player.RepeatMode int repeatMode)
      throws ExoPlaybackException {
    this.repeatMode = repeatMode;
    if (!queue.updateRepeatMode(playbackInfo.timeline, repeatMode)) {
      seekToCurrentPosition(/* sendDiscontinuity= */ true);
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private void setShuffleModeEnabledInternal(boolean shuffleModeEnabled)
      throws ExoPlaybackException {
    this.shuffleModeEnabled = shuffleModeEnabled;
    if (!queue.updateShuffleModeEnabled(playbackInfo.timeline, shuffleModeEnabled)) {
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
      playbackInfo =
          handlePositionDiscontinuity(
              periodId, newPositionUs, playbackInfo.requestedContentPositionUs);
      if (sendDiscontinuity) {
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
      }
    }
  }

  private void startRenderers() throws ExoPlaybackException {
    rebuffering = false;
    mediaClock.start();
    for (Renderer renderer : renderers) {
      if (isRendererEnabled(renderer)) {
        renderer.start();
      }
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    mediaClock.stop();
    for (Renderer renderer : renderers) {
      if (isRendererEnabled(renderer)) {
        ensureStopped(renderer);
      }
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
            handlePositionDiscontinuity(
                playbackInfo.periodId,
                discontinuityPositionUs,
                playbackInfo.requestedContentPositionUs);
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
        if (!isRendererEnabled(renderer)) {
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
    boolean finishedRendering =
        renderersEnded
            && playingPeriodHolder.prepared
            && (playingPeriodDurationUs == C.TIME_UNSET
                || playingPeriodDurationUs <= playbackInfo.positionUs);
    if (finishedRendering && pendingPauseAtEndOfPeriod) {
      pendingPauseAtEndOfPeriod = false;
      setPlayWhenReadyInternal(
          /* playWhenReady= */ false,
          playbackInfo.playbackSuppressionReason,
          /* operationAck= */ false,
          Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM);
    }
    if (finishedRendering && playingPeriodHolder.info.isFinal) {
      setState(Player.STATE_ENDED);
      stopRenderers();
    } else if (playbackInfo.playbackState == Player.STATE_BUFFERING
        && shouldTransitionToReadyState(renderersAllowPlayback)) {
      setState(Player.STATE_READY);
      if (shouldPlayWhenReady()) {
        startRenderers();
      }
    } else if (playbackInfo.playbackState == Player.STATE_READY
        && !(enabledRendererCount == 0 ? isTimelineReady() : renderersAllowPlayback)) {
      rebuffering = shouldPlayWhenReady();
      setState(Player.STATE_BUFFERING);
      stopRenderers();
    }

    if (playbackInfo.playbackState == Player.STATE_BUFFERING) {
      for (int i = 0; i < renderers.length; i++) {
        if (isRendererEnabled(renderers[i])
            && renderers[i].getStream() == playingPeriodHolder.sampleStreams[i]) {
          renderers[i].maybeThrowStreamError();
        }
      }
      if (throwWhenStuckBuffering
          && !playbackInfo.isLoading
          && playbackInfo.totalBufferedDurationUs < 500_000
          && isLoadingPossible()) {
        // Throw if the LoadControl prevents loading even if the buffer is empty or almost empty. We
        // can't compare against 0 to account for small differences between the renderer position
        // and buffered position in the media at the point where playback gets stuck.
        throw new IllegalStateException("Playback stuck buffering and not loading");
      }
    }

    if ((shouldPlayWhenReady() && playbackInfo.playbackState == Player.STATE_READY)
        || playbackInfo.playbackState == Player.STATE_BUFFERING) {
      scheduleNextWork(operationStartTimeMs, ACTIVE_INTERVAL_MS);
    } else if (enabledRendererCount != 0 && playbackInfo.playbackState != Player.STATE_ENDED) {
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
    long requestedContentPosition;
    boolean seekPositionAdjusted;
    @Nullable
    Pair<Object, Long> resolvedSeekPosition =
        resolveSeekPosition(
            playbackInfo.timeline,
            seekPosition,
            /* trySubsequentPeriods= */ true,
            repeatMode,
            shuffleModeEnabled,
            window,
            period);
    if (resolvedSeekPosition == null) {
      // The seek position was valid for the timeline that it was performed into, but the
      // timeline has changed or is not ready and a suitable seek position could not be resolved.
      Pair<MediaPeriodId, Long> firstPeriodAndPosition =
          getDummyFirstMediaPeriodPosition(playbackInfo.timeline);
      periodId = firstPeriodAndPosition.first;
      periodPositionUs = firstPeriodAndPosition.second;
      requestedContentPosition = C.TIME_UNSET;
      seekPositionAdjusted = !playbackInfo.timeline.isEmpty();
    } else {
      // Update the resolved seek position to take ads into account.
      Object periodUid = resolvedSeekPosition.first;
      long resolvedContentPosition = resolvedSeekPosition.second;
      requestedContentPosition =
          seekPosition.windowPositionUs == C.TIME_UNSET ? C.TIME_UNSET : resolvedContentPosition;
      periodId =
          queue.resolveMediaPeriodIdForAds(
              playbackInfo.timeline, periodUid, resolvedContentPosition);
      if (periodId.isAd()) {
        playbackInfo.timeline.getPeriodByUid(periodId.periodUid, period);
        periodPositionUs =
            period.getFirstAdIndexToPlay(periodId.adGroupIndex) == periodId.adIndexInAdGroup
                ? period.getAdResumePositionUs()
                : 0;
        seekPositionAdjusted = true;
      } else {
        periodPositionUs = resolvedContentPosition;
        seekPositionAdjusted = seekPosition.windowPositionUs == C.TIME_UNSET;
      }
    }

    try {
      if (playbackInfo.timeline.isEmpty()) {
        // Save seek position for later, as we are still waiting for a prepared source.
        pendingInitialSeekPosition = seekPosition;
      } else if (resolvedSeekPosition == null) {
        // End playback, as we didn't manage to find a valid seek position.
        if (playbackInfo.playbackState != Player.STATE_IDLE) {
          setState(Player.STATE_ENDED);
        }
        resetInternal(
            /* resetRenderers= */ false,
            /* resetPosition= */ true,
            /* releaseMediaSourceList= */ false,
            /* clearMediaSourceList= */ false,
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
          if (C.usToMs(newPeriodPositionUs) == C.usToMs(playbackInfo.positionUs)
              && (playbackInfo.playbackState == Player.STATE_BUFFERING
                  || playbackInfo.playbackState == Player.STATE_READY)) {
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
      playbackInfo =
          handlePositionDiscontinuity(periodId, periodPositionUs, requestedContentPosition);
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

    // Find the requested period if it already exists.
    @Nullable MediaPeriodHolder oldPlayingPeriodHolder = queue.getPlayingPeriod();
    @Nullable MediaPeriodHolder newPlayingPeriodHolder = oldPlayingPeriodHolder;
    while (newPlayingPeriodHolder != null) {
      if (periodId.equals(newPlayingPeriodHolder.info.id)) {
        break;
      }
      newPlayingPeriodHolder = newPlayingPeriodHolder.getNext();
    }

    // Disable all renderers if the period being played is changing, if the seek results in negative
    // renderer timestamps, or if forced.
    if (forceDisableRenderers
        || oldPlayingPeriodHolder != newPlayingPeriodHolder
        || (newPlayingPeriodHolder != null
            && newPlayingPeriodHolder.toRendererTime(periodPositionUs) < 0)) {
      for (Renderer renderer : renderers) {
        disableRenderer(renderer);
      }
      if (newPlayingPeriodHolder != null) {
        // Update the queue and reenable renderers if the requested media period already exists.
        while (queue.getPlayingPeriod() != newPlayingPeriodHolder) {
          queue.advancePlayingPeriod();
        }
        queue.removeAfter(newPlayingPeriodHolder);
        newPlayingPeriodHolder.setRendererOffset(/* rendererPositionOffsetUs= */ 0);
        enableRenderers();
      }
    }

    // Do the actual seeking.
    if (newPlayingPeriodHolder != null) {
      queue.removeAfter(newPlayingPeriodHolder);
      if (!newPlayingPeriodHolder.prepared) {
        newPlayingPeriodHolder.info =
            newPlayingPeriodHolder.info.copyWithStartPositionUs(periodPositionUs);
      } else {
        if (newPlayingPeriodHolder.info.durationUs != C.TIME_UNSET
            && periodPositionUs >= newPlayingPeriodHolder.info.durationUs) {
          // Make sure seek position doesn't exceed period duration.
          periodPositionUs = Math.max(0, newPlayingPeriodHolder.info.durationUs - 1);
        }
        if (newPlayingPeriodHolder.hasEnabledTracks) {
          periodPositionUs = newPlayingPeriodHolder.mediaPeriod.seekToUs(periodPositionUs);
          newPlayingPeriodHolder.mediaPeriod.discardBuffer(
              periodPositionUs - backBufferDurationUs, retainBackBufferFromKeyframe);
        }
      }
      resetRendererPosition(periodPositionUs);
      maybeContinueLoading();
    } else {
      // New period has not been prepared.
      queue.clear();
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
    for (Renderer renderer : renderers) {
      if (isRendererEnabled(renderer)) {
        renderer.resetPosition(rendererPositionUs);
      }
    }
    notifyTrackSelectionDiscontinuity();
  }

  private void setPlaybackSpeedInternal(float playbackSpeed) {
    mediaClock.setPlaybackSpeed(playbackSpeed);
    sendPlaybackSpeedChangedInternal(mediaClock.getPlaybackSpeed(), /* acknowledgeCommand= */ true);
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
          if (!isRendererEnabled(renderer)) {
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
        /* releaseMediaSourceList= */ true,
        /* clearMediaSourceList= */ resetPositionAndState,
        /* resetError= */ resetPositionAndState);
    playbackInfoUpdate.incrementPendingOperationAcks(acknowledgeStop ? 1 : 0);
    loadControl.onStopped();
    setState(Player.STATE_IDLE);
  }

  private void releaseInternal() {
    resetInternal(
        /* resetRenderers= */ true,
        /* resetPosition= */ true,
        /* releaseMediaSourceList= */ true,
        /* clearMediaSourceList= */ true,
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
      boolean releaseMediaSourceList,
      boolean clearMediaSourceList,
      boolean resetError) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    rebuffering = false;
    mediaClock.stop();
    rendererPositionUs = 0;
    for (Renderer renderer : renderers) {
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
    enabledRendererCount = 0;

    Timeline timeline = playbackInfo.timeline;
    if (clearMediaSourceList) {
      timeline = mediaSourceList.clear(/* shuffleOrder= */ null);
      for (PendingMessageInfo pendingMessageInfo : pendingMessages) {
        pendingMessageInfo.message.markAsProcessed(/* isDelivered= */ false);
      }
      pendingMessages.clear();
      nextPendingMessageIndex = 0;
      resetPosition = true;
    }
    MediaPeriodId mediaPeriodId = playbackInfo.periodId;
    long startPositionUs = playbackInfo.positionUs;
    long requestedContentPositionUs =
        shouldUseRequestedContentPosition(playbackInfo, period, window)
            ? playbackInfo.requestedContentPositionUs
            : playbackInfo.positionUs;
    boolean resetTrackInfo = clearMediaSourceList;
    if (resetPosition) {
      pendingInitialSeekPosition = null;
      Pair<MediaPeriodId, Long> firstPeriodAndPosition = getDummyFirstMediaPeriodPosition(timeline);
      mediaPeriodId = firstPeriodAndPosition.first;
      startPositionUs = firstPeriodAndPosition.second;
      requestedContentPositionUs = C.TIME_UNSET;
      if (!mediaPeriodId.equals(playbackInfo.periodId)) {
        resetTrackInfo = true;
      }
    }

    queue.clear();
    shouldContinueLoading = false;

    playbackInfo =
        new PlaybackInfo(
            timeline,
            mediaPeriodId,
            requestedContentPositionUs,
            playbackInfo.playbackState,
            resetError ? null : playbackInfo.playbackError,
            /* isLoading= */ false,
            resetTrackInfo ? TrackGroupArray.EMPTY : playbackInfo.trackGroups,
            resetTrackInfo ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult,
            mediaPeriodId,
            playbackInfo.playWhenReady,
            playbackInfo.playbackSuppressionReason,
            startPositionUs,
            /* totalBufferedDurationUs= */ 0,
            startPositionUs);
    if (releaseMediaSourceList) {
      mediaSourceList.release();
    }
  }

  private Pair<MediaPeriodId, Long> getDummyFirstMediaPeriodPosition(Timeline timeline) {
    if (timeline.isEmpty()) {
      return Pair.create(PlaybackInfo.getDummyPeriodForEmptyTimeline(), 0L);
    }
    int firstWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
    Pair<Object, Long> firstPeriodAndPosition =
        timeline.getPeriodPosition(
            window, period, firstWindowIndex, /* windowPositionUs= */ C.TIME_UNSET);
    // Add ad metadata if any and propagate the window sequence number to new period id.
    MediaPeriodId firstPeriodId =
        queue.resolveMediaPeriodIdForAds(
            timeline, firstPeriodAndPosition.first, /* positionUs= */ 0);
    long positionUs = firstPeriodAndPosition.second;
    if (firstPeriodId.isAd()) {
      timeline.getPeriodByUid(firstPeriodId.periodUid, period);
      positionUs =
          firstPeriodId.adIndexInAdGroup == period.getFirstAdIndexToPlay(firstPeriodId.adGroupIndex)
              ? period.getAdResumePositionUs()
              : 0;
    }
    return Pair.create(firstPeriodId, positionUs);
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
      if (resolvePendingMessagePosition(
          pendingMessageInfo,
          /* newTimeline= */ playbackInfo.timeline,
          /* previousTimeline= */ playbackInfo.timeline,
          repeatMode,
          shuffleModeEnabled,
          window,
          period)) {
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

  private void resolvePendingMessagePositions(Timeline newTimeline, Timeline previousTimeline) {
    if (newTimeline.isEmpty() && previousTimeline.isEmpty()) {
      // Keep all messages unresolved until we have a non-empty timeline.
      return;
    }
    for (int i = pendingMessages.size() - 1; i >= 0; i--) {
      if (!resolvePendingMessagePosition(
          pendingMessages.get(i),
          newTimeline,
          previousTimeline,
          repeatMode,
          shuffleModeEnabled,
          window,
          period)) {
        // Unable to resolve a new position for the message. Remove it.
        pendingMessages.get(i).message.markAsProcessed(/* isDelivered= */ false);
        pendingMessages.remove(i);
      }
    }
    // Re-sort messages by playback order.
    Collections.sort(pendingMessages);
  }

  private void maybeTriggerPendingMessages(long oldPeriodPositionUs, long newPeriodPositionUs)
      throws ExoPlaybackException {
    if (pendingMessages.isEmpty() || playbackInfo.periodId.isAd()) {
      return;
    }
    // If this is the first call after resetting the renderer position, include oldPeriodPositionUs
    // in potential trigger positions, but make sure we deliver it only once.
    if (deliverPendingMessageAtStartPositionRequired) {
      oldPeriodPositionUs--;
      deliverPendingMessageAtStartPositionRequired = false;
    }

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
    if (!isRendererEnabled(renderer)) {
      return;
    }
    mediaClock.onRendererDisabled(renderer);
    ensureStopped(renderer);
    renderer.disable();
    enabledRendererCount--;
  }

  private void reselectTracksInternal() throws ExoPlaybackException {
    float playbackSpeed = mediaClock.getPlaybackSpeed();
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
      playbackInfo =
          handlePositionDiscontinuity(
              playbackInfo.periodId, periodPositionUs, playbackInfo.requestedContentPositionUs);
      if (playbackInfo.playbackState != Player.STATE_ENDED
          && periodPositionUs != playbackInfo.positionUs) {
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
        resetRendererPosition(periodPositionUs);
      }

      boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        rendererWasEnabledFlags[i] = isRendererEnabled(renderer);
        SampleStream sampleStream = playingPeriodHolder.sampleStreams[i];
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
      enableRenderers(rendererWasEnabledFlags);
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
    if (enabledRendererCount == 0) {
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
            getTotalBufferedDurationUs(), mediaClock.getPlaybackSpeed(), rebuffering);
  }

  private boolean isTimelineReady() {
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
    return playingPeriodHolder.prepared
        && (playingPeriodDurationUs == C.TIME_UNSET
            || playbackInfo.positionUs < playingPeriodDurationUs
            || !shouldPlayWhenReady());
  }

  private void maybeThrowSourceInfoRefreshError() throws IOException {
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    if (loadingPeriodHolder != null) {
      // Defer throwing until we read all available media periods.
      for (Renderer renderer : renderers) {
        if (isRendererEnabled(renderer) && !renderer.hasReadStreamToEnd()) {
          return;
        }
      }
    }
    mediaSourceList.maybeThrowSourceInfoRefreshError();
  }

  private void handleMediaSourceListInfoRefreshed(Timeline timeline) throws ExoPlaybackException {
    PositionUpdateForPlaylistChange positionUpdate =
        resolvePositionForPlaylistChange(
            timeline,
            playbackInfo,
            pendingInitialSeekPosition,
            queue,
            repeatMode,
            shuffleModeEnabled,
            window,
            period);
    MediaPeriodId newPeriodId = positionUpdate.periodId;
    long newRequestedContentPositionUs = positionUpdate.requestedContentPositionUs;
    boolean forceBufferingState = positionUpdate.forceBufferingState;
    long newPositionUs = positionUpdate.periodPositionUs;
    boolean periodPositionChanged =
        !playbackInfo.periodId.equals(newPeriodId) || newPositionUs != playbackInfo.positionUs;

    try {
      if (positionUpdate.endPlayback) {
        if (playbackInfo.playbackState != Player.STATE_IDLE) {
          setState(Player.STATE_ENDED);
        }
        resetInternal(
            /* resetRenderers= */ false,
            /* resetPosition= */ false,
            /* releaseMediaSourceList= */ false,
            /* clearMediaSourceList= */ false,
            /* resetError= */ true);
      }
      if (!periodPositionChanged) {
        // We can keep the current playing period. Update the rest of the queued periods.
        if (!queue.updateQueuedPeriods(
            timeline, rendererPositionUs, getMaxRendererReadPositionUs())) {
          seekToCurrentPosition(/* sendDiscontinuity= */ false);
        }
      } else if (!timeline.isEmpty()) {
        // Something changed. Seek to new start position.
        @Nullable MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
        while (periodHolder != null) {
          // Update the new playing media period info if it already exists.
          if (periodHolder.info.id.equals(newPeriodId)) {
            periodHolder.info = queue.getUpdatedMediaPeriodInfo(timeline, periodHolder.info);
          }
          periodHolder = periodHolder.getNext();
        }
        newPositionUs = seekToPeriodPosition(newPeriodId, newPositionUs, forceBufferingState);
      }
    } finally {
      if (periodPositionChanged
          || newRequestedContentPositionUs != playbackInfo.requestedContentPositionUs) {
        playbackInfo =
            handlePositionDiscontinuity(newPeriodId, newPositionUs, newRequestedContentPositionUs);
      }
      resetPendingPauseAtEndOfPeriod();
      resolvePendingMessagePositions(
          /* newTimeline= */ timeline, /* previousTimeline= */ playbackInfo.timeline);
      playbackInfo = playbackInfo.copyWithTimeline(timeline);
      if (!timeline.isEmpty()) {
        // Retain pending seek position only while the timeline is still empty.
        pendingInitialSeekPosition = null;
      }
      handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
    }
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
      if (!isRendererEnabled(renderers[i])
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

  private void updatePeriods() throws ExoPlaybackException, IOException {
    if (playbackInfo.timeline.isEmpty() || !mediaSourceList.isPrepared()) {
      // We're waiting to get information about periods.
      mediaSourceList.maybeThrowSourceInfoRefreshError();
      return;
    }
    maybeUpdateLoadingPeriod();
    maybeUpdateReadingPeriod();
    maybeUpdateReadingRenderers();
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
                mediaSourceList,
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
      // We should still be loading, except in the case that it's no longer possible (i.e., because
      // we've loaded the current playlist to the end).
      shouldContinueLoading = isLoadingPossible();
      updateIsLoading();
    } else {
      maybeContinueLoading();
    }
  }

  private void maybeUpdateReadingPeriod() {
    @Nullable MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    if (readingPeriodHolder == null) {
      return;
    }

    if (readingPeriodHolder.getNext() == null || pendingPauseAtEndOfPeriod) {
      // We don't have a successor to advance the reading period to or we want to let them end
      // intentionally to pause at the end of the period.
      if (readingPeriodHolder.info.isFinal || pendingPauseAtEndOfPeriod) {
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

    if (!readingPeriodHolder.getNext().prepared
        && rendererPositionUs < readingPeriodHolder.getNext().getStartPositionRendererTime()) {
      // The successor is not prepared yet and playback hasn't reached the transition point.
      return;
    }

    TrackSelectorResult oldTrackSelectorResult = readingPeriodHolder.getTrackSelectorResult();
    readingPeriodHolder = queue.advanceReadingPeriod();
    TrackSelectorResult newTrackSelectorResult = readingPeriodHolder.getTrackSelectorResult();

    if (readingPeriodHolder.prepared
        && readingPeriodHolder.mediaPeriod.readDiscontinuity() != C.TIME_UNSET) {
      // The new period starts with a discontinuity, so the renderers will play out all data, then
      // be disabled and re-enabled when they start playing the next period.
      setAllRendererStreamsFinal();
      return;
    }
    for (int i = 0; i < renderers.length; i++) {
      boolean oldRendererEnabled = oldTrackSelectorResult.isRendererEnabled(i);
      boolean newRendererEnabled = newTrackSelectorResult.isRendererEnabled(i);
      if (oldRendererEnabled && !renderers[i].isCurrentStreamFinal()) {
        boolean isNoSampleRenderer = rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE;
        RendererConfiguration oldConfig = oldTrackSelectorResult.rendererConfigurations[i];
        RendererConfiguration newConfig = newTrackSelectorResult.rendererConfigurations[i];
        if (!newRendererEnabled || !newConfig.equals(oldConfig) || isNoSampleRenderer) {
          // The renderer will be disabled when transitioning to playing the next period, because
          // there's no new selection, or because a configuration change is required, or because
          // it's a no-sample renderer for which rendererOffsetUs should be updated only when
          // starting to play the next period. Mark the SampleStream as final to play out any
          // remaining data.
          renderers[i].setCurrentStreamFinal();
        }
      }
    }
  }

  private void maybeUpdateReadingRenderers() throws ExoPlaybackException {
    @Nullable MediaPeriodHolder readingPeriod = queue.getReadingPeriod();
    if (readingPeriod == null
        || queue.getPlayingPeriod() == readingPeriod
        || readingPeriod.allRenderersEnabled) {
      // Not reading ahead or all renderers updated.
      return;
    }
    if (replaceStreamsOrDisableRendererForTransition()) {
      enableRenderers();
    }
  }

  private boolean replaceStreamsOrDisableRendererForTransition() throws ExoPlaybackException {
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    TrackSelectorResult newTrackSelectorResult = readingPeriodHolder.getTrackSelectorResult();
    boolean needsToWaitForRendererToEnd = false;
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      if (!isRendererEnabled(renderer)) {
        continue;
      }
      boolean rendererIsReadingOldStream =
          renderer.getStream() != readingPeriodHolder.sampleStreams[i];
      boolean rendererShouldBeEnabled = newTrackSelectorResult.isRendererEnabled(i);
      if (rendererShouldBeEnabled && !rendererIsReadingOldStream) {
        // All done.
        continue;
      }
      if (!renderer.isCurrentStreamFinal()) {
        // The renderer stream is not final, so we can replace the sample streams immediately.
        Format[] formats = getFormats(newTrackSelectorResult.selections.get(i));
        renderer.replaceStream(
            formats, readingPeriodHolder.sampleStreams[i], readingPeriodHolder.getRendererOffset());
      } else if (renderer.isEnded()) {
        // The renderer has finished playback, so we can disable it now.
        disableRenderer(renderer);
      } else {
        // We need to wait until rendering finished before disabling the renderer.
        needsToWaitForRendererToEnd = true;
      }
    }
    return !needsToWaitForRendererToEnd;
  }

  private void maybeUpdatePlayingPeriod() throws ExoPlaybackException {
    boolean advancedPlayingPeriod = false;
    while (shouldAdvancePlayingPeriod()) {
      if (advancedPlayingPeriod) {
        // If we advance more than one period at a time, notify listeners after each update.
        maybeNotifyPlaybackInfoChanged();
      }
      MediaPeriodHolder oldPlayingPeriodHolder = queue.getPlayingPeriod();
      MediaPeriodHolder newPlayingPeriodHolder = queue.advancePlayingPeriod();
      playbackInfo =
          handlePositionDiscontinuity(
              newPlayingPeriodHolder.info.id,
              newPlayingPeriodHolder.info.startPositionUs,
              newPlayingPeriodHolder.info.requestedContentPositionUs);
      int discontinuityReason =
          oldPlayingPeriodHolder.info.isLastInTimelinePeriod
              ? Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
              : Player.DISCONTINUITY_REASON_AD_INSERTION;
      playbackInfoUpdate.setPositionDiscontinuity(discontinuityReason);
      resetPendingPauseAtEndOfPeriod();
      updatePlaybackPositions();
      advancedPlayingPeriod = true;
    }
  }

  private void resetPendingPauseAtEndOfPeriod() {
    @Nullable MediaPeriodHolder playingPeriod = queue.getPlayingPeriod();
    pendingPauseAtEndOfPeriod =
        playingPeriod != null && playingPeriod.info.isLastInTimelineWindow && pauseAtEndOfWindow;
  }

  private boolean shouldAdvancePlayingPeriod() {
    if (!shouldPlayWhenReady()) {
      return false;
    }
    if (pendingPauseAtEndOfPeriod) {
      return false;
    }
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (playingPeriodHolder == null) {
      return false;
    }
    MediaPeriodHolder nextPlayingPeriodHolder = playingPeriodHolder.getNext();
    return nextPlayingPeriodHolder != null
        && rendererPositionUs >= nextPlayingPeriodHolder.getStartPositionRendererTime()
        && nextPlayingPeriodHolder.allRenderersEnabled;
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
    loadingPeriodHolder.handlePrepared(mediaClock.getPlaybackSpeed(), playbackInfo.timeline);
    updateLoadControlTrackSelection(
        loadingPeriodHolder.getTrackGroups(), loadingPeriodHolder.getTrackSelectorResult());
    if (loadingPeriodHolder == queue.getPlayingPeriod()) {
      // This is the first prepared period, so update the position and the renderers.
      resetRendererPosition(loadingPeriodHolder.info.startPositionUs);
      enableRenderers();
      playbackInfo =
          handlePositionDiscontinuity(
              playbackInfo.periodId,
              loadingPeriodHolder.info.startPositionUs,
              playbackInfo.requestedContentPositionUs);
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

  private void handlePlaybackSpeed(float playbackSpeed, boolean acknowledgeCommand)
      throws ExoPlaybackException {
    eventHandler
        .obtainMessage(MSG_PLAYBACK_SPEED_CHANGED, acknowledgeCommand ? 1 : 0, 0, playbackSpeed)
        .sendToTarget();
    updateTrackSelectionPlaybackSpeed(playbackSpeed);
    for (Renderer renderer : renderers) {
      if (renderer != null) {
        renderer.setOperatingRate(playbackSpeed);
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
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    long bufferedDurationUs =
        getTotalBufferedDurationUs(loadingPeriodHolder.getNextLoadPositionUs());
    long playbackPositionUs =
        loadingPeriodHolder == queue.getPlayingPeriod()
            ? loadingPeriodHolder.toPeriodTime(rendererPositionUs)
            : loadingPeriodHolder.toPeriodTime(rendererPositionUs)
                - loadingPeriodHolder.info.startPositionUs;
    return loadControl.shouldContinueLoading(
        playbackPositionUs, bufferedDurationUs, mediaClock.getPlaybackSpeed());
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

  @CheckResult
  private PlaybackInfo handlePositionDiscontinuity(
      MediaPeriodId mediaPeriodId, long positionUs, long contentPositionUs) {
    deliverPendingMessageAtStartPositionRequired =
        deliverPendingMessageAtStartPositionRequired
            || positionUs != playbackInfo.positionUs
            || !mediaPeriodId.equals(playbackInfo.periodId);
    resetPendingPauseAtEndOfPeriod();
    TrackGroupArray trackGroupArray = playbackInfo.trackGroups;
    TrackSelectorResult trackSelectorResult = playbackInfo.trackSelectorResult;
    if (mediaSourceList.isPrepared()) {
      @Nullable MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
      trackGroupArray =
          playingPeriodHolder == null
              ? TrackGroupArray.EMPTY
              : playingPeriodHolder.getTrackGroups();
      trackSelectorResult =
          playingPeriodHolder == null
              ? emptyTrackSelectorResult
              : playingPeriodHolder.getTrackSelectorResult();
    } else if (!mediaPeriodId.equals(playbackInfo.periodId)) {
      // Reset previously kept track info if unprepared and the period changes.
      trackGroupArray = TrackGroupArray.EMPTY;
      trackSelectorResult = emptyTrackSelectorResult;
    }
    return playbackInfo.copyWithNewPosition(
        mediaPeriodId,
        positionUs,
        contentPositionUs,
        getTotalBufferedDurationUs(),
        trackGroupArray,
        trackSelectorResult);
  }

  private void enableRenderers() throws ExoPlaybackException {
    enableRenderers(/* rendererWasEnabledFlags= */ new boolean[renderers.length]);
  }

  private void enableRenderers(boolean[] rendererWasEnabledFlags) throws ExoPlaybackException {
    MediaPeriodHolder readingMediaPeriod = queue.getReadingPeriod();
    TrackSelectorResult trackSelectorResult = readingMediaPeriod.getTrackSelectorResult();
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
        enableRenderer(i, rendererWasEnabledFlags[i]);
      }
    }
    readingMediaPeriod.allRenderersEnabled = true;
  }

  private void enableRenderer(int rendererIndex, boolean wasRendererEnabled)
      throws ExoPlaybackException {
    Renderer renderer = renderers[rendererIndex];
    if (isRendererEnabled(renderer)) {
      return;
    }
    MediaPeriodHolder periodHolder = queue.getReadingPeriod();
    boolean mayRenderStartOfStream = periodHolder == queue.getPlayingPeriod();
    TrackSelectorResult trackSelectorResult = periodHolder.getTrackSelectorResult();
    RendererConfiguration rendererConfiguration =
        trackSelectorResult.rendererConfigurations[rendererIndex];
    TrackSelection newSelection = trackSelectorResult.selections.get(rendererIndex);
    Format[] formats = getFormats(newSelection);
    // The renderer needs enabling with its new track selection.
    boolean playing = shouldPlayWhenReady() && playbackInfo.playbackState == Player.STATE_READY;
    // Consider as joining only if the renderer was previously disabled.
    boolean joining = !wasRendererEnabled && playing;
    // Enable the renderer.
    enabledRendererCount++;
    renderer.enable(
        rendererConfiguration,
        formats,
        periodHolder.sampleStreams[rendererIndex],
        rendererPositionUs,
        joining,
        mayRenderStartOfStream,
        periodHolder.getRendererOffset());
    mediaClock.onRendererEnabled(renderer);
    // Start the renderer if playing.
    if (playing) {
      renderer.start();
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

  private void sendPlaybackSpeedChangedInternal(float playbackSpeed, boolean acknowledgeCommand) {
    handler
        .obtainMessage(
            MSG_PLAYBACK_SPEED_CHANGED_INTERNAL, acknowledgeCommand ? 1 : 0, 0, playbackSpeed)
        .sendToTarget();
  }

  private boolean shouldPlayWhenReady() {
    return playbackInfo.playWhenReady
        && playbackInfo.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  private static PositionUpdateForPlaylistChange resolvePositionForPlaylistChange(
      Timeline timeline,
      PlaybackInfo playbackInfo,
      @Nullable SeekPosition pendingInitialSeekPosition,
      MediaPeriodQueue queue,
      @RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Timeline.Window window,
      Timeline.Period period) {
    if (timeline.isEmpty()) {
      return new PositionUpdateForPlaylistChange(
          PlaybackInfo.getDummyPeriodForEmptyTimeline(),
          /* periodPositionUs= */ 0,
          /* requestedContentPositionUs= */ C.TIME_UNSET,
          /* forceBufferingState= */ false,
          /* endPlayback= */ true);
    }
    MediaPeriodId oldPeriodId = playbackInfo.periodId;
    Object newPeriodUid = oldPeriodId.periodUid;
    boolean shouldUseRequestedContentPosition =
        shouldUseRequestedContentPosition(playbackInfo, period, window);
    long oldContentPositionUs =
        shouldUseRequestedContentPosition
            ? playbackInfo.requestedContentPositionUs
            : playbackInfo.positionUs;
    long newContentPositionUs = oldContentPositionUs;
    int startAtDefaultPositionWindowIndex = C.INDEX_UNSET;
    boolean forceBufferingState = false;
    boolean endPlayback = false;
    if (pendingInitialSeekPosition != null) {
      // Resolve initial seek position.
      @Nullable
      Pair<Object, Long> periodPosition =
          resolveSeekPosition(
              timeline,
              pendingInitialSeekPosition,
              /* trySubsequentPeriods= */ true,
              repeatMode,
              shuffleModeEnabled,
              window,
              period);
      if (periodPosition == null) {
        // The initial seek in the empty old timeline is invalid in the new timeline.
        endPlayback = true;
        startAtDefaultPositionWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
      } else {
        // The pending seek has been resolved successfully in the new timeline.
        if (pendingInitialSeekPosition.windowPositionUs == C.TIME_UNSET) {
          startAtDefaultPositionWindowIndex =
              timeline.getPeriodByUid(periodPosition.first, period).windowIndex;
        } else {
          newPeriodUid = periodPosition.first;
          newContentPositionUs = periodPosition.second;
        }
        forceBufferingState = playbackInfo.playbackState == Player.STATE_ENDED;
      }
    } else if (playbackInfo.timeline.isEmpty()) {
      // Resolve to default position if the old timeline is empty and no seek is requested above.
      startAtDefaultPositionWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
    } else if (timeline.getIndexOfPeriod(newPeriodUid) == C.INDEX_UNSET) {
      // The current period isn't in the new timeline. Attempt to resolve a subsequent period whose
      // window we can restart from.
      @Nullable
      Object subsequentPeriodUid =
          resolveSubsequentPeriod(
              window,
              period,
              repeatMode,
              shuffleModeEnabled,
              newPeriodUid,
              playbackInfo.timeline,
              timeline);
      if (subsequentPeriodUid == null) {
        // We failed to resolve a suitable restart position but the timeline is not empty.
        endPlayback = true;
        startAtDefaultPositionWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
      } else {
        // We resolved a subsequent period. Start at the default position in the corresponding
        // window.
        startAtDefaultPositionWindowIndex =
            timeline.getPeriodByUid(subsequentPeriodUid, period).windowIndex;
      }
    } else if (shouldUseRequestedContentPosition) {
      // We previously requested a content position, but haven't used it yet. Re-resolve the
      // requested window position to the period uid and position in case they changed.
      if (oldContentPositionUs == C.TIME_UNSET) {
        startAtDefaultPositionWindowIndex =
            timeline.getPeriodByUid(newPeriodUid, period).windowIndex;
      } else {
        playbackInfo.timeline.getPeriodByUid(oldPeriodId.periodUid, period);
        long windowPositionUs = oldContentPositionUs + period.getPositionInWindowUs();
        int windowIndex = timeline.getPeriodByUid(newPeriodUid, period).windowIndex;
        Pair<Object, Long> periodPosition =
            timeline.getPeriodPosition(window, period, windowIndex, windowPositionUs);
        newPeriodUid = periodPosition.first;
        newContentPositionUs = periodPosition.second;
      }
    }

    // Set period uid for default positions and resolve position for ad resolution.
    long contentPositionForAdResolutionUs = newContentPositionUs;
    if (startAtDefaultPositionWindowIndex != C.INDEX_UNSET) {
      Pair<Object, Long> defaultPosition =
          timeline.getPeriodPosition(
              window,
              period,
              startAtDefaultPositionWindowIndex,
              /* windowPositionUs= */ C.TIME_UNSET);
      newPeriodUid = defaultPosition.first;
      contentPositionForAdResolutionUs = defaultPosition.second;
      newContentPositionUs = C.TIME_UNSET;
    }

    // Ensure ad insertion metadata is up to date.
    MediaPeriodId periodIdWithAds =
        queue.resolveMediaPeriodIdForAds(timeline, newPeriodUid, contentPositionForAdResolutionUs);
    boolean oldAndNewPeriodIdAreSame =
        oldPeriodId.periodUid.equals(newPeriodUid)
            && !oldPeriodId.isAd()
            && !periodIdWithAds.isAd();
    // Drop update if we keep playing the same content (MediaPeriod.periodUid are identical) and
    // only MediaPeriodId.nextAdGroupIndex may have changed. This postpones a potential
    // discontinuity until we reach the former next ad group position.
    MediaPeriodId newPeriodId = oldAndNewPeriodIdAreSame ? oldPeriodId : periodIdWithAds;

    long periodPositionUs = contentPositionForAdResolutionUs;
    if (newPeriodId.isAd()) {
      if (newPeriodId.equals(oldPeriodId)) {
        periodPositionUs = playbackInfo.positionUs;
      } else {
        timeline.getPeriodByUid(newPeriodId.periodUid, period);
        periodPositionUs =
            newPeriodId.adIndexInAdGroup == period.getFirstAdIndexToPlay(newPeriodId.adGroupIndex)
                ? period.getAdResumePositionUs()
                : 0;
      }
    }

    return new PositionUpdateForPlaylistChange(
        newPeriodId, periodPositionUs, newContentPositionUs, forceBufferingState, endPlayback);
  }

  private static boolean shouldUseRequestedContentPosition(
      PlaybackInfo playbackInfo, Timeline.Period period, Timeline.Window window) {
    // Only use the actual position as content position if it's not an ad and we already have
    // prepared media information. Otherwise use the requested position.
    MediaPeriodId periodId = playbackInfo.periodId;
    Timeline timeline = playbackInfo.timeline;
    return periodId.isAd()
        || timeline.isEmpty()
        || timeline.getWindow(
                timeline.getPeriodByUid(periodId.periodUid, period).windowIndex, window)
            .isPlaceholder;
  }

  /**
   * Updates pending message to a new timeline.
   *
   * @param pendingMessageInfo The pending message.
   * @param newTimeline The new timeline.
   * @param previousTimeline The previous timeline used to set the message positions.
   * @param repeatMode The current repeat mode.
   * @param shuffleModeEnabled The current shuffle mode.
   * @param window A scratch window.
   * @param period A scratch period.
   * @return Whether the message position could be resolved to the current timeline.
   */
  private static boolean resolvePendingMessagePosition(
      PendingMessageInfo pendingMessageInfo,
      Timeline newTimeline,
      Timeline previousTimeline,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Timeline.Window window,
      Timeline.Period period) {
    if (pendingMessageInfo.resolvedPeriodUid == null) {
      // Position is still unresolved. Try to find window in new timeline.
      long requestPositionUs =
          pendingMessageInfo.message.getPositionMs() == C.TIME_END_OF_SOURCE
              ? C.TIME_UNSET
              : C.msToUs(pendingMessageInfo.message.getPositionMs());
      @Nullable
      Pair<Object, Long> periodPosition =
          resolveSeekPosition(
              newTimeline,
              new SeekPosition(
                  pendingMessageInfo.message.getTimeline(),
                  pendingMessageInfo.message.getWindowIndex(),
                  requestPositionUs),
              /* trySubsequentPeriods= */ false,
              repeatMode,
              shuffleModeEnabled,
              window,
              period);
      if (periodPosition == null) {
        return false;
      }
      pendingMessageInfo.setResolvedPosition(
          /* periodIndex= */ newTimeline.getIndexOfPeriod(periodPosition.first),
          /* periodTimeUs= */ periodPosition.second,
          /* periodUid= */ periodPosition.first);
      if (pendingMessageInfo.message.getPositionMs() == C.TIME_END_OF_SOURCE) {
        resolvePendingMessageEndOfStreamPosition(newTimeline, pendingMessageInfo, window, period);
      }
      return true;
    }
    // Position has been resolved for a previous timeline. Try to find the updated period index.
    int index = newTimeline.getIndexOfPeriod(pendingMessageInfo.resolvedPeriodUid);
    if (index == C.INDEX_UNSET) {
      return false;
    }
    if (pendingMessageInfo.message.getPositionMs() == C.TIME_END_OF_SOURCE) {
      // Re-resolve end of stream in case the duration changed.
      resolvePendingMessageEndOfStreamPosition(newTimeline, pendingMessageInfo, window, period);
      return true;
    }
    pendingMessageInfo.resolvedPeriodIndex = index;
    previousTimeline.getPeriodByUid(pendingMessageInfo.resolvedPeriodUid, period);
    if (previousTimeline.getWindow(period.windowIndex, window).isPlaceholder) {
      // The position needs to be re-resolved because the window in the previous timeline wasn't
      // fully prepared.
      long windowPositionUs =
          pendingMessageInfo.resolvedPeriodTimeUs + period.getPositionInWindowUs();
      int windowIndex =
          newTimeline.getPeriodByUid(pendingMessageInfo.resolvedPeriodUid, period).windowIndex;
      Pair<Object, Long> periodPosition =
          newTimeline.getPeriodPosition(window, period, windowIndex, windowPositionUs);
      pendingMessageInfo.setResolvedPosition(
          /* periodIndex= */ newTimeline.getIndexOfPeriod(periodPosition.first),
          /* periodTimeUs= */ periodPosition.second,
          /* periodUid= */ periodPosition.first);
    }
    return true;
  }

  private static void resolvePendingMessageEndOfStreamPosition(
      Timeline timeline,
      PendingMessageInfo messageInfo,
      Timeline.Window window,
      Timeline.Period period) {
    int windowIndex = timeline.getPeriodByUid(messageInfo.resolvedPeriodUid, period).windowIndex;
    int lastPeriodIndex = timeline.getWindow(windowIndex, window).lastPeriodIndex;
    Object lastPeriodUid = timeline.getPeriod(lastPeriodIndex, period, /* setIds= */ true).uid;
    long positionUs = period.durationUs != C.TIME_UNSET ? period.durationUs - 1 : Long.MAX_VALUE;
    messageInfo.setResolvedPosition(lastPeriodIndex, positionUs, lastPeriodUid);
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
  private static Pair<Object, Long> resolveSeekPosition(
      Timeline timeline,
      SeekPosition seekPosition,
      boolean trySubsequentPeriods,
      @RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Timeline.Window window,
      Timeline.Period period) {
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
    if (timeline.equals(seekTimeline)) {
      // Our internal timeline is the seek timeline, so the mapped position is correct.
      return periodPosition;
    }
    // Attempt to find the mapped period in the internal timeline.
    int periodIndex = timeline.getIndexOfPeriod(periodPosition.first);
    if (periodIndex != C.INDEX_UNSET) {
      // We successfully located the period in the internal timeline.
      seekTimeline.getPeriodByUid(periodPosition.first, period);
      if (seekTimeline.getWindow(period.windowIndex, window).isPlaceholder) {
        // The seek timeline was using a placeholder, so we need to re-resolve using the updated
        // timeline in case the resolved position changed.
        int newWindowIndex = timeline.getPeriodByUid(periodPosition.first, period).windowIndex;
        periodPosition =
            timeline.getPeriodPosition(
                window, period, newWindowIndex, seekPosition.windowPositionUs);
      }
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
        return timeline.getPeriodPosition(
            window,
            period,
            timeline.getPeriodByUid(periodUid, period).windowIndex,
            /* windowPositionUs= */ C.TIME_UNSET);
      }
    }
    // We didn't find one. Give up.
    return null;
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

  private static boolean isRendererEnabled(Renderer renderer) {
    return renderer.getState() != Renderer.STATE_DISABLED;
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

  private static final class PositionUpdateForPlaylistChange {
    public final MediaPeriodId periodId;
    public final long periodPositionUs;
    public final long requestedContentPositionUs;
    public final boolean forceBufferingState;
    public final boolean endPlayback;

    public PositionUpdateForPlaylistChange(
        MediaPeriodId periodId,
        long periodPositionUs,
        long requestedContentPositionUs,
        boolean forceBufferingState,
        boolean endPlayback) {
      this.periodId = periodId;
      this.periodPositionUs = periodPositionUs;
      this.requestedContentPositionUs = requestedContentPositionUs;
      this.forceBufferingState = forceBufferingState;
      this.endPlayback = endPlayback;
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

  private static final class MediaSourceListUpdateMessage {

    private final List<MediaSourceList.MediaSourceHolder> mediaSourceHolders;
    private final ShuffleOrder shuffleOrder;
    private final int windowIndex;
    private final long positionUs;

    private MediaSourceListUpdateMessage(
        List<MediaSourceList.MediaSourceHolder> mediaSourceHolders,
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

  /* package */ static final class PlaybackInfoUpdate {

    private boolean hasPendingChange;

    public PlaybackInfo playbackInfo;
    public int operationAcks;
    public boolean positionDiscontinuity;
    @DiscontinuityReason public int discontinuityReason;
    public boolean hasPlayWhenReadyChangeReason;
    @PlayWhenReadyChangeReason public int playWhenReadyChangeReason;

    public PlaybackInfoUpdate(PlaybackInfo playbackInfo) {
      this.playbackInfo = playbackInfo;
    }

    public void incrementPendingOperationAcks(int operationAcks) {
      hasPendingChange |= operationAcks > 0;
      this.operationAcks += operationAcks;
    }

    public void setPlaybackInfo(PlaybackInfo playbackInfo) {
      hasPendingChange |= this.playbackInfo != playbackInfo;
      this.playbackInfo = playbackInfo;
    }

    public void setPositionDiscontinuity(@DiscontinuityReason int discontinuityReason) {
      if (positionDiscontinuity
          && this.discontinuityReason != Player.DISCONTINUITY_REASON_INTERNAL) {
        // We always prefer non-internal discontinuity reasons. We also assume that we won't report
        // more than one non-internal discontinuity per message iteration.
        Assertions.checkArgument(discontinuityReason == Player.DISCONTINUITY_REASON_INTERNAL);
        return;
      }
      hasPendingChange = true;
      positionDiscontinuity = true;
      this.discontinuityReason = discontinuityReason;
    }

    public void setPlayWhenReadyChangeReason(
        @PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
      hasPendingChange = true;
      this.hasPlayWhenReadyChangeReason = true;
      this.playWhenReadyChangeReason = playWhenReadyChangeReason;
    }
  }
}
