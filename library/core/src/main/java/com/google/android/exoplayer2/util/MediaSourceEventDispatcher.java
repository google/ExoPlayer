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

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;

/**
 * Event dispatcher which forwards events to a list of registered listeners.
 *
 * <p>Adds the correct {@code windowIndex} and {@code mediaPeriodId} values (and {@code
 * mediaTimeOffsetMs} if needed).
 *
 * <p>Allows listeners of any type to be registered, calls to {@link #dispatch} then provide the
 * type of listener to forward to, which is used to filter the registered listeners.
 */
// TODO: Make this final when MediaSourceEventListener.EventDispatcher is deleted.
public class MediaSourceEventDispatcher {

  /**
   * Functional interface to send an event with {@code windowIndex} and {@code mediaPeriodId}
   * attached.
   */
  public interface EventWithPeriodId<T> {

    /** Sends the event to a listener. */
    void sendTo(T listener, int windowIndex, @Nullable MediaPeriodId mediaPeriodId);
  }

  /** The timeline window index reported with the events. */
  public final int windowIndex;
  /** The {@link MediaPeriodId} reported with the events. */
  @Nullable public final MediaPeriodId mediaPeriodId;

  // TODO: Make these private when MediaSourceEventListener.EventDispatcher is deleted.
  protected final CopyOnWriteMultiset<ListenerAndHandler> listenerAndHandlers;
  // TODO: Define exactly what this means, and check it's always set correctly.
  protected final long mediaTimeOffsetMs;

  /** Creates an event dispatcher. */
  public MediaSourceEventDispatcher() {
    this(
        /* listenerAndHandlers= */ new CopyOnWriteMultiset<>(),
        /* windowIndex= */ 0,
        /* mediaPeriodId= */ null,
        /* mediaTimeOffsetMs= */ 0);
  }

  protected MediaSourceEventDispatcher(
      CopyOnWriteMultiset<ListenerAndHandler> listenerAndHandlers,
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      long mediaTimeOffsetMs) {
    this.listenerAndHandlers = listenerAndHandlers;
    this.windowIndex = windowIndex;
    this.mediaPeriodId = mediaPeriodId;
    this.mediaTimeOffsetMs = mediaTimeOffsetMs;
  }

  /**
   * Creates a view of the event dispatcher with pre-configured window index, media period id, and
   * media time offset.
   *
   * @param windowIndex The timeline window index to be reported with the events.
   * @param mediaPeriodId The {@link MediaPeriodId} to be reported with the events.
   * @param mediaTimeOffsetMs The offset to be added to all media times, in milliseconds.
   * @return A view of the event dispatcher with the pre-configured parameters.
   */
  @CheckResult
  public MediaSourceEventDispatcher withParameters(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
    return new MediaSourceEventDispatcher(
        listenerAndHandlers, windowIndex, mediaPeriodId, mediaTimeOffsetMs);
  }

  /**
   * Adds a listener to the event dispatcher.
   *
   * @param handler A handler on the which listener events will be posted.
   * @param eventListener The listener to be added.
   */
  public void addEventListener(Handler handler, Object eventListener) {
    Assertions.checkNotNull(handler);
    Assertions.checkNotNull(eventListener);
    listenerAndHandlers.add(new ListenerAndHandler(handler, eventListener));
  }

  /**
   * Removes a listener from the event dispatcher.
   *
   * @param eventListener The listener to be removed.
   */
  public void removeEventListener(Object eventListener) {
    for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
      if (listenerAndHandler.listener == eventListener) {
        listenerAndHandlers.remove(listenerAndHandler);
      }
    }
  }

  /** Dispatches {@code event} to all registered listeners of type {@code listenerClass}. */
  @SuppressWarnings("unchecked") // The cast is gated with listenerClass.isInstance()
  public <T> void dispatch(EventWithPeriodId<T> event, Class<T> listenerClass) {
    for (ListenerAndHandler listenerAndHandler : listenerAndHandlers.elementSet()) {
      if (listenerClass.isInstance(listenerAndHandler.listener)) {
        postOrRun(
            listenerAndHandler.handler,
            () -> event.sendTo((T) listenerAndHandler.listener, windowIndex, mediaPeriodId));
      }
    }
  }

  private static void postOrRun(Handler handler, Runnable runnable) {
    if (handler.getLooper() == Looper.myLooper()) {
      runnable.run();
    } else {
      handler.post(runnable);
    }
  }

  public static long adjustMediaTime(long mediaTimeUs, long mediaTimeOffsetMs) {
    long mediaTimeMs = C.usToMs(mediaTimeUs);
    return mediaTimeMs == C.TIME_UNSET ? C.TIME_UNSET : mediaTimeOffsetMs + mediaTimeMs;
  }

  /** Container class for a {@link Handler} and {@code listener} object. */
  protected static final class ListenerAndHandler {

    public final Handler handler;
    public final Object listener;

    public ListenerAndHandler(Handler handler, Object listener) {
      this.handler = handler;
      this.listener = listener;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ListenerAndHandler)) {
        return false;
      }

      // We deliberately only consider listener (and not handler) in equals() and hashcode()
      // because the handler used to process the callbacks is an implementation detail.
      ListenerAndHandler that = (ListenerAndHandler) o;
      return listener.equals(that.listener);
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }
  }
}
