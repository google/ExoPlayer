/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.MediaFormat;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.video.ColorInfo;
import java.nio.ByteBuffer;
import java.util.List;

/** Helper class containing utility methods for managing {@link MediaFormat} instances. */
public final class MediaFormatUtil {

  /**
   * Custom {@link MediaFormat} key associated with a float representing the ratio between a pixel's
   * width and height.
   */
  public static final String KEY_EXO_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT =
      "exo-pixel-width-height-ratio-float";

  /**
   * Custom {@link MediaFormat} key associated with an integer representing the PCM encoding.
   *
   * <p>Equivalent to {@link MediaFormat#KEY_PCM_ENCODING}, except it allows additional
   * ExoPlayer-specific values including {@link C#ENCODING_PCM_16BIT_BIG_ENDIAN}, {@link
   * C#ENCODING_PCM_24BIT}, and {@link C#ENCODING_PCM_32BIT}.
   */
  public static final String KEY_EXO_PCM_ENCODING = "exo-pcm-encoding-int";

  private static final int MAX_POWER_OF_TWO_INT = 1 << 30;

  /**
   * Returns a {@link MediaFormat} representing the given ExoPlayer {@link Format}.
   *
   * <p>May include the following custom keys:
   *
   * <ul>
   *   <li>{@link #KEY_EXO_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT}.
   *   <li>{@link #KEY_EXO_PCM_ENCODING}.
   * </ul>
   */
  @SuppressLint("InlinedApi") // Inlined MediaFormat keys.
  public static MediaFormat createMediaFormatFromFormat(Format format) {
    MediaFormat result = new MediaFormat();
    maybeSetInteger(result, MediaFormat.KEY_BIT_RATE, format.bitrate);
    maybeSetInteger(result, MediaFormat.KEY_CHANNEL_COUNT, format.channelCount);

    maybeSetColorInfo(result, format.colorInfo);

    maybeSetString(result, MediaFormat.KEY_MIME, format.sampleMimeType);
    maybeSetString(result, MediaFormat.KEY_CODECS_STRING, format.codecs);
    maybeSetFloat(result, MediaFormat.KEY_FRAME_RATE, format.frameRate);
    maybeSetInteger(result, MediaFormat.KEY_WIDTH, format.width);
    maybeSetInteger(result, MediaFormat.KEY_HEIGHT, format.height);

    setCsdBuffers(result, format.initializationData);
    maybeSetPcmEncoding(result, format.pcmEncoding);
    maybeSetString(result, MediaFormat.KEY_LANGUAGE, format.language);
    maybeSetInteger(result, MediaFormat.KEY_MAX_INPUT_SIZE, format.maxInputSize);
    maybeSetInteger(result, MediaFormat.KEY_SAMPLE_RATE, format.sampleRate);
    maybeSetInteger(result, MediaFormat.KEY_CAPTION_SERVICE_NUMBER, format.accessibilityChannel);
    result.setInteger(MediaFormat.KEY_ROTATION, format.rotationDegrees);

    int selectionFlags = format.selectionFlags;
    setBooleanAsInt(
        result, MediaFormat.KEY_IS_AUTOSELECT, selectionFlags & C.SELECTION_FLAG_AUTOSELECT);
    setBooleanAsInt(result, MediaFormat.KEY_IS_DEFAULT, selectionFlags & C.SELECTION_FLAG_DEFAULT);
    setBooleanAsInt(
        result, MediaFormat.KEY_IS_FORCED_SUBTITLE, selectionFlags & C.SELECTION_FLAG_FORCED);

    result.setInteger(MediaFormat.KEY_ENCODER_DELAY, format.encoderDelay);
    result.setInteger(MediaFormat.KEY_ENCODER_PADDING, format.encoderPadding);

    maybeSetPixelAspectRatio(result, format.pixelWidthHeightRatio);
    return result;
  }

  /**
   * Sets a {@link MediaFormat} {@link String} value. Does nothing if {@code value} is null.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param key The key to set.
   * @param value The value to set.
   */
  public static void maybeSetString(MediaFormat format, String key, @Nullable String value) {
    if (value != null) {
      format.setString(key, value);
    }
  }

  /**
   * Sets a {@link MediaFormat}'s codec specific data buffers.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param csdBuffers The csd buffers to set.
   */
  public static void setCsdBuffers(MediaFormat format, List<byte[]> csdBuffers) {
    for (int i = 0; i < csdBuffers.size(); i++) {
      format.setByteBuffer("csd-" + i, ByteBuffer.wrap(csdBuffers.get(i)));
    }
  }

