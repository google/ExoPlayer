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

import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.metadata.id3.Id3Parser;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

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
    void onAudioEnabled(CodecCounters counters);
    void onAudioDecoderInitialized(String decoderName, long elapsedRealtimeMs,
        long initializationDurationMs);
    void onAudioFormatChanged(Format format);
    void onAudioDisabled(CodecCounters counters);
    void onVideoEnabled(CodecCounters counters);
    void onVideoDecoderInitialized(String decoderName, long elapsedRealtimeMs,
        long initializationDurationMs);
    void onVideoFormatChanged(Format format);
    void onVideoDisabled(CodecCounters counters);
    void onDroppedFrames(int count, long elapsed);
    void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);
  }

  /**
   * A listener for receiving notifications of timed text.
   */
  public interface CaptionListener {
    void onCues(List<Cue> cues);
  }

  /**
   * A listener for receiving ID3 metadata parsed from the media stream.
   */
  public interface Id3MetadataListener {
    void onId3Metadata(List<Id3Frame> id3Frames);
  }

  private static final String TAG = "SimpleExoPlayer";
  private static final int MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50;

  private final ExoPlayer player;
  private final BandwidthMeter bandwidthMeter;
  private final Renderer[] renderers;
  private final ComponentListener componentListener;
  private final Handler mainHandler;
  private final int videoRendererCount;
  private final int audioRendererCount;

  private Format videoFormat;
  private Format audioFormat;

  private CaptionListener captionListener;
  private Id3MetadataListener id3MetadataListener;
  private VideoListener videoListener;
  private DebugListener debugListener;
  private CodecCounters videoCodecCounters;
  private CodecCounters audioCodecCounters;

  /* package */ SimpleExoPlayer(Context context, TrackSelector trackSelector,
      LoadControl loadControl, DrmSessionManager drmSessionManager,
      boolean preferExtensionDecoders, long allowedVideoJoiningTimeMs) {
    mainHandler = new Handler();
    bandwidthMeter = new DefaultBandwidthMeter();
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
   * Sets the {@link Surface} onto which video will be rendered.
   *
   * @param surface The {@link Surface}.
   */
  public void setSurface(Surface surface) {
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
   * @return The {@link BandwidthMeter} being used by the player.
   */
  public BandwidthMeter getBandwidthMeter() {
    return bandwidthMeter;
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
   * @return The {@link CodecCounters} for video, or null if there is no video component to the
   *     current media.
   */
  public CodecCounters getVideoCodecCounters() {
    return videoCodecCounters;
  }

  /**
   * @return The {@link CodecCounters} for audio, or null if there is no audio component to the
   *     current media.
   */
  public CodecCounters getAudioCodecCounters() {
    return audioCodecCounters;
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
   * Sets a listener to receive caption events.
   *
   * @param listener The listener.
   */
  public void setCaptionListener(CaptionListener listener) {
    captionListener = listener;
  }

  /**
   * Sets a listener to receive metadata events.
   *
   * @param listener The listener.
   */
  public void setMetadataListener(Id3MetadataListener listener) {
    id3MetadataListener = listener;
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
        mainHandler.getLooper(), new Id3Parser());
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

  private final class ComponentListener implements VideoRendererEventListener,
      AudioRendererEventListener, TextRenderer.Output, MetadataRenderer.Output<List<Id3Frame>> {

    // VideoRendererEventListener implementation

    @Override
    public void onVideoEnabled(CodecCounters counters) {
      videoCodecCounters = counters;
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
    public void onVideoDisabled(CodecCounters counters) {
      if (debugListener != null) {
        debugListener.onVideoDisabled(counters);
      }
      videoFormat = null;
      videoCodecCounters = null;
    }

    // AudioRendererEventListener implementation

    @Override
    public void onAudioEnabled(CodecCounters counters) {
      audioCodecCounters = counters;
      if (debugListener != null) {
        debugListener.onAudioEnabled(counters);
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
    public void onAudioDisabled(CodecCounters counters) {
      if (debugListener != null) {
        debugListener.onAudioDisabled(counters);
      }
      audioFormat = null;
      audioCodecCounters = null;
    }

    // TextRendererOutput implementation

    @Override
    public void onCues(List<Cue> cues) {
      if (captionListener != null) {
        captionListener.onCues(cues);
      }
    }

    // MetadataRenderer implementation

    @Override
    public void onMetadata(List<Id3Frame> id3Frames) {
      if (id3MetadataListener != null) {
        id3MetadataListener.onId3Metadata(id3Frames);
      }
    }

  }

}
