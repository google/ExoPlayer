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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.CompositeSequenceableLoader;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Fake {@link MediaPeriod} that provides tracks from the given {@link TrackGroupArray}. Selecting a
 * track will give the player a {@link ChunkSampleStream}.
 */
public class FakeAdaptiveMediaPeriod extends FakeMediaPeriod
    implements SequenceableLoader.Callback<ChunkSampleStream<FakeChunkSource>> {

  private final Allocator allocator;
  private final FakeChunkSource.Factory chunkSourceFactory;
  @Nullable private final TransferListener transferListener;
  private final long durationUs;

  @MonotonicNonNull private Callback callback;
  private ChunkSampleStream<FakeChunkSource>[] sampleStreams;
  private SequenceableLoader sequenceableLoader;

  public FakeAdaptiveMediaPeriod(
      TrackGroupArray trackGroupArray,
      EventDispatcher eventDispatcher,
      Allocator allocator,
      FakeChunkSource.Factory chunkSourceFactory,
      long durationUs,
      @Nullable TransferListener transferListener) {
    super(trackGroupArray, eventDispatcher);
    this.allocator = allocator;
    this.chunkSourceFactory = chunkSourceFactory;
    this.transferListener = transferListener;
    this.durationUs = durationUs;
    this.sampleStreams = newSampleStreamArray(0);
    this.sequenceableLoader = new CompositeSequenceableLoader(new SequenceableLoader[0]);
  }

  @Override
  public void release() {
    for (ChunkSampleStream<FakeChunkSource> sampleStream : sampleStreams) {
      sampleStream.release();
    }
    super.release();
  }

  @Override
  public synchronized void prepare(Callback callback, long positionUs) {
    super.prepare(callback, positionUs);
    this.callback = callback;
  }

  @Override
  @SuppressWarnings("unchecked")
  public long selectTracks(
      @NullableType TrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    long returnPositionUs = super.selectTracks(selections, mayRetainStreamFlags, streams,
        streamResetFlags, positionUs);
    List<ChunkSampleStream<FakeChunkSource>> validStreams = new ArrayList<>();
    for (SampleStream stream : streams) {
      if (stream != null) {
        validStreams.add((ChunkSampleStream<FakeChunkSource>) stream);
      }
    }
    sampleStreams = newSampleStreamArray(validStreams.size());
    validStreams.toArray(sampleStreams);
    this.sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    return returnPositionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    super.discardBuffer(positionUs, toKeyframe);
    for (ChunkSampleStream<FakeChunkSource> sampleStream : sampleStreams) {
      sampleStream.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    super.reevaluateBuffer(positionUs);
    sequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public long getBufferedPositionUs() {
    super.getBufferedPositionUs();
    return sequenceableLoader.getBufferedPositionUs();
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
  public boolean isLoading() {
    return sequenceableLoader.isLoading();
  }

  @Override
  protected SampleStream createSampleStream(
      long positionUs, TrackSelection trackSelection, EventDispatcher eventDispatcher) {
    FakeChunkSource chunkSource =
        chunkSourceFactory.createChunkSource(trackSelection, durationUs, transferListener);
    return new ChunkSampleStream<>(
        MimeTypes.getTrackType(trackSelection.getSelectedFormat().sampleMimeType),
        /* embeddedTrackTypes= */ null,
        /* embeddedTrackFormats= */ null,
        chunkSource,
        /* callback= */ this,
        allocator,
        positionUs,
        /* drmSessionManager= */ DrmSessionManager.getDummyDrmSessionManager(),
        new DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount= */ 3),
        eventDispatcher);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void seekSampleStream(SampleStream sampleStream, long positionUs) {
    ((ChunkSampleStream<FakeChunkSource>) sampleStream).seekToUs(positionUs);
  }

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<FakeChunkSource> source) {
    Assertions.checkStateNotNull(callback).onContinueLoadingRequested(this);
  }

  // We won't assign the array to a variable that erases the generic type, and then write into it.
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ChunkSampleStream<FakeChunkSource>[] newSampleStreamArray(int length) {
    return new ChunkSampleStream[length];
  }
}
