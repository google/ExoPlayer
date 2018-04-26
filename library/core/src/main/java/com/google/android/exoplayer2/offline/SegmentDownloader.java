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
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for multi segment stream downloaders.
 *
 * @param <M> The type of the manifest object.
 * @param <K> The type of the representation key object.
 */
public abstract class SegmentDownloader<M extends FilterableManifest<M, K>, K>
    implements Downloader {

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
  private final ArrayList<K> keys;

  private M manifest;
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
    keys = new ArrayList<>();
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

  /** Returns keys for all representations. */
  public abstract K[] getAllRepresentationKeys() throws IOException;

  /**
   * Selects multiple representations pointed to by the keys for downloading, checking status. Any
   * previous selection is cleared. If keys array is null or empty then all representations are
   * downloaded.
   */
  public final void selectRepresentations(K[] keys) {
    this.keys.clear();
    Collections.addAll(this.keys, keys);
    resetCounters();
  }

  /**
   * Initializes the downloader for the selected representations.
   *
   * @throws IOException Thrown when there is an error downloading.
   * @throws InterruptedException If the thread has been interrupted.
   */
  @Override
  public final void init() throws IOException, InterruptedException {
    try {
      getManifestIfNeeded(true);
    } catch (IOException e) {
      // Either the manifest file isn't available offline or not parsable.
      return;
    }
    try {
      initStatus(true);
    } catch (IOException e) {
      resetCounters();
      throw e;
    }
  }

  /**
   * Downloads the content for the selected representations in sync or resumes a previously stopped
   * download.
   *
   * @throws IOException Thrown when there is an error downloading.
   * @throws InterruptedException If the thread has been interrupted.
   */
  @Override
  public final synchronized void download() throws IOException, InterruptedException {
    priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    try {
      getManifestIfNeeded(false);
      List<Segment> segments = initStatus(false);
      Collections.sort(segments);
      byte[] buffer = new byte[BUFFER_SIZE_BYTES];
      CachingCounters cachingCounters = new CachingCounters();
      for (int i = 0; i < segments.size(); i++) {
        CacheUtil.cache(segments.get(i).dataSpec, cache, dataSource, buffer,
            priorityTaskManager, C.PRIORITY_DOWNLOAD, cachingCounters, true);
        downloadedBytes += cachingCounters.newlyCachedBytes;
        downloadedSegments++;
      }
    } finally {
      priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
    }
  }

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
      return C.PERCENTAGE_UNSET;
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
        segments = getSegments(offlineDataSource, manifest, true).first;
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
   * Returns a list of all downloadable {@link Segment}s for a given manifest.
   *
   * @param dataSource The {@link DataSource} through which to load any required data.
   * @param manifest The manifest containing the segments.
   * @param allowIncompleteIndex Whether to continue in the case that a load error prevents all
   *     segments from being listed. If true then a partial segment list will be returned. If false
   *     an {@link IOException} will be thrown.
   * @throws InterruptedException Thrown if the thread was interrupted.
   * @throws IOException Thrown if {@code allowPartialIndex} is false and a load error occurs, or if
   *     the media is not in a form that allows for its segments to be listed.
   * @return A list of {@link Segment}s for given keys, and a boolean indicating whether the list is
   *     complete.
   */
  protected abstract Pair<List<Segment>, Boolean> getSegments(
      DataSource dataSource, M manifest, boolean allowIncompleteIndex)
      throws InterruptedException, IOException;

  private void resetCounters() {
    totalSegments = C.LENGTH_UNSET;
    downloadedSegments = 0;
    downloadedBytes = 0;
  }

  private void remove(Uri uri) {
    CacheUtil.remove(cache, CacheUtil.generateKey(uri));
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
    M filteredManifest = keys.isEmpty() ? manifest : manifest.copy(keys);
    Pair<List<Segment>, Boolean> result = getSegments(dataSource, filteredManifest, offline);
    List<Segment> segments = result.first;
    boolean isSegmentListComplete = result.second;
    CachingCounters cachingCounters = new CachingCounters();
    totalSegments = isSegmentListComplete ? segments.size() : C.LENGTH_UNSET;
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
