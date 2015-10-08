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
package com.google.android.exoplayer.smoothstreaming;

import com.google.android.exoplayer.chunk.VideoFormatSelectorUtil;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;

import android.content.Context;

import java.io.IOException;
import java.util.Arrays;

/**
 * A default {@link SmoothStreamingTrackSelector} implementation.
 */
// TODO: Add configuration options (e.g. ability to disable adaptive track output, disable format
// filtering etc).
public final class DefaultSmoothStreamingTrackSelector implements SmoothStreamingTrackSelector {

  private final Context context;
  private final int streamElementType;

  /**
   * @param context A context.
   * @param streamElementType The type of stream to select. One of {@link StreamElement#TYPE_AUDIO},
   *     {@link StreamElement#TYPE_VIDEO} and {@link StreamElement#TYPE_TEXT}.
   */
  public DefaultSmoothStreamingTrackSelector(Context context, int streamElementType) {
    this.context = context;
    this.streamElementType = streamElementType;
  }

  @Override
  public void selectTracks(SmoothStreamingManifest manifest, Output output) throws IOException {
    for (int i = 0; i < manifest.streamElements.length; i++) {
      if (manifest.streamElements[i].type == streamElementType) {
        if (streamElementType == StreamElement.TYPE_VIDEO) {
          int[] trackIndices = VideoFormatSelectorUtil.selectVideoFormatsForDefaultDisplay(
              context, Arrays.asList(manifest.streamElements[i].tracks), null, false);
          int trackCount = trackIndices.length;
          if (trackCount > 1) {
            output.adaptiveTrack(manifest, i, trackIndices);
          }
          for (int j = 0; j < trackCount; j++) {
            output.fixedTrack(manifest, i, trackIndices[j]);
          }
        } else {
          for (int j = 0; j < manifest.streamElements[i].tracks.length; j++) {
            output.fixedTrack(manifest, i, j);
          }
        }
      }
    }
  }

}
