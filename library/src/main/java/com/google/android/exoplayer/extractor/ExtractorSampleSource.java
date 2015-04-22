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
package com.google.android.exoplayer.extractor;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;

import android.net.Uri;
import android.os.SystemClock;
import android.util.SparseArray;

import java.io.IOException;

/**
 * A {@link SampleSource} that extracts sample data using an {@link Extractor}
 */
public class ExtractorSampleSource implements SampleSource, ExtractorOutput, Loader.Callback {

  /**
   * The default minimum number of times to retry loading prior to failing for on-demand streams.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND = 3;

  /**
   * The default minimum number of times to retry loading prior to failing for live streams.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_LIVE = 6;

  private static final int BUFFER_FRAGMENT_LENGTH = 256 * 1024;
  private static final int MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA = -1;
  private static final int NO_RESET_PENDING = -1;

  private final Extractor extractor;
  private final BufferPool bufferPool;
  private final int requestedBufferSize;
  private final SparseArray<InternalTrackOutput> sampleQueues;
  private final int minLoadableRetryCount;
  private final boolean frameAccurateSeeking;
  private final Uri uri;
  private final DataSource dataSource;

  private volatile boolean tracksBuilt;
  private volatile SeekMap seekMap;
  private volatile DrmInitData drmInitData;

  private boolean prepared;
  private int enabledTrackCount;
  private TrackInfo[] trackInfos;
  private boolean[] pendingMediaFormat;
  private boolean[] pendingDiscontinuities;
  private boolean[] trackEnabledStates;

  private int remainingReleaseCount;
  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private boolean havePendingNextSampleUs;
  private long pendingNextSampleUs;
  private long sampleTimeOffsetUs;

  private Loader loader;
  private ExtractingLoadable loadable;
  private IOException currentLoadableException;
  private boolean currentLoadableExceptionFatal;
  // TODO: Set this back to 0 in the correct place (some place indicative of making progress).
  private int currentLoadableExceptionCount;
  private long currentLoadableExceptionTimestamp;
  private boolean loadingFinished;

  private int extractedSampleCount;
  private int extractedSampleCountAtStartOfLoad;

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSource A data source to read the media stream.
   * @param extractor An {@link Extractor} to extract the media stream.
   * @param downstreamRendererCount Number of track renderers dependent on this sample source.
   * @param requestedBufferSize The requested total buffer size for storing sample data, in bytes.
   *     The actual allocated size may exceed the value passed in if the implementation requires it.
   */
  public ExtractorSampleSource(Uri uri, DataSource dataSource, Extractor extractor,
      int downstreamRendererCount, int requestedBufferSize) {
    this(uri, dataSource, extractor, downstreamRendererCount, requestedBufferSize,
        MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSource A data source to read the media stream.
   * @param extractor An {@link Extractor} to extract the media stream.
   * @param downstreamRendererCount Number of track renderers dependent on this sample source.
   * @param requestedBufferSize The requested total buffer size for storing sample data, in bytes.
   *     The actual allocated size may exceed the value passed in if the implementation requires it.
   * @param minLoadableRetryCount The minimum number of times that the sample source will retry
   *     if a loading error occurs.
   */
  public ExtractorSampleSource(Uri uri, DataSource dataSource, Extractor extractor,
      int downstreamRendererCount, int requestedBufferSize, int minLoadableRetryCount) {
    this.uri = uri;
    this.dataSource = dataSource;
    this.extractor = extractor;
    this.remainingReleaseCount = downstreamRendererCount;
    this.requestedBufferSize = requestedBufferSize;
    this.minLoadableRetryCount = minLoadableRetryCount;
    sampleQueues = new SparseArray<InternalTrackOutput>();
    bufferPool = new BufferPool(BUFFER_FRAGMENT_LENGTH);
    pendingResetPositionUs = NO_RESET_PENDING;
    frameAccurateSeeking = true;
    extractor.init(this);
  }

  @Override
  public boolean prepare() throws IOException {
    if (prepared) {
      return true;
    }
    if (loader == null) {
      loader = new Loader("Loader:ExtractorSampleSource");
    }

    continueBufferingInternal();

    // TODO: Support non-seekable content? Or at least avoid getting stuck here if a seekMap doesn't
    // arrive (we may end up filling the sample buffers whilst we're still not prepared, and then
    // getting stuck).
    if (seekMap != null && tracksBuilt && haveFormatsForAllTracks()) {
      int trackCount = sampleQueues.size();
      trackEnabledStates = new boolean[trackCount];
      pendingDiscontinuities = new boolean[trackCount];
      pendingMediaFormat = new boolean[trackCount];
      trackInfos = new TrackInfo[trackCount];
      for (int i = 0; i < trackCount; i++) {
        MediaFormat format = sampleQueues.valueAt(i).getFormat();
        trackInfos[i] = new TrackInfo(format.mimeType, format.durationUs);
      }
      prepared = true;
      return true;
    } else {
      maybeThrowLoadableException();
      return false;
    }
  }

  @Override
  public int getTrackCount() {
    return sampleQueues.size();
  }

  @Override
  public TrackInfo getTrackInfo(int track) {
    Assertions.checkState(prepared);
    return trackInfos[track];
  }

  @Override
  public void enable(int track, long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(!trackEnabledStates[track]);
    enabledTrackCount++;
    trackEnabledStates[track] = true;
    pendingMediaFormat[track] = true;
    if (enabledTrackCount == 1) {
      seekToUs(positionUs);
    }
  }

  @Override
  public void disable(int track) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackEnabledStates[track]);
    enabledTrackCount--;
    trackEnabledStates[track] = false;
    pendingDiscontinuities[track] = false;
    if (enabledTrackCount == 0) {
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        clearState();
        bufferPool.trim(0);
      }
    }
  }

  @Override
  public boolean continueBuffering(long playbackPositionUs) throws IOException {
    Assertions.checkState(prepared);
    Assertions.checkState(enabledTrackCount > 0);
    downstreamPositionUs = playbackPositionUs;
    discardSamplesForDisabledTracks(downstreamPositionUs);
    return loadingFinished || continueBufferingInternal();
  }

  @Override
  public int readData(int track, long playbackPositionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder, boolean onlyReadDiscontinuity) throws IOException {
    downstreamPositionUs = playbackPositionUs;

    if (pendingDiscontinuities[track]) {
      pendingDiscontinuities[track] = false;
      return DISCONTINUITY_READ;
    }

    if (onlyReadDiscontinuity || isPendingReset()) {
      maybeThrowLoadableException();
      return NOTHING_READ;
    }

    InternalTrackOutput sampleQueue = sampleQueues.valueAt(track);
    if (pendingMediaFormat[track]) {
      formatHolder.format = sampleQueue.getFormat();
      formatHolder.drmInitData = drmInitData;
      pendingMediaFormat[track] = false;
      return FORMAT_READ;
    }

    if (sampleQueue.getSample(sampleHolder)) {
      boolean decodeOnly = frameAccurateSeeking && sampleHolder.timeUs < lastSeekPositionUs;
      sampleHolder.flags |= decodeOnly ? C.SAMPLE_FLAG_DECODE_ONLY : 0;
      if (havePendingNextSampleUs) {
        // Set the offset to make the timestamp of this sample equal to pendingNextSampleUs.
        sampleTimeOffsetUs = pendingNextSampleUs - sampleHolder.timeUs;
        havePendingNextSampleUs = false;
      }
      sampleHolder.timeUs += sampleTimeOffsetUs;
      return SAMPLE_READ;
    }

    if (loadingFinished) {
      return END_OF_STREAM;
    }

    maybeThrowLoadableException();
    return NOTHING_READ;
  }

  @Override
  public void seekToUs(long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(enabledTrackCount > 0);
    if (!seekMap.isSeekable()) {
      // Treat all seeks into non-seekable media as seeks to the start.
      positionUs = 0;
    }

    lastSeekPositionUs = positionUs;
    if ((isPendingReset() ? pendingResetPositionUs : downstreamPositionUs) == positionUs) {
      return;
    }

    downstreamPositionUs = positionUs;

    // If we're not pending a reset, see if we can seek within the sample queues.
    boolean seekInsideBuffer = !isPendingReset();
    for (int i = 0; seekInsideBuffer && i < sampleQueues.size(); i++) {
      seekInsideBuffer &= sampleQueues.valueAt(i).skipToKeyframeBefore(positionUs);
    }

    // If we failed to seek within the sample queues, we need to restart.
    if (!seekInsideBuffer) {
      restartFrom(positionUs);
    }

    // Either way, we need to send discontinuities to the downstream components.
    for (int i = 0; i < pendingDiscontinuities.length; i++) {
      pendingDiscontinuities[i] = true;
    }
  }

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return TrackRenderer.END_OF_TRACK_US;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long largestParsedTimestampUs = Long.MIN_VALUE;
      for (int i = 0; i < sampleQueues.size(); i++) {
        largestParsedTimestampUs = Math.max(largestParsedTimestampUs,
            sampleQueues.valueAt(i).getLargestParsedTimestampUs());
      }
      return largestParsedTimestampUs == Long.MIN_VALUE ? downstreamPositionUs
          : largestParsedTimestampUs;
    }
  }

  @Override
  public void release() {
    Assertions.checkState(remainingReleaseCount > 0);
    if (--remainingReleaseCount == 0) {
      loader.release();
      loader = null;
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Loadable loadable) {
    loadingFinished = true;
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    if (enabledTrackCount > 0) {
      restartFrom(pendingResetPositionUs);
    } else {
      clearState();
      bufferPool.trim(0);
    }
  }

  @Override
  public void onLoadError(Loadable ignored, IOException e) {
    currentLoadableException = e;
    currentLoadableExceptionCount = extractedSampleCount > extractedSampleCountAtStartOfLoad ? 1
        : currentLoadableExceptionCount + 1;
    currentLoadableExceptionTimestamp = SystemClock.elapsedRealtime();
    maybeStartLoading();
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id) {
    InternalTrackOutput sampleQueue = sampleQueues.get(id);
    if (sampleQueue == null) {
      sampleQueue = new InternalTrackOutput(bufferPool);
      sampleQueues.put(id, sampleQueue);
    }
    return sampleQueue;
  }

  @Override
  public void endTracks() {
    tracksBuilt = true;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
  }

  @Override
  public void drmInitData(DrmInitData drmInitData) {
    this.drmInitData = drmInitData;
  }

  // Internal stuff.

  private boolean continueBufferingInternal() throws IOException {
    maybeStartLoading();
    if (isPendingReset()) {
      return false;
    }
    boolean haveSamples = prepared && haveSampleForOneEnabledTrack();
    if (!haveSamples) {
      maybeThrowLoadableException();
    }
    return haveSamples;
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

  private void maybeStartLoading() {
    if (currentLoadableExceptionFatal || loadingFinished || loader.isLoading()) {
      return;
    }

    if (currentLoadableException != null) {
      Assertions.checkState(loadable != null);
      long elapsedMillis = SystemClock.elapsedRealtime() - currentLoadableExceptionTimestamp;
      if (elapsedMillis >= getRetryDelayMillis(currentLoadableExceptionCount)) {
        currentLoadableException = null;
        if (!prepared) {
          // We don't know whether we're playing an on-demand or a live stream. For a live stream
          // we need to load from the start, as outlined below. Since we might be playing a live
          // stream, play it safe and load from the start.
          for (int i = 0; i < sampleQueues.size(); i++) {
            sampleQueues.valueAt(i).clear();
          }
          loadable = createLoadableFromStart();
        } else if (!seekMap.isSeekable()) {
          // We're playing a non-seekable stream. Assume it's live, and therefore that the data at
          // the uri is a continuously shifting window of the latest available media. For this case
          // there's no way to continue loading from where a previous load finished, and hence it's
          // necessary to load from the start whenever commencing a new load.
          for (int i = 0; i < sampleQueues.size(); i++) {
            sampleQueues.valueAt(i).clear();
          }
          loadable = createLoadableFromStart();
          // To avoid introducing a discontinuity, we shift the sample timestamps so that they will
          // continue from the current downstream position.
          pendingNextSampleUs = downstreamPositionUs;
          havePendingNextSampleUs = true;
        } else {
          // We're playing a seekable on-demand stream. Resume the current loadable, which will
          // request data starting from the point it left off.
        }
        extractedSampleCountAtStartOfLoad = extractedSampleCount;
        loader.startLoading(loadable, this);
      }
      return;
    }

    // We're not retrying, so we're either starting a playback or responding to an explicit seek.
    // In both cases sampleTimeOffsetUs should be reset to zero, and any pending adjustment to
    // sample timestamps should be discarded.
    sampleTimeOffsetUs = 0;
    havePendingNextSampleUs = false;

    if (!prepared) {
      loadable = createLoadableFromStart();
    } else {
      Assertions.checkState(isPendingReset());
      loadable = createLoadableFromPositionUs(pendingResetPositionUs);
      pendingResetPositionUs = NO_RESET_PENDING;
    }
    extractedSampleCountAtStartOfLoad = extractedSampleCount;
    loader.startLoading(loadable, this);
  }

  private void maybeThrowLoadableException() throws IOException {
    if (currentLoadableException == null) {
      return;
    }
    if (currentLoadableExceptionFatal) {
      throw currentLoadableException;
    }
    int minLoadableRetryCountForMedia;
    if (minLoadableRetryCount != MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA) {
      minLoadableRetryCountForMedia = minLoadableRetryCount;
    } else {
      minLoadableRetryCountForMedia = seekMap != null && !seekMap.isSeekable()
          ? DEFAULT_MIN_LOADABLE_RETRY_COUNT_LIVE
          : DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND;
    }
    if (currentLoadableExceptionCount > minLoadableRetryCountForMedia) {
      throw currentLoadableException;
    }
  }

  private ExtractingLoadable createLoadableFromStart() {
    return new ExtractingLoadable(uri, dataSource, extractor, bufferPool, requestedBufferSize, 0);
  }

  private ExtractingLoadable createLoadableFromPositionUs(long positionUs) {
    return new ExtractingLoadable(uri, dataSource, extractor, bufferPool, requestedBufferSize,
        seekMap.getPosition(positionUs));
  }

  private boolean haveFormatsForAllTracks() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      if (!sampleQueues.valueAt(i).hasFormat()) {
        return false;
      }
    }
    return true;
  }

  private boolean haveSampleForOneEnabledTrack() {
    for (int i = 0; i < trackEnabledStates.length; i++) {
      if (trackEnabledStates[i] && !sampleQueues.valueAt(i).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private void discardSamplesForDisabledTracks(long timeUs) {
    for (int i = 0; i < trackEnabledStates.length; i++) {
      if (!trackEnabledStates[i]) {
        sampleQueues.valueAt(i).discardUntil(timeUs);
      }
    }
  }

  private void clearState() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).clear();
    }
    loadable = null;
    currentLoadableException = null;
    currentLoadableExceptionCount = 0;
    currentLoadableExceptionFatal = false;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != NO_RESET_PENDING;
  }

  private long getRetryDelayMillis(long errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  /**
   * Extension of {@link DefaultTrackOutput} that increments a shared counter of the total number
   * of extracted samples.
   */
  private class InternalTrackOutput extends DefaultTrackOutput {

    public InternalTrackOutput(Allocator allocator) {
      super(allocator);
    }

    @Override
    public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
      super.sampleMetadata(timeUs, flags, size, offset, encryptionKey);
      extractedSampleCount++;
    }

  }

  /**
   * Loads the media stream and extracts sample data from it.
   */
  private static class ExtractingLoadable implements Loadable {

    private final Uri uri;
    private final DataSource dataSource;
    private final Extractor extractor;
    private final BufferPool bufferPool;
    private final int bufferPoolSizeLimit;
    private final PositionHolder positionHolder;

    private volatile boolean loadCanceled;

    private boolean pendingExtractorSeek;

    public ExtractingLoadable(Uri uri, DataSource dataSource, Extractor extractor,
        BufferPool bufferPool, int bufferPoolSizeLimit, long position) {
      this.uri = Assertions.checkNotNull(uri);
      this.dataSource = Assertions.checkNotNull(dataSource);
      this.extractor = Assertions.checkNotNull(extractor);
      this.bufferPool = Assertions.checkNotNull(bufferPool);
      this.bufferPoolSizeLimit = bufferPoolSizeLimit;
      positionHolder = new PositionHolder();
      positionHolder.position = position;
      pendingExtractorSeek = true;
    }

    @Override
    public void cancelLoad() {
      loadCanceled = true;
    }

    @Override
    public boolean isLoadCanceled() {
      return loadCanceled;
    }

    @Override
    public void load() throws IOException, InterruptedException {
      if (pendingExtractorSeek) {
        extractor.seek();
        pendingExtractorSeek = false;
      }
      int result = Extractor.RESULT_CONTINUE;
      while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
        ExtractorInput input = null;
        try {
          long position = positionHolder.position;
          long length = dataSource.open(new DataSpec(uri, position, C.LENGTH_UNBOUNDED, null));
          if (length != C.LENGTH_UNBOUNDED) {
            length += position;
          }
          input = new DefaultExtractorInput(dataSource, position, length);
          while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
            bufferPool.blockWhileAllocatedSizeExceeds(bufferPoolSizeLimit);
            result = extractor.read(input, positionHolder);
            // TODO: Implement throttling to stop us from buffering data too often.
          }
        } finally {
          if (result == Extractor.RESULT_SEEK) {
            result = Extractor.RESULT_CONTINUE;
          } else if (input != null) {
            positionHolder.position = input.getPosition();
          }
          dataSource.close();
        }
      }
    }

  }

}
