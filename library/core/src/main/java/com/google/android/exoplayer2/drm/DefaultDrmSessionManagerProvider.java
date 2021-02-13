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
package com.google.android.exoplayer2.drm;

import static com.google.android.exoplayer2.drm.DefaultDrmSessionManager.MODE_PLAYBACK;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import com.google.common.primitives.Ints;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Default implementation of {@link DrmSessionManagerProvider}. */
public final class DefaultDrmSessionManagerProvider implements DrmSessionManagerProvider {

  private final Object lock;

  @GuardedBy("lock")
  private MediaItem.@MonotonicNonNull DrmConfiguration drmConfiguration;

  @GuardedBy("lock")
  private @MonotonicNonNull DrmSessionManager manager;

  @Nullable private HttpDataSource.Factory drmHttpDataSourceFactory;
  @Nullable private String userAgent;

  public DefaultDrmSessionManagerProvider() {
    lock = new Object();
  }

  /**
   * Sets the {@link HttpDataSource.Factory} to be used for creating {@link HttpMediaDrmCallback
   * HttpMediaDrmCallbacks} which executes key and provisioning requests over HTTP. If {@code null}
   * is passed the {@link DefaultHttpDataSourceFactory} is used.
   *
   * @param drmHttpDataSourceFactory The HTTP data source factory or {@code null} to use {@link
   *     DefaultHttpDataSourceFactory}.
   */
  public void setDrmHttpDataSourceFactory(
      @Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
    this.drmHttpDataSourceFactory = drmHttpDataSourceFactory;
  }

  /**
   * Sets the optional user agent to be used for DRM requests.
   *
   * <p>In case a factory has been set by {@link
   * #setDrmHttpDataSourceFactory(HttpDataSource.Factory)}, this user agent is ignored.
   *
   * @param userAgent The user agent to be used for DRM requests.
   */
  public void setDrmUserAgent(@Nullable String userAgent) {
    this.userAgent = userAgent;
  }

  @Override
  public DrmSessionManager get(MediaItem mediaItem) {
    checkNotNull(mediaItem.playbackProperties);
    @Nullable
    MediaItem.DrmConfiguration drmConfiguration = mediaItem.playbackProperties.drmConfiguration;
    if (drmConfiguration == null || Util.SDK_INT < 18) {
      return DrmSessionManager.DRM_UNSUPPORTED;
    }

    synchronized (lock) {
      if (!Util.areEqual(drmConfiguration, this.drmConfiguration)) {
        this.drmConfiguration = drmConfiguration;
        this.manager = createManager(drmConfiguration);
      }
      return checkNotNull(this.manager);
    }
  }

  @RequiresApi(18)
  private DrmSessionManager createManager(MediaItem.DrmConfiguration drmConfiguration) {
    HttpDataSource.Factory dataSourceFactory =
        drmHttpDataSourceFactory != null
            ? drmHttpDataSourceFactory
            : new DefaultHttpDataSource.Factory().setUserAgent(userAgent);
    HttpMediaDrmCallback httpDrmCallback =
        new HttpMediaDrmCallback(
            drmConfiguration.licenseUri == null ? null : drmConfiguration.licenseUri.toString(),
            drmConfiguration.forceDefaultLicenseUri,
            dataSourceFactory);
    for (Map.Entry<String, String> entry : drmConfiguration.requestHeaders.entrySet()) {
      httpDrmCallback.setKeyRequestProperty(entry.getKey(), entry.getValue());
    }
    DefaultDrmSessionManager drmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(
                drmConfiguration.uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(drmConfiguration.multiSession)
            .setPlayClearSamplesWithoutKeys(drmConfiguration.playClearContentWithoutKey)
            .setUseDrmSessionsForClearContent(Ints.toArray(drmConfiguration.sessionForClearTypes))
            .build(httpDrmCallback);
    drmSessionManager.setMode(MODE_PLAYBACK, drmConfiguration.getKeySetId());
    return drmSessionManager;
  }
}
