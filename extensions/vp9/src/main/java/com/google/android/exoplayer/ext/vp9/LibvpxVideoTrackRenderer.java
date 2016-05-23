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
package com.google.android.exoplayer.ext.vp9;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.CodecCounters;
import com.google.android.exoplayer.DecoderInputBuffer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.VideoTrackRendererEventListener;
import com.google.android.exoplayer.VideoTrackRendererEventListener.EventDispatcher;
import com.google.android.exoplayer.util.MimeTypes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

/**
 * Decodes and renders video using the native VP9 decoder.
 */
public final class LibvpxVideoTrackRenderer extends TrackRenderer {

  /**
   * The type of a message that can be passed to an instance of this class via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be the target {@link VpxOutputBufferRenderer}, or null.
   */
  public static final int MSG_SET_OUTPUT_BUFFER_RENDERER = 2;

  /**
   * The number of input buffers and the number of output buffers. The track renderer may limit the
   * minimum possible value due to requiring multiple output buffers to be dequeued at a time for it
   * to make progress.
   */
  private static final int NUM_BUFFERS = 16;
  private static final int INITIAL_INPUT_BUFFER_SIZE = 768 * 1024; // Value based on cs/SoftVpx.cpp.

  public final CodecCounters codecCounters = new CodecCounters();

  private final boolean scaleToFit;
  private final EventDispatcher eventDispatcher;
  private final int maxDroppedFrameCountToNotify;
  private final FormatHolder formatHolder;

  private Format format;
  private VpxDecoder decoder;
  private DecoderInputBuffer inputBuffer;
  private VpxOutputBuffer outputBuffer;
  private VpxOutputBuffer nextOutputBuffer;

  private Bitmap bitmap;
  private boolean drawnToSurface;
  private boolean renderedFirstFrame;
  private Surface surface;
  private VpxOutputBufferRenderer outputBufferRenderer;
  private int outputMode;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private int previousWidth;
  private int previousHeight;

  private long droppedFrameAccumulationStartTimeMs;
  private int droppedFrameCount;
  private int consecutiveDroppedFrameCount;

  /**
   * @param scaleToFit Boolean that indicates if video frames should be scaled to fit when
   *     rendering.
   */
  public LibvpxVideoTrackRenderer(boolean scaleToFit) {
    this(scaleToFit, null, null, 0);
  }

  /**
   * @param scaleToFit Boolean that indicates if video frames should be scaled to fit when
   *     rendering.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFrameCountToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoTrackRendererEventListener#onDroppedFrames(int, long)}.
   */
  public LibvpxVideoTrackRenderer(boolean scaleToFit, Handler eventHandler,
      VideoTrackRendererEventListener eventListener, int maxDroppedFrameCountToNotify) {
    this.scaleToFit = scaleToFit;
    this.maxDroppedFrameCountToNotify = maxDroppedFrameCountToNotify;
    previousWidth = -1;
    previousHeight = -1;
    formatHolder = new FormatHolder();
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    outputMode = VpxDecoder.OUTPUT_MODE_NONE;
  }

  /**
   * Returns whether the underlying libvpx library is available.
   */
  public static boolean isLibvpxAvailable() {
    return VpxDecoder.IS_AVAILABLE;
  }

  /**
   * Returns the version of the underlying libvpx library if available, otherwise {@code null}.
   */
  public static String getLibvpxVersion() {
    return isLibvpxAvailable() ? VpxDecoder.getLibvpxVersion() : null;
  }

  @Override
  public int getTrackType() {
    return C.TRACK_TYPE_VIDEO;
  }

  @Override
  protected int supportsFormat(Format format) {
    return isLibvpxAvailable() && MimeTypes.VIDEO_VP9.equalsIgnoreCase(format.sampleMimeType)
        ? (FORMAT_HANDLED | ADAPTIVE_SEAMLESS) : FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  protected void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }

    // Try and read a format if we don't have one already.
    if (format == null && !readFormat()) {
      // We can't make progress without one.
      return;
    }

