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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MutableFlags}. */
@RunWith(AndroidJUnit4.class)
public final class MutableFlagsTest {

  @Test
  public void contains_withoutAdd_returnsFalseForAllValues() {
    MutableFlags flags = new MutableFlags();

    assertThat(flags.contains(/* flag= */ -1234)).isFalse();
    assertThat(flags.contains(/* flag= */ 0)).isFalse();
    assertThat(flags.contains(/* flag= */ 2)).isFalse();
    assertThat(flags.contains(/* flag= */ Integer.MAX_VALUE)).isFalse();
  }

  @Test
  public void contains_afterAdd_returnsTrueForAddedValues() {
    MutableFlags flags = new MutableFlags();

    flags.add(/* flag= */ -1234);
    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 2);
    flags.add(/* flag= */ Integer.MAX_VALUE);

    assertThat(flags.contains(/* flag= */ -1235)).isFalse();
    assertThat(flags.contains(/* flag= */ -1234)).isTrue();
    assertThat(flags.contains(/* flag= */ 0)).isTrue();
    assertThat(flags.contains(/* flag= */ 1)).isFalse();
    assertThat(flags.contains(/* flag= */ 2)).isTrue();
    assertThat(flags.contains(/* flag= */ Integer.MAX_VALUE - 1)).isFalse();
    assertThat(flags.contains(/* flag= */ Integer.MAX_VALUE)).isTrue();
  }

  @Test
  public void contains_afterClear_returnsFalseForAllValues() {
    MutableFlags flags = new MutableFlags();
    flags.add(/* flag= */ -1234);
    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 2);
    flags.add(/* flag= */ Integer.MAX_VALUE);

    flags.clear();

    assertThat(flags.contains(/* flag= */ -1234)).isFalse();
    assertThat(flags.contains(/* flag= */ 0)).isFalse();
    assertThat(flags.contains(/* flag= */ 2)).isFalse();
    assertThat(flags.contains(/* flag= */ Integer.MAX_VALUE)).isFalse();
  }

  @Test
  public void size_withoutAdd_returnsZero() {
    MutableFlags flags = new MutableFlags();

    assertThat(flags.size()).isEqualTo(0);
  }

  @Test
  public void size_afterAdd_returnsNumberUniqueOfElements() {
    MutableFlags flags = new MutableFlags();

    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 123);
    flags.add(/* flag= */ 123);

    assertThat(flags.size()).isEqualTo(2);
  }

  @Test
  public void size_afterClear_returnsZero() {
    MutableFlags flags = new MutableFlags();

    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 123);
    flags.clear();

    assertThat(flags.size()).isEqualTo(0);
  }

  @Test
  public void get_withNegativeIndex_throwsIllegalArgumentException() {
    MutableFlags flags = new MutableFlags();

    assertThrows(IllegalArgumentException.class, () -> flags.get(/* index= */ -1));
  }

  @Test
  public void get_withIndexExceedingSize_throwsIllegalArgumentException() {
    MutableFlags flags = new MutableFlags();

    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 123);

    assertThrows(IllegalArgumentException.class, () -> flags.get(/* index= */ 2));
  }

  @Test
  public void get_afterAdd_returnsAllUniqueValues() {
    MutableFlags flags = new MutableFlags();

    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 0);
    flags.add(/* flag= */ 123);
    flags.add(/* flag= */ 123);
    flags.add(/* flag= */ 456);

    List<Integer> values = new ArrayList<>();
    for (int i = 0; i < flags.size(); i++) {
      values.add(flags.get(i));
    }
    assertThat(values).containsExactly(0, 123, 456);
  }
}
