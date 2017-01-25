/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.media.MediaDrm;
import android.media.MediaDrm.MediaDrmStateException;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.playbacktests.util.ActionSchedule;
import com.google.android.exoplayer2.playbacktests.util.DebugSimpleExoPlayer;
import com.google.android.exoplayer2.playbacktests.util.DecoderCountersUtil;
import com.google.android.exoplayer2.playbacktests.util.ExoHostedTest;
import com.google.android.exoplayer2.playbacktests.util.HostActivity;
import com.google.android.exoplayer2.playbacktests.util.MetricsLogger;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import junit.framework.AssertionFailedError;

/**
 * Tests DASH playbacks using {@link ExoPlayer}.
 */
public final class DashTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "DashTest";
  private static final String VIDEO_TAG = TAG + ":Video";
  private static final String AUDIO_TAG = TAG + ":Audio";
  private static final String REPORT_NAME = "GtsExoPlayerTestCases";
  private static final String REPORT_OBJECT_NAME = "playbacktest";
  private static final int VIDEO_RENDERER_INDEX = 0;
  private static final int AUDIO_RENDERER_INDEX = 1;

  private static final long TEST_TIMEOUT_MS = 5 * 60 * 1000;
  private static final int MIN_LOADABLE_RETRY_COUNT = 10;
  private static final int MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES = 10;
  private static final float MAX_DROPPED_VIDEO_FRAME_FRACTION = 0.01f;

  private static final String MANIFEST_URL_PREFIX = "https://storage.googleapis.com/exoplayer-test-"
      + "media-1/gen-3/screens/dash-vod-single-segment/";
  // Clear content manifests.
  private static final String H264_MANIFEST = "manifest-h264.mpd";
  private static final String H265_MANIFEST = "manifest-h265.mpd";
  private static final String VP9_MANIFEST = "manifest-vp9.mpd";
  private static final String H264_23_MANIFEST = "manifest-h264-23.mpd";
  private static final String H264_24_MANIFEST = "manifest-h264-24.mpd";
  private static final String H264_29_MANIFEST = "manifest-h264-29.mpd";
  // Widevine encrypted content manifests.
  private static final String WIDEVINE_H264_MANIFEST_PREFIX = "manifest-h264-enc";
  private static final String WIDEVINE_H265_MANIFEST_PREFIX = "manifest-h265-enc";
  private static final String WIDEVINE_VP9_MANIFEST_PREFIX = "manifest-vp9-enc";
  private static final String WIDEVINE_H264_23_MANIFEST_PREFIX = "manifest-h264-23-enc";
  private static final String WIDEVINE_H264_24_MANIFEST_PREFIX = "manifest-h264-24-enc";
  private static final String WIDEVINE_H264_29_MANIFEST_PREFIX = "manifest-h264-29-enc";
  private static final String WIDEVINE_L1_SUFFIX = "-hw.mpd";
  private static final String WIDEVINE_L3_SUFFIX = "-sw.mpd";

  private static final String AAC_AUDIO_REPRESENTATION_ID = "141";
  private static final String H264_BASELINE_240P_VIDEO_REPRESENTATION_ID = "avc-baseline-240";
  private static final String H264_BASELINE_480P_VIDEO_REPRESENTATION_ID = "avc-baseline-480";
  private static final String H264_MAIN_240P_VIDEO_REPRESENTATION_ID = "avc-main-240";
  private static final String H264_MAIN_480P_VIDEO_REPRESENTATION_ID = "avc-main-480";
  // The highest quality H264 format mandated by the Android CDD.
  private static final String H264_CDD_FIXED = Util.SDK_INT < 23
      ? H264_BASELINE_480P_VIDEO_REPRESENTATION_ID : H264_MAIN_480P_VIDEO_REPRESENTATION_ID;
  // Multiple H264 formats mandated by the Android CDD. Note: The CDD actually mandated main profile
  // support from API level 23, but we opt to test only from 24 due to known issues on API level 23
  // when switching between baseline and main profiles on certain devices.
  private static final String[] H264_CDD_ADAPTIVE = Util.SDK_INT < 24
      ? new String[] {
          H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          H264_BASELINE_480P_VIDEO_REPRESENTATION_ID}
      : new String[] {
          H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          H264_BASELINE_480P_VIDEO_REPRESENTATION_ID,
          H264_MAIN_240P_VIDEO_REPRESENTATION_ID,
          H264_MAIN_480P_VIDEO_REPRESENTATION_ID};

  private static final String H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID =
      "avc-baseline-480-23";
  private static final String H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID =
      "avc-baseline-480-24";
  private static final String H264_BASELINE_480P_29FPS_VIDEO_REPRESENTATION_ID =
      "avc-baseline-480-29";

  private static final String H265_BASELINE_288P_VIDEO_REPRESENTATION_ID = "hevc-main-288";
  private static final String H265_BASELINE_360P_VIDEO_REPRESENTATION_ID = "hevc-main-360";
  // The highest quality H265 format mandated by the Android CDD.
  private static final String H265_CDD_FIXED = H265_BASELINE_360P_VIDEO_REPRESENTATION_ID;
  // Multiple H265 formats mandated by the Android CDD.
  private static final String[] H265_CDD_ADAPTIVE =
      new String[] {
          H265_BASELINE_288P_VIDEO_REPRESENTATION_ID,
          H265_BASELINE_360P_VIDEO_REPRESENTATION_ID};

  private static final String VORBIS_AUDIO_REPRESENTATION_ID = "4";
  private static final String VP9_180P_VIDEO_REPRESENTATION_ID = "0";
  private static final String VP9_360P_VIDEO_REPRESENTATION_ID = "1";
  // The highest quality VP9 format mandated by the Android CDD.
  private static final String VP9_CDD_FIXED = VP9_360P_VIDEO_REPRESENTATION_ID;
  // Multiple VP9 formats mandated by the Android CDD.
  private static final String[] VP9_CDD_ADAPTIVE =
      new String[] {
          VP9_180P_VIDEO_REPRESENTATION_ID,
          VP9_360P_VIDEO_REPRESENTATION_ID};

  // Widevine encrypted content representation ids.
  private static final String WIDEVINE_AAC_AUDIO_REPRESENTATION_ID = "0";
  private static final String WIDEVINE_H264_BASELINE_240P_VIDEO_REPRESENTATION_ID = "1";
  private static final String WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID = "2";
  private static final String WIDEVINE_H264_MAIN_240P_VIDEO_REPRESENTATION_ID = "3";
  private static final String WIDEVINE_H264_MAIN_480P_VIDEO_REPRESENTATION_ID = "4";
  // The highest quality H264 format mandated by the Android CDD.
  private static final String WIDEVINE_H264_CDD_FIXED = Util.SDK_INT < 23
      ? WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID
      : WIDEVINE_H264_MAIN_480P_VIDEO_REPRESENTATION_ID;
  // Multiple H264 formats mandated by the Android CDD. Note: The CDD actually mandated main profile
  // support from API level 23, but we opt to test only from 24 due to known issues on API level 23
  // when switching between baseline and main profiles on certain devices.
  private static final String[] WIDEVINE_H264_CDD_ADAPTIVE = Util.SDK_INT < 24
      ? new String[] {
      WIDEVINE_H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
      WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID}
      : new String[] {
      WIDEVINE_H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
      WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID,
      WIDEVINE_H264_MAIN_240P_VIDEO_REPRESENTATION_ID,
      WIDEVINE_H264_MAIN_480P_VIDEO_REPRESENTATION_ID};

  private static final String WIDEVINE_H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID = "2";
  private static final String WIDEVINE_H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID = "2";
  private static final String WIDEVINE_H264_BASELINE_480P_29FPS_VIDEO_REPRESENTATION_ID = "2";

  private static final String WIDEVINE_H265_BASELINE_288P_VIDEO_REPRESENTATION_ID = "1";
  private static final String WIDEVINE_H265_BASELINE_360P_VIDEO_REPRESENTATION_ID = "2";
  // The highest quality H265 format mandated by the Android CDD.
  private static final String WIDEVINE_H265_CDD_FIXED =
      WIDEVINE_H265_BASELINE_360P_VIDEO_REPRESENTATION_ID;
  // Multiple H265 formats mandated by the Android CDD.
  private static final String[] WIDEVINE_H265_CDD_ADAPTIVE =
      new String[] {
          WIDEVINE_H265_BASELINE_288P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H265_BASELINE_360P_VIDEO_REPRESENTATION_ID};

  private static final String WIDEVINE_VORBIS_AUDIO_REPRESENTATION_ID = "0";
  private static final String WIDEVINE_VP9_180P_VIDEO_REPRESENTATION_ID = "1";
  private static final String WIDEVINE_VP9_360P_VIDEO_REPRESENTATION_ID = "2";
  // The highest quality VP9 format mandated by the Android CDD.
  private static final String WIDEVINE_VP9_CDD_FIXED = VP9_360P_VIDEO_REPRESENTATION_ID;
  // Multiple VP9 formats mandated by the Android CDD.
  private static final String[] WIDEVINE_VP9_CDD_ADAPTIVE =
      new String[] {
          WIDEVINE_VP9_180P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_VP9_360P_VIDEO_REPRESENTATION_ID};

  private static final String WIDEVINE_LICENSE_URL =
      "https://proxy.uat.widevine.com/proxy?provider=widevine_test&video_id=";
  private static final String WIDEVINE_SW_CRYPTO_CONTENT_ID = "exoplayer_test_1";
  private static final String WIDEVINE_HW_SECURE_DECODE_CONTENT_ID = "exoplayer_test_2";
  private static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
  private static final String WIDEVINE_SECURITY_LEVEL_1 = "L1";
  private static final String WIDEVINE_SECURITY_LEVEL_3 = "L3";
  private static final String SECURITY_LEVEL_PROPERTY = "securityLevel";

  // Whether adaptive tests should enable video formats beyond those mandated by the Android CDD
  // if the device advertises support for them.
  private static final boolean ALLOW_ADDITIONAL_VIDEO_FORMATS = Util.SDK_INT >= 24;

  private static final ActionSchedule SEEKING_SCHEDULE = new ActionSchedule.Builder(TAG)
      .delay(10000).seek(15000)
      .delay(10000).seek(30000).seek(31000).seek(32000).seek(33000).seek(34000)
      .delay(1000).pause().delay(1000).play()
      .delay(1000).pause().seek(120000).delay(1000).play()
      .build();
  private static final ActionSchedule RENDERER_DISABLING_SCHEDULE = new ActionSchedule.Builder(TAG)
      // Wait 10 seconds, disable the video renderer, wait another 10 seconds and enable it again.
      .delay(10000).disableRenderer(VIDEO_RENDERER_INDEX)
      .delay(10000).enableRenderer(VIDEO_RENDERER_INDEX)
      // Ditto for the audio renderer.
      .delay(10000).disableRenderer(AUDIO_RENDERER_INDEX)
      .delay(10000).enableRenderer(AUDIO_RENDERER_INDEX)
      // Wait 10 seconds, then disable and enable the video renderer 5 times in quick succession.
      .delay(10000).disableRenderer(VIDEO_RENDERER_INDEX)
      .enableRenderer(VIDEO_RENDERER_INDEX)
      .disableRenderer(VIDEO_RENDERER_INDEX)
      .enableRenderer(VIDEO_RENDERER_INDEX)
      .disableRenderer(VIDEO_RENDERER_INDEX)
      .enableRenderer(VIDEO_RENDERER_INDEX)
      .disableRenderer(VIDEO_RENDERER_INDEX)
      .enableRenderer(VIDEO_RENDERER_INDEX)
      .disableRenderer(VIDEO_RENDERER_INDEX)
      .enableRenderer(VIDEO_RENDERER_INDEX)
      // Ditto for the audio renderer.
      .delay(10000).disableRenderer(AUDIO_RENDERER_INDEX)
      .enableRenderer(AUDIO_RENDERER_INDEX)
      .disableRenderer(AUDIO_RENDERER_INDEX)
      .enableRenderer(AUDIO_RENDERER_INDEX)
      .disableRenderer(AUDIO_RENDERER_INDEX)
      .enableRenderer(AUDIO_RENDERER_INDEX)
      .disableRenderer(AUDIO_RENDERER_INDEX)
      .enableRenderer(AUDIO_RENDERER_INDEX)
      .disableRenderer(AUDIO_RENDERER_INDEX)
      .enableRenderer(AUDIO_RENDERER_INDEX)
      .delay(10000).seek(120000)
      .build();

  public DashTest() {
    super(HostActivity.class);
  }

  // H264 CDD.

  public void testH264Fixed() {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    String streamName = "test_h264_fixed";
    testDashPlayback(getActivity(), streamName, H264_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false,
        MimeTypes.VIDEO_H264, false, H264_CDD_FIXED);
  }

  public void testH264Adaptive() throws DecoderQueryException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_h264_adaptive";
    testDashPlayback(getActivity(), streamName, H264_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false,
        MimeTypes.VIDEO_H264, ALLOW_ADDITIONAL_VIDEO_FORMATS, H264_CDD_ADAPTIVE);
  }

  public void testH264AdaptiveWithSeeking() throws DecoderQueryException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_h264_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false, H264_MANIFEST,
        AAC_AUDIO_REPRESENTATION_ID, false, MimeTypes.VIDEO_H264, ALLOW_ADDITIONAL_VIDEO_FORMATS,
        H264_CDD_ADAPTIVE);
  }

  public void testH264AdaptiveWithRendererDisabling() throws DecoderQueryException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_h264_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false, H264_MANIFEST,
        AAC_AUDIO_REPRESENTATION_ID, false, MimeTypes.VIDEO_H264, ALLOW_ADDITIONAL_VIDEO_FORMATS,
        H264_CDD_ADAPTIVE);
  }

  // H265 CDD.

  public void testH265Fixed() {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_h265_fixed";
    testDashPlayback(getActivity(), streamName, H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false,
        MimeTypes.VIDEO_H265, false, H265_CDD_FIXED);
  }

  public void testH265Adaptive() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_h265_adaptive";
    testDashPlayback(getActivity(), streamName, H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false,
        MimeTypes.VIDEO_H265, ALLOW_ADDITIONAL_VIDEO_FORMATS, H265_CDD_ADAPTIVE);
  }

  public void testH265AdaptiveWithSeeking() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_h265_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false, H265_MANIFEST,
        AAC_AUDIO_REPRESENTATION_ID, false, MimeTypes.VIDEO_H265, ALLOW_ADDITIONAL_VIDEO_FORMATS,
        H265_CDD_ADAPTIVE);
  }

  public void testH265AdaptiveWithRendererDisabling() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_h265_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false,
        H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false, MimeTypes.VIDEO_H265,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, H265_CDD_ADAPTIVE);
  }

  // VP9 (CDD).

  public void testVp9Fixed360p() {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_fixed_360p";
    testDashPlayback(getActivity(), streamName, VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID, false,
        MimeTypes.VIDEO_VP9, false, VP9_CDD_FIXED);
  }

  public void testVp9Adaptive() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_adaptive";
    testDashPlayback(getActivity(), streamName, VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID, false,
        MimeTypes.VIDEO_VP9, ALLOW_ADDITIONAL_VIDEO_FORMATS, VP9_CDD_ADAPTIVE);
  }

  public void testVp9AdaptiveWithSeeking() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false, VP9_MANIFEST,
        VORBIS_AUDIO_REPRESENTATION_ID, false, MimeTypes.VIDEO_VP9, ALLOW_ADDITIONAL_VIDEO_FORMATS,
        VP9_CDD_ADAPTIVE);
  }

  public void testVp9AdaptiveWithRendererDisabling() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false,
        VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID, false, MimeTypes.VIDEO_VP9,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, VP9_CDD_ADAPTIVE);
  }

  // H264: Other frame-rates for output buffer count assertions.

  // 23.976 fps.
  public void test23FpsH264Fixed() {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_23fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, H264_23_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        false, MimeTypes.VIDEO_H264, false, H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID);
  }

  // 24 fps.
  public void test24FpsH264Fixed() {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_24fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, H264_24_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        false, MimeTypes.VIDEO_H264, false, H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID);
  }

  // 29.97 fps.
  public void test29FpsH264Fixed() {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_29fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, H264_29_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        false, MimeTypes.VIDEO_H264, false, H264_BASELINE_480P_29FPS_VIDEO_REPRESENTATION_ID);
  }

  // Widevine encrypted media tests.
  // H264 CDD.

  public void testWidevineH264Fixed() throws DecoderQueryException {
    if (Util.SDK_INT < 18) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h264_fixed";
    testDashPlayback(getActivity(), streamName, WIDEVINE_H264_MANIFEST_PREFIX,
        WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true, MimeTypes.VIDEO_H264, false,
        WIDEVINE_H264_CDD_FIXED);
  }

  public void testWidevineH264Adaptive() throws DecoderQueryException {
    if (Util.SDK_INT < 18 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h264_adaptive";
    testDashPlayback(getActivity(), streamName, WIDEVINE_H264_MANIFEST_PREFIX,
        WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true, MimeTypes.VIDEO_H264,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, WIDEVINE_H264_CDD_ADAPTIVE);
  }

  public void testWidevineH264AdaptiveWithSeeking() throws DecoderQueryException {
    if (Util.SDK_INT < 18 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h264_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false,
        WIDEVINE_H264_MANIFEST_PREFIX, WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true,
        MimeTypes.VIDEO_H264, ALLOW_ADDITIONAL_VIDEO_FORMATS, WIDEVINE_H264_CDD_ADAPTIVE);
  }

  public void testWidevineH264AdaptiveWithRendererDisabling() throws DecoderQueryException {
    if (Util.SDK_INT < 18 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h264_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false,
        WIDEVINE_H264_MANIFEST_PREFIX, WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true,
        MimeTypes.VIDEO_H264, ALLOW_ADDITIONAL_VIDEO_FORMATS, WIDEVINE_H264_CDD_ADAPTIVE);
  }

  // H265 CDD.

  public void testWidevineH265Fixed() throws DecoderQueryException {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h265_fixed";
    testDashPlayback(getActivity(), streamName, WIDEVINE_H265_MANIFEST_PREFIX,
        WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true, MimeTypes.VIDEO_H265, false,
        WIDEVINE_H265_CDD_FIXED);
  }

  public void testWidevineH265Adaptive() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h265_adaptive";
    testDashPlayback(getActivity(), streamName, WIDEVINE_H265_MANIFEST_PREFIX,
        WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true, MimeTypes.VIDEO_H265,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, WIDEVINE_H265_CDD_ADAPTIVE);
  }

  public void testWidevineH265AdaptiveWithSeeking() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h265_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false,
        WIDEVINE_H265_MANIFEST_PREFIX, WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true,
        MimeTypes.VIDEO_H265, ALLOW_ADDITIONAL_VIDEO_FORMATS, WIDEVINE_H265_CDD_ADAPTIVE);
  }

  public void testWidevineH265AdaptiveWithRendererDisabling() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h265_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false,
        WIDEVINE_H265_MANIFEST_PREFIX, WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true,
        MimeTypes.VIDEO_H265, ALLOW_ADDITIONAL_VIDEO_FORMATS, WIDEVINE_H265_CDD_ADAPTIVE);
  }

  // VP9 (CDD).

  public void testWidevineVp9Fixed360p() throws DecoderQueryException {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_vp9_fixed_360p";
    testDashPlayback(getActivity(), streamName, WIDEVINE_VP9_MANIFEST_PREFIX,
        WIDEVINE_VORBIS_AUDIO_REPRESENTATION_ID, true, MimeTypes.VIDEO_VP9, false,
        WIDEVINE_VP9_CDD_FIXED);
  }

  public void testWidevineVp9Adaptive() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_vp9_adaptive";
    testDashPlayback(getActivity(), streamName, WIDEVINE_VP9_MANIFEST_PREFIX,
        WIDEVINE_VORBIS_AUDIO_REPRESENTATION_ID, true, MimeTypes.VIDEO_VP9,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, WIDEVINE_VP9_CDD_ADAPTIVE);
  }

  public void testWidevineVp9AdaptiveWithSeeking() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_vp9_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false,
        WIDEVINE_VP9_MANIFEST_PREFIX, WIDEVINE_VORBIS_AUDIO_REPRESENTATION_ID, true,
        MimeTypes.VIDEO_VP9, ALLOW_ADDITIONAL_VIDEO_FORMATS, WIDEVINE_VP9_CDD_ADAPTIVE);
  }

  public void testWidevineVp9AdaptiveWithRendererDisabling() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_vp9_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false,
        WIDEVINE_VP9_MANIFEST_PREFIX, WIDEVINE_VORBIS_AUDIO_REPRESENTATION_ID, true,
        MimeTypes.VIDEO_VP9, ALLOW_ADDITIONAL_VIDEO_FORMATS, WIDEVINE_VP9_CDD_ADAPTIVE);
  }

  // H264: Other frame-rates for output buffer count assertions.

  // 23.976 fps.
  public void testWidevine23FpsH264Fixed() throws DecoderQueryException {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_23fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, WIDEVINE_H264_23_MANIFEST_PREFIX,
        WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true, MimeTypes.VIDEO_H264, false,
        WIDEVINE_H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID);
  }

  // 24 fps.
  public void testWidevine24FpsH264Fixed() throws DecoderQueryException {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_24fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, WIDEVINE_H264_24_MANIFEST_PREFIX,
        WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true, MimeTypes.VIDEO_H264, false,
        WIDEVINE_H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID);
  }

  // 29.97 fps.
  public void testWidevine29FpsH264Fixed() throws DecoderQueryException {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_29fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, WIDEVINE_H264_29_MANIFEST_PREFIX,
        WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, true, MimeTypes.VIDEO_H264, false,
        WIDEVINE_H264_BASELINE_480P_29FPS_VIDEO_REPRESENTATION_ID);
  }

  // Offline license tests

  public void testWidevineOfflineLicense() throws Exception {
    if (Util.SDK_INT < 22) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h264_fixed_offline";
    DashHostedTestEncParameters parameters = newDashHostedTestEncParameters(
        WIDEVINE_H264_MANIFEST_PREFIX, true, MimeTypes.VIDEO_H264);
    TestOfflineLicenseHelper helper = new TestOfflineLicenseHelper(parameters);
    try {
      byte[] keySetId = helper.downloadLicense();
      testDashPlayback(getActivity(), streamName, null, true, parameters,
          WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, false, keySetId, WIDEVINE_H264_CDD_FIXED);
      helper.renewLicense();
    } finally {
      helper.releaseResources();
    }
  }

  public void testWidevineOfflineReleasedLicense() throws Throwable {
    if (Util.SDK_INT < 22) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h264_fixed_offline";
    DashHostedTestEncParameters parameters = newDashHostedTestEncParameters(
        WIDEVINE_H264_MANIFEST_PREFIX, true, MimeTypes.VIDEO_H264);
    TestOfflineLicenseHelper helper = new TestOfflineLicenseHelper(parameters);
    try {
      byte[] keySetId = helper.downloadLicense();
      helper.releaseLicense(); // keySetId no longer valid.
      try {
        testDashPlayback(getActivity(), streamName, null, true, parameters,
            WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, false, keySetId, WIDEVINE_H264_CDD_FIXED);
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
    } finally {
      helper.releaseResources();
    }
  }

  public void testWidevineOfflineExpiredLicense() throws Exception {
    if (Util.SDK_INT < 22) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h264_fixed_offline";
    DashHostedTestEncParameters parameters = newDashHostedTestEncParameters(
        WIDEVINE_H264_MANIFEST_PREFIX, true, MimeTypes.VIDEO_H264);
    TestOfflineLicenseHelper helper = new TestOfflineLicenseHelper(parameters);
    try {
      byte[] keySetId = helper.downloadLicense();

      // Wait until the license expires
      long licenseDuration = helper.getLicenseDurationRemainingSec().first;
      assertTrue("License duration should be less than 30 sec. "
          + "Server settings might have changed.", licenseDuration < 30);
      while (licenseDuration > 0) {
        synchronized (this) {
          wait(licenseDuration * 1000 + 2000);
        }
        long previousDuration = licenseDuration;
        licenseDuration = helper.getLicenseDurationRemainingSec().first;
        assertTrue("License duration should be decreasing.", previousDuration > licenseDuration);
      }

      // DefaultDrmSessionManager should renew the license and stream play fine
      testDashPlayback(getActivity(), streamName, null, true, parameters,
          WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, false, keySetId, WIDEVINE_H264_CDD_FIXED);
    } finally {
      helper.releaseResources();
    }
  }

  public void testWidevineOfflineLicenseExpiresOnPause() throws Exception {
    if (Util.SDK_INT < 22) {
      // Pass.
      return;
    }
    String streamName = "test_widevine_h264_fixed_offline";
    DashHostedTestEncParameters parameters = newDashHostedTestEncParameters(
        WIDEVINE_H264_MANIFEST_PREFIX, true, MimeTypes.VIDEO_H264);
    TestOfflineLicenseHelper helper = new TestOfflineLicenseHelper(parameters);
    try {
      byte[] keySetId = helper.downloadLicense();
      // During playback pause until the license expires then continue playback
      Pair<Long, Long> licenseDurationRemainingSec = helper.getLicenseDurationRemainingSec();
      long licenseDuration = licenseDurationRemainingSec.first;
      assertTrue("License duration should be less than 30 sec. "
          + "Server settings might have changed.", licenseDuration < 30);
      ActionSchedule schedule = new ActionSchedule.Builder(TAG)
          .delay(3000).pause().delay(licenseDuration * 1000 + 2000).play().build();
      // DefaultDrmSessionManager should renew the license and stream play fine
      testDashPlayback(getActivity(), streamName, schedule, true, parameters,
          WIDEVINE_AAC_AUDIO_REPRESENTATION_ID, false, keySetId, WIDEVINE_H264_CDD_FIXED);
    } finally {
      helper.releaseResources();
    }
  }

  // Internal.

  private void testDashPlayback(HostActivity activity, String streamName, String manifestFileName,
      String audioFormat, boolean isWidevineEncrypted, String videoMimeType,
      boolean canIncludeAdditionalVideoFormats, String... videoFormats) {
    testDashPlayback(activity, streamName, null, true, manifestFileName, audioFormat,
        isWidevineEncrypted, videoMimeType, canIncludeAdditionalVideoFormats, videoFormats);
  }

  private void testDashPlayback(HostActivity activity, String streamName,
      ActionSchedule actionSchedule, boolean fullPlaybackNoSeeking, String manifestFileName,
      String audioFormat, boolean isWidevineEncrypted, String videoMimeType,
      boolean canIncludeAdditionalVideoFormats, String... videoFormats) {
    testDashPlayback(activity, streamName, actionSchedule, fullPlaybackNoSeeking,
        newDashHostedTestEncParameters(manifestFileName, isWidevineEncrypted, videoMimeType),
        audioFormat, canIncludeAdditionalVideoFormats, null, videoFormats);
  }

  private void testDashPlayback(HostActivity activity, String streamName,
      ActionSchedule actionSchedule, boolean fullPlaybackNoSeeking,
      DashHostedTestEncParameters parameters, String audioFormat,
      boolean canIncludeAdditionalVideoFormats, byte[] offlineLicenseKeySetId,
      String... videoFormats) {
    MetricsLogger metricsLogger = MetricsLogger.Factory.createDefault(getInstrumentation(), TAG,
        REPORT_NAME, REPORT_OBJECT_NAME);
    DashHostedTest test = new DashHostedTest(streamName, metricsLogger, fullPlaybackNoSeeking,
        audioFormat, canIncludeAdditionalVideoFormats, false, actionSchedule, parameters,
        offlineLicenseKeySetId, videoFormats);
    activity.runTest(test, TEST_TIMEOUT_MS);
    // Retry test exactly once if adaptive test fails due to excessive dropped buffers when playing
    // non-CDD required formats (b/28220076).
    if (test.needsCddLimitedRetry) {
      metricsLogger = MetricsLogger.Factory.createDefault(getInstrumentation(), TAG, REPORT_NAME,
          REPORT_OBJECT_NAME);
      test = new DashHostedTest(streamName, metricsLogger, fullPlaybackNoSeeking, audioFormat,
          false, true, actionSchedule, parameters, offlineLicenseKeySetId, videoFormats);
      activity.runTest(test, TEST_TIMEOUT_MS);
    }
  }

  private static DashHostedTestEncParameters newDashHostedTestEncParameters(String manifestFileName,
      boolean isWidevineEncrypted, String videoMimeType) {
    String manifestPath = MANIFEST_URL_PREFIX + manifestFileName;
    return new DashHostedTestEncParameters(manifestPath, isWidevineEncrypted, videoMimeType);
  }

  private static boolean shouldSkipAdaptiveTest(String mimeType) throws DecoderQueryException {
    MediaCodecInfo decoderInfo = MediaCodecUtil.getDecoderInfo(mimeType, false);
    assertNotNull(decoderInfo);
    if (decoderInfo.adaptive) {
      return false;
    }
    assertTrue(Util.SDK_INT < 21);
    return true;
  }

  private static class DashHostedTestEncParameters {

    public final String manifestUrl;
    public final boolean useL1Widevine;
    public final String widevineLicenseUrl;
    public final boolean isWidevineEncrypted;

    public DashHostedTestEncParameters(String manifestUrl, boolean isWidevineEncrypted,
        String videoMimeType) {
      this.isWidevineEncrypted = isWidevineEncrypted;
      if (!isWidevineEncrypted) {
        this.manifestUrl = manifestUrl;
        this.useL1Widevine = true;
        this.widevineLicenseUrl = null;
      } else {
        this.useL1Widevine = isL1WidevineAvailable(videoMimeType);
        this.widevineLicenseUrl = WIDEVINE_LICENSE_URL + (useL1Widevine
            ? WIDEVINE_HW_SECURE_DECODE_CONTENT_ID : WIDEVINE_SW_CRYPTO_CONTENT_ID);
        this.manifestUrl =
            manifestUrl + (useL1Widevine ? WIDEVINE_L1_SUFFIX : WIDEVINE_L3_SUFFIX);
      }
    }

    @TargetApi(18)
    @SuppressWarnings("ResourceType")
    private static boolean isL1WidevineAvailable(String videoMimeType) {
      try {
        // Force L3 if secure decoder is not available.
        if (MediaCodecUtil.getDecoderInfo(videoMimeType, true) == null) {
          return false;
        }

        MediaDrm mediaDrm = new MediaDrm(WIDEVINE_UUID);
        String securityProperty = mediaDrm.getPropertyString(SECURITY_LEVEL_PROPERTY);
        mediaDrm.release();
        return WIDEVINE_SECURITY_LEVEL_1.equals(securityProperty);
      } catch (DecoderQueryException | UnsupportedSchemeException e) {
        throw new IllegalStateException(e);
      }
    }

  }

  private static class TestOfflineLicenseHelper {

    private final DashHostedTestEncParameters parameters;
    private final OfflineLicenseHelper<FrameworkMediaCrypto> offlineLicenseHelper;
    private final DefaultHttpDataSourceFactory httpDataSourceFactory;
    private byte[] offlineLicenseKeySetId;

    public TestOfflineLicenseHelper(DashHostedTestEncParameters parameters)
        throws UnsupportedDrmException {
      this.parameters = parameters;
      httpDataSourceFactory = new DefaultHttpDataSourceFactory("ExoPlayerPlaybackTests");
      offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(
          parameters.widevineLicenseUrl, httpDataSourceFactory);
    }

    public byte[] downloadLicense() throws InterruptedException, DrmSessionException, IOException {
      assertNull(offlineLicenseKeySetId);
      offlineLicenseKeySetId = offlineLicenseHelper
          .download(httpDataSourceFactory.createDataSource(), parameters.manifestUrl);
      assertNotNull(offlineLicenseKeySetId);
      assertTrue(offlineLicenseKeySetId.length > 0);
      return offlineLicenseKeySetId;
    }

    public void renewLicense() throws DrmSessionException {
      assertNotNull(offlineLicenseKeySetId);
      offlineLicenseKeySetId = offlineLicenseHelper.renew(offlineLicenseKeySetId);
      assertNotNull(offlineLicenseKeySetId);
    }

    public void releaseLicense() throws DrmSessionException {
      assertNotNull(offlineLicenseKeySetId);
      offlineLicenseHelper.release(offlineLicenseKeySetId);
      offlineLicenseKeySetId = null;
    }

    public Pair<Long, Long> getLicenseDurationRemainingSec() throws DrmSessionException {
      return offlineLicenseHelper.getLicenseDurationRemainingSec(offlineLicenseKeySetId);
    }

    public void releaseResources() throws DrmSessionException {
      if (offlineLicenseKeySetId != null) {
        releaseLicense();
      }
      if (offlineLicenseHelper != null) {
        offlineLicenseHelper.releaseResources();
      }
    }

  }

  @TargetApi(16)
  private static class DashHostedTest extends ExoHostedTest {

    private final String streamName;
    private final MetricsLogger metricsLogger;
    private final boolean fullPlaybackNoSeeking;
    private final boolean isCddLimitedRetry;
    private final DashTestTrackSelector trackSelector;
    private final DashHostedTestEncParameters parameters;
    private final byte[] offlineLicenseKeySetId;

    private boolean needsCddLimitedRetry;

    /**
     * @param streamName The name of the test stream for metric logging.
     * @param metricsLogger Logger to log metrics from the test.
     * @param fullPlaybackNoSeeking Whether the test will play the entire source with no seeking.
     * @param audioFormat The audio format.
     * @param canIncludeAdditionalVideoFormats Whether to use video formats in addition to those
     *     listed in the videoFormats argument, if the device is capable of playing them.
     * @param isCddLimitedRetry Whether this is a CDD limited retry following a previous failure.
     * @param actionSchedule The action schedule for the test.
     * @param parameters Encryption parameters.
     * @param offlineLicenseKeySetId The key set id of the license to be used.
     * @param videoFormats The video formats.
     */
    public DashHostedTest(String streamName, MetricsLogger metricsLogger,
        boolean fullPlaybackNoSeeking, String audioFormat,
        boolean canIncludeAdditionalVideoFormats, boolean isCddLimitedRetry,
        ActionSchedule actionSchedule, DashHostedTestEncParameters parameters,
        byte[] offlineLicenseKeySetId, String... videoFormats) {
      super(TAG, fullPlaybackNoSeeking);
      Assertions.checkArgument(!(isCddLimitedRetry && canIncludeAdditionalVideoFormats));
      this.streamName = streamName;
      this.metricsLogger = metricsLogger;
      this.fullPlaybackNoSeeking = fullPlaybackNoSeeking;
      this.isCddLimitedRetry = isCddLimitedRetry;
      this.parameters = parameters;
      this.offlineLicenseKeySetId = offlineLicenseKeySetId;
      trackSelector = new DashTestTrackSelector(audioFormat, videoFormats,
          canIncludeAdditionalVideoFormats);
      if (actionSchedule != null) {
        setSchedule(actionSchedule);
      }
    }

    @Override
    protected MappingTrackSelector buildTrackSelector(HostActivity host,
        BandwidthMeter bandwidthMeter) {
      return trackSelector;
    }

    @Override
    protected final DefaultDrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(
        final String userAgent) {
      DefaultDrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
      if (parameters.isWidevineEncrypted) {
        try {
          MediaDrmCallback drmCallback = new HttpMediaDrmCallback(parameters.widevineLicenseUrl,
              new DefaultHttpDataSourceFactory(userAgent));
          drmSessionManager = DefaultDrmSessionManager.newWidevineInstance(drmCallback, null,
              null, null);
          if (!parameters.useL1Widevine) {
            drmSessionManager.setPropertyString(SECURITY_LEVEL_PROPERTY, WIDEVINE_SECURITY_LEVEL_3);
          }
          if (offlineLicenseKeySetId != null) {
            drmSessionManager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK,
                offlineLicenseKeySetId);
          }
        } catch (UnsupportedDrmException e) {
          throw new IllegalStateException(e);
        }
      }
      return drmSessionManager;
    }

    @Override
    protected SimpleExoPlayer buildExoPlayer(HostActivity host, Surface surface,
        MappingTrackSelector trackSelector,
        DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
      SimpleExoPlayer player = new DebugSimpleExoPlayer(host, trackSelector,
          new DefaultLoadControl(), drmSessionManager);
      player.setVideoSurface(surface);
      return player;
    }

    @Override
    protected MediaSource buildSource(HostActivity host, String userAgent,
        TransferListener<? super DataSource> mediaTransferListener) {
      DataSource.Factory manifestDataSourceFactory = new DefaultDataSourceFactory(host, userAgent);
      DataSource.Factory mediaDataSourceFactory = new DefaultDataSourceFactory(host, userAgent,
          mediaTransferListener);
      Uri manifestUri = Uri.parse(parameters.manifestUrl);
      DefaultDashChunkSource.Factory chunkSourceFactory = new DefaultDashChunkSource.Factory(
          mediaDataSourceFactory);
      return new DashMediaSource(manifestUri, manifestDataSourceFactory, chunkSourceFactory,
          MIN_LOADABLE_RETRY_COUNT, 0 /* livePresentationDelayMs */, null, null);
    }

    @Override
    protected void logMetrics(DecoderCounters audioCounters, DecoderCounters videoCounters) {
      metricsLogger.logMetric(MetricsLogger.KEY_TEST_NAME, streamName);
      metricsLogger.logMetric(MetricsLogger.KEY_IS_CDD_LIMITED_RETRY, isCddLimitedRetry);
      metricsLogger.logMetric(MetricsLogger.KEY_FRAMES_DROPPED_COUNT,
          videoCounters.droppedOutputBufferCount);
      metricsLogger.logMetric(MetricsLogger.KEY_MAX_CONSECUTIVE_FRAMES_DROPPED_COUNT,
          videoCounters.maxConsecutiveDroppedOutputBufferCount);
      metricsLogger.logMetric(MetricsLogger.KEY_FRAMES_SKIPPED_COUNT,
          videoCounters.skippedOutputBufferCount);
      metricsLogger.logMetric(MetricsLogger.KEY_FRAMES_RENDERED_COUNT,
          videoCounters.renderedOutputBufferCount);
      metricsLogger.close();
    }

    @Override
    protected void assertPassed(DecoderCounters audioCounters, DecoderCounters videoCounters) {
      if (fullPlaybackNoSeeking) {
        // We shouldn't have skipped any output buffers.
        DecoderCountersUtil.assertSkippedOutputBufferCount(AUDIO_TAG, audioCounters, 0);
        DecoderCountersUtil.assertSkippedOutputBufferCount(VIDEO_TAG, videoCounters, 0);
        // We allow one fewer output buffer due to the way that MediaCodecRenderer and the
        // underlying decoders handle the end of stream. This should be tightened up in the future.
        DecoderCountersUtil.assertTotalOutputBufferCount(AUDIO_TAG, audioCounters,
            audioCounters.inputBufferCount - 1, audioCounters.inputBufferCount);
        DecoderCountersUtil.assertTotalOutputBufferCount(VIDEO_TAG, videoCounters,
            videoCounters.inputBufferCount - 1, videoCounters.inputBufferCount);
      }
      try {
        int droppedFrameLimit = (int) Math.ceil(MAX_DROPPED_VIDEO_FRAME_FRACTION
            * DecoderCountersUtil.getTotalOutputBuffers(videoCounters));
        // Assert that performance is acceptable.
        // Assert that total dropped frames were within limit.
        DecoderCountersUtil.assertDroppedOutputBufferLimit(VIDEO_TAG, videoCounters,
            droppedFrameLimit);
        // Assert that consecutive dropped frames were within limit.
        DecoderCountersUtil.assertConsecutiveDroppedOutputBufferLimit(VIDEO_TAG, videoCounters,
            MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES);
      } catch (AssertionFailedError e) {
        if (trackSelector.includedAdditionalVideoFormats) {
          // Retry limiting to CDD mandated formats (b/28220076).
          Log.e(TAG, "Too many dropped or consecutive dropped frames.", e);
          needsCddLimitedRetry = true;
        } else {
          throw e;
        }
      }
    }

  }

  private static final class DashTestTrackSelector extends MappingTrackSelector {

    private final String audioFormatId;
    private final String[] videoFormatIds;
    private final boolean canIncludeAdditionalVideoFormats;

    public boolean includedAdditionalVideoFormats;

    private DashTestTrackSelector(String audioFormatId, String[] videoFormatIds,
        boolean canIncludeAdditionalVideoFormats) {
      this.audioFormatId = audioFormatId;
      this.videoFormatIds = videoFormatIds;
      this.canIncludeAdditionalVideoFormats = canIncludeAdditionalVideoFormats;
    }

    @Override
    protected TrackSelection[] selectTracks(RendererCapabilities[] rendererCapabilities,
        TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports)
        throws ExoPlaybackException {
      Assertions.checkState(rendererCapabilities[VIDEO_RENDERER_INDEX].getTrackType()
          == C.TRACK_TYPE_VIDEO);
      Assertions.checkState(rendererCapabilities[AUDIO_RENDERER_INDEX].getTrackType()
          == C.TRACK_TYPE_AUDIO);
      Assertions.checkState(rendererTrackGroupArrays[VIDEO_RENDERER_INDEX].length == 1);
      Assertions.checkState(rendererTrackGroupArrays[AUDIO_RENDERER_INDEX].length == 1);
      TrackSelection[] selections = new TrackSelection[rendererCapabilities.length];
      selections[VIDEO_RENDERER_INDEX] = new RandomTrackSelection(
          rendererTrackGroupArrays[VIDEO_RENDERER_INDEX].get(0),
          getVideoTrackIndices(rendererTrackGroupArrays[VIDEO_RENDERER_INDEX].get(0),
              rendererFormatSupports[VIDEO_RENDERER_INDEX][0], videoFormatIds,
              canIncludeAdditionalVideoFormats),
          0 /* seed */);
      selections[AUDIO_RENDERER_INDEX] = new FixedTrackSelection(
          rendererTrackGroupArrays[AUDIO_RENDERER_INDEX].get(0),
          getTrackIndex(rendererTrackGroupArrays[AUDIO_RENDERER_INDEX].get(0), audioFormatId));
      includedAdditionalVideoFormats =
          selections[VIDEO_RENDERER_INDEX].length() > videoFormatIds.length;
      return selections;
    }

    private static int[] getVideoTrackIndices(TrackGroup trackGroup, int[] formatSupport,
        String[] formatIds, boolean canIncludeAdditionalFormats) {
      List<Integer> trackIndices = new ArrayList<>();

      // Always select explicitly listed representations.
      for (String formatId : formatIds) {
        int trackIndex = getTrackIndex(trackGroup, formatId);
        Log.d(TAG, "Adding base video format: "
            + Format.toLogString(trackGroup.getFormat(trackIndex)));
        trackIndices.add(trackIndex);
      }

      // Select additional video representations, if supported by the device.
      if (canIncludeAdditionalFormats) {
        for (int i = 0; i < trackGroup.length; i++) {
          if (!trackIndices.contains(i) && isFormatHandled(formatSupport[i])) {
            Log.d(TAG, "Adding extra video format: "
                + Format.toLogString(trackGroup.getFormat(i)));
            trackIndices.add(i);
          }
        }
      }

      int[] trackIndicesArray = Util.toArray(trackIndices);
      Arrays.sort(trackIndicesArray);
      return trackIndicesArray;
    }

    private static int getTrackIndex(TrackGroup trackGroup, String formatId) {
      for (int i = 0; i < trackGroup.length; i++) {
        if (trackGroup.getFormat(i).id.equals(formatId)) {
          return i;
        }
      }
      throw new IllegalStateException("Format " + formatId + " not found.");
    }

    private static boolean isFormatHandled(int formatSupport) {
      return (formatSupport & RendererCapabilities.FORMAT_SUPPORT_MASK)
          == RendererCapabilities.FORMAT_HANDLED;
    }

  }

}
