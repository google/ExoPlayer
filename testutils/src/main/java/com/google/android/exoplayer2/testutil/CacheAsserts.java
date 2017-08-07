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
package com.google.android.exoplayer2.testutil;

import static junit.framework.Assert.assertEquals;

import android.net.Uri;
import android.test.MoreAsserts;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import junit.framework.Assert;

/**
 * Assertion methods for {@link Cache}.
 */
public final class CacheAsserts {

  /** Asserts that the cache content is equal to the data in the {@code fakeDataSet}. */
  public static void assertCachedData(Cache cache, FakeDataSet fakeDataSet) throws IOException {
    ArrayList<FakeData> allData = fakeDataSet.getAllData();
    String[] uriStrings = new String[allData.size()];
    for (int i = 0; i < allData.size(); i++) {
      uriStrings[i] = allData.get(i).uri;
    }
    assertCachedData(cache, fakeDataSet, uriStrings);
  }

  /**
   * Asserts that the cache content is equal to the given subset of data in the {@code fakeDataSet}.
   */
  public static void assertCachedData(Cache cache, FakeDataSet fakeDataSet, String... uriStrings)
      throws IOException {
    int totalLength = 0;
    for (String uriString : uriStrings) {
      byte[] data = fakeDataSet.getData(uriString).getData();
      assertDataCached(cache, uriString, data);
      totalLength += data.length;
    }
    assertEquals(totalLength, cache.getCacheSpace());
  }

  /** Asserts that the cache contains the given subset of data in the {@code fakeDataSet}. */
  public static void assertDataCached(Cache cache, FakeDataSet fakeDataSet, String... uriStrings)
      throws IOException {
    for (String uriString : uriStrings) {
      assertDataCached(cache, uriString, fakeDataSet.getData(uriString).getData());
    }
  }

  /** Asserts that the cache contains the given data for {@code uriString}. */
  public static void assertDataCached(Cache cache, String uriString, byte[] expected)
      throws IOException {
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
    MoreAsserts.assertEquals("Cached data doesn't match expected for '" + uriString + "',",
        expected, outputStream.toByteArray());
  }

  /** Asserts that there is no cache content for the given {@code uriStrings}. */
  public static void assertDataNotCached(Cache cache, String... uriStrings) {
    for (String uriString : uriStrings) {
      Assert.assertNull("There is cached data for '" + uriString + "',",
          cache.getCachedSpans(CacheUtil.generateKey(Uri.parse(uriString))));
    }
  }

  /** Asserts that the cache is empty. */
  public static void assertCacheEmpty(Cache cache) {
    assertEquals(0, cache.getCacheSpace());
  }

  private CacheAsserts() {}

}
