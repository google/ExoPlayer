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
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
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
import android.os.Bundle;
import android.os.Handler;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests DASH playbacks using {@link ExoPlayer}.
 */
public final class DashTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "DashTest";

  private static final long MAX_PLAYING_TIME_DISCREPANCY_MS = 2000;
  private static final float MAX_DROPPED_VIDEO_FRAME_FRACTION = 0.01f;
  private static final int MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES = 10;

  private static final long MAX_ADDITIONAL_TIME_MS = 180000;
  private static final int MIN_LOADABLE_RETRY_COUNT = 10;

  private static final String MANIFEST_URL_PREFIX = "https://storage.googleapis.com/exoplayer-test-"
      + "media-1/gen-2/screens/dash-vod-single-segment/";
  private static final String H264_MANIFEST = "manifest-h264.mpd";
  private static final String H265_MANIFEST = "manifest-h265.mpd";
  private static final String VP9_MANIFEST = "manifest-vp9.mpd";
  private static final int AAC_AUDIO_FRAME_COUNT = 5524;
  private static final int VIDEO_FRAME_COUNT = 3841;
  private static final int VORBIS_AUDIO_FRAME_COUNT = 7773;

  private static final String AAC_AUDIO_REPRESENTATION_ID = "141";
  private static final String H264_BASELINE_240P_VIDEO_REPRESENTATION_ID = "avc-baseline-240";
  private static final String H264_BASELINE_480P_VIDEO_REPRESENTATION_ID = "avc-baseline-480";
  private static final String H264_MAIN_240P_VIDEO_REPRESENTATION_ID = "avc-main-240";
  private static final String H264_MAIN_480P_VIDEO_REPRESENTATION_ID = "avc-main-480";
  // The highest quality H264 format mandated by the Android CDD.
  private static final String H264_CDD_FIXED = Util.SDK_INT < 23
      ? H264_BASELINE_480P_VIDEO_REPRESENTATION_ID : H264_MAIN_480P_VIDEO_REPRESENTATION_ID;
  // Multiple H264 formats mandated by the Android CDD.
  private static final String[] H264_CDD_ADAPTIVE = Util.SDK_INT < 23
      ? new String[] {
          H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          H264_BASELINE_480P_VIDEO_REPRESENTATION_ID}
      : new String[] {
          H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          H264_BASELINE_480P_VIDEO_REPRESENTATION_ID,
          H264_MAIN_240P_VIDEO_REPRESENTATION_ID,
          H264_MAIN_480P_VIDEO_REPRESENTATION_ID};

  private static final String H265_BASELINE_288P_VIDEO_REPRESENTATION_ID = "hevc-main-288";
  private static final String H265_BASELINE_360P_VIDEO_REPRESENTATION_ID = "hevc-main-360";
  // The highest quality H265 format mandated by the Android CDD.
  private static final String H265_CDD_FIXED = H265_BASELINE_360P_VIDEO_REPRESENTATION_ID;
  // Multiple H265 formats mandated by the Android CDD.
  private static final String[] H265_CDD_ADAPTIVE =
      new String[] {
          H265_BASELINE_288P_VIDEO_REPRESENTATION_ID,
          H265_BASELINE_360P_VIDEO_REPRESENTATION_ID};

  private static final String VORBIS_AUDIO_REPRESENTATION_ID = "2";
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
  private static final boolean ALLOW_ADDITIONAL_VIDEO_FORMATS = Util.SDK_INT >= 21;

  private static final ActionSchedule SEEKING_SCHEDULE = new ActionSchedule.Builder(TAG)
      .delay(10000).seek(15000)
      .delay(10000).seek(30000).seek(31000).seek(32000).seek(33000).seek(34000)
      .delay(1000).pause().delay(1000).play()
      .delay(1000).pause().seek(100000).delay(1000).play()
      .build();
  private static final ActionSchedule RENDERER_DISABLING_SCHEDULE = new ActionSchedule.Builder(TAG)
      // Wait 10 seconds, disable the video renderer, wait another 5 seconds and enable it again.
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
    String testName = "testH264Fixed";
    testDashPlayback(getActivity(), testName, AAC_AUDIO_FRAME_COUNT, VIDEO_FRAME_COUNT,
        H264_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false, H264_CDD_FIXED);
  }

  public void testH264Adaptive() throws IOException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String testName = "testH264Adaptive";
    testDashPlayback(getActivity(), testName, AAC_AUDIO_FRAME_COUNT, VIDEO_FRAME_COUNT,
        H264_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS,
        H264_CDD_ADAPTIVE);
  }

  public void testH264AdaptiveWithSeeking() throws IOException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String testName = "testH264AdaptiveWithSeeking";
    testDashPlayback(getActivity(), testName, SEEKING_SCHEDULE, false, AAC_AUDIO_FRAME_COUNT,
        VIDEO_FRAME_COUNT, H264_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, H264_CDD_ADAPTIVE);
  }

  public void testH264AdaptiveWithRendererDisabling() throws IOException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H264)) {
      // Pass.
      return;
    }
    String testName = "testH264AdaptiveWithRendererDisabling";
    testDashPlayback(getActivity(), testName, RENDERER_DISABLING_SCHEDULE, false,
        AAC_AUDIO_FRAME_COUNT, VIDEO_FRAME_COUNT, H264_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, H264_CDD_ADAPTIVE);
  }

  // H265 CDD.

  public void testH265Fixed() throws IOException {
    if (Util.SDK_INT < 21) {
      // Pass.
      return;
    }
    String testName = "testH265Fixed";
    testDashPlayback(getActivity(), testName, AAC_AUDIO_FRAME_COUNT, VIDEO_FRAME_COUNT,
        H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, false, H265_CDD_FIXED);
  }

  public void testH265Adaptive() throws IOException {
    if (Util.SDK_INT < 21 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String testName = "testH265Adaptive";
    testDashPlayback(getActivity(), testName, AAC_AUDIO_FRAME_COUNT, VIDEO_FRAME_COUNT,
        H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS,
        H265_CDD_ADAPTIVE);
  }

  public void testH265AdaptiveWithSeeking() throws IOException {
    if (Util.SDK_INT < 21 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String testName = "testH265AdaptiveWithSeeking";
    testDashPlayback(getActivity(), testName, SEEKING_SCHEDULE, false, AAC_AUDIO_FRAME_COUNT,
        VIDEO_FRAME_COUNT, H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, H265_CDD_ADAPTIVE);
  }

  public void testH265AdaptiveWithRendererDisabling() throws IOException {
    if (Util.SDK_INT < 21 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_H265)) {
      // Pass.
      return;
    }
    String testName = "testH265AdaptiveWithRendererDisabling";
    testDashPlayback(getActivity(), testName, RENDERER_DISABLING_SCHEDULE, false,
        AAC_AUDIO_FRAME_COUNT, VIDEO_FRAME_COUNT, H265_MANIFEST, AAC_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, H265_CDD_ADAPTIVE);
    }

  // VP9 (CDD).

  public void testVp9Fixed360p() throws IOException {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    String testName = "testVp9Fixed360p";
    testDashPlayback(getActivity(), testName, VORBIS_AUDIO_FRAME_COUNT, VIDEO_FRAME_COUNT,
        VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID, false, VP9_CDD_FIXED);
  }

  public void testVp9Adaptive() throws IOException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String testName = "testVp9Adaptive";
    testDashPlayback(getActivity(), testName, VORBIS_AUDIO_FRAME_COUNT, VIDEO_FRAME_COUNT,
        VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID, ALLOW_ADDITIONAL_VIDEO_FORMATS,
        VP9_CDD_ADAPTIVE);
  }

  public void testVp9AdaptiveWithSeeking() throws IOException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String testName = "testVp9AdaptiveWithSeeking";
    testDashPlayback(getActivity(), testName, SEEKING_SCHEDULE, false, VORBIS_AUDIO_FRAME_COUNT,
        VIDEO_FRAME_COUNT, VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, VP9_CDD_ADAPTIVE);
  }

  public void testVp9AdaptiveWithRendererDisabling() throws IOException {
    if (Util.SDK_INT < 16 || shouldSkipAdaptiveTest(MimeTypes.VIDEO_VP9)) {
      // Pass.
      return;
    }
    String testName = "testVp9AdaptiveWithRendererDisabling";
    testDashPlayback(getActivity(), testName, RENDERER_DISABLING_SCHEDULE, false,
        VORBIS_AUDIO_FRAME_COUNT, VIDEO_FRAME_COUNT, VP9_MANIFEST, VORBIS_AUDIO_REPRESENTATION_ID,
        ALLOW_ADDITIONAL_VIDEO_FORMATS, VP9_CDD_ADAPTIVE);
  }

  // Internal.

  private void testDashPlayback(HostActivity activity, String testName,
      int sourceAudioFrameCount, int sourceVideoFrameCount, String manifestFileName,
      String audioFormat, boolean includeAdditionalVideoFormats, String... videoFormats)
      throws IOException {
    testDashPlayback(activity, testName, null, true, sourceAudioFrameCount,
        sourceVideoFrameCount, manifestFileName, audioFormat, includeAdditionalVideoFormats,
        videoFormats);
  }

  private void testDashPlayback(HostActivity activity, String testName,
      ActionSchedule actionSchedule, boolean fullPlaybackNoSeeking, int sourceAudioFrameCount,
      int sourceVideoFrameCount, String manifestFileName, String audioFormat,
      boolean includeAdditionalVideoFormats, String... videoFormats) throws IOException {
    MediaPresentationDescription mpd = TestUtil.loadManifest(activity,
        MANIFEST_URL_PREFIX + manifestFileName, new MediaPresentationDescriptionParser());
    MetricsLogger metricsLogger = MetricsLogger.Factory.createDefault(getInstrumentation(), TAG);
    DashHostedTest test = new DashHostedTest(testName, mpd, metricsLogger, fullPlaybackNoSeeking,
        sourceAudioFrameCount, sourceVideoFrameCount, audioFormat, includeAdditionalVideoFormats,
        videoFormats);
    if (actionSchedule != null) {
      test.setSchedule(actionSchedule);
    }
    activity.runTest(test, mpd.duration + MAX_ADDITIONAL_TIME_MS);
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

    private final String testName;
    private final MediaPresentationDescription mpd;
    private final MetricsLogger metricsLogger;
    private final boolean fullPlaybackNoSeeking;
    private final int sourceAudioFrameCount;
    private final int sourceVideoFrameCount;
    private final boolean includeAdditionalVideoFormats;
    private final String[] audioFormats;
    private final String[] videoFormats;

    private CodecCounters videoCounters;
    private CodecCounters audioCounters;

    /**
     * @param testName The name of the test.
     * @param mpd The manifest.
     * @param metricsLogger Logger to log metrics from the test.
     * @param fullPlaybackNoSeeking True if the test will play the entire source with no seeking.
     *     False otherwise.
     * @param sourceAudioFrameCount The number of audio frames in the source.
     * @param sourceVideoFrameCount The number of video frames in the source.
     * @param audioFormat The audio format.
     * @param includeAdditionalVideoFormats Whether to use video formats in addition to
     *     those listed in the videoFormats argument, if the device is capable of playing them.
     * @param videoFormats The video formats.
     */
    public DashHostedTest(String testName, MediaPresentationDescription mpd,
        MetricsLogger metricsLogger, boolean fullPlaybackNoSeeking, int sourceAudioFrameCount,
        int sourceVideoFrameCount, String audioFormat, boolean includeAdditionalVideoFormats,
        String... videoFormats) {
      super(RENDERER_COUNT);
      this.testName = testName;
      this.mpd = Assertions.checkNotNull(mpd);
      this.metricsLogger = metricsLogger;
      this.fullPlaybackNoSeeking = fullPlaybackNoSeeking;
      this.sourceAudioFrameCount = sourceAudioFrameCount;
      this.sourceVideoFrameCount = sourceVideoFrameCount;
      this.audioFormats = new String[] {audioFormat};
      this.includeAdditionalVideoFormats = includeAdditionalVideoFormats;
      this.videoFormats = videoFormats;
    }

    @Override
    public TrackRenderer[] buildRenderers(HostActivity host, ExoPlayer player, Surface surface) {
      Handler handler = new Handler();
      LogcatLogger logger = new LogcatLogger(TAG, player);
      LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
      String userAgent = TestUtil.getUserAgent(host);

      // Build the video renderer.
      DataSource videoDataSource = new DefaultUriDataSource(host, null, userAgent);
      TrackSelector videoTrackSelector = new TrackSelector(AdaptationSet.TYPE_VIDEO,
          includeAdditionalVideoFormats, videoFormats);
      ChunkSource videoChunkSource = new DashChunkSource(mpd, videoTrackSelector, videoDataSource,
          new FormatEvaluator.RandomEvaluator(0));
      ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
          VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, handler, logger, VIDEO_EVENT_ID,
          MIN_LOADABLE_RETRY_COUNT);
      MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(host,
          videoSampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT,
          0, handler, logger, 50);
      videoCounters = videoRenderer.codecCounters;
      player.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);

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

        if (videoFormats != null && videoFormats.length == 1) {
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
            sourceAudioFrameCount - 1, sourceAudioFrameCount);
        CodecCountersUtil.assertTotalOutputBufferCount(VIDEO_TAG, videoCounters,
            sourceVideoFrameCount - 1, sourceVideoFrameCount);

        // The total playing time should match the source duration.
        long sourceDuration = mpd.duration;
        long minAllowedActualPlayingTime = sourceDuration - MAX_PLAYING_TIME_DISCREPANCY_MS;
        long maxAllowedActualPlayingTime = sourceDuration + MAX_PLAYING_TIME_DISCREPANCY_MS;
        long actualPlayingTime = getTotalPlayingTimeMs();
        assertTrue("Total playing time: " + actualPlayingTime + ". Actual media duration: "
            + sourceDuration, minAllowedActualPlayingTime <= actualPlayingTime
            && actualPlayingTime <= maxAllowedActualPlayingTime);
      }

      // Assert that the level of performance was acceptable.
      // Assert that total dropped frames were within limit.
      int droppedFrameLimit = (int) Math.ceil(MAX_DROPPED_VIDEO_FRAME_FRACTION
          * CodecCountersUtil.getTotalOutputBuffers(videoCounters));
      CodecCountersUtil.assertDroppedOutputBufferLimit(VIDEO_TAG, videoCounters, droppedFrameLimit);
      // Assert that consecutive dropped frames were within limit.
      CodecCountersUtil.assertConsecutiveDroppedOutputBufferLimit(VIDEO_TAG, videoCounters,
          MAX_CONSECUTIVE_DROPPED_VIDEO_FRAMES);
    }

    @Override
    protected void logMetrics() {
      // Create Bundle of metrics from the test.
      Bundle metrics = new Bundle();
      metrics.putString(MetricsLogger.KEY_TEST_NAME, testName);
      metrics.putInt(MetricsLogger.KEY_FRAMES_DROPPED_COUNT,
          videoCounters.droppedOutputBufferCount);
      metrics.putInt(MetricsLogger.KEY_MAX_CONSECUTIVE_FRAMES_DROPPED_COUNT,
          videoCounters.maxConsecutiveDroppedOutputBufferCount);
      metrics.putInt(MetricsLogger.KEY_FRAMES_SKIPPED_COUNT,
          videoCounters.skippedOutputBufferCount);
      metrics.putInt(MetricsLogger.KEY_FRAMES_RENDERED_COUNT,
          videoCounters.renderedOutputBufferCount);

      // Send metrics for logging.
      metricsLogger.logMetrics(metrics);
    }

    private static final class TrackSelector implements DashTrackSelector {

      private final int adaptationSetType;
      private final String[] representationIds;
      private final boolean includeAdditionalVideoRepresentations;

      private TrackSelector(int adaptationSetType, boolean includeAdditionalVideoRepresentations,
          String[] representationIds) {
        Assertions.checkState(!includeAdditionalVideoRepresentations
            || adaptationSetType == AdaptationSet.TYPE_VIDEO);
        this.adaptationSetType = adaptationSetType;
        this.includeAdditionalVideoRepresentations = includeAdditionalVideoRepresentations;
        this.representationIds = representationIds;
      }

      @Override
      public void selectTracks(MediaPresentationDescription manifest, int periodIndex,
          Output output) throws IOException {
        Period period = manifest.getPeriod(periodIndex);
        int adaptationSetIndex = period.getAdaptationSetIndex(adaptationSetType);
        AdaptationSet adaptationSet = period.adaptationSets.get(adaptationSetIndex);
        int[] representationIndices = getRepresentationIndices(adaptationSet, representationIds,
            includeAdditionalVideoRepresentations);
        if (adaptationSetType == AdaptationSet.TYPE_VIDEO) {
          output.adaptiveTrack(manifest, periodIndex, adaptationSetIndex, representationIndices);
        }
        for (int i = 0; i < representationIndices.length; i++) {
          output.fixedTrack(manifest, periodIndex, adaptationSetIndex, representationIndices[i]);
        }
      }

      private static int[] getRepresentationIndices(AdaptationSet adaptationSet,
          String[] representationIds, boolean includeAdditionalVideoRepresentations)
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
        if (includeAdditionalVideoRepresentations) {
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

