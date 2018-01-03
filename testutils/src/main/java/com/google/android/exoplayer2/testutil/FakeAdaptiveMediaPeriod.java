/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import com.google.android.exoplayer2.source.CompositeSequenceableLoader;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake {@link MediaPeriod} that provides tracks from the given {@link TrackGroupArray}. Selecting a
 * track will give the player a {@link ChunkSampleStream<FakeChunkSource>}.
 */
public class FakeAdaptiveMediaPeriod extends FakeMediaPeriod
    implements SequenceableLoader.Callback<ChunkSampleStream<FakeChunkSource>> {

  private final EventDispatcher eventDispatcher;
  private final Allocator allocator;
  private final FakeChunkSource.Factory chunkSourceFactory;
  private final long durationUs;

  private Callback callback;
  private ChunkSampleStream<FakeChunkSource>[] sampleStreams;
  private SequenceableLoader sequenceableLoader;

  public FakeAdaptiveMediaPeriod(TrackGroupArray trackGroupArray, EventDispatcher eventDispatcher,
      Allocator allocator, FakeChunkSource.Factory chunkSourceFactory, long durationUs) {
    super(trackGroupArray);
    this.eventDispatcher = eventDispatcher;
    this.allocator = allocator;
    this.chunkSourceFactory = chunkSourceFactory;
    this.durationUs = durationUs;
  }

  @Override
  public void release() {
    super.release();
    for (ChunkSampleStream<FakeChunkSource> sampleStream : sampleStreams) {
      sampleStream.release();
    }
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    super.prepare(callback, positionUs);
    this.callback = callback;
  }

  @Override
  @SuppressWarnings("unchecked")
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    long returnPositionUs = super.selectTracks(selections, mayRetainStreamFlags, streams,
        streamResetFlags, positionUs);
    List<ChunkSampleStream<FakeChunkSource>> validStreams = new ArrayList<>();
    for (SampleStream stream : streams) {
      if (stream != null) {
        validStreams.add((ChunkSampleStream<FakeChunkSource>) stream);
      }
    }
    this.sampleStreams = validStreams.toArray(new ChunkSampleStream[validStreams.size()]);
    this.sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    return returnPositionUs;
  }

  @Override
  public long getBufferedPositionUs() {
    super.getBufferedPositionUs();
    return sequenceableLoader.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    for (ChunkSampleStream<FakeChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return super.seekToUs(positionUs);
  }

  @Override
  public long getNextLoadPositionUs() {
    super.getNextLoadPositionUs();
    return sequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public boolean continueLoading(long positionUs) {
    super.continueLoading(positionUs);
    return sequenceableLoader.continueLoading(positionUs);
  }

  @Override
  protected SampleStream createSampleStream(TrackSelection trackSelection) {
    FakeChunkSource chunkSource = chunkSourceFactory.createChunkSource(trackSelection, durationUs);
    return new ChunkSampleStream<>(
        MimeTypes.getTrackType(trackSelection.getSelectedFormat().sampleMimeType), null,
        chunkSource, this, allocator, 0, 3, eventDispatcher);
  }

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<FakeChunkSource> source) {
    callback.onContinueLoadingRequested(this);
  }

}
