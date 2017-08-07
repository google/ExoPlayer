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

import android.test.ActivityInstrumentationTestCase2;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.HostActivity;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

/**
 * Test playback of encrypted DASH streams using different CENC scheme types.
 */
public final class CommonEncryptionDrmTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "CencDrmTest";

  private static final String URL_cenc =
      "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd";
  private static final String URL_cbc1 =
      "https://storage.googleapis.com/wvmedia/cbc1/h264/tears/tears_aes_cbc1.mpd";
  private static final String URL_cbcs =
      "https://storage.googleapis.com/wvmedia/cbcs/h264/tears/tears_aes_cbcs.mpd";
  private static final String ID_AUDIO = "0";
  private static final String[] IDS_VIDEO = new String[] {"1", "2"};

  // Seeks help reproduce playback issues in certain devices.
  private static final ActionSchedule ACTION_SCHEDULE_WITH_SEEKS = new ActionSchedule.Builder(TAG)
      .delay(30000).seek(300000).delay(10000).seek(270000).delay(10000).seek(200000).delay(10000)
      .stop().build();

  private DashTestRunner testRunner;

  public CommonEncryptionDrmTest() {
    super(HostActivity.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    testRunner = new DashTestRunner(TAG, getActivity(), getInstrumentation())
        .setWidevineInfo(MimeTypes.VIDEO_H264, false)
        .setActionSchedule(ACTION_SCHEDULE_WITH_SEEKS)
        .setAudioVideoFormats(ID_AUDIO, IDS_VIDEO)
        .setCanIncludeAdditionalVideoFormats(true);
  }

  @Override
  protected void tearDown() throws Exception {
    testRunner = null;
    super.tearDown();
  }

  public void testCencSchemeType() {
    if (Util.SDK_INT < 18) {
      // Pass.
      return;
    }
    testRunner.setStreamName("test_widevine_h264_scheme_cenc").setManifestUrl(URL_cenc).run();
  }

  public void testCbc1SchemeType() {
    if (Util.SDK_INT < 24) {
      // Pass.
      return;
    }
    testRunner.setStreamName("test_widevine_h264_scheme_cbc1").setManifestUrl(URL_cbc1).run();
  }

  public void testCbcsSchemeType() {
    if (Util.SDK_INT < 24) {
      // Pass.
      return;
    }
    testRunner.setStreamName("test_widevine_h264_scheme_cbcs").setManifestUrl(URL_cbcs).run();
  }

  public void testCensSchemeType() {
    // TODO: Implement once content is available. Track [internal: b/31219813].
  }

}
