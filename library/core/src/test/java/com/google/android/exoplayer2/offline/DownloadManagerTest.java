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

import android.os.ConditionVariable;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadListener;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadState;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadState.State;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.util.Util;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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

  private DownloadManager downloadManager;
  private File actionFile;
  private TestDownloadListener testDownloadListener;
  private DummyMainThread dummyMainThread;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    dummyMainThread = new DummyMainThread();
    actionFile = Util.createTempFile(RuntimeEnvironment.application, "ExoPlayerTest");
    testDownloadListener = new TestDownloadListener();
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
    doTestActionRuns(createDownloadAction("media 1"));
  }

  @Test
  public void testRemoveActionRuns() throws Throwable {
    doTestActionRuns(createRemoveAction("media 1"));
  }

  @Test
  public void testDownloadRetriesThenFails() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction("media 1");
    downloadAction.post();
    FakeDownloader fakeDownloader = downloadAction.getFakeDownloader();
    fakeDownloader.enableDownloadIOException = true;
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      fakeDownloader.assertStarted(MAX_RETRY_DELAY).unblock();
    }
    downloadAction.assertError();
    testDownloadListener.clearDownloadError();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDownloadNoRetryWhenCancelled() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction("media 1").ignoreInterrupts();
    downloadAction.getFakeDownloader().enableDownloadIOException = true;
    downloadAction.post().assertStarted();

    FakeDownloadAction removeAction = createRemoveAction("media 1").post();

    downloadAction.unblock().assertCancelled();
    removeAction.unblock();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDownloadRetriesThenContinues() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction("media 1");
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
    downloadAction.assertEnded();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  @SuppressWarnings({"NonAtomicVolatileUpdate", "NonAtomicOperationOnVolatileField"})
  public void testDownloadRetryCountResetsOnProgress() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction("media 1");
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
    downloadAction.assertEnded();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDifferentMediaDownloadActionsStartInParallel() throws Throwable {
    doTestActionsRunInParallel(createDownloadAction("media 1"), createDownloadAction("media 2"));
  }

  @Test
  public void testDifferentMediaDifferentActionsStartInParallel() throws Throwable {
    doTestActionsRunInParallel(createDownloadAction("media 1"), createRemoveAction("media 2"));
  }

  @Test
  public void testSameMediaDownloadActionsStartInParallel() throws Throwable {
    doTestActionsRunInParallel(createDownloadAction("media 1"), createDownloadAction("media 1"));
  }

  @Test
  public void testSameMediaRemoveActionWaitsDownloadAction() throws Throwable {
    doTestActionsRunSequentially(createDownloadAction("media 1"), createRemoveAction("media 1"));
  }

  @Test
  public void testSameMediaDownloadActionWaitsRemoveAction() throws Throwable {
    doTestActionsRunSequentially(createRemoveAction("media 1"), createDownloadAction("media 1"));
  }

  @Test
  public void testSameMediaRemoveActionWaitsRemoveAction() throws Throwable {
    doTestActionsRunSequentially(createRemoveAction("media 1"), createRemoveAction("media 1"));
  }

  @Test
  public void testSameMediaMultipleActions() throws Throwable {
    FakeDownloadAction downloadAction1 = createDownloadAction("media 1").ignoreInterrupts();
    FakeDownloadAction downloadAction2 = createDownloadAction("media 1").ignoreInterrupts();
    FakeDownloadAction removeAction1 = createRemoveAction("media 1");
    FakeDownloadAction downloadAction3 = createDownloadAction("media 1");
    FakeDownloadAction removeAction2 = createRemoveAction("media 1");

    // Two download actions run in parallel.
    downloadAction1.post().assertStarted();
    downloadAction2.post().assertStarted();
    // removeAction1 is added. It interrupts the two download actions' threads but they are
    // configured to ignore it so removeAction1 doesn't start.
    removeAction1.post().assertDoesNotStart();

    // downloadAction2 finishes but it isn't enough to start removeAction1.
    downloadAction2.unblock().assertCancelled();
    removeAction1.assertDoesNotStart();
    // downloadAction3 is post to DownloadManager but it waits for removeAction1 to finish.
    downloadAction3.post().assertDoesNotStart();

    // When downloadAction1 finishes, removeAction1 starts.
    downloadAction1.unblock().assertCancelled();
    removeAction1.assertStarted();
    // downloadAction3 still waits removeAction1
    downloadAction3.assertDoesNotStart();

    // removeAction2 is posted. removeAction1 and downloadAction3 is canceled so removeAction2
    // starts immediately.
    removeAction2.post();
    removeAction1.assertCancelled();
    downloadAction3.assertCancelled();
    removeAction2.assertStarted().unblock().assertEnded();
    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testMultipleRemoveActionWaitsLastCancelsAllOther() throws Throwable {
    FakeDownloadAction removeAction1 = createRemoveAction("media 1").ignoreInterrupts();
    FakeDownloadAction removeAction2 = createRemoveAction("media 1");
    FakeDownloadAction removeAction3 = createRemoveAction("media 1");

    removeAction1.post().assertStarted();
    removeAction2.post().assertDoesNotStart();
    removeAction3.post().assertDoesNotStart();

    removeAction2.assertCancelled();

    removeAction1.unblock().assertCancelled();
    removeAction3.assertStarted().unblock().assertEnded();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testGetTasks() throws Throwable {
    FakeDownloadAction removeAction = createRemoveAction("media 1");
    FakeDownloadAction downloadAction1 = createDownloadAction("media 1");
    FakeDownloadAction downloadAction2 = createDownloadAction("media 1");

    removeAction.post().assertStarted();
    downloadAction1.post().assertDoesNotStart();
    downloadAction2.post().assertDoesNotStart();

    DownloadState[] states = downloadManager.getDownloadStates();
    assertThat(states).hasLength(3);
    assertThat(states[0].downloadAction).isEqualTo(removeAction);
    assertThat(states[1].downloadAction).isEqualTo(downloadAction1);
    assertThat(states[2].downloadAction).isEqualTo(downloadAction2);
  }

  @Test
  public void testMultipleWaitingDownloadActionStartsInParallel() throws Throwable {
    FakeDownloadAction removeAction = createRemoveAction("media 1");
    FakeDownloadAction downloadAction1 = createDownloadAction("media 1");
    FakeDownloadAction downloadAction2 = createDownloadAction("media 1");

    removeAction.post().assertStarted();
    downloadAction1.post().assertDoesNotStart();
    downloadAction2.post().assertDoesNotStart();

    removeAction.unblock().assertEnded();
    downloadAction1.assertStarted();
    downloadAction2.assertStarted();
    downloadAction1.unblock().assertEnded();
    downloadAction2.unblock().assertEnded();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDifferentMediaDownloadActionsPreserveOrder() throws Throwable {
    FakeDownloadAction removeAction = createRemoveAction("media 1").ignoreInterrupts();
    FakeDownloadAction downloadAction1 = createDownloadAction("media 1");
    FakeDownloadAction downloadAction2 = createDownloadAction("media 2");

    removeAction.post().assertStarted();
    downloadAction1.post().assertDoesNotStart();
    downloadAction2.post().assertDoesNotStart();

    removeAction.unblock().assertEnded();
    downloadAction1.assertStarted();
    downloadAction2.assertStarted();
    downloadAction1.unblock().assertEnded();
    downloadAction2.unblock().assertEnded();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testDifferentMediaRemoveActionsDoNotPreserveOrder() throws Throwable {
    FakeDownloadAction downloadAction = createDownloadAction("media 1").ignoreInterrupts();
    FakeDownloadAction removeAction1 = createRemoveAction("media 1");
    FakeDownloadAction removeAction2 = createRemoveAction("media 2");

    downloadAction.post().assertStarted();
    removeAction1.post().assertDoesNotStart();
    removeAction2.post().assertStarted();

    downloadAction.unblock().assertCancelled();
    removeAction2.unblock().assertEnded();

    removeAction1.assertStarted();
    removeAction1.unblock().assertEnded();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testStopAndResume() throws Throwable {
    FakeDownloadAction download1Action = createDownloadAction("media 1");
    FakeDownloadAction remove2Action = createRemoveAction("media 2");
    FakeDownloadAction download2Action = createDownloadAction("media 2");
    FakeDownloadAction remove1Action = createRemoveAction("media 1");
    FakeDownloadAction download3Action = createDownloadAction("media 3");

    download1Action.post().assertStarted();
    remove2Action.post().assertStarted();
    download2Action.post().assertDoesNotStart();

    runOnMainThread(
        new Runnable() {
          @Override
          public void run() {
            downloadManager.stopDownloads();
          }
        });

    download1Action.assertStopped();

    // remove actions aren't stopped.
    remove2Action.unblock().assertEnded();
    // Although remove2Action is finished, download2Action doesn't start.
    download2Action.assertDoesNotStart();

    // When a new remove action is added, it cancels stopped download actions with the same media.
    remove1Action.post();
    download1Action.assertCancelled();
    remove1Action.assertStarted().unblock().assertEnded();

    // New download actions can be added but they don't start.
    download3Action.post().assertDoesNotStart();

    runOnMainThread(
        new Runnable() {
          @Override
          public void run() {
            downloadManager.startDownloads();
          }
        });

    download2Action.assertStarted().unblock().assertEnded();
    download3Action.assertStarted().unblock().assertEnded();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void testResumeBeforeTotallyStopped() throws Throwable {
    setUpDownloadManager(2);
    FakeDownloadAction download1Action = createDownloadAction("media 1").ignoreInterrupts();
    FakeDownloadAction download2Action = createDownloadAction("media 2");
    FakeDownloadAction download3Action = createDownloadAction("media 3");

    download1Action.post().assertStarted();
    download2Action.post().assertStarted();
    // download3Action doesn't start as DM was configured to run two downloads in parallel.
    download3Action.post().assertDoesNotStart();

    runOnMainThread(
        new Runnable() {
          @Override
          public void run() {
            downloadManager.stopDownloads();
          }
        });

    // download1Action doesn't stop yet as it ignores interrupts.
    download2Action.assertStopped();

    runOnMainThread(
        new Runnable() {
          @Override
          public void run() {
            downloadManager.startDownloads();
          }
        });

    // download2Action starts immediately.
    download2Action.assertStarted();

    // download3Action doesn't start as download1Action still holds its slot.
    download3Action.assertDoesNotStart();

    // when unblocked download1Action stops and starts immediately.
    download1Action.unblock().assertStopped().assertStarted();

    download1Action.unblock();
    download2Action.unblock();
    download3Action.unblock();

    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void setUpDownloadManager(final int maxActiveDownloadTasks) throws Exception {
    if (downloadManager != null) {
      releaseDownloadManager();
    }
    try {
      runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              downloadManager =
                  new DownloadManager(
                      new DownloaderConstructorHelper(
                          Mockito.mock(Cache.class), DummyDataSource.FACTORY),
                      maxActiveDownloadTasks,
                      MIN_RETRY_COUNT,
                      actionFile.getAbsolutePath());
              downloadManager.addListener(testDownloadListener);
              downloadManager.startDownloads();
            }
          });
    } catch (Throwable throwable) {
      throw new Exception(throwable);
    }
  }

  private void releaseDownloadManager() throws Exception {
    try {
      runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              downloadManager.release();
            }
          });
    } catch (Throwable throwable) {
      throw new Exception(throwable);
    }
  }

  private void doTestActionRuns(FakeDownloadAction action) throws Throwable {
    action.post().assertStarted().unblock().assertEnded();
    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void doTestActionsRunSequentially(FakeDownloadAction action1, FakeDownloadAction action2)
      throws Throwable {
    action1.ignoreInterrupts().post().assertStarted();
    action2.post().assertDoesNotStart();

    action1.unblock();
    action2.assertStarted();

    action2.unblock().assertEnded();
    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void doTestActionsRunInParallel(FakeDownloadAction action1, FakeDownloadAction action2)
      throws Throwable {
    action1.post().assertStarted();
    action2.post().assertStarted();
    action1.unblock().assertEnded();
    action2.unblock().assertEnded();
    testDownloadListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private FakeDownloadAction createDownloadAction(String mediaId) {
    return new FakeDownloadAction(mediaId, false);
  }

  private FakeDownloadAction createRemoveAction(String mediaId) {
    return new FakeDownloadAction(mediaId, true);
  }

  private void runOnMainThread(final Runnable r) throws Throwable {
    dummyMainThread.runOnMainThread(r);
  }

  private static final class TestDownloadListener implements DownloadListener {

    private ConditionVariable downloadFinishedCondition;
    private Throwable downloadError;

    private TestDownloadListener() {
      downloadFinishedCondition = new ConditionVariable();
    }

    @Override
    public void onStateChange(DownloadManager downloadManager, DownloadState downloadState) {
      if (downloadState.state == DownloadState.STATE_ERROR && downloadError == null) {
        downloadError = downloadState.error;
      }
      ((FakeDownloadAction) downloadState.downloadAction).onStateChange(downloadState.state);
    }

    @Override
    public void onIdle(DownloadManager downloadManager) {
      downloadFinishedCondition.open();
    }

    private void clearDownloadError() {
      this.downloadError = null;
    }

    private void blockUntilTasksCompleteAndThrowAnyDownloadError() throws Throwable {
      assertThat(downloadFinishedCondition.block(ASSERT_TRUE_TIMEOUT)).isTrue();
      downloadFinishedCondition.close();
      if (downloadError != null) {
        throw new Exception(downloadError);
      }
    }
  }

  private class FakeDownloadAction extends DownloadAction {

    private final String mediaId;
    private final boolean removeAction;
    private final FakeDownloader downloader;
    private final BlockingQueue<Integer> states;

    private FakeDownloadAction(String mediaId, boolean removeAction) {
      super(mediaId);
      this.mediaId = mediaId;
      this.removeAction = removeAction;
      this.downloader = new FakeDownloader(removeAction);
      this.states = new ArrayBlockingQueue<>(10);
    }

    @Override
    protected String getType() {
      return "FakeDownloadAction";
    }

    @Override
    protected void writeToStream(DataOutputStream output) throws IOException {
      // do nothing.
    }

    @Override
    public boolean isRemoveAction() {
      return removeAction;
    }

    @Override
    protected boolean isSameMedia(DownloadAction other) {
      return other instanceof FakeDownloadAction
          && mediaId.equals(((FakeDownloadAction) other).mediaId);
    }

    @Override
    protected Downloader createDownloader(DownloaderConstructorHelper downloaderConstructorHelper) {
      return downloader;
    }

    private FakeDownloader getFakeDownloader() {
      return downloader;
    }

    private FakeDownloadAction post() throws Throwable {
      runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              downloadManager.handleAction(FakeDownloadAction.this);
            }
          });
      return this;
    }

    private FakeDownloadAction assertDoesNotStart() throws InterruptedException {
      Thread.sleep(ASSERT_FALSE_TIME);
      assertThat(downloader.started.getCount()).isEqualTo(1);
      return this;
    }

    private FakeDownloadAction assertStarted() throws InterruptedException {
      downloader.assertStarted(ASSERT_TRUE_TIMEOUT);
      return assertState(DownloadState.STATE_STARTED);
    }

    private FakeDownloadAction assertEnded() {
      return assertState(DownloadState.STATE_ENDED);
    }

    private FakeDownloadAction assertError() {
      return assertState(DownloadState.STATE_ERROR);
    }

    private FakeDownloadAction assertCancelled() {
      return assertState(DownloadState.STATE_CANCELED);
    }

    private FakeDownloadAction assertStopped() {
      return assertState(DownloadState.STATE_QUEUED);
    }

    private FakeDownloadAction assertState(@State int expectedState) {
      ArrayList<Integer> receivedStates = new ArrayList<>();
      while (true) {
        Integer state = null;
        try {
          state = states.poll(ASSERT_TRUE_TIMEOUT, TimeUnit.MILLISECONDS);
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

    private FakeDownloadAction unblock() {
      downloader.unblock();
      return this;
    }

    private FakeDownloadAction ignoreInterrupts() {
      downloader.ignoreInterrupts = true;
      return this;
    }

    private void onStateChange(int state) {
      states.add(state);
    }
  }

  private static class FakeDownloader implements Downloader {

    private final com.google.android.exoplayer2.util.ConditionVariable blocker;
    private final boolean removeAction;

    private CountDownLatch started;
    private boolean ignoreInterrupts;
    private volatile boolean enableDownloadIOException;
    private volatile int downloadedBytes = C.LENGTH_UNSET;

    private FakeDownloader(boolean removeAction) {
      this.removeAction = removeAction;
      this.started = new CountDownLatch(1);
      this.blocker = new com.google.android.exoplayer2.util.ConditionVariable();
    }

    @Override
    public void init() throws InterruptedException, IOException {
      // do nothing.
    }

    @Override
    public void download(@Nullable ProgressListener listener)
        throws InterruptedException, IOException {
      assertThat(removeAction).isFalse();
      started.countDown();
      block();
      if (enableDownloadIOException) {
        throw new IOException();
      }
    }

    @Override
    public void remove() throws InterruptedException {
      assertThat(removeAction).isTrue();
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
