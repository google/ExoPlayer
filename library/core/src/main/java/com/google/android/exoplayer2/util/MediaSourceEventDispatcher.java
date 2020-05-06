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
  protected final CopyOnWriteMultiset<ListenerInfo> listenerInfos;
  // TODO: Define exactly what this means, and check it's always set correctly.
  protected final long mediaTimeOffsetMs;

  /** Creates an event dispatcher. */
  public MediaSourceEventDispatcher() {
    this(
        /* listenerInfos= */ new CopyOnWriteMultiset<>(),
        /* windowIndex= */ 0,
        /* mediaPeriodId= */ null,
        /* mediaTimeOffsetMs= */ 0);
  }

  protected MediaSourceEventDispatcher(
      CopyOnWriteMultiset<ListenerInfo> listenerInfos,
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      long mediaTimeOffsetMs) {
    this.listenerInfos = listenerInfos;
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
        listenerInfos, windowIndex, mediaPeriodId, mediaTimeOffsetMs);
  }

  /**
   * Adds a listener to the event dispatcher.
   *
   * <p>Calls to {@link #dispatch(EventWithPeriodId, Class)} will propagate to {@code eventListener}
   * if the {@code listenerClass} types are equal.
   *
   * <p>The same listener instance can be added multiple times with different {@code listenerClass}
   * values (i.e. if the instance implements multiple listener interfaces).
   *
   * <p>Duplicate {@code {eventListener, listenerClass}} pairs are also permitted. In this case an
   * event dispatched to {@code listenerClass} will only be passed to the {@code eventListener}
   * once.
   *
   * <p><b>NOTE</b>: This doesn't interact well with hierarchies of listener interfaces. If a
   * listener is registered with a super-class type then it will only receive events dispatched
   * directly to that super-class type. Similarly, if a listener is registered with a sub-class type
   * then it will only receive events dispatched directly to that sub-class.
   *
   * @param handler A handler on the which listener events will be posted.
   * @param eventListener The listener to be added.
   * @param listenerClass The type used to register the listener. Can be a superclass of {@code
   *     eventListener}.
   */
  public <T> void addEventListener(Handler handler, T eventListener, Class<T> listenerClass) {
    Assertions.checkNotNull(handler);
    Assertions.checkNotNull(eventListener);
    listenerInfos.add(new ListenerInfo(handler, eventListener, listenerClass));
  }

  /**
   * Removes a listener from the event dispatcher.
   *
   * <p>If there are duplicate registrations of {@code {eventListener, listenerClass}} this will
   * only remove one (so events dispatched to {@code listenerClass} will still be passed to {@code
   * eventListener}).
   *
   * @param eventListener The listener to be removed.
   * @param listenerClass The listener type passed to {@link #addEventListener(Handler, Object,
   *     Class)}.
   */
  public <T> void removeEventListener(T eventListener, Class<T> listenerClass) {
    for (ListenerInfo listenerInfo : listenerInfos) {
      if (listenerInfo.listener == eventListener
          && listenerInfo.listenerClass.equals(listenerClass)) {
        listenerInfos.remove(listenerInfo);
        return;
      }
    }
  }

  /** Dispatches {@code event} to all registered listeners of type {@code listenerClass}. */
  @SuppressWarnings("unchecked") // The cast is gated with listenerClass.isInstance()
  public <T> void dispatch(EventWithPeriodId<T> event, Class<T> listenerClass) {
    for (ListenerInfo listenerInfo : listenerInfos.elementSet()) {
      if (listenerInfo.listenerClass.equals(listenerClass)) {
        postOrRun(
            listenerInfo.handler,
            () -> event.sendTo((T) listenerInfo.listener, windowIndex, mediaPeriodId));
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

  /** Container class for a {@link Handler}, {@code listener} and {@code listenerClass}. */
  protected static final class ListenerInfo {

    public final Handler handler;
    public final Object listener;
    public final Class<?> listenerClass;

    public ListenerInfo(Handler handler, Object listener, Class<?> listenerClass) {
      this.handler = handler;
      this.listener = listener;
      this.listenerClass = listenerClass;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ListenerInfo)) {
        return false;
      }

      ListenerInfo that = (ListenerInfo) o;

      // We deliberately only consider listener and listenerClass (and not handler) in equals() and
      // hashcode() because the handler used to process the callbacks is an implementation detail.
      return listener.equals(that.listener) && listenerClass.equals(that.listenerClass);
    }

    @Override
    public int hashCode() {
      int result = 31 * listener.hashCode();
      return result + 31 * listenerClass.hashCode();
    }
  }
}
