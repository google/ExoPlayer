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
package com.google.android.exoplayer.dash;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.TrackGroupArray;
import com.google.android.exoplayer.TrackSelection;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.chunk.ChunkTrackStream;
import com.google.android.exoplayer.chunk.ChunkTrackStreamEventListener;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.AdaptiveEvaluator;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescriptionParser;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.dash.mpd.UtcTimingElement;
import com.google.android.exoplayer.dash.mpd.UtcTimingElementResolver;
import com.google.android.exoplayer.dash.mpd.UtcTimingElementResolver.UtcTimingCallback;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link SampleSource} for DASH media.
 */
public final class DashSampleSource implements SampleSource, UtcTimingCallback {

  private static final String TAG = "DashSampleSource";

  private final ManifestFetcher<MediaPresentationDescription> manifestFetcher;
  private final DataSourceFactory dataSourceFactory;
  private final BandwidthMeter bandwidthMeter;
  private final Handler eventHandler;
  private final ChunkTrackStreamEventListener eventListener;
  private final LoadControl loadControl;

  private boolean prepared;
  private boolean released;
  private long durationUs;
  private long elapsedRealtimeOffset;
  private MediaPresentationDescription manifest;
  private TrackGroupArray trackGroups;
  private int[] trackGroupAdaptationSetIndices;
  private boolean pendingReset;
  private long lastSeekPositionUs;

  private DashChunkSource[] chunkSources;
  private ChunkTrackStream[] trackStreams;

  public DashSampleSource(Uri uri, DataSourceFactory dataSourceFactory,
      BandwidthMeter bandwidthMeter, Handler eventHandler,
      ChunkTrackStreamEventListener eventListener) {
    this.dataSourceFactory = dataSourceFactory;
    this.bandwidthMeter = bandwidthMeter;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;

    loadControl = new DefaultLoadControl(new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE));
    chunkSources = new DashChunkSource[0];
    trackStreams = new ChunkTrackStream[0];

