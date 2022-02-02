/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.util.Arrays;

/**
 * Optimized unbounded array of ints.
 * Used primarily to create Index (SeekMap) data.
 */
public class UnboundedIntArray {
  @NonNull
  @VisibleForTesting
  int[] array;
  //uint
  private int size = 0;

  public UnboundedIntArray() {
    this(8);
  }

  public UnboundedIntArray(int size) {
    if (size < 0) {
      throw new IllegalArgumentException("Initial size must be positive: " + size);
    }
    array = new int[size];
  }

  public void add(int v) {
    if (size == array.length) {
      grow();
    }
    array[size++] = v;
  }

  public int get(final int index) {
    if (index >= size) {
      throw new ArrayIndexOutOfBoundsException(index + ">=" + size);
    }
    return array[index];
  }

  public int getSize() {
    return size;
  }

  public void pack() {
    if (size != array.length) {
      array = Arrays.copyOf(array, size);
    }
  }

  protected void grow() {
    int increase = Math.max(array.length /4, 1);
    array = Arrays.copyOf(array, increase + array.length + size);
  }

  public int[] getArray() {
    pack();
    return array;
  }

  /**
   * Only works if values are in sequential order
   */
  public int indexOf(int v) {
    return Arrays.binarySearch(array, v);
  }
}
