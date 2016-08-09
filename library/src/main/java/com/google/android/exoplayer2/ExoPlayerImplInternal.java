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
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import com.google.android.exoplayer2.ExoPlayer.ExoPlayerMessage;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.Timeline;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.PriorityHandlerThread;
import com.google.android.exoplayer2.util.StandaloneMediaClock;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Implements the internal behavior of {@link ExoPlayerImpl}.
 */
/* package */ final class ExoPlayerImplInternal implements Handler.Callback, MediaPeriod.Callback,
    TrackSelector.InvalidationListener, MediaSource.Listener {

  /**
   * Playback position information which is read on the application's thread by
   * {@link ExoPlayerImpl} and read/written internally on the player's thread.
   */
  public static final class PlaybackInfo {

    public final int periodIndex;

    public volatile long positionUs;
    public volatile long bufferedPositionUs;
    public volatile long durationUs;
    public volatile long startPositionUs;

    public PlaybackInfo(int periodIndex) {
      this.periodIndex = periodIndex;
      durationUs = C.UNSET_TIME_US;
    }

  }

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_STATE_CHANGED = 1;
  public static final int MSG_LOADING_CHANGED = 2;
  public static final int MSG_SET_PLAY_WHEN_READY_ACK = 3;
  public static final int MSG_SEEK_ACK = 4;
  public static final int MSG_POSITION_DISCONTINUITY = 5;
  public static final int MSG_SOURCE_INFO_REFRESHED = 6;
  public static final int MSG_ERROR = 7;

  // Internal messages
  private static final int MSG_SET_MEDIA_SOURCE = 0;
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_STOP = 4;
  private static final int MSG_RELEASE = 5;
  private static final int MSG_PERIOD_PREPARED = 6;
  private static final int MSG_SOURCE_CONTINUE_LOADING_REQUESTED = 7;
  private static final int MSG_TRACK_SELECTION_INVALIDATED = 8;
  private static final int MSG_CUSTOM = 9;

  private static final int PREPARING_SOURCE_INTERVAL_MS = 10;
  private static final int RENDERING_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;

  /**
   * Limits the maximum number of periods to buffer ahead of the current playing period. The
   * buffering policy normally prevents buffering too far ahead, but the policy could allow too many
   * small periods to be buffered if the period count were not limited.
   */
  private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;

  private final Renderer[] renderers;
  private final RendererCapabilities[] rendererCapabilities;
  private final TrackSelector trackSelector;
  private final LoadControl loadControl;
  private final StandaloneMediaClock standaloneMediaClock;
  private final Handler handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;

  private PlaybackInfo playbackInfo;
  private Renderer rendererMediaClockSource;
  private MediaClock rendererMediaClock;
  private MediaSource mediaSource;
  private Renderer[] enabledRenderers;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private boolean isLoading;
  private int state;
  private int customMessagesSent;
  private int customMessagesProcessed;
  private long elapsedRealtimeUs;

  private long internalPositionUs;

  private boolean isTimelineReady;
  private boolean isTimelineEnded;
  private int bufferAheadPeriodCount;
  private Period playingPeriod;
  private Period readingPeriod;
  private Period loadingPeriod;
  private long playingPeriodEndPositionUs;

  private Timeline timeline;

  public ExoPlayerImplInternal(Renderer[] renderers, TrackSelector trackSelector,
      LoadControl loadControl, boolean playWhenReady, Handler eventHandler,
      PlaybackInfo playbackInfo) {
    this.renderers = renderers;
    this.trackSelector = trackSelector;
    this.loadControl = loadControl;
    this.playWhenReady = playWhenReady;
    this.eventHandler = eventHandler;
    this.state = ExoPlayer.STATE_IDLE;
    this.playbackInfo = playbackInfo;

    rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      renderers[i].setIndex(i);
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    playingPeriodEndPositionUs = C.UNSET_TIME_US;

    standaloneMediaClock = new StandaloneMediaClock();
    enabledRenderers = new Renderer[0];

    trackSelector.init(this);

    // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
    // not normally change to this priority" is incorrect.
    internalPlaybackThread = new PriorityHandlerThread("ExoPlayerImplInternal:Handler",
        Process.THREAD_PRIORITY_AUDIO);
    internalPlaybackThread.start();
    handler = new Handler(internalPlaybackThread.getLooper(), this);
  }

  public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
    handler.obtainMessage(MSG_SET_MEDIA_SOURCE, resetPosition ? 1 : 0, 0, mediaSource)
        .sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(int periodIndex, long positionUs) {
    handler.obtainMessage(MSG_SEEK_TO, periodIndex, 0, positionUs).sendToTarget();
  }

  public void stop() {
    handler.sendEmptyMessage(MSG_STOP);
  }

  public void sendMessages(ExoPlayerMessage... messages) {
    if (released) {
      Log.w(TAG, "Ignoring messages sent after release.");
      return;
    }
    customMessagesSent++;
    handler.obtainMessage(MSG_CUSTOM, messages).sendToTarget();
  }

  public synchronized void blockingSendMessages(ExoPlayerMessage... messages) {
    if (released) {
      Log.w(TAG, "Ignoring messages sent after release.");
      return;
    }
    int messageNumber = customMessagesSent++;
    handler.obtainMessage(MSG_CUSTOM, messages).sendToTarget();
    while (customMessagesProcessed <= messageNumber) {
      try {
        wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public synchronized void release() {
    if (released) {
      return;
    }
    handler.sendEmptyMessage(MSG_RELEASE);
    while (!released) {
      try {
        wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    internalPlaybackThread.quit();
  }

  // TrackSelector.InvalidationListener implementation.

  @Override
  public void onTrackSelectionsInvalidated() {
    handler.sendEmptyMessage(MSG_TRACK_SELECTION_INVALIDATED);
  }

  // MediaPeriod.Callback implementation.

  @Override
  public void onPeriodPrepared(MediaPeriod source) {
    handler.obtainMessage(MSG_PERIOD_PREPARED, source).sendToTarget();
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod source) {
    handler.obtainMessage(MSG_SOURCE_CONTINUE_LOADING_REQUESTED, source).sendToTarget();
  }

  // Handler.Callback implementation.

  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_SET_MEDIA_SOURCE: {
          setMediaSourceInternal((MediaSource) msg.obj, msg.arg1 != 0);
          return true;
        }
        case MSG_SET_PLAY_WHEN_READY: {
          setPlayWhenReadyInternal(msg.arg1 != 0);
          return true;
        }
        case MSG_DO_SOME_WORK: {
          doSomeWork();
          return true;
        }
        case MSG_SEEK_TO: {
          seekToInternal(msg.arg1, (Long) msg.obj);
          return true;
        }
        case MSG_STOP: {
          stopInternal();
          return true;
        }
        case MSG_RELEASE: {
          releaseInternal();
          return true;
        }
        case MSG_PERIOD_PREPARED: {
          handlePeriodPrepared((MediaPeriod) msg.obj);
          return true;
        }
        case MSG_SOURCE_CONTINUE_LOADING_REQUESTED: {
          handleContinueLoadingRequested((MediaPeriod) msg.obj);
          return true;
        }
        case MSG_TRACK_SELECTION_INVALIDATED: {
          reselectTracksInternal();
          return true;
        }
        case MSG_CUSTOM: {
          sendMessagesInternal((ExoPlayerMessage[]) msg.obj);
          return true;
        }
        default:
          return false;
      }
    } catch (ExoPlaybackException e) {
      Log.e(TAG, "Renderer error.", e);
      eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
      stopInternal();
      return true;
    } catch (IOException e) {
      Log.e(TAG, "Source error.", e);
      eventHandler.obtainMessage(MSG_ERROR, ExoPlaybackException.createForSource(e)).sendToTarget();
      stopInternal();
      return true;
    } catch (RuntimeException e) {
      Log.e(TAG, "Internal runtime error.", e);
      eventHandler.obtainMessage(MSG_ERROR, ExoPlaybackException.createForUnexpected(e))
          .sendToTarget();
      stopInternal();
      return true;
    }
  }

  // MediaSource.Listener implementation.

  @Override
  public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
    try {
      eventHandler.obtainMessage(MSG_SOURCE_INFO_REFRESHED, Pair.create(timeline, manifest))
          .sendToTarget();
      handleTimelineRefreshed(timeline);
    } catch (ExoPlaybackException | IOException e) {
      Log.e(TAG, "Error handling timeline change.", e);
      eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
      stopInternal();
    }
  }

  // Private methods.

  private void setState(int state) {
    if (this.state != state) {
      this.state = state;
      eventHandler.obtainMessage(MSG_STATE_CHANGED, state, 0).sendToTarget();
    }
  }

  private void setIsLoading(boolean isLoading) {
    if (this.isLoading != isLoading) {
      this.isLoading = isLoading;
      eventHandler.obtainMessage(MSG_LOADING_CHANGED, isLoading ? 1 : 0, 0).sendToTarget();
    }
  }

  private void setMediaSourceInternal(MediaSource mediaSource, boolean resetPosition)
      throws ExoPlaybackException {
    resetInternal();
    if (resetPosition) {
      playbackInfo = new PlaybackInfo(0);
      playbackInfo.startPositionUs = C.UNSET_TIME_US;
      playbackInfo.positionUs = C.UNSET_TIME_US;
    }
    this.mediaSource = mediaSource;
    mediaSource.prepareSource(this);
    setState(ExoPlayer.STATE_BUFFERING);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void setPlayWhenReadyInternal(boolean playWhenReady) throws ExoPlaybackException {
    try {
      rebuffering = false;
      this.playWhenReady = playWhenReady;
      if (!playWhenReady) {
        stopRenderers();
        updatePlaybackPositions();
      } else {
        if (state == ExoPlayer.STATE_READY) {
          startRenderers();
          handler.sendEmptyMessage(MSG_DO_SOME_WORK);
        } else if (state == ExoPlayer.STATE_BUFFERING) {
          handler.sendEmptyMessage(MSG_DO_SOME_WORK);
        }
      }
    } finally {
      eventHandler.sendEmptyMessage(MSG_SET_PLAY_WHEN_READY_ACK);
    }
  }

  private void startRenderers() throws ExoPlaybackException {
    rebuffering = false;
    standaloneMediaClock.start();
    for (Renderer renderer : enabledRenderers) {
      renderer.start();
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    standaloneMediaClock.stop();
    for (Renderer renderer : enabledRenderers) {
      ensureStopped(renderer);
    }
  }

  private void updatePlaybackPositions() throws ExoPlaybackException {
    if (playingPeriod == null) {
      return;
    }
    MediaPeriod mediaPeriod = playingPeriod.mediaPeriod;

    // Update the duration.
    if (playbackInfo.durationUs == C.UNSET_TIME_US) {
      playbackInfo.durationUs = mediaPeriod.getDurationUs();
    }

    // Update the playback position.
    long positionUs = mediaPeriod.readDiscontinuity();
    if (positionUs != C.UNSET_TIME_US) {
      resetInternalPosition(positionUs);
    } else {
      if (rendererMediaClockSource != null && !rendererMediaClockSource.isEnded()) {
        internalPositionUs = rendererMediaClock.getPositionUs();
        standaloneMediaClock.setPositionUs(internalPositionUs);
      } else {
        internalPositionUs = standaloneMediaClock.getPositionUs();
      }
      positionUs = internalPositionUs - playingPeriod.offsetUs;
    }
    playbackInfo.positionUs = positionUs;
    elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;

    // Update the buffered position.
    long bufferedPositionUs;
    if (enabledRenderers.length == 0) {
      bufferedPositionUs = C.END_OF_SOURCE_US;
    } else {
      bufferedPositionUs = mediaPeriod.getBufferedPositionUs();
    }
    playbackInfo.bufferedPositionUs = bufferedPositionUs;
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = SystemClock.elapsedRealtime();

    updatePeriods();
    if (playingPeriod == null) {
      // We're still waiting for the first period to be prepared.
      maybeThrowPeriodPrepareError();
      scheduleNextOperation(MSG_DO_SOME_WORK, operationStartTimeMs, PREPARING_SOURCE_INTERVAL_MS);
      return;
    }

    TraceUtil.beginSection("doSomeWork");

    updatePlaybackPositions();
    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (Renderer renderer : enabledRenderers) {
      // TODO: Each renderer should return the maximum delay before which it wishes to be called
      // again. The minimum of these values should then be used as the delay before the next
      // invocation of this method.
      renderer.render(internalPositionUs, elapsedRealtimeUs);
      allRenderersEnded = allRenderersEnded && renderer.isEnded();
      // Determine whether the renderer is ready (or ended). If it's not, throw an error that's
      // preventing the renderer from making progress, if such an error exists.
      boolean rendererReadyOrEnded = renderer.isReady() || renderer.isEnded();
      if (!rendererReadyOrEnded) {
        renderer.maybeThrowStreamError();
      }
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded;
    }

    if (!allRenderersReadyOrEnded) {
      maybeThrowPeriodPrepareError();
    }

    if (allRenderersEnded
        && (playbackInfo.durationUs == C.UNSET_TIME_US
            || playbackInfo.durationUs <= playbackInfo.positionUs)
        && isTimelineEnded) {
      setState(ExoPlayer.STATE_ENDED);
      stopRenderers();
    } else if (state == ExoPlayer.STATE_BUFFERING) {
      if ((enabledRenderers.length > 0 ? allRenderersReadyOrEnded : isTimelineReady)
          && haveSufficientBuffer(rebuffering)) {
        setState(ExoPlayer.STATE_READY);
        if (playWhenReady) {
          startRenderers();
        }
      }
    } else if (state == ExoPlayer.STATE_READY) {
      if (enabledRenderers.length > 0 ? !allRenderersReadyOrEnded : !isTimelineReady) {
        rebuffering = playWhenReady;
        setState(ExoPlayer.STATE_BUFFERING);
        stopRenderers();
      }
    }

    if (state == ExoPlayer.STATE_BUFFERING) {
      for (Renderer renderer : enabledRenderers) {
        renderer.maybeThrowStreamError();
      }
    }

    handler.removeMessages(MSG_DO_SOME_WORK);
    if ((playWhenReady && state == ExoPlayer.STATE_READY) || state == ExoPlayer.STATE_BUFFERING) {
      scheduleNextOperation(MSG_DO_SOME_WORK, operationStartTimeMs, RENDERING_INTERVAL_MS);
    } else if (enabledRenderers.length != 0) {
      scheduleNextOperation(MSG_DO_SOME_WORK, operationStartTimeMs, IDLE_INTERVAL_MS);
    }

    TraceUtil.endSection();
  }

  private void scheduleNextOperation(int operationType, long thisOperationStartTimeMs,
      long intervalMs) {
    long nextOperationStartTimeMs = thisOperationStartTimeMs + intervalMs;
    long nextOperationDelayMs = nextOperationStartTimeMs - SystemClock.elapsedRealtime();
    if (nextOperationDelayMs <= 0) {
      handler.sendEmptyMessage(operationType);
    } else {
      handler.sendEmptyMessageDelayed(operationType, nextOperationDelayMs);
    }
  }

  private void seekToInternal(int periodIndex, long positionUs) throws ExoPlaybackException {
    try {
      if (positionUs == C.UNSET_TIME_US && mediaSource != null) {
        MediaSource.Position defaultStartPosition =
            mediaSource.getDefaultStartPosition(periodIndex);
        if (defaultStartPosition != null) {
          // We know the default position so seek to it now.
          periodIndex = defaultStartPosition.periodIndex;
          positionUs = defaultStartPosition.positionUs;
        }
      }

      if (periodIndex == playbackInfo.periodIndex
          && ((positionUs == C.UNSET_TIME_US && playbackInfo.positionUs == C.UNSET_TIME_US)
              || ((positionUs / 1000) == (playbackInfo.positionUs / 1000)))) {
        // Seek position equals the current position. Do nothing.
        return;
      }
      seekToPeriodPosition(periodIndex, positionUs);
    } finally {
      eventHandler.sendEmptyMessage(MSG_SEEK_ACK);
    }
  }

  private void seekToPeriodPosition(int periodIndex, long positionUs) throws ExoPlaybackException {
    if (periodIndex != playbackInfo.periodIndex) {
      playbackInfo = new PlaybackInfo(periodIndex);
      playbackInfo.startPositionUs = positionUs;
      playbackInfo.positionUs = positionUs;
      eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY, playbackInfo).sendToTarget();
    } else {
      playbackInfo.startPositionUs = positionUs;
      playbackInfo.positionUs = positionUs;
    }

    if (mediaSource == null) {
      if (positionUs != C.UNSET_TIME_US) {
        resetInternalPosition(positionUs);
      }
      return;
    }

    stopRenderers();
    rebuffering = false;

    if (positionUs == C.UNSET_TIME_US) {
      // We don't know where to seek to yet, so clear the whole timeline.
      periodIndex = Timeline.NO_PERIOD_INDEX;
    }

    // Clear the timeline, but keep the requested period if it is already prepared.
    Period period = playingPeriod;
    Period newPlayingPeriod = null;
    while (period != null) {
      if (period.index == periodIndex && period.prepared) {
        newPlayingPeriod = period;
      } else {
        period.release();
      }
      period = period.nextPeriod;
    }

    // Disable all the renderers if the period is changing.
    if (newPlayingPeriod != playingPeriod) {
      for (Renderer renderer : enabledRenderers) {
        renderer.disable();
      }
      enabledRenderers = new Renderer[0];
      rendererMediaClock = null;
      rendererMediaClockSource = null;
    }

    // Update loaded periods.
    bufferAheadPeriodCount = 0;
    if (newPlayingPeriod != null) {
      newPlayingPeriod.nextPeriod = null;
      setPlayingPeriod(newPlayingPeriod);
      updateTimelineState();
      readingPeriod = playingPeriod;
      loadingPeriod = playingPeriod;
      if (playingPeriod.hasEnabledTracks) {
        positionUs = playingPeriod.mediaPeriod.seekToUs(positionUs);
        playbackInfo.startPositionUs = positionUs;
        playbackInfo.positionUs = positionUs;
      }
      resetInternalPosition(positionUs);
      maybeContinueLoading();
    } else {
      playingPeriod = null;
      readingPeriod = null;
      loadingPeriod = null;
      if (positionUs != C.UNSET_TIME_US) {
        resetInternalPosition(positionUs);
      }
    }
    updatePlaybackPositions();
    setState(ExoPlayer.STATE_BUFFERING);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void resetInternalPosition(long periodPositionUs) throws ExoPlaybackException {
    long periodOffsetUs = playingPeriod == null ? 0 : playingPeriod.offsetUs;
    internalPositionUs = periodOffsetUs + periodPositionUs;
    standaloneMediaClock.setPositionUs(internalPositionUs);
    for (Renderer renderer : enabledRenderers) {
      renderer.resetPosition(internalPositionUs);
    }
  }

  private void stopInternal() {
    resetInternal();
    setState(ExoPlayer.STATE_IDLE);
  }

  private void releaseInternal() {
    resetInternal();
    setState(ExoPlayer.STATE_IDLE);
    synchronized (this) {
      released = true;
      notifyAll();
    }
  }

  private void resetInternal() {
    handler.removeMessages(MSG_DO_SOME_WORK);
    rebuffering = false;
    standaloneMediaClock.stop();
    rendererMediaClock = null;
    rendererMediaClockSource = null;
    for (Renderer renderer : enabledRenderers) {
      try {
        ensureStopped(renderer);
        renderer.disable();
      } catch (ExoPlaybackException | RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Stop failed.", e);
      }
    }
    enabledRenderers = new Renderer[0];
    if (mediaSource != null) {
      mediaSource.releaseSource();
      mediaSource = null;
    }
    releasePeriodsFrom(playingPeriod != null ? playingPeriod : loadingPeriod);
    playingPeriodEndPositionUs = C.UNSET_TIME_US;
    isTimelineReady = false;
    isTimelineEnded = false;
    playingPeriod = null;
    readingPeriod = null;
    loadingPeriod = null;
    timeline = null;
    bufferAheadPeriodCount = 0;
    loadControl.reset();
    setIsLoading(false);
  }

  private void sendMessagesInternal(ExoPlayerMessage[] messages) throws ExoPlaybackException {
    try {
      for (ExoPlayerMessage message : messages) {
        message.target.handleMessage(message.messageType, message.message);
      }
      if (mediaSource != null) {
        // The message may have caused something to change that now requires us to do work.
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    } finally {
      synchronized (this) {
        customMessagesProcessed++;
        notifyAll();
      }
    }
  }

  private void ensureStopped(Renderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == Renderer.STATE_STARTED) {
      renderer.stop();
    }
  }

  private void reselectTracksInternal() throws ExoPlaybackException {
    if (playingPeriod == null) {
      // We don't have tracks yet, so we don't care.
      return;
    }
    // Reselect tracks on each period in turn, until the selection changes.
    Period period = playingPeriod;
    boolean selectionsChangedForReadPeriod = true;
    while (true) {
      if (period == null || !period.prepared) {
        // The reselection did not change any prepared periods.
        return;
      }
      if (period.selectTracks()) {
        // Selected tracks have changed for this period.
        break;
      }
      if (period == readingPeriod) {
        // The track reselection didn't affect any period that has been read.
        selectionsChangedForReadPeriod = false;
      }
      period = period.nextPeriod;
    }

    if (selectionsChangedForReadPeriod) {
      // Release everything after the playing period because a renderer may have read data from a
      // track whose selection has now changed.
      releasePeriodsFrom(playingPeriod.nextPeriod);
      playingPeriod.nextPeriod = null;
      readingPeriod = playingPeriod;
      loadingPeriod = playingPeriod;
      playingPeriodEndPositionUs = C.UNSET_TIME_US;
      bufferAheadPeriodCount = 0;

      // Update streams for the new selection, recreating all streams if reading ahead.
      boolean recreateStreams = readingPeriod != playingPeriod;
      TrackSelectionArray playingPeriodOldTrackSelections = playingPeriod.periodTrackSelections;
      playingPeriod.updatePeriodTrackSelection(playbackInfo.positionUs, loadControl,
          recreateStreams);

      int enabledRendererCount = 0;
      boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
        TrackSelection oldSelection = playingPeriodOldTrackSelections.get(i);
        TrackSelection newSelection = playingPeriod.trackSelections.get(i);
        if (newSelection != null) {
          enabledRendererCount++;
        }
        if (rendererWasEnabledFlags[i]
            && (recreateStreams || !Util.areEqual(oldSelection, newSelection))) {
          // We need to disable the renderer so that we can enable it with its new stream.
          if (renderer == rendererMediaClockSource) {
            // The renderer is providing the media clock.
            if (newSelection == null) {
              // The renderer won't be re-enabled. Sync standaloneMediaClock so that it can take
              // over timing responsibilities.
              standaloneMediaClock.setPositionUs(rendererMediaClock.getPositionUs());
            }
            rendererMediaClock = null;
            rendererMediaClockSource = null;
          }
          ensureStopped(renderer);
          renderer.disable();
        }
      }
      trackSelector.onSelectionActivated(playingPeriod.trackSelectionData);
      enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
    } else {
      // Release and re-prepare/buffer periods after the one whose selection changed.
      loadingPeriod = period;
      period = loadingPeriod.nextPeriod;
      while (period != null) {
        period.release();
        period = period.nextPeriod;
        bufferAheadPeriodCount--;
      }
      loadingPeriod.nextPeriod = null;
      long positionUs = Math.max(0, internalPositionUs - loadingPeriod.offsetUs);
      loadingPeriod.updatePeriodTrackSelection(positionUs, loadControl, false);
    }
    maybeContinueLoading();
    updatePlaybackPositions();
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  public boolean haveSufficientBuffer(boolean rebuffering) {
    if (loadingPeriod == null) {
      return false;
    }
    long positionUs = internalPositionUs - loadingPeriod.offsetUs;
    long bufferedPositionUs =
        !loadingPeriod.prepared ? 0 : loadingPeriod.mediaPeriod.getBufferedPositionUs();
    if (bufferedPositionUs == C.END_OF_SOURCE_US) {
      if (loadingPeriod.isLast) {
        return true;
      }
      bufferedPositionUs = loadingPeriod.mediaPeriod.getDurationUs();
    }
    return loadControl.shouldStartPlayback(bufferedPositionUs - positionUs, rebuffering);
  }

  public void maybeThrowPeriodPrepareError() throws IOException {
    if (loadingPeriod != null && !loadingPeriod.prepared
        && (readingPeriod == null || readingPeriod.nextPeriod == loadingPeriod)) {
      for (Renderer renderer : enabledRenderers) {
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }
      loadingPeriod.mediaPeriod.maybeThrowPrepareError();
    }
  }

  public void handleTimelineRefreshed(Timeline timeline) throws ExoPlaybackException, IOException {
    Timeline oldTimeline = this.timeline;
    this.timeline = timeline;

    // Update the loaded periods to take into account the new timeline.
    if (playingPeriod != null) {
      int index = timeline.getIndexOfPeriod(playingPeriod.id);
      if (index == Timeline.NO_PERIOD_INDEX) {
        int newPlayingPeriodIndex =
            mediaSource.getNewPlayingPeriodIndex(playingPeriod.index, oldTimeline);
        if (newPlayingPeriodIndex == Timeline.NO_PERIOD_INDEX) {
          // There is no period to play, so stop the player.
          stopInternal();
          return;
        }

        // Release all loaded periods and seek to the new playing period index.
        releasePeriodsFrom(playingPeriod);
        playingPeriod = null;

        MediaSource.Position defaultStartPosition =
            mediaSource.getDefaultStartPosition(newPlayingPeriodIndex);
        if (defaultStartPosition != null) {
          seekToPeriodPosition(defaultStartPosition.periodIndex, defaultStartPosition.positionUs);
        } else {
          seekToPeriodPosition(newPlayingPeriodIndex, C.UNSET_TIME_US);
        }
        return;
      }

      // The playing period is also in the new timeline. Update index and isLast on each loaded
      // period until a period is found that has changed.
      int periodCount = timeline.getPeriodCount();
      playingPeriod.index = index;
      playingPeriod.isLast = timeline.isFinal() && index == periodCount - 1;

      Period previousPeriod = playingPeriod;
      boolean seenReadingPeriod = false;
      bufferAheadPeriodCount = 0;
      while (previousPeriod.nextPeriod != null) {
        Period period = previousPeriod.nextPeriod;
        index++;
        if (!period.id.equals(timeline.getPeriodId(index))) {
          if (!seenReadingPeriod) {
            // Renderers may have read a period that has been removed, so release all loaded periods
            // and seek to the playing period index.
            index = playingPeriod.index;
            releasePeriodsFrom(playingPeriod);
            playingPeriod = null;
            seekToPeriodPosition(index, 0);
            return;
          }

          // Update the loading period to be the latest period that is still valid.
          loadingPeriod = previousPeriod;
          loadingPeriod.nextPeriod = null;

          // Release the rest of the timeline.
          releasePeriodsFrom(period);
          break;
        }

        bufferAheadPeriodCount++;
        period.index = index;
        period.isLast = timeline.isFinal() && index == periodCount - 1;
        if (period == readingPeriod) {
          seenReadingPeriod = true;
        }
        previousPeriod = period;
      }
    } else if (loadingPeriod != null) {
      Object id = loadingPeriod.id;
      int index = timeline.getIndexOfPeriod(id);
      if (index == Timeline.NO_PERIOD_INDEX) {
        loadingPeriod.release();
        loadingPeriod = null;
        bufferAheadPeriodCount = 0;
      } else {
        int periodCount = timeline.getPeriodCount();
        loadingPeriod.index = index;
        loadingPeriod.isLast = timeline.isFinal() && index == periodCount - 1;
      }
    }

    // TODO[playlists]: Signal the identifier discontinuity, even if the index hasn't changed.
    if (oldTimeline != null) {
      int newPlayingIndex = playingPeriod != null ? playingPeriod.index
          : loadingPeriod != null ? loadingPeriod.index
              : mediaSource.getNewPlayingPeriodIndex(playbackInfo.periodIndex, oldTimeline);
      if (newPlayingIndex != Timeline.NO_PERIOD_INDEX
          && newPlayingIndex != playbackInfo.periodIndex) {
        long oldPositionUs = playbackInfo.positionUs;
        playbackInfo = new PlaybackInfo(newPlayingIndex);
        playbackInfo.startPositionUs = oldPositionUs;
        updatePlaybackPositions();
        eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY, playbackInfo).sendToTarget();
      }
    }
  }

  public void updatePeriods() throws ExoPlaybackException, IOException {
    if (timeline == null) {
      // We're waiting to get information about periods.
      return;
    }

    // Update the loading period.
    if (loadingPeriod == null || (loadingPeriod.isFullyBuffered() && !loadingPeriod.isLast
        && bufferAheadPeriodCount < MAXIMUM_BUFFER_AHEAD_PERIODS)) {
      int periodIndex = loadingPeriod == null ? playbackInfo.periodIndex : loadingPeriod.index + 1;
      long startPositionUs = playbackInfo.positionUs;
      if (loadingPeriod != null || startPositionUs == C.UNSET_TIME_US) {
        // We are starting to load the next period or seeking to the default position, so request a
        // period and position from the source.
        MediaSource.Position defaultStartPosition =
            mediaSource.getDefaultStartPosition(periodIndex);
        if (defaultStartPosition != null) {
          periodIndex = defaultStartPosition.periodIndex;
          startPositionUs = defaultStartPosition.positionUs;
        } else {
          startPositionUs = C.UNSET_TIME_US;
        }
      }

      MediaPeriod mediaPeriod;
      if (startPositionUs != C.UNSET_TIME_US
          && (mediaPeriod = mediaSource.createPeriod(periodIndex)) != null) {
        Period newPeriod = new Period(renderers, rendererCapabilities, trackSelector, mediaPeriod,
            timeline.getPeriodId(periodIndex), periodIndex, startPositionUs);
        newPeriod.isLast = timeline.isFinal() && periodIndex == timeline.getPeriodCount() - 1;
        if (loadingPeriod != null) {
          loadingPeriod.setNextPeriod(newPeriod);
        }
        bufferAheadPeriodCount++;
        loadingPeriod = newPeriod;
        setIsLoading(true);
        loadingPeriod.mediaPeriod.preparePeriod(this, loadControl.getAllocator(), startPositionUs);
      }
    }

    if (loadingPeriod == null || loadingPeriod.isFullyBuffered()) {
      setIsLoading(false);
    } else if (loadingPeriod != null && loadingPeriod.needsContinueLoading) {
      maybeContinueLoading();
    }

    if (playingPeriod == null) {
      // We're waiting for the first period to be prepared.
      return;
    }

    // Update the playing and reading periods.
    if (playingPeriodEndPositionUs == C.UNSET_TIME_US && playingPeriod.isFullyBuffered()) {
      playingPeriodEndPositionUs = playingPeriod.offsetUs
          + playingPeriod.mediaPeriod.getDurationUs();
    }
    while (playingPeriod != readingPeriod && playingPeriod.nextPeriod != null
        && internalPositionUs >= playingPeriod.nextPeriod.offsetUs) {
      // All enabled renderers' streams have been read to the end, and the playback position reached
      // the end of the playing period, so advance playback to the next period.
      playingPeriod.release();
      setPlayingPeriod(playingPeriod.nextPeriod);
      bufferAheadPeriodCount--;
      playbackInfo = new PlaybackInfo(playingPeriod.index);
      playbackInfo.startPositionUs = playingPeriod.startPositionUs;
      updatePlaybackPositions();
      eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY, playbackInfo).sendToTarget();
    }
    updateTimelineState();
    if (readingPeriod == null) {
      // The renderers have their final SampleStreams.
      return;
    }
    for (Renderer renderer : enabledRenderers) {
      if (!renderer.hasReadStreamToEnd()) {
        return;
      }
    }
    if (readingPeriod.nextPeriod != null && readingPeriod.nextPeriod.prepared) {
      TrackSelectionArray oldTrackSelections = readingPeriod.trackSelections;
      readingPeriod = readingPeriod.nextPeriod;
      TrackSelectionArray newTrackSelections = readingPeriod.trackSelections;
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        TrackSelection oldSelection = oldTrackSelections.get(i);
        TrackSelection newSelection = newTrackSelections.get(i);
        if (oldSelection != null) {
          if (newSelection != null) {
            // Replace the renderer's SampleStream so the transition to playing the next period can
            // be seamless.
            Format[] formats = new Format[newSelection.length()];
            for (int j = 0; j < formats.length; j++) {
              formats[j] = newSelection.getFormat(j);
            }
            renderer.replaceStream(formats, readingPeriod.sampleStreams[i], readingPeriod.offsetUs);
          } else {
            // The renderer will be disabled when transitioning to playing the next period. Mark the
            // SampleStream as final to play out any remaining data.
            renderer.setCurrentStreamIsFinal();
          }
        }
      }
    } else if (readingPeriod.isLast) {
      readingPeriod = null;
      for (Renderer renderer : enabledRenderers) {
        renderer.setCurrentStreamIsFinal();
      }
    }
  }

  public void handlePeriodPrepared(MediaPeriod period) throws ExoPlaybackException {
    if (loadingPeriod == null || loadingPeriod.mediaPeriod != period) {
      // Stale event.
      return;
    }
    loadingPeriod.handlePrepared(loadingPeriod.startPositionUs, loadControl);
    if (playingPeriod == null) {
      // This is the first prepared period, so start playing it.
      readingPeriod = loadingPeriod;
      setPlayingPeriod(readingPeriod);
      if (playbackInfo.startPositionUs == C.UNSET_TIME_US) {
        // Update the playback info when seeking to a default position.
        playbackInfo = new PlaybackInfo(playingPeriod.index);
        playbackInfo.startPositionUs = playingPeriod.startPositionUs;
        resetInternalPosition(playbackInfo.startPositionUs);
        updatePlaybackPositions();
        eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY, playbackInfo).sendToTarget();
      }
      updateTimelineState();
    }
    maybeContinueLoading();
  }

  public void handleContinueLoadingRequested(MediaPeriod period) {
    if (loadingPeriod == null || loadingPeriod.mediaPeriod != period) {
      return;
    }
    maybeContinueLoading();
  }

  private void maybeContinueLoading() {
    long nextLoadPositionUs = loadingPeriod.mediaPeriod.getNextLoadPositionUs();
    if (nextLoadPositionUs != C.END_OF_SOURCE_US) {
      long positionUs = internalPositionUs - loadingPeriod.offsetUs;
      long bufferedDurationUs = nextLoadPositionUs - positionUs;
      boolean continueLoading = loadControl.shouldContinueLoading(bufferedDurationUs);
      setIsLoading(continueLoading);
      if (continueLoading) {
        loadingPeriod.needsContinueLoading = false;
        loadingPeriod.mediaPeriod.continueLoading(positionUs);
      } else {
        loadingPeriod.needsContinueLoading = true;
      }
    } else {
      setIsLoading(false);
    }
  }

  private void releasePeriodsFrom(Period period) {
    while (period != null) {
      period.release();
      period = period.nextPeriod;
    }
  }

  private void setPlayingPeriod(Period period) throws ExoPlaybackException {
    int enabledRendererCount = 0;
    boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
      TrackSelection newSelection = period.trackSelections.get(i);
      if (newSelection != null) {
        // The renderer should be enabled when playing the new period.
        enabledRendererCount++;
      } else if (rendererWasEnabledFlags[i]) {
        // The renderer should be disabled when playing the new period.
        if (renderer == rendererMediaClockSource) {
          // Sync standaloneMediaClock so that it can take over timing responsibilities.
          standaloneMediaClock.setPositionUs(rendererMediaClock.getPositionUs());
          rendererMediaClock = null;
          rendererMediaClockSource = null;
        }
        ensureStopped(renderer);
        renderer.disable();
      }
    }

    trackSelector.onSelectionActivated(period.trackSelectionData);
    playingPeriod = period;
    playingPeriodEndPositionUs = C.UNSET_TIME_US;
    enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
  }

  private void updateTimelineState() {
    isTimelineReady = playingPeriodEndPositionUs == C.UNSET_TIME_US
        || internalPositionUs < playingPeriodEndPositionUs
        || (playingPeriod.nextPeriod != null && playingPeriod.nextPeriod.prepared);
    isTimelineEnded = playingPeriod.isLast;
  }

  private void enableRenderers(boolean[] rendererWasEnabledFlags, int enabledRendererCount)
      throws ExoPlaybackException {
    enabledRenderers = new Renderer[enabledRendererCount];
    enabledRendererCount = 0;
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      TrackSelection newSelection = playingPeriod.trackSelections.get(i);
      if (newSelection != null) {
        enabledRenderers[enabledRendererCount++] = renderer;
        if (renderer.getState() == Renderer.STATE_DISABLED) {
          // The renderer needs enabling with its new track selection.
          boolean playing = playWhenReady && state == ExoPlayer.STATE_READY;
          // Consider as joining only if the renderer was previously disabled.
          boolean joining = !rendererWasEnabledFlags[i] && playing;
          // Build an array of formats contained by the selection.
          Format[] formats = new Format[newSelection.length()];
          for (int j = 0; j < formats.length; j++) {
            formats[j] = newSelection.getFormat(j);
          }
          // Enable the renderer.
          renderer.enable(formats, playingPeriod.sampleStreams[i], internalPositionUs, joining,
              playingPeriod.offsetUs);
          MediaClock mediaClock = renderer.getMediaClock();
          if (mediaClock != null) {
            if (rendererMediaClock != null) {
              throw ExoPlaybackException.createForUnexpected(
                  new IllegalStateException("Multiple renderer media clocks enabled."));
            }
            rendererMediaClock = mediaClock;
            rendererMediaClockSource = renderer;
          }
          // Start the renderer if playing.
          if (playing) {
            renderer.start();
          }
        }
      }
    }
  }

  /**
   * Represents a {@link MediaPeriod} with information required to play it as part of a timeline.
   */
  private static final class Period {

    public final MediaPeriod mediaPeriod;
    public final Object id;
    public final SampleStream[] sampleStreams;
    public final long startPositionUs;

    public int index;
    public boolean isLast;
    public boolean prepared;
    public boolean hasEnabledTracks;
    public long offsetUs;
    public Period nextPeriod;
    public boolean needsContinueLoading;

    private final Renderer[] renderers;
    private final RendererCapabilities[] rendererCapabilities;
    private final TrackSelector trackSelector;

    private Object trackSelectionData;
    private TrackSelectionArray trackSelections;
    private TrackSelectionArray periodTrackSelections;

    public Period(Renderer[] renderers, RendererCapabilities[] rendererCapabilities,
        TrackSelector trackSelector, MediaPeriod mediaPeriod, Object id, int index,
        long positionUs) {
      this.renderers = renderers;
      this.rendererCapabilities = rendererCapabilities;
      this.trackSelector = trackSelector;
      this.mediaPeriod = mediaPeriod;
      this.id = Assertions.checkNotNull(id);
      sampleStreams = new SampleStream[renderers.length];
      startPositionUs = positionUs;
      this.index = index;
    }

    public void setNextPeriod(Period nextPeriod) {
      this.nextPeriod = nextPeriod;
      nextPeriod.offsetUs = offsetUs + mediaPeriod.getDurationUs();
    }

    public boolean isFullyBuffered() {
      return prepared
          && (!hasEnabledTracks || mediaPeriod.getBufferedPositionUs() == C.END_OF_SOURCE_US);
    }

    public void handlePrepared(long positionUs, LoadControl loadControl)
        throws ExoPlaybackException {
      prepared = true;
      selectTracks();
      updatePeriodTrackSelection(positionUs, loadControl, false);
    }

    public boolean selectTracks() throws ExoPlaybackException {
      Pair<TrackSelectionArray, Object> result =
          trackSelector.selectTracks(rendererCapabilities, mediaPeriod.getTrackGroups());
      TrackSelectionArray newTrackSelections = result.first;
      if (newTrackSelections.equals(periodTrackSelections)) {
        return false;
      }
      trackSelections = newTrackSelections;
      trackSelectionData = result.second;
      return true;
    }

    public void updatePeriodTrackSelection(long positionUs, LoadControl loadControl,
        boolean forceRecreateStreams) throws ExoPlaybackException {
      // Populate lists of streams that are being disabled/newly enabled.
      ArrayList<SampleStream> oldStreams = new ArrayList<>();
      ArrayList<TrackSelection> newSelections = new ArrayList<>();
      for (int i = 0; i < trackSelections.length; i++) {
        TrackSelection oldSelection =
            periodTrackSelections == null ? null : periodTrackSelections.get(i);
        TrackSelection newSelection = trackSelections.get(i);
        if (forceRecreateStreams || !Util.areEqual(oldSelection, newSelection)) {
          if (oldSelection != null) {
            oldStreams.add(sampleStreams[i]);
          }
          if (newSelection != null) {
            newSelections.add(newSelection);
          }
        }
      }

      // Disable streams on the period and get new streams for updated/newly-enabled tracks.
      SampleStream[] newStreams = mediaPeriod.selectTracks(oldStreams, newSelections, positionUs);
      periodTrackSelections = trackSelections;
      hasEnabledTracks = false;
      for (int i = 0; i < trackSelections.length; i++) {
        TrackSelection selection = trackSelections.get(i);
        if (selection != null) {
          hasEnabledTracks = true;
          int index = newSelections.indexOf(selection);
          if (index != -1) {
            sampleStreams[i] = newStreams[index];
          } else {
            // This selection/stream is unchanged.
          }
        } else {
          sampleStreams[i] = null;
        }
      }

      // The track selection has changed.
      loadControl.onTrackSelections(renderers, mediaPeriod.getTrackGroups(), trackSelections);
    }

    public void release() {
      try {
        mediaPeriod.releasePeriod();
      } catch (RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Period release failed.", e);
      }
    }

  }

}
