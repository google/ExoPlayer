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
package androidx.media3.common.util;

import static androidx.media3.common.util.Util.SDK_INT;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.MediaFormat;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.List;

/** Helper class containing utility methods for managing {@link MediaFormat} instances. */
@UnstableApi
public final class MediaFormatUtil {

  /**
   * Custom {@link MediaFormat} key associated with a float representing the ratio between a pixel's
   * width and height.
   */
  // The constant value must not be changed, because it's also set by the framework MediaParser API.
  public static final String KEY_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT =
      "exo-pixel-width-height-ratio-float";

  /**
   * Custom {@link MediaFormat} key associated with an integer representing the PCM encoding.
   *
   * <p>Equivalent to {@link MediaFormat#KEY_PCM_ENCODING}, except it allows additional values
   * defined by {@link C.PcmEncoding}, including {@link C#ENCODING_PCM_16BIT_BIG_ENDIAN}, {@link
   * C#ENCODING_PCM_24BIT}, {@link C#ENCODING_PCM_24BIT_BIG_ENDIAN}, {@link C#ENCODING_PCM_32BIT}
   * and {@link C#ENCODING_PCM_32BIT_BIG_ENDIAN}.
   */
  // The constant value must not be changed, because it's also set by the framework MediaParser API.
  public static final String KEY_PCM_ENCODING_EXTENDED = "exo-pcm-encoding-int";

  /**
   * The {@link MediaFormat} key for the maximum bitrate in bits per second.
   *
   * <p>The associated value is an integer.
   *
   * <p>The key string constant is the same as {@code MediaFormat#KEY_MAX_BITRATE}. Values for it
   * are already returned by the framework MediaExtractor; the key is a hidden field in {@code
   * MediaFormat} though, which is why it's being replicated here.
   */
  // The constant value must not be changed, because it's also set by the framework MediaParser and
  // MediaExtractor APIs.
  public static final String KEY_MAX_BIT_RATE = "max-bitrate";

  private static final int MAX_POWER_OF_TWO_INT = 1 << 30;

  /** Returns a {@link Format} representing the given {@link MediaFormat}. */
  @SuppressLint("InlinedApi") // Inlined MediaFormat keys.
  public static Format createFormatFromMediaFormat(MediaFormat mediaFormat) {
    Format.Builder formatBuilder =
        new Format.Builder()
            .setSampleMimeType(mediaFormat.getString(MediaFormat.KEY_MIME))
            .setLanguage(mediaFormat.getString(MediaFormat.KEY_LANGUAGE))
            .setPeakBitrate(
                getInteger(mediaFormat, KEY_MAX_BIT_RATE, /* defaultValue= */ Format.NO_VALUE))
            .setAverageBitrate(
                getInteger(
                    mediaFormat, MediaFormat.KEY_BIT_RATE, /* defaultValue= */ Format.NO_VALUE))
            .setCodecs(mediaFormat.getString(MediaFormat.KEY_CODECS_STRING))
            .setFrameRate(getFrameRate(mediaFormat, /* defaultValue= */ Format.NO_VALUE))
            .setWidth(
                getInteger(mediaFormat, MediaFormat.KEY_WIDTH, /* defaultValue= */ Format.NO_VALUE))
            .setHeight(
                getInteger(
                    mediaFormat, MediaFormat.KEY_HEIGHT, /* defaultValue= */ Format.NO_VALUE))
            .setPixelWidthHeightRatio(
                getPixelWidthHeightRatio(mediaFormat, /* defaultValue= */ 1.0f))
            .setMaxInputSize(
                getInteger(
                    mediaFormat,
                    MediaFormat.KEY_MAX_INPUT_SIZE,
                    /* defaultValue= */ Format.NO_VALUE))
            .setRotationDegrees(
                getInteger(mediaFormat, MediaFormat.KEY_ROTATION, /* defaultValue= */ 0))
            // TODO(b/278101856): Disallow invalid values after confirming.
            .setColorInfo(getColorInfo(mediaFormat, /* allowInvalidValues= */ true))
            .setSampleRate(
                getInteger(
                    mediaFormat, MediaFormat.KEY_SAMPLE_RATE, /* defaultValue= */ Format.NO_VALUE))
            .setChannelCount(
                getInteger(
                    mediaFormat,
                    MediaFormat.KEY_CHANNEL_COUNT,
                    /* defaultValue= */ Format.NO_VALUE))
            .setPcmEncoding(
                getInteger(
                    mediaFormat,
                    MediaFormat.KEY_PCM_ENCODING,
                    /* defaultValue= */ Format.NO_VALUE));

    ImmutableList.Builder<byte[]> csdBuffers = new ImmutableList.Builder<>();
    int csdIndex = 0;
    while (true) {
      @Nullable ByteBuffer csdByteBuffer = mediaFormat.getByteBuffer("csd-" + csdIndex);
      if (csdByteBuffer == null) {
        break;
      }
      byte[] csdBufferData = new byte[csdByteBuffer.remaining()];
      csdByteBuffer.get(csdBufferData);
      csdByteBuffer.rewind();

      csdBuffers.add(csdBufferData);
      csdIndex++;
    }

    formatBuilder.setInitializationData(csdBuffers.build());

    return formatBuilder.build();
  }

