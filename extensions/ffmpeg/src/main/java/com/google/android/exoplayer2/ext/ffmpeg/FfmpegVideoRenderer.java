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

import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.Decoder;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.TraceUtil;
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

  private static final String TAG = "FfmpegAudioRenderer";

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
    super(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
    // TODO: Implement.
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  @RendererCapabilities.Capabilities
  public final int supportsFormat(Format format) {
    // TODO: Remove this line and uncomment the implementation below.
    return FORMAT_UNSUPPORTED_TYPE;
    /*
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
    */
  }

  @SuppressWarnings("return.type.incompatible")
  @Override
  protected Decoder<VideoDecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegDecoderException>
      createDecoder(Format format, @Nullable ExoMediaCrypto mediaCrypto)
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
}
