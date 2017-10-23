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
package com.google.android.exoplayer2.playbacktests.gts;

import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import com.google.android.exoplayer2.offline.Downloader;
import com.google.android.exoplayer2.offline.Downloader.ProgressListener;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.RepresentationKey;
import com.google.android.exoplayer2.source.dash.offline.DashDownloader;
import com.google.android.exoplayer2.testutil.HostActivity;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.DummyDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests downloaded DASH playbacks.
 */
public final class DashDownloadTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "DashDownloadTest";

  private DashTestRunner testRunner;
  private File tempFolder;
  private SimpleCache cache;

  public DashDownloadTest() {
    super(HostActivity.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    testRunner = new DashTestRunner(TAG, getActivity(), getInstrumentation())
        .setManifestUrl(DashTestData.H264_MANIFEST)
        .setFullPlaybackNoSeeking(true)
        .setCanIncludeAdditionalVideoFormats(false)
        .setAudioVideoFormats(DashTestData.AAC_AUDIO_REPRESENTATION_ID,
            DashTestData.H264_CDD_FIXED);
    tempFolder = Util.createTempDirectory(getActivity(), "ExoPlayerTest");
    cache = new SimpleCache(tempFolder, new NoOpCacheEvictor());
  }

  @Override
  protected void tearDown() throws Exception {
    testRunner = null;
    Util.recursiveDelete(tempFolder);
    cache = null;
    super.tearDown();
  }

  // Download tests

  public void testDownload() throws Exception {
    if (Util.SDK_INT < 16) {
      return; // Pass.
    }

    // Download manifest only
    createDashDownloader(false).getManifest();
    long manifestLength = cache.getCacheSpace();

    // Download representations
    DashDownloader dashDownloader = downloadContent(false, Float.NaN);
    assertEquals(cache.getCacheSpace() - manifestLength, dashDownloader.getDownloadedBytes());

    testRunner.setStreamName("test_h264_fixed_download").
        setDataSourceFactory(newOfflineCacheDataSourceFactory()).run();

    dashDownloader.remove();

    assertEquals("There should be no content left.", 0, cache.getKeys().size());
    assertEquals("There should be no content left.", 0, cache.getCacheSpace());
  }

  public void testPartialDownload() throws Exception {
    if (Util.SDK_INT < 16) {
      return; // Pass.
    }

    // Just download the first half and manifest
    downloadContent(false, 0.5f);

    // Download the rest
    DashDownloader dashDownloader = downloadContent(false, Float.NaN);
    long downloadedBytes = dashDownloader.getDownloadedBytes();

    // Make sure it doesn't download any data
    dashDownloader = downloadContent(true, Float.NaN);
    assertEquals(downloadedBytes, dashDownloader.getDownloadedBytes());

    testRunner.setStreamName("test_h264_fixed_partial_download")
        .setDataSourceFactory(newOfflineCacheDataSourceFactory()).run();
  }

  private DashDownloader downloadContent(boolean offline, float stopAt) throws Exception {
    DashDownloader dashDownloader = createDashDownloader(offline);
    DashManifest dashManifest = dashDownloader.getManifest();
    try {
      ArrayList<RepresentationKey> keys = new ArrayList<>();
      for (int pIndex = 0; pIndex < dashManifest.getPeriodCount(); pIndex++) {
        List<AdaptationSet> adaptationSets = dashManifest.getPeriod(pIndex).adaptationSets;
        for (int aIndex = 0; aIndex < adaptationSets.size(); aIndex++) {
          AdaptationSet adaptationSet = adaptationSets.get(aIndex);
          List<Representation> representations = adaptationSet.representations;
          for (int rIndex = 0; rIndex < representations.size(); rIndex++) {
            String id = representations.get(rIndex).format.id;
            if (DashTestData.AAC_AUDIO_REPRESENTATION_ID.equals(id)
                || DashTestData.H264_CDD_FIXED.equals(id)) {
              keys.add(new RepresentationKey(pIndex, aIndex, rIndex));
            }
          }
        }
        dashDownloader.selectRepresentations(keys.toArray(new RepresentationKey[keys.size()]));
        TestProgressListener listener = new TestProgressListener(stopAt);
        dashDownloader.download(listener);
      }
    } catch (InterruptedException e) {
      // do nothing
    } catch (IOException e) {
      Throwable exception = e;
      while (!(exception instanceof InterruptedIOException)) {
        if (exception == null) {
          throw e;
        }
        exception = exception.getCause();
      }
      // else do nothing
    }
    return dashDownloader;
  }

  private DashDownloader createDashDownloader(boolean offline) {
    DownloaderConstructorHelper constructorHelper = new DownloaderConstructorHelper(cache,
        offline ? DummyDataSource.FACTORY : new DefaultHttpDataSourceFactory("ExoPlayer", null));
    return new DashDownloader(Uri.parse(DashTestData.H264_MANIFEST), constructorHelper);
  }

  private CacheDataSourceFactory newOfflineCacheDataSourceFactory() {
    return new CacheDataSourceFactory(cache, DummyDataSource.FACTORY,
        CacheDataSource.FLAG_BLOCK_ON_CACHE);
  }

  private static class TestProgressListener implements ProgressListener {

    private final float stopAt;

    private TestProgressListener(float stopAt) {
      this.stopAt = stopAt;
    }

    @Override
    public void onDownloadProgress(Downloader downloader, float downloadPercentage,
        long downloadedBytes) {
      Log.d("DashDownloadTest",
          String.format("onDownloadProgress downloadPercentage = [%g], downloadedData = [%d]%n",
          downloadPercentage, downloadedBytes));
      if (downloadPercentage >= stopAt) {
        Thread.currentThread().interrupt();
      }
    }

  }

}
