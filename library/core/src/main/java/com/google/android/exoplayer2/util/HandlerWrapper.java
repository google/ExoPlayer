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
package com.google.android.exoplayer2.util;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * An interface to call through to a {@link Handler}. Instances must be created by calling {@link
 * Clock#createHandler(Looper, Handler.Callback)} on {@link Clock#DEFAULT} for all non-test cases.
 */
public interface HandlerWrapper {

  /** @see Handler#getLooper(). */
  Looper getLooper();

  /** @see Handler#obtainMessage(int). */
  Message obtainMessage(int what);

  /** @see Handler#obtainMessage(int, Object). */
  Message obtainMessage(int what, Object obj);

  /** @see Handler#obtainMessage(int, int, int). */
  Message obtainMessage(int what, int arg1, int arg2);

  /** @see Handler#obtainMessage(int, int, int, Object). */
  Message obtainMessage(int what, int arg1, int arg2, Object obj);

  /** @see Handler#sendEmptyMessage(int). */
  boolean sendEmptyMessage(int what);

  /**
   * Variant of {@code Handler#sendEmptyMessageDelayed(int, long)} which also takes a reference time
   * measured by {@code android.os.SystemClock#elapsedRealtime()} to which the delay is added.
   *
   * @param what The message identifier.
   * @param delayMs The delay in milliseconds to send the message. This delay is added to the {@code
   *     referenceTimeMs}.
   * @param referenceTimeMs The time which the delay is added to. Always measured with {@code
   *     android.os.SystemClock#elapsedRealtime()}.
   * @return Whether the message was successfully enqueued on the Handler thread.
   */
  boolean sendEmptyMessageDelayed(int what, long delayMs, long referenceTimeMs);

  /** @see Handler#removeMessages(int). */
  void removeMessages(int what);

  /** @see Handler#removeCallbacksAndMessages(Object). */
  void removeCallbacksAndMessages(Object token);

  /** @see Handler#post(Runnable). */
  boolean post(Runnable runnable);

  /** @see Handler#postDelayed(Runnable, long). */
  boolean postDelayed(Runnable runnable, long delayMs);
}
