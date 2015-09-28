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
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.SampleSource.SampleSourceReader;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.BaseChunkSampleSourceEventListener;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;

import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.util.LinkedList;

/**
 * A {@link SampleSource} for HLS streams.
 */
public final class HlsSampleSource implements SampleSource, SampleSourceReader, Loader.Callback {

  /**
   * Interface definition for a callback to be notified of {@link HlsSampleSource} events.
   */
  public interface EventListener extends BaseChunkSampleSourceEventListener {}

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private static final long NO_RESET_PENDING = Long.MIN_VALUE;

  private final HlsChunkSource chunkSource;
  private final LinkedList<HlsExtractorWrapper> extractors;
  private final int minLoadableRetryCount;
  private final int bufferSizeContribution;
  private final ChunkOperationHolder chunkOperationHolder;

  private final int eventSourceId;
  private final LoadControl loadControl;
  private final Handler eventHandler;
  private final EventListener eventListener;

  private int remainingReleaseCount;
  private boolean prepared;
  private boolean loadControlRegistered;
  private int trackCount;
  private int enabledTrackCount;
  private boolean[] trackEnabledStates;
  private boolean[] pendingDiscontinuities;
  private MediaFormat[] trackFormat;
  private MediaFormat[] downstreamMediaFormats;
  private Format downstreamFormat;

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
  public SampleSourceReader register() {
    remainingReleaseCount++;
    return this;
  }