    try {
      if (decoder == null) {
        // If we don't have a decoder yet, we need to instantiate one.
        long codecInitializingTimestamp = SystemClock.elapsedRealtime();
        decoder = new VpxDecoder(NUM_BUFFERS, NUM_BUFFERS, INITIAL_INPUT_BUFFER_SIZE);
        decoder.setOutputMode(outputMode);
        long codecInitializedTimestamp = SystemClock.elapsedRealtime();
        eventDispatcher.decoderInitialized(decoder.getName(), codecInitializedTimestamp,
            codecInitializedTimestamp - codecInitializingTimestamp);
        codecCounters.codecInitCount++;
      }
      while (drainOutputBuffer(positionUs)) {}
      while (feedInputBuffer()) {}
    } catch (VpxDecoderException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
    codecCounters.ensureUpdated();
  }

  private boolean drainOutputBuffer(long positionUs) throws VpxDecoderException {
    if (outputStreamEnded) {
      return false;
    }

    // Acquire outputBuffer either from nextOutputBuffer or from the decoder.
    if (outputBuffer == null) {
      if (nextOutputBuffer != null) {
        outputBuffer = nextOutputBuffer;
        nextOutputBuffer = null;
      } else {
        outputBuffer = decoder.dequeueOutputBuffer();
      }
      if (outputBuffer == null) {
        return false;
      }
      codecCounters.skippedOutputBufferCount += outputBuffer.skippedOutputBufferCount;
    }

    if (nextOutputBuffer == null) {
      nextOutputBuffer = decoder.dequeueOutputBuffer();
    }

    if (outputBuffer.isEndOfStream()) {
      outputStreamEnded = true;
      outputBuffer.release();
      outputBuffer = null;
      return false;
    }

    // Drop frame only if we have the next frame and that's also late, otherwise render whatever we
    // have.
    if (nextOutputBuffer != null && nextOutputBuffer.timestampUs < positionUs) {
      // Drop frame if we are too late.
      codecCounters.droppedOutputBufferCount++;
      droppedFrameCount++;
      consecutiveDroppedFrameCount++;
      codecCounters.maxConsecutiveDroppedOutputBufferCount = Math.max(consecutiveDroppedFrameCount,
          codecCounters.maxConsecutiveDroppedOutputBufferCount);
      if (droppedFrameCount == maxDroppedFrameCountToNotify) {
        maybeNotifyDroppedFrameCount();
      }
      outputBuffer.release();
      outputBuffer = null;
      return true;
    }

    // If we have not rendered any frame so far (either initially or immediately following a seek),
    // render one frame irrespective of the state or current position.
    if (!renderedFirstFrame) {
      renderBuffer();
      renderedFirstFrame = true;
      return false;
    }

    if (getState() == TrackRenderer.STATE_STARTED
        && outputBuffer.timestampUs <= positionUs + 30000) {
      renderBuffer();
    }
    return false;
  }

  private void renderBuffer() {
    codecCounters.renderedOutputBufferCount++;
    consecutiveDroppedFrameCount = 0;
    maybeNotifyVideoSizeChanged(outputBuffer.width, outputBuffer.height);
    if (outputBuffer.mode == VpxDecoder.OUTPUT_MODE_RGB && surface != null) {
      renderRgbFrame(outputBuffer, scaleToFit);
      if (!drawnToSurface) {
        drawnToSurface = true;
        eventDispatcher.drawnToSurface(surface);
      }
      outputBuffer.release();
    } else if (outputBuffer.mode == VpxDecoder.OUTPUT_MODE_YUV && outputBufferRenderer != null) {
      // The renderer will release the buffer.
      outputBufferRenderer.setOutputBuffer(outputBuffer);
    } else {
      outputBuffer.release();
    }
    outputBuffer = null;
  }

  private void renderRgbFrame(VpxOutputBuffer outputBuffer, boolean scale) {
    if (bitmap == null || bitmap.getWidth() != outputBuffer.width
        || bitmap.getHeight() != outputBuffer.height) {
      bitmap = Bitmap.createBitmap(outputBuffer.width, outputBuffer.height, Bitmap.Config.RGB_565);
    }
    bitmap.copyPixelsFromBuffer(outputBuffer.data);
    Canvas canvas = surface.lockCanvas(null);
    if (scale) {
      canvas.scale(((float) canvas.getWidth()) / outputBuffer.width,
          ((float) canvas.getHeight()) / outputBuffer.height);
    }
    canvas.drawBitmap(bitmap, 0, 0, null);
    surface.unlockCanvasAndPost(canvas);
  }

