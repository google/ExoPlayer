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

import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import java.io.IOException;

/**
 * A downloader for progressive media streams.
 */
public final class ProgressiveDownloader implements Downloader {

  private static final int BUFFER_SIZE_BYTES = 128 * 1024;

  private final DataSpec dataSpec;
  private final Cache cache;
  private final CacheDataSource dataSource;
  private final PriorityTaskManager priorityTaskManager;
  private final CacheUtil.CachingCounters cachingCounters;

  /**
   * @param uri Uri of the data to be downloaded.
   * @param customCacheKey A custom key that uniquely identifies the original stream. Used for cache
   *     indexing. May be null.
   * @param constructorHelper a {@link DownloaderConstructorHelper} instance.
   */
  public ProgressiveDownloader(
      String uri, String customCacheKey, DownloaderConstructorHelper constructorHelper) {
    this.dataSpec = new DataSpec(Uri.parse(uri), 0, C.LENGTH_UNSET, customCacheKey, 0);
    this.cache = constructorHelper.getCache();
    this.dataSource = constructorHelper.buildCacheDataSource(false);
    this.priorityTaskManager = constructorHelper.getPriorityTaskManager();
    cachingCounters = new CachingCounters();
  }

  @Override
  public void init() {
    CacheUtil.getCached(dataSpec, cache, cachingCounters);
  }

  @Override
  public void download(@Nullable ProgressListener listener) throws InterruptedException,
      IOException {
    priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    try {
      byte[] buffer = new byte[BUFFER_SIZE_BYTES];
      CacheUtil.cache(dataSpec, cache, dataSource, buffer, priorityTaskManager, C.PRIORITY_DOWNLOAD,
          cachingCounters, true);
      // TODO: Work out how to call onDownloadProgress periodically during the download, or else
      // get rid of ProgressListener and move to a model where the manager periodically polls
      // Downloaders.
      if (listener != null) {
        listener.onDownloadProgress(this, 100, cachingCounters.contentLength);
      }
    } finally {
      priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
    }
  }

  @Override
  public void remove() {
    CacheUtil.remove(cache, CacheUtil.getKey(dataSpec));
  }

  @Override
  public long getDownloadedBytes() {
    return cachingCounters.totalCachedBytes();
  }

  @Override
  public float getDownloadPercentage() {
    long contentLength = cachingCounters.contentLength;
    return contentLength == C.LENGTH_UNSET ? Float.NaN
        : ((cachingCounters.totalCachedBytes() * 100f) / contentLength);
  }

}
