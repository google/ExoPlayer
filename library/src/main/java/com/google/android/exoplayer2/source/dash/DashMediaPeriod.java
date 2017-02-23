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
import com.google.android.exoplayer2.source.EmptySampleStream;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SchemeValuePair;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A DASH {@link MediaPeriod}.
 */
/* package */ final class DashMediaPeriod implements MediaPeriod,
    SequenceableLoader.Callback<ChunkSampleStream<DashChunkSource>> {

  /* package */ final int id;
  private final DashChunkSource.Factory chunkSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final long elapsedRealtimeOffset;
  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final Allocator allocator;
  private final TrackGroupArray trackGroups;

  private Callback callback;
  private ChunkSampleStream<DashChunkSource>[] sampleStreams;
  private CompositeSequenceableLoader sequenceableLoader;
  private DashManifest manifest;
  private int periodIndex;
  private List<AdaptationSet> adaptationSets;

  public DashMediaPeriod(int id, DashManifest manifest, int periodIndex,
      DashChunkSource.Factory chunkSourceFactory,  int minLoadableRetryCount,
      EventDispatcher eventDispatcher, long elapsedRealtimeOffset,
      LoaderErrorThrower manifestLoaderErrorThrower, Allocator allocator) {
    this.id = id;
    this.manifest = manifest;
    this.periodIndex = periodIndex;
    this.chunkSourceFactory = chunkSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    this.elapsedRealtimeOffset = elapsedRealtimeOffset;
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.allocator = allocator;
    sampleStreams = newSampleStreamArray(0);
    sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    trackGroups = buildTrackGroups(adaptationSets);
  }

  public void updateManifest(DashManifest manifest, int periodIndex) {
    this.manifest = manifest;
    this.periodIndex = periodIndex;
    adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    if (sampleStreams != null) {
      for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
        sampleStream.getChunkSource().updateManifest(manifest, periodIndex);
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  public void release() {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.release();
    }
  }

  @Override
  public void prepare(Callback callback) {
    this.callback = callback;
    callback.onPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    manifestLoaderErrorThrower.maybeThrowError();
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    ArrayList<ChunkSampleStream<DashChunkSource>> sampleStreamsList = new ArrayList<>();
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] instanceof ChunkSampleStream) {
        @SuppressWarnings("unchecked")
        ChunkSampleStream<DashChunkSource> stream = (ChunkSampleStream<DashChunkSource>) streams[i];
        if (selections[i] == null || !mayRetainStreamFlags[i]) {
          stream.release();
          streams[i] = null;
        } else {
          sampleStreamsList.add(stream);
        }
      } else if (streams[i] instanceof EmptySampleStream && selections[i] == null) {
        // TODO: Release streams for cea-608 and emsg tracks.
        streams[i] = null;
      }
      if (streams[i] == null && selections[i] != null) {
        int adaptationSetIndex = trackGroups.indexOf(selections[i].getTrackGroup());
        if (adaptationSetIndex < adaptationSets.size()) {
          ChunkSampleStream<DashChunkSource> stream = buildSampleStream(adaptationSetIndex,
              selections[i], positionUs);
          sampleStreamsList.add(stream);
          streams[i] = stream;
        } else {
          // TODO: Output streams for cea-608 and emsg tracks.
          streams[i] = new EmptySampleStream();
        }
        streamResetFlags[i] = true;
      }
    }
    sampleStreams = newSampleStreamArray(sampleStreamsList.size());
    sampleStreamsList.toArray(sampleStreams);
    sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    return positionUs;
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
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      long rendererBufferedPositionUs = sampleStream.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.TIME_END_OF_SOURCE) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : bufferedPositionUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return positionUs;
  }

  // SequenceableLoader.Callback implementation.

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<DashChunkSource> sampleStream) {
    callback.onContinueLoadingRequested(this);
  }

  // Internal methods.

  private static TrackGroupArray buildTrackGroups(List<AdaptationSet> adaptationSets) {
    int adaptationSetCount = adaptationSets.size();
    int eventMessageTrackCount = getEventMessageTrackCount(adaptationSets);
    int cea608TrackCount = getCea608TrackCount(adaptationSets);
    TrackGroup[] trackGroupArray = new TrackGroup[adaptationSetCount + eventMessageTrackCount
        + cea608TrackCount];
    int eventMessageTrackIndex = 0;
    int cea608TrackIndex = 0;
    for (int i = 0; i < adaptationSetCount; i++) {
      AdaptationSet adaptationSet = adaptationSets.get(i);
      List<Representation> representations = adaptationSet.representations;
      Format[] formats = new Format[representations.size()];
      for (int j = 0; j < formats.length; j++) {
        formats[j] = representations.get(j).format;
      }
      trackGroupArray[i] = new TrackGroup(formats);
      if (hasEventMessageTrack(adaptationSet)) {
        Format format = Format.createSampleFormat(adaptationSet.id + ":emsg",
            MimeTypes.APPLICATION_EMSG, null, Format.NO_VALUE, null);
        trackGroupArray[adaptationSetCount + eventMessageTrackIndex++] = new TrackGroup(format);
      }
      if (hasCea608Track(adaptationSet)) {
        Format format = Format.createTextSampleFormat(adaptationSet.id + ":cea608",
            MimeTypes.APPLICATION_CEA608, null, Format.NO_VALUE, 0, null, null);
        trackGroupArray[adaptationSetCount + eventMessageTrackCount + cea608TrackIndex++] =
            new TrackGroup(format);
      }
    }
    return new TrackGroupArray(trackGroupArray);
  }

  private ChunkSampleStream<DashChunkSource> buildSampleStream(int adaptationSetIndex,
      TrackSelection selection, long positionUs) {
    AdaptationSet adaptationSet = adaptationSets.get(adaptationSetIndex);
    boolean enableEventMessageTrack = hasEventMessageTrack(adaptationSet);
    boolean enableCea608Track = hasCea608Track(adaptationSet);
    DashChunkSource chunkSource = chunkSourceFactory.createDashChunkSource(
        manifestLoaderErrorThrower, manifest, periodIndex, adaptationSetIndex, selection,
        elapsedRealtimeOffset, enableEventMessageTrack, enableCea608Track);
    return new ChunkSampleStream<>(adaptationSet.type, chunkSource, this, allocator, positionUs,
        minLoadableRetryCount, eventDispatcher);
  }

  private static int getEventMessageTrackCount(List<AdaptationSet> adaptationSets) {
    int inbandEventStreamTrackCount = 0;
    for (int i = 0; i < adaptationSets.size(); i++) {
      if (hasEventMessageTrack(adaptationSets.get(i))) {
        inbandEventStreamTrackCount++;
      }
    }
    return inbandEventStreamTrackCount;
  }

  private static boolean hasEventMessageTrack(AdaptationSet adaptationSet) {
    List<Representation> representations = adaptationSet.representations;
    for (int i = 0; i < representations.size(); i++) {
      Representation representation = representations.get(i);
      if (!representation.inbandEventStreams.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static int getCea608TrackCount(List<AdaptationSet> adaptationSets) {
    int cea608TrackCount = 0;
    for (int i = 0; i < adaptationSets.size(); i++) {
      if (hasCea608Track(adaptationSets.get(i))) {
        cea608TrackCount++;
      }
    }
    return cea608TrackCount;
  }

  private static boolean hasCea608Track(AdaptationSet adaptationSet) {
    List<SchemeValuePair> descriptors = adaptationSet.accessibilityDescriptors;
    for (int i = 0; i < descriptors.size(); i++) {
      SchemeValuePair descriptor = descriptors.get(i);
      if ("urn:scte:dash:cc:cea-608:2015".equals(descriptor.schemeIdUri)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static ChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
    return new ChunkSampleStream[length];
  }

}
