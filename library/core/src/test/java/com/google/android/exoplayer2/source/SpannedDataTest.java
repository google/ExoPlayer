/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SpannedData}. */
@RunWith(AndroidJUnit4.class)
public final class SpannedDataTest {

  private static final String VALUE_1 = "value 1";
  private static final String VALUE_2 = "value 2";
  private static final String VALUE_3 = "value 3";

  @Test
  public void appendMultipleSpansThenRead() {
    SpannedData<String> spannedData = new SpannedData<>();

    spannedData.appendSpan(/* startKey= */ 0, VALUE_1);
    spannedData.appendSpan(/* startKey= */ 2, VALUE_2);
    spannedData.appendSpan(/* startKey= */ 4, VALUE_3);

    assertThat(spannedData.get(0)).isEqualTo(VALUE_1);
    assertThat(spannedData.get(1)).isEqualTo(VALUE_1);
    assertThat(spannedData.get(2)).isEqualTo(VALUE_2);
    assertThat(spannedData.get(3)).isEqualTo(VALUE_2);
    assertThat(spannedData.get(4)).isEqualTo(VALUE_3);
    assertThat(spannedData.get(5)).isEqualTo(VALUE_3);
  }

  @Test
  public void append_emptySpansDiscarded() {
    SpannedData<String> spannedData = new SpannedData<>();

    spannedData.appendSpan(/* startKey= */ 0, VALUE_1);
    spannedData.appendSpan(/* startKey= */ 2, VALUE_2);
    spannedData.appendSpan(/* startKey= */ 2, VALUE_3);

    assertThat(spannedData.get(0)).isEqualTo(VALUE_1);
    assertThat(spannedData.get(1)).isEqualTo(VALUE_1);
    assertThat(spannedData.get(2)).isEqualTo(VALUE_3);
    assertThat(spannedData.get(3)).isEqualTo(VALUE_3);
  }

  @Test
  public void discardTo() {
    SpannedData<String> spannedData = new SpannedData<>();

    spannedData.appendSpan(/* startKey= */ 0, VALUE_1);
    spannedData.appendSpan(/* startKey= */ 2, VALUE_2);
    spannedData.appendSpan(/* startKey= */ 4, VALUE_3);

    spannedData.discardTo(2);

    assertThat(spannedData.get(0)).isEqualTo(VALUE_2);
    assertThat(spannedData.get(2)).isEqualTo(VALUE_2);

    spannedData.discardTo(4);

    assertThat(spannedData.get(3)).isEqualTo(VALUE_3);
    assertThat(spannedData.get(4)).isEqualTo(VALUE_3);
  }

  @Test
  public void discardTo_prunesEmptySpans() {
    SpannedData<String> spannedData = new SpannedData<>();

    spannedData.appendSpan(/* startKey= */ 0, VALUE_1);
    spannedData.appendSpan(/* startKey= */ 2, VALUE_2);
    spannedData.appendSpan(/* startKey= */ 2, VALUE_3);

    spannedData.discardTo(2);

    assertThat(spannedData.get(0)).isEqualTo(VALUE_3);
    assertThat(spannedData.get(2)).isEqualTo(VALUE_3);
  }

  @Test
  public void discardFromThenAppend_keepsValueIfSpanEndsUpNonEmpty() {
    SpannedData<String> spannedData = new SpannedData<>();

    spannedData.appendSpan(/* startKey= */ 0, VALUE_1);
    spannedData.appendSpan(/* startKey= */ 2, VALUE_2);
    spannedData.appendSpan(/* startKey= */ 4, VALUE_3);

    spannedData.discardFrom(2);
    assertThat(spannedData.getEndValue()).isEqualTo(VALUE_2);

    spannedData.appendSpan(/* startKey= */ 3, VALUE_3);

    assertThat(spannedData.get(0)).isEqualTo(VALUE_1);
    assertThat(spannedData.get(1)).isEqualTo(VALUE_1);
    assertThat(spannedData.get(2)).isEqualTo(VALUE_2);
    assertThat(spannedData.get(3)).isEqualTo(VALUE_3);
  }

  @Test
  public void discardFromThenAppend_prunesEmptySpan() {
    SpannedData<String> spannedData = new SpannedData<>();

    spannedData.appendSpan(/* startKey= */ 0, VALUE_1);
    spannedData.appendSpan(/* startKey= */ 2, VALUE_2);

    spannedData.discardFrom(2);

    spannedData.appendSpan(/* startKey= */ 2, VALUE_3);

    assertThat(spannedData.get(0)).isEqualTo(VALUE_1);
    assertThat(spannedData.get(1)).isEqualTo(VALUE_1);
    assertThat(spannedData.get(2)).isEqualTo(VALUE_3);
  }

  @Test
  public void clear() {
    SpannedData<String> spannedData = new SpannedData<>();

    spannedData.appendSpan(/* startKey= */ 0, VALUE_1);
    spannedData.appendSpan(/* startKey= */ 2, VALUE_2);

    spannedData.clear();

    spannedData.appendSpan(/* startKey= */ 1, VALUE_3);

    assertThat(spannedData.get(0)).isEqualTo(VALUE_3);
    assertThat(spannedData.get(1)).isEqualTo(VALUE_3);
  }
}
