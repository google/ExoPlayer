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

import android.os.ConditionVariable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * A {@link Cache} implementation that maintains an in-memory representation. Note, only one
 * instance of SimpleCache is allowed for a given directory at a given time.
 */
public final class SimpleCache implements Cache {

  private static final String TAG = "SimpleCache";
  /**
   * Cache files are distributed between a number of subdirectories. This helps to avoid poor
   * performance in cases where the performance of the underlying file system (e.g. FAT32) scales
   * badly with the number of files per directory. See
   * https://github.com/google/ExoPlayer/issues/4253.
   */
  private static final int SUBDIRECTORY_COUNT = 10;

  private static final String UID_FILE_SUFFIX = ".uid";

  private static final HashSet<File> lockedCacheDirs = new HashSet<>();

  private static boolean cacheFolderLockingDisabled;

  private final File cacheDir;
  private final CacheEvictor evictor;
  private final CachedContentIndex contentIndex;
  @Nullable private final CacheFileMetadataIndex fileIndex;
  private final HashMap<String, ArrayList<Listener>> listeners;
  private final Random random;

  private long totalSpace;
  private boolean released;

  /**
   * Returns whether {@code cacheFolder} is locked by a {@link SimpleCache} instance. To unlock the
   * folder the {@link SimpleCache} instance should be released.
   */
  public static synchronized boolean isCacheFolderLocked(File cacheFolder) {
    return lockedCacheDirs.contains(cacheFolder.getAbsoluteFile());
  }

  /**
   * Disables locking the cache folders which {@link SimpleCache} instances are using and releases
   * any previous lock.
   *
   * <p>The locking prevents multiple {@link SimpleCache} instances from being created for the same
   * folder. Disabling it may cause the cache data to be corrupted. Use at your own risk.
   *
   * @deprecated Don't create multiple {@link SimpleCache} instances for the same cache folder. If
   *     you need to create another instance, make sure you call {@link #release()} on the previous
   *     instance.
   */
  @Deprecated
  public static synchronized void disableCacheFolderLocking() {
    cacheFolderLockingDisabled = true;
    lockedCacheDirs.clear();
  }

  /**
   * Constructs the cache. The cache will delete any unrecognized files from the directory. Hence
   * the directory cannot be used to store other files.
   *
   * @param cacheDir A dedicated cache directory.
   * @param evictor The evictor to be used.
   */
  public SimpleCache(File cacheDir, CacheEvictor evictor) {
    this(cacheDir, evictor, null, false);
  }

  /**
   * Constructs the cache. The cache will delete any unrecognized files from the directory. Hence
   * the directory cannot be used to store other files.
   *
   * @param cacheDir A dedicated cache directory.
   * @param evictor The evictor to be used.
   * @param secretKey If not null, cache keys will be stored encrypted on filesystem using AES/CBC.
   *     The key must be 16 bytes long.
   */
  public SimpleCache(File cacheDir, CacheEvictor evictor, byte[] secretKey) {
    this(cacheDir, evictor, secretKey, secretKey != null);
  }

  /**
   * Constructs the cache. The cache will delete any unrecognized files from the directory. Hence
   * the directory cannot be used to store other files.
   *
   * @param cacheDir A dedicated cache directory.
   * @param evictor The evictor to be used.
   * @param secretKey If not null, cache keys will be stored encrypted on filesystem using AES/CBC.
   *     The key must be 16 bytes long.
   * @param encrypt Whether the index will be encrypted when written. Must be false if {@code
   *     secretKey} is null.
   */
  public SimpleCache(File cacheDir, CacheEvictor evictor, byte[] secretKey, boolean encrypt) {
    this(
        cacheDir,
        evictor,
        new CachedContentIndex(
            /* databaseProvider= */ null,
            cacheDir,
            secretKey,
            encrypt,
            /* preferLegacyStorage= */ true));
  }

