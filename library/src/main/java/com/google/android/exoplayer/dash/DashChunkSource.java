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

import com.google.android.exoplayer.BehindLiveWindowException;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.ContainerMediaChunk;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.Format.DecreasingBandwidthComparator;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer.chunk.InitializationChunk;
import com.google.android.exoplayer.chunk.MediaChunk;
import com.google.android.exoplayer.chunk.SingleSampleMediaChunk;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.ContentProtection;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.RangedUri;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.extractor.ChunkIndex;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer.extractor.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.Clock;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.SystemClock;

import android.os.Handler;
import android.util.SparseArray;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * An {@link ChunkSource} for DASH streams.
 * <p>
 * This implementation currently supports fMP4, webm, and webvtt.
 * <p>
 * This implementation makes the following assumptions about multi-period manifests:
 * <ol>
 * <li>that new periods will contain the same representations as previous periods (i.e. no new or
 * missing representations) and</li>
 * <li>that representations are contiguous across multiple periods</li>
 * </ol>
 */
// TODO: handle cases where the above assumption are false
public class DashChunkSource implements ChunkSource {

  /**
   * Interface definition for a callback to be notified of {@link DashChunkSource} events.
   */
  public interface EventListener {

    /**
     * Invoked when the available seek range of the stream has changed.
     *
     * @param availableRange The range which specifies available content that can be seeked to.
     */
    public void onAvailableRangeChanged(TimeRange availableRange);

  }

  /**
   * Thrown when an AdaptationSet is missing from the MPD.
   */
  public static class NoAdaptationSetException extends IOException {

    public NoAdaptationSetException(String message) {
      super(message);
    }

  }

  /**
   * Specifies that we should process all tracks.
   */
  public static final int USE_ALL_TRACKS = -1;

  private final Handler eventHandler;
  private final EventListener eventListener;

  private final MediaFormat mediaFormat;
  private final DataSource dataSource;
  private final FormatEvaluator formatEvaluator;
  private final Evaluation evaluation;
  private final Clock systemClock;
  private final long liveEdgeLatencyUs;
  private final long elapsedRealtimeOffsetUs;
  private final int maxWidth;
  private final int maxHeight;

  private final SparseArray<PeriodHolder> periodHolders;

  private final ManifestFetcher<MediaPresentationDescription> manifestFetcher;
  private final int adaptationSetIndex;
  private final int[] representationIndices;

  private MediaPresentationDescription currentManifest;

  private int periodHolderNextIndex;

  private DrmInitData drmInitData;
  private TimeRange availableRange;
  private long[] availableRangeValues;

  private boolean startAtLiveEdge;
  private boolean lastChunkWasInitialization;
  private IOException fatalError;

  /**
   * Lightweight constructor to use for fixed duration content.
   *
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param formatEvaluator Selects from the available formats.
   * @param representations The representations to be considered by the source.
   */
  public DashChunkSource(DataSource dataSource, FormatEvaluator formatEvaluator,
      Representation... representations) {
    this(buildManifest(Arrays.asList(representations)), 0, null, dataSource, formatEvaluator);
  }

  /**
   * Lightweight constructor to use for fixed duration content.
   *
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param formatEvaluator Selects from the available formats.
   * @param representations The representations to be considered by the source.
   */
  public DashChunkSource(DataSource dataSource, FormatEvaluator formatEvaluator,
      List<Representation> representations) {
    this(buildManifest(representations), 0, null, dataSource, formatEvaluator);
  }

  /**
   * Constructor to use for fixed duration content.
   *
   * @param manifest The manifest.
   * @param adaptationSetIndex The index of the adaptation set that should be used.
   * @param representationIndices The indices of the representations within the adaptations set
   *     that should be used. May be null if all representations within the adaptation set should
   *     be considered.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param formatEvaluator Selects from the available formats.
   */
  public DashChunkSource(MediaPresentationDescription manifest, int adaptationSetIndex,
      int[] representationIndices, DataSource dataSource, FormatEvaluator formatEvaluator) {
    this(null, manifest, adaptationSetIndex, representationIndices, dataSource, formatEvaluator,
        new SystemClock(), 0, 0, false, null, null);
  }

