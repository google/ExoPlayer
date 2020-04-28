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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

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
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
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
  private final List<MediaSourceList.MediaSourceHolder> mediaSourceHolders;
  private final boolean useLazyPreparation;
  private final MediaSourceFactory mediaSourceFactory;

  @RepeatMode private int repeatMode;
  private boolean shuffleModeEnabled;
  private int pendingOperationAcks;
  private boolean hasPendingDiscontinuity;
  @DiscontinuityReason private int pendingDiscontinuityReason;
  @PlayWhenReadyChangeReason private int pendingPlayWhenReadyChangeReason;
  private boolean foregroundMode;
  private int pendingSetPlaybackSpeedAcks;
  private float playbackSpeed;
  private SeekParameters seekParameters;
  private ShuffleOrder shuffleOrder;
  private boolean pauseAtEndOfMediaItems;
  private boolean hasAdsMediaSource;

  // Playback information when there is no pending seek/set source operation.
  private PlaybackInfo playbackInfo;

  // Playback information when there is a pending seek/set source operation.
  private int maskingWindowIndex;
  private int maskingPeriodIndex;
  private long maskingWindowPositionMs;

  /**
   * Constructs an instance. Must be called from a thread that has an associated {@link Looper}.
   *
   * @param renderers The {@link Renderer}s.
   * @param trackSelector The {@link TrackSelector}.
   * @param mediaSourceFactory The {@link MediaSourceFactory}.
   * @param loadControl The {@link LoadControl}.
   * @param bandwidthMeter The {@link BandwidthMeter}.
   * @param analyticsCollector The {@link AnalyticsCollector}.
   * @param useLazyPreparation Whether playlist items are prepared lazily. If false, all manifest
   *     loads and other initial preparation steps happen immediately. If true, these initial
   *     preparations are triggered only when the player starts buffering the media.
   * @param clock The {@link Clock}.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  @SuppressLint("HandlerLeak")
  public ExoPlayerImpl(
      Renderer[] renderers,
      TrackSelector trackSelector,
      MediaSourceFactory mediaSourceFactory,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      @Nullable AnalyticsCollector analyticsCollector,
      boolean useLazyPreparation,
      Clock clock,
      Looper looper) {
    Log.i(TAG, "Init " + Integer.toHexString(System.identityHashCode(this)) + " ["
        + ExoPlayerLibraryInfo.VERSION_SLASHY + "] [" + Util.DEVICE_DEBUG_INFO + "]");
    Assertions.checkState(renderers.length > 0);
    this.renderers = checkNotNull(renderers);
    this.trackSelector = checkNotNull(trackSelector);
    this.mediaSourceFactory = mediaSourceFactory;
    this.useLazyPreparation = useLazyPreparation;
    repeatMode = Player.REPEAT_MODE_OFF;
    listeners = new CopyOnWriteArrayList<>();
    mediaSourceHolders = new ArrayList<>();
    shuffleOrder = new ShuffleOrder.DefaultShuffleOrder(/* length= */ 0);
    emptyTrackSelectorResult =
        new TrackSelectorResult(
            new RendererConfiguration[renderers.length],
            new TrackSelection[renderers.length],
            null);
    period = new Timeline.Period();
    playbackSpeed = Player.DEFAULT_PLAYBACK_SPEED;
    seekParameters = SeekParameters.DEFAULT;
    maskingWindowIndex = C.INDEX_UNSET;
    eventHandler =
        new Handler(looper) {
          @Override
          public void handleMessage(Message msg) {
            ExoPlayerImpl.this.handleEvent(msg);
          }
        };
    playbackInfo = PlaybackInfo.createDummy(emptyTrackSelectorResult);
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

  /**
   * Configures the player to throw when it detects it's stuck buffering.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release. It should
   * only be called before the player is used.
   */
  public void experimental_throwWhenStuckBuffering() {
    internalPlayer.experimental_throwWhenStuckBuffering();
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
  @Nullable
  public DeviceComponent getDeviceComponent() {
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
    return playbackInfo.playbackSuppressionReason;
  }

  @Deprecated
  @Override
  @Nullable
  public ExoPlaybackException getPlaybackError() {
    return getPlayerError();
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlayerError() {
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
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
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
  public void setMediaItems(
      List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs) {
    setMediaSources(createMediaSources(mediaItems), startWindowIndex, startPositionMs);
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
    setMediaSourcesInternal(
        mediaSources,
        /* startWindowIndex= */ C.INDEX_UNSET,
        /* startPositionMs= */ C.TIME_UNSET,
        /* resetToDefaultPosition= */ resetPosition);
  }

  @Override
  public void setMediaSources(
      List<MediaSource> mediaSources, int startWindowIndex, long startPositionMs) {
    setMediaSourcesInternal(
        mediaSources, startWindowIndex, startPositionMs, /* resetToDefaultPosition= */ false);
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    addMediaItems(/* index= */ mediaSourceHolders.size(), mediaItems);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    addMediaSources(index, createMediaSources(mediaItems));
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
    validateMediaSources(mediaSources, /* mediaSourceReplacement= */ false);
    int currentWindowIndex = getCurrentWindowIndex();
    long currentPositionMs = getCurrentPosition();
    Timeline oldTimeline = getCurrentTimeline();
    pendingOperationAcks++;
    List<MediaSourceList.MediaSourceHolder> holders = addMediaSourceHolders(index, mediaSources);
    PlaybackInfo playbackInfo =
        maskTimelineAndWindowIndex(currentWindowIndex, currentPositionMs, oldTimeline);
    internalPlayer.addMediaSources(index, holders, shuffleOrder);
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    Assertions.checkArgument(toIndex > fromIndex);
    removeMediaItemsInternal(fromIndex, toIndex);
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
    MediaSourceList.moveMediaSourceHolders(mediaSourceHolders, fromIndex, toIndex, newFromIndex);
    PlaybackInfo playbackInfo =
        maskTimelineAndWindowIndex(currentWindowIndex, currentPositionMs, oldTimeline);
    internalPlayer.moveMediaSources(fromIndex, toIndex, newFromIndex, shuffleOrder);
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false);
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
    PlaybackInfo playbackInfo = maskTimeline();
    maskWithCurrentPosition();
    pendingOperationAcks++;
    this.shuffleOrder = shuffleOrder;
    internalPlayer.setShuffleOrder(shuffleOrder);
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    setPlayWhenReady(
        playWhenReady,
        PLAYBACK_SUPPRESSION_REASON_NONE,
        PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
  }

  @Override
  public void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
    if (this.pauseAtEndOfMediaItems == pauseAtEndOfMediaItems) {
      return;
    }
    this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems;
    internalPlayer.setPauseAtEndOfWindow(pauseAtEndOfMediaItems);
  }

  @Override
  public boolean getPauseAtEndOfMediaItems() {
    return pauseAtEndOfMediaItems;
  }

  public void setPlayWhenReady(
      boolean playWhenReady,
      @PlaybackSuppressionReason int playbackSuppressionReason,
      @PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
    if (playbackInfo.playWhenReady == playWhenReady
        && playbackInfo.playbackSuppressionReason == playbackSuppressionReason) {
      return;
    }
    maskWithCurrentPosition();
    pendingOperationAcks++;
    PlaybackInfo playbackInfo =
        this.playbackInfo.copyWithPlayWhenReady(playWhenReady, playbackSuppressionReason);
    internalPlayer.setPlayWhenReady(playWhenReady, playbackSuppressionReason);
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        playWhenReadyChangeReason,
        /* seekProcessed= */ false);
  }

  @Override
  public boolean getPlayWhenReady() {
    return playbackInfo.playWhenReady;
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
    @Player.State
    int newPlaybackState =
        getPlaybackState() == Player.STATE_IDLE ? Player.STATE_IDLE : Player.STATE_BUFFERING;
    PlaybackInfo playbackInfo = this.playbackInfo.copyWithPlaybackState(newPlaybackState);
    internalPlayer.seekTo(timeline, windowIndex, C.msToUs(positionMs));
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ true,
        /* positionDiscontinuityReason= */ DISCONTINUITY_REASON_SEEK,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ true);
  }

  /** @deprecated Use {@link #setPlaybackSpeed(float)} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    setPlaybackSpeed(
        playbackParameters != null ? playbackParameters.speed : Player.DEFAULT_PLAYBACK_SPEED);
  }

  /** @deprecated Use {@link #getPlaybackSpeed()} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public PlaybackParameters getPlaybackParameters() {
    return new PlaybackParameters(playbackSpeed);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void setPlaybackSpeed(float playbackSpeed) {
    Assertions.checkState(playbackSpeed > 0);
    if (this.playbackSpeed == playbackSpeed) {
      return;
    }
    pendingSetPlaybackSpeedAcks++;
    this.playbackSpeed = playbackSpeed;
    PlaybackParameters playbackParameters = new PlaybackParameters(playbackSpeed);
    internalPlayer.setPlaybackSpeed(playbackSpeed);
    notifyListeners(
        listener -> {
          listener.onPlaybackParametersChanged(playbackParameters);
          listener.onPlaybackSpeedChanged(playbackSpeed);
        });
  }

  @Override
  public float getPlaybackSpeed() {
    return playbackSpeed;
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
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
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
      return playbackInfo.requestedContentPositionUs == C.TIME_UNSET
          ? playbackInfo.timeline.getWindow(getCurrentWindowIndex(), window).getDefaultPositionMs()
          : period.getPositionInWindowMs() + C.usToMs(playbackInfo.requestedContentPositionUs);
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
        handlePlaybackInfo((ExoPlayerImplInternal.PlaybackInfoUpdate) msg.obj);
        break;
      case ExoPlayerImplInternal.MSG_PLAYBACK_SPEED_CHANGED:
        handlePlaybackSpeed((Float) msg.obj, /* operationAck= */ msg.arg1 != 0);
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

  private List<MediaSource> createMediaSources(List<MediaItem> mediaItems) {
    List<MediaSource> mediaSources = new ArrayList<>();
    for (int i = 0; i < mediaItems.size(); i++) {
      mediaSources.add(mediaSourceFactory.createMediaSource(mediaItems.get(i)));
    }
    return mediaSources;
  }

  @SuppressWarnings("deprecation")
  private void handlePlaybackSpeed(float playbackSpeed, boolean operationAck) {
    if (operationAck) {
      pendingSetPlaybackSpeedAcks--;
    }
    if (pendingSetPlaybackSpeedAcks == 0) {
      if (this.playbackSpeed != playbackSpeed) {
        this.playbackSpeed = playbackSpeed;
        notifyListeners(
            listener -> {
              listener.onPlaybackParametersChanged(new PlaybackParameters(playbackSpeed));
              listener.onPlaybackSpeedChanged(playbackSpeed);
            });
      }
    }
  }

  private void handlePlaybackInfo(ExoPlayerImplInternal.PlaybackInfoUpdate playbackInfoUpdate) {
    pendingOperationAcks -= playbackInfoUpdate.operationAcks;
    if (playbackInfoUpdate.positionDiscontinuity) {
      hasPendingDiscontinuity = true;
      pendingDiscontinuityReason = playbackInfoUpdate.discontinuityReason;
    }
    if (playbackInfoUpdate.hasPlayWhenReadyChangeReason) {
      pendingPlayWhenReadyChangeReason = playbackInfoUpdate.playWhenReadyChangeReason;
    }
    if (pendingOperationAcks == 0) {
      if (!this.playbackInfo.timeline.isEmpty()
          && playbackInfoUpdate.playbackInfo.timeline.isEmpty()) {
        // Update the masking variables, which are used when the timeline becomes empty.
        resetMaskingPosition();
      }
      boolean positionDiscontinuity = hasPendingDiscontinuity;
      hasPendingDiscontinuity = false;
      updatePlaybackInfo(
          playbackInfoUpdate.playbackInfo,
          positionDiscontinuity,
          pendingDiscontinuityReason,
          TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
          pendingPlayWhenReadyChangeReason,
          /* seekProcessed= */ false);
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
      maskWithCurrentPosition();
    }
    Timeline timeline = playbackInfo.timeline;
    MediaPeriodId mediaPeriodId = playbackInfo.periodId;
    long requestedContentPositionUs = playbackInfo.requestedContentPositionUs;
    long positionUs = playbackInfo.positionUs;
    if (clearPlaylist) {
      timeline = Timeline.EMPTY;
      mediaPeriodId = PlaybackInfo.getDummyPeriodForEmptyTimeline();
      requestedContentPositionUs = C.TIME_UNSET;
      positionUs = 0;
    }
    return new PlaybackInfo(
        timeline,
        mediaPeriodId,
        requestedContentPositionUs,
        playbackState,
        resetError ? null : playbackInfo.playbackError,
        /* isLoading= */ false,
        clearPlaylist ? TrackGroupArray.EMPTY : playbackInfo.trackGroups,
        clearPlaylist ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult,
        mediaPeriodId,
        playbackInfo.playWhenReady,
        playbackInfo.playbackSuppressionReason,
        positionUs,
        /* totalBufferedDurationUs= */ 0,
        positionUs);
  }

  private void updatePlaybackInfo(
      PlaybackInfo playbackInfo,
      boolean positionDiscontinuity,
      @DiscontinuityReason int positionDiscontinuityReason,
      @TimelineChangeReason int timelineChangeReason,
      @PlayWhenReadyChangeReason int playWhenReadyChangeReason,
      boolean seekProcessed) {
    // Assign playback info immediately such that all getters return the right values.
    PlaybackInfo previousPlaybackInfo = this.playbackInfo;
    this.playbackInfo = playbackInfo;
    notifyListeners(
        new PlaybackInfoUpdate(
            playbackInfo,
            previousPlaybackInfo,
            listeners,
            trackSelector,
            positionDiscontinuity,
            positionDiscontinuityReason,
            timelineChangeReason,
            playWhenReadyChangeReason,
            seekProcessed));
  }

  private void setMediaSourcesInternal(
      List<MediaSource> mediaSources,
      int startWindowIndex,
      long startPositionMs,
      boolean resetToDefaultPosition) {
    validateMediaSources(mediaSources, /* mediaSourceReplacement= */ true);
    int currentWindowIndex = getCurrentWindowIndexInternal();
    long currentPositionMs = getCurrentPosition();
    pendingOperationAcks++;
    if (!mediaSourceHolders.isEmpty()) {
      removeMediaSourceHolders(
          /* fromIndex= */ 0, /* toIndexExclusive= */ mediaSourceHolders.size());
    }
    List<MediaSourceList.MediaSourceHolder> holders =
        addMediaSourceHolders(/* index= */ 0, mediaSources);
    PlaybackInfo playbackInfo = maskTimeline();
    Timeline timeline = playbackInfo.timeline;
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
    // Mask the playback state.
    int maskingPlaybackState = playbackInfo.playbackState;
    if (startWindowIndex != C.INDEX_UNSET && playbackInfo.playbackState != STATE_IDLE) {
      // Position reset to startWindowIndex (results in pending initial seek).
      if (timeline.isEmpty() || startWindowIndex >= timeline.getWindowCount()) {
        // Setting an empty timeline or invalid seek transitions to ended.
        maskingPlaybackState = STATE_ENDED;
      } else {
        maskingPlaybackState = STATE_BUFFERING;
      }
    }
    playbackInfo = playbackInfo.copyWithPlaybackState(maskingPlaybackState);
    internalPlayer.setMediaSources(
        holders, startWindowIndex, C.msToUs(startPositionMs), shuffleOrder);
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ Player.DISCONTINUITY_REASON_INTERNAL,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false);
  }

  private List<MediaSourceList.MediaSourceHolder> addMediaSourceHolders(
      int index, List<MediaSource> mediaSources) {
    List<MediaSourceList.MediaSourceHolder> holders = new ArrayList<>();
    for (int i = 0; i < mediaSources.size(); i++) {
      MediaSourceList.MediaSourceHolder holder =
          new MediaSourceList.MediaSourceHolder(mediaSources.get(i), useLazyPreparation);
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
    Timeline oldTimeline = getCurrentTimeline();
    int currentMediaSourceCount = mediaSourceHolders.size();
    pendingOperationAcks++;
    removeMediaSourceHolders(fromIndex, /* toIndexExclusive= */ toIndex);
    PlaybackInfo playbackInfo =
        maskTimelineAndWindowIndex(currentWindowIndex, currentPositionMs, oldTimeline);
    // Player transitions to STATE_ENDED if the current index is part of the removed tail.
    final boolean transitionsToEnded =
        playbackInfo.playbackState != STATE_IDLE
            && playbackInfo.playbackState != STATE_ENDED
            && fromIndex < toIndex
            && toIndex == currentMediaSourceCount
            && currentWindowIndex >= playbackInfo.timeline.getWindowCount();
    if (transitionsToEnded) {
      playbackInfo = playbackInfo.copyWithPlaybackState(STATE_ENDED);
    }
    internalPlayer.removeMediaSources(fromIndex, toIndex, shuffleOrder);
    updatePlaybackInfo(
        playbackInfo,
        /* positionDiscontinuity= */ false,
        /* ignored */ Player.DISCONTINUITY_REASON_INTERNAL,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false);
  }

  private List<MediaSourceList.MediaSourceHolder> removeMediaSourceHolders(
      int fromIndex, int toIndexExclusive) {
    List<MediaSourceList.MediaSourceHolder> removed = new ArrayList<>();
    for (int i = toIndexExclusive - 1; i >= fromIndex; i--) {
      removed.add(mediaSourceHolders.remove(i));
    }
    shuffleOrder = shuffleOrder.cloneAndRemove(fromIndex, toIndexExclusive);
    if (mediaSourceHolders.isEmpty()) {
      hasAdsMediaSource = false;
    }
    return removed;
  }

  /**
   * Validates media sources before any modification of the existing list of media sources is made.
   * This way we can throw an exception before changing the state of the player in case of a
   * validation failure.
   *
   * @param mediaSources The media sources to set or add.
   * @param mediaSourceReplacement Whether the given media sources will replace existing ones.
   */
  private void validateMediaSources(
      List<MediaSource> mediaSources, boolean mediaSourceReplacement) {
    if (hasAdsMediaSource && !mediaSourceReplacement && !mediaSources.isEmpty()) {
      // Adding media sources to an ads media source is not allowed
      // (see https://github.com/google/ExoPlayer/issues/3750).
      throw new IllegalStateException();
    }
    int sizeAfterModification =
        mediaSources.size() + (mediaSourceReplacement ? 0 : mediaSourceHolders.size());
    for (int i = 0; i < mediaSources.size(); i++) {
      MediaSource mediaSource = checkNotNull(mediaSources.get(i));
      if (mediaSource instanceof AdsMediaSource) {
        if (sizeAfterModification > 1) {
          // Ads media sources only allowed with a single source
          // (see https://github.com/google/ExoPlayer/issues/3750).
          throw new IllegalArgumentException();
        }
        hasAdsMediaSource = true;
      }
    }
  }

  private PlaybackInfo maskTimeline() {
    return playbackInfo.copyWithTimeline(
        mediaSourceHolders.isEmpty()
            ? Timeline.EMPTY
            : new MediaSourceList.PlaylistTimeline(mediaSourceHolders, shuffleOrder));
  }

  private PlaybackInfo maskTimelineAndWindowIndex(
      int currentWindowIndex, long currentPositionMs, Timeline oldTimeline) {
    PlaybackInfo playbackInfo = maskTimeline();
    Timeline maskingTimeline = playbackInfo.timeline;
    if (oldTimeline.isEmpty()) {
      // The index is the default index or was set by a seek in the empty old timeline.
      maskingWindowIndex = currentWindowIndex;
      if (!maskingTimeline.isEmpty() && currentWindowIndex >= maskingTimeline.getWindowCount()) {
        // The seek is not valid in the new timeline.
        maskWithDefaultPosition(maskingTimeline);
      }
      return playbackInfo;
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
      maskingPeriodIndex = maskingTimeline.getIndexOfPeriod(periodUid);
      maskingWindowPositionMs = currentPositionMs;
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
    return playbackInfo;
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

  private void maskWithCurrentPosition() {
    maskingWindowIndex = getCurrentWindowIndexInternal();
    maskingPeriodIndex = getCurrentPeriodIndex();
    maskingWindowPositionMs = getCurrentPosition();
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
    @DiscontinuityReason private final int positionDiscontinuityReason;
    @TimelineChangeReason private final int timelineChangeReason;
    @PlayWhenReadyChangeReason private final int playWhenReadyChangeReason;
    private final boolean seekProcessed;
    private final boolean playbackStateChanged;
    private final boolean playbackErrorChanged;
    private final boolean timelineChanged;
    private final boolean isLoadingChanged;
    private final boolean trackSelectorResultChanged;
    private final boolean isPlayingChanged;
    private final boolean playWhenReadyChanged;
    private final boolean playbackSuppressionReasonChanged;

    public PlaybackInfoUpdate(
        PlaybackInfo playbackInfo,
        PlaybackInfo previousPlaybackInfo,
        CopyOnWriteArrayList<ListenerHolder> listeners,
        TrackSelector trackSelector,
        boolean positionDiscontinuity,
        @DiscontinuityReason int positionDiscontinuityReason,
        @TimelineChangeReason int timelineChangeReason,
        @PlayWhenReadyChangeReason int playWhenReadyChangeReason,
        boolean seekProcessed) {
      this.playbackInfo = playbackInfo;
      this.listenerSnapshot = new CopyOnWriteArrayList<>(listeners);
      this.trackSelector = trackSelector;
      this.positionDiscontinuity = positionDiscontinuity;
      this.positionDiscontinuityReason = positionDiscontinuityReason;
      this.timelineChangeReason = timelineChangeReason;
      this.playWhenReadyChangeReason = playWhenReadyChangeReason;
      this.seekProcessed = seekProcessed;
      playbackStateChanged = previousPlaybackInfo.playbackState != playbackInfo.playbackState;
      playbackErrorChanged =
          previousPlaybackInfo.playbackError != playbackInfo.playbackError
              && playbackInfo.playbackError != null;
      isLoadingChanged = previousPlaybackInfo.isLoading != playbackInfo.isLoading;
      timelineChanged = !previousPlaybackInfo.timeline.equals(playbackInfo.timeline);
      trackSelectorResultChanged =
          previousPlaybackInfo.trackSelectorResult != playbackInfo.trackSelectorResult;
      playWhenReadyChanged = previousPlaybackInfo.playWhenReady != playbackInfo.playWhenReady;
      playbackSuppressionReasonChanged =
          previousPlaybackInfo.playbackSuppressionReason != playbackInfo.playbackSuppressionReason;
      isPlayingChanged = isPlaying(previousPlaybackInfo) != isPlaying(playbackInfo);
    }

    @SuppressWarnings("deprecation")
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
        invokeAll(
            listenerSnapshot, listener -> listener.onIsLoadingChanged(playbackInfo.isLoading));
      }
      if (playbackStateChanged || playWhenReadyChanged) {
        invokeAll(
            listenerSnapshot,
            listener ->
                listener.onPlayerStateChanged(
                    playbackInfo.playWhenReady, playbackInfo.playbackState));
      }
      if (playbackStateChanged) {
        invokeAll(
            listenerSnapshot,
            listener -> listener.onPlaybackStateChanged(playbackInfo.playbackState));
      }
      if (playWhenReadyChanged) {
        invokeAll(
            listenerSnapshot,
            listener ->
                listener.onPlayWhenReadyChanged(
                    playbackInfo.playWhenReady, playWhenReadyChangeReason));
      }
      if (playbackSuppressionReasonChanged) {
        invokeAll(
            listenerSnapshot,
            listener ->
                listener.onPlaybackSuppressionReasonChanged(
                    playbackInfo.playbackSuppressionReason));
      }
      if (isPlayingChanged) {
        invokeAll(
            listenerSnapshot, listener -> listener.onIsPlayingChanged(isPlaying(playbackInfo)));
      }
      if (seekProcessed) {
        invokeAll(listenerSnapshot, EventListener::onSeekProcessed);
      }
    }

    private static boolean isPlaying(PlaybackInfo playbackInfo) {
      return playbackInfo.playbackState == Player.STATE_READY
          && playbackInfo.playWhenReady
          && playbackInfo.playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
    }
  }

  private static void invokeAll(
      CopyOnWriteArrayList<ListenerHolder> listeners, ListenerInvocation listenerInvocation) {
    for (ListenerHolder listenerHolder : listeners) {
      listenerHolder.invoke(listenerInvocation);
    }
  }
}
