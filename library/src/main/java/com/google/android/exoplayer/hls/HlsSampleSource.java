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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DecoderInputBuffer;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.TrackGroupArray;
import com.google.android.exoplayer.TrackSelection;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.chunk.BaseChunkSampleSourceEventListener;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;

import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link SampleSource} for HLS streams.
 */
public final class HlsSampleSource implements SampleSource, Loader.Callback {

  /**
   * Interface definition for a callback to be notified of {@link HlsSampleSource} events.
   */
  public interface EventListener extends BaseChunkSampleSourceEventListener {}

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private static final long NO_RESET_PENDING = Long.MIN_VALUE;

  private static final int PRIMARY_TYPE_NONE = 0;
  private static final int PRIMARY_TYPE_TEXT = 1;
  private static final int PRIMARY_TYPE_AUDIO = 2;
  private static final int PRIMARY_TYPE_VIDEO = 3;

  private final HlsChunkSource chunkSource;
  private final LinkedList<HlsExtractorWrapper> extractors;
  private final int minLoadableRetryCount;
  private final int bufferSizeContribution;
  private final ChunkOperationHolder chunkOperationHolder;

  private final int eventSourceId;
  private final LoadControl loadControl;
  private final Handler eventHandler;
  private final EventListener eventListener;

  private boolean prepared;
  private boolean seenFirstTrackSelection;
  private boolean loadControlRegistered;
  private int enabledTrackCount;

  private Format downstreamFormat;

  // Tracks are complicated in HLS. See documentation of buildTracks for details.
  // Indexed by track (as exposed by this source).
  private TrackGroupArray trackGroups;
  private int primaryTrackGroupIndex;
  // Indexed by group.
  private boolean[] groupEnabledStates;
  private boolean[] pendingResets;
  private Format[] downstreamSampleFormats;

  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private boolean loadingFinished;
  private Chunk currentLoadable;
  private TsChunk currentTsLoadable;
  private TsChunk previousTsLoadable;

  private Loader loader;
  private IOException currentLoadableException;
  private int currentLoadableExceptionCount;
  private long currentLoadableExceptionTimestamp;
  private long currentLoadStartTimeMs;

  public HlsSampleSource(HlsChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution) {
    this(chunkSource, loadControl, bufferSizeContribution, null, null, 0);
  }

