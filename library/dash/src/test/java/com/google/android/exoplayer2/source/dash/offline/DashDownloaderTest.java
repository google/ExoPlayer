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

import static com.google.android.exoplayer2.source.dash.offline.DashDownloadTestData.TEST_MPD;
import static com.google.android.exoplayer2.source.dash.offline.DashDownloadTestData.TEST_MPD_NO_INDEX;
import static com.google.android.exoplayer2.source.dash.offline.DashDownloadTestData.TEST_MPD_URI;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCacheEmpty;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCachedData;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadException;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.Downloader;
import com.google.android.exoplayer2.offline.DownloaderFactory;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.testutil.CacheAsserts.RequestSet;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.PlaceholderDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link DashDownloader}. */
@RunWith(AndroidJUnit4.class)
public class DashDownloaderTest {

  private SimpleCache cache;
  private File tempFolder;
  private ProgressListener progressListener;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    tempFolder =
        Util.createTempDirectory(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    cache =
        new SimpleCache(tempFolder, new NoOpCacheEvictor(), TestUtil.getInMemoryDatabaseProvider());
    progressListener = new ProgressListener();
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
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .setStreamKeys(
                    Collections.singletonList(
                        new StreamKey(/* groupIndex= */ 0, /* streamIndex= */ 0)))
                .build());
    assertThat(downloader).isInstanceOf(DashDownloader.class);
  }

  @Test
  public void downloadRepresentation() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6);

    DashDownloader dashDownloader = getDashDownloader(fakeDataSet, new StreamKey(0, 0, 0));
    dashDownloader.download(progressListener);
    assertCachedData(cache, new RequestSet(fakeDataSet).useBoundedDataSpecFor("audio_init_data"));
  }

  @Test
  public void downloadRepresentationInSmallParts() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .newData("audio_segment_1")
            .appendReadData(TestUtil.buildTestData(10))
            .appendReadData(TestUtil.buildTestData(10))
            .appendReadData(TestUtil.buildTestData(10))
            .endData()
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6);

    DashDownloader dashDownloader = getDashDownloader(fakeDataSet, new StreamKey(0, 0, 0));
    dashDownloader.download(progressListener);
    assertCachedData(cache, new RequestSet(fakeDataSet).useBoundedDataSpecFor("audio_init_data"));
  }

  @Test
  public void downloadRepresentations() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6)
            .setRandomData("text_segment_1", 1)
            .setRandomData("text_segment_2", 2)
            .setRandomData("text_segment_3", 3);

    DashDownloader dashDownloader =
        getDashDownloader(fakeDataSet, new StreamKey(0, 0, 0), new StreamKey(0, 1, 0));
    dashDownloader.download(progressListener);
    assertCachedData(cache, new RequestSet(fakeDataSet).useBoundedDataSpecFor("audio_init_data"));
  }

  @Test
  public void downloadAllRepresentations() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6)
            .setRandomData("text_segment_1", 1)
            .setRandomData("text_segment_2", 2)
            .setRandomData("text_segment_3", 3)
            .setRandomData("period_2_segment_1", 1)
            .setRandomData("period_2_segment_2", 2)
            .setRandomData("period_2_segment_3", 3);

    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);
    dashDownloader.download(progressListener);
    assertCachedData(cache, new RequestSet(fakeDataSet).useBoundedDataSpecFor("audio_init_data"));
  }

  @Test
  public void progressiveDownload() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6)
            .setRandomData("text_segment_1", 1)
            .setRandomData("text_segment_2", 2)
            .setRandomData("text_segment_3", 3);
    FakeDataSource fakeDataSource = new FakeDataSource(fakeDataSet);
    FakeDataSource.Factory factory = mock(FakeDataSource.Factory.class);
    when(factory.createDataSource()).thenReturn(fakeDataSource);

    DashDownloader dashDownloader =
        getDashDownloader(factory, new StreamKey(0, 0, 0), new StreamKey(0, 1, 0));
    dashDownloader.download(progressListener);

    DataSpec[] openedDataSpecs = fakeDataSource.getAndClearOpenedDataSpecs();
    assertThat(openedDataSpecs.length).isEqualTo(8);
    assertThat(openedDataSpecs[0].uri).isEqualTo(TEST_MPD_URI);
    assertThat(openedDataSpecs[1].uri.getPath()).isEqualTo("audio_init_data");
    assertThat(openedDataSpecs[2].uri.getPath()).isEqualTo("audio_segment_1");
    assertThat(openedDataSpecs[3].uri.getPath()).isEqualTo("text_segment_1");
    assertThat(openedDataSpecs[4].uri.getPath()).isEqualTo("audio_segment_2");
    assertThat(openedDataSpecs[5].uri.getPath()).isEqualTo("text_segment_2");
    assertThat(openedDataSpecs[6].uri.getPath()).isEqualTo("audio_segment_3");
    assertThat(openedDataSpecs[7].uri.getPath()).isEqualTo("text_segment_3");
  }

  @Test
  public void progressiveDownloadSeparatePeriods() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6)
            .setRandomData("period_2_segment_1", 1)
            .setRandomData("period_2_segment_2", 2)
            .setRandomData("period_2_segment_3", 3);
    FakeDataSource fakeDataSource = new FakeDataSource(fakeDataSet);
    FakeDataSource.Factory factory = mock(FakeDataSource.Factory.class);
    when(factory.createDataSource()).thenReturn(fakeDataSource);

    DashDownloader dashDownloader =
        getDashDownloader(factory, new StreamKey(0, 0, 0), new StreamKey(1, 0, 0));
    dashDownloader.download(progressListener);

    DataSpec[] openedDataSpecs = fakeDataSource.getAndClearOpenedDataSpecs();
    assertThat(openedDataSpecs.length).isEqualTo(8);
    assertThat(openedDataSpecs[0].uri).isEqualTo(TEST_MPD_URI);
    assertThat(openedDataSpecs[1].uri.getPath()).isEqualTo("audio_init_data");
    assertThat(openedDataSpecs[2].uri.getPath()).isEqualTo("audio_segment_1");
    assertThat(openedDataSpecs[3].uri.getPath()).isEqualTo("audio_segment_2");
    assertThat(openedDataSpecs[4].uri.getPath()).isEqualTo("audio_segment_3");
    assertThat(openedDataSpecs[5].uri.getPath()).isEqualTo("period_2_segment_1");
    assertThat(openedDataSpecs[6].uri.getPath()).isEqualTo("period_2_segment_2");
    assertThat(openedDataSpecs[7].uri.getPath()).isEqualTo("period_2_segment_3");
  }

  @Test
  public void downloadRepresentationFailure() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .newData("audio_segment_2")
            .appendReadData(TestUtil.buildTestData(2))
            .appendReadError(new IOException())
            .appendReadData(TestUtil.buildTestData(3))
            .endData()
            .setRandomData("audio_segment_3", 6);

    DashDownloader dashDownloader = getDashDownloader(fakeDataSet, new StreamKey(0, 0, 0));
    try {
      dashDownloader.download(progressListener);
      fail();
    } catch (IOException e) {
      // Expected.
    }
    dashDownloader.download(progressListener);
    assertCachedData(cache, new RequestSet(fakeDataSet).useBoundedDataSpecFor("audio_init_data"));
  }

  @Test
  public void counters() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .newData("audio_segment_2")
            .appendReadData(TestUtil.buildTestData(2))
            .appendReadError(new IOException())
            .appendReadData(TestUtil.buildTestData(3))
            .endData()
            .setRandomData("audio_segment_3", 6);

    DashDownloader dashDownloader = getDashDownloader(fakeDataSet, new StreamKey(0, 0, 0));

    try {
      dashDownloader.download(progressListener);
      fail();
    } catch (IOException e) {
      // Failure expected after downloading init data, segment 1 and 2 bytes in segment 2.
    }
    progressListener.assertBytesDownloaded(10 + 4 + 2);

    dashDownloader.download(progressListener);
    progressListener.assertBytesDownloaded(10 + 4 + 5 + 6);
  }

  @Test
  public void remove() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6)
            .setRandomData("text_segment_1", 1)
            .setRandomData("text_segment_2", 2)
            .setRandomData("text_segment_3", 3);

    DashDownloader dashDownloader =
        getDashDownloader(fakeDataSet, new StreamKey(0, 0, 0), new StreamKey(0, 1, 0));
    dashDownloader.download(progressListener);
    dashDownloader.remove();
    assertCacheEmpty(cache);
  }

  @Test
  public void representationWithoutIndex() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD_NO_INDEX)
            .setRandomData("test_segment_1", 4);

    DashDownloader dashDownloader = getDashDownloader(fakeDataSet, new StreamKey(0, 0, 0));
    try {
      dashDownloader.download(progressListener);
      fail();
    } catch (DownloadException e) {
      // Expected.
    }
    dashDownloader.remove();
    assertCacheEmpty(cache);
  }

  private DashDownloader getDashDownloader(FakeDataSet fakeDataSet, StreamKey... keys) {
    return getDashDownloader(new FakeDataSource.Factory().setFakeDataSet(fakeDataSet), keys);
  }

  private DashDownloader getDashDownloader(
      FakeDataSource.Factory upstreamDataSourceFactory, StreamKey... keys) {
    CacheDataSource.Factory cacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory);
    return new DashDownloader(
        new MediaItem.Builder().setUri(TEST_MPD_URI).setStreamKeys(keysList(keys)).build(),
        cacheDataSourceFactory);
  }

  private static ArrayList<StreamKey> keysList(StreamKey... keys) {
    ArrayList<StreamKey> keysList = new ArrayList<>();
    Collections.addAll(keysList, keys);
    return keysList;
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
