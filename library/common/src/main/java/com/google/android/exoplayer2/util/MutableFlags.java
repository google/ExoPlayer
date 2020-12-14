/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import android.util.SparseBooleanArray;
import androidx.annotation.Nullable;

/**
 * A set of integer flags.
 *
 * <p>Intended for usages where the number of flags may exceed 32 and can no longer be represented
 * by an IntDef.
 */
public class MutableFlags {

  private final SparseBooleanArray flags;

  /** Creates the set of flags. */
  public MutableFlags() {
    flags = new SparseBooleanArray();
  }

  /** Clears all previously set flags. */
  public void clear() {
    flags.clear();
  }

  /**
   * Adds a flag to the set.
   *
   * @param flag The flag to add.
   */
  public void add(int flag) {
    flags.append(flag, /* value= */ true);
  }

  /**
   * Returns whether the set contains the given flag.
   *
   * @param flag The flag.
   * @return Whether the set contains the flag.
   */
  public boolean contains(int flag) {
    return flags.get(flag);
  }

  /**
   * Returns whether the set contains at least one of the given flags.
   *
   * @param flags The flags.
   * @return Whether the set contains at least one of the flags.
   */
  public boolean containsAny(int... flags) {
    for (int flag : flags) {
      if (contains(flag)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the number of flags in this set. */
  public int size() {
    return flags.size();
  }

  /**
   * Returns the flag at the given index.
   *
   * @param index The index. Must be between 0 (inclusive) and {@link #size()} (exclusive).
   * @return The flag at the given index.
   * @throws IllegalArgumentException If index is outside the allowed range.
   */
  public int get(int index) {
    Assertions.checkArgument(index >= 0 && index < size());
    return flags.keyAt(index);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MutableFlags)) {
      return false;
    }
    MutableFlags that = (MutableFlags) o;
    return flags.equals(that.flags);
  }

  @Override
  public int hashCode() {
    return flags.hashCode();
  }
}