  private boolean feedInputBuffer() throws VpxDecoderException {
    if (inputStreamEnded) {
      return false;
    }

    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }

    int result = readSource(formatHolder, inputBuffer);
    if (result == TrackStream.NOTHING_READ) {
      return false;
    }
    if (result == TrackStream.FORMAT_READ) {
      onInputFormatChanged(formatHolder.format);
      return true;
    }
    if (inputBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      return false;
    }
    inputBuffer.flip();
    decoder.queueInputBuffer(inputBuffer);
    codecCounters.inputBufferCount++;
    inputBuffer = null;
    return true;
  }

  private void flushDecoder() {
    inputBuffer = null;
    if (outputBuffer != null) {
      outputBuffer.release();
      outputBuffer = null;
    }
    if (nextOutputBuffer != null) {
      nextOutputBuffer.release();
      nextOutputBuffer = null;
    }
    decoder.flush();
  }

  @Override
  protected boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  protected boolean isReady() {
    return format != null && (isSourceReady() || outputBuffer != null) && renderedFirstFrame;
  }

  @Override
  protected void reset(long positionUs) {
    inputStreamEnded = false;
    outputStreamEnded = false;
    renderedFirstFrame = false;
    consecutiveDroppedFrameCount = 0;
    if (decoder != null) {
      flushDecoder();
    }
  }

  @Override
  protected void onEnabled(Format[] formats, boolean joining) throws ExoPlaybackException {
    eventDispatcher.codecCounters(codecCounters);
  }

  @Override
  protected void onStarted() {
    droppedFrameCount = 0;
    droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
  }

  @Override
  protected void onStopped() {
    maybeNotifyDroppedFrameCount();
  }

  @Override
  protected void onDisabled() {
    inputBuffer = null;
    outputBuffer = null;
    format = null;
    try {
      if (decoder != null) {
        decoder.release();
        decoder = null;
        codecCounters.codecReleaseCount++;
      }
    } finally {
      super.onDisabled();
    }
  }

  private boolean readFormat() {
    int result = readSource(formatHolder, null);
    if (result == TrackStream.FORMAT_READ) {
      onInputFormatChanged(formatHolder.format);
      return true;
    }
    return false;
  }

  private void onInputFormatChanged(Format newFormat) {
    format = newFormat;
    eventDispatcher.inputFormatChanged(format);
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    if (messageType == C.MSG_SET_SURFACE) {
      setSurface((Surface) message);
    } else if (messageType == MSG_SET_OUTPUT_BUFFER_RENDERER) {
      setOutputBufferRenderer((VpxOutputBufferRenderer) message);
    } else {
      super.handleMessage(messageType, message);
    }
  }

  private void setSurface(Surface surface) {
    if (this.surface == surface) {
      return;
    }
    this.surface = surface;
    outputBufferRenderer = null;
    outputMode = (surface != null) ? VpxDecoder.OUTPUT_MODE_RGB : VpxDecoder.OUTPUT_MODE_NONE;
    if (decoder != null) {
      decoder.setOutputMode(outputMode);
    }
    drawnToSurface = false;
  }

  private void setOutputBufferRenderer(VpxOutputBufferRenderer outputBufferRenderer) {
    if (this.outputBufferRenderer == outputBufferRenderer) {
      return;
    }
    this.outputBufferRenderer = outputBufferRenderer;
    surface = null;
    outputMode = (outputBufferRenderer != null) ? VpxDecoder.OUTPUT_MODE_YUV
        : VpxDecoder.OUTPUT_MODE_NONE;
    if (decoder != null) {
      decoder.setOutputMode(outputMode);
    }
  }

  private void maybeNotifyVideoSizeChanged(final int width, final int height) {
    if (previousWidth != width || previousHeight != height) {
      previousWidth = width;
      previousHeight = height;
      eventDispatcher.videoSizeChanged(width, height, 0, 1);
    }
  }

  private void maybeNotifyDroppedFrameCount() {
    if (droppedFrameCount > 0) {
      long now = SystemClock.elapsedRealtime();
      long elapsedMs = now - droppedFrameAccumulationStartTimeMs;
      eventDispatcher.droppedFrameCount(droppedFrameCount, elapsedMs);
      droppedFrameCount = 0;
      droppedFrameAccumulationStartTimeMs = now;
    }
  }

}
