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
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.Format.DecreasingBandwidthComparator;
import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.TimeRange.DynamicTimeRange;
import com.google.android.exoplayer.TimeRange.StaticTimeRange;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.ContainerMediaChunk;
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
import java.util.List;

/**
 * An {@link ChunkSource} for DASH streams.
 * <p>
 * This implementation currently supports fMP4, webm, webvtt and ttml.
 * <p>
 * This implementation makes the following assumptions about multi-period manifests:
 * <ol>
 * <li>that new periods will contain the same representations as previous periods (i.e. no new or
 * missing representations) and</li>
 * <li>that representations are contiguous across multiple periods</li>
 * </ol>
 */
// TODO: handle cases where the above assumption are false
// TODO[REFACTOR]: Handle multiple adaptation sets of the same type (at a higher level).
public class DashChunkSource implements ChunkSource {

  /**
   * Interface definition for a callback to be notified of {@link DashChunkSource} events.
   */
  public interface EventListener {

    /**
     * Invoked when the available seek range of the stream has changed.
     *
     * @param sourceId The id of the reporting {@link DashChunkSource}.
     * @param availableRange The range which specifies available content that can be seeked to.
     */
    public void onAvailableRangeChanged(int sourceId, TimeRange availableRange);

  }

  /**
   * Thrown when an AdaptationSet is missing from the MPD.
   */
  public static class NoAdaptationSetException extends IOException {

    public NoAdaptationSetException(String message) {
      super(message);
    }

  }

  private final Handler eventHandler;
  private final EventListener eventListener;

  private final int adaptationSetType;
  private final DataSource dataSource;
  private final FormatEvaluator adaptiveFormatEvaluator;
  private final Evaluation evaluation;
  private final ManifestFetcher<MediaPresentationDescription> manifestFetcher;
  private final SparseArray<PeriodHolder> periodHolders;
  private final Clock systemClock;
  private final long liveEdgeLatencyUs;
  private final long elapsedRealtimeOffsetUs;
  private final long[] availableRangeValues;
  private final int eventSourceId;

  private boolean live;
  private long durationUs;
  private MediaPresentationDescription currentManifest;
  private MediaPresentationDescription processedManifest;
  private int nextPeriodHolderIndex;
  private TimeRange availableRange;
  private boolean startAtLiveEdge;
  private boolean lastChunkWasInitialization;
  private IOException fatalError;

  // Properties of exposed tracks.
  private int adaptationSetIndex;
  private TrackGroup trackGroup;

  // Properties of enabled tracks.
  private Format[] enabledFormats;
  private boolean[] adaptiveFormatBlacklistFlags;

  /**
   * @param manifestFetcher A fetcher for the manifest.
   * @param adaptationSetType The type of the adaptation set exposed by this source. One of
   *     {@link AdaptationSet#TYPE_AUDIO}, {@link AdaptationSet#TYPE_VIDEO} and
   *     {@link AdaptationSet#TYPE_TEXT}.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
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
   * @param eventSourceId An identifier that gets passed to {@code eventListener} methods.
   */
  public DashChunkSource(ManifestFetcher<MediaPresentationDescription> manifestFetcher,
      int adaptationSetType, DataSource dataSource, FormatEvaluator adaptiveFormatEvaluator,
      long liveEdgeLatencyMs, long elapsedRealtimeOffsetMs, Handler eventHandler,
      EventListener eventListener, int eventSourceId) {
    this(manifestFetcher, adaptationSetType, dataSource, adaptiveFormatEvaluator, new SystemClock(),
        liveEdgeLatencyMs * 1000, elapsedRealtimeOffsetMs * 1000, true, eventHandler, eventListener,
        eventSourceId);
  }

