/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.google.android.exoplayer2.C.LENGTH_UNSET;
import static com.google.android.exoplayer2.upstream.cache.CacheAsserts.assertCacheEmpty;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.Arrays.copyOf;
import static java.util.Arrays.copyOfRange;
import static org.junit.Assert.fail;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link CacheDataSource}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public final class CacheDataSourceTest {

  private static final byte[] TEST_DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
  private static final int MAX_CACHE_FILE_SIZE = 3;

  private Uri testDataUri;
  private String testDataKey;
  private File tempFolder;
  private SimpleCache cache;

  @Before
  public void setUp() throws Exception {
    testDataUri = Uri.parse("test_data");
    testDataKey = CacheUtil.generateKey(testDataUri);
    tempFolder = Util.createTempDirectory(RuntimeEnvironment.application, "ExoPlayerTest");
    cache = new SimpleCache(tempFolder, new NoOpCacheEvictor());
  }

  @After
  public void tearDown() throws Exception {
    Util.recursiveDelete(tempFolder);
  }

  @Test
  public void testMaxCacheFileSize() throws Exception {
    CacheDataSource cacheDataSource = createCacheDataSource(false, false);
    assertReadDataContentLength(cacheDataSource, false, false);
    for (String key : cache.getKeys()) {
      for (CacheSpan cacheSpan : cache.getCachedSpans(key)) {
        assertThat(cacheSpan.length <= MAX_CACHE_FILE_SIZE).isTrue();
        assertThat(cacheSpan.file.length() <= MAX_CACHE_FILE_SIZE).isTrue();
      }
    }
  }

  @Test
  public void testCacheAndRead() throws Exception {
    assertCacheAndRead(false, false);
  }

  @Test
  public void testCacheAndReadUnboundedRequest() throws Exception {
    assertCacheAndRead(true, false);
  }

  @Test
  public void testCacheAndReadUnknownLength() throws Exception {
    assertCacheAndRead(false, true);
  }

  // Disabled test as we don't support caching of definitely unknown length content
  @Ignore
  @Test
  public void disabledTestCacheAndReadUnboundedRequestUnknownLength() throws Exception {
    assertCacheAndRead(true, true);
  }

  @Test
  public void testUnsatisfiableRange() throws Exception {
    // Bounded request but the content length is unknown. This forces all data to be cached but not
    // the length
    assertCacheAndRead(false, true);

    // Now do an unbounded request. This will read all of the data from cache and then try to read
    // more from upstream which will cause to a 416 so CDS will store the length.
    CacheDataSource cacheDataSource = createCacheDataSource(true, true);
    assertReadDataContentLength(cacheDataSource, true, true);

    // If the user try to access off range then it should throw an IOException
    try {
      cacheDataSource = createCacheDataSource(false, false);
      cacheDataSource.open(new DataSpec(testDataUri, TEST_DATA.length, 5, testDataKey));
      fail();
    } catch (IOException e) {
      // success
    }
  }

  @Test
  public void testContentLengthEdgeCases() throws Exception {
    // Read partial at EOS but don't cross it so length is unknown
    CacheDataSource cacheDataSource = createCacheDataSource(false, true);
    assertReadData(cacheDataSource, true, TEST_DATA.length - 2, 2);
    assertThat(cache.getContentLength(testDataKey)).isEqualTo(LENGTH_UNSET);

    // Now do an unbounded request for whole data. This will cause a bounded request from upstream.
    // End of data from upstream shouldn't be mixed up with EOS and cause length set wrong.
    cacheDataSource = createCacheDataSource(false, true);
    assertReadDataContentLength(cacheDataSource, true, true);

    // Now the length set correctly do an unbounded request with offset
    assertThat(
            cacheDataSource.open(
                new DataSpec(testDataUri, TEST_DATA.length - 2, LENGTH_UNSET, testDataKey)))
        .isEqualTo(2);

    // An unbounded request with offset for not cached content
    assertThat(
            cacheDataSource.open(
                new DataSpec(Uri.parse("notCachedUri"), TEST_DATA.length - 2, LENGTH_UNSET, null)))
        .isEqualTo(LENGTH_UNSET);
  }

  @Test
  public void testIgnoreCacheForUnsetLengthRequests() throws Exception {
    CacheDataSource cacheDataSource = createCacheDataSource(false, true,
        CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS);
    assertReadData(cacheDataSource, true, 0, C.LENGTH_UNSET);
    assertThat(cache.getKeys()).isEmpty();
  }

  @Test
  public void testReadOnlyCache() throws Exception {
    CacheDataSource cacheDataSource = createCacheDataSource(false, false, 0, null);
    assertReadDataContentLength(cacheDataSource, false, false);
    assertCacheEmpty(cache);
  }

  @Test
  public void testSwitchToCacheSourceWithReadOnlyCacheDataSource() throws Exception {
    // Create a fake data source with a 1 MB default data.
    FakeDataSource upstream = new FakeDataSource();
    FakeData fakeData = upstream.getDataSet().newDefaultData().appendReadData(1024 * 1024 - 1);
    // Insert an action just before the end of the data to fail the test if reading from upstream
    // reaches end of the data.
    fakeData
        .appendReadAction(
            new Runnable() {
              @Override
              public void run() {
                fail("Read from upstream shouldn't reach to the end of the data.");
              }
            })
        .appendReadData(1);
    // Create cache read-only CacheDataSource.
    CacheDataSource cacheDataSource =
        new CacheDataSource(cache, upstream, new FileDataSource(), null, 0, null);

    // Open source and read some data from upstream as the data hasn't cached yet.
    DataSpec dataSpec = new DataSpec(testDataUri, 0, C.LENGTH_UNSET, testDataKey);
    cacheDataSource.open(dataSpec);
    byte[] buffer = new byte[1024];
    cacheDataSource.read(buffer, 0, buffer.length);

    // Cache the data.
    // Although we use another FakeDataSource instance, it shouldn't matter.
    FakeDataSource upstream2 =
        new FakeDataSource(
            new FakeDataSource()
                .getDataSet()
                .newDefaultData()
                .appendReadData(1024 * 1024)
                .endData());
    CacheUtil.cache(dataSpec, cache, upstream2, null);

    // Read the rest of the data.
    while (true) {
      if (cacheDataSource.read(buffer, 0, buffer.length) == C.RESULT_END_OF_INPUT) {
        break;
      }
    }
    cacheDataSource.close();
  }

  @Test
  public void testSwitchToCacheSourceWithNonBlockingCacheDataSource() throws Exception {
    // Create a fake data source with a 1 MB default data.
    FakeDataSource upstream = new FakeDataSource();
    FakeData fakeData = upstream.getDataSet().newDefaultData().appendReadData(1024 * 1024 - 1);
    // Insert an action just before the end of the data to fail the test if reading from upstream
    // reaches end of the data.
    fakeData
        .appendReadAction(
            new Runnable() {
              @Override
              public void run() {
                fail("Read from upstream shouldn't reach to the end of the data.");
              }
            })
        .appendReadData(1);

    // Lock the content on the cache.
    SimpleCacheSpan cacheSpan = cache.startReadWriteNonBlocking(testDataKey, 0);
    assertThat(cacheSpan).isNotNull();
    assertThat(cacheSpan.isHoleSpan()).isTrue();

    // Create non blocking CacheDataSource.
    CacheDataSource cacheDataSource = new CacheDataSource(cache, upstream, 0);

    // Open source and read some data from upstream without writing to cache as the data is locked.
    DataSpec dataSpec = new DataSpec(testDataUri, 0, C.LENGTH_UNSET, testDataKey);
    cacheDataSource.open(dataSpec);
    byte[] buffer = new byte[1024];
    cacheDataSource.read(buffer, 0, buffer.length);

    // Unlock the span.
    cache.releaseHoleSpan(cacheSpan);
    assertCacheEmpty(cache);

    // Cache the data.
    // Although we use another FakeDataSource instance, it shouldn't matter.
    FakeDataSource upstream2 =
        new FakeDataSource(
            new FakeDataSource()
                .getDataSet()
                .newDefaultData()
                .appendReadData(1024 * 1024)
                .endData());
    CacheUtil.cache(dataSpec, cache, upstream2, null);

    // Read the rest of the data.
    while (true) {
      if (cacheDataSource.read(buffer, 0, buffer.length) == C.RESULT_END_OF_INPUT) {
        break;
      }
    }
    cacheDataSource.close();
  }

  private void assertCacheAndRead(boolean unboundedRequest, boolean simulateUnknownLength)
      throws IOException {
    // Read all data from upstream and write to cache
    CacheDataSource cacheDataSource = createCacheDataSource(false, simulateUnknownLength);
    assertReadDataContentLength(cacheDataSource, unboundedRequest, simulateUnknownLength);

    // Just read from cache
    cacheDataSource = createCacheDataSource(true, simulateUnknownLength);
    assertReadDataContentLength(cacheDataSource, unboundedRequest,
        false /*length is already cached*/);
  }

  /**
   * Reads data until EOI and compares it to {@link #TEST_DATA}. Also checks content length returned
   * from open() call and the cached content length.
   */
  private void assertReadDataContentLength(CacheDataSource cacheDataSource,
      boolean unboundedRequest, boolean unknownLength) throws IOException {
    int length = unboundedRequest ? C.LENGTH_UNSET : TEST_DATA.length;
    assertReadData(cacheDataSource, unknownLength, 0, length);
    assertWithMessage(
            "When the range specified, CacheDataSource doesn't reach EOS so shouldn't "
                + "cache content length")
        .that(cache.getContentLength(testDataKey))
        .isEqualTo(!unboundedRequest ? C.LENGTH_UNSET : TEST_DATA.length);
  }

  private void assertReadData(CacheDataSource cacheDataSource, boolean unknownLength, int position,
      int length) throws IOException {
    int testDataLength = TEST_DATA.length - position;
    if (length != C.LENGTH_UNSET) {
      testDataLength = Math.min(testDataLength, length);
    }
    assertThat(cacheDataSource.open(new DataSpec(testDataUri, position, length, testDataKey)))
        .isEqualTo(unknownLength ? length : testDataLength);

    byte[] buffer = new byte[100];
    int totalBytesRead = 0;
    while (true) {
      int read = cacheDataSource.read(buffer, totalBytesRead, buffer.length - totalBytesRead);
      if (read == C.RESULT_END_OF_INPUT) {
        break;
      }
      totalBytesRead += read;
    }
    assertThat(totalBytesRead).isEqualTo(testDataLength);
    assertThat(copyOf(buffer, totalBytesRead))
        .isEqualTo(copyOfRange(TEST_DATA, position, position + testDataLength));

    cacheDataSource.close();
  }

  private CacheDataSource createCacheDataSource(boolean setReadException,
      boolean simulateUnknownLength) {
    return createCacheDataSource(setReadException, simulateUnknownLength,
        CacheDataSource.FLAG_BLOCK_ON_CACHE);
  }

  private CacheDataSource createCacheDataSource(boolean setReadException,
      boolean simulateUnknownLength, @CacheDataSource.Flags int flags) {
    return createCacheDataSource(setReadException, simulateUnknownLength, flags,
        new CacheDataSink(cache, MAX_CACHE_FILE_SIZE));
  }

  private CacheDataSource createCacheDataSource(boolean setReadException,
      boolean simulateUnknownLength, @CacheDataSource.Flags int flags,
      CacheDataSink cacheWriteDataSink) {
    FakeDataSource upstream = new FakeDataSource();
    FakeData fakeData = upstream.getDataSet().newDefaultData()
        .setSimulateUnknownLength(simulateUnknownLength).appendReadData(TEST_DATA);
    if (setReadException) {
      fakeData.appendReadError(new IOException("Shouldn't read from upstream"));
    }
    return new CacheDataSource(cache, upstream, new FileDataSource(), cacheWriteDataSink,
        flags, null);
  }

}