  public HlsSampleSource(HlsChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, Handler eventHandler, EventListener eventListener,
      int eventSourceId) {
    this(chunkSource, loadControl, bufferSizeContribution, eventHandler, eventListener,
        eventSourceId, DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  public HlsSampleSource(HlsChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, Handler eventHandler, EventListener eventListener,
      int eventSourceId, int minLoadableRetryCount) {
    this.chunkSource = chunkSource;
    this.loadControl = loadControl;
    this.bufferSizeContribution = bufferSizeContribution;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
    this.pendingResetPositionUs = NO_RESET_PENDING;
    extractors = new LinkedList<>();
    chunkOperationHolder = new ChunkOperationHolder();
  }

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (prepared) {
      return true;
    }
    if (!chunkSource.prepare()) {
      return false;
    }
    if (chunkSource.getTrackCount() == 0) {
      trackGroups = new TrackGroupArray();
      prepared = true;
      return true;
    }
    if (!extractors.isEmpty()) {
      while (true) {
        // We're not prepared, but we might have loaded what we need.
        HlsExtractorWrapper extractor = extractors.getFirst();
        if (extractor.isPrepared()) {
          buildTracks(extractor);
          maybeStartLoading(); // Update the load control.
          prepared = true;
          return true;
        } else if (extractors.size() > 1) {
          extractors.removeFirst().clear();
        } else {
          break;
        }
      }
    }
    // We're not prepared and we haven't loaded what we need.
    if (loader == null) {
      loader = new Loader("Loader:HLS");
      loadControl.register(this, bufferSizeContribution);
      loadControlRegistered = true;
    }
    if (!loader.isLoading()) {
      // We're going to have to start loading a chunk to get what we need for preparation. We should
      // attempt to load the chunk at positionUs, so that we'll already be loading the correct chunk
      // in the common case where the renderer is subsequently enabled at this position.
      pendingResetPositionUs = positionUs;
      downstreamPositionUs = positionUs;
    }
    maybeStartLoading();
    maybeThrowError();
    return false;
  }

  @Override
  public long getDurationUs() {
    return chunkSource.getDurationUs();
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    Assertions.checkState(prepared);
    // Unselect old tracks.
    for (int i = 0; i < oldStreams.size(); i++) {
      int group = ((TrackStreamImpl) oldStreams.get(i)).group;
      setTrackGroupEnabledState(group, false);
    }
    // Select new tracks.
    boolean primaryTracksDeselected = false;
    TrackStream[] newStreams = new TrackStream[newSelections.size()];
    for (int i = 0; i < newStreams.length; i++) {
      TrackSelection selection = newSelections.get(i);
      int group = selection.group;
      int[] tracks = selection.getTracks();
      setTrackGroupEnabledState(group, true);
      downstreamSampleFormats[group] = null;
      pendingResets[group] = false;
      if (group == primaryTrackGroupIndex) {
        primaryTracksDeselected |= chunkSource.selectTracks(tracks);
      }
      newStreams[i] = new TrackStreamImpl(group);
    }
    // Cancel or start requests as necessary.
    if (enabledTrackCount == 0) {
      chunkSource.reset();
      downstreamPositionUs = Long.MIN_VALUE;
      downstreamFormat = null;
      if (loader != null) {
        if (loadControlRegistered) {
          loadControl.unregister(this);
          loadControlRegistered = false;
        }
        if (loader.isLoading()) {
          loader.cancelLoading();
        } else {
          clearState();
          loadControl.trimAllocator();
        }
      }
    } else if (primaryTracksDeselected || (seenFirstTrackSelection && newStreams.length > 0)) {
      if (!loadControlRegistered) {
        loadControl.register(this, bufferSizeContribution);
        loadControlRegistered = true;
      }
      seekToInternal(positionUs);
    }
    seenFirstTrackSelection = true;
    return newStreams;
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    downstreamPositionUs = playbackPositionUs;
    if (!extractors.isEmpty()) {
      discardSamplesForDisabledTracks(getCurrentExtractor(), downstreamPositionUs);
    }
    maybeStartLoading();
  }

  /* package */ boolean isReady(int group) {
    Assertions.checkState(groupEnabledStates[group]);
    if (loadingFinished) {
      return true;
    }
    if (isPendingReset() || extractors.isEmpty()) {
      return false;
    }
    for (int extractorIndex = 0; extractorIndex < extractors.size(); extractorIndex++) {
      HlsExtractorWrapper extractor = extractors.get(extractorIndex);
      if (!extractor.isPrepared()) {
        break;
      }
      if (extractor.hasSamples(group)) {
        return true;
      }
    }
    return false;
  }

  /* package */ long readReset(int group) {
    if (pendingResets[group]) {
      pendingResets[group] = false;
      return lastSeekPositionUs;
    }
    return TrackStream.NO_RESET;
  }

  /* package */ int readData(int group, FormatHolder formatHolder, DecoderInputBuffer buffer) {
    if (pendingResets[group] || isPendingReset()) {
      return TrackStream.NOTHING_READ;
    }

    HlsExtractorWrapper extractor = getCurrentExtractor();
    if (!extractor.isPrepared()) {
      return TrackStream.NOTHING_READ;
    }

    if (downstreamFormat == null || !downstreamFormat.equals(extractor.format)) {
      // Notify a change in the downstream format.
      notifyDownstreamFormatChanged(extractor.format, extractor.trigger, extractor.startTimeUs);
      downstreamFormat = extractor.format;
    }

    if (extractors.size() > 1) {
      // If there's more than one extractor, attempt to configure a seamless splice from the
      // current one to the next one.
      extractor.configureSpliceTo(extractors.get(1));
    }

    int extractorIndex = 0;
    while (extractors.size() > extractorIndex + 1 && !extractor.hasSamples(group)) {
      // We're finished reading from the extractor for this particular track, so advance to the
      // next one for the current read.
      extractor = extractors.get(++extractorIndex);
      if (!extractor.isPrepared()) {
        return TrackStream.NOTHING_READ;
      }
    }

    Format sampleFormat = extractor.getSampleFormat(group);
    if (sampleFormat != null && !sampleFormat.equals(downstreamSampleFormats[group])) {
      formatHolder.format = sampleFormat;
      downstreamSampleFormats[group] = sampleFormat;
      return TrackStream.FORMAT_READ;
    }

    if (extractor.getSample(group, buffer)) {
      if (buffer.timeUs < lastSeekPositionUs) {
        buffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
      }
      return TrackStream.BUFFER_READ;
    }

    if (loadingFinished) {
      buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      return TrackStream.BUFFER_READ;
    }

    return TrackStream.NOTHING_READ;
  }

  /* package */ void maybeThrowError() throws IOException {
    if (currentLoadableException != null && currentLoadableExceptionCount > minLoadableRetryCount) {
      throw currentLoadableException;
    } else if (currentLoadable == null) {
      chunkSource.maybeThrowError();
    }
  }

  @Override
  public void seekToUs(long positionUs) {
    seekToInternal(positionUs);
  }

  @Override
  public long getBufferedPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else if (loadingFinished) {
      return C.END_OF_SOURCE_US;
    } else {
      long bufferedPositionUs = extractors.getLast().getLargestParsedTimestampUs();
      if (extractors.size() > 1) {
        // When adapting from one format to the next, the penultimate extractor may have the largest
        // parsed timestamp (e.g. if the last extractor hasn't parsed any timestamps yet).
        bufferedPositionUs = Math.max(bufferedPositionUs,
            extractors.get(extractors.size() - 2).getLargestParsedTimestampUs());
      }
      if (previousTsLoadable != null) {
        // Buffered position should be at least as large as the end time of the previously loaded
        // chunk.
        bufferedPositionUs = Math.max(previousTsLoadable.endTimeUs, bufferedPositionUs);
      }
      return bufferedPositionUs == Long.MIN_VALUE ? downstreamPositionUs
          : bufferedPositionUs;
    }
  }

