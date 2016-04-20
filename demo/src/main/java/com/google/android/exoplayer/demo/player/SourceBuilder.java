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
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.AdaptiveEvaluator;
import com.google.android.exoplayer.dash.DashChunkSource;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescriptionParser;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.hls.PtsTimestampAdjusterProvider;
import com.google.android.exoplayer.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingChunkSource;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifestParser;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.Handler;

/**
 * A utility class for building {@link SampleSource} instances.
 */
public class SourceBuilder {

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;

  private static final int MUXED_BUFFER_SEGMENTS = 256;
  private static final int VIDEO_BUFFER_SEGMENTS = 200;
  private static final int AUDIO_BUFFER_SEGMENTS = 54;
  private static final int TEXT_BUFFER_SEGMENTS = 2;

  private SourceBuilder() {}

  // TODO[REFACTOR]: Bring back DASH DRM support.
  // TODO[REFACTOR]: Bring back DASH UTC timing element support.
  public static SampleSource buildDashSource(DemoPlayer player, DataSourceFactory dataSourceFactory,
      Uri uri, MediaDrmCallback drmCallback) {
    MediaPresentationDescriptionParser parser = new MediaPresentationDescriptionParser();
    DataSource manifestDataSource = dataSourceFactory.createDataSource();
    // TODO[REFACTOR]: This needs releasing.
    ManifestFetcher<MediaPresentationDescription> manifestFetcher = new ManifestFetcher<>(uri,
        manifestDataSource, parser);

    Handler mainHandler = player.getMainHandler();
    BandwidthMeter bandwidthMeter = player.getBandwidthMeter();
    LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));

    // Build the video renderer.
    DataSource videoDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    ChunkSource videoChunkSource = new DashChunkSource(manifestFetcher, C.TRACK_TYPE_VIDEO,
        videoDataSource, new AdaptiveEvaluator(bandwidthMeter));
    ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
        VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_VIDEO);

    // Build the audio renderer.
    DataSource audioDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    ChunkSource audioChunkSource = new DashChunkSource(manifestFetcher, C.TRACK_TYPE_AUDIO,
        audioDataSource, null);
    ChunkSampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource, loadControl,
        AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player,
        DemoPlayer.TYPE_AUDIO);

    // Build the text renderer.
    DataSource textDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    ChunkSource textChunkSource = new DashChunkSource(manifestFetcher, C.TRACK_TYPE_TEXT,
        textDataSource, null);
    ChunkSampleSource textSampleSource = new ChunkSampleSource(textChunkSource, loadControl,
        TEXT_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player,
        DemoPlayer.TYPE_TEXT);

    return new MultiSampleSource(videoSampleSource, audioSampleSource, textSampleSource);
  }

  // TODO[REFACTOR]: Bring back DRM support.
  public static SampleSource buildSmoothStreamingSource(DemoPlayer player,
      DataSourceFactory dataSourceFactory, Uri uri, MediaDrmCallback drmCallback) {
    if (!Util.toLowerInvariant(uri.getLastPathSegment()).equals("manifest")) {
      uri = Uri.withAppendedPath(uri, "Manifest");
    }
    SmoothStreamingManifestParser parser = new SmoothStreamingManifestParser();
    // TODO[REFACTOR]: This needs releasing.
    DataSource manifestDataSource = dataSourceFactory.createDataSource();
    ManifestFetcher<SmoothStreamingManifest> manifestFetcher = new ManifestFetcher<>(uri,
        manifestDataSource, parser);
    Handler mainHandler = player.getMainHandler();
    BandwidthMeter bandwidthMeter = player.getBandwidthMeter();
    LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));

    // Build the video renderer.
    DataSource videoDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    ChunkSource videoChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
        C.TRACK_TYPE_VIDEO, videoDataSource, new AdaptiveEvaluator(bandwidthMeter));
    ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
        VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player,
        DemoPlayer.TYPE_VIDEO);

    // Build the audio renderer.
    DataSource audioDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    ChunkSource audioChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
        C.TRACK_TYPE_AUDIO, audioDataSource, null);
    ChunkSampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource, loadControl,
        AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_AUDIO);

    // Build the text renderer.
    DataSource textDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    ChunkSource textChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
        C.TRACK_TYPE_TEXT, textDataSource, null);
    ChunkSampleSource textSampleSource = new ChunkSampleSource(textChunkSource, loadControl,
        TEXT_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_TEXT);

    return new MultiSampleSource(videoSampleSource, audioSampleSource, textSampleSource);
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
    LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
    PtsTimestampAdjusterProvider timestampAdjusterProvider = new PtsTimestampAdjusterProvider();

    DataSource defaultDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource defaultChunkSource = new HlsChunkSource(manifestFetcher,
        C.TRACK_TYPE_DEFAULT, defaultDataSource, timestampAdjusterProvider,
        new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter));
    HlsSampleSource defaultSampleSource = new HlsSampleSource(defaultChunkSource, loadControl,
        MUXED_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_VIDEO);

    DataSource audioDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource audioChunkSource = new HlsChunkSource(manifestFetcher, C.TRACK_TYPE_AUDIO,
        audioDataSource, timestampAdjusterProvider, null);
    HlsSampleSource audioSampleSource = new HlsSampleSource(audioChunkSource, loadControl,
        AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_AUDIO);

    DataSource subtitleDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource subtitleChunkSource = new HlsChunkSource(manifestFetcher,
        C.TRACK_TYPE_TEXT, subtitleDataSource, timestampAdjusterProvider, null);
    HlsSampleSource subtitleSampleSource = new HlsSampleSource(subtitleChunkSource, loadControl,
        TEXT_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_TEXT);

    return new MultiSampleSource(defaultSampleSource, audioSampleSource, subtitleSampleSource);
  }

  public static SampleSource buildExtractorSource(DemoPlayer player,
      DataSourceFactory dataSourceFactory, Uri uri) {
    Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
    DataSource dataSource = dataSourceFactory.createDataSource(player.getBandwidthMeter());
    return new ExtractorSampleSource(uri, dataSource, allocator,
        MUXED_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, player.getMainHandler(), player, 0);
  }

}
