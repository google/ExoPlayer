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
import android.support.annotation.Nullable;

/**
 * An interface to call through to an {@link Handler}. The {@link Factory#DEFAULT} factory must be
 * used for all non-test cases.
 */
public interface HandlerWrapper {

  /** A factory for handler instances. */
  interface Factory {

    /** Default HandlerWrapper factory to use for all non-test cases. */
    Factory DEFAULT = new SystemHandler.Factory();

    /**
     * Creates a HandlerWrapper running a specified looper and using a specified callback for
     * messages.
     *
     * @see Handler#Handler(Looper, Handler.Callback).
     */
    HandlerWrapper createHandler(Looper looper, @Nullable Handler.Callback callback);
  }

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

  /** @see Handler#sendEmptyMessageDelayed(int, long). */
  boolean sendEmptyMessageDelayed(int what, long delayMs);

  /** @see Handler#removeMessages(int). */
  void removeMessages(int what);

  /** @see Handler#removeCallbacksAndMessages(Object). */
  void removeCallbacksAndMessages(Object token);

  /** @see Handler#post(Runnable). */
  boolean post(Runnable runnable);

  /** @see Handler#postDelayed(Runnable, long). */
  boolean postDelayed(Runnable runnable, long delayMs);
}
