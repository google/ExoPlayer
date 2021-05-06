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

import static com.google.android.exoplayer2.ext.ima.ImaUtil.BITRATE_UNSET;
import static com.google.android.exoplayer2.ext.ima.ImaUtil.TIMEOUT_UNSET;
import static com.google.android.exoplayer2.ext.ima.ImaUtil.getImaLooper;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot;
import com.google.ads.interactivemedia.v3.api.FriendlyObstruction;
import com.google.ads.interactivemedia.v3.api.FriendlyObstructionPurpose;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.UiElement;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.ui.AdViewProvider;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * {@link AdsLoader} using the IMA SDK. All methods must be called on the main thread.
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
public final class ImaAdsLoader implements Player.Listener, AdsLoader {

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
    @Nullable private Boolean enableContinuousPlayback;
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
     * com.google.ads.interactivemedia.v3.api.AdsLoader#addAdErrorListener(AdErrorListener)} and
     * {@link AdsManager#addAdErrorListener(AdErrorListener)}.
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
     * Sets whether to enable continuous playback. Pass {@code true} if content videos will be
     * played continuously, similar to a TV broadcast. This setting may modify the ads request but
     * does not affect ad playback behavior. The requested value is unknown by default.
     *
     * @param enableContinuousPlayback Whether to enable continuous playback.
     * @return This builder, for convenience.
     * @see AdsRequest#setContinuousPlayback(boolean)
     */
    public Builder setEnableContinuousPlayback(boolean enableContinuousPlayback) {
      this.enableContinuousPlayback = enableContinuousPlayback;
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

    /** Returns a new {@link ImaAdsLoader}. */
    public ImaAdsLoader build() {
      return new ImaAdsLoader(
          context,
          new ImaUtil.Configuration(
              adPreloadTimeoutMs,
              vastLoadTimeoutMs,
              mediaLoadTimeoutMs,
              focusSkipButtonWhenAvailable,
              playAdBeforeStartPosition,
              mediaBitrate,
              enableContinuousPlayback,
              adMediaMimeTypes,
              adUiElements,
              companionAdSlots,
              adErrorListener,
              adEventListener,
              videoAdPlayerCallback,
              imaSdkSettings,
              debugModeEnabled),
          imaFactory);
    }
  }

  private final ImaUtil.Configuration configuration;
  private final Context context;
  private final ImaUtil.ImaFactory imaFactory;
  private final HashMap<Object, AdTagLoader> adTagLoaderByAdsId;
  private final HashMap<AdsMediaSource, AdTagLoader> adTagLoaderByAdsMediaSource;
  private final Timeline.Period period;
  private final Timeline.Window window;

  private boolean wasSetPlayerCalled;
  @Nullable private Player nextPlayer;
  private List<String> supportedMimeTypes;
  @Nullable private Player player;
  @Nullable private AdTagLoader currentAdTagLoader;

  private ImaAdsLoader(
      Context context, ImaUtil.Configuration configuration, ImaUtil.ImaFactory imaFactory) {
    this.context = context.getApplicationContext();
    this.configuration = configuration;
    this.imaFactory = imaFactory;
    supportedMimeTypes = ImmutableList.of();
    adTagLoaderByAdsId = new HashMap<>();
    adTagLoaderByAdsMediaSource = new HashMap<>();
    period = new Timeline.Period();
    window = new Timeline.Window();
  }

  /**
   * Returns the underlying {@link com.google.ads.interactivemedia.v3.api.AdsLoader} wrapped by this
   * instance, or {@code null} if ads have not been requested yet.
   */
  @Nullable
  public com.google.ads.interactivemedia.v3.api.AdsLoader getAdsLoader() {
    return currentAdTagLoader != null ? currentAdTagLoader.getAdsLoader() : null;
  }

  /**
   * Returns the {@link AdDisplayContainer} used by this loader, or {@code null} if ads have not
   * been requested yet.
   *
   * <p>Note: any video controls overlays registered via {@link
   * AdDisplayContainer#registerFriendlyObstruction(FriendlyObstruction)} will be unregistered
   * automatically when the media source detaches from this instance. It is therefore necessary to
   * re-register views each time the ads loader is reused. Alternatively, provide overlay views via
   * the {@link AdViewProvider} when creating the media source to benefit from automatic
   * registration.
   */
  @Nullable
  public AdDisplayContainer getAdDisplayContainer() {
    return currentAdTagLoader != null ? currentAdTagLoader.getAdDisplayContainer() : null;
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
   * @param adsId A opaque identifier for the ad playback state across start/stop calls.
   * @param adViewGroup A {@link ViewGroup} on top of the player that will show any ad UI, or {@code
   *     null} if playing audio-only ads.
   */
  public void requestAds(DataSpec adTagDataSpec, Object adsId, @Nullable ViewGroup adViewGroup) {
    if (!adTagLoaderByAdsId.containsKey(adsId)) {
      AdTagLoader adTagLoader =
          new AdTagLoader(
              context,
              configuration,
              imaFactory,
              supportedMimeTypes,
              adTagDataSpec,
              adsId,
              adViewGroup);
      adTagLoaderByAdsId.put(adsId, adTagLoader);
    }
  }

  /**
   * Skips the current ad.
   *
   * <p>This method is intended for apps that play audio-only ads and so need to provide their own
   * UI for users to skip skippable ads. Apps showing video ads should not call this method, as the
   * IMA SDK provides the UI to skip ads in the ad view group passed via {@link AdViewProvider}.
   */
  public void skipAd() {
    if (currentAdTagLoader != null) {
      currentAdTagLoader.skipAd();
    }
  }

  /**
   * Moves UI focus to the skip button (or other interactive elements), if currently shown. See
   * {@link AdsManager#focus()}.
   */
  public void focusSkipButton() {
    if (currentAdTagLoader != null) {
      currentAdTagLoader.focusSkipButton();
    }
  }

  // AdsLoader implementation.

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
  public void start(
      AdsMediaSource adsMediaSource,
      DataSpec adTagDataSpec,
      Object adsId,
      AdViewProvider adViewProvider,
      EventListener eventListener) {
    checkState(
        wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player.");
    if (adTagLoaderByAdsMediaSource.isEmpty()) {
      player = nextPlayer;
      @Nullable Player player = this.player;
      if (player == null) {
        return;
      }
      player.addListener(this);
    }

    @Nullable AdTagLoader adTagLoader = adTagLoaderByAdsId.get(adsId);
    if (adTagLoader == null) {
      requestAds(adTagDataSpec, adsId, adViewProvider.getAdViewGroup());
      adTagLoader = adTagLoaderByAdsId.get(adsId);
    }
    adTagLoaderByAdsMediaSource.put(adsMediaSource, checkNotNull(adTagLoader));
    adTagLoader.addListenerWithAdView(eventListener, adViewProvider);
    maybeUpdateCurrentAdTagLoader();
  }

  @Override
  public void stop(AdsMediaSource adsMediaSource, EventListener eventListener) {
    @Nullable AdTagLoader removedAdTagLoader = adTagLoaderByAdsMediaSource.remove(adsMediaSource);
    maybeUpdateCurrentAdTagLoader();
    if (removedAdTagLoader != null) {
      removedAdTagLoader.removeListener(eventListener);
    }

    if (player != null && adTagLoaderByAdsMediaSource.isEmpty()) {
      player.removeListener(this);
      player = null;
    }
  }

  @Override
  public void release() {
    if (player != null) {
      player.removeListener(this);
      player = null;
      maybeUpdateCurrentAdTagLoader();
    }
    nextPlayer = null;

    for (AdTagLoader adTagLoader : adTagLoaderByAdsMediaSource.values()) {
      adTagLoader.release();
    }
    adTagLoaderByAdsMediaSource.clear();

    for (AdTagLoader adTagLoader : adTagLoaderByAdsId.values()) {
      adTagLoader.release();
    }
    adTagLoaderByAdsId.clear();
  }

  @Override
  public void handlePrepareComplete(
      AdsMediaSource adsMediaSource, int adGroupIndex, int adIndexInAdGroup) {
    if (player == null) {
      return;
    }
    checkNotNull(adTagLoaderByAdsMediaSource.get(adsMediaSource))
        .handlePrepareComplete(adGroupIndex, adIndexInAdGroup);
  }

  @Override
  public void handlePrepareError(
      AdsMediaSource adsMediaSource,
      int adGroupIndex,
      int adIndexInAdGroup,
      IOException exception) {
    if (player == null) {
      return;
    }
    checkNotNull(adTagLoaderByAdsMediaSource.get(adsMediaSource))
        .handlePrepareError(adGroupIndex, adIndexInAdGroup, exception);
  }

  // Player.Listener implementation.

  @Override
  public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
    if (timeline.isEmpty()) {
      // The player is being reset or contains no media.
      return;
    }
    maybeUpdateCurrentAdTagLoader();
    maybePreloadNextPeriodAds();
  }

  @Override
  public void onPositionDiscontinuity(
      Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition,
      @Player.DiscontinuityReason int reason) {
    maybeUpdateCurrentAdTagLoader();
    maybePreloadNextPeriodAds();
  }

  @Override
  public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
    maybePreloadNextPeriodAds();
  }

  @Override
  public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
    maybePreloadNextPeriodAds();
  }

