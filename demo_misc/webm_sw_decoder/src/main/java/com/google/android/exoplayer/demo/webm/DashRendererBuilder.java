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
package com.google.android.exoplayer.demo.webm;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.AdaptiveEvaluator;
import com.google.android.exoplayer.chunk.MultiTrackChunkSource;
import com.google.android.exoplayer.dash.DashChunkSource;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescriptionParser;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.ext.opus.LibopusAudioTrackRenderer;
import com.google.android.exoplayer.ext.vp9.LibvpxVideoTrackRenderer;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;
import com.google.android.exoplayer.util.MimeTypes;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Helper class that parses the manifest and builds the track renderers.
 */
public class DashRendererBuilder implements ManifestCallback<MediaPresentationDescription> {

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int VIDEO_BUFFER_SEGMENTS = 200;
  private static final int AUDIO_BUFFER_SEGMENTS = 60;

  private final String manifestUrl;
  private final String userAgent;
  private final VideoPlayer player;

  public DashRendererBuilder(String manifestUrl, String userAgent, VideoPlayer player) {
    this.manifestUrl = manifestUrl;
    this.userAgent = userAgent;
    this.player = player;
  }

  public void build() {
    MediaPresentationDescriptionParser parser = new MediaPresentationDescriptionParser();
    ManifestFetcher<MediaPresentationDescription> manifestFetcher =
        new ManifestFetcher<>(manifestUrl, new DefaultHttpDataSource(userAgent, null), parser);
    manifestFetcher.singleLoad(player.getMainHandler().getLooper(), this);
  }

  @Override
  public void onSingleManifestError(IOException e) {
    // TODO: do something meaningful here.
    e.printStackTrace();
  }

  @Override
  public void onSingleManifest(MediaPresentationDescription manifest) {
    LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(null, null);

    // Obtain Representations for playback.
    ArrayList<Representation> audioRepresentationsList = new ArrayList<>();
    ArrayList<Representation> videoRepresentationsList = new ArrayList<>();
    Period period = manifest.periods.get(0);
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      int adaptationSetType = adaptationSet.type;
      for (int j = 0; j < adaptationSet.representations.size(); j++) {
        Representation representation = adaptationSet.representations.get(j);
        if (adaptationSetType == AdaptationSet.TYPE_AUDIO) {
          audioRepresentationsList.add(representation);
        } else if (adaptationSetType == AdaptationSet.TYPE_VIDEO) {
          videoRepresentationsList.add(representation);
        }
      }
    }
    Representation[] videoRepresentations = new Representation[videoRepresentationsList.size()];
    videoRepresentationsList.toArray(videoRepresentations);

    // Build the video renderer.
    LibvpxVideoTrackRenderer videoRenderer = null;
    if (!videoRepresentationsList.isEmpty()) {
      DataSource videoDataSource = new DefaultUriDataSource(player, bandwidthMeter, userAgent);
      ChunkSource videoChunkSource;
      String mimeType = videoRepresentations[0].format.mimeType;
      if (mimeType.equals(MimeTypes.VIDEO_WEBM)) {
        videoChunkSource = new DashChunkSource(videoDataSource,
            new AdaptiveEvaluator(bandwidthMeter), videoRepresentations);
      } else {
        throw new IllegalStateException("Unexpected mime type: " + mimeType);
      }
      ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource, loadControl,
          VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE);
      videoRenderer = new LibvpxVideoTrackRenderer(videoSampleSource,
          true, player.getMainHandler(), player, 50);
    }

    // Build the audio renderer.
    MultiTrackChunkSource audioChunkSource = null;
    TrackRenderer audioRenderer = null;
    if (!audioRepresentationsList.isEmpty()) {
      DataSource audioDataSource = new DefaultUriDataSource(player, bandwidthMeter, userAgent);
      ChunkSource[] audioChunkSources = new ChunkSource[audioRepresentationsList.size()];
      FormatEvaluator audioEvaluator = new FormatEvaluator.FixedEvaluator();
      for (int i = 0; i < audioRepresentationsList.size(); i++) {
        Representation representation = audioRepresentationsList.get(i);
        audioChunkSources[i] = new DashChunkSource(audioDataSource,
            audioEvaluator, representation);
      }
      audioChunkSource = new MultiTrackChunkSource(audioChunkSources);
      SampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource, loadControl,
          AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE);
      if (manifestUrl.contains("opus")) { // TODO: Need a better logic here.
        audioRenderer = new LibopusAudioTrackRenderer(audioSampleSource);
      } else {
        audioRenderer = new MediaCodecAudioTrackRenderer(audioSampleSource);
      }
    }

    TrackRenderer[] renderers = new TrackRenderer[(audioRenderer == null) ? 1 : 2];
    renderers[0] = videoRenderer;
    if (audioRenderer != null) {
      renderers[1] = audioRenderer;
    }
    player.onRenderersBuilt(renderers);
  }

}
