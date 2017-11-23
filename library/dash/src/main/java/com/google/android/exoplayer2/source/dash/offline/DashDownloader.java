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
 * <p>Except {@link #getTotalSegments()}, {@link #getDownloadedSegments()} and
 * {@link #getDownloadedBytes()}, this class isn't thread safe.
 *
 * <p>Example usage:
 *
 * <pre>
 * {@code
 * SimpleCache cache = new SimpleCache(downloadFolder, new NoOpCacheEvictor());
 * DefaultHttpDataSourceFactory factory = new DefaultHttpDataSourceFactory("ExoPlayer", null);
 * DownloaderConstructorHelper constructorHelper =
 *     new DownloaderConstructorHelper(cache, factory);
 * DashDownloader dashDownloader = new DashDownloader(manifestUrl, constructorHelper);
 * // Select the first representation of the first adaptation set of the first period
 * dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
 * dashDownloader.download(new ProgressListener() {
 *   @Override
 *   public void onDownloadProgress(Downloader downloader, float downloadPercentage,
 *       long downloadedBytes) {
 *     // Invoked periodically during the download.
 *   }
 * });
 * // Access downloaded data using CacheDataSource
 * CacheDataSource cacheDataSource =
 *     new CacheDataSource(cache, factory.createDataSource(), CacheDataSource.FLAG_BLOCK_ON_CACHE);}
 * </pre>
 */
public final class DashDownloader extends SegmentDownloader<DashManifest, RepresentationKey> {

  /**
   * @see SegmentDownloader#SegmentDownloader(Uri, DownloaderConstructorHelper)
   */
  public DashDownloader(Uri manifestUri, DownloaderConstructorHelper constructorHelper)  {
    super(manifestUri, constructorHelper);
  }

  @Override
  public DashManifest getManifest(DataSource dataSource, Uri uri) throws IOException {
    return DashUtil.loadManifest(dataSource, uri);
  }

  @Override
  protected List<Segment> getAllSegments(DataSource dataSource, DashManifest manifest,
      boolean allowIndexLoadErrors) throws InterruptedException, IOException {
    ArrayList<Segment> segments = new ArrayList<>();
    for (int periodIndex = 0; periodIndex < manifest.getPeriodCount(); periodIndex++) {
      List<AdaptationSet> adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
      for (int adaptationIndex = 0; adaptationIndex < adaptationSets.size(); adaptationIndex++) {
        AdaptationSet adaptationSet = adaptationSets.get(adaptationIndex);
        RepresentationKey[] keys = new RepresentationKey[adaptationSet.representations.size()];
        for (int i = 0; i < keys.length; i++) {
          keys[i] = new RepresentationKey(periodIndex, adaptationIndex, i);
        }
        segments.addAll(getSegments(dataSource, manifest, keys, allowIndexLoadErrors));
      }
    }
    return segments;
  }

  @Override
  protected List<Segment> getSegments(DataSource dataSource, DashManifest manifest,
      RepresentationKey[] keys, boolean allowIndexLoadErrors)
      throws InterruptedException, IOException {
    ArrayList<Segment> segments = new ArrayList<>();
    for (RepresentationKey key : keys) {
      DashSegmentIndex index;
      try {
        index = getSegmentIndex(dataSource, manifest, key);
        if (index == null) {
          // Loading succeeded but there was no index. This is always a failure.
          throw new DownloadException("No index for representation: " + key);
        }
      } catch (IOException e) {
        if (allowIndexLoadErrors) {
          // Loading failed, but load errors are allowed. Advance to the next key.
          continue;
        } else {
          throw e;
        }
      }

      int segmentCount = index.getSegmentCount(C.TIME_UNSET);
      if (segmentCount == DashSegmentIndex.INDEX_UNBOUNDED) {
        throw new DownloadException("Unbounded index for representation: " + key);
      }

      Period period = manifest.getPeriod(key.periodIndex);
      Representation representation = period.adaptationSets.get(key.adaptationSetIndex)
          .representations.get(key.representationIndex);
      long startUs = C.msToUs(period.startMs);
      String baseUrl = representation.baseUrl;
      RangedUri initializationUri = representation.getInitializationUri();
      if (initializationUri != null) {
        addSegment(segments, startUs, baseUrl, initializationUri);
      }
      RangedUri indexUri = representation.getIndexUri();
      if (indexUri != null) {
        addSegment(segments, startUs, baseUrl, indexUri);
      }

      int firstSegmentNum = index.getFirstSegmentNum();
      int lastSegmentNum = firstSegmentNum + segmentCount - 1;
      for (int j = firstSegmentNum; j <= lastSegmentNum; j++) {
        addSegment(segments, startUs + index.getTimeUs(j), baseUrl, index.getSegmentUrl(j));
      }
    }
    return segments;
  }

  /**
   * Returns DashSegmentIndex for given representation.
   */
  private DashSegmentIndex getSegmentIndex(DataSource dataSource, DashManifest manifest,
      RepresentationKey key) throws IOException, InterruptedException {
    AdaptationSet adaptationSet = manifest.getPeriod(key.periodIndex).adaptationSets.get(
        key.adaptationSetIndex);
    Representation representation = adaptationSet.representations.get(key.representationIndex);
    DashSegmentIndex index = representation.getIndex();
    if (index != null) {
      return index;
    }
    ChunkIndex seekMap = DashUtil.loadChunkIndex(dataSource, adaptationSet.type, representation);
    return seekMap == null ? null : new DashWrappingSegmentIndex(seekMap);
  }

  private static void addSegment(ArrayList<Segment> segments, long startTimeUs, String baseUrl,
      RangedUri rangedUri) {
    DataSpec dataSpec = new DataSpec(rangedUri.resolveUri(baseUrl), rangedUri.start,
        rangedUri.length, null);
    segments.add(new Segment(startTimeUs, dataSpec));
  }

}
