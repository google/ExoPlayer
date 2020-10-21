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

import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.annotation.Nonnull;

/**
 * A set of listeners.
 *
 * <p>Events are guaranteed to arrive in the order in which they happened even if a new event is
 * triggered recursively from another listener.
 *
 * <p>Events are also guaranteed to be only sent to the listeners registered at the time the event
 * was enqueued and haven't been removed since.
 *
 * @param <T> The listener type.
 */
public final class ListenerSet<T> {

  /**
   * An event sent to a listener.
   *
   * @param <T> The listener type.
   */
  public interface Event<T> {

    /** Invokes the event notification on the given listener. */
    void invoke(T listener);
  }

  private final CopyOnWriteArraySet<ListenerHolder<T>> listeners;
  private final ArrayDeque<Runnable> flushingEvents;
  private final ArrayDeque<Runnable> queuedEvents;

  private boolean released;

  /** Creates the listener set. */
  public ListenerSet() {
    listeners = new CopyOnWriteArraySet<>();
    flushingEvents = new ArrayDeque<>();
    queuedEvents = new ArrayDeque<>();
  }

  /**
   * Adds a listener to the set.
   *
   * <p>If a listener is already present, it will not be added again.
   *
   * @param listener The listener to be added.
   */
  public void add(T listener) {
    if (released) {
      return;
    }
    Assertions.checkNotNull(listener);
    listeners.add(new ListenerHolder<T>(listener));
  }

  /**
   * Removes a listener from the set.
   *
   * <p>If the listener is not present, nothing happens.
   *
   * @param listener The listener to be removed.
   */
  public void remove(T listener) {
    for (ListenerHolder<T> listenerHolder : listeners) {
      if (listenerHolder.listener.equals(listener)) {
        listenerHolder.release();
        listeners.remove(listenerHolder);
      }
    }
  }

  /**
   * Adds an event that is sent to the listeners when {@link #flushEvents} is called.
   *
   * @param event The event.
   */
  public void queueEvent(Event<T> event) {
    CopyOnWriteArraySet<ListenerHolder<T>> listenerSnapshot = new CopyOnWriteArraySet<>(listeners);
    queuedEvents.add(
        () -> {
          for (ListenerHolder<T> holder : listenerSnapshot) {
            holder.invoke(event);
          }
        });
  }

  /** Notifies listeners of events previously enqueued with {@link #queueEvent(Event)}. */
  public void flushEvents() {
    boolean recursiveFlushInProgress = !flushingEvents.isEmpty();
    flushingEvents.addAll(queuedEvents);
    queuedEvents.clear();
    if (recursiveFlushInProgress) {
      // Recursive call to flush. Let the outer call handle the flush queue.
      return;
    }
    while (!flushingEvents.isEmpty()) {
      flushingEvents.peekFirst().run();
      flushingEvents.removeFirst();
    }
  }

  /**
   * {@link #queueEvent(Event) Queues} a single event and immediately {@link #flushEvents() flushes}
   * the event queue to notify all listeners.
   *
   * @param event The event.
   */
  public void sendEvent(Event<T> event) {
    queueEvent(event);
    flushEvents();
  }

  /**
   * Releases the set of listeners.
   *
   * <p>This will ensure no events are sent to any listener after this method has been called.
   */
  public void release() {
    for (ListenerHolder<T> listenerHolder : listeners) {
      listenerHolder.release();
    }
    listeners.clear();
    released = true;
  }

  private static final class ListenerHolder<T> {

    @Nonnull public final T listener;

    private boolean released;

    public ListenerHolder(@Nonnull T listener) {
      this.listener = listener;
    }

    public void release() {
      released = true;
    }

    public void invoke(Event<T> event) {
      if (!released) {
        event.invoke(listener);
      }
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      return listener.equals(((ListenerHolder<?>) other).listener);
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }
  }
}
