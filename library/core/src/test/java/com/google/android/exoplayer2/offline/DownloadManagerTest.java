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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.Download.State;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.testutil.DownloadBuilder;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.DummyMainThread.TestRunnable;
import com.google.android.exoplayer2.testutil.TestDownloadManagerListener;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadows.ShadowLog;

/** Tests {@link DownloadManager}. */
@RunWith(AndroidJUnit4.class)
@LooperMode(Mode.PAUSED)
public class DownloadManagerTest {

  /** Used to check if condition becomes true in this time interval. */
  private static final int ASSERT_TRUE_TIMEOUT = 10000;
  /** Used to check if condition stays false for this time interval. */
  private static final int ASSERT_FALSE_TIME = 1000;
  /** A stop reason. */
  private static final int APP_STOP_REASON = 1;
  /** The minimum number of times a task must be retried before failing. */
  private static final int MIN_RETRY_COUNT = 3;
  /** Dummy value for the current time. */
  private static final long NOW_MS = 1234;

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
    downloadIndex = new DefaultDownloadIndex(TestUtil.getInMemoryDatabaseProvider());
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
  public void postDownloadRequest_downloads() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();
    runner.assertDownloading();

    FakeDownloader downloader = runner.getDownloader(0);
    downloader.unblock();
    downloader.assertCompleted();
    downloader.assertStartCount(1);

    runner.assertCompleted();
    runner.assertCreatedDownloaderCount(1);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void postRemoveRequest_removes() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();
    runner.postRemoveRequest();
    runner.assertRemoving();

    FakeDownloader downloader = runner.getDownloader(1);
    downloader.unblock();
    downloader.assertCompleted();
    downloader.assertStartCount(1);

    runner.assertRemoved();
    runner.assertCreatedDownloaderCount(2);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void downloadFails_retriesThenTaskFails() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();