  /**
   * Constructor to use for live DVR streaming.
   *
   * @param manifestFetcher A fetcher for the manifest.
   * @param adaptationSetType The type of the adaptation set exposed by this source. One of
   *     {@link AdaptationSet#TYPE_AUDIO}, {@link AdaptationSet#TYPE_VIDEO} and
   *     {@link AdaptationSet#TYPE_TEXT}.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
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
   * @param eventSourceId An identifier that gets passed to {@code eventListener} methods.
   */
  public DashChunkSource(ManifestFetcher<MediaPresentationDescription> manifestFetcher,
      int adaptationSetType, DataSource dataSource, FormatEvaluator adaptiveFormatEvaluator,
      long liveEdgeLatencyMs, long elapsedRealtimeOffsetMs, boolean startAtLiveEdge,
      Handler eventHandler, EventListener eventListener, int eventSourceId) {
    this(manifestFetcher, adaptationSetType, dataSource, adaptiveFormatEvaluator, new SystemClock(),
        liveEdgeLatencyMs * 1000, elapsedRealtimeOffsetMs * 1000, startAtLiveEdge, eventHandler,
        eventListener, eventSourceId);
  }

  /* package */ DashChunkSource(ManifestFetcher<MediaPresentationDescription> manifestFetcher,
      int adaptationSetType, DataSource dataSource, FormatEvaluator adaptiveFormatEvaluator,
      Clock systemClock, long liveEdgeLatencyUs, long elapsedRealtimeOffsetUs,
      boolean startAtLiveEdge, Handler eventHandler, EventListener eventListener,
      int eventSourceId) {
    this.manifestFetcher = manifestFetcher;
    this.adaptationSetType = adaptationSetType;
    this.dataSource = dataSource;
    this.adaptiveFormatEvaluator = adaptiveFormatEvaluator;
    this.systemClock = systemClock;
    this.liveEdgeLatencyUs = liveEdgeLatencyUs;
    this.elapsedRealtimeOffsetUs = elapsedRealtimeOffsetUs;
    this.startAtLiveEdge = startAtLiveEdge;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
    this.evaluation = new Evaluation();
    this.availableRangeValues = new long[2];
    periodHolders = new SparseArray<>();
  }

  // ChunkSource implementation.

