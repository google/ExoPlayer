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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.SDK_INT;
import static java.lang.Math.abs;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
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

  @Nullable private final EncoderSelector videoEncoderSelector;

  /** Creates a new instance. */
  public DefaultCodecFactory(@Nullable EncoderSelector videoEncoderSelector) {
    this.videoEncoderSelector = videoEncoderSelector;
  }

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
        /* mediaCodecName= */ null,
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
        format,
        mediaFormat,
        /* mediaCodecName= */ null,
        /* isVideo= */ true,
        /* isDecoder= */ true,
        outputSurface);
  }

  @Override
  public Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
      throws TransformationException {
    // TODO(b/210591626) Add encoder selection for audio.
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
        /* mediaCodecName= */ null,
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
    checkStateNotNull(videoEncoderSelector);

    @Nullable
    EncoderAndSupportedFormat encoderAndSupportedFormat =
        findEncoderWithClosestFormatSupport(format, videoEncoderSelector, allowedMimeTypes);
    if (encoderAndSupportedFormat == null) {
      throw createTransformationException(
          new IllegalArgumentException(
              "No encoder available that supports the requested output format."),
          format,
          /* isVideo= */ true,
          /* isDecoder= */ false,
          /* mediaCodecName= */ null);
    }

    format = encoderAndSupportedFormat.supportedFormat;
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
        encoderAndSupportedFormat.encoderInfo.getName(),
        /* isVideo= */ true,
        /* isDecoder= */ false,
        /* outputSurface= */ null);
  }

  @RequiresNonNull("#1.sampleMimeType")
  private static Codec createCodec(
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

  /**
   * Finds the {@link EncoderAndSupportedFormat} whose {@link EncoderAndSupportedFormat#encoderInfo
   * encoder} supports the {@code requestedFormat} most closely; {@code null} if none is found.
   */
  @RequiresNonNull("#1.sampleMimeType")
  @Nullable
  private static EncoderAndSupportedFormat findEncoderWithClosestFormatSupport(
      Format requestedFormat, EncoderSelector encoderSelector, List<String> allowedMimeTypes) {
    String mimeType = requestedFormat.sampleMimeType;

    // TODO(b/210591626) Improve MIME type selection.
    List<MediaCodecInfo> encodersForMimeType = encoderSelector.selectEncoderInfos(mimeType);
    if (!allowedMimeTypes.contains(mimeType) || encodersForMimeType.isEmpty()) {
      mimeType =
          allowedMimeTypes.contains(DEFAULT_FALLBACK_MIME_TYPE)
              ? DEFAULT_FALLBACK_MIME_TYPE
              : allowedMimeTypes.get(0);
      encodersForMimeType = encoderSelector.selectEncoderInfos(mimeType);
      if (encodersForMimeType.isEmpty()) {
        return null;
      }
    }

    String finalMimeType = mimeType;
    ImmutableList<MediaCodecInfo> filteredEncoders =
        filterEncoders(
            encodersForMimeType,
            /* cost= */ (encoderInfo) -> {
              @Nullable
              Pair<Integer, Integer> closestSupportedResolution =
                  EncoderUtil.getClosestSupportedResolution(
                      encoderInfo, finalMimeType, requestedFormat.width, requestedFormat.height);
              if (closestSupportedResolution == null) {
                // Drops encoder.
                return Integer.MAX_VALUE;
              }
              return abs(
                  requestedFormat.width * requestedFormat.height
                      - closestSupportedResolution.first * closestSupportedResolution.second);
            });
    if (filteredEncoders.isEmpty()) {
      return null;
    }
    // The supported resolution is the same for all remaining encoders.
    Pair<Integer, Integer> finalResolution =
        checkNotNull(
            EncoderUtil.getClosestSupportedResolution(
                filteredEncoders.get(0),
                finalMimeType,
                requestedFormat.width,
                requestedFormat.height));

    int requestedBitrate =
        requestedFormat.averageBitrate == Format.NO_VALUE
            ? getSuggestedBitrate(
                /* width= */ finalResolution.first,
                /* height= */ finalResolution.second,
                requestedFormat.frameRate == Format.NO_VALUE
                    ? DEFAULT_FRAME_RATE
                    : requestedFormat.frameRate)
            : requestedFormat.averageBitrate;
    filteredEncoders =
        filterEncoders(
            filteredEncoders,
            /* cost= */ (encoderInfo) -> {
              int achievableBitrate =
                  EncoderUtil.getClosestSupportedBitrate(
                      encoderInfo, finalMimeType, requestedBitrate);
              return abs(achievableBitrate - requestedBitrate);
            });
    if (filteredEncoders.isEmpty()) {
      return null;
    }

    MediaCodecInfo pickedEncoder = filteredEncoders.get(0);
    @Nullable
    Pair<Integer, Integer> profileLevel = MediaCodecUtil.getCodecProfileAndLevel(requestedFormat);
    @Nullable String codecs = null;
    if (profileLevel != null
        && requestedFormat.sampleMimeType.equals(finalMimeType)
        && EncoderUtil.isProfileLevelSupported(
            pickedEncoder,
            finalMimeType,
            /* profile= */ profileLevel.first,
            /* level= */ profileLevel.second)) {
      codecs = requestedFormat.codecs;
    }

    Format encoderSupportedFormat =
        requestedFormat
            .buildUpon()
            .setSampleMimeType(finalMimeType)
            .setCodecs(codecs)
            .setWidth(finalResolution.first)
            .setHeight(finalResolution.second)
            .setFrameRate(
                requestedFormat.frameRate != Format.NO_VALUE
                    ? requestedFormat.frameRate
                    : DEFAULT_FRAME_RATE)
            .setAverageBitrate(
                EncoderUtil.getClosestSupportedBitrate(
                    pickedEncoder, finalMimeType, requestedBitrate))
            .build();
    return new EncoderAndSupportedFormat(pickedEncoder, encoderSupportedFormat);
  }

  private interface EncoderFallbackCost {
    /**
     * Returns a cost that represents the gap between the requested encoding parameter(s) and the
     * {@link MediaCodecInfo encoder}'s support for them.
     *
     * <p>The method must return {@link Integer#MAX_VALUE} when the {@link MediaCodecInfo encoder}
     * does not support the encoding parameters.
     */
    int getParameterSupportGap(MediaCodecInfo encoderInfo);
  }

  private static ImmutableList<MediaCodecInfo> filterEncoders(
      List<MediaCodecInfo> encoders, EncoderFallbackCost cost) {
    List<MediaCodecInfo> filteredEncoders = new ArrayList<>(encoders.size());

    int minGap = Integer.MAX_VALUE;
    for (int i = 0; i < encoders.size(); i++) {
      MediaCodecInfo encoderInfo = encoders.get(i);
      int gap = cost.getParameterSupportGap(encoderInfo);
      if (gap == Integer.MAX_VALUE) {
        continue;
      }

      if (gap < minGap) {
        minGap = gap;
        filteredEncoders.clear();
        filteredEncoders.add(encoderInfo);
      } else if (gap == minGap) {
        filteredEncoders.add(encoderInfo);
      }
    }
    return ImmutableList.copyOf(filteredEncoders);
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

  /**
   * A class wrapping a selected {@link MediaCodecInfo encoder} and its supported {@link Format}.
   */
  private static class EncoderAndSupportedFormat {
    /** The {@link MediaCodecInfo} that describes the encoder. */
    public final MediaCodecInfo encoderInfo;
    /** The {@link Format} that this encoder supports. */
    public final Format supportedFormat;

    /** Creates a new instance. */
    public EncoderAndSupportedFormat(MediaCodecInfo encoderInfo, Format supportedFormat) {
      this.encoderInfo = encoderInfo;
      this.supportedFormat = supportedFormat;
    }
  }
}
