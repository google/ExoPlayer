/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer;

import android.content.Context;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.AuxEffectInfo;
import androidx.media3.common.BasePlayer;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroupArray;
import androidx.media3.common.TrackSelectionArray;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.TracksInfo;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ShuffleOrder;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;
import androidx.media3.exoplayer.video.spherical.CameraMotionListener;
import androidx.media3.extractor.ExtractorsFactory;
import java.util.List;

/** @deprecated Use {@link ExoPlayer} instead. */
@UnstableApi
@Deprecated
public class SimpleExoPlayer extends BasePlayer
    implements ExoPlayer,
        ExoPlayer.AudioComponent,
        ExoPlayer.VideoComponent,
        ExoPlayer.TextComponent,
        ExoPlayer.DeviceComponent {

  /** @deprecated Use {@link ExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static final class Builder {

    private final ExoPlayer.Builder wrappedBuilder;

    /** @deprecated Use {@link ExoPlayer.Builder#Builder(Context)} instead. */
    @Deprecated
    public Builder(Context context) {
      wrappedBuilder = new ExoPlayer.Builder(context);
    }

    /** @deprecated Use {@link ExoPlayer.Builder#Builder(Context, RenderersFactory)} instead. */
    @Deprecated
    public Builder(Context context, RenderersFactory renderersFactory) {
      wrappedBuilder = new ExoPlayer.Builder(context, renderersFactory);
    }

    /**
     * @deprecated Use {@link ExoPlayer.Builder#Builder(Context, MediaSource.Factory)} and {@link
     *     DefaultMediaSourceFactory#DefaultMediaSourceFactory(Context, ExtractorsFactory)} instead.
     */
    @Deprecated
    public Builder(Context context, ExtractorsFactory extractorsFactory) {
      wrappedBuilder =
          new ExoPlayer.Builder(context, new DefaultMediaSourceFactory(context, extractorsFactory));
    }

    /**
     * @deprecated Use {@link ExoPlayer.Builder#Builder(Context, RenderersFactory,
     *     MediaSource.Factory)} and {@link
     *     DefaultMediaSourceFactory#DefaultMediaSourceFactory(Context, ExtractorsFactory)} instead.
     */
    @Deprecated
    public Builder(
        Context context, RenderersFactory renderersFactory, ExtractorsFactory extractorsFactory) {
      wrappedBuilder =
          new ExoPlayer.Builder(
              context, renderersFactory, new DefaultMediaSourceFactory(context, extractorsFactory));
    }

    /**
     * @deprecated Use {@link ExoPlayer.Builder#Builder(Context, RenderersFactory,
     *     MediaSource.Factory, TrackSelector, LoadControl, BandwidthMeter, AnalyticsCollector)}
     *     instead.
     */
    @Deprecated
    public Builder(
        Context context,
        RenderersFactory renderersFactory,
        TrackSelector trackSelector,
        MediaSource.Factory mediaSourceFactory,
        LoadControl loadControl,
        BandwidthMeter bandwidthMeter,
        AnalyticsCollector analyticsCollector) {
      wrappedBuilder =
          new ExoPlayer.Builder(
              context,
              renderersFactory,
              mediaSourceFactory,
              trackSelector,
              loadControl,
              bandwidthMeter,
              analyticsCollector);
    }

    /**
     * @deprecated Use {@link ExoPlayer.Builder#experimentalSetForegroundModeTimeoutMs(long)}
     *     instead.
     */
    @Deprecated
    public Builder experimentalSetForegroundModeTimeoutMs(long timeoutMs) {
      wrappedBuilder.experimentalSetForegroundModeTimeoutMs(timeoutMs);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setTrackSelector(TrackSelector)} instead. */
    @Deprecated
    public Builder setTrackSelector(TrackSelector trackSelector) {
      wrappedBuilder.setTrackSelector(trackSelector);
      return this;
    }

    /**
     * @deprecated Use {@link ExoPlayer.Builder#setMediaSourceFactory(MediaSource.Factory)} instead.
     */
    @Deprecated
    public Builder setMediaSourceFactory(MediaSource.Factory mediaSourceFactory) {
      wrappedBuilder.setMediaSourceFactory(mediaSourceFactory);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setLoadControl(LoadControl)} instead. */
    @Deprecated
    public Builder setLoadControl(LoadControl loadControl) {
      wrappedBuilder.setLoadControl(loadControl);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setBandwidthMeter(BandwidthMeter)} instead. */
    @Deprecated
    public Builder setBandwidthMeter(BandwidthMeter bandwidthMeter) {
      wrappedBuilder.setBandwidthMeter(bandwidthMeter);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setLooper(Looper)} instead. */
    @Deprecated
    public Builder setLooper(Looper looper) {
      wrappedBuilder.setLooper(looper);
      return this;
    }

    /**
     * @deprecated Use {@link ExoPlayer.Builder#setAnalyticsCollector(AnalyticsCollector)} instead.
     */
    @Deprecated
    public Builder setAnalyticsCollector(AnalyticsCollector analyticsCollector) {
      wrappedBuilder.setAnalyticsCollector(analyticsCollector);
      return this;
    }

    /**
     * @deprecated Use {@link ExoPlayer.Builder#setPriorityTaskManager(PriorityTaskManager)}
     *     instead.
     */
    @Deprecated
    public Builder setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
      wrappedBuilder.setPriorityTaskManager(priorityTaskManager);
      return this;
    }

    /**
     * @deprecated Use {@link ExoPlayer.Builder#setAudioAttributes(AudioAttributes, boolean)}
     *     instead.
     */
    @Deprecated
    public Builder setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
      wrappedBuilder.setAudioAttributes(audioAttributes, handleAudioFocus);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setWakeMode(int)} instead. */
    @Deprecated
    public Builder setWakeMode(@C.WakeMode int wakeMode) {
      wrappedBuilder.setWakeMode(wakeMode);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setHandleAudioBecomingNoisy(boolean)} instead. */
    @Deprecated
    public Builder setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
      wrappedBuilder.setHandleAudioBecomingNoisy(handleAudioBecomingNoisy);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setSkipSilenceEnabled(boolean)} instead. */
    @Deprecated
    public Builder setSkipSilenceEnabled(boolean skipSilenceEnabled) {
      wrappedBuilder.setSkipSilenceEnabled(skipSilenceEnabled);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setVideoScalingMode(int)} instead. */
    @Deprecated
    public Builder setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
      wrappedBuilder.setVideoScalingMode(videoScalingMode);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setVideoChangeFrameRateStrategy(int)} instead. */
    @Deprecated
    public Builder setVideoChangeFrameRateStrategy(
        @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy) {
      wrappedBuilder.setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setUseLazyPreparation(boolean)} instead. */
    @Deprecated
    public Builder setUseLazyPreparation(boolean useLazyPreparation) {
      wrappedBuilder.setUseLazyPreparation(useLazyPreparation);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setSeekParameters(SeekParameters)} instead. */
    @Deprecated
    public Builder setSeekParameters(SeekParameters seekParameters) {
      wrappedBuilder.setSeekParameters(seekParameters);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setSeekBackIncrementMs(long)} instead. */
    @Deprecated
    public Builder setSeekBackIncrementMs(@IntRange(from = 1) long seekBackIncrementMs) {
      wrappedBuilder.setSeekBackIncrementMs(seekBackIncrementMs);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setSeekForwardIncrementMs(long)} instead. */
    @Deprecated
    public Builder setSeekForwardIncrementMs(@IntRange(from = 1) long seekForwardIncrementMs) {
      wrappedBuilder.setSeekForwardIncrementMs(seekForwardIncrementMs);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setReleaseTimeoutMs(long)} instead. */
    @Deprecated
    public Builder setReleaseTimeoutMs(long releaseTimeoutMs) {
      wrappedBuilder.setReleaseTimeoutMs(releaseTimeoutMs);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setDetachSurfaceTimeoutMs(long)} instead. */
    @Deprecated
    public Builder setDetachSurfaceTimeoutMs(long detachSurfaceTimeoutMs) {
      wrappedBuilder.setDetachSurfaceTimeoutMs(detachSurfaceTimeoutMs);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setPauseAtEndOfMediaItems(boolean)} instead. */
    @Deprecated
    public Builder setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
      wrappedBuilder.setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems);
      return this;
    }

    /**
     * @deprecated Use {@link
     *     ExoPlayer.Builder#setLivePlaybackSpeedControl(LivePlaybackSpeedControl)} instead.
     */
    @Deprecated
    public Builder setLivePlaybackSpeedControl(LivePlaybackSpeedControl livePlaybackSpeedControl) {
      wrappedBuilder.setLivePlaybackSpeedControl(livePlaybackSpeedControl);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#setClock(Clock)} instead. */
    @Deprecated
    @VisibleForTesting
    public Builder setClock(Clock clock) {
      wrappedBuilder.setClock(clock);
      return this;
    }

    /** @deprecated Use {@link ExoPlayer.Builder#build()} instead. */
    @Deprecated
    public SimpleExoPlayer build() {
      return wrappedBuilder.buildSimpleExoPlayer();
    }
  }

  private final ExoPlayerImpl player;

  /** @deprecated Use the {@link ExoPlayer.Builder}. */
  @Deprecated
  protected SimpleExoPlayer(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      MediaSource.Factory mediaSourceFactory,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      AnalyticsCollector analyticsCollector,
      boolean useLazyPreparation,
      Clock clock,
      Looper applicationLooper) {
    this(
        new ExoPlayer.Builder(
                context,
                renderersFactory,
                mediaSourceFactory,
                trackSelector,
                loadControl,
                bandwidthMeter,
                analyticsCollector)
            .setUseLazyPreparation(useLazyPreparation)
            .setClock(clock)
            .setLooper(applicationLooper));
  }

  /** @param builder The {@link Builder} to obtain all construction parameters. */
  protected SimpleExoPlayer(Builder builder) {
    this(builder.wrappedBuilder);
  }

  /** @param builder The {@link ExoPlayer.Builder} to obtain all construction parameters. */
  /* package */ SimpleExoPlayer(ExoPlayer.Builder builder) {
    player = new ExoPlayerImpl(builder, /* wrappingPlayer= */ this);
  }

  @Override
  public void experimentalSetOffloadSchedulingEnabled(boolean offloadSchedulingEnabled) {
    player.experimentalSetOffloadSchedulingEnabled(offloadSchedulingEnabled);
  }

  @Override
  public boolean experimentalIsSleepingForOffload() {
    return player.experimentalIsSleepingForOffload();
  }

  @Override
  @Nullable
  public AudioComponent getAudioComponent() {
    return this;
  }

  @Override
  @Nullable
  public VideoComponent getVideoComponent() {
    return this;
  }

  @Override
  @Nullable
  public TextComponent getTextComponent() {
    return this;
  }

  @Override
  @Nullable
  public DeviceComponent getDeviceComponent() {
    return this;
  }

  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
    player.setVideoScalingMode(videoScalingMode);
  }

  @Override
  @C.VideoScalingMode
  public int getVideoScalingMode() {
    return player.getVideoScalingMode();
  }

  @Override
  public void setVideoChangeFrameRateStrategy(
      @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy) {
    player.setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy);
  }

  @Override
  @C.VideoChangeFrameRateStrategy
  public int getVideoChangeFrameRateStrategy() {
    return player.getVideoChangeFrameRateStrategy();
  }

  @Override
  public VideoSize getVideoSize() {
    return player.getVideoSize();
  }

  @Override
  public void clearVideoSurface() {
    player.clearVideoSurface();
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    player.clearVideoSurface(surface);
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    player.setVideoSurface(surface);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    player.setVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    player.clearVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    player.setVideoSurfaceView(surfaceView);
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    player.clearVideoSurfaceView(surfaceView);
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    player.setVideoTextureView(textureView);
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    player.clearVideoTextureView(textureView);
  }

  @Override
  public void addAudioOffloadListener(AudioOffloadListener listener) {
    player.addAudioOffloadListener(listener);
  }

  @Override
  public void removeAudioOffloadListener(AudioOffloadListener listener) {
    player.removeAudioOffloadListener(listener);
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    player.setAudioAttributes(audioAttributes, handleAudioFocus);
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    return player.getAudioAttributes();
  }

  @Override
  public void setAudioSessionId(int audioSessionId) {
    player.setAudioSessionId(audioSessionId);
  }

  @Override
  public int getAudioSessionId() {
    return player.getAudioSessionId();
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    player.setAuxEffectInfo(auxEffectInfo);
  }

  @Override
  public void clearAuxEffectInfo() {
    player.clearAuxEffectInfo();
  }

  @Override
  public void setVolume(float volume) {
    player.setVolume(volume);
  }

  @Override
  public float getVolume() {
    return player.getVolume();
  }

  @Override
  public boolean getSkipSilenceEnabled() {
    return player.getSkipSilenceEnabled();
  }

  @Override
  public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {
    player.setSkipSilenceEnabled(skipSilenceEnabled);
  }

  @Override
  public AnalyticsCollector getAnalyticsCollector() {
    return player.getAnalyticsCollector();
  }

  @Override
  public void addAnalyticsListener(AnalyticsListener listener) {
    player.addAnalyticsListener(listener);
  }

  @Override
  public void removeAnalyticsListener(AnalyticsListener listener) {
    player.removeAnalyticsListener(listener);
  }

  @Override
  public void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
    player.setHandleAudioBecomingNoisy(handleAudioBecomingNoisy);
  }

  @Override
  public void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
    player.setPriorityTaskManager(priorityTaskManager);
  }

  @Override
  @Nullable
  public Format getVideoFormat() {
    return player.getVideoFormat();
  }

  @Override
  @Nullable
  public Format getAudioFormat() {
    return player.getAudioFormat();
  }

  @Override
  @Nullable
  public DecoderCounters getVideoDecoderCounters() {
    return player.getVideoDecoderCounters();
  }

  @Override
  @Nullable
  public DecoderCounters getAudioDecoderCounters() {
    return player.getAudioDecoderCounters();
  }

  @Override
  public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    player.setVideoFrameMetadataListener(listener);
  }

  @Override
  public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    player.clearVideoFrameMetadataListener(listener);
  }

  @Override
  public void setCameraMotionListener(CameraMotionListener listener) {
    player.setCameraMotionListener(listener);
  }

  @Override
  public void clearCameraMotionListener(CameraMotionListener listener) {
    player.clearCameraMotionListener(listener);
  }

  @Override
  public List<Cue> getCurrentCues() {
    return player.getCurrentCues();
  }

  // ExoPlayer implementation

  @Override
  public Looper getPlaybackLooper() {
    return player.getPlaybackLooper();
  }

  @Override
  public Looper getApplicationLooper() {
    return player.getApplicationLooper();
  }

  @Override
  public Clock getClock() {
    return player.getClock();
  }

  @Override
  public void addListener(Listener listener) {
    player.addListener(listener);
  }

  @Deprecated
  @Override
  public void addListener(Player.EventListener listener) {
    player.addEventListener(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    player.removeListener(listener);
  }

  @Deprecated
  @Override
  public void removeListener(Player.EventListener listener) {
    player.removeEventListener(listener);
  }

  @Override
  @State
  public int getPlaybackState() {
    return player.getPlaybackState();
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    return player.getPlaybackSuppressionReason();
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlayerError() {
    return player.getPlayerError();
  }

  /** @deprecated Use {@link #prepare()} instead. */
  @Deprecated
  @Override
  @SuppressWarnings("deprecation") // Calling deprecated method.
  public void retry() {
    player.retry();
  }

  @Override
  public Commands getAvailableCommands() {
    return player.getAvailableCommands();
  }

  @Override
  public void prepare() {
    player.prepare();
  }

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource)} and {@link ExoPlayer#prepare()} instead.
   */
  @Deprecated
  @Override
  @SuppressWarnings("deprecation") // Forwarding deprecated method.
  public void prepare(MediaSource mediaSource) {
    player.prepare(mediaSource);
  }

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource, boolean)} and {@link ExoPlayer#prepare()}
   *     instead.
   */
  @Deprecated
  @Override
  @SuppressWarnings("deprecation") // Forwarding deprecated method.
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    player.prepare(mediaSource, resetPosition, resetState);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    player.setMediaItems(mediaItems, resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    player.setMediaItems(mediaItems, startIndex, startPositionMs);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources) {
    player.setMediaSources(mediaSources);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {
    player.setMediaSources(mediaSources, resetPosition);
  }

  @Override
  public void setMediaSources(
      List<MediaSource> mediaSources, int startMediaItemIndex, long startPositionMs) {
    player.setMediaSources(mediaSources, startMediaItemIndex, startPositionMs);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource) {
    player.setMediaSource(mediaSource);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
    player.setMediaSource(mediaSource, resetPosition);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, long startPositionMs) {
    player.setMediaSource(mediaSource, startPositionMs);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    player.addMediaItems(index, mediaItems);
  }

  @Override
  public void addMediaSource(MediaSource mediaSource) {
    player.addMediaSource(mediaSource);
  }

  @Override
  public void addMediaSource(int index, MediaSource mediaSource) {
    player.addMediaSource(index, mediaSource);
  }

  @Override
  public void addMediaSources(List<MediaSource> mediaSources) {
    player.addMediaSources(mediaSources);
  }

  @Override
  public void addMediaSources(int index, List<MediaSource> mediaSources) {
    player.addMediaSources(index, mediaSources);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    player.moveMediaItems(fromIndex, toIndex, newIndex);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    player.removeMediaItems(fromIndex, toIndex);
  }

  @Override
  public void setShuffleOrder(ShuffleOrder shuffleOrder) {
    player.setShuffleOrder(shuffleOrder);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    player.setPlayWhenReady(playWhenReady);
  }

  @Override
  public boolean getPlayWhenReady() {
    return player.getPlayWhenReady();
  }

  @Override
  public void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
    player.setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems);
  }

  @Override
  public boolean getPauseAtEndOfMediaItems() {
    return player.getPauseAtEndOfMediaItems();
  }

  @Override
  public @RepeatMode int getRepeatMode() {
    return player.getRepeatMode();
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    player.setRepeatMode(repeatMode);
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    player.setShuffleModeEnabled(shuffleModeEnabled);
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return player.getShuffleModeEnabled();
  }

  @Override
  public boolean isLoading() {
    return player.isLoading();
  }

  @Override
  public void seekTo(int mediaItemIndex, long positionMs) {
    player.seekTo(mediaItemIndex, positionMs);
  }

  @Override
  public long getSeekBackIncrement() {
    return player.getSeekBackIncrement();
  }

  @Override
  public long getSeekForwardIncrement() {
    return player.getSeekForwardIncrement();
  }

  @Override
  public long getMaxSeekToPreviousPosition() {
    return player.getMaxSeekToPreviousPosition();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    player.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return player.getPlaybackParameters();
  }

  @Override
  public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    player.setSeekParameters(seekParameters);
  }

  @Override
  public SeekParameters getSeekParameters() {
    return player.getSeekParameters();
  }

  @Override
  public void setForegroundMode(boolean foregroundMode) {
    player.setForegroundMode(foregroundMode);
  }

  @Override
  public void stop() {
    player.stop();
  }

  @Deprecated
  @Override
  public void stop(boolean reset) {
    player.stop(reset);
  }

  @Override
  public void release() {
    player.release();
  }

  @Override
  public PlayerMessage createMessage(PlayerMessage.Target target) {
    return player.createMessage(target);
  }

  @Override
  public int getRendererCount() {
    return player.getRendererCount();
  }

  @Override
  public @C.TrackType int getRendererType(int index) {
    return player.getRendererType(index);
  }

  @Override
  public Renderer getRenderer(int index) {
    return player.getRenderer(index);
  }

  @Override
  public TrackSelector getTrackSelector() {
    return player.getTrackSelector();
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return player.getCurrentTrackGroups();
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return player.getCurrentTrackSelections();
  }

  @Override
  public TracksInfo getCurrentTracksInfo() {
    return player.getCurrentTracksInfo();
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    return player.getTrackSelectionParameters();
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    player.setTrackSelectionParameters(parameters);
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    return player.getMediaMetadata();
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    return player.getPlaylistMetadata();
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
    player.setPlaylistMetadata(mediaMetadata);
  }

  @Override
  public Timeline getCurrentTimeline() {
    return player.getCurrentTimeline();
  }

  @Override
  public int getCurrentPeriodIndex() {
    return player.getCurrentPeriodIndex();
  }

  @Override
  public int getCurrentMediaItemIndex() {
    return player.getCurrentMediaItemIndex();
  }

  @Override
  public long getDuration() {
    return player.getDuration();
  }

  @Override
  public long getCurrentPosition() {
    return player.getCurrentPosition();
  }

  @Override
  public long getBufferedPosition() {
    return player.getBufferedPosition();
  }

  @Override
  public long getTotalBufferedDuration() {
    return player.getTotalBufferedDuration();
  }

  @Override
  public boolean isPlayingAd() {
    return player.isPlayingAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return player.getCurrentAdGroupIndex();
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return player.getCurrentAdIndexInAdGroup();
  }

  @Override
  public long getContentPosition() {
    return player.getContentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    return player.getContentBufferedPosition();
  }

  @Deprecated
  @Override
  public void setHandleWakeLock(boolean handleWakeLock) {
    player.setHandleWakeLock(handleWakeLock);
  }

  @Override
  public void setWakeMode(@C.WakeMode int wakeMode) {
    player.setWakeMode(wakeMode);
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    return player.getDeviceInfo();
  }

  @Override
  public int getDeviceVolume() {
    return player.getDeviceVolume();
  }

  @Override
  public boolean isDeviceMuted() {
    return player.isDeviceMuted();
  }

  @Override
  public void setDeviceVolume(int volume) {
    player.setDeviceVolume(volume);
  }

  @Override
  public void increaseDeviceVolume() {
    player.increaseDeviceVolume();
  }

  @Override
  public void decreaseDeviceVolume() {
    player.decreaseDeviceVolume();
  }

  @Override
  public void setDeviceMuted(boolean muted) {
    player.setDeviceMuted(muted);
  }

  /* package */ void setThrowsWhenUsingWrongThread(boolean throwsWhenUsingWrongThread) {
    player.setThrowsWhenUsingWrongThread(throwsWhenUsingWrongThread);
  }
}
