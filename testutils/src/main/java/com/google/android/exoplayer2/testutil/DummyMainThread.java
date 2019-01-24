/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import com.google.android.exoplayer2.util.Util;
import java.util.concurrent.atomic.AtomicReference;

/** Helper class to simulate main/UI thread in tests. */
public final class DummyMainThread {

  /** Default timeout value used for {@link #runOnMainThread(Runnable)}. */
  public static final int TIMEOUT_MS = 10000;

  private final HandlerThread thread;
  private final Handler handler;

  public DummyMainThread() {
    thread = new HandlerThread("DummyMainThread");
    thread.start();
    handler = new Handler(thread.getLooper());
  }

  /**
   * Runs the provided {@link Runnable} on the main thread, blocking until execution completes or
   * until {@link #TIMEOUT_MS} milliseconds have passed.
   *
   * @param runnable The {@link Runnable} to run.
   */
  public void runOnMainThread(final Runnable runnable) {
    runOnMainThread(TIMEOUT_MS, runnable);
  }

  /**
   * Runs the provided {@link Runnable} on the main thread, blocking until execution completes or
   * until timeout milliseconds have passed.
   *
   * @param timeoutMs the maximum time to wait in milliseconds.
   * @param runnable The {@link Runnable} to run.
   */
  public void runOnMainThread(int timeoutMs, final Runnable runnable) {
    if (Looper.myLooper() == handler.getLooper()) {
      runnable.run();
    } else {
      ConditionVariable finishedCondition = new ConditionVariable();
      AtomicReference<Throwable> thrown = new AtomicReference<>();
      handler.post(
          () -> {
            try {
              runnable.run();
            } catch (Throwable t) {
              thrown.set(t);
            }
            finishedCondition.open();
          });
      assertThat(finishedCondition.block(timeoutMs)).isTrue();
      if (thrown.get() != null) {
        Util.sneakyThrow(thrown.get());
      }
    }
  }

  public void release() {
    thread.quit();
  }
}
