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
/* package */ final class ExoPlayerImplInternal implements Handler.Callback, SampleSource.Callback,
    InvalidationListener {

  /**
   * Playback position information which is read on the application's thread by
   * {@link ExoPlayerImpl} and read/written internally on the player's thread.
   */
  public static final class PlaybackInfo {

    public final int sourceIndex;

    public volatile long positionUs;
    public volatile long bufferedPositionUs;
    public volatile long durationUs;

    public PlaybackInfo(int sourceIndex) {
      this.sourceIndex = sourceIndex;
      durationUs = C.UNSET_TIME_US;
    }

  }

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_STATE_CHANGED = 1;
  public static final int MSG_LOADING_CHANGED = 2;
  public static final int MSG_SET_PLAY_WHEN_READY_ACK = 3;
  public static final int MSG_SET_SOURCE_PROVIDER_ACK = 4;
  public static final int MSG_SEEK_ACK = 5;
  public static final int MSG_SOURCE_CHANGED = 6;
  public static final int MSG_ERROR = 7;

  // Internal messages
  private static final int MSG_SET_SOURCE_PROVIDER = 0;
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_STOP = 4;
  private static final int MSG_RELEASE = 5;
  private static final int MSG_SOURCE_PREPARED = 6;
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
  private TrackRenderer rendererMediaClockSource;
  private MediaClock rendererMediaClock;
  private SampleSourceProvider sampleSourceProvider;
  private TrackRenderer[] enabledRenderers;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private boolean isLoading;
  private int state;
  private int customMessagesSent;
  private int customMessagesProcessed;
  private long elapsedRealtimeUs;

  private long internalPositionUs;

  public ExoPlayerImplInternal(TrackRenderer[] renderers, TrackSelector trackSelector,
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
    enabledRenderers = new TrackRenderer[0];
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

  public void setSourceProvider(SampleSourceProvider sourceProvider) {
    handler.obtainMessage(MSG_SET_SOURCE_PROVIDER, sourceProvider).sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(int sourceIndex, long positionUs) {
    handler.obtainMessage(MSG_SEEK_TO, sourceIndex, -1, positionUs).sendToTarget();
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

  // SampleSource.Callback implementation.

  @Override
  public void onSourcePrepared(SampleSource source) {
    handler.obtainMessage(MSG_SOURCE_PREPARED, source).sendToTarget();
  }

  @Override
  public void onContinueLoadingRequested(SampleSource source) {
    handler.obtainMessage(MSG_SOURCE_CONTINUE_LOADING_REQUESTED, source).sendToTarget();
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
        case MSG_SOURCE_PREPARED: {
          timeline.handleSourcePrepared((SampleSource) msg.obj);
          return true;
        }
        case MSG_SOURCE_CONTINUE_LOADING_REQUESTED: {
          timeline.handleContinueLoadingRequested((SampleSource) msg.obj);
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

  private void setSourceProviderInternal(SampleSourceProvider sourceProvider) {
    try {
      resetInternal();
      sampleSourceProvider = sourceProvider;
      setState(ExoPlayer.STATE_BUFFERING);
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    } finally {
      eventHandler.sendEmptyMessage(MSG_SET_SOURCE_PROVIDER_ACK);
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

  private void updatePlaybackPositions() throws ExoPlaybackException {
    SampleSource sampleSource = timeline.getSampleSource();
    if (sampleSource == null) {
      return;
    }

    // Update the duration.
    if (playbackInfo.durationUs == C.UNSET_TIME_US) {
      playbackInfo.durationUs = sampleSource.getDurationUs();
    }

    // Update the playback position.
    long positionUs = sampleSource.readDiscontinuity();
    if (positionUs != C.UNSET_TIME_US) {
      resetInternalPosition(positionUs);
    } else {
      if (rendererMediaClockSource != null && !rendererMediaClockSource.isEnded()) {
        internalPositionUs = rendererMediaClock.getPositionUs();
        standaloneMediaClock.setPositionUs(internalPositionUs);
      } else {
        internalPositionUs = standaloneMediaClock.getPositionUs();
      }
      positionUs = internalPositionUs - timeline.playingSource.offsetUs;
    }
    playbackInfo.positionUs = positionUs;
    elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;

    // Update the buffered position.
    long bufferedPositionUs;
    if (enabledRenderers.length == 0) {
      bufferedPositionUs = C.END_OF_SOURCE_US;
    } else {
      bufferedPositionUs = sampleSource.getBufferedPositionUs();
    }
    playbackInfo.bufferedPositionUs = bufferedPositionUs;
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = SystemClock.elapsedRealtime();

    timeline.updateSources();
    if (timeline.getSampleSource() == null) {
      // We're still waiting for the first source to be prepared.
      timeline.maybeThrowSourcePrepareError();
      scheduleNextOperation(MSG_DO_SOME_WORK, operationStartTimeMs, PREPARING_SOURCE_INTERVAL_MS);
      return;
    }

    TraceUtil.beginSection("doSomeWork");

    updatePlaybackPositions();
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
      boolean rendererReadyOrEnded = renderer.isReady() || renderer.isEnded();
      if (!rendererReadyOrEnded) {
        renderer.maybeThrowStreamError();
      }
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded;
    }

    if (!allRenderersReadyOrEnded) {
      timeline.maybeThrowSourcePrepareError();
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

  private void seekToInternal(int sourceIndex, long seekPositionUs) throws ExoPlaybackException {
    try {
      if (sourceIndex == playbackInfo.sourceIndex
          && (seekPositionUs / 1000) == (playbackInfo.positionUs / 1000)) {
        // Seek position equals the current position to the nearest millisecond. Do nothing.
        return;
      }

      stopRenderers();
      rebuffering = false;

      seekPositionUs = timeline.seekTo(sourceIndex, seekPositionUs);
      if (sourceIndex != playbackInfo.sourceIndex) {
        playbackInfo = new PlaybackInfo(sourceIndex);
        playbackInfo.positionUs = seekPositionUs;
        eventHandler.obtainMessage(MSG_SOURCE_CHANGED, playbackInfo).sendToTarget();
      } else {
        playbackInfo.positionUs = seekPositionUs;
      }

      updatePlaybackPositions();
      if (sampleSourceProvider != null) {
        setState(ExoPlayer.STATE_BUFFERING);
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    } finally {
      eventHandler.sendEmptyMessage(MSG_SEEK_ACK);
    }
  }

  private void resetInternalPosition(long sourcePositionUs) throws ExoPlaybackException {
    long sourceOffsetUs = timeline.playingSource == null ? 0 : timeline.playingSource.offsetUs;
    internalPositionUs = sourceOffsetUs + sourcePositionUs;
    standaloneMediaClock.setPositionUs(internalPositionUs);
    for (TrackRenderer renderer : enabledRenderers) {
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
    for (TrackRenderer renderer : enabledRenderers) {
      try {
        ensureStopped(renderer);
        renderer.disable();
      } catch (ExoPlaybackException | RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Stop failed.", e);
      }
    }
    enabledRenderers = new TrackRenderer[0];
    sampleSourceProvider = null;
    timeline.reset();
    loadControl.reset();
    setIsLoading(false);
  }

  private void sendMessagesInternal(ExoPlayerMessage[] messages) throws ExoPlaybackException {
    try {
      for (ExoPlayerMessage message : messages) {
        message.target.handleMessage(message.messageType, message.message);
      }
      if (sampleSourceProvider != null) {
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
    if (timeline.getSampleSource() == null) {
      // We don't have tracks yet, so we don't care.
      return;
    }
    timeline.reselectTracks();
    updatePlaybackPositions();
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  /**
   * Keeps track of the {@link Source}s of media being played in the timeline.
   */
  private final class Timeline {

    private final TrackRenderer[] renderers;

    public boolean isReady;
    public boolean isEnded;

    private Source playingSource;
    private Source readingSource;
    private Source loadingSource;

    private int pendingSourceIndex;
    private long playingSourceEndPositionUs;

    public Timeline(TrackRenderer[] renderers) {
      this.renderers = renderers;
      playingSourceEndPositionUs = C.UNSET_TIME_US;
    }

    public SampleSource getSampleSource() throws ExoPlaybackException {
      return playingSource == null ? null : playingSource.sampleSource;
    }

    public boolean haveSufficientBuffer(boolean rebuffering) {
      if (loadingSource == null) {
        return false;
      }
      long positionUs = internalPositionUs - loadingSource.offsetUs;
      long bufferedPositionUs = !loadingSource.prepared ? 0
          : loadingSource.sampleSource.getBufferedPositionUs();
      if (bufferedPositionUs == C.END_OF_SOURCE_US) {
        int sourceCount = sampleSourceProvider.getSourceCount();
        if (sourceCount != SampleSourceProvider.UNKNOWN_SOURCE_COUNT
            && loadingSource.index == sourceCount - 1) {
          return true;
        }
        bufferedPositionUs = loadingSource.sampleSource.getDurationUs();
      }
      return loadControl.shouldStartPlayback(bufferedPositionUs - positionUs, rebuffering);
    }

    public void maybeThrowSourcePrepareError() throws IOException {
      if (loadingSource != null && !loadingSource.prepared
          && (readingSource == null || readingSource.nextSource == loadingSource)) {
        for (TrackRenderer renderer : enabledRenderers) {
          if (!renderer.hasReadStreamToEnd()) {
            return;
          }
        }
        loadingSource.sampleSource.maybeThrowPrepareError();
      }
    }

    public void updateSources() throws ExoPlaybackException, IOException {
      // TODO[playlists]: Let sample source providers invalidate sources that are already loaded.

      // Update the loading source.
      int sourceCount = sampleSourceProvider.getSourceCount();
      if (loadingSource == null
          || (loadingSource.isFullyBuffered() && loadingSource.index
              - (playingSource != null ? playingSource.index : 0) < MAXIMUM_BUFFER_AHEAD_SOURCES)) {
        // Try and obtain the next source to start loading.
        int sourceIndex = loadingSource == null ? pendingSourceIndex : loadingSource.index + 1;
        if (sourceCount == SampleSourceProvider.UNKNOWN_SOURCE_COUNT || sourceIndex < sourceCount) {
          // Attempt to create the next source.
          SampleSource sampleSource = sampleSourceProvider.createSource(sourceIndex);
          if (sampleSource != null) {
            Source newSource = new Source(renderers, trackSelector, sampleSource, sourceIndex);
            if (loadingSource != null) {
              loadingSource.setNextSource(newSource);
            }
            loadingSource = newSource;
            long startPositionUs = playingSource == null ? playbackInfo.positionUs : 0;
            setIsLoading(true);
            loadingSource.sampleSource.prepare(ExoPlayerImplInternal.this,
                loadControl.getAllocator(), startPositionUs);
          }
        }
      }

      if (loadingSource == null || loadingSource.isFullyBuffered()) {
        setIsLoading(false);
      } else if (loadingSource != null && loadingSource.needsContinueLoading) {
        maybeContinueLoading();
      }

      if (playingSource == null) {
        // We're waiting for the first source to be prepared.
        return;
      }

      // Update the playing and reading sources.
      if (playingSourceEndPositionUs == C.UNSET_TIME_US && playingSource.isFullyBuffered()) {
        playingSourceEndPositionUs = playingSource.offsetUs
            + playingSource.sampleSource.getDurationUs();
      }
      while (playingSource != readingSource && playingSource.nextSource != null
          && internalPositionUs >= playingSource.nextSource.offsetUs) {
        // All enabled renderers' streams have been read to the end, and the playback position
        // reached the end of the playing source, so advance playback to the next source.
        playingSource.release();
        setPlayingSource(playingSource.nextSource);
        playbackInfo = new PlaybackInfo(playingSource.index);
        updatePlaybackPositions();
        eventHandler.obtainMessage(MSG_SOURCE_CHANGED, playbackInfo).sendToTarget();
      }
      updateTimelineState();
      if (readingSource == null) {
        // The renderers have their final TrackStreams.
        return;
      }
      for (TrackRenderer renderer : enabledRenderers) {
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }
      if (readingSource.nextSource != null && readingSource.nextSource.prepared) {
        TrackSelectionArray oldTrackSelections = readingSource.trackSelections;
        readingSource = readingSource.nextSource;
        TrackSelectionArray newTrackSelections = readingSource.trackSelections;
        TrackGroupArray groups = readingSource.sampleSource.getTrackGroups();
        for (int i = 0; i < renderers.length; i++) {
          TrackRenderer renderer = renderers[i];
          TrackSelection oldSelection = oldTrackSelections.get(i);
          TrackSelection newSelection = newTrackSelections.get(i);
          if (oldSelection != null) {
            if (newSelection != null) {
              // Replace the renderer's TrackStream so the transition to playing the next source can
              // be seamless.
              Format[] formats = new Format[newSelection.length];
              for (int j = 0; j < formats.length; j++) {
                formats[j] = groups.get(newSelection.group).getFormat(newSelection.getTrack(j));
              }
              renderer.replaceTrackStream(formats, readingSource.trackStreams[i],
                  readingSource.offsetUs);
            } else {
              // The renderer will be disabled when transitioning to playing the next source. Mark
              // the TrackStream as final to play out any remaining data.
              renderer.setCurrentTrackStreamIsFinal();
            }
          }
        }
      } else if (sourceCount != SampleSourceProvider.UNKNOWN_SOURCE_COUNT
          && readingSource.index == sourceCount - 1) {
        readingSource = null;
        // This is the last source, so signal the renderers to read the end of the stream.
        for (TrackRenderer renderer : enabledRenderers) {
          renderer.setCurrentTrackStreamIsFinal();
        }
      }
    }

    public void handleSourcePrepared(SampleSource source) throws ExoPlaybackException {
      if (loadingSource == null || loadingSource.sampleSource != source) {
        // Stale event.
        return;
      }
      long startPositionUs = playingSource == null ? playbackInfo.positionUs : 0;
      loadingSource.handlePrepared(startPositionUs, loadControl);
      if (playingSource == null) {
        // This is the first prepared source, so start playing it.
        readingSource = loadingSource;
        setPlayingSource(readingSource);
        updateTimelineState();
      }
      maybeContinueLoading();
    }

    public void handleContinueLoadingRequested(SampleSource source) {
      if (loadingSource == null || loadingSource.sampleSource != source) {
        return;
      }
      maybeContinueLoading();
    }

    private void maybeContinueLoading() {
      long nextLoadPositionUs = loadingSource.sampleSource.getNextLoadPositionUs();
      if (nextLoadPositionUs != C.END_OF_SOURCE_US) {
        long positionUs = internalPositionUs - loadingSource.offsetUs;
        long bufferedDurationUs = nextLoadPositionUs - positionUs;
        boolean continueLoading = loadControl.shouldContinueLoading(bufferedDurationUs);
        setIsLoading(continueLoading);
        if (continueLoading) {
          loadingSource.needsContinueLoading = false;
          loadingSource.sampleSource.continueLoading(positionUs);
        } else {
          loadingSource.needsContinueLoading = true;
        }
      } else {
        setIsLoading(false);
      }
    }

    public long seekTo(int sourceIndex, long seekPositionUs) throws ExoPlaybackException {
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
        newPlayingSource.nextSource = null;
        setPlayingSource(newPlayingSource);
        updateTimelineState();
        readingSource = playingSource;
        loadingSource = playingSource;
        if (playingSource.hasEnabledTracks) {
          seekPositionUs = playingSource.sampleSource.seekToUs(seekPositionUs);
        }
        resetInternalPosition(seekPositionUs);
        maybeContinueLoading();
      } else {
        for (TrackRenderer renderer : enabledRenderers) {
          ensureStopped(renderer);
          renderer.disable();
        }
        enabledRenderers = new TrackRenderer[0];
        playingSource = null;
        readingSource = null;
        loadingSource = null;
        pendingSourceIndex = sourceIndex;
        resetInternalPosition(seekPositionUs);
      }
      return seekPositionUs;
    }

    public void reselectTracks() throws ExoPlaybackException {
      // Reselect tracks on each source in turn, until the selection changes.
      Source source = playingSource;
      boolean selectionsChangedForReadSource = true;
      while (true) {
        if (source == null || !source.prepared) {
          // The reselection did not change any prepared sources.
          return;
        }
        if (source.selectTracks()) {
          break;
        }
        if (source == readingSource) {
          // The track reselection didn't affect any source that has been read.
          selectionsChangedForReadSource = false;
        }
        source = source.nextSource;
      }

      if (selectionsChangedForReadSource) {
        // Release everything after the playing source because a renderer may have read data from a
        // track whose selection has now changed.
        source = playingSource.nextSource;
        while (source != null) {
          source.release();
          source = source.nextSource;
        }
        playingSource.nextSource = null;
        readingSource = playingSource;
        loadingSource = playingSource;
        playingSourceEndPositionUs = C.UNSET_TIME_US;

        // Update streams for the new selection, recreating all streams if reading ahead.
        boolean recreateStreams = readingSource != playingSource;
        TrackSelectionArray playingSourceOldTrackSelections = playingSource.sourceTrackSelections;
        playingSource.updateSourceTrackSelection(playbackInfo.positionUs, loadControl,
            recreateStreams);

        int enabledRendererCount = 0;
        boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
        for (int i = 0; i < renderers.length; i++) {
          TrackRenderer renderer = renderers[i];
          rendererWasEnabledFlags[i] = renderer.getState() != TrackRenderer.STATE_DISABLED;
          TrackSelection oldSelection = playingSourceOldTrackSelections.get(i);
          TrackSelection newSelection = playingSource.trackSelections.get(i);
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
        trackSelector.onSelectionActivated(playingSource.trackSelectionData);
        enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
      } else {
        // Release and re-prepare/buffer sources after the one whose selection changed.
        loadingSource = source;
        source = loadingSource.nextSource;
        while (source != null) {
          source.release();
          source = source.nextSource;
        }
        loadingSource.nextSource = null;
        long positionUs = Math.max(0, internalPositionUs - loadingSource.offsetUs);
        loadingSource.updateSourceTrackSelection(positionUs, loadControl, false);
      }
      maybeContinueLoading();
    }

    public void reset() {
      Source source = playingSource != null ? playingSource : loadingSource;
      while (source != null) {
        source.release();
        source = source.nextSource;
      }
      isReady = false;
      isEnded = false;
      playingSource = null;
      readingSource = null;
      loadingSource = null;
      playingSourceEndPositionUs = C.UNSET_TIME_US;
      pendingSourceIndex = 0;
      playbackInfo = new PlaybackInfo(0);
      eventHandler.obtainMessage(MSG_SOURCE_CHANGED, playbackInfo).sendToTarget();
    }

    private void setPlayingSource(Source source) throws ExoPlaybackException {
      int enabledRendererCount = 0;
      boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
      for (int i = 0; i < renderers.length; i++) {
        TrackRenderer renderer = renderers[i];
        rendererWasEnabledFlags[i] = renderer.getState() != TrackRenderer.STATE_DISABLED;
        TrackSelection newSelection = source.trackSelections.get(i);
        if (newSelection != null) {
          // The renderer should be enabled when playing the new source.
          enabledRendererCount++;
        } else if (rendererWasEnabledFlags[i]) {
          // The renderer should be disabled when playing the new source.
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

      trackSelector.onSelectionActivated(source.trackSelectionData);
      playingSource = source;
      playingSourceEndPositionUs = C.UNSET_TIME_US;
      enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
    }

    private void updateTimelineState() {
      isReady = playingSourceEndPositionUs == C.UNSET_TIME_US
          || internalPositionUs < playingSourceEndPositionUs
          || (playingSource.nextSource != null && playingSource.nextSource.prepared);
      int sourceCount = sampleSourceProvider.getSourceCount();
      isEnded = sourceCount != SampleSourceProvider.UNKNOWN_SOURCE_COUNT
          && playingSource.index == sourceCount - 1;
    }

    private void enableRenderers(boolean[] rendererWasEnabledFlags, int enabledRendererCount)
        throws ExoPlaybackException {
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
            // Build an array of formats contained by the selection.
            Format[] formats = new Format[newSelection.length];
            for (int j = 0; j < formats.length; j++) {
              formats[j] = trackGroups.get(newSelection.group).getFormat(newSelection.getTrack(j));
            }
            // Enable the renderer.
            renderer.enable(formats, playingSource.trackStreams[i], internalPositionUs, joining,
                playingSource.offsetUs);
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

    private final TrackRenderer[] renderers;
    private final TrackSelector trackSelector;

    public final SampleSource sampleSource;
    public final int index;
    public final TrackStream[] trackStreams;

    public boolean prepared;
    public boolean hasEnabledTracks;
    public long offsetUs;
    public Source nextSource;
    public boolean needsContinueLoading;

    private Object trackSelectionData;
    private TrackSelectionArray trackSelections;
    private TrackSelectionArray sourceTrackSelections;

    public Source(TrackRenderer[] renderers, TrackSelector trackSelector, SampleSource sampleSource,
        int index) {
      this.renderers = renderers;
      this.trackSelector = trackSelector;
      this.sampleSource = sampleSource;
      this.index = index;
      trackStreams = new TrackStream[renderers.length];
    }

    public void setNextSource(Source nextSource) {
      this.nextSource = nextSource;
      nextSource.offsetUs = offsetUs + sampleSource.getDurationUs();
    }

    public boolean isFullyBuffered() {
      return prepared && (!hasEnabledTracks
          || sampleSource.getBufferedPositionUs() == C.END_OF_SOURCE_US);
    }

    public void handlePrepared(long positionUs, LoadControl loadControl)
        throws ExoPlaybackException {
      prepared = true;
      selectTracks();
      updateSourceTrackSelection(positionUs, loadControl, false);
    }

    public boolean selectTracks() throws ExoPlaybackException {
      Pair<TrackSelectionArray, Object> result =
          trackSelector.selectTracks(renderers, sampleSource.getTrackGroups());
      TrackSelectionArray newTrackSelections = result.first;
      if (newTrackSelections.equals(sourceTrackSelections)) {
        return false;
      }
      trackSelections = newTrackSelections;
      trackSelectionData = result.second;
      return true;
    }

    public void updateSourceTrackSelection(long positionUs, LoadControl loadControl,
        boolean forceRecreateStreams) throws ExoPlaybackException {
      // Populate lists of streams that are being disabled/newly enabled.
      ArrayList<TrackStream> oldStreams = new ArrayList<>();
      ArrayList<TrackSelection> newSelections = new ArrayList<>();
      for (int i = 0; i < trackSelections.length; i++) {
        TrackSelection oldSelection =
            sourceTrackSelections == null ? null : sourceTrackSelections.get(i);
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

      // Disable streams on the source and get new streams for updated/newly-enabled tracks.
      TrackStream[] newStreams = sampleSource.selectTracks(oldStreams, newSelections, positionUs);
      sourceTrackSelections = trackSelections;
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
      loadControl.onTrackSelections(renderers, sampleSource.getTrackGroups(), trackSelections);
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
