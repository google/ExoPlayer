package com.google.android.exoplayer2.upstream.cache;

import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import junit.framework.AssertionFailedError;

/**
 * Tests {@link CachedContentIndex}.
 */
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
    cacheDir = Util.createTempDirectory(getInstrumentation().getContext(), "ExoPlayerTest");
    index = new CachedContentIndex(cacheDir);
  }

  @Override
  protected void tearDown() throws Exception {
    Util.recursiveDelete(cacheDir);
  }

  public void testAddGetRemove() throws Exception {
    final String key1 = "key1";
    final String key2 = "key2";
    final String key3 = "key3";

    // Add two CachedContents with add methods
    CachedContent cachedContent1 = new CachedContent(5, key1, 10);
    index.addNew(cachedContent1);
    CachedContent cachedContent2 = index.add(key2);
    assertTrue(cachedContent1.id != cachedContent2.id);

    // add a span
    File cacheSpanFile = SimpleCacheSpanTest
        .createCacheSpanFile(cacheDir, cachedContent1.id, 10, 20, 30);
    SimpleCacheSpan span = SimpleCacheSpan.createCacheEntry(cacheSpanFile, index);
    assertNotNull(span);
    cachedContent1.addSpan(span);

    // Check if they are added and get method returns null if the key isn't found
    assertEquals(cachedContent1, index.get(key1));
    assertEquals(cachedContent2, index.get(key2));
    assertNull(index.get(key3));

    // test getAll()
    Collection<CachedContent> cachedContents = index.getAll();
    assertEquals(2, cachedContents.size());
    assertTrue(Arrays.asList(cachedContent1, cachedContent2).containsAll(cachedContents));

    // test getKeys()
    Set<String> keys = index.getKeys();
    assertEquals(2, keys.size());
    assertTrue(Arrays.asList(key1, key2).containsAll(keys));

    // test getKeyForId()
    assertEquals(key1, index.getKeyForId(cachedContent1.id));
    assertEquals(key2, index.getKeyForId(cachedContent2.id));

    // test remove()
    index.removeEmpty(key2);
    index.removeEmpty(key3);
    assertEquals(cachedContent1, index.get(key1));
    assertNull(index.get(key2));
    assertTrue(cacheSpanFile.exists());

    // test removeEmpty()
    index.addNew(cachedContent2);
    index.removeEmpty();
    assertEquals(cachedContent1, index.get(key1));
    assertNull(index.get(key2));
    assertTrue(cacheSpanFile.exists());
  }

  public void testStoreAndLoad() throws Exception {
    assertStoredAndLoadedEqual(index, new CachedContentIndex(cacheDir));
  }

  public void testLoadV1() throws Exception {
    FileOutputStream fos = new FileOutputStream(new File(cacheDir, CachedContentIndex.FILE_NAME));
    fos.write(testIndexV1File);
    fos.close();

    index.load();
    assertEquals(2, index.getAll().size());
    assertEquals(5, index.assignIdForKey("ABCDE"));
    assertEquals(10, index.getContentLength("ABCDE"));
    assertEquals(2, index.assignIdForKey("KLMNO"));
    assertEquals(2560, index.getContentLength("KLMNO"));
  }

  public void testStoreV1() throws Exception {
    index.addNew(new CachedContent(2, "KLMNO", 2560));
    index.addNew(new CachedContent(5, "ABCDE", 10));

    index.store();

    byte[] buffer = new byte[testIndexV1File.length];
    FileInputStream fos = new FileInputStream(new File(cacheDir, CachedContentIndex.FILE_NAME));
    assertEquals(testIndexV1File.length, fos.read(buffer));
    assertEquals(-1, fos.read());
    fos.close();

    // TODO: The order of the CachedContent stored in index file isn't defined so this test may fail
    // on a different implementation of the underlying set
    MoreAsserts.assertEquals(testIndexV1File, buffer);
  }

  public void testAssignIdForKeyAndGetKeyForId() throws Exception {
    final String key1 = "key1";
    final String key2 = "key2";
    int id1 = index.assignIdForKey(key1);
    int id2 = index.assignIdForKey(key2);
    assertEquals(key1, index.getKeyForId(id1));
    assertEquals(key2, index.getKeyForId(id2));
    assertTrue(id1 != id2);
    assertEquals(id1, index.assignIdForKey(key1));
    assertEquals(id2, index.assignIdForKey(key2));
  }

  public void testSetGetContentLength() throws Exception {
    final String key1 = "key1";
    assertEquals(C.LENGTH_UNSET, index.getContentLength(key1));
    index.setContentLength(key1, 10);
    assertEquals(10, index.getContentLength(key1));
  }

  public void testGetNewId() throws Exception {
    SparseArray<String> idToKey = new SparseArray<>();
    assertEquals(0, CachedContentIndex.getNewId(idToKey));
    idToKey.put(10, "");
    assertEquals(11, CachedContentIndex.getNewId(idToKey));
    idToKey.put(Integer.MAX_VALUE, "");
    assertEquals(0, CachedContentIndex.getNewId(idToKey));
    idToKey.put(0, "");
    assertEquals(1, CachedContentIndex.getNewId(idToKey));
  }

  public void testEncryption() throws Exception {
    byte[] key = "Bar12345Bar12345".getBytes(C.UTF8_NAME); // 128 bit key
    byte[] key2 = "Foo12345Foo12345".getBytes(C.UTF8_NAME); // 128 bit key

    assertStoredAndLoadedEqual(new CachedContentIndex(cacheDir, key),
        new CachedContentIndex(cacheDir, key));

    // Rename the index file from the test above
    File file1 = new File(cacheDir, CachedContentIndex.FILE_NAME);
    File file2 = new File(cacheDir, "file2compare");
    assertTrue(file1.renameTo(file2));

    // Write a new index file
    assertStoredAndLoadedEqual(new CachedContentIndex(cacheDir, key),
        new CachedContentIndex(cacheDir, key));

    assertEquals(file2.length(), file1.length());
    // Assert file content is different
    FileInputStream fis1 = new FileInputStream(file1);
    FileInputStream fis2 = new FileInputStream(file2);
    for (int b; (b = fis1.read()) == fis2.read(); ) {
      assertTrue(b != -1);
    }

    boolean threw = false;
    try {
      assertStoredAndLoadedEqual(new CachedContentIndex(cacheDir, key),
          new CachedContentIndex(cacheDir, key2));
    } catch (AssertionFailedError e) {
      threw = true;
    }
    assertTrue("Encrypted index file can not be read with different encryption key", threw);

    try {
      assertStoredAndLoadedEqual(new CachedContentIndex(cacheDir, key),
          new CachedContentIndex(cacheDir));
    } catch (AssertionFailedError e) {
      threw = true;
    }
    assertTrue("Encrypted index file can not be read without encryption key", threw);

    // Non encrypted index file can be read even when encryption key provided.
    assertStoredAndLoadedEqual(new CachedContentIndex(cacheDir),
        new CachedContentIndex(cacheDir, key));

    // Test multiple store() calls
    CachedContentIndex index = new CachedContentIndex(cacheDir, key);
    index.addNew(new CachedContent(15, "key3", 110));
    index.store();
    assertStoredAndLoadedEqual(index, new CachedContentIndex(cacheDir, key));
  }

  private void assertStoredAndLoadedEqual(CachedContentIndex index, CachedContentIndex index2)
      throws IOException {
    index.addNew(new CachedContent(5, "key1", 10));
    index.add("key2");
    index.store();

    index2.load();
    Set<String> keys = index.getKeys();
    Set<String> keys2 = index2.getKeys();
    assertEquals(keys, keys2);
    for (String key : keys) {
      assertEquals(index.getContentLength(key), index2.getContentLength(key));
      assertEquals(index.get(key).getSpans(), index2.get(key).getSpans());
    }
  }

}
