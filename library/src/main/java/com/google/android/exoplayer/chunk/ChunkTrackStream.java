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
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.chunk.ChunkTrackStreamEventListener.EventDispatcher;
import com.google.android.exoplayer.extractor.DefaultTrackOutput;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.util.Assertions;

import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link TrackStream} that loads media in {@link Chunk}s, obtained from a {@link ChunkSource}.
 */
public class ChunkTrackStream implements TrackStream, Loader.Callback<Chunk> {

  private final Loader loader;
  private final ChunkSource chunkSource;
  private final int minLoadableRetryCount;
  private final LinkedList<BaseMediaChunk> mediaChunks;
  private final List<BaseMediaChunk> readOnlyMediaChunks;
  private final DefaultTrackOutput sampleQueue;
  private final ChunkHolder nextChunkHolder;
  private final EventDispatcher eventDispatcher;
  private final LoadControl loadControl;

  private boolean readingEnabled;
  private long lastPreferredQueueSizeEvaluationTimeMs;
  private Format downstreamFormat;

  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private boolean loadingFinished;
  private boolean released;

  /**
   * @param chunkSource A {@link ChunkSource} from which chunks to load are obtained.
   * @param loadControl Controls when the source is permitted to load data.
   * @param bufferSizeContribution The contribution of this source to the media buffer, in bytes.
   * @param positionUs The position from which to start loading media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param eventSourceId An identifier that gets passed to {@code eventListener} methods.
   * @param minLoadableRetryCount The minimum number of times that the source should retry a load
   *     before propagating an error.
   */
  public ChunkTrackStream(ChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, long positionUs, Handler eventHandler,
      ChunkTrackStreamEventListener eventListener, int eventSourceId, int minLoadableRetryCount) {
    this.chunkSource = chunkSource;
    this.loadControl = loadControl;
    this.minLoadableRetryCount = minLoadableRetryCount;
    loader = new Loader("Loader:ChunkTrackStream");
    eventDispatcher = new EventDispatcher(eventHandler, eventListener, eventSourceId);
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
    loadControl.unregister(this);
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      clearState();
      loadControl.trimAllocator();
    }
    loader.release();
    released = true;
  }

  // TrackStream implementation.

  @Override
  public boolean isReady() {
    return loadingFinished || (!isPendingReset() && !sampleQueue.isEmpty());
  }

  @Override
  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    chunkSource.maybeThrowError();
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
      eventDispatcher.downstreamFormatChanged(format, currentChunk.trigger,
          currentChunk.startTimeUs);
    }
    downstreamFormat = format;

    return sampleQueue.readData(formatHolder, buffer, loadingFinished, lastSeekPositionUs);
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Chunk loadable, long elapsedMs) {
    long now = SystemClock.elapsedRealtime();
    chunkSource.onChunkLoadCompleted(loadable);
    if (isMediaChunk(loadable)) {
      BaseMediaChunk mediaChunk = (BaseMediaChunk) loadable;
      eventDispatcher.loadCompleted(loadable.bytesLoaded(), mediaChunk.type,
          mediaChunk.trigger, mediaChunk.format, mediaChunk.startTimeUs, mediaChunk.endTimeUs, now,
          elapsedMs);
    } else {
      eventDispatcher.loadCompleted(loadable.bytesLoaded(), loadable.type, loadable.trigger,
          loadable.format, -1, -1, now, elapsedMs);
    }
    maybeStartLoading();
  }

  @Override
  public void onLoadCanceled(Chunk loadable, long elapsedMs) {
    eventDispatcher.loadCanceled(loadable.bytesLoaded());
    if (!released) {
      restartFrom(pendingResetPositionUs);
    } else {
      clearState();
      loadControl.trimAllocator();
    }
  }

  @Override
  public int onLoadError(Chunk loadable, long elapsedMs, IOException e) {
    long bytesLoaded = loadable.bytesLoaded();
    boolean isMediaChunk = isMediaChunk(loadable);
    boolean cancelable = !isMediaChunk || bytesLoaded == 0 || mediaChunks.size() > 1;
    if (chunkSource.onChunkLoadError(loadable, cancelable, e)) {
      if (isMediaChunk) {
        BaseMediaChunk removed = mediaChunks.removeLast();
        Assertions.checkState(removed == loadable);
        sampleQueue.discardUpstreamSamples(removed.getFirstSampleIndex());
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        }
      }
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
      eventDispatcher.loadStarted(mediaChunk.dataSpec.length, mediaChunk.type, mediaChunk.trigger,
          mediaChunk.format, mediaChunk.startTimeUs, mediaChunk.endTimeUs);
    } else {
      eventDispatcher.loadStarted(loadable.dataSpec.length, loadable.type, loadable.trigger,
          loadable.format, -1, -1);
    }
    loader.startLoading(loadable, this, minLoadableRetryCount);
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
    eventDispatcher.upstreamDiscarded(startTimeUs, endTimeUs);
    return true;
  }

}
