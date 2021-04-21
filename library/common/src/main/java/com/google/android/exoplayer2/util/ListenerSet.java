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

import android.os.Looper;
import android.os.Message;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
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

  /**
   * An event sent to a listener when all other events sent during one {@link Looper} message queue
   * iteration were handled by the listener.
   *
   * @param <T> The listener type.
   */
  public interface IterationFinishedEvent<T> {

    /**
     * Invokes the iteration finished event.
     *
     * @param listener The listener to invoke the event on.
     * @param eventFlags The combined event {@link ExoFlags flags} of all events sent in this
     *     iteration.
     */
    void invoke(T listener, ExoFlags eventFlags);
  }

  private static final int MSG_ITERATION_FINISHED = 0;
  private static final int MSG_LAZY_RELEASE = 1;

  private final Clock clock;
  private final HandlerWrapper handler;
  private final IterationFinishedEvent<T> iterationFinishedEvent;
  private final CopyOnWriteArraySet<ListenerHolder<T>> listeners;
  private final ArrayDeque<Runnable> flushingEvents;
  private final ArrayDeque<Runnable> queuedEvents;

  private boolean released;

  /**
   * Creates a new listener set.
   *
   * @param looper A {@link Looper} used to call listeners on. The same {@link Looper} must be used
   *     to call all other methods of this class.
   * @param clock A {@link Clock}.
   * @param iterationFinishedEvent An {@link IterationFinishedEvent} sent when all other events sent
   *     during one {@link Looper} message queue iteration were handled by the listeners.
   */
  public ListenerSet(Looper looper, Clock clock, IterationFinishedEvent<T> iterationFinishedEvent) {
    this(
        /* listeners= */ new CopyOnWriteArraySet<>(),
        looper,
        clock,
        iterationFinishedEvent);
  }

  private ListenerSet(
      CopyOnWriteArraySet<ListenerHolder<T>> listeners,
      Looper looper,
      Clock clock,
      IterationFinishedEvent<T> iterationFinishedEvent) {
    this.clock = clock;
    this.listeners = listeners;
    this.iterationFinishedEvent = iterationFinishedEvent;
    flushingEvents = new ArrayDeque<>();
    queuedEvents = new ArrayDeque<>();
    // It's safe to use "this" because we don't send a message before exiting the constructor.
    @SuppressWarnings("methodref.receiver.bound.invalid")
    HandlerWrapper handler = clock.createHandler(looper, this::handleMessage);
    this.handler = handler;
  }

  /**
   * Copies the listener set.
   *
   * @param looper The new {@link Looper} for the copied listener set.
   * @param iterationFinishedEvent The new {@link IterationFinishedEvent} sent when all other events
   *     sent during one {@link Looper} message queue iteration were handled by the listeners.
   * @return The copied listener set.
   */
  @CheckResult
  public ListenerSet<T> copy(Looper looper, IterationFinishedEvent<T> iterationFinishedEvent) {
    return new ListenerSet<>(listeners, looper, clock, iterationFinishedEvent);
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
    listeners.add(new ListenerHolder<>(listener));
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
        listenerHolder.release(iterationFinishedEvent);
        listeners.remove(listenerHolder);
      }
    }
  }

  /**
   * Adds an event that is sent to the listeners when {@link #flushEvents} is called.
   *
   * @param eventFlag An integer indicating the type of the event, or {@link C#INDEX_UNSET} to
   *     report this event without flag.
   * @param event The event.
   */
  public void queueEvent(int eventFlag, Event<T> event) {
    CopyOnWriteArraySet<ListenerHolder<T>> listenerSnapshot = new CopyOnWriteArraySet<>(listeners);
    queuedEvents.add(
        () -> {
          for (ListenerHolder<T> holder : listenerSnapshot) {
            holder.invoke(eventFlag, event);
          }
        });
  }

  /** Notifies listeners of events previously enqueued with {@link #queueEvent(int, Event)}. */
  public void flushEvents() {
    if (queuedEvents.isEmpty()) {
      return;
    }
    if (!handler.hasMessages(MSG_ITERATION_FINISHED)) {
      handler.obtainMessage(MSG_ITERATION_FINISHED).sendToTarget();
    }
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
   * {@link #queueEvent(int, Event) Queues} a single event and immediately {@link #flushEvents()
   * flushes} the event queue to notify all listeners.
   *
   * @param eventFlag An integer flag indicating the type of the event, or {@link C#INDEX_UNSET} to
   *     report this event without flag.
   * @param event The event.
   */
  public void sendEvent(int eventFlag, Event<T> event) {
    queueEvent(eventFlag, event);
    flushEvents();
  }

  /**
   * Releases the set of listeners immediately.
   *
   * <p>This will ensure no events are sent to any listener after this method has been called.
   */
  public void release() {
    for (ListenerHolder<T> listenerHolder : listeners) {
      listenerHolder.release(iterationFinishedEvent);
    }
    listeners.clear();
    released = true;
  }

  /**
   * Releases the set of listeners after all already scheduled {@link Looper} messages were able to
   * trigger final events.
   *
   * <p>After the specified released callback event, no other events are sent to a listener.
   *
   * @param releaseEventFlag An integer flag indicating the type of the release event, or {@link
   *     C#INDEX_UNSET} to report this event without a flag.
   * @param releaseEvent The release event.
   */
  public void lazyRelease(int releaseEventFlag, Event<T> releaseEvent) {
    handler.obtainMessage(MSG_LAZY_RELEASE, releaseEventFlag, 0, releaseEvent).sendToTarget();
  }

  private boolean handleMessage(Message message) {
    if (message.what == MSG_ITERATION_FINISHED) {
      for (ListenerHolder<T> holder : listeners) {
        holder.iterationFinished(iterationFinishedEvent);
        if (handler.hasMessages(MSG_ITERATION_FINISHED)) {
          // The invocation above triggered new events (and thus scheduled a new message). We need
          // to stop here because this new message will take care of informing every listener about
          // the new update (including the ones already called here).
          break;
        }
      }
    } else if (message.what == MSG_LAZY_RELEASE) {
      int releaseEventFlag = message.arg1;
      @SuppressWarnings("unchecked")
      Event<T> releaseEvent = (Event<T>) message.obj;
      sendEvent(releaseEventFlag, releaseEvent);
      release();
    }
    return true;
  }

  private static final class ListenerHolder<T> {

    @Nonnull public final T listener;

    private ExoFlags.Builder flagsBuilder;
    private boolean needsIterationFinishedEvent;
    private boolean released;

    public ListenerHolder(@Nonnull T listener) {
      this.listener = listener;
      this.flagsBuilder = new ExoFlags.Builder();
    }

    public void release(IterationFinishedEvent<T> event) {
      released = true;
      if (needsIterationFinishedEvent) {
        event.invoke(listener, flagsBuilder.build());
      }
    }

    public void invoke(int eventFlag, Event<T> event) {
      if (!released) {
        if (eventFlag != C.INDEX_UNSET) {
          flagsBuilder.add(eventFlag);
        }
        needsIterationFinishedEvent = true;
        event.invoke(listener);
      }
    }

    public void iterationFinished(IterationFinishedEvent<T> event) {
      if (!released && needsIterationFinishedEvent) {
        // Reset flags before invoking the listener to ensure we keep all new flags that are set by
        // recursive events triggered from this callback.
        ExoFlags flagsToNotify = flagsBuilder.build();
        flagsBuilder = new ExoFlags.Builder();
        needsIterationFinishedEvent = false;
        event.invoke(listener, flagsToNotify);
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
