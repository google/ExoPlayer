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

import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Track;
import com.google.android.exoplayer2.extractor.mp4.TrackEncryptionBox;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.chunk.BaseMediaChunkIterator;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A default {@link SsChunkSource} implementation.
 */
public class DefaultSsChunkSource implements SsChunkSource, FragmentedMp4Extractor.SsAtomCallback {

  public static final class Factory implements SsChunkSource.Factory {

    private final DataSource.Factory dataSourceFactory;

    public Factory(DataSource.Factory dataSourceFactory) {
      this.dataSourceFactory = dataSourceFactory;
    }

    @Override
    public SsChunkSource createChunkSource(
        LoaderErrorThrower manifestLoaderErrorThrower,
        SsManifest manifest,
        int elementIndex,
        TrackSelection trackSelection,
        @Nullable TransferListener transferListener,
	SsMediaSource mediaSource) {
      DataSource dataSource = dataSourceFactory.createDataSource();
      if (transferListener != null) {
        dataSource.addTransferListener(transferListener);
      }
      return new DefaultSsChunkSource(
          manifestLoaderErrorThrower, manifest, elementIndex, trackSelection, dataSource, mediaSource);
    }

  }

  public static class ChunkInfo implements Comparable<ChunkInfo> {
    //Times are in timescale base
    public final long startTimeTs;
    public final long durationTs;
    public final int chunkId;
    public ChunkInfo(long startTimeTs, long durationTs, int chunkId) {
      this.startTimeTs = startTimeTs;
      this.durationTs=durationTs;
      this.chunkId=chunkId;
    }

    @Override
    public int compareTo(ChunkInfo chunkInfo) {
      if(this.startTimeTs > chunkInfo.startTimeTs)
        return 1;
      else if(this.startTimeTs < chunkInfo.startTimeTs)
        return -1;
      return 0;
    }
  }

  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final int streamElementIndex;
  private final ChunkExtractorWrapper[] extractorWrappers;
  private final DataSource dataSource;

  private TrackSelection trackSelection;
  private SsManifest manifest;
  private int currentManifestChunkOffset;

  @Nullable private IOException fatalError;
  private final TreeSet<ChunkInfo> ssChunks = new TreeSet<ChunkInfo>();
  @Nullable private final SsMediaSource mediaSource;
  private final long tsDeltaUs;

  /**
   * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
   * @param manifest The initial manifest.
   * @param streamElementIndex The index of the stream element in the manifest.
   * @param trackSelection The track selection.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   */
  public DefaultSsChunkSource(
      LoaderErrorThrower manifestLoaderErrorThrower,
      SsManifest manifest,
      int streamElementIndex,
      TrackSelection trackSelection,
      DataSource dataSource,
      SsMediaSource mediaSource) {
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.manifest = manifest;
    this.streamElementIndex = streamElementIndex;
    this.trackSelection = trackSelection;
    this.dataSource = dataSource;
    this.mediaSource = mediaSource;

    StreamElement streamElement = manifest.streamElements[streamElementIndex];
    extractorWrappers = new ChunkExtractorWrapper[trackSelection.length()];
    for (int i = 0; i < extractorWrappers.length; i++) {
      int manifestTrackIndex = trackSelection.getIndexInTrackGroup(i);
      Format format = streamElement.formats[manifestTrackIndex];
      @Nullable
      TrackEncryptionBox[] trackEncryptionBoxes =
          format.drmInitData != null
              ? Assertions.checkNotNull(manifest.protectionElement).trackEncryptionBoxes
              : null;
      int nalUnitLengthFieldLength = streamElement.type == C.TRACK_TYPE_VIDEO ? 4 : 0;
      Track track = new Track(manifestTrackIndex, streamElement.type, streamElement.timescale,
          C.TIME_UNSET, manifest.durationUs, format, Track.TRANSFORMATION_NONE,
          trackEncryptionBoxes, nalUnitLengthFieldLength, null, null);
      FragmentedMp4Extractor extractor =
          new FragmentedMp4Extractor(
              FragmentedMp4Extractor.FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME
                  | FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_TFDT_BOX,
              /* timestampAdjuster= */ null,
              track);
      extractorWrappers[i] = new ChunkExtractorWrapper(extractor, streamElement.type, format);
      extractor.setSsAtomCallback(this);
    }
    for(int i=0; i<streamElement.chunkCount; i++) {
      ssChunks.add(new ChunkInfo(
          streamElement.getStartTime(i),
          streamElement.getChunkDuration(i),
          i
        ));
    }
    //Assume now = lastChunk start + duration
    tsDeltaUs =
        System.currentTimeMillis()*1000L -
            (streamElement.getStartTimeUs(streamElement.chunkCount - 1) +
                streamElement.getChunkDurationUs(streamElement.chunkCount - 1));
    currentManifestChunkOffset=streamElement.chunkCount;
  }

