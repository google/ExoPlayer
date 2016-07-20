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
import com.google.android.exoplayer2.Format.DecreasingBandwidthComparator;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Track;
import com.google.android.exoplayer2.extractor.mp4.TrackEncryptionBox;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk;
import com.google.android.exoplayer2.source.chunk.FormatEvaluator;
import com.google.android.exoplayer2.source.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.Loader;

import android.net.Uri;
import android.text.TextUtils;

import java.io.IOException;
import java.util.Arrays;
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
        int elementIndex, TrackGroup trackGroup, int[] tracks,
        TrackEncryptionBox[] trackEncryptionBoxes) {
      FormatEvaluator adaptiveEvaluator = tracks.length > 1
          ? formatEvaluatorFactory.createFormatEvaluator() : null;
      DataSource dataSource = dataSourceFactory.createDataSource();
      return new DefaultSsChunkSource(manifestLoader, manifest, elementIndex,
          trackGroup, tracks, dataSource, adaptiveEvaluator,
          trackEncryptionBoxes);
    }

  }

  private final Loader manifestLoader;
  private final int elementIndex;
  private final TrackGroup trackGroup;
  private final ChunkExtractorWrapper[] extractorWrappers;
  private final Format[] enabledFormats;
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
   * @param trackGroup The track group corresponding to the stream element.
   * @param tracks The indices of the selected tracks within the stream element.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
   * @param trackEncryptionBoxes Track encryption boxes for the stream.
   */
  public DefaultSsChunkSource(Loader manifestLoader, SsManifest manifest, int elementIndex,
      TrackGroup trackGroup, int[] tracks, DataSource dataSource,
      FormatEvaluator adaptiveFormatEvaluator, TrackEncryptionBox[] trackEncryptionBoxes) {
    this.manifestLoader = manifestLoader;
    this.manifest = manifest;
    this.elementIndex = elementIndex;
    this.trackGroup = trackGroup;
    this.dataSource = dataSource;
    this.adaptiveFormatEvaluator = adaptiveFormatEvaluator;
    this.evaluation = new Evaluation();

    StreamElement streamElement = manifest.streamElements[elementIndex];
    Format[] formats = streamElement.formats;
    extractorWrappers = new ChunkExtractorWrapper[formats.length];
    for (int j = 0; j < formats.length; j++) {
      int nalUnitLengthFieldLength = streamElement.type == C.TRACK_TYPE_VIDEO ? 4 : -1;
      Track track = new Track(j, streamElement.type, streamElement.timescale, C.UNSET_TIME_US,
          manifest.durationUs, formats[j], Track.TRANSFORMATION_NONE, trackEncryptionBoxes,
          nalUnitLengthFieldLength, null, null);
      FragmentedMp4Extractor extractor = new FragmentedMp4Extractor(
          FragmentedMp4Extractor.FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME
          | FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_TFDT_BOX, track);
      extractorWrappers[j] = new ChunkExtractorWrapper(extractor, formats[j], false);
    }

    enabledFormats = new Format[tracks.length];
    for (int i = 0; i < tracks.length; i++) {
      enabledFormats[i] = trackGroup.getFormat(tracks[i]);
    }
    Arrays.sort(enabledFormats, new DecreasingBandwidthComparator());
    if (adaptiveFormatEvaluator != null) {
      adaptiveFormatEvaluator.enable(enabledFormats);
      adaptiveFormatBlacklistFlags = new boolean[tracks.length];
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
    if (fatalError != null || enabledFormats.length < 2) {
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

    if (enabledFormats.length > 1) {
      long bufferedDurationUs = previous != null ? (previous.endTimeUs - playbackPositionUs) : 0;
      adaptiveFormatEvaluator.evaluateFormat(bufferedDurationUs, adaptiveFormatBlacklistFlags,
          evaluation);
    } else {
      evaluation.format = enabledFormats[0];
      evaluation.trigger = FormatEvaluator.TRIGGER_UNKNOWN;
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

    int trackGroupTrackIndex = getTrackGroupTrackIndex(trackGroup, selectedFormat);
    ChunkExtractorWrapper extractorWrapper = extractorWrappers[trackGroupTrackIndex];

    int manifestTrackIndex = getManifestTrackIndex(streamElement, selectedFormat);
    Uri uri = streamElement.buildRequestUri(manifestTrackIndex, chunkIndex);

    out.chunk = newMediaChunk(selectedFormat, dataSource, uri, null, currentAbsoluteChunkIndex,
        chunkStartTimeUs, chunkEndTimeUs, evaluation.trigger, evaluation.data, extractorWrapper);
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

  /**
   * Gets the index of a format in a track group, using referential equality.
   */
  private static int getTrackGroupTrackIndex(TrackGroup trackGroup, Format format) {
    for (int i = 0; i < trackGroup.length; i++) {
      if (trackGroup.getFormat(i) == format) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  /**
   * Gets the index of a format in an element, using format.id equality.
   * <p>
   * This method will return the same index as {@link #getTrackGroupTrackIndex(TrackGroup, Format)}
   * except in the case where a live manifest is refreshed and the ordering of the tracks in the
   * manifest has changed.
   */
  private static int getManifestTrackIndex(StreamElement element, Format format) {
    Format[] formats = element.formats;
    for (int i = 0; i < formats.length; i++) {
      if (TextUtils.equals(formats[i].id, format.id)) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  private static MediaChunk newMediaChunk(Format format, DataSource dataSource, Uri uri,
      String cacheKey, int chunkIndex, long chunkStartTimeUs, long chunkEndTimeUs,
      int formatEvaluatorTrigger, Object formatEvaluatorData,
      ChunkExtractorWrapper extractorWrapper) {
    DataSpec dataSpec = new DataSpec(uri, 0, -1, cacheKey);
    // In SmoothStreaming each chunk contains sample timestamps relative to the start of the chunk.
    // To convert them the absolute timestamps, we need to set sampleOffsetUs to chunkStartTimeUs.
    long sampleOffsetUs = chunkStartTimeUs;
    return new ContainerMediaChunk(dataSource, dataSpec, format, formatEvaluatorTrigger,
        formatEvaluatorData, chunkStartTimeUs, chunkEndTimeUs, chunkIndex, sampleOffsetUs,
        extractorWrapper, format);
  }

}