  @Override
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else if (live) {
      manifestFetcher.maybeThrowError();
    }
  }

  @Override
  public boolean prepare() throws IOException {
    if (currentManifest == null) {
      currentManifest = manifestFetcher.getManifest();
      if (currentManifest == null) {
        manifestFetcher.maybeThrowError();
        manifestFetcher.requestRefresh();
        return false;
      } else {
        live = currentManifest.dynamic;
        durationUs = live ? C.UNKNOWN_TIME_US : currentManifest.duration * 1000;
        initForManifest(currentManifest);
      }
    }
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public final TrackGroup getTracks() {
    return trackGroup;
  }

  @Override
  public void enable(int[] tracks) {
    enabledFormats = new Format[tracks.length];
    for (int i = 0; i < tracks.length; i++) {
      enabledFormats[i] = trackGroup.getFormat(tracks[i]);
    }
    Arrays.sort(enabledFormats, new DecreasingBandwidthComparator());
    if (enabledFormats.length > 1) {
      adaptiveFormatEvaluator.enable(enabledFormats);
      adaptiveFormatBlacklistFlags = new boolean[tracks.length];
    }
    processManifest(manifestFetcher.getManifest());
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    if (!currentManifest.dynamic || fatalError != null) {
      return;
    }

    MediaPresentationDescription newManifest = manifestFetcher.getManifest();
    if (newManifest != null && newManifest != processedManifest) {
      processManifest(newManifest);
      // Manifests may be rejected, so the new manifest may not become the next currentManifest.
      // Track a manifest has been processed to avoid processing twice when it was discarded.
      processedManifest = newManifest;
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
  public final void getChunkOperation(List<? extends MediaChunk> queue, long playbackPositionUs,
      ChunkOperationHolder out) {
    if (fatalError != null) {
      out.chunk = null;
      return;
    }

    evaluation.queueSize = queue.size();
    if (evaluation.format == null || !lastChunkWasInitialization) {
      if (enabledFormats.length > 1) {
        adaptiveFormatEvaluator.evaluate(queue, playbackPositionUs, 0, adaptiveFormatBlacklistFlags,
            evaluation);
      } else {
        evaluation.format = enabledFormats[0];
        evaluation.trigger = Chunk.TRIGGER_MANUAL;
      }
    }

    Format selectedFormat = evaluation.format;
    out.queueSize = evaluation.queueSize;

    if (selectedFormat == null) {
      out.chunk = null;
      return;
    } else if (out.queueSize == queue.size() && out.chunk != null
        && out.chunk.format == selectedFormat) {
      // We already have a chunk, and the evaluation hasn't changed either the format or the size
      // of the queue. Leave unchanged.
      return;
    }

    // In all cases where we return before instantiating a new chunk, we want out.chunk to be null.
    out.chunk = null;

    boolean startingNewPeriod;
    PeriodHolder periodHolder;

    availableRange.getCurrentBoundsUs(availableRangeValues);
    if (queue.isEmpty()) {
      if (live) {
        if (startAtLiveEdge) {
          // We want live streams to start at the live edge instead of the beginning of the
          // manifest
          playbackPositionUs = Math.max(availableRangeValues[0],
              availableRangeValues[1] - liveEdgeLatencyUs);
        } else {
          // we subtract 1 from the upper bound because it's exclusive for that bound
          playbackPositionUs = Math.min(playbackPositionUs, availableRangeValues[1] - 1);
          playbackPositionUs = Math.max(playbackPositionUs, availableRangeValues[0]);
        }
      }

      periodHolder = findPeriodHolder(playbackPositionUs);
      startingNewPeriod = true;
    } else {
      if (startAtLiveEdge) {
        // now that we know the player is consuming media chunks (since the queue isn't empty),
        // set startAtLiveEdge to false so that the user can perform seek operations
        startAtLiveEdge = false;
      }

      MediaChunk previous = queue.get(out.queueSize - 1);
      long nextSegmentStartTimeUs = previous.endTimeUs;
      if (live && nextSegmentStartTimeUs < availableRangeValues[0]) {
        // This is before the first chunk in the current manifest.
        fatalError = new BehindLiveWindowException();
        return;
      } else if (currentManifest.dynamic && nextSegmentStartTimeUs >= availableRangeValues[1]) {
        // This chunk is beyond the last chunk in the current manifest. If the index is bounded
        // we'll need to wait until it's refreshed. If it's unbounded we just need to wait for a
        // while before attempting to load the chunk.
        return;
      } else {
        // A period's duration is the maximum of its various representation's durations, so it's
        // possible that due to the minor differences between them our available range values might
        // not sync exactly with the actual available content, so double check whether or not we've
        // really run out of content to play.
        PeriodHolder lastPeriodHolder = periodHolders.valueAt(periodHolders.size() - 1);
        if (previous.parentId == lastPeriodHolder.localIndex) {
          RepresentationHolder representationHolder =
              lastPeriodHolder.representationHolders[getTrackIndex(previous.format)];
          if (representationHolder.isBeyondLastSegment(previous.getNextChunkIndex())) {
            if (!currentManifest.dynamic) {
              // The current manifest isn't dynamic, so we've reached the end of the stream.
              out.endOfStream = true;
            }
            return;
          }
        }
      }

      startingNewPeriod = false;
      periodHolder = periodHolders.get(previous.parentId);
      if (periodHolder == null) {
        // The previous chunk was from a period that's no longer on the manifest, therefore the
        // next chunk must be the first one in the first period that's still on the manifest
        // (note that we can't actually update the segmentNum yet because the new period might
        // have a different sequence and its segmentIndex might not have been loaded yet).
        periodHolder = periodHolders.valueAt(0);
        startingNewPeriod = true;
      } else if (!periodHolder.isIndexUnbounded()) {
        RepresentationHolder representationHolder =
            periodHolder.representationHolders[getTrackIndex(previous.format)];
        if (representationHolder.isBeyondLastSegment(previous.getNextChunkIndex())) {
          // We reached the end of a period. Start the next one.
          periodHolder = periodHolders.get(previous.parentId + 1);
          startingNewPeriod = true;
        }
      }
    }

    RepresentationHolder representationHolder =
        periodHolder.representationHolders[getTrackIndex(selectedFormat)];
    Representation selectedRepresentation = representationHolder.representation;

    RangedUri pendingInitializationUri = null;
    RangedUri pendingIndexUri = null;

    Format sampleFormat = representationHolder.sampleFormat;
    if (sampleFormat == null) {
      pendingInitializationUri = selectedRepresentation.getInitializationUri();
    }
    if (representationHolder.segmentIndex == null) {
      pendingIndexUri = selectedRepresentation.getIndexUri();
    }

    if (pendingInitializationUri != null || pendingIndexUri != null) {
      // We have initialization and/or index requests to make.
      Chunk initializationChunk = newInitializationChunk(pendingInitializationUri, pendingIndexUri,
          selectedRepresentation, representationHolder.extractorWrapper, dataSource,
          periodHolder.localIndex, evaluation.trigger);
      lastChunkWasInitialization = true;
      out.chunk = initializationChunk;
      return;
    }

    int segmentNum = queue.isEmpty() ? representationHolder.getSegmentNum(playbackPositionUs)
          : startingNewPeriod ? representationHolder.getFirstAvailableSegmentNum()
          : queue.get(out.queueSize - 1).getNextChunkIndex();
    Chunk nextMediaChunk = newMediaChunk(periodHolder, representationHolder, dataSource,
        selectedFormat, sampleFormat, segmentNum, evaluation.trigger);
    lastChunkWasInitialization = false;
    out.chunk = nextMediaChunk;
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof InitializationChunk) {
      InitializationChunk initializationChunk = (InitializationChunk) chunk;
      PeriodHolder periodHolder = periodHolders.get(initializationChunk.parentId);
      if (periodHolder == null) {
        // period for this initialization chunk may no longer be on the manifest
        return;
      }

      RepresentationHolder representationHolder =
          periodHolder.representationHolders[getTrackIndex(initializationChunk.format)];
      if (initializationChunk.hasSampleFormat()) {
        representationHolder.sampleFormat = initializationChunk.getSampleFormat();
      }
      // The null check avoids overwriting an index obtained from the manifest with one obtained
      // from the stream. If the manifest defines an index then the stream shouldn't, but in cases
      // where it does we should ignore it.
      if (representationHolder.segmentIndex == null && initializationChunk.hasSeekMap()) {
        representationHolder.segmentIndex = new DashWrappingSegmentIndex(
            (ChunkIndex) initializationChunk.getSeekMap(),
            initializationChunk.dataSpec.uri.toString());
      }
      // The null check avoids overwriting drmInitData obtained from the manifest with drmInitData
      // obtained from the stream, as per DASH IF Interoperability Recommendations V3.0, 7.5.3.
      if (periodHolder.drmInitData == null && initializationChunk.hasDrmInitData()) {
        periodHolder.drmInitData = initializationChunk.getDrmInitData();
      }
    }
  }

  @Override
  public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e) {
    // TODO: Consider implementing representation blacklisting.
    return false;
  }

  @Override
  public void disable() {
    if (enabledFormats.length > 1) {
      adaptiveFormatEvaluator.disable();
    }
    periodHolders.clear();
    evaluation.clear();
    availableRange = null;
    fatalError = null;
    enabledFormats = null;
  }

  // Private methods.

  private void initForManifest(MediaPresentationDescription manifest) {
    Period period = manifest.getPeriod(0);
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      if (adaptationSet.type == adaptationSetType) {
        adaptationSetIndex = i;
        List<Representation> representations = adaptationSet.representations;
        if (!representations.isEmpty()) {
          // We've found a non-empty adaptation set of the exposed type.
          Format[] trackFormats = new Format[representations.size()];
          for (int j = 0; j < trackFormats.length; j++) {
            trackFormats[j] = representations.get(j).format;
          }
          trackGroup = new TrackGroup(adaptiveFormatEvaluator != null, trackFormats);
          return;
        }
      }
    }
    trackGroup = null;
  }

  // Visible for testing.
  /* package */ TimeRange getAvailableRange() {
    return availableRange;
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

  protected Chunk newMediaChunk(PeriodHolder periodHolder,
      RepresentationHolder representationHolder, DataSource dataSource, Format trackFormat,
      Format sampleFormat, int segmentNum, int trigger) {
    Representation representation = representationHolder.representation;
    long startTimeUs = representationHolder.getSegmentStartTimeUs(segmentNum);
    long endTimeUs = representationHolder.getSegmentEndTimeUs(segmentNum);
    RangedUri segmentUri = representationHolder.getSegmentUrl(segmentNum);
    DataSpec dataSpec = new DataSpec(segmentUri.getUri(), segmentUri.start, segmentUri.length,
        representation.getCacheKey());

    long sampleOffsetUs = periodHolder.startTimeUs - representation.presentationTimeOffsetUs;
    if (representationHolder.extractorWrapper == null) {
      return new SingleSampleMediaChunk(dataSource, dataSpec, Chunk.TRIGGER_INITIAL, trackFormat,
          startTimeUs, endTimeUs, segmentNum, trackFormat, null, periodHolder.localIndex);
    } else {
      boolean isSampleFormatFinal = sampleFormat != null;
      return new ContainerMediaChunk(dataSource, dataSpec, trigger, trackFormat, startTimeUs,
          endTimeUs, segmentNum, sampleOffsetUs, representationHolder.extractorWrapper,
          sampleFormat, periodHolder.drmInitData, isSampleFormatFinal, periodHolder.localIndex);
    }
  }

  private long getNowUnixTimeUs() {
    if (elapsedRealtimeOffsetUs != 0) {
      return (systemClock.elapsedRealtime() * 1000) + elapsedRealtimeOffsetUs;
    } else {
      return System.currentTimeMillis() * 1000;
    }
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
    // Remove old periods.
    Period firstPeriod = manifest.getPeriod(0);
    while (periodHolders.size() > 0
        && periodHolders.valueAt(0).startTimeUs < firstPeriod.startMs * 1000) {
      PeriodHolder periodHolder = periodHolders.valueAt(0);
      // TODO: Use periodHolders.removeAt(0) if the minimum API level is ever increased to 11.
      periodHolders.remove(periodHolder.localIndex);
    }

    // After discarding old periods, we should never have more periods than listed in the new
    // manifest.  That would mean that a previously announced period is no longer advertised.  If
    // this condition occurs, assume that we are hitting a manifest server that is out of sync and
    // behind, discard this manifest, and try again later.
    if (periodHolders.size() > manifest.getPeriodCount()) {
      return;
    }

    // Update existing periods. Only the first and last periods can change.
    try {
      int periodHolderCount = periodHolders.size();
      if (periodHolderCount > 0) {
        periodHolders.valueAt(0).updatePeriod(manifest, 0, adaptationSetIndex);
        if (periodHolderCount > 1) {
          int lastIndex = periodHolderCount - 1;
          periodHolders.valueAt(lastIndex).updatePeriod(manifest, lastIndex, adaptationSetIndex);
        }
      }
    } catch (BehindLiveWindowException e) {
      fatalError = e;
      return;
    }

    // Add new periods.
    for (int i = periodHolders.size(); i < manifest.getPeriodCount(); i++) {
      PeriodHolder holder = new PeriodHolder(nextPeriodHolderIndex, manifest, i,
          adaptationSetIndex);
      periodHolders.put(nextPeriodHolderIndex, holder);
      nextPeriodHolderIndex++;
    }

    // Update the available range.
    TimeRange newAvailableRange = getAvailableRange(getNowUnixTimeUs());
    if (availableRange == null || !availableRange.equals(newAvailableRange)) {
      availableRange = newAvailableRange;
      notifyAvailableRangeChanged(availableRange);
    }

    currentManifest = manifest;
  }

  private TimeRange getAvailableRange(long nowUnixTimeUs) {
    PeriodHolder firstPeriod = periodHolders.valueAt(0);
    PeriodHolder lastPeriod = periodHolders.valueAt(periodHolders.size() - 1);

    if (!currentManifest.dynamic || lastPeriod.isIndexExplicit()) {
      return new StaticTimeRange(firstPeriod.getAvailableStartTimeUs(),
          lastPeriod.getAvailableEndTimeUs());
    }

    long minStartPositionUs = firstPeriod.getAvailableStartTimeUs();
    long maxEndPositionUs = lastPeriod.isIndexUnbounded() ? Long.MAX_VALUE
        : lastPeriod.getAvailableEndTimeUs();
    long elapsedRealtimeAtZeroUs = (systemClock.elapsedRealtime() * 1000)
        - (nowUnixTimeUs - (currentManifest.availabilityStartTime * 1000));
    long timeShiftBufferDepthUs = currentManifest.timeShiftBufferDepth == -1 ? -1
        : currentManifest.timeShiftBufferDepth * 1000;
    return new DynamicTimeRange(minStartPositionUs, maxEndPositionUs, elapsedRealtimeAtZeroUs,
        timeShiftBufferDepthUs, systemClock);
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

  private void notifyAvailableRangeChanged(final TimeRange seekRange) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onAvailableRangeChanged(eventSourceId, seekRange);
        }
      });
    }
  }

  // Protected classes.

  protected static final class RepresentationHolder {

    public final ChunkExtractorWrapper extractorWrapper;

    public Representation representation;
    public DashSegmentIndex segmentIndex;
    public Format sampleFormat;

    private final long periodStartTimeUs;

    private long periodDurationUs;
    private int segmentNumShift;

    public RepresentationHolder(long periodStartTimeUs, long periodDurationUs,
        Representation representation) {
      this.periodStartTimeUs = periodStartTimeUs;
      this.periodDurationUs = periodDurationUs;
      this.representation = representation;
      String containerMimeType = representation.format.containerMimeType;
      extractorWrapper = mimeTypeIsRawText(containerMimeType) ? null : new ChunkExtractorWrapper(
          mimeTypeIsWebm(containerMimeType) ? new WebmExtractor() : new FragmentedMp4Extractor());
      segmentIndex = representation.getIndex();
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

    public int getSegmentNum(long positionUs) {
      return segmentIndex.getSegmentNum(positionUs - periodStartTimeUs, periodDurationUs)
          + segmentNumShift;
    }

    public long getSegmentStartTimeUs(int segmentNum) {
      return segmentIndex.getTimeUs(segmentNum - segmentNumShift) + periodStartTimeUs;
    }

    public long getSegmentEndTimeUs(int segmentNum) {
      return getSegmentStartTimeUs(segmentNum)
          + segmentIndex.getDurationUs(segmentNum - segmentNumShift, periodDurationUs);
    }

    public int getLastSegmentNum() {
      return segmentIndex.getLastSegmentNum(periodDurationUs);
    }

    public boolean isBeyondLastSegment(int segmentNum) {
      int lastSegmentNum = getLastSegmentNum();
      return lastSegmentNum == DashSegmentIndex.INDEX_UNBOUNDED ? false
          : segmentNum > (lastSegmentNum + segmentNumShift);
    }

    public int getFirstAvailableSegmentNum() {
      return segmentIndex.getFirstSegmentNum() + segmentNumShift;
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

  protected static final class PeriodHolder {

    public final int localIndex;
    public final long startTimeUs;
    public final RepresentationHolder[] representationHolders;

    private DrmInitData drmInitData;

    private boolean indexIsUnbounded;
    private boolean indexIsExplicit;
    private long availableStartTimeUs;
    private long availableEndTimeUs;

    public PeriodHolder(int localIndex, MediaPresentationDescription manifest, int manifestIndex,
        int adaptationSetIndex) {
      this.localIndex = localIndex;

      Period period = manifest.getPeriod(manifestIndex);
      long periodDurationUs = getPeriodDurationUs(manifest, manifestIndex);
      AdaptationSet adaptationSet = period.adaptationSets.get(adaptationSetIndex);
      List<Representation> representations = adaptationSet.representations;

      startTimeUs = period.startMs * 1000;
      drmInitData = getDrmInitData(adaptationSet);

      representationHolders = new RepresentationHolder[representations.size()];
      for (int i = 0; i < representationHolders.length; i++) {
        Representation representation = representations.get(i);
        representationHolders[i] = new RepresentationHolder(startTimeUs,
            periodDurationUs, representation);
      }
      updateRepresentationIndependentProperties(periodDurationUs,
          representationHolders[0].representation);
    }

    public void updatePeriod(MediaPresentationDescription manifest, int manifestIndex,
        int adaptationSetIndex) throws BehindLiveWindowException {
      Period period = manifest.getPeriod(manifestIndex);
      long periodDurationUs = getPeriodDurationUs(manifest, manifestIndex);
      List<Representation> representations = period.adaptationSets
          .get(adaptationSetIndex).representations;

      for (int i = 0; i < representationHolders.length; i++) {
        Representation representation = representations.get(i);
        representationHolders[i].updateRepresentation(periodDurationUs, representation);
      }
      updateRepresentationIndependentProperties(periodDurationUs,
          representationHolders[0].representation);
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

    public DrmInitData getDrmInitData() {
      return drmInitData;
    }

    // Private methods.

    private void updateRepresentationIndependentProperties(long periodDurationUs,
        Representation arbitaryRepresentation) {
      DashSegmentIndex segmentIndex = arbitaryRepresentation.getIndex();
      if (segmentIndex != null) {
        int firstSegmentNum = segmentIndex.getFirstSegmentNum();
        int lastSegmentNum = segmentIndex.getLastSegmentNum(periodDurationUs);
        indexIsUnbounded = lastSegmentNum == DashSegmentIndex.INDEX_UNBOUNDED;
        indexIsExplicit = segmentIndex.isExplicit();
        availableStartTimeUs = startTimeUs + segmentIndex.getTimeUs(firstSegmentNum);
        if (!indexIsUnbounded) {
          availableEndTimeUs = startTimeUs + segmentIndex.getTimeUs(lastSegmentNum)
              + segmentIndex.getDurationUs(lastSegmentNum, periodDurationUs);
        }
      } else {
        indexIsUnbounded = false;
        indexIsExplicit = true;
        availableStartTimeUs = startTimeUs;
        availableEndTimeUs = startTimeUs + periodDurationUs;
      }
    }

    private static DrmInitData getDrmInitData(AdaptationSet adaptationSet) {
      if (adaptationSet.contentProtections.isEmpty()) {
        return null;
      } else {
        DrmInitData.Mapped drmInitData = null;
        for (int i = 0; i < adaptationSet.contentProtections.size(); i++) {
          ContentProtection contentProtection = adaptationSet.contentProtections.get(i);
          if (contentProtection.uuid != null && contentProtection.data != null) {
            if (drmInitData == null) {
              drmInitData = new DrmInitData.Mapped();
            }
            drmInitData.put(contentProtection.uuid, contentProtection.data);
          }
        }
        return drmInitData;
      }
    }

    private static long getPeriodDurationUs(MediaPresentationDescription manifest, int index) {
      long durationMs = manifest.getPeriodDuration(index);
      if (durationMs == -1) {
        return C.UNKNOWN_TIME_US;
      } else {
        return durationMs * 1000;
      }
    }

  }

}
