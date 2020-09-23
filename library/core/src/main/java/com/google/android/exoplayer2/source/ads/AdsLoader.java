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
package com.google.android.exoplayer2.source.ads;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Interface for loaders of ads, which can be used with {@link AdsMediaSource}.
 *
 * <p>Ads loaders notify the {@link AdsMediaSource} about events via {@link EventListener}. In
 * particular, implementations must call {@link EventListener#onAdPlaybackState(AdPlaybackState)}
 * with a new copy of the current {@link AdPlaybackState} whenever further information about ads
 * becomes known (for example, when an ad media URI is available, or an ad has played to the end).
 *
 * <p>{@link #start(EventListener, AdViewProvider)} will be called when the ads media source first
 * initializes, at which point the loader can request ads. If the player enters the background,
 * {@link #stop()} will be called. Loaders should maintain any ad playback state in preparation for
 * a later call to {@link #start(EventListener, AdViewProvider)}. If an ad is playing when the
 * player is detached, update the ad playback state with the current playback position using {@link
 * AdPlaybackState#withAdResumePositionUs(long)}.
 *
 * <p>If {@link EventListener#onAdPlaybackState(AdPlaybackState)} has been called, the
 * implementation of {@link #start(EventListener, AdViewProvider)} should invoke the same listener
 * to provide the existing playback state to the new player.
 */
public interface AdsLoader {

  /** Listener for ads loader events. All methods are called on the main thread. */
  interface EventListener {

    /**
     * Called when the ad playback state has been updated.
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

  /** Provides information about views for the ad playback UI. */
  interface AdViewProvider {

    /**
     * Returns the {@link ViewGroup} on top of the player that will show any ad UI, or {@code null}
     * if playing audio-only ads. Any views on top of the returned view group must be described by
     * {@link OverlayInfo OverlayInfos} returned by {@link #getAdOverlayInfos()}, for accurate
     * viewability measurement.
     */
    @Nullable
    ViewGroup getAdViewGroup();

    /** @deprecated Use {@link #getAdOverlayInfos()} instead. */
    @Deprecated
    default View[] getAdOverlayViews() {
      return new View[0];
    }

    /**
     * Returns a list of {@link OverlayInfo} instances describing views that are on top of the ad
     * view group, but that are essential for controlling playback and should be excluded from ad
     * viewability measurements by the {@link AdsLoader} (if it supports this).
     *
     * <p>Each view must be either a fully transparent overlay (for capturing touch events), or a
     * small piece of transient UI that is essential to the user experience of playback (such as a
     * button to pause/resume playback or a transient full-screen or cast button). For more
     * information see the documentation for your ads loader.
     */
    @SuppressWarnings("deprecation")
    default List<OverlayInfo> getAdOverlayInfos() {
      ImmutableList.Builder<OverlayInfo> listBuilder = new ImmutableList.Builder<>();
      // Call through to deprecated version.
      for (View view : getAdOverlayViews()) {
        listBuilder.add(new OverlayInfo(view, OverlayInfo.PURPOSE_CONTROLS));
      }
      return listBuilder.build();
    }
  }

  /** Provides information about an overlay view shown on top of an ad view group. */
  final class OverlayInfo {

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PURPOSE_CONTROLS, PURPOSE_CLOSE_AD, PURPOSE_OTHER, PURPOSE_NOT_VISIBLE})
    public @interface Purpose {}
    /** Purpose for playback controls overlaying the player. */
    public static final int PURPOSE_CONTROLS = 0;
    /** Purpose for ad close buttons overlaying the player. */
    public static final int PURPOSE_CLOSE_AD = 1;
    /** Purpose for other overlays. */
    public static final int PURPOSE_OTHER = 2;
    /** Purpose for overlays that are not visible. */
    public static final int PURPOSE_NOT_VISIBLE = 3;

    /** The overlay view. */
    public final View view;
    /** The purpose of the overlay view. */
    @Purpose public final int purpose;
    /** An optional, detailed reason that the overlay view is needed. */
    @Nullable public final String reasonDetail;

    /**
     * Creates a new overlay info.
     *
     * @param view The view that is overlaying the player.
     * @param purpose The purpose of the view.
     */
    public OverlayInfo(View view, @Purpose int purpose) {
      this(view, purpose, /* detailedReason= */ null);
    }

    /**
     * Creates a new overlay info.
     *
     * @param view The view that is overlaying the player.
     * @param purpose The purpose of the view.
     * @param detailedReason An optional, detailed reason that the view is on top of the player. See
     *     the documentation for the {@link AdsLoader} implementation for more information on this
     *     string's formatting.
     */
    public OverlayInfo(View view, @Purpose int purpose, @Nullable String detailedReason) {
      this.view = view;
      this.purpose = purpose;
      this.reasonDetail = detailedReason;
    }
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
   * #start(EventListener, AdViewProvider)}. Subsequent calls may be ignored. Called on the main
   * thread by {@link AdsMediaSource}.
   *
   * @param contentTypes The supported content types for ad media. Each element must be one of
   *     {@link C#TYPE_DASH}, {@link C#TYPE_HLS}, {@link C#TYPE_SS} and {@link C#TYPE_OTHER}.
   */
  void setSupportedContentTypes(@C.ContentType int... contentTypes);

  /**
   * Starts using the ads loader for playback. Called on the main thread by {@link AdsMediaSource}.
   *
   * @param eventListener Listener for ads loader events.
   * @param adViewProvider Provider of views for the ad UI.
   */
  void start(EventListener eventListener, AdViewProvider adViewProvider);

  /**
   * Stops using the ads loader for playback and deregisters the event listener. Called on the main
   * thread by {@link AdsMediaSource}.
   */
  void stop();

  /**
   * Notifies the ads loader that preparation of an ad media period is complete. Called on the main
   * thread by {@link AdsMediaSource}.
   *
   * @param adGroupIndex The index of the ad group.
   * @param adIndexInAdGroup The index of the ad in the ad group.
   */
  void handlePrepareComplete(int adGroupIndex, int adIndexInAdGroup);

  /**
   * Notifies the ads loader that the player was not able to prepare media for a given ad.
   * Implementations should update the ad playback state as the specified ad has failed to load.
   * Called on the main thread by {@link AdsMediaSource}.
   *
   * @param adGroupIndex The index of the ad group.
   * @param adIndexInAdGroup The index of the ad in the ad group.
   * @param exception The preparation error.
   */
  void handlePrepareError(int adGroupIndex, int adIndexInAdGroup, IOException exception);
}
