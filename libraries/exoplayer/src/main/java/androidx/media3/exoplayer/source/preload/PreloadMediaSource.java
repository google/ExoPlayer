/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.source.preload;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.source.WrappingMediaSource;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import java.util.Arrays;

/**
 * Preloads a {@link MediaSource} and provides a {@link MediaPeriod} that has data loaded before
 * playback.
 */
@UnstableApi
public final class PreloadMediaSource extends WrappingMediaSource {

  /**
   * Controls preloading of {@link PreloadMediaSource}.
   *
   * <p>The methods are called on the {@link Looper} that is passed when constructing the {@link
   * PreloadMediaSource.Factory}.
   */
  public interface PreloadControl {

    /**
     * Called from {@link PreloadMediaSource} when the {@link Timeline} is refreshed.
     *
     * @param mediaSource The {@link PreloadMediaSource} that has its {@link Timeline} refreshed.
     * @return True if the {@code mediaSource} should continue preloading, false otherwise.
     */
    boolean onTimelineRefreshed(PreloadMediaSource mediaSource);

    /**
     * Called from {@link PreloadMediaSource} when it is prepared.
     *
     * @param mediaSource The {@link PreloadMediaSource} it is prepared.
     * @return True if the {@code mediaSource} should continue preloading, false otherwise.
     */
    boolean onPrepared(PreloadMediaSource mediaSource);

    /**
     * Called from {@link PreloadMediaSource} when it requests to continue loading.
     *
     * @param mediaSource The {@link PreloadMediaSource} that requests to continue loading.
     * @param bufferedPositionUs An estimate of the absolute position in microseconds up to which
     *     data is buffered, or {@link C#TIME_END_OF_SOURCE} if the track is fully buffered.
     */
    boolean onContinueLoadingRequested(PreloadMediaSource mediaSource, long bufferedPositionUs);
  }

  /** Factory for {@link PreloadMediaSource}. */
  public static final class Factory implements MediaSource.Factory {
    private final MediaSource.Factory mediaSourceFactory;
    private final Looper preloadLooper;
    private final Allocator allocator;
    private final TrackSelector trackSelector;
    private final BandwidthMeter bandwidthMeter;
    private final RendererCapabilities[] rendererCapabilities;
    private final PreloadControl preloadControl;

    /**
     * Creates a new factory for {@link PreloadMediaSource}.
     *
     * @param mediaSourceFactory The underlying {@link MediaSource.Factory}.
     * @param preloadControl The {@link PreloadControl} that will control the progress of preloading
     *     the created {@link PreloadMediaSource} instances.
     * @param trackSelector The {@link TrackSelector}. The instance passed should be {@link
     *     TrackSelector#init(TrackSelector.InvalidationListener, BandwidthMeter) initialized}.
     * @param bandwidthMeter The {@link BandwidthMeter}. It should be the same bandwidth meter of
     *     the {@link ExoPlayer} that is injected by {@link
     *     ExoPlayer.Builder#setBandwidthMeter(BandwidthMeter)}.
     * @param rendererCapabilities The array of {@link RendererCapabilities}. It should be derived
     *     from the same {@link RenderersFactory} of the {@link ExoPlayer} that is injected by
     *     {@link ExoPlayer.Builder#setRenderersFactory(RenderersFactory)}.
     * @param allocator The {@link Allocator}. It should be the same allocator of the {@link
     *     ExoPlayer} that is injected by {@link ExoPlayer.Builder#setLoadControl(LoadControl)}.
     * @param preloadLooper The {@link Looper} that will be used for preloading. It should be the
     *     same looper with {@link ExoPlayer.Builder#setPlaybackLooper(Looper)} that will play the
     *     created {@link PreloadMediaSource} instances.
     */
    public Factory(
        MediaSource.Factory mediaSourceFactory,
        PreloadControl preloadControl,
        TrackSelector trackSelector,
        BandwidthMeter bandwidthMeter,
        RendererCapabilities[] rendererCapabilities,
        Allocator allocator,
        Looper preloadLooper) {
      this.mediaSourceFactory = mediaSourceFactory;
      this.preloadControl = preloadControl;
      this.trackSelector = trackSelector;
      this.bandwidthMeter = bandwidthMeter;
      this.rendererCapabilities = Arrays.copyOf(rendererCapabilities, rendererCapabilities.length);
      this.allocator = allocator;
      this.preloadLooper = preloadLooper;
    }

