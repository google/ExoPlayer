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
package com.google.android.exoplayer2.source.ads;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.ViewGroup;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.DeferredMediaPeriod;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link MediaSource} that inserts ads linearly with a provided content media source.
 */
public final class AdsMediaSource implements MediaSource {

  /** Factory for creating {@link MediaSource}s to play ad media. */
  public interface MediaSourceFactory {

    /**
     * Creates a new {@link MediaSource} for loading the ad media with the specified {@code uri}.
     *
     * @param uri The URI of the media or manifest to play.
     * @param handler A handler for listener events. May be null if delivery of events is not
     *     required.
     * @param listener A listener for events. May be null if delivery of events is not required.
     * @return The new media source.
     */
    MediaSource createMediaSource(
        Uri uri, @Nullable Handler handler, @Nullable MediaSourceEventListener listener);

    /**
     * Returns the content types supported by media sources created by this factory. Each element
     * should be one of {@link C#TYPE_DASH}, {@link C#TYPE_SS}, {@link C#TYPE_HLS} or {@link
     * C#TYPE_OTHER}.
     *
     * @return The content types supported by media sources created by this factory.
     */
    int[] getSupportedTypes();
  }

  /** Listener for ads media source events. */
  public interface EventListener extends MediaSourceEventListener {

    /**
     * Called if there was an error loading ads. The media source will load the content without ads
     * if ads can't be loaded, so listen for this event if you need to implement additional handling
     * (for example, stopping the player).
     *
     * @param error The error.
     */
    void onAdLoadError(IOException error);

    /**
     * Called when the user clicks through an ad (for example, following a 'learn more' link).
     */
    void onAdClicked();

    /**
     * Called when the user taps a non-clickthrough part of an ad.
     */
    void onAdTapped();

  }

  private static final String TAG = "AdsMediaSource";

  private final MediaSource contentMediaSource;
  private final MediaSourceFactory adMediaSourceFactory;
  private final AdsLoader adsLoader;
  private final ViewGroup adUiViewGroup;
  @Nullable private final Handler eventHandler;
  @Nullable private final EventListener eventListener;
  private final Handler mainHandler;
  private final ComponentListener componentListener;
  private final Map<MediaSource, List<DeferredMediaPeriod>> deferredMediaPeriodByAdMediaSource;
  private final Timeline.Period period;

  private Handler playerHandler;
  private ExoPlayer player;
  private volatile boolean released;

  // Accessed on the player thread.
  private Timeline contentTimeline;
  private Object contentManifest;
  private AdPlaybackState adPlaybackState;
  private MediaSource[][] adGroupMediaSources;
  private long[][] adDurationsUs;
  private MediaSource.Listener listener;

  /**
   * Constructs a new source that inserts ads linearly with the content specified by {@code
   * contentMediaSource}. Ad media is loaded using {@link ExtractorMediaSource}.
   *
   * @param contentMediaSource The {@link MediaSource} providing the content to play.
   * @param dataSourceFactory Factory for data sources used to load ad media.
   * @param adsLoader The loader for ads.
   * @param adUiViewGroup A {@link ViewGroup} on top of the player that will show any ad UI.
   */
  public AdsMediaSource(
      MediaSource contentMediaSource,
      DataSource.Factory dataSourceFactory,
      AdsLoader adsLoader,
      ViewGroup adUiViewGroup) {
    this(
        contentMediaSource,
        dataSourceFactory,
        adsLoader,
        adUiViewGroup,
        /* eventHandler= */ null,
        /* eventListener= */ null);
  }

