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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.TrackGroupArray;
import com.google.android.exoplayer.TrackSelection;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.chunk.ChunkTrackStreamEventListener;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ManifestFetcher;

import android.net.Uri;
import android.os.Handler;
import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A {@link SampleSource} for HLS streams.
 */
public final class HlsSampleSource implements SampleSource {

  private final ManifestFetcher<HlsPlaylist> manifestFetcher;
  private final HlsTrackStreamWrapper[] trackStreamWrappers;
  private final IdentityHashMap<TrackStream, HlsTrackStreamWrapper> trackStreamSources;
  private final int[] selectedTrackCounts;

  private boolean prepared;
  private boolean seenFirstTrackSelection;
  private long durationUs;
  private TrackGroupArray trackGroups;
  private HlsTrackStreamWrapper[] enabledTrackStreamWrappers;

  public HlsSampleSource(Uri uri, DataSourceFactory dataSourceFactory,
      BandwidthMeter bandwidthMeter, Handler eventHandler,
      ChunkTrackStreamEventListener eventListener) {
    HlsPlaylistParser parser = new HlsPlaylistParser();
    DataSource manifestDataSource = dataSourceFactory.createDataSource();
    manifestFetcher = new ManifestFetcher<>(uri, manifestDataSource, parser);

    LoadControl loadControl = new DefaultLoadControl(
        new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE));
    PtsTimestampAdjusterProvider timestampAdjusterProvider = new PtsTimestampAdjusterProvider();

