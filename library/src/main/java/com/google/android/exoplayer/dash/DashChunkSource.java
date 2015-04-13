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
import com.google.android.exoplayer.TrackInfo;
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
import com.google.android.exoplayer.text.webvtt.WebvttParser;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.Clock;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.SystemClock;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * An {@link ChunkSource} for DASH streams.
 * <p>
 * This implementation currently supports fMP4, webm, and webvtt.
 */
public class DashChunkSource implements ChunkSource {

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

  private final TrackInfo trackInfo;
  private final DataSource dataSource;
  private final FormatEvaluator evaluator;
  private final Evaluation evaluation;
  private final Clock systemClock;
  private final StringBuilder headerBuilder;
  private final long liveEdgeLatencyUs;
  private final long elapsedRealtimeOffsetUs;
  private final int maxWidth;
  private final int maxHeight;

  private final Format[] formats;
  private final HashMap<String, RepresentationHolder> representationHolders;

  private final ManifestFetcher<MediaPresentationDescription> manifestFetcher;
  private final int adaptationSetIndex;
  private final int[] representationIndices;

  private DrmInitData drmInitData;
  private MediaPresentationDescription currentManifest;
  private boolean finishedCurrentManifest;

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
        new SystemClock(), 0, 0);
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
   *    server-side unix time and {@link SystemClock#elapsedRealtime()} in milliseconds, specified
   *    as the server's unix time minus the local elapsed time. It unknown, set to 0.
   */
  public DashChunkSource(ManifestFetcher<MediaPresentationDescription> manifestFetcher,
      int adaptationSetIndex, int[] representationIndices, DataSource dataSource,
      FormatEvaluator formatEvaluator, long liveEdgeLatencyMs, long elapsedRealtimeOffsetMs) {
    this(manifestFetcher, manifestFetcher.getManifest(), adaptationSetIndex, representationIndices,
        dataSource, formatEvaluator, new SystemClock(), liveEdgeLatencyMs * 1000,
        elapsedRealtimeOffsetMs * 1000);
  }

  /* package */ DashChunkSource(ManifestFetcher<MediaPresentationDescription> manifestFetcher,
      MediaPresentationDescription initialManifest, int adaptationSetIndex,
      int[] representationIndices, DataSource dataSource, FormatEvaluator formatEvaluator,
      Clock systemClock, long liveEdgeLatencyUs, long elapsedRealtimeOffsetUs) {
    this.manifestFetcher = manifestFetcher;
    this.currentManifest = initialManifest;
    this.adaptationSetIndex = adaptationSetIndex;
    this.representationIndices = representationIndices;
    this.dataSource = dataSource;
    this.evaluator = formatEvaluator;
    this.systemClock = systemClock;
    this.liveEdgeLatencyUs = liveEdgeLatencyUs;
    this.elapsedRealtimeOffsetUs = elapsedRealtimeOffsetUs;
    this.evaluation = new Evaluation();
    this.headerBuilder = new StringBuilder();

    drmInitData = getDrmInitData(currentManifest, adaptationSetIndex);
    Representation[] representations = getFilteredRepresentations(currentManifest,
        adaptationSetIndex, representationIndices);
    long periodDurationUs = (representations[0].periodDurationMs == TrackRenderer.UNKNOWN_TIME_US)
        ? TrackRenderer.UNKNOWN_TIME_US : representations[0].periodDurationMs * 1000;
    this.trackInfo = new TrackInfo(representations[0].format.mimeType, periodDurationUs);

    this.formats = new Format[representations.length];
    this.representationHolders = new HashMap<String, RepresentationHolder>();
    int maxWidth = 0;
    int maxHeight = 0;
    for (int i = 0; i < representations.length; i++) {
      formats[i] = representations[i].format;
      maxWidth = Math.max(formats[i].width, maxWidth);
      maxHeight = Math.max(formats[i].height, maxHeight);
      Extractor extractor = mimeTypeIsWebm(formats[i].mimeType) ? new WebmExtractor()
          : new FragmentedMp4Extractor();
      representationHolders.put(formats[i].id,
          new RepresentationHolder(representations[i], new ChunkExtractorWrapper(extractor)));
    }
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
    Arrays.sort(formats, new DecreasingBandwidthComparator());
  }

  @Override
  public final void getMaxVideoDimensions(MediaFormat out) {
    if (trackInfo.mimeType.startsWith("video")) {
      out.setMaxVideoDimensions(maxWidth, maxHeight);
    }
  }

  @Override
  public final TrackInfo getTrackInfo() {
    return trackInfo;
  }

  @Override
  public void enable() {
    evaluator.enable();
    if (manifestFetcher != null) {
      manifestFetcher.enable();
    }
  }

  @Override
  public void disable(List<? extends MediaChunk> queue) {
    evaluator.disable();
    if (manifestFetcher != null) {
      manifestFetcher.disable();
    }
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    if (manifestFetcher == null || !currentManifest.dynamic || fatalError != null) {
      return;
    }

    MediaPresentationDescription newManifest = manifestFetcher.getManifest();
    if (currentManifest != newManifest && newManifest != null) {
      Representation[] newRepresentations = DashChunkSource.getFilteredRepresentations(newManifest,
          adaptationSetIndex, representationIndices);
      for (Representation representation : newRepresentations) {
        RepresentationHolder representationHolder =
            representationHolders.get(representation.format.id);
        DashSegmentIndex oldIndex = representationHolder.segmentIndex;
        DashSegmentIndex newIndex = representation.getIndex();
        int newFirstSegmentNum = newIndex.getFirstSegmentNum();
        int segmentNumShift = oldIndex.getSegmentNum(newIndex.getTimeUs(newFirstSegmentNum))
            - newFirstSegmentNum;
        representationHolder.segmentNumShift += segmentNumShift;
        representationHolder.segmentIndex = newIndex;
      }
      currentManifest = newManifest;
      finishedCurrentManifest = false;
    }

    // TODO: This is a temporary hack to avoid constantly refreshing the MPD in cases where
    // minUpdatePeriod is set to 0. In such cases we shouldn't refresh unless there is explicit
    // signaling in the stream, according to:
    // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service/
    long minUpdatePeriod = currentManifest.minUpdatePeriod;
    if (minUpdatePeriod == 0) {
      minUpdatePeriod = 5000;
    }

    if (finishedCurrentManifest && (android.os.SystemClock.elapsedRealtime()
        > manifestFetcher.getManifestLoadTimestamp() + minUpdatePeriod)) {
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
      evaluator.evaluate(queue, playbackPositionUs, formats, evaluation);
    }
    Format selectedFormat = evaluation.format;
    out.queueSize = evaluation.queueSize;

    if (selectedFormat == null) {
      out.chunk = null;
      return;
    } else if (out.queueSize == queue.size() && out.chunk != null
        && out.chunk.format.id.equals(selectedFormat.id)) {
      // We already have a chunk, and the evaluation hasn't changed either the format or the size
      // of the queue. Leave unchanged.
      return;
    }

    RepresentationHolder representationHolder = representationHolders.get(selectedFormat.id);
    Representation selectedRepresentation = representationHolder.representation;
    DashSegmentIndex segmentIndex = representationHolder.segmentIndex;
    ChunkExtractorWrapper extractorWrapper = representationHolder.extractorWrapper;

    RangedUri pendingInitializationUri = null;
    RangedUri pendingIndexUri = null;

    if (representationHolder.format == null) {
      pendingInitializationUri = selectedRepresentation.getInitializationUri();
    }
    if (segmentIndex == null) {
      pendingIndexUri = selectedRepresentation.getIndexUri();
    }

    if (pendingInitializationUri != null || pendingIndexUri != null) {
      // We have initialization and/or index requests to make.
      Chunk initializationChunk = newInitializationChunk(pendingInitializationUri, pendingIndexUri,
          selectedRepresentation, extractorWrapper, dataSource, evaluation.trigger);
      lastChunkWasInitialization = true;
      out.chunk = initializationChunk;
      return;
    }

    long nowUs;
    if (elapsedRealtimeOffsetUs != 0) {
      nowUs = (systemClock.elapsedRealtime() * 1000) + elapsedRealtimeOffsetUs;
    } else {
      nowUs = System.currentTimeMillis() * 1000;
    }

    int firstAvailableSegmentNum = segmentIndex.getFirstSegmentNum();
    int lastAvailableSegmentNum = segmentIndex.getLastSegmentNum();
    boolean indexUnbounded = lastAvailableSegmentNum == DashSegmentIndex.INDEX_UNBOUNDED;
    if (indexUnbounded) {
      // The index is itself unbounded. We need to use the current time to calculate the range of
      // available segments.
      long liveEdgeTimestampUs = nowUs - currentManifest.availabilityStartTime * 1000;
      if (currentManifest.timeShiftBufferDepth != -1) {
        long bufferDepthUs = currentManifest.timeShiftBufferDepth * 1000;
        firstAvailableSegmentNum = Math.max(firstAvailableSegmentNum,
            segmentIndex.getSegmentNum(liveEdgeTimestampUs - bufferDepthUs));
      }
      // getSegmentNum(liveEdgeTimestampUs) will not be completed yet, so subtract one to get the
      // index of the last completed segment.
      lastAvailableSegmentNum = segmentIndex.getSegmentNum(liveEdgeTimestampUs) - 1;
    }

    int segmentNum;
    if (queue.isEmpty()) {
      if (currentManifest.dynamic) {
        seekPositionUs = getLiveSeekPosition(nowUs, indexUnbounded, segmentIndex.isExplicit());
      }
      segmentNum = segmentIndex.getSegmentNum(seekPositionUs);
    } else {
      MediaChunk previous = queue.get(out.queueSize - 1);
      segmentNum = previous.isLastChunk ? -1
          : previous.chunkIndex + 1 - representationHolder.segmentNumShift;
    }

    if (currentManifest.dynamic) {
      if (segmentNum < firstAvailableSegmentNum) {
        // This is before the first chunk in the current manifest.
        fatalError = new BehindLiveWindowException();
        return;
      } else if (segmentNum > lastAvailableSegmentNum) {
        // This chunk is beyond the last chunk in the current manifest. If the index is bounded
        // we'll need to refresh it. If it's unbounded we just need to wait for a while before
        // attempting to load the chunk.
        finishedCurrentManifest = !indexUnbounded;
        return;
      } else if (!indexUnbounded && segmentNum == lastAvailableSegmentNum) {
        // This is the last chunk in a dynamic bounded manifest. We'll need to refresh the manifest
        // to obtain the next chunk.
        finishedCurrentManifest = true;
      }
    }

    if (segmentNum == -1) {
      out.chunk = null;
      return;
    }

    Chunk nextMediaChunk = newMediaChunk(representationHolder, dataSource, segmentNum,
        evaluation.trigger);
    lastChunkWasInitialization = false;
    out.chunk = nextMediaChunk;
  }

  @Override
  public IOException getError() {
    return fatalError != null ? fatalError
        : (manifestFetcher != null ? manifestFetcher.getError() : null);
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof InitializationChunk) {
      InitializationChunk initializationChunk = (InitializationChunk) chunk;
      String formatId = initializationChunk.format.id;
      RepresentationHolder representationHolder = representationHolders.get(formatId);
      if (initializationChunk.hasFormat()) {
        representationHolder.format = initializationChunk.getFormat();
      }
      if (initializationChunk.hasSeekMap()) {
        representationHolder.segmentIndex = new DashWrappingSegmentIndex(
            (ChunkIndex) initializationChunk.getSeekMap(),
            initializationChunk.dataSpec.uri.toString());
      }
      if (initializationChunk.hasDrmInitData()) {
        drmInitData = initializationChunk.getDrmInitData();
      }
    }
  }

  @Override
  public void onChunkLoadError(Chunk chunk, Exception e) {
    // Do nothing.
  }

  private static boolean mimeTypeIsWebm(String mimeType) {
    return mimeType.startsWith(MimeTypes.VIDEO_WEBM) || mimeType.startsWith(MimeTypes.AUDIO_WEBM);
  }

  private Chunk newInitializationChunk(RangedUri initializationUri, RangedUri indexUri,
      Representation representation, ChunkExtractorWrapper extractor, DataSource dataSource,
      int trigger) {
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
    return new InitializationChunk(dataSource, dataSpec, trigger, representation.format, extractor);
  }

  private Chunk newMediaChunk(RepresentationHolder representationHolder, DataSource dataSource,
      int segmentNum, int trigger) {
    Representation representation = representationHolder.representation;
    DashSegmentIndex segmentIndex = representationHolder.segmentIndex;

    long startTimeUs = segmentIndex.getTimeUs(segmentNum);
    long endTimeUs = startTimeUs + segmentIndex.getDurationUs(segmentNum);

    int absoluteSegmentNum = segmentNum + representationHolder.segmentNumShift;
    boolean isLastSegment = !currentManifest.dynamic
        && segmentNum == segmentIndex.getLastSegmentNum();

    RangedUri segmentUri = segmentIndex.getSegmentUrl(segmentNum);
    DataSpec dataSpec = new DataSpec(segmentUri.getUri(), segmentUri.start, segmentUri.length,
        representation.getCacheKey());

    long presentationTimeOffsetUs = representation.presentationTimeOffsetUs;
    if (representation.format.mimeType.equals(MimeTypes.TEXT_VTT)) {
      if (representationHolder.vttHeaderOffsetUs != presentationTimeOffsetUs) {
        // Update the VTT header.
        headerBuilder.setLength(0);
        headerBuilder.append(WebvttParser.EXO_HEADER).append("=")
            .append(WebvttParser.OFFSET).append(presentationTimeOffsetUs).append("\n");
        representationHolder.vttHeader = headerBuilder.toString().getBytes();
        representationHolder.vttHeaderOffsetUs = presentationTimeOffsetUs;
      }
      return new SingleSampleMediaChunk(dataSource, dataSpec, Chunk.TRIGGER_INITIAL,
          representation.format, startTimeUs, endTimeUs, absoluteSegmentNum, isLastSegment, null,
          null, representationHolder.vttHeader);
    } else {
      return new ContainerMediaChunk(dataSource, dataSpec, trigger, representation.format,
          startTimeUs, endTimeUs, absoluteSegmentNum, isLastSegment, 0,
          representationHolder.extractorWrapper, representationHolder.format, drmInitData, true);
    }
  }

  /**
   * For live playbacks, determines the seek position that snaps playback to be
   * {@link #liveEdgeLatencyUs} behind the live edge of the current manifest
   *
   * @param nowUs An estimate of the current server time, in microseconds.
   * @param indexUnbounded True if the segment index for this source is unbounded. False otherwise.
   * @param indexExplicit True if the segment index is explicit. False otherwise.
   * @return The seek position in microseconds.
   */
  private long getLiveSeekPosition(long nowUs, boolean indexUnbounded, boolean indexExplicit) {
    long liveEdgeTimestampUs;
    if (indexUnbounded) {
      liveEdgeTimestampUs = nowUs - currentManifest.availabilityStartTime * 1000;
    } else {
      liveEdgeTimestampUs = Long.MIN_VALUE;
      for (RepresentationHolder representationHolder : representationHolders.values()) {
        DashSegmentIndex segmentIndex = representationHolder.segmentIndex;
        int lastSegmentNum = segmentIndex.getLastSegmentNum();
        long indexLiveEdgeTimestampUs = segmentIndex.getTimeUs(lastSegmentNum)
            + segmentIndex.getDurationUs(lastSegmentNum);
        liveEdgeTimestampUs = Math.max(liveEdgeTimestampUs, indexLiveEdgeTimestampUs);
      }
      if (!indexExplicit) {
        // Some segments defined by the index may not be available yet. Bound the calculated live
        // edge based on the elapsed time since the manifest became available.
        liveEdgeTimestampUs = Math.min(liveEdgeTimestampUs,
            nowUs - currentManifest.availabilityStartTime * 1000);
      }
    }
    return liveEdgeTimestampUs - liveEdgeLatencyUs;
  }

  private static Representation[] getFilteredRepresentations(MediaPresentationDescription manifest,
      int adaptationSetIndex, int[] representationIndices) {
    AdaptationSet adaptationSet = manifest.periods.get(0).adaptationSets.get(adaptationSetIndex);
    List<Representation> representations = adaptationSet.representations;
    if (representationIndices == null) {
      Representation[] filteredRepresentations = new Representation[representations.size()];
      representations.toArray(filteredRepresentations);
      return filteredRepresentations;
    } else {
      Representation[] filteredRepresentations = new Representation[representationIndices.length];
      for (int i = 0; i < representationIndices.length; i++) {
        filteredRepresentations[i] = representations.get(representationIndices[i]);
      }
      return filteredRepresentations;
    }
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
      for (ContentProtection contentProtection : adaptationSet.contentProtections) {
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
    return new MediaPresentationDescription(-1, duration, -1, false, -1, -1, null,
        Collections.singletonList(period));
  }

  private static class RepresentationHolder {

    public final Representation representation;
    public final ChunkExtractorWrapper extractorWrapper;

    public DashSegmentIndex segmentIndex;
    public MediaFormat format;

    public int segmentNumShift;
    public long vttHeaderOffsetUs;
    public byte[] vttHeader;

    public RepresentationHolder(Representation representation,
        ChunkExtractorWrapper extractorWrapper) {
      this.representation = representation;
      this.extractorWrapper = extractorWrapper;
      this.segmentIndex = representation.getIndex();
    }

  }

}
