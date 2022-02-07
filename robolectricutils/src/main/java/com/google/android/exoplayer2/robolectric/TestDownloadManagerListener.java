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
package com.google.android.exoplayer2.robolectric;

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.createRobolectricConditionVariable;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.fail;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Allows tests to block for, and assert properties of, calls from a {@link DownloadManager} to its
 * {@link DownloadManager.Listener}.
 */
public final class TestDownloadManagerListener implements DownloadManager.Listener {

  private static final int TIMEOUT_MS = 10_000;
  private static final int STATE_REMOVED = -1;

  private final DownloadManager downloadManager;
  private final HashMap<String, LinkedBlockingQueue<Integer>> downloadStates;
  private final ConditionVariable initializedCondition;
  private final ConditionVariable idleCondition;

  private @Download.FailureReason int failureReason;

  public TestDownloadManagerListener(DownloadManager downloadManager) {
    this.downloadManager = downloadManager;
    downloadStates = new HashMap<>();
    initializedCondition = createRobolectricConditionVariable();
    idleCondition = createRobolectricConditionVariable();
    downloadManager.addListener(this);
  }

  /** Blocks until the manager is initialized. */
  public void blockUntilInitialized() throws InterruptedException {
    assertThat(initializedCondition.block(TIMEOUT_MS)).isTrue();
  }

  /** Blocks until the manager is idle. */
  public void blockUntilIdle() throws InterruptedException {
    idleCondition.close();
    // If the manager is already idle the condition will be opened by the code immediately below.
    // Else it will be opened by onIdle().
    ConditionVariable checkedOnMainThread = createRobolectricConditionVariable();
    new Handler(downloadManager.getApplicationLooper())
        .post(
            () -> {
              if (downloadManager.isIdle()) {
                idleCondition.open();
              }
              checkedOnMainThread.open();
            });
    assertThat(checkedOnMainThread.block(TIMEOUT_MS)).isTrue();
    assertThat(idleCondition.block(TIMEOUT_MS)).isTrue();
  }

  /** Blocks until the manager is idle and throws if any of the downloads failed. */
  public void blockUntilIdleAndThrowAnyFailure() throws Exception {
    blockUntilIdle();
    if (failureReason != Download.FAILURE_REASON_NONE) {
      throw new Exception("Failure reason: " + failureReason);
    }
  }

  /** Asserts that the specified download transitions to the specified state. */
  public void assertState(String id, @Download.State int state) {
    assertStateInternal(id, state);
  }

  /** Asserts that the specified download is removed. */
  public void assertRemoved(String id) {
    assertStateInternal(id, STATE_REMOVED);
  }

  // DownloadManager.Listener implementation.

  @Override
  public void onInitialized(DownloadManager downloadManager) {
    initializedCondition.open();
  }

  @Override
  public void onDownloadChanged(
      DownloadManager downloadManager, Download download, @Nullable Exception finalException) {
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
  public void onIdle(DownloadManager downloadManager) {
    idleCondition.open();
  }

  // Internal logic.

  private void assertStateInternal(String id, int expectedState) {
    while (true) {
      @Nullable Integer state = null;
      try {
        state = getStateQueue(id).poll(TIMEOUT_MS, MILLISECONDS);
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

  private LinkedBlockingQueue<Integer> getStateQueue(String id) {
    synchronized (downloadStates) {
      @Nullable LinkedBlockingQueue<Integer> stateQueue = downloadStates.get(id);
      if (stateQueue == null) {
        stateQueue = new LinkedBlockingQueue<>();
        downloadStates.put(id, stateQueue);
      }
      return stateQueue;
    }
  }
}
