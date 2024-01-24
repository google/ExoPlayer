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
package androidx.media3.exoplayer.dash;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.UriUtil;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.BaseUrl;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.RangedUri;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.source.BehindLiveWindowException;
import androidx.media3.exoplayer.source.chunk.BaseMediaChunkIterator;
import androidx.media3.exoplayer.source.chunk.BundledChunkExtractor;
import androidx.media3.exoplayer.source.chunk.Chunk;
import androidx.media3.exoplayer.source.chunk.ChunkExtractor;
import androidx.media3.exoplayer.source.chunk.ChunkHolder;
import androidx.media3.exoplayer.source.chunk.ContainerMediaChunk;
import androidx.media3.exoplayer.source.chunk.InitializationChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.source.chunk.SingleSampleMediaChunk;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.CmcdData;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoaderErrorThrower;
import androidx.media3.extractor.ChunkIndex;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A default {@link DashChunkSource} implementation. */
@UnstableApi
public class DefaultDashChunkSource implements DashChunkSource {

  /** {@link DashChunkSource.Factory} for {@link DefaultDashChunkSource} instances. */
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

    @CanIgnoreReturnValue
    @Override
    public Factory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
      chunkExtractorFactory.setSubtitleParserFactory(subtitleParserFactory);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory experimentalParseSubtitlesDuringExtraction(
        boolean parseSubtitlesDuringExtraction) {
      chunkExtractorFactory.experimentalParseSubtitlesDuringExtraction(
          parseSubtitlesDuringExtraction);
      return this;
    }

    @Override
    public DashChunkSource createDashChunkSource(
        LoaderErrorThrower manifestLoaderErrorThrower,
        DashManifest manifest,
        BaseUrlExclusionList baseUrlExclusionList,
        int periodIndex,
        int[] adaptationSetIndices,
        ExoTrackSelection trackSelection,
        @C.TrackType int trackType,
        long elapsedRealtimeOffsetMs,
        boolean enableEventMessageTrack,
        List<Format> closedCaptionFormats,
        @Nullable PlayerTrackEmsgHandler playerEmsgHandler,
        @Nullable TransferListener transferListener,
        PlayerId playerId,
        @Nullable CmcdConfiguration cmcdConfiguration) {
      DataSource dataSource = dataSourceFactory.createDataSource();
      if (transferListener != null) {
        dataSource.addTransferListener(transferListener);
      }
      return new DefaultDashChunkSource(
          chunkExtractorFactory,
          manifestLoaderErrorThrower,
          manifest,
          baseUrlExclusionList,
          periodIndex,
          adaptationSetIndices,
          trackSelection,
          trackType,
          dataSource,
          elapsedRealtimeOffsetMs,
          maxSegmentsPerLoad,
          enableEventMessageTrack,
          closedCaptionFormats,
          playerEmsgHandler,
          playerId,
          cmcdConfiguration);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation delegates determining of the output format to the {@link
     * ChunkExtractor.Factory} passed to the constructor of this class.
     */
    @Override
    public Format getOutputTextFormat(Format sourceFormat) {
      return chunkExtractorFactory.getOutputTextFormat(sourceFormat);
    }
  }

  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final BaseUrlExclusionList baseUrlExclusionList;
  private final int[] adaptationSetIndices;
  private final @C.TrackType int trackType;
  private final DataSource dataSource;
  private final long elapsedRealtimeOffsetMs;
  private final int maxSegmentsPerLoad;
  @Nullable private final PlayerTrackEmsgHandler playerTrackEmsgHandler;
  @Nullable private final CmcdConfiguration cmcdConfiguration;

  protected final RepresentationHolder[] representationHolders;

  private ExoTrackSelection trackSelection;
  private DashManifest manifest;
  private int periodIndex;
  @Nullable private IOException fatalError;
  private boolean missingLastSegment;

  /**
   * The time at which the last {@link #getNextChunk(LoadingInfo, long, List, ChunkHolder)} method
   * was called, as measured by {@link SystemClock#elapsedRealtime}.
   */
  private long lastChunkRequestRealtimeMs;

