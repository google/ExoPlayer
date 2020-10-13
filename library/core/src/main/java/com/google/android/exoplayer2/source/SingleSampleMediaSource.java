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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.net.Uri;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;
import java.util.Collections;

/**
 * Loads data at a given {@link Uri} as a single sample belonging to a single {@link MediaPeriod}.
 */
public final class SingleSampleMediaSource extends BaseMediaSource {

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

    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private boolean treatLoadErrorsAsEndOfStream;
    @Nullable private Object tag;
    @Nullable private String trackId;

    /**
     * Creates a factory for {@link SingleSampleMediaSource}s.
     *
     * @param dataSourceFactory The factory from which the {@link DataSource} to read the media will
     *     be obtained.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this.dataSourceFactory = checkNotNull(dataSourceFactory);
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
    }

    /**
     * Sets a tag for the media source which will be published in the {@link Timeline} of the source
     * as {@link com.google.android.exoplayer2.MediaItem.PlaybackProperties#tag
     * Window#mediaItem.playbackProperties.tag}.
     *
     * @param tag A tag for the media source.
     * @return This factory, for convenience.
     */
    public Factory setTag(@Nullable Object tag) {
      this.tag = tag;
      return this;
    }

    /**
     * Sets an optional track id to be used.
     *
     * @param trackId An optional track id.
     * @return This factory, for convenience.
     */
    public Factory setTrackId(@Nullable String trackId) {
      this.trackId = trackId;
      return this;
    }

