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
package com.google.android.exoplayer2.source.hls.offline;

import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.ENC_MEDIA_PLAYLIST_DATA;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.ENC_MEDIA_PLAYLIST_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_0_DIR;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_0_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_1_DIR;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_1_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_2_DIR;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_2_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_3_DIR;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_3_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_DATA;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MULTIVARIANT_MEDIA_PLAYLIST_1_INDEX;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MULTIVARIANT_MEDIA_PLAYLIST_2_INDEX;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MULTIVARIANT_PLAYLIST_DATA;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MULTIVARIANT_PLAYLIST_URI;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCacheEmpty;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCachedData;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.Downloader;
import com.google.android.exoplayer2.offline.DownloaderFactory;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.hls.playlist.HlsMultivariantPlaylist;
import com.google.android.exoplayer2.testutil.CacheAsserts;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.PlaceholderDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/** Unit tests for {@link HlsDownloader}. */
@RunWith(AndroidJUnit4.class)
public class HlsDownloaderTest {

  private SimpleCache cache;
  private File tempFolder;
  private ProgressListener progressListener;
  private FakeDataSet fakeDataSet;

  @Before
  public void setUp() throws Exception {
    tempFolder =
        Util.createTempDirectory(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    cache =
        new SimpleCache(tempFolder, new NoOpCacheEvictor(), TestUtil.getInMemoryDatabaseProvider());
    progressListener = new ProgressListener();
    fakeDataSet =
        new FakeDataSet()
            .setData(MULTIVARIANT_PLAYLIST_URI, MULTIVARIANT_PLAYLIST_DATA)
            .setData(MEDIA_PLAYLIST_1_URI, MEDIA_PLAYLIST_DATA)
            .setRandomData(MEDIA_PLAYLIST_1_DIR + "fileSequence0.ts", 10)
            .setRandomData(MEDIA_PLAYLIST_1_DIR + "fileSequence1.ts", 11)
            .setRandomData(MEDIA_PLAYLIST_1_DIR + "fileSequence2.ts", 12)
            .setData(MEDIA_PLAYLIST_2_URI, MEDIA_PLAYLIST_DATA)
            .setRandomData(MEDIA_PLAYLIST_2_DIR + "fileSequence0.ts", 13)
            .setRandomData(MEDIA_PLAYLIST_2_DIR + "fileSequence1.ts", 14)
            .setRandomData(MEDIA_PLAYLIST_2_DIR + "fileSequence2.ts", 15);
  }

  @After
  public void tearDown() {
    Util.recursiveDelete(tempFolder);
  }

  @Test
  public void createWithDefaultDownloaderFactory() {
    CacheDataSource.Factory cacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(Mockito.mock(Cache.class))
            .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY);
    DownloaderFactory factory =
        new DefaultDownloaderFactory(cacheDataSourceFactory, /* executor= */ Runnable::run);

    Downloader downloader =
        factory.createDownloader(
            new DownloadRequest.Builder(/* id= */ "id", Uri.parse("https://www.test.com/download"))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setStreamKeys(
                    Collections.singletonList(
                        new StreamKey(/* groupIndex= */ 0, /* streamIndex= */ 0)))
                .build());
    assertThat(downloader).isInstanceOf(HlsDownloader.class);
  }

  @Test
  public void counterMethods() throws Exception {
    HlsDownloader downloader =
        getHlsDownloader(MULTIVARIANT_PLAYLIST_URI, getKeys(MULTIVARIANT_MEDIA_PLAYLIST_1_INDEX));
    downloader.download(progressListener);

    progressListener.assertBytesDownloaded(MEDIA_PLAYLIST_DATA.length + 10 + 11 + 12);
  }

  @Test
  public void downloadRepresentation() throws Exception {
    HlsDownloader downloader =
        getHlsDownloader(MULTIVARIANT_PLAYLIST_URI, getKeys(MULTIVARIANT_MEDIA_PLAYLIST_1_INDEX));
    downloader.download(progressListener);

    assertCachedData(
        cache,
        new CacheAsserts.RequestSet(fakeDataSet)
            .subset(
                MULTIVARIANT_PLAYLIST_URI,
                MEDIA_PLAYLIST_1_URI,
                MEDIA_PLAYLIST_1_DIR + "fileSequence0.ts",
                MEDIA_PLAYLIST_1_DIR + "fileSequence1.ts",
                MEDIA_PLAYLIST_1_DIR + "fileSequence2.ts"));
  }

