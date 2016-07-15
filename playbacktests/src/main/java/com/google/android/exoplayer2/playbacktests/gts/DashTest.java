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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.playbacktests.util.ActionSchedule;
import com.google.android.exoplayer2.playbacktests.util.DecoderCountersUtil;
import com.google.android.exoplayer2.playbacktests.util.ExoHostedTest;
import com.google.android.exoplayer2.playbacktests.util.HostActivity;
import com.google.android.exoplayer2.playbacktests.util.MetricsLogger;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSourceFactory;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import android.annotation.TargetApi;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import junit.framework.AssertionFailedError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests DASH playbacks using {@link ExoPlayer}.
 */
public final class DashTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "DashTest";
  private static final String VIDEO_TAG = TAG + ":Video";
  private static final String AUDIO_TAG = TAG + ":Audio";
  private static final String REPORT_NAME = "GtsExoPlayerTestCases";
  private static final int VIDEO_RENDERER_INDEX = 0;
  private static final int AUDIO_RENDERER_INDEX = 1;

  private static final long TEST_TIMEOUT_MS = 5 * 60 * 1000;
  private static final int MIN_LOADABLE_RETRY_COUNT = 10;
  private static final int MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES = 10;
  private static final float MAX_DROPPED_VIDEO_FRAME_FRACTION = 0.01f;

  private static final String MANIFEST_URL_PREFIX = "https://storage.googleapis.com/exoplayer-test-"
      + "media-1/gen-3/screens/dash-vod-single-segment/";
  private static final String H264_MANIFEST = "manifest-h264.mpd";
  private static final String H265_MANIFEST = "manifest-h265.mpd";
  private static final String VP9_MANIFEST = "manifest-vp9.mpd";
  private static final String H264_23_MANIFEST = "manifest-h264-23.mpd";
  private static final String H264_24_MANIFEST = "manifest-h264-24.mpd";
  private static final String H264_29_MANIFEST = "manifest-h264-29.mpd";

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
        H264_CDD_FIXED);
  }

  public void testH264Adaptive() throws DecoderQueryException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_h264_adaptive";
    testDashPlayback(getActivity(), streamName, H264_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, H264_CDD_ADAPTIVE);
  }

  public void testH264AdaptiveWithSeeking() throws DecoderQueryException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_h264_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false, H264_MANIFEST,
        AAC_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS, H264_CDD_ADAPTIVE);
  }

  public void testH264AdaptiveWithRendererDisabling() throws DecoderQueryException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_h264_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false, H264_MANIFEST,
        AAC_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS, H264_CDD_ADAPTIVE);
  }

  // H265 CDD.

  public void testH265Fixed() {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_h265_fixed";
    testDashPlayback(getActivity(), streamName, H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false,
        H265_CDD_FIXED);
  }

  public void testH265Adaptive() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_h265_adaptive";
    testDashPlayback(getActivity(), streamName, H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, H265_CDD_ADAPTIVE);
  }

  public void testH265AdaptiveWithSeeking() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_h265_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false, H265_MANIFEST,
        AAC_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS, H265_CDD_ADAPTIVE);
  }

  public void testH265AdaptiveWithRendererDisabling() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_h265_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false,
        H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS,
        H265_CDD_ADAPTIVE);
  }

  // VP9 (CDD).

  public void testVp9Fixed360p() {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_fixed_360p";
    testDashPlayback(getActivity(), streamName, VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID, false,
        VP9_CDD_FIXED);
  }

  public void testVp9Adaptive() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_adaptive";
    testDashPlayback(getActivity(), streamName, VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, VP9_CDD_ADAPTIVE);
  }

  public void testVp9AdaptiveWithSeeking() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false, VP9_MANIFEST,
        VORBIS_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS, VP9_CDD_ADAPTIVE);
  }

  public void testVp9AdaptiveWithRendererDisabling() throws DecoderQueryException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false,
        VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS,
        VP9_CDD_ADAPTIVE);
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
        false, H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID);
  }

  // 24 fps.
  public void test24FpsH264Fixed() {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_24fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, H264_24_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        false, H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID);
  }

  // 29.97 fps.
  public void test29FpsH264Fixed() {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_29fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, H264_29_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        false, H264_BASELINE_480P_29FPS_VIDEO_REPRESENTATION_ID);
  }

  // Internal.

  private void testDashPlayback(HostActivity activity, String streamName, String manifestFileName,
      String audioFormat, boolean canIncludeAdditionalVideoFormats, String... videoFormats) {
    testDashPlayback(activity, streamName, null, true, manifestFileName, audioFormat,
        canIncludeAdditionalVideoFormats, videoFormats);
  }

  private void testDashPlayback(HostActivity activity, String streamName,
      ActionSchedule actionSchedule, boolean fullPlaybackNoSeeking, String manifestFileName,
      String audioFormat, boolean canIncludeAdditionalVideoFormats, String... videoFormats) {
    Uri manifestUri = Uri.parse(MANIFEST_URL_PREFIX + manifestFileName);
    MetricsLogger metricsLogger = MetricsLogger.Factory.createDefault(getInstrumentation(), TAG,
        REPORT_NAME, streamName);
    DashHostedTest test = new DashHostedTest(streamName, manifestUri, metricsLogger,
        fullPlaybackNoSeeking, audioFormat, canIncludeAdditionalVideoFormats, false, actionSchedule,
        videoFormats);
    activity.runTest(test, TEST_TIMEOUT_MS);
    // Retry test exactly once if adaptive test fails due to excessive dropped buffers when playing
    // non-CDD required formats (b/28220076).
    if (test.needsCddLimitedRetry) {
      metricsLogger = MetricsLogger.Factory.createDefault(getInstrumentation(), TAG, REPORT_NAME,
          streamName + "_cdd_limited_retry");
      test = new DashHostedTest(streamName, manifestUri, metricsLogger, fullPlaybackNoSeeking,
          audioFormat, false, true, actionSchedule, videoFormats);
      activity.runTest(test, TEST_TIMEOUT_MS);
    }
  }

  private boolean shouldSkipAdaptiveTest(String mimeType) throws DecoderQueryException {
    MediaCodecInfo decoderInfo = MediaCodecUtil.getDecoderInfo(mimeType, false);
    assertNotNull(decoderInfo);
    if (decoderInfo.adaptive) {
      return false;
    }
    assertTrue(Util.SDK_INT < 21);
    return true;
  }

  @TargetApi(16)
  private static class DashHostedTest extends ExoHostedTest {

    private final String streamName;
    private final Uri manifestUri;
    private final MetricsLogger metricsLogger;
    private final boolean fullPlaybackNoSeeking;
    private final DashTestTrackSelector trackSelector;

    private boolean needsCddLimitedRetry;

    /**
     * @param streamName The name of the test stream for metric logging.
     * @param manifestUri The manifest uri.
     * @param metricsLogger Logger to log metrics from the test.
     * @param fullPlaybackNoSeeking True if the test will play the entire source with no seeking.
     *     False otherwise.
     * @param audioFormat The audio format.
     * @param canIncludeAdditionalVideoFormats Whether to use video formats in addition to those
     *     listed in the videoFormats argument, if the device is capable of playing them.
     * @param isCddLimitedRetry Whether this is a CDD limited retry following a previous failure.
     * @param videoFormats The video formats.
     */
    public DashHostedTest(String streamName, Uri manifestUri, MetricsLogger metricsLogger,
        boolean fullPlaybackNoSeeking, String audioFormat, boolean canIncludeAdditionalVideoFormats,
        boolean isCddLimitedRetry, ActionSchedule actionSchedule, String... videoFormats) {
      super(TAG, fullPlaybackNoSeeking);
      Assertions.checkArgument(!(isCddLimitedRetry && canIncludeAdditionalVideoFormats));
      this.streamName = streamName;
      this.manifestUri = manifestUri;
      this.metricsLogger = metricsLogger;
      this.fullPlaybackNoSeeking = fullPlaybackNoSeeking;
      trackSelector = new DashTestTrackSelector(new String[] {audioFormat},
          videoFormats, canIncludeAdditionalVideoFormats);
      if (actionSchedule != null) {
        setSchedule(actionSchedule);
      }
    }

    @Override
    protected MappingTrackSelector buildTrackSelector(HostActivity host) {
      return trackSelector;
    }

    @Override
    public MediaSource buildSource(HostActivity host, DataSourceFactory dataSourceFactory,
        BandwidthMeter bandwidthMeter) {
      return new DashMediaSource(manifestUri, dataSourceFactory, bandwidthMeter,
          MIN_LOADABLE_RETRY_COUNT, null, null);
    }

    @Override
    protected void logMetrics(DecoderCounters audioCounters, DecoderCounters videoCounters) {
      metricsLogger.logMetric(MetricsLogger.KEY_TEST_NAME, streamName);
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

    private final String[] audioFormatIds;
    private final String[] videoFormatIds;
    private final boolean canIncludeAdditionalVideoFormats;

    public boolean includedAdditionalVideoFormats;

    private DashTestTrackSelector(String[] audioFormatIds, String[] videoFormatIds,
        boolean canIncludeAdditionalVideoFormats) {
      super(null);
      this.audioFormatIds = audioFormatIds;
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
      selections[VIDEO_RENDERER_INDEX] = new TrackSelection(0,
          getTrackIndices(rendererTrackGroupArrays[VIDEO_RENDERER_INDEX].get(0),
              rendererFormatSupports[VIDEO_RENDERER_INDEX][0], videoFormatIds,
              canIncludeAdditionalVideoFormats));
      selections[AUDIO_RENDERER_INDEX] = new TrackSelection(0,
          getTrackIndices(rendererTrackGroupArrays[AUDIO_RENDERER_INDEX].get(0),
              rendererFormatSupports[AUDIO_RENDERER_INDEX][0], audioFormatIds, false));
      includedAdditionalVideoFormats =
          selections[VIDEO_RENDERER_INDEX].length > videoFormatIds.length;
      return selections;
    }

    private static int[] getTrackIndices(TrackGroup trackGroup, int[] formatSupport,
        String[] formatIds, boolean canIncludeAdditionalFormats) {
      List<Integer> trackIndices = new ArrayList<>();

      // Always select explicitly listed representations, failing if they're missing.
      for (String formatId : formatIds) {
        boolean foundIndex = false;
        for (int j = 0; j < trackGroup.length && !foundIndex; j++) {
          if (trackGroup.getFormat(j).id.equals(formatId)) {
            trackIndices.add(j);
            foundIndex = true;
          }
        }
        if (!foundIndex) {
          throw new IllegalStateException("Format " + formatId + " not found.");
        }
      }

      // Select additional video representations, if supported by the device.
      if (canIncludeAdditionalFormats) {
        for (int i = 0; i < trackGroup.length; i++) {
          if (!trackIndices.contains(i) && (formatSupport[i] & Renderer.FORMAT_SUPPORT_MASK)
              == Renderer.FORMAT_HANDLED) {
            Log.d(TAG, "Adding video format: " + trackGroup.getFormat(i).id);
            trackIndices.add(i);
          }
        }
      }

      int[] trackIndicesArray = Util.toArray(trackIndices);
      Arrays.sort(trackIndicesArray);
      return trackIndicesArray;
    }

  }

}