    /**
     * Sets the minimum number of times to retry if a loading error occurs. See {@link
     * #setLoadErrorHandlingPolicy} for the default value.
     *
     * <p>Calling this method is equivalent to calling {@link #setLoadErrorHandlingPolicy} with
     * {@link DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy(int)
     * DefaultLoadErrorHandlingPolicy(minLoadableRetryCount)}
     *
     * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
     * @return This factory, for convenience.
     * @deprecated Use {@link #setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)} instead.
     */
    @Deprecated
    public Factory setMinLoadableRetryCount(int minLoadableRetryCount) {
      return setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount));
    }

    /**
     * Sets the {@link LoadErrorHandlingPolicy}. The default value is created by calling {@link
     * DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy()}.
     *
     * <p>Calling this method overrides any calls to {@link #setMinLoadableRetryCount(int)}.
     *
     * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
     * @return This factory, for convenience.
     */
    public Factory setLoadErrorHandlingPolicy(
        @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.loadErrorHandlingPolicy =
          loadErrorHandlingPolicy != null
              ? loadErrorHandlingPolicy
              : new DefaultLoadErrorHandlingPolicy();
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
     */
    public Factory setTreatLoadErrorsAsEndOfStream(boolean treatLoadErrorsAsEndOfStream) {
      this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
      return this;
    }

    /**
     * Returns a new {@link SingleSampleMediaSource} using the current parameters.
     *
     * @param subtitle The {@link MediaItem.Subtitle}.
     * @param durationUs The duration of the media stream in microseconds.
     * @return The new {@link SingleSampleMediaSource}.
     */
    public SingleSampleMediaSource createMediaSource(MediaItem.Subtitle subtitle, long durationUs) {
      return new SingleSampleMediaSource(
          trackId,
          subtitle,
          dataSourceFactory,
          durationUs,
          loadErrorHandlingPolicy,
          treatLoadErrorsAsEndOfStream,
          tag);
    }

    /** @deprecated Use {@link #createMediaSource(MediaItem.Subtitle, long)} instead. */
    @Deprecated
    public SingleSampleMediaSource createMediaSource(Uri uri, Format format, long durationUs) {
      return new SingleSampleMediaSource(
          format.id == null ? trackId : format.id,
          new MediaItem.Subtitle(
              uri, checkNotNull(format.sampleMimeType), format.language, format.selectionFlags),
          dataSourceFactory,
          durationUs,
          loadErrorHandlingPolicy,
          treatLoadErrorsAsEndOfStream,
          tag);
    }
  }

  private final DataSpec dataSpec;
  private final DataSource.Factory dataSourceFactory;
  private final Format format;
  private final long durationUs;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final boolean treatLoadErrorsAsEndOfStream;
  private final Timeline timeline;
  private final MediaItem mediaItem;

  @Nullable private TransferListener transferListener;

  /** @deprecated Use {@link Factory} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public SingleSampleMediaSource(
      Uri uri, DataSource.Factory dataSourceFactory, Format format, long durationUs) {
    this(
        uri,
        dataSourceFactory,
        format,
        durationUs,
        DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  /** @deprecated Use {@link Factory} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public SingleSampleMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      Format format,
      long durationUs,
      int minLoadableRetryCount) {
    this(
        uri,
        dataSourceFactory,
        format,
        durationUs,
        minLoadableRetryCount,
        /* eventHandler= */ null,
        /* eventListener= */ null,
        /* ignored */ C.INDEX_UNSET,
        /* treatLoadErrorsAsEndOfStream= */ false);
  }

  /** @deprecated Use {@link Factory} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public SingleSampleMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      Format format,
      long durationUs,
      int minLoadableRetryCount,
      @Nullable Handler eventHandler,
      @Nullable EventListener eventListener,
      int eventSourceId,
      boolean treatLoadErrorsAsEndOfStream) {
    this(
        /* trackId= */ null,
        new MediaItem.Subtitle(
            uri, checkNotNull(format.sampleMimeType), format.language, format.selectionFlags),
        dataSourceFactory,
        durationUs,
        new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount),
        treatLoadErrorsAsEndOfStream,
        /* tag= */ null);
    if (eventHandler != null && eventListener != null) {
      addEventListener(eventHandler, new EventListenerWrapper(eventListener, eventSourceId));
    }
  }

  private SingleSampleMediaSource(
      @Nullable String trackId,
      MediaItem.Subtitle subtitle,
      DataSource.Factory dataSourceFactory,
      long durationUs,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      boolean treatLoadErrorsAsEndOfStream,
      @Nullable Object tag) {
    this.dataSourceFactory = dataSourceFactory;
    this.durationUs = durationUs;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
    mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.EMPTY)
            .setMediaId(subtitle.uri.toString())
            .setSubtitles(Collections.singletonList(subtitle))
            .setTag(tag)
            .build();
    format =
        new Format.Builder()
            .setId(trackId)
            .setSampleMimeType(subtitle.mimeType)
            .setLanguage(subtitle.language)
            .setSelectionFlags(subtitle.selectionFlags)
            .setRoleFlags(subtitle.roleFlags)
            .setLabel(subtitle.label)
            .build();
    dataSpec =
        new DataSpec.Builder().setUri(subtitle.uri).setFlags(DataSpec.FLAG_ALLOW_GZIP).build();
    timeline =
        new SinglePeriodTimeline(
            durationUs,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* manifest= */ null,
            mediaItem);
  }

  // MediaSource implementation.

  /**
   * @deprecated Use {@link #getMediaItem()} and {@link MediaItem.PlaybackProperties#tag} instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  @Nullable
  public Object getTag() {
    return castNonNull(mediaItem.playbackProperties).tag;
  }

  @Override
  public MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    transferListener = mediaTransferListener;
    refreshSourceInfo(timeline);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    return new SingleSampleMediaPeriod(
        dataSpec,
        dataSourceFactory,
        transferListener,
        format,
        durationUs,
        loadErrorHandlingPolicy,
        createEventDispatcher(id),
        treatLoadErrorsAsEndOfStream);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((SingleSampleMediaPeriod) mediaPeriod).release();
  }

  @Override
  protected void releaseSourceInternal() {
    // Do nothing.
  }

  /**
   * Wraps a deprecated {@link EventListener}, invoking its callback from the equivalent callback in
   * {@link MediaSourceEventListener}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  private static final class EventListenerWrapper implements MediaSourceEventListener {

    private final EventListener eventListener;
    private final int eventSourceId;

    public EventListenerWrapper(EventListener eventListener, int eventSourceId) {
      this.eventListener = checkNotNull(eventListener);
      this.eventSourceId = eventSourceId;
    }

    @Override
    public void onLoadError(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      eventListener.onLoadError(eventSourceId, error);
    }
  }
}
