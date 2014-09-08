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
package com.google.android.exoplayer;

import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.TraceUtil;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Decodes and renders video using {@link MediaCodec}.
 */
@TargetApi(16)
public class MediaCodecVideoTrackRenderer extends MediaCodecTrackRenderer {

  /**
   * Interface definition for a callback to be notified of {@link MediaCodecVideoTrackRenderer}
   * events.
   */
  public interface EventListener extends MediaCodecTrackRenderer.EventListener {

    /**
     * Invoked to report the number of frames dropped by the renderer. Dropped frames are reported
     * whenever the renderer is stopped having dropped frames, and optionally, whenever the count
     * reaches a specified threshold whilst the renderer is started.
     *
     * @param count The number of dropped frames.
     * @param elapsed The duration in milliseconds over which the frames were dropped. This
     *     duration is timed from when the renderer was started or from when dropped frames were
     *     last reported (whichever was more recent), and not from when the first of the reported
     *     drops occurred.
     */
    void onDroppedFrames(int count, long elapsed);

    /**
     * Invoked each time there's a change in the size of the video being rendered.
     *
     * @param width The video width in pixels.
     * @param height The video height in pixels.
     */
    void onVideoSizeChanged(int width, int height);

    /**
     * Invoked when a frame is rendered to a surface for the first time following that surface
     * having been set as the target for the renderer.
     *
     * @param surface The surface to which a first frame has been rendered.
     */
    void onDrawnToSurface(Surface surface);

  }

  // TODO: Use MediaFormat constants if these get exposed through the API. See [redacted].
  private static final String KEY_CROP_LEFT = "crop-left";
  private static final String KEY_CROP_RIGHT = "crop-right";
  private static final String KEY_CROP_BOTTOM = "crop-bottom";
  private static final String KEY_CROP_TOP = "crop-top";

  /**
   * The type of a message that can be passed to an instance of this class via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be the target {@link Surface}, or null.
   */
  public static final int MSG_SET_SURFACE = 1;

  private final EventListener eventListener;
  private final long allowedJoiningTimeUs;
  private final int videoScalingMode;
  private final int maxDroppedFrameCountToNotify;

  private Surface surface;
  private boolean drawnToSurface;
  private boolean renderedFirstFrame;
  private long joiningDeadlineUs;
  private long droppedFrameAccumulationStartTimeMs;
  private int droppedFrameCount;

