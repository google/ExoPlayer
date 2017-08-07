/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
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
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads ads using the IMA SDK. All methods are called on the main thread.
 */
public final class ImaAdsLoader implements Player.EventListener, VideoAdPlayer,
    ContentProgressProvider, AdErrorListener, AdsLoadedListener, AdEventListener {

  /**
   * Listener for ad loader events. All methods are called on the main thread.
   */
  /* package */ interface EventListener {

    /**
     * Called when the ad playback state has been updated.
     *
     * @param adPlaybackState The new ad playback state.
     */
    void onAdPlaybackState(AdPlaybackState adPlaybackState);

    /**
     * Called when there was an error loading ads.
     *
     * @param error The error.
     */
    void onLoadError(IOException error);

    /**
     * Called when the user clicks through an ad (for example, following a 'learn more' link).
     */
    void onAdClicked();

    /**
     * Called when the user taps a non-clickthrough part of an ad.
     */
    void onAdTapped();

  }

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.ima");
  }

  private static final boolean DEBUG = false;
  private static final String TAG = "ImaAdsLoader";

  /**
   * Whether to enable preloading of ads in {@link AdsRenderingSettings}.
   */
  private static final boolean ENABLE_PRELOADING = true;

  private static final String IMA_SDK_SETTINGS_PLAYER_TYPE = "google/exo.ext.ima";
  private static final String IMA_SDK_SETTINGS_PLAYER_VERSION = ExoPlayerLibraryInfo.VERSION;

  /**
   * Threshold before the end of content at which IMA is notified that content is complete if the
   * player buffers, in milliseconds.
   */
  private static final long END_OF_CONTENT_POSITION_THRESHOLD_MS = 5000;

  private final Uri adTagUri;
  private final Timeline.Period period;
  private final List<VideoAdPlayerCallback> adCallbacks;
  private final ImaSdkFactory imaSdkFactory;
  private final AdDisplayContainer adDisplayContainer;
  private final AdsLoader adsLoader;

  private EventListener eventListener;
  private Player player;
  private VideoProgressUpdate lastContentProgress;
  private VideoProgressUpdate lastAdProgress;

  private AdsManager adsManager;
  private Timeline timeline;
  private long contentDurationMs;
  private AdPlaybackState adPlaybackState;

  // Fields tracking IMA's state.

  /**
   * The index of the current ad group that IMA is loading.
   */
  private int adGroupIndex;
  /**
   * Whether IMA has sent an ad event to pause content since the last resume content event.
   */
  private boolean imaPausedContent;
  /**
   * If {@link #playingAd} is set, stores whether IMA has called {@link #playAd()} and not
   * {@link #stopAd()}.
   */
  private boolean imaPlayingAd;
  /**
   * If {@link #playingAd} is set, stores whether IMA has called {@link #pauseAd()} since a
   * preceding call to {@link #playAd()} for the current ad.
   */
  private boolean imaPausedInAd;
  /**
   * Whether {@link AdsLoader#contentComplete()} has been called since starting ad playback.
   */
  private boolean sentContentComplete;

  // Fields tracking the player/loader state.

  /**
   * Whether the player's play when ready flag has temporarily been set to true for playing ads.
   */
  private boolean playWhenReadyOverriddenForAds;
  /**
   * Whether the player is playing an ad.
   */
  private boolean playingAd;
  /**
   * If the player is playing an ad, stores the ad index in its ad group. {@link C#INDEX_UNSET}
   * otherwise.
   */
  private int playingAdIndexInAdGroup;
  /**
   * If a content period has finished but IMA has not yet sent an ad event with
   * {@link AdEvent.AdEventType#CONTENT_PAUSE_REQUESTED}, stores the value of
   * {@link SystemClock#elapsedRealtime()} when the content stopped playing. This can be used to
   * determine a fake, increasing content position. {@link C#TIME_UNSET} otherwise.
   */
  private long fakeContentProgressElapsedRealtimeMs;
  /**
   * If {@link #fakeContentProgressElapsedRealtimeMs} is set, stores the offset from which the
   * content progress should increase. {@link C#TIME_UNSET} otherwise.
   */
  private long fakeContentProgressOffsetMs;
  /**
   * Stores the pending content position when a seek operation was intercepted to play an ad.
   */
  private long pendingContentPositionMs;
  /**
   * Whether {@link #getContentProgress()} has sent {@link #pendingContentPositionMs} to IMA.
   */
  private boolean sentPendingContentPositionMs;
  /**
   * Whether {@link #release()} has been called.
   */
  private boolean released;

  /**
   * Creates a new IMA ads loader.
   *
   * @param context The context.
   * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
   *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
   *     more information.
   */
  public ImaAdsLoader(Context context, Uri adTagUri) {
    this(context, adTagUri, null);
  }

  /**
   * Creates a new IMA ads loader.
   *
   * @param context The context.
   * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
   *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
   *     more information.
   * @param imaSdkSettings {@link ImaSdkSettings} used to configure the IMA SDK, or {@code null} to
   *     use the default settings. If set, the player type and version fields may be overwritten.
   */
  public ImaAdsLoader(Context context, Uri adTagUri, ImaSdkSettings imaSdkSettings) {
    this.adTagUri = adTagUri;
    period = new Timeline.Period();
    adCallbacks = new ArrayList<>(1);
    imaSdkFactory = ImaSdkFactory.getInstance();
    adDisplayContainer = imaSdkFactory.createAdDisplayContainer();
    adDisplayContainer.setPlayer(this);
    if (imaSdkSettings == null) {
      imaSdkSettings = imaSdkFactory.createImaSdkSettings();
    }
    imaSdkSettings.setPlayerType(IMA_SDK_SETTINGS_PLAYER_TYPE);
    imaSdkSettings.setPlayerVersion(IMA_SDK_SETTINGS_PLAYER_VERSION);
    adsLoader = imaSdkFactory.createAdsLoader(context, imaSdkSettings);
    adsLoader.addAdErrorListener(this);
    adsLoader.addAdsLoadedListener(this);
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
    fakeContentProgressOffsetMs = C.TIME_UNSET;
    pendingContentPositionMs = C.TIME_UNSET;
    adGroupIndex = C.INDEX_UNSET;
    contentDurationMs = C.TIME_UNSET;
  }

  /**
   * Attaches a player that will play ads loaded using this instance.
   *
   * @param player The player instance that will play the loaded ads.
   * @param eventListener Listener for ads loader events.
   * @param adUiViewGroup A {@link ViewGroup} on top of the player that will show any ad UI.
   */
  /* package */ void attachPlayer(ExoPlayer player, EventListener eventListener,
      ViewGroup adUiViewGroup) {
    this.player = player;
    this.eventListener = eventListener;
    lastAdProgress = null;
    lastContentProgress = null;
    adDisplayContainer.setAdContainer(adUiViewGroup);
    player.addListener(this);
    if (adPlaybackState != null) {
      eventListener.onAdPlaybackState(adPlaybackState);
      if (imaPausedContent) {
        adsManager.resume();
      }
    } else {
      requestAds();
    }
  }

  /**
   * Detaches the attached player and event listener. To attach a new player, call
   * {@link #attachPlayer(ExoPlayer, EventListener, ViewGroup)}. Call {@link #release()} to release
   * all resources associated with this instance.
   */
  /* package */ void detachPlayer() {
    if (adsManager != null && imaPausedContent) {
      adPlaybackState.setAdResumePositionUs(C.msToUs(player.getCurrentPosition()));
      adsManager.pause();
    }
    lastAdProgress = getAdProgress();
    lastContentProgress = getContentProgress();
    player.removeListener(this);
    player = null;
    eventListener = null;
  }

  /**
   * Releases the loader. Must be called when the instance is no longer needed.
   */
  public void release() {
    released = true;
    if (adsManager != null) {
      adsManager.destroy();
      adsManager = null;
    }
  }

  // AdsLoader.AdsLoadedListener implementation.

  @Override
  public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
    AdsManager adsManager = adsManagerLoadedEvent.getAdsManager();
    if (released) {
      adsManager.destroy();
      return;
    }
    this.adsManager = adsManager;
    adsManager.addAdErrorListener(this);
    adsManager.addAdEventListener(this);
    if (ENABLE_PRELOADING) {
      ImaSdkFactory imaSdkFactory = ImaSdkFactory.getInstance();
      AdsRenderingSettings adsRenderingSettings = imaSdkFactory.createAdsRenderingSettings();
      adsRenderingSettings.setEnablePreloading(true);
      adsManager.init(adsRenderingSettings);
      if (DEBUG) {
        Log.d(TAG, "Initialized with preloading");
      }
    } else {
      adsManager.init();
      if (DEBUG) {
        Log.d(TAG, "Initialized without preloading");
      }
    }
    long[] adGroupTimesUs = getAdGroupTimesUs(adsManager.getAdCuePoints());
    adPlaybackState = new AdPlaybackState(adGroupTimesUs);
    updateAdPlaybackState();
  }

  // AdEvent.AdEventListener implementation.

  @Override
  public void onAdEvent(AdEvent adEvent) {
    AdEventType adEventType = adEvent.getType();
    boolean isLogAdEvent = adEventType == AdEventType.LOG;
    if (DEBUG || isLogAdEvent) {
      Log.w(TAG, "onAdEvent: " + adEventType);
      if (isLogAdEvent) {
        for (Map.Entry<String, String> entry : adEvent.getAdData().entrySet()) {
          Log.w(TAG, "  " + entry.getKey() + ": " + entry.getValue());
        }
      }
    }
    if (adsManager == null) {
      Log.w(TAG, "Dropping ad event after release: " + adEvent);
      return;
    }
    Ad ad = adEvent.getAd();
    switch (adEvent.getType()) {
      case LOADED:
        // The ad position is not always accurate when using preloading. See [Internal: b/62613240].
        AdPodInfo adPodInfo = ad.getAdPodInfo();
        int podIndex = adPodInfo.getPodIndex();
        adGroupIndex = podIndex == -1 ? adPlaybackState.adGroupCount - 1 : podIndex;
        int adPosition = adPodInfo.getAdPosition();
        int adCountInAdGroup = adPodInfo.getTotalAds();
        adsManager.start();
        if (DEBUG) {
          Log.d(TAG, "Loaded ad " + adPosition + " of " + adCountInAdGroup + " in ad group "
              + adGroupIndex);
        }
        adPlaybackState.setAdCount(adGroupIndex, adCountInAdGroup);
        updateAdPlaybackState();
        break;
      case CONTENT_PAUSE_REQUESTED:
        // After CONTENT_PAUSE_REQUESTED, IMA will playAd/pauseAd/stopAd to show one or more ads
        // before sending CONTENT_RESUME_REQUESTED.
        imaPausedContent = true;
        pauseContentInternal();
        break;
      case TAPPED:
        if (eventListener != null) {
          eventListener.onAdTapped();
        }
        break;
      case CLICKED:
        if (eventListener != null) {
          eventListener.onAdClicked();
        }
        break;
      case CONTENT_RESUME_REQUESTED:
        imaPausedContent = false;
        resumeContentInternal();
        break;
      case ALL_ADS_COMPLETED:
        // Do nothing. The ads manager will be released when the source is released.
      default:
        break;
    }
  }

  // AdErrorEvent.AdErrorListener implementation.

  @Override
  public void onAdError(AdErrorEvent adErrorEvent) {
    if (DEBUG) {
      Log.d(TAG, "onAdError " + adErrorEvent);
    }
    if (adsManager == null) {
      adPlaybackState = new AdPlaybackState(new long[0]);
      updateAdPlaybackState();
    }
    if (eventListener != null) {
      IOException exception = new IOException("Ad error: " + adErrorEvent, adErrorEvent.getError());
      eventListener.onLoadError(exception);
    }
  }

  // ContentProgressProvider implementation.

  @Override
  public VideoProgressUpdate getContentProgress() {
    if (player == null) {
      return lastContentProgress;
    } else if (pendingContentPositionMs != C.TIME_UNSET) {
      sentPendingContentPositionMs = true;
      return new VideoProgressUpdate(pendingContentPositionMs, contentDurationMs);
    } else if (fakeContentProgressElapsedRealtimeMs != C.TIME_UNSET) {
      long elapsedSinceEndMs = SystemClock.elapsedRealtime() - fakeContentProgressElapsedRealtimeMs;
      long fakePositionMs = fakeContentProgressOffsetMs + elapsedSinceEndMs;
      return new VideoProgressUpdate(fakePositionMs, contentDurationMs);
    } else if (playingAd || contentDurationMs == C.TIME_UNSET) {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    } else {
      return new VideoProgressUpdate(player.getCurrentPosition(), contentDurationMs);
    }
  }

  // VideoAdPlayer implementation.

  @Override
  public VideoProgressUpdate getAdProgress() {
    if (player == null) {
      return lastAdProgress;
    } else if (!playingAd) {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    } else {
      return new VideoProgressUpdate(player.getCurrentPosition(), player.getDuration());
    }
  }

  @Override
  public void loadAd(String adUriString) {
    if (DEBUG) {
      Log.d(TAG, "loadAd in ad group " + adGroupIndex);
    }
    adPlaybackState.addAdUri(adGroupIndex, Uri.parse(adUriString));
    updateAdPlaybackState();
  }

  @Override
  public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
    adCallbacks.add(videoAdPlayerCallback);
  }

  @Override
  public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
    adCallbacks.remove(videoAdPlayerCallback);
  }

  @Override
  public void playAd() {
    if (DEBUG) {
      Log.d(TAG, "playAd");
    }
    if (player == null) {
      // Sometimes messages from IMA arrive after detaching the player. See [Internal: b/63801642].
      Log.w(TAG, "Unexpected playAd while detached");
    } else if (!player.getPlayWhenReady()) {
      playWhenReadyOverriddenForAds = true;
      player.setPlayWhenReady(true);
    }
    if (imaPlayingAd && !imaPausedInAd) {
      // Work around an issue where IMA does not always call stopAd before resuming content.
      // See [Internal: b/38354028, b/63320878].
      Log.w(TAG, "Unexpected playAd without stopAd");
    }
    if (!imaPlayingAd) {
      imaPlayingAd = true;
      for (VideoAdPlayerCallback callback : adCallbacks) {
        callback.onPlay();
      }
    } else if (imaPausedInAd) {
      imaPausedInAd = false;
      for (VideoAdPlayerCallback callback : adCallbacks) {
        callback.onResume();
      }
    }
  }

  @Override
  public void stopAd() {
    if (DEBUG) {
      Log.d(TAG, "stopAd");
    }
    if (player == null) {
      // Sometimes messages from IMA arrive after detaching the player. See [Internal: b/63801642].
      Log.w(TAG, "Unexpected stopAd while detached");
    }
    if (!imaPlayingAd) {
      Log.w(TAG, "Unexpected stopAd");
      return;
    }
    stopAdInternal();
  }

  @Override
  public void pauseAd() {
    if (DEBUG) {
      Log.d(TAG, "pauseAd");
    }
    if (!imaPlayingAd) {
      // This method is called after content is resumed.
      return;
    }
    imaPausedInAd = true;
    for (VideoAdPlayerCallback callback : adCallbacks) {
      callback.onPause();
    }
  }

  @Override
  public void resumeAd() {
    // This method is never called. See [Internal: b/18931719].
    throw new IllegalStateException();
  }

  // Player.EventListener implementation.

  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest) {
    if (timeline.isEmpty()) {
      // The player is being re-prepared and this source will be released.
      return;
    }
    Assertions.checkArgument(timeline.getPeriodCount() == 1);
    this.timeline = timeline;
    contentDurationMs = C.usToMs(timeline.getPeriod(0, period).durationUs);
    updateImaStateForPlayerState();
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    // Do nothing.
  }

  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (adsManager == null) {
      return;
    }

    if (!imaPlayingAd && playbackState == Player.STATE_BUFFERING && playWhenReady) {
      checkForContentComplete();
    } else if (imaPlayingAd && playbackState == Player.STATE_ENDED) {
      // IMA is waiting for the ad playback to finish so invoke the callback now.
      // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
      for (VideoAdPlayerCallback callback : adCallbacks) {
        callback.onEnded();
      }
    }
  }

  @Override
  public void onRepeatModeChanged(int repeatMode) {
    // Do nothing.
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    if (playingAd) {
      for (VideoAdPlayerCallback callback : adCallbacks) {
        callback.onError();
      }
    }
  }

  @Override
  public void onPositionDiscontinuity() {
    if (adsManager == null) {
      return;
    }
    if (!playingAd && !player.isPlayingAd()) {
      checkForContentComplete();
      if (sentContentComplete) {
        for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
          if (adPlaybackState.adGroupTimesUs[i] != C.TIME_END_OF_SOURCE) {
            adPlaybackState.playedAdGroup(i);
          }
        }
        updateAdPlaybackState();
      } else {
        long positionMs = player.getCurrentPosition();
        timeline.getPeriod(0, period);
        if (period.getAdGroupIndexForPositionUs(C.msToUs(positionMs)) != C.INDEX_UNSET) {
          sentPendingContentPositionMs = false;
          pendingContentPositionMs = positionMs;
        }
      }
    } else {
      updateImaStateForPlayerState();
    }
  }

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    // Do nothing.
  }

  // Internal methods.

  private void requestAds() {
    AdsRequest request = imaSdkFactory.createAdsRequest();
    request.setAdTagUrl(adTagUri.toString());
    request.setAdDisplayContainer(adDisplayContainer);
    request.setContentProgressProvider(this);
    adsLoader.requestAds(request);
  }

  private void updateImaStateForPlayerState() {
    boolean wasPlayingAd = playingAd;
    playingAd = player.isPlayingAd();
    if (!playingAd && playWhenReadyOverriddenForAds) {
      playWhenReadyOverriddenForAds = false;
      player.setPlayWhenReady(false);
    }
    if (!sentContentComplete) {
      boolean adFinished = (wasPlayingAd && !playingAd)
          || playingAdIndexInAdGroup != player.getCurrentAdIndexInAdGroup();
      if (adFinished) {
        // IMA is waiting for the ad playback to finish so invoke the callback now.
        // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
        for (VideoAdPlayerCallback callback : adCallbacks) {
          callback.onEnded();
        }
      }
      if (!wasPlayingAd && playingAd) {
        int adGroupIndex = player.getCurrentAdGroupIndex();
        // IMA hasn't sent CONTENT_PAUSE_REQUESTED yet, so fake the content position.
        Assertions.checkState(fakeContentProgressElapsedRealtimeMs == C.TIME_UNSET);
        fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime();
        fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
        if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
          fakeContentProgressOffsetMs = contentDurationMs;
        }
      }
    }
    playingAdIndexInAdGroup = playingAd ? player.getCurrentAdIndexInAdGroup() : C.INDEX_UNSET;
  }

  private void resumeContentInternal() {
    if (imaPlayingAd) {
      if (DEBUG) {
        Log.d(TAG, "Unexpected CONTENT_RESUME_REQUESTED without stopAd");
      }
    }
    if (playingAd && adGroupIndex != C.INDEX_UNSET) {
      adPlaybackState.playedAdGroup(adGroupIndex);
      adGroupIndex = C.INDEX_UNSET;
      updateAdPlaybackState();
    }
    clearFlags();
  }

  private void pauseContentInternal() {
    if (sentPendingContentPositionMs) {
      pendingContentPositionMs = C.TIME_UNSET;
      sentPendingContentPositionMs = false;
    }
    // IMA is requesting to pause content, so stop faking the content position.
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
    fakeContentProgressOffsetMs = C.TIME_UNSET;
    clearFlags();
  }

  private void stopAdInternal() {
    Assertions.checkState(imaPlayingAd);
    adPlaybackState.playedAd(adGroupIndex);
    updateAdPlaybackState();
    if (!playingAd) {
      adGroupIndex = C.INDEX_UNSET;
    }
    clearFlags();
  }

  private void clearFlags() {
    // If an ad is displayed, these flags will be updated in response to playAd/pauseAd/stopAd until
    // the content is resumed.
    imaPlayingAd = false;
    imaPausedInAd = false;
  }

  private void checkForContentComplete() {
    if (contentDurationMs != C.TIME_UNSET && pendingContentPositionMs == C.TIME_UNSET
        && player.getContentPosition() + END_OF_CONTENT_POSITION_THRESHOLD_MS >= contentDurationMs
        && !sentContentComplete) {
      adsLoader.contentComplete();
      if (DEBUG) {
        Log.d(TAG, "adsLoader.contentComplete");
      }
      sentContentComplete = true;
    }
  }

  private void updateAdPlaybackState() {
    // Ignore updates while detached. When a player is attached it will receive the latest state.
    if (eventListener != null) {
      eventListener.onAdPlaybackState(adPlaybackState.copy());
    }
  }

  private static long[] getAdGroupTimesUs(List<Float> cuePoints) {
    if (cuePoints.isEmpty()) {
      // If no cue points are specified, there is a preroll ad.
      return new long[] {0};
    }

    int count = cuePoints.size();
    long[] adGroupTimesUs = new long[count];
    for (int i = 0; i < count; i++) {
      double cuePoint = cuePoints.get(i);
      adGroupTimesUs[i] =
          cuePoint == -1.0 ? C.TIME_END_OF_SOURCE : (long) (C.MICROS_PER_SECOND * cuePoint);
    }
    return adGroupTimesUs;
  }

}
