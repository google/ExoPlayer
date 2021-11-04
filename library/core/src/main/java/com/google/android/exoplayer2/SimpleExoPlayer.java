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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO;
import static com.google.android.exoplayer2.C.TRACK_TYPE_CAMERA_MOTION;
import static com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO;
import static com.google.android.exoplayer2.Renderer.MSG_SET_AUDIO_ATTRIBUTES;
import static com.google.android.exoplayer2.Renderer.MSG_SET_AUDIO_SESSION_ID;
import static com.google.android.exoplayer2.Renderer.MSG_SET_AUX_EFFECT_INFO;
import static com.google.android.exoplayer2.Renderer.MSG_SET_CAMERA_MOTION_LISTENER;
import static com.google.android.exoplayer2.Renderer.MSG_SET_CHANGE_FRAME_RATE_STRATEGY;
import static com.google.android.exoplayer2.Renderer.MSG_SET_SCALING_MODE;
import static com.google.android.exoplayer2.Renderer.MSG_SET_SKIP_SILENCE_ENABLED;
import static com.google.android.exoplayer2.Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER;
import static com.google.android.exoplayer2.Renderer.MSG_SET_VIDEO_OUTPUT;
import static com.google.android.exoplayer2.Renderer.MSG_SET_VOLUME;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.Renderer.MessageType;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import com.google.android.exoplayer2.video.spherical.SphericalGLSurfaceView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;

