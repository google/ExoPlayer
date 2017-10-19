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

import android.os.ConditionVariable;
import android.support.annotation.Nullable;
import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadListener;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadTask;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadTask.State;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.util.ClosedSource;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests {@link DownloadManager}. */
@ClosedSource(reason = "Not ready yet")
public class DownloadManagerTest extends InstrumentationTestCase {

  /* Used to check if condition becomes true in this time interval. */
  private static final int ASSERT_TRUE_TIMEOUT = 1000;
  /* Used to check if condition stays false for this time interval. */
  private static final int ASSERT_FALSE_TIME = 1000;

  private DownloadManager downloadManager;
  private ConditionVariable downloadFinishedCondition;
  private Throwable downloadError;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setUpMockito(this);

    downloadManager = new DownloadManager(
        new DownloaderConstructorHelper(Mockito.mock(Cache.class), DummyDataSource.FACTORY),
        Executors.newCachedThreadPool());

    downloadFinishedCondition = new ConditionVariable();
    downloadManager.setListener(new TestDownloadListener());
  }

  class TestDownloadListener implements DownloadListener {
    @Override
    public void onStateChange(DownloadManager downloadManager, DownloadTask downloadTask, int state,
        Throwable error) {
      if (state == DownloadTask.STATE_ERROR && downloadError == null) {
        downloadError = error;
      }
      ((FakeDownloadAction) downloadTask.getDownloadAction()).onStateChange();
    }

    @Override
    public void onTasksFinished(DownloadManager downloadManager) {
      downloadFinishedCondition.open();
    }
  }

  public void testDownloadActionRuns() throws Throwable {
    doTestActionRuns(createDownloadAction("media 1"));
  }

  public void testRemoveActionRuns() throws Throwable {
    doTestActionRuns(createRemoveAction("media 1"));
  }

  public void testDifferentMediaDownloadActionsStartInParallel() throws Throwable {
    doTestActionsRunInParallel(createDownloadAction("media 1"),
        createDownloadAction("media 2"));
  }

  public void testDifferentMediaDifferentActionsStartInParallel() throws Throwable {
    doTestActionsRunInParallel(createDownloadAction("media 1"),
        createRemoveAction("media 2"));
  }

  public void testSameMediaDownloadActionsStartInParallel() throws Throwable {
    doTestActionsRunInParallel(createDownloadAction("media 1"),
        createDownloadAction("media 1"));
  }

  public void testSameMediaRemoveActionWaitsDownloadAction() throws Throwable {
    doTestActionsRunSequentially(createDownloadAction("media 1"),
        createRemoveAction("media 1"));
  }

  public void testSameMediaDownloadActionWaitsRemoveAction() throws Throwable {
    doTestActionsRunSequentially(createRemoveAction("media 1"),
        createDownloadAction("media 1"));
  }

  public void testSameMediaRemoveActionWaitsRemoveAction() throws Throwable {
    doTestActionsRunSequentially(createRemoveAction("media 1"),
        createRemoveAction("media 1"));
  }

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
    downloadAction2.finishAndAssertFinished();
    removeAction1.assertDoesNotStart();
    // downloadAction3 is post to DownloadManager but it waits for removeAction1 to finish.
    downloadAction3.post().assertDoesNotStart();

    // When downloadAction1 finishes, removeAction1 starts.
    downloadAction1.finishAndAssertFinished();
    removeAction1.assertStarted();
    // downloadAction3 still waits removeAction1
    downloadAction3.assertDoesNotStart();

    // removeAction2 is posted. removeAction1 and downloadAction3 is canceled so removeAction2
    // starts immediately.
    removeAction2.post().assertStarted().finishAndAssertFinished();
    blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  public void testMultipleWaitingAction() throws Throwable {
    FakeDownloadAction removeAction1 = createRemoveAction("media 1").ignoreInterrupts();
    FakeDownloadAction removeAction2 = createRemoveAction("media 1");
    FakeDownloadAction removeAction3 = createRemoveAction("media 1");

    removeAction1.post().assertStarted();
    removeAction2.post().assertDoesNotStart();
    removeAction3.post().assertDoesNotStart();

    removeAction1.finishAndAssertFinished();
    removeAction2.assertTaskState(DownloadTask.STATE_CANCELLED);
    removeAction3.assertStarted().finishAndAssertFinished();

    blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void doTestActionRuns(FakeDownloadAction action) throws Throwable {
    action.post().assertStarted().finishAndAssertFinished();
    blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void doTestActionsRunSequentially(FakeDownloadAction action1,
      FakeDownloadAction action2) throws Throwable {
    action1.ignoreInterrupts().post().assertStarted();
    action2.post().assertDoesNotStart();

    action1.finishAndAssertFinished();
    action2.assertStarted();

    action2.finishAndAssertFinished();
    blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void doTestActionsRunInParallel(FakeDownloadAction action1,
      FakeDownloadAction action2) throws Throwable {
    action1.post().assertStarted();
    action2.post().assertStarted();
    action1.finishAndAssertFinished();
    action2.finishAndAssertFinished();
    blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private FakeDownloadAction createDownloadAction(String mediaId) {
    return new FakeDownloadAction(downloadManager, mediaId, false);
  }

  private FakeDownloadAction createRemoveAction(String mediaId) {
    return new FakeDownloadAction(downloadManager, mediaId, true);
  }

  private void blockUntilTasksCompleteAndThrowAnyDownloadError() throws Throwable {
    assertTrue(downloadFinishedCondition.block(ASSERT_TRUE_TIMEOUT));
    downloadFinishedCondition.close();
    if (downloadError != null) {
      throw downloadError;
    }
  }

  /**
   * Sets up Mockito for an instrumentation test.
   */
  private static void setUpMockito(InstrumentationTestCase instrumentationTestCase) {
    // Workaround for https://code.google.com/p/dexmaker/issues/detail?id=2.
    System.setProperty("dexmaker.dexcache",
        instrumentationTestCase.getInstrumentation().getTargetContext().getCacheDir().getPath());
    MockitoAnnotations.initMocks(instrumentationTestCase);
  }

  private static class FakeDownloadAction extends DownloadAction {

    private final DownloadManager downloadManager;
    private final String mediaId;
    private final boolean removeAction;
    private final FakeDownloader downloader;
    private final ConditionVariable stateChanged;
    private DownloadTask downloadTask;

    private FakeDownloadAction(DownloadManager downloadManager, String mediaId,
        boolean removeAction) {
      this.downloadManager = downloadManager;
      this.mediaId = mediaId;
      this.removeAction = removeAction;
      this.downloader = new FakeDownloader(removeAction);
      this.stateChanged = new ConditionVariable();
    }

    @Override
    protected String getType() {
      return "FakeDownloadAction";
    }

    @Override
    protected void writeToStream(DataOutputStream output) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    protected boolean isRemoveAction() {
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

    private FakeDownloadAction post() throws DownloadException {
      downloadTask = downloadManager.handleAction(this);
      return this;
    }

    private FakeDownloadAction assertDoesNotStart() {
      assertFalse(downloader.started.block(ASSERT_FALSE_TIME));
      return this;
    }

    private FakeDownloadAction assertStarted() {
      assertTrue(downloader.started.block(ASSERT_TRUE_TIMEOUT));
      assertTaskState(DownloadTask.STATE_STARTED);
      return this;
    }

    private FakeDownloadAction assertTaskState(@State int state) {
      assertTrue(stateChanged.block(ASSERT_TRUE_TIMEOUT));
      stateChanged.close();
      assertEquals(state, downloadTask.getState());
      return this;
    }

    private FakeDownloadAction finishAndAssertFinished() {
      downloader.finish.open();
      assertTaskState(DownloadTask.STATE_ENDED);
      return this;
    }

    private FakeDownloadAction ignoreInterrupts() {
      downloader.ignoreInterrupts = true;
      return this;
    }

    private void onStateChange() {
      stateChanged.open();
    }
  }

  private static class FakeDownloader implements Downloader {
    private final ConditionVariable started;
    private final com.google.android.exoplayer2.util.ConditionVariable finish;
    private final boolean removeAction;
    private boolean ignoreInterrupts;

    private FakeDownloader(boolean removeAction) {
      this.removeAction = removeAction;
      this.started = new ConditionVariable();
      this.finish = new com.google.android.exoplayer2.util.ConditionVariable();
    }

    @Override
    public void init() throws InterruptedException, IOException {
      // do nothing.
    }

    @Override
    public void download(@Nullable ProgressListener listener)
        throws InterruptedException, IOException {
      assertFalse(removeAction);
      started.open();
      blockUntilFinish();
    }

    @Override
    public void remove() throws InterruptedException {
      assertTrue(removeAction);
      started.open();
      blockUntilFinish();
    }

    private void blockUntilFinish() throws InterruptedException {
      while (true){
        try {
          finish.block();
          break;
        } catch (InterruptedException e) {
          if (!ignoreInterrupts) {
            throw e;
          }
        }
      }
    }

    @Override
    public long getDownloadedBytes() {
      return 0;
    }

    @Override
    public float getDownloadPercentage() {
      return 0;
    }
  }

}
