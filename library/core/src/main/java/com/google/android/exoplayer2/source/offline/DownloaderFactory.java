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
package com.google.android.exoplayer2.source.offline;

import android.support.annotation.Nullable;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.util.ClosedSource;
import com.google.android.exoplayer2.util.PriorityTaskManager;

/**
 * A factory class that produces {@link Downloader}.
 */
@ClosedSource(reason = "Not ready yet")
public abstract class DownloaderFactory<T extends Downloader<?, ?>> {

  private final Factory upstreamDataSourceFactory;
  private final Factory cacheReadDataSourceFactory;
  private final DataSink.Factory cacheWriteDataSinkFactory;

  protected final Cache cache;
  protected final PriorityTaskManager priorityTaskManager;

  /**
   * Constructs a DashDownloaderFactory.
   *
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamDataSourceFactory A {@link Factory} for downloading data.
   */
  public DownloaderFactory(Cache cache, Factory upstreamDataSourceFactory) {
    this(cache, upstreamDataSourceFactory, null, null, null);
  }

  /**
   * Constructs a DashDownloaderFactory.
   *
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamDataSourceFactory A {@link Factory} for downloading data.
   * @param cacheReadDataSourceFactory A {@link Factory} for reading data from the cache.
   *     If null, null is passed to {@link Downloader} constructor.
   * @param cacheWriteDataSinkFactory A {@link DataSink.Factory} for writing data to the cache. If
   *     null, null is passed to {@link Downloader} constructor.
   * @param priorityTaskManager If one is given then the download priority is set lower than
   *     loading. If null, null is passed to {@link Downloader} constructor.
   */
  public DownloaderFactory(Cache cache, Factory upstreamDataSourceFactory,
      @Nullable Factory cacheReadDataSourceFactory,
      @Nullable DataSink.Factory cacheWriteDataSinkFactory,
      @Nullable PriorityTaskManager priorityTaskManager) {
    this.cache = cache;
    this.upstreamDataSourceFactory = upstreamDataSourceFactory;
    this.cacheReadDataSourceFactory = cacheReadDataSourceFactory;
    this.cacheWriteDataSinkFactory = cacheWriteDataSinkFactory;
    this.priorityTaskManager = priorityTaskManager;
  }

  /**
   * Creates a {@link Downloader} with the given manifest.
   *
   * @param manifestUri The URI of the manifest of the DASH to be downloaded.
   * @return A {@link Downloader}.
   */
  public final T create(String manifestUri) {
    return create(manifestUri,
        upstreamDataSourceFactory != null ? upstreamDataSourceFactory.createDataSource() : null,
        cacheReadDataSourceFactory != null ? cacheReadDataSourceFactory.createDataSource() : null,
        cacheWriteDataSinkFactory != null ? cacheWriteDataSinkFactory.createDataSink() : null);
  }

  protected abstract T create(String manifestUri, DataSource upstreamDataSource,
      DataSource cacheReadDataSource, DataSink cacheWriteDataSink);

}
