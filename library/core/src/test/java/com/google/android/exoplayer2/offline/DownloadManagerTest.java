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
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.util.Util;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
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
  private DownloadManager downloadManager;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    uri1 = Uri.parse("http://abc.com/media1");
    uri2 = Uri.parse("http://abc.com/media2");
    uri3 = Uri.parse("http://abc.com/media3");
    dummyMainThread = new DummyMainThread();
    actionFile = Util.createTempFile(RuntimeEnvironment.application, "ExoPlayerTest");
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
    doTestActionRuns(createDownloadAction(uri1));
  }

  @Test
  public void testRemoveActionRuns() throws Throwable {
    doTestActionRuns(createRemoveAction(uri1));
  }

  @Test
  public void testDownloadRetriesThenFails() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction(uri1);
    downloadAction.post();
    FakeDownloader fakeDownloader = downloadAction.getFakeDownloader();
    fakeDownloader.enableDownloadIOException = true;
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      fakeDownloader.assertStarted(MAX_RETRY_DELAY).unblock();
    }
    downloadAction.assertFailed();
    downloadManagerListener.clearDownloadError();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDownloadNoRetryWhenCanceled() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction(uri1).ignoreInterrupts();
    downloadAction.getFakeDownloader().enableDownloadIOException = true;
    downloadAction.post().assertStarted();

    FakeDownloadAction removeAction = createRemoveAction(uri1).post();

    downloadAction.unblock().assertCanceled();
    removeAction.unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDownloadRetriesThenContinues() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction(uri1);
    downloadAction.post();
    FakeDownloader fakeDownloader = downloadAction.getFakeDownloader();
    fakeDownloader.enableDownloadIOException = true;
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      fakeDownloader.assertStarted(MAX_RETRY_DELAY);
      if (i == MIN_RETRY_COUNT) {
        fakeDownloader.enableDownloadIOException = false;
      }
      fakeDownloader.unblock();
    }
    downloadAction.assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  @SuppressWarnings({"NonAtomicVolatileUpdate", "NonAtomicOperationOnVolatileField"})
  public void testDownloadRetryCountResetsOnProgress() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction(uri1);
    downloadAction.post();
    FakeDownloader fakeDownloader = downloadAction.getFakeDownloader();
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
    downloadAction.assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDifferentMediaDownloadActionsStartInParallel() throws Throwable {
    doTestActionsRunInParallel(createDownloadAction(uri1), createDownloadAction(uri2));
  }

  @Test
  public void testDifferentMediaDifferentActionsStartInParallel() throws Throwable {
    doTestActionsRunInParallel(createDownloadAction(uri1), createRemoveAction(uri2));
  }

  @Test
  public void testSameMediaDownloadActionsStartInParallel() throws Throwable {
    doTestActionsRunInParallel(createDownloadAction(uri1), createDownloadAction(uri1));
  }

  @Test
  public void testSameMediaRemoveActionWaitsDownloadAction() throws Throwable {
    doTestActionsRunSequentially(createDownloadAction(uri1), createRemoveAction(uri1));
  }

  @Test
  public void testSameMediaDownloadActionWaitsRemoveAction() throws Throwable {
    doTestActionsRunSequentially(createRemoveAction(uri1), createDownloadAction(uri1));
  }

  @Test
  public void testSameMediaRemoveActionWaitsRemoveAction() throws Throwable {
    doTestActionsRunSequentially(createRemoveAction(uri1), createRemoveAction(uri1));
  }

  @Test
  public void testSameMediaMultipleActions() throws Throwable {
    FakeDownloadAction downloadAction1 = createDownloadAction(uri1).ignoreInterrupts();
    FakeDownloadAction downloadAction2 = createDownloadAction(uri1).ignoreInterrupts();
    FakeDownloadAction removeAction1 = createRemoveAction(uri1);
    FakeDownloadAction downloadAction3 = createDownloadAction(uri1);
    FakeDownloadAction removeAction2 = createRemoveAction(uri1);

    // Two download actions run in parallel.
    downloadAction1.post().assertStarted();
    downloadAction2.post().assertStarted();
    // removeAction1 is added. It interrupts the two download actions' threads but they are
    // configured to ignore it so removeAction1 doesn't start.
    removeAction1.post().assertDoesNotStart();

    // downloadAction2 finishes but it isn't enough to start removeAction1.
    downloadAction2.unblock().assertCanceled();
    removeAction1.assertDoesNotStart();
    // downloadAction3 is post to DownloadManager but it waits for removeAction1 to finish.
    downloadAction3.post().assertDoesNotStart();

    // When downloadAction1 finishes, removeAction1 starts.
    downloadAction1.unblock().assertCanceled();
    removeAction1.assertStarted();
    // downloadAction3 still waits removeAction1
    downloadAction3.assertDoesNotStart();

    // removeAction2 is posted. removeAction1 and downloadAction3 is canceled so removeAction2
    // starts immediately.
    removeAction2.post();
    removeAction1.assertCanceled();
    downloadAction3.assertCanceled();
    removeAction2.assertStarted().unblock().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testMultipleRemoveActionWaitsLastCancelsAllOther() throws Throwable {
    FakeDownloadAction removeAction1 = createRemoveAction(uri1).ignoreInterrupts();
    FakeDownloadAction removeAction2 = createRemoveAction(uri1);
    FakeDownloadAction removeAction3 = createRemoveAction(uri1);

    removeAction1.post().assertStarted();
    removeAction2.post().assertDoesNotStart();
    removeAction3.post().assertDoesNotStart();

    removeAction2.assertCanceled();

    removeAction1.unblock().assertCanceled();
    removeAction3.assertStarted().unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testGetTasks() throws Throwable {
    FakeDownloadAction removeAction = createRemoveAction(uri1);
    FakeDownloadAction downloadAction1 = createDownloadAction(uri1);
    FakeDownloadAction downloadAction2 = createDownloadAction(uri1);

    removeAction.post().assertStarted();
    downloadAction1.post().assertDoesNotStart();
    downloadAction2.post().assertDoesNotStart();

    TaskState[] states = downloadManager.getAllTaskStates();
    assertThat(states).hasLength(3);
    assertThat(states[0].action).isEqualTo(removeAction);
    assertThat(states[1].action).isEqualTo(downloadAction1);
    assertThat(states[2].action).isEqualTo(downloadAction2);
  }

  @Test
  public void testMultipleWaitingDownloadActionStartsInParallel() throws Throwable {
    FakeDownloadAction removeAction = createRemoveAction(uri1);
    FakeDownloadAction downloadAction1 = createDownloadAction(uri1);
    FakeDownloadAction downloadAction2 = createDownloadAction(uri1);

    removeAction.post().assertStarted();
    downloadAction1.post().assertDoesNotStart();
    downloadAction2.post().assertDoesNotStart();

    removeAction.unblock().assertCompleted();
    downloadAction1.assertStarted();
    downloadAction2.assertStarted();
    downloadAction1.unblock().assertCompleted();
    downloadAction2.unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDifferentMediaDownloadActionsPreserveOrder() throws Throwable {
    FakeDownloadAction removeAction = createRemoveAction(uri1).ignoreInterrupts();
    FakeDownloadAction downloadAction1 = createDownloadAction(uri1);
    FakeDownloadAction downloadAction2 = createDownloadAction(uri2);

    removeAction.post().assertStarted();
    downloadAction1.post().assertDoesNotStart();
    downloadAction2.post().assertDoesNotStart();

    removeAction.unblock().assertCompleted();
    downloadAction1.assertStarted();
    downloadAction2.assertStarted();
    downloadAction1.unblock().assertCompleted();
    downloadAction2.unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDifferentMediaRemoveActionsDoNotPreserveOrder() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction(uri1).ignoreInterrupts();
    FakeDownloadAction removeAction1 = createRemoveAction(uri1);
    FakeDownloadAction removeAction2 = createRemoveAction(uri2);

    downloadAction.post().assertStarted();
    removeAction1.post().assertDoesNotStart();
    removeAction2.post().assertStarted();

    downloadAction.unblock().assertCanceled();
    removeAction2.unblock().assertCompleted();

    removeAction1.assertStarted();
    removeAction1.unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testStopAndResume() throws Throwable {
    FakeDownloadAction download1Action = createDownloadAction(uri1);
    FakeDownloadAction remove2Action = createRemoveAction(uri2);
    FakeDownloadAction download2Action = createDownloadAction(uri2);
    FakeDownloadAction remove1Action = createRemoveAction(uri1);
    FakeDownloadAction download3Action = createDownloadAction(uri3);

    download1Action.post().assertStarted();
    remove2Action.post().assertStarted();
    download2Action.post().assertDoesNotStart();

    runOnMainThread(() -> downloadManager.stopDownloads());

    download1Action.assertStopped();

    // remove actions aren't stopped.
    remove2Action.unblock().assertCompleted();
    // Although remove2Action is finished, download2Action doesn't start.
    download2Action.assertDoesNotStart();

    // When a new remove action is added, it cancels stopped download actions with the same media.
    remove1Action.post();
    download1Action.assertCanceled();
    remove1Action.assertStarted().unblock().assertCompleted();

    // New download actions can be added but they don't start.
    download3Action.post().assertDoesNotStart();

    runOnMainThread(() -> downloadManager.startDownloads());

    download2Action.assertStarted().unblock().assertCompleted();
    download3Action.assertStarted().unblock().assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testResumeBeforeTotallyStopped() throws Throwable {
    setUpDownloadManager(2);
    FakeDownloadAction download1Action = createDownloadAction(uri1).ignoreInterrupts();
    FakeDownloadAction download2Action = createDownloadAction(uri2);
    FakeDownloadAction download3Action = createDownloadAction(uri3);

    download1Action.post().assertStarted();
    download2Action.post().assertStarted();
    // download3Action doesn't start as DM was configured to run two downloads in parallel.
    download3Action.post().assertDoesNotStart();

    runOnMainThread(() -> downloadManager.stopDownloads());

    // download1Action doesn't stop yet as it ignores interrupts.
    download2Action.assertStopped();

    runOnMainThread(() -> downloadManager.startDownloads());

    // download2Action starts immediately.
    download2Action.assertStarted();

    // download3Action doesn't start as download1Action still holds its slot.
    download3Action.assertDoesNotStart();

    // when unblocked download1Action stops and starts immediately.
    download1Action.unblock().assertStopped().assertStarted();

    download1Action.unblock();
    download2Action.unblock();
    download3Action.unblock();

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
                    new DownloaderConstructorHelper(
                        Mockito.mock(Cache.class), DummyDataSource.FACTORY),
                    maxActiveDownloadTasks,
                    MIN_RETRY_COUNT,
                    actionFile,
                    ProgressiveDownloadAction.DESERIALIZER);
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

  private void doTestActionRuns(FakeDownloadAction action) throws Throwable {
    action.post().assertStarted().unblock().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void doTestActionsRunSequentially(FakeDownloadAction action1, FakeDownloadAction action2)
      throws Throwable {
    action1.ignoreInterrupts().post().assertStarted();
    action2.post().assertDoesNotStart();

    action1.unblock();
    action2.assertStarted();

    action2.unblock().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void doTestActionsRunInParallel(FakeDownloadAction action1, FakeDownloadAction action2)
      throws Throwable {
    action1.post().assertStarted();
    action2.post().assertStarted();
    action1.unblock().assertCompleted();
    action2.unblock().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private FakeDownloadAction createDownloadAction(Uri uri) {
    return new FakeDownloadAction(uri, /* isRemoveAction= */ false);
  }

  private FakeDownloadAction createRemoveAction(Uri uri) {
    return new FakeDownloadAction(uri, /* isRemoveAction= */ true);
  }

  private void runOnMainThread(final Runnable r) {
    dummyMainThread.runOnMainThread(r);
  }

  private class FakeDownloadAction extends DownloadAction {

    private final FakeDownloader downloader;

    private FakeDownloadAction(Uri uri, boolean isRemoveAction) {
      super("Fake", /* version= */ 0, uri, isRemoveAction, /* data= */ null);
      this.downloader = new FakeDownloader(isRemoveAction);
    }

    @Override
    protected void writeToStream(DataOutputStream output) {
      // do nothing.
    }

    @Override
    public Downloader createDownloader(DownloaderConstructorHelper downloaderConstructorHelper) {
      return downloader;
    }

    private FakeDownloader getFakeDownloader() {
      return downloader;
    }

    private FakeDownloadAction post() {
      runOnMainThread(() -> downloadManager.handleAction(FakeDownloadAction.this));
      return this;
    }

    private FakeDownloadAction assertDoesNotStart() throws InterruptedException {
      Thread.sleep(ASSERT_FALSE_TIME);
      assertThat(downloader.started.getCount()).isEqualTo(1);
      return this;
    }

    private FakeDownloadAction assertStarted() throws InterruptedException {
      downloader.assertStarted(ASSERT_TRUE_TIMEOUT);
      return assertState(TaskState.STATE_STARTED);
    }

    private FakeDownloadAction assertCompleted() {
      return assertState(TaskState.STATE_COMPLETED);
    }

    private FakeDownloadAction assertFailed() {
      return assertState(TaskState.STATE_FAILED);
    }

    private FakeDownloadAction assertCanceled() {
      return assertState(TaskState.STATE_CANCELED);
    }

    private FakeDownloadAction assertStopped() {
      return assertState(TaskState.STATE_QUEUED);
    }

    private FakeDownloadAction assertState(@State int expectedState) {
      while (true) {
        Integer state = null;
        try {
          state = downloadManagerListener.pollStateChange(this, ASSERT_TRUE_TIMEOUT);
        } catch (InterruptedException e) {
          fail(e.getMessage());
        }
        if (expectedState == state) {
          return this;
        }
      }
    }

    private FakeDownloadAction unblock() {
      downloader.unblock();
      return this;
    }

    private FakeDownloadAction ignoreInterrupts() {
      downloader.ignoreInterrupts = true;
      return this;
    }
  }

  private static class FakeDownloader implements Downloader {

    private final com.google.android.exoplayer2.util.ConditionVariable blocker;
    private final boolean isRemoveAction;

    private CountDownLatch started;
    private boolean ignoreInterrupts;
    private volatile boolean enableDownloadIOException;
    private volatile int downloadedBytes = C.LENGTH_UNSET;

    private FakeDownloader(boolean isRemoveAction) {
      this.isRemoveAction = isRemoveAction;
      this.started = new CountDownLatch(1);
      this.blocker = new com.google.android.exoplayer2.util.ConditionVariable();
    }

    @Override
    public void download() throws InterruptedException, IOException {
      assertThat(isRemoveAction).isFalse();
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
      assertThat(isRemoveAction).isTrue();
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
    public float getDownloadPercentage() {
      return Float.NaN;
    }
  }
}
