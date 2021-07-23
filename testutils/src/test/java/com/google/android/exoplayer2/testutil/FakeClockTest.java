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
import static org.robolectric.Shadows.shadowOf;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

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
    FakeClock fakeClock =
        new FakeClock(
            /* bootTimeMs= */ 150, /* initialTimeMs= */ 200, /* isAutoAdvancing= */ false);
    assertThat(fakeClock.currentTimeMillis()).isEqualTo(350);
  }

  @Test
  public void currentTimeMillis_afterAdvanceTime_currentTimeHasAdvanced() {
    FakeClock fakeClock =
        new FakeClock(/* bootTimeMs= */ 100, /* initialTimeMs= */ 50, /* isAutoAdvancing= */ false);
    fakeClock.advanceTime(/* timeDiffMs */ 250);
    assertThat(fakeClock.currentTimeMillis()).isEqualTo(400);
  }

  @Test
  public void elapsedRealtime_afterAdvanceTime_timeHasAdvanced() {
    FakeClock fakeClock = new FakeClock(2000);
    assertThat(fakeClock.elapsedRealtime()).isEqualTo(2000);
    fakeClock.advanceTime(500);
    assertThat(fakeClock.elapsedRealtime()).isEqualTo(2500);
    fakeClock.advanceTime(0);
    assertThat(fakeClock.elapsedRealtime()).isEqualTo(2500);
  }

  @Test
  public void createHandler_obtainMessageSendToTarget_triggersMessage() {
    HandlerThread handlerThread = new HandlerThread("FakeClockTest");
    handlerThread.start();
    FakeClock fakeClock = new FakeClock(/* initialTimeMs= */ 0);
    TestCallback callback = new TestCallback();
    HandlerWrapper handler = fakeClock.createHandler(handlerThread.getLooper(), callback);

    Object testObject = new Object();
    handler.obtainMessage(/* what= */ 1).sendToTarget();
    handler.obtainMessage(/* what= */ 2, /* obj= */ testObject).sendToTarget();
    handler.obtainMessage(/* what= */ 3, /* arg1= */ 99, /* arg2= */ 44).sendToTarget();
    handler
        .obtainMessage(/* what= */ 4, /* arg1= */ 88, /* arg2= */ 33, /* obj=*/ testObject)
        .sendToTarget();
    ShadowLooper.idleMainLooper();
    shadowOf(handler.getLooper()).idle();

    assertThat(callback.messages)
        .containsExactly(
            new MessageData(/* what= */ 1, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null),
            new MessageData(/* what= */ 2, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ testObject),
            new MessageData(/* what= */ 3, /* arg1= */ 99, /* arg2= */ 44, /* obj=*/ null),
            new MessageData(/* what= */ 4, /* arg1= */ 88, /* arg2= */ 33, /* obj=*/ testObject))
        .inOrder();
  }

  @Test
  public void createHandler_sendEmptyMessage_triggersMessageAtCorrectTime() {
    HandlerThread handlerThread = new HandlerThread("FakeClockTest");
    handlerThread.start();
    FakeClock fakeClock = new FakeClock(/* initialTimeMs= */ 0);
    TestCallback callback = new TestCallback();
    HandlerWrapper handler = fakeClock.createHandler(handlerThread.getLooper(), callback);

    handler.sendEmptyMessage(/* what= */ 1);
    handler.sendEmptyMessageAtTime(/* what= */ 2, /* uptimeMs= */ fakeClock.uptimeMillis() + 60);
    handler.sendEmptyMessageDelayed(/* what= */ 3, /* delayMs= */ 50);
    handler.sendEmptyMessage(/* what= */ 4);
    ShadowLooper.idleMainLooper();
    shadowOf(handler.getLooper()).idle();

    assertThat(callback.messages)
        .containsExactly(
            new MessageData(/* what= */ 1, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null),
            new MessageData(/* what= */ 4, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null))
        .inOrder();

    fakeClock.advanceTime(50);
    shadowOf(handler.getLooper()).idle();

    assertThat(callback.messages).hasSize(3);
    assertThat(Iterables.getLast(callback.messages))
        .isEqualTo(new MessageData(/* what= */ 3, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null));

    fakeClock.advanceTime(50);
    shadowOf(handler.getLooper()).idle();

    assertThat(callback.messages).hasSize(4);
    assertThat(Iterables.getLast(callback.messages))
        .isEqualTo(new MessageData(/* what= */ 2, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null));
  }

  @Test
  public void createHandler_sendMessageAtFrontOfQueue_sendsMessageFirst() {
    HandlerThread handlerThread = new HandlerThread("FakeClockTest");
    handlerThread.start();
    FakeClock fakeClock = new FakeClock(/* initialTimeMs= */ 0);
    TestCallback callback = new TestCallback();
    HandlerWrapper handler = fakeClock.createHandler(handlerThread.getLooper(), callback);

    handler.obtainMessage(/* what= */ 1).sendToTarget();
    handler.sendMessageAtFrontOfQueue(handler.obtainMessage(/* what= */ 2));
    handler.sendMessageAtFrontOfQueue(handler.obtainMessage(/* what= */ 3));
    handler.obtainMessage(/* what= */ 4).sendToTarget();
    ShadowLooper.idleMainLooper();
    shadowOf(handler.getLooper()).idle();

    assertThat(callback.messages)
        .containsExactly(
            new MessageData(/* what= */ 3, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null),
            new MessageData(/* what= */ 2, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null),
            new MessageData(/* what= */ 1, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null),
            new MessageData(/* what= */ 4, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null))
        .inOrder();
  }

  @Test
  public void createHandler_postDelayed_triggersMessagesUpToCurrentTime() {
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
    ShadowLooper.idleMainLooper();
    shadowOf(handler.getLooper()).idle();
    assertTestRunnableStates(new boolean[] {true, false, false, false, false}, testRunnables);

    fakeClock.advanceTime(150);
    handler.postDelayed(testRunnables[3], 50);
    handler.postDelayed(testRunnables[4], 100);
    ShadowLooper.idleMainLooper();
    shadowOf(handler.getLooper()).idle();
    assertTestRunnableStates(new boolean[] {true, true, false, false, false}, testRunnables);

    fakeClock.advanceTime(50);
    shadowOf(handler.getLooper()).idle();
    assertTestRunnableStates(new boolean[] {true, true, true, true, false}, testRunnables);

    fakeClock.advanceTime(1000);
    shadowOf(handler.getLooper()).idle();
    assertTestRunnableStates(new boolean[] {true, true, true, true, true}, testRunnables);
  }

  @Test
  public void createHandler_removeMessages_removesMessages() {
    HandlerThread handlerThread = new HandlerThread("FakeClockTest");
    handlerThread.start();
    FakeClock fakeClock = new FakeClock(/* initialTimeMs= */ 0);
    TestCallback callback = new TestCallback();
    HandlerWrapper handler = fakeClock.createHandler(handlerThread.getLooper(), callback);
    TestCallback otherCallback = new TestCallback();
    HandlerWrapper otherHandler = fakeClock.createHandler(handlerThread.getLooper(), otherCallback);

    TestRunnable testRunnable1 = new TestRunnable();
    TestRunnable testRunnable2 = new TestRunnable();
    Object messageToken = new Object();
    handler.obtainMessage(/* what= */ 1, /* obj= */ messageToken).sendToTarget();
    handler.sendEmptyMessageDelayed(/* what= */ 2, /* delayMs= */ 50);
    handler.post(testRunnable1);
    handler.postDelayed(testRunnable2, /* delayMs= */ 25);
    handler.sendEmptyMessage(/* what= */ 3);
    otherHandler.sendEmptyMessage(/* what= */ 2);

    handler.removeMessages(/* what= */ 2);
    handler.removeCallbacksAndMessages(messageToken);

    fakeClock.advanceTime(50);
    ShadowLooper.idleMainLooper();
    shadowOf(handlerThread.getLooper()).idle();

    assertThat(callback.messages)
        .containsExactly(
            new MessageData(/* what= */ 3, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null));
    assertThat(testRunnable1.hasRun).isTrue();
    assertThat(testRunnable2.hasRun).isTrue();

    // Assert that message with same "what" on other handler wasn't removed.
    assertThat(otherCallback.messages)
        .containsExactly(
            new MessageData(/* what= */ 2, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null));
  }

  @Test
  public void createHandler_removeAllMessages_removesAllMessages() {
    HandlerThread handlerThread = new HandlerThread("FakeClockTest");
    handlerThread.start();
    FakeClock fakeClock = new FakeClock(/* initialTimeMs= */ 0);
    TestCallback callback = new TestCallback();
    HandlerWrapper handler = fakeClock.createHandler(handlerThread.getLooper(), callback);
    TestCallback otherCallback = new TestCallback();
    HandlerWrapper otherHandler = fakeClock.createHandler(handlerThread.getLooper(), otherCallback);

    TestRunnable testRunnable1 = new TestRunnable();
    TestRunnable testRunnable2 = new TestRunnable();
    Object messageToken = new Object();
    handler.obtainMessage(/* what= */ 1, /* obj= */ messageToken).sendToTarget();
    handler.sendEmptyMessageDelayed(/* what= */ 2, /* delayMs= */ 50);
    handler.post(testRunnable1);
    handler.postDelayed(testRunnable2, /* delayMs= */ 25);
    handler.sendEmptyMessage(/* what= */ 3);
    otherHandler.sendEmptyMessage(/* what= */ 1);

    handler.removeCallbacksAndMessages(/* token= */ null);

    fakeClock.advanceTime(50);
    ShadowLooper.idleMainLooper();
    shadowOf(handlerThread.getLooper()).idle();

    assertThat(callback.messages).isEmpty();
    assertThat(testRunnable1.hasRun).isFalse();
    assertThat(testRunnable2.hasRun).isFalse();

    // Assert that message on other handler wasn't removed.
    assertThat(otherCallback.messages)
        .containsExactly(
            new MessageData(/* what= */ 1, /* arg1= */ 0, /* arg2= */ 0, /* obj=*/ null));
  }

  @Test
  public void createHandler_withIsAutoAdvancing_advancesTimeToNextMessages() {
    HandlerThread handlerThread = new HandlerThread("FakeClockTest");
    handlerThread.start();
    FakeClock fakeClock = new FakeClock(/* initialTimeMs= */ 0, /* isAutoAdvancing= */ true);
    HandlerWrapper handler =
        fakeClock.createHandler(handlerThread.getLooper(), /* callback= */ null);

    // Post a series of immediate and delayed messages.
    ArrayList<Long> clockTimes = new ArrayList<>();
    handler.post(
        () -> {
          handler.postDelayed(
              () -> clockTimes.add(fakeClock.elapsedRealtime()), /* delayMs= */ 100);
          handler.postDelayed(() -> clockTimes.add(fakeClock.elapsedRealtime()), /* delayMs= */ 50);
          handler.post(() -> clockTimes.add(fakeClock.elapsedRealtime()));
          handler.postDelayed(
              () -> {
                clockTimes.add(fakeClock.elapsedRealtime());
                handler.postDelayed(
                    () -> clockTimes.add(fakeClock.elapsedRealtime()), /* delayMs= */ 50);
              },
              /* delayMs= */ 20);
        });
    ShadowLooper.idleMainLooper();
    shadowOf(handler.getLooper()).idle();

    assertThat(clockTimes).containsExactly(0L, 20L, 50L, 70L, 100L).inOrder();
  }

  @Test
  public void createHandler_multiThreadCommunication_deliversMessagesDeterministicallyInOrder() {
    HandlerThread handlerThread1 = new HandlerThread("FakeClockTest");
    handlerThread1.start();
    HandlerThread handlerThread2 = new HandlerThread("FakeClockTest");
    handlerThread2.start();
    FakeClock fakeClock = new FakeClock(/* initialTimeMs= */ 0);
    HandlerWrapper handler1 =
        fakeClock.createHandler(handlerThread1.getLooper(), /* callback= */ null);
    HandlerWrapper handler2 =
        fakeClock.createHandler(handlerThread2.getLooper(), /* callback= */ null);

    ConditionVariable messagesFinished = new ConditionVariable();
    ArrayList<Integer> executionOrder = new ArrayList<>();
    handler1.post(
        () -> {
          executionOrder.add(1);
          handler2.post(() -> executionOrder.add(2));
          handler1.post(() -> executionOrder.add(3));
          handler2.post(
              () -> {
                executionOrder.add(4);
                handler2.post(() -> executionOrder.add(7));
                handler1.post(
                    () -> {
                      executionOrder.add(8);
                      messagesFinished.open();
                    });
              });
          handler2.post(() -> executionOrder.add(5));
          handler1.post(() -> executionOrder.add(6));
        });
    ShadowLooper.idleMainLooper();
    messagesFinished.block();

    assertThat(executionOrder).containsExactly(1, 2, 3, 4, 5, 6, 7, 8).inOrder();
  }

  @Test
  public void createHandler_blockingThreadWithOnBusyWaiting_canBeUnblockedByOtherThread() {
    HandlerThread handlerThread1 = new HandlerThread("FakeClockTest");
    handlerThread1.start();
    HandlerThread handlerThread2 = new HandlerThread("FakeClockTest");
    handlerThread2.start();
    FakeClock fakeClock = new FakeClock(/* initialTimeMs= */ 0, /* isAutoAdvancing= */ true);
    HandlerWrapper handler1 =
        fakeClock.createHandler(handlerThread1.getLooper(), /* callback= */ null);
    HandlerWrapper handler2 =
        fakeClock.createHandler(handlerThread2.getLooper(), /* callback= */ null);

    ArrayList<Integer> executionOrder = new ArrayList<>();
    handler1.post(
        () -> {
          executionOrder.add(1);
          ConditionVariable blockingCondition = new ConditionVariable();
          handler2.postDelayed(
              () -> {
                executionOrder.add(2);
                blockingCondition.open();
              },
              /* delayMs= */ 50);
          handler1.post(() -> executionOrder.add(4));
          fakeClock.onThreadBlocked();
          blockingCondition.block();
          executionOrder.add(3);
        });
    ShadowLooper.idleMainLooper();
    shadowOf(handler1.getLooper()).idle();
    shadowOf(handler2.getLooper()).idle();

    assertThat(executionOrder).containsExactly(1, 2, 3, 4).inOrder();
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

  private static final class TestCallback implements Handler.Callback {

    public final List<MessageData> messages;

    public TestCallback() {
      messages = new ArrayList<>();
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
      messages.add(new MessageData(msg.what, msg.arg1, msg.arg2, msg.obj));
      return true;
    }
  }

  private static final class MessageData {

    public final int what;
    public final int arg1;
    public final int arg2;
    @Nullable public final Object obj;

    public MessageData(int what, int arg1, int arg2, @Nullable Object obj) {
      this.what = what;
      this.arg1 = arg1;
      this.arg2 = arg2;
      this.obj = obj;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof MessageData)) {
        return false;
      }
      MessageData that = (MessageData) o;
      return what == that.what
          && arg1 == that.arg1
          && arg2 == that.arg2
          && Objects.equal(obj, that.obj);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(what, arg1, arg2, obj);
    }
  }
}