  private int currentWidth;
  private int currentHeight;
  private int lastReportedWidth;
  private int lastReportedHeight;

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   */
  public MediaCodecVideoTrackRenderer(SampleSource source, int videoScalingMode) {
    this(source, null, true, videoScalingMode);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisision. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   */
  public MediaCodecVideoTrackRenderer(SampleSource source, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys, int videoScalingMode) {
    this(source, drmSessionManager, playClearSamplesWithoutKeys, videoScalingMode, 0);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   */
  public MediaCodecVideoTrackRenderer(SampleSource source, int videoScalingMode,
      long allowedJoiningTimeMs) {
    this(source, null, true, videoScalingMode, allowedJoiningTimeMs);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisision. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   */
  public MediaCodecVideoTrackRenderer(SampleSource source, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys, int videoScalingMode, long allowedJoiningTimeMs) {
    this(source, drmSessionManager, playClearSamplesWithoutKeys, videoScalingMode,
        allowedJoiningTimeMs, null, null, -1);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFrameCountToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link EventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoTrackRenderer(SampleSource source, int videoScalingMode,
      long allowedJoiningTimeMs, Handler eventHandler, EventListener eventListener,
      int maxDroppedFrameCountToNotify) {
    this(source, null, true, videoScalingMode, allowedJoiningTimeMs, eventHandler, eventListener,
        maxDroppedFrameCountToNotify);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisision. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param videoScalingMode The scaling mode to pass to
   *     {@link MediaCodec#setVideoScalingMode(int)}.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFrameCountToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link EventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoTrackRenderer(SampleSource source, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys, int videoScalingMode, long allowedJoiningTimeMs,
      Handler eventHandler, EventListener eventListener, int maxDroppedFrameCountToNotify) {
    super(source, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener);
    this.videoScalingMode = videoScalingMode;
    this.allowedJoiningTimeUs = allowedJoiningTimeMs * 1000;
    this.eventListener = eventListener;
    this.maxDroppedFrameCountToNotify = maxDroppedFrameCountToNotify;
    joiningDeadlineUs = -1;
    currentWidth = -1;
    currentHeight = -1;
    lastReportedWidth = -1;
    lastReportedHeight = -1;
  }

  @Override
  protected boolean handlesMimeType(String mimeType) {
    return MimeTypes.isVideo(mimeType) && super.handlesMimeType(mimeType);
  }

  @Override
  protected void onEnabled(long startTimeUs, boolean joining) {
    super.onEnabled(startTimeUs, joining);
    renderedFirstFrame = false;
    if (joining && allowedJoiningTimeUs > 0) {
      joiningDeadlineUs = SystemClock.elapsedRealtime() * 1000L + allowedJoiningTimeUs;
    }
  }

  @Override
  protected void seekTo(long timeUs) throws ExoPlaybackException {
    super.seekTo(timeUs);
    renderedFirstFrame = false;
    joiningDeadlineUs = -1;
  }

  @Override
  protected boolean isReady() {
    if (super.isReady() && (renderedFirstFrame || !codecInitialized()
        || getSourceState() == SOURCE_STATE_READY_READ_MAY_FAIL)) {
      // Ready. If we were joining then we've now joined, so clear the joining deadline.
      joiningDeadlineUs = -1;
      return true;
    } else if (joiningDeadlineUs == -1) {
      // Not joining.
      return false;
    } else if (SystemClock.elapsedRealtime() * 1000 < joiningDeadlineUs) {
      // Joining and still within the joining deadline.
      return true;
    } else {
      // The joining deadline has been exceeded. Give up and clear the deadline.
      joiningDeadlineUs = -1;
      return false;
    }
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    droppedFrameCount = 0;
    droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
  }

  @Override
  protected void onStopped() {
    super.onStopped();
    joiningDeadlineUs = -1;
    notifyAndResetDroppedFrameCount();
  }

  @Override
  public void onDisabled() {
    super.onDisabled();
    currentWidth = -1;
    currentHeight = -1;
    lastReportedWidth = -1;
    lastReportedHeight = -1;
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    if (messageType == MSG_SET_SURFACE) {
      setSurface((Surface) message);
    } else {
      super.handleMessage(messageType, message);
    }
  }

  /**
   * @param surface The surface to set.
   * @throws ExoPlaybackException
   */
  private void setSurface(Surface surface) throws ExoPlaybackException {
    if (this.surface == surface) {
      return;
    }
    this.surface = surface;
    this.drawnToSurface = false;
    int state = getState();
    if (state == TrackRenderer.STATE_ENABLED || state == TrackRenderer.STATE_STARTED) {
      releaseCodec();
      maybeInitCodec();
    }
  }

  @Override
  protected boolean shouldInitCodec() {
    return super.shouldInitCodec() && surface != null;
  }

  // Override configureCodec to provide the surface.
  @Override
  protected void configureCodec(MediaCodec codec, android.media.MediaFormat format,
      MediaCrypto crypto) {
    codec.configure(format, surface, crypto, 0);
    codec.setVideoScalingMode(videoScalingMode);
  }

  @Override
  protected void onOutputFormatChanged(android.media.MediaFormat format) {
    boolean hasCrop = format.containsKey(KEY_CROP_RIGHT) && format.containsKey(KEY_CROP_LEFT)
        && format.containsKey(KEY_CROP_BOTTOM) && format.containsKey(KEY_CROP_TOP);
    currentWidth = hasCrop
        ? format.getInteger(KEY_CROP_RIGHT) - format.getInteger(KEY_CROP_LEFT) + 1
        : format.getInteger(android.media.MediaFormat.KEY_WIDTH);
    currentHeight = hasCrop
        ? format.getInteger(KEY_CROP_BOTTOM) - format.getInteger(KEY_CROP_TOP) + 1
        : format.getInteger(android.media.MediaFormat.KEY_HEIGHT);
  }

  @Override
  protected boolean canReconfigureCodec(MediaCodec codec, boolean codecIsAdaptive,
      MediaFormat oldFormat, MediaFormat newFormat) {
    // TODO: Relax this check to also allow non-H264 adaptive decoders.
    return newFormat.mimeType.equals(MimeTypes.VIDEO_H264)
        && oldFormat.mimeType.equals(MimeTypes.VIDEO_H264)
        && codecIsAdaptive
            || (oldFormat.width == newFormat.width && oldFormat.height == newFormat.height);
  }

  @Override
  protected boolean processOutputBuffer(long timeUs, MediaCodec codec, ByteBuffer buffer,
      MediaCodec.BufferInfo bufferInfo, int bufferIndex, boolean shouldSkip) {
    if (shouldSkip) {
      skipOutputBuffer(codec, bufferIndex);
      return true;
    }

    long earlyUs = bufferInfo.presentationTimeUs - timeUs;
    if (earlyUs < -30000) {
      // We're more than 30ms late rendering the frame.
      dropOutputBuffer(codec, bufferIndex);
      return true;
    }

    if (!renderedFirstFrame) {
      renderOutputBuffer(codec, bufferIndex);
      renderedFirstFrame = true;
      return true;
    }

    if (getState() == TrackRenderer.STATE_STARTED && earlyUs < 30000) {
      if (earlyUs > 11000) {
        // We're a little too early to render the frame. Sleep until the frame can be rendered.
        // Note: The 11ms threshold was chosen fairly arbitrarily.
        try {
          // Subtracting 10000 rather than 11000 ensures that the sleep time will be at least 1ms.
          Thread.sleep((earlyUs - 10000) / 1000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      renderOutputBuffer(codec, bufferIndex);
      return true;
    }

    // We're either not playing, or it's not time to render the frame yet.
    return false;
  }

  private void skipOutputBuffer(MediaCodec codec, int bufferIndex) {
    TraceUtil.beginSection("skipVideoBuffer");
    codec.releaseOutputBuffer(bufferIndex, false);
    TraceUtil.endSection();
    codecCounters.skippedOutputBufferCount++;
  }

  private void dropOutputBuffer(MediaCodec codec, int bufferIndex) {
    TraceUtil.beginSection("dropVideoBuffer");
    codec.releaseOutputBuffer(bufferIndex, false);
    TraceUtil.endSection();
    codecCounters.droppedOutputBufferCount++;
    droppedFrameCount++;
    if (droppedFrameCount == maxDroppedFrameCountToNotify) {
      notifyAndResetDroppedFrameCount();
    }
  }

  private void renderOutputBuffer(MediaCodec codec, int bufferIndex) {
    if (lastReportedWidth != currentWidth || lastReportedHeight != currentHeight) {
      lastReportedWidth = currentWidth;
      lastReportedHeight = currentHeight;
      notifyVideoSizeChanged(currentWidth, currentHeight);
    }
    TraceUtil.beginSection("renderVideoBuffer");
    codec.releaseOutputBuffer(bufferIndex, true);
    TraceUtil.endSection();
    codecCounters.renderedOutputBufferCount++;
    if (!drawnToSurface) {
      drawnToSurface = true;
      notifyDrawnToSurface(surface);
    }
  }

  private void notifyVideoSizeChanged(final int width, final int height) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onVideoSizeChanged(width, height);
        }
      });
    }
  }

  private void notifyDrawnToSurface(final Surface surface) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDrawnToSurface(surface);
        }
      });
    }
  }

  private void notifyAndResetDroppedFrameCount() {
    if (eventHandler != null && eventListener != null && droppedFrameCount > 0) {
      long now = SystemClock.elapsedRealtime();
      final int countToNotify = droppedFrameCount;
      final long elapsedToNotify = now - droppedFrameAccumulationStartTimeMs;
      droppedFrameCount = 0;
      droppedFrameAccumulationStartTimeMs = now;
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDroppedFrames(countToNotify, elapsedToNotify);
        }
      });
    }
  }

}