  /**
   * Constructs the cache. The cache will delete any unrecognized files from the directory. Hence
   * the directory cannot be used to store other files.
   *
   * @param cacheDir A dedicated cache directory.
   * @param evictor The evictor to be used.
   * @param contentIndex The content index to be used.
   */
  /* package */ SimpleCache(File cacheDir, CacheEvictor evictor, CachedContentIndex contentIndex) {
    if (!lockFolder(cacheDir)) {
      throw new IllegalStateException("Another SimpleCache instance uses the folder: " + cacheDir);
    }

    this.cacheDir = cacheDir;
    this.evictor = evictor;
    this.contentIndex = contentIndex;
    this.fileIndex = null;
    listeners = new HashMap<>();
    random = new Random();

    // Start cache initialization.
    final ConditionVariable conditionVariable = new ConditionVariable();
    new Thread("SimpleCache.initialize()") {
      @Override
      public void run() {
        synchronized (SimpleCache.this) {
          conditionVariable.open();
          initialize();
          SimpleCache.this.evictor.onCacheInitialized();
        }
      }
    }.start();
    conditionVariable.block();
  }

  @Override
  public synchronized void release() {
    if (released) {
      return;
    }
    listeners.clear();
    removeStaleSpans();
    try {
      contentIndex.store();
    } catch (CacheException e) {
      Log.e(TAG, "Storing index file failed", e);
    } finally {
      unlockFolder(cacheDir);
      released = true;
    }
  }

  @Override
  public synchronized NavigableSet<CacheSpan> addListener(String key, Listener listener) {
    Assertions.checkState(!released);
    ArrayList<Listener> listenersForKey = listeners.get(key);
    if (listenersForKey == null) {
      listenersForKey = new ArrayList<>();
      listeners.put(key, listenersForKey);
    }
    listenersForKey.add(listener);
    return getCachedSpans(key);
  }

  @Override
  public synchronized void removeListener(String key, Listener listener) {
    if (released) {
      return;
    }
    ArrayList<Listener> listenersForKey = listeners.get(key);
    if (listenersForKey != null) {
      listenersForKey.remove(listener);
      if (listenersForKey.isEmpty()) {
        listeners.remove(key);
      }
    }
  }

  @NonNull
  @Override
  public synchronized NavigableSet<CacheSpan> getCachedSpans(String key) {
    Assertions.checkState(!released);
    CachedContent cachedContent = contentIndex.get(key);
    return cachedContent == null || cachedContent.isEmpty()
        ? new TreeSet<>()
        : new TreeSet<CacheSpan>(cachedContent.getSpans());
  }

  @Override
  public synchronized Set<String> getKeys() {
    Assertions.checkState(!released);
    return new HashSet<>(contentIndex.getKeys());
  }

  @Override
  public synchronized long getCacheSpace() {
    Assertions.checkState(!released);
    return totalSpace;
  }

  @Override
  public synchronized SimpleCacheSpan startReadWrite(String key, long position)
      throws InterruptedException, CacheException {
    while (true) {
      SimpleCacheSpan span = startReadWriteNonBlocking(key, position);
      if (span != null) {
        return span;
      } else {
        // Write case, lock not available. We'll be woken up when a locked span is released (if the
        // released lock is for the requested key then we'll be able to make progress) or when a
        // span is added to the cache (if the span is for the requested key and covers the requested
        // position, then we'll become a read and be able to make progress).
        wait();
      }
    }
  }

  @Override
  public synchronized @Nullable SimpleCacheSpan startReadWriteNonBlocking(
      String key, long position) {
    Assertions.checkState(!released);
    SimpleCacheSpan span = getSpan(key, position);

    // Read case.
    if (span.isCached) {
      String fileName = span.file.getName();
      long length = span.length;
      long lastAccessTimestamp = System.currentTimeMillis();
      boolean updateFile = false;
      if (fileIndex != null) {
        fileIndex.set(fileName, length, lastAccessTimestamp);
      } else {
        // Updating the file itself to incorporate the new last access timestamp is much slower than
        // updating the file index. Hence we only update the file if we don't have a file index.
        updateFile = true;
      }
      SimpleCacheSpan newSpan =
          contentIndex.get(key).setLastAccessTimestamp(span, lastAccessTimestamp, updateFile);
      notifySpanTouched(span, newSpan);
      return newSpan;
    }

    CachedContent cachedContent = contentIndex.getOrAdd(key);
    if (!cachedContent.isLocked()) {
      // Write case, lock available.
      cachedContent.setLocked(true);
      return span;
    }

    // Write case, lock not available.
    return null;
  }

