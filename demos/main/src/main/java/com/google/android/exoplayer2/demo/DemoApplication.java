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
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.offline.ActionFile;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadIndexUtil;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;

/**
 * Placeholder application to facilitate overriding Application methods for debugging and testing.
 */
public class DemoApplication extends Application {

  private static final String TAG = "DemoApplication";
  private static final String DOWNLOAD_ACTION_FILE = "actions";
  private static final String DOWNLOAD_TRACKER_ACTION_FILE = "tracked_actions";
  private static final String DOWNLOAD_CONTENT_DIRECTORY = "downloads";
  private static final int MAX_SIMULTANEOUS_DOWNLOADS = 2;

  protected String userAgent;

  private File downloadDirectory;
  private Cache downloadCache;
  private DownloadManager downloadManager;
  private DownloadTracker downloadTracker;

  @Override
  public void onCreate() {
    super.onCreate();
    userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
  }

  /** Returns a {@link DataSource.Factory}. */
  public DataSource.Factory buildDataSourceFactory() {
    DefaultDataSourceFactory upstreamFactory =
        new DefaultDataSourceFactory(this, buildHttpDataSourceFactory());
    return buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache());
  }

  /** Returns a {@link HttpDataSource.Factory}. */
  public HttpDataSource.Factory buildHttpDataSourceFactory() {
    return new DefaultHttpDataSourceFactory(userAgent);
  }

  /** Returns whether extension renderers should be used. */
  public boolean useExtensionRenderers() {
    return "withExtensions".equals(BuildConfig.FLAVOR);
  }

  public RenderersFactory buildRenderersFactory(boolean preferExtensionRenderer) {
    @DefaultRenderersFactory.ExtensionRendererMode
    int extensionRendererMode =
        useExtensionRenderers()
            ? (preferExtensionRenderer
                ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
    return new DefaultRenderersFactory(this, extensionRendererMode);
  }

  public DownloadManager getDownloadManager() {
    initDownloadManager();
    return downloadManager;
  }

  public DownloadTracker getDownloadTracker() {
    initDownloadManager();
    return downloadTracker;
  }

  private synchronized void initDownloadManager() {
    if (downloadManager == null) {
      DefaultDownloadIndex downloadIndex = new DefaultDownloadIndex(new ExoDatabaseProvider(this));
      File actionFile = new File(getDownloadDirectory(), DOWNLOAD_TRACKER_ACTION_FILE);
      if (actionFile.exists()) {
        try {
          DownloadIndexUtil.upgradeActionFile(new ActionFile(actionFile), downloadIndex, null);
        } catch (IOException e) {
          Log.e(TAG, "Upgrading action file failed", e);
        }
        actionFile.delete();
      }
      DownloaderConstructorHelper downloaderConstructorHelper =
          new DownloaderConstructorHelper(getDownloadCache(), buildHttpDataSourceFactory());
      downloadManager =
          new DownloadManager(
              this,
              new File(getDownloadDirectory(), DOWNLOAD_ACTION_FILE),
              new DefaultDownloaderFactory(downloaderConstructorHelper),
              MAX_SIMULTANEOUS_DOWNLOADS,
              DownloadManager.DEFAULT_MIN_RETRY_COUNT,
              DownloadManager.DEFAULT_REQUIREMENTS);
      downloadTracker =
          new DownloadTracker(/* context= */ this, buildDataSourceFactory(), downloadIndex);
      downloadManager.addListener(downloadTracker);
    }
  }

  private synchronized Cache getDownloadCache() {
    if (downloadCache == null) {
      File downloadContentDirectory = new File(getDownloadDirectory(), DOWNLOAD_CONTENT_DIRECTORY);
      downloadCache = new SimpleCache(downloadContentDirectory, new NoOpCacheEvictor());
    }
    return downloadCache;
  }

  private File getDownloadDirectory() {
    if (downloadDirectory == null) {
      downloadDirectory = getExternalFilesDir(null);
      if (downloadDirectory == null) {
        downloadDirectory = getFilesDir();
      }
    }
    return downloadDirectory;
  }

  private static CacheDataSourceFactory buildReadOnlyCacheDataSource(
      DefaultDataSourceFactory upstreamFactory, Cache cache) {
    return new CacheDataSourceFactory(
        cache,
        upstreamFactory,
        new FileDataSourceFactory(),
        /* cacheWriteDataSinkFactory= */ null,
        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
        /* eventListener= */ null);
  }
}
