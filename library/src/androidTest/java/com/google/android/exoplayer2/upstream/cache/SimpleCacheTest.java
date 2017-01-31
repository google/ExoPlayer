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

import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.NavigableSet;
import java.util.Random;
import java.util.Set;

/**
 * Unit tests for {@link SimpleCache}.
 */
public class SimpleCacheTest extends InstrumentationTestCase {

  private static final String KEY_1 = "key1";

  private File cacheDir;

  @Override
  protected void setUp() throws Exception {
    this.cacheDir = TestUtil.createTempFolder(getInstrumentation().getContext());
  }

  @Override
  protected void tearDown() throws Exception {
    TestUtil.recursiveDelete(cacheDir);
  }

  public void testCommittingOneFile() throws Exception {
    SimpleCache simpleCache = getSimpleCache();

    CacheSpan cacheSpan1 = simpleCache.startReadWrite(KEY_1, 0);
    assertFalse(cacheSpan1.isCached);
    assertTrue(cacheSpan1.isOpenEnded());

    assertNull(simpleCache.startReadWriteNonBlocking(KEY_1, 0));

    assertEquals(0, simpleCache.getKeys().size());
    NavigableSet<CacheSpan> cachedSpans = simpleCache.getCachedSpans(KEY_1);
    assertTrue(cachedSpans == null || cachedSpans.size() == 0);
    assertEquals(0, simpleCache.getCacheSpace());
    assertEquals(0, cacheDir.listFiles().length);

    addCache(simpleCache, KEY_1, 0, 15);

    Set<String> cachedKeys = simpleCache.getKeys();
    assertEquals(1, cachedKeys.size());
    assertTrue(cachedKeys.contains(KEY_1));
    cachedSpans = simpleCache.getCachedSpans(KEY_1);
    assertEquals(1, cachedSpans.size());
    assertTrue(cachedSpans.contains(cacheSpan1));
    assertEquals(15, simpleCache.getCacheSpace());

    simpleCache.releaseHoleSpan(cacheSpan1);

    CacheSpan cacheSpan2 = simpleCache.startReadWrite(KEY_1, 0);
    assertTrue(cacheSpan2.isCached);
    assertFalse(cacheSpan2.isOpenEnded());
    assertEquals(15, cacheSpan2.length);
    assertCachedDataReadCorrect(cacheSpan2);
  }

  public void testReadCacheWithoutReleasingWriteCacheSpan() throws Exception {
    SimpleCache simpleCache = getSimpleCache();

    CacheSpan cacheSpan1 = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    CacheSpan cacheSpan2 = simpleCache.startReadWrite(KEY_1, 0);
    assertCachedDataReadCorrect(cacheSpan2);
    simpleCache.releaseHoleSpan(cacheSpan1);
  }

  public void testSetGetLength() throws Exception {
    SimpleCache simpleCache = getSimpleCache();

    assertEquals(C.LENGTH_UNSET, simpleCache.getContentLength(KEY_1));
    simpleCache.setContentLength(KEY_1, 15);
    assertEquals(15, simpleCache.getContentLength(KEY_1));

    simpleCache.startReadWrite(KEY_1, 0);

    addCache(simpleCache, KEY_1, 0, 15);

    simpleCache.setContentLength(KEY_1, 150);
    assertEquals(150, simpleCache.getContentLength(KEY_1));

    addCache(simpleCache, KEY_1, 140, 10);

    // Check if values are kept after cache is reloaded.
    SimpleCache simpleCache2 = getSimpleCache();
    Set<String> keys = simpleCache.getKeys();
    Set<String> keys2 = simpleCache2.getKeys();
    assertEquals(keys, keys2);
    for (String key : keys) {
      assertEquals(simpleCache.getContentLength(key), simpleCache2.getContentLength(key));
      assertEquals(simpleCache.getCachedSpans(key), simpleCache2.getCachedSpans(key));
    }

    // Removing the last span shouldn't cause the length be change next time cache loaded
    SimpleCacheSpan lastSpan = simpleCache2.startReadWrite(KEY_1, 145);
    simpleCache2.removeSpan(lastSpan);
    simpleCache2 = getSimpleCache();
    assertEquals(150, simpleCache2.getContentLength(KEY_1));
  }

