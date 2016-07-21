/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.util.Assertions;

import android.net.Uri;
import android.os.Handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Loads data at a given {@link Uri} as a single sample belonging to a single {@link MediaPeriod}.
 */
public final class SingleSampleMediaSource implements MediaPeriod, MediaSource, SampleStream,
    Loader.Callback<SingleSampleMediaSource.SourceLoadable> {

  /**
   * Interface definition for a callback to be notified of {@link SingleSampleMediaSource} events.
   */
  public interface EventListener {

    /**
     * Invoked when an error occurs loading media data.
     *
     * @param sourceId The id of the reporting {@link SingleSampleMediaSource}.
     * @param e The cause of the failure.
     */
    void onLoadError(int sourceId, IOException e);

  }

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  /**
   * The initial size of the allocation used to hold the sample data.
   */
  private static final int INITIAL_SAMPLE_SIZE = 1;

  private static final int STREAM_STATE_SEND_FORMAT = 0;
  private static final int STREAM_STATE_SEND_SAMPLE = 1;
  private static final int STREAM_STATE_END_OF_STREAM = 2;

  private final Uri uri;
  private final DataSource.Factory dataSourceFactory;
  private final Format format;
  private final long durationUs;
  private final int minLoadableRetryCount;
  private final TrackGroupArray tracks;
  private final Handler eventHandler;
  private final EventListener eventListener;
  private final int eventSourceId;

  private Loader loader;
  private boolean loadingFinished;

  private int streamState;
  private byte[] sampleData;
  private int sampleSize;

  public SingleSampleMediaSource(Uri uri, DataSource.Factory dataSourceFactory, Format format,
      long durationUs) {
    this(uri, dataSourceFactory, format, durationUs, DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  public SingleSampleMediaSource(Uri uri, DataSource.Factory dataSourceFactory, Format format,
      long durationUs, int minLoadableRetryCount) {
    this(uri, dataSourceFactory, format, durationUs, minLoadableRetryCount, null, null, 0);
  }

  public SingleSampleMediaSource(Uri uri, DataSource.Factory dataSourceFactory, Format format,
      long durationUs, int minLoadableRetryCount, Handler eventHandler, EventListener eventListener,
      int eventSourceId) {
    this.uri = uri;
    this.dataSourceFactory = dataSourceFactory;
    this.format = format;
    this.durationUs = durationUs;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
    tracks = new TrackGroupArray(new TrackGroup(format));
    sampleData = new byte[INITIAL_SAMPLE_SIZE];
    streamState = STREAM_STATE_SEND_FORMAT;
  }

  // MediaSource implementation.

  @Override
  public void prepareSource() {
    // do nothing
  }

  @Override
  public int getPeriodCount() {
    return 1;
  }

  @Override
  public MediaPeriod createPeriod(int index) {
    Assertions.checkArgument(index == 0);
    return this;
  }

  @Override
  public void releaseSource() {
    // do nothing
  }

  // MediaPeriod implementation.

  @Override
  public void preparePeriod(Callback callback, Allocator allocator, long positionUs) {
    loader = new Loader("Loader:SingleSampleMediaSource");
    callback.onPeriodPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    // Do nothing.
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return tracks;
  }

  @Override
  public SampleStream[] selectTracks(List<SampleStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    Assertions.checkState(oldStreams.size() <= 1);
    Assertions.checkState(newSelections.size() <= 1);
    // Select new tracks.
    SampleStream[] newStreams = new SampleStream[newSelections.size()];
    if (!newSelections.isEmpty()) {
      newStreams[0] = this;
      streamState = STREAM_STATE_SEND_FORMAT;
    }
    return newStreams;
  }

  @Override
  public boolean continueLoading(long positionUs) {
    if (loadingFinished || loader.isLoading()) {
      return false;
    }
    loader.startLoading(new SourceLoadable(uri, dataSourceFactory.createDataSource()), this,
        minLoadableRetryCount);
    return true;
  }

  @Override
  public long getNextLoadPositionUs() {
    return loadingFinished || loader.isLoading() ? C.END_OF_SOURCE_US : 0;
  }

  @Override
  public long getBufferedPositionUs() {
    return loadingFinished ? C.END_OF_SOURCE_US : 0;
  }

  @Override
  public long seekToUs(long positionUs) {
    if (streamState == STREAM_STATE_END_OF_STREAM) {
      streamState = STREAM_STATE_SEND_SAMPLE;
    }
    return positionUs;
  }

  @Override
  public void releasePeriod() {
    if (loader != null) {
      loader.release();
      loader = null;
    }
    loadingFinished = false;
    streamState = STREAM_STATE_SEND_FORMAT;
    sampleData = null;
    sampleSize = 0;
  }

  // SampleStream implementation.

  @Override
  public boolean isReady() {
    return loadingFinished;
  }

  @Override
  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
  }

  @Override
  public long readDiscontinuity() {
    return C.UNSET_TIME_US;
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
    if (streamState == STREAM_STATE_END_OF_STREAM) {
      buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      return C.RESULT_BUFFER_READ;
    } else if (streamState == STREAM_STATE_SEND_FORMAT) {
      formatHolder.format = format;
      streamState = STREAM_STATE_SEND_SAMPLE;
      return C.RESULT_FORMAT_READ;
    }

    Assertions.checkState(streamState == STREAM_STATE_SEND_SAMPLE);
    if (!loadingFinished) {
      return C.RESULT_NOTHING_READ;
    } else {
      buffer.timeUs = 0;
      buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
      buffer.ensureSpaceForWrite(sampleSize);
      buffer.data.put(sampleData, 0, sampleSize);
      streamState = STREAM_STATE_END_OF_STREAM;
      return C.RESULT_BUFFER_READ;
    }
  }

  @Override
  public void skipToKeyframeBefore(long timeUs) {
    // do nothing
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(SourceLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    sampleSize = loadable.sampleSize;
    sampleData = loadable.sampleData;
    loadingFinished = true;
  }

  @Override
  public void onLoadCanceled(SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs,
      boolean released) {
    // Do nothing.
  }

  @Override
  public int onLoadError(SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs,
      IOException error) {
    notifyLoadError(error);
    return Loader.RETRY;
  }

  // Internal methods.

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

  /* package */ static final class SourceLoadable implements Loadable {

    private final Uri uri;
    private final DataSource dataSource;

    private int sampleSize;
    private byte[] sampleData;

    public SourceLoadable(Uri uri, DataSource dataSource) {
      this.uri = uri;
      this.dataSource = dataSource;
    }

    @Override
    public void cancelLoad() {
      // Never happens.
    }

    @Override
    public boolean isLoadCanceled() {
      return false;
    }

    @Override
    public void load() throws IOException, InterruptedException {
      // We always load from the beginning, so reset the sampleSize to 0.
      sampleSize = 0;
      try {
        // Create and open the input.
        dataSource.open(new DataSpec(uri));
        // Load the sample data.
        int result = 0;
        while (result != C.RESULT_END_OF_INPUT) {
          sampleSize += result;
          if (sampleSize == sampleData.length) {
            sampleData = Arrays.copyOf(sampleData, sampleData.length * 2);
          }
          result = dataSource.read(sampleData, sampleSize, sampleData.length - sampleSize);
        }
      } finally {
        dataSource.close();
      }
    }

  }

}
