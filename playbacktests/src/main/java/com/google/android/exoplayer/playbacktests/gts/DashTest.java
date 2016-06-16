/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.playbacktests.gts;

import com.google.android.exoplayer.CodecCounters;
import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.VideoFormatSelectorUtil;
import com.google.android.exoplayer.dash.DashChunkSource;
import com.google.android.exoplayer.dash.DashTrackSelector;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescriptionParser;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.playbacktests.util.ActionSchedule;
import com.google.android.exoplayer.playbacktests.util.CodecCountersUtil;
import com.google.android.exoplayer.playbacktests.util.DebugMediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.playbacktests.util.ExoHostedTest;
import com.google.android.exoplayer.playbacktests.util.HostActivity;
import com.google.android.exoplayer.playbacktests.util.LogcatLogger;
import com.google.android.exoplayer.playbacktests.util.MetricsLogger;
import com.google.android.exoplayer.playbacktests.util.TestUtil;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.os.Handler;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.Surface;

import junit.framework.AssertionFailedError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests DASH playbacks using {@link ExoPlayer}.
 */
public final class DashTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "DashTest";
  private static final String REPORT_NAME = "GtsExoPlayerTestCases";

  private static final long MAX_PLAYING_TIME_DISCREPANCY_MS = 2000;
  private static final float MAX_DROPPED_VIDEO_FRAME_FRACTION = 0.01f;
  private static final int MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES = 10;

  private static final long MAX_ADDITIONAL_TIME_MS = 180000;
  private static final int MIN_LOADABLE_RETRY_COUNT = 10;

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
      .delay(10000).disableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
      .delay(10000).enableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
      // Ditto for the audio renderer.
      .delay(10000).disableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
      .delay(10000).enableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
      // Wait 10 seconds, then disable and enable the video renderer 5 times in quick succession.
      .delay(10000).disableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
          .disableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
          .disableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
          .disableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
          .disableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.VIDEO_RENDERER_INDEX)
      // Ditto for the audio renderer.
      .delay(10000).disableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
          .disableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
          .disableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
          .disableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
          .disableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
          .enableRenderer(DashHostedTest.AUDIO_RENDERER_INDEX)
      .delay(10000).seek(120000)
      .build();

  public DashTest() {
    super(HostActivity.class);
  }

  // H264 CDD.

  public void testH264Fixed() throws IOException {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    String streamName = "test_h264_fixed";
    testDashPlayback(getActivity(), streamName, H264_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false,
        H264_CDD_FIXED);
  }

  public void testH264Adaptive() throws IOException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_h264_adaptive";
    testDashPlayback(getActivity(), streamName, H264_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, H264_CDD_ADAPTIVE);
  }

  public void testH264AdaptiveWithSeeking() throws IOException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_h264_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false, H264_MANIFEST,
        AAC_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS, H264_CDD_ADAPTIVE);
  }

  public void testH264AdaptiveWithRendererDisabling() throws IOException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String streamName = "test_h264_adaptive_with_renderer_disabling";
    testDashPlayback(getActivity(), streamName, RENDERER_DISABLING_SCHEDULE, false, H264_MANIFEST,
        AAC_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS, H264_CDD_ADAPTIVE);
  }

  // H265 CDD.

  public void testH265Fixed() throws IOException {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_h265_fixed";
    testDashPlayback(getActivity(), streamName, H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false,
        H265_CDD_FIXED);
  }

  public void testH265Adaptive() throws IOException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_h265_adaptive";
    testDashPlayback(getActivity(), streamName, H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, H265_CDD_ADAPTIVE);
  }

  public void testH265AdaptiveWithSeeking() throws IOException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String streamName = "test_h265_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false, H265_MANIFEST,
        AAC_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS, H265_CDD_ADAPTIVE);
  }

  public void testH265AdaptiveWithRendererDisabling() throws IOException {
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

  public void testVp9Fixed360p() throws IOException {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_fixed_360p";
    testDashPlayback(getActivity(), streamName, VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID, false,
        VP9_CDD_FIXED);
  }

  public void testVp9Adaptive() throws IOException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_adaptive";
    testDashPlayback(getActivity(), streamName, VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, VP9_CDD_ADAPTIVE);
  }

  public void testVp9AdaptiveWithSeeking() throws IOException {
    if (Util.SDK_INT < 24 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String streamName = "test_vp9_adaptive_with_seeking";
    testDashPlayback(getActivity(), streamName, SEEKING_SCHEDULE, false, VP9_MANIFEST,
        VORBIS_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS, VP9_CDD_ADAPTIVE);
  }

  public void testVp9AdaptiveWithRendererDisabling() throws IOException {
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
  public void test23FpsH264Fixed() throws IOException {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_23fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, H264_23_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        false, H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID);
  }

  // 24 fps.
  public void test24FpsH264Fixed() throws IOException {
    if (Util.SDK_INT < 23) {
      // Pass.
      return;
    }
    String streamName = "test_24fps_h264_fixed";
    testDashPlayback(getActivity(), streamName, H264_24_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        false, H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID);
  }

  // 29.97 fps.
  public void test29FpsH264Fixed() throws IOException {
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
      String audioFormat, boolean canIncludeAdditionalVideoFormats, String... videoFormats)
      throws IOException {
    testDashPlayback(activity, streamName, null, true, manifestFileName, audioFormat,
        canIncludeAdditionalVideoFormats, videoFormats);
  }

  private void testDashPlayback(HostActivity activity, String streamName,
      ActionSchedule actionSchedule, boolean fullPlaybackNoSeeking, String manifestFileName,
      String audioFormat, boolean canIncludeAdditionalVideoFormats, String... videoFormats)
      throws IOException {
    MediaPresentationDescription mpd = TestUtil.loadManifest(activity, TAG,
        MANIFEST_URL_PREFIX + manifestFileName, new MediaPresentationDescriptionParser());
    MetricsLogger metricsLogger = MetricsLogger.Factory.createDefault(getInstrumentation(), TAG,
        REPORT_NAME, streamName);
    DashHostedTest test = new DashHostedTest(streamName, mpd, metricsLogger, fullPlaybackNoSeeking,
        audioFormat, canIncludeAdditionalVideoFormats, false, actionSchedule, videoFormats);
    activity.runTest(test, mpd.duration + MAX_ADDITIONAL_TIME_MS);
    // Retry test exactly once if adaptive test fails due to excessive dropped buffers when playing
    // non-CDD required formats (b/28220076).
    if (test.needsCddLimitedRetry) {
      metricsLogger = MetricsLogger.Factory.createDefault(getInstrumentation(), TAG, REPORT_NAME,
          streamName + "_cdd_limited_retry");
      test = new DashHostedTest(streamName, mpd, metricsLogger, fullPlaybackNoSeeking, audioFormat,
          false, true, actionSchedule, videoFormats);
      activity.runTest(test, mpd.duration + MAX_ADDITIONAL_TIME_MS);
    }
  }

  private boolean shouldSkipAdaptiveTest(String mimeType) throws IOException {
    if (!MediaCodecUtil.getDecoderInfo(mimeType, false).adaptive) {
      assertTrue(Util.SDK_INT < 21);
      return true;
    }
    return false;
  }

  @TargetApi(16)
  private static class DashHostedTest extends ExoHostedTest {

    private static final int RENDERER_COUNT = 2;
    private static final int VIDEO_RENDERER_INDEX = 0;
    private static final int AUDIO_RENDERER_INDEX = 1;

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int VIDEO_BUFFER_SEGMENTS = 200;
    private static final int AUDIO_BUFFER_SEGMENTS = 60;

    private static final String VIDEO_TAG = "Video";
    private static final String AUDIO_TAG = "Audio";
    private static final int VIDEO_EVENT_ID = 0;
    private static final int AUDIO_EVENT_ID = 1;

    private final String streamName;
    private final MediaPresentationDescription mpd;
    private final MetricsLogger metricsLogger;
    private final boolean fullPlaybackNoSeeking;
    private final boolean canIncludeAdditionalVideoFormats;
    private final String[] audioFormats;
    private final String[] videoFormats;

    private CodecCounters videoCounters;
    private CodecCounters audioCounters;
    private boolean needsCddLimitedRetry;
    private TrackSelector videoTrackSelector;

    /**
     * @param streamName The name of the test stream for metric logging.
     * @param mpd The manifest.
     * @param metricsLogger Logger to log metrics from the test.
     * @param fullPlaybackNoSeeking True if the test will play the entire source with no seeking.
     *     False otherwise.
     * @param audioFormat The audio format.
     * @param canIncludeAdditionalVideoFormats Whether to use video formats in addition to
     *     those listed in the videoFormats argument, if the device is capable of playing them.
     * @param isCddLimitedRetry Whether this is a CDD limited retry following a previous failure.
     * @param videoFormats The video formats.
     */
    public DashHostedTest(String streamName, MediaPresentationDescription mpd,
        MetricsLogger metricsLogger, boolean fullPlaybackNoSeeking, String audioFormat,
        boolean canIncludeAdditionalVideoFormats, boolean isCddLimitedRetry,
        ActionSchedule actionSchedule, String... videoFormats) {
      super(RENDERER_COUNT);
      Assertions.checkArgument(!(isCddLimitedRetry && canIncludeAdditionalVideoFormats));
      this.streamName = streamName;
      this.mpd = Assertions.checkNotNull(mpd);
      this.metricsLogger = metricsLogger;
      this.fullPlaybackNoSeeking = fullPlaybackNoSeeking;
      this.audioFormats = new String[] {audioFormat};
      this.canIncludeAdditionalVideoFormats = canIncludeAdditionalVideoFormats;
      this.videoFormats = videoFormats;
      if (actionSchedule != null) {
        setSchedule(actionSchedule);
      }
    }

    @Override
    public TrackRenderer[] buildRenderers(HostActivity host, ExoPlayer player, Surface surface) {
      Handler handler = new Handler();
      LogcatLogger logger = new LogcatLogger(TAG, player);
      LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
      String userAgent = TestUtil.getUserAgent(host);

      // Build the video renderer.
      DataSource videoDataSource = new DefaultUriDataSource(host, null, userAgent);
      videoTrackSelector = new TrackSelector(AdaptationSet.TYPE_VIDEO,
          canIncludeAdditionalVideoFormats, videoFormats);
      ChunkSource videoChunkSource = new DashChunkSource(mpd, videoTrackSelector, videoDataSource,
          new FormatEvaluator.RandomEvaluator(0));
      ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
          VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, handler, logger, VIDEO_EVENT_ID,
          MIN_LOADABLE_RETRY_COUNT);
      DebugMediaCodecVideoTrackRenderer videoRenderer = new DebugMediaCodecVideoTrackRenderer(host,
          videoSampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT,
          0, handler, logger, 50);
      videoCounters = videoRenderer.codecCounters;
      player.sendMessage(videoRenderer, DebugMediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);

      // Build the audio renderer.
      DataSource audioDataSource = new DefaultUriDataSource(host, null, userAgent);
      TrackSelector audioTrackSelector = new TrackSelector(AdaptationSet.TYPE_AUDIO, false,
          audioFormats);
      ChunkSource audioChunkSource = new DashChunkSource(mpd, audioTrackSelector, audioDataSource,
          null);
      ChunkSampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource, loadControl,
          AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, handler, logger, AUDIO_EVENT_ID,
          MIN_LOADABLE_RETRY_COUNT);
      MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
          audioSampleSource, MediaCodecSelector.DEFAULT, handler, logger);
      audioCounters = audioRenderer.codecCounters;

      TrackRenderer[] renderers = new TrackRenderer[RENDERER_COUNT];
      renderers[VIDEO_RENDERER_INDEX] = videoRenderer;
      renderers[AUDIO_RENDERER_INDEX] = audioRenderer;
      return renderers;
    }

    @Override
    protected void assertPassed() {
      if (fullPlaybackNoSeeking) {
        // Audio is not adaptive and we didn't seek (which can re-instantiate the audio decoder
        // in ExoPlayer), so the decoder output format should have changed exactly once. The output
        // buffers should have changed 0 or 1 times.
        CodecCountersUtil.assertOutputFormatChangedCount(AUDIO_TAG, audioCounters, 1);
        CodecCountersUtil.assertOutputBuffersChangedLimit(AUDIO_TAG, audioCounters, 1);

        if (videoFormats.length == 1) {
          // Video is not adaptive, so the decoder output format should have changed exactly once.
          // The output buffers should have changed 0 or 1 times.
          CodecCountersUtil.assertOutputFormatChangedCount(VIDEO_TAG, videoCounters, 1);
          CodecCountersUtil.assertOutputBuffersChangedLimit(VIDEO_TAG, videoCounters, 1);
        }

        // We shouldn't have skipped any output buffers.
        CodecCountersUtil.assertSkippedOutputBufferCount(AUDIO_TAG, audioCounters, 0);
        CodecCountersUtil.assertSkippedOutputBufferCount(VIDEO_TAG, videoCounters, 0);

        // We allow one fewer output buffer due to the way that MediaCodecTrackRenderer and the
        // underlying decoders handle the end of stream. This should be tightened up in the future.
        CodecCountersUtil.assertTotalOutputBufferCount(AUDIO_TAG, audioCounters,
            audioCounters.inputBufferCount - 1, audioCounters.inputBufferCount);
        CodecCountersUtil.assertTotalOutputBufferCount(VIDEO_TAG, videoCounters,
            videoCounters.inputBufferCount - 1, videoCounters.inputBufferCount);

        // The total playing time should match the source duration.
        long sourceDuration = mpd.duration;
        long minAllowedActualPlayingTime = sourceDuration - MAX_PLAYING_TIME_DISCREPANCY_MS;
        long maxAllowedActualPlayingTime = sourceDuration + MAX_PLAYING_TIME_DISCREPANCY_MS;
        long actualPlayingTime = getTotalPlayingTimeMs();
        assertTrue("Total playing time: " + actualPlayingTime + ". Actual media duration: "
            + sourceDuration, minAllowedActualPlayingTime <= actualPlayingTime
            && actualPlayingTime <= maxAllowedActualPlayingTime);
      }
      try {
        int droppedFrameLimit = (int) Math.ceil(MAX_DROPPED_VIDEO_FRAME_FRACTION
            * CodecCountersUtil.getTotalOutputBuffers(videoCounters));
        // Assert that performance is acceptable.
        // Assert that total dropped frames were within limit.
        CodecCountersUtil.assertDroppedOutputBufferLimit(VIDEO_TAG, videoCounters,
            droppedFrameLimit);
        // Assert that consecutive dropped frames were within limit.
        CodecCountersUtil.assertConsecutiveDroppedOutputBufferLimit(VIDEO_TAG, videoCounters,
            MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES);
      } catch (AssertionFailedError e) {
        if (videoTrackSelector.includedAdditionalVideoRepresentations) {
          // Retry limiting to CDD mandated formats (b/28220076).
          Log.e(TAG, "Too many dropped or consecutive dropped frames.", e);
          needsCddLimitedRetry = true;
        } else {
          throw e;
        }
      }
    }

    @Override
    protected void logMetrics() {
      // Log metrics from the test.
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

    private static final class TrackSelector implements DashTrackSelector {

      private final int adaptationSetType;
      private final String[] representationIds;
      private final boolean canIncludeAdditionalVideoRepresentations;

      public boolean includedAdditionalVideoRepresentations;

      private TrackSelector(int adaptationSetType, boolean canIncludeAdditionalVideoRepresentations,
          String[] representationIds) {
        Assertions.checkState(!canIncludeAdditionalVideoRepresentations
            || adaptationSetType == AdaptationSet.TYPE_VIDEO);
        this.adaptationSetType = adaptationSetType;
        this.canIncludeAdditionalVideoRepresentations = canIncludeAdditionalVideoRepresentations;
        this.representationIds = representationIds;
      }

      @Override
      public void selectTracks(MediaPresentationDescription manifest, int periodIndex,
          Output output) throws IOException {
        Period period = manifest.getPeriod(periodIndex);
        int adaptationSetIndex = period.getAdaptationSetIndex(adaptationSetType);
        AdaptationSet adaptationSet = period.adaptationSets.get(adaptationSetIndex);
        int[] representationIndices = getRepresentationIndices(adaptationSet, representationIds,
            canIncludeAdditionalVideoRepresentations);
        if (representationIndices.length > representationIds.length) {
          includedAdditionalVideoRepresentations = true;
        }
        if (adaptationSetType == AdaptationSet.TYPE_VIDEO) {
          output.adaptiveTrack(manifest, periodIndex, adaptationSetIndex, representationIndices);
        }
        for (int i = 0; i < representationIndices.length; i++) {
          output.fixedTrack(manifest, periodIndex, adaptationSetIndex, representationIndices[i]);
        }
      }

      private static int[] getRepresentationIndices(AdaptationSet adaptationSet,
          String[] representationIds, boolean canIncludeAdditionalVideoRepresentations)
          throws IOException {
        List<Representation> availableRepresentations = adaptationSet.representations;
        List<Integer> selectedRepresentationIndices = new ArrayList<>();

        // Always select explicitly listed representations, failing if they're missing.
        for (int i = 0; i < representationIds.length; i++) {
          String representationId = representationIds[i];
          boolean foundIndex = false;
          for (int j = 0; j < availableRepresentations.size() && !foundIndex; j++) {
            if (availableRepresentations.get(j).format.id.equals(representationId)) {
              selectedRepresentationIndices.add(j);
              foundIndex = true;
            }
          }
          if (!foundIndex) {
            throw new IllegalStateException("Representation " + representationId + " not found.");
          }
        }

        // Select additional video representations, if supported by the device.
        if (canIncludeAdditionalVideoRepresentations) {
           int[] supportedVideoRepresentationIndices = VideoFormatSelectorUtil.selectVideoFormats(
               availableRepresentations, null, false, true, -1, -1);
           for (int i = 0; i < supportedVideoRepresentationIndices.length; i++) {
             int representationIndex = supportedVideoRepresentationIndices[i];
             if (!selectedRepresentationIndices.contains(representationIndex)) {
               Log.d(TAG, "Adding video format: " + availableRepresentations.get(i).format.id);
               selectedRepresentationIndices.add(representationIndex);
             }
           }

        }

        return Util.toArray(selectedRepresentationIndices);
      }
    }
  }

}

