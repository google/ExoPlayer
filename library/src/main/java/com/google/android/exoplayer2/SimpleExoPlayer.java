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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioTrack;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.TrackSelections;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link ExoPlayer} implementation that uses default {@link Renderer} components. Instances can
 * be obtained from {@link ExoPlayerFactory}.
 */
@TargetApi(16)
public final class SimpleExoPlayer implements ExoPlayer {

  /**
   * A listener for video rendering information from a {@link SimpleExoPlayer}.
   */
  public interface VideoListener {

    /**
     * Called each time there's a change in the size of the video being rendered.
     *
     * @param width The video width in pixels.
     * @param height The video height in pixels.
     * @param unappliedRotationDegrees For videos that require a rotation, this is the clockwise
     *     rotation in degrees that the application should apply for the video for it to be rendered
     *     in the correct orientation. This value will always be zero on API levels 21 and above,
     *     since the renderer will apply all necessary rotations internally. On earlier API levels
     *     this is not possible. Applications that use {@link android.view.TextureView} can apply
     *     the rotation by calling {@link android.view.TextureView#setTransform}. Applications that
     *     do not expect to encounter rotated videos can safely ignore this parameter.
     * @param pixelWidthHeightRatio The width to height ratio of each pixel. For the normal case
     *     of square pixels this will be equal to 1.0. Different values are indicative of anamorphic
     *     content.
     */
    void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio);

    /**
     * Called when a frame is rendered for the first time since setting the surface, and when a
     * frame is rendered for the first time since a video track was selected.
     */
    void onRenderedFirstFrame();

