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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.upstream.ParsingLoadable.Parser;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.google.android.exoplayer2.upstream.cache.CacheWriter;
import com.google.android.exoplayer2.upstream.cache.ContentMetadata;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for multi segment stream downloaders.
 *
 * @param <M> The type of the manifest object.
 */
public abstract class SegmentDownloader<M extends FilterableManifest<M>> implements Downloader {

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
    public int compareTo(Segment other) {
      return Util.compareLong(startTimeUs, other.startTimeUs);
    }
  }

  private static final int BUFFER_SIZE_BYTES = 128 * 1024;
  private static final long MAX_MERGED_SEGMENT_START_TIME_DIFF_US = 20 * C.MICROS_PER_SECOND;

  private final DataSpec manifestDataSpec;
  private final Parser<M> manifestParser;
  private final ArrayList<StreamKey> streamKeys;
  private final CacheDataSource.Factory cacheDataSourceFactory;
  private final Executor executor;
  private final AtomicBoolean isCanceled;

  /**
   * @param mediaItem The {@link MediaItem} to be downloaded.
   * @param manifestParser A parser for manifests belonging to the media to be downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   * @param executor An {@link Executor} used to make requests for the media being downloaded.
   *     Providing an {@link Executor} that uses multiple threads will speed up the download by
   *     allowing parts of it to be executed in parallel.
   */
  public SegmentDownloader(
      MediaItem mediaItem,
      Parser<M> manifestParser,
      CacheDataSource.Factory cacheDataSourceFactory,
      Executor executor) {
    checkNotNull(mediaItem.playbackProperties);
    this.manifestDataSpec = getCompressibleDataSpec(mediaItem.playbackProperties.uri);
    this.manifestParser = manifestParser;
    this.streamKeys = new ArrayList<>(mediaItem.playbackProperties.streamKeys);
    this.cacheDataSourceFactory = cacheDataSourceFactory;
    this.executor = executor;
    isCanceled = new AtomicBoolean();
  }

  @Override
  public final void download(@Nullable ProgressListener progressListener) throws IOException {
    @Nullable
    PriorityTaskManager priorityTaskManager =
        cacheDataSourceFactory.getUpstreamPriorityTaskManager();
    if (priorityTaskManager != null) {
      priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    }
    try {
      Cache cache = Assertions.checkNotNull(cacheDataSourceFactory.getCache());
      CacheKeyFactory cacheKeyFactory = cacheDataSourceFactory.getCacheKeyFactory();
      CacheDataSource dataSource = cacheDataSourceFactory.createDataSourceForDownloading();

      // Get the manifest and all of the segments.
      M manifest = getManifest(dataSource, manifestDataSpec);
      if (!streamKeys.isEmpty()) {
        manifest = manifest.copy(streamKeys);
      }
      List<Segment> segments = getSegments(dataSource, manifest, /* allowIncompleteList= */ false);
      Collections.sort(segments);
      mergeSegments(segments, cacheKeyFactory);

      // Scan the segments, removing any that are fully downloaded.
      int totalSegments = segments.size();
      int segmentsDownloaded = 0;
      long contentLength = 0;
      long bytesDownloaded = 0;
      for (int i = segments.size() - 1; i >= 0; i--) {
        DataSpec dataSpec = segments.get(i).dataSpec;
        String cacheKey = cacheKeyFactory.buildCacheKey(dataSpec);
        long segmentLength = dataSpec.length;
        if (segmentLength == C.LENGTH_UNSET) {
          long resourceLength =
              ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey));
          if (resourceLength != C.LENGTH_UNSET) {
            segmentLength = resourceLength - dataSpec.position;
          }
        }
        long segmentBytesDownloaded =
            cache.getCachedBytes(cacheKey, dataSpec.position, segmentLength);
        bytesDownloaded += segmentBytesDownloaded;
        if (segmentLength != C.LENGTH_UNSET) {
          if (segmentLength == segmentBytesDownloaded) {
            // The segment is fully downloaded.
            segmentsDownloaded++;
            segments.remove(i);
          }
          if (contentLength != C.LENGTH_UNSET) {
            contentLength += segmentLength;
          }
        } else {
          contentLength = C.LENGTH_UNSET;
        }
      }

      // Download the segments.
      @Nullable
      ProgressNotifier progressNotifier =
          progressListener != null
              ? new ProgressNotifier(
                  progressListener,
                  contentLength,
                  totalSegments,
                  bytesDownloaded,
                  segmentsDownloaded)
              : null;
      byte[] temporaryBuffer = new byte[BUFFER_SIZE_BYTES];
      int segmentIndex = 0;
      while (!isCanceled.get() && segmentIndex < segments.size()) {
        if (priorityTaskManager != null) {
          priorityTaskManager.proceed(dataSource.getUpstreamPriority());
        }
        CacheWriter cacheWriter =
            new CacheWriter(
                dataSource,
                segments.get(segmentIndex).dataSpec,
                /* allowShortContent= */ false,
                isCanceled,
                temporaryBuffer,
                progressNotifier);
        try {
          cacheWriter.cache();
          segmentIndex++;
          if (progressNotifier != null) {
            progressNotifier.onSegmentDownloaded();
          }
        } catch (PriorityTooLowException e) {
          // The next loop iteration will block until the task is able to proceed, then try and
          // download the same segment again.
        }
      }
    } catch (InterruptedException e) {
      // The download was canceled.
    } finally {
      if (priorityTaskManager != null) {
        priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
      }
    }
  }

  @Override
  public void cancel() {
    isCanceled.set(true);
  }

  @Override
  public final void remove() {
    Cache cache = Assertions.checkNotNull(cacheDataSourceFactory.getCache());
    CacheKeyFactory cacheKeyFactory = cacheDataSourceFactory.getCacheKeyFactory();
    CacheDataSource dataSource = cacheDataSourceFactory.createDataSourceForRemovingDownload();
    try {
      M manifest = getManifest(dataSource, manifestDataSpec);
      List<Segment> segments = getSegments(dataSource, manifest, true);
      for (int i = 0; i < segments.size(); i++) {
        cache.removeResource(cacheKeyFactory.buildCacheKey(segments.get(i).dataSpec));
      }
    } catch (IOException e) {
      // Ignore exceptions when removing.
    } finally {
      // Always attempt to remove the manifest.
      cache.removeResource(cacheKeyFactory.buildCacheKey(manifestDataSpec));
    }
  }

  // Internal methods.

  /**
   * Loads and parses a manifest.
   *
   * @param dataSource The {@link DataSource} through which to load.
   * @param dataSpec The manifest {@link DataSpec}.
   * @return The manifest.
   * @throws IOException If an error occurs reading data.
   */
  protected final M getManifest(DataSource dataSource, DataSpec dataSpec) throws IOException {
    return ParsingLoadable.load(dataSource, manifestParser, dataSpec, C.DATA_TYPE_MANIFEST);
  }

  /**
   * Returns a list of all downloadable {@link Segment}s for a given manifest.
   *
   * @param dataSource The {@link DataSource} through which to load any required data.
   * @param manifest The manifest containing the segments.
   * @param allowIncompleteList Whether to continue in the case that a load error prevents all
   *     segments from being listed. If true then a partial segment list will be returned. If false
   *     an {@link IOException} will be thrown.
   * @return The list of downloadable {@link Segment}s.
   * @throws IOException Thrown if {@code allowPartialIndex} is false and a load error occurs, or if
   *     the media is not in a form that allows for its segments to be listed.
   */
  protected abstract List<Segment> getSegments(
      DataSource dataSource, M manifest, boolean allowIncompleteList) throws IOException;

  protected static DataSpec getCompressibleDataSpec(Uri uri) {
    return new DataSpec.Builder().setUri(uri).setFlags(DataSpec.FLAG_ALLOW_GZIP).build();
  }

  private static void mergeSegments(List<Segment> segments, CacheKeyFactory keyFactory) {
    HashMap<String, Integer> lastIndexByCacheKey = new HashMap<>();
    int nextOutIndex = 0;
    for (int i = 0; i < segments.size(); i++) {
      Segment segment = segments.get(i);
      String cacheKey = keyFactory.buildCacheKey(segment.dataSpec);
      @Nullable Integer lastIndex = lastIndexByCacheKey.get(cacheKey);
      @Nullable Segment lastSegment = lastIndex == null ? null : segments.get(lastIndex);
      if (lastSegment == null
          || segment.startTimeUs > lastSegment.startTimeUs + MAX_MERGED_SEGMENT_START_TIME_DIFF_US
          || !canMergeSegments(lastSegment.dataSpec, segment.dataSpec)) {
        lastIndexByCacheKey.put(cacheKey, nextOutIndex);
        segments.set(nextOutIndex, segment);
        nextOutIndex++;
      } else {
        long mergedLength =
            segment.dataSpec.length == C.LENGTH_UNSET
                ? C.LENGTH_UNSET
                : lastSegment.dataSpec.length + segment.dataSpec.length;
        DataSpec mergedDataSpec = lastSegment.dataSpec.subrange(/* offset= */ 0, mergedLength);
        segments.set(
            Assertions.checkNotNull(lastIndex),
            new Segment(lastSegment.startTimeUs, mergedDataSpec));
      }
    }
    Util.removeRange(segments, /* fromIndex= */ nextOutIndex, /* toIndex= */ segments.size());
  }

  private static boolean canMergeSegments(DataSpec dataSpec1, DataSpec dataSpec2) {
    return dataSpec1.uri.equals(dataSpec2.uri)
        && dataSpec1.length != C.LENGTH_UNSET
        && (dataSpec1.position + dataSpec1.length == dataSpec2.position)
        && Util.areEqual(dataSpec1.key, dataSpec2.key)
        && dataSpec1.flags == dataSpec2.flags
        && dataSpec1.httpMethod == dataSpec2.httpMethod
        && dataSpec1.httpRequestHeaders.equals(dataSpec2.httpRequestHeaders);
  }

  private static final class ProgressNotifier implements CacheWriter.ProgressListener {

    private final ProgressListener progressListener;

    private final long contentLength;
    private final int totalSegments;

    private long bytesDownloaded;
    private int segmentsDownloaded;

    public ProgressNotifier(
        ProgressListener progressListener,
        long contentLength,
        int totalSegments,
        long bytesDownloaded,
        int segmentsDownloaded) {
      this.progressListener = progressListener;
      this.contentLength = contentLength;
      this.totalSegments = totalSegments;
      this.bytesDownloaded = bytesDownloaded;
      this.segmentsDownloaded = segmentsDownloaded;
    }

    @Override
    public void onProgress(long requestLength, long bytesCached, long newBytesCached) {
      bytesDownloaded += newBytesCached;
      progressListener.onProgress(contentLength, bytesDownloaded, getPercentDownloaded());
    }

    public void onSegmentDownloaded() {
      segmentsDownloaded++;
      progressListener.onProgress(contentLength, bytesDownloaded, getPercentDownloaded());
    }

    private float getPercentDownloaded() {
      if (contentLength != C.LENGTH_UNSET && contentLength != 0) {
        return (bytesDownloaded * 100f) / contentLength;
      } else if (totalSegments != 0) {
        return (segmentsDownloaded * 100f) / totalSegments;
      } else {
        return C.PERCENTAGE_UNSET;
      }
    }
  }
}
