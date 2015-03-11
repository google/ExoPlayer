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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MultiTrackSource;
import com.google.android.exoplayer.ExoPlayer.ExoPlayerComponent;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;
import java.util.List;

/**
 * A {@link HlsChunkSource} providing the ability to switch between multiple other {@link HlsChunkSource}
 * instances.
 */
public class MultiTrackHlsChunkSource implements HlsChunkSource, MultiTrackSource, ExoPlayerComponent {

  /**
   * A message to indicate a source selection. Source selection can only be performed when the
   * source is disabled.
   */
  public static final int MSG_SELECT_TRACK = 1;

  private final HlsChunkSource[] allSources;

  private HlsChunkSource selectedSource;
  private boolean enabled;

  public MultiTrackHlsChunkSource(HlsChunkSource... sources) {
    this.allSources = sources;
    this.selectedSource = sources[0];
  }

  public MultiTrackHlsChunkSource(List<HlsChunkSource> sources) {
    this(toHlsChunkSourceArray(sources));
  }

  /**
   * Gets the number of tracks that this source can switch between. May be called safely from any
   * thread.
   *
   * @return The number of tracks.
   */
  @Override
  public int getTrackCount() {
    return allSources.length;
  }

  @Override
  public long getDurationUs() {
    return selectedSource.getDurationUs();
  }

  @Override
  public HlsChunk getChunkOperation(TsChunk previousTsChunk, long seekPositionUs,
      long playbackPositionUs) {
    return selectedSource.getChunkOperation(previousTsChunk, seekPositionUs, playbackPositionUs);
  }

  @Override
  public void getMaxVideoDimensions(MediaFormat out) {
    selectedSource.getMaxVideoDimensions(out);
  }

  @Override
  public void handleMessage(int what, Object msg) throws ExoPlaybackException {
    Assertions.checkState(!enabled);
    if (what == MSG_SELECT_TRACK) {
      selectedSource = allSources[(Integer) msg];
    }
  }

  @Override
  public boolean onLoadError(HlsChunk chunk, IOException e) {
    return selectedSource.onLoadError(chunk, e);
  }

  private static HlsChunkSource[] toHlsChunkSourceArray(List<HlsChunkSource> sources) {
    HlsChunkSource[] chunkSourceArray = new HlsChunkSource[sources.size()];
    sources.toArray(chunkSourceArray);
    return chunkSourceArray;
  }

  @Override
  public void selectTrack(ExoPlayer player, int index) {
    player.sendMessage(this, MultiTrackHlsChunkSource.MSG_SELECT_TRACK,
                       index);
  }


}
