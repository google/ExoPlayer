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

import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.File;
import java.util.Random;
import junit.framework.TestCase;

/**
 * Unit tests for {@link CacheSpan}.
 */
public class CacheSpanTest extends TestCase {

  public void testCacheFile() throws Exception {
    assertCacheSpan(new File("parent"), "key", 0, 0);
    assertCacheSpan(new File("parent/"), "key", 1, 2);
    assertCacheSpan(new File("parent"), "<>:\"/\\|?*%", 1, 2);
    assertCacheSpan(new File("/"), "key", 1, 2);

    assertNullCacheSpan(new File("parent"), "", 1, 2);
    assertNullCacheSpan(new File("parent"), "key", -1, 2);
    assertNullCacheSpan(new File("parent"), "key", 1, -2);

    assertNotNull(CacheSpan.createCacheEntry(new File("/asd%aa.1.2.v2.exo")));
    assertNull(CacheSpan.createCacheEntry(new File("/asd%za.1.2.v2.exo")));

    assertCacheSpan(new File("parent"),
        "A newline (line feed) character \n"
            + "A carriage-return character followed immediately by a newline character \r\n"
            + "A standalone carriage-return character \r"
            + "A next-line character \u0085"
            + "A line-separator character \u2028"
            + "A paragraph-separator character \u2029", 1, 2);
  }

  public void testCacheFileNameRandomData() throws Exception {
    Random random = new Random(0);
    File parent = new File("parent");
    for (int i = 0; i < 1000; i++) {
      String key = TestUtil.buildTestString(1000, random);
      long offset = Math.abs(random.nextLong());
      long lastAccessTimestamp = Math.abs(random.nextLong());
      assertCacheSpan(parent, key, offset, lastAccessTimestamp);
    }
  }

  private void assertCacheSpan(File parent, String key, long offset, long lastAccessTimestamp) {
    File cacheFile = CacheSpan.getCacheFileName(parent, key, offset, lastAccessTimestamp);
    CacheSpan cacheSpan = CacheSpan.createCacheEntry(cacheFile);
    String message = cacheFile.toString();
    assertNotNull(message, cacheSpan);
    assertEquals(message, parent, cacheFile.getParentFile());
    assertEquals(message, key, cacheSpan.key);
    assertEquals(message, offset, cacheSpan.position);
    assertEquals(message, lastAccessTimestamp, cacheSpan.lastAccessTimestamp);
  }

  private void assertNullCacheSpan(File parent, String key, long offset,
      long lastAccessTimestamp) {
    File cacheFile = CacheSpan.getCacheFileName(parent, key, offset, lastAccessTimestamp);
    CacheSpan cacheSpan = CacheSpan.createCacheEntry(cacheFile);
    assertNull(cacheFile.toString(), cacheSpan);
  }

}
