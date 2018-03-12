package com.google.android.exoplayer2.upstream.cache;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.test.InstrumentationTestCase;
import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/** Tests {@link CachedContentIndex}. */
public class CachedContentIndexTest extends InstrumentationTestCase {

  private final byte[] testIndexV1File = {
      0, 0, 0, 1, // version
      0, 0, 0, 0, // flags
      0, 0, 0, 2, // number_of_CachedContent
      0, 0, 0, 5, // cache_id
      0, 5, 65, 66, 67, 68, 69, // cache_key
      0, 0, 0, 0, 0, 0, 0, 10, // original_content_length
      0, 0, 0, 2, // cache_id
      0, 5, 75, 76, 77, 78, 79, // cache_key
      0, 0, 0, 0, 0, 0, 10, 0, // original_content_length
      (byte) 0xF6, (byte) 0xFB, 0x50, 0x41 // hashcode_of_CachedContent_array
  };
  private CachedContentIndex index;
  private File cacheDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    cacheDir = Util.createTempDirectory(getInstrumentation().getContext(), "ExoPlayerTest");
    index = new CachedContentIndex(cacheDir);
  }

  @Override
  protected void tearDown() throws Exception {
    Util.recursiveDelete(cacheDir);
    super.tearDown();
  }

  public void testAddGetRemove() throws Exception {
    final String key1 = "key1";
    final String key2 = "key2";
    final String key3 = "key3";

    // Add two CachedContents with add methods
    CachedContent cachedContent1 = new CachedContent(5, key1);
    index.addNew(cachedContent1);
    CachedContent cachedContent2 = index.getOrAdd(key2);
    assertThat(cachedContent1.id != cachedContent2.id).isTrue();

    // add a span
    File cacheSpanFile =
        SimpleCacheSpanTest.createCacheSpanFile(cacheDir, cachedContent1.id, 10, 20, 30);
    SimpleCacheSpan span = SimpleCacheSpan.createCacheEntry(cacheSpanFile, index);
    assertThat(span).isNotNull();
    cachedContent1.addSpan(span);

    // Check if they are added and get method returns null if the key isn't found
    assertThat(index.get(key1)).isEqualTo(cachedContent1);
    assertThat(index.get(key2)).isEqualTo(cachedContent2);
    assertThat(index.get(key3)).isNull();

    // test getAll()
    Collection<CachedContent> cachedContents = index.getAll();
    assertThat(cachedContents).containsExactly(cachedContent1, cachedContent2);

    // test getKeys()
    Set<String> keys = index.getKeys();
    assertThat(keys).containsExactly(key1, key2);

    // test getKeyForId()
    assertThat(index.getKeyForId(cachedContent1.id)).isEqualTo(key1);
    assertThat(index.getKeyForId(cachedContent2.id)).isEqualTo(key2);

    // test remove()
    index.maybeRemove(key2);
    index.maybeRemove(key3);
    assertThat(index.get(key1)).isEqualTo(cachedContent1);
    assertThat(index.get(key2)).isNull();
    assertThat(cacheSpanFile.exists()).isTrue();

    // test removeEmpty()
    index.addNew(cachedContent2);
    index.removeEmpty();
    assertThat(index.get(key1)).isEqualTo(cachedContent1);
    assertThat(index.get(key2)).isNull();
    assertThat(cacheSpanFile.exists()).isTrue();
  }

  public void testStoreAndLoad() throws Exception {
    assertStoredAndLoadedEqual(index, new CachedContentIndex(cacheDir));
  }

  public void testLoadV1() throws Exception {
    FileOutputStream fos = new FileOutputStream(new File(cacheDir, CachedContentIndex.FILE_NAME));
    fos.write(testIndexV1File);
    fos.close();

    index.load();
    assertThat(index.getAll()).hasSize(2);
    assertThat(index.assignIdForKey("ABCDE")).isEqualTo(5);
    assertThat(index.getContentLength("ABCDE")).isEqualTo(10);
    assertThat(index.assignIdForKey("KLMNO")).isEqualTo(2);
    assertThat(index.getContentLength("KLMNO")).isEqualTo(2560);
  }

  public void testStoreV1() throws Exception {
    CachedContent cachedContent1 = new CachedContent(2, "KLMNO");
    cachedContent1.setLength(2560);
    index.addNew(cachedContent1);
    CachedContent cachedContent2 = new CachedContent(5, "ABCDE");
    cachedContent2.setLength(10);
    index.addNew(cachedContent2);

    index.store();

    byte[] buffer = new byte[testIndexV1File.length];
    FileInputStream fos = new FileInputStream(new File(cacheDir, CachedContentIndex.FILE_NAME));
    assertThat(fos.read(buffer)).isEqualTo(testIndexV1File.length);
    assertThat(fos.read()).isEqualTo(-1);
    fos.close();

    // TODO: The order of the CachedContent stored in index file isn't defined so this test may fail
    // on a different implementation of the underlying set
    assertThat(buffer).isEqualTo(testIndexV1File);
  }

  public void testAssignIdForKeyAndGetKeyForId() throws Exception {
    final String key1 = "key1";
    final String key2 = "key2";
    int id1 = index.assignIdForKey(key1);
    int id2 = index.assignIdForKey(key2);
    assertThat(index.getKeyForId(id1)).isEqualTo(key1);
    assertThat(index.getKeyForId(id2)).isEqualTo(key2);
    assertThat(id1 != id2).isTrue();
    assertThat(index.assignIdForKey(key1)).isEqualTo(id1);
    assertThat(index.assignIdForKey(key2)).isEqualTo(id2);
  }

  public void testSetGetContentLength() throws Exception {
    final String key1 = "key1";
    assertThat(index.getContentLength(key1)).isEqualTo(C.LENGTH_UNSET);
    index.setContentLength(key1, 10);
    assertThat(index.getContentLength(key1)).isEqualTo(10);
  }

  public void testGetNewId() throws Exception {
    SparseArray<String> idToKey = new SparseArray<>();
    assertThat(CachedContentIndex.getNewId(idToKey)).isEqualTo(0);
    idToKey.put(10, "");
    assertThat(CachedContentIndex.getNewId(idToKey)).isEqualTo(11);
    idToKey.put(Integer.MAX_VALUE, "");
    assertThat(CachedContentIndex.getNewId(idToKey)).isEqualTo(0);
    idToKey.put(0, "");
    assertThat(CachedContentIndex.getNewId(idToKey)).isEqualTo(1);
  }

  public void testEncryption() throws Exception {
    byte[] key = "Bar12345Bar12345".getBytes(C.UTF8_NAME); // 128 bit key
    byte[] key2 = "Foo12345Foo12345".getBytes(C.UTF8_NAME); // 128 bit key

    assertStoredAndLoadedEqual(
        new CachedContentIndex(cacheDir, key), new CachedContentIndex(cacheDir, key));

    // Rename the index file from the test above
    File file1 = new File(cacheDir, CachedContentIndex.FILE_NAME);
    File file2 = new File(cacheDir, "file2compare");
    assertThat(file1.renameTo(file2)).isTrue();

    // Write a new index file
    assertStoredAndLoadedEqual(
        new CachedContentIndex(cacheDir, key), new CachedContentIndex(cacheDir, key));

    assertThat(file1.length()).isEqualTo(file2.length());
    // Assert file content is different
    FileInputStream fis1 = new FileInputStream(file1);
    FileInputStream fis2 = new FileInputStream(file2);
    for (int b; (b = fis1.read()) == fis2.read(); ) {
      assertThat(b != -1).isTrue();
    }

    boolean threw = false;
    try {
      assertStoredAndLoadedEqual(
          new CachedContentIndex(cacheDir, key), new CachedContentIndex(cacheDir, key2));
    } catch (AssertionError e) {
      threw = true;
    }
    assertWithMessage("Encrypted index file can not be read with different encryption key")
        .that(threw)
        .isTrue();

    try {
      assertStoredAndLoadedEqual(
          new CachedContentIndex(cacheDir, key), new CachedContentIndex(cacheDir));
    } catch (AssertionError e) {
      threw = true;
    }
    assertWithMessage("Encrypted index file can not be read without encryption key")
        .that(threw)
        .isTrue();

    // Non encrypted index file can be read even when encryption key provided.
    assertStoredAndLoadedEqual(
        new CachedContentIndex(cacheDir), new CachedContentIndex(cacheDir, key));

    // Test multiple store() calls
    CachedContentIndex index = new CachedContentIndex(cacheDir, key);
    index.addNew(new CachedContent(15, "key3"));
    index.store();
    assertStoredAndLoadedEqual(index, new CachedContentIndex(cacheDir, key));
  }

  public void testRemoveEmptyNotLockedCachedContent() throws Exception {
    CachedContent cachedContent = new CachedContent(5, "key1");
    index.addNew(cachedContent);

    index.maybeRemove(cachedContent.key);

    assertThat(index.get(cachedContent.key)).isNull();
  }

  public void testCantRemoveNotEmptyCachedContent() throws Exception {
    CachedContent cachedContent = new CachedContent(5, "key1");
    index.addNew(cachedContent);
    File cacheSpanFile =
        SimpleCacheSpanTest.createCacheSpanFile(cacheDir, cachedContent.id, 10, 20, 30);
    SimpleCacheSpan span = SimpleCacheSpan.createCacheEntry(cacheSpanFile, index);
    cachedContent.addSpan(span);

    index.maybeRemove(cachedContent.key);

    assertThat(index.get(cachedContent.key)).isNotNull();
  }

  public void testCantRemoveLockedCachedContent() throws Exception {
    CachedContent cachedContent = new CachedContent(5, "key1");
    cachedContent.setLocked(true);
    index.addNew(cachedContent);

    index.maybeRemove(cachedContent.key);

    assertThat(index.get(cachedContent.key)).isNotNull();
  }

  private void assertStoredAndLoadedEqual(CachedContentIndex index, CachedContentIndex index2)
      throws IOException {
    index.addNew(new CachedContent(5, "key1"));
    index.getOrAdd("key2");
    index.store();

    index2.load();
    Set<String> keys = index.getKeys();
    Set<String> keys2 = index2.getKeys();
    assertThat(keys2).isEqualTo(keys);
    for (String key : keys) {
      assertThat(index2.getContentLength(key)).isEqualTo(index.getContentLength(key));
      assertThat(index2.get(key).getSpans()).isEqualTo(index.get(key).getSpans());
    }
  }
}