  @Override
  public synchronized File startFile(String key, long position, long length) {
    Assertions.checkState(!released);
    CachedContent cachedContent = contentIndex.get(key);
    Assertions.checkNotNull(cachedContent);
    Assertions.checkState(cachedContent.isLocked());
    if (!cacheDir.exists()) {
      // For some reason the cache directory doesn't exist. Make a best effort to create it.
      cacheDir.mkdirs();
      removeStaleSpans();
    }
    evictor.onStartFile(this, key, position, length);
    // Randomly distribute files into subdirectories with a uniform distribution.
    File fileDir = new File(cacheDir, Integer.toString(random.nextInt(SUBDIRECTORY_COUNT)));
    if (!fileDir.exists()) {
      fileDir.mkdir();
    }
    long lastAccessTimestamp = System.currentTimeMillis();
    return SimpleCacheSpan.getCacheFile(fileDir, cachedContent.id, position, lastAccessTimestamp);
  }

  @Override
  public synchronized void commitFile(File file, long length) throws CacheException {
    Assertions.checkState(!released);
    if (!file.exists()) {
      return;
    }
    if (length == 0) {
      file.delete();
      return;
    }

    SimpleCacheSpan span = SimpleCacheSpan.createCacheEntry(file, length, contentIndex);
    Assertions.checkState(span != null);
    CachedContent cachedContent = contentIndex.get(span.key);
    Assertions.checkNotNull(cachedContent);
    Assertions.checkState(cachedContent.isLocked());

    // Check if the span conflicts with the set content length
    long contentLength = ContentMetadata.getContentLength(cachedContent.getMetadata());
    if (contentLength != C.LENGTH_UNSET) {
      Assertions.checkState((span.position + span.length) <= contentLength);
    }

    if (fileIndex != null) {
      fileIndex.set(file.getName(), span.length, span.lastAccessTimestamp);
    }
    addSpan(span);
    contentIndex.store();
    notifyAll();
  }

  @Override
  public synchronized void releaseHoleSpan(CacheSpan holeSpan) {
    Assertions.checkState(!released);
    CachedContent cachedContent = contentIndex.get(holeSpan.key);
    Assertions.checkNotNull(cachedContent);
    Assertions.checkState(cachedContent.isLocked());
    cachedContent.setLocked(false);
    contentIndex.maybeRemove(cachedContent.key);
    notifyAll();
  }

  @Override
  public synchronized void removeSpan(CacheSpan span) {
    Assertions.checkState(!released);
    removeSpanInternal(span);
  }

  @Override
  public synchronized boolean isCached(String key, long position, long length) {
    Assertions.checkState(!released);
    CachedContent cachedContent = contentIndex.get(key);
    return cachedContent != null && cachedContent.getCachedBytesLength(position, length) >= length;
  }

  @Override
  public synchronized long getCachedLength(String key, long position, long length) {
    Assertions.checkState(!released);
    CachedContent cachedContent = contentIndex.get(key);
    return cachedContent != null ? cachedContent.getCachedBytesLength(position, length) : -length;
  }

  @Override
  public synchronized void applyContentMetadataMutations(
      String key, ContentMetadataMutations mutations) throws CacheException {
    Assertions.checkState(!released);
    contentIndex.applyContentMetadataMutations(key, mutations);
    contentIndex.store();
  }

  @Override
  public synchronized ContentMetadata getContentMetadata(String key) {
    Assertions.checkState(!released);
    return contentIndex.getContentMetadata(key);
  }

