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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.Download.State;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.util.Util;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link DownloadManager.Listener} for testing. */
public final class TestDownloadManagerListener implements DownloadManager.Listener {

  private static final int TIMEOUT_MS = 1000;
  private static final int INITIALIZATION_TIMEOUT_MS = 10_000;
  private static final int STATE_REMOVED = -1;

  private final DownloadManager downloadManager;
  private final DummyMainThread dummyMainThread;
  private final HashMap<String, ArrayBlockingQueue<Integer>> downloadStates;
  private final CountDownLatch initializedCondition;
  private final int timeoutMs;

  private @MonotonicNonNull CountDownLatch downloadFinishedCondition;
  @Download.FailureReason private int failureReason;

  public TestDownloadManagerListener(
      DownloadManager downloadManager, DummyMainThread dummyMainThread) {
    this(downloadManager, dummyMainThread, TIMEOUT_MS);
  }

  public TestDownloadManagerListener(
      DownloadManager downloadManager, DummyMainThread dummyMainThread, int timeoutMs) {
    this.downloadManager = downloadManager;
    this.dummyMainThread = dummyMainThread;
    this.timeoutMs = timeoutMs;
    downloadStates = new HashMap<>();
    initializedCondition = new CountDownLatch(1);
    downloadManager.addListener(this);
  }

  @Nullable
  public Integer pollStateChange(String taskId, long timeoutMs) throws InterruptedException {
    return getStateQueue(taskId).poll(timeoutMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public void onInitialized(DownloadManager downloadManager) {
    initializedCondition.countDown();
  }

  public void waitUntilInitialized() throws InterruptedException {
    if (!downloadManager.isInitialized()) {
      assertThat(initializedCondition.await(INITIALIZATION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          .isTrue();
    }
  }

  @Override
  public void onDownloadChanged(DownloadManager downloadManager, Download download) {
    if (download.state == Download.STATE_FAILED) {
      failureReason = download.failureReason;
    }
    getStateQueue(download.request.id).add(download.state);
  }

  @Override
  public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
    getStateQueue(download.request.id).add(STATE_REMOVED);
  }

  @Override
  public synchronized void onIdle(DownloadManager downloadManager) {
    if (downloadFinishedCondition != null) {
      downloadFinishedCondition.countDown();
    }
  }

  /**
   * Blocks until all remove and download tasks are complete and throws an exception if there was an
   * error.
   */
  public void blockUntilTasksCompleteAndThrowAnyDownloadError() throws Throwable {
    blockUntilTasksComplete();
    if (failureReason != Download.FAILURE_REASON_NONE) {
      throw new Exception("Failure reason: " + failureReason);
    }
  }

  /** Blocks until all remove and download tasks are complete. Task errors are ignored. */
  public void blockUntilTasksComplete() throws InterruptedException {
    synchronized (this) {
      downloadFinishedCondition = new CountDownLatch(1);
    }
    dummyMainThread.runOnMainThread(
        () -> {
          if (downloadManager.isIdle()) {
            Util.castNonNull(downloadFinishedCondition).countDown();
          }
        });
    assertThat(downloadFinishedCondition.await(timeoutMs, TimeUnit.MILLISECONDS)).isTrue();
  }

  private ArrayBlockingQueue<Integer> getStateQueue(String taskId) {
    synchronized (downloadStates) {
      @Nullable ArrayBlockingQueue<Integer> stateQueue = downloadStates.get(taskId);
      if (stateQueue == null) {
        stateQueue = new ArrayBlockingQueue<>(10);
        downloadStates.put(taskId, stateQueue);
      }
      return stateQueue;
    }
  }

  public void assertRemoved(String taskId, int timeoutMs) {
    assertStateInternal(taskId, STATE_REMOVED, timeoutMs);
  }

  public void assertState(String taskId, @State int expectedState, int timeoutMs) {
    assertStateInternal(taskId, expectedState, timeoutMs);
  }

  private void assertStateInternal(String taskId, int expectedState, int timeoutMs) {
    while (true) {
      @Nullable Integer state = null;
      try {
        state = pollStateChange(taskId, timeoutMs);
      } catch (InterruptedException e) {
        fail("Interrupted: " + e.getMessage());
      }
      if (state != null) {
        if (expectedState == state) {
          return;
        }
      } else {
        fail("Didn't receive expected state: " + expectedState);
      }
    }
  }
}
