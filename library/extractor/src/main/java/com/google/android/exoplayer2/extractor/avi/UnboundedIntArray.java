package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.util.Arrays;

public class UnboundedIntArray {
  @NonNull
  @VisibleForTesting
  int[] array;
  //unint
  private int size =0;

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
   * @param v
   * @return
   */
  public int indexOf(int v) {
    return Arrays.binarySearch(array, v);
  }
}
