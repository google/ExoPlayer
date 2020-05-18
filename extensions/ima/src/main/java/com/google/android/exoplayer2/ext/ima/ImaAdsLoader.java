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

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdError.AdErrorCode;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.UiElement;
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
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link AdsLoader} using the IMA SDK. All methods must be called on the main thread.
 *
 * <p>The player instance that will play the loaded ads must be set before playback using {@link
 * #setPlayer(Player)}. If the ads loader is no longer required, it must be released by calling
 * {@link #release()}.
 *
 * <p>The IMA SDK can take into account video control overlay views when calculating ad viewability.
 * For more details see {@link AdDisplayContainer#registerVideoControlsOverlay(View)} and {@link
 * AdViewProvider#getAdOverlayViews()}.
 */
public final class ImaAdsLoader
    implements Player.EventListener,
        AdsLoader,
        VideoAdPlayer,
        ContentProgressProvider,
        AdErrorListener,
        AdsLoadedListener,
        AdEventListener {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.ima");
  }

  /** Builder for {@link ImaAdsLoader}. */
  public static final class Builder {

    /**
     * The default duration in milliseconds for which the player must buffer while preloading an ad
     * group before that ad group is skipped and marked as having failed to load.
     *
     * <p>This value should be large enough not to trigger discarding the ad when it actually might
     * load soon, but small enough so that user is not waiting for too long.
     *
     * @see #setAdPreloadTimeoutMs(long)
     */
    public static final long DEFAULT_AD_PRELOAD_TIMEOUT_MS = 10 * C.MILLIS_PER_SECOND;

    private final Context context;

    @Nullable private ImaSdkSettings imaSdkSettings;
    @Nullable private AdEventListener adEventListener;
    @Nullable private Set<UiElement> adUiElements;
    private long adPreloadTimeoutMs;
    private int vastLoadTimeoutMs;
    private int mediaLoadTimeoutMs;
    private int mediaBitrate;
    private boolean focusSkipButtonWhenAvailable;
    private ImaFactory imaFactory;

    /**
     * Creates a new builder for {@link ImaAdsLoader}.
     *
     * @param context The context;
     */
    public Builder(Context context) {
      this.context = Assertions.checkNotNull(context);
      adPreloadTimeoutMs = DEFAULT_AD_PRELOAD_TIMEOUT_MS;
      vastLoadTimeoutMs = TIMEOUT_UNSET;
      mediaLoadTimeoutMs = TIMEOUT_UNSET;
      mediaBitrate = BITRATE_UNSET;
      focusSkipButtonWhenAvailable = true;
      imaFactory = new DefaultImaFactory();
    }

    /**
     * Sets the IMA SDK settings. The provided settings instance's player type and version fields
     * may be overwritten.
     *
     * <p>If this method is not called the default settings will be used.
     *
     * @param imaSdkSettings The {@link ImaSdkSettings}.
     * @return This builder, for convenience.
     */
    public Builder setImaSdkSettings(ImaSdkSettings imaSdkSettings) {
      this.imaSdkSettings = Assertions.checkNotNull(imaSdkSettings);
      return this;
    }

    /**
     * Sets a listener for ad events that will be passed to {@link
     * AdsManager#addAdEventListener(AdEventListener)}.
     *
     * @param adEventListener The ad event listener.
     * @return This builder, for convenience.
     */
    public Builder setAdEventListener(AdEventListener adEventListener) {
      this.adEventListener = Assertions.checkNotNull(adEventListener);
      return this;
    }

    /**
     * Sets the ad UI elements to be rendered by the IMA SDK.
     *
     * @param adUiElements The ad UI elements to be rendered by the IMA SDK.
     * @return This builder, for convenience.
     * @see AdsRenderingSettings#setUiElements(Set)
     */
    public Builder setAdUiElements(Set<UiElement> adUiElements) {
      this.adUiElements = new HashSet<>(Assertions.checkNotNull(adUiElements));
      return this;
    }

    /**
     * Sets the duration in milliseconds for which the player must buffer while preloading an ad
     * group before that ad group is skipped and marked as having failed to load. Pass {@link
     * C#TIME_UNSET} if there should be no such timeout. The default value is {@value
     * DEFAULT_AD_PRELOAD_TIMEOUT_MS} ms.
     *
     * <p>The purpose of this timeout is to avoid playback getting stuck in the unexpected case that
     * the IMA SDK does not load an ad break based on the player's reported content position.
     *
     * @param adPreloadTimeoutMs The timeout buffering duration in milliseconds, or {@link
     *     C#TIME_UNSET} for no timeout.
     * @return This builder, for convenience.
     */
    public Builder setAdPreloadTimeoutMs(long adPreloadTimeoutMs) {
      Assertions.checkArgument(adPreloadTimeoutMs == C.TIME_UNSET || adPreloadTimeoutMs > 0);
      this.adPreloadTimeoutMs = adPreloadTimeoutMs;
      return this;
    }

    /**
     * Sets the VAST load timeout, in milliseconds.
     *
     * @param vastLoadTimeoutMs The VAST load timeout, in milliseconds.
     * @return This builder, for convenience.
     * @see AdsRequest#setVastLoadTimeout(float)
     */
    public Builder setVastLoadTimeoutMs(int vastLoadTimeoutMs) {
      Assertions.checkArgument(vastLoadTimeoutMs > 0);
      this.vastLoadTimeoutMs = vastLoadTimeoutMs;
      return this;
    }

    /**
     * Sets the ad media load timeout, in milliseconds.
     *
     * @param mediaLoadTimeoutMs The ad media load timeout, in milliseconds.
     * @return This builder, for convenience.
     * @see AdsRenderingSettings#setLoadVideoTimeout(int)
     */
    public Builder setMediaLoadTimeoutMs(int mediaLoadTimeoutMs) {
      Assertions.checkArgument(mediaLoadTimeoutMs > 0);
      this.mediaLoadTimeoutMs = mediaLoadTimeoutMs;
      return this;
    }

    /**
     * Sets the media maximum recommended bitrate for ads, in bps.
     *
     * @param bitrate The media maximum recommended bitrate for ads, in bps.
     * @return This builder, for convenience.
     * @see AdsRenderingSettings#setBitrateKbps(int)
     */
    public Builder setMaxMediaBitrate(int bitrate) {
      Assertions.checkArgument(bitrate > 0);
      this.mediaBitrate = bitrate;
      return this;
    }

    /**
     * Sets whether to focus the skip button (when available) on Android TV devices. The default
     * setting is {@code true}.
     *
     * @param focusSkipButtonWhenAvailable Whether to focus the skip button (when available) on
     *     Android TV devices.
     * @return This builder, for convenience.
     * @see AdsRenderingSettings#setFocusSkipButtonWhenAvailable(boolean)
     */
    public Builder setFocusSkipButtonWhenAvailable(boolean focusSkipButtonWhenAvailable) {
      this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
      return this;
    }

    @VisibleForTesting
    /* package */ Builder setImaFactory(ImaFactory imaFactory) {
      this.imaFactory = Assertions.checkNotNull(imaFactory);
      return this;
    }

    /**
     * Returns a new {@link ImaAdsLoader} for the specified ad tag.
     *
     * @param adTagUri The URI of a compatible ad tag to load. See
     *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
     *     information on compatible ad tags.
     * @return The new {@link ImaAdsLoader}.
     */
    public ImaAdsLoader buildForAdTag(Uri adTagUri) {
      return new ImaAdsLoader(
          context,
          adTagUri,
          imaSdkSettings,
          /* adsResponse= */ null,
          adPreloadTimeoutMs,
          vastLoadTimeoutMs,
          mediaLoadTimeoutMs,
          mediaBitrate,
          focusSkipButtonWhenAvailable,
          adUiElements,
          adEventListener,
          imaFactory);
    }

    /**
     * Returns a new {@link ImaAdsLoader} with the specified sideloaded ads response.
     *
     * @param adsResponse The sideloaded VAST, VMAP, or ad rules response to be used instead of
     *     making a request via an ad tag URL.
     * @return The new {@link ImaAdsLoader}.
     */
    public ImaAdsLoader buildForAdsResponse(String adsResponse) {
      return new ImaAdsLoader(
          context,
          /* adTagUri= */ null,
          imaSdkSettings,
          adsResponse,
          adPreloadTimeoutMs,
          vastLoadTimeoutMs,
          mediaLoadTimeoutMs,
          mediaBitrate,
          focusSkipButtonWhenAvailable,
          adUiElements,
          adEventListener,
          imaFactory);
    }
  }

  private static final boolean DEBUG = false;
  private static final String TAG = "ImaAdsLoader";

  private static final String IMA_SDK_SETTINGS_PLAYER_TYPE = "google/exo.ext.ima";
  private static final String IMA_SDK_SETTINGS_PLAYER_VERSION = ExoPlayerLibraryInfo.VERSION;

  /**
   * Interval at which ad progress updates are provided to the IMA SDK, in milliseconds. 100 ms is
   * the interval recommended by the IMA documentation.
   *
   * @see com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback
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

  private static final int TIMEOUT_UNSET = -1;
  private static final int BITRATE_UNSET = -1;

  /** The state of ad playback. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({IMA_AD_STATE_NONE, IMA_AD_STATE_PLAYING, IMA_AD_STATE_PAUSED})
  private @interface ImaAdState {}
  /**
   * The ad playback state when IMA is not playing an ad.
   */
  private static final int IMA_AD_STATE_NONE = 0;
  /**
   * The ad playback state when IMA has called {@link #playAd(AdMediaInfo)} and not {@link
   * #pauseAd(AdMediaInfo)}.
   */
  private static final int IMA_AD_STATE_PLAYING = 1;
  /**
   * The ad playback state when IMA has called {@link #pauseAd(AdMediaInfo)} while playing an ad.
   */
  private static final int IMA_AD_STATE_PAUSED = 2;

  @Nullable private final Uri adTagUri;
  @Nullable private final String adsResponse;
  private final long adPreloadTimeoutMs;
  private final int vastLoadTimeoutMs;
  private final int mediaLoadTimeoutMs;
  private final boolean focusSkipButtonWhenAvailable;
  private final int mediaBitrate;
  @Nullable private final Set<UiElement> adUiElements;
  @Nullable private final AdEventListener adEventListener;
  private final ImaFactory imaFactory;
  private final Timeline.Period period;
  private final Handler handler;
  private final List<VideoAdPlayerCallback> adCallbacks;
  private final AdDisplayContainer adDisplayContainer;
  private final com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader;
  private final Runnable updateAdProgressRunnable;
  private final Map<AdMediaInfo, AdInfo> adInfoByAdMediaInfo;

  private boolean wasSetPlayerCalled;
  @Nullable private Player nextPlayer;
  @Nullable private Object pendingAdRequestContext;
  private List<String> supportedMimeTypes;
  @Nullable private EventListener eventListener;
  @Nullable private Player player;
  private VideoProgressUpdate lastContentProgress;
  private VideoProgressUpdate lastAdProgress;
  private int lastVolumePercentage;

  @Nullable private AdsManager adsManager;
  private boolean initializedAdsManager;
  private boolean hasAdPlaybackState;
  @Nullable private AdLoadException pendingAdLoadError;
  private Timeline timeline;
  private long contentDurationMs;
  private AdPlaybackState adPlaybackState;

  // Fields tracking IMA's state.

  /** Whether IMA has sent an ad event to pause content since the last resume content event. */
  private boolean imaPausedContent;
  /** The current ad playback state. */
  private @ImaAdState int imaAdState;
  /** The current ad media info, or {@code null} if in state {@link #IMA_AD_STATE_NONE}. */
  @Nullable private AdMediaInfo imaAdMediaInfo;
  /** The current ad info, or {@code null} if in state {@link #IMA_AD_STATE_NONE}. */
  @Nullable private AdInfo imaAdInfo;
  /**
   * Whether {@link com.google.ads.interactivemedia.v3.api.AdsLoader#contentComplete()} has been
   * called since starting ad playback.
   */
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
   * If a content period has finished but IMA has not yet called {@link #playAd(AdMediaInfo)},
   * stores the value of {@link SystemClock#elapsedRealtime()} when the content stopped playing.
   * This can be used to determine a fake, increasing content position. {@link C#TIME_UNSET}
   * otherwise.
   */
  private long fakeContentProgressElapsedRealtimeMs;
  /**
   * If {@link #fakeContentProgressElapsedRealtimeMs} is set, stores the offset from which the
   * content progress should increase. {@link C#TIME_UNSET} otherwise.
   */
  private long fakeContentProgressOffsetMs;
  /** Stores the pending content position when a seek operation was intercepted to play an ad. */
  private long pendingContentPositionMs;
  /** Whether {@link #getContentProgress()} has sent {@link #pendingContentPositionMs} to IMA. */
  private boolean sentPendingContentPositionMs;
  /**
   * Stores the real time in milliseconds at which the player started buffering, possibly due to not
   * having preloaded an ad, or {@link C#TIME_UNSET} if not applicable.
   */
  private long waitingForPreloadElapsedRealtimeMs;

  /**
   * Creates a new IMA ads loader.
   *
   * <p>If you need to customize the ad request, use {@link ImaAdsLoader.Builder} instead.
   *
   * @param context The context.
   * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
   *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
   *     more information.
   */
  public ImaAdsLoader(Context context, Uri adTagUri) {
    this(
        context,
        adTagUri,
        /* imaSdkSettings= */ null,
        /* adsResponse= */ null,
        /* adPreloadTimeoutMs= */ Builder.DEFAULT_AD_PRELOAD_TIMEOUT_MS,
        /* vastLoadTimeoutMs= */ TIMEOUT_UNSET,
        /* mediaLoadTimeoutMs= */ TIMEOUT_UNSET,
        /* mediaBitrate= */ BITRATE_UNSET,
        /* focusSkipButtonWhenAvailable= */ true,
        /* adUiElements= */ null,
        /* adEventListener= */ null,
        /* imaFactory= */ new DefaultImaFactory());
  }

  @SuppressWarnings({"nullness:argument.type.incompatible", "methodref.receiver.bound.invalid"})
  private ImaAdsLoader(
      Context context,
      @Nullable Uri adTagUri,
      @Nullable ImaSdkSettings imaSdkSettings,
      @Nullable String adsResponse,
      long adPreloadTimeoutMs,
      int vastLoadTimeoutMs,
      int mediaLoadTimeoutMs,
      int mediaBitrate,
      boolean focusSkipButtonWhenAvailable,
      @Nullable Set<UiElement> adUiElements,
      @Nullable AdEventListener adEventListener,
      ImaFactory imaFactory) {
    Assertions.checkArgument(adTagUri != null || adsResponse != null);
    this.adTagUri = adTagUri;
    this.adsResponse = adsResponse;
    this.adPreloadTimeoutMs = adPreloadTimeoutMs;
    this.vastLoadTimeoutMs = vastLoadTimeoutMs;
    this.mediaLoadTimeoutMs = mediaLoadTimeoutMs;
    this.mediaBitrate = mediaBitrate;
    this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
    this.adUiElements = adUiElements;
    this.adEventListener = adEventListener;
    this.imaFactory = imaFactory;
    if (imaSdkSettings == null) {
      imaSdkSettings = imaFactory.createImaSdkSettings();
      if (DEBUG) {
        imaSdkSettings.setDebugMode(true);
      }
    }
    imaSdkSettings.setPlayerType(IMA_SDK_SETTINGS_PLAYER_TYPE);
    imaSdkSettings.setPlayerVersion(IMA_SDK_SETTINGS_PLAYER_VERSION);
    period = new Timeline.Period();
    handler = Util.createHandler(getImaLooper(), /* callback= */ null);
    adCallbacks = new ArrayList<>(/* initialCapacity= */ 1);
    adDisplayContainer = imaFactory.createAdDisplayContainer();
    adDisplayContainer.setPlayer(/* videoAdPlayer= */ this);
    adsLoader =
        imaFactory.createAdsLoader(
            context.getApplicationContext(), imaSdkSettings, adDisplayContainer);
    adsLoader.addAdErrorListener(/* adErrorListener= */ this);
    adsLoader.addAdsLoadedListener(/* adsLoadedListener= */ this);
    updateAdProgressRunnable = this::updateAdProgress;
    adInfoByAdMediaInfo = new HashMap<>();
    supportedMimeTypes = Collections.emptyList();
    lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
    fakeContentProgressOffsetMs = C.TIME_UNSET;
    pendingContentPositionMs = C.TIME_UNSET;
    waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET;
    contentDurationMs = C.TIME_UNSET;
    timeline = Timeline.EMPTY;
    adPlaybackState = AdPlaybackState.NONE;
  }

  /**
   * Returns the underlying {@code com.google.ads.interactivemedia.v3.api.AdsLoader} wrapped by
   * this instance.
   */
  public com.google.ads.interactivemedia.v3.api.AdsLoader getAdsLoader() {
    return adsLoader;
  }

  /**
   * Returns the {@link AdDisplayContainer} used by this loader.
   *
   * <p>Note: any video controls overlays registered via {@link
   * AdDisplayContainer#registerVideoControlsOverlay(View)} will be unregistered automatically when
   * the media source detaches from this instance. It is therefore necessary to re-register views
   * each time the ads loader is reused. Alternatively, provide overlay views via the {@link
   * AdsLoader.AdViewProvider} when creating the media source to benefit from automatic
   * registration.
   */
  public AdDisplayContainer getAdDisplayContainer() {
    return adDisplayContainer;
  }

  /**
   * Requests ads, if they have not already been requested. Must be called on the main thread.
   *
   * <p>Ads will be requested automatically when the player is prepared if this method has not been
   * called, so it is only necessary to call this method if you want to request ads before preparing
   * the player.
   *
   * @param adViewGroup A {@link ViewGroup} on top of the player that will show any ad UI.
   */
  public void requestAds(ViewGroup adViewGroup) {
    if (hasAdPlaybackState || adsManager != null || pendingAdRequestContext != null) {
      // Ads have already been requested.
      return;
    }
    adDisplayContainer.setAdContainer(adViewGroup);
    AdsRequest request = imaFactory.createAdsRequest();
    if (adTagUri != null) {
      request.setAdTagUrl(adTagUri.toString());
    } else {
      request.setAdsResponse(castNonNull(adsResponse));
    }
    if (vastLoadTimeoutMs != TIMEOUT_UNSET) {
      request.setVastLoadTimeout(vastLoadTimeoutMs);
    }
    request.setContentProgressProvider(this);
    pendingAdRequestContext = new Object();
    request.setUserRequestContext(pendingAdRequestContext);
    adsLoader.requestAds(request);
  }

  // AdsLoader implementation.

  @Override
  public void setPlayer(@Nullable Player player) {
    Assertions.checkState(Looper.myLooper() == getImaLooper());
    Assertions.checkState(player == null || player.getApplicationLooper() == getImaLooper());
    nextPlayer = player;
    wasSetPlayerCalled = true;
  }

  @Override
  public void setSupportedContentTypes(@C.ContentType int... contentTypes) {
    List<String> supportedMimeTypes = new ArrayList<>();
    for (@C.ContentType int contentType : contentTypes) {
      // IMA does not support Smooth Streaming ad media.
      if (contentType == C.TYPE_DASH) {
        supportedMimeTypes.add(MimeTypes.APPLICATION_MPD);
      } else if (contentType == C.TYPE_HLS) {
        supportedMimeTypes.add(MimeTypes.APPLICATION_M3U8);
      } else if (contentType == C.TYPE_OTHER) {
        supportedMimeTypes.addAll(
            Arrays.asList(
                MimeTypes.VIDEO_MP4,
                MimeTypes.VIDEO_WEBM,
                MimeTypes.VIDEO_H263,
                MimeTypes.AUDIO_MP4,
                MimeTypes.AUDIO_MPEG));
      }
    }
    this.supportedMimeTypes = Collections.unmodifiableList(supportedMimeTypes);
  }

  @Override
  public void start(EventListener eventListener, AdViewProvider adViewProvider) {
    Assertions.checkState(
        wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player.");
    player = nextPlayer;
    if (player == null) {
      return;
    }
    player.addListener(this);
    boolean playWhenReady = player.getPlayWhenReady();
    this.eventListener = eventListener;
    lastVolumePercentage = 0;
    lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    ViewGroup adViewGroup = adViewProvider.getAdViewGroup();
    adDisplayContainer.setAdContainer(adViewGroup);
    View[] adOverlayViews = adViewProvider.getAdOverlayViews();
    for (View view : adOverlayViews) {
      adDisplayContainer.registerVideoControlsOverlay(view);
    }
    maybeNotifyPendingAdLoadError();
    if (hasAdPlaybackState) {
      // Pass the ad playback state to the player, and resume ads if necessary.
      eventListener.onAdPlaybackState(adPlaybackState);
      if (adsManager != null && imaPausedContent && playWhenReady) {
        adsManager.resume();
      }
    } else if (adsManager != null) {
      adPlaybackState = new AdPlaybackState(getAdGroupTimesUs(adsManager.getAdCuePoints()));
      updateAdPlaybackState();
    } else {
      // Ads haven't loaded yet, so request them.
      requestAds(adViewGroup);
    }
  }

  @Override
  public void stop() {
    @Nullable Player player = this.player;
    if (player == null) {
      return;
    }
    if (adsManager != null && imaPausedContent) {
      adsManager.pause();
      adPlaybackState =
          adPlaybackState.withAdResumePositionUs(
              playingAd ? C.msToUs(player.getCurrentPosition()) : 0);
    }
    lastVolumePercentage = getVolume();
    lastAdProgress = getAdVideoProgressUpdate();
    lastContentProgress = getContentProgress();
    adDisplayContainer.unregisterAllVideoControlsOverlays();
    player.removeListener(this);
    this.player = null;
    eventListener = null;
  }

  @Override
  public void release() {
    pendingAdRequestContext = null;
    if (adsManager != null) {
      adsManager.removeAdErrorListener(this);
      adsManager.removeAdEventListener(this);
      if (adEventListener != null) {
        adsManager.removeAdEventListener(adEventListener);
      }
      adsManager.destroy();
      adsManager = null;
    }
    adsLoader.removeAdsLoadedListener(/* adsLoadedListener= */ this);
    adsLoader.removeAdErrorListener(/* adErrorListener= */ this);
    imaPausedContent = false;
    imaAdState = IMA_AD_STATE_NONE;
    imaAdMediaInfo = null;
    stopUpdatingAdProgress();
    imaAdInfo = null;
    pendingAdLoadError = null;
    adPlaybackState = AdPlaybackState.NONE;
    hasAdPlaybackState = false;
    updateAdPlaybackState();
  }

  @Override
  public void handlePrepareError(int adGroupIndex, int adIndexInAdGroup, IOException exception) {
    if (player == null) {
      return;
    }
    try {
      handleAdPrepareError(adGroupIndex, adIndexInAdGroup, exception);
    } catch (Exception e) {
      maybeNotifyInternalError("handlePrepareError", e);
    }
  }

  // com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener implementation.

  @Override
  public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
    AdsManager adsManager = adsManagerLoadedEvent.getAdsManager();
    if (!Util.areEqual(pendingAdRequestContext, adsManagerLoadedEvent.getUserRequestContext())) {
      adsManager.destroy();
      return;
    }
    pendingAdRequestContext = null;
    this.adsManager = adsManager;
    adsManager.addAdErrorListener(this);
    adsManager.addAdEventListener(this);
    if (adEventListener != null) {
      adsManager.addAdEventListener(adEventListener);
    }
    if (player != null) {
      // If a player is attached already, start playback immediately.
      try {
        adPlaybackState = new AdPlaybackState(getAdGroupTimesUs(adsManager.getAdCuePoints()));
        hasAdPlaybackState = true;
        updateAdPlaybackState();
      } catch (Exception e) {
        maybeNotifyInternalError("onAdsManagerLoaded", e);
      }
    }
  }

  // AdEvent.AdEventListener implementation.

  @Override
  public void onAdEvent(AdEvent adEvent) {
    AdEventType adEventType = adEvent.getType();
    if (DEBUG && adEventType != AdEventType.AD_PROGRESS) {
      Log.d(TAG, "onAdEvent: " + adEventType);
    }
    if (adsManager == null) {
      // Drop events after release.
      return;
    }
    try {
      handleAdEvent(adEvent);
    } catch (Exception e) {
      maybeNotifyInternalError("onAdEvent", e);
    }
  }

  // AdErrorEvent.AdErrorListener implementation.

  @Override
  public void onAdError(AdErrorEvent adErrorEvent) {
    AdError error = adErrorEvent.getError();
    if (DEBUG) {
      Log.d(TAG, "onAdError", error);
    }
    if (adsManager == null) {
      // No ads were loaded, so allow playback to start without any ads.
      pendingAdRequestContext = null;
      adPlaybackState = AdPlaybackState.NONE;
      hasAdPlaybackState = true;
      updateAdPlaybackState();
    } else if (isAdGroupLoadError(error)) {
      try {
        handleAdGroupLoadError(error);
      } catch (Exception e) {
        maybeNotifyInternalError("onAdError", e);
      }
    }
    if (pendingAdLoadError == null) {
      pendingAdLoadError = AdLoadException.createForAllAds(error);
    }
    maybeNotifyPendingAdLoadError();
  }

  // ContentProgressProvider implementation.

  @Override
  public VideoProgressUpdate getContentProgress() {
    VideoProgressUpdate videoProgressUpdate = getContentVideoProgressUpdate();
    if (DEBUG) {
      Log.d(TAG, "Content progress: " + videoProgressUpdate);
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
    }

    return videoProgressUpdate;
  }

  // VideoAdPlayer implementation.

  @Override
  public VideoProgressUpdate getAdProgress() {
    throw new IllegalStateException("Unexpected call to getAdProgress when using preloading");
  }

  @Override
  public int getVolume() {
    @Nullable Player player = this.player;
    if (player == null) {
      return lastVolumePercentage;
    }

    @Nullable Player.AudioComponent audioComponent = player.getAudioComponent();
    if (audioComponent != null) {
      return (int) (audioComponent.getVolume() * 100);
    }

    // Check for a selected track using an audio renderer.
    TrackSelectionArray trackSelections = player.getCurrentTrackSelections();
    for (int i = 0; i < player.getRendererCount() && i < trackSelections.length; i++) {
      if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO && trackSelections.get(i) != null) {
        return 100;
      }
    }
    return 0;
  }

  @Override
  public void loadAd(AdMediaInfo adMediaInfo, AdPodInfo adPodInfo) {
    try {
      if (DEBUG) {
        Log.d(TAG, "loadAd " + getAdMediaInfoString(adMediaInfo) + ", ad pod " + adPodInfo);
      }
      if (adsManager == null) {
        // Drop events after release.
        return;
      }
      int adGroupIndex = getAdGroupIndexForAdPod(adPodInfo);
      int adIndexInAdGroup = adPodInfo.getAdPosition() - 1;
      AdInfo adInfo = new AdInfo(adGroupIndex, adIndexInAdGroup);
      adInfoByAdMediaInfo.put(adMediaInfo, adInfo);
      if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) {
        // We have already marked this ad as having failed to load, so ignore the request. IMA will
        // timeout after its media load timeout.
        return;
      }
      AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adInfo.adGroupIndex];
      if (adGroup.count == C.LENGTH_UNSET) {
        adPlaybackState =
            adPlaybackState.withAdCount(
                adInfo.adGroupIndex, Math.max(adPodInfo.getTotalAds(), adGroup.states.length));
        adGroup = adPlaybackState.adGroups[adInfo.adGroupIndex];
      }
      for (int i = 0; i < adIndexInAdGroup; i++) {
        // Any preceding ads that haven't loaded are not going to load.
        if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
          adPlaybackState =
              adPlaybackState.withAdLoadError(
                  /* adGroupIndex= */ adGroupIndex, /* adIndexInAdGroup= */ i);
        }
      }
      Uri adUri = Uri.parse(adMediaInfo.getUrl());
      adPlaybackState =
          adPlaybackState.withAdUri(adInfo.adGroupIndex, adInfo.adIndexInAdGroup, adUri);
      updateAdPlaybackState();
    } catch (Exception e) {
      maybeNotifyInternalError("loadAd", e);
    }
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
  public void playAd(AdMediaInfo adMediaInfo) {
    if (DEBUG) {
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
      imaAdInfo = Assertions.checkNotNull(adInfoByAdMediaInfo.get(adMediaInfo));
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
      Assertions.checkState(adMediaInfo.equals(imaAdMediaInfo));
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onResume(adMediaInfo);
      }
    }
    if (!Assertions.checkNotNull(player).getPlayWhenReady()) {
      Assertions.checkNotNull(adsManager).pause();
    }
  }

  @Override
  public void stopAd(AdMediaInfo adMediaInfo) {
    if (DEBUG) {
      Log.d(TAG, "stopAd " + getAdMediaInfoString(adMediaInfo));
    }
    if (adsManager == null) {
      // Drop event after release.
      return;
    }

    Assertions.checkNotNull(player);
    Assertions.checkState(imaAdState != IMA_AD_STATE_NONE);
    try {
      stopAdInternal();
    } catch (Exception e) {
      maybeNotifyInternalError("stopAd", e);
    }
  }

  @Override
  public void pauseAd(AdMediaInfo adMediaInfo) {
    if (DEBUG) {
      Log.d(TAG, "pauseAd " + getAdMediaInfoString(adMediaInfo));
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      // This method is called after content is resumed.
      return;
    }
    Assertions.checkState(adMediaInfo.equals(imaAdMediaInfo));
    imaAdState = IMA_AD_STATE_PAUSED;
    for (int i = 0; i < adCallbacks.size(); i++) {
      adCallbacks.get(i).onPause(adMediaInfo);
    }
  }

  // Player.EventListener implementation.

  @Override
  public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
    if (timeline.isEmpty()) {
      // The player is being reset or contains no media.
      return;
    }
    Assertions.checkArgument(timeline.getPeriodCount() == 1);
    this.timeline = timeline;
    long contentDurationUs = timeline.getPeriod(/* periodIndex= */ 0, period).durationUs;
    contentDurationMs = C.usToMs(contentDurationUs);
    if (contentDurationUs != C.TIME_UNSET) {
      adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs);
    }
    if (!initializedAdsManager && adsManager != null) {
      initializedAdsManager = true;
      initializeAdsManager(adsManager);
    }
    handleTimelineOrPositionChanged();
  }

  @Override
  public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    handleTimelineOrPositionChanged();
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
    @Nullable Player player = this.player;
    if (adsManager == null || player == null) {
      return;
    }

    if (playbackState == Player.STATE_BUFFERING && !player.isPlayingAd()) {
      // Check whether we are waiting for an ad to preload.
      int adGroupIndex = getLoadingAdGroupIndex();
      if (adGroupIndex == C.INDEX_UNSET) {
        return;
      }
      AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
      if (adGroup.count != C.LENGTH_UNSET
          && adGroup.count != 0
          && adGroup.states[0] != AdPlaybackState.AD_STATE_UNAVAILABLE) {
        // An ad is available already so we must be buffering for some other reason.
        return;
      }
      long adGroupTimeMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
      long contentPositionMs = getContentPeriodPositionMs(player, timeline, period);
      long timeUntilAdMs = adGroupTimeMs - contentPositionMs;
      if (timeUntilAdMs < adPreloadTimeoutMs) {
        waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime();
      }
    } else if (playbackState == Player.STATE_READY) {
      waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET;
    }

    if (imaAdState == IMA_AD_STATE_PLAYING && !playWhenReady) {
      adsManager.pause();
      return;
    }

    if (imaAdState == IMA_AD_STATE_PAUSED && playWhenReady) {
      adsManager.resume();
      return;
    }

    handlePlayerStateChanged(playWhenReady, playbackState);
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    if (imaAdState != IMA_AD_STATE_NONE) {
      AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onError(adMediaInfo);
      }
    }
  }

  // Internal methods.

  private void initializeAdsManager(AdsManager adsManager) {
    AdsRenderingSettings adsRenderingSettings = imaFactory.createAdsRenderingSettings();
    adsRenderingSettings.setEnablePreloading(true);
    adsRenderingSettings.setMimeTypes(supportedMimeTypes);
    if (mediaLoadTimeoutMs != TIMEOUT_UNSET) {
      adsRenderingSettings.setLoadVideoTimeout(mediaLoadTimeoutMs);
    }
    if (mediaBitrate != BITRATE_UNSET) {
      adsRenderingSettings.setBitrateKbps(mediaBitrate / 1000);
    }
    adsRenderingSettings.setFocusSkipButtonWhenAvailable(focusSkipButtonWhenAvailable);
    if (adUiElements != null) {
      adsRenderingSettings.setUiElements(adUiElements);
    }

    // Skip ads based on the start position as required.
    long[] adGroupTimesUs = getAdGroupTimesUs(adsManager.getAdCuePoints());
    long contentPositionMs =
        getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period);
    int adGroupIndexForPosition =
        adPlaybackState.getAdGroupIndexForPositionUs(
            C.msToUs(contentPositionMs), C.msToUs(contentDurationMs));
    if (adGroupIndexForPosition > 0 && adGroupIndexForPosition != C.INDEX_UNSET) {
      // Skip any ad groups before the one at or immediately before the playback position.
      for (int i = 0; i < adGroupIndexForPosition; i++) {
        adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
      }
      // Play ads after the midpoint between the ad to play and the one before it, to avoid issues
      // with rounding one of the two ad times.
      long adGroupForPositionTimeUs = adGroupTimesUs[adGroupIndexForPosition];
      long adGroupBeforeTimeUs = adGroupTimesUs[adGroupIndexForPosition - 1];
      double midpointTimeUs = (adGroupForPositionTimeUs + adGroupBeforeTimeUs) / 2d;
      adsRenderingSettings.setPlayAdsAfterTime(midpointTimeUs / C.MICROS_PER_SECOND);
    }

    if (adGroupIndexForPosition != C.INDEX_UNSET && hasMidrollAdGroups(adGroupTimesUs)) {
      // Provide the player's initial position to trigger loading and playing the ad.
      pendingContentPositionMs = contentPositionMs;
    }

    adsManager.init(adsRenderingSettings);
    adsManager.start();
    updateAdPlaybackState();
    if (DEBUG) {
      Log.d(TAG, "Initialized with ads rendering settings: " + adsRenderingSettings);
    }
  }

  private void handleAdEvent(AdEvent adEvent) {
    switch (adEvent.getType()) {
      case AD_BREAK_FETCH_ERROR:
        String adGroupTimeSecondsString =
            Assertions.checkNotNull(adEvent.getAdData().get("adBreakTime"));
        if (DEBUG) {
          Log.d(TAG, "Fetch error for ad at " + adGroupTimeSecondsString + " seconds");
        }
        int adGroupTimeSeconds = Integer.parseInt(adGroupTimeSecondsString);
        int adGroupIndex =
            adGroupTimeSeconds == -1
                ? adPlaybackState.adGroupCount - 1
                : Util.linearSearch(
                    adPlaybackState.adGroupTimesUs, C.MICROS_PER_SECOND * adGroupTimeSeconds);
        AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
        if (adGroup.count == C.LENGTH_UNSET) {
          adPlaybackState =
              adPlaybackState.withAdCount(adGroupIndex, Math.max(1, adGroup.states.length));
          adGroup = adPlaybackState.adGroups[adGroupIndex];
        }
        for (int i = 0; i < adGroup.count; i++) {
          if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
            if (DEBUG) {
              Log.d(TAG, "Removing ad " + i + " in ad group " + adGroupIndex);
            }
            adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, i);
          }
        }
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
      case LOG:
        Map<String, String> adData = adEvent.getAdData();
        String message = "AdEvent: " + adData;
        Log.i(TAG, message);
        break;
      default:
        break;
    }
  }

  private VideoProgressUpdate getContentVideoProgressUpdate() {
    if (player == null) {
      return lastContentProgress;
    }
    boolean hasContentDuration = contentDurationMs != C.TIME_UNSET;
    long contentPositionMs;
    if (pendingContentPositionMs != C.TIME_UNSET) {
      sentPendingContentPositionMs = true;
      contentPositionMs = pendingContentPositionMs;
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
    AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
    for (int i = 0; i < adCallbacks.size(); i++) {
      adCallbacks.get(i).onAdProgress(adMediaInfo, videoProgressUpdate);
    }
    handler.removeCallbacks(updateAdProgressRunnable);
    handler.postDelayed(updateAdProgressRunnable, AD_PROGRESS_UPDATE_INTERVAL_MS);
  }

  private void stopUpdatingAdProgress() {
    handler.removeCallbacks(updateAdProgressRunnable);
  }

  private void handlePlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
    if (playingAd && imaAdState == IMA_AD_STATE_PLAYING) {
      if (!bufferingAd && playbackState == Player.STATE_BUFFERING) {
        AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
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
      checkForContentComplete();
    } else if (imaAdState != IMA_AD_STATE_NONE && playbackState == Player.STATE_ENDED) {
      AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
      if (adMediaInfo == null) {
        Log.w(TAG, "onEnded without ad media info");
      } else {
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onEnded(adMediaInfo);
        }
      }
      if (DEBUG) {
        Log.d(TAG, "VideoAdPlayerCallback.onEnded in onPlayerStateChanged");
      }
    }
  }

  private void handleTimelineOrPositionChanged() {
    @Nullable Player player = this.player;
    if (adsManager == null || player == null) {
      return;
    }
    if (!playingAd && !player.isPlayingAd()) {
      checkForContentComplete();
      if (sentContentComplete) {
        for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
          if (adPlaybackState.adGroupTimesUs[i] != C.TIME_END_OF_SOURCE) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(/* adGroupIndex= */ i);
          }
        }
        updateAdPlaybackState();
      } else if (!timeline.isEmpty()) {
        long positionMs = getContentPeriodPositionMs(player, timeline, period);
        timeline.getPeriod(/* periodIndex= */ 0, period);
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
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onEnded(adMediaInfo);
        }
      }
      if (DEBUG) {
        Log.d(TAG, "VideoAdPlayerCallback.onEnded in onTimelineChanged/onPositionDiscontinuity");
      }
    }
    if (!sentContentComplete && !wasPlayingAd && playingAd && imaAdState == IMA_AD_STATE_NONE) {
      int adGroupIndex = player.getCurrentAdGroupIndex();
      // IMA hasn't called playAd yet, so fake the content position.
      fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime();
      fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
      if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
        fakeContentProgressOffsetMs = contentDurationMs;
      }
    }
  }

  private void resumeContentInternal() {
    if (imaAdInfo != null) {
      adPlaybackState = adPlaybackState.withSkippedAdGroup(imaAdInfo.adGroupIndex);
      updateAdPlaybackState();
    }
  }

  private void pauseContentInternal() {
    imaAdState = IMA_AD_STATE_NONE;
    if (sentPendingContentPositionMs) {
      pendingContentPositionMs = C.TIME_UNSET;
      sentPendingContentPositionMs = false;
    }
  }

  private void stopAdInternal() {
    imaAdState = IMA_AD_STATE_NONE;
    stopUpdatingAdProgress();
    // TODO: Handle the skipped event so the ad can be marked as skipped rather than played.
    Assertions.checkNotNull(imaAdInfo);
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
    if (player == null) {
      return;
    }

    // TODO: Once IMA signals which ad group failed to load, remove this call.
    int adGroupIndex = getLoadingAdGroupIndex();
    if (adGroupIndex == C.INDEX_UNSET) {
      Log.w(TAG, "Unable to determine ad group index for ad group load error", error);
      return;
    }

    AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
    if (adGroup.count == C.LENGTH_UNSET) {
      adPlaybackState =
          adPlaybackState.withAdCount(adGroupIndex, Math.max(1, adGroup.states.length));
      adGroup = adPlaybackState.adGroups[adGroupIndex];
    }
    for (int i = 0; i < adGroup.count; i++) {
      if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
        if (DEBUG) {
          Log.d(TAG, "Removing ad " + i + " in ad group " + adGroupIndex);
        }
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, i);
      }
    }
    updateAdPlaybackState();
    if (pendingAdLoadError == null) {
      pendingAdLoadError = AdLoadException.createForAdGroup(error, adGroupIndex);
    }
    pendingContentPositionMs = C.TIME_UNSET;
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
  }

  private void handleAdPrepareError(int adGroupIndex, int adIndexInAdGroup, Exception exception) {
    if (DEBUG) {
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
      AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
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
        adCallbacks.get(i).onError(Assertions.checkNotNull(adMediaInfo));
      }
    }
    adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup);
    updateAdPlaybackState();
  }

  private void checkForContentComplete() {
    long positionMs = getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period);
    if (!sentContentComplete
        && contentDurationMs != C.TIME_UNSET
        && pendingContentPositionMs == C.TIME_UNSET
        && positionMs + THRESHOLD_END_OF_CONTENT_MS >= contentDurationMs) {
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
      eventListener.onAdPlaybackState(adPlaybackState);
    }
  }

  private void maybeNotifyPendingAdLoadError() {
    if (pendingAdLoadError != null && eventListener != null) {
      eventListener.onAdLoadError(pendingAdLoadError, getAdsDataSpec(adTagUri));
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
    if (eventListener != null) {
      eventListener.onAdLoadError(
          AdLoadException.createForUnexpected(new RuntimeException(message, cause)),
          getAdsDataSpec(adTagUri));
    }
  }

  private int getAdGroupIndexForAdPod(AdPodInfo adPodInfo) {
    if (adPodInfo.getPodIndex() == -1) {
      // This is a postroll ad.
      return adPlaybackState.adGroupCount - 1;
    }

    // adPodInfo.podIndex may be 0-based or 1-based, so for now look up the cue point instead.
    long adGroupTimeUs = (long) (((float) adPodInfo.getTimeOffset()) * C.MICROS_PER_SECOND);
    for (int adGroupIndex = 0; adGroupIndex < adPlaybackState.adGroupCount; adGroupIndex++) {
      if (adPlaybackState.adGroupTimesUs[adGroupIndex] == adGroupTimeUs) {
        return adGroupIndex;
      }
    }
    throw new IllegalStateException("Failed to find cue point");
  }

  /**
   * Returns the index of the ad group that will preload next, or {@link C#INDEX_UNSET} if there is
   * no such ad group.
   */
  private int getLoadingAdGroupIndex() {
    long playerPositionUs =
        C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period));
    int adGroupIndex =
        adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, C.msToUs(contentDurationMs));
    if (adGroupIndex == C.INDEX_UNSET) {
      adGroupIndex =
          adPlaybackState.getAdGroupIndexAfterPositionUs(
              playerPositionUs, C.msToUs(contentDurationMs));
    }
    return adGroupIndex;
  }

  private String getAdMediaInfoString(AdMediaInfo adMediaInfo) {
    @Nullable AdInfo adInfo = adInfoByAdMediaInfo.get(adMediaInfo);
    return "AdMediaInfo[" + adMediaInfo.getUrl() + (adInfo != null ? ", " + adInfo : "") + "]";
  }

  private static DataSpec getAdsDataSpec(@Nullable Uri adTagUri) {
    return new DataSpec(adTagUri != null ? adTagUri : Uri.EMPTY);
  }

  private static long getContentPeriodPositionMs(
      Player player, Timeline timeline, Timeline.Period period) {
    long contentWindowPositionMs = player.getContentPosition();
    return contentWindowPositionMs
        - (timeline.isEmpty()
            ? 0
            : timeline.getPeriod(/* periodIndex= */ 0, period).getPositionInWindowMs());
  }

  private static long[] getAdGroupTimesUs(List<Float> cuePoints) {
    if (cuePoints.isEmpty()) {
      // If no cue points are specified, there is a preroll ad.
      return new long[] {0};
    }

    int count = cuePoints.size();
    long[] adGroupTimesUs = new long[count];
    int adGroupIndex = 0;
    for (int i = 0; i < count; i++) {
      double cuePoint = cuePoints.get(i);
      if (cuePoint == -1.0) {
        adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE;
      } else {
        adGroupTimesUs[adGroupIndex++] = (long) (C.MICROS_PER_SECOND * cuePoint);
      }
    }
    // Cue points may be out of order, so sort them.
    Arrays.sort(adGroupTimesUs, 0, adGroupIndex);
    return adGroupTimesUs;
  }

  private static boolean isAdGroupLoadError(AdError adError) {
    // TODO: Find out what other errors need to be handled (if any), and whether each one relates to
    // a single ad, ad group or the whole timeline.
    return adError.getErrorCode() == AdErrorCode.VAST_LINEAR_ASSET_MISMATCH
        || adError.getErrorCode() == AdErrorCode.UNKNOWN_ERROR;
  }

  private static Looper getImaLooper() {
    // IMA SDK callbacks occur on the main thread. This method can be used to check that the player
    // is using the same looper, to ensure all interaction with this class is on the main thread.
    return Looper.getMainLooper();
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

  /** Factory for objects provided by the IMA SDK. */
  @VisibleForTesting
  /* package */ interface ImaFactory {
    /** @see ImaSdkSettings */
    ImaSdkSettings createImaSdkSettings();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdsRenderingSettings() */
    AdsRenderingSettings createAdsRenderingSettings();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdDisplayContainer() */
    AdDisplayContainer createAdDisplayContainer();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdsRequest() */
    AdsRequest createAdsRequest();
    /** @see ImaSdkFactory#createAdsLoader(Context, ImaSdkSettings, AdDisplayContainer) */
    com.google.ads.interactivemedia.v3.api.AdsLoader createAdsLoader(
        Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer);
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

  /** Default {@link ImaFactory} for non-test usage, which delegates to {@link ImaSdkFactory}. */
  private static final class DefaultImaFactory implements ImaFactory {
    @Override
    public ImaSdkSettings createImaSdkSettings() {
      return ImaSdkFactory.getInstance().createImaSdkSettings();
    }

    @Override
    public AdsRenderingSettings createAdsRenderingSettings() {
      return ImaSdkFactory.getInstance().createAdsRenderingSettings();
    }

    @Override
    public AdDisplayContainer createAdDisplayContainer() {
      return ImaSdkFactory.getInstance().createAdDisplayContainer();
    }

    @Override
    public AdsRequest createAdsRequest() {
      return ImaSdkFactory.getInstance().createAdsRequest();
    }

    @Override
    public com.google.ads.interactivemedia.v3.api.AdsLoader createAdsLoader(
        Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer) {
      return ImaSdkFactory.getInstance()
          .createAdsLoader(context, imaSdkSettings, adDisplayContainer);
    }
  }
}
