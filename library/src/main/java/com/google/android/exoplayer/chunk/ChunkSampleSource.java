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
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.TrackGroupArray;
import com.google.android.exoplayer.TrackSelection;
import com.google.android.exoplayer.TrackStream;
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
   * Interface definition for a callback to be notified of {@link ChunkSampleSource} events.
   */
  public interface EventListener extends BaseChunkSampleSourceEventListener {}

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private static final int STATE_IDLE = 0;
  private static final int STATE_PREPARED = 1;
  private static final int STATE_ENABLED = 2;

  private static final long NO_RESET_PENDING = Long.MIN_VALUE;

  private final int eventSourceId;
  private final LoadControl loadControl;
  private final ChunkSource chunkSource;
  private final ChunkOperationHolder currentLoadableHolder;
  private final LinkedList<BaseMediaChunk> mediaChunks;
  private final List<BaseMediaChunk> readOnlyMediaChunks;
  private final DefaultTrackOutput sampleQueue;
  private final int bufferSizeContribution;
  private final Handler eventHandler;
  private final EventListener eventListener;
  private final int minLoadableRetryCount;

  private int state;
  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;
  private long lastPerformedBufferOperation;
  private boolean pendingReset;

  private TrackGroupArray trackGroups;
  private long durationUs;
  private Loader loader;
  private boolean loadingFinished;
  private IOException currentLoadableException;
  private int enabledTrackCount;
  private int currentLoadableExceptionCount;
  private long currentLoadableExceptionTimestamp;
  private long currentLoadStartTimeMs;

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
      int bufferSizeContribution, Handler eventHandler, EventListener eventListener,
      int eventSourceId) {
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
      int bufferSizeContribution, Handler eventHandler, EventListener eventListener,
      int eventSourceId, int minLoadableRetryCount) {
    this.chunkSource = chunkSource;
    this.loadControl = loadControl;
    this.bufferSizeContribution = bufferSizeContribution;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
    this.minLoadableRetryCount = minLoadableRetryCount;
    currentLoadableHolder = new ChunkOperationHolder();
    mediaChunks = new LinkedList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);
    sampleQueue = new DefaultTrackOutput(loadControl.getAllocator());
    state = STATE_IDLE;
    pendingResetPositionUs = NO_RESET_PENDING;
  }

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (state != STATE_IDLE) {
      return true;
    }
    if (!chunkSource.prepare()) {
      return false;
    }
    durationUs = chunkSource.getDurationUs();
    TrackGroup trackGroup = chunkSource.getTracks();
    if (trackGroup != null) {
      loader = new Loader("Loader:" + trackGroup.getFormat(0).containerMimeType);
      trackGroups = new TrackGroupArray(trackGroup);
    } else {
      trackGroups = new TrackGroupArray();
    }
    state = STATE_PREPARED;
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    Assertions.checkState(state != STATE_IDLE);
    return trackGroups;
  }

  @Override
  public TrackStream enable(TrackSelection selection, long positionUs) {
    Assertions.checkState(state == STATE_PREPARED);
    Assertions.checkState(enabledTrackCount++ == 0);
    state = STATE_ENABLED;
    chunkSource.enable(selection.getTracks());
    loadControl.register(this, bufferSizeContribution);
    downstreamFormat = null;
    downstreamSampleFormat = null;
    downstreamPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
    pendingReset = false;
    restartFrom(positionUs);
    return this;
  }

  @Override
  public void disable(TrackStream trackStream) {
    Assertions.checkState(state == STATE_ENABLED);
    Assertions.checkState(--enabledTrackCount == 0);
    state = STATE_PREPARED;
    try {
      chunkSource.disable();
    } finally {
      loadControl.unregister(this);
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        sampleQueue.clear();
        mediaChunks.clear();
        clearCurrentLoadable();
        loadControl.trimAllocator();
      }
    }
  }

  @Override
  public void continueBuffering(long positionUs) {
    Assertions.checkState(state != STATE_IDLE);
    if (state == STATE_PREPARED) {
      return;
    }
    downstreamPositionUs = positionUs;
    chunkSource.continueBuffering(positionUs);
    updateLoadControl();
  }

  @Override
  public boolean isReady() {
    Assertions.checkState(state == STATE_ENABLED);
    return loadingFinished || !sampleQueue.isEmpty();
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
  public int readData(FormatHolder formatHolder, SampleHolder sampleHolder) {
    Assertions.checkState(state == STATE_ENABLED);
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
      notifyDownstreamFormatChanged(currentChunk.format, currentChunk.trigger,
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
        sampleHolder.addFlag(C.SAMPLE_FLAG_END_OF_STREAM);
        return END_OF_STREAM;
      }
      return NOTHING_READ;
    }

    if (sampleQueue.getSample(sampleHolder)) {
      if (sampleHolder.timeUs < lastSeekPositionUs) {
        sampleHolder.addFlag(C.SAMPLE_FLAG_DECODE_ONLY);
      }
      onSampleRead(currentChunk, sampleHolder);
      return SAMPLE_READ;
    }

    return NOTHING_READ;
  }

  @Override
  public void seekToUs(long positionUs) {
    Assertions.checkState(state != STATE_IDLE);
    if (state == STATE_PREPARED) {
      return;
    }
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
  public void maybeThrowError() throws IOException {
    if (currentLoadableException != null && currentLoadableExceptionCount > minLoadableRetryCount) {
      throw currentLoadableException;
    } else if (currentLoadableHolder.chunk == null) {
      chunkSource.maybeThrowError();
    }
  }

  @Override
  public long getBufferedPositionUs() {
    Assertions.checkState(state != STATE_IDLE);
    if (state != STATE_ENABLED || loadingFinished) {
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
  public void release() {
    Assertions.checkState(state != STATE_ENABLED);
    if (loader != null) {
      loader.release();
      loader = null;
    }
    state = STATE_IDLE;
  }

  @Override
  public void onLoadCompleted(Loadable loadable) {
    long now = SystemClock.elapsedRealtime();
    long loadDurationMs = now - currentLoadStartTimeMs;
    Chunk currentLoadable = currentLoadableHolder.chunk;
    chunkSource.onChunkLoadCompleted(currentLoadable);
    if (isMediaChunk(currentLoadable)) {
      BaseMediaChunk mediaChunk = (BaseMediaChunk) currentLoadable;
      notifyLoadCompleted(currentLoadable.bytesLoaded(), mediaChunk.type, mediaChunk.trigger,
          mediaChunk.format, mediaChunk.startTimeUs, mediaChunk.endTimeUs, now, loadDurationMs);
    } else {
      notifyLoadCompleted(currentLoadable.bytesLoaded(), currentLoadable.type,
          currentLoadable.trigger, currentLoadable.format, -1, -1, now, loadDurationMs);
    }
    clearCurrentLoadable();
    updateLoadControl();
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    Chunk currentLoadable = currentLoadableHolder.chunk;
    notifyLoadCanceled(currentLoadable.bytesLoaded());
    clearCurrentLoadable();
    if (state == STATE_ENABLED) {
      restartFrom(pendingResetPositionUs);
    } else {
      sampleQueue.clear();
      mediaChunks.clear();
      clearCurrentLoadable();
      loadControl.trimAllocator();
    }
  }

  @Override
  public void onLoadError(Loadable loadable, IOException e) {
    currentLoadableException = e;
    currentLoadableExceptionCount++;
    currentLoadableExceptionTimestamp = SystemClock.elapsedRealtime();
    notifyLoadError(e);
    chunkSource.onChunkLoadError(currentLoadableHolder.chunk, e);
    updateLoadControl();
  }

  /**
   * Called when a sample has been read. Can be used to perform any modifications necessary before
   * the sample is returned.
   *
   * @param mediaChunk The chunk from which the sample was obtained.
   * @param sampleHolder Holds the read sample.
   */
  protected void onSampleRead(MediaChunk mediaChunk, SampleHolder sampleHolder) {
    // Do nothing.
  }

  private void restartFrom(long positionUs) {
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      sampleQueue.clear();
      mediaChunks.clear();
      clearCurrentLoadable();
      updateLoadControl();
    }
  }

  private void clearCurrentLoadable() {
    currentLoadableHolder.chunk = null;
    clearCurrentLoadableException();
  }

  private void clearCurrentLoadableException() {
    currentLoadableException = null;
    currentLoadableExceptionCount = 0;
  }

  private void updateLoadControl() {
    long now = SystemClock.elapsedRealtime();
    long nextLoadPositionUs = getNextLoadPositionUs();
    boolean isBackedOff = currentLoadableException != null;
    boolean loadingOrBackedOff = loader.isLoading() || isBackedOff;

    // If we're not loading or backed off, evaluate the operation if (a) we don't have the next
    // chunk yet and we're not finished, or (b) if the last evaluation was over 2000ms ago.
    if (!loadingOrBackedOff && ((currentLoadableHolder.chunk == null && nextLoadPositionUs != -1)
        || (now - lastPerformedBufferOperation > 2000))) {
      // Perform the evaluation.
      lastPerformedBufferOperation = now;
      doChunkOperation();
      boolean chunksDiscarded = discardUpstreamMediaChunks(currentLoadableHolder.queueSize);
      // Update the next load position as appropriate.
      if (currentLoadableHolder.chunk == null) {
        // Set loadPosition to -1 to indicate that we don't have anything to load.
        nextLoadPositionUs = -1;
      } else if (chunksDiscarded) {
        // Chunks were discarded, so we need to re-evaluate the load position.
        nextLoadPositionUs = getNextLoadPositionUs();
      }
    }

    // Update the control with our current state, and determine whether we're the next loader.
    boolean nextLoader = loadControl.update(this, downstreamPositionUs, nextLoadPositionUs,
        loadingOrBackedOff);

    if (isBackedOff) {
      long elapsedMillis = now - currentLoadableExceptionTimestamp;
      if (elapsedMillis >= getRetryDelayMillis(currentLoadableExceptionCount)) {
        resumeFromBackOff();
      }
      return;
    }

    if (!loader.isLoading() && nextLoader) {
      maybeStartLoading();
    }
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
   * Resumes loading.
   * <p>
   * If the {@link ChunkSource} returns a chunk equivalent to the backed off chunk B, then the
   * loading of B will be resumed. In all other cases B will be discarded and the new chunk will
   * be loaded.
   */
  private void resumeFromBackOff() {
    currentLoadableException = null;

    Chunk backedOffChunk = currentLoadableHolder.chunk;
    if (!isMediaChunk(backedOffChunk)) {
      doChunkOperation();
      discardUpstreamMediaChunks(currentLoadableHolder.queueSize);
      if (currentLoadableHolder.chunk == backedOffChunk) {
        // Chunk was unchanged. Resume loading.
        loader.startLoading(backedOffChunk, this);
      } else {
        // Chunk was changed. Notify that the existing load was canceled.
        notifyLoadCanceled(backedOffChunk.bytesLoaded());
        // Start loading the replacement.
        maybeStartLoading();
      }
      return;
    }

    if (backedOffChunk == mediaChunks.getFirst()) {
      // We're not able to clear the first media chunk, so we have no choice but to continue
      // loading it.
      loader.startLoading(backedOffChunk, this);
      return;
    }

    // The current loadable is the last media chunk. Remove it before we invoke the chunk source,
    // and add it back again afterwards.
    BaseMediaChunk removedChunk = mediaChunks.removeLast();
    Assertions.checkState(backedOffChunk == removedChunk);
    doChunkOperation();
    mediaChunks.add(removedChunk);

    if (currentLoadableHolder.chunk == backedOffChunk) {
      // Chunk was unchanged. Resume loading.
      loader.startLoading(backedOffChunk, this);
    } else {
      // Chunk was changed. Notify that the existing load was canceled.
      notifyLoadCanceled(backedOffChunk.bytesLoaded());
      // This call will remove and release at least one chunk from the end of mediaChunks. Since
      // the current loadable is the last media chunk, it is guaranteed to be removed.
      discardUpstreamMediaChunks(currentLoadableHolder.queueSize);
      clearCurrentLoadableException();
      maybeStartLoading();
    }
  }

  private void maybeStartLoading() {
    Chunk currentLoadable = currentLoadableHolder.chunk;
    if (currentLoadable == null) {
      // Nothing to load.
      return;
    }
    currentLoadStartTimeMs = SystemClock.elapsedRealtime();
    if (isMediaChunk(currentLoadable)) {
      BaseMediaChunk mediaChunk = (BaseMediaChunk) currentLoadable;
      mediaChunk.init(sampleQueue);
      mediaChunks.add(mediaChunk);
      if (isPendingReset()) {
        pendingResetPositionUs = NO_RESET_PENDING;
      }
      notifyLoadStarted(mediaChunk.dataSpec.length, mediaChunk.type, mediaChunk.trigger,
          mediaChunk.format, mediaChunk.startTimeUs, mediaChunk.endTimeUs);
    } else {
      notifyLoadStarted(currentLoadable.dataSpec.length, currentLoadable.type,
          currentLoadable.trigger, currentLoadable.format, -1, -1);
    }
    loader.startLoading(currentLoadable, this);
  }

  /**
   * Sets up the {@link #currentLoadableHolder}, passes it to the chunk source to cause it to be
   * updated with the next operation, and updates {@link #loadingFinished} if the end of the stream
   * is reached.
   */
  private void doChunkOperation() {
    currentLoadableHolder.endOfStream = false;
    currentLoadableHolder.queueSize = readOnlyMediaChunks.size();
    chunkSource.getChunkOperation(readOnlyMediaChunks,
        pendingResetPositionUs != NO_RESET_PENDING ? pendingResetPositionUs : downstreamPositionUs,
        currentLoadableHolder);
    loadingFinished = currentLoadableHolder.endOfStream;
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
    }
    sampleQueue.discardUpstreamSamples(removed.getFirstSampleIndex());

    notifyUpstreamDiscarded(startTimeUs, endTimeUs);
    return true;
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof BaseMediaChunk;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != NO_RESET_PENDING;
  }

  private long getRetryDelayMillis(long errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  protected final long usToMs(long timeUs) {
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

  private void notifyUpstreamDiscarded(final long mediaStartTimeUs, final long mediaEndTimeUs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onUpstreamDiscarded(eventSourceId, usToMs(mediaStartTimeUs),
              usToMs(mediaEndTimeUs));
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
