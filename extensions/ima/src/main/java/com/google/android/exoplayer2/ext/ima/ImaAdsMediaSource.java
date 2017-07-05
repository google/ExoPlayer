/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.ima;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link MediaSource} that inserts ads linearly with a provided content media source using the
 * Interactive Media Ads SDK for ad loading and tracking.
 */
public final class ImaAdsMediaSource implements MediaSource {

  private final MediaSource contentMediaSource;
  private final DataSource.Factory dataSourceFactory;
  private final Context context;
  private final Uri adTagUri;
  private final ViewGroup adUiViewGroup;
  private final ImaSdkSettings imaSdkSettings;
  private final Handler mainHandler;
  private final AdListener adLoaderListener;
  private final Map<MediaPeriod, MediaSource> adMediaSourceByMediaPeriod;
  private final Timeline.Period period;

  private Handler playerHandler;
  private ExoPlayer player;
  private volatile boolean released;

  // Accessed on the player thread.
  private Timeline contentTimeline;
  private Object contentManifest;
  private long[] adGroupTimesUs;
  private boolean[] hasPlayedAdGroup;
  private int[] adCounts;
  private MediaSource[][] adGroupMediaSources;
  private boolean[][] isAdAvailable;
  private long[][] adDurationsUs;
  private MediaSource.Listener listener;
  private IOException adLoadError;

  // Accessed on the main thread.
  private ImaAdsLoader imaAdsLoader;

  /**
   * Constructs a new source that inserts ads linearly with the content specified by
   * {@code contentMediaSource}.
   *
   * @param contentMediaSource The {@link MediaSource} providing the content to play.
   * @param dataSourceFactory Factory for data sources used to load ad media.
   * @param context The context.
   * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
   *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
   *     more information.
   * @param adUiViewGroup A {@link ViewGroup} on top of the player that will show any ad user
   *     interface.
   */
  public ImaAdsMediaSource(MediaSource contentMediaSource, DataSource.Factory dataSourceFactory,
      Context context, Uri adTagUri, ViewGroup adUiViewGroup) {
    this(contentMediaSource, dataSourceFactory, context, adTagUri, adUiViewGroup, null);
  }

