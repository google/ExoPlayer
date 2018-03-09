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
import com.google.android.exoplayer2.source.CompositeMediaSource;
import com.google.android.exoplayer2.source.DeferredMediaPeriod;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
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

/** A {@link MediaSource} that inserts ads linearly with a provided content media source. */
public final class AdsMediaSource extends CompositeMediaSource<MediaPeriodId> {

  /** Factory for creating {@link MediaSource}s to play ad media. */
  public interface MediaSourceFactory {

    /**
     * Creates a new {@link MediaSource} for loading the ad media with the specified {@code uri}.
     *
     * @param uri The URI of the media or manifest to play.
     * @return The new media source.
     */
    MediaSource createMediaSource(Uri uri);

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
     * Called if there was an error loading one or more ads. The loader will skip the problematic
     * ad(s).
     *
     * @param error The error.
     */
    void onAdLoadError(IOException error);

    /**
     * Called when an unexpected internal error is encountered while loading ads. The loader will
     * skip all remaining ads, as the error is not recoverable.
     *
     * @param error The error.
     */
    void onInternalAdLoadError(RuntimeException error);

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
  private final Map<MediaSource, List<DeferredMediaPeriod>> deferredMediaPeriodByAdMediaSource;
  private final Timeline.Period period;

  // Accessed on the player thread.
  private ComponentListener componentListener;
  private Timeline contentTimeline;
  private Object contentManifest;
  private AdPlaybackState adPlaybackState;
  private MediaSource[][] adGroupMediaSources;
  private long[][] adDurationsUs;

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
    deferredMediaPeriodByAdMediaSource = new HashMap<>();
    period = new Timeline.Period();
    adGroupMediaSources = new MediaSource[0][];
    adDurationsUs = new long[0][];
    adsLoader.setSupportedContentTypes(adMediaSourceFactory.getSupportedTypes());
  }

  @Override
  public void prepareSourceInternal(final ExoPlayer player, boolean isTopLevelSource) {
    super.prepareSourceInternal(player, isTopLevelSource);
    Assertions.checkArgument(isTopLevelSource);
    final ComponentListener componentListener = new ComponentListener();
    this.componentListener = componentListener;
    prepareChildSource(new MediaPeriodId(/* periodIndex= */ 0), contentMediaSource);
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        adsLoader.attachPlayer(player, componentListener, adUiViewGroup);
      }
    });
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    if (adPlaybackState.adGroupCount > 0 && id.isAd()) {
      int adGroupIndex = id.adGroupIndex;
      int adIndexInAdGroup = id.adIndexInAdGroup;
      if (adGroupMediaSources[adGroupIndex].length <= adIndexInAdGroup) {
        Uri adUri = adPlaybackState.adGroups[id.adGroupIndex].uris[id.adIndexInAdGroup];
        MediaSource adMediaSource = adMediaSourceFactory.createMediaSource(adUri);
        adMediaSource.addEventListener(eventHandler, eventListener);
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
        prepareChildSource(id, adMediaSource);
      }
      MediaSource mediaSource = adGroupMediaSources[adGroupIndex][adIndexInAdGroup];
      DeferredMediaPeriod deferredMediaPeriod =
          new DeferredMediaPeriod(
              mediaSource,
              new MediaPeriodId(/* periodIndex= */ 0, id.windowSequenceNumber),
              allocator);
      deferredMediaPeriod.setPrepareErrorListener(
          new AdPrepareErrorListener(adGroupIndex, adIndexInAdGroup));
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
    DeferredMediaPeriod deferredMediaPeriod = (DeferredMediaPeriod) mediaPeriod;
    List<DeferredMediaPeriod> mediaPeriods =
        deferredMediaPeriodByAdMediaSource.get(deferredMediaPeriod.mediaSource);
    if (mediaPeriods != null) {
      mediaPeriods.remove(deferredMediaPeriod);
    }
    deferredMediaPeriod.releasePeriod();
  }

  @Override
  public void releaseSourceInternal() {
    super.releaseSourceInternal();
    componentListener.release();
    componentListener = null;
    deferredMediaPeriodByAdMediaSource.clear();
    contentTimeline = null;
    contentManifest = null;
    adPlaybackState = null;
    adGroupMediaSources = new MediaSource[0][];
    adDurationsUs = new long[0][];
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        adsLoader.detachPlayer();
      }
    });
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      MediaPeriodId mediaPeriodId,
      MediaSource mediaSource,
      Timeline timeline,
      @Nullable Object manifest) {
    if (mediaPeriodId.isAd()) {
      int adGroupIndex = mediaPeriodId.adGroupIndex;
      int adIndexInAdGroup = mediaPeriodId.adIndexInAdGroup;
      onAdSourceInfoRefreshed(mediaSource, adGroupIndex, adIndexInAdGroup, timeline);
    } else {
      onContentSourceInfoRefreshed(timeline, manifest);
    }
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
      adPlaybackState = adPlaybackState.withAdDurationsUs(adDurationsUs);
      Timeline timeline =
          adPlaybackState.adGroupCount == 0
              ? contentTimeline
              : new SinglePeriodAdTimeline(contentTimeline, adPlaybackState);
      refreshSourceInfo(timeline, contentManifest);
    }
  }

  /** Listener for component events. All methods are called on the main thread. */
  private final class ComponentListener implements AdsLoader.EventListener {

    private final Handler playerHandler;
    private volatile boolean released;

    /**
     * Creates new listener which forwards ad playback states on the creating thread and all other
     * events on the external event listener thread.
     */
    public ComponentListener() {
      playerHandler = new Handler();
    }

    /** Releases the component listener. */
    public void release() {
      released = true;
      playerHandler.removeCallbacksAndMessages(null);
    }

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
      if (released) {
        return;
      }
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
      if (released) {
        return;
      }
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
    public void onAdLoadError(final IOException error) {
      if (released) {
        return;
      }
      Log.w(TAG, "Ad load error", error);
      if (eventHandler != null && eventListener != null) {
        eventHandler.post(
            new Runnable() {
              @Override
              public void run() {
                if (!released) {
                  eventListener.onAdLoadError(error);
                }
              }
            });
      }
    }

    @Override
    public void onInternalAdLoadError(final RuntimeException error) {
      if (released) {
        return;
      }
      Log.w(TAG, "Internal ad load error", error);
      if (eventHandler != null && eventListener != null) {
        eventHandler.post(
            new Runnable() {
              @Override
              public void run() {
                if (!released) {
                  eventListener.onInternalAdLoadError(error);
                }
              }
            });
      }
    }
  }

  private final class AdPrepareErrorListener implements DeferredMediaPeriod.PrepareErrorListener {

    private final int adGroupIndex;
    private final int adIndexInAdGroup;

    public AdPrepareErrorListener(int adGroupIndex, int adIndexInAdGroup) {
      this.adGroupIndex = adGroupIndex;
      this.adIndexInAdGroup = adIndexInAdGroup;
    }

    @Override
    public void onPrepareError(final IOException exception) {
      mainHandler.post(
          new Runnable() {
            @Override
            public void run() {
              adsLoader.handlePrepareError(adGroupIndex, adIndexInAdGroup, exception);
            }
          });
    }
  }
}