  /**
   * Constructs a new source that inserts ads linearly with the content specified by {@code
   * contentMediaSource}. Ad media is loaded using {@link ExtractorMediaSource}.
   *
   * @param contentMediaSource The {@link MediaSource} providing the content to play.
   * @param dataSourceFactory Factory for data sources used to load ad media.
   * @param adsLoader The loader for ads.
   * @param adUiViewGroup A {@link ViewGroup} on top of the player that will show any ad UI.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public AdsMediaSource(
      MediaSource contentMediaSource,
      DataSource.Factory dataSourceFactory,
      AdsLoader adsLoader,
      ViewGroup adUiViewGroup,
      @Nullable Handler eventHandler,
      @Nullable EventListener eventListener) {
    this(
        contentMediaSource,
        new ExtractorMediaSource.Factory(dataSourceFactory),
        adsLoader,
        adUiViewGroup,
        eventHandler,
        eventListener);
  }

  /**
   * Constructs a new source that inserts ads linearly with the content specified by {@code
   * contentMediaSource}.
   *
   * @param contentMediaSource The {@link MediaSource} providing the content to play.
   * @param adMediaSourceFactory Factory for media sources used to load ad media.
   * @param adsLoader The loader for ads.
   * @param adUiViewGroup A {@link ViewGroup} on top of the player that will show any ad UI.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public AdsMediaSource(
      MediaSource contentMediaSource,
      MediaSourceFactory adMediaSourceFactory,
      AdsLoader adsLoader,
      ViewGroup adUiViewGroup,
      @Nullable Handler eventHandler,
      @Nullable EventListener eventListener) {
    this.contentMediaSource = contentMediaSource;
    this.adMediaSourceFactory = adMediaSourceFactory;
    this.adsLoader = adsLoader;
    this.adUiViewGroup = adUiViewGroup;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    mainHandler = new Handler(Looper.getMainLooper());
    componentListener = new ComponentListener();
    deferredMediaPeriodByAdMediaSource = new HashMap<>();
    period = new Timeline.Period();
    adGroupMediaSources = new MediaSource[0][];
    adDurationsUs = new long[0][];
    adsLoader.setSupportedContentTypes(adMediaSourceFactory.getSupportedTypes());
  }

  @Override
  public void prepareSource(final ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    Assertions.checkArgument(isTopLevelSource);
    this.listener = listener;
    this.player = player;
    playerHandler = new Handler();
    contentMediaSource.prepareSource(player, false, new Listener() {
      @Override
      public void onSourceInfoRefreshed(MediaSource source, Timeline timeline, Object manifest) {
        AdsMediaSource.this.onContentSourceInfoRefreshed(timeline, manifest);
      }
    });
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        adsLoader.attachPlayer(player, componentListener, adUiViewGroup);
      }
    });
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    contentMediaSource.maybeThrowSourceInfoRefreshError();
    for (MediaSource[] mediaSources : adGroupMediaSources) {
      for (MediaSource mediaSource : mediaSources) {
        if (mediaSource != null) {
          mediaSource.maybeThrowSourceInfoRefreshError();
        }
      }
    }
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    if (adPlaybackState.adGroupCount > 0 && id.isAd()) {
      final int adGroupIndex = id.adGroupIndex;
      final int adIndexInAdGroup = id.adIndexInAdGroup;
      if (adGroupMediaSources[adGroupIndex].length <= adIndexInAdGroup) {
        Uri adUri = adPlaybackState.adUris[id.adGroupIndex][id.adIndexInAdGroup];
        final MediaSource adMediaSource =
            adMediaSourceFactory.createMediaSource(adUri, eventHandler, eventListener);
        int oldAdCount = adGroupMediaSources[id.adGroupIndex].length;
        if (adIndexInAdGroup >= oldAdCount) {
          int adCount = adIndexInAdGroup + 1;
          adGroupMediaSources[adGroupIndex] =
              Arrays.copyOf(adGroupMediaSources[adGroupIndex], adCount);
          adDurationsUs[adGroupIndex] = Arrays.copyOf(adDurationsUs[adGroupIndex], adCount);
          Arrays.fill(adDurationsUs[adGroupIndex], oldAdCount, adCount, C.TIME_UNSET);
        }
        adGroupMediaSources[adGroupIndex][adIndexInAdGroup] = adMediaSource;
        deferredMediaPeriodByAdMediaSource.put(adMediaSource, new ArrayList<DeferredMediaPeriod>());
        adMediaSource.prepareSource(player, false, new MediaSource.Listener() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline,
              @Nullable Object manifest) {
            onAdSourceInfoRefreshed(adMediaSource, adGroupIndex, adIndexInAdGroup, timeline);
          }
        });
      }
      MediaSource mediaSource = adGroupMediaSources[adGroupIndex][adIndexInAdGroup];
      DeferredMediaPeriod deferredMediaPeriod =
          new DeferredMediaPeriod(mediaSource, new MediaPeriodId(0), allocator);
      List<DeferredMediaPeriod> mediaPeriods = deferredMediaPeriodByAdMediaSource.get(mediaSource);
      if (mediaPeriods == null) {
        deferredMediaPeriod.createPeriod();
      } else {
        // Keep track of the deferred media period so it can be populated with the real media period
        // when the source's info becomes available.
        mediaPeriods.add(deferredMediaPeriod);
      }
      return deferredMediaPeriod;
    } else {
      DeferredMediaPeriod mediaPeriod = new DeferredMediaPeriod(contentMediaSource, id, allocator);
      mediaPeriod.createPeriod();
      return mediaPeriod;
    }
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((DeferredMediaPeriod) mediaPeriod).releasePeriod();
  }

  @Override
  public void releaseSource() {
    released = true;
    contentMediaSource.releaseSource();
    for (MediaSource[] mediaSources : adGroupMediaSources) {
      for (MediaSource mediaSource : mediaSources) {
        if (mediaSource != null) {
          mediaSource.releaseSource();
        }
      }
    }
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        adsLoader.detachPlayer();
      }
    });
  }

  // Internal methods.

  private void onAdPlaybackState(AdPlaybackState adPlaybackState) {
    if (this.adPlaybackState == null) {
      adGroupMediaSources = new MediaSource[adPlaybackState.adGroupCount][];
      Arrays.fill(adGroupMediaSources, new MediaSource[0]);
      adDurationsUs = new long[adPlaybackState.adGroupCount][];
      Arrays.fill(adDurationsUs, new long[0]);
    }
    this.adPlaybackState = adPlaybackState;
    maybeUpdateSourceInfo();
  }

  private void onLoadError(final IOException error) {
    Log.w(TAG, "Ad load error", error);
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          if (!released) {
            eventListener.onAdLoadError(error);
          }
        }
      });
    }
  }

  private void onContentSourceInfoRefreshed(Timeline timeline, Object manifest) {
    contentTimeline = timeline;
    contentManifest = manifest;
    maybeUpdateSourceInfo();
  }

  private void onAdSourceInfoRefreshed(MediaSource mediaSource, int adGroupIndex,
      int adIndexInAdGroup, Timeline timeline) {
    Assertions.checkArgument(timeline.getPeriodCount() == 1);
    adDurationsUs[adGroupIndex][adIndexInAdGroup] = timeline.getPeriod(0, period).getDurationUs();
    if (deferredMediaPeriodByAdMediaSource.containsKey(mediaSource)) {
      List<DeferredMediaPeriod> mediaPeriods = deferredMediaPeriodByAdMediaSource.get(mediaSource);
      for (int i = 0; i < mediaPeriods.size(); i++) {
        mediaPeriods.get(i).createPeriod();
      }
      deferredMediaPeriodByAdMediaSource.remove(mediaSource);
    }
    maybeUpdateSourceInfo();
  }

  private void maybeUpdateSourceInfo() {
    if (adPlaybackState != null && contentTimeline != null) {
      Timeline timeline = adPlaybackState.adGroupCount == 0 ? contentTimeline
          : new SinglePeriodAdTimeline(contentTimeline, adPlaybackState.adGroupTimesUs,
              adPlaybackState.adCounts, adPlaybackState.adsLoadedCounts,
              adPlaybackState.adsPlayedCounts, adDurationsUs, adPlaybackState.adResumePositionUs,
              adPlaybackState.contentDurationUs);
      listener.onSourceInfoRefreshed(this, timeline, contentManifest);
    }
  }

  /** Listener for component events. All methods are called on the main thread. */
  private final class ComponentListener implements AdsLoader.EventListener {

    @Override
    public void onAdPlaybackState(final AdPlaybackState adPlaybackState) {
      if (released) {
        return;
      }
      playerHandler.post(new Runnable() {
        @Override
        public void run() {
          if (released) {
            return;
          }
          AdsMediaSource.this.onAdPlaybackState(adPlaybackState);
        }
      });
    }

    @Override
    public void onAdClicked() {
      if (eventHandler != null && eventListener != null) {
        eventHandler.post(new Runnable() {
          @Override
          public void run() {
            if (!released) {
              eventListener.onAdClicked();
            }
          }
        });
      }
    }

    @Override
    public void onAdTapped() {
      if (eventHandler != null && eventListener != null) {
        eventHandler.post(new Runnable() {
          @Override
          public void run() {
            if (!released) {
              eventListener.onAdTapped();
            }
          }
        });
      }
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
          AdsMediaSource.this.onLoadError(error);
        }
      });
    }

  }

}
