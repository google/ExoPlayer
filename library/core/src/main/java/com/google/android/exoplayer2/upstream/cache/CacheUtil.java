/*
 * Copyright (C) 2017 The Android Open Source Project                     
 *                                                                       
 * Licensed under the Apache License, Version 2.0 (the "License");        
 * you may not use this file except in compliance with the License.       
 * You may obtain a copy of the License at                                
 *                                                                       
 *     http://www.apache.org/licenses/LICENSE-2.0                        
 *                                                                       
 * Unless required by applicable law or agreed to in writing, software    
 * distributed under the License is distributed on an "AS IS" BASIS,      
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and    
 * limitations under the License.
 */
package com.google.android.exoplayer2.upstream.cache;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.util.NavigableSet;

/**
 * Caching related utility methods.
 */
@SuppressWarnings({"NonAtomicVolatileUpdate", "NonAtomicOperationOnVolatileField"})
public final class CacheUtil {

  /** Holds the counters used during caching. */
  public static class CachingCounters {
    /** Total number of already cached bytes. */
    public volatile long alreadyCachedBytes;
    /** Total number of downloaded bytes. */
    public volatile long downloadedBytes;
    /**
     * Total number of bytes. This is the sum of already cached, downloaded and missing bytes. If
     * the length of the missing bytes is unknown this is set to {@link C#LENGTH_UNSET}.
     */
    public volatile long totalBytes = C.LENGTH_UNSET;
  }

  /** Default buffer size to be used while caching. */
  public static final int DEFAULT_BUFFER_SIZE_BYTES = 128 * 1024;

  /**
   * Generates a cache key out of the given {@link Uri}.
   *
   * @param uri Uri of a content which the requested key is for.
   */
  public static String generateKey(Uri uri) {
    return uri.toString();
  }

  /**
   * Returns the {@code dataSpec.key} if not null, otherwise generates a cache key out of {@code
   * dataSpec.uri}
   *
   * @param dataSpec Defines a content which the requested key is for.
   */
  public static String getKey(DataSpec dataSpec) {
    return dataSpec.key != null ? dataSpec.key : generateKey(dataSpec.uri);
  }

