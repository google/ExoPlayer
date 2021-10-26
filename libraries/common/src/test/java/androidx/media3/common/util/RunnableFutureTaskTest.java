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
package androidx.media3.common.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link RunnableFutureTask}. */
@RunWith(AndroidJUnit4.class)
public class RunnableFutureTaskTest {

  @Test
  public void blockUntilStarted_ifNotStarted_blocks() throws InterruptedException {
    RunnableFutureTask<Void, Exception> task =
        new RunnableFutureTask<Void, Exception>() {
          @Override
          protected Void doWork() {
            return null;
          }
        };

    AtomicBoolean blockUntilStartedReturned = new AtomicBoolean();
    Thread testThread =
        new Thread() {
          @Override
          public void run() {
            task.blockUntilStarted();
            blockUntilStartedReturned.set(true);
          }
        };
    testThread.start();

    Thread.sleep(1000);
    assertThat(blockUntilStartedReturned.get()).isFalse();

    // Thread cleanup.
    task.run();
    testThread.join();
  }

  @Test(timeout = 1000)
  public void blockUntilStarted_ifStarted_unblocks() throws InterruptedException {
    ConditionVariable finish = new ConditionVariable();
    RunnableFutureTask<Void, Exception> task =
        new RunnableFutureTask<Void, Exception>() {
          @Override
          protected Void doWork() {
            finish.blockUninterruptible();
            return null;
          }
        };
    Thread testThread = new Thread(task);
    testThread.start();
    task.blockUntilStarted(); // Should unblock.

    // Thread cleanup.
    finish.open();
    testThread.join();
  }

  @Test(timeout = 1000)
  public void blockUntilStarted_ifCanceled_unblocks() {
    RunnableFutureTask<Void, Exception> task =
        new RunnableFutureTask<Void, Exception>() {
          @Override
          protected Void doWork() {
            return null;
          }
        };

    task.cancel(/* interruptIfRunning= */ false);

    // Should not block.
    task.blockUntilStarted();
  }

  @Test
  public void blockUntilFinished_ifNotFinished_blocks() throws InterruptedException {
    ConditionVariable finish = new ConditionVariable();
    RunnableFutureTask<Void, Exception> task =
        new RunnableFutureTask<Void, Exception>() {
          @Override
          protected Void doWork() {
            finish.blockUninterruptible();
            return null;
          }
        };
    Thread testThread1 = new Thread(task);
    testThread1.start();

    AtomicBoolean blockUntilFinishedReturned = new AtomicBoolean();
    Thread testThread2 =
        new Thread() {
          @Override
          public void run() {
            task.blockUntilFinished();
            blockUntilFinishedReturned.set(true);
          }
        };
    testThread2.start();

    Thread.sleep(1000);
    assertThat(blockUntilFinishedReturned.get()).isFalse();

    // Thread cleanup.
    finish.open();
    testThread1.join();
    testThread2.join();
  }

  @Test(timeout = 1000)
  public void blockUntilFinished_ifFinished_unblocks() throws InterruptedException {
    RunnableFutureTask<Void, Exception> task =
        new RunnableFutureTask<Void, Exception>() {
          @Override
          protected Void doWork() {
            return null;
          }
        };
    Thread testThread = new Thread(task);
    testThread.start();

    task.blockUntilFinished();
    assertThat(task.isDone()).isTrue();

    // Thread cleanup.
    testThread.join();
  }

  @Test(timeout = 1000)
  public void blockUntilFinished_ifCanceled_unblocks() {
    RunnableFutureTask<Void, Exception> task =
        new RunnableFutureTask<Void, Exception>() {
          @Override
          protected Void doWork() {
            return null;
          }
        };

    task.cancel(/* interruptIfRunning= */ false);

    // Should not block.
    task.blockUntilFinished();
  }

  @Test
  public void get_ifNotFinished_blocks() throws InterruptedException {
    ConditionVariable finish = new ConditionVariable();
    RunnableFutureTask<Void, Exception> task =
        new RunnableFutureTask<Void, Exception>() {
          @Override
          protected Void doWork() {
            finish.blockUninterruptible();
            return null;
          }
        };
    Thread testThread1 = new Thread(task);
    testThread1.start();

    AtomicBoolean blockUntilGetResultReturned = new AtomicBoolean();
    Thread testThread2 =
        new Thread() {
          @Override
          public void run() {
            try {
              task.get();
            } catch (ExecutionException | InterruptedException e) {
              // Do nothing.
            } finally {
              blockUntilGetResultReturned.set(true);
            }
          }
        };
    testThread2.start();

    Thread.sleep(1000);
    assertThat(blockUntilGetResultReturned.get()).isFalse();

    // Thread cleanup.
    finish.open();
    testThread1.join();
    testThread2.join();
  }

  @Test(timeout = 1000)
  public void get_returnsResult() throws ExecutionException, InterruptedException {
    Object result = new Object();
    RunnableFutureTask<Object, Exception> task =
        new RunnableFutureTask<Object, Exception>() {
          @Override
          protected Object doWork() {
            return result;
          }
        };
    Thread testThread = new Thread(task);
    testThread.start();

    assertThat(task.get()).isSameInstanceAs(result);

    // Thread cleanup.
    testThread.join();
  }

  @Test(timeout = 1000)
  public void get_throwsExecutionException_containsIOException() throws InterruptedException {
    IOException exception = new IOException();
    RunnableFutureTask<Object, IOException> task =
        new RunnableFutureTask<Object, IOException>() {
          @Override
          protected Object doWork() throws IOException {
            throw exception;
          }
        };
    Thread testThread = new Thread(task);
    testThread.start();

    ExecutionException executionException = assertThrows(ExecutionException.class, task::get);
    assertThat(executionException).hasCauseThat().isSameInstanceAs(exception);

    // Thread cleanup.
    testThread.join();
  }

  @Test(timeout = 1000)
  public void get_throwsExecutionException_containsRuntimeException() throws InterruptedException {
    RuntimeException exception = new RuntimeException();
    RunnableFutureTask<Object, Exception> task =
        new RunnableFutureTask<Object, Exception>() {
          @Override
          protected Object doWork() {
            throw exception;
          }
        };
    Thread testThread = new Thread(task);
    testThread.start();

    ExecutionException executionException = assertThrows(ExecutionException.class, task::get);
    assertThat(executionException).hasCauseThat().isSameInstanceAs(exception);

    // Thread cleanup.
    testThread.join();
  }

  @Test
  public void run_throwsError() {
    Error error = new Error();
    RunnableFutureTask<Object, Exception> task =
        new RunnableFutureTask<Object, Exception>() {
          @Override
          protected Object doWork() {
            throw error;
          }
        };
    Error thrownError = assertThrows(Error.class, task::run);
    assertThat(thrownError).isSameInstanceAs(error);
  }

  @Test
  public void cancel_whenNotStarted_returnsTrue() {
    RunnableFutureTask<Void, Exception> task =
        new RunnableFutureTask<Void, Exception>() {
          @Override
          protected Void doWork() {
            return null;
          }
        };
    assertThat(task.cancel(/* interruptIfRunning= */ false)).isTrue();
  }

  @Test
  public void cancel_whenCanceled_returnsFalse() {
    RunnableFutureTask<Void, Exception> task =
        new RunnableFutureTask<Void, Exception>() {
          @Override
          protected Void doWork() {
            return null;
          }
        };
    task.cancel(/* interruptIfRunning= */ false);
    assertThat(task.cancel(/* interruptIfRunning= */ false)).isFalse();
  }
}
