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
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads ads using the IMA SDK. All methods are called on the main thread.
 */
/* package */ final class ImaAdsLoader implements ExoPlayer.EventListener, VideoAdPlayer,
    ContentProgressProvider, AdErrorListener, AdsLoadedListener, AdEventListener {

  private static final boolean DEBUG = false;
  private static final String TAG = "ImaAdsLoader";

  /**
   * Listener for ad loader events. All methods are called on the main thread.
   */
  public interface EventListener {

    /**
     * Called when the times of ad groups are known.
     *
     * @param adGroupTimesUs The times of ad groups, in microseconds.
     */
    void onAdGroupTimesUsLoaded(long[] adGroupTimesUs);

    /**
     * Called when an ad group has been played to the end.
     *
     * @param adGroupIndex The index of the ad group.
     */
    void onAdGroupPlayedToEnd(int adGroupIndex);

    /**
     * Called when the URI for the media of an ad has been loaded.
     *
     * @param adGroupIndex The index of the ad group containing the ad with the media URI.
     * @param adIndexInAdGroup The index of the ad in its ad group.
     * @param uri The URI for the ad's media.
     */
    void onAdUriLoaded(int adGroupIndex, int adIndexInAdGroup, Uri uri);

    /**
     * Called when an ad group has loaded.
     *
     * @param adGroupIndex The index of the ad group containing the ad.
     * @param adCountInAdGroup The number of ads in the ad group.
     */
    void onAdGroupLoaded(int adGroupIndex, int adCountInAdGroup);

    /**
     * Called when there was an error loading ads.
     *
     * @param error The error.
     */
    void onLoadError(IOException error);

  }

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

  private final EventListener eventListener;
  private final ExoPlayer player;
  private final Timeline.Period period;
  private final List<VideoAdPlayerCallback> adCallbacks;
  private final AdsLoader adsLoader;

  private AdsManager adsManager;
  private long[] adGroupTimesUs;
  private int[] adsLoadedInAdGroup;
  private Timeline timeline;
  private long contentDurationMs;

  private boolean released;

  // Fields tracking IMA's state.

  /**
   * The index of the current ad group that IMA is loading.
   */
  private int adGroupIndex;
  /**
   * If {@link #playingAdGroupIndex} is set, stores whether IMA has called {@link #playAd()} and not
   * {@link #stopAd()}.
   */
  private boolean playingAd;
  /**
   * If {@link #playingAdGroupIndex} is set, stores whether IMA has called {@link #pauseAd()} since
   * a preceding call to {@link #playAd()} for the current ad.
   */
  private boolean pausedInAd;
  /**
   * Whether {@link AdsLoader#contentComplete()} has been called since starting ad playback.
   */
  private boolean sentContentComplete;

  // Fields tracking the player/loader state.

  /**
   * If the player is playing an ad, stores the ad group index. {@link C#INDEX_UNSET} otherwise.
   */
  private int playingAdGroupIndex;
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
   * Stores the pending content position when a seek operation was intercepted to play an ad.
   */
  private long pendingContentPositionMs;
  /**
   * Whether {@link #getContentProgress()} has sent {@link #pendingContentPositionMs} to IMA.
   */
  private boolean sentPendingContentPositionMs;

  /**
   * Creates a new IMA ads loader.
   *
   * @param context The context.
   * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
   *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
   *     more information.
   * @param adUiViewGroup A {@link ViewGroup} on top of the player that will show any ad UI.
   * @param imaSdkSettings {@link ImaSdkSettings} used to configure the IMA SDK, or {@code null} to
   *     use the default settings. If set, the player type and version fields may be overwritten.
   * @param player The player instance that will play the loaded ad schedule.
   * @param eventListener Listener for ad loader events.
   */
  public ImaAdsLoader(Context context, Uri adTagUri, ViewGroup adUiViewGroup,
      ImaSdkSettings imaSdkSettings, ExoPlayer player, EventListener eventListener) {
    this.eventListener = eventListener;
    this.player = player;
    period = new Timeline.Period();
    adCallbacks = new ArrayList<>(1);

    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
    pendingContentPositionMs = C.TIME_UNSET;
    adGroupIndex = C.INDEX_UNSET;
    contentDurationMs = C.TIME_UNSET;

    player.addListener(this);

    ImaSdkFactory imaSdkFactory = ImaSdkFactory.getInstance();
    AdDisplayContainer adDisplayContainer = imaSdkFactory.createAdDisplayContainer();
    adDisplayContainer.setPlayer(this);
    adDisplayContainer.setAdContainer(adUiViewGroup);

    if (imaSdkSettings == null) {
      imaSdkSettings = imaSdkFactory.createImaSdkSettings();
    }
    imaSdkSettings.setPlayerType(IMA_SDK_SETTINGS_PLAYER_TYPE);
    imaSdkSettings.setPlayerVersion(IMA_SDK_SETTINGS_PLAYER_VERSION);

    AdsRequest request = imaSdkFactory.createAdsRequest();
    request.setAdTagUrl(adTagUri.toString());
    request.setAdDisplayContainer(adDisplayContainer);
    request.setContentProgressProvider(this);

    adsLoader = imaSdkFactory.createAdsLoader(context, imaSdkSettings);
    adsLoader.addAdErrorListener(this);
    adsLoader.addAdsLoadedListener(this);
    adsLoader.requestAds(request);
  }

  /**
   * Releases the loader. Must be called when the instance is no longer needed.
   */
  public void release() {
    if (adsManager != null) {
      adsManager.destroy();
      adsManager = null;
    }
    player.removeListener(this);
    released = true;
  }

  // AdsLoader.AdsLoadedListener implementation.

  @Override
  public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
    adsManager = adsManagerLoadedEvent.getAdsManager();
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
    adGroupTimesUs = getAdGroupTimesUs(adsManager.getAdCuePoints());
    adsLoadedInAdGroup = new int[adGroupTimesUs.length];
    eventListener.onAdGroupTimesUsLoaded(adGroupTimesUs);
  }

  // AdEvent.AdEventListener implementation.

  @Override
  public void onAdEvent(AdEvent adEvent) {
    Ad ad = adEvent.getAd();
    if (DEBUG) {
      Log.d(TAG, "onAdEvent " + adEvent.getType());
    }
    if (released) {
      // The ads manager may pass CONTENT_RESUME_REQUESTED after it is destroyed.
      return;
    }
    switch (adEvent.getType()) {
      case LOADED:
        // The ad position is not always accurate when using preloading. See [Internal: b/62613240].
        AdPodInfo adPodInfo = ad.getAdPodInfo();
        int podIndex = adPodInfo.getPodIndex();
        adGroupIndex = podIndex == -1 ? adGroupTimesUs.length - 1 : podIndex;
        int adPosition = adPodInfo.getAdPosition();
        int adCountInAdGroup = adPodInfo.getTotalAds();
        adsManager.start();
        if (DEBUG) {
          Log.d(TAG, "Loaded ad " + adPosition + " of " + adCountInAdGroup + " in ad group "
              + adGroupIndex);
        }
        eventListener.onAdGroupLoaded(adGroupIndex, adCountInAdGroup);
        break;
      case CONTENT_PAUSE_REQUESTED:
        // After CONTENT_PAUSE_REQUESTED, IMA will playAd/pauseAd/stopAd to show one or more ads
        // before sending CONTENT_RESUME_REQUESTED.
        pauseContentInternal();
        break;
      case SKIPPED: // Fall through.
      case CONTENT_RESUME_REQUESTED:
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
    IOException exception = new IOException("Ad error: " + adErrorEvent, adErrorEvent.getError());
    eventListener.onLoadError(exception);
    // TODO: Provide a timeline to the player if it doesn't have one yet, so the content can play.
  }

  // ContentProgressProvider implementation.

  @Override
  public VideoProgressUpdate getContentProgress() {
    if (pendingContentPositionMs != C.TIME_UNSET) {
      sentPendingContentPositionMs = true;
      return new VideoProgressUpdate(pendingContentPositionMs, contentDurationMs);
    }
    if (fakeContentProgressElapsedRealtimeMs != C.TIME_UNSET) {
      long adGroupTimeMs = C.usToMs(adGroupTimesUs[adGroupIndex]);
      if (adGroupTimeMs == C.TIME_END_OF_SOURCE) {
        adGroupTimeMs = contentDurationMs;
      }
      long elapsedSinceEndMs = SystemClock.elapsedRealtime() - fakeContentProgressElapsedRealtimeMs;
      return new VideoProgressUpdate(adGroupTimeMs + elapsedSinceEndMs, contentDurationMs);
    }
    if (player.isPlayingAd() || contentDurationMs == C.TIME_UNSET) {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    }
    return new VideoProgressUpdate(player.getCurrentPosition(), contentDurationMs);
  }

  // VideoAdPlayer implementation.

  @Override
  public VideoProgressUpdate getAdProgress() {
    if (!player.isPlayingAd()) {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    }
    return new VideoProgressUpdate(player.getCurrentPosition(), player.getDuration());
  }

  @Override
  public void loadAd(String adUriString) {
    int adIndexInAdGroup = adsLoadedInAdGroup[adGroupIndex]++;
    if (DEBUG) {
      Log.d(TAG, "loadAd at index " + adIndexInAdGroup + " in ad group " + adGroupIndex);
    }
    eventListener.onAdUriLoaded(adGroupIndex, adIndexInAdGroup, Uri.parse(adUriString));
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
    if (playingAd && !pausedInAd) {
      // Work around an issue where IMA does not always call stopAd before resuming content.
      // See [Internal: b/38354028].
      if (DEBUG) {
        Log.d(TAG, "Unexpected playAd without stopAd");
      }
      stopAdInternal();
    }
    player.setPlayWhenReady(true);
    if (!playingAd) {
      playingAd = true;
      for (VideoAdPlayerCallback callback : adCallbacks) {
        callback.onPlay();
      }
    } else if (pausedInAd) {
      pausedInAd = false;
      for (VideoAdPlayerCallback callback : adCallbacks) {
        callback.onResume();
      }
    }
  }

  @Override
  public void stopAd() {
    if (!playingAd) {
      if (DEBUG) {
        Log.d(TAG, "Ignoring unexpected stopAd");
      }
      return;
    }
    if (DEBUG) {
      Log.d(TAG, "stopAd");
    }
    stopAdInternal();
  }

  @Override
  public void pauseAd() {
    if (DEBUG) {
      Log.d(TAG, "pauseAd");
    }
    if (released || !playingAd) {
      // This method is called after content is resumed, and may also be called after release.
      return;
    }
    pausedInAd = true;
    player.setPlayWhenReady(false);
    for (VideoAdPlayerCallback callback : adCallbacks) {
      callback.onPause();
    }
  }

  @Override
  public void resumeAd() {
    // This method is never called. See [Internal: b/18931719].
    throw new IllegalStateException();
  }

  // ExoPlayer.EventListener implementation.

  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest) {
    if (timeline.isEmpty()) {
      // The player is being re-prepared and this source will be released.
      return;
    }
    Assertions.checkArgument(timeline.getPeriodCount() == 1);
    this.timeline = timeline;
    contentDurationMs = C.usToMs(timeline.getPeriod(0, period).durationUs);
    if (player.isPlayingAd()) {
      playingAdGroupIndex = player.getCurrentAdGroupIndex();
      playingAdIndexInAdGroup = player.getCurrentAdIndexInAdGroup();
    }
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
    if (!playingAd && playbackState == ExoPlayer.STATE_BUFFERING && playWhenReady) {
      checkForContentComplete();
    } else if (playingAd && playbackState == ExoPlayer.STATE_ENDED) {
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
    if (player.isPlayingAd()) {
      for (VideoAdPlayerCallback callback : adCallbacks) {
        callback.onError();
      }
    }
  }

  @Override
  public void onPositionDiscontinuity() {
    if (!player.isPlayingAd() && playingAdGroupIndex == C.INDEX_UNSET) {
      long positionUs = C.msToUs(player.getCurrentPosition());
      int adGroupIndex = timeline.getPeriod(0, period).getAdGroupIndexForPositionUs(positionUs);
      if (adGroupIndex != C.INDEX_UNSET) {
        sentPendingContentPositionMs = false;
        pendingContentPositionMs = player.getCurrentPosition();
      }
      return;
    }

    boolean adFinished = (!player.isPlayingAd() && playingAdGroupIndex != C.INDEX_UNSET)
        || (player.isPlayingAd() && playingAdIndexInAdGroup != player.getCurrentAdIndexInAdGroup());
    if (adFinished) {
      // IMA is waiting for the ad playback to finish so invoke the callback now.
      // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
      for (VideoAdPlayerCallback callback : adCallbacks) {
        callback.onEnded();
      }
    }

    if (player.isPlayingAd() && playingAdGroupIndex == C.INDEX_UNSET) {
      player.setPlayWhenReady(false);
      // IMA hasn't sent CONTENT_PAUSE_REQUESTED yet, so fake the content position.
      Assertions.checkState(fakeContentProgressElapsedRealtimeMs == C.TIME_UNSET);
      fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime();
      if (adGroupIndex == adGroupTimesUs.length - 1) {
        adsLoader.contentComplete();
        if (DEBUG) {
          Log.d(TAG, "adsLoader.contentComplete");
        }
      }
    }
    boolean isPlayingAd = player.isPlayingAd();
    playingAdGroupIndex = isPlayingAd ? player.getCurrentAdGroupIndex() : C.INDEX_UNSET;
    playingAdIndexInAdGroup = isPlayingAd ? player.getCurrentAdIndexInAdGroup() : C.INDEX_UNSET;
  }

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    // Do nothing.
  }

  // Internal methods.

  /**
   * Resumes the player, ensuring the current period is a content period by seeking if necessary.
   */
  private void resumeContentInternal() {
    if (contentDurationMs != C.TIME_UNSET) {
      if (playingAd) {
        // Work around an issue where IMA does not always call stopAd before resuming content.
        // See [Internal: b/38354028].
        if (DEBUG) {
          Log.d(TAG, "Unexpected CONTENT_RESUME_REQUESTED without stopAd");
        }
        stopAdInternal();
      }
    }
    player.setPlayWhenReady(true);
    clearFlags();
  }

  private void pauseContentInternal() {
    if (sentPendingContentPositionMs) {
      pendingContentPositionMs = C.TIME_UNSET;
      sentPendingContentPositionMs = false;
    }
    // IMA is requesting to pause content, so stop faking the content position.
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
    player.setPlayWhenReady(false);
    clearFlags();
  }

  private void stopAdInternal() {
    Assertions.checkState(playingAd);
    player.setPlayWhenReady(false);
    if (!player.isPlayingAd()) {
      eventListener.onAdGroupPlayedToEnd(adGroupIndex);
      adGroupIndex = C.INDEX_UNSET;
    }
    clearFlags();
  }

  private void clearFlags() {
    // If an ad is displayed, these flags will be updated in response to playAd/pauseAd/stopAd until
    // the content is resumed.
    playingAd = false;
    pausedInAd = false;
  }

  private void checkForContentComplete() {
    if (contentDurationMs != C.TIME_UNSET
        && player.getCurrentPosition() + END_OF_CONTENT_POSITION_THRESHOLD_MS >= contentDurationMs
        && !sentContentComplete) {
      adsLoader.contentComplete();
      if (DEBUG) {
        Log.d(TAG, "adsLoader.contentComplete");
      }
      sentContentComplete = true;
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
