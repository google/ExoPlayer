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

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.device.DeviceListener;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * An {@link ExoPlayer} implementation that uses default {@link Renderer} components. Instances can
 * be obtained from {@link SimpleExoPlayer.Builder}.
 */
public class SimpleExoPlayer extends BasePlayer
    implements ExoPlayer,
        Player.AudioComponent,
        Player.VideoComponent,
        Player.TextComponent,
        Player.MetadataComponent,
        Player.DeviceComponent {

  /** @deprecated Use {@link com.google.android.exoplayer2.video.VideoListener}. */
  @Deprecated
  public interface VideoListener extends com.google.android.exoplayer2.video.VideoListener {}

  /**
   * A builder for {@link SimpleExoPlayer} instances.
   *
   * <p>See {@link #Builder(Context)} for the list of default values.
   */
  public static final class Builder {

    private final Context context;
    private final RenderersFactory renderersFactory;

    private Clock clock;
    private TrackSelector trackSelector;
    private MediaSourceFactory mediaSourceFactory;
    private LoadControl loadControl;
    private BandwidthMeter bandwidthMeter;
    private AnalyticsCollector analyticsCollector;
    private Looper looper;
    private boolean useLazyPreparation;
    private boolean throwWhenStuckBuffering;
    private boolean buildCalled;

    /**
     * Creates a builder.
     *
     * <p>Use {@link #Builder(Context, RenderersFactory)} instead, if you intend to provide a custom
     * {@link RenderersFactory}. This is to ensure that ProGuard or R8 can remove ExoPlayer's {@link
     * DefaultRenderersFactory} from the APK.
     *
     * <p>The builder uses the following default values:
     *
     * <ul>
     *   <li>{@link RenderersFactory}: {@link DefaultRenderersFactory}
     *   <li>{@link TrackSelector}: {@link DefaultTrackSelector}
     *   <li>{@link MediaSourceFactory}: {@link DefaultMediaSourceFactory}
     *   <li>{@link LoadControl}: {@link DefaultLoadControl}
     *   <li>{@link BandwidthMeter}: {@link DefaultBandwidthMeter#getSingletonInstance(Context)}
     *   <li>{@link Looper}: The {@link Looper} associated with the current thread, or the {@link
     *       Looper} of the application's main thread if the current thread doesn't have a {@link
     *       Looper}
     *   <li>{@link AnalyticsCollector}: {@link AnalyticsCollector} with {@link Clock#DEFAULT}
     *   <li>{@code useLazyPreparation}: {@code true}
     *   <li>{@link Clock}: {@link Clock#DEFAULT}
     * </ul>
     *
     * @param context A {@link Context}.
     */
    public Builder(Context context) {
      this(context, new DefaultRenderersFactory(context));
    }

    /**
     * Creates a builder with a custom {@link RenderersFactory}.
     *
     * <p>See {@link #Builder(Context)} for a list of default values.
     *
     * @param context A {@link Context}.
     * @param renderersFactory A factory for creating {@link Renderer Renderers} to be used by the
     *     player.
     */
    public Builder(Context context, RenderersFactory renderersFactory) {
      this(
          context,
          renderersFactory,
          new DefaultTrackSelector(context),
          DefaultMediaSourceFactory.newInstance(context),
          new DefaultLoadControl(),
          DefaultBandwidthMeter.getSingletonInstance(context),
          Util.getLooper(),
          new AnalyticsCollector(Clock.DEFAULT),
          /* useLazyPreparation= */ true,
          Clock.DEFAULT);
    }

    /**
     * Creates a builder with the specified custom components.
     *
     * <p>Note that this constructor is only useful if you try to ensure that ExoPlayer's default
     * components can be removed by ProGuard or R8. For most components except renderers, there is
     * only a marginal benefit of doing that.
     *
     * @param context A {@link Context}.
     * @param renderersFactory A factory for creating {@link Renderer Renderers} to be used by the
     *     player.
     * @param trackSelector A {@link TrackSelector}.
     * @param mediaSourceFactory A {@link MediaSourceFactory}.
     * @param loadControl A {@link LoadControl}.
     * @param bandwidthMeter A {@link BandwidthMeter}.
     * @param looper A {@link Looper} that must be used for all calls to the player.
     * @param analyticsCollector An {@link AnalyticsCollector}.
     * @param useLazyPreparation Whether playlist items should be prepared lazily. If false, all
     *     initial preparation steps (e.g., manifest loads) happen immediately. If true, these
     *     initial preparations are triggered only when the player starts buffering the media.
     * @param clock A {@link Clock}. Should always be {@link Clock#DEFAULT}.
     */
    public Builder(
        Context context,
        RenderersFactory renderersFactory,
        TrackSelector trackSelector,
        MediaSourceFactory mediaSourceFactory,
        LoadControl loadControl,
        BandwidthMeter bandwidthMeter,
        Looper looper,
        AnalyticsCollector analyticsCollector,
        boolean useLazyPreparation,
        Clock clock) {
      this.context = context;
      this.renderersFactory = renderersFactory;
      this.trackSelector = trackSelector;
      this.mediaSourceFactory = mediaSourceFactory;
      this.loadControl = loadControl;
      this.bandwidthMeter = bandwidthMeter;
      this.looper = looper;
      this.analyticsCollector = analyticsCollector;
      this.useLazyPreparation = useLazyPreparation;
      this.clock = clock;
    }

    /**
     * Sets the {@link TrackSelector} that will be used by the player.
     *
     * @param trackSelector A {@link TrackSelector}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setTrackSelector(TrackSelector trackSelector) {
      Assertions.checkState(!buildCalled);
      this.trackSelector = trackSelector;
      return this;
    }

    /**
     * Sets the {@link MediaSourceFactory} that will be used by the player.
     *
     * @param mediaSourceFactory A {@link MediaSourceFactory}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setMediaSourceFactory(MediaSourceFactory mediaSourceFactory) {
      Assertions.checkState(!buildCalled);
      this.mediaSourceFactory = mediaSourceFactory;
      return this;
    }

    /**
     * Sets the {@link LoadControl} that will be used by the player.
     *
     * @param loadControl A {@link LoadControl}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setLoadControl(LoadControl loadControl) {
      Assertions.checkState(!buildCalled);
      this.loadControl = loadControl;
      return this;
    }

    /**
     * Sets the {@link BandwidthMeter} that will be used by the player.
     *
     * @param bandwidthMeter A {@link BandwidthMeter}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setBandwidthMeter(BandwidthMeter bandwidthMeter) {
      Assertions.checkState(!buildCalled);
      this.bandwidthMeter = bandwidthMeter;
      return this;
    }

    /**
     * Sets the {@link Looper} that must be used for all calls to the player and that is used to
     * call listeners on.
     *
     * @param looper A {@link Looper}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setLooper(Looper looper) {
      Assertions.checkState(!buildCalled);
      this.looper = looper;
      return this;
    }

    /**
     * Sets the {@link AnalyticsCollector} that will collect and forward all player events.
     *
     * @param analyticsCollector An {@link AnalyticsCollector}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setAnalyticsCollector(AnalyticsCollector analyticsCollector) {
      Assertions.checkState(!buildCalled);
      this.analyticsCollector = analyticsCollector;
      return this;
    }

    /**
     * Sets whether media sources should be initialized lazily.
     *
     * <p>If false, all initial preparation steps (e.g., manifest loads) happen immediately. If
     * true, these initial preparations are triggered only when the player starts buffering the
     * media.
     *
     * @param useLazyPreparation Whether to use lazy preparation.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setUseLazyPreparation(boolean useLazyPreparation) {
      Assertions.checkState(!buildCalled);
      this.useLazyPreparation = useLazyPreparation;
      return this;
    }

    /**
     * Sets whether the player should throw when it detects it's stuck buffering.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     *
     * @param throwWhenStuckBuffering Whether to throw when the player detects it's stuck buffering.
     * @return This builder.
     */
    public Builder experimental_setThrowWhenStuckBuffering(boolean throwWhenStuckBuffering) {
      this.throwWhenStuckBuffering = throwWhenStuckBuffering;
      return this;
    }

    /**
     * Sets the {@link Clock} that will be used by the player. Should only be set for testing
     * purposes.
     *
     * @param clock A {@link Clock}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @VisibleForTesting
    public Builder setClock(Clock clock) {
      Assertions.checkState(!buildCalled);
      this.clock = clock;
      return this;
    }

    /**
     * Builds a {@link SimpleExoPlayer} instance.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public SimpleExoPlayer build() {
      Assertions.checkState(!buildCalled);
      buildCalled = true;
      return new SimpleExoPlayer(/* builder= */ this);
    }
  }

  private static final String TAG = "SimpleExoPlayer";

  protected final Renderer[] renderers;

  private final ExoPlayerImpl player;
  private final Handler eventHandler;
  private final ComponentListener componentListener;
  private final CopyOnWriteArraySet<com.google.android.exoplayer2.video.VideoListener>
      videoListeners;
  private final CopyOnWriteArraySet<AudioListener> audioListeners;
  private final CopyOnWriteArraySet<TextOutput> textOutputs;
  private final CopyOnWriteArraySet<MetadataOutput> metadataOutputs;
  private final CopyOnWriteArraySet<DeviceListener> deviceListeners;
  private final CopyOnWriteArraySet<VideoRendererEventListener> videoDebugListeners;
  private final CopyOnWriteArraySet<AudioRendererEventListener> audioDebugListeners;
  private final BandwidthMeter bandwidthMeter;
  private final AnalyticsCollector analyticsCollector;

  private final AudioBecomingNoisyManager audioBecomingNoisyManager;
  private final AudioFocusManager audioFocusManager;
  private final StreamVolumeManager streamVolumeManager;
  private final WakeLockManager wakeLockManager;
  private final WifiLockManager wifiLockManager;

  @Nullable private Format videoFormat;
  @Nullable private Format audioFormat;

  @Nullable private VideoDecoderOutputBufferRenderer videoDecoderOutputBufferRenderer;
  @Nullable private Surface surface;
  private boolean ownsSurface;
  private @C.VideoScalingMode int videoScalingMode;
  @Nullable private SurfaceHolder surfaceHolder;
  @Nullable private TextureView textureView;
  private int surfaceWidth;
  private int surfaceHeight;
  @Nullable private DecoderCounters videoDecoderCounters;
  @Nullable private DecoderCounters audioDecoderCounters;
  private int audioSessionId;
  private AudioAttributes audioAttributes;
  private float audioVolume;
  private boolean skipSilenceEnabled;
  private List<Cue> currentCues;
  @Nullable private VideoFrameMetadataListener videoFrameMetadataListener;
  @Nullable private CameraMotionListener cameraMotionListener;
  private boolean hasNotifiedFullWrongThreadWarning;
  @Nullable private PriorityTaskManager priorityTaskManager;
  private boolean isPriorityTaskManagerRegistered;
  private boolean playerReleased;
  private DeviceInfo deviceInfo;

  /** @param builder The {@link Builder} to obtain all construction parameters. */
  protected SimpleExoPlayer(Builder builder) {
    this(
        builder.context,
        builder.renderersFactory,
        builder.trackSelector,
        builder.mediaSourceFactory,
        builder.loadControl,
        builder.bandwidthMeter,
        builder.analyticsCollector,
        builder.useLazyPreparation,
        builder.clock,
        builder.looper);
    if (builder.throwWhenStuckBuffering) {
      player.experimental_throwWhenStuckBuffering();
    }
  }

  /**
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param bandwidthMeter The {@link BandwidthMeter} that will be used by the instance.
   * @param analyticsCollector A factory for creating the {@link AnalyticsCollector} that will
   *     collect and forward all player events.
   * @param useLazyPreparation Whether playlist items are prepared lazily. If false, all manifest
   *     loads and other initial preparation steps happen immediately. If true, these initial
   *     preparations are triggered only when the player starts buffering the media.
   * @param clock The {@link Clock} that will be used by the instance. Should always be {@link
   *     Clock#DEFAULT}, unless the player is being used from a test.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
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
      Looper looper) {
    this.bandwidthMeter = bandwidthMeter;
    this.analyticsCollector = analyticsCollector;
    componentListener = new ComponentListener();
    videoListeners = new CopyOnWriteArraySet<>();
    audioListeners = new CopyOnWriteArraySet<>();
    textOutputs = new CopyOnWriteArraySet<>();
    metadataOutputs = new CopyOnWriteArraySet<>();
    deviceListeners = new CopyOnWriteArraySet<>();
    videoDebugListeners = new CopyOnWriteArraySet<>();
    audioDebugListeners = new CopyOnWriteArraySet<>();
    eventHandler = new Handler(looper);
    renderers =
        renderersFactory.createRenderers(
            eventHandler,
            componentListener,
            componentListener,
            componentListener,
            componentListener);

    // Set initial values.
    audioVolume = 1;
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    audioAttributes = AudioAttributes.DEFAULT;
    videoScalingMode = Renderer.VIDEO_SCALING_MODE_DEFAULT;
    currentCues = Collections.emptyList();

    // Build the player and associated objects.
    player =
        new ExoPlayerImpl(
            renderers,
            trackSelector,
            mediaSourceFactory,
            loadControl,
            bandwidthMeter,
            analyticsCollector,
            useLazyPreparation,
            clock,
            looper);
    analyticsCollector.setPlayer(player);
    player.addListener(analyticsCollector);
    player.addListener(componentListener);
    videoDebugListeners.add(analyticsCollector);
    videoListeners.add(analyticsCollector);
    audioDebugListeners.add(analyticsCollector);
    audioListeners.add(analyticsCollector);
    addMetadataOutput(analyticsCollector);
    bandwidthMeter.addEventListener(eventHandler, analyticsCollector);
    audioBecomingNoisyManager =
        new AudioBecomingNoisyManager(context, eventHandler, componentListener);
    audioFocusManager = new AudioFocusManager(context, eventHandler, componentListener);
    streamVolumeManager = new StreamVolumeManager(context, eventHandler, componentListener);
    wakeLockManager = new WakeLockManager(context);
    wifiLockManager = new WifiLockManager(context);
    deviceInfo = createDeviceInfo(streamVolumeManager);
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
  public MetadataComponent getMetadataComponent() {
    return this;
  }

  @Override
  @Nullable
  public DeviceComponent getDeviceComponent() {
    return this;
  }

  /**
   * Sets the video scaling mode.
   *
   * <p>Note that the scaling mode only applies if a {@link MediaCodec}-based video {@link Renderer}
   * is enabled and if the output surface is owned by a {@link android.view.SurfaceView}.
   *
   * @param videoScalingMode The video scaling mode.
   */
  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
    verifyApplicationThread();
    this.videoScalingMode = videoScalingMode;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_SCALING_MODE)
            .setPayload(videoScalingMode)
            .send();
      }
    }
  }

  @Override
  public @C.VideoScalingMode int getVideoScalingMode() {
    return videoScalingMode;
  }

  @Override
  public void clearVideoSurface() {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    setVideoSurfaceInternal(/* surface= */ null, /* ownsSurface= */ false);
    maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    if (surface != null && surface == this.surface) {
      clearVideoSurface();
    }
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    if (surface != null) {
      clearVideoDecoderOutputBufferRenderer();
    }
    setVideoSurfaceInternal(surface, /* ownsSurface= */ false);
    int newSurfaceSize = surface == null ? 0 : C.LENGTH_UNSET;
    maybeNotifySurfaceSizeChanged(/* width= */ newSurfaceSize, /* height= */ newSurfaceSize);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    if (surfaceHolder != null) {
      clearVideoDecoderOutputBufferRenderer();
    }
    this.surfaceHolder = surfaceHolder;
    if (surfaceHolder == null) {
      setVideoSurfaceInternal(null, /* ownsSurface= */ false);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    } else {
      surfaceHolder.addCallback(componentListener);
      Surface surface = surfaceHolder.getSurface();
      if (surface != null && surface.isValid()) {
        setVideoSurfaceInternal(surface, /* ownsSurface= */ false);
        Rect surfaceSize = surfaceHolder.getSurfaceFrame();
        maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
      } else {
        setVideoSurfaceInternal(/* surface= */ null, /* ownsSurface= */ false);
        maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      }
    }
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
      setVideoSurfaceHolder(null);
    }
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    clearVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    if (textureView != null) {
      clearVideoDecoderOutputBufferRenderer();
    }
    this.textureView = textureView;
    if (textureView == null) {
      setVideoSurfaceInternal(/* surface= */ null, /* ownsSurface= */ true);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    } else {
      if (textureView.getSurfaceTextureListener() != null) {
        Log.w(TAG, "Replacing existing SurfaceTextureListener.");
      }
      textureView.setSurfaceTextureListener(componentListener);
      SurfaceTexture surfaceTexture =
          textureView.isAvailable() ? textureView.getSurfaceTexture() : null;
      if (surfaceTexture == null) {
        setVideoSurfaceInternal(/* surface= */ null, /* ownsSurface= */ true);
        maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      } else {
        setVideoSurfaceInternal(new Surface(surfaceTexture), /* ownsSurface= */ true);
        maybeNotifySurfaceSizeChanged(textureView.getWidth(), textureView.getHeight());
      }
    }
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    if (textureView != null && textureView == this.textureView) {
      setVideoTextureView(null);
    }
  }

  @Override
  public void setVideoDecoderOutputBufferRenderer(
      @Nullable VideoDecoderOutputBufferRenderer videoDecoderOutputBufferRenderer) {
    verifyApplicationThread();
    if (videoDecoderOutputBufferRenderer != null) {
      clearVideoSurface();
    }
    setVideoDecoderOutputBufferRendererInternal(videoDecoderOutputBufferRenderer);
  }

  @Override
  public void clearVideoDecoderOutputBufferRenderer() {
    verifyApplicationThread();
    setVideoDecoderOutputBufferRendererInternal(/* videoDecoderOutputBufferRenderer= */ null);
  }

  @Override
  public void clearVideoDecoderOutputBufferRenderer(
      @Nullable VideoDecoderOutputBufferRenderer videoDecoderOutputBufferRenderer) {
    verifyApplicationThread();
    if (videoDecoderOutputBufferRenderer != null
        && videoDecoderOutputBufferRenderer == this.videoDecoderOutputBufferRenderer) {
      clearVideoDecoderOutputBufferRenderer();
    }
  }

  @Override
  public void addAudioListener(AudioListener listener) {
    audioListeners.add(listener);
  }

  @Override
  public void removeAudioListener(AudioListener listener) {
    audioListeners.remove(listener);
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false);
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    verifyApplicationThread();
    if (playerReleased) {
      return;
    }
    if (!Util.areEqual(this.audioAttributes, audioAttributes)) {
      this.audioAttributes = audioAttributes;
      for (Renderer renderer : renderers) {
        if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
          player
              .createMessage(renderer)
              .setType(Renderer.MSG_SET_AUDIO_ATTRIBUTES)
              .setPayload(audioAttributes)
              .send();
        }
      }
      streamVolumeManager.setStreamType(Util.getStreamTypeForAudioUsage(audioAttributes.usage));
      for (AudioListener audioListener : audioListeners) {
        audioListener.onAudioAttributesChanged(audioAttributes);
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
    this.audioSessionId = audioSessionId;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_AUDIO_SESSION_ID)
            .setPayload(audioSessionId)
            .send();
      }
    }
    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
      notifyAudioSessionIdSet();
    }
  }

  @Override
  public int getAudioSessionId() {
    return audioSessionId;
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    verifyApplicationThread();
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_AUX_EFFECT_INFO)
            .setPayload(auxEffectInfo)
            .send();
      }
    }
  }

  @Override
  public void clearAuxEffectInfo() {
    setAuxEffectInfo(new AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, /* sendLevel= */ 0f));
  }

  @Override
  public void setVolume(float audioVolume) {
    verifyApplicationThread();
    audioVolume = Util.constrainValue(audioVolume, /* min= */ 0, /* max= */ 1);
    if (this.audioVolume == audioVolume) {
      return;
    }
    this.audioVolume = audioVolume;
    sendVolumeToRenderers();
    for (AudioListener audioListener : audioListeners) {
      audioListener.onVolumeChanged(audioVolume);
    }
  }

  @Override
  public float getVolume() {
    return audioVolume;
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
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_SKIP_SILENCE_ENABLED)
            .setPayload(skipSilenceEnabled)
            .send();
      }
    }
    notifySkipSilenceEnabledChanged();
  }

  /**
   * Sets the stream type for audio playback, used by the underlying audio track.
   *
   * <p>Setting the stream type during playback may introduce a short gap in audio output as the
   * audio track is recreated. A new audio session id will also be generated.
   *
   * <p>Calling this method overwrites any attributes set previously by calling {@link
   * #setAudioAttributes(AudioAttributes)}.
   *
   * @deprecated Use {@link #setAudioAttributes(AudioAttributes)}.
   * @param streamType The stream type for audio playback.
   */
  @Deprecated
  public void setAudioStreamType(@C.StreamType int streamType) {
    @C.AudioUsage int usage = Util.getAudioUsageForStreamType(streamType);
    @C.AudioContentType int contentType = Util.getAudioContentTypeForStreamType(streamType);
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build();
    setAudioAttributes(audioAttributes);
  }

  /**
   * Returns the stream type for audio playback.
   *
   * @deprecated Use {@link #getAudioAttributes()}.
   */
  @Deprecated
  public @C.StreamType int getAudioStreamType() {
    return Util.getStreamTypeForAudioUsage(audioAttributes.usage);
  }

  /** Returns the {@link AnalyticsCollector} used for collecting analytics events. */
  public AnalyticsCollector getAnalyticsCollector() {
    return analyticsCollector;
  }

  /**
   * Adds an {@link AnalyticsListener} to receive analytics events.
   *
   * @param listener The listener to be added.
   */
  public void addAnalyticsListener(AnalyticsListener listener) {
    verifyApplicationThread();
    analyticsCollector.addListener(listener);
  }

  /**
   * Removes an {@link AnalyticsListener}.
   *
   * @param listener The listener to be removed.
   */
  public void removeAnalyticsListener(AnalyticsListener listener) {
    verifyApplicationThread();
    analyticsCollector.removeListener(listener);
  }

  /**
   * Sets whether the player should pause automatically when audio is rerouted from a headset to
   * device speakers. See the <a
   * href="https://developer.android.com/guide/topics/media-apps/volume-and-earphones#becoming-noisy">audio
   * becoming noisy</a> documentation for more information.
   *
   * <p>This feature is not enabled by default.
   *
   * @param handleAudioBecomingNoisy Whether the player should pause automatically when audio is
   *     rerouted from a headset to device speakers.
   */
  public void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
    verifyApplicationThread();
    if (playerReleased) {
      return;
    }
    audioBecomingNoisyManager.setEnabled(handleAudioBecomingNoisy);
  }

  /**
   * Sets a {@link PriorityTaskManager}, or null to clear a previously set priority task manager.
   *
   * <p>The priority {@link C#PRIORITY_PLAYBACK} will be set while the player is loading.
   *
   * @param priorityTaskManager The {@link PriorityTaskManager}, or null to clear a previously set
   *     priority task manager.
   */
  public void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
    verifyApplicationThread();
    if (Util.areEqual(this.priorityTaskManager, priorityTaskManager)) {
      return;
    }
    if (isPriorityTaskManagerRegistered) {
      Assertions.checkNotNull(this.priorityTaskManager).remove(C.PRIORITY_PLAYBACK);
    }
    if (priorityTaskManager != null && isLoading()) {
      priorityTaskManager.add(C.PRIORITY_PLAYBACK);
      isPriorityTaskManagerRegistered = true;
    } else {
      isPriorityTaskManagerRegistered = false;
    }
    this.priorityTaskManager = priorityTaskManager;
  }

  /** @deprecated Use {@link #setPlaybackSpeed(float)} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  @RequiresApi(23)
  public void setPlaybackParams(@Nullable PlaybackParams params) {
    float playbackSpeed;
    if (params != null) {
      params.allowDefaults();
      playbackSpeed = params.getSpeed();
    } else {
      playbackSpeed = 1.0f;
    }
    setPlaybackSpeed(playbackSpeed);
  }

  /** Returns the video format currently being played, or null if no video is being played. */
  @Nullable
  public Format getVideoFormat() {
    return videoFormat;
  }

  /** Returns the audio format currently being played, or null if no audio is being played. */
  @Nullable
  public Format getAudioFormat() {
    return audioFormat;
  }

  /** Returns {@link DecoderCounters} for video, or null if no video is being played. */
  @Nullable
  public DecoderCounters getVideoDecoderCounters() {
    return videoDecoderCounters;
  }

  /** Returns {@link DecoderCounters} for audio, or null if no audio is being played. */
  @Nullable
  public DecoderCounters getAudioDecoderCounters() {
    return audioDecoderCounters;
  }

  @Override
  public void addVideoListener(com.google.android.exoplayer2.video.VideoListener listener) {
    videoListeners.add(listener);
  }

  @Override
  public void removeVideoListener(com.google.android.exoplayer2.video.VideoListener listener) {
    videoListeners.remove(listener);
  }

  @Override
  public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    verifyApplicationThread();
    videoFrameMetadataListener = listener;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER)
            .setPayload(listener)
            .send();
      }
    }
  }

  @Override
  public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    verifyApplicationThread();
    if (videoFrameMetadataListener != listener) {
      return;
    }
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER)
            .setPayload(null)
            .send();
      }
    }
  }

  @Override
  public void setCameraMotionListener(CameraMotionListener listener) {
    verifyApplicationThread();
    cameraMotionListener = listener;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_CAMERA_MOTION) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_CAMERA_MOTION_LISTENER)
            .setPayload(listener)
            .send();
      }
    }
  }

  @Override
  public void clearCameraMotionListener(CameraMotionListener listener) {
    verifyApplicationThread();
    if (cameraMotionListener != listener) {
      return;
    }
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_CAMERA_MOTION) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_CAMERA_MOTION_LISTENER)
            .setPayload(null)
            .send();
      }
    }
  }

  /** Returns whether skipping silences in the audio stream is enabled. */
  public boolean isSkipSilenceEnabled() {
    return skipSilenceEnabled;
  }

  /**
   * Sets a listener to receive video events, removing all existing listeners.
   *
   * @param listener The listener.
   * @deprecated Use {@link #addVideoListener(com.google.android.exoplayer2.video.VideoListener)}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public void setVideoListener(VideoListener listener) {
    videoListeners.clear();
    if (listener != null) {
      addVideoListener(listener);
    }
  }

  /**
   * Equivalent to {@link #removeVideoListener(com.google.android.exoplayer2.video.VideoListener)}.
   *
   * @param listener The listener to clear.
   * @deprecated Use {@link
   *     #removeVideoListener(com.google.android.exoplayer2.video.VideoListener)}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public void clearVideoListener(VideoListener listener) {
    removeVideoListener(listener);
  }

  @Override
  public void addTextOutput(TextOutput listener) {
    if (!currentCues.isEmpty()) {
      listener.onCues(currentCues);
    }
    textOutputs.add(listener);
  }

  @Override
  public void removeTextOutput(TextOutput listener) {
    textOutputs.remove(listener);
  }

  /**
   * Sets an output to receive text events, removing all existing outputs.
   *
   * @param output The output.
   * @deprecated Use {@link #addTextOutput(TextOutput)}.
   */
  @Deprecated
  public void setTextOutput(TextOutput output) {
    textOutputs.clear();
    if (output != null) {
      addTextOutput(output);
    }
  }

  /**
   * Equivalent to {@link #removeTextOutput(TextOutput)}.
   *
   * @param output The output to clear.
   * @deprecated Use {@link #removeTextOutput(TextOutput)}.
   */
  @Deprecated
  public void clearTextOutput(TextOutput output) {
    removeTextOutput(output);
  }

  @Override
  public void addMetadataOutput(MetadataOutput listener) {
    metadataOutputs.add(listener);
  }

  @Override
  public void removeMetadataOutput(MetadataOutput listener) {
    metadataOutputs.remove(listener);
  }

  /**
   * Sets an output to receive metadata events, removing all existing outputs.
   *
   * @param output The output.
   * @deprecated Use {@link #addMetadataOutput(MetadataOutput)}.
   */
  @Deprecated
  public void setMetadataOutput(MetadataOutput output) {
    metadataOutputs.retainAll(Collections.singleton(analyticsCollector));
    if (output != null) {
      addMetadataOutput(output);
    }
  }

  /**
   * Equivalent to {@link #removeMetadataOutput(MetadataOutput)}.
   *
   * @param output The output to clear.
   * @deprecated Use {@link #removeMetadataOutput(MetadataOutput)}.
   */
  @Deprecated
  public void clearMetadataOutput(MetadataOutput output) {
    removeMetadataOutput(output);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public void setVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListeners.retainAll(Collections.singleton(analyticsCollector));
    if (listener != null) {
      addVideoDebugListener(listener);
    }
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  public void addVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListeners.add(listener);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} and {@link
   *     #removeAnalyticsListener(AnalyticsListener)} to get more detailed debug information.
   */
  @Deprecated
  public void removeVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListeners.remove(listener);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public void setAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListeners.retainAll(Collections.singleton(analyticsCollector));
    if (listener != null) {
      addAudioDebugListener(listener);
    }
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  public void addAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListeners.add(listener);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} and {@link
   *     #removeAnalyticsListener(AnalyticsListener)} to get more detailed debug information.
   */
  @Deprecated
  public void removeAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListeners.remove(listener);
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
  public void addListener(Player.EventListener listener) {
    verifyApplicationThread();
    player.addListener(listener);
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    verifyApplicationThread();
    player.removeListener(listener);
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

  /** @deprecated Use {@link #getPlayerError()} instead. */
  @Deprecated
  @Override
  @Nullable
  public ExoPlaybackException getPlaybackError() {
    return getPlayerError();
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
    setMediaSources(
        Collections.singletonList(mediaSource),
        /* startWindowIndex= */ resetPosition ? 0 : C.INDEX_UNSET,
        /* startPositionMs= */ C.TIME_UNSET);
    prepare();
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaItems(mediaItems);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaItems(mediaItems, resetPosition);
  }

  @Override
  public void setMediaItems(
      List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaItems(mediaItems, startWindowIndex, startPositionMs);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaItem(mediaItem);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaItem(mediaItem, resetPosition);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaItem(mediaItem, startPositionMs);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaSources(mediaSources);
  }

  @Override
  public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaSources(mediaSources, resetPosition);
  }

  @Override
  public void setMediaSources(
      List<MediaSource> mediaSources, int startWindowIndex, long startPositionMs) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaSources(mediaSources, startWindowIndex, startPositionMs);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaSource(mediaSource);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaSource(mediaSource, resetPosition);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, long startPositionMs) {
    verifyApplicationThread();
    analyticsCollector.resetForNewPlaylist();
    player.setMediaSource(mediaSource, startPositionMs);
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    verifyApplicationThread();
    player.addMediaItems(mediaItems);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    verifyApplicationThread();
    player.addMediaItems(index, mediaItems);
  }

  @Override
  public void addMediaItem(MediaItem mediaItem) {
    verifyApplicationThread();
    player.addMediaItem(mediaItem);
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    verifyApplicationThread();
    player.addMediaItem(index, mediaItem);
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
  public void moveMediaItem(int currentIndex, int newIndex) {
    verifyApplicationThread();
    player.moveMediaItem(currentIndex, newIndex);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    verifyApplicationThread();
    player.moveMediaItems(fromIndex, toIndex, newIndex);
  }

  @Override
  public void removeMediaItem(int index) {
    verifyApplicationThread();
    player.removeMediaItem(index);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    verifyApplicationThread();
    player.removeMediaItems(fromIndex, toIndex);
  }

  @Override
  public void clearMediaItems() {
    verifyApplicationThread();
    player.clearMediaItems();
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
  public void seekTo(int windowIndex, long positionMs) {
    verifyApplicationThread();
    analyticsCollector.notifySeekStarted();
    player.seekTo(windowIndex, positionMs);
  }

  /**
   * @deprecated Use {@link #setPlaybackSpeed(float)} and {@link #setSkipSilenceEnabled(boolean)}
   *     instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    verifyApplicationThread();
    player.setPlaybackParameters(playbackParameters);
  }

  /** @deprecated Use {@link #getPlaybackSpeed()} and {@link #getSkipSilenceEnabled()} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public PlaybackParameters getPlaybackParameters() {
    verifyApplicationThread();
    return player.getPlaybackParameters();
  }

  @Override
  public void setPlaybackSpeed(float playbackSpeed) {
    verifyApplicationThread();
    player.setPlaybackSpeed(playbackSpeed);
  }

  @Override
  public float getPlaybackSpeed() {
    verifyApplicationThread();
    return player.getPlaybackSpeed();
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
  public void stop(boolean reset) {
    verifyApplicationThread();
    audioFocusManager.updateAudioFocus(getPlayWhenReady(), Player.STATE_IDLE);
    player.stop(reset);
    currentCues = Collections.emptyList();
  }

  @Override
  public void release() {
    verifyApplicationThread();
    audioBecomingNoisyManager.setEnabled(false);
    streamVolumeManager.release();
    wakeLockManager.setStayAwake(false);
    wifiLockManager.setStayAwake(false);
    audioFocusManager.release();
    player.release();
    removeSurfaceCallbacks();
    if (surface != null) {
      if (ownsSurface) {
        surface.release();
      }
      surface = null;
    }
    if (isPriorityTaskManagerRegistered) {
      Assertions.checkNotNull(priorityTaskManager).remove(C.PRIORITY_PLAYBACK);
      isPriorityTaskManagerRegistered = false;
    }
    bandwidthMeter.removeEventListener(analyticsCollector);
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
  public int getRendererType(int index) {
    verifyApplicationThread();
    return player.getRendererType(index);
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
  public int getCurrentWindowIndex() {
    verifyApplicationThread();
    return player.getCurrentWindowIndex();
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

  /**
   * Sets whether the player should use a {@link android.os.PowerManager.WakeLock} to ensure the
   * device stays awake for playback, even when the screen is off.
   *
   * <p>Enabling this feature requires the {@link android.Manifest.permission#WAKE_LOCK} permission.
   * It should be used together with a foreground {@link android.app.Service} for use cases where
   * playback can occur when the screen is off (e.g. background audio playback). It is not useful if
   * the screen will always be on during playback (e.g. foreground video playback).
   *
   * <p>This feature is not enabled by default. If enabled, a WakeLock is held whenever the player
   * is in the {@link #STATE_READY READY} or {@link #STATE_BUFFERING BUFFERING} states with {@code
   * playWhenReady = true}.
   *
   * @param handleWakeLock Whether the player should use a {@link android.os.PowerManager.WakeLock}
   *     to ensure the device stays awake for playback, even when the screen is off.
   * @deprecated Use {@link #setWakeMode(int)} instead.
   */
  @Deprecated
  public void setHandleWakeLock(boolean handleWakeLock) {
    setWakeMode(handleWakeLock ? C.WAKE_MODE_LOCAL : C.WAKE_MODE_NONE);
  }

  /**
   * Sets how the player should keep the device awake for playback when the screen is off.
   *
   * <p>Enabling this feature requires the {@link android.Manifest.permission#WAKE_LOCK} permission.
   * It should be used together with a foreground {@link android.app.Service} for use cases where
   * playback occurs and the screen is off (e.g. background audio playback). It is not useful when
   * the screen will be kept on during playback (e.g. foreground video playback).
   *
   * <p>When enabled, the locks ({@link android.os.PowerManager.WakeLock} / {@link
   * android.net.wifi.WifiManager.WifiLock}) will be held whenever the player is in the {@link
   * #STATE_READY} or {@link #STATE_BUFFERING} states with {@code playWhenReady = true}. The locks
   * held depends on the specified {@link C.WakeMode}.
   *
   * @param wakeMode The {@link C.WakeMode} option to keep the device awake during playback.
   */
  public void setWakeMode(@C.WakeMode int wakeMode) {
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
  public void addDeviceListener(DeviceListener listener) {
    deviceListeners.add(listener);
  }

  @Override
  public void removeDeviceListener(DeviceListener listener) {
    deviceListeners.remove(listener);
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

  // Internal methods.

  private void removeSurfaceCallbacks() {
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

  private void setVideoSurfaceInternal(@Nullable Surface surface, boolean ownsSurface) {
    // Note: We don't turn this method into a no-op if the surface is being replaced with itself
    // so as to ensure onRenderedFirstFrame callbacks are still called in this case.
    List<PlayerMessage> messages = new ArrayList<>();
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        messages.add(
            player
                .createMessage(renderer)
                .setType(Renderer.MSG_SET_SURFACE)
                .setPayload(surface)
                .send());
      }
    }
    if (this.surface != null && this.surface != surface) {
      // We're replacing a surface. Block to ensure that it's not accessed after the method returns.
      try {
        for (PlayerMessage message : messages) {
          message.blockUntilDelivered();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      // If we created the previous surface, we are responsible for releasing it.
      if (this.ownsSurface) {
        this.surface.release();
      }
    }
    this.surface = surface;
    this.ownsSurface = ownsSurface;
  }

  private void setVideoDecoderOutputBufferRendererInternal(
      @Nullable VideoDecoderOutputBufferRenderer videoDecoderOutputBufferRenderer) {
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_VIDEO_DECODER_OUTPUT_BUFFER_RENDERER)
            .setPayload(videoDecoderOutputBufferRenderer)
            .send();
      }
    }
    this.videoDecoderOutputBufferRenderer = videoDecoderOutputBufferRenderer;
  }

  private void maybeNotifySurfaceSizeChanged(int width, int height) {
    if (width != surfaceWidth || height != surfaceHeight) {
      surfaceWidth = width;
      surfaceHeight = height;
      for (com.google.android.exoplayer2.video.VideoListener videoListener : videoListeners) {
        videoListener.onSurfaceSizeChanged(width, height);
      }
    }
  }

  private void sendVolumeToRenderers() {
    float scaledVolume = audioVolume * audioFocusManager.getVolumeMultiplier();
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        player
            .createMessage(renderer)
            .setType(Renderer.MSG_SET_VOLUME)
            .setPayload(scaledVolume)
            .send();
      }
    }
  }

  private void notifyAudioSessionIdSet() {
    for (AudioListener audioListener : audioListeners) {
      // Prevent duplicate notification if a listener is both a AudioRendererEventListener and
      // a AudioListener, as they have the same method signature.
      if (!audioDebugListeners.contains(audioListener)) {
        audioListener.onAudioSessionId(audioSessionId);
      }
    }
    for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
      audioDebugListener.onAudioSessionId(audioSessionId);
    }
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  private void notifySkipSilenceEnabledChanged() {
    for (AudioListener listener : audioListeners) {
      // Prevent duplicate notification if a listener is both a AudioRendererEventListener and
      // a AudioListener, as they have the same method signature.
      if (!audioDebugListeners.contains(listener)) {
        listener.onSkipSilenceEnabledChanged(skipSilenceEnabled);
      }
    }
    for (AudioRendererEventListener listener : audioDebugListeners) {
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
        wakeLockManager.setStayAwake(getPlayWhenReady());
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
    if (Looper.myLooper() != getApplicationLooper()) {
      Log.w(
          TAG,
          "Player is accessed on the wrong thread. See "
              + "https://exoplayer.dev/issues/player-accessed-on-wrong-thread",
          hasNotifiedFullWrongThreadWarning ? null : new IllegalStateException());
      hasNotifiedFullWrongThreadWarning = true;
    }
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
          AudioFocusManager.PlayerControl,
          AudioBecomingNoisyManager.EventListener,
          StreamVolumeManager.Listener,
          Player.EventListener {

    // VideoRendererEventListener implementation

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
      videoDecoderCounters = counters;
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoEnabled(counters);
      }
    }

    @Override
    public void onVideoDecoderInitialized(
        String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoDecoderInitialized(
            decoderName, initializedTimestampMs, initializationDurationMs);
      }
    }

    @Override
    public void onVideoInputFormatChanged(Format format) {
      videoFormat = format;
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoInputFormatChanged(format);
      }
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onDroppedFrames(count, elapsed);
      }
    }

    @Override
    public void onVideoSizeChanged(
        int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
      for (com.google.android.exoplayer2.video.VideoListener videoListener : videoListeners) {
        // Prevent duplicate notification if a listener is both a VideoRendererEventListener and
        // a VideoListener, as they have the same method signature.
        if (!videoDebugListeners.contains(videoListener)) {
          videoListener.onVideoSizeChanged(
              width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
        }
      }
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoSizeChanged(
            width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
      }
    }

    @Override
    public void onRenderedFirstFrame(Surface surface) {
      if (SimpleExoPlayer.this.surface == surface) {
        for (com.google.android.exoplayer2.video.VideoListener videoListener : videoListeners) {
          videoListener.onRenderedFirstFrame();
        }
      }
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onRenderedFirstFrame(surface);
      }
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoDisabled(counters);
      }
      videoFormat = null;
      videoDecoderCounters = null;
    }

    @Override
    public void onVideoFrameProcessingOffset(
        long totalProcessingOffsetUs, int frameCount, Format format) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoFrameProcessingOffset(
            totalProcessingOffsetUs, frameCount, format);
      }
    }

    // AudioRendererEventListener implementation

    @Override
    public void onAudioEnabled(DecoderCounters counters) {
      audioDecoderCounters = counters;
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioEnabled(counters);
      }
    }

    @Override
    public void onAudioSessionId(int sessionId) {
      if (audioSessionId == sessionId) {
        return;
      }
      audioSessionId = sessionId;
      notifyAudioSessionIdSet();
    }

    @Override
    public void onAudioDecoderInitialized(
        String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioDecoderInitialized(
            decoderName, initializedTimestampMs, initializationDurationMs);
      }
    }

    @Override
    public void onAudioInputFormatChanged(Format format) {
      audioFormat = format;
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioInputFormatChanged(format);
      }
    }

    @Override
    public void onAudioSinkUnderrun(
        int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioSinkUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }
    }

    @Override
    public void onAudioDisabled(DecoderCounters counters) {
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioDisabled(counters);
      }
      audioFormat = null;
      audioDecoderCounters = null;
      audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    }

    @Override
    public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
      if (SimpleExoPlayer.this.skipSilenceEnabled == skipSilenceEnabled) {
        return;
      }
      SimpleExoPlayer.this.skipSilenceEnabled = skipSilenceEnabled;
      notifySkipSilenceEnabledChanged();
    }

    // TextOutput implementation

    @Override
    public void onCues(List<Cue> cues) {
      currentCues = cues;
      for (TextOutput textOutput : textOutputs) {
        textOutput.onCues(cues);
      }
    }

    // MetadataOutput implementation

    @Override
    public void onMetadata(Metadata metadata) {
      for (MetadataOutput metadataOutput : metadataOutputs) {
        metadataOutput.onMetadata(metadata);
      }
    }

    // SurfaceHolder.Callback implementation

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      setVideoSurfaceInternal(holder.getSurface(), false);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      setVideoSurfaceInternal(/* surface= */ null, /* ownsSurface= */ false);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    }

    // TextureView.SurfaceTextureListener implementation

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
      setVideoSurfaceInternal(new Surface(surfaceTexture), /* ownsSurface= */ true);
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
      setVideoSurfaceInternal(/* surface= */ null, /* ownsSurface= */ true);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
      // Do nothing.
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
        for (DeviceListener deviceListener : deviceListeners) {
          deviceListener.onDeviceInfoChanged(deviceInfo);
        }
      }
    }

    @Override
    public void onStreamVolumeChanged(int streamVolume, boolean streamMuted) {
      for (DeviceListener deviceListener : deviceListeners) {
        deviceListener.onDeviceVolumeChanged(streamVolume, streamMuted);
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
  }
}
