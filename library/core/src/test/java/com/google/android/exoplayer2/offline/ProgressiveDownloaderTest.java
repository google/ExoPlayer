/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.testutil.FailOnCloseDataSink;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ProgressiveDownloader}. */
@RunWith(AndroidJUnit4.class)
public class ProgressiveDownloaderTest {

  private File testDir;
  private Cache downloadCache;

  @Before
  public void createDownloadCache() throws Exception {
    testDir =
        Util.createTempFile(
            ApplicationProvider.getApplicationContext(), "ProgressiveDownloaderTest");
    assertThat(testDir.delete()).isTrue();
    assertThat(testDir.mkdirs()).isTrue();

    DatabaseProvider databaseProvider = TestUtil.getInMemoryDatabaseProvider();
    downloadCache = new SimpleCache(testDir, new NoOpCacheEvictor(), databaseProvider);
  }

  @After
  public void deleteDownloadCache() {
    downloadCache.release();
    Util.recursiveDelete(testDir);
  }

  @Test
  public void download_afterReadFailure_succeeds() throws Exception {
    Uri uri = Uri.parse("test:///test.mp4");

    // Fake data has a built in failure after 10 bytes.
    FakeDataSet data = new FakeDataSet();
    data.newData(uri).appendReadData(10).appendReadError(new IOException()).appendReadData(20);
    DataSource.Factory upstreamDataSource = new FakeDataSource.Factory().setFakeDataSet(data);

    MediaItem mediaItem = MediaItem.fromUri(uri);
    CacheDataSource.Factory cacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(upstreamDataSource);
    ProgressiveDownloader downloader = new ProgressiveDownloader(mediaItem, cacheDataSourceFactory);

    TestProgressListener progressListener = new TestProgressListener();

    // Failure expected after 10 bytes.
    assertThrows(IOException.class, () -> downloader.download(progressListener));
    assertThat(progressListener.bytesDownloaded).isEqualTo(10);

    // Retry should succeed.
    downloader.download(progressListener);
    assertThat(progressListener.bytesDownloaded).isEqualTo(30);
  }

  @Test
  public void download_afterWriteFailureOnClose_succeeds() throws Exception {
    Uri uri = Uri.parse("test:///test.mp4");

    FakeDataSet data = new FakeDataSet();
    data.newData(uri).appendReadData(1024);
    DataSource.Factory upstreamDataSource = new FakeDataSource.Factory().setFakeDataSet(data);

    AtomicBoolean failOnClose = new AtomicBoolean(/* initialValue= */ true);
    FailOnCloseDataSink.Factory dataSinkFactory =
        new FailOnCloseDataSink.Factory(downloadCache, failOnClose);

    MediaItem mediaItem = MediaItem.fromUri(uri);
    CacheDataSource.Factory cacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(dataSinkFactory)
            .setUpstreamDataSourceFactory(upstreamDataSource);
    ProgressiveDownloader downloader = new ProgressiveDownloader(mediaItem, cacheDataSourceFactory);

    TestProgressListener progressListener = new TestProgressListener();

    // Failure expected after 1024 bytes.
    assertThrows(IOException.class, () -> downloader.download(progressListener));
    assertThat(progressListener.bytesDownloaded).isEqualTo(1024);

    failOnClose.set(false);

    // Retry should succeed.
    downloader.download(progressListener);
    assertThat(progressListener.bytesDownloaded).isEqualTo(1024);
  }

  @Test
  public void download_afterPriorityTooLow_succeeds() throws Exception {
    PriorityTaskManager priorityTaskManager = new PriorityTaskManager();
    AtomicBoolean hasSetPlaybackPriority = new AtomicBoolean(/* initialValue= */ false);
    Uri uri = Uri.parse("test:///test.mp4");
    // Fake data which briefly sets the playback priority while downloading for the first time.
    FakeDataSet data = new FakeDataSet();
    data.newData(uri)
        .appendReadAction(
            () -> {
              if (!hasSetPlaybackPriority.getAndSet(true)) {
                // This only interrupts the download the next time the DataSource checks for the
                // priority, so delay the removal of the playback priority slightly. As we can't
                // check when the DataSource throws the PriorityTooLowException exactly, we need to
                // use an arbitrary delay.
                priorityTaskManager.add(C.PRIORITY_PLAYBACK);
                new Thread(
                        () -> {
                          try {
                            Thread.sleep(200);
                          } catch (InterruptedException e) {
                            // Ignore.
                          }
                          priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
                        })
                    .start();
              }
            })
        .appendReadData(2_000_000);
    DataSource.Factory upstreamDataSource = new FakeDataSource.Factory().setFakeDataSet(data);
    MediaItem mediaItem = MediaItem.fromUri(uri);
    CacheDataSource.Factory cacheDataSourceFactory =
        new CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(upstreamDataSource)
            .setUpstreamPriorityTaskManager(priorityTaskManager);
    ProgressiveDownloader downloader = new ProgressiveDownloader(mediaItem, cacheDataSourceFactory);
    TestProgressListener progressListener = new TestProgressListener();

    // Download expected to finish (despite the interruption by the higher playback priority).
    downloader.download(progressListener);

    assertThat(progressListener.bytesDownloaded).isEqualTo(2_000_000);
  }

  private static final class TestProgressListener implements Downloader.ProgressListener {

    public long bytesDownloaded;

    @Override
    public void onProgress(long contentLength, long bytesDownloaded, float percentDownloaded) {
      this.bytesDownloaded = bytesDownloaded;
    }
  }
}