  /**
   * Returns the cache {@link SimpleCacheSpan} corresponding to the provided lookup {@link
   * SimpleCacheSpan}.
   *
   * <p>If the lookup position is contained by an existing entry in the cache, then the returned
   * {@link SimpleCacheSpan} defines the file in which the data is stored. If the lookup position is
   * not contained by an existing entry, then the returned {@link SimpleCacheSpan} defines the
   * maximum extents of the hole in the cache.
   *
   * @param key The key of the span being requested.
   * @param position The position of the span being requested.
   * @return The corresponding cache {@link SimpleCacheSpan}.
   */
  private SimpleCacheSpan getSpan(String key, long position) {
    CachedContent cachedContent = contentIndex.get(key);
    if (cachedContent == null) {
      return SimpleCacheSpan.createOpenHole(key, position);
    }
    while (true) {
      SimpleCacheSpan span = cachedContent.getSpan(position);
      if (span.isCached && !span.file.exists()) {
        // The file has been deleted from under us. It's likely that other files will have been
        // deleted too, so scan the whole in-memory representation.
        removeStaleSpans();
        continue;
      }
      return span;
    }
  }

  /** Ensures that the cache's in-memory representation has been initialized. */
  private void initialize() {
    if (!cacheDir.exists()) {
      cacheDir.mkdirs();
      return;
    }

    File[] files = cacheDir.listFiles();

    long uid = 0;
    try {
      uid = loadUid(cacheDir, files);
    } catch (IOException e) {
      // TODO: Decide how to handle this.
    }

    contentIndex.initialize(uid);
    if (fileIndex != null) {
      fileIndex.initialize(uid);
      Map<String, CacheFileMetadata> fileMetadata = fileIndex.getAll();
      loadDirectory(cacheDir, /* isRoot= */ true, files, fileMetadata);
      fileIndex.removeAll(fileMetadata.keySet());
    } else {
      loadDirectory(cacheDir, /* isRoot= */ true, files, /* fileMetadata= */ null);
    }
    contentIndex.removeEmpty();
    try {
      contentIndex.store();
    } catch (CacheException e) {
      Log.e(TAG, "Storing index file failed", e);
    }
  }

  /**
   * Loads a cache directory. If the root directory is passed, also loads any subdirectories.
   *
   * @param directory The directory.
   * @param isRoot Whether the directory is the root directory.
   * @param files The files belonging to the directory.
   * @param fileMetadata A mutable map containing cache file metadata, keyed by file name. The map
   *     is modified by removing entries for all loaded files. When the method call returns, the map
   *     will contain only metadata that was unused. May be null if no file metadata is available.
   */
  private void loadDirectory(
      File directory,
      boolean isRoot,
      File[] files,
      @Nullable Map<String, CacheFileMetadata> fileMetadata) {
    if (files == null || files.length == 0) {
      // Either (a) directory isn't really a directory (b) it's empty, or (c) listing files failed.
      if (!isRoot) {
        // For (a) and (b) deletion is the desired result. For (c) it will be a no-op if the
        // directory is non-empty, so there's no harm in trying.
        directory.delete();
      }
      return;
    }
    for (File file : files) {
      String fileName = file.getName();
      if (isRoot && fileName.indexOf('.') == -1) {
        loadDirectory(file, /* isRoot= */ false, file.listFiles(), fileMetadata);
      } else {
        if (isRoot
            && (CachedContentIndex.isIndexFile(fileName) || fileName.endsWith(UID_FILE_SUFFIX))) {
          // Skip expected UID and index files in the root directory.
          continue;
        }
        long length = C.LENGTH_UNSET;
        long lastAccessTimestamp = C.TIME_UNSET;
        CacheFileMetadata metadata = fileMetadata != null ? fileMetadata.remove(fileName) : null;
        if (metadata != null) {
          length = metadata.length;
          lastAccessTimestamp = metadata.lastAccessTimestamp;
        }
        SimpleCacheSpan span =
            SimpleCacheSpan.createCacheEntry(file, length, lastAccessTimestamp, contentIndex);
        if (span != null) {
          addSpan(span);
        } else {
          file.delete();
        }
      }
    }
  }

