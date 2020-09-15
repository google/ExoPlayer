/*
 * Copyright 2020 The Android Open Source Project
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

import android.content.Context;
import android.net.Uri;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsLoader.AdViewProvider;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.List;

/**
 * The default {@link MediaSourceFactory} implementation.
 *
 * <p>This implementation delegates calls to {@link #createMediaSource(MediaItem)} to the following
 * factories:
 *
 * <ul>
 *   <li>{@code DashMediaSource.Factory} if the item's {@link MediaItem.PlaybackProperties#uri uri}
 *       ends in '.mpd' or if its {@link MediaItem.PlaybackProperties#mimeType mimeType field} is
 *       explicitly set to {@link MimeTypes#APPLICATION_MPD} (Requires the <a
 *       href="https://exoplayer.dev/hello-world.html#add-exoplayer-modules">exoplayer-dash module
 *       to be added</a> to the app).
 *   <li>{@code HlsMediaSource.Factory} if the item's {@link MediaItem.PlaybackProperties#uri uri}
 *       ends in '.m3u8' or if its {@link MediaItem.PlaybackProperties#mimeType mimeType field} is
 *       explicitly set to {@link MimeTypes#APPLICATION_M3U8} (Requires the <a
 *       href="https://exoplayer.dev/hello-world.html#add-exoplayer-modules">exoplayer-hls module to
 *       be added</a> to the app).
 *   <li>{@code SsMediaSource.Factory} if the item's {@link MediaItem.PlaybackProperties#uri uri}
 *       ends in '.ism', '.ism/Manifest' or if its {@link MediaItem.PlaybackProperties#mimeType
 *       mimeType field} is explicitly set to {@link MimeTypes#APPLICATION_SS} (Requires the <a
 *       href="https://exoplayer.dev/hello-world.html#add-exoplayer-modules">
 *       exoplayer-smoothstreaming module to be added</a> to the app).
 *   <li>{@link ProgressiveMediaSource.Factory} serves as a fallback if the item's {@link
 *       MediaItem.PlaybackProperties#uri uri} doesn't match one of the above. It tries to infer the
 *       required extractor by using the {@link
 *       com.google.android.exoplayer2.extractor.DefaultExtractorsFactory} or the {@link
 *       ExtractorsFactory} provided in the constructor. An {@link UnrecognizedInputFormatException}
 *       is thrown if none of the available extractors can read the stream.
 * </ul>
 *
 * <h3>Ad support for media items with ad tag URIs</h3>
 *
 * <p>To support media items with {@link MediaItem.PlaybackProperties#adTagUri ad tag URIs}, {@link
 * #setAdsLoaderProvider} and {@link #setAdViewProvider} need to be called to configure the factory
 * with the required providers.
 */
public final class DefaultMediaSourceFactory implements MediaSourceFactory {

  /**
   * Provides {@link AdsLoader} instances for media items that have {@link
   * MediaItem.PlaybackProperties#adTagUri ad tag URIs}.
   */
  public interface AdsLoaderProvider {

    /**
     * Returns an {@link AdsLoader} for the given {@link MediaItem.PlaybackProperties#adTagUri ad
     * tag URI}, or null if no ads loader is available for the given ad tag URI.
     *
     * <p>This method is called each time a {@link MediaSource} is created from a {@link MediaItem}
     * that defines an {@link MediaItem.PlaybackProperties#adTagUri ad tag URI}.
     */
    @Nullable
    AdsLoader getAdsLoader(Uri adTagUri);
  }

  private static final String TAG = "DefaultMediaSourceFactory";

  private final MediaSourceDrmHelper mediaSourceDrmHelper;
  private final DataSource.Factory dataSourceFactory;
  private final SparseArray<MediaSourceFactory> mediaSourceFactories;
  @C.ContentType private final int[] supportedTypes;

  @Nullable private AdsLoaderProvider adsLoaderProvider;
  @Nullable private AdViewProvider adViewProvider;
  @Nullable private DrmSessionManager drmSessionManager;
  @Nullable private List<StreamKey> streamKeys;
  @Nullable private LoadErrorHandlingPolicy loadErrorHandlingPolicy;

  /**
   * Creates a new instance.
   *
   * @param context Any context.
   */
  public DefaultMediaSourceFactory(Context context) {
    this(new DefaultDataSourceFactory(context));
  }

