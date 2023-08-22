/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.annotation.VisibleForTesting;
import java.util.NoSuchElementException;

/**
 * Array-based unbounded queue for long primitives with amortized O(1) add and remove.
 *
 * <p>Use this class instead of a {@link java.util.Deque} to avoid boxing long primitives to {@link
 * Long} instances.
 */
@UnstableApi
public final class LongArrayQueue {

  /** Default initial capacity. */
  public static final int DEFAULT_INITIAL_CAPACITY = 16;

  private int headIndex;
  private int tailIndex;
  private int size;
  private long[] data;
  private int wrapAroundMask;

  /** Creates a queue with an initial capacity of {@link #DEFAULT_INITIAL_CAPACITY}. */
  public LongArrayQueue() {
    this(DEFAULT_INITIAL_CAPACITY);
  }

  /**
   * Creates a queue with capacity for at least {@code minCapacity}
   *
   * @param minCapacity minCapacity the minimum capacity, between 1 and 2^30 inclusive
   */
  public LongArrayQueue(int minCapacity) {
    checkArgument(minCapacity >= 0 && minCapacity <= (1 << 30));
    minCapacity = minCapacity == 0 ? 1 : minCapacity;
    // If capacity isn't a power of 2, round up to the next highest power of 2.
    if (Integer.bitCount(minCapacity) != 1) {
      minCapacity = Integer.highestOneBit(minCapacity - 1) << 1;
    }
    headIndex = 0;
    tailIndex = -1;
    size = 0;
    data = new long[minCapacity];
    wrapAroundMask = data.length - 1;
  }

  /** Add a new item to the queue. */
  public void add(long value) {
    if (size == data.length) {
      doubleArraySize();
    }

    tailIndex = (tailIndex + 1) & wrapAroundMask;
    data[tailIndex] = value;
    size++;
  }

  /**
   * Remove an item from the queue.
   *
   * @throws NoSuchElementException if the queue is empty.
   */
  public long remove() {
    if (size == 0) {
      throw new NoSuchElementException();
    }

    long value = data[headIndex];
    headIndex = (headIndex + 1) & wrapAroundMask;
    size--;

    return value;
  }

  /**
   * Retrieves, but does not remove, the head of the queue.
   *
   * @throws NoSuchElementException if the queue is empty.
   */
  public long element() {
    if (size == 0) {
      throw new NoSuchElementException();
    }

    return data[headIndex];
  }

  /** Returns the number of items in the queue. */
  public int size() {
    return size;
  }

  /** Returns whether the queue is empty. */
  public boolean isEmpty() {
    return size == 0;
  }

  /** Clears the queue. */
  public void clear() {
    headIndex = 0;
    tailIndex = -1;
    size = 0;
  }

  /** Returns the length of the backing array. */
  @VisibleForTesting
  /* package */ int capacity() {
    return data.length;
  }

  private void doubleArraySize() {
    int newCapacity = data.length << 1;
    if (newCapacity < 0) {
      throw new IllegalStateException();
    }

    long[] newData = new long[newCapacity];
    int itemsToRight = data.length - headIndex;
    int itemsToLeft = headIndex;
    System.arraycopy(data, headIndex, newData, 0, itemsToRight);
    System.arraycopy(data, 0, newData, itemsToRight, itemsToLeft);

    headIndex = 0;
    tailIndex = size - 1;
    data = newData;
    wrapAroundMask = data.length - 1;
  }
}
