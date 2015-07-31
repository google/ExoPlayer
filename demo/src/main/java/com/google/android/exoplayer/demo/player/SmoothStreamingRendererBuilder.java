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
import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
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

import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;

import java.io.IOException;
import java.util.Arrays;

/**
 * A {@link RendererBuilder} for SmoothStreaming.
 */
public class SmoothStreamingRendererBuilder implements RendererBuilder {

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int VIDEO_BUFFER_SEGMENTS = 200;
  private static final int AUDIO_BUFFER_SEGMENTS = 60;
  private static final int TEXT_BUFFER_SEGMENTS = 2;
  private static final int LIVE_EDGE_LATENCY_MS = 30000;

  private final Context context;
  private final String userAgent;
  private final String url;
  private final MediaDrmCallback drmCallback;

  private AsyncRendererBuilder currentAsyncBuilder;

  public SmoothStreamingRendererBuilder(Context context, String userAgent, String url,
      MediaDrmCallback drmCallback) {
    this.context = context;
    this.userAgent = userAgent;
    this.url = Util.toLowerInvariant(url).endsWith("/manifest") ? url : url + "/Manifest";
    this.drmCallback = drmCallback;
  }

  @Override
  public void buildRenderers(DemoPlayer player) {
    currentAsyncBuilder = new AsyncRendererBuilder(context, userAgent, url, drmCallback, player);
    currentAsyncBuilder.init();
  }

  @Override
  public void cancel() {
    if (currentAsyncBuilder != null) {
      currentAsyncBuilder.cancel();
      currentAsyncBuilder = null;
    }
  }

  private static final class AsyncRendererBuilder
      implements ManifestFetcher.ManifestCallback<SmoothStreamingManifest> {

    private final Context context;
    private final String userAgent;
    private final MediaDrmCallback drmCallback;
    private final DemoPlayer player;
    private final ManifestFetcher<SmoothStreamingManifest> manifestFetcher;

    private boolean canceled;

    public AsyncRendererBuilder(Context context, String userAgent, String url,
        MediaDrmCallback drmCallback, DemoPlayer player) {
      this.context = context;
      this.userAgent = userAgent;
      this.drmCallback = drmCallback;
      this.player = player;
      SmoothStreamingManifestParser parser = new SmoothStreamingManifestParser();
      manifestFetcher = new ManifestFetcher<>(url, new DefaultHttpDataSource(userAgent, null),
          parser);
    }

    public void init() {
      manifestFetcher.singleLoad(player.getMainHandler().getLooper(), this);
    }

    public void cancel() {
      canceled = true;
    }

    @Override
    public void onSingleManifestError(IOException exception) {
      if (canceled) {
        return;
      }

      player.onRenderersError(exception);
    }

    @Override
    public void onSingleManifest(SmoothStreamingManifest manifest) {
      if (canceled) {
        return;
      }

      Handler mainHandler = player.getMainHandler();
      LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
      DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler, player);

      // Check drm support if necessary.
      DrmSessionManager drmSessionManager = null;
      if (manifest.protectionElement != null) {
        if (Util.SDK_INT < 18) {
          player.onRenderersError(
              new UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME));
          return;
        }
        try {
          drmSessionManager = new StreamingDrmSessionManager(manifest.protectionElement.uuid,
              player.getPlaybackLooper(), drmCallback, null, player.getMainHandler(), player);
        } catch (UnsupportedDrmException e) {
          player.onRenderersError(e);
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
          player.onRenderersError(e);
          return;
        }
      }

      // Build the video renderer.
      final MediaCodecVideoTrackRenderer videoRenderer;
      if (videoTrackIndices == null || videoTrackIndices.length == 0) {
        videoRenderer = null;
      } else {
        DataSource videoDataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
        ChunkSource videoChunkSource = new SmoothStreamingChunkSource(manifestFetcher,
            videoStreamElementIndex, videoTrackIndices, videoDataSource,
            new AdaptiveEvaluator(bandwidthMeter), LIVE_EDGE_LATENCY_MS);
        ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
            VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player,
            DemoPlayer.TYPE_VIDEO);
        videoRenderer = new MediaCodecVideoTrackRenderer(videoSampleSource, drmSessionManager, true,
            MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, null, mainHandler, player, 50);
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
            AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player,
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
            textChunkSources[textStreamElementCount] = new SmoothStreamingChunkSource(
                manifestFetcher, i, new int[] {0}, ttmlDataSource, ttmlFormatEvaluator,
                LIVE_EDGE_LATENCY_MS);
            textStreamElementCount++;
          }
        }
        textChunkSource = new MultiTrackChunkSource(textChunkSources);
        ChunkSampleSource ttmlSampleSource = new ChunkSampleSource(textChunkSource, loadControl,
            TEXT_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player,
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
      player.onRenderers(trackNames, multiTrackChunkSources, renderers, bandwidthMeter);
    }

  }

}
