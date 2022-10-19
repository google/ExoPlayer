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

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.createRobolectricConditionVariable;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import android.net.Uri;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.robolectric.TestDownloadManagerListener;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.testutil.DownloadBuilder;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.DummyMainThread.TestRunnable;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLog;

/** Tests {@link DownloadManager}. */
@RunWith(AndroidJUnit4.class)
public class DownloadManagerTest {

  /** Timeout to use when blocking on conditions that we expect to become unblocked. */
  private static final int TIMEOUT_MS = 10_000;
  /** An application provided stop reason. */
  private static final int APP_STOP_REASON = 1;
  /** The minimum number of times a download must be retried before failing. */
  private static final int MIN_RETRY_COUNT = 3;
  /** Test value for the current time. */
  private static final long NOW_MS = 1234;

  private static final String ID1 = "id1";
  private static final String ID2 = "id2";
  private static final String ID3 = "id3";

  @GuardedBy("downloaders")
  private final List<FakeDownloader> downloaders = new ArrayList<>();

  private DownloadManager downloadManager;
  private TestDownloadManagerListener downloadManagerListener;
  private DummyMainThread testThread;

  @Before
  public void setUp() throws Exception {
    ShadowLog.stream = System.out;
    testThread = new DummyMainThread();
    setupDownloadManager(/* maxParallelDownloads= */ 100);
  }

  @After
  public void tearDown() throws Exception {
    releaseDownloadManager();
    testThread.release();
  }

