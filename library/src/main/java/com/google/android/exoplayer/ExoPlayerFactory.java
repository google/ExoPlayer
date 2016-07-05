/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer;

import com.google.android.exoplayer.drm.DrmSessionManager;

import android.content.Context;
import android.os.Looper;

/**
 * A factory for instantiating {@link ExoPlayer} instances.
 */
public final class ExoPlayerFactory {

  /**
   * The default maximum duration for which a video renderer can attempt to seamlessly join an
   * ongoing playback.
   */
  public static final long DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000;

  private ExoPlayerFactory() {}

  /**
   * Obtains a {@link SimpleExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector) {
    return newSimpleInstance(context, trackSelector, new DefaultBufferingControl(), null);
  }

  /**
   * Obtains a {@link SimpleExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param bufferingControl The {@link BufferingControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      BufferingControl bufferingControl, DrmSessionManager drmSessionManager) {
    return newSimpleInstance(context, trackSelector, bufferingControl, drmSessionManager, false);
  }

  /**
   * Obtains a {@link SimpleExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param bufferingControl The {@link BufferingControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param preferExtensionDecoders True to prefer {@link TrackRenderer} instances defined in
   *     available extensions over those defined in the core library. Note that extensions must be
   *     included in the application build for setting this flag to have any effect.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      BufferingControl bufferingControl, DrmSessionManager drmSessionManager,
      boolean preferExtensionDecoders) {
    return newSimpleInstance(context, trackSelector, bufferingControl, drmSessionManager,
        preferExtensionDecoders, DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS);
  }

  /**
   * Obtains a {@link SimpleExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param bufferingControl The {@link BufferingControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param preferExtensionDecoders True to prefer {@link TrackRenderer} instances defined in
   *     available extensions over those defined in the core library. Note that extensions must be
   *     included in the application build for setting this flag to have any effect.
   * @param allowedVideoJoiningTimeMs The maximum duration for which a video renderer can attempt to
   *     seamlessly join an ongoing playback.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      BufferingControl bufferingControl, DrmSessionManager drmSessionManager,
      boolean preferExtensionDecoders, long allowedVideoJoiningTimeMs) {
    return new SimpleExoPlayer(context, trackSelector, bufferingControl, drmSessionManager,
        preferExtensionDecoders, allowedVideoJoiningTimeMs);
  }

  /**
   * Obtains an {@link ExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param renderers The {@link TrackRenderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   */
  public static ExoPlayer newInstance(TrackRenderer[] renderers, TrackSelector trackSelector) {
    return newInstance(renderers, trackSelector, new DefaultBufferingControl());
  }

  /**
   * Obtains an {@link ExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param renderers The {@link TrackRenderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param bufferingControl The {@link BufferingControl} that will be used by the instance.
   */
  public static ExoPlayer newInstance(TrackRenderer[] renderers, TrackSelector trackSelector,
      BufferingControl bufferingControl) {
    return new ExoPlayerImpl(renderers, trackSelector, bufferingControl);
  }

}
