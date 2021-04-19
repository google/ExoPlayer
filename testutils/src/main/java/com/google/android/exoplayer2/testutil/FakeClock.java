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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.os.Build;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fake {@link Clock} implementation that allows to {@link #advanceTime(long) advance the time}
 * manually to trigger pending timed messages.
 *
 * <p>All timed messages sent by a {@link #createHandler(Looper, Callback) Handler} created from
 * this clock are governed by the clock's time. Messages sent through these handlers are not
 * triggered until previous messages on any thread have been handled to ensure deterministic
 * execution. Note that this includes messages sent from the main Robolectric test thread, meaning
 * that these messages are only triggered if the main test thread is idle, which can be explicitly
 * requested by calling {@code ShadowLooper.idleMainLooper()}.
 *
 * <p>The clock also sets the time of the {@link SystemClock} to match the {@link #elapsedRealtime()
 * clock's time}.
 */
public class FakeClock implements Clock {

  private static long messageIdProvider = 0;

  private final boolean isRobolectric;
  private final boolean isAutoAdvancing;

  @GuardedBy("this")
  private final List<HandlerMessage> handlerMessages;

  @GuardedBy("this")
  private final Set<Looper> busyLoopers;

  @GuardedBy("this")
  private final long bootTimeMs;

  @GuardedBy("this")
  private long timeSinceBootMs;

  @GuardedBy("this")
  private boolean waitingForMessage;

  /**
   * Creates a fake clock that doesn't auto-advance and assumes that the system was booted exactly
   * at time {@code 0} (the Unix Epoch) and {@code initialTimeMs} milliseconds have passed since
   * system boot.
   *
   * @param initialTimeMs The initial elapsed time since the boot time, in milliseconds.
   */
  public FakeClock(long initialTimeMs) {
    this(/* bootTimeMs= */ 0, initialTimeMs, /* isAutoAdvancing= */ false);
  }

  /**
   * Creates a fake clock that assumes that the system was booted exactly at time {@code 0} (the
   * Unix Epoch) and an {@code initialTimeMs} of {@code 0}.
   *
   * @param isAutoAdvancing Whether the clock should automatically advance the time to the time of
   *     next message that is due to be sent.
   */
  public FakeClock(boolean isAutoAdvancing) {
    this(/* bootTimeMs= */ 0, /* initialTimeMs= */ 0, isAutoAdvancing);
  }

  /**
   * Creates a fake clock that assumes that the system was booted exactly at time {@code 0} (the
   * Unix Epoch) and {@code initialTimeMs} milliseconds have passed since system boot.
   *
   * @param initialTimeMs The initial elapsed time since the boot time, in milliseconds.
   * @param isAutoAdvancing Whether the clock should automatically advance the time to the time of
   *     next message that is due to be sent.
   */
  public FakeClock(long initialTimeMs, boolean isAutoAdvancing) {
    this(/* bootTimeMs= */ 0, initialTimeMs, isAutoAdvancing);
  }

  /**
   * Creates a fake clock specifying when the system was booted and how much time has passed since
   * then.
   *
   * @param bootTimeMs The time the system was booted since the Unix Epoch, in milliseconds.
   * @param initialTimeMs The initial elapsed time since the boot time, in milliseconds.
   * @param isAutoAdvancing Whether the clock should automatically advance the time to the time of
   *     next message that is due to be sent.
   */
  public FakeClock(long bootTimeMs, long initialTimeMs, boolean isAutoAdvancing) {
    this.bootTimeMs = bootTimeMs;
    this.timeSinceBootMs = initialTimeMs;
    this.isAutoAdvancing = isAutoAdvancing;
    this.handlerMessages = new ArrayList<>();
    this.busyLoopers = new HashSet<>();
    this.isRobolectric = "robolectric".equals(Build.FINGERPRINT);
    if (isRobolectric) {
      SystemClock.setCurrentTimeMillis(initialTimeMs);
    }
  }

  /**
   * Advance timestamp of {@link FakeClock} by the specified duration.
   *
   * @param timeDiffMs The amount of time to add to the timestamp in milliseconds.
   */
  public synchronized void advanceTime(long timeDiffMs) {
    advanceTimeInternal(timeDiffMs);
    maybeTriggerMessage();
  }

  @Override
  public synchronized long currentTimeMillis() {
    return bootTimeMs + timeSinceBootMs;
  }

  @Override
  public synchronized long elapsedRealtime() {
    return timeSinceBootMs;
  }

  @Override
  public long uptimeMillis() {
    return elapsedRealtime();
  }

  @Override
  public HandlerWrapper createHandler(Looper looper, @Nullable Callback callback) {
    return new ClockHandler(looper, callback);
  }

  @Override
  public synchronized void onThreadBlocked() {
    @Nullable Looper currentLooper = Looper.myLooper();
    if (currentLooper == null || !waitingForMessage) {
      // This isn't a looper message created by this class, so no need to handle the blocking.
      return;
    }
    busyLoopers.add(checkNotNull(Looper.myLooper()));
    waitingForMessage = false;
    maybeTriggerMessage();
  }

  /** Adds a message to the list of pending messages. */
  protected synchronized void addPendingHandlerMessage(HandlerMessage message) {
    handlerMessages.add(message);
    if (!waitingForMessage) {
      // This method isn't executed from inside a looper message created by this class.
      @Nullable Looper currentLooper = Looper.myLooper();
      if (currentLooper == null) {
        // This message is triggered from a non-looper thread, so just execute it directly.
        maybeTriggerMessage();
      } else {
        // Make sure the current looper message is finished before handling the new message.
        waitingForMessage = true;
        new Handler(checkNotNull(Looper.myLooper())).post(this::onMessageHandled);
      }
    }
  }

  private synchronized void removePendingHandlerMessages(ClockHandler handler, int what) {
    for (int i = handlerMessages.size() - 1; i >= 0; i--) {
      HandlerMessage message = handlerMessages.get(i);
      if (message.handler.equals(handler) && message.what == what) {
        handlerMessages.remove(i);
      }
    }
    handler.handler.removeMessages(what);
  }

  private synchronized void removePendingHandlerMessages(
      ClockHandler handler, @Nullable Object token) {
    for (int i = handlerMessages.size() - 1; i >= 0; i--) {
      HandlerMessage message = handlerMessages.get(i);
      if (message.handler.equals(handler) && (token == null || message.obj == token)) {
        handlerMessages.remove(i);
      }
    }
    handler.handler.removeCallbacksAndMessages(token);
  }

  private synchronized boolean hasPendingMessage(ClockHandler handler, int what) {
    for (int i = 0; i < handlerMessages.size(); i++) {
      HandlerMessage message = handlerMessages.get(i);
      if (message.handler.equals(handler) && message.what == what) {
        return true;
      }
    }
    return handler.handler.hasMessages(what);
  }

  private synchronized void maybeTriggerMessage() {
    if (waitingForMessage) {
      return;
    }
    if (handlerMessages.isEmpty()) {
      return;
    }
    Collections.sort(handlerMessages);
    int messageIndex = 0;
    HandlerMessage message = handlerMessages.get(messageIndex);
    int messageCount = handlerMessages.size();
    while (busyLoopers.contains(message.handler.getLooper()) && messageIndex < messageCount) {
      messageIndex++;
      if (messageIndex == messageCount) {
        return;
      }
      message = handlerMessages.get(messageIndex);
    }
    if (message.timeMs > timeSinceBootMs) {
      if (isAutoAdvancing) {
        advanceTimeInternal(message.timeMs - timeSinceBootMs);
      } else {
        return;
      }
    }
    handlerMessages.remove(messageIndex);
    waitingForMessage = true;
    if (message.runnable != null) {
      message.handler.handler.post(message.runnable);
    } else {
      message
          .handler
          .handler
          .obtainMessage(message.what, message.arg1, message.arg2, message.obj)
          .sendToTarget();
    }
    message.handler.internalHandler.post(this::onMessageHandled);
  }

  private synchronized void onMessageHandled() {
    busyLoopers.remove(Looper.myLooper());
    waitingForMessage = false;
    maybeTriggerMessage();
  }

  private synchronized void advanceTimeInternal(long timeDiffMs) {
    timeSinceBootMs += timeDiffMs;
    if (isRobolectric) {
      SystemClock.setCurrentTimeMillis(timeSinceBootMs);
    }
  }

  private static synchronized long getNextMessageId() {
    return messageIdProvider++;
  }

  /** Message data saved to send messages or execute runnables at a later time on a Handler. */
  protected final class HandlerMessage
      implements Comparable<HandlerMessage>, HandlerWrapper.Message {

    private final long messageId;
    private final long timeMs;
    private final ClockHandler handler;
    @Nullable private final Runnable runnable;
    private final int what;
    private final int arg1;
    private final int arg2;
    @Nullable private final Object obj;

    public HandlerMessage(
        long timeMs,
        ClockHandler handler,
        int what,
        int arg1,
        int arg2,
        @Nullable Object obj,
        @Nullable Runnable runnable) {
      this.messageId = getNextMessageId();
      this.timeMs = timeMs;
      this.handler = handler;
      this.runnable = runnable;
      this.what = what;
      this.arg1 = arg1;
      this.arg2 = arg2;
      this.obj = obj;
    }

    /** Returns the time of the message, in milliseconds since boot. */
    /* package */ long getTimeMs() {
      return timeMs;
    }

    @Override
    public void sendToTarget() {
      addPendingHandlerMessage(/* message= */ this);
    }

    @Override
    public HandlerWrapper getTarget() {
      return handler;
    }

    @Override
    public int compareTo(HandlerMessage other) {
      return ComparisonChain.start()
          .compare(this.timeMs, other.timeMs)
          .compare(
              this.messageId,
              other.messageId,
              timeMs == Long.MIN_VALUE ? Ordering.natural().reverse() : Ordering.natural())
          .result();
    }
  }

  /** HandlerWrapper implementation using the enclosing Clock to schedule delayed messages. */
  private final class ClockHandler implements HandlerWrapper {

    public final Handler handler;
    public final Handler internalHandler;

    public ClockHandler(Looper looper, @Nullable Callback callback) {
      handler = new Handler(looper, callback);
      internalHandler = new Handler(looper);
    }

    @Override
    public Looper getLooper() {
      return handler.getLooper();
    }

    @Override
    public boolean hasMessages(int what) {
      return hasPendingMessage(/* handler= */ this, what);
    }

    @Override
    public Message obtainMessage(int what) {
      return obtainMessage(what, /* obj= */ null);
    }

    @Override
    public Message obtainMessage(int what, @Nullable Object obj) {
      return obtainMessage(what, /* arg1= */ 0, /* arg2= */ 0, obj);
    }

    @Override
    public Message obtainMessage(int what, int arg1, int arg2) {
      return obtainMessage(what, arg1, arg2, /* obj= */ null);
    }

    @Override
    public Message obtainMessage(int what, int arg1, int arg2, @Nullable Object obj) {
      return new HandlerMessage(
          uptimeMillis(), /* handler= */ this, what, arg1, arg2, obj, /* runnable= */ null);
    }

    @Override
    public boolean sendMessageAtFrontOfQueue(Message msg) {
      HandlerMessage message = (HandlerMessage) msg;
      new HandlerMessage(
              /* timeMs= */ Long.MIN_VALUE,
              /* handler= */ this,
              message.what,
              message.arg1,
              message.arg2,
              message.obj,
              message.runnable)
          .sendToTarget();
      return true;
    }

    @Override
    public boolean sendEmptyMessage(int what) {
      return sendEmptyMessageAtTime(what, uptimeMillis());
    }

    @Override
    public boolean sendEmptyMessageDelayed(int what, int delayMs) {
      return sendEmptyMessageAtTime(what, uptimeMillis() + delayMs);
    }

    @Override
    public boolean sendEmptyMessageAtTime(int what, long uptimeMs) {
      new HandlerMessage(
              uptimeMs,
              /* handler= */ this,
              what,
              /* arg1= */ 0,
              /* arg2= */ 0,
              /* obj= */ null,
              /* runnable= */ null)
          .sendToTarget();
      return true;
    }

    @Override
    public void removeMessages(int what) {
      removePendingHandlerMessages(/* handler= */ this, what);
    }

    @Override
    public void removeCallbacksAndMessages(@Nullable Object token) {
      removePendingHandlerMessages(/* handler= */ this, token);
    }

    @Override
    public boolean post(Runnable runnable) {
      return postDelayed(runnable, /* delayMs= */ 0);
    }

    @Override
    public boolean postDelayed(Runnable runnable, long delayMs) {
      postRunnableAtTime(runnable, uptimeMillis() + delayMs);
      return true;
    }

    @Override
    public boolean postAtFrontOfQueue(Runnable runnable) {
      postRunnableAtTime(runnable, /* timeMs= */ Long.MIN_VALUE);
      return true;
    }

    private void postRunnableAtTime(Runnable runnable, long timeMs) {
      new HandlerMessage(
              timeMs,
              /* handler= */ this,
              /* what= */ 0,
              /* arg1= */ 0,
              /* arg2= */ 0,
              /* obj= */ null,
              runnable)
          .sendToTarget();
    }
  }
}



