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
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.FormatEvaluator;
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
import com.google.android.exoplayer.playbacktests.util.TestUtil;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.os.Handler;
import android.test.ActivityInstrumentationTestCase2;
import android.view.Surface;

import java.io.IOException;
import java.util.List;

/**
 * Tests H264 DASH playbacks using {@link ExoPlayer}.
 */
public final class H264DashTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "H264DashTest";

  private static final long MAX_PLAYING_TIME_DISCREPANCY_MS = 2000;
  private static final float MAX_DROPPED_VIDEO_FRAME_FRACTION = 0.01f;

  private static final long MAX_ADDITIONAL_TIME_MS = 180000;
  private static final int MIN_LOADABLE_RETRY_COUNT = 10;

  private static final String SOURCE_URL = "https://storage.googleapis.com/exoplayer-test-media-1"
      + "/gen/screens/dash-vod-single-segment/manifest-baseline.mpd";
  private static final int SOURCE_VIDEO_FRAME_COUNT = 3840;
  private static final int SOURCE_AUDIO_FRAME_COUNT = 5524;
  private static final String AUDIO_REPRESENTATION_ID = "141";
  private static final String VIDEO_REPRESENTATION_ID_240 = "avc-baseline-240";
  private static final String VIDEO_REPRESENTATION_ID_480 = "avc-baseline-480";

  public H264DashTest() {
    super(HostActivity.class);
  }

  public void testBaseline480() throws IOException {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    MediaPresentationDescription mpd = TestUtil.loadManifest(getActivity(), SOURCE_URL,
        new MediaPresentationDescriptionParser());
    H264DashHostedTest test = new H264DashHostedTest(mpd, true, AUDIO_REPRESENTATION_ID,
        VIDEO_REPRESENTATION_ID_480);
    getActivity().runTest(test, mpd.duration + MAX_ADDITIONAL_TIME_MS);
  }

  public void testBaselineAdaptive() throws IOException {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    MediaPresentationDescription mpd = TestUtil.loadManifest(getActivity(), SOURCE_URL,
        new MediaPresentationDescriptionParser());
    H264DashHostedTest test = new H264DashHostedTest(mpd, true, AUDIO_REPRESENTATION_ID,
        VIDEO_REPRESENTATION_ID_240, VIDEO_REPRESENTATION_ID_480);
    getActivity().runTest(test, mpd.duration + MAX_ADDITIONAL_TIME_MS);
  }

  public void testBaselineAdaptiveWithSeeking() throws IOException {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    MediaPresentationDescription mpd = TestUtil.loadManifest(getActivity(), SOURCE_URL,
        new MediaPresentationDescriptionParser());
    H264DashHostedTest test = new H264DashHostedTest(mpd, false, AUDIO_REPRESENTATION_ID,
        VIDEO_REPRESENTATION_ID_240, VIDEO_REPRESENTATION_ID_480);
    test.setSchedule(new ActionSchedule.Builder(TAG)
        .delay(10000).seek(15000)
        .delay(10000).seek(30000).seek(31000).seek(32000).seek(33000).seek(34000)
        .delay(1000).pause().delay(1000).play()
        .delay(1000).pause().seek(100000).delay(1000).play()
        .build());
    getActivity().runTest(test, mpd.duration + MAX_ADDITIONAL_TIME_MS);
  }

  public void testBaselineAdaptiveWithRendererDisabling() throws IOException {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    MediaPresentationDescription mpd = TestUtil.loadManifest(getActivity(), SOURCE_URL,
        new MediaPresentationDescriptionParser());
    H264DashHostedTest test = new H264DashHostedTest(mpd, false, AUDIO_REPRESENTATION_ID,
        VIDEO_REPRESENTATION_ID_240, VIDEO_REPRESENTATION_ID_480);
    test.setSchedule(new ActionSchedule.Builder(TAG)
        // Wait 10 seconds, disable the video renderer, wait another 5 seconds and enable it again.
        .delay(10000).disableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
        .delay(10000).enableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
        // Ditto for the audio renderer.
        .delay(10000).disableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
        .delay(10000).enableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
        // Wait 10 seconds, then disable and enable the video renderer 5 times in quick succession.
        .delay(10000).disableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
            .disableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
            .disableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
            .disableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
            .disableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.VIDEO_RENDERER_INDEX)
        // Ditto for the audio renderer.
        .delay(10000).disableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
            .disableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
            .disableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
            .disableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
            .disableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
            .enableRenderer(H264DashHostedTest.AUDIO_RENDERER_INDEX)
        .build());
    getActivity().runTest(test, mpd.duration + MAX_ADDITIONAL_TIME_MS);
  }

  @TargetApi(16)
  private static class H264DashHostedTest extends ExoHostedTest {

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

    private final MediaPresentationDescription mpd;
    private final boolean fullPlaybackNoSeeking;
    private String[] audioFormats;
    private String[] videoFormats;

    private CodecCounters videoCounters;
    private CodecCounters audioCounters;

    /**
     * @param mpd The manifest.
     * @param fullPlaybackNoSeeking True if the test will play the entire source with no seeking.
     *     False otherwise.
     * @param audioFormat The audio format.
     * @param videoFormats The video formats.
     */
    public H264DashHostedTest(MediaPresentationDescription mpd, boolean fullPlaybackNoSeeking,
        String audioFormat, String... videoFormats) {
      super(RENDERER_COUNT);
      this.mpd = Assertions.checkNotNull(mpd);
      this.fullPlaybackNoSeeking = fullPlaybackNoSeeking;
      this.audioFormats = new String[] {audioFormat};
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
      TrackSelector videoTrackSelector = new TrackSelector(AdaptationSet.TYPE_VIDEO, videoFormats);
      ChunkSource videoChunkSource = new DashChunkSource(mpd, videoTrackSelector, videoDataSource,
          new FormatEvaluator.RandomEvaluator(0));
      ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
          VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, handler, logger, VIDEO_EVENT_ID,
          MIN_LOADABLE_RETRY_COUNT);
      MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(host,
          videoSampleSource, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 0, handler, logger, 50);
      videoCounters = videoRenderer.codecCounters;
      player.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);

      // Build the audio renderer.
      DataSource audioDataSource = new DefaultUriDataSource(host, null, userAgent);
      TrackSelector audioTrackSelector = new TrackSelector(AdaptationSet.TYPE_AUDIO, audioFormats);
      ChunkSource audioChunkSource = new DashChunkSource(mpd, audioTrackSelector, audioDataSource,
          null);
      ChunkSampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource, loadControl,
          AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, handler, logger, AUDIO_EVENT_ID,
          MIN_LOADABLE_RETRY_COUNT);
      MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
          audioSampleSource, handler, logger);
      audioCounters = audioRenderer.codecCounters;

      TrackRenderer[] renderers = new TrackRenderer[RENDERER_COUNT];
      renderers[VIDEO_RENDERER_INDEX] = videoRenderer;
      renderers[AUDIO_RENDERER_INDEX] = audioRenderer;
      return renderers;
    }

    @Override
    protected void assertPassedInternal() {
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
        CodecCountersUtil.assertTotalOutputBufferCount(VIDEO_TAG, videoCounters,
            SOURCE_VIDEO_FRAME_COUNT - 1, SOURCE_VIDEO_FRAME_COUNT);
        CodecCountersUtil.assertTotalOutputBufferCount(AUDIO_TAG, audioCounters,
            SOURCE_AUDIO_FRAME_COUNT - 1, SOURCE_AUDIO_FRAME_COUNT);

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
      int droppedFrameLimit = (int) Math.ceil(MAX_DROPPED_VIDEO_FRAME_FRACTION
          * CodecCountersUtil.getTotalOutputBuffers(videoCounters));
      CodecCountersUtil.assertDroppedOutputBufferLimit(VIDEO_TAG, videoCounters, droppedFrameLimit);
    }

    private static final class TrackSelector implements DashTrackSelector {

      private final int adaptationSetType;
      private final String[] representationIds;

      private TrackSelector(int adaptationSetType, String[] representationIds) {
        this.adaptationSetType = adaptationSetType;
        this.representationIds = representationIds;
      }

      @Override
      public void selectTracks(MediaPresentationDescription manifest, int periodIndex,
          Output output) throws IOException {
        Period period = manifest.getPeriod(periodIndex);
        int adaptationSetIndex = period.getAdaptationSetIndex(adaptationSetType);
        AdaptationSet adaptationSet = period.adaptationSets.get(adaptationSetIndex);
        int[] representationIndices = getRepresentationIndices(representationIds, adaptationSet);
        if (adaptationSetType == AdaptationSet.TYPE_VIDEO) {
          output.adaptiveTrack(manifest, periodIndex, adaptationSetIndex, representationIndices);
        }
        for (int i = 0; i < representationIndices.length; i++) {
          output.fixedTrack(manifest, periodIndex, adaptationSetIndex, representationIndices[i]);
        }
      }

      private static int[] getRepresentationIndices(String[] representationIds,
          AdaptationSet adaptationSet) {
        List<Representation> representations = adaptationSet.representations;
        int[] representationIndices = new int[representationIds.length];
        for (int i = 0; i < representationIds.length; i++) {
          String representationId = representationIds[i];
          boolean foundIndex = false;
          for (int j = 0; j < representations.size() && !foundIndex; j++) {
            if (representations.get(j).format.id.equals(representationId)) {
              representationIndices[i] = j;
              foundIndex = true;
            }
          }
          if (!foundIndex) {
            throw new IllegalStateException("Representation " + representationId + " not found.");
          }
        }
        return representationIndices;
      }

    }

  }

}