    FakeDownloader downloader = runner.getDownloader(0);
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      downloader.assertStarted();
      downloader.fail();
    }
    downloader.assertCompleted();
    downloader.assertStartCount(MIN_RETRY_COUNT + 1);

    runner.assertFailed();
    downloadManagerListener.blockUntilTasksComplete();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void downloadFails_retries() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();

    FakeDownloader downloader = runner.getDownloader(0);
    for (int i = 0; i < MIN_RETRY_COUNT; i++) {
      downloader.assertStarted();
      downloader.fail();
    }
    downloader.assertStarted();
    downloader.unblock();
    downloader.assertCompleted();
    downloader.assertStartCount(MIN_RETRY_COUNT + 1);

    runner.assertCompleted();
    downloadManagerListener.blockUntilTasksComplete();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void downloadProgressOnRetry_retryCountResets() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();

    FakeDownloader downloader = runner.getDownloader(0);
    int tooManyRetries = MIN_RETRY_COUNT + 10;
    for (int i = 0; i < tooManyRetries; i++) {
      downloader.incrementBytesDownloaded();
      downloader.assertStarted();
      downloader.fail();
    }
    downloader.assertStarted();
    downloader.unblock();
    downloader.assertCompleted();
    downloader.assertStartCount(tooManyRetries + 1);

    runner.assertCompleted();
    downloadManagerListener.blockUntilTasksComplete();
  }

  @Test
  public void removeCancelsDownload() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();

    FakeDownloader downloader1 = runner.getDownloader(0);
    downloader1.assertStarted();

    runner.postRemoveRequest();

    downloader1.assertCanceled();
    downloader1.assertStartCount(1);
    FakeDownloader downloader2 = runner.getDownloader(1);
    downloader2.unblock();
    downloader2.assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void downloadNotCancelRemove() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();
    runner.postRemoveRequest();

    FakeDownloader downloader1 = runner.getDownloader(1);
    downloader1.assertStarted();

    runner.postDownloadRequest();

    downloader1.unblock();
    downloader1.assertCompleted();

    FakeDownloader downloader2 = runner.getDownloader(2);
    downloader2.unblock();
    downloader2.assertCompleted();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void secondSameRemoveRequestIgnored() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();
    runner.postRemoveRequest();

    FakeDownloader downloader = runner.getDownloader(1);
    downloader.assertStarted();

    runner.postRemoveRequest();

    downloader.unblock();
    downloader.assertCompleted();

    runner.assertRemoved();
    runner.assertCreatedDownloaderCount(2);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void removeAllDownloads_removesAllDownloads() throws Throwable {
    // Finish one download and keep one running.
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    runner1.postDownloadRequest();
    runner1.getDownloader(0).unblock();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
    runner2.postDownloadRequest();

    runner1.postRemoveAllRequest();
    runner1.getDownloader(1).unblock();
    runner2.getDownloader(1).unblock();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();

    runner1.assertRemoved();
    runner2.assertRemoved();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
    assertThat(downloadIndex.getDownloads().getCount()).isEqualTo(0);
  }

  @Test
  public void differentDownloadRequestsMerged() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(0);

    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);

    runner.postDownloadRequest(streamKey1);
    downloader1.assertStarted();
    runner.postDownloadRequest(streamKey2);

    downloader1.assertCanceled();

    FakeDownloader downloader2 = runner.getDownloader(1);
    downloader2.assertStarted();
    assertThat(downloader2.request.streamKeys).containsExactly(streamKey1, streamKey2);
    downloader2.unblock();

    runner.assertCompleted();
    runner.assertCreatedDownloaderCount(2);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void requestsForDifferentContent_executedInParallel() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    runner1.postDownloadRequest();
    runner2.postDownloadRequest();

    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);
    downloader1.assertStarted();
    downloader2.assertStarted();
    downloader1.unblock();
    downloader2.unblock();

    runner1.assertCompleted();
    runner2.assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void requestsForDifferentContent_ifMaxDownloadIs1_executedSequentially() throws Throwable {
    setUpDownloadManager(1);
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    runner1.postDownloadRequest();
    runner2.postDownloadRequest();

    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);
    downloader1.assertStarted();
    downloader2.assertDoesNotStart();
    runner2.assertQueued();
    downloader1.unblock();
    downloader2.assertStarted();
    downloader2.unblock();

    runner1.assertCompleted();
    runner2.assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void removeRequestForDifferentContent_ifMaxDownloadIs1_executedInParallel()
      throws Throwable {
    setUpDownloadManager(1);
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    runner1.postDownloadRequest();
    runner2.postDownloadRequest();
    runner2.postRemoveRequest();

    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);
    downloader1.assertStarted();
    downloader2.assertStarted();
    downloader1.unblock();
    downloader2.unblock();

    runner1.assertCompleted();
    runner2.assertRemoved();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void downloadRequestFollowingRemove_ifMaxDownloadIs1_isNotStarted() throws Throwable {
    setUpDownloadManager(1);
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    runner1.postDownloadRequest();
    runner2.postDownloadRequest();
    runner2.postRemoveRequest();
    runner2.postDownloadRequest();

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

    runner1.assertCompleted();
    runner2.assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void getCurrentDownloads_returnsCurrentDownloads() {
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    DownloadRunner runner3 = new DownloadRunner(uri3);
    runner1.postDownloadRequest();
    runner2.postDownloadRequest();
    runner3.postDownloadRequest();
    runner3.postRemoveRequest();
    runner3.assertRemoving();

    List<Download> downloads = downloadManager.getCurrentDownloads();
    assertThat(downloads).hasSize(3);
    String[] taskIds = {runner1.id, runner2.id, runner3.id};
    String[] downloadIds = {
      downloads.get(0).request.id, downloads.get(1).request.id, downloads.get(2).request.id
    };
    assertThat(downloadIds).isEqualTo(taskIds);
  }

  @Test
  public void pauseAndResume() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    DownloadRunner runner3 = new DownloadRunner(uri3);

    runner1.postDownloadRequest();
    runner1.assertDownloading();
    runner2.postDownloadRequest();
    runner2.postRemoveRequest();
    runner2.assertRemoving();
    runner2.postDownloadRequest();

    runOnMainThread(() -> downloadManager.pauseDownloads());

    runner1.assertQueued();

    // remove requests aren't stopped.
    FakeDownloader downloader1 = runner2.getDownloader(1);
    downloader1.unblock();
    downloader1.assertCompleted();
    runner2.assertQueued();
    // Although remove2 is finished, download2 doesn't start.
    runner2.getDownloader(2).assertDoesNotStart();

    // When a new remove request is added, it cancels stopped download requests with the same media.
    runner1.postRemoveRequest();
    FakeDownloader downloader2 = runner1.getDownloader(1);
    downloader2.assertStarted();
    downloader2.unblock();
    runner1.assertRemoved();

    // New download requests can be added but they don't start.
    runner3.postDownloadRequest();
    FakeDownloader downloader3 = runner3.getDownloader(0);
    downloader3.assertDoesNotStart();

    runOnMainThread(() -> downloadManager.resumeDownloads());

    FakeDownloader downloader4 = runner2.getDownloader(2);
    downloader4.assertStarted();
    downloader4.unblock();
    FakeDownloader downloader5 = runner3.getDownloader(0);
    downloader5.assertStarted();
    downloader5.unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void setAndClearSingleDownloadStopReason() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();

    runner.assertDownloading();

    runOnMainThread(() -> downloadManager.setStopReason(runner.id, APP_STOP_REASON));

    runner.assertStopped();

    runOnMainThread(() -> downloadManager.setStopReason(runner.id, Download.STOP_REASON_NONE));

    FakeDownloader downloader = runner.getDownloader(1);
    downloader.assertStarted();
    downloader.unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void setSingleDownloadStopReasonThenRemove_removesDownload() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();

    runner.assertDownloading();

    runOnMainThread(() -> downloadManager.setStopReason(runner.id, APP_STOP_REASON));

    runner.assertStopped();

    runner.postRemoveRequest();
    FakeDownloader downloader = runner.getDownloader(1);
    downloader.assertStarted();
    downloader.unblock();
    runner.assertRemoved();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void setSingleDownloadStopReason_doesNotAffectOtherDownloads() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    DownloadRunner runner3 = new DownloadRunner(uri3);

    runner1.postDownloadRequest();
    runner1.assertDownloading();
    runner2.postDownloadRequest();
    runner2.postRemoveRequest();
    runner2.assertRemoving();

    runOnMainThread(() -> downloadManager.setStopReason(runner1.id, APP_STOP_REASON));

    runner1.assertStopped();

    // Other downloads aren't affected.
    FakeDownloader downloader1 = runner2.getDownloader(1);
    downloader1.unblock();
    downloader1.assertCompleted();

    // New download requests can be added and they start.
    runner3.postDownloadRequest();
    FakeDownloader downloader2 = runner3.getDownloader(0);
    downloader2.assertStarted();
    downloader2.unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void mergeRequest_removing_becomesRestarting() {
    DownloadRequest downloadRequest = createDownloadRequest();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadRequest).setState(Download.STATE_REMOVING);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeRequest(download, downloadRequest, download.stopReason, NOW_MS);

    Download expectedDownload =
        downloadBuilder.setStartTimeMs(NOW_MS).setState(Download.STATE_RESTARTING).build();
    assertEqualIgnoringUpdateTime(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeRequest_failed_becomesQueued() {
    DownloadRequest downloadRequest = createDownloadRequest();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadRequest)
            .setState(Download.STATE_FAILED)
            .setFailureReason(Download.FAILURE_REASON_UNKNOWN);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeRequest(download, downloadRequest, download.stopReason, NOW_MS);

    Download expectedDownload =
        downloadBuilder
            .setStartTimeMs(NOW_MS)
            .setState(Download.STATE_QUEUED)
            .setFailureReason(Download.FAILURE_REASON_NONE)
            .build();
    assertEqualIgnoringUpdateTime(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeRequest_stopped_staysStopped() {
    DownloadRequest downloadRequest = createDownloadRequest();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadRequest)
            .setState(Download.STATE_STOPPED)
            .setStopReason(/* stopReason= */ 1);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeRequest(download, downloadRequest, download.stopReason, NOW_MS);

    assertEqualIgnoringUpdateTime(mergedDownload, download);
  }

  @Test
  public void mergeRequest_completedWithStopReason_becomesStopped() {
    DownloadRequest downloadRequest = createDownloadRequest();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadRequest)
            .setState(Download.STATE_COMPLETED)
            .setStopReason(/* stopReason= */ 1);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeRequest(download, downloadRequest, download.stopReason, NOW_MS);

    Download expectedDownload =
        downloadBuilder.setStartTimeMs(NOW_MS).setState(Download.STATE_STOPPED).build();
    assertEqualIgnoringUpdateTime(mergedDownload, expectedDownload);
  }

  private void setUpDownloadManager(final int maxParallelDownloads) throws Exception {
    if (downloadManager != null) {
      releaseDownloadManager();
    }
    try {
      runOnMainThread(
          () -> {
            downloadManager =
                new DownloadManager(
                    ApplicationProvider.getApplicationContext(), downloadIndex, downloaderFactory);
            downloadManager.setMaxParallelDownloads(maxParallelDownloads);
            downloadManager.setMinRetryCount(MIN_RETRY_COUNT);
            downloadManager.setRequirements(new Requirements(0));
            downloadManager.resumeDownloads();
            downloadManagerListener =
                new TestDownloadManagerListener(downloadManager, dummyMainThread);
          });
      downloadManagerListener.blockUntilInitialized();
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

  private void runOnMainThread(TestRunnable r) {
    dummyMainThread.runTestOnMainThread(r);
  }

  private static void assertEqualIgnoringUpdateTime(Download download, Download that) {
    assertThat(download.request).isEqualTo(that.request);
    assertThat(download.state).isEqualTo(that.state);
    assertThat(download.startTimeMs).isEqualTo(that.startTimeMs);
    assertThat(download.contentLength).isEqualTo(that.contentLength);
    assertThat(download.failureReason).isEqualTo(that.failureReason);
    assertThat(download.stopReason).isEqualTo(that.stopReason);
    assertThat(download.getPercentDownloaded()).isEqualTo(that.getPercentDownloaded());
    assertThat(download.getBytesDownloaded()).isEqualTo(that.getBytesDownloaded());
  }

  private static DownloadRequest createDownloadRequest() {
    return new DownloadRequest(
        "id",
        DownloadRequest.TYPE_DASH,
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

    private DownloadRunner(Uri uri) {
      this.uri = uri;
      id = uri.toString();
      downloaders = new ArrayList<>();
      downloader = addDownloader();
      downloaderFactory.registerDownloadRunner(this);
    }

    public void assertDownloading() {
      assertState(Download.STATE_DOWNLOADING);
    }

    public void assertCompleted() {
      assertState(Download.STATE_COMPLETED);
    }

    public void assertRemoving() {
      assertState(Download.STATE_REMOVING);
    }

    public void assertFailed() {
      assertState(Download.STATE_FAILED);
    }

    public void assertQueued() {
      assertState(Download.STATE_QUEUED);
    }

    public void assertStopped() {
      assertState(Download.STATE_STOPPED);
    }

    public void assertState(@State int expectedState) {
      downloadManagerListener.assertState(id, expectedState, ASSERT_TRUE_TIMEOUT);
    }

    public void assertRemoved() {
      downloadManagerListener.assertRemoved(id, ASSERT_TRUE_TIMEOUT);
    }

    private void postRemoveRequest() {
      runOnMainThread(() -> downloadManager.removeDownload(id));
    }

    private void postRemoveAllRequest() {
      runOnMainThread(() -> downloadManager.removeAllDownloads());
    }

    private void postDownloadRequest(StreamKey... keys) {
      DownloadRequest downloadRequest =
          new DownloadRequest(
              id,
              DownloadRequest.TYPE_PROGRESSIVE,
              uri,
              Arrays.asList(keys),
              /* customCacheKey= */ null,
              /* data= */ null);
      runOnMainThread(() -> downloadManager.addDownload(downloadRequest));
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

    private synchronized Downloader createDownloader(DownloadRequest request) {
      downloader = getDownloader(createdDownloaderCount++);
      downloader.request = request;
      return downloader;
    }

    private void assertCreatedDownloaderCount(int count) {
      assertThat(createdDownloaderCount).isEqualTo(count);
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
    public Downloader createDownloader(DownloadRequest request) {
      return downloaders.get(request.uri).createDownloader(request);
    }
  }

  private static final class FakeDownloader implements Downloader {

    private DownloadRequest request;

    private final ConditionVariable started;
    private final ConditionVariable finished;
    private final ConditionVariable blocker;
    private final AtomicInteger startCount;
    private final AtomicInteger bytesDownloaded;

    private volatile boolean interrupted;
    private volatile boolean canceled;
    private volatile boolean enableDownloadIOException;

    private FakeDownloader() {
      started = TestUtil.createRobolectricConditionVariable();
      finished = TestUtil.createRobolectricConditionVariable();
      blocker = TestUtil.createRobolectricConditionVariable();
      startCount = new AtomicInteger();
      bytesDownloaded = new AtomicInteger();
    }

    @Override
    public void cancel() {
      canceled = true;
    }

    @Override
    public void download(ProgressListener listener) throws InterruptedException, IOException {
      startCount.incrementAndGet();
      started.open();
      try {
        block();
        int bytesDownloaded = this.bytesDownloaded.get();
        if (listener != null && bytesDownloaded > 0) {
          listener.onProgress(C.LENGTH_UNSET, bytesDownloaded, C.PERCENTAGE_UNSET);
        }
        if (enableDownloadIOException) {
          enableDownloadIOException = false;
          throw new IOException();
        }
      } finally {
        finished.open();
      }
    }

    @Override
    public void remove() throws InterruptedException {
      startCount.incrementAndGet();
      started.open();
      try {
        block();
      } finally {
        finished.open();
      }
    }

    /** Unblocks {@link #download} or {@link #remove}, allowing the task to finish successfully. */
    public FakeDownloader unblock() {
      blocker.open();
      return this;
    }

    /** Fails {@link #download} or {@link #remove}, allowing the task to finish with an error. */
    public FakeDownloader fail() {
      enableDownloadIOException = true;
      blocker.open();
      return this;
    }

    /** Increments the number of bytes that the fake downloader has downloaded. */
    public void incrementBytesDownloaded() {
      bytesDownloaded.incrementAndGet();
    }

    public void assertStarted() throws InterruptedException {
      assertThat(started.block(ASSERT_TRUE_TIMEOUT)).isTrue();
      started.close();
    }

    public void assertStartCount(int count) {
      assertThat(startCount.get()).isEqualTo(count);
    }

    public void assertCompleted() throws InterruptedException {
      blockUntilFinished();
      assertThat(interrupted).isFalse();
      assertThat(canceled).isFalse();
    }

    public void assertCanceled() throws InterruptedException {
      blockUntilFinished();
      assertThat(interrupted).isTrue();
      assertThat(canceled).isTrue();
    }

    public void assertDoesNotStart() throws InterruptedException {
      Thread.sleep(ASSERT_FALSE_TIME);
      assertThat(started.isOpen()).isFalse();
    }

    // Internal methods.

    private void block() throws InterruptedException {
      try {
        blocker.block();
      } catch (InterruptedException e) {
        interrupted = true;
        throw e;
      } finally {
        blocker.close();
      }
    }

    private void blockUntilFinished() throws InterruptedException {
      assertThat(finished.block(ASSERT_TRUE_TIMEOUT)).isTrue();
      finished.close();
    }
  }
}
