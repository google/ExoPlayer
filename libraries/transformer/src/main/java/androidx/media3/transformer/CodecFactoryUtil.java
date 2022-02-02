/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.media3.transformer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.util.TraceUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Utility methods for {@link Codec}'s factory methods. */
/* package */ final class CodecFactoryUtil {
  /** Creates a {@link Codec}. */
  @RequiresNonNull("#1.sampleMimeType")
  public static Codec createCodec(
      Format format,
      MediaFormat mediaFormat,
      @Nullable String mediaCodecName,
      boolean isVideo,
      boolean isDecoder,
      @Nullable Surface outputSurface)
      throws TransformationException {
    @Nullable MediaCodec mediaCodec = null;
    @Nullable Surface inputSurface = null;
    try {
      mediaCodec =
          mediaCodecName != null
              ? MediaCodec.createByCodecName(mediaCodecName)
              : isDecoder
                  ? MediaCodec.createDecoderByType(format.sampleMimeType)
                  : MediaCodec.createEncoderByType(format.sampleMimeType);
      configureCodec(mediaCodec, mediaFormat, isDecoder, outputSurface);
      if (isVideo && !isDecoder) {
        inputSurface = mediaCodec.createInputSurface();
      }
      startCodec(mediaCodec);
    } catch (Exception e) {
      if (inputSurface != null) {
        inputSurface.release();
      }
      if (mediaCodec != null) {
        mediaCodecName = mediaCodec.getName();
        mediaCodec.release();
      }
      throw createTransformationException(e, format, isVideo, isDecoder, mediaCodecName);
    }
    return new Codec(mediaCodec, format, inputSurface);
  }

  /** Creates a {@link TransformationException}. */
  public static TransformationException createTransformationException(
      Exception cause,
      Format format,
      boolean isVideo,
      boolean isDecoder,
      @Nullable String mediaCodecName) {
    String componentName = (isVideo ? "Video" : "Audio") + (isDecoder ? "Decoder" : "Encoder");
    if (cause instanceof IOException || cause instanceof MediaCodec.CodecException) {
      return TransformationException.createForCodec(
          cause,
          componentName,
          format,
          mediaCodecName,
          isDecoder
              ? TransformationException.ERROR_CODE_DECODER_INIT_FAILED
              : TransformationException.ERROR_CODE_ENCODER_INIT_FAILED);
    }
    if (cause instanceof IllegalArgumentException) {
      return TransformationException.createForCodec(
          cause,
          componentName,
          format,
          mediaCodecName,
          isDecoder
              ? TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
              : TransformationException.ERROR_CODE_OUTPUT_FORMAT_UNSUPPORTED);
    }
    return TransformationException.createForUnexpected(cause);
  }

  private static void configureCodec(
      MediaCodec codec,
      MediaFormat mediaFormat,
      boolean isDecoder,
      @Nullable Surface outputSurface) {
    TraceUtil.beginSection("configureCodec");
    codec.configure(
        mediaFormat,
        outputSurface,
        /* crypto= */ null,
        isDecoder ? 0 : MediaCodec.CONFIGURE_FLAG_ENCODE);
    TraceUtil.endSection();
  }

  private static void startCodec(MediaCodec codec) {
    TraceUtil.beginSection("startCodec");
    codec.start();
    TraceUtil.endSection();
  }

  private CodecFactoryUtil() {}
}
