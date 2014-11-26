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
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;

import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link SampleSource} that loads media in {@link Chunk}s, which are themselves obtained from a
 * {@link ChunkSource}.
 */
public class ChunkSampleSource implements SampleSource, Loader.Callback {

  /**
   * Interface definition for a callback to be notified of {@link ChunkSampleSource} events.
   */
  public interface EventListener {

    /**
     * Invoked when an upstream load is started.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param formatId The format id.
     * @param trigger A trigger for the format selection, as specified by the {@link ChunkSource}.
     * @param isInitialization Whether the load is for format initialization data.
     * @param mediaStartTimeMs The media time of the start of the data being loaded, or -1 if this
     *     load is for initialization data.
     * @param mediaEndTimeMs The media time of the end of the data being loaded, or -1 if this
     *     load is for initialization data.
     * @param length The length of the data being loaded in bytes, or {@link C#LENGTH_UNBOUNDED} if
     *     the length of the data has not yet been determined.
     */
    void onLoadStarted(int sourceId, String formatId, int trigger, boolean isInitialization,
        int mediaStartTimeMs, int mediaEndTimeMs, long length);

    /**
     * Invoked when the current load operation completes.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param bytesLoaded The number of bytes that were loaded.
     */
    void onLoadCompleted(int sourceId, long bytesLoaded);

    /**
     * Invoked when the current upstream load operation is canceled.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param bytesLoaded The number of bytes that were loaded prior to the cancellation.
     */
    void onLoadCanceled(int sourceId, long bytesLoaded);

    /**
     * Invoked when data is removed from the back of the buffer, typically so that it can be
     * re-buffered using a different representation.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param mediaStartTimeMs The media time of the start of the discarded data.
     * @param mediaEndTimeMs The media time of the end of the discarded data.
     * @param bytesDiscarded The length of the data being discarded in bytes.
     */
    void onUpstreamDiscarded(int sourceId, int mediaStartTimeMs, int mediaEndTimeMs,
        long bytesDiscarded);

    /**
     * Invoked when an error occurs loading media data.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param e The cause of the failure.
     */
    void onUpstreamError(int sourceId, IOException e);

    /**
     * Invoked when an error occurs consuming loaded data.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param e The cause of the failure.
     */
    void onConsumptionError(int sourceId, IOException e);

    /**
     * Invoked when data is removed from the front of the buffer, typically due to a seek or
     * because the data has been consumed.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param mediaStartTimeMs The media time of the start of the discarded data.
     * @param mediaEndTimeMs The media time of the end of the discarded data.
     * @param bytesDiscarded The length of the data being discarded in bytes.
     */
    void onDownstreamDiscarded(int sourceId, int mediaStartTimeMs, int mediaEndTimeMs,
        long bytesDiscarded);

    /**
     * Invoked when the downstream format changes (i.e. when the format being supplied to the
     * caller of {@link SampleSource#readData} changes).
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param formatId The format id.
     * @param trigger The trigger specified in the corresponding upstream load, as specified by the
     *     {@link ChunkSource}.
     * @param mediaTimeMs The media time at which the change occurred.
     */
    void onDownstreamFormatChanged(int sourceId, String formatId, int trigger, int mediaTimeMs);

  }

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 1;

  private static final int STATE_UNPREPARED = 0;
  private static final int STATE_PREPARED = 1;
  private static final int STATE_ENABLED = 2;

  private static final int NO_RESET_PENDING = -1;

  private final int eventSourceId;
  private final LoadControl loadControl;
  private final ChunkSource chunkSource;
  private final ChunkOperationHolder currentLoadableHolder;
  private final LinkedList<MediaChunk> mediaChunks;
  private final List<MediaChunk> readOnlyMediaChunks;
  private final int bufferSizeContribution;
  private final boolean frameAccurateSeeking;
  private final Handler eventHandler;
  private final EventListener eventListener;
  private final int minLoadableRetryCount;

  private int state;
  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;
  private long lastPerformedBufferOperation;
  private boolean pendingDiscontinuity;

  private Loader loader;
  private IOException currentLoadableException;
  private boolean currentLoadableExceptionFatal;
  private int currentLoadableExceptionCount;
  private long currentLoadableExceptionTimestamp;

  private MediaFormat downstreamMediaFormat;
  private volatile Format downstreamFormat;

