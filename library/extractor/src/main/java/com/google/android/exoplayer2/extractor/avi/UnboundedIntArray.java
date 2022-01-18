package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import java.util.Arrays;

public class UnboundedIntArray {
  @NonNull
  int[] array;
  //unint
  int size =0;

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

  public int getSize() {
    return size;
  }

  public void pack() {
    array = Arrays.copyOf(array, size);
  }

  protected void grow() {
    int increase = Math.max(array.length /4, 1);
    array = Arrays.copyOf(array, increase + array.length + size);
  }

  /**
   * Only works if values are in sequential order
   * @param v
   * @return
   */
  public int indexOf(int v) {
    return Arrays.binarySearch(array, v);
  }
}
