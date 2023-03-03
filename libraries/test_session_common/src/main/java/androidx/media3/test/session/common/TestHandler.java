/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.media3.test.session.common;

import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Handler for testing. */
public class TestHandler extends Handler {

  private static final long DEFAULT_TIMEOUT_MS = LONG_TIMEOUT_MS;

  public TestHandler(Looper looper) {
    super(looper);
  }

  /** Posts {@link Runnable} and waits until it finishes, or runs it directly on the same looper. */
  public void postAndSync(TestRunnable runnable) throws Exception {
    postAndSync(runnable, DEFAULT_TIMEOUT_MS);
  }

  /** Posts {@link Runnable} and waits until it finishes, or runs it directly on the same looper. */
  public void postAndSync(TestRunnable runnable, long timeoutMs) throws Exception {
    if (getLooper() == Looper.myLooper()) {
      runnable.run();
    } else {
      AtomicReference<Exception> exception = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);
      post(
          () -> {
            try {
              runnable.run();
            } catch (Exception e) {
              exception.set(e);
            }
            latch.countDown();
          });
      assertThat(latch.await(timeoutMs, MILLISECONDS)).isTrue();
      if (exception.get() != null) {
        throw exception.get();
      }
    }
  }

  /**
   * Posts {@link Callable} and returns the result when it finishes, or calls it directly on the
   * same looper.
   */
  public <V> V postAndSync(Callable<V> callable) throws Exception {
    return postAndSync(callable, DEFAULT_TIMEOUT_MS);
  }

  /**
   * Posts {@link Callable} and returns the result when it finishes, or calls it directly on the
   * same looper.
   */
  public <V> V postAndSync(Callable<V> callable, long timeoutMs) throws Exception {
    if (getLooper() == Looper.myLooper()) {
      return callable.call();
    } else {
      AtomicReference<V> result = new AtomicReference<>();
      AtomicReference<Exception> exception = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);
      post(
          () -> {
            try {
              result.set(callable.call());
            } catch (Exception e) {
              exception.set(e);
            }
            latch.countDown();
          });
      assertThat(latch.await(timeoutMs, MILLISECONDS)).isTrue();
      if (exception.get() != null) {
        throw exception.get();
      }
      return result.get();
    }
  }

  /** {@link Runnable} variant which can throw a checked exception. */
  public interface TestRunnable {
    void run() throws Exception;
  }
}