  public ChunkSampleSource(ChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, boolean frameAccurateSeeking) {
    this(chunkSource, loadControl, bufferSizeContribution, frameAccurateSeeking, null, null, 0);
  }

  public ChunkSampleSource(ChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, boolean frameAccurateSeeking, Handler eventHandler,
      EventListener eventListener, int eventSourceId) {
    this(chunkSource, loadControl, bufferSizeContribution, frameAccurateSeeking, eventHandler,
        eventListener, eventSourceId, DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  public ChunkSampleSource(ChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, boolean frameAccurateSeeking, Handler eventHandler,
      EventListener eventListener, int eventSourceId, int minLoadableRetryCount) {
    this.chunkSource = chunkSource;
    this.loadControl = loadControl;
    this.bufferSizeContribution = bufferSizeContribution;
    this.frameAccurateSeeking = frameAccurateSeeking;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
    this.minLoadableRetryCount = minLoadableRetryCount;
    currentLoadableHolder = new ChunkOperationHolder();
    mediaChunks = new LinkedList<MediaChunk>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);
    state = STATE_UNPREPARED;
  }

  /**
   * Exposes the current downstream format for debugging purposes. Can be called from any thread.
   *
   * @return The current downstream format.
   */
  public Format getFormat() {
    return downstreamFormat;
  }

  @Override
  public boolean prepare() {
    Assertions.checkState(state == STATE_UNPREPARED);
    loader = new Loader("Loader:" + chunkSource.getTrackInfo().mimeType);
    state = STATE_PREPARED;
    return true;
  }

  @Override
  public int getTrackCount() {
    Assertions.checkState(state != STATE_UNPREPARED);
    return 1;
  }

  @Override
  public TrackInfo getTrackInfo(int track) {
    Assertions.checkState(state != STATE_UNPREPARED);
    Assertions.checkState(track == 0);
    return chunkSource.getTrackInfo();
  }

  @Override
  public void enable(int track, long positionUs) {
    Assertions.checkState(state == STATE_PREPARED);
    Assertions.checkState(track == 0);
    state = STATE_ENABLED;
    chunkSource.enable();
    loadControl.register(this, bufferSizeContribution);
    downstreamFormat = null;
    downstreamMediaFormat = null;
    downstreamPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
    restartFrom(positionUs);
  }

  @Override
  public void disable(int track) {
    Assertions.checkState(state == STATE_ENABLED);
    Assertions.checkState(track == 0);
    pendingDiscontinuity = false;
    state = STATE_PREPARED;
    try {
      chunkSource.disable(mediaChunks);
    } finally {
      loadControl.unregister(this);
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        clearMediaChunks();
        clearCurrentLoadable();
        loadControl.trimAllocator();
      }
    }
  }

  @Override
  public boolean continueBuffering(long positionUs) throws IOException {
    Assertions.checkState(state == STATE_ENABLED);
    downstreamPositionUs = positionUs;
    chunkSource.continueBuffering(positionUs);
    updateLoadControl();
    if (isPendingReset() || mediaChunks.isEmpty()) {
      return false;
    } else if (mediaChunks.getFirst().sampleAvailable()) {
      // There's a sample available to be read from the current chunk.
      return true;
    } else {
      // It may be the case that the current chunk has been fully read but not yet discarded and
      // that the next chunk has an available sample. Return true if so, otherwise false.
      return mediaChunks.size() > 1 && mediaChunks.get(1).sampleAvailable();
    }
  }