  public void testReloadCache() throws Exception {
    SimpleCache simpleCache = getSimpleCache();

    // write data
    CacheSpan cacheSpan1 = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    simpleCache.releaseHoleSpan(cacheSpan1);

    // Reload cache
    simpleCache = getSimpleCache();

    // read data back
    CacheSpan cacheSpan2 = simpleCache.startReadWrite(KEY_1, 0);
    assertCachedDataReadCorrect(cacheSpan2);
  }

  public void testEncryptedIndex() throws Exception {
    byte[] key = "Bar12345Bar12345".getBytes(C.UTF8_NAME); // 128 bit key
    SimpleCache simpleCache = getEncryptedSimpleCache(key);

    // write data
    CacheSpan cacheSpan1 = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    simpleCache.releaseHoleSpan(cacheSpan1);

    // Reload cache
    simpleCache = getEncryptedSimpleCache(key);

    // read data back
    CacheSpan cacheSpan2 = simpleCache.startReadWrite(KEY_1, 0);
    assertCachedDataReadCorrect(cacheSpan2);
  }

  public void testEncryptedIndexWrongKey() throws Exception {
    byte[] key = "Bar12345Bar12345".getBytes(C.UTF8_NAME); // 128 bit key
    SimpleCache simpleCache = getEncryptedSimpleCache(key);

    // write data
    CacheSpan cacheSpan1 = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    simpleCache.releaseHoleSpan(cacheSpan1);

    // Reload cache
    byte[] key2 = "Foo12345Foo12345".getBytes(C.UTF8_NAME); // 128 bit key
    simpleCache = getEncryptedSimpleCache(key2);

    // Cache should be cleared
    assertEquals(0, simpleCache.getKeys().size());
    assertEquals(0, cacheDir.listFiles().length);
  }

  public void testEncryptedIndexLostKey() throws Exception {
    byte[] key = "Bar12345Bar12345".getBytes(C.UTF8_NAME); // 128 bit key
    SimpleCache simpleCache = getEncryptedSimpleCache(key);

    // write data
    CacheSpan cacheSpan1 = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    simpleCache.releaseHoleSpan(cacheSpan1);

    // Reload cache
    simpleCache = getSimpleCache();

    // Cache should be cleared
    assertEquals(0, simpleCache.getKeys().size());
    assertEquals(0, cacheDir.listFiles().length);
  }

  private SimpleCache getSimpleCache() {
    return new SimpleCache(cacheDir, new NoOpCacheEvictor());
  }

  private SimpleCache getEncryptedSimpleCache(byte[] secretKey) {
    return new SimpleCache(cacheDir, new NoOpCacheEvictor(), secretKey);
  }

  private static void addCache(SimpleCache simpleCache, String key, int position, int length)
      throws IOException {
    File file = simpleCache.startFile(key, position, length);
    FileOutputStream fos = new FileOutputStream(file);
    try {
      fos.write(generateData(key, position, length));
    } finally {
      fos.close();
    }
    simpleCache.commitFile(file);
  }

  private static void assertCachedDataReadCorrect(CacheSpan cacheSpan) throws IOException {
    assertTrue(cacheSpan.isCached);
    byte[] expected = generateData(cacheSpan.key, (int) cacheSpan.position, (int) cacheSpan.length);
    FileInputStream inputStream = new FileInputStream(cacheSpan.file);
    try {
      MoreAsserts.assertEquals(expected, Util.toByteArray(inputStream));
    } finally {
      inputStream.close();
    }
  }

  private static byte[] generateData(String key, int position, int length) {
    byte[] bytes = new byte[length];
    new Random((long) (key.hashCode() ^ position)).nextBytes(bytes);
    return bytes;
  }

}
