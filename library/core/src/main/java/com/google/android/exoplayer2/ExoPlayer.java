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
import android.media.AudioTrack;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.device.DeviceListener;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import java.util.List;

/**
 * An extensible media player that plays {@link MediaSource}s. Instances can be obtained from {@link
 * SimpleExoPlayer.Builder}.
 *
 * <h3>Player components</h3>
 *
 * <p>ExoPlayer is designed to make few assumptions about (and hence impose few restrictions on) the
 * type of the media being played, how and where it is stored, and how it is rendered. Rather than
 * implementing the loading and rendering of media directly, ExoPlayer implementations delegate this
 * work to components that are injected when a player is created or when it's prepared for playback.
 * Components common to all ExoPlayer implementations are:
 *
 * <ul>
 *   <li><b>{@link MediaSource MediaSources}</b> that define the media to be played, load the media,
 *       and from which the loaded media can be read. MediaSources are created from {@link MediaItem
 *       MediaItems} by the {@link MediaSourceFactory} injected into the player {@link
 *       SimpleExoPlayer.Builder#setMediaSourceFactory Builder}, or can be added directly by methods
 *       like {@link #setMediaSource(MediaSource)}. The library provides a {@link
 *       DefaultMediaSourceFactory} for progressive media files, DASH, SmoothStreaming and HLS,
 *       which also includes functionality for side-loading subtitle files and clipping media.
 *   <li><b>{@link Renderer}</b>s that render individual components of the media. The library
 *       provides default implementations for common media types ({@link MediaCodecVideoRenderer},
 *       {@link MediaCodecAudioRenderer}, {@link TextRenderer} and {@link MetadataRenderer}). A
 *       Renderer consumes media from the MediaSource being played. Renderers are injected when the
 *       player is created. The number of renderers and their respective track types can be obtained
 *       by calling {@link #getRendererCount()} and {@link #getRendererType(int)}.
 *   <li>A <b>{@link TrackSelector}</b> that selects tracks provided by the MediaSource to be
 *       consumed by each of the available Renderers. The library provides a default implementation
 *       ({@link DefaultTrackSelector}) suitable for most use cases. A TrackSelector is injected
 *       when the player is created.
 *   <li>A <b>{@link LoadControl}</b> that controls when the MediaSource buffers more media, and how
 *       much media is buffered. The library provides a default implementation ({@link
 *       DefaultLoadControl}) suitable for most use cases. A LoadControl is injected when the player
 *       is created.
 * </ul>
 *
 * <p>An ExoPlayer can be built using the default components provided by the library, but may also
 * be built using custom implementations if non-standard behaviors are required. For example a
 * custom LoadControl could be injected to change the player's buffering strategy, or a custom
 * Renderer could be injected to add support for a video codec not supported natively by Android.
 *
 * <p>The concept of injecting components that implement pieces of player functionality is present
 * throughout the library. The default component implementations listed above delegate work to
 * further injected components. This allows many sub-components to be individually replaced with
 * custom implementations. For example the default MediaSource implementations require one or more
 * {@link DataSource} factories to be injected via their constructors. By providing a custom factory
 * it's possible to load data from a non-standard source, or through a different network stack.
 *
 * <h3>Threading model</h3>
 *
 * <p>The figure below shows ExoPlayer's threading model.
 *
 * <p style="align:center"><img src="doc-files/exoplayer-threading-model.svg" alt="ExoPlayer's
 * threading model">
 *
 * <ul>
 *   <li>ExoPlayer instances must be accessed from a single application thread. For the vast
 *       majority of cases this should be the application's main thread. Using the application's
 *       main thread is also a requirement when using ExoPlayer's UI components or the IMA
 *       extension. The thread on which an ExoPlayer instance must be accessed can be explicitly
 *       specified by passing a `Looper` when creating the player. If no `Looper` is specified, then
 *       the `Looper` of the thread that the player is created on is used, or if that thread does
 *       not have a `Looper`, the `Looper` of the application's main thread is used. In all cases
 *       the `Looper` of the thread from which the player must be accessed can be queried using
 *       {@link #getApplicationLooper()}.
 *   <li>Registered listeners are called on the thread associated with {@link
 *       #getApplicationLooper()}. Note that this means registered listeners are called on the same
 *       thread which must be used to access the player.
 *   <li>An internal playback thread is responsible for playback. Injected player components such as
 *       Renderers, MediaSources, TrackSelectors and LoadControls are called by the player on this
 *       thread.
 *   <li>When the application performs an operation on the player, for example a seek, a message is
 *       delivered to the internal playback thread via a message queue. The internal playback thread
 *       consumes messages from the queue and performs the corresponding operations. Similarly, when
 *       a playback event occurs on the internal playback thread, a message is delivered to the
 *       application thread via a second message queue. The application thread consumes messages
 *       from the queue, updating the application visible state and calling corresponding listener
 *       methods.
 *   <li>Injected player components may use additional background threads. For example a MediaSource
 *       may use background threads to load data. These are implementation specific.
 * </ul>
 */
