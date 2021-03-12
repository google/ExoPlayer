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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.drm.DrmSessionManager.DrmSessionReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link SpannedData}. */
@RunWith(AndroidJUnit4.class)
public final class SpannedDataTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private DrmSessionReference value1;
  @Mock private DrmSessionReference value2;
  @Mock private DrmSessionReference value3;

  @Test
  public void appendMultipleSpansThenRead() {
    SpannedData<DrmSessionReference> spannedData =
        new SpannedData<>(/* removeCallback= */ DrmSessionReference::release);

    spannedData.appendSpan(/* startKey= */ 0, value1);
    spannedData.appendSpan(/* startKey= */ 2, value2);
    spannedData.appendSpan(/* startKey= */ 4, value3);

    assertThat(spannedData.get(0)).isEqualTo(value1);
    assertThat(spannedData.get(1)).isEqualTo(value1);
    assertThat(spannedData.get(2)).isEqualTo(value2);
    assertThat(spannedData.get(3)).isEqualTo(value2);
    assertThat(spannedData.get(4)).isEqualTo(value3);
    assertThat(spannedData.get(5)).isEqualTo(value3);

    verify(value1, never()).release();
    verify(value2, never()).release();
    verify(value3, never()).release();
  }

  @Test
  public void append_emptySpansDiscarded() {
    SpannedData<DrmSessionReference> spannedData = new SpannedData<>();

    spannedData.appendSpan(/* startKey= */ 0, value1);
    spannedData.appendSpan(/* startKey= */ 2, value2);
    spannedData.appendSpan(/* startKey= */ 2, value3);

    assertThat(spannedData.get(0)).isEqualTo(value1);
    assertThat(spannedData.get(1)).isEqualTo(value1);
    assertThat(spannedData.get(2)).isEqualTo(value3);
    assertThat(spannedData.get(3)).isEqualTo(value3);
  }

  @Test
  public void getEndValue() {
    SpannedData<String> spannedData = new SpannedData<>();

    assertThrows(Exception.class, spannedData::getEndValue);

    spannedData.appendSpan(/* startKey= */ 0, "test 1");
    spannedData.appendSpan(/* startKey= */ 2, "test 2");
    spannedData.appendSpan(/* startKey= */ 4, "test 3");

    assertThat(spannedData.getEndValue()).isEqualTo("test 3");

    spannedData.discardFrom(2);
    assertThat(spannedData.getEndValue()).isEqualTo("test 2");

    spannedData.clear();
    assertThrows(Exception.class, spannedData::getEndValue);
  }

  @Test
  public void discardTo() {
    SpannedData<DrmSessionReference> spannedData =
        new SpannedData<>(/* removeCallback= */ DrmSessionReference::release);

    spannedData.appendSpan(/* startKey= */ 0, value1);
    spannedData.appendSpan(/* startKey= */ 2, value2);
    spannedData.appendSpan(/* startKey= */ 4, value3);

    spannedData.discardTo(2);

    verify(value1).release();
    verify(value2, never()).release();
    assertThat(spannedData.get(0)).isEqualTo(value2);
    assertThat(spannedData.get(2)).isEqualTo(value2);

    spannedData.discardTo(4);

    verify(value2).release();
    verify(value3, never()).release();
    assertThat(spannedData.get(3)).isEqualTo(value3);
    assertThat(spannedData.get(4)).isEqualTo(value3);
  }

  @Test
  public void discardTo_prunesEmptySpans() {
    SpannedData<DrmSessionReference> spannedData = new SpannedData<>();

    spannedData.appendSpan(/* startKey= */ 0, value1);
    spannedData.appendSpan(/* startKey= */ 2, value2);
    spannedData.appendSpan(/* startKey= */ 2, value3);

    spannedData.discardTo(2);

    assertThat(spannedData.get(0)).isEqualTo(value3);
    assertThat(spannedData.get(2)).isEqualTo(value3);
  }

  @Test
  public void discardFromThenAppend_keepsValueIfSpanEndsUpNonEmpty() {
    SpannedData<DrmSessionReference> spannedData =
        new SpannedData<>(/* removeCallback= */ DrmSessionReference::release);

    spannedData.appendSpan(/* startKey= */ 0, value1);
    spannedData.appendSpan(/* startKey= */ 2, value2);
    spannedData.appendSpan(/* startKey= */ 4, value3);

    spannedData.discardFrom(2);

    verify(value3).release();
    assertThat(spannedData.getEndValue()).isEqualTo(value2);

    spannedData.appendSpan(/* startKey= */ 3, value3);

    verify(value1, never()).release();
    verify(value2, never()).release();
    assertThat(spannedData.get(0)).isEqualTo(value1);
    assertThat(spannedData.get(1)).isEqualTo(value1);
    assertThat(spannedData.get(2)).isEqualTo(value2);
    assertThat(spannedData.get(3)).isEqualTo(value3);
  }

  @Test
  public void discardFromThenAppend_prunesEmptySpan() {
    SpannedData<DrmSessionReference> spannedData =
        new SpannedData<>(/* removeCallback= */ DrmSessionReference::release);

    spannedData.appendSpan(/* startKey= */ 0, value1);
    spannedData.appendSpan(/* startKey= */ 2, value2);

    spannedData.discardFrom(2);

    verify(value2, never()).release();

    spannedData.appendSpan(/* startKey= */ 2, value3);

    verify(value2).release();
    assertThat(spannedData.get(0)).isEqualTo(value1);
    assertThat(spannedData.get(1)).isEqualTo(value1);
    assertThat(spannedData.get(2)).isEqualTo(value3);
  }

  @Test
  public void clear() {
    SpannedData<DrmSessionReference> spannedData =
        new SpannedData<>(/* removeCallback= */ DrmSessionReference::release);

    spannedData.appendSpan(/* startKey= */ 0, value1);
    spannedData.appendSpan(/* startKey= */ 2, value2);

    spannedData.clear();

    verify(value1).release();
    verify(value2).release();

    spannedData.appendSpan(/* startKey= */ 1, value3);

    assertThat(spannedData.get(0)).isEqualTo(value3);
    assertThat(spannedData.get(1)).isEqualTo(value3);
  }

  @Test
  public void isEmpty() {
    SpannedData<String> spannedData = new SpannedData<>();

    assertThat(spannedData.isEmpty()).isTrue();

    spannedData.appendSpan(/* startKey= */ 0, "test 1");
    spannedData.appendSpan(/* startKey= */ 2, "test 2");

    assertThat(spannedData.isEmpty()).isFalse();

    // Discarding from 0 still retains the 'first' span, so collection doesn't end up empty.
    spannedData.discardFrom(0);
    assertThat(spannedData.isEmpty()).isFalse();

    spannedData.appendSpan(/* startKey= */ 2, "test 2");

    // Discarding to 3 still retains the 'last' span, so collection doesn't end up empty.
    spannedData.discardTo(3);
    assertThat(spannedData.isEmpty()).isFalse();

    spannedData.clear();

    assertThat(spannedData.isEmpty()).isTrue();
  }
}
