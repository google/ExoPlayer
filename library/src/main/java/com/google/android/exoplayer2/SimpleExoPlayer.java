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
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioTrack;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.Timeline;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link ExoPlayer} that uses default {@link Renderer} components.
 * <p>
 * Instances of this class can be obtained from {@link ExoPlayerFactory}.
 */
@TargetApi(16)
public final class SimpleExoPlayer implements ExoPlayer {

  /**
   * A listener for video rendering information.
   */
  public interface VideoListener {
    void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio);
    void onDrawnToSurface(Surface surface);
  }

  /**
   * A listener for debugging information.
   */
  public interface DebugListener {
    void onAudioEnabled(DecoderCounters counters);
    void onAudioSessionId(int audioSessionId);
    void onAudioDecoderInitialized(String decoderName, long elapsedRealtimeMs,
        long initializationDurationMs);
    void onAudioFormatChanged(Format format);
    void onAudioDisabled(DecoderCounters counters);
    void onVideoEnabled(DecoderCounters counters);
    void onVideoDecoderInitialized(String decoderName, long elapsedRealtimeMs,
        long initializationDurationMs);
    void onVideoFormatChanged(Format format);
    void onVideoDisabled(DecoderCounters counters);
    void onDroppedFrames(int count, long elapsed);
    void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);
  }

  private static final String TAG = "SimpleExoPlayer";
  private static final int MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50;

  private final ExoPlayer player;
  private final Renderer[] renderers;
  private final ComponentListener componentListener;
  private final Handler mainHandler;
  private final int videoRendererCount;
  private final int audioRendererCount;

  private Format videoFormat;
  private Format audioFormat;

  private SurfaceHolder surfaceHolder;
  private TextRenderer.Output textOutput;
  private MetadataRenderer.Output<List<Id3Frame>> id3Output;
  private VideoListener videoListener;
  private DebugListener debugListener;
  private DecoderCounters videoDecoderCounters;
  private DecoderCounters audioDecoderCounters;
  private int audioSessionId;

  /* package */ SimpleExoPlayer(Context context, TrackSelector trackSelector,
      LoadControl loadControl, DrmSessionManager drmSessionManager,
      boolean preferExtensionDecoders, long allowedVideoJoiningTimeMs) {
    mainHandler = new Handler();
    componentListener = new ComponentListener();

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
    this.audioSessionId = AudioTrack.SESSION_ID_NOT_SET;

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
   * Sets the {@link Surface} onto which video will be rendered. The caller is responsible for
   * tracking the lifecycle of the surface, and must clear the surface by calling
   * {@code setVideoSurface(null)} if the surface is destroyed.
   * <p>
   * If the surface is held by a {@link SurfaceHolder} then it's recommended to use
   * {@link #setVideoSurfaceHolder(SurfaceHolder)} rather than this method, since passing the
   * holder allows the player to track the lifecycle of the surface automatically.
   *
   * @param surface The {@link Surface}.
   */
  public void setVideoSurface(Surface surface) {
    if (surfaceHolder != null) {
      surfaceHolder.removeCallback(componentListener);
      surfaceHolder = null;
    }
    setVideoSurfaceInternal(surface);
  }

  /**
   * Sets the {@link SurfaceHolder} that holds the {@link Surface} onto which video will be
   * rendered. The player will track the lifecycle of the surface automatically.
   *
   * @param surfaceHolder The surface holder.
   */
  public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
    if (this.surfaceHolder != null) {
      this.surfaceHolder.removeCallback(componentListener);
    }
    this.surfaceHolder = surfaceHolder;
    if (surfaceHolder == null) {
      setVideoSurfaceInternal(null);
    } else {
      setVideoSurfaceInternal(surfaceHolder.getSurface());
      surfaceHolder.addCallback(componentListener);
    }
  }

  /**
   * Sets the audio volume, with 0 being silence and 1 being unity gain.
   *
   * @param volume The volume.
   */
  public void setVolume(float volume) {
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
   * Sets {@link PlaybackParams} governing audio playback.
   *
   * @param params The {@link PlaybackParams}.
   */
  public void setPlaybackParams(PlaybackParams params) {
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
   * @return The video format currently being played, or null if there is no video component to the
   *     current media.
   */
  public Format getVideoFormat() {
    return videoFormat;
  }

  /**
   * @return The audio format currently being played, or null if there is no audio component to the
   *     current media.
   */
  public Format getAudioFormat() {
    return audioFormat;
  }

  /**
   * @return The audio session identifier. If not set {@code AudioTrack.SESSION_ID_NOT_SET} is
   *     returned.
   */
  public int getAudioSessionId() {
    return audioSessionId;
  }

  /**
   * @return The {@link DecoderCounters} for video, or null if there is no video component to the
   *     current media.
   */
  public DecoderCounters getVideoDecoderCounters() {
    return videoDecoderCounters;
  }

  /**
   * @return The {@link DecoderCounters} for audio, or null if there is no audio component to the
   *     current media.
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
   * Sets a listener to receive debug events.
   *
   * @param listener The listener.
   */
  public void setDebugListener(DebugListener listener) {
    debugListener = listener;
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
  public void setMediaSource(MediaSource mediaSource) {
    player.setMediaSource(mediaSource);
  }

  @Override
  public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
    player.setMediaSource(mediaSource, resetPosition);
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
  public boolean isPlayWhenReadyCommitted() {
    return player.isPlayWhenReadyCommitted();
  }

  @Override
  public boolean isLoading() {
    return player.isLoading();
  }

  @Override
  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  @Override
  public void seekTo(int periodIndex, long positionMs) {
    player.seekTo(periodIndex, positionMs);
  }

  @Override
  public void seekToDefaultPosition(int periodIndex) {
    player.seekToDefaultPosition(periodIndex);
  }

  @Override
  public void stop() {
    player.stop();
  }

  @Override
  public void release() {
    player.release();
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
  public long getDuration() {
    return player.getDuration();
  }

  @Override
  public long getCurrentPosition() {
    return player.getCurrentPosition();
  }

  @Override
  public int getCurrentPeriodIndex() {
    return player.getCurrentPeriodIndex();
  }

  @Override
  public Timeline getCurrentTimeline() {
    return player.getCurrentTimeline();
  }

  @Override
  public long getBufferedPosition() {
    return player.getBufferedPosition();
  }

  @Override
  public int getBufferedPercentage() {
    return player.getBufferedPercentage();
  }

  // Internal methods.

  private void buildRenderers(Context context, DrmSessionManager drmSessionManager,
      ArrayList<Renderer> renderersList, long allowedVideoJoiningTimeMs) {
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

  private void setVideoSurfaceInternal(Surface surface) {
    ExoPlayerMessage[] messages = new ExoPlayerMessage[videoRendererCount];
    int count = 0;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_SURFACE, surface);
      }
    }
    if (surface == null) {
      // Block to ensure that the surface is not accessed after the method returns.
      player.blockingSendMessages(messages);
    } else {
      player.sendMessages(messages);
    }
  }

  private final class ComponentListener implements VideoRendererEventListener,
      AudioRendererEventListener, TextRenderer.Output, MetadataRenderer.Output<List<Id3Frame>>,
      SurfaceHolder.Callback {

    // VideoRendererEventListener implementation

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
      videoDecoderCounters = counters;
      if (debugListener != null) {
        debugListener.onVideoEnabled(counters);
      }
    }

    @Override
    public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      if (debugListener != null) {
        debugListener.onVideoDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onVideoInputFormatChanged(Format format) {
      videoFormat = format;
      if (debugListener != null) {
        debugListener.onVideoFormatChanged(format);
      }
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
      if (debugListener != null) {
        debugListener.onDroppedFrames(count, elapsed);
      }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio) {
      if (videoListener != null) {
        videoListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
            pixelWidthHeightRatio);
      }
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
      if (videoListener != null) {
        videoListener.onDrawnToSurface(surface);
      }
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
      if (debugListener != null) {
        debugListener.onVideoDisabled(counters);
      }
      videoFormat = null;
      videoDecoderCounters = null;
    }

    // AudioRendererEventListener implementation

    @Override
    public void onAudioEnabled(DecoderCounters counters) {
      audioDecoderCounters = counters;
      if (debugListener != null) {
        debugListener.onAudioEnabled(counters);
      }
    }

    @Override
    public void onAudioSessionId(int sessionId) {
      audioSessionId = sessionId;
      if (debugListener != null) {
        debugListener.onAudioSessionId(sessionId);
      }
    }

    @Override
    public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      if (debugListener != null) {
        debugListener.onAudioDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onAudioInputFormatChanged(Format format) {
      audioFormat = format;
      if (debugListener != null) {
        debugListener.onAudioFormatChanged(format);
      }
    }

    @Override
    public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs,
        long elapsedSinceLastFeedMs) {
      if (debugListener != null) {
        debugListener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }
    }

    @Override
    public void onAudioDisabled(DecoderCounters counters) {
      if (debugListener != null) {
        debugListener.onAudioDisabled(counters);
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
      if (player != null) {
        setVideoSurfaceInternal(holder.getSurface());
      }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      if (player != null) {
        setVideoSurfaceInternal(null);
      }
    }

  }

}