  /**
   * Creates a new instance.
   *
   * @param context Any context.
   * @param extractorsFactory An {@link ExtractorsFactory} used to extract progressive media from
   *     its container.
   */
  public DefaultMediaSourceFactory(Context context, ExtractorsFactory extractorsFactory) {
    this(new DefaultDataSourceFactory(context), extractorsFactory);
  }

  /**
   * Creates a new instance.
   *
   * @param dataSourceFactory A {@link DataSource.Factory} to create {@link DataSource} instances
   *     for requesting media data.
   */
  public DefaultMediaSourceFactory(DataSource.Factory dataSourceFactory) {
    this(dataSourceFactory, new DefaultExtractorsFactory());
  }

  /**
   * Creates a new instance.
   *
   * @param dataSourceFactory A {@link DataSource.Factory} to create {@link DataSource} instances
   *     for requesting media data.
   * @param extractorsFactory An {@link ExtractorsFactory} used to extract progressive media from
   *     its container.
   */
  public DefaultMediaSourceFactory(
      DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory) {
    this.dataSourceFactory = dataSourceFactory;
    mediaSourceDrmHelper = new MediaSourceDrmHelper();
    mediaSourceFactories = loadDelegates(dataSourceFactory, extractorsFactory);
    supportedTypes = new int[mediaSourceFactories.size()];
    for (int i = 0; i < mediaSourceFactories.size(); i++) {
      supportedTypes[i] = mediaSourceFactories.keyAt(i);
    }
  }

