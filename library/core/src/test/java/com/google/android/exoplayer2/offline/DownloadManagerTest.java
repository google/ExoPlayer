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
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.testutil.DownloadBuilder;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.DummyMainThread.TestRunnable;
import com.google.android.exoplayer2.testutil.TestDownloadManagerListener;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadows.ShadowLog;

/** Tests {@link DownloadManager}. */
@RunWith(AndroidJUnit4.class)
@LooperMode(Mode.PAUSED)
public class DownloadManagerTest {

  /** Timeout to use when blocking on conditions that we expect to become unblocked. */
  private static final int TIMEOUT_MS = 10_000;
  /** An application provided stop reason. */
  private static final int APP_STOP_REASON = 1;
  /** The minimum number of times a download must be retried before failing. */
  private static final int MIN_RETRY_COUNT = 3;
  /** Dummy value for the current time. */
  private static final long NOW_MS = 1234;

  private static final String ID1 = "id1";
  private static final String ID2 = "id2";
  private static final String ID3 = "id3";

  @GuardedBy("downloaders")
  private final List<FakeDownloader> downloaders = new ArrayList<>();

  private DownloadManager downloadManager;
  private TestDownloadManagerListener downloadManagerListener;
  private DummyMainThread dummyMainThread;

  @Before
  public void setUp() throws Exception {
    ShadowLog.stream = System.out;
    dummyMainThread = new DummyMainThread();
    setupDownloadManager(/* maxParallelDownloads= */ 100);
  }

  @After
  public void tearDown() throws Exception {
    releaseDownloadManager();
    dummyMainThread.release();
  }

