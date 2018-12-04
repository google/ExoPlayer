/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.dash.offline;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.offline.TrackKey;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A {@link DownloadHelper} for DASH streams. */
public final class DashDownloadHelper extends DownloadHelper<DashManifest> {

  private final DataSource.Factory manifestDataSourceFactory;

  public DashDownloadHelper(Uri uri, DataSource.Factory manifestDataSourceFactory) {
    super(DownloadAction.TYPE_DASH, uri, /* cacheKey= */ null);
    this.manifestDataSourceFactory = manifestDataSourceFactory;
  }

  @Override
  protected DashManifest loadManifest(Uri uri) throws IOException {
    DataSource dataSource = manifestDataSourceFactory.createDataSource();
    return ParsingLoadable.load(dataSource, new DashManifestParser(), uri, C.DATA_TYPE_MANIFEST);
  }

  @Override
  public TrackGroupArray[] getTrackGroupArrays(DashManifest manifest) {
    int periodCount = manifest.getPeriodCount();
    TrackGroupArray[] trackGroupArrays = new TrackGroupArray[periodCount];
    for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
      List<AdaptationSet> adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
      TrackGroup[] trackGroups = new TrackGroup[adaptationSets.size()];
      for (int i = 0; i < trackGroups.length; i++) {
        List<Representation> representations = adaptationSets.get(i).representations;
        Format[] formats = new Format[representations.size()];
        int representationsCount = representations.size();
        for (int j = 0; j < representationsCount; j++) {
          formats[j] = representations.get(j).format;
        }
        trackGroups[i] = new TrackGroup(formats);
      }
      trackGroupArrays[periodIndex] = new TrackGroupArray(trackGroups);
    }
    return trackGroupArrays;
  }

  @Override
  protected List<StreamKey> toStreamKeys(List<TrackKey> trackKeys) {
    List<StreamKey> streamKeys = new ArrayList<>(trackKeys.size());
    for (int i = 0; i < trackKeys.size(); i++) {
      TrackKey trackKey = trackKeys.get(i);
      streamKeys.add(new StreamKey(trackKey.periodIndex, trackKey.groupIndex, trackKey.trackIndex));
    }
    return streamKeys;
  }
}
