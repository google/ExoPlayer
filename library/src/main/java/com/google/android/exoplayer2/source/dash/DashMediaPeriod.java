/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.source.dash;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.CompositeSequenceableLoader;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.chunk.FormatEvaluator;
import com.google.android.exoplayer2.source.dash.mpd.AdaptationSet;
import com.google.android.exoplayer2.source.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer2.source.dash.mpd.Period;
import com.google.android.exoplayer2.source.dash.mpd.Representation;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceFactory;
import com.google.android.exoplayer2.upstream.Loader;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A DASH {@link MediaPeriod}.
 */
/* package */ final class DashMediaPeriod implements MediaPeriod,
    SequenceableLoader.Callback<ChunkSampleStream<DashChunkSource>> {

  private final Loader loader;
  private final DataSourceFactory dataSourceFactory;
  private final BandwidthMeter bandwidthMeter;
  private final EventDispatcher eventDispatcher;

  private ChunkSampleStream<DashChunkSource>[] sampleStreams;
  private CompositeSequenceableLoader sequenceableLoader;
  private Callback callback;
  private Allocator allocator;
  private long durationUs;
  private TrackGroupArray trackGroups;
  private int[] trackGroupAdaptationSetIndices;
  private MediaPresentationDescription manifest;
  private int index;
  private int minLoadableRetryCount;
  private long elapsedRealtimeOffset;
  private Period period;

  public DashMediaPeriod(MediaPresentationDescription manifest, int index,
      DataSourceFactory dataSourceFactory, BandwidthMeter bandwidthMeter,
      int minLoadableRetryCount, EventDispatcher eventDispatcher, long elapsedRealtimeOffset,
      Loader loader) {
    this.manifest = manifest;
    this.index = index;
    this.dataSourceFactory = dataSourceFactory;
    this.bandwidthMeter = bandwidthMeter;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    this.elapsedRealtimeOffset = elapsedRealtimeOffset;
    this.loader = loader;
    period = manifest.getPeriod(index);
    durationUs = manifest.dynamic ? C.UNSET_TIME_US : manifest.getPeriodDuration(index) * 1000;
  }

  public void updateManifest(MediaPresentationDescription manifest, int index) {
    this.manifest = manifest;
    this.index = index;
    period = manifest.getPeriod(index);
    if (sampleStreams != null) {
      for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
        sampleStream.getChunkSource().updateManifest(manifest, index);
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  // MediaPeriod implementation.

  @Override
  public void preparePeriod(Callback callback, Allocator allocator, long positionUs) {
    this.callback = callback;
    this.allocator = allocator;
    sampleStreams = newSampleStreamArray(0);
    sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    buildTrackGroups();
    callback.onPeriodPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    loader.maybeThrowError();
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
  public SampleStream[] selectTracks(List<SampleStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    int newEnabledSourceCount = sampleStreams.length + newSelections.size() - oldStreams.size();
    ChunkSampleStream<DashChunkSource>[] newSampleStreams =
        newSampleStreamArray(newEnabledSourceCount);
    int newEnabledSourceIndex = 0;

    // Iterate over currently enabled streams, either releasing them or adding them to the new
    // list.
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      if (oldStreams.contains(sampleStream)) {
        sampleStream.release();
      } else {
        newSampleStreams[newEnabledSourceIndex++] = sampleStream;
      }
    }

    // Instantiate and return new streams.
    SampleStream[] streamsToReturn = new SampleStream[newSelections.size()];
    for (int i = 0; i < newSelections.size(); i++) {
      newSampleStreams[newEnabledSourceIndex] =
          buildSampleStream(newSelections.get(i), positionUs);
      streamsToReturn[i] = newSampleStreams[newEnabledSourceIndex];
      newEnabledSourceIndex++;
    }

    sampleStreams = newSampleStreams;
    sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    return streamsToReturn;
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return sequenceableLoader.continueLoading(positionUs);
  }

  @Override
  public long getNextLoadPositionUs() {
    return sequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    return C.UNSET_TIME_US;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      long rendererBufferedPositionUs = sampleStream.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.END_OF_SOURCE_US) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.END_OF_SOURCE_US : bufferedPositionUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return positionUs;
  }

  @Override
  public void releasePeriod() {
    if (sampleStreams != null) {
      for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
        sampleStream.release();
      }
      sampleStreams = null;
    }
    sequenceableLoader = null;
    callback = null;
    allocator = null;
    durationUs = 0;
    trackGroups = null;
    trackGroupAdaptationSetIndices = null;
  }

  // SequenceableLoader.Callback implementation.

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<DashChunkSource> sampleStream) {
    callback.onContinueLoadingRequested(this);
  }

  // Internal methods.

  private void buildTrackGroups() {
    int trackGroupCount = 0;
    trackGroupAdaptationSetIndices = new int[period.adaptationSets.size()];
    TrackGroup[] trackGroupArray = new TrackGroup[period.adaptationSets.size()];
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      int adaptationSetType = adaptationSet.type;
      List<Representation> representations = adaptationSet.representations;
      if (!representations.isEmpty() && (adaptationSetType == C.TRACK_TYPE_AUDIO
          || adaptationSetType == C.TRACK_TYPE_VIDEO || adaptationSetType == C.TRACK_TYPE_TEXT)) {
        Format[] formats = new Format[representations.size()];
        for (int j = 0; j < formats.length; j++) {
          formats[j] = representations.get(j).format;
        }
        trackGroupAdaptationSetIndices[trackGroupCount] = i;
        boolean adaptive = adaptationSetType == C.TRACK_TYPE_VIDEO;
        trackGroupArray[trackGroupCount++] = new TrackGroup(adaptive, formats);
      }
    }
    if (trackGroupCount < trackGroupArray.length) {
      trackGroupAdaptationSetIndices = Arrays.copyOf(trackGroupAdaptationSetIndices,
          trackGroupCount);
      trackGroupArray = Arrays.copyOf(trackGroupArray, trackGroupCount);
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
  }

  private ChunkSampleStream<DashChunkSource> buildSampleStream(TrackSelection selection,
      long positionUs) {
    int[] selectedTracks = selection.getTracks();
    FormatEvaluator adaptiveEvaluator = selectedTracks.length > 1
        ? new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter) : null;
    int adaptationSetIndex = trackGroupAdaptationSetIndices[selection.group];
    AdaptationSet adaptationSet = period.adaptationSets.get(
        adaptationSetIndex);
    int adaptationSetType = adaptationSet.type;
    DataSource dataSource =
        dataSourceFactory.createDataSource(bandwidthMeter);
    DashChunkSource chunkSource = new DashChunkSource(loader, manifest, adaptationSetIndex,
        trackGroups.get(selection.group), selectedTracks, dataSource, adaptiveEvaluator,
        elapsedRealtimeOffset, index);
    return new ChunkSampleStream<>(adaptationSetType, chunkSource, this, allocator, positionUs,
        minLoadableRetryCount, eventDispatcher);
  }

  @SuppressWarnings("unchecked")
  private static ChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
    return new ChunkSampleStream[length];
  }

}
