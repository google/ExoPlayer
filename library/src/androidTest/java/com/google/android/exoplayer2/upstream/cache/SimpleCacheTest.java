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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.NavigableSet;
import java.util.Set;

/**
 * Unit tests for {@link SimpleCache}.
 */
public class SimpleCacheTest extends InstrumentationTestCase {

  private static final String KEY_1 = "key1";

  private File cacheDir;

  @Override
  protected void setUp() throws Exception {
    // Create a temporary folder
    cacheDir = File.createTempFile("SimpleCacheTest", null);
    assertTrue(cacheDir.delete());
    assertTrue(cacheDir.mkdir());
  }

  @Override
  protected void tearDown() throws Exception {
    TestUtil.recursiveDelete(cacheDir);
  }

  public void testCommittingOneFile() throws Exception {
    SimpleCache simpleCache = new SimpleCache(cacheDir, new NoOpCacheEvictor());

    CacheSpan cacheSpan = simpleCache.startReadWrite(KEY_1, 0);
    assertFalse(cacheSpan.isCached);
    assertTrue(cacheSpan.isOpenEnded());

    assertNull(simpleCache.startReadWriteNonBlocking(KEY_1, 0));

    assertEquals(0, simpleCache.getKeys().size());
    NavigableSet<CacheSpan> cachedSpans = simpleCache.getCachedSpans(KEY_1);
    assertTrue(cachedSpans == null || cachedSpans.size() == 0);
    assertEquals(0, simpleCache.getCacheSpace());
    assertEquals(0, cacheDir.listFiles().length);

    addCache(simpleCache, 0, 15);

    Set<String> cachedKeys = simpleCache.getKeys();
    assertEquals(1, cachedKeys.size());
    assertTrue(cachedKeys.contains(KEY_1));
    cachedSpans = simpleCache.getCachedSpans(KEY_1);
    assertEquals(1, cachedSpans.size());
    assertTrue(cachedSpans.contains(cacheSpan));
    assertEquals(15, simpleCache.getCacheSpace());

    cacheSpan = simpleCache.startReadWrite(KEY_1, 0);
    assertTrue(cacheSpan.isCached);
    assertFalse(cacheSpan.isOpenEnded());
    assertEquals(15, cacheSpan.length);
  }

  public void testSetGetLength() throws Exception {
    SimpleCache simpleCache = new SimpleCache(cacheDir, new NoOpCacheEvictor());

    assertEquals(C.LENGTH_UNSET, simpleCache.getContentLength(KEY_1));
    assertTrue(simpleCache.setContentLength(KEY_1, 15));
    assertEquals(15, simpleCache.getContentLength(KEY_1));

    simpleCache.startReadWrite(KEY_1, 0);

    addCache(simpleCache, 0, 15);

    assertTrue(simpleCache.setContentLength(KEY_1, 150));
    assertEquals(150, simpleCache.getContentLength(KEY_1));

    addCache(simpleCache, 140, 10);

    // Try to set length shorter then the content
    assertFalse(simpleCache.setContentLength(KEY_1, 15));
    assertEquals("Content length should be unchanged.",
        150, simpleCache.getContentLength(KEY_1));

    /* TODO Enable when the length persistance is fixed
    // Check if values are kept after cache is reloaded.
    simpleCache = new SimpleCache(cacheDir, new NoOpCacheEvictor());
    assertEquals(150, simpleCache.getContentLength(KEY_1));
    CacheSpan lastSpan = simpleCache.startReadWrite(KEY_1, 145);

    // Removing the last span shouldn't cause the length be change next time cache loaded
    simpleCache.removeSpan(lastSpan);
    simpleCache = new SimpleCache(cacheDir, new NoOpCacheEvictor());
    assertEquals(150, simpleCache.getContentLength(KEY_1));
     */
  }

  private void addCache(SimpleCache simpleCache, int position, int length) throws IOException {
    File file = simpleCache.startFile(KEY_1, position, length);
    FileOutputStream fos = new FileOutputStream(file);
    fos.write(new byte[length]);
    fos.close();
    simpleCache.commitFile(file);
  }

}
