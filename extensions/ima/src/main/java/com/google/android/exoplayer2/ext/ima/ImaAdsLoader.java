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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;

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
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot;
import com.google.ads.interactivemedia.v3.api.FriendlyObstruction;
import com.google.ads.interactivemedia.v3.api.FriendlyObstructionPurpose;
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
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * {@link com.google.android.exoplayer2.source.ads.AdsLoader} using the IMA SDK. All methods must be
 * called on the main thread.
 *
 * <p>The player instance that will play the loaded ads must be set before playback using {@link
 * #setPlayer(Player)}. If the ads loader is no longer required, it must be released by calling
 * {@link #release()}.
 *
 * <p>See https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
 * information on compatible ad tag formats. Pass the ad tag URI when setting media item playback
 * properties (if using the media item API) or as a {@link DataSpec} when constructing the {@link
 * AdsMediaSource} (if using media sources directly). For the latter case, please note that this
 * implementation delegates loading of the data spec to the IMA SDK, so range and headers
 * specifications will be ignored in ad tag URIs. Literal ads responses can be encoded as data
 * scheme data specs, for example, by constructing the data spec using a URI generated via {@link
 * Util#getDataUriForString(String, String)}.
 *
 * <p>The IMA SDK can report obstructions to the ad view for accurate viewability measurement. This
 * means that any overlay views that obstruct the ad overlay but are essential for playback need to
 * be registered via the {@link AdViewProvider} passed to the {@link AdsMediaSource}. See the <a
 * href="https://developers.google.com/interactive-media-ads/docs/sdks/android/client-side/omsdk">IMA
 * SDK Open Measurement documentation</a> for more information.
 */
public final class ImaAdsLoader
    implements Player.EventListener, com.google.android.exoplayer2.source.ads.AdsLoader {

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
    @Nullable private AdErrorListener adErrorListener;
    @Nullable private AdEventListener adEventListener;
    @Nullable private VideoAdPlayer.VideoAdPlayerCallback videoAdPlayerCallback;
    @Nullable private List<String> adMediaMimeTypes;
    @Nullable private Set<UiElement> adUiElements;
    @Nullable private Collection<CompanionAdSlot> companionAdSlots;
    private long adPreloadTimeoutMs;
    private int vastLoadTimeoutMs;
    private int mediaLoadTimeoutMs;
    private int mediaBitrate;
    private boolean focusSkipButtonWhenAvailable;
    private boolean playAdBeforeStartPosition;
    private boolean debugModeEnabled;
    private ImaUtil.ImaFactory imaFactory;

    /**
     * Creates a new builder for {@link ImaAdsLoader}.
     *
     * @param context The context;
     */
    public Builder(Context context) {
      this.context = checkNotNull(context).getApplicationContext();
      adPreloadTimeoutMs = DEFAULT_AD_PRELOAD_TIMEOUT_MS;
      vastLoadTimeoutMs = TIMEOUT_UNSET;
      mediaLoadTimeoutMs = TIMEOUT_UNSET;
      mediaBitrate = BITRATE_UNSET;
      focusSkipButtonWhenAvailable = true;
      playAdBeforeStartPosition = true;
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
      this.imaSdkSettings = checkNotNull(imaSdkSettings);
      return this;
    }

    /**
     * Sets a listener for ad errors that will be passed to {@link
     * AdsLoader#addAdErrorListener(AdErrorListener)} and {@link
     * AdsManager#addAdErrorListener(AdErrorListener)}.
     *
     * @param adErrorListener The ad error listener.
     * @return This builder, for convenience.
     */
    public Builder setAdErrorListener(AdErrorListener adErrorListener) {
      this.adErrorListener = checkNotNull(adErrorListener);
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
      this.adEventListener = checkNotNull(adEventListener);
      return this;
    }

    /**
     * Sets a callback to receive video ad player events. Note that these events are handled
     * internally by the IMA SDK and this ads loader. For analytics and diagnostics, new
     * implementations should generally use events from the top-level {@link Player} listeners
     * instead of setting a callback via this method.
     *
     * @param videoAdPlayerCallback The callback to receive video ad player events.
     * @return This builder, for convenience.
     * @see com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback
     */
    public Builder setVideoAdPlayerCallback(
        VideoAdPlayer.VideoAdPlayerCallback videoAdPlayerCallback) {
      this.videoAdPlayerCallback = checkNotNull(videoAdPlayerCallback);
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
      this.adUiElements = ImmutableSet.copyOf(checkNotNull(adUiElements));
      return this;
    }

    /**
     * Sets the slots to use for companion ads, if they are present in the loaded ad.
     *
     * @param companionAdSlots The slots to use for companion ads.
     * @return This builder, for convenience.
     * @see AdDisplayContainer#setCompanionSlots(Collection)
     */
    public Builder setCompanionAdSlots(Collection<CompanionAdSlot> companionAdSlots) {
      this.companionAdSlots = ImmutableList.copyOf(checkNotNull(companionAdSlots));
      return this;
    }

    /**
     * Sets the MIME types to prioritize for linear ad media. If not specified, MIME types supported
     * by the {@link MediaSourceFactory adMediaSourceFactory} used to construct the {@link
     * AdsMediaSource} will be used.
     *
     * @param adMediaMimeTypes The MIME types to prioritize for linear ad media. May contain {@link
     *     MimeTypes#APPLICATION_MPD}, {@link MimeTypes#APPLICATION_M3U8}, {@link
     *     MimeTypes#VIDEO_MP4}, {@link MimeTypes#VIDEO_WEBM}, {@link MimeTypes#VIDEO_H263}, {@link
     *     MimeTypes#AUDIO_MP4} and {@link MimeTypes#AUDIO_MPEG}.
     * @return This builder, for convenience.
     * @see AdsRenderingSettings#setMimeTypes(List)
     */
    public Builder setAdMediaMimeTypes(List<String> adMediaMimeTypes) {
      this.adMediaMimeTypes = ImmutableList.copyOf(checkNotNull(adMediaMimeTypes));
      return this;
    }

    /**
     * Sets the duration in milliseconds for which the player must buffer while preloading an ad
     * group before that ad group is skipped and marked as having failed to load. Pass {@link
     * C#TIME_UNSET} if there should be no such timeout. The default value is {@value
     * #DEFAULT_AD_PRELOAD_TIMEOUT_MS} ms.
     *
     * <p>The purpose of this timeout is to avoid playback getting stuck in the unexpected case that
     * the IMA SDK does not load an ad break based on the player's reported content position.
     *
     * @param adPreloadTimeoutMs The timeout buffering duration in milliseconds, or {@link
     *     C#TIME_UNSET} for no timeout.
     * @return This builder, for convenience.
     */
    public Builder setAdPreloadTimeoutMs(long adPreloadTimeoutMs) {
      checkArgument(adPreloadTimeoutMs == C.TIME_UNSET || adPreloadTimeoutMs > 0);
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
      checkArgument(vastLoadTimeoutMs > 0);
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
      checkArgument(mediaLoadTimeoutMs > 0);
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
      checkArgument(bitrate > 0);
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

    /**
     * Sets whether to play an ad before the start position when beginning playback. If {@code
     * true}, an ad will be played if there is one at or before the start position. If {@code
     * false}, an ad will be played only if there is one exactly at the start position. The default
     * setting is {@code true}.
     *
     * @param playAdBeforeStartPosition Whether to play an ad before the start position when
     *     beginning playback.
     * @return This builder, for convenience.
     */
    public Builder setPlayAdBeforeStartPosition(boolean playAdBeforeStartPosition) {
      this.playAdBeforeStartPosition = playAdBeforeStartPosition;
      return this;
    }

    /**
     * Sets whether to enable outputting verbose logs for the IMA extension and IMA SDK. The default
     * value is {@code false}. This setting is intended for debugging only, and should not be
     * enabled in production applications.
     *
     * @param debugModeEnabled Whether to enable outputting verbose logs for the IMA extension and
     *     IMA SDK.
     * @return This builder, for convenience.
     * @see ImaSdkSettings#setDebugMode(boolean)
     */
    public Builder setDebugModeEnabled(boolean debugModeEnabled) {
      this.debugModeEnabled = debugModeEnabled;
      return this;
    }

    @VisibleForTesting
    /* package */ Builder setImaFactory(ImaUtil.ImaFactory imaFactory) {
      this.imaFactory = checkNotNull(imaFactory);
      return this;
    }

    /**
     * Returns a new {@link ImaAdsLoader} for the specified ad tag.
     *
     * @param adTagUri The URI of a compatible ad tag to load. See
     *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
     *     information on compatible ad tags.
     * @return The new {@link ImaAdsLoader}.
     * @deprecated Pass the ad tag URI when setting media item playback properties (if using the
     *     media item API) or as a {@link DataSpec} when constructing the {@link AdsMediaSource} (if
     *     using media sources directly).
     */
    @Deprecated
    public ImaAdsLoader buildForAdTag(Uri adTagUri) {
      return new ImaAdsLoader(
          context,
          getConfiguration(),
          imaFactory,
          /* adTagUri= */ adTagUri,
          /* adsResponse= */ null);
    }

    /**
     * Returns a new {@link ImaAdsLoader} with the specified sideloaded ads response.
     *
     * @param adsResponse The sideloaded VAST, VMAP, or ad rules response to be used instead of
     *     making a request via an ad tag URL.
     * @return The new {@link ImaAdsLoader}.
     * @deprecated Pass the ads response as a data URI when setting media item playback properties
     *     (if using the media item API) or as a {@link DataSpec} when constructing the {@link
     *     AdsMediaSource} (if using media sources directly). {@link
     *     Util#getDataUriForString(String, String)} can be used to construct a data URI from
     *     literal string ads response (with MIME type text/xml).
     */
    @Deprecated
    public ImaAdsLoader buildForAdsResponse(String adsResponse) {
      return new ImaAdsLoader(
          context, getConfiguration(), imaFactory, /* adTagUri= */ null, adsResponse);
    }

    /** Returns a new {@link ImaAdsLoader}. */
    public ImaAdsLoader build() {
      return new ImaAdsLoader(
          context, getConfiguration(), imaFactory, /* adTagUri= */ null, /* adsResponse= */ null);
    }

    // TODO(internal: b/169646419): Remove/hide once the deprecated constructor has been removed.
    /* package */ ImaUtil.Configuration getConfiguration() {
      return new ImaUtil.Configuration(
          adPreloadTimeoutMs,
          vastLoadTimeoutMs,
          mediaLoadTimeoutMs,
          focusSkipButtonWhenAvailable,
          playAdBeforeStartPosition,
          mediaBitrate,
          adMediaMimeTypes,
          adUiElements,
          companionAdSlots,
          adErrorListener,
          adEventListener,
          videoAdPlayerCallback,
          imaSdkSettings,
          debugModeEnabled);
    }
  }

  private static final String TAG = "ImaAdsLoader";

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

  private static final int TIMEOUT_UNSET = -1;
  private static final int BITRATE_UNSET = -1;

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

  private static final DataSpec EMPTY_AD_TAG_DATA_SPEC = new DataSpec(Uri.EMPTY);

  private final ImaUtil.Configuration configuration;
  private final Context context;
  private final ImaUtil.ImaFactory imaFactory;
  @Nullable private final Uri adTagUri;
  @Nullable private final String adsResponse;
  private final ImaSdkSettings imaSdkSettings;
  private final Timeline.Period period;
  private final Handler handler;
  private final ComponentListener componentListener;
  private final List<VideoAdPlayer.VideoAdPlayerCallback> adCallbacks;
  private final Runnable updateAdProgressRunnable;
  private final BiMap<AdMediaInfo, AdInfo> adInfoByAdMediaInfo;

  private @MonotonicNonNull AdDisplayContainer adDisplayContainer;
  private @MonotonicNonNull AdsLoader adsLoader;
  private boolean wasSetPlayerCalled;
  @Nullable private Player nextPlayer;
  @Nullable private Object pendingAdRequestContext;
  private List<String> supportedMimeTypes;
  @Nullable private EventListener eventListener;
  @Nullable private Player player;
  private DataSpec adTagDataSpec;
  private VideoProgressUpdate lastContentProgress;
  private VideoProgressUpdate lastAdProgress;
  private int lastVolumePercent;

  @Nullable private AdsManager adsManager;
  private boolean isAdsManagerInitialized;
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

  /**
   * Creates a new IMA ads loader.
   *
   * <p>If you need to customize the ad request, use {@link ImaAdsLoader.Builder} instead.
   *
   * @param context The context.
   * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
   *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
   *     more information.
   * @deprecated Use {@link Builder} to create an instance. Pass the ad tag URI when setting media
   *     item playback properties (if using the media item API) or as a {@link DataSpec} when
   *     constructing the {@link AdsMediaSource} (if using media sources directly).
   */
  @Deprecated
  public ImaAdsLoader(Context context, Uri adTagUri) {
    this(
        context,
        new Builder(context).getConfiguration(),
        new DefaultImaFactory(),
        adTagUri,
        /* adsResponse= */ null);
  }

  @SuppressWarnings({"nullness:argument.type.incompatible", "methodref.receiver.bound.invalid"})
  private ImaAdsLoader(
      Context context,
      ImaUtil.Configuration configuration,
      ImaUtil.ImaFactory imaFactory,
      @Nullable Uri adTagUri,
      @Nullable String adsResponse) {
    this.context = context.getApplicationContext();
    this.configuration = configuration;
    this.imaFactory = imaFactory;
    this.adTagUri = adTagUri;
    this.adsResponse = adsResponse;
    @Nullable ImaSdkSettings imaSdkSettings = configuration.imaSdkSettings;
    if (imaSdkSettings == null) {
      imaSdkSettings = imaFactory.createImaSdkSettings();
      if (configuration.debugModeEnabled) {
        imaSdkSettings.setDebugMode(true);
      }
    }
    imaSdkSettings.setPlayerType(IMA_SDK_SETTINGS_PLAYER_TYPE);
    imaSdkSettings.setPlayerVersion(IMA_SDK_SETTINGS_PLAYER_VERSION);
    this.imaSdkSettings = imaSdkSettings;
    period = new Timeline.Period();
    handler = Util.createHandler(getImaLooper(), /* callback= */ null);
    componentListener = new ComponentListener();
    adCallbacks = new ArrayList<>(/* initialCapacity= */ 1);
    if (configuration.applicationVideoAdPlayerCallback != null) {
      adCallbacks.add(configuration.applicationVideoAdPlayerCallback);
    }
    updateAdProgressRunnable = this::updateAdProgress;
    adInfoByAdMediaInfo = HashBiMap.create();
    supportedMimeTypes = Collections.emptyList();
    adTagDataSpec = EMPTY_AD_TAG_DATA_SPEC;
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
   * Returns the underlying {@link AdsLoader} wrapped by this instance, or {@code null} if ads have
   * not been requested yet.
   */
  @Nullable
  public AdsLoader getAdsLoader() {
    return adsLoader;
  }

  /**
   * Returns the {@link AdDisplayContainer} used by this loader, or {@code null} if ads have not
   * been requested yet.
   *
   * <p>Note: any video controls overlays registered via {@link
   * AdDisplayContainer#registerFriendlyObstruction(FriendlyObstruction)} will be unregistered
   * automatically when the media source detaches from this instance. It is therefore necessary to
   * re-register views each time the ads loader is reused. Alternatively, provide overlay views via
   * the {@link com.google.android.exoplayer2.source.ads.AdsLoader.AdViewProvider} when creating the
   * media source to benefit from automatic registration.
   */
  @Nullable
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
   * @param adViewGroup A {@link ViewGroup} on top of the player that will show any ad UI, or {@code
   *     null} if playing audio-only ads.
   * @deprecated Use {@link #requestAds(DataSpec, ViewGroup)}, specifying the ad tag data spec to
   *     request, and migrate off deprecated builder methods/constructor that require an ad tag or
   *     ads response.
   */
  @Deprecated
  public void requestAds(@Nullable ViewGroup adViewGroup) {
    requestAds(adTagDataSpec, adViewGroup);
  }

  /**
   * Requests ads, if they have not already been requested. Must be called on the main thread.
   *
   * <p>Ads will be requested automatically when the player is prepared if this method has not been
   * called, so it is only necessary to call this method if you want to request ads before preparing
   * the player.
   *
   * @param adTagDataSpec The data specification of the ad tag to load. See class javadoc for
   *     information about compatible ad tag formats.
   * @param adViewGroup A {@link ViewGroup} on top of the player that will show any ad UI, or {@code
   *     null} if playing audio-only ads.
   */
  public void requestAds(DataSpec adTagDataSpec, @Nullable ViewGroup adViewGroup) {
    if (hasAdPlaybackState || adsManager != null || pendingAdRequestContext != null) {
      // Ads have already been requested.
      return;
    }

    if (EMPTY_AD_TAG_DATA_SPEC.equals(adTagDataSpec)) {
      // Handle deprecated ways of specifying the ad tag.
      if (adTagUri != null) {
        adTagDataSpec = new DataSpec(adTagUri);
      } else if (adsResponse != null) {
        adTagDataSpec =
            new DataSpec(
                Util.getDataUriForString(/* mimeType= */ "text/xml", /* data= */ adsResponse));
      } else {
        throw new IllegalStateException();
      }
    }

    AdsRequest request;
    try {
      request = ImaUtil.getAdsRequestForAdTagDataSpec(imaFactory, adTagDataSpec);
    } catch (IOException e) {
      hasAdPlaybackState = true;
      updateAdPlaybackState();
      pendingAdLoadError = AdLoadException.createForAllAds(e);
      maybeNotifyPendingAdLoadError();
      return;
    }
    this.adTagDataSpec = adTagDataSpec;
    pendingAdRequestContext = new Object();
    request.setUserRequestContext(pendingAdRequestContext);
    if (configuration.vastLoadTimeoutMs != TIMEOUT_UNSET) {
      request.setVastLoadTimeout(configuration.vastLoadTimeoutMs);
    }
    request.setContentProgressProvider(componentListener);

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

    adsLoader = imaFactory.createAdsLoader(context, imaSdkSettings, adDisplayContainer);
    adsLoader.addAdErrorListener(componentListener);
    if (configuration.applicationAdErrorListener != null) {
      adsLoader.addAdErrorListener(configuration.applicationAdErrorListener);
    }
    adsLoader.addAdsLoadedListener(componentListener);
    adsLoader.requestAds(request);
  }

  /**
   * Skips the current ad.
   *
   * <p>This method is intended for apps that play audio-only ads and so need to provide their own
   * UI for users to skip skippable ads. Apps showing video ads should not call this method, as the
   * IMA SDK provides the UI to skip ads in the ad view group passed via {@link AdViewProvider}.
   */
  public void skipAd() {
    if (adsManager != null) {
      adsManager.skip();
    }
  }

  // com.google.android.exoplayer2.source.ads.AdsLoader implementation.

  @Override
  public void setPlayer(@Nullable Player player) {
    checkState(Looper.myLooper() == getImaLooper());
    checkState(player == null || player.getApplicationLooper() == getImaLooper());
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
  public void setAdTagDataSpec(DataSpec adTagDataSpec) {
    this.adTagDataSpec = adTagDataSpec;
  }

  @Override
  public void start(EventListener eventListener, AdViewProvider adViewProvider) {
    checkState(
        wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player.");
    player = nextPlayer;
    if (player == null) {
      return;
    }
    player.addListener(this);
    boolean playWhenReady = player.getPlayWhenReady();
    this.eventListener = eventListener;
    lastVolumePercent = 0;
    lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    maybeNotifyPendingAdLoadError();
    if (hasAdPlaybackState) {
      // Pass the ad playback state to the player, and resume ads if necessary.
      eventListener.onAdPlaybackState(adPlaybackState);
      if (adsManager != null && imaPausedContent && playWhenReady) {
        adsManager.resume();
      }
    } else if (adsManager != null) {
      adPlaybackState = ImaUtil.getInitialAdPlaybackStateForCuePoints(adsManager.getAdCuePoints());
      updateAdPlaybackState();
    } else {
      // Ads haven't loaded yet, so request them.
      requestAds(adTagDataSpec, adViewProvider.getAdViewGroup());
    }
    if (adDisplayContainer != null) {
      for (OverlayInfo overlayInfo : adViewProvider.getAdOverlayInfos()) {
        adDisplayContainer.registerFriendlyObstruction(
            imaFactory.createFriendlyObstruction(
                overlayInfo.view,
                ImaUtil.getFriendlyObstructionPurpose(overlayInfo.purpose),
                overlayInfo.reasonDetail));
      }
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
    lastVolumePercent = getPlayerVolumePercent();
    lastAdProgress = getAdVideoProgressUpdate();
    lastContentProgress = getContentVideoProgressUpdate();
    if (adDisplayContainer != null) {
      adDisplayContainer.unregisterAllFriendlyObstructions();
    }
    player.removeListener(this);
    this.player = null;
    eventListener = null;
  }

  @Override
  public void release() {
    pendingAdRequestContext = null;
    destroyAdsManager();
    if (adsLoader != null) {
      adsLoader.removeAdsLoadedListener(componentListener);
      adsLoader.removeAdErrorListener(componentListener);
      if (configuration.applicationAdErrorListener != null) {
        adsLoader.removeAdErrorListener(configuration.applicationAdErrorListener);
      }
      adsLoader.release();
    }
    imaPausedContent = false;
    imaAdState = IMA_AD_STATE_NONE;
    imaAdMediaInfo = null;
    stopUpdatingAdProgress();
    imaAdInfo = null;
    pendingAdLoadError = null;
    adPlaybackState = AdPlaybackState.NONE;
    hasAdPlaybackState = true;
    updateAdPlaybackState();
  }

  @Override
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

  @Override
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
    checkArgument(timeline.getPeriodCount() == 1);
    this.timeline = timeline;
    long contentDurationUs = timeline.getPeriod(/* periodIndex= */ 0, period).durationUs;
    contentDurationMs = C.usToMs(contentDurationUs);
    if (contentDurationUs != C.TIME_UNSET) {
      adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs);
    }
    @Nullable AdsManager adsManager = this.adsManager;
    if (!isAdsManagerInitialized && adsManager != null) {
      isAdsManagerInitialized = true;
      @Nullable AdsRenderingSettings adsRenderingSettings = setupAdsRendering();
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
    handleTimelineOrPositionChanged();
  }

  @Override
  public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    handleTimelineOrPositionChanged();
  }

  @Override
  public void onPlaybackStateChanged(@Player.State int playbackState) {
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
      if (timeUntilAdMs < configuration.adPreloadTimeoutMs) {
        waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime();
      }
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

  /**
   * Configures ads rendering for starting playback, returning the settings for the IMA SDK or
   * {@code null} if no ads should play.
   */
  @Nullable
  private AdsRenderingSettings setupAdsRendering() {
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
    long contentPositionMs = getContentPeriodPositionMs(checkNotNull(player), timeline, period);
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
    } else {
      // Mark any ads for the current/reported player position that haven't loaded as being in the
      // error state, to force resuming content. This includes VPAID ads that never load.
      long playerPositionUs;
      if (player != null) {
        playerPositionUs = C.msToUs(getContentPeriodPositionMs(player, timeline, period));
      } else if (!VideoProgressUpdate.VIDEO_TIME_NOT_READY.equals(lastContentProgress)) {
        // Playback is backgrounded so use the last reported content position.
        playerPositionUs = C.msToUs(lastContentProgress.getCurrentTimeMs());
      } else {
        return;
      }
      int adGroupIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(
              playerPositionUs, C.msToUs(contentDurationMs));
      if (adGroupIndex != C.INDEX_UNSET) {
        markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex);
      }
    }
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
    adInfoByAdMediaInfo.put(adMediaInfo, adInfo);
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
    if (!checkNotNull(player).getPlayWhenReady()) {
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
    checkState(adMediaInfo.equals(imaAdMediaInfo));
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
    checkNotNull(player);
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
    // Ignore updates while detached. When a player is attached it will receive the latest state.
    if (eventListener != null) {
      eventListener.onAdPlaybackState(adPlaybackState);
    }
  }

  private void maybeNotifyPendingAdLoadError() {
    if (pendingAdLoadError != null && eventListener != null) {
      eventListener.onAdLoadError(pendingAdLoadError, adTagDataSpec);
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
          AdLoadException.createForUnexpected(new RuntimeException(message, cause)), adTagDataSpec);
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

  private String getAdMediaInfoString(AdMediaInfo adMediaInfo) {
    @Nullable AdInfo adInfo = adInfoByAdMediaInfo.get(adMediaInfo);
    return "AdMediaInfo[" + adMediaInfo.getUrl() + (adInfo != null ? ", " + adInfo : "") + "]";
  }

  private static long getContentPeriodPositionMs(
      Player player, Timeline timeline, Timeline.Period period) {
    long contentWindowPositionMs = player.getContentPosition();
    return contentWindowPositionMs
        - (timeline.isEmpty()
            ? 0
            : timeline.getPeriod(/* periodIndex= */ 0, period).getPositionInWindowMs());
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
      ImaAdsLoader.this.adsManager = adsManager;
      adsManager.addAdErrorListener(this);
      if (configuration.applicationAdErrorListener != null) {
        adsManager.addAdErrorListener(configuration.applicationAdErrorListener);
      }
      adsManager.addAdEventListener(this);
      if (configuration.applicationAdEventListener != null) {
        adsManager.addAdEventListener(configuration.applicationAdEventListener);
      }
      if (player != null) {
        // If a player is attached already, start playback immediately.
        try {
          adPlaybackState =
              ImaUtil.getInitialAdPlaybackStateForCuePoints(adsManager.getAdCuePoints());
          hasAdPlaybackState = true;
          updateAdPlaybackState();
        } catch (RuntimeException e) {
          maybeNotifyInternalError("onAdsManagerLoaded", e);
        }
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
        adPlaybackState = AdPlaybackState.NONE;
        hasAdPlaybackState = true;
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

  /**
   * Default {@link ImaUtil.ImaFactory} for non-test usage, which delegates to {@link
   * ImaSdkFactory}.
   */
  private static final class DefaultImaFactory implements ImaUtil.ImaFactory {
    @Override
    public ImaSdkSettings createImaSdkSettings() {
      ImaSdkSettings settings = ImaSdkFactory.getInstance().createImaSdkSettings();
      settings.setLanguage(getImaLanguageCodeForDefaultLocale());
      return settings;
    }

    @Override
    public AdsRenderingSettings createAdsRenderingSettings() {
      return ImaSdkFactory.getInstance().createAdsRenderingSettings();
    }

    @Override
    public AdDisplayContainer createAdDisplayContainer(ViewGroup container, VideoAdPlayer player) {
      return ImaSdkFactory.createAdDisplayContainer(container, player);
    }

    @Override
    public AdDisplayContainer createAudioAdDisplayContainer(Context context, VideoAdPlayer player) {
      return ImaSdkFactory.createAudioAdDisplayContainer(context, player);
    }

    // The reasonDetail parameter to createFriendlyObstruction is annotated @Nullable but the
    // annotation is not kept in the obfuscated dependency.
    @SuppressWarnings("nullness:argument.type.incompatible")
    @Override
    public FriendlyObstruction createFriendlyObstruction(
        View view,
        FriendlyObstructionPurpose friendlyObstructionPurpose,
        @Nullable String reasonDetail) {
      return ImaSdkFactory.getInstance()
          .createFriendlyObstruction(view, friendlyObstructionPurpose, reasonDetail);
    }

    @Override
    public AdsRequest createAdsRequest() {
      return ImaSdkFactory.getInstance().createAdsRequest();
    }

    @Override
    public AdsLoader createAdsLoader(
        Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer) {
      return ImaSdkFactory.getInstance()
          .createAdsLoader(context, imaSdkSettings, adDisplayContainer);
    }

    /**
     * Returns a language code that's suitable for passing to {@link ImaSdkSettings#setLanguage} and
     * corresponds to the device's {@link Locale#getDefault() default Locale}. IMA will fall back to
     * its default language code ("en") if the value returned is unsupported.
     */
    // TODO: It may be possible to define a better mapping onto IMA's supported language codes. See:
    // https://developers.google.com/interactive-media-ads/docs/sdks/android/client-side/localization.
    // [Internal ref: b/174042000] will help if implemented.
    private static String getImaLanguageCodeForDefaultLocale() {
      return Util.splitAtFirst(Util.getSystemLanguageCodes()[0], "-")[0];
    }
  }
}
