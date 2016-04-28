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
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.dash.DashSampleSource;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.hls.PtsTimestampAdjusterProvider;
import com.google.android.exoplayer.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.util.ManifestFetcher;

import android.net.Uri;
import android.os.Handler;

/**
 * A utility class for building {@link SampleSource} instances.
 */
public class SourceBuilder {

  private SourceBuilder() {}

  // TODO[REFACTOR]: Bring back DASH DRM support.
  // TODO[REFACTOR]: Bring back DASH UTC timing element support.
  public static SampleSource buildDashSource(DemoPlayer player, DataSourceFactory dataSourceFactory,
      Uri uri, MediaDrmCallback drmCallback) {
    return new DashSampleSource(uri, dataSourceFactory, player.getBandwidthMeter(),
        player.getMainHandler(), player);
  }

  // TODO[REFACTOR]: Bring back DRM support.
  public static SampleSource buildSmoothStreamingSource(DemoPlayer player,
      DataSourceFactory dataSourceFactory, Uri uri, MediaDrmCallback drmCallback) {
    return new SmoothStreamingSampleSource(uri, dataSourceFactory, player.getBandwidthMeter(),
        player.getMainHandler(), player);
  }

  public static SampleSource buildHlsSource(DemoPlayer player, DataSourceFactory dataSourceFactory,
      Uri uri) {
    HlsPlaylistParser parser = new HlsPlaylistParser();
    DataSource manifestDataSource = dataSourceFactory.createDataSource();
    // TODO[REFACTOR]: This needs releasing.
    ManifestFetcher<HlsPlaylist> manifestFetcher = new ManifestFetcher<>(uri, manifestDataSource,
        parser);

    Handler mainHandler = player.getMainHandler();
    BandwidthMeter bandwidthMeter = player.getBandwidthMeter();
    LoadControl loadControl = new DefaultLoadControl(
        new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE));
    PtsTimestampAdjusterProvider timestampAdjusterProvider = new PtsTimestampAdjusterProvider();

    DataSource defaultDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource defaultChunkSource = new HlsChunkSource(manifestFetcher, C.TRACK_TYPE_DEFAULT,
        defaultDataSource, timestampAdjusterProvider,
        new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter));
    HlsSampleSource defaultSampleSource = new HlsSampleSource(defaultChunkSource, loadControl,
        C.DEFAULT_MUXED_BUFFER_SIZE, mainHandler, player, C.TRACK_TYPE_VIDEO);

    DataSource audioDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource audioChunkSource = new HlsChunkSource(manifestFetcher, C.TRACK_TYPE_AUDIO,
        audioDataSource, timestampAdjusterProvider, null);
    HlsSampleSource audioSampleSource = new HlsSampleSource(audioChunkSource, loadControl,
        C.DEFAULT_AUDIO_BUFFER_SIZE, mainHandler, player, C.TRACK_TYPE_AUDIO);

    DataSource subtitleDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource subtitleChunkSource = new HlsChunkSource(manifestFetcher, C.TRACK_TYPE_TEXT,
        subtitleDataSource, timestampAdjusterProvider, null);
    HlsSampleSource subtitleSampleSource = new HlsSampleSource(subtitleChunkSource, loadControl,
        C.DEFAULT_TEXT_BUFFER_SIZE, mainHandler, player, C.TRACK_TYPE_TEXT);

    return new MultiSampleSource(defaultSampleSource, audioSampleSource, subtitleSampleSource);
  }

  public static SampleSource buildExtractorSource(DemoPlayer player,
      DataSourceFactory dataSourceFactory, Uri uri) {
    Allocator allocator = new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE);
    DataSource dataSource = dataSourceFactory.createDataSource(player.getBandwidthMeter());
    return new ExtractorSampleSource(uri, dataSource, allocator,
        C.DEFAULT_MUXED_BUFFER_SIZE, player.getMainHandler(), player, 0);
  }

}