    @Override
    public Factory setCmcdConfigurationFactory(CmcdConfiguration.Factory cmcdConfigurationFactory) {
      this.mediaSourceFactory.setCmcdConfigurationFactory(cmcdConfigurationFactory);
      return this;
    }

    @Override
    public Factory setDrmSessionManagerProvider(
        DrmSessionManagerProvider drmSessionManagerProvider) {
      this.mediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
      return this;
    }

    @Override
    public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.mediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
      return this;
    }

    @Override
    public int[] getSupportedTypes() {
      return this.mediaSourceFactory.getSupportedTypes();
    }

    @Override
    public PreloadMediaSource createMediaSource(MediaItem mediaItem) {
      return new PreloadMediaSource(
          mediaSourceFactory.createMediaSource(mediaItem),
          preloadControl,
          trackSelector,
          bandwidthMeter,
          rendererCapabilities,
          allocator,
          preloadLooper);
    }

    public PreloadMediaSource createMediaSource(MediaSource mediaSource) {
      return new PreloadMediaSource(
          mediaSource,
          preloadControl,
          trackSelector,
          bandwidthMeter,
          rendererCapabilities,
          allocator,
          preloadLooper);
    }
  }

  private static final String TAG = "PreloadMediaSource";

  private final PreloadControl preloadControl;
  private final TrackSelector trackSelector;
  private final BandwidthMeter bandwidthMeter;
  private final RendererCapabilities[] rendererCapabilities;
  private final Allocator allocator;
  private final Handler preloadHandler;
  private boolean preloadCalled;
  private boolean prepareChildSourceCalled;
  private long startPositionUs;
  @Nullable private Timeline timeline;
  @Nullable private Pair<PreloadMediaPeriod, MediaPeriodKey> preloadingMediaPeriodAndKey;
  @Nullable private Pair<PreloadMediaPeriod, MediaPeriodId> playingPreloadedMediaPeriodAndId;

  private PreloadMediaSource(
      MediaSource mediaSource,
      PreloadControl preloadControl,
      TrackSelector trackSelector,
      BandwidthMeter bandwidthMeter,
      RendererCapabilities[] rendererCapabilities,
      Allocator allocator,
      Looper preloadLooper) {
    super(mediaSource);
    this.preloadControl = preloadControl;
    this.trackSelector = trackSelector;
    this.bandwidthMeter = bandwidthMeter;
    this.rendererCapabilities = rendererCapabilities;
    this.allocator = allocator;

    preloadHandler = Util.createHandler(preloadLooper, /* callback= */ null);
    startPositionUs = C.TIME_UNSET;
  }

  /**
   * Preloads the {@link PreloadMediaSource} for an expected start position {@code startPositionUs}.
   *
   * <p>Can be called from any thread.
   *
   * @param startPositionUs The expected starting position in microseconds, or {@link C#TIME_UNSET}
   *     to indicate the default start position.
   */
  public void preload(long startPositionUs) {
    preloadHandler.post(
        () -> {
          preloadCalled = true;
          this.startPositionUs = startPositionUs;
          if (!isUsedByPlayer()) {
            setPlayerId(PlayerId.UNSET); // Set to PlayerId.UNSET as there is no ongoing playback.
            prepareSourceInternal(bandwidthMeter.getTransferListener());
          }
        });
  }

  @Override
  protected void prepareSourceInternal() {
    if (timeline != null) {
      onChildSourceInfoRefreshed(timeline);
    } else if (!prepareChildSourceCalled) {
      prepareChildSourceCalled = true;
      prepareChildSource();
    }
  }

  @Override
  protected void onChildSourceInfoRefreshed(Timeline newTimeline) {
    this.timeline = newTimeline;
    refreshSourceInfo(newTimeline);
    if (isUsedByPlayer() || !preloadControl.onTimelineRefreshed(PreloadMediaSource.this)) {
      return;
    }
    Pair<Object, Long> periodPosition =
        newTimeline.getPeriodPositionUs(
            new Timeline.Window(),
            new Timeline.Period(),
            /* windowIndex= */ 0,
            /* windowPositionUs= */ startPositionUs);
    MediaPeriodId mediaPeriodId = new MediaPeriodId(periodPosition.first);
    PreloadMediaPeriod mediaPeriod =
        PreloadMediaSource.this.createPeriod(mediaPeriodId, allocator, periodPosition.second);
    mediaPeriod.preload(
        new PreloadMediaPeriodCallback(periodPosition.second),
        /* positionUs= */ periodPosition.second);
  }

  @Override
  public PreloadMediaPeriod createPeriod(
      MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaPeriodKey key = new MediaPeriodKey(id, startPositionUs);
    if (preloadingMediaPeriodAndKey != null && key.equals(preloadingMediaPeriodAndKey.second)) {
      PreloadMediaPeriod mediaPeriod = checkNotNull(preloadingMediaPeriodAndKey).first;
      if (isUsedByPlayer()) {
        preloadingMediaPeriodAndKey = null;
        playingPreloadedMediaPeriodAndId = new Pair<>(mediaPeriod, id);
      }
      return mediaPeriod;
    } else if (preloadingMediaPeriodAndKey != null) {
      mediaSource.releasePeriod(checkNotNull(preloadingMediaPeriodAndKey).first.mediaPeriod);
      preloadingMediaPeriodAndKey = null;
    }

    PreloadMediaPeriod mediaPeriod =
        new PreloadMediaPeriod(mediaSource.createPeriod(id, allocator, startPositionUs));
    if (!isUsedByPlayer()) {
      preloadingMediaPeriodAndKey = new Pair<>(mediaPeriod, key);
    }
    return mediaPeriod;
  }

  @Override
  protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(MediaPeriodId mediaPeriodId) {
    if (playingPreloadedMediaPeriodAndId != null
        && mediaPeriodIdEqualsWithoutWindowSequenceNumber(
            mediaPeriodId, checkNotNull(playingPreloadedMediaPeriodAndId).second)) {
      return checkNotNull(playingPreloadedMediaPeriodAndId).second;
    }
    return mediaPeriodId;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    PreloadMediaPeriod preloadMediaPeriod = ((PreloadMediaPeriod) mediaPeriod);
    if (preloadingMediaPeriodAndKey != null
        && preloadMediaPeriod == checkNotNull(preloadingMediaPeriodAndKey).first) {
      preloadingMediaPeriodAndKey = null;
    } else if (playingPreloadedMediaPeriodAndId != null
        && preloadMediaPeriod == checkNotNull(playingPreloadedMediaPeriodAndId).first) {
      playingPreloadedMediaPeriodAndId = null;
    }
    MediaPeriod periodToRelease = preloadMediaPeriod.mediaPeriod;
    mediaSource.releasePeriod(periodToRelease);
  }

  @Override
  protected void releaseSourceInternal() {
    if (!preloadCalled && !isUsedByPlayer()) {
      timeline = null;
      prepareChildSourceCalled = false;
      super.releaseSourceInternal();
    }
  }

  /**
   * Releases the preloaded resources in {@link PreloadMediaSource}.
   *
   * <p>Can be called from any thread.
   */
  public void releasePreloadMediaSource() {
    preloadHandler.post(
        () -> {
          preloadCalled = false;
          startPositionUs = C.TIME_UNSET;
          if (preloadingMediaPeriodAndKey != null) {
            mediaSource.releasePeriod(preloadingMediaPeriodAndKey.first.mediaPeriod);
            preloadingMediaPeriodAndKey = null;
          }
          releaseSourceInternal();
          preloadHandler.removeCallbacksAndMessages(null);
        });
  }

  private class PreloadMediaPeriodCallback implements MediaPeriod.Callback {

    private final long periodStartPositionUs;
    private boolean prepared;

    public PreloadMediaPeriodCallback(long periodStartPositionUs) {
      this.periodStartPositionUs = periodStartPositionUs;
    }

    @Override
    public void onPrepared(MediaPeriod mediaPeriod) {
      prepared = true;
      PreloadMediaPeriod preloadMediaPeriod = (PreloadMediaPeriod) mediaPeriod;
      TrackGroupArray trackGroups = preloadMediaPeriod.getTrackGroups();
      @Nullable TrackSelectorResult trackSelectorResult = null;
      MediaPeriodKey key = checkNotNull(preloadingMediaPeriodAndKey).second;
      try {
        trackSelectorResult =
            trackSelector.selectTracks(
                rendererCapabilities, trackGroups, key.mediaPeriodId, checkNotNull(timeline));
      } catch (ExoPlaybackException e) {
        Log.e(TAG, "Failed to select tracks", e);
      }
      if (trackSelectorResult != null) {
        preloadMediaPeriod.selectTracksForPreloading(
            trackSelectorResult.selections, periodStartPositionUs);
        if (preloadControl.onPrepared(PreloadMediaSource.this)) {
          preloadMediaPeriod.continueLoading(
              new LoadingInfo.Builder().setPlaybackPositionUs(periodStartPositionUs).build());
        }
      }
    }

    @Override
    public void onContinueLoadingRequested(MediaPeriod mediaPeriod) {
      PreloadMediaPeriod preloadMediaPeriod = (PreloadMediaPeriod) mediaPeriod;
      if (!prepared
          || preloadControl.onContinueLoadingRequested(
              PreloadMediaSource.this, preloadMediaPeriod.getBufferedPositionUs())) {
        preloadMediaPeriod.continueLoading(
            new LoadingInfo.Builder().setPlaybackPositionUs(periodStartPositionUs).build());
      }
    }
  }

  /* package */ boolean isUsedByPlayer() {
    return prepareSourceCalled();
  }

  private static boolean mediaPeriodIdEqualsWithoutWindowSequenceNumber(
      MediaPeriodId firstPeriodId, MediaPeriodId secondPeriodId) {
    return firstPeriodId.periodUid.equals(secondPeriodId.periodUid)
        && firstPeriodId.adGroupIndex == secondPeriodId.adGroupIndex
        && firstPeriodId.adIndexInAdGroup == secondPeriodId.adIndexInAdGroup
        && firstPeriodId.nextAdGroupIndex == secondPeriodId.nextAdGroupIndex;
  }

  private static class MediaPeriodKey {

    public final MediaSource.MediaPeriodId mediaPeriodId;
    private final Long startPositionUs;

    public MediaPeriodKey(MediaSource.MediaPeriodId mediaPeriodId, long startPositionUs) {
      this.mediaPeriodId = mediaPeriodId;
      this.startPositionUs = startPositionUs;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof MediaPeriodKey)) {
        return false;
      }
      MediaPeriodKey mediaPeriodKey = (MediaPeriodKey) other;
      // The MediaPeriodId.windowSequenceNumber is intentionally left out of equals to ensure we
      // detect the "same" media even if it's used with a different sequence number.
      return mediaPeriodIdEqualsWithoutWindowSequenceNumber(
              this.mediaPeriodId, mediaPeriodKey.mediaPeriodId)
          && startPositionUs.equals(mediaPeriodKey.startPositionUs);
    }

    @Override
    public int hashCode() {
      // The MediaPeriodId.windowSequenceNumber is intentionally left out of hashCode to ensure we
      // detect the "same" media even if it's used with a different sequence number.
      int result = 17;
      result = 31 * result + mediaPeriodId.periodUid.hashCode();
      result = 31 * result + mediaPeriodId.adGroupIndex;
      result = 31 * result + mediaPeriodId.adIndexInAdGroup;
      result = 31 * result + mediaPeriodId.nextAdGroupIndex;
      result = 31 * result + startPositionUs.intValue();
      return result;
    }
  }
}