  /**
   * Returns already cached and missing bytes in the {@code cache} for the data defined by {@code
   * dataSpec}.
   *
   * @param dataSpec Defines the data to be checked.
   * @param cache A {@link Cache} which has the data.
   * @param counters The counters to be set. If null a new {@link CachingCounters} is created and
   *     used.
   * @return The used {@link CachingCounters} instance.
   */
  public static CachingCounters getCached(DataSpec dataSpec, Cache cache,
      CachingCounters counters) {
    try {
      return internalCache(dataSpec, cache, null, null, null, 0, counters, false);
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Caches the data defined by {@code dataSpec} while skipping already cached data. Caching stops
   * early if end of input is reached.
   *
   * @param dataSpec Defines the data to be cached.
   * @param cache A {@link Cache} to store the data.
   * @param upstream A {@link DataSource} for reading data not in the cache.
   * @param counters The counters to be set during caching. If not null its values reset to
   *     zero before using. If null a new {@link CachingCounters} is created and used.
   * @return The used {@link CachingCounters} instance.
   * @throws IOException If an error occurs reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  public static CachingCounters cache(DataSpec dataSpec, Cache cache,
      DataSource upstream, CachingCounters counters) throws IOException, InterruptedException {
    return cache(dataSpec, cache, new CacheDataSource(cache, upstream),
        new byte[DEFAULT_BUFFER_SIZE_BYTES], null, 0, counters, false);
  }

  /**
   * Caches the data defined by {@code dataSpec} while skipping already cached data. Caching stops
   * early if end of input is reached and {@code enableEOFException} is false.
   *
   * @param dataSpec Defines the data to be cached.
   * @param cache A {@link Cache} to store the data.
   * @param dataSource A {@link CacheDataSource} that works on the {@code cache}.
   * @param buffer The buffer to be used while caching.
   * @param priorityTaskManager If not null it's used to check whether it is allowed to proceed with
   *     caching.
   * @param priority The priority of this task. Used with {@code priorityTaskManager}.
   * @param counters The counters to be set during caching. If not null its values reset to
   *     zero before using. If null a new {@link CachingCounters} is created and used.
   * @param enableEOFException Whether to throw an {@link EOFException} if end of input has been
   *     reached unexpectedly.
   * @return The used {@link CachingCounters} instance.
   * @throws IOException If an error occurs reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  public static CachingCounters cache(DataSpec dataSpec, Cache cache, CacheDataSource dataSource,
      byte[] buffer, PriorityTaskManager priorityTaskManager, int priority,
      CachingCounters counters, boolean enableEOFException)
      throws IOException, InterruptedException {
    Assertions.checkNotNull(dataSource);
    Assertions.checkNotNull(buffer);
    return internalCache(dataSpec, cache, dataSource, buffer, priorityTaskManager, priority,
        counters, enableEOFException);
  }

  /**
   * Caches the data defined by {@code dataSpec} while skipping already cached data. If {@code
   * dataSource} or {@code buffer} is null performs a dry run.
   *
   * @param dataSpec Defines the data to be cached.
   * @param cache A {@link Cache} to store the data.
   * @param dataSource A {@link CacheDataSource} that works on the {@code cache}. If null a dry run
   *     is performed.
   * @param buffer The buffer to be used while caching. If null a dry run is performed.
   * @param priorityTaskManager If not null it's used to check whether it is allowed to proceed with
   *     caching.
   * @param priority The priority of this task. Used with {@code priorityTaskManager}.
   * @param counters The counters to be set during caching. If not null its values reset to
   *     zero before using. If null a new {@link CachingCounters} is created and used.
   * @param enableEOFException Whether to throw an {@link EOFException} if end of input has been
   *     reached unexpectedly.
   * @return The used {@link CachingCounters} instance.
   * @throws IOException If not dry run and an error occurs reading from the source.
   * @throws InterruptedException If not dry run and the thread was interrupted.
   */
  private static CachingCounters internalCache(DataSpec dataSpec, Cache cache,
      CacheDataSource dataSource, byte[] buffer, PriorityTaskManager priorityTaskManager,
      int priority, CachingCounters counters, boolean enableEOFException)
      throws IOException, InterruptedException {
    long start = dataSpec.absoluteStreamPosition;
    long left = dataSpec.length;
    String key = getKey(dataSpec);
    if (left == C.LENGTH_UNSET) {
      left = cache.getContentLength(key);
    }
    if (counters == null) {
      counters = new CachingCounters();
    } else {
      counters.alreadyCachedBytes = 0;
      counters.downloadedBytes = 0;
    }
    counters.totalBytes = left;

    while (left != 0) {
      long blockLength = cache.getCachedBytes(key, start,
          left != C.LENGTH_UNSET ? left : Long.MAX_VALUE);
      // Skip already cached data
      if (blockLength > 0) {
        counters.alreadyCachedBytes += blockLength;
      } else {
        // There is a hole in the cache which is at least "-blockLength" long.
        blockLength = -blockLength;
        if (dataSource != null && buffer != null) {
          long read = readAndDiscard(dataSpec, start, blockLength, dataSource, buffer,
              priorityTaskManager, priority, counters);
          if (read < blockLength) {
            // Reached to the end of the data.
            if (enableEOFException && left != C.LENGTH_UNSET) {
              throw new EOFException();
            }
            break;
          }
        } else if (blockLength == Long.MAX_VALUE) {
          break;
        }
      }
      start += blockLength;
      if (left != C.LENGTH_UNSET) {
        left -= blockLength;
      }
    }
    return counters;
  }

  /**
   * Reads and discards all data specified by the {@code dataSpec}.
   *
   * @param dataSpec Defines the data to be read. {@code absoluteStreamPosition} and {@code length}
   *     fields are overwritten by the following parameters.
   * @param absoluteStreamPosition The absolute position of the data to be read.
   * @param length Length of the data to be read, or {@link C#LENGTH_UNSET} if it is unknown.
   * @param dataSource The {@link DataSource} to read the data from.
   * @param buffer The buffer to be used while downloading.
   * @param priorityTaskManager If not null it's used to check whether it is allowed to proceed with
   *     caching.
   * @param priority The priority of this task.
   * @param counters The counters to be set during reading.
   * @return Number of read bytes, or 0 if no data is available because the end of the opened range
   *     has been reached.
   */
  private static long readAndDiscard(DataSpec dataSpec, long absoluteStreamPosition, long length,
      DataSource dataSource, byte[] buffer, PriorityTaskManager priorityTaskManager, int priority,
      CachingCounters counters) throws IOException, InterruptedException {
    while (true) {
      if (priorityTaskManager != null) {
        // Wait for any other thread with higher priority to finish its job.
        priorityTaskManager.proceed(priority);
      }
      try {
        // Create a new dataSpec setting length to C.LENGTH_UNSET to prevent getting an error in
        // case the given length exceeds the end of input.
        dataSpec = new DataSpec(dataSpec.uri, dataSpec.postBody, absoluteStreamPosition,
            dataSpec.position + absoluteStreamPosition - dataSpec.absoluteStreamPosition,
            C.LENGTH_UNSET, dataSpec.key,
            dataSpec.flags | DataSpec.FLAG_ALLOW_CACHING_UNKNOWN_LENGTH);
        long resolvedLength = dataSource.open(dataSpec);
        if (counters.totalBytes == C.LENGTH_UNSET && resolvedLength != C.LENGTH_UNSET) {
          counters.totalBytes = dataSpec.absoluteStreamPosition + resolvedLength;
        }
        long totalRead = 0;
        while (totalRead != length) {
          if (Thread.interrupted()) {
            throw new InterruptedException();
          }
          int read = dataSource.read(buffer, 0,
              length != C.LENGTH_UNSET ? (int) Math.min(buffer.length, length - totalRead)
                  : buffer.length);
          if (read == C.RESULT_END_OF_INPUT) {
            if (counters.totalBytes == C.LENGTH_UNSET) {
              counters.totalBytes = dataSpec.absoluteStreamPosition + totalRead;
            }
            break;
          }
          totalRead += read;
          counters.downloadedBytes += read;
        }
        return totalRead;
      } catch (PriorityTaskManager.PriorityTooLowException exception) {
        // catch and try again
      } finally {
        Util.closeQuietly(dataSource);
      }
    }
  }

  /** Removes all of the data in the {@code cache} pointed by the {@code key}. */
  public static void remove(Cache cache, String key) {
    NavigableSet<CacheSpan> cachedSpans = cache.getCachedSpans(key);
    if (cachedSpans == null) {
      return;
    }
    for (CacheSpan cachedSpan : cachedSpans) {
      try {
        cache.removeSpan(cachedSpan);
      } catch (Cache.CacheException e) {
        // do nothing
      }
    }
  }

  private CacheUtil() {}

}
