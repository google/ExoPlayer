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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.net.Uri;
import android.view.ViewGroup;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.media3.common.AdOverlayInfo;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AdViewProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.CompositeMediaSource;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceFactory;
import androidx.media3.exoplayer.source.ads.ServerSideInsertedAdsMediaSource;
import androidx.media3.exoplayer.source.ads.ServerSideInsertedAdsUtil;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.Loader;
import androidx.media3.exoplayer.upstream.Loader.LoadErrorAction;
import androidx.media3.exoplayer.upstream.Loader.Loadable;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot;
import com.google.ads.interactivemedia.v3.api.CuePoint;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.StreamDisplayContainer;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/** Creates instances of {@link MediaSource} that are specific to IMA DAI ads playback. */
@UnstableApi
public final class ImaServerSideDaiMediaSourceFactory implements MediaSourceFactory {

  /** Builder for {@link ImaServerSideDaiMediaSourceFactory}. */
  public static final class Builder {

    private final MediaSourceFactory childStreamsMediaSourceFactory;
    private final Context context;
    private final PlayerProvider playerProvider;
    private final ViewGroup adsContainer;
    private final AdErrorListener adErrorListener;

    @Nullable private ImaSdkSettings imaSdkSettings;
    @Nullable private AdEventListener adEventListener;
    @Nullable private VideoAdPlayer.VideoAdPlayerCallback videoAdPlayerCallback;
    @Nullable private List<CompanionAdSlot> companionAdSlots;
    @Nullable private AdViewProvider adViewProvider;

    private boolean debugModeEnabled;

    /** Creates a new builder for {@link ImaServerSideDaiMediaSourceFactory}. */
    public Builder(
        MediaSourceFactory childStreamsMediaSourceFactory,
        Context context,
        PlayerProvider playerProvider,
        ViewGroup adsContainer,
        AdErrorListener adErrorListener) {
      this.childStreamsMediaSourceFactory = checkNotNull(childStreamsMediaSourceFactory);
      this.context = checkNotNull(context).getApplicationContext();
      this.playerProvider = checkNotNull(playerProvider);
      this.adsContainer = checkNotNull(adsContainer);
      this.adErrorListener = checkNotNull(adErrorListener);
    }

    /**
     * Sets the IMA SDK settings. The provided settings instance's player type and version fields
     * may be overwritten.
     *
     * <p>If this method is not called the default settings will be used.
     *
     * @param imaSdkSettings The {@link ImaSdkSettings}.
     * @return This builder, for convenience.
     */
    public Builder setImaSdkSettings(ImaSdkSettings imaSdkSettings) {
      this.imaSdkSettings = checkNotNull(imaSdkSettings);
      return this;
    }

    /**
     * Sets a listener for ad events that will be passed to {@link
     * AdsManager#addAdEventListener(AdEventListener)}.
     *
     * @param adEventListener The ad event listener.
     * @return This builder, for convenience.
     */
    public Builder setAdEventListener(AdEventListener adEventListener) {
      this.adEventListener = checkNotNull(adEventListener);
      return this;
    }

    /**
     * Sets a callback to receive video ad player events. Note that these events are handled
     * internally by the IMA SDK and the medias source being build by this builder. For analytics
     * and diagnostics, new implementations should generally use events from the top-level {@link
     * Player.Listener top-level listeners} instead of setting a callback via this method.
     *
     * @param videoAdPlayerCallback The callback to receive video ad player events.
     * @return This builder, for convenience.
     * @see com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback
     */
    public Builder setVideoAdPlayerCallback(
        VideoAdPlayer.VideoAdPlayerCallback videoAdPlayerCallback) {
      this.videoAdPlayerCallback = checkNotNull(videoAdPlayerCallback);
      return this;
    }

    /**
     * Sets the slots to use for companion ads, if they are present in the loaded ad.
     *
     * @param companionAdSlots The slots to use for companion ads.
     * @return This builder, for convenience.
     * @see AdDisplayContainer#setCompanionSlots(Collection)
     */
    public Builder setCompanionAdSlots(Collection<CompanionAdSlot> companionAdSlots) {
      this.companionAdSlots = ImmutableList.copyOf(checkNotNull(companionAdSlots));
      return this;
    }