  /**
   * @param chunkExtractorFactory Creates {@link ChunkExtractor} instances to use for extracting
   *     chunks.
   * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
   * @param manifest The initial manifest.
   * @param baseUrlExclusionList The base URL exclusion list.
   * @param periodIndex The index of the period in the manifest.
   * @param adaptationSetIndices The indices of the adaptation sets in the period.
   * @param trackSelection The track selection.
   * @param trackType The {@link C.TrackType type} of the tracks in the selection.
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
   *     messages targeting the player. Maybe null if this is not necessary.
   * @param playerId The {@link PlayerId} of the player using this chunk source.
   * @param cmcdConfiguration The {@link CmcdConfiguration} for this chunk source.
   */
  public DefaultDashChunkSource(
      ChunkExtractor.Factory chunkExtractorFactory,
      LoaderErrorThrower manifestLoaderErrorThrower,
      DashManifest manifest,
      BaseUrlExclusionList baseUrlExclusionList,
      int periodIndex,
      int[] adaptationSetIndices,
      ExoTrackSelection trackSelection,
      @C.TrackType int trackType,
      DataSource dataSource,
      long elapsedRealtimeOffsetMs,
      int maxSegmentsPerLoad,
      boolean enableEventMessageTrack,
      List<Format> closedCaptionFormats,
      @Nullable PlayerTrackEmsgHandler playerTrackEmsgHandler,
      PlayerId playerId,
      @Nullable CmcdConfiguration cmcdConfiguration) {
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.manifest = manifest;
    this.baseUrlExclusionList = baseUrlExclusionList;
    this.adaptationSetIndices = adaptationSetIndices;
    this.trackSelection = trackSelection;
    this.trackType = trackType;
    this.dataSource = dataSource;
    this.periodIndex = periodIndex;
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    this.maxSegmentsPerLoad = maxSegmentsPerLoad;
    this.playerTrackEmsgHandler = playerTrackEmsgHandler;
    this.cmcdConfiguration = cmcdConfiguration;
    this.lastChunkRequestRealtimeMs = C.TIME_UNSET;

    long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);

