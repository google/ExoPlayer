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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.ConditionVariable;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TestUtil}. */
@RunWith(AndroidJUnit4.class)
public class TestUtilTest {

  @Test
  public void createRobolectricConditionVariable_blockWithTimeout_timesOut()
      throws InterruptedException {
    ConditionVariable conditionVariable = TestUtil.createRobolectricConditionVariable();
    assertThat(conditionVariable.block(/* timeoutMs= */ 1)).isFalse();
    assertThat(conditionVariable.isOpen()).isFalse();
  }

  @Test
  public void createRobolectricConditionVariable_blockWithTimeout_blocksForAtLeastTimeout()
      throws InterruptedException {
    ConditionVariable conditionVariable = TestUtil.createRobolectricConditionVariable();
    long startTimeMs = System.currentTimeMillis();
    assertThat(conditionVariable.block(/* timeoutMs= */ 500)).isFalse();
    long endTimeMs = System.currentTimeMillis();
    assertThat(endTimeMs - startTimeMs).isAtLeast(500);
  }
}
