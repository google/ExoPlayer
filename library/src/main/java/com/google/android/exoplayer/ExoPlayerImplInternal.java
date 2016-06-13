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
  private final Timeline timeline;

  private TrackRenderer rendererMediaClockSource;
  private MediaClock rendererMediaClock;
  private SampleSourceProvider sampleSourceProvider;
  private SampleSource sampleSource;
  private TrackRenderer[] enabledRenderers;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private int state;
  private int customMessagesSent;
  private int customMessagesProcessed;
  private long lastSeekPositionMs;
  private int lastSeekSourceIndex;
  private long elapsedRealtimeUs;

  private long sourceOffsetUs;
  private long internalPositionUs;
  private int sourceIndex;

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
    timeline = new Timeline();

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
    return bufferedPositionUs == C.UNSET_TIME_US || bufferedPositionUs == C.END_OF_SOURCE_US
        ? ExoPlayer.UNKNOWN_TIME : bufferedPositionUs / 1000;
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
    // TODO[playlists]: Move to ExoPlayerImpl.
    int sourceIndex;
    synchronized (timeline) {
      sourceIndex = this.sourceIndex;
    }
    seekTo(sourceIndex, positionMs);
  }

  public void seekTo(int sourceIndex, long positionMs) {
    // TODO[playlists]: Expose the current source index and seeking to sources in ExoPlayer.
    lastSeekSourceIndex = sourceIndex;
    lastSeekPositionMs = positionMs;
    pendingSeekCount.incrementAndGet();
    handler.obtainMessage(MSG_SEEK_TO, lastSeekSourceIndex, -1, positionMs).sendToTarget();
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

  // Handler.Callback implementation.

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

  // Private methods.

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
    // TODO[playlists]: Take into account the buffered position in the timeline.
    long minBufferDurationUs = rebuffering ? minRebufferUs : minBufferUs;
    return minBufferDurationUs <= 0
        || bufferedPositionUs == C.UNSET_TIME_US
        || bufferedPositionUs == C.END_OF_SOURCE_US
        || bufferedPositionUs >= positionUs + minBufferDurationUs
        || (durationUs != C.UNSET_TIME_US && bufferedPositionUs >= durationUs);
  }

  private void setSourceProviderInternal(SampleSourceProvider sourceProvider) {
    resetInternal();
    sampleSourceProvider = sourceProvider;
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
      internalPositionUs = rendererMediaClock.getPositionUs();
      standaloneMediaClock.setPositionUs(internalPositionUs);
    } else {
      internalPositionUs = standaloneMediaClock.getPositionUs();
    }
    positionUs = internalPositionUs - sourceOffsetUs;
    elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;
  }

  private void updateBufferedPositionUs() {
    long sourceBufferedPositionUs = enabledRenderers.length > 0
        ? sampleSource.getBufferedPositionUs() : C.END_OF_SOURCE_US;
    bufferedPositionUs = sourceBufferedPositionUs == C.END_OF_SOURCE_US
        && durationUs != C.UNSET_TIME_US ? durationUs : sourceBufferedPositionUs;
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    if (sampleSource == null) {
      timeline.updateSources();
      sampleSource = timeline.getSampleSource(internalPositionUs);
      if (sampleSource != null) {
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
        sampleSource = timeline.getSampleSource(internalPositionUs);
      }
      updateBufferedPositionUs();
      timeline.updateSources();
    } else {
      updatePositionUs();
    }

    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (TrackRenderer renderer : enabledRenderers) {
      // TODO: Each renderer should return the maximum delay before which it wishes to be invoked
      // again. The minimum of these values should then be used as the delay before the next
      // invocation of this method.
      renderer.render(internalPositionUs, elapsedRealtimeUs);
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
        && haveSufficientBuffer() && timeline.isReady(internalPositionUs)) {
      setState(ExoPlayer.STATE_READY);
      if (playWhenReady) {
        startRenderers();
      }
    } else if (state == ExoPlayer.STATE_READY && (!allRenderersReadyOrEnded
        || !timeline.isReady(internalPositionUs))) {
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

  private void seekToInternal(int sourceIndex, long positionMs) throws ExoPlaybackException {
    try {
      if (positionMs == (positionUs / 1000)) {
        // Seek is to the current position. Do nothing.
        return;
      }

      rebuffering = false;
      positionUs = positionMs * 1000;
      internalPositionUs = sourceOffsetUs + positionUs;
      standaloneMediaClock.stop();
      standaloneMediaClock.setPositionUs(internalPositionUs);
      sampleSource = timeline.seekTo(sourceIndex, positionUs);
      if (sampleSource == null) {
        // The source isn't prepared.
        return;
      }

      if (enabledRenderers.length > 0) {
        for (TrackRenderer renderer : enabledRenderers) {
          ensureStopped(renderer);
        }
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
      setState(allRenderersReadyOrEnded && haveSufficientBuffer()
          && timeline.isReady(internalPositionUs) ? ExoPlayer.STATE_READY
              : ExoPlayer.STATE_BUFFERING);
    }

    // Start the renderers if ready, and schedule the first piece of work.
    if (playWhenReady && state == ExoPlayer.STATE_READY) {
      startRenderers();
    }
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private boolean checkForSourceResetInternal() throws ExoPlaybackException {
    long resetPositionUs = sampleSource.readReset();
    if (resetPositionUs == C.UNSET_TIME_US) {
      return false;
    }
    internalPositionUs = sourceOffsetUs + resetPositionUs;
    standaloneMediaClock.setPositionUs(internalPositionUs);
    for (TrackRenderer renderer : enabledRenderers) {
      renderer.reset(internalPositionUs);
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
    rebuffering = false;
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
    sampleSource = null;
    timeline.reset();
  }

  private void sendMessagesInternal(ExoPlayerMessage[] messages) throws ExoPlaybackException {
    try {
      for (ExoPlayerMessage message : messages) {
        message.target.handleMessage(message.messageType, message.message);
      }
      if (sampleSource != null) {
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

  private void ensureStopped(TrackRenderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == TrackRenderer.STATE_STARTED) {
      renderer.stop();
    }
  }

  private void reselectTracksInternal() throws ExoPlaybackException {
    if (sampleSource == null) {
      // We don't have tracks yet, so we don't care.
      return;
    }
    timeline.reselectTracks();
    updateBufferedPositionUs();
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  /**
   * Keeps track of the {@link Source}s of media being played in the timeline.
   */
  private final class Timeline {

    // Used during track reselection.
    private final boolean[] rendererWasEnabledFlags;
    private final ArrayList<TrackStream> oldStreams;
    private final ArrayList<TrackSelection> newSelections;

    private int nextSourceIndex;

    private Source playingSource;
    private Source readingSource;
    private Source bufferingSource;

    private long playingSourceEndPositionUs;
    private long nextSourceOffsetUs;

    public Timeline() {
      rendererWasEnabledFlags = new boolean[renderers.length];
      oldStreams = new ArrayList<>();
      newSelections = new ArrayList<>();
      playingSourceEndPositionUs = C.UNSET_TIME_US;
    }

    public void updateSources() throws ExoPlaybackException, IOException {
      // TODO[playlists]: Let sample source providers invalidate sources that are already buffering.

      // Continue buffering the first source that is not yet buffered, if any.
      int sourceCount = sampleSourceProvider.getSourceCount();
      if (bufferingSource == null || bufferingSource.isFullyBuffered()) {
        if (sourceCount == SampleSourceProvider.UNKNOWN_SOURCE_COUNT
            || nextSourceIndex < sourceCount) {
          // Create the next source and prepare to buffer it.
          int index = nextSourceIndex++;
          SampleSource sampleSource = sampleSourceProvider.createSource(index);
          if (sampleSource != null) {
            Source newSource = new Source(sampleSource, index, renderers.length);
            if (bufferingSource != null) {
              bufferingSource.nextSource = newSource;
            }
            bufferingSource = newSource;
          }
        }
      }

      if (bufferingSource != null) {
        if (!bufferingSource.prepared) {
          // Continue preparation.
          // TODO[playlists]: Add support for setting the start position to play in a source.
          long startPositionUs = playingSource == null ? positionUs : 0;
          if (bufferingSource.prepare(startPositionUs)) {
            Pair<TrackSelectionArray, Object> result = trackSelector.selectTracks(renderers,
                bufferingSource.sampleSource.getTrackGroups());
            bufferingSource.selectTracks(result.first, result.second, startPositionUs);
            if (playingSource == null) {
              // This is the first prepared source, so start playing it.
              sourceOffsetUs = 0;
              setPlayingSource(bufferingSource);
            }
          }
        }

        if (bufferingSource.hasEnabledTracks) {
          long bufferingPositionUs;
          if (bufferingSource == playingSource) {
            bufferingPositionUs = internalPositionUs - sourceOffsetUs;
          } else if (bufferingSource == readingSource) {
            // TODO[playlists]: Make sure continueBuffering supports a negative downstream position.
            bufferingPositionUs = internalPositionUs - nextSourceOffsetUs;
          } else {
            bufferingPositionUs = 0;
          }
          bufferingSource.sampleSource.continueBuffering(bufferingPositionUs);
        }
      }

      if (playingSource == null || readingSource != playingSource) {
        // We are either waiting for preparation to complete, or already reading ahead.
        return;
      }

      // Check whether all enabled renderers have read to the end of their TrackStreams.
      for (TrackRenderer renderer : enabledRenderers) {
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }
      if (playingSourceEndPositionUs == C.UNSET_TIME_US) {
        // Calculate the next source's start position in the timeline.
        long playingSourceDurationUs = playingSource.sampleSource.getDurationUs();
        if (playingSourceDurationUs == C.UNSET_TIME_US) {
          // The duration of the current source is unknown, so use the maximum rendered timestamp
          // plus a small extra offset to make sure that renderers don't read two buffers with the
          // same timestamp.
          playingSourceEndPositionUs = 0;
          for (TrackRenderer renderer : enabledRenderers) {
            playingSourceEndPositionUs =
                Math.max(playingSourceEndPositionUs, renderer.getMaximumTimeUs());
          }
          nextSourceOffsetUs = playingSourceEndPositionUs + 10000;
        } else {
          playingSourceEndPositionUs = sourceOffsetUs + playingSourceDurationUs;
          nextSourceOffsetUs = playingSourceEndPositionUs;
        }
      }
      if (sourceCount != SampleSourceProvider.UNKNOWN_SOURCE_COUNT
          && readingSource.index == sourceCount - 1) {
        // This is the last source, so signal the renderers to read the end of the stream.
        for (TrackRenderer renderer : enabledRenderers) {
          renderer.setCurrentTrackStreamIsFinal();
        }
        readingSource = null;
        playingSourceEndPositionUs = C.UNSET_TIME_US;
        return;
      }
      if (playingSource.nextSource != null && playingSource.nextSource.prepared) {
        readingSource = playingSource.nextSource;
        // Suppress reading a reset so that the transition can be seamless.
        readingSource.sampleSource.readReset();
        // Replace enabled renderers' TrackStreams if they will continue to be enabled when the
        // new source starts playing, so that the transition can be seamless.
        TrackSelectionArray newTrackSelections = readingSource.trackSelections;
        TrackGroupArray groups = readingSource.sampleSource.getTrackGroups();
        for (int i = 0; i < renderers.length; i++) {
          TrackRenderer renderer = renderers[i];
          TrackSelection selection = newTrackSelections.get(i);
          if (selection != null && renderer.getState() != TrackRenderer.STATE_DISABLED) {
            // The renderer is enabled and will continue to be enabled after the transition.
            Format[] formats = new Format[selection.length];
            for (int j = 0; j < formats.length; j++) {
              formats[j] = groups.get(selection.group).getFormat(selection.getTrack(j));
            }
            renderer.replaceTrackStream(formats, readingSource.trackStreams[i], nextSourceOffsetUs);
          }
        }
      }
    }

    public boolean isReady(long positionUs) {
      return playingSourceEndPositionUs == C.UNSET_TIME_US
          || positionUs < playingSourceEndPositionUs || playingSource.nextSource != null;
    }

    public SampleSource getSampleSource(long positionUs) throws ExoPlaybackException {
      if (playingSource == null) {
        return null;
      }
      if (readingSource != playingSource && playingSourceEndPositionUs != C.UNSET_TIME_US
          && positionUs >= playingSourceEndPositionUs) {
        // Renderers are playing the next source, so update the timeline.
        playingSource.release();
        sourceOffsetUs = nextSourceOffsetUs;
        setPlayingSource(readingSource);
      }
      return playingSource.sampleSource;
    }

    public SampleSource seekTo(int sourceIndex, long sourcePositionUs) throws ExoPlaybackException {
      // Clear the timeline, but keep the requested source if it is already prepared.
      Source source = playingSource;
      Source newPlayingSource = null;
      while (source != null) {
        if (source.index == sourceIndex && source.prepared) {
          newPlayingSource = source;
        } else {
          source.release();
        }
        source = source.nextSource;
      }
      if (newPlayingSource != null) {
        nextSourceIndex = sourceIndex + 1;
        newPlayingSource.nextSource = null;
        setPlayingSource(newPlayingSource);
        bufferingSource = playingSource;
        if (playingSource.hasEnabledTracks) {
          sampleSource.seekToUs(sourcePositionUs);
        }
      } else {
        playingSource = null;
        readingSource = null;
        bufferingSource = null;
        durationUs = C.UNSET_TIME_US;
        sampleSource = null;
        // Set the next source index so that the required source is created in updateSources.
        nextSourceIndex = sourceIndex;
      }
      return sampleSource;
    }

    public void reselectTracks() throws ExoPlaybackException {
      if (readingSource != null && readingSource != playingSource) {
        // Newly enabled tracks in playingSource can increase the calculated start timestamp for the
        // next source, so we have to discard the reading source. Reset TrackStreams for renderers
        // that are reading the next source already back to the playing source.
        TrackSelectionArray newTrackSelections = readingSource.trackSelections;
        TrackGroupArray groups = readingSource.sampleSource.getTrackGroups();
        for (int i = 0; i < renderers.length; i++) {
          TrackRenderer renderer = renderers[i];
          TrackSelection selection = newTrackSelections.get(i);
          if (selection != null && renderer.getState() != TrackRenderer.STATE_DISABLED) {
            // The renderer is enabled and will continue to be enabled after the transition.
            Format[] formats = new Format[selection.length];
            for (int j = 0; j < formats.length; j++) {
              formats[j] = groups.get(selection.group).getFormat(selection.getTrack(j));
            }
            renderer.replaceTrackStream(formats, playingSource.trackStreams[i], sourceOffsetUs);
          }
        }
      }

      // Discard the rest of the timeline after the playing source, as the player may need to
      // rebuffer after track selection.
      Source source = playingSource.nextSource;
      while (source != null) {
        source.release();
        source = source.nextSource;
      }
      playingSource.nextSource = null;
      readingSource = playingSource;
      bufferingSource = playingSource;
      nextSourceIndex = playingSource.index + 1;
      playingSourceEndPositionUs = C.UNSET_TIME_US;

      // Update the track selection for the playing source.
      Pair<TrackSelectionArray, Object> result =
          trackSelector.selectTracks(renderers, playingSource.sampleSource.getTrackGroups());
      TrackSelectionArray newTrackSelections = result.first;
      Object trackSelectionData = result.second;
      if (newTrackSelections.equals(playingSource.trackSelections)) {
        trackSelector.onSelectionActivated(trackSelectionData);
        return;
      }

      int enabledRendererCount = disableRenderers(false, newTrackSelections);
      TrackStream[] newStreams = playingSource.updateTrackStreams(oldStreams, newTrackSelections,
          newSelections, positionUs);
      trackSelector.onSelectionActivated(trackSelectionData);

      // Update the stored TrackStreams.
      for (int i = 0; i < renderers.length; i++) {
        TrackRenderer renderer = renderers[i];
        TrackSelection newSelection = newTrackSelections.get(i);
        if (newSelection != null && renderer.getState() == TrackRenderer.STATE_DISABLED) {
          int newStreamIndex = newSelections.indexOf(newSelection);
          playingSource.trackStreams[i] = newStreams[newStreamIndex];
        }
      }

      enableRenderers(newTrackSelections, enabledRendererCount);
    }

    public void reset() {
      Source source = playingSource != null ? playingSource : bufferingSource;
      while (source != null) {
        source.release();
        source = source.nextSource;
      }
      playingSource = null;
      readingSource = null;
      bufferingSource = null;
      durationUs = C.UNSET_TIME_US;
      nextSourceIndex = 0;
      sourceOffsetUs = 0;
      playingSourceEndPositionUs = C.UNSET_TIME_US;
      nextSourceOffsetUs = 0;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Timeline[");
      Source source = playingSource != null ? playingSource : bufferingSource;
      while (source != null) {
        sb.append(source);
        source = source.nextSource;
        if (source != null) {
          sb.append(", ");
        }
      }
      sb.append("]");
      return sb.toString();
    }

    private void setPlayingSource(Source source) throws ExoPlaybackException {
      playingSourceEndPositionUs = C.UNSET_TIME_US;
      durationUs = source.sampleSource.getDurationUs();

      // Disable/enable renderers for the new source.
      int enabledRendererCount = disableRenderers(true, source.trackSelections);
      if (playingSource != source) {
        trackSelector.onSelectionActivated(source.trackSelectionData);
      }
      readingSource = source;
      playingSource = source;
      enableRenderers(source.trackSelections, enabledRendererCount);

      // Update the timeline position for the new source index.
      synchronized (timeline) {
        sourceIndex = source.index;
        updatePositionUs();
      }
    }

    private int disableRenderers(boolean sourceTransition, TrackSelectionArray newTrackSelections)
        throws ExoPlaybackException {
      // Disable any renderers whose selections have changed, adding the corresponding TrackStream
      // instances to oldStreams. Where we need to obtain a new TrackStream instance for a renderer,
      // we add the corresponding TrackSelection to newSelections.
      oldStreams.clear();
      newSelections.clear();
      int enabledRendererCount = 0;
      for (int i = 0; i < renderers.length; i++) {
        TrackRenderer renderer = renderers[i];
        rendererWasEnabledFlags[i] = renderer.getState() != TrackRenderer.STATE_DISABLED;
        TrackSelection oldSelection = playingSource == null ? null
            : playingSource.trackSelections.get(i);
        TrackSelection newSelection = newTrackSelections.get(i);
        if (newSelection != null) {
          enabledRendererCount++;
        }
        // If the player is transitioning to a new source, disable renderers that are not used when
        // playing the new source. Otherwise, disable renderers whose selections are changing.
        if ((sourceTransition && oldSelection != null && newSelection == null)
            || (!sourceTransition && !Util.areEqual(oldSelection, newSelection))) {
          // Either this is a source transition and the renderer is not needed any more, or the
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
            // TODO[playlists]: Consider using TrackStreams from the timeline source instead of
            // returning a TrackStream from TrackRenderer.disable.
            oldStreams.add(renderer.disable());
          }
          if (newSelection != null) {
            newSelections.add(newSelection);
          }
        }
      }
      return enabledRendererCount;
    }

    private void enableRenderers(TrackSelectionArray newTrackSelections, int enabledRendererCount)
        throws ExoPlaybackException {
      playingSource.trackSelections = newTrackSelections;
      enabledRenderers = new TrackRenderer[enabledRendererCount];
      enabledRendererCount = 0;
      TrackGroupArray trackGroups = playingSource.sampleSource.getTrackGroups();
      for (int i = 0; i < renderers.length; i++) {
        TrackRenderer renderer = renderers[i];
        TrackSelection newSelection = playingSource.trackSelections.get(i);
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
              formats[j] = trackGroups.get(newSelection.group).getFormat(newSelection.getTrack(j));
            }
            // Enable the renderer.
            renderer.enable(formats, playingSource.trackStreams[i], internalPositionUs, joining,
                sourceOffsetUs);
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
   * Represents a {@link SampleSource} with information required to play it as part of a timeline.
   */
  private static final class Source {

    public final SampleSource sampleSource;
    public final int index;
    public final TrackStream[] trackStreams;

    public boolean prepared;
    public boolean hasEnabledTracks;
    public TrackSelectionArray trackSelections;
    public Object trackSelectionData;

    public Source nextSource;

    public Source(SampleSource sampleSource, int index, int rendererCount) {
      this.sampleSource = sampleSource;
      this.index = index;
      trackStreams = new TrackStream[rendererCount];
    }

    public boolean isFullyBuffered() {
      return prepared && sampleSource.getBufferedPositionUs() == C.END_OF_SOURCE_US;
    }

    public boolean prepare(long startPositionUs) throws IOException {
      if (sampleSource.prepare(startPositionUs)) {
        prepared = true;
        return true;
      } else {
        return false;
      }
    }

    public void selectTracks(TrackSelectionArray newTrackSelections, Object trackSelectionData,
        long positionUs) throws ExoPlaybackException {
      this.trackSelectionData = trackSelectionData;
      if (newTrackSelections.equals(trackSelections)) {
        return;
      }

      ArrayList<TrackStream> oldStreams = new ArrayList<>();
      ArrayList<TrackSelection> newSelections = new ArrayList<>();
      for (int i = 0; i < newTrackSelections.length; i++) {
        TrackSelection oldSelection = trackSelections == null ? null : trackSelections.get(i);
        TrackSelection newSelection = newTrackSelections.get(i);
        if (!Util.areEqual(oldSelection, newSelection)) {
          if (oldSelection != null) {
            oldStreams.add(trackStreams[i]);
          }
          if (newSelection != null) {
            newSelections.add(newSelection);
          }
        }
      }
      updateTrackStreams(oldStreams, newTrackSelections, newSelections, positionUs);
    }

    public TrackStream[] updateTrackStreams(ArrayList<TrackStream> oldStreams,
        TrackSelectionArray newTrackSelections, ArrayList<TrackSelection> newSelections,
        long positionUs) {
      TrackStream[] newStreams = sampleSource.selectTracks(oldStreams, newSelections,
          positionUs);
      hasEnabledTracks = false;
      for (int i = 0; i < newTrackSelections.length; i++) {
        TrackSelection selection = newTrackSelections.get(i);
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
      trackSelections = newTrackSelections;
      return newStreams;
    }

    public void release() {
      try {
        sampleSource.release();
      } catch (RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Source release failed.", e);
      }
    }

  }

}
