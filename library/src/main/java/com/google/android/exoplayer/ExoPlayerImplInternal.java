/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer;

import com.google.android.exoplayer.ExoPlayer.ExoPlayerComponent;
import com.google.android.exoplayer.TrackSelector.InvalidationListener;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.PriorityHandlerThread;
import com.google.android.exoplayer.util.TraceUtil;
import com.google.android.exoplayer.util.Util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements the internal behavior of {@link ExoPlayerImpl}.
 */
// TODO[REFACTOR]: Make sure renderer errors that will prevent prepare from being called again are
// always propagated properly.
/* package */ final class ExoPlayerImplInternal implements Handler.Callback, InvalidationListener {

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_STATE_CHANGED = 1;
  public static final int MSG_SET_PLAY_WHEN_READY_ACK = 2;
  public static final int MSG_ERROR = 3;

  // Internal messages
  private static final int MSG_PREPARE = 1;
  private static final int MSG_INCREMENTAL_PREPARE = 2;
  private static final int MSG_SET_PLAY_WHEN_READY = 3;
  private static final int MSG_STOP = 4;
  private static final int MSG_RELEASE = 5;
  private static final int MSG_SEEK_TO = 6;
  private static final int MSG_DO_SOME_WORK = 7;
  private static final int MSG_TRACK_SELECTION_INVALIDATED = 8;
  private static final int MSG_CUSTOM = 9;

  private static final int PREPARE_INTERVAL_MS = 10;
  private static final int RENDERING_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;

  private final TrackSelector trackSelector;
  private final TrackRenderer[] renderers;
  private final StandaloneMediaClock standaloneMediaClock;
  private final long minBufferUs;
  private final long minRebufferUs;
  private final Handler handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;
  private final AtomicInteger pendingSeekCount;

  private TrackSelectionArray trackSelections;
  private TrackRenderer rendererMediaClockSource;
  private MediaClock rendererMediaClock;
  private SampleSource source;
  private TrackRenderer[] enabledRenderers;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private int state;
  private int customMessagesSent = 0;
  private int customMessagesProcessed = 0;
  private long lastSeekPositionMs;
  private long elapsedRealtimeUs;

  private volatile long durationUs;
  private volatile long positionUs;
  private volatile long bufferedPositionUs;

  public ExoPlayerImplInternal(TrackRenderer[] renderers, TrackSelector trackSelector,
      int minBufferMs, int minRebufferMs, boolean playWhenReady, Handler eventHandler) {
    this.renderers = renderers;
    this.trackSelector = trackSelector;
    this.minBufferUs = minBufferMs * 1000L;
    this.minRebufferUs = minRebufferMs * 1000L;
    this.playWhenReady = playWhenReady;
    this.eventHandler = eventHandler;
    this.state = ExoPlayer.STATE_IDLE;
    this.durationUs = C.UNKNOWN_TIME_US;
    this.bufferedPositionUs = C.UNKNOWN_TIME_US;
    standaloneMediaClock = new StandaloneMediaClock();
    pendingSeekCount = new AtomicInteger();
    enabledRenderers = new TrackRenderer[0];

    trackSelector.init(this);

    // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
    // not normally change to this priority" is incorrect.
    internalPlaybackThread = new PriorityHandlerThread("ExoPlayerImplInternal:Handler",
        Process.THREAD_PRIORITY_AUDIO);
    internalPlaybackThread.start();
    handler = new Handler(internalPlaybackThread.getLooper(), this);
  }

  public Looper getPlaybackLooper() {
    return internalPlaybackThread.getLooper();
  }

  public long getCurrentPosition() {
    return pendingSeekCount.get() > 0 ? lastSeekPositionMs : (positionUs / 1000);
  }

  public long getBufferedPosition() {
    long bufferedPositionUs = this.bufferedPositionUs;
    return bufferedPositionUs == C.UNKNOWN_TIME_US ? ExoPlayer.UNKNOWN_TIME
        : bufferedPositionUs / 1000;
  }

  public long getDuration() {
    long durationUs = this.durationUs;
    return durationUs == C.UNKNOWN_TIME_US ? ExoPlayer.UNKNOWN_TIME : durationUs / 1000;
  }

  public void prepare(SampleSource sampleSource) {
    handler.obtainMessage(MSG_PREPARE, sampleSource).sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(long positionMs) {
    lastSeekPositionMs = positionMs;
    pendingSeekCount.incrementAndGet();
    handler.obtainMessage(MSG_SEEK_TO, Util.getTopInt(positionMs),
        Util.getBottomInt(positionMs)).sendToTarget();
  }

  public void stop() {
    handler.sendEmptyMessage(MSG_STOP);
  }

  public void sendMessage(ExoPlayerComponent target, int messageType, Object message) {
    customMessagesSent++;
    handler.obtainMessage(MSG_CUSTOM, messageType, 0, Pair.create(target, message)).sendToTarget();
  }

  public synchronized void blockingSendMessage(ExoPlayerComponent target, int messageType,
      Object message) {
    if (released) {
      Log.w(TAG, "Sent message(" + messageType + ") after release. Message ignored.");
      return;
    }
    int messageNumber = customMessagesSent++;
    handler.obtainMessage(MSG_CUSTOM, messageType, 0, Pair.create(target, message)).sendToTarget();
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

  @Override
  public void onTrackSelectionsInvalidated() {
    handler.sendEmptyMessage(MSG_TRACK_SELECTION_INVALIDATED);
  }

  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_PREPARE: {
          prepareInternal((SampleSource) msg.obj);
          return true;
        }
        case MSG_INCREMENTAL_PREPARE: {
          incrementalPrepareInternal();
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
          seekToInternal(Util.getLong(msg.arg1, msg.arg2));
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
        case MSG_CUSTOM: {
          sendMessageInternal(msg.arg1, msg.obj);
          return true;
        }
        case MSG_TRACK_SELECTION_INVALIDATED: {
          reselectTracksInternal();
          return true;
        }
        default:
          return false;
      }
    } catch (ExoPlaybackException e) {
      Log.e(TAG, "Internal track renderer error.", e);
      eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
      stopInternal();
      return true;
    } catch (IOException e) {
      Log.e(TAG, "Source track renderer error.", e);
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

  private void setState(int state) {
    if (this.state != state) {
      this.state = state;
      eventHandler.obtainMessage(MSG_STATE_CHANGED, state, 0).sendToTarget();
    }
  }

  private void prepareInternal(SampleSource sampleSource) throws ExoPlaybackException, IOException {
    resetInternal();
    setState(ExoPlayer.STATE_PREPARING);
    this.source = sampleSource;
    incrementalPrepareInternal();
  }

  private void incrementalPrepareInternal() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    if (!source.prepare(positionUs)) {
      // We're still waiting for the source to be prepared.
      scheduleNextOperation(MSG_INCREMENTAL_PREPARE, operationStartTimeMs, PREPARE_INTERVAL_MS);
      return;
    }

    durationUs = source.getDurationUs();
    selectTracksInternal(true);

    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (TrackRenderer renderer : renderers) {
      allRenderersEnded = allRenderersEnded && renderer.isEnded();
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && isReadyOrEnded(renderer);
    }

    if (allRenderersEnded && (durationUs == C.UNKNOWN_TIME_US || durationUs <= positionUs)) {
      // We don't expect this case, but handle it anyway.
      setState(ExoPlayer.STATE_ENDED);
    } else {
      setState(allRenderersReadyOrEnded && haveSufficientBuffer() ? ExoPlayer.STATE_READY
          : ExoPlayer.STATE_BUFFERING);
    }

    // Start the renderers if required, and schedule the first piece of work.
    if (playWhenReady && state == ExoPlayer.STATE_READY) {
      startRenderers();
    }
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private boolean isReadyOrEnded(TrackRenderer renderer) {
    return renderer.isReady() || renderer.isEnded();
  }

  private boolean haveSufficientBuffer() {
    long minBufferDurationUs = rebuffering ? minRebufferUs : minBufferUs;
    return minBufferDurationUs <= 0
        || bufferedPositionUs == C.UNKNOWN_TIME_US
        || bufferedPositionUs == C.END_OF_SOURCE_US
        || bufferedPositionUs >= positionUs + minBufferDurationUs
        || (durationUs != C.UNKNOWN_TIME_US && bufferedPositionUs >= durationUs);
  }

  private void setPlayWhenReadyInternal(boolean playWhenReady) throws ExoPlaybackException {
    try {
      rebuffering = false;
      this.playWhenReady = playWhenReady;
      if (!playWhenReady) {
        stopRenderers();
        updatePositionUs();
      } else {
        if (state == ExoPlayer.STATE_READY) {
          startRenderers();
          handler.sendEmptyMessage(MSG_DO_SOME_WORK);
        } else if (state == ExoPlayer.STATE_BUFFERING) {
          handler.sendEmptyMessage(MSG_DO_SOME_WORK);
        }
      }
    } finally {
      eventHandler.obtainMessage(MSG_SET_PLAY_WHEN_READY_ACK).sendToTarget();
    }
  }

  private void startRenderers() throws ExoPlaybackException {
    rebuffering = false;
    standaloneMediaClock.start();
    for (TrackRenderer renderer : enabledRenderers) {
      renderer.start();
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    standaloneMediaClock.stop();
    for (TrackRenderer renderer : enabledRenderers) {
      ensureStopped(renderer);
    }
  }

  private void updatePositionUs() {
    if (rendererMediaClock != null
        && rendererMediaClockSource.getState() != TrackRenderer.STATE_DISABLED) {
      positionUs = rendererMediaClock.getPositionUs();
      standaloneMediaClock.setPositionUs(positionUs);
    } else {
      positionUs = standaloneMediaClock.getPositionUs();
    }
    elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;
  }

  private void updateBufferedPositionUs() {
    long sourceBufferedPositionUs = enabledRenderers.length > 0 ? source.getBufferedPositionUs()
        : C.END_OF_SOURCE_US;
    bufferedPositionUs = sourceBufferedPositionUs == C.END_OF_SOURCE_US
        && durationUs != C.UNKNOWN_TIME_US ? durationUs : sourceBufferedPositionUs;
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    TraceUtil.beginSection("doSomeWork");
    long operationStartTimeMs = SystemClock.elapsedRealtime();

    // Process resets.
    for (TrackRenderer renderer : enabledRenderers) {
      renderer.checkForReset();
    }

    updatePositionUs();
    updateBufferedPositionUs();
    if (enabledRenderers.length > 0) {
      source.continueBuffering(positionUs);
    }

    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (TrackRenderer renderer : enabledRenderers) {
      // TODO: Each renderer should return the maximum delay before which it wishes to be
      // invoked again. The minimum of these values should then be used as the delay before the next
      // invocation of this method.
      renderer.render(positionUs, elapsedRealtimeUs);
      allRenderersEnded = allRenderersEnded && renderer.isEnded();
      // Determine whether the renderer is ready (or ended). If it's not, throw an error that's
      // preventing the renderer from making progress, if such an error exists.
      boolean rendererReadyOrEnded = isReadyOrEnded(renderer);
      if (!rendererReadyOrEnded) {
        renderer.maybeThrowError();
      }
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded;
    }

    if (allRenderersEnded && (durationUs == C.UNKNOWN_TIME_US || durationUs <= positionUs)) {
      setState(ExoPlayer.STATE_ENDED);
      stopRenderers();
    } else if (state == ExoPlayer.STATE_BUFFERING && allRenderersReadyOrEnded
        && haveSufficientBuffer()) {
      setState(ExoPlayer.STATE_READY);
      if (playWhenReady) {
        startRenderers();
      }
    } else if (state == ExoPlayer.STATE_READY && !allRenderersReadyOrEnded) {
      rebuffering = playWhenReady;
      setState(ExoPlayer.STATE_BUFFERING);
      stopRenderers();
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

  private void seekToInternal(long positionMs) throws ExoPlaybackException {
    try {
      if (positionMs == (positionUs / 1000)) {
        // Seek is to the current position. Do nothing.
        return;
      }

      rebuffering = false;
      positionUs = positionMs * 1000;
      standaloneMediaClock.stop();
      standaloneMediaClock.setPositionUs(positionUs);
      if (state == ExoPlayer.STATE_IDLE || state == ExoPlayer.STATE_PREPARING) {
        return;
      }

      if (enabledRenderers != null) {
        for (TrackRenderer renderer : enabledRenderers) {
          ensureStopped(renderer);
        }
        source.seekToUs(positionUs);
        for (TrackRenderer renderer : enabledRenderers) {
          renderer.checkForReset();
        }
      }

      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    } finally {
      pendingSeekCount.decrementAndGet();
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
    handler.removeMessages(MSG_INCREMENTAL_PREPARE);
    rebuffering = false;
    trackSelections = null;
    standaloneMediaClock.stop();
    rendererMediaClock = null;
    rendererMediaClockSource = null;
    enabledRenderers = new TrackRenderer[0];
    for (TrackRenderer renderer : renderers) {
      try {
        ensureStopped(renderer);
        if (renderer.getState() == TrackRenderer.STATE_ENABLED) {
          renderer.disable();
        }
      } catch (ExoPlaybackException e) {
        // There's nothing we can do.
        Log.e(TAG, "Stop failed.", e);
      } catch (RuntimeException e) {
        // Ditto.
        Log.e(TAG, "Stop failed.", e);
      }
    }
    if (source != null) {
      source.release();
      source = null;
    }
  }

  private <T> void sendMessageInternal(int what, Object obj)
      throws ExoPlaybackException {
    try {
      @SuppressWarnings("unchecked")
      Pair<ExoPlayerComponent, Object> targetAndMessage = (Pair<ExoPlayerComponent, Object>) obj;
      targetAndMessage.first.handleMessage(what, targetAndMessage.second);
      if (state != ExoPlayer.STATE_IDLE && state != ExoPlayer.STATE_PREPARING) {
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

  private void selectTracksInternal(boolean initialSelection) throws ExoPlaybackException {
    TrackGroupArray groups = source.getTrackGroups();

    Pair<TrackSelectionArray, Object> result = trackSelector.selectTracks(renderers, groups);
    TrackSelectionArray newSelections = result.first;

    if (newSelections.equals(trackSelections)) {
      trackSelector.onSelectionActivated(result.second);
      // No changes to the track selections.
      return;
    }

    if (initialSelection) {
      Assertions.checkState(source.getState() == SampleSource.STATE_SELECTING_TRACKS);
    } else {
      Assertions.checkState(source.getState() == SampleSource.STATE_READING);
      source.startTrackSelection();
    }

    // We disable all renderers whose track selections have changed, then enable renderers with new
    // track selections during a second pass. Doing all disables before any enables is necessary
    // because the new track selection for some renderer X may have the same track group as the old
    // selection for some other renderer Y. Trying to enable X before disabling Y would fail, since
    // sources do not support being enabled more than once per track group at a time.

    // Disable renderers whose track selections have changed.
    boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
    int enabledRendererCount = 0;
    for (int i = 0; i < renderers.length; i++) {
      TrackRenderer renderer = renderers[i];
      TrackSelection oldSelection = trackSelections == null ? null : trackSelections.get(i);
      TrackSelection newSelection = newSelections.get(i);
      if (newSelection != null) {
        enabledRendererCount++;
      }
      rendererWasEnabledFlags[i] = renderer.getState() != TrackRenderer.STATE_DISABLED;
      if (!Util.areEqual(oldSelection, newSelection)) {
        // The track selection has changed for this renderer.
        if (rendererWasEnabledFlags[i]) {
          // We need to disable the renderer so that we can enable it with its new selection.
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
          if (renderer.getState() == TrackRenderer.STATE_ENABLED) {
            TrackStream trackStream = renderer.disable();
            source.unselectTrack(trackStream);
          }
        }
      }
    }

    trackSelector.onSelectionActivated(result.second);
    trackSelections = newSelections;
    enabledRenderers = new TrackRenderer[enabledRendererCount];
    enabledRendererCount = 0;

    // Enable renderers with their new track selections.
    for (int i = 0; i < renderers.length; i++) {
      TrackRenderer renderer = renderers[i];
      TrackSelection newSelection = trackSelections.get(i);
      if (newSelection != null) {
        enabledRenderers[enabledRendererCount++] = renderer;
        if (renderer.getState() == TrackRenderer.STATE_DISABLED) {
          // The renderer needs enabling with its new track selection.
          boolean playing = playWhenReady && state == ExoPlayer.STATE_READY;
          // Consider as joining only if the renderer was previously disabled.
          boolean joining = !rendererWasEnabledFlags[i] && playing;
          // Enable the source and obtain the stream for the renderer to consume.
          TrackStream trackStream = source.selectTrack(newSelection, positionUs);
          // Build an array of formats contained by the new selection.
          Format[] formats = new Format[newSelection.length];
          for (int j = 0; j < formats.length; j++) {
            formats[j] = groups.get(newSelection.group).getFormat(newSelection.getTrack(j));
          }
          // Enable the renderer.
          renderer.enable(formats, trackStream, positionUs, joining);
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

    source.endTrackSelection(positionUs);
    updateBufferedPositionUs();
  }

  private void reselectTracksInternal() throws ExoPlaybackException {
    if (state == ExoPlayer.STATE_IDLE || state == ExoPlayer.STATE_PREPARING) {
      // We don't have tracks yet, so we don't care.
      return;
    }
    selectTracksInternal(false);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void ensureStopped(TrackRenderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == TrackRenderer.STATE_STARTED) {
      renderer.stop();
    }
  }

}
