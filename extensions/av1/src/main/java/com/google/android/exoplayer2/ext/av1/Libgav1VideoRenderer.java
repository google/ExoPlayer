/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.av1;

import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.SimpleDecoderVideoRenderer;
import com.google.android.exoplayer2.video.VideoDecoderException;
import com.google.android.exoplayer2.video.VideoDecoderInputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

/**
 * Decodes and renders video using libgav1 decoder.
 *
 * <p>This renderer accepts the following messages sent via {@link ExoPlayer#createMessage(Target)}
 * on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link C#MSG_SET_SURFACE} to set the output surface. The message payload
 *       should be the target {@link Surface}, or null.
 *   <li>Message with type {@link C#MSG_SET_VIDEO_DECODER_OUTPUT_BUFFER_RENDERER} to set the output
 *       buffer renderer. The message payload should be the target {@link
 *       VideoDecoderOutputBufferRenderer}, or null.
 * </ul>
 */
public class Libgav1VideoRenderer extends SimpleDecoderVideoRenderer {

  /**
   * Attempts to use as many threads as performance processors available on the device. If the
   * number of performance processors cannot be detected, the number of available processors is
   * used.
   */
  public static final int THREAD_COUNT_AUTODETECT = 0;

  private static final int DEFAULT_NUM_OF_INPUT_BUFFERS = 4;
  private static final int DEFAULT_NUM_OF_OUTPUT_BUFFERS = 4;
  /* Default size based on 720p resolution video compressed by a factor of two. */
  private static final int DEFAULT_INPUT_BUFFER_SIZE =
      Util.ceilDivide(1280, 64) * Util.ceilDivide(720, 64) * (64 * 64 * 3 / 2) / 2;

  /** The number of input buffers. */
  private final int numInputBuffers;
  /**
   * The number of output buffers. The renderer may limit the minimum possible value due to
   * requiring multiple output buffers to be dequeued at a time for it to make progress.
   */
  private final int numOutputBuffers;

  private final int threads;

  @Nullable private Gav1Decoder decoder;

  /**
   * Creates a Libgav1VideoRenderer.
   *
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public Libgav1VideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    this(
        allowedJoiningTimeMs,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify,
        THREAD_COUNT_AUTODETECT,
        DEFAULT_NUM_OF_INPUT_BUFFERS,
        DEFAULT_NUM_OF_OUTPUT_BUFFERS);
  }

  /**
   * Creates a Libgav1VideoRenderer.
   *
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   * @param threads Number of threads libgav1 will use to decode. If
   *     {@link #THREAD_COUNT_AUTODETECT} is passed, then the number of threads to use is
   *     auto-detected based on CPU capabilities.
   * @param numInputBuffers Number of input buffers.
   * @param numOutputBuffers Number of output buffers.
   */
  public Libgav1VideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      int threads,
      int numInputBuffers,
      int numOutputBuffers) {
    super(
        allowedJoiningTimeMs,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false);
    this.threads = threads;
    this.numInputBuffers = numInputBuffers;
    this.numOutputBuffers = numOutputBuffers;
  }

  @Override
  @Capabilities
  protected int supportsFormatInternal(
      @Nullable DrmSessionManager<ExoMediaCrypto> drmSessionManager, Format format) {
    if (!MimeTypes.VIDEO_AV1.equalsIgnoreCase(format.sampleMimeType)
        || !Gav1Library.isAvailable()) {
      return RendererCapabilities.create(FORMAT_UNSUPPORTED_TYPE);
    }
    if (!supportsFormatDrm(drmSessionManager, format.drmInitData)) {
      return RendererCapabilities.create(FORMAT_UNSUPPORTED_DRM);
    }
    return RendererCapabilities.create(FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED);
  }

  @Override
  protected SimpleDecoder<
          VideoDecoderInputBuffer,
          ? extends VideoDecoderOutputBuffer,
          ? extends VideoDecoderException>
      createDecoder(Format format, @Nullable ExoMediaCrypto mediaCrypto)
          throws VideoDecoderException {
    TraceUtil.beginSection("createGav1Decoder");
    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    Gav1Decoder decoder =
        new Gav1Decoder(numInputBuffers, numOutputBuffers, initialInputBufferSize, threads);
    this.decoder = decoder;
    TraceUtil.endSection();
    return decoder;
  }

  @Override
  protected void renderOutputBufferToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws Gav1DecoderException {
    if (decoder == null) {
      throw new Gav1DecoderException(
          "Failed to render output buffer to surface: decoder is not initialized.");
    }
    decoder.renderToSurface(outputBuffer, surface);
    outputBuffer.release();
  }

  @Override
  protected void setDecoderOutputMode(@C.VideoOutputMode int outputMode) {
    if (decoder != null) {
      decoder.setOutputMode(outputMode);
    }
  }

  // PlayerMessage.Target implementation.

  @Override
  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    if (messageType == C.MSG_SET_SURFACE) {
      setOutputSurface((Surface) message);
    } else if (messageType == C.MSG_SET_VIDEO_DECODER_OUTPUT_BUFFER_RENDERER) {
      setOutputBufferRenderer((VideoDecoderOutputBufferRenderer) message);
    } else {
      super.handleMessage(messageType, message);
    }
  }
}
