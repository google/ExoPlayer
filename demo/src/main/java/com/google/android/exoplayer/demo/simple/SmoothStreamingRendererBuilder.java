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
package com.google.android.exoplayer.demo.simple;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.AdaptiveEvaluator;
import com.google.android.exoplayer.demo.simple.SimplePlayerActivity.RendererBuilder;
import com.google.android.exoplayer.demo.simple.SimplePlayerActivity.RendererBuilderCallback;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingChunkSource;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.TrackElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifestParser;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.UriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;
import com.google.android.exoplayer.util.Util;

import android.media.MediaCodec;
import android.os.Handler;

import java.io.IOException;
import java.util.ArrayList;

/**
 * A {@link RendererBuilder} for SmoothStreaming.
 */
/* package */ class SmoothStreamingRendererBuilder implements RendererBuilder,
    ManifestCallback<SmoothStreamingManifest> {

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int VIDEO_BUFFER_SEGMENTS = 200;
  private static final int AUDIO_BUFFER_SEGMENTS = 60;
  private static final int LIVE_EDGE_LATENCY_MS = 30000;

  private final SimplePlayerActivity playerActivity;
  private final String userAgent;
  private final String url;
  private final String contentId;

  private RendererBuilderCallback callback;
  private ManifestFetcher<SmoothStreamingManifest> manifestFetcher;

  public SmoothStreamingRendererBuilder(SimplePlayerActivity playerActivity, String userAgent,
      String url, String contentId) {
    this.playerActivity = playerActivity;
    this.userAgent = userAgent;
    this.url = url;
    this.contentId = contentId;
  }

  @Override
  public void buildRenderers(RendererBuilderCallback callback) {
    this.callback = callback;
    SmoothStreamingManifestParser parser = new SmoothStreamingManifestParser();
    manifestFetcher = new ManifestFetcher<SmoothStreamingManifest>(parser, contentId,
        url + "/Manifest", userAgent);
    manifestFetcher.singleLoad(playerActivity.getMainLooper(), this);
  }

  @Override
  public void onManifestError(String contentId, IOException e) {
    callback.onRenderersError(e);
  }

  @Override
  public void onManifest(String contentId, SmoothStreamingManifest manifest) {
    Handler mainHandler = playerActivity.getMainHandler();
    LoadControl loadControl = new DefaultLoadControl(new BufferPool(BUFFER_SEGMENT_SIZE));
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

    // Obtain stream elements for playback.
    int maxDecodableFrameSize = MediaCodecUtil.maxH264DecodableFrameSize();
    int audioStreamElementIndex = -1;
    int videoStreamElementIndex = -1;
    ArrayList<Integer> videoTrackIndexList = new ArrayList<Integer>();
    for (int i = 0; i < manifest.streamElements.length; i++) {
      if (audioStreamElementIndex == -1
          && manifest.streamElements[i].type == StreamElement.TYPE_AUDIO) {
        audioStreamElementIndex = i;
      } else if (videoStreamElementIndex == -1
          && manifest.streamElements[i].type == StreamElement.TYPE_VIDEO) {
        videoStreamElementIndex = i;
        StreamElement streamElement = manifest.streamElements[i];
        for (int j = 0; j < streamElement.tracks.length; j++) {
          TrackElement trackElement = streamElement.tracks[j];
          if (trackElement.maxWidth * trackElement.maxHeight <= maxDecodableFrameSize) {
            videoTrackIndexList.add(j);
          } else {
            // The device isn't capable of playing this stream.
          }
        }
      }
    }
    int[] videoTrackIndices = Util.toArray(videoTrackIndexList);

    // Build the video renderer.
    DataSource videoDataSource = new UriDataSource(userAgent, bandwidthMeter);
    ChunkSource videoChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
        videoStreamElementIndex, videoTrackIndices, videoDataSource,
        new AdaptiveEvaluator(bandwidthMeter), LIVE_EDGE_LATENCY_MS);
    ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
        VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(videoSampleSource,
        MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 0, mainHandler, playerActivity, 50);

    // Build the audio renderer.
    DataSource audioDataSource = new UriDataSource(userAgent, bandwidthMeter);
    ChunkSource audioChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
        audioStreamElementIndex, new int[] {0}, audioDataSource,
        new FormatEvaluator.FixedEvaluator(), LIVE_EDGE_LATENCY_MS);
    SampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource, loadControl,
        AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true);
    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
        audioSampleSource);
    callback.onRenderers(videoRenderer, audioRenderer);
  }

}