  /**
   * Constructor to use for live streaming.
   * <p>
   * May also be used for fixed duration content, in which case the call is equivalent to calling
   * the other constructor, passing {@code manifestFetcher.getManifest()} is the first argument.
   *
   * @param manifestFetcher A fetcher for the manifest, which must have already successfully
   *     completed an initial load.
   * @param adaptationSetIndex The index of the adaptation set that should be used.
   * @param representationIndices The indices of the representations within the adaptations set
   *     that should be used. May be null if all representations within the adaptation set should
   *     be considered.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param formatEvaluator Selects from the available formats.
   * @param liveEdgeLatencyMs For live streams, the number of milliseconds that the playback should
   *     lag behind the "live edge" (i.e. the end of the most recently defined media in the
   *     manifest). Choosing a small value will minimize latency introduced by the player, however
   *     note that the value sets an upper bound on the length of media that the player can buffer.
   *     Hence a small value may increase the probability of rebuffering and playback failures.
   * @param elapsedRealtimeOffsetMs If known, an estimate of the instantaneous difference between
   *     server-side unix time and {@link SystemClock#elapsedRealtime()} in milliseconds, specified
   *     as the server's unix time minus the local elapsed time. It unknown, set to 0.
   * @param eventHandler A handler to use when delivering events to {@code EventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public DashChunkSource(ManifestFetcher<MediaPresentationDescription> manifestFetcher,
      int adaptationSetIndex, int[] representationIndices, DataSource dataSource,
      FormatEvaluator formatEvaluator, long liveEdgeLatencyMs, long elapsedRealtimeOffsetMs,
      Handler eventHandler, EventListener eventListener) {
    this(manifestFetcher, manifestFetcher.getManifest(), adaptationSetIndex, representationIndices,
        dataSource, formatEvaluator, new SystemClock(), liveEdgeLatencyMs * 1000,
        elapsedRealtimeOffsetMs * 1000, true, eventHandler, eventListener);
  }

  /**
   * Constructor to use for live DVR streaming.
   *
   * @param manifestFetcher A fetcher for the manifest, which must have already successfully
   *     completed an initial load.
   * @param adaptationSetIndex The index of the adaptation set that should be used.
   * @param representationIndices The indices of the representations within the adaptations set
   *     that should be used. May be null if all representations within the adaptation set should
   *     be considered.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param formatEvaluator Selects from the available formats.
   * @param liveEdgeLatencyMs For live streams, the number of milliseconds that the playback should
   *     lag behind the "live edge" (i.e. the end of the most recently defined media in the
   *     manifest). Choosing a small value will minimize latency introduced by the player, however
   *     note that the value sets an upper bound on the length of media that the player can buffer.
   *     Hence a small value may increase the probability of rebuffering and playback failures.
   * @param elapsedRealtimeOffsetMs If known, an estimate of the instantaneous difference between
   *     server-side unix time and {@link SystemClock#elapsedRealtime()} in milliseconds, specified
   *     as the server's unix time minus the local elapsed time. It unknown, set to 0.
   * @param startAtLiveEdge True if the stream should start at the live edge; false if it should
   *     at the beginning of the live window.
   * @param eventHandler A handler to use when delivering events to {@code EventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public DashChunkSource(ManifestFetcher<MediaPresentationDescription> manifestFetcher,
      int adaptationSetIndex, int[] representationIndices, DataSource dataSource,
      FormatEvaluator formatEvaluator, long liveEdgeLatencyMs, long elapsedRealtimeOffsetMs,
      boolean startAtLiveEdge, Handler eventHandler, EventListener eventListener) {
    this(manifestFetcher, manifestFetcher.getManifest(), adaptationSetIndex, representationIndices,
        dataSource, formatEvaluator, new SystemClock(), liveEdgeLatencyMs * 1000,
        elapsedRealtimeOffsetMs * 1000, startAtLiveEdge, eventHandler, eventListener);
  }

  /* package */ DashChunkSource(ManifestFetcher<MediaPresentationDescription> manifestFetcher,
      MediaPresentationDescription initialManifest, int adaptationSetIndex,
      int[] representationIndices, DataSource dataSource, FormatEvaluator formatEvaluator,
      Clock systemClock, long liveEdgeLatencyUs, long elapsedRealtimeOffsetUs,
      boolean startAtLiveEdge, Handler eventHandler, EventListener eventListener) {
    this.manifestFetcher = manifestFetcher;
    this.currentManifest = initialManifest;
    this.adaptationSetIndex = adaptationSetIndex;
    this.representationIndices = representationIndices;
    this.dataSource = dataSource;
    this.formatEvaluator = formatEvaluator;
    this.systemClock = systemClock;
    this.liveEdgeLatencyUs = liveEdgeLatencyUs;
    this.elapsedRealtimeOffsetUs = elapsedRealtimeOffsetUs;
    this.startAtLiveEdge = startAtLiveEdge;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.evaluation = new Evaluation();
    this.availableRangeValues = new long[2];

    drmInitData = getDrmInitData(currentManifest, adaptationSetIndex);
    periodHolders = new SparseArray<>();

    processManifest(currentManifest);

    String mimeType = "";
    long totalDurationUs = 0;
    int maxHeight = 0;
    int maxWidth = 0;

    for (int i = 0; i < periodHolders.size(); i++) {
      PeriodHolder periodHolder = periodHolders.valueAt(i);
      if (totalDurationUs != TrackRenderer.UNKNOWN_TIME_US) {
        if (periodHolder.durationUs == TrackRenderer.UNKNOWN_TIME_US) {
          totalDurationUs = TrackRenderer.UNKNOWN_TIME_US;
        } else {
          totalDurationUs += periodHolder.durationUs;
        }
      }
      mimeType = periodHolder.mimeType;
      maxHeight = Math.max(maxHeight, periodHolder.maxHeight);
      maxWidth = Math.max(maxWidth, periodHolder.maxWidth);
    }
    // TODO: Remove this and pass proper formats instead (b/22996976).
    this.mediaFormat = MediaFormat.createFormatForMimeType(mimeType, MediaFormat.NO_VALUE,
        totalDurationUs);
    this.maxHeight = maxHeight;
    this.maxWidth = maxWidth;
  }

