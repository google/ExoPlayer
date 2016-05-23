/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.demo.player;

import com.google.android.exoplayer.AudioTrackRendererEventListener;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.CodecCounters;
import com.google.android.exoplayer.DefaultTrackSelectionPolicy;
import com.google.android.exoplayer.DefaultTrackSelector;
import com.google.android.exoplayer.DefaultTrackSelector.TrackInfo;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.SingleSampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.VideoTrackRendererEventListener;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.chunk.ChunkTrackStreamEventListener;
import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer.MetadataRenderer;
import com.google.android.exoplayer.metadata.id3.Id3Frame;
import com.google.android.exoplayer.metadata.id3.Id3Parser;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.TextRenderer;
import com.google.android.exoplayer.text.TextTrackRenderer;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.util.DebugTextViewHelper;
import com.google.android.exoplayer.util.PlayerControl;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A wrapper around {@link ExoPlayer} that provides a higher level interface.
 */
public class DemoPlayer implements ExoPlayer.Listener, DefaultTrackSelector.EventListener,
    ChunkTrackStreamEventListener, ExtractorSampleSource.EventListener,
    SingleSampleSource.EventListener, DefaultBandwidthMeter.EventListener,
    VideoTrackRendererEventListener, AudioTrackRendererEventListener,
    StreamingDrmSessionManager.EventListener, TextRenderer, MetadataRenderer<List<Id3Frame>>,
    DebugTextViewHelper.Provider {

  /**
   * A listener for core events.
   */
  public interface Listener {
    void onStateChanged(boolean playWhenReady, int playbackState);
    void onError(ExoPlaybackException e);
    void onTracksChanged(TrackInfo trackInfo);
    void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio);
  }

  /**
   * A listener for internal errors.
   * <p>
   * These errors are not visible to the user, and hence this listener is provided for
   * informational purposes only. Note however that an internal error may cause a fatal
   * error if the player fails to recover. If this happens,
   * {@link Listener#onError(ExoPlaybackException)} will be invoked.
   */
  public interface InternalErrorListener {
    void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);
    void onLoadError(int sourceId, IOException e);
    void onDrmSessionManagerError(Exception e);
  }

  /**
   * A listener for debugging information.
   */
  public interface InfoListener {
    void onAudioDecoderInitialized(String decoderName, long elapsedRealtimeMs,
        long initializationDurationMs);
    void onVideoDecoderInitialized(String decoderName, long elapsedRealtimeMs,
        long initializationDurationMs);
    void onAudioFormatEnabled(Format format, int trigger, long mediaTimeMs);
    void onVideoFormatEnabled(Format format, int trigger, long mediaTimeMs);
    void onDroppedFrames(int count, long elapsed);
    void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate);
    void onLoadStarted(int sourceId, long length, int type, int trigger, Format format,
        long mediaStartTimeMs, long mediaEndTimeMs);
    void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format,
        long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs);
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

  // Constants pulled into this class for convenience.
  public static final int STATE_IDLE = ExoPlayer.STATE_IDLE;
  public static final int STATE_BUFFERING = ExoPlayer.STATE_BUFFERING;
  public static final int STATE_READY = ExoPlayer.STATE_READY;
  public static final int STATE_ENDED = ExoPlayer.STATE_ENDED;

  private static final String TAG = "DemoPlayer";

  private final ExoPlayer player;
  private final DefaultTrackSelector trackSelector;
  private final BandwidthMeter bandwidthMeter;
  private final TrackRenderer[] renderers;
  private final PlayerControl playerControl;
  private final Handler mainHandler;
  private final CopyOnWriteArrayList<Listener> listeners;

  private Surface surface;
  private Format videoFormat;
  private TrackInfo trackInfo;

  private CaptionListener captionListener;
  private Id3MetadataListener id3MetadataListener;
  private InternalErrorListener internalErrorListener;
  private InfoListener infoListener;
  private CodecCounters videoCodecCounters;

  public DemoPlayer(Context context, MediaDrmCallback drmCallback, boolean useExtensionDecoders) {
    mainHandler = new Handler();
    bandwidthMeter = new DefaultBandwidthMeter();
    listeners = new CopyOnWriteArrayList<>();

    // Build the renderers.
    ArrayList<TrackRenderer> renderersList = new ArrayList<>();
    if (useExtensionDecoders) {
      buildExtensionRenderers(renderersList);
    }
    buildRenderers(context, drmCallback, renderersList);
    renderers = renderersList.toArray(new TrackRenderer[renderersList.size()]);

    // Build the player and associated objects.
    trackSelector = new DefaultTrackSelector(mainHandler, this, new DefaultTrackSelectionPolicy());
    player = ExoPlayer.Factory.newInstance(renderers, trackSelector, 1000, 5000);
    player.addListener(this);
    playerControl = new PlayerControl(player);
  }

  public DefaultTrackSelector getTrackSelector() {
    return trackSelector;
  }

  public PlayerControl getPlayerControl() {
    return playerControl;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public void setInternalErrorListener(InternalErrorListener listener) {
    internalErrorListener = listener;
  }

  public void setInfoListener(InfoListener listener) {
    infoListener = listener;
  }

  public void setCaptionListener(CaptionListener listener) {
    captionListener = listener;
  }

  public void setMetadataListener(Id3MetadataListener listener) {
    id3MetadataListener = listener;
  }

  public void setSurface(Surface surface) {
    this.surface = surface;
    pushSurface(false);
  }

  public Surface getSurface() {
    return surface;
  }

  public void blockingClearSurface() {
    surface = null;
    pushSurface(true);
  }

  public TrackInfo getTrackInfo() {
    return trackInfo;
  }

  public void setSource(SampleSource source) {
    player.setSource(source);
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    player.setPlayWhenReady(playWhenReady);
  }

  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  public void release() {
    surface = null;
    player.release();
  }

  public int getPlaybackState() {
    return player.getPlaybackState();
  }

  @Override
  public Format getFormat() {
    return videoFormat;
  }

  @Override
  public BandwidthMeter getBandwidthMeter() {
    return bandwidthMeter;
  }

  @Override
  public CodecCounters getCodecCounters() {
    return videoCodecCounters;
  }

  @Override
  public long getCurrentPosition() {
    return player.getCurrentPosition();
  }

  public long getDuration() {
    return player.getDuration();
  }

  public int getBufferedPercentage() {
    return player.getBufferedPercentage();
  }

  public boolean getPlayWhenReady() {
    return player.getPlayWhenReady();
  }

  public Handler getMainHandler() {
    return mainHandler;
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int state) {
    for (Listener listener : listeners) {
      listener.onStateChanged(playWhenReady, state);
    }
  }

  @Override
  public void onPlayerError(ExoPlaybackException exception) {
    for (Listener listener : listeners) {
      listener.onError(exception);
    }
  }

  @Override
  public void onTracksChanged(TrackInfo trackInfo) {
    this.trackInfo = trackInfo;
    for (Listener listener : listeners) {
      listener.onTracksChanged(trackInfo);
    }
  }

  @Override
  public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
      float pixelWidthHeightRatio) {
    for (Listener listener : listeners) {
      listener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
    }
  }

  @Override
  public void onDroppedFrames(int count, long elapsed) {
    if (infoListener != null) {
      infoListener.onDroppedFrames(count, elapsed);
    }
  }

  @Override
  public void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate) {
    if (infoListener != null) {
      infoListener.onBandwidthSample(elapsedMs, bytes, bitrateEstimate);
    }
  }

  @Override
  public void onDownstreamFormatChanged(int sourceId, Format format, int trigger,
      long mediaTimeMs) {
    if (infoListener == null) {
      return;
    }
    if (sourceId == C.TRACK_TYPE_VIDEO) {
      videoFormat = format;
      infoListener.onVideoFormatEnabled(format, trigger, mediaTimeMs);
    } else if (sourceId == C.TRACK_TYPE_AUDIO) {
      infoListener.onAudioFormatEnabled(format, trigger, mediaTimeMs);
    }
  }

  @Override
  public void onDrmKeysLoaded() {
    // Do nothing.
  }

  @Override
  public void onDrmSessionManagerError(Exception e) {
    if (internalErrorListener != null) {
      internalErrorListener.onDrmSessionManagerError(e);
    }
  }

  @Override
  public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    if (internalErrorListener != null) {
      internalErrorListener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }
  }

  @Override
  public void onAudioCodecCounters(CodecCounters counters) {
    // do nothing
  }

  @Override
  public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs,
      long initializationDurationMs) {
    if (infoListener != null) {
      infoListener.onAudioDecoderInitialized(decoderName, initializedTimestampMs,
          initializationDurationMs);
    }
  }

  @Override
  public void onLoadError(int sourceId, IOException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onLoadError(sourceId, e);
    }
  }

  @Override
  public void onCues(List<Cue> cues) {
    if (captionListener != null) {
      captionListener.onCues(cues);
    }
  }

  @Override
  public void onMetadata(List<Id3Frame> id3Frames) {
    if (id3MetadataListener != null) {
      id3MetadataListener.onId3Metadata(id3Frames);
    }
  }

  @Override
  public void onPlayWhenReadyCommitted() {
    // Do nothing.
  }

  @Override
  public void onDrawnToSurface(Surface surface) {
    // Do nothing.
  }

  @Override
  public void onVideoCodecCounters(CodecCounters counters) {
    this.videoCodecCounters = counters;
  }

  @Override
  public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs,
      long initializationDurationMs) {
    if (infoListener != null) {
      infoListener.onVideoDecoderInitialized(decoderName, initializedTimestampMs,
          initializationDurationMs);
    }
  }

  @Override
  public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format,
      long mediaStartTimeMs, long mediaEndTimeMs) {
    if (infoListener != null) {
      infoListener.onLoadStarted(sourceId, length, type, trigger, format, mediaStartTimeMs,
          mediaEndTimeMs);
    }
  }

  @Override
  public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format,
      long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {
    if (infoListener != null) {
      infoListener.onLoadCompleted(sourceId, bytesLoaded, type, trigger, format, mediaStartTimeMs,
          mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs);
    }
  }

  @Override
  public void onLoadCanceled(int sourceId, long bytesLoaded) {
    // Do nothing.
  }

  @Override
  public void onUpstreamDiscarded(int sourceId, long mediaStartTimeMs, long mediaEndTimeMs) {
    // Do nothing.
  }

  public int getRendererType(int index) {
    return renderers[index].getTrackType();
  }

  private void pushSurface(boolean blockForSurfacePush) {
    for (TrackRenderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        if (blockForSurfacePush) {
          player.blockingSendMessage(renderer, C.MSG_SET_SURFACE, surface);
        } else {
          player.sendMessage(renderer, C.MSG_SET_SURFACE, surface);
        }
      }
    }
  }

  private void buildRenderers(Context context, MediaDrmCallback drmCallback,
      ArrayList<TrackRenderer> renderersList) {
    DrmSessionManager drmSessionManager = drmCallback == null ? null
        : new StreamingDrmSessionManager(drmCallback, null, mainHandler, this);

    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context,
        MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
        drmSessionManager, false, mainHandler, this, 50);
    renderersList.add(videoRenderer);

    TrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(MediaCodecSelector.DEFAULT,
        drmSessionManager, true, mainHandler, this, AudioCapabilities.getCapabilities(context),
        AudioManager.STREAM_MUSIC);
    renderersList.add(audioRenderer);

    TrackRenderer textRenderer = new TextTrackRenderer(this, mainHandler.getLooper());
    renderersList.add(textRenderer);

    MetadataTrackRenderer<List<Id3Frame>> id3Renderer = new MetadataTrackRenderer<>(new Id3Parser(),
        this, mainHandler.getLooper());
    renderersList.add(id3Renderer);
  }

  private void buildExtensionRenderers(ArrayList<TrackRenderer> renderersList) {
    // Load extension renderers using reflection so that demo app doesn't depend on them.
    // Class.forName(<class name>) appears for each renderer so that automated tools like proguard
    // can detect the use of reflection (see http://proguard.sourceforge.net/FAQ.html#forname).
    try {
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer.ext.vp9.LibvpxVideoTrackRenderer");
      Constructor<?> constructor = clazz.getConstructor(boolean.class, Handler.class,
          VideoTrackRendererEventListener.class, int.class);
      renderersList.add((TrackRenderer) constructor.newInstance(true, mainHandler, this, 50));
      Log.i(TAG, "Loaded LibvpxVideoTrackRenderer.");
    } catch (Exception e) {
      // Expected if the app was built without the extension.
    }

    try {
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer.ext.opus.LibopusAudioTrackRenderer");
      renderersList.add((TrackRenderer) clazz.newInstance());
      Log.i(TAG, "Loaded LibopusAudioTrackRenderer.");
    } catch (Exception e) {
      // Expected if the app was built without the extension.
    }

    try {
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer.ext.flac.LibflacAudioTrackRenderer");
      renderersList.add((TrackRenderer) clazz.newInstance());
      Log.i(TAG, "Loaded LibflacAudioTrackRenderer.");
    } catch (Exception e) {
      // Expected if the app was built without the extension.
    }

    try {
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer.ext.ffmpeg.FfmpegAudioTrackRenderer");
      renderersList.add((TrackRenderer) clazz.newInstance());
      Log.i(TAG, "Loaded FfmpegAudioTrackRenderer.");
    } catch (Exception e) {
      // Expected if the app was built without the extension.
    }
  }

}
