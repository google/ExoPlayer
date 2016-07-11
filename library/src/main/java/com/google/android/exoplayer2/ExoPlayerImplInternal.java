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

import com.google.android.exoplayer2.ExoPlayer.ExoPlayerMessage;
import com.google.android.exoplayer2.TrackSelector.InvalidationListener;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.PriorityHandlerThread;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Implements the internal behavior of {@link ExoPlayerImpl}.
 */
/* package */ final class ExoPlayerImplInternal implements Handler.Callback, MediaPeriod.Callback,
    InvalidationListener {

  /**
   * Playback position information which is read on the application's thread by
   * {@link ExoPlayerImpl} and read/written internally on the player's thread.
   */
  public static final class PlaybackInfo {

    public final int periodIndex;

    public volatile long positionUs;
    public volatile long bufferedPositionUs;
    public volatile long durationUs;

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
  public static final int MSG_SET_MEDIA_SOURCE_ACK = 4;
  public static final int MSG_SEEK_ACK = 5;
  public static final int MSG_PERIOD_CHANGED = 6;
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
   * Limits the maximum number of sources to buffer ahead of the current source in the timeline. The
   * source buffering policy normally prevents buffering too far ahead, but the policy could allow
   * too many very small sources to be buffered if the buffered source count were not limited.
   */
  private static final int MAXIMUM_BUFFER_AHEAD_SOURCES = 100;

  private final TrackSelector trackSelector;
  private final LoadControl loadControl;
  private final StandaloneMediaClock standaloneMediaClock;
  private final Handler handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;
  private final Timeline timeline;

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

  public ExoPlayerImplInternal(Renderer[] renderers, TrackSelector trackSelector,
      LoadControl loadControl, boolean playWhenReady, Handler eventHandler) {
    this.trackSelector = trackSelector;
    this.loadControl = loadControl;
    this.playWhenReady = playWhenReady;
    this.eventHandler = eventHandler;
    this.state = ExoPlayer.STATE_IDLE;

    for (int i = 0; i < renderers.length; i++) {
      renderers[i].setIndex(i);
    }

    standaloneMediaClock = new StandaloneMediaClock();
    enabledRenderers = new Renderer[0];
    timeline = new Timeline(renderers);
    playbackInfo = new PlaybackInfo(0);

    trackSelector.init(this);

    // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
    // not normally change to this priority" is incorrect.
    internalPlaybackThread = new PriorityHandlerThread("ExoPlayerImplInternal:Handler",
        Process.THREAD_PRIORITY_AUDIO);
    internalPlaybackThread.start();
    handler = new Handler(internalPlaybackThread.getLooper(), this);
  }

  public void setMediaSource(MediaSource mediaSource) {
    handler.obtainMessage(MSG_SET_MEDIA_SOURCE, mediaSource).sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(int periodIndex, long positionUs) {
    handler.obtainMessage(MSG_SEEK_TO, periodIndex, -1, positionUs).sendToTarget();
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

  // InvalidationListener implementation.

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
          setMediaSourceInternal((MediaSource) msg.obj);
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
          timeline.handlePeriodPrepared((MediaPeriod) msg.obj);
          return true;
        }
        case MSG_SOURCE_CONTINUE_LOADING_REQUESTED: {
          timeline.handleContinueLoadingRequested((MediaPeriod) msg.obj);
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

  private void setMediaSourceInternal(MediaSource mediaSource) {
    try {
      resetInternal();
      this.mediaSource = mediaSource;
      setState(ExoPlayer.STATE_BUFFERING);
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    } finally {
      eventHandler.sendEmptyMessage(MSG_SET_MEDIA_SOURCE_ACK);
    }
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
    MediaPeriod mediaPeriod = timeline.getPeriod();
    if (mediaPeriod == null) {
      return;
    }

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
      positionUs = internalPositionUs - timeline.playingPeriod.offsetUs;
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

    timeline.updatePeriods();
    if (timeline.getPeriod() == null) {
      // We're still waiting for the first source to be prepared.
      timeline.maybeThrowPeriodPrepareError();
      scheduleNextOperation(MSG_DO_SOME_WORK, operationStartTimeMs, PREPARING_SOURCE_INTERVAL_MS);
      return;
    }

    TraceUtil.beginSection("doSomeWork");

    updatePlaybackPositions();
    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (Renderer renderer : enabledRenderers) {
      // TODO: Each renderer should return the maximum delay before which it wishes to be invoked
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
      timeline.maybeThrowPeriodPrepareError();
    }

    if (allRenderersEnded && (playbackInfo.durationUs == C.UNSET_TIME_US
        || playbackInfo.durationUs <= playbackInfo.positionUs) && timeline.isEnded) {
      setState(ExoPlayer.STATE_ENDED);
      stopRenderers();
    } else if (state == ExoPlayer.STATE_BUFFERING) {
      if ((enabledRenderers.length > 0 ? allRenderersReadyOrEnded : timeline.isReady)
          && timeline.haveSufficientBuffer(rebuffering)) {
        setState(ExoPlayer.STATE_READY);
        if (playWhenReady) {
          startRenderers();
        }
      }
    } else if (state == ExoPlayer.STATE_READY) {
      if (enabledRenderers.length > 0 ? !allRenderersReadyOrEnded : !timeline.isReady) {
        rebuffering = playWhenReady;
        setState(ExoPlayer.STATE_BUFFERING);
        stopRenderers();
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

  private void seekToInternal(int periodIndex, long seekPositionUs) throws ExoPlaybackException {
    try {
      if (periodIndex == playbackInfo.periodIndex
          && (seekPositionUs / 1000) == (playbackInfo.positionUs / 1000)) {
        // Seek position equals the current position to the nearest millisecond. Do nothing.
        return;
      }

      stopRenderers();
      rebuffering = false;

      seekPositionUs = timeline.seekTo(periodIndex, seekPositionUs);
      if (periodIndex != playbackInfo.periodIndex) {
        playbackInfo = new PlaybackInfo(periodIndex);
        playbackInfo.positionUs = seekPositionUs;
        eventHandler.obtainMessage(MSG_PERIOD_CHANGED, playbackInfo).sendToTarget();
      } else {
        playbackInfo.positionUs = seekPositionUs;
      }

      updatePlaybackPositions();
      if (mediaSource != null) {
        setState(ExoPlayer.STATE_BUFFERING);
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    } finally {
      eventHandler.sendEmptyMessage(MSG_SEEK_ACK);
    }
  }

  private void resetInternalPosition(long periodPositionUs) throws ExoPlaybackException {
    long sourceOffsetUs = timeline.playingPeriod == null ? 0 : timeline.playingPeriod.offsetUs;
    internalPositionUs = sourceOffsetUs + periodPositionUs;
    standaloneMediaClock.setPositionUs(internalPositionUs);
    for (Renderer renderer : enabledRenderers) {
      renderer.reset(internalPositionUs);
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
    mediaSource = null;
    timeline.reset();
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
    if (timeline.getPeriod() == null) {
      // We don't have tracks yet, so we don't care.
      return;
    }
    timeline.reselectTracks();
    updatePlaybackPositions();
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  /**
   * Keeps track of the {@link Period}s of media being played in the timeline.
   */
  private final class Timeline {

    private final Renderer[] renderers;

    public boolean isReady;
    public boolean isEnded;

    private Period playingPeriod;
    private Period readingPeriod;
    private Period loadingPeriod;

    private int pendingPeriodIndex;
    private long playingPeriodEndPositionUs;

    public Timeline(Renderer[] renderers) {
      this.renderers = renderers;
      playingPeriodEndPositionUs = C.UNSET_TIME_US;
    }

    public MediaPeriod getPeriod() throws ExoPlaybackException {
      return playingPeriod == null ? null : playingPeriod.mediaPeriod;
    }

    public boolean haveSufficientBuffer(boolean rebuffering) {
      if (loadingPeriod == null) {
        return false;
      }
      long positionUs = internalPositionUs - loadingPeriod.offsetUs;
      long bufferedPositionUs = !loadingPeriod.prepared ? 0
          : loadingPeriod.mediaPeriod.getBufferedPositionUs();
      if (bufferedPositionUs == C.END_OF_SOURCE_US) {
        int periodCount = mediaSource.getPeriodCount();
        if (periodCount != MediaSource.UNKNOWN_PERIOD_COUNT
            && loadingPeriod.index == periodCount - 1) {
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

    public void updatePeriods() throws ExoPlaybackException, IOException {
      // TODO[playlists]: Let MediaSource invalidate periods that are already loaded.

      // Update the loading period.
      int periodCount = mediaSource.getPeriodCount();
      if (loadingPeriod == null
          || (loadingPeriod.isFullyBuffered() && loadingPeriod.index
              - (playingPeriod != null ? playingPeriod.index : 0) < MAXIMUM_BUFFER_AHEAD_SOURCES)) {
        // Try and obtain the next period to start loading.
        int periodIndex = loadingPeriod == null ? pendingPeriodIndex : loadingPeriod.index + 1;
        if (periodCount == MediaSource.UNKNOWN_PERIOD_COUNT || periodIndex < periodCount) {
          // Attempt to create the next period.
          MediaPeriod mediaPeriod = mediaSource.createPeriod(periodIndex);
          if (mediaPeriod != null) {
            Period newPeriod = new Period(renderers, trackSelector, mediaPeriod, periodIndex);
            if (loadingPeriod != null) {
              loadingPeriod.setNextPeriod(newPeriod);
            }
            loadingPeriod = newPeriod;
            long startPositionUs = playingPeriod == null ? playbackInfo.positionUs : 0;
            setIsLoading(true);
            loadingPeriod.mediaPeriod.prepare(ExoPlayerImplInternal.this,
                loadControl.getAllocator(), startPositionUs);
          }
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
        // All enabled renderers' streams have been read to the end, and the playback position
        // reached the end of the playing period, so advance playback to the next period.
        playingPeriod.release();
        setPlayingPeriod(playingPeriod.nextPeriod);
        playbackInfo = new PlaybackInfo(playingPeriod.index);
        updatePlaybackPositions();
        eventHandler.obtainMessage(MSG_PERIOD_CHANGED, playbackInfo).sendToTarget();
      }
      updateTimelineState();
      if (readingPeriod == null) {
        // The renderers have their final TrackStreams.
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
        TrackGroupArray groups = readingPeriod.mediaPeriod.getTrackGroups();
        for (int i = 0; i < renderers.length; i++) {
          Renderer renderer = renderers[i];
          TrackSelection oldSelection = oldTrackSelections.get(i);
          TrackSelection newSelection = newTrackSelections.get(i);
          if (oldSelection != null) {
            if (newSelection != null) {
              // Replace the renderer's TrackStream so the transition to playing the next period can
              // be seamless.
              Format[] formats = new Format[newSelection.length];
              for (int j = 0; j < formats.length; j++) {
                formats[j] = groups.get(newSelection.group).getFormat(newSelection.getTrack(j));
              }
              renderer.replaceTrackStream(formats, readingPeriod.trackStreams[i],
                  readingPeriod.offsetUs);
            } else {
              // The renderer will be disabled when transitioning to playing the next period. Mark
              // the TrackStream as final to play out any remaining data.
              renderer.setCurrentTrackStreamIsFinal();
            }
          }
        }
      } else if (periodCount != MediaSource.UNKNOWN_PERIOD_COUNT
          && readingPeriod.index == periodCount - 1) {
        readingPeriod = null;
        // This is the last period, so signal the renderers to read the end of the stream.
        for (Renderer renderer : enabledRenderers) {
          renderer.setCurrentTrackStreamIsFinal();
        }
      }
    }

    public void handlePeriodPrepared(MediaPeriod period) throws ExoPlaybackException {
      if (loadingPeriod == null || loadingPeriod.mediaPeriod != period) {
        // Stale event.
        return;
      }
      long startPositionUs = playingPeriod == null ? playbackInfo.positionUs : 0;
      loadingPeriod.handlePrepared(startPositionUs, loadControl);
      if (playingPeriod == null) {
        // This is the first prepared period, so start playing it.
        readingPeriod = loadingPeriod;
        setPlayingPeriod(readingPeriod);
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

    public long seekTo(int periodIndex, long seekPositionUs) throws ExoPlaybackException {
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

      if (newPlayingPeriod != null) {
        newPlayingPeriod.nextPeriod = null;
        setPlayingPeriod(newPlayingPeriod);
        updateTimelineState();
        readingPeriod = playingPeriod;
        loadingPeriod = playingPeriod;
        if (playingPeriod.hasEnabledTracks) {
          seekPositionUs = playingPeriod.mediaPeriod.seekToUs(seekPositionUs);
        }
        resetInternalPosition(seekPositionUs);
        maybeContinueLoading();
      } else {
        for (Renderer renderer : enabledRenderers) {
          ensureStopped(renderer);
          renderer.disable();
        }
        enabledRenderers = new Renderer[0];
        playingPeriod = null;
        readingPeriod = null;
        loadingPeriod = null;
        pendingPeriodIndex = periodIndex;
        resetInternalPosition(seekPositionUs);
      }
      return seekPositionUs;
    }

    public void reselectTracks() throws ExoPlaybackException {
      // Reselect tracks on each period in turn, until the selection changes.
      Period period = playingPeriod;
      boolean selectionsChangedForReadPeriod = true;
      while (true) {
        if (period == null || !period.prepared) {
          // The reselection did not change any prepared periods.
          return;
        }
        if (period.selectTracks()) {
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
        period = playingPeriod.nextPeriod;
        while (period != null) {
          period.release();
          period = period.nextPeriod;
        }
        playingPeriod.nextPeriod = null;
        readingPeriod = playingPeriod;
        loadingPeriod = playingPeriod;
        playingPeriodEndPositionUs = C.UNSET_TIME_US;

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
        }
        loadingPeriod.nextPeriod = null;
        long positionUs = Math.max(0, internalPositionUs - loadingPeriod.offsetUs);
        loadingPeriod.updatePeriodTrackSelection(positionUs, loadControl, false);
      }
      maybeContinueLoading();
    }

    public void reset() {
      Period period = playingPeriod != null ? playingPeriod : loadingPeriod;
      while (period != null) {
        period.release();
        period = period.nextPeriod;
      }
      isReady = false;
      isEnded = false;
      playingPeriod = null;
      readingPeriod = null;
      loadingPeriod = null;
      playingPeriodEndPositionUs = C.UNSET_TIME_US;
      pendingPeriodIndex = 0;
      playbackInfo = new PlaybackInfo(0);
      eventHandler.obtainMessage(MSG_PERIOD_CHANGED, playbackInfo).sendToTarget();
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
      isReady = playingPeriodEndPositionUs == C.UNSET_TIME_US
          || internalPositionUs < playingPeriodEndPositionUs
          || (playingPeriod.nextPeriod != null && playingPeriod.nextPeriod.prepared);
      int periodCount = mediaSource.getPeriodCount();
      isEnded = periodCount != MediaSource.UNKNOWN_PERIOD_COUNT
          && playingPeriod.index == periodCount - 1;
    }

    private void enableRenderers(boolean[] rendererWasEnabledFlags, int enabledRendererCount)
        throws ExoPlaybackException {
      enabledRenderers = new Renderer[enabledRendererCount];
      enabledRendererCount = 0;
      TrackGroupArray trackGroups = playingPeriod.mediaPeriod.getTrackGroups();
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
            Format[] formats = new Format[newSelection.length];
            for (int j = 0; j < formats.length; j++) {
              formats[j] = trackGroups.get(newSelection.group).getFormat(newSelection.getTrack(j));
            }
            // Enable the renderer.
            renderer.enable(formats, playingPeriod.trackStreams[i], internalPositionUs, joining,
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

  }

  /**
   * Represents a {@link MediaPeriod} with information required to play it as part of a timeline.
   */
  private static final class Period {

    private final Renderer[] renderers;
    private final TrackSelector trackSelector;

    public final MediaPeriod mediaPeriod;
    public final int index;
    public final TrackStream[] trackStreams;

    public boolean prepared;
    public boolean hasEnabledTracks;
    public long offsetUs;
    public Period nextPeriod;
    public boolean needsContinueLoading;

    private Object trackSelectionData;
    private TrackSelectionArray trackSelections;
    private TrackSelectionArray periodTrackSelections;

    public Period(Renderer[] renderers, TrackSelector trackSelector, MediaPeriod mediaPeriod,
        int index) {
      this.renderers = renderers;
      this.trackSelector = trackSelector;
      this.mediaPeriod = mediaPeriod;
      this.index = index;
      trackStreams = new TrackStream[renderers.length];
    }

    public void setNextPeriod(Period nextPeriod) {
      this.nextPeriod = nextPeriod;
      nextPeriod.offsetUs = offsetUs + mediaPeriod.getDurationUs();
    }

    public boolean isFullyBuffered() {
      return prepared && (!hasEnabledTracks
          || mediaPeriod.getBufferedPositionUs() == C.END_OF_SOURCE_US);
    }

    public void handlePrepared(long positionUs, LoadControl loadControl)
        throws ExoPlaybackException {
      prepared = true;
      selectTracks();
      updatePeriodTrackSelection(positionUs, loadControl, false);
    }

    public boolean selectTracks() throws ExoPlaybackException {
      Pair<TrackSelectionArray, Object> result =
          trackSelector.selectTracks(renderers, mediaPeriod.getTrackGroups());
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
      ArrayList<TrackStream> oldStreams = new ArrayList<>();
      ArrayList<TrackSelection> newSelections = new ArrayList<>();
      for (int i = 0; i < trackSelections.length; i++) {
        TrackSelection oldSelection =
            periodTrackSelections == null ? null : periodTrackSelections.get(i);
        TrackSelection newSelection = trackSelections.get(i);
        if (forceRecreateStreams || !Util.areEqual(oldSelection, newSelection)) {
          if (oldSelection != null) {
            oldStreams.add(trackStreams[i]);
          }
          if (newSelection != null) {
            newSelections.add(newSelection);
          }
        }
      }

      // Disable streams on the period and get new streams for updated/newly-enabled tracks.
      TrackStream[] newStreams = mediaPeriod.selectTracks(oldStreams, newSelections, positionUs);
      periodTrackSelections = trackSelections;
      hasEnabledTracks = false;
      for (int i = 0; i < trackSelections.length; i++) {
        TrackSelection selection = trackSelections.get(i);
        if (selection != null) {
          hasEnabledTracks = true;
          int index = newSelections.indexOf(selection);
          if (index != -1) {
            trackStreams[i] = newStreams[index];
          } else {
            // This selection/stream is unchanged.
          }
        } else {
          trackStreams[i] = null;
        }
      }

      // The track selection has changed.
      loadControl.onTrackSelections(renderers, mediaPeriod.getTrackGroups(), trackSelections);
    }

    public void release() {
      try {
        mediaPeriod.release();
      } catch (RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Period release failed.", e);
      }
    }

  }

}
