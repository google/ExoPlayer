/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls.offline;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.offline.TrackKey;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A {@link DownloadHelper} for HLS streams. */
public final class HlsDownloadHelper extends DownloadHelper<HlsPlaylist> {

  private final DataSource.Factory manifestDataSourceFactory;

  private int[] renditionGroups;

  public HlsDownloadHelper(Uri uri, DataSource.Factory manifestDataSourceFactory) {
    super(DownloadAction.TYPE_HLS, uri, /* cacheKey= */ null);
    this.manifestDataSourceFactory = manifestDataSourceFactory;
  }

  @Override
  protected HlsPlaylist loadManifest(Uri uri) throws IOException {
    DataSource dataSource = manifestDataSourceFactory.createDataSource();
    return ParsingLoadable.load(dataSource, new HlsPlaylistParser(), uri, C.DATA_TYPE_MANIFEST);
  }

  @Override
  protected TrackGroupArray[] getTrackGroupArrays(HlsPlaylist playlist) {
    Assertions.checkNotNull(playlist);
    if (playlist instanceof HlsMediaPlaylist) {
      renditionGroups = new int[0];
      return new TrackGroupArray[] {TrackGroupArray.EMPTY};
    }
    // TODO: Generate track groups as in playback. Reverse the mapping in getDownloadAction.
    HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) playlist;
    TrackGroup[] trackGroups = new TrackGroup[3];
    renditionGroups = new int[3];
    int trackGroupIndex = 0;
    if (!masterPlaylist.variants.isEmpty()) {
      renditionGroups[trackGroupIndex] = HlsMasterPlaylist.GROUP_INDEX_VARIANT;
      trackGroups[trackGroupIndex++] = new TrackGroup(toFormats(masterPlaylist.variants));
    }
    if (!masterPlaylist.audios.isEmpty()) {
      renditionGroups[trackGroupIndex] = HlsMasterPlaylist.GROUP_INDEX_AUDIO;
      trackGroups[trackGroupIndex++] = new TrackGroup(toFormats(masterPlaylist.audios));
    }
    if (!masterPlaylist.subtitles.isEmpty()) {
      renditionGroups[trackGroupIndex] = HlsMasterPlaylist.GROUP_INDEX_SUBTITLE;
      trackGroups[trackGroupIndex++] = new TrackGroup(toFormats(masterPlaylist.subtitles));
    }
    return new TrackGroupArray[] {new TrackGroupArray(Arrays.copyOf(trackGroups, trackGroupIndex))};
  }

  @Override
  protected List<StreamKey> toStreamKeys(List<TrackKey> trackKeys) {
    List<StreamKey> representationKeys = new ArrayList<>(trackKeys.size());
    for (int i = 0; i < trackKeys.size(); i++) {
      TrackKey trackKey = trackKeys.get(i);
      representationKeys.add(
          new StreamKey(renditionGroups[trackKey.groupIndex], trackKey.trackIndex));
    }
    return representationKeys;
  }

  private static Format[] toFormats(List<HlsMasterPlaylist.HlsUrl> hlsUrls) {
    Format[] formats = new Format[hlsUrls.size()];
    for (int i = 0; i < hlsUrls.size(); i++) {
      formats[i] = hlsUrls.get(i).format;
    }
    return formats;
  }
}
