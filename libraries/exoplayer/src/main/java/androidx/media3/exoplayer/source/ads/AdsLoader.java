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
package androidx.media3.exoplayer.source.ads;

import androidx.annotation.Nullable;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AdViewProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ads.AdsMediaSource.AdLoadException;
import java.io.IOException;

/**
 * Interface for loaders of ads, which can be used with {@link AdsMediaSource}.
 *
 * <p>Ads loaders notify the {@link AdsMediaSource} about events via {@link EventListener}. In
 * particular, implementations must call {@link EventListener#onAdPlaybackState(AdPlaybackState)}
 * with a new copy of the current {@link AdPlaybackState} whenever further information about ads
 * becomes known (for example, when an ad media URI is available, or an ad has played to the end).
 *
 * <p>{@link #start(AdsMediaSource, DataSpec, Object, AdViewProvider, EventListener)} will be called
 * when an ads media source first initializes, at which point the loader can request ads. If the
 * player enters the background, {@link #stop(AdsMediaSource, EventListener)} will be called.
 * Loaders should maintain any ad playback state in preparation for a later call to {@link
 * #start(AdsMediaSource, DataSpec, Object, AdViewProvider, EventListener)}. If an ad is playing
 * when the player is detached, update the ad playback state with the current playback position
 * using {@link AdPlaybackState#withAdResumePositionUs(long)}.
 *
 * <p>If {@link EventListener#onAdPlaybackState(AdPlaybackState)} has been called, the
 * implementation of {@link #start(AdsMediaSource, DataSpec, Object, AdViewProvider, EventListener)}
 * should invoke the same listener to provide the existing playback state to the new player.
 */
public interface AdsLoader {

  /**
   * Provides {@link AdsLoader} instances for media items that have {@link
   * MediaItem.LocalConfiguration#adsConfiguration ad tag URIs}.
   */
  interface Provider {

    /**
     * Returns an {@link AdsLoader} for the given {@link
     * MediaItem.LocalConfiguration#adsConfiguration ads configuration}, or {@code null} if no ads
     * loader is available for the given ads configuration.
     *
     * <p>This method is called each time a {@link MediaSource} is created from a {@link MediaItem}
     * that defines an {@link MediaItem.LocalConfiguration#adsConfiguration ads configuration}.
     */
    @Nullable
    AdsLoader getAdsLoader(MediaItem.AdsConfiguration adsConfiguration);
  }

  /** Listener for ads loader events. All methods are called on the main thread. */
  @UnstableApi
  interface EventListener {

    /**
     * Called when the ad playback state has been updated. The number of {@link
     * AdPlaybackState#adGroupCount ad groups} may not change after the first call.
     *
     * @param adPlaybackState The new ad playback state.
     */
    default void onAdPlaybackState(AdPlaybackState adPlaybackState) {}

    /**
     * Called when there was an error loading ads.
     *
     * @param error The error.
     * @param dataSpec The data spec associated with the load error.
     */
    default void onAdLoadError(AdLoadException error, DataSpec dataSpec) {}

    /** Called when the user clicks through an ad (for example, following a 'learn more' link). */
    default void onAdClicked() {}

    /** Called when the user taps a non-clickthrough part of an ad. */
    default void onAdTapped() {}
  }

  // Methods called by the application.

  /**
   * Sets the player that will play the loaded ads.
   *
   * <p>This method must be called before the player is prepared with media using this ads loader.
   *
   * <p>This method must also be called on the main thread and only players which are accessed on
   * the main thread are supported ({@code player.getApplicationLooper() ==
   * Looper.getMainLooper()}).
   *
   * @param player The player instance that will play the loaded ads. May be null to delete the
   *     reference to a previously set player.
   */
  void setPlayer(@Nullable Player player);

  /**
   * Releases the loader. Must be called by the application on the main thread when the instance is
   * no longer needed.
   */
  void release();

  // Methods called by AdsMediaSource.

  /**
   * Sets the supported content types for ad media. Must be called before the first call to {@link
   * #start(AdsMediaSource, DataSpec, Object, AdViewProvider, EventListener)}. Subsequent calls may
   * be ignored. Called on the main thread by {@link AdsMediaSource}.
   *
   * @param contentTypes The supported content types for ad media. Each element must be one of
   *     {@link C#CONTENT_TYPE_DASH}, {@link C#CONTENT_TYPE_HLS}, {@link C#CONTENT_TYPE_SS} and
   *     {@link C#CONTENT_TYPE_OTHER}.
   */
  @UnstableApi
  void setSupportedContentTypes(@C.ContentType int... contentTypes);

  /**
   * Starts using the ads loader for playback. Called on the main thread by {@link AdsMediaSource}.
   *
   * @param adsMediaSource The ads media source requesting to start loading ads.
   * @param adTagDataSpec A data spec for the ad tag to load.
   * @param adsId An opaque identifier for the ad playback state across start/stop calls.
   * @param adViewProvider Provider of views for the ad UI.
   * @param eventListener Listener for ads loader events.
   */
  @UnstableApi
  void start(
      AdsMediaSource adsMediaSource,
      DataSpec adTagDataSpec,
      Object adsId,
      AdViewProvider adViewProvider,
      EventListener eventListener);

  /**
   * Stops using the ads loader for playback and deregisters the event listener. Called on the main
   * thread by {@link AdsMediaSource}.
   *
   * @param adsMediaSource The ads media source requesting to stop loading/playing ads.
   * @param eventListener The ads media source's listener for ads loader events.
   */
  @UnstableApi
  void stop(AdsMediaSource adsMediaSource, EventListener eventListener);

  /**
   * Notifies the ads loader that preparation of an ad media period is complete. Called on the main
   * thread by {@link AdsMediaSource}.
   *
   * @param adsMediaSource The ads media source for which preparation of ad media completed.
   * @param adGroupIndex The index of the ad group.
   * @param adIndexInAdGroup The index of the ad in the ad group.
   */
  @UnstableApi
  void handlePrepareComplete(AdsMediaSource adsMediaSource, int adGroupIndex, int adIndexInAdGroup);

  /**
   * Notifies the ads loader that the player was not able to prepare media for a given ad.
   * Implementations should update the ad playback state as the specified ad has failed to load.
   * Called on the main thread by {@link AdsMediaSource}.
   *
   * @param adsMediaSource The ads media source for which preparation of ad media failed.
   * @param adGroupIndex The index of the ad group.
   * @param adIndexInAdGroup The index of the ad in the ad group.
   * @param exception The preparation error.
   */
  @UnstableApi
  void handlePrepareError(
      AdsMediaSource adsMediaSource, int adGroupIndex, int adIndexInAdGroup, IOException exception);
}
