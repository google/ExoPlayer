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
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertDataCached;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloadException;
import com.google.android.exoplayer2.offline.Downloader.ProgressListener;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.RepresentationKey;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.FakeDataSource.Factory;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link DashDownloader}. */
@RunWith(RobolectricTestRunner.class)
public class DashDownloaderTest {

  private SimpleCache cache;
  private File tempFolder;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    tempFolder = Util.createTempDirectory(RuntimeEnvironment.application, "ExoPlayerTest");
    cache = new SimpleCache(tempFolder, new NoOpCacheEvictor());
  }

  @After
  public void tearDown() throws Exception {
    Util.recursiveDelete(tempFolder);
  }

  @Test
  public void testGetManifest() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setData(TEST_MPD_URI, TEST_MPD);
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    DashManifest manifest = dashDownloader.getManifest();

    assertThat(manifest).isNotNull();
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testDownloadManifestFailure() throws Exception {
    byte[] testMpdFirstPart = Arrays.copyOf(TEST_MPD, 10);
    byte[] testMpdSecondPart = Arrays.copyOfRange(TEST_MPD, 10, TEST_MPD.length);
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .newData(TEST_MPD_URI)
            .appendReadData(testMpdFirstPart)
            .appendReadError(new IOException())
            .appendReadData(testMpdSecondPart)
            .endData();
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    // fails on the first try
    try {
      dashDownloader.getManifest();
      fail();
    } catch (IOException e) {
      // ignore
    }
    DataSpec dataSpec = new DataSpec(TEST_MPD_URI, 0, testMpdFirstPart.length, null);
    assertDataCached(cache, dataSpec, testMpdFirstPart);

    // on the second try it downloads the rest of the data
    DashManifest manifest = dashDownloader.getManifest();

    assertThat(manifest).isNotNull();
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testDownloadRepresentation() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6);
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
    dashDownloader.download(null);

    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testDownloadRepresentationInSmallParts() throws Exception {
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
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
    dashDownloader.download(null);

    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testDownloadRepresentations() throws Exception {
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
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    dashDownloader.selectRepresentations(
        new RepresentationKey[] {new RepresentationKey(0, 0, 0), new RepresentationKey(0, 1, 0)});
    dashDownloader.download(null);

    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testDownloadAllRepresentations() throws Exception {
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

    // dashDownloader.selectRepresentations() isn't called
    dashDownloader.download(null);
    assertCachedData(cache, fakeDataSet);
    dashDownloader.remove();

    // select something random
    dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
    // clear selection
    dashDownloader.selectRepresentations(null);
    dashDownloader.download(null);
    assertCachedData(cache, fakeDataSet);
    dashDownloader.remove();

    dashDownloader.selectRepresentations(new RepresentationKey[0]);
    dashDownloader.download(null);
    assertCachedData(cache, fakeDataSet);
    dashDownloader.remove();
  }

  @Test
  public void testProgressiveDownload() throws Exception {
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
    Factory factory = mock(Factory.class);
    when(factory.createDataSource()).thenReturn(fakeDataSource);
    DashDownloader dashDownloader =
        new DashDownloader(TEST_MPD_URI, new DownloaderConstructorHelper(cache, factory));

    dashDownloader.selectRepresentations(
        new RepresentationKey[] {new RepresentationKey(0, 0, 0), new RepresentationKey(0, 1, 0)});
    dashDownloader.download(null);

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
  public void testProgressiveDownloadSeparatePeriods() throws Exception {
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
    Factory factory = mock(Factory.class);
    when(factory.createDataSource()).thenReturn(fakeDataSource);
    DashDownloader dashDownloader =
        new DashDownloader(TEST_MPD_URI, new DownloaderConstructorHelper(cache, factory));

    dashDownloader.selectRepresentations(
        new RepresentationKey[] {new RepresentationKey(0, 0, 0), new RepresentationKey(1, 0, 0)});
    dashDownloader.download(null);

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
  public void testDownloadRepresentationFailure() throws Exception {
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
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
    // downloadRepresentations fails on the first try
    try {
      dashDownloader.download(null);
      fail();
    } catch (IOException e) {
      // ignore
    }
    dashDownloader.download(null);

    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testCounters() throws Exception {
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
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    assertCounters(dashDownloader, C.LENGTH_UNSET, C.LENGTH_UNSET, C.LENGTH_UNSET);

    dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
    dashDownloader.init();
    assertCounters(dashDownloader, C.LENGTH_UNSET, C.LENGTH_UNSET, C.LENGTH_UNSET);

    // downloadRepresentations fails after downloading init data, segment 1 and 2 bytes in segment 2
    try {
      dashDownloader.download(null);
      fail();
    } catch (IOException e) {
      // ignore
    }
    dashDownloader.init();
    assertCounters(dashDownloader, 4, 2, 10 + 4 + 2);

    dashDownloader.download(null);

    assertCounters(dashDownloader, 4, 4, 10 + 4 + 5 + 6);
  }

  @Test
  public void testListener() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6);
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
    ProgressListener mockListener = Mockito.mock(ProgressListener.class);
    dashDownloader.download(mockListener);
    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder.verify(mockListener).onDownloadProgress(dashDownloader, 0.0f, 0);
    inOrder.verify(mockListener).onDownloadProgress(dashDownloader, 25.0f, 10);
    inOrder.verify(mockListener).onDownloadProgress(dashDownloader, 50.0f, 14);
    inOrder.verify(mockListener).onDownloadProgress(dashDownloader, 75.0f, 19);
    inOrder.verify(mockListener).onDownloadProgress(dashDownloader, 100.0f, 25);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testRemoveAll() throws Exception {
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
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);
    dashDownloader.selectRepresentations(
        new RepresentationKey[] {new RepresentationKey(0, 0, 0), new RepresentationKey(0, 1, 0)});
    dashDownloader.download(null);

    dashDownloader.remove();

    assertCacheEmpty(cache);
  }

  @Test
  public void testRepresentationWithoutIndex() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD_NO_INDEX)
            .setRandomData("test_segment_1", 4);
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
    dashDownloader.init();
    try {
      dashDownloader.download(null);
      fail();
    } catch (DownloadException e) {
      // expected exception.
    }
    dashDownloader.remove();

    assertCacheEmpty(cache);
  }

  @Test
  public void testSelectRepresentationsClearsPreviousSelection() throws Exception {
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6);
    DashDownloader dashDownloader = getDashDownloader(fakeDataSet);

    dashDownloader.selectRepresentations(
        new RepresentationKey[] {new RepresentationKey(0, 0, 0), new RepresentationKey(0, 1, 0)});
    dashDownloader.selectRepresentations(new RepresentationKey[] {new RepresentationKey(0, 0, 0)});
    dashDownloader.download(null);

    assertCachedData(cache, fakeDataSet);
  }

  private DashDownloader getDashDownloader(FakeDataSet fakeDataSet) {
    Factory factory = new Factory(null).setFakeDataSet(fakeDataSet);
    return new DashDownloader(TEST_MPD_URI, new DownloaderConstructorHelper(cache, factory));
  }

  private static void assertCounters(
      DashDownloader dashDownloader,
      int totalSegments,
      int downloadedSegments,
      int downloadedBytes) {
    assertThat(dashDownloader.getTotalSegments()).isEqualTo(totalSegments);
    assertThat(dashDownloader.getDownloadedSegments()).isEqualTo(downloadedSegments);
    assertThat(dashDownloader.getDownloadedBytes()).isEqualTo(downloadedBytes);
  }
}
