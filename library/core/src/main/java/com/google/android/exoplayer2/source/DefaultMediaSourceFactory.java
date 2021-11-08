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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.text.SubtitleDecoderFactory;
import com.google.android.exoplayer2.text.SubtitleExtractor;
import com.google.android.exoplayer2.ui.AdViewProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * The default {@link MediaSourceFactory} implementation.
 *
 * <p>This implementation delegates calls to {@link #createMediaSource(MediaItem)} to the following
 * factories:
 *
 * <ul>
 *   <li>{@code DashMediaSource.Factory} if the item's {@link MediaItem.LocalConfiguration#uri uri}
 *       ends in '.mpd' or if its {@link MediaItem.LocalConfiguration#mimeType mimeType field} is
 *       explicitly set to {@link MimeTypes#APPLICATION_MPD} (Requires the <a
 *       href="https://exoplayer.dev/hello-world.html#add-exoplayer-modules">exoplayer-dash module
 *       to be added</a> to the app).
 *   <li>{@code HlsMediaSource.Factory} if the item's {@link MediaItem.LocalConfiguration#uri uri}
 *       ends in '.m3u8' or if its {@link MediaItem.LocalConfiguration#mimeType mimeType field} is
 *       explicitly set to {@link MimeTypes#APPLICATION_M3U8} (Requires the <a
 *       href="https://exoplayer.dev/hello-world.html#add-exoplayer-modules">exoplayer-hls module to
 *       be added</a> to the app).
 *   <li>{@code SsMediaSource.Factory} if the item's {@link MediaItem.LocalConfiguration#uri uri}
 *       ends in '.ism', '.ism/Manifest' or if its {@link MediaItem.LocalConfiguration#mimeType
 *       mimeType field} is explicitly set to {@link MimeTypes#APPLICATION_SS} (Requires the <a
 *       href="https://exoplayer.dev/hello-world.html#add-exoplayer-modules">
 *       exoplayer-smoothstreaming module to be added</a> to the app).
 *   <li>{@link ProgressiveMediaSource.Factory} serves as a fallback if the item's {@link
 *       MediaItem.LocalConfiguration#uri uri} doesn't match one of the above. It tries to infer the
 *       required extractor by using the {@link DefaultExtractorsFactory} or the {@link
 *       ExtractorsFactory} provided in the constructor. An {@link UnrecognizedInputFormatException}
 *       is thrown if none of the available extractors can read the stream.
 * </ul>
 *
 * <h2>Ad support for media items with ad tag URIs</h2>
 *
 * <p>To support media items with {@link MediaItem.LocalConfiguration#adsConfiguration ads
 * configuration}, {@link #setAdsLoaderProvider} and {@link #setAdViewProvider} need to be called to
 * configure the factory with the required providers.
 */
public final class DefaultMediaSourceFactory implements MediaSourceFactory {

  /**
   * Provides {@link AdsLoader} instances for media items that have {@link
   * MediaItem.LocalConfiguration#adsConfiguration ad tag URIs}.
   */
  public interface AdsLoaderProvider {

    /**
     * Returns an {@link AdsLoader} for the given {@link
     * MediaItem.LocalConfiguration#adsConfiguration ads configuration}, or {@code null} if no ads
     * loader is available for the given ads configuration.
     *
     * <p>This method is called each time a {@link MediaSource} is created from a {@link MediaItem}
     * that defines an {@link MediaItem.LocalConfiguration#adsConfiguration ads configuration}.
     */
    @Nullable
    AdsLoader getAdsLoader(MediaItem.AdsConfiguration adsConfiguration);
  }

  private static final String TAG = "DefaultMediaSourceFactory";

  private final DataSource.Factory dataSourceFactory;
  private final DelegateFactoryLoader delegateFactoryLoader;

  @Nullable private final MediaSourceFactory serverSideDaiMediaSourceFactory;
  @Nullable private AdsLoaderProvider adsLoaderProvider;
  @Nullable private AdViewProvider adViewProvider;
  @Nullable private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private long liveTargetOffsetMs;
  private long liveMinOffsetMs;
  private long liveMaxOffsetMs;
  private float liveMinSpeed;
  private float liveMaxSpeed;
  private boolean useProgressiveMediaSourceForSubtitles;

  /**
   * Creates a new instance.
   *
   * @param context Any context.
   */
  public DefaultMediaSourceFactory(Context context) {
    this(new DefaultDataSource.Factory(context));
  }

  /**
   * Creates a new instance.
   *
   * @param context Any context.
   * @param extractorsFactory An {@link ExtractorsFactory} used to extract progressive media from
   *     its container.
   */
  public DefaultMediaSourceFactory(Context context, ExtractorsFactory extractorsFactory) {
    this(
        new DefaultDataSource.Factory(context),
        extractorsFactory,
        /* serverSideDaiMediaSourceFactory= */ null);
  }

  /**
   * Creates a new instance.
   *
   * @param dataSourceFactory A {@link DataSource.Factory} to create {@link DataSource} instances
   *     for requesting media data.
   */
  public DefaultMediaSourceFactory(DataSource.Factory dataSourceFactory) {
    this(
        dataSourceFactory,
        new DefaultExtractorsFactory(),
        /* serverSideDaiMediaSourceFactory= */ null);
  }

  /**
   * Creates a new instance.
   *
   * @param dataSourceFactory A {@link DataSource.Factory} to create {@link DataSource} instances
   *     for requesting media data.
   * @param extractorsFactory An {@link ExtractorsFactory} used to extract progressive media from
   *     its container.
   * @param serverSideDaiMediaSourceFactory A {@link MediaSourceFactory} for creating server side
   *     inserted ad media sources.
   */
  public DefaultMediaSourceFactory(
      DataSource.Factory dataSourceFactory,
      ExtractorsFactory extractorsFactory,
      @Nullable MediaSourceFactory serverSideDaiMediaSourceFactory) {
    this.dataSourceFactory = dataSourceFactory;
    // Temporary until factory registration is agreed upon.
    this.serverSideDaiMediaSourceFactory = serverSideDaiMediaSourceFactory;

    delegateFactoryLoader = new DelegateFactoryLoader(dataSourceFactory, extractorsFactory);
    liveTargetOffsetMs = C.TIME_UNSET;
    liveMinOffsetMs = C.TIME_UNSET;
    liveMaxOffsetMs = C.TIME_UNSET;
    liveMinSpeed = C.RATE_UNSET;
    liveMaxSpeed = C.RATE_UNSET;
  }

  /**
   * Sets whether a {@link ProgressiveMediaSource} or {@link SingleSampleMediaSource} is constructed
   * to handle {@link MediaItem.LocalConfiguration#subtitleConfigurations}. Defaults to false (i.e.
   * {@link SingleSampleMediaSource}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param useProgressiveMediaSourceForSubtitles Indicates that {@link ProgressiveMediaSource}
   *     should be used for subtitles instead of {@link SingleSampleMediaSource}.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory experimentalUseProgressiveMediaSourceForSubtitles(
      boolean useProgressiveMediaSourceForSubtitles) {
    this.useProgressiveMediaSourceForSubtitles = useProgressiveMediaSourceForSubtitles;
    return this;
  }

  /**
   * Sets the {@link AdsLoaderProvider} that provides {@link AdsLoader} instances for media items
   * that have {@link MediaItem.LocalConfiguration#adsConfiguration ads configurations}.
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

  @Deprecated
  @Override
  public DefaultMediaSourceFactory setDrmHttpDataSourceFactory(
      @Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
    delegateFactoryLoader.setDrmHttpDataSourceFactory(drmHttpDataSourceFactory);
    return this;
  }

  @Deprecated
  @Override
  public DefaultMediaSourceFactory setDrmUserAgent(@Nullable String userAgent) {
    delegateFactoryLoader.setDrmUserAgent(userAgent);
    return this;
  }

  @Deprecated
  @Override
  public DefaultMediaSourceFactory setDrmSessionManager(
      @Nullable DrmSessionManager drmSessionManager) {
    delegateFactoryLoader.setDrmSessionManager(drmSessionManager);
    return this;
  }

  @Override
  public DefaultMediaSourceFactory setDrmSessionManagerProvider(
      @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
    delegateFactoryLoader.setDrmSessionManagerProvider(drmSessionManagerProvider);
    return this;
  }

  @Override
  public DefaultMediaSourceFactory setLoadErrorHandlingPolicy(
      @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    delegateFactoryLoader.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
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
    delegateFactoryLoader.setStreamKeys(streamKeys);
    return this;
  }

  @Override
  public int[] getSupportedTypes() {
    return delegateFactoryLoader.getSupportedTypes();
  }

  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
    Assertions.checkNotNull(mediaItem.localConfiguration);
    @Nullable String scheme = mediaItem.localConfiguration.uri.getScheme();
    if (scheme != null && scheme.equals("imadai")) {
      return checkNotNull(serverSideDaiMediaSourceFactory).createMediaSource(mediaItem);
    }
    @C.ContentType
    int type =
        Util.inferContentTypeForUriAndMimeType(
            mediaItem.localConfiguration.uri, mediaItem.localConfiguration.mimeType);
    @Nullable
    MediaSourceFactory mediaSourceFactory = delegateFactoryLoader.getMediaSourceFactory(type);
    checkStateNotNull(
        mediaSourceFactory, "No suitable media source factory found for content type: " + type);

    MediaItem.LiveConfiguration.Builder liveConfigurationBuilder =
        mediaItem.liveConfiguration.buildUpon();
    if (mediaItem.liveConfiguration.targetOffsetMs == C.TIME_UNSET) {
      liveConfigurationBuilder.setTargetOffsetMs(liveTargetOffsetMs);
    }
    if (mediaItem.liveConfiguration.minPlaybackSpeed == C.RATE_UNSET) {
      liveConfigurationBuilder.setMinPlaybackSpeed(liveMinSpeed);
    }
    if (mediaItem.liveConfiguration.maxPlaybackSpeed == C.RATE_UNSET) {
      liveConfigurationBuilder.setMaxPlaybackSpeed(liveMaxSpeed);
    }
    if (mediaItem.liveConfiguration.minOffsetMs == C.TIME_UNSET) {
      liveConfigurationBuilder.setMinOffsetMs(liveMinOffsetMs);
    }
    if (mediaItem.liveConfiguration.maxOffsetMs == C.TIME_UNSET) {
      liveConfigurationBuilder.setMaxOffsetMs(liveMaxOffsetMs);
    }
    MediaItem.LiveConfiguration liveConfiguration = liveConfigurationBuilder.build();
    // Make sure to retain the very same media item instance, if no value needs to be overridden.
    if (!liveConfiguration.equals(mediaItem.liveConfiguration)) {
      mediaItem = mediaItem.buildUpon().setLiveConfiguration(liveConfiguration).build();
    }

    MediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);

    List<MediaItem.SubtitleConfiguration> subtitleConfigurations =
        castNonNull(mediaItem.localConfiguration).subtitleConfigurations;
    if (!subtitleConfigurations.isEmpty()) {
      MediaSource[] mediaSources = new MediaSource[subtitleConfigurations.size() + 1];
      mediaSources[0] = mediaSource;
      for (int i = 0; i < subtitleConfigurations.size(); i++) {
        if (useProgressiveMediaSourceForSubtitles) {
          Format format =
              new Format.Builder()
                  .setSampleMimeType(subtitleConfigurations.get(i).mimeType)
                  .setLanguage(subtitleConfigurations.get(i).language)
                  .setSelectionFlags(subtitleConfigurations.get(i).selectionFlags)
                  .setRoleFlags(subtitleConfigurations.get(i).roleFlags)
                  .setLabel(subtitleConfigurations.get(i).label)
                  .build();
          ExtractorsFactory extractorsFactory =
              () ->
                  new Extractor[] {
                    SubtitleDecoderFactory.DEFAULT.supportsFormat(format)
                        ? new SubtitleExtractor(
                            SubtitleDecoderFactory.DEFAULT.createDecoder(format), format)
                        : new UnknownSubtitlesExtractor(format)
                  };
          mediaSources[i + 1] =
              new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                  .createMediaSource(
                      MediaItem.fromUri(subtitleConfigurations.get(i).uri.toString()));
        } else {
          SingleSampleMediaSource.Factory singleSampleSourceFactory =
              new SingleSampleMediaSource.Factory(dataSourceFactory)
                  .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
          mediaSources[i + 1] =
              singleSampleSourceFactory.createMediaSource(
                  subtitleConfigurations.get(i), /* durationUs= */ C.TIME_UNSET);
        }
      }

      mediaSource = new MergingMediaSource(mediaSources);
    }
    return maybeWrapWithAdsMediaSource(mediaItem, maybeClipMediaSource(mediaItem, mediaSource));
  }

  // internal methods

  private static MediaSource maybeClipMediaSource(MediaItem mediaItem, MediaSource mediaSource) {
    if (mediaItem.clippingConfiguration.startPositionMs == 0
        && mediaItem.clippingConfiguration.endPositionMs == C.TIME_END_OF_SOURCE
        && !mediaItem.clippingConfiguration.relativeToDefaultPosition) {
      return mediaSource;
    }
    return new ClippingMediaSource(
        mediaSource,
        Util.msToUs(mediaItem.clippingConfiguration.startPositionMs),
        Util.msToUs(mediaItem.clippingConfiguration.endPositionMs),
        /* enableInitialDiscontinuity= */ !mediaItem.clippingConfiguration.startsAtKeyFrame,
        /* allowDynamicClippingUpdates= */ mediaItem.clippingConfiguration.relativeToLiveWindow,
        mediaItem.clippingConfiguration.relativeToDefaultPosition);
  }

  private MediaSource maybeWrapWithAdsMediaSource(MediaItem mediaItem, MediaSource mediaSource) {
    checkNotNull(mediaItem.localConfiguration);
    @Nullable
    MediaItem.AdsConfiguration adsConfiguration = mediaItem.localConfiguration.adsConfiguration;
    if (adsConfiguration == null) {
      return mediaSource;
    }
    @Nullable AdsLoaderProvider adsLoaderProvider = this.adsLoaderProvider;
    @Nullable AdViewProvider adViewProvider = this.adViewProvider;
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
            : ImmutableList.of(
                mediaItem.mediaId, mediaItem.localConfiguration.uri, adsConfiguration.adTagUri),
        /* adMediaSourceFactory= */ this,
        adsLoader,
        adViewProvider);
  }

  /** Loads media source factories lazily. */
  private static final class DelegateFactoryLoader {
    private final DataSource.Factory dataSourceFactory;
    private final ExtractorsFactory extractorsFactory;
    private final Map<Integer, @NullableType Supplier<MediaSourceFactory>>
        mediaSourceFactorySuppliers;
    private final Set<Integer> supportedTypes;
    private final Map<Integer, MediaSourceFactory> mediaSourceFactories;

    @Nullable private HttpDataSource.Factory drmHttpDataSourceFactory;
    @Nullable private String userAgent;
    @Nullable private DrmSessionManager drmSessionManager;
    @Nullable private DrmSessionManagerProvider drmSessionManagerProvider;
    @Nullable private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    @Nullable private List<StreamKey> streamKeys;

    public DelegateFactoryLoader(
        DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory) {
      this.dataSourceFactory = dataSourceFactory;
      this.extractorsFactory = extractorsFactory;
      mediaSourceFactorySuppliers = new HashMap<>();
      supportedTypes = new HashSet<>();
      mediaSourceFactories = new HashMap<>();
    }

    @C.ContentType
    public int[] getSupportedTypes() {
      ensureAllSuppliersAreLoaded();
      return Ints.toArray(supportedTypes);
    }

    @SuppressWarnings("deprecation") // Forwarding to deprecated methods.
    @Nullable
    public MediaSourceFactory getMediaSourceFactory(@C.ContentType int contentType) {
      @Nullable MediaSourceFactory mediaSourceFactory = mediaSourceFactories.get(contentType);
      if (mediaSourceFactory != null) {
        return mediaSourceFactory;
      }
      @Nullable
      Supplier<MediaSourceFactory> mediaSourceFactorySupplier = maybeLoadSupplier(contentType);
      if (mediaSourceFactorySupplier == null) {
        return null;
      }

      mediaSourceFactory = mediaSourceFactorySupplier.get();
      if (drmHttpDataSourceFactory != null) {
        mediaSourceFactory.setDrmHttpDataSourceFactory(drmHttpDataSourceFactory);
      }
      if (userAgent != null) {
        mediaSourceFactory.setDrmUserAgent(userAgent);
      }
      if (drmSessionManager != null) {
        mediaSourceFactory.setDrmSessionManager(drmSessionManager);
      }
      if (drmSessionManagerProvider != null) {
        mediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
      }
      if (loadErrorHandlingPolicy != null) {
        mediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
      }
      if (streamKeys != null) {
        mediaSourceFactory.setStreamKeys(streamKeys);
      }
      mediaSourceFactories.put(contentType, mediaSourceFactory);
      return mediaSourceFactory;
    }

    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void setDrmHttpDataSourceFactory(
        @Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
      this.drmHttpDataSourceFactory = drmHttpDataSourceFactory;
      for (MediaSourceFactory mediaSourceFactory : mediaSourceFactories.values()) {
        mediaSourceFactory.setDrmHttpDataSourceFactory(drmHttpDataSourceFactory);
      }
    }

    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void setDrmUserAgent(@Nullable String userAgent) {
      this.userAgent = userAgent;
      for (MediaSourceFactory mediaSourceFactory : mediaSourceFactories.values()) {
        mediaSourceFactory.setDrmUserAgent(userAgent);
      }
    }

    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
      this.drmSessionManager = drmSessionManager;
      for (MediaSourceFactory mediaSourceFactory : mediaSourceFactories.values()) {
        mediaSourceFactory.setDrmSessionManager(drmSessionManager);
      }
    }

    public void setDrmSessionManagerProvider(
        @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
      this.drmSessionManagerProvider = drmSessionManagerProvider;
      for (MediaSourceFactory mediaSourceFactory : mediaSourceFactories.values()) {
        mediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
      }
    }

    public void setLoadErrorHandlingPolicy(
        @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
      for (MediaSourceFactory mediaSourceFactory : mediaSourceFactories.values()) {
        mediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
      }
    }

    @SuppressWarnings("deprecation") // Forwarding to deprecated method.
    public void setStreamKeys(@Nullable List<StreamKey> streamKeys) {
      this.streamKeys = streamKeys;
      for (MediaSourceFactory mediaSourceFactory : mediaSourceFactories.values()) {
        mediaSourceFactory.setStreamKeys(streamKeys);
      }
    }

    private void ensureAllSuppliersAreLoaded() {
      maybeLoadSupplier(C.TYPE_DASH);
      maybeLoadSupplier(C.TYPE_SS);
      maybeLoadSupplier(C.TYPE_HLS);
      maybeLoadSupplier(C.TYPE_RTSP);
      maybeLoadSupplier(C.TYPE_OTHER);
    }

    @Nullable
    private Supplier<MediaSourceFactory> maybeLoadSupplier(@C.ContentType int contentType) {
      if (mediaSourceFactorySuppliers.containsKey(contentType)) {
        return mediaSourceFactorySuppliers.get(contentType);
      }

      @Nullable Supplier<MediaSourceFactory> mediaSourceFactorySupplier = null;
      try {
        Class<? extends MediaSourceFactory> clazz;
        switch (contentType) {
          case C.TYPE_DASH:
            clazz =
                Class.forName("com.google.android.exoplayer2.source.dash.DashMediaSource$Factory")
                    .asSubclass(MediaSourceFactory.class);
            mediaSourceFactorySupplier = () -> newInstance(clazz, dataSourceFactory);
            break;
          case C.TYPE_SS:
            clazz =
                Class.forName(
                        "com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource$Factory")
                    .asSubclass(MediaSourceFactory.class);
            mediaSourceFactorySupplier = () -> newInstance(clazz, dataSourceFactory);
            break;
          case C.TYPE_HLS:
            clazz =
                Class.forName("com.google.android.exoplayer2.source.hls.HlsMediaSource$Factory")
                    .asSubclass(MediaSourceFactory.class);
            mediaSourceFactorySupplier = () -> newInstance(clazz, dataSourceFactory);
            break;
          case C.TYPE_RTSP:
            clazz =
                Class.forName("com.google.android.exoplayer2.source.rtsp.RtspMediaSource$Factory")
                    .asSubclass(MediaSourceFactory.class);
            mediaSourceFactorySupplier = () -> newInstance(clazz);
            break;
          case C.TYPE_OTHER:
            mediaSourceFactorySupplier =
                () -> new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory);
            break;
          default:
            // Do nothing.
        }
      } catch (ClassNotFoundException e) {
        // Expected if the app was built without the specific module.
      }
      mediaSourceFactorySuppliers.put(contentType, mediaSourceFactorySupplier);
      if (mediaSourceFactorySupplier != null) {
        supportedTypes.add(contentType);
      }
      return mediaSourceFactorySupplier;
    }
  }

  private static final class UnknownSubtitlesExtractor implements Extractor {
    private final Format format;

    public UnknownSubtitlesExtractor(Format format) {
      this.format = format;
    }

    @Override
    public boolean sniff(ExtractorInput input) {
      return true;
    }

    @Override
    public void init(ExtractorOutput output) {
      TrackOutput trackOutput = output.track(/* id= */ 0, C.TRACK_TYPE_TEXT);
      output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
      output.endTracks();
      trackOutput.format(
          format
              .buildUpon()
              .setSampleMimeType(MimeTypes.TEXT_UNKNOWN)
              .setCodecs(format.sampleMimeType)
              .build());
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
      int skipResult = input.skip(Integer.MAX_VALUE);
      if (skipResult == C.RESULT_END_OF_INPUT) {
        return RESULT_END_OF_INPUT;
      }
      return RESULT_CONTINUE;
    }

    @Override
    public void seek(long position, long timeUs) {}

    @Override
    public void release() {}
  }

  private static MediaSourceFactory newInstance(
      Class<? extends MediaSourceFactory> clazz, DataSource.Factory dataSourceFactory) {
    try {
      return clazz.getConstructor(DataSource.Factory.class).newInstance(dataSourceFactory);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static MediaSourceFactory newInstance(Class<? extends MediaSourceFactory> clazz) {
    try {
      return clazz.getConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
