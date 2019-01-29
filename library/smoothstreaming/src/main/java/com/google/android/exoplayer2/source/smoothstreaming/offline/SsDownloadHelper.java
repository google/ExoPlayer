/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.smoothstreaming.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;

/** A {@link DownloadHelper} for SmoothStreaming streams. */
public final class SsDownloadHelper extends DownloadHelper {

  /**
   * Creates a SmoothStreaming download helper.
   *
   * <p>The helper uses {@link DownloadHelper#DEFAULT_TRACK_SELECTOR_PARAMETERS} for track selection
   * and does not support drm protected content.
   *
   * @param uri A manifest {@link Uri}.
   * @param manifestDataSourceFactory A {@link DataSource.Factory} used to load the manifest.
   * @param renderersFactory The {@link RenderersFactory} creating the renderers for which tracks
   *     are selected.
   */
  public SsDownloadHelper(
      Uri uri, DataSource.Factory manifestDataSourceFactory, RenderersFactory renderersFactory) {
    this(
        uri,
        manifestDataSourceFactory,
        DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS,
        renderersFactory,
        /* drmSessionManager= */ null);
  }

  /**
   * Creates a SmoothStreaming download helper.
   *
   * @param uri A manifest {@link Uri}.
   * @param manifestDataSourceFactory A {@link DataSource.Factory} used to load the manifest.
   * @param trackSelectorParameters {@link DefaultTrackSelector.Parameters} for selecting tracks for
   *     downloading.
   * @param renderersFactory The {@link RenderersFactory} creating the renderers for which tracks
   *     are selected.
   * @param drmSessionManager An optional {@link DrmSessionManager} used by the renderers created by
   *     {@code renderersFactory}.
   */
  public SsDownloadHelper(
      Uri uri,
      DataSource.Factory manifestDataSourceFactory,
      DefaultTrackSelector.Parameters trackSelectorParameters,
      RenderersFactory renderersFactory,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    super(
        DownloadAction.TYPE_SS,
        uri,
        /* cacheKey= */ null,
        new SsMediaSource.Factory(manifestDataSourceFactory).createMediaSource(uri),
        trackSelectorParameters,
        renderersFactory,
        drmSessionManager);
  }
}
