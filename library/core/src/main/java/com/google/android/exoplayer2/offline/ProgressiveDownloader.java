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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** A downloader for progressive media streams. */
public final class ProgressiveDownloader implements Downloader {

  private static final int BUFFER_SIZE_BYTES = 128 * 1024;

  private final DataSpec dataSpec;
  private final CacheDataSource dataSource;
  private final AtomicBoolean isCanceled;

  @Nullable private volatile Thread downloadThread;

  /** @deprecated Use {@link #ProgressiveDownloader(MediaItem, CacheDataSource.Factory)} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public ProgressiveDownloader(
      Uri uri, @Nullable String customCacheKey, CacheDataSource.Factory cacheDataSourceFactory) {
    this(uri, customCacheKey, cacheDataSourceFactory, Runnable::run);
  }

  /**
   * Creates a new instance.
   *
   * @param mediaItem The media item with a uri to the stream to be downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   */
  public ProgressiveDownloader(
      MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory) {
    this(mediaItem, cacheDataSourceFactory, Runnable::run);
  }

  /**
   * @deprecated Use {@link #ProgressiveDownloader(MediaItem, CacheDataSource.Factory, Executor)}
   *     instead.
   */
  @Deprecated
  public ProgressiveDownloader(
      Uri uri,
      @Nullable String customCacheKey,
      CacheDataSource.Factory cacheDataSourceFactory,
      Executor executor) {
    this(
        new MediaItem.Builder().setUri(uri).setCustomCacheKey(customCacheKey).build(),
        cacheDataSourceFactory,
        executor);
  }

  /**
   * Creates a new instance.
   *
   * @param mediaItem The media item with a uri to the stream to be downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   * @param executor An {@link Executor} used to make requests for the media being downloaded. In
   *     the future, providing an {@link Executor} that uses multiple threads may speed up the
   *     download by allowing parts of it to be executed in parallel.
   */
  public ProgressiveDownloader(
      MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory, Executor executor) {
    Assertions.checkNotNull(mediaItem.playbackProperties);
    dataSpec =
        new DataSpec.Builder()
            .setUri(mediaItem.playbackProperties.uri)
            .setKey(mediaItem.playbackProperties.customCacheKey)
            .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
            .build();
    dataSource = cacheDataSourceFactory.createDataSourceForDownloading();
    isCanceled = new AtomicBoolean();
  }

  @Override
  public void download(@Nullable ProgressListener progressListener) throws IOException {
    downloadThread = Thread.currentThread();
    if (isCanceled.get()) {
      return;
    }
    @Nullable PriorityTaskManager priorityTaskManager = dataSource.getUpstreamPriorityTaskManager();
    if (priorityTaskManager != null) {
      priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    }
    try {
      CacheUtil.cache(
          dataSource,
          dataSpec,
          progressListener == null ? null : new ProgressForwarder(progressListener),
          isCanceled,
          /* enableEOFException= */ true,
          /* temporaryBuffer= */ new byte[BUFFER_SIZE_BYTES]);
    } finally {
      if (priorityTaskManager != null) {
        priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
      }
    }
  }

  @Override
  public void cancel() {
    isCanceled.set(true);
    @Nullable Thread downloadThread = this.downloadThread;
    if (downloadThread != null) {
      downloadThread.interrupt();
    }
  }

  @Override
  public void remove() {
    dataSource.getCache().removeResource(dataSource.getCacheKeyFactory().buildCacheKey(dataSpec));
  }

  private static final class ProgressForwarder implements CacheUtil.ProgressListener {

    private final ProgressListener progressListener;

    public ProgressForwarder(ProgressListener progressListener) {
      this.progressListener = progressListener;
    }

    @Override
    public void onProgress(long contentLength, long bytesCached, long newBytesCached) {
      float percentDownloaded =
          contentLength == C.LENGTH_UNSET || contentLength == 0
              ? C.PERCENTAGE_UNSET
              : ((bytesCached * 100f) / contentLength);
      progressListener.onProgress(contentLength, bytesCached, percentDownloaded);
    }
  }
}
