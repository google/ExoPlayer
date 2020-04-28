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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ConditionVariableTest}. */
@RunWith(AndroidJUnit4.class)
public class ConditionVariableTest {

  @Test
  public void initialState_isClosed() {
    ConditionVariable conditionVariable = buildTestConditionVariable();
    assertThat(conditionVariable.isOpen()).isFalse();
  }

  @Test
  public void blockWithTimeout_timesOut() throws InterruptedException {
    ConditionVariable conditionVariable = buildTestConditionVariable();
    assertThat(conditionVariable.block(1)).isFalse();
    assertThat(conditionVariable.isOpen()).isFalse();
  }

  @Test
  public void blockWithTimeout_blocksForAtLeastTimeout() throws InterruptedException {
    ConditionVariable conditionVariable = buildTestConditionVariable();
    long startTimeMs = System.currentTimeMillis();
    assertThat(conditionVariable.block(/* timeoutMs= */ 500)).isFalse();
    long endTimeMs = System.currentTimeMillis();
    assertThat(endTimeMs - startTimeMs).isAtLeast(500L);
  }

  @Test
  public void blockWithoutTimeout_blocks() throws InterruptedException {
    ConditionVariable conditionVariable = buildTestConditionVariable();

    AtomicBoolean blockReturned = new AtomicBoolean();
    AtomicBoolean blockWasInterrupted = new AtomicBoolean();
    Thread blockingThread =
        new Thread(
            () -> {
              try {
                conditionVariable.block();
                blockReturned.set(true);
              } catch (InterruptedException e) {
                blockWasInterrupted.set(true);
              }
            });

    blockingThread.start();
    Thread.sleep(500);
    assertThat(blockReturned.get()).isFalse();

    blockingThread.interrupt();
    blockingThread.join();
    assertThat(blockWasInterrupted.get()).isTrue();
    assertThat(conditionVariable.isOpen()).isFalse();
  }

  @Test
  public void blockWithMaxTimeout_blocks() throws InterruptedException {
    ConditionVariable conditionVariable = buildTestConditionVariable();

    AtomicBoolean blockReturned = new AtomicBoolean();
    AtomicBoolean blockWasInterrupted = new AtomicBoolean();
    Thread blockingThread =
        new Thread(
            () -> {
              try {
                conditionVariable.block(/* timeoutMs= */ Long.MAX_VALUE);
                blockReturned.set(true);
              } catch (InterruptedException e) {
                blockWasInterrupted.set(true);
              }
            });

    blockingThread.start();
    Thread.sleep(500);
    assertThat(blockReturned.get()).isFalse();

    blockingThread.interrupt();
    blockingThread.join();
    assertThat(blockWasInterrupted.get()).isTrue();
    assertThat(conditionVariable.isOpen()).isFalse();
  }

  @Test
  public void open_unblocksBlock() throws InterruptedException {
    ConditionVariable conditionVariable = buildTestConditionVariable();

    AtomicBoolean blockReturned = new AtomicBoolean();
    AtomicBoolean blockWasInterrupted = new AtomicBoolean();
    Thread blockingThread =
        new Thread(
            () -> {
              try {
                conditionVariable.block();
                blockReturned.set(true);
              } catch (InterruptedException e) {
                blockWasInterrupted.set(true);
              }
            });

    blockingThread.start();
    Thread.sleep(500);
    assertThat(blockReturned.get()).isFalse();

    conditionVariable.open();
    blockingThread.join();
    assertThat(blockReturned.get()).isTrue();
    assertThat(conditionVariable.isOpen()).isTrue();
  }

  private static ConditionVariable buildTestConditionVariable() {
    return new ConditionVariable(
        new SystemClock() {
          @Override
          public long elapsedRealtime() {
            // elapsedRealtime() does not advance during Robolectric test execution, so use
            // currentTimeMillis() instead. This is technically unsafe because this clock is not
            // guaranteed to be monotonic, but in practice it will work provided the clock of the
            // host machine does not change during test execution.
            return Clock.DEFAULT.currentTimeMillis();
          }
        });
  }
}