  /**
   * Sets a {@link MediaFormat} integer value. Does nothing if {@code value} is {@link
   * Format#NO_VALUE}.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param key The key to set.
   * @param value The value to set.
   */
  public static void maybeSetInteger(MediaFormat format, String key, int value) {
    if (value != Format.NO_VALUE) {
      format.setInteger(key, value);
    }
  }

  /**
   * Sets a {@link MediaFormat} float value. Does nothing if {@code value} is {@link
   * Format#NO_VALUE}.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param key The key to set.
   * @param value The value to set.
   */
  public static void maybeSetFloat(MediaFormat format, String key, float value) {
    if (value != Format.NO_VALUE) {
      format.setFloat(key, value);
    }
  }

  /**
   * Sets a {@link MediaFormat} {@link ByteBuffer} value. Does nothing if {@code value} is null.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param key The key to set.
   * @param value The byte array that will be wrapped to obtain the value.
   */
  public static void maybeSetByteBuffer(MediaFormat format, String key, @Nullable byte[] value) {
    if (value != null) {
      format.setByteBuffer(key, ByteBuffer.wrap(value));
    }
  }

  /**
   * Sets a {@link MediaFormat}'s color information. Does nothing if {@code colorInfo} is null.
   *
   * @param format The {@link MediaFormat} being configured.
   * @param colorInfo The color info to set.
   */
  @SuppressWarnings("InlinedApi")
  public static void maybeSetColorInfo(MediaFormat format, @Nullable ColorInfo colorInfo) {
    if (colorInfo != null) {
      maybeSetInteger(format, MediaFormat.KEY_COLOR_TRANSFER, colorInfo.colorTransfer);
      maybeSetInteger(format, MediaFormat.KEY_COLOR_STANDARD, colorInfo.colorSpace);
      maybeSetInteger(format, MediaFormat.KEY_COLOR_RANGE, colorInfo.colorRange);
      maybeSetByteBuffer(format, MediaFormat.KEY_HDR_STATIC_INFO, colorInfo.hdrStaticInfo);
    }
  }

  // Internal methods.

  private static void setBooleanAsInt(MediaFormat format, String key, int value) {
    format.setInteger(key, value != 0 ? 1 : 0);
  }

  // Inlined MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH and MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT.
  @SuppressLint("InlinedApi")
  private static void maybeSetPixelAspectRatio(
      MediaFormat mediaFormat, float pixelWidthHeightRatio) {
    mediaFormat.setFloat(KEY_EXO_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT, pixelWidthHeightRatio);
    int pixelAspectRatioWidth = 1;
    int pixelAspectRatioHeight = 1;
    // ExoPlayer extractors output the pixel aspect ratio as a float. Do our best to recreate the
    // pixel aspect ratio width and height by using a large power of two factor.
    if (pixelWidthHeightRatio < 1.0f) {
      pixelAspectRatioHeight = MAX_POWER_OF_TWO_INT;
      pixelAspectRatioWidth = (int) (pixelWidthHeightRatio * pixelAspectRatioHeight);
    } else if (pixelWidthHeightRatio > 1.0f) {
      pixelAspectRatioWidth = MAX_POWER_OF_TWO_INT;
      pixelAspectRatioHeight = (int) (pixelAspectRatioWidth / pixelWidthHeightRatio);
    }
    mediaFormat.setInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH, pixelAspectRatioWidth);
    mediaFormat.setInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT, pixelAspectRatioHeight);
  }

  @SuppressLint("InlinedApi") // Inlined KEY_PCM_ENCODING.
  private static void maybeSetPcmEncoding(
      MediaFormat mediaFormat, @C.PcmEncoding int exoPcmEncoding) {
    if (exoPcmEncoding == Format.NO_VALUE) {
      return;
    }
    int mediaFormatPcmEncoding;
    maybeSetInteger(mediaFormat, KEY_EXO_PCM_ENCODING, exoPcmEncoding);
    switch (exoPcmEncoding) {
      case C.ENCODING_PCM_8BIT:
        mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_8BIT;
        break;
      case C.ENCODING_PCM_16BIT:
        mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
        break;
      case C.ENCODING_PCM_FLOAT:
        mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_FLOAT;
        break;
      default:
        // No matching value. Do nothing.
        return;
    }
    mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, mediaFormatPcmEncoding);
  }

  private MediaFormatUtil() {}
}