  /**
   * Returns a {@link MediaFormat} representing the given ExoPlayer {@link Format}.
   *
   * <p>May include the following custom keys:
   *
   * <ul>
   *   <li>{@link #KEY_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT}.
   *   <li>{@link #KEY_PCM_ENCODING_EXTENDED}.
   * </ul>
   */
  @SuppressLint("InlinedApi") // Inlined MediaFormat keys.
  public static MediaFormat createMediaFormatFromFormat(Format format) {
    MediaFormat result = new MediaFormat();
    maybeSetInteger(result, MediaFormat.KEY_BIT_RATE, format.bitrate);
    maybeSetInteger(result, KEY_MAX_BIT_RATE, format.peakBitrate);
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

  /**
   * Creates and returns a {@code ColorInfo}, if a valid instance is described in the {@link
   * MediaFormat}.
   *
   * <p>Under API 24, {@code null} will always be returned, because {@link MediaFormat} color keys
   * like {@link MediaFormat#KEY_COLOR_STANDARD} were only added in API 24.
   */
  @Nullable
  public static ColorInfo getColorInfo(MediaFormat mediaFormat) {
    return getColorInfo(mediaFormat, /* allowInvalidValues= */ false);
  }

  // Internal methods.

  @Nullable
  private static ColorInfo getColorInfo(MediaFormat mediaFormat, boolean allowInvalidValues) {
    if (SDK_INT < 24) {
      // MediaFormat KEY_COLOR_TRANSFER and other KEY_COLOR values available from API 24.
      return null;
    }
    int colorSpace =
        getInteger(
            mediaFormat, MediaFormat.KEY_COLOR_STANDARD, /* defaultValue= */ Format.NO_VALUE);
    int colorRange =
        getInteger(mediaFormat, MediaFormat.KEY_COLOR_RANGE, /* defaultValue= */ Format.NO_VALUE);
    int colorTransfer =
        getInteger(
            mediaFormat, MediaFormat.KEY_COLOR_TRANSFER, /* defaultValue= */ Format.NO_VALUE);
    @Nullable
    ByteBuffer hdrStaticInfoByteBuffer = mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO);
    @Nullable
    byte[] hdrStaticInfo =
        hdrStaticInfoByteBuffer != null ? getArray(hdrStaticInfoByteBuffer) : null;

    if (!allowInvalidValues) {
      // Some devices may produce invalid values from MediaFormat#getInteger.
      // See b/239435670 for more information.
      if (!isValidColorSpace(colorSpace)) {
        colorSpace = Format.NO_VALUE;
      }
      if (!isValidColorRange(colorRange)) {
        colorRange = Format.NO_VALUE;
      }
      if (!isValidColorTransfer(colorTransfer)) {
        colorTransfer = Format.NO_VALUE;
      }
    }

    if (colorSpace != Format.NO_VALUE
        || colorRange != Format.NO_VALUE
        || colorTransfer != Format.NO_VALUE
        || hdrStaticInfo != null) {
      return new ColorInfo.Builder()
          .setColorSpace(colorSpace)
          .setColorRange(colorRange)
          .setColorTransfer(colorTransfer)
          .setHdrStaticInfo(hdrStaticInfo)
          .build();
    }
    return null;
  }

  /** Supports {@link MediaFormat#getInteger(String, int)} for {@code API < 29}. */
  public static int getInteger(MediaFormat mediaFormat, String name, int defaultValue) {
    return mediaFormat.containsKey(name) ? mediaFormat.getInteger(name) : defaultValue;
  }

  /** Supports {@link MediaFormat#getFloat(String, float)} for {@code API < 29}. */
  public static float getFloat(MediaFormat mediaFormat, String name, float defaultValue) {
    return mediaFormat.containsKey(name) ? mediaFormat.getFloat(name) : defaultValue;
  }

