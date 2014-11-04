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
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.demo.DemoUtil;
import com.google.android.exoplayer.demo.full.player.DemoPlayer;
import com.google.android.exoplayer.demo.simple.SimplePlayerActivity.RendererBuilder;
import com.google.android.exoplayer.demo.simple.SimplePlayerActivity.RendererBuilderCallback;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsMasterPlaylist;
import com.google.android.exoplayer.hls.HlsMasterPlaylist.Variant;
import com.google.android.exoplayer.hls.HlsMasterPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.UriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;

import android.media.MediaCodec;
import android.net.Uri;

import java.io.IOException;
import java.util.Collections;

/**
 * A {@link RendererBuilder} for HLS.
 */
/* package */ class HlsRendererBuilder implements RendererBuilder,
    ManifestCallback<HlsMasterPlaylist> {

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int VIDEO_BUFFER_SEGMENTS = 200;

  private final SimplePlayerActivity playerActivity;
  private final String userAgent;
  private final String url;
  private final String contentId;
  private final int playlistType;

  private RendererBuilderCallback callback;

  public HlsRendererBuilder(SimplePlayerActivity playerActivity, String userAgent, String url,
      String contentId, int playlistType) {
    this.playerActivity = playerActivity;
    this.userAgent = userAgent;
    this.url = url;
    this.contentId = contentId;
    this.playlistType = playlistType;
  }

  @Override
  public void buildRenderers(RendererBuilderCallback callback) {
    this.callback = callback;
    switch (playlistType) {
      case DemoUtil.TYPE_HLS_MASTER:
        HlsMasterPlaylistParser parser = new HlsMasterPlaylistParser();
        ManifestFetcher<HlsMasterPlaylist> mediaPlaylistFetcher =
            new ManifestFetcher<HlsMasterPlaylist>(parser, contentId, url, userAgent);
        mediaPlaylistFetcher.singleLoad(playerActivity.getMainLooper(), this);
        break;
      case DemoUtil.TYPE_HLS_MEDIA:
        onManifest(contentId, newSimpleMasterPlaylist(url));
        break;
    }
  }

  @Override
  public void onManifestError(String contentId, IOException e) {
    callback.onRenderersError(e);
  }

  @Override
  public void onManifest(String contentId, HlsMasterPlaylist manifest) {
    LoadControl loadControl = new DefaultLoadControl(new BufferPool(BUFFER_SEGMENT_SIZE));

    DataSource dataSource = new UriDataSource(userAgent, null);
    HlsChunkSource chunkSource = new HlsChunkSource(dataSource, manifest);
    HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, loadControl,
        VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true, 2);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
        MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 0, playerActivity.getMainHandler(),
        playerActivity, 50);
    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);

    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
    callback.onRenderers(videoRenderer, audioRenderer);
  }

  private HlsMasterPlaylist newSimpleMasterPlaylist(String mediaPlaylistUrl) {
    return new HlsMasterPlaylist(Uri.parse(""),
        Collections.singletonList(new Variant(mediaPlaylistUrl, 0, null, -1, -1)));
  }

}