    /**
     * Sets the {@link AdViewProvider} that provides information about views for the ad playback UI.
     *
     * @param adViewProvider A provider for {@link ViewGroup} instances.
     * @return This builder, for convenience.
     */
    public Builder setAdViewProvider(@Nullable AdViewProvider adViewProvider) {
      this.adViewProvider = adViewProvider;
      return this;
    }

    /**
     * Sets whether to enable outputting verbose logs for the IMA extension and IMA SDK. The default
     * value is {@code false}. This setting is intended for debugging only, and should not be
     * enabled in production applications.
     *
     * @param debugModeEnabled Whether to enable outputting verbose logs for the IMA extension and
     *     IMA SDK.
     * @return This builder, for convenience.
     * @see ImaSdkSettings#setDebugMode(boolean)
     */
    public Builder setDebugModeEnabled(boolean debugModeEnabled) {
      this.debugModeEnabled = debugModeEnabled;
      return this;
    }

    /** Returns a new {@link ImaServerSideDaiMediaSourceFactory}. */
    public ImaServerSideDaiMediaSourceFactory build() {
      DaiStreamPlayer streamPlayer = new DaiStreamPlayer(playerProvider);
      return new ImaServerSideDaiMediaSourceFactory(
          childStreamsMediaSourceFactory,
          context,
          playerProvider,
          streamPlayer,
          adsContainer,
          adViewProvider,
          new ImaUtil.DaiConfiguration(
              adErrorListener,
              companionAdSlots,
              adEventListener,
              videoAdPlayerCallback,
              imaSdkSettings,
              debugModeEnabled));
    }
  }

  /** Provides {@link Player} instances. */
  public interface PlayerProvider {

    /**
     * Returns an {@link Player} instance.
     *
     * <p>This method is called each time a {@link MediaSource} is created from a {@link MediaItem}
     * that represents DAI stream.
     */
    Player getPlayer();
  }

  /** Simplified and more targeted ad state representation within stream for DAI ads. */
  private interface AdState {

    AdPlaybackState getAdPlaybackState();

    /**
     * Updates the {@link AdPlaybackState} with new ad information.
     *
     * @param postroll Ad belongs to a postroll ad break.
     * @param adStartUs The ad start position, in microseconds.
     * @param adDurationUs The ad duration, in microseconds.
     */
    void handleAdLoaded(boolean postroll, long adStartUs, long adDurationUs);

    /**
     * Sets the ad breaks/cue points.
     *
     * @param adGroupTimesUs A list of cuepoints.
     */
    void addAdBreaks(long[] adGroupTimesUs);

    /**
     * Called when an ad is skipped. Puts that ad in a skipped state.
     *
     * @param adPosition The position of the ad within the pod.
     */
    void handleAdSkipped(int adPosition);

    /** Called when an ad break ends. */
    void handleAdBreakEnded();
  }

  /**
   * A listener for stream load. IMA sdk will send stream data when stream finishes initialization.
   */
  private interface DaiStreamLoadListener {

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

  // Entities shared by all IMA DAI media sources.
  private final MediaSourceFactory childStreamsMediaSourceFactory;
  private final ImaSdkFactory imaSdkFactory;
  private final ImaSdkSettings imaSdkSettings;
  private final PlayerProvider playerProvider;
  private final DaiStreamPlayer streamPlayerForSdk;
  private final ImaUtil.DaiConfiguration config;
  private final Context context;
  private final StreamDisplayContainer container;

  private ImaServerSideDaiMediaSourceFactory(
      MediaSourceFactory childStreamsMediaSourceFactory,
      Context context,
      PlayerProvider playerProvider,
      DaiStreamPlayer streamPlayerForSdk,
      ViewGroup adsContainer,
      @Nullable AdViewProvider adViewProvider,
      ImaUtil.DaiConfiguration config) {
    imaSdkFactory = ImaSdkFactory.getInstance();
    this.childStreamsMediaSourceFactory = childStreamsMediaSourceFactory;
    this.context = context;
    this.playerProvider = playerProvider;
    this.streamPlayerForSdk = streamPlayerForSdk;
    this.config = config;
    container = ImaSdkFactory.createStreamDisplayContainer(adsContainer, streamPlayerForSdk);
    if (config.companionAdSlots != null) {
      container.setCompanionSlots(config.companionAdSlots);
    }
    imaSdkSettings =
        config.imaSdkSettings == null
            ? imaSdkFactory.createImaSdkSettings()
            : config.imaSdkSettings;
    imaSdkSettings.setLanguage(Util.getSystemLanguageCodes()[0]);
    if (config.debugModeEnabled) {
      imaSdkSettings.setDebugMode(true);
    }
    registerFriendlyObstructions(container, adViewProvider);
  }

