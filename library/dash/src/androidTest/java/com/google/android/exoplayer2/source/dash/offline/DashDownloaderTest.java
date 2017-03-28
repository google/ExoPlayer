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
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.RepresentationKey;
import com.google.android.exoplayer2.source.dash.offline.DashDownloader.ProgressListener;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.FakeDataSource.FakeData;
import com.google.android.exoplayer2.testutil.FakeDataSource.FakeDataSet;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.ClosedSource;
import com.google.android.exoplayer2.util.Util;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Unit tests for {@link DashDownloader}.
 */
@ClosedSource(reason = "Not ready yet")
public class DashDownloaderTest extends InstrumentationTestCase {

  private static final byte[] TEST_MPD =
      ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
          + "    type=\"dynamic\">\n"
          + "    <Period start=\"PT6462826.784S\" >\n"
          + "        <SegmentList\n"
          + "            presentationTimeOffset=\"34740095\"\n"
          + "            timescale=\"1000\" >\n"
          + "            <SegmentTimeline>\n"
          + "                <S d=\"4804\" />\n"
          + "                <S d=\"5338\" />\n"
          + "                <S d=\"4938\" />\n"
          + "            </SegmentTimeline>\n"
          + "        </SegmentList>\n"
          + "        <AdaptationSet>\n"
          + "            <Representation>\n"
          + "                <SegmentList>\n"
          // Bounded range data
          + "                    <Initialization range=\"0-9\" sourceURL=\"audio_init_data\" />\n"
          // Unbounded range data
          + "                    <SegmentURL media=\"audio_segment_1\" />\n"
          + "                    <SegmentURL media=\"audio_segment_2\" />\n"
          + "                    <SegmentURL media=\"audio_segment_3\" />\n"
          + "                </SegmentList>\n"
          + "            </Representation>\n"
          + "        </AdaptationSet>\n"
          + "        <AdaptationSet>\n"
          + "            <Representation>\n"
          + "                <SegmentList>\n"
          + "                    <SegmentURL media=\"text_segment_1\" />\n"
          + "                    <SegmentURL media=\"text_segment_2\" />\n"
          + "                    <SegmentURL media=\"text_segment_3\" />\n"
          + "                </SegmentList>\n"
          + "            </Representation>\n"
          + "        </AdaptationSet>\n"
          + "    </Period>\n"
          + "</MPD>").getBytes();

  private static final byte[] TEST_MPD_NO_INDEX =
      ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" type=\"dynamic\">\n"
          + "    <Period start=\"PT6462826.784S\" >\n"
          + "        <AdaptationSet>\n"
          + "            <Representation>\n"
          + "                <SegmentBase indexRange='0-10'/>\n"
          + "            </Representation>\n"
          + "        </AdaptationSet>\n"
          + "    </Period>\n"
          + "</MPD>").getBytes();

  private File tempFolder;
  private SimpleCache cache;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    tempFolder = Util.createTempDirectory(getInstrumentation().getContext(), "ExoPlayerTest");
    cache = new SimpleCache(tempFolder, new NoOpCacheEvictor());
  }

  @Override
  public void tearDown() throws Exception {
    Util.recursiveDelete(tempFolder);
  }

  public void testDownloadManifest() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD);
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));

    DashManifest manifest = dashDownloader.downloadManifest();

    assertNotNull(manifest);
    assertCachedData(fakeDataSet);
  }

  public void testDownloadManifestFailure() throws Exception {
    byte[] testMpdFirstPart = Arrays.copyOf(TEST_MPD, 10);
    byte[] testMpdSecondPart = Arrays.copyOfRange(TEST_MPD, 10, TEST_MPD.length);
    FakeDataSet fakeDataSet = new FakeDataSet()
        .newData("test.mpd")
        .appendReadData(testMpdFirstPart)
        .appendReadError(new IOException())
        .appendReadData(testMpdSecondPart)
        .endData();
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));

    // downloadManifest fails on the first try
    try {
      dashDownloader.downloadManifest();
      fail();
    } catch (IOException e) {
      // ignore
    }
    assertCachedData("test.mpd", testMpdFirstPart);

    // on the second try it downloads the rest of the data
    DashManifest manifest = dashDownloader.downloadManifest();

    assertNotNull(manifest);
    assertCachedData(fakeDataSet);
  }

  public void testDownloadRepresentation() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD)
        .setData("audio_init_data", TestUtil.buildTestData(10))
        .setData("audio_segment_1", TestUtil.buildTestData(4))
        .setData("audio_segment_2", TestUtil.buildTestData(5))
        .setData("audio_segment_3", TestUtil.buildTestData(6));
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));
    dashDownloader.downloadManifest();

    dashDownloader.selectRepresentations(new RepresentationKey(0, 0, 0));
    dashDownloader.downloadRepresentations(null);

    assertCachedData(fakeDataSet);
  }

  public void testDownloadRepresentationInSmallParts() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD)
        .setData("audio_init_data", TestUtil.buildTestData(10))
        .newData("audio_segment_1")
        .appendReadData(TestUtil.buildTestData(10))
        .appendReadData(TestUtil.buildTestData(10))
        .appendReadData(TestUtil.buildTestData(10))
        .endData()
        .setData("audio_segment_2", TestUtil.buildTestData(5))
        .setData("audio_segment_3", TestUtil.buildTestData(6));
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));
    dashDownloader.downloadManifest();

    dashDownloader.selectRepresentations(new RepresentationKey(0, 0, 0));
    dashDownloader.downloadRepresentations(null);

    assertCachedData(fakeDataSet);
  }

  public void testDownloadRepresentations() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD)
        .setData("audio_init_data", TestUtil.buildTestData(10))
        .setData("audio_segment_1", TestUtil.buildTestData(4))
        .setData("audio_segment_2", TestUtil.buildTestData(5))
        .setData("audio_segment_3", TestUtil.buildTestData(6))
        .setData("text_segment_1", TestUtil.buildTestData(1))
        .setData("text_segment_2", TestUtil.buildTestData(2))
        .setData("text_segment_3", TestUtil.buildTestData(3));
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));
    dashDownloader.downloadManifest();

    dashDownloader.selectRepresentations(
        new RepresentationKey(0, 0, 0),
        new RepresentationKey(0, 1, 0));
    dashDownloader.downloadRepresentations(null);

    assertCachedData(fakeDataSet);
  }

  public void testDownloadRepresentationFailure() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD)
        .setData("audio_init_data", TestUtil.buildTestData(10))
        .setData("audio_segment_1", TestUtil.buildTestData(4))
        .newData("audio_segment_2")
        .appendReadData(TestUtil.buildTestData(2))
        .appendReadError(new IOException())
        .appendReadData(TestUtil.buildTestData(3))
        .endData()
        .setData("audio_segment_3", TestUtil.buildTestData(6));
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));
    dashDownloader.downloadManifest();

    dashDownloader.selectRepresentations(new RepresentationKey(0, 0, 0));
    // downloadRepresentations fails on the first try
    try {
      dashDownloader.downloadRepresentations(null);
      fail();
    } catch (IOException e) {
      // ignore
    }
    dashDownloader.downloadRepresentations(null);

    assertCachedData(fakeDataSet);
  }

  public void testCounters() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD)
        .setData("audio_init_data", TestUtil.buildTestData(10))
        .setData("audio_segment_1", TestUtil.buildTestData(4))
        .newData("audio_segment_2")
        .appendReadData(TestUtil.buildTestData(2))
        .appendReadError(new IOException())
        .appendReadData(TestUtil.buildTestData(3))
        .endData()
        .setData("audio_segment_3", TestUtil.buildTestData(6));
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));
    dashDownloader.downloadManifest();

    assertCounters(dashDownloader, C.LENGTH_UNSET, C.LENGTH_UNSET, C.LENGTH_UNSET);

    dashDownloader.selectRepresentations(new RepresentationKey(0, 0, 0));
    dashDownloader.initStatus();
    assertCounters(dashDownloader, 3, 0, 0);

    // downloadRepresentations fails after downloading init data, segment 1 and 2 bytes in segment 2
    try {
      dashDownloader.downloadRepresentations(null);
      fail();
    } catch (IOException e) {
      // ignore
    }
    dashDownloader.initStatus();
    assertCounters(dashDownloader, 3, 1, 10 + 4 + 2);

    dashDownloader.downloadRepresentations(null);

    assertCounters(dashDownloader, 3, 3, 10 + 4 + 5 + 6);
  }

  public void testListener() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD)
        .setData("audio_init_data", TestUtil.buildTestData(10))
        .setData("audio_segment_1", TestUtil.buildTestData(4))
        .setData("audio_segment_2", TestUtil.buildTestData(5))
        .setData("audio_segment_3", TestUtil.buildTestData(6));
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));
    dashDownloader.downloadManifest();

    dashDownloader.selectRepresentations(new RepresentationKey(0, 0, 0));
    dashDownloader.downloadRepresentations(new ProgressListener() {
      private int counter = 0;
      @Override
      public void onDownloadProgress(DashDownloader dashDownloader, int totalSegments,
          int downloadedSegments,
          long downloadedBytes) {
        switch (counter++) {
          case 0:
            assertTrue(totalSegments == 3 && downloadedSegments == 0 && downloadedBytes == 10);
            break;
          case 1:
            assertTrue(totalSegments == 3 && downloadedSegments == 1 && downloadedBytes == 14);
            break;
          case 2:
            assertTrue(totalSegments == 3 && downloadedSegments == 2 && downloadedBytes == 19);
            break;
          case 3:
            assertTrue(totalSegments == 3 && downloadedSegments == 3 && downloadedBytes == 25);
            break;
          default:
            fail();
        }
      }
    });
  }

  public void testRemoveAll() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD)
        .setData("audio_init_data", TestUtil.buildTestData(10))
        .setData("audio_segment_1", TestUtil.buildTestData(4))
        .setData("audio_segment_2", TestUtil.buildTestData(5))
        .setData("audio_segment_3", TestUtil.buildTestData(6))
        .setData("text_segment_1", TestUtil.buildTestData(1))
        .setData("text_segment_2", TestUtil.buildTestData(2))
        .setData("text_segment_3", TestUtil.buildTestData(3));
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));
    dashDownloader.downloadManifest();
    dashDownloader.selectRepresentations(
        new RepresentationKey(0, 0, 0),
        new RepresentationKey(0, 1, 0));
    dashDownloader.downloadRepresentations(null);

    dashDownloader.removeAll();

    assertEquals(0, cache.getCacheSpace());
  }

  public void testRemoveRepresentations() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD)
        .setData("audio_init_data", TestUtil.buildTestData(10))
        .setData("audio_segment_1", TestUtil.buildTestData(4))
        .setData("audio_segment_2", TestUtil.buildTestData(5))
        .setData("audio_segment_3", TestUtil.buildTestData(6))
        .setData("text_segment_1", TestUtil.buildTestData(1))
        .setData("text_segment_2", TestUtil.buildTestData(2))
        .setData("text_segment_3", TestUtil.buildTestData(3));
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));
    dashDownloader.downloadManifest();
    dashDownloader.selectRepresentations(
        new RepresentationKey(0, 0, 0),
        new RepresentationKey(0, 1, 0));
    dashDownloader.downloadRepresentations(null);

    dashDownloader.removeRepresentations();

    assertEquals(TEST_MPD.length, cache.getCacheSpace());
    assertCachedData("test.mpd", TEST_MPD);
  }

  public void testMpdNoIndex() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet()
        .setData("test.mpd", TEST_MPD_NO_INDEX)
        .setData("test_segment_1", TestUtil.buildTestData(4));
    DashDownloader dashDownloader =
        new DashDownloader("test.mpd", cache, new FakeDataSource(fakeDataSet));
    dashDownloader.downloadManifest();

    dashDownloader.selectRepresentations(new RepresentationKey(0, 0, 0));
    dashDownloader.initStatus();
    try {
      dashDownloader.downloadRepresentations(null);
      fail();
    } catch (DashDownloaderException e) {
      // expected interrupt.
    }
    dashDownloader.removeAll();

    assertEquals(0, cache.getCacheSpace());
  }

  private void assertCachedData(FakeDataSet fakeDataSet) throws IOException {
    int totalLength = 0;
    for (FakeData fakeData : fakeDataSet.getAllData()) {
      byte[] data = fakeData.getData();
      assertCachedData(fakeData.uri, data);
      totalLength += data.length;
    }
    assertEquals(totalLength, cache.getCacheSpace());
  }

  private void assertCachedData(String uriString, byte[] expected) throws IOException {
    CacheDataSource dataSource = new CacheDataSource(cache, DummyDataSource.INSTANCE, 0);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    DataSourceInputStream inputStream = new DataSourceInputStream(dataSource,
        new DataSpec(Uri.parse(uriString), DataSpec.FLAG_ALLOW_CACHING_UNKNOWN_LENGTH));
    try {
      inputStream.open();
      byte[] buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }
    } catch (IOException e) {
      // Ignore
    } finally {
      inputStream.close();
    }
    MoreAsserts.assertEquals(expected, outputStream.toByteArray());
  }

  private static void assertCounters(DashDownloader dashDownloader, int totalSegments,
      int downloadedSegments, int downloadedBytes) {
    assertEquals(totalSegments, dashDownloader.getTotalSegments());
    assertEquals(downloadedSegments, dashDownloader.getDownloadedSegments());
    assertEquals(downloadedBytes, dashDownloader.getDownloadedBytes());
  }

}
