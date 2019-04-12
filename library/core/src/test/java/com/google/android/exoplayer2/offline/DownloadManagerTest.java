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

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.offline.Download.State;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.DummyMainThread.TestRunnable;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.testutil.TestDownloadManagerListener;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

/** Tests {@link DownloadManager}. */
@RunWith(AndroidJUnit4.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class DownloadManagerTest {

  /** Used to check if condition becomes true in this time interval. */
  private static final int ASSERT_TRUE_TIMEOUT = 10000;
  /** Used to check if condition stays false for this time interval. */
  private static final int ASSERT_FALSE_TIME = 1000;
  /** Maximum retry delay in DownloadManager. */
  private static final int MAX_RETRY_DELAY = 5000;
  /** Maximum number of times a downloader can be restarted before doing a released check. */
  private static final int MAX_STARTS_BEFORE_RELEASED = 1;
  /** A manual stop reason. */
  private static final int APP_STOP_REASON = 1;
  /** The minimum number of times a task must be retried before failing. */
  private static final int MIN_RETRY_COUNT = 3;

  private Uri uri1;
  private Uri uri2;
  private Uri uri3;
  private DummyMainThread dummyMainThread;
  private DefaultDownloadIndex downloadIndex;
  private TestDownloadManagerListener downloadManagerListener;
  private FakeDownloaderFactory downloaderFactory;
  private DownloadManager downloadManager;

  @Before
  public void setUp() throws Exception {
    ShadowLog.stream = System.out;
    MockitoAnnotations.initMocks(this);
    uri1 = Uri.parse("http://abc.com/media1");
    uri2 = Uri.parse("http://abc.com/media2");
    uri3 = Uri.parse("http://abc.com/media3");
    dummyMainThread = new DummyMainThread();
    downloadIndex = new DefaultDownloadIndex(TestUtil.getTestDatabaseProvider());
    downloaderFactory = new FakeDownloaderFactory();
    setUpDownloadManager(100);
  }

  @After
  public void tearDown() throws Exception {
    releaseDownloadManager();
    dummyMainThread.release();
  }

  @Test
  public void downloadRunner_multipleInstancePerContent_throwsException() {
    boolean exceptionThrown = false;
    try {
      new DownloadRunner(uri1);
      new DownloadRunner(uri1);
      // can't put fail() here as it would be caught in the catch below.
    } catch (Throwable e) {
      exceptionThrown = true;
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  public void multipleActionsForTheSameContent_executedOnTheSameTask() {
    // Two download actions on first task
    new DownloadRunner(uri1).postDownloadAction().postDownloadAction();
    // One download, one remove actions on second task
    new DownloadRunner(uri2).postDownloadAction().postRemoveAction();
    // Two remove actions on third task
    new DownloadRunner(uri3).postRemoveAction().postRemoveAction();
  }

  @Test
  public void actionsForDifferentContent_executedOnDifferentTasks() {
    TaskWrapper task1 = new DownloadRunner(uri1).postDownloadAction().getTask();
    TaskWrapper task2 = new DownloadRunner(uri2).postDownloadAction().getTask();
    TaskWrapper task3 = new DownloadRunner(uri3).postRemoveAction().getTask();

    assertThat(task1).isNoneOf(task2, task3);
    assertThat(task2).isNotEqualTo(task3);
  }

  @Test
  public void postDownloadAction_downloads() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    TaskWrapper task = runner.postDownloadAction().getTask();
    task.assertDownloading();
    runner.getDownloader(0).unblock().assertReleased().assertStartCount(1);
    task.assertCompleted();
    runner.assertCreatedDownloaderCount(1);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void postRemoveAction_removes() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    TaskWrapper task = runner.postDownloadAction().postRemoveAction().getTask();
    task.assertRemoving();
    runner.getDownloader(1).unblock().assertReleased().assertStartCount(1);
    task.assertRemoved();
    runner.assertCreatedDownloaderCount(2);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void downloadFails_retriesThenTaskFails() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadAction();
    FakeDownloader downloader = runner.getDownloader(0);

    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      downloader.assertStarted(MAX_RETRY_DELAY).fail();
    }

    downloader.assertReleased().assertStartCount(MIN_RETRY_COUNT + 1);
    runner.getTask().assertFailed();
    downloadManagerListener.blockUntilTasksComplete();
  }

  @Test
  public void downloadFails_retries() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadAction();
    FakeDownloader downloader = runner.getDownloader(0);

    for (int i = 0; i < MIN_RETRY_COUNT; i++) {
      downloader.assertStarted(MAX_RETRY_DELAY).fail();
    }
    downloader.assertStarted(MAX_RETRY_DELAY).unblock();

    downloader.assertReleased().assertStartCount(MIN_RETRY_COUNT + 1);
    runner.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksComplete();
  }

  @Test
  public void downloadProgressOnRetry_retryCountResets() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadAction();
    FakeDownloader downloader = runner.getDownloader(0);

    int tooManyRetries = MIN_RETRY_COUNT + 10;
    for (int i = 0; i < tooManyRetries; i++) {
      downloader.increaseDownloadedByteCount();
      downloader.assertStarted(MAX_RETRY_DELAY).fail();
    }
    downloader.assertStarted(MAX_RETRY_DELAY).unblock();

    downloader.assertReleased().assertStartCount(tooManyRetries + 1);
    runner.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksComplete();
  }

  @Test
  public void removeCancelsDownload() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(0);

    runner.postDownloadAction();
    downloader1.assertStarted();
    runner.postRemoveAction();

    downloader1.assertCanceled().assertStartCount(1);
    runner.getDownloader(1).unblock().assertNotCanceled();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void downloadNotCancelRemove() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(1);

    runner.postDownloadAction().postRemoveAction();
    downloader1.assertStarted();
    runner.postDownloadAction();

    downloader1.unblock().assertNotCanceled();
    runner.getDownloader(2).unblock().assertNotCanceled();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void secondSameRemoveActionIgnored() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(1);

    runner.postDownloadAction().postRemoveAction();
    downloader1.assertStarted();
    runner.postRemoveAction();

    downloader1.unblock().assertNotCanceled();
    runner.getTask().assertRemoved();
    runner.assertCreatedDownloaderCount(2);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void differentDownloadActionsMerged() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(0);

    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);

    runner.postDownloadAction(streamKey1);
    downloader1.assertStarted();
    runner.postDownloadAction(streamKey2);

    downloader1.assertCanceled();

    FakeDownloader downloader2 = runner.getDownloader(1);
    downloader2.assertStarted();
    assertThat(downloader2.action.streamKeys).containsExactly(streamKey1, streamKey2);
    downloader2.unblock();

    runner.getTask().assertCompleted();
    runner.assertCreatedDownloaderCount(2);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void actionsForDifferentContent_executedInParallel() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1).postDownloadAction();
    DownloadRunner runner2 = new DownloadRunner(uri2).postDownloadAction();
    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);

    downloader1.assertStarted();
    downloader2.assertStarted();
    downloader1.unblock();
    downloader2.unblock();

    runner1.getTask().assertCompleted();
    runner2.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void actionsForDifferentContent_ifMaxDownloadIs1_executedSequentially() throws Throwable {
    setUpDownloadManager(1);
    DownloadRunner runner1 = new DownloadRunner(uri1).postDownloadAction();
    DownloadRunner runner2 = new DownloadRunner(uri2).postDownloadAction();
    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);

    downloader1.assertStarted();
    downloader2.assertDoesNotStart();
    runner2.getTask().assertQueued();
    downloader1.unblock();
    downloader2.assertStarted();
    downloader2.unblock();

    runner1.getTask().assertCompleted();
    runner2.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void removeActionForDifferentContent_ifMaxDownloadIs1_executedInParallel()
      throws Throwable {
    setUpDownloadManager(1);
    DownloadRunner runner1 = new DownloadRunner(uri1).postDownloadAction();
    DownloadRunner runner2 = new DownloadRunner(uri2).postDownloadAction().postRemoveAction();
    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);

    downloader1.assertStarted();
    downloader2.assertStarted();
    downloader1.unblock();
    downloader2.unblock();

    runner1.getTask().assertCompleted();
    runner2.getTask().assertRemoved();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void downloadActionFollowingRemove_ifMaxDownloadIs1_isNotStarted() throws Throwable {
    setUpDownloadManager(1);
    DownloadRunner runner1 = new DownloadRunner(uri1).postDownloadAction();
    DownloadRunner runner2 = new DownloadRunner(uri2).postDownloadAction().postRemoveAction();
    runner2.postDownloadAction();
    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);
    FakeDownloader downloader3 = runner2.getDownloader(1);

    downloader1.assertStarted();
    downloader2.assertStarted();
    downloader2.unblock();
    downloader3.assertDoesNotStart();
    downloader1.unblock();
    downloader3.assertStarted();
    downloader3.unblock();

    runner1.getTask().assertCompleted();
    runner2.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void getTasks_returnTasks() {
    TaskWrapper task1 = new DownloadRunner(uri1).postDownloadAction().getTask();
    TaskWrapper task2 = new DownloadRunner(uri2).postDownloadAction().getTask();
    TaskWrapper task3 = new DownloadRunner(uri3).postDownloadAction().postRemoveAction().getTask();

    task3.assertRemoving();
    Download[] downloads = downloadManager.getCurrentDownloads();

    assertThat(downloads).hasLength(3);
    String[] taskIds = {task1.taskId, task2.taskId, task3.taskId};
    String[] downloadIds = {downloads[0].action.id, downloads[1].action.id, downloads[2].action.id};
    assertThat(downloadIds).isEqualTo(taskIds);
  }

  @Test
  public void stopAndResume() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    DownloadRunner runner3 = new DownloadRunner(uri3);

    runner1.postDownloadAction().getTask().assertDownloading();
    runner2.postDownloadAction().postRemoveAction().getTask().assertRemoving();
    runner2.postDownloadAction();

    runOnMainThread(() -> downloadManager.stopDownloads());

    runner1.getTask().assertStopped();

    // remove actions aren't stopped.
    runner2.getDownloader(1).unblock().assertReleased();
    runner2.getTask().assertStopped();
    // Although remove2 is finished, download2 doesn't start.
    runner2.getDownloader(2).assertDoesNotStart();

    // When a new remove action is added, it cancels stopped download actions with the same media.
    runner1.postRemoveAction();
    runner1.getDownloader(1).assertStarted().unblock();
    runner1.getTask().assertRemoved();

    // New download actions can be added but they don't start.
    runner3.postDownloadAction().getDownloader(0).assertDoesNotStart();

    runOnMainThread(() -> downloadManager.startDownloads());

    runner2.getDownloader(2).assertStarted().unblock();
    runner3.getDownloader(0).assertStarted().unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void manuallyStopAndResumeSingleDownload() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1).postDownloadAction();
    TaskWrapper task = runner.getTask();

    task.assertDownloading();

    runOnMainThread(() -> downloadManager.setManualStopReason(task.taskId, APP_STOP_REASON));

    task.assertStopped();

    runOnMainThread(
        () -> downloadManager.setManualStopReason(task.taskId, Download.MANUAL_STOP_REASON_NONE));

    runner.getDownloader(1).assertStarted().unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void manuallyStoppedDownloadCanBeCancelled() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1).postDownloadAction();
    TaskWrapper task = runner.getTask();

    task.assertDownloading();

    runOnMainThread(() -> downloadManager.setManualStopReason(task.taskId, APP_STOP_REASON));

    task.assertStopped();

    runner.postRemoveAction();
    runner.getDownloader(1).assertStarted().unblock();
    task.assertRemoved();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void manuallyStoppedSingleDownload_doesNotAffectOthers() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    DownloadRunner runner3 = new DownloadRunner(uri3);

    runner1.postDownloadAction().getTask().assertDownloading();
    runner2.postDownloadAction().postRemoveAction().getTask().assertRemoving();

    runOnMainThread(
        () -> downloadManager.setManualStopReason(runner1.getTask().taskId, APP_STOP_REASON));

    runner1.getTask().assertStopped();

    // Other downloads aren't affected.
    runner2.getDownloader(1).unblock().assertReleased();

    // New download actions can be added and they start.
    runner3.postDownloadAction().getDownloader(0).assertStarted().unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void mergeAction_removingDownload_becomesRestarting() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction).setState(Download.STATE_REMOVING);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeAction(download, downloadAction, download.manualStopReason);

    Download expectedDownload = downloadBuilder.setState(Download.STATE_RESTARTING).build();
    assertEqualIgnoringTimeFields(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeAction_failedDownload_becomesQueued() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction)
            .setState(Download.STATE_FAILED)
            .setFailureReason(Download.FAILURE_REASON_UNKNOWN);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeAction(download, downloadAction, download.manualStopReason);

    Download expectedDownload =
        downloadBuilder
            .setState(Download.STATE_QUEUED)
            .setFailureReason(Download.FAILURE_REASON_NONE)
            .build();
    assertEqualIgnoringTimeFields(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeAction_stoppedDownload_staysStopped() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction)
            .setState(Download.STATE_STOPPED)
            .setManualStopReason(/* manualStopReason= */ 1);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeAction(download, downloadAction, download.manualStopReason);

    assertEqualIgnoringTimeFields(mergedDownload, download);
  }

  @Test
  public void mergeAction_manualStopReasonSetButNotStopped_becomesStopped() {
    DownloadAction downloadAction = createDownloadAction();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadAction)
            .setState(Download.STATE_COMPLETED)
            .setManualStopReason(/* manualStopReason= */ 1);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeAction(download, downloadAction, download.manualStopReason);

    Download expectedDownload = downloadBuilder.setState(Download.STATE_STOPPED).build();
    assertEqualIgnoringTimeFields(mergedDownload, expectedDownload);
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
                    ApplicationProvider.getApplicationContext(),
                    downloadIndex,
                    downloaderFactory,
                    maxActiveDownloadTasks,
                    MIN_RETRY_COUNT,
                    new Requirements(0));
            downloadManager.startDownloads();
            downloadManagerListener =
                new TestDownloadManagerListener(downloadManager, dummyMainThread);
          });
      downloadManagerListener.waitUntilInitialized();
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

  private void runOnMainThread(final TestRunnable r) {
    dummyMainThread.runTestOnMainThread(r);
  }

  private static void assertEqualIgnoringTimeFields(Download download, Download that) {
    assertThat(download.action).isEqualTo(that.action);
    assertThat(download.state).isEqualTo(that.state);
    assertThat(download.failureReason).isEqualTo(that.failureReason);
    assertThat(download.manualStopReason).isEqualTo(that.manualStopReason);
    assertThat(download.getDownloadPercentage()).isEqualTo(that.getDownloadPercentage());
    assertThat(download.getDownloadedBytes()).isEqualTo(that.getDownloadedBytes());
    assertThat(download.getTotalBytes()).isEqualTo(that.getTotalBytes());
  }

  private static DownloadAction createDownloadAction() {
    return new DownloadAction(
        "id",
        DownloadAction.TYPE_DASH,
        Uri.parse("https://www.test.com/download"),
        Collections.emptyList(),
        /* customCacheKey= */ null,
        /* data= */ null);
  }

  private final class DownloadRunner {

    private final Uri uri;
    private final String id;
    private final ArrayList<FakeDownloader> downloaders;
    private int createdDownloaderCount = 0;
    private FakeDownloader downloader;
    private final TaskWrapper taskWrapper;

    private DownloadRunner(Uri uri) {
      this.uri = uri;
      id = uri.toString();
      downloaders = new ArrayList<>();
      downloader = addDownloader();
      downloaderFactory.registerDownloadRunner(this);
      taskWrapper = new TaskWrapper(id);
    }

    private DownloadRunner postRemoveAction() {
      runOnMainThread(() -> downloadManager.removeDownload(id));
      return this;
    }

    private DownloadRunner postDownloadAction(StreamKey... keys) {
      DownloadAction downloadAction =
          new DownloadAction(
              id,
              DownloadAction.TYPE_PROGRESSIVE,
              uri,
              Arrays.asList(keys),
              /* customCacheKey= */ null,
              /* data= */ null);
      runOnMainThread(() -> downloadManager.addDownload(downloadAction));
      return this;
    }

    private synchronized FakeDownloader addDownloader() {
      FakeDownloader fakeDownloader = new FakeDownloader();
      downloaders.add(fakeDownloader);
      return fakeDownloader;
    }

    private synchronized FakeDownloader getDownloader(int index) {
      while (downloaders.size() <= index) {
        addDownloader();
      }
      return downloaders.get(index);
    }

    private synchronized Downloader createDownloader(DownloadAction action) {
      downloader = getDownloader(createdDownloaderCount++);
      downloader.action = action;
      return downloader;
    }

    private TaskWrapper getTask() {
      return taskWrapper;
    }

    private void assertCreatedDownloaderCount(int count) {
      assertThat(createdDownloaderCount).isEqualTo(count);
    }
  }

  private final class TaskWrapper {
    private final String taskId;

    private TaskWrapper(String taskId) {
      this.taskId = taskId;
    }

    private TaskWrapper assertDownloading() {
      return assertState(Download.STATE_DOWNLOADING);
    }

    private TaskWrapper assertCompleted() {
      return assertState(Download.STATE_COMPLETED);
    }

    private TaskWrapper assertRemoving() {
      return assertState(Download.STATE_REMOVING);
    }

    private TaskWrapper assertFailed() {
      return assertState(Download.STATE_FAILED);
    }

    private TaskWrapper assertQueued() {
      return assertState(Download.STATE_QUEUED);
    }

    private TaskWrapper assertStopped() {
      return assertState(Download.STATE_STOPPED);
    }

    private TaskWrapper assertState(@State int expectedState) {
      downloadManagerListener.assertState(taskId, expectedState, ASSERT_TRUE_TIMEOUT);
      return this;
    }

    private TaskWrapper assertRemoved() {
      downloadManagerListener.assertRemoved(taskId, ASSERT_TRUE_TIMEOUT);
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      return taskId.equals(((TaskWrapper) o).taskId);
    }

    @Override
    public int hashCode() {
      return taskId.hashCode();
    }
  }

  private static final class FakeDownloaderFactory implements DownloaderFactory {

    private final HashMap<Uri, DownloadRunner> downloaders;

    public FakeDownloaderFactory() {
      downloaders = new HashMap<>();
    }

    public void registerDownloadRunner(DownloadRunner downloadRunner) {
      assertThat(downloaders.put(downloadRunner.uri, downloadRunner)).isNull();
    }

    @Override
    public Downloader createDownloader(DownloadAction action) {
      return downloaders.get(action.uri).createDownloader(action);
    }
  }

  private static final class FakeDownloader implements Downloader {

    private final com.google.android.exoplayer2.util.ConditionVariable blocker;

    private DownloadAction action;
    private CountDownLatch started;
    private volatile boolean interrupted;
    private volatile boolean cancelled;
    private volatile boolean enableDownloadIOException;
    private volatile int startCount;
    private CachingCounters counters;

    private FakeDownloader() {
      this.started = new CountDownLatch(1);
      this.blocker = new com.google.android.exoplayer2.util.ConditionVariable();
      counters = new CachingCounters();
    }

    @SuppressWarnings({"NonAtomicOperationOnVolatileField", "NonAtomicVolatileUpdate"})
    @Override
    public void download() throws InterruptedException, IOException {
      // It's ok to update this directly as no other thread will update it.
      startCount++;
      started.countDown();
      block();
      if (enableDownloadIOException) {
        enableDownloadIOException = false;
        throw new IOException();
      }
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    @SuppressWarnings({"NonAtomicOperationOnVolatileField", "NonAtomicVolatileUpdate"})
    @Override
    public void remove() throws InterruptedException {
      // It's ok to update this directly as no other thread will update it.
      startCount++;
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
            interrupted = true;
            throw e;
          }
        }
      } finally {
        blocker.close();
      }
    }

    private FakeDownloader assertStarted() throws InterruptedException {
      return assertStarted(ASSERT_TRUE_TIMEOUT);
    }

    private FakeDownloader assertStarted(int timeout) throws InterruptedException {
      assertThat(started.await(timeout, TimeUnit.MILLISECONDS)).isTrue();
      started = new CountDownLatch(1);
      return this;
    }

    private FakeDownloader assertStartCount(int count) throws InterruptedException {
      assertThat(startCount).isEqualTo(count);
      return this;
    }

    private FakeDownloader assertReleased() throws InterruptedException {
      int count = 0;
      while (started.await(ASSERT_TRUE_TIMEOUT, TimeUnit.MILLISECONDS)) {
        if (count++ >= MAX_STARTS_BEFORE_RELEASED) {
          fail();
        }
        started = new CountDownLatch(1);
      }
      return this;
    }

    private FakeDownloader assertCanceled() throws InterruptedException {
      assertReleased();
      assertThat(interrupted).isTrue();
      assertThat(cancelled).isTrue();
      return this;
    }

    private FakeDownloader assertNotCanceled() throws InterruptedException {
      assertReleased();
      assertThat(interrupted).isFalse();
      assertThat(cancelled).isFalse();
      return this;
    }

    private FakeDownloader unblock() {
      blocker.open();
      return this;
    }

    private FakeDownloader fail() {
      enableDownloadIOException = true;
      return unblock();
    }

    @Override
    public long getDownloadedBytes() {
      return counters.newlyCachedBytes;
    }

    @Override
    public long getTotalBytes() {
      return counters.contentLength;
    }

    @Override
    public float getDownloadPercentage() {
      return counters.percentage;
    }

    @Override
    public CachingCounters getCounters() {
      return counters;
    }

    private void assertDoesNotStart() throws InterruptedException {
      Thread.sleep(ASSERT_FALSE_TIME);
      assertThat(started.getCount()).isEqualTo(1);
    }

    @SuppressWarnings({"NonAtomicOperationOnVolatileField", "NonAtomicVolatileUpdate"})
    private void increaseDownloadedByteCount() {
      counters.newlyCachedBytes++;
    }
  }
}