  /**
   * Returns the frame rate from a {@link MediaFormat}.
   *
   * <p>The {@link MediaFormat#KEY_FRAME_RATE} can have both integer and float value so it returns
   * which ever value is set.
   */
  private static float getFrameRate(MediaFormat mediaFormat, float defaultValue) {
    float frameRate = defaultValue;
    if (mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
      try {
        frameRate = mediaFormat.getFloat(MediaFormat.KEY_FRAME_RATE);
      } catch (ClassCastException ex) {
        frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
      }
    }
    return frameRate;
  }

  /** Returns the ratio between a pixel's width and height for a {@link MediaFormat}. */
  // Inlined MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH and MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT.
  @SuppressLint("InlinedApi")
  private static float getPixelWidthHeightRatio(MediaFormat mediaFormat, float defaultValue) {
    if (mediaFormat.containsKey(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH)
        && mediaFormat.containsKey(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT)) {
      return (float) mediaFormat.getInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH)
          / (float) mediaFormat.getInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT);
    }

    return defaultValue;
  }

  public static byte[] getArray(ByteBuffer byteBuffer) {
    byte[] array = new byte[byteBuffer.remaining()];
    byteBuffer.get(array);
    return array;
  }

  /** Returns whether a {@link MediaFormat} is a video format. */
  public static boolean isVideoFormat(MediaFormat mediaFormat) {
    return MimeTypes.isVideo(mediaFormat.getString(MediaFormat.KEY_MIME));
  }

  /** Returns whether a {@link MediaFormat} is an audio format. */
  public static boolean isAudioFormat(MediaFormat mediaFormat) {
    return MimeTypes.isAudio(mediaFormat.getString(MediaFormat.KEY_MIME));
  }

  /** Returns the time lapse capture FPS from the given {@link MediaFormat} if it was set. */
  @Nullable
  public static Integer getTimeLapseFrameRate(MediaFormat format) {
    if (format.containsKey("time-lapse-enable")
        && format.getInteger("time-lapse-enable") > 0
        && format.containsKey("time-lapse-fps")) {
      return format.getInteger("time-lapse-fps");
    } else {
      return null;
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
    mediaFormat.setFloat(KEY_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT, pixelWidthHeightRatio);
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
    maybeSetInteger(mediaFormat, KEY_PCM_ENCODING_EXTENDED, exoPcmEncoding);
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
      case C.ENCODING_PCM_24BIT:
        mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_24BIT_PACKED;
        break;
      case C.ENCODING_PCM_32BIT:
        mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_32BIT;
        break;
      case C.ENCODING_INVALID:
        mediaFormatPcmEncoding = AudioFormat.ENCODING_INVALID;
        break;
      case Format.NO_VALUE:
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
      case C.ENCODING_PCM_24BIT_BIG_ENDIAN:
      case C.ENCODING_PCM_32BIT_BIG_ENDIAN:
      default:
        // No matching value. Do nothing.
        return;
    }
    mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, mediaFormatPcmEncoding);
  }

  /** Whether this is a valid {@link C.ColorSpace} instance. */
  private static boolean isValidColorSpace(int colorSpace) {
    // LINT.IfChange(color_space)
    return colorSpace == C.COLOR_SPACE_BT601
        || colorSpace == C.COLOR_SPACE_BT709
        || colorSpace == C.COLOR_SPACE_BT2020
        || colorSpace == Format.NO_VALUE;
  }

  /** Whether this is a valid {@link C.ColorRange} instance. */
  private static boolean isValidColorRange(int colorRange) {
    // LINT.IfChange(color_range)
    return colorRange == C.COLOR_RANGE_LIMITED
        || colorRange == C.COLOR_RANGE_FULL
        || colorRange == Format.NO_VALUE;
  }

  /** Whether this is a valid {@link C.ColorTransfer} instance. */
  private static boolean isValidColorTransfer(int colorTransfer) {
    // LINT.IfChange(color_transfer)
    // C.COLOR_TRANSFER_GAMMA_2_2 & C.COLOR_TRANSFER_SRGB aren't valid because MediaCodec, and
    // hence MediaFormat, do not support them.
    return colorTransfer == C.COLOR_TRANSFER_LINEAR
        || colorTransfer == C.COLOR_TRANSFER_SDR
        || colorTransfer == C.COLOR_TRANSFER_ST2084
        || colorTransfer == C.COLOR_TRANSFER_HLG
        || colorTransfer == Format.NO_VALUE;
  }

  private MediaFormatUtil() {}
}