  @Test
  public void postDownloadRequest_downloads() throws Throwable {
    postDownloadRequest(ID1);
    assertDownloading(ID1);

    FakeDownloader downloader = getDownloaderAt(0);
    downloader.assertId(ID1);
    downloader.assertDownloadStarted();
    downloader.unblock();
    downloader.assertCompleted();
    downloader.assertStartCount(1);

    assertCompleted(ID1);
    assertDownloaderCount(1);
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void postRemoveRequest_removes() throws Throwable {
    postDownloadRequest(ID1);
    postRemoveRequest(ID1);
    assertRemoving(ID1);

    FakeDownloader downloader = getDownloaderAt(1);
    downloader.assertId(ID1);
    downloader.unblock();
    downloader.assertCompleted();
    downloader.assertStartCount(1);

    assertRemoved(ID1);
    assertDownloaderCount(2);
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void downloadFails_retriesThenTaskFails() throws Throwable {
    postDownloadRequest(ID1);

    FakeDownloader downloader = getDownloaderAt(0);
    downloader.assertId(ID1);
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      downloader.assertDownloadStarted();
      downloader.fail();
    }
    downloader.assertCompleted();
    downloader.assertStartCount(MIN_RETRY_COUNT + 1);

    assertFailed(ID1);
    downloadManagerListener.blockUntilIdle();
    assertCurrentDownloadCount(0);
  }

  @Test
  public void downloadFails_retries() throws Throwable {
    postDownloadRequest(ID1);

    FakeDownloader downloader = getDownloaderAt(0);
    downloader.assertId(ID1);
    for (int i = 0; i < MIN_RETRY_COUNT; i++) {
      downloader.assertDownloadStarted();
      downloader.fail();
    }
    downloader.assertDownloadStarted();
    downloader.unblock();
    downloader.assertCompleted();
    downloader.assertStartCount(MIN_RETRY_COUNT + 1);

    assertCompleted(ID1);
    downloadManagerListener.blockUntilIdle();
    assertCurrentDownloadCount(0);
  }

  @Test
  public void downloadProgressOnRetry_retryCountResets() throws Throwable {
    postDownloadRequest(ID1);

    FakeDownloader downloader = getDownloaderAt(0);
    downloader.assertId(ID1);
    int tooManyRetries = MIN_RETRY_COUNT + 10;
    for (int i = 0; i < tooManyRetries; i++) {
      downloader.assertDownloadStarted();
      downloader.incrementBytesDownloaded(); // Make some progress.
      downloader.fail();
    }
    downloader.assertDownloadStarted();
    downloader.unblock();
    downloader.assertCompleted();
    downloader.assertStartCount(tooManyRetries + 1);

    assertCompleted(ID1);
    downloadManagerListener.blockUntilIdle();
  }

  @Test
  public void removeCancelsDownload() throws Throwable {
    postDownloadRequest(ID1);

    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();

    postRemoveRequest(ID1);

    downloader0.assertCanceled();
    downloader0.assertStartCount(1);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertRemoveStarted();
    downloader1.unblock();
    downloader1.assertCompleted();

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void downloadNotCancelRemove() throws Throwable {
    postDownloadRequest(ID1);
    postRemoveRequest(ID1);

    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertRemoveStarted();

    postDownloadRequest(ID1);

    downloader1.unblock();
    downloader1.assertCompleted();

    FakeDownloader downloader2 = getDownloaderAt(2);
    downloader2.assertId(ID1);

    downloader2.assertDownloadStarted();
    downloader2.unblock();
    downloader2.assertCompleted();

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void secondSameRemoveRequestIgnored() throws Throwable {
    postDownloadRequest(ID1);
    postRemoveRequest(ID1);

    FakeDownloader downloader = getDownloaderAt(1);
    downloader.assertId(ID1);
    downloader.assertRemoveStarted();

    postRemoveRequest(ID1);

    downloader.unblock();
    downloader.assertCompleted();

    assertRemoved(ID1);
    assertDownloaderCount(2);
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void removeAllDownloads_removesAllDownloads() throws Throwable {
    // Finish one download and keep one running.
    postDownloadRequest(ID1);
    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.unblock();
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();

    postDownloadRequest(ID2);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID2);
    downloader1.assertDownloadStarted();

    postRemoveAllRequest();
    FakeDownloader downloader2 = getDownloaderAt(2);
    downloader2.assertId(ID1);
    downloader2.unblock();
    FakeDownloader downloader3 = getDownloaderAt(3);
    downloader3.assertId(ID2);
    downloader3.unblock();
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();

    assertRemoved(ID1);
    assertRemoved(ID2);
    assertCurrentDownloadCount(0);
    assertDownloadIndexSize(0);
  }

  @Test
  public void differentDownloadRequestsMerged() throws Throwable {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    postDownloadRequest(ID1, streamKey1);
    postDownloadRequest(ID1, streamKey2);

    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();
    downloader0.assertCanceled();

    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertDownloadStarted();
    downloader1.assertStreamKeys(streamKey1, streamKey2);
    downloader1.unblock();

    assertCompleted(ID1);
    assertDownloaderCount(2);
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void requestsForDifferentContent_executedInParallel() throws Throwable {
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);

    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID2);
    downloader0.assertDownloadStarted();
    downloader1.assertDownloadStarted();
    downloader0.unblock();
    downloader1.unblock();

    assertCompleted(ID1);
    assertCompleted(ID2);
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void requestsForDifferentContent_ifMaxDownloadIs1_executedSequentially() throws Throwable {
    setupDownloadManager(/* maxParallelDownloads= */ 1);
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);

    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();
    assertNoDownloaderAt(1);
    assertQueued(ID2);
    downloader0.unblock();
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID2);
    downloader1.assertDownloadStarted();
    downloader1.unblock();

    assertCompleted(ID1);
    assertCompleted(ID2);
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void removeRequestForDifferentContent_ifMaxDownloadIs1_executedInParallel()
      throws Throwable {
    setupDownloadManager(/* maxParallelDownloads= */ 1);
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);
    postRemoveRequest(ID2);

    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID2);
    downloader0.assertDownloadStarted();
    downloader1.assertRemoveStarted();
    downloader0.unblock();
    downloader1.unblock();

    assertCompleted(ID1);
    assertRemoved(ID2);
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void downloadRequestFollowingRemove_ifMaxDownloadIs1_isNotStarted() throws Throwable {
    setupDownloadManager(/* maxParallelDownloads= */ 1);
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);
    postRemoveRequest(ID2);
    postDownloadRequest(ID2);

    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID2);
    downloader0.assertDownloadStarted();
    downloader1.assertRemoveStarted();
    downloader1.unblock();
    assertNoDownloaderAt(2);
    downloader0.unblock();
    FakeDownloader downloader2 = getDownloaderAt(2);
    downloader2.assertId(ID2);