  @Test
  public void downloadRequest_downloads() throws Throwable {
    postDownloadRequest(ID1);
    assertDownloading(ID1);

    FakeDownloader downloader = getDownloaderAt(0);
    downloader.assertId(ID1);
    downloader.assertDownloadStarted();
    downloader.finish();
    assertCompleted(ID1);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(1);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void removeRequest_cancelsAndRemovesDownload() throws Throwable {
    postDownloadRequest(ID1);

    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();
    assertDownloading(ID1);

    // The download will be canceled by the remove request.
    postRemoveRequest(ID1);
    downloader0.assertCanceled();
    assertRemoving(ID1);

    // The download will be removed.
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertRemoveStarted();
    downloader1.finish();
    assertRemoved(ID1);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(0);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void download_retryUntilMinRetryCount_withoutProgress_thenFails() throws Throwable {
    postDownloadRequest(ID1);

    FakeDownloader downloader = getDownloaderAt(0);
    downloader.assertId(ID1);
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      downloader.assertDownloadStarted();
      downloader.fail();
    }
    assertFailed(ID1);

    downloadManagerListener.blockUntilIdle();
    assertDownloaderCount(1);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void download_retryUntilMinRetryCountMinusOne_thenSucceeds() throws Throwable {
    postDownloadRequest(ID1);

    FakeDownloader downloader = getDownloaderAt(0);
    downloader.assertId(ID1);
    for (int i = 0; i < MIN_RETRY_COUNT; i++) {
      downloader.assertDownloadStarted();
      downloader.fail();
    }
    downloader.assertDownloadStarted();
    downloader.finish();
    assertCompleted(ID1);

    downloadManagerListener.blockUntilIdle();
    assertDownloaderCount(1);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void download_retryMakesProgress_resetsRetryCount() throws Throwable {
    postDownloadRequest(ID1);

    FakeDownloader downloader = getDownloaderAt(0);
    downloader.assertId(ID1);
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      downloader.assertDownloadStarted();
      downloader.incrementBytesDownloaded(); // Make some progress.
      downloader.fail();
    }
    // Since previous attempts all made progress the current error count should be 1. Therefore we
    // should be able to fail (MIN_RETRY_COUNT - 1) more times and then still complete the download
    // successfully.
    for (int i = 0; i < MIN_RETRY_COUNT - 1; i++) {
      downloader.assertDownloadStarted();
      downloader.fail();
    }
    downloader.assertDownloadStarted();
    downloader.finish();
    assertCompleted(ID1);

    downloadManagerListener.blockUntilIdle();
    assertDownloaderCount(1);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void download_retryMakesProgress_resetsRetryCount_thenFails() throws Throwable {
    postDownloadRequest(ID1);

    FakeDownloader downloader = getDownloaderAt(0);
    downloader.assertId(ID1);
    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      downloader.assertDownloadStarted();
      downloader.incrementBytesDownloaded(); // Make some progress.
      downloader.fail();
    }
    // Since previous attempts all made progress the current error count should be 1. Therefore we
    // should fail after MIN_RETRY_COUNT more attempts without making any progress.
    for (int i = 0; i < MIN_RETRY_COUNT; i++) {
      downloader.assertDownloadStarted();
      downloader.fail();
    }
    assertFailed(ID1);

    downloadManagerListener.blockUntilIdle();
    assertDownloaderCount(1);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void download_WhenRemovalInProgress_doesNotCancelRemoval() throws Throwable {
    postDownloadRequest(ID1);
    postRemoveRequest(ID1);
    assertRemoving(ID1);

    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertRemoveStarted();

    postDownloadRequest(ID1);
    // The removal should still complete.
    downloader1.finish();

    // The download should then start and complete.
    FakeDownloader downloader2 = getDownloaderAt(2);
    downloader2.assertId(ID1);
    downloader2.assertDownloadStarted();
    downloader2.finish();
    assertCompleted(ID1);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(3);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void remove_WhenRemovalInProgress_doesNothing() throws Throwable {
    postDownloadRequest(ID1);
    postRemoveRequest(ID1);
    assertRemoving(ID1);

    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertRemoveStarted();

    postRemoveRequest(ID1);
    // The existing removal should still complete.
    downloader1.finish();
    assertRemoved(ID1);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(0);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void removeAllDownloads_removesAllDownloads() throws Throwable {
    // Finish one download.
    postDownloadRequest(ID1);
    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();
    downloader0.finish();
    assertCompleted(ID1);

    // Start a second download.
    postDownloadRequest(ID2);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID2);
    downloader1.assertDownloadStarted();

    postRemoveAllRequest();
    // Both downloads should be removed.
    FakeDownloader downloader2 = getDownloaderAt(2);
    downloader2.assertId(ID1);
    downloader2.assertRemoveStarted();
    downloader2.finish();
    assertRemoved(ID1);
    FakeDownloader downloader3 = getDownloaderAt(3);
    downloader3.assertId(ID2);
    downloader3.assertRemoveStarted();
    downloader3.finish();
    assertRemoved(ID2);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(4);
    assertDownloadIndexSize(0);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void downloads_withSameIdsAndDifferentStreamKeys_areMerged() throws Throwable {
    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    postDownloadRequest(ID1, streamKey1);
    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();

    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);
    postDownloadRequest(ID1, streamKey2);
    // The request for streamKey2 will cause the downloader for streamKey1 to be canceled and
    // replaced with a new downloader for both keys.
    downloader0.assertCanceled();
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertStreamKeys(streamKey1, streamKey2);
    downloader1.assertDownloadStarted();
    downloader1.finish();
    assertCompleted(ID1);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void downloads_withDifferentIds_executeInParallel() throws Throwable {
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);

    FakeDownloader downloader0 = getDownloaderAt(0);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader0.assertId(ID1);
    downloader1.assertId(ID2);
    downloader0.assertDownloadStarted();
    downloader1.assertDownloadStarted();
    downloader0.finish();
    downloader1.finish();
    assertCompleted(ID1);
    assertCompleted(ID2);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(2);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void downloads_withDifferentIds_maxDownloadsIsOne_executedSequentially() throws Throwable {
    setupDownloadManager(/* maxParallelDownloads= */ 1);
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);
    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();

    // The second download should be queued and the first one should be able to complete.
    assertNoDownloaderAt(1);
    assertQueued(ID2);
    downloader0.finish();
    assertCompleted(ID1);

    // The second download can start once the first one has completed.
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID2);
    downloader1.assertDownloadStarted();
    downloader1.finish();
    assertCompleted(ID2);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(2);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void downloadAndRemove_withDifferentIds_maxDownloadsIsOne_executeInParallel()
      throws Throwable {
    setupDownloadManager(/* maxParallelDownloads= */ 1);

    // Complete a download so that we can remove it.
    postDownloadRequest(ID1);
    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();
    downloader0.finish();

    // Request removal of the first download, and downloading of a second download.
    postRemoveRequest(ID1);
    postDownloadRequest(ID2);

    // The removal and download should proceed in parallel.
    FakeDownloader downloader1 = getDownloaderAt(1);
    FakeDownloader downloader2 = getDownloaderAt(2);
    downloader1.assertId(ID1);
    downloader2.assertId(ID2);
    downloader1.assertRemoveStarted();
    downloader2.assertDownloadStarted();
    downloader1.finish();
    downloader2.finish();
    assertRemoved(ID1);
    assertCompleted(ID2);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(3);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void downloadAfterRemove_maxDownloadIsOne_isNotStarted() throws Throwable {
    setupDownloadManager(/* maxParallelDownloads= */ 1);
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);
    postRemoveRequest(ID2);
    postDownloadRequest(ID2);

    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();

    // The second download shouldn't have been started, so the second downloader is for removal.
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID2);
    downloader1.assertRemoveStarted();
    downloader1.finish();
    // A downloader to re-download the second download should not be started.
    assertNoDownloaderAt(2);
    // The first download should be able to complete.
    downloader0.finish();
    assertCompleted(ID1);

    // Now the first download has completed, the second download should start.
    FakeDownloader downloader2 = getDownloaderAt(2);
    downloader2.assertId(ID2);
    downloader2.assertDownloadStarted();
    downloader2.finish();
    assertCompleted(ID2);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(3);
    assertDownloadIndexSize(2);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void pauseAndResume_pausesAndResumesDownload() throws Throwable {
    postDownloadRequest(ID1);
    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();

    postPauseDownloads();
    downloader0.assertCanceled();
    assertQueued(ID1);

    postResumeDownloads();
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertDownloadStarted();
    downloader1.finish();
    assertCompleted(ID1);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void pause_doesNotCancelRemove() throws Throwable {
    postDownloadRequest(ID1);
    postRemoveRequest(ID1);
    FakeDownloader downloader = getDownloaderAt(1);
    downloader.assertId(ID1);
    downloader.assertRemoveStarted();

    postPauseDownloads();
    downloader.finish();
    assertRemoved(ID1);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(0);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void setAndClearStopReason_stopsAndRestartsDownload() throws Throwable {
    postDownloadRequest(ID1);
    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();

    postSetStopReason(ID1, APP_STOP_REASON);
    downloader0.assertCanceled();
    assertStopped(ID1);

    postSetStopReason(ID1, Download.STOP_REASON_NONE);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertDownloadStarted();
    downloader1.finish();

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(1);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void setStopReason_doesNotStopOtherDownload() throws Throwable {
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);

    FakeDownloader downloader0 = getDownloaderAt(0);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader0.assertId(ID1);
    downloader1.assertId(ID2);
    downloader0.assertDownloadStarted();
    downloader1.assertDownloadStarted();

    postSetStopReason(ID1, APP_STOP_REASON);
    downloader0.assertCanceled();
    assertStopped(ID1);

    // The second download should still complete.
    downloader1.finish();
    assertCompleted(ID2);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(2);
    assertCurrentDownloadCount(1);
  }

  @Test
  public void remove_removesStoppedDownload() throws Throwable {
    postDownloadRequest(ID1);
    FakeDownloader downloader0 = getDownloaderAt(0);
    downloader0.assertId(ID1);
    downloader0.assertDownloadStarted();

    postSetStopReason(ID1, APP_STOP_REASON);
    downloader0.assertCanceled();
    assertStopped(ID1);

    postRemoveRequest(ID1);
    FakeDownloader downloader1 = getDownloaderAt(1);
    downloader1.assertId(ID1);
    downloader1.assertRemoveStarted();
    downloader1.finish();
    assertRemoved(ID1);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
    assertDownloaderCount(2);
    assertDownloadIndexSize(0);
    assertCurrentDownloadCount(0);
  }

  @Test
  public void getCurrentDownloads_returnsCurrentDownloads() throws Throwable {
    setupDownloadManager(/* maxParallelDownloads= */ 1);
    postDownloadRequest(ID1);
    postDownloadRequest(ID2);
    postDownloadRequest(ID3);
    postRemoveRequest(ID3);

    assertRemoving(ID3); // Blocks until the downloads will be visible.

    List<Download> downloads = postGetCurrentDownloads();
    assertThat(downloads).hasSize(3);
    Download download0 = downloads.get(0);
    assertThat(download0.request.id).isEqualTo(ID1);
    assertThat(download0.state).isEqualTo(Download.STATE_DOWNLOADING);
    Download download1 = downloads.get(1);
    assertThat(download1.request.id).isEqualTo(ID2);
    assertThat(download1.state).isEqualTo(Download.STATE_QUEUED);
    Download download2 = downloads.get(2);
    assertThat(download2.request.id).isEqualTo(ID3);
    assertThat(download2.state).isEqualTo(Download.STATE_REMOVING);
  }

  @Test
  public void addDownload_whilstRemovingWithStopReason_addsStartedDownload() throws Throwable {
    runOnMainThread(
        () -> downloadManager.addDownload(createDownloadRequest(ID1), /* stopReason= */ 1234));

    postRemoveRequest(ID1);
    FakeDownloader downloadRemover = getDownloaderAt(0);
    downloadRemover.assertRemoveStarted();

    // Re-add the download without a stop reason.
    postDownloadRequest(ID1);

    downloadRemover.finish();

    FakeDownloader downloader = getDownloaderAt(1);
    downloader.finish();
    assertCompleted(ID1);

    assertDownloadIndexSize(1);
    // We expect one downloader for the removal, and one for when the download was re-added.
    assertDownloaderCount(2);
    // The download has completed, and so is no longer current.
    assertCurrentDownloadCount(0);

    Download download = postGetDownloadIndex().getDownload(ID1);
    assertThat(download.state).isEqualTo(Download.STATE_COMPLETED);
    assertThat(download.stopReason).isEqualTo(0);
  }

  /** Test for https://github.com/google/ExoPlayer/issues/8419 */
  @Test
  public void addDownloadWithStopReason_whilstRemoving_addsStoppedDownload() throws Throwable {
    postDownloadRequest(ID1);
    getDownloaderAt(0).finish();
    assertCompleted(ID1);

    postRemoveRequest(ID1);
    FakeDownloader downloadRemover = getDownloaderAt(1);
    downloadRemover.assertRemoveStarted();

    // Re-add the download with a stop reason.
    runOnMainThread(
        () -> downloadManager.addDownload(createDownloadRequest(ID1), /* stopReason= */ 1234));

    downloadRemover.finish();
    downloadManagerListener.blockUntilIdle();

    assertDownloadIndexSize(1);
    // We expect one downloader for the initial download, and one for the removal. A downloader
    // should not be created when the download is re-added, since a stop reason is specified.
    assertDownloaderCount(2);
    // The download isn't completed, and is therefore still current.
    assertCurrentDownloadCount(1);

    List<Download> downloads = postGetCurrentDownloads();
    Download download = downloads.get(0);
    assertThat(download.request.id).isEqualTo(ID1);
    assertThat(download.state).isEqualTo(Download.STATE_STOPPED);
    assertThat(download.stopReason).isEqualTo(1234);
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

  @Test
  public void removeRequests_runSequentially() throws Throwable {
    // Trigger two remove requests.
    postDownloadRequest(ID1);
    getDownloaderAt(0).finish();
    postDownloadRequest(ID2);
    getDownloaderAt(1).finish();
    postRemoveRequest(ID1);
    postRemoveRequest(ID2);

    // Assert first remove request is executing, second one is queued.
    assertRemoving(ID1);
    assertQueued(ID2);
    FakeDownloader downloader2 = getDownloaderAt(2);
    downloader2.assertId(ID1);
    downloader2.assertRemoveStarted();
    downloader2.finish();
    assertRemoved(ID1);

    // Assert second one is running after first one finished
    assertRemoving(ID2);
    FakeDownloader downloader3 = getDownloaderAt(3);
    downloader3.assertId(ID2);
    downloader3.assertRemoveStarted();
    downloader3.finish();
    assertRemoved(ID2);

    downloadManagerListener.blockUntilIdleAndThrowAnyFailure();
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
    runOnMainThread(() -> currentDownloadsReference.set(downloadManager.getCurrentDownloads()));
    return currentDownloadsReference.get();
  }

  private DownloadIndex postGetDownloadIndex() {
    AtomicReference<DownloadIndex> downloadIndexReference = new AtomicReference<>();
    runOnMainThread(() -> downloadIndexReference.set(downloadManager.getDownloadIndex()));
    return downloadIndexReference.get();
  }

  private void runOnMainThread(TestRunnable r) {
    testThread.runTestOnMainThread(r);
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
    return new DownloadRequest.Builder(id, Uri.parse("http://abc.com/ " + id))
        .setStreamKeys(asList(keys))
        .build();
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
      downloadStarted = createRobolectricConditionVariable();
      removeStarted = createRobolectricConditionVariable();
      finished = createRobolectricConditionVariable();
      blocker = createRobolectricConditionVariable();
      startCount = new AtomicInteger();
      bytesDownloaded = new AtomicInteger();
    }

    @Override
    public void cancel() {
      canceled = true;
      blocker.open();
    }

    @Override
    public void download(ProgressListener listener) throws IOException {
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
    public void remove() {
      startCount.incrementAndGet();
      removeStarted.open();
      try {
        block();
      } finally {
        finished.open();
      }
    }

    /** Finishes the {@link #download} or {@link #remove} without an error. */
    public void finish() throws InterruptedException {
      blocker.open();
      blockUntilFinished();
    }

    /** Fails {@link #download} or {@link #remove} with an error. */
    public void fail() throws InterruptedException {
      enableDownloadIOException = true;
      blocker.open();
      blockUntilFinished();
    }

    /** Increments the number of bytes that the fake downloader has downloaded. */
    public void incrementBytesDownloaded() {
      bytesDownloaded.incrementAndGet();
    }

    public void assertId(String id) {
      assertThat(request.id).isEqualTo(id);
    }

    public void assertStreamKeys(StreamKey... streamKeys) {
      assertThat(request.streamKeys).containsExactlyElementsIn(streamKeys);
    }

    public void assertDownloadStarted() throws InterruptedException {
      assertThat(downloadStarted.block(TIMEOUT_MS)).isTrue();
      downloadStarted.close();
    }

    public void assertRemoveStarted() throws InterruptedException {
      assertThat(removeStarted.block(TIMEOUT_MS)).isTrue();
      removeStarted.close();
    }

    public void assertCanceled() throws InterruptedException {
      blockUntilFinished();
      assertThat(canceled).isTrue();
    }

    // Internal methods.

    private void block() {
      try {
        blocker.block();
      } catch (InterruptedException e) {
        throw new IllegalStateException(e); // Never happens.
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
