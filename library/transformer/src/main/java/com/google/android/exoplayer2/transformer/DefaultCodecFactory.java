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
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import java.io.IOException;
import java.util.List;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A default {@link Codec.DecoderFactory} and {@link Codec.EncoderFactory}. */
/* package */ final class DefaultCodecFactory
    implements Codec.DecoderFactory, Codec.EncoderFactory {
  // TODO(b/214973843): Add option to disable fallback.

  // TODO(b/210591626): Fall back adaptively to H265 if possible.
  private static final String DEFAULT_FALLBACK_MIME_TYPE = MimeTypes.VIDEO_H264;
  private static final int DEFAULT_COLOR_FORMAT = CodecCapabilities.COLOR_FormatSurface;
  private static final int DEFAULT_FRAME_RATE = 60;
  private static final int DEFAULT_I_FRAME_INTERVAL_SECS = 1;

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
  public Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
      throws TransformationException {
    checkArgument(!allowedMimeTypes.isEmpty());
    if (!allowedMimeTypes.contains(format.sampleMimeType)) {
      // TODO(b/210591626): Pick fallback MIME type using same strategy as for encoder
      // capabilities limitations.
      format = format.buildUpon().setSampleMimeType(allowedMimeTypes.get(0)).build();
    }
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
  public Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes)
      throws TransformationException {
    checkArgument(format.width != Format.NO_VALUE);
    checkArgument(format.height != Format.NO_VALUE);
    // According to interface Javadoc, format.rotationDegrees should be 0. The video should always
    // be in landscape orientation.
    checkArgument(format.height <= format.width);
    checkArgument(format.rotationDegrees == 0);
    checkNotNull(format.sampleMimeType);

    checkArgument(!allowedMimeTypes.isEmpty());

    format = getVideoEncoderSupportedFormat(format, allowedMimeTypes);

    MediaFormat mediaFormat =
        MediaFormat.createVideoFormat(
            checkNotNull(format.sampleMimeType), format.width, format.height);
    mediaFormat.setFloat(MediaFormat.KEY_FRAME_RATE, format.frameRate);
    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, format.averageBitrate);

    @Nullable
    Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format);
    if (codecProfileAndLevel != null) {
      mediaFormat.setInteger(MediaFormat.KEY_PROFILE, codecProfileAndLevel.first);
      if (SDK_INT >= 23) {
        mediaFormat.setInteger(MediaFormat.KEY_LEVEL, codecProfileAndLevel.second);
      }
    }

    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, DEFAULT_COLOR_FORMAT);
    mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL_SECS);

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

  @RequiresNonNull("#1.sampleMimeType")
  private static Format getVideoEncoderSupportedFormat(
      Format requestedFormat, List<String> allowedMimeTypes) throws TransformationException {
    String mimeType = requestedFormat.sampleMimeType;
    Format.Builder formatBuilder = requestedFormat.buildUpon();

    // TODO(b/210591626) Implement encoder filtering.
    if (!allowedMimeTypes.contains(mimeType)
        || EncoderUtil.getSupportedEncoders(mimeType).isEmpty()) {
      mimeType =
          allowedMimeTypes.contains(DEFAULT_FALLBACK_MIME_TYPE)
              ? DEFAULT_FALLBACK_MIME_TYPE
              : allowedMimeTypes.get(0);
      if (EncoderUtil.getSupportedEncoders(mimeType).isEmpty()) {
        throw createTransformationException(
            new IllegalArgumentException(
                "No encoder is found for requested MIME type " + requestedFormat.sampleMimeType),
            requestedFormat,
            /* isVideo= */ true,
            /* isDecoder= */ false,
            /* mediaCodecName= */ null);
      }
    }

    formatBuilder.setSampleMimeType(mimeType);
    MediaCodecInfo encoderInfo = EncoderUtil.getSupportedEncoders(mimeType).get(0);

    int width = requestedFormat.width;
    int height = requestedFormat.height;
    @Nullable
    Pair<Integer, Integer> encoderSupportedResolution =
        EncoderUtil.getClosestSupportedResolution(encoderInfo, mimeType, width, height);
    if (encoderSupportedResolution == null) {
      throw createTransformationException(
          new IllegalArgumentException(
              "Cannot find fallback resolution for resolution " + width + " x " + height),
          requestedFormat,
          /* isVideo= */ true,
          /* isDecoder= */ false,
          /* mediaCodecName= */ null);
    }
    width = encoderSupportedResolution.first;
    height = encoderSupportedResolution.second;
    formatBuilder.setWidth(width).setHeight(height);

    // The frameRate does not affect the resulting frame rate. It affects the encoder's rate control
    // algorithm. Setting it too high may lead to video quality degradation.
    float frameRate =
        requestedFormat.frameRate != Format.NO_VALUE
            ? requestedFormat.frameRate
            : DEFAULT_FRAME_RATE;
    int bitrate =
        EncoderUtil.getClosestSupportedBitrate(
            encoderInfo,
            mimeType,
            /* bitrate= */ requestedFormat.averageBitrate != Format.NO_VALUE
                ? requestedFormat.averageBitrate
                : getSuggestedBitrate(width, height, frameRate));
    formatBuilder.setFrameRate(frameRate).setAverageBitrate(bitrate);

    @Nullable
    Pair<Integer, Integer> profileLevel = MediaCodecUtil.getCodecProfileAndLevel(requestedFormat);
    if (profileLevel == null
        // Transcoding to another MIME type.
        || !requestedFormat.sampleMimeType.equals(mimeType)
        || !EncoderUtil.isProfileLevelSupported(
            encoderInfo,
            mimeType,
            /* profile= */ profileLevel.first,
            /* level= */ profileLevel.second)) {
      formatBuilder.setCodecs(null);
    }

    return formatBuilder.build();
  }

  /** Computes the video bit rate using the Kush Gauge. */
  private static int getSuggestedBitrate(int width, int height, float frameRate) {
    // TODO(b/210591626) Implement bitrate estimation.
    // 1080p30 -> 6.2Mbps, 720p30 -> 2.7Mbps.
    return (int) (width * height * frameRate * 0.1);
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
              : TransformationException.ERROR_CODE_OUTPUT_FORMAT_UNSUPPORTED);
    }
    return TransformationException.createForUnexpected(cause);
  }
}