  // Internal methods.

  private void maybeUpdateCurrentAdTagLoader() {
    @Nullable AdTagLoader oldAdTagLoader = currentAdTagLoader;
    @Nullable AdTagLoader newAdTagLoader = getCurrentAdTagLoader();
    if (!Util.areEqual(oldAdTagLoader, newAdTagLoader)) {
      if (oldAdTagLoader != null) {
        oldAdTagLoader.deactivate();
      }
      currentAdTagLoader = newAdTagLoader;
      if (newAdTagLoader != null) {
        newAdTagLoader.activate(checkNotNull(player));
      }
    }
  }

  @Nullable
  private AdTagLoader getCurrentAdTagLoader() {
    @Nullable Player player = this.player;
    if (player == null) {
      return null;
    }
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return null;
    }
    int periodIndex = player.getCurrentPeriodIndex();
    @Nullable Object adsId = timeline.getPeriod(periodIndex, period).getAdsId();
    if (adsId == null) {
      return null;
    }
    @Nullable AdTagLoader adTagLoader = adTagLoaderByAdsId.get(adsId);
    if (adTagLoader == null || !adTagLoaderByAdsMediaSource.containsValue(adTagLoader)) {
      return null;
    }
    return adTagLoader;
  }

  private void maybePreloadNextPeriodAds() {
    @Nullable Player player = this.player;
    if (player == null) {
      return;
    }
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return;
    }
    int nextPeriodIndex =
        timeline.getNextPeriodIndex(
            player.getCurrentPeriodIndex(),
            period,
            window,
            player.getRepeatMode(),
            player.getShuffleModeEnabled());
    if (nextPeriodIndex == C.INDEX_UNSET) {
      return;
    }
    timeline.getPeriod(nextPeriodIndex, period);
    @Nullable Object nextAdsId = period.getAdsId();
    if (nextAdsId == null) {
      return;
    }
    @Nullable AdTagLoader nextAdTagLoader = adTagLoaderByAdsId.get(nextAdsId);
    if (nextAdTagLoader == null || nextAdTagLoader == currentAdTagLoader) {
      return;
    }
    long periodPositionUs =
        timeline.getPeriodPosition(
                window, period, period.windowIndex, /* windowPositionUs= */ C.TIME_UNSET)
            .second;
    nextAdTagLoader.maybePreloadAds(C.usToMs(periodPositionUs), C.usToMs(period.durationUs));
  }

  /**
   * Default {@link ImaUtil.ImaFactory} for non-test usage, which delegates to {@link
   * ImaSdkFactory}.
   */
  private static final class DefaultImaFactory implements ImaUtil.ImaFactory {
    @Override
    public ImaSdkSettings createImaSdkSettings() {
      ImaSdkSettings settings = ImaSdkFactory.getInstance().createImaSdkSettings();
      settings.setLanguage(Util.getSystemLanguageCodes()[0]);
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
    public com.google.ads.interactivemedia.v3.api.AdsLoader createAdsLoader(
        Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer) {
      return ImaSdkFactory.getInstance()
          .createAdsLoader(context, imaSdkSettings, adDisplayContainer);
    }
  }
}
