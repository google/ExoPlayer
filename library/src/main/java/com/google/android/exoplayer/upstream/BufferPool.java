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
 * An {@link Allocator} that maintains a pool of fixed length byte arrays (buffers).
 * <p>
 * An {@link Allocation} obtained from a {@link BufferPool} consists of the whole number of these
 * buffers. When an {@link Allocation} is released, the underlying buffers are returned to the pool
 * for re-use.
 */
public final class BufferPool implements Allocator {

  private static final int INITIAL_RECYCLED_BUFFERS_CAPACITY = 100;

  /**
   * The length in bytes of each individual buffer in the pool.
   */
  public final int bufferLength;

  private int allocatedBufferCount;
  private int recycledBufferCount;
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
  public synchronized int getAllocatedSize() {
    return allocatedBufferCount * bufferLength;
  }

  @Override
  public synchronized void trim(int targetSize) {
    int targetBufferCount = (targetSize + bufferLength - 1) / bufferLength;
    int targetRecycledBufferCount = Math.max(0, targetBufferCount - allocatedBufferCount);
    if (targetRecycledBufferCount < recycledBufferCount) {
      Arrays.fill(recycledBuffers, targetRecycledBufferCount, recycledBufferCount, null);
      recycledBufferCount = targetRecycledBufferCount;
    }
  }

  @Override
  public synchronized Allocation allocate(int size) {
    int requiredBufferCount = requiredBufferCount(size);
    allocatedBufferCount += requiredBufferCount;
    byte[][] buffers = new byte[requiredBufferCount][];
    for (int i = 0; i < requiredBufferCount; i++) {
      // Use a recycled buffer if one is available. Else instantiate a new one.
      buffers[i] = recycledBufferCount > 0 ? recycledBuffers[--recycledBufferCount] :
          new byte[bufferLength];
    }
    return new AllocationImpl(buffers);
  }

  /**
   * Returns the buffers belonging to an allocation to the pool.
   *
   * @param allocation The allocation to return.
   */
  /* package */ synchronized void release(AllocationImpl allocation) {
    byte[][] buffers = allocation.getBuffers();
    allocatedBufferCount -= buffers.length;

    int newRecycledBufferCount = recycledBufferCount + buffers.length;
    if (recycledBuffers.length < newRecycledBufferCount) {
      // Expand the capacity of the recycled buffers array.
      byte[][] newRecycledBuffers = new byte[newRecycledBufferCount * 2][];
      if (recycledBufferCount > 0) {
        System.arraycopy(recycledBuffers, 0, newRecycledBuffers, 0, recycledBufferCount);
      }
      recycledBuffers = newRecycledBuffers;
    }
    System.arraycopy(buffers, 0, recycledBuffers, recycledBufferCount, buffers.length);
    recycledBufferCount = newRecycledBufferCount;
  }

  private int requiredBufferCount(long size) {
    return (int) ((size + bufferLength - 1) / bufferLength);
  }

  private class AllocationImpl implements Allocation {

    private byte[][] buffers;

    public AllocationImpl(byte[][] buffers) {
      this.buffers = buffers;
    }

    @Override
    public byte[][] getBuffers() {
      return buffers;
    }

    @Override
    public int getFragmentOffset(int index) {
      return 0;
    }

    @Override
    public int getFragmentLength(int index) {
      return bufferLength;
    }

    @Override
    public void release() {
      if (buffers != null) {
        BufferPool.this.release(this);
        buffers = null;
      }
    }

  }

}