    downloader2.assertDownloadStarted();
    downloader2.unblock();

    assertCompleted(ID1);
    assertCompleted(ID2);
    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void getCurrentDownloads_returnsCurrentDownloads() {
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);
    postDownloadRequest(ID3);
    postRemoveRequest(ID3);
    assertRemoving(ID3);

    List<Download> downloads = downloadManager.getCurrentDownloads();
    assertThat(downloads).hasSize(3);
    String[] taskIds = {ID1, ID2, ID3};
    String[] downloadIds = {
      downloads.get(0).request.id, downloads.get(1).request.id, downloads.get(2).request.id
    };
    assertThat(downloadIds).isEqualTo(taskIds);
  }

  @Test
  public void pauseAndResume() throws Throwable {
    postDownloadRequest(ID1);
    assertDownloading(ID1);
    postDownloadRequest(ID2);
    postRemoveRequest(ID2);
    assertRemoving(ID2);
    postDownloadRequest(ID2);

    postPauseDownloads();

    assertQueued(ID1);

    // remove requests aren't stopped.
    FakeDownloader downloader2 = getDownloaderAt(2);
    downloader2.assertId(ID2);
    downloader2.unblock();
    downloader2.assertCompleted();
    assertQueued(ID2);
    // Although remove2 is finished, download2 doesn't start.
    assertNoDownloaderAt(3);

    // When a new remove request is added, it cancels stopped download requests with the same media.
    postRemoveRequest(ID1);
    FakeDownloader downloader3 = getDownloaderAt(3);
    downloader3.assertId(ID1);
    downloader3.assertRemoveStarted();
    downloader3.unblock();
    assertRemoved(ID1);

    // New download requests can be added but they don't start.
    postDownloadRequest(ID3);
    assertNoDownloaderAt(4);

    postResumeDownloads();

    FakeDownloader downloader4 = getDownloaderAt(4);
    downloader4.assertId(ID2);
    downloader4.assertDownloadStarted();
    downloader4.unblock();
    FakeDownloader downloader5 = getDownloaderAt(5);
    downloader5.assertId(ID3);
    downloader5.assertDownloadStarted();
    downloader5.unblock();

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void setAndClearSingleDownloadStopReason() throws Throwable {
    postDownloadRequest(ID1);

    assertDownloading(ID1);

    postSetStopReason(ID1, APP_STOP_REASON);

    assertStopped(ID1);

    postSetStopReason(ID1, Download.STOP_REASON_NONE);

    FakeDownloader downloader = getDownloaderAt(1);
    downloader.assertId(ID1);
    downloader.assertDownloadStarted();
    downloader.unblock();

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void setSingleDownloadStopReasonThenRemove_removesDownload() throws Throwable {
    postDownloadRequest(ID1);

    assertDownloading(ID1);

    postSetStopReason(ID1, APP_STOP_REASON);

    assertStopped(ID1);

    postRemoveRequest(ID1);
    FakeDownloader downloader = getDownloaderAt(1);
    downloader.assertId(ID1);
    downloader.assertRemoveStarted();
    downloader.unblock();
    assertRemoving(ID1);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void setSingleDownloadStopReason_doesNotAffectOtherDownloads() throws Throwable {
    postDownloadRequest(ID1);
    assertDownloading(ID1);
    postDownloadRequest(ID2);
    postRemoveRequest(ID2);
    assertRemoving(ID2);

    postSetStopReason(ID1, APP_STOP_REASON);

    assertStopped(ID1);

    // Other downloaders aren't affected.
    FakeDownloader downloader1 = getDownloaderAt(2);
    downloader1.assertId(ID2);
    downloader1.assertRemoveStarted();
    downloader1.unblock();
    downloader1.assertCompleted();

    // New download requests can be added and they start.
    postDownloadRequest(ID3);
    FakeDownloader downloader3 = getDownloaderAt(3);
    downloader3.assertId(ID3);
    downloader3.assertDownloadStarted();
    downloader3.unblock();

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
  }

  @Test
  public void mergeRequest_removing_becomesRestarting() {
    DownloadRequest downloadRequest = createDownloadRequest(ID1);
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
    DownloadRequest downloadRequest = createDownloadRequest(ID1);
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
    DownloadRequest downloadRequest = createDownloadRequest(ID1);
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
    DownloadRequest downloadRequest = createDownloadRequest(ID1);
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

  private void setupDownloadManager(int maxParallelDownloads) throws Exception {
    if (downloadManager != null) {
      releaseDownloadManager();
    }
    try {
      runOnMainThread(
          () -> {
            downloadManager =
                new DownloadManager(
                    ApplicationProvider.getApplicationContext(),
                    new DefaultDownloadIndex(TestUtil.getInMemoryDatabaseProvider()),
                    new FakeDownloaderFactory());
            downloadManager.setMaxParallelDownloads(maxParallelDownloads);
            downloadManager.setMinRetryCount(MIN_RETRY_COUNT);
            downloadManager.setRequirements(new Requirements(0));
            downloadManager.resumeDownloads();
            downloadManagerListener = new TestDownloadManagerListener(downloadManager);
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

  private void postRemoveRequest(String id) {
    runOnMainThread(() -> downloadManager.removeDownload(id));
  }

  private void postRemoveAllRequest() {
    runOnMainThread(() -> downloadManager.removeAllDownloads());
  }

  private void postPauseDownloads() {
    runOnMainThread(() -> downloadManager.pauseDownloads());
  }

  private void postResumeDownloads() {
    runOnMainThread(() -> downloadManager.resumeDownloads());
  }

  private void postSetStopReason(String id, int reason) {
    runOnMainThread(() -> downloadManager.setStopReason(id, reason));
  }

  private void postDownloadRequest(String id, StreamKey... keys) {
    runOnMainThread(() -> downloadManager.addDownload(createDownloadRequest(id, keys)));
  }

  private List<Download> postGetCurrentDownloads() {
    AtomicReference<List<Download>> currentDownloadsReference = new AtomicReference<>();
    runOnMainThread(
        () -> {
          currentDownloadsReference.set(downloadManager.getCurrentDownloads());
        });
    return currentDownloadsReference.get();
  }

  private DownloadIndex postGetDownloadIndex() {
    AtomicReference<DownloadIndex> downloadIndexReference = new AtomicReference<>();
    runOnMainThread(
        () -> {
          downloadIndexReference.set(downloadManager.getDownloadIndex());
        });
    return downloadIndexReference.get();
  }

  private void runOnMainThread(TestRunnable r) {
    dummyMainThread.runTestOnMainThread(r);
  }

  private FakeDownloader getDownloaderAt(int index) throws InterruptedException {
    return Assertions.checkNotNull(getDownloaderInternal(index, TIMEOUT_MS));
  }

  private void assertNoDownloaderAt(int index) throws InterruptedException {
    // We use a timeout shorter than TIMEOUT_MS because timing out is expected in this case.
    assertThat(getDownloaderInternal(index, /* timeoutMs= */ 1_000)).isNull();
  }

  private void assertDownloading(String id) {
    downloadManagerListener.assertState(id, Download.STATE_DOWNLOADING);
  }

  private void assertCompleted(String id) {
    downloadManagerListener.assertState(id, Download.STATE_COMPLETED);
  }

  private void assertRemoving(String id) {
    downloadManagerListener.assertState(id, Download.STATE_REMOVING);
  }

  private void assertFailed(String id) {
    downloadManagerListener.assertState(id, Download.STATE_FAILED);
  }

  private void assertQueued(String id) {
    downloadManagerListener.assertState(id, Download.STATE_QUEUED);
  }

  private void assertStopped(String id) {
    downloadManagerListener.assertState(id, Download.STATE_STOPPED);
  }

  private void assertRemoved(String id) {
    downloadManagerListener.assertRemoved(id);
  }

  private void assertDownloaderCount(int expectedCount) {
    synchronized (downloaders) {
      assertThat(downloaders).hasSize(expectedCount);
    }
  }

  private void assertCurrentDownloadCount(int expectedCount) {
    assertThat(postGetCurrentDownloads()).hasSize(expectedCount);
  }

  private void assertDownloadIndexSize(int expectedSize) throws IOException {
    assertThat(postGetDownloadIndex().getDownloads().getCount()).isEqualTo(expectedSize);
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

  private static DownloadRequest createDownloadRequest(String id, StreamKey... keys) {
    return new DownloadRequest(
        id,
        DownloadRequest.TYPE_DASH,
        Uri.parse("http://abc.com/ " + id),
        Arrays.asList(keys),
        /* customCacheKey= */ null,
        /* data= */ null);
  }

  // Internal methods.

  @Nullable
  private FakeDownloader getDownloaderInternal(int index, long timeoutMs)
      throws InterruptedException {
    long nowMs = System.currentTimeMillis();
    long endMs = nowMs + timeoutMs;
    synchronized (downloaders) {
      while (downloaders.size() <= index && nowMs < endMs) {
        downloaders.wait(endMs - nowMs);
        nowMs = System.currentTimeMillis();
      }
      return downloaders.size() <= index ? null : downloaders.get(index);
    }
  }

  private final class FakeDownloaderFactory implements DownloaderFactory {

    @Override
    public Downloader createDownloader(DownloadRequest request) {
      FakeDownloader fakeDownloader = new FakeDownloader(request);
      synchronized (downloaders) {
        downloaders.add(fakeDownloader);
        downloaders.notifyAll();
      }
      return fakeDownloader;
    }
  }

  private static final class FakeDownloader implements Downloader {

    private final DownloadRequest request;
    private final ConditionVariable downloadStarted;
    private final ConditionVariable removeStarted;
    private final ConditionVariable finished;
    private final ConditionVariable blocker;
    private final AtomicInteger startCount;
    private final AtomicInteger bytesDownloaded;

    private volatile boolean canceled;
    private volatile boolean enableDownloadIOException;

    private FakeDownloader(DownloadRequest request) {
      this.request = request;
      downloadStarted = TestUtil.createRobolectricConditionVariable();
      removeStarted = TestUtil.createRobolectricConditionVariable();
      finished = TestUtil.createRobolectricConditionVariable();
      blocker = TestUtil.createRobolectricConditionVariable();
      startCount = new AtomicInteger();
      bytesDownloaded = new AtomicInteger();
    }

    @Override
    public void cancel() {
      canceled = true;
      blocker.open();
    }

    @Override
    public void download(ProgressListener listener) throws InterruptedException, IOException {
      startCount.incrementAndGet();
      downloadStarted.open();
      try {
        block();
        if (canceled) {
          return;
        }
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
      removeStarted.open();
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

    public void assertId(String id) {
      assertThat(request.id).isEqualTo(id);
    }

    public void assertStreamKeys(StreamKey... streamKeys) {
      assertThat(request.streamKeys).containsExactly(streamKeys);
    }

    public void assertDownloadStarted() throws InterruptedException {
      assertThat(downloadStarted.block(TIMEOUT_MS)).isTrue();
      downloadStarted.close();
    }

    public void assertRemoveStarted() throws InterruptedException {
      assertThat(removeStarted.block(TIMEOUT_MS)).isTrue();
      removeStarted.close();
    }

    public void assertStartCount(int count) {
      assertThat(startCount.get()).isEqualTo(count);
    }

    public void assertCompleted() throws InterruptedException {
      blockUntilFinished();
      assertThat(canceled).isFalse();
    }

    public void assertCanceled() throws InterruptedException {
      blockUntilFinished();
      assertThat(canceled).isTrue();
    }

    // Internal methods.

    private void block() throws InterruptedException {
      try {
        blocker.block();
      } finally {
        blocker.close();
      }
    }

    private void blockUntilFinished() throws InterruptedException {
      assertThat(finished.block(TIMEOUT_MS)).isTrue();
      finished.close();
    }
  }
}
