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
import static com.google.android.exoplayer2.util.Util.toByteArray;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doAnswer;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.NavigableSet;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link SimpleCache}. */
@RunWith(AndroidJUnit4.class)
public class SimpleCacheTest {

  private static final byte[] ENCRYPTED_INDEX_KEY = Util.getUtf8Bytes("Bar12345Bar12345");
  private static final String KEY_1 = "key1";
  private static final String KEY_2 = "key2";

  private File testDir;
  private File cacheDir;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    testDir = Util.createTempFile(ApplicationProvider.getApplicationContext(), "SimpleCacheTest");
    assertThat(testDir.delete()).isTrue();
    assertThat(testDir.mkdirs()).isTrue();
    cacheDir = new File(testDir, "cache");
  }

  @After
  public void tearDown() {
    Util.recursiveDelete(testDir);
  }

  @Test
  public void newInstance_withEmptyDirectory() {
    SimpleCache cache = getSimpleCache();

    // Cache initialization should have created a non-negative UID.
    long uid = cache.getUid();
    assertThat(uid).isAtLeast(0L);
    // And the cache directory.
    assertThat(cacheDir.exists()).isTrue();

    // Reinitialization should load the same non-negative UID.
    cache.release();
    cache = getSimpleCache();
    assertThat(cache.getUid()).isEqualTo(uid);

    // Cache should be empty.
    assertThat(cache.getKeys()).isEmpty();
  }

  @Test
  public void newInstance_withConflictingFile_fails() throws IOException {
    // Creating a file where the cache should be will cause an error during initialization.
    assertThat(cacheDir.createNewFile()).isTrue();

    // Cache initialization should not throw an exception, but no UID will be generated.
    SimpleCache cache = getSimpleCache();
    long uid = cache.getUid();
    assertThat(uid).isEqualTo(-1L);
  }

  @Test
  public void newInstance_withExistingCacheDirectory_loadsCachedData() throws Exception {
    SimpleCache simpleCache = getSimpleCache();

    // Write some data and metadata to the cache.
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    simpleCache.releaseHoleSpan(holeSpan);
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    ContentMetadataMutations.setRedirectedUri(mutations, Uri.parse("https://redirect.google.com"));
    simpleCache.applyContentMetadataMutations(KEY_1, mutations);
    simpleCache.release();

    // Create a new instance pointing to the same directory.
    simpleCache = getSimpleCache();

    // Read the cached data and metadata back.
    CacheSpan fileSpan = simpleCache.startReadWrite(KEY_1, 0);
    assertCachedDataReadCorrect(fileSpan);
    assertThat(ContentMetadata.getRedirectedUri(simpleCache.getContentMetadata(KEY_1)))
        .isEqualTo(Uri.parse("https://redirect.google.com"));
  }

  @Test
  public void newInstance_withExistingCacheInstance_fails() {
    getSimpleCache();

    // Instantiation should fail because the directory is locked by the first instance.
    assertThrows(IllegalStateException.class, this::getSimpleCache);
  }

  @Test
  public void newInstance_withExistingCacheDirectory_resolvesInconsistentState() throws Exception {
    SimpleCache simpleCache = getSimpleCache();

    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    simpleCache.releaseHoleSpan(holeSpan);
    simpleCache.removeSpan(simpleCache.getCachedSpans(KEY_1).first());

    // Don't release the cache. This means the index file won't have been written to disk after the
    // span was removed. Move the cache directory instead, so we can reload it without failing the
    // folder locking check.
    File cacheDir2 = new File(testDir, "cache2");
    cacheDir.renameTo(cacheDir2);

    // Create a new instance pointing to the new directory.
    simpleCache = new SimpleCache(cacheDir2, new NoOpCacheEvictor());

    // The entry for KEY_1 should have been removed when the cache was reloaded.
    assertThat(simpleCache.getCachedSpans(KEY_1)).isEmpty();
  }

  @Test
  public void newInstance_withEncryptedIndex() throws Exception {
    SimpleCache simpleCache = getEncryptedSimpleCache(ENCRYPTED_INDEX_KEY);
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    simpleCache.releaseHoleSpan(holeSpan);
    simpleCache.release();

    // Create a new instance pointing to the same directory.
    simpleCache = getEncryptedSimpleCache(ENCRYPTED_INDEX_KEY);

    // Read the cached data back.
    CacheSpan fileSpan = simpleCache.startReadWrite(KEY_1, 0);
    assertCachedDataReadCorrect(fileSpan);
  }

  @Test
  public void newInstance_withEncryptedIndexAndWrongKey_clearsCache() throws Exception {
    SimpleCache simpleCache = getEncryptedSimpleCache(ENCRYPTED_INDEX_KEY);

    // Write data.
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    simpleCache.releaseHoleSpan(holeSpan);
    simpleCache.release();

    // Create a new instance pointing to the same directory, with a different key.
    simpleCache = getEncryptedSimpleCache(Util.getUtf8Bytes("Foo12345Foo12345"));

    // Cache should be cleared.
    assertThat(simpleCache.getKeys()).isEmpty();
    assertNoCacheFiles(cacheDir);
  }

  @Test
  public void newInstance_withEncryptedIndexAndNoKey_clearsCache() throws Exception {
    SimpleCache simpleCache = getEncryptedSimpleCache(ENCRYPTED_INDEX_KEY);

    // Write data.
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);
    simpleCache.releaseHoleSpan(holeSpan);
    simpleCache.release();

    // Create a new instance pointing to the same directory, with no key.
    simpleCache = getSimpleCache();

    // Cache should be cleared.
    assertThat(simpleCache.getKeys()).isEmpty();
    assertNoCacheFiles(cacheDir);
  }

  @Test
  public void write_oneLock_oneFile_thenRead() throws Exception {
    SimpleCache simpleCache = getSimpleCache();

    CacheSpan holeSpan1 = simpleCache.startReadWrite(KEY_1, 0);
    assertThat(holeSpan1.isCached).isFalse();
    assertThat(holeSpan1.isOpenEnded()).isTrue();
    addCache(simpleCache, KEY_1, 0, 15);

    CacheSpan readSpan = simpleCache.startReadWrite(KEY_1, 0);
    assertThat(readSpan.position).isEqualTo(0);
    assertThat(readSpan.length).isEqualTo(15);
    assertCachedDataReadCorrect(readSpan);
    assertThat(simpleCache.getCacheSpace()).isEqualTo(15);
  }

  @Test
  public void write_oneLock_twoFiles_thenRead() throws Exception {
    SimpleCache simpleCache = getSimpleCache();

    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 7);
    addCache(simpleCache, KEY_1, 7, 8);

    CacheSpan readSpan1 = simpleCache.startReadWrite(KEY_1, 0);
    assertThat(readSpan1.position).isEqualTo(0);
    assertThat(readSpan1.length).isEqualTo(7);
    assertCachedDataReadCorrect(readSpan1);
    CacheSpan readSpan2 = simpleCache.startReadWrite(KEY_1, 7);
    assertThat(readSpan2.position).isEqualTo(7);
    assertThat(readSpan2.length).isEqualTo(8);
    assertCachedDataReadCorrect(readSpan2);
    assertThat(simpleCache.getCacheSpace()).isEqualTo(15);
  }

  @Test
  public void write_differentKeyLocked_thenRead() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    CacheSpan holeSpan1 = simpleCache.startReadWrite(KEY_1, 50);

    CacheSpan holeSpan2 = simpleCache.startReadWrite(KEY_2, 50);
    assertThat(holeSpan1.isCached).isFalse();
    assertThat(holeSpan1.isOpenEnded()).isTrue();
    addCache(simpleCache, KEY_2, 0, 15);

    CacheSpan readSpan = simpleCache.startReadWrite(KEY_2, 0);
    assertThat(readSpan.length).isEqualTo(15);
    assertCachedDataReadCorrect(readSpan);
    assertThat(simpleCache.getCacheSpace()).isEqualTo(15);
  }

  @Test
  public void write_sameKeyLocked_fails() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    CacheSpan cacheSpan1 = simpleCache.startReadWrite(KEY_1, 50);

    assertThat(simpleCache.startReadWriteNonBlocking(KEY_1, 25)).isNull();
  }

  @Test
  public void applyContentMetadataMutations_setsContentLength() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    assertThat(ContentMetadata.getContentLength(simpleCache.getContentMetadata(KEY_1)))
        .isEqualTo(LENGTH_UNSET);

    ContentMetadataMutations mutations = new ContentMetadataMutations();
    ContentMetadataMutations.setContentLength(mutations, 15);
    simpleCache.applyContentMetadataMutations(KEY_1, mutations);
    assertThat(ContentMetadata.getContentLength(simpleCache.getContentMetadata(KEY_1)))
        .isEqualTo(15);
  }

  @Test
  public void removeSpans_removesSpansWithSameKey() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 10);
    addCache(simpleCache, KEY_1, 20, 10);
    simpleCache.releaseHoleSpan(holeSpan);
    holeSpan = simpleCache.startReadWrite(KEY_2, 20);
    addCache(simpleCache, KEY_2, 20, 10);
    simpleCache.releaseHoleSpan(holeSpan);

    simpleCache.removeResource(KEY_1);
    assertThat(simpleCache.getCachedSpans(KEY_1)).isEmpty();
    assertThat(simpleCache.getCachedSpans(KEY_2)).hasSize(1);
  }

  @Test
  public void getCachedLength_noCachedContent_returnsNegativeMaxHoleLength() {
    SimpleCache simpleCache = getSimpleCache();

    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ 100))
        .isEqualTo(-100);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ Long.MAX_VALUE))
        .isEqualTo(-Long.MAX_VALUE);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ LENGTH_UNSET))
        .isEqualTo(-Long.MAX_VALUE);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ 100))
        .isEqualTo(-100);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ Long.MAX_VALUE))
        .isEqualTo(-Long.MAX_VALUE);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ LENGTH_UNSET))
        .isEqualTo(-Long.MAX_VALUE);
  }

  @Test
  public void getCachedLength_returnsNegativeHoleLength() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, /* position= */ 0);
    addCache(simpleCache, KEY_1, /* position= */ 50, /* length= */ 50);
    simpleCache.releaseHoleSpan(holeSpan);

    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ 100))
        .isEqualTo(-50);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ Long.MAX_VALUE))
        .isEqualTo(-50);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ LENGTH_UNSET))
        .isEqualTo(-50);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ 100))
        .isEqualTo(-30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ Long.MAX_VALUE))
        .isEqualTo(-30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ LENGTH_UNSET))
        .isEqualTo(-30);
  }

  @Test
  public void getCachedLength_returnsCachedLength() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, /* position= */ 0);
    addCache(simpleCache, KEY_1, /* position= */ 0, /* length= */ 50);
    simpleCache.releaseHoleSpan(holeSpan);

    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ 100))
        .isEqualTo(50);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ Long.MAX_VALUE))
        .isEqualTo(50);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ LENGTH_UNSET))
        .isEqualTo(50);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ 100))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ Long.MAX_VALUE))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ LENGTH_UNSET))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ 15))
        .isEqualTo(15);
  }

  @Test
  public void getCachedLength_withMultipleAdjacentSpans_returnsCachedLength() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, /* position= */ 0);
    addCache(simpleCache, KEY_1, /* position= */ 0, /* length= */ 25);
    addCache(simpleCache, KEY_1, /* position= */ 25, /* length= */ 25);
    simpleCache.releaseHoleSpan(holeSpan);

    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ 100))
        .isEqualTo(50);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ Long.MAX_VALUE))
        .isEqualTo(50);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ LENGTH_UNSET))
        .isEqualTo(50);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ 100))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ Long.MAX_VALUE))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ LENGTH_UNSET))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ 15))
        .isEqualTo(15);
  }

  @Test
  public void getCachedLength_withMultipleNonAdjacentSpans_returnsCachedLength() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, /* position= */ 0);
    addCache(simpleCache, KEY_1, /* position= */ 0, /* length= */ 10);
    addCache(simpleCache, KEY_1, /* position= */ 15, /* length= */ 35);
    simpleCache.releaseHoleSpan(holeSpan);

    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ 100))
        .isEqualTo(10);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ Long.MAX_VALUE))
        .isEqualTo(10);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 0, /* length= */ LENGTH_UNSET))
        .isEqualTo(10);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ 100))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ Long.MAX_VALUE))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ LENGTH_UNSET))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedLength(KEY_1, /* position= */ 20, /* length= */ 15))
        .isEqualTo(15);
  }

  @Test
  public void getCachedBytes_noCachedContent_returnsZero() {
    SimpleCache simpleCache = getSimpleCache();

    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 0, /* length= */ 100))
        .isEqualTo(0);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 0, /* length= */ Long.MAX_VALUE))
        .isEqualTo(0);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 0, /* length= */ LENGTH_UNSET))
        .isEqualTo(0);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ 100))
        .isEqualTo(0);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ Long.MAX_VALUE))
        .isEqualTo(0);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ LENGTH_UNSET))
        .isEqualTo(0);
  }

  @Test
  public void getCachedBytes_withMultipleAdjacentSpans_returnsCachedBytes() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, /* position= */ 0);
    addCache(simpleCache, KEY_1, /* position= */ 0, /* length= */ 25);
    addCache(simpleCache, KEY_1, /* position= */ 25, /* length= */ 25);
    simpleCache.releaseHoleSpan(holeSpan);

    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 0, /* length= */ 100))
        .isEqualTo(50);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 0, /* length= */ Long.MAX_VALUE))
        .isEqualTo(50);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 0, /* length= */ LENGTH_UNSET))
        .isEqualTo(50);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ 100))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ Long.MAX_VALUE))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ LENGTH_UNSET))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ 15))
        .isEqualTo(15);
  }

  @Test
  public void getCachedBytes_withMultipleNonAdjacentSpans_returnsCachedBytes() throws Exception {
    SimpleCache simpleCache = getSimpleCache();
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, /* position= */ 0);
    addCache(simpleCache, KEY_1, /* position= */ 0, /* length= */ 10);
    addCache(simpleCache, KEY_1, /* position= */ 15, /* length= */ 35);
    simpleCache.releaseHoleSpan(holeSpan);

    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 0, /* length= */ 100))
        .isEqualTo(45);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 0, /* length= */ Long.MAX_VALUE))
        .isEqualTo(45);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 0, /* length= */ LENGTH_UNSET))
        .isEqualTo(45);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ 100))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ Long.MAX_VALUE))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ LENGTH_UNSET))
        .isEqualTo(30);
    assertThat(simpleCache.getCachedBytes(KEY_1, /* position= */ 20, /* length= */ 10))
        .isEqualTo(10);
  }

  /* Tests https://github.com/google/ExoPlayer/issues/3260 case. */
  @Test
  public void exceptionDuringIndexStore_doesNotPreventEviction() throws Exception {
    CachedContentIndex contentIndex =
        Mockito.spy(new CachedContentIndex(TestUtil.getInMemoryDatabaseProvider()));
    SimpleCache simpleCache =
        new SimpleCache(
            cacheDir, new LeastRecentlyUsedCacheEvictor(20), contentIndex, /* fileIndex= */ null);

    // Add some content.
    CacheSpan holeSpan = simpleCache.startReadWrite(KEY_1, 0);
    addCache(simpleCache, KEY_1, 0, 15);

    // Make index.store() throw exception from now on.
    doAnswer(
            invocation -> {
              throw new CacheException("SimpleCacheTest");
            })
        .when(contentIndex)
        .store();

    // Adding more content should evict previous content.
    assertThrows(CacheException.class, () -> addCache(simpleCache, KEY_1, 15, 15));
    simpleCache.releaseHoleSpan(holeSpan);

    // Although store() failed, the first span should have been removed and the new one added.
    NavigableSet<CacheSpan> cachedSpans = simpleCache.getCachedSpans(KEY_1);
    assertThat(cachedSpans).hasSize(1);
    CacheSpan fileSpan = cachedSpans.first();
    assertThat(fileSpan.position).isEqualTo(15);
    assertThat(fileSpan.length).isEqualTo(15);
  }

  @Test
  public void usingReleasedCache_throwsException() {
    SimpleCache simpleCache = new SimpleCache(cacheDir, new NoOpCacheEvictor());
    simpleCache.release();
    assertThrows(
        IllegalStateException.class, () -> simpleCache.startReadWriteNonBlocking(KEY_1, 0));
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
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(generateData(key, position, length));
    }
    simpleCache.commitFile(file, length);
  }

  private static void assertCachedDataReadCorrect(CacheSpan cacheSpan) throws IOException {
    assertThat(cacheSpan.isCached).isTrue();
    byte[] expected = generateData(cacheSpan.key, (int) cacheSpan.position, (int) cacheSpan.length);
    try (FileInputStream inputStream = new FileInputStream(cacheSpan.file)) {
      assertThat(toByteArray(inputStream)).isEqualTo(expected);
    }
  }

  private static void assertNoCacheFiles(File dir) {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        assertNoCacheFiles(file);
      } else {
        assertThat(file.getName().endsWith(SimpleCacheSpan.COMMON_SUFFIX)).isFalse();
      }
    }
  }

  private static byte[] generateData(String key, int position, int length) {
    byte[] bytes = new byte[length];
    new Random(key.hashCode() ^ position).nextBytes(bytes);
    return bytes;
  }
}