  @Override
  public void release() {
    enabledTrackCount = 0;
    if (loader != null) {
      if (loadControlRegistered) {
        loadControl.unregister(this);
        loadControlRegistered = false;
      }
      loader.release();
      loader = null;
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Loadable loadable) {
    Assertions.checkState(loadable == currentLoadable);
    long now = SystemClock.elapsedRealtime();
    long loadDurationMs = now - currentLoadStartTimeMs;
    chunkSource.onChunkLoadCompleted(currentLoadable);
    if (isTsChunk(currentLoadable)) {
      Assertions.checkState(currentLoadable == currentTsLoadable);
      previousTsLoadable = currentTsLoadable;
      notifyLoadCompleted(currentLoadable.bytesLoaded(), currentTsLoadable.type,
          currentTsLoadable.trigger, currentTsLoadable.format, currentTsLoadable.startTimeUs,
          currentTsLoadable.endTimeUs, now, loadDurationMs);
    } else {
      notifyLoadCompleted(currentLoadable.bytesLoaded(), currentLoadable.type,
          currentLoadable.trigger, currentLoadable.format, -1, -1, now, loadDurationMs);
    }
    clearCurrentLoadable();
    maybeStartLoading();
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    notifyLoadCanceled(currentLoadable.bytesLoaded());
    if (enabledTrackCount > 0) {
      restartFrom(pendingResetPositionUs);
    } else {
      clearState();
      loadControl.trimAllocator();
    }
  }

  @Override
  public void onLoadError(Loadable loadable, IOException e) {
    long bytesLoaded = currentLoadable.bytesLoaded();
    boolean cancelable = !isTsChunk(currentLoadable) || bytesLoaded == 0;
    if (chunkSource.onChunkLoadError(currentLoadable, cancelable, e)) {
      if (previousTsLoadable == null && !isPendingReset()) {
        pendingResetPositionUs = lastSeekPositionUs;
      }
      clearCurrentLoadable();
      notifyLoadError(e);
      notifyLoadCanceled(bytesLoaded);
    } else {
      currentLoadableException = e;
      currentLoadableExceptionCount++;
      currentLoadableExceptionTimestamp = SystemClock.elapsedRealtime();
      notifyLoadError(e);
    }
    maybeStartLoading();
  }

  // Internal stuff.

  /**
   * Builds tracks that are exposed by this {@link HlsSampleSource} instance, as well as internal
   * data-structures required for operation.
   * <p>
   * Tracks in HLS are complicated. A HLS master playlist contains a number of "variants". Each
   * variant stream typically contains muxed video, audio and (possibly) additional audio, metadata
   * and caption tracks. We wish to allow the user to select between an adaptive track that spans
   * all variants, as well as each individual variant. If multiple audio tracks are present within
   * each variant then we wish to allow the user to select between those also.
   * <p>
   * To do this, tracks are constructed as follows. The {@link HlsChunkSource} exposes (N+1) tracks,
   * where N is the number of variants defined in the HLS master playlist. These consist of one
   * adaptive track defined to span all variants and a track for each individual variant. The
   * adaptive track is initially selected. The extractor is then prepared to discover the tracks
   * inside of each variant stream. The two sets of tracks are then combined by this method to
   * create a third set, which is the set exposed by this {@link HlsSampleSource}:
   * <ul>
   * <li>The extractor tracks are inspected to infer a "primary" track type. If a video track is
   * present then it is always the primary type. If not, audio is the primary type if present.
   * Else text is the primary type if present. Else there is no primary type.</li>
   * <li>If there is exactly one extractor track of the primary type, it's expanded into (N+1)
   * exposed tracks, all of which correspond to the primary extractor track and each of which
   * corresponds to a different chunk source track. Selecting one of these tracks has the effect
   * of switching the selected track on the chunk source.</li>
   * <li>All other extractor tracks are exposed directly. Selecting one of these tracks has the
   * effect of selecting an extractor track, leaving the selected track on the chunk source
   * unchanged.</li>
   * </ul>
   *
   * @param extractor The prepared extractor.
   */
  private void buildTracks(HlsExtractorWrapper extractor) {
    // Iterate through the extractor tracks to discover the "primary" track type, and the index
    // of the single track of this type.
    int primaryExtractorTrackType = PRIMARY_TYPE_NONE;
    int primaryExtractorTrackIndex = -1;
    int extractorTrackCount = extractor.getTrackCount();
    for (int i = 0; i < extractorTrackCount; i++) {
      String sampleMimeType = extractor.getSampleFormat(i).sampleMimeType;
      int trackType;
      if (MimeTypes.isVideo(sampleMimeType)) {
        trackType = PRIMARY_TYPE_VIDEO;
      } else if (MimeTypes.isAudio(sampleMimeType)) {
        trackType = PRIMARY_TYPE_AUDIO;
      } else if (MimeTypes.isText(sampleMimeType)) {
        trackType = PRIMARY_TYPE_TEXT;
      } else {
        trackType = PRIMARY_TYPE_NONE;
      }
      if (trackType > primaryExtractorTrackType) {
        primaryExtractorTrackType = trackType;
        primaryExtractorTrackIndex = i;
      } else if (trackType == primaryExtractorTrackType && primaryExtractorTrackIndex != -1) {
        // We have multiple tracks of the primary type. We only want an index if there only
        // exists a single track of the primary type, so set the index back to -1.
        primaryExtractorTrackIndex = -1;
      }
    }

    // Calculate the number of tracks that will be exposed.
    int chunkSourceTrackCount = chunkSource.getTrackCount();

    // Instantiate the necessary internal data-structures.
    primaryTrackGroupIndex = -1;
    groupEnabledStates = new boolean[extractorTrackCount];
    pendingResets = new boolean[extractorTrackCount];
    downstreamSampleFormats = new Format[extractorTrackCount];

    // Construct the set of exposed track groups.
    TrackGroup[] trackGroups = new TrackGroup[extractorTrackCount];
    for (int i = 0; i < extractorTrackCount; i++) {
      Format sampleFormat = extractor.getSampleFormat(i);
      if (i == primaryExtractorTrackIndex) {
        Format[] formats = new Format[chunkSourceTrackCount];
        for (int j = 0; j < chunkSourceTrackCount; j++) {
          formats[j] = getSampleFormat(chunkSource.getTrackFormat(j), sampleFormat);
        }
        trackGroups[i] = new TrackGroup(chunkSource.isAdaptive(), formats);
        primaryTrackGroupIndex = i;
      } else {
        Format trackFormat = null;
        if (primaryExtractorTrackType == PRIMARY_TYPE_VIDEO) {
          if (MimeTypes.isAudio(sampleFormat.sampleMimeType)) {
            trackFormat = chunkSource.getMuxedAudioFormat();
          } else if (MimeTypes.APPLICATION_EIA608.equals(sampleFormat.sampleMimeType)) {
            trackFormat = chunkSource.getMuxedCaptionFormat();
          }
        }
        trackGroups[i] = new TrackGroup(getSampleFormat(trackFormat, sampleFormat));
      }
    }
    this.trackGroups = new TrackGroupArray(trackGroups);
  }

  /**
   * Enables or disables a specified track group.
   *
   * @param group The index of the track group.
   * @param enabledState True if the group is being enabled, or false if it's being disabled.
   */
  private void setTrackGroupEnabledState(int group, boolean enabledState) {
    Assertions.checkState(groupEnabledStates[group] != enabledState);
    groupEnabledStates[group] = enabledState;
    enabledTrackCount = enabledTrackCount + (enabledState ? 1 : -1);
  }

  /**
   * Derives a sample format corresponding to a given container format, by combining it with sample
   * level information obtained from a second sample format.
   *
   * @param containerFormat The container format for which the sample format should be derived.
   * @param sampleFormat A sample format from which to obtain sample level information.
   * @return The derived sample format.
   */
  private static Format getSampleFormat(Format containerFormat, Format sampleFormat) {
    if (containerFormat == null) {
      return sampleFormat;
    }
    int width = containerFormat.width == -1 ? Format.NO_VALUE : containerFormat.width;
    int height = containerFormat.height == -1 ? Format.NO_VALUE : containerFormat.height;
    return sampleFormat.copyWithContainerInfo(containerFormat.id, containerFormat.bitrate, width,
        height, containerFormat.language);
  }

  /**
   * Performs a seek. The operation is performed even if the seek is to the current position.
   *
   * @param positionUs The position to seek to.
   */
  private void seekToInternal(long positionUs) {
    // Treat all seeks into non-seekable media as being to t=0.
    positionUs = chunkSource.isLive() ? 0 : positionUs;
    lastSeekPositionUs = positionUs;
    downstreamPositionUs = positionUs;
    Arrays.fill(pendingResets, true);
    chunkSource.seek();
    restartFrom(positionUs);
  }

  /**
   * Gets the current extractor from which samples should be read.
   * <p>
   * Calling this method discards extractors without any samples from the front of the queue. The
   * last extractor is retained even if it doesn't have any samples.
   * <p>
   * This method must not be called unless {@link #extractors} is non-empty.
   *
   * @return The current extractor from which samples should be read. Guaranteed to be non-null.
   */
  private HlsExtractorWrapper getCurrentExtractor() {
    HlsExtractorWrapper extractor = extractors.getFirst();
    while (extractors.size() > 1 && !haveSamplesForEnabledTracks(extractor)) {
      // We're finished reading from the extractor for all tracks, and so can discard it.
      extractors.removeFirst().clear();
      extractor = extractors.getFirst();
    }
    return extractor;
  }

  private void discardSamplesForDisabledTracks(HlsExtractorWrapper extractor, long timeUs) {
    if (!extractor.isPrepared()) {
      return;
    }
    for (int i = 0; i < groupEnabledStates.length; i++) {
      if (!groupEnabledStates[i]) {
        extractor.discardUntil(i, timeUs);
      }
    }
  }

  private boolean haveSamplesForEnabledTracks(HlsExtractorWrapper extractor) {
    if (!extractor.isPrepared()) {
      return false;
    }
    for (int i = 0; i < groupEnabledStates.length; i++) {
      if (groupEnabledStates[i] && extractor.hasSamples(i)) {
        return true;
      }
    }
    return false;
  }

  private void restartFrom(long positionUs) {
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      clearState();
      maybeStartLoading();
    }
  }