  /**
   * Constructs a new source that inserts ads linearly with the content specified by
   * {@code contentMediaSource}.
   *
   * @param contentMediaSource The {@link MediaSource} providing the content to play.
   * @param dataSourceFactory Factory for data sources used to load ad media.
   * @param context The context.
   * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
   *     https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
   *     more information.
   * @param adUiViewGroup A {@link ViewGroup} on top of the player that will show any ad UI.
   * @param imaSdkSettings {@link ImaSdkSettings} used to configure the IMA SDK, or {@code null} to
   *     use the default settings. If set, the player type and version fields may be overwritten.
   */
  public ImaAdsMediaSource(MediaSource contentMediaSource, DataSource.Factory dataSourceFactory,
      Context context, Uri adTagUri, ViewGroup adUiViewGroup, ImaSdkSettings imaSdkSettings) {
    this.contentMediaSource = contentMediaSource;
    this.dataSourceFactory = dataSourceFactory;
    this.context = context;
    this.adTagUri = adTagUri;
    this.adUiViewGroup = adUiViewGroup;
    this.imaSdkSettings = imaSdkSettings;
    mainHandler = new Handler(Looper.getMainLooper());
    adLoaderListener = new AdListener();
    adMediaSourceByMediaPeriod = new HashMap<>();
    period = new Timeline.Period();
    adGroupMediaSources = new MediaSource[0][];
    isAdAvailable = new boolean[0][];
    adDurationsUs = new long[0][];
  }

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    Assertions.checkArgument(isTopLevelSource);
    this.listener = listener;
    this.player = player;
    playerHandler = new Handler();
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        imaAdsLoader = new ImaAdsLoader(context, adTagUri, adUiViewGroup, imaSdkSettings,
            ImaAdsMediaSource.this.player, adLoaderListener);
      }
    });
    contentMediaSource.prepareSource(player, false, new Listener() {
      @Override
      public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
        ImaAdsMediaSource.this.onContentSourceInfoRefreshed(timeline, manifest);
      }
    });
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    if (adLoadError != null) {
      throw adLoadError;
    }
    contentMediaSource.maybeThrowSourceInfoRefreshError();
    for (MediaSource[] mediaSources : adGroupMediaSources) {
      for (MediaSource mediaSource : mediaSources) {
        mediaSource.maybeThrowSourceInfoRefreshError();
      }
    }
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    if (id.isAd()) {
      MediaSource mediaSource = adGroupMediaSources[id.adGroupIndex][id.adIndexInAdGroup];
      MediaPeriod mediaPeriod = mediaSource.createPeriod(new MediaPeriodId(0), allocator);
      adMediaSourceByMediaPeriod.put(mediaPeriod, mediaSource);
      return mediaPeriod;
    } else {
      return contentMediaSource.createPeriod(id, allocator);
    }
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    if (adMediaSourceByMediaPeriod.containsKey(mediaPeriod)) {
      adMediaSourceByMediaPeriod.remove(mediaPeriod).releasePeriod(mediaPeriod);
    } else {
      contentMediaSource.releasePeriod(mediaPeriod);
    }
  }

  @Override
  public void releaseSource() {
    released = true;
    adLoadError = null;
    contentMediaSource.releaseSource();
    for (MediaSource[] mediaSources : adGroupMediaSources) {
      for (MediaSource mediaSource : mediaSources) {
        mediaSource.releaseSource();
      }
    }
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        // TODO: The source will be released when the application is paused/stopped, which can occur
        // if the user taps on the ad. In this case, we should keep the ads manager alive but pause
        // it, instead of destroying it.
        imaAdsLoader.release();
        imaAdsLoader = null;
      }
    });
  }

  // Internal methods.

  private void onAdGroupTimesUsLoaded(long[] adGroupTimesUs) {
    Assertions.checkState(this.adGroupTimesUs == null);
    int adGroupCount = adGroupTimesUs.length;
    this.adGroupTimesUs = adGroupTimesUs;
    hasPlayedAdGroup = new boolean[adGroupCount];
    adCounts = new int[adGroupCount];
    Arrays.fill(adCounts, C.LENGTH_UNSET);
    adGroupMediaSources = new MediaSource[adGroupCount][];
    Arrays.fill(adGroupMediaSources, new MediaSource[0]);
    isAdAvailable = new boolean[adGroupCount][];
    Arrays.fill(isAdAvailable, new boolean[0]);
    adDurationsUs = new long[adGroupCount][];
    Arrays.fill(adDurationsUs, new long[0]);
    maybeUpdateSourceInfo();
  }

  private void onContentSourceInfoRefreshed(Timeline timeline, Object manifest) {
    contentTimeline = timeline;
    contentManifest = manifest;
    maybeUpdateSourceInfo();
  }

  private void onAdGroupPlayedToEnd(int adGroupIndex) {
    hasPlayedAdGroup[adGroupIndex] = true;
    maybeUpdateSourceInfo();
  }

  private void onAdUriLoaded(final int adGroupIndex, final int adIndexInAdGroup, Uri uri) {
    MediaSource adMediaSource = new ExtractorMediaSource(uri, dataSourceFactory,
        new DefaultExtractorsFactory(), mainHandler, adLoaderListener);
    int oldAdCount = adGroupMediaSources[adGroupIndex].length;
    if (adIndexInAdGroup >= oldAdCount) {
      int adCount = adIndexInAdGroup + 1;
      adGroupMediaSources[adGroupIndex] = Arrays.copyOf(adGroupMediaSources[adGroupIndex], adCount);
      isAdAvailable[adGroupIndex] = Arrays.copyOf(isAdAvailable[adGroupIndex], adCount);
      adDurationsUs[adGroupIndex] = Arrays.copyOf(adDurationsUs[adGroupIndex], adCount);
      Arrays.fill(adDurationsUs[adGroupIndex], oldAdCount, adCount, C.TIME_UNSET);
    }
    adGroupMediaSources[adGroupIndex][adIndexInAdGroup] = adMediaSource;
    isAdAvailable[adGroupIndex][adIndexInAdGroup] = true;
    adMediaSource.prepareSource(player, false, new Listener() {
      @Override
      public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
        onAdSourceInfoRefreshed(adGroupIndex, adIndexInAdGroup, timeline);
      }
    });
  }

  private void onAdSourceInfoRefreshed(int adGroupIndex, int adIndexInAdGroup, Timeline timeline) {
    Assertions.checkArgument(timeline.getPeriodCount() == 1);
    adDurationsUs[adGroupIndex][adIndexInAdGroup] = timeline.getPeriod(0, period).getDurationUs();
    maybeUpdateSourceInfo();
  }

  private void onAdGroupLoaded(int adGroupIndex, int adCountInAdGroup) {
    if (adCounts[adGroupIndex] == C.LENGTH_UNSET) {
      adCounts[adGroupIndex] = adCountInAdGroup;
      maybeUpdateSourceInfo();
    }
  }

  private void maybeUpdateSourceInfo() {
    if (adGroupTimesUs != null && contentTimeline != null) {
      SinglePeriodAdTimeline timeline = new SinglePeriodAdTimeline(contentTimeline, adGroupTimesUs,
          hasPlayedAdGroup, adCounts, isAdAvailable, adDurationsUs);
      listener.onSourceInfoRefreshed(timeline, contentManifest);
    }
  }

  /**
   * Listener for ad loading events. All methods are called on the main thread.
   */
  private final class AdListener implements ImaAdsLoader.EventListener,
      ExtractorMediaSource.EventListener {

    @Override
    public void onAdGroupTimesUsLoaded(final long[] adGroupTimesUs) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          ImaAdsMediaSource.this.onAdGroupTimesUsLoaded(adGroupTimesUs);
        }
      });
    }

    @Override
    public void onAdGroupPlayedToEnd(final int adGroupIndex) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          ImaAdsMediaSource.this.onAdGroupPlayedToEnd(adGroupIndex);
        }
      });
    }

    @Override
    public void onAdUriLoaded(final int adGroupIndex, final int adIndexInAdGroup, final Uri uri) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          ImaAdsMediaSource.this.onAdUriLoaded(adGroupIndex, adIndexInAdGroup, uri);
        }
      });
    }

    @Override
    public void onAdGroupLoaded(final int adGroupIndex, final int adCountInAdGroup) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          ImaAdsMediaSource.this.onAdGroupLoaded(adGroupIndex, adCountInAdGroup);
        }
      });
    }

    @Override
    public void onLoadError(final IOException error) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          adLoadError = error;
        }
      });
    }

  }

}
