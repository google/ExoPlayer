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
package com.google.android.exoplayer2.source.smoothstreaming.offline;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloader;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.TrackKey;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to download SmoothStreaming streams.
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
 * SsDownloader ssDownloader = new SsDownloader(manifestUrl, constructorHelper);
 * // Select the first track of the first stream element
 * ssDownloader.selectRepresentations(new TrackKey[] {new TrackKey(0, 0)});
 * ssDownloader.download(new ProgressListener() {
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
public final class SsDownloader extends SegmentDownloader<SsManifest, TrackKey> {

  /**
   * @see SegmentDownloader#SegmentDownloader(Uri, DownloaderConstructorHelper)
   */
  public SsDownloader(Uri manifestUri, DownloaderConstructorHelper constructorHelper)  {
    super(manifestUri, constructorHelper);
  }

  @Override
  public SsManifest getManifest(DataSource dataSource, Uri uri) throws IOException {
    DataSpec dataSpec = new DataSpec(uri,
        DataSpec.FLAG_ALLOW_CACHING_UNKNOWN_LENGTH | DataSpec.FLAG_ALLOW_GZIP);
    ParsingLoadable<SsManifest> loadable = new ParsingLoadable<>(dataSource, dataSpec,
        C.DATA_TYPE_MANIFEST, new SsManifestParser());
    loadable.load();
    return loadable.getResult();
  }

  @Override
  protected List<Segment> getAllSegments(DataSource dataSource, SsManifest manifest,
      boolean allowIndexLoadErrors) throws InterruptedException, IOException {
    ArrayList<Segment> segments = new ArrayList<>();
    for (int i = 0; i < manifest.streamElements.length; i++) {
      StreamElement streamElement = manifest.streamElements[i];
      for (int j = 0; j < streamElement.formats.length; j++) {
        segments.addAll(getSegments(dataSource, manifest, new TrackKey[] {new TrackKey(i, j)},
            allowIndexLoadErrors));
      }
    }
    return segments;
  }

  @Override
  protected List<Segment> getSegments(DataSource dataSource, SsManifest manifest,
      TrackKey[] keys, boolean allowIndexLoadErrors) throws InterruptedException, IOException {
    ArrayList<Segment> segments = new ArrayList<>();
    for (TrackKey key : keys) {
      StreamElement streamElement = manifest.streamElements[key.streamElementIndex];
      for (int i = 0; i < streamElement.chunkCount; i++) {
        segments.add(new Segment(streamElement.getStartTimeUs(i),
            new DataSpec(streamElement.buildRequestUri(key.trackIndex, i))));
      }
    }
    return segments;
  }

}
