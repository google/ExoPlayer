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
package com.google.android.exoplayer2.upstream.cache;

import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCachedData;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FailOnCloseDataSink;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link CacheWriter}. */
@RunWith(AndroidJUnit4.class)
public final class CacheWriterTest {

  private File tempFolder;
  private SimpleCache cache;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    tempFolder =
        Util.createTempDirectory(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    cache =
        new SimpleCache(tempFolder, new NoOpCacheEvictor(), TestUtil.getInMemoryDatabaseProvider());
  }

  @After
  public void tearDown() {
    Util.recursiveDelete(tempFolder);
  }

  @Test
  public void cache() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    CachingCounters counters = new CachingCounters();

    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            new DataSpec(Uri.parse("test_data")),
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(0, 100, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void cacheSetOffsetAndLength() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    Uri testUri = Uri.parse("test_data");
    DataSpec dataSpec = new DataSpec(testUri, /* position= */ 10, /* length= */ 20);
    CachingCounters counters = new CachingCounters();

    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            dataSpec,
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(0, 20, 20);
    counters.reset();

    cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            new DataSpec(testUri),
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(20, 80, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void cacheUnknownLength() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().newData("test_data")
        .setSimulateUnknownLength(true)
        .appendReadData(TestUtil.buildTestData(100)).endData();
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    DataSpec dataSpec = new DataSpec(Uri.parse("test_data"));
    CachingCounters counters = new CachingCounters();

    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            dataSpec,
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(0, 100, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void cacheUnknownLengthPartialCaching() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().newData("test_data")
        .setSimulateUnknownLength(true)
        .appendReadData(TestUtil.buildTestData(100)).endData();
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    Uri testUri = Uri.parse("test_data");
    DataSpec dataSpec = new DataSpec(testUri, /* position= */ 10, /* length= */ 20);
    CachingCounters counters = new CachingCounters();

    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            dataSpec,
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(0, 20, 20);
    counters.reset();

    cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            new DataSpec(testUri),
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(20, 80, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void cacheLengthExceedsActualDataLength() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    Uri testUri = Uri.parse("test_data");
    DataSpec dataSpec = new DataSpec(testUri, /* position= */ 0, /* length= */ 1000);
    CachingCounters counters = new CachingCounters();

    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            dataSpec,
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(0, 100, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void cache_afterFailureOnClose_succeeds() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource upstreamDataSource = new FakeDataSource(fakeDataSet);

    AtomicBoolean failOnClose = new AtomicBoolean(/* initialValue= */ true);
    FailOnCloseDataSink dataSink = new FailOnCloseDataSink(cache, failOnClose);

    CacheDataSource cacheDataSource =
        new CacheDataSource(
            cache,
            upstreamDataSource,
            new FileDataSource(),
            dataSink,
            /* flags= */ 0,
            /* eventListener= */ null);

    CachingCounters counters = new CachingCounters();

    CacheWriter cacheWriter =
        new CacheWriter(
            cacheDataSource,
            new DataSpec(Uri.parse("test_data")),
            /* temporaryBuffer= */ null,
            counters);

    // DataSink.close failing must cause the operation to fail rather than succeed.
    assertThrows(IOException.class, cacheWriter::cache);
    // Since all of the bytes were read through the DataSource chain successfully before the sink
    // was closed, the progress listener will have seen all of the bytes being cached, even though
    // this may not really be the case.
    counters.assertValues(
        /* bytesAlreadyCached= */ 0, /* bytesNewlyCached= */ 100, /* contentLength= */ 100);

    failOnClose.set(false);

    // The bytes will be downloaded again, but cached successfully this time.
    cacheWriter.cache();
    counters.assertValues(
        /* bytesAlreadyCached= */ 0, /* bytesNewlyCached= */ 100, /* contentLength= */ 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void cachePolling() throws Exception {
    final CachingCounters counters = new CachingCounters();
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .newData("test_data")
            .appendReadData(TestUtil.buildTestData(100))
            .appendReadAction(() -> counters.assertValues(0, 100, 300))
            .appendReadData(TestUtil.buildTestData(100))
            .appendReadAction(() -> counters.assertValues(0, 200, 300))
            .appendReadData(TestUtil.buildTestData(100))
            .endData();
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            new DataSpec(Uri.parse("test_data")),
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(0, 300, 300);
    assertCachedData(cache, fakeDataSet);
  }

  private static final class CachingCounters implements CacheWriter.ProgressListener {

    private long contentLength = C.LENGTH_UNSET;
    private long bytesAlreadyCached;
    private long bytesNewlyCached;
    private boolean seenFirstProgressUpdate;

    @Override
    public void onProgress(long contentLength, long bytesCached, long newBytesCached) {
      this.contentLength = contentLength;
      if (!seenFirstProgressUpdate) {
        bytesAlreadyCached = bytesCached;
        seenFirstProgressUpdate = true;
      }
      bytesNewlyCached = bytesCached - bytesAlreadyCached;
    }

    public void assertValues(int bytesAlreadyCached, int bytesNewlyCached, int contentLength) {
      assertThat(this.bytesAlreadyCached).isEqualTo(bytesAlreadyCached);
      assertThat(this.bytesNewlyCached).isEqualTo(bytesNewlyCached);
      assertThat(this.contentLength).isEqualTo(contentLength);
    }

    public void reset() {
      contentLength = C.LENGTH_UNSET;
      bytesAlreadyCached = 0;
      bytesNewlyCached = 0;
      seenFirstProgressUpdate = false;
    }
  }
}
