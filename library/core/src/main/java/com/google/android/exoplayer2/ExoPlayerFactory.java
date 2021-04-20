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
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;

/** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
@Deprecated
public final class ExoPlayerFactory {

  private ExoPlayerFactory() {}

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
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl) {
    return new SimpleExoPlayer(
        context,
        renderersFactory,
        trackSelector,
        new DefaultMediaSourceFactory(context),
        loadControl,
        DefaultBandwidthMeter.getSingletonInstance(context),
        new AnalyticsCollector(Clock.DEFAULT),
        /* useLazyPreparation= */ true,
        Clock.DEFAULT,
        Util.getCurrentOrMainLooper());
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  public static ExoPlayer newInstance(
      Context context, Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl) {
    return new ExoPlayerImpl(
        renderers,
        trackSelector,
        new DefaultMediaSourceFactory(context),
        loadControl,
        DefaultBandwidthMeter.getSingletonInstance(context),
        /* analyticsCollector= */ null,
        /* useLazyPreparation= */ true,
        SeekParameters.DEFAULT,
        new DefaultLivePlaybackSpeedControl.Builder().build(),
        ExoPlayer.DEFAULT_RELEASE_TIMEOUT_MS,
        /* pauseAtEndOfMediaItems= */ false,
        Clock.DEFAULT,
        Util.getCurrentOrMainLooper(),
        /* wrappingPlayer= */ null,
        /* additionalPermanentAvailableCommands= */ Player.Commands.EMPTY);
  }
}
