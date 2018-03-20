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
package com.google.android.exoplayer2.demo;

import android.app.Application;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import java.io.File;

/**
 * Placeholder application to facilitate overriding Application methods for debugging and testing.
 */
public class DemoApplication extends Application {

  private static final String DOWNLOAD_CACHE_FOLDER = "downloads";

  protected String userAgent;
  private Cache downloadCache;

  @Override
  public void onCreate() {
    super.onCreate();
    userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
  }

  /** Returns a {@link DataSource.Factory}. */
  public DataSource.Factory buildDataSourceFactory(TransferListener<? super DataSource> listener) {
    DefaultDataSourceFactory upstreamFactory =
        new DefaultDataSourceFactory(this, listener, buildHttpDataSourceFactory(listener));
    return createReadOnlyCacheDataSource(upstreamFactory, getDownloadCache());
  }

  /** Returns a {@link HttpDataSource.Factory}. */
  public HttpDataSource.Factory buildHttpDataSourceFactory(
      TransferListener<? super DataSource> listener) {
    return new DefaultHttpDataSourceFactory(userAgent, listener);
  }

  /** Returns the download {@link Cache}. */
  public Cache getDownloadCache() {
    if (downloadCache == null) {
      File dir = getExternalFilesDir(null);
      if (dir == null) {
        dir = getFilesDir();
      }
      File downloadCacheFolder = new File(dir, DOWNLOAD_CACHE_FOLDER);
      downloadCache = new SimpleCache(downloadCacheFolder, new NoOpCacheEvictor());
    }
    return downloadCache;
  }

  public boolean useExtensionRenderers() {
    return "withExtensions".equals(BuildConfig.FLAVOR);
  }

  private static CacheDataSourceFactory createReadOnlyCacheDataSource(
      DefaultDataSourceFactory upstreamFactory, Cache cache) {
    return new CacheDataSourceFactory(
        cache,
        upstreamFactory,
        new FileDataSourceFactory(),
        /*cacheWriteDataSinkFactory=*/ null,
        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
        /*eventListener=*/ null);
  }
}
