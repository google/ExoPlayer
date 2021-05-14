/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot;
import com.google.ads.interactivemedia.v3.api.FriendlyObstruction;
import com.google.ads.interactivemedia.v3.api.FriendlyObstructionPurpose;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.UiElement;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ui.AdOverlayInfo;
import com.google.android.exoplayer2.upstream.DataSchemeDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Utilities for working with IMA SDK and IMA extension data types. */
/* package */ final class ImaUtil {

  /** Factory for objects provided by the IMA SDK. */
  public interface ImaFactory {
    /** Creates {@link ImaSdkSettings} for configuring the IMA SDK. */
    ImaSdkSettings createImaSdkSettings();
    /**
     * Creates {@link AdsRenderingSettings} for giving the {@link AdsManager} parameters that
     * control rendering of ads.
     */
    AdsRenderingSettings createAdsRenderingSettings();
    /**
     * Creates an {@link AdDisplayContainer} to hold the player for video ads, a container for
     * non-linear ads, and slots for companion ads.
     */
    AdDisplayContainer createAdDisplayContainer(ViewGroup container, VideoAdPlayer player);
    /** Creates an {@link AdDisplayContainer} to hold the player for audio ads. */
    AdDisplayContainer createAudioAdDisplayContainer(Context context, VideoAdPlayer player);
    /**
     * Creates a {@link FriendlyObstruction} to describe an obstruction considered "friendly" for
     * viewability measurement purposes.
     */
    FriendlyObstruction createFriendlyObstruction(
        View view,
        FriendlyObstructionPurpose friendlyObstructionPurpose,
        @Nullable String reasonDetail);
    /** Creates an {@link AdsRequest} to contain the data used to request ads. */
    AdsRequest createAdsRequest();
    /** Creates an {@link AdsLoader} for requesting ads using the specified settings. */
    AdsLoader createAdsLoader(
        Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer);
  }

  /** Stores configuration for ad loading and playback. */
  public static final class Configuration {

    public final long adPreloadTimeoutMs;
    public final int vastLoadTimeoutMs;
    public final int mediaLoadTimeoutMs;
    public final boolean focusSkipButtonWhenAvailable;
    public final boolean playAdBeforeStartPosition;
    public final int mediaBitrate;
    @Nullable public final Boolean enableContinuousPlayback;
    @Nullable public final List<String> adMediaMimeTypes;
    @Nullable public final Set<UiElement> adUiElements;
    @Nullable public final Collection<CompanionAdSlot> companionAdSlots;
    @Nullable public final AdErrorEvent.AdErrorListener applicationAdErrorListener;
    @Nullable public final AdEvent.AdEventListener applicationAdEventListener;
    @Nullable public final VideoAdPlayer.VideoAdPlayerCallback applicationVideoAdPlayerCallback;
    @Nullable public final ImaSdkSettings imaSdkSettings;
    public final boolean debugModeEnabled;

    public Configuration(
        long adPreloadTimeoutMs,
        int vastLoadTimeoutMs,
        int mediaLoadTimeoutMs,
        boolean focusSkipButtonWhenAvailable,
        boolean playAdBeforeStartPosition,
        int mediaBitrate,
        @Nullable Boolean enableContinuousPlayback,
        @Nullable List<String> adMediaMimeTypes,
        @Nullable Set<UiElement> adUiElements,
        @Nullable Collection<CompanionAdSlot> companionAdSlots,
        @Nullable AdErrorEvent.AdErrorListener applicationAdErrorListener,
        @Nullable AdEvent.AdEventListener applicationAdEventListener,
        @Nullable VideoAdPlayer.VideoAdPlayerCallback applicationVideoAdPlayerCallback,
        @Nullable ImaSdkSettings imaSdkSettings,
        boolean debugModeEnabled) {
      this.adPreloadTimeoutMs = adPreloadTimeoutMs;
      this.vastLoadTimeoutMs = vastLoadTimeoutMs;
      this.mediaLoadTimeoutMs = mediaLoadTimeoutMs;
      this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
      this.playAdBeforeStartPosition = playAdBeforeStartPosition;
      this.mediaBitrate = mediaBitrate;
      this.enableContinuousPlayback = enableContinuousPlayback;
      this.adMediaMimeTypes = adMediaMimeTypes;
      this.adUiElements = adUiElements;
      this.companionAdSlots = companionAdSlots;
      this.applicationAdErrorListener = applicationAdErrorListener;
      this.applicationAdEventListener = applicationAdEventListener;
      this.applicationVideoAdPlayerCallback = applicationVideoAdPlayerCallback;
      this.imaSdkSettings = imaSdkSettings;
      this.debugModeEnabled = debugModeEnabled;
    }
  }

  public static final int TIMEOUT_UNSET = -1;
  public static final int BITRATE_UNSET = -1;

  /**
   * Returns the IMA {@link FriendlyObstructionPurpose} corresponding to the given {@link
   * AdOverlayInfo#purpose}.
   */
  public static FriendlyObstructionPurpose getFriendlyObstructionPurpose(
      @AdOverlayInfo.Purpose int purpose) {
    switch (purpose) {
      case AdOverlayInfo.PURPOSE_CONTROLS:
        return FriendlyObstructionPurpose.VIDEO_CONTROLS;
      case AdOverlayInfo.PURPOSE_CLOSE_AD:
        return FriendlyObstructionPurpose.CLOSE_AD;
      case AdOverlayInfo.PURPOSE_NOT_VISIBLE:
        return FriendlyObstructionPurpose.NOT_VISIBLE;
      case AdOverlayInfo.PURPOSE_OTHER:
      default:
        return FriendlyObstructionPurpose.OTHER;
    }
  }

  /**
   * Returns the microsecond ad group timestamps corresponding to the specified cue points.
   *
   * @param cuePoints The cue points of the ads in seconds, provided by the IMA SDK.
   * @return The corresponding microsecond ad group timestamps.
   */
  public static long[] getAdGroupTimesUsForCuePoints(List<Float> cuePoints) {
    if (cuePoints.isEmpty()) {
      return new long[] {0L};
    }

    int count = cuePoints.size();
    long[] adGroupTimesUs = new long[count];
    int adGroupIndex = 0;
    for (int i = 0; i < count; i++) {
      double cuePoint = cuePoints.get(i);
      if (cuePoint == -1.0) {
        adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE;
      } else {
        adGroupTimesUs[adGroupIndex++] = Math.round(C.MICROS_PER_SECOND * cuePoint);
      }
    }
    // Cue points may be out of order, so sort them.
    Arrays.sort(adGroupTimesUs, 0, adGroupIndex);
    return adGroupTimesUs;
  }

  /** Returns an {@link AdsRequest} based on the specified ad tag {@link DataSpec}. */
  public static AdsRequest getAdsRequestForAdTagDataSpec(
      ImaFactory imaFactory, DataSpec adTagDataSpec) throws IOException {
    AdsRequest request = imaFactory.createAdsRequest();
    if (DataSchemeDataSource.SCHEME_DATA.equals(adTagDataSpec.uri.getScheme())) {
      DataSchemeDataSource dataSchemeDataSource = new DataSchemeDataSource();
      try {
        dataSchemeDataSource.open(adTagDataSpec);
        request.setAdsResponse(Util.fromUtf8Bytes(Util.readToEnd(dataSchemeDataSource)));
      } finally {
        dataSchemeDataSource.close();
      }
    } else {
      request.setAdTagUrl(adTagDataSpec.uri.toString());
    }
    return request;
  }

  /** Returns whether the ad error indicates that an entire ad group failed to load. */
  public static boolean isAdGroupLoadError(AdError adError) {
    // TODO: Find out what other errors need to be handled (if any), and whether each one relates to
    // a single ad, ad group or the whole timeline.
    return adError.getErrorCode() == AdError.AdErrorCode.VAST_LINEAR_ASSET_MISMATCH
        || adError.getErrorCode() == AdError.AdErrorCode.UNKNOWN_ERROR;
  }

  /** Returns the looper on which all IMA SDK interaction must occur. */
  public static Looper getImaLooper() {
    // IMA SDK callbacks occur on the main thread. This method can be used to check that the player
    // is using the same looper, to ensure all interaction with this class is on the main thread.
    return Looper.getMainLooper();
  }

  /** Returns a human-readable representation of a video progress update. */
  public static String getStringForVideoProgressUpdate(VideoProgressUpdate videoProgressUpdate) {
    if (VideoProgressUpdate.VIDEO_TIME_NOT_READY.equals(videoProgressUpdate)) {
      return "not ready";
    } else {
      return Util.formatInvariant(
          "%d ms of %d ms",
          videoProgressUpdate.getCurrentTimeMs(), videoProgressUpdate.getDurationMs());
    }
  }

  private ImaUtil() {}
}
