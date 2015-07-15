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
import com.google.android.exoplayer.util.Util;

import java.util.Arrays;

/**
 * Default implementation of {@link Allocator}.
 */
public final class DefaultAllocator implements Allocator {

  private static final int INITIAL_RECYCLED_ALLOCATION_CAPACITY = 100;

  private final int individualAllocationSize;

  private int allocatedCount;
  private int recycledCount;
  private Allocation[] recycledAllocations;

  /**
   * Constructs an empty pool.
   *
   * @param individualAllocationSize The length of each individual allocation.
   */
  public DefaultAllocator(int individualAllocationSize) {
    Assertions.checkArgument(individualAllocationSize > 0);
    this.individualAllocationSize = individualAllocationSize;
    this.recycledAllocations = new Allocation[INITIAL_RECYCLED_ALLOCATION_CAPACITY];
  }

  @Override
  public synchronized Allocation allocate() {
    allocatedCount++;
    return recycledCount > 0 ? recycledAllocations[--recycledCount]
        : new Allocation(new byte[individualAllocationSize], 0);
  }

  @Override
  public synchronized void release(Allocation allocation) {
    // Weak sanity check that the allocation probably originated from this pool.
    Assertions.checkArgument(allocation.data.length == individualAllocationSize);
    allocatedCount--;
    if (recycledCount == recycledAllocations.length) {
      recycledAllocations = Arrays.copyOf(recycledAllocations, recycledAllocations.length * 2);
    }
    recycledAllocations[recycledCount++] = allocation;
    // Wake up threads waiting for the allocated size to drop.
    notifyAll();
  }

  @Override
  public synchronized void trim(int targetSize) {
    int targetAllocationCount = Util.ceilDivide(targetSize, individualAllocationSize);
    int targetRecycledAllocationCount = Math.max(0, targetAllocationCount - allocatedCount);
    if (targetRecycledAllocationCount < recycledCount) {
      Arrays.fill(recycledAllocations, targetRecycledAllocationCount, recycledCount, null);
      recycledCount = targetRecycledAllocationCount;
    }
  }

  @Override
  public synchronized int getTotalBytesAllocated() {
    return allocatedCount * individualAllocationSize;
  }

  @Override
  public int getIndividualAllocationLength() {
    return individualAllocationSize;
  }

  /**
   * Blocks execution until the allocated number of bytes allocated is not greater than the
   * threshold, or the thread is interrupted.
   */
  public synchronized void blockWhileTotalBytesAllocatedExceeds(int limit)
      throws InterruptedException {
    while (getTotalBytesAllocated() > limit) {
      wait();
    }
  }

}
