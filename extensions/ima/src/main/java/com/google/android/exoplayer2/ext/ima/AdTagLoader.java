/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.ima;

import static com.google.android.exoplayer2.Player.COMMAND_GET_VOLUME;
import static com.google.android.exoplayer2.ext.ima.ImaUtil.BITRATE_UNSET;
import static com.google.android.exoplayer2.ext.ima.ImaUtil.TIMEOUT_UNSET;
import static com.google.android.exoplayer2.ext.ima.ImaUtil.getAdGroupTimesUsForCuePoints;
import static com.google.android.exoplayer2.ext.ima.ImaUtil.getImaLooper;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.view.ViewGroup;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdsLoader.EventListener;
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionUtil;
import com.google.android.exoplayer2.ui.AdOverlayInfo;
import com.google.android.exoplayer2.ui.AdViewProvider;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Handles loading and playback of a single ad tag. */
/* package */ final class AdTagLoader implements Player.Listener {

  private static final String TAG = "AdTagLoader";

  private static final String IMA_SDK_SETTINGS_PLAYER_TYPE = "google/exo.ext.ima";
  private static final String IMA_SDK_SETTINGS_PLAYER_VERSION = ExoPlayerLibraryInfo.VERSION;

  /**
   * Interval at which ad progress updates are provided to the IMA SDK, in milliseconds. 100 ms is
   * the interval recommended by the IMA documentation.
   *
   * @see VideoAdPlayer.VideoAdPlayerCallback
   */
  private static final int AD_PROGRESS_UPDATE_INTERVAL_MS = 100;

  /** The value used in {@link VideoProgressUpdate}s to indicate an unset duration. */
  private static final long IMA_DURATION_UNSET = -1L;

  /**
   * Threshold before the end of content at which IMA is notified that content is complete if the
   * player buffers, in milliseconds.
   */
  private static final long THRESHOLD_END_OF_CONTENT_MS = 5000;
  /**
   * Threshold before the start of an ad at which IMA is expected to be able to preload the ad, in
   * milliseconds.
   */
  private static final long THRESHOLD_AD_PRELOAD_MS = 4000;
  /** The threshold below which ad cue points are treated as matching, in microseconds. */
  private static final long THRESHOLD_AD_MATCH_US = 1000;

  /** The state of ad playback. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({IMA_AD_STATE_NONE, IMA_AD_STATE_PLAYING, IMA_AD_STATE_PAUSED})
  private @interface ImaAdState {}

  /** The ad playback state when IMA is not playing an ad. */
  private static final int IMA_AD_STATE_NONE = 0;
  /**
   * The ad playback state when IMA has called {@link ComponentListener#playAd(AdMediaInfo)} and not
   * {@link ComponentListener##pauseAd(AdMediaInfo)}.
   */
  private static final int IMA_AD_STATE_PLAYING = 1;
  /**
   * The ad playback state when IMA has called {@link ComponentListener#pauseAd(AdMediaInfo)} while
   * playing an ad.
   */
  private static final int IMA_AD_STATE_PAUSED = 2;

  private final ImaUtil.Configuration configuration;
  private final ImaUtil.ImaFactory imaFactory;
  private final List<String> supportedMimeTypes;
  private final DataSpec adTagDataSpec;
  private final Object adsId;
  private final Timeline.Period period;
  private final Handler handler;
  private final ComponentListener componentListener;
  private final List<EventListener> eventListeners;
  private final List<VideoAdPlayer.VideoAdPlayerCallback> adCallbacks;
  private final Runnable updateAdProgressRunnable;
  private final BiMap<AdMediaInfo, AdInfo> adInfoByAdMediaInfo;
  private final AdDisplayContainer adDisplayContainer;
  private final AdsLoader adsLoader;

  @Nullable private Object pendingAdRequestContext;
  @Nullable private Player player;
  private VideoProgressUpdate lastContentProgress;
  private VideoProgressUpdate lastAdProgress;
  private int lastVolumePercent;

  @Nullable private AdsManager adsManager;
  private boolean isAdsManagerInitialized;
  @Nullable private AdLoadException pendingAdLoadError;
  private Timeline timeline;
  private long contentDurationMs;
  private AdPlaybackState adPlaybackState;

  private boolean released;

  // Fields tracking IMA's state.

  /** Whether IMA has sent an ad event to pause content since the last resume content event. */
  private boolean imaPausedContent;
  /** The current ad playback state. */
  private @ImaAdState int imaAdState;
  /** The current ad media info, or {@code null} if in state {@link #IMA_AD_STATE_NONE}. */
  @Nullable private AdMediaInfo imaAdMediaInfo;
  /** The current ad info, or {@code null} if in state {@link #IMA_AD_STATE_NONE}. */
  @Nullable private AdInfo imaAdInfo;
  /** Whether IMA has been notified that playback of content has finished. */
  private boolean sentContentComplete;

  // Fields tracking the player/loader state.

  /** Whether the player is playing an ad. */
  private boolean playingAd;
  /** Whether the player is buffering an ad. */
  private boolean bufferingAd;
  /**
   * If the player is playing an ad, stores the ad index in its ad group. {@link C#INDEX_UNSET}
   * otherwise.
   */
  private int playingAdIndexInAdGroup;
  /**
   * The ad info for a pending ad for which the media failed preparation, or {@code null} if no
   * pending ads have failed to prepare.
   */
  @Nullable private AdInfo pendingAdPrepareErrorAdInfo;
  /**
   * If a content period has finished but IMA has not yet called {@link
   * ComponentListener#playAd(AdMediaInfo)}, stores the value of {@link
   * SystemClock#elapsedRealtime()} when the content stopped playing. This can be used to determine
   * a fake, increasing content position. {@link C#TIME_UNSET} otherwise.
   */
  private long fakeContentProgressElapsedRealtimeMs;
  /**
   * If {@link #fakeContentProgressElapsedRealtimeMs} is set, stores the offset from which the
   * content progress should increase. {@link C#TIME_UNSET} otherwise.
   */
  private long fakeContentProgressOffsetMs;
  /** Stores the pending content position when a seek operation was intercepted to play an ad. */
  private long pendingContentPositionMs;
  /**
   * Whether {@link ComponentListener#getContentProgress()} has sent {@link
   * #pendingContentPositionMs} to IMA.
   */
  private boolean sentPendingContentPositionMs;
  /**
   * Stores the real time in milliseconds at which the player started buffering, possibly due to not
   * having preloaded an ad, or {@link C#TIME_UNSET} if not applicable.
   */
  private long waitingForPreloadElapsedRealtimeMs;

  /** Creates a new ad tag loader, starting the ad request if the ad tag is valid. */
  @SuppressWarnings({"methodref.receiver.bound.invalid", "method.invocation.invalid"})
  public AdTagLoader(
      Context context,
      ImaUtil.Configuration configuration,
      ImaUtil.ImaFactory imaFactory,
      List<String> supportedMimeTypes,
      DataSpec adTagDataSpec,
      Object adsId,
      @Nullable ViewGroup adViewGroup) {
    this.configuration = configuration;
    this.imaFactory = imaFactory;
    @Nullable ImaSdkSettings imaSdkSettings = configuration.imaSdkSettings;
    if (imaSdkSettings == null) {
      imaSdkSettings = imaFactory.createImaSdkSettings();
      if (configuration.debugModeEnabled) {
        imaSdkSettings.setDebugMode(true);
      }
    }
    imaSdkSettings.setPlayerType(IMA_SDK_SETTINGS_PLAYER_TYPE);
    imaSdkSettings.setPlayerVersion(IMA_SDK_SETTINGS_PLAYER_VERSION);
    this.supportedMimeTypes = supportedMimeTypes;
    this.adTagDataSpec = adTagDataSpec;
    this.adsId = adsId;
    period = new Timeline.Period();
    handler = Util.createHandler(getImaLooper(), /* callback= */ null);
    componentListener = new ComponentListener();
    eventListeners = new ArrayList<>();
    adCallbacks = new ArrayList<>(/* initialCapacity= */ 1);
    if (configuration.applicationVideoAdPlayerCallback != null) {
      adCallbacks.add(configuration.applicationVideoAdPlayerCallback);
    }
    updateAdProgressRunnable = this::updateAdProgress;
    adInfoByAdMediaInfo = HashBiMap.create();
    lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
    fakeContentProgressOffsetMs = C.TIME_UNSET;
    pendingContentPositionMs = C.TIME_UNSET;
    waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET;
    contentDurationMs = C.TIME_UNSET;
    timeline = Timeline.EMPTY;
    adPlaybackState = AdPlaybackState.NONE;
    if (adViewGroup != null) {
      adDisplayContainer =
          imaFactory.createAdDisplayContainer(adViewGroup, /* player= */ componentListener);
    } else {
      adDisplayContainer =
          imaFactory.createAudioAdDisplayContainer(context, /* player= */ componentListener);
    }
    if (configuration.companionAdSlots != null) {
      adDisplayContainer.setCompanionSlots(configuration.companionAdSlots);
    }
    adsLoader = requestAds(context, imaSdkSettings, adDisplayContainer);
  }

  /** Returns the underlying IMA SDK ads loader. */
  public AdsLoader getAdsLoader() {
    return adsLoader;
  }

  /** Returns the IMA SDK ad display container. */
  public AdDisplayContainer getAdDisplayContainer() {
    return adDisplayContainer;
  }

  /** Skips the current skippable ad, if there is one. */
  public void skipAd() {
    if (adsManager != null) {
      adsManager.skip();
    }
  }

  /**
   * Moves UI focus to the skip button (or other interactive elements), if currently shown. See
   * {@link AdsManager#focus()}.
   */
  public void focusSkipButton() {
    if (adsManager != null) {
      adsManager.focus();
    }
  }

  /**
   * Starts passing events from this instance (including any pending ad playback state) and
   * registers obstructions.
   */
  public void addListenerWithAdView(EventListener eventListener, AdViewProvider adViewProvider) {
    boolean isStarted = !eventListeners.isEmpty();
    eventListeners.add(eventListener);
    if (isStarted) {
      if (!AdPlaybackState.NONE.equals(adPlaybackState)) {
        // Pass the existing ad playback state to the new listener.
        eventListener.onAdPlaybackState(adPlaybackState);
      }
      return;
    }
    lastVolumePercent = 0;
    lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    maybeNotifyPendingAdLoadError();
    if (!AdPlaybackState.NONE.equals(adPlaybackState)) {
      // Pass the ad playback state to the player, and resume ads if necessary.
      eventListener.onAdPlaybackState(adPlaybackState);
    } else if (adsManager != null) {
      adPlaybackState =
          new AdPlaybackState(adsId, getAdGroupTimesUsForCuePoints(adsManager.getAdCuePoints()));
      updateAdPlaybackState();
    }
    for (AdOverlayInfo overlayInfo : adViewProvider.getAdOverlayInfos()) {
      adDisplayContainer.registerFriendlyObstruction(
          imaFactory.createFriendlyObstruction(
              overlayInfo.view,
              ImaUtil.getFriendlyObstructionPurpose(overlayInfo.purpose),
              overlayInfo.reasonDetail));
    }
  }

  /**
   * Populates the ad playback state with loaded cue points, if available. Any preroll will be
   * paused immediately while waiting for this instance to be {@link #activate(Player) activated}.
   */
  public void maybePreloadAds(long contentPositionMs, long contentDurationMs) {
    maybeInitializeAdsManager(contentPositionMs, contentDurationMs);
  }

  /** Activates playback. */
  public void activate(Player player) {
    this.player = player;
    player.addListener(this);

    boolean playWhenReady = player.getPlayWhenReady();
    onTimelineChanged(player.getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    @Nullable AdsManager adsManager = this.adsManager;
    if (!AdPlaybackState.NONE.equals(adPlaybackState) && adsManager != null && imaPausedContent) {
      // Check whether the current ad break matches the expected ad break based on the current
      // position. If not, discard the current ad break so that the correct ad break can load.
      long contentPositionMs = getContentPeriodPositionMs(player, timeline, period);
      int adGroupForPositionIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(
              C.msToUs(contentPositionMs), C.msToUs(contentDurationMs));
      if (adGroupForPositionIndex != C.INDEX_UNSET
          && imaAdInfo != null
          && imaAdInfo.adGroupIndex != adGroupForPositionIndex) {
        if (configuration.debugModeEnabled) {
          Log.d(TAG, "Discarding preloaded ad " + imaAdInfo);
        }
        adsManager.discardAdBreak();
      }
      if (playWhenReady) {
        adsManager.resume();
      }
    }
  }

  /** Deactivates playback. */
  public void deactivate() {
    Player player = checkNotNull(this.player);
    if (!AdPlaybackState.NONE.equals(adPlaybackState) && imaPausedContent) {
      if (adsManager != null) {
        adsManager.pause();
      }
      adPlaybackState =
          adPlaybackState.withAdResumePositionUs(
              playingAd ? C.msToUs(player.getCurrentPosition()) : 0);
    }
    lastVolumePercent = getPlayerVolumePercent();
    lastAdProgress = getAdVideoProgressUpdate();
    lastContentProgress = getContentVideoProgressUpdate();

    player.removeListener(this);
    this.player = null;
  }

  /** Stops passing of events from this instance and unregisters obstructions. */
  public void removeListener(EventListener eventListener) {
    eventListeners.remove(eventListener);
    if (eventListeners.isEmpty()) {
      adDisplayContainer.unregisterAllFriendlyObstructions();
    }
  }

  /** Releases all resources used by the ad tag loader. */
  public void release() {
    if (released) {
      return;
    }
    released = true;
    pendingAdRequestContext = null;
    destroyAdsManager();
    adsLoader.removeAdsLoadedListener(componentListener);
    adsLoader.removeAdErrorListener(componentListener);
    if (configuration.applicationAdErrorListener != null) {
      adsLoader.removeAdErrorListener(configuration.applicationAdErrorListener);
    }
    adsLoader.release();
    imaPausedContent = false;
    imaAdState = IMA_AD_STATE_NONE;
    imaAdMediaInfo = null;
    stopUpdatingAdProgress();
    imaAdInfo = null;
    pendingAdLoadError = null;
    // No more ads will play once the loader is released, so mark all ad groups as skipped.
    for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
      adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
    }
    updateAdPlaybackState();
  }

  /** Notifies the IMA SDK that the specified ad has been prepared for playback. */
  public void handlePrepareComplete(int adGroupIndex, int adIndexInAdGroup) {
    AdInfo adInfo = new AdInfo(adGroupIndex, adIndexInAdGroup);
    if (configuration.debugModeEnabled) {
      Log.d(TAG, "Prepared ad " + adInfo);
    }
    @Nullable AdMediaInfo adMediaInfo = adInfoByAdMediaInfo.inverse().get(adInfo);
    if (adMediaInfo != null) {
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onLoaded(adMediaInfo);
      }
    } else {
      Log.w(TAG, "Unexpected prepared ad " + adInfo);
    }
  }

  /** Notifies the IMA SDK that the specified ad has failed to prepare for playback. */
  public void handlePrepareError(int adGroupIndex, int adIndexInAdGroup, IOException exception) {
    if (player == null) {
      return;
    }
    try {
      handleAdPrepareError(adGroupIndex, adIndexInAdGroup, exception);
    } catch (RuntimeException e) {
      maybeNotifyInternalError("handlePrepareError", e);
    }
  }

  // Player.EventListener implementation.

  @Override
  public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
    if (timeline.isEmpty()) {
      // The player is being reset or contains no media.
      return;
    }
    this.timeline = timeline;
    Player player = checkNotNull(this.player);
    long contentDurationUs = timeline.getPeriod(player.getCurrentPeriodIndex(), period).durationUs;
    contentDurationMs = C.usToMs(contentDurationUs);
    if (contentDurationUs != adPlaybackState.contentDurationUs) {
      adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs);
      updateAdPlaybackState();
    }
    long contentPositionMs = getContentPeriodPositionMs(player, timeline, period);
    maybeInitializeAdsManager(contentPositionMs, contentDurationMs);
    handleTimelineOrPositionChanged();
  }

  @Override
  public void onPositionDiscontinuity(
      Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition,
      @Player.DiscontinuityReason int reason) {
    handleTimelineOrPositionChanged();
  }

  @Override
  public void onPlaybackStateChanged(@Player.State int playbackState) {
    @Nullable Player player = this.player;
    if (adsManager == null || player == null) {
      return;
    }

    if (playbackState == Player.STATE_BUFFERING
        && !player.isPlayingAd()
        && isWaitingForAdToLoad()) {
      waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime();
    } else if (playbackState == Player.STATE_READY) {
      waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET;
    }

    handlePlayerStateChanged(player.getPlayWhenReady(), playbackState);
  }

  @Override
  public void onPlayWhenReadyChanged(
      boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
    if (adsManager == null || player == null) {
      return;
    }

    if (imaAdState == IMA_AD_STATE_PLAYING && !playWhenReady) {
      adsManager.pause();
      return;
    }

    if (imaAdState == IMA_AD_STATE_PAUSED && playWhenReady) {
      adsManager.resume();
      return;
    }
    handlePlayerStateChanged(playWhenReady, player.getPlaybackState());
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    if (imaAdState != IMA_AD_STATE_NONE) {
      AdMediaInfo adMediaInfo = checkNotNull(imaAdMediaInfo);
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onError(adMediaInfo);
      }
    }
  }

  // Internal methods.

  private AdsLoader requestAds(
      Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer) {
    AdsLoader adsLoader = imaFactory.createAdsLoader(context, imaSdkSettings, adDisplayContainer);
    adsLoader.addAdErrorListener(componentListener);
    if (configuration.applicationAdErrorListener != null) {
      adsLoader.addAdErrorListener(configuration.applicationAdErrorListener);
    }
    adsLoader.addAdsLoadedListener(componentListener);
    AdsRequest request;
    try {
      request = ImaUtil.getAdsRequestForAdTagDataSpec(imaFactory, adTagDataSpec);
    } catch (IOException e) {
      adPlaybackState = new AdPlaybackState(adsId);
      updateAdPlaybackState();
      pendingAdLoadError = AdLoadException.createForAllAds(e);
      maybeNotifyPendingAdLoadError();
      return adsLoader;
    }
    pendingAdRequestContext = new Object();
    request.setUserRequestContext(pendingAdRequestContext);
    if (configuration.enableContinuousPlayback != null) {
      request.setContinuousPlayback(configuration.enableContinuousPlayback);
    }
    if (configuration.vastLoadTimeoutMs != TIMEOUT_UNSET) {
      request.setVastLoadTimeout(configuration.vastLoadTimeoutMs);
    }
    request.setContentProgressProvider(componentListener);
    adsLoader.requestAds(request);
    return adsLoader;
  }

  private void maybeInitializeAdsManager(long contentPositionMs, long contentDurationMs) {
    @Nullable AdsManager adsManager = this.adsManager;
    if (!isAdsManagerInitialized && adsManager != null) {
      isAdsManagerInitialized = true;
      @Nullable
      AdsRenderingSettings adsRenderingSettings =
          setupAdsRendering(contentPositionMs, contentDurationMs);
      if (adsRenderingSettings == null) {
        // There are no ads to play.
        destroyAdsManager();
      } else {
        adsManager.init(adsRenderingSettings);
        adsManager.start();
        if (configuration.debugModeEnabled) {
          Log.d(TAG, "Initialized with ads rendering settings: " + adsRenderingSettings);
        }
      }
      updateAdPlaybackState();
    }
  }

  /**
   * Configures ads rendering for starting playback, returning the settings for the IMA SDK or
   * {@code null} if no ads should play.
   */
  @Nullable
  private AdsRenderingSettings setupAdsRendering(long contentPositionMs, long contentDurationMs) {
    AdsRenderingSettings adsRenderingSettings = imaFactory.createAdsRenderingSettings();
    adsRenderingSettings.setEnablePreloading(true);
    adsRenderingSettings.setMimeTypes(
        configuration.adMediaMimeTypes != null
            ? configuration.adMediaMimeTypes
            : supportedMimeTypes);
    if (configuration.mediaLoadTimeoutMs != TIMEOUT_UNSET) {
      adsRenderingSettings.setLoadVideoTimeout(configuration.mediaLoadTimeoutMs);
    }
    if (configuration.mediaBitrate != BITRATE_UNSET) {
      adsRenderingSettings.setBitrateKbps(configuration.mediaBitrate / 1000);
    }
    adsRenderingSettings.setFocusSkipButtonWhenAvailable(
        configuration.focusSkipButtonWhenAvailable);
    if (configuration.adUiElements != null) {
      adsRenderingSettings.setUiElements(configuration.adUiElements);
    }

    // Skip ads based on the start position as required.
    long[] adGroupTimesUs = adPlaybackState.adGroupTimesUs;
    int adGroupForPositionIndex =
        adPlaybackState.getAdGroupIndexForPositionUs(
            C.msToUs(contentPositionMs), C.msToUs(contentDurationMs));
    if (adGroupForPositionIndex != C.INDEX_UNSET) {
      boolean playAdWhenStartingPlayback =
          configuration.playAdBeforeStartPosition
              || adGroupTimesUs[adGroupForPositionIndex] == C.msToUs(contentPositionMs);
      if (!playAdWhenStartingPlayback) {
        adGroupForPositionIndex++;
      } else if (hasMidrollAdGroups(adGroupTimesUs)) {
        // Provide the player's initial position to trigger loading and playing the ad. If there are
        // no midrolls, we are playing a preroll and any pending content position wouldn't be
        // cleared.
        pendingContentPositionMs = contentPositionMs;
      }
      if (adGroupForPositionIndex > 0) {
        for (int i = 0; i < adGroupForPositionIndex; i++) {
          adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
        }
        if (adGroupForPositionIndex == adGroupTimesUs.length) {
          // We don't need to play any ads. Because setPlayAdsAfterTime does not discard non-VMAP
          // ads, we signal that no ads will render so the caller can destroy the ads manager.
          return null;
        }
        long adGroupForPositionTimeUs = adGroupTimesUs[adGroupForPositionIndex];
        long adGroupBeforePositionTimeUs = adGroupTimesUs[adGroupForPositionIndex - 1];
        if (adGroupForPositionTimeUs == C.TIME_END_OF_SOURCE) {
          // Play the postroll by offsetting the start position just past the last non-postroll ad.
          adsRenderingSettings.setPlayAdsAfterTime(
              (double) adGroupBeforePositionTimeUs / C.MICROS_PER_SECOND + 1d);
        } else {
          // Play ads after the midpoint between the ad to play and the one before it, to avoid
          // issues with rounding one of the two ad times.
          double midpointTimeUs = (adGroupForPositionTimeUs + adGroupBeforePositionTimeUs) / 2d;
          adsRenderingSettings.setPlayAdsAfterTime(midpointTimeUs / C.MICROS_PER_SECOND);
        }
      }
    }
    return adsRenderingSettings;
  }

  private VideoProgressUpdate getContentVideoProgressUpdate() {
    boolean hasContentDuration = contentDurationMs != C.TIME_UNSET;
    long contentPositionMs;
    if (pendingContentPositionMs != C.TIME_UNSET) {
      sentPendingContentPositionMs = true;
      contentPositionMs = pendingContentPositionMs;
    } else if (player == null) {
      return lastContentProgress;
    } else if (fakeContentProgressElapsedRealtimeMs != C.TIME_UNSET) {
      long elapsedSinceEndMs = SystemClock.elapsedRealtime() - fakeContentProgressElapsedRealtimeMs;
      contentPositionMs = fakeContentProgressOffsetMs + elapsedSinceEndMs;
    } else if (imaAdState == IMA_AD_STATE_NONE && !playingAd && hasContentDuration) {
      contentPositionMs = getContentPeriodPositionMs(player, timeline, period);
    } else {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    }
    long contentDurationMs = hasContentDuration ? this.contentDurationMs : IMA_DURATION_UNSET;
    return new VideoProgressUpdate(contentPositionMs, contentDurationMs);
  }

  private VideoProgressUpdate getAdVideoProgressUpdate() {
    if (player == null) {
      return lastAdProgress;
    } else if (imaAdState != IMA_AD_STATE_NONE && playingAd) {
      long adDuration = player.getDuration();
      return adDuration == C.TIME_UNSET
          ? VideoProgressUpdate.VIDEO_TIME_NOT_READY
          : new VideoProgressUpdate(player.getCurrentPosition(), adDuration);
    } else {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    }
  }

  private void updateAdProgress() {
    VideoProgressUpdate videoProgressUpdate = getAdVideoProgressUpdate();
    if (configuration.debugModeEnabled) {
      Log.d(TAG, "Ad progress: " + ImaUtil.getStringForVideoProgressUpdate(videoProgressUpdate));
    }

    AdMediaInfo adMediaInfo = checkNotNull(imaAdMediaInfo);
    for (int i = 0; i < adCallbacks.size(); i++) {
      adCallbacks.get(i).onAdProgress(adMediaInfo, videoProgressUpdate);
    }
    handler.removeCallbacks(updateAdProgressRunnable);
    handler.postDelayed(updateAdProgressRunnable, AD_PROGRESS_UPDATE_INTERVAL_MS);
  }

  private void stopUpdatingAdProgress() {
    handler.removeCallbacks(updateAdProgressRunnable);
  }

  private int getPlayerVolumePercent() {
    @Nullable Player player = this.player;
    if (player == null) {
      return lastVolumePercent;
    }

    if (player.isCommandAvailable(COMMAND_GET_VOLUME)) {
      return (int) (player.getVolume() * 100);
    }

    // Check for a selected track using an audio renderer.
    TrackSelectionArray trackSelections = player.getCurrentTrackSelections();
    return TrackSelectionUtil.hasTrackOfType(trackSelections, C.TRACK_TYPE_AUDIO) ? 100 : 0;
  }

  private void handleAdEvent(AdEvent adEvent) {
    if (adsManager == null) {
      // Drop events after release.
      return;
    }
    switch (adEvent.getType()) {
      case AD_BREAK_FETCH_ERROR:
        String adGroupTimeSecondsString = checkNotNull(adEvent.getAdData().get("adBreakTime"));
        if (configuration.debugModeEnabled) {
          Log.d(TAG, "Fetch error for ad at " + adGroupTimeSecondsString + " seconds");
        }
        double adGroupTimeSeconds = Double.parseDouble(adGroupTimeSecondsString);
        int adGroupIndex =
            adGroupTimeSeconds == -1.0
                ? adPlaybackState.adGroupCount - 1
                : getAdGroupIndexForCuePointTimeSeconds(adGroupTimeSeconds);
        markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex);
        break;
      case CONTENT_PAUSE_REQUESTED:
        // After CONTENT_PAUSE_REQUESTED, IMA will playAd/pauseAd/stopAd to show one or more ads
        // before sending CONTENT_RESUME_REQUESTED.
        imaPausedContent = true;
        pauseContentInternal();
        break;
      case TAPPED:
        for (int i = 0; i < eventListeners.size(); i++) {
          eventListeners.get(i).onAdTapped();
        }
        break;
      case CLICKED:
        for (int i = 0; i < eventListeners.size(); i++) {
          eventListeners.get(i).onAdClicked();
        }
        break;
      case CONTENT_RESUME_REQUESTED:
        imaPausedContent = false;
        resumeContentInternal();
        break;
      case LOG:
        Map<String, String> adData = adEvent.getAdData();
        String message = "AdEvent: " + adData;
        Log.i(TAG, message);
        break;
      default:
        break;
    }
  }

  private void pauseContentInternal() {
    imaAdState = IMA_AD_STATE_NONE;
    if (sentPendingContentPositionMs) {
      pendingContentPositionMs = C.TIME_UNSET;
      sentPendingContentPositionMs = false;
    }
  }

  private void resumeContentInternal() {
    if (imaAdInfo != null) {
      adPlaybackState = adPlaybackState.withSkippedAdGroup(imaAdInfo.adGroupIndex);
      updateAdPlaybackState();
    }
  }

  /**
   * Returns whether this instance is expecting the first ad in an the upcoming ad group to load
   * within the {@link ImaUtil.Configuration#adPreloadTimeoutMs preload timeout}.
   */
  private boolean isWaitingForAdToLoad() {
    @Nullable Player player = this.player;
    if (player == null) {
      return false;
    }
    int adGroupIndex = getLoadingAdGroupIndex();
    if (adGroupIndex == C.INDEX_UNSET) {
      return false;
    }
    AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
    if (adGroup.count != C.LENGTH_UNSET
        && adGroup.count != 0
        && adGroup.states[0] != AdPlaybackState.AD_STATE_UNAVAILABLE) {
      // An ad is available already.
      return false;
    }
    long adGroupTimeMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
    long contentPositionMs = getContentPeriodPositionMs(player, timeline, period);
    long timeUntilAdMs = adGroupTimeMs - contentPositionMs;
    return timeUntilAdMs < configuration.adPreloadTimeoutMs;
  }

  private void handlePlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
    if (playingAd && imaAdState == IMA_AD_STATE_PLAYING) {
      if (!bufferingAd && playbackState == Player.STATE_BUFFERING) {
        bufferingAd = true;
        AdMediaInfo adMediaInfo = checkNotNull(imaAdMediaInfo);
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onBuffering(adMediaInfo);
        }
        stopUpdatingAdProgress();
      } else if (bufferingAd && playbackState == Player.STATE_READY) {
        bufferingAd = false;
        updateAdProgress();
      }
    }

    if (imaAdState == IMA_AD_STATE_NONE
        && playbackState == Player.STATE_BUFFERING
        && playWhenReady) {
      ensureSentContentCompleteIfAtEndOfStream();
    } else if (imaAdState != IMA_AD_STATE_NONE && playbackState == Player.STATE_ENDED) {
      @Nullable AdMediaInfo adMediaInfo = imaAdMediaInfo;
      if (adMediaInfo == null) {
        Log.w(TAG, "onEnded without ad media info");
      } else {
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onEnded(adMediaInfo);
        }
      }
      if (configuration.debugModeEnabled) {
        Log.d(TAG, "VideoAdPlayerCallback.onEnded in onPlaybackStateChanged");
      }
    }
  }

  private void handleTimelineOrPositionChanged() {
    @Nullable Player player = this.player;
    if (adsManager == null || player == null) {
      return;
    }
    if (!playingAd && !player.isPlayingAd()) {
      ensureSentContentCompleteIfAtEndOfStream();
      if (!sentContentComplete && !timeline.isEmpty()) {
        long positionMs = getContentPeriodPositionMs(player, timeline, period);
        timeline.getPeriod(player.getCurrentPeriodIndex(), period);
        int newAdGroupIndex = period.getAdGroupIndexForPositionUs(C.msToUs(positionMs));
        if (newAdGroupIndex != C.INDEX_UNSET) {
          sentPendingContentPositionMs = false;
          pendingContentPositionMs = positionMs;
        }
      }
    }

    boolean wasPlayingAd = playingAd;
    int oldPlayingAdIndexInAdGroup = playingAdIndexInAdGroup;
    playingAd = player.isPlayingAd();
    playingAdIndexInAdGroup = playingAd ? player.getCurrentAdIndexInAdGroup() : C.INDEX_UNSET;
    boolean adFinished = wasPlayingAd && playingAdIndexInAdGroup != oldPlayingAdIndexInAdGroup;
    if (adFinished) {
      // IMA is waiting for the ad playback to finish so invoke the callback now.
      // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
      @Nullable AdMediaInfo adMediaInfo = imaAdMediaInfo;
      if (adMediaInfo == null) {
        Log.w(TAG, "onEnded without ad media info");
      } else {
        @Nullable AdInfo adInfo = adInfoByAdMediaInfo.get(adMediaInfo);
        if (playingAdIndexInAdGroup == C.INDEX_UNSET
            || (adInfo != null && adInfo.adIndexInAdGroup < playingAdIndexInAdGroup)) {
          for (int i = 0; i < adCallbacks.size(); i++) {
            adCallbacks.get(i).onEnded(adMediaInfo);
          }
          if (configuration.debugModeEnabled) {
            Log.d(
                TAG, "VideoAdPlayerCallback.onEnded in onTimelineChanged/onPositionDiscontinuity");
          }
        }
      }
    }
    if (!sentContentComplete && !wasPlayingAd && playingAd && imaAdState == IMA_AD_STATE_NONE) {
      int adGroupIndex = player.getCurrentAdGroupIndex();
      if (adPlaybackState.adGroupTimesUs[adGroupIndex] == C.TIME_END_OF_SOURCE) {
        sendContentComplete();
      } else {
        // IMA hasn't called playAd yet, so fake the content position.
        fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime();
        fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
        if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
          fakeContentProgressOffsetMs = contentDurationMs;
        }
      }
    }
  }

  private void loadAdInternal(AdMediaInfo adMediaInfo, AdPodInfo adPodInfo) {
    if (adsManager == null) {
      // Drop events after release.
      if (configuration.debugModeEnabled) {
        Log.d(
            TAG,
            "loadAd after release " + getAdMediaInfoString(adMediaInfo) + ", ad pod " + adPodInfo);
      }
      return;
    }

    int adGroupIndex = getAdGroupIndexForAdPod(adPodInfo);
    int adIndexInAdGroup = adPodInfo.getAdPosition() - 1;
    AdInfo adInfo = new AdInfo(adGroupIndex, adIndexInAdGroup);
    // The ad URI may already be known, so force put to update it if needed.
    adInfoByAdMediaInfo.forcePut(adMediaInfo, adInfo);
    if (configuration.debugModeEnabled) {
      Log.d(TAG, "loadAd " + getAdMediaInfoString(adMediaInfo));
    }
    if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) {
      // We have already marked this ad as having failed to load, so ignore the request. IMA will
      // timeout after its media load timeout.
      return;
    }

    // The ad count may increase on successive loads of ads in the same ad pod, for example, due to
    // separate requests for ad tags with multiple ads within the ad pod completing after an earlier
    // ad has loaded. See also https://github.com/google/ExoPlayer/issues/7477.
    AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adInfo.adGroupIndex];
    adPlaybackState =
        adPlaybackState.withAdCount(
            adInfo.adGroupIndex, max(adPodInfo.getTotalAds(), adGroup.states.length));
    adGroup = adPlaybackState.adGroups[adInfo.adGroupIndex];
    for (int i = 0; i < adIndexInAdGroup; i++) {
      // Any preceding ads that haven't loaded are not going to load.
      if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, /* adIndexInAdGroup= */ i);
      }
    }

    Uri adUri = Uri.parse(adMediaInfo.getUrl());
    adPlaybackState =
        adPlaybackState.withAdUri(adInfo.adGroupIndex, adInfo.adIndexInAdGroup, adUri);
    updateAdPlaybackState();
  }

  private void playAdInternal(AdMediaInfo adMediaInfo) {
    if (configuration.debugModeEnabled) {
      Log.d(TAG, "playAd " + getAdMediaInfoString(adMediaInfo));
    }
    if (adsManager == null) {
      // Drop events after release.
      return;
    }

    if (imaAdState == IMA_AD_STATE_PLAYING) {
      // IMA does not always call stopAd before resuming content.
      // See [Internal: b/38354028].
      Log.w(TAG, "Unexpected playAd without stopAd");
    }

    if (imaAdState == IMA_AD_STATE_NONE) {
      // IMA is requesting to play the ad, so stop faking the content position.
      fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
      fakeContentProgressOffsetMs = C.TIME_UNSET;
      imaAdState = IMA_AD_STATE_PLAYING;
      imaAdMediaInfo = adMediaInfo;
      imaAdInfo = checkNotNull(adInfoByAdMediaInfo.get(adMediaInfo));
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onPlay(adMediaInfo);
      }
      if (pendingAdPrepareErrorAdInfo != null && pendingAdPrepareErrorAdInfo.equals(imaAdInfo)) {
        pendingAdPrepareErrorAdInfo = null;
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onError(adMediaInfo);
        }
      }
      updateAdProgress();
    } else {
      imaAdState = IMA_AD_STATE_PLAYING;
      checkState(adMediaInfo.equals(imaAdMediaInfo));
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onResume(adMediaInfo);
      }
    }
    if (player == null || !player.getPlayWhenReady()) {
      // Either this loader hasn't been activated yet, or the player is paused now.
      checkNotNull(adsManager).pause();
    }
  }

  private void pauseAdInternal(AdMediaInfo adMediaInfo) {
    if (configuration.debugModeEnabled) {
      Log.d(TAG, "pauseAd " + getAdMediaInfoString(adMediaInfo));
    }
    if (adsManager == null) {
      // Drop event after release.
      return;
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      // This method is called if loadAd has been called but the loaded ad won't play due to a seek
      // to a different position, so drop the event. See also [Internal: b/159111848].
      return;
    }
    if (configuration.debugModeEnabled && !adMediaInfo.equals(imaAdMediaInfo)) {
      Log.w(
          TAG,
          "Unexpected pauseAd for "
              + getAdMediaInfoString(adMediaInfo)
              + ", expected "
              + getAdMediaInfoString(imaAdMediaInfo));
    }
    imaAdState = IMA_AD_STATE_PAUSED;
    for (int i = 0; i < adCallbacks.size(); i++) {
      adCallbacks.get(i).onPause(adMediaInfo);
    }
  }

  private void stopAdInternal(AdMediaInfo adMediaInfo) {
    if (configuration.debugModeEnabled) {
      Log.d(TAG, "stopAd " + getAdMediaInfoString(adMediaInfo));
    }
    if (adsManager == null) {
      // Drop event after release.
      return;
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      // This method is called if loadAd has been called but the preloaded ad won't play due to a
      // seek to a different position, so drop the event and discard the ad. See also [Internal:
      // b/159111848].
      @Nullable AdInfo adInfo = adInfoByAdMediaInfo.get(adMediaInfo);
      if (adInfo != null) {
        adPlaybackState =
            adPlaybackState.withSkippedAd(adInfo.adGroupIndex, adInfo.adIndexInAdGroup);
        updateAdPlaybackState();
      }
      return;
    }
    imaAdState = IMA_AD_STATE_NONE;
    stopUpdatingAdProgress();
    // TODO: Handle the skipped event so the ad can be marked as skipped rather than played.
    checkNotNull(imaAdInfo);
    int adGroupIndex = imaAdInfo.adGroupIndex;
    int adIndexInAdGroup = imaAdInfo.adIndexInAdGroup;
    if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) {
      // We have already marked this ad as having failed to load, so ignore the request.
      return;
    }
    adPlaybackState =
        adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup).withAdResumePositionUs(0);
    updateAdPlaybackState();
    if (!playingAd) {
      imaAdMediaInfo = null;
      imaAdInfo = null;
    }
  }

  private void handleAdGroupLoadError(Exception error) {
    int adGroupIndex = getLoadingAdGroupIndex();
    if (adGroupIndex == C.INDEX_UNSET) {
      Log.w(TAG, "Unable to determine ad group index for ad group load error", error);
      return;
    }
    markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex);
    if (pendingAdLoadError == null) {
      pendingAdLoadError = AdLoadException.createForAdGroup(error, adGroupIndex);
    }
  }

  private void markAdGroupInErrorStateAndClearPendingContentPosition(int adGroupIndex) {
    // Update the ad playback state so all ads in the ad group are in the error state.
    AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
    if (adGroup.count == C.LENGTH_UNSET) {
      adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, max(1, adGroup.states.length));
      adGroup = adPlaybackState.adGroups[adGroupIndex];
    }
    for (int i = 0; i < adGroup.count; i++) {
      if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
        if (configuration.debugModeEnabled) {
          Log.d(TAG, "Removing ad " + i + " in ad group " + adGroupIndex);
        }
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, i);
      }
    }
    updateAdPlaybackState();
    // Clear any pending content position that triggered attempting to load the ad group.
    pendingContentPositionMs = C.TIME_UNSET;
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
  }

  private void handleAdPrepareError(int adGroupIndex, int adIndexInAdGroup, Exception exception) {
    if (configuration.debugModeEnabled) {
      Log.d(
          TAG, "Prepare error for ad " + adIndexInAdGroup + " in group " + adGroupIndex, exception);
    }
    if (adsManager == null) {
      Log.w(TAG, "Ignoring ad prepare error after release");
      return;
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      // Send IMA a content position at the ad group so that it will try to play it, at which point
      // we can notify that it failed to load.
      fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime();
      fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
      if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
        fakeContentProgressOffsetMs = contentDurationMs;
      }
      pendingAdPrepareErrorAdInfo = new AdInfo(adGroupIndex, adIndexInAdGroup);
    } else {
      AdMediaInfo adMediaInfo = checkNotNull(imaAdMediaInfo);
      // We're already playing an ad.
      if (adIndexInAdGroup > playingAdIndexInAdGroup) {
        // Mark the playing ad as ended so we can notify the error on the next ad and remove it,
        // which means that the ad after will load (if any).
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onEnded(adMediaInfo);
        }
      }
      playingAdIndexInAdGroup = adPlaybackState.adGroups[adGroupIndex].getFirstAdIndexToPlay();
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onError(checkNotNull(adMediaInfo));
      }
    }
    adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup);
    updateAdPlaybackState();
  }

  private void ensureSentContentCompleteIfAtEndOfStream() {
    if (!sentContentComplete
        && contentDurationMs != C.TIME_UNSET
        && pendingContentPositionMs == C.TIME_UNSET
        && getContentPeriodPositionMs(checkNotNull(player), timeline, period)
                + THRESHOLD_END_OF_CONTENT_MS
            >= contentDurationMs) {
      sendContentComplete();
    }
  }

  private void sendContentComplete() {
    for (int i = 0; i < adCallbacks.size(); i++) {
      adCallbacks.get(i).onContentComplete();
    }
    sentContentComplete = true;
    if (configuration.debugModeEnabled) {
      Log.d(TAG, "adsLoader.contentComplete");
    }
    for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
      if (adPlaybackState.adGroupTimesUs[i] != C.TIME_END_OF_SOURCE) {
        adPlaybackState = adPlaybackState.withSkippedAdGroup(/* adGroupIndex= */ i);
      }
    }
    updateAdPlaybackState();
  }

  private void updateAdPlaybackState() {
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners.get(i).onAdPlaybackState(adPlaybackState);
    }
  }

  private void maybeNotifyPendingAdLoadError() {
    if (pendingAdLoadError != null) {
      for (int i = 0; i < eventListeners.size(); i++) {
        eventListeners.get(i).onAdLoadError(pendingAdLoadError, adTagDataSpec);
      }
      pendingAdLoadError = null;
    }
  }

  private void maybeNotifyInternalError(String name, Exception cause) {
    String message = "Internal error in " + name;
    Log.e(TAG, message, cause);
    // We can't recover from an unexpected error in general, so skip all remaining ads.
    for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
      adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
    }
    updateAdPlaybackState();
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners
          .get(i)
          .onAdLoadError(
              AdLoadException.createForUnexpected(new RuntimeException(message, cause)),
              adTagDataSpec);
    }
  }

  private int getAdGroupIndexForAdPod(AdPodInfo adPodInfo) {
    if (adPodInfo.getPodIndex() == -1) {
      // This is a postroll ad.
      return adPlaybackState.adGroupCount - 1;
    }

    // adPodInfo.podIndex may be 0-based or 1-based, so for now look up the cue point instead.
    return getAdGroupIndexForCuePointTimeSeconds(adPodInfo.getTimeOffset());
  }

  /**
   * Returns the index of the ad group that will preload next, or {@link C#INDEX_UNSET} if there is
   * no such ad group.
   */
  private int getLoadingAdGroupIndex() {
    if (player == null) {
      return C.INDEX_UNSET;
    }
    long playerPositionUs = C.msToUs(getContentPeriodPositionMs(player, timeline, period));
    int adGroupIndex =
        adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, C.msToUs(contentDurationMs));
    if (adGroupIndex == C.INDEX_UNSET) {
      adGroupIndex =
          adPlaybackState.getAdGroupIndexAfterPositionUs(
              playerPositionUs, C.msToUs(contentDurationMs));
    }
    return adGroupIndex;
  }

  private int getAdGroupIndexForCuePointTimeSeconds(double cuePointTimeSeconds) {
    // We receive initial cue points from IMA SDK as floats. This code replicates the same
    // calculation used to populate adGroupTimesUs (having truncated input back to float, to avoid
    // failures if the behavior of the IMA SDK changes to provide greater precision).
    float cuePointTimeSecondsFloat = (float) cuePointTimeSeconds;
    long adPodTimeUs = Math.round((double) cuePointTimeSecondsFloat * C.MICROS_PER_SECOND);
    for (int adGroupIndex = 0; adGroupIndex < adPlaybackState.adGroupCount; adGroupIndex++) {
      long adGroupTimeUs = adPlaybackState.adGroupTimesUs[adGroupIndex];
      if (adGroupTimeUs != C.TIME_END_OF_SOURCE
          && Math.abs(adGroupTimeUs - adPodTimeUs) < THRESHOLD_AD_MATCH_US) {
        return adGroupIndex;
      }
    }
    throw new IllegalStateException("Failed to find cue point");
  }

  private String getAdMediaInfoString(@Nullable AdMediaInfo adMediaInfo) {
    @Nullable AdInfo adInfo = adInfoByAdMediaInfo.get(adMediaInfo);
    return "AdMediaInfo["
        + (adMediaInfo == null ? "null" : adMediaInfo.getUrl())
        + ", "
        + adInfo
        + "]";
  }

  private static long getContentPeriodPositionMs(
      Player player, Timeline timeline, Timeline.Period period) {
    long contentWindowPositionMs = player.getContentPosition();
    if (timeline.isEmpty()) {
      return contentWindowPositionMs;
    } else {
      return contentWindowPositionMs
          - timeline.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs();
    }
  }

  private static boolean hasMidrollAdGroups(long[] adGroupTimesUs) {
    int count = adGroupTimesUs.length;
    if (count == 1) {
      return adGroupTimesUs[0] != 0 && adGroupTimesUs[0] != C.TIME_END_OF_SOURCE;
    } else if (count == 2) {
      return adGroupTimesUs[0] != 0 || adGroupTimesUs[1] != C.TIME_END_OF_SOURCE;
    } else {
      // There's at least one midroll ad group, as adGroupTimesUs is never empty.
      return true;
    }
  }

  private void destroyAdsManager() {
    if (adsManager != null) {
      adsManager.removeAdErrorListener(componentListener);
      if (configuration.applicationAdErrorListener != null) {
        adsManager.removeAdErrorListener(configuration.applicationAdErrorListener);
      }
      adsManager.removeAdEventListener(componentListener);
      if (configuration.applicationAdEventListener != null) {
        adsManager.removeAdEventListener(configuration.applicationAdEventListener);
      }
      adsManager.destroy();
      adsManager = null;
    }
  }

  private final class ComponentListener
      implements AdsLoadedListener,
          ContentProgressProvider,
          AdEventListener,
          AdErrorListener,
          VideoAdPlayer {

    // AdsLoader.AdsLoadedListener implementation.

    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
      AdsManager adsManager = adsManagerLoadedEvent.getAdsManager();
      if (!Util.areEqual(pendingAdRequestContext, adsManagerLoadedEvent.getUserRequestContext())) {
        adsManager.destroy();
        return;
      }
      pendingAdRequestContext = null;
      AdTagLoader.this.adsManager = adsManager;
      adsManager.addAdErrorListener(this);
      if (configuration.applicationAdErrorListener != null) {
        adsManager.addAdErrorListener(configuration.applicationAdErrorListener);
      }
      adsManager.addAdEventListener(this);
      if (configuration.applicationAdEventListener != null) {
        adsManager.addAdEventListener(configuration.applicationAdEventListener);
      }
      try {
        adPlaybackState =
            new AdPlaybackState(adsId, getAdGroupTimesUsForCuePoints(adsManager.getAdCuePoints()));
        updateAdPlaybackState();
      } catch (RuntimeException e) {
        maybeNotifyInternalError("onAdsManagerLoaded", e);
      }
    }

    // ContentProgressProvider implementation.

    @Override
    public VideoProgressUpdate getContentProgress() {
      VideoProgressUpdate videoProgressUpdate = getContentVideoProgressUpdate();
      if (configuration.debugModeEnabled) {
        Log.d(
            TAG,
            "Content progress: " + ImaUtil.getStringForVideoProgressUpdate(videoProgressUpdate));
      }

      if (waitingForPreloadElapsedRealtimeMs != C.TIME_UNSET) {
        // IMA is polling the player position but we are buffering for an ad to preload, so playback
        // may be stuck. Detect this case and signal an error if applicable.
        long stuckElapsedRealtimeMs =
            SystemClock.elapsedRealtime() - waitingForPreloadElapsedRealtimeMs;
        if (stuckElapsedRealtimeMs >= THRESHOLD_AD_PRELOAD_MS) {
          waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET;
          handleAdGroupLoadError(new IOException("Ad preloading timed out"));
          maybeNotifyPendingAdLoadError();
        }
      } else if (pendingContentPositionMs != C.TIME_UNSET
          && player != null
          && player.getPlaybackState() == Player.STATE_BUFFERING
          && isWaitingForAdToLoad()) {
        // Prepare to timeout the load of an ad for the pending seek operation.
        waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime();
      }

      return videoProgressUpdate;
    }

    // AdEvent.AdEventListener implementation.

    @Override
    public void onAdEvent(AdEvent adEvent) {
      AdEventType adEventType = adEvent.getType();
      if (configuration.debugModeEnabled && adEventType != AdEventType.AD_PROGRESS) {
        Log.d(TAG, "onAdEvent: " + adEventType);
      }
      try {
        handleAdEvent(adEvent);
      } catch (RuntimeException e) {
        maybeNotifyInternalError("onAdEvent", e);
      }
    }

    // AdErrorEvent.AdErrorListener implementation.

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
      AdError error = adErrorEvent.getError();
      if (configuration.debugModeEnabled) {
        Log.d(TAG, "onAdError", error);
      }
      if (adsManager == null) {
        // No ads were loaded, so allow playback to start without any ads.
        pendingAdRequestContext = null;
        adPlaybackState = new AdPlaybackState(adsId);
        updateAdPlaybackState();
      } else if (ImaUtil.isAdGroupLoadError(error)) {
        try {
          handleAdGroupLoadError(error);
        } catch (RuntimeException e) {
          maybeNotifyInternalError("onAdError", e);
        }
      }
      if (pendingAdLoadError == null) {
        pendingAdLoadError = AdLoadException.createForAllAds(error);
      }
      maybeNotifyPendingAdLoadError();
    }

    // VideoAdPlayer implementation.

    @Override
    public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
      adCallbacks.add(videoAdPlayerCallback);
    }

    @Override
    public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
      adCallbacks.remove(videoAdPlayerCallback);
    }

    @Override
    public VideoProgressUpdate getAdProgress() {
      throw new IllegalStateException("Unexpected call to getAdProgress when using preloading");
    }

    @Override
    public int getVolume() {
      return getPlayerVolumePercent();
    }

    @Override
    public void loadAd(AdMediaInfo adMediaInfo, AdPodInfo adPodInfo) {
      try {
        loadAdInternal(adMediaInfo, adPodInfo);
      } catch (RuntimeException e) {
        maybeNotifyInternalError("loadAd", e);
      }
    }

    @Override
    public void playAd(AdMediaInfo adMediaInfo) {
      try {
        playAdInternal(adMediaInfo);
      } catch (RuntimeException e) {
        maybeNotifyInternalError("playAd", e);
      }
    }

    @Override
    public void pauseAd(AdMediaInfo adMediaInfo) {
      try {
        pauseAdInternal(adMediaInfo);
      } catch (RuntimeException e) {
        maybeNotifyInternalError("pauseAd", e);
      }
    }

    @Override
    public void stopAd(AdMediaInfo adMediaInfo) {
      try {
        stopAdInternal(adMediaInfo);
      } catch (RuntimeException e) {
        maybeNotifyInternalError("stopAd", e);
      }
    }

    @Override
    public void release() {
      // Do nothing.
    }
  }

  // TODO: Consider moving this into AdPlaybackState.
  private static final class AdInfo {

    public final int adGroupIndex;
    public final int adIndexInAdGroup;

    public AdInfo(int adGroupIndex, int adIndexInAdGroup) {
      this.adGroupIndex = adGroupIndex;
      this.adIndexInAdGroup = adIndexInAdGroup;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      AdInfo adInfo = (AdInfo) o;
      if (adGroupIndex != adInfo.adGroupIndex) {
        return false;
      }
      return adIndexInAdGroup == adInfo.adIndexInAdGroup;
    }

    @Override
    public int hashCode() {
      int result = adGroupIndex;
      result = 31 * result + adIndexInAdGroup;
      return result;
    }

    @Override
    public String toString() {
      return "(" + adGroupIndex + ", " + adIndexInAdGroup + ')';
    }
  }
}
