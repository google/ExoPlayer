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
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/** An {@link ExoPlayer} implementation. */
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
  /* package */ final Commands permanentAvailableCommands;

  private final Renderer[] renderers;
  private final TrackSelector trackSelector;
  private final HandlerWrapper playbackInfoUpdateHandler;
  private final ExoPlayerImplInternal.PlaybackInfoUpdateListener playbackInfoUpdateListener;
  private final ExoPlayerImplInternal internalPlayer;
  private final ListenerSet<Player.EventListener> listeners;
  private final CopyOnWriteArraySet<AudioOffloadListener> audioOffloadListeners;
  private final Timeline.Period period;
  private final List<MediaSourceHolderSnapshot> mediaSourceHolderSnapshots;
  private final boolean useLazyPreparation;
  private final MediaSourceFactory mediaSourceFactory;
  @Nullable private final AnalyticsCollector analyticsCollector;
  private final Looper applicationLooper;
  private final BandwidthMeter bandwidthMeter;
  private final Clock clock;

  @RepeatMode private int repeatMode;
  private boolean shuffleModeEnabled;
  private int pendingOperationAcks;
  @DiscontinuityReason private int pendingDiscontinuityReason;
  private boolean pendingDiscontinuity;
  @PlayWhenReadyChangeReason private int pendingPlayWhenReadyChangeReason;
  private boolean foregroundMode;
  private SeekParameters seekParameters;
  private ShuffleOrder shuffleOrder;
  private boolean pauseAtEndOfMediaItems;
  private Commands availableCommands;
  private MediaMetadata mediaMetadata;

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
   * @param seekParameters The {@link SeekParameters}.
   * @param livePlaybackSpeedControl The {@link LivePlaybackSpeedControl}.
   * @param releaseTimeoutMs The timeout for calls to {@link #release()} in milliseconds.
   * @param pauseAtEndOfMediaItems Whether to pause playback at the end of each media item.
   * @param clock The {@link Clock}.
   * @param applicationLooper The {@link Looper} that must be used for all calls to the player and
   *     which is used to call listeners on.
   * @param wrappingPlayer The {@link Player} wrapping this one if applicable. This player instance
   *     should be used for all externally visible callbacks.
   * @param additionalPermanentAvailableCommands The {@link Commands} that are permanently available
   *     in the wrapping player but that are not in this player.
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
      SeekParameters seekParameters,
      LivePlaybackSpeedControl livePlaybackSpeedControl,
      long releaseTimeoutMs,
      boolean pauseAtEndOfMediaItems,
      Clock clock,
      Looper applicationLooper,
      @Nullable Player wrappingPlayer,
      Commands additionalPermanentAvailableCommands) {
    Log.i(
        TAG,
        "Init "
            + Integer.toHexString(System.identityHashCode(this))
            + " ["
            + ExoPlayerLibraryInfo.VERSION_SLASHY
            + "] ["
            + Util.DEVICE_DEBUG_INFO
            + "]");
    checkState(renderers.length > 0);
    this.renderers = checkNotNull(renderers);
    this.trackSelector = checkNotNull(trackSelector);
    this.mediaSourceFactory = mediaSourceFactory;
    this.bandwidthMeter = bandwidthMeter;
    this.analyticsCollector = analyticsCollector;
    this.useLazyPreparation = useLazyPreparation;
    this.seekParameters = seekParameters;
    this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems;
    this.applicationLooper = applicationLooper;
    this.clock = clock;
    repeatMode = Player.REPEAT_MODE_OFF;
    Player playerForListeners = wrappingPlayer != null ? wrappingPlayer : this;
    listeners =
        new ListenerSet<>(
            applicationLooper,
            clock,
            (listener, flags) -> listener.onEvents(playerForListeners, new Events(flags)));
    audioOffloadListeners = new CopyOnWriteArraySet<>();
    mediaSourceHolderSnapshots = new ArrayList<>();
    shuffleOrder = new ShuffleOrder.DefaultShuffleOrder(/* length= */ 0);
    emptyTrackSelectorResult =
        new TrackSelectorResult(
            new RendererConfiguration[renderers.length],
            new ExoTrackSelection[renderers.length],
            /* info= */ null);
    period = new Timeline.Period();
    permanentAvailableCommands =
        new Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_PREPARE_STOP,
                COMMAND_SET_SPEED_AND_PITCH,
                COMMAND_SET_SHUFFLE_MODE,
                COMMAND_SET_REPEAT_MODE,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_MEDIA_ITEMS,
                COMMAND_GET_MEDIA_ITEMS_METADATA,
                COMMAND_CHANGE_MEDIA_ITEMS)
            .addAll(additionalPermanentAvailableCommands)
            .build();
    availableCommands =
        new Commands.Builder()
            .addAll(permanentAvailableCommands)
            .add(COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(COMMAND_SEEK_TO_MEDIA_ITEM)
            .build();
    mediaMetadata = MediaMetadata.EMPTY;
    maskingWindowIndex = C.INDEX_UNSET;
    playbackInfoUpdateHandler = clock.createHandler(applicationLooper, /* callback= */ null);
    playbackInfoUpdateListener =
        playbackInfoUpdate ->
            playbackInfoUpdateHandler.post(() -> handlePlaybackInfo(playbackInfoUpdate));
    playbackInfo = PlaybackInfo.createDummy(emptyTrackSelectorResult);
    if (analyticsCollector != null) {
      analyticsCollector.setPlayer(playerForListeners, applicationLooper);
      addListener(analyticsCollector);
      bandwidthMeter.addEventListener(new Handler(applicationLooper), analyticsCollector);
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
            seekParameters,
            livePlaybackSpeedControl,
            releaseTimeoutMs,
            pauseAtEndOfMediaItems,
            applicationLooper,
            clock,
            playbackInfoUpdateListener);
  }

  /**
   * Set a limit on the time a call to {@link #setForegroundMode} can spend. If a call to {@link
   * #setForegroundMode} takes more than {@code timeoutMs} milliseconds to complete, the player will
   * raise an error via {@link Player.Listener#onPlayerError}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release. It should
   * only be called before the player is used.
   *
   * @param timeoutMs The time limit in milliseconds.
   */
  public void experimentalSetForegroundModeTimeoutMs(long timeoutMs) {
    internalPlayer.experimentalSetForegroundModeTimeoutMs(timeoutMs);
  }

  @Override
  public void experimentalSetOffloadSchedulingEnabled(boolean offloadSchedulingEnabled) {
    internalPlayer.experimentalSetOffloadSchedulingEnabled(offloadSchedulingEnabled);
  }

  @Override
  public boolean experimentalIsSleepingForOffload() {
    return playbackInfo.sleepingForOffload;
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
    return applicationLooper;
  }

  @Override
  public Clock getClock() {
    return clock;
  }

  @Override
  public void addListener(Listener listener) {
    EventListener eventListener = listener;
    addListener(eventListener);
  }

  @Override
  public void addListener(Player.EventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    EventListener eventListener = listener;
    removeListener(eventListener);
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void addAudioOffloadListener(AudioOffloadListener listener) {
    audioOffloadListeners.add(listener);
  }

  @Override
  public void removeAudioOffloadListener(AudioOffloadListener listener) {
    audioOffloadListeners.remove(listener);
  }

  @Override
  public Commands getAvailableCommands() {
    return availableCommands;
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
    PlaybackInfo playbackInfo = this.playbackInfo.copyWithPlaybackError(null);
    playbackInfo =
        playbackInfo.copyWithPlaybackState(
            playbackInfo.timeline.isEmpty() ? Player.STATE_ENDED : Player.STATE_BUFFERING);
    // Trigger internal prepare first before updating the playback info and notifying external
    // listeners to ensure that new operations issued in the listener notifications reach the
    // player after this prepare. The internal player can't change the playback info immediately
    // because it uses a callback.
    pendingOperationAcks++;
    internalPlayer.prepare();
    updatePlaybackInfo(
        playbackInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET);
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
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    setMediaSources(createMediaSources(mediaItems), resetPosition);
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
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    index = min(index, mediaSourceHolderSnapshots.size());
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
    addMediaSources(/* index= */ mediaSourceHolderSnapshots.size(), mediaSources);
  }

  @Override
  public void addMediaSources(int index, List<MediaSource> mediaSources) {
    Assertions.checkArgument(index >= 0);
    Timeline oldTimeline = getCurrentTimeline();
    pendingOperationAcks++;
    List<MediaSourceList.MediaSourceHolder> holders = addMediaSourceHolders(index, mediaSources);
    Timeline newTimeline = createMaskingTimeline();
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            newTimeline,
            getPeriodPositionAfterTimelineChanged(oldTimeline, newTimeline));
    internalPlayer.addMediaSources(index, holders, shuffleOrder);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    toIndex = min(toIndex, mediaSourceHolderSnapshots.size());
    PlaybackInfo newPlaybackInfo = removeMediaItemsInternal(fromIndex, toIndex);
    boolean positionDiscontinuity =
        !newPlaybackInfo.periodId.periodUid.equals(playbackInfo.periodId.periodUid);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false,
        positionDiscontinuity,
        Player.DISCONTINUITY_REASON_REMOVE,
        /* discontinuityWindowStartPositionUs= */ getCurrentPositionUsInternal(newPlaybackInfo),
        /* ignored */ C.INDEX_UNSET);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newFromIndex) {
    Assertions.checkArgument(
        fromIndex >= 0
            && fromIndex <= toIndex
            && toIndex <= mediaSourceHolderSnapshots.size()
            && newFromIndex >= 0);
    Timeline oldTimeline = getCurrentTimeline();
    pendingOperationAcks++;
    newFromIndex = min(newFromIndex, mediaSourceHolderSnapshots.size() - (toIndex - fromIndex));
    Util.moveItems(mediaSourceHolderSnapshots, fromIndex, toIndex, newFromIndex);
    Timeline newTimeline = createMaskingTimeline();
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            newTimeline,
            getPeriodPositionAfterTimelineChanged(oldTimeline, newTimeline));
    internalPlayer.moveMediaSources(fromIndex, toIndex, newFromIndex, shuffleOrder);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET);
  }

  @Override
  public void setShuffleOrder(ShuffleOrder shuffleOrder) {
    Timeline timeline = createMaskingTimeline();
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            timeline,
            getPeriodPositionOrMaskWindowPosition(
                timeline, getCurrentWindowIndex(), getCurrentPosition()));
    pendingOperationAcks++;
    this.shuffleOrder = shuffleOrder;
    internalPlayer.setShuffleOrder(shuffleOrder);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET);
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
    pendingOperationAcks++;
    PlaybackInfo playbackInfo =
        this.playbackInfo.copyWithPlayWhenReady(playWhenReady, playbackSuppressionReason);
    internalPlayer.setPlayWhenReady(playWhenReady, playbackSuppressionReason);
    updatePlaybackInfo(
        playbackInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        playWhenReadyChangeReason,
        /* seekProcessed= */ false,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET);
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
      listeners.queueEvent(
          Player.EVENT_REPEAT_MODE_CHANGED, listener -> listener.onRepeatModeChanged(repeatMode));
      updateAvailableCommands();
      listeners.flushEvents();
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
      listeners.queueEvent(
          Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          listener -> listener.onShuffleModeEnabledChanged(shuffleModeEnabled));
      updateAvailableCommands();
      listeners.flushEvents();
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
      ExoPlayerImplInternal.PlaybackInfoUpdate playbackInfoUpdate =
          new ExoPlayerImplInternal.PlaybackInfoUpdate(this.playbackInfo);
      playbackInfoUpdate.incrementPendingOperationAcks(1);
      playbackInfoUpdateListener.onPlaybackInfoUpdate(playbackInfoUpdate);
      return;
    }
    @Player.State
    int newPlaybackState =
        getPlaybackState() == Player.STATE_IDLE ? Player.STATE_IDLE : Player.STATE_BUFFERING;
    int oldMaskingWindowIndex = getCurrentWindowIndex();
    PlaybackInfo newPlaybackInfo = playbackInfo.copyWithPlaybackState(newPlaybackState);
    newPlaybackInfo =
        maskTimelineAndPosition(
            newPlaybackInfo,
            timeline,
            getPeriodPositionOrMaskWindowPosition(timeline, windowIndex, positionMs));
    internalPlayer.seekTo(timeline, windowIndex, C.msToUs(positionMs));
    updatePlaybackInfo(
        newPlaybackInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ true,
        /* positionDiscontinuity= */ true,
        /* positionDiscontinuityReason= */ DISCONTINUITY_REASON_SEEK,
        /* discontinuityWindowStartPositionUs= */ getCurrentPositionUsInternal(newPlaybackInfo),
        oldMaskingWindowIndex);
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    if (playbackParameters == null) {
      playbackParameters = PlaybackParameters.DEFAULT;
    }
    if (playbackInfo.playbackParameters.equals(playbackParameters)) {
      return;
    }
    PlaybackInfo newPlaybackInfo = playbackInfo.copyWithPlaybackParameters(playbackParameters);
    pendingOperationAcks++;
    internalPlayer.setPlaybackParameters(playbackParameters);
    updatePlaybackInfo(
        newPlaybackInfo,
        /* ignored */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false,
        /* positionDiscontinuity= */ false,
        /* ignored */ DISCONTINUITY_REASON_INTERNAL,
        /* ignored */ C.TIME_UNSET,
        /* ignored */ C.INDEX_UNSET);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playbackInfo.playbackParameters;
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
      if (!internalPlayer.setForegroundMode(foregroundMode)) {
        // One of the renderers timed out releasing its resources.
        stop(
            /* reset= */ false,
            ExoPlaybackException.createForRenderer(
                new ExoTimeoutException(
                    ExoTimeoutException.TIMEOUT_OPERATION_SET_FOREGROUND_MODE)));
      }
    }
  }

  @Override
  public void stop(boolean reset) {
    stop(reset, /* error= */ null);
  }

  /**
   * Stops the player.
   *
   * @param reset Whether the playlist should be cleared and whether the playback position and
   *     playback error should be reset.
   * @param error An optional {@link ExoPlaybackException} to set.
   */
  public void stop(boolean reset, @Nullable ExoPlaybackException error) {
    PlaybackInfo playbackInfo;
    if (reset) {
      playbackInfo =
          removeMediaItemsInternal(
              /* fromIndex= */ 0, /* toIndex= */ mediaSourceHolderSnapshots.size());
      playbackInfo = playbackInfo.copyWithPlaybackError(null);
    } else {
      playbackInfo = this.playbackInfo.copyWithLoadingMediaPeriodId(this.playbackInfo.periodId);
      playbackInfo.bufferedPositionUs = playbackInfo.positionUs;
      playbackInfo.totalBufferedDurationUs = 0;
    }
    playbackInfo = playbackInfo.copyWithPlaybackState(Player.STATE_IDLE);
    if (error != null) {
      playbackInfo = playbackInfo.copyWithPlaybackError(error);
    }
    pendingOperationAcks++;
    internalPlayer.stop();
    boolean positionDiscontinuity =
        playbackInfo.timeline.isEmpty() && !this.playbackInfo.timeline.isEmpty();
    updatePlaybackInfo(
        playbackInfo,
        TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false,
        positionDiscontinuity,
        DISCONTINUITY_REASON_REMOVE,
        /* discontinuityWindowStartPositionUs= */ getCurrentPositionUsInternal(playbackInfo),
        /* ignored */ C.INDEX_UNSET);
  }

  @Override
  public void release() {
    Log.i(
        TAG,
        "Release "
            + Integer.toHexString(System.identityHashCode(this))
            + " ["
            + ExoPlayerLibraryInfo.VERSION_SLASHY
            + "] ["
            + Util.DEVICE_DEBUG_INFO
            + "] ["
            + ExoPlayerLibraryInfo.registeredModules()
            + "]");
    if (!internalPlayer.release()) {
      // One of the renderers timed out releasing its resources.
      listeners.sendEvent(
          Player.EVENT_PLAYER_ERROR,
          listener ->
              listener.onPlayerError(
                  ExoPlaybackException.createForRenderer(
                      new ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_RELEASE))));
    }
    listeners.release();
    playbackInfoUpdateHandler.removeCallbacksAndMessages(null);
    if (analyticsCollector != null) {
      bandwidthMeter.removeEventListener(analyticsCollector);
    }
    playbackInfo = playbackInfo.copyWithPlaybackState(Player.STATE_IDLE);
    playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(playbackInfo.periodId);
    playbackInfo.bufferedPositionUs = playbackInfo.positionUs;
    playbackInfo.totalBufferedDurationUs = 0;
  }

  @Override
  public PlayerMessage createMessage(Target target) {
    return new PlayerMessage(
        internalPlayer,
        target,
        playbackInfo.timeline,
        getCurrentWindowIndex(),
        clock,
        internalPlayer.getPlaybackLooper());
  }

  @Override
  public int getCurrentPeriodIndex() {
    if (playbackInfo.timeline.isEmpty()) {
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
    return C.usToMs(getCurrentPositionUsInternal(playbackInfo));
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
    return playbackInfo.periodId.isAd();
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
    if (playbackInfo.timeline.isEmpty()) {
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
    return C.usToMs(
        periodPositionUsToWindowPositionUs(
            playbackInfo.timeline, playbackInfo.loadingMediaPeriodId, contentBufferedPositionUs));
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
  @Nullable
  public TrackSelector getTrackSelector() {
    return trackSelector;
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return playbackInfo.trackGroups;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return new TrackSelectionArray(playbackInfo.trackSelectorResult.selections);
  }

  @Override
  public List<Metadata> getCurrentStaticMetadata() {
    return playbackInfo.staticMetadata;
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    return mediaMetadata;
  }

  public void onMetadata(Metadata metadata) {
    MediaMetadata newMediaMetadata =
        mediaMetadata.buildUpon().populateFromMetadata(metadata).build();
    if (newMediaMetadata.equals(mediaMetadata)) {
      return;
    }
    mediaMetadata = newMediaMetadata;
    listeners.sendEvent(
        EVENT_MEDIA_METADATA_CHANGED, listener -> listener.onMediaMetadataChanged(mediaMetadata));
  }

  @Override
  public Timeline getCurrentTimeline() {
    return playbackInfo.timeline;
  }

  /** This method is not supported and returns {@link AudioAttributes#DEFAULT}. */
  @Override
  public AudioAttributes getAudioAttributes() {
    return AudioAttributes.DEFAULT;
  }

  /** This method is not supported and does nothing. */
  @Override
  public void setVolume(float audioVolume) {}

  /** This method is not supported and returns 1. */
  @Override
  public float getVolume() {
    return 1;
  }

  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoSurface() {}

  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoSurface(@Nullable Surface surface) {}

  /** This method is not supported and does nothing. */
  @Override
  public void setVideoSurface(@Nullable Surface surface) {}

  /** This method is not supported and does nothing. */
  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {}

  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {}

  /** This method is not supported and does nothing. */
  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {}

  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {}

  /** This method is not supported and does nothing. */
  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {}

  /** This method is not supported and does nothing. */
  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {}

  /** This method is not supported and returns {@link VideoSize#UNKNOWN}. */
  @Override
  public VideoSize getVideoSize() {
    return VideoSize.UNKNOWN;
  }

  /** This method is not supported and returns an empty list. */
  @Override
  public ImmutableList<Cue> getCurrentCues() {
    return ImmutableList.of();
  }

  /** This method is not supported and always returns {@link DeviceInfo#UNKNOWN}. */
  @Override
  public DeviceInfo getDeviceInfo() {
    return DeviceInfo.UNKNOWN;
  }

  /** This method is not supported and always returns {@code 0}. */
  @Override
  public int getDeviceVolume() {
    return 0;
  }

  /** This method is not supported and always returns {@link false}. */
  @Override
  public boolean isDeviceMuted() {
    return false;
  }

  /** This method is not supported and does nothing. */
  @Override
  public void setDeviceVolume(int volume) {}

  /** This method is not supported and does nothing. */
  @Override
  public void increaseDeviceVolume() {}

  /** This method is not supported and does nothing. */
  @Override
  public void decreaseDeviceVolume() {}

  /** This method is not supported and does nothing. */
  @Override
  public void setDeviceMuted(boolean muted) {}

  private int getCurrentWindowIndexInternal() {
    if (playbackInfo.timeline.isEmpty()) {
      return maskingWindowIndex;
    } else {
      return playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period)
          .windowIndex;
    }
  }

  private long getCurrentPositionUsInternal(PlaybackInfo playbackInfo) {
    if (playbackInfo.timeline.isEmpty()) {
      return C.msToUs(maskingWindowPositionMs);
    } else if (playbackInfo.periodId.isAd()) {
      return playbackInfo.positionUs;
    } else {
      return periodPositionUsToWindowPositionUs(
          playbackInfo.timeline, playbackInfo.periodId, playbackInfo.positionUs);
    }
  }

  private List<MediaSource> createMediaSources(List<MediaItem> mediaItems) {
    List<MediaSource> mediaSources = new ArrayList<>();
    for (int i = 0; i < mediaItems.size(); i++) {
      mediaSources.add(mediaSourceFactory.createMediaSource(mediaItems.get(i)));
    }
    return mediaSources;
  }

  private void handlePlaybackInfo(ExoPlayerImplInternal.PlaybackInfoUpdate playbackInfoUpdate) {
    pendingOperationAcks -= playbackInfoUpdate.operationAcks;
    if (playbackInfoUpdate.positionDiscontinuity) {
      pendingDiscontinuityReason = playbackInfoUpdate.discontinuityReason;
      pendingDiscontinuity = true;
    }
    if (playbackInfoUpdate.hasPlayWhenReadyChangeReason) {
      pendingPlayWhenReadyChangeReason = playbackInfoUpdate.playWhenReadyChangeReason;
    }
    if (pendingOperationAcks == 0) {
      Timeline newTimeline = playbackInfoUpdate.playbackInfo.timeline;
      if (!this.playbackInfo.timeline.isEmpty() && newTimeline.isEmpty()) {
        // Update the masking variables, which are used when the timeline becomes empty because a
        // ConcatenatingMediaSource has been cleared.
        maskingWindowIndex = C.INDEX_UNSET;
        maskingWindowPositionMs = 0;
        maskingPeriodIndex = 0;
      }
      if (!newTimeline.isEmpty()) {
        List<Timeline> timelines = ((PlaylistTimeline) newTimeline).getChildTimelines();
        checkState(timelines.size() == mediaSourceHolderSnapshots.size());
        for (int i = 0; i < timelines.size(); i++) {
          mediaSourceHolderSnapshots.get(i).timeline = timelines.get(i);
        }
      }
      boolean positionDiscontinuity = false;
      long discontinuityWindowStartPositionUs = C.TIME_UNSET;
      if (pendingDiscontinuity) {
        positionDiscontinuity =
            !playbackInfoUpdate.playbackInfo.periodId.equals(playbackInfo.periodId)
                || playbackInfoUpdate.playbackInfo.discontinuityStartPositionUs
                    != playbackInfo.positionUs;
        if (positionDiscontinuity) {
          discontinuityWindowStartPositionUs =
              newTimeline.isEmpty() || playbackInfoUpdate.playbackInfo.periodId.isAd()
                  ? playbackInfoUpdate.playbackInfo.discontinuityStartPositionUs
                  : periodPositionUsToWindowPositionUs(
                      newTimeline,
                      playbackInfoUpdate.playbackInfo.periodId,
                      playbackInfoUpdate.playbackInfo.discontinuityStartPositionUs);
        }
      }
      pendingDiscontinuity = false;
      updatePlaybackInfo(
          playbackInfoUpdate.playbackInfo,
          TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
          pendingPlayWhenReadyChangeReason,
          /* seekProcessed= */ false,
          positionDiscontinuity,
          pendingDiscontinuityReason,
          discontinuityWindowStartPositionUs,
          /* ignored */ C.INDEX_UNSET);
    }
  }

  // Calling deprecated listeners.
  @SuppressWarnings("deprecation")
  private void updatePlaybackInfo(
      PlaybackInfo playbackInfo,
      @TimelineChangeReason int timelineChangeReason,
      @PlayWhenReadyChangeReason int playWhenReadyChangeReason,
      boolean seekProcessed,
      boolean positionDiscontinuity,
      @DiscontinuityReason int positionDiscontinuityReason,
      long discontinuityWindowStartPositionUs,
      int oldMaskingWindowIndex) {

    // Assign playback info immediately such that all getters return the right values, but keep
    // snapshot of previous and new state so that listener invocations are triggered correctly.
    PlaybackInfo previousPlaybackInfo = this.playbackInfo;
    PlaybackInfo newPlaybackInfo = playbackInfo;
    this.playbackInfo = playbackInfo;

    Pair<Boolean, Integer> mediaItemTransitionInfo =
        evaluateMediaItemTransitionReason(
            newPlaybackInfo,
            previousPlaybackInfo,
            positionDiscontinuity,
            positionDiscontinuityReason,
            !previousPlaybackInfo.timeline.equals(newPlaybackInfo.timeline));
    boolean mediaItemTransitioned = mediaItemTransitionInfo.first;
    int mediaItemTransitionReason = mediaItemTransitionInfo.second;
    MediaMetadata newMediaMetadata = mediaMetadata;
    @Nullable MediaItem mediaItem = null;
    if (mediaItemTransitioned) {
      if (!newPlaybackInfo.timeline.isEmpty()) {
        int windowIndex =
            newPlaybackInfo.timeline.getPeriodByUid(newPlaybackInfo.periodId.periodUid, period)
                .windowIndex;
        mediaItem = newPlaybackInfo.timeline.getWindow(windowIndex, window).mediaItem;
      }
      mediaMetadata = mediaItem != null ? mediaItem.mediaMetadata : MediaMetadata.EMPTY;
    }
    if (!previousPlaybackInfo.staticMetadata.equals(newPlaybackInfo.staticMetadata)) {
      newMediaMetadata =
          newMediaMetadata.buildUpon().populateFromMetadata(newPlaybackInfo.staticMetadata).build();
    }
    boolean metadataChanged = !newMediaMetadata.equals(mediaMetadata);
    mediaMetadata = newMediaMetadata;

    if (!previousPlaybackInfo.timeline.equals(newPlaybackInfo.timeline)) {
      listeners.queueEvent(
          Player.EVENT_TIMELINE_CHANGED,
          listener -> {
            @Nullable Object manifest = null;
            if (newPlaybackInfo.timeline.getWindowCount() == 1) {
              // Legacy behavior was to report the manifest for single window timelines only.
              Timeline.Window window = new Timeline.Window();
              manifest = newPlaybackInfo.timeline.getWindow(0, window).manifest;
            }
            listener.onTimelineChanged(newPlaybackInfo.timeline, manifest, timelineChangeReason);
            listener.onTimelineChanged(newPlaybackInfo.timeline, timelineChangeReason);
          });
    }
    if (positionDiscontinuity) {
      PositionInfo previousPositionInfo =
          getPreviousPositionInfo(
              positionDiscontinuityReason, previousPlaybackInfo, oldMaskingWindowIndex);
      PositionInfo positionInfo = getPositionInfo(discontinuityWindowStartPositionUs);
      listeners.queueEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          listener -> {
            listener.onPositionDiscontinuity(positionDiscontinuityReason);
            listener.onPositionDiscontinuity(
                previousPositionInfo, positionInfo, positionDiscontinuityReason);
          });
    }
    if (mediaItemTransitioned) {
      @Nullable final MediaItem finalMediaItem = mediaItem;
      listeners.queueEvent(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          listener -> listener.onMediaItemTransition(finalMediaItem, mediaItemTransitionReason));
    }
    if (previousPlaybackInfo.playbackError != newPlaybackInfo.playbackError
        && newPlaybackInfo.playbackError != null) {
      listeners.queueEvent(
          Player.EVENT_PLAYER_ERROR,
          listener -> listener.onPlayerError(newPlaybackInfo.playbackError));
    }
    if (previousPlaybackInfo.trackSelectorResult != newPlaybackInfo.trackSelectorResult) {
      trackSelector.onSelectionActivated(newPlaybackInfo.trackSelectorResult.info);
      TrackSelectionArray newSelection =
          new TrackSelectionArray(newPlaybackInfo.trackSelectorResult.selections);
      listeners.queueEvent(
          Player.EVENT_TRACKS_CHANGED,
          listener -> listener.onTracksChanged(newPlaybackInfo.trackGroups, newSelection));
    }
    if (!previousPlaybackInfo.staticMetadata.equals(newPlaybackInfo.staticMetadata)) {
      listeners.queueEvent(
          Player.EVENT_STATIC_METADATA_CHANGED,
          listener -> listener.onStaticMetadataChanged(newPlaybackInfo.staticMetadata));
    }
    if (metadataChanged) {
      final MediaMetadata finalMediaMetadata = mediaMetadata;
      listeners.queueEvent(
          Player.EVENT_MEDIA_METADATA_CHANGED,
          listener -> listener.onMediaMetadataChanged(finalMediaMetadata));
    }
    if (previousPlaybackInfo.isLoading != newPlaybackInfo.isLoading) {
      listeners.queueEvent(
          Player.EVENT_IS_LOADING_CHANGED,
          listener -> {
            listener.onLoadingChanged(newPlaybackInfo.isLoading);
            listener.onIsLoadingChanged(newPlaybackInfo.isLoading);
          });
    }
    if (previousPlaybackInfo.playbackState != newPlaybackInfo.playbackState
        || previousPlaybackInfo.playWhenReady != newPlaybackInfo.playWhenReady) {
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener ->
              listener.onPlayerStateChanged(
                  newPlaybackInfo.playWhenReady, newPlaybackInfo.playbackState));
    }
    if (previousPlaybackInfo.playbackState != newPlaybackInfo.playbackState) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          listener -> listener.onPlaybackStateChanged(newPlaybackInfo.playbackState));
    }
    if (previousPlaybackInfo.playWhenReady != newPlaybackInfo.playWhenReady) {
      listeners.queueEvent(
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          listener ->
              listener.onPlayWhenReadyChanged(
                  newPlaybackInfo.playWhenReady, playWhenReadyChangeReason));
    }
    if (previousPlaybackInfo.playbackSuppressionReason
        != newPlaybackInfo.playbackSuppressionReason) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
          listener ->
              listener.onPlaybackSuppressionReasonChanged(
                  newPlaybackInfo.playbackSuppressionReason));
    }
    if (isPlaying(previousPlaybackInfo) != isPlaying(newPlaybackInfo)) {
      listeners.queueEvent(
          Player.EVENT_IS_PLAYING_CHANGED,
          listener -> listener.onIsPlayingChanged(isPlaying(newPlaybackInfo)));
    }
    if (!previousPlaybackInfo.playbackParameters.equals(newPlaybackInfo.playbackParameters)) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
          listener -> listener.onPlaybackParametersChanged(newPlaybackInfo.playbackParameters));
    }
    if (seekProcessed) {
      listeners.queueEvent(/* eventFlag= */ C.INDEX_UNSET, EventListener::onSeekProcessed);
    }
    updateAvailableCommands();
    listeners.flushEvents();

    if (previousPlaybackInfo.offloadSchedulingEnabled != newPlaybackInfo.offloadSchedulingEnabled) {
      for (AudioOffloadListener listener : audioOffloadListeners) {
        listener.onExperimentalOffloadSchedulingEnabledChanged(
            newPlaybackInfo.offloadSchedulingEnabled);
      }
    }
    if (previousPlaybackInfo.sleepingForOffload != newPlaybackInfo.sleepingForOffload) {
      for (AudioOffloadListener listener : audioOffloadListeners) {
        listener.onExperimentalSleepingForOffloadChanged(newPlaybackInfo.sleepingForOffload);
      }
    }
  }

  private PositionInfo getPreviousPositionInfo(
      @DiscontinuityReason int positionDiscontinuityReason,
      PlaybackInfo oldPlaybackInfo,
      int oldMaskingWindowIndex) {
    @Nullable Object oldWindowUid = null;
    @Nullable Object oldPeriodUid = null;
    int oldWindowIndex = oldMaskingWindowIndex;
    int oldPeriodIndex = C.INDEX_UNSET;
    Timeline.Period oldPeriod = new Timeline.Period();
    if (!oldPlaybackInfo.timeline.isEmpty()) {
      oldPeriodUid = oldPlaybackInfo.periodId.periodUid;
      oldPlaybackInfo.timeline.getPeriodByUid(oldPeriodUid, oldPeriod);
      oldWindowIndex = oldPeriod.windowIndex;
      oldPeriodIndex = oldPlaybackInfo.timeline.getIndexOfPeriod(oldPeriodUid);
      oldWindowUid = oldPlaybackInfo.timeline.getWindow(oldWindowIndex, window).uid;
    }
    long oldPositionUs;
    long oldContentPositionUs;
    if (positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
      oldPositionUs = oldPeriod.positionInWindowUs + oldPeriod.durationUs;
      oldContentPositionUs = oldPositionUs;
      if (oldPlaybackInfo.periodId.isAd()) {
        // The old position is the end of the previous ad.
        oldPositionUs =
            oldPeriod.getAdDurationUs(
                oldPlaybackInfo.periodId.adGroupIndex, oldPlaybackInfo.periodId.adIndexInAdGroup);
        // The ad cue point is stored in the old requested content position.
        oldContentPositionUs = getRequestedContentPositionUs(oldPlaybackInfo);
      } else if (oldPlaybackInfo.periodId.nextAdGroupIndex != C.INDEX_UNSET
          && playbackInfo.periodId.isAd()) {
        // If it's a transition from content to an ad in the same window, the old position is the
        // ad cue point that is the same as current content position.
        oldPositionUs = getRequestedContentPositionUs(playbackInfo);
        oldContentPositionUs = oldPositionUs;
      }
    } else if (oldPlaybackInfo.periodId.isAd()) {
      oldPositionUs = oldPlaybackInfo.positionUs;
      oldContentPositionUs = getRequestedContentPositionUs(oldPlaybackInfo);
    } else {
      oldPositionUs = oldPeriod.positionInWindowUs + oldPlaybackInfo.positionUs;
      oldContentPositionUs = oldPositionUs;
    }
    return new PositionInfo(
        oldWindowUid,
        oldWindowIndex,
        oldPeriodUid,
        oldPeriodIndex,
        C.usToMs(oldPositionUs),
        C.usToMs(oldContentPositionUs),
        oldPlaybackInfo.periodId.adGroupIndex,
        oldPlaybackInfo.periodId.adIndexInAdGroup);
  }

  private PositionInfo getPositionInfo(long discontinuityWindowStartPositionUs) {
    @Nullable Object newWindowUid = null;
    @Nullable Object newPeriodUid = null;
    int newWindowIndex = getCurrentWindowIndex();
    int newPeriodIndex = C.INDEX_UNSET;
    if (!playbackInfo.timeline.isEmpty()) {
      newPeriodUid = playbackInfo.periodId.periodUid;
      playbackInfo.timeline.getPeriodByUid(newPeriodUid, period);
      newPeriodIndex = playbackInfo.timeline.getIndexOfPeriod(newPeriodUid);
      newWindowUid = playbackInfo.timeline.getWindow(newWindowIndex, window).uid;
    }
    long positionMs = C.usToMs(discontinuityWindowStartPositionUs);
    return new PositionInfo(
        newWindowUid,
        newWindowIndex,
        newPeriodUid,
        newPeriodIndex,
        positionMs,
        /* contentPositionMs= */ playbackInfo.periodId.isAd()
            ? C.usToMs(getRequestedContentPositionUs(playbackInfo))
            : positionMs,
        playbackInfo.periodId.adGroupIndex,
        playbackInfo.periodId.adIndexInAdGroup);
  }

  private static long getRequestedContentPositionUs(PlaybackInfo playbackInfo) {
    Timeline.Window window = new Timeline.Window();
    Timeline.Period period = new Timeline.Period();
    playbackInfo.timeline.getPeriodByUid(playbackInfo.periodId.periodUid, period);
    return playbackInfo.requestedContentPositionUs == C.TIME_UNSET
        ? playbackInfo.timeline.getWindow(period.windowIndex, window).getDefaultPositionUs()
        : period.getPositionInWindowUs() + playbackInfo.requestedContentPositionUs;
  }

  private Pair<Boolean, Integer> evaluateMediaItemTransitionReason(
      PlaybackInfo playbackInfo,
      PlaybackInfo oldPlaybackInfo,
      boolean positionDiscontinuity,
      @DiscontinuityReason int positionDiscontinuityReason,
      boolean timelineChanged) {

    Timeline oldTimeline = oldPlaybackInfo.timeline;
    Timeline newTimeline = playbackInfo.timeline;
    if (newTimeline.isEmpty() && oldTimeline.isEmpty()) {
      return new Pair<>(/* isTransitioning */ false, /* mediaItemTransitionReason */ C.INDEX_UNSET);
    } else if (newTimeline.isEmpty() != oldTimeline.isEmpty()) {
      return new Pair<>(/* isTransitioning */ true, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    }

    int oldWindowIndex =
        oldTimeline.getPeriodByUid(oldPlaybackInfo.periodId.periodUid, period).windowIndex;
    Object oldWindowUid = oldTimeline.getWindow(oldWindowIndex, window).uid;
    int newWindowIndex =
        newTimeline.getPeriodByUid(playbackInfo.periodId.periodUid, period).windowIndex;
    Object newWindowUid = newTimeline.getWindow(newWindowIndex, window).uid;
    if (!oldWindowUid.equals(newWindowUid)) {
      @Player.MediaItemTransitionReason int transitionReason;
      if (positionDiscontinuity
          && positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
        transitionReason = MEDIA_ITEM_TRANSITION_REASON_AUTO;
      } else if (positionDiscontinuity
          && positionDiscontinuityReason == DISCONTINUITY_REASON_SEEK) {
        transitionReason = MEDIA_ITEM_TRANSITION_REASON_SEEK;
      } else if (timelineChanged) {
        transitionReason = MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
      } else {
        // A change in window uid must be justified by one of the reasons above.
        throw new IllegalStateException();
      }
      return new Pair<>(/* isTransitioning */ true, transitionReason);
    } else if (positionDiscontinuity
        && positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION
        && oldPlaybackInfo.periodId.windowSequenceNumber
            < playbackInfo.periodId.windowSequenceNumber) {
      return new Pair<>(/* isTransitioning */ true, MEDIA_ITEM_TRANSITION_REASON_REPEAT);
    }
    return new Pair<>(/* isTransitioning */ false, /* mediaItemTransitionReason */ C.INDEX_UNSET);
  }

  private void updateAvailableCommands() {
    Commands previousAvailableCommands = availableCommands;
    availableCommands = getAvailableCommands(permanentAvailableCommands);
    if (!availableCommands.equals(previousAvailableCommands)) {
      listeners.queueEvent(
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
          listener -> listener.onAvailableCommandsChanged(availableCommands));
    }
  }

  private void setMediaSourcesInternal(
      List<MediaSource> mediaSources,
      int startWindowIndex,
      long startPositionMs,
      boolean resetToDefaultPosition) {
    int currentWindowIndex = getCurrentWindowIndexInternal();
    long currentPositionMs = getCurrentPosition();
    pendingOperationAcks++;
    if (!mediaSourceHolderSnapshots.isEmpty()) {
      removeMediaSourceHolders(
          /* fromIndex= */ 0, /* toIndexExclusive= */ mediaSourceHolderSnapshots.size());
    }
    List<MediaSourceList.MediaSourceHolder> holders =
        addMediaSourceHolders(/* index= */ 0, mediaSources);
    Timeline timeline = createMaskingTimeline();
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
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            timeline,
            getPeriodPositionOrMaskWindowPosition(timeline, startWindowIndex, startPositionMs));
    // Mask the playback state.
    int maskingPlaybackState = newPlaybackInfo.playbackState;
    if (startWindowIndex != C.INDEX_UNSET && newPlaybackInfo.playbackState != STATE_IDLE) {
      // Position reset to startWindowIndex (results in pending initial seek).
      if (timeline.isEmpty() || startWindowIndex >= timeline.getWindowCount()) {
        // Setting an empty timeline or invalid seek transitions to ended.
        maskingPlaybackState = STATE_ENDED;
      } else {
        maskingPlaybackState = STATE_BUFFERING;
      }
    }
    newPlaybackInfo = newPlaybackInfo.copyWithPlaybackState(maskingPlaybackState);
    internalPlayer.setMediaSources(
        holders, startWindowIndex, C.msToUs(startPositionMs), shuffleOrder);
    boolean positionDiscontinuity =
        !playbackInfo.periodId.periodUid.equals(newPlaybackInfo.periodId.periodUid)
            && !playbackInfo.timeline.isEmpty();
    updatePlaybackInfo(
        newPlaybackInfo,
        /* timelineChangeReason= */ TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        /* ignored */ PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        /* seekProcessed= */ false,
        /* positionDiscontinuity= */ positionDiscontinuity,
        Player.DISCONTINUITY_REASON_REMOVE,
        /* discontinuityWindowStartPositionUs= */ getCurrentPositionUsInternal(newPlaybackInfo),
        /* ignored */ C.INDEX_UNSET);
  }

  private List<MediaSourceList.MediaSourceHolder> addMediaSourceHolders(
      int index, List<MediaSource> mediaSources) {
    List<MediaSourceList.MediaSourceHolder> holders = new ArrayList<>();
    for (int i = 0; i < mediaSources.size(); i++) {
      MediaSourceList.MediaSourceHolder holder =
          new MediaSourceList.MediaSourceHolder(mediaSources.get(i), useLazyPreparation);
      holders.add(holder);
      mediaSourceHolderSnapshots.add(
          i + index, new MediaSourceHolderSnapshot(holder.uid, holder.mediaSource.getTimeline()));
    }
    shuffleOrder =
        shuffleOrder.cloneAndInsert(
            /* insertionIndex= */ index, /* insertionCount= */ holders.size());
    return holders;
  }

  private PlaybackInfo removeMediaItemsInternal(int fromIndex, int toIndex) {
    Assertions.checkArgument(
        fromIndex >= 0 && toIndex >= fromIndex && toIndex <= mediaSourceHolderSnapshots.size());
    int currentWindowIndex = getCurrentWindowIndex();
    Timeline oldTimeline = getCurrentTimeline();
    int currentMediaSourceCount = mediaSourceHolderSnapshots.size();
    pendingOperationAcks++;
    removeMediaSourceHolders(fromIndex, /* toIndexExclusive= */ toIndex);
    Timeline newTimeline = createMaskingTimeline();
    PlaybackInfo newPlaybackInfo =
        maskTimelineAndPosition(
            playbackInfo,
            newTimeline,
            getPeriodPositionAfterTimelineChanged(oldTimeline, newTimeline));
    // Player transitions to STATE_ENDED if the current index is part of the removed tail.
    final boolean transitionsToEnded =
        newPlaybackInfo.playbackState != STATE_IDLE
            && newPlaybackInfo.playbackState != STATE_ENDED
            && fromIndex < toIndex
            && toIndex == currentMediaSourceCount
            && currentWindowIndex >= newPlaybackInfo.timeline.getWindowCount();
    if (transitionsToEnded) {
      newPlaybackInfo = newPlaybackInfo.copyWithPlaybackState(STATE_ENDED);
    }
    internalPlayer.removeMediaSources(fromIndex, toIndex, shuffleOrder);
    return newPlaybackInfo;
  }

  private void removeMediaSourceHolders(int fromIndex, int toIndexExclusive) {
    for (int i = toIndexExclusive - 1; i >= fromIndex; i--) {
      mediaSourceHolderSnapshots.remove(i);
    }
    shuffleOrder = shuffleOrder.cloneAndRemove(fromIndex, toIndexExclusive);
  }

  private Timeline createMaskingTimeline() {
    return new PlaylistTimeline(mediaSourceHolderSnapshots, shuffleOrder);
  }

  private PlaybackInfo maskTimelineAndPosition(
      PlaybackInfo playbackInfo, Timeline timeline, @Nullable Pair<Object, Long> periodPosition) {
    Assertions.checkArgument(timeline.isEmpty() || periodPosition != null);
    Timeline oldTimeline = playbackInfo.timeline;
    // Mask the timeline.
    playbackInfo = playbackInfo.copyWithTimeline(timeline);

    if (timeline.isEmpty()) {
      // Reset periodId and loadingPeriodId.
      MediaPeriodId dummyMediaPeriodId = PlaybackInfo.getDummyPeriodForEmptyTimeline();
      long positionUs = C.msToUs(maskingWindowPositionMs);
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              dummyMediaPeriodId,
              positionUs,
              /* requestedContentPositionUs= */ positionUs,
              /* discontinuityStartPositionUs= */ positionUs,
              /* totalBufferedDurationUs= */ 0,
              TrackGroupArray.EMPTY,
              emptyTrackSelectorResult,
              /* staticMetadata= */ ImmutableList.of());
      playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(dummyMediaPeriodId);
      playbackInfo.bufferedPositionUs = playbackInfo.positionUs;
      return playbackInfo;
    }

    Object oldPeriodUid = playbackInfo.periodId.periodUid;
    boolean playingPeriodChanged = !oldPeriodUid.equals(castNonNull(periodPosition).first);
    MediaPeriodId newPeriodId =
        playingPeriodChanged ? new MediaPeriodId(periodPosition.first) : playbackInfo.periodId;
    long newContentPositionUs = periodPosition.second;
    long oldContentPositionUs = C.msToUs(getContentPosition());
    if (!oldTimeline.isEmpty()) {
      oldContentPositionUs -=
          oldTimeline.getPeriodByUid(oldPeriodUid, period).getPositionInWindowUs();
    }

    if (playingPeriodChanged || newContentPositionUs < oldContentPositionUs) {
      checkState(!newPeriodId.isAd());
      // The playing period changes or a backwards seek within the playing period occurs.
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              newPeriodId,
              /* positionUs= */ newContentPositionUs,
              /* requestedContentPositionUs= */ newContentPositionUs,
              /* discontinuityStartPositionUs= */ newContentPositionUs,
              /* totalBufferedDurationUs= */ 0,
              playingPeriodChanged ? TrackGroupArray.EMPTY : playbackInfo.trackGroups,
              playingPeriodChanged ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult,
              playingPeriodChanged ? ImmutableList.of() : playbackInfo.staticMetadata);
      playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(newPeriodId);
      playbackInfo.bufferedPositionUs = newContentPositionUs;
    } else if (newContentPositionUs == oldContentPositionUs) {
      // Period position remains unchanged.
      int loadingPeriodIndex =
          timeline.getIndexOfPeriod(playbackInfo.loadingMediaPeriodId.periodUid);
      if (loadingPeriodIndex == C.INDEX_UNSET
          || timeline.getPeriod(loadingPeriodIndex, period).windowIndex
              != timeline.getPeriodByUid(newPeriodId.periodUid, period).windowIndex) {
        // Discard periods after the playing period, if the loading period is discarded or the
        // playing and loading period are not in the same window.
        timeline.getPeriodByUid(newPeriodId.periodUid, period);
        long maskedBufferedPositionUs =
            newPeriodId.isAd()
                ? period.getAdDurationUs(newPeriodId.adGroupIndex, newPeriodId.adIndexInAdGroup)
                : period.durationUs;
        playbackInfo =
            playbackInfo.copyWithNewPosition(
                newPeriodId,
                /* positionUs= */ playbackInfo.positionUs,
                /* requestedContentPositionUs= */ playbackInfo.positionUs,
                playbackInfo.discontinuityStartPositionUs,
                /* totalBufferedDurationUs= */ maskedBufferedPositionUs - playbackInfo.positionUs,
                playbackInfo.trackGroups,
                playbackInfo.trackSelectorResult,
                playbackInfo.staticMetadata);
        playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(newPeriodId);
        playbackInfo.bufferedPositionUs = maskedBufferedPositionUs;
      }
    } else {
      checkState(!newPeriodId.isAd());
      // A forward seek within the playing period (timeline did not change).
      long maskedTotalBufferedDurationUs =
          max(
              0,
              playbackInfo.totalBufferedDurationUs - (newContentPositionUs - oldContentPositionUs));
      long maskedBufferedPositionUs = playbackInfo.bufferedPositionUs;
      if (playbackInfo.loadingMediaPeriodId.equals(playbackInfo.periodId)) {
        maskedBufferedPositionUs = newContentPositionUs + maskedTotalBufferedDurationUs;
      }
      playbackInfo =
          playbackInfo.copyWithNewPosition(
              newPeriodId,
              /* positionUs= */ newContentPositionUs,
              /* requestedContentPositionUs= */ newContentPositionUs,
              /* discontinuityStartPositionUs= */ newContentPositionUs,
              maskedTotalBufferedDurationUs,
              playbackInfo.trackGroups,
              playbackInfo.trackSelectorResult,
              playbackInfo.staticMetadata);
      playbackInfo.bufferedPositionUs = maskedBufferedPositionUs;
    }
    return playbackInfo;
  }

  @Nullable
  private Pair<Object, Long> getPeriodPositionAfterTimelineChanged(
      Timeline oldTimeline, Timeline newTimeline) {
    long currentPositionMs = getContentPosition();
    if (oldTimeline.isEmpty() || newTimeline.isEmpty()) {
      boolean isCleared = !oldTimeline.isEmpty() && newTimeline.isEmpty();
      return getPeriodPositionOrMaskWindowPosition(
          newTimeline,
          isCleared ? C.INDEX_UNSET : getCurrentWindowIndexInternal(),
          isCleared ? C.TIME_UNSET : currentPositionMs);
    }
    int currentWindowIndex = getCurrentWindowIndex();
    @Nullable
    Pair<Object, Long> oldPeriodPosition =
        oldTimeline.getPeriodPosition(
            window, period, currentWindowIndex, C.msToUs(currentPositionMs));
    Object periodUid = castNonNull(oldPeriodPosition).first;
    if (newTimeline.getIndexOfPeriod(periodUid) != C.INDEX_UNSET) {
      // The old period position is still available in the new timeline.
      return oldPeriodPosition;
    }
    // Period uid not found in new timeline. Try to get subsequent period.
    @Nullable
    Object nextPeriodUid =
        ExoPlayerImplInternal.resolveSubsequentPeriod(
            window, period, repeatMode, shuffleModeEnabled, periodUid, oldTimeline, newTimeline);
    if (nextPeriodUid != null) {
      // Reset position to the default position of the window of the subsequent period.
      newTimeline.getPeriodByUid(nextPeriodUid, period);
      return getPeriodPositionOrMaskWindowPosition(
          newTimeline,
          period.windowIndex,
          newTimeline.getWindow(period.windowIndex, window).getDefaultPositionMs());
    } else {
      // No subsequent period found and the new timeline is not empty. Use the default position.
      return getPeriodPositionOrMaskWindowPosition(
          newTimeline, /* windowIndex= */ C.INDEX_UNSET, /* windowPositionMs= */ C.TIME_UNSET);
    }
  }

  @Nullable
  private Pair<Object, Long> getPeriodPositionOrMaskWindowPosition(
      Timeline timeline, int windowIndex, long windowPositionMs) {
    if (timeline.isEmpty()) {
      // If empty we store the initial seek in the masking variables.
      maskingWindowIndex = windowIndex;
      maskingWindowPositionMs = windowPositionMs == C.TIME_UNSET ? 0 : windowPositionMs;
      maskingPeriodIndex = 0;
      return null;
    }
    if (windowIndex == C.INDEX_UNSET || windowIndex >= timeline.getWindowCount()) {
      // Use default position of timeline if window index still unset or if a previous initial seek
      // now turns out to be invalid.
      windowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
      windowPositionMs = timeline.getWindow(windowIndex, window).getDefaultPositionMs();
    }
    return timeline.getPeriodPosition(window, period, windowIndex, C.msToUs(windowPositionMs));
  }

  private long periodPositionUsToWindowPositionUs(
      Timeline timeline, MediaPeriodId periodId, long positionUs) {
    timeline.getPeriodByUid(periodId.periodUid, period);
    positionUs += period.getPositionInWindowUs();
    return positionUs;
  }

  private static boolean isPlaying(PlaybackInfo playbackInfo) {
    return playbackInfo.playbackState == Player.STATE_READY
        && playbackInfo.playWhenReady
        && playbackInfo.playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  private static final class MediaSourceHolderSnapshot implements MediaSourceInfoHolder {

    private final Object uid;

    private Timeline timeline;

    public MediaSourceHolderSnapshot(Object uid, Timeline timeline) {
      this.uid = uid;
      this.timeline = timeline;
    }

    @Override
    public Object getUid() {
      return uid;
    }

    @Override
    public Timeline getTimeline() {
      return timeline;
    }
  }
}