  @Override
  public MediaSourceFactory setDrmSessionManagerProvider(
      @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
    childStreamsMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
    return this;
  }

  @Override
  public MediaSourceFactory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
    return this;
  }

  @Override
  public MediaSourceFactory setDrmHttpDataSourceFactory(
      @Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
    return this;
  }

  @Override
  public MediaSourceFactory setDrmUserAgent(@Nullable String userAgent) {
    return this;
  }

  @Override
  public MediaSourceFactory setLoadErrorHandlingPolicy(
      @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    childStreamsMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
    return this;
  }

  @Override
  public int[] getSupportedTypes() {
    return new int[] {C.TYPE_DASH, C.TYPE_HLS};
  }

  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
    // Ads loader can be shared, but it is not recommended. Each media source will use its own ads
    // loader to handle stream request.
    AdsLoader adsLoader = imaSdkFactory.createAdsLoader(context, imaSdkSettings, container);
    DaiMediaSource daiMediaSource =
        new DaiMediaSource(
            mediaItem,
            playerProvider.getPlayer(),
            childStreamsMediaSourceFactory,
            adsLoader,
            config,
            streamPlayerForSdk);
    streamPlayerForSdk.mediaSourceCreated(daiMediaSource);
    return daiMediaSource;
  }

  private void registerFriendlyObstructions(
      StreamDisplayContainer container, @Nullable AdViewProvider adViewProvider) {
    if (adViewProvider != null) {
      for (AdOverlayInfo overlayInfo : adViewProvider.getAdOverlayInfos()) {
        checkNotNull(overlayInfo.reasonDetail);
        container.registerFriendlyObstruction(
            imaSdkFactory.createFriendlyObstruction(
                overlayInfo.view,
                ImaUtil.getFriendlyObstructionPurpose(overlayInfo.purpose),
                overlayInfo.reasonDetail));
      }
    }
  }

  /** Loads all the required data for a stream with ads. */
  private static class StreamManagerLoadable
      implements Loadable, AdsLoader.AdsLoadedListener, AdErrorEvent.AdErrorListener {

    private final ImaUtil.DaiConfiguration config;
    private final AdsLoader adsLoader;
    private final DaiStreamPlayer streamPlayerForSdk;
    private final StreamRequest request;
    @Nullable private StreamManager streamManager;
    @Nullable private Uri streamManifestUri;

    public StreamManagerLoadable(
        ImaUtil.DaiConfiguration config,
        AdsLoader adsLoader,
        StreamRequest request,
        DaiStreamPlayer streamPlayerForSdk) {
      this.config = checkNotNull(config);
      this.adsLoader = checkNotNull(adsLoader);
      this.request = checkNotNull(request);
      this.streamPlayerForSdk = checkNotNull(streamPlayerForSdk);
    }

    @Override
    public void cancelLoad() {
      // No-op, we never cancel load.
    }

    @Override
    public void load() {
      final ConditionVariable conditionVariable = new ConditionVariable();
      // SDK will call loadUrl on stream player for SDK once manifest uri is available.
      streamPlayerForSdk.setStreamLoadListener(
          (streamUri, subtitles) -> {
            streamManifestUri = Uri.parse(streamUri);
            conditionVariable.open();
          });
      adsLoader.addAdsLoadedListener(this);
      adsLoader.addAdErrorListener(this);
      // We need to inform integrating app about errors within the ads loader
      if (config.applicationAdErrorListener != null) {
        adsLoader.addAdErrorListener(config.applicationAdErrorListener);
      }
      adsLoader.requestStream(request);
      conditionVariable.blockUninterruptible();
    }

    public Uri getStreamUri() {
      checkNotNull(streamManifestUri);
      return streamManifestUri;
    }

    @Nullable
    public StreamManager getStreamManager() {
      return streamManager;
    }

    // AdsLoader.AdsLoadedListener implementation.

    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
      removeAdsLoaderListeners();
      streamManager = event.getStreamManager();
      // We need to inform integrating app about ad events within the stream manager.
      if (config.applicationAdEventListener != null) {
        streamManager.addAdEventListener(config.applicationAdEventListener);
      }
      // We need to inform integrating app about errors within the stream manager.
      if (config.applicationAdErrorListener != null) {
        streamManager.addAdErrorListener(config.applicationAdErrorListener);
      }
      // Init triggers stream initialization which leads to stream manifest uri provided in a
      // callback.
      streamManager.init();
    }

    // AdErrorEvent.AdErrorListener implementation.

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
      removeAdsLoaderListeners();
    }

    /** Cleans up stream manager. */
    public void release() {
      removeAdsLoaderListeners();
      if (streamManager != null) {
        if (config.applicationAdEventListener != null) {
          streamManager.removeAdEventListener(config.applicationAdEventListener);
        }
        // We need to inform integrating app about errors within the stream manager.
        if (config.applicationAdErrorListener != null) {
          streamManager.removeAdErrorListener(config.applicationAdErrorListener);
        }
        streamManager.destroy();
        streamManager = null;
      }
    }

    /** Remove all listeners after ads loader succeeded or errored out. */
    private void removeAdsLoaderListeners() {
      adsLoader.removeAdsLoadedListener(this);
      adsLoader.removeAdErrorListener(this);
      if (config.applicationAdErrorListener != null) {
        adsLoader.removeAdErrorListener(config.applicationAdErrorListener);
      }
    }
  }

  /**
   * Listens to the main exoplayer instance and communicates with IMA sdk to react to sdk callbacks
   * as well as update sdk about exoplayer state.
   */
  private static final class DaiStreamPlayer implements VideoStreamPlayer, Player.Listener {

    private final PlayerProvider playerProvider;
    private final List<VideoStreamPlayer.VideoStreamPlayerCallback> callbacks;

    @Nullable private ImaServerSideDaiMediaSourceFactory.DaiStreamLoadListener streamLoadListener;
    @Nullable private AdState adState;
    @Nullable private Player player;

    public DaiStreamPlayer(PlayerProvider playerProvider) {
      this.playerProvider = playerProvider;
      this.callbacks = new ArrayList<>(/* initialCapacity= */ 1);
    }

    public void mediaSourceCreated(AdState adState) {
      player = playerProvider.getPlayer();
      // Multiple add calls result in just one listener added when listener is the same object.
      player.addListener(this);
      this.adState = adState;
    }

    public void setStreamLoadListener(
        ImaServerSideDaiMediaSourceFactory.DaiStreamLoadListener listener) {
      streamLoadListener = Assertions.checkNotNull(listener);
    }

    public void release() {
      callbacks.clear();
      streamLoadListener = null;
      if (player != null) {
        player.removeListener(this);
      }
    }

    private void triggerContentComplete() {
      for (VideoStreamPlayer.VideoStreamPlayerCallback callback : callbacks) {
        callback.onContentComplete();
      }
    }

    private void triggerUserTextReceived(String userText) {
      for (VideoStreamPlayer.VideoStreamPlayerCallback callback : callbacks) {
        callback.onUserTextReceived(userText);
      }
    }

    private void triggerVolumeChanged(int percentage) {
      for (VideoStreamPlayer.VideoStreamPlayerCallback callback : callbacks) {
        callback.onVolumeChanged(percentage);
      }
    }

    // VideoStreamPlayer interface methods called by the sdk. Some of these methods are no-op,
    // because they do not make sense in the DAI plugin context.

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
      // TODO(gdambrauskas): skippable ad did nothing when clicking skip button, continued play
      // as usual eventhough seek was called with 30s.
      if (player != null) {
        player.seekTo(timeMs);
      }
    }

    // From VolumeProvider
    @Override
    public int getVolume() {
      if (player != null) {
        return (int) Math.floor(player.getVolume() * 100);
      }
      return 0;
    }

    // From ContentProgressProvider
    @Override
    public VideoProgressUpdate getContentProgress() {
      if (adState == null || adState.getAdPlaybackState() == null) {
        return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
      }
      checkNotNull(adState);
      checkNotNull(player);
      long positionMs =
          Util.usToMs(
              ServerSideInsertedAdsUtil.getStreamPositionUs(player, adState.getAdPlaybackState()));
      checkNotNull(adState);
      checkNotNull(player);
      long durationMs =
          Util.usToMs(
              ServerSideInsertedAdsUtil.getStreamDurationUs(player, adState.getAdPlaybackState()));
      return new VideoProgressUpdate(positionMs, durationMs);
    }

    // Listen and handle Exoplayer events we care about.
    // From Player.Listener interface.
    @Override
    public void onMetadata(Metadata metadata) {
      for (int i = 0; i < metadata.length(); i++) {
        Metadata.Entry entry = metadata.get(i);
        if (entry instanceof TextInformationFrame) {
          TextInformationFrame textFrame = (TextInformationFrame) entry;
          if ("TXXX".equals(textFrame.id)) {
            triggerUserTextReceived(textFrame.value);
          }
        } else if (entry instanceof EventMessage) {
          EventMessage eventMessage = (EventMessage) entry;
          String eventMessageValue = new String(eventMessage.messageData, UTF_8);
          triggerUserTextReceived(eventMessageValue);
        }
      }
    }

    // From Player.EventListener
    @Override
    public void onPlaybackStateChanged(int playbackState) {
      switch (playbackState) {
        case Player.STATE_ENDED:
          triggerContentComplete();
          break;
        default:
          break;
      }
    }

    // From Player.Listener
    @Override
    public void onVolumeChanged(float volume) {
      int volumePct = (int) Math.floor(volume * 100);
      triggerVolumeChanged(volumePct);
    }

    /**
     * Returns the playback position in the current content window or ad, in milliseconds, or the
     * prospective position in milliseconds if the {@link Player#getCurrentTimeline() current
     * timeline} is empty.
     */
    public long getCurrentPosition() {
      checkNotNull(player);
      return player.getCurrentPosition();
    }
  }

  /** Media source for IMA streams with inserted ads. */
  private static final class DaiMediaSource extends CompositeMediaSource<Void>
      implements Player.Listener,
          ImaServerSideDaiMediaSourceFactory.AdState,
          AdEvent.AdEventListener {

    private final MediaItem mediaItem;
    private final Player player;
    // Factory used to construct child media source, which is the concrete media source playing the
    // stream.
    private final MediaSourceFactory mediaSourceFactory;
    private final StreamManagerReadyCallback streamManagerReadyCallback;
    private final StreamManagerLoadable streamManagerLoadable;

    private int adBreakIndex = 0;
    private AdPlaybackState adPlaybackState;
    private Object childSourceWindowUid;

    // VOD has a fixed number of ad breaks. Allows to create more ad groups (for live streams) vs
    // adding more ads to the existing ad groups (for VOD).
    @Nullable private long[] knownAdBreaksCuepoints = null;
    @Nullable private ServerSideInsertedAdsMediaSource mediaSource;
    @Nullable private Loader loader;
    @Nullable private IOException loadError;

    public DaiMediaSource(
        MediaItem mediaItem,
        Player player,
        MediaSourceFactory mediaSourceFactory,
        AdsLoader adsLoader,
        ImaUtil.DaiConfiguration config,
        DaiStreamPlayer streamPlayerForSdk) {
      checkNotNull(mediaItem.localConfiguration);
      this.mediaItem = mediaItem;
      this.player = player;
      this.mediaSourceFactory = mediaSourceFactory;
      adPlaybackState =
          new AdPlaybackState(
                  /* adsId= */ new Object(), /* adGroupTimesUs...= */ C.TIME_END_OF_SOURCE)
              .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true);
      childSourceWindowUid = new Object();

      // TODO(gdambrauskas): pass in loadable from outside, simplifies testing.
      checkNotNull(mediaItem.localConfiguration);
      StreamRequest request =
          DaiStreamRequest.fromUri(mediaItem.localConfiguration.uri).getStreamRequest();
      streamManagerLoadable =
          new StreamManagerLoadable(config, adsLoader, request, streamPlayerForSdk);

      streamManagerReadyCallback = new DaiMediaSource.StreamManagerReadyCallback();
      player.addListener(this);
    }

    @Override
    protected void releaseSourceInternal() {
      super.releaseSourceInternal();
      player.removeListener(this);
      StreamManager manager = streamManagerLoadable.getStreamManager();
      checkNotNull(manager);
      if (manager != null) {
        manager.removeAdEventListener(this);
      }
      streamManagerLoadable.release();
    }

    @Override
    public MediaItem getMediaItem() {
      return mediaItem;
    }

    @Override
    public void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
      super.prepareSourceInternal(mediaTransferListener);
      loader = new Loader("DaiMediaSource");
      loader.startLoading(
          streamManagerLoadable, streamManagerReadyCallback, /* defaultMinRetryCount= */ 0);
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
      checkNotNull(mediaSource);
      return mediaSource.createPeriod(id, allocator, startPositionUs);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
      checkNotNull(mediaSource);
      mediaSource.releasePeriod(mediaPeriod);
    }

    @Override
    protected void onChildSourceInfoRefreshed(
        Void id, MediaSource mediaSource, Timeline newTimeline) {
      childSourceWindowUid = newTimeline.getWindow(/* windowIndex= */ 0, new Timeline.Window()).uid;
      refreshSourceInfo(newTimeline);
    }

    @Override
    @CallSuper
    public void maybeThrowSourceInfoRefreshError() throws IOException {
      super.maybeThrowSourceInfoRefreshError();
      if (loadError != null) {
        throw loadError;
      }
    }

    // ImaServerSideDaiMediaSourceFactory.AdState implementation.

    @Override
    public AdPlaybackState getAdPlaybackState() {
      return adPlaybackState;
    }

    @Override
    public void addAdBreaks(long[] adGroupTimesUs) {
      adPlaybackState = new AdPlaybackState(/* adsId= */ new Object(), adGroupTimesUs);
      // Mark all ad breaks as server side inserted.
      for (int i = 0; i < adGroupTimesUs.length; i++) {
        adPlaybackState =
            adPlaybackState.withIsServerSideInserted(
                /* adGroupIndex= */ i, /* isServerSideInserted= */ true);
      }
      checkNotNull(mediaSource);
      mediaSource.setAdPlaybackState(adPlaybackState);
    }

    @Override
    public void handleAdBreakEnded() {
      adBreakIndex++;
    }

    @Override
    public void handleAdSkipped(int adPosition) {
      adPlaybackState = adPlaybackState.withSkippedAd(adBreakIndex, adPosition);
      checkNotNull(mediaSource);
      mediaSource.setAdPlaybackState(adPlaybackState);
      // TODO(gdambrauskas): seek is disabled in exo code when ads are playing, seek does nothing
      // here when we try to seek past ad.
    }

    @Override
    public void handleAdLoaded(boolean postroll, long adStartUs, long adDurationUs) {
      long adEndUs = adStartUs + adDurationUs;
      if (knownAdBreaksCuepoints != null) {
        int adGroupIndex = getAdGroupIndexForKnownCuepoint(adStartUs);
        adPlaybackState =
            ServerSideInsertedAdsUtil.addAdToAdGroup(
                adPlaybackState, adGroupIndex, adStartUs, adEndUs, adDurationUs);
      } else {
        // When number of ad breaks can grow infinitely (live streams), we treat each ad as its own
        // ad break and just keep adding each ad as a new ad break.
        adPlaybackState =
            ServerSideInsertedAdsUtil.addAdGroupToAdPlaybackState(
                adPlaybackState, adStartUs, adEndUs, adDurationUs);
      }
      // if (postroll) {
      // TODO(gdambrauskas): needs testing, not clear what values are expected at the end of
      // the stream for postroll for ad break end. Same as midroll?
      //   adPlaybackState =
      //       ServerSideInsertedAdsUtil.addAdGroupToAdPlaybackState(
      //           adPlaybackState, C.TIME_END_OF_SOURCE, adBreakEndUs, adDurationUs);
      // }
      checkNotNull(mediaSource);
      mediaSource.setAdPlaybackState(adPlaybackState);
    }

    /**
     * Gets ad group index based on ad start time.
     *
     * @param adStartUs Ad start time. IMA SDK returns same ad start time for every ad within a
     *     single ad break.
     * @return The ad group index.
     */
    private int getAdGroupIndexForKnownCuepoint(long adStartUs) {
      int adGroupIndex = 0;
      checkNotNull(knownAdBreaksCuepoints);
      // TODO(gdambrauskas): need to test stream with postroll.
      for (long cuepointUs : knownAdBreaksCuepoints) {
        if (cuepointUs == adStartUs) {
          return adGroupIndex;
        }
        adGroupIndex++;
      }
      return -1;
    }

    // Player.Listener implementation.

    @Override
    public void onPositionDiscontinuity(
        Player.PositionInfo oldPosition,
        Player.PositionInfo newPosition,
        @Player.DiscontinuityReason int reason) {
      // Make sure discontinuity is for our child media source.
      if (!childSourceWindowUid.equals(oldPosition.windowUid)
          || !childSourceWindowUid.equals(newPosition.windowUid)) {
        return;
      }
      if (oldPosition.adGroupIndex != C.INDEX_UNSET && newPosition.adGroupIndex == C.INDEX_UNSET) {
        for (int i = 0; i <= oldPosition.adIndexInAdGroup; i++) {
          if (adPlaybackState.getAdGroup(oldPosition.adGroupIndex).states[i]
              == AdPlaybackState.AD_STATE_SKIPPED) {
            // Ads that were skipped, stay in skipped state.
            continue;
          }
          // Mark ads in old ad groups as played.
          adPlaybackState =
              adPlaybackState.withPlayedAd(oldPosition.adGroupIndex, /* adIndexInAdGroup= */ i);
        }
      }
      checkNotNull(mediaSource);
      mediaSource.setAdPlaybackState(adPlaybackState);
    }

    /** AdEvent.AdEventListener implementation. */
    @Override
    public void onAdEvent(AdEvent event) {
      switch (event.getType()) {
        case SKIPPED:
          // IMA sdk always returns index starting at 1.
          handleAdSkipped(event.getAd().getAdPodInfo().getAdPosition() - 1);
          break;
        case AD_BREAK_ENDED:
          handleAdBreakEnded();
          break;
          // Cuepoints changed event is available only for VOD streams.
        case CUEPOINTS_CHANGED:
          // CUEPOINTS_CHANGED is firing multiple times. For a stream with 2
          // ad breaks, there are 2 cue point change events, before preroll and before the
          // midroll. Store cuepoints only once.
          if (knownAdBreaksCuepoints == null) {
            StreamManager manager = streamManagerLoadable.getStreamManager();
            checkNotNull(manager);
            knownAdBreaksCuepoints = getAdGroupTimesUsForCuePoints(manager.getCuePoints());
            addAdBreaks(knownAdBreaksCuepoints);
          }
          break;
        case LOADED:
          AdPodInfo adPodInfo = event.getAd().getAdPodInfo();

          // This is an ad belonging to a postroll ad break or DAI live stream (live stream does not
          // know entirety of cue points ahead of time).
          boolean postroll = adPodInfo.getPodIndex() == -1;
          long adStartUs = (long) (adPodInfo.getTimeOffset() * C.MICROS_PER_SECOND);
          handleAdLoaded(
              postroll, adStartUs, (long) (event.getAd().getDuration() * C.MICROS_PER_SECOND));
          break;
        default:
          break;
      }
    }

    /** Invoked when stream manager is initialized and has manifest uri. */
    private final class StreamManagerReadyCallback
        implements Loader.Callback<StreamManagerLoadable> {

      @Override
      public void onLoadCompleted(
          StreamManagerLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {
        // We only care to listen to ad events. Errors are already reported to the integrating app
        // and we can't do anything about an error.
        StreamManager manager = loadable.getStreamManager();
        checkNotNull(manager);
        manager.addAdEventListener(DaiMediaSource.this);
        Uri streamUri = loadable.getStreamUri();
        checkNotNull(streamUri);
        MediaSource contentMediaSource =
            mediaSourceFactory.createMediaSource(MediaItem.fromUri(streamUri));
        mediaSource = new ServerSideInsertedAdsMediaSource(contentMediaSource);
        mediaSource.setAdPlaybackState(adPlaybackState);
        prepareChildSource(/* id= */ null, mediaSource);
      }

      @Override
      public void onLoadCanceled(
          StreamManagerLoadable loadable,
          long elapsedRealtimeMs,
          long loadDurationMs,
          boolean released) {
        // Load can only be cancelled by us, so this can't really happen.
        throw new IllegalStateException("Do not cancel loading of IMA stream manager.");
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
  }

  /** @return List of all the cuepoints. */
  @SuppressWarnings("deprecation")
  private static long[] getAdGroupTimesUsForCuePoints(List<CuePoint> cuePoints) {
    if (cuePoints.isEmpty()) {
      return new long[] {0L};
    }

    int count = cuePoints.size();
    long[] adGroupTimesUs = new long[count];
    int adGroupIndex = 0;
    for (CuePoint cuePoint : cuePoints) {
      if (cuePoint.getStartTime() == -1.0) {
        adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE;
      } else {
        adGroupTimesUs[adGroupIndex++] =
            Util.msToUs((long) Math.floor(cuePoint.getStartTime() * 1000d));
      }
    }
    return adGroupTimesUs;
  }
}
