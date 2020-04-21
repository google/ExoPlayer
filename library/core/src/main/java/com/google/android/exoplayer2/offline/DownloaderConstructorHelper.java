/*
 * Copyright (C) 2017 The Android Open Source Project
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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSinkFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.google.android.exoplayer2.util.PriorityTaskManager;

/** A helper class that holds necessary parameters for {@link Downloader} construction. */
public final class DownloaderConstructorHelper {

  private final CacheDataSource.Factory onlineCacheDataSourceFactory;
  private final CacheDataSource.Factory offlineCacheDataSourceFactory;

  /**
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamFactory A {@link DataSource.Factory} for creating {@link DataSource}s for
   *     downloading data.
   */
  public DownloaderConstructorHelper(Cache cache, DataSource.Factory upstreamFactory) {
    this(
        cache,
        upstreamFactory,
        /* cacheReadDataSourceFactory= */ null,
        /* cacheWriteDataSinkFactory= */ null,
        /* priorityTaskManager= */ null);
  }

  /**
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamFactory A {@link DataSource.Factory} for creating {@link DataSource}s for
   *     downloading data.
   * @param cacheReadDataSourceFactory A {@link DataSource.Factory} for creating {@link DataSource}s
   *     for reading data from the cache. If null then a {@link FileDataSource.Factory} will be
   *     used.
   * @param cacheWriteDataSinkFactory A {@link DataSink.Factory} for creating {@link DataSource}s
   *     for writing data to the cache. If null then a {@link CacheDataSinkFactory} will be used.
   * @param priorityTaskManager A {@link PriorityTaskManager} to use when downloading. If non-null,
   *     downloaders will register as tasks with priority {@link C#PRIORITY_DOWNLOAD} whilst
   *     downloading.
   */
  public DownloaderConstructorHelper(
      Cache cache,
      DataSource.Factory upstreamFactory,
      @Nullable DataSource.Factory cacheReadDataSourceFactory,
      @Nullable DataSink.Factory cacheWriteDataSinkFactory,
      @Nullable PriorityTaskManager priorityTaskManager) {
    this(
        cache,
        upstreamFactory,
        cacheReadDataSourceFactory,
        cacheWriteDataSinkFactory,
        priorityTaskManager,
        /* cacheKeyFactory= */ null);
  }

  /**
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamFactory A {@link DataSource.Factory} for creating {@link DataSource}s for
   *     downloading data.
   * @param cacheReadDataSourceFactory A {@link DataSource.Factory} for creating {@link DataSource}s
   *     for reading data from the cache. If null then a {@link FileDataSource.Factory} will be
   *     used.
   * @param cacheWriteDataSinkFactory A {@link DataSink.Factory} for creating {@link DataSource}s
   *     for writing data to the cache. If null then a {@link CacheDataSinkFactory} will be used.
   * @param priorityTaskManager A {@link PriorityTaskManager} to use when downloading. If non-null,
   *     downloaders will register as tasks with priority {@link C#PRIORITY_DOWNLOAD} whilst
   *     downloading.
   * @param cacheKeyFactory An optional factory for cache keys.
   */
  public DownloaderConstructorHelper(
      Cache cache,
      DataSource.Factory upstreamFactory,
      @Nullable DataSource.Factory cacheReadDataSourceFactory,
      @Nullable DataSink.Factory cacheWriteDataSinkFactory,
      @Nullable PriorityTaskManager priorityTaskManager,
      @Nullable CacheKeyFactory cacheKeyFactory) {
    onlineCacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setUpstreamPriorityTaskManager(priorityTaskManager)
            .setUpstreamPriority(C.PRIORITY_DOWNLOAD)
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE);
    offlineCacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(cache)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE);
    if (cacheKeyFactory != null) {
      onlineCacheDataSourceFactory.setCacheKeyFactory(cacheKeyFactory);
      offlineCacheDataSourceFactory.setCacheKeyFactory(cacheKeyFactory);
    }
    if (cacheReadDataSourceFactory != null) {
      onlineCacheDataSourceFactory.setCacheReadDataSourceFactory(cacheReadDataSourceFactory);
      offlineCacheDataSourceFactory.setCacheReadDataSourceFactory(cacheReadDataSourceFactory);
    }
    if (cacheWriteDataSinkFactory != null) {
      onlineCacheDataSourceFactory.setCacheWriteDataSinkFactory(cacheWriteDataSinkFactory);
    }
  }

  /** Returns a new {@link CacheDataSource} instance. */
  public CacheDataSource createCacheDataSource() {
    return onlineCacheDataSourceFactory.createDataSource();
  }

  /**
   * Returns a new {@link CacheDataSource} instance which accesses cache read-only and throws an
   * exception on cache miss.
   */
  public CacheDataSource createOfflineCacheDataSource() {
    return offlineCacheDataSourceFactory.createDataSource();
  }
}
