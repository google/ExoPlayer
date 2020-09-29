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

import android.content.Context;
import android.os.Looper;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;

/** @deprecated Use {@link SimpleExoPlayer.Builder} or {@link ExoPlayer.Builder} instead. */
@Deprecated
public final class ExoPlayerFactory {

  private ExoPlayerFactory() {}

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode) {
    RenderersFactory renderersFactory =
        new DefaultRenderersFactory(context).setExtensionRendererMode(extensionRendererMode);
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl);
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode,
      long allowedVideoJoiningTimeMs) {
    RenderersFactory renderersFactory =
        new DefaultRenderersFactory(context)
            .setExtensionRendererMode(extensionRendererMode)
            .setAllowedVideoJoiningTimeMs(allowedVideoJoiningTimeMs);
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl);
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(Context context) {
    return newSimpleInstance(context, new DefaultTrackSelector(context));
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector) {
    return newSimpleInstance(context, new DefaultRenderersFactory(context), trackSelector);
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context, RenderersFactory renderersFactory, TrackSelector trackSelector) {
    return newSimpleInstance(context, renderersFactory, trackSelector, new DefaultLoadControl());
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context, TrackSelector trackSelector, LoadControl loadControl) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl);
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl) {
    return newSimpleInstance(
        context, renderersFactory, trackSelector, loadControl, Util.getCurrentOrMainLooper());
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        bandwidthMeter,
        new AnalyticsCollector(Clock.DEFAULT),
        Util.getCurrentOrMainLooper());
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      AnalyticsCollector analyticsCollector) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        analyticsCollector,
        Util.getCurrentOrMainLooper());
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      Looper applicationLooper) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        new AnalyticsCollector(Clock.DEFAULT),
        applicationLooper);
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      AnalyticsCollector analyticsCollector,
      Looper applicationLooper) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        DefaultBandwidthMeter.getSingletonInstance(context),
        analyticsCollector,
        applicationLooper);
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      AnalyticsCollector analyticsCollector,
      Looper applicationLooper) {
    return new SimpleExoPlayer(
        context,
        renderersFactory,
        trackSelector,
        new DefaultMediaSourceFactory(context),
        loadControl,
        bandwidthMeter,
        analyticsCollector,
        /* useLazyPreparation= */ true,
        Clock.DEFAULT,
        applicationLooper);
  }

  /** @deprecated Use {@link ExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static ExoPlayer newInstance(
      Context context, Renderer[] renderers, TrackSelector trackSelector) {
    return newInstance(context, renderers, trackSelector, new DefaultLoadControl());
  }

  /** @deprecated Use {@link ExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static ExoPlayer newInstance(
      Context context, Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl) {
    return newInstance(
        context, renderers, trackSelector, loadControl, Util.getCurrentOrMainLooper());
  }

  /** @deprecated Use {@link ExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static ExoPlayer newInstance(
      Context context,
      Renderer[] renderers,
      TrackSelector trackSelector,
      LoadControl loadControl,
      Looper applicationLooper) {
    return newInstance(
        context,
        renderers,
        trackSelector,
        loadControl,
        DefaultBandwidthMeter.getSingletonInstance(context),
        applicationLooper);
  }

  /** @deprecated Use {@link ExoPlayer.Builder} instead. */
  @Deprecated
  public static ExoPlayer newInstance(
      Context context,
      Renderer[] renderers,
      TrackSelector trackSelector,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      Looper applicationLooper) {
    return new ExoPlayerImpl(
        renderers,
        trackSelector,
        new DefaultMediaSourceFactory(context),
        loadControl,
        bandwidthMeter,
        /* analyticsCollector= */ null,
        /* useLazyPreparation= */ true,
        SeekParameters.DEFAULT,
        /* pauseAtEndOfMediaItems= */ false,
        Clock.DEFAULT,
        applicationLooper);
  }
}
