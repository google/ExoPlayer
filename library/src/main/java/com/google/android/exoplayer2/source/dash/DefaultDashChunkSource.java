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
import com.google.android.exoplayer2.Format.DecreasingBandwidthComparator;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk;
import com.google.android.exoplayer2.source.chunk.FormatEvaluator;
import com.google.android.exoplayer2.source.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer2.source.chunk.InitializationChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.SingleSampleMediaChunk;
import com.google.android.exoplayer2.source.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer2.source.dash.mpd.RangedUri;
import com.google.android.exoplayer2.source.dash.mpd.Representation;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import android.os.SystemClock;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A default {@link DashChunkSource} implementation.
 */
public class DefaultDashChunkSource implements DashChunkSource {

  public static final class Factory implements DashChunkSource.Factory {

    private final FormatEvaluator.Factory formatEvaluatorFactory;
    private final DataSource.Factory dataSourceFactory;

    public Factory(DataSource.Factory dataSourceFactory,
        FormatEvaluator.Factory formatEvaluatorFactory) {
      this.dataSourceFactory = dataSourceFactory;
      this.formatEvaluatorFactory = formatEvaluatorFactory;
    }

    @Override
    public DashChunkSource createDashChunkSource(Loader manifestLoader,
        MediaPresentationDescription manifest, int periodIndex, int adaptationSetIndex,
        TrackGroup trackGroup, int[] tracks, long elapsedRealtimeOffsetMs) {
      FormatEvaluator adaptiveEvaluator = tracks.length > 1
          ? formatEvaluatorFactory.createFormatEvaluator() : null;
      DataSource dataSource = dataSourceFactory.createDataSource();
     return new DefaultDashChunkSource(manifestLoader, manifest, periodIndex, adaptationSetIndex,
         trackGroup, tracks, dataSource, adaptiveEvaluator, elapsedRealtimeOffsetMs);
    }

  }

  private final Loader manifestLoader;
  private final int adaptationSetIndex;
  private final TrackGroup trackGroup;
  private final RepresentationHolder[] representationHolders;
  private final Format[] enabledFormats;
  private final boolean[] adaptiveFormatBlacklistFlags;
  private final DataSource dataSource;
  private final FormatEvaluator adaptiveFormatEvaluator;
  private final long elapsedRealtimeOffsetUs;
  private final Evaluation evaluation;

  private MediaPresentationDescription manifest;

  private boolean lastChunkWasInitialization;
  private IOException fatalError;
  private boolean missingLastSegment;

