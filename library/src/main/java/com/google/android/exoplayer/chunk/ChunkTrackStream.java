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

import com.google.android.exoplayer.AdaptiveSourceEventListener.EventDispatcher;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DecoderInputBuffer;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.extractor.DefaultTrackOutput;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.util.Assertions;

import android.os.SystemClock;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link TrackStream} that loads media in {@link Chunk}s, obtained from a {@link ChunkSource}.
 */
public class ChunkTrackStream<T extends ChunkSource> implements TrackStream,
    Loader.Callback<Chunk> {

  private final int trackType;
  private final T chunkSource;
  private final LoadControl loadControl;
  private final EventDispatcher eventDispatcher;
  private final int minLoadableRetryCount;
  private final LinkedList<BaseMediaChunk> mediaChunks;
  private final List<BaseMediaChunk> readOnlyMediaChunks;
  private final DefaultTrackOutput sampleQueue;
  private final ChunkHolder nextChunkHolder;
  private final Loader loader;

  private boolean readingEnabled;
  private long lastPreferredQueueSizeEvaluationTimeMs;
  private Format downstreamFormat;

  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private boolean loadingFinished;

  /**
   * @param trackType The type of the track. One of the {@link C} {@code TRACK_TYPE_*} constants.
   * @param chunkSource A {@link ChunkSource} from which chunks to load are obtained.
   * @param loadControl Controls when the source is permitted to load data.
   * @param bufferSizeContribution The contribution of this source to the media buffer, in bytes.
   * @param positionUs The position from which to start loading media.
   * @param minLoadableRetryCount The minimum number of times that the source should retry a load
   *     before propagating an error.
   * @param eventDispatcher A dispatcher to notify of events.
   */
  public ChunkTrackStream(int trackType, T chunkSource, LoadControl loadControl,
      int bufferSizeContribution, long positionUs, int minLoadableRetryCount,
      EventDispatcher eventDispatcher) {
    this.trackType = trackType;
    this.chunkSource = chunkSource;
    this.loadControl = loadControl;
    this.eventDispatcher = eventDispatcher;
    this.minLoadableRetryCount = minLoadableRetryCount;
    loader = new Loader("Loader:ChunkTrackStream");
    nextChunkHolder = new ChunkHolder();
    mediaChunks = new LinkedList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);
    sampleQueue = new DefaultTrackOutput(loadControl.getAllocator());
    pendingResetPositionUs = C.UNSET_TIME_US;
    readingEnabled = true;
    downstreamPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
    loadControl.register(this, bufferSizeContribution);
    restartFrom(positionUs);
  }

  /**
   * Enables or disables reading of data from {@link #readData(FormatHolder, DecoderInputBuffer)}.
   *
   * @param readingEnabled Whether reading should be enabled.
   */
  public void setReadingEnabled(boolean readingEnabled) {
    this.readingEnabled = readingEnabled;
  }

  // TODO[REFACTOR]: Find a way to get rid of this.
  public void continueBuffering(long positionUs) {
    downstreamPositionUs = positionUs;
    if (!loader.isLoading()) {
      maybeStartLoading();
    }
  }

  /**
   * Returns the {@link ChunkSource} used by this stream.
   *
   * @return The {@link ChunkSource}.
   */
  public T getChunkSource() {
    return chunkSource;
  }

  /**
   * Returns an estimate of the position up to which data is buffered.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered, or
   *     {@link C#END_OF_SOURCE_US} if the track is fully buffered.
   */
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.END_OF_SOURCE_US;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = downstreamPositionUs;
      BaseMediaChunk lastMediaChunk = mediaChunks.getLast();
      BaseMediaChunk lastCompletedMediaChunk = lastMediaChunk.isLoadCompleted() ? lastMediaChunk
          : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = Math.max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      return Math.max(bufferedPositionUs, sampleQueue.getLargestQueuedTimestampUs());
    }
  }

  /**
   * Seeks to the specified position in microseconds.
   *
   * @param positionUs The seek position in microseconds.
   */
  public void seekToUs(long positionUs) {
    downstreamPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
    // If we're not pending a reset, see if we can seek within the sample queue.
    boolean seekInsideBuffer = !isPendingReset() && sampleQueue.skipToKeyframeBefore(positionUs);
    if (seekInsideBuffer) {
      // We succeeded. All we need to do is discard any chunks that we've moved past.
      while (mediaChunks.size() > 1
          && mediaChunks.get(1).getFirstSampleIndex() <= sampleQueue.getReadIndex()) {
        mediaChunks.removeFirst();
      }
    } else {
      // We failed, and need to restart.
      restartFrom(positionUs);
    }
  }

  /**
   * Releases the stream.
   * <p>
   * This method should be called when the stream is no longer required.
   */
  public void release() {
    chunkSource.release();
    sampleQueue.disable();
    loadControl.unregister(this);
    loader.release();
  }

  // TrackStream implementation.

  @Override
  public boolean isReady() {
    return loadingFinished || (!isPendingReset() && !sampleQueue.isEmpty());
  }

  @Override
  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    if (!loader.isLoading()) {
      chunkSource.maybeThrowError();
    }
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
    if (!readingEnabled || isPendingReset()) {
      return NOTHING_READ;
    }

    while (mediaChunks.size() > 1
        && mediaChunks.get(1).getFirstSampleIndex() <= sampleQueue.getReadIndex()) {
      mediaChunks.removeFirst();
    }
    BaseMediaChunk currentChunk = mediaChunks.getFirst();

    Format format = currentChunk.format;
    if (!format.equals(downstreamFormat)) {
      eventDispatcher.downstreamFormatChanged(trackType, format,
          currentChunk.formatEvaluatorTrigger, currentChunk.formatEvaluatorData,
          currentChunk.startTimeUs);
    }
    downstreamFormat = format;

    return sampleQueue.readData(formatHolder, buffer, loadingFinished, lastSeekPositionUs);
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
    chunkSource.onChunkLoadCompleted(loadable);
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, trackType, loadable.format,
        loadable.formatEvaluatorTrigger, loadable.formatEvaluatorData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
    maybeStartLoading();
  }

  @Override
  public void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      boolean released) {
    eventDispatcher.loadCanceled(loadable.dataSpec, loadable.type, trackType, loadable.format,
        loadable.formatEvaluatorTrigger, loadable.formatEvaluatorData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
    if (!released) {
      restartFrom(pendingResetPositionUs);
    }
  }

  @Override
  public int onLoadError(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      IOException error) {
    long bytesLoaded = loadable.bytesLoaded();
    boolean isMediaChunk = isMediaChunk(loadable);
    boolean cancelable = !isMediaChunk || bytesLoaded == 0 || mediaChunks.size() > 1;
    boolean canceled = false;
    if (chunkSource.onChunkLoadError(loadable, cancelable, error)) {
      canceled = true;
      if (isMediaChunk) {
        BaseMediaChunk removed = mediaChunks.removeLast();
        Assertions.checkState(removed == loadable);
        sampleQueue.discardUpstreamSamples(removed.getFirstSampleIndex());
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        }
      }
    }
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, trackType, loadable.format,
        loadable.formatEvaluatorTrigger, loadable.formatEvaluatorData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, bytesLoaded, error,
        canceled);
    if (canceled) {
      maybeStartLoading();
      return Loader.DONT_RETRY;
    } else {
      return Loader.RETRY;
    }
  }

  // Internal methods.

  private void restartFrom(long positionUs) {
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    mediaChunks.clear();
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      sampleQueue.reset(true);
      maybeStartLoading();
    }
  }

  private void maybeStartLoading() {
    long now = SystemClock.elapsedRealtime();
    if (now - lastPreferredQueueSizeEvaluationTimeMs > 5000) {
      int queueSize = chunkSource.getPreferredQueueSize(downstreamPositionUs, readOnlyMediaChunks);
      // Never discard the first chunk.
      discardUpstreamMediaChunks(Math.max(1, queueSize));
      lastPreferredQueueSizeEvaluationTimeMs = now;
    }

    boolean isNext = loadControl.update(this, downstreamPositionUs, getNextLoadPositionUs(), false);
    if (!isNext) {
      return;
    }

    chunkSource.getNextChunk(mediaChunks.isEmpty() ? null : mediaChunks.getLast(),
        pendingResetPositionUs != C.UNSET_TIME_US ? pendingResetPositionUs : downstreamPositionUs,
        nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    Chunk loadable = nextChunkHolder.chunk;
    nextChunkHolder.clear();

    if (endOfStream) {
      loadingFinished = true;
      loadControl.update(this, downstreamPositionUs, C.UNSET_TIME_US, false);
      return;
    }

    if (loadable == null) {
      return;
    }

    if (isMediaChunk(loadable)) {
      pendingResetPositionUs = C.UNSET_TIME_US;
      BaseMediaChunk mediaChunk = (BaseMediaChunk) loadable;
      mediaChunk.init(sampleQueue);
      mediaChunks.add(mediaChunk);
    }
    long elapsedRealtimeMs = loader.startLoading(loadable, this, minLoadableRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, trackType, loadable.format,
        loadable.formatEvaluatorTrigger, loadable.formatEvaluatorData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs);
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
      return loadingFinished ? C.UNSET_TIME_US : mediaChunks.getLast().endTimeUs;
    }
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof BaseMediaChunk;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.UNSET_TIME_US;
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
    eventDispatcher.upstreamDiscarded(trackType, startTimeUs, endTimeUs);
    return true;
  }

}
