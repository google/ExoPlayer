/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.upstream.cache;

import com.google.android.exoplayer.util.Assertions;

import android.os.ConditionVariable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A {@link Cache} implementation that maintains an in-memory representation.
 */
public class SimpleCache implements Cache {

  private final File cacheDir;
  private final CacheEvictor evictor;
  private final HashMap<String, CacheSpan> lockedSpans;
  private final HashMap<String, TreeSet<CacheSpan>> cachedSpans;
  private final HashMap<String, ArrayList<Listener>> listeners;
  private long totalSpace = 0;

  /**
   * Constructs the cache. The cache will delete any unrecognized files from the directory. Hence
   * the directory cannot be used to store other files.
   *
   * @param cacheDir A dedicated cache directory.
   */
  public SimpleCache(File cacheDir, CacheEvictor evictor) {
    this.cacheDir = cacheDir;
    this.evictor = evictor;
    this.lockedSpans = new HashMap<String, CacheSpan>();
    this.cachedSpans = new HashMap<String, TreeSet<CacheSpan>>();
    this.listeners = new HashMap<String, ArrayList<Listener>>();
    // Start cache initialization.
    final ConditionVariable conditionVariable = new ConditionVariable();
    new Thread() {
      @Override
      public void run() {
        synchronized (SimpleCache.this) {
          conditionVariable.open();
          initialize();
        }
      }
    }.start();
    conditionVariable.block();
  }

  @Override
  public synchronized NavigableSet<CacheSpan> addListener(String key, Listener listener) {
    ArrayList<Listener> listenersForKey = listeners.get(key);
    if (listenersForKey == null) {
      listenersForKey = new ArrayList<Listener>();
      listeners.put(key, listenersForKey);
    }
    listenersForKey.add(listener);
    return getCachedSpans(key);
  }

  @Override
  public synchronized void removeListener(String key, Listener listener) {
    ArrayList<Listener> listenersForKey = listeners.get(key);
    if (listenersForKey != null) {
      listenersForKey.remove(listener);
      if (listenersForKey.isEmpty()) {
        listeners.remove(key);
      }
    }
  }

  @Override
  public synchronized NavigableSet<CacheSpan> getCachedSpans(String key) {
    TreeSet<CacheSpan> spansForKey = cachedSpans.get(key);
    return spansForKey == null ? null : new TreeSet<CacheSpan>(spansForKey);
  }

  @Override
  public synchronized Set<String> getKeys() {
    return new HashSet<String>(cachedSpans.keySet());
  }

  @Override
  public synchronized long getCacheSpace() {
    return totalSpace;
  }

  @Override
  public synchronized CacheSpan startReadWrite(String key, long position)
      throws InterruptedException {
    CacheSpan lookupSpan = CacheSpan.createLookup(key, position);
    // Wait until no-one holds a lock for the key.
    while (lockedSpans.containsKey(key)) {
      wait();
    }
    return getSpanningRegion(key, lookupSpan);
  }

  @Override
  public synchronized CacheSpan startReadWriteNonBlocking(String key, long position)
      throws InterruptedException {
    CacheSpan lookupSpan = CacheSpan.createLookup(key, position);
    // Return null if key is locked
    if (lockedSpans.containsKey(key)) {
      return null;
    }
    return getSpanningRegion(key, lookupSpan);
  }

  private CacheSpan getSpanningRegion(String key, CacheSpan lookupSpan) {
    CacheSpan spanningRegion = getSpan(lookupSpan);
    if (spanningRegion.isCached) {
      CacheSpan oldCacheSpan = spanningRegion;
      // Remove the old span from the in-memory representation.
      TreeSet<CacheSpan> spansForKey = cachedSpans.get(oldCacheSpan.key);
      Assertions.checkState(spansForKey.remove(oldCacheSpan));
      // Obtain a new span with updated last access timestamp.
      spanningRegion = oldCacheSpan.touch();
      // Add the updated span back into the in-memory representation.
      spansForKey.add(spanningRegion);
      notifySpanTouched(oldCacheSpan, spanningRegion);
    } else {
      lockedSpans.put(key, spanningRegion);
    }
    return spanningRegion;
  }

  @Override
  public synchronized File startFile(String key, long position, long length) {
    Assertions.checkState(lockedSpans.containsKey(key));
    if (!cacheDir.exists()) {
      // For some reason the cache directory doesn't exist. Make a best effort to create it.
      removeStaleSpans();
      cacheDir.mkdirs();
    }
    evictor.onStartFile(this, key, position, length);
    return CacheSpan.getCacheFileName(cacheDir, key, position, System.currentTimeMillis());
  }

  @Override
  public synchronized void commitFile(File file) {
    CacheSpan span = CacheSpan.createCacheEntry(file);
    Assertions.checkState(span != null);
    Assertions.checkState(lockedSpans.containsKey(span.key));
    // If the file doesn't exist, don't add it to the in-memory representation.
    if (!file.exists()) {
      return;
    }
    // If the file has length 0, delete it and don't add it to the in-memory representation.
    long length = file.length();
    if (length == 0) {
      file.delete();
      return;
    }
    addSpan(span);
  }

