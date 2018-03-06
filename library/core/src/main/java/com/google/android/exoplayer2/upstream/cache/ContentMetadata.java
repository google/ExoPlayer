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

import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;

/** Interface for accessing cached content metadata which is stored as name, value pairs. */
public interface ContentMetadata {

  /**
   * Interface for modifying values in a {@link ContentMetadata} object. The changes you make in an
   * editor are not copied back to the original {@link ContentMetadata} until you call {@link
   * #commit()}.
   */
  interface Editor {
    /**
     * Sets a metadata value, to be committed once {@link #commit()} is called. Passing {@code null}
     * as {@code value} isn't allowed. {@code value} byte array shouldn't be modified after passed
     * to this method.
     *
     * @param name The name of the metadata value.
     * @param value The value to be set.
     * @return This Editor instance, for convenience.
     */
    Editor set(String name, byte[] value);
    /**
     * Sets a metadata value, to be committed once {@link #commit()} is called. Passing {@code null}
     * as value isn't allowed.
     *
     * @param name The name of the metadata value.
     * @param value The value to be set.
     * @return This Editor instance, for convenience.
     */
    Editor set(String name, String value);
    /**
     * Sets a metadata value, to be committed once {@link #commit()} is called.
     *
     * @param name The name of the metadata value.
     * @param value The value to be set.
     * @return This Editor instance, for convenience.
     */
    Editor set(String name, long value);
    /**
     * Sets a metadata value, to be committed once {@link #commit()} is called. Passing {@code null}
     * as value isn't allowed.
     *
     * @param name The name of the metadata value.
     * @return This Editor instance, for convenience.
     */
    Editor remove(String name);
    /**
     * Commits changes. It can be called only once.
     *
     * @throws CacheException If the commit fails.
     */
    void commit() throws CacheException;
  }

  /** Returns an editor to change metadata values. */
  Editor edit();

  /**
   * Returns a metadata value.
   *
   * @param name Name of the metadata to be returned.
   * @param defaultValue Value to return if the metadata doesn't exist.
   * @return The metadata value.
   */
  byte[] get(String name, byte[] defaultValue);

  /**
   * Returns a metadata value.
   *
   * @param name Name of the metadata to be returned.
   * @param defaultValue Value to return if the metadata doesn't exist.
   * @return The metadata value.
   */
  String get(String name, String defaultValue);

  /**
   * Returns a metadata value.
   *
   * @param name Name of the metadata to be returned.
   * @param defaultValue Value to return if the metadata doesn't exist.
   * @return The metadata value.
   */
  long get(String name, long defaultValue);

  /** Returns whether the metadata is available. */
  boolean contains(String name);
}
