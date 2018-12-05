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

import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadManager;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A {@link DownloadManager.Listener} for testing. */
public final class TestDownloadManagerListener implements DownloadManager.Listener {

  private static final int TIMEOUT = 1000;

  private final DownloadManager downloadManager;
  private final DummyMainThread dummyMainThread;
  private final HashMap<DownloadAction, ArrayBlockingQueue<Integer>> actionStates;

  private CountDownLatch downloadFinishedCondition;
  private Throwable downloadError;

  public TestDownloadManagerListener(
      DownloadManager downloadManager, DummyMainThread dummyMainThread) {
    this.downloadManager = downloadManager;
    this.dummyMainThread = dummyMainThread;
    actionStates = new HashMap<>();
  }

  public int pollStateChange(DownloadAction action, long timeoutMs) throws InterruptedException {
    return getStateQueue(action).poll(timeoutMs, TimeUnit.MILLISECONDS);
  }

  public void clearDownloadError() {
    this.downloadError = null;
  }

  @Override
  public void onInitialized(DownloadManager downloadManager) {
    // Do nothing.
  }

  @Override
  public void onTaskStateChanged(
      DownloadManager downloadManager, DownloadManager.TaskState taskState) {
    if (taskState.state == DownloadManager.TaskState.STATE_FAILED && downloadError == null) {
      downloadError = taskState.error;
    }
    getStateQueue(taskState.action).add(taskState.state);
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
    if (downloadError != null) {
      throw new Exception(downloadError);
    }
  }

  private ArrayBlockingQueue<Integer> getStateQueue(DownloadAction action) {
    synchronized (actionStates) {
      if (!actionStates.containsKey(action)) {
        actionStates.put(action, new ArrayBlockingQueue<>(10));
      }
      return actionStates.get(action);
    }
  }
}
