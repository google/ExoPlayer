/*
 * Copyright (C) 2021 The Android Open Source Project
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
package androidx.media3.exoplayer.ima;

import static androidx.media3.common.AdPlaybackState.AD_STATE_AVAILABLE;
import static androidx.media3.common.AdPlaybackState.AD_STATE_UNAVAILABLE;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.msToUs;
import static androidx.media3.common.util.Util.usToMs;
import static androidx.media3.exoplayer.ima.ImaUtil.addLiveAdBreak;
import static androidx.media3.exoplayer.ima.ImaUtil.expandAdGroupPlaceholder;
import static androidx.media3.exoplayer.ima.ImaUtil.getAdGroupAndIndexInLiveMultiPeriodTimeline;
import static androidx.media3.exoplayer.ima.ImaUtil.getAdGroupAndIndexInVodMultiPeriodTimeline;
import static androidx.media3.exoplayer.ima.ImaUtil.getAdGroupDurationUsForLiveAdPeriodIndex;
import static androidx.media3.exoplayer.ima.ImaUtil.getWindowStartTimeUs;
import static androidx.media3.exoplayer.ima.ImaUtil.handleAdPeriodRemovedFromTimeline;
import static androidx.media3.exoplayer.ima.ImaUtil.maybeCorrectPreviouslyUnknownAdDurations;
import static androidx.media3.exoplayer.ima.ImaUtil.secToMsRounded;
import static androidx.media3.exoplayer.ima.ImaUtil.secToUsRounded;
import static androidx.media3.exoplayer.ima.ImaUtil.splitAdGroup;
import static androidx.media3.exoplayer.ima.ImaUtil.splitAdPlaybackStateForPeriods;
import static androidx.media3.exoplayer.ima.ImaUtil.updateAdDurationInAdGroup;
import static androidx.media3.exoplayer.source.ads.ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState;
import static com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.LOADED;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.ViewGroup;
import androidx.annotation.GuardedBy;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AdOverlayInfo;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AdViewProvider;
import androidx.media3.common.Bundleable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.ima.ImaUtil.ServerSideAdInsertionConfiguration;
import androidx.media3.exoplayer.source.CompositeMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.ForwardingTimeline;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ads.ServerSideAdInsertionMediaSource;
import androidx.media3.exoplayer.source.ads.ServerSideAdInsertionMediaSource.AdPlaybackStateUpdater;
import androidx.media3.exoplayer.source.ads.ServerSideAdInsertionUtil;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.Loader;
import androidx.media3.exoplayer.upstream.Loader.LoadErrorAction;
import androidx.media3.exoplayer.upstream.Loader.Loadable;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot;
import com.google.ads.interactivemedia.v3.api.CuePoint;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.StreamDisplayContainer;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** MediaSource for IMA server side inserted ad streams. */
@UnstableApi
public final class ImaServerSideAdInsertionMediaSource extends CompositeMediaSource<Void> {

  /** A listener to be notified of stream events. */
  public interface StreamEventListener {

    /**
     * Called when the {@linkplain StreamManager#getStreamId() stream ID} provided by the IMA SDK
     * changed.
     *
     * <p>This method is called on the main thread.
     *
     * @param mediaItem The media item that the source resolved to the given stream ID.
     * @param streamId The stream ID.
     */
    void onStreamIdChanged(MediaItem mediaItem, String streamId);
  }

  /**
   * Factory for creating {@link ImaServerSideAdInsertionMediaSource
   * ImaServerSideAdInsertionMediaSources}.
   *
   * <p>Apps can use the {@link ImaServerSideAdInsertionMediaSource.Factory} to customized the
   * {@link DefaultMediaSourceFactory} that is used to build a player:
   */
  public static final class Factory implements MediaSource.Factory {

    private final AdsLoader adsLoader;
    private final MediaSource.Factory contentMediaSourceFactory;

    /**
     * Creates a new factory for {@link ImaServerSideAdInsertionMediaSource
     * ImaServerSideAdInsertionMediaSources}.
     *
     * @param adsLoader The {@link AdsLoader}.
     * @param contentMediaSourceFactory The content media source factory to create content sources.
     */
    public Factory(AdsLoader adsLoader, MediaSource.Factory contentMediaSourceFactory) {
      this.adsLoader = adsLoader;
      this.contentMediaSourceFactory = contentMediaSourceFactory;
    }

    @CanIgnoreReturnValue
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(
        LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      contentMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(
        DrmSessionManagerProvider drmSessionManagerProvider) {
      contentMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
      return this;
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return contentMediaSourceFactory.getSupportedTypes();
    }

    @Override
    public MediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      Player player = checkNotNull(adsLoader.player);
      Uri streamRequestUri = checkNotNull(mediaItem.localConfiguration).uri;
      StreamRequest streamRequest =
          ImaServerSideAdInsertionUriBuilder.createStreamRequest(streamRequestUri);
      StreamPlayer streamPlayer = new StreamPlayer(player, mediaItem, streamRequest);
      ImaSdkFactory imaSdkFactory = ImaSdkFactory.getInstance();
      StreamDisplayContainer streamDisplayContainer =
          createStreamDisplayContainer(imaSdkFactory, adsLoader.configuration, streamPlayer);
      com.google.ads.interactivemedia.v3.api.AdsLoader imaAdsLoader =
          imaSdkFactory.createAdsLoader(
              adsLoader.context, adsLoader.configuration.imaSdkSettings, streamDisplayContainer);
      ImaServerSideAdInsertionMediaSource mediaSource =
          new ImaServerSideAdInsertionMediaSource(
              player,
              mediaItem,
              streamRequest,
              adsLoader,
              imaAdsLoader,
              streamPlayer,
              contentMediaSourceFactory);
      adsLoader.addMediaSourceResources(mediaSource, streamPlayer, imaAdsLoader);
      return mediaSource;
    }
  }

  /** An ads loader for IMA server side ad insertion streams. */
  public static final class AdsLoader {

    /** Builder for building an {@link AdsLoader}. */
    public static final class Builder {

      private final Context context;
      private final AdViewProvider adViewProvider;

      @Nullable private ImaSdkSettings imaSdkSettings;
      private StreamEventListener streamEventListener;
      @Nullable private AdEventListener adEventListener;
      @Nullable private AdErrorEvent.AdErrorListener adErrorListener;
      private State state;
      private ImmutableList<CompanionAdSlot> companionAdSlots;
      private boolean focusSkipButtonWhenAvailable;

      /**
       * Creates an instance.
       *
       * @param context A context.
       * @param adViewProvider A provider for {@link ViewGroup} instances.
       */
      public Builder(Context context, AdViewProvider adViewProvider) {
        this.context = context;
        this.adViewProvider = adViewProvider;
        companionAdSlots = ImmutableList.of();
        state = new State(ImmutableMap.of());
        focusSkipButtonWhenAvailable = true;
        streamEventListener =
            (mediaItem, streamId) -> {
              // Do nothing.
            };
      }

