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
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.AdaptiveEvaluator;
import com.google.android.exoplayer.chunk.MultiTrackChunkSource;
import com.google.android.exoplayer.chunk.VideoFormatSelectorUtil;
import com.google.android.exoplayer.demo.player.DemoPlayer.RendererBuilder;
import com.google.android.exoplayer.demo.player.DemoPlayer.RendererBuilderCallback;
import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingChunkSource;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifestParser;
import com.google.android.exoplayer.text.TextTrackRenderer;
import com.google.android.exoplayer.text.ttml.TtmlParser;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.UnsupportedSchemeException;
import android.os.Handler;
import android.widget.TextView;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * A {@link RendererBuilder} for SmoothStreaming.
 */
public class SmoothStreamingRendererBuilder implements RendererBuilder,
    ManifestFetcher.ManifestCallback<SmoothStreamingManifest> {

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int VIDEO_BUFFER_SEGMENTS = 200;
  private static final int AUDIO_BUFFER_SEGMENTS = 60;
  private static final int TEXT_BUFFER_SEGMENTS = 2;
  private static final int LIVE_EDGE_LATENCY_MS = 30000;

  private final Context context;
  private final String userAgent;
  private final String url;
  private final MediaDrmCallback drmCallback;
  private final TextView debugTextView;

  private DemoPlayer player;
  private RendererBuilderCallback callback;
  private ManifestFetcher<SmoothStreamingManifest> manifestFetcher;

  public SmoothStreamingRendererBuilder(Context context, String userAgent, String url,
      MediaDrmCallback drmCallback, TextView debugTextView) {
    this.context = context;
    this.userAgent = userAgent;
    this.url = url;
    this.drmCallback = drmCallback;
    this.debugTextView = debugTextView;
  }

  @Override
  public void buildRenderers(DemoPlayer player, RendererBuilderCallback callback) {
    this.player = player;
    this.callback = callback;
    String manifestUrl = url;
    if (!manifestUrl.endsWith("/Manifest")) {
      manifestUrl += "/Manifest";
    }
    SmoothStreamingManifestParser parser = new SmoothStreamingManifestParser();
    manifestFetcher = new ManifestFetcher<SmoothStreamingManifest>(manifestUrl,
        new DefaultHttpDataSource(userAgent, null), parser);
    manifestFetcher.singleLoad(player.getMainHandler().getLooper(), this);
  }

  @Override
  public void onSingleManifestError(IOException exception) {
    callback.onRenderersError(exception);
  }

  @Override
  public void onSingleManifest(SmoothStreamingManifest manifest) {
    Handler mainHandler = player.getMainHandler();
    LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler, player);

    // Check drm support if necessary.
    DrmSessionManager drmSessionManager = null;
    if (manifest.protectionElement != null) {
      if (Util.SDK_INT < 18) {
        callback.onRenderersError(
            new UnsupportedDrmException(UnsupportedDrmException.REASON_NO_DRM));
        return;
      }
      try {
        drmSessionManager = V18Compat.getDrmSessionManager(manifest.protectionElement.uuid, player,
            drmCallback);
      } catch (UnsupportedDrmException e) {
        callback.onRenderersError(e);
        return;
      }
    }

    // Obtain stream elements for playback.
    int audioStreamElementCount = 0;
    int textStreamElementCount = 0;
    int videoStreamElementIndex = -1;
    for (int i = 0; i < manifest.streamElements.length; i++) {
      if (manifest.streamElements[i].type == StreamElement.TYPE_AUDIO) {
        audioStreamElementCount++;
      } else if (manifest.streamElements[i].type == StreamElement.TYPE_TEXT) {
        textStreamElementCount++;
      } else if (videoStreamElementIndex == -1
          && manifest.streamElements[i].type == StreamElement.TYPE_VIDEO) {
        videoStreamElementIndex = i;
      }
    }

    // Determine which video tracks we should use for playback.
    int[] videoTrackIndices = null;
    if (videoStreamElementIndex != -1) {
      try {
        videoTrackIndices = VideoFormatSelectorUtil.selectVideoFormatsForDefaultDisplay(context,
            Arrays.asList(manifest.streamElements[videoStreamElementIndex].tracks), null, false);
      } catch (DecoderQueryException e) {
        callback.onRenderersError(e);
        return;
      }
    }

    // Build the video renderer.
    final MediaCodecVideoTrackRenderer videoRenderer;
    final TrackRenderer debugRenderer;
    if (videoTrackIndices == null || videoTrackIndices.length == 0) {
      videoRenderer = null;
      debugRenderer = null;
    } else {
      DataSource videoDataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
      ChunkSource videoChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
          videoStreamElementIndex, videoTrackIndices, videoDataSource,
          new AdaptiveEvaluator(bandwidthMeter), LIVE_EDGE_LATENCY_MS);
      ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
          VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true, mainHandler, player,
          DemoPlayer.TYPE_VIDEO);
      videoRenderer = new MediaCodecVideoTrackRenderer(videoSampleSource, drmSessionManager, true,
          MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, null, mainHandler, player, 50);
      debugRenderer = debugTextView != null
          ? new DebugTrackRenderer(debugTextView, player, videoRenderer) : null;
    }

    // Build the audio renderer.
    final String[] audioTrackNames;
    final MultiTrackChunkSource audioChunkSource;
    final MediaCodecAudioTrackRenderer audioRenderer;
    if (audioStreamElementCount == 0) {
      audioTrackNames = null;
      audioChunkSource = null;
      audioRenderer = null;
    } else {
      audioTrackNames = new String[audioStreamElementCount];
      ChunkSource[] audioChunkSources = new ChunkSource[audioStreamElementCount];
      DataSource audioDataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
      FormatEvaluator audioFormatEvaluator = new FormatEvaluator.FixedEvaluator();
      audioStreamElementCount = 0;
      for (int i = 0; i < manifest.streamElements.length; i++) {
        if (manifest.streamElements[i].type == StreamElement.TYPE_AUDIO) {
          audioTrackNames[audioStreamElementCount] = manifest.streamElements[i].name;
          audioChunkSources[audioStreamElementCount] = new SmoothStreamingChunkSource(
              manifestFetcher, i, new int[] {0}, audioDataSource, audioFormatEvaluator,
              LIVE_EDGE_LATENCY_MS);
          audioStreamElementCount++;
        }
      }
      audioChunkSource = new MultiTrackChunkSource(audioChunkSources);
      ChunkSampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource, loadControl,
          AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true, mainHandler, player,
          DemoPlayer.TYPE_AUDIO);
      audioRenderer = new MediaCodecAudioTrackRenderer(audioSampleSource, drmSessionManager, true,
          mainHandler, player);
    }

    // Build the text renderer.
    final String[] textTrackNames;
    final MultiTrackChunkSource textChunkSource;
    final TrackRenderer textRenderer;
    if (textStreamElementCount == 0) {
      textTrackNames = null;
      textChunkSource = null;
      textRenderer = null;
    } else {
      textTrackNames = new String[textStreamElementCount];
      ChunkSource[] textChunkSources = new ChunkSource[textStreamElementCount];
      DataSource ttmlDataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
      FormatEvaluator ttmlFormatEvaluator = new FormatEvaluator.FixedEvaluator();
      textStreamElementCount = 0;
      for (int i = 0; i < manifest.streamElements.length; i++) {
        if (manifest.streamElements[i].type == StreamElement.TYPE_TEXT) {
          textTrackNames[textStreamElementCount] = manifest.streamElements[i].language;
          textChunkSources[textStreamElementCount] = new SmoothStreamingChunkSource(manifestFetcher,
              i, new int[] {0}, ttmlDataSource, ttmlFormatEvaluator, LIVE_EDGE_LATENCY_MS);
          textStreamElementCount++;
        }
      }
      textChunkSource = new MultiTrackChunkSource(textChunkSources);
      ChunkSampleSource ttmlSampleSource = new ChunkSampleSource(textChunkSource, loadControl,
          TEXT_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, true, mainHandler, player,
          DemoPlayer.TYPE_TEXT);
      textRenderer = new TextTrackRenderer(ttmlSampleSource, player, mainHandler.getLooper(),
          new TtmlParser());
    }

    // Invoke the callback.
    String[][] trackNames = new String[DemoPlayer.RENDERER_COUNT][];
    trackNames[DemoPlayer.TYPE_AUDIO] = audioTrackNames;
    trackNames[DemoPlayer.TYPE_TEXT] = textTrackNames;

    MultiTrackChunkSource[] multiTrackChunkSources =
        new MultiTrackChunkSource[DemoPlayer.RENDERER_COUNT];
    multiTrackChunkSources[DemoPlayer.TYPE_AUDIO] = audioChunkSource;
    multiTrackChunkSources[DemoPlayer.TYPE_TEXT] = textChunkSource;

    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
    renderers[DemoPlayer.TYPE_TEXT] = textRenderer;
    renderers[DemoPlayer.TYPE_DEBUG] = debugRenderer;
    callback.onRenderers(trackNames, multiTrackChunkSources, renderers);
  }

  @TargetApi(18)
  private static class V18Compat {

    public static DrmSessionManager getDrmSessionManager(UUID uuid, DemoPlayer player,
        MediaDrmCallback drmCallback) throws UnsupportedDrmException {
      try {
        return new StreamingDrmSessionManager(uuid, player.getPlaybackLooper(), drmCallback, null,
            player.getMainHandler(), player);
      } catch (UnsupportedSchemeException e) {
        throw new UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME);
      } catch (Exception e) {
        throw new UnsupportedDrmException(UnsupportedDrmException.REASON_UNKNOWN, e);
      }
    }

  }

}
