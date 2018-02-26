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
package com.google.android.exoplayer2.source.dash.offline;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadListener;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadState;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A {@link DownloadListener} for testing. */
/*package*/ final class TestDownloadListener implements DownloadListener {

  private static final int TIMEOUT = 1000;

  private final DownloadManager downloadManager;
  private final DummyMainThread dummyMainThread;
  private CountDownLatch downloadFinishedCondition;
  private Throwable downloadError;

  public TestDownloadListener(DownloadManager downloadManager, DummyMainThread dummyMainThread) {
    this.downloadManager = downloadManager;
    this.dummyMainThread = dummyMainThread;
  }

  @Override
  public void onStateChange(DownloadManager downloadManager, DownloadState downloadState) {
    if (downloadState.state == DownloadState.STATE_ERROR && downloadError == null) {
      downloadError = downloadState.error;
    }
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
        new Runnable() {
          @Override
          public void run() {
            if (downloadManager.isIdle()) {
              downloadFinishedCondition.countDown();
            }
          }
        });
    assertThat(downloadFinishedCondition.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    if (downloadError != null) {
      throw new Exception(downloadError);
    }
  }
}
