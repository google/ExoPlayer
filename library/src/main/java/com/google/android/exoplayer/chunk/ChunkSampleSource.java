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
package com.google.android.exoplayer.chunk;

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
import com.google.android.exoplayer.chunk.ChunkSampleSourceEventListener.EventDispatcher;
import com.google.android.exoplayer.extractor.DefaultTrackOutput;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;

import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link SampleSource} that loads media in {@link Chunk}s, which are themselves obtained from a
 * {@link ChunkSource}.
 */
public class ChunkSampleSource implements SampleSource, TrackStream, Loader.Callback {

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private static final long NO_RESET_PENDING = Long.MIN_VALUE;

  private final Loader loader;
  private final LoadControl loadControl;
  private final ChunkSource chunkSource;
  private final ChunkHolder nextChunkHolder;
  private final LinkedList<BaseMediaChunk> mediaChunks;
  private final List<BaseMediaChunk> readOnlyMediaChunks;
  private final DefaultTrackOutput sampleQueue;
  private final int bufferSizeContribution;
  private final EventDispatcher eventDispatcher;

  private boolean prepared;
  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;
  private long lastPreferredQueueSizeEvaluationTimeMs;
  private boolean pendingReset;
  private boolean loadControlRegistered;

  private TrackGroupArray trackGroups;
  private long durationUs;
  private boolean loadingFinished;
  private boolean trackEnabled;
  private long currentLoadStartTimeMs;

  private Chunk currentLoadable;
  private Format downstreamFormat;
  private Format downstreamSampleFormat;

  /**
   * @param chunkSource A {@link ChunkSource} from which chunks to load are obtained.
   * @param loadControl Controls when the source is permitted to load data.
   * @param bufferSizeContribution The contribution of this source to the media buffer, in bytes.
   */
  public ChunkSampleSource(ChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution) {
    this(chunkSource, loadControl, bufferSizeContribution, null, null, 0);
  }

  /**
   * @param chunkSource A {@link ChunkSource} from which chunks to load are obtained.
   * @param loadControl Controls when the source is permitted to load data.
   * @param bufferSizeContribution The contribution of this source to the media buffer, in bytes.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param eventSourceId An identifier that gets passed to {@code eventListener} methods.
   */
  public ChunkSampleSource(ChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, Handler eventHandler,
      ChunkSampleSourceEventListener eventListener, int eventSourceId) {
    this(chunkSource, loadControl, bufferSizeContribution, eventHandler, eventListener,
        eventSourceId, DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  /**
   * @param chunkSource A {@link ChunkSource} from which chunks to load are obtained.
   * @param loadControl Controls when the source is permitted to load data.
   * @param bufferSizeContribution The contribution of this source to the media buffer, in bytes.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param eventSourceId An identifier that gets passed to {@code eventListener} methods.
   * @param minLoadableRetryCount The minimum number of times that the source should retry a load
   *     before propagating an error.
   */
  public ChunkSampleSource(ChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, Handler eventHandler,
      ChunkSampleSourceEventListener eventListener, int eventSourceId, int minLoadableRetryCount) {
    this.chunkSource = chunkSource;
    this.loadControl = loadControl;
    this.bufferSizeContribution = bufferSizeContribution;
    loader = new Loader("Loader:ChunkSampleSource", minLoadableRetryCount);
    eventDispatcher = new EventDispatcher(eventHandler, eventListener, eventSourceId);
    nextChunkHolder = new ChunkHolder();
    mediaChunks = new LinkedList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);
    sampleQueue = new DefaultTrackOutput(loadControl.getAllocator());
    pendingResetPositionUs = NO_RESET_PENDING;
  }

  // SampleSource implementation.

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (prepared) {
      return true;
    }
    if (!chunkSource.prepare()) {
      return false;
    }
    durationUs = chunkSource.getDurationUs();
    TrackGroup tracks = chunkSource.getTracks();
    if (tracks != null) {
      trackGroups = new TrackGroupArray(tracks);
    } else {
      trackGroups = new TrackGroupArray();
    }
    prepared = true;
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(oldStreams.size() <= 1);
    Assertions.checkState(newSelections.size() <= 1);
    // Unselect old tracks.
    if (!oldStreams.isEmpty()) {
      Assertions.checkState(trackEnabled);
      trackEnabled = false;
      chunkSource.disable();
    }
    // Select new tracks.
    TrackStream[] newStreams = new TrackStream[newSelections.size()];
    if (!newSelections.isEmpty()) {
      Assertions.checkState(!trackEnabled);
      trackEnabled = true;
      chunkSource.enable(newSelections.get(0).getTracks());
      newStreams[0] = this;
    }
    // Cancel or start requests as necessary.
    if (!trackEnabled) {
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
    } else if (trackEnabled) {
      if (!loadControlRegistered) {
        loadControl.register(this, bufferSizeContribution);
        loadControlRegistered = true;
      }
      downstreamFormat = null;
      downstreamSampleFormat = null;
      downstreamPositionUs = positionUs;
      lastSeekPositionUs = positionUs;
      pendingReset = false;
      restartFrom(positionUs);
    }
    return newStreams;
  }

