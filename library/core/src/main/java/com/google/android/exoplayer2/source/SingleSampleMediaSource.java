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

import android.net.Uri;
import android.os.Handler;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Loads data at a given {@link Uri} as a single sample belonging to a single {@link MediaPeriod}.
 */
public final class SingleSampleMediaSource implements MediaSource {

  /**
   * Listener of {@link SingleSampleMediaSource} events.
   *
   * @deprecated Use {@link MediaSourceEventListener}.
   */
  @Deprecated
  public interface EventListener {

    /**
     * Called when an error occurs loading media data.
     *
     * @param sourceId The id of the reporting {@link SingleSampleMediaSource}.
     * @param e The cause of the failure.
     */
    void onLoadError(int sourceId, IOException e);

  }

  /** Factory for {@link SingleSampleMediaSource}. */
  public static final class Factory {

    private final DataSource.Factory dataSourceFactory;

    private int minLoadableRetryCount;
    private boolean treatLoadErrorsAsEndOfStream;
    private boolean isCreateCalled;

    /**
     * Creates a factory for {@link SingleSampleMediaSource}s.
     *
     * @param dataSourceFactory The factory from which the {@link DataSource} to read the media will
     *     be obtained.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this.dataSourceFactory = Assertions.checkNotNull(dataSourceFactory);
      this.minLoadableRetryCount = DEFAULT_MIN_LOADABLE_RETRY_COUNT;
    }

    /**
     * Sets the minimum number of times to retry if a loading error occurs. The default value is
     * {@link #DEFAULT_MIN_LOADABLE_RETRY_COUNT}.
     *
     * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setMinLoadableRetryCount(int minLoadableRetryCount) {
      Assertions.checkState(!isCreateCalled);
      this.minLoadableRetryCount = minLoadableRetryCount;
      return this;
    }

    /**
     * Sets whether load errors will be treated as end-of-stream signal (load errors will not be
     * propagated). The default value is false.
     *
     * @param treatLoadErrorsAsEndOfStream If true, load errors will not be propagated by sample
     *     streams, treating them as ended instead. If false, load errors will be propagated
     *     normally by {@link SampleStream#maybeThrowError()}.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setTreatLoadErrorsAsEndOfStream(boolean treatLoadErrorsAsEndOfStream) {
      Assertions.checkState(!isCreateCalled);
      this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
      return this;
    }

    /**
     * Returns a new {@link ExtractorMediaSource} using the current parameters. Media source events
     * will not be delivered.
     *
     * @param uri The {@link Uri}.
     * @param format The {@link Format} of the media stream.
     * @param durationUs The duration of the media stream in microseconds.
     * @return The new {@link ExtractorMediaSource}.
     */
    public SingleSampleMediaSource createMediaSource(Uri uri, Format format, long durationUs) {
      return createMediaSource(uri, format, durationUs, null, null);
    }

