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

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MultiSampleSource;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.demo.player.DemoPlayer.SourceBuilder;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.hls.PtsTimestampAdjusterProvider;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;

/**
 * A {@link SourceBuilder} for HLS.
 */
// TODO[REFACTOR]: Bring back caption support.
public class HlsSourceBuilder implements SourceBuilder {

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int MAIN_BUFFER_SEGMENTS = 254;
  private static final int AUDIO_BUFFER_SEGMENTS = 54;
  private static final int TEXT_BUFFER_SEGMENTS = 2;

  private final Context context;
  private final String userAgent;
  private final String url;

  public HlsSourceBuilder(Context context, String userAgent, String url) {
    this.context = context;
    this.userAgent = userAgent;
    this.url = url;
  }

  @Override
  public SampleSource buildRenderers(DemoPlayer player) {
    HlsPlaylistParser parser = new HlsPlaylistParser();
    DefaultDataSource manifestDataSource = new DefaultDataSource(context, userAgent);
    ManifestFetcher<HlsPlaylist> manifestFetcher = new ManifestFetcher<>(Uri.parse(url),
        manifestDataSource, parser);

    Handler mainHandler = player.getMainHandler();
    BandwidthMeter bandwidthMeter = player.getBandwidthMeter();
    LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
    PtsTimestampAdjusterProvider timestampAdjusterProvider = new PtsTimestampAdjusterProvider();

    DataSource defaultDataSource = new DefaultDataSource(context, bandwidthMeter, userAgent);
    HlsChunkSource defaultChunkSource = new HlsChunkSource(manifestFetcher,
        HlsChunkSource.TYPE_DEFAULT, defaultDataSource, timestampAdjusterProvider,
        new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter));
    HlsSampleSource defaultSampleSource = new HlsSampleSource(defaultChunkSource, loadControl,
        MAIN_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_VIDEO);

    DataSource audioDataSource = new DefaultDataSource(context, bandwidthMeter, userAgent);
    HlsChunkSource audioChunkSource = new HlsChunkSource(manifestFetcher, HlsChunkSource.TYPE_AUDIO,
        audioDataSource, timestampAdjusterProvider, null);
    HlsSampleSource audioSampleSource = new HlsSampleSource(audioChunkSource, loadControl,
        AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_AUDIO);

    DataSource subtitleDataSource = new DefaultDataSource(context, bandwidthMeter, userAgent);
    HlsChunkSource subtitleChunkSource = new HlsChunkSource(manifestFetcher,
        HlsChunkSource.TYPE_SUBTITLE, subtitleDataSource, timestampAdjusterProvider, null);
    HlsSampleSource subtitleSampleSource = new HlsSampleSource(subtitleChunkSource, loadControl,
        TEXT_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, DemoPlayer.TYPE_TEXT);

    return new MultiSampleSource(defaultSampleSource, audioSampleSource, subtitleSampleSource);
  }

}