    List<Representation> representations = getRepresentations();
    representationHolders = new RepresentationHolder[trackSelection.length()];
    for (int i = 0; i < representationHolders.length; i++) {
      Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
      @Nullable
      BaseUrl selectedBaseUrl = baseUrlExclusionList.selectBaseUrl(representation.baseUrls);
      representationHolders[i] =
          new RepresentationHolder(
              periodDurationUs,
              representation,
              selectedBaseUrl != null ? selectedBaseUrl : representation.baseUrls.get(0),
              chunkExtractorFactory.createProgressiveMediaExtractor(
                  trackType,
                  representation.format,
                  enableEventMessageTrack,
                  closedCaptionFormats,
                  playerTrackEmsgHandler,
                  playerId),
              /* segmentNumShift= */ 0,
              representation.getIndex());
    }
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    // Segments are aligned across representations, so any segment index will do.
    for (RepresentationHolder representationHolder : representationHolders) {
      if (representationHolder.segmentIndex != null) {
        long segmentCount = representationHolder.getSegmentCount();
        if (segmentCount == 0) {
          continue;
        }
        long segmentNum = representationHolder.getSegmentNum(positionUs);
        long firstSyncUs = representationHolder.getSegmentStartTimeUs(segmentNum);
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
      LoadingInfo loadingInfo,
      long loadPositionUs,
      List<? extends MediaChunk> queue,
      ChunkHolder out) {
    if (fatalError != null) {
      return;
    }

    long playbackPositionUs = loadingInfo.playbackPositionUs;
    long bufferedDurationUs = loadPositionUs - playbackPositionUs;
    long presentationPositionUs =
        Util.msToUs(manifest.availabilityStartTimeMs)
            + Util.msToUs(manifest.getPeriod(periodIndex).startMs)
            + loadPositionUs;

    if (playerTrackEmsgHandler != null
        && playerTrackEmsgHandler.maybeRefreshManifestBeforeLoadingNextChunk(
            presentationPositionUs)) {
      return;
    }

    long nowUnixTimeUs = Util.msToUs(Util.getNowUnixTimeMs(elapsedRealtimeOffsetMs));
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
          representationHolder = updateSelectedBaseUrl(/* trackIndex= */ i);
          chunkIterators[i] =
              new RepresentationSegmentIterator(
                  representationHolder, segmentNum, lastAvailableSegmentNum, nowPeriodTimeUs);
        }
      }
    }

    long availableLiveDurationUs = getAvailableLiveDurationUs(nowUnixTimeUs, playbackPositionUs);
    trackSelection.updateSelectedTrack(
        playbackPositionUs, bufferedDurationUs, availableLiveDurationUs, queue, chunkIterators);
    int selectedTrackIndex = trackSelection.getSelectedIndex();

    @Nullable
    CmcdData.Factory cmcdDataFactory =
        cmcdConfiguration == null
            ? null
            : new CmcdData.Factory(
                cmcdConfiguration,
                trackSelection,
                max(0, bufferedDurationUs),
                /* playbackRate= */ loadingInfo.playbackSpeed,
                /* streamingFormat= */ CmcdData.Factory.STREAMING_FORMAT_DASH,
                /* isLive= */ manifest.dynamic,
                /* didRebuffer= */ loadingInfo.rebufferedSince(lastChunkRequestRealtimeMs),
                /* isBufferEmpty= */ queue.isEmpty());
    lastChunkRequestRealtimeMs = SystemClock.elapsedRealtime();

    RepresentationHolder representationHolder = updateSelectedBaseUrl(selectedTrackIndex);
    if (representationHolder.chunkExtractor != null) {
      Representation selectedRepresentation = representationHolder.representation;
      @Nullable RangedUri pendingInitializationUri = null;
      @Nullable RangedUri pendingIndexUri = null;
      if (representationHolder.chunkExtractor.getSampleFormats() == null) {
        pendingInitializationUri = selectedRepresentation.getInitializationUri();
      }
      if (representationHolder.segmentIndex == null) {
        pendingIndexUri = selectedRepresentation.getIndexUri();
      }
      if (pendingInitializationUri != null || pendingIndexUri != null) {
        // We have initialization and/or index requests to make.
        out.chunk =
            newInitializationChunk(
                representationHolder,
                dataSource,
                trackSelection.getSelectedFormat(),
                trackSelection.getSelectionReason(),
                trackSelection.getSelectionData(),
                pendingInitializationUri,
                pendingIndexUri,
                cmcdDataFactory);
        return;
      }
    }

    long periodDurationUs = representationHolder.periodDurationUs;
    boolean isLastPeriodInDynamicManifest =
        manifest.dynamic && periodIndex == manifest.getPeriodCount() - 1;
    boolean periodEnded = !isLastPeriodInDynamicManifest || periodDurationUs != C.TIME_UNSET;

    if (representationHolder.getSegmentCount() == 0) {
      // The index doesn't define any segments.
      out.endOfStream = periodEnded;
      return;
    }

    long firstAvailableSegmentNum = representationHolder.getFirstAvailableSegmentNum(nowUnixTimeUs);
    long lastAvailableSegmentNum = representationHolder.getLastAvailableSegmentNum(nowUnixTimeUs);
    if (isLastPeriodInDynamicManifest) {
      long lastAvailableSegmentEndTimeUs =
          representationHolder.getSegmentEndTimeUs(lastAvailableSegmentNum);
      long lastSegmentDurationUs =
          lastAvailableSegmentEndTimeUs
              - representationHolder.getSegmentStartTimeUs(lastAvailableSegmentNum);
      // Account for some inaccuracy in the overall period duration value by assuming that the
      // period is finished once no further full sample fits into the overall duration.
      periodEnded &= (lastAvailableSegmentEndTimeUs + lastSegmentDurationUs >= periodDurationUs);
    }
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
            nowPeriodTimeUs,
            cmcdDataFactory);
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
        @Nullable
        ChunkIndex chunkIndex =
            checkStateNotNull(representationHolder.chunkExtractor).getChunkIndex();
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
      Chunk chunk,
      boolean cancelable,
      LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    if (!cancelable) {
      return false;
    }
    if (playerTrackEmsgHandler != null && playerTrackEmsgHandler.onChunkLoadError(chunk)) {
      return true;
    }
    // Workaround for missing segment at the end of the period
    if (!manifest.dynamic
        && chunk instanceof MediaChunk
        && loadErrorInfo.exception instanceof InvalidResponseCodeException
        && ((InvalidResponseCodeException) loadErrorInfo.exception).responseCode == 404) {
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

    int trackIndex = trackSelection.indexOf(chunk.trackFormat);
    RepresentationHolder representationHolder = representationHolders[trackIndex];
    @Nullable
    BaseUrl newBaseUrl =
        baseUrlExclusionList.selectBaseUrl(representationHolder.representation.baseUrls);
    if (newBaseUrl != null && !representationHolder.selectedBaseUrl.equals(newBaseUrl)) {
      // The base URL has changed since the failing chunk was created. Request a replacement chunk,
      // which will use the new base URL.
      return true;
    }

    LoadErrorHandlingPolicy.FallbackOptions fallbackOptions =
        createFallbackOptions(trackSelection, representationHolder.representation.baseUrls);
    if (!fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK)
        && !fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION)) {
      return false;
    }
    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection fallbackSelection =
        loadErrorHandlingPolicy.getFallbackSelectionFor(fallbackOptions, loadErrorInfo);
    if (fallbackSelection == null || !fallbackOptions.isFallbackAvailable(fallbackSelection.type)) {
      // Policy indicated to not use any fallback or a fallback type that is not available.
      return false;
    }

    boolean cancelLoad = false;
    if (fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) {
      cancelLoad =
          trackSelection.excludeTrack(
              trackSelection.indexOf(chunk.trackFormat), fallbackSelection.exclusionDurationMs);
    } else if (fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION) {
      baseUrlExclusionList.exclude(
          representationHolder.selectedBaseUrl, fallbackSelection.exclusionDurationMs);
      cancelLoad = true;
    }
    return cancelLoad;
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

  private LoadErrorHandlingPolicy.FallbackOptions createFallbackOptions(
      ExoTrackSelection trackSelection, List<BaseUrl> baseUrls) {
    long nowMs = SystemClock.elapsedRealtime();
    int numberOfTracks = trackSelection.length();
    int numberOfExcludedTracks = 0;
    for (int i = 0; i < numberOfTracks; i++) {
      if (trackSelection.isTrackExcluded(i, nowMs)) {
        numberOfExcludedTracks++;
      }
    }
    int priorityCount = BaseUrlExclusionList.getPriorityCount(baseUrls);
    return new LoadErrorHandlingPolicy.FallbackOptions(
        /* numberOfLocations= */ priorityCount,
        /* numberOfExcludedLocations= */ priorityCount
            - baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls),
        numberOfTracks,
        numberOfExcludedTracks);
  }

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

  @RequiresNonNull({"manifest", "adaptationSetIndices"})
  private ArrayList<Representation> getRepresentations(
      @UnknownInitialization DefaultDashChunkSource this) {
    List<AdaptationSet> manifestAdaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    ArrayList<Representation> representations = new ArrayList<>();
    for (int adaptationSetIndex : adaptationSetIndices) {
      representations.addAll(manifestAdaptationSets.get(adaptationSetIndex).representations);
    }
    return representations;
  }

  private long getAvailableLiveDurationUs(long nowUnixTimeUs, long playbackPositionUs) {
    if (!manifest.dynamic || representationHolders[0].getSegmentCount() == 0) {
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
            - Util.msToUs(
                manifest.availabilityStartTimeMs + manifest.getPeriod(periodIndex).startMs);
  }

  /**
   * Creates a new {@link Chunk} for initialization.
   *
   * @param representationHolder The {@link Representation} holder for initialization.
   * @param dataSource The source from which the data should be loaded.
   * @param trackFormat The format of the track to which this chunk belongs.
   * @param trackSelectionReason One of the {@link C.SelectionReason selection reasons}.
   * @param trackSelectionData Additional data related to track selection.
   * @param initializationUri The URI pointing to initialization data. Can be {@code null} if {@code
   *     indexUri} is not {@code null}.
   * @param indexUri The URI pointing to index data. Can be {@code null} if {@code
   *     initializationUri} is not {@code null}.
   * @param cmcdDataFactory The {@link CmcdData.Factory} for generating CMCD data.
   */
  @RequiresNonNull("#1.chunkExtractor")
  protected Chunk newInitializationChunk(
      RepresentationHolder representationHolder,
      DataSource dataSource,
      Format trackFormat,
      @C.SelectionReason int trackSelectionReason,
      @Nullable Object trackSelectionData,
      @Nullable RangedUri initializationUri,
      @Nullable RangedUri indexUri,
      @Nullable CmcdData.Factory cmcdDataFactory) {
    Representation representation = representationHolder.representation;
    RangedUri requestUri;
    if (initializationUri != null) {
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      requestUri =
          initializationUri.attemptMerge(indexUri, representationHolder.selectedBaseUrl.url);
      if (requestUri == null) {
        requestUri = initializationUri;
      }
    } else {
      requestUri = checkNotNull(indexUri);
    }
    DataSpec dataSpec =
        DashUtil.buildDataSpec(
            representation,
            representationHolder.selectedBaseUrl.url,
            requestUri,
            /* flags= */ 0,
            /* httpRequestHeaders= */ ImmutableMap.of());
    if (cmcdDataFactory != null) {
      CmcdData cmcdData =
          cmcdDataFactory.setObjectType(CmcdData.Factory.OBJECT_TYPE_INIT_SEGMENT).createCmcdData();
      dataSpec = cmcdData.addToDataSpec(dataSpec);
    }

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
      @C.TrackType int trackType,
      Format trackFormat,
      @C.SelectionReason int trackSelectionReason,
      @Nullable Object trackSelectionData,
      long firstSegmentNum,
      int maxSegmentCount,
      long seekTimeUs,
      long nowPeriodTimeUs,
      @Nullable CmcdData.Factory cmcdDataFactory) {
    Representation representation = representationHolder.representation;
    long startTimeUs = representationHolder.getSegmentStartTimeUs(firstSegmentNum);
    RangedUri segmentUri = representationHolder.getSegmentUrl(firstSegmentNum);
    if (representationHolder.chunkExtractor == null) {
      long endTimeUs = representationHolder.getSegmentEndTimeUs(firstSegmentNum);
      int flags =
          representationHolder.isSegmentAvailableAtFullNetworkSpeed(
                  firstSegmentNum, nowPeriodTimeUs)
              ? 0
              : DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED;
      DataSpec dataSpec =
          DashUtil.buildDataSpec(
              representation,
              representationHolder.selectedBaseUrl.url,
              segmentUri,
              flags,
              /* httpRequestHeaders= */ ImmutableMap.of());
      if (cmcdDataFactory != null) {
        cmcdDataFactory
            .setChunkDurationUs(endTimeUs - startTimeUs)
            .setObjectType(CmcdData.Factory.getObjectType(trackSelection));
        @Nullable
        Pair<String, String> nextObjectAndRangeRequest =
            getNextObjectAndRangeRequest(firstSegmentNum, segmentUri, representationHolder);
        if (nextObjectAndRangeRequest != null) {
          cmcdDataFactory
              .setNextObjectRequest(nextObjectAndRangeRequest.first)
              .setNextRangeRequest(nextObjectAndRangeRequest.second);
        }
        CmcdData cmcdData = cmcdDataFactory.createCmcdData();
        dataSpec = cmcdData.addToDataSpec(dataSpec);
      }

      return new SingleSampleMediaChunk(
          dataSource,
          dataSpec,
          trackFormat,
          trackSelectionReason,
          trackSelectionData,
          startTimeUs,
          endTimeUs,
          firstSegmentNum,
          trackType,
          trackFormat);
    } else {
      int segmentCount = 1;
      for (int i = 1; i < maxSegmentCount; i++) {
        RangedUri nextSegmentUri = representationHolder.getSegmentUrl(firstSegmentNum + i);
        @Nullable
        RangedUri mergedSegmentUri =
            segmentUri.attemptMerge(nextSegmentUri, representationHolder.selectedBaseUrl.url);
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
      DataSpec dataSpec =
          DashUtil.buildDataSpec(
              representation,
              representationHolder.selectedBaseUrl.url,
              segmentUri,
              flags,
              /* httpRequestHeaders= */ ImmutableMap.of());
      if (cmcdDataFactory != null) {
        cmcdDataFactory
            .setChunkDurationUs(endTimeUs - startTimeUs)
            .setObjectType(CmcdData.Factory.getObjectType(trackSelection));
        @Nullable
        Pair<String, String> nextObjectAndRangeRequest =
            getNextObjectAndRangeRequest(firstSegmentNum, segmentUri, representationHolder);
        if (nextObjectAndRangeRequest != null) {
          cmcdDataFactory
              .setNextObjectRequest(nextObjectAndRangeRequest.first)
              .setNextRangeRequest(nextObjectAndRangeRequest.second);
        }
        CmcdData cmcdData = cmcdDataFactory.createCmcdData();
        dataSpec = cmcdData.addToDataSpec(dataSpec);
      }
      long sampleOffsetUs = -representation.presentationTimeOffsetUs;
      if (MimeTypes.isImage(trackFormat.sampleMimeType)) {
        sampleOffsetUs += startTimeUs;
      }
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

  @Nullable
  private Pair<String, String> getNextObjectAndRangeRequest(
      long segmentNum, RangedUri segmentUri, RepresentationHolder representationHolder) {
    if (segmentNum + 1 >= representationHolder.getSegmentCount()) {
      return null;
    }
    RangedUri nextSegmentUri = representationHolder.getSegmentUrl(segmentNum + 1);
    Uri uri = segmentUri.resolveUri(representationHolder.selectedBaseUrl.url);
    Uri nextUri = nextSegmentUri.resolveUri(representationHolder.selectedBaseUrl.url);
    String nextObjectRequest = UriUtil.getRelativePath(uri, nextUri);

    String nextRangeRequest = nextSegmentUri.start + "-";
    if (nextSegmentUri.length != C.LENGTH_UNSET) {
      nextRangeRequest += (nextSegmentUri.start + nextSegmentUri.length);
    }
    return new Pair<>(nextObjectRequest, nextRangeRequest);
  }

  private RepresentationHolder updateSelectedBaseUrl(int trackIndex) {
    RepresentationHolder representationHolder = representationHolders[trackIndex];
    @Nullable
    BaseUrl selectedBaseUrl =
        baseUrlExclusionList.selectBaseUrl(representationHolder.representation.baseUrls);
    if (selectedBaseUrl != null && !selectedBaseUrl.equals(representationHolder.selectedBaseUrl)) {
      representationHolder = representationHolder.copyWithNewSelectedBaseUrl(selectedBaseUrl);
      representationHolders[trackIndex] = representationHolder;
    }
    return representationHolder;
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
      return DashUtil.buildDataSpec(
          representationHolder.representation,
          representationHolder.selectedBaseUrl.url,
          segmentUri,
          flags,
          /* httpRequestHeaders= */ ImmutableMap.of());
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
    public final BaseUrl selectedBaseUrl;
    @Nullable public final DashSegmentIndex segmentIndex;

    private final long periodDurationUs;
    private final long segmentNumShift;

    /* package */ RepresentationHolder(
        long periodDurationUs,
        Representation representation,
        BaseUrl selectedBaseUrl,
        @Nullable ChunkExtractor chunkExtractor,
        long segmentNumShift,
        @Nullable DashSegmentIndex segmentIndex) {
      this.periodDurationUs = periodDurationUs;
      this.representation = representation;
      this.selectedBaseUrl = selectedBaseUrl;
      this.segmentNumShift = segmentNumShift;
      this.chunkExtractor = chunkExtractor;
      this.segmentIndex = segmentIndex;
    }

    @CheckResult
    /* package */ RepresentationHolder copyWithNewRepresentation(
        long newPeriodDurationUs, Representation newRepresentation)
        throws BehindLiveWindowException {
      @Nullable DashSegmentIndex oldIndex = representation.getIndex();
      @Nullable DashSegmentIndex newIndex = newRepresentation.getIndex();

      if (oldIndex == null) {
        // Segment numbers cannot shift if the index isn't defined by the manifest.
        return new RepresentationHolder(
            newPeriodDurationUs,
            newRepresentation,
            selectedBaseUrl,
            chunkExtractor,
            segmentNumShift,
            oldIndex);
      }

      if (!oldIndex.isExplicit()) {
        // Segment numbers cannot shift if the index isn't explicit.
        return new RepresentationHolder(
            newPeriodDurationUs,
            newRepresentation,
            selectedBaseUrl,
            chunkExtractor,
            segmentNumShift,
            newIndex);
      }

      long oldIndexSegmentCount = oldIndex.getSegmentCount(newPeriodDurationUs);
      if (oldIndexSegmentCount == 0) {
        // Segment numbers cannot shift if the old index was empty.
        return new RepresentationHolder(
            newPeriodDurationUs,
            newRepresentation,
            selectedBaseUrl,
            chunkExtractor,
            segmentNumShift,
            newIndex);
      }

      checkStateNotNull(newIndex);

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
          newPeriodDurationUs,
          newRepresentation,
          selectedBaseUrl,
          chunkExtractor,
          newSegmentNumShift,
          newIndex);
    }

    @CheckResult
    /* package */ RepresentationHolder copyWithNewSegmentIndex(DashSegmentIndex segmentIndex) {
      return new RepresentationHolder(
          periodDurationUs,
          representation,
          selectedBaseUrl,
          chunkExtractor,
          segmentNumShift,
          segmentIndex);
    }

    @CheckResult
    /* package */ RepresentationHolder copyWithNewSelectedBaseUrl(BaseUrl selectedBaseUrl) {
      return new RepresentationHolder(
          periodDurationUs,
          representation,
          selectedBaseUrl,
          chunkExtractor,
          segmentNumShift,
          segmentIndex);
    }

    public long getFirstSegmentNum() {
      return checkStateNotNull(segmentIndex).getFirstSegmentNum() + segmentNumShift;
    }

    public long getFirstAvailableSegmentNum(long nowUnixTimeUs) {
      return checkStateNotNull(segmentIndex)
              .getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs)
          + segmentNumShift;
    }

    public long getSegmentCount() {
      return checkStateNotNull(segmentIndex).getSegmentCount(periodDurationUs);
    }

    public long getSegmentStartTimeUs(long segmentNum) {
      return checkStateNotNull(segmentIndex).getTimeUs(segmentNum - segmentNumShift);
    }

    public long getSegmentEndTimeUs(long segmentNum) {
      return getSegmentStartTimeUs(segmentNum)
          + checkStateNotNull(segmentIndex)
              .getDurationUs(segmentNum - segmentNumShift, periodDurationUs);
    }

    public long getSegmentNum(long positionUs) {
      return checkStateNotNull(segmentIndex).getSegmentNum(positionUs, periodDurationUs)
          + segmentNumShift;
    }

    public RangedUri getSegmentUrl(long segmentNum) {
      return checkStateNotNull(segmentIndex).getSegmentUrl(segmentNum - segmentNumShift);
    }

    public long getLastAvailableSegmentNum(long nowUnixTimeUs) {
      return getFirstAvailableSegmentNum(nowUnixTimeUs)
          + checkStateNotNull(segmentIndex)
              .getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs)
          - 1;
    }

    public boolean isSegmentAvailableAtFullNetworkSpeed(long segmentNum, long nowPeriodTimeUs) {
      if (checkStateNotNull(segmentIndex).isExplicit()) {
        // We don't support segment availability for explicit indices (internal ref: b/172894901).
        // Hence, also assume all segments in explicit indices are always available at full network
        // speed even if they end in the future.
        return true;
      }
      return nowPeriodTimeUs == C.TIME_UNSET || getSegmentEndTimeUs(segmentNum) <= nowPeriodTimeUs;
    }
  }
}
