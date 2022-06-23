/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.transformer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.media3.common.util.GlUtil;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper around a single thread {@link ExecutorService} for executing {@link FrameProcessingTask}
 * instances.
 *
 * <p>The wrapper handles calling {@link
 * FrameProcessor.Listener#onFrameProcessingError(FrameProcessingException)} for errors that occur
 * during these tasks.
 */
/* package */ final class FrameProcessingTaskExecutor {

  private final ExecutorService singleThreadExecutorService;
  private final FrameProcessor.Listener listener;
  private final ConcurrentLinkedQueue<Future<?>> futures;
  private final AtomicBoolean shouldCancelTasks;

  /** Creates a new instance. */
  public FrameProcessingTaskExecutor(
      ExecutorService singleThreadExecutorService, FrameProcessor.Listener listener) {
    this.singleThreadExecutorService = singleThreadExecutorService;
    this.listener = listener;

    futures = new ConcurrentLinkedQueue<>();
    shouldCancelTasks = new AtomicBoolean();
  }

  /**
   * Submits the given {@link FrameProcessingTask} to be executed after any pending tasks have
   * completed.
   */
  public void submit(FrameProcessingTask task) {
    if (shouldCancelTasks.get()) {
      return;
    }
    try {
      futures.add(submitTask(task));
    } catch (RejectedExecutionException e) {
      if (!shouldCancelTasks.getAndSet(true)) {
        listener.onFrameProcessingError(new FrameProcessingException(e));
      }
    }
  }

  /**
   * Cancels remaining tasks, runs the given release task, and shuts down the background thread.
   *
   * @param releaseTask A {@link FrameProcessingTask} to execute before shutting down the background
   *     thread.
   * @param releaseWaitTimeMs How long to wait for the release task to terminate, in milliseconds.
   * @throws InterruptedException If interrupted while releasing resources.
   */
  public void release(FrameProcessingTask releaseTask, long releaseWaitTimeMs)
      throws InterruptedException {
    shouldCancelTasks.getAndSet(true);
    while (!futures.isEmpty()) {
      futures.remove().cancel(/* mayInterruptIfRunning= */ false);
    }
    Future<?> releaseFuture = submitTask(releaseTask);
    singleThreadExecutorService.shutdown();
    try {
      if (!singleThreadExecutorService.awaitTermination(releaseWaitTimeMs, MILLISECONDS)) {
        listener.onFrameProcessingError(new FrameProcessingException("Release timed out"));
      }
      releaseFuture.get();
    } catch (ExecutionException e) {
      listener.onFrameProcessingError(new FrameProcessingException(e));
    }
  }

  private Future<?> submitTask(FrameProcessingTask glTask) {
    return singleThreadExecutorService.submit(
        () -> {
          try {
            glTask.run();
            removeFinishedFutures();
          } catch (FrameProcessingException | GlUtil.GlException | RuntimeException e) {
            listener.onFrameProcessingError(FrameProcessingException.from(e));
          }
        });
  }

  private void removeFinishedFutures() {
    while (!futures.isEmpty()) {
      if (!futures.element().isDone()) {
        return;
      }
      try {
        futures.remove().get();
      } catch (ExecutionException e) {
        listener.onFrameProcessingError(new FrameProcessingException(e));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        listener.onFrameProcessingError(new FrameProcessingException(e));
      }
    }
  }
}
