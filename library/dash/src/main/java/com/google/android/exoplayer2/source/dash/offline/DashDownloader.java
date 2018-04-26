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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.offline.DownloadException;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloader;
import com.google.android.exoplayer2.source.dash.DashSegmentIndex;
import com.google.android.exoplayer2.source.dash.DashUtil;
import com.google.android.exoplayer2.source.dash.DashWrappingSegmentIndex;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.RepresentationKey;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to download DASH streams.
 *
 * <p>Except {@link #getTotalSegments()}, {@link #getDownloadedSegments()} and {@link
 * #getDownloadedBytes()}, this class isn't thread safe.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SimpleCache cache = new SimpleCache(downloadFolder, new NoOpCacheEvictor());
 * DefaultHttpDataSourceFactory factory = new DefaultHttpDataSourceFactory("ExoPlayer", null);
 * DownloaderConstructorHelper constructorHelper =
 *     new DownloaderConstructorHelper(cache, factory);
 * DashDownloader dashDownloader = new DashDownloader(manifestUrl, constructorHelper);
 * // Select the first representation of the first adaptation set of the first period
 * dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
 * dashDownloader.download();
 * // Access downloaded data using CacheDataSource
 * CacheDataSource cacheDataSource =
 *     new CacheDataSource(cache, factory.createDataSource(), CacheDataSource.FLAG_BLOCK_ON_CACHE);
 * }</pre>
 */
public final class DashDownloader extends SegmentDownloader<DashManifest, RepresentationKey> {

  /**
   * @see SegmentDownloader#SegmentDownloader(Uri, DownloaderConstructorHelper)
   */
  public DashDownloader(Uri manifestUri, DownloaderConstructorHelper constructorHelper)  {
    super(manifestUri, constructorHelper);
  }

  @Override
  public RepresentationKey[] getAllRepresentationKeys() throws IOException {
    ArrayList<RepresentationKey> keys = new ArrayList<>();
    DashManifest manifest = getManifest();
    for (int periodIndex = 0; periodIndex < manifest.getPeriodCount(); periodIndex++) {
      List<AdaptationSet> adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
      for (int adaptationIndex = 0; adaptationIndex < adaptationSets.size(); adaptationIndex++) {
        int representationsCount = adaptationSets.get(adaptationIndex).representations.size();
        for (int i = 0; i < representationsCount; i++) {
          keys.add(new RepresentationKey(periodIndex, adaptationIndex, i));
        }
      }
    }
    return keys.toArray(new RepresentationKey[keys.size()]);
  }

  @Override
  protected DashManifest getManifest(DataSource dataSource, Uri uri) throws IOException {
    return DashUtil.loadManifest(dataSource, uri);
  }

  @Override
  protected List<Segment> getSegments(
      DataSource dataSource, DashManifest manifest, boolean allowIndexLoadErrors)
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
            allowIndexLoadErrors,
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
      boolean allowIndexLoadErrors,
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
        if (allowIndexLoadErrors) {
          // Loading failed, but load errors are allowed. Advance to the next representation.
          continue;
        } else {
          throw e;
        }
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
        new DataSpec(rangedUri.resolveUri(baseUrl), rangedUri.start, rangedUri.length, null);
    out.add(new Segment(startTimeUs, dataSpec));
  }

  private static DashSegmentIndex getSegmentIndex(
      DataSource dataSource, int trackType, Representation representation)
      throws IOException, InterruptedException {
    DashSegmentIndex index = representation.getIndex();
    if (index != null) {
      return index;
    }
    ChunkIndex seekMap = DashUtil.loadChunkIndex(dataSource, trackType, representation);
    return seekMap == null ? null : new DashWrappingSegmentIndex(seekMap);
  }

}
