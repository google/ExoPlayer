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
package com.google.android.exoplayer2.ext.vp9;

import static java.lang.Runtime.getRuntime;

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
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.video.SimpleDecoderVideoRenderer;
import com.google.android.exoplayer2.video.VideoDecoderException;
import com.google.android.exoplayer2.video.VideoDecoderInputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

/**
 * Decodes and renders video using the native VP9 decoder.
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
public class LibvpxVideoRenderer extends SimpleDecoderVideoRenderer {

  /** The number of input buffers. */
  private final int numInputBuffers;
  /**
   * The number of output buffers. The renderer may limit the minimum possible value due to
   * requiring multiple output buffers to be dequeued at a time for it to make progress.
   */
  private final int numOutputBuffers;
  /**
   * The default input buffer size. The value is based on <a
   * href="https://android.googlesource.com/platform/frameworks/av/+/d42b90c5183fbd9d6a28d9baee613fddbf8131d6/media/libstagefright/codecs/on2/dec/SoftVPX.cpp">SoftVPX.cpp</a>.
   */
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 768 * 1024;

  private final int threads;

  @Nullable private VpxDecoder decoder;
  @Nullable private VideoFrameMetadataListener frameMetadataListener;

  /**
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   */
  public LibvpxVideoRenderer(long allowedJoiningTimeMs) {
    this(allowedJoiningTimeMs, null, null, 0);
  }

  /**
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public LibvpxVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    this(
        allowedJoiningTimeMs,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false);
  }

  /**
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   * @param drmSessionManager For use with encrypted media. May be null if support for encrypted
   *     media is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @deprecated Use {@link #LibvpxVideoRenderer(long, Handler, VideoRendererEventListener, int,
   *     int, int, int)}} instead, and pass DRM-related parameters to the {@link MediaSource}
   *     factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public LibvpxVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      @Nullable DrmSessionManager<ExoMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys) {
    this(
        allowedJoiningTimeMs,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        getRuntime().availableProcessors(),
        /* numInputBuffers= */ 4,
        /* numOutputBuffers= */ 4);
  }

  /**
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   * @param threads Number of threads libvpx will use to decode.
   * @param numInputBuffers Number of input buffers.
   * @param numOutputBuffers Number of output buffers.
   */
  @SuppressWarnings("deprecation")
  public LibvpxVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      int threads,
      int numInputBuffers,
      int numOutputBuffers) {
    this(
        allowedJoiningTimeMs,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false,
        threads,
        numInputBuffers,
        numOutputBuffers);
  }

  /**
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   * @param drmSessionManager For use with encrypted media. May be null if support for encrypted
   *     media is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param threads Number of threads libvpx will use to decode.
   * @param numInputBuffers Number of input buffers.
   * @param numOutputBuffers Number of output buffers.
   * @deprecated Use {@link #LibvpxVideoRenderer(long, Handler, VideoRendererEventListener, int,
   *     int, int, int)}} instead, and pass DRM-related parameters to the {@link MediaSource}
   *     factories.
   */
  @Deprecated
  public LibvpxVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      @Nullable DrmSessionManager<ExoMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      int threads,
      int numInputBuffers,
      int numOutputBuffers) {
    super(
        allowedJoiningTimeMs,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify,
        drmSessionManager,
        playClearSamplesWithoutKeys);
    this.threads = threads;
    this.numInputBuffers = numInputBuffers;
    this.numOutputBuffers = numOutputBuffers;
  }

  @Override
  @Capabilities
  protected int supportsFormatInternal(
      @Nullable DrmSessionManager<ExoMediaCrypto> drmSessionManager, Format format) {
    if (!VpxLibrary.isAvailable() || !MimeTypes.VIDEO_VP9.equalsIgnoreCase(format.sampleMimeType)) {
      return RendererCapabilities.create(FORMAT_UNSUPPORTED_TYPE);
    }
    boolean drmIsSupported =
        format.drmInitData == null
            || VpxLibrary.matchesExpectedExoMediaCryptoType(format.exoMediaCryptoType)
            || (format.exoMediaCryptoType == null
                && supportsFormatDrm(drmSessionManager, format.drmInitData));
    if (!drmIsSupported) {
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
    TraceUtil.beginSection("createVpxDecoder");
    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    VpxDecoder decoder =
        new VpxDecoder(
            numInputBuffers, numOutputBuffers, initialInputBufferSize, mediaCrypto, threads);
    this.decoder = decoder;
    TraceUtil.endSection();
    return decoder;
  }

  @Override
  protected void renderOutputBuffer(
      VideoDecoderOutputBuffer outputBuffer, long presentationTimeUs, Format outputFormat)
      throws VideoDecoderException {
    if (frameMetadataListener != null) {
      frameMetadataListener.onVideoFrameAboutToBeRendered(
          presentationTimeUs, System.nanoTime(), outputFormat, /* mediaFormat= */ null);
    }
    super.renderOutputBuffer(outputBuffer, presentationTimeUs, outputFormat);
  }

  @Override
  protected void renderOutputBufferToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws VpxDecoderException {
    if (decoder == null) {
      throw new VpxDecoderException(
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
    } else if (messageType == C.MSG_SET_VIDEO_FRAME_METADATA_LISTENER) {
      frameMetadataListener = (VideoFrameMetadataListener) message;
    } else {
      super.handleMessage(messageType, message);
    }
  }
}
