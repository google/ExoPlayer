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
package androidx.media3.exoplayer.source;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.net.Uri;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;

/**
 * Provides one period that loads data from a {@link Uri} and extracted using an {@link Extractor}.
 *
 * <p>If the possible input stream container formats are known, pass a factory that instantiates
 * extractors for them to the constructor. Otherwise, pass a {@link DefaultExtractorsFactory} to use
 * the default extractors. When reading a new stream, the first {@link Extractor} in the array of
 * extractors created by the factory that returns {@code true} from {@link Extractor#sniff} will be
 * used to extract samples from the input stream.
 *
 * <p>Note that the built-in extractor for FLV streams does not support seeking.
 */
@UnstableApi
public final class ProgressiveMediaSource extends BaseMediaSource
    implements ProgressiveMediaPeriod.Listener {

  /** Factory for {@link ProgressiveMediaSource}s. */
  public static final class Factory implements MediaSourceFactory {

    private final DataSource.Factory dataSourceFactory;

    private ProgressiveMediaExtractor.Factory progressiveMediaExtractorFactory;
    private boolean usingCustomDrmSessionManagerProvider;
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private int continueLoadingCheckIntervalBytes;
    @Nullable private String customCacheKey;
    @Nullable private Object tag;

    /**
     * Creates a new factory for {@link ProgressiveMediaSource}s, using the extractors provided by
     * {@link DefaultExtractorsFactory}.
     *
     * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(dataSourceFactory, new DefaultExtractorsFactory());
    }

    /**
     * Equivalent to {@link #Factory(DataSource.Factory, ProgressiveMediaExtractor.Factory) new
     * Factory(dataSourceFactory, () -> new BundledExtractorsAdapter(extractorsFactory)}.
     */
    public Factory(DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory) {
      this(dataSourceFactory, playerId -> new BundledExtractorsAdapter(extractorsFactory));
    }

    /**
     * Creates a new factory for {@link ProgressiveMediaSource}s.
     *
     * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
     * @param progressiveMediaExtractorFactory A factory for the {@link ProgressiveMediaExtractor}
     *     to extract media from its container.
     */
    public Factory(
        DataSource.Factory dataSourceFactory,
        ProgressiveMediaExtractor.Factory progressiveMediaExtractorFactory) {
      this.dataSourceFactory = dataSourceFactory;
      this.progressiveMediaExtractorFactory = progressiveMediaExtractorFactory;
      drmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      continueLoadingCheckIntervalBytes = DEFAULT_LOADING_CHECK_INTERVAL_BYTES;
    }

    /**
     * @deprecated Pass the {@link ExtractorsFactory} via {@link #Factory(DataSource.Factory,
     *     ExtractorsFactory)}. This is necessary so that proguard can treat the default extractors
     *     factory as unused.
     */
    @Deprecated
    public Factory setExtractorsFactory(@Nullable ExtractorsFactory extractorsFactory) {
      this.progressiveMediaExtractorFactory =
          playerId ->
              new BundledExtractorsAdapter(
                  extractorsFactory != null ? extractorsFactory : new DefaultExtractorsFactory());
      return this;
    }

    /**
     * @deprecated Use {@link MediaItem.Builder#setCustomCacheKey(String)} and {@link
     *     #createMediaSource(MediaItem)} instead.
     */
    @Deprecated
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

    /**
     * Sets the {@link LoadErrorHandlingPolicy}. The default value is created by calling {@link
     * DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy()}.
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

    @Override
    public Factory setDrmSessionManagerProvider(
        @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
      if (drmSessionManagerProvider != null) {
        this.drmSessionManagerProvider = drmSessionManagerProvider;
        this.usingCustomDrmSessionManagerProvider = true;
      } else {
        this.drmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
        this.usingCustomDrmSessionManagerProvider = false;
      }
      return this;
    }

    @Deprecated
    @Override
    public Factory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
      if (drmSessionManager == null) {
        setDrmSessionManagerProvider(null);
      } else {
        setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager);
      }
      return this;
    }

    @Deprecated
    @Override
    public Factory setDrmHttpDataSourceFactory(
        @Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
      if (!usingCustomDrmSessionManagerProvider) {
        ((DefaultDrmSessionManagerProvider) drmSessionManagerProvider)
            .setDrmHttpDataSourceFactory(drmHttpDataSourceFactory);
      }
      return this;
    }

    @Deprecated
    @Override
    public Factory setDrmUserAgent(@Nullable String userAgent) {
      if (!usingCustomDrmSessionManagerProvider) {
        ((DefaultDrmSessionManagerProvider) drmSessionManagerProvider).setDrmUserAgent(userAgent);
      }
      return this;
    }

    /** @deprecated Use {@link #createMediaSource(MediaItem)} instead. */
    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public ProgressiveMediaSource createMediaSource(Uri uri) {
      return createMediaSource(new MediaItem.Builder().setUri(uri).build());
    }

    /**
     * Returns a new {@link ProgressiveMediaSource} using the current parameters.
     *
     * @param mediaItem The {@link MediaItem}.
     * @return The new {@link ProgressiveMediaSource}.
     * @throws NullPointerException if {@link MediaItem#localConfiguration} is {@code null}.
     */
    @Override
    public ProgressiveMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      boolean needsTag = mediaItem.localConfiguration.tag == null && tag != null;
      boolean needsCustomCacheKey =
          mediaItem.localConfiguration.customCacheKey == null && customCacheKey != null;
      if (needsTag && needsCustomCacheKey) {
        mediaItem = mediaItem.buildUpon().setTag(tag).setCustomCacheKey(customCacheKey).build();
      } else if (needsTag) {
        mediaItem = mediaItem.buildUpon().setTag(tag).build();
      } else if (needsCustomCacheKey) {
        mediaItem = mediaItem.buildUpon().setCustomCacheKey(customCacheKey).build();
      }
      return new ProgressiveMediaSource(
          mediaItem,
          dataSourceFactory,
          progressiveMediaExtractorFactory,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          continueLoadingCheckIntervalBytes);
    }

    @Override
    public int[] getSupportedTypes() {
      return new int[] {C.TYPE_OTHER};
    }
  }

  /**
   * The default number of bytes that should be loaded between each each invocation of {@link
   * MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}.
   */
  public static final int DEFAULT_LOADING_CHECK_INTERVAL_BYTES = 1024 * 1024;

  private final MediaItem mediaItem;
  private final MediaItem.LocalConfiguration localConfiguration;
  private final DataSource.Factory dataSourceFactory;
  private final ProgressiveMediaExtractor.Factory progressiveMediaExtractorFactory;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadableLoadErrorHandlingPolicy;
  private final int continueLoadingCheckIntervalBytes;

  private boolean timelineIsPlaceholder;
  private long timelineDurationUs;
  private boolean timelineIsSeekable;
  private boolean timelineIsLive;
  @Nullable private TransferListener transferListener;

  private ProgressiveMediaSource(
      MediaItem mediaItem,
      DataSource.Factory dataSourceFactory,
      ProgressiveMediaExtractor.Factory progressiveMediaExtractorFactory,
      DrmSessionManager drmSessionManager,
      LoadErrorHandlingPolicy loadableLoadErrorHandlingPolicy,
      int continueLoadingCheckIntervalBytes) {
    this.localConfiguration = checkNotNull(mediaItem.localConfiguration);
    this.mediaItem = mediaItem;
    this.dataSourceFactory = dataSourceFactory;
    this.progressiveMediaExtractorFactory = progressiveMediaExtractorFactory;
    this.drmSessionManager = drmSessionManager;
    this.loadableLoadErrorHandlingPolicy = loadableLoadErrorHandlingPolicy;
    this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
    this.timelineIsPlaceholder = true;
    this.timelineDurationUs = C.TIME_UNSET;
  }

  @Override
  public MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    transferListener = mediaTransferListener;
    drmSessionManager.prepare();
    drmSessionManager.setPlayer(
        /* playbackLooper= */ checkNotNull(Looper.myLooper()), getPlayerId());
    notifySourceInfoRefreshed();
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    DataSource dataSource = dataSourceFactory.createDataSource();
    if (transferListener != null) {
      dataSource.addTransferListener(transferListener);
    }
    return new ProgressiveMediaPeriod(
        localConfiguration.uri,
        dataSource,
        progressiveMediaExtractorFactory.createProgressiveMediaExtractor(getPlayerId()),
        drmSessionManager,
        createDrmEventDispatcher(id),
        loadableLoadErrorHandlingPolicy,
        createEventDispatcher(id),
        this,
        allocator,
        localConfiguration.customCacheKey,
        continueLoadingCheckIntervalBytes);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((ProgressiveMediaPeriod) mediaPeriod).release();
  }

  @Override
  protected void releaseSourceInternal() {
    drmSessionManager.release();
  }

  // ProgressiveMediaPeriod.Listener implementation.

  @Override
  public void onSourceInfoRefreshed(long durationUs, boolean isSeekable, boolean isLive) {
    // If we already have the duration from a previous source info refresh, use it.
    durationUs = durationUs == C.TIME_UNSET ? timelineDurationUs : durationUs;
    if (!timelineIsPlaceholder
        && timelineDurationUs == durationUs
        && timelineIsSeekable == isSeekable
        && timelineIsLive == isLive) {
      // Suppress no-op source info changes.
      return;
    }
    timelineDurationUs = durationUs;
    timelineIsSeekable = isSeekable;
    timelineIsLive = isLive;
    timelineIsPlaceholder = false;
    notifySourceInfoRefreshed();
  }

  // Internal methods.

  private void notifySourceInfoRefreshed() {
    // TODO: Split up isDynamic into multiple fields to indicate which values may change. Then
    // indicate that the duration may change until it's known. See [internal: b/69703223].
    Timeline timeline =
        new SinglePeriodTimeline(
            timelineDurationUs,
            timelineIsSeekable,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ timelineIsLive,
            /* manifest= */ null,
            mediaItem);
    if (timelineIsPlaceholder) {
      // TODO: Actually prepare the extractors during preparation so that we don't need a
      // placeholder. See https://github.com/google/ExoPlayer/issues/4727.
      timeline =
          new ForwardingTimeline(timeline) {
            @Override
            public Window getWindow(
                int windowIndex, Window window, long defaultPositionProjectionUs) {
              super.getWindow(windowIndex, window, defaultPositionProjectionUs);
              window.isPlaceholder = true;
              return window;
            }

            @Override
            public Period getPeriod(int periodIndex, Period period, boolean setIds) {
              super.getPeriod(periodIndex, period, setIds);
              period.isPlaceholder = true;
              return period;
            }
          };
    }
    refreshSourceInfo(timeline);
  }
}
