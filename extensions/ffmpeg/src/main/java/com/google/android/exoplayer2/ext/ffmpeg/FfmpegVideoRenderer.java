/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.ffmpeg;

import static java.lang.Runtime.getRuntime;

import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.Decoder;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.DecoderVideoRenderer;
import com.google.android.exoplayer2.video.VideoDecoderInputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBuffer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

// TODO: Remove the NOTE below.
/**
 * <b>NOTE: This class if under development and is not yet functional.</b>
 *
 * <p>Decodes and renders video using FFmpeg.
 */
public final class FfmpegVideoRenderer extends DecoderVideoRenderer {

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

  @Nullable private FfmpegVideoDecoder decoder;

  /**
   * Creates a new instance.
   *
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public FfmpegVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    this(
        allowedJoiningTimeMs,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify,
        /* threads= */ getRuntime().availableProcessors(),
        DEFAULT_NUM_OF_INPUT_BUFFERS,
        DEFAULT_NUM_OF_OUTPUT_BUFFERS);
  }

  /**
   * Creates a new instance.
   *
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   * @param threads Number of threads libgav1 will use to decode.
   * @param numInputBuffers Number of input buffers.
   * @param numOutputBuffers Number of output buffers.
   */
  public FfmpegVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      int threads,
      int numInputBuffers,
      int numOutputBuffers) {
    super(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
    this.threads = threads;
    this.numInputBuffers = numInputBuffers;
    this.numOutputBuffers = numOutputBuffers;
  }

  @Override
  @RendererCapabilities.Capabilities
  public final int supportsFormat(Format format) {
    String mimeType = Assertions.checkNotNull(format.sampleMimeType);
    if (!FfmpegLibrary.isAvailable() || !MimeTypes.isVideo(mimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    } else if (!FfmpegLibrary.supportsFormat(format.sampleMimeType)) {
      return RendererCapabilities.create(FORMAT_UNSUPPORTED_SUBTYPE);
    } else if (format.drmInitData != null && format.exoMediaCryptoType == null) {
      return RendererCapabilities.create(FORMAT_UNSUPPORTED_DRM);
    } else {
      return RendererCapabilities.create(
          FORMAT_HANDLED,
          ADAPTIVE_SEAMLESS,
          TUNNELING_NOT_SUPPORTED);
    }
  }

  @Override
  protected Decoder<VideoDecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegDecoderException>
      createDecoder(Format format, @Nullable ExoMediaCrypto mediaCrypto)
          throws FfmpegDecoderException {
    TraceUtil.beginSection("createGav1Decoder");
    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    FfmpegVideoDecoder decoder =
        new FfmpegVideoDecoder(numInputBuffers, numOutputBuffers, initialInputBufferSize, threads, format);
    this.decoder = decoder;
    TraceUtil.endSection();
    return decoder;
  }

  @Override
  protected void renderOutputBufferToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws FfmpegDecoderException {
    if (decoder == null) {
      throw new FfmpegDecoderException(
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
}