    /**
     * Called when a video track is no longer selected.
     */
    void onVideoTracksDisabled();

  }

  private static final String TAG = "SimpleExoPlayer";
  private static final int MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50;

  private final ExoPlayer player;
  private final Renderer[] renderers;
  private final ComponentListener componentListener;
  private final Handler mainHandler;
  private final int videoRendererCount;
  private final int audioRendererCount;

  private boolean videoTracksEnabled;
  private Format videoFormat;
  private Format audioFormat;

  private Surface surface;
  private boolean ownsSurface;
  private SurfaceHolder surfaceHolder;
  private TextureView textureView;
  private TextRenderer.Output textOutput;
  private MetadataRenderer.Output<List<Id3Frame>> id3Output;
  private VideoListener videoListener;
  private AudioRendererEventListener audioDebugListener;
  private VideoRendererEventListener videoDebugListener;
  private DecoderCounters videoDecoderCounters;
  private DecoderCounters audioDecoderCounters;
  private int audioSessionId;
  private float volume;
  private PlaybackParamsHolder playbackParamsHolder;

  /* package */ SimpleExoPlayer(Context context, TrackSelector<?> trackSelector,
      LoadControl loadControl, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean preferExtensionDecoders, long allowedVideoJoiningTimeMs) {
    mainHandler = new Handler();
    componentListener = new ComponentListener();
    trackSelector.addListener(componentListener);

    // Build the renderers.
    ArrayList<Renderer> renderersList = new ArrayList<>();
    if (preferExtensionDecoders) {
      buildExtensionRenderers(renderersList, allowedVideoJoiningTimeMs);
      buildRenderers(context, drmSessionManager, renderersList, allowedVideoJoiningTimeMs);
    } else {
      buildRenderers(context, drmSessionManager, renderersList, allowedVideoJoiningTimeMs);
      buildExtensionRenderers(renderersList, allowedVideoJoiningTimeMs);
    }
    renderers = renderersList.toArray(new Renderer[renderersList.size()]);

    // Obtain counts of video and audio renderers.
    int videoRendererCount = 0;
    int audioRendererCount = 0;
    for (Renderer renderer : renderers) {
      switch (renderer.getTrackType()) {
        case C.TRACK_TYPE_VIDEO:
          videoRendererCount++;
          break;
        case C.TRACK_TYPE_AUDIO:
          audioRendererCount++;
          break;
      }
    }
    this.videoRendererCount = videoRendererCount;
    this.audioRendererCount = audioRendererCount;

    // Set initial values.
    audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    volume = 1;

    // Build the player and associated objects.
    player = new ExoPlayerImpl(renderers, trackSelector, loadControl);
  }

  /**
   * Returns the number of renderers.
   *
   * @return The number of renderers.
   */
  public int getRendererCount() {
    return renderers.length;
  }

  /**
   * Returns the track type that the renderer at a given index handles.
   *
   * @see Renderer#getTrackType()
   * @param index The index of the renderer.
   * @return One of the {@code TRACK_TYPE_*} constants defined in {@link C}.
   */
  public int getRendererType(int index) {
    return renderers[index].getTrackType();
  }

  /**
   * Clears any {@link Surface}, {@link SurfaceHolder}, {@link SurfaceView} or {@link TextureView}
   * currently set on the player.
   */
  public void clearVideoSurface() {
    setVideoSurface(null);
  }

  /**
   * Sets the {@link Surface} onto which video will be rendered. The caller is responsible for
   * tracking the lifecycle of the surface, and must clear the surface by calling
   * {@code setVideoSurface(null)} if the surface is destroyed.
   * <p>
   * If the surface is held by a {@link SurfaceView}, {@link TextureView} or {@link SurfaceHolder}
   * then it's recommended to use {@link #setVideoSurfaceView(SurfaceView)},
   * {@link #setVideoTextureView(TextureView)} or {@link #setVideoSurfaceHolder(SurfaceHolder)}
   * rather than this method, since passing the holder allows the player to track the lifecycle of
   * the surface automatically.
   *
   * @param surface The {@link Surface}.
   */
  public void setVideoSurface(Surface surface) {
    removeSurfaceCallbacks();
    setVideoSurfaceInternal(surface, false);
  }

  /**
   * Sets the {@link SurfaceHolder} that holds the {@link Surface} onto which video will be
   * rendered. The player will track the lifecycle of the surface automatically.
   *
   * @param surfaceHolder The surface holder.
   */
  public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
    removeSurfaceCallbacks();
    this.surfaceHolder = surfaceHolder;
    if (surfaceHolder == null) {
      setVideoSurfaceInternal(null, false);
    } else {
      setVideoSurfaceInternal(surfaceHolder.getSurface(), false);
      surfaceHolder.addCallback(componentListener);
    }
  }

  /**
   * Sets the {@link SurfaceView} onto which video will be rendered. The player will track the
   * lifecycle of the surface automatically.
   *
   * @param surfaceView The surface view.
   */
  public void setVideoSurfaceView(SurfaceView surfaceView) {
    setVideoSurfaceHolder(surfaceView.getHolder());
  }

  /**
   * Sets the {@link TextureView} onto which video will be rendered. The player will track the
   * lifecycle of the surface automatically.
   *
   * @param textureView The texture view.
   */
  public void setVideoTextureView(TextureView textureView) {
    removeSurfaceCallbacks();
    this.textureView = textureView;
    if (textureView == null) {
      setVideoSurfaceInternal(null, true);
    } else {
      if (textureView.getSurfaceTextureListener() != null) {
        Log.w(TAG, "Replacing existing SurfaceTextureListener.");
      }
      SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
      setVideoSurfaceInternal(surfaceTexture == null ? null : new Surface(surfaceTexture), true);
      textureView.setSurfaceTextureListener(componentListener);
    }
  }

  /**
   * Sets the audio volume, with 0 being silence and 1 being unity gain.
   *
   * @param volume The volume.
   */
  public void setVolume(float volume) {
    this.volume = volume;
    ExoPlayerMessage[] messages = new ExoPlayerMessage[audioRendererCount];
    int count = 0;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_VOLUME, volume);
      }
    }
    player.sendMessages(messages);
  }

  /**
   * Returns the audio volume, with 0 being silence and 1 being unity gain.
   */
  public float getVolume() {
    return volume;
  }

  /**
   * Sets the {@link PlaybackParams} governing audio playback.
   *
   * @param params The {@link PlaybackParams}, or null to clear any previously set parameters.
   */
  @TargetApi(23)
  public void setPlaybackParams(PlaybackParams params) {
    if (params != null) {
      // The audio renderers will call this on the playback thread to ensure they can query
      // parameters without failure. We do the same up front, which is redundant except that it
      // ensures an immediate call to getPlaybackParams will retrieve the instance with defaults
      // allowed, rather than this change becoming visible sometime later once the audio renderers
      // receive the parameters.
      params.allowDefaults();
      playbackParamsHolder = new PlaybackParamsHolder(params);
    } else {
      playbackParamsHolder = null;
    }
    ExoPlayerMessage[] messages = new ExoPlayerMessage[audioRendererCount];
    int count = 0;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_PLAYBACK_PARAMS, params);
      }
    }
    player.sendMessages(messages);
  }

  /**
   * Returns the {@link PlaybackParams} governing audio playback, or null if not set.
   */
  @TargetApi(23)
  public PlaybackParams getPlaybackParams() {
    return playbackParamsHolder == null ? null : playbackParamsHolder.params;
  }

  /**
   * Returns the video format currently being played, or null if no video is being played.
   */
  public Format getVideoFormat() {
    return videoFormat;
  }

  /**
   * Returns the audio format currently being played, or null if no audio is being played.
   */
  public Format getAudioFormat() {
    return audioFormat;
  }

  /**
   * Returns the audio session identifier, or {@code AudioTrack.SESSION_ID_NOT_SET} if not set.
   */
  public int getAudioSessionId() {
    return audioSessionId;
  }

  /**
   * Returns {@link DecoderCounters} for video, or null if no video is being played.
   */
  public DecoderCounters getVideoDecoderCounters() {
    return videoDecoderCounters;
  }

  /**
   * Returns {@link DecoderCounters} for audio, or null if no audio is being played.
   */
  public DecoderCounters getAudioDecoderCounters() {
    return audioDecoderCounters;
  }

  /**
   * Sets a listener to receive video events.
   *
   * @param listener The listener.
   */
  public void setVideoListener(VideoListener listener) {
    videoListener = listener;
  }

  /**
   * Sets a listener to receive debug events from the video renderer.
   *
   * @param listener The listener.
   */
  public void setVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListener = listener;
  }

  /**
   * Sets a listener to receive debug events from the audio renderer.
   *
   * @param listener The listener.
   */
  public void setAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListener = listener;
  }

  /**
   * Sets an output to receive text events.
   *
   * @param output The output.
   */
  public void setTextOutput(TextRenderer.Output output) {
    textOutput = output;
  }

  /**
   * Sets a listener to receive ID3 metadata events.
   *
   * @param output The output.
   */
  public void setId3Output(MetadataRenderer.Output<List<Id3Frame>> output) {
    id3Output = output;
  }

  // ExoPlayer implementation

  @Override
  public void addListener(EventListener listener) {
    player.addListener(listener);
  }

  @Override
  public void removeListener(EventListener listener) {
    player.removeListener(listener);
  }

  @Override
  public int getPlaybackState() {
    return player.getPlaybackState();
  }

  @Override
  public void prepare(MediaSource mediaSource) {
    player.prepare(mediaSource);
  }

  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetTimeline) {
    player.prepare(mediaSource, resetPosition, resetTimeline);
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
  public boolean isLoading() {
    return player.isLoading();
  }

  @Override
  public void seekToDefaultPosition() {
    player.seekToDefaultPosition();
  }

  @Override
  public void seekToDefaultPosition(int windowIndex) {
    player.seekToDefaultPosition(windowIndex);
  }

  @Override
  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    player.seekTo(windowIndex, positionMs);
  }

  @Override
  public void stop() {
    player.stop();
  }

  @Override
  public void release() {
    player.release();
    removeSurfaceCallbacks();
    if (surface != null) {
      if (ownsSurface) {
        surface.release();
      }
      surface = null;
    }
  }

  @Override
  public void sendMessages(ExoPlayerMessage... messages) {
    player.sendMessages(messages);
  }

  @Override
  public void blockingSendMessages(ExoPlayerMessage... messages) {
    player.blockingSendMessages(messages);
  }

  @Override
  public int getCurrentPeriodIndex() {
    return player.getCurrentPeriodIndex();
  }

  @Override
  public int getCurrentWindowIndex() {
    return player.getCurrentWindowIndex();
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
  public int getBufferedPercentage() {
    return player.getBufferedPercentage();
  }

  @Override
  public Timeline getCurrentTimeline() {
    return player.getCurrentTimeline();
  }

  @Override
  public Object getCurrentManifest() {
    return player.getCurrentManifest();
  }

  // Internal methods.

  private void buildRenderers(Context context,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, ArrayList<Renderer> renderersList,
      long allowedVideoJoiningTimeMs) {
    MediaCodecVideoRenderer videoRenderer = new MediaCodecVideoRenderer(context,
        MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT,
        allowedVideoJoiningTimeMs, drmSessionManager, false, mainHandler, componentListener,
        MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    renderersList.add(videoRenderer);

    Renderer audioRenderer = new MediaCodecAudioRenderer(MediaCodecSelector.DEFAULT,
        drmSessionManager, true, mainHandler, componentListener,
        AudioCapabilities.getCapabilities(context), AudioManager.STREAM_MUSIC);
    renderersList.add(audioRenderer);

    Renderer textRenderer = new TextRenderer(componentListener, mainHandler.getLooper());
    renderersList.add(textRenderer);

    MetadataRenderer<List<Id3Frame>> id3Renderer = new MetadataRenderer<>(componentListener,
        mainHandler.getLooper(), new Id3Decoder());
    renderersList.add(id3Renderer);
  }

  private void buildExtensionRenderers(ArrayList<Renderer> renderersList,
      long allowedVideoJoiningTimeMs) {
    // Load extension renderers using reflection so that demo app doesn't depend on them.
    // Class.forName(<class name>) appears for each renderer so that automated tools like proguard
    // can detect the use of reflection (see http://proguard.sourceforge.net/FAQ.html#forname).
    try {
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer2.ext.vp9.LibvpxVideoRenderer");
      Constructor<?> constructor = clazz.getConstructor(boolean.class, long.class, Handler.class,
          VideoRendererEventListener.class, int.class);
      renderersList.add((Renderer) constructor.newInstance(true, allowedVideoJoiningTimeMs,
          mainHandler, componentListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
      Log.i(TAG, "Loaded LibvpxVideoRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    try {
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer2.ext.opus.LibopusAudioRenderer");
      Constructor<?> constructor = clazz.getConstructor(Handler.class,
          AudioRendererEventListener.class);
      renderersList.add((Renderer) constructor.newInstance(mainHandler, componentListener));
      Log.i(TAG, "Loaded LibopusAudioRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    try {
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer");
      Constructor<?> constructor = clazz.getConstructor(Handler.class,
          AudioRendererEventListener.class);
      renderersList.add((Renderer) constructor.newInstance(mainHandler, componentListener));
      Log.i(TAG, "Loaded LibflacAudioRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    try {
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer");
      Constructor<?> constructor = clazz.getConstructor(Handler.class,
          AudioRendererEventListener.class);
      renderersList.add((Renderer) constructor.newInstance(mainHandler, componentListener));
      Log.i(TAG, "Loaded FfmpegAudioRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

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

  private void setVideoSurfaceInternal(Surface surface, boolean ownsSurface) {
    // Note: We don't turn this method into a no-op if the surface is being replaced with itself
    // so as to ensure onRenderedFirstFrame callbacks are still called in this case.
    ExoPlayerMessage[] messages = new ExoPlayerMessage[videoRendererCount];
    int count = 0;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_SURFACE, surface);
      }
    }
    if (this.surface != null && this.surface != surface) {
      // If we created this surface, we are responsible for releasing it.
      if (this.ownsSurface) {
        this.surface.release();
      }
      // We're replacing a surface. Block to ensure that it's not accessed after the method returns.
      player.blockingSendMessages(messages);
    } else {
      player.sendMessages(messages);
    }
    this.surface = surface;
    this.ownsSurface = ownsSurface;
  }

  private final class ComponentListener implements VideoRendererEventListener,
      AudioRendererEventListener, TextRenderer.Output, MetadataRenderer.Output<List<Id3Frame>>,
      SurfaceHolder.Callback, TextureView.SurfaceTextureListener,
      TrackSelector.EventListener<Object> {

    // VideoRendererEventListener implementation

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
      videoDecoderCounters = counters;
      if (videoDebugListener != null) {
        videoDebugListener.onVideoEnabled(counters);
      }
    }

    @Override
    public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      if (videoDebugListener != null) {
        videoDebugListener.onVideoDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onVideoInputFormatChanged(Format format) {
      videoFormat = format;
      if (videoDebugListener != null) {
        videoDebugListener.onVideoInputFormatChanged(format);
      }
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
      if (videoDebugListener != null) {
        videoDebugListener.onDroppedFrames(count, elapsed);
      }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio) {
      if (videoListener != null) {
        videoListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
            pixelWidthHeightRatio);
      }
      if (videoDebugListener != null) {
        videoDebugListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
            pixelWidthHeightRatio);
      }
    }

    @Override
    public void onRenderedFirstFrame(Surface surface) {
      if (videoListener != null && SimpleExoPlayer.this.surface == surface) {
        videoListener.onRenderedFirstFrame();
      }
      if (videoDebugListener != null) {
        videoDebugListener.onRenderedFirstFrame(surface);
      }
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
      if (videoDebugListener != null) {
        videoDebugListener.onVideoDisabled(counters);
      }
      videoFormat = null;
      videoDecoderCounters = null;
    }

    // AudioRendererEventListener implementation

    @Override
    public void onAudioEnabled(DecoderCounters counters) {
      audioDecoderCounters = counters;
      if (audioDebugListener != null) {
        audioDebugListener.onAudioEnabled(counters);
      }
    }

    @Override
    public void onAudioSessionId(int sessionId) {
      audioSessionId = sessionId;
      if (audioDebugListener != null) {
        audioDebugListener.onAudioSessionId(sessionId);
      }
    }

    @Override
    public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      if (audioDebugListener != null) {
        audioDebugListener.onAudioDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onAudioInputFormatChanged(Format format) {
      audioFormat = format;
      if (audioDebugListener != null) {
        audioDebugListener.onAudioInputFormatChanged(format);
      }
    }

    @Override
    public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs,
        long elapsedSinceLastFeedMs) {
      if (audioDebugListener != null) {
        audioDebugListener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }
    }

    @Override
    public void onAudioDisabled(DecoderCounters counters) {
      if (audioDebugListener != null) {
        audioDebugListener.onAudioDisabled(counters);
      }
      audioFormat = null;
      audioDecoderCounters = null;
      audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    }

    // TextRenderer.Output implementation

    @Override
    public void onCues(List<Cue> cues) {
      if (textOutput != null) {
        textOutput.onCues(cues);
      }
    }

    // MetadataRenderer.Output<List<Id3Frame>> implementation

    @Override
    public void onMetadata(List<Id3Frame> id3Frames) {
      if (id3Output != null) {
        id3Output.onMetadata(id3Frames);
      }
    }

    // SurfaceHolder.Callback implementation

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      setVideoSurfaceInternal(holder.getSurface(), false);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      setVideoSurfaceInternal(null, false);
    }

    // TextureView.SurfaceTextureListener implementation

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
      setVideoSurfaceInternal(new Surface(surfaceTexture), true);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
      // Do nothing.
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
      setVideoSurfaceInternal(null, true);
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
      // Do nothing.
    }

    // TrackSelector.EventListener implementation

    @Override
    public void onTrackSelectionsChanged(TrackSelections<?> trackSelections) {
      boolean videoTracksEnabled = false;
      for (int i = 0; i < renderers.length; i++) {
        if (renderers[i].getTrackType() == C.TRACK_TYPE_VIDEO && trackSelections.get(i) != null) {
          videoTracksEnabled = true;
          break;
        }
      }
      if (videoListener != null && SimpleExoPlayer.this.videoTracksEnabled && !videoTracksEnabled) {
        videoListener.onVideoTracksDisabled();
      }
      SimpleExoPlayer.this.videoTracksEnabled = videoTracksEnabled;
    }

  }

  @TargetApi(23)
  private static final class PlaybackParamsHolder {

    public final PlaybackParams params;

    public PlaybackParamsHolder(PlaybackParams params) {
      this.params = params;
    }

  }

}
