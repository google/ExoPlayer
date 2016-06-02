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

import com.google.android.exoplayer.ExoPlayer.ExoPlayerMessage;
import com.google.android.exoplayer.TrackSelector.InvalidationListener;
import com.google.android.exoplayer.util.PriorityHandlerThread;
import com.google.android.exoplayer.util.TraceUtil;
import com.google.android.exoplayer.util.Util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
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
  private static final int MSG_SET_SOURCE_PROVIDER = 0;
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_STOP = 4;
  private static final int MSG_RELEASE = 5;
  private static final int MSG_TRACK_SELECTION_INVALIDATED = 6;
  private static final int MSG_CUSTOM = 7;

  private static final int PREPARING_SOURCE_INTERVAL_MS = 10;
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
  private boolean preparedSource;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private int state;
  private int customMessagesSent;
  private int customMessagesProcessed;
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
    this.durationUs = C.UNSET_TIME_US;
    this.bufferedPositionUs = C.UNSET_TIME_US;

    for (int i = 0; i < renderers.length; i++) {
      renderers[i].setIndex(i);
    }

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

  public long getCurrentPosition() {
    return pendingSeekCount.get() > 0 ? lastSeekPositionMs : (positionUs / 1000);
  }

  public long getBufferedPosition() {
    long bufferedPositionUs = this.bufferedPositionUs;
    return bufferedPositionUs == C.UNSET_TIME_US ? ExoPlayer.UNKNOWN_TIME
        : bufferedPositionUs / 1000;
  }

  public long getDuration() {
    long durationUs = this.durationUs;
    return durationUs == C.UNSET_TIME_US ? ExoPlayer.UNKNOWN_TIME : durationUs / 1000;
  }

  public void setSourceProvider(SampleSourceProvider sourceProvider) {
    handler.obtainMessage(MSG_SET_SOURCE_PROVIDER, sourceProvider).sendToTarget();
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

  @Override
  public void onTrackSelectionsInvalidated() {
    handler.sendEmptyMessage(MSG_TRACK_SELECTION_INVALIDATED);
  }

  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_SET_SOURCE_PROVIDER: {
          setSourceProviderInternal((SampleSourceProvider) msg.obj);
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
          sendMessagesInternal((ExoPlayerMessage[]) msg.obj);
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

  private void setState(int state) {
    if (this.state != state) {
      this.state = state;
      eventHandler.obtainMessage(MSG_STATE_CHANGED, state, 0).sendToTarget();
    }
  }

  private boolean isReadyOrEnded(TrackRenderer renderer) {
    return renderer.isReady() || renderer.isEnded();
  }

  private boolean haveSufficientBuffer() {
    long minBufferDurationUs = rebuffering ? minRebufferUs : minBufferUs;
    return minBufferDurationUs <= 0
        || bufferedPositionUs == C.UNSET_TIME_US
        || bufferedPositionUs == C.END_OF_SOURCE_US
        || bufferedPositionUs >= positionUs + minBufferDurationUs
        || (durationUs != C.UNSET_TIME_US && bufferedPositionUs >= durationUs);
  }

  private void setSourceProviderInternal(SampleSourceProvider sourceProvider) {
    resetInternal();
    // TODO[playlists]: Create and use sources after the first one.
    this.source = sourceProvider.createSource(0);
    setState(ExoPlayer.STATE_BUFFERING);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
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
    if (rendererMediaClockSource != null && !rendererMediaClockSource.isEnded()) {
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
        && durationUs != C.UNSET_TIME_US ? durationUs : sourceBufferedPositionUs;
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    if (!preparedSource) {
      preparedSource = source.prepare(positionUs);
      if (preparedSource) {
        durationUs = source.getDurationUs();
        selectTracksInternal();
        resumeInternal();
      } else {
        // We're still waiting for the source to be prepared.
        scheduleNextOperation(MSG_DO_SOME_WORK, operationStartTimeMs, PREPARING_SOURCE_INTERVAL_MS);
      }
      return;
    }

    TraceUtil.beginSection("doSomeWork");

    if (enabledRenderers.length > 0) {
      // Process reset if there is one, else update the position.
      if (!checkForSourceResetInternal()) {
        updatePositionUs();
      }
      updateBufferedPositionUs();
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
        renderer.maybeThrowStreamError();
      }
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded;
    }

    if (allRenderersEnded && (durationUs == C.UNSET_TIME_US || durationUs <= positionUs)) {
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
      if (!preparedSource) {
        return;
      }

      if (enabledRenderers.length > 0) {
        for (TrackRenderer renderer : enabledRenderers) {
          ensureStopped(renderer);
        }
        source.seekToUs(positionUs);
        checkForSourceResetInternal();
      }

      resumeInternal();
    } finally {
      pendingSeekCount.decrementAndGet();
    }
  }

  private void resumeInternal() throws ExoPlaybackException {
    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (TrackRenderer renderer : renderers) {
      allRenderersEnded = allRenderersEnded && renderer.isEnded();
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && isReadyOrEnded(renderer);
    }

    updateBufferedPositionUs();
    if (allRenderersEnded && (durationUs == C.UNSET_TIME_US || durationUs <= positionUs)) {
      setState(ExoPlayer.STATE_ENDED);
    } else {
      setState(allRenderersReadyOrEnded && haveSufficientBuffer() ? ExoPlayer.STATE_READY
          : ExoPlayer.STATE_BUFFERING);
    }

    // Start the renderers if ready, and schedule the first piece of work.
    if (playWhenReady && state == ExoPlayer.STATE_READY) {
      startRenderers();
    }
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private boolean checkForSourceResetInternal() throws ExoPlaybackException {
    long resetPositionUs = source.readReset();
    if (resetPositionUs == C.UNSET_TIME_US) {
      return false;
    }
    positionUs = resetPositionUs;
    standaloneMediaClock.setPositionUs(resetPositionUs);
    for (TrackRenderer renderer : enabledRenderers) {
      renderer.reset(resetPositionUs);
    }
    return true;
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
    preparedSource = false;
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
      } catch (ExoPlaybackException | RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Stop failed.", e);
      }
    }
    if (source != null) {
      try {
        source.release();
      } catch (RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Source release failed.", e);
      }
      source = null;
    }
  }

  private void sendMessagesInternal(ExoPlayerMessage[] messages) throws ExoPlaybackException {
    try {
      for (ExoPlayerMessage message : messages) {
        message.target.handleMessage(message.messageType, message.message);
      }
      if (preparedSource) {
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

  private void selectTracksInternal() throws ExoPlaybackException {
    TrackGroupArray groups = source.getTrackGroups();

    Pair<TrackSelectionArray, Object> result = trackSelector.selectTracks(renderers, groups);
    TrackSelectionArray newTrackSelections = result.first;

    if (newTrackSelections.equals(trackSelections)) {
      trackSelector.onSelectionActivated(result.second);
      return;
    }

    // Disable any renderers whose selections have changed, adding the corresponding TrackStream
    // instances to oldStreams. Where we need to obtain a new TrackStream instance for a renderer,
    // we add the corresponding TrackSelection to newSelections.
    ArrayList<TrackStream> oldStreams = new ArrayList<>();
    ArrayList<TrackSelection> newSelections = new ArrayList<>();
    boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
    int enabledRendererCount = 0;
    for (int i = 0; i < renderers.length; i++) {
      TrackRenderer renderer = renderers[i];
      TrackSelection oldSelection = trackSelections == null ? null : trackSelections.get(i);
      TrackSelection newSelection = newTrackSelections.get(i);
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
          oldStreams.add(renderer.disable());
        }
        if (newSelection != null) {
          newSelections.add(newSelection);
        }
      }
    }

    // Update the source selection.
    TrackStream[] newStreams = source.selectTracks(oldStreams, newSelections, positionUs);
    trackSelector.onSelectionActivated(result.second);
    trackSelections = newTrackSelections;

    // Enable renderers with their new selections.
    enabledRenderers = new TrackRenderer[enabledRendererCount];
    enabledRendererCount = 0;
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
          // Build an array of formats contained by the new selection.
          Format[] formats = new Format[newSelection.length];
          for (int j = 0; j < formats.length; j++) {
            formats[j] = groups.get(newSelection.group).getFormat(newSelection.getTrack(j));
          }
          // Enable the renderer.
          int newStreamIndex = newSelections.indexOf(newSelection);
          renderer.enable(formats, newStreams[newStreamIndex], positionUs, joining);
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

  private void reselectTracksInternal() throws ExoPlaybackException {
    if (!preparedSource) {
      // We don't have tracks yet, so we don't care.
      return;
    }
    selectTracksInternal();
    updateBufferedPositionUs();
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void ensureStopped(TrackRenderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == TrackRenderer.STATE_STARTED) {
      renderer.stop();
    }
  }

}
