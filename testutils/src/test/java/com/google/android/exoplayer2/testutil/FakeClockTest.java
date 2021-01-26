/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.ConditionVariable;
import android.os.HandlerThread;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.HandlerWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link FakeClock}. */
@RunWith(AndroidJUnit4.class)
public final class FakeClockTest {

  @Test
  public void currentTimeMillis_withoutBootTime() {
    FakeClock fakeClock = new FakeClock(/* initialTimeMs= */ 10);
    assertThat(fakeClock.currentTimeMillis()).isEqualTo(10);
  }

  @Test
  public void currentTimeMillis_withBootTime() {
    FakeClock fakeClock = new FakeClock(/* bootTimeMs= */ 150, /* initialTimeMs= */ 200);
    assertThat(fakeClock.currentTimeMillis()).isEqualTo(350);
  }

  @Test
  public void currentTimeMillis_advanceTime_currentTimeHasAdvanced() {
    FakeClock fakeClock = new FakeClock(/* bootTimeMs= */ 100, /* initialTimeMs= */ 50);
    fakeClock.advanceTime(/* timeDiffMs */ 250);
    assertThat(fakeClock.currentTimeMillis()).isEqualTo(400);
  }

  @Test
  public void testAdvanceTime() {
    FakeClock fakeClock = new FakeClock(2000);
    assertThat(fakeClock.elapsedRealtime()).isEqualTo(2000);
    fakeClock.advanceTime(500);
    assertThat(fakeClock.elapsedRealtime()).isEqualTo(2500);
    fakeClock.advanceTime(0);
    assertThat(fakeClock.elapsedRealtime()).isEqualTo(2500);
  }

  @Test
  public void testPostDelayed() {
    HandlerThread handlerThread = new HandlerThread("FakeClockTest");
    handlerThread.start();
    FakeClock fakeClock = new FakeClock(0);
    HandlerWrapper handler =
        fakeClock.createHandler(handlerThread.getLooper(), /* callback= */ null);

    TestRunnable[] testRunnables = {
      new TestRunnable(),
      new TestRunnable(),
      new TestRunnable(),
      new TestRunnable(),
      new TestRunnable()
    };
    handler.postDelayed(testRunnables[0], 0);
    handler.postDelayed(testRunnables[1], 100);
    handler.postDelayed(testRunnables[2], 200);
    waitForHandler(handler);
    assertTestRunnableStates(new boolean[] {true, false, false, false, false}, testRunnables);

    fakeClock.advanceTime(150);
    handler.postDelayed(testRunnables[3], 50);
    handler.postDelayed(testRunnables[4], 100);
    waitForHandler(handler);
    assertTestRunnableStates(new boolean[] {true, true, false, false, false}, testRunnables);

    fakeClock.advanceTime(50);
    waitForHandler(handler);
    assertTestRunnableStates(new boolean[] {true, true, true, true, false}, testRunnables);

    fakeClock.advanceTime(1000);
    waitForHandler(handler);
    assertTestRunnableStates(new boolean[] {true, true, true, true, true}, testRunnables);
  }

  private static void waitForHandler(HandlerWrapper handler) {
    final ConditionVariable handlerFinished = new ConditionVariable();
    handler.post(handlerFinished::open);
    handlerFinished.block();
  }

  private static void assertTestRunnableStates(boolean[] states, TestRunnable[] testRunnables) {
    for (int i = 0; i < testRunnables.length; i++) {
      assertThat(testRunnables[i].hasRun).isEqualTo(states[i]);
    }
  }

  private static final class TestRunnable implements Runnable {

    public boolean hasRun;

    @Override
    public void run() {
      hasRun = true;
    }
  }
}
