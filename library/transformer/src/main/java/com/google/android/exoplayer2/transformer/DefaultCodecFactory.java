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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.SDK_INT;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.TraceUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A default {@link Codec.DecoderFactory} and {@link Codec.EncoderFactory}. */
/* package */ final class DefaultCodecFactory
    implements Codec.DecoderFactory, Codec.EncoderFactory {

  @Override
  public Codec createForAudioDecoding(Format format) throws TransformationException {
    MediaFormat mediaFormat =
        MediaFormat.createAudioFormat(
            checkNotNull(format.sampleMimeType), format.sampleRate, format.channelCount);
    MediaFormatUtil.maybeSetInteger(
        mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, format.maxInputSize);
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);

    return createCodec(
        format,
        mediaFormat,
        /* isVideo= */ false,
        /* isDecoder= */ true,
        /* outputSurface= */ null);
  }

  @Override
  @SuppressLint("InlinedApi")
  public Codec createForVideoDecoding(Format format, Surface outputSurface)
      throws TransformationException {
    MediaFormat mediaFormat =
        MediaFormat.createVideoFormat(
            checkNotNull(format.sampleMimeType), format.width, format.height);
    MediaFormatUtil.maybeSetInteger(mediaFormat, MediaFormat.KEY_ROTATION, format.rotationDegrees);
    MediaFormatUtil.maybeSetInteger(
        mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, format.maxInputSize);
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    if (SDK_INT >= 29) {
      // On API levels over 29, Transformer decodes as many frames as possible in one render
      // cycle. This key ensures no frame dropping when the decoder's output surface is full.
      mediaFormat.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 0);
    }

    return createCodec(
        format, mediaFormat, /* isVideo= */ true, /* isDecoder= */ true, outputSurface);
  }

  @Override
  public Codec createForAudioEncoding(Format format) throws TransformationException {
    MediaFormat mediaFormat =
        MediaFormat.createAudioFormat(
            checkNotNull(format.sampleMimeType), format.sampleRate, format.channelCount);
    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, format.bitrate);

    return createCodec(
        format,
        mediaFormat,
        /* isVideo= */ false,
        /* isDecoder= */ false,
        /* outputSurface= */ null);
  }

  @Override
  public Codec createForVideoEncoding(Format format) throws TransformationException {
    checkArgument(format.width != Format.NO_VALUE);
    checkArgument(format.height != Format.NO_VALUE);
    // According to interface Javadoc, format.rotationDegrees should be 0. The video should always
    // be in landscape orientation.
    checkArgument(format.height < format.width);
    checkArgument(format.rotationDegrees == 0);

    MediaFormat mediaFormat =
        MediaFormat.createVideoFormat(
            checkNotNull(format.sampleMimeType), format.width, format.height);
    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface);
    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
    mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 413_000);

    return createCodec(
        format,
        mediaFormat,
        /* isVideo= */ true,
        /* isDecoder= */ false,
        /* outputSurface= */ null);
  }

  @RequiresNonNull("#1.sampleMimeType")
  private static Codec createCodec(
      Format format,
      MediaFormat mediaFormat,
      boolean isVideo,
      boolean isDecoder,
      @Nullable Surface outputSurface)
      throws TransformationException {
    @Nullable MediaCodec mediaCodec = null;
    @Nullable Surface inputSurface = null;
    try {
      mediaCodec =
          isDecoder
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
      @Nullable String mediaCodecName = null;
      if (mediaCodec != null) {
        mediaCodecName = mediaCodec.getName();
        mediaCodec.release();
      }
      throw createTransformationException(e, format, isVideo, isDecoder, mediaCodecName);
    }
    return new Codec(mediaCodec, format, inputSurface);
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

  private static TransformationException createTransformationException(
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
              : TransformationException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED);
    }
    return TransformationException.createForUnexpected(cause);
  }
}