  /**
   * @param manifestLoader The {@link Loader} being used to load manifests.
   * @param manifest The initial manifest.
   * @param periodIndex The index of the period in the manifest.
   * @param adaptationSetIndex The index of the adaptation set in the period.
   * @param trackGroup The track group corresponding to the adaptation set.
   * @param tracks The indices of the selected tracks within the adaptation set.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
   * @param elapsedRealtimeOffsetMs If known, an estimate of the instantaneous difference between
   *     server-side unix time and {@link SystemClock#elapsedRealtime()} in milliseconds, specified
   *     as the server's unix time minus the local elapsed time. If unknown, set to 0.
   */
  public DefaultDashChunkSource(Loader manifestLoader, MediaPresentationDescription manifest,
      int periodIndex, int adaptationSetIndex, TrackGroup trackGroup, int[] tracks,
      DataSource dataSource, FormatEvaluator adaptiveFormatEvaluator,
      long elapsedRealtimeOffsetMs) {
    this.manifestLoader = manifestLoader;
    this.manifest = manifest;
    this.adaptationSetIndex = adaptationSetIndex;
    this.trackGroup = trackGroup;
    this.dataSource = dataSource;
    this.adaptiveFormatEvaluator = adaptiveFormatEvaluator;
    this.elapsedRealtimeOffsetUs = elapsedRealtimeOffsetMs * 1000;
    this.evaluation = new Evaluation();

    long periodDurationUs = getPeriodDurationUs(periodIndex);
    List<Representation> representations = getRepresentations(periodIndex);
    representationHolders = new RepresentationHolder[representations.size()];

    for (int i = 0; i < representations.size(); i++) {
      Representation representation = representations.get(i);
      representationHolders[i] = new RepresentationHolder(periodDurationUs, representation);
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
  public void updateManifest(MediaPresentationDescription newManifest, int periodIndex) {
    try {
      manifest = newManifest;
      long periodDurationUs = getPeriodDurationUs(periodIndex);
      List<Representation> representations = getRepresentations(periodIndex);
      for (int i = 0; i < representationHolders.length; i++) {
        Representation representation = representations.get(i);
        representationHolders[i].updateRepresentation(periodDurationUs, representation);
      }
    } catch (BehindLiveWindowException e) {
      fatalError = e;
    }
  }

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

    if (evaluation.format == null || !lastChunkWasInitialization) {
      if (enabledFormats.length > 1) {
        long bufferedDurationUs = previous != null ? (previous.endTimeUs - playbackPositionUs) : 0;
        adaptiveFormatEvaluator.evaluateFormat(bufferedDurationUs, adaptiveFormatBlacklistFlags,
            evaluation);
      } else {
        evaluation.format = enabledFormats[0];
        evaluation.trigger = FormatEvaluator.TRIGGER_UNKNOWN;
        evaluation.data = null;
      }
    }

    Format selectedFormat = evaluation.format;
    if (selectedFormat == null) {
      return;
    }

    RepresentationHolder representationHolder =
        representationHolders[getTrackIndex(selectedFormat)];
    Representation selectedRepresentation = representationHolder.representation;
    DashSegmentIndex segmentIndex = representationHolder.segmentIndex;

    RangedUri pendingInitializationUri = null;
    RangedUri pendingIndexUri = null;
    Format sampleFormat = representationHolder.sampleFormat;
    if (sampleFormat == null) {
      pendingInitializationUri = selectedRepresentation.getInitializationUri();
    }
    if (segmentIndex == null) {
      pendingIndexUri = selectedRepresentation.getIndexUri();
    }
    if (pendingInitializationUri != null || pendingIndexUri != null) {
      // We have initialization and/or index requests to make.
      Chunk initializationChunk = newInitializationChunk(representationHolder, dataSource,
          selectedFormat, pendingInitializationUri, pendingIndexUri, evaluation.trigger,
          evaluation.data);
      lastChunkWasInitialization = true;
      out.chunk = initializationChunk;
      return;
    }

    long nowUs = getNowUnixTimeUs();
    int firstAvailableSegmentNum = representationHolder.getFirstSegmentNum();
    int lastAvailableSegmentNum = representationHolder.getLastSegmentNum();
    boolean indexUnbounded = lastAvailableSegmentNum == DashSegmentIndex.INDEX_UNBOUNDED;
    if (indexUnbounded) {
      // The index is itself unbounded. We need to use the current time to calculate the range of
      // available segments.
      long liveEdgeTimestampUs = nowUs - manifest.availabilityStartTime * 1000;
      if (manifest.timeShiftBufferDepth != -1) {
        long bufferDepthUs = manifest.timeShiftBufferDepth * 1000;
        firstAvailableSegmentNum = Math.max(firstAvailableSegmentNum,
            representationHolder.getSegmentNum(liveEdgeTimestampUs - bufferDepthUs));
      }
      // getSegmentNum(liveEdgeTimestampUs) will not be completed yet, so subtract one to get the
      // index of the last completed segment.
      lastAvailableSegmentNum = representationHolder.getSegmentNum(liveEdgeTimestampUs) - 1;
    }

    int segmentNum;
    if (previous == null) {
      segmentNum = Util.constrainValue(representationHolder.getSegmentNum(playbackPositionUs),
          firstAvailableSegmentNum, lastAvailableSegmentNum);
    } else {
      segmentNum = previous.getNextChunkIndex();
      if (segmentNum < firstAvailableSegmentNum) {
        // This is before the first chunk in the current manifest.
        fatalError = new BehindLiveWindowException();
        return;
      }
    }

    if (segmentNum > lastAvailableSegmentNum
        || (missingLastSegment && segmentNum >= lastAvailableSegmentNum)) {
      // This is beyond the last chunk in the current manifest.
      out.endOfStream = !manifest.dynamic;
      return;
    }

    Chunk nextMediaChunk = newMediaChunk(representationHolder, dataSource, selectedFormat,
        sampleFormat, segmentNum, evaluation.trigger, evaluation.data);
    lastChunkWasInitialization = false;
    out.chunk = nextMediaChunk;
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof InitializationChunk) {
      InitializationChunk initializationChunk = (InitializationChunk) chunk;
      RepresentationHolder representationHolder =
          representationHolders[getTrackIndex(initializationChunk.format)];
      Format sampleFormat = initializationChunk.getSampleFormat();
      if (sampleFormat != null) {
        representationHolder.setSampleFormat(sampleFormat);
      }
      // The null check avoids overwriting an index obtained from the manifest with one obtained
      // from the stream. If the manifest defines an index then the stream shouldn't, but in cases
      // where it does we should ignore it.
      if (representationHolder.segmentIndex == null) {
        SeekMap seekMap = initializationChunk.getSeekMap();
        if (seekMap != null) {
          representationHolder.segmentIndex = new DashWrappingSegmentIndex((ChunkIndex) seekMap,
              initializationChunk.dataSpec.uri.toString());
        }
      }
    }
  }

