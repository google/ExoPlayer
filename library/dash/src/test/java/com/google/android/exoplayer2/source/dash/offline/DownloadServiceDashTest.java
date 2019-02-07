/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.dash.offline;

import static com.google.android.exoplayer2.source.dash.offline.DashDownloadTestData.TEST_MPD;
import static com.google.android.exoplayer2.source.dash.offline.DashDownloadTestData.TEST_MPD_URI;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCacheEmpty;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCachedData;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.testutil.TestDownloadManagerListener;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit tests for {@link DownloadService}. */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class DownloadServiceDashTest {

  private SimpleCache cache;
  private File tempFolder;
  private FakeDataSet fakeDataSet;
  private StreamKey fakeStreamKey1;
  private StreamKey fakeStreamKey2;
  private Context context;
  private DownloadService dashDownloadService;
  private ConditionVariable pauseDownloadCondition;
  private TestDownloadManagerListener downloadManagerListener;
  private DummyMainThread dummyMainThread;

  @Before
  public void setUp() throws IOException {
    dummyMainThread = new DummyMainThread();
    context = RuntimeEnvironment.application;
    tempFolder = Util.createTempDirectory(context, "ExoPlayerTest");
    cache = new SimpleCache(tempFolder, new NoOpCacheEvictor());

    Runnable pauseAction =
        () -> {
          if (pauseDownloadCondition != null) {
            try {
              pauseDownloadCondition.block();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        };
    fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .newData("audio_init_data")
            .appendReadAction(pauseAction)
            .appendReadData(TestUtil.buildTestData(10))
            .endData()
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6)
            .setRandomData("text_segment_1", 1)
            .setRandomData("text_segment_2", 2)
            .setRandomData("text_segment_3", 3);
    final DataSource.Factory fakeDataSourceFactory =
        new FakeDataSource.Factory().setFakeDataSet(fakeDataSet);
    fakeStreamKey1 = new StreamKey(0, 0, 0);
    fakeStreamKey2 = new StreamKey(0, 1, 0);

    dummyMainThread.runOnMainThread(
        () -> {
          File actionFile;
          try {
            actionFile = Util.createTempFile(context, "ExoPlayerTest");
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          actionFile.delete();
          final DownloadManager dashDownloadManager =
              new DownloadManager(
                  RuntimeEnvironment.application,
                  actionFile,
                  new DefaultDownloaderFactory(
                      new DownloaderConstructorHelper(cache, fakeDataSourceFactory)),
                  /* maxSimultaneousDownloads= */ 1,
                  /* minRetryCount= */ 3,
                  new Requirements(0));
          downloadManagerListener =
              new TestDownloadManagerListener(dashDownloadManager, dummyMainThread);
          dashDownloadManager.startDownloads();

          dashDownloadService =
              new DownloadService(DownloadService.FOREGROUND_NOTIFICATION_ID_NONE) {
                @Override
                protected DownloadManager getDownloadManager() {
                  return dashDownloadManager;
                }

                @Nullable
                @Override
                protected Scheduler getScheduler() {
                  return null;
                }
              };
          dashDownloadService.onCreate();
        });
  }

  @After
  public void tearDown() {
    dummyMainThread.runOnMainThread(() -> dashDownloadService.onDestroy());
    Util.recursiveDelete(tempFolder);
    dummyMainThread.release();
  }

  @Ignore // b/78877092
  @Test
  public void testMultipleDownloadAction() throws Throwable {
    downloadKeys(fakeStreamKey1);
    downloadKeys(fakeStreamKey2);

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();

    assertCachedData(cache, fakeDataSet);
  }

  @Ignore // b/78877092
  @Test
  public void testRemoveAction() throws Throwable {
    downloadKeys(fakeStreamKey1, fakeStreamKey2);

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();

    removeAll();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();

    assertCacheEmpty(cache);
  }

  @Ignore // b/78877092
  @Test
  public void testRemoveBeforeDownloadComplete() throws Throwable {
    pauseDownloadCondition = new ConditionVariable();
    downloadKeys(fakeStreamKey1, fakeStreamKey2);

    removeAll();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();

    assertCacheEmpty(cache);
  }

  private void removeAll() throws Throwable {
    callDownloadServiceOnStart(newAction(TEST_MPD_URI, true, null));
  }

  private void downloadKeys(StreamKey... keys) {
    callDownloadServiceOnStart(newAction(TEST_MPD_URI, false, null, keys));
  }

  private void callDownloadServiceOnStart(final DownloadAction action) {
    dummyMainThread.runOnMainThread(
        () -> {
          Intent startIntent =
              DownloadService.buildAddActionIntent(context, DownloadService.class, action, false);
          dashDownloadService.onStartCommand(startIntent, 0, 0);
        });
  }

  private static DownloadAction newAction(
      Uri uri, boolean isRemoveAction, @Nullable byte[] data, StreamKey... keys) {
    ArrayList<StreamKey> keysList = new ArrayList<>();
    Collections.addAll(keysList, keys);
    DownloadAction result;
    if (isRemoveAction) {
      result =
          DownloadAction.createRemoveAction(
              DownloadAction.TYPE_DASH, uri, /* customCacheKey= */ null);
    } else {
      result =
          DownloadAction.createDownloadAction(
              DownloadAction.TYPE_DASH, uri, keysList, /* customCacheKey= */ null, data);
    }
    return result;
  }
}