      /**
       * Sets the IMA SDK settings.
       *
       * <p>If this method is not called, the {@linkplain ImaSdkFactory#createImaSdkSettings()
       * default settings} will be used with the language set to {@linkplain
       * Util#getSystemLanguageCodes() the preferred system language}.
       *
       * @param imaSdkSettings The {@link ImaSdkSettings}.
       * @return This builder, for convenience.
       */
      @CanIgnoreReturnValue
      public AdsLoader.Builder setImaSdkSettings(ImaSdkSettings imaSdkSettings) {
        this.imaSdkSettings = imaSdkSettings;
        return this;
      }

      /**
       * Sets the optional {@link StreamEventListener} that will be called for stream events.
       *
       * @param streamEventListener The stream event listener.
       * @return This builder, for convenience.
       */
      @CanIgnoreReturnValue
      public AdsLoader.Builder setStreamEventListener(StreamEventListener streamEventListener) {
        this.streamEventListener = streamEventListener;
        return this;
      }

      /**
       * Sets the optional {@link AdEventListener} that will be passed to {@link
       * StreamManager#addAdEventListener(AdEventListener)} when the stream manager becomes
       * available.
       *
       * @param adEventListener The ad event listener.
       * @return This builder, for convenience.
       */
      @CanIgnoreReturnValue
      public AdsLoader.Builder setAdEventListener(AdEventListener adEventListener) {
        this.adEventListener = adEventListener;
        return this;
      }

      /**
       * Sets the optional {@link AdErrorEvent.AdErrorListener} that will be passed to {@link
       * StreamManager#addAdErrorListener(AdErrorEvent.AdErrorListener)} when the stream manager
       * becomes available.
       *
       * @param adErrorListener The {@link AdErrorEvent.AdErrorListener}.
       * @return This builder, for convenience.
       */
      @CanIgnoreReturnValue
      public AdsLoader.Builder setAdErrorListener(AdErrorEvent.AdErrorListener adErrorListener) {
        this.adErrorListener = adErrorListener;
        return this;
      }

      /**
       * Sets the slots to use for companion ads, if they are present in the loaded ad.
       *
       * @param companionAdSlots The slots to use for companion ads.
       * @return This builder, for convenience.
       * @see AdDisplayContainer#setCompanionSlots(Collection)
       */
      @CanIgnoreReturnValue
      public AdsLoader.Builder setCompanionAdSlots(Collection<CompanionAdSlot> companionAdSlots) {
        this.companionAdSlots = ImmutableList.copyOf(companionAdSlots);
        return this;
      }

      /**
       * Sets the optional state to resume with.
       *
       * <p>The state can be received when {@link #release() releasing} the {@link AdsLoader}.
       *
       * @param state The state to resume with.
       * @return This builder, for convenience.
       */
      @CanIgnoreReturnValue
      public AdsLoader.Builder setAdsLoaderState(State state) {
        this.state = state;
        return this;
      }

      /**
       * Sets whether to focus the skip button (when available) on Android TV devices. The default
       * setting is {@code true}.
       *
       * @param focusSkipButtonWhenAvailable Whether to focus the skip button (when available) on
       *     Android TV devices.
       * @return This builder, for convenience.
       * @see AdsRenderingSettings#setFocusSkipButtonWhenAvailable(boolean)
       */
      @CanIgnoreReturnValue
      public AdsLoader.Builder setFocusSkipButtonWhenAvailable(
          boolean focusSkipButtonWhenAvailable) {
        this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
        return this;
      }

      /** Returns a new {@link AdsLoader}. */
      public AdsLoader build() {
        @Nullable ImaSdkSettings imaSdkSettings = this.imaSdkSettings;
        if (imaSdkSettings == null) {
          imaSdkSettings = ImaSdkFactory.getInstance().createImaSdkSettings();
          imaSdkSettings.setLanguage(Util.getSystemLanguageCodes()[0]);
        }
        ServerSideAdInsertionConfiguration configuration =
            new ServerSideAdInsertionConfiguration(
                adViewProvider,
                imaSdkSettings,
                streamEventListener,
                adEventListener,
                adErrorListener,
                companionAdSlots,
                focusSkipButtonWhenAvailable,
                imaSdkSettings.isDebugMode());
        return new AdsLoader(context, configuration, state);
      }
    }

    /** The state of the {@link AdsLoader} that can be used when resuming from the background. */
    public static class State implements Bundleable {

      private final ImmutableMap<String, AdPlaybackState> adPlaybackStates;

      @VisibleForTesting
      /* package */ State(ImmutableMap<String, AdPlaybackState> adPlaybackStates) {
        this.adPlaybackStates = adPlaybackStates;
      }

      @Override
      public boolean equals(@Nullable Object o) {
        if (this == o) {
          return true;
        }
        if (!(o instanceof State)) {
          return false;
        }
        State state = (State) o;
        return adPlaybackStates.equals(state.adPlaybackStates);
      }

      @Override
      public int hashCode() {
        return adPlaybackStates.hashCode();
      }

      // Bundleable implementation.

      private static final String FIELD_AD_PLAYBACK_STATES = Util.intToStringMaxRadix(1);

      @Override
      public Bundle toBundle() {
        Bundle bundle = new Bundle();
        Bundle adPlaybackStatesBundle = new Bundle();
        for (Map.Entry<String, AdPlaybackState> entry : adPlaybackStates.entrySet()) {
          adPlaybackStatesBundle.putBundle(entry.getKey(), entry.getValue().toBundle());
        }
        bundle.putBundle(FIELD_AD_PLAYBACK_STATES, adPlaybackStatesBundle);
        return bundle;
      }

      /**
       * Object that can restore {@link AdsLoader.State} from a {@link Bundle}.
       *
       * @deprecated Use {@link #fromBundle} instead.
       */
      @Deprecated
      @SuppressWarnings("deprecation") // Deprecated instance of deprecated class
      public static final Bundleable.Creator<State> CREATOR = State::fromBundle;

      /** Restores a {@code State} from a {@link Bundle}. */
      public static State fromBundle(Bundle bundle) {
        @Nullable
        ImmutableMap.Builder<String, AdPlaybackState> adPlaybackStateMap =
            new ImmutableMap.Builder<>();
        Bundle adPlaybackStateBundle = checkNotNull(bundle.getBundle(FIELD_AD_PLAYBACK_STATES));
        for (String key : adPlaybackStateBundle.keySet()) {
          AdPlaybackState adPlaybackState =
              AdPlaybackState.fromBundle(checkNotNull(adPlaybackStateBundle.getBundle(key)));
          adPlaybackStateMap.put(
              key, AdPlaybackState.fromAdPlaybackState(/* adsId= */ key, adPlaybackState));
        }
        return new State(adPlaybackStateMap.buildOrThrow());
      }
    }

    private final ServerSideAdInsertionConfiguration configuration;
    private final Context context;
    private final Map<String, MediaSourceResourceHolder> mediaSourceResources;
    private final Map<String, AdPlaybackState> adPlaybackStateMap;

    @Nullable private Player player;

