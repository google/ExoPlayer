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
   * The default minimum duration of data that must be buffered for playback to start or resume
   * following a user action such as a seek.
   */
  public static final int DEFAULT_MIN_BUFFER_MS = 2500;

  /**
   * The default minimum duration of data that must be buffered for playback to resume
   * after a player-invoked rebuffer (i.e. a rebuffer that occurs due to buffer depletion, and
   * not due to a user action such as starting playback or seeking).
   */
  public static final int DEFAULT_MIN_REBUFFER_MS = 5000;

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
    return newSimpleInstance(context, trackSelector, null);
  }

  /**
   * Obtains a {@link SimpleExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      DrmSessionManager drmSessionManager) {
    return newSimpleInstance(context, trackSelector, drmSessionManager, false);
  }

  /**
   * Obtains a {@link SimpleExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param preferExtensionDecoders True to prefer {@link TrackRenderer} instances defined in
   *     available extensions over those defined in the core library. Note that extensions must be
   *     included in the application build for setting this flag to have any effect.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      DrmSessionManager drmSessionManager, boolean preferExtensionDecoders) {
    return newSimpleInstance(context, trackSelector, drmSessionManager, preferExtensionDecoders,
        DEFAULT_MIN_BUFFER_MS, DEFAULT_MIN_REBUFFER_MS, DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS);
  }

  /**
   * Obtains a {@link SimpleExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param preferExtensionDecoders True to prefer {@link TrackRenderer} instances defined in
   *     available extensions over those defined in the core library. Note that extensions must be
   *     included in the application build for setting this flag to have any effect.
   * @param minBufferMs A minimum duration of data that must be buffered for playback to start
   *     or resume following a user action such as a seek.
   * @param minRebufferMs A minimum duration of data that must be buffered for playback to resume
   *     after a player-invoked rebuffer (i.e. a rebuffer that occurs due to buffer depletion, and
   *     not due to a user action such as starting playback or seeking).
   * @param allowedVideoJoiningTimeMs The maximum duration for which a video renderer can attempt to
   *     seamlessly join an ongoing playback.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      DrmSessionManager drmSessionManager, boolean preferExtensionDecoders, int minBufferMs,
      int minRebufferMs, long allowedVideoJoiningTimeMs) {
    return new SimpleExoPlayer(context, trackSelector, drmSessionManager, preferExtensionDecoders,
        minBufferMs, minRebufferMs, allowedVideoJoiningTimeMs);
  }

  /**
   * Obtains an {@link ExoPlayer} instance.
   * <p>
   * Must be called from a thread that has an associated {@link Looper}.
   *
   * @param renderers The {@link TrackRenderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param minBufferMs A minimum duration of data that must be buffered for playback to start
   *     or resume following a user action such as a seek.
   * @param minRebufferMs A minimum duration of data that must be buffered for playback to resume
   *     after a player-invoked rebuffer (i.e. a rebuffer that occurs due to buffer depletion, and
   *     not due to a user action such as starting playback or seeking).
   */
  public static ExoPlayer newInstance(TrackRenderer[] renderers, TrackSelector trackSelector,
      int minBufferMs, int minRebufferMs) {
    return new ExoPlayerImpl(renderers, trackSelector, minBufferMs, minRebufferMs);
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
    return new ExoPlayerImpl(renderers, trackSelector, DEFAULT_MIN_BUFFER_MS,
        DEFAULT_MIN_REBUFFER_MS);
  }

}
