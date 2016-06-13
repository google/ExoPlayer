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
package com.google.android.exoplayer;

import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;

import android.net.Uri;
import android.os.Handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link SampleSource} that loads the data at a given {@link Uri} as a single sample.
 */
public final class SingleSampleSource implements SampleSource, TrackStream,
    Loader.Callback<SingleSampleSource>, Loadable {

  /**
   * Interface definition for a callback to be notified of {@link SingleSampleSource} events.
   */
  public interface EventListener {

    /**
     * Invoked when an error occurs loading media data.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
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
  private final DataSource dataSource;
  private final Loader loader;
  private final Format format;
  private final long durationUs;
  private final int minLoadableRetryCount;
  private final TrackGroupArray tracks;
  private final Handler eventHandler;
  private final EventListener eventListener;
  private final int eventSourceId;

  private long pendingResetPositionUs;
  private boolean loadingFinished;

  private int streamState;
  private byte[] sampleData;
  private int sampleSize;

  public SingleSampleSource(Uri uri, DataSource dataSource, Format format, long durationUs) {
    this(uri, dataSource, format, durationUs, DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  public SingleSampleSource(Uri uri, DataSource dataSource, Format format, long durationUs,
      int minLoadableRetryCount) {
    this(uri, dataSource, format, durationUs, minLoadableRetryCount, null, null, 0);
  }

  public SingleSampleSource(Uri uri, DataSource dataSource, Format format, long durationUs,
      int minLoadableRetryCount, Handler eventHandler, EventListener eventListener,
      int eventSourceId) {
    this.uri = uri;
    this.dataSource = dataSource;
    this.format = format;
    this.durationUs = durationUs;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
    loader = new Loader("Loader:SingleSampleSource");
    tracks = new TrackGroupArray(new TrackGroup(format));
    sampleData = new byte[INITIAL_SAMPLE_SIZE];
  }

  // SampleSource implementation.

  @Override
  public boolean prepare(long positionUs) {
    return true;
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
  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    Assertions.checkState(oldStreams.size() <= 1);
    Assertions.checkState(newSelections.size() <= 1);
    // Unselect old tracks.
    if (!oldStreams.isEmpty()) {
      streamState = STREAM_STATE_END_OF_STREAM;
      if (loader.isLoading()) {
        loader.cancelLoading();
      }
    }
    // Select new tracks.
    TrackStream[] newStreams = new TrackStream[newSelections.size()];
    if (!newSelections.isEmpty()) {
      newStreams[0] = this;
      streamState = STREAM_STATE_SEND_FORMAT;
      pendingResetPositionUs = C.UNSET_TIME_US;
      maybeStartLoading();
    }
    return newStreams;
  }

  @Override
  public void continueBuffering(long positionUs) {
    // Do nothing.
  }

  @Override
  public long getBufferedPositionUs() {
    return loadingFinished ? C.END_OF_SOURCE_US : 0;
  }

  @Override
  public void seekToUs(long positionUs) {
    if (streamState == STREAM_STATE_END_OF_STREAM) {
      pendingResetPositionUs = positionUs;
      streamState = STREAM_STATE_SEND_SAMPLE;
    }
  }

  @Override
  public void release() {
    loader.release();
  }

  // TrackStream implementation.

  @Override
  public boolean isReady() {
    return loadingFinished;
  }

  @Override
  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
  }

  @Override
  public long readReset() {
    long resetPositionUs = pendingResetPositionUs;
    pendingResetPositionUs = C.UNSET_TIME_US;
    return resetPositionUs;
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
    if (streamState == STREAM_STATE_END_OF_STREAM) {
      buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      return BUFFER_READ;
    } else if (streamState == STREAM_STATE_SEND_FORMAT) {
      formatHolder.format = format;
      streamState = STREAM_STATE_SEND_SAMPLE;
      return FORMAT_READ;
    }

    Assertions.checkState(streamState == STREAM_STATE_SEND_SAMPLE);
    if (!loadingFinished) {
      return NOTHING_READ;
    } else {
      buffer.timeUs = 0;
      buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
      buffer.ensureSpaceForWrite(sampleSize);
      buffer.data.put(sampleData, 0, sampleSize);
      streamState = STREAM_STATE_END_OF_STREAM;
      return BUFFER_READ;
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(SingleSampleSource loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    loadingFinished = true;
  }

  @Override
  public void onLoadCanceled(SingleSampleSource loadable, long elapsedRealtimeMs,
      long loadDurationMs, boolean released) {
    if (!released) {
      maybeStartLoading();
    }
  }

  @Override
  public int onLoadError(SingleSampleSource loadable, long elapsedRealtimeMs,
      long loadDurationMs, IOException error) {
    notifyLoadError(error);
    return Loader.RETRY;
  }

  // Loadable implementation.

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

  // Internal methods.

  private void maybeStartLoading() {
    if (loadingFinished || streamState == STREAM_STATE_END_OF_STREAM || loader.isLoading()) {
      return;
    }
    loader.startLoading(this, this, minLoadableRetryCount);
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

}
