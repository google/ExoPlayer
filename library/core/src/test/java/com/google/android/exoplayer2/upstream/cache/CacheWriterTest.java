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
import static java.lang.Math.min;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link CacheWriter}. */
@RunWith(AndroidJUnit4.class)
public final class CacheWriterTest {

  /**
   * Abstract fake Cache implementation used by the test. This class must be public so Mockito can
   * create a proxy for it.
   */
  public abstract static class AbstractFakeCache implements Cache {

    // This array is set to alternating length of cached and not cached regions in tests:
    // spansAndGaps = {<length of 1st cached region>, <length of 1st not cached region>,
    //    <length of 2nd cached region>, <length of 2nd not cached region>, ... }
    // Ideally it should end with a cached region but it shouldn't matter for any code.
    private int[] spansAndGaps;
    private long contentLength;

    private void init() {
      spansAndGaps = new int[] {};
      contentLength = C.LENGTH_UNSET;
    }

    @Override
    public long getCachedLength(String key, long position, long length) {
      if (length == C.LENGTH_UNSET) {
        length = Long.MAX_VALUE;
      }
      for (int i = 0; i < spansAndGaps.length; i++) {
        int spanOrGap = spansAndGaps[i];
        if (position < spanOrGap) {
          long left = min(spanOrGap - position, length);
          return (i & 1) == 1 ? -left : left;
        }
        position -= spanOrGap;
      }
      return -length;
    }

    @Override
    public ContentMetadata getContentMetadata(String key) {
      DefaultContentMetadata metadata = new DefaultContentMetadata();
      ContentMetadataMutations mutations = new ContentMetadataMutations();
      ContentMetadataMutations.setContentLength(mutations, contentLength);
      return metadata.copyWithMutationsApplied(mutations);
    }
  }

  @Mock(answer = Answers.CALLS_REAL_METHODS) private AbstractFakeCache mockCache;
  private File tempFolder;
  private SimpleCache cache;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mockCache.init();
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
            /* allowShortContent= */ false,
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
            /* allowShortContent= */ false,
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(0, 20, 20);
    counters.reset();

    cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            new DataSpec(testUri),
            /* allowShortContent= */ false,
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
            /* allowShortContent= */ false,
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
            /* allowShortContent= */ false,
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(0, 20, 20);
    counters.reset();

    cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, dataSource),
            new DataSpec(testUri),
            /* allowShortContent= */ false,
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
            /* allowShortContent= */ true,
            /* temporaryBuffer= */ null,
            counters);
    cacheWriter.cache();

    counters.assertValues(0, 100, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void cacheThrowEOFException() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    Uri testUri = Uri.parse("test_data");
    DataSpec dataSpec = new DataSpec(testUri, /* position= */ 0, /* length= */ 1000);

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                new CacheWriter(
                        new CacheDataSource(cache, dataSource),
                        dataSpec,
                        /* allowShortContent= */ false,
                        /* temporaryBuffer= */ null,
                        /* progressListener= */ null)
                    .cache());
    assertThat(DataSourceException.isCausedByPositionOutOfRange(exception)).isTrue();
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
            /* allowShortContent= */ false,
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