  private synchronized boolean clearOldChunks() {
    long tsNowUs = System.currentTimeMillis()*1000L - tsDeltaUs;
    long tsOld = tsNowUs*10L - manifest.dvrWindowLengthUs*10L;

    ArrayList<ChunkInfo> toRemove = new ArrayList<>();
    for (ChunkInfo i : ssChunks) {
      if (i.startTimeTs < tsOld)
        toRemove.add(i);
    }
    ssChunks.removeAll(toRemove);
    return toRemove.isEmpty();
  }

  private synchronized void updateTimeline() {
    ChunkInfo end = ssChunks.last();
    ChunkInfo head = ssChunks.first();

    long chunksWindowDuration = (end.durationTs + end.startTimeTs - head.startTimeTs)/10L;
    long startTime = head.startTimeTs/10L;
    long defaultStart = manifest.dvrWindowLengthUs - 10*1000L*1000L;

    SinglePeriodTimeline timeline = new SinglePeriodTimeline(C.TIME_UNSET, chunksWindowDuration, startTime,
            defaultStart, true /* isSeekable */, true /* isDynamic */, true, null, null);
    if(mediaSource != null) {
        mediaSource.sourceInfoRefreshed(timeline);
    }
  }

  public synchronized void onTfrfAtom(long start, long duration) {
    if(!manifest.isLive) return;

    boolean ret =
            ssChunks.add(new ChunkInfo(
              start,
              duration,
              currentManifestChunkOffset++));
    //If we were already aware of this chunk, don't do anything
    if(!ret) return;
    clearOldChunks();
    updateTimeline();
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    StreamElement streamElement = manifest.streamElements[streamElementIndex];
    int chunkIndex = streamElement.getChunkIndex(positionUs);
    long firstSyncUs = streamElement.getStartTimeUs(chunkIndex);
    long secondSyncUs =
        firstSyncUs < positionUs && chunkIndex < streamElement.chunkCount - 1
            ? streamElement.getStartTimeUs(chunkIndex + 1)
            : firstSyncUs;
    return seekParameters.resolveSeekPositionUs(positionUs, firstSyncUs, secondSyncUs);
  }

  @Override
  public void updateManifest(SsManifest newManifest) {
    StreamElement currentElement = manifest.streamElements[streamElementIndex];
    int currentElementChunkCount = currentElement.chunkCount;
    StreamElement newElement = newManifest.streamElements[streamElementIndex];
    if (currentElementChunkCount == 0 || newElement.chunkCount == 0) {
      // There's no overlap between the old and new elements because at least one is empty.
      currentManifestChunkOffset += currentElementChunkCount;
    } else {
      long currentElementEndTimeUs = currentElement.getStartTimeUs(currentElementChunkCount - 1)
          + currentElement.getChunkDurationUs(currentElementChunkCount - 1);
      long newElementStartTimeUs = newElement.getStartTimeUs(0);
      if (currentElementEndTimeUs <= newElementStartTimeUs) {
        // There's no overlap between the old and new elements.
        currentManifestChunkOffset += currentElementChunkCount;
      } else {
        // The new element overlaps with the old one.
        currentManifestChunkOffset += currentElement.getChunkIndex(newElementStartTimeUs);
      }
    }
    manifest = newManifest;
  }

  @Override
  public void updateTrackSelection(TrackSelection trackSelection) {
    this.trackSelection = trackSelection;
  }

  // ChunkSource implementation.