  /**
   * Sets the {@link AdsLoaderProvider} that provides {@link AdsLoader} instances for media items
   * that have {@link MediaItem.PlaybackProperties#adTagUri ad tag URIs}.
   *
   * @param adsLoaderProvider A provider for {@link AdsLoader} instances.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory setAdsLoaderProvider(
      @Nullable AdsLoaderProvider adsLoaderProvider) {
    this.adsLoaderProvider = adsLoaderProvider;
    return this;
  }

  /**
   * Sets the {@link AdViewProvider} that provides information about views for the ad playback UI.
   *
   * @param adViewProvider A provider for {@link AdsLoader} instances.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory setAdViewProvider(@Nullable AdViewProvider adViewProvider) {
    this.adViewProvider = adViewProvider;
    return this;
  }

  @Override
  public DefaultMediaSourceFactory setDrmHttpDataSourceFactory(
      @Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
    mediaSourceDrmHelper.setDrmHttpDataSourceFactory(drmHttpDataSourceFactory);
    return this;
  }

  @Override
  public DefaultMediaSourceFactory setDrmUserAgent(@Nullable String userAgent) {
    mediaSourceDrmHelper.setDrmUserAgent(userAgent);
    return this;
  }

  @Override
  public DefaultMediaSourceFactory setDrmSessionManager(
      @Nullable DrmSessionManager drmSessionManager) {
    this.drmSessionManager = drmSessionManager;
    return this;
  }

  @Override
  public DefaultMediaSourceFactory setLoadErrorHandlingPolicy(
      @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    return this;
  }

  /**
   * @deprecated Use {@link MediaItem.Builder#setStreamKeys(List)} and {@link
   *     #createMediaSource(MediaItem)} instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public DefaultMediaSourceFactory setStreamKeys(@Nullable List<StreamKey> streamKeys) {
    this.streamKeys = streamKeys != null && !streamKeys.isEmpty() ? streamKeys : null;
    return this;
  }

  @Override
  public int[] getSupportedTypes() {
    return Arrays.copyOf(supportedTypes, supportedTypes.length);
  }

  @SuppressWarnings("deprecation")
  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
    Assertions.checkNotNull(mediaItem.playbackProperties);
    @C.ContentType
    int type =
        Util.inferContentTypeForUriAndMimeType(
            mediaItem.playbackProperties.uri, mediaItem.playbackProperties.mimeType);
    @Nullable MediaSourceFactory mediaSourceFactory = mediaSourceFactories.get(type);
    Assertions.checkNotNull(
        mediaSourceFactory, "No suitable media source factory found for content type: " + type);
    mediaSourceFactory.setDrmSessionManager(
        drmSessionManager != null ? drmSessionManager : mediaSourceDrmHelper.create(mediaItem));
    mediaSourceFactory.setStreamKeys(
        !mediaItem.playbackProperties.streamKeys.isEmpty()
            ? mediaItem.playbackProperties.streamKeys
            : streamKeys);
    mediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);

    MediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);

    List<MediaItem.Subtitle> subtitles = mediaItem.playbackProperties.subtitles;
    if (!subtitles.isEmpty()) {
      MediaSource[] mediaSources = new MediaSource[subtitles.size() + 1];
      mediaSources[0] = mediaSource;
      SingleSampleMediaSource.Factory singleSampleSourceFactory =
          new SingleSampleMediaSource.Factory(dataSourceFactory);
      for (int i = 0; i < subtitles.size(); i++) {
        mediaSources[i + 1] =
            singleSampleSourceFactory.createMediaSource(
                subtitles.get(i), /* durationUs= */ C.TIME_UNSET);
      }
      mediaSource = new MergingMediaSource(mediaSources);
    }
    return maybeWrapWithAdsMediaSource(mediaItem, maybeClipMediaSource(mediaItem, mediaSource));
  }

  // internal methods

  private static MediaSource maybeClipMediaSource(MediaItem mediaItem, MediaSource mediaSource) {
    if (mediaItem.clippingProperties.startPositionMs == 0
        && mediaItem.clippingProperties.endPositionMs == C.TIME_END_OF_SOURCE
        && !mediaItem.clippingProperties.relativeToDefaultPosition) {
      return mediaSource;
    }
    return new ClippingMediaSource(
        mediaSource,
        C.msToUs(mediaItem.clippingProperties.startPositionMs),
        C.msToUs(mediaItem.clippingProperties.endPositionMs),
        /* enableInitialDiscontinuity= */ !mediaItem.clippingProperties.startsAtKeyFrame,
        /* allowDynamicClippingUpdates= */ mediaItem.clippingProperties.relativeToLiveWindow,
        mediaItem.clippingProperties.relativeToDefaultPosition);
  }

  private MediaSource maybeWrapWithAdsMediaSource(MediaItem mediaItem, MediaSource mediaSource) {
    Assertions.checkNotNull(mediaItem.playbackProperties);
    if (mediaItem.playbackProperties.adTagUri == null) {
      return mediaSource;
    }
    AdsLoaderProvider adsLoaderProvider = this.adsLoaderProvider;
    AdViewProvider adViewProvider = this.adViewProvider;
    if (adsLoaderProvider == null || adViewProvider == null) {
      Log.w(
          TAG,
          "Playing media without ads. Configure ad support by calling setAdsLoaderProvider and"
              + " setAdViewProvider.");
      return mediaSource;
    }
    @Nullable
    AdsLoader adsLoader = adsLoaderProvider.getAdsLoader(mediaItem.playbackProperties.adTagUri);
    if (adsLoader == null) {
      Log.w(TAG, "Playing media without ads. No AdsLoader for provided adTagUri");
      return mediaSource;
    }
    return new AdsMediaSource(
        mediaSource, /* adMediaSourceFactory= */ this, adsLoader, adViewProvider);
  }

  private static SparseArray<MediaSourceFactory> loadDelegates(
      DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory) {
    SparseArray<MediaSourceFactory> factories = new SparseArray<>();
    // LINT.IfChange
    try {
      Class<? extends MediaSourceFactory> factoryClazz =
          Class.forName("com.google.android.exoplayer2.source.dash.DashMediaSource$Factory")
              .asSubclass(MediaSourceFactory.class);
      factories.put(
          C.TYPE_DASH,
          factoryClazz.getConstructor(DataSource.Factory.class).newInstance(dataSourceFactory));
    } catch (Exception e) {
      // Expected if the app was built without the dash module.
    }
    try {
      Class<? extends MediaSourceFactory> factoryClazz =
          Class.forName(
                  "com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource$Factory")
              .asSubclass(MediaSourceFactory.class);
      factories.put(
          C.TYPE_SS,
          factoryClazz.getConstructor(DataSource.Factory.class).newInstance(dataSourceFactory));
    } catch (Exception e) {
      // Expected if the app was built without the smoothstreaming module.
    }
    try {
      Class<? extends MediaSourceFactory> factoryClazz =
          Class.forName("com.google.android.exoplayer2.source.hls.HlsMediaSource$Factory")
              .asSubclass(MediaSourceFactory.class);
      factories.put(
          C.TYPE_HLS,
          factoryClazz.getConstructor(DataSource.Factory.class).newInstance(dataSourceFactory));
    } catch (Exception e) {
      // Expected if the app was built without the hls module.
    }
    // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    factories.put(
        C.TYPE_OTHER, new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory));
    return factories;
  }
}
