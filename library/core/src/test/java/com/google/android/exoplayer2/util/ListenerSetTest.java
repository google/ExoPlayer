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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Unit test for {@link ListenerSet}. */
@RunWith(AndroidJUnit4.class)
public class ListenerSetTest {

  @Test
  public void queueEvent_isNotSentWithoutFlush() {
    ListenerSet<TestListener> listenerSet = new ListenerSet<>();
    TestListener listener = mock(TestListener.class);
    listenerSet.add(listener);

    listenerSet.queueEvent(TestListener::callback1);
    listenerSet.queueEvent(TestListener::callback2);

    verifyNoMoreInteractions(listener);
  }

  @Test
  public void flushEvents_sendsPreviouslyQueuedEventsToAllListeners() {
    ListenerSet<TestListener> listenerSet = new ListenerSet<>();
    TestListener listener1 = mock(TestListener.class);
    TestListener listener2 = mock(TestListener.class);
    listenerSet.add(listener1);
    listenerSet.add(listener2);

    listenerSet.queueEvent(TestListener::callback1);
    listenerSet.queueEvent(TestListener::callback2);
    listenerSet.queueEvent(TestListener::callback1);
    listenerSet.flushEvents();

    InOrder inOrder = Mockito.inOrder(listener1, listener2);
    inOrder.verify(listener1).callback1();
    inOrder.verify(listener2).callback1();
    inOrder.verify(listener1).callback2();
    inOrder.verify(listener2).callback2();
    inOrder.verify(listener1).callback1();
    inOrder.verify(listener2).callback1();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void flushEvents_recursive_sendsEventsInCorrectOrder() {
    ListenerSet<TestListener> listenerSet = new ListenerSet<>();
    // Listener1 sends callback3 recursively when receiving callback1.
    TestListener listener1 =
        spy(
            new TestListener() {
              @Override
              public void callback1() {
                listenerSet.queueEvent(TestListener::callback3);
                listenerSet.flushEvents();
              }
            });
    TestListener listener2 = mock(TestListener.class);
    listenerSet.add(listener1);
    listenerSet.add(listener2);

    listenerSet.queueEvent(TestListener::callback1);
    listenerSet.queueEvent(TestListener::callback2);
    listenerSet.flushEvents();

    InOrder inOrder = Mockito.inOrder(listener1, listener2);
    inOrder.verify(listener1).callback1();
    inOrder.verify(listener2).callback1();
    inOrder.verify(listener1).callback2();
    inOrder.verify(listener2).callback2();
    inOrder.verify(listener1).callback3();
    inOrder.verify(listener2).callback3();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void add_withRecursion_onlyReceivesUpdatesForFutureEvents() {
    ListenerSet<TestListener> listenerSet = new ListenerSet<>();
    TestListener listener2 = mock(TestListener.class);
    // Listener1 adds listener2 recursively.
    TestListener listener1 =
        spy(
            new TestListener() {
              @Override
              public void callback1() {
                listenerSet.add(listener2);
              }
            });

    listenerSet.sendEvent(TestListener::callback1);
    listenerSet.add(listener1);
    // This should add listener2, but the event should not be received yet as it happened before
    // listener2 was added.
    listenerSet.sendEvent(TestListener::callback1);
    listenerSet.sendEvent(TestListener::callback1);

    verify(listener1, times(2)).callback1();
    verify(listener2).callback1();
  }

  @Test
  public void add_withQueueing_onlyReceivesUpdatesForFutureEvents() {
    ListenerSet<TestListener> listenerSet = new ListenerSet<>();
    TestListener listener1 = mock(TestListener.class);
    TestListener listener2 = mock(TestListener.class);

    // This event is flushed after listener2 was added, but shouldn't be sent to listener2 because
    // the event itself occurred before the listener was added.
    listenerSet.add(listener1);
    listenerSet.queueEvent(TestListener::callback2);
    listenerSet.add(listener2);
    listenerSet.queueEvent(TestListener::callback2);
    listenerSet.flushEvents();

    verify(listener1, times(2)).callback2();
    verify(listener2).callback2();
  }

  @Test
  public void remove_withRecursion_stopsReceivingEventsImmediately() {
    ListenerSet<TestListener> listenerSet = new ListenerSet<>();
    TestListener listener2 = mock(TestListener.class);
    // Listener1 removes listener2 recursively.
    TestListener listener1 =
        spy(
            new TestListener() {
              @Override
              public void callback1() {
                listenerSet.remove(listener2);
              }
            });
    listenerSet.add(listener1);
    listenerSet.add(listener2);

    // Listener2 shouldn't even get this event as it's removed before the event can be invoked.
    listenerSet.sendEvent(TestListener::callback1);
    listenerSet.remove(listener1);
    listenerSet.sendEvent(TestListener::callback1);

    verify(listener1).callback1();
    verify(listener2, never()).callback1();
  }

  @Test
  public void remove_withQueueing_stopsReceivingEventsImmediately() {
    ListenerSet<TestListener> listenerSet = new ListenerSet<>();
    TestListener listener1 = mock(TestListener.class);
    TestListener listener2 = mock(TestListener.class);
    listenerSet.add(listener1);
    listenerSet.add(listener2);

    // Listener1 shouldn't even get this event as it's removed before the event can be invoked.
    listenerSet.queueEvent(TestListener::callback1);
    listenerSet.remove(listener1);
    listenerSet.queueEvent(TestListener::callback1);
    listenerSet.flushEvents();

    verify(listener1, never()).callback1();
    verify(listener2, times(2)).callback1();
  }

  @Test
  public void release_stopsForwardingEventsImmediately() {
    ListenerSet<TestListener> listenerSet = new ListenerSet<>();
    TestListener listener2 = mock(TestListener.class);
    // Listener1 releases the set from within the callback.
    TestListener listener1 =
        spy(
            new TestListener() {
              @Override
              public void callback1() {
                listenerSet.release();
              }
            });
    listenerSet.add(listener1);
    listenerSet.add(listener2);

    // Listener2 shouldn't even get this event as it's released before the event can be invoked.
    listenerSet.sendEvent(TestListener::callback1);
    listenerSet.sendEvent(TestListener::callback2);

    verify(listener1).callback1();
    verify(listener2, never()).callback1();
    verify(listener1, never()).callback2();
    verify(listener2, never()).callback2();
  }

  @Test
  public void release_preventsRegisteringNewListeners() {
    ListenerSet<TestListener> listenerSet = new ListenerSet<>();
    TestListener listener = mock(TestListener.class);

    listenerSet.release();
    listenerSet.add(listener);
    listenerSet.sendEvent(TestListener::callback1);

    verify(listener, never()).callback1();
  }

  private interface TestListener {
    default void callback1() {}

    default void callback2() {}

    default void callback3() {}
  }
}
