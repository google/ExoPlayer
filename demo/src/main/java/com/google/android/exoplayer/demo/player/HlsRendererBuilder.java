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

import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.demo.player.DemoPlayer.RendererBuilder;
import com.google.android.exoplayer.demo.player.DemoPlayer.RendererBuilderCallback;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsChunkSourceImpl;
import com.google.android.exoplayer.hls.MultiTrackHlsChunkSource;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsMasterPlaylist;
import com.google.android.exoplayer.hls.HlsMediaPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.hls.AlternateMedia;
import com.google.android.exoplayer.metadata.Id3Parser;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer;
import com.google.android.exoplayer.text.eia608.Eia608TrackRenderer;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.UriDataSource;
import com.google.android.exoplayer.util.UriUtil;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;

import android.media.MediaCodec;
import android.net.Uri;

import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;


/**
 * A {@link RendererBuilder} for HLS.
 */
public class HlsRendererBuilder implements RendererBuilder, ManifestCallback<HlsPlaylist> {

  private final String userAgent;
  private final String url;
  private HlsMediaPlaylist[] alternatePlaylists;
  private HlsMasterPlaylist master;
  private int toFetch;

  private DemoPlayer player;
  private RendererBuilderCallback callback;

  public HlsRendererBuilder(String userAgent, String url) {
    this.userAgent = userAgent;
    this.url = url;
  }

  @Override
  public void buildRenderers(DemoPlayer player, RendererBuilderCallback callback) {
    this.player = player;
    this.callback = callback;
    HlsPlaylistParser parser = new HlsPlaylistParser();
    ManifestFetcher<HlsPlaylist> playlistFetcher =
        new ManifestFetcher<HlsPlaylist>(url, new DefaultHttpDataSource(userAgent, null), parser);
    playlistFetcher.singleLoad(player.getMainHandler().getLooper(), this);
  }

  @Override
  public void onSingleManifestError(IOException e) {
    callback.onRenderersError(e);
  }

  @Override
  public void onSingleManifest(HlsPlaylist manifest) {

    // Try to load alternate audio media
    if (manifest.type == HlsPlaylist.TYPE_MASTER) {
      master = (HlsMasterPlaylist) manifest;
      if (master.alternateAudio.size() > 0 && alternatePlaylists == null) {
        toFetch = master.alternateAudio.size();
        alternatePlaylists = new HlsMediaPlaylist[toFetch];
        for (AlternateMedia i: master.alternateAudio) {
          // Multiple audio track with one audio in the TS stream is
          // not supported
          if (i.uri == null) {
            toFetch--;
            continue;
          }
          HlsPlaylistParser parser = new HlsPlaylistParser();
          String url = UriUtil.resolveToUri(master.baseUri, i.uri).toString();
          ManifestFetcher<HlsPlaylist> playlistFetcher =
            new ManifestFetcher<HlsPlaylist>(url, new DefaultHttpDataSource(userAgent, null), parser);
          playlistFetcher.singleLoad(player.getMainHandler().getLooper(), this);
        }
      }
    } else if (alternatePlaylists != null) {
      toFetch--;
      for (AlternateMedia i: master.alternateAudio) {
        if (manifest.baseUri.equals(UriUtil.resolveToUri(master.baseUri, i.uri).toString())) {
          alternatePlaylists[i.index] = (HlsMediaPlaylist) manifest;
          break;
        }
      }
    }

    if (toFetch > 0)
      return;

    // if we do not have a master playlist it means we are trying to
    // directly play a media list.
    everythingLoaded(master != null ? master : manifest);
  }

  private void everythingLoaded(HlsPlaylist manifest) {
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

    DataSource dataSource = new UriDataSource(userAgent, bandwidthMeter);
    HlsChunkSource chunkSource = new HlsChunkSourceImpl(dataSource, url, manifest, bandwidthMeter, null,
        HlsChunkSourceImpl.ADAPTIVE_MODE_SPLICE);
    HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, true, 3);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
        MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, player.getMainHandler(), player, 50);

    MediaCodecAudioTrackRenderer audioRenderer;
    // Build the audio chunk sources.
    String[] audioTrackNames = null;
    MultiTrackHlsChunkSource audioChunkSource = null;


    boolean canUseAlternate = false;
    if (manifest.type == HlsPlaylist.TYPE_MASTER) {
      if (master.alternateAudio.size() > 0)
          canUseAlternate = true;
      for (AlternateMedia i: master.alternateAudio) {
        // We do not support alternate media in TS stream
        if (i.uri == null) {
          canUseAlternate = false;
          break;
        }
      }
    }

    if (canUseAlternate) {
      DataSource audioDataSource = new UriDataSource(userAgent, bandwidthMeter);
      List<HlsChunkSource> audioChunkSourceList = new ArrayList<HlsChunkSource>();
      List<String> audioTrackNameList = new ArrayList<String>();
      for (AlternateMedia i: master.alternateAudio) {
        audioChunkSourceList.add(new HlsChunkSourceImpl(audioDataSource, UriUtil.resolveToUri(master.baseUri, i.uri).toString(), alternatePlaylists[i.index], bandwidthMeter, null, HlsChunkSourceImpl.ADAPTIVE_MODE_NONE));
        audioTrackNameList.add(i.name);
      }

      // Build the audio renderer.
      if (audioChunkSourceList.isEmpty()) {
        audioTrackNames = null;
        audioChunkSource = null;
        audioRenderer = null;
      } else {
        audioTrackNames = new String[audioTrackNameList.size()];
        audioTrackNameList.toArray(audioTrackNames);
        audioChunkSource = new MultiTrackHlsChunkSource(audioChunkSourceList);
        HlsSampleSource audioSampleSource = new HlsSampleSource(audioChunkSource, true, 3);
        audioRenderer = new MediaCodecAudioTrackRenderer(audioSampleSource, null, true,
                                                         player.getMainHandler(), player);
      }
    } else {
      audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);
    }

    MetadataTrackRenderer<Map<String, Object>> id3Renderer =
        new MetadataTrackRenderer<Map<String, Object>>(sampleSource, new Id3Parser(),
            player.getId3MetadataRenderer(), player.getMainHandler().getLooper());

    Eia608TrackRenderer closedCaptionRenderer = new Eia608TrackRenderer(sampleSource, player,
        player.getMainHandler().getLooper());

    String[][] trackNames = new String[DemoPlayer.RENDERER_COUNT][];
    trackNames[DemoPlayer.TYPE_AUDIO] = audioTrackNames;

    MultiTrackHlsChunkSource[] multiTrackChunkSources =
      new MultiTrackHlsChunkSource[DemoPlayer.RENDERER_COUNT];
    multiTrackChunkSources[DemoPlayer.TYPE_AUDIO] = audioChunkSource;

    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
    renderers[DemoPlayer.TYPE_TIMED_METADATA] = id3Renderer;
    renderers[DemoPlayer.TYPE_TEXT] = closedCaptionRenderer;
    callback.onRenderers(trackNames, multiTrackChunkSources, renderers);
  }

}
