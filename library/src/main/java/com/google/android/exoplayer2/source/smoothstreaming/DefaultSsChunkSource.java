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
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Track;
import com.google.android.exoplayer2.extractor.mp4.TrackEncryptionBox;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk;
import com.google.android.exoplayer2.source.chunk.FormatEvaluator;
import com.google.android.exoplayer2.source.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.Loader;

import android.net.Uri;

import java.io.IOException;
import java.util.List;

/**
 * A default {@link SsChunkSource} implementation.
 */
public class DefaultSsChunkSource implements SsChunkSource {

  public static final class Factory implements SsChunkSource.Factory {

    private final FormatEvaluator.Factory formatEvaluatorFactory;
    private final DataSource.Factory dataSourceFactory;

    public Factory(DataSource.Factory dataSourceFactory,
        FormatEvaluator.Factory formatEvaluatorFactory) {
      this.dataSourceFactory = dataSourceFactory;
      this.formatEvaluatorFactory = formatEvaluatorFactory;
    }

    @Override
    public SsChunkSource createChunkSource(Loader manifestLoader, SsManifest manifest,
        int elementIndex, TrackSelection trackSelection,
        TrackEncryptionBox[] trackEncryptionBoxes) {
      FormatEvaluator adaptiveEvaluator = trackSelection.length > 1
          ? formatEvaluatorFactory.createFormatEvaluator() : null;
      DataSource dataSource = dataSourceFactory.createDataSource();
      return new DefaultSsChunkSource(manifestLoader, manifest, elementIndex, trackSelection,
          dataSource, adaptiveEvaluator, trackEncryptionBoxes);
    }

  }

  private final Loader manifestLoader;
  private final int elementIndex;
  private final TrackSelection trackSelection;
  private final ChunkExtractorWrapper[] extractorWrappers;
  private final boolean[] adaptiveFormatBlacklistFlags;
  private final DataSource dataSource;
  private final Evaluation evaluation;
  private final FormatEvaluator adaptiveFormatEvaluator;

  private SsManifest manifest;
  private int currentManifestChunkOffset;

  private IOException fatalError;

  /**
   * @param manifestLoader The {@link Loader} being used to load manifests.
   * @param manifest The initial manifest.
   * @param elementIndex The index of the stream element in the manifest.
   * @param trackSelection The track selection.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
   * @param trackEncryptionBoxes Track encryption boxes for the stream.
   */
  public DefaultSsChunkSource(Loader manifestLoader, SsManifest manifest, int elementIndex,
      TrackSelection trackSelection, DataSource dataSource, FormatEvaluator adaptiveFormatEvaluator,
      TrackEncryptionBox[] trackEncryptionBoxes) {
    this.manifestLoader = manifestLoader;
    this.manifest = manifest;
    this.elementIndex = elementIndex;
    this.trackSelection = trackSelection;
    this.dataSource = dataSource;
    this.adaptiveFormatEvaluator = adaptiveFormatEvaluator;
    this.evaluation = new Evaluation();

    StreamElement streamElement = manifest.streamElements[elementIndex];

    extractorWrappers = new ChunkExtractorWrapper[trackSelection.length];
    for (int i = 0; i < trackSelection.length; i++) {
      int manifestTrackIndex = trackSelection.getTrack(i);
      Format format = trackSelection.getFormat(i);
      int nalUnitLengthFieldLength = streamElement.type == C.TRACK_TYPE_VIDEO ? 4 : -1;
      Track track = new Track(manifestTrackIndex, streamElement.type, streamElement.timescale,
          C.UNSET_TIME_US, manifest.durationUs, format, Track.TRANSFORMATION_NONE,
          trackEncryptionBoxes, nalUnitLengthFieldLength, null, null);
      FragmentedMp4Extractor extractor = new FragmentedMp4Extractor(
          FragmentedMp4Extractor.FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME
          | FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_TFDT_BOX, track);
      extractorWrappers[i] = new ChunkExtractorWrapper(extractor, format, false);
    }
    if (adaptiveFormatEvaluator != null) {
      adaptiveFormatEvaluator.enable(trackSelection.getFormats());
      adaptiveFormatBlacklistFlags = new boolean[trackSelection.length];
    } else {
      adaptiveFormatBlacklistFlags = null;
    }
  }

