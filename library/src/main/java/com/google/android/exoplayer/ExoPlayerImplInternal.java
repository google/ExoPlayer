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
import com.google.android.exoplayer.util.TraceUtil;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the internal behavior of {@link ExoPlayerImpl}.
 */
/* package */ final class ExoPlayerImplInternal implements Handler.Callback {

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
  private static final int MSG_SET_RENDERER_ENABLED = 8;
  private static final int MSG_CUSTOM = 9;

  private static final int PREPARE_INTERVAL_MS = 10;
  private static final int RENDERING_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;

  private final Handler handler;
  private final HandlerThread internalPlayerThread;
  private final Handler eventHandler;
  private final MediaClock mediaClock;
  private final boolean[] rendererEnabledFlags;
  private final long minBufferUs;
  private final long minRebufferUs;

  private final List<TrackRenderer> enabledRenderers;
  private TrackRenderer[] renderers;
  private TrackRenderer timeSourceTrackRenderer;

  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private int state;
  private int customMessagesSent = 0;
  private int customMessagesProcessed = 0;

  private volatile long durationUs;
  private volatile long positionUs;
  private volatile long bufferedPositionUs;

  @SuppressLint("HandlerLeak")
  public ExoPlayerImplInternal(Handler eventHandler, boolean playWhenReady,
      boolean[] rendererEnabledFlags, int minBufferMs, int minRebufferMs) {
    this.eventHandler = eventHandler;
    this.playWhenReady = playWhenReady;
    this.rendererEnabledFlags = new boolean[rendererEnabledFlags.length];
    this.minBufferUs = minBufferMs * 1000L;
    this.minRebufferUs = minRebufferMs * 1000L;
    for (int i = 0; i < rendererEnabledFlags.length; i++) {
      this.rendererEnabledFlags[i] = rendererEnabledFlags[i];
    }

    this.state = ExoPlayer.STATE_IDLE;
    this.durationUs = TrackRenderer.UNKNOWN_TIME;
    this.bufferedPositionUs = TrackRenderer.UNKNOWN_TIME;

    mediaClock = new MediaClock();
    enabledRenderers = new ArrayList<TrackRenderer>(rendererEnabledFlags.length);
    internalPlayerThread = new HandlerThread(getClass().getSimpleName() + ":Handler") {
      @Override
      public void run() {
        // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
        // not normally change to this priority" is incorrect.
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        super.run();
      }
    };
    internalPlayerThread.start();
    handler = new Handler(internalPlayerThread.getLooper(), this);
  }

  public Looper getPlaybackLooper() {
    return internalPlayerThread.getLooper();
  }

  public int getCurrentPosition() {
    return (int) (positionUs / 1000);
  }

  public int getBufferedPosition() {
    return bufferedPositionUs == TrackRenderer.UNKNOWN_TIME ? ExoPlayer.UNKNOWN_TIME
        : (int) (bufferedPositionUs / 1000);
  }

  public int getDuration() {
    return durationUs == TrackRenderer.UNKNOWN_TIME ? ExoPlayer.UNKNOWN_TIME
        : (int) (durationUs / 1000);
  }

  public void prepare(TrackRenderer... renderers) {
    handler.obtainMessage(MSG_PREPARE, renderers).sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(int positionMs) {
    handler.obtainMessage(MSG_SEEK_TO, positionMs, 0).sendToTarget();
  }

  public void stop() {
    handler.sendEmptyMessage(MSG_STOP);
  }

  public void setRendererEnabled(int index, boolean enabled) {
    handler.obtainMessage(MSG_SET_RENDERER_ENABLED, index, enabled ? 1 : 0).sendToTarget();
  }

  public void sendMessage(ExoPlayerComponent target, int messageType, Object message) {
    customMessagesSent++;
    handler.obtainMessage(MSG_CUSTOM, messageType, 0, Pair.create(target, message)).sendToTarget();
  }

  public synchronized void blockingSendMessage(ExoPlayerComponent target, int messageType,
      Object message) {
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
    if (!released) {
      handler.sendEmptyMessage(MSG_RELEASE);
      while (!released) {
        try {
          wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      internalPlayerThread.quit();
    }
  }

  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_PREPARE: {
          prepareInternal((TrackRenderer[]) msg.obj);
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
          seekToInternal(msg.arg1);
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
        case MSG_SET_RENDERER_ENABLED: {
          setRendererEnabledInternal(msg.arg1, msg.arg2 != 0);
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
    } catch (RuntimeException e) {
      Log.e(TAG, "Internal runtime error.", e);
      eventHandler.obtainMessage(MSG_ERROR, new ExoPlaybackException(e)).sendToTarget();
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

  private void prepareInternal(TrackRenderer[] renderers) {
    rebuffering = false;
    this.renderers = renderers;
    for (int i = 0; i < renderers.length; i++) {
      if (renderers[i].isTimeSource()) {
        Assertions.checkState(timeSourceTrackRenderer == null);
        timeSourceTrackRenderer = renderers[i];
      }
    }
    setState(ExoPlayer.STATE_PREPARING);
    handler.sendEmptyMessage(MSG_INCREMENTAL_PREPARE);
  }

  private void incrementalPrepareInternal() throws ExoPlaybackException {
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    boolean prepared = true;
    for (int i = 0; i < renderers.length; i++) {
      if (renderers[i].getState() == TrackRenderer.STATE_UNPREPARED) {
        int state = renderers[i].prepare();
        if (state == TrackRenderer.STATE_UNPREPARED) {
          prepared = false;
        }
      }
    }

    if (!prepared) {
      // We're still waiting for some sources to be prepared.
      scheduleNextOperation(MSG_INCREMENTAL_PREPARE, operationStartTimeMs, PREPARE_INTERVAL_MS);
      return;
    }

    long durationUs = 0;
    boolean isEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (int i = 0; i < renderers.length; i++) {
      TrackRenderer renderer = renderers[i];
      if (rendererEnabledFlags[i] && renderer.getState() == TrackRenderer.STATE_PREPARED) {
        renderer.enable(positionUs, false);
        enabledRenderers.add(renderer);
        isEnded = isEnded && renderer.isEnded();
        allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded(renderer);
        if (durationUs == TrackRenderer.UNKNOWN_TIME) {
          // We've already encountered a track for which the duration is unknown, so the media
          // duration is unknown regardless of the duration of this track.
        } else {
          long trackDurationUs = renderer.getDurationUs();
          if (trackDurationUs == TrackRenderer.UNKNOWN_TIME) {
            durationUs = TrackRenderer.UNKNOWN_TIME;
          } else if (trackDurationUs == TrackRenderer.MATCH_LONGEST) {
            // Do nothing.
          } else {
            durationUs = Math.max(durationUs, trackDurationUs);
          }
        }
      }
    }
    this.durationUs = durationUs;

    if (isEnded) {
      // We don't expect this case, but handle it anyway.
      setState(ExoPlayer.STATE_ENDED);
    } else {
      setState(allRenderersReadyOrEnded ? ExoPlayer.STATE_READY : ExoPlayer.STATE_BUFFERING);
      if (playWhenReady && state == ExoPlayer.STATE_READY) {
        startRenderers();
      }
    }

    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private boolean rendererReadyOrEnded(TrackRenderer renderer) {
    if (renderer.isEnded()) {
      return true;
    }
    if (!renderer.isReady()) {
      return false;
    }
    if (state == ExoPlayer.STATE_READY) {
      return true;
    }
    long rendererDurationUs = renderer.getDurationUs();
    long rendererBufferedPositionUs = renderer.getBufferedPositionUs();
    long minBufferDurationUs = rebuffering ? minRebufferUs : minBufferUs;
    return minBufferDurationUs <= 0
        || rendererBufferedPositionUs == TrackRenderer.UNKNOWN_TIME
        || rendererBufferedPositionUs == TrackRenderer.END_OF_TRACK
        || rendererBufferedPositionUs >= positionUs + minBufferDurationUs
        || (rendererDurationUs != TrackRenderer.UNKNOWN_TIME
            && rendererDurationUs != TrackRenderer.MATCH_LONGEST
            && rendererBufferedPositionUs >= rendererDurationUs);
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
    mediaClock.start();
    for (int i = 0; i < enabledRenderers.size(); i++) {
      enabledRenderers.get(i).start();
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    mediaClock.stop();
    for (int i = 0; i < enabledRenderers.size(); i++) {
      ensureStopped(enabledRenderers.get(i));
    }
  }

  private void updatePositionUs() {
    positionUs = timeSourceTrackRenderer != null &&
        enabledRenderers.contains(timeSourceTrackRenderer) ?
        timeSourceTrackRenderer.getCurrentPositionUs() :
        mediaClock.getTimeUs();
  }

  private void doSomeWork() throws ExoPlaybackException {
    TraceUtil.beginSection("doSomeWork");
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    long bufferedPositionUs = durationUs != TrackRenderer.UNKNOWN_TIME ? durationUs
        : Long.MAX_VALUE;
    boolean isEnded = true;
    boolean allRenderersReadyOrEnded = true;
    updatePositionUs();
    for (int i = 0; i < enabledRenderers.size(); i++) {
      TrackRenderer renderer = enabledRenderers.get(i);
      // TODO: Each renderer should return the maximum delay before which it wishes to be
      // invoked again. The minimum of these values should then be used as the delay before the next
      // invocation of this method.
      renderer.doSomeWork(positionUs);
      isEnded = isEnded && renderer.isEnded();
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded(renderer);

      if (bufferedPositionUs == TrackRenderer.UNKNOWN_TIME) {
        // We've already encountered a track for which the buffered position is unknown. Hence the
        // media buffer position unknown regardless of the buffered position of this track.
      } else {
        long rendererDurationUs = renderer.getDurationUs();
        long rendererBufferedPositionUs = renderer.getBufferedPositionUs();
        if (rendererBufferedPositionUs == TrackRenderer.UNKNOWN_TIME) {
          bufferedPositionUs = TrackRenderer.UNKNOWN_TIME;
        } else if (rendererBufferedPositionUs == TrackRenderer.END_OF_TRACK
            || (rendererDurationUs != TrackRenderer.UNKNOWN_TIME
                && rendererDurationUs != TrackRenderer.MATCH_LONGEST
                && rendererBufferedPositionUs >= rendererDurationUs)) {
          // This track is fully buffered.
        } else {
          bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
        }
      }
    }
    this.bufferedPositionUs = bufferedPositionUs;

    if (isEnded) {
      setState(ExoPlayer.STATE_ENDED);
      stopRenderers();
    } else if (state == ExoPlayer.STATE_BUFFERING && allRenderersReadyOrEnded) {
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

  private void seekToInternal(int positionMs) throws ExoPlaybackException {
    rebuffering = false;
    positionUs = positionMs * 1000L;
    mediaClock.stop();
    mediaClock.setTimeUs(positionUs);
    if (state == ExoPlayer.STATE_IDLE || state == ExoPlayer.STATE_PREPARING) {
      return;
    }
    for (int i = 0; i < enabledRenderers.size(); i++) {
      TrackRenderer renderer = enabledRenderers.get(i);
      ensureStopped(renderer);
      renderer.seekTo(positionUs);
    }
    setState(ExoPlayer.STATE_BUFFERING);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void stopInternal() {
    rebuffering = false;
    resetInternal();
  }

  private void releaseInternal() {
    resetInternal();
    synchronized (this) {
      released = true;
      notifyAll();
    }
  }

  private void resetInternal() {
    handler.removeMessages(MSG_DO_SOME_WORK);
    handler.removeMessages(MSG_INCREMENTAL_PREPARE);
    mediaClock.stop();
    if (renderers == null) {
      return;
    }
    for (int i = 0; i < renderers.length; i++) {
      try {
        TrackRenderer renderer = renderers[i];
        ensureStopped(renderer);
        if (renderer.getState() == TrackRenderer.STATE_ENABLED) {
          renderer.disable();
        }
        renderer.release();
      } catch (ExoPlaybackException e) {
        // There's nothing we can do. Catch the exception here so that other renderers still have
        // a chance of being cleaned up correctly.
        Log.e(TAG, "Stop failed.", e);
      } catch (RuntimeException e) {
        // Ditto.
        Log.e(TAG, "Stop failed.", e);
      }
    }
    renderers = null;
    timeSourceTrackRenderer = null;
    enabledRenderers.clear();
    setState(ExoPlayer.STATE_IDLE);
  }

  private <T> void sendMessageInternal(int what, Object obj)
      throws ExoPlaybackException {
    try {
      @SuppressWarnings("unchecked")
      Pair<ExoPlayerComponent, Object> targetAndMessage = (Pair<ExoPlayerComponent, Object>) obj;
      targetAndMessage.first.handleMessage(what, targetAndMessage.second);
    } finally {
      synchronized (this) {
        customMessagesProcessed++;
        notifyAll();
      }
    }
    if (state != ExoPlayer.STATE_IDLE) {
      // The message may have caused something to change that now requires us to do work.
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
  }

  private void setRendererEnabledInternal(int index, boolean enabled)
      throws ExoPlaybackException {
    if (rendererEnabledFlags[index] == enabled) {
      return;
    }

    rendererEnabledFlags[index] = enabled;
    if (state == ExoPlayer.STATE_IDLE || state == ExoPlayer.STATE_PREPARING) {
      return;
    }

    TrackRenderer renderer = renderers[index];
    int rendererState = renderer.getState();
    if (rendererState != TrackRenderer.STATE_PREPARED &&
        rendererState != TrackRenderer.STATE_ENABLED &&
        rendererState != TrackRenderer.STATE_STARTED) {
      return;
    }

    if (enabled) {
      boolean playing = playWhenReady && state == ExoPlayer.STATE_READY;
      renderer.enable(positionUs, playing);
      enabledRenderers.add(renderer);
      if (playing) {
        renderer.start();
      }
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    } else {
      if (renderer == timeSourceTrackRenderer) {
        // We've been using timeSourceTrackRenderer to advance the current position, but it's
        // being disabled. Sync mediaClock so that it can take over timing responsibilities.
        mediaClock.setTimeUs(renderer.getCurrentPositionUs());
      }
      ensureStopped(renderer);
      enabledRenderers.remove(renderer);
      renderer.disable();
    }
  }

  private void ensureStopped(TrackRenderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == TrackRenderer.STATE_STARTED) {
      renderer.stop();
    }
  }

}
