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
import com.google.android.exoplayer2.offline.DownloadState.State;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.testutil.TestDownloadManagerListener;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
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
import org.robolectric.shadows.ShadowLog;

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
  /* Maximum number of times a downloader can be restarted before doing a released check. */
  private static final int MAX_STARTS_BEFORE_RELEASED = 1;
  /** The minimum number of times a task must be retried before failing. */
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
    ShadowLog.stream = System.out;
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
  public void downloadRunner_handleActionReturnsDifferentTaskId_throwsException() {
    DownloadRunner runner = new DownloadRunner(uri1).postDownloadAction();
    TaskWrapper task = runner.getTask();
    runner.setTask(new TaskWrapper(task.taskId + 10000));
    boolean exceptionThrown = false;
    try {
      runner.postDownloadAction();
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
    TaskWrapper task = runner.postRemoveAction().getTask();
    task.assertRemoving();
    runner.getDownloader(0).unblock().assertReleased().assertStartCount(1);
    task.assertRemoved();
    runner.assertCreatedDownloaderCount(1);
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
    FakeDownloader downloader1 = runner.getDownloader(0);

    runner.postRemoveAction();
    downloader1.assertStarted();
    runner.postDownloadAction();

    downloader1.unblock().assertNotCanceled();
    runner.getDownloader(1).unblock().assertNotCanceled();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void secondSameRemoveActionIgnored() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(0);

    runner.postRemoveAction();
    downloader1.assertStarted();
    runner.postRemoveAction();

    downloader1.unblock().assertNotCanceled();
    runner.getTask().assertRemoved();
    runner.assertCreatedDownloaderCount(1);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void secondSameDownloadActionIgnored() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(0);

    runner.postDownloadAction();
    downloader1.assertStarted();
    runner.postDownloadAction();

    downloader1.unblock().assertNotCanceled();
    runner.getTask().assertCompleted();
    runner.assertCreatedDownloaderCount(1);
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

    downloader1.unblock().assertCanceled();

    FakeDownloader downloader2 = runner.getDownloader(1);
    downloader2.assertStarted();
    assertThat(downloader2.action.keys).containsExactly(streamKey1, streamKey2);
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
    DownloadRunner runner2 = new DownloadRunner(uri2).postRemoveAction();
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
    DownloadRunner runner2 = new DownloadRunner(uri2).postRemoveAction().postDownloadAction();
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
    TaskWrapper task3 = new DownloadRunner(uri3).postRemoveAction().getTask();

    DownloadState[] states = downloadManager.getAllDownloadStates();

    assertThat(states).hasLength(3);
    String[] taskIds = {task1.taskId, task2.taskId, task3.taskId};
    String[] stateTaskIds = {states[0].id, states[1].id, states[2].id};
    assertThat(stateTaskIds).isEqualTo(taskIds);
  }

  @Test
  public void stopAndResume() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    DownloadRunner runner3 = new DownloadRunner(uri3);

    runner1.postDownloadAction().getTask().assertDownloading();
    runner2.postRemoveAction().getTask().assertRemoving();
    runner2.postDownloadAction();

    runOnMainThread(() -> downloadManager.stopDownloads());

    runner1.getTask().assertStopped();

    // remove actions aren't stopped.
    runner2.getDownloader(0).unblock().assertReleased();
    runner2.getTask().assertStopped();
    // Although remove2 is finished, download2 doesn't start.
    runner2.getDownloader(1).assertDoesNotStart();

    // When a new remove action is added, it cancels stopped download actions with the same media.
    runner1.postRemoveAction();
    runner1.getDownloader(1).assertStarted().unblock();
    runner1.getTask().assertRemoved();

    // New download actions can be added but they don't start.
    runner3.postDownloadAction().getDownloader(0).assertDoesNotStart();

    runOnMainThread(() -> downloadManager.startDownloads());

    runner2.getDownloader(1).assertStarted().unblock();
    runner3.getDownloader(0).assertStarted().unblock();

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
                    RuntimeEnvironment.application,
                    actionFile,
                    downloaderFactory,
                    maxActiveDownloadTasks,
                    MIN_RETRY_COUNT,
                    new Requirements(0));
            downloadManagerListener =
                new TestDownloadManagerListener(downloadManager, dummyMainThread);
            downloadManager.startDownloads();
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

  private void runOnMainThread(final Runnable r) {
    dummyMainThread.runOnMainThread(r);
  }

  private final class DownloadRunner {

    private final Uri uri;
    private final ArrayList<FakeDownloader> downloaders;
    private int createdDownloaderCount = 0;
    private FakeDownloader downloader;
    private TaskWrapper taskWrapper;

    private DownloadRunner(Uri uri) {
      this.uri = uri;
      downloaders = new ArrayList<>();
      downloader = addDownloader();
      downloaderFactory.registerDownloadRunner(this);
    }

    private DownloadRunner postRemoveAction() {
      return postAction(createRemoveAction(uri));
    }

    private DownloadRunner postDownloadAction(StreamKey... keys) {
      return postAction(createDownloadAction(uri, keys));
    }

    private DownloadRunner postAction(DownloadAction action) {
      runOnMainThread(() -> downloadManager.handleAction(action));
      if (taskWrapper == null) {
        taskWrapper = new TaskWrapper(action.id);
      } else {
        assertThat(action.id).isEqualTo(taskWrapper.taskId);
      }
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

    public void setTask(TaskWrapper taskWrapper) {
      this.taskWrapper = taskWrapper;
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
      return assertState(DownloadState.STATE_DOWNLOADING);
    }

    private TaskWrapper assertCompleted() {
      return assertState(DownloadState.STATE_COMPLETED);
    }

    private TaskWrapper assertRemoving() {
      return assertState(DownloadState.STATE_REMOVING);
    }

    private TaskWrapper assertRemoved() {
      return assertState(DownloadState.STATE_REMOVED);
    }

    private TaskWrapper assertFailed() {
      return assertState(DownloadState.STATE_FAILED);
    }

    private TaskWrapper assertQueued() {
      return assertState(DownloadState.STATE_QUEUED);
    }

    private TaskWrapper assertStopped() {
      return assertState(DownloadState.STATE_STOPPED);
    }

    private TaskWrapper assertState(@State int expectedState) {
      ArrayList<Integer> receivedStates = new ArrayList<>();
      while (true) {
        Integer state = null;
        try {
          state = downloadManagerListener.pollStateChange(taskId, ASSERT_TRUE_TIMEOUT);
        } catch (InterruptedException e) {
          fail(e.getMessage());
        }
        if (state != null) {
          if (expectedState == state) {
            return this;
          }
          receivedStates.add(state);
        } else {
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < receivedStates.size(); i++) {
            if (i > 0) {
              sb.append(',');
            }
            sb.append(DownloadState.getStateString(receivedStates.get(i)));
          }
          fail(
              String.format(
                  Locale.US,
                  "expected:<%s> but was:<%s>",
                  DownloadState.getStateString(expectedState),
                  sb));
        }
      }
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

  private static DownloadAction createDownloadAction(Uri uri, StreamKey... keys) {
    return DownloadAction.createDownloadAction(
        DownloadAction.TYPE_PROGRESSIVE,
        uri,
        Arrays.asList(keys),
        /* customCacheKey= */ null,
        /* data= */ null);
  }

  private static DownloadAction createRemoveAction(Uri uri) {
    return DownloadAction.createRemoveAction(
        DownloadAction.TYPE_PROGRESSIVE, uri, /* customCacheKey= */ null);
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
    private volatile int downloadedBytes;
    private volatile int startCount;

    private FakeDownloader() {
      this.started = new CountDownLatch(1);
      this.blocker = new com.google.android.exoplayer2.util.ConditionVariable();
      downloadedBytes = C.LENGTH_UNSET;
    }

    @SuppressWarnings({"NonAtomicOperationOnVolatileField", "NonAtomicVolatileUpdate"})
    @Override
    public void download() throws InterruptedException, IOException {
      // It's ok to update this directly as no other thread will update it.
      startCount++;
      assertThat(action.isRemoveAction).isFalse();
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
      assertThat(action.isRemoveAction).isTrue();
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

    private void assertDoesNotStart() throws InterruptedException {
      Thread.sleep(ASSERT_FALSE_TIME);
      assertThat(started.getCount()).isEqualTo(1);
    }

    @SuppressWarnings({"NonAtomicOperationOnVolatileField", "NonAtomicVolatileUpdate"})
    private void increaseDownloadedByteCount() {
      downloadedBytes++;
    }
  }
}