  @Override
  public void continueBuffering(long positionUs) {
    downstreamPositionUs = positionUs;
    chunkSource.continueBuffering(positionUs);
    maybeStartLoading();
  }

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.END_OF_SOURCE_US;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long largestParsedTimestampUs = sampleQueue.getLargestParsedTimestampUs();
      return largestParsedTimestampUs == Long.MIN_VALUE ? downstreamPositionUs
          : largestParsedTimestampUs;
    }
  }

  @Override
  public void seekToUs(long positionUs) {
    downstreamPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
    // If we're not pending a reset, see if we can seek within the sample queue.
    boolean seekInsideBuffer = !isPendingReset() && sampleQueue.skipToKeyframeBefore(positionUs);
    if (seekInsideBuffer) {
      // We succeeded. All we need to do is discard any chunks that we've moved past.
      boolean haveSamples = !sampleQueue.isEmpty();
      while (haveSamples && mediaChunks.size() > 1
          && mediaChunks.get(1).getFirstSampleIndex() <= sampleQueue.getReadIndex()) {
        mediaChunks.removeFirst();
      }
    } else {
      // We failed, and need to restart.
      restartFrom(positionUs);
    }
    // Either way, we need to send a discontinuity to the downstream components.
    pendingReset = true;
  }

  @Override
  public void release() {
    prepared = false;
    trackEnabled = false;
    loader.release();
  }

  // TrackStream implementation.

  @Override
  public boolean isReady() {
    return loadingFinished || !sampleQueue.isEmpty();
  }

  @Override
  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    chunkSource.maybeThrowError();
  }

  @Override
  public long readReset() {
    if (pendingReset) {
      pendingReset = false;
      return lastSeekPositionUs;
    }
    return TrackStream.NO_RESET;
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
    if (pendingReset || isPendingReset()) {
      return NOTHING_READ;
    }

    boolean haveSamples = !sampleQueue.isEmpty();
    BaseMediaChunk currentChunk = mediaChunks.getFirst();
    while (haveSamples && mediaChunks.size() > 1
        && mediaChunks.get(1).getFirstSampleIndex() <= sampleQueue.getReadIndex()) {
      mediaChunks.removeFirst();
      currentChunk = mediaChunks.getFirst();
    }

    if (downstreamFormat == null || !downstreamFormat.equals(currentChunk.format)) {
      eventDispatcher.downstreamFormatChanged(currentChunk.format, currentChunk.trigger,
          currentChunk.startTimeUs);
      downstreamFormat = currentChunk.format;
    }

    if (haveSamples || currentChunk.isSampleFormatFinal) {
      Format sampleFormat = currentChunk.getSampleFormat();
      if (!sampleFormat.equals(downstreamSampleFormat)) {
        formatHolder.format = sampleFormat;
        formatHolder.drmInitData = currentChunk.getDrmInitData();
        downstreamSampleFormat = sampleFormat;
        return FORMAT_READ;
      }
    }

    if (!haveSamples) {
      if (loadingFinished) {
        buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
        return BUFFER_READ;
      }
      return NOTHING_READ;
    }

    if (sampleQueue.getSample(buffer)) {
      if (buffer.timeUs < lastSeekPositionUs) {
        buffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
      }
      onSampleRead(currentChunk, buffer);
      return BUFFER_READ;
    }

    return NOTHING_READ;
  }

  // Loadable.Callback implementation.

  @Override
  public void onLoadCompleted(Loadable loadable) {
    long now = SystemClock.elapsedRealtime();
    long loadDurationMs = now - currentLoadStartTimeMs;
    chunkSource.onChunkLoadCompleted(currentLoadable);
    if (isMediaChunk(currentLoadable)) {
      BaseMediaChunk mediaChunk = (BaseMediaChunk) currentLoadable;
      eventDispatcher.loadCompleted(currentLoadable.bytesLoaded(), mediaChunk.type,
          mediaChunk.trigger, mediaChunk.format, mediaChunk.startTimeUs, mediaChunk.endTimeUs, now,
          loadDurationMs);
    } else {
      eventDispatcher.loadCompleted(currentLoadable.bytesLoaded(), currentLoadable.type,
          currentLoadable.trigger, currentLoadable.format, -1, -1, now, loadDurationMs);
    }
    clearCurrentLoadable();
    maybeStartLoading();
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    eventDispatcher.loadCanceled(currentLoadable.bytesLoaded());
    if (trackEnabled) {
      restartFrom(pendingResetPositionUs);
    } else {
      clearState();
      loadControl.trimAllocator();
    }
  }

  @Override
  public int onLoadError(Loadable loadable, IOException e) {
    long bytesLoaded = currentLoadable.bytesLoaded();
    boolean isMediaChunk = isMediaChunk(currentLoadable);
    boolean cancelable = !isMediaChunk || bytesLoaded == 0 || mediaChunks.size() > 1;
    if (chunkSource.onChunkLoadError(currentLoadable, cancelable, e)) {
      if (isMediaChunk) {
        BaseMediaChunk removed = mediaChunks.removeLast();
        Assertions.checkState(removed == currentLoadable);
        sampleQueue.discardUpstreamSamples(removed.getFirstSampleIndex());
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        }
      }
      clearCurrentLoadable();
      eventDispatcher.loadError(e);
      eventDispatcher.loadCanceled(bytesLoaded);
      maybeStartLoading();
      return Loader.DONT_RETRY;
    } else {
      eventDispatcher.loadError(e);
      return Loader.RETRY;
    }
  }

  // Internal methods.

  /**
   * Called when a sample has been read. Can be used to perform any modifications necessary before
   * the sample is returned.
   *
   * @param mediaChunk The chunk from which the sample was obtained.
   * @param buffer Holds the read sample.
   */
  protected void onSampleRead(MediaChunk mediaChunk, DecoderInputBuffer buffer) {
    // Do nothing.
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
    sampleQueue.clear();
    mediaChunks.clear();
    clearCurrentLoadable();
  }

  private void clearCurrentLoadable() {
    currentLoadable = null;
  }

  private void maybeStartLoading() {
    if (loader.isLoading()) {
      return;
    }

    long now = SystemClock.elapsedRealtime();
    if (now - lastPreferredQueueSizeEvaluationTimeMs > 5000) {
      int queueSize = chunkSource.getPreferredQueueSize(downstreamPositionUs, readOnlyMediaChunks);
      // Never discard the first chunk.
      discardUpstreamMediaChunks(Math.max(1, queueSize));
      lastPreferredQueueSizeEvaluationTimeMs = now;
    }

    long nextLoadPositionUs = getNextLoadPositionUs();
    boolean isNext = loadControl.update(this, downstreamPositionUs, nextLoadPositionUs, false);
    if (!isNext) {
      return;
    }

    chunkSource.getNextChunk(mediaChunks.isEmpty() ? null : mediaChunks.getLast(),
        pendingResetPositionUs != NO_RESET_PENDING ? pendingResetPositionUs : downstreamPositionUs,
        nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    Chunk nextLoadable = nextChunkHolder.chunk;
    nextChunkHolder.clear();

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
    if (isMediaChunk(currentLoadable)) {
      BaseMediaChunk mediaChunk = (BaseMediaChunk) currentLoadable;
      mediaChunk.init(sampleQueue);
      mediaChunks.add(mediaChunk);
      if (isPendingReset()) {
        pendingResetPositionUs = NO_RESET_PENDING;
      }
      eventDispatcher.loadStarted(mediaChunk.dataSpec.length, mediaChunk.type, mediaChunk.trigger,
          mediaChunk.format, mediaChunk.startTimeUs, mediaChunk.endTimeUs);
    } else {
      eventDispatcher.loadStarted(currentLoadable.dataSpec.length, currentLoadable.type,
          currentLoadable.trigger, currentLoadable.format, -1, -1);
    }
    loader.startLoading(currentLoadable, this);
    // Update the load control again to indicate that we're now loading.
    loadControl.update(this, downstreamPositionUs, getNextLoadPositionUs(), true);
  }

  /**
   * Gets the next load time, assuming that the next load starts where the previous chunk ended (or
   * from the pending reset time, if there is one).
   */
  private long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished ? -1 : mediaChunks.getLast().endTimeUs;
    }
  }

  /**
   * Discard upstream media chunks until the queue length is equal to the length specified.
   *
   * @param queueLength The desired length of the queue.
   * @return True if chunks were discarded. False otherwise.
   */
  private boolean discardUpstreamMediaChunks(int queueLength) {
    if (mediaChunks.size() <= queueLength) {
      return false;
    }
    long startTimeUs = 0;
    long endTimeUs = mediaChunks.getLast().endTimeUs;

    BaseMediaChunk removed = null;
    while (mediaChunks.size() > queueLength) {
      removed = mediaChunks.removeLast();
      startTimeUs = removed.startTimeUs;
      loadingFinished = false;
    }
    sampleQueue.discardUpstreamSamples(removed.getFirstSampleIndex());
    eventDispatcher.upstreamDiscarded(startTimeUs, endTimeUs);
    return true;
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof BaseMediaChunk;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != NO_RESET_PENDING;
  }

}
