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

import com.google.android.exoplayer.util.Assertions;

import java.util.Arrays;

/**
 * Default implementation of {@link Allocator}.
 */
public final class BufferPool implements Allocator {

  private static final int INITIAL_RECYCLED_BUFFERS_CAPACITY = 100;

  private final int bufferLength;

  private int allocatedCount;
  private int recycledCount;
  private byte[][] recycledBuffers;

  /**
   * Constructs an empty pool.
   *
   * @param bufferLength The length of each buffer in the pool.
   */
  public BufferPool(int bufferLength) {
    Assertions.checkArgument(bufferLength > 0);
    this.bufferLength = bufferLength;
    this.recycledBuffers = new byte[INITIAL_RECYCLED_BUFFERS_CAPACITY][];
  }

  @Override
  public synchronized byte[] allocateBuffer() {
    allocatedCount++;
    return recycledCount > 0 ? recycledBuffers[--recycledCount] : new byte[bufferLength];
  }

  @Override
  public synchronized void releaseBuffer(byte[] buffer) {
    // Weak sanity check that the buffer probably originated from this pool.
    Assertions.checkArgument(buffer.length == bufferLength);
    allocatedCount--;
    if (recycledCount == recycledBuffers.length) {
      recycledBuffers = Arrays.copyOf(recycledBuffers, recycledBuffers.length * 2);
    }
    recycledBuffers[recycledCount++] = buffer;
    // Wake up threads waiting for the allocated size to drop.
    notifyAll();
  }

  @Override
  public synchronized void trim(int targetSize) {
    int targetBufferCount = (targetSize + bufferLength - 1) / bufferLength;
    int targetRecycledBufferCount = Math.max(0, targetBufferCount - allocatedCount);
    if (targetRecycledBufferCount < recycledCount) {
      Arrays.fill(recycledBuffers, targetRecycledBufferCount, recycledCount, null);
      recycledCount = targetRecycledBufferCount;
    }
  }

  @Override
  public synchronized int getAllocatedSize() {
    return allocatedCount * bufferLength;
  }

  @Override
  public int getBufferLength() {
    return bufferLength;
  }

  /**
   * Blocks execution until the allocated size is not greater than the threshold, or the thread is
   * interrupted.
   */
  public synchronized void blockWhileAllocatedSizeExceeds(int limit) throws InterruptedException {
    while (getAllocatedSize() > limit) {
      wait();
    }
  }

}