  @Override
  public synchronized void releaseHoleSpan(CacheSpan holeSpan) {
    Assertions.checkState(holeSpan == lockedSpans.remove(holeSpan.key));
    notifyAll();
  }

  /**
   * Returns the cache {@link CacheSpan} corresponding to the provided lookup {@link CacheSpan}.
   * <p>
   * If the lookup position is contained by an existing entry in the cache, then the returned
   * {@link CacheSpan} defines the file in which the data is stored. If the lookup position is not
   * contained by an existing entry, then the returned {@link CacheSpan} defines the maximum extents
   * of the hole in the cache.
   *
   * @param lookupSpan A lookup {@link CacheSpan} specifying a key and position.
   * @return The corresponding cache {@link CacheSpan}.
   */
  private CacheSpan getSpan(CacheSpan lookupSpan) {
    String key = lookupSpan.key;
    long offset = lookupSpan.position;
    TreeSet<CacheSpan> entries = cachedSpans.get(key);
    if (entries == null) {
      return CacheSpan.createOpenHole(key, lookupSpan.position);
    }
    CacheSpan floorSpan = entries.floor(lookupSpan);
    if (floorSpan != null &&
        floorSpan.position <= offset && offset < floorSpan.position + floorSpan.length) {
      // The lookup position is contained within floorSpan.
      if (floorSpan.file.exists()) {
        return floorSpan;
      } else {
        // The file has been deleted from under us. It's likely that other files will have been
        // deleted too, so scan the whole in-memory representation.
        removeStaleSpans();
        return getSpan(lookupSpan);
      }
    }
    CacheSpan ceilEntry = entries.ceiling(lookupSpan);
    return ceilEntry == null ? CacheSpan.createOpenHole(key, lookupSpan.position) :
        CacheSpan.createClosedHole(key, lookupSpan.position,
            ceilEntry.position - lookupSpan.position);
  }

  /**
   * Ensures that the cache's in-memory representation has been initialized.
   */
  private void initialize() {
    if (!cacheDir.exists()) {
      cacheDir.mkdirs();
    }
    File[] files = cacheDir.listFiles();
    if (files == null) {
      return;
    }
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      if (file.length() == 0) {
        file.delete();
      } else {
        CacheSpan span = CacheSpan.createCacheEntry(file);
        if (span == null) {
          file.delete();
        } else {
          addSpan(span);
        }
      }
    }
  }

  /**
   * Adds a cached span to the in-memory representation.
   *
   * @param span The span to be added.
   */
  private void addSpan(CacheSpan span) {
    TreeSet<CacheSpan> spansForKey = cachedSpans.get(span.key);
    if (spansForKey == null) {
      spansForKey = new TreeSet<CacheSpan>();
      cachedSpans.put(span.key, spansForKey);
    }
    spansForKey.add(span);
    totalSpace += span.length;
    notifySpanAdded(span);
  }

  @Override
  public synchronized void removeSpan(CacheSpan span) {
    TreeSet<CacheSpan> spansForKey = cachedSpans.get(span.key);
    totalSpace -= span.length;
    Assertions.checkState(spansForKey.remove(span));
    span.file.delete();
    if (spansForKey.isEmpty()) {
      cachedSpans.remove(span.key);
    }
    notifySpanRemoved(span);
  }

  /**
   * Scans all of the cached spans in the in-memory representation, removing any for which files
   * no longer exist.
   */
  private void removeStaleSpans() {
    Iterator<Entry<String, TreeSet<CacheSpan>>> iterator = cachedSpans.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<String, TreeSet<CacheSpan>> next = iterator.next();
      Iterator<CacheSpan> spanIterator = next.getValue().iterator();
      boolean isEmpty = true;
      while (spanIterator.hasNext()) {
        CacheSpan span = spanIterator.next();
        if (!span.file.exists()) {
          spanIterator.remove();
          if (span.isCached) {
            totalSpace -= span.length;
          }
          notifySpanRemoved(span);
        } else {
          isEmpty = false;
        }
      }
      if (isEmpty) {
        iterator.remove();
      }
    }
  }

  private void notifySpanRemoved(CacheSpan span) {
    ArrayList<Listener> keyListeners = listeners.get(span.key);
    if (keyListeners != null) {
      for (int i = keyListeners.size() - 1; i >= 0; i--) {
        keyListeners.get(i).onSpanRemoved(this, span);
      }
    }
    evictor.onSpanRemoved(this, span);
  }

  private void notifySpanAdded(CacheSpan span) {
    ArrayList<Listener> keyListeners = listeners.get(span.key);
    if (keyListeners != null) {
      for (int i = keyListeners.size() - 1; i >= 0; i--) {
        keyListeners.get(i).onSpanAdded(this, span);
      }
    }
    evictor.onSpanAdded(this, span);
  }

  private void notifySpanTouched(CacheSpan oldSpan, CacheSpan newSpan) {
    ArrayList<Listener> keyListeners = listeners.get(oldSpan.key);
    if (keyListeners != null) {
      for (int i = keyListeners.size() - 1; i >= 0; i--) {
        keyListeners.get(i).onSpanTouched(this, oldSpan, newSpan);
      }
    }
    evictor.onSpanTouched(this, oldSpan, newSpan);
  }

}
