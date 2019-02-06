/*
 * Copyright (C) 2018 The Android Open Source Project
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Maintains an index of cache file metadata. */
/* package */ class CacheFileMetadataIndex {

  /**
   * Returns all file metadata keyed by file name. The returned map is mutable and may be modified
   * by the caller.
   */
  public Map<String, CacheFileMetadata> getAll() {
    return Collections.emptyMap();
  }

  /**
   * Sets metadata for a given file.
   *
   * @param name The name of the file.
   * @param length The file length.
   * @param lastAccessTimestamp The file last access timestamp.
   * @return Whether the index was updated successfully.
   */
  public boolean set(String name, long length, long lastAccessTimestamp) {
    // TODO.
    return false;
  }

  /**
   * Removes metadata.
   *
   * @param name The name of the file whose metadata is to be removed.
   */
  public void remove(String name) {
    // TODO.
  }

  /**
   * Removes metadata.
   *
   * @param names The names of the files whose metadata is to be removed.
   */
  public void removeAll(Set<String> names) {
    // TODO.
  }
}
