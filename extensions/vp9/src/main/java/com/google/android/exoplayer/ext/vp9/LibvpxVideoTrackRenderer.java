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

import com.google.android.exoplayer.CodecCounters;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.SampleSourceTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.extensions.Buffer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

/**
 * Decodes and renders video using the native VP9 decoder.
 */
public final class LibvpxVideoTrackRenderer extends SampleSourceTrackRenderer {

  /**
   * Interface definition for a callback to be notified of {@link LibvpxVideoTrackRenderer} events.
   */
  public interface EventListener {

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

    /**
     * Invoked when one of the following happens: libvpx initialization failure, decoder error,
     * renderer error.
     *
     * @param e The corresponding exception.
     */
    void onDecoderError(VpxDecoderException e);

    /**
     * Invoked when a decoder is successfully created.
     *
     * @param decoderName The decoder that was configured and created.
     * @param elapsedRealtimeMs {@code elapsedRealtime} timestamp of when the initialization
     *    finished.
     * @param initializationDurationMs Amount of time taken to initialize the decoder.
     */
    void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
        long initializationDurationMs);

  }

  /**
   * The type of a message that can be passed to an instance of this class via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be the target {@link Surface}, or null.
   */
  public static final int MSG_SET_SURFACE = 1;
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
  private final Handler eventHandler;
  private final EventListener eventListener;
  private final int maxDroppedFrameCountToNotify;
  private final MediaFormatHolder formatHolder;

  private MediaFormat format;
  private VpxDecoder decoder;
  private VpxInputBuffer inputBuffer;
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
  private boolean sourceIsReady;
  private int previousWidth;
  private int previousHeight;

  private int droppedFrameCount;
  private long droppedFrameAccumulationStartTimeMs;

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param scaleToFit Boolean that indicates if video frames should be scaled to fit when
   *     rendering.
   */
  public LibvpxVideoTrackRenderer(SampleSource source, boolean scaleToFit) {
    this(source, scaleToFit, null, null, 0);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param scaleToFit Boolean that indicates if video frames should be scaled to fit when
   *     rendering.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFrameCountToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link EventListener#onDroppedFrames(int, long)}.
   */
  public LibvpxVideoTrackRenderer(SampleSource source, boolean scaleToFit,
      Handler eventHandler, EventListener eventListener, int maxDroppedFrameCountToNotify) {
    super(source);
    this.scaleToFit = scaleToFit;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.maxDroppedFrameCountToNotify = maxDroppedFrameCountToNotify;
    previousWidth = -1;
    previousHeight = -1;
    formatHolder = new MediaFormatHolder();
    outputMode = VpxDecoder.OUTPUT_MODE_UNKNOWN;
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
  protected boolean handlesTrack(MediaFormat mediaFormat) {
    return MimeTypes.VIDEO_VP9.equalsIgnoreCase(mediaFormat.mimeType);
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady)
      throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }
    this.sourceIsReady = sourceIsReady;

    // Try and read a format if we don't have one already.
    if (format == null && !readFormat(positionUs)) {
      // We can't make progress without one.
      return;
    }

    try {
      if (decoder == null) {
        // If we don't have a decoder yet, we need to instantiate one.
        long startElapsedRealtimeMs = SystemClock.elapsedRealtime();
        decoder = new VpxDecoder(NUM_BUFFERS, NUM_BUFFERS, INITIAL_INPUT_BUFFER_SIZE);
        decoder.setOutputMode(outputMode);
        decoder.start();
        notifyDecoderInitialized(startElapsedRealtimeMs, SystemClock.elapsedRealtime());
        codecCounters.codecInitCount++;
      }
      while (processOutputBuffer(positionUs)) {}
      while (feedInputBuffer(positionUs)) {}
    } catch (VpxDecoderException e) {
      notifyDecoderError(e);
      throw new ExoPlaybackException(e);
    }
    codecCounters.ensureUpdated();
  }

  private boolean processOutputBuffer(long positionUs)
      throws VpxDecoderException {
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
    }

    if (nextOutputBuffer == null) {
      nextOutputBuffer = decoder.dequeueOutputBuffer();
    }

    if (outputBuffer.getFlag(Buffer.FLAG_END_OF_STREAM)) {
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
      if (droppedFrameCount == maxDroppedFrameCountToNotify) {
        notifyAndResetDroppedFrameCount();
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
    notifyIfVideoSizeChanged(outputBuffer.width, outputBuffer.height);
    if (outputBuffer.mode == VpxDecoder.OUTPUT_MODE_RGB && surface != null) {
      renderRgbFrame(outputBuffer, scaleToFit);
      if (!drawnToSurface) {
        drawnToSurface = true;
        notifyDrawnToSurface(surface);
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

  private boolean feedInputBuffer(long positionUs) throws VpxDecoderException {
    if (inputStreamEnded) {
      return false;
    }

    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }

    int result = readSource(positionUs, formatHolder, inputBuffer.sampleHolder);
    if (result == SampleSource.NOTHING_READ) {
      return false;
    }
    if (result == SampleSource.FORMAT_READ) {
      format = formatHolder.format;
      return true;
    }
    if (result == SampleSource.END_OF_STREAM) {
      inputBuffer.setFlag(Buffer.FLAG_END_OF_STREAM);
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      inputStreamEnded = true;
      return false;
    }

    inputBuffer.width = format.width;
    inputBuffer.height = format.height;
    if (inputBuffer.sampleHolder.isDecodeOnly()) {
      inputBuffer.setFlag(Buffer.FLAG_DECODE_ONLY);
    }
    decoder.queueInputBuffer(inputBuffer);
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
    return format != null && (sourceIsReady || outputBuffer != null) && renderedFirstFrame;
  }

  @Override
  protected void onDiscontinuity(long positionUs) {
    sourceIsReady = false;
    inputStreamEnded = false;
    outputStreamEnded = false;
    renderedFirstFrame = false;
    if (decoder != null) {
      flushDecoder();
    }
  }

  @Override
  protected void onStarted() {
    droppedFrameCount = 0;
    droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
  }

  @Override
  protected void onStopped() {
    notifyAndResetDroppedFrameCount();
  }

  @Override
  protected void onDisabled() throws ExoPlaybackException {
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

  private boolean readFormat(long positionUs) {
    int result = readSource(positionUs, formatHolder, null);
    if (result == SampleSource.FORMAT_READ) {
      format = formatHolder.format;
      return true;
    }
    return false;
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    if (messageType == MSG_SET_SURFACE) {
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
    outputMode = (surface != null) ? VpxDecoder.OUTPUT_MODE_RGB : VpxDecoder.OUTPUT_MODE_UNKNOWN;
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
    outputMode = (outputBufferRenderer != null)
        ? VpxDecoder.OUTPUT_MODE_YUV : VpxDecoder.OUTPUT_MODE_UNKNOWN;
    if (decoder != null) {
      decoder.setOutputMode(outputMode);
    }
  }

  private void notifyIfVideoSizeChanged(final int width, final int height) {
    if (previousWidth == -1 || previousHeight == -1 || previousWidth != width
        || previousHeight != height) {
      previousWidth = width;
      previousHeight = height;
      if (eventHandler != null && eventListener != null) {
        eventHandler.post(new Runnable()  {
          @Override
          public void run() {
            eventListener.onVideoSizeChanged(width, height);
          }
        });
      }
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

  private void notifyDecoderError(final VpxDecoderException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDecoderError(e);
        }
      });
    }
  }

  private void notifyDecoderInitialized(
      final long startElapsedRealtimeMs, final long finishElapsedRealtimeMs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onDecoderInitialized("libvpx" + getLibvpxVersion(),
              finishElapsedRealtimeMs, finishElapsedRealtimeMs - startElapsedRealtimeMs);
        }
      });
    }
  }

}
