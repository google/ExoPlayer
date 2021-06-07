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

import static com.google.android.exoplayer2.Renderer.MSG_SET_AUDIO_ATTRIBUTES;
import static com.google.android.exoplayer2.Renderer.MSG_SET_AUDIO_SESSION_ID;
import static com.google.android.exoplayer2.Renderer.MSG_SET_AUX_EFFECT_INFO;
import static com.google.android.exoplayer2.Renderer.MSG_SET_CAMERA_MOTION_LISTENER;
import static com.google.android.exoplayer2.Renderer.MSG_SET_SCALING_MODE;
import static com.google.android.exoplayer2.Renderer.MSG_SET_SKIP_SILENCE_ENABLED;
import static com.google.android.exoplayer2.Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER;
import static com.google.android.exoplayer2.Renderer.MSG_SET_VIDEO_OUTPUT;
import static com.google.android.exoplayer2.Renderer.MSG_SET_VOLUME;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.device.DeviceListener;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
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
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import com.google.android.exoplayer2.video.spherical.SphericalGLSurfaceView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;

/**
 * An {@link ExoPlayer} implementation that uses default {@link Renderer} components. Instances can
 * be obtained from {@link SimpleExoPlayer.Builder}.
 */
public class SimpleExoPlayer extends BasePlayer
    implements ExoPlayer,
        ExoPlayer.AudioComponent,
        ExoPlayer.VideoComponent,
        ExoPlayer.TextComponent,
        ExoPlayer.MetadataComponent,
        ExoPlayer.DeviceComponent {

  /** The default timeout for detaching a surface from the player, in milliseconds. */
  public static final long DEFAULT_DETACH_SURFACE_TIMEOUT_MS = 2_000;

  /**
   * A builder for {@link SimpleExoPlayer} instances.
   *
   * <p>See {@link #Builder(Context)} for the list of default values.
   */
  public static final class Builder {

    private final Context context;
    private final RenderersFactory renderersFactory;

    private Clock clock;
    private long foregroundModeTimeoutMs;
    private TrackSelector trackSelector;
    private MediaSourceFactory mediaSourceFactory;
    private LoadControl loadControl;
    private BandwidthMeter bandwidthMeter;
    private AnalyticsCollector analyticsCollector;
    private Looper looper;
    @Nullable private PriorityTaskManager priorityTaskManager;
    private AudioAttributes audioAttributes;
    private boolean handleAudioFocus;
    @C.WakeMode private int wakeMode;
    private boolean handleAudioBecomingNoisy;
    private boolean skipSilenceEnabled;
    @C.VideoScalingMode private int videoScalingMode;
    private boolean useLazyPreparation;
    private SeekParameters seekParameters;
    private LivePlaybackSpeedControl livePlaybackSpeedControl;
    private long releaseTimeoutMs;
    private long detachSurfaceTimeoutMs;
    private boolean pauseAtEndOfMediaItems;
    private boolean buildCalled;

    /**
     * Creates a builder.
     *
     * <p>Use {@link #Builder(Context, RenderersFactory)}, {@link #Builder(Context,
     * RenderersFactory)} or {@link #Builder(Context, RenderersFactory, ExtractorsFactory)} instead,
     * if you intend to provide a custom {@link RenderersFactory} or a custom {@link
     * ExtractorsFactory}. This is to ensure that ProGuard or R8 can remove ExoPlayer's {@link
     * DefaultRenderersFactory} and {@link DefaultExtractorsFactory} from the APK.
     *
     * <p>The builder uses the following default values:
     *
     * <ul>
     *   <li>{@link RenderersFactory}: {@link DefaultRenderersFactory}
     *   <li>{@link TrackSelector}: {@link DefaultTrackSelector}
     *   <li>{@link MediaSourceFactory}: {@link DefaultMediaSourceFactory}
     *   <li>{@link LoadControl}: {@link DefaultLoadControl}
     *   <li>{@link BandwidthMeter}: {@link DefaultBandwidthMeter#getSingletonInstance(Context)}
     *   <li>{@link LivePlaybackSpeedControl}: {@link DefaultLivePlaybackSpeedControl}
     *   <li>{@link Looper}: The {@link Looper} associated with the current thread, or the {@link
     *       Looper} of the application's main thread if the current thread doesn't have a {@link
     *       Looper}
     *   <li>{@link AnalyticsCollector}: {@link AnalyticsCollector} with {@link Clock#DEFAULT}
     *   <li>{@link PriorityTaskManager}: {@code null} (not used)
     *   <li>{@link AudioAttributes}: {@link AudioAttributes#DEFAULT}, not handling audio focus
     *   <li>{@link C.WakeMode}: {@link C#WAKE_MODE_NONE}
     *   <li>{@code handleAudioBecomingNoisy}: {@code false}
     *   <li>{@code skipSilenceEnabled}: {@code false}
     *   <li>{@link C.VideoScalingMode}: {@link C#VIDEO_SCALING_MODE_DEFAULT}
     *   <li>{@code useLazyPreparation}: {@code true}
     *   <li>{@link SeekParameters}: {@link SeekParameters#DEFAULT}
     *   <li>{@code releaseTimeoutMs}: {@link ExoPlayer#DEFAULT_RELEASE_TIMEOUT_MS}
     *   <li>{@code detachSurfaceTimeoutMs}: {@link #DEFAULT_DETACH_SURFACE_TIMEOUT_MS}
     *   <li>{@code pauseAtEndOfMediaItems}: {@code false}
     *   <li>{@link Clock}: {@link Clock#DEFAULT}
     * </ul>
     *
     * @param context A {@link Context}.
     */
    public Builder(Context context) {
      this(context, new DefaultRenderersFactory(context), new DefaultExtractorsFactory());
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
      this(context, renderersFactory, new DefaultExtractorsFactory());
    }

    /**
     * Creates a builder with a custom {@link ExtractorsFactory}.
     *
     * <p>See {@link #Builder(Context)} for a list of default values.
     *
     * @param context A {@link Context}.
     * @param extractorsFactory An {@link ExtractorsFactory} used to extract progressive media from
     *     its container.
     */
    public Builder(Context context, ExtractorsFactory extractorsFactory) {
      this(context, new DefaultRenderersFactory(context), extractorsFactory);
    }

    /**
     * Creates a builder with a custom {@link RenderersFactory} and {@link ExtractorsFactory}.
     *
     * <p>See {@link #Builder(Context)} for a list of default values.
     *
     * @param context A {@link Context}.
     * @param renderersFactory A factory for creating {@link Renderer Renderers} to be used by the
     *     player.
     * @param extractorsFactory An {@link ExtractorsFactory} used to extract progressive media from
     *     its container.
     */
    public Builder(
        Context context, RenderersFactory renderersFactory, ExtractorsFactory extractorsFactory) {
      this(
          context,
          renderersFactory,
          new DefaultTrackSelector(context),
          new DefaultMediaSourceFactory(context, extractorsFactory),
          new DefaultLoadControl(),
          DefaultBandwidthMeter.getSingletonInstance(context),
          new AnalyticsCollector(Clock.DEFAULT));
    }

    /**
     * Creates a builder with the specified custom components.
     *
     * <p>Note that this constructor is only useful to try and ensure that ExoPlayer's default
     * components can be removed by ProGuard or R8.
     *
     * @param context A {@link Context}.
     * @param renderersFactory A factory for creating {@link Renderer Renderers} to be used by the
     *     player.
     * @param trackSelector A {@link TrackSelector}.
     * @param mediaSourceFactory A {@link MediaSourceFactory}.
     * @param loadControl A {@link LoadControl}.
     * @param bandwidthMeter A {@link BandwidthMeter}.
     * @param analyticsCollector An {@link AnalyticsCollector}.
     */
    public Builder(
        Context context,
        RenderersFactory renderersFactory,
        TrackSelector trackSelector,
        MediaSourceFactory mediaSourceFactory,
        LoadControl loadControl,
        BandwidthMeter bandwidthMeter,
        AnalyticsCollector analyticsCollector) {
      this.context = context;
      this.renderersFactory = renderersFactory;
      this.trackSelector = trackSelector;
      this.mediaSourceFactory = mediaSourceFactory;
      this.loadControl = loadControl;
      this.bandwidthMeter = bandwidthMeter;
      this.analyticsCollector = analyticsCollector;
      looper = Util.getCurrentOrMainLooper();
      audioAttributes = AudioAttributes.DEFAULT;
      wakeMode = C.WAKE_MODE_NONE;
      videoScalingMode = C.VIDEO_SCALING_MODE_DEFAULT;
      useLazyPreparation = true;
      seekParameters = SeekParameters.DEFAULT;
      livePlaybackSpeedControl = new DefaultLivePlaybackSpeedControl.Builder().build();
      clock = Clock.DEFAULT;
      releaseTimeoutMs = ExoPlayer.DEFAULT_RELEASE_TIMEOUT_MS;
      detachSurfaceTimeoutMs = DEFAULT_DETACH_SURFACE_TIMEOUT_MS;
    }

    /**
     * Set a limit on the time a call to {@link #setForegroundMode} can spend. If a call to {@link
     * #setForegroundMode} takes more than {@code timeoutMs} milliseconds to complete, the player
     * will raise an error via {@link Player.Listener#onPlayerError}.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     *
     * @param timeoutMs The time limit in milliseconds.
     */
    public Builder experimentalSetForegroundModeTimeoutMs(long timeoutMs) {
      Assertions.checkState(!buildCalled);
      foregroundModeTimeoutMs = timeoutMs;
      return this;
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
     * Sets an {@link PriorityTaskManager} that will be used by the player.
     *
     * <p>The priority {@link C#PRIORITY_PLAYBACK} will be set while the player is loading.
     *
     * @param priorityTaskManager A {@link PriorityTaskManager}, or null to not use one.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
      Assertions.checkState(!buildCalled);
      this.priorityTaskManager = priorityTaskManager;
      return this;
    }

    /**
     * Sets {@link AudioAttributes} that will be used by the player and whether to handle audio
     * focus.
     *
     * <p>If audio focus should be handled, the {@link AudioAttributes#usage} must be {@link
     * C#USAGE_MEDIA} or {@link C#USAGE_GAME}. Other usages will throw an {@link
     * IllegalArgumentException}.
     *
     * @param audioAttributes {@link AudioAttributes}.
     * @param handleAudioFocus Whether the player should handle audio focus.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
      Assertions.checkState(!buildCalled);
      this.audioAttributes = audioAttributes;
      this.handleAudioFocus = handleAudioFocus;
      return this;
    }

    /**
     * Sets the {@link C.WakeMode} that will be used by the player.
     *
     * <p>Enabling this feature requires the {@link android.Manifest.permission#WAKE_LOCK}
     * permission. It should be used together with a foreground {@link android.app.Service} for use
     * cases where playback occurs and the screen is off (e.g. background audio playback). It is not
     * useful when the screen will be kept on during playback (e.g. foreground video playback).
     *
     * <p>When enabled, the locks ({@link android.os.PowerManager.WakeLock} / {@link
     * android.net.wifi.WifiManager.WifiLock}) will be held whenever the player is in the {@link
     * #STATE_READY} or {@link #STATE_BUFFERING} states with {@code playWhenReady = true}. The locks
     * held depend on the specified {@link C.WakeMode}.
     *
     * @param wakeMode A {@link C.WakeMode}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setWakeMode(@C.WakeMode int wakeMode) {
      Assertions.checkState(!buildCalled);
      this.wakeMode = wakeMode;
      return this;
    }

    /**
     * Sets whether the player should pause automatically when audio is rerouted from a headset to
     * device speakers. See the <a
     * href="https://developer.android.com/guide/topics/media-apps/volume-and-earphones#becoming-noisy">audio
     * becoming noisy</a> documentation for more information.
     *
     * @param handleAudioBecomingNoisy Whether the player should pause automatically when audio is
     *     rerouted from a headset to device speakers.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
      Assertions.checkState(!buildCalled);
      this.handleAudioBecomingNoisy = handleAudioBecomingNoisy;
      return this;
    }

    /**
     * Sets whether silences silences in the audio stream is enabled.
     *
     * @param skipSilenceEnabled Whether skipping silences is enabled.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setSkipSilenceEnabled(boolean skipSilenceEnabled) {
      Assertions.checkState(!buildCalled);
      this.skipSilenceEnabled = skipSilenceEnabled;
      return this;
    }

    /**
     * Sets the {@link C.VideoScalingMode} that will be used by the player.
     *
     * <p>Note that the scaling mode only applies if a {@link MediaCodec}-based video {@link
     * Renderer} is enabled and if the output surface is owned by a {@link
     * android.view.SurfaceView}.
     *
     * @param videoScalingMode A {@link C.VideoScalingMode}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
      Assertions.checkState(!buildCalled);
      this.videoScalingMode = videoScalingMode;
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
     * Sets the parameters that control how seek operations are performed.
     *
     * @param seekParameters The {@link SeekParameters}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setSeekParameters(SeekParameters seekParameters) {
      Assertions.checkState(!buildCalled);
      this.seekParameters = seekParameters;
      return this;
    }

    /**
     * Sets a timeout for calls to {@link #release} and {@link #setForegroundMode}.
     *
     * <p>If a call to {@link #release} or {@link #setForegroundMode} takes more than {@code
     * timeoutMs} to complete, the player will report an error via {@link
     * Player.Listener#onPlayerError}.
     *
     * @param releaseTimeoutMs The release timeout, in milliseconds.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setReleaseTimeoutMs(long releaseTimeoutMs) {
      Assertions.checkState(!buildCalled);
      this.releaseTimeoutMs = releaseTimeoutMs;
      return this;
    }

    /**
     * Sets a timeout for detaching a surface from the player.
     *
     * <p>If detaching a surface or replacing a surface takes more than {@code
     * detachSurfaceTimeoutMs} to complete, the player will report an error via {@link
     * Player.Listener#onPlayerError}.
     *
     * @param detachSurfaceTimeoutMs The timeout for detaching a surface, in milliseconds.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setDetachSurfaceTimeoutMs(long detachSurfaceTimeoutMs) {
      Assertions.checkState(!buildCalled);
      this.detachSurfaceTimeoutMs = detachSurfaceTimeoutMs;
      return this;
    }

    /**
     * Sets whether to pause playback at the end of each media item.
     *
     * <p>This means the player will pause at the end of each window in the current {@link
     * #getCurrentTimeline() timeline}. Listeners will be informed by a call to {@link
     * Player.Listener#onPlayWhenReadyChanged(boolean, int)} with the reason {@link
     * Player#PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM} when this happens.
     *
     * @param pauseAtEndOfMediaItems Whether to pause playback at the end of each media item.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
      Assertions.checkState(!buildCalled);
      this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems;
      return this;
    }

    /**
     * Sets the {@link LivePlaybackSpeedControl} that will control the playback speed when playing
     * live streams, in order to maintain a steady target offset from the live stream edge.
     *
     * @param livePlaybackSpeedControl The {@link LivePlaybackSpeedControl}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setLivePlaybackSpeedControl(LivePlaybackSpeedControl livePlaybackSpeedControl) {
      Assertions.checkState(!buildCalled);
      this.livePlaybackSpeedControl = livePlaybackSpeedControl;
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
     * @throws IllegalStateException If this method has already been called.
     */
    public SimpleExoPlayer build() {
      Assertions.checkState(!buildCalled);
      buildCalled = true;
      return new SimpleExoPlayer(/* builder= */ this);
    }
  }

  private static final String TAG = "SimpleExoPlayer";

  protected final Renderer[] renderers;

  private final ConditionVariable constructorFinished;
  private final Context applicationContext;
  private final ExoPlayerImpl player;
  private final ComponentListener componentListener;
  private final FrameMetadataListener frameMetadataListener;
  private final CopyOnWriteArraySet<VideoListener> videoListeners;
  private final CopyOnWriteArraySet<AudioListener> audioListeners;
  private final CopyOnWriteArraySet<TextOutput> textOutputs;
  private final CopyOnWriteArraySet<MetadataOutput> metadataOutputs;
  private final CopyOnWriteArraySet<DeviceListener> deviceListeners;
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
  private boolean throwsWhenUsingWrongThread;
  private boolean hasNotifiedFullWrongThreadWarning;
  @Nullable private PriorityTaskManager priorityTaskManager;
  private boolean isPriorityTaskManagerRegistered;
  private boolean playerReleased;
  private DeviceInfo deviceInfo;
  private VideoSize videoSize;

  /** @deprecated Use the {@link Builder} and pass it to {@link #SimpleExoPlayer(Builder)}. */
  @Deprecated
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
        new Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setAnalyticsCollector(analyticsCollector)
            .setUseLazyPreparation(useLazyPreparation)
            .setClock(clock)
            .setLooper(applicationLooper));
  }

  /** @param builder The {@link Builder} to obtain all construction parameters. */
  protected SimpleExoPlayer(Builder builder) {
    constructorFinished = new ConditionVariable();
    try {
      applicationContext = builder.context.getApplicationContext();
      analyticsCollector = builder.analyticsCollector;
      priorityTaskManager = builder.priorityTaskManager;
      audioAttributes = builder.audioAttributes;
      videoScalingMode = builder.videoScalingMode;
      skipSilenceEnabled = builder.skipSilenceEnabled;
      detachSurfaceTimeoutMs = builder.detachSurfaceTimeoutMs;
      componentListener = new ComponentListener();
      frameMetadataListener = new FrameMetadataListener();
      videoListeners = new CopyOnWriteArraySet<>();
      audioListeners = new CopyOnWriteArraySet<>();
      textOutputs = new CopyOnWriteArraySet<>();
      metadataOutputs = new CopyOnWriteArraySet<>();
      deviceListeners = new CopyOnWriteArraySet<>();
      Handler eventHandler = new Handler(builder.looper);
      renderers =
          builder.renderersFactory.createRenderers(
              eventHandler,
              componentListener,
              componentListener,
              componentListener,
              componentListener);

      // Set initial values.
      audioVolume = 1;
      if (Util.SDK_INT < 21) {
        audioSessionId = initializeKeepSessionIdAudioTrack(C.AUDIO_SESSION_ID_UNSET);
      } else {
        audioSessionId = C.generateAudioSessionIdV21(applicationContext);
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
              builder.trackSelector,
              builder.mediaSourceFactory,
              builder.loadControl,
              builder.bandwidthMeter,
              analyticsCollector,
              builder.useLazyPreparation,
              builder.seekParameters,
              builder.livePlaybackSpeedControl,
              builder.releaseTimeoutMs,
              builder.pauseAtEndOfMediaItems,
              builder.clock,
              builder.looper,
              /* wrappingPlayer= */ this,
              additionalPermanentAvailableCommands);
      player.addListener(componentListener);
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

      sendRendererMessage(C.TRACK_TYPE_AUDIO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
      sendRendererMessage(C.TRACK_TYPE_VIDEO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
      sendRendererMessage(C.TRACK_TYPE_AUDIO, MSG_SET_AUDIO_ATTRIBUTES, audioAttributes);
      sendRendererMessage(C.TRACK_TYPE_VIDEO, MSG_SET_SCALING_MODE, videoScalingMode);
      sendRendererMessage(C.TRACK_TYPE_AUDIO, MSG_SET_SKIP_SILENCE_ENABLED, skipSilenceEnabled);
      sendRendererMessage(
          C.TRACK_TYPE_VIDEO, MSG_SET_VIDEO_FRAME_METADATA_LISTENER, frameMetadataListener);
      sendRendererMessage(
          C.TRACK_TYPE_CAMERA_MOTION, MSG_SET_CAMERA_MOTION_LISTENER, frameMetadataListener);
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
   * @param videoScalingMode The {@link C.VideoScalingMode}.
   */
  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
    verifyApplicationThread();
    this.videoScalingMode = videoScalingMode;
    sendRendererMessage(C.TRACK_TYPE_VIDEO, MSG_SET_SCALING_MODE, videoScalingMode);
  }

  @Override
  @C.VideoScalingMode
  public int getVideoScalingMode() {
    return videoScalingMode;
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
  public void addAudioListener(AudioListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    Assertions.checkNotNull(listener);
    audioListeners.add(listener);
  }

  @Override
  public void removeAudioListener(AudioListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    audioListeners.remove(listener);
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    verifyApplicationThread();
    if (playerReleased) {
      return;
    }
    if (!Util.areEqual(this.audioAttributes, audioAttributes)) {
      this.audioAttributes = audioAttributes;
      sendRendererMessage(C.TRACK_TYPE_AUDIO, MSG_SET_AUDIO_ATTRIBUTES, audioAttributes);
      streamVolumeManager.setStreamType(Util.getStreamTypeForAudioUsage(audioAttributes.usage));
      analyticsCollector.onAudioAttributesChanged(audioAttributes);
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
    if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
      if (Util.SDK_INT < 21) {
        audioSessionId = initializeKeepSessionIdAudioTrack(C.AUDIO_SESSION_ID_UNSET);
      } else {
        audioSessionId = C.generateAudioSessionIdV21(applicationContext);
      }
    } else if (Util.SDK_INT < 21) {
      // We need to re-initialize keepSessionIdAudioTrack to make sure the session is kept alive for
      // as long as the player is using it.
      initializeKeepSessionIdAudioTrack(audioSessionId);
    }
    this.audioSessionId = audioSessionId;
    sendRendererMessage(C.TRACK_TYPE_AUDIO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
    sendRendererMessage(C.TRACK_TYPE_VIDEO, MSG_SET_AUDIO_SESSION_ID, audioSessionId);
    analyticsCollector.onAudioSessionIdChanged(audioSessionId);
    for (AudioListener audioListener : audioListeners) {
      audioListener.onAudioSessionIdChanged(audioSessionId);
    }
  }

  @Override
  public int getAudioSessionId() {
    return audioSessionId;
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    verifyApplicationThread();
    sendRendererMessage(C.TRACK_TYPE_AUDIO, MSG_SET_AUX_EFFECT_INFO, auxEffectInfo);
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
    analyticsCollector.onVolumeChanged(audioVolume);
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
    sendRendererMessage(C.TRACK_TYPE_AUDIO, MSG_SET_SKIP_SILENCE_ENABLED, skipSilenceEnabled);
    notifySkipSilenceEnabledChanged();
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
    // Don't verify application thread. We allow calls to this method from any thread.
    Assertions.checkNotNull(listener);
    analyticsCollector.addListener(listener);
  }

  /**
   * Removes an {@link AnalyticsListener}.
   *
   * @param listener The listener to be removed.
   */
  public void removeAnalyticsListener(AnalyticsListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    analyticsCollector.removeListener(listener);
  }

  /**
   * Sets whether the player should pause automatically when audio is rerouted from a headset to
   * device speakers. See the <a
   * href="https://developer.android.com/guide/topics/media-apps/volume-and-earphones#becoming-noisy">audio
   * becoming noisy</a> documentation for more information.
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
  public void addVideoListener(VideoListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    Assertions.checkNotNull(listener);
    videoListeners.add(listener);
  }

  @Override
  public void removeVideoListener(VideoListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    videoListeners.remove(listener);
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
  public void addTextOutput(TextOutput listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    Assertions.checkNotNull(listener);
    textOutputs.add(listener);
  }

  @Override
  public void removeTextOutput(TextOutput listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    textOutputs.remove(listener);
  }

  @Override
  public List<Cue> getCurrentCues() {
    verifyApplicationThread();
    return currentCues;
  }

  @Override
  public void addMetadataOutput(MetadataOutput listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    Assertions.checkNotNull(listener);
    metadataOutputs.add(listener);
  }

  @Override
  public void removeMetadataOutput(MetadataOutput listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    metadataOutputs.remove(listener);
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
    Assertions.checkNotNull(listener);
    addAudioListener(listener);
    addVideoListener(listener);
    addTextOutput(listener);
    addMetadataOutput(listener);
    addDeviceListener(listener);
    EventListener eventListener = listener;
    addListener(eventListener);
  }

  @Override
  public void addListener(Player.EventListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    Assertions.checkNotNull(listener);
    player.addListener(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    Assertions.checkNotNull(listener);
    removeAudioListener(listener);
    removeVideoListener(listener);
    removeTextOutput(listener);
    removeMetadataOutput(listener);
    removeDeviceListener(listener);
    EventListener eventListener = listener;
    removeListener(eventListener);
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
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
  public void setMediaItems(
      List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs) {
    verifyApplicationThread();
    player.setMediaItems(mediaItems, startWindowIndex, startPositionMs);
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
      List<MediaSource> mediaSources, int startWindowIndex, long startPositionMs) {
    verifyApplicationThread();
    player.setMediaSources(mediaSources, startWindowIndex, startPositionMs);
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
  public void seekTo(int windowIndex, long positionMs) {
    verifyApplicationThread();
    analyticsCollector.notifySeekStarted();
    player.seekTo(windowIndex, positionMs);
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
      Assertions.checkNotNull(priorityTaskManager).remove(C.PRIORITY_PLAYBACK);
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
  public int getRendererType(int index) {
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
  public List<Metadata> getCurrentStaticMetadata() {
    verifyApplicationThread();
    return player.getCurrentStaticMetadata();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    return player.getMediaMetadata();
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
  public void addDeviceListener(DeviceListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    Assertions.checkNotNull(listener);
    deviceListeners.add(listener);
  }

  @Override
  public void removeDeviceListener(DeviceListener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
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

  /**
   * Sets whether the player should throw an {@link IllegalStateException} when methods are called
   * from a thread other than the one associated with {@link #getApplicationLooper()}.
   *
   * <p>The default is {@code true} and this method will be removed in the future.
   *
   * @param throwsWhenUsingWrongThread Whether to throw when methods are called from a wrong thread.
   * @deprecated Disabling the enforcement can result in hard-to-detect bugs. Do not use this method
   *     except to ease the transition while wrong thread access problems are fixed.
   */
  @Deprecated
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
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        messages.add(
            player
                .createMessage(renderer)
                .setType(MSG_SET_VIDEO_OUTPUT)
                .setPayload(videoOutput)
                .send());
      }
    }
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
        // One of the renderers timed out releasing its resources.
        player.stop(
            /* reset= */ false,
            ExoPlaybackException.createForRenderer(
                new ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE)));
      }
      if (this.videoOutput == ownedSurface) {
        // We're replacing a surface that we are responsible for releasing.
        ownedSurface.release();
        ownedSurface = null;
      }
    }
    this.videoOutput = videoOutput;
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
      for (VideoListener videoListener : videoListeners) {
        videoListener.onSurfaceSizeChanged(width, height);
      }
    }
  }

  private void sendVolumeToRenderers() {
    float scaledVolume = audioVolume * audioFocusManager.getVolumeMultiplier();
    sendRendererMessage(C.TRACK_TYPE_AUDIO, MSG_SET_VOLUME, scaledVolume);
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  private void notifySkipSilenceEnabledChanged() {
    analyticsCollector.onSkipSilenceEnabledChanged(skipSilenceEnabled);
    for (AudioListener listener : audioListeners) {
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

  private void sendRendererMessage(int trackType, int messageType, @Nullable Object payload) {
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
      for (VideoListener videoListener : videoListeners) {
        videoListener.onVideoSizeChanged(videoSize);
        videoListener.onVideoSizeChanged(
            videoSize.width,
            videoSize.height,
            videoSize.unappliedRotationDegrees,
            videoSize.pixelWidthHeightRatio);
      }
    }

    @Override
    public void onRenderedFirstFrame(Object output, long renderTimeMs) {
      analyticsCollector.onRenderedFirstFrame(output, renderTimeMs);
      if (videoOutput == output) {
        for (VideoListener videoListener : videoListeners) {
          videoListener.onRenderedFirstFrame();
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
      for (TextOutput textOutput : textOutputs) {
        textOutput.onCues(cues);
      }
    }

    // MetadataOutput implementation

    @Override
    public void onMetadata(Metadata metadata) {
      analyticsCollector.onMetadata(metadata);
      player.onMetadata(metadata);
      for (MetadataOutput metadataOutput : metadataOutputs) {
        metadataOutput.onMetadata(metadata);
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

    // Player.AudioOffloadListener implementation.

    @Override
    public void onExperimentalSleepingForOffloadChanged(boolean sleepingForOffload) {
      updateWakeAndWifiLock();
    }
  }

  /** Listeners that are called on the playback thread. */
  private static final class FrameMetadataListener
      implements VideoFrameMetadataListener, CameraMotionListener, PlayerMessage.Target {

    public static final int MSG_SET_VIDEO_FRAME_METADATA_LISTENER =
        Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER;
    public static final int MSG_SET_CAMERA_MOTION_LISTENER =
        Renderer.MSG_SET_CAMERA_MOTION_LISTENER;
    public static final int MSG_SET_SPHERICAL_SURFACE_VIEW = Renderer.MSG_CUSTOM_BASE;

    @Nullable private VideoFrameMetadataListener videoFrameMetadataListener;
    @Nullable private CameraMotionListener cameraMotionListener;
    @Nullable private VideoFrameMetadataListener internalVideoFrameMetadataListener;
    @Nullable private CameraMotionListener internalCameraMotionListener;

    @Override
    public void handleMessage(int messageType, @Nullable Object payload) {
      switch (messageType) {
        case MSG_SET_VIDEO_FRAME_METADATA_LISTENER:
          videoFrameMetadataListener = (VideoFrameMetadataListener) payload;
          break;
        case MSG_SET_CAMERA_MOTION_LISTENER:
          cameraMotionListener = (CameraMotionListener) payload;
          break;
        case MSG_SET_SPHERICAL_SURFACE_VIEW:
          SphericalGLSurfaceView surfaceView = (SphericalGLSurfaceView) payload;
          if (surfaceView == null) {
            internalVideoFrameMetadataListener = null;
            internalCameraMotionListener = null;
          } else {
            internalVideoFrameMetadataListener = surfaceView.getVideoFrameMetadataListener();
            internalCameraMotionListener = surfaceView.getCameraMotionListener();
          }
          break;
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