  @Override
  public final MediaFormat getWithMaxVideoDimensions(MediaFormat format) {
    return MimeTypes.isVideo(mediaFormat.mimeType)
        ? format.copyWithMaxVideoDimensions(maxWidth, maxHeight) : format;
  }

  @Override
  public boolean prepare() {
    return true;
  }

  @Override
  public int getTrackCount() {
    return 1;
  }

  @Override
  public final MediaFormat getFormat(int track) {
    return mediaFormat;
  }

  // VisibleForTesting
  /* package */ TimeRange getAvailableRange() {
    return availableRange;
  }

  @Override
  public void enable(int track) {
    fatalError = null;
    formatEvaluator.enable();
    if (manifestFetcher != null) {
      manifestFetcher.enable();
    }
    updateAvailableBounds(getNowUs());
  }

  @Override
  public void disable(List<? extends MediaChunk> queue) {
    formatEvaluator.disable();
    if (manifestFetcher != null) {
      manifestFetcher.disable();
    }
    availableRange = null;
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    if (manifestFetcher == null || !currentManifest.dynamic || fatalError != null) {
      return;
    }

    MediaPresentationDescription newManifest = manifestFetcher.getManifest();
    if (currentManifest != newManifest && newManifest != null) {
      processManifest(newManifest);
      updateAvailableBounds(getNowUs());
    }

    // TODO: This is a temporary hack to avoid constantly refreshing the MPD in cases where
    // minUpdatePeriod is set to 0. In such cases we shouldn't refresh unless there is explicit
    // signaling in the stream, according to:
    // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service/
    long minUpdatePeriod = currentManifest.minUpdatePeriod;
    if (minUpdatePeriod == 0) {
      minUpdatePeriod = 5000;
    }

    if (android.os.SystemClock.elapsedRealtime()
        > manifestFetcher.getManifestLoadStartTimestamp() + minUpdatePeriod) {
      manifestFetcher.requestRefresh();
    }
  }

