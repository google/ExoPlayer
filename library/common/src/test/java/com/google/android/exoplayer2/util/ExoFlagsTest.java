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

/** Unit test for {@link ExoFlags}. */
@RunWith(AndroidJUnit4.class)
public final class ExoFlagsTest {

  @Test
  public void contains_withoutAdd_returnsFalseForAllValues() {
    ExoFlags flags = new ExoFlags.Builder().build();

    assertThat(flags.contains(/* flag= */ -1234)).isFalse();
    assertThat(flags.contains(/* flag= */ 0)).isFalse();
    assertThat(flags.contains(/* flag= */ 2)).isFalse();
    assertThat(flags.contains(/* flag= */ Integer.MAX_VALUE)).isFalse();
  }

  @Test
  public void contains_afterAdd_returnsTrueForAddedValues() {
    ExoFlags flags =
        new ExoFlags.Builder()
            .add(/* flag= */ -1234)
            .add(/* flag= */ 0)
            .add(/* flag= */ 2)
            .add(/* flag= */ Integer.MAX_VALUE)
            .build();

    assertThat(flags.contains(/* flag= */ -1235)).isFalse();
    assertThat(flags.contains(/* flag= */ -1234)).isTrue();
    assertThat(flags.contains(/* flag= */ 0)).isTrue();
    assertThat(flags.contains(/* flag= */ 1)).isFalse();
    assertThat(flags.contains(/* flag= */ 2)).isTrue();
    assertThat(flags.contains(/* flag= */ Integer.MAX_VALUE - 1)).isFalse();
    assertThat(flags.contains(/* flag= */ Integer.MAX_VALUE)).isTrue();
  }

  @Test
  public void contains_afterAddIf_returnsTrueForAddedValues() {
    ExoFlags flags =
        new ExoFlags.Builder()
            .addIf(/* flag= */ -1234, /* condition= */ true)
            .addIf(/* flag= */ 0, /* condition= */ false)
            .addIf(/* flag= */ 2, /* condition= */ true)
            .addIf(/* flag= */ Integer.MAX_VALUE, /* condition= */ false)
            .build();

    assertThat(flags.contains(/* flag= */ -1235)).isFalse();
    assertThat(flags.contains(/* flag= */ -1234)).isTrue();
    assertThat(flags.contains(/* flag= */ 0)).isFalse();
    assertThat(flags.contains(/* flag= */ 1)).isFalse();
    assertThat(flags.contains(/* flag= */ 2)).isTrue();
    assertThat(flags.contains(/* flag= */ Integer.MAX_VALUE - 1)).isFalse();
    assertThat(flags.contains(/* flag= */ Integer.MAX_VALUE)).isFalse();
  }

  @Test
  public void containsAny_withoutAdd_returnsFalseForAllValues() {
    ExoFlags flags = new ExoFlags.Builder().build();

    assertThat(flags.containsAny(/* flags...= */ -1234, 0, 2, Integer.MAX_VALUE)).isFalse();
  }

  @Test
  public void containsAny_afterAdd_returnsTrueForAddedValues() {
    ExoFlags flags =
        new ExoFlags.Builder()
            .add(/* flag= */ -1234)
            .add(/* flag= */ 0)
            .add(/* flag= */ 2)
            .add(/* flag= */ Integer.MAX_VALUE)
            .build();

    assertThat(
            flags.containsAny(
                /* flags...= */ -1235, -1234, 0, 1, 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE))
        .isTrue();
    assertThat(flags.containsAny(/* flags...= */ -1235, 1, Integer.MAX_VALUE - 1)).isFalse();
  }

  @Test
  public void size_withoutAdd_returnsZero() {
    ExoFlags flags = new ExoFlags.Builder().build();

    assertThat(flags.size()).isEqualTo(0);
  }

  @Test
  public void size_afterAdd_returnsNumberUniqueOfElements() {
    ExoFlags flags =
        new ExoFlags.Builder()
            .add(/* flag= */ 0)
            .add(/* flag= */ 0)
            .add(/* flag= */ 0)
            .add(/* flag= */ 123)
            .add(/* flag= */ 123)
            .build();

    assertThat(flags.size()).isEqualTo(2);
  }

  @Test
  public void get_withNegativeIndex_throwsIndexOutOfBoundsException() {
    ExoFlags flags = new ExoFlags.Builder().build();

    assertThrows(IndexOutOfBoundsException.class, () -> flags.get(/* index= */ -1));
  }

  @Test
  public void get_withIndexExceedingSize_throwsIndexOutOfBoundsException() {
    ExoFlags flags = new ExoFlags.Builder().add(/* flag= */ 0).add(/* flag= */ 123).build();

    assertThrows(IndexOutOfBoundsException.class, () -> flags.get(/* index= */ 2));
  }

  @Test
  public void get_afterAdd_returnsAllUniqueValues() {
    ExoFlags flags =
        new ExoFlags.Builder()
            .add(/* flag= */ 0)
            .add(/* flag= */ 0)
            .add(/* flag= */ 0)
            .add(/* flag= */ 123)
            .add(/* flag= */ 123)
            .add(/* flag= */ 456)
            .build();

    List<Integer> values = new ArrayList<>();
    for (int i = 0; i < flags.size(); i++) {
      values.add(flags.get(i));
    }
    assertThat(values).containsExactly(0, 123, 456);
  }
}