  @Test
  public void downloadMultipleRepresentations() throws Exception {
    HlsDownloader downloader =
        getHlsDownloader(
            MULTIVARIANT_PLAYLIST_URI,
            getKeys(MULTIVARIANT_MEDIA_PLAYLIST_1_INDEX, MULTIVARIANT_MEDIA_PLAYLIST_2_INDEX));
    downloader.download(progressListener);

    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void downloadAllRepresentations() throws Exception {
    // Add data for the rest of the playlists
    fakeDataSet
        .setData(MEDIA_PLAYLIST_0_URI, MEDIA_PLAYLIST_DATA)
        .setRandomData(MEDIA_PLAYLIST_0_DIR + "fileSequence0.ts", 10)
        .setRandomData(MEDIA_PLAYLIST_0_DIR + "fileSequence1.ts", 11)
        .setRandomData(MEDIA_PLAYLIST_0_DIR + "fileSequence2.ts", 12)
        .setData(MEDIA_PLAYLIST_3_URI, MEDIA_PLAYLIST_DATA)
        .setRandomData(MEDIA_PLAYLIST_3_DIR + "fileSequence0.ts", 13)
        .setRandomData(MEDIA_PLAYLIST_3_DIR + "fileSequence1.ts", 14)
        .setRandomData(MEDIA_PLAYLIST_3_DIR + "fileSequence2.ts", 15);

    HlsDownloader downloader = getHlsDownloader(MULTIVARIANT_PLAYLIST_URI, getKeys());
    downloader.download(progressListener);

    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void remove() throws Exception {
    HlsDownloader downloader =
        getHlsDownloader(
            MULTIVARIANT_PLAYLIST_URI,
            getKeys(MULTIVARIANT_MEDIA_PLAYLIST_1_INDEX, MULTIVARIANT_MEDIA_PLAYLIST_2_INDEX));
    downloader.download(progressListener);
    downloader.remove();

    assertCacheEmpty(cache);
  }

  @Test
  public void downloadMediaPlaylist() throws Exception {
    HlsDownloader downloader = getHlsDownloader(MEDIA_PLAYLIST_1_URI, getKeys());
    downloader.download(progressListener);

    assertCachedData(
        cache,
        new CacheAsserts.RequestSet(fakeDataSet)
            .subset(
                MEDIA_PLAYLIST_1_URI,
                MEDIA_PLAYLIST_1_DIR + "fileSequence0.ts",
                MEDIA_PLAYLIST_1_DIR + "fileSequence1.ts",
                MEDIA_PLAYLIST_1_DIR + "fileSequence2.ts"));
  }

  @Test
  public void downloadEncMediaPlaylist() throws Exception {
    fakeDataSet =
        new FakeDataSet()
            .setData(ENC_MEDIA_PLAYLIST_URI, ENC_MEDIA_PLAYLIST_DATA)
            .setRandomData("enc.key", 8)
            .setRandomData("enc2.key", 9)
            .setRandomData("fileSequence0.ts", 10)
            .setRandomData("fileSequence1.ts", 11)
            .setRandomData("fileSequence2.ts", 12);

    HlsDownloader downloader = getHlsDownloader(ENC_MEDIA_PLAYLIST_URI, getKeys());
    downloader.download(progressListener);
    assertCachedData(cache, fakeDataSet);
  }

  private HlsDownloader getHlsDownloader(String mediaPlaylistUri, List<StreamKey> keys) {
    CacheDataSource.Factory cacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(new FakeDataSource.Factory().setFakeDataSet(fakeDataSet));
    return new HlsDownloader(
        new MediaItem.Builder().setUri(mediaPlaylistUri).setStreamKeys(keys).build(),
        cacheDataSourceFactory);
  }

  private static ArrayList<StreamKey> getKeys(int... variantIndices) {
    ArrayList<StreamKey> streamKeys = new ArrayList<>();
    for (int variantIndex : variantIndices) {
      streamKeys.add(new StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, variantIndex));
    }
    return streamKeys;
  }

  private static final class ProgressListener implements Downloader.ProgressListener {

    private long bytesDownloaded;

    @Override
    public void onProgress(long contentLength, long bytesDownloaded, float percentDownloaded) {
      this.bytesDownloaded = bytesDownloaded;
    }

    public void assertBytesDownloaded(long bytesDownloaded) {
      assertThat(this.bytesDownloaded).isEqualTo(bytesDownloaded);
    }
  }
}
