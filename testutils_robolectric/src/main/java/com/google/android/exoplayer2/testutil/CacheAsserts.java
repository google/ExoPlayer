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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.net.Uri;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;

/** Assertion methods for {@link Cache}. */
public final class CacheAsserts {

  /**
   * Asserts that the cache content is equal to the data in the {@code fakeDataSet}.
   *
   * @throws IOException If an error occurred reading from the Cache.
   */
  public static void assertCachedData(Cache cache, FakeDataSet fakeDataSet) throws IOException {
    ArrayList<FakeData> allData = fakeDataSet.getAllData();
    Uri[] uris = new Uri[allData.size()];
    for (int i = 0; i < allData.size(); i++) {
      uris[i] = allData.get(i).uri;
    }
    assertCachedData(cache, fakeDataSet, uris);
  }

  /**
   * Asserts that the cache content is equal to the given subset of data in the {@code fakeDataSet}.
   *
   * @throws IOException If an error occurred reading from the Cache.
   */
  public static void assertCachedData(Cache cache, FakeDataSet fakeDataSet, String... uriStrings)
      throws IOException {
    Uri[] uris = new Uri[uriStrings.length];
    for (int i = 0; i < uriStrings.length; i++) {
      uris[i] = Uri.parse(uriStrings[i]);
    }
    assertCachedData(cache, fakeDataSet, uris);
  }

  /**
   * Asserts that the cache content is equal to the given subset of data in the {@code fakeDataSet}.
   *
   * @throws IOException If an error occurred reading from the Cache.
   */
  public static void assertCachedData(Cache cache, FakeDataSet fakeDataSet, Uri... uris)
      throws IOException {
    int totalLength = 0;
    for (Uri uri : uris) {
      byte[] data = fakeDataSet.getData(uri).getData();
      assertDataCached(cache, uri, data);
      totalLength += data.length;
    }
    assertThat(cache.getCacheSpace()).isEqualTo(totalLength);
  }

  /**
   * Asserts that the cache contains the given data for {@code uriString}.
   *
   * @throws IOException If an error occurred reading from the Cache.
   */
  public static void assertDataCached(Cache cache, Uri uri, byte[] expected) throws IOException {
    DataSpec dataSpec = new DataSpec(uri);
    assertDataCached(cache, dataSpec, expected);
  }

  /**
   * Asserts that the cache contains the given data for {@code dataSpec}.
   *
   * @throws IOException If an error occurred reading from the Cache.
   */
  public static void assertDataCached(Cache cache, DataSpec dataSpec, byte[] expected)
      throws IOException {
    DataSource dataSource = new CacheDataSource(cache, DummyDataSource.INSTANCE, 0);
    dataSource.open(dataSpec);
    try {
      byte[] bytes = TestUtil.readToEnd(dataSource);
      assertWithMessage("Cached data doesn't match expected for '" + dataSpec.uri + "',")
          .that(bytes)
          .isEqualTo(expected);
    } finally {
      dataSource.close();
    }
  }

  /**
   * Asserts that the read data from {@code dataSource} specified by {@code dataSpec} is equal to
   * {@code expected} or not.
   *
   * @throws IOException If an error occurred reading from the Cache.
   */
  public static void assertReadData(DataSource dataSource, DataSpec dataSpec, byte[] expected)
      throws IOException {
    DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
    byte[] bytes = null;
    try {
      bytes = Util.toByteArray(inputStream);
    } catch (IOException e) {
      // Ignore
    } finally {
      inputStream.close();
    }
    assertThat(bytes).isEqualTo(expected);
  }

  /** Asserts that the cache is empty. */
  public static void assertCacheEmpty(Cache cache) {
    assertThat(cache.getCacheSpace()).isEqualTo(0);
    assertThat(cache.getKeys()).isEmpty();
  }

  private CacheAsserts() {}
}
