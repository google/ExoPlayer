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
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.SynchronousMediaCodecAdapter;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import java.io.IOException;

/** A default {@link Codec.DecoderFactory} and {@link Codec.EncoderFactory}. */
/* package */ final class DefaultCodecFactory
    implements Codec.DecoderFactory, Codec.EncoderFactory {

  private static final MediaCodecInfo PLACEHOLDER_MEDIA_CODEC_INFO =
      MediaCodecInfo.newInstance(
          /* name= */ "name-placeholder",
          /* mimeType= */ "mime-type-placeholder",
          /* codecMimeType= */ "mime-type-placeholder",
          /* capabilities= */ null,
          /* hardwareAccelerated= */ false,
          /* softwareOnly= */ false,
          /* vendor= */ false,
          /* forceDisableAdaptive= */ false,
          /* forceSecure= */ false);

  @Override
  public Codec createForAudioDecoding(Format format) throws TransformationException {
    MediaFormat mediaFormat =
        MediaFormat.createAudioFormat(
            checkNotNull(format.sampleMimeType), format.sampleRate, format.channelCount);
    MediaFormatUtil.maybeSetInteger(
        mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, format.maxInputSize);
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);

    MediaCodecAdapter adapter;
    try {
      adapter =
          new MediaCodecFactory()
              .createAdapter(
                  MediaCodecAdapter.Configuration.createForAudioDecoding(
                      PLACEHOLDER_MEDIA_CODEC_INFO, mediaFormat, format, /* crypto= */ null));
    } catch (Exception e) {
      throw createTransformationException(e, format, /* isVideo= */ false, /* isDecoder= */ true);
    }
    return new Codec(adapter);
  }

  @Override
  @SuppressLint("InlinedApi")
  public Codec createForVideoDecoding(Format format, Surface surface)
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

    MediaCodecAdapter adapter;
    try {
      adapter =
          new MediaCodecFactory()
              .createAdapter(
                  MediaCodecAdapter.Configuration.createForVideoDecoding(
                      PLACEHOLDER_MEDIA_CODEC_INFO,
                      mediaFormat,
                      format,
                      surface,
                      /* crypto= */ null));
    } catch (Exception e) {
      throw createTransformationException(e, format, /* isVideo= */ true, /* isDecoder= */ true);
    }
    return new Codec(adapter);
  }

  @Override
  public Codec createForAudioEncoding(Format format) throws TransformationException {
    MediaFormat mediaFormat =
        MediaFormat.createAudioFormat(
            checkNotNull(format.sampleMimeType), format.sampleRate, format.channelCount);
    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, format.bitrate);

    MediaCodecAdapter adapter;
    try {
      adapter =
          new MediaCodecFactory()
              .createAdapter(
                  MediaCodecAdapter.Configuration.createForAudioEncoding(
                      PLACEHOLDER_MEDIA_CODEC_INFO, mediaFormat, format));
    } catch (Exception e) {
      throw createTransformationException(e, format, /* isVideo= */ false, /* isDecoder= */ false);
    }
    return new Codec(adapter);
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

    MediaCodecAdapter adapter;
    try {
      adapter =
          new MediaCodecFactory()
              .createAdapter(
                  MediaCodecAdapter.Configuration.createForVideoEncoding(
                      PLACEHOLDER_MEDIA_CODEC_INFO, mediaFormat, format));
    } catch (Exception e) {
      throw createTransformationException(e, format, /* isVideo= */ true, /* isDecoder= */ false);
    }
    return new Codec(adapter);
  }

  private static final class MediaCodecFactory extends SynchronousMediaCodecAdapter.Factory {
    @Override
    protected MediaCodec createCodec(MediaCodecAdapter.Configuration configuration)
        throws IOException {
      String sampleMimeType =
          checkNotNull(configuration.mediaFormat.getString(MediaFormat.KEY_MIME));
      boolean isDecoder = (configuration.flags & MediaCodec.CONFIGURE_FLAG_ENCODE) == 0;
      return isDecoder
          ? MediaCodec.createDecoderByType(checkNotNull(sampleMimeType))
          : MediaCodec.createEncoderByType(checkNotNull(sampleMimeType));
    }
  }

  private static TransformationException createTransformationException(
      Exception cause, Format format, boolean isVideo, boolean isDecoder) {
    String componentName = (isVideo ? "Video" : "Audio") + (isDecoder ? "Decoder" : "Encoder");
    if (cause instanceof IOException) {
      return TransformationException.createForCodec(
          cause,
          componentName,
          format,
          isDecoder
              ? TransformationException.ERROR_CODE_DECODER_INIT_FAILED
              : TransformationException.ERROR_CODE_ENCODER_INIT_FAILED);
    }
    if (cause instanceof IllegalArgumentException) {
      return TransformationException.createForCodec(
          cause,
          componentName,
          format,
          isDecoder
              ? TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
              : TransformationException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED);
    }
    return TransformationException.createForUnexpected(cause);
  }
}
