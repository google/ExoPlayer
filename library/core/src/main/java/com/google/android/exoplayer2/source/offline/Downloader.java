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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.PriorityDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.util.ClosedSource;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import java.io.IOException;

/**
 * Base class for stream downloaders.
 *
 * <p>All of the methods are blocking. Also they are not thread safe, except {@link
 * #getTotalSegments()}, {@link #getDownloadedSegments()} and {@link #getDownloadedBytes()}.
 *
 * @param <M> The type of the manifest object.
 * @param <K> The type of the representation key object.
 */
@ClosedSource(reason = "Not ready yet")
public abstract class Downloader<M, K> {

  /**
   * Listener notified when download progresses.
   */
  public interface ProgressListener {
    /**
     * Called for the first time after the initialization and then after download of each segment.
     * It is called on the thread which invoked {@link #downloadRepresentations(ProgressListener)}.
     *
     * @param downloader The reporting instance.
     * @param totalSegments Total number of segments in the content.
     * @param downloadedSegments Total number of downloaded segments.
     * @param downloadedBytes Total number of downloaded bytes.
     * @see #downloadRepresentations(ProgressListener)
     */
    void onDownloadProgress(Downloader<?, ?> downloader, int totalSegments,
        int downloadedSegments, long downloadedBytes);
  }

  protected final Cache cache;
  protected final CacheDataSource dataSource;
  protected final CacheDataSource offlineDataSource;
  protected final PriorityTaskManager priorityTaskManager;
  protected final String manifestUri;

  protected volatile int totalSegments;
  protected volatile int downloadedSegments;
  protected volatile long downloadedBytes;

  /**
   * Constructs a Downloader.
   *
   * @param manifestUri The URI of the manifest to be downloaded.
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamDataSource A {@link DataSource} for downloading data.
   * @param cacheReadDataSource A {@link DataSource} for reading data from the cache.
   *     If null, a {@link FileDataSource} instance is created and used.
   * @param cacheWriteDataSink A {@link DataSink} for writing data to the cache. If
   *     null, a {@link CacheDataSink} instance is created and used.
   * @param priorityTaskManager If not null it's used to check whether it is allowed to proceed with
   *     download. Downloader priority is {@link C#PRIORITY_DOWNLOAD}.
   */
  public Downloader(String manifestUri, Cache cache, DataSource upstreamDataSource,
      @Nullable DataSource cacheReadDataSource, @Nullable DataSink cacheWriteDataSink,
      @Nullable PriorityTaskManager priorityTaskManager) {
    if (priorityTaskManager != null) {
      upstreamDataSource =
          new PriorityDataSource(upstreamDataSource, priorityTaskManager, C.PRIORITY_DOWNLOAD);
    } else {
      priorityTaskManager = new PriorityTaskManager(); // dummy PriorityTaskManager
    }
    if (cacheReadDataSource == null) {
      cacheReadDataSource = new FileDataSource();
    }
    if (cacheWriteDataSink == null) {
      cacheWriteDataSink = new CacheDataSink(cache,
          CacheDataSource.DEFAULT_MAX_CACHE_FILE_SIZE);
    }

    this.manifestUri = manifestUri;
    this.cache = cache;
    this.dataSource = new CacheDataSource(cache, upstreamDataSource, cacheReadDataSource,
            cacheWriteDataSink, CacheDataSource.FLAG_BLOCK_ON_CACHE, null);
    this.offlineDataSource = new CacheDataSource(cache, DummyDataSource.INSTANCE,
            cacheReadDataSource, null, CacheDataSource.FLAG_BLOCK_ON_CACHE, null);
    this.priorityTaskManager = priorityTaskManager;

    resetCounters();
  }

  /**
   * Downloads the manifest.
   *
   * @return The downloaded manifest.
   * @throws IOException If an error occurs reading data from the stream.
   */
  public abstract M downloadManifest() throws IOException;

  /**
   * Selects multiple representations pointed to by the keys for downloading, removing or checking
   * status. Any previous selection is cleared.
   */
  public abstract void selectRepresentations(K... keys);

  /**
   * Initializes the total segments, downloaded segments and downloaded bytes counters for the
   * selected representations.
   *
   * @throws IOException Thrown when there is an error while reading from cache.
   * @throws DownloaderException Thrown when a representation index is unbounded.
   * @throws InterruptedException If the thread has been interrupted.
   * @see #getTotalSegments()
   * @see #getDownloadedSegments()
   * @see #getDownloadedBytes()
   */
  public abstract void initStatus() throws DownloaderException, InterruptedException, IOException;

  /**
   * Downloads the content for the selected representations in sync or resumes a previously stopped
   * download.
   *
   * @throws IOException Thrown when there is an error while downloading.
   * @throws DownloaderException Thrown when no index data can be found for a representation or
   *     the index is unbounded.
   * @throws InterruptedException If the thread has been interrupted.
   */
  public abstract void downloadRepresentations(@Nullable ProgressListener listener)
      throws IOException, DownloaderException, InterruptedException;

  /**
   * Returns the total number of segments in the representations which are selected, or {@link
   * C#LENGTH_UNSET} if it hasn't been calculated yet.
   *
   * @see #initStatus()
   */
  public final int getTotalSegments() {
    return totalSegments;
  }

  /**
   * Returns the total number of downloaded segments in the representations which are selected, or
   * {@link C#LENGTH_UNSET} if it hasn't been calculated yet.
   *
   * @see #initStatus()
   */
  public final int getDownloadedSegments() {
    return downloadedSegments;
  }

  /**
   * Returns the total number of downloaded bytes in the representations which are selected, or
   * {@link C#LENGTH_UNSET} if it hasn't been calculated yet.
   *
   * @see #initStatus()
   */
  public final long getDownloadedBytes() {
    return downloadedBytes;
  }

  /**
   * Removes all representations declared in the manifest and the manifest itself.
   *
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  public abstract void removeAll() throws InterruptedException;

  protected final void resetCounters() {
    totalSegments = C.LENGTH_UNSET;
    downloadedSegments = C.LENGTH_UNSET;
    downloadedBytes = C.LENGTH_UNSET;
  }

}