  @Override
  public int readData(int track, long positionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder, boolean onlyReadDiscontinuity) throws IOException {
    Assertions.checkState(state == STATE_ENABLED);
    Assertions.checkState(track == 0);

    if (pendingDiscontinuity) {
      pendingDiscontinuity = false;
      return DISCONTINUITY_READ;
    }

    if (onlyReadDiscontinuity) {
      return NOTHING_READ;
    }

    downstreamPositionUs = positionUs;
    if (isPendingReset()) {
      maybeThrowLoadableException();
      IOException chunkSourceException = chunkSource.getError();
      if (chunkSourceException != null) {
        throw chunkSourceException;
      }
      return NOTHING_READ;
    }

    MediaChunk mediaChunk = mediaChunks.getFirst();
    if (mediaChunk.isReadFinished()) {
      // We've read all of the samples from the current media chunk.
      if (mediaChunks.size() > 1) {
        discardDownstreamMediaChunk();
        mediaChunk = mediaChunks.getFirst();
        mediaChunk.seekToStart();
        return readData(track, positionUs, formatHolder, sampleHolder, false);
      } else if (mediaChunk.isLastChunk()) {
        return END_OF_STREAM;
      }
      IOException chunkSourceException = chunkSource.getError();
      if (chunkSourceException != null) {
        throw chunkSourceException;
      }
      return NOTHING_READ;
    }

    if (downstreamFormat == null || !downstreamFormat.equals(mediaChunk.format)) {
      notifyDownstreamFormatChanged(mediaChunk.format.id, mediaChunk.trigger,
          mediaChunk.startTimeUs);
      downstreamFormat = mediaChunk.format;
    }

    if (!mediaChunk.prepare()) {
      if (currentLoadableException != null) {
        throw currentLoadableException;
      }
      return NOTHING_READ;
    }

    MediaFormat mediaFormat = mediaChunk.getMediaFormat();
    if (mediaFormat != null && !mediaFormat.equals(downstreamMediaFormat, true)) {
      chunkSource.getMaxVideoDimensions(mediaFormat);
      formatHolder.format = mediaFormat;
      formatHolder.drmInitData = mediaChunk.getPsshInfo();
      downstreamMediaFormat = mediaFormat;
      return FORMAT_READ;
    }

    if (mediaChunk.read(sampleHolder)) {
      sampleHolder.decodeOnly = frameAccurateSeeking && sampleHolder.timeUs < lastSeekPositionUs;
      onSampleRead(mediaChunk, sampleHolder);
      return SAMPLE_READ;
    } else {
      maybeThrowLoadableException();
      return NOTHING_READ;
    }
  }

  @Override
  public void seekToUs(long positionUs) {
    Assertions.checkState(state == STATE_ENABLED);
    downstreamPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
    if (pendingResetPositionUs == positionUs) {
      return;
    }

    MediaChunk mediaChunk = getMediaChunk(positionUs);
    if (mediaChunk == null) {
      restartFrom(positionUs);
      pendingDiscontinuity = true;
    } else {
      pendingDiscontinuity |= mediaChunk.seekTo(positionUs, mediaChunk == mediaChunks.getFirst());
      discardDownstreamMediaChunks(mediaChunk);
      updateLoadControl();
    }
  }

  private void maybeThrowLoadableException() throws IOException {
    if (currentLoadableException != null && currentLoadableExceptionCount > minLoadableRetryCount) {
      throw currentLoadableException;
    }
  }

  private MediaChunk getMediaChunk(long positionUs) {
    Iterator<MediaChunk> mediaChunkIterator = mediaChunks.iterator();
    while (mediaChunkIterator.hasNext()) {
      MediaChunk mediaChunk = mediaChunkIterator.next();
      if (positionUs < mediaChunk.startTimeUs) {
        return null;
      } else if (mediaChunk.isLastChunk() || positionUs < mediaChunk.endTimeUs) {
        return mediaChunk;
      }
    }
    return null;
  }

  @Override
  public long getBufferedPositionUs() {
    Assertions.checkState(state == STATE_ENABLED);
    if (isPendingReset()) {
      return pendingResetPositionUs;
    }
    MediaChunk mediaChunk = mediaChunks.getLast();
    Chunk currentLoadable = currentLoadableHolder.chunk;
    if (currentLoadable != null && mediaChunk == currentLoadable) {
      // Linearly interpolate partially-fetched chunk times.
      long chunkLength = mediaChunk.getLength();
      if (chunkLength != C.LENGTH_UNBOUNDED) {
        return mediaChunk.startTimeUs + ((mediaChunk.endTimeUs - mediaChunk.startTimeUs) *
            mediaChunk.bytesLoaded()) / chunkLength;
      } else {
        return mediaChunk.startTimeUs;
      }
    } else if (mediaChunk.isLastChunk()) {
      return TrackRenderer.END_OF_TRACK_US;
    } else {
      return mediaChunk.endTimeUs;
    }
  }

  @Override
  public void release() {
    Assertions.checkState(state != STATE_ENABLED);
    if (loader != null) {
      loader.release();
      loader = null;
    }
    state = STATE_UNPREPARED;
  }