    private AdsLoader(
        Context context, ServerSideAdInsertionConfiguration configuration, State state) {
      this.context = context.getApplicationContext();
      this.configuration = configuration;
      mediaSourceResources = new HashMap<>();
      adPlaybackStateMap = new HashMap<>();
      for (Map.Entry<String, AdPlaybackState> entry : state.adPlaybackStates.entrySet()) {
        adPlaybackStateMap.put(entry.getKey(), entry.getValue());
      }
    }

    /**
     * Sets the player.
     *
     * <p>This method needs to be called before adding server side ad insertion media items to the
     * player.
     */
    public void setPlayer(Player player) {
      this.player = player;
    }

    /**
     * Puts the focus on the skip button, if a skip button is present and an ad is playing.
     *
     * @see StreamManager#focus()
     */
    public void focusSkipButton() {
      if (player == null) {
        return;
      }
      if (player.getPlaybackState() != Player.STATE_IDLE
          && player.getPlaybackState() != Player.STATE_ENDED
          && player.getMediaItemCount() > 0) {
        int currentPeriodIndex = player.getCurrentPeriodIndex();
        Object adsId =
            player
                .getCurrentTimeline()
                .getPeriod(currentPeriodIndex, new Timeline.Period())
                .getAdsId();
        if (adsId instanceof String) {
          MediaSourceResourceHolder mediaSourceResourceHolder = mediaSourceResources.get(adsId);
          if (mediaSourceResourceHolder != null
              && mediaSourceResourceHolder.imaServerSideAdInsertionMediaSource.streamManager
                  != null) {
            mediaSourceResourceHolder.imaServerSideAdInsertionMediaSource.streamManager.focus();
          }
        }
      }
    }

    /**
     * Releases resources.
     *
     * @return The {@link State} that can be used when resuming from the background.
     */
    public State release() {
      for (MediaSourceResourceHolder resourceHolder : mediaSourceResources.values()) {
        resourceHolder.streamPlayer.release();
        resourceHolder.imaServerSideAdInsertionMediaSource.setStreamManager(
            /* streamManager= */ null);
        resourceHolder.adsLoader.release();
      }
      State state = new State(ImmutableMap.copyOf(adPlaybackStateMap));
      adPlaybackStateMap.clear();
      mediaSourceResources.clear();
      player = null;
      return state;
    }

    // Internal methods.

    private void addMediaSourceResources(
        ImaServerSideAdInsertionMediaSource mediaSource,
        StreamPlayer streamPlayer,
        com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader) {
      mediaSourceResources.put(
          mediaSource.adsId, new MediaSourceResourceHolder(mediaSource, streamPlayer, adsLoader));
    }

    private AdPlaybackState getAdPlaybackState(String adsId) {
      @Nullable AdPlaybackState adPlaybackState = adPlaybackStateMap.get(adsId);
      return adPlaybackState != null ? adPlaybackState : AdPlaybackState.NONE;
    }

    private void setAdPlaybackState(String adsId, AdPlaybackState adPlaybackState) {
      this.adPlaybackStateMap.put(adsId, adPlaybackState);
    }

    private static final class MediaSourceResourceHolder {
      public final ImaServerSideAdInsertionMediaSource imaServerSideAdInsertionMediaSource;
      public final StreamPlayer streamPlayer;
      public final com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader;

      private MediaSourceResourceHolder(
          ImaServerSideAdInsertionMediaSource imaServerSideAdInsertionMediaSource,
          StreamPlayer streamPlayer,
          com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader) {
        this.imaServerSideAdInsertionMediaSource = imaServerSideAdInsertionMediaSource;
        this.streamPlayer = streamPlayer;
        this.adsLoader = adsLoader;
      }
    }
  }

  private static final String TAG = "ImaSSAIMediaSource";

  private final Player player;
  private final MediaSource.Factory contentMediaSourceFactory;
  private final AdsLoader adsLoader;
  private final com.google.ads.interactivemedia.v3.api.AdsLoader sdkAdsLoader;
  private final StreamEventListener streamEventListener;
  @Nullable private final AdEventListener applicationAdEventListener;
  @Nullable private final AdErrorListener applicationAdErrorListener;
  private final boolean isLiveStream;
  private final String adsId;
  private final StreamRequest streamRequest;
  private final int loadVideoTimeoutMs;
  private final StreamPlayer streamPlayer;
  private final Handler mainHandler;
  private final ComponentListener componentListener;

  @Nullable private Loader loader;
  @Nullable private StreamManager streamManager;
  @Nullable private String streamId;
  @Nullable private ServerSideAdInsertionMediaSource serverSideAdInsertionMediaSource;
  @Nullable private IOException loadError;
  @Nullable private Timeline contentTimeline;
  private AdPlaybackState adPlaybackState;

  @GuardedBy("this")
  private MediaItem mediaItem;

  private ImaServerSideAdInsertionMediaSource(
      Player player,
      MediaItem mediaItem,
      StreamRequest streamRequest,
      AdsLoader adsLoader,
      com.google.ads.interactivemedia.v3.api.AdsLoader sdkAdsLoader,
      StreamPlayer streamPlayer,
      MediaSource.Factory contentMediaSourceFactory) {
    this.player = player;
    this.mediaItem = mediaItem;
    this.streamRequest = streamRequest;
    this.adsLoader = adsLoader;
    this.sdkAdsLoader = sdkAdsLoader;
    this.streamPlayer = streamPlayer;
    this.contentMediaSourceFactory = contentMediaSourceFactory;
    this.streamEventListener = adsLoader.configuration.streamEventListener;
    this.applicationAdEventListener = adsLoader.configuration.applicationAdEventListener;
    this.applicationAdErrorListener = adsLoader.configuration.applicationAdErrorListener;
    Assertions.checkArgument(player.getApplicationLooper() == Looper.getMainLooper());
    mainHandler = new Handler(Looper.getMainLooper());
    Uri streamRequestUri = checkNotNull(mediaItem.localConfiguration).uri;
    isLiveStream = ImaServerSideAdInsertionUriBuilder.isLiveStream(streamRequestUri);
    adsId = ImaServerSideAdInsertionUriBuilder.getAdsId(streamRequestUri);
    loadVideoTimeoutMs = ImaServerSideAdInsertionUriBuilder.getLoadVideoTimeoutMs(streamRequestUri);
    streamRequest = ImaServerSideAdInsertionUriBuilder.createStreamRequest(streamRequestUri);
    boolean isDashStream = Objects.equals(streamRequest.getFormat(), StreamFormat.DASH);
    componentListener =
        new ComponentListener(
            isLiveStream
                ? (isDashStream
                    ? new MultiPeriodLiveAdEventListener()
                    : new SinglePeriodLiveAdEventListener())
                : new VodAdEventListener());
    adPlaybackState = adsLoader.getAdPlaybackState(adsId);
  }

