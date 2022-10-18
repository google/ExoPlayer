/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.SDK_INT;

import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A default implementation of {@link Codec.DecoderFactory}. */
/* package */ final class DefaultDecoderFactory implements Codec.DecoderFactory {

  @Override
  public Codec createForAudioDecoding(Format format) throws TransformationException {
    MediaFormat mediaFormat =
        MediaFormat.createAudioFormat(
            checkNotNull(format.sampleMimeType), format.sampleRate, format.channelCount);
    MediaFormatUtil.maybeSetInteger(
        mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, format.maxInputSize);
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);

    @Nullable
    String mediaCodecName = EncoderUtil.findCodecForFormat(mediaFormat, /* isDecoder= */ true);
    if (mediaCodecName == null) {
      throw createTransformationException(format);
    }
    return new DefaultCodec(
        format, mediaFormat, mediaCodecName, /* isDecoder= */ true, /* outputSurface= */ null);
  }

  @Override
  public Codec createForVideoDecoding(
      Format format, Surface outputSurface, boolean enableRequestSdrToneMapping)
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
    if (SDK_INT >= 31 && enableRequestSdrToneMapping) {
      mediaFormat.setInteger(
          MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
    }

    @Nullable
    String mediaCodecName = EncoderUtil.findCodecForFormat(mediaFormat, /* isDecoder= */ true);
    if (mediaCodecName == null) {
      throw createTransformationException(format);
    }
    return new DefaultCodec(
        format, mediaFormat, mediaCodecName, /* isDecoder= */ true, outputSurface);
  }

  @RequiresNonNull("#1.sampleMimeType")
  private static TransformationException createTransformationException(Format format) {
    return TransformationException.createForCodec(
        new IllegalArgumentException("The requested decoding format is not supported."),
        MimeTypes.isVideo(format.sampleMimeType),
        /* isDecoder= */ true,
        format,
        /* mediaCodecName= */ null,
        TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
  }
}
