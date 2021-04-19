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

import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCacheEmpty;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.min;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.CacheAsserts;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link CacheDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class CacheDataSourceTest {

  private static final byte[] TEST_DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
  private static final int CACHE_FRAGMENT_SIZE = 3;
  private static final String DATASPEC_KEY = "dataSpecKey";

  // Test data
  private Uri testDataUri;
  private Map<String, String> httpRequestHeaders;
  private DataSpec unboundedDataSpec;
  private DataSpec boundedDataSpec;
  private DataSpec unboundedDataSpecWithKey;
  private DataSpec boundedDataSpecWithKey;
  private String defaultCacheKey;
  private String customCacheKey;

  // Dependencies of SUT
  private CacheKeyFactory cacheKeyFactory;
  private File tempFolder;
  private SimpleCache cache;
  private FakeDataSource upstreamDataSource;

  @Before
  public void setUp() throws Exception {
    testDataUri = Uri.parse("https://www.test.com/data");
    httpRequestHeaders = new HashMap<>();
    httpRequestHeaders.put("Test-key", "Test-val");
    unboundedDataSpec = buildDataSpec(/* unbounded= */ true, /* key= */ null);
    boundedDataSpec = buildDataSpec(/* unbounded= */ false, /* key= */ null);
    unboundedDataSpecWithKey = buildDataSpec(/* unbounded= */ true, DATASPEC_KEY);
    boundedDataSpecWithKey = buildDataSpec(/* unbounded= */ false, DATASPEC_KEY);
    defaultCacheKey = CacheKeyFactory.DEFAULT.buildCacheKey(unboundedDataSpec);
    customCacheKey = "customKey." + defaultCacheKey;
    cacheKeyFactory = dataSpec -> customCacheKey;

    tempFolder =
        Util.createTempDirectory(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    cache =
        new SimpleCache(tempFolder, new NoOpCacheEvictor(), TestUtil.getInMemoryDatabaseProvider());
    upstreamDataSource = new FakeDataSource();
  }

  @After
  public void tearDown() throws Exception {
    Util.recursiveDelete(tempFolder);
  }

  @Test
  public void fragmentSize() throws Exception {
    CacheDataSource cacheDataSource = createCacheDataSource(false, false);
    assertReadDataContentLength(cacheDataSource, boundedDataSpec, false, false);
    for (String key : cache.getKeys()) {
      for (CacheSpan cacheSpan : cache.getCachedSpans(key)) {
        assertThat(cacheSpan.length <= CACHE_FRAGMENT_SIZE).isTrue();
        assertThat(cacheSpan.file.length() <= CACHE_FRAGMENT_SIZE).isTrue();
      }
    }
  }

  @Test
  public void cacheAndReadUnboundedRequest() throws Exception {
    assertCacheAndRead(unboundedDataSpec, /* unknownLength= */ false);
  }

  @Test
  public void cacheAndReadUnknownLength() throws Exception {
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ true);
  }

  @Test
  public void cacheAndReadUnboundedRequestUnknownLength() throws Exception {
    assertCacheAndRead(unboundedDataSpec, /* unknownLength= */ true);
  }

  @Test
  public void cacheAndRead() throws Exception {
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ false);
  }

  @Test
  public void propagatesHttpHeadersUpstream() throws Exception {
    CacheDataSource cacheDataSource =
        createCacheDataSource(/* setReadException= */ false, /* unknownLength= */ false);
    DataSpec dataSpec = buildDataSpec(/* position= */ 2, /* length= */ 5);
    cacheDataSource.open(dataSpec);

    DataSpec[] upstreamDataSpecs = upstreamDataSource.getAndClearOpenedDataSpecs();

    assertThat(upstreamDataSpecs).hasLength(1);
    assertThat(upstreamDataSpecs[0].httpRequestHeaders).isEqualTo(this.httpRequestHeaders);
  }

  @Test
  public void unsatisfiableRange() throws Exception {
    // Bounded request but the content length is unknown. This forces all data to be cached but not
    // the length.
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ true);

    // Now do an unbounded request. This will read all of the data from cache and then try to read
    // more from upstream which will cause to a 416 so CDS will store the length.
    CacheDataSource cacheDataSource =
        createCacheDataSource(/* setReadException= */ true, /* unknownLength= */ true);
    assertReadDataContentLength(
        cacheDataSource, unboundedDataSpec, /* unknownLength= */ true, /* customCacheKey= */ false);

    // If the user try to access off range then it should throw an IOException.
    try {
      cacheDataSource =
          createCacheDataSource(/* setReadException= */ false, /* unknownLength= */ false);
      cacheDataSource.open(buildDataSpec(TEST_DATA.length + 1, /* length= */ 1, defaultCacheKey));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  @Test
  public void cacheAndReadUnboundedRequestWithCacheKeyFactoryWithNullDataSpecCacheKey()
      throws Exception {
    assertCacheAndRead(unboundedDataSpec, /* unknownLength= */ false, cacheKeyFactory);
  }

  @Test
  public void cacheAndReadUnknownLengthWithCacheKeyFactoryOverridingWithNullDataSpecCacheKey()
      throws Exception {
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ true, cacheKeyFactory);
  }

  @Test
  public void
      testCacheAndReadUnboundedRequestUnknownLengthWithCacheKeyFactoryWithNullDataSpecCacheKey()
          throws Exception {
    assertCacheAndRead(unboundedDataSpec, /* unknownLength= */ true, cacheKeyFactory);
  }

  @Test
  public void cacheAndReadWithCacheKeyFactoryWithNullDataSpecCacheKey() throws Exception {
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ false, cacheKeyFactory);
  }

  @Test
  public void unsatisfiableRangeWithCacheKeyFactoryNullDataSpecCacheKey() throws Exception {
    // Bounded request but the content length is unknown. This forces all data to be cached but not
    // the length.
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ true, cacheKeyFactory);

    // Now do an unbounded request. This will read all of the data from cache and then try to read
    // more from upstream which will cause to a 416 so CDS will store the length.
    CacheDataSource cacheDataSource =
        createCacheDataSource(
            /* setReadException= */ true, /* unknownLength= */ true, cacheKeyFactory);
    assertReadDataContentLength(
        cacheDataSource, unboundedDataSpec, /* unknownLength= */ true, /* customCacheKey= */ true);

    // If the user try to access off range then it should throw an IOException.
    try {
      cacheDataSource =
          createCacheDataSource(
              /* setReadException= */ false, /* unknownLength= */ false, cacheKeyFactory);
      cacheDataSource.open(buildDataSpec(TEST_DATA.length + 1, /* length= */ 1, customCacheKey));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  @Test
  public void cacheAndReadUnboundedRequestWithCacheKeyFactoryOverridingDataSpecCacheKey()
      throws Exception {
    assertCacheAndRead(unboundedDataSpecWithKey, false, cacheKeyFactory);
  }

  @Test
  public void cacheAndReadUnknownLengthWithCacheKeyFactoryOverridingDataSpecCacheKey()
      throws Exception {
    assertCacheAndRead(boundedDataSpecWithKey, true, cacheKeyFactory);
  }

  @Test
  public void
      testCacheAndReadUnboundedRequestUnknownLengthWithCacheKeyFactoryOverridingDataSpecCacheKey()
          throws Exception {
    assertCacheAndRead(unboundedDataSpecWithKey, /* unknownLength= */ true, cacheKeyFactory);
  }

  @Test
  public void cacheAndReadWithCacheKeyFactoryOverridingDataSpecCacheKey() throws Exception {
    assertCacheAndRead(boundedDataSpecWithKey, /* unknownLength= */ false, cacheKeyFactory);
  }

  @Test
  public void unsatisfiableRangeWithCacheKeyFactoryOverridingDataSpecCacheKey() throws Exception {
    // Bounded request but the content length is unknown. This forces all data to be cached but not
    // the length.
    assertCacheAndRead(boundedDataSpecWithKey, /* unknownLength= */ true, cacheKeyFactory);

    // Now do an unbounded request. This will read all of the data from cache and then try to read
    // more from upstream which will cause to a 416 so CDS will store the length.
    CacheDataSource cacheDataSource =
        createCacheDataSource(
            /* setReadException= */ true, /* unknownLength= */ true, cacheKeyFactory);
    assertReadDataContentLength(
        cacheDataSource,
        unboundedDataSpecWithKey,
        /* unknownLength= */ true,
        /* customCacheKey= */ true);

    // If the user try to access off range then it should throw an IOException.
    try {
      cacheDataSource =
          createCacheDataSource(
              /* setReadException= */ false, /* unknownLength= */ false, cacheKeyFactory);
      cacheDataSource.open(buildDataSpec(TEST_DATA.length + 1, /* length= */ 1, customCacheKey));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  @Test
  public void boundedRead_doesNotSetContentLength() throws Exception {
    DataSpec dataSpec = buildDataSpec(0, TEST_DATA.length);

    // Read up to the end of the data, but since the DataSpec is bounded, the read doesn't see the
    // EOS, and so the content length remains unknown.
    CacheDataSource cacheDataSource = createCacheDataSource(false, true);

    assertReadData(cacheDataSource, dataSpec, true);
    assertThat(ContentMetadata.getContentLength(cache.getContentMetadata(defaultCacheKey)))
        .isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void unboundedRead_setsContentLength() throws IOException {
    // Perform an unbounded request for the whole data. This should cause the content length to
    // become known.
    CacheDataSource cacheDataSource = createCacheDataSource(false, true);
    assertReadDataContentLength(cacheDataSource, unboundedDataSpec, true, false);

    // Check the correct length is returned for an unbounded request.
    assertThat(
            cacheDataSource.open(
                buildDataSpec(TEST_DATA.length - 2, C.LENGTH_UNSET, defaultCacheKey)))
        .isEqualTo(2);
    cacheDataSource.close();
  }

  @Test
  public void unknownLengthContentReadInOneConnectionAndLengthIsResolved() throws Exception {
    FakeDataSource upstream = new FakeDataSource();
    upstream
        .getDataSet()
        .newData(testDataUri)
        .appendReadData(TEST_DATA)
        .setSimulateUnknownLength(true);
    CacheDataSource cacheDataSource = new CacheDataSource(cache, upstream, 0);

    cacheDataSource.open(unboundedDataSpec);
    Util.readToEnd(cacheDataSource);
    cacheDataSource.close();

    assertThat(upstream.getAndClearOpenedDataSpecs()).hasLength(1);
    assertThat(ContentMetadata.getContentLength(cache.getContentMetadata(defaultCacheKey)))
        .isEqualTo(TEST_DATA.length);
  }

  @Test
  public void ignoreCacheForUnsetLengthRequests() throws Exception {
    FakeDataSource upstream = new FakeDataSource();
    upstream.getDataSet().setData(testDataUri, TEST_DATA);
    CacheDataSource cacheDataSource =
        new CacheDataSource(
            cache, upstream, CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS);

    cacheDataSource.open(unboundedDataSpec);
    Util.readToEnd(cacheDataSource);
    cacheDataSource.close();

    assertThat(cache.getKeys()).isEmpty();
  }

  @Test
  public void readOnlyCache() throws Exception {
    CacheDataSource cacheDataSource = createCacheDataSource(false, false, 0, null);
    assertReadDataContentLength(cacheDataSource, boundedDataSpec, false, false);
    assertCacheEmpty(cache);
  }

  @Test
  public void switchToCacheSourceWithReadOnlyCacheDataSource() throws Exception {
    // Create a fake data source with a 1 MB default data.
    FakeDataSource upstream = new FakeDataSource();
    FakeData fakeData = upstream.getDataSet().newDefaultData().appendReadData(1024 * 1024 - 1);
    // Insert an action just before the end of the data to fail the test if reading from upstream
    // reaches end of the data.
    fakeData
        .appendReadAction(() -> fail("Read from upstream shouldn't reach to the end of the data."))
        .appendReadData(1);
    // Create cache read-only CacheDataSource.
    CacheDataSource cacheDataSource =
        new CacheDataSource(cache, upstream, new FileDataSource(), null, 0, null);

    // Open source and read some data from upstream as the data hasn't cached yet.
    cacheDataSource.open(unboundedDataSpec);
    byte[] buffer = new byte[1024];
    cacheDataSource.read(buffer, 0, buffer.length);

    // Cache the data. Although we use another FakeDataSource instance, it shouldn't matter.
    FakeDataSource upstream2 =
        new FakeDataSource(
            new FakeDataSource()
                .getDataSet()
                .newDefaultData()
                .appendReadData(1024 * 1024)
                .endData());
    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, upstream2),
            unboundedDataSpec,
            /* temporaryBuffer= */ null,
            /* progressListener= */ null);
    cacheWriter.cache();

    // Read the rest of the data.
    Util.readToEnd(cacheDataSource);
    cacheDataSource.close();
  }

  @Test
  public void switchToCacheSourceWithNonBlockingCacheDataSource() throws Exception {
    // Create a fake data source with a 1 MB default data.
    FakeDataSource upstream = new FakeDataSource();
    FakeData fakeData = upstream.getDataSet().newDefaultData().appendReadData(1024 * 1024 - 1);
    // Insert an action just before the end of the data to fail the test if reading from upstream
    // reaches end of the data.
    fakeData
        .appendReadAction(() -> fail("Read from upstream shouldn't reach to the end of the data."))
        .appendReadData(1);

    // Lock the content on the cache.
    CacheSpan cacheSpan = cache.startReadWriteNonBlocking(defaultCacheKey, 0, C.LENGTH_UNSET);
    assertThat(cacheSpan).isNotNull();
    assertThat(cacheSpan.isHoleSpan()).isTrue();

    // Create non blocking CacheDataSource.
    CacheDataSource cacheDataSource = new CacheDataSource(cache, upstream, 0);

    // Open source and read some data from upstream without writing to cache as the data is locked.
    cacheDataSource.open(unboundedDataSpec);
    byte[] buffer = new byte[1024];
    cacheDataSource.read(buffer, 0, buffer.length);

    // Unlock the span.
    cache.releaseHoleSpan(cacheSpan);
    assertCacheEmpty(cache);

    // Cache the data. Although we use another FakeDataSource instance, it shouldn't matter.
    FakeDataSource upstream2 =
        new FakeDataSource(
            new FakeDataSource()
                .getDataSet()
                .newDefaultData()
                .appendReadData(1024 * 1024)
                .endData());
    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, upstream2),
            unboundedDataSpec,
            /* temporaryBuffer= */ null,
            /* progressListener= */ null);
    cacheWriter.cache();

    // Read the rest of the data.
    Util.readToEnd(cacheDataSource);
    cacheDataSource.close();
  }

  @Test
  public void deleteCachedWhileReadingFromUpstreamWithReadOnlyCacheDataSourceDoesNotCrash()
      throws Exception {
    // Create a fake data source with a 1 KB default data.
    FakeDataSource upstream = new FakeDataSource();
    int dataLength = 1024;
    upstream.getDataSet().newDefaultData().appendReadData(dataLength).endData();

    // Cache the latter half of the data.
    int halfDataLength = 512;
    DataSpec dataSpec = buildDataSpec(halfDataLength, C.LENGTH_UNSET);
    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, upstream),
            dataSpec,
            /* temporaryBuffer= */ null,
            /* progressListener= */ null);
    cacheWriter.cache();

    // Create cache read-only CacheDataSource.
    CacheDataSource cacheDataSource =
        new CacheDataSource(cache, upstream, new FileDataSource(), null, 0, null);

    // Open source and read some data from upstream as the data hasn't cached yet.
    cacheDataSource.open(unboundedDataSpec);
    Util.readExactly(cacheDataSource, 100);

    // Delete cached data.
    cache.removeResource(cacheDataSource.getCacheKeyFactory().buildCacheKey(unboundedDataSpec));
    assertCacheEmpty(cache);

    // Read the rest of the data.
    Util.readToEnd(cacheDataSource);
    cacheDataSource.close();
  }

  @Test
  public void deleteCachedWhileReadingFromUpstreamWithBlockingCacheDataSourceDoesNotBlock()
      throws Exception {
    // Create a fake data source with a 1 KB default data.
    FakeDataSource upstream = new FakeDataSource();
    int dataLength = 1024;
    upstream.getDataSet().newDefaultData().appendReadData(dataLength).endData();

    // Cache the latter half of the data.
    int halfDataLength = 512;
    DataSpec dataSpec = buildDataSpec(/* position= */ 0, halfDataLength);
    CacheWriter cacheWriter =
        new CacheWriter(
            new CacheDataSource(cache, upstream),
            dataSpec,
            /* temporaryBuffer= */ null,
            /* progressListener= */ null);
    cacheWriter.cache();

    // Create blocking CacheDataSource.
    CacheDataSource cacheDataSource =
        new CacheDataSource(cache, upstream, CacheDataSource.FLAG_BLOCK_ON_CACHE);

    cacheDataSource.open(unboundedDataSpec);

    // Read the first half from upstream as it hasn't cached yet.
    Util.readExactly(cacheDataSource, halfDataLength);

    // Delete the cached latter half.
    NavigableSet<CacheSpan> cachedSpans = cache.getCachedSpans(defaultCacheKey);
    for (CacheSpan cachedSpan : cachedSpans) {
      if (cachedSpan.position >= halfDataLength) {
        cache.removeSpan(cachedSpan);
      }
    }

    // Read the rest of the data.
    Util.readToEnd(cacheDataSource);
    cacheDataSource.close();
  }

  private void assertCacheAndRead(DataSpec dataSpec, boolean unknownLength) throws IOException {
    assertCacheAndRead(dataSpec, unknownLength, /* cacheKeyFactory= */ null);
  }

  private void assertCacheAndRead(
      DataSpec dataSpec, boolean unknownLength, @Nullable CacheKeyFactory cacheKeyFactory)
      throws IOException {
    // Read all data from upstream and write to cache.
    CacheDataSource cacheDataSource =
        createCacheDataSource(/* setReadException= */ false, unknownLength, cacheKeyFactory);
    assertReadDataContentLength(cacheDataSource, dataSpec, unknownLength, cacheKeyFactory != null);

    // Just read from cache.
    cacheDataSource =
        createCacheDataSource(/* setReadException= */ true, unknownLength, cacheKeyFactory);
    assertReadDataContentLength(
        cacheDataSource,
        dataSpec,
        /* unknownLength= */ false,
        /* customCacheKey= */ cacheKeyFactory != null);
  }

  /**
   * Reads data until EOI and compares it to {@link #TEST_DATA}. Also checks content length returned
   * from open() call and the cached content length.
   */
  private void assertReadDataContentLength(
      CacheDataSource cacheDataSource,
      DataSpec dataSpec,
      boolean unknownLength,
      boolean customCacheKey)
      throws IOException {
    assertReadData(cacheDataSource, dataSpec, unknownLength);
    // If the request was unbounded then the content length should be cached, either because the
    // content length was known or because EOS was read. If the request was bounded then the content
    // length will not have been determined.
    ContentMetadata metadata =
        cache.getContentMetadata(customCacheKey ? this.customCacheKey : defaultCacheKey);
    assertThat(ContentMetadata.getContentLength(metadata))
        .isEqualTo(dataSpec.length == C.LENGTH_UNSET ? TEST_DATA.length : C.LENGTH_UNSET);
  }

  private void assertReadData(
      CacheDataSource cacheDataSource, DataSpec dataSpec, boolean unknownLength)
      throws IOException {
    int position = (int) dataSpec.position;
    int requestLength = (int) dataSpec.length;
    int readLength = TEST_DATA.length - position;
    if (requestLength != C.LENGTH_UNSET) {
      readLength = min(readLength, requestLength);
    }
    assertThat(cacheDataSource.open(dataSpec))
        .isEqualTo(unknownLength ? requestLength : readLength);
    cacheDataSource.close();

    byte[] expected = Arrays.copyOfRange(TEST_DATA, position, position + readLength);
    CacheAsserts.assertReadData(cacheDataSource, dataSpec, expected);
  }

  private CacheDataSource createCacheDataSource(boolean setReadException, boolean unknownLength) {
    return createCacheDataSource(
        setReadException, unknownLength, CacheDataSource.FLAG_BLOCK_ON_CACHE);
  }

  private CacheDataSource createCacheDataSource(
      boolean setReadException, boolean unknownLength, CacheKeyFactory cacheKeyFactory) {
    return createCacheDataSource(
        setReadException,
        unknownLength,
        CacheDataSource.FLAG_BLOCK_ON_CACHE,
        new CacheDataSink(cache, CACHE_FRAGMENT_SIZE),
        cacheKeyFactory);
  }

  private CacheDataSource createCacheDataSource(
      boolean setReadException, boolean unknownLength, @CacheDataSource.Flags int flags) {
    return createCacheDataSource(
        setReadException, unknownLength, flags, new CacheDataSink(cache, CACHE_FRAGMENT_SIZE));
  }

  private CacheDataSource createCacheDataSource(
      boolean setReadException,
      boolean unknownLength,
      @CacheDataSource.Flags int flags,
      CacheDataSink cacheWriteDataSink) {
    return createCacheDataSource(
        setReadException, unknownLength, flags, cacheWriteDataSink, /* cacheKeyFactory= */ null);
  }

  private CacheDataSource createCacheDataSource(
      boolean setReadException,
      boolean unknownLength,
      @CacheDataSource.Flags int flags,
      CacheDataSink cacheWriteDataSink,
      CacheKeyFactory cacheKeyFactory) {
    FakeData fakeData =
        upstreamDataSource
            .getDataSet()
            .newDefaultData()
            .setSimulateUnknownLength(unknownLength)
            .appendReadData(TEST_DATA);
    if (setReadException) {
      fakeData.appendReadError(new IOException("Shouldn't read from upstream"));
    }
    return new CacheDataSource(
        cache,
        upstreamDataSource,
        new FileDataSource(),
        cacheWriteDataSink,
        flags,
        /* eventListener= */ null,
        cacheKeyFactory);
  }

  private DataSpec buildDataSpec(boolean unbounded, @Nullable String key) {
    return buildDataSpec(/* position= */ 0, unbounded ? C.LENGTH_UNSET : TEST_DATA.length, key);
  }

  private DataSpec buildDataSpec(long position, long length) {
    return buildDataSpec(position, length, /* key= */ null);
  }

  private DataSpec buildDataSpec(long position, long length, @Nullable String key) {
    return new DataSpec.Builder()
        .setUri(testDataUri)
        .setPosition(position)
        .setLength(length)
        .setKey(key)
        .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
        .setHttpRequestHeaders(httpRequestHeaders)
        .build();
  }
}
