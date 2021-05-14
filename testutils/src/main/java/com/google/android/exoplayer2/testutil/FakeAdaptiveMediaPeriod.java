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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.CompositeSequenceableLoader;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Fake {@link MediaPeriod} that provides tracks from the given {@link TrackGroupArray}. Selecting a
 * track will give the player a {@link ChunkSampleStream}.
 */
public class FakeAdaptiveMediaPeriod
    implements MediaPeriod, SequenceableLoader.Callback<ChunkSampleStream<FakeChunkSource>> {

  private static final DataSpec FAKE_DATA_SPEC = new DataSpec(Uri.parse("http://fake.test"));

  private final TrackGroupArray trackGroupArray;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final long fakePreparationLoadTaskId;
  private final FakeChunkSource.Factory chunkSourceFactory;
  private final Allocator allocator;
  private final long durationUs;
  @Nullable private final TransferListener transferListener;
  private final List<ChunkSampleStream<FakeChunkSource>> sampleStreams;

  @Nullable private Callback callback;
  private boolean prepared;
  private SequenceableLoader sequenceableLoader;

  public FakeAdaptiveMediaPeriod(
      TrackGroupArray trackGroupArray,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      Allocator allocator,
      FakeChunkSource.Factory chunkSourceFactory,
      long durationUs,
      @Nullable TransferListener transferListener) {
    this.trackGroupArray = trackGroupArray;
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.chunkSourceFactory = chunkSourceFactory;
    this.allocator = allocator;
    this.durationUs = durationUs;
    this.transferListener = transferListener;
    sampleStreams = new ArrayList<>();
    sequenceableLoader = new CompositeSequenceableLoader(new SequenceableLoader[0]);
    fakePreparationLoadTaskId = LoadEventInfo.getNewId();
  }

  /** Releases the media period. */
  public void release() {
    prepared = false;
    for (ChunkSampleStream<FakeChunkSource> sampleStream : sampleStreams) {
      sampleStream.release();
    }
    sampleStreams.clear();
    sequenceableLoader = new CompositeSequenceableLoader(new SequenceableLoader[0]);
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    mediaSourceEventDispatcher.loadStarted(
        new LoadEventInfo(fakePreparationLoadTaskId, FAKE_DATA_SPEC, SystemClock.elapsedRealtime()),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        /* mediaEndTimeUs = */ C.TIME_UNSET);
    this.callback = callback;
    prepared = true;
    Util.castNonNull(this.callback).onPrepared(this);
    mediaSourceEventDispatcher.loadCompleted(
        new LoadEventInfo(
            fakePreparationLoadTaskId,
            FAKE_DATA_SPEC,
            FAKE_DATA_SPEC.uri,
            /* responseHeaders= */ ImmutableMap.of(),
            SystemClock.elapsedRealtime(),
            /* loadDurationMs= */ 0,
            /* bytesLoaded= */ 100),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        /* mediaEndTimeUs = */ C.TIME_UNSET);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    // Do nothing.
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    assertThat(prepared).isTrue();
    return trackGroupArray;
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // Casting sample streams created by this class.
  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    assertThat(prepared).isTrue();
    int rendererCount = selections.length;
    for (int i = 0; i < rendererCount; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        ((ChunkSampleStream<FakeChunkSource>) streams[i]).release();
        sampleStreams.remove(streams[i]);
        streams[i] = null;
      }
      if (streams[i] == null && selections[i] != null) {
        ExoTrackSelection selection = selections[i];
        assertThat(selection.length()).isAtLeast(1);
        TrackGroup trackGroup = selection.getTrackGroup();
        assertThat(trackGroupArray.indexOf(trackGroup)).isNotEqualTo(C.INDEX_UNSET);
        int indexInTrackGroup = selection.getIndexInTrackGroup(selection.getSelectedIndex());
        assertThat(indexInTrackGroup).isAtLeast(0);
        assertThat(indexInTrackGroup).isLessThan(trackGroup.length);
        FakeChunkSource chunkSource =
            chunkSourceFactory.createChunkSource(selection, durationUs, transferListener);
        ChunkSampleStream<FakeChunkSource> sampleStream =
            new ChunkSampleStream<>(
                MimeTypes.getTrackType(selection.getSelectedFormat().sampleMimeType),
                /* embeddedTrackTypes= */ null,
                /* embeddedTrackFormats= */ null,
                chunkSource,
                /* callback= */ this,
                allocator,
                positionUs,
                DrmSessionManager.DRM_UNSUPPORTED,
                new DrmSessionEventListener.EventDispatcher(),
                new DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount= */ 3),
                mediaSourceEventDispatcher);
        streams[i] = sampleStream;
        sampleStreams.add(sampleStream);
        streamResetFlags[i] = true;
      }
    }
    sequenceableLoader =
        new CompositeSequenceableLoader(sampleStreams.toArray(new ChunkSampleStream[0]));
    return seekToUs(positionUs);
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (ChunkSampleStream<FakeChunkSource> sampleStream : sampleStreams) {
      sampleStream.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    sequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public long readDiscontinuity() {
    assertThat(prepared).isTrue();
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    assertThat(prepared).isTrue();
    return sequenceableLoader.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    assertThat(prepared).isTrue();
    for (ChunkSampleStream<FakeChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs;
  }

  @Override
  public long getNextLoadPositionUs() {
    assertThat(prepared).isTrue();
    return sequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public boolean continueLoading(long positionUs) {
    sequenceableLoader.continueLoading(positionUs);
    return true;
  }

  @Override
  public boolean isLoading() {
    return sequenceableLoader.isLoading();
  }

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<FakeChunkSource> source) {
    Assertions.checkStateNotNull(callback).onContinueLoadingRequested(this);
  }
}