  @Override
  public final void getChunkOperation(List<? extends MediaChunk> queue, long seekPositionUs,
      long playbackPositionUs, ChunkOperationHolder out) {
    if (fatalError != null) {
      out.chunk = null;
      return;
    }

    evaluation.queueSize = queue.size();
    if (evaluation.format == null || !lastChunkWasInitialization) {
      PeriodHolder periodHolder = null;
      if (!queue.isEmpty()) {
        periodHolder = periodHolders.get(queue.get(queue.size() - 1).parentId);
      }
      if (periodHolder == null) {
        periodHolder = periodHolders.valueAt(0);
      }
      formatEvaluator.evaluate(queue, playbackPositionUs, periodHolder.formats, evaluation);
    }
    Format selectedFormat = evaluation.format;
    out.queueSize = evaluation.queueSize;

    if (selectedFormat == null) {
      out.chunk = null;
      return;
    } else if (out.queueSize == queue.size() && out.chunk != null
        && out.chunk.format.equals(selectedFormat)) {
      // We already have a chunk, and the evaluation hasn't changed either the format or the size
      // of the queue. Leave unchanged.
      return;
    }

    // In all cases where we return before instantiating a new chunk, we want out.chunk to be null.
    out.chunk = null;

    if (currentManifest.dynamic
        && periodHolders.valueAt(periodHolders.size() - 1).isIndexUnbounded()) {
      // Manifests with unbounded indexes aren't updated regularly, so we need to update the
      // segment bounds before use to ensure that they are accurate to the current time
      updateAvailableBounds(getNowUs());
    }
    availableRangeValues = availableRange.getCurrentBoundsUs(availableRangeValues);

    long segmentStartTimeUs;
    int segmentNum = -1;
    boolean startingNewPeriod = false;
    PeriodHolder periodHolder;

    if (queue.isEmpty()) {
      if (currentManifest.dynamic) {
        if (startAtLiveEdge) {
          // We want live streams to start at the live edge instead of the beginning of the
          // manifest
          seekPositionUs = Math.max(availableRangeValues[0],
              availableRangeValues[1] - liveEdgeLatencyUs);
        } else {
          // we subtract 1 from the upper bound because it's exclusive for that bound
          seekPositionUs = Math.min(seekPositionUs, availableRangeValues[1] - 1);
          seekPositionUs = Math.max(seekPositionUs, availableRangeValues[0]);
        }
      }

      periodHolder = findPeriodHolder(seekPositionUs);
      segmentStartTimeUs = seekPositionUs;
      startingNewPeriod = true;
    } else {
      if (startAtLiveEdge) {
        // now that we know the player is consuming media chunks (since the queue isn't empty),
        // set startAtLiveEdge to false so that the user can perform seek operations
        startAtLiveEdge = false;
      }

      MediaChunk previous = queue.get(out.queueSize - 1);
      if (previous.isLastChunk) {
        // We've reached the end of the stream.
        return;
      }

      segmentNum = previous.chunkIndex + 1;
      segmentStartTimeUs = previous.endTimeUs;

      if (currentManifest.dynamic) {
        if (segmentStartTimeUs < availableRangeValues[0]) {
          // This is before the first chunk in the current manifest.
          fatalError = new BehindLiveWindowException();
          return;
        } else if (segmentStartTimeUs >= availableRangeValues[1]) {
          // This chunk is beyond the last chunk in the current manifest. If the index is bounded
          // we'll need to wait until it's refreshed. If it's unbounded we just need to wait for a
          // while before attempting to load the chunk.
          return;
        }
      }

      periodHolder = periodHolders.get(previous.parentId);
      if (periodHolder == null) {
        // the previous chunk was from a period that's no longer on the manifest, therefore the
        // next chunk must be the first one in the first period that's still on the manifest
        // (note that we can't actually update the segmentNum yet because the new period might
        // have a different sequence and it's segmentIndex might not have been loaded yet)
        periodHolder = periodHolders.valueAt(0);
        startingNewPeriod = true;
      } else if (!periodHolder.isIndexUnbounded()
          && segmentStartTimeUs >= periodHolder.getAvailableEndTimeUs()) {
        // we reached the end of a period, start the next one (note that we can't actually
        // update the segmentNum yet because the new period might have a different
        // sequence and it's segmentIndex might not have been loaded yet)
        periodHolder = periodHolders.get(previous.parentId + 1);
        startingNewPeriod = true;
      }
    }

    RepresentationHolder representationHolder =
        periodHolder.representationHolders.get(selectedFormat.id);
    Representation selectedRepresentation = representationHolder.representation;
    ChunkExtractorWrapper extractorWrapper = representationHolder.extractorWrapper;

    RangedUri pendingInitializationUri = null;
    RangedUri pendingIndexUri = null;

    MediaFormat mediaFormat = representationHolder.mediaFormat;
    if (mediaFormat == null) {
      pendingInitializationUri = selectedRepresentation.getInitializationUri();
    }
    if (representationHolder.segmentIndex == null) {
      pendingIndexUri = selectedRepresentation.getIndexUri();
    }

    if (pendingInitializationUri != null || pendingIndexUri != null) {
      // We have initialization and/or index requests to make.
      Chunk initializationChunk = newInitializationChunk(pendingInitializationUri, pendingIndexUri,
          selectedRepresentation, extractorWrapper, dataSource, periodHolder.manifestIndex,
          evaluation.trigger);
      lastChunkWasInitialization = true;
      out.chunk = initializationChunk;
      return;
    }

    if (startingNewPeriod) {
      if (queue.isEmpty()) {
        // when starting a new period (or beginning playback for the first time), the segment
        // numbering might have been reset, so we'll need to determine the correct number from
        // the representation holder itself
        segmentNum = representationHolder.getSegmentNum(segmentStartTimeUs);
      } else {
        segmentNum = representationHolder.getFirstAvailableSegmentNum();
      }
    }

    Chunk nextMediaChunk = newMediaChunk(periodHolder, representationHolder, dataSource,
        mediaFormat, segmentNum, evaluation.trigger);
    lastChunkWasInitialization = false;
    out.chunk = nextMediaChunk;
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else if (manifestFetcher != null) {
      manifestFetcher.maybeThrowError();
    }
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof InitializationChunk) {
      InitializationChunk initializationChunk = (InitializationChunk) chunk;
      String formatId = initializationChunk.format.id;
      PeriodHolder periodHolder = periodHolders.get(initializationChunk.parentId);
      if (periodHolder == null) {
        // period for this initialization chunk may no longer be on the manifest
        return;
      }

      RepresentationHolder representationHolder = periodHolder.representationHolders.get(formatId);
      if (initializationChunk.hasFormat()) {
        representationHolder.mediaFormat = initializationChunk.getFormat();
      }
      if (initializationChunk.hasSeekMap()) {
        representationHolder.segmentIndex = new DashWrappingSegmentIndex(
            (ChunkIndex) initializationChunk.getSeekMap(),
            initializationChunk.dataSpec.uri.toString(),
            periodHolder.startTimeUs);
      }

      // The null check avoids overwriting drmInitData obtained from the manifest with drmInitData
      // obtained from the stream, as per DASH IF Interoperability Recommendations V3.0, 7.5.3.
      if (drmInitData == null && initializationChunk.hasDrmInitData()) {
        drmInitData = initializationChunk.getDrmInitData();
      }
    }
  }

  @Override
  public void onChunkLoadError(Chunk chunk, Exception e) {
    // Do nothing.
  }

  private void updateAvailableBounds(long nowUs) {
    PeriodHolder firstPeriod = periodHolders.valueAt(0);
    long earliestAvailablePosition = firstPeriod.getAvailableStartTimeUs();
    PeriodHolder lastPeriod = periodHolders.valueAt(periodHolders.size() - 1);
    boolean isManifestUnbounded = lastPeriod.isIndexUnbounded();
    long latestAvailablePosition;
    if (!currentManifest.dynamic || !isManifestUnbounded) {
      latestAvailablePosition = lastPeriod.getAvailableEndTimeUs();
    } else {
      latestAvailablePosition = TrackRenderer.UNKNOWN_TIME_US;
    }

    if (currentManifest.dynamic) {
      if (isManifestUnbounded) {
        latestAvailablePosition = nowUs - currentManifest.availabilityStartTime * 1000;
      } else if (!lastPeriod.isIndexExplicit()) {
        // Some segments defined by the index may not be available yet. Bound the calculated live
        // edge based on the elapsed time since the manifest became available.
        latestAvailablePosition = Math.min(latestAvailablePosition,
            nowUs - currentManifest.availabilityStartTime * 1000);
      }

      // if we have a limited timeshift buffer, we need to adjust the earliest seek position so
      // that it doesn't start before the buffer
      if (currentManifest.timeShiftBufferDepth != -1) {
        long bufferDepthUs = currentManifest.timeShiftBufferDepth * 1000;
        earliestAvailablePosition = Math.max(earliestAvailablePosition,
            latestAvailablePosition - bufferDepthUs);
      }
    }

    TimeRange newAvailableRange = new TimeRange(TimeRange.TYPE_SNAPSHOT, earliestAvailablePosition,
        latestAvailablePosition);
    if (availableRange == null || !availableRange.equals(newAvailableRange)) {
      availableRange = newAvailableRange;
      notifyAvailableRangeChanged(availableRange);
    }
  }

  private static boolean mimeTypeIsWebm(String mimeType) {
    return mimeType.startsWith(MimeTypes.VIDEO_WEBM) || mimeType.startsWith(MimeTypes.AUDIO_WEBM);
  }

  private Chunk newInitializationChunk(RangedUri initializationUri, RangedUri indexUri,
      Representation representation, ChunkExtractorWrapper extractor, DataSource dataSource,
      int manifestIndex, int trigger) {
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
        representation.getCacheKey());
    return new InitializationChunk(dataSource, dataSpec, trigger, representation.format,
        extractor, manifestIndex);
  }

  private Chunk newMediaChunk(PeriodHolder periodHolder, RepresentationHolder representationHolder,
      DataSource dataSource, MediaFormat mediaFormat, int segmentNum, int trigger) {
    Representation representation = representationHolder.representation;

    long startTimeUs = representationHolder.getSegmentStartTimeUs(segmentNum);
    long endTimeUs = representationHolder.getSegmentEndTimeUs(segmentNum);
    boolean isLastSegment = !currentManifest.dynamic
        && periodHolders.valueAt(periodHolders.size() - 1) == periodHolder
        && representationHolder.isLastSegment(segmentNum);

    RangedUri segmentUri = representationHolder.getSegmentUrl(segmentNum);
    DataSpec dataSpec = new DataSpec(segmentUri.getUri(), segmentUri.start, segmentUri.length,
        representation.getCacheKey());

    long sampleOffsetUs = periodHolder.startTimeUs - representation.presentationTimeOffsetUs;
    if (representation.format.mimeType.equals(MimeTypes.TEXT_VTT)) {
      return new SingleSampleMediaChunk(dataSource, dataSpec, Chunk.TRIGGER_INITIAL,
          representation.format, startTimeUs, endTimeUs, segmentNum, isLastSegment,
          MediaFormat.createTextFormat(MimeTypes.TEXT_VTT, MediaFormat.NO_VALUE,
          representation.format.language), null, periodHolder.manifestIndex);
    } else {
      boolean isMediaFormatFinal = (mediaFormat != null);
      return new ContainerMediaChunk(dataSource, dataSpec, trigger, representation.format,
          startTimeUs, endTimeUs, segmentNum, isLastSegment, sampleOffsetUs,
          representationHolder.extractorWrapper, mediaFormat, drmInitData, isMediaFormatFinal,
          periodHolder.manifestIndex);
    }
  }

  private long getNowUs() {
    if (elapsedRealtimeOffsetUs != 0) {
      return (systemClock.elapsedRealtime() * 1000) + elapsedRealtimeOffsetUs;
    } else {
      return System.currentTimeMillis() * 1000;
    }
  }

  private static String getMediaMimeType(Representation representation) {
    String mimeType = representation.format.mimeType;
    if (MimeTypes.APPLICATION_MP4.equals(representation.format.mimeType)
        && "stpp".equals(representation.format.codecs)) {
      return MimeTypes.APPLICATION_TTML;
    }
    // TODO: Use codecs to determine media mime type for other formats too.
    return mimeType;
  }

  private static DrmInitData getDrmInitData(MediaPresentationDescription manifest,
      int adaptationSetIndex) {
    AdaptationSet adaptationSet = manifest.periods.get(0).adaptationSets.get(adaptationSetIndex);
    String drmInitMimeType = mimeTypeIsWebm(adaptationSet.representations.get(0).format.mimeType)
        ? MimeTypes.VIDEO_WEBM : MimeTypes.VIDEO_MP4;
    if (adaptationSet.contentProtections.isEmpty()) {
      return null;
    } else {
      DrmInitData.Mapped drmInitData = null;
      for (int i = 0; i < adaptationSet.contentProtections.size(); i++) {
        ContentProtection contentProtection = adaptationSet.contentProtections.get(i);
        if (contentProtection.uuid != null && contentProtection.data != null) {
          if (drmInitData == null) {
            drmInitData = new DrmInitData.Mapped(drmInitMimeType);
          }
          drmInitData.put(contentProtection.uuid, contentProtection.data);
        }
      }
      return drmInitData;
    }
  }

  private static MediaPresentationDescription buildManifest(List<Representation> representations) {
    Representation firstRepresentation = representations.get(0);
    AdaptationSet adaptationSet = new AdaptationSet(0, AdaptationSet.TYPE_UNKNOWN, representations);
    Period period = new Period(null, firstRepresentation.periodStartMs,
        firstRepresentation.periodDurationMs, Collections.singletonList(adaptationSet));
    long duration = firstRepresentation.periodDurationMs - firstRepresentation.periodStartMs;
    return new MediaPresentationDescription(-1, duration, -1, false, -1, -1, null, null,
        Collections.singletonList(period));
  }

  private PeriodHolder findPeriodHolder(long positionUs) {
    // if positionUs is before the first period, return the first period
    if (positionUs < periodHolders.valueAt(0).getAvailableStartTimeUs()) {
      return periodHolders.valueAt(0);
    }

    for (int i = 0; i < periodHolders.size() - 1; i++) {
      PeriodHolder periodHolder = periodHolders.valueAt(i);
      if (positionUs < periodHolder.getAvailableEndTimeUs()) {
        return periodHolder;
      }
    }

    // positionUs is within or after the last period
    return periodHolders.valueAt(periodHolders.size() - 1);
  }

  private void processManifest(MediaPresentationDescription manifest) {
    List<Period> newPeriods = manifest.periods;

    // Remove old periods.
    Period firstPeriod = newPeriods.get(0);
    while (periodHolders.size() > 0
        && periodHolders.valueAt(0).startTimeUs < firstPeriod.startMs * 1000) {
      PeriodHolder periodHolder = periodHolders.valueAt(0);
      // TODO: Use periodHolders.removeAt(0) if the minimum API level is ever increased to 11.
      periodHolders.remove(periodHolder.manifestIndex);
    }

    // Update existing periods.
    try {
      for (int i = 0; i < periodHolders.size(); i++) {
        periodHolders.valueAt(i).updatePeriod(newPeriods.get(i));
      }
    } catch (BehindLiveWindowException e) {
      fatalError = e;
      return;
    }

    // Add new periods.
    for (int i = periodHolders.size(); i < newPeriods.size(); i++) {
      Period period = newPeriods.get(i);
      PeriodHolder periodHolder = new PeriodHolder(periodHolderNextIndex, period,
          adaptationSetIndex, representationIndices);
      periodHolders.put(periodHolderNextIndex, periodHolder);
      periodHolderNextIndex++;
    }

    currentManifest = manifest;
  }

  private void notifyAvailableRangeChanged(final TimeRange seekRange) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onAvailableRangeChanged(seekRange);
        }
      });
    }
  }

  private static final class RepresentationHolder {

    public final ChunkExtractorWrapper extractorWrapper;

    public Representation representation;
    public DashSegmentIndex segmentIndex;
    public MediaFormat mediaFormat;

    private int segmentNumShift;

    public RepresentationHolder(Representation representation,
        ChunkExtractorWrapper extractorWrapper) {
      this.representation = representation;
      this.extractorWrapper = extractorWrapper;
      this.segmentIndex = representation.getIndex();
    }

    public void updateRepresentation(Representation newRepresentation)
        throws BehindLiveWindowException{
      DashSegmentIndex oldIndex = representation.getIndex();
      DashSegmentIndex newIndex = newRepresentation.getIndex();
      representation = newRepresentation;
      if (newIndex == null) {
        return;
      }

      segmentIndex = newIndex;
      int oldIndexLastSegmentNum = oldIndex.getLastSegmentNum();
      long oldIndexEndTimeUs = oldIndex.getTimeUs(oldIndexLastSegmentNum)
          + oldIndex.getDurationUs(oldIndexLastSegmentNum);
      int newIndexFirstSegmentNum = newIndex.getFirstSegmentNum();
      long newIndexStartTimeUs = newIndex.getTimeUs(newIndexFirstSegmentNum);
      if (oldIndexEndTimeUs == newIndexStartTimeUs) {
        // The new manifest continues where the old one ended, with no overlap.
        segmentNumShift += oldIndex.getLastSegmentNum() + 1 - newIndexFirstSegmentNum;
      } else if (oldIndexEndTimeUs < newIndexStartTimeUs) {
        // There's a gap between the old manifest and the new one which means we've slipped
        // behind the live window and can't proceed.
        throw new BehindLiveWindowException();
      } else {
        // The new manifest overlaps with the old one.
        segmentNumShift += oldIndex.getSegmentNum(newIndexStartTimeUs) - newIndexFirstSegmentNum;
      }
    }

    public int getSegmentNum(long positionUs) {
      return segmentIndex.getSegmentNum(positionUs) + segmentNumShift;
    }

    public long getSegmentStartTimeUs(int segmentNum) {
      return segmentIndex.getTimeUs(segmentNum - segmentNumShift);
    }

    public long getSegmentEndTimeUs(int segmentNum) {
      return getSegmentStartTimeUs(segmentNum)
          + segmentIndex.getDurationUs(segmentNum - segmentNumShift);
    }

    public boolean isLastSegment(int segmentNum) {
      return (segmentNum - segmentNumShift) == segmentIndex.getLastSegmentNum();
    }

    public int getFirstAvailableSegmentNum() {
      return segmentIndex.getFirstSegmentNum() + segmentNumShift;
    }

    public RangedUri getSegmentUrl(int segmentNum) {
      return segmentIndex.getSegmentUrl(segmentNum - segmentNumShift);
    }

  }

  private static final class PeriodHolder {

    public final int manifestIndex;
    public final long startTimeUs;
    public final long durationUs;
    public final String mimeType;
    public final Format[] formats;
    public final HashMap<String, RepresentationHolder> representationHolders;

    private final int adaptationSetIndex;
    private final int[] representationIndices;
    private final int maxWidth;
    private final int maxHeight;

    private boolean indexIsUnbounded;
    private boolean indexIsExplicit;
    private long availableStartTimeUs;
    private long availableEndTimeUs;

    public PeriodHolder(int manifestIndex, Period period, int adaptationSetIndex,
        int[] representationIndices) {
      this.manifestIndex = manifestIndex;
      this.adaptationSetIndex = adaptationSetIndex;
      this.representationIndices = representationIndices;

      List<Representation> periodRepresentations =
          period.adaptationSets.get(adaptationSetIndex).representations;
      int representationCount = representationIndices != null ? representationIndices.length
          : periodRepresentations.size();
      formats = new Format[representationCount];
      representationHolders = new HashMap<>(representationCount);

      int maxWidth = -1;
      int maxHeight = -1;
      String mimeType = "";
      for (int i = 0; i < representationCount; i++) {
        int representationIndex = representationIndices != null ? representationIndices[i] : i;
        Representation representation = periodRepresentations.get(representationIndex);
        formats[i] = representation.format;
        mimeType = getMediaMimeType(representation);
        maxWidth = Math.max(formats[i].width, maxWidth);
        maxHeight = Math.max(formats[i].height, maxHeight);
        Extractor extractor = mimeTypeIsWebm(formats[i].mimeType) ? new WebmExtractor()
          : new FragmentedMp4Extractor();
        RepresentationHolder representationHolder =
            new RepresentationHolder(representation, new ChunkExtractorWrapper(extractor));
        representationHolders.put(formats[i].id, representationHolder);
      }
      this.maxWidth = maxWidth;
      this.maxHeight = maxHeight;
      this.mimeType = mimeType;

      startTimeUs = period.startMs * 1000;
      long durationMs = period.durationMs;
      if (durationMs == -1) {
        durationUs = TrackRenderer.UNKNOWN_TIME_US;
      } else {
        durationUs = durationMs * 1000;
      }

      Arrays.sort(formats, new DecreasingBandwidthComparator());
      updateRepresentationIndependentProperties();
    }

    public void updatePeriod(Period period) throws BehindLiveWindowException {
      List<Representation> representations =
          period.adaptationSets.get(adaptationSetIndex).representations;
      int representationCount = formats.length;
      for (int i = 0; i < representationCount; i++) {
        int representationIndex = representationIndices != null ? representationIndices[i] : i;
        Representation representation = representations.get(representationIndex);
        representationHolders.get(representation.format.id).updateRepresentation(representation);
      }
      updateRepresentationIndependentProperties();
    }

    public long getAvailableStartTimeUs() {
      return availableStartTimeUs;
    }

    public long getAvailableEndTimeUs() {
      if (isIndexUnbounded()) {
        throw new IllegalStateException("Period has unbounded index");
      }
      return availableEndTimeUs;
    }

    public boolean isIndexUnbounded() {
      return indexIsUnbounded;
    }

    public boolean isIndexExplicit() {
      return indexIsExplicit;
    }

    private void updateRepresentationIndependentProperties() {
      // Arbitrarily use the first representation to derive representation independent properties.
      Representation representation = representationHolders.get(formats[0].id).representation;
      DashSegmentIndex segmentIndex = representation.getIndex();
      if (segmentIndex != null) {
        int lastSegmentNum = segmentIndex.getLastSegmentNum();
        indexIsUnbounded = lastSegmentNum == DashSegmentIndex.INDEX_UNBOUNDED;
        indexIsExplicit = segmentIndex.isExplicit();
        availableStartTimeUs = segmentIndex.getTimeUs(segmentIndex.getFirstSegmentNum());
        if (!indexIsUnbounded) {
          availableEndTimeUs = segmentIndex.getTimeUs(lastSegmentNum)
              + segmentIndex.getDurationUs(lastSegmentNum);
        }
      } else {
        indexIsUnbounded = false;
        indexIsExplicit = true;
        availableStartTimeUs = startTimeUs;
        availableEndTimeUs = startTimeUs + durationUs;
      }
    }

  }

}