  @Override
  public boolean prepare(long positionUs) {
    if (prepared) {
      return true;
    }
    if (!extractors.isEmpty()) {
      while (true) {
        // We're not prepared, but we might have loaded what we need.
        HlsExtractorWrapper extractor = extractors.getFirst();
        if (extractor.isPrepared()) {
          trackCount = extractor.getTrackCount();
          trackEnabledStates = new boolean[trackCount];
          pendingDiscontinuities = new boolean[trackCount];
          downstreamMediaFormats = new MediaFormat[trackCount];
          trackFormat = new MediaFormat[trackCount];
          long durationUs = chunkSource.getDurationUs();
          for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getMediaFormat(i).copyWithDurationUs(durationUs);
            if (MimeTypes.isVideo(format.mimeType)) {
              format = format.copyAsAdaptive();
            }
            trackFormat[i] = format;
          }
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
    }
    if (!loadControlRegistered) {
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
    return false;
  }

  @Override
  public int getTrackCount() {
    Assertions.checkState(prepared);
    return trackCount;
  }

  @Override
  public MediaFormat getFormat(int track) {
    Assertions.checkState(prepared);
    return trackFormat[track];
  }

  @Override
  public void enable(int track, long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(!trackEnabledStates[track]);
    enabledTrackCount++;
    trackEnabledStates[track] = true;
    downstreamMediaFormats[track] = null;
    pendingDiscontinuities[track] = false;
    downstreamFormat = null;
    boolean wasLoadControlRegistered = loadControlRegistered;
    if (!loadControlRegistered) {
      loadControl.register(this, bufferSizeContribution);
      loadControlRegistered = true;
    }
    if (enabledTrackCount == 1) {
      lastSeekPositionUs = positionUs;
      if (wasLoadControlRegistered && downstreamPositionUs == positionUs) {
        // TODO: Address [Internal: b/21743989] to remove the need for this kind of hack.
        // This is the first track to be enabled after preparation and the position is the same as
        // was passed to prepare. In this case we can avoid restarting, which would reload the same
        // chunks as were loaded during preparation.
        maybeStartLoading();
      } else {
        downstreamPositionUs = positionUs;
        restartFrom(positionUs);
      }
    }
  }

  @Override
  public void disable(int track) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackEnabledStates[track]);
    enabledTrackCount--;
    trackEnabledStates[track] = false;
    if (enabledTrackCount == 0) {
      chunkSource.reset();
      downstreamPositionUs = Long.MIN_VALUE;
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
  }

  @Override
  public boolean continueBuffering(int track, long playbackPositionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackEnabledStates[track]);
    downstreamPositionUs = playbackPositionUs;
    if (!extractors.isEmpty()) {
      discardSamplesForDisabledTracks(getCurrentExtractor(), downstreamPositionUs);
    }
    if (loadingFinished) {
      return true;
    }
    maybeStartLoading();
    if (isPendingReset() || extractors.isEmpty()) {
      return false;
    }
    for (int extractorIndex = 0; extractorIndex < extractors.size(); extractorIndex++) {
      HlsExtractorWrapper extractor = extractors.get(extractorIndex);
      if (!extractor.isPrepared()) {
        break;
      }
      if (extractor.hasSamples(track)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int readData(int track, long playbackPositionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder, boolean onlyReadDiscontinuity) {
    Assertions.checkState(prepared);
    downstreamPositionUs = playbackPositionUs;

    if (pendingDiscontinuities[track]) {
      pendingDiscontinuities[track] = false;
      return DISCONTINUITY_READ;
    }

    if (onlyReadDiscontinuity) {
      return NOTHING_READ;
    }

    if (isPendingReset()) {
      return NOTHING_READ;
    }

    HlsExtractorWrapper extractor = getCurrentExtractor();
    if (!extractor.isPrepared()) {
      return NOTHING_READ;
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
    while (extractors.size() > extractorIndex + 1 && !extractor.hasSamples(track)) {
      // We're finished reading from the extractor for this particular track, so advance to the
      // next one for the current read.
      extractor = extractors.get(++extractorIndex);
      if (!extractor.isPrepared()) {
        return NOTHING_READ;
      }
    }

    MediaFormat mediaFormat = extractor.getMediaFormat(track);
    if (mediaFormat != null && !mediaFormat.equals(downstreamMediaFormats[track])) {
      formatHolder.format = mediaFormat;
      downstreamMediaFormats[track] = mediaFormat;
      return FORMAT_READ;
    }

    if (extractor.getSample(track, sampleHolder)) {
      boolean decodeOnly = sampleHolder.timeUs < lastSeekPositionUs;
      sampleHolder.flags |= decodeOnly ? C.SAMPLE_FLAG_DECODE_ONLY : 0;
      return SAMPLE_READ;
    }

    if (loadingFinished) {
      return END_OF_STREAM;
    }

    return NOTHING_READ;
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (currentLoadableException != null && currentLoadableExceptionCount > minLoadableRetryCount) {
      throw currentLoadableException;
    } else if (currentLoadable == null) {
      chunkSource.maybeThrowError();
    }
  }

  @Override
  public void seekToUs(long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(enabledTrackCount > 0);

    long currentPositionUs = isPendingReset() ? pendingResetPositionUs : downstreamPositionUs;
    downstreamPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
    if (currentPositionUs == positionUs) {
      return;
    }

    // TODO: Optimize the seek for the case where the position is already buffered.
    downstreamPositionUs = positionUs;
    for (int i = 0; i < pendingDiscontinuities.length; i++) {
      pendingDiscontinuities[i] = true;
    }
    restartFrom(positionUs);
  }

  @Override
  public long getBufferedPositionUs() {
    Assertions.checkState(prepared);
    Assertions.checkState(enabledTrackCount > 0);
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else if (loadingFinished) {
      return TrackRenderer.END_OF_TRACK_US;
    } else {
      long largestParsedTimestampUs = extractors.getLast().getLargestParsedTimestampUs();
      if (extractors.size() > 1) {
        // When adapting from one format to the next, the penultimate extractor may have the largest
        // parsed timestamp (e.g. if the last extractor hasn't parsed any timestamps yet).
        largestParsedTimestampUs = Math.max(largestParsedTimestampUs,
            extractors.get(extractors.size() - 2).getLargestParsedTimestampUs());
      }
      return largestParsedTimestampUs == Long.MIN_VALUE ? downstreamPositionUs
          : largestParsedTimestampUs;
    }
  }

  @Override
  public void release() {
    Assertions.checkState(remainingReleaseCount > 0);
    if (--remainingReleaseCount == 0 && loader != null) {
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
    if (enabledTrackCount > 0 || !prepared) {
      maybeStartLoading();
    }
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
    if (chunkSource.onChunkLoadError(currentLoadable, e)) {
      // Error handled by source.
      if (previousTsLoadable == null && !isPendingReset()) {
        pendingResetPositionUs = lastSeekPositionUs;
      }
      clearCurrentLoadable();
    } else {
      currentLoadableException = e;
      currentLoadableExceptionCount++;
      currentLoadableExceptionTimestamp = SystemClock.elapsedRealtime();
    }
    notifyLoadError(e);
    maybeStartLoading();
  }

  // Internal stuff.

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
    for (int i = 0; i < trackEnabledStates.length; i++) {
      if (!trackEnabledStates[i]) {
        extractor.discardUntil(i, timeUs);
      }
    }
  }

  private boolean haveSamplesForEnabledTracks(HlsExtractorWrapper extractor) {
    if (!extractor.isPrepared()) {
      return false;
    }
    for (int i = 0; i < trackEnabledStates.length; i++) {
      if (trackEnabledStates[i] && extractor.hasSamples(i)) {
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

    if (loader.isLoading() || !nextLoader) {
      return;
    }

    chunkSource.getChunkOperation(previousTsLoadable, pendingResetPositionUs,
        downstreamPositionUs, chunkOperationHolder);
    boolean endOfStream = chunkOperationHolder.endOfStream;
    Chunk nextLoadable = chunkOperationHolder.chunk;
    chunkOperationHolder.clear();

    if (endOfStream) {
      loadingFinished = true;
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
      return loadingFinished ? -1
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

}
