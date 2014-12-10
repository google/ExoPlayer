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

import com.google.android.exoplayer.FrameworkSampleSource;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.demo.full.player.DemoPlayer.RendererBuilder;
import com.google.android.exoplayer.demo.full.player.DemoPlayer.RendererBuilderCallback;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;
import android.widget.TextView;

/**
 * A {@link RendererBuilder} for streams that can be read using
 * {@link android.media.MediaExtractor}.
 */
public class DefaultRendererBuilder implements RendererBuilder {

  private final Context context;
  private final Uri uri;
  private final TextView debugTextView;

  public DefaultRendererBuilder(Context context, Uri uri, TextView debugTextView) {
    this.context = context;
    this.uri = uri;
    this.debugTextView = debugTextView;
  }

  @Override
  public void buildRenderers(DemoPlayer player, RendererBuilderCallback callback) {
    // Build the video and audio renderers.
    FrameworkSampleSource sampleSource = new FrameworkSampleSource(context, uri, null, 2);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
        null, true, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, null, player.getMainHandler(),
        player, 50);
    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
        null, true, player.getMainHandler(), player);

    // Build the debug renderer.
    TrackRenderer debugRenderer = debugTextView != null
        ? new DebugTrackRenderer(debugTextView, videoRenderer)
        : null;

    // Invoke the callback.
    TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
    renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
    renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
    renderers[DemoPlayer.TYPE_DEBUG] = debugRenderer;
    callback.onRenderers(null, null, renderers);
  }

}