public interface ExoPlayer extends Player {

  /** The audio component of an {@link ExoPlayer}. */
  interface AudioComponent {

    /**
     * Adds a listener to receive audio events.
     *
     * @param listener The listener to register.
     * @deprecated Use {@link #addListener(Listener)}.
     */
    @Deprecated
    void addAudioListener(AudioListener listener);

    /**
     * Removes a listener of audio events.
     *
     * @param listener The listener to unregister.
     * @deprecated Use {@link #removeListener(Listener)}.
     */
    @Deprecated
    void removeAudioListener(AudioListener listener);

    /**
     * Sets the attributes for audio playback, used by the underlying audio track. If not set, the
     * default audio attributes will be used. They are suitable for general media playback.
     *
     * <p>Setting the audio attributes during playback may introduce a short gap in audio output as
     * the audio track is recreated. A new audio session id will also be generated.
     *
     * <p>If tunneling is enabled by the track selector, the specified audio attributes will be
     * ignored, but they will take effect if audio is later played without tunneling.
     *
     * <p>If the device is running a build before platform API version 21, audio attributes cannot
     * be set directly on the underlying audio track. In this case, the usage will be mapped onto an
     * equivalent stream type using {@link Util#getStreamTypeForAudioUsage(int)}.
     *
     * <p>If audio focus should be handled, the {@link AudioAttributes#usage} must be {@link
     * C#USAGE_MEDIA} or {@link C#USAGE_GAME}. Other usages will throw an {@link
     * IllegalArgumentException}.
     *
     * @param audioAttributes The attributes to use for audio playback.
     * @param handleAudioFocus True if the player should handle audio focus, false otherwise.
     */
    void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus);

    /** Returns the attributes for audio playback. */
    AudioAttributes getAudioAttributes();

    /**
     * Sets the ID of the audio session to attach to the underlying {@link
     * android.media.AudioTrack}.
     *
     * <p>The audio session ID can be generated using {@link C#generateAudioSessionIdV21(Context)}
     * for API 21+.
     *
     * @param audioSessionId The audio session ID, or {@link C#AUDIO_SESSION_ID_UNSET} if it should
     *     be generated by the framework.
     */
    void setAudioSessionId(int audioSessionId);

    /** Returns the audio session identifier, or {@link C#AUDIO_SESSION_ID_UNSET} if not set. */
    int getAudioSessionId();

    /** Sets information on an auxiliary audio effect to attach to the underlying audio track. */
    void setAuxEffectInfo(AuxEffectInfo auxEffectInfo);

    /** Detaches any previously attached auxiliary audio effect from the underlying audio track. */
    void clearAuxEffectInfo();

    /**
     * Sets the audio volume, with 0 being silence and 1 being unity gain (signal unchanged).
     *
     * @param audioVolume Linear output gain to apply to all audio channels.
     */
    void setVolume(float audioVolume);

    /**
     * Returns the audio volume, with 0 being silence and 1 being unity gain (signal unchanged).
     *
     * @return The linear gain applied to all audio channels.
     */
    float getVolume();

    /**
     * Sets whether skipping silences in the audio stream is enabled.
     *
     * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
     */
    void setSkipSilenceEnabled(boolean skipSilenceEnabled);

