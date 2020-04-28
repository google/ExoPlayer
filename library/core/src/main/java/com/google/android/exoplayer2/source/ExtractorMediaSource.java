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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;

/** @deprecated Use {@link ProgressiveMediaSource} instead. */
@Deprecated
@SuppressWarnings("deprecation")
public final class ExtractorMediaSource extends CompositeMediaSource<Void> {

  /** @deprecated Use {@link MediaSourceEventListener} instead. */
  @Deprecated
  public interface EventListener {

    /**
     * Called when an error occurs loading media data.
     * <p>
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error and continue. Hence applications should
     * <em>not</em> implement this method to display a user visible error or initiate an application
     * level retry ({@link Player.EventListener#onPlayerError} is the appropriate place to implement
     * such behavior). This method is called to provide the application with an opportunity to log
     * the error if it wishes to do so.
     *
     * @param error The load error.
     */
    void onLoadError(IOException error);

  }

  /** @deprecated Use {@link ProgressiveMediaSource.Factory} instead. */
  @Deprecated
  public static final class Factory implements MediaSourceFactory {

    private final DataSource.Factory dataSourceFactory;

    private ExtractorsFactory extractorsFactory;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private int continueLoadingCheckIntervalBytes;
    @Nullable private String customCacheKey;
    @Nullable private Object tag;

    /**
     * Creates a new factory for {@link ExtractorMediaSource}s.
     *
     * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this.dataSourceFactory = dataSourceFactory;
      extractorsFactory = new DefaultExtractorsFactory();
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      continueLoadingCheckIntervalBytes = DEFAULT_LOADING_CHECK_INTERVAL_BYTES;
    }

    /**
     * Sets the factory for {@link Extractor}s to process the media stream. The default value is an
     * instance of {@link DefaultExtractorsFactory}.
     *
     * @param extractorsFactory A factory for {@link Extractor}s to process the media stream. If the
     *     possible formats are known, pass a factory that instantiates extractors for those
     *     formats.
     * @return This factory, for convenience.
     */
    public Factory setExtractorsFactory(@Nullable ExtractorsFactory extractorsFactory) {
      this.extractorsFactory =
          extractorsFactory != null ? extractorsFactory : new DefaultExtractorsFactory();
      return this;
    }

    /**
     * Sets the custom key that uniquely identifies the original stream. Used for cache indexing.
     * The default value is {@code null}.
     *
     * @param customCacheKey A custom key that uniquely identifies the original stream. Used for
     *     cache indexing.
     * @return This factory, for convenience.
     */
    public Factory setCustomCacheKey(@Nullable String customCacheKey) {
      this.customCacheKey = customCacheKey;
      return this;
    }

    /**
     * @deprecated Use {@link MediaItem.Builder#setTag(Object)} and {@link
     *     #createMediaSource(MediaItem)} instead.
     */
    @Deprecated
    public Factory setTag(@Nullable Object tag) {
      this.tag = tag;
      return this;
    }

    /** @deprecated Use {@link #setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)} instead. */
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
    @Override
    public Factory setLoadErrorHandlingPolicy(
        @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.loadErrorHandlingPolicy =
          loadErrorHandlingPolicy != null
              ? loadErrorHandlingPolicy
              : new DefaultLoadErrorHandlingPolicy();
      return this;
    }

    /**
     * Sets the number of bytes that should be loaded between each invocation of {@link
     * MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}. The default value is
     * {@link #DEFAULT_LOADING_CHECK_INTERVAL_BYTES}.
     *
     * @param continueLoadingCheckIntervalBytes The number of bytes that should be loaded between
     *     each invocation of {@link
     *     MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}.
     * @return This factory, for convenience.
     */
    public Factory setContinueLoadingCheckIntervalBytes(int continueLoadingCheckIntervalBytes) {
      this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
      return this;
    }

    /** @deprecated Use {@link ProgressiveMediaSource.Factory#setDrmSessionManager} instead. */
    @Deprecated
    @Override
    public Factory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
      throw new UnsupportedOperationException();
    }

    /** @deprecated Use {@link #createMediaSource(MediaItem)} instead. */
    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public ExtractorMediaSource createMediaSource(Uri uri) {
      return createMediaSource(new MediaItem.Builder().setSourceUri(uri).build());
    }

    /**
     * Returns a new {@link ExtractorMediaSource} using the current parameters.
     *
     * @param mediaItem The {@link MediaItem}.
     * @return The new {@link ExtractorMediaSource}.
     * @throws NullPointerException if {@link MediaItem#playbackProperties} is {@code null}.
     */
    @Override
    public ExtractorMediaSource createMediaSource(MediaItem mediaItem) {
      Assertions.checkNotNull(mediaItem.playbackProperties);
      return new ExtractorMediaSource(
          mediaItem.playbackProperties.sourceUri,
          dataSourceFactory,
          extractorsFactory,
          loadErrorHandlingPolicy,
          customCacheKey,
          continueLoadingCheckIntervalBytes,
          mediaItem.playbackProperties.tag != null ? mediaItem.playbackProperties.tag : tag);
    }