  /**
   * Adds a cached span to the in-memory representation.
   *
   * @param span The span to be added.
   */
  private void addSpan(SimpleCacheSpan span) {
    contentIndex.getOrAdd(span.key).addSpan(span);
    totalSpace += span.length;
    notifySpanAdded(span);
  }

  private void removeSpanInternal(CacheSpan span) {
    CachedContent cachedContent = contentIndex.get(span.key);
    if (cachedContent == null || !cachedContent.removeSpan(span)) {
      return;
    }
    totalSpace -= span.length;
    if (fileIndex != null) {
      fileIndex.remove(span.file.getName());
    }
    contentIndex.maybeRemove(cachedContent.key);
    notifySpanRemoved(span);
  }

  /**
   * Scans all of the cached spans in the in-memory representation, removing any for which files no
   * longer exist.
   */
  private void removeStaleSpans() {
    ArrayList<CacheSpan> spansToBeRemoved = new ArrayList<>();
    for (CachedContent cachedContent : contentIndex.getAll()) {
      for (CacheSpan span : cachedContent.getSpans()) {
        if (!span.file.exists()) {
          spansToBeRemoved.add(span);
        }
      }
    }
    for (int i = 0; i < spansToBeRemoved.size(); i++) {
      removeSpanInternal(spansToBeRemoved.get(i));
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

  private void notifySpanAdded(SimpleCacheSpan span) {
    ArrayList<Listener> keyListeners = listeners.get(span.key);
    if (keyListeners != null) {
      for (int i = keyListeners.size() - 1; i >= 0; i--) {
        keyListeners.get(i).onSpanAdded(this, span);
      }
    }
    evictor.onSpanAdded(this, span);
  }

  private void notifySpanTouched(SimpleCacheSpan oldSpan, CacheSpan newSpan) {
    ArrayList<Listener> keyListeners = listeners.get(oldSpan.key);
    if (keyListeners != null) {
      for (int i = keyListeners.size() - 1; i >= 0; i--) {
        keyListeners.get(i).onSpanTouched(this, oldSpan, newSpan);
      }
    }
    evictor.onSpanTouched(this, oldSpan, newSpan);
  }

  /**
   * Loads the cache UID from the files belonging to the root directory, generating one if needed.
   *
   * @param directory The root directory.
   * @param files The files belonging to the root directory.
   * @return The cache loaded UID.
   * @throws IOException If there is an error loading or generating the UID.
   */
  private static long loadUid(File directory, File[] files) throws IOException {
    if (files != null) {
      for (File file : files) {
        String fileName = file.getName();
        if (fileName.endsWith(UID_FILE_SUFFIX)) {
          try {
            return parseUid(fileName);
          } catch (NumberFormatException e) {
            // This should never happen, but if it does delete the malformed UID file and continue.
            Log.e(TAG, "Malformed UID file: " + file);
            file.delete();
          }
        }
      }
    }
    return createUid(directory);
  }

  @SuppressWarnings("TrulyRandom")
  private static long createUid(File directory) throws IOException {
    // Generate a non-negative UID.
    long uid = new SecureRandom().nextLong();
    uid = uid == Long.MIN_VALUE ? 0 : Math.abs(uid);
    // Persist it as a file.
    String hexUid = Long.toString(uid, /* radix= */ 16);
    File hexUidFile = new File(directory, hexUid + UID_FILE_SUFFIX);
    if (!hexUidFile.createNewFile()) {
      // False means that the file already exists, so this should never happen.
      throw new IOException("Failed to create UID file: " + hexUidFile);
    }
    return uid;
  }

  private static long parseUid(String fileName) {
    return Long.parseLong(fileName.substring(0, fileName.indexOf('.')), /* radix= */ 16);
  }

  private static synchronized boolean lockFolder(File cacheDir) {
    if (cacheFolderLockingDisabled) {
      return true;
    }
    return lockedCacheDirs.add(cacheDir.getAbsoluteFile());
  }

  private static synchronized void unlockFolder(File cacheDir) {
    if (!cacheFolderLockingDisabled) {
      lockedCacheDirs.remove(cacheDir.getAbsoluteFile());
    }
  }
}
