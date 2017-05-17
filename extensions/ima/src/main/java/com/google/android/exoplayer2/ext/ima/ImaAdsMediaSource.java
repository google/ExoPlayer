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
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ClippingMediaPeriod;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
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
  private final Map<MediaPeriod, MediaSource> mediaSourceByMediaPeriod;

  private Handler playerHandler;
  private ExoPlayer player;
  private volatile boolean released;

  // Accessed on the player thread.
  private Timeline contentTimeline;
  private Object contentManifest;
  private long[] adBreakTimesUs;
  private boolean[] playedAdBreak;
  private Ad[][] adBreakAds;
  private Timeline[][] adBreakTimelines;
  private MediaSource[][] adBreakMediaSources;
  private DeferredMediaPeriod[][] adBreakDeferredMediaPeriods;
  private AdTimeline timeline;
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
    mediaSourceByMediaPeriod = new HashMap<>();
    adBreakMediaSources = new MediaSource[0][];
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
    for (MediaSource[] mediaSources : adBreakMediaSources) {
      for (MediaSource mediaSource : mediaSources) {
        mediaSource.maybeThrowSourceInfoRefreshError();
      }
    }
  }

  @Override
  public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
    if (timeline.isPeriodAd(index)) {
      int adBreakIndex = timeline.getAdBreakIndex(index);
      int adIndexInAdBreak = timeline.getAdIndexInAdBreak(index);
      if (adIndexInAdBreak >= adBreakMediaSources[adBreakIndex].length) {
        DeferredMediaPeriod deferredPeriod = new DeferredMediaPeriod(0, allocator, positionUs);
        if (adIndexInAdBreak >= adBreakDeferredMediaPeriods[adBreakIndex].length) {
          adBreakDeferredMediaPeriods[adBreakIndex] = Arrays.copyOf(
              adBreakDeferredMediaPeriods[adBreakIndex], adIndexInAdBreak + 1);
        }
        adBreakDeferredMediaPeriods[adBreakIndex][adIndexInAdBreak] = deferredPeriod;
        return deferredPeriod;
      }

      MediaSource adBreakMediaSource = adBreakMediaSources[adBreakIndex][adIndexInAdBreak];
      MediaPeriod adBreakMediaPeriod = adBreakMediaSource.createPeriod(0, allocator, positionUs);
      mediaSourceByMediaPeriod.put(adBreakMediaPeriod, adBreakMediaSource);
      return adBreakMediaPeriod;
    } else {
      long startUs = timeline.getContentStartTimeUs(index);
      long endUs = timeline.getContentEndTimeUs(index);
      long contentStartUs = startUs + positionUs;
      MediaPeriod contentMediaPeriod = contentMediaSource.createPeriod(0, allocator,
          contentStartUs);
      ClippingMediaPeriod clippingPeriod = new ClippingMediaPeriod(contentMediaPeriod);
      clippingPeriod.setClipping(startUs, endUs == C.TIME_UNSET ? C.TIME_END_OF_SOURCE : endUs);
      mediaSourceByMediaPeriod.put(contentMediaPeriod, contentMediaSource);
      return clippingPeriod;
    }
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    if (mediaPeriod instanceof DeferredMediaPeriod) {
      mediaPeriod = ((DeferredMediaPeriod) mediaPeriod).mediaPeriod;
      if (mediaPeriod == null) {
        // Nothing to do.
        return;
      }
    } else if (mediaPeriod instanceof ClippingMediaPeriod) {
      mediaPeriod = ((ClippingMediaPeriod) mediaPeriod).mediaPeriod;
    }
    mediaSourceByMediaPeriod.remove(mediaPeriod).releasePeriod(mediaPeriod);
  }

  @Override
  public void releaseSource() {
    released = true;
    adLoadError = null;
    contentMediaSource.releaseSource();
    for (MediaSource[] mediaSources : adBreakMediaSources) {
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

  private void onAdBreakTimesUsLoaded(long[] adBreakTimesUs) {
    Assertions.checkState(this.adBreakTimesUs == null);
    this.adBreakTimesUs = adBreakTimesUs;
    int adBreakCount = adBreakTimesUs.length;
    adBreakAds = new Ad[adBreakCount][];
    Arrays.fill(adBreakAds, new Ad[0]);
    adBreakTimelines = new Timeline[adBreakCount][];
    Arrays.fill(adBreakTimelines, new Timeline[0]);
    adBreakMediaSources = new MediaSource[adBreakCount][];
    Arrays.fill(adBreakMediaSources, new MediaSource[0]);
    adBreakDeferredMediaPeriods = new DeferredMediaPeriod[adBreakCount][];
    Arrays.fill(adBreakDeferredMediaPeriods, new DeferredMediaPeriod[0]);
    playedAdBreak = new boolean[adBreakCount];
    maybeUpdateSourceInfo();
  }

  private void onContentSourceInfoRefreshed(Timeline timeline, Object manifest) {
    contentTimeline = timeline;
    contentManifest = manifest;
    maybeUpdateSourceInfo();
  }

  private void onAdUriLoaded(final int adBreakIndex, final int adIndexInAdBreak, Uri uri) {
    MediaSource adMediaSource = new ExtractorMediaSource(uri, dataSourceFactory,
        new DefaultExtractorsFactory(), mainHandler, adLoaderListener);
    if (adBreakMediaSources[adBreakIndex].length <= adIndexInAdBreak) {
      int adCount = adIndexInAdBreak + 1;
      adBreakMediaSources[adBreakIndex] = Arrays.copyOf(adBreakMediaSources[adBreakIndex], adCount);
      adBreakTimelines[adBreakIndex] = Arrays.copyOf(adBreakTimelines[adBreakIndex], adCount);
    }
    adBreakMediaSources[adBreakIndex][adIndexInAdBreak] = adMediaSource;
    if (adIndexInAdBreak < adBreakDeferredMediaPeriods[adBreakIndex].length
        && adBreakDeferredMediaPeriods[adBreakIndex][adIndexInAdBreak] != null) {
      adBreakDeferredMediaPeriods[adBreakIndex][adIndexInAdBreak].setMediaSource(
          adBreakMediaSources[adBreakIndex][adIndexInAdBreak]);
      mediaSourceByMediaPeriod.put(
          adBreakDeferredMediaPeriods[adBreakIndex][adIndexInAdBreak].mediaPeriod, adMediaSource);
    }
    adMediaSource.prepareSource(player, false, new Listener() {
      @Override
      public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
        onAdSourceInfoRefreshed(adBreakIndex, adIndexInAdBreak, timeline);
      }
    });
  }

  private void onAdSourceInfoRefreshed(int adBreakIndex, int adIndexInAdBreak, Timeline timeline) {
    adBreakTimelines[adBreakIndex][adIndexInAdBreak] = timeline;
    maybeUpdateSourceInfo();
  }

  private void onAdLoaded(int adBreakIndex, int adIndexInAdBreak, Ad ad) {
    if (adBreakAds[adBreakIndex].length <= adIndexInAdBreak) {
      int adCount = adIndexInAdBreak + 1;
      adBreakAds[adBreakIndex] = Arrays.copyOf(adBreakAds[adBreakIndex], adCount);
    }
    adBreakAds[adBreakIndex][adIndexInAdBreak] = ad;
    maybeUpdateSourceInfo();
  }

  private void maybeUpdateSourceInfo() {
    if (adBreakTimesUs == null || contentTimeline == null) {
      // We don't have enough information to start building the timeline yet.
      return;
    }

    AdTimeline.Builder builder = new AdTimeline.Builder(contentTimeline);
    int count = adBreakTimesUs.length;
    boolean preroll = adBreakTimesUs[0] == 0;
    boolean postroll = adBreakTimesUs[count - 1] == C.TIME_UNSET;
    int midrollCount = count - (preroll ? 1 : 0) - (postroll ? 1 : 0);

    int adBreakIndex = 0;
    long contentTimeUs = 0;
    if (preroll) {
      addAdBreak(builder, adBreakIndex++);
    }
    for (int i = 0; i < midrollCount; i++) {
      long startTimeUs = contentTimeUs;
      contentTimeUs = adBreakTimesUs[adBreakIndex];
      builder.addContent(startTimeUs, contentTimeUs);
      addAdBreak(builder, adBreakIndex++);
    }
    builder.addContent(contentTimeUs, C.TIME_UNSET);
    if (postroll) {
      addAdBreak(builder, adBreakIndex);
    }

    timeline = builder.build();
    listener.onSourceInfoRefreshed(timeline, contentManifest);
  }

  private void addAdBreak(AdTimeline.Builder builder, int adBreakIndex) {
    int adCount = adBreakMediaSources[adBreakIndex].length;
    AdPodInfo adPodInfo = null;
    for (int adIndex = 0; adIndex < adCount; adIndex++) {
      Timeline adTimeline = adBreakTimelines[adBreakIndex][adIndex];
      long adDurationUs = adTimeline != null
          ? adTimeline.getPeriod(0, new Timeline.Period()).getDurationUs() : C.TIME_UNSET;
      Ad ad = adIndex < adBreakAds[adBreakIndex].length
          ? adBreakAds[adBreakIndex][adIndex] : null;
      builder.addAdPeriod(ad, adBreakIndex, adIndex, adDurationUs);
      if (ad != null) {
        adPodInfo = ad.getAdPodInfo();
      }
    }
    if (adPodInfo == null || adPodInfo.getTotalAds() > adCount) {
      // We don't know how many ads are in the ad break, or they have not loaded yet.
      builder.addAdPeriod(null, adBreakIndex, adCount, C.TIME_UNSET);
    }
  }

  private void onAdBreakPlayedToEnd(int adBreakIndex) {
    playedAdBreak[adBreakIndex] = true;
  }

  /**
   * Listener for ad loading events. All methods are called on the main thread.
   */
  private final class AdListener implements ImaAdsLoader.EventListener,
      ExtractorMediaSource.EventListener {

    @Override
    public void onAdBreakTimesUsLoaded(final long[] adBreakTimesUs) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          ImaAdsMediaSource.this.onAdBreakTimesUsLoaded(adBreakTimesUs);
        }
      });
    }

    @Override
    public void onUriLoaded(final int adBreakIndex, final int adIndexInAdBreak, final Uri uri) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          ImaAdsMediaSource.this.onAdUriLoaded(adBreakIndex, adIndexInAdBreak, uri);
        }
      });
    }

    @Override
    public void onAdLoaded(final int adBreakIndex, final int adIndexInAdBreak, final Ad ad) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          ImaAdsMediaSource.this.onAdLoaded(adBreakIndex, adIndexInAdBreak, ad);
        }
      });
    }

    @Override
    public void onAdBreakPlayedToEnd(final int adBreakIndex) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          ImaAdsMediaSource.this.onAdBreakPlayedToEnd(adBreakIndex);
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

  private static final class DeferredMediaPeriod implements MediaPeriod, MediaPeriod.Callback {

    private final int index;
    private final Allocator allocator;
    private final long positionUs;

    public MediaPeriod mediaPeriod;
    private MediaPeriod.Callback callback;

    public DeferredMediaPeriod(int index, Allocator allocator, long positionUs) {
      this.index = index;
      this.allocator = allocator;
      this.positionUs = positionUs;
    }

    public void setMediaSource(MediaSource mediaSource) {
      mediaPeriod = mediaSource.createPeriod(index, allocator, positionUs);
      if (callback != null) {
        mediaPeriod.prepare(this);
      }
    }

    @Override
    public void prepare(Callback callback) {
      this.callback = callback;
      if (mediaPeriod != null) {
        mediaPeriod.prepare(this);
      }
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
      if (mediaPeriod != null) {
        mediaPeriod.maybeThrowPrepareError();
      }
    }

    @Override
    public TrackGroupArray getTrackGroups() {
      return mediaPeriod.getTrackGroups();
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
        SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
      return mediaPeriod.selectTracks(selections, mayRetainStreamFlags, streams, streamResetFlags,
          positionUs);
    }

    @Override
    public void discardBuffer(long positionUs) {
      // Do nothing.
    }

    @Override
    public long readDiscontinuity() {
      return mediaPeriod.readDiscontinuity();
    }

    @Override
    public long getBufferedPositionUs() {
      return mediaPeriod.getBufferedPositionUs();
    }

    @Override
    public long seekToUs(long positionUs) {
      return mediaPeriod.seekToUs(positionUs);
    }

    @Override
    public long getNextLoadPositionUs() {
      return mediaPeriod.getNextLoadPositionUs();
    }

    @Override
    public boolean continueLoading(long positionUs) {
      return mediaPeriod.continueLoading(positionUs);
    }

    // MediaPeriod.Callback implementation.

    @Override
    public void onPrepared(MediaPeriod mediaPeriod) {
      Assertions.checkArgument(this.mediaPeriod == mediaPeriod);
      callback.onPrepared(this);
    }

    @Override
    public void onContinueLoadingRequested(MediaPeriod mediaPeriod) {
      Assertions.checkArgument(this.mediaPeriod == mediaPeriod);
      callback.onContinueLoadingRequested(this);
    }

  }

}
