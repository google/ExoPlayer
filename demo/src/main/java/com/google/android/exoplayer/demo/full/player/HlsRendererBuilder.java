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
package com.google.android.exoplayer.demo.full.player;

import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.demo.DemoUtil;
import com.google.android.exoplayer.demo.full.player.DemoPlayer.RendererBuilder;
import com.google.android.exoplayer.demo.full.player.DemoPlayer.RendererBuilderCallback;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsMasterPlaylist;
import com.google.android.exoplayer.hls.HlsMasterPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.hls.Variant;
import com.google.android.exoplayer.metadata.ClosedCaption;
import com.google.android.exoplayer.metadata.Eia608Parser;
import com.google.android.exoplayer.metadata.Id3Parser;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.UriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;
import com.google.android.exoplayer.util.MimeTypes;

import android.media.MediaCodec;
import android.net.Uri;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A {@link RendererBuilder} for HLS.
 */
public class HlsRendererBuilder implements RendererBuilder, ManifestCallback<HlsMasterPlaylist> {

  private final String userAgent;
  private final String url;
  private final String contentId;
  private final int contentType;

  private DemoPlayer player;
  private RendererBuilderCallback callback;

  public HlsRendererBuilder(String userAgent, String url, String contentId, int contentType) {
    this.userAgent = userAgent;
    this.url = url;
    this.contentId = contentId;
    this.contentType = contentType;
  }

  @Override
  public void buildRenderers(DemoPlayer player, RendererBuilderCallback callback) {
    this.player = player;
    this.callback = callback;
    switch (contentType) {
      case DemoUtil.TYPE_HLS_MASTER:
        HlsMasterPlaylistParser parser = new HlsMasterPlaylistParser();
        ManifestFetcher<HlsMasterPlaylist> mediaPlaylistFetcher =
            new ManifestFetcher<HlsMasterPlaylist>(parser, contentId, url, userAgent);
        mediaPlaylistFetcher.singleLoad(player.getMainHandler().getLooper(), this);
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
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

    DataSource dataSource = new UriDataSource(userAgent, bandwidthMeter);
    HlsChunkSource chunkSource = new HlsChunkSource(dataSource, manifest, bandwidthMeter, null,
        MediaCodecUtil.getDecoderInfo(MimeTypes.VIDEO_H264, false).adaptive);
    HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, true, 3);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
        MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 0, player.getMainHandler(), player, 50);
    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);

    MetadataTrackRenderer<Map<String, Object>> id3Renderer =
        new MetadataTrackRenderer<Map<String, Object>>(sampleSource, new Id3Parser(),
            player.getId3MetadataRenderer(), player.getMainHandler().getLooper());

    MetadataTrackRenderer<List<ClosedCaption>> closedCaptionRenderer =
        new MetadataTrackRenderer<List<ClosedCaption>>(sampleSource, new Eia608Parser(),
            player.getClosedCaptionMetadataRenderer(), player.getMainHandler().getLooper());

    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
    renderers[DemoPlayer.TYPE_TIMED_METADATA] = id3Renderer;
    renderers[DemoPlayer.TYPE_CLOSED_CAPTIONS] = closedCaptionRenderer;
    callback.onRenderers(null, null, renderers);
  }

  private HlsMasterPlaylist newSimpleMasterPlaylist(String mediaPlaylistUrl) {
    return new HlsMasterPlaylist(Uri.parse(""),
        Collections.singletonList(new Variant(0, mediaPlaylistUrl, 0, null, -1, -1)));
  }

}