  private void clearState() {
    for (int i = 0; i < extractors.size(); i++) {
      extractors.get(i).clear();
    }
    extractors.clear();
    clearCurrentLoadable();
    previousTsLoadable = null;
  }

  private void clearCurrentLoadable() {
    currentTsLoadable = null;
    currentLoadable = null;
    currentLoadableException = null;
    currentLoadableExceptionCount = 0;
  }

  private void maybeStartLoading() {
    long now = SystemClock.elapsedRealtime();
    long nextLoadPositionUs = getNextLoadPositionUs();
    boolean isBackedOff = currentLoadableException != null;
    boolean loadingOrBackedOff = loader.isLoading() || isBackedOff;

    // Update the control with our current state, and determine whether we're the next loader.
    boolean nextLoader = loadControl.update(this, downstreamPositionUs, nextLoadPositionUs,
        loadingOrBackedOff);

    if (isBackedOff) {
      long elapsedMillis = now - currentLoadableExceptionTimestamp;
      if (elapsedMillis >= getRetryDelayMillis(currentLoadableExceptionCount)) {
        currentLoadableException = null;
        loader.startLoading(currentLoadable, this);
      }
      return;
    }

    if (loader.isLoading() || !nextLoader || (prepared && enabledTrackCount == 0)) {
      return;
    }

    chunkSource.getChunkOperation(previousTsLoadable,
        pendingResetPositionUs != NO_RESET_PENDING ? pendingResetPositionUs : downstreamPositionUs,
        chunkOperationHolder);
    boolean endOfStream = chunkOperationHolder.endOfStream;
    Chunk nextLoadable = chunkOperationHolder.chunk;
    chunkOperationHolder.clear();

    if (endOfStream) {
      loadingFinished = true;
      loadControl.update(this, downstreamPositionUs, -1, false);
      return;
    }
    if (nextLoadable == null) {
      return;
    }

    currentLoadStartTimeMs = now;
    currentLoadable = nextLoadable;
    if (isTsChunk(currentLoadable)) {
      TsChunk tsChunk = (TsChunk) currentLoadable;
      if (isPendingReset()) {
        pendingResetPositionUs = NO_RESET_PENDING;
      }
      HlsExtractorWrapper extractorWrapper = tsChunk.extractorWrapper;
      if (extractors.isEmpty() || extractors.getLast() != extractorWrapper) {
        extractorWrapper.init(loadControl.getAllocator());
        extractors.addLast(extractorWrapper);
      }
      notifyLoadStarted(tsChunk.dataSpec.length, tsChunk.type, tsChunk.trigger, tsChunk.format,
          tsChunk.startTimeUs, tsChunk.endTimeUs);
      currentTsLoadable = tsChunk;
    } else {
      notifyLoadStarted(currentLoadable.dataSpec.length, currentLoadable.type,
          currentLoadable.trigger, currentLoadable.format, -1, -1);
    }
    loader.startLoading(currentLoadable, this);
  }

