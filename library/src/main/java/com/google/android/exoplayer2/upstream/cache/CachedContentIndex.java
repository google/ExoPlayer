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

import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.AtomicFile;
import com.google.android.exoplayer2.util.Util;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

/**
 * This class maintains the index of cached content.
 */
/*package*/ final class CachedContentIndex {

  public static final String FILE_NAME = "cached_content_index.exi";
  private static final int VERSION = 1;

  private final HashMap<String, CachedContent> keyToContent;
  private final SparseArray<String> idToKey;
  private final AtomicFile atomicFile;
  private boolean changed;

  /** Creates a CachedContentIndex which works on the index file in the given cacheDir. */
  public CachedContentIndex(File cacheDir) {
    keyToContent = new HashMap<>();
    idToKey = new SparseArray<>();
    atomicFile = new AtomicFile(new File(cacheDir, FILE_NAME));
  }

  /** Loads the index file. */
  public void load() {
    Assertions.checkState(!changed);
    File cacheIndex = atomicFile.getBaseFile();
    if (cacheIndex.exists()) {
      if (!readFile()) {
        cacheIndex.delete();
        keyToContent.clear();
        idToKey.clear();
      }
    }
  }

  /** Stores the index data to index file if there is a change. */
  public void store() {
    if (!changed) {
      return;
    }
    writeFile();
    changed = false;
  }

  /**
   * Adds the given key to the index if it isn't there already.
   *
   * @param key The cache key that uniquely identifies the original stream.
   * @return A new or existing CachedContent instance with the given key.
   */
  public CachedContent add(String key) {
    CachedContent cachedContent = keyToContent.get(key);
    if (cachedContent == null) {
      cachedContent = addNew(key, C.LENGTH_UNSET);
    }
    return cachedContent;
  }

  /** Returns a CachedContent instance with the given key or null if there isn't one. */
  public CachedContent get(String key) {
    return keyToContent.get(key);
  }

  /**
   * Returns a Collection of all CachedContent instances in the index. The collection is backed by
   * the {@code keyToContent} map, so changes to the map are reflected in the collection, and
   * vice-versa. If the map is modified while an iteration over the collection is in progress
   * (except through the iterator's own remove operation), the results of the iteration are
   * undefined.
   */
  public Collection<CachedContent> getAll() {
    return keyToContent.values();
  }

  /** Returns an existing or new id assigned to the given key. */
  public int assignIdForKey(String key) {
    return add(key).id;
  }

  /** Returns the key which has the given id assigned. */
  public String getKeyForId(int id) {
    return idToKey.get(id);
  }

  /**
   * Removes {@link CachedContent} with the given key from index. It shouldn't contain any spans.
   *
   * @throws IllegalStateException If {@link CachedContent} isn't empty.
   */
  public void removeEmpty(String key) {
    CachedContent cachedContent = keyToContent.remove(key);
    if (cachedContent != null) {
      Assertions.checkState(cachedContent.isEmpty());
      idToKey.remove(cachedContent.id);
      changed = true;
    }
  }

  /** Removes empty {@link CachedContent} instances from index. */
  public void removeEmpty() {
    LinkedList<String> cachedContentToBeRemoved = new LinkedList<>();
    for (CachedContent cachedContent : keyToContent.values()) {
      if (cachedContent.isEmpty()) {
        cachedContentToBeRemoved.add(cachedContent.key);
      }
    }
    for (String key : cachedContentToBeRemoved) {
      removeEmpty(key);
    }
  }

  /**
   * Returns a set of all content keys. The set is backed by the {@code keyToContent} map, so
   * changes to the map are reflected in the set, and vice-versa. If the map is modified while an
   * iteration over the set is in progress (except through the iterator's own remove operation), the
   * results of the iteration are undefined.
   */
  public Set<String> getKeys() {
    return keyToContent.keySet();
  }

  /**
   * Sets the content length for the given key. A new {@link CachedContent} is added if there isn't
   * one already with the given key.
   */
  public void setContentLength(String key, long length) {
    CachedContent cachedContent = get(key);
    if (cachedContent != null) {
      if (cachedContent.getLength() != length) {
        cachedContent.setLength(length);
        changed = true;
      }
    } else {
      addNew(key, length);
    }
  }

  /**
   * Returns the content length for the given key if one set, or {@link
   * com.google.android.exoplayer2.C#LENGTH_UNSET} otherwise.
   */
  public long getContentLength(String key) {
    CachedContent cachedContent = get(key);
    return cachedContent == null ? C.LENGTH_UNSET : cachedContent.getLength();
  }

  private boolean readFile() {
    DataInputStream input = null;
    try {
      input = new DataInputStream(atomicFile.openRead());
      int version = input.readInt();
      if (version != VERSION) {
        // Currently there is no other version
        return false;
      }
      input.readInt(); // ignore flags placeholder
      int count = input.readInt();
      int hashCode = 0;
      for (int i = 0; i < count; i++) {
        CachedContent cachedContent = new CachedContent(input);
        addNew(cachedContent);
        hashCode += cachedContent.headerHashCode();
      }
      if (input.readInt() != hashCode) {
        return false;
      }
    } catch (IOException e) {
      return false;
    } finally {
      if (input != null) {
        Util.closeQuietly(input);
      }
    }
    return true;
  }

  private void writeFile() {
    FileOutputStream outputStream = null;
    try {
      outputStream = atomicFile.startWrite();
      DataOutputStream output = new DataOutputStream(outputStream);

      output.writeInt(VERSION);
      output.writeInt(0); // flags placeholder
      output.writeInt(keyToContent.size());
      int hashCode = 0;
      for (CachedContent cachedContent : keyToContent.values()) {
        cachedContent.writeToStream(output);
        hashCode += cachedContent.headerHashCode();
      }
      output.writeInt(hashCode);

      output.flush();
      atomicFile.finishWrite(outputStream);
    } catch (IOException e) {
      atomicFile.failWrite(outputStream);
      throw new RuntimeException("Writing the new cache index file failed.", e);
    }
  }

  /** Adds the given CachedContent to the index. */
  /*package*/ void addNew(CachedContent cachedContent) {
    keyToContent.put(cachedContent.key, cachedContent);
    idToKey.put(cachedContent.id, cachedContent.key);
    changed = true;
  }

  private CachedContent addNew(String key, long length) {
    int id = getNewId(idToKey);
    CachedContent cachedContent = new CachedContent(id, key, length);
    addNew(cachedContent);
    return cachedContent;
  }

  /**
   * Returns an id which isn't used in the given array. If the maximum id in the array is smaller
   * than {@link java.lang.Integer#MAX_VALUE} it just returns the next bigger integer. Otherwise it
   * returns the smallest unused non-negative integer.
   */
  //@VisibleForTesting
  public static int getNewId(SparseArray<String> idToKey) {
    int size = idToKey.size();
    int id = size == 0 ? 0 : (idToKey.keyAt(size - 1) + 1);
    if (id < 0) { // In case if we pass max int value.
      // TODO optimization: defragmentation or binary search?
      for (id = 0; id < size; id++) {
        if (id != idToKey.keyAt(id)) {
          break;
        }
      }
    }
    return id;
  }

}
