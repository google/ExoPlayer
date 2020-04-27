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
package com.google.android.exoplayer2.source.dash.offline;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.offline.DownloadException;
import com.google.android.exoplayer2.offline.SegmentDownloader;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.dash.DashSegmentIndex;
import com.google.android.exoplayer2.source.dash.DashUtil;
import com.google.android.exoplayer2.source.dash.DashWrappingSegmentIndex;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A downloader for DASH streams.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SimpleCache cache = new SimpleCache(downloadFolder, new NoOpCacheEvictor(), databaseProvider);
 * CacheDataSource.Factory cacheDataSourceFactory =
 *     new CacheDataSource.Factory()
 *         .setCache(cache)
 *         .setUpstreamDataSourceFactory(new DefaultHttpDataSourceFactory(userAgent));
 * // Create a downloader for the first representation of the first adaptation set of the first
 * // period.
 * DashDownloader dashDownloader =
 *     new DashDownloader(
 *         manifestUrl, Collections.singletonList(new StreamKey(0, 0, 0)), cacheDataSourceFactory);
 * // Perform the download.
 * dashDownloader.download(progressListener);
 * // Use the downloaded data for playback.
 * DashMediaSource mediaSource =
 *     new DashMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem);
 * }</pre>
 */
public final class DashDownloader extends SegmentDownloader<DashManifest> {

  /**
   * @param manifestUri The {@link Uri} of the manifest to be downloaded.
   * @param streamKeys Keys defining which representations in the manifest should be selected for
   *     download. If empty, all representations are downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   */
  public DashDownloader(
      Uri manifestUri, List<StreamKey> streamKeys, CacheDataSource.Factory cacheDataSourceFactory) {
    this(manifestUri, streamKeys, cacheDataSourceFactory, Runnable::run);
  }

  /**
   * @param manifestUri The {@link Uri} of the manifest to be downloaded.
   * @param streamKeys Keys defining which representations in the manifest should be selected for
   *     download. If empty, all representations are downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   * @param executor An {@link Executor} used to make requests for the media being downloaded.
   *     Providing an {@link Executor} that uses multiple threads will speed up the download by
   *     allowing parts of it to be executed in parallel.
   */
  public DashDownloader(
      Uri manifestUri,
      List<StreamKey> streamKeys,
      CacheDataSource.Factory cacheDataSourceFactory,
      Executor executor) {
    super(manifestUri, streamKeys, cacheDataSourceFactory, executor);
  }

  @Override
  protected DashManifest getManifest(DataSource dataSource, DataSpec dataSpec) throws IOException {
    return ParsingLoadable.load(
        dataSource, new DashManifestParser(), dataSpec, C.DATA_TYPE_MANIFEST);
  }

  @Override
  protected List<Segment> getSegments(
      DataSource dataSource, DashManifest manifest, boolean allowIncompleteList)
      throws InterruptedException, IOException {
    ArrayList<Segment> segments = new ArrayList<>();
    for (int i = 0; i < manifest.getPeriodCount(); i++) {
      Period period = manifest.getPeriod(i);
      long periodStartUs = C.msToUs(period.startMs);
      long periodDurationUs = manifest.getPeriodDurationUs(i);
      List<AdaptationSet> adaptationSets = period.adaptationSets;
      for (int j = 0; j < adaptationSets.size(); j++) {
        addSegmentsForAdaptationSet(
            dataSource,
            adaptationSets.get(j),
            periodStartUs,
            periodDurationUs,
            allowIncompleteList,
            segments);
      }
    }
    return segments;
  }

  private static void addSegmentsForAdaptationSet(
      DataSource dataSource,
      AdaptationSet adaptationSet,
      long periodStartUs,
      long periodDurationUs,
      boolean allowIncompleteList,
      ArrayList<Segment> out)
      throws IOException, InterruptedException {
    for (int i = 0; i < adaptationSet.representations.size(); i++) {
      Representation representation = adaptationSet.representations.get(i);
      DashSegmentIndex index;
      try {
        index = getSegmentIndex(dataSource, adaptationSet.type, representation);
        if (index == null) {
          // Loading succeeded but there was no index.
          throw new DownloadException("Missing segment index");
        }
      } catch (IOException e) {
        if (!allowIncompleteList) {
          throw e;
        }
        // Generating an incomplete segment list is allowed. Advance to the next representation.
        continue;
      }

      int segmentCount = index.getSegmentCount(periodDurationUs);
      if (segmentCount == DashSegmentIndex.INDEX_UNBOUNDED) {
        throw new DownloadException("Unbounded segment index");
      }

      String baseUrl = representation.baseUrl;
      RangedUri initializationUri = representation.getInitializationUri();
      if (initializationUri != null) {
        addSegment(periodStartUs, baseUrl, initializationUri, out);
      }
      RangedUri indexUri = representation.getIndexUri();
      if (indexUri != null) {
        addSegment(periodStartUs, baseUrl, indexUri, out);
      }
      long firstSegmentNum = index.getFirstSegmentNum();
      long lastSegmentNum = firstSegmentNum + segmentCount - 1;
      for (long j = firstSegmentNum; j <= lastSegmentNum; j++) {
        addSegment(periodStartUs + index.getTimeUs(j), baseUrl, index.getSegmentUrl(j), out);
      }
    }
  }

  private static void addSegment(
      long startTimeUs, String baseUrl, RangedUri rangedUri, ArrayList<Segment> out) {
    DataSpec dataSpec =
        new DataSpec(rangedUri.resolveUri(baseUrl), rangedUri.start, rangedUri.length);
    out.add(new Segment(startTimeUs, dataSpec));
  }

  private static @Nullable DashSegmentIndex getSegmentIndex(
      DataSource dataSource, int trackType, Representation representation)
      throws IOException, InterruptedException {
    DashSegmentIndex index = representation.getIndex();
    if (index != null) {
      return index;
    }
    ChunkIndex seekMap = DashUtil.loadChunkIndex(dataSource, trackType, representation);
    return seekMap == null
        ? null
        : new DashWrappingSegmentIndex(seekMap, representation.presentationTimeOffsetUs);
  }

}