  @Override
  public void onLoadCompleted(Loadable loadable) {
    Chunk currentLoadable = currentLoadableHolder.chunk;
    notifyLoadCompleted(currentLoadable.bytesLoaded());
    try {
      currentLoadable.consume();
    } catch (IOException e) {
      currentLoadableException = e;
      currentLoadableExceptionCount++;
      currentLoadableExceptionTimestamp = SystemClock.elapsedRealtime();
      currentLoadableExceptionFatal = true;
      notifyConsumptionError(e);
    } finally {
      if (!isMediaChunk(currentLoadable)) {
        currentLoadable.release();
      }
      if (!currentLoadableExceptionFatal) {
        clearCurrentLoadable();
      }
      updateLoadControl();
    }
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    Chunk currentLoadable = currentLoadableHolder.chunk;
    notifyLoadCanceled(currentLoadable.bytesLoaded());
    if (!isMediaChunk(currentLoadable)) {
      currentLoadable.release();
    }
    clearCurrentLoadable();
    if (state == STATE_ENABLED) {
      restartFrom(pendingResetPositionUs);
    } else {
      clearMediaChunks();
      loadControl.trimAllocator();
    }
  }

  @Override
  public void onLoadError(Loadable loadable, IOException e) {
    currentLoadableException = e;
    currentLoadableExceptionCount++;
    currentLoadableExceptionTimestamp = SystemClock.elapsedRealtime();
    notifyUpstreamError(e);
    chunkSource.onChunkLoadError(currentLoadableHolder.chunk, e);
    updateLoadControl();
  }

  /**
   * Called when a sample has been read from a {@link MediaChunk}. Can be used to perform any
   * modifications necessary before the sample is returned.
   *
   * @param mediaChunk The MediaChunk the sample was ready from.
   * @param sampleHolder The sample that has just been read.
   */
  protected void onSampleRead(MediaChunk mediaChunk, SampleHolder sampleHolder) {
    // no-op
  }

