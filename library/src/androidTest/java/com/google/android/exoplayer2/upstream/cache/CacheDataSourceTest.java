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

import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.FakeDataSource.Builder;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Unit tests for {@link CacheDataSource}.
 */
public class CacheDataSourceTest extends InstrumentationTestCase {

  private static final byte[] TEST_DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
  private static final int MAX_CACHE_FILE_SIZE = 3;
  private static final String KEY_1 = "key 1";
  private static final String KEY_2 = "key 2";

  private File cacheDir;
  private SimpleCache simpleCache;

  @Override
  protected void setUp() throws Exception {
    cacheDir = TestUtil.createTempFolder(getInstrumentation().getContext());
    simpleCache = new SimpleCache(cacheDir, new NoOpCacheEvictor());
  }

  @Override
  protected void tearDown() throws Exception {
    TestUtil.recursiveDelete(cacheDir);
  }

  public void testMaxCacheFileSize() throws Exception {
    CacheDataSource cacheDataSource = createCacheDataSource(false, false);
    assertReadDataContentLength(cacheDataSource, false, false);
    File[] files = cacheDir.listFiles();
    for (File file : files) {
      if (!file.getName().equals(CachedContentIndex.FILE_NAME)) {
        assertTrue(file.length() <= MAX_CACHE_FILE_SIZE);
      }
    }
  }

  public void testCacheAndRead() throws Exception {
    assertCacheAndRead(false, false);
  }

  public void testCacheAndReadUnboundedRequest() throws Exception {
    assertCacheAndRead(true, false);
  }

  public void testCacheAndReadUnknownLength() throws Exception {
    assertCacheAndRead(false, true);
  }

  // Disabled test as we don't support caching of definitely unknown length content
  public void disabledTestCacheAndReadUnboundedRequestUnknownLength() throws Exception {
    assertCacheAndRead(true, true);
  }

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
      cacheDataSource.open(new DataSpec(Uri.EMPTY, TEST_DATA.length, 5, KEY_1));
      fail();
    } catch (IOException e) {
      // success
    }
  }

  public void testContentLengthEdgeCases() throws Exception {
    // Read partial at EOS but don't cross it so length is unknown
    CacheDataSource cacheDataSource = createCacheDataSource(false, true);
    assertReadData(cacheDataSource, true, TEST_DATA.length - 2, 2);
    assertEquals(C.LENGTH_UNSET, simpleCache.getContentLength(KEY_1));

    // Now do an unbounded request for whole data. This will cause a bounded request from upstream.
    // End of data from upstream shouldn't be mixed up with EOS and cause length set wrong.
    cacheDataSource = createCacheDataSource(false, true);
    assertReadDataContentLength(cacheDataSource, true, true);

    // Now the length set correctly do an unbounded request with offset
    assertEquals(2, cacheDataSource.open(new DataSpec(Uri.EMPTY, TEST_DATA.length - 2,
        C.LENGTH_UNSET, KEY_1)));

    // An unbounded request with offset for not cached content
    assertEquals(C.LENGTH_UNSET, cacheDataSource.open(new DataSpec(Uri.EMPTY, TEST_DATA.length - 2,
        C.LENGTH_UNSET, KEY_2)));
  }

  public void testIgnoreCacheForUnsetLengthRequests() throws Exception {
    CacheDataSource cacheDataSource = createCacheDataSource(false, true,
        CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS);
    assertReadData(cacheDataSource, true, 0, C.LENGTH_UNSET);
    MoreAsserts.assertEmpty(simpleCache.getKeys());
  }

  private void assertCacheAndRead(boolean unboundedRequest, boolean simulateUnknownLength)
      throws IOException {
    // Read all data from upstream and cache
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
    assertEquals("When the range specified, CacheDataSource doesn't reach EOS so shouldn't cache "
        + "content length", !unboundedRequest ? C.LENGTH_UNSET : TEST_DATA.length,
        simpleCache.getContentLength(KEY_1));
  }

  private void assertReadData(CacheDataSource cacheDataSource, boolean unknownLength, int position,
      int length) throws IOException {
    int actualLength = TEST_DATA.length - position;
    if (length != C.LENGTH_UNSET) {
      actualLength = Math.min(actualLength, length);
    }
    assertEquals(unknownLength ? length : actualLength,
        cacheDataSource.open(new DataSpec(Uri.EMPTY, position, length, KEY_1)));

    byte[] buffer = new byte[100];
    int index = 0;
    while (true) {
      int read = cacheDataSource.read(buffer, index, buffer.length - index);
      if (read == C.RESULT_END_OF_INPUT) {
        break;
      }
      index += read;
    }
    assertEquals(actualLength, index);
    MoreAsserts.assertEquals(Arrays.copyOfRange(TEST_DATA, position, position + actualLength),
        Arrays.copyOf(buffer, index));

    cacheDataSource.close();
  }

  private CacheDataSource createCacheDataSource(boolean setReadException,
      boolean simulateUnknownLength) {
    return createCacheDataSource(setReadException, simulateUnknownLength,
        CacheDataSource.FLAG_BLOCK_ON_CACHE);
  }

  private CacheDataSource createCacheDataSource(boolean setReadException,
      boolean simulateUnknownLength, @CacheDataSource.Flags int flags) {
    Builder builder = new Builder();
    if (setReadException) {
      builder.appendReadError(new IOException("Shouldn't read from upstream"));
    }
    builder.setSimulateUnknownLength(simulateUnknownLength);
    builder.appendReadData(TEST_DATA);
    FakeDataSource upstream = builder.build();
    return new CacheDataSource(simpleCache, upstream, flags, MAX_CACHE_FILE_SIZE);
  }

}
