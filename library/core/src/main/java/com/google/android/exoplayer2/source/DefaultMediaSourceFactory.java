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

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.content.Context;
import android.util.Pair;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.ui.AdViewProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
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
 * <p>To support media items with {@link MediaItem.PlaybackProperties#adsConfiguration ads
 * configuration}, {@link #setAdsLoaderProvider} and {@link #setAdViewProvider} need to be called to
 * configure the factory with the required providers.
 */
public final class DefaultMediaSourceFactory implements MediaSourceFactory {

  /**
   * Provides {@link AdsLoader} instances for media items that have {@link
   * MediaItem.PlaybackProperties#adsConfiguration ad tag URIs}.
   */
  public interface AdsLoaderProvider {

    /**
     * Returns an {@link AdsLoader} for the given {@link
     * MediaItem.PlaybackProperties#adsConfiguration ads configuration}, or {@code null} if no ads
     * loader is available for the given ads configuration.
     *
     * <p>This method is called each time a {@link MediaSource} is created from a {@link MediaItem}
     * that defines an {@link MediaItem.PlaybackProperties#adsConfiguration ads configuration}.
     */
    @Nullable
    AdsLoader getAdsLoader(MediaItem.AdsConfiguration adsConfiguration);
  }

  private static final String TAG = "DefaultMediaSourceFactory";

  private final DataSource.Factory dataSourceFactory;
  private final SparseArray<MediaSourceFactory> mediaSourceFactories;
  @C.ContentType private final int[] supportedTypes;

  @Nullable private AdsLoaderProvider adsLoaderProvider;
  @Nullable private AdViewProvider adViewProvider;
  @Nullable private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private long liveTargetOffsetMs;
  private long liveMinOffsetMs;
  private long liveMaxOffsetMs;
  private float liveMinSpeed;
  private float liveMaxSpeed;

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
    mediaSourceFactories = loadDelegates(dataSourceFactory, extractorsFactory);
    supportedTypes = new int[mediaSourceFactories.size()];
    for (int i = 0; i < mediaSourceFactories.size(); i++) {
      supportedTypes[i] = mediaSourceFactories.keyAt(i);
    }
    liveTargetOffsetMs = C.TIME_UNSET;
    liveMinOffsetMs = C.TIME_UNSET;
    liveMaxOffsetMs = C.TIME_UNSET;
    liveMinSpeed = C.RATE_UNSET;
    liveMaxSpeed = C.RATE_UNSET;
  }

  /**
   * Sets the {@link AdsLoaderProvider} that provides {@link AdsLoader} instances for media items
   * that have {@link MediaItem.PlaybackProperties#adsConfiguration ads configurations}.
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

  /**
   * Sets the target live offset for live streams, in milliseconds.
   *
   * @param liveTargetOffsetMs The target live offset, in milliseconds, or {@link C#TIME_UNSET} to
   *     use the media-defined default.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory setLiveTargetOffsetMs(long liveTargetOffsetMs) {
    this.liveTargetOffsetMs = liveTargetOffsetMs;
    return this;
  }

  /**
   * Sets the minimum offset from the live edge for live streams, in milliseconds.
   *
   * @param liveMinOffsetMs The minimum allowed live offset, in milliseconds, or {@link
   *     C#TIME_UNSET} to use the media-defined default.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory setLiveMinOffsetMs(long liveMinOffsetMs) {
    this.liveMinOffsetMs = liveMinOffsetMs;
    return this;
  }

  /**
   * Sets the maximum offset from the live edge for live streams, in milliseconds.
   *
   * @param liveMaxOffsetMs The maximum allowed live offset, in milliseconds, or {@link
   *     C#TIME_UNSET} to use the media-defined default.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory setLiveMaxOffsetMs(long liveMaxOffsetMs) {
    this.liveMaxOffsetMs = liveMaxOffsetMs;
    return this;
  }

  /**
   * Sets the minimum playback speed for live streams.
   *
   * @param minSpeed The minimum factor by which playback can be sped up for live streams, or {@link
   *     C#RATE_UNSET} to use the media-defined default.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory setLiveMinSpeed(float minSpeed) {
    this.liveMinSpeed = minSpeed;
    return this;
  }

  /**
   * Sets the maximum playback speed for live streams.
   *
   * @param maxSpeed The maximum factor by which playback can be sped up for live streams, or {@link
   *     C#RATE_UNSET} to use the media-defined default.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory setLiveMaxSpeed(float maxSpeed) {
    this.liveMaxSpeed = maxSpeed;
    return this;
  }

  @SuppressWarnings("deprecation") // Calling through to the same deprecated method.
  @Override
  public DefaultMediaSourceFactory setDrmHttpDataSourceFactory(
      @Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
    for (int i = 0; i < mediaSourceFactories.size(); i++) {
      mediaSourceFactories.valueAt(i).setDrmHttpDataSourceFactory(drmHttpDataSourceFactory);
    }
    return this;
  }

  @SuppressWarnings("deprecation") // Calling through to the same deprecated method.
  @Override
  public DefaultMediaSourceFactory setDrmUserAgent(@Nullable String userAgent) {
    for (int i = 0; i < mediaSourceFactories.size(); i++) {
      mediaSourceFactories.valueAt(i).setDrmUserAgent(userAgent);
    }
    return this;
  }

  @SuppressWarnings("deprecation") // Calling through to the same deprecated method.
  @Override
  public DefaultMediaSourceFactory setDrmSessionManager(
      @Nullable DrmSessionManager drmSessionManager) {
    for (int i = 0; i < mediaSourceFactories.size(); i++) {
      mediaSourceFactories.valueAt(i).setDrmSessionManager(drmSessionManager);
    }
    return this;
  }

  @Override
  public DefaultMediaSourceFactory setDrmSessionManagerProvider(
      @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
    for (int i = 0; i < mediaSourceFactories.size(); i++) {
      mediaSourceFactories.valueAt(i).setDrmSessionManagerProvider(drmSessionManagerProvider);
    }
    return this;
  }

  @Override
  public DefaultMediaSourceFactory setLoadErrorHandlingPolicy(
      @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    for (int i = 0; i < mediaSourceFactories.size(); i++) {
      mediaSourceFactories.valueAt(i).setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
    }
    return this;
  }

  /**
   * @deprecated Use {@link MediaItem.Builder#setStreamKeys(List)} and {@link
   *     #createMediaSource(MediaItem)} instead.
   */
  @SuppressWarnings("deprecation") // Calling through to the same deprecated method.
  @Deprecated
  @Override
  public DefaultMediaSourceFactory setStreamKeys(@Nullable List<StreamKey> streamKeys) {
    for (int i = 0; i < mediaSourceFactories.size(); i++) {
      mediaSourceFactories.valueAt(i).setStreamKeys(streamKeys);
    }
    return this;
  }

  @Override
  public int[] getSupportedTypes() {
    return Arrays.copyOf(supportedTypes, supportedTypes.length);
  }

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

    // Make sure to retain the very same media item instance, if no value needs to be overridden.
    if ((mediaItem.liveConfiguration.targetOffsetMs == C.TIME_UNSET
            && liveTargetOffsetMs != C.TIME_UNSET)
        || (mediaItem.liveConfiguration.minPlaybackSpeed == C.RATE_UNSET
            && liveMinSpeed != C.RATE_UNSET)
        || (mediaItem.liveConfiguration.maxPlaybackSpeed == C.RATE_UNSET
            && liveMaxSpeed != C.RATE_UNSET)
        || (mediaItem.liveConfiguration.minOffsetMs == C.TIME_UNSET
            && liveMinOffsetMs != C.TIME_UNSET)
        || (mediaItem.liveConfiguration.maxOffsetMs == C.TIME_UNSET
            && liveMaxOffsetMs != C.TIME_UNSET)) {
      mediaItem =
          mediaItem
              .buildUpon()
              .setLiveTargetOffsetMs(
                  mediaItem.liveConfiguration.targetOffsetMs == C.TIME_UNSET
                      ? liveTargetOffsetMs
                      : mediaItem.liveConfiguration.targetOffsetMs)
              .setLiveMinPlaybackSpeed(
                  mediaItem.liveConfiguration.minPlaybackSpeed == C.RATE_UNSET
                      ? liveMinSpeed
                      : mediaItem.liveConfiguration.minPlaybackSpeed)
              .setLiveMaxPlaybackSpeed(
                  mediaItem.liveConfiguration.maxPlaybackSpeed == C.RATE_UNSET
                      ? liveMaxSpeed
                      : mediaItem.liveConfiguration.maxPlaybackSpeed)
              .setLiveMinOffsetMs(
                  mediaItem.liveConfiguration.minOffsetMs == C.TIME_UNSET
                      ? liveMinOffsetMs
                      : mediaItem.liveConfiguration.minOffsetMs)
              .setLiveMaxOffsetMs(
                  mediaItem.liveConfiguration.maxOffsetMs == C.TIME_UNSET
                      ? liveMaxOffsetMs
                      : mediaItem.liveConfiguration.maxOffsetMs)
              .build();
    }
    MediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);

    List<MediaItem.Subtitle> subtitles = castNonNull(mediaItem.playbackProperties).subtitles;
    if (!subtitles.isEmpty()) {
      MediaSource[] mediaSources = new MediaSource[subtitles.size() + 1];
      mediaSources[0] = mediaSource;
      SingleSampleMediaSource.Factory singleSampleSourceFactory =
          new SingleSampleMediaSource.Factory(dataSourceFactory)
              .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
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
    @Nullable
    MediaItem.AdsConfiguration adsConfiguration = mediaItem.playbackProperties.adsConfiguration;
    if (adsConfiguration == null) {
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
    @Nullable AdsLoader adsLoader = adsLoaderProvider.getAdsLoader(adsConfiguration);
    if (adsLoader == null) {
      Log.w(TAG, "Playing media without ads, as no AdsLoader was provided.");
      return mediaSource;
    }
    return new AdsMediaSource(
        mediaSource,
        new DataSpec(adsConfiguration.adTagUri),
        /* adsId= */ adsConfiguration.adsId != null
            ? adsConfiguration.adsId
            : Pair.create(mediaItem.mediaId, adsConfiguration.adTagUri),
        /* adMediaSourceFactory= */ this,
        adsLoader,
        adViewProvider);
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
    try {
      Class<? extends MediaSourceFactory> factoryClazz =
          Class.forName("com.google.android.exoplayer2.source.rtsp.RtspMediaSource$Factory")
              .asSubclass(MediaSourceFactory.class);
      factories.put(C.TYPE_RTSP, factoryClazz.getConstructor().newInstance());
    } catch (Exception e) {
      // Expected if the app was built without the RTSP module.
    }
    // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    factories.put(
        C.TYPE_OTHER, new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory));
    return factories;
  }
}