    /**
     * @deprecated Use {@link #createMediaSource(Uri)} and {@link #addEventListener(Handler,
     *     MediaSourceEventListener)} instead.
     */
    @Deprecated
    public ExtractorMediaSource createMediaSource(
        Uri uri, @Nullable Handler eventHandler, @Nullable MediaSourceEventListener eventListener) {
      ExtractorMediaSource mediaSource = createMediaSource(uri);
      if (eventHandler != null && eventListener != null) {
        mediaSource.addEventListener(eventHandler, eventListener);
      }
      return mediaSource;
    }

    @Override
    public int[] getSupportedTypes() {
      return new int[] {C.TYPE_OTHER};
    }
  }

  /**
   * @deprecated Use {@link ProgressiveMediaSource#DEFAULT_LOADING_CHECK_INTERVAL_BYTES} instead.
   */
  @Deprecated
  public static final int DEFAULT_LOADING_CHECK_INTERVAL_BYTES =
      ProgressiveMediaSource.DEFAULT_LOADING_CHECK_INTERVAL_BYTES;

  private final ProgressiveMediaSource progressiveMediaSource;

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
   * @param extractorsFactory A factory for {@link Extractor}s to process the media stream. If the
   *     possible formats are known, pass a factory that instantiates extractors for those formats.
   *     Otherwise, pass a {@link DefaultExtractorsFactory} to use default extractors.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public ExtractorMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      ExtractorsFactory extractorsFactory,
      @Nullable Handler eventHandler,
      @Nullable EventListener eventListener) {
    this(uri, dataSourceFactory, extractorsFactory, eventHandler, eventListener, null);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
   * @param extractorsFactory A factory for {@link Extractor}s to process the media stream. If the
   *     possible formats are known, pass a factory that instantiates extractors for those formats.
   *     Otherwise, pass a {@link DefaultExtractorsFactory} to use default extractors.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param customCacheKey A custom key that uniquely identifies the original stream. Used for cache
   *     indexing. May be null.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public ExtractorMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      ExtractorsFactory extractorsFactory,
      @Nullable Handler eventHandler,
      @Nullable EventListener eventListener,
      @Nullable String customCacheKey) {
    this(
        uri,
        dataSourceFactory,
        extractorsFactory,
        eventHandler,
        eventListener,
        customCacheKey,
        DEFAULT_LOADING_CHECK_INTERVAL_BYTES);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
   * @param extractorsFactory A factory for {@link Extractor}s to process the media stream. If the
   *     possible formats are known, pass a factory that instantiates extractors for those formats.
   *     Otherwise, pass a {@link DefaultExtractorsFactory} to use default extractors.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param customCacheKey A custom key that uniquely identifies the original stream. Used for cache
   *     indexing. May be null.
   * @param continueLoadingCheckIntervalBytes The number of bytes that should be loaded between each
   *     invocation of {@link MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}.
   * @deprecated Use {@link Factory} instead.
   */
  @Deprecated
  public ExtractorMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      ExtractorsFactory extractorsFactory,
      @Nullable Handler eventHandler,
      @Nullable EventListener eventListener,
      @Nullable String customCacheKey,
      int continueLoadingCheckIntervalBytes) {
    this(
        uri,
        dataSourceFactory,
        extractorsFactory,
        new DefaultLoadErrorHandlingPolicy(),
        customCacheKey,
        continueLoadingCheckIntervalBytes,
        /* tag= */ null);
    if (eventListener != null && eventHandler != null) {
      addEventListener(eventHandler, new EventListenerWrapper(eventListener));
    }
  }

  private ExtractorMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      ExtractorsFactory extractorsFactory,
      LoadErrorHandlingPolicy loadableLoadErrorHandlingPolicy,
      @Nullable String customCacheKey,
      int continueLoadingCheckIntervalBytes,
      @Nullable Object tag) {
    progressiveMediaSource =
        new ProgressiveMediaSource(
            uri,
            dataSourceFactory,
            extractorsFactory,
            DrmSessionManager.getDummyDrmSessionManager(),
            loadableLoadErrorHandlingPolicy,
            customCacheKey,
            continueLoadingCheckIntervalBytes,
            tag);
  }

  @Override
  @Nullable
  public Object getTag() {
    return progressiveMediaSource.getTag();
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(mediaTransferListener);
    prepareChildSource(/* id= */ null, progressiveMediaSource);
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      @Nullable Void id, MediaSource mediaSource, Timeline timeline) {
    refreshSourceInfo(timeline);
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    return progressiveMediaSource.createPeriod(id, allocator, startPositionUs);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    progressiveMediaSource.releasePeriod(mediaPeriod);
  }

  @Deprecated
  private static final class EventListenerWrapper implements MediaSourceEventListener {

    private final EventListener eventListener;

    public EventListenerWrapper(EventListener eventListener) {
      this.eventListener = Assertions.checkNotNull(eventListener);
    }

    @Override
    public void onLoadError(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      eventListener.onLoadError(error);
    }
  }
}