    /**
     * Returns a new {@link SingleSampleMediaSource} using the current parameters.
     *
     * @param uri The {@link Uri}.
     * @param format The {@link Format} of the media stream.
     * @param durationUs The duration of the media stream in microseconds.
     * @param eventHandler A handler for events.
     * @param eventListener A listener of events., Format format, long durationUs
     * @return The newly built {@link SingleSampleMediaSource}.
     */
    public SingleSampleMediaSource createMediaSource(
        Uri uri,
        Format format,
        long durationUs,
        @Nullable Handler eventHandler,
        @Nullable MediaSourceEventListener eventListener) {
      isCreateCalled = true;
      return new SingleSampleMediaSource(
          uri,
          dataSourceFactory,
          format,
          durationUs,
          minLoadableRetryCount,
          eventHandler,
          eventListener,
          treatLoadErrorsAsEndOfStream);
    }

  }

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private final DataSpec dataSpec;
  private final DataSource.Factory dataSourceFactory;
  private final Format format;
  private final long durationUs;
  private final MediaSourceEventListener.EventDispatcher eventDispatcher;
  private final int minLoadableRetryCount;
  private final boolean treatLoadErrorsAsEndOfStream;
  private final Timeline timeline;

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory The factory from which the {@link DataSource} to read the media will
   *     be obtained.
   * @param format The {@link Format} associated with the output track.
   * @param durationUs The duration of the media stream in microseconds.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public SingleSampleMediaSource(
      Uri uri, DataSource.Factory dataSourceFactory, Format format, long durationUs) {
    this(uri, dataSourceFactory, format, durationUs, DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory The factory from which the {@link DataSource} to read the media will
   *     be obtained.
   * @param format The {@link Format} associated with the output track.
   * @param durationUs The duration of the media stream in microseconds.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public SingleSampleMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      Format format,
      long durationUs,
      int minLoadableRetryCount) {
    this(uri, dataSourceFactory, format, durationUs, minLoadableRetryCount, null, null, false);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory The factory from which the {@link DataSource} to read the media will
   *     be obtained.
   * @param format The {@link Format} associated with the output track.
   * @param durationUs The duration of the media stream in microseconds.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param eventSourceId An identifier that gets passed to {@code eventListener} methods.
   * @param treatLoadErrorsAsEndOfStream If true, load errors will not be propagated by sample
   *     streams, treating them as ended instead. If false, load errors will be propagated normally
   *     by {@link SampleStream#maybeThrowError()}.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public SingleSampleMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      Format format,
      long durationUs,
      int minLoadableRetryCount,
      Handler eventHandler,
      EventListener eventListener,
      int eventSourceId,
      boolean treatLoadErrorsAsEndOfStream) {
    this(
        uri,
        dataSourceFactory,
        format,
        durationUs,
        minLoadableRetryCount,
        eventHandler,
        eventListener == null ? null : new EventListenerWrapper(eventListener, eventSourceId),
        treatLoadErrorsAsEndOfStream);
  }

  private SingleSampleMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      Format format,
      long durationUs,
      int minLoadableRetryCount,
      Handler eventHandler,
      MediaSourceEventListener eventListener,
      boolean treatLoadErrorsAsEndOfStream) {
    this.dataSourceFactory = dataSourceFactory;
    this.format = format;
    this.durationUs = durationUs;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
    this.eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    dataSpec = new DataSpec(uri);
    timeline = new SinglePeriodTimeline(durationUs, true);
  }

  // MediaSource implementation.

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    listener.onSourceInfoRefreshed(this, timeline, null);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    Assertions.checkArgument(id.periodIndex == 0);
    return new SingleSampleMediaPeriod(
        dataSpec,
        dataSourceFactory,
        format,
        durationUs,
        minLoadableRetryCount,
        eventDispatcher,
        treatLoadErrorsAsEndOfStream);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((SingleSampleMediaPeriod) mediaPeriod).release();
  }

  @Override
  public void releaseSource() {
    // Do nothing.
  }

  /**
   * Wraps a deprecated {@link EventListener}, invoking its callback from the equivalent callback in
   * {@link MediaSourceEventListener}.
   */
  private static final class EventListenerWrapper implements MediaSourceEventListener {

    private final EventListener eventListener;
    private final int eventSourceId;

    public EventListenerWrapper(EventListener eventListener, int eventSourceId) {
      this.eventListener = Assertions.checkNotNull(eventListener);
      this.eventSourceId = eventSourceId;
    }

    @Override
    public void onLoadStarted(
        DataSpec dataSpec,
        int dataType,
        int trackType,
        Format trackFormat,
        int trackSelectionReason,
        Object trackSelectionData,
        long mediaStartTimeMs,
        long mediaEndTimeMs,
        long elapsedRealtimeMs) {
      // Do nothing.
    }

    @Override
    public void onLoadCompleted(
        DataSpec dataSpec,
        int dataType,
        int trackType,
        Format trackFormat,
        int trackSelectionReason,
        Object trackSelectionData,
        long mediaStartTimeMs,
        long mediaEndTimeMs,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded) {
      // Do nothing.
    }

    @Override
    public void onLoadCanceled(
        DataSpec dataSpec,
        int dataType,
        int trackType,
        Format trackFormat,
        int trackSelectionReason,
        Object trackSelectionData,
        long mediaStartTimeMs,
        long mediaEndTimeMs,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded) {
      // Do nothing.
    }

    @Override
    public void onLoadError(
        DataSpec dataSpec,
        int dataType,
        int trackType,
        Format trackFormat,
        int trackSelectionReason,
        Object trackSelectionData,
        long mediaStartTimeMs,
        long mediaEndTimeMs,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded,
        IOException error,
        boolean wasCanceled) {
      eventListener.onLoadError(eventSourceId, error);
    }

    @Override
    public void onUpstreamDiscarded(int trackType, long mediaStartTimeMs, long mediaEndTimeMs) {
      // Do nothing.
    }

    @Override
    public void onDownstreamFormatChanged(
        int trackType,
        Format trackFormat,
        int trackSelectionReason,
        Object trackSelectionData,
        long mediaTimeMs) {
      // Do nothing.
    }
  }
}