  @Override
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else {
      manifestLoaderErrorThrower.maybeThrowError();
    }
  }

  @Override
  public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    if (fatalError != null || trackSelection.length() < 2) {
      return queue.size();
    }
    return trackSelection.evaluateQueueSize(playbackPositionUs, queue);
  }

  private synchronized ChunkInfo bestChunk(MediaChunk previous, long loadPositionUs) {
    // We'll have chunkFloor < chunkCeiling
    ChunkInfo chunkCeiling = ssChunks.ceiling(new ChunkInfo(loadPositionUs*10L, 0L, 0));
    ChunkInfo chunkFloor = ssChunks.floor(new ChunkInfo(loadPositionUs*10L, 0L, 0));
    ChunkInfo chunk = chunkCeiling;
    if(chunkFloor == null) return ssChunks.last();
    if(chunkCeiling == null) return chunkFloor;

    if(previous == null) {
      //If it's the first chunk we send, send the closest one
      if(Math.abs(chunkCeiling.startTimeTs - loadPositionUs) >
              Math.abs(chunkFloor.startTimeTs - loadPositionUs))
        chunk = chunkFloor;
      else
        chunk = chunkCeiling;
    }
    return chunk;
  }

  @Override
  public final void getNextChunk(
      long playbackPositionUs,
      long loadPositionUs,
      List<? extends MediaChunk> queue,
      ChunkHolder out) {
    if (fatalError != null) {
      return;
    }

    StreamElement streamElement = manifest.streamElements[streamElementIndex];
    if (streamElement.chunkCount == 0) {
      // There aren't any chunks for us to load.
      out.endOfStream = !manifest.isLive;
      return;
    }

    clearOldChunks();

    MediaChunk previous = queue.isEmpty() ? null : queue.get(queue.size() - 1);
    ChunkInfo chunk = bestChunk(previous, loadPositionUs);
    if(chunk == null) {
      // This is before the first chunk in the current manifest.
      out.endOfStream = !manifest.isLive;
      return;
    }

    long bufferedDurationUs = loadPositionUs - playbackPositionUs;
    long timeToLiveEdgeUs = resolveTimeToLiveEdgeUs(playbackPositionUs);

    MediaChunkIterator[] chunkIterators = new MediaChunkIterator[trackSelection.length()];
    for (int i = 0; i < chunkIterators.length; i++) {
      int trackIndex = trackSelection.getIndexInTrackGroup(i);
      chunkIterators[i] = new StreamElementIterator(streamElement, trackIndex, chunk.chunkId);
    }
    trackSelection.updateSelectedTrack(
        playbackPositionUs, bufferedDurationUs, timeToLiveEdgeUs, queue, chunkIterators);

    long chunkStartTimeUs = chunk.startTimeTs/10L;
    long chunkEndTimeUs = chunkStartTimeUs + chunk.durationTs/10L;
    long chunkSeekTimeUs = queue.isEmpty() ? loadPositionUs : C.TIME_UNSET;

    int trackSelectionIndex = trackSelection.getSelectedIndex();
    ChunkExtractorWrapper extractorWrapper = extractorWrappers[trackSelectionIndex];

    int manifestTrackIndex = trackSelection.getIndexInTrackGroup(trackSelectionIndex);
    Uri uri = streamElement.buildRequestUriFromStartTime(manifestTrackIndex, chunk.startTimeTs);

    out.chunk =
        newMediaChunk(
            trackSelection.getSelectedFormat(),
            dataSource,
            uri,
            chunk.chunkId,
            chunkStartTimeUs,
            chunkEndTimeUs,
            chunkSeekTimeUs,
            trackSelection.getSelectionReason(),
            trackSelection.getSelectionData(),
            extractorWrapper);
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    // Do nothing.
  }

  @Override
  public boolean onChunkLoadError(
      Chunk chunk, boolean cancelable, Exception e, long blacklistDurationMs) {
    return cancelable
        && blacklistDurationMs != C.TIME_UNSET
        && trackSelection.blacklist(trackSelection.indexOf(chunk.trackFormat), blacklistDurationMs);
  }

  // Private methods.

  private static MediaChunk newMediaChunk(
      Format format,
      DataSource dataSource,
      Uri uri,
      int chunkIndex,
      long chunkStartTimeUs,
      long chunkEndTimeUs,
      long chunkSeekTimeUs,
      int trackSelectionReason,
      @Nullable Object trackSelectionData,
      ChunkExtractorWrapper extractorWrapper) {
    DataSpec dataSpec = new DataSpec(uri);
    // In SmoothStreaming each chunk contains sample timestamps relative to the start of the chunk.
    // To convert them the absolute timestamps, we need to set sampleOffsetUs to chunkStartTimeUs.
    long sampleOffsetUs = chunkStartTimeUs;
    return new ContainerMediaChunk(
        dataSource,
        dataSpec,
        format,
        trackSelectionReason,
        trackSelectionData,
        chunkStartTimeUs,
        chunkEndTimeUs,
        chunkSeekTimeUs,
        /* clippedEndTimeUs= */ C.TIME_UNSET,
        chunkIndex,
        /* chunkCount= */ 1,
        sampleOffsetUs,
        extractorWrapper);
  }

  private long resolveTimeToLiveEdgeUs(long playbackPositionUs) {
    if (!manifest.isLive) {
      return C.TIME_UNSET;
    }

    StreamElement currentElement = manifest.streamElements[streamElementIndex];
    int lastChunkIndex = currentElement.chunkCount - 1;
    long lastChunkEndTimeUs = currentElement.getStartTimeUs(lastChunkIndex)
        + currentElement.getChunkDurationUs(lastChunkIndex);
    return lastChunkEndTimeUs - playbackPositionUs;
  }

  /** {@link MediaChunkIterator} wrapping a track of a {@link StreamElement}. */
  private static final class StreamElementIterator extends BaseMediaChunkIterator {

    private final StreamElement streamElement;
    private final int trackIndex;

    /**
     * Creates iterator.
     *
     * @param streamElement The {@link StreamElement} to wrap.
     * @param trackIndex The track index in the stream element.
     * @param chunkIndex The index of the first available chunk.
     */
    public StreamElementIterator(StreamElement streamElement, int trackIndex, int chunkIndex) {
      super(/* fromIndex= */ chunkIndex, /* toIndex= */ streamElement.chunkCount - 1);
      this.streamElement = streamElement;
      this.trackIndex = trackIndex;
    }

    @Override
    public DataSpec getDataSpec() {
      checkInBounds();
      Uri uri = streamElement.buildRequestUri(trackIndex, (int) getCurrentIndex());
      return new DataSpec(uri);
    }

    @Override
    public long getChunkStartTimeUs() {
      checkInBounds();
      return streamElement.getStartTimeUs((int) getCurrentIndex());
    }

    @Override
    public long getChunkEndTimeUs() {
      long chunkStartTimeUs = getChunkStartTimeUs();
      return chunkStartTimeUs + streamElement.getChunkDurationUs((int) getCurrentIndex());
    }
  }
}
