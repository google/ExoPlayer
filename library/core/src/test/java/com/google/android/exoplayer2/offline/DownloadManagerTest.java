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
package com.google.android.exoplayer2.offline;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloadManager.TaskState;
import com.google.android.exoplayer2.offline.DownloadManager.TaskState.State;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.testutil.TestDownloadManagerListener;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Tests {@link DownloadManager}. */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class DownloadManagerTest {

  /* Used to check if condition becomes true in this time interval. */
  private static final int ASSERT_TRUE_TIMEOUT = 10000;
  /* Used to check if condition stays false for this time interval. */
  private static final int ASSERT_FALSE_TIME = 1000;
  /* Maximum retry delay in DownloadManager. */
  private static final int MAX_RETRY_DELAY = 5000;

  private static final int MIN_RETRY_COUNT = 3;

  private Uri uri1;
  private Uri uri2;
  private Uri uri3;
  private DummyMainThread dummyMainThread;
  private File actionFile;
  private TestDownloadManagerListener downloadManagerListener;
  private FakeDownloaderFactory downloaderFactory;
  private DownloadManager downloadManager;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    uri1 = Uri.parse("http://abc.com/media1");
    uri2 = Uri.parse("http://abc.com/media2");
    uri3 = Uri.parse("http://abc.com/media3");
    dummyMainThread = new DummyMainThread();
    actionFile = Util.createTempFile(RuntimeEnvironment.application, "ExoPlayerTest");
    downloaderFactory = new FakeDownloaderFactory();
    setUpDownloadManager(100);
  }

  @After
  public void tearDown() throws Exception {
    releaseDownloadManager();
    actionFile.delete();
    dummyMainThread.release();
  }

  @Test
  public void testDownloadActionRuns() throws Throwable {
    doTestDownloaderRuns(createDownloadRunner(uri1));
  }

  @Test
  public void testRemoveActionRuns() throws Throwable {
    doTestDownloaderRuns(createRemoveRunner(uri1));
  }

  @Test
  public void testDownloadRetriesThenFails() throws Throwable {
    DownloadRunner downloadRunner = createDownloadRunner(uri1);
    downloadRunner.postAction();
    FakeDownloader fakeDownloader = downloadRunner.downloader;
    fakeDownloader.enableDownloadIOException = true;
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      fakeDownloader.assertStarted(MAX_RETRY_DELAY).unblock();
    }
    downloadRunner.assertFailed();
    downloadManagerListener.clearDownloadError();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDownloadNoRetryWhenCanceled() throws Throwable {
    DownloadRunner downloadRunner = createDownloadRunner(uri1).ignoreInterrupts();
    downloadRunner.downloader.enableDownloadIOException = true;
    downloadRunner.postAction().assertStarted();

    DownloadRunner removeRunner = createRemoveRunner(uri1).postAction();

    downloadRunner.unblock().assertCanceled();
    removeRunner.unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDownloadRetriesThenContinues() throws Throwable {
    DownloadRunner downloadRunner = createDownloadRunner(uri1);
    downloadRunner.postAction();
    FakeDownloader fakeDownloader = downloadRunner.downloader;
    fakeDownloader.enableDownloadIOException = true;
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      fakeDownloader.assertStarted(MAX_RETRY_DELAY);
      if (i == MIN_RETRY_COUNT) {
        fakeDownloader.enableDownloadIOException = false;
      }
      fakeDownloader.unblock();
    }
    downloadRunner.assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  @SuppressWarnings({"NonAtomicVolatileUpdate", "NonAtomicOperationOnVolatileField"})
  public void testDownloadRetryCountResetsOnProgress() throws Throwable {
    DownloadRunner downloadRunner = createDownloadRunner(uri1);
    downloadRunner.postAction();
    FakeDownloader fakeDownloader = downloadRunner.downloader;
    fakeDownloader.enableDownloadIOException = true;
    fakeDownloader.downloadedBytes = 0;
    for (int i = 0; i <= MIN_RETRY_COUNT + 10; i++) {
      fakeDownloader.assertStarted(MAX_RETRY_DELAY);
      fakeDownloader.downloadedBytes++;
      if (i == MIN_RETRY_COUNT + 10) {
        fakeDownloader.enableDownloadIOException = false;
      }
      fakeDownloader.unblock();
    }
    downloadRunner.assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDifferentMediaDownloadActionsStartInParallel() throws Throwable {
    doTestDownloadersRunInParallel(createDownloadRunner(uri1), createDownloadRunner(uri2));
  }

  @Test
  public void testDifferentMediaDifferentActionsStartInParallel() throws Throwable {
    doTestDownloadersRunInParallel(createDownloadRunner(uri1), createRemoveRunner(uri2));
  }

  @Test
  public void testSameMediaDownloadActionsStartInParallel() throws Throwable {
    doTestDownloadersRunInParallel(createDownloadRunner(uri1), createDownloadRunner(uri1));
  }

  @Test
  public void testSameMediaRemoveActionWaitsDownloadAction() throws Throwable {
    doTestDownloadersRunSequentially(createDownloadRunner(uri1), createRemoveRunner(uri1));
  }

  @Test
  public void testSameMediaDownloadActionWaitsRemoveAction() throws Throwable {
    doTestDownloadersRunSequentially(createRemoveRunner(uri1), createDownloadRunner(uri1));
  }

  @Test
  public void testSameMediaRemoveActionWaitsRemoveAction() throws Throwable {
    doTestDownloadersRunSequentially(createRemoveRunner(uri1), createRemoveRunner(uri1));
  }

  @Test
  public void testSameMediaMultipleActions() throws Throwable {
    DownloadRunner downloadAction1 = createDownloadRunner(uri1).ignoreInterrupts();
    DownloadRunner downloadAction2 = createDownloadRunner(uri1).ignoreInterrupts();
    DownloadRunner removeAction1 = createRemoveRunner(uri1);
    DownloadRunner downloadAction3 = createDownloadRunner(uri1);
    DownloadRunner removeAction2 = createRemoveRunner(uri1);

    // Two download actions run in parallel.
    downloadAction1.postAction().assertStarted();
    downloadAction2.postAction().assertStarted();
    // removeAction1 is added. It interrupts the two download actions' threads but they are
    // configured to ignore it so removeAction1 doesn't start.
    removeAction1.postAction().assertDoesNotStart();

    // downloadAction2 finishes but it isn't enough to start removeAction1.
    downloadAction2.unblock().assertCanceled();
    removeAction1.assertDoesNotStart();
    // downloadAction3 is postAction to DownloadManager but it waits for removeAction1 to finish.
    downloadAction3.postAction().assertDoesNotStart();

    // When downloadAction1 finishes, removeAction1 starts.
    downloadAction1.unblock().assertCanceled();
    removeAction1.assertStarted();
    // downloadAction3 still waits removeAction1
    downloadAction3.assertDoesNotStart();

    // removeAction2 is posted. removeAction1 and downloadAction3 is canceled so removeAction2
    // starts immediately.
    removeAction2.postAction();
    removeAction1.assertCanceled();
    downloadAction3.assertCanceled();
    removeAction2.assertStarted().unblock().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testMultipleRemoveActionWaitsLastCancelsAllOther() throws Throwable {
    DownloadRunner removeAction1 = createRemoveRunner(uri1).ignoreInterrupts();
    DownloadRunner removeAction2 = createRemoveRunner(uri1);
    DownloadRunner removeAction3 = createRemoveRunner(uri1);

    removeAction1.postAction().assertStarted();
    removeAction2.postAction().assertDoesNotStart();
    removeAction3.postAction().assertDoesNotStart();

    removeAction2.assertCanceled();

    removeAction1.unblock().assertCanceled();
    removeAction3.assertStarted().unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testGetTasks() throws Throwable {
    DownloadRunner removeAction = createRemoveRunner(uri1);
    DownloadRunner downloadAction1 = createDownloadRunner(uri1);
    DownloadRunner downloadAction2 = createDownloadRunner(uri1);

    removeAction.postAction().assertStarted();
    downloadAction1.postAction().assertDoesNotStart();
    downloadAction2.postAction().assertDoesNotStart();

    TaskState[] states = downloadManager.getAllTaskStates();
    assertThat(states).hasLength(3);
    assertThat(states[0].action).isEqualTo(removeAction.action);
    assertThat(states[1].action).isEqualTo(downloadAction1.action);
    assertThat(states[2].action).isEqualTo(downloadAction2.action);
  }

  @Test
  public void testMultipleWaitingDownloadActionStartsInParallel() throws Throwable {
    DownloadRunner removeAction = createRemoveRunner(uri1);
    DownloadRunner downloadAction1 = createDownloadRunner(uri1);
    DownloadRunner downloadAction2 = createDownloadRunner(uri1);

    removeAction.postAction().assertStarted();
    downloadAction1.postAction().assertDoesNotStart();
    downloadAction2.postAction().assertDoesNotStart();

    removeAction.unblock().assertCompleted();
    downloadAction1.assertStarted();
    downloadAction2.assertStarted();
    downloadAction1.unblock().assertCompleted();
    downloadAction2.unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDifferentMediaDownloadActionsPreserveOrder() throws Throwable {
    DownloadRunner removeRunner = createRemoveRunner(uri1).ignoreInterrupts();
    DownloadRunner downloadRunner1 = createDownloadRunner(uri1);
    DownloadRunner downloadRunner2 = createDownloadRunner(uri2);

    removeRunner.postAction().assertStarted();
    downloadRunner1.postAction().assertDoesNotStart();
    downloadRunner2.postAction().assertDoesNotStart();

    removeRunner.unblock().assertCompleted();
    downloadRunner1.assertStarted();
    downloadRunner2.assertStarted();
    downloadRunner1.unblock().assertCompleted();
    downloadRunner2.unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDifferentMediaRemoveActionsDoNotPreserveOrder() throws Throwable {
    DownloadRunner downloadRunner = createDownloadRunner(uri1).ignoreInterrupts();
    DownloadRunner removeRunner1 = createRemoveRunner(uri1);
    DownloadRunner removeRunner2 = createRemoveRunner(uri2);

    downloadRunner.postAction().assertStarted();
    removeRunner1.postAction().assertDoesNotStart();
    removeRunner2.postAction().assertStarted();

    downloadRunner.unblock().assertCanceled();
    removeRunner2.unblock().assertCompleted();

    removeRunner1.assertStarted();
    removeRunner1.unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testStopAndResume() throws Throwable {
    DownloadRunner download1Runner = createDownloadRunner(uri1);
    DownloadRunner remove2Runner = createRemoveRunner(uri2);
    DownloadRunner download2Runner = createDownloadRunner(uri2);
    DownloadRunner remove1Runner = createRemoveRunner(uri1);
    DownloadRunner download3Runner = createDownloadRunner(uri3);

    download1Runner.postAction().assertStarted();
    remove2Runner.postAction().assertStarted();
    download2Runner.postAction().assertDoesNotStart();

    runOnMainThread(() -> downloadManager.stopDownloads());

    download1Runner.assertStopped();

    // remove actions aren't stopped.
    remove2Runner.unblock().assertCompleted();
    // Although remove2 is finished, download2 doesn't start.
    download2Runner.assertDoesNotStart();

    // When a new remove action is added, it cancels stopped download actions with the same media.
    remove1Runner.postAction();
    download1Runner.assertCanceled();
    remove1Runner.assertStarted().unblock().assertCompleted();

    // New download actions can be added but they don't start.
    download3Runner.postAction().assertDoesNotStart();

    runOnMainThread(() -> downloadManager.startDownloads());

    download2Runner.assertStarted().unblock().assertCompleted();
    download3Runner.assertStarted().unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testResumeBeforeTotallyStopped() throws Throwable {
    setUpDownloadManager(2);
    DownloadRunner download1Runner = createDownloadRunner(uri1).ignoreInterrupts();
    DownloadRunner download2Runner = createDownloadRunner(uri2);
    DownloadRunner download3Runner = createDownloadRunner(uri3);

    download1Runner.postAction().assertStarted();
    download2Runner.postAction().assertStarted();
    // download3 doesn't start as DM was configured to run two downloads in parallel.
    download3Runner.postAction().assertDoesNotStart();

    runOnMainThread(() -> downloadManager.stopDownloads());

    // download1 doesn't stop yet as it ignores interrupts.
    download2Runner.assertStopped();

    runOnMainThread(() -> downloadManager.startDownloads());

    // download2 starts immediately.
    download2Runner.assertStarted();

    // download3 doesn't start as download1 still holds its slot.
    download3Runner.assertDoesNotStart();

    // when unblocked download1 stops and starts immediately.
    download1Runner.unblock().assertStopped().assertStarted();

    download1Runner.unblock();
    download2Runner.unblock();
    download3Runner.unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void setUpDownloadManager(final int maxActiveDownloadTasks) throws Exception {
    if (downloadManager != null) {
      releaseDownloadManager();
    }
    try {
      runOnMainThread(
          () -> {
            downloadManager =
                new DownloadManager(
                    actionFile, downloaderFactory, maxActiveDownloadTasks, MIN_RETRY_COUNT);
            downloadManagerListener =
                new TestDownloadManagerListener(downloadManager, dummyMainThread);
            downloadManager.addListener(downloadManagerListener);
            downloadManager.startDownloads();
          });
    } catch (Throwable throwable) {
      throw new Exception(throwable);
    }
  }

  private void releaseDownloadManager() throws Exception {
    try {
      runOnMainThread(() -> downloadManager.release());
    } catch (Throwable throwable) {
      throw new Exception(throwable);
    }
  }

  private void doTestDownloaderRuns(DownloadRunner runner) throws Throwable {
    runner.postAction().assertStarted().unblock().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void doTestDownloadersRunSequentially(DownloadRunner runner1, DownloadRunner runner2)
      throws Throwable {
    runner1.ignoreInterrupts().postAction().assertStarted();
    runner2.postAction().assertDoesNotStart();

    runner1.unblock();
    runner2.assertStarted();

    runner2.unblock().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void doTestDownloadersRunInParallel(DownloadRunner runner1, DownloadRunner runner2)
      throws Throwable {
    runner1.postAction().assertStarted();
    runner2.postAction().assertStarted();
    runner1.unblock().assertCompleted();
    runner2.unblock().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private DownloadRunner createDownloadRunner(Uri uri) {
    return new DownloadRunner(uri, /* isRemoveAction= */ false);
  }

  private DownloadRunner createRemoveRunner(Uri uri) {
    return new DownloadRunner(uri, /* isRemoveAction= */ true);
  }

  private void runOnMainThread(final Runnable r) {
    dummyMainThread.runOnMainThread(r);
  }

  private class DownloadRunner {

    public final DownloadAction action;
    public final FakeDownloader downloader;

    private DownloadRunner(Uri uri, boolean isRemoveAction) {
      action =
          isRemoveAction
              ? DownloadAction.createRemoveAction(
                  DownloadAction.TYPE_PROGRESSIVE, uri, /* customCacheKey= */ null)
              : DownloadAction.createDownloadAction(
                  DownloadAction.TYPE_PROGRESSIVE,
                  uri,
                  /* keys= */ Collections.emptyList(),
                  /* customCacheKey= */ null,
                  /* data= */ null);
      downloader = new FakeDownloader(isRemoveAction);
      downloaderFactory.putFakeDownloader(action, downloader);
    }

    private DownloadRunner postAction() {
      runOnMainThread(() -> downloadManager.handleAction(action));
      return this;
    }

    private DownloadRunner assertDoesNotStart() throws InterruptedException {
      Thread.sleep(ASSERT_FALSE_TIME);
      assertThat(downloader.started.getCount()).isEqualTo(1);
      return this;
    }

    private DownloadRunner assertStarted() throws InterruptedException {
      downloader.assertStarted(ASSERT_TRUE_TIMEOUT);
      return assertState(TaskState.STATE_STARTED);
    }

    private DownloadRunner assertCompleted() {
      return assertState(TaskState.STATE_COMPLETED);
    }

    private DownloadRunner assertFailed() {
      return assertState(TaskState.STATE_FAILED);
    }

    private DownloadRunner assertCanceled() {
      return assertState(TaskState.STATE_CANCELED);
    }

    private DownloadRunner assertStopped() {
      return assertState(TaskState.STATE_QUEUED);
    }

    private DownloadRunner assertState(@State int expectedState) {
      while (true) {
        Integer state = null;
        try {
          state = downloadManagerListener.pollStateChange(action, ASSERT_TRUE_TIMEOUT);
        } catch (InterruptedException e) {
          fail(e.getMessage());
        }
        if (expectedState == state) {
          return this;
        }
      }
    }

    private DownloadRunner unblock() {
      downloader.unblock();
      return this;
    }

    private DownloadRunner ignoreInterrupts() {
      downloader.ignoreInterrupts = true;
      return this;
    }
  }

  private static class FakeDownloaderFactory implements DownloaderFactory {

    public IdentityHashMap<DownloadAction, FakeDownloader> downloaders;

    public FakeDownloaderFactory() {
      downloaders = new IdentityHashMap<>();
    }

    public void putFakeDownloader(DownloadAction action, FakeDownloader downloader) {
      downloaders.put(action, downloader);
    }

    @Override
    public Downloader createDownloader(DownloadAction action) {
      return downloaders.get(action);
    }
  }

  private static class FakeDownloader implements Downloader {

    private final com.google.android.exoplayer2.util.ConditionVariable blocker;
    private final boolean isRemove;

    private CountDownLatch started;
    private boolean ignoreInterrupts;
    private volatile boolean enableDownloadIOException;
    private volatile int downloadedBytes = C.LENGTH_UNSET;

    private FakeDownloader(boolean isRemove) {
      this.isRemove = isRemove;
      this.started = new CountDownLatch(1);
      this.blocker = new com.google.android.exoplayer2.util.ConditionVariable();
    }

    @Override
    public void download() throws InterruptedException, IOException {
      assertThat(isRemove).isFalse();
      started.countDown();
      block();
      if (enableDownloadIOException) {
        throw new IOException();
      }
    }

    @Override
    public void cancel() {
      // Do nothing.
    }

    @Override
    public void remove() throws InterruptedException {
      assertThat(isRemove).isTrue();
      started.countDown();
      block();
    }

    private void block() throws InterruptedException {
      try {
        while (true) {
          try {
            blocker.block();
            break;
          } catch (InterruptedException e) {
            if (!ignoreInterrupts) {
              throw e;
            }
          }
        }
      } finally {
        blocker.close();
      }
    }

    private FakeDownloader assertStarted(int timeout) throws InterruptedException {
      assertThat(started.await(timeout, TimeUnit.MILLISECONDS)).isTrue();
      started = new CountDownLatch(1);
      return this;
    }

    private FakeDownloader unblock() {
      blocker.open();
      return this;
    }

    @Override
    public long getDownloadedBytes() {
      return downloadedBytes;
    }

    @Override
    public long getTotalBytes() {
      return C.LENGTH_UNSET;
    }

    @Override
    public float getDownloadPercentage() {
      return C.PERCENTAGE_UNSET;
    }
  }
}
