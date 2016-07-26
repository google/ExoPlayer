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
package com.google.android.exoplayer2.source.smoothstreaming;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.mp4.TrackEncryptionBox;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.CompositeSequenceableLoader;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.ProtectionElement;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;

import android.util.Base64;

import java.io.IOException;
import java.util.List;

/**
 * A SmoothStreaming {@link MediaPeriod}.
 */
/* package */ final class SsMediaPeriod implements MediaPeriod,
    SequenceableLoader.Callback<ChunkSampleStream<SsChunkSource>> {

  private static final int INITIALIZATION_VECTOR_SIZE = 8;

  private final SsChunkSource.Factory chunkSourceFactory;
  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final TrackGroupArray trackGroups;
  private final TrackEncryptionBox[] trackEncryptionBoxes;

  private SsManifest manifest;
  private ChunkSampleStream<SsChunkSource>[] sampleStreams;
  private CompositeSequenceableLoader sequenceableLoader;
  private Callback callback;
  private Allocator allocator;

  public SsMediaPeriod(SsManifest manifest, SsChunkSource.Factory chunkSourceFactory,
      int minLoadableRetryCount, EventDispatcher eventDispatcher,
      LoaderErrorThrower manifestLoaderErrorThrower) {
    this.manifest = manifest;
    this.chunkSourceFactory = chunkSourceFactory;
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    trackGroups = buildTrackGroups(manifest);
    ProtectionElement protectionElement = manifest.protectionElement;
    if (protectionElement != null) {
      byte[] keyId = getProtectionElementKeyId(protectionElement.data);
      trackEncryptionBoxes = new TrackEncryptionBox[] {
          new TrackEncryptionBox(true, INITIALIZATION_VECTOR_SIZE, keyId)};
    } else {
      trackEncryptionBoxes = null;
    }
  }

  public void updateManifest(SsManifest manifest) {
    this.manifest = manifest;
    if (sampleStreams != null) {
      for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
        sampleStream.getChunkSource().updateManifest(manifest);
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public void preparePeriod(Callback callback, Allocator allocator, long positionUs) {
    this.callback = callback;
    this.allocator = allocator;
    sampleStreams = newSampleStreamArray(0);
    sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    callback.onPeriodPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    manifestLoaderErrorThrower.maybeThrowError();
  }

  @Override
  public long getDurationUs() {
    return manifest.durationUs;
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public SampleStream[] selectTracks(List<SampleStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    int newEnabledSourceCount = sampleStreams.length + newSelections.size() - oldStreams.size();
    ChunkSampleStream<SsChunkSource>[] newSampleStreams =
        newSampleStreamArray(newEnabledSourceCount);
    int newEnabledSourceIndex = 0;

    // Iterate over currently enabled streams, either releasing them or adding them to the new list.
    for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
      if (oldStreams.contains(sampleStream)) {
        sampleStream.release();
      } else {
        newSampleStreams[newEnabledSourceIndex++] = sampleStream;
      }
    }

    // Instantiate and return new streams.
    SampleStream[] streamsToReturn = new SampleStream[newSelections.size()];
    for (int i = 0; i < newSelections.size(); i++) {
      newSampleStreams[newEnabledSourceIndex] = buildSampleStream(newSelections.get(i), positionUs);
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
    for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
      long rendererBufferedPositionUs = sampleStream.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.END_OF_SOURCE_US) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.END_OF_SOURCE_US : bufferedPositionUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return positionUs;
  }

  @Override
  public void releasePeriod() {
    if (sampleStreams != null) {
      for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
        sampleStream.release();
      }
      sampleStreams = null;
    }
    sequenceableLoader = null;
    callback = null;
    allocator = null;
  }

  // SequenceableLoader.Callback implementation

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<SsChunkSource> sampleStream) {
    callback.onContinueLoadingRequested(this);
  }

  // Private methods.

  private ChunkSampleStream<SsChunkSource> buildSampleStream(TrackSelection selection,
      long positionUs) {
    int streamElementIndex = trackGroups.indexOf(selection.group);
    SsChunkSource chunkSource = chunkSourceFactory.createChunkSource(manifestLoaderErrorThrower,
        manifest, streamElementIndex, selection, trackEncryptionBoxes);
    return new ChunkSampleStream<>(manifest.streamElements[streamElementIndex].type, chunkSource,
        this, allocator, positionUs, minLoadableRetryCount, eventDispatcher);
  }

  private static TrackGroupArray buildTrackGroups(SsManifest manifest) {
    TrackGroup[] trackGroups = new TrackGroup[manifest.streamElements.length];
    for (int i = 0; i < manifest.streamElements.length; i++) {
      trackGroups[i] = new TrackGroup(manifest.streamElements[i].formats);
    }
    return new TrackGroupArray(trackGroups);
  }

  @SuppressWarnings("unchecked")
  private static ChunkSampleStream<SsChunkSource>[] newSampleStreamArray(int length) {
    return new ChunkSampleStream[length];
  }

  private static byte[] getProtectionElementKeyId(byte[] initData) {
    StringBuilder initDataStringBuilder = new StringBuilder();
    for (int i = 0; i < initData.length; i += 2) {
      initDataStringBuilder.append((char) initData[i]);
    }
    String initDataString = initDataStringBuilder.toString();
    String keyIdString = initDataString.substring(
        initDataString.indexOf("<KID>") + 5, initDataString.indexOf("</KID>"));
    byte[] keyId = Base64.decode(keyIdString, Base64.DEFAULT);
    swap(keyId, 0, 3);
    swap(keyId, 1, 2);
    swap(keyId, 4, 5);
    swap(keyId, 6, 7);
    return keyId;
  }

  private static void swap(byte[] data, int firstPosition, int secondPosition) {
    byte temp = data[firstPosition];
    data[firstPosition] = data[secondPosition];
    data[secondPosition] = temp;
  }

}
