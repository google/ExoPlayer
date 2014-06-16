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
package com.google.android.exoplayer.upstream;

/**
 * A source of {@link Allocation}s.
 */
public interface Allocator {

  /**
   * Obtains an allocation of at least the specified size.
   *
   * @param size The size of the required allocation, in bytes.
   * @return The allocation.
   */
  public Allocation allocate(int size);

  /**
   * Hints to the {@link Allocator} that it should make a best effort to release any memory that it
   * has allocated for the purpose of backing {@link Allocation}s, beyond the specified target
   * number of bytes.
   *
   * @param targetSize The target size in bytes.
   */
  public void trim(int targetSize);

  /**
   * Returns the number of bytes currently allocated in the form of {@link Allocation}s.
   *
   * @return The number of allocated bytes.
   */
  public int getAllocatedSize();

}
