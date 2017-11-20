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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Base class for multi segment stream downloaders.
 *
 * <p>All of the methods are blocking. Also they are not thread safe, except {@link
 * #getTotalSegments()}, {@link #getDownloadedSegments()} and {@link #getDownloadedBytes()}.
 *
 * @param <M> The type of the manifest object.
 * @param <K> The type of the representation key object.
 */
public abstract class SegmentDownloader<M, K> implements Downloader {

  /** Smallest unit of content to be downloaded. */
  protected static class Segment implements Comparable<Segment> {
    /** The start time of the segment in microseconds. */
    public final long startTimeUs;

    /** The {@link DataSpec} of the segment. */
    public final DataSpec dataSpec;

    /** Constructs a Segment. */
    public Segment(long startTimeUs, DataSpec dataSpec) {
      this.startTimeUs = startTimeUs;
      this.dataSpec = dataSpec;
    }

    @Override
    public int compareTo(@NonNull Segment other) {
      long startOffsetDiff = startTimeUs - other.startTimeUs;
      return startOffsetDiff == 0 ? 0 : ((startOffsetDiff < 0) ? -1 : 1);
    }
  }

  private static final int BUFFER_SIZE_BYTES = 128 * 1024;

  private final Uri manifestUri;
  private final PriorityTaskManager priorityTaskManager;
  private final Cache cache;
  private final CacheDataSource dataSource;
  private final CacheDataSource offlineDataSource;

  private M manifest;
  private K[] keys;
  private volatile int totalSegments;
  private volatile int downloadedSegments;
  private volatile long downloadedBytes;

  /**
   * @param manifestUri The {@link Uri} of the manifest to be downloaded.
   * @param constructorHelper a {@link DownloaderConstructorHelper} instance.
   */
  public SegmentDownloader(Uri manifestUri, DownloaderConstructorHelper constructorHelper) {
    this.manifestUri = manifestUri;
    this.cache = constructorHelper.getCache();
    this.dataSource = constructorHelper.buildCacheDataSource(false);
    this.offlineDataSource = constructorHelper.buildCacheDataSource(true);
    this.priorityTaskManager = constructorHelper.getPriorityTaskManager();
    resetCounters();
  }

  /**
   * Returns the manifest. Downloads and parses it if necessary.
   *
   * @return The manifest.
   * @throws IOException If an error occurs reading data.
   */
  public final M getManifest() throws IOException {
    return getManifestIfNeeded(false);
  }

  /**
   * Selects multiple representations pointed to by the keys for downloading, checking status. Any
   * previous selection is cleared. If keys are null or empty, all representations are downloaded.
   */
  public final void selectRepresentations(K[] keys) {
    this.keys = keys != null ? keys.clone() : null;
    resetCounters();
  }

  /**
   * Initializes the total segments, downloaded segments and downloaded bytes counters for the
   * selected representations.
   *
   * @throws IOException Thrown when there is an io error while reading from cache.
   * @throws DownloadException Thrown if the media cannot be downloaded.
   * @throws InterruptedException If the thread has been interrupted.
   * @see #getTotalSegments()
   * @see #getDownloadedSegments()
   * @see #getDownloadedBytes()
   */
  @Override
  public final void init() throws InterruptedException, IOException {
    try {
      getManifestIfNeeded(true);
    } catch (IOException e) {
      // Either the manifest file isn't available offline or not parsable.
      return;
    }
    try {
      initStatus(true);
    } catch (IOException | InterruptedException e) {
      resetCounters();
      throw e;
    }
  }

  /**
   * Downloads the content for the selected representations in sync or resumes a previously stopped
   * download.
   *
   * @param listener If not null, called during download.
   * @throws IOException Thrown when there is an io error while downloading.
   * @throws DownloadException Thrown if the media cannot be downloaded.
   * @throws InterruptedException If the thread has been interrupted.
   */
  @Override
  public final synchronized void download(@Nullable ProgressListener listener)
      throws IOException, InterruptedException {
    priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    try {
      getManifestIfNeeded(false);
      List<Segment> segments = initStatus(false);
      notifyListener(listener); // Initial notification.
      Collections.sort(segments);
      byte[] buffer = new byte[BUFFER_SIZE_BYTES];
      CachingCounters cachingCounters = new CachingCounters();
      for (int i = 0; i < segments.size(); i++) {
        CacheUtil.cache(segments.get(i).dataSpec, cache, dataSource, buffer,
            priorityTaskManager, C.PRIORITY_DOWNLOAD, cachingCounters, true);
        downloadedBytes += cachingCounters.newlyCachedBytes;
        downloadedSegments++;
        notifyListener(listener);
      }
    } finally {
      priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
    }
  }

  /**
   * Returns the total number of segments in the representations which are selected, or {@link
   * C#LENGTH_UNSET} if it hasn't been calculated yet.
   *
   * @see #init()
   */
  public final int getTotalSegments() {
    return totalSegments;
  }

  /**
   * Returns the total number of downloaded segments in the representations which are selected, or
   * {@link C#LENGTH_UNSET} if it hasn't been calculated yet.
   *
   * @see #init()
   */
  public final int getDownloadedSegments() {
    return downloadedSegments;
  }

  /**
   * Returns the total number of downloaded bytes in the representations which are selected, or
   * {@link C#LENGTH_UNSET} if it hasn't been calculated yet.
   *
   * @see #init()
   */
  @Override
  public final long getDownloadedBytes() {
    return downloadedBytes;
  }

  @Override
  public float getDownloadPercentage() {
    // Take local snapshot of the volatile fields
    int totalSegments = this.totalSegments;
    int downloadedSegments = this.downloadedSegments;
    if (totalSegments == C.LENGTH_UNSET || downloadedSegments == C.LENGTH_UNSET) {
      return Float.NaN;
    }
    return totalSegments == 0 ? 100f : (downloadedSegments * 100f) / totalSegments;
  }

  @Override
  public final void remove() throws InterruptedException {
    try {
      getManifestIfNeeded(true);
    } catch (IOException e) {
      // Either the manifest file isn't available offline, or it's not parsable. Continue anyway to
      // reset the counters and attempt to remove the manifest file.
    }
    resetCounters();
    if (manifest != null) {
      List<Segment> segments = null;
      try {
        segments = getAllSegments(offlineDataSource, manifest, true);
      } catch (IOException e) {
        // Ignore exceptions. We do our best with what's available offline.
      }
      if (segments != null) {
        for (int i = 0; i < segments.size(); i++) {
          remove(segments.get(i).dataSpec.uri);
        }
      }
      manifest = null;
    }
    remove(manifestUri);
  }

  /**
   * Loads and parses the manifest.
   *
   * @param dataSource The {@link DataSource} through which to load.
   * @param uri The manifest uri.
   * @return The manifest.
   * @throws IOException If an error occurs reading data.
   */
  protected abstract M getManifest(DataSource dataSource, Uri uri) throws IOException;

  /**
   * Returns a list of {@link Segment}s for given keys.
   *
   * @param dataSource The {@link DataSource} through which to load any required data.
   * @param manifest The manifest containing the segments.
   * @param keys The selected representation keys.
   * @param allowIncompleteIndex Whether to continue in the case that a load error prevents all
   *     segments from being listed. If true then a partial segment list will be returned. If false
   *     an {@link IOException} will be thrown.
   * @throws InterruptedException Thrown if the thread was interrupted.
   * @throws IOException Thrown if {@code allowPartialIndex} is false and a load error occurs, or if
   *     the media is not in a form that allows for its segments to be listed.
   * @return A list of {@link Segment}s for given keys.
   */
  protected abstract List<Segment> getSegments(DataSource dataSource, M manifest, K[] keys,
      boolean allowIncompleteIndex) throws InterruptedException, IOException;

  /**
   * Returns a list of all segments.
   *
   * @see #getSegments(DataSource, M, Object[], boolean)
   */
  protected abstract List<Segment> getAllSegments(DataSource dataSource, M manifest,
      boolean allowPartialIndex) throws InterruptedException, IOException;

  private void resetCounters() {
    totalSegments = C.LENGTH_UNSET;
    downloadedSegments = C.LENGTH_UNSET;
    downloadedBytes = C.LENGTH_UNSET;
  }

  private void remove(Uri uri) {
    CacheUtil.remove(cache, CacheUtil.generateKey(uri));
  }

  private void notifyListener(ProgressListener listener) {
    if (listener != null) {
      listener.onDownloadProgress(this, getDownloadPercentage(), downloadedBytes);
    }
  }

  /**
   * Initializes totalSegments, downloadedSegments and downloadedBytes for selected representations.
   * If not offline then downloads missing metadata.
   *
   * @return A list of not fully downloaded segments.
   */
  private synchronized List<Segment> initStatus(boolean offline)
      throws IOException, InterruptedException {
    DataSource dataSource = getDataSource(offline);
    List<Segment> segments = keys != null && keys.length > 0
        ? getSegments(dataSource, manifest, keys, offline)
        : getAllSegments(dataSource, manifest, offline);
    CachingCounters cachingCounters = new CachingCounters();
    totalSegments = segments.size();
    downloadedSegments = 0;
    downloadedBytes = 0;
    for (int i = segments.size() - 1; i >= 0; i--) {
      Segment segment = segments.get(i);
      CacheUtil.getCached(segment.dataSpec, cache, cachingCounters);
      downloadedBytes += cachingCounters.alreadyCachedBytes;
      if (cachingCounters.alreadyCachedBytes == cachingCounters.contentLength) {
        // The segment is fully downloaded.
        downloadedSegments++;
        segments.remove(i);
      }
    }
    return segments;
  }

  private M getManifestIfNeeded(boolean offline) throws IOException {
    if (manifest == null) {
      manifest = getManifest(getDataSource(offline), manifestUri);
    }
    return manifest;
  }

  private DataSource getDataSource(boolean offline) {
    return offline ? offlineDataSource : dataSource;
  }

}
