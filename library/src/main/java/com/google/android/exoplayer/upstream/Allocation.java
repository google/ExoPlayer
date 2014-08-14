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
 * An {@link Allocation}, defined to consist of a set of fragments of underlying byte arrays.
 * <p>
 * The byte arrays in which the fragments are located are obtained by {@link #getBuffers}. For
 * each, the offset and length of the fragment within the byte array are obtained using
 * {@link #getFragmentOffset} and {@link #getFragmentLength} respectively.
 */
public interface Allocation {

  /**
   * Ensures the allocation has a capacity greater than or equal to the specified size in bytes.
   * <p>
   * If {@code size} is greater than the current capacity of the allocation, then it will grow
   * to have a capacity of at least {@code size}. The allocation is grown by adding new fragments.
   * Existing fragments remain unchanged, and any data that has been written to them will be
   * preserved.
   * <p>
   * If {@code size} is less than or equal to the capacity of the allocation, then the call is a
   * no-op.
   *
   * @param size The minimum required capacity, in bytes.
   */
  public void ensureCapacity(int size);

  /**
   * Gets the capacity of the allocation, in bytes.
   *
   * @return The capacity of the allocation, in bytes.
   */
  public int capacity();

  /**
   * Gets the buffers in which the fragments are allocated.
   *
   * @return The buffers in which the fragments are allocated.
   */
  public byte[][] getBuffers();

  /**
   * The offset of the fragment in the buffer at the specified index.
   *
   * @param index The index of the buffer.
   * @return The offset of the fragment in the buffer.
   */
  public int getFragmentOffset(int index);

  /**
   * The length of the fragment in the buffer at the specified index.
   *
   * @param index The index of the buffer.
   * @return The length of the fragment in the buffer.
   */
  public int getFragmentLength(int index);

  /**
   * Releases the allocation.
   */
  public void release();

}
