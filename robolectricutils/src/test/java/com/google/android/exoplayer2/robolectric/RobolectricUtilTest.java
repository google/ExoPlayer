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
package com.google.android.exoplayer2.robolectric;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.common.base.Supplier;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RobolectricUtil}. */
@RunWith(AndroidJUnit4.class)
public class RobolectricUtilTest {
  @Test
  public void createRobolectricConditionVariable_blockWithTimeout_timesOut()
      throws InterruptedException {
    ConditionVariable conditionVariable = RobolectricUtil.createRobolectricConditionVariable();
    assertThat(conditionVariable.block(/* timeoutMs= */ 1)).isFalse();
    assertThat(conditionVariable.isOpen()).isFalse();
  }

  @Test
  public void createRobolectricConditionVariable_blockWithTimeout_blocksForAtLeastTimeout()
      throws InterruptedException {
    ConditionVariable conditionVariable = RobolectricUtil.createRobolectricConditionVariable();
    long startTimeMs = System.currentTimeMillis();
    assertThat(conditionVariable.block(/* timeoutMs= */ 500)).isFalse();
    long endTimeMs = System.currentTimeMillis();
    assertThat(endTimeMs - startTimeMs).isAtLeast(500);
  }

  @Test
  public void runMainLooperUntil_withConditionAlreadyTrue_returnsImmediately() throws Exception {
    Clock mockClock = mock(Clock.class);

    RobolectricUtil.runMainLooperUntil(() -> true, /* timeoutMs= */ 0, mockClock);

    verify(mockClock, atMost(1)).currentTimeMillis();
  }

  @Test
  public void runMainLooperUntil_withConditionThatNeverBecomesTrue_timesOut() {
    Clock mockClock = mock(Clock.class);
    when(mockClock.currentTimeMillis()).thenReturn(0L, 41L, 42L);

    assertThrows(
        TimeoutException.class,
        () -> RobolectricUtil.runMainLooperUntil(() -> false, /* timeoutMs= */ 42, mockClock));

    verify(mockClock, times(3)).currentTimeMillis();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void
      runMainLooperUntil_whenConditionBecomesTrueAfterDelay_returnsWhenConditionBecomesTrue()
          throws Exception {
    Supplier<Boolean> mockCondition = mock(Supplier.class);
    when(mockCondition.get())
        .thenReturn(false)
        .thenReturn(false)
        .thenReturn(false)
        .thenReturn(false)
        .thenReturn(true);

    RobolectricUtil.runMainLooperUntil(mockCondition, /* timeoutMs= */ 5674, mock(Clock.class));

    verify(mockCondition, times(5)).get();
  }
}
