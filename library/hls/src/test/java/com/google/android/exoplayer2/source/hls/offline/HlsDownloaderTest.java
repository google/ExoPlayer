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
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MASTER_PLAYLIST_DATA;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MASTER_PLAYLIST_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_0_DIR;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_0_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_1_DIR;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_1_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_2_DIR;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_2_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_3_DIR;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_3_URI;
import static com.google.android.exoplayer2.source.hls.offline.HlsDownloadTestData.MEDIA_PLAYLIST_DATA;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCacheEmpty;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCachedData;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource.Factory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link HlsDownloader}. */
@RunWith(RobolectricTestRunner.class)
public class HlsDownloaderTest {

  private SimpleCache cache;
  private File tempFolder;
  private FakeDataSet fakeDataSet;
  private HlsDownloader hlsDownloader;

  @Before
  public void setUp() throws Exception {
    tempFolder = Util.createTempDirectory(RuntimeEnvironment.application, "ExoPlayerTest");
    cache = new SimpleCache(tempFolder, new NoOpCacheEvictor());

    fakeDataSet =
        new FakeDataSet()
            .setData(MASTER_PLAYLIST_URI, MASTER_PLAYLIST_DATA)
            .setData(MEDIA_PLAYLIST_1_URI, MEDIA_PLAYLIST_DATA)
            .setRandomData(MEDIA_PLAYLIST_1_DIR + "fileSequence0.ts", 10)
            .setRandomData(MEDIA_PLAYLIST_1_DIR + "fileSequence1.ts", 11)
            .setRandomData(MEDIA_PLAYLIST_1_DIR + "fileSequence2.ts", 12)
            .setData(MEDIA_PLAYLIST_2_URI, MEDIA_PLAYLIST_DATA)
            .setRandomData(MEDIA_PLAYLIST_2_DIR + "fileSequence0.ts", 13)
            .setRandomData(MEDIA_PLAYLIST_2_DIR + "fileSequence1.ts", 14)
            .setRandomData(MEDIA_PLAYLIST_2_DIR + "fileSequence2.ts", 15);
    hlsDownloader = getHlsDownloader(MASTER_PLAYLIST_URI);
  }

  @After
  public void tearDown() throws Exception {
    Util.recursiveDelete(tempFolder);
  }

  @Test
  public void testDownloadManifest() throws Exception {
    HlsMasterPlaylist manifest = hlsDownloader.getManifest();

    assertThat(manifest).isNotNull();
    assertCachedData(cache, fakeDataSet, MASTER_PLAYLIST_URI);
  }

