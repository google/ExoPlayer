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

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.SystemClock;
import android.text.style.UpdateLayout;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.chunk.BaseMediaChunkIterator;
import com.google.android.exoplayer2.source.chunk.BundledChunkExtractor;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkExtractor;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk;
import com.google.android.exoplayer2.source.chunk.InitializationChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.source.chunk.SingleSampleMediaChunk;
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A default {@link DashChunkSource} implementation. */
public class DefaultDashChunkSource implements DashChunkSource {

  public static final class Factory implements DashChunkSource.Factory {

    private final DataSource.Factory dataSourceFactory;
    private final int maxSegmentsPerLoad;
    private final ChunkExtractor.Factory chunkExtractorFactory;

    /**
     * Equivalent to {@link #Factory(ChunkExtractor.Factory, DataSource.Factory, int) new
     * Factory(BundledChunkExtractor.FACTORY, dataSourceFactory, maxSegmentsPerLoad = 1)}.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(dataSourceFactory, /* maxSegmentsPerLoad= */ 1);
    }

    /**
     * Equivalent to {@link #Factory(ChunkExtractor.Factory, DataSource.Factory, int) new
     * Factory(BundledChunkExtractor.FACTORY, dataSourceFactory, maxSegmentsPerLoad)}.
     */
    public Factory(DataSource.Factory dataSourceFactory, int maxSegmentsPerLoad) {
      this(BundledChunkExtractor.FACTORY, dataSourceFactory, maxSegmentsPerLoad);
    }

    /**
     * Creates a new instance.
     *
     * @param chunkExtractorFactory Creates {@link ChunkExtractor} instances to use for extracting
     *     chunks.
     * @param dataSourceFactory Creates the {@link DataSource} to use for downloading chunks.
     * @param maxSegmentsPerLoad See {@link DefaultDashChunkSource#DefaultDashChunkSource}.
     */
    public Factory(
        ChunkExtractor.Factory chunkExtractorFactory,
        DataSource.Factory dataSourceFactory,
        int maxSegmentsPerLoad) {
      this.chunkExtractorFactory = chunkExtractorFactory;
      this.dataSourceFactory = dataSourceFactory;
      this.maxSegmentsPerLoad = maxSegmentsPerLoad;
    }

    @Override
    public DashChunkSource createDashChunkSource(
        LoaderErrorThrower manifestLoaderErrorThrower,
        DashManifest manifest,
        MediaItem.PlaybackProperties playbackProperties,
        int periodIndex,
        int[] adaptationSetIndices,
        ExoTrackSelection trackSelection,
        int trackType,
        long elapsedRealtimeOffsetMs,
        boolean enableEventMessageTrack,
        List<Format> closedCaptionFormats,
        @Nullable PlayerTrackEmsgHandler playerEmsgHandler,
        @Nullable TransferListener transferListener) {
      DataSource dataSource = dataSourceFactory.createDataSource();
      if (transferListener != null) {
        dataSource.addTransferListener(transferListener);
      }
      return new DefaultDashChunkSource(
          chunkExtractorFactory,
          manifestLoaderErrorThrower,
          manifest,
          playbackProperties,
          periodIndex,
          adaptationSetIndices,
          trackSelection,
          trackType,
          dataSource,
          elapsedRealtimeOffsetMs,
          maxSegmentsPerLoad,
          enableEventMessageTrack,
          closedCaptionFormats,
          playerEmsgHandler);
    }

  }

  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final int[] adaptationSetIndices;
  private final int trackType;
  private final DataSource dataSource;
  private final long elapsedRealtimeOffsetMs;
  private final int maxSegmentsPerLoad;
  @Nullable private final PlayerTrackEmsgHandler playerTrackEmsgHandler;

  protected final RepresentationHolder[] representationHolders;

  private ExoTrackSelection trackSelection;
  private DashManifest manifest;
  private MediaItem.PlaybackProperties playbackProperties;
  private int periodIndex;
  @Nullable private IOException fatalError;
  private boolean missingLastSegment;

  /**
   * @param chunkExtractorFactory Creates {@link ChunkExtractor} instances to use for extracting
   *     chunks.
   * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
   * @param manifest The initial manifest.
   * @param playbackProperties Data for the media item this chunk belongs to
   * @param periodIndex The index of the period in the manifest.
   * @param adaptationSetIndices The indices of the adaptation sets in the period.
   * @param trackSelection The track selection.
   * @param trackType The type of the tracks in the selection.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param elapsedRealtimeOffsetMs If known, an estimate of the instantaneous difference between
*     server-side unix time and {@link SystemClock#elapsedRealtime()} in milliseconds, specified
*     as the server's unix time minus the local elapsed time. Or {@link C#TIME_UNSET} if unknown.
   * @param maxSegmentsPerLoad The maximum number of segments to combine into a single request. Note
*     that segments will only be combined if their {@link Uri}s are the same and if their data
*     ranges are adjacent.
   * @param enableEventMessageTrack Whether to output an event message track.
   * @param closedCaptionFormats The {@link Format Formats} of closed caption tracks to be output.
   * @param playerTrackEmsgHandler The {@link PlayerTrackEmsgHandler} instance to handle emsg
   */
  public DefaultDashChunkSource(
      ChunkExtractor.Factory chunkExtractorFactory,
      LoaderErrorThrower manifestLoaderErrorThrower,
      DashManifest manifest,
      MediaItem.PlaybackProperties playbackProperties,
      int periodIndex,
      int[] adaptationSetIndices,
      ExoTrackSelection trackSelection,
      int trackType,
      DataSource dataSource,
      long elapsedRealtimeOffsetMs,
      int maxSegmentsPerLoad,
      boolean enableEventMessageTrack,
      List<Format> closedCaptionFormats,
      @Nullable PlayerTrackEmsgHandler playerTrackEmsgHandler) {
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.manifest = manifest;
    this.playbackProperties = playbackProperties;
    this.adaptationSetIndices = adaptationSetIndices;
    this.trackSelection = trackSelection;
    this.trackType = trackType;
    this.dataSource = dataSource;
    this.periodIndex = periodIndex;
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    this.maxSegmentsPerLoad = maxSegmentsPerLoad;
    this.playerTrackEmsgHandler = playerTrackEmsgHandler;

    long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);

    List<Representation> representations = getRepresentations();
    representationHolders = new RepresentationHolder[trackSelection.length()];
    for (int i = 0; i < representationHolders.length; i++) {
      Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
      representationHolders[i] =
          new RepresentationHolder(
              periodDurationUs,
              representation,
              BundledChunkExtractor.FACTORY.createProgressiveMediaExtractor(
                  trackType,
                  representation.format,
                  enableEventMessageTrack,
                  closedCaptionFormats,
                  playerTrackEmsgHandler),
              /* segmentNumShift= */ 0,
              representation.getIndex());
    }
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    // Segments are aligned across representations, so any segment index will do.
    for (RepresentationHolder representationHolder : representationHolders) {
      if (representationHolder.segmentIndex != null) {
        long segmentNum = representationHolder.getSegmentNum(positionUs);
        long firstSyncUs = representationHolder.getSegmentStartTimeUs(segmentNum);
        long segmentCount = representationHolder.getSegmentCount();
        long secondSyncUs =
            firstSyncUs < positionUs
                    && (segmentCount == DashSegmentIndex.INDEX_UNBOUNDED
                        || segmentNum
                            < representationHolder.getFirstSegmentNum() + segmentCount - 1)
                ? representationHolder.getSegmentStartTimeUs(segmentNum + 1)
                : firstSyncUs;
        return seekParameters.resolveSeekPositionUs(positionUs, firstSyncUs, secondSyncUs);
      }
    }
    // We don't have a segment index to adjust the seek position with yet.
    return positionUs;
  }

  @Override
  public void updateManifest(DashManifest newManifest, int newPeriodIndex) {
    try {
      manifest = newManifest;
      periodIndex = newPeriodIndex;
      long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      List<Representation> representations = getRepresentations();
      for (int i = 0; i < representationHolders.length; i++) {
        Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
        representationHolders[i] =
            representationHolders[i].copyWithNewRepresentation(periodDurationUs, representation);
      }
    } catch (BehindLiveWindowException e) {
      fatalError = e;
    }
  }

  @Override
  public void updateTrackSelection(ExoTrackSelection trackSelection) {
    this.trackSelection = trackSelection;
  }

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

  @Override
  public boolean shouldCancelLoad(
      long playbackPositionUs, Chunk loadingChunk, List<? extends MediaChunk> queue) {
    if (fatalError != null) {
      return false;
    }
    return trackSelection.shouldCancelChunkLoad(playbackPositionUs, loadingChunk, queue);
  }

  @Override
  public void getNextChunk(
      long playbackPositionUs,
      long loadPositionUs,
      List<? extends MediaChunk> queue,
      ChunkHolder out) {
    if (fatalError != null) {
      return;
    }

    long bufferedDurationUs = loadPositionUs - playbackPositionUs;
    long presentationPositionUs =
        C.msToUs(manifest.availabilityStartTimeMs)
            + C.msToUs(manifest.getPeriod(periodIndex).startMs)
            + loadPositionUs;

    if (playerTrackEmsgHandler != null
        && playerTrackEmsgHandler.maybeRefreshManifestBeforeLoadingNextChunk(
            presentationPositionUs)) {
      return;
    }

    long nowUnixTimeUs = C.msToUs(Util.getNowUnixTimeMs(elapsedRealtimeOffsetMs));
    long nowPeriodTimeUs = getNowPeriodTimeUs(nowUnixTimeUs);
    MediaChunk previous = queue.isEmpty() ? null : queue.get(queue.size() - 1);
    MediaChunkIterator[] chunkIterators = new MediaChunkIterator[trackSelection.length()];
    for (int i = 0; i < chunkIterators.length; i++) {
      RepresentationHolder representationHolder = representationHolders[i];
      if (representationHolder.segmentIndex == null) {
        chunkIterators[i] = MediaChunkIterator.EMPTY;
      } else {
        long firstAvailableSegmentNum =
            representationHolder.getFirstAvailableSegmentNum(nowUnixTimeUs);
        long lastAvailableSegmentNum =
            representationHolder.getLastAvailableSegmentNum(nowUnixTimeUs);
        long segmentNum =
            getSegmentNum(
                representationHolder,
                previous,
                loadPositionUs,
                firstAvailableSegmentNum,
                lastAvailableSegmentNum);
        if (segmentNum < firstAvailableSegmentNum) {
          chunkIterators[i] = MediaChunkIterator.EMPTY;
        } else {
          chunkIterators[i] =
              new RepresentationSegmentIterator(
                  representationHolder, segmentNum, lastAvailableSegmentNum, nowPeriodTimeUs);
        }
      }
    }

    long availableLiveDurationUs = getAvailableLiveDurationUs(nowUnixTimeUs, playbackPositionUs);
    trackSelection.updateSelectedTrack(
        playbackPositionUs, bufferedDurationUs, availableLiveDurationUs, queue, chunkIterators);

    RepresentationHolder representationHolder =
        representationHolders[trackSelection.getSelectedIndex()];

    if (representationHolder.chunkExtractor != null) {
      Representation selectedRepresentation = representationHolder.representation;
      RangedUri pendingInitializationUri = null;
      RangedUri pendingIndexUri = null;
      if (representationHolder.chunkExtractor.getSampleFormats() == null) {
        pendingInitializationUri = selectedRepresentation.getInitializationUri();
      }
      if (representationHolder.segmentIndex == null) {
        pendingIndexUri = selectedRepresentation.getIndexUri();
      }
      if (pendingInitializationUri != null || pendingIndexUri != null) {
        // We have initialization and/or index requests to make.
        out.chunk = newInitializationChunk(representationHolder, dataSource,
            trackSelection.getSelectedFormat(), trackSelection.getSelectionReason(),
            trackSelection.getSelectionData(), pendingInitializationUri, pendingIndexUri);
        return;
      }
    }

    long periodDurationUs = representationHolder.periodDurationUs;
    boolean periodEnded = periodDurationUs != C.TIME_UNSET;

    if (representationHolder.getSegmentCount() == 0) {
      // The index doesn't define any segments.
      out.endOfStream = periodEnded;
      return;
    }

    long firstAvailableSegmentNum = representationHolder.getFirstAvailableSegmentNum(nowUnixTimeUs);
    long lastAvailableSegmentNum = representationHolder.getLastAvailableSegmentNum(nowUnixTimeUs);
    long segmentNum =
        getSegmentNum(
            representationHolder,
            previous,
            loadPositionUs,
            firstAvailableSegmentNum,
            lastAvailableSegmentNum);
    if (segmentNum < firstAvailableSegmentNum) {
      // This is before the first chunk in the current manifest.
      fatalError = new BehindLiveWindowException();
      return;
    }

    if (segmentNum > lastAvailableSegmentNum
        || (missingLastSegment && segmentNum >= lastAvailableSegmentNum)) {
      // The segment is beyond the end of the period.
      out.endOfStream = periodEnded;
      return;
    }

    if (periodEnded && representationHolder.getSegmentStartTimeUs(segmentNum) >= periodDurationUs) {
      // The period duration clips the period to a position before the segment.
      out.endOfStream = true;
      return;
    }

    int maxSegmentCount = (int) min(maxSegmentsPerLoad, lastAvailableSegmentNum - segmentNum + 1);
    if (periodDurationUs != C.TIME_UNSET) {
      while (maxSegmentCount > 1
          && representationHolder.getSegmentStartTimeUs(segmentNum + maxSegmentCount - 1)
              >= periodDurationUs) {
        // The period duration clips the period to a position before the last segment in the range
        // [segmentNum, segmentNum + maxSegmentCount - 1]. Reduce maxSegmentCount.
        maxSegmentCount--;
      }
    }

    long seekTimeUs = queue.isEmpty() ? loadPositionUs : C.TIME_UNSET;
    out.chunk =
        newMediaChunk(
            representationHolder,
            dataSource,
            trackType,
            trackSelection.getSelectedFormat(),
            trackSelection.getSelectionReason(),
            trackSelection.getSelectionData(),
            segmentNum,
            maxSegmentCount,
            seekTimeUs,
            nowPeriodTimeUs);
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof InitializationChunk) {
      InitializationChunk initializationChunk = (InitializationChunk) chunk;
      int trackIndex = trackSelection.indexOf(initializationChunk.trackFormat);
      RepresentationHolder representationHolder = representationHolders[trackIndex];
      // The null check avoids overwriting an index obtained from the manifest with one obtained
      // from the stream. If the manifest defines an index then the stream shouldn't, but in cases
      // where it does we should ignore it.
      if (representationHolder.segmentIndex == null) {
        @Nullable ChunkIndex chunkIndex = representationHolder.chunkExtractor.getChunkIndex();
        if (chunkIndex != null) {
          representationHolders[trackIndex] =
              representationHolder.copyWithNewSegmentIndex(
                  new DashWrappingSegmentIndex(
                      chunkIndex, representationHolder.representation.presentationTimeOffsetUs));
        }
      }
    }
    if (playerTrackEmsgHandler != null) {
      playerTrackEmsgHandler.onChunkLoadCompleted(chunk);
    }
  }

  @Override
  public boolean onChunkLoadError(
      Chunk chunk, boolean cancelable, Exception e, long exclusionDurationMs) {
    if (!cancelable) {
      return false;
    }
    if (playerTrackEmsgHandler != null && playerTrackEmsgHandler.onChunkLoadError(chunk)) {
      return true;
    }
    // Workaround for missing segment at the end of the period
    if (!manifest.dynamic && chunk instanceof MediaChunk
        && e instanceof InvalidResponseCodeException
        && ((InvalidResponseCodeException) e).responseCode == 404) {
      RepresentationHolder representationHolder =
          representationHolders[trackSelection.indexOf(chunk.trackFormat)];
      long segmentCount = representationHolder.getSegmentCount();
      if (segmentCount != DashSegmentIndex.INDEX_UNBOUNDED && segmentCount != 0) {
        long lastAvailableSegmentNum = representationHolder.getFirstSegmentNum() + segmentCount - 1;
        if (((MediaChunk) chunk).getNextChunkIndex() > lastAvailableSegmentNum) {
          missingLastSegment = true;
          return true;
        }
      }
    }
    return exclusionDurationMs != C.TIME_UNSET
        && trackSelection.blacklist(trackSelection.indexOf(chunk.trackFormat), exclusionDurationMs);
  }

  @Override
  public void release() {
    for (RepresentationHolder representationHolder : representationHolders) {
      @Nullable ChunkExtractor chunkExtractor = representationHolder.chunkExtractor;
      if (chunkExtractor != null) {
        chunkExtractor.release();
      }
    }
  }

  // Internal methods.

  private long getSegmentNum(
      RepresentationHolder representationHolder,
      @Nullable MediaChunk previousChunk,
      long loadPositionUs,
      long firstAvailableSegmentNum,
      long lastAvailableSegmentNum) {
    return previousChunk != null
        ? previousChunk.getNextChunkIndex()
        : Util.constrainValue(
            representationHolder.getSegmentNum(loadPositionUs),
            firstAvailableSegmentNum,
            lastAvailableSegmentNum);
  }

  private ArrayList<Representation> getRepresentations() {
    List<AdaptationSet> manifestAdaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    ArrayList<Representation> representations = new ArrayList<>();
    for (int adaptationSetIndex : adaptationSetIndices) {
      representations.addAll(manifestAdaptationSets.get(adaptationSetIndex).representations);
    }
    return representations;
  }

  private long getAvailableLiveDurationUs(long nowUnixTimeUs, long playbackPositionUs) {
    if (!manifest.dynamic) {
      return C.TIME_UNSET;
    }
    long lastSegmentNum = representationHolders[0].getLastAvailableSegmentNum(nowUnixTimeUs);
    long lastSegmentEndTimeUs = representationHolders[0].getSegmentEndTimeUs(lastSegmentNum);
    long nowPeriodTimeUs = getNowPeriodTimeUs(nowUnixTimeUs);
    long availabilityEndTimeUs = min(nowPeriodTimeUs, lastSegmentEndTimeUs);
    return max(0, availabilityEndTimeUs - playbackPositionUs);
  }

  private long getNowPeriodTimeUs(long nowUnixTimeUs) {
    return manifest.availabilityStartTimeMs == C.TIME_UNSET
        ? C.TIME_UNSET
        : nowUnixTimeUs
            - C.msToUs(manifest.availabilityStartTimeMs + manifest.getPeriod(periodIndex).startMs);
  }

  protected Chunk newInitializationChunk(
      RepresentationHolder representationHolder,
      DataSource dataSource,
      Format trackFormat,
      int trackSelectionReason,
      Object trackSelectionData,
      RangedUri initializationUri,
      RangedUri indexUri) {
    Representation representation = representationHolder.representation;
    RangedUri requestUri;
    if (initializationUri != null) {
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      requestUri = initializationUri.attemptMerge(indexUri, representation.baseUrl);
      if (requestUri == null) {
        requestUri = initializationUri;
      }
    } else {
      requestUri = indexUri;
    }
    DataSpec dataSpec = DashUtil.buildDataSpec(representation, requestUri, playbackProperties.headers, /* flags= */ 0);
    return new InitializationChunk(
        dataSource,
        dataSpec,
        trackFormat,
        trackSelectionReason,
        trackSelectionData,
        representationHolder.chunkExtractor);
  }

  protected Chunk newMediaChunk(
      RepresentationHolder representationHolder,
      DataSource dataSource,
      int trackType,
      Format trackFormat,
      int trackSelectionReason,
      Object trackSelectionData,
      long firstSegmentNum,
      int maxSegmentCount,
      long seekTimeUs,
      long nowPeriodTimeUs) {
    Representation representation = representationHolder.representation;
    long startTimeUs = representationHolder.getSegmentStartTimeUs(firstSegmentNum);
    RangedUri segmentUri = representationHolder.getSegmentUrl(firstSegmentNum);
    String baseUrl = representation.baseUrl;
    if (representationHolder.chunkExtractor == null) {
      long endTimeUs = representationHolder.getSegmentEndTimeUs(firstSegmentNum);
      int flags =
          representationHolder.isSegmentAvailableAtFullNetworkSpeed(
                  firstSegmentNum, nowPeriodTimeUs)
              ? 0
              : DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED;
      DataSpec dataSpec = DashUtil.buildDataSpec(representation, segmentUri, playbackProperties.headers, flags);
      return new SingleSampleMediaChunk(dataSource, dataSpec, trackFormat, trackSelectionReason,
          trackSelectionData, startTimeUs, endTimeUs, firstSegmentNum, trackType, trackFormat);
    } else {
      int segmentCount = 1;
      for (int i = 1; i < maxSegmentCount; i++) {
        RangedUri nextSegmentUri = representationHolder.getSegmentUrl(firstSegmentNum + i);
        @Nullable RangedUri mergedSegmentUri = segmentUri.attemptMerge(nextSegmentUri, baseUrl);
        if (mergedSegmentUri == null) {
          // Unable to merge segment fetches because the URIs do not merge.
          break;
        }
        segmentUri = mergedSegmentUri;
        segmentCount++;
      }
      long segmentNum = firstSegmentNum + segmentCount - 1;
      long endTimeUs = representationHolder.getSegmentEndTimeUs(segmentNum);
      long periodDurationUs = representationHolder.periodDurationUs;
      long clippedEndTimeUs =
          periodDurationUs != C.TIME_UNSET && periodDurationUs <= endTimeUs
              ? periodDurationUs
              : C.TIME_UNSET;
      int flags =
          representationHolder.isSegmentAvailableAtFullNetworkSpeed(segmentNum, nowPeriodTimeUs)
              ? 0
              : DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED;
      DataSpec dataSpec = DashUtil.buildDataSpec(representation, segmentUri, playbackProperties.headers, flags);
      long sampleOffsetUs = -representation.presentationTimeOffsetUs;
      return new ContainerMediaChunk(
          dataSource,
          dataSpec,
          trackFormat,
          trackSelectionReason,
          trackSelectionData,
          startTimeUs,
          endTimeUs,
          seekTimeUs,
          clippedEndTimeUs,
          firstSegmentNum,
          segmentCount,
          sampleOffsetUs,
          representationHolder.chunkExtractor);
    }
  }

  // Protected classes.

  /** {@link MediaChunkIterator} wrapping a {@link RepresentationHolder}. */
  protected static final class RepresentationSegmentIterator extends BaseMediaChunkIterator {

    private final RepresentationHolder representationHolder;
    private final long nowPeriodTimeUs;

    /**
     * Creates iterator.
     *
     * @param representation The {@link RepresentationHolder} to wrap.
     * @param firstAvailableSegmentNum The number of the first available segment.
     * @param lastAvailableSegmentNum The number of the last available segment.
     * @param nowPeriodTimeUs The current time in microseconds since the start of the period used
     *     for calculating if segments are available at full network speed.
     */
    public RepresentationSegmentIterator(
        RepresentationHolder representation,
        long firstAvailableSegmentNum,
        long lastAvailableSegmentNum,
        long nowPeriodTimeUs) {
      super(/* fromIndex= */ firstAvailableSegmentNum, /* toIndex= */ lastAvailableSegmentNum);
      this.representationHolder = representation;
      this.nowPeriodTimeUs = nowPeriodTimeUs;
    }

    @Override
    public DataSpec getDataSpec() {
      checkInBounds();
      long currentIndex = getCurrentIndex();
      RangedUri segmentUri = representationHolder.getSegmentUrl(currentIndex);
      int flags =
          representationHolder.isSegmentAvailableAtFullNetworkSpeed(currentIndex, nowPeriodTimeUs)
              ? 0
              : DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED;
      return DashUtil.buildDataSpec(representationHolder.representation, segmentUri, Collections.emptyMap(), flags);
    }

    @Override
    public long getChunkStartTimeUs() {
      checkInBounds();
      return representationHolder.getSegmentStartTimeUs(getCurrentIndex());
    }

    @Override
    public long getChunkEndTimeUs() {
      checkInBounds();
      return representationHolder.getSegmentEndTimeUs(getCurrentIndex());
    }
  }

  /** Holds information about a snapshot of a single {@link Representation}. */
  protected static final class RepresentationHolder {

    @Nullable /* package */ final ChunkExtractor chunkExtractor;

    public final Representation representation;
    @Nullable public final DashSegmentIndex segmentIndex;

    private final long periodDurationUs;
    private final long segmentNumShift;

    /* package */ RepresentationHolder(
        long periodDurationUs,
        Representation representation,
        @Nullable ChunkExtractor chunkExtractor,
        long segmentNumShift,
        @Nullable DashSegmentIndex segmentIndex) {
      this.periodDurationUs = periodDurationUs;
      this.representation = representation;
      this.segmentNumShift = segmentNumShift;
      this.chunkExtractor = chunkExtractor;
      this.segmentIndex = segmentIndex;
    }

    @CheckResult
    /* package */ RepresentationHolder copyWithNewRepresentation(
        long newPeriodDurationUs, Representation newRepresentation)
        throws BehindLiveWindowException {
      DashSegmentIndex oldIndex = representation.getIndex();
      DashSegmentIndex newIndex = newRepresentation.getIndex();

      if (oldIndex == null) {
        // Segment numbers cannot shift if the index isn't defined by the manifest.
        return new RepresentationHolder(
            newPeriodDurationUs, newRepresentation, chunkExtractor, segmentNumShift, oldIndex);
      }

      if (!oldIndex.isExplicit()) {
        // Segment numbers cannot shift if the index isn't explicit.
        return new RepresentationHolder(
            newPeriodDurationUs, newRepresentation, chunkExtractor, segmentNumShift, newIndex);
      }

      long oldIndexSegmentCount = oldIndex.getSegmentCount(newPeriodDurationUs);
      if (oldIndexSegmentCount == 0) {
        // Segment numbers cannot shift if the old index was empty.
        return new RepresentationHolder(
            newPeriodDurationUs, newRepresentation, chunkExtractor, segmentNumShift, newIndex);
      }

      long oldIndexFirstSegmentNum = oldIndex.getFirstSegmentNum();
      long oldIndexStartTimeUs = oldIndex.getTimeUs(oldIndexFirstSegmentNum);
      long oldIndexLastSegmentNum = oldIndexFirstSegmentNum + oldIndexSegmentCount - 1;
      long oldIndexEndTimeUs =
          oldIndex.getTimeUs(oldIndexLastSegmentNum)
              + oldIndex.getDurationUs(oldIndexLastSegmentNum, newPeriodDurationUs);
      long newIndexFirstSegmentNum = newIndex.getFirstSegmentNum();
      long newIndexStartTimeUs = newIndex.getTimeUs(newIndexFirstSegmentNum);
      long newSegmentNumShift = segmentNumShift;
      if (oldIndexEndTimeUs == newIndexStartTimeUs) {
        // The new index continues where the old one ended, with no overlap.
        newSegmentNumShift += oldIndexLastSegmentNum + 1 - newIndexFirstSegmentNum;
      } else if (oldIndexEndTimeUs < newIndexStartTimeUs) {
        // There's a gap between the old index and the new one which means we've slipped behind the
        // live window and can't proceed.
        throw new BehindLiveWindowException();
      } else if (newIndexStartTimeUs < oldIndexStartTimeUs) {
        // The new index overlaps with (but does not have a start position contained within) the old
        // index. This can only happen if extra segments have been added to the start of the index.
        newSegmentNumShift -=
            newIndex.getSegmentNum(oldIndexStartTimeUs, newPeriodDurationUs)
                - oldIndexFirstSegmentNum;
      } else {
        // The new index overlaps with (and has a start position contained within) the old index.
        newSegmentNumShift +=
            oldIndex.getSegmentNum(newIndexStartTimeUs, newPeriodDurationUs)
                - newIndexFirstSegmentNum;
      }
      return new RepresentationHolder(
          newPeriodDurationUs, newRepresentation, chunkExtractor, newSegmentNumShift, newIndex);
    }

    @CheckResult
    /* package */ RepresentationHolder copyWithNewSegmentIndex(DashSegmentIndex segmentIndex) {
      return new RepresentationHolder(
          periodDurationUs, representation, chunkExtractor, segmentNumShift, segmentIndex);
    }

    public long getFirstSegmentNum() {
      return segmentIndex.getFirstSegmentNum() + segmentNumShift;
    }

    public long getFirstAvailableSegmentNum(long nowUnixTimeUs) {
      return segmentIndex.getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs)
          + segmentNumShift;
    }

    public long getSegmentCount() {
      return segmentIndex.getSegmentCount(periodDurationUs);
    }

    public long getSegmentStartTimeUs(long segmentNum) {
      return segmentIndex.getTimeUs(segmentNum - segmentNumShift);
    }

    public long getSegmentEndTimeUs(long segmentNum) {
      return getSegmentStartTimeUs(segmentNum)
          + segmentIndex.getDurationUs(segmentNum - segmentNumShift, periodDurationUs);
    }

    public long getSegmentNum(long positionUs) {
      return segmentIndex.getSegmentNum(positionUs, periodDurationUs) + segmentNumShift;
    }

    public RangedUri getSegmentUrl(long segmentNum) {
      return segmentIndex.getSegmentUrl(segmentNum - segmentNumShift);
    }

    public long getLastAvailableSegmentNum(long nowUnixTimeUs) {
      return getFirstAvailableSegmentNum(nowUnixTimeUs)
          + segmentIndex.getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs)
          - 1;
    }

    public boolean isSegmentAvailableAtFullNetworkSpeed(long segmentNum, long nowPeriodTimeUs) {
      return nowPeriodTimeUs == C.TIME_UNSET || getSegmentEndTimeUs(segmentNum) <= nowPeriodTimeUs;
    }
  }
}