/** @deprecated Use {@link ExoPlayer} instead. */
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
     * @deprecated Use {@link ExoPlayer.Builder#Builder(Context, MediaSourceFactory)} and {@link
     *     DefaultMediaSourceFactory#DefaultMediaSourceFactory(Context, ExtractorsFactory)} instead.
     */
    @Deprecated
    public Builder(Context context, ExtractorsFactory extractorsFactory) {
      wrappedBuilder =
          new ExoPlayer.Builder(context, new DefaultMediaSourceFactory(context, extractorsFactory));
    }

    /**
     * @deprecated Use {@link ExoPlayer.Builder#Builder(Context, RenderersFactory,
     *     MediaSourceFactory)} and {@link
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
     *     MediaSourceFactory, TrackSelector, LoadControl, BandwidthMeter, AnalyticsCollector)}
     *     instead.
     */
    @Deprecated
    public Builder(
        Context context,
        RenderersFactory renderersFactory,
        TrackSelector trackSelector,
        MediaSourceFactory mediaSourceFactory,
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
     * @deprecated Use {@link ExoPlayer.Builder#setMediaSourceFactory(MediaSourceFactory)} instead.
     */
    @Deprecated
    public Builder setMediaSourceFactory(MediaSourceFactory mediaSourceFactory) {
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

  private static final String TAG = "SimpleExoPlayer";

  protected final Renderer[] renderers;

  private final ConditionVariable constructorFinished;
  private final Context applicationContext;
  private final ExoPlayerImpl player;
  private final ComponentListener componentListener;
  private final FrameMetadataListener frameMetadataListener;
  private final CopyOnWriteArraySet<Listener> listeners;
  private final AnalyticsCollector analyticsCollector;
  private final AudioBecomingNoisyManager audioBecomingNoisyManager;
  private final AudioFocusManager audioFocusManager;
  private final StreamVolumeManager streamVolumeManager;
  private final WakeLockManager wakeLockManager;
  private final WifiLockManager wifiLockManager;
  private final long detachSurfaceTimeoutMs;

  @Nullable private Format videoFormat;
  @Nullable private Format audioFormat;
  @Nullable private AudioTrack keepSessionIdAudioTrack;
  @Nullable private Object videoOutput;
  @Nullable private Surface ownedSurface;
  @Nullable private SurfaceHolder surfaceHolder;
  @Nullable private SphericalGLSurfaceView sphericalGLSurfaceView;
  private boolean surfaceHolderSurfaceIsVideoOutput;
  @Nullable private TextureView textureView;
  @C.VideoScalingMode private int videoScalingMode;
  @C.VideoChangeFrameRateStrategy private int videoChangeFrameRateStrategy;
  private int surfaceWidth;
  private int surfaceHeight;
  @Nullable private DecoderCounters videoDecoderCounters;
  @Nullable private DecoderCounters audioDecoderCounters;
  private int audioSessionId;
  private AudioAttributes audioAttributes;
  private float volume;
  private boolean skipSilenceEnabled;
  private List<Cue> currentCues;
  @Nullable private VideoFrameMetadataListener videoFrameMetadataListener;
  @Nullable private CameraMotionListener cameraMotionListener;
  private boolean throwsWhenUsingWrongThread;
  private boolean hasNotifiedFullWrongThreadWarning;
  @Nullable private PriorityTaskManager priorityTaskManager;
  private boolean isPriorityTaskManagerRegistered;
  private boolean playerReleased;
  private DeviceInfo deviceInfo;
  private VideoSize videoSize;

  /** @deprecated Use the {@link ExoPlayer.Builder}. */
  @Deprecated
  @SuppressWarnings("deprecation")
  protected SimpleExoPlayer(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      MediaSourceFactory mediaSourceFactory,
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
  @SuppressWarnings("deprecation")
  /* package */ SimpleExoPlayer(ExoPlayer.Builder builder) {
    constructorFinished = new ConditionVariable();
    try {
      applicationContext = builder.context.getApplicationContext();
      analyticsCollector = builder.analyticsCollectorSupplier.get();
      priorityTaskManager = builder.priorityTaskManager;
      audioAttributes = builder.audioAttributes;
      videoScalingMode = builder.videoScalingMode;
      videoChangeFrameRateStrategy = builder.videoChangeFrameRateStrategy;
      skipSilenceEnabled = builder.skipSilenceEnabled;
      detachSurfaceTimeoutMs = builder.detachSurfaceTimeoutMs;
      componentListener = new ComponentListener();
      frameMetadataListener = new FrameMetadataListener();
      listeners = new CopyOnWriteArraySet<>();
      Handler eventHandler = new Handler(builder.looper);
      renderers =
          builder
              .renderersFactorySupplier
              .get()
              .createRenderers(
                  eventHandler,
                  componentListener,
                  componentListener,
                  componentListener,
                  componentListener);

      // Set initial values.
      volume = 1;
      if (Util.SDK_INT < 21) {
        audioSessionId = initializeKeepSessionIdAudioTrack(C.AUDIO_SESSION_ID_UNSET);
      } else {
        audioSessionId = Util.generateAudioSessionIdV21(applicationContext);
      }
      currentCues = Collections.emptyList();
      throwsWhenUsingWrongThread = true;

      // Build the player and associated objects.
      Commands additionalPermanentAvailableCommands =
          new Commands.Builder()
              .addAll(
                  COMMAND_GET_AUDIO_ATTRIBUTES,
                  COMMAND_GET_VOLUME,
                  COMMAND_GET_DEVICE_VOLUME,
                  COMMAND_SET_VOLUME,
                  COMMAND_SET_DEVICE_VOLUME,
                  COMMAND_ADJUST_DEVICE_VOLUME,
                  COMMAND_SET_VIDEO_SURFACE,
                  COMMAND_GET_TEXT)
              .build();
      player =
          new ExoPlayerImpl(
              renderers,
              builder.trackSelectorSupplier.get(),
              builder.mediaSourceFactorySupplier.get(),
              builder.loadControlSupplier.get(),
              builder.bandwidthMeterSupplier.get(),
              analyticsCollector,
              builder.useLazyPreparation,
              builder.seekParameters,
              builder.seekBackIncrementMs,
              builder.seekForwardIncrementMs,
              builder.livePlaybackSpeedControl,
              builder.releaseTimeoutMs,
              builder.pauseAtEndOfMediaItems,
              builder.clock,
              builder.looper,
              /* wrappingPlayer= */ this,
              additionalPermanentAvailableCommands);
      player.addEventListener(componentListener);
      player.addAudioOffloadListener(componentListener);
      if (builder.foregroundModeTimeoutMs > 0) {
        player.experimentalSetForegroundModeTimeoutMs(builder.foregroundModeTimeoutMs);
      }

      audioBecomingNoisyManager =
          new AudioBecomingNoisyManager(builder.context, eventHandler, componentListener);
      audioBecomingNoisyManager.setEnabled(builder.handleAudioBecomingNoisy);
      audioFocusManager = new AudioFocusManager(builder.context, eventHandler, componentListener);
      audioFocusManager.setAudioAttributes(builder.handleAudioFocus ? audioAttributes : null);
      streamVolumeManager =
          new StreamVolumeManager(builder.context, eventHandler, componentListener);
      streamVolumeManager.setStreamType(Util.getStreamTypeForAudioUsage(audioAttributes.usage));
      wakeLockManager = new WakeLockManager(builder.context);
      wakeLockManager.setEnabled(builder.wakeMode != C.WAKE_MODE_NONE);
      wifiLockManager = new WifiLockManager(builder.context);
      wifiLockManager.setEnabled(builder.wakeMode == C.WAKE_MODE_NETWORK);
      deviceInfo = createDeviceInfo(streamVolumeManager);
      videoSize = VideoSize.UNKNOWN;

      sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
      sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
      sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUDIO_ATTRIBUTES, audioAttributes);
      sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_SCALING_MODE, videoScalingMode);
      sendRendererMessage(
          TRACK_TYPE_VIDEO, MSG_SET_CHANGE_FRAME_RATE_STRATEGY, videoChangeFrameRateStrategy);
      sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_SKIP_SILENCE_ENABLED, skipSilenceEnabled);
      sendRendererMessage(
          TRACK_TYPE_VIDEO, MSG_SET_VIDEO_FRAME_METADATA_LISTENER, frameMetadataListener);
      sendRendererMessage(
          TRACK_TYPE_CAMERA_MOTION, MSG_SET_CAMERA_MOTION_LISTENER, frameMetadataListener);
    } finally {
      constructorFinished.open();
    }
  }

  @Override
  public void experimentalSetOffloadSchedulingEnabled(boolean offloadSchedulingEnabled) {
    verifyApplicationThread();
    player.experimentalSetOffloadSchedulingEnabled(offloadSchedulingEnabled);
  }

  @Override
  public boolean experimentalIsSleepingForOffload() {
    verifyApplicationThread();
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
    verifyApplicationThread();
    this.videoScalingMode = videoScalingMode;
    sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_SCALING_MODE, videoScalingMode);
  }

  @Override
  @C.VideoScalingMode
  public int getVideoScalingMode() {
    return videoScalingMode;
  }

  @Override
  public void setVideoChangeFrameRateStrategy(
      @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy) {
    verifyApplicationThread();
    if (this.videoChangeFrameRateStrategy == videoChangeFrameRateStrategy) {
      return;
    }
    this.videoChangeFrameRateStrategy = videoChangeFrameRateStrategy;
    sendRendererMessage(
        TRACK_TYPE_VIDEO, MSG_SET_CHANGE_FRAME_RATE_STRATEGY, videoChangeFrameRateStrategy);
  }

  @Override
  @C.VideoChangeFrameRateStrategy
  public int getVideoChangeFrameRateStrategy() {
    return videoChangeFrameRateStrategy;
  }

  @Override
  public VideoSize getVideoSize() {
    return videoSize;
  }

  @Override
  public void clearVideoSurface() {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    setVideoOutputInternal(/* videoOutput= */ null);
    maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    if (surface != null && surface == videoOutput) {
      clearVideoSurface();
    }
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    setVideoOutputInternal(surface);
    int newSurfaceSize = surface == null ? 0 : C.LENGTH_UNSET;
    maybeNotifySurfaceSizeChanged(/* width= */ newSurfaceSize, /* height= */ newSurfaceSize);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (surfaceHolder == null) {
      clearVideoSurface();
    } else {
      removeSurfaceCallbacks();
      this.surfaceHolderSurfaceIsVideoOutput = true;
      this.surfaceHolder = surfaceHolder;
      surfaceHolder.addCallback(componentListener);
      Surface surface = surfaceHolder.getSurface();
      if (surface != null && surface.isValid()) {
        setVideoOutputInternal(surface);
        Rect surfaceSize = surfaceHolder.getSurfaceFrame();
        maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
      } else {
        setVideoOutputInternal(/* videoOutput= */ null);
        maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      }
    }
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
      clearVideoSurface();
    }
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    if (surfaceView instanceof VideoDecoderOutputBufferRenderer) {
      removeSurfaceCallbacks();
      setVideoOutputInternal(surfaceView);
      setNonVideoOutputSurfaceHolderInternal(surfaceView.getHolder());
    } else if (surfaceView instanceof SphericalGLSurfaceView) {
      removeSurfaceCallbacks();
      sphericalGLSurfaceView = (SphericalGLSurfaceView) surfaceView;
      player
          .createMessage(frameMetadataListener)
          .setType(FrameMetadataListener.MSG_SET_SPHERICAL_SURFACE_VIEW)
          .setPayload(sphericalGLSurfaceView)
          .send();
      sphericalGLSurfaceView.addVideoSurfaceListener(componentListener);
      setVideoOutputInternal(sphericalGLSurfaceView.getVideoSurface());
      setNonVideoOutputSurfaceHolderInternal(surfaceView.getHolder());
    } else {
      setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
    }
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    clearVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    if (textureView == null) {
      clearVideoSurface();
    } else {
      removeSurfaceCallbacks();
      this.textureView = textureView;
      if (textureView.getSurfaceTextureListener() != null) {
        Log.w(TAG, "Replacing existing SurfaceTextureListener.");
      }
      textureView.setSurfaceTextureListener(componentListener);
      @Nullable
      SurfaceTexture surfaceTexture =
          textureView.isAvailable() ? textureView.getSurfaceTexture() : null;
      if (surfaceTexture == null) {
        setVideoOutputInternal(/* videoOutput= */ null);
        maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      } else {
        setSurfaceTextureInternal(surfaceTexture);
        maybeNotifySurfaceSizeChanged(textureView.getWidth(), textureView.getHeight());
      }
    }
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    if (textureView != null && textureView == this.textureView) {
      clearVideoSurface();
    }
  }

  @Override
  public void addAudioOffloadListener(AudioOffloadListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    player.addAudioOffloadListener(listener);
  }

  @Override
  public void removeAudioOffloadListener(AudioOffloadListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    player.removeAudioOffloadListener(listener);
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    verifyApplicationThread();
    if (playerReleased) {
      return;
    }
    if (!Util.areEqual(this.audioAttributes, audioAttributes)) {
      this.audioAttributes = audioAttributes;
      sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUDIO_ATTRIBUTES, audioAttributes);
      streamVolumeManager.setStreamType(Util.getStreamTypeForAudioUsage(audioAttributes.usage));
      analyticsCollector.onAudioAttributesChanged(audioAttributes);
      // TODO(internal b/187152483): Events should be dispatched via ListenerSet
      for (Listener listener : listeners) {
        listener.onAudioAttributesChanged(audioAttributes);
      }
    }

    audioFocusManager.setAudioAttributes(handleAudioFocus ? audioAttributes : null);
    boolean playWhenReady = getPlayWhenReady();
    @AudioFocusManager.PlayerCommand
    int playerCommand = audioFocusManager.updateAudioFocus(playWhenReady, getPlaybackState());
    updatePlayWhenReady(
        playWhenReady, playerCommand, getPlayWhenReadyChangeReason(playWhenReady, playerCommand));
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    return audioAttributes;
  }

  @Override
  public void setAudioSessionId(int audioSessionId) {
    verifyApplicationThread();
    if (this.audioSessionId == audioSessionId) {
      return;
    }
    if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
      if (Util.SDK_INT < 21) {
        audioSessionId = initializeKeepSessionIdAudioTrack(C.AUDIO_SESSION_ID_UNSET);
      } else {
        audioSessionId = Util.generateAudioSessionIdV21(applicationContext);
      }
    } else if (Util.SDK_INT < 21) {
      // We need to re-initialize keepSessionIdAudioTrack to make sure the session is kept alive for
      // as long as the player is using it.
      initializeKeepSessionIdAudioTrack(audioSessionId);
    }
    this.audioSessionId = audioSessionId;
    sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
    sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
    analyticsCollector.onAudioSessionIdChanged(audioSessionId);
    // TODO(internal b/187152483): Events should be dispatched via ListenerSet
    for (Listener listener : listeners) {
      listener.onAudioSessionIdChanged(audioSessionId);
    }
  }

  @Override
  public int getAudioSessionId() {
    return audioSessionId;
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    verifyApplicationThread();
    sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_AUX_EFFECT_INFO, auxEffectInfo);
  }

  @Override
  public void clearAuxEffectInfo() {
    setAuxEffectInfo(new AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, /* sendLevel= */ 0f));
  }

  @Override
  public void setVolume(float volume) {
    verifyApplicationThread();
    volume = Util.constrainValue(volume, /* min= */ 0, /* max= */ 1);
    if (this.volume == volume) {
      return;
    }
    this.volume = volume;
    sendVolumeToRenderers();
    analyticsCollector.onVolumeChanged(volume);
    // TODO(internal b/187152483): Events should be dispatched via ListenerSet
    for (Listener listener : listeners) {
      listener.onVolumeChanged(volume);
    }
  }

  @Override
  public float getVolume() {
    return volume;
  }

  @Override
  public boolean getSkipSilenceEnabled() {
    return skipSilenceEnabled;
  }

  @Override
  public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {
    verifyApplicationThread();
    if (this.skipSilenceEnabled == skipSilenceEnabled) {
      return;
    }
    this.skipSilenceEnabled = skipSilenceEnabled;
    sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_SKIP_SILENCE_ENABLED, skipSilenceEnabled);
    notifySkipSilenceEnabledChanged();
  }

  @Override
  public AnalyticsCollector getAnalyticsCollector() {
    return analyticsCollector;
  }

  @Override
  public void addAnalyticsListener(AnalyticsListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    checkNotNull(listener);
    analyticsCollector.addListener(listener);
  }

  @Override
  public void removeAnalyticsListener(AnalyticsListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    analyticsCollector.removeListener(listener);
  }

  @Override
  public void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
    verifyApplicationThread();
    if (playerReleased) {
      return;
    }
    audioBecomingNoisyManager.setEnabled(handleAudioBecomingNoisy);
  }

  @Override
  public void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
    verifyApplicationThread();
    if (Util.areEqual(this.priorityTaskManager, priorityTaskManager)) {
      return;
    }
    if (isPriorityTaskManagerRegistered) {
      checkNotNull(this.priorityTaskManager).remove(C.PRIORITY_PLAYBACK);
    }
    if (priorityTaskManager != null && isLoading()) {
      priorityTaskManager.add(C.PRIORITY_PLAYBACK);
      isPriorityTaskManagerRegistered = true;
    } else {
      isPriorityTaskManagerRegistered = false;
    }
    this.priorityTaskManager = priorityTaskManager;
  }

  @Override
  @Nullable
  public Format getVideoFormat() {
    return videoFormat;
  }

  @Override
  @Nullable
  public Format getAudioFormat() {
    return audioFormat;
  }

  @Override
  @Nullable
  public DecoderCounters getVideoDecoderCounters() {
    return videoDecoderCounters;
  }

  @Override
  @Nullable
  public DecoderCounters getAudioDecoderCounters() {
    return audioDecoderCounters;
  }

  @Override
  public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    verifyApplicationThread();
    videoFrameMetadataListener = listener;
    player
        .createMessage(frameMetadataListener)
        .setType(FrameMetadataListener.MSG_SET_VIDEO_FRAME_METADATA_LISTENER)
        .setPayload(listener)
        .send();
  }

  @Override
  public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    verifyApplicationThread();
    if (videoFrameMetadataListener != listener) {
      return;
    }
    player
        .createMessage(frameMetadataListener)
        .setType(FrameMetadataListener.MSG_SET_VIDEO_FRAME_METADATA_LISTENER)
        .setPayload(null)
        .send();
  }

  @Override
  public void setCameraMotionListener(CameraMotionListener listener) {
    verifyApplicationThread();
    cameraMotionListener = listener;
    player
        .createMessage(frameMetadataListener)
        .setType(FrameMetadataListener.MSG_SET_CAMERA_MOTION_LISTENER)
        .setPayload(listener)
        .send();
  }

  @Override
  public void clearCameraMotionListener(CameraMotionListener listener) {
    verifyApplicationThread();
    if (cameraMotionListener != listener) {
      return;
    }
    player
        .createMessage(frameMetadataListener)
        .setType(FrameMetadataListener.MSG_SET_CAMERA_MOTION_LISTENER)
        .setPayload(null)
        .send();
  }

  @Override
  public List<Cue> getCurrentCues() {
    verifyApplicationThread();
    return currentCues;
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
    checkNotNull(listener);
    listeners.add(listener);
    EventListener eventListener = listener;
    addListener(eventListener);
  }

  @Deprecated
  @Override
  public void addListener(Player.EventListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    checkNotNull(listener);
    player.addEventListener(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    checkNotNull(listener);
    listeners.remove(listener);
    EventListener eventListener = listener;
    removeListener(eventListener);
  }

  @Deprecated
  @Override
  public void removeListener(Player.EventListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    player.removeEventListener(listener);
  }

  @Override
  @State
  public int getPlaybackState() {
    verifyApplicationThread();
    return player.getPlaybackState();
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    verifyApplicationThread();
    return player.getPlaybackSuppressionReason();
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlayerError() {
    verifyApplicationThread();
    return player.getPlayerError();
  }

  /** @deprecated Use {@link #prepare()} instead. */
  @Deprecated
  @Override
  public void retry() {
    verifyApplicationThread();
    prepare();
  }

  @Override
  public Commands getAvailableCommands() {
    verifyApplicationThread();
    return player.getAvailableCommands();
  }

  @Override
  public void prepare() {
    verifyApplicationThread();
    boolean playWhenReady = getPlayWhenReady();
    @AudioFocusManager.PlayerCommand
    int playerCommand = audioFocusManager.updateAudioFocus(playWhenReady, Player.STATE_BUFFERING);
    updatePlayWhenReady(
        playWhenReady, playerCommand, getPlayWhenReadyChangeReason(playWhenReady, playerCommand));
    player.prepare();
  }

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource)} and {@link ExoPlayer#prepare()} instead.
   */
  @Deprecated
  @Override
  @SuppressWarnings("deprecation")
  public void prepare(MediaSource mediaSource) {
    prepare(mediaSource, /* resetPosition= */ true, /* resetState= */ true);
  }

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource, boolean)} and {@link ExoPlayer#prepare()}
   *     instead.
   */
  @Deprecated
  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    verifyApplicationThread();
    setMediaSources(Collections.singletonList(mediaSource), resetPosition);
    prepare();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    verifyApplicationThread();
    player.setMediaItems(mediaItems, resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    verifyApplicationThread();
    player.setMediaItems(mediaItems, startIndex, startPositionMs);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources) {
    verifyApplicationThread();
    player.setMediaSources(mediaSources);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {
    verifyApplicationThread();
    player.setMediaSources(mediaSources, resetPosition);
  }

  @Override
  public void setMediaSources(
      List<MediaSource> mediaSources, int startMediaItemIndex, long startPositionMs) {
    verifyApplicationThread();
    player.setMediaSources(mediaSources, startMediaItemIndex, startPositionMs);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource) {
    verifyApplicationThread();
    player.setMediaSource(mediaSource);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
    verifyApplicationThread();
    player.setMediaSource(mediaSource, resetPosition);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, long startPositionMs) {
    verifyApplicationThread();
    player.setMediaSource(mediaSource, startPositionMs);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    verifyApplicationThread();
    player.addMediaItems(index, mediaItems);
  }

  @Override
  public void addMediaSource(MediaSource mediaSource) {
    verifyApplicationThread();
    player.addMediaSource(mediaSource);
  }

  @Override
  public void addMediaSource(int index, MediaSource mediaSource) {
    verifyApplicationThread();
    player.addMediaSource(index, mediaSource);
  }

  @Override
  public void addMediaSources(List<MediaSource> mediaSources) {
    verifyApplicationThread();
    player.addMediaSources(mediaSources);
  }

  @Override
  public void addMediaSources(int index, List<MediaSource> mediaSources) {
    verifyApplicationThread();
    player.addMediaSources(index, mediaSources);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    verifyApplicationThread();
    player.moveMediaItems(fromIndex, toIndex, newIndex);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    verifyApplicationThread();
    player.removeMediaItems(fromIndex, toIndex);
  }

  @Override
  public void setShuffleOrder(ShuffleOrder shuffleOrder) {
    verifyApplicationThread();
    player.setShuffleOrder(shuffleOrder);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThread();
    @AudioFocusManager.PlayerCommand
    int playerCommand = audioFocusManager.updateAudioFocus(playWhenReady, getPlaybackState());
    updatePlayWhenReady(
        playWhenReady, playerCommand, getPlayWhenReadyChangeReason(playWhenReady, playerCommand));
  }

  @Override
  public boolean getPlayWhenReady() {
    verifyApplicationThread();
    return player.getPlayWhenReady();
  }

  @Override
  public void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
    verifyApplicationThread();
    player.setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems);
  }

  @Override
  public boolean getPauseAtEndOfMediaItems() {
    verifyApplicationThread();
    return player.getPauseAtEndOfMediaItems();
  }

  @Override
  public @RepeatMode int getRepeatMode() {
    verifyApplicationThread();
    return player.getRepeatMode();
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    verifyApplicationThread();
    player.setRepeatMode(repeatMode);
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    verifyApplicationThread();
    player.setShuffleModeEnabled(shuffleModeEnabled);
  }

  @Override
  public boolean getShuffleModeEnabled() {
    verifyApplicationThread();
    return player.getShuffleModeEnabled();
  }

  @Override
  public boolean isLoading() {
    verifyApplicationThread();
    return player.isLoading();
  }

  @Override
  public void seekTo(int mediaItemIndex, long positionMs) {
    verifyApplicationThread();
    analyticsCollector.notifySeekStarted();
    player.seekTo(mediaItemIndex, positionMs);
  }

  @Override
  public long getSeekBackIncrement() {
    verifyApplicationThread();
    return player.getSeekBackIncrement();
  }

  @Override
  public long getSeekForwardIncrement() {
    verifyApplicationThread();
    return player.getSeekForwardIncrement();
  }

  @Override
  public long getMaxSeekToPreviousPosition() {
    verifyApplicationThread();
    return player.getMaxSeekToPreviousPosition();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    verifyApplicationThread();
    player.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    verifyApplicationThread();
    return player.getPlaybackParameters();
  }

  @Override
  public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    verifyApplicationThread();
    player.setSeekParameters(seekParameters);
  }

  @Override
  public SeekParameters getSeekParameters() {
    verifyApplicationThread();
    return player.getSeekParameters();
  }

  @Override
  public void setForegroundMode(boolean foregroundMode) {
    verifyApplicationThread();
    player.setForegroundMode(foregroundMode);
  }

  @Override
  public void stop() {
    stop(/* reset= */ false);
  }

  @Deprecated
  @Override
  public void stop(boolean reset) {
    verifyApplicationThread();
    audioFocusManager.updateAudioFocus(getPlayWhenReady(), Player.STATE_IDLE);
    player.stop(reset);
    currentCues = Collections.emptyList();
  }

  @Override
  public void release() {
    verifyApplicationThread();
    if (Util.SDK_INT < 21 && keepSessionIdAudioTrack != null) {
      keepSessionIdAudioTrack.release();
      keepSessionIdAudioTrack = null;
    }
    audioBecomingNoisyManager.setEnabled(false);
    streamVolumeManager.release();
    wakeLockManager.setStayAwake(false);
    wifiLockManager.setStayAwake(false);
    audioFocusManager.release();
    player.release();
    analyticsCollector.release();
    removeSurfaceCallbacks();
    if (ownedSurface != null) {
      ownedSurface.release();
      ownedSurface = null;
    }
    if (isPriorityTaskManagerRegistered) {
      checkNotNull(priorityTaskManager).remove(C.PRIORITY_PLAYBACK);
      isPriorityTaskManagerRegistered = false;
    }
    currentCues = Collections.emptyList();
    playerReleased = true;
  }

  @Override
  public PlayerMessage createMessage(PlayerMessage.Target target) {
    verifyApplicationThread();
    return player.createMessage(target);
  }

  @Override
  public int getRendererCount() {
    verifyApplicationThread();
    return player.getRendererCount();
  }

  @Override
  public @C.TrackType int getRendererType(int index) {
    verifyApplicationThread();
    return player.getRendererType(index);
  }

  @Override
  @Nullable
  public TrackSelector getTrackSelector() {
    verifyApplicationThread();
    return player.getTrackSelector();
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    verifyApplicationThread();
    return player.getCurrentTrackGroups();
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    verifyApplicationThread();
    return player.getCurrentTrackSelections();
  }

  @Override
  public TracksInfo getCurrentTracksInfo() {
    verifyApplicationThread();
    return player.getCurrentTracksInfo();
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    verifyApplicationThread();
    return player.getTrackSelectionParameters();
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    verifyApplicationThread();
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
    verifyApplicationThread();
    return player.getCurrentTimeline();
  }

  @Override
  public int getCurrentPeriodIndex() {
    verifyApplicationThread();
    return player.getCurrentPeriodIndex();
  }

  @Override
  public int getCurrentMediaItemIndex() {
    verifyApplicationThread();
    return player.getCurrentMediaItemIndex();
  }

  @Override
  public long getDuration() {
    verifyApplicationThread();
    return player.getDuration();
  }

  @Override
  public long getCurrentPosition() {
    verifyApplicationThread();
    return player.getCurrentPosition();
  }

  @Override
  public long getBufferedPosition() {
    verifyApplicationThread();
    return player.getBufferedPosition();
  }

  @Override
  public long getTotalBufferedDuration() {
    verifyApplicationThread();
    return player.getTotalBufferedDuration();
  }

  @Override
  public boolean isPlayingAd() {
    verifyApplicationThread();
    return player.isPlayingAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    verifyApplicationThread();
    return player.getCurrentAdGroupIndex();
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    verifyApplicationThread();
    return player.getCurrentAdIndexInAdGroup();
  }

  @Override
  public long getContentPosition() {
    verifyApplicationThread();
    return player.getContentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    verifyApplicationThread();
    return player.getContentBufferedPosition();
  }

  @Deprecated
  @Override
  public void setHandleWakeLock(boolean handleWakeLock) {
    setWakeMode(handleWakeLock ? C.WAKE_MODE_LOCAL : C.WAKE_MODE_NONE);
  }

  @Override
  public void setWakeMode(@C.WakeMode int wakeMode) {
    verifyApplicationThread();
    switch (wakeMode) {
      case C.WAKE_MODE_NONE:
        wakeLockManager.setEnabled(false);
        wifiLockManager.setEnabled(false);
        break;
      case C.WAKE_MODE_LOCAL:
        wakeLockManager.setEnabled(true);
        wifiLockManager.setEnabled(false);
        break;
      case C.WAKE_MODE_NETWORK:
        wakeLockManager.setEnabled(true);
        wifiLockManager.setEnabled(true);
        break;
      default:
        break;
    }
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    verifyApplicationThread();
    return deviceInfo;
  }

  @Override
  public int getDeviceVolume() {
    verifyApplicationThread();
    return streamVolumeManager.getVolume();
  }

  @Override
  public boolean isDeviceMuted() {
    verifyApplicationThread();
    return streamVolumeManager.isMuted();
  }

  @Override
  public void setDeviceVolume(int volume) {
    verifyApplicationThread();
    streamVolumeManager.setVolume(volume);
  }

  @Override
  public void increaseDeviceVolume() {
    verifyApplicationThread();
    streamVolumeManager.increaseVolume();
  }

  @Override
  public void decreaseDeviceVolume() {
    verifyApplicationThread();
    streamVolumeManager.decreaseVolume();
  }

  @Override
  public void setDeviceMuted(boolean muted) {
    verifyApplicationThread();
    streamVolumeManager.setMuted(muted);
  }

  @Deprecated
  @Override
  public void setThrowsWhenUsingWrongThread(boolean throwsWhenUsingWrongThread) {
    this.throwsWhenUsingWrongThread = throwsWhenUsingWrongThread;
  }

  // Internal methods.

  private void removeSurfaceCallbacks() {
    if (sphericalGLSurfaceView != null) {
      player
          .createMessage(frameMetadataListener)
          .setType(FrameMetadataListener.MSG_SET_SPHERICAL_SURFACE_VIEW)
          .setPayload(null)
          .send();
      sphericalGLSurfaceView.removeVideoSurfaceListener(componentListener);
      sphericalGLSurfaceView = null;
    }
    if (textureView != null) {
      if (textureView.getSurfaceTextureListener() != componentListener) {
        Log.w(TAG, "SurfaceTextureListener already unset or replaced.");
      } else {
        textureView.setSurfaceTextureListener(null);
      }
      textureView = null;
    }
    if (surfaceHolder != null) {
      surfaceHolder.removeCallback(componentListener);
      surfaceHolder = null;
    }
  }

  private void setSurfaceTextureInternal(SurfaceTexture surfaceTexture) {
    Surface surface = new Surface(surfaceTexture);
    setVideoOutputInternal(surface);
    ownedSurface = surface;
  }

  private void setVideoOutputInternal(@Nullable Object videoOutput) {
    // Note: We don't turn this method into a no-op if the output is being replaced with itself so
    // as to ensure onRenderedFirstFrame callbacks are still called in this case.
    List<PlayerMessage> messages = new ArrayList<>();
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == TRACK_TYPE_VIDEO) {
        messages.add(
            player
                .createMessage(renderer)
                .setType(MSG_SET_VIDEO_OUTPUT)
                .setPayload(videoOutput)
                .send());
      }
    }
    boolean messageDeliveryTimedOut = false;
    if (this.videoOutput != null && this.videoOutput != videoOutput) {
      // We're replacing an output. Block to ensure that this output will not be accessed by the
      // renderers after this method returns.
      try {
        for (PlayerMessage message : messages) {
          message.blockUntilDelivered(detachSurfaceTimeoutMs);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (TimeoutException e) {
        messageDeliveryTimedOut = true;
      }
      if (this.videoOutput == ownedSurface) {
        // We're replacing a surface that we are responsible for releasing.
        ownedSurface.release();
        ownedSurface = null;
      }
    }
    this.videoOutput = videoOutput;
    if (messageDeliveryTimedOut) {
      player.stop(
          /* reset= */ false,
          ExoPlaybackException.createForUnexpected(
              new ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE),
              PlaybackException.ERROR_CODE_TIMEOUT));
    }
  }

  /**
   * Sets the holder of the surface that will be displayed to the user, but which should
   * <em>not</em> be the output for video renderers. This case occurs when video frames need to be
   * rendered to an intermediate surface (which is not the one held by the provided holder).
   *
   * @param nonVideoOutputSurfaceHolder The holder of the surface that will eventually be displayed
   *     to the user.
   */
  private void setNonVideoOutputSurfaceHolderInternal(SurfaceHolder nonVideoOutputSurfaceHolder) {
    // Although we won't use the view's surface directly as the video output, still use the holder
    // to query the surface size, to be informed in changes to the size via componentListener, and
    // for equality checking in clearVideoSurfaceHolder.
    surfaceHolderSurfaceIsVideoOutput = false;
    surfaceHolder = nonVideoOutputSurfaceHolder;
    surfaceHolder.addCallback(componentListener);
    Surface surface = surfaceHolder.getSurface();
    if (surface != null && surface.isValid()) {
      Rect surfaceSize = surfaceHolder.getSurfaceFrame();
      maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
    } else {
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    }
  }

  private void maybeNotifySurfaceSizeChanged(int width, int height) {
    if (width != surfaceWidth || height != surfaceHeight) {
      surfaceWidth = width;
      surfaceHeight = height;
      analyticsCollector.onSurfaceSizeChanged(width, height);
      // TODO(internal b/187152483): Events should be dispatched via ListenerSet
      for (Listener listener : listeners) {
        listener.onSurfaceSizeChanged(width, height);
      }
    }
  }

  private void sendVolumeToRenderers() {
    float scaledVolume = volume * audioFocusManager.getVolumeMultiplier();
    sendRendererMessage(TRACK_TYPE_AUDIO, MSG_SET_VOLUME, scaledVolume);
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  private void notifySkipSilenceEnabledChanged() {
    analyticsCollector.onSkipSilenceEnabledChanged(skipSilenceEnabled);
    // TODO(internal b/187152483): Events should be dispatched via ListenerSet
    for (Listener listener : listeners) {
      listener.onSkipSilenceEnabledChanged(skipSilenceEnabled);
    }
  }

  private void updatePlayWhenReady(
      boolean playWhenReady,
      @AudioFocusManager.PlayerCommand int playerCommand,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
    playWhenReady = playWhenReady && playerCommand != AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY;
    @PlaybackSuppressionReason
    int playbackSuppressionReason =
        playWhenReady && playerCommand != AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY
            ? Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
            : Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    player.setPlayWhenReady(playWhenReady, playbackSuppressionReason, playWhenReadyChangeReason);
  }

  private void updateWakeAndWifiLock() {
    @State int playbackState = getPlaybackState();
    switch (playbackState) {
      case Player.STATE_READY:
      case Player.STATE_BUFFERING:
        boolean isSleeping = experimentalIsSleepingForOffload();
        wakeLockManager.setStayAwake(getPlayWhenReady() && !isSleeping);
        // The wifi lock is not released while sleeping to avoid interrupting downloads.
        wifiLockManager.setStayAwake(getPlayWhenReady());
        break;
      case Player.STATE_ENDED:
      case Player.STATE_IDLE:
        wakeLockManager.setStayAwake(false);
        wifiLockManager.setStayAwake(false);
        break;
      default:
        throw new IllegalStateException();
    }
  }

  private void verifyApplicationThread() {
    // The constructor may be executed on a background thread. Wait with accessing the player from
    // the app thread until the constructor finished executing.
    constructorFinished.blockUninterruptible();
    if (Thread.currentThread() != getApplicationLooper().getThread()) {
      String message =
          Util.formatInvariant(
              "Player is accessed on the wrong thread.\n"
                  + "Current thread: '%s'\n"
                  + "Expected thread: '%s'\n"
                  + "See https://exoplayer.dev/issues/player-accessed-on-wrong-thread",
              Thread.currentThread().getName(), getApplicationLooper().getThread().getName());
      if (throwsWhenUsingWrongThread) {
        throw new IllegalStateException(message);
      }
      Log.w(TAG, message, hasNotifiedFullWrongThreadWarning ? null : new IllegalStateException());
      hasNotifiedFullWrongThreadWarning = true;
    }
  }

  private void sendRendererMessage(
      @C.TrackType int trackType, int messageType, @Nullable Object payload) {
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == trackType) {
        player.createMessage(renderer).setType(messageType).setPayload(payload).send();
      }
    }
  }

  /**
   * Initializes {@link #keepSessionIdAudioTrack} to keep an audio session ID alive. If the audio
   * session ID is {@link C#AUDIO_SESSION_ID_UNSET} then a new audio session ID is generated.
   *
   * <p>Use of this method is only required on API level 21 and earlier.
   *
   * @param audioSessionId The audio session ID, or {@link C#AUDIO_SESSION_ID_UNSET} to generate a
   *     new one.
   * @return The audio session ID.
   */
  private int initializeKeepSessionIdAudioTrack(int audioSessionId) {
    if (keepSessionIdAudioTrack != null
        && keepSessionIdAudioTrack.getAudioSessionId() != audioSessionId) {
      keepSessionIdAudioTrack.release();
      keepSessionIdAudioTrack = null;
    }
    if (keepSessionIdAudioTrack == null) {
      int sampleRate = 4000; // Minimum sample rate supported by the platform.
      int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
      @C.PcmEncoding int encoding = C.ENCODING_PCM_16BIT;
      int bufferSize = 2; // Use a two byte buffer, as it is not actually used for playback.
      keepSessionIdAudioTrack =
          new AudioTrack(
              C.STREAM_TYPE_DEFAULT,
              sampleRate,
              channelConfig,
              encoding,
              bufferSize,
              AudioTrack.MODE_STATIC,
              audioSessionId);
    }
    return keepSessionIdAudioTrack.getAudioSessionId();
  }

  private static DeviceInfo createDeviceInfo(StreamVolumeManager streamVolumeManager) {
    return new DeviceInfo(
        DeviceInfo.PLAYBACK_TYPE_LOCAL,
        streamVolumeManager.getMinVolume(),
        streamVolumeManager.getMaxVolume());
  }

  private static int getPlayWhenReadyChangeReason(boolean playWhenReady, int playerCommand) {
    return playWhenReady && playerCommand != AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY
        ? PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
        : PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
  }

  private final class ComponentListener
      implements VideoRendererEventListener,
          AudioRendererEventListener,
          TextOutput,
          MetadataOutput,
          SurfaceHolder.Callback,
          TextureView.SurfaceTextureListener,
          SphericalGLSurfaceView.VideoSurfaceListener,
          AudioFocusManager.PlayerControl,
          AudioBecomingNoisyManager.EventListener,
          StreamVolumeManager.Listener,
          Player.EventListener,
          AudioOffloadListener {

    // VideoRendererEventListener implementation

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
      videoDecoderCounters = counters;
      analyticsCollector.onVideoEnabled(counters);
    }

    @Override
    public void onVideoDecoderInitialized(
        String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      analyticsCollector.onVideoDecoderInitialized(
          decoderName, initializedTimestampMs, initializationDurationMs);
    }

    @Override
    public void onVideoInputFormatChanged(
        Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
      videoFormat = format;
      analyticsCollector.onVideoInputFormatChanged(format, decoderReuseEvaluation);
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
      analyticsCollector.onDroppedFrames(count, elapsed);
    }

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
      SimpleExoPlayer.this.videoSize = videoSize;
      analyticsCollector.onVideoSizeChanged(videoSize);
      // TODO(internal b/187152483): Events should be dispatched via ListenerSet
      for (Listener listener : listeners) {
        listener.onVideoSizeChanged(videoSize);
      }
    }

    @Override
    public void onRenderedFirstFrame(Object output, long renderTimeMs) {
      analyticsCollector.onRenderedFirstFrame(output, renderTimeMs);
      if (videoOutput == output) {
        // TODO(internal b/187152483): Events should be dispatched via ListenerSet
        for (Listener listener : listeners) {
          listener.onRenderedFirstFrame();
        }
      }
    }

    @Override
    public void onVideoDecoderReleased(String decoderName) {
      analyticsCollector.onVideoDecoderReleased(decoderName);
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
      analyticsCollector.onVideoDisabled(counters);
      videoFormat = null;
      videoDecoderCounters = null;
    }

    @Override
    public void onVideoFrameProcessingOffset(long totalProcessingOffsetUs, int frameCount) {
      analyticsCollector.onVideoFrameProcessingOffset(totalProcessingOffsetUs, frameCount);
    }

    @Override
    public void onVideoCodecError(Exception videoCodecError) {
      analyticsCollector.onVideoCodecError(videoCodecError);
    }

    // AudioRendererEventListener implementation

    @Override
    public void onAudioEnabled(DecoderCounters counters) {
      audioDecoderCounters = counters;
      analyticsCollector.onAudioEnabled(counters);
    }

    @Override
    public void onAudioDecoderInitialized(
        String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      analyticsCollector.onAudioDecoderInitialized(
          decoderName, initializedTimestampMs, initializationDurationMs);
    }

    @Override
    public void onAudioInputFormatChanged(
        Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
      audioFormat = format;
      analyticsCollector.onAudioInputFormatChanged(format, decoderReuseEvaluation);
    }

    @Override
    public void onAudioPositionAdvancing(long playoutStartSystemTimeMs) {
      analyticsCollector.onAudioPositionAdvancing(playoutStartSystemTimeMs);
    }

    @Override
    public void onAudioUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      analyticsCollector.onAudioUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }

    @Override
    public void onAudioDecoderReleased(String decoderName) {
      analyticsCollector.onAudioDecoderReleased(decoderName);
    }

    @Override
    public void onAudioDisabled(DecoderCounters counters) {
      analyticsCollector.onAudioDisabled(counters);
      audioFormat = null;
      audioDecoderCounters = null;
    }

    @Override
    public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
      if (SimpleExoPlayer.this.skipSilenceEnabled == skipSilenceEnabled) {
        return;
      }
      SimpleExoPlayer.this.skipSilenceEnabled = skipSilenceEnabled;
      notifySkipSilenceEnabledChanged();
    }

    @Override
    public void onAudioSinkError(Exception audioSinkError) {
      analyticsCollector.onAudioSinkError(audioSinkError);
    }

    @Override
    public void onAudioCodecError(Exception audioCodecError) {
      analyticsCollector.onAudioCodecError(audioCodecError);
    }

    // TextOutput implementation

    @Override
    public void onCues(List<Cue> cues) {
      currentCues = cues;
      // TODO(internal b/187152483): Events should be dispatched via ListenerSet
      for (Listener listeners : listeners) {
        listeners.onCues(cues);
      }
    }

    // MetadataOutput implementation

    @Override
    public void onMetadata(Metadata metadata) {
      analyticsCollector.onMetadata(metadata);
      player.onMetadata(metadata);
      // TODO(internal b/187152483): Events should be dispatched via ListenerSet
      for (Listener listener : listeners) {
        listener.onMetadata(metadata);
      }
    }

    // SurfaceHolder.Callback implementation

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      if (surfaceHolderSurfaceIsVideoOutput) {
        setVideoOutputInternal(holder.getSurface());
      }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      if (surfaceHolderSurfaceIsVideoOutput) {
        setVideoOutputInternal(/* videoOutput= */ null);
      }
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    }

    // TextureView.SurfaceTextureListener implementation

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
      setSurfaceTextureInternal(surfaceTexture);
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
      setVideoOutputInternal(/* videoOutput= */ null);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
      // Do nothing.
    }

    // SphericalGLSurfaceView.VideoSurfaceListener

    @Override
    public void onVideoSurfaceCreated(Surface surface) {
      setVideoOutputInternal(surface);
    }

    @Override
    public void onVideoSurfaceDestroyed(Surface surface) {
      setVideoOutputInternal(/* videoOutput= */ null);
    }

    // AudioFocusManager.PlayerControl implementation

    @Override
    public void setVolumeMultiplier(float volumeMultiplier) {
      sendVolumeToRenderers();
    }

    @Override
    public void executePlayerCommand(@AudioFocusManager.PlayerCommand int playerCommand) {
      boolean playWhenReady = getPlayWhenReady();
      updatePlayWhenReady(
          playWhenReady, playerCommand, getPlayWhenReadyChangeReason(playWhenReady, playerCommand));
    }

    // AudioBecomingNoisyManager.EventListener implementation.

    @Override
    public void onAudioBecomingNoisy() {
      updatePlayWhenReady(
          /* playWhenReady= */ false,
          AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY,
          Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY);
    }

    // StreamVolumeManager.Listener implementation.

    @Override
    public void onStreamTypeChanged(@C.StreamType int streamType) {
      DeviceInfo deviceInfo = createDeviceInfo(streamVolumeManager);
      if (!deviceInfo.equals(SimpleExoPlayer.this.deviceInfo)) {
        SimpleExoPlayer.this.deviceInfo = deviceInfo;
        // TODO(internal b/187152483): Events should be dispatched via ListenerSet
        for (Listener listener : listeners) {
          listener.onDeviceInfoChanged(deviceInfo);
        }
      }
    }

    @Override
    public void onStreamVolumeChanged(int streamVolume, boolean streamMuted) {
      // TODO(internal b/187152483): Events should be dispatched via ListenerSet
      for (Listener listener : listeners) {
        listener.onDeviceVolumeChanged(streamVolume, streamMuted);
      }
    }

    // Player.EventListener implementation.

    @Override
    public void onIsLoadingChanged(boolean isLoading) {
      if (priorityTaskManager != null) {
        if (isLoading && !isPriorityTaskManagerRegistered) {
          priorityTaskManager.add(C.PRIORITY_PLAYBACK);
          isPriorityTaskManagerRegistered = true;
        } else if (!isLoading && isPriorityTaskManagerRegistered) {
          priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
          isPriorityTaskManagerRegistered = false;
        }
      }
    }

    @Override
    public void onPlaybackStateChanged(@State int playbackState) {
      updateWakeAndWifiLock();
    }

    @Override
    public void onPlayWhenReadyChanged(
        boolean playWhenReady, @PlayWhenReadyChangeReason int reason) {
      updateWakeAndWifiLock();
    }

    // Player.AudioOffloadListener implementation.

    @Override
    public void onExperimentalSleepingForOffloadChanged(boolean sleepingForOffload) {
      updateWakeAndWifiLock();
    }
  }

  /** Listeners that are called on the playback thread. */
  private static final class FrameMetadataListener
      implements VideoFrameMetadataListener, CameraMotionListener, PlayerMessage.Target {

    @MessageType
    public static final int MSG_SET_VIDEO_FRAME_METADATA_LISTENER =
        Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER;

    @MessageType
    public static final int MSG_SET_CAMERA_MOTION_LISTENER =
        Renderer.MSG_SET_CAMERA_MOTION_LISTENER;

    @MessageType public static final int MSG_SET_SPHERICAL_SURFACE_VIEW = Renderer.MSG_CUSTOM_BASE;

    @Nullable private VideoFrameMetadataListener videoFrameMetadataListener;
    @Nullable private CameraMotionListener cameraMotionListener;
    @Nullable private VideoFrameMetadataListener internalVideoFrameMetadataListener;
    @Nullable private CameraMotionListener internalCameraMotionListener;

    @Override
    public void handleMessage(@MessageType int messageType, @Nullable Object message) {
      switch (messageType) {
        case MSG_SET_VIDEO_FRAME_METADATA_LISTENER:
          videoFrameMetadataListener = (VideoFrameMetadataListener) message;
          break;
        case MSG_SET_CAMERA_MOTION_LISTENER:
          cameraMotionListener = (CameraMotionListener) message;
          break;
        case MSG_SET_SPHERICAL_SURFACE_VIEW:
          @Nullable SphericalGLSurfaceView surfaceView = (SphericalGLSurfaceView) message;
          if (surfaceView == null) {
            internalVideoFrameMetadataListener = null;
            internalCameraMotionListener = null;
          } else {
            internalVideoFrameMetadataListener = surfaceView.getVideoFrameMetadataListener();
            internalCameraMotionListener = surfaceView.getCameraMotionListener();
          }
          break;
        case Renderer.MSG_SET_AUDIO_ATTRIBUTES:
        case Renderer.MSG_SET_AUDIO_SESSION_ID:
        case Renderer.MSG_SET_AUX_EFFECT_INFO:
        case Renderer.MSG_SET_CHANGE_FRAME_RATE_STRATEGY:
        case Renderer.MSG_SET_SCALING_MODE:
        case Renderer.MSG_SET_SKIP_SILENCE_ENABLED:
        case Renderer.MSG_SET_VIDEO_OUTPUT:
        case Renderer.MSG_SET_VOLUME:
        case Renderer.MSG_SET_WAKEUP_LISTENER:
        default:
          break;
      }
    }

    // VideoFrameMetadataListener

    @Override
    public void onVideoFrameAboutToBeRendered(
        long presentationTimeUs,
        long releaseTimeNs,
        Format format,
        @Nullable MediaFormat mediaFormat) {
      if (internalVideoFrameMetadataListener != null) {
        internalVideoFrameMetadataListener.onVideoFrameAboutToBeRendered(
            presentationTimeUs, releaseTimeNs, format, mediaFormat);
      }
      if (videoFrameMetadataListener != null) {
        videoFrameMetadataListener.onVideoFrameAboutToBeRendered(
            presentationTimeUs, releaseTimeNs, format, mediaFormat);
      }
    }

    // CameraMotionListener

    @Override
    public void onCameraMotion(long timeUs, float[] rotation) {
      if (internalCameraMotionListener != null) {
        internalCameraMotionListener.onCameraMotion(timeUs, rotation);
      }
      if (cameraMotionListener != null) {
        cameraMotionListener.onCameraMotion(timeUs, rotation);
      }
    }

    @Override
    public void onCameraMotionReset() {
      if (internalCameraMotionListener != null) {
        internalCameraMotionListener.onCameraMotionReset();
      }
      if (cameraMotionListener != null) {
        cameraMotionListener.onCameraMotionReset();
      }
    }
  }
}
