/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import java.util.List;

/**
 * Factory for creating {@link MediaSource}s from URIs.
 *
 * <h3>DrmSessionManager creation for protected content</h3>
 *
 * <p>In case a {@link DrmSessionManager} is passed to {@link
 * #setDrmSessionManager(DrmSessionManager)}, it will be used regardless of the drm configuration of
 * the media item.
 *
 * <p>For a media item with a {@link MediaItem.DrmConfiguration}, a {@link DefaultDrmSessionManager}
 * is created based on that configuration. The following setter can be used to optionally configure
 * the creation:
 *
 * <ul>
 *   <li>{@link #setDrmHttpDataSourceFactory(HttpDataSource.Factory)}: Sets the data source factory
 *       to be used by the {@link HttpMediaDrmCallback} for network requests (default: {@link
 *       DefaultHttpDataSourceFactory}).
 * </ul>
 */
public interface MediaSourceFactory {

  /** @deprecated Use {@link MediaItem.PlaybackProperties#streamKeys} instead. */
  @Deprecated
  default MediaSourceFactory setStreamKeys(@Nullable List<StreamKey> streamKeys) {
    return this;
  }

  /**
   * Sets the {@link DrmSessionManager} to use for all media items regardless of their {@link
   * MediaItem.DrmConfiguration}.
   *
   * @param drmSessionManager The {@link DrmSessionManager}, or {@code null} to use the {@link
   *     DefaultDrmSessionManager}.
   * @return This factory, for convenience.
   */
  MediaSourceFactory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager);

  /**
   * Sets the {@link HttpDataSource.Factory} to be used for creating {@link HttpMediaDrmCallback
   * HttpMediaDrmCallbacks} to execute key and provisioning requests over HTTP.
   *
   * <p>In case a {@link DrmSessionManager} has been set by {@link
   * #setDrmSessionManager(DrmSessionManager)}, this data source factory is ignored.
   *
   * @param drmHttpDataSourceFactory The HTTP data source factory, or {@code null} to use {@link
   *     DefaultHttpDataSourceFactory}.
   * @return This factory, for convenience.
   */
  MediaSourceFactory setDrmHttpDataSourceFactory(
      @Nullable HttpDataSource.Factory drmHttpDataSourceFactory);

  /**
   * Sets the optional user agent to be used for DRM requests.
   *
   * <p>In case a factory has been set by {@link
   * #setDrmHttpDataSourceFactory(HttpDataSource.Factory)} or a {@link DrmSessionManager} has been
   * set by {@link #setDrmSessionManager(DrmSessionManager)}, this user agent is ignored.
   *
   * @param userAgent The user agent to be used for DRM requests, or {@code null} to use the
   *     default.
   * @return This factory, for convenience.
   */
  MediaSourceFactory setDrmUserAgent(@Nullable String userAgent);

  /**
   * Sets an optional {@link LoadErrorHandlingPolicy}.
   *
   * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}, or {@code null} to use the
   *     {@link DefaultLoadErrorHandlingPolicy}.
   * @return This factory, for convenience.
   */
  MediaSourceFactory setLoadErrorHandlingPolicy(
      @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy);

  /**
   * Returns the {@link C.ContentType content types} supported by media sources created by this
   * factory.
   */
  @C.ContentType
  int[] getSupportedTypes();

  /**
   * Creates a new {@link MediaSource} with the specified {@link MediaItem}.
   *
   * @param mediaItem The media item to play.
   * @return The new {@link MediaSource media source}.
   */
  MediaSource createMediaSource(MediaItem mediaItem);

  /** @deprecated Use {@link #createMediaSource(MediaItem)} instead. */
  @Deprecated
  default MediaSource createMediaSource(Uri uri) {
    return createMediaSource(MediaItem.fromUri(uri));
  }
}