  @Test
  public void testSelectRepresentationsClearsPreviousSelection() throws Exception {
    hlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_1_URI});
    hlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_2_URI});
    hlsDownloader.download(null);

    assertCachedData(
        cache,
        fakeDataSet,
        MASTER_PLAYLIST_URI,
        MEDIA_PLAYLIST_2_URI,
        MEDIA_PLAYLIST_2_DIR + "fileSequence0.ts",
        MEDIA_PLAYLIST_2_DIR + "fileSequence1.ts",
        MEDIA_PLAYLIST_2_DIR + "fileSequence2.ts");
  }

  @Test
  public void testCounterMethods() throws Exception {
    hlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_1_URI});
    hlsDownloader.download(null);

    assertThat(hlsDownloader.getTotalSegments()).isEqualTo(4);
    assertThat(hlsDownloader.getDownloadedSegments()).isEqualTo(4);
    assertThat(hlsDownloader.getDownloadedBytes())
        .isEqualTo(MEDIA_PLAYLIST_DATA.length + 10 + 11 + 12);
  }

  @Test
  public void testInitStatus() throws Exception {
    hlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_1_URI});
    hlsDownloader.download(null);

    HlsDownloader newHlsDownloader = getHlsDownloader(MASTER_PLAYLIST_URI);
    newHlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_1_URI});
    newHlsDownloader.init();

    assertThat(newHlsDownloader.getTotalSegments()).isEqualTo(4);
    assertThat(newHlsDownloader.getDownloadedSegments()).isEqualTo(4);
    assertThat(newHlsDownloader.getDownloadedBytes())
        .isEqualTo(MEDIA_PLAYLIST_DATA.length + 10 + 11 + 12);
  }

  @Test
  public void testDownloadRepresentation() throws Exception {
    hlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_1_URI});
    hlsDownloader.download(null);

    assertCachedData(
        cache,
        fakeDataSet,
        MASTER_PLAYLIST_URI,
        MEDIA_PLAYLIST_1_URI,
        MEDIA_PLAYLIST_1_DIR + "fileSequence0.ts",
        MEDIA_PLAYLIST_1_DIR + "fileSequence1.ts",
        MEDIA_PLAYLIST_1_DIR + "fileSequence2.ts");
  }

  @Test
  public void testDownloadMultipleRepresentations() throws Exception {
    hlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_1_URI, MEDIA_PLAYLIST_2_URI});
    hlsDownloader.download(null);

    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testDownloadAllRepresentations() throws Exception {
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
    hlsDownloader = getHlsDownloader(MASTER_PLAYLIST_URI);

    // hlsDownloader.selectRepresentations() isn't called
    hlsDownloader.download(null);
    assertCachedData(cache, fakeDataSet);
    hlsDownloader.remove();

    // select something random
    hlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_1_URI});
    // clear selection
    hlsDownloader.selectRepresentations(null);
    hlsDownloader.download(null);
    assertCachedData(cache, fakeDataSet);
    hlsDownloader.remove();

    hlsDownloader.selectRepresentations(new String[0]);
    hlsDownloader.download(null);
    assertCachedData(cache, fakeDataSet);
    hlsDownloader.remove();
  }

  @Test
  public void testRemoveAll() throws Exception {
    hlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_1_URI, MEDIA_PLAYLIST_2_URI});
    hlsDownloader.download(null);
    hlsDownloader.remove();

    assertCacheEmpty(cache);
  }

  @Test
  public void testDownloadMediaPlaylist() throws Exception {
    hlsDownloader = getHlsDownloader(MEDIA_PLAYLIST_1_URI);
    hlsDownloader.selectRepresentations(new String[] {MEDIA_PLAYLIST_1_URI});
    hlsDownloader.download(null);

    assertCachedData(
        cache,
        fakeDataSet,
        MEDIA_PLAYLIST_1_URI,
        MEDIA_PLAYLIST_1_DIR + "fileSequence0.ts",
        MEDIA_PLAYLIST_1_DIR + "fileSequence1.ts",
        MEDIA_PLAYLIST_1_DIR + "fileSequence2.ts");
  }

  @Test
  public void testDownloadEncMediaPlaylist() throws Exception {
    fakeDataSet =
        new FakeDataSet()
            .setData(ENC_MEDIA_PLAYLIST_URI, ENC_MEDIA_PLAYLIST_DATA)
            .setRandomData("enc.key", 8)
            .setRandomData("enc2.key", 9)
            .setRandomData("fileSequence0.ts", 10)
            .setRandomData("fileSequence1.ts", 11)
            .setRandomData("fileSequence2.ts", 12);
    hlsDownloader = getHlsDownloader(ENC_MEDIA_PLAYLIST_URI);
    hlsDownloader.selectRepresentations(new String[] {ENC_MEDIA_PLAYLIST_URI});
    hlsDownloader.download(null);

    assertCachedData(cache, fakeDataSet);
  }

  private HlsDownloader getHlsDownloader(String mediaPlaylistUri) {
    Factory factory = new Factory(null).setFakeDataSet(fakeDataSet);
    return new HlsDownloader(
        Uri.parse(mediaPlaylistUri), new DownloaderConstructorHelper(cache, factory));
  }
}
