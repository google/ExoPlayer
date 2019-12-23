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

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

/**
 * An {@link ExoPlayer} implementation. Instances can be obtained from {@link ExoPlayer.Builder}.
 */
/* package */ final class ExoPlayerImpl extends BasePlayer implements ExoPlayer {

  private static final String TAG = "ExoPlayerImpl";

  /**
   * This empty track selector result can only be used for {@link PlaybackInfo#trackSelectorResult}
   * when the player does not have any track selection made (such as when player is reset, or when
   * player seeks to an unprepared period). It will not be used as result of any {@link
   * TrackSelector#selectTracks(RendererCapabilities[], TrackGroupArray, MediaPeriodId, Timeline)}
   * operation.
   */
  /* package */ final TrackSelectorResult emptyTrackSelectorResult;

  private final Renderer[] renderers;
  private final TrackSelector trackSelector;
  private final Handler eventHandler;
  private final ExoPlayerImplInternal internalPlayer;
  private final Handler internalPlayerHandler;
  private final CopyOnWriteArrayList<ListenerHolder> listeners;
  private final Timeline.Period period;
  private final ArrayDeque<Runnable> pendingListenerNotifications;
  private final List<Playlist.MediaSourceHolder> mediaSourceHolders;
  private final boolean useLazyPreparation;

  private boolean playWhenReady;
  @PlaybackSuppressionReason private int playbackSuppressionReason;
  @RepeatMode private int repeatMode;
  private boolean shuffleModeEnabled;
  private int pendingOperationAcks;
  private boolean hasPendingSeek;
  private boolean foregroundMode;
  private int pendingSetPlaybackParametersAcks;
  private PlaybackParameters playbackParameters;
  private SeekParameters seekParameters;
  private ShuffleOrder shuffleOrder;

  // Playback information when there is no pending seek/set source operation.
  private PlaybackInfo playbackInfo;

  // Playback information when there is a pending seek/set source operation.
  private int maskingWindowIndex;
  private int maskingPeriodIndex;
  private long maskingWindowPositionMs;

  /**
   * Constructs an instance. Must be called from a thread that has an associated {@link Looper}.
   *
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param bandwidthMeter The {@link BandwidthMeter} that will be used by the instance.
   * @param analyticsCollector The {@link AnalyticsCollector} that will be used by the instance.
   * @param useLazyPreparation Whether playlist items are prepared lazily. If false, all manifest
   *     loads and other initial preparation steps happen immediately. If true, these initial
   *     preparations are triggered only when the player starts buffering the media.
   * @param clock The {@link Clock} that will be used by the instance.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  @SuppressLint("HandlerLeak")
  public ExoPlayerImpl(
      Renderer[] renderers,
      TrackSelector trackSelector,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      @Nullable AnalyticsCollector analyticsCollector,
      boolean useLazyPreparation,
      Clock clock,
      Looper looper) {
    Log.i(TAG, "Init " + Integer.toHexString(System.identityHashCode(this)) + " ["
        + ExoPlayerLibraryInfo.VERSION_SLASHY + "] [" + Util.DEVICE_DEBUG_INFO + "]");
    Assertions.checkState(renderers.length > 0);
    this.renderers = Assertions.checkNotNull(renderers);
    this.trackSelector = Assertions.checkNotNull(trackSelector);
    this.useLazyPreparation = useLazyPreparation;
    playWhenReady = false;
    repeatMode = Player.REPEAT_MODE_OFF;
    shuffleModeEnabled = false;
    listeners = new CopyOnWriteArrayList<>();
    mediaSourceHolders = new ArrayList<>();
    shuffleOrder = new ShuffleOrder.DefaultShuffleOrder(/* length= */ 0);
    emptyTrackSelectorResult =
        new TrackSelectorResult(
            new RendererConfiguration[renderers.length],
            new TrackSelection[renderers.length],
            null);
    period = new Timeline.Period();
    playbackParameters = PlaybackParameters.DEFAULT;
    seekParameters = SeekParameters.DEFAULT;
    playbackSuppressionReason = PLAYBACK_SUPPRESSION_REASON_NONE;
    maskingWindowIndex = C.INDEX_UNSET;
    eventHandler =
        new Handler(looper) {
          @Override
          public void handleMessage(Message msg) {
            ExoPlayerImpl.this.handleEvent(msg);
          }
        };
    playbackInfo = PlaybackInfo.createDummy(/* startPositionUs= */ 0, emptyTrackSelectorResult);
    pendingListenerNotifications = new ArrayDeque<>();
    if (analyticsCollector != null) {
      analyticsCollector.setPlayer(this);
    }
    internalPlayer =
        new ExoPlayerImplInternal(
            renderers,
            trackSelector,
            emptyTrackSelectorResult,
            loadControl,
            bandwidthMeter,
            playWhenReady,
            repeatMode,
            shuffleModeEnabled,
            analyticsCollector,
            eventHandler,
            clock);
    internalPlayerHandler = new Handler(internalPlayer.getPlaybackLooper());
  }

  /**
   * Set a limit on the time a call to {@link #release()} can spend. If a call to {@link #release()}
   * takes more than {@code timeoutMs} milliseconds to complete, the player will raise an error via
   * {@link Player.EventListener#onPlayerError}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release. It should
   * only be called before the player is used.
   *
   * @param timeoutMs The time limit in milliseconds, or 0 for no limit.
   */
  public void experimental_setReleaseTimeoutMs(long timeoutMs) {
    internalPlayer.experimental_setReleaseTimeoutMs(timeoutMs);
  }

  @Override
  @Nullable
  public AudioComponent getAudioComponent() {
    return null;
  }

  @Override
  @Nullable
  public VideoComponent getVideoComponent() {
    return null;
  }

  @Override
  @Nullable
  public TextComponent getTextComponent() {
    return null;
  }

  @Override
  @Nullable
  public MetadataComponent getMetadataComponent() {
    return null;
  }

  @Override
  public Looper getPlaybackLooper() {
    return internalPlayer.getPlaybackLooper();
  }

  @Override
  public Looper getApplicationLooper() {
    return eventHandler.getLooper();
  }

  @Override
  public void addListener(Player.EventListener listener) {
    listeners.addIfAbsent(new ListenerHolder(listener));
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    for (ListenerHolder listenerHolder : listeners) {
      if (listenerHolder.listener.equals(listener)) {
        listenerHolder.release();
        listeners.remove(listenerHolder);
      }
    }
  }

  @Override
  @State
  public int getPlaybackState() {
    return playbackInfo.playbackState;
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    return playbackSuppressionReason;
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlaybackError() {
    return playbackInfo.playbackError;
  }

  /** @deprecated Use {@link #prepare()} instead. */
  @Deprecated
  @Override
  public void retry() {
    prepare();
  }

  @Override
  public void prepare() {
    if (playbackInfo.playbackState != Player.STATE_IDLE) {
      return;
    }
    PlaybackInfo playbackInfo =
        getResetPlaybackInfo(
            /* clearPlaylist= */ false,
            /* resetError= */ true,
            /* playbackState= */ this.playbackInfo.timeline.isEmpty()
                ? Player.STATE_ENDED
                : Player.STATE_BUFFERING);
    // Trigger internal prepare first before updating the playback info and notifying external
    // listeners to ensure that new operations issued in the listener notifications reach the
    // player after this prepare. The internal player can't change the playback info immediately
    // because it uses a callback.
    pendingOperationAcks++;
    internalPlayer.prepare();
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        /* seekProcessed= */ false);
  }

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource)} and {@link ExoPlayer#prepare()} instead.
   */
  @Deprecated
  @Override
  public void prepare(MediaSource mediaSource) {
    setMediaSource(mediaSource);
    prepare();
  }

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource, boolean)} and {@link ExoPlayer#prepare()}
   *     instead.
   */
  @Deprecated
  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    setMediaSource(mediaSource, resetPosition);
    prepare();
  }

  @Override
  public void setMediaSource(MediaSource mediaSource) {
    setMediaSources(Collections.singletonList(mediaSource));
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, long startPositionMs) {
    setMediaSources(
        Collections.singletonList(mediaSource), /* startWindowIndex= */ 0, startPositionMs);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
    setMediaSources(Collections.singletonList(mediaSource), resetPosition);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources) {
    setMediaSources(mediaSources, /* resetPosition= */ true);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {
    setMediaItemsInternal(
        mediaSources,
        /* startWindowIndex= */ C.INDEX_UNSET,
        /* startPositionMs= */ C.TIME_UNSET,
        /* resetToDefaultPosition= */ resetPosition);
  }

  @Override
  public void setMediaSources(
      List<MediaSource> mediaSources, int startWindowIndex, long startPositionMs) {
    setMediaItemsInternal(
        mediaSources, startWindowIndex, startPositionMs, /* resetToDefaultPosition= */ false);
  }

  @Override
  public void addMediaSource(MediaSource mediaSource) {
    addMediaSources(Collections.singletonList(mediaSource));
  }

  @Override
  public void addMediaSource(int index, MediaSource mediaSource) {
    addMediaSources(index, Collections.singletonList(mediaSource));
  }

  @Override
  public void addMediaSources(List<MediaSource> mediaSources) {
    addMediaSources(/* index= */ mediaSourceHolders.size(), mediaSources);
  }

  @Override
  public void addMediaSources(int index, List<MediaSource> mediaSources) {
    Assertions.checkArgument(index >= 0);
    int currentWindowIndex = getCurrentWindowIndex();
    long currentPositionMs = getCurrentPosition();
    Timeline oldTimeline = getCurrentTimeline();
    pendingOperationAcks++;
    List<Playlist.MediaSourceHolder> holders = addMediaSourceHolders(index, mediaSources);
    Timeline timeline =
        maskTimelineAndWindowIndex(currentWindowIndex, currentPositionMs, oldTimeline);
    internalPlayer.addMediaSources(index, holders, shuffleOrder);
    notifyListeners(
        listener -> listener.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
  }

  @Override
  public void removeMediaItem(int index) {
    removeMediaItemsInternal(/* fromIndex= */ index, /* toIndex= */ index + 1);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    Assertions.checkArgument(toIndex > fromIndex);
    removeMediaItemsInternal(fromIndex, toIndex);
  }

  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    Assertions.checkArgument(currentIndex != newIndex);
    moveMediaItems(/* fromIndex= */ currentIndex, /* toIndex= */ currentIndex + 1, newIndex);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newFromIndex) {
    Assertions.checkArgument(
        fromIndex >= 0
            && fromIndex <= toIndex
            && toIndex <= mediaSourceHolders.size()
            && newFromIndex >= 0);
    int currentWindowIndex = getCurrentWindowIndex();
    long currentPositionMs = getCurrentPosition();
    Timeline oldTimeline = getCurrentTimeline();
    pendingOperationAcks++;
    newFromIndex = Math.min(newFromIndex, mediaSourceHolders.size() - (toIndex - fromIndex));
    Playlist.moveMediaSourceHolders(mediaSourceHolders, fromIndex, toIndex, newFromIndex);
    Timeline timeline =
        maskTimelineAndWindowIndex(currentWindowIndex, currentPositionMs, oldTimeline);
    internalPlayer.moveMediaSources(fromIndex, toIndex, newFromIndex, shuffleOrder);
    notifyListeners(
        listener -> listener.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
  }

  @Override
  public void clearMediaItems() {
    if (mediaSourceHolders.isEmpty()) {
      return;
    }
    removeMediaItemsInternal(/* fromIndex= */ 0, /* toIndex= */ mediaSourceHolders.size());
  }

  @Override
  public void setShuffleOrder(ShuffleOrder shuffleOrder) {
    pendingOperationAcks++;
    this.shuffleOrder = shuffleOrder;
    Timeline timeline = maskTimeline();
    internalPlayer.setShuffleOrder(shuffleOrder);
    notifyListeners(
        listener -> listener.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    setPlayWhenReady(playWhenReady, PLAYBACK_SUPPRESSION_REASON_NONE);
  }

  public void setPlayWhenReady(
      boolean playWhenReady, @PlaybackSuppressionReason int playbackSuppressionReason) {
    boolean oldIsPlaying = isPlaying();
    boolean oldInternalPlayWhenReady =
        this.playWhenReady && this.playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
    boolean internalPlayWhenReady =
        playWhenReady && playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
    if (oldInternalPlayWhenReady != internalPlayWhenReady) {
      internalPlayer.setPlayWhenReady(internalPlayWhenReady);
    }
    boolean playWhenReadyChanged = this.playWhenReady != playWhenReady;
    boolean suppressionReasonChanged = this.playbackSuppressionReason != playbackSuppressionReason;
    this.playWhenReady = playWhenReady;
    this.playbackSuppressionReason = playbackSuppressionReason;
    boolean isPlaying = isPlaying();
    boolean isPlayingChanged = oldIsPlaying != isPlaying;
    if (playWhenReadyChanged || suppressionReasonChanged || isPlayingChanged) {
      int playbackState = playbackInfo.playbackState;
      notifyListeners(
          listener -> {
            if (playWhenReadyChanged) {
              listener.onPlayerStateChanged(playWhenReady, playbackState);
            }
            if (suppressionReasonChanged) {
              listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason);
            }
            if (isPlayingChanged) {
              listener.onIsPlayingChanged(isPlaying);
            }
          });
    }
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady;
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    if (this.repeatMode != repeatMode) {
      this.repeatMode = repeatMode;
      internalPlayer.setRepeatMode(repeatMode);
      notifyListeners(listener -> listener.onRepeatModeChanged(repeatMode));
    }
  }

  @Override
  public @RepeatMode int getRepeatMode() {
    return repeatMode;
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    if (this.shuffleModeEnabled != shuffleModeEnabled) {
      this.shuffleModeEnabled = shuffleModeEnabled;
      internalPlayer.setShuffleModeEnabled(shuffleModeEnabled);
      notifyListeners(listener -> listener.onShuffleModeEnabledChanged(shuffleModeEnabled));
    }
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return shuffleModeEnabled;
  }

  @Override
  public boolean isLoading() {
    return playbackInfo.isLoading;
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    Timeline timeline = playbackInfo.timeline;
    if (windowIndex < 0 || (!timeline.isEmpty() && windowIndex >= timeline.getWindowCount())) {
      throw new IllegalSeekPositionException(timeline, windowIndex, positionMs);
    }
    hasPendingSeek = true;
    pendingOperationAcks++;
    if (isPlayingAd()) {
      // TODO: Investigate adding support for seeking during ads. This is complicated to do in
      // general because the midroll ad preceding the seek destination must be played before the
      // content position can be played, if a different ad is playing at the moment.
      Log.w(TAG, "seekTo ignored because an ad is playing");
      eventHandler
          .obtainMessage(
              ExoPlayerImplInternal.MSG_PLAYBACK_INFO_CHANGED,
              /* operationAcks */ 1,
              /* positionDiscontinuityReason */ C.INDEX_UNSET,
              playbackInfo)
          .sendToTarget();
      return;
    }
    maskWindowIndexAndPositionForSeek(timeline, windowIndex, positionMs);
    internalPlayer.seekTo(timeline, windowIndex, C.msToUs(positionMs));
    notifyListeners(listener -> listener.onPositionDiscontinuity(DISCONTINUITY_REASON_SEEK));
  }

  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    if (playbackParameters == null) {
      playbackParameters = PlaybackParameters.DEFAULT;
    }
    if (this.playbackParameters.equals(playbackParameters)) {
      return;
    }
    pendingSetPlaybackParametersAcks++;
    this.playbackParameters = playbackParameters;
    internalPlayer.setPlaybackParameters(playbackParameters);
    PlaybackParameters playbackParametersToNotify = playbackParameters;
    notifyListeners(listener -> listener.onPlaybackParametersChanged(playbackParametersToNotify));
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters;
  }

  @Override
  public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    if (seekParameters == null) {
      seekParameters = SeekParameters.DEFAULT;
    }
    if (!this.seekParameters.equals(seekParameters)) {
      this.seekParameters = seekParameters;
      internalPlayer.setSeekParameters(seekParameters);
    }
  }

  @Override
  public SeekParameters getSeekParameters() {
    return seekParameters;
  }

  @Override
  public void setForegroundMode(boolean foregroundMode) {
    if (this.foregroundMode != foregroundMode) {
      this.foregroundMode = foregroundMode;
      internalPlayer.setForegroundMode(foregroundMode);
    }
  }

  @Override
  public void stop(boolean reset) {
    PlaybackInfo playbackInfo =
        getResetPlaybackInfo(
            /* clearPlaylist= */ reset,
            /* resetError= */ reset,
            /* playbackState= */ Player.STATE_IDLE);
    // Trigger internal stop first before updating the playback info and notifying external
    // listeners to ensure that new operations issued in the listener notifications reach the
    // player after this stop. The internal player can't change the playback info immediately
    // because it uses a callback.
    pendingOperationAcks++;
    internalPlayer.stop(reset);
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* seekProcessed= */ false);
  }

  @Override
  public void release() {
    Log.i(TAG, "Release " + Integer.toHexString(System.identityHashCode(this)) + " ["
        + ExoPlayerLibraryInfo.VERSION_SLASHY + "] [" + Util.DEVICE_DEBUG_INFO + "] ["
        + ExoPlayerLibraryInfo.registeredModules() + "]");
    if (!internalPlayer.release()) {
      notifyListeners(
          listener ->
              listener.onPlayerError(
                  ExoPlaybackException.createForUnexpected(
                      new RuntimeException(new TimeoutException("Player release timed out.")))));
    }
    eventHandler.removeCallbacksAndMessages(null);
    playbackInfo =
        getResetPlaybackInfo(
            /* clearPlaylist= */ false,
            /* resetError= */ false,
            /* playbackState= */ Player.STATE_IDLE);
  }

  @Override
  public PlayerMessage createMessage(Target target) {
    return new PlayerMessage(
        internalPlayer,
        target,
        playbackInfo.timeline,
        getCurrentWindowIndex(),
        internalPlayerHandler);
  }

  @Override
  public int getCurrentPeriodIndex() {
    if (shouldMaskPosition()) {
      return maskingPeriodIndex;
    } else {
      return playbackInfo.timeline.getIndexOfPeriod(playbackInfo.periodId.periodUid);
    }
  }

  @Override
  public int getCurrentWindowIndex() {
    int currentWindowIndex = getCurrentWindowIndexInternal();
    return currentWindowIndex == C.INDEX_UNSET ? 0 : currentWindowIndex;
  }

  @Override
  public long getDuration() {
    if (isPlayingAd()) {
      MediaPeriodId periodId = playbackInfo.periodId;
      playbackInfo.timeline.getPeriodByUid(periodId.periodUid, period);
      long adDurationUs = period.getAdDurationUs(periodId.adGroupIndex, periodId.adIndexInAdGroup);
      return C.usToMs(adDurationUs);
    }
    return getContentDuration();
  }

  @Override
  public long getCurrentPosition() {
    if (shouldMaskPosition()) {
      return maskingWindowPositionMs;
    } else if (playbackInfo.periodId.isAd()) {
      return C.usToMs(playbackInfo.positionUs);
    } else {
      return periodPositionUsToWindowPositionMs(playbackInfo.periodId, playbackInfo.positionUs);
    }
  }

  @Override
  public long getBufferedPosition() {
    if (isPlayingAd()) {
      return playbackInfo.loadingMediaPeriodId.equals(playbackInfo.periodId)
          ? C.usToMs(playbackInfo.bufferedPositionUs)
          : getDuration();
    }
    return getContentBufferedPosition();
  }

  @Override
  public long getTotalBufferedDuration() {
    return C.usToMs(playbackInfo.totalBufferedDurationUs);
  }

  @Override
  public boolean isPlayingAd() {
    return !shouldMaskPosition() && playbackInfo.periodId.isAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return isPlayingAd() ? playbackInfo.periodId.adGroupIndex : C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return isPlayingAd() ? playbackInfo.periodId.adIndexInAdGroup : C.INDEX_UNSET;
  }

  @Override
  public long getContentPosition() {
    if (isPlayingAd()) {
      playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period);
      return playbackInfo.contentPositionUs == C.TIME_UNSET
          ? playbackInfo.timeline.getWindow(getCurrentWindowIndex(), window).getDefaultPositionMs()
          : period.getPositionInWindowMs() + C.usToMs(playbackInfo.contentPositionUs);
    } else {
      return getCurrentPosition();
    }
  }

  @Override
  public long getContentBufferedPosition() {
    if (shouldMaskPosition()) {
      return maskingWindowPositionMs;
    }
    if (playbackInfo.loadingMediaPeriodId.windowSequenceNumber
        != playbackInfo.periodId.windowSequenceNumber) {
      return playbackInfo.timeline.getWindow(getCurrentWindowIndex(), window).getDurationMs();
    }
    long contentBufferedPositionUs = playbackInfo.bufferedPositionUs;
    if (playbackInfo.loadingMediaPeriodId.isAd()) {
      Timeline.Period loadingPeriod =
          playbackInfo.timeline.getPeriodByUid(playbackInfo.loadingMediaPeriodId.periodUid, period);
      contentBufferedPositionUs =
          loadingPeriod.getAdGroupTimeUs(playbackInfo.loadingMediaPeriodId.adGroupIndex);
      if (contentBufferedPositionUs == C.TIME_END_OF_SOURCE) {
        contentBufferedPositionUs = loadingPeriod.durationUs;
      }
    }
    return periodPositionUsToWindowPositionMs(
        playbackInfo.loadingMediaPeriodId, contentBufferedPositionUs);
  }

  @Override
  public int getRendererCount() {
    return renderers.length;
  }

  @Override
  public int getRendererType(int index) {
    return renderers[index].getTrackType();
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return playbackInfo.trackGroups;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return playbackInfo.trackSelectorResult.selections;
  }

  @Override
  public Timeline getCurrentTimeline() {
    return playbackInfo.timeline;
  }

  // Not private so it can be called from an inner class without going through a thunk method.
  /* package */ void handleEvent(Message msg) {

    switch (msg.what) {
      case ExoPlayerImplInternal.MSG_PLAYBACK_INFO_CHANGED:
        handlePlaybackInfo(
            /* playbackInfo= */ (PlaybackInfo) msg.obj,
            /* operationAcks= */ msg.arg1,
            /* positionDiscontinuity= */ msg.arg2 != C.INDEX_UNSET,
            /* positionDiscontinuityReason= */ msg.arg2);
        break;
      case ExoPlayerImplInternal.MSG_PLAYBACK_PARAMETERS_CHANGED:
        handlePlaybackParameters((PlaybackParameters) msg.obj, /* operationAck= */ msg.arg1 != 0);
        break;
      default:
        throw new IllegalStateException();
    }
  }

  private int getCurrentWindowIndexInternal() {
    if (shouldMaskPosition()) {
      return maskingWindowIndex;
    } else {
      return playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period)
          .windowIndex;
    }
  }

  private void handlePlaybackParameters(
      PlaybackParameters playbackParameters, boolean operationAck) {
    if (operationAck) {
      pendingSetPlaybackParametersAcks--;
    }
    if (pendingSetPlaybackParametersAcks == 0) {
      if (!this.playbackParameters.equals(playbackParameters)) {
        this.playbackParameters = playbackParameters;
        notifyListeners(listener -> listener.onPlaybackParametersChanged(playbackParameters));
      }
    }
  }

  private void handlePlaybackInfo(
      PlaybackInfo playbackInfo,
      int operationAcks,
      boolean positionDiscontinuity,
      @DiscontinuityReason int positionDiscontinuityReason) {
    pendingOperationAcks -= operationAcks;
    if (pendingOperationAcks == 0) {
      if (playbackInfo.startPositionUs == C.TIME_UNSET) {
        // Replace internal unset start position with externally visible start position of zero.
        playbackInfo =
            playbackInfo.copyWithNewPosition(
                playbackInfo.periodId,
                /* positionUs= */ 0,
                playbackInfo.contentPositionUs,
                playbackInfo.totalBufferedDurationUs);
      }
      if (!this.playbackInfo.timeline.isEmpty() && playbackInfo.timeline.isEmpty()) {
        // Update the masking variables, which are used when the timeline becomes empty.
        resetMaskingPosition();
      }
      boolean seekProcessed = hasPendingSeek;
      hasPendingSeek = false;
      updatePlaybackInfo(
          playbackInfo,
          positionDiscontinuity,
          positionDiscontinuityReason,
          TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
          seekProcessed);
    }
  }

  private PlaybackInfo getResetPlaybackInfo(
      boolean clearPlaylist, boolean resetError, @Player.State int playbackState) {
    if (clearPlaylist) {
      // Reset list of media source holders which are used for creating the masking timeline.
      removeMediaSourceHolders(
          /* fromIndex= */ 0, /* toIndexExclusive= */ mediaSourceHolders.size());
      resetMaskingPosition();
    } else {
      maskingWindowIndex = getCurrentWindowIndex();
      maskingPeriodIndex = getCurrentPeriodIndex();
      maskingWindowPositionMs = getCurrentPosition();
    }
    Timeline timeline = playbackInfo.timeline;
    MediaPeriodId mediaPeriodId = playbackInfo.periodId;
    long contentPositionUs = playbackInfo.contentPositionUs;
    long startPositionUs = playbackInfo.positionUs;
    if (clearPlaylist) {
      timeline = Timeline.EMPTY;
      mediaPeriodId = playbackInfo.getDummyPeriodForEmptyTimeline();
      contentPositionUs = C.TIME_UNSET;
      startPositionUs = C.TIME_UNSET;
    }
    return new PlaybackInfo(
        timeline,
        mediaPeriodId,
        startPositionUs,
        contentPositionUs,
        playbackState,
        resetError ? null : playbackInfo.playbackError,
        /* isLoading= */ false,
        clearPlaylist ? TrackGroupArray.EMPTY : playbackInfo.trackGroups,
        clearPlaylist ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult,
        mediaPeriodId,
        startPositionUs,
        /* totalBufferedDurationUs= */ 0,
        startPositionUs);
  }

  private void updatePlaybackInfo(
      PlaybackInfo playbackInfo,
      boolean positionDiscontinuity,
      @DiscontinuityReason int positionDiscontinuityReason,
      @TimelineChangeReason int timelineChangeReason,
      boolean seekProcessed) {
    boolean previousIsPlaying = isPlaying();
    // Assign playback info immediately such that all getters return the right values.
    PlaybackInfo previousPlaybackInfo = this.playbackInfo;
    this.playbackInfo = playbackInfo;
    boolean isPlaying = isPlaying();
    notifyListeners(
        new PlaybackInfoUpdate(
            playbackInfo,
            previousPlaybackInfo,
            listeners,
            trackSelector,
            positionDiscontinuity,
            positionDiscontinuityReason,
            timelineChangeReason,
            seekProcessed,
            playWhenReady,
            /* isPlayingChanged= */ previousIsPlaying != isPlaying));
  }

  private void setMediaItemsInternal(
      List<MediaSource> mediaItems,
      int startWindowIndex,
      long startPositionMs,
      boolean resetToDefaultPosition) {
    int currentWindowIndex = getCurrentWindowIndexInternal();
    long currentPositionMs = getCurrentPosition();
    boolean currentPlayWhenReady = getPlayWhenReady();
    pendingOperationAcks++;
    if (!mediaSourceHolders.isEmpty()) {
      removeMediaSourceHolders(
          /* fromIndex= */ 0, /* toIndexExclusive= */ mediaSourceHolders.size());
    }
    List<Playlist.MediaSourceHolder> holders = addMediaSourceHolders(/* index= */ 0, mediaItems);
    Timeline timeline = maskTimeline();
    if (!timeline.isEmpty() && startWindowIndex >= timeline.getWindowCount()) {
      throw new IllegalSeekPositionException(timeline, startWindowIndex, startPositionMs);
    }
    // Evaluate the actual start position.
    if (resetToDefaultPosition) {
      startWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
      startPositionMs = C.TIME_UNSET;
    } else if (startWindowIndex == C.INDEX_UNSET) {
      startWindowIndex = currentWindowIndex;
      startPositionMs = currentPositionMs;
    }
    maskWindowIndexAndPositionForSeek(
        timeline, startWindowIndex == C.INDEX_UNSET ? 0 : startWindowIndex, startPositionMs);
    // mask the playback state
    int maskingPlaybackState = playbackInfo.playbackState;
    if (startWindowIndex != C.INDEX_UNSET) {
      // Position reset to startWindowIndex (results in pending initial seek).
      if (timeline.isEmpty() || startWindowIndex >= timeline.getWindowCount()) {
        // Setting an empty timeline or invalid seek transitions to ended.
        maskingPlaybackState = STATE_ENDED;
      } else {
        maskingPlaybackState = STATE_BUFFERING;
      }
    }
    boolean playbackStateChanged =
        playbackInfo.playbackState != STATE_IDLE
            && playbackInfo.playbackState != maskingPlaybackState;
    int finalMaskingPlaybackState = maskingPlaybackState;
    if (playbackStateChanged) {
      playbackInfo = playbackInfo.copyWithPlaybackState(finalMaskingPlaybackState);
    }
    internalPlayer.setMediaSources(
        holders, startWindowIndex, C.msToUs(startPositionMs), shuffleOrder);
    notifyListeners(
        listener -> {
          listener.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
          if (playbackStateChanged) {
            listener.onPlayerStateChanged(currentPlayWhenReady, finalMaskingPlaybackState);
          }
        });
  }

  private List<Playlist.MediaSourceHolder> addMediaSourceHolders(
      int index, List<MediaSource> mediaSources) {
    List<Playlist.MediaSourceHolder> holders = new ArrayList<>();
    for (int i = 0; i < mediaSources.size(); i++) {
      Playlist.MediaSourceHolder holder =
          new Playlist.MediaSourceHolder(mediaSources.get(i), useLazyPreparation);
      holders.add(holder);
      mediaSourceHolders.add(i + index, holder);
    }
    shuffleOrder =
        shuffleOrder.cloneAndInsert(
            /* insertionIndex= */ index, /* insertionCount= */ holders.size());
    return holders;
  }

  private void removeMediaItemsInternal(int fromIndex, int toIndex) {
    Assertions.checkArgument(
        fromIndex >= 0 && toIndex >= fromIndex && toIndex <= mediaSourceHolders.size());
    int currentWindowIndex = getCurrentWindowIndex();
    long currentPositionMs = getCurrentPosition();
    boolean currentPlayWhenReady = getPlayWhenReady();
    Timeline oldTimeline = getCurrentTimeline();
    int currentMediaSourceCount = mediaSourceHolders.size();
    pendingOperationAcks++;
    removeMediaSourceHolders(fromIndex, /* toIndexExclusive= */ toIndex);
    Timeline timeline =
        maskTimelineAndWindowIndex(currentWindowIndex, currentPositionMs, oldTimeline);
    // Player transitions to STATE_ENDED if the current index is part of the removed tail.
    final boolean transitionsToEnded =
        playbackInfo.playbackState != STATE_IDLE
            && playbackInfo.playbackState != STATE_ENDED
            && fromIndex < toIndex
            && toIndex == currentMediaSourceCount
            && currentWindowIndex >= timeline.getWindowCount();
    if (transitionsToEnded) {
      playbackInfo = playbackInfo.copyWithPlaybackState(STATE_ENDED);
    }
    internalPlayer.removeMediaSources(fromIndex, toIndex, shuffleOrder);
    notifyListeners(
        listener -> {
          listener.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
          if (transitionsToEnded) {
            listener.onPlayerStateChanged(currentPlayWhenReady, STATE_ENDED);
          }
        });
  }

  private List<Playlist.MediaSourceHolder> removeMediaSourceHolders(
      int fromIndex, int toIndexExclusive) {
    List<Playlist.MediaSourceHolder> removed = new ArrayList<>();
    for (int i = toIndexExclusive - 1; i >= fromIndex; i--) {
      removed.add(mediaSourceHolders.remove(i));
    }
    shuffleOrder = shuffleOrder.cloneAndRemove(fromIndex, toIndexExclusive);
    return removed;
  }

  private Timeline maskTimeline() {
    playbackInfo =
        playbackInfo.copyWithTimeline(
            mediaSourceHolders.isEmpty()
                ? Timeline.EMPTY
                : new Playlist.PlaylistTimeline(mediaSourceHolders, shuffleOrder));
    return playbackInfo.timeline;
  }

  private Timeline maskTimelineAndWindowIndex(
      int currentWindowIndex, long currentPositionMs, Timeline oldTimeline) {
    Timeline maskingTimeline = maskTimeline();
    if (oldTimeline.isEmpty()) {
      // The index is the default index or was set by a seek in the empty old timeline.
      maskingWindowIndex = currentWindowIndex;
      if (!maskingTimeline.isEmpty() && currentWindowIndex >= maskingTimeline.getWindowCount()) {
        // The seek is not valid in the new timeline.
        maskWithDefaultPosition(maskingTimeline);
      }
      return maskingTimeline;
    }
    @Nullable
    Pair<Object, Long> periodPosition =
        oldTimeline.getPeriodPosition(
            window,
            period,
            currentWindowIndex,
            C.msToUs(currentPositionMs),
            /* defaultPositionProjectionUs= */ 0);
    Object periodUid = Util.castNonNull(periodPosition).first;
    if (maskingTimeline.getIndexOfPeriod(periodUid) != C.INDEX_UNSET) {
      // Get the window index of the current period that exists in the new timeline also.
      maskingWindowIndex = maskingTimeline.getPeriodByUid(periodUid, period).windowIndex;
    } else {
      // Period uid not found in new timeline. Try to get subsequent period.
      @Nullable
      Object nextPeriodUid =
          ExoPlayerImplInternal.resolveSubsequentPeriod(
              window,
              period,
              repeatMode,
              shuffleModeEnabled,
              periodUid,
              oldTimeline,
              maskingTimeline);
      if (nextPeriodUid != null) {
        // Set masking to the default position of the window of the subsequent period.
        maskingWindowIndex = maskingTimeline.getPeriodByUid(nextPeriodUid, period).windowIndex;
        maskingPeriodIndex = maskingTimeline.getWindow(maskingWindowIndex, window).firstPeriodIndex;
        maskingWindowPositionMs = window.getDefaultPositionMs();
      } else {
        // Reset if no subsequent period is found.
        maskWithDefaultPosition(maskingTimeline);
      }
    }
    return maskingTimeline;
  }

  private void maskWindowIndexAndPositionForSeek(
      Timeline timeline, int windowIndex, long positionMs) {
    maskingWindowIndex = windowIndex;
    if (timeline.isEmpty()) {
      maskingWindowPositionMs = positionMs == C.TIME_UNSET ? 0 : positionMs;
      maskingPeriodIndex = 0;
    } else if (windowIndex >= timeline.getWindowCount()) {
      // An initial seek now proves to be invalid in the actual timeline.
      maskWithDefaultPosition(timeline);
    } else {
      long windowPositionUs =
          positionMs == C.TIME_UNSET
              ? timeline.getWindow(windowIndex, window).getDefaultPositionUs()
              : C.msToUs(positionMs);
      Pair<Object, Long> periodUidAndPosition =
          timeline.getPeriodPosition(window, period, windowIndex, windowPositionUs);
      maskingWindowPositionMs = C.usToMs(windowPositionUs);
      maskingPeriodIndex = timeline.getIndexOfPeriod(periodUidAndPosition.first);
    }
  }

  private void maskWithDefaultPosition(Timeline timeline) {
    if (timeline.isEmpty()) {
      resetMaskingPosition();
      return;
    }
    maskingWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
    timeline.getWindow(maskingWindowIndex, window);
    maskingWindowPositionMs = window.getDefaultPositionMs();
    maskingPeriodIndex = window.firstPeriodIndex;
  }

  private void resetMaskingPosition() {
    maskingWindowIndex = C.INDEX_UNSET;
    maskingWindowPositionMs = 0;
    maskingPeriodIndex = 0;
  }

  private void notifyListeners(ListenerInvocation listenerInvocation) {
    CopyOnWriteArrayList<ListenerHolder> listenerSnapshot = new CopyOnWriteArrayList<>(listeners);
    notifyListeners(() -> invokeAll(listenerSnapshot, listenerInvocation));
  }

  private void notifyListeners(Runnable listenerNotificationRunnable) {
    boolean isRunningRecursiveListenerNotification = !pendingListenerNotifications.isEmpty();
    pendingListenerNotifications.addLast(listenerNotificationRunnable);
    if (isRunningRecursiveListenerNotification) {
      return;
    }
    while (!pendingListenerNotifications.isEmpty()) {
      pendingListenerNotifications.peekFirst().run();
      pendingListenerNotifications.removeFirst();
    }
  }

  private long periodPositionUsToWindowPositionMs(MediaPeriodId periodId, long positionUs) {
    long positionMs = C.usToMs(positionUs);
    playbackInfo.timeline.getPeriodByUid(periodId.periodUid, period);
    positionMs += period.getPositionInWindowMs();
    return positionMs;
  }

  private boolean shouldMaskPosition() {
    return playbackInfo.timeline.isEmpty() || pendingOperationAcks > 0;
  }

  private static final class PlaybackInfoUpdate implements Runnable {

    private final PlaybackInfo playbackInfo;
    private final CopyOnWriteArrayList<ListenerHolder> listenerSnapshot;
    private final TrackSelector trackSelector;
    private final boolean positionDiscontinuity;
    private final @Player.DiscontinuityReason int positionDiscontinuityReason;
    private final int timelineChangeReason;
    private final boolean seekProcessed;
    private final boolean playbackStateChanged;
    private final boolean playbackErrorChanged;
    private final boolean timelineChanged;
    private final boolean isLoadingChanged;
    private final boolean trackSelectorResultChanged;
    private final boolean playWhenReady;
    private final boolean isPlayingChanged;

    public PlaybackInfoUpdate(
        PlaybackInfo playbackInfo,
        PlaybackInfo previousPlaybackInfo,
        CopyOnWriteArrayList<ListenerHolder> listeners,
        TrackSelector trackSelector,
        boolean positionDiscontinuity,
        @DiscontinuityReason int positionDiscontinuityReason,
        @TimelineChangeReason int timelineChangeReason,
        boolean seekProcessed,
        boolean playWhenReady,
        boolean isPlayingChanged) {
      this.playbackInfo = playbackInfo;
      this.listenerSnapshot = new CopyOnWriteArrayList<>(listeners);
      this.trackSelector = trackSelector;
      this.positionDiscontinuity = positionDiscontinuity;
      this.positionDiscontinuityReason = positionDiscontinuityReason;
      this.timelineChangeReason = timelineChangeReason;
      this.seekProcessed = seekProcessed;
      this.playWhenReady = playWhenReady;
      this.isPlayingChanged = isPlayingChanged;
      playbackStateChanged = previousPlaybackInfo.playbackState != playbackInfo.playbackState;
      playbackErrorChanged =
          previousPlaybackInfo.playbackError != playbackInfo.playbackError
              && playbackInfo.playbackError != null;
      isLoadingChanged = previousPlaybackInfo.isLoading != playbackInfo.isLoading;
      timelineChanged = !previousPlaybackInfo.timeline.equals(playbackInfo.timeline);
      trackSelectorResultChanged =
          previousPlaybackInfo.trackSelectorResult != playbackInfo.trackSelectorResult;
    }

    @Override
    public void run() {
      if (timelineChanged) {
        invokeAll(
            listenerSnapshot,
            listener -> listener.onTimelineChanged(playbackInfo.timeline, timelineChangeReason));
      }
      if (positionDiscontinuity) {
        invokeAll(
            listenerSnapshot,
            listener -> listener.onPositionDiscontinuity(positionDiscontinuityReason));
      }
      if (playbackErrorChanged) {
        invokeAll(listenerSnapshot, listener -> listener.onPlayerError(playbackInfo.playbackError));
      }
      if (trackSelectorResultChanged) {
        trackSelector.onSelectionActivated(playbackInfo.trackSelectorResult.info);
        invokeAll(
            listenerSnapshot,
            listener ->
                listener.onTracksChanged(
                    playbackInfo.trackGroups, playbackInfo.trackSelectorResult.selections));
      }
      if (isLoadingChanged) {
        invokeAll(listenerSnapshot, listener -> listener.onLoadingChanged(playbackInfo.isLoading));
      }
      if (playbackStateChanged) {
        invokeAll(
            listenerSnapshot,
            listener -> listener.onPlayerStateChanged(playWhenReady, playbackInfo.playbackState));
      }
      if (isPlayingChanged) {
        invokeAll(
            listenerSnapshot,
            listener ->
                listener.onIsPlayingChanged(playbackInfo.playbackState == Player.STATE_READY));
      }
      if (seekProcessed) {
        invokeAll(listenerSnapshot, EventListener::onSeekProcessed);
      }
    }
  }

  private static void invokeAll(
      CopyOnWriteArrayList<ListenerHolder> listeners, ListenerInvocation listenerInvocation) {
    for (ListenerHolder listenerHolder : listeners) {
      listenerHolder.invoke(listenerInvocation);
    }
  }
}
