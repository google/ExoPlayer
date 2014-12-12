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

import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.demo.full.player.DemoPlayer;
import com.google.android.exoplayer.demo.simple.SimplePlayerActivity.RendererBuilder;
import com.google.android.exoplayer.demo.simple.SimplePlayerActivity.RendererBuilderCallback;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.UriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;
import com.google.android.exoplayer.util.MimeTypes;

import android.media.MediaCodec;

import java.io.IOException;

/**
 * A {@link RendererBuilder} for HLS.
 */
/* package */ class HlsRendererBuilder implements RendererBuilder,
    ManifestCallback<HlsPlaylist> {

  private final SimplePlayerActivity playerActivity;
  private final String userAgent;
  private final String url;
  private final String contentId;

  private RendererBuilderCallback callback;

  public HlsRendererBuilder(SimplePlayerActivity playerActivity, String userAgent, String url,
      String contentId) {
    this.playerActivity = playerActivity;
    this.userAgent = userAgent;
    this.url = url;
    this.contentId = contentId;
  }

  @Override
  public void buildRenderers(RendererBuilderCallback callback) {
    this.callback = callback;
    HlsPlaylistParser parser = new HlsPlaylistParser();
    ManifestFetcher<HlsPlaylist> playlistFetcher =
        new ManifestFetcher<HlsPlaylist>(parser, contentId, url, userAgent);
    playlistFetcher.singleLoad(playerActivity.getMainLooper(), this);
  }

  @Override
  public void onManifestError(String contentId, IOException e) {
    callback.onRenderersError(e);
  }

  @Override
  public void onManifest(String contentId, HlsPlaylist manifest) {
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

    DataSource dataSource = new UriDataSource(userAgent, bandwidthMeter);
    boolean adaptiveDecoder = MediaCodecUtil.getDecoderInfo(MimeTypes.VIDEO_H264, false).adaptive;
    HlsChunkSource chunkSource = new HlsChunkSource(dataSource, url, manifest, bandwidthMeter, null,
        adaptiveDecoder ? HlsChunkSource.ADAPTIVE_MODE_SPLICE : HlsChunkSource.ADAPTIVE_MODE_NONE);
    HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, true, 2);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
        MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 0, playerActivity.getMainHandler(),
        playerActivity, 50);
    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);

    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
    callback.onRenderers(videoRenderer, audioRenderer);
  }

}