  private void restartFrom(long positionUs) {
    pendingResetPositionUs = positionUs;
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      clearMediaChunks();
      clearCurrentLoadable();
      updateLoadControl();
    }
  }

  private void clearMediaChunks() {
    discardDownstreamMediaChunks(null);
  }

  private void clearCurrentLoadable() {
    currentLoadableHolder.chunk = null;
    currentLoadableException = null;
    currentLoadableExceptionCount = 0;
    currentLoadableExceptionFatal = false;
  }

  private void updateLoadControl() {
    if (currentLoadableExceptionFatal) {
      // We've failed, but we still need to update the control with our current state.
      loadControl.update(this, downstreamPositionUs, -1, false, true);
      return;
    }

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
      currentLoadableHolder.queueSize = readOnlyMediaChunks.size();
      chunkSource.getChunkOperation(readOnlyMediaChunks, pendingResetPositionUs,
          downstreamPositionUs, currentLoadableHolder);
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
        loadingOrBackedOff, false);

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
      MediaChunk lastMediaChunk = mediaChunks.getLast();
      return lastMediaChunk.nextChunkIndex == -1 ? -1 : lastMediaChunk.endTimeUs;
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
      currentLoadableHolder.queueSize = readOnlyMediaChunks.size();
      chunkSource.getChunkOperation(readOnlyMediaChunks, pendingResetPositionUs,
          downstreamPositionUs, currentLoadableHolder);
      discardUpstreamMediaChunks(currentLoadableHolder.queueSize);
      if (currentLoadableHolder.chunk == backedOffChunk) {
        // Chunk was unchanged. Resume loading.
        loader.startLoading(backedOffChunk, this);
      } else {
        backedOffChunk.release();
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
    MediaChunk removedChunk = mediaChunks.removeLast();
    Assertions.checkState(backedOffChunk == removedChunk);
    currentLoadableHolder.queueSize = readOnlyMediaChunks.size();
    chunkSource.getChunkOperation(readOnlyMediaChunks, pendingResetPositionUs, downstreamPositionUs,
        currentLoadableHolder);
    mediaChunks.add(removedChunk);

    if (currentLoadableHolder.chunk == backedOffChunk) {
      // Chunk was unchanged. Resume loading.
      loader.startLoading(backedOffChunk, this);
    } else {
      // This call will remove and release at least one chunk from the end of mediaChunks. Since
      // the current loadable is the last media chunk, it is guaranteed to be removed.
      discardUpstreamMediaChunks(currentLoadableHolder.queueSize);
      clearCurrentLoadable();
      maybeStartLoading();
    }
  }

  private void maybeStartLoading() {
    Chunk currentLoadable = currentLoadableHolder.chunk;
    if (currentLoadable == null) {
      // Nothing to load.
      return;
    }
    currentLoadable.init(loadControl.getAllocator());
    if (isMediaChunk(currentLoadable)) {
      MediaChunk mediaChunk = (MediaChunk) currentLoadable;
      if (isPendingReset()) {
        mediaChunk.seekTo(pendingResetPositionUs, false);
        pendingResetPositionUs = NO_RESET_PENDING;
      }
      mediaChunks.add(mediaChunk);
      notifyLoadStarted(mediaChunk.format.id, mediaChunk.trigger, false,
          mediaChunk.startTimeUs, mediaChunk.endTimeUs, mediaChunk.getLength());
    } else {
      notifyLoadStarted(currentLoadable.format.id, currentLoadable.trigger, true, -1, -1,
          currentLoadable.getLength());
    }
    loader.startLoading(currentLoadable, this);
  }

  /**
   * Discards downstream media chunks until {@code untilChunk} if found. {@code untilChunk} is not
   * itself discarded. Null can be passed to discard all media chunks.
   *
   * @param untilChunk The first media chunk to keep, or null to discard all media chunks.
   */
  private void discardDownstreamMediaChunks(MediaChunk untilChunk) {
    if (mediaChunks.isEmpty() || untilChunk == mediaChunks.getFirst()) {
      return;
    }
    long totalBytes = 0;
    long startTimeUs = mediaChunks.getFirst().startTimeUs;
    long endTimeUs = 0;
    while (!mediaChunks.isEmpty() && untilChunk != mediaChunks.getFirst()) {
      MediaChunk removed = mediaChunks.removeFirst();
      totalBytes += removed.bytesLoaded();
      endTimeUs = removed.endTimeUs;
      removed.release();
    }
    notifyDownstreamDiscarded(startTimeUs, endTimeUs, totalBytes);
  }

  /**
   * Discards the first downstream media chunk.
   */
  private void discardDownstreamMediaChunk() {
    MediaChunk removed = mediaChunks.removeFirst();
    long totalBytes = removed.bytesLoaded();
    removed.release();
    notifyDownstreamDiscarded(removed.startTimeUs, removed.endTimeUs, totalBytes);
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
    long totalBytes = 0;
    long startTimeUs = 0;
    long endTimeUs = mediaChunks.getLast().endTimeUs;
    while (mediaChunks.size() > queueLength) {
      MediaChunk removed = mediaChunks.removeLast();
      totalBytes += removed.bytesLoaded();
      startTimeUs = removed.startTimeUs;
      removed.release();
    }
    notifyUpstreamDiscarded(startTimeUs, endTimeUs, totalBytes);
    return true;
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof MediaChunk;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != NO_RESET_PENDING;
  }

  private long getRetryDelayMillis(long errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  protected final int usToMs(long timeUs) {
    return (int) (timeUs / 1000);
  }

  private void notifyLoadStarted(final String formatId, final int trigger,
      final boolean isInitialization, final long mediaStartTimeUs, final long mediaEndTimeUs,
      final long length) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadStarted(eventSourceId, formatId, trigger, isInitialization,
              usToMs(mediaStartTimeUs), usToMs(mediaEndTimeUs), length);
        }
      });
    }
  }

  private void notifyLoadCompleted(final long bytesLoaded) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadCompleted(eventSourceId, bytesLoaded);
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

  private void notifyUpstreamError(final IOException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onUpstreamError(eventSourceId, e);
        }
      });
    }
  }

  private void notifyConsumptionError(final IOException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onConsumptionError(eventSourceId, e);
        }
      });
    }
  }

  private void notifyUpstreamDiscarded(final long mediaStartTimeUs, final long mediaEndTimeUs,
      final long totalBytes) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onUpstreamDiscarded(eventSourceId, usToMs(mediaStartTimeUs),
              usToMs(mediaEndTimeUs), totalBytes);
        }
      });
    }
  }

  private void notifyDownstreamFormatChanged(final String formatId, final int trigger,
      final long positionUs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDownstreamFormatChanged(eventSourceId, formatId, trigger,
              usToMs(positionUs));
        }
      });
    }
  }

  private void notifyDownstreamDiscarded(final long mediaStartTimeUs, final long mediaEndTimeUs,
      final long bytesDiscarded) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDownstreamDiscarded(eventSourceId, usToMs(mediaStartTimeUs),
              usToMs(mediaEndTimeUs), bytesDiscarded);
        }
      });
    }
  }

}
