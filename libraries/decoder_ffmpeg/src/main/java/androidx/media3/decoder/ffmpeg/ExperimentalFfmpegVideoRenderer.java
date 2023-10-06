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
package androidx.media3.decoder.ffmpeg;

import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_MIME_TYPE_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION;

import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.Decoder;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.video.DecoderVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

// TODO: Merge actual implementation in https://github.com/google/ExoPlayer/pull/7132.
/**
 * <b>NOTE: This class if under development and is not yet functional.</b>
 *
 * <p>Decodes and renders video using FFmpeg.
 */
@UnstableApi
public final class ExperimentalFfmpegVideoRenderer extends DecoderVideoRenderer {

  private static final String TAG = "ExperimentalFfmpegVideoRenderer";

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
  public ExperimentalFfmpegVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    super(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
    // TODO: Implement.
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public final @RendererCapabilities.Capabilities int supportsFormat(Format format) {
    // TODO: Remove this line and uncomment the implementation below.
    return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    /*
    String mimeType = Assertions.checkNotNull(format.sampleMimeType);
    if (!FfmpegLibrary.isAvailable() || !MimeTypes.isVideo(mimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    } else if (!FfmpegLibrary.supportsFormat(format.sampleMimeType)) {
      return RendererCapabilities.create(FORMAT_UNSUPPORTED_SUBTYPE);
    } else if (format.exoMediaCryptoType != null) {
      return RendererCapabilities.create(FORMAT_UNSUPPORTED_DRM);
    } else {
      return RendererCapabilities.create(
          FORMAT_HANDLED,
          ADAPTIVE_SEAMLESS,
          TUNNELING_NOT_SUPPORTED);
    }
    */
  }

  @SuppressWarnings("nullness:return")
  @Override
  protected Decoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegDecoderException>
      createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
          throws FfmpegDecoderException {
    TraceUtil.beginSection("createFfmpegVideoDecoder");
    // TODO: Implement, remove the SuppressWarnings annotation, and update the return type to use
    // the concrete type of the decoder (probably FfmepgVideoDecoder).
    TraceUtil.endSection();
    return null;
  }

  @Override
  protected void renderOutputBufferToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws FfmpegDecoderException {
    // TODO: Implement.
  }

  @Override
  protected void setDecoderOutputMode(@C.VideoOutputMode int outputMode) {
    // TODO: Uncomment the implementation below.
    /*
    if (decoder != null) {
      decoder.setOutputMode(outputMode);
    }
    */
  }

  @Override
  protected DecoderReuseEvaluation canReuseDecoder(
      String decoderName, Format oldFormat, Format newFormat) {
    boolean sameMimeType = Util.areEqual(oldFormat.sampleMimeType, newFormat.sampleMimeType);
    // TODO: Ability to reuse the decoder may be MIME type dependent.
    return new DecoderReuseEvaluation(
        decoderName,
        oldFormat,
        newFormat,
        sameMimeType ? REUSE_RESULT_YES_WITHOUT_RECONFIGURATION : REUSE_RESULT_NO,
        sameMimeType ? 0 : DISCARD_REASON_MIME_TYPE_CHANGED);
  }
}