    MediaPresentationDescriptionParser parser = new MediaPresentationDescriptionParser();
    DataSource manifestDataSource = dataSourceFactory.createDataSource();
    manifestFetcher = new ManifestFetcher<>(uri, manifestDataSource, parser);
  }

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (prepared) {
      return true;
    }

    if (manifest == null) {
      manifest = manifestFetcher.getManifest();
      if (manifest == null) {
        manifestFetcher.maybeThrowError();
        manifestFetcher.requestRefresh();
        return false;
      }
      durationUs = manifest.dynamic ? C.UNSET_TIME_US : manifest.duration * 1000;
      buildTrackGroups(manifest);
      if (manifest.utcTiming != null) {
        UtcTimingElementResolver.resolveTimingElement(dataSourceFactory.createDataSource(),
            manifest.utcTiming, manifestFetcher.getManifestLoadCompleteTimestamp(), this);
      } else {
        prepared = true;
      }
    }

    return prepared;
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
    int newEnabledSourceCount = trackStreams.length + newSelections.size() - oldStreams.size();
    DashChunkSource[] newChunkSources = new DashChunkSource[newEnabledSourceCount];
    ChunkTrackStream[] newTrackStreams = new ChunkTrackStream[newEnabledSourceCount];
    int newEnabledSourceIndex = 0;

    // Iterate over currently enabled streams, either releasing them or adding them to the new list.
    for (int i = 0; i < trackStreams.length; i++) {
      ChunkTrackStream trackStream = trackStreams[i];
      if (oldStreams.contains(trackStream)) {
        chunkSources[i].release();
        trackStream.release();
      } else {
        newChunkSources[newEnabledSourceIndex] = chunkSources[i];
        newTrackStreams[newEnabledSourceIndex++] = trackStream;
      }
    }

    // Instantiate and return new streams.
    TrackStream[] streamsToReturn = new TrackStream[newSelections.size()];
    for (int i = 0; i < newSelections.size(); i++) {
      Pair<DashChunkSource, ChunkTrackStream> trackComponents =
          buildTrackStream(newSelections.get(i), positionUs);
      newChunkSources[newEnabledSourceIndex] = trackComponents.first;
      newTrackStreams[newEnabledSourceIndex++] = trackComponents.second;
      streamsToReturn[i] = trackComponents.second;
    }

    chunkSources = newChunkSources;
    trackStreams = newTrackStreams;
    return streamsToReturn;
  }

  @Override
  public void continueBuffering(long positionUs) {
    if (manifest.dynamic) {
      MediaPresentationDescription newManifest = manifestFetcher.getManifest();
      if (newManifest != manifest) {
        manifest = newManifest;
        for (DashChunkSource chunkSource : chunkSources) {
          chunkSource.updateManifest(newManifest);
        }
      }

      long minUpdatePeriod = manifest.minUpdatePeriod;
      if (minUpdatePeriod == 0) {
        // TODO: This is a temporary hack to avoid constantly refreshing the MPD in cases where
        // minUpdatePeriod is set to 0. In such cases we shouldn't refresh unless there is explicit
        // signaling in the stream, according to:
        // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service/
        minUpdatePeriod = 5000;
      }

      if (SystemClock.elapsedRealtime() > manifestFetcher.getManifestLoadStartTimestamp()
          + minUpdatePeriod) {
        manifestFetcher.requestRefresh();
      }
    }

    for (ChunkTrackStream trackStream : trackStreams) {
      trackStream.continueBuffering(positionUs);
    }
  }

  @Override
  public long readReset() {
    if (pendingReset) {
      pendingReset = false;
      for (ChunkTrackStream trackStream : trackStreams) {
        trackStream.setReadingEnabled(true);
      }
      return lastSeekPositionUs;
    }
    return C.UNSET_TIME_US;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (ChunkTrackStream trackStream : trackStreams) {
      long rendererBufferedPositionUs = trackStream.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.END_OF_SOURCE_US) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.END_OF_SOURCE_US : bufferedPositionUs;
  }

  @Override
  public void seekToUs(long positionUs) {
    lastSeekPositionUs = positionUs;
    pendingReset = true;
    for (ChunkTrackStream trackStream : trackStreams) {
      trackStream.setReadingEnabled(false);
      trackStream.seekToUs(positionUs);
    }
  }

  @Override
  public void release() {
    manifestFetcher.release();
    for (DashChunkSource chunkSource : chunkSources) {
      chunkSource.release();
    }
    for (ChunkTrackStream trackStream : trackStreams) {
      trackStream.release();
    }
    released = true;
  }

  // UtcTimingCallback implementation.

  @Override
  public void onTimestampResolved(UtcTimingElement utcTiming, long elapsedRealtimeOffset) {
    if (released) {
      return;
    }
    this.elapsedRealtimeOffset = elapsedRealtimeOffset;
    prepared = true;
  }

  @Override
  public void onTimestampError(UtcTimingElement utcTiming, IOException e) {
    if (released) {
      return;
    }
    Log.e(TAG, "Failed to resolve UtcTiming element [" + utcTiming + "]", e);
    // Be optimistic and continue in the hope that the device clock is correct.
    prepared = true;
  }

  // Internal methods.

  private void buildTrackGroups(MediaPresentationDescription manifest) {
    Period period = manifest.getPeriod(0);
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

  private Pair<DashChunkSource, ChunkTrackStream> buildTrackStream(TrackSelection selection,
      long positionUs) {
    int[] selectedTracks = selection.getTracks();
    FormatEvaluator adaptiveEvaluator = selectedTracks.length > 1
        ? new AdaptiveEvaluator(bandwidthMeter) : null;
    int adaptationSetIndex = trackGroupAdaptationSetIndices[selection.group];
    AdaptationSet adaptationSet = manifest.getPeriod(0).adaptationSets.get(
        adaptationSetIndex);
    int adaptationSetType = adaptationSet.type;
    int bufferSize = Util.getDefaultBufferSize(adaptationSetType);
    DataSource dataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    DashChunkSource chunkSource = new DashChunkSource(manifest, adaptationSetIndex,
        trackGroups.get(selection.group), selectedTracks, dataSource, adaptiveEvaluator,
        elapsedRealtimeOffset);
    ChunkTrackStream trackStream = new ChunkTrackStream(chunkSource, loadControl, bufferSize,
        positionUs, eventHandler, eventListener, adaptationSetType);
    return Pair.create(chunkSource, trackStream);
  }

}