  @Override
  public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e) {
    // Workaround for missing segment at the end of the period
    if (cancelable && !manifest.dynamic && chunk instanceof MediaChunk
        && e instanceof InvalidResponseCodeException
        && ((InvalidResponseCodeException) e).responseCode == 404) {
      RepresentationHolder representationHolder =
          representationHolders[getTrackIndex(chunk.format)];
      int lastAvailableSegmentNum = representationHolder.getLastSegmentNum();
      if (((MediaChunk) chunk).chunkIndex >= lastAvailableSegmentNum) {
        missingLastSegment = true;
        return true;
      }
    }
    // TODO: Consider implementing representation blacklisting.
    return false;
  }

  @Override
  public void release() {
    if (adaptiveFormatEvaluator != null) {
      adaptiveFormatEvaluator.disable();
    }
  }

  // Private methods.

  private List<Representation> getRepresentations(int periodIndex) {
    return manifest.getPeriod(periodIndex).adaptationSets.get(adaptationSetIndex).representations;
  }

  private long getNowUnixTimeUs() {
    if (elapsedRealtimeOffsetUs != 0) {
      return (SystemClock.elapsedRealtime() * 1000) + elapsedRealtimeOffsetUs;
    } else {
      return System.currentTimeMillis() * 1000;
    }
  }

  private Chunk newInitializationChunk(RepresentationHolder representationHolder,
      DataSource dataSource, Format trackFormat, RangedUri initializationUri, RangedUri indexUri,
      int formatEvaluatorTrigger, Object formatEvaluatorData) {
    RangedUri requestUri;
    if (initializationUri != null) {
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      requestUri = initializationUri.attemptMerge(indexUri);
      if (requestUri == null) {
        requestUri = initializationUri;
      }
    } else {
      requestUri = indexUri;
    }
    DataSpec dataSpec = new DataSpec(requestUri.getUri(), requestUri.start, requestUri.length,
        representationHolder.representation.getCacheKey());
    return new InitializationChunk(dataSource, dataSpec, trackFormat,
        formatEvaluatorTrigger, formatEvaluatorData, representationHolder.extractorWrapper);
  }

  private Chunk newMediaChunk(RepresentationHolder representationHolder, DataSource dataSource,
      Format trackFormat, Format sampleFormat, int segmentNum, int formatEvaluatorTrigger,
      Object formatEvaluatorData) {
    Representation representation = representationHolder.representation;
    long startTimeUs = representationHolder.getSegmentStartTimeUs(segmentNum);
    long endTimeUs = representationHolder.getSegmentEndTimeUs(segmentNum);
    RangedUri segmentUri = representationHolder.getSegmentUrl(segmentNum);
    DataSpec dataSpec = new DataSpec(segmentUri.getUri(), segmentUri.start, segmentUri.length,
        representation.getCacheKey());

    if (representationHolder.extractorWrapper == null) {
      return new SingleSampleMediaChunk(dataSource, dataSpec, trackFormat, formatEvaluatorTrigger,
          formatEvaluatorData, startTimeUs, endTimeUs, segmentNum, trackFormat);
    } else {
      long sampleOffsetUs = -representation.presentationTimeOffsetUs;
      return new ContainerMediaChunk(dataSource, dataSpec, trackFormat, formatEvaluatorTrigger,
          formatEvaluatorData, startTimeUs, endTimeUs, segmentNum, sampleOffsetUs,
          representationHolder.extractorWrapper, sampleFormat);
    }
  }

  private int getTrackIndex(Format format) {
    for (int i = 0; i < trackGroup.length; i++) {
      if (trackGroup.getFormat(i) == format) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  private long getPeriodDurationUs(int periodIndex) {
    long durationMs = manifest.getPeriodDuration(periodIndex);
    if (durationMs == -1) {
      return C.UNSET_TIME_US;
    } else {
      return durationMs * 1000;
    }
  }

  // Protected classes.

  protected static final class RepresentationHolder {

    public final ChunkExtractorWrapper extractorWrapper;

    public Representation representation;
    public DashSegmentIndex segmentIndex;
    public Format sampleFormat;

    private long periodDurationUs;
    private int segmentNumShift;

    public RepresentationHolder(long periodDurationUs, Representation representation) {
      this.periodDurationUs = periodDurationUs;
      this.representation = representation;
      String containerMimeType = representation.format.containerMimeType;
      // Prefer drmInitData obtained from the manifest over drmInitData obtained from the stream,
      // as per DASH IF Interoperability Recommendations V3.0, 7.5.3.
      extractorWrapper = mimeTypeIsRawText(containerMimeType) ? null : new ChunkExtractorWrapper(
          mimeTypeIsWebm(containerMimeType) ? new MatroskaExtractor()
              : new FragmentedMp4Extractor(),
          representation.format, true /* preferManifestDrmInitData */);
      segmentIndex = representation.getIndex();
    }

    public void setSampleFormat(Format sampleFormat) {
      this.sampleFormat = sampleFormat;
    }

    public void updateRepresentation(long newPeriodDurationUs, Representation newRepresentation)
        throws BehindLiveWindowException{
      DashSegmentIndex oldIndex = representation.getIndex();
      DashSegmentIndex newIndex = newRepresentation.getIndex();

      periodDurationUs = newPeriodDurationUs;
      representation = newRepresentation;
      if (oldIndex == null) {
        // Segment numbers cannot shift if the index isn't defined by the manifest.
        return;
      }

      segmentIndex = newIndex;
      if (!oldIndex.isExplicit()) {
        // Segment numbers cannot shift if the index isn't explicit.
        return;
      }

      int oldIndexLastSegmentNum = oldIndex.getLastSegmentNum(periodDurationUs);
      long oldIndexEndTimeUs = oldIndex.getTimeUs(oldIndexLastSegmentNum)
          + oldIndex.getDurationUs(oldIndexLastSegmentNum, periodDurationUs);
      int newIndexFirstSegmentNum = newIndex.getFirstSegmentNum();
      long newIndexStartTimeUs = newIndex.getTimeUs(newIndexFirstSegmentNum);
      if (oldIndexEndTimeUs == newIndexStartTimeUs) {
        // The new index continues where the old one ended, with no overlap.
        segmentNumShift += oldIndex.getLastSegmentNum(periodDurationUs) + 1
            - newIndexFirstSegmentNum;
      } else if (oldIndexEndTimeUs < newIndexStartTimeUs) {
        // There's a gap between the old index and the new one which means we've slipped behind the
        // live window and can't proceed.
        throw new BehindLiveWindowException();
      } else {
        // The new index overlaps with the old one.
        segmentNumShift += oldIndex.getSegmentNum(newIndexStartTimeUs, periodDurationUs)
            - newIndexFirstSegmentNum;
      }
    }

    public int getFirstSegmentNum() {
      return segmentIndex.getFirstSegmentNum() + segmentNumShift;
    }

    public int getLastSegmentNum() {
      int lastSegmentNum = segmentIndex.getLastSegmentNum(periodDurationUs);
      if (lastSegmentNum == DashSegmentIndex.INDEX_UNBOUNDED) {
        return DashSegmentIndex.INDEX_UNBOUNDED;
      }
      return lastSegmentNum + segmentNumShift;
    }

    public long getSegmentStartTimeUs(int segmentNum) {
      return segmentIndex.getTimeUs(segmentNum - segmentNumShift);
    }

    public long getSegmentEndTimeUs(int segmentNum) {
      return getSegmentStartTimeUs(segmentNum)
          + segmentIndex.getDurationUs(segmentNum - segmentNumShift, periodDurationUs);
    }

    public int getSegmentNum(long positionUs) {
      return segmentIndex.getSegmentNum(positionUs, periodDurationUs) + segmentNumShift;
    }

    public RangedUri getSegmentUrl(int segmentNum) {
      return segmentIndex.getSegmentUrl(segmentNum - segmentNumShift);
    }

    private static boolean mimeTypeIsWebm(String mimeType) {
      return mimeType.startsWith(MimeTypes.VIDEO_WEBM) || mimeType.startsWith(MimeTypes.AUDIO_WEBM)
          || mimeType.startsWith(MimeTypes.APPLICATION_WEBM);
    }

    private static boolean mimeTypeIsRawText(String mimeType) {
      return MimeTypes.isText(mimeType) || MimeTypes.APPLICATION_TTML.equals(mimeType);
    }

  }

}