  @Override
  public synchronized MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public boolean canUpdateMediaItem(MediaItem mediaItem) {
    MediaItem existingMediaItem = getMediaItem();
    MediaItem.LocalConfiguration existingConfiguration =
        checkNotNull(existingMediaItem.localConfiguration);
    @Nullable MediaItem.LocalConfiguration newConfiguration = mediaItem.localConfiguration;
    return newConfiguration != null
        && newConfiguration.uri.equals(existingConfiguration.uri)
        && newConfiguration.streamKeys.equals(existingConfiguration.streamKeys)
        && Util.areEqual(newConfiguration.customCacheKey, existingConfiguration.customCacheKey)
        && Util.areEqual(newConfiguration.drmConfiguration, existingConfiguration.drmConfiguration)
        && existingMediaItem.liveConfiguration.equals(mediaItem.liveConfiguration);
  }

  @Override
  public synchronized void updateMediaItem(MediaItem mediaItem) {
    this.mediaItem = mediaItem;
  }

  @Override
  public void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    mainHandler.post(() -> assertSingleInstanceInPlaylist(checkNotNull(player)));
    super.prepareSourceInternal(mediaTransferListener);
    if (loader == null) {
      Loader loader = new Loader("ImaServerSideAdInsertionMediaSource");
      player.addListener(componentListener);
      StreamManagerLoadable streamManagerLoadable =
          new StreamManagerLoadable(
              sdkAdsLoader,
              /* imaServerSideAdInsertionMediaSource= */ this,
              streamRequest,
              streamPlayer,
              applicationAdErrorListener);
      loader.startLoading(
          streamManagerLoadable,
          new StreamManagerLoadableCallback(),
          /* defaultMinRetryCount= */ 0);
      this.loader = loader;
    }
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      Void childSourceId, MediaSource mediaSource, Timeline newTimeline) {
    MediaItem mediaItem = getMediaItem();
    refreshSourceInfo(
        new ForwardingTimeline(newTimeline) {
          @Override
          public Window getWindow(
              int windowIndex, Window window, long defaultPositionProjectionUs) {
            newTimeline.getWindow(windowIndex, window, defaultPositionProjectionUs);
            window.mediaItem = mediaItem;
            return window;
          }
        });
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    return checkNotNull(serverSideAdInsertionMediaSource)
        .createPeriod(id, allocator, startPositionUs);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    checkNotNull(serverSideAdInsertionMediaSource).releasePeriod(mediaPeriod);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    super.maybeThrowSourceInfoRefreshError();
    if (loadError != null) {
      IOException loadError = this.loadError;
      this.loadError = null;
      throw loadError;
    }
  }

  @Override
  protected void releaseSourceInternal() {
    super.releaseSourceInternal();
    if (loader != null) {
      loader.release();
      mainHandler.post(
          () -> {
            player.removeListener(componentListener);
            setStreamManager(/* streamManager= */ null);
          });
      loader = null;
    }
    contentTimeline = null;
    serverSideAdInsertionMediaSource = null;
  }

  // Internal methods (called on the main thread).

  @MainThread
  private void setStreamManager(@Nullable StreamManager streamManager) {
    if (this.streamManager == streamManager) {
      return;
    }
    if (this.streamManager != null) {
      if (applicationAdEventListener != null) {
        this.streamManager.removeAdEventListener(applicationAdEventListener);
      }
      if (applicationAdErrorListener != null) {
        this.streamManager.removeAdErrorListener(applicationAdErrorListener);
      }
      this.streamManager.removeAdEventListener(componentListener);
      this.streamManager.destroy();
      streamId = null;
    }
    this.streamManager = streamManager;
    if (streamManager != null) {
      String newStreamId = streamManager.getStreamId();
      if (!Objects.equals(streamId, newStreamId)) {
        streamId = newStreamId;
        streamEventListener.onStreamIdChanged(getMediaItem(), newStreamId);
      }
      streamManager.addAdEventListener(componentListener);
      if (applicationAdEventListener != null) {
        streamManager.addAdEventListener(applicationAdEventListener);
      }
      if (applicationAdErrorListener != null) {
        streamManager.addAdErrorListener(applicationAdErrorListener);
      }
      AdsRenderingSettings adsRenderingSettings =
          ImaSdkFactory.getInstance().createAdsRenderingSettings();
      adsRenderingSettings.setLoadVideoTimeout(loadVideoTimeoutMs);
      adsRenderingSettings.setFocusSkipButtonWhenAvailable(
          adsLoader.configuration.focusSkipButtonWhenAvailable);
      streamManager.init(adsRenderingSettings);
    }
  }

  @MainThread
  private void setAdPlaybackState(AdPlaybackState adPlaybackState) {
    if (adPlaybackState.equals(this.adPlaybackState)) {
      return;
    }
    this.adPlaybackState = adPlaybackState;
    invalidateServerSideAdInsertionAdPlaybackState();
  }

  @MainThread
  private void setContentTimeline(Timeline contentTimeline) {
    if (contentTimeline.equals(this.contentTimeline)) {
      return;
    }
    if (isLiveStream && Objects.equals(streamRequest.getFormat(), StreamFormat.DASH)) {
      // If the ad started playing while the corresponding period in the timeline had an unknown
      // duration, the ad duration is estimated and needs to be corrected when the actual duration
      // is reported.
      adPlaybackState = maybeCorrectPreviouslyUnknownAdDurations(contentTimeline, adPlaybackState);
    }
    this.contentTimeline = contentTimeline;
    invalidateServerSideAdInsertionAdPlaybackState();
  }

  @MainThread
  private void invalidateServerSideAdInsertionAdPlaybackState() {
    if (!adPlaybackState.equals(AdPlaybackState.NONE)
        && contentTimeline != null
        && serverSideAdInsertionMediaSource != null) {
      Timeline contentTimeline = checkNotNull(this.contentTimeline);
      ImmutableMap<Object, AdPlaybackState> splitAdPlaybackStates;
      if (Objects.equals(streamRequest.getFormat(), StreamFormat.DASH)) {
        // DASH ad groups are always split by period.
        splitAdPlaybackStates = splitAdPlaybackStateForPeriods(adPlaybackState, contentTimeline);
      } else {
        // The HLS single period timeline for VOD and live must not be split.
        int firstPeriodIndex =
            contentTimeline.getWindow(/* windowIndex= */ 0, new Timeline.Window()).firstPeriodIndex;
        Object periodUid =
            checkNotNull(
                contentTimeline.getPeriod(
                        firstPeriodIndex, new Timeline.Period(), /* setIds= */ true)
                    .uid);
        splitAdPlaybackStates = ImmutableMap.of(periodUid, adPlaybackState);
      }
      streamPlayer.setAdPlaybackStates(adsId, splitAdPlaybackStates, contentTimeline);
      checkNotNull(serverSideAdInsertionMediaSource)
          .setAdPlaybackStates(splitAdPlaybackStates, contentTimeline);
      if (!isLiveStream) {
        adsLoader.setAdPlaybackState(adsId, adPlaybackState);
      }
    }
  }

  // Internal methods (called on the playback thread).

  private void setContentUri(Uri contentUri) {
    if (serverSideAdInsertionMediaSource == null) {
      MediaItem mediaItem = getMediaItem();
      MediaItem contentMediaItem =
          new MediaItem.Builder()
              .setUri(contentUri)
              .setDrmConfiguration(checkNotNull(mediaItem.localConfiguration).drmConfiguration)
              .setLiveConfiguration(mediaItem.liveConfiguration)
              .setCustomCacheKey(mediaItem.localConfiguration.customCacheKey)
              .setStreamKeys(mediaItem.localConfiguration.streamKeys)
              .build();
      ServerSideAdInsertionMediaSource serverSideAdInsertionMediaSource =
          new ServerSideAdInsertionMediaSource(
              contentMediaSourceFactory.createMediaSource(contentMediaItem), componentListener);
      this.serverSideAdInsertionMediaSource = serverSideAdInsertionMediaSource;
      if (isLiveStream) {
        mainHandler.post(
            () ->
                setAdPlaybackState(
                    new AdPlaybackState(adsId).withLivePostrollPlaceholderAppended()));
      }
      prepareChildSource(/* id= */ null, serverSideAdInsertionMediaSource);
    }
  }

  // Static methods.

  @SuppressWarnings("deprecation") // b/192231683 prevents using non-deprecated method
  private static AdPlaybackState setVodAdGroupPlaceholders(
      List<CuePoint> cuePoints, AdPlaybackState adPlaybackState) {
    // TODO(b/192231683) Use getEndTimeMs()/getStartTimeMs() after jar target was removed
    for (int i = 0; i < cuePoints.size(); i++) {
      CuePoint cuePoint = cuePoints.get(i);
      long fromPositionUs = msToUs(secToMsRounded(cuePoint.getStartTime()));
      adPlaybackState =
          addAdGroupToAdPlaybackState(
              adPlaybackState,
              /* fromPositionUs= */ fromPositionUs,
              /* contentResumeOffsetUs= */ 0,
              /* adDurationsUs...= */ getAdDuration(
                  /* startTimeSeconds= */ cuePoint.getStartTime(),
                  /* endTimeSeconds= */ cuePoint.getEndTime()));
    }
    return adPlaybackState;
  }

  private static long getAdDuration(double startTimeSeconds, double endTimeSeconds) {
    // startTimeSeconds and endTimeSeconds that are coming from the SDK, only have a precision of
    // milliseconds so everything that is below a millisecond can be safely considered as coming
    // from rounding issues.
    return msToUs(secToMsRounded(endTimeSeconds - startTimeSeconds));
  }

  private static AdPlaybackState setVodAdInPlaceholder(Ad ad, AdPlaybackState adPlaybackState) {
    AdPodInfo adPodInfo = ad.getAdPodInfo();
    // Handle post rolls that have a podIndex of -1.
    int adGroupIndex =
        adPodInfo.getPodIndex() == -1 ? adPlaybackState.adGroupCount - 1 : adPodInfo.getPodIndex();
    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
    int adIndexInAdGroup = adPodInfo.getAdPosition() - 1;
    if (adGroup.count < adPodInfo.getTotalAds()) {
      adPlaybackState =
          expandAdGroupPlaceholder(
              adGroupIndex,
              /* adGroupDurationUs= */ msToUs(secToMsRounded(adPodInfo.getMaxDuration())),
              adIndexInAdGroup,
              /* adDurationUs= */ msToUs(secToMsRounded(ad.getDuration())),
              /* adsInAdGroupCount= */ adPodInfo.getTotalAds(),
              adPlaybackState);
    } else if (adIndexInAdGroup < adGroup.count - 1) {
      adPlaybackState =
          updateAdDurationInAdGroup(
              adGroupIndex,
              adIndexInAdGroup,
              /* adDurationUs= */ msToUs(secToMsRounded(ad.getDuration())),
              adPlaybackState);
    }
    return adPlaybackState;
  }

  private static AdPlaybackState skipAd(Ad ad, AdPlaybackState adPlaybackState) {
    AdPodInfo adPodInfo = ad.getAdPodInfo();
    int adGroupIndex = adPodInfo.getPodIndex();
    // IMA SDK always returns index starting at 1.
    int adIndexInAdGroup = adPodInfo.getAdPosition() - 1;
    return adPlaybackState.withSkippedAd(adGroupIndex, adIndexInAdGroup);
  }

  private final class ComponentListener
      implements AdEvent.AdEventListener, Player.Listener, AdPlaybackStateUpdater {

    private final AdEventListener adEventListener;

    /** Creates an new instance. */
    public ComponentListener(AdEventListener adEventListener) {
      this.adEventListener = adEventListener;
    }

    // Implement Player.Listener.

    @Override
    public void onPositionDiscontinuity(
        Player.PositionInfo oldPosition,
        Player.PositionInfo newPosition,
        @Player.DiscontinuityReason int reason) {
      if (!(reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
          || (isLiveStream && reason == Player.DISCONTINUITY_REASON_REMOVE))) {
        // Only auto transitions and removals of an ad period in live streams need to be handled.
        return;
      }

      MediaItem mediaItem = getMediaItem();
      if (mediaItem.equals(oldPosition.mediaItem) && !mediaItem.equals(newPosition.mediaItem)) {
        // Playback automatically transitioned to the next media item. Notify the SDK.
        streamPlayer.onContentCompleted();
      }

      if (!mediaItem.equals(oldPosition.mediaItem)
          || !mediaItem.equals(newPosition.mediaItem)
          || !adsId.equals(
              player
                  .getCurrentTimeline()
                  .getPeriodByUid(checkNotNull(newPosition.periodUid), new Timeline.Period())
                  .getAdsId())) {
        // Discontinuity not within this ad media source.
        return;
      }

      if (oldPosition.adGroupIndex != C.INDEX_UNSET) {
        int adGroupIndex = oldPosition.adGroupIndex;
        int adIndexInAdGroup = oldPosition.adIndexInAdGroup;
        Timeline timeline = player.getCurrentTimeline();
        Timeline.Window window =
            timeline.getWindow(oldPosition.mediaItemIndex, new Timeline.Window());
        if (window.lastPeriodIndex > window.firstPeriodIndex) {
          if (reason == Player.DISCONTINUITY_REASON_REMOVE) {
            setAdPlaybackState(
                handleAdPeriodRemovedFromTimeline(
                    player.getCurrentPeriodIndex(), timeline, adPlaybackState));
            return;
          }
          // Map adGroupIndex and adIndexInAdGroup to multi-period window.
          int periodIndexInContentTimeline = oldPosition.periodIndex - window.firstPeriodIndex;
          Pair<Integer, Integer> adGroupIndexAndAdIndexInAdGroup =
              window.isLive()
                  ? getAdGroupAndIndexInLiveMultiPeriodTimeline(
                      periodIndexInContentTimeline, adPlaybackState, checkNotNull(contentTimeline))
                  : getAdGroupAndIndexInVodMultiPeriodTimeline(
                      periodIndexInContentTimeline, adPlaybackState, checkNotNull(contentTimeline));
          adGroupIndex = adGroupIndexAndAdIndexInAdGroup.first;
          adIndexInAdGroup = adGroupIndexAndAdIndexInAdGroup.second;
        }

        AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
        int adState = adGroup.states[adIndexInAdGroup];
        if (adState == AD_STATE_AVAILABLE || adState == AD_STATE_UNAVAILABLE) {
          AdPlaybackState newAdPlaybackState =
              adPlaybackState.withPlayedAd(adGroupIndex, /* adIndexInAdGroup= */ adIndexInAdGroup);
          adGroup = newAdPlaybackState.getAdGroup(adGroupIndex);
          if (isLiveStream
              && newPosition.adGroupIndex == C.INDEX_UNSET
              && adIndexInAdGroup < adGroup.states.length - 1
              && adGroup.states[adIndexInAdGroup + 1] == AD_STATE_AVAILABLE) {
            // There is an available ad after the ad period that just ended being played!
            Log.w(TAG, "Detected late ad event. Regrouping trailing ads into separate ad group.");
            newAdPlaybackState =
                splitAdGroup(
                    adGroup,
                    adGroupIndex,
                    /* splitIndexExclusive= */ adIndexInAdGroup + 1,
                    newAdPlaybackState);
          }
          setAdPlaybackState(newAdPlaybackState);
        }
      }
    }

    @Override
    public void onMetadata(Metadata metadata) {
      if (!isCurrentAdPlaying(player, getMediaItem(), adsId)) {
        return;
      }
      for (int i = 0; i < metadata.length(); i++) {
        Metadata.Entry entry = metadata.get(i);
        if (entry instanceof TextInformationFrame) {
          TextInformationFrame textFrame = (TextInformationFrame) entry;
          if ("TXXX".equals(textFrame.id)) {
            streamPlayer.triggerUserTextReceived(textFrame.values.get(0));
          }
        } else if (entry instanceof EventMessage) {
          EventMessage eventMessage = (EventMessage) entry;
          String eventMessageValue = new String(eventMessage.messageData);
          streamPlayer.triggerUserTextReceived(eventMessageValue);
        }
      }
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int state) {
      if (state == Player.STATE_ENDED && isCurrentAdPlaying(player, getMediaItem(), adsId)) {
        streamPlayer.onContentCompleted();
      }
    }

    @Override
    public void onVolumeChanged(float volume) {
      if (!isCurrentAdPlaying(player, getMediaItem(), adsId)) {
        return;
      }
      int volumePct = (int) Math.floor(volume * 100);
      streamPlayer.onContentVolumeChanged(volumePct);
    }

    // Implement AdEvent.AdEventListener.

    @MainThread
    @Override
    public void onAdEvent(AdEvent event) {
      adEventListener.onAdEvent(event);
    }

    // Implement AdPlaybackStateUpdater (called on the playback thread).

    @Override
    public boolean onAdPlaybackStateUpdateRequested(Timeline contentTimeline) {
      mainHandler.post(() -> setContentTimeline(contentTimeline));
      // Defer source refresh to ad playback state update for VOD (wait for potential ad cue points)
      // or DASH (split manifest).
      return !isLiveStream || Objects.equals(streamRequest.getFormat(), StreamFormat.DASH);
    }
  }

  private final class StreamManagerLoadableCallback
      implements Loader.Callback<StreamManagerLoadable> {

    @Override
    public void onLoadCompleted(
        StreamManagerLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {
      setContentUri(checkNotNull(loadable.getContentUri()));
    }

    @Override
    public void onLoadCanceled(
        StreamManagerLoadable loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        boolean released) {
      // We only cancel when the loader is released.
      checkState(released);
    }

    @Override
    public LoadErrorAction onLoadError(
        StreamManagerLoadable loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      loadError = error;
      return Loader.DONT_RETRY;
    }
  }

  /** Loads the {@link StreamManager} and the content URI. */
  private static class StreamManagerLoadable
      implements Loadable, AdsLoadedListener, AdErrorListener {

    private final com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader;
    private final ImaServerSideAdInsertionMediaSource imaServerSideAdInsertionMediaSource;
    private final StreamRequest request;
    private final StreamPlayer streamPlayer;
    @Nullable private final AdErrorListener adErrorListener;
    private final ConditionVariable conditionVariable;

    @Nullable private volatile Uri contentUri;
    private volatile boolean cancelled;
    private volatile boolean error;
    @Nullable private volatile String errorMessage;
    private volatile int errorCode;

    /** Creates an instance. */
    private StreamManagerLoadable(
        com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader,
        ImaServerSideAdInsertionMediaSource imaServerSideAdInsertionMediaSource,
        StreamRequest request,
        StreamPlayer streamPlayer,
        @Nullable AdErrorListener adErrorListener) {
      this.adsLoader = adsLoader;
      this.imaServerSideAdInsertionMediaSource = imaServerSideAdInsertionMediaSource;
      this.request = request;
      this.streamPlayer = streamPlayer;
      this.adErrorListener = adErrorListener;
      conditionVariable = new ConditionVariable();
      errorCode = -1;
    }

    /** Returns the DAI content URI or null if not yet available. */
    @Nullable
    public Uri getContentUri() {
      return contentUri;
    }

    // Implement Loadable.

    @Override
    public void load() throws IOException {
      try {
        // SDK will call loadUrl on stream player for SDK once manifest uri is available.
        streamPlayer.setStreamLoadListener(
            (streamUri, subtitles) -> {
              contentUri = Uri.parse(streamUri);
              conditionVariable.open();
            });
        if (adErrorListener != null) {
          adsLoader.addAdErrorListener(adErrorListener);
        }
        adsLoader.addAdsLoadedListener(this);
        adsLoader.addAdErrorListener(this);
        adsLoader.requestStream(request);
        while (contentUri == null && !cancelled && !error) {
          try {
            conditionVariable.block();
          } catch (InterruptedException e) {
            /* Do nothing. */
          }
        }
        if (error && contentUri == null) {
          throw new IOException(errorMessage + " [errorCode: " + errorCode + "]");
        }
      } finally {
        adsLoader.removeAdsLoadedListener(this);
        adsLoader.removeAdErrorListener(this);
        if (adErrorListener != null) {
          adsLoader.removeAdErrorListener(adErrorListener);
        }
      }
    }

    @Override
    public void cancelLoad() {
      cancelled = true;
    }

    // AdsLoader.AdsLoadedListener implementation.

    @MainThread
    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
      StreamManager streamManager = event.getStreamManager();
      if (streamManager == null) {
        error = true;
        errorMessage = "streamManager is null after ads manager has been loaded";
        conditionVariable.open();
        return;
      }
      imaServerSideAdInsertionMediaSource.setStreamManager(streamManager);
    }

    // AdErrorEvent.AdErrorListener implementation.

    @MainThread
    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
      error = true;
      if (adErrorEvent.getError() != null) {
        @Nullable String errorMessage = adErrorEvent.getError().getMessage();
        if (errorMessage != null) {
          this.errorMessage = errorMessage.replace('\n', ' ');
        }
        errorCode = adErrorEvent.getError().getErrorCodeNumber();
      }
      conditionVariable.open();
    }
  }

  /**
   * Receives the content URI from the SDK and sends back in-band media metadata and playback
   * progression data to the SDK.
   */
  private static final class StreamPlayer implements VideoStreamPlayer {

    /** A listener to listen for the stream URI loaded by the SDK. */
    public interface StreamLoadListener {
      /**
       * Loads a stream with dynamic ad insertion given the stream url and subtitles array. The
       * subtitles array is only used in VOD streams.
       *
       * <p>Each entry in the subtitles array is a HashMap that corresponds to a language. Each map
       * will have a "language" key with a two letter language string value, a "language name" to
       * specify the set of subtitles if multiple sets exist for the same language, and one or more
       * subtitle key/value pairs. Here's an example the map for English:
       *
       * <p>"language" -> "en" "language_name" -> "English" "webvtt" ->
       * "https://example.com/vtt/en.vtt" "ttml" -> "https://example.com/ttml/en.ttml"
       */
      void onLoadStream(String streamUri, List<HashMap<String, String>> subtitles);
    }

    private final List<VideoStreamPlayer.VideoStreamPlayerCallback> callbacks;
    private final Player player;
    private final MediaItem mediaItem;
    private final Timeline.Window window;
    private final Timeline.Period period;
    private final boolean isDashStream;

    private ImmutableMap<Object, AdPlaybackState> adPlaybackStates;
    @Nullable private Timeline contentTimeline;
    @Nullable private Object adsId;
    @Nullable private StreamLoadListener streamLoadListener;

    /** Creates an instance. */
    public StreamPlayer(Player player, MediaItem mediaItem, StreamRequest streamRequest) {
      this.player = player;
      this.mediaItem = mediaItem;
      this.isDashStream = streamRequest.getFormat() == StreamFormat.DASH;
      callbacks = new ArrayList<>(/* initialCapacity= */ 1);
      adPlaybackStates = ImmutableMap.of();
      window = new Timeline.Window();
      period = new Timeline.Period();
    }

    /** Registers the ad playback states matching to the given content timeline. */
    public void setAdPlaybackStates(
        Object adsId,
        ImmutableMap<Object, AdPlaybackState> adPlaybackStates,
        Timeline contentTimeline) {
      this.adsId = adsId;
      this.adPlaybackStates = adPlaybackStates;
      this.contentTimeline = contentTimeline;
    }

    /** Sets the {@link StreamLoadListener} to be called when the SSAI content URI was loaded. */
    public void setStreamLoadListener(StreamLoadListener listener) {
      streamLoadListener = Assertions.checkNotNull(listener);
    }

    /** Called when the content has completed playback. */
    public void onContentCompleted() {
      for (VideoStreamPlayer.VideoStreamPlayerCallback callback : callbacks) {
        callback.onContentComplete();
      }
    }

    /** Called when the content player changed the volume. */
    public void onContentVolumeChanged(int volumePct) {
      for (VideoStreamPlayer.VideoStreamPlayerCallback callback : callbacks) {
        callback.onVolumeChanged(volumePct);
      }
    }

    /** Releases the player. */
    public void release() {
      callbacks.clear();
      adsId = null;
      adPlaybackStates = ImmutableMap.of();
      contentTimeline = null;
      streamLoadListener = null;
    }

    // Implements VolumeProvider.

    @Override
    public int getVolume() {
      return (int) Math.floor(player.getVolume() * 100);
    }

    // Implement ContentProgressProvider.

    @Override
    public VideoProgressUpdate getContentProgress() {
      if (!isCurrentAdPlaying(player, mediaItem, adsId)) {
        return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
      } else if (adPlaybackStates.isEmpty()) {
        return new VideoProgressUpdate(/* currentTimeMs= */ 0, /* durationMs= */ C.TIME_UNSET);
      }

      Timeline timeline = player.getCurrentTimeline();
      int currentPeriodIndex = player.getCurrentPeriodIndex();
      timeline.getPeriod(currentPeriodIndex, period, /* setIds= */ true);
      timeline.getWindow(player.getCurrentMediaItemIndex(), window);
      long streamPositionMs;
      if (isDashStream && window.isLive()) {
        // In multi-period live streams, we can't assume to find the same period in both timelines
        // with a given period index. Calculate stream position from the period structure instead.
        streamPositionMs =
            player.isPlayingAd()
                ? window.windowStartTimeMs
                    + usToMs(period.positionInWindowUs)
                    + player.getCurrentPosition()
                : window.windowStartTimeMs + player.getContentPosition();
      } else {
        // The map of ad playback states is keyed with the period UID of the content timeline. In
        // timelines that do not change the periods (VOD and single period live), we can use the
        // period index in both timelines.
        Timeline.Period contentPeriod =
            checkNotNull(contentTimeline)
                .getPeriod(
                    currentPeriodIndex - window.firstPeriodIndex,
                    new Timeline.Period(),
                    /* setIds= */ true);
        AdPlaybackState adPlaybackState = checkNotNull(adPlaybackStates.get(contentPeriod.uid));
        // Calculate the stream position from the current position and the playback state.
        streamPositionMs =
            usToMs(ServerSideAdInsertionUtil.getStreamPositionUs(player, adPlaybackState));
        if (window.windowStartTimeMs != C.TIME_UNSET) {
          // Add the time since epoch at start of the window for live streams.
          streamPositionMs += window.windowStartTimeMs + period.getPositionInWindowMs();
        } else if (currentPeriodIndex > window.firstPeriodIndex) {
          // Add the end position of the previous period in the underlying stream.
          checkNotNull(contentTimeline)
              .getPeriod(
                  currentPeriodIndex - window.firstPeriodIndex - 1,
                  contentPeriod,
                  /* setIds= */ true);
          streamPositionMs += usToMs(contentPeriod.positionInWindowUs + contentPeriod.durationUs);
        }
      }
      return new VideoProgressUpdate(
          streamPositionMs,
          checkNotNull(contentTimeline).getWindow(/* windowIndex= */ 0, window).getDurationMs());
    }

    // Implement VideoStreamPlayer.

    @Override
    public void loadUrl(String url, List<HashMap<String, String>> subtitles) {
      if (streamLoadListener != null) {
        // SDK provided manifest url, notify the listener.
        streamLoadListener.onLoadStream(url, subtitles);
      }
    }

    @Override
    public void addCallback(VideoStreamPlayer.VideoStreamPlayerCallback callback) {
      callbacks.add(callback);
    }

    @Override
    public void removeCallback(VideoStreamPlayer.VideoStreamPlayerCallback callback) {
      callbacks.remove(callback);
    }

    @Override
    public void onAdBreakStarted() {
      // Do nothing.
    }

    @Override
    public void onAdBreakEnded() {
      // Do nothing.
    }

    @Override
    public void onAdPeriodStarted() {
      // Do nothing.
    }

    @Override
    public void onAdPeriodEnded() {
      // Do nothing.
    }

    @Override
    public void pause() {
      // Do nothing.
    }

    @Override
    public void resume() {
      // Do nothing.
    }

    @Override
    public void seek(long timeMs) {
      // Do nothing.
    }

    // Internal methods.

    private void triggerUserTextReceived(String userText) {
      for (VideoStreamPlayer.VideoStreamPlayerCallback callback : callbacks) {
        callback.onUserTextReceived(userText);
      }
    }
  }

  private static boolean isCurrentAdPlaying(
      Player player, MediaItem mediaItem, @Nullable Object adsId) {
    if (player.getPlaybackState() == Player.STATE_IDLE) {
      return false;
    }
    Timeline.Period period = new Timeline.Period();
    player.getCurrentTimeline().getPeriod(player.getCurrentPeriodIndex(), period);
    return (period.isPlaceholder && mediaItem.equals(player.getCurrentMediaItem()))
        || (adsId != null && adsId.equals(period.getAdsId()));
  }

  private static StreamDisplayContainer createStreamDisplayContainer(
      ImaSdkFactory imaSdkFactory,
      ServerSideAdInsertionConfiguration config,
      StreamPlayer streamPlayer) {
    StreamDisplayContainer container =
        ImaSdkFactory.createStreamDisplayContainer(
            checkNotNull(config.adViewProvider.getAdViewGroup()), streamPlayer);
    container.setCompanionSlots(config.companionAdSlots);
    registerFriendlyObstructions(imaSdkFactory, container, config.adViewProvider);
    return container;
  }

  private static void registerFriendlyObstructions(
      ImaSdkFactory imaSdkFactory,
      StreamDisplayContainer container,
      AdViewProvider adViewProvider) {
    for (int i = 0; i < adViewProvider.getAdOverlayInfos().size(); i++) {
      AdOverlayInfo overlayInfo = adViewProvider.getAdOverlayInfos().get(i);
      container.registerFriendlyObstruction(
          imaSdkFactory.createFriendlyObstruction(
              overlayInfo.view,
              ImaUtil.getFriendlyObstructionPurpose(overlayInfo.purpose),
              overlayInfo.reasonDetail != null ? overlayInfo.reasonDetail : "Unknown reason"));
    }
  }

  private static void assertSingleInstanceInPlaylist(Player player) {
    int counter = 0;
    for (int i = 0; i < player.getMediaItemCount(); i++) {
      MediaItem mediaItem = player.getMediaItemAt(i);
      if (mediaItem.localConfiguration != null
          && C.SSAI_SCHEME.equals(mediaItem.localConfiguration.uri.getScheme())
          && ImaServerSideAdInsertionUriBuilder.IMA_AUTHORITY.equals(
              mediaItem.localConfiguration.uri.getAuthority())) {
        if (++counter > 1) {
          throw new IllegalStateException(
              "Multiple IMA server side ad insertion sources not supported.");
        }
      }
    }
  }

  private class VodAdEventListener implements AdEventListener {
    @Override
    public void onAdEvent(AdEvent event) {
      AdPlaybackState newAdPlaybackState = adPlaybackState;
      switch (event.getType()) {
        case CUEPOINTS_CHANGED:
          if (newAdPlaybackState.equals(AdPlaybackState.NONE)) {
            newAdPlaybackState =
                setVodAdGroupPlaceholders(
                    checkNotNull(streamManager).getCuePoints(), new AdPlaybackState(adsId));
          }
          break;
        case LOADED:
          newAdPlaybackState = setVodAdInPlaceholder(event.getAd(), newAdPlaybackState);
          break;
        case SKIPPED:
          newAdPlaybackState = skipAd(event.getAd(), newAdPlaybackState);
          break;
        default:
          // Do nothing.
          break;
      }
      setAdPlaybackState(newAdPlaybackState);
    }
  }

  private class SinglePeriodLiveAdEventListener implements AdEventListener {
    @Override
    public void onAdEvent(AdEvent event) {
      if (!Objects.equals(event.getType(), LOADED)) {
        return;
      }
      AdPlaybackState newAdPlaybackState = adPlaybackState;
      Timeline timeline = player.getCurrentTimeline();
      Timeline.Period currentPeriod = new Timeline.Period();
      long positionInWindowUs =
          timeline.getPeriod(player.getCurrentPeriodIndex(), currentPeriod).positionInWindowUs;
      long contentPositionUs =
          player.isPlayingAd()
              ? currentPeriod.getAdGroupTimeUs(player.getCurrentAdGroupIndex())
              : msToUs(player.getContentPosition());
      Ad ad = event.getAd();
      AdPodInfo adPodInfo = ad.getAdPodInfo();
      newAdPlaybackState =
          addLiveAdBreak(
              /* currentContentPeriodPositionUs= */ contentPositionUs - positionInWindowUs,
              /* adDurationUs= */ secToUsRounded(ad.getDuration()),
              /* adPositionInAdPod= */ adPodInfo.getAdPosition(),
              /* totalAdDurationUs= */ secToUsRounded(adPodInfo.getMaxDuration()),
              /* totalAdsInAdPod= */ adPodInfo.getTotalAds(),
              /* adPlaybackState= */ newAdPlaybackState.equals(AdPlaybackState.NONE)
                  ? new AdPlaybackState(adsId)
                  : newAdPlaybackState);
      setAdPlaybackState(newAdPlaybackState);
    }
  }

  private class MultiPeriodLiveAdEventListener implements AdEventListener {
    @Override
    public void onAdEvent(AdEvent event) {
      if (!Objects.equals(event.getType(), LOADED)) {
        return;
      }
      AdPodInfo adPodInfo = event.getAd().getAdPodInfo();
      Timeline timeline = player.getCurrentTimeline();
      Timeline.Window window = new Timeline.Window();
      Timeline.Period adPeriod = new Timeline.Period();
      // In case all periods are in the live window, we need to correct the ad group duration when
      // inserting the first ad. Try calculate ad group duration from media structure.
      long totalAdDurationUs =
          getAdGroupDurationUsForLiveAdPeriodIndex(
              timeline,
              adPodInfo,
              /* adPeriodIndex= */ player.getCurrentPeriodIndex(),
              window,
              adPeriod);
      long adPeriodStartTimeUs =
          getWindowStartTimeUs(window.windowStartTimeMs, window.positionInFirstPeriodUs)
              + adPeriod.positionInWindowUs;
      long adDurationUs =
          adPeriod.durationUs != C.TIME_UNSET
              ? adPeriod.durationUs
              : secToUsRounded(event.getAd().getDuration());
      setAdPlaybackState(
          addLiveAdBreak(
              /* currentContentPeriodPositionUs= */ adPeriodStartTimeUs,
              adDurationUs,
              adPodInfo.getAdPosition(),
              totalAdDurationUs,
              adPodInfo.getTotalAds(),
              adPlaybackState));
    }
  }
}
