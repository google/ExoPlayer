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

import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.Loader;
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
public class ChunkSampleSource implements SampleSource, Loader.Listener {

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
     * @param totalBytes The length of the data being loaded in bytes.
     */
    void onLoadStarted(int sourceId, int formatId, int trigger, boolean isInitialization,
        int mediaStartTimeMs, int mediaEndTimeMs, long totalBytes);

    /**
     * Invoked when the current load operation completes.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     */
    void onLoadCompleted(int sourceId);

    /**
     * Invoked when the current upstream load operation is canceled.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     */
    void onLoadCanceled(int sourceId);

    /**
     * Invoked when data is removed from the back of the buffer, typically so that it can be
     * re-buffered using a different representation.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param mediaStartTimeMs The media time of the start of the discarded data.
     * @param mediaEndTimeMs The media time of the end of the discarded data.
     * @param totalBytes The length of the data being discarded in bytes.
     */
    void onUpstreamDiscarded(int sourceId, int mediaStartTimeMs, int mediaEndTimeMs,
        long totalBytes);

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
     * @param totalBytes The length of the data being discarded in bytes.
     */
    void onDownstreamDiscarded(int sourceId, int mediaStartTimeMs, int mediaEndTimeMs,
        long totalBytes);

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
    void onDownstreamFormatChanged(int sourceId, int formatId, int trigger, int mediaTimeMs);

  }

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

  private int state;
  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetTime;
  private long lastPerformedBufferOperation;
  private boolean pendingDiscontinuity;

  private Loader loader;
  private IOException currentLoadableException;
  private boolean currentLoadableExceptionFatal;
  private int currentLoadableExceptionCount;
  private long currentLoadableExceptionTimestamp;

  private volatile Format downstreamFormat;

  public ChunkSampleSource(ChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, boolean frameAccurateSeeking) {
    this(chunkSource, loadControl, bufferSizeContribution, frameAccurateSeeking, null, null, 0);
  }

  public ChunkSampleSource(ChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, boolean frameAccurateSeeking, Handler eventHandler,
      EventListener eventListener, int eventSourceId) {
    this.chunkSource = chunkSource;
    this.loadControl = loadControl;
    this.bufferSizeContribution = bufferSizeContribution;
    this.frameAccurateSeeking = frameAccurateSeeking;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
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
    loader = new Loader("Loader:" + chunkSource.getTrackInfo().mimeType, this);
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
  public void enable(int track, long timeUs) {
    Assertions.checkState(state == STATE_PREPARED);
    Assertions.checkState(track == 0);
    state = STATE_ENABLED;
    chunkSource.enable();
    loadControl.register(this, bufferSizeContribution);
    downstreamFormat = null;
    downstreamPositionUs = timeUs;
    lastSeekPositionUs = timeUs;
    restartFrom(timeUs);
  }

  @Override
  public void disable(int track) {
    Assertions.checkState(state == STATE_ENABLED);
    Assertions.checkState(track == 0);
    pendingDiscontinuity = false;
    state = STATE_PREPARED;
    loadControl.unregister(this);
    chunkSource.disable(mediaChunks);
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      clearMediaChunks();
      clearCurrentLoadable();
      loadControl.trimAllocator();
    }
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    Assertions.checkState(state == STATE_ENABLED);
    downstreamPositionUs = playbackPositionUs;
    chunkSource.continueBuffering(playbackPositionUs);
    updateLoadControl();
  }

  @Override
  public int readData(int track, long playbackPositionUs, FormatHolder formatHolder,
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

    downstreamPositionUs = playbackPositionUs;
    if (isPendingReset()) {
      if (currentLoadableException != null) {
        throw currentLoadableException;
      }
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
        return readData(track, playbackPositionUs, formatHolder, sampleHolder, false);
      } else if (mediaChunk.isLastChunk()) {
        return END_OF_STREAM;
      } else {
        IOException chunkSourceException = chunkSource.getError();
        if (chunkSourceException != null) {
          throw chunkSourceException;
        }
        return NOTHING_READ;
      }
    } else if (downstreamFormat == null || downstreamFormat.id != mediaChunk.format.id) {
      notifyDownstreamFormatChanged(mediaChunk.format.id, mediaChunk.trigger,
          mediaChunk.startTimeUs);
      MediaFormat format = mediaChunk.getMediaFormat();
      chunkSource.getMaxVideoDimensions(format);
      formatHolder.format = format;
      formatHolder.drmInitData = mediaChunk.getPsshInfo();
      downstreamFormat = mediaChunk.format;
      return FORMAT_READ;
    }

    if (mediaChunk.read(sampleHolder)) {
      sampleHolder.decodeOnly = frameAccurateSeeking && sampleHolder.timeUs < lastSeekPositionUs;
      onSampleRead(mediaChunk, sampleHolder);
      return SAMPLE_READ;
    } else {
      if (currentLoadableException != null) {
        throw currentLoadableException;
      }
      return NOTHING_READ;
    }
  }

  @Override
  public void seekToUs(long timeUs) {
    Assertions.checkState(state == STATE_ENABLED);
    downstreamPositionUs = timeUs;
    lastSeekPositionUs = timeUs;
    if (pendingResetTime == timeUs) {
      return;
    }

    MediaChunk mediaChunk = getMediaChunk(timeUs);
    if (mediaChunk == null) {
      restartFrom(timeUs);
      pendingDiscontinuity = true;
    } else {
      pendingDiscontinuity |= mediaChunk.seekTo(timeUs, mediaChunk == mediaChunks.getFirst());
      discardDownstreamMediaChunks(mediaChunk);
      updateLoadControl();
    }
  }

  private MediaChunk getMediaChunk(long timeUs) {
    Iterator<MediaChunk> mediaChunkIterator = mediaChunks.iterator();
    while (mediaChunkIterator.hasNext()) {
      MediaChunk mediaChunk = mediaChunkIterator.next();
      if (timeUs < mediaChunk.startTimeUs) {
        return null;
      } else if (mediaChunk.isLastChunk() || timeUs < mediaChunk.endTimeUs) {
        return mediaChunk;
      }
    }
    return null;
  }

  @Override
  public long getBufferedPositionUs() {
    Assertions.checkState(state == STATE_ENABLED);
    if (isPendingReset()) {
      return pendingResetTime;
    }
    MediaChunk mediaChunk = mediaChunks.getLast();
    Chunk currentLoadable = currentLoadableHolder.chunk;
    if (currentLoadable != null && mediaChunk == currentLoadable) {
      // Linearly interpolate partially-fetched chunk times.
      long chunkLength = mediaChunk.getLength();
      if (chunkLength != DataSpec.LENGTH_UNBOUNDED) {
        return mediaChunk.startTimeUs + ((mediaChunk.endTimeUs - mediaChunk.startTimeUs) *
            mediaChunk.bytesLoaded()) / chunkLength;
      } else {
        return mediaChunk.startTimeUs;
      }
    } else if (mediaChunk.isLastChunk()) {
      return TrackRenderer.END_OF_TRACK;
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
  public void onLoaded() {
    Chunk currentLoadable = currentLoadableHolder.chunk;
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
      notifyLoadCompleted();
      updateLoadControl();
    }
  }

  @Override
  public void onCanceled() {
    Chunk currentLoadable = currentLoadableHolder.chunk;
    if (!isMediaChunk(currentLoadable)) {
      currentLoadable.release();
    }
    clearCurrentLoadable();
    notifyLoadCanceled();
    if (state == STATE_ENABLED) {
      restartFrom(pendingResetTime);
    } else {
      clearMediaChunks();
      loadControl.trimAllocator();
    }
  }

  @Override
  public void onError(IOException e) {
    currentLoadableException = e;
    currentLoadableExceptionCount++;
    currentLoadableExceptionTimestamp = SystemClock.elapsedRealtime();
    notifyUpstreamError(e);
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

  private void restartFrom(long timeUs) {
    pendingResetTime = timeUs;
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
    long loadPositionUs;
    if (isPendingReset()) {
      loadPositionUs = pendingResetTime;
    } else {
      MediaChunk lastMediaChunk = mediaChunks.getLast();
      loadPositionUs = lastMediaChunk.nextChunkIndex == -1 ? -1 : lastMediaChunk.endTimeUs;
    }

    boolean isBackedOff = currentLoadableException != null && !currentLoadableExceptionFatal;
    boolean nextLoader = loadControl.update(this, downstreamPositionUs, loadPositionUs,
        isBackedOff || loader.isLoading(), currentLoadableExceptionFatal);

    if (currentLoadableExceptionFatal) {
      return;
    }

    long now = SystemClock.elapsedRealtime();

    if (isBackedOff) {
      long elapsedMillis = now - currentLoadableExceptionTimestamp;
      if (elapsedMillis >= getRetryDelayMillis(currentLoadableExceptionCount)) {
        resumeFromBackOff();
      }
      return;
    }

    if (!loader.isLoading()) {
      if (currentLoadableHolder.chunk == null || now - lastPerformedBufferOperation > 1000) {
        lastPerformedBufferOperation = now;
        currentLoadableHolder.queueSize = readOnlyMediaChunks.size();
        chunkSource.getChunkOperation(readOnlyMediaChunks, pendingResetTime, downstreamPositionUs,
            currentLoadableHolder);
        discardUpstreamMediaChunks(currentLoadableHolder.queueSize);
      }
      if (nextLoader) {
        maybeStartLoading();
      }
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
      chunkSource.getChunkOperation(readOnlyMediaChunks, pendingResetTime, downstreamPositionUs,
          currentLoadableHolder);
      discardUpstreamMediaChunks(currentLoadableHolder.queueSize);
      if (currentLoadableHolder.chunk == backedOffChunk) {
        // Chunk was unchanged. Resume loading.
        loader.startLoading(backedOffChunk);
      } else {
        backedOffChunk.release();
        maybeStartLoading();
      }
      return;
    }

    if (backedOffChunk == mediaChunks.getFirst()) {
      // We're not able to clear the first media chunk, so we have no choice but to continue
      // loading it.
      loader.startLoading(backedOffChunk);
      return;
    }

    // The current loadable is the last media chunk. Remove it before we invoke the chunk source,
    // and add it back again afterwards.
    MediaChunk removedChunk = mediaChunks.removeLast();
    Assertions.checkState(backedOffChunk == removedChunk);
    currentLoadableHolder.queueSize = readOnlyMediaChunks.size();
    chunkSource.getChunkOperation(readOnlyMediaChunks, pendingResetTime, downstreamPositionUs,
        currentLoadableHolder);
    mediaChunks.add(removedChunk);

    if (currentLoadableHolder.chunk == backedOffChunk) {
      // Chunk was unchanged. Resume loading.
      loader.startLoading(backedOffChunk);
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
        mediaChunk.seekTo(pendingResetTime, false);
        pendingResetTime = NO_RESET_PENDING;
      }
      mediaChunks.add(mediaChunk);
      notifyLoadStarted(mediaChunk.format.id, mediaChunk.trigger, false,
          mediaChunk.startTimeUs, mediaChunk.endTimeUs, mediaChunk.getLength());
    } else {
      notifyLoadStarted(currentLoadable.format.id, currentLoadable.trigger, true, -1, -1,
          currentLoadable.getLength());
    }
    loader.startLoading(currentLoadable);
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
   */
  private void discardUpstreamMediaChunks(int queueLength) {
    if (mediaChunks.size() <= queueLength) {
      return;
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
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof MediaChunk;
  }

  private boolean isPendingReset() {
    return pendingResetTime != NO_RESET_PENDING;
  }

  private long getRetryDelayMillis(long errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  protected final int usToMs(long timeUs) {
    return (int) (timeUs / 1000);
  }

  private void notifyLoadStarted(final int formatId, final int trigger,
      final boolean isInitialization, final long mediaStartTimeUs, final long mediaEndTimeUs,
      final long totalBytes) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadStarted(eventSourceId, formatId, trigger, isInitialization,
              usToMs(mediaStartTimeUs), usToMs(mediaEndTimeUs), totalBytes);
        }
      });
    }
  }

  private void notifyLoadCompleted() {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadCompleted(eventSourceId);
        }
      });
    }
  }

  private void notifyLoadCanceled() {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadCanceled(eventSourceId);
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

  private void notifyDownstreamFormatChanged(final int formatId, final int trigger,
      final long mediaTimeUs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDownstreamFormatChanged(eventSourceId, formatId, trigger,
              usToMs(mediaTimeUs));
        }
      });
    }
  }

  private void notifyDownstreamDiscarded(final long mediaStartTimeUs, final long mediaEndTimeUs,
      final long totalBytes) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDownstreamDiscarded(eventSourceId, usToMs(mediaStartTimeUs),
              usToMs(mediaEndTimeUs), totalBytes);
        }
      });
    }
  }

}