    DataSource defaultDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource defaultChunkSource = new HlsChunkSource(manifestFetcher, C.TRACK_TYPE_DEFAULT,
        defaultDataSource, timestampAdjusterProvider,
        new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter));
    HlsTrackStreamWrapper defaultTrackStreamWrapper = new HlsTrackStreamWrapper(defaultChunkSource,
        loadControl, C.DEFAULT_MUXED_BUFFER_SIZE, eventHandler, eventListener, C.TRACK_TYPE_VIDEO);

    DataSource audioDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource audioChunkSource = new HlsChunkSource(manifestFetcher, C.TRACK_TYPE_AUDIO,
        audioDataSource, timestampAdjusterProvider, null);
    HlsTrackStreamWrapper audioTrackStreamWrapper = new HlsTrackStreamWrapper(audioChunkSource,
        loadControl, C.DEFAULT_AUDIO_BUFFER_SIZE, eventHandler, eventListener, C.TRACK_TYPE_AUDIO);

    DataSource subtitleDataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource subtitleChunkSource = new HlsChunkSource(manifestFetcher, C.TRACK_TYPE_TEXT,
        subtitleDataSource, timestampAdjusterProvider, null);
    HlsTrackStreamWrapper subtitleTrackStreamWrapper = new HlsTrackStreamWrapper(
        subtitleChunkSource, loadControl, C.DEFAULT_TEXT_BUFFER_SIZE, eventHandler, eventListener,
        C.TRACK_TYPE_TEXT);

    trackStreamWrappers = new HlsTrackStreamWrapper[] {defaultTrackStreamWrapper,
        audioTrackStreamWrapper, subtitleTrackStreamWrapper};
    selectedTrackCounts = new int[trackStreamWrappers.length];
    trackStreamSources = new IdentityHashMap<>();
  }

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (prepared) {
      return true;
    }
    boolean trackStreamWrappersPrepared = true;
    for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
      trackStreamWrappersPrepared &= trackStreamWrapper.prepare(positionUs);
    }
    if (!trackStreamWrappersPrepared) {
      return false;
    }
    durationUs = 0;
    int totalTrackGroupCount = 0;
    for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
      totalTrackGroupCount += trackStreamWrapper.getTrackGroups().length;
      if (durationUs != C.UNSET_TIME_US) {
        long wrapperDurationUs = trackStreamWrapper.getDurationUs();
        durationUs = wrapperDurationUs == C.UNSET_TIME_US
            ? C.UNSET_TIME_US : Math.max(durationUs, wrapperDurationUs);
      }
    }
    TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
    int trackGroupIndex = 0;
    for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
      int wrapperTrackGroupCount = trackStreamWrapper.getTrackGroups().length;
      for (int j = 0; j < wrapperTrackGroupCount; j++) {
        trackGroupArray[trackGroupIndex++] = trackStreamWrapper.getTrackGroups().get(j);
      }
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
    prepared = true;
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    Assertions.checkState(prepared);
    TrackStream[] newStreams = new TrackStream[newSelections.size()];
    // Select tracks for each wrapper.
    int enabledTrackStreamWrapperCount = 0;
    for (int i = 0; i < trackStreamWrappers.length; i++) {
      selectedTrackCounts[i] += selectTracks(trackStreamWrappers[i], oldStreams, newSelections,
          positionUs, newStreams);
      if (selectedTrackCounts[i] > 0) {
        enabledTrackStreamWrapperCount++;
      }
    }
    // Update the enabled wrappers.
    enabledTrackStreamWrappers = new HlsTrackStreamWrapper[enabledTrackStreamWrapperCount];
    enabledTrackStreamWrapperCount = 0;
    for (int i = 0; i < trackStreamWrappers.length; i++) {
      if (selectedTrackCounts[i] > 0) {
        enabledTrackStreamWrappers[enabledTrackStreamWrapperCount++] = trackStreamWrappers[i];
      }
    }
    seenFirstTrackSelection = true;
    return newStreams;
  }

  @Override
  public void continueBuffering(long positionUs) {
    for (HlsTrackStreamWrapper trackStreamWrapper : enabledTrackStreamWrappers) {
      trackStreamWrapper.continueBuffering(positionUs);
    }
  }

  @Override
  public long readReset() {
    long resetPositionUs = C.UNSET_TIME_US;
    for (HlsTrackStreamWrapper trackStreamWrapper : enabledTrackStreamWrappers) {
      long childResetPositionUs = trackStreamWrapper.readReset();
      if (resetPositionUs == C.UNSET_TIME_US) {
        resetPositionUs = childResetPositionUs;
      } else if (childResetPositionUs != C.UNSET_TIME_US) {
        resetPositionUs = Math.min(resetPositionUs, childResetPositionUs);
      }
    }
    return resetPositionUs;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = durationUs != C.UNSET_TIME_US ? durationUs : Long.MAX_VALUE;
    for (HlsTrackStreamWrapper trackStreamWrapper : enabledTrackStreamWrappers) {
      long rendererBufferedPositionUs = trackStreamWrapper.getBufferedPositionUs();
      if (rendererBufferedPositionUs == C.UNSET_TIME_US) {
        return C.UNSET_TIME_US;
      } else if (rendererBufferedPositionUs == C.END_OF_SOURCE_US) {
        // This wrapper is fully buffered.
      } else {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.UNSET_TIME_US : bufferedPositionUs;
  }

  @Override
  public void seekToUs(long positionUs) {
    for (HlsTrackStreamWrapper trackStreamWrapper : enabledTrackStreamWrappers) {
      trackStreamWrapper.seekToUs(positionUs);
    }
  }

  @Override
  public void release() {
    manifestFetcher.release();
    for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
      trackStreamWrapper.release();
    }
  }

  // Internal methods.

  private int selectTracks(HlsTrackStreamWrapper trackStreamWrapper,
      List<TrackStream> allOldStreams, List<TrackSelection> allNewSelections, long positionUs,
      TrackStream[] allNewStreams) {
    // Get the subset of the old streams for the source.
    ArrayList<TrackStream> oldStreams = new ArrayList<>();
    for (int i = 0; i < allOldStreams.size(); i++) {
      TrackStream stream = allOldStreams.get(i);
      if (trackStreamSources.get(stream) == trackStreamWrapper) {
        trackStreamSources.remove(stream);
        oldStreams.add(stream);
      }
    }
    // Get the subset of the new selections for the wrapper.
    ArrayList<TrackSelection> newSelections = new ArrayList<>();
    int[] newSelectionOriginalIndices = new int[allNewSelections.size()];
    for (int i = 0; i < allNewSelections.size(); i++) {
      TrackSelection selection = allNewSelections.get(i);
      Pair<HlsTrackStreamWrapper, Integer> sourceAndGroup = getSourceAndGroup(selection.group);
      if (sourceAndGroup.first == trackStreamWrapper) {
        newSelectionOriginalIndices[newSelections.size()] = i;
        newSelections.add(new TrackSelection(sourceAndGroup.second, selection.getTracks()));
      }
    }
    // Do nothing if nothing has changed, except during the first selection.
    if (seenFirstTrackSelection && oldStreams.isEmpty() && newSelections.isEmpty()) {
      return 0;
    }
    // Perform the selection.
    TrackStream[] newStreams = trackStreamWrapper.selectTracks(oldStreams, newSelections,
        positionUs);
    for (int j = 0; j < newStreams.length; j++) {
      allNewStreams[newSelectionOriginalIndices[j]] = newStreams[j];
      trackStreamSources.put(newStreams[j], trackStreamWrapper);
    }
    return newSelections.size() - oldStreams.size();
  }

  private Pair<HlsTrackStreamWrapper, Integer> getSourceAndGroup(int group) {
    int totalTrackGroupCount = 0;
    for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
      int sourceTrackGroupCount = trackStreamWrapper.getTrackGroups().length;
      if (group < totalTrackGroupCount + sourceTrackGroupCount) {
        return Pair.create(trackStreamWrapper, group - totalTrackGroupCount);
      }
      totalTrackGroupCount += sourceTrackGroupCount;
    }
    throw new IndexOutOfBoundsException();
  }

}
