/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.offline;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Default {@link DownloaderFactory}, supporting creation of progressive, DASH, HLS and
 * SmoothStreaming downloaders. Note that for the latter three, the corresponding library module
 * must be built into the application.
 */
public class DefaultDownloaderFactory implements DownloaderFactory {

  @Nullable private static final Constructor<? extends Downloader> DASH_DOWNLOADER_CONSTRUCTOR;
  @Nullable private static final Constructor<? extends Downloader> HLS_DOWNLOADER_CONSTRUCTOR;
  @Nullable private static final Constructor<? extends Downloader> SS_DOWNLOADER_CONSTRUCTOR;

  static {
    @Nullable Constructor<? extends Downloader> dashDownloaderConstructor = null;
    try {
      // LINT.IfChange
      dashDownloaderConstructor =
          getDownloaderConstructor(
              Class.forName("com.google.android.exoplayer2.source.dash.offline.DashDownloader"));
      // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the DASH module.
    }
    DASH_DOWNLOADER_CONSTRUCTOR = dashDownloaderConstructor;
    @Nullable Constructor<? extends Downloader> hlsDownloaderConstructor = null;
    try {
      // LINT.IfChange
      hlsDownloaderConstructor =
          getDownloaderConstructor(
              Class.forName("com.google.android.exoplayer2.source.hls.offline.HlsDownloader"));
      // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the HLS module.
    }
    HLS_DOWNLOADER_CONSTRUCTOR = hlsDownloaderConstructor;
    @Nullable Constructor<? extends Downloader> ssDownloaderConstructor = null;
    try {
      // LINT.IfChange
      ssDownloaderConstructor =
          getDownloaderConstructor(
              Class.forName(
                  "com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloader"));
      // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the SmoothStreaming module.
    }
    SS_DOWNLOADER_CONSTRUCTOR = ssDownloaderConstructor;
  }

  private final CacheDataSource.Factory cacheDataSourceFactory;
  @Nullable private final ExecutorService executorService;

  /**
   * Creates an instance.
   *
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which
   *     downloads will be written.
   */
  public DefaultDownloaderFactory(CacheDataSource.Factory cacheDataSourceFactory) {
    this(cacheDataSourceFactory, /* executorService= */ null);
  }

  /**
   * Creates an instance.
   *
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which
   *     downloads will be written.
   * @param executorService An {@link ExecutorService} used to make requests for media being
   *     downloaded. Must not be a direct executor, but may be {@code null} if each download should
   *     make its requests directly on its own thread. Providing an {@link ExecutorService} that
   *     uses multiple threads will speed up download tasks that can be split into smaller parts for
   *     parallel execution.
   */
  public DefaultDownloaderFactory(
      CacheDataSource.Factory cacheDataSourceFactory, @Nullable ExecutorService executorService) {
    this.cacheDataSourceFactory = cacheDataSourceFactory;
    this.executorService = executorService;
  }

  @Override
  public Downloader createDownloader(DownloadRequest request) {
    switch (request.type) {
      case DownloadRequest.TYPE_PROGRESSIVE:
        return new ProgressiveDownloader(
            request.uri, request.customCacheKey, cacheDataSourceFactory, executorService);
      case DownloadRequest.TYPE_DASH:
        return createDownloader(request, DASH_DOWNLOADER_CONSTRUCTOR);
      case DownloadRequest.TYPE_HLS:
        return createDownloader(request, HLS_DOWNLOADER_CONSTRUCTOR);
      case DownloadRequest.TYPE_SS:
        return createDownloader(request, SS_DOWNLOADER_CONSTRUCTOR);
      default:
        throw new IllegalArgumentException("Unsupported type: " + request.type);
    }
  }

  // Checker framework has no way of knowing whether arguments to newInstance can be null or not,
  // and so opts to be conservative and assumes they cannot. In this case executorService can be
  // nullable, so we suppress the warning.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private Downloader createDownloader(
      DownloadRequest request, @Nullable Constructor<? extends Downloader> constructor) {
    if (constructor == null) {
      throw new IllegalStateException("Module missing for: " + request.type);
    }
    try {
      return constructor.newInstance(
          request.uri, request.streamKeys, cacheDataSourceFactory, executorService);
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate downloader for: " + request.type, e);
    }
  }

  // LINT.IfChange
  private static Constructor<? extends Downloader> getDownloaderConstructor(Class<?> clazz) {
    try {
      return clazz
          .asSubclass(Downloader.class)
          .getConstructor(
              Uri.class, List.class, CacheDataSource.Factory.class, ExecutorService.class);
    } catch (NoSuchMethodException e) {
      // The downloader is present, but the expected constructor is missing.
      throw new RuntimeException("Downloader constructor missing", e);
    }
  }
  // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
}