  /**
   * Gets the next load time, assuming that the next load starts where the previous chunk ended (or
   * from the pending reset time, if there is one).
   */
  private long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished || (prepared && enabledTrackCount == 0) ? -1
          : currentTsLoadable != null ? currentTsLoadable.endTimeUs : previousTsLoadable.endTimeUs;
    }
  }

  private boolean isTsChunk(Chunk chunk) {
    return chunk instanceof TsChunk;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != NO_RESET_PENDING;
  }

  private long getRetryDelayMillis(long errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  /* package */ long usToMs(long timeUs) {
    return timeUs / 1000;
  }

  private void notifyLoadStarted(final long length, final int type, final int trigger,
      final Format format, final long mediaStartTimeUs, final long mediaEndTimeUs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadStarted(eventSourceId, length, type, trigger, format,
              usToMs(mediaStartTimeUs), usToMs(mediaEndTimeUs));
        }
      });
    }
  }

  private void notifyLoadCompleted(final long bytesLoaded, final int type, final int trigger,
      final Format format, final long mediaStartTimeUs, final long mediaEndTimeUs,
      final long elapsedRealtimeMs, final long loadDurationMs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadCompleted(eventSourceId, bytesLoaded, type, trigger, format,
              usToMs(mediaStartTimeUs), usToMs(mediaEndTimeUs), elapsedRealtimeMs, loadDurationMs);
        }
      });
    }
  }

  private void notifyLoadCanceled(final long bytesLoaded) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadCanceled(eventSourceId, bytesLoaded);
        }
      });
    }
  }

  private void notifyLoadError(final IOException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadError(eventSourceId, e);
        }
      });
    }
  }

  private void notifyDownstreamFormatChanged(final Format format, final int trigger,
      final long positionUs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDownstreamFormatChanged(eventSourceId, format, trigger,
              usToMs(positionUs));
        }
      });
    }
  }

  private final class TrackStreamImpl implements TrackStream {

    private final int group;

    public TrackStreamImpl(int group) {
      this.group = group;
    }

    @Override
    public boolean isReady() {
      return HlsSampleSource.this.isReady(group);
    }

    @Override
    public void maybeThrowError() throws IOException {
      HlsSampleSource.this.maybeThrowError();
    }

    @Override
    public long readReset() {
      return HlsSampleSource.this.readReset(group);
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
      return HlsSampleSource.this.readData(group, formatHolder, buffer);
    }

  }

}
