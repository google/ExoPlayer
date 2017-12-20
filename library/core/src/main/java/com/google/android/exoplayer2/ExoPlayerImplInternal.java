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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import com.google.android.exoplayer2.DefaultMediaClock.PlaybackParameterListener;
import com.google.android.exoplayer2.MediaPeriodInfoSequence.MediaPeriodInfo;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.source.ClippingMediaPeriod;
import com.google.android.exoplayer2.source.EmptySampleStream;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/** Implements the internal behavior of {@link ExoPlayerImpl}. */
/* package */ final class ExoPlayerImplInternal
    implements Handler.Callback,
        MediaPeriod.Callback,
        TrackSelector.InvalidationListener,
        MediaSource.Listener,
        PlaybackParameterListener,
        PlayerMessage.Sender {

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_PLAYBACK_INFO_CHANGED = 0;
  public static final int MSG_PLAYBACK_PARAMETERS_CHANGED = 1;
  public static final int MSG_ERROR = 2;

  // Internal messages
  private static final int MSG_PREPARE = 0;
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_SET_PLAYBACK_PARAMETERS = 4;
  private static final int MSG_SET_SEEK_PARAMETERS = 5;
  private static final int MSG_STOP = 6;
  private static final int MSG_RELEASE = 7;
  private static final int MSG_REFRESH_SOURCE_INFO = 8;
  private static final int MSG_PERIOD_PREPARED = 9;
  private static final int MSG_SOURCE_CONTINUE_LOADING_REQUESTED = 10;
  private static final int MSG_TRACK_SELECTION_INVALIDATED = 11;
  private static final int MSG_CUSTOM = 12;
  private static final int MSG_SET_REPEAT_MODE = 13;
  private static final int MSG_SET_SHUFFLE_ENABLED = 14;

  private static final int PREPARING_SOURCE_INTERVAL_MS = 10;
  private static final int RENDERING_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;

  /**
   * Limits the maximum number of periods to buffer ahead of the current playing period. The
   * buffering policy normally prevents buffering too far ahead, but the policy could allow too many
   * small periods to be buffered if the period count were not limited.
   */
  private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;

  /**
   * Offset added to all sample timestamps read by renderers to make them non-negative. This is
   * provided for convenience of sources that may return negative timestamps due to prerolling
   * samples from a keyframe before their first sample with timestamp zero, so it must be set to a
   * value greater than or equal to the maximum key-frame interval in seekable periods.
   */
  private static final int RENDERER_TIMESTAMP_OFFSET_US = 60000000;

  private final Renderer[] renderers;
  private final RendererCapabilities[] rendererCapabilities;
  private final TrackSelector trackSelector;
  private final TrackSelectorResult emptyTrackSelectorResult;
  private final LoadControl loadControl;
  private final Handler handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;
  private final ExoPlayer player;
  private final Timeline.Window window;
  private final Timeline.Period period;
  private final MediaPeriodInfoSequence mediaPeriodInfoSequence;
  private final long backBufferDurationUs;
  private final boolean retainBackBufferFromKeyframe;
  private final DefaultMediaClock mediaClock;
  private final PlaybackInfoUpdate playbackInfoUpdate;
  private final ArrayList<CustomMessageInfo> customMessageInfos;

  @SuppressWarnings("unused")
  private SeekParameters seekParameters;

  private PlaybackInfo playbackInfo;
  private MediaSource mediaSource;
  private Renderer[] enabledRenderers;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private @Player.RepeatMode int repeatMode;
  private boolean shuffleModeEnabled;
  private long elapsedRealtimeUs;

  private int pendingPrepareCount;
  private SeekPosition pendingInitialSeekPosition;
  private long rendererPositionUs;
  private int nextCustomMessageInfoIndex;

  private MediaPeriodHolder loadingPeriodHolder;
  private MediaPeriodHolder readingPeriodHolder;
  private MediaPeriodHolder playingPeriodHolder;

  public ExoPlayerImplInternal(
      Renderer[] renderers,
      TrackSelector trackSelector,
      TrackSelectorResult emptyTrackSelectorResult,
      LoadControl loadControl,
      boolean playWhenReady,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Handler eventHandler,
      ExoPlayer player) {
    this.renderers = renderers;
    this.trackSelector = trackSelector;
    this.emptyTrackSelectorResult = emptyTrackSelectorResult;
    this.loadControl = loadControl;
    this.playWhenReady = playWhenReady;
    this.repeatMode = repeatMode;
    this.shuffleModeEnabled = shuffleModeEnabled;
    this.eventHandler = eventHandler;
    this.player = player;

    backBufferDurationUs = loadControl.getBackBufferDurationUs();
    retainBackBufferFromKeyframe = loadControl.retainBackBufferFromKeyframe();

    seekParameters = SeekParameters.DEFAULT;
    playbackInfo =
        new PlaybackInfo(
            /* timeline= */ null, /* startPositionUs= */ C.TIME_UNSET, emptyTrackSelectorResult);
    playbackInfoUpdate = new PlaybackInfoUpdate();
    rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      renderers[i].setIndex(i);
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    mediaClock = new DefaultMediaClock(this);
    customMessageInfos = new ArrayList<>();
    enabledRenderers = new Renderer[0];
    window = new Timeline.Window();
    period = new Timeline.Period();
    mediaPeriodInfoSequence = new MediaPeriodInfoSequence();
    trackSelector.init(this);

    // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
    // not normally change to this priority" is incorrect.
    internalPlaybackThread = new HandlerThread("ExoPlayerImplInternal:Handler",
        Process.THREAD_PRIORITY_AUDIO);
    internalPlaybackThread.start();
    handler = new Handler(internalPlaybackThread.getLooper(), this);
  }

  public void prepare(MediaSource mediaSource, boolean resetPosition) {
    handler.obtainMessage(MSG_PREPARE, resetPosition ? 1 : 0, 0, mediaSource)
        .sendToTarget();
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
    handler.obtainMessage(MSG_SEEK_TO, new SeekPosition(timeline, windowIndex, positionUs))
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

  @Override
  public synchronized void sendMessage(
      PlayerMessage message, PlayerMessage.Sender.Listener listener) {
    if (released) {
      Log.w(TAG, "Ignoring messages sent after release.");
      listener.onMessageDeleted();
      return;
    }
    handler.obtainMessage(MSG_CUSTOM, new CustomMessageInfo(message, listener)).sendToTarget();
  }

  public synchronized void release() {
    if (released) {
      return;
    }
    handler.sendEmptyMessage(MSG_RELEASE);
    boolean wasInterrupted = false;
    while (!released) {
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

  public Looper getPlaybackLooper() {
    return internalPlaybackThread.getLooper();
  }

  // MediaSource.Listener implementation.

  @Override
  public void onSourceInfoRefreshed(MediaSource source, Timeline timeline, Object manifest) {
    handler.obtainMessage(MSG_REFRESH_SOURCE_INFO,
        new MediaSourceRefreshInfo(source, timeline, manifest)).sendToTarget();
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
    eventHandler.obtainMessage(MSG_PLAYBACK_PARAMETERS_CHANGED, playbackParameters).sendToTarget();
    updateTrackSelectionPlaybackSpeed(playbackParameters.speed);
  }

  // Handler.Callback implementation.

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_PREPARE:
          prepareInternal((MediaSource) msg.obj, msg.arg1 != 0);
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
        case MSG_STOP:
          stopInternal(/* reset= */ msg.arg1 != 0, /* acknowledgeStop= */ true);
          break;
        case MSG_PERIOD_PREPARED:
          handlePeriodPrepared((MediaPeriod) msg.obj);
          break;
        case MSG_REFRESH_SOURCE_INFO:
          handleSourceInfoRefreshed((MediaSourceRefreshInfo) msg.obj);
          break;
        case MSG_SOURCE_CONTINUE_LOADING_REQUESTED:
          handleContinueLoadingRequested((MediaPeriod) msg.obj);
          break;
        case MSG_TRACK_SELECTION_INVALIDATED:
          reselectTracksInternal();
          break;
        case MSG_CUSTOM:
          sendMessageInternal((CustomMessageInfo) msg.obj);
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
      Log.e(TAG, "Renderer error.", e);
      stopInternal(/* reset= */ false, /* acknowledgeStop= */ false);
      eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
      maybeNotifyPlaybackInfoChanged();
    } catch (IOException e) {
      Log.e(TAG, "Source error.", e);
      stopInternal(/* reset= */ false, /* acknowledgeStop= */ false);
      eventHandler.obtainMessage(MSG_ERROR, ExoPlaybackException.createForSource(e)).sendToTarget();
      maybeNotifyPlaybackInfoChanged();
    } catch (RuntimeException e) {
      Log.e(TAG, "Internal runtime error.", e);
      stopInternal(/* reset= */ false, /* acknowledgeStop= */ false);
      eventHandler.obtainMessage(MSG_ERROR, ExoPlaybackException.createForUnexpected(e))
          .sendToTarget();
      maybeNotifyPlaybackInfoChanged();
    }
    return true;
  }

  // Private methods.

  private void setState(int state) {
    if (playbackInfo.playbackState != state) {
      playbackInfo = playbackInfo.copyWithPlaybackState(state);
    }
  }

  private void setIsLoading(boolean isLoading) {
    if (playbackInfo.isLoading != isLoading) {
      playbackInfo = playbackInfo.copyWithIsLoading(isLoading);
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

  private void prepareInternal(MediaSource mediaSource, boolean resetPosition) {
    pendingPrepareCount++;
    resetInternal(/* releaseMediaSource= */ true, resetPosition, /* resetState= */ true);
    loadControl.onPrepared();
    this.mediaSource = mediaSource;
    setState(Player.STATE_BUFFERING);
    mediaSource.prepareSource(player, /* isTopLevelSource= */ true, /* listener= */ this);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
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
    mediaPeriodInfoSequence.setRepeatMode(repeatMode);
    validateExistingPeriodHolders();
  }

  private void setShuffleModeEnabledInternal(boolean shuffleModeEnabled)
      throws ExoPlaybackException {
    this.shuffleModeEnabled = shuffleModeEnabled;
    mediaPeriodInfoSequence.setShuffleModeEnabled(shuffleModeEnabled);
    validateExistingPeriodHolders();
  }

  private void validateExistingPeriodHolders() throws ExoPlaybackException {
    // Find the last existing period holder that matches the new period order.
    MediaPeriodHolder lastValidPeriodHolder = playingPeriodHolder != null
        ? playingPeriodHolder : loadingPeriodHolder;
    if (lastValidPeriodHolder == null) {
      return;
    }
    while (true) {
      int nextPeriodIndex = playbackInfo.timeline.getNextPeriodIndex(
          lastValidPeriodHolder.info.id.periodIndex, period, window, repeatMode,
          shuffleModeEnabled);
      while (lastValidPeriodHolder.next != null
          && !lastValidPeriodHolder.info.isLastInTimelinePeriod) {
        lastValidPeriodHolder = lastValidPeriodHolder.next;
      }
      if (nextPeriodIndex == C.INDEX_UNSET || lastValidPeriodHolder.next == null
          || lastValidPeriodHolder.next.info.id.periodIndex != nextPeriodIndex) {
        break;
      }
      lastValidPeriodHolder = lastValidPeriodHolder.next;
    }

    // Release any period holders that don't match the new period order.
    int loadingPeriodHolderIndex = loadingPeriodHolder.index;
    int readingPeriodHolderIndex =
        readingPeriodHolder != null ? readingPeriodHolder.index : C.INDEX_UNSET;
    if (lastValidPeriodHolder.next != null) {
      releasePeriodHoldersFrom(lastValidPeriodHolder.next);
      lastValidPeriodHolder.next = null;
    }

    // Update the period info for the last holder, as it may now be the last period in the timeline.
    lastValidPeriodHolder.info =
        mediaPeriodInfoSequence.getUpdatedMediaPeriodInfo(lastValidPeriodHolder.info);

    // Handle cases where loadingPeriodHolder or readingPeriodHolder have been removed.
    boolean seenLoadingPeriodHolder = loadingPeriodHolderIndex <= lastValidPeriodHolder.index;
    if (!seenLoadingPeriodHolder) {
      loadingPeriodHolder = lastValidPeriodHolder;
    }
    boolean seenReadingPeriodHolder = readingPeriodHolderIndex != C.INDEX_UNSET
        && readingPeriodHolderIndex <= lastValidPeriodHolder.index;
    if (!seenReadingPeriodHolder && playingPeriodHolder != null) {
      // Renderers may have read from a period that's been removed. Seek back to the current
      // position of the playing period to make sure none of the removed period is played.
      MediaPeriodId periodId = playingPeriodHolder.info.id;
      long newPositionUs = seekToPeriodPosition(periodId, playbackInfo.positionUs);
      if (newPositionUs != playbackInfo.positionUs) {
        playbackInfo =
            playbackInfo.fromNewPosition(periodId, newPositionUs, playbackInfo.contentPositionUs);
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
    if (playingPeriodHolder == null) {
      return;
    }

    // Update the playback position.
    long periodPositionUs = playingPeriodHolder.mediaPeriod.readDiscontinuity();
    if (periodPositionUs != C.TIME_UNSET) {
      resetRendererPosition(periodPositionUs);
      // A MediaPeriod may report a discontinuity at the current playback position to ensure the
      // renderers are flushed. Only report the discontinuity externally if the position changed.
      if (periodPositionUs != playbackInfo.positionUs) {
        playbackInfo = playbackInfo.fromNewPosition(playbackInfo.periodId, periodPositionUs,
            playbackInfo.contentPositionUs);
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
      }
    } else {
      rendererPositionUs = mediaClock.syncAndGetPositionUs();
      periodPositionUs = playingPeriodHolder.toPeriodTime(rendererPositionUs);
      maybeTriggerCustomMessages(playbackInfo.positionUs, periodPositionUs);
      playbackInfo.positionUs = periodPositionUs;
    }
    elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;

    // Update the buffered position.
    long bufferedPositionUs = enabledRenderers.length == 0 ? C.TIME_END_OF_SOURCE
        : playingPeriodHolder.mediaPeriod.getBufferedPositionUs();
    playbackInfo.bufferedPositionUs = bufferedPositionUs == C.TIME_END_OF_SOURCE
        ? playingPeriodHolder.info.durationUs : bufferedPositionUs;
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    updatePeriods();
    if (playingPeriodHolder == null) {
      // We're still waiting for the first period to be prepared.
      maybeThrowPeriodPrepareError();
      scheduleNextWork(operationStartTimeMs, PREPARING_SOURCE_INTERVAL_MS);
      return;
    }

    TraceUtil.beginSection("doSomeWork");

    updatePlaybackPositions();
    playingPeriodHolder.mediaPeriod.discardBuffer(playbackInfo.positionUs - backBufferDurationUs,
        retainBackBufferFromKeyframe);

    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (Renderer renderer : enabledRenderers) {
      // TODO: Each renderer should return the maximum delay before which it wishes to be called
      // again. The minimum of these values should then be used as the delay before the next
      // invocation of this method.
      renderer.render(rendererPositionUs, elapsedRealtimeUs);
      allRenderersEnded = allRenderersEnded && renderer.isEnded();
      // Determine whether the renderer is ready (or ended). We override to assume the renderer is
      // ready if it needs the next sample stream. This is necessary to avoid getting stuck if
      // tracks in the current period have uneven durations. See:
      // https://github.com/google/ExoPlayer/issues/1874
      boolean rendererReadyOrEnded = renderer.isReady() || renderer.isEnded()
          || rendererWaitingForNextStream(renderer);
      if (!rendererReadyOrEnded) {
        renderer.maybeThrowStreamError();
      }
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded;
    }

    if (!allRenderersReadyOrEnded) {
      maybeThrowPeriodPrepareError();
    }

    long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
    if (allRenderersEnded
        && (playingPeriodDurationUs == C.TIME_UNSET
        || playingPeriodDurationUs <= playbackInfo.positionUs)
        && playingPeriodHolder.info.isFinal) {
      setState(Player.STATE_ENDED);
      stopRenderers();
    } else if (playbackInfo.playbackState == Player.STATE_BUFFERING) {
      float playbackSpeed = mediaClock.getPlaybackParameters().speed;
      boolean isNewlyReady = enabledRenderers.length > 0
          ? (allRenderersReadyOrEnded && loadingPeriodHolder.haveSufficientBuffer(
              rendererPositionUs, playbackSpeed, rebuffering))
          : isTimelineReady(playingPeriodDurationUs);
      if (isNewlyReady) {
        setState(Player.STATE_READY);
        if (playWhenReady) {
          startRenderers();
        }
      }
    } else if (playbackInfo.playbackState == Player.STATE_READY) {
      boolean isStillReady = enabledRenderers.length > 0 ? allRenderersReadyOrEnded
          : isTimelineReady(playingPeriodDurationUs);
      if (!isStillReady) {
        rebuffering = playWhenReady;
        setState(Player.STATE_BUFFERING);
        stopRenderers();
      }
    }

    if (playbackInfo.playbackState == Player.STATE_BUFFERING) {
      for (Renderer renderer : enabledRenderers) {
        renderer.maybeThrowStreamError();
      }
    }

    if ((playWhenReady && playbackInfo.playbackState == Player.STATE_READY)
        || playbackInfo.playbackState == Player.STATE_BUFFERING) {
      scheduleNextWork(operationStartTimeMs, RENDERING_INTERVAL_MS);
    } else if (enabledRenderers.length != 0 && playbackInfo.playbackState != Player.STATE_ENDED) {
      scheduleNextWork(operationStartTimeMs, IDLE_INTERVAL_MS);
    } else {
      handler.removeMessages(MSG_DO_SOME_WORK);
    }

    TraceUtil.endSection();
  }

  private void scheduleNextWork(long thisOperationStartTimeMs, long intervalMs) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    long nextOperationStartTimeMs = thisOperationStartTimeMs + intervalMs;
    long nextOperationDelayMs = nextOperationStartTimeMs - SystemClock.elapsedRealtime();
    if (nextOperationDelayMs <= 0) {
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    } else {
      handler.sendEmptyMessageDelayed(MSG_DO_SOME_WORK, nextOperationDelayMs);
    }
  }

  private void seekToInternal(SeekPosition seekPosition) throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline = playbackInfo.timeline;
    if (mediaSource == null || timeline == null) {
      pendingInitialSeekPosition = seekPosition;
      return;
    }

    boolean seekPositionAdjusted = seekPosition.windowPositionUs == C.TIME_UNSET;
    try {
      Pair<Integer, Long> periodPosition =
          resolveSeekPosition(seekPosition, /* trySubsequentPeriods= */ true);
      if (periodPosition == null) {
        // The seek position was valid for the timeline that it was performed into, but the
        // timeline has changed and a suitable seek position could not be resolved in the new one.
        setState(Player.STATE_ENDED);
        // Reset, but retain the source so that it can still be used should a seek occur.
        resetInternal(
            /* releaseMediaSource= */ false, /* resetPosition= */ true, /* resetState= */ false);
        seekPositionAdjusted = true;
        return;
      }

      int periodIndex = periodPosition.first;
      long periodPositionUs = periodPosition.second;
      long contentPositionUs = periodPositionUs;
      MediaPeriodId periodId =
          mediaPeriodInfoSequence.resolvePeriodPositionForAds(periodIndex, periodPositionUs);
      if (periodId.isAd()) {
        seekPositionAdjusted = true;
        periodPositionUs = 0;
      }
      try {
        long newPeriodPositionUs = periodPositionUs;
        if (periodId.equals(playbackInfo.periodId)) {
          if (playingPeriodHolder != null && newPeriodPositionUs != 0) {
            newPeriodPositionUs =
                playingPeriodHolder.mediaPeriod.getAdjustedSeekPositionUs(
                    newPeriodPositionUs, seekParameters);
          }
          if ((newPeriodPositionUs / 1000) == (playbackInfo.positionUs / 1000)) {
            // Seek will be performed to the current position. Do nothing.
            periodPositionUs = playbackInfo.positionUs;
            return;
          }
        }
        newPeriodPositionUs = seekToPeriodPosition(periodId, newPeriodPositionUs);
        seekPositionAdjusted |= periodPositionUs != newPeriodPositionUs;
        periodPositionUs = newPeriodPositionUs;
      } finally {
        playbackInfo = playbackInfo.fromNewPosition(periodId, periodPositionUs, contentPositionUs);
      }
    } finally {
      if (seekPositionAdjusted) {
        playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT);
      }
    }
  }

  private long seekToPeriodPosition(MediaPeriodId periodId, long periodPositionUs)
      throws ExoPlaybackException {
    stopRenderers();
    rebuffering = false;
    setState(Player.STATE_BUFFERING);

    MediaPeriodHolder newPlayingPeriodHolder = null;
    if (playingPeriodHolder == null) {
      // We're still waiting for the first period to be prepared.
      if (loadingPeriodHolder != null) {
        loadingPeriodHolder.release();
      }
    } else {
      // Clear the timeline, but keep the requested period if it is already prepared.
      MediaPeriodHolder periodHolder = playingPeriodHolder;
      while (periodHolder != null) {
        if (newPlayingPeriodHolder == null
            && shouldKeepPeriodHolder(periodId, periodPositionUs, periodHolder)) {
          newPlayingPeriodHolder = periodHolder;
        } else {
          periodHolder.release();
        }
        periodHolder = periodHolder.next;
      }
    }

    // Disable all the renderers if the period being played is changing, or if the renderers are
    // reading from a period other than the one being played.
    if (playingPeriodHolder != newPlayingPeriodHolder
        || playingPeriodHolder != readingPeriodHolder) {
      for (Renderer renderer : enabledRenderers) {
        disableRenderer(renderer);
      }
      enabledRenderers = new Renderer[0];
      playingPeriodHolder = null;
    }

    // Update the holders.
    if (newPlayingPeriodHolder != null) {
      newPlayingPeriodHolder.next = null;
      loadingPeriodHolder = newPlayingPeriodHolder;
      readingPeriodHolder = newPlayingPeriodHolder;
      setPlayingPeriodHolder(newPlayingPeriodHolder);
      if (playingPeriodHolder.hasEnabledTracks) {
        periodPositionUs = playingPeriodHolder.mediaPeriod.seekToUs(periodPositionUs);
        playingPeriodHolder.mediaPeriod.discardBuffer(periodPositionUs - backBufferDurationUs,
            retainBackBufferFromKeyframe);
      }
      resetRendererPosition(periodPositionUs);
      maybeContinueLoading();
    } else {
      loadingPeriodHolder = null;
      readingPeriodHolder = null;
      playingPeriodHolder = null;
      resetRendererPosition(periodPositionUs);
    }

    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    return periodPositionUs;
  }

  private boolean shouldKeepPeriodHolder(MediaPeriodId seekPeriodId, long positionUs,
      MediaPeriodHolder holder) {
    if (seekPeriodId.equals(holder.info.id) && holder.prepared) {
      playbackInfo.timeline.getPeriod(holder.info.id.periodIndex, period);
      int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(positionUs);
      if (nextAdGroupIndex == C.INDEX_UNSET
          || period.getAdGroupTimeUs(nextAdGroupIndex) == holder.info.endPositionUs) {
        return true;
      }
    }
    return false;
  }

  private void resetRendererPosition(long periodPositionUs) throws ExoPlaybackException {
    rendererPositionUs = playingPeriodHolder == null
        ? periodPositionUs + RENDERER_TIMESTAMP_OFFSET_US
        : playingPeriodHolder.toRendererTime(periodPositionUs);
    mediaClock.resetPosition(rendererPositionUs);
    for (Renderer renderer : enabledRenderers) {
      renderer.resetPosition(rendererPositionUs);
    }
  }

  private void setPlaybackParametersInternal(PlaybackParameters playbackParameters) {
    mediaClock.setPlaybackParameters(playbackParameters);
  }

  private void setSeekParametersInternal(SeekParameters seekParameters) {
    this.seekParameters = seekParameters;
  }

  private void stopInternal(boolean reset, boolean acknowledgeStop) {
    resetInternal(
        /* releaseMediaSource= */ true, /* resetPosition= */ reset, /* resetState= */ reset);
    playbackInfoUpdate.incrementPendingOperationAcks(
        pendingPrepareCount + (acknowledgeStop ? 1 : 0));
    pendingPrepareCount = 0;
    loadControl.onStopped();
    setState(Player.STATE_IDLE);
  }

  private void releaseInternal() {
    resetInternal(
        /* releaseMediaSource= */ true, /* resetPosition= */ true, /* resetState= */ true);
    loadControl.onReleased();
    setState(Player.STATE_IDLE);
    internalPlaybackThread.quit();
    synchronized (this) {
      released = true;
      notifyAll();
    }
  }

  private void resetInternal(
      boolean releaseMediaSource, boolean resetPosition, boolean resetState) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    rebuffering = false;
    mediaClock.stop();
    rendererPositionUs = RENDERER_TIMESTAMP_OFFSET_US;
    for (Renderer renderer : enabledRenderers) {
      try {
        disableRenderer(renderer);
      } catch (ExoPlaybackException | RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Stop failed.", e);
      }
    }
    enabledRenderers = new Renderer[0];
    releasePeriodHoldersFrom(playingPeriodHolder != null ? playingPeriodHolder
        : loadingPeriodHolder);
    loadingPeriodHolder = null;
    readingPeriodHolder = null;
    playingPeriodHolder = null;
    setIsLoading(false);
    Timeline timeline = playbackInfo.timeline;
    int firstPeriodIndex =
        timeline == null || timeline.isEmpty()
            ? 0
            : timeline.getWindow(timeline.getFirstWindowIndex(shuffleModeEnabled), window)
                .firstPeriodIndex;
    if (resetPosition) {
      pendingInitialSeekPosition = null;
    }
    if (resetState) {
      mediaPeriodInfoSequence.setTimeline(null);
      for (CustomMessageInfo customMessageInfo : customMessageInfos) {
        customMessageInfo.listener.onMessageDeleted();
      }
      customMessageInfos.clear();
      nextCustomMessageInfoIndex = 0;
    }
    playbackInfo =
        new PlaybackInfo(
            resetState ? null : playbackInfo.timeline,
            resetState ? null : playbackInfo.manifest,
            resetPosition ? new MediaPeriodId(firstPeriodIndex) : playbackInfo.periodId,
            // Set the start position to TIME_UNSET so that a subsequent seek to 0 isn't ignored.
            resetPosition ? C.TIME_UNSET : playbackInfo.startPositionUs,
            resetPosition ? C.TIME_UNSET : playbackInfo.contentPositionUs,
            playbackInfo.playbackState,
            /* isLoading= */ false,
            resetState ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult);
    if (releaseMediaSource) {
      if (mediaSource != null) {
        mediaSource.releaseSource();
        mediaSource = null;
      }
    }
  }

  private void sendMessageInternal(CustomMessageInfo customMessageInfo) {
    if (customMessageInfo.message.getPositionMs() == C.TIME_UNSET) {
      // If no delivery time is specified, trigger immediate message delivery.
      sendCustomMessagesToTarget(customMessageInfo);
    } else if (playbackInfo.timeline == null) {
      // Still waiting for initial timeline to resolve position.
      customMessageInfos.add(customMessageInfo);
    } else {
      if (resolveCustomMessagePosition(customMessageInfo)) {
        customMessageInfos.add(customMessageInfo);
        // Ensure new message is inserted according to playback order.
        Collections.sort(customMessageInfos);
      } else {
        customMessageInfo.listener.onMessageDeleted();
      }
    }
  }

  private void sendCustomMessagesToTarget(final CustomMessageInfo customMessageInfo) {
    final Runnable handleMessageRunnable =
        new Runnable() {
          @Override
          public void run() {
            try {
              customMessageInfo
                  .message
                  .getTarget()
                  .handleMessage(
                      customMessageInfo.message.getType(), customMessageInfo.message.getMessage());
            } catch (ExoPlaybackException e) {
              eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
            } finally {
              customMessageInfo.listener.onMessageDelivered();
              if (customMessageInfo.message.getDeleteAfterDelivery()) {
                customMessageInfo.listener.onMessageDeleted();
              }
              // The message may have caused something to change that now requires us to do
              // work.
              handler.sendEmptyMessage(MSG_DO_SOME_WORK);
            }
          }
        };
    handler.post(
        new Runnable() {
          @Override
          public void run() {
            customMessageInfo.message.getHandler().post(handleMessageRunnable);
          }
        });
  }

  private void resolveCustomMessagePositions() {
    for (int i = customMessageInfos.size() - 1; i >= 0; i--) {
      if (!resolveCustomMessagePosition(customMessageInfos.get(i))) {
        // Remove messages if new position can't be resolved.
        customMessageInfos.get(i).listener.onMessageDeleted();
        customMessageInfos.remove(i);
      }
    }
    // Re-sort messages by playback order.
    Collections.sort(customMessageInfos);
  }

  private boolean resolveCustomMessagePosition(CustomMessageInfo customMessageInfo) {
    if (customMessageInfo.resolvedPeriodUid == null) {
      // Position is still unresolved. Try to find window in current timeline.
      Pair<Integer, Long> periodPosition =
          resolveSeekPosition(
              new SeekPosition(
                  customMessageInfo.message.getTimeline(),
                  customMessageInfo.message.getWindowIndex(),
                  C.msToUs(customMessageInfo.message.getPositionMs())),
              /* trySubsequentPeriods= */ false);
      if (periodPosition == null) {
        return false;
      }
      customMessageInfo.setResolvedPosition(
          periodPosition.first,
          periodPosition.second,
          playbackInfo.timeline.getPeriod(periodPosition.first, period, true).uid);
    } else {
      // Position has been resolved for a previous timeline. Try to find the updated period index.
      int index = playbackInfo.timeline.getIndexOfPeriod(customMessageInfo.resolvedPeriodUid);
      if (index == C.INDEX_UNSET) {
        return false;
      }
      customMessageInfo.resolvedPeriodIndex = index;
    }
    return true;
  }

  private void maybeTriggerCustomMessages(long oldPeriodPositionUs, long newPeriodPositionUs) {
    if (customMessageInfos.isEmpty() || playbackInfo.periodId.isAd()) {
      return;
    }
    // If this is the first call from the start position, include oldPeriodPositionUs in potential
    // trigger positions.
    if (playbackInfo.startPositionUs == oldPeriodPositionUs) {
      oldPeriodPositionUs--;
    }
    // Correct next index if necessary (e.g. after seeking, timeline changes, or new messages)
    int currentPeriodIndex = playbackInfo.periodId.periodIndex;
    CustomMessageInfo prevInfo =
        nextCustomMessageInfoIndex > 0
            ? customMessageInfos.get(nextCustomMessageInfoIndex - 1)
            : null;
    while (prevInfo != null
        && (prevInfo.resolvedPeriodIndex > currentPeriodIndex
            || (prevInfo.resolvedPeriodIndex == currentPeriodIndex
                && prevInfo.resolvedPeriodTimeUs > oldPeriodPositionUs))) {
      nextCustomMessageInfoIndex--;
      prevInfo =
          nextCustomMessageInfoIndex > 0
              ? customMessageInfos.get(nextCustomMessageInfoIndex - 1)
              : null;
    }
    CustomMessageInfo nextInfo =
        nextCustomMessageInfoIndex < customMessageInfos.size()
            ? customMessageInfos.get(nextCustomMessageInfoIndex)
            : null;
    while (nextInfo != null
        && nextInfo.resolvedPeriodUid != null
        && (nextInfo.resolvedPeriodIndex < currentPeriodIndex
            || (nextInfo.resolvedPeriodIndex == currentPeriodIndex
                && nextInfo.resolvedPeriodTimeUs <= oldPeriodPositionUs))) {
      nextCustomMessageInfoIndex++;
      nextInfo =
          nextCustomMessageInfoIndex < customMessageInfos.size()
              ? customMessageInfos.get(nextCustomMessageInfoIndex)
              : null;
    }
    // Check if any message falls within the covered time span.
    while (nextInfo != null
        && nextInfo.resolvedPeriodUid != null
        && nextInfo.resolvedPeriodIndex == currentPeriodIndex
        && nextInfo.resolvedPeriodTimeUs > oldPeriodPositionUs
        && nextInfo.resolvedPeriodTimeUs <= newPeriodPositionUs) {
      sendCustomMessagesToTarget(nextInfo);
      if (nextInfo.message.getDeleteAfterDelivery()) {
        customMessageInfos.remove(nextCustomMessageInfoIndex);
      } else {
        nextCustomMessageInfoIndex++;
      }
      nextInfo =
          nextCustomMessageInfoIndex < customMessageInfos.size()
              ? customMessageInfos.get(nextCustomMessageInfoIndex)
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
    if (playingPeriodHolder == null) {
      // We don't have tracks yet, so we don't care.
      return;
    }
    float playbackSpeed = mediaClock.getPlaybackParameters().speed;
    // Reselect tracks on each period in turn, until the selection changes.
    MediaPeriodHolder periodHolder = playingPeriodHolder;
    boolean selectionsChangedForReadPeriod = true;
    while (true) {
      if (periodHolder == null || !periodHolder.prepared) {
        // The reselection did not change any prepared periods.
        return;
      }
      if (periodHolder.selectTracks(playbackSpeed)) {
        // Selected tracks have changed for this period.
        break;
      }
      if (periodHolder == readingPeriodHolder) {
        // The track reselection didn't affect any period that has been read.
        selectionsChangedForReadPeriod = false;
      }
      periodHolder = periodHolder.next;
    }

    if (selectionsChangedForReadPeriod) {
      // Update streams and rebuffer for the new selection, recreating all streams if reading ahead.
      boolean recreateStreams = readingPeriodHolder != playingPeriodHolder;
      releasePeriodHoldersFrom(playingPeriodHolder.next);
      playingPeriodHolder.next = null;
      loadingPeriodHolder = playingPeriodHolder;
      readingPeriodHolder = playingPeriodHolder;

      boolean[] streamResetFlags = new boolean[renderers.length];
      long periodPositionUs = playingPeriodHolder.updatePeriodTrackSelection(
          playbackInfo.positionUs, recreateStreams, streamResetFlags);
      if (playbackInfo.playbackState != Player.STATE_ENDED
          && periodPositionUs != playbackInfo.positionUs) {
        playbackInfo = playbackInfo.fromNewPosition(playbackInfo.periodId, periodPositionUs,
            playbackInfo.contentPositionUs);
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
      playbackInfo = playbackInfo.copyWithTrackSelectorResult(periodHolder.trackSelectorResult);
      enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
    } else {
      // Release and re-prepare/buffer periods after the one whose selection changed.
      loadingPeriodHolder = periodHolder;
      periodHolder = loadingPeriodHolder.next;
      while (periodHolder != null) {
        periodHolder.release();
        periodHolder = periodHolder.next;
      }
      loadingPeriodHolder.next = null;
      if (loadingPeriodHolder.prepared) {
        long loadingPeriodPositionUs = Math.max(loadingPeriodHolder.info.startPositionUs,
            loadingPeriodHolder.toPeriodTime(rendererPositionUs));
        loadingPeriodHolder.updatePeriodTrackSelection(loadingPeriodPositionUs, false);
      }
    }
    if (playbackInfo.playbackState != Player.STATE_ENDED) {
      maybeContinueLoading();
      updatePlaybackPositions();
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
  }

  private void updateTrackSelectionPlaybackSpeed(float playbackSpeed) {
    MediaPeriodHolder periodHolder =
        playingPeriodHolder != null ? playingPeriodHolder : loadingPeriodHolder;
    while (periodHolder != null) {
      if (periodHolder.trackSelectorResult != null) {
        TrackSelection[] trackSelections = periodHolder.trackSelectorResult.selections.getAll();
        for (TrackSelection trackSelection : trackSelections) {
          if (trackSelection != null) {
            trackSelection.onPlaybackSpeed(playbackSpeed);
          }
        }
      }
      periodHolder = periodHolder.next;
    }
  }

  private boolean isTimelineReady(long playingPeriodDurationUs) {
    return playingPeriodDurationUs == C.TIME_UNSET
        || playbackInfo.positionUs < playingPeriodDurationUs
        || (playingPeriodHolder.next != null
        && (playingPeriodHolder.next.prepared || playingPeriodHolder.next.info.id.isAd()));
  }

  private void maybeThrowPeriodPrepareError() throws IOException {
    if (loadingPeriodHolder != null && !loadingPeriodHolder.prepared
        && (readingPeriodHolder == null || readingPeriodHolder.next == loadingPeriodHolder)) {
      for (Renderer renderer : enabledRenderers) {
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }
      loadingPeriodHolder.mediaPeriod.maybeThrowPrepareError();
    }
  }

  private void handleSourceInfoRefreshed(MediaSourceRefreshInfo sourceRefreshInfo)
      throws ExoPlaybackException {
    if (sourceRefreshInfo.source != mediaSource) {
      // Stale event.
      return;
    }

    Timeline oldTimeline = playbackInfo.timeline;
    Timeline timeline = sourceRefreshInfo.timeline;
    Object manifest = sourceRefreshInfo.manifest;
    mediaPeriodInfoSequence.setTimeline(timeline);
    playbackInfo = playbackInfo.copyWithTimeline(timeline, manifest);
    resolveCustomMessagePositions();

    if (oldTimeline == null) {
      playbackInfoUpdate.incrementPendingOperationAcks(pendingPrepareCount);
      pendingPrepareCount = 0;
      if (pendingInitialSeekPosition != null) {
        Pair<Integer, Long> periodPosition =
            resolveSeekPosition(pendingInitialSeekPosition, /* trySubsequentPeriods= */ true);
        pendingInitialSeekPosition = null;
        if (periodPosition == null) {
          // The seek position was valid for the timeline that it was performed into, but the
          // timeline has changed and a suitable seek position could not be resolved in the new one.
          handleSourceInfoRefreshEndedPlayback();
        } else {
          int periodIndex = periodPosition.first;
          long positionUs = periodPosition.second;
          MediaPeriodId periodId =
              mediaPeriodInfoSequence.resolvePeriodPositionForAds(periodIndex, positionUs);
          playbackInfo = playbackInfo.fromNewPosition(periodId, periodId.isAd() ? 0 : positionUs,
              positionUs);
        }
      } else if (playbackInfo.startPositionUs == C.TIME_UNSET) {
        if (timeline.isEmpty()) {
          handleSourceInfoRefreshEndedPlayback();
        } else {
          Pair<Integer, Long> defaultPosition = getPeriodPosition(timeline,
              timeline.getFirstWindowIndex(shuffleModeEnabled), C.TIME_UNSET);
          int periodIndex = defaultPosition.first;
          long startPositionUs = defaultPosition.second;
          MediaPeriodId periodId = mediaPeriodInfoSequence.resolvePeriodPositionForAds(periodIndex,
              startPositionUs);
          playbackInfo = playbackInfo.fromNewPosition(periodId,
              periodId.isAd() ? 0 : startPositionUs, startPositionUs);
        }
      }
      return;
    }

    int playingPeriodIndex = playbackInfo.periodId.periodIndex;
    MediaPeriodHolder periodHolder = playingPeriodHolder != null ? playingPeriodHolder
        : loadingPeriodHolder;
    if (periodHolder == null && playingPeriodIndex >= oldTimeline.getPeriodCount()) {
      return;
    }
    Object playingPeriodUid = periodHolder == null
        ? oldTimeline.getPeriod(playingPeriodIndex, period, true).uid : periodHolder.uid;
    int periodIndex = timeline.getIndexOfPeriod(playingPeriodUid);
    if (periodIndex == C.INDEX_UNSET) {
      // We didn't find the current period in the new timeline. Attempt to resolve a subsequent
      // period whose window we can restart from.
      int newPeriodIndex = resolveSubsequentPeriod(playingPeriodIndex, oldTimeline, timeline);
      if (newPeriodIndex == C.INDEX_UNSET) {
        // We failed to resolve a suitable restart position.
        handleSourceInfoRefreshEndedPlayback();
        return;
      }
      // We resolved a subsequent period. Seek to the default position in the corresponding window.
      Pair<Integer, Long> defaultPosition = getPeriodPosition(timeline,
          timeline.getPeriod(newPeriodIndex, period).windowIndex, C.TIME_UNSET);
      newPeriodIndex = defaultPosition.first;
      long newPositionUs = defaultPosition.second;
      timeline.getPeriod(newPeriodIndex, period, true);
      if (periodHolder != null) {
        // Clear the index of each holder that doesn't contain the default position. If a holder
        // contains the default position then update its index so it can be re-used when seeking.
        Object newPeriodUid = period.uid;
        periodHolder.info = periodHolder.info.copyWithPeriodIndex(C.INDEX_UNSET);
        while (periodHolder.next != null) {
          periodHolder = periodHolder.next;
          if (periodHolder.uid.equals(newPeriodUid)) {
            periodHolder.info = mediaPeriodInfoSequence.getUpdatedMediaPeriodInfo(periodHolder.info,
                newPeriodIndex);
          } else {
            periodHolder.info = periodHolder.info.copyWithPeriodIndex(C.INDEX_UNSET);
          }
        }
      }
      // Actually do the seek.
      MediaPeriodId periodId = new MediaPeriodId(newPeriodIndex);
      newPositionUs = seekToPeriodPosition(periodId, newPositionUs);
      playbackInfo = playbackInfo.fromNewPosition(periodId, newPositionUs, C.TIME_UNSET);
      return;
    }

    // The current period is in the new timeline. Update the playback info.
    if (periodIndex != playingPeriodIndex) {
      playbackInfo = playbackInfo.copyWithPeriodIndex(periodIndex);
    }

    if (playbackInfo.periodId.isAd()) {
      // Check that the playing ad hasn't been marked as played. If it has, skip forward.
      MediaPeriodId periodId = mediaPeriodInfoSequence.resolvePeriodPositionForAds(periodIndex,
          playbackInfo.contentPositionUs);
      if (!periodId.isAd() || periodId.adIndexInAdGroup != playbackInfo.periodId.adIndexInAdGroup) {
        long newPositionUs = seekToPeriodPosition(periodId, playbackInfo.contentPositionUs);
        long contentPositionUs = periodId.isAd() ? playbackInfo.contentPositionUs : C.TIME_UNSET;
        playbackInfo = playbackInfo.fromNewPosition(periodId, newPositionUs, contentPositionUs);
        return;
      }
    }

    if (periodHolder == null) {
      // We don't have any period holders, so we're done.
      return;
    }

    // Update the holder indices. If we find a subsequent holder that's inconsistent with the new
    // timeline then take appropriate action.
    periodHolder = updatePeriodInfo(periodHolder, periodIndex);
    while (periodHolder.next != null) {
      MediaPeriodHolder previousPeriodHolder = periodHolder;
      periodHolder = periodHolder.next;
      periodIndex = timeline.getNextPeriodIndex(periodIndex, period, window, repeatMode,
          shuffleModeEnabled);
      if (periodIndex != C.INDEX_UNSET
          && periodHolder.uid.equals(timeline.getPeriod(periodIndex, period, true).uid)) {
        // The holder is consistent with the new timeline. Update its index and continue.
        periodHolder = updatePeriodInfo(periodHolder, periodIndex);
      } else {
        // The holder is inconsistent with the new timeline.
        boolean seenReadingPeriodHolder =
            readingPeriodHolder != null && readingPeriodHolder.index < periodHolder.index;
        if (!seenReadingPeriodHolder) {
          // Renderers may have read from a period that's been removed. Seek back to the current
          // position of the playing period to make sure none of the removed period is played.
          long newPositionUs =
              seekToPeriodPosition(playingPeriodHolder.info.id, playbackInfo.positionUs);
          playbackInfo = playbackInfo.fromNewPosition(playingPeriodHolder.info.id, newPositionUs,
              playbackInfo.contentPositionUs);
        } else {
          // Update the loading period to be the last period that's still valid, and release all
          // subsequent periods.
          loadingPeriodHolder = previousPeriodHolder;
          loadingPeriodHolder.next = null;
          // Release the rest of the timeline.
          releasePeriodHoldersFrom(periodHolder);
        }
        break;
      }
    }
  }

  private MediaPeriodHolder updatePeriodInfo(MediaPeriodHolder periodHolder, int periodIndex) {
    while (true) {
      periodHolder.info =
          mediaPeriodInfoSequence.getUpdatedMediaPeriodInfo(periodHolder.info, periodIndex);
      if (periodHolder.info.isLastInTimelinePeriod || periodHolder.next == null) {
        return periodHolder;
      }
      periodHolder = periodHolder.next;
    }
  }

  private void handleSourceInfoRefreshEndedPlayback() {
    setState(Player.STATE_ENDED);
    // Reset, but retain the source so that it can still be used should a seek occur.
    resetInternal(
        /* releaseMediaSource= */ false, /* resetPosition= */ true, /* resetState= */ false);
  }

  /**
   * Given a period index into an old timeline, finds the first subsequent period that also exists
   * in a new timeline. The index of this period in the new timeline is returned.
   *
   * @param oldPeriodIndex The index of the period in the old timeline.
   * @param oldTimeline The old timeline.
   * @param newTimeline The new timeline.
   * @return The index in the new timeline of the first subsequent period, or {@link C#INDEX_UNSET}
   *     if no such period was found.
   */
  private int resolveSubsequentPeriod(int oldPeriodIndex, Timeline oldTimeline,
      Timeline newTimeline) {
    int newPeriodIndex = C.INDEX_UNSET;
    int maxIterations = oldTimeline.getPeriodCount();
    for (int i = 0; i < maxIterations && newPeriodIndex == C.INDEX_UNSET; i++) {
      oldPeriodIndex = oldTimeline.getNextPeriodIndex(oldPeriodIndex, period, window, repeatMode,
          shuffleModeEnabled);
      if (oldPeriodIndex == C.INDEX_UNSET) {
        // We've reached the end of the old timeline.
        break;
      }
      newPeriodIndex = newTimeline.getIndexOfPeriod(
          oldTimeline.getPeriod(oldPeriodIndex, period, true).uid);
    }
    return newPeriodIndex;
  }

  /**
   * Converts a {@link SeekPosition} into the corresponding (periodIndex, periodPositionUs) for the
   * internal timeline.
   *
   * @param seekPosition The position to resolve.
   * @param trySubsequentPeriods Whether the position can be resolved to a subsequent matching
   *     period if the original period is no longer available.
   * @return The resolved position, or null if resolution was not successful.
   * @throws IllegalSeekPositionException If the window index of the seek position is outside the
   *     bounds of the timeline.
   */
  private Pair<Integer, Long> resolveSeekPosition(
      SeekPosition seekPosition, boolean trySubsequentPeriods) {
    Timeline timeline = playbackInfo.timeline;
    Timeline seekTimeline = seekPosition.timeline;
    if (seekTimeline.isEmpty()) {
      // The application performed a blind seek without a non-empty timeline (most likely based on
      // knowledge of what the future timeline will be). Use the internal timeline.
      seekTimeline = timeline;
    }
    // Map the SeekPosition to a position in the corresponding timeline.
    Pair<Integer, Long> periodPosition;
    try {
      periodPosition = seekTimeline.getPeriodPosition(window, period, seekPosition.windowIndex,
          seekPosition.windowPositionUs);
    } catch (IndexOutOfBoundsException e) {
      // The window index of the seek position was outside the bounds of the timeline.
      throw new IllegalSeekPositionException(timeline, seekPosition.windowIndex,
          seekPosition.windowPositionUs);
    }
    if (timeline == seekTimeline) {
      // Our internal timeline is the seek timeline, so the mapped position is correct.
      return periodPosition;
    }
    // Attempt to find the mapped period in the internal timeline.
    int periodIndex = timeline.getIndexOfPeriod(
        seekTimeline.getPeriod(periodPosition.first, period, true).uid);
    if (periodIndex != C.INDEX_UNSET) {
      // We successfully located the period in the internal timeline.
      return Pair.create(periodIndex, periodPosition.second);
    }
    if (trySubsequentPeriods) {
      // Try and find a subsequent period from the seek timeline in the internal timeline.
      periodIndex = resolveSubsequentPeriod(periodPosition.first, seekTimeline, timeline);
      if (periodIndex != C.INDEX_UNSET) {
        // We found one. Map the SeekPosition onto the corresponding default position.
        return getPeriodPosition(
            timeline, timeline.getPeriod(periodIndex, period).windowIndex, C.TIME_UNSET);
      }
    }
    // We didn't find one. Give up.
    return null;
  }

  /**
   * Calls {@link Timeline#getPeriodPosition(Timeline.Window, Timeline.Period, int, long)} using the
   * current timeline.
   */
  private Pair<Integer, Long> getPeriodPosition(Timeline timeline, int windowIndex,
      long windowPositionUs) {
    return timeline.getPeriodPosition(window, period, windowIndex, windowPositionUs);
  }

  private void updatePeriods() throws ExoPlaybackException, IOException {
    if (mediaSource == null) {
      // The player has no media source yet.
      return;
    }
    if (playbackInfo.timeline == null) {
      // We're waiting to get information about periods.
      mediaSource.maybeThrowSourceInfoRefreshError();
      return;
    }

    // Update the loading period if required.
    maybeUpdateLoadingPeriod();

    if (loadingPeriodHolder == null || loadingPeriodHolder.isFullyBuffered()) {
      setIsLoading(false);
    } else if (loadingPeriodHolder != null && !playbackInfo.isLoading) {
      maybeContinueLoading();
    }

    if (playingPeriodHolder == null) {
      // We're waiting for the first period to be prepared.
      return;
    }

    // Advance the playing period if necessary.
    boolean advancedPlayingPeriod = false;
    while (playWhenReady && playingPeriodHolder != readingPeriodHolder
        && rendererPositionUs >= playingPeriodHolder.next.rendererPositionOffsetUs) {
      // All enabled renderers' streams have been read to the end, and the playback position reached
      // the end of the playing period, so advance playback to the next period.
      if (advancedPlayingPeriod) {
        // If we advance more than one period at a time, notify listeners after each update.
        maybeNotifyPlaybackInfoChanged();
      }
      playingPeriodHolder.release();
      setPlayingPeriodHolder(playingPeriodHolder.next);
      playbackInfo = playbackInfo.fromNewPosition(playingPeriodHolder.info.id,
          playingPeriodHolder.info.startPositionUs, playingPeriodHolder.info.contentPositionUs);
      playbackInfoUpdate.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
      updatePlaybackPositions();
      advancedPlayingPeriod = true;
    }

    if (readingPeriodHolder.info.isFinal) {
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        SampleStream sampleStream = readingPeriodHolder.sampleStreams[i];
        // Defer setting the stream as final until the renderer has actually consumed the whole
        // stream in case of playlist changes that cause the stream to be no longer final.
        if (sampleStream != null && renderer.getStream() == sampleStream
            && renderer.hasReadStreamToEnd()) {
          renderer.setCurrentStreamFinal();
        }
      }
      return;
    }

    // Advance the reading period if necessary.
    if (readingPeriodHolder.next == null || !readingPeriodHolder.next.prepared) {
      // We don't have a successor to advance the reading period to.
      return;
    }

    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      SampleStream sampleStream = readingPeriodHolder.sampleStreams[i];
      if (renderer.getStream() != sampleStream
          || (sampleStream != null && !renderer.hasReadStreamToEnd())) {
        // The current reading period is still being read by at least one renderer.
        return;
      }
    }

    TrackSelectorResult oldTrackSelectorResult = readingPeriodHolder.trackSelectorResult;
    readingPeriodHolder = readingPeriodHolder.next;
    TrackSelectorResult newTrackSelectorResult = readingPeriodHolder.trackSelectorResult;

    boolean initialDiscontinuity =
        readingPeriodHolder.mediaPeriod.readDiscontinuity() != C.TIME_UNSET;
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      boolean rendererWasEnabled = oldTrackSelectorResult.renderersEnabled[i];
      if (!rendererWasEnabled) {
        // The renderer was disabled and will be enabled when we play the next period.
      } else if (initialDiscontinuity) {
        // The new period starts with a discontinuity, so the renderer will play out all data then
        // be disabled and re-enabled when it starts playing the next period.
        renderer.setCurrentStreamFinal();
      } else if (!renderer.isCurrentStreamFinal()) {
        TrackSelection newSelection = newTrackSelectorResult.selections.get(i);
        boolean newRendererEnabled = newTrackSelectorResult.renderersEnabled[i];
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
          renderer.replaceStream(formats, readingPeriodHolder.sampleStreams[i],
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

  private void maybeUpdateLoadingPeriod() throws IOException {
    MediaPeriodInfo info;
    if (loadingPeriodHolder == null) {
      info = mediaPeriodInfoSequence.getFirstMediaPeriodInfo(playbackInfo);
    } else {
      loadingPeriodHolder.reevaluateBuffer(rendererPositionUs);
      if (loadingPeriodHolder.info.isFinal || !loadingPeriodHolder.isFullyBuffered()
          || loadingPeriodHolder.info.durationUs == C.TIME_UNSET) {
        return;
      }
      if (playingPeriodHolder != null) {
        int bufferAheadPeriodCount = loadingPeriodHolder.index - playingPeriodHolder.index;
        if (bufferAheadPeriodCount == MAXIMUM_BUFFER_AHEAD_PERIODS) {
          // We are already buffering the maximum number of periods ahead.
          return;
        }
      }
      info = mediaPeriodInfoSequence.getNextMediaPeriodInfo(loadingPeriodHolder.info,
          loadingPeriodHolder.getRendererOffset(), rendererPositionUs);
    }
    if (info == null) {
      mediaSource.maybeThrowSourceInfoRefreshError();
      return;
    }

    long rendererPositionOffsetUs =
        loadingPeriodHolder == null
            ? (info.startPositionUs + RENDERER_TIMESTAMP_OFFSET_US)
            : (loadingPeriodHolder.getRendererOffset() + loadingPeriodHolder.info.durationUs);
    int holderIndex = loadingPeriodHolder == null ? 0 : loadingPeriodHolder.index + 1;
    Object uid = playbackInfo.timeline.getPeriod(info.id.periodIndex, period, true).uid;
    MediaPeriodHolder newPeriodHolder = new MediaPeriodHolder(renderers, rendererCapabilities,
        rendererPositionOffsetUs, trackSelector, loadControl, mediaSource, uid, holderIndex, info);
    if (loadingPeriodHolder != null) {
      loadingPeriodHolder.next = newPeriodHolder;
    }
    loadingPeriodHolder = newPeriodHolder;
    loadingPeriodHolder.mediaPeriod.prepare(this, info.startPositionUs);
    setIsLoading(true);
  }

  private void handlePeriodPrepared(MediaPeriod period) throws ExoPlaybackException {
    if (loadingPeriodHolder == null || loadingPeriodHolder.mediaPeriod != period) {
      // Stale event.
      return;
    }
    loadingPeriodHolder.handlePrepared(mediaClock.getPlaybackParameters().speed);
    if (playingPeriodHolder == null) {
      // This is the first prepared period, so start playing it.
      readingPeriodHolder = loadingPeriodHolder;
      resetRendererPosition(readingPeriodHolder.info.startPositionUs);
      setPlayingPeriodHolder(readingPeriodHolder);
    }
    maybeContinueLoading();
  }

  private void handleContinueLoadingRequested(MediaPeriod period) {
    if (loadingPeriodHolder == null || loadingPeriodHolder.mediaPeriod != period) {
      // Stale event.
      return;
    }
    loadingPeriodHolder.reevaluateBuffer(rendererPositionUs);
    maybeContinueLoading();
  }

  private void maybeContinueLoading() {
    boolean continueLoading = loadingPeriodHolder.shouldContinueLoading(
        rendererPositionUs, mediaClock.getPlaybackParameters().speed);
    setIsLoading(continueLoading);
    if (continueLoading) {
      loadingPeriodHolder.continueLoading(rendererPositionUs);
    }
  }

  private void releasePeriodHoldersFrom(MediaPeriodHolder periodHolder) {
    while (periodHolder != null) {
      periodHolder.release();
      periodHolder = periodHolder.next;
    }
  }

  private void setPlayingPeriodHolder(MediaPeriodHolder periodHolder) throws ExoPlaybackException {
    if (playingPeriodHolder == periodHolder) {
      return;
    }

    int enabledRendererCount = 0;
    boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
      if (periodHolder.trackSelectorResult.renderersEnabled[i]) {
        enabledRendererCount++;
      }
      if (rendererWasEnabledFlags[i] && (!periodHolder.trackSelectorResult.renderersEnabled[i]
          || (renderer.isCurrentStreamFinal()
          && renderer.getStream() == playingPeriodHolder.sampleStreams[i]))) {
        // The renderer should be disabled before playing the next period, either because it's not
        // needed to play the next period, or because we need to re-enable it as its current stream
        // is final and it's not reading ahead.
        disableRenderer(renderer);
      }
    }

    playingPeriodHolder = periodHolder;
    playbackInfo = playbackInfo.copyWithTrackSelectorResult(periodHolder.trackSelectorResult);
    enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
  }

  private void enableRenderers(boolean[] rendererWasEnabledFlags, int totalEnabledRendererCount)
      throws ExoPlaybackException {
    enabledRenderers = new Renderer[totalEnabledRendererCount];
    int enabledRendererCount = 0;
    for (int i = 0; i < renderers.length; i++) {
      if (playingPeriodHolder.trackSelectorResult.renderersEnabled[i]) {
        enableRenderer(i, rendererWasEnabledFlags[i], enabledRendererCount++);
      }
    }
  }

  private void enableRenderer(int rendererIndex, boolean wasRendererEnabled,
      int enabledRendererIndex) throws ExoPlaybackException {
    Renderer renderer = renderers[rendererIndex];
    enabledRenderers[enabledRendererIndex] = renderer;
    if (renderer.getState() == Renderer.STATE_DISABLED) {
      RendererConfiguration rendererConfiguration =
          playingPeriodHolder.trackSelectorResult.rendererConfigurations[rendererIndex];
      TrackSelection newSelection = playingPeriodHolder.trackSelectorResult.selections.get(
          rendererIndex);
      Format[] formats = getFormats(newSelection);
      // The renderer needs enabling with its new track selection.
      boolean playing = playWhenReady && playbackInfo.playbackState == Player.STATE_READY;
      // Consider as joining only if the renderer was previously disabled.
      boolean joining = !wasRendererEnabled && playing;
      // Enable the renderer.
      renderer.enable(rendererConfiguration, formats,
          playingPeriodHolder.sampleStreams[rendererIndex], rendererPositionUs,
          joining, playingPeriodHolder.getRendererOffset());
      mediaClock.onRendererEnabled(renderer);
      // Start the renderer if playing.
      if (playing) {
        renderer.start();
      }
    }
  }

  private boolean rendererWaitingForNextStream(Renderer renderer) {
    return readingPeriodHolder.next != null && readingPeriodHolder.next.prepared
        && renderer.hasReadStreamToEnd();
  }

  @NonNull
  private static Format[] getFormats(TrackSelection newSelection) {
    // Build an array of formats contained by the selection.
    int length = newSelection != null ? newSelection.length() : 0;
    Format[] formats = new Format[length];
    for (int i = 0; i < length; i++) {
      formats[i] = newSelection.getFormat(i);
    }
    return formats;
  }

  /**
   * Holds a {@link MediaPeriod} with information required to play it as part of a timeline.
   */
  private static final class MediaPeriodHolder {

    public final MediaPeriod mediaPeriod;
    public final Object uid;
    public final int index;
    public final SampleStream[] sampleStreams;
    public final boolean[] mayRetainStreamFlags;

    public long rendererPositionOffsetUs;
    public MediaPeriodInfo info;
    public boolean prepared;
    public boolean hasEnabledTracks;
    public MediaPeriodHolder next;
    public TrackSelectorResult trackSelectorResult;

    private final Renderer[] renderers;
    private final RendererCapabilities[] rendererCapabilities;
    private final TrackSelector trackSelector;
    private final LoadControl loadControl;
    private final MediaSource mediaSource;

    private TrackSelectorResult periodTrackSelectorResult;

    public MediaPeriodHolder(Renderer[] renderers, RendererCapabilities[] rendererCapabilities,
        long rendererPositionOffsetUs, TrackSelector trackSelector, LoadControl loadControl,
        MediaSource mediaSource, Object periodUid, int index, MediaPeriodInfo info) {
      this.renderers = renderers;
      this.rendererCapabilities = rendererCapabilities;
      this.rendererPositionOffsetUs = rendererPositionOffsetUs - info.startPositionUs;
      this.trackSelector = trackSelector;
      this.loadControl = loadControl;
      this.mediaSource = mediaSource;
      this.uid = Assertions.checkNotNull(periodUid);
      this.index = index;
      this.info = info;
      sampleStreams = new SampleStream[renderers.length];
      mayRetainStreamFlags = new boolean[renderers.length];
      MediaPeriod mediaPeriod = mediaSource.createPeriod(info.id, loadControl.getAllocator());
      if (info.endPositionUs != C.TIME_END_OF_SOURCE) {
        ClippingMediaPeriod clippingMediaPeriod = new ClippingMediaPeriod(mediaPeriod, true);
        clippingMediaPeriod.setClipping(0, info.endPositionUs);
        mediaPeriod = clippingMediaPeriod;
      }
      this.mediaPeriod = mediaPeriod;
    }

    public long toRendererTime(long periodTimeUs) {
      return periodTimeUs + getRendererOffset();
    }

    public long toPeriodTime(long rendererTimeUs) {
      return rendererTimeUs - getRendererOffset();
    }

    public long getRendererOffset() {
      return rendererPositionOffsetUs;
    }

    public boolean isFullyBuffered() {
      return prepared
          && (!hasEnabledTracks || mediaPeriod.getBufferedPositionUs() == C.TIME_END_OF_SOURCE);
    }

    public boolean haveSufficientBuffer(long rendererPositionUs, float playbackSpeed,
        boolean rebuffering) {
      long bufferedPositionUs = !prepared ? info.startPositionUs
          : mediaPeriod.getBufferedPositionUs();
      if (bufferedPositionUs == C.TIME_END_OF_SOURCE) {
        if (info.isFinal) {
          return true;
        }
        bufferedPositionUs = info.durationUs;
      }
      return loadControl.shouldStartPlayback(bufferedPositionUs - toPeriodTime(rendererPositionUs),
          playbackSpeed, rebuffering);
    }

    public void handlePrepared(float playbackSpeed) throws ExoPlaybackException {
      prepared = true;
      selectTracks(playbackSpeed);
      long newStartPositionUs = updatePeriodTrackSelection(info.startPositionUs, false);
      rendererPositionOffsetUs += info.startPositionUs - newStartPositionUs;
      info = info.copyWithStartPositionUs(newStartPositionUs);
    }

    public void reevaluateBuffer(long rendererPositionUs) {
      if (prepared) {
        mediaPeriod.reevaluateBuffer(toPeriodTime(rendererPositionUs));
      }
    }

    public boolean shouldContinueLoading(long rendererPositionUs, float playbackSpeed) {
      long nextLoadPositionUs = !prepared ? 0 : mediaPeriod.getNextLoadPositionUs();
      if (nextLoadPositionUs == C.TIME_END_OF_SOURCE) {
        return false;
      } else {
        long bufferedDurationUs = nextLoadPositionUs - toPeriodTime(rendererPositionUs);
        return loadControl.shouldContinueLoading(bufferedDurationUs, playbackSpeed);
      }
    }

    public void continueLoading(long rendererPositionUs) {
      long loadingPeriodPositionUs = toPeriodTime(rendererPositionUs);
      mediaPeriod.continueLoading(loadingPeriodPositionUs);
    }

    public boolean selectTracks(float playbackSpeed) throws ExoPlaybackException {
      TrackSelectorResult selectorResult = trackSelector.selectTracks(rendererCapabilities,
          mediaPeriod.getTrackGroups());
      if (selectorResult.isEquivalent(periodTrackSelectorResult)) {
        return false;
      }
      trackSelectorResult = selectorResult;
      for (TrackSelection trackSelection : trackSelectorResult.selections.getAll()) {
        if (trackSelection != null) {
          trackSelection.onPlaybackSpeed(playbackSpeed);
        }
      }
      return true;
    }

    public long updatePeriodTrackSelection(long positionUs, boolean forceRecreateStreams) {
      return updatePeriodTrackSelection(positionUs, forceRecreateStreams,
          new boolean[renderers.length]);
    }

    public long updatePeriodTrackSelection(long positionUs, boolean forceRecreateStreams,
        boolean[] streamResetFlags) {
      TrackSelectionArray trackSelections = trackSelectorResult.selections;
      for (int i = 0; i < trackSelections.length; i++) {
        mayRetainStreamFlags[i] = !forceRecreateStreams
            && trackSelectorResult.isEquivalent(periodTrackSelectorResult, i);
      }

      // Undo the effect of previous call to associate no-sample renderers with empty tracks
      // so the mediaPeriod receives back whatever it sent us before.
      disassociateNoSampleRenderersWithEmptySampleStream(sampleStreams);
      updatePeriodTrackSelectorResult(trackSelectorResult);
      // Disable streams on the period and get new streams for updated/newly-enabled tracks.
      positionUs = mediaPeriod.selectTracks(trackSelections.getAll(), mayRetainStreamFlags,
          sampleStreams, streamResetFlags, positionUs);
      associateNoSampleRenderersWithEmptySampleStream(sampleStreams);

      // Update whether we have enabled tracks and sanity check the expected streams are non-null.
      hasEnabledTracks = false;
      for (int i = 0; i < sampleStreams.length; i++) {
        if (sampleStreams[i] != null) {
          Assertions.checkState(trackSelectorResult.renderersEnabled[i]);
          // hasEnabledTracks should be true only when non-empty streams exists.
          if (rendererCapabilities[i].getTrackType() != C.TRACK_TYPE_NONE) {
            hasEnabledTracks = true;
          }
        } else {
          Assertions.checkState(trackSelections.get(i) == null);
        }
      }
      // The track selection has changed.
      loadControl.onTracksSelected(renderers, trackSelectorResult.groups, trackSelections);
      return positionUs;
    }

    public void release() {
      updatePeriodTrackSelectorResult(null);
      try {
        if (info.endPositionUs != C.TIME_END_OF_SOURCE) {
          mediaSource.releasePeriod(((ClippingMediaPeriod) mediaPeriod).mediaPeriod);
        } else {
          mediaSource.releasePeriod(mediaPeriod);
        }
      } catch (RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Period release failed.", e);
      }
    }

    private void updatePeriodTrackSelectorResult(TrackSelectorResult trackSelectorResult) {
      if (periodTrackSelectorResult != null) {
        disableTrackSelectionsInResult(periodTrackSelectorResult);
      }
      periodTrackSelectorResult = trackSelectorResult;
      if (periodTrackSelectorResult != null) {
        enableTrackSelectionsInResult(periodTrackSelectorResult);
      }
    }

    private void enableTrackSelectionsInResult(TrackSelectorResult trackSelectorResult) {
      for (int i = 0; i < trackSelectorResult.renderersEnabled.length; i++) {
        boolean rendererEnabled = trackSelectorResult.renderersEnabled[i];
        TrackSelection trackSelection = trackSelectorResult.selections.get(i);
        if (rendererEnabled && trackSelection != null) {
          trackSelection.enable();
        }
      }
    }

    private void disableTrackSelectionsInResult(TrackSelectorResult trackSelectorResult) {
      for (int i = 0; i < trackSelectorResult.renderersEnabled.length; i++) {
        boolean rendererEnabled = trackSelectorResult.renderersEnabled[i];
        TrackSelection trackSelection = trackSelectorResult.selections.get(i);
        if (rendererEnabled && trackSelection != null) {
          trackSelection.disable();
        }
      }
    }

    /**
     * For each renderer of type {@link C#TRACK_TYPE_NONE}, we will remove the dummy
     * {@link EmptySampleStream} that was associated with it.
     */
    private void disassociateNoSampleRenderersWithEmptySampleStream(SampleStream[] sampleStreams) {
      for (int i = 0; i < rendererCapabilities.length; i++) {
        if (rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE) {
          sampleStreams[i] = null;
        }
      }
    }

    /**
     * For each renderer of type {@link C#TRACK_TYPE_NONE} that was enabled, we will
     * associate it with a dummy {@link EmptySampleStream}.
     */
    private void associateNoSampleRenderersWithEmptySampleStream(SampleStream[] sampleStreams) {
      for (int i = 0; i < rendererCapabilities.length; i++) {
        if (rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE
            && trackSelectorResult.renderersEnabled[i]) {
          sampleStreams[i] = new EmptySampleStream();
        }
      }
    }

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

  private static final class CustomMessageInfo implements Comparable<CustomMessageInfo> {

    public final PlayerMessage message;
    public final PlayerMessage.Sender.Listener listener;

    public int resolvedPeriodIndex;
    public long resolvedPeriodTimeUs;
    public @Nullable Object resolvedPeriodUid;

    public CustomMessageInfo(PlayerMessage message, PlayerMessage.Sender.Listener listener) {
      this.message = message;
      this.listener = listener;
    }

    public void setResolvedPosition(int periodIndex, long periodTimeUs, Object periodUid) {
      resolvedPeriodIndex = periodIndex;
      resolvedPeriodTimeUs = periodTimeUs;
      resolvedPeriodUid = periodUid;
    }

    @Override
    public int compareTo(@NonNull CustomMessageInfo other) {
      if ((resolvedPeriodUid == null) != (other.resolvedPeriodUid == null)) {
        // CustomMessageInfos with a resolved period position are always smaller.
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

  private static final class MediaSourceRefreshInfo {

    public final MediaSource source;
    public final Timeline timeline;
    public final Object manifest;

    public MediaSourceRefreshInfo(MediaSource source, Timeline timeline, Object manifest) {
      this.source = source;
      this.timeline = timeline;
      this.manifest = manifest;
    }
  }

  private static final class PlaybackInfoUpdate {

    private PlaybackInfo lastPlaybackInfo;
    private int operationAcks;
    private boolean positionDiscontinuity;
    private @DiscontinuityReason int discontinuityReason;

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
