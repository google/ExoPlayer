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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements the internal behavior of {@link ExoPlayerImpl}.
 */
// TODO[REFACTOR]: Make sure renderer errors that will prevent prepare from being called again are
// always propagated properly.
/* package */ final class ExoPlayerImplInternal implements Handler.Callback {

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_PREPARED = 1;
  public static final int MSG_STATE_CHANGED = 2;
  public static final int MSG_SET_PLAY_WHEN_READY_ACK = 3;
  public static final int MSG_ERROR = 4;

  // Internal messages
  private static final int MSG_PREPARE = 1;
  private static final int MSG_INCREMENTAL_PREPARE = 2;
  private static final int MSG_SET_PLAY_WHEN_READY = 3;
  private static final int MSG_STOP = 4;
  private static final int MSG_RELEASE = 5;
  private static final int MSG_SEEK_TO = 6;
  private static final int MSG_DO_SOME_WORK = 7;
  private static final int MSG_SET_RENDERER_SELECTED_TRACK = 8;
  private static final int MSG_CUSTOM = 9;

  private static final int PREPARE_INTERVAL_MS = 10;
  private static final int RENDERING_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;

  private final TrackRenderer[] renderers;
  private final TrackRenderer rendererMediaClockSource;
  private final MediaClock rendererMediaClock;
  private final StandaloneMediaClock standaloneMediaClock;
  private final long minBufferUs;
  private final long minRebufferUs;
  private final List<TrackRenderer> enabledRenderers;
  private final int[] selectedTrackIndices;
  private final TrackSelection[][] trackSelections;
  private final Format[][][] trackFormats;
  private final Handler handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;
  private final AtomicInteger pendingSeekCount;

  private SampleSource source;
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

  public ExoPlayerImplInternal(TrackRenderer[] renderers, int minBufferMs, int minRebufferMs,
      boolean playWhenReady, int[] selectedTrackIndices, Handler eventHandler) {
    this.renderers = renderers;
    this.minBufferUs = minBufferMs * 1000L;
    this.minRebufferUs = minRebufferMs * 1000L;
    this.playWhenReady = playWhenReady;
    this.selectedTrackIndices = Arrays.copyOf(selectedTrackIndices, selectedTrackIndices.length);
    this.eventHandler = eventHandler;
    this.state = ExoPlayer.STATE_IDLE;
    this.durationUs = C.UNKNOWN_TIME_US;
    this.bufferedPositionUs = C.UNKNOWN_TIME_US;

    MediaClock rendererMediaClock = null;
    TrackRenderer rendererMediaClockSource = null;
    for (int i = 0; i < renderers.length; i++) {
      renderers[i].setIndex(i);
      MediaClock mediaClock = renderers[i].getMediaClock();
      if (mediaClock != null) {
        Assertions.checkState(rendererMediaClock == null);
        rendererMediaClock = mediaClock;
        rendererMediaClockSource = renderers[i];
        break;
      }
    }
    this.rendererMediaClock = rendererMediaClock;
    this.rendererMediaClockSource = rendererMediaClockSource;

    standaloneMediaClock = new StandaloneMediaClock();
    pendingSeekCount = new AtomicInteger();
    enabledRenderers = new ArrayList<>(renderers.length);
    trackSelections = new TrackSelection[renderers.length][];
    trackFormats = new Format[renderers.length][][];
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
    return bufferedPositionUs == C.UNKNOWN_TIME_US ? ExoPlayer.UNKNOWN_TIME
        : bufferedPositionUs / 1000;
  }

  public long getDuration() {
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

  public void setRendererSelectedTrack(int rendererIndex, int trackIndex) {
    handler.obtainMessage(MSG_SET_RENDERER_SELECTED_TRACK, rendererIndex, trackIndex)
        .sendToTarget();
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
        case MSG_SET_RENDERER_SELECTED_TRACK: {
          setRendererSelectedTrackInternal(msg.arg1, msg.arg2);
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
    bufferedPositionUs = source.getBufferedPositionUs();
    TrackGroup[] trackGroups = source.getTrackGroups();

    // The maximum number of tracks that one renderer can support is the total number of tracks in
    // all groups, plus possibly one adaptive track per group.
    int maxTrackCount = trackGroups.length;
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      maxTrackCount += trackGroups[groupIndex].length;
    }
    // Construct tracks for each renderer.
    Format[][] externalTrackFormats = new Format[renderers.length][];
    for (int rendererIndex = 0; rendererIndex < renderers.length; rendererIndex++) {
      TrackRenderer renderer = renderers[rendererIndex];
      int rendererTrackCount = 0;
      Format[] rendererExternalTrackFormats = new Format[maxTrackCount];
      TrackSelection[] rendererTrackSelections = new TrackSelection[maxTrackCount];
      Format[][] rendererTrackFormats = new Format[maxTrackCount][];
      for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
        TrackGroup trackGroup = trackGroups[groupIndex];
        // TODO[REFACTOR]: This should check that the renderer is capable of adaptive playback, in
        // addition to checking that the group is adaptive.
        if (trackGroup.adaptive) {
          // Try and build an adaptive track.
          int adaptiveTrackIndexCount = 0;
          int[] adaptiveTrackIndices = new int[trackGroup.length];
          Format[] adaptiveTrackFormats = new Format[trackGroup.length];
          for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
            Format trackFormat = trackGroup.getFormat(trackIndex);
            if ((renderer.supportsFormat(trackFormat) & TrackRenderer.FORMAT_SUPPORT_MASK)
                == TrackRenderer.FORMAT_HANDLED) {
              adaptiveTrackIndices[adaptiveTrackIndexCount] = trackIndex;
              adaptiveTrackFormats[adaptiveTrackIndexCount++] = trackFormat;
            }
          }
          if (adaptiveTrackIndexCount > 1) {
            // We succeeded in building an adaptive track.
            rendererTrackSelections[rendererTrackCount] = new TrackSelection(groupIndex,
                Arrays.copyOf(adaptiveTrackIndices, adaptiveTrackIndexCount));
            rendererTrackFormats[rendererTrackCount] =
                Arrays.copyOf(adaptiveTrackFormats, adaptiveTrackIndexCount);
            rendererExternalTrackFormats[rendererTrackCount++] = Format.createSampleFormat(
                "auto", adaptiveTrackFormats[0].sampleMimeType, Format.NO_VALUE);
          }
        }
        for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
          Format trackFormat = trackGroup.getFormat(trackIndex);
          if ((renderer.supportsFormat(trackFormat) & TrackRenderer.FORMAT_SUPPORT_MASK)
              == TrackRenderer.FORMAT_HANDLED) {
            rendererTrackSelections[rendererTrackCount] = new TrackSelection(groupIndex,
                trackIndex);
            rendererTrackFormats[rendererTrackCount] = new Format[] {trackFormat};
            rendererExternalTrackFormats[rendererTrackCount++] = trackFormat;
          }
        }
      }
      trackSelections[rendererIndex] = Arrays.copyOf(rendererTrackSelections, rendererTrackCount);
      trackFormats[rendererIndex] = Arrays.copyOf(rendererTrackFormats, rendererTrackCount);
      externalTrackFormats[rendererIndex] = Arrays.copyOf(rendererExternalTrackFormats,
          rendererTrackCount);
    }

    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;

    // Enable renderers where appropriate.
    for (int rendererIndex = 0; rendererIndex < renderers.length; rendererIndex++) {
      TrackRenderer renderer = renderers[rendererIndex];
      int trackIndex = selectedTrackIndices[rendererIndex];
      if (0 <= trackIndex && trackIndex < trackSelections[rendererIndex].length) {
        TrackStream trackStream = source.enable(trackSelections[rendererIndex][trackIndex],
            positionUs);
        renderer.enable(trackFormats[rendererIndex][trackIndex], trackStream, positionUs, false);
        enabledRenderers.add(renderer);
        allRenderersEnded = allRenderersEnded && renderer.isEnded();
        allRenderersReadyOrEnded = allRenderersReadyOrEnded && isReadyOrEnded(renderer);
      }
    }

    if (allRenderersEnded && (durationUs == C.UNKNOWN_TIME_US || durationUs <= positionUs)) {
      // We don't expect this case, but handle it anyway.
      state = ExoPlayer.STATE_ENDED;
    } else {
      state = allRenderersReadyOrEnded && haveSufficientBuffer() ? ExoPlayer.STATE_READY
          : ExoPlayer.STATE_BUFFERING;
    }

    // Fire an event indicating that the player has been prepared, passing the initial state and
    // renderer track information.
    eventHandler.obtainMessage(MSG_PREPARED, state, 0, externalTrackFormats).sendToTarget();

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
    for (int i = 0; i < enabledRenderers.size(); i++) {
      enabledRenderers.get(i).start();
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    standaloneMediaClock.stop();
    for (int i = 0; i < enabledRenderers.size(); i++) {
      ensureStopped(enabledRenderers.get(i));
    }
  }

  private void updatePositionUs() {
    if (rendererMediaClock != null && enabledRenderers.contains(rendererMediaClockSource)
        && !rendererMediaClockSource.isEnded()) {
      positionUs = rendererMediaClock.getPositionUs();
      standaloneMediaClock.setPositionUs(positionUs);
    } else {
      positionUs = standaloneMediaClock.getPositionUs();
    }
    elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    TraceUtil.beginSection("doSomeWork");
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    updatePositionUs();
    bufferedPositionUs = source.getBufferedPositionUs();
    source.continueBuffering(positionUs);

    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (int i = 0; i < enabledRenderers.size(); i++) {
      TrackRenderer renderer = enabledRenderers.get(i);
      // TODO: Each renderer should return the maximum delay before which it wishes to be
      // invoked again. The minimum of these values should then be used as the delay before the next
      // invocation of this method.
      renderer.doSomeWork(positionUs, elapsedRealtimeUs);
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
    } else if (!enabledRenderers.isEmpty()) {
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
      for (int i = 0; i < enabledRenderers.size(); i++) {
        TrackRenderer renderer = enabledRenderers.get(i);
        ensureStopped(renderer);
      }
      setState(ExoPlayer.STATE_BUFFERING);
      source.seekToUs(positionUs);
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
    standaloneMediaClock.stop();
    if (renderers == null) {
      return;
    }
    for (int i = 0; i < renderers.length; i++) {
      resetRendererInternal(renderers[i]);
    }
    enabledRenderers.clear();
    Arrays.fill(trackSelections, null);
    source = null;
  }

  private void resetRendererInternal(TrackRenderer renderer) {
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

  private void setRendererSelectedTrackInternal(int rendererIndex, int trackIndex)
      throws ExoPlaybackException {
    if (selectedTrackIndices[rendererIndex] == trackIndex) {
      return;
    }

    selectedTrackIndices[rendererIndex] = trackIndex;
    if (state == ExoPlayer.STATE_IDLE || state == ExoPlayer.STATE_PREPARING) {
      return;
    }

    TrackRenderer renderer = renderers[rendererIndex];
    int rendererState = renderer.getState();
    if (trackSelections[rendererIndex].length == 0) {
      return;
    }

    boolean isEnabled = rendererState == TrackRenderer.STATE_ENABLED
        || rendererState == TrackRenderer.STATE_STARTED;
    boolean shouldEnable = 0 <= trackIndex && trackIndex < trackSelections[rendererIndex].length;

    if (isEnabled) {
      // The renderer is currently enabled. We need to disable it, so that we can either re-enable
      // it with the newly selected track (if shouldEnable is true) or because we want to leave it
      // disabled (if shouldEnable is false).
      if (!shouldEnable && renderer == rendererMediaClockSource) {
        // We've been using rendererMediaClockSource to advance the current position, but it's being
        // disabled and won't be re-enabled. Sync standaloneMediaClock so that it can take over
        // timing responsibilities.
        standaloneMediaClock.setPositionUs(rendererMediaClock.getPositionUs());
      }
      ensureStopped(renderer);
      enabledRenderers.remove(renderer);
      renderer.disable();
    }

    if (shouldEnable) {
      // Re-enable the renderer with the newly selected track.
      boolean playing = playWhenReady && state == ExoPlayer.STATE_READY;
      // Consider as joining if the renderer was previously disabled, but not when switching tracks.
      boolean joining = !isEnabled && playing;
      TrackStream trackStream = source.enable(trackSelections[rendererIndex][trackIndex],
          positionUs);
      renderer.enable(trackFormats[rendererIndex][trackIndex], trackStream, positionUs, joining);
      enabledRenderers.add(renderer);
      if (playing) {
        renderer.start();
      }
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
  }

  private void ensureStopped(TrackRenderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == TrackRenderer.STATE_STARTED) {
      renderer.stop();
    }
  }

}
