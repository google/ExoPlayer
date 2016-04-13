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
package com.google.android.exoplayer.demo.player;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MultiSampleSource;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.FormatEvaluator.AdaptiveEvaluator;
import com.google.android.exoplayer.demo.player.DemoPlayer.SourceBuilder;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingChunkSource;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifestParser;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.Handler;

/**
 * A {@link SourceBuilder} for SmoothStreaming.
 */
// TODO[REFACTOR]: Bring back DRM support.
public class SmoothStreamingSourceBuilder implements SourceBuilder {

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int VIDEO_BUFFER_SEGMENTS = 200;
  private static final int AUDIO_BUFFER_SEGMENTS = 54;
  private static final int TEXT_BUFFER_SEGMENTS = 2;
  private static final int LIVE_EDGE_LATENCY_MS = 30000;

  private final DataSourceFactory dataSourceFactory;
  private final String url;
  private final MediaDrmCallback drmCallback;

  public SmoothStreamingSourceBuilder(DataSourceFactory dataSourceFactory, String url,
      MediaDrmCallback drmCallback) {
    this.dataSourceFactory = dataSourceFactory;
    this.url = Util.toLowerInvariant(url).endsWith("/manifest") ? url : url + "/Manifest";
    this.drmCallback = drmCallback;
  }

  @Override
  public SampleSource buildRenderers(DemoPlayer player) {
    SmoothStreamingManifestParser parser = new SmoothStreamingManifestParser();
    // TODO[REFACTOR]: This needs releasing.
    DataSource manifestDataSource = dataSourceFactory.createDataSource();
    ManifestFetcher<SmoothStreamingManifest> manifestFetcher = new ManifestFetcher<>(Uri.parse(url),
        manifestDataSource, parser);
    Handler mainHandler = player.getMainHandler();
    BandwidthMeter bandwidthMeter = player.getBandwidthMeter();
    LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));

    // Build the video renderer.
    DataSource videoDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    ChunkSource videoChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
        C.TRACK_TYPE_VIDEO, videoDataSource, new AdaptiveEvaluator(bandwidthMeter),
        LIVE_EDGE_LATENCY_MS);
    ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
        VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player,
        DemoPlayer.TYPE_VIDEO);

    // Build the audio renderer.
    DataSource audioDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    ChunkSource audioChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
        C.TRACK_TYPE_AUDIO, audioDataSource, null, LIVE_EDGE_LATENCY_MS);
    ChunkSampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource, loadControl,
        AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_AUDIO);

    // Build the text renderer.
    DataSource textDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    ChunkSource textChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
        C.TRACK_TYPE_TEXT, textDataSource, null, LIVE_EDGE_LATENCY_MS);
    ChunkSampleSource textSampleSource = new ChunkSampleSource(textChunkSource, loadControl,
        TEXT_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_TEXT);

    return new MultiSampleSource(videoSampleSource, audioSampleSource, textSampleSource);
  }

}
