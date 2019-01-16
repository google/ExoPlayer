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

import android.os.ConditionVariable;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadState;
import com.google.android.exoplayer2.scheduler.Requirements;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A {@link DownloadManager.Listener} for testing. */
public final class TestDownloadManagerListener implements DownloadManager.Listener {

  private static final int TIMEOUT = 1000;
  private static final int INITIALIZATION_TIMEOUT = 10000;

  private final DownloadManager downloadManager;
  private final DummyMainThread dummyMainThread;
  private final HashMap<String, ArrayBlockingQueue<Integer>> actionStates;
  private final ConditionVariable initializedCondition;

  private CountDownLatch downloadFinishedCondition;
  @DownloadState.FailureReason private int failureReason;

  public TestDownloadManagerListener(
      DownloadManager downloadManager, DummyMainThread dummyMainThread) {
    this.downloadManager = downloadManager;
    this.dummyMainThread = dummyMainThread;
    actionStates = new HashMap<>();
    initializedCondition = new ConditionVariable();
    downloadManager.addListener(this);
  }

  public Integer pollStateChange(String taskId, long timeoutMs) throws InterruptedException {
    return getStateQueue(taskId).poll(timeoutMs, TimeUnit.MILLISECONDS);
  }

  public void clearDownloadError() {
    this.failureReason = DownloadState.FAILURE_REASON_NONE;
  }

  @Override
  public void onInitialized(DownloadManager downloadManager) {
    initializedCondition.open();
  }

  public void waitUntilInitialized() {
    if (!downloadManager.isInitialized()) {
      assertThat(initializedCondition.block(INITIALIZATION_TIMEOUT)).isTrue();
    }
  }

  @Override
  public void onDownloadStateChanged(DownloadManager downloadManager, DownloadState downloadState) {
    if (downloadState.state == DownloadState.STATE_FAILED) {
      failureReason = downloadState.failureReason;
    }
    getStateQueue(downloadState.id).add(downloadState.state);
  }

  @Override
  public synchronized void onIdle(DownloadManager downloadManager) {
    if (downloadFinishedCondition != null) {
      downloadFinishedCondition.countDown();
    }
  }

  @Override
  public void onRequirementsStateChanged(
      DownloadManager downloadManager, Requirements requirements, int notMetRequirements) {
    // Do nothing.
  }

  /**
   * Blocks until all remove and download tasks are complete and throws an exception if there was an
   * error.
   */
  public void blockUntilTasksCompleteAndThrowAnyDownloadError() throws Throwable {
    blockUntilTasksComplete();
    if (failureReason != DownloadState.FAILURE_REASON_NONE) {
      throw new Exception("Failure reason: " + DownloadState.getFailureString(failureReason));
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
            downloadFinishedCondition.countDown();
          }
        });
    assertThat(downloadFinishedCondition.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
  }

  private ArrayBlockingQueue<Integer> getStateQueue(String taskId) {
    synchronized (actionStates) {
      if (!actionStates.containsKey(taskId)) {
        actionStates.put(taskId, new ArrayBlockingQueue<>(10));
      }
      return actionStates.get(taskId);
    }
  }
}