  @Override
  public void updateManifest(SsManifest newManifest) {
    StreamElement currentElement = manifest.streamElements[elementIndex];
    int currentElementChunkCount = currentElement.chunkCount;
    StreamElement newElement = newManifest.streamElements[elementIndex];
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

  // ChunkSource implementation.

  @Override
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else {
      manifestLoader.maybeThrowError();
    }
  }

  @Override
  public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    if (fatalError != null || trackSelection.length < 2) {
      return queue.size();
    }
    return adaptiveFormatEvaluator.evaluateQueueSize(playbackPositionUs, queue,
        adaptiveFormatBlacklistFlags);
  }

  @Override
  public final void getNextChunk(MediaChunk previous, long playbackPositionUs, ChunkHolder out) {
    if (fatalError != null) {
      return;
    }

    if (trackSelection.length > 1) {
      long bufferedDurationUs = previous != null ? (previous.endTimeUs - playbackPositionUs) : 0;
      adaptiveFormatEvaluator.evaluateFormat(bufferedDurationUs, adaptiveFormatBlacklistFlags,
          evaluation);
    } else {
      evaluation.format = trackSelection.getFormat(0);
      evaluation.reason = C.SELECTION_REASON_UNKNOWN;
      evaluation.data = null;
    }

    Format selectedFormat = evaluation.format;
    if (selectedFormat == null) {
      return;
    }

    StreamElement streamElement = manifest.streamElements[elementIndex];
    if (streamElement.chunkCount == 0) {
      // There aren't any chunks for us to load.
      out.endOfStream = !manifest.isLive;
      return;
    }

    int chunkIndex;
    if (previous == null) {
      chunkIndex = streamElement.getChunkIndex(playbackPositionUs);
    } else {
      chunkIndex = previous.getNextChunkIndex() - currentManifestChunkOffset;
      if (chunkIndex < 0) {
        // This is before the first chunk in the current manifest.
        fatalError = new BehindLiveWindowException();
        return;
      }
    }

    if (chunkIndex >= streamElement.chunkCount) {
      // This is beyond the last chunk in the current manifest.
      out.endOfStream = !manifest.isLive;
      return;
    }

    long chunkStartTimeUs = streamElement.getStartTimeUs(chunkIndex);
    long chunkEndTimeUs = chunkStartTimeUs + streamElement.getChunkDurationUs(chunkIndex);
    int currentAbsoluteChunkIndex = chunkIndex + currentManifestChunkOffset;

    int trackSelectionIndex = trackSelection.indexOf(selectedFormat);
    ChunkExtractorWrapper extractorWrapper = extractorWrappers[trackSelectionIndex];

    int manifestTrackIndex = trackSelection.getTrack(trackSelectionIndex);
    Uri uri = streamElement.buildRequestUri(manifestTrackIndex, chunkIndex);

    out.chunk = newMediaChunk(selectedFormat, dataSource, uri, null, currentAbsoluteChunkIndex,
        chunkStartTimeUs, chunkEndTimeUs, evaluation.reason, evaluation.data, extractorWrapper);
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    // Do nothing.
  }

  @Override
  public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e) {
    // TODO: Consider implementing stream element blacklisting.
    return false;
  }

  @Override
  public void release() {
    if (adaptiveFormatEvaluator != null) {
      adaptiveFormatEvaluator.disable();
    }
  }

  // Private methods.

  private static MediaChunk newMediaChunk(Format format, DataSource dataSource, Uri uri,
      String cacheKey, int chunkIndex, long chunkStartTimeUs, long chunkEndTimeUs,
      int trackSelectionReason, Object trackSelectionData, ChunkExtractorWrapper extractorWrapper) {
    DataSpec dataSpec = new DataSpec(uri, 0, -1, cacheKey);
    // In SmoothStreaming each chunk contains sample timestamps relative to the start of the chunk.
    // To convert them the absolute timestamps, we need to set sampleOffsetUs to chunkStartTimeUs.
    long sampleOffsetUs = chunkStartTimeUs;
    return new ContainerMediaChunk(dataSource, dataSpec, format, trackSelectionReason,
        trackSelectionData, chunkStartTimeUs, chunkEndTimeUs, chunkIndex, sampleOffsetUs,
        extractorWrapper, format);
  }

}