    /** Returns whether skipping silences in the audio stream is enabled. */
    boolean getSkipSilenceEnabled();
  }

  /** The video component of an {@link ExoPlayer}. */
  interface VideoComponent {

    /**
     * Sets the {@link C.VideoScalingMode}.
     *
     * @param videoScalingMode The {@link C.VideoScalingMode}.
     */
    void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode);

    /** Returns the {@link C.VideoScalingMode}. */
    @C.VideoScalingMode
    int getVideoScalingMode();

    /**
     * Adds a listener to receive video events.
     *
     * @param listener The listener to register.
     * @deprecated Use {@link #addListener(Listener)}.
     */
    @Deprecated
    void addVideoListener(VideoListener listener);

    /**
     * Removes a listener of video events.
     *
     * @param listener The listener to unregister.
     * @deprecated Use {@link #removeListener(Listener)}.
     */
    @Deprecated
    void removeVideoListener(VideoListener listener);

    /**
     * Sets a listener to receive video frame metadata events.
     *
     * <p>This method is intended to be called by the same component that sets the {@link Surface}
     * onto which video will be rendered. If using ExoPlayer's standard UI components, this method
     * should not be called directly from application code.
     *
     * @param listener The listener.
     */
    void setVideoFrameMetadataListener(VideoFrameMetadataListener listener);

    /**
     * Clears the listener which receives video frame metadata events if it matches the one passed.
     * Else does nothing.
     *
     * @param listener The listener to clear.
     */
    void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener);

    /**
     * Sets a listener of camera motion events.
     *
     * @param listener The listener.
     */
    void setCameraMotionListener(CameraMotionListener listener);

    /**
     * Clears the listener which receives camera motion events if it matches the one passed. Else
     * does nothing.
     *
     * @param listener The listener to clear.
     */
    void clearCameraMotionListener(CameraMotionListener listener);

    /**
     * Clears any {@link Surface}, {@link SurfaceHolder}, {@link SurfaceView} or {@link TextureView}
     * currently set on the player.
     */
    void clearVideoSurface();

    /**
     * Clears the {@link Surface} onto which video is being rendered if it matches the one passed.
     * Else does nothing.
     *
     * @param surface The surface to clear.
     */
    void clearVideoSurface(@Nullable Surface surface);

    /**
     * Sets the {@link Surface} onto which video will be rendered. The caller is responsible for
     * tracking the lifecycle of the surface, and must clear the surface by calling {@code
     * setVideoSurface(null)} if the surface is destroyed.
     *
     * <p>If the surface is held by a {@link SurfaceView}, {@link TextureView} or {@link
     * SurfaceHolder} then it's recommended to use {@link #setVideoSurfaceView(SurfaceView)}, {@link
     * #setVideoTextureView(TextureView)} or {@link #setVideoSurfaceHolder(SurfaceHolder)} rather
     * than this method, since passing the holder allows the player to track the lifecycle of the
     * surface automatically.
     *
     * @param surface The {@link Surface}.
     */
    void setVideoSurface(@Nullable Surface surface);

    /**
     * Sets the {@link SurfaceHolder} that holds the {@link Surface} onto which video will be
     * rendered. The player will track the lifecycle of the surface automatically.
     *
     * @param surfaceHolder The surface holder.
     */
    void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

    /**
     * Clears the {@link SurfaceHolder} that holds the {@link Surface} onto which video is being
     * rendered if it matches the one passed. Else does nothing.
     *
     * @param surfaceHolder The surface holder to clear.
     */
    void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

    /**
     * Sets the {@link SurfaceView} onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     * @param surfaceView The surface view.
     */
    void setVideoSurfaceView(@Nullable SurfaceView surfaceView);

    /**
     * Clears the {@link SurfaceView} onto which video is being rendered if it matches the one
     * passed. Else does nothing.
     *
     * @param surfaceView The texture view to clear.
     */
    void clearVideoSurfaceView(@Nullable SurfaceView surfaceView);

    /**
     * Sets the {@link TextureView} onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     * @param textureView The texture view.
     */
    void setVideoTextureView(@Nullable TextureView textureView);

    /**
     * Clears the {@link TextureView} onto which video is being rendered if it matches the one
     * passed. Else does nothing.
     *
     * @param textureView The texture view to clear.
     */
    void clearVideoTextureView(@Nullable TextureView textureView);

    /**
     * Gets the size of the video.
     *
     * <p>The width and height of size could be 0 if there is no video or the size has not been
     * determined yet.
     *
     * @see Listener#onVideoSizeChanged(int, int, int, float)
     */
    VideoSize getVideoSize();
  }

  /** The text component of an {@link ExoPlayer}. */
  interface TextComponent {

    /**
     * Registers an output to receive text events.
     *
     * @param listener The output to register.
     * @deprecated Use {@link #addListener(Listener)}.
     */
    @Deprecated
    void addTextOutput(TextOutput listener);

    /**
     * Removes a text output.
     *
     * @param listener The output to remove.
     * @deprecated Use {@link #removeListener(Listener)}.
     */
    @Deprecated
    void removeTextOutput(TextOutput listener);

    /** Returns the current {@link Cue Cues}. This list may be empty. */
    List<Cue> getCurrentCues();
  }

  /** The metadata component of an {@link ExoPlayer}. */
  interface MetadataComponent {

    /**
     * Adds a {@link MetadataOutput} to receive metadata.
     *
     * @param output The output to register.
     * @deprecated Use {@link #addListener(Listener)}.
     */
    @Deprecated
    void addMetadataOutput(MetadataOutput output);

    /**
     * Removes a {@link MetadataOutput}.
     *
     * @param output The output to remove.
     * @deprecated Use {@link #removeListener(Listener)}.
     */
    @Deprecated
    void removeMetadataOutput(MetadataOutput output);
  }

  /** The device component of an {@link ExoPlayer}. */
  interface DeviceComponent {

    /**
     * Adds a listener to receive device events.
     *
     * @deprecated Use {@link #addListener(Listener)}.
     */
    @Deprecated
    void addDeviceListener(DeviceListener listener);

    /**
     * Removes a listener of device events.
     *
     * @deprecated Use {@link #removeListener(Listener)}.
     */
    @Deprecated
    void removeDeviceListener(DeviceListener listener);

    /** Gets the device information. */
    DeviceInfo getDeviceInfo();

    /**
     * Gets the current volume of the device.
     *
     * <p>For devices with {@link DeviceInfo#PLAYBACK_TYPE_LOCAL local playback}, the volume
     * returned by this method varies according to the current {@link C.StreamType stream type}. The
     * stream type is determined by {@link AudioAttributes#usage} which can be converted to stream
     * type with {@link Util#getStreamTypeForAudioUsage(int)}.
     *
     * <p>For devices with {@link DeviceInfo#PLAYBACK_TYPE_REMOTE remote playback}, the volume of
     * the remote device is returned.
     */
    int getDeviceVolume();

    /** Gets whether the device is muted or not. */
    boolean isDeviceMuted();

    /**
     * Sets the volume of the device.
     *
     * @param volume The volume to set.
     */
    void setDeviceVolume(int volume);

    /** Increases the volume of the device. */
    void increaseDeviceVolume();

    /** Decreases the volume of the device. */
    void decreaseDeviceVolume();

    /** Sets the mute state of the device. */
    void setDeviceMuted(boolean muted);
  }

  /**
   * The default timeout for calls to {@link #release} and {@link #setForegroundMode}, in
   * milliseconds.
   */
  long DEFAULT_RELEASE_TIMEOUT_MS = 500;

  /**
   * A listener for audio offload events.
   *
   * <p>This class is experimental, and might be renamed, moved or removed in a future release.
   */
  interface AudioOffloadListener {
    /**
     * Called when the player has started or stopped offload scheduling using {@link
     * ExoPlayer#experimentalSetOffloadSchedulingEnabled(boolean)}.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     */
    default void onExperimentalOffloadSchedulingEnabledChanged(boolean offloadSchedulingEnabled) {}

    /**
     * Called when the player has started or finished sleeping for offload.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     */
    default void onExperimentalSleepingForOffloadChanged(boolean sleepingForOffload) {}
  }

  /**
   * A builder for {@link ExoPlayer} instances.
   *
   * <p>See {@link #Builder(Context, Renderer...)} for the list of default values.
   *
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead.
   */
  @Deprecated
  final class Builder {

    private final Renderer[] renderers;

    private Clock clock;
    private TrackSelector trackSelector;
    private MediaSourceFactory mediaSourceFactory;
    private LoadControl loadControl;
    private BandwidthMeter bandwidthMeter;
    private Looper looper;
    @Nullable private AnalyticsCollector analyticsCollector;
    private boolean useLazyPreparation;
    private SeekParameters seekParameters;
    private boolean pauseAtEndOfMediaItems;
    private long releaseTimeoutMs;
    private LivePlaybackSpeedControl livePlaybackSpeedControl;
    private boolean buildCalled;

    private long setForegroundModeTimeoutMs;

    /**
     * Creates a builder with a list of {@link Renderer Renderers}.
     *
     * <p>The builder uses the following default values:
     *
     * <ul>
     *   <li>{@link TrackSelector}: {@link DefaultTrackSelector}
     *   <li>{@link MediaSourceFactory}: {@link DefaultMediaSourceFactory}
     *   <li>{@link LoadControl}: {@link DefaultLoadControl}
     *   <li>{@link BandwidthMeter}: {@link DefaultBandwidthMeter#getSingletonInstance(Context)}
     *   <li>{@link LivePlaybackSpeedControl}: {@link DefaultLivePlaybackSpeedControl}
     *   <li>{@link Looper}: The {@link Looper} associated with the current thread, or the {@link
     *       Looper} of the application's main thread if the current thread doesn't have a {@link
     *       Looper}
     *   <li>{@link AnalyticsCollector}: {@link AnalyticsCollector} with {@link Clock#DEFAULT}
     *   <li>{@code useLazyPreparation}: {@code true}
     *   <li>{@link SeekParameters}: {@link SeekParameters#DEFAULT}
     *   <li>{@code releaseTimeoutMs}: {@link ExoPlayer#DEFAULT_RELEASE_TIMEOUT_MS}
     *   <li>{@code pauseAtEndOfMediaItems}: {@code false}
     *   <li>{@link Clock}: {@link Clock#DEFAULT}
     * </ul>
     *
     * @param context A {@link Context}.
     * @param renderers The {@link Renderer Renderers} to be used by the player.
     */
    public Builder(Context context, Renderer... renderers) {
      this(
          renderers,
          new DefaultTrackSelector(context),
          new DefaultMediaSourceFactory(context),
          new DefaultLoadControl(),
          DefaultBandwidthMeter.getSingletonInstance(context));
    }

    /**
     * Creates a builder with the specified custom components.
     *
     * <p>Note that this constructor is only useful to try and ensure that ExoPlayer's default
     * components can be removed by ProGuard or R8.
     *
     * @param renderers The {@link Renderer Renderers} to be used by the player.
     * @param trackSelector A {@link TrackSelector}.
     * @param mediaSourceFactory A {@link MediaSourceFactory}.
     * @param loadControl A {@link LoadControl}.
     * @param bandwidthMeter A {@link BandwidthMeter}.
     */
    public Builder(
        Renderer[] renderers,
        TrackSelector trackSelector,
        MediaSourceFactory mediaSourceFactory,
        LoadControl loadControl,
        BandwidthMeter bandwidthMeter) {
      Assertions.checkArgument(renderers.length > 0);
      this.renderers = renderers;
      this.trackSelector = trackSelector;
      this.mediaSourceFactory = mediaSourceFactory;
      this.loadControl = loadControl;
      this.bandwidthMeter = bandwidthMeter;
      looper = Util.getCurrentOrMainLooper();
      useLazyPreparation = true;
      seekParameters = SeekParameters.DEFAULT;
      livePlaybackSpeedControl = new DefaultLivePlaybackSpeedControl.Builder().build();
      clock = Clock.DEFAULT;
      releaseTimeoutMs = DEFAULT_RELEASE_TIMEOUT_MS;
    }

    /**
     * Set a limit on the time a call to {@link ExoPlayer#setForegroundMode} can spend. If a call to
     * {@link ExoPlayer#setForegroundMode} takes more than {@code timeoutMs} milliseconds to
     * complete, the player will raise an error via {@link Player.Listener#onPlayerError}.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     *
     * @param timeoutMs The time limit in milliseconds.
     */
    public Builder experimentalSetForegroundModeTimeoutMs(long timeoutMs) {
      Assertions.checkState(!buildCalled);
      setForegroundModeTimeoutMs = timeoutMs;
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
     * Builds an {@link ExoPlayer} instance.
     *
     * @throws IllegalStateException If {@code build} has already been called.
     */
    public ExoPlayer build() {
      Assertions.checkState(!buildCalled);
      buildCalled = true;
      ExoPlayerImpl player =
          new ExoPlayerImpl(
              renderers,
              trackSelector,
              mediaSourceFactory,
              loadControl,
              bandwidthMeter,
              analyticsCollector,
              useLazyPreparation,
              seekParameters,
              livePlaybackSpeedControl,
              releaseTimeoutMs,
              pauseAtEndOfMediaItems,
              clock,
              looper,
              /* wrappingPlayer= */ null,
              /* additionalPermanentAvailableCommands= */ Commands.EMPTY);

      if (setForegroundModeTimeoutMs > 0) {
        player.experimentalSetForegroundModeTimeoutMs(setForegroundModeTimeoutMs);
      }
      return player;
    }
  }

  /** Returns the component of this player for audio output, or null if audio is not supported. */
  @Nullable
  AudioComponent getAudioComponent();

  /** Returns the component of this player for video output, or null if video is not supported. */
  @Nullable
  VideoComponent getVideoComponent();

  /** Returns the component of this player for text output, or null if text is not supported. */
  @Nullable
  TextComponent getTextComponent();

  /**
   * Returns the component of this player for metadata output, or null if metadata is not supported.
   */
  @Nullable
  MetadataComponent getMetadataComponent();

  /** Returns the component of this player for playback device, or null if it's not supported. */
  @Nullable
  DeviceComponent getDeviceComponent();

  /**
   * Adds a listener to receive audio offload events.
   *
   * @param listener The listener to register.
   */
  void addAudioOffloadListener(AudioOffloadListener listener);

  /**
   * Removes a listener of audio offload events.
   *
   * @param listener The listener to unregister.
   */
  void removeAudioOffloadListener(AudioOffloadListener listener);

  /** Returns the number of renderers. */
  int getRendererCount();

  /**
   * Returns the track type that the renderer at a given index handles.
   *
   * <p>For example, a video renderer will return {@link C#TRACK_TYPE_VIDEO}, an audio renderer will
   * return {@link C#TRACK_TYPE_AUDIO} and a text renderer will return {@link C#TRACK_TYPE_TEXT}.
   *
   * @param index The index of the renderer.
   * @return One of the {@code TRACK_TYPE_*} constants defined in {@link C}.
   */
  int getRendererType(int index);

  /**
   * Returns the track selector that this player uses, or null if track selection is not supported.
   */
  @Nullable
  TrackSelector getTrackSelector();

  /** Returns the {@link Looper} associated with the playback thread. */
  Looper getPlaybackLooper();

  /** Returns the {@link Clock} used for playback. */
  Clock getClock();

  /** @deprecated Use {@link #prepare()} instead. */
  @Deprecated
  void retry();

  /** @deprecated Use {@link #setMediaSource(MediaSource)} and {@link #prepare()} instead. */
  @Deprecated
  void prepare(MediaSource mediaSource);

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource, boolean)} and {@link #prepare()} instead.
   */
  @Deprecated
  void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState);

  /**
   * Clears the playlist, adds the specified {@link MediaSource MediaSources} and resets the
   * position to the default position.
   *
   * @param mediaSources The new {@link MediaSource MediaSources}.
   */
  void setMediaSources(List<MediaSource> mediaSources);

  /**
   * Clears the playlist and adds the specified {@link MediaSource MediaSources}.
   *
   * @param mediaSources The new {@link MediaSource MediaSources}.
   * @param resetPosition Whether the playback position should be reset to the default position in
   *     the first {@link Timeline.Window}. If false, playback will start from the position defined
   *     by {@link #getCurrentWindowIndex()} and {@link #getCurrentPosition()}.
   */
  void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition);

  /**
   * Clears the playlist and adds the specified {@link MediaSource MediaSources}.
   *
   * @param mediaSources The new {@link MediaSource MediaSources}.
   * @param startWindowIndex The window index to start playback from. If {@link C#INDEX_UNSET} is
   *     passed, the current position is not reset.
   * @param startPositionMs The position in milliseconds to start playback from. If {@link
   *     C#TIME_UNSET} is passed, the default position of the given window is used. In any case, if
   *     {@code startWindowIndex} is set to {@link C#INDEX_UNSET}, this parameter is ignored and the
   *     position is not reset at all.
   */
  void setMediaSources(List<MediaSource> mediaSources, int startWindowIndex, long startPositionMs);

  /**
   * Clears the playlist, adds the specified {@link MediaSource} and resets the position to the
   * default position.
   *
   * @param mediaSource The new {@link MediaSource}.
   */
  void setMediaSource(MediaSource mediaSource);

  /**
   * Clears the playlist and adds the specified {@link MediaSource}.
   *
   * @param mediaSource The new {@link MediaSource}.
   * @param startPositionMs The position in milliseconds to start playback from.
   */
  void setMediaSource(MediaSource mediaSource, long startPositionMs);

  /**
   * Clears the playlist and adds the specified {@link MediaSource}.
   *
   * @param mediaSource The new {@link MediaSource}.
   * @param resetPosition Whether the playback position should be reset to the default position. If
   *     false, playback will start from the position defined by {@link #getCurrentWindowIndex()}
   *     and {@link #getCurrentPosition()}.
   */
  void setMediaSource(MediaSource mediaSource, boolean resetPosition);

  /**
   * Adds a media source to the end of the playlist.
   *
   * @param mediaSource The {@link MediaSource} to add.
   */
  void addMediaSource(MediaSource mediaSource);

  /**
   * Adds a media source at the given index of the playlist.
   *
   * @param index The index at which to add the source.
   * @param mediaSource The {@link MediaSource} to add.
   */
  void addMediaSource(int index, MediaSource mediaSource);

  /**
   * Adds a list of media sources to the end of the playlist.
   *
   * @param mediaSources The {@link MediaSource MediaSources} to add.
   */
  void addMediaSources(List<MediaSource> mediaSources);

  /**
   * Adds a list of media sources at the given index of the playlist.
   *
   * @param index The index at which to add the media sources.
   * @param mediaSources The {@link MediaSource MediaSources} to add.
   */
  void addMediaSources(int index, List<MediaSource> mediaSources);

  /**
   * Sets the shuffle order.
   *
   * @param shuffleOrder The shuffle order.
   */
  void setShuffleOrder(ShuffleOrder shuffleOrder);

  /**
   * Creates a message that can be sent to a {@link PlayerMessage.Target}. By default, the message
   * will be delivered immediately without blocking on the playback thread. The default {@link
   * PlayerMessage#getType()} is 0 and the default {@link PlayerMessage#getPayload()} is null. If a
   * position is specified with {@link PlayerMessage#setPosition(long)}, the message will be
   * delivered at this position in the current window defined by {@link #getCurrentWindowIndex()}.
   * Alternatively, the message can be sent at a specific window using {@link
   * PlayerMessage#setPosition(int, long)}.
   */
  PlayerMessage createMessage(PlayerMessage.Target target);

  /**
   * Sets the parameters that control how seek operations are performed.
   *
   * @param seekParameters The seek parameters, or {@code null} to use the defaults.
   */
  void setSeekParameters(@Nullable SeekParameters seekParameters);

  /** Returns the currently active {@link SeekParameters} of the player. */
  SeekParameters getSeekParameters();

  /**
   * Sets whether the player is allowed to keep holding limited resources such as video decoders,
   * even when in the idle state. By doing so, the player may be able to reduce latency when
   * starting to play another piece of content for which the same resources are required.
   *
   * <p>This mode should be used with caution, since holding limited resources may prevent other
   * players of media components from acquiring them. It should only be enabled when <em>both</em>
   * of the following conditions are true:
   *
   * <ul>
   *   <li>The application that owns the player is in the foreground.
   *   <li>The player is used in a way that may benefit from foreground mode. For this to be true,
   *       the same player instance must be used to play multiple pieces of content, and there must
   *       be gaps between the playbacks (i.e. {@link #stop} is called to halt one playback, and
   *       {@link #prepare} is called some time later to start a new one).
   * </ul>
   *
   * <p>Note that foreground mode is <em>not</em> useful for switching between content without gaps
   * between the playbacks. For this use case {@link #stop} does not need to be called, and simply
   * calling {@link #prepare} for the new media will cause limited resources to be retained even if
   * foreground mode is not enabled.
   *
   * <p>If foreground mode is enabled, it's the application's responsibility to disable it when the
   * conditions described above no longer hold.
   *
   * @param foregroundMode Whether the player is allowed to keep limited resources even when in the
   *     idle state.
   */
  void setForegroundMode(boolean foregroundMode);

  /**
   * Sets whether to pause playback at the end of each media item.
   *
   * <p>This means the player will pause at the end of each window in the current {@link
   * #getCurrentTimeline() timeline}. Listeners will be informed by a call to {@link
   * Player.Listener#onPlayWhenReadyChanged(boolean, int)} with the reason {@link
   * Player#PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM} when this happens.
   *
   * @param pauseAtEndOfMediaItems Whether to pause playback at the end of each media item.
   */
  void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems);

  /**
   * Returns whether the player pauses playback at the end of each media item.
   *
   * @see #setPauseAtEndOfMediaItems(boolean)
   */
  boolean getPauseAtEndOfMediaItems();

  /**
   * Sets whether audio offload scheduling is enabled. If enabled, ExoPlayer's main loop will run as
   * rarely as possible when playing an audio stream using audio offload.
   *
   * <p>Only use this scheduling mode if the player is not displaying anything to the user. For
   * example when the application is in the background, or the screen is off. The player state
   * (including position) is rarely updated (roughly between every 10 seconds and 1 minute).
   *
   * <p>While offload scheduling is enabled, player events may be delivered severely delayed and
   * apps should not interact with the player. When returning to the foreground, disable offload
   * scheduling and wait for {@link
   * AudioOffloadListener#onExperimentalOffloadSchedulingEnabledChanged(boolean)} to be called with
   * {@code offloadSchedulingEnabled = false} before interacting with the player.
   *
   * <p>This mode should save significant power when the phone is playing offload audio with the
   * screen off.
   *
   * <p>This mode only has an effect when playing an audio track in offload mode, which requires all
   * the following:
   *
   * <ul>
   *   <li>Audio offload rendering is enabled in {@link
   *       DefaultRenderersFactory#setEnableAudioOffload} or the equivalent option passed to {@link
   *       DefaultAudioSink#DefaultAudioSink(AudioCapabilities,
   *       DefaultAudioSink.AudioProcessorChain, boolean, boolean, int)}.
   *   <li>An audio track is playing in a format that the device supports offloading (for example,
   *       MP3 or AAC).
   *   <li>The {@link AudioSink} is playing with an offload {@link AudioTrack}.
   * </ul>
   *
   * <p>The state where ExoPlayer main loop has been paused to save power during offload playback
   * can be queried with {@link #experimentalIsSleepingForOffload()}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param offloadSchedulingEnabled Whether to enable offload scheduling.
   */
  void experimentalSetOffloadSchedulingEnabled(boolean offloadSchedulingEnabled);

  /**
   * Returns whether the player has paused its main loop to save power in offload scheduling mode.
   *
   * @see #experimentalSetOffloadSchedulingEnabled(boolean)
   * @see AudioOffloadListener#onExperimentalSleepingForOffloadChanged(boolean)
   */
  boolean experimentalIsSleepingForOffload();
}
