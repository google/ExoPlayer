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
import com.google.android.exoplayer.extractor.DefaultTrackOutput;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

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

  private static final long NO_RESET_PENDING = Long.MIN_VALUE;

  private final Loader loader;
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

  private boolean prepared;
  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;
  private long lastPerformedBufferOperation;
  private boolean pendingReset;
  private boolean loadControlRegistered;

  private TrackGroupArray trackGroups;
  private long durationUs;
  private boolean loadingFinished;
  private boolean trackEnabled;
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
    loader = new Loader("Loader:ChunkSampleSource", minLoadableRetryCount);
    currentLoadableHolder = new ChunkOperationHolder();
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
        sampleQueue.clear();
        mediaChunks.clear();
        clearCurrentLoadable();
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
    if (currentLoadableHolder.chunk == null) {
      chunkSource.maybeThrowError();
    }
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
    maybeStartLoading();
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    Chunk currentLoadable = currentLoadableHolder.chunk;
    notifyLoadCanceled(currentLoadable.bytesLoaded());
    clearCurrentLoadable();
    if (trackEnabled) {
      restartFrom(pendingResetPositionUs);
    } else {
      sampleQueue.clear();
      mediaChunks.clear();
      loadControl.trimAllocator();
    }
  }

  @Override
  public int onLoadError(Loadable loadable, IOException e) {
    Chunk currentLoadable = currentLoadableHolder.chunk;
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
      notifyLoadError(e);
      notifyLoadCanceled(bytesLoaded);
      maybeStartLoading();
      return Loader.DONT_RETRY;
    } else {
      notifyLoadError(e);
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
      sampleQueue.clear();
      mediaChunks.clear();
      clearCurrentLoadable();
      maybeStartLoading();
    }
  }

  private void clearCurrentLoadable() {
    currentLoadableHolder.chunk = null;
  }

  private void maybeStartLoading() {
    if (loader.isLoading()) {
      return;
    }

    long now = SystemClock.elapsedRealtime();
    long nextLoadPositionUs = getNextLoadPositionUs();

    // Evaluate the operation if (a) we don't have the next chunk yet and we're not finished, or (b)
    // if the last evaluation was over 2000ms ago.
    if ((currentLoadableHolder.chunk == null && nextLoadPositionUs != -1)
        || (now - lastPerformedBufferOperation > 2000)) {
      // Perform the evaluation.
      currentLoadableHolder.endOfStream = false;
      currentLoadableHolder.queueSize = readOnlyMediaChunks.size();
      long playbackPositionUs = pendingResetPositionUs != NO_RESET_PENDING ? pendingResetPositionUs
          : downstreamPositionUs;
      chunkSource.getChunkOperation(readOnlyMediaChunks, playbackPositionUs, currentLoadableHolder);
      loadingFinished = currentLoadableHolder.endOfStream;
      boolean chunksDiscarded = discardUpstreamMediaChunks(currentLoadableHolder.queueSize);
      lastPerformedBufferOperation = now;
      // Update the next load position as appropriate.
      if (currentLoadableHolder.chunk == null) {
        // Set loadPosition to -1 to indicate that we don't have anything to load.
        nextLoadPositionUs = -1;
      } else if (chunksDiscarded) {
        // Chunks were discarded, so we need to re-evaluate the load position.
        nextLoadPositionUs = getNextLoadPositionUs();
      }
    }

    boolean nextLoader = loadControl.update(this, downstreamPositionUs, nextLoadPositionUs, false);
    if (!nextLoader) {
      // We're not allowed to start loading yet.
      return;
    }

    Chunk currentLoadable = currentLoadableHolder.chunk;
    if (currentLoadable == null) {
      // We're allowed to start loading, but have nothing to load.
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

  private void notifyLoadStarted(final long length, final int type, final int trigger,
      final Format format, final long mediaStartTimeUs, final long mediaEndTimeUs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadStarted(eventSourceId, length, type, trigger, format,
              Util.usToMs(mediaStartTimeUs), Util.usToMs(mediaEndTimeUs));
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
              Util.usToMs(mediaStartTimeUs), Util.usToMs(mediaEndTimeUs), elapsedRealtimeMs,
              loadDurationMs);
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
          eventListener.onUpstreamDiscarded(eventSourceId, Util.usToMs(mediaStartTimeUs),
              Util.usToMs(mediaEndTimeUs));
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
              Util.usToMs(positionUs));
        }
      });
    }
  }

}
