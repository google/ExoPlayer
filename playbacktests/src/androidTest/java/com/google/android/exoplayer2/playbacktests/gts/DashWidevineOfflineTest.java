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

import android.media.MediaDrm.MediaDrmStateException;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Pair;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;
import com.google.android.exoplayer2.source.dash.DashUtil;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.HostActivity;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import junit.framework.Assert;

/**
 * Tests Widevine encrypted DASH playbacks using offline keys.
 */
public final class DashWidevineOfflineTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "DashWidevineOfflineTest";
  private static final String USER_AGENT = "ExoPlayerPlaybackTests";

  private DashTestRunner testRunner;
  private DefaultHttpDataSourceFactory httpDataSourceFactory;
  private OfflineLicenseHelper<FrameworkMediaCrypto> offlineLicenseHelper;
  private byte[] offlineLicenseKeySetId;

  public DashWidevineOfflineTest() {
    super(HostActivity.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    testRunner = new DashTestRunner(TAG, getActivity(), getInstrumentation())
        .setStreamName("test_widevine_h264_fixed_offline")
        .setManifestUrl(DashTestData.WIDEVINE_H264_MANIFEST)
        .setWidevineInfo(MimeTypes.VIDEO_H264, true)
        .setFullPlaybackNoSeeking(true)
        .setCanIncludeAdditionalVideoFormats(false)
        .setAudioVideoFormats(DashTestData.WIDEVINE_AAC_AUDIO_REPRESENTATION_ID,
            DashTestData.WIDEVINE_H264_CDD_FIXED);

    boolean useL1Widevine = DashTestRunner.isL1WidevineAvailable(MimeTypes.VIDEO_H264);
    String widevineLicenseUrl = DashTestData.getWidevineLicenseUrl(true, useL1Widevine);
    httpDataSourceFactory = new DefaultHttpDataSourceFactory(USER_AGENT);
    offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(widevineLicenseUrl,
        httpDataSourceFactory);
  }

  @Override
  protected void tearDown() throws Exception {
    testRunner = null;
    if (offlineLicenseKeySetId != null) {
      releaseLicense();
    }
    if (offlineLicenseHelper != null) {
      offlineLicenseHelper.release();
    }
    offlineLicenseHelper = null;
    httpDataSourceFactory = null;
    super.tearDown();
  }

  // Offline license tests

  public void testWidevineOfflineLicense() throws Exception {
    if (Util.SDK_INT < 22) {
      return; // Pass.
    }
    downloadLicense();
    testRunner.run();

    // Renew license after playback should still work
    offlineLicenseKeySetId = offlineLicenseHelper.renewLicense(offlineLicenseKeySetId);
    Assert.assertNotNull(offlineLicenseKeySetId);
  }

  public void testWidevineOfflineReleasedLicense() throws Throwable {
    if (Util.SDK_INT < 22) {
      return; // Pass.
    }
    downloadLicense();
    releaseLicense(); // keySetId no longer valid.

    try {
      testRunner.run();
      fail("Playback should fail because the license has been released.");
    } catch (Throwable e) {
      // Get the root cause
      while (true) {
        Throwable cause = e.getCause();
        if (cause == null || cause == e) {
          break;
        }
        e = cause;
      }
      // It should be a MediaDrmStateException instance
      if (!(e instanceof MediaDrmStateException)) {
        throw e;
      }
    }
  }

  public void testWidevineOfflineExpiredLicense() throws Exception {
    if (Util.SDK_INT < 22) {
      return; // Pass.
    }
    downloadLicense();

    // Wait until the license expires
    long licenseDuration =
        offlineLicenseHelper.getLicenseDurationRemainingSec(offlineLicenseKeySetId).first;
    assertTrue("License duration should be less than 30 sec. "
        + "Server settings might have changed.", licenseDuration < 30);
    while (licenseDuration > 0) {
      synchronized (this) {
        wait(licenseDuration * 1000 + 2000);
      }
      long previousDuration = licenseDuration;
      licenseDuration =
          offlineLicenseHelper.getLicenseDurationRemainingSec(offlineLicenseKeySetId).first;
      assertTrue("License duration should be decreasing.", previousDuration > licenseDuration);
    }

    // DefaultDrmSessionManager should renew the license and stream play fine
    testRunner.run();
  }

  public void testWidevineOfflineLicenseExpiresOnPause() throws Exception {
    if (Util.SDK_INT < 22) {
      return; // Pass.
    }
    downloadLicense();

    // During playback pause until the license expires then continue playback
    Pair<Long, Long> licenseDurationRemainingSec =
        offlineLicenseHelper.getLicenseDurationRemainingSec(offlineLicenseKeySetId);
    long licenseDuration = licenseDurationRemainingSec.first;
    assertTrue("License duration should be less than 30 sec. "
        + "Server settings might have changed.", licenseDuration < 30);
    ActionSchedule schedule = new ActionSchedule.Builder(TAG)
        .delay(3000).pause().delay(licenseDuration * 1000 + 2000).play().build();

    // DefaultDrmSessionManager should renew the license and stream play fine
    testRunner.setActionSchedule(schedule).run();
  }

  private void downloadLicense() throws InterruptedException, DrmSessionException, IOException {
    DataSource dataSource = httpDataSourceFactory.createDataSource();
    DashManifest dashManifest = DashUtil.loadManifest(dataSource,
        DashTestData.WIDEVINE_H264_MANIFEST);
    DrmInitData drmInitData = DashUtil.loadDrmInitData(dataSource, dashManifest.getPeriod(0));
    offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(drmInitData);
    Assert.assertNotNull(offlineLicenseKeySetId);
    Assert.assertTrue(offlineLicenseKeySetId.length > 0);
    testRunner.setOfflineLicenseKeySetId(offlineLicenseKeySetId);
  }

  private void releaseLicense() throws DrmSessionException {
    offlineLicenseHelper.releaseLicense(offlineLicenseKeySetId);
    offlineLicenseKeySetId = null;
  }

}
